package io.github.luneto10.machiavillager.bridge;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.dialect.Dialects;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Checks one wire message against the protocol schema bundled in the mod jar. The schema holds every rule the
 * bridge enforces, so this is the only validator. Loaded once and safe to share between threads.
 */
final class SchemaValidator {
    // Classpath path of the schema. The build copies shared/protocol.schema.json here; nobody edits the copy.
    static final String SCHEMA_RESOURCE = "/machiavillager/protocol.schema.json";

    // The one error reported when the text is not JSON at all.
    static final String NOT_JSON = "message is not valid JSON";

    // The first error is enough to name the broken rule, so each one is cut to a short length.
    private static final int MAX_ERROR_LENGTH = 200;

    private final Schema schema;

    /** Loads the bundled schema. Fails at once when it is missing, because nothing can be checked without it. */
    SchemaValidator() {
        this.schema = SchemaRegistry.withDialect(Dialects.getDraft202012())
                .getSchema(readSchema(), InputFormat.JSON);
    }

    /** Every rule the message breaks, as short text. Empty when the message is valid. */
    List<String> errors(String json) {
        try {
            return schema.validate(json, InputFormat.JSON, context ->
                            // Format is only annotated by default; date-time must be asserted, as the contract says.
                            context.executionConfig(config -> config.formatAssertionsEnabled(true)))
                    .stream()
                    .map(error -> shorten(String.valueOf(error.getMessage())))
                    .toList();
        } catch (RuntimeException unparsable) {
            // The library throws on text it cannot parse at all; that is one rule, not a crash.
            return List.of(NOT_JSON);
        }
    }

    private static String shorten(String message) {
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    // Reads the bundled schema text. A missing resource means a broken build, so it fails loudly.
    private static String readSchema() {
        try (InputStream stream = SchemaValidator.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("bundled protocol schema missing: " + SCHEMA_RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
