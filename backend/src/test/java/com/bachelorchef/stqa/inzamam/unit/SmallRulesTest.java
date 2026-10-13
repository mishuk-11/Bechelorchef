package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.service.MediaUrlPolicy;
import com.bachelorchef.service.ReviewService;
import com.bachelorchef.service.TextNormalizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Normalisation and review-rating rules. */
class SmallRulesTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {"  Green   Chili |green chili", "EGG|egg", "Soy\tSauce|soy sauce", "Ä Ö|ä ö"})
    @DisplayName("Ingredient/tag keys are trimmed, whitespace-collapsed and case-folded")
    void keys(String input, String expected) {
        assertThat(TextNormalizer.key(input)).isEqualTo(expected);
    }

    @Test
    void blankKeyIsNull() {
        assertThat(TextNormalizer.key("   ")).isNull();
        assertThat(TextNormalizer.key(null)).isNull();
        assertThat(TextNormalizer.clean("  Masala   Oats ")).isEqualTo("Masala Oats");
    }

    @Test
    void emailNormalisation() {
        assertThat(TextNormalizer.email("  User@Example.COM ")).isEqualTo("user@example.com");
    }

    private final ReviewService reviewRules = new ReviewService(null, null, null, null, null,
            new MediaUrlPolicy(List.of("res.cloudinary.com")), null, null, null);

    @ParameterizedTest(name = "rating {0} valid={1}")
    @CsvSource({"0,false", "1,true", "3,true", "5,true", "6,false", "-1,false"})
    @DisplayName("Review rating BVA 0/1/3/5/6")
    void rating(int rating, boolean valid) {
        Map<String, String> errors = reviewRules.validate(new CommunityDtos.ReviewRequest(rating, "Nice", null, null));
        assertThat(errors.containsKey("rating")).isEqualTo(!valid);
    }

    @Test
    void ratingRequired() {
        assertThat(reviewRules.validate(new CommunityDtos.ReviewRequest(null, null, null, null))).containsKey("rating");
    }

    @Test
    @DisplayName("Cloudinary signature: SHA-1 of sorted k=v pairs joined by '&' plus the secret (Cloudinary's documented example)")
    void cloudinarySignature() {
        java.util.TreeMap<String, String> params = new java.util.TreeMap<>(Map.of("timestamp", "1315060510", "public_id", "sample_image"));
        assertThat(com.bachelorchef.service.MediaService.signature(params, "abcd")).isEqualTo("b4ad47fb4e25c7bf5f92a20089f9db59bc302313");
    }

    @Test
    void reviewCommentLengthAndPhoto() {
        Map<String, String> errors = reviewRules.validate(new CommunityDtos.ReviewRequest(4, "x".repeat(2001), "http://bad.example/a.png", null));
        assertThat(errors).containsKeys("comment", "photoUrl");
    }
}
