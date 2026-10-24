package io.github.luneto10.machiavillager.domain.behavior;

import java.util.Optional;

/**
 * Fairness rules for pulling a player to a meeting. Every rule must hold, so a summons never catches
 * someone mid-fight, mid-flight, in a vehicle, in another dimension, after opting out, too soon after the
 * last one, or while the NPC himself is in danger.
 *
 * @param sameDimension the player is in the NPC's dimension
 * @param inCombat the player is fighting
 * @param flying the player is flying or gliding
 * @param inVehicle the player is riding something
 * @param optedOut the player has turned summons off
 * @param cooldownReady enough time has passed since the last summons
 * @param npcInDanger the NPC is under threat right now
 */
public record SummonCheck(
        boolean sameDimension,
        boolean inCombat,
        boolean flying,
        boolean inVehicle,
        boolean optedOut,
        boolean cooldownReady,
        boolean npcInDanger) {

    /** True only when every fairness rule holds. */
    public boolean allowed() {
        return blocker().isEmpty();
    }

    /** The first rule that refuses the summons, in the order the rules are checked; empty when allowed. */
    public Optional<SummonBlocker> blocker() {
        // Checked in a fixed order so the reason shown to the player is always the same for the same facts.
        if (!sameDimension) {
            return Optional.of(SummonBlocker.OTHER_DIMENSION);
        }
        if (inCombat) {
            return Optional.of(SummonBlocker.IN_COMBAT);
        }
        if (flying) {
            return Optional.of(SummonBlocker.FLYING);
        }
        if (inVehicle) {
            return Optional.of(SummonBlocker.IN_VEHICLE);
        }
        if (optedOut) {
            return Optional.of(SummonBlocker.OPTED_OUT);
        }
        if (!cooldownReady) {
            return Optional.of(SummonBlocker.COOLDOWN);
        }
        if (npcInDanger) {
            return Optional.of(SummonBlocker.NPC_IN_DANGER);
        }
        return Optional.empty();
    }
}
