package com.projecthero.mod.skyfortress.block;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the Wind Vent's timer. A {@link #PERIOD}-tick cycle (offset per vent so a row of them never fires together):
 * {@link #TELL} ticks of hissing cloud puffs, then {@link #GUST} ticks of gust -- every non-creative player in the lane
 * (from the vent {@link #LANE} blocks along its facing, {@link #HALF_WIDTH} either side, 0-4 blocks up) is pushed
 * {@link #PUSH} blocks/tick harder along the facing each tick, up to {@link #MAX_SPEED}. Sneaking halves it. Nothing is
 * saved; it only works while a player is within 24 blocks.
 */
public class WindVentBlockEntity extends BlockEntity {
	public static final int PERIOD = 170;
	public static final int TELL = 30;
	public static final int GUST = 34;
	public static final int LANE = 15;
	public static final double HALF_WIDTH = 2.5;
	public static final double PUSH = 0.075;
	public static final double MAX_SPEED = 0.75;

	public WindVentBlockEntity(BlockPos pos, BlockState state) {
		super(SkyFortressBlocks.WIND_VENT_BE, pos, state);
	}

	/** Where in its cycle the vent at {@code pos} is at {@code gameTime}. */
	public static int phase(BlockPos pos, long gameTime) {
		long off = Math.floorMod(pos.asLong() * 2654435761L, PERIOD);
		return (int) Math.floorMod(gameTime + off, PERIOD);
	}

	/** The gust lane in front of a vent. */
	public static AABB lane(BlockPos pos, Direction f) {
		Vec3 c = Vec3.atBottomCenterOf(pos.above());
		Vec3 end = c.add(f.getStepX() * LANE, 0, f.getStepZ() * LANE);
		double wx = f.getAxis() == Direction.Axis.X ? 0 : HALF_WIDTH;
		double wz = f.getAxis() == Direction.Axis.Z ? 0 : HALF_WIDTH;
		return new AABB(Math.min(c.x, end.x) - wx, c.y - 0.5, Math.min(c.z, end.z) - wz, Math.max(c.x, end.x) + wx, c.y + 4, Math.max(c.z, end.z) + wz);
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, WindVentBlockEntity be) {
		if (!(level instanceof ServerLevel server) || level.getGameTime() % 2 != 0 && phase(pos, level.getGameTime()) >= TELL + GUST) {
			return;
		}
		if (server.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 24, false) == null) {
			return;
		}
		int t = phase(pos, level.getGameTime());
		Direction f = state.getValue(WindVentBlock.FACING);
		double x = pos.getX() + 0.5;
		double y = pos.getY() + 1.05;
		double z = pos.getZ() + 0.5;
		if (t < TELL) {
			if (t == 0) {
				server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 0.6f);
			}
			if (t % 4 == 0) {
				server.sendParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.25, 0.05, 0.25, 0.02);
			}
			return;
		}
		if (t >= TELL + GUST) {
			return;
		}
		if (t == TELL) {
			server.playSound(null, pos, SoundEvents.ELYTRA_FLYING, SoundSource.BLOCKS, 0.9f, 1.5f);
			server.playSound(null, pos, SoundEvents.PHANTOM_FLAP, SoundSource.BLOCKS, 1.2f, 0.5f);
		}
		if (t % 3 == 0) {
			for (int i = 0; i < 4; i++) {
				double d = server.random.nextDouble() * LANE;
				double side = (server.random.nextDouble() - 0.5) * 2 * HALF_WIDTH;
				double px = x + f.getStepX() * d + (f.getAxis() == Direction.Axis.Z ? side : 0);
				double pz = z + f.getStepZ() * d + (f.getAxis() == Direction.Axis.X ? side : 0);
				server.sendParticles(ParticleTypes.CLOUD, px, y + server.random.nextDouble() * 2.5, pz, 0, f.getStepX(), 0.02, f.getStepZ(), 0.6);
			}
		}
		push(server, pos, f);
	}

	/** One tick of gust on everyone in the lane. Returns how many players were pushed. */
	public static int push(ServerLevel level, BlockPos pos, Direction f) {
		List<ServerPlayer> in = level.getEntitiesOfClass(ServerPlayer.class, lane(pos, f), p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
		for (Player p : in) {
			Vec3 v = p.getDeltaMovement();
			double along = v.x * f.getStepX() + v.z * f.getStepZ();
			double push = p.isShiftKeyDown() ? PUSH * 0.5 : PUSH;
			if (along < MAX_SPEED) {
				p.setDeltaMovement(v.add(f.getStepX() * push, 0.0, f.getStepZ() * push));
				p.hurtMarked = true;
			}
		}
		return in.size();
	}
}
