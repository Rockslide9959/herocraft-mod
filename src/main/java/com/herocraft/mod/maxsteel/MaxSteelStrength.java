package com.herocraft.mod.maxsteel;

import com.herocraft.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Ability 2 -- Turbo Strength Mode, and its Heavy Punch. The mode's stat bonuses / penalties are
 * attribute modifiers in {@link MaxSteelAttributes} and the crouch shield block is in
 * {@link MaxSteelDamage}; this class owns only the Heavy Punch, which fires when the player lands a
 * melee hit while <em>sprinting</em> in Strength Mode.
 *
 * <p>v0.9.4: the standing Resistance effect is gone -- Strength Mode's defence is now just the +KB
 * resistance attribute and the crouch shield block.
 */
public final class MaxSteelStrength {
	public static final String HEAVY_PUNCH = "heavy_punch";
	public static final String SLAM = "turbo_slam";

	private MaxSteelStrength() {
	}

	/**
	 * v0.9.2 Turbo Slam: pressing the Strength-Mode key again while in Strength Mode drives a fist into
	 * the ground, dealing {@link MaxSteelConfig#STRENGTH_SLAM_DAMAGE} to every hostile within
	 * {@link MaxSteelConfig#STRENGTH_SLAM_RADIUS} blocks and knocking them back. Own internal cooldown.
	 */
	public static void slam(ServerPlayer player) {
		if (MaxSteel.mode(player) != MaxSteelMode.STRENGTH || !MaxSteel.abilityReady(player, SLAM)) {
			return;
		}
		if (!MaxSteelEnergy.spend(player, MaxSteelConfig.STRENGTH_SLAM_COST)) {
			MaxSteelFeedback.noEnergy(player, MaxSteelConfig.STRENGTH_SLAM_COST);
			return;
		}
		MaxSteel.triggerCooldown(player, SLAM, MaxSteelConfig.STRENGTH_SLAM_COOLDOWN_TICKS);
		MaxSteelEnergy.markCombat(player);
		MaxSteelStealth.onOffensiveAction(player);

		Vec3 centre = player.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, centre, MaxSteelConfig.STRENGTH_SLAM_RADIUS)) {
			AbilityHelpers.hurt(player, e, MaxSteelConfig.STRENGTH_SLAM_DAMAGE);
			AbilityHelpers.knockbackFrom(e, centre, 1.6);
			e.push(0, 0.45, 0);
		}

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.1, centre.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 0.1, centre.z,
				40, MaxSteelConfig.STRENGTH_SLAM_RADIUS / 2, 0.1, MaxSteelConfig.STRENGTH_SLAM_RADIUS / 2, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y + 0.2, centre.z, 18, 1.2, 0.1, 1.2, 0.06);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.7f);
		player.displayClientMessage(Component.translatable("message.herocraft.max_steel.slam"), true);
	}

	/**
	 * Called from the mod's attack hook after a Max Steel player lands a melee hit. Adds the Heavy
	 * Punch bonus damage + a short shockwave when the conditions are met, on its own internal cooldown.
	 */
	public static void onMeleeHit(ServerPlayer player, Entity target) {
		if (MaxSteel.mode(player) != MaxSteelMode.STRENGTH || !player.isSprinting()) {
			return;
		}
		if (!MaxSteel.abilityReady(player, HEAVY_PUNCH) || !(target instanceof LivingEntity primary)) {
			return;
		}
		MaxSteel.triggerCooldown(player, HEAVY_PUNCH, MaxSteelConfig.HEAVY_PUNCH_COOLDOWN_TICKS);
		MaxSteelEnergy.markCombat(player);

		AbilityHelpers.hurt(player, primary, MaxSteelConfig.HEAVY_PUNCH_BONUS_DAMAGE);
		Vec3 centre = primary.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, centre, MaxSteelConfig.HEAVY_PUNCH_SHOCKWAVE_RADIUS)) {
			if (e == primary) {
				continue;
			}
			AbilityHelpers.hurt(player, e, MaxSteelConfig.HEAVY_PUNCH_BONUS_DAMAGE * 0.5f);
			AbilityHelpers.knockbackFrom(e, player.position(), 1.1);
		}
		AbilityHelpers.knockbackFrom(primary, player.position(), 1.4);

		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.5, centre.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y + 0.8, centre.z, 6, 0.6, 0.3, 0.6, 0.0);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.6f);
	}
}
