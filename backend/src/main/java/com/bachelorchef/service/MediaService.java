package com.bachelorchef.service;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.MediaDtos;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.security.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Signs direct browser-to-Cloudinary uploads so image bytes never pass through the API.
 * Only JPEG, PNG and WebP are allowed (no SVG). Without Cloudinary credentials, uploads are
 * reported as unavailable and users may paste an HTTPS URL from an approved host instead.
 */
@Service
public class MediaService {

    public static final List<String> ALLOWED_FORMATS = List.of("jpg", "jpeg", "png", "webp");

    private final AppProperties properties;
    private final MediaUrlPolicy urlPolicy;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public MediaService(AppProperties properties, MediaUrlPolicy urlPolicy, RateLimiter rateLimiter, Clock clock) {
        this.properties = properties;
        this.urlPolicy = urlPolicy;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public MediaDtos.MediaConfig config() {
        return new MediaDtos.MediaConfig(properties.media().cloudinaryConfigured(), urlPolicy.allowedHosts(),
                properties.media().maxImageBytes(), ALLOWED_FORMATS);
    }

    public MediaDtos.SignatureResponse sign(AuthUser actor, MediaDtos.Purpose purpose) {
        AppProperties.Media media = properties.media();
        if (!media.cloudinaryConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_NOT_CONFIGURED",
                    "Image uploads are not configured on this server. Paste an HTTPS image link instead.");
        }
        rateLimiter.check("media-sign:" + actor.id(), 60, Duration.ofHours(1));
        long timestamp = clock.instant().getEpochSecond();
        String folder = media.folder() + "/" + purpose.name().toLowerCase(Locale.ROOT) + "/u" + actor.id();
        String allowedFormats = String.join(",", ALLOWED_FORMATS);
        Map<String, String> params = new TreeMap<>();
        params.put("allowed_formats", allowedFormats);
        params.put("folder", folder);
        params.put("timestamp", String.valueOf(timestamp));
        String signature = signature(params, media.apiSecret());
        return new MediaDtos.SignatureResponse(
                "https://api.cloudinary.com/v1_1/" + media.cloudName() + "/image/upload",
                media.cloudName(), media.apiKey(), timestamp, signature, folder, allowedFormats, media.maxImageBytes());
    }

    /** Cloudinary signature: SHA-1 hex of alphabetically sorted "k=v" pairs joined by '&', followed by the secret. */
    public static String signature(Map<String, String> sortedParams, String apiSecret) {
        String toSign = sortedParams.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")) + apiSecret;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
