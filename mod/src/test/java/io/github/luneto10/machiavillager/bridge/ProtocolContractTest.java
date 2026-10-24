package io.github.luneto10.machiavillager.bridge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ProtocolContractTest {
    private static final Path SCHEMA_RELATIVE = Path.of("shared", "protocol.schema.json");
    private static final Path VALID_RELATIVE = Path.of("shared", "examples", "valid");
    private static final Path INVALID_RELATIVE = Path.of("shared", "examples", "invalid");

    // Loaded once: the schema is the same for every example.
    private static final Schema PROTOCOL = loadProtocolSchema();

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

    static List<Path> validExamples() {
        return examplesIn(VALID_RELATIVE);
    }

    static List<Path> invalidExamples() {
        return examplesIn(INVALID_RELATIVE);
    }

    // The 2019-09+ dialects only annotate "format" unless told otherwise; date-time must be asserted.
    private void enableFormats(com.networknt.schema.ExecutionContext context) {
        context.executionConfig(config -> config.formatAssertionsEnabled(true));
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
