package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.shield.ForceBubble;
import com.projecthero.mod.squad.SquadManager;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Directional Shield (Z, held) and Protective Dome (Shift+Z, instant + timed). Both keep their live HP
 * in the non-persisted {@link ModAttachments#GREEN_LANTERN_BARRIER_HP} attachment (0 = inactive) so
 * neither survives a relog; {@link ModAttachments#GREEN_LANTERN_BARRIER_IS_DOME} tells the HUD/renderer
 * which shape is up. Damage mitigation itself lives in {@code GreenLanternDamage} (the
 * cancel-and-reapply-smaller pattern every hero-tier power in this mod uses for partial absorption).
 */
public final class GreenLanternShield {
	/** Lantern-Corps green, matching {@code GreenLanternHud}/{@code GreenLanternCombat}'s green (0x35F075). */
	private static final ParticleOptions GREEN_DUST = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.6f);

	private static final String SHIELD_COOLDOWN = "directional_shield";
	private static final String DOME_COOLDOWN = "protective_dome";

	/** Owner UUID -> absolute game-time the dome was deployed -- drives the v0.11.7 expand-out animation. */
	private static final Map<UUID, Long> DOME_DEPLOY_TICK = new ConcurrentHashMap<>();

	private GreenLanternShield() {
	}

	public static void clearSessionState() {
		DOME_DEPLOY_TICK.clear();
	}

	/** The dome's current radius -- 0 the instant it deploys, growing linearly to {@link GreenLanternConfig#DOME_RADIUS}
	 *  over {@link GreenLanternConfig#DOME_EXPAND_TICKS} (v0.11.7, explicit user request: "make the dome
	 *  ability expand from the player to a 10 radius"). Already at full radius for anyone this map has no
	 *  entry for (e.g. mid-tick after a relog), so a missing entry never reads as "still expanding forever". */
	public static double currentDomeRadius(ServerPlayer player) {
		Long deployedAt = DOME_DEPLOY_TICK.get(player.getUUID());
		if (deployedAt == null) {
			return GreenLanternConfig.DOME_RADIUS;
		}
		long elapsed = player.level().getGameTime() - deployedAt;
		if (elapsed >= GreenLanternConfig.DOME_EXPAND_TICKS) {
			return GreenLanternConfig.DOME_RADIUS;
		}
		return GreenLanternConfig.DOME_RADIUS * Math.max(0L, elapsed) / (double) GreenLanternConfig.DOME_EXPAND_TICKS;
	}

	public static boolean isActive(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f) > 0f;
	}

	public static boolean isDome(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
	}

	public static float hp(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
	}

	// ---------------- Directional Shield (held) ----------------

	/** The shared shield/dome uptime meter, 0..1. Full for anyone this hasn't been set for yet. */
	public static float meter(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_BARRIER_METER, 1f);
	}

	/**
	 * v0.15.15: Z pressed -- raise the hard-light bubble (the shared {@link ForceBubble} in
	 * {@link ForceBubble.Style#GREEN_LANTERN}). It stays up while Z is held, draining
	 * {@link GreenLanternConfig#BUBBLE_SHIELD_DRAIN_PER_SEC}; no up-front cost, no HP, no uptime meter. It is still the
	 * "barrier" attachment (HP > 0, not a dome) so the HUD and pose code see it like the old shield.
	 */
	public static void startShield(ServerPlayer player) {
		if (isActive(player)) {
			return;
		}
		if (!GreenLantern.abilityReady(player, SHIELD_COOLDOWN)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.shield_recharging");
			return;
		}
		if (bubbleHp(player) <= 0f) {
			return;
		}
		if (!GreenLanternEnergy.canSpend(player, GreenLanternConfig.BUBBLE_SHIELD_DRAIN_PER_SEC / 20f)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, GreenLanternConfig.SHIELD_HP);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		ForceBubble.raise(player, ForceBubble.Style.GREEN_LANTERN);
	}

	/** Z released: the bubble drops and goes on its 8-second cooldown (v0.15.16); its HP starts to regenerate. */
	public static void stopShield(ServerPlayer player) {
		if (isActive(player) && !isDome(player)) {
			endBarrier(player, false);
			GreenLantern.triggerCooldown(player, SHIELD_COOLDOWN, GreenLanternConfig.SHIELD_BREAK_COOLDOWN_TICKS);
			ForceBubble.drop(player, ForceBubble.Style.GREEN_LANTERN);
		}
	}

	/** The Z bubble's current HP (full for anyone it has never been set for). */
	public static float bubbleHp(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_SHIELD_HP, GreenLanternConfig.SHIELD_HP);
	}

	/**
	 * The bubble stopped a hit worth {@code amount}: takes it off the bubble's own HP. At 0 the bubble breaks (drops, the
	 * 8-second cooldown starts). Returns what it could NOT soak -- the caller re-applies that to the player, so a nearly
	 * dead bubble does not no-sell a huge hit.
	 */
	public static float absorbBubble(ServerPlayer player, float amount) {
		float hp = bubbleHp(player);
		float soaked = Math.min(hp, amount);
		float left = hp - soaked;
		player.setAttached(ModAttachments.GREEN_LANTERN_SHIELD_HP, left);
		if (left <= 0f && isBubble(player)) {
			endBarrier(player, true);
		}
		return amount - soaked;
	}

	/** What a projectile the bubble destroys in flight costs it: an arrow's damage at its speed, 4 for anything else. */
	private static float projectileCost(net.minecraft.world.entity.projectile.Projectile proj) {
		if (proj instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
			return (float) Math.max(2.0, Math.ceil(arrow.getBaseDamage() * Math.min(3.0, proj.getDeltaMovement().length())));
		}
		return 4f;
	}

	/** Whether the Z bubble (not the dome) is up. */
	public static boolean isBubble(ServerPlayer player) {
		return isActive(player) && !isDome(player);
	}

	/** Per-tick upkeep while the bubble is held: 40 charge/s, projectiles inside it destroyed, drops when charge runs out. */
	public static void tickShieldUpkeep(ServerPlayer player) {
		if (!isActive(player) || isDome(player)) {
			return;
		}
		if (!GreenLanternEnergy.drainTick(player, GreenLanternConfig.BUBBLE_SHIELD_DRAIN_PER_SEC / 20f)) {
			endBarrier(player, false);
			GreenLantern.triggerCooldown(player, SHIELD_COOLDOWN, GreenLanternConfig.SHIELD_BREAK_COOLDOWN_TICKS);
			ForceBubble.drop(player, ForceBubble.Style.GREEN_LANTERN);
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		ForceBubble.tick(player, ForceBubble.Style.GREEN_LANTERN, proj -> absorbBubble(player, projectileCost(proj)));
	}

	// ---------------- Protective Dome (timed) ----------------

	public static void deployDome(ServerPlayer player) {
		if (isActive(player) || !GreenLantern.abilityReady(player, DOME_COOLDOWN)) {
			return;
		}
		if (meter(player) <= 0f) {
			GreenLanternEnergy.feedback(player, "message.projecthero.green_lantern.barrier_recharging");
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.DOME_INITIAL_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(player);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, GreenLanternConfig.DOME_HP);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, true);
		DOME_DEPLOY_TICK.put(player.getUUID(), player.level().getGameTime());
		ServerLevel level = player.serverLevel();
		// v0.15.1: no particle outline -- the client draws the dome as a hard-light globe (GreenLanternDomeRenderer)
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	/**
	 * Per-tick upkeep while the dome is up. v0.11.7: also enforces the dome's own exclusion zone every
	 * tick ("only allow the players squad members inside it") -- anyone else caught within the current
	 * (possibly still-expanding) radius is pushed outward, which during the expansion window is exactly
	 * "push out nearby entities as it expands" and for the rest of the dome's lifetime keeps outsiders
	 * from walking back in. v0.11.8: the fixed max-duration cooldown ("dome_expiry") is gone -- the
	 * shared uptime meter reaching 0 (via {@link #drainMeter}) now IS the max-duration expiry.
	 */
	public static void tickDomeUpkeep(ServerPlayer player) {
		if (!isActive(player) || !isDome(player)) {
			return;
		}
		double radius = currentDomeRadius(player);
		pushOutNonSquad(player, radius);
		if (!GreenLanternEnergy.drainTick(player, GreenLanternConfig.DOME_UPKEEP_PER_SEC / 20f)) {
			endBarrier(player, true);
			return;
		}
		drainMeter(player);
	}

	/**
	 * Drains the shared uptime meter by one tick's worth while a barrier is active -- forces it down the
	 * instant the meter empties, which is how the 22-second cap (v0.11.8) is actually enforced.
	 */
	private static void drainMeter(ServerPlayer player) {
		float meter = meter(player) - 1f / GreenLanternConfig.BARRIER_METER_MAX_TICKS;
		if (meter <= 0f) {
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_METER, 0f);
			endBarrier(player, true);
		} else {
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_METER, meter);
		}
	}

	/**
	 * Refills the shared uptime meter while neither the shield nor the dome is up -- called every tick
	 * for every bonded Green Lantern from {@code GreenLanternAbilityManager#serverTick}, regardless of
	 * whether a barrier has ever been used, so a fresh ring is simply always at full meter. v0.11.8,
	 * explicit user request ("the bar passively recharges as the player stops using the dome").
	 */
	public static void tickMeterRegen(ServerPlayer player) {
		if (isActive(player)) {
			return;
		}
		float bubble = bubbleHp(player); // v0.15.16: the Z bubble heals while it is down
		if (bubble < GreenLanternConfig.SHIELD_HP) {
			player.setAttached(ModAttachments.GREEN_LANTERN_SHIELD_HP,
					Math.min(GreenLanternConfig.SHIELD_HP, bubble + GreenLanternConfig.SHIELD_REGEN_PER_SEC / 20f));
		}
		float meter = meter(player);
		if (meter >= 1f) {
			return;
		}
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_METER,
				Math.min(1f, meter + 1f / GreenLanternConfig.BARRIER_METER_MAX_TICKS));
	}

	/** Knocks anyone within {@code radius} of the dome's live centre outward, unless they're the owner or a squadmate. */
	private static void pushOutNonSquad(ServerPlayer owner, double radius) {
		if (radius <= 0.0) {
			return;
		}
		Vec3 center = owner.position().add(0, 1.0, 0);
		var squad = owner.getServer() == null ? null : SquadManager.get(owner.getServer()).squadOf(owner.getUUID());
		for (LivingEntity e : AbilityHelpers.living(owner.serverLevel(), center, radius,
				le -> com.projecthero.mod.combat.HeroTargets.canHarm(owner, le))) { // v0.14.20: not your own pets
			if (e instanceof Player p && squad != null && squad.has(p.getUUID())) {
				continue;
			}
			AbilityHelpers.knockbackFrom(e, center, GreenLanternConfig.DOME_PUSH_SPEED);
		}
	}

	/**
	 * Absorb up to {@code amount} of incoming damage into the barrier's HP. Returns whatever could NOT
	 * be absorbed (0 if the barrier had enough HP left) -- the caller (see {@code GreenLanternDamage})
	 * is responsible for re-applying that overflow to the player, exactly like every other Hero-Tier
	 * power's cancel-and-reapply-smaller damage hook. A near-dead shield must not no-sell a huge hit.
	 */
	public static float absorb(ServerPlayer player, float amount) {
		float hp = hp(player);
		float absorbed = Math.min(hp, amount);
		float remainingHp = hp - absorbed;
		if (remainingHp <= 0f) {
			endBarrier(player, true);
		} else {
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, remainingHp);
		}
		return amount - absorbed;
	}

	private static void endBarrier(ServerPlayer player, boolean broke) {
		boolean dome = isDome(player);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		DOME_DEPLOY_TICK.remove(player.getUUID());
		String cooldownKey = dome ? DOME_COOLDOWN : SHIELD_COOLDOWN;
		int cooldownTicks = dome ? GreenLanternConfig.DOME_COOLDOWN_TICKS : GreenLanternConfig.SHIELD_BREAK_COOLDOWN_TICKS;
		if (broke) {
			GreenLantern.triggerCooldown(player, cooldownKey, cooldownTicks);
			ServerLevel level = player.serverLevel();
			level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 20, 0.6, 0.6, 0.6, 0.1);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7f, 0.7f);
			player.displayClientMessage(Component.translatable(dome
					? "message.projecthero.green_lantern.dome_collapsed" : "message.projecthero.green_lantern.shield_broken"), true);
		}
	}

	/** Death/respawn/logout/dimension-change/power-loss cleanup -- also resets the uptime meter to full,
	 *  same "fresh start" convention as every other transient combat flag this clears. */
	public static void dismissAll(ServerPlayer player) {
		if (isActive(player)) {
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		}
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_METER, 1f);
		player.setAttached(ModAttachments.GREEN_LANTERN_SHIELD_HP, GreenLanternConfig.SHIELD_HP);
		DOME_DEPLOY_TICK.remove(player.getUUID());
	}

	/**
	 * A second Shift+Z while the dome is up (v0.11.2): the player chose to drop it early, as opposed to
	 * it breaking or running out of uptime. Same effect as {@link #dismissAll} (no "broke"
	 * particles/message) but does NOT reset the meter -- only the death/logout path gets a free refill --
	 * and, v0.11.8, applies the new flat {@link GreenLanternConfig#BARRIER_TOGGLE_COOLDOWN_TICKS} (8s,
	 * explicit user request: "when they toggle off the dome it goes on an 8 second cooldown") in place of
	 * the old half-of-break-cooldown.
	 */
	public static void dismissDomeVoluntarily(ServerPlayer player) {
		if (isActive(player)) {
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		}
		DOME_DEPLOY_TICK.remove(player.getUUID());
		GreenLantern.triggerCooldown(player, DOME_COOLDOWN, GreenLanternConfig.BARRIER_TOGGLE_COOLDOWN_TICKS);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.3f);
	}
}
