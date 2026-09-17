package com.projecthero.mod.greenlantern;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Personal Power Battery's recharge ritual (v0.11.4): there is no passive Ring Charge regen and no
 * per-second channel any more -- right-clicking a battery below max charge starts an Oath, one line of
 * {@link #OATH_LINES} appearing on screen every {@link GreenLanternConfig#OATH_LINE_TICKS} in green,
 * and completing the full recitation fills the ring instantly. Any input during the recitation --
 * moving, turning, taking damage, or using any ring ability -- cancels both the oath and the recharge;
 * there is no partial credit and no way to resume mid-oath.
 *
 * <p>"Any input" is approximated (no raw key-press event exists server-side for this): a drift in
 * position or look angle past a small epsilon, or the existing per-ability {@link #onAbilityUsed}/
 * {@link #onDamaged} hooks that every Green Lantern ability already calls. This mirrors the old
 * channel's own interrupt conditions (damage/movement/ability-use/logout), just re-purposed for a fixed
 * recitation instead of a walk-and-wait loop.
 */
public final class GreenLanternBattery {
	/** v0.11.4: the classic Green Lantern Oath, one line at a time. */
	private static final String[] OATH_LINES = {
			"message.projecthero.green_lantern.oath.line1",
			"message.projecthero.green_lantern.oath.line2",
			"message.projecthero.green_lantern.oath.line3",
			"message.projecthero.green_lantern.oath.line4",
	};

	private static final class Oath {
		final BlockPos batteryPos;
		final Vec3 originPos;
		final float originYaw;
		final float originPitch;
		final long startTick;
		int lastLineShown = -1;

		Oath(BlockPos batteryPos, Vec3 originPos, float originYaw, float originPitch, long startTick) {
			this.batteryPos = batteryPos;
			this.originPos = originPos;
			this.originYaw = originYaw;
			this.originPitch = originPitch;
			this.startTick = startTick;
		}
	}

	private static final Map<UUID, Oath> OATHS = new ConcurrentHashMap<>();

	private GreenLanternBattery() {
	}

	public static void clearSessionState() {
		OATHS.clear();
	}

	public static boolean isRecitingOath(ServerPlayer player) {
		return OATHS.containsKey(player.getUUID());
	}

	public static void beginOath(ServerPlayer player, BlockPos batteryPos) {
		if (GreenLanternEnergy.get(player) >= GreenLanternConfig.MAX_RING_CHARGE) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.battery_full"), true);
			return;
		}
		if (isRecitingOath(player)) {
			return;
		}
		OATHS.put(player.getUUID(),
				new Oath(batteryPos.immutable(), player.position(), player.getYRot(), player.getXRot(),
						player.level().getGameTime()));
	}

	public static void cancelOath(UUID playerId) {
		OATHS.remove(playerId);
	}

	/** Called by every Green Lantern ability the instant it activates -- cancels an in-progress oath. */
	public static void onAbilityUsed(ServerPlayer player) {
		if (OATHS.remove(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath.cancelled"), true);
		}
	}

	public static void onDamaged(ServerPlayer player) {
		if (OATHS.remove(player.getUUID()) != null) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath.cancelled"), true);
		}
	}

	/** Per-player server tick. */
	public static void tick(ServerPlayer player) {
		Oath o = OATHS.get(player.getUUID());
		if (o == null) {
			return;
		}
		if (!player.level().getBlockState(o.batteryPos).is(com.projecthero.mod.greenlantern.block.GreenLanternBlocks.POWER_BATTERY)
				|| movedOrTurned(player, o)) {
			OATHS.remove(player.getUUID());
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath.cancelled"), true);
			return;
		}

		long elapsed = player.level().getGameTime() - o.startTick;
		int line = (int) (elapsed / GreenLanternConfig.OATH_LINE_TICKS);
		if (line >= OATH_LINES.length) {
			OATHS.remove(player.getUUID());
			GreenLanternEnergy.addCharge(player, GreenLanternConfig.MAX_RING_CHARGE);
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.oath.complete")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), true);
			ServerLevel level = player.serverLevel();
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.3f);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(),
					25, 0.5, 0.8, 0.5, 0.05);
			return;
		}
		if (line != o.lastLineShown) {
			o.lastLineShown = line;
			player.displayClientMessage(Component.translatable(OATH_LINES[line]).withStyle(ChatFormatting.GREEN), true);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.4f, 1.8f);
		}
	}

	/** A drift in position or look angle past a small epsilon counts as "input" and cancels the oath. */
	private static boolean movedOrTurned(ServerPlayer player, Oath o) {
		if (player.position().distanceToSqr(o.originPos) > GreenLanternConfig.OATH_MOVE_EPSILON * GreenLanternConfig.OATH_MOVE_EPSILON) {
			return true;
		}
		return Math.abs(Mth.degreesDifference(o.originYaw, player.getYRot())) > GreenLanternConfig.OATH_ROT_EPSILON
				|| Math.abs(player.getXRot() - o.originPitch) > GreenLanternConfig.OATH_ROT_EPSILON;
	}
}
