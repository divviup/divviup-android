package org.divviup.android;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;

public class Client<M> {
    private static final String HPKE_CONFIG_LIST_CONTENT_TYPE = "application/dap-hpke-config-list";
    private static final String REPORT_CONTENT_TYPE = "application/dap-report";

    static {
        System.loadLibrary("divviup_android");
    }

    private final URI leaderEndpoint, helperEndpoint;
    private final TaskId taskId;
    private final long timePrecisionSeconds;
    private final ReportPreparer<M> reportPreparer;


    private Client(
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            ReportPreparer<M> reportPreparer
    ) {
        if (!leaderEndpoint.getScheme().equals("https") && !leaderEndpoint.getScheme().equals("http")) {
            throw new IllegalArgumentException("leaderEndpoint must be an HTTP or HTTPS URI");
        }

        if (!helperEndpoint.getScheme().equals("https") && !helperEndpoint.getScheme().equals("http")) {
            throw new IllegalArgumentException("helperEndpoint must be an HTTP or HTTPS URI");
        }

        if (timePrecisionSeconds <= 0) {
            throw new IllegalArgumentException("timePrecisionSeconds must be positive");
        }

        this.leaderEndpoint = leaderEndpoint;
        this.helperEndpoint = helperEndpoint;
        this.taskId = taskId;
        this.timePrecisionSeconds = timePrecisionSeconds;
        this.reportPreparer = reportPreparer;
    }

    public static Client<Boolean> createPrio3Count(
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds
    ) {
        return new Client<>(
                leaderEndpoint,
                helperEndpoint,
                taskId,
                timePrecisionSeconds,
                new Prio3CountReportPreparer()
        );
    }

    public static Client<Long> createPrio3Sum(
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            long bits
    ) {
        return new Client<>(
                leaderEndpoint,
                helperEndpoint,
                taskId,
                timePrecisionSeconds,
                new Prio3SumReportPreparer(bits)
        );
    }

    public static Client<long[]> createPrio3SumVec(
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            long length,
            long bits,
            long chunkLength
    ) {
        return new Client<>(
                leaderEndpoint,
                helperEndpoint,
                taskId,
                timePrecisionSeconds,
                new Prio3SumVecReportPreparer(length, bits, chunkLength)
        );
    }

    public static Client<Long> createPrio3Histogram(
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            long length,
            long chunkLength
    ) {
        return new Client<>(
                leaderEndpoint,
                helperEndpoint,
                taskId,
                timePrecisionSeconds,
                new Prio3HistogramReportPreparer(length, chunkLength)
        );
    }

