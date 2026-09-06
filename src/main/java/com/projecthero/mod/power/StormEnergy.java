package com.projecthero.mod.power;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.world.entity.player.Player;

/**
 * The "Storm Energy" resource bar from THOR_DESIGN.md section 4. Flight drains it, Lightning
 * Strike costs a chunk of it, and it slowly regenerates (faster in rain/thunder).
 */
public final class StormEnergy {
	/** v0.6.22: bumped 100 -> 250 so Thor can sustain flight/laser and still fuel the ultimate. */
	public static final float MAX = 250.0f;

	public static final float FLIGHT_DRAIN_PER_SECOND = 1.0f;
	public static final float LIGHTNING_COST = 18.0f;
	/** Continuous drain while Lightning Laser is firing -- faster than flight since it's a strong sustained effect. */
	public static final float LASER_DRAIN_PER_SECOND = 6.0f;
	/** v0.6.23: God of Thunder's Wrath needs at least this much and drains exactly this much on cast. */
	public static final float GOD_OF_THUNDER_MIN = 150.0f;
	public static final float GOD_OF_THUNDER_COST = 150.0f;
	/** Thunderclap's Storm Energy cost. */
	public static final float THUNDERCLAP_COST = 10.0f;

	public static final float REGEN_PER_SECOND = 4.0f;
	public static final float REGEN_PER_SECOND_RAIN = 6.0f;
	public static final float REGEN_PER_SECOND_THUNDER = 8.0f;
	/** Regen while a Storm Call is active -- faster than even the passive thunderstorm bonus. */
	public static final float REGEN_PER_SECOND_STORM_CALL = 10.0f;

	private StormEnergy() {
	}

	public static float get(Player player) {
		return player.getAttachedOrElse(ModAttachments.STORM_ENERGY, MAX);
	}

	public static void set(Player player, float value) {
		player.setAttached(ModAttachments.STORM_ENERGY, Math.max(0.0f, Math.min(MAX, value)));
	}

	public static boolean has(Player player, float amount) {
		return get(player) >= amount;
	}

	public static void spend(Player player, float amount) {
		set(player, get(player) - amount);
	}

	public static void add(Player player, float amount) {
		set(player, get(player) + amount);
	}
}
