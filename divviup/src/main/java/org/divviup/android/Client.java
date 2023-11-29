package org.divviup.android;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;

import okhttp3.Cache;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A client that can submit reports to a particular DAP task. Objects of this class are immutable,
 * and thus thread-safe.
 *
 * @param <M>   the type of measurements (determined by the VDAF)
 */
public class Client<M> {
    private static final String HPKE_CONFIG_LIST_CONTENT_TYPE = "application/dap-hpke-config-list";
    private static final MediaType REPORT_CONTENT_TYPE = MediaType.get("application/dap-report");
    private static final long DISK_CACHE_SIZE = 1024 * 100;
    private static OkHttpClient HTTP_CLIENT = null;

    static {
        System.loadLibrary("divviup_android");
    }

    private final URI leaderEndpoint, helperEndpoint;
    private final TaskId taskId;
    private final long timePrecisionSeconds;
    private final ReportPreparer<M> reportPreparer;
    private final OkHttpClient client;

    private Client(
            Context context,
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

        this.client = getHTTPClient(context);
    }

    private static synchronized OkHttpClient getHTTPClient(Context context) {
        // The same cache directory may not be used with multiple Cache instances, and OkHttpClient
        // has an internal connection pool, so we construct a singleton client object. It is not
        // necessary to shut down the client, as threads and connections will be cleaned up when
        // idle automatically.
        if (HTTP_CLIENT == null) {
            File cacheDir = new File(context.getCacheDir(), "divviup-http");
            Cache cache = new Cache(cacheDir, DISK_CACHE_SIZE);
            HTTP_CLIENT = new OkHttpClient.Builder().addNetworkInterceptor(chain -> {
                Request request = chain
                        .request()
                        .newBuilder()
                        .header("User-Agent", getUserAgent())
                        .build();
                return chain.proceed(request);
            }).cache(cache).build();
        }
        return HTTP_CLIENT;
    }

    /**
     * Constructs a client for a DAP task using the Prio3Count VDAF. Measurements are
     * <code>Boolean</code>s. The aggregate result is the number of <code>true</code> measurements.
     *
     * @param context                   the app's {@link Context}. This is used to access the cache
     *                                  directory.
     * @param leaderEndpoint            the URI of the leader aggregator's HTTPS endpoint
     * @param helperEndpoint            the URI of the helper aggregator's HTTPS endpoint
     * @param taskId                    the {@link TaskId} of the DAP task
     * @param timePrecisionSeconds      the time precision of the DAP task, in seconds
     * @return                          a client for the configured DAP task
     * @throws IllegalArgumentException if the scheme of leaderEndpoint or helperEndpoint is not
     *                                  http or https, or if timePrecisionSeconds is not a positive
     *                                  number
     */
    public static Client<Boolean> createPrio3Count(
            Context context,
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds
    ) {
        return new Client<>(
                context,
                leaderEndpoint,
                helperEndpoint,
                taskId,
                timePrecisionSeconds,
                new Prio3CountReportPreparer()
        );
    }

    /**
     * Constructs a client for a DAP task using the Prio3Sum VDAF. Measurements are <code>Long</code>
     * integers. Valid measurements must be greater than or equal to zero, and less than
     * <code>2 ^ bits</code>. The aggregate result is the sum of all measurements.
     *
     * @param context                   the app's {@link Context}. This is used to access the cache
     *                                  directory.
     * @param leaderEndpoint            the URI of the leader aggregator's HTTPS endpoint
     * @param helperEndpoint            the URI of the helper aggregator's HTTPS endpoint
     * @param taskId                    the {@link TaskId} of the DAP task
     * @param timePrecisionSeconds      the time precision of the DAP task, in seconds
     * @param bits                      the bit width of measurements. This is a parameter of the
     *                                  Prio3Sum VDAF.
     * @return                          a client for the configured DAP task
     * @throws IllegalArgumentException if the scheme of leaderEndpoint or helperEndpoint is not
     *                                  http or https, or if timePrecisionSeconds is not a positive
     *                                  number
     */
    public static Client<Long> createPrio3Sum(
            Context context,
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            long bits
    ) {
        return new Client<>(
                context,
                leaderEndpoint,
                helperEndpoint,
                taskId,
                timePrecisionSeconds,
                new Prio3SumReportPreparer(bits)
        );
    }

