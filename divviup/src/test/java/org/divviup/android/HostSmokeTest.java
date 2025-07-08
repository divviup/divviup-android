package org.divviup.android;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import android.content.Context;

import org.divviup.commontest.MockAggregator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.URI;

import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

@RunWith(MockitoJUnitRunner.class)
public class HostSmokeTest {
    private static final TaskId ZERO_TASK_ID = TaskId.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

    @ClassRule
    public static final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock(strictness = Mock.Strictness.LENIENT)
    private static Context mockContext;

    @Before
    public void initMock() {
        when(mockContext.getCacheDir()).thenReturn(temporaryFolder.getRoot());
    }

    @Test
    public void smokeTestPrio3Count() throws IOException, InterruptedException {
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Boolean> client = Client.createPrio3Count(mockContext, uri, uri, ZERO_TASK_ID, 300);
            client.sendMeasurement(true);

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3Sum() throws IOException, InterruptedException {
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Long> client = Client.createPrio3Sum(mockContext, uri, uri, ZERO_TASK_ID, 300, 32);
            client.sendMeasurement(1000000L);

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3SumVec() throws IOException, InterruptedException {
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<long[]> client = Client.createPrio3SumVec(mockContext, uri, uri, ZERO_TASK_ID, 300, 10, 8, 12);
            client.sendMeasurement(new long[] {252L, 7L, 80L, 194L, 190L, 217L, 141L, 85L, 222L, 243L});

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3Histogram() throws IOException, InterruptedException {
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Long> client = Client.createPrio3Histogram(mockContext, uri, uri, ZERO_TASK_ID, 300, 5, 2);
            client.sendMeasurement(2L);

            basicUploadChecks(server);
        }
    }

    private static void basicUploadChecks(MockWebServer server) throws InterruptedException {
        RecordedRequest r1 = server.takeRequest();
        assertEquals(r1.getMethod(), "GET");
        assertEquals(r1.getUrl().encodedPath(), "/hpke_config");
        assertEquals(r1.getUrl().encodedQuery(), "task_id=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        RecordedRequest r2 = server.takeRequest();
        assertEquals(r2.getMethod(), "GET");
        assertEquals(r2.getUrl().encodedPath(), "/hpke_config");
        assertEquals(r2.getUrl().encodedQuery(), "task_id=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        RecordedRequest r3 = server.takeRequest();
        assertEquals(r3.getMethod(), "PUT");
        assertEquals(r3.getUrl().encodedPath(), "/tasks/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/reports");
        assertEquals(r3.getHeaders().get("Content-Type"), "application/dap-report");
        assertTrue(r3.getBody().size() > 0);
    }
}
