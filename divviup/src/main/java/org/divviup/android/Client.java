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
    private final long timePrecisionSeconds, length, bits, chunkLength;
    private final Vdaf vdaf;


    private Client(
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            Vdaf vdaf,
            long length,
            long bits,
            long chunkLength
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
        this.vdaf = vdaf;
        this.length = length;
        this.bits = bits;
        this.chunkLength = chunkLength;
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
                Vdaf.PRIO3COUNT,
                0,
                0,
                0
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
                Vdaf.PRIO3SUM,
                0,
                bits,
                0
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
                Vdaf.PRIO3SUMVEC,
                length,
                bits,
                chunkLength
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
                Vdaf.PRIO3HISTOGRAM,
                length,
                0,
                chunkLength
        );
    }

    public void sendMeasurement(M measurement) throws IOException {
        HpkeConfigList leaderConfigList = this.fetchHPKEConfigList(this.leaderEndpoint, this.taskId);
        HpkeConfigList helperConfigList = this.fetchHPKEConfigList(this.helperEndpoint, this.taskId);
        long timestamp = this.reportTimestamp();
        byte[] report;
        switch (vdaf) {
            case PRIO3COUNT:
                if (measurement instanceof Boolean) {
                    report = this.prepareReportPrio3Count(
                            taskId.toBytes(),
                            leaderConfigList.bytes,
                            helperConfigList.bytes,
                            timestamp,
                            (Boolean) measurement
                    );
                } else {
                    throw new IllegalArgumentException("measurement for Prio3Count must be a Boolean");
                }
                break;

            case PRIO3SUM:
                if (measurement instanceof Long) {
                    report = this.prepareReportPrio3Sum(
                            taskId.toBytes(),
                            leaderConfigList.bytes,
                            helperConfigList.bytes,
                            timestamp,
                            bits,
                            (Long) measurement
                    );
                } else {
                    throw new IllegalArgumentException("measurement for Prio3Sum must be a Long");
                }
                break;

            case PRIO3SUMVEC:
                if (measurement instanceof long[]) {
                    long[] measurementArray = (long[]) measurement;
                    // Copy the measurement array, so we can prevent data races while the Rust code
                    // reads it.
                    long[] measurementCopy = Arrays.copyOf(measurementArray, measurementArray.length);
                    report = this.prepareReportPrio3SumVec(
                            taskId.toBytes(),
                            leaderConfigList.bytes,
                            helperConfigList.bytes,
                            timestamp,
                            length,
                            bits,
                            chunkLength,
                            measurementCopy
                    );
                } else {
                    throw new IllegalArgumentException("measurement for Prio3SumVec must be a long[]");
                }
                break;

            case PRIO3HISTOGRAM:
                if (measurement instanceof Long) {
                    report = this.prepareReportPrio3Histogram(
                            taskId.toBytes(),
                            leaderConfigList.bytes,
                            helperConfigList.bytes,
                            timestamp,
                            length,
                            chunkLength,
                            (Long) measurement
                    );
                } else {
                    throw new IllegalArgumentException("measurement for Prio3Histogram must be a Long");
                }
                break;

            default:
                throw new IllegalArgumentException("unsupported VDAF");
        }

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

    private native byte[] prepareReportPrio3Count(
            byte[] taskId,
            byte[] leaderHPKEConfigList,
            byte[] helperHPKEConfigList,
            long timestamp,
            boolean measurement
    );

    private native byte[] prepareReportPrio3Sum(
            byte[] taskId,
            byte[] leaderHPKEConfigList,
            byte[] helperHPKEConfigList,
            long timestamp,
            long bits,
            long measurement
    );

    private native byte[] prepareReportPrio3SumVec(
            byte[] taskId,
            byte[] leaderHPKEConfigList,
            byte[] helperHPKEConfigList,
            long timestamp,
            long length,
            long bits,
            long chunkLength,
            long[] measurement
    );

    private native byte[] prepareReportPrio3Histogram(
            byte[] taskId,
            byte[] leaderHPKEConfigList,
            byte[] helperHPKEConfigList,
            long timestamp,
            long length,
            long chunkLength,
            long measurement
    );

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

    private enum Vdaf {
        PRIO3COUNT, PRIO3SUM, PRIO3SUMVEC, PRIO3HISTOGRAM
    }
}
