package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

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
	/** How often (in ticks) the dome's boundary outline is re-emitted while it is up. */
	private static final int DOME_OUTLINE_INTERVAL_TICKS = 15;

	private static final String SHIELD_COOLDOWN = "directional_shield";
	private static final String DOME_COOLDOWN = "protective_dome";

	private GreenLanternShield() {
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

	public static void startShield(ServerPlayer player) {
		if (isActive(player) || !GreenLantern.abilityReady(player, SHIELD_COOLDOWN)) {
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.SHIELD_INITIAL_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternEnergy.markAbilityUsed(player);
		GreenLanternBattery.onAbilityUsed(player);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, GreenLanternConfig.SHIELD_HP);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.5f, 1.4f);
	}

	public static void stopShield(ServerPlayer player) {
		if (isActive(player) && !isDome(player)) {
			endBarrier(player, false);
		}
	}

	/** Per-tick upkeep while the Directional Shield is held. */
	public static void tickShieldUpkeep(ServerPlayer player) {
		if (!isActive(player) || isDome(player)) {
			return;
		}
		if (!GreenLanternEnergy.drainTick(player, GreenLanternConfig.SHIELD_UPKEEP_PER_SEC / 20f)) {
			endBarrier(player, true);
		}
	}

	// ---------------- Protective Dome (timed) ----------------

	public static void deployDome(ServerPlayer player) {
		if (isActive(player) || !GreenLantern.abilityReady(player, DOME_COOLDOWN)) {
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.DOME_INITIAL_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternEnergy.markAbilityUsed(player);
		GreenLanternBattery.onAbilityUsed(player);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, GreenLanternConfig.DOME_HP);
		player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, true);
		GreenLantern.triggerCooldown(player, "dome_expiry", GreenLanternConfig.DOME_MAX_DURATION_TICKS);
		ServerLevel level = player.serverLevel();
		emitDomeOutline(player, level);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	/**
	 * A wireframe sphere of green particles at the dome's boundary -- three perpendicular great circles
	 * (one per axis plane), which reads as a hollow globe outline rather than a solid burst. Called once
	 * on deploy and then re-emitted every {@link #DOME_OUTLINE_INTERVAL_TICKS} while the dome is up (see
	 * {@link #tickDomeUpkeep}) so the boundary stays visibly marked instead of a one-shot puff that
	 * immediately disperses (v0.11.2).
	 */
	private static void emitDomeOutline(ServerPlayer player, ServerLevel level) {
		double r = GreenLanternConfig.DOME_RADIUS;
		double cx = player.getX();
		double cy = player.getY() + 1.0;
		double cz = player.getZ();
		int points = 14;
		for (int i = 0; i < points; i++) {
			double a = (Math.PI * 2 * i) / points;
			double cos = Math.cos(a) * r;
			double sin = Math.sin(a) * r;
			level.sendParticles(GREEN_DUST, cx + cos, cy + sin, cz, 1, 0, 0, 0, 0.0);
			level.sendParticles(GREEN_DUST, cx + cos, cy, cz + sin, 1, 0, 0, 0, 0.0);
			level.sendParticles(GREEN_DUST, cx, cy + cos, cz + sin, 1, 0, 0, 0, 0.0);
		}
	}

	/** Per-tick upkeep + max-duration expiry while the dome is up. */
	public static void tickDomeUpkeep(ServerPlayer player) {
		if (!isActive(player) || !isDome(player)) {
			return;
		}
		if (player.tickCount % DOME_OUTLINE_INTERVAL_TICKS == 0) {
			emitDomeOutline(player, player.serverLevel());
		}
		if (!GreenLanternEnergy.drainTick(player, GreenLanternConfig.DOME_UPKEEP_PER_SEC / 20f)
				|| GreenLantern.abilityReady(player, "dome_expiry")) {
			endBarrier(player, true);
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
		GreenLanternMastery.onDamageBlocked(player, absorbed);
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

	public static void dismissAll(ServerPlayer player) {
		if (isActive(player)) {
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, 0f);
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_IS_DOME, false);
		}
	}

	/**
	 * A second Shift+Z while the dome is up (v0.11.2): the player chose to drop it early, as opposed to
	 * it breaking or timing out. Goes through {@link #dismissAll} (no "broke" cooldown/particles/message)
	 * rather than {@link #endBarrier} -- but still applies half of {@link GreenLanternConfig#DOME_COOLDOWN_TICKS}
	 * so a full-HP dome can't be redeployed instantly after soaking most of a hit; a genuinely idle dome
	 * costs nothing extra to end early, it just can't be immediately re-thrown at full strength.
	 */
	public static void dismissDomeVoluntarily(ServerPlayer player) {
		dismissAll(player);
		GreenLantern.triggerCooldown(player, DOME_COOLDOWN, GreenLanternConfig.DOME_COOLDOWN_TICKS / 2);
		player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 1.3f);
	}
}
