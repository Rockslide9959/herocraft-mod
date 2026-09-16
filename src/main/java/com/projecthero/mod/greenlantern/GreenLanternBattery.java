package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The Personal Power Battery's recharge channel: 500 charge/sec while the bonded owner (or any Green
 * Lantern, per the build brief's ownership fallback) stands within 2 blocks of the battery they began
 * channelling at. Interrupts on damage taken, moving out of range, using a ring ability, or logout --
 * cannot be resumed mid-interrupt, the player has to right-click the battery again.
 */
public final class GreenLanternBattery {
	private record Channel(BlockPos batteryPos, Vec3 origin) {
	}

	private static final Map<UUID, Channel> CHANNELS = new ConcurrentHashMap<>();

	private GreenLanternBattery() {
	}

	public static void clearSessionState() {
		CHANNELS.clear();
	}

	public static boolean isChannelling(ServerPlayer player) {
		return CHANNELS.containsKey(player.getUUID());
	}

	public static void beginChannel(ServerPlayer player, BlockPos batteryPos) {
		if (GreenLanternEnergy.get(player) >= GreenLanternConfig.MAX_RING_CHARGE) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.battery_full"), true);
			return;
		}
		CHANNELS.put(player.getUUID(), new Channel(batteryPos.immutable(), player.position()));
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.battery_start"), true);
	}

	public static void cancelChannel(UUID playerId) {
		CHANNELS.remove(playerId);
	}

	public static void onAbilityUsed(ServerPlayer player) {
		cancelChannel(player.getUUID());
	}

	public static void onDamaged(ServerPlayer player) {
		if (CHANNELS.remove(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.battery_interrupted"), true);
		}
	}

	/** Per-player server tick. */
	public static void tick(ServerPlayer player) {
		Channel c = CHANNELS.get(player.getUUID());
		if (c == null) {
			return;
		}
		if (!player.level().getBlockState(c.batteryPos).is(com.projecthero.mod.greenlantern.block.GreenLanternBlocks.POWER_BATTERY)
				|| player.position().distanceTo(c.origin) > 2.0
				|| player.distanceToSqr(Vec3.atCenterOf(c.batteryPos)) > 16.0) {
			CHANNELS.remove(player.getUUID());
			return;
		}
		GreenLanternEnergy.addCharge(player, GreenLanternConfig.BATTERY_RECHARGE_PER_SEC / 20f);
		if (GreenLanternEnergy.get(player) >= GreenLanternConfig.MAX_RING_CHARGE) {
			CHANNELS.remove(player.getUUID());
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.battery_full"), true);
		}
		if (player.tickCount % 4 == 0) {
			ServerLevel level = player.serverLevel();
			Vec3 from = Vec3.atCenterOf(c.batteryPos).add(0, 1.0, 0);
			Vec3 to = player.position().add(0, 1.0, 0);
			int steps = 6;
			for (int i = 0; i <= steps; i++) {
				Vec3 p = from.lerp(to, i / (double) steps);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			level.playSound(null, c.batteryPos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.6f);
		}
	}
}
