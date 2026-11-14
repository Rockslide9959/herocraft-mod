package com.projecthero.mod.client.beastboy;

import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastBoyForms;
import com.projecthero.mod.beastboy.BeastForm;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 Beast Boy sea forms (user: "make the sea creatures easier to control please"): the dolphin, shark, octopus and whale swim
 * with their own movement instead of vanilla's player swimming, on the local player only (hooked from {@code Player#travel} by
 * {@code BeastBoySwimTravelMixin}, like {@code DirectionalFlight}).
 * <ul>
 *   <li><b>Swim where you look:</b> W goes along the look (up and down too), S backs off slowly, A / D slide sideways.</li>
 *   <li><b>Space / Sneak</b> rise and sink; <b>Sprint</b> is a burst of speed.</li>
 *   <li><b>Smooth:</b> the velocity eases toward the wanted one (about half a second to full speed, a longer glide to a stop) and
 *       there's no gravity or buoyancy, so he hangs still where he stops.</li>
 *   <li><b>The surface:</b> he can't swim up out of the water by looking up -- he settles with his back just out of it and cruises
 *       along there without bobbing in and out (vanilla swimming bounces a player at the surface). Space at the surface leaps out
 *       (a dolphin jump), and swimming into a ledge near the surface climbs out onto it.</li>
 *   <li>A shove from the server (a knockback, a move's lunge) is taken as it comes and eased out of, never cancelled.</li>
 * </ul>
 * Only the local player's movement is replaced; the server and other players see the result as ordinary movement.
 */
public final class BeastSwim {
	/** The last velocity this set, to tell an outside shove from vanilla's own little Space / Sneak nudges. */
	private static Vec3 velocity;
	private static int leapCooldown;

	private BeastSwim() {
	}

	/** Cruise / sprint speed in blocks a tick. */
	static double speed(BeastForm f, boolean sprint) {
		return switch (f) {
			case DOLPHIN -> sprint ? 0.48 : 0.26;
			case SHARK -> sprint ? 0.44 : 0.24;
			case OCTOPUS -> sprint ? 0.36 : 0.19;
			case WHALE -> sprint ? 0.30 : 0.18;
			default -> 0.2;
		};
	}

	static double leap(BeastForm f) {
		return switch (f) {
			case DOLPHIN -> 0.62;
			case SHARK -> 0.5;
			case OCTOPUS -> 0.45;
			case WHALE -> 0.4;
			default -> 0.42;
		};
	}

	/** Moves {@code p} one tick if he swims as a sea animal right now; false = vanilla moves him. */
	public static boolean travel(LocalPlayer p) {
		BeastForm f = BeastBoy.form(p);
		if (!BeastBoyForms.aquatic(f) || !p.isInWater() || p.getAbilities().flying || p.isPassenger() || p.isSpectator()) {
			velocity = null;
			return false;
		}
		if (leapCooldown > 0) {
			leapCooldown--;
		}
		boolean jump = p.input.jumping;
		boolean sneak = p.input.shiftKeyDown;
		Vec3 current = p.getDeltaMovement();
		if (velocity == null) {
			velocity = current;
		} else {
			// vanilla's aiStep nudges the vertical velocity by 0.04 for Space / Sneak in water before travel: anything bigger is a shove
			double nudge = ((jump ? 1 : 0) - (sneak ? 1 : 0)) * 0.04;
			if (current.subtract(velocity.add(0, nudge, 0)).lengthSqr() > 0.12 * 0.12) {
				velocity = current;
			}
		}

		// the wanted velocity: along the look for W / S, sideways for A / D, straight up / down for Space / Sneak
		float fwd = (p.input.up ? 1f : 0f) - (p.input.down ? 1f : 0f);
		float side = (p.input.left ? 1f : 0f) - (p.input.right ? 1f : 0f);
		int vert = (jump ? 1 : 0) - (sneak ? 1 : 0);
		Vec3 look = p.getLookAngle();
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
		Vec3 dir = look.scale(fwd > 0 ? fwd : fwd * 0.45).add(left.scale(side * 0.7)).add(0, vert * 0.8, 0);
		if (dir.lengthSqr() > 1.0) {
			dir = dir.normalize();
		}
		boolean sprint = fwd > 0 && (p.isSprinting() || Minecraft.getInstance().options.keySprint.isDown());
		Vec3 target = dir.scale(speed(f, sprint));

		// the surface: hold him with his back just out of the water rather than letting the look carry him out
		double h = p.getBbHeight();
		double inWater = submersion(p);
		boolean atSurface = inWater < h * 0.98;
		if (inWater < h + 0.6) {
			double hold = (inWater - h * 0.82) * 0.3; // + = below the hold line (rise), - = above it (settle)
			if (!jump || leapCooldown > 0) {
				target = new Vec3(target.x, Math.min(target.y, Math.max(hold, -0.12)), target.z);
			}
		}
		// Space at the surface: a leap out of the water
		if (jump && atSurface && leapCooldown == 0) {
			Vec3 flat = new Vec3(velocity.x, 0, velocity.z);
			velocity = new Vec3(flat.x * 1.1, leap(f), flat.z * 1.1);
			leapCooldown = 12;
		} else {
			// ease toward the wanted velocity: quicker to speed up than to glide to a stop
			boolean speeding = target.lengthSqr() > velocity.lengthSqr();
			double k = speeding ? 0.11 : 0.075;
			velocity = velocity.add(target.subtract(velocity).scale(k));
			if (velocity.lengthSqr() < 1.0e-6) {
				velocity = Vec3.ZERO;
			}
		}

		double y0 = p.getY();
		p.setDeltaMovement(velocity);
		p.move(MoverType.SELF, velocity);
		// swimming into a ledge near the surface: climb out onto it (vanilla's in-water wall hop)
		if (p.horizontalCollision && fwd > 0 && inWater < h + 0.3
				&& p.isFree(p.getDeltaMovement().x, p.getDeltaMovement().y + 0.6 - p.getY() + y0, p.getDeltaMovement().z)) {
			p.setDeltaMovement(p.getDeltaMovement().x, 0.3, p.getDeltaMovement().z);
		}
		velocity = p.getDeltaMovement(); // Entity.move zeroes whichever components hit something
		p.resetFallDistance();
		return true;
	}

	/** How deep his feet are under the water's surface (blocks); a big number when the surface is well above his head. */
	static double submersion(LocalPlayer p) {
		double top = p.getY() + p.getBbHeight();
		BlockPos.MutableBlockPos pos = BlockPos.containing(p.getX(), p.getY(), p.getZ()).mutable();
		double surface = Double.NaN;
		while (pos.getY() <= top + 2.0) {
			FluidState fluid = p.level().getFluidState(pos);
			if (fluid.is(FluidTags.WATER)) {
				surface = pos.getY() + fluid.getHeight(p.level(), pos);
			} else if (!Double.isNaN(surface)) {
				return surface - p.getY();
			}
			pos.move(0, 1, 0);
		}
		return Double.isNaN(surface) ? 0.0 : 99.0;
	}
}
