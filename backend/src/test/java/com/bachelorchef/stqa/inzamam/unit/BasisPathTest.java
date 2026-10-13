package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bachelorchef.config.AppProperties;
import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.entity.ImportFileType;
import com.bachelorchef.entity.ImportStatus;
import com.bachelorchef.entity.Recipe;
import com.bachelorchef.entity.RecipeStatus;
import com.bachelorchef.entity.Role;
import com.bachelorchef.entity.User;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.importer.ImportService;
import com.bachelorchef.security.AuthUser;
import com.bachelorchef.service.CookingHistoryService;
import com.bachelorchef.service.GoalProgressCalculator;
import com.bachelorchef.service.MediaUrlPolicy;
import com.bachelorchef.service.PantryMatcher;
import com.bachelorchef.service.PortionCalculator;
import com.bachelorchef.service.RecipeDraft;
import com.bachelorchef.service.RecipeQueryService;
import com.bachelorchef.service.RecipeValidator;
import com.bachelorchef.service.ReviewService;
import com.bachelorchef.service.TimeCategory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Basis path testing. For each module the control-flow graph was drawn from the actual source and
 * V(G) = (number of predicate nodes) + 1, counting every simple condition of a short-circuit
 * && / || as its own predicate (as in the compiled CFG). One test per independent path; the IDs
 * (BP-xx-Pn) match the "Basis Path" sheet of docs/stqa/BachelorChef_TestCases.xlsx and docs/testing.md.
 */
class BasisPathTest {

    private static final MediaUrlPolicy URLS = new MediaUrlPolicy(List.of("res.cloudinary.com"));
    private static final RecipeValidator VALIDATOR = new RecipeValidator(URLS);

    // ------------------------------------------------------------------ BP-01
    /** TimeCategory.of(int): predicates total&lt;1, total&gt;60, total&lt;=15, total&lt;=20 → V(G) = 5. */
    @Nested
    @DisplayName("BP-01 TimeCategory.of — V(G)=5")
    class BP01 {
        @Test @DisplayName("BP-01-P1 total=0 → rejected (total<1 true)")
        void p1() { assertThatThrownBy(() -> TimeCategory.of(0)).isInstanceOf(IllegalArgumentException.class); }

        @Test @DisplayName("BP-01-P2 total=61 → rejected (total>60 true)")
        void p2() { assertThatThrownBy(() -> TimeCategory.of(61)).isInstanceOf(IllegalArgumentException.class); }

        @Test @DisplayName("BP-01-P3 total=10 → EXPRESS")
        void p3() { assertThat(TimeCategory.of(10)).isEqualTo(TimeCategory.EXPRESS); }

        @Test @DisplayName("BP-01-P4 total=18 → BACHELOR_QUICK")
        void p4() { assertThat(TimeCategory.of(18)).isEqualTo(TimeCategory.BACHELOR_QUICK); }

        @Test @DisplayName("BP-01-P5 total=45 → EXTENDED")
        void p5() { assertThat(TimeCategory.of(45)).isEqualTo(TimeCategory.EXTENDED); }
    }

    // ------------------------------------------------------------------ BP-02
    /** PortionCalculator.isValidServings: servings!=null && >=1 && <=20 → 3 predicates → V(G) = 4. */
    @Nested
    @DisplayName("BP-02 PortionCalculator.isValidServings — V(G)=4")
    class BP02 {
        @Test @DisplayName("BP-02-P1 null → false") void p1() { assertThat(PortionCalculator.isValidServings(null)).isFalse(); }
        @Test @DisplayName("BP-02-P2 0 → false") void p2() { assertThat(PortionCalculator.isValidServings(0)).isFalse(); }
        @Test @DisplayName("BP-02-P3 21 → false") void p3() { assertThat(PortionCalculator.isValidServings(21)).isFalse(); }
        @Test @DisplayName("BP-02-P4 5 → true") void p4() { assertThat(PortionCalculator.isValidServings(5)).isTrue(); }
    }

