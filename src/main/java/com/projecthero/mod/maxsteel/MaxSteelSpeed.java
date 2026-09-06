package com.projecthero.mod.maxsteel;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 3 -- Turbo Speed Mode, and its Turbo Dash.
 *
 * <p>The mode is "controlled high ground speed": a large but bounded {@code MOVEMENT_SPEED} multiplier
 * plus the step-assist from {@link MaxSteelAttributes}, rather than a stacked vanilla Speed potion --
 * so acceleration stays smooth and the player is still steerable indoors. Fall damage is cut 60% while
 * it is active. Tapping the ability again mid-mode fires a short forward Dash.
 */
public final class MaxSteelSpeed {
	public static final String DASH = "turbo_dash";
	private static final ResourceLocation SPEED_BOOST = ProjectHeroMod.id("max_steel_speed_boost");

	private MaxSteelSpeed() {
	}

	/** Applied while Speed Mode is active (called from the mode-runtime tick / on enter). */
	public static void applyBoost(ServerPlayer player) {
		PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SPEED_BOOST, 1.10,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	/** Reset the speed-mode movement boost. Idempotent. */
	public static void forceStop(ServerPlayer player) {
		PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPEED_BOOST);
	}

	/** Tap Ability 3 again while Speed Mode is active: a short forward dash that hits the first enemy. */
	public static void dash(ServerPlayer player) {
		if (MaxSteel.mode(player) != MaxSteelMode.SPEED) {
			return;
		}
		if (!MaxSteel.abilityReady(player, DASH)) {
			return;
		}
		if (!MaxSteelEnergy.spend(player, MaxSteelConfig.TURBO_DASH_COST)) {
			MaxSteelFeedback.noEnergy(player, MaxSteelConfig.TURBO_DASH_COST);
			return;
		}
		MaxSteel.triggerCooldown(player, DASH, MaxSteelConfig.TURBO_DASH_COOLDOWN_TICKS);
		MaxSteelEnergy.markCombat(player);

		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, Math.max(-0.15, Math.min(0.25, look.y)), look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
		AbilityHelpers.launchSelf(player, flat.scale(MaxSteelConfig.TURBO_DASH_DISTANCE * 0.28));

		LivingEntity target = AbilityHelpers.raycastEntity(player, MaxSteelConfig.TURBO_DASH_DISTANCE + 1.0);
		if (target != null) {
			AbilityHelpers.hurt(player, target, MaxSteelConfig.TURBO_DASH_DAMAGE);
			AbilityHelpers.knockbackFrom(target, player.position(), 0.7);
		}

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
				12, 0.2, 0.1, 0.2, 0.02);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
	}

	/** Brief speed-streak particles while the mode is active. Cheap. */
	public static void tickFx(ServerPlayer player) {
		if (MaxSteel.mode(player) == MaxSteelMode.SPEED && player.tickCount % 3 == 0
				&& player.getDeltaMovement().horizontalDistanceSqr() > 0.02) {
			player.serverLevel().sendParticles(ParticleTypes.END_ROD,
					player.getX(), player.getY() + 0.4, player.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
		}
	}
}
