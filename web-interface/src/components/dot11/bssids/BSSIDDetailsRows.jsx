import React from "react";
import SignalStrength from "../../shared/SignalStrength";
import moment from "moment/moment";
import numeral from "numeral";
import LoadingSpinner from "../../misc/LoadingSpinner";
import Channel from "../util/Channel";
import ApiRoutes from "../../../util/ApiRoutes";
import InfrastructureTypes from "../util/InfrastructureTypes";
import Dot11SecurityProtocolList from "../shared/Dot11SecurityProtocolList";

function BSSIDDetailsRows(props) {

  const COLSPAN = 8;

  const bssid = props.bssid;
  const ssids = props.ssids;
  const loading = props.loading;
  const hideBSSIDLink = props.hideBSSIDLink;

  const bssidLinkRow = () => {
    if (hideBSSIDLink) {
      return null;
    } else {
      return (
          <tr>
            <td colSpan={COLSPAN} style={{textAlign: "center"}}>
              <a href={ApiRoutes.DOT11.NETWORKS.BSSID(bssid.address)}>Show BSSID Details</a>
            </td>
          </tr>
      )
    }
  }

  if (loading) {
    return (
        <tr>
          <td colSpan={COLSPAN}>
            <LoadingSpinner />
          </td>
        </tr>
    )
  }

  if (ssids === null) {
    return null;
  }

  if (ssids.length === 0) {
    return (
        <tr>
          <td colSpan={COLSPAN}>
            Only hidden SSIDs.{' '}
            {hideBSSIDLink ? null : <a href={ApiRoutes.DOT11.NETWORKS.BSSID(bssid.address)}>Open BSSID Details</a>}
          </td>
        </tr>
    )
  }

  return (
    <React.Fragment>
      <tr>
        <td colSpan={COLSPAN}>
          <table className="table table-sm table-hover table-striped mb-0">
            <thead>
            <tr>
              <th>SSID</th>
              <th>Mode</th>
              <th>Channel</th>
              <th>Usage</th>
              <th>Signal Strength</th>
              <th>Security</th>
              <th>WPS</th>
              <th>Last Seen</th>
            </tr>
            </thead>
            <tbody>
              {ssids.sort((a, b) => a.ssid.localeCompare(b.ssid)).sort((a, b) => b.is_main_active - a.is_main_active).map(function (ssid, i) {
                return (
                  <tr key={"ssid-" + i}>
                    <td>
                      <a href={ApiRoutes.DOT11.NETWORKS.SSID(bssid.address, ssid.ssid, ssid.frequency)}>{ssid.ssid}</a>{' '}
                    </td>
                    <td><InfrastructureTypes types={ssid.infrastructure_types} /></td>
                    <td>
                      <Channel channel={ssid.channel} frequency={ssid.frequency} is_main_active={ssid.is_main_active} />
                    </td>
                    <td>{numeral(ssid.total_frames).format("0,0")} frames / {numeral(ssid.total_bytes).format("0,0b")}</td>
                    <td><SignalStrength strength={ssid.signal_strength_average} /></td>
                    <td>{ssid.security_protocols.length === 0 || ssid.security_protocols[0] === "" ? "None" : <Dot11SecurityProtocolList protocols={ssid.security_protocols} />}</td>
                    <td>{ssid.is_wps.join(",")}</td>
                    <td title={moment(ssid.last_seen).format()}>
                      {moment(ssid.last_seen).fromNow()}
                    </td>
                  </tr>
                )
              })}
              {bssidLinkRow()}
            </tbody>
          </table>
        </td>
      </tr>
    </React.Fragment>
  )

}

export default BSSIDDetailsRows;