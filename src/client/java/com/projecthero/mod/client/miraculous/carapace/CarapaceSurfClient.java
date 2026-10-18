package com.projecthero.mod.client.miraculous.carapace;

import com.projecthero.mod.miraculous.carapace.Carapace;
import com.projecthero.mod.miraculous.carapace.CarapaceConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6, X: the Shield Surf ride, simulated by the rider's own client (players' movement is client-side; the server
 * only holds the toggle and gives the extra step height). Holding forward glides along the look at
 * {@link CarapaceConfig#SURF_SPEED} (faster on ice, {@link CarapaceConfig#SURF_ICE_SPEED}) over ground, snow and water;
 * letting go coasts to a stop. On water the board floats: it rides the surface instead of sinking (Shift sinks).
 */
public final class CarapaceSurfClient {
	private CarapaceSurfClient() {
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused() || !Carapace.surfing(p) || p.getAbilities().flying || p.isPassenger()) {
			speed = 0.0;
			heading = null;
			return;
		}
		Vec3 v = p.getDeltaMovement();
		BlockPos feet = p.blockPosition();
		BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
		boolean water = p.level().getFluidState(feet).is(FluidTags.WATER) || p.level().getFluidState(under).is(FluidTags.WATER);
		boolean ice = p.level().getBlockState(under).is(BlockTags.ICE);
		double vy = v.y;
		if (water && !p.isShiftKeyDown() && !p.isUnderWater()) {
			// float on the surface: rise while the feet are in the water, hold level just above it
			double surface = feet.getY() + p.level().getFluidState(feet).getHeight(p.level(), feet);
			if (!p.level().getFluidState(feet).is(FluidTags.WATER)) {
				surface = under.getY() + p.level().getFluidState(under).getHeight(p.level(), under);
			}
			double gap = surface - p.getY();
			vy = gap > -0.05 ? Math.min(0.25, gap * 0.6 + 0.1) : Math.max(vy, 0.0);
			p.resetFallDistance();
		}
		double target = ice ? CarapaceConfig.SURF_ICE_SPEED : CarapaceConfig.SURF_SPEED;
		Vec3 look = p.getLookAngle();
		Vec3 dir = new Vec3(look.x, 0, look.z);
		if (dir.lengthSqr() > 1.0e-4) {
			heading = heading == null ? dir.normalize() : heading.lerp(dir.normalize(), 0.35).normalize();
		}
		if (p.input.forwardImpulse > 0.1f) {
			speed = Math.min(target, speed + (water ? 0.04 : 0.06));
		} else {
			speed *= water ? 0.94 : 0.9;
		}
		if (p.horizontalCollision && speed > 0.2) {
			speed = 0.2;
		}
		if (heading == null || speed < 0.02) {
			speed = 0.0;
			if (water) {
				p.setDeltaMovement(v.x, vy, v.z);
			}
			return;
		}
		// the move this tick is this velocity (vanilla's friction only applies after it)
		p.setDeltaMovement(heading.x * speed, vy, heading.z * speed);
	}

	/** The board's own speed and heading (the local rider only). */
	private static double speed;
	private static Vec3 heading;
}
