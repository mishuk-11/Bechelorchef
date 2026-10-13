package com.bachelorchef.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class MediaDtos {

    private MediaDtos() {
    }

    public enum Purpose { RECIPE, STEP, AVATAR, REVIEW }

    public record MediaConfig(boolean uploadsEnabled, List<String> allowedHosts, long maxBytes,
                              List<String> allowedFormats) {
    }

    public record SignatureRequest(@NotNull(message = "Purpose is required.") Purpose purpose) {
    }

    /** Parameters for a direct, signed browser upload to Cloudinary. The API secret is never returned. */
    public record SignatureResponse(String uploadUrl, String cloudName, String apiKey, long timestamp,
                                    String signature, String folder, String allowedFormats, long maxBytes) {
    }
}