    // ------------------------------------------------------------------ BP-03
    /** ImportService.finalStatus: imported==0, failed==0, skipped==0 → V(G) = 4. */
    @Nested
    @DisplayName("BP-03 ImportService.finalStatus — V(G)=4")
    class BP03 {
        @Test @DisplayName("BP-03-P1 imported=0 → FAILED") void p1() { assertThat(ImportService.finalStatus(0, 3, 0)).isEqualTo(ImportStatus.FAILED); }
        @Test @DisplayName("BP-03-P2 all imported → COMPLETED") void p2() { assertThat(ImportService.finalStatus(5, 0, 0)).isEqualTo(ImportStatus.COMPLETED); }
        @Test @DisplayName("BP-03-P3 failed>0 → COMPLETED_WITH_ERRORS") void p3() { assertThat(ImportService.finalStatus(4, 1, 0)).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS); }
        @Test @DisplayName("BP-03-P4 skipped>0 → COMPLETED_WITH_ERRORS") void p4() { assertThat(ImportService.finalStatus(4, 0, 1)).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS); }
    }

    // ------------------------------------------------------------------ BP-04 / BP-05 / BP-06 (RecipeValidator)
    private static RecipeDraft draft(String title, Integer prep, Integer cook, BigDecimal cost) {
        return new RecipeDraft(title, null, prep, cook, "EASY", cost, 2, null,
                List.of(new RecipeDraft.IngredientLine("1", BigDecimal.ONE, "pcs", false)),
                List.of(new RecipeDraft.StepLine("Cook.", null)), List.of());
    }

    /** RecipeValidator.validateTitle: cleaned==null, length<3, length>150 → V(G) = 4. */
    @Nested
    @DisplayName("BP-04 RecipeValidator.validateTitle — V(G)=4")
    class BP04 {
        private Map<String, String> v(String title) { return VALIDATOR.validate(draft(title, 5, 5, BigDecimal.TEN)); }
        @Test @DisplayName("BP-04-P1 blank → 'Title is required.'") void p1() { assertThat(v("   ")).containsEntry("title", "Title is required."); }
        @Test @DisplayName("BP-04-P2 'ab' → length error") void p2() { assertThat(v("ab").get("title")).startsWith("Must be between"); }
        @Test @DisplayName("BP-04-P3 151 chars → length error") void p3() { assertThat(v("x".repeat(151)).get("title")).startsWith("Must be between"); }
        @Test @DisplayName("BP-04-P4 'Masala Oats' → valid") void p4() { assertThat(v("Masala Oats")).doesNotContainKey("title"); }
    }

    /** RecipeValidator.validateCost: cost==null, signum<0, >MAX, scale>2 → V(G) = 5. */
    @Nested
    @DisplayName("BP-05 RecipeValidator.validateCost — V(G)=5")
    class BP05 {
        private String v(String cost) {
            return VALIDATOR.validate(draft("Valid title", 5, 5, cost == null ? null : new BigDecimal(cost))).get("estimatedCostBdt");
        }
        @Test @DisplayName("BP-05-P1 null → required") void p1() { assertThat(v(null)).isEqualTo("Estimated cost is required."); }
        @Test @DisplayName("BP-05-P2 -1 → range error") void p2() { assertThat(v("-1")).startsWith("Must be between"); }
        @Test @DisplayName("BP-05-P3 100000.01 → range error") void p3() { assertThat(v("100000.01")).startsWith("Must be between"); }
        @Test @DisplayName("BP-05-P4 1.005 → decimals error") void p4() { assertThat(v("1.005")).isEqualTo("Use at most 2 decimal places."); }
        @Test @DisplayName("BP-05-P5 80.00 → valid") void p5() { assertThat(v("80.00")).isNull(); }
    }

    /**
     * RecipeValidator.validateTimes: prepValid (3 conditions), cookValid (3), if !prepValid (1),
     * if !cookValid (1), final if prepValid && cookValid && total>60 (3) → 11 predicates → V(G) = 12.
     */
    @Nested
    @DisplayName("BP-06 RecipeValidator.validateTimes — V(G)=12")
    class BP06 {
        private Map<String, String> v(Integer prep, Integer cook) { return VALIDATOR.validate(draft("Valid title", prep, cook, BigDecimal.TEN)); }
        @Test @DisplayName("BP-06-P1 prep=null") void p1() { assertThat(v(null, 10)).containsKey("prepTimeMins").doesNotContainKeys("cookTimeMins", "totalTimeMins"); }
        @Test @DisplayName("BP-06-P2 prep=-1") void p2() { assertThat(v(-1, 10)).containsKey("prepTimeMins").doesNotContainKey("totalTimeMins"); }
        @Test @DisplayName("BP-06-P3 prep=61") void p3() { assertThat(v(61, 10)).containsKey("prepTimeMins").doesNotContainKey("totalTimeMins"); }
        @Test @DisplayName("BP-06-P4 cook=null") void p4() { assertThat(v(5, null)).containsKey("cookTimeMins").doesNotContainKeys("prepTimeMins", "totalTimeMins"); }
        @Test @DisplayName("BP-06-P5 cook=0") void p5() { assertThat(v(5, 0)).containsKey("cookTimeMins").doesNotContainKey("totalTimeMins"); }
        @Test @DisplayName("BP-06-P6 cook=61") void p6() { assertThat(v(5, 61)).containsKey("cookTimeMins").doesNotContainKey("totalTimeMins"); }
        @Test @DisplayName("BP-06-P7 5+10 → valid") void p7() { assertThat(v(5, 10)).doesNotContainKeys("prepTimeMins", "cookTimeMins", "totalTimeMins"); }
        @Test @DisplayName("BP-06-P8 30+31 → total error") void p8() { assertThat(v(30, 31)).containsKey("totalTimeMins").doesNotContainKeys("prepTimeMins", "cookTimeMins"); }
        @Test @DisplayName("BP-06-P9 both null") void p9() { assertThat(v(null, null)).containsKeys("prepTimeMins", "cookTimeMins").doesNotContainKey("totalTimeMins"); }
        @Test @DisplayName("BP-06-P10 prep=-1, cook=0") void p10() { assertThat(v(-1, 0)).containsKeys("prepTimeMins", "cookTimeMins"); }
        @Test @DisplayName("BP-06-P11 prep=61, cook=61") void p11() { assertThat(v(61, 61)).containsKeys("prepTimeMins", "cookTimeMins").doesNotContainKey("totalTimeMins"); }
        @Test @DisplayName("BP-06-P12 0+60 → valid (total boundary)") void p12() { assertThat(v(0, 60)).doesNotContainKeys("prepTimeMins", "cookTimeMins", "totalTimeMins"); }
    }

    // ------------------------------------------------------------------ BP-07
    /** PantryMatcher.compare: five "if (by... != 0)" predicates → V(G) = 6. Each path stops at a different key. */
    @Nested
    @DisplayName("BP-07 PantryMatcher ranking comparator — V(G)=6")
    class BP07 {
        private PantryMatcher.Candidate c(long id, String title, int time, String rating, int req, int matched) {
            return new PantryMatcher.Candidate(id, title, time, new BigDecimal(rating), req, matched);
        }
        private int cmp(PantryMatcher.Candidate a, PantryMatcher.Candidate b) { return Integer.signum(PantryMatcher.RANKING.compare(a, b)); }

        @Test @DisplayName("BP-07-P1 decided by match ratio") void p1() { assertThat(cmp(c(1, "A", 10, "1", 2, 2), c(2, "A", 10, "1", 2, 1))).isEqualTo(-1); }
        @Test @DisplayName("BP-07-P2 decided by missing count") void p2() { assertThat(cmp(c(1, "A", 10, "1", 2, 1), c(2, "A", 10, "1", 4, 2))).isEqualTo(-1); }
        @Test @DisplayName("BP-07-P3 decided by total time") void p3() { assertThat(cmp(c(1, "A", 20, "1", 2, 2), c(2, "A", 10, "1", 2, 2))).isEqualTo(1); }
        @Test @DisplayName("BP-07-P4 decided by rating") void p4() { assertThat(cmp(c(1, "A", 10, "4.5", 2, 2), c(2, "A", 10, "3.0", 2, 2))).isEqualTo(-1); }
        @Test @DisplayName("BP-07-P5 decided by title") void p5() { assertThat(cmp(c(1, "beta", 10, "1", 2, 2), c(2, "Alpha", 10, "1", 2, 2))).isEqualTo(1); }
        @Test @DisplayName("BP-07-P6 decided by id") void p6() { assertThat(cmp(c(1, "A", 10, "1", 2, 2), c(2, "a", 10, "1", 2, 2))).isEqualTo(-1); }
    }

    // ------------------------------------------------------------------ BP-08
    /** GoalProgressCalculator.parseMonth: value==null || !matches → V(G) = 3. */
    @Nested
    @DisplayName("BP-08 GoalProgressCalculator.parseMonth — V(G)=3")
    class BP08 {
        @Test @DisplayName("BP-08-P1 null → null") void p1() { assertThat(GoalProgressCalculator.parseMonth(null)).isNull(); }
        @Test @DisplayName("BP-08-P2 '2026-13' → null") void p2() { assertThat(GoalProgressCalculator.parseMonth("2026-13")).isNull(); }
        @Test @DisplayName("BP-08-P3 '2026-10' → October 2026") void p3() { assertThat(GoalProgressCalculator.parseMonth("2026-10")).hasToString("2026-10"); }
    }

    // ------------------------------------------------------------------ BP-09
    /**
     * MediaUrlPolicy.check: url==null, length>500, URISyntaxException (catch edge), scheme!=https,
     * userInfo!=null, host==null, !allowed → 7 predicates → V(G) = 8.
     */
    @Nested
    @DisplayName("BP-09 MediaUrlPolicy.check — V(G)=8")
    class BP09 {
        @Test @DisplayName("BP-09-P1 null → accepted (optional)") void p1() { assertThat(URLS.check(null)).isEmpty(); }
        @Test @DisplayName("BP-09-P2 501 chars → too long") void p2() { assertThat(URLS.check("https://res.cloudinary.com/" + "a".repeat(480))).get().asString().contains("at most"); }
        @Test @DisplayName("BP-09-P3 malformed → invalid URL") void p3() { assertThat(URLS.check("https://bad host/a.jpg")).contains("Must be a valid URL."); }
        @Test @DisplayName("BP-09-P4 http → https required") void p4() { assertThat(URLS.check("http://res.cloudinary.com/a.jpg")).contains("Must be an https:// URL."); }
        @Test @DisplayName("BP-09-P5 credentials in URL → rejected") void p5() { assertThat(URLS.check("https://u:p@res.cloudinary.com/a.jpg")).contains("Must be a valid image URL."); }
        @Test @DisplayName("BP-09-P6 missing host → rejected") void p6() { assertThat(URLS.check("https:///a.jpg")).contains("Must be a valid image URL."); }
        @Test @DisplayName("BP-09-P7 unapproved host → rejected") void p7() { assertThat(URLS.check("https://evil.example/a.jpg")).get().asString().contains("approved image host"); }
        @Test @DisplayName("BP-09-P8 approved https → accepted") void p8() { assertThat(URLS.check("https://res.cloudinary.com/demo/a.jpg")).isEmpty(); }
    }

    // ------------------------------------------------------------------ BP-10
    /** ReviewService.validate: rating==null, rating<1, rating>5, comment length>2000, photo error present → V(G) = 6. */
    @Nested
    @DisplayName("BP-10 ReviewService.validate — V(G)=6")
    class BP10 {
        private final ReviewService rules = new ReviewService(null, null, null, null, null, URLS, null, null, null);
        private Map<String, String> v(Integer rating, String comment, String photo) {
            return rules.validate(new CommunityDtos.ReviewRequest(rating, comment, photo, null));
        }
        @Test @DisplayName("BP-10-P1 rating=null") void p1() { assertThat(v(null, null, null)).containsEntry("rating", "A rating is required."); }
        @Test @DisplayName("BP-10-P2 rating=0") void p2() { assertThat(v(0, null, null)).containsKey("rating"); }
        @Test @DisplayName("BP-10-P3 rating=6") void p3() { assertThat(v(6, null, null)).containsKey("rating"); }
        @Test @DisplayName("BP-10-P4 comment 2001 chars") void p4() { assertThat(v(3, "x".repeat(2001), null)).containsOnlyKeys("comment"); }
        @Test @DisplayName("BP-10-P5 http photo") void p5() { assertThat(v(3, "ok", "http://x.test/a.png")).containsOnlyKeys("photoUrl"); }
        @Test @DisplayName("BP-10-P6 all valid") void p6() { assertThat(v(3, "Tasty", "https://res.cloudinary.com/a.jpg")).isEmpty(); }
    }

    // ------------------------------------------------------------------ BP-11
    /** ImportService.detectType: contentType==null, .csv, csv type, .json, json type → V(G) = 6. */
    @Nested
    @DisplayName("BP-11 ImportService.detectType — V(G)=6")
    class BP11 {
        @Test @DisplayName("BP-11-P1 .csv + null type → CSV") void p1() { assertThat(ImportService.detectType("a.csv", null)).isEqualTo(ImportFileType.CSV); }
        @Test @DisplayName("BP-11-P2 .csv + text/csv → CSV") void p2() { assertThat(ImportService.detectType("a.csv", "text/csv; charset=utf-8")).isEqualTo(ImportFileType.CSV); }
        @Test @DisplayName("BP-11-P3 .csv + image/png → rejected") void p3() { assertThatThrownBy(() -> ImportService.detectType("a.csv", "image/png")).isInstanceOf(ApiException.class); }
        @Test @DisplayName("BP-11-P4 .json + application/json → JSON") void p4() { assertThat(ImportService.detectType("a.json", "application/json")).isEqualTo(ImportFileType.JSON); }
        @Test @DisplayName("BP-11-P5 .json + image/png → rejected") void p5() { assertThatThrownBy(() -> ImportService.detectType("a.json", "image/png")).isInstanceOf(ApiException.class); }
        @Test @DisplayName("BP-11-P6 .xlsx → rejected") void p6() { assertThatThrownBy(() -> ImportService.detectType("a.xlsx", "text/csv")).isInstanceOf(ApiException.class); }
    }

    // ------------------------------------------------------------------ BP-12
    /** CookingHistoryService.validateCookedOn: requested==null, isAfter(now+5m), isBefore(now-366d) → V(G) = 4. */
    @Nested
    @DisplayName("BP-12 CookingHistoryService.validateCookedOn — V(G)=4")
    class BP12 {
        private final Instant now = Instant.parse("2026-10-13T10:00:00Z");
        private final CookingHistoryService service = new CookingHistoryService(null, null, null, null,
                new AppProperties("Asia/Dhaka", List.of("x"), null, null, null, null, null), Clock.fixed(now, ZoneOffset.UTC));
        @Test @DisplayName("BP-12-P1 null → now") void p1() { assertThat(service.validateCookedOn(null)).isEqualTo(now); }
        @Test @DisplayName("BP-12-P2 one hour ahead → rejected") void p2() { assertThatThrownBy(() -> service.validateCookedOn(now.plus(Duration.ofHours(1)))).isInstanceOf(ApiException.class); }
        @Test @DisplayName("BP-12-P3 400 days ago → rejected") void p3() { assertThatThrownBy(() -> service.validateCookedOn(now.minus(Duration.ofDays(400)))).isInstanceOf(ApiException.class); }
        @Test @DisplayName("BP-12-P4 yesterday → accepted") void p4() { assertThat(service.validateCookedOn(now.minus(Duration.ofDays(1)))).isEqualTo(now.minus(Duration.ofDays(1))); }
    }

    // ------------------------------------------------------------------ BP-13
    /** RecipeQueryService.canView: isPublic || (viewer!=null && (isAdmin || isOwnedBy)) → 4 predicates → V(G) = 5. */
    @Nested
    @DisplayName("BP-13 RecipeQueryService.canView — V(G)=5")
    class BP13 {
        private Recipe recipe(RecipeStatus status, long ownerId) {
            User owner = mock(User.class);
            when(owner.getId()).thenReturn(ownerId);
            Recipe r = new Recipe();
            r.setStatus(status);
            r.setCreatedBy(owner);
            return r;
        }
        private final AuthUser admin = new AuthUser(1L, "a@x.test", Role.ADMIN);
        private final AuthUser owner = new AuthUser(7L, "o@x.test", Role.USER);
        private final AuthUser other = new AuthUser(9L, "u@x.test", Role.USER);

        @Test @DisplayName("BP-13-P1 approved, anonymous → visible") void p1() { assertThat(RecipeQueryService.canView(recipe(RecipeStatus.APPROVED, 7), null)).isTrue(); }
        @Test @DisplayName("BP-13-P2 pending, anonymous → hidden") void p2() { assertThat(RecipeQueryService.canView(recipe(RecipeStatus.PENDING, 7), null)).isFalse(); }
        @Test @DisplayName("BP-13-P3 pending, admin → visible") void p3() { assertThat(RecipeQueryService.canView(recipe(RecipeStatus.PENDING, 7), admin)).isTrue(); }
        @Test @DisplayName("BP-13-P4 pending, owner → visible") void p4() { assertThat(RecipeQueryService.canView(recipe(RecipeStatus.PENDING, 7), owner)).isTrue(); }
        @Test @DisplayName("BP-13-P5 pending, other user → hidden") void p5() { assertThat(RecipeQueryService.canView(recipe(RecipeStatus.PENDING, 7), other)).isFalse(); }
    }
}
