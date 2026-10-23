package io.github.luneto10.machiavillager.domain.behavior;

import java.util.Objects;
import java.util.UUID;

/**
 * A decision about what the NPC should do next. This is a command object: it describes the action,
 * and the Minecraft layer executes it later.
 */
public sealed interface NpcAction permits NpcAction.Follow, NpcAction.Wander, NpcAction.Bark {

    /** Walk after this player. */
    record Follow(UUID target) implements NpcAction {
        public Follow {
            Objects.requireNonNull(target, "target");
        }
    }

    /** Stroll around. */
    record Wander() implements NpcAction {
    }

    /** Say a line to this player. */
    record Bark(UUID listener) implements NpcAction {
        public Bark {
            Objects.requireNonNull(listener, "listener");
        }
    }
}
