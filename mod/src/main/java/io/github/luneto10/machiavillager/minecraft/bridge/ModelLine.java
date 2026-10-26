package io.github.luneto10.machiavillager.minecraft.bridge;

import io.github.luneto10.machiavillager.domain.bridge.ModelLabel;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Remembers the label of the last accepted reply, for the dev boss bar. Written by ActionExecutor and read by the
 * overlay, both on the server thread, so a plain field is enough.
 */
public final class ModelLine {
    // Before any reply the bar says so, instead of showing nothing.
    private String text = ModelLabel.NO_REPLY_YET;

    /** Records the model that wrote the last reply, or none for a canned line. */
    public void record(Optional<String> model, OptionalInt latencyMs) {
        text = ModelLabel.forReply(
                Objects.requireNonNull(model, "model"), Objects.requireNonNull(latencyMs, "latencyMs"));
    }

    /** The text the dev boss bar shows. */
    public String text() {
        return text;
    }
}
