package org.divviup.android;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Calendar;

public class Client {
    private static final String HPKE_CONFIG_LIST_CONTENT_TYPE = "application/dap-hpke-config-list";
    private static final String REPORT_CONTENT_TYPE = "application/dap-report";

    private static boolean isLibraryLoaded = false;

    private final URI leaderEndpoint, helperEndpoint;
    private final TaskId taskId;
    private final long timePrecisionSeconds;
    private final VDAF vdaf;


    private Client(
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            VDAF vdaf
    ) {
        if (!isLibraryLoaded) {
            // Load the native library upon first use.
            System.loadLibrary("divviup_android");
            isLibraryLoaded = true;
        }

        if (timePrecisionSeconds <= 0) {
            throw new IllegalArgumentException("timePrecisionSeconds must be positive");
        }

        this.leaderEndpoint = leaderEndpoint;
        this.helperEndpoint = helperEndpoint;
        this.taskId = taskId;
        this.timePrecisionSeconds = timePrecisionSeconds;
        this.vdaf = vdaf;
    }

    public static Client createPrio3Count(
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds
    ) {
        return new Client(leaderEndpoint, helperEndpoint, taskId, timePrecisionSeconds, VDAF.PRIO3COUNT);
    }

    public void sendMeasurement(Object measurement) throws IOException {
        HPKEConfigList leaderConfigList = this.fetchHPKEConfigList(this.leaderEndpoint, this.taskId);
        HPKEConfigList helperConfigList = this.fetchHPKEConfigList(this.helperEndpoint, this.taskId);
        long timestamp = this.reportTimestamp();
        byte[] report = null;
        if (this.vdaf == VDAF.PRIO3COUNT) {
            if (measurement instanceof Boolean) {
                report = this.prepareReportPrio3Count(
                        taskId.toBytes(),
                        leaderConfigList.bytes,
                        helperConfigList.bytes,
                        timestamp,
                        (Boolean)measurement
                );
            } else {
                throw new IllegalArgumentException("measurement for Prio3Count must be a Boolean");
            }
        }
        if (report == null) {
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

    private HPKEConfigList fetchHPKEConfigList(URI aggregatorEndpoint, TaskId taskId) throws IOException {
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
        return new HPKEConfigList(data);
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
        long seconds = Calendar.getInstance().getTimeInMillis() / 1000L;
        return seconds - (seconds % timePrecisionSeconds);
    }

    private static class HPKEConfigList {
        private final byte[] bytes;

        public HPKEConfigList(byte[] bytes) {
            this.bytes = bytes;
        }
    }

    private enum VDAF {
        PRIO3COUNT
    }
}
