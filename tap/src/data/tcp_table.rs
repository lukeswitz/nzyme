
use std::{sync::{Arc}};
use std::collections::{BTreeMap, HashMap};
use std::net::IpAddr;
use std::sync::{Mutex, MutexGuard};
use chrono::{DateTime, Duration, Utc};
use log::{error, trace, warn};
use strum_macros::Display;
use crate::data::tcp_table::TcpSessionState::{ClosedFin, ClosedRst, ClosedTimeout, Established, FinWait1, FinWait2, Refused, SynReceived, SynSent};
use crate::ethernet::detection::l4_session_tagger::{L4SessionTag, tag_tcp_sessions};
use crate::ethernet::packets::{TcpSegment};
use crate::ethernet::tcp_session_key::TcpSessionKey;
use crate::ethernet::traffic_direction::TrafficDirection;
use crate::link::leaderlink::Leaderlink;
use crate::link::reports::tcp_sessions_report;

static SESSION_TIMEOUT: usize = 60;

pub struct TcpTable {
    pub leaderlink: Arc<Mutex<Leaderlink>>,
    pub sessions: Mutex<HashMap<TcpSessionKey, TcpSession>>,
}

#[derive(Debug)]
pub struct TcpSession {
    pub state: TcpSessionState,
    pub source_address: IpAddr,
    pub source_port: u16,
    pub destination_address: IpAddr,
    pub destination_port: u16,
    pub start_time: DateTime<Utc>,
    pub end_time: Option<DateTime<Utc>>,
    pub most_recent_segment_time: DateTime<Utc>,
    pub segment_count: u64,
    pub bytes_count: u64,
    pub segments_client_to_server: BTreeMap<u32, Vec<u8>>,
    pub segments_server_to_client: BTreeMap<u32, Vec<u8>>,
    pub tags: Option<Vec<L4SessionTag>>
}

#[derive(PartialEq, Debug, Display, Clone)]
pub enum TcpSessionState {
    SynSent,
    SynReceived,
    Established,
    FinWait1,
    FinWait2,
    ClosedFin,
    ClosedRst,
    ClosedTimeout,
    Refused
}

impl TcpTable {

    pub fn new(leaderlink: Arc<Mutex<Leaderlink>>) -> Self {
        Self {
            leaderlink,
            sessions: Mutex::new(HashMap::new())
        }
    }

    pub fn register_segment(&mut self, segment: &Arc<TcpSegment>) {
        match self.sessions.lock() {
            Ok(mut sessions) => {
                match sessions.get_mut(&segment.session_key) {
                    Some(session) => {
                        let session_state = Self::determine_session_state(segment, Some(session));

                        // TOOD direction.
                        if !segment.payload.is_empty() {
                            match segment.determine_direction() {
                                TrafficDirection::ClientToServer => {
                                    insert_session_segment(segment, &mut session.segments_client_to_server);
                                }
                                TrafficDirection::ServerToClient => {
                                    insert_session_segment(segment, &mut session.segments_server_to_client);
                                }
                            }
                        }

                        session.most_recent_segment_time = segment.timestamp;
                        session.state = session_state.clone();
                        session.segment_count += 1;
                        session.bytes_count += segment.size as u64;

                        if session.end_time.is_none() && (session_state == ClosedFin || session_state == ClosedRst) {
                            session.end_time = Some(segment.timestamp);
                        }

                        trace!("Segment of existing TCP Session: {:?}, State: {:?}, Flags: {:?}",
                            segment.session_key, session_state, segment.flags);
                    },
                    None => {
                        // First time seeing this session.
                        let session_state = Self::determine_session_state(segment, None);

                        // We only record new sessions, not mid-session.
                        if session_state == SynSent {
                            let new_session = TcpSession {
                                state: session_state.clone(),
                                start_time: segment.timestamp,
                                end_time: None,
                                most_recent_segment_time: segment.timestamp,
                                source_address: segment.source_address,
                                source_port: segment.source_port,
                                destination_address: segment.destination_address,
                                destination_port: segment.destination_port,
                                segment_count: 1,
                                bytes_count: segment.size as u64,
                                segments_client_to_server: BTreeMap::new(),
                                segments_server_to_client: BTreeMap::new(),
                                tags: None
                            };

                            trace!("New TCP Session: {:?}, State: {:?}, Flags: {:?}",
                            segment.session_key, session_state, segment.flags);

                            sessions.insert(segment.session_key.clone(), new_session);
                        }
                    }
                }
            },
            Err(e) => {
                error!("Could not acquire TCP sessions table mutex: {}", e);
            }
        }
    }

