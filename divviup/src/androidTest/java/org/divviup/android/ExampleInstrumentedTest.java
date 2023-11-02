package org.divviup.android;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("org.divviup.android.test", appContext.getPackageName());
    }

    private static Buffer loadHpkeConfigList() throws IOException {
        Buffer hpkeConfigListBuffer;
        ClassLoader classLoader = Objects.requireNonNull(ExampleInstrumentedTest.class.getClassLoader());
        try (InputStream is = classLoader.getResourceAsStream("hpke_config_list.bin")) {
            hpkeConfigListBuffer = new Buffer();
            hpkeConfigListBuffer.readFrom(is);
        }
        return hpkeConfigListBuffer;
    }

    private static MockWebServer setupMockServer() throws IOException {
        Buffer hpkeConfigListBuffer = loadHpkeConfigList();
        MockWebServer server = new MockWebServer();
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/dap-hpke-config-list")
                        .setBody(hpkeConfigListBuffer)
        );
        server.enqueue(
                new MockResponse()
                        .setHeader("Content-Type", "application/dap-hpke-config-list")
                        .setBody(hpkeConfigListBuffer)
        );
        server.enqueue(new MockResponse());
        server.start();
        return server;
    }

    @Test
    public void smokeTestPrio3Count() throws IOException, InterruptedException {
        try (MockWebServer server = setupMockServer()) {
            URI uri = server.url("/").uri();
            TaskId taskId = TaskId.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            Client<Boolean> client = Client.createPrio3Count(uri, uri, taskId, 300);
            client.sendMeasurement(true);

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3Sum() throws IOException, InterruptedException {
        try (MockWebServer server = setupMockServer()) {
            URI uri = server.url("/").uri();
            TaskId taskId = TaskId.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            Client<Long> client = Client.createPrio3Sum(uri, uri, taskId, 300, 32);
            client.sendMeasurement(1000000L);

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3SumVec() throws IOException, InterruptedException {
        try (MockWebServer server = setupMockServer()) {
            URI uri = server.url("/").uri();
            TaskId taskId = TaskId.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            Client<long[]> client = Client.createPrio3SumVec(uri, uri, taskId, 300, 10, 8, 12);
            client.sendMeasurement(new long[] {252L, 7L, 80L, 194L, 190L, 217L, 141L, 85L, 222L, 243L});

            basicUploadChecks(server);
        }
    }

    @Test
    public void smokeTestPrio3Histogram() throws IOException, InterruptedException {
        try (MockWebServer server = setupMockServer()) {
            URI uri = server.url("/").uri();
            TaskId taskId = TaskId.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            Client<Long> client = Client.createPrio3Histogram(uri, uri, taskId, 300, 5, 2);
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
