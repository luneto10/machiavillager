package io.github.luneto10.machiavillager.domain;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * A set of short lines the NPC can say, picked at random. Pure Java: no Minecraft imports.
 * Later the LLM will fill these in batches; today they are written by hand.
 */
public record Barks(List<String> lines) {
    public Barks {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Barks needs at least one line");
        }
        lines = List.copyOf(lines);
    }

    public Barks(String... lines) {
        this(List.of(lines));
    }

    public String pick(RandomGenerator random) {
        return lines.get(random.nextInt(lines.size()));
    }
}
