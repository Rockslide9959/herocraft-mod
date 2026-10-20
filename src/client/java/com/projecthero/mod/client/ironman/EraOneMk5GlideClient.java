package com.projecthero.mod.client.ironman;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.ability.era1.EraOneMk5Stealth;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era I Mk V Stealth, Shift+X Silent Glide -- the client half. The server times the glide (10 s), keeps the
 * wearer from being kicked for floating and holds the {@link IronManAbilityFx#COMIC_GLIDE} pose on him while it runs; the
 * owning client sees that pose on itself while wearing the Mk V and flies its player: W / S / A / D along the flat look,
 * Space up, Sneak down, no input = a still hover (the Hellflame Flame Boost pattern). The velocity eases toward the wanted
 * one from this class's own record, so gravity never drags the hover down.
 */
public final class EraOneMk5GlideClient {
	private static final double ACCEL = 0.25;
	private static Vec3 velocity;

	private EraOneMk5GlideClient() {
	}

	public static void initialize() {
		ClientTickEvents.START_CLIENT_TICK.register(EraOneMk5GlideClient::tick);
	}

	private static boolean gliding(LocalPlayer p) {
		IronManAbilityPose.Play play = IronManAbilityPose.current(p);
		return play != null && play.anim() == IronManAbilityFx.COMIC_GLIDE && EraOneMk5Stealth.SUIT.equals(IronManArmor.wornSuitId(p));
	}

	static void tick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.isPaused()) {
			return;
		}
		if (!gliding(p) || p.getAbilities().flying || p.isPassenger() || p.isSpectator() || p.isFallFlying() || p.isInWater()
				|| p.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)) {
			velocity = null;
			return;
		}
		Vec3 current = p.getDeltaMovement();
		if (velocity == null) {
			velocity = current;
		}
		double speed = EraOneMk5Stealth.GLIDE_SPEED;
		float forward = p.input.forwardImpulse;
		float strafe = p.input.leftImpulse;
		double yaw = Math.toRadians(p.getYRot());
		Vec3 ahead = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
		Vec3 left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		double up = (p.input.jumping ? 1 : 0) - (p.input.shiftKeyDown ? 1 : 0);
		Vec3 wanted = ahead.scale(forward * speed).add(left.scale(strafe * speed * 0.7)).add(0, up * EraOneMk5Stealth.GLIDE_VERTICAL, 0);
		// an outside push (a knockback, a blink) is taken as it is
		if (current.subtract(velocity).lengthSqr() > 0.6 * 0.6) {
			velocity = current;
		}
		velocity = velocity.lerp(wanted, ACCEL);
		if (wanted.lengthSqr() < 1.0e-4 && velocity.lengthSqr() < 1.0e-4) {
			velocity = Vec3.ZERO;
		}
		p.setDeltaMovement(velocity);
		p.resetFallDistance();
	}
}
