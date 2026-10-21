package cn.cctstudio.smartdiypcassistantbackend.gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Single public backend boundary; Python retains Agent ownership and authorization. */
@RestController
public class AgentGatewayController {
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
    private final String origin;
    public AgentGatewayController(@Value("${app.agent.url:http://127.0.0.1:8000}") String origin) {
        this.origin = origin.replaceAll("/+$", "");
    }

    @RequestMapping("/agent/**")
    public void forward(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String target = origin + request.getRequestURI()
                + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        var builder = HttpRequest.newBuilder(URI.create(target)).timeout(Duration.ofSeconds(650));
        for (String header : new String[]{"Authorization", "Content-Type", "Accept", "Last-Event-ID"}) {
            if (request.getHeader(header) != null) builder.header(header, request.getHeader(header));
        }
        // Do not parse multipart: preserve uploads and their original boundary verbatim.
        byte[] body = request.getInputStream().readNBytes(10 * 1024 * 1024 + 1);
        if (body.length > 10 * 1024 * 1024) { response.sendError(413); return; }
        builder.method(request.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            var upstream = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            response.setStatus(upstream.statusCode());
            for (String header : new String[]{"Content-Type", "Cache-Control", "Content-Disposition"}) {
                upstream.headers().firstValue(header).ifPresent(value -> response.setHeader(header, value));
            }
            response.setHeader("X-Accel-Buffering", "no");
            try (var input = upstream.body()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, count);
                    response.flushBuffer();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!response.isCommitted()) response.sendError(502, "Agent service interrupted");
        } catch (IOException e) {
            if (!response.isCommitted()) response.sendError(502, "Agent service unavailable");
        }
    }
}