    public void sendMeasurement(M measurement) throws IOException {
        HpkeConfigList leaderConfigList = this.fetchHPKEConfigList(this.leaderEndpoint, this.taskId);
        HpkeConfigList helperConfigList = this.fetchHPKEConfigList(this.helperEndpoint, this.taskId);
        byte[] report = reportPreparer.prepareReport(this, leaderConfigList, helperConfigList, measurement);

        String path = "tasks/" + this.taskId.encodeToString() + "/reports";
        URL url = leaderEndpoint.resolve(path).toURL();
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setRequestProperty("User-Agent", getUserAgent());
        connection.setRequestProperty("Content-Type", REPORT_CONTENT_TYPE);
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(report.length);
        connection.connect();
        OutputStream out = connection.getOutputStream();
        IOUtils.write(report, out);
        out.close();
        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException(
                    "aggregator returned HTTP response code " + code + " when uploading report"
            );
        }
    }

    private HpkeConfigList fetchHPKEConfigList(URI aggregatorEndpoint, TaskId taskId) throws IOException {
        String path = "hpke_config?task_id=" + taskId.encodeToString();
        URL url = aggregatorEndpoint.resolve(path).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("User-Agent", getUserAgent());
        connection.setUseCaches(true);
        connection.setDoInput(true);
        connection.connect();
        int code = connection.getResponseCode();
        if (code >= 400) {
            throw new IOException(
                    "aggregator returned HTTP response code " + code + " when fetching HPKE configs"
            );
        }
        String contentType = connection.getContentType();
        if (!contentType.equals(HPKE_CONFIG_LIST_CONTENT_TYPE)) {
            throw new IOException("wrong content type for HPKE configs: " + contentType);
        }
        InputStream in = connection.getInputStream();
        byte[] data = IOUtils.toByteArray(in);
        in.close();
        return new HpkeConfigList(data);
    }

    private static String getUserAgent() {
        Package pkg = Client.class.getPackage();
        if (pkg != null) {
            return "divviup-android/" + pkg.getImplementationVersion();
        } else {
            return "divviup-android";
        }
    }

    private long reportTimestamp() {
        long seconds = System.currentTimeMillis() / 1000L;
        return seconds - (seconds % timePrecisionSeconds);
    }

    private static class HpkeConfigList {
        private final byte[] bytes;

        public HpkeConfigList(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    private interface ReportPreparer<M> {
        byte[] prepareReport(
                Client<M> client,
                HpkeConfigList leaderConfigList,
                HpkeConfigList helperConfigList,
                M measurement
        );
    }

    private static class Prio3CountReportPreparer implements ReportPreparer<Boolean> {
        @Override
        public byte[] prepareReport(Client<Boolean> client, HpkeConfigList leaderConfigList, HpkeConfigList helperConfigList, Boolean measurement) {
            if (measurement != null) {
                return this.prepareReportNative(
                        client.taskId.toBytes(),
                        leaderConfigList.bytes,
                        helperConfigList.bytes,
                        client.reportTimestamp(),
                        (Boolean) measurement
                );
            } else {
                throw new IllegalArgumentException("measurement for Prio3Count must be a Boolean");
            }
        }

        private native byte[] prepareReportNative(
                byte[] taskId,
                byte[] leaderHPKEConfigList,
                byte[] helperHPKEConfigList,
                long timestamp,
                boolean measurement
        );
    }

    private static class Prio3SumReportPreparer implements ReportPreparer<Long> {
        private final long bits;

        public Prio3SumReportPreparer(long bits) {
            this.bits = bits;
        }

        @Override
        public byte[] prepareReport(Client<Long> client, HpkeConfigList leaderConfigList, HpkeConfigList helperConfigList, Long measurement) {
            if (measurement != null) {
                return this.prepareReportNative(
                        client.taskId.toBytes(),
                        leaderConfigList.bytes,
                        helperConfigList.bytes,
                        client.reportTimestamp(),
                        bits,
                        (Long) measurement
                );
            } else {
                throw new IllegalArgumentException("measurement for Prio3Sum must be a Long");
            }
        }

        private native byte[] prepareReportNative(
                byte[] taskId,
                byte[] leaderHPKEConfigList,
                byte[] helperHPKEConfigList,
                long timestamp,
                long bits,
                long measurement
        );
    }

    private static class Prio3SumVecReportPreparer implements ReportPreparer<long[]> {
        private final long length, bits, chunkLength;

        public Prio3SumVecReportPreparer(long length, long bits, long chunkLength) {
            this.length = length;
            this.bits = bits;
            this.chunkLength = chunkLength;
        }

        @Override
        public byte[] prepareReport(Client<long[]> client, HpkeConfigList leaderConfigList, HpkeConfigList helperConfigList, long[] measurement) {
            if (measurement != null) {
                // Copy the measurement array, so we can prevent data races while the Rust code
                // reads it.
                long[] measurementCopy = Arrays.copyOf(measurement, measurement.length);
                return this.prepareReportNative(
                        client.taskId.toBytes(),
                        leaderConfigList.bytes,
                        helperConfigList.bytes,
                        client.reportTimestamp(),
                        length,
                        bits,
                        chunkLength,
                        measurementCopy
                );
            } else {
                throw new IllegalArgumentException("measurement for Prio3SumVec must be a long[]");
            }
        }

        private native byte[] prepareReportNative(
                byte[] taskId,
                byte[] leaderHPKEConfigList,
                byte[] helperHPKEConfigList,
                long timestamp,
                long length,
                long bits,
                long chunkLength,
                long[] measurement
        );
    }

    private static class Prio3HistogramReportPreparer implements ReportPreparer<Long> {
        private final long length, chunkLength;

        public Prio3HistogramReportPreparer(long length, long chunkLength) {
            this.length = length;
            this.chunkLength = chunkLength;
        }

        @Override
        public byte[] prepareReport(Client<Long> client, HpkeConfigList leaderConfigList, HpkeConfigList helperConfigList, Long measurement) {
            if (measurement != null) {
                return this.prepareReportNative(
                        client.taskId.toBytes(),
                        leaderConfigList.bytes,
                        helperConfigList.bytes,
                        client.reportTimestamp(),
                        length,
                        chunkLength,
                        (Long) measurement
                );
            } else {
                throw new IllegalArgumentException("measurement for Prio3Histogram must be a Long");
            }
        }

        private native byte[] prepareReportNative(
                byte[] taskId,
                byte[] leaderHPKEConfigList,
                byte[] helperHPKEConfigList,
                long timestamp,
                long length,
                long chunkLength,
                long measurement
        );
    }
}
