package com.projecthero.mod.greenlantern;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * Shift+V -- Ring Scan. Hostiles within 24 blocks get vanilla Glowing (a real see-through-walls
 * outline, tinted by the client's own glow-colour handling) for 6 seconds; dropped items within 16
 * blocks get a short particle beacon since {@code MobEffects.GLOWING} does not apply to item entities.
 * Deliberately does not x-ray ore through solid blocks (per the build brief).
 */
public final class GreenLanternScan {
	private static final String SCAN_CD = "ring_scan";

	private GreenLanternScan() {
	}

	public static void scan(ServerPlayer player) {
		if (!GreenLantern.abilityReady(player, SCAN_CD)) {
			return;
		}
		if (!GreenLanternEnergy.spend(player, GreenLanternConfig.SCAN_COST)) {
			GreenLanternEnergy.feedback(player, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternEnergy.markAbilityUsed(player);
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, SCAN_CD, GreenLanternConfig.SCAN_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position(), GreenLanternConfig.SCAN_RADIUS)) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, GreenLanternConfig.SCAN_DURATION_TICKS, 0, false, false, true));
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
				player.getBoundingBox().inflate(GreenLanternConfig.SCAN_ITEM_RADIUS))) {
			level.sendParticles(ParticleTypes.END_ROD, item.getX(), item.getY() + 0.4, item.getZ(), 3, 0.1, 0.2, 0.1, 0.01);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1, player.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(),
				30, GreenLanternConfig.SCAN_RADIUS * 0.3, 1.0, GreenLanternConfig.SCAN_RADIUS * 0.3, 0.1);
		AbilityHelpers.sound(player, SoundEvents.BEACON_ACTIVATE, 0.5f, 1.8f);
	}
}
