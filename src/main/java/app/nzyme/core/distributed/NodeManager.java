package app.nzyme.core.distributed;

import app.nzyme.core.NzymeNode;
import app.nzyme.core.distributed.database.NodeEntry;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class NodeManager {

    private static final Logger LOG = LogManager.getLogger(NodeManager.class);

    private final NzymeNode nzyme;

    private UUID localNodeId;

    public NodeManager(NzymeNode nzyme) {
        this.nzyme = nzyme;
    }

    public void initialize() throws NodeInitializationException {
        // Read local node id.
        Path nodeIdFile = Path.of(nzyme.getDataDirectory().toString(), "node_id");
        if (Files.exists(nodeIdFile)) {
            try {
                LOG.debug("Node ID file exists at [{}]", nodeIdFile.toAbsolutePath());
                localNodeId = UUID.fromString(Files.readString(nodeIdFile));
            } catch (IOException e) {
                throw new NodeInitializationException("Could not read node ID file at [" + nodeIdFile.toAbsolutePath() + "]", e);
            }
        } else {
            LOG.debug("Node ID file does not exist at [{}]. Creating.", nodeIdFile.toAbsolutePath());
            UUID newNodeId = UUID.randomUUID();

            try {
                Files.writeString(nodeIdFile, newNodeId.toString(), Charsets.UTF_8, StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new NodeInitializationException("Could not write node ID file at [" + nodeIdFile.toAbsolutePath() + "]", e);
            }

            localNodeId = newNodeId;
            LOG.info("Created node ID: [{}]", localNodeId);
        }

        Executors.newSingleThreadScheduledExecutor(
                new ThreadFactoryBuilder()
                        .setNameFormat("node-metrics-updater-%d")
                        .setDaemon(true)
                        .build()
        ).scheduleAtFixedRate(this::writeMetrics, 1, 1, TimeUnit.MINUTES);

        LOG.info("Node ID: [{}]", localNodeId);
    }

    public void registerSelf() {
        if (localNodeId == null) {
            throw new RuntimeException("Not initialized. Cannot register myself.");
        }

        NodeInformation.Info ni = new NodeInformation().collect();

        nzyme.getDatabase().useHandle(handle ->
                handle.createUpdate("INSERT INTO nodes(uuid, name, http_external_uri, version, last_seen, " +
                                "memory_bytes_total, memory_bytes_available, memory_bytes_used, heap_bytes_total, " +
                                "heap_bytes_available, heap_bytes_used, cpu_system_load, cpu_thread_count, " +
                                "process_start_time, process_virtual_size, process_arguments, os_information) " +
                                "VALUES(:uuid, :name, :http_external_uri, :version, NOW(), :memory_bytes_total, " +
                                ":memory_bytes_available, :memory_bytes_used, :heap_bytes_total, :heap_bytes_available, " +
                                " :heap_bytes_used, :cpu_system_load, :cpu_thread_count, :process_start_time, " +
                                ":process_virtual_size, :process_arguments, :os_information) " +
                                "ON CONFLICT(uuid) DO UPDATE SET name = :name, http_external_uri = :http_external_uri, " +
                                "version = :version, last_seen = NOW(), memory_bytes_total = :memory_bytes_total, " +
                                "memory_bytes_available = :memory_bytes_available, memory_bytes_used = :memory_bytes_used, " +
                                "heap_bytes_total = :heap_bytes_total, heap_bytes_available = :heap_bytes_available, " +
                                "heap_bytes_used = :heap_bytes_used, cpu_system_load = :cpu_system_load, " +
                                "cpu_thread_count = :cpu_thread_count, process_start_time = :process_start_time, " +
                                "process_virtual_size = :process_virtual_size, process_arguments = :process_arguments, " +
                                "os_information = :os_information")
                        .bind("uuid", localNodeId)
                        .bind("name", nzyme.getNodeInformation().name())
                        .bind("http_external_uri", nzyme.getConfiguration().httpExternalUri().toString())
                        .bind("version", nzyme.getVersion().getVersion().toString())
                        .bind("memory_bytes_total", ni.memoryTotal())
                        .bind("memory_bytes_available", ni.memoryAvailable())
                        .bind("memory_bytes_used", ni.memoryUsed())
                        .bind("heap_bytes_total", ni.heapTotal())
                        .bind("heap_bytes_available", ni.heapAvailable())
                        .bind("heap_bytes_used", ni.heapUsed())
                        .bind("cpu_system_load", ni.cpuSystemLoad())
                        .bind("cpu_thread_count", ni.cpuThreadCount())
                        .bind("process_start_time", ni.processStartTime())
                        .bind("process_virtual_size", ni.processVirtualSize())
                        .bind("process_arguments", ni.processArguments())
                        .bind("os_information", ni.osInformation())
                        .execute()
        );
    }

    public List<Node> getActiveNodes() {
        List<NodeEntry> dbEntries = nzyme.getDatabase().withHandle(handle ->
                handle.createQuery("SELECT * FROM nodes WHERE last_seen > :timeout ORDER BY name DESC")
                        .bind("timeout", DateTime.now().minusHours(24))
                        .mapTo(NodeEntry.class)
                        .list()
        );

        List<Node> nodes = Lists.newArrayList();
        for (NodeEntry dbEntry : dbEntries) {
            try {
                URI httpExternalUri = URI.create(dbEntry.httpExternalUri());
                nodes.add(Node.create(
                        dbEntry.uuid(),
                        dbEntry.name(),
                        httpExternalUri,
                        dbEntry.memoryBytesTotal(),
                        dbEntry.memoryBytesAvailable(),
                        dbEntry.memoryBytesUsed(),
                        dbEntry.heapBytesTotal(),
                        dbEntry.heapBytesAvailable(),
                        dbEntry.heapBytesUsed(),
                        dbEntry.cpuSystemLoad(),
                        dbEntry.cpuThreadCount(),
                        dbEntry.processStartTime(),
                        dbEntry.processVirtualSize(),
                        dbEntry.processArguments(),
                        dbEntry.osInformation(),
                        dbEntry.version(),
                        dbEntry.lastSeen()
                ));
            } catch (Exception e) {
                LOG.error("Could not create node from database entry. Skipping.", e);
            }
        }

        return nodes;
    }

    private void writeMetrics() {
        try {
            NodeInformation.Info ni = new NodeInformation().collect();

            writeGauge("memory_bytes_total", ni.memoryTotal());
            writeGauge("memory_bytes_available", ni.memoryAvailable());
            writeGauge("memory_bytes_used", ni.memoryUsed());
            writeGauge("heap_bytes_total", ni.heapTotal());
            writeGauge("heap_bytes_available", ni.heapAvailable());
            writeGauge("heap_bytes_used", ni.heapUsed());
            writeGauge("cpu_system_load", ni.cpuSystemLoad());
            writeGauge("process_virtual_size", ni.processVirtualSize());
        } catch(Exception e) {
            LOG.error("Could not write node metrics.", e);
        }

        // Retention clean old metrics.
        nzyme.getDatabase().useHandle(handle -> {
            handle.createUpdate("DELETE FROM node_metrics_gauges WHERE created_at < :created_at")
                    .bind("created_at", DateTime.now().minusHours(24))
                    .execute();
        });
    }

    private void writeGauge(String metricName, Long metricValue) {
        writeGauge(metricName, metricValue.doubleValue());
    }

    private void writeGauge(String metricName, Double metricValue) {
        nzyme.getDatabase().withHandle(handle -> handle.createUpdate("INSERT INTO node_metrics_gauges(node_id, metric_name, metric_value, created_at) " +
                        "VALUES(:node_id, :metric_name, :metric_value, :created_at)")
                .bind("node_id", nzyme.getNodeInformation().id())
                .bind("metric_name", metricName)
                .bind("metric_value", metricValue)
                .bind("created_at", DateTime.now())
                .execute()
        );
    }

    public UUID getLocalNodeId() {
        return localNodeId;
    }

    public static final class NodeInitializationException extends Throwable {

        public NodeInitializationException(String msg) {
            super(msg);
        }

        public NodeInitializationException(String msg, Throwable e) {
            super(msg, e);
        }

    }

}
