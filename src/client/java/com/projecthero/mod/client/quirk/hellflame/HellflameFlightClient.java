package com.projecthero.mod.client.quirk.hellflame;

import com.projecthero.mod.quirk.hellflame.Hellflame;
import com.projecthero.mod.quirk.hellflame.HellflameConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7, X held (Flame Boost): the client half -- the owning client flies its player on the foot jets (the server only
 * times it, adds the Heat and keeps him from being kicked for floating). W / S along the full look (climb or dive by
 * looking), A / D sideways, Space / Sneak straight up / down, Sprint for {@link HellflameConfig#BOOST_SPRINT_SPEED}, no
 * input: a hover. The velocity eases toward the wanted one from this class's own record, so gravity never drags the
 * hover down.
 */
public final class HellflameFlightClient {
	private static Vec3 velocity;

	private HellflameFlightClient() {
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			return;
		}
		if (!Hellflame.boosting(p) || p.getAbilities().flying || p.isPassenger() || p.isSpectator() || p.isFallFlying()) {
			velocity = null;
			return;
		}
		Vec3 current = p.getDeltaMovement();
		if (velocity == null) {
			velocity = current;
		}
		double speed = p.isSprinting() ? HellflameConfig.BOOST_SPRINT_SPEED : HellflameConfig.BOOST_SPEED;
		float forward = p.input.forwardImpulse;
		float strafe = p.input.leftImpulse;
		Vec3 look = p.getLookAngle();
		double yaw = Math.toRadians(p.getYRot());
		Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		double up = (p.input.jumping ? 1 : 0) - (p.input.shiftKeyDown ? 1 : 0);
		Vec3 wanted = look.scale(forward * speed).add(left.scale(strafe * speed * 0.6)).add(0, up * HellflameConfig.BOOST_VERTICAL, 0);
		// an outside push (a knockback, the server's dodge) is taken as it is
		if (current.subtract(velocity).lengthSqr() > 0.6 * 0.6) {
			velocity = current;
		}
		velocity = velocity.lerp(wanted, HellflameConfig.BOOST_ACCEL);
		if (wanted.lengthSqr() < 1.0e-4 && velocity.lengthSqr() < 1.0e-4) {
			velocity = Vec3.ZERO;
		}
		p.setDeltaMovement(velocity);
		p.resetFallDistance();
	}
}
