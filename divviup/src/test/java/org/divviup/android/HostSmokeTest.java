package org.divviup.android;

import static org.junit.Assert.*;

import org.divviup.commontest.MockAggregator;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class HostSmokeTest {
    private static final TaskId ZERO_TASK_ID = TaskId.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

    @Test
    public void smokeTestPrio3Count() throws IOException, InterruptedException {
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Boolean> client = Client.createPrio3Count(uri, uri, ZERO_TASK_ID, 300);
            client.sendMeasurement(true);

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3Sum() throws IOException, InterruptedException {
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Long> client = Client.createPrio3Sum(uri, uri, ZERO_TASK_ID, 300, 32);
            client.sendMeasurement(1000000L);

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3SumVec() throws IOException, InterruptedException {
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<long[]> client = Client.createPrio3SumVec(uri, uri, ZERO_TASK_ID, 300, 10, 8, 12);
            client.sendMeasurement(new long[] {252L, 7L, 80L, 194L, 190L, 217L, 141L, 85L, 222L, 243L});

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3Histogram() throws IOException, InterruptedException {
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Long> client = Client.createPrio3Histogram(uri, uri, ZERO_TASK_ID, 300, 5, 2);
            client.sendMeasurement(2L);

            basicUploadChecks(server);
        }
    }

    private static void basicUploadChecks(MockWebServer server) throws InterruptedException {
        RecordedRequest r1 = server.takeRequest();
        assertEquals(r1.getMethod(), "GET");
        assertEquals(r1.getPath(), "/hpke_config?task_id=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        RecordedRequest r2 = server.takeRequest();
        assertEquals(r2.getMethod(), "GET");
        assertEquals(r2.getPath(), "/hpke_config?task_id=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        RecordedRequest r3 = server.takeRequest();
        assertEquals(r3.getMethod(), "PUT");
        assertEquals(r3.getPath(), "/tasks/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/reports");
        assertEquals(r3.getHeader("Content-Type"), "application/dap-report");
        assertTrue(r3.getBody().size() > 0);
    }
}
