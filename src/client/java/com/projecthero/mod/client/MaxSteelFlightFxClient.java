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
	private MaxSteelFlightFxClient() {
	}

	public static void clientTick(Minecraft client) {
		if (client.level == null || client.player == null) {
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
		// on the Turbo Flight form's own wings (v0.14.2; was the tinted-elytra layer).
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

	/**
	 * v0.14.2: sparks drawn in to the fist while any Max Steel in view charges a Turbo Blast -- read off the synced
	 * {@code MaxSteelFx} charge clock (not the local key), so everyone sees it. The orb itself is a model now
	 * ({@code MaxSteelGearLayer}); these are just the energy being pulled into it.
	 */
	private static void tickBlastCharge(Minecraft client, float pt) {
		var rand = client.level.random;
		long now = client.level.getGameTime();
		for (Player player : client.level.players()) {
			com.projecthero.mod.maxsteel.data.MaxSteelFx fx = player.getAttachedOrElse(ModAttachments.MAX_STEEL_FX, null);
			if (fx == null || fx.blastChargeStart() == 0L || !MaxSteel.isTransformed(player)) {
				continue;
			}
			if (player == client.player && client.options.getCameraType().isFirstPerson()) {
				continue; // right in front of the camera it just blinds the pilot -- they see the orb in their fist
			}
			long held = now - fx.blastChargeStart();
			if (held < 4) {
				continue;
			}
			float frac = Math.min(1.0f, held / (float) com.projecthero.mod.maxsteel.MaxSteelConfig.BLAST_MAX_CHARGE_TICKS);
			Vec3 hand = com.projecthero.mod.hero.power.AbilityHelpers.handPosition(player).add(player.getViewVector(pt).scale(0.4));
			int count = 1 + Math.round(frac * 3);
			for (int i = 0; i < count; i++) {
				double r = 0.6 + rand.nextDouble() * 0.7 * (1.0 - frac * 0.4);
				double a = rand.nextDouble() * Math.PI * 2;
				double e = (rand.nextDouble() - 0.5) * 1.2;
				double px = hand.x + Math.cos(a) * r;
				double py = hand.y + e;
				double pz = hand.z + Math.sin(a) * r;
				Vec3 pull = hand.subtract(new Vec3(px, py, pz)).scale(0.35);
				client.level.addParticle(ParticleTypes.ELECTRIC_SPARK, px, py, pz, pull.x, pull.y, pull.z);
			}
		}
	}
}
