package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bachelorchef.dto.CommunityDtos;
import com.bachelorchef.dto.RecipeDtos;
import com.bachelorchef.entity.Difficulty;
import com.bachelorchef.entity.ImportFileType;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.importer.FileRejectedException;
import com.bachelorchef.importer.ImportService;
import com.bachelorchef.service.GoalProgressCalculator;
import com.bachelorchef.service.MediaUrlPolicy;
import com.bachelorchef.service.PantryMatchService;
import com.bachelorchef.service.PortionCalculator;
import com.bachelorchef.service.ReviewService;
import com.bachelorchef.service.TextNormalizer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Equivalence class testing: one representative value per valid/invalid class. IDs (EC-xx-Vn /
 * EC-xx-In) match the "Equivalence Classes" sheet and docs/testing.md.
 */
class EquivalenceClassTest {

    private static final MediaUrlPolicy URLS = new MediaUrlPolicy(List.of("res.cloudinary.com"));

    @Nested
    @DisplayName("EC-01 Review rating")
    class Rating {
        private final ReviewService rules = new ReviewService(null, null, null, null, null, URLS, null, null, null);

        @ParameterizedTest(name = "{0}: rating={1} valid={2}")
        @CsvSource(nullValues = "null", value = {
                "EC-01-I1 below range, -3, false",
                "EC-01-V1 in range,     4, true",
                "EC-01-I2 above range,  9, false",
                "EC-01-I3 missing,   null, false"})
        void rating(String id, Integer rating, boolean valid) {
            assertThat(rules.validate(new CommunityDtos.ReviewRequest(rating, null, null, null)).containsKey("rating")).isEqualTo(!valid);
        }
    }

    @Nested
    @DisplayName("EC-02 Servings for portion scaling")
    class Servings {
        @ParameterizedTest(name = "{0}: servings={1} valid={2}")
        @CsvSource(nullValues = "null", value = {
                "EC-02-I1 below range, -5, false",
                "EC-02-V1 in range,    10, true",
                "EC-02-I2 above range, 99, false",
                "EC-02-I3 missing,   null, false"})
        void servings(String id, Integer servings, boolean valid) {
            assertThat(PortionCalculator.isValidServings(servings)).isEqualTo(valid);
        }
    }

    @Nested
    @DisplayName("EC-03 Goal target count")
    class GoalTarget {
        @ParameterizedTest(name = "{0}: target={1} valid={2}")
        @CsvSource({"EC-03-I1 below range, -1, false", "EC-03-V1 in range, 12, true", "EC-03-I2 above range, 100, false"})
        void target(String id, int target, boolean valid) {
            assertThat(GoalProgressCalculator.isValidTarget(target)).isEqualTo(valid);
        }
    }

    @Nested
    @DisplayName("EC-04 Goal month text")
    class Month {
        @ParameterizedTest(name = "{0}: \"{1}\" valid={2}")
        @CsvSource({"EC-04-V1 YYYY-MM, 2027-03, true", "EC-04-I1 month out of range, 2027-00, false",
                "EC-04-I2 wrong format, 03/2027, false", "EC-04-I3 not a date, soon, false"})
        void month(String id, String month, boolean valid) {
            assertThat(GoalProgressCalculator.parseMonth(month) != null).isEqualTo(valid);
        }
    }

    @Nested
    @DisplayName("EC-05 Difficulty text")
    class DifficultyClasses {
        @ParameterizedTest(name = "{0}: \"{1}\" valid={2}")
        @CsvSource(nullValues = "null", value = {"EC-05-V1 upper case, HARD, true", "EC-05-V2 mixed case, mEdIuM, true",
                "EC-05-I1 unknown word, SPICY, false", "EC-05-I2 missing, null, false"})
        void difficulty(String id, String value, boolean valid) {
            assertThat(Difficulty.parse(value).isPresent()).isEqualTo(valid);
        }
    }

    @Nested
    @DisplayName("EC-06 Image URL")
    class ImageUrl {
        @ParameterizedTest(name = "{0}: {1} valid={2}")
        @CsvSource({"EC-06-V1 https approved host, https://res.cloudinary.com/x.jpg, true",
                "EC-06-V2 https approved subdomain, https://img.res.cloudinary.com/x.jpg, true",
                "EC-06-I1 https other host, https://example.org/x.jpg, false",
                "EC-06-I2 non-https scheme, ftp://res.cloudinary.com/x.jpg, false",
                "EC-06-I3 script scheme, javascript:alert(1), false"})
        void url(String id, String url, boolean valid) {
            assertThat(URLS.check(url).isEmpty()).isEqualTo(valid);
        }
    }

    @Nested
    @DisplayName("EC-07 Import file kind")
    class FileKind {
        @ParameterizedTest(name = "{0}: {1} ({2}) → {3}")
        @CsvSource({"EC-07-V1 CSV, recipes.csv, text/csv, CSV", "EC-07-V2 JSON, recipes.json, application/json, JSON",
                "EC-07-I1 spreadsheet, recipes.xlsx, application/vnd.ms-excel, REJECT",
                "EC-07-I2 image renamed, recipes.csv, image/jpeg, REJECT"})
        void kind(String id, String name, String type, String expected) {
            if ("REJECT".equals(expected)) {
                assertThatThrownBy(() -> ImportService.detectType(name, type)).isInstanceOf(ApiException.class);
            } else {
                assertThat(ImportService.detectType(name, type)).isEqualTo(ImportFileType.valueOf(expected));
            }
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({"EC-07-I3 JSON content in .csv, CSV", "EC-07-I4 CSV content in .json, JSON"})
        void contentMismatch(String id, ImportFileType declared) {
            String content = declared == ImportFileType.CSV ? "[{\"title\":\"x\"}]" : "title,prep\nA,1";
            assertThatThrownBy(() -> ImportService.sniff(content, declared)).isInstanceOf(FileRejectedException.class);
        }
    }

    @Nested
    @DisplayName("EC-08 Pantry max total time")
    class MaxTime {
        private void normalise(Integer maxTime) throws Exception {
            Method m = PantryMatchService.class.getDeclaredMethod("normalise", RecipeDtos.MatchRequest.class);
            m.setAccessible(true);
            try {
                m.invoke(null, new RecipeDtos.MatchRequest(List.of(1L), maxTime, null, null, null, null, null, null));
            } catch (InvocationTargetException e) {
                throw (Exception) e.getCause();
            }
        }

        @ParameterizedTest(name = "{0}: maxTime={1} valid={2}")
        @CsvSource(nullValues = "null", value = {"EC-08-I1 below range, -10, false", "EC-08-V1 in range, 35, true",
                "EC-08-I2 above range, 120, false", "EC-08-V2 omitted (default 20), null, true"})
        void maxTime(String id, Integer maxTime, boolean valid) {
            if (valid) {
                assertThatCode(() -> normalise(maxTime)).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> normalise(maxTime)).isInstanceOf(ApiException.class);
            }
        }
    }

    @Nested
    @DisplayName("EC-09 Email normalisation")
    class Email {
        @ParameterizedTest(name = "{0}: \"{1}\" → \"{2}\"")
        @CsvSource(delimiter = '|', value = {"EC-09-V1 already normal|ana@example.com|ana@example.com",
                "EC-09-V2 upper case|ANA@Example.COM|ana@example.com", "EC-09-V3 surrounding spaces|  ana@example.com |ana@example.com"})
        void email(String id, String input, String expected) {
            assertThat(TextNormalizer.email(input)).isEqualTo(expected);
        }
    }
}
