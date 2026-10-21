package cn.cctstudio.smartdiypcassistantbackend.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AgentGatewayTests {
    @Test
    void preservesAuthorizationQueryMultipartAndSse() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/agent/upload", exchange -> {
            assertEquals("Bearer test-token", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("after=3", exchange.getRequestURI().getQuery());
            assertEquals("--boundary\r\ncontent\r\n--boundary--", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("id: 4\ndata: {\"type\":\"tool_started\"}\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();
        try {
            var gateway = new AgentGatewayController("http://127.0.0.1:" + server.getAddress().getPort());
            var request = new MockHttpServletRequest("POST", "/agent/upload");
            request.setQueryString("after=3");
            request.addHeader("Authorization", "Bearer test-token");
            request.setContentType("multipart/form-data; boundary=boundary");
            request.setContent("--boundary\r\ncontent\r\n--boundary--".getBytes(StandardCharsets.UTF_8));
            assertFalse(new GatewayMultipartConfig().multipartResolver().isMultipart(request));
            var response = new MockHttpServletResponse();
            gateway.forward(request, response);
            assertEquals(200, response.getStatus());
            assertEquals("text/event-stream", response.getContentType());
            assertTrue(response.getContentAsString().contains("tool_started"));
            assertEquals("no", response.getHeader("X-Accel-Buffering"));
        } finally { server.stop(0); }
    }
    @Test
    void unavailableAgentReturnsBadGateway() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort(); server.start(); server.stop(0);
        var response = new MockHttpServletResponse();
        new AgentGatewayController("http://127.0.0.1:" + port).forward(new MockHttpServletRequest("GET", "/agent/health"), response);
        assertEquals(502, response.getStatus());
    }
}
