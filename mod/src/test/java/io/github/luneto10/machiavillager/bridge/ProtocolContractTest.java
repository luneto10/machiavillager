package io.github.luneto10.machiavillager.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtocolContractTest {
    private static final Path SCHEMA_RELATIVE = Path.of("shared", "protocol.schema.json");
    private static final Path VALID_RELATIVE = Path.of("shared", "examples", "valid");
    private static final Path INVALID_RELATIVE = Path.of("shared", "examples", "invalid");

    // A field no message defines. The schema must reject it everywhere (additionalProperties false).
    private static final String UNEXPECTED_FIELD = "unexpected_field";

    // Loaded once: the schema is the same for every example.
    private static final Schema PROTOCOL = loadProtocolSchema();

    // The six structural rules, mirrored from backend/tests/test_protocol_contract.py.
    // Each mutant of a valid example must fail validation. Each one edits a copy, never the original.
    private static final Map<String, UnaryOperator<JsonObject>> MUTATIONS = Map.of(
            "add_top_level_field", node -> edited(node, copy -> copy.addProperty(UNEXPECTED_FIELD, true)),
            "v_is_2", node -> edited(node, copy -> copy.addProperty("v", 2)),
            "remove_v", node -> edited(node, copy -> copy.remove("v")),
            "remove_type", node -> edited(node, copy -> copy.remove("type")),
            "type_not_a_type", node -> edited(node, copy -> copy.addProperty("type", "not_a_type")),
            "nested_unexpected_field", node -> edited(node, ProtocolContractTest::addFieldToNestedObjects));

    @ParameterizedTest(name = "{0}")
    @MethodSource("validExamples")
    void validExample_passesSchema(Path example) throws IOException {
        var errors = PROTOCOL.validate(Files.readString(example), InputFormat.JSON, this::enableFormats);

        assertTrue(errors.isEmpty(), () -> example.getFileName() + " should be valid: " + errors);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidExamples")
    void invalidExample_failsSchema(Path example) throws IOException {
        var errors = PROTOCOL.validate(Files.readString(example), InputFormat.JSON, this::enableFormats);

        assertFalse(errors.isEmpty(), () -> example.getFileName() + " should be rejected");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mutants")
    void structuralMutant_failsSchema(String label, String json) {
        var errors = PROTOCOL.validate(json, InputFormat.JSON, this::enableFormats);

        assertFalse(errors.isEmpty(), () -> label + " should be rejected");
    }

    static List<Path> validExamples() {
        return examplesIn(VALID_RELATIVE);
    }

    static List<Path> invalidExamples() {
        return examplesIn(INVALID_RELATIVE);
    }

    static Stream<Arguments> mutants() {
        return validExamples().stream().flatMap(ProtocolContractTest::mutantsOf);
    }

    // A mutation that leaves the message unchanged (no nested object to edit) is skipped, not passed.
    private static Stream<Arguments> mutantsOf(Path example) {
        JsonObject original = readObject(example);
        return MUTATIONS.entrySet().stream()
                .map(mutation -> Map.entry(mutation.getKey(), mutation.getValue().apply(original)))
                .filter(mutant -> !mutant.getValue().equals(original))
                .map(mutant -> Arguments.of(
                        example.getFileName() + ":" + mutant.getKey(), mutant.getValue().toString()));
    }

    private static JsonObject edited(JsonObject node, Consumer<JsonObject> edit) {
        JsonObject copy = node.deepCopy();
        edit.accept(copy);
        return copy;
    }

    private static void addFieldToNestedObjects(JsonObject copy) {
        for (var entry : copy.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                entry.getValue().getAsJsonObject().addProperty(UNEXPECTED_FIELD, true);
            }
        }
    }

    // The 2019-09+ dialects only annotate "format" unless told otherwise; date-time must be asserted.
    private void enableFormats(com.networknt.schema.ExecutionContext context) {
        context.executionConfig(config -> config.formatAssertionsEnabled(true));
    }

    private static JsonObject readObject(Path example) {
        try {
            return JsonParser.parseString(Files.readString(example)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Schema loadProtocolSchema() {
        try {
            String schemaText = Files.readString(repoRoot().resolve(SCHEMA_RELATIVE));
            return SchemaRegistry.withDialect(Dialects.getDraft202012())
                    .getSchema(schemaText, InputFormat.JSON);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Path> examplesIn(Path relativeDir) {
        Path dir = repoRoot().resolve(relativeDir);
        try (var files = Files.list(dir)) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Gradle runs tests from the mod/ folder; walk up so the lookup survives moving the module.
    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(SCHEMA_RELATIVE))) {
                return dir;
            }
        }
        throw new IllegalStateException("shared/protocol.schema.json not found above the working directory");
    }
}
