package org.divviup.android;

import static org.junit.Assert.*;

import android.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.dockerjava.api.DockerClient;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class JanusIntegrationTest {
    /** @noinspection SpellCheckingInspection */
    private static final DockerImageName JANUS_INTEROP_AGGREGATOR = DockerImageName.parse(
            "us-west2-docker.pkg.dev/divviup-artifacts-public/janus/janus_interop_aggregator@sha256:8cc873f7a8be459fe2dbecdf78561806b514ac98b4d644dc9a7f6bb25bb9df02"
    );
    /** @noinspection SpellCheckingInspection */
    private static final DockerImageName JANUS_INTEROP_COLLECTOR = DockerImageName.parse(
            "us-west2-docker.pkg.dev/divviup-artifacts-public/janus/janus_interop_collector@sha256:982110bc29842639355830339b95fac77432cbbcc28df0cd07daf91551570602"
    );
    private static final int BASE64_FLAGS = Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE;
    private static final int TIME_PRECISION_SECONDS = 3600;
    private static final String LEADER_ALIAS = "leader", HELPER_ALIAS = "helper";

    private GenericContainer<?> leader, helper, collector;

    /** @noinspection resource */
    @Before
    public void setUp() {
        Network network = Network.newNetwork();
        leader = new GenericContainer<>(JANUS_INTEROP_AGGREGATOR)
                .withNetwork(network)
                .withNetworkAliases(LEADER_ALIAS)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/internal/test/ready").withMethod("POST"));
        helper = new GenericContainer<>(JANUS_INTEROP_AGGREGATOR)
                .withNetwork(network)
                .withNetworkAliases(HELPER_ALIAS)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/internal/test/ready").withMethod("POST"));
        collector = new GenericContainer<>(JANUS_INTEROP_COLLECTOR)
                .withNetwork(network)
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/internal/test/ready").withMethod("POST"));
        Startables.deepStart(leader, helper, collector).join();
    }

    @After
    public void after() {
        if (leader != null)
            leader.stop();
        if (helper != null)
            helper.stop();
        if (collector != null)
            collector.stop();
    }

    @Test
    public void testPrio3Count() throws URISyntaxException, IOException, InteropApiException, InterruptedException, NoSuchAlgorithmException {
        ObjectNode vdaf = JsonNodeFactory.instance.objectNode();
        vdaf.set("type", JsonNodeFactory.instance.textNode("Prio3Count"));
        runIntegrationTest(
                (leaderUri, helperUri, taskId) -> Client.createPrio3Count(leaderUri, helperUri, taskId, TIME_PRECISION_SECONDS),
                vdaf,
                new Boolean[] { true, true, true, true, false, false, false, false, false, false },
                new TextNode("4")
        );
    }

    @Test
    public void testPrio3Sum() throws URISyntaxException, IOException, InteropApiException, InterruptedException, NoSuchAlgorithmException {
        ObjectNode vdaf = JsonNodeFactory.instance.objectNode();
        vdaf.set("type", JsonNodeFactory.instance.textNode("Prio3Sum"));
        vdaf.set("bits", JsonNodeFactory.instance.textNode("16"));
        runIntegrationTest(
                (leaderUri, helperUri, taskId) -> Client.createPrio3Sum(leaderUri, helperUri, taskId, TIME_PRECISION_SECONDS, 16),
                vdaf,
                new Long[] { 31865L, 42987L, 30615L, 504L, 30113L },
                new TextNode("136084")
        );
    }

    @Test
    public void testPrio3SumVec() throws URISyntaxException, IOException, InteropApiException, InterruptedException, NoSuchAlgorithmException {
        ObjectNode vdaf = JsonNodeFactory.instance.objectNode();
        vdaf.set("type", JsonNodeFactory.instance.textNode("Prio3SumVec"));
        vdaf.set("length", JsonNodeFactory.instance.textNode("3"));
        vdaf.set("bits", JsonNodeFactory.instance.textNode("8"));
        vdaf.set("chunk_length", JsonNodeFactory.instance.textNode("4"));
        ArrayNode expectedResult = JsonNodeFactory.instance.arrayNode(3);
        expectedResult.add("845");
        expectedResult.add("449");
        expectedResult.add("711");
        runIntegrationTest(
                (leaderUri, helperUri, taskId) -> Client.createPrio3SumVec(leaderUri, helperUri, taskId, TIME_PRECISION_SECONDS, 3, 8, 4),
                vdaf,
                new long[][] {
                        { 178, 26, 198 },
                        { 197, 52, 146 },
                        { 205, 139, 137 },
                        { 215, 224, 14 },
                        { 50, 8, 216 }
                },
                expectedResult
        );
    }

    @Test
    public void testPrio3Histogram() throws URISyntaxException, IOException, InteropApiException, InterruptedException, NoSuchAlgorithmException {
        ObjectNode vdaf = JsonNodeFactory.instance.objectNode();
        vdaf.set("type", JsonNodeFactory.instance.textNode("Prio3Histogram"));
        vdaf.set("length", JsonNodeFactory.instance.textNode("4"));
        vdaf.set("chunk_length", JsonNodeFactory.instance.textNode("2"));
        ArrayNode expectedResult = JsonNodeFactory.instance.arrayNode(4);
        expectedResult.add("1");
        expectedResult.add("1");
        expectedResult.add("2");
        expectedResult.add("4");
        runIntegrationTest(
                (leaderUri, helperUri, taskId) -> Client.createPrio3Histogram(leaderUri, helperUri, taskId, TIME_PRECISION_SECONDS, 4, 2),
                vdaf,
                new Long[] { 2L, 3L, 3L, 3L, 1L, 0L, 3L, 2L },
                expectedResult
        );
    }

    private <M> void runIntegrationTest(
            ClientConstructor<M> clientConstructor,
            JsonNode vdaf,
            M[] measurements,
            JsonNode expectedResult
    ) throws URISyntaxException, IOException, InteropApiException, InterruptedException, NoSuchAlgorithmException {
        // Prepare task parameters
        URI leaderUriHost = new URI("http", null, leader.getHost(), leader.getFirstMappedPort(), "/", null, null);
        URI helperUriHost = new URI("http", null, helper.getHost(), helper.getFirstMappedPort(), "/", null, null);
        URI collectorUriHost = new URI("http", null, collector.getHost(), collector.getFirstMappedPort(), "/", null, null);
        URI leaderUriDocker = new URI("http", null, LEADER_ALIAS, 8080, "/", null, null);
        URI helperUriDocker = new URI("http", null, HELPER_ALIAS, 8080, "/", null, null);
        TaskId taskId = randomTaskId();
        String aggregatorAuthToken = randomAuthToken("aggregator-");
        String collectorAuthToken = randomAuthToken("collector-");
        String encodedVdafVerifyKey = randomEncodedVdafVerifyKey();
        int maxBatchQueryCount = 1;
        int batchSize = measurements.length;
        int taskExpirationTimestamp = Integer.MAX_VALUE;
        int queryType = 2; // fixed size

        AggregatorInteropApi leaderApi = new AggregatorInteropApi(leaderUriHost);
        AggregatorInteropApi helperApi = new AggregatorInteropApi(helperUriHost);
        CollectorInteropApi collectorApi = new CollectorInteropApi(collectorUriHost);

        JsonNode result;
        try {
            // Provision task into collector
            String encodedCollectorHpkeConfig = collectorApi.addTask(
                    taskId,
                    leaderUriDocker,
                    vdaf,
                    collectorAuthToken,
                    queryType
            );

            // Provision task into leader
            leaderApi.addTask(
                    taskId,
                    "leader",
                    leaderUriDocker,
                    helperUriDocker,
                    vdaf,
                    aggregatorAuthToken,
                    collectorAuthToken,
                    encodedVdafVerifyKey,
                    maxBatchQueryCount,
                    batchSize,
                    batchSize,
                    encodedCollectorHpkeConfig,
                    taskExpirationTimestamp,
                    queryType
            );

            // Provision task into helper
            helperApi.addTask(
                    taskId,
                    "helper",
                    leaderUriDocker,
                    helperUriDocker,
                    vdaf,
                    aggregatorAuthToken,
                    null,
                    encodedVdafVerifyKey,
                    maxBatchQueryCount,
                    batchSize,
                    batchSize,
                    encodedCollectorHpkeConfig,
                    taskExpirationTimestamp,
                    queryType
            );

            // Send measurements
            Client<M> client = clientConstructor.construct(leaderUriHost, helperUriHost, taskId);
            for (M measurement : measurements) {
                client.sendMeasurement(measurement);
            }

            // Start collection
            ObjectNode query = JsonNodeFactory.instance.objectNode();
            query.set("type", JsonNodeFactory.instance.numberNode(queryType));
            query.set("subtype", JsonNodeFactory.instance.numberNode(1)); // current_batch

            result = null;
            for (int startAttempt = 0; startAttempt < 5; startAttempt++) {
                try {
                    String handle = collectorApi.startCollection(taskId, new byte[0], query);

                    for (int pollAttempt = 0; pollAttempt < 30; pollAttempt++) {
                        result = collectorApi.pollCollection(handle);
                        if (result != null && result != NullNode.getInstance()) {
                            break;
                        }
                        Thread.sleep(1000);
                    }
                    break;
                } catch (InteropApiException e) {
                    Thread.sleep(1000);
                }
            }
        } finally {
            propagateLogs(leader, "leader");
            propagateLogs(helper, "helper");
            propagateLogs(collector, "collector");
        }

        assertNotNull(result);
        assertEquals(expectedResult, result);
    }

    private static TaskId randomTaskId() throws NoSuchAlgorithmException {
        byte[] bytes = SecureRandom.getInstanceStrong().generateSeed(32);
        String encoded = Base64.encodeToString(bytes, BASE64_FLAGS);
        return TaskId.parse(encoded);
    }

    private static String randomAuthToken(String prefix) throws NoSuchAlgorithmException {
        byte[] bytes = SecureRandom.getInstanceStrong().generateSeed(16);
        return prefix + Base64.encodeToString(bytes, BASE64_FLAGS);
    }

    private static String randomEncodedVdafVerifyKey() throws NoSuchAlgorithmException {
        byte[] bytes = SecureRandom.getInstanceStrong().generateSeed(16);
        return Base64.encodeToString(bytes, BASE64_FLAGS);
    }

    private static void propagateLogs(GenericContainer<?> container, String label) throws IOException {
        String id = container.getContainerId();
        DockerClient dockerClient = container.getDockerClient();
        InputStream inputStream = dockerClient.copyArchiveFromContainerCmd(id, "logs").exec();
        TarArchiveInputStream tarInputStream = new TarArchiveInputStream(inputStream);
        for (TarArchiveEntry entry = tarInputStream.getNextTarEntry(); entry != null; entry = tarInputStream.getNextTarEntry()) {
            if (entry.isFile()) {
                Logger logger = LoggerFactory.getLogger("org.divviup.interop/" + label + "/" + entry.getName());
                BufferedReader reader = new BufferedReader(new InputStreamReader(tarInputStream));
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    logger.info(line);
                }
            }
        }
    }

    private interface ClientConstructor<M> {
        Client<M> construct(URI leaderUri, URI helperUri, TaskId taskId);
    }

    private static class InteropApi {
        private final URI endpoint;

        InteropApi(URI endpoint) {
            this.endpoint = endpoint;
        }

        JsonNode makeRequest(String path, JsonNode body) throws IOException, InteropApiException {
            URL url = endpoint.resolve(path).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.connect();
            OutputStream os = connection.getOutputStream();
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(os, body);
            os.close();
            int code = connection.getResponseCode();
            if (code >= 400) {
                InputStream is = connection.getErrorStream();
                String responseBody = IOUtils.toString(is, StandardCharsets.UTF_8);
                throw new IOException("got HTTP response code " + code + " from " + url + ", body: " + responseBody);
            }
            InputStream is = connection.getInputStream();
            JsonNode response = mapper.readTree(is);
            JsonNode status = response.get("status");
            if (status == null) {
                throw new IOException("status key is missing from response to " + url + ": " + response);
            }
            String statusText = status.asText();
            if (!statusText.equals("success") && !statusText.equals("complete") && !statusText.equals("in progress")) {
                throw new InteropApiException("bad status in response to " + url + ": " + response);
            }
            return response;
        }
    }

    private static class AggregatorInteropApi extends InteropApi {
        AggregatorInteropApi(URI endpoint) {
            super(endpoint);
        }

        void addTask(
                TaskId taskId,
                String role,
                URI leaderEndpoint,
                URI helperEndpoint,
                JsonNode vdaf,
                String aggregatorAuthToken,
                String collectorAuthToken,
                String encodedVdafVerifyKey,
                int maxBatchQueryCount,
                int minBatchSize,
                int maxBatchSize,
                String encodedCollectorHpkeConfig,
                int taskExpirationTimestamp,
                int queryType
        ) throws IOException, InteropApiException {
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.set("task_id", JsonNodeFactory.instance.textNode(taskId.encodeToString()));
            body.set("leader", JsonNodeFactory.instance.textNode(leaderEndpoint.toString()));
            body.set("helper", JsonNodeFactory.instance.textNode(helperEndpoint.toString()));
            body.set("vdaf", vdaf);
            body.set("leader_authentication_token", JsonNodeFactory.instance.textNode(aggregatorAuthToken));
            if (collectorAuthToken != null) {
                body.set("collector_authentication_token", JsonNodeFactory.instance.textNode(collectorAuthToken));
            }
            body.set("role", JsonNodeFactory.instance.textNode(role));
            body.set("vdaf_verify_key", JsonNodeFactory.instance.textNode(encodedVdafVerifyKey));
            body.set("max_batch_query_count", JsonNodeFactory.instance.numberNode(maxBatchQueryCount));
            body.set("query_type", JsonNodeFactory.instance.numberNode(queryType));
            body.set("min_batch_size", JsonNodeFactory.instance.numberNode(minBatchSize));
            if (maxBatchSize > 0) {
                body.set("max_batch_size", JsonNodeFactory.instance.numberNode(maxBatchSize));
            }
            body.set("time_precision", JsonNodeFactory.instance.numberNode(TIME_PRECISION_SECONDS));
            body.set("collector_hpke_config", JsonNodeFactory.instance.textNode(encodedCollectorHpkeConfig));
            body.set("task_expiration", JsonNodeFactory.instance.numberNode(taskExpirationTimestamp));
            makeRequest("internal/test/add_task", body);
        }
    }

    private static class CollectorInteropApi extends InteropApi {
        CollectorInteropApi(URI endpoint) {
            super(endpoint);
        }

        String addTask(
                TaskId taskId,
                URI leaderEndpoint,
                JsonNode vdaf,
                String collectorAuthToken,
                int queryType
        ) throws IOException, InteropApiException {
            System.out.println(leaderEndpoint);
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.set("task_id", JsonNodeFactory.instance.textNode(taskId.encodeToString()));
            body.set("leader", JsonNodeFactory.instance.textNode(leaderEndpoint.toString()));
            body.set("vdaf", vdaf);
            body.set("collector_authentication_token", JsonNodeFactory.instance.textNode(collectorAuthToken));
            body.set("query_type", JsonNodeFactory.instance.numberNode(queryType));
            JsonNode response = makeRequest("internal/test/add_task", body);
            return response.get("collector_hpke_config").asText();
        }

        String startCollection(
                TaskId taskId,
                byte[] aggregationParam,
                JsonNode query
        ) throws IOException, InteropApiException {
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.set("task_id", JsonNodeFactory.instance.textNode(taskId.encodeToString()));
            body.set("agg_param", JsonNodeFactory.instance.textNode(Base64.encodeToString(aggregationParam, BASE64_FLAGS)));
            body.set("query", query);
            JsonNode response = makeRequest("internal/test/collection_start", body);
            return response.get("handle").asText();
        }

        JsonNode pollCollection(String handle) throws IOException, InteropApiException {
            ObjectNode body = JsonNodeFactory.instance.objectNode();
            body.set("handle", JsonNodeFactory.instance.textNode(handle));
            JsonNode response = makeRequest("internal/test/collection_poll", body);
            return response.get("result");
        }
    }

    private static class InteropApiException extends Exception {
        public InteropApiException(String message) {
            super(message);
        }
    }
}
