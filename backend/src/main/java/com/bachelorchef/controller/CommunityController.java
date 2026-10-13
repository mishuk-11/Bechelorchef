package com.bachelorchef.controller;

import com.bachelorchef.dto.CatalogDtos;
import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.MediaDtos;
import com.bachelorchef.dto.PageResponse;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.CatalogService;
import com.bachelorchef.service.MediaService;
import com.bachelorchef.service.ReportService;
import com.bachelorchef.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Catalogue lookups, review edits, content reports and media signing. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catalogue & community")
public class CommunityController {

    private final CatalogService catalog;
    private final ReviewService reviews;
    private final ReportService reports;
    private final MediaService media;

    public CommunityController(CatalogService catalog, ReviewService reviews, ReportService reports, MediaService media) {
        this.catalog = catalog;
        this.reviews = reviews;
        this.reports = reports;
        this.media = media;
    }

    @GetMapping("/ingredients")
    @Operation(summary = "Search active ingredients (for autocomplete)")
    public PageResponse<CatalogDtos.IngredientView> ingredients(@RequestParam(required = false) String query,
                                                                @RequestParam(required = false) String category,
                                                                @RequestParam(required = false) Integer page,
                                                                @RequestParam(required = false) Integer size) {
        return catalog.searchIngredients(query, category, false, "name", page, size == null ? 20 : size);
    }

    @GetMapping("/tags")
    public List<CatalogDtos.TagView> tags() {
        return catalog.listTags();
    }

    @PatchMapping("/reviews/{reviewId}")
    @Operation(summary = "Edit my review (returns it to moderation)")
    public CommunityDtos.ReviewView updateReview(@PathVariable Long reviewId, @AuthenticationPrincipal AuthUser user,
                                                 @Valid @RequestBody CommunityDtos.ReviewRequest request) {
        return reviews.update(user, reviewId, request);
    }

    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId, @AuthenticationPrincipal AuthUser user) {
        reviews.delete(user, reviewId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports")
    @Operation(summary = "Flag a published recipe or review for moderators")
    public ResponseEntity<Void> report(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CommunityDtos.ReportRequest request) {
        reports.create(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/media/config")
    public MediaDtos.MediaConfig mediaConfig() {
        return media.config();
    }

    @PostMapping("/media/upload-signature")
    @Operation(summary = "Signed parameters for a direct image upload to the media provider")
    public MediaDtos.SignatureResponse sign(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody MediaDtos.SignatureRequest request) {
        return media.sign(user, request.purpose());
    }
}
