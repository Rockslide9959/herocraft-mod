package com.herocraft.mod.firearm;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The seam between the generic firearm engine (this package, built first) and the Punisher power
 * (built later). The engine calls these for every shot / reload; the default implementation is
 * neutral (a plain player with a crafted gun). {@code com.herocraft.mod.punisher} installs a real
 * implementation in its init so a Punisher gets infinite reserve ammo, faster handling, the No Mercy
 * execution bonus and headshot / kill callbacks for Vigilante Training.
 *
 * <p>Keeping this here (rather than importing {@code Punisher} from the firearm package) means the
 * firearm engine has zero compile-time dependency on the power, so Phase 1 builds and ships on its
 * own and normal-player firearm behaviour is fully defined without the power existing.
 */
public interface FirearmHooks {
	FirearmHooks DEFAULT = new FirearmHooks() { };

	/** Installed by {@code Punisher.initialize()}. Never null. */
	FirearmHooks[] ACTIVE = { DEFAULT };

	static FirearmHooks get() {
		return ACTIVE[0];
	}

	static void install(FirearmHooks hooks) {
		ACTIVE[0] = hooks == null ? DEFAULT : hooks;
	}

	/** True if this player reloads without consuming any ammo item (Punisher weapon proficiency). */
	default boolean infiniteReserve(ServerPlayer player) {
		return false;
	}

	/** Multiplier on reload duration (&lt; 1 = faster). Punisher: 0.85; with Adrenaline stacking guarded. */
	default float reloadSpeedFactor(ServerPlayer player) {
		return 1.0f;
	}

	/** Multiplier on all spread (rest + recoil). Punisher ballistic expertise trims this a little. */
	default float spreadFactor(ServerPlayer player, boolean aiming) {
		return 1.0f;
	}

	/** Multiplier on accumulated recoil growth (&lt; 1 = steadier). */
	default float recoilFactor(ServerPlayer player) {
		return 1.0f;
	}

	/** Multiplier on outgoing firearm damage before the per-hit headshot/falloff maths. */
	default float damageFactor(ServerPlayer player, LivingEntity target, boolean headshot) {
		return 1.0f;
	}

	/** Called after a shot is fired (ammo already spent). {@code weaponId} is a {@link Firearms} key. */
	default void onFired(ServerPlayer player, String weaponId, boolean aiming) {
	}

	/** Multiplier on the fire-rate interval (&lt; 1 = faster). Suppressive Fire uses this. */
	default float fireIntervalFactor(ServerPlayer player) {
		return 1.0f;
	}

	/** Called for every living target a shot hits (before it may have died). */
	default void onHit(ServerPlayer player, LivingEntity target, boolean headshot) {
	}

	/** Called once per shot that landed a headshot on a living target. */
	default void onHeadshot(ServerPlayer player, LivingEntity target) {
	}

	/** Called when a firearm shot from this player kills a living target. */
	default void onFirearmKill(ServerPlayer player, LivingEntity target) {
	}

	/** Called when this player crafts a firearm ({@code weaponId} is a {@link Firearms} key). */
	default void onCrafted(ServerPlayer player, String weaponId) {
	}
}
