package cn.cctstudio.smartdiypcassistantbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class MediaResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadRoot = Path.of("uploads").toAbsolutePath().normalize();
        registry.addResourceHandler("/media/**")
                .addResourceLocations(uploadRoot.toUri().toString());
    }
}