    /**
     * Constructs a client for a DAP task using the Prio3SumVec VDAF. Measurements are vectors of
     * integers, as a <code>long[]</code> array of the given length. Valid measurements must have
     * every integer be greater than or equal to zero, and less than <code>2 ^ bits</code>. The
     * aggregate result is the element-wise sum of all measurements.
     *
     * @param context                   the app's {@link Context}. This is used to access the cache
     *                                  directory.
     * @param leaderEndpoint            the URI of the leader aggregator's HTTPS endpoint
     * @param helperEndpoint            the URI of the helper aggregator's HTTPS endpoint
     * @param taskId                    the {@link TaskId} of the DAP task
     * @param timePrecisionSeconds      the time precision of the DAP task, in seconds
     * @param length                    the length of measurement vectors. This is a parameter of
     *                                  the Prio3SumVec VDAF.
     * @param bits                      the bit width of each element of the measurement vector.
     *                                  This is a parameter of the Prio3SumVec VDAF.
     * @param chunkLength               the chunk length internally used by the Prio3SumVec VDAF
     * @return                          a client for the configured DAP task
     * @throws IllegalArgumentException if the scheme of leaderEndpoint or helperEndpoint is not
     *                                  http or https, or if timePrecisionSeconds is not a positive
     *                                  number
     */
    public static Client<long[]> createPrio3SumVec(
            Context context,
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            long length,
            long bits,
            long chunkLength
    ) {
        return new Client<>(
                context,
                leaderEndpoint,
                helperEndpoint,
                taskId,
                timePrecisionSeconds,
                new Prio3SumVecReportPreparer(length, bits, chunkLength)
        );
    }

    /**
     * Constructs a client for a DAP task using the Prio3Histogram VDAF. Measurements are bucket
     * indexes, as <code>Long</code> integers. Valid measurements must be greater than or equal to
     * zero, and less than the <code>length</code> parameter. The aggregate result counts how many
     * times each bucket index appeared in a measurement.
     *
     * @param context                   the app's {@link Context}. This is used to access the cache
     *                                  directory.
     * @param leaderEndpoint            the URI of the leader aggregator's HTTPS endpoint
     * @param helperEndpoint            the URI of the helper aggregator's HTTPS endpoint
     * @param taskId                    the {@link TaskId} of the DAP task
     * @param timePrecisionSeconds      the time precision of the DAP task, in seconds
     * @param length                    the total number of histogram buckets. This is a parameter
     *                                  of the Prio3Histogram VDAF.
     * @param chunkLength               the chunk length internally used by the Prio3Histogram VDAF
     * @return                          a client for the configured DAP task
     * @throws IllegalArgumentException if the scheme of leaderEndpoint or helperEndpoint is not
     *                                  http or https, or if timePrecisionSeconds is not a positive
     *                                  number
     */
    public static Client<Long> createPrio3Histogram(
            Context context,
            URI leaderEndpoint,
            URI helperEndpoint,
            TaskId taskId,
            long timePrecisionSeconds,
            long length,
            long chunkLength
    ) {
        return new Client<>(
                context,
                leaderEndpoint,
                helperEndpoint,
                taskId,
                timePrecisionSeconds,
                new Prio3HistogramReportPreparer(length, chunkLength)
        );
    }

    /**
     * Encodes a measurement into a DAP report, and submits it. This must not be called from the UI
     * thread.
     *
     * @param measurement               the measurement to be aggregated
     * @throws IOException              if requests to either aggregator fail
     * @throws IllegalArgumentException if the measurement is of the wrong type
     * @throws RuntimeException         if there is an internal error while preparing the report
     */
    public void sendMeasurement(M measurement) throws IOException {
        HpkeConfigList leaderConfigList = this.fetchHPKEConfigList(this.leaderEndpoint, this.taskId);
        HpkeConfigList helperConfigList = this.fetchHPKEConfigList(this.helperEndpoint, this.taskId);
        byte[] report = reportPreparer.prepareReport(this, leaderConfigList, helperConfigList, measurement);

        String path = "tasks/" + this.taskId.encodeToString() + "/reports";
        URL url = leaderEndpoint.resolve(path).toURL();
        RequestBody body = RequestBody.create(report, REPORT_CONTENT_TYPE);
        Request request = new Request.Builder().url(url).put(body).build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            if (code >= 400) {
                throw new IOException(
                        "aggregator returned HTTP response code " + code + " when uploading report"
                );
            }
        }
    }

    private HpkeConfigList fetchHPKEConfigList(URI aggregatorEndpoint, TaskId taskId) throws IOException {
        String path = "hpke_config?task_id=" + taskId.encodeToString();
        URL url = aggregatorEndpoint.resolve(path).toURL();
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            int code = response.code();
            if (code >= 400) {
                throw new IOException(
                        "aggregator returned HTTP response code " + code + " when fetching HPKE configs"
                );
            }
            String contentType = response.header("Content-Type");
            if (contentType == null) {
                throw new IOException("no content type header in HPKE configs response");
            }
            if (!contentType.equals(HPKE_CONFIG_LIST_CONTENT_TYPE)) {
                throw new IOException("wrong content type for HPKE configs: " + contentType);
            }
            ResponseBody body = response.body();
            // This assertion is OK because we got this response from execute(), and we only
            // retrieve the body once.
            assert body != null;
            byte[] data = body.bytes();
            return new HpkeConfigList(data);
        }
    }

    private static String getUserAgent() {
        return "divviup-android/" + BuildConfig.VERSION;
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
                        measurement
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
                        measurement
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
                        measurement
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
