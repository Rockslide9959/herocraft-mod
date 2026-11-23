package com.projecthero.mod.skyfortress;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.skyfortress.item.SkyFortressItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the Launch Pad's <b>fling</b>. A player who steps on a pad holding a Fortress Pass is shot straight up the
 * column above it: every tick the server sets an upward velocity ({@link #riseSpeed}, eased as they near the top and sent
 * like knockback) and keeps them centred on the pad, until they are above the hangar floor -- then a nudge sideways
 * off the lift hatch, Slow Falling to settle, and the fall forgiven.
 *
 * <p>The target is found, not stored: the first solid block straight above the pad (from 4 up) is the hangar ceiling
 * over the lift hatch, and the hangar floor's standing level is {@link #CEILING_TO_FLOOR} below it.
 *
 * <p>Static state: {@link #ACTIVE}, player UUID to a small record (positions and counters only -- no worlds, entities
 * or block states); cleared with {@link #clear} from {@code ServerStateReset}.
 */
public final class SkyFling {
	/** Hangar ceiling block layer to the floor's standing level (interior 6 high). */
	public static final int CEILING_TO_FLOOR = 6;
	public static final double MIN_RISE = 0.9;
	public static final double MAX_RISE = 2.6;
	public static final int MAX_TICKS = 260;

	/** One fling in progress. */
	public static final class Fling {
		public final BlockPos pad;
		public final int targetY;
		public final Direction land;
		public int ticks;

		Fling(BlockPos pad, int targetY, Direction land) {
			this.pad = pad;
			this.targetY = targetY;
			this.land = land;
		}
	}

	private static final Map<UUID, Fling> ACTIVE = new HashMap<>();

	private SkyFling() {
	}

	public static void clear() {
		ACTIVE.clear();
	}

	public static Fling active(Player player) {
		return ACTIVE.get(player.getUUID());
	}

	public static boolean holdsPass(Player player) {
		return player.getMainHandItem().is(SkyFortressItems.FORTRESS_PASS) || player.getOffhandItem().is(SkyFortressItems.FORTRESS_PASS);
	}

	/** The standing level the pad at {@code pad} sends you to, or {@link Integer#MIN_VALUE} with nothing overhead. */
	public static int targetY(ServerLevel level, BlockPos pad) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = pad.getY() + 4; y < level.getMaxBuildHeight(); y++) {
			m.set(pad.getX(), y, pad.getZ());
			if (!level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
				int t = y - CEILING_TO_FLOOR;
				return t > pad.getY() + 4 ? t : Integer.MIN_VALUE;
			}
		}
		return Integer.MIN_VALUE;
	}

	/**
	 * Launches {@code player} from the pad at {@code pad} if they hold a Fortress Pass ({@code force} skips the pass, for
	 * tests). Returns true when a fling started.
	 */
	public static boolean tryLaunch(ServerLevel level, BlockPos pad, ServerPlayer player, boolean force) {
		if (ACTIVE.containsKey(player.getUUID()) || player.isSpectator()) {
			return false;
		}
		if (!force && !holdsPass(player)) {
			if (player.tickCount % 40 == 0) {
				player.displayClientMessage(Component.translatable("block.projecthero.launch_pad.no_pass").withStyle(ChatFormatting.GRAY), true);
			}
			return false;
		}
		int target = targetY(level, pad);
		if (target == Integer.MIN_VALUE) {
			if (player.tickCount % 40 == 0) {
				player.displayClientMessage(Component.translatable("block.projecthero.launch_pad.clear").withStyle(ChatFormatting.GRAY), true);
			}
			return false;
		}
		Direction land = Direction.fromYRot(player.getYRot());
		ACTIVE.put(player.getUUID(), new Fling(pad.immutable(), target, land));
		level.playSound(null, pad, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 2.0f, 0.6f);
		level.playSound(null, pad, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 1.5f, 0.5f);
		level.sendParticles(ParticleTypes.CLOUD, pad.getX() + 0.5, pad.getY() + 1.1, pad.getZ() + 0.5, 30, 1.0, 0.1, 1.0, 0.08);
		level.sendParticles(ParticleTypes.END_ROD, pad.getX() + 0.5, pad.getY() + 1.1, pad.getZ() + 0.5, 20, 0.6, 0.3, 0.6, 0.15);
		player.displayClientMessage(Component.translatable("block.projecthero.launch_pad.launch").withStyle(ChatFormatting.AQUA), true);
		step(level, player, ACTIVE.get(player.getUUID()));
		return true;
	}

	/** Upward speed for a player at {@code y} rising to {@code targetY}. */
	public static double riseSpeed(double y, int targetY) {
		return Mth.clamp((targetY + 3 - y) * 0.18, MIN_RISE, MAX_RISE);
	}

	/** Every server tick: drive each fling one step. */
	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Fling>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Fling> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !p.isAlive() || !(p.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (!step(level, p, e.getValue())) {
				it.remove();
			}
		}
	}

	/** One step of a fling. False when it is over (landed or timed out). */
	public static boolean step(ServerLevel level, ServerPlayer p, Fling f) {
		f.ticks++;
		p.resetFallDistance();
		if (f.ticks > MAX_TICKS) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false, true));
			return false;
		}
		if (p.getY() < f.targetY + 0.6) {
			double vy = riseSpeed(p.getY(), f.targetY);
			double cx = (f.pad.getX() + 0.5 - p.getX()) * 0.3;
			double cz = (f.pad.getZ() + 0.5 - p.getZ()) * 0.3;
			p.setDeltaMovement(new Vec3(cx, vy, cz));
			p.hurtMarked = true;
			if (f.ticks % 2 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY(), p.getZ(), 2, 0.2, 0.1, 0.2, 0.02);
				level.sendParticles(ParticleTypes.FIREWORK, p.getX(), p.getY() - 0.2, p.getZ(), 2, 0.15, 0.1, 0.15, 0.02);
			}
			return true;
		}
		land(level, p, f);
		return false;
	}

	/**
	 * Which way to step off the hatch: the side with the longest run of floor (up to 10 blocks) at the landing level,
	 * so nobody is nudged toward the open hangar mouth; ties keep the player's facing.
	 */
	public static Direction landingSide(ServerLevel level, BlockPos pad, int targetY, Direction facing) {
		Direction best = facing;
		int bestRun = -1;
		for (Direction d : new Direction[] { facing, facing.getClockWise(), facing.getOpposite(), facing.getCounterClockWise() }) {
			int run = 0;
			for (int i = 2; i <= 11; i++) {
				BlockPos floor = new BlockPos(pad.getX() + d.getStepX() * i, targetY - 1, pad.getZ() + d.getStepZ() * i);
				if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
					break;
				}
				run++;
			}
			if (run > bestRun) {
				bestRun = run;
				best = d;
			}
		}
		return best;
	}

	/** The top: off the hatch, slow fall, welcome aboard. */
	public static void land(ServerLevel level, ServerPlayer p, Fling f) {
		Direction side = landingSide(level, f.pad, f.targetY, f.land);
		p.setDeltaMovement(new Vec3(side.getStepX() * 0.42, 0.25, side.getStepZ() * 0.42));
		p.hurtMarked = true;
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, true));
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.6f, 1.6f);
		p.displayClientMessage(Component.translatable("block.projecthero.launch_pad.aboard").withStyle(ChatFormatting.AQUA), true);
	}

	/** Right-click on a pad: what it does. */
	public static void explain(ServerLevel level, BlockPos pad, ServerPlayer player) {
		player.displayClientMessage(Component.translatable(holdsPass(player) ? "block.projecthero.launch_pad.step" : "block.projecthero.launch_pad.no_pass")
				.withStyle(ChatFormatting.GRAY), true);
	}
}
