package cn.cctstudio.smartdiypcassistantbackend.gateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
@Configuration
public class GatewayMultipartConfig {
    @Bean
    public StandardServletMultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver() {
            @Override public boolean isMultipart(HttpServletRequest request) {
                return !request.getRequestURI().startsWith("/agent/") && super.isMultipart(request);
            }
        };
    }
}
