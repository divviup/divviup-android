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

    @Test
    public void smokeTestPrio3Count() throws IOException, InterruptedException {
        Buffer hpkeConfigListBuffer;
        ClassLoader classLoader = Objects.requireNonNull(this.getClass().getClassLoader());
        try (InputStream is = classLoader.getResourceAsStream("hpke_config_list.bin")) {
            hpkeConfigListBuffer = new Buffer();
            hpkeConfigListBuffer.readFrom(is);
        }

        try (MockWebServer server = new MockWebServer()) {
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

            URI uri = server.url("/").uri();
            TaskId taskId = TaskId.parse("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            Client<Boolean> client = Client.createPrio3Count(uri, uri, taskId, 300);
            client.sendMeasurement(true);

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
}
