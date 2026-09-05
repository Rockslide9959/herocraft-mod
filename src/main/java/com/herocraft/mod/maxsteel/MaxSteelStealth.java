package com.herocraft.mod.maxsteel;

import com.herocraft.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

/**
 * Ability 5 -- Turbo Stealth Mode. Near-total invisibility (the suit model fades, the nameplate hides
 * at range), footsteps go quiet, and nearby hostile mobs lose track of the player. Breaks the instant
 * the player does something offensive or takes a real hit, dropping back to Base Mode with a 12s
 * cooldown before it can be used again.
 *
 * <p>Deliberately not "impossible to detect": mobs already targeting from far off keep their target,
 * and the effect ends on any aggressive action.
 */
public final class MaxSteelStealth {
	public static final String ABILITY = "turbo_stealth";
	private static final double UNTARGET_RADIUS = 14.0;

	private MaxSteelStealth() {
	}

	public static boolean isStealthed(ServerPlayer player) {
		return MaxSteel.mode(player) == MaxSteelMode.STEALTH;
	}

	/** Whether the stealth cooldown is still running (checked by the mode toggle). */
	public static boolean onCooldown(ServerPlayer player) {
		return !MaxSteel.abilityReady(player, ABILITY);
	}

	public static void onEnter(ServerPlayer player) {
		refreshInvisibility(player);
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
				20, 0.4, 0.7, 0.4, 0.02);
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.4f, 1.8f);
	}

	/** Per-tick while Stealth is the active mode: keep invisibility topped up and shed nearby aggro. */
	public static void tick(ServerPlayer player) {
		if (!isStealthed(player)) {
			return;
		}
		refreshInvisibility(player);
		if (player.tickCount % 5 == 0) {
			AABB box = player.getBoundingBox().inflate(UNTARGET_RADIUS);
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy)) {
				if (mob.getTarget() == player) {
					mob.setTarget(null);
				}
				if (mob.getLastHurtByMob() == player) {
					mob.setLastHurtByMob(null);
				}
			}
		}
	}

	private static void refreshInvisibility(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false, false));
	}

	/** The player did something offensive -- break stealth. */
	public static void onOffensiveAction(ServerPlayer player) {
		if (isStealthed(player)) {
			breakStealth(player);
		}
	}

	/** A hit above the break threshold landed. */
	public static void onHardHit(ServerPlayer player) {
		if (isStealthed(player)) {
			breakStealth(player);
		}
	}

	private static void breakStealth(ServerPlayer player) {
		MaxSteelModes.exitToBase(player, false);
		forceStop(player);
		MaxSteel.triggerCooldown(player, ABILITY, MaxSteelConfig.STEALTH_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
				18, 0.35, 0.6, 0.35, 0.05);
		player.displayClientMessage(Component.translatable("message.herocraft.max_steel.stealth_broken"), true);
	}

	/**
	 * Remove our short, hidden invisibility. Idempotent, and deliberately does not touch a real
	 * Invisibility <em>potion</em> (those are long and particle-visible).
	 */
	public static void forceStop(ServerPlayer player) {
		MobEffectInstance inv = player.getEffect(MobEffects.INVISIBILITY);
		if (inv != null && inv.getDuration() <= 25 && !inv.isVisible()) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
	}
}
