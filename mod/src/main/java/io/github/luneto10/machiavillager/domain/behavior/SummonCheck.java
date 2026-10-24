package io.github.luneto10.machiavillager.domain.behavior;

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
        // Each rule is a separate reason to refuse, so all of them must pass together.
        return sameDimension && !inCombat && !flying && !inVehicle && !optedOut && cooldownReady && !npcInDanger;
    }
}
