package com.projecthero.mod.quirk.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Engine revamp, survival: the <b>water skim</b> (Gear 3, 70% momentum: he runs across water) and the <b>wall kick</b>
 * (sprinting into a wall in gear runs up it, up to {@link EngineConfig#WALL_MAX_CLIMB} blocks). Client-safe: the local
 * player's run is simulated on his own client ({@code EngineRunClient} calls these every tick), the server applies the same
 * rules for its own bookkeeping (fall damage, the FX flags) and the gametests drive them directly.
 */
public final class EngineSurvival {
	private EngineSurvival() {
	}

	/** Fast enough to skim: holds Engine, Gear 3, momentum 70%+, sprinting, not sneaking. */
	public static boolean canSkim(Player p) {
		return Engine.is(p) && Engine.gear(p) >= 3 && Engine.momentum(p) >= EngineConfig.SKIM_MIN_MOMENTUM - 0.02f && p.isSprinting()
				&& !p.isShiftKeyDown() && !p.isPassenger() && !p.getAbilities().flying;
	}

	/** The top of the water under (or around) his feet, or NaN where there is none (open water only: air above it). */
	public static double waterSurface(Player p) {
		for (int dy = 0; dy >= -1; dy--) {
			BlockPos pos = BlockPos.containing(p.getX(), p.getY() + 0.05 + dy, p.getZ());
			FluidState f = p.level().getFluidState(pos);
			if (f.is(FluidTags.WATER) && !p.level().getFluidState(pos.above()).is(FluidTags.WATER)) {
				return pos.getY() + f.getHeight(p.level(), pos);
			}
		}
		return Double.NaN;
	}

	/**
	 * Holds a fast enough runner on the water's surface: snapped up onto it, the fall cancelled, "on the ground" (so the run
	 * carries on). True while he is skimming.
	 */
	public static boolean skim(Player p) {
		if (!canSkim(p)) {
			return false;
		}
		double s = waterSurface(p);
		if (Double.isNaN(s) || p.getY() < s - 0.7 || p.getY() > s + 0.3) {
			return false;
		}
		Vec3 v = p.getDeltaMovement();
		if (p.getY() < s + 0.01) {
			p.setPos(p.getX(), s + 0.01, p.getZ());
		}
		p.setDeltaMovement(v.x, Math.max(0.0, v.y), v.z);
		p.setOnGround(true);
		p.resetFallDistance();
		return true;
	}

	/** Fast enough to run up a wall: holds Engine, in gear, momentum 30%+, sprinting, not sneaking. */
	public static boolean canWallKick(Player p) {
		return Engine.is(p) && Engine.gear(p) >= 1 && Engine.momentum(p) >= EngineConfig.WALL_MIN_MOMENTUM && p.isSprinting() && !p.isShiftKeyDown()
				&& !p.isPassenger() && !p.isInWater() && !p.getAbilities().flying;
	}

	/** The climb speed up a wall after {@code climbed} blocks of it (blocks a tick), or -1 once he has run out of wall. */
	public static double wallLift(double climbed) {
		return climbed < EngineConfig.WALL_MAX_CLIMB ? EngineConfig.WALL_CLIMB_SPEED : -1.0;
	}
}
