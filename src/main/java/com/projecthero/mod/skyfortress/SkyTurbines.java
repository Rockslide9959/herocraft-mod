package com.projecthero.mod.skyfortress;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.alarm.AlarmZone;
import com.projecthero.mod.alarm.AlarmZones;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.skyfortress.block.SkyFortressBlocks;
import com.projecthero.mod.skyfortress.block.SkyTurbineBlock;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: sabotaging the Sky Fortress's engine <b>Turbines</b>, and what happens when the last one dies.
 * <ul>
 * <li>Holding right-click on a running turbine repeats the use every 4 ticks; each use adds the ticks since the last
 * (a gap over {@link #GAP} starts again) up to {@link #HOLD_TICKS} -- then it bursts and stops for good. Works in any
 * alarm state (the turbine room is meant to be a fight).</li>
 * <li>The fortress's turbines and shield blocks are found in its alarm zone's box, within {@link #DOWN} below to
 * {@link #UP} above the turbine (no zone: {@link #RADIUS} round it).</li>
 * <li>Last turbine down: every Bridge Shield block in that region drops, and the ship lurches -- a camera shake
 * ({@code TitanShakePayload}) and a shove to one side for everyone aboard, creaking hull sounds.</li>
 * </ul>
 * Static state: sabotage progress, player UUID to {@code [pos, lastTick, progress]} (numbers only); cleared with
 * {@link #clear} from {@code ServerStateReset}.
 */
public final class SkyTurbines {
	public static final int HOLD_TICKS = 40;
	public static final int GAP = 10;
	public static final int RADIUS = 40;
	public static final int DOWN = 12;
	public static final int UP = 16;

	private static final Map<UUID, long[]> PROGRESS = new HashMap<>();

	private SkyTurbines() {
	}

	public static void clear() {
		PROGRESS.clear();
	}

	/** One use of a turbine by {@code player}. Returns the progress after it (-1 refused, HOLD_TICKS done). */
	public static int use(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
		if (!state.getValue(SkyTurbineBlock.RUNNING)) {
			player.displayClientMessage(Component.translatable("block.projecthero.sky_turbine.dead").withStyle(ChatFormatting.GRAY), true);
			return -1;
		}
		long now = level.getGameTime();
		long[] s = PROGRESS.get(player.getUUID());
		if (s == null || s[0] != pos.asLong() || now - s[1] > GAP || now < s[1]) {
			s = new long[] { pos.asLong(), now, 0L };
			PROGRESS.put(player.getUUID(), s);
		} else {
			s[2] += now - s[1];
			s[1] = now;
		}
		int progress = (int) s[2];
		if (progress >= HOLD_TICKS) {
			PROGRESS.remove(player.getUUID());
			sabotage(level, pos, player);
			return HOLD_TICKS;
		}
		int bars = 10;
		int filled = progress * bars / HOLD_TICKS;
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < bars; i++) {
			b.append(i < filled ? '|' : '.');
		}
		player.displayClientMessage(Component.translatable("block.projecthero.sky_turbine.sabotaging", b.toString()).withStyle(ChatFormatting.YELLOW), true);
		level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.3f, 1.4f + progress / 60f);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.08);
		return progress;
	}

	/** The search region of the fortress that holds {@code pos}. */
	public static BoundingBox region(ServerLevel level, BlockPos pos) {
		AlarmZone z = AlarmZones.find(level, pos);
		int y0 = pos.getY() - DOWN;
		int y1 = pos.getY() + UP;
		if (z != null) {
			BoundingBox b = z.box;
			return new BoundingBox(b.minX(), Math.max(b.minY(), y0), b.minZ(), b.maxX(), Math.min(b.maxY(), y1), b.maxZ());
		}
		return new BoundingBox(pos.getX() - RADIUS, y0, pos.getZ() - RADIUS, pos.getX() + RADIUS, y1, pos.getZ() + RADIUS);
	}

	/** Positions of every turbine (running or not) in {@code box}. */
	public static List<BlockPos> turbines(ServerLevel level, BoundingBox box) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.isLoaded(p) && level.getBlockState(p).is(SkyFortressBlocks.TURBINE)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	/** How many turbines still run in the fortress that holds {@code pos}. */
	public static int countRunning(ServerLevel level, BlockPos pos) {
		int n = 0;
		for (BlockPos p : turbines(level, region(level, pos))) {
			if (level.getBlockState(p).getValue(SkyTurbineBlock.RUNNING)) {
				n++;
			}
		}
		return n;
	}

	/** Kills the turbine at {@code pos} now; drops the shield when it was the last. Returns turbines still running. */
	public static int sabotage(ServerLevel level, BlockPos pos, ServerPlayer player) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(SkyFortressBlocks.TURBINE) || !state.getValue(SkyTurbineBlock.RUNNING)) {
			return countRunning(level, pos);
		}
		level.setBlock(pos, state.setValue(SkyTurbineBlock.RUNNING, false), 3);
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 0.5;
		double z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 2, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 40, 0.6, 0.6, 0.6, 0.3);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 20, 0.5, 0.5, 0.5, 0.04);
		level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.2f, 1.3f);
		level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.6f);
		BoundingBox box = region(level, pos);
		int running = 0;
		for (BlockPos p : turbines(level, box)) {
			if (level.getBlockState(p).getValue(SkyTurbineBlock.RUNNING)) {
				running++;
			}
		}
		Component msg = running > 0 ? Component.translatable("block.projecthero.sky_turbine.down", running).withStyle(ChatFormatting.GOLD)
				: Component.translatable("block.projecthero.sky_turbine.all_down").withStyle(ChatFormatting.AQUA);
		for (ServerPlayer p : playersIn(level, box, 24)) {
			p.displayClientMessage(msg, false);
		}
		if (running == 0) {
			dropShield(level, box);
			lurch(level, box);
		}
		return running;
	}

	/** Removes every Bridge Shield block in {@code box}. Returns how many. */
	public static int dropShield(ServerLevel level, BoundingBox box) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.isLoaded(p) && level.getBlockState(p).is(SkyFortressBlocks.SHIELD)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
				level.sendParticles(ParticleTypes.GLOW, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
				if (n++ == 0) {
					level.playSound(null, p, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.0f, 0.5f);
				}
			}
		}
		return n;
	}

	/** The ship lurches: shake, a shove to one side, groaning metal. */
	public static void lurch(ServerLevel level, BoundingBox box) {
		Vec3 side = level.random.nextBoolean() ? new Vec3(0.55, 0.15, 0.2) : new Vec3(-0.55, 0.15, -0.2);
		BlockPos c = box.getCenter();
		level.playSound(null, c, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 3.0f, 0.4f);
		level.playSound(null, c, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.BLOCKS, 3.0f, 0.3f);
		for (ServerPlayer p : playersIn(level, box, 40)) {
			ServerPlayNetworking.send(p, new TitanShakePayload(0.9f, 70));
			if (!p.isCreative() && !p.isSpectator()) {
				p.push(side.x, side.y, side.z);
				p.hurtMarked = true;
			}
			p.displayClientMessage(Component.translatable("projecthero.sky_fortress.lurch").withStyle(ChatFormatting.RED), true);
		}
	}

	static List<ServerPlayer> playersIn(ServerLevel level, BoundingBox box, int pad) {
		AABB a = new AABB(box.minX() - pad, box.minY() - pad, box.minZ() - pad, box.maxX() + 1 + pad, box.maxY() + 1 + pad, box.maxZ() + 1 + pad);
		return level.getEntitiesOfClass(ServerPlayer.class, a, ServerPlayer::isAlive);
	}
}
