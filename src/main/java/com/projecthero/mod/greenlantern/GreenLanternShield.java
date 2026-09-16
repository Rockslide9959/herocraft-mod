package com.projecthero.mod.greenlantern;

import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Directional Shield (Z, held) and Protective Dome (Shift+Z, instant + timed). Both keep their live HP
 * in the non-persisted {@link ModAttachments#GREEN_LANTERN_BARRIER_HP} attachment (0 = inactive) so
 * neither survives a relog; {@link ModAttachments#GREEN_LANTERN_BARRIER_IS_DOME} tells the HUD/renderer
 * which shape is up. Damage mitigation itself lives in {@code GreenLanternDamage} (the
 * cancel-and-reapply-smaller pattern every hero-tier power in this mod uses for partial absorption).
 */
public final class GreenLanternShield {
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
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(),
				40, GreenLanternConfig.DOME_RADIUS, 1.0, GreenLanternConfig.DOME_RADIUS, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6f, 1.6f);
	}

	/** Per-tick upkeep + max-duration expiry while the dome is up. */
	public static void tickDomeUpkeep(ServerPlayer player) {
		if (!isActive(player) || !isDome(player)) {
			return;
		}
		if (!GreenLanternEnergy.drainTick(player, GreenLanternConfig.DOME_UPKEEP_PER_SEC / 20f)
				|| GreenLantern.abilityReady(player, "dome_expiry")) {
			endBarrier(player, true);
		}
	}

	public static void absorb(ServerPlayer player, float amount) {
		float hp = hp(player);
		float remaining = hp - amount;
		GreenLanternMastery.onDamageBlocked(player, Math.min(hp, amount));
		if (remaining <= 0f) {
			endBarrier(player, true);
		} else {
			player.setAttached(ModAttachments.GREEN_LANTERN_BARRIER_HP, remaining);
		}
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
}
