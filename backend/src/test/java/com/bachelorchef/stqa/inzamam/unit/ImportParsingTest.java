package com.bachelorchef.stqa.inzamam.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bachelorchef.entity.ImportFileType;
import com.bachelorchef.entity.ImportStatus;
import com.bachelorchef.exception.ApiException;
import com.bachelorchef.importer.CsvRecipeParser;
import com.bachelorchef.importer.FileRejectedException;
import com.bachelorchef.importer.ImportIssue;
import com.bachelorchef.importer.ImportRow;
import com.bachelorchef.importer.ImportRowValidator;
import com.bachelorchef.importer.ImportService;
import com.bachelorchef.importer.ImportText;
import com.bachelorchef.importer.JsonRecipeParser;
import com.bachelorchef.importer.ParsedRecord;
import com.bachelorchef.service.MediaUrlPolicy;
import com.bachelorchef.service.RecipeValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** CSV/JSON parsing, file-level rejection, batch-size boundaries and row validation. */
class ImportParsingTest {

    static final String HEADER = "title,prep_time_mins,cook_time_mins,difficulty_level,estimated_cost_bdt,ingredients_csv,instructions_pipe";
    static final int MAX = 500;

    private CsvRecipeParser csv;
    private JsonRecipeParser jsonParser;
    private ImportRowValidator rowValidator;
    private Path tempFile;