    pub fn process_report(&self) {
        match self.sessions.lock() {
            Ok(mut sessions) => {
                // Mark all timed out sessions in table as ClosedTimeout.
                timeout_sweep(&mut sessions);

                tag_tcp_sessions(&mut sessions);

                let report: String = serde_json::to_string(&tcp_sessions_report::generate(&sessions)).unwrap(); // TODO unwrap

                // Send data. Only proceed with cleanup if successful.
                // TODO error handling.
                match self.leaderlink.lock() {
                    Ok(link) => {
                        if let Err(e) = link.send_report("tcp/sessions", report) {
                            error!("Could not submit TCP report: {}", e);
                        }
                    },
                    Err(e) => error!("Could not acquire TCP table lock for report submission: {}", e)
                }

                // Delete all timed out and closedfin/closedrst sessions in table.
                retention_sweep(&mut sessions);
            },
            Err(e) => {
                error!("Could not acquire TCP sessions table mutex for report generation: {}", e);
            }
        }
    }

    fn determine_session_state(segment: &TcpSegment, session: Option<&TcpSession>)
        -> TcpSessionState {
        match session {
            Some(session) => {
                // Do not re-open closed connections in case of out-of-order segments.
                if session.state == ClosedFin { return ClosedFin }
                if session.state == ClosedRst { return ClosedRst }
                if session.state == ClosedTimeout { return ClosedTimeout }

                if segment.flags.syn && segment.flags.ack {
                    // SYN ACK.
                    SynReceived
                } else if segment.flags.ack && session.state == SynReceived {
                    // Final ACK. Handshake complete.
                    Established
                } else if segment.flags.reset && session.state == SynSent {
                    // SYN got an immediate RST response. Connection rejected by host.
                    Refused
                } else if segment.flags.reset {
                    ClosedRst
                } else if segment.flags.fin && session.state == Established {
                    // Initial FIN
                    FinWait1
                } else if segment.flags.ack && session.state == FinWait1 {
                    // FIN ACK
                    FinWait2
                } else if segment.flags.ack && session.state == FinWait2 {
                    // Final FIN.
                    ClosedFin
                } else {
                    Established
                }
            },
            None => {
                // First time we see this session.
                if segment.flags.syn && !segment.flags.ack {
                    SynSent
                } else if segment.flags.syn && segment.flags.ack {
                    SynReceived
                } else if !segment.flags.syn && segment.flags.ack {
                    /*
                     * Either normal segment flow in established connection or final
                     * ACK in handshake or final ACK in FIN process. Considering that there was
                     * no previous segment recorded, we assume the likeliest case: A segment
                     * exchanged during an established connection.
                     *
                     * We'd falsely mark this connection as ESTABLISHED and have it time out if
                     * this was a FIN ACK and accept that risk, as it can only happen right after
                     * capture start.
                     *
                     * Additionally, the processing logic is only recording newly established
                     * connections and discarding anything else.
                     */
                    Established
                } else if segment.flags.fin {
                    FinWait1
                } else if segment.flags.reset {
                    ClosedRst
                } else {
                    warn!("Unexpected flags for new TCP session: <{:?}> {:?}",
                        segment.session_key, segment.flags);

                    Established
                }
            }
        }
    }
}

fn insert_session_segment(segment: &TcpSegment, segments: &mut BTreeMap<u32, Vec<u8>>) {
    match segments.get(&segment.sequence_number) {
        Some(_) => {
            trace!("TCP session {:?} already contains segment {}. Ignoring as \
                                retransmission.", segment.session_key, segment.sequence_number)
        },
        None => {
            // New segment. (not a retransmission of segment we already recorded)
            segments.insert(segment.sequence_number, segment.payload.clone());
        }
    }
}

fn timeout_sweep(sessions: &mut MutexGuard<HashMap<TcpSessionKey, TcpSession>>) {
    for session in sessions.values_mut() {
        if Utc::now() - session.most_recent_segment_time
            > Duration::try_seconds(SESSION_TIMEOUT as i64).unwrap() {
            session.state = ClosedTimeout;
        }
    }
}

fn retention_sweep(sessions: &mut MutexGuard<HashMap<TcpSessionKey, TcpSession>>) {
    sessions.retain(|_, s| s.state != ClosedTimeout && s.state != ClosedRst && s.state != ClosedFin)
}