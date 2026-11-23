package com.projecthero.mod.alarm.block;

import com.projecthero.mod.alarm.AlarmVision;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: a Security Camera's brain. The head sweeps {@link #SWEEP} degrees either side of the way the camera faces over
 * {@link #PERIOD} ticks (the same formula on both sides, from the game time, so the client draws exactly what the server
 * checks) and looks {@link #PITCH} degrees down. Seeing a player (the {@link AlarmVision#cameraSees} cone) fills a meter;
 * {@link #LOCK_TICKS} of sight trips it.
 */
public class SecurityCameraBlockEntity extends SensorBlockEntity {
	public static final float SWEEP = 50f;
	public static final int PERIOD = 160;
	public static final float PITCH = 25f;
	public static final int LOCK_TICKS = 20;

	private int seen;

	public SecurityCameraBlockEntity(BlockPos pos, BlockState state) {
		super(AlarmBlocks.CAMERA_BE, pos, state);
	}

	/** Head yaw (Minecraft degrees) at {@code time} for a camera facing {@code facing} at {@code pos}. */
	public static float yaw(Direction facing, BlockPos pos, float time) {
		int phase = Math.floorMod((int) (pos.asLong() ^ (pos.asLong() >>> 21)), PERIOD);
		return facing.toYRot() + SWEEP * Mth.sin((time + phase) * Mth.TWO_PI / PERIOD);
	}

	/** The lens position. */
	public static Vec3 eye(Direction facing, BlockPos pos) {
		return Vec3.atCenterOf(pos).add(facing.getStepX() * 0.45, 0.05, facing.getStepZ() * 0.45);
	}

	public static Vec3 forward(Direction facing, BlockPos pos, float time) {
		return Vec3.directionFromRotation(PITCH, yaw(facing, pos, time));
	}

	@Override
	protected void sense(ServerLevel level, BlockPos pos, BlockState state) {
		Direction f = state.getValue(SecurityCameraBlock.FACING);
		Vec3 eye = eye(f, pos);
		Vec3 fwd = forward(f, pos, level.getGameTime());
		Player caught = null;
		for (Player p : level.players()) {
			if (p.distanceToSqr(eye) < 16 * 16 && AlarmVision.cameraSees(level, eye, fwd, p)) {
				caught = p;
				break;
			}
		}
		seen = caught == null ? Math.max(0, seen - 2) : seen + 2;
		if (caught != null && seen >= LOCK_TICKS) {
			seen = 0;
			trip(level, pos, caught);
		}
	}
}