    @BeforeEach
    void setUp() throws IOException {
        csv = new CsvRecipeParser();
        jsonParser = new JsonRecipeParser(JsonMapper.builder().build());
        rowValidator = new ImportRowValidator(new RecipeValidator(new MediaUrlPolicy(List.of("res.cloudinary.com"))));
        tempFile = Files.createTempFile("bulk-recipes", ".csv");
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    /** Writes real content to a temp file and reads it back, as an uploaded file would be. */
    private String viaTempFile(String content) throws IOException {
        Files.writeString(tempFile, content, StandardCharsets.UTF_8);
        return ImportText.decodeUtf8(Files.readAllBytes(tempFile));
    }

    static String csvRows(int n) {
        return HEADER + "\n" + IntStream.rangeClosed(1, n)
                .mapToObj(i -> "Recipe " + i + ",2,5,EASY,30.00,Egg:1:pcs|Salt:0.5:tsp,Fry|Serve")
                .collect(Collectors.joining("\n")) + "\n";
    }

    @Nested
    @DisplayName("CSV")
    class Csv {

        @Test
        @DisplayName("Quoted commas, escaped pipe, optional ingredient and tags are parsed correctly")
        void fixture() throws IOException {
            String text = viaTempFile(new String(ImportParsingTest.class.getResourceAsStream("/bulk_recipes.csv").readAllBytes(), StandardCharsets.UTF_8));
            List<ParsedRecord> records = csv.parse(text, MAX);
            assertThat(records).hasSize(4).allMatch(r -> !r.hasErrors());
            ImportRow quoted = records.get(2).row();
            assertThat(quoted.title()).isEqualTo("Chili, Garlic & Egg Toast");
            assertThat(quoted.steps()).extracting(ImportRow.Step::instruction).contains("Fry egg with garlic, chili");
            assertThat(quoted.ingredients().get(3).optional()).isTrue();
            assertThat(records.get(3).row().description()).isEqualTo("Pipe in text: 1\\|2 cups");
            assertThat(records.get(0).row().tags()).containsExactly("Quick", "Noodles");
            assertThat(records.get(3).row().baseServings()).isEqualTo(2);
            assertThat(records.get(0).rowNumber()).isEqualTo(1);
            assertThat(records.get(0).lineNumber()).isEqualTo(2); // header is line 1
        }

        @Test
        @DisplayName("Escaped pipe inside an instruction is kept literally")
        void escapedPipe() throws IOException {
            List<ParsedRecord> r = csv.parse(viaTempFile(HEADER + "\nPipe Test,1,2,EASY,1,Egg:1:pcs,Mix 1\\|2 cup|Serve\n"), MAX);
            assertThat(r.get(0).row().steps()).extracting(ImportRow.Step::instruction).containsExactly("Mix 1|2 cup", "Serve");
        }

        @Test
        void invalidNumber() throws IOException {
            ParsedRecord r = csv.parse(viaTempFile(HEADER + "\nBad,abc,2.5,EASY,x,Egg:1:pcs,Cook\n"), MAX).get(0);
            assertThat(r.issues()).extracting(ImportIssue::field).contains("prep_time_mins", "cook_time_mins", "estimated_cost_bdt");
            assertThat(r.hasErrors()).isTrue();
        }

        @Test
        void emptyIngredientAndStep() throws IOException {
            ParsedRecord r = csv.parse(viaTempFile(HEADER + "\nGaps,1,2,EASY,1,Egg:1:pcs||Salt:1:tsp,Cook||Serve|\n"), MAX).get(0);
            assertThat(r.issues()).extracting(ImportIssue::code).contains("EMPTY_INGREDIENT", "EMPTY_STEP");
        }

        @Test
        void malformedIngredientItemAndQuantity() throws IOException {
            ParsedRecord r = csv.parse(viaTempFile(HEADER + "\nItems,1,2,EASY,1,Egg-1-pcs|Salt:lots:tsp|:1:pcs,Cook\n"), MAX).get(0);
            assertThat(r.issues()).extracting(ImportIssue::code).contains("MALFORMED_INGREDIENT", "INVALID_NUMBER", "EMPTY_INGREDIENT");
        }

        @Test
        void wrongColumnCount() throws IOException {
            ParsedRecord r = csv.parse(viaTempFile(HEADER + "\nShort,1,2\n"), MAX).get(0);
            assertThat(r.row()).isNull();
            assertThat(r.issues()).extracting(ImportIssue::code).containsExactly("COLUMN_COUNT");
        }

        @Test
        void missingAndUnknownHeaders() {
            assertThatThrownBy(() -> csv.parse("title,prep_time_mins\nA,1\n", MAX))
                    .isInstanceOf(FileRejectedException.class).hasMessageContaining("cook_time_mins");
            assertThatThrownBy(() -> csv.parse(HEADER + ",is_official\nA,1,2,EASY,1,Egg:1:pcs,Cook,true\n", MAX))
                    .isInstanceOf(FileRejectedException.class).hasMessageContaining("is_official");
        }

        @Test
        void unclosedQuoteIsMalformed() {
            assertThatThrownBy(() -> csv.parse(HEADER + "\n\"Broken,1,2,EASY,1,Egg:1:pcs,Cook\n", MAX))
                    .isInstanceOf(FileRejectedException.class)
                    .satisfies(e -> assertThat(((FileRejectedException) e).getCode()).isEqualTo("MALFORMED_FILE"));
        }

        @Test
        void binaryContentRejected() {
            assertThatThrownBy(() -> ImportText.decodeUtf8(new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x41}))
                    .isInstanceOf(FileRejectedException.class);
        }

        @ParameterizedTest(name = "{0} data rows accepted")
        @ValueSource(ints = {1, 50, 500})
        @DisplayName("Batch BVA: 1, 50 and 500 rows are accepted")
        void acceptedSizes(int rows) throws IOException {
            assertThat(csv.parse(viaTempFile(csvRows(rows)), MAX)).hasSize(rows).noneMatch(ParsedRecord::hasErrors);
        }

        @Test
        @DisplayName("Batch BVA: 0 data rows (header only) is rejected")
        void zeroRows() throws IOException {
            String text = viaTempFile(HEADER + "\n");
            assertThatThrownBy(() -> csv.parse(text, MAX)).isInstanceOf(FileRejectedException.class)
                    .satisfies(e -> assertThat(((FileRejectedException) e).getCode()).isEqualTo("EMPTY_FILE"));
        }

        @Test
        @DisplayName("Batch BVA: 501 data rows is rejected before any processing")
        void tooManyRows() throws IOException {
            String text = viaTempFile(csvRows(501));
            assertThatThrownBy(() -> csv.parse(text, MAX)).isInstanceOf(FileRejectedException.class)
                    .satisfies(e -> assertThat(((FileRejectedException) e).getCode()).isEqualTo("TOO_MANY_ROWS"));
        }

        @Test
        @DisplayName("Business rules are applied and mapped to CSV column names")
        void businessValidation() throws IOException {
            String text = viaTempFile(new String(ImportParsingTest.class.getResourceAsStream("/bulk_recipes_invalid.csv").readAllBytes(), StandardCharsets.UTF_8));
            List<ParsedRecord> records = csv.parse(text, MAX);
            assertThat(records).hasSize(7);
            List<ImportIssue> tooLong = rowValidator.validate(records.get(1).row(), ImportFileType.CSV);
            assertThat(tooLong).extracting(ImportIssue::code).containsExactly("TOTAL_TIME_EXCEEDED");
            assertThat(tooLong.get(0).field()).isEqualTo("total_time_mins");
            assertThat(rowValidator.validate(records.get(0).row(), ImportFileType.CSV)).isEmpty();
            assertThat(rowValidator.validate(records.get(5).row(), ImportFileType.CSV)).extracting(ImportIssue::field)
                    .containsExactly("difficulty_level");
        }
    }

