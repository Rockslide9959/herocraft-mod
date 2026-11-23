package com.projecthero.mod.alarm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: a Laser Tripwire's beam: from the emitter's centre along {@link LaserTripwireBlock#FACING} until the first
 * block with a collision shape, at most {@link #MAX_LENGTH} blocks. A player whose box (shrunk by
 * {@link #GRACE}) the beam line passes through trips it -- jump a floor beam, crouch under a head-height one.
 */
public class LaserTripwireBlockEntity extends SensorBlockEntity {
	public static final int MAX_LENGTH = 12;
	public static final double GRACE = 0.06;

	public LaserTripwireBlockEntity(BlockPos pos, BlockState state) {
		super(AlarmBlocks.LASER_BE, pos, state);
	}

	/** Beam length in blocks (0 when blocked right away). */
	public static int length(BlockGetter level, BlockPos pos, Direction d) {
		BlockPos.MutableBlockPos m = pos.mutable();
		for (int i = 1; i <= MAX_LENGTH; i++) {
			m.move(d);
			BlockState s = level.getBlockState(m);
			if (!s.getCollisionShape(level, m).isEmpty()) {
				return i - 1;
			}
		}
		return MAX_LENGTH;
	}

	public static Vec3 start(BlockPos pos, Direction d) {
		return Vec3.atCenterOf(pos).add(d.getStepX() * 0.3, d.getStepY() * 0.3, d.getStepZ() * 0.3);
	}

	public static Vec3 end(BlockPos pos, Direction d, int length) {
		return Vec3.atCenterOf(pos).add(d.getStepX() * (length + 0.5), d.getStepY() * (length + 0.5), d.getStepZ() * (length + 0.5));
	}

	@Override
	protected void sense(ServerLevel level, BlockPos pos, BlockState state) {
		Direction d = state.getValue(LaserTripwireBlock.FACING);
		int len = length(level, pos, d);
		if (len <= 0) {
			return;
		}
		Vec3 a = start(pos, d);
		Vec3 b = end(pos, d, len);
		AABB area = new AABB(a, b).inflate(0.5);
		for (Player p : level.getEntitiesOfClass(Player.class, area, p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
			if (p.getBoundingBox().deflate(GRACE).clip(a, b).isPresent()) {
				trip(level, pos, p);
				return;
			}
		}
	}
}
