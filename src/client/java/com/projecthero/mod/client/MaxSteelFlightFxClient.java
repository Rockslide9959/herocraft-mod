package com.projecthero.mod.client;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.maxsteel.MaxSteel;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side Max Steel flight cosmetics (v0.6.17):
 * <ul>
 *   <li>a cyan energy <b>trail</b> streaming off every player in Turbo Flight -- denser than the
 *       server-side thrusters, and drawn at the interpolated render position so it actually reads as
 *       a ribbon;</li>
 *   <li>the Turbo Blast <b>charge-up</b>: while the local player holds Ability&nbsp;1 suited, cyan
 *       particles converge on the firing hand, thicker the longer it is held.</li>
 * </ul>
 */
public final class MaxSteelFlightFxClient {
	private static int chargeTicks;

	private MaxSteelFlightFxClient() {
	}

	public static void clientTick(Minecraft client) {
		if (client.level == null || client.player == null) {
			chargeTicks = 0;
			return;
		}
		float pt = client.getTimer().getGameTimeDeltaPartialTick(false);

		for (Player player : client.level.players()) {
			if (MaxSteel.isTransformed(player)
					&& player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)) {
				emitTrail(client, player, pt);
			}
		}

		tickBlastCharge(client, pt);
	}

	private static void emitTrail(Minecraft client, Player player, float pt) {
		double x = Mth.lerp(pt, player.xo, player.getX());
		double y = Mth.lerp(pt, player.yo, player.getY());
		double z = Mth.lerp(pt, player.zo, player.getZ());
		Vec3 v = player.getDeltaMovement();
		double speed = v.length();
		Vec3 back = v.lengthSqr() > 1.0E-4 ? v.normalize().scale(-0.25) : Vec3.ZERO;
		var rand = client.level.random;

		// v0.6.20: the trail streams off three fixed points on the body -- the feet and the two
		// extended T.U.R.B.O. wing tips -- and tracks them, instead of a cloud around the waist.
		float bodyYaw = Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot);
		Vec3 right = Vec3.directionFromRotation(0.0f, bodyYaw + 90.0f);
		Vec3 fwd = Vec3.directionFromRotation(0.0f, bodyYaw);
		Vec3 feet = new Vec3(x, y + 0.1, z);
		// The wings mount near the neck, spread ~0.95 to each side and swept a little back and down
		// in the glide pose held by MaxSteelWingsModel.
		Vec3 wingRoot = new Vec3(x, y + player.getBbHeight() * 0.72, z).add(fwd.scale(-0.15));
		Vec3 leftTip = wingRoot.add(right.scale(-0.95)).add(0.0, -0.15, 0.0);
		Vec3 rightTip = wingRoot.add(right.scale(0.95)).add(0.0, -0.15, 0.0);

		int puffs = speed > 0.25 ? 2 : 1;
		emitFrom(client, feet, back, rand, puffs);
		emitFrom(client, leftTip, back, rand, puffs);
		emitFrom(client, rightTip, back, rand, puffs);
	}

	private static void emitFrom(Minecraft client, Vec3 origin, Vec3 back,
			net.minecraft.util.RandomSource rand, int puffs) {
		for (int i = 0; i < puffs; i++) {
			double sx = origin.x + back.x + (rand.nextDouble() - 0.5) * 0.28;
			double sy = origin.y + back.y + (rand.nextDouble() - 0.5) * 0.28;
			double sz = origin.z + back.z + (rand.nextDouble() - 0.5) * 0.28;
			client.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, sx, sy, sz,
					back.x * 0.3, back.y * 0.3, back.z * 0.3);
			if (rand.nextBoolean()) {
				client.level.addParticle(ParticleTypes.END_ROD, sx, sy, sz,
						back.x * 0.2, back.y * 0.2 + 0.01, back.z * 0.2);
			}
		}
	}

	private static void tickBlastCharge(Minecraft client, float pt) {
		Player player = client.player;
		boolean holding = com.projecthero.mod.client.ModKeyBindings.ABILITY_1.isDown()
				&& MaxSteel.isTransformed(player) && client.screen == null;
		if (!holding) {
			chargeTicks = 0;
			return;
		}
		chargeTicks = Math.min(chargeTicks + 1, 40);
		if (chargeTicks < 4) {
			return; // let the hold-to-power-down / tap gestures happen without FX noise
		}
		float frac = Math.min(1.0f, chargeTicks / 30.0f);

		Vec3 look = player.getViewVector(pt);
		Vec3 side = new Vec3(-look.z, 0, look.x);
		if (side.lengthSqr() > 1.0E-4) {
			side = side.normalize().scale(0.32);
		}
		Vec3 hand = player.getEyePosition().add(look.scale(0.7)).add(side).subtract(0, 0.25, 0);

		int count = 2 + Math.round(frac * 6);
		var rand = client.level.random;
		for (int i = 0; i < count; i++) {
			double r = 0.7 + rand.nextDouble() * 0.9 * (1.0 - frac * 0.4);
			double a = rand.nextDouble() * Math.PI * 2;
			double e = (rand.nextDouble() - 0.5) * 1.4;
			double px = hand.x + Math.cos(a) * r;
			double py = hand.y + e;
			double pz = hand.z + Math.sin(a) * r;
			// velocity points back at the hand -- particles collect
			Vec3 pull = hand.subtract(new Vec3(px, py, pz)).scale(0.35);
			client.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, pull.x, pull.y, pull.z);
			if (frac > 0.5 && rand.nextBoolean()) {
				client.level.addParticle(ParticleTypes.END_ROD, px, py, pz, pull.x, pull.y, pull.z);
			}
		}
		if (frac >= 1.0f && (client.level.getGameTime() % 3) == 0) {
			client.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z, 0, 0, 0);
		}
	}
}