    @Nested
    @DisplayName("JSON")
    class Json {

        private List<ParsedRecord> parse(String s) {
            return jsonParser.parse(s.getBytes(StandardCharsets.UTF_8), MAX);
        }

        @Test
        @DisplayName("Valid fixture: steps sorted by step_number, total_time_mins ignored with a warning")
        void fixture() throws IOException {
            byte[] bytes = ImportParsingTest.class.getResourceAsStream("/bulk_recipes.json").readAllBytes();
            List<ParsedRecord> records = jsonParser.parse(bytes, MAX);
            assertThat(records).hasSize(2).noneMatch(ParsedRecord::hasErrors);
            assertThat(records.get(0).row().steps().get(0).instruction()).isEqualTo("Heat oil in a pan.");
            assertThat(records.get(1).issues()).extracting(ImportIssue::code).containsExactly("IGNORED_FIELD");
            assertThat(records.get(1).row().baseServings()).isEqualTo(1);
            assertThat(records.get(0).lineNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("Wrong types, forbidden fields, unknown keys and non-object elements are row errors")
        void invalidFixture() throws IOException {
            byte[] bytes = ImportParsingTest.class.getResourceAsStream("/bulk_recipes_invalid.json").readAllBytes();
            List<ParsedRecord> records = jsonParser.parse(bytes, MAX);
            assertThat(records).hasSize(6);
            assertThat(records.get(0).hasErrors()).isFalse();
            assertThat(records.get(1).issues()).extracting(ImportIssue::field).contains("prep_time_mins");
            assertThat(records.get(2).issues()).extracting(ImportIssue::code).containsOnly("FORBIDDEN_FIELD");
            assertThat(rowValidator.validate(records.get(3).row(), ImportFileType.JSON)).extracting(ImportIssue::code)
                    .contains("TOTAL_TIME_EXCEEDED");
            assertThat(records.get(4).issues()).extracting(ImportIssue::code).contains("UNKNOWN_FIELD");
            assertThat(rowValidator.validate(records.get(4).row(), ImportFileType.JSON)).extracting(ImportIssue::field).contains("steps");
            assertThat(records.get(5).row()).isNull();
        }

        @Test
        void malformedJson() {
            assertThatThrownBy(() -> parse("[ { \"title\": \"x\", } ")).isInstanceOf(FileRejectedException.class)
                    .hasMessageContaining("line");
        }

        @Test
        void nonArrayRoot() {
            assertThatThrownBy(() -> parse("{\"title\":\"x\"}")).isInstanceOf(FileRejectedException.class)
                    .satisfies(e -> assertThat(((FileRejectedException) e).getCode()).isEqualTo("INVALID_ROOT"));
        }

        @Test
        void emptyArray() {
            assertThatThrownBy(() -> parse("[]")).isInstanceOf(FileRejectedException.class);
        }

        @Test
        void tooManyObjects() {
            String item = "{\"title\":\"T\",\"prep_time_mins\":1,\"cook_time_mins\":1,\"difficulty_level\":\"EASY\",\"estimated_cost_bdt\":1,"
                    + "\"ingredients\":[{\"name\":\"Egg\",\"quantity\":1,\"unit\":\"pcs\"}],\"steps\":[{\"instruction_text\":\"x\"}]}";
            String ok = "[" + String.join(",", java.util.Collections.nCopies(500, item)) + "]";
            assertThat(parse(ok)).hasSize(500);
            String tooMany = "[" + String.join(",", java.util.Collections.nCopies(501, item)) + "]";
            assertThatThrownBy(() -> parse(tooMany)).isInstanceOf(FileRejectedException.class).hasMessageContaining("500");
        }

        @Test
        void duplicateStepNumbers() {
            List<ParsedRecord> r = parse("[{\"title\":\"Dup\",\"prep_time_mins\":1,\"cook_time_mins\":1,\"difficulty_level\":\"EASY\","
                    + "\"estimated_cost_bdt\":1,\"ingredients\":[{\"name\":\"Egg\",\"quantity\":1,\"unit\":\"pcs\"}],"
                    + "\"steps\":[{\"step_number\":1,\"instruction_text\":\"a\"},{\"step_number\":1,\"instruction_text\":\"b\"}]}]");
            assertThat(r.get(0).issues()).extracting(ImportIssue::code).contains("DUPLICATE_STEP_NUMBER");
        }
    }

    @Nested
    @DisplayName("File type checks")
    class FileTypes {
        @Test
        void extensionAndContentType() {
            assertThat(ImportService.detectType("a.csv", "text/csv")).isEqualTo(ImportFileType.CSV);
            assertThat(ImportService.detectType("a.json", "application/json")).isEqualTo(ImportFileType.JSON);
            assertThatThrownBy(() -> ImportService.detectType("a.xlsx", "application/vnd.ms-excel")).isInstanceOf(ApiException.class);
            assertThatThrownBy(() -> ImportService.detectType("a.csv", "image/png")).isInstanceOf(ApiException.class);
        }

        @Test
        @DisplayName("Extension alone is not proof of format")
        void sniffing() {
            assertThatThrownBy(() -> ImportService.sniff("[{\"a\":1}]", ImportFileType.CSV)).isInstanceOf(FileRejectedException.class);
            assertThatThrownBy(() -> ImportService.sniff("title,a\nx,y", ImportFileType.JSON)).isInstanceOf(FileRejectedException.class);
            assertThatThrownBy(() -> ImportService.sniff("   ", ImportFileType.CSV)).isInstanceOf(FileRejectedException.class);
        }

        @Test
        void filenameSanitising() {
            assertThat(ImportText.sanitizeFilename("..\\..\\evil<script>.csv")).isEqualTo("evil_script_.csv");
            assertThat(ImportText.sanitizeFilename(null)).isEqualTo("upload");
        }

        @Test
        @DisplayName("Final job status reflects the real outcome")
        void finalStatus() {
            assertThat(ImportService.finalStatus(5, 0, 0)).isEqualTo(ImportStatus.COMPLETED);
            assertThat(ImportService.finalStatus(4, 1, 0)).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
            assertThat(ImportService.finalStatus(4, 0, 1)).isEqualTo(ImportStatus.COMPLETED_WITH_ERRORS);
            assertThat(ImportService.finalStatus(0, 3, 0)).isEqualTo(ImportStatus.FAILED);
        }
    }
}
