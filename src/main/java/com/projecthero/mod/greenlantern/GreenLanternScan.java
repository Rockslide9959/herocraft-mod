package com.projecthero.mod.greenlantern;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.GreenLanternRingScanPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Shift+V -- Ring Scan (v0.11.10 rework). Every living creature within {@link GreenLanternConfig#SCAN_RADIUS}
 * blocks (50, up from 24) glows through walls for {@link GreenLanternConfig#SCAN_DURATION_TICKS} -- red
 * for hostiles, green for everything else (passive/neutral/tamed) -- but only in the <em>scanning
 * player's own client</em>. This no longer applies the vanilla {@code GLOWING} mob effect at all (that
 * synced a shared entity flag every tracking client could see, which is exactly the reported bug --
 * "right now everyone in the world can see the glowing creatures"); instead a
 * {@link GreenLanternRingScanPayload} goes to the caster alone and {@code EntityGlowMixin} renders the
 * outline purely on their end, the same private-glow pattern Spider-Sense/Predator Vision already use.
 * Players are deliberately never highlighted (mirrors every other detection power in the mod). Dropped
 * items within the same radius still get a short particle beacon, since glowing has no meaning for an
 * {@link ItemEntity}. Deliberately does not x-ray ore through solid blocks (per the build brief).
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
		GreenLanternBattery.onAbilityUsed(player);
		GreenLantern.triggerCooldown(player, SCAN_CD, GreenLanternConfig.SCAN_COOLDOWN_TICKS);

		ServerLevel level = player.serverLevel();
		IntList hostileIds = new IntArrayList();
		IntList passiveIds = new IntArrayList();
		for (LivingEntity e : AbilityHelpers.living(level, player.position(), GreenLanternConfig.SCAN_RADIUS,
				le -> le != player && !(le instanceof Player))) {
			if (e instanceof Enemy) {
				hostileIds.add(e.getId());
			} else {
				passiveIds.add(e.getId());
			}
		}
		ServerPlayNetworking.send(player, new GreenLanternRingScanPayload(hostileIds.toIntArray(), passiveIds.toIntArray()));

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
