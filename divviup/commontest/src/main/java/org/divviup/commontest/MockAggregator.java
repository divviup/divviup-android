package org.divviup.commontest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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
}
