package io.github.luneto10.machiavillager.domain.lines;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads the saved line pools back. Pure Java: a save may be old or hand-edited, so nothing in it is trusted. */
public final class SavedLinePools {
    private SavedLinePools() {
    }

    /**
     * The pools whose id is a known key and that keep at least one valid line. Unknown ids are skipped, and so are
     * invalid lines, which are dropped by the same rules a delivered batch passes.
     */
    public static Map<LineKey, List<String>> parse(Map<String, List<String>> saved) {
        Objects.requireNonNull(saved, "saved");

        Map<LineKey, List<String>> pools = new HashMap<>();
        saved.forEach((id, lines) -> LineKey.fromId(id).ifPresent(key -> {
            // Checked here too: a line that was too long or used a foreign placeholder must never reach the bank.
            List<String> valid = LineRules.validLines(key.kind(), lines);
            if (!valid.isEmpty()) {
                pools.put(key, valid);
            }
        }));
        return Map.copyOf(pools);
    }
}
