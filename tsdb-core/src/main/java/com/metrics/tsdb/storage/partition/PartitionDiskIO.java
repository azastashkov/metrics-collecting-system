package com.metrics.tsdb.storage.partition;

import com.metrics.tsdb.model.SeriesId;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public class PartitionDiskIO {

    private static final int MAGIC = 0x54534442; // "TSDB"
    private static final int VERSION = 1;

    public static void write(TimePartition partition, Path directory, long durationMs) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("partition_" + partition.getStartTimeMs() + ".bin");

        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);

            Set<SeriesId> seriesIds = partition.getSeriesIds();
            out.writeInt(seriesIds.size());

            for (SeriesId id : seriesIds) {
                SeriesData data = partition.getSeriesData(id);
                if (data == null) continue;

                long[] ts = data.getTimestampsCopy();
                double[] vals = data.getValuesCopy();
                int count = ts.length;

                out.writeLong(id.getId());
                out.writeInt(count);
                for (int i = 0; i < count; i++) {
                    out.writeLong(ts[i]);
                }
                for (int i = 0; i < count; i++) {
                    out.writeDouble(vals[i]);
                }
            }
        }
    }

    public static TimePartition read(Path file, long durationMs) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid TSDB file: bad magic number");
            }

            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported TSDB file version: " + version);
            }

            int seriesCount = in.readInt();

            // Extract start time from filename
            String fileName = file.getFileName().toString();
            long startTimeMs = Long.parseLong(
                    fileName.replace("partition_", "").replace(".bin", ""));

            TimePartition partition = new TimePartition(startTimeMs, durationMs);

            for (int s = 0; s < seriesCount; s++) {
                long seriesIdVal = in.readLong();
                int count = in.readInt();

                long[] timestamps = new long[count];
                for (int i = 0; i < count; i++) {
                    timestamps[i] = in.readLong();
                }

                double[] values = new double[count];
                for (int i = 0; i < count; i++) {
                    values[i] = in.readDouble();
                }

                SeriesId id = new SeriesId(seriesIdVal);
                SeriesData data = new SeriesData(timestamps, values, count);
                // Write each sample to the partition to populate it
                for (int i = 0; i < count; i++) {
                    partition.write(id, timestamps[i], values[i]);
                }
            }

            return partition;
        }
    }
}
