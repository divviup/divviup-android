package org.divviup.commontest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.Headers;
import okio.Buffer;

public class MockAggregator {
    private static Buffer loadHpkeConfigList() throws IOException {
        Buffer hpkeConfigListBuffer;
        ClassLoader classLoader = Objects.requireNonNull(MockAggregator.class.getClassLoader());
        try (InputStream is = classLoader.getResourceAsStream("hpke_config_list.bin")) {
            hpkeConfigListBuffer = new Buffer();
            hpkeConfigListBuffer.readFrom(is);
        }
        return hpkeConfigListBuffer;
    }

    public static MockWebServer setupMockServer() throws IOException {
        Buffer hpkeConfigListBuffer = loadHpkeConfigList();
        MockWebServer server = new MockWebServer();
        server.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/dap-hpke-config-list")
                        .body(hpkeConfigListBuffer)
                        .build()
        );
        server.enqueue(
                new MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/dap-hpke-config-list")
                        .body(hpkeConfigListBuffer)
                        .build()
        );
        server.enqueue(new MockResponse(200, Headers.EMPTY, ""));
        server.start();
        return server;
    }
}
