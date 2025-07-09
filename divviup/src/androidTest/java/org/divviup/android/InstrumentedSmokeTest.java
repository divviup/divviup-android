package org.divviup.android;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.divviup.commontest.MockAggregator;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import android.content.Context;

import java.io.IOException;
import java.net.URI;

import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;

@RunWith(AndroidJUnit4.class)
public class InstrumentedSmokeTest {
    private static final TaskId ZERO_TASK_ID = TaskId.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

    @Test
    public void smokeTestPrio3Count() throws IOException, InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Boolean> client = Client.createPrio3Count(context, uri, uri, ZERO_TASK_ID, 300);
            client.sendMeasurement(true);

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3Sum() throws IOException, InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Long> client = Client.createPrio3Sum(context, uri, uri, ZERO_TASK_ID, 300, 32);
            client.sendMeasurement(1000000L);

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3SumVec() throws IOException, InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<long[]> client = Client.createPrio3SumVec(context, uri, uri, ZERO_TASK_ID, 300, 10, 8, 12);
            client.sendMeasurement(new long[] {252L, 7L, 80L, 194L, 190L, 217L, 141L, 85L, 222L, 243L});

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3Histogram() throws IOException, InterruptedException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (MockWebServer server = MockAggregator.setupMockServer()) {
            URI uri = server.url("/").uri();
            Client<Long> client = Client.createPrio3Histogram(context, uri, uri, ZERO_TASK_ID, 300, 5, 2);
            client.sendMeasurement(2L);

            basicUploadChecks(server);
        }
    }

    private static void basicUploadChecks(MockWebServer server) throws InterruptedException {
        RecordedRequest r1 = server.takeRequest();
        assertEquals("GET", r1.getMethod());
        assertEquals("/hpke_config", r1.getUrl().encodedPath());
        assertEquals("task_id=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", r1.getUrl().encodedQuery());

        RecordedRequest r2 = server.takeRequest();
        assertEquals("GET", r2.getMethod());
        assertEquals("/hpke_config", r2.getUrl().encodedPath());
        assertEquals("task_id=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", r2.getUrl().encodedQuery());

        RecordedRequest r3 = server.takeRequest();
        assertEquals("PUT", r3.getMethod());
        assertEquals("/tasks/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/reports", r3.getUrl().encodedPath());
        assertEquals("application/dap-report", r3.getHeaders().get("Content-Type"));
        assertTrue(r3.getBody().size() > 0);
    }
}
