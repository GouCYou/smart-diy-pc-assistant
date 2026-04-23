package cn.cctstudio.smartdiypcassistantbackend.file;

import cn.cctstudio.smartdiypcassistantbackend.config.properties.QiniuProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Service
public class MediaUrlService {

    private final QiniuProperties properties;

    public MediaUrlService(QiniuProperties properties) {
        this.properties = properties;
    }

    public String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("/media/")) {
            return StringUtils.trimLeadingCharacter(trimmed.substring("/media/".length()), '/');
        }
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                String path = URI.create(trimmed).getPath();
                if (StringUtils.hasText(path)) {
                    if (path.startsWith("/media/")) {
                        return StringUtils.trimLeadingCharacter(path.substring("/media/".length()), '/');
                    }
                    return StringUtils.trimLeadingCharacter(path.trim(), '/');
                }
            } catch (IllegalArgumentException ignored) {
                return trimmed;
            }
        }
        return StringUtils.trimLeadingCharacter(trimmed, '/');
    }

    public List<String> normalizeKeys(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    public String toPublicUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        String key = normalizeKey(trimmed);
        if (StringUtils.hasText(key) && key.startsWith("local/")) {
            return localPublicUrl(key);
        }
        if (!StringUtils.hasText(properties.domain())) {
            return trimmed;
        }
        if (!StringUtils.hasText(key)) {
            return "";
        }
        String domain = properties.domain().trim();
        if (domain.startsWith("http://") || domain.startsWith("https://")) {
            return domain + "/" + key;
        }
        return "https://" + domain + "/" + key;
    }

    public List<String> toPublicUrls(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(this::toPublicUrl)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String localPublicUrl(String key) {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/media/")
                    .path(key)
                    .toUriString();
        } catch (IllegalStateException exception) {
            return "/media/" + key;
        }
    }
}
