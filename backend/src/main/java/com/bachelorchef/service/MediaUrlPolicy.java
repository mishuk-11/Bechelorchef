package com.bachelorchef.service;

import com.bachelorchef.config.AppProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Accepts only HTTPS image URLs on an allow-listed host (or its subdomains). Rejects other
 * protocols (http, javascript, data, file), credentials in URLs and over-long values.
 */
@Component
public class MediaUrlPolicy {

    public static final int MAX_LENGTH = 500;

    private final List<String> allowedHosts;

    @Autowired
    public MediaUrlPolicy(AppProperties properties) {
        this(properties.media().allowedImageHosts());
    }

    public MediaUrlPolicy(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts.stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).filter(h -> !h.isEmpty()).toList();
    }

    /** @return an error message, or empty when the URL is acceptable */
    public Optional<String> check(String url) {
        if (url == null) {
            return Optional.empty();
        }
        if (url.length() > MAX_LENGTH) {
            return Optional.of("Must be at most " + MAX_LENGTH + " characters.");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            return Optional.of("Must be a valid URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return Optional.of("Must be an https:// URL.");
        }
        if (uri.getRawUserInfo() != null || uri.getHost() == null) {
            return Optional.of("Must be a valid image URL.");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = allowedHosts.stream().anyMatch(h -> host.equals(h) || host.endsWith("." + h));
        if (!allowed) {
            return Optional.of("Images must be hosted on an approved image host (" + String.join(", ", allowedHosts) + ").");
        }
        return Optional.empty();
    }

    public List<String> allowedHosts() {
        return allowedHosts;
    }
}
