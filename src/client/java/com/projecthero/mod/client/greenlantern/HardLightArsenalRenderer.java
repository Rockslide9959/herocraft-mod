package com.projecthero.mod.client.greenlantern;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.greenlantern.GreenLanternArsenal;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Green Lantern revamp: the models of the fifteen new constructs ({@code GreenLanternArsenal}), built from
 * hard-light boxes like the rest of {@link HardLightConstructRenderer}. The pose arrives at the entity's position.
 */
final class HardLightArsenalRenderer {
	private HardLightArsenalRenderer() {
	}

	static void render(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		switch (e.shape()) {
			case GLOVE -> glove(e, vc, pose, age, alpha, partial);
			case SWATTER -> swatter(e, vc, pose, age, alpha);
			case CATAPULT -> catapult(e, vc, pose, age, alpha);
			case BOULDER -> boulder(e, vc, pose, age, alpha, partial);
			case TENTACLE -> tentacle(e, vc, pose, age, alpha);
			case WRECKING_BALL -> wreckingBall(e, vc, pose, age, alpha);
			case CANNON -> cannon(e, vc, pose, age, alpha, partial);
			case CANNON_SHOT -> line(e, vc, pose, age, alpha, partial, 0.35f * e.scale());
			case BOW -> bow(e, vc, pose, age, alpha, partial);
			case ARROW -> arrow(e, vc, pose, alpha, partial);
			case BULLDOZER -> bulldozer(e, vc, pose, age, alpha, partial);
			case BEAR_TRAP -> bearTrap(e, vc, pose, age, alpha);
			case MIRROR -> mirror(e, vc, pose, age, alpha, partial);
			case CLAW -> claw(e, vc, pose, age, alpha, partial);
			case JET -> jet(e, vc, pose, age, alpha, partial);
			case DRAGON -> dragon(e, vc, pose, age, alpha, partial);
			case MACE -> mace(e, vc, pose, age, alpha, partial);
			case MED_BAY -> medBay(vc, pose, age, alpha);
			default -> {
			}
		}
	}

	private static void orient(HardLightConstructEntity e, PoseStack pose, float partial) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getViewYRot(partial)));
		pose.mulPose(Axis.XP.rotationDegrees(e.getViewXRot(partial)));
	}

	private static double aim(HardLightConstructEntity e, PoseStack pose, Vec3 target, float partial) {
		Vec3 d = target.subtract(e.getPosition(partial));
		pose.mulPose(Axis.YP.rotationDegrees((float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG)));
		pose.mulPose(Axis.XP.rotationDegrees((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG)));
		return d.length();
	}

	private static void p(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, float a) {
		HardLightDraw.part(vc, pose, x, y, z, hx, hy, hz, a);
	}

	// ---- Boxing Glove: a coiled spring shoots out and the glove punches, then it snaps back

	private static void glove(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		float len = (float) aim(e, pose, e.end(), partial);
		float k = age < 4f ? age / 4f : age < 8f ? 1f : Math.max(0f, 1f - (age - 8f) / 6f);
		float reach = Math.max(0.4f, len * k);
		int coils = Math.max(8, (int) (reach * 3.2f)); // a tight spring, not loose dashes
		for (int i = 0; i < coils; i++) {
			float z = (i + 0.5f) * reach / coils;
			float x = (i % 2 == 0 ? 0.14f : -0.14f);
			pose.pushPose();
			pose.translate(0f, 0f, z);
			pose.mulPose(Axis.YP.rotationDegrees(i % 2 == 0 ? 35f : -35f));
			p(vc, pose, x * 0.3f, 0f, 0f, 0.17f, 0.03f, 0.03f + reach / coils * 0.5f, alpha);
			pose.popPose();
		}
		pose.translate(0f, 0f, reach + 0.35f);
		p(vc, pose, 0f, 0f, 0f, 0.42f, 0.38f, 0.36f, alpha);
		p(vc, pose, 0f, 0f, -0.36f, 0.3f, 0.3f, 0.12f, alpha);
		p(vc, pose, -0.44f, -0.06f, -0.05f, 0.1f, 0.14f, 0.2f, alpha);
		HardLightDraw.glow(vc, pose, 0f, 0.1f, 0.3f, 0.12f, 0.12f, 0.04f, alpha);
	}

	// ---- Fly Swatter: raised high, then a whip-crack down flat onto the spot

	private static void swatter(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		float t = Math.min(1f, age / 6f);
		float theta = -100f * (1f - t * t);
		pose.translate(0f, 0.25f, -5.5f);
		pose.mulPose(Axis.XP.rotationDegrees(theta));
		p(vc, pose, 0f, 0f, 1.6f, 0.07f, 0.07f, 1.6f, alpha);
		p(vc, pose, 0f, 0f, 0.25f, 0.11f, 0.11f, 0.3f, alpha);
		// the mesh head: a frame with a grid of light across it
		float c = 5.5f;
		float hw = 1.9f;
		p(vc, pose, 0f, 0f, c - hw, hw, 0.06f, 0.06f, alpha);
		p(vc, pose, 0f, 0f, c + hw, hw, 0.06f, 0.06f, alpha);
		p(vc, pose, -hw, 0f, c, 0.06f, 0.06f, hw, alpha);
		p(vc, pose, hw, 0f, c, 0.06f, 0.06f, hw, alpha);
		for (int i = -3; i <= 3; i++) {
			float o = i * hw / 4f;
			p(vc, pose, o, 0f, c, 0.02f, 0.02f, hw, alpha * 0.8f);
			p(vc, pose, 0f, 0f, c + o, hw, 0.02f, 0.02f, alpha * 0.8f);
		}
		if (e.actionTick() >= 0) {
			float k = (age - e.actionTick()) / 8f;
			if (k < 1f) {
				HardLightDraw.glow(vc, pose, 0f, -0.1f, c, hw + k * 2f, 0.05f, hw + k * 2f, (1f - k) * alpha);
			}
		}
	}

	// ---- Catapult: a frame on wheels whose arm whips over

	private static void catapult(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		float grow = Math.min(1f, age / 3f);
		p(vc, pose, -0.55f, 0.25f, 0f, 0.08f, 0.08f, 1.0f, alpha * grow);
		p(vc, pose, 0.55f, 0.25f, 0f, 0.08f, 0.08f, 1.0f, alpha * grow);
		p(vc, pose, 0f, 0.25f, -0.9f, 0.55f, 0.06f, 0.06f, alpha * grow);
		p(vc, pose, 0f, 0.25f, 0.9f, 0.55f, 0.06f, 0.06f, alpha * grow);
		for (int s = -1; s <= 1; s += 2) {
			for (int f = -1; f <= 1; f += 2) {
				p(vc, pose, s * 0.66f, 0.25f, f * 0.75f, 0.04f, 0.24f, 0.24f, alpha * grow);
			}
			p(vc, pose, s * 0.5f, 0.75f, 0f, 0.06f, 0.5f, 0.06f, alpha * grow);
		}
		float swing = age < 4f ? -70f + (age / 4f) * 120f : 50f;
		pose.pushPose();
		pose.translate(0f, 1.2f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(-swing));
		p(vc, pose, 0f, 0f, 0.0f, 0.5f, 0.07f, 0.07f, alpha);
		p(vc, pose, 0f, 0.0f, -0.9f, 0.06f, 0.06f, 1.0f, alpha);
		p(vc, pose, 0f, 0.15f, -1.9f, 0.25f, 0.06f, 0.25f, alpha);
		pose.popPose();
	}

	private static void boulder(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		pose.mulPose(Axis.YP.rotationDegrees(age * 13f));
		pose.mulPose(Axis.XP.rotationDegrees(age * 9f));
		p(vc, pose, 0f, 0f, 0f, 0.55f, 0.5f, 0.55f, alpha);
		pose.mulPose(Axis.YP.rotationDegrees(45f));
		p(vc, pose, 0f, 0f, 0f, 0.5f, 0.58f, 0.45f, alpha * 0.8f);
		pose.mulPose(Axis.XP.rotationDegrees(45f));
		p(vc, pose, 0f, 0f, 0f, 0.45f, 0.45f, 0.6f, alpha * 0.7f);
	}

	// ---- Emerald Tentacles: a tapering, swaying arm rising out of the ground and curling round its catch

	private static void tentacle(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		int hold = GreenLanternConfig.TENTACLES_HOLD_TICKS;
		float h;
		if (e.actionTick() >= 0) {
			h = Math.max(0.2f, 2.8f - (age - e.actionTick()) * 0.6f);
		} else {
			h = Math.min(2.8f, age * 0.3f);
		}
		int segs = 12;
		for (int i = 0; i < segs; i++) {
			float f = i / (float) segs;
			float y = f * h;
			float sway = Mth.sin(age * 0.25f + f * 4f) * 0.25f * f;
			float w = 0.24f * (1f - f * 0.6f);
			p(vc, pose, sway, y, Mth.cos(age * 0.2f + f * 3f) * 0.15f * f, w, h / segs * 0.6f, w, alpha);
		}
		// the curl round whatever it holds
		if (age < hold) {
			pose.pushPose();
			pose.translate(0f, h * 0.7f, 0f);
			pose.mulPose(Axis.YP.rotationDegrees(age * 6f));
			for (int i = 0; i < 8; i++) {
				pose.pushPose();
				pose.mulPose(Axis.YP.rotationDegrees(i * 45f));
				p(vc, pose, 0.5f, i * 0.06f, 0f, 0.07f, 0.07f, 0.2f, alpha * 0.9f);
				pose.popPose();
			}
			pose.popPose();
		}
	}

	// ---- Wrecking Ball: a chain from the Lantern's hand to a huge ball, swinging round

	private static void wreckingBall(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		double a = GreenLanternArsenal.wreckingAngle(e.getYRot(), age);
		float r = (float) GreenLanternConfig.WRECKING_RADIUS;
		Vec3 hub = new Vec3(0, 1.4, 0);
		Vec3 ball = new Vec3(Math.cos(a) * r, 1.2, Math.sin(a) * r);
		Vec3 d = ball.subtract(hub);
		float len = (float) d.length();
		pose.pushPose();
		pose.translate(hub.x, hub.y, hub.z);
		pose.mulPose(Axis.YP.rotationDegrees((float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG)));
		pose.mulPose(Axis.XP.rotationDegrees((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG)));
		int links = (int) (len / 0.22f);
		for (int i = 0; i < links; i++) {
			pose.pushPose();
			pose.translate(0f, 0f, i * len / links);
			pose.mulPose(Axis.ZP.rotationDegrees(i % 2 == 0 ? 0f : 90f));
			p(vc, pose, 0f, 0f, 0f, 0.07f, 0.025f, 0.12f, alpha);
			pose.popPose();
		}
		pose.popPose();
		pose.translate(ball.x, ball.y, ball.z);
		pose.mulPose(Axis.YP.rotationDegrees(age * 20f));
		p(vc, pose, 0f, 0f, 0f, 0.6f, 0.6f, 0.6f, alpha);
		pose.mulPose(Axis.YP.rotationDegrees(45f));
		pose.mulPose(Axis.XP.rotationDegrees(45f));
		p(vc, pose, 0f, 0f, 0f, 0.55f, 0.55f, 0.55f, alpha * 0.7f);
		HardLightDraw.emblem(vc, pose, 0.5f, alpha);
	}

	// ---- Emerald Cannon (held): a barrel on the ring fist whose muzzle swells with light as it charges

	private static void cannon(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		orient(e, pose, partial);
		float k = Math.min(1f, age / GreenLanternConfig.CANNON_FULL_CHARGE_TICKS);
		p(vc, pose, 0f, 0f, 0.4f, 0.2f, 0.2f, 0.55f, alpha);
		p(vc, pose, 0f, 0f, 0.95f, 0.26f, 0.26f, 0.06f, alpha);
		p(vc, pose, 0f, 0f, 0.1f, 0.26f, 0.26f, 0.06f, alpha);
		p(vc, pose, 0f, -0.28f, 0.3f, 0.08f, 0.1f, 0.25f, alpha);
		float g = 0.08f + 0.22f * k;
		HardLightDraw.glow(vc, pose, 0f, 0f, 1.02f, g, g, 0.05f + 0.05f * k, alpha * (0.5f + 0.5f * Mth.sin(age * 0.8f) * k + 0.5f * k));
	}

	/** A thick shot of light from the entity to its end point (the Cannon). */
	private static void line(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial, float w) {
		float len = (float) aim(e, pose, e.end(), partial);
		float a = alpha * Math.max(0f, 1f - age / 10f);
		HardLightDraw.streak(vc, pose, len, w * (1f + age * 0.08f), age, a);
		pose.translate(0f, 0f, len);
		HardLightDraw.glow(vc, pose, 0f, 0f, 0f, w * 1.5f, w * 1.5f, w * 1.5f, a);
	}

	// ---- Emerald Bow (held): a recurve of light; the string draws back as you hold

	private static void bow(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		orient(e, pose, partial);
		pose.translate(0f, 0f, 0.35f);
		float draw = Math.min(1f, age / GreenLanternConfig.BOW_DRAW_TICKS);
		int n = 9;
		for (int i = 0; i < n; i++) {
			float f = (i / (float) (n - 1)) * 2f - 1f;
			float y = f * 0.7f;
			float z = (1f - f * f) * 0.22f;
			p(vc, pose, 0f, y, z, 0.04f, 0.09f, 0.04f, alpha);
		}
		float pull = -0.05f - draw * 0.45f;
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(0f, s * 0.35f, pull * 0.5f);
			p(vc, pose, 0f, 0f, 0f, 0.01f, 0.36f, 0.01f, alpha * 0.9f);
			pose.popPose();
		}
		p(vc, pose, 0f, 0f, pull + 0.35f, 0.02f, 0.02f, 0.4f, alpha);
		HardLightDraw.glow(vc, pose, 0f, 0f, pull + 0.78f, 0.05f, 0.05f, 0.06f, alpha);
	}

	private static void arrow(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float alpha, float partial) {
		orient(e, pose, partial);
		p(vc, pose, 0f, 0f, 0f, 0.025f, 0.025f, 0.4f, alpha);
		HardLightDraw.glow(vc, pose, 0f, 0f, 0.42f, 0.06f, 0.06f, 0.08f, alpha);
		p(vc, pose, 0f, 0f, -0.38f, 0.1f, 0.01f, 0.06f, alpha * 0.8f);
	}

	// ---- Bulldozer: tracks, a cab and a big blade that tips up at the end

	private static void bulldozer(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getViewYRot(partial)));
		float grow = Math.min(1f, age / 4f);
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.7f, 0.25f, 0f, 0.18f, 0.25f, 1.05f, alpha * grow);
			for (int i = 0; i < 6; i++) {
				float z = ((i * 0.38f + age * 0.15f) % 2.1f) - 1.05f;
				p(vc, pose, s * 0.7f, 0.52f, z, 0.2f, 0.02f, 0.04f, alpha * grow);
			}
		}
		p(vc, pose, 0f, 0.7f, -0.1f, 0.55f, 0.3f, 0.8f, alpha * grow);
		p(vc, pose, 0f, 1.25f, -0.35f, 0.38f, 0.28f, 0.38f, alpha * grow);
		HardLightDraw.glow(vc, pose, 0f, 1.3f, 0.04f, 0.3f, 0.12f, 0.02f, alpha * grow);
		float tip = e.actionTick() >= 0 ? Math.min(1f, (age - e.actionTick()) / 5f) : 0f;
		pose.translate(0f, 0.15f, 1.35f);
		pose.mulPose(Axis.XP.rotationDegrees(-15f - 55f * tip));
		p(vc, pose, 0f, 0.4f, 0f, 1.2f, 0.45f, 0.06f, alpha);
		p(vc, pose, 0f, 0.0f, 0.06f, 1.2f, 0.05f, 0.08f, alpha);
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.5f, 0.35f, -0.6f, 0.05f, 0.05f, 0.6f, alpha * 0.9f);
		}
	}

	// ---- Bear Trap: two rows of jaws, flat and open until something steps in

	private static void bearTrap(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		float shut = e.actionTick() >= 0 ? Math.min(1f, (age - e.actionTick()) / 2f) : 0f;
		p(vc, pose, 0f, 0.04f, 0f, 0.5f, 0.03f, 0.08f, alpha);
		HardLightDraw.glow(vc, pose, 0f, 0.06f, 0f, 0.08f, 0.04f, 0.08f, alpha * (0.5f + 0.5f * Mth.sin(age * 0.2f)));
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(0f, 0.05f, s * 0.06f);
			pose.mulPose(Axis.XP.rotationDegrees(s * (80f - 80f * shut)));
			p(vc, pose, 0f, 0f, s * 0.35f, 0.45f, 0.025f, 0.025f, alpha);
			for (int i = -3; i <= 3; i++) {
				pose.pushPose();
				pose.translate(i * 0.12f, 0f, s * 0.32f);
				pose.mulPose(Axis.XP.rotationDegrees(s * -90f));
				p(vc, pose, 0f, 0f, s * 0.07f, 0.025f, 0.025f, 0.07f, alpha);
				pose.popPose();
			}
			pose.popPose();
		}
	}

	// ---- Mirror Shield (held): a curved, bright sheet of light in front of you

	private static void mirror(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		orient(e, pose, partial);
		float grow = Math.min(1f, age / 3f);
		for (int i = -3; i <= 3; i++) {
			float a = i * 14f;
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(a));
			pose.translate(0f, 0f, 0.6f);
			p(vc, pose, 0f, 0f, 0f, 0.15f * grow, 0.8f * grow, 0.02f, alpha * 0.8f);
			HardLightDraw.glow(vc, pose, 0f, 0.6f * Mth.sin(age * 0.3f + i), 0.02f, 0.12f, 0.03f, 0.01f, alpha * 0.6f);
			pose.popPose();
		}
	}

	// ---- Grappling Claw: a line of light ending in a three-pronged claw

	private static void claw(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		float len = (float) aim(e, pose, e.end(), partial);
		float reach = len * Math.min(1f, age / 3f);
		HardLightDraw.streak(vc, pose, reach, 0.08f, age, alpha);
		pose.translate(0f, 0f, reach);
		p(vc, pose, 0f, 0f, 0f, 0.12f, 0.12f, 0.08f, alpha);
		for (int i = 0; i < 3; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 120f));
			pose.translate(0f, 0.14f, 0.1f);
			pose.mulPose(Axis.XP.rotationDegrees(-35f));
			p(vc, pose, 0f, 0f, 0.12f, 0.03f, 0.03f, 0.14f, alpha);
			pose.popPose();
		}
	}

	// ---- Fighter Jet: a swept-wing jet of light banking round its circle

	private static void jet(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		orient(e, pose, partial);
		pose.mulPose(Axis.ZP.rotationDegrees(-25f));
		float grow = Math.min(1f, age / 5f);
		pose.scale(grow, grow, grow);
		p(vc, pose, 0f, 0f, 0f, 0.28f, 0.26f, 1.6f, alpha);
		p(vc, pose, 0f, 0f, 1.75f, 0.14f, 0.14f, 0.25f, alpha);
		HardLightDraw.glow(vc, pose, 0f, 0.28f, 0.7f, 0.16f, 0.1f, 0.35f, alpha);
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(s * 1.0f, 0f, -0.2f);
			pose.mulPose(Axis.YP.rotationDegrees(s * -25f));
			p(vc, pose, 0f, 0f, 0f, 0.9f, 0.04f, 0.45f, alpha);
			pose.popPose();
			p(vc, pose, s * 0.45f, 0.1f, -1.45f, 0.35f, 0.03f, 0.18f, alpha);
		}
		p(vc, pose, 0f, 0.45f, -1.4f, 0.03f, 0.35f, 0.25f, alpha);
		HardLightDraw.glow(vc, pose, 0f, 0f, -1.7f, 0.2f, 0.2f, 0.12f + 0.06f * Mth.sin(age), alpha);
	}

	// ---- Emerald Dragon: a long body trailing behind a horned head, wings beating

	private static void dragon(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		orient(e, pose, partial);
		float grow = 0.6f * Math.min(1f, age / 6f); // a companion, not a boss
		pose.scale(grow, grow, grow);
		// the tail and body: segments that ripple behind the head
		for (int i = 0; i < 10; i++) {
			float z = -0.3f - i * 0.45f;
			float y = Mth.sin(age * 0.25f - i * 0.6f) * 0.18f * (i / 10f + 0.3f);
			float x = Mth.cos(age * 0.2f - i * 0.5f) * 0.12f * (i / 10f);
			float w = 0.3f * (1f - i * 0.075f);
			p(vc, pose, x, y, z, w, w * 0.85f, 0.26f, alpha);
			if (i < 6) {
				p(vc, pose, x, y + w, z, 0.03f, 0.08f, 0.08f, alpha * 0.8f);
			}
		}
		// the head with its jaw open while it breathes
		boolean breathing = e.actionTick() >= 0 && age - e.actionTick() < 8f;
		float jaw = breathing ? 22f : 6f;
		p(vc, pose, 0f, 0.05f, 0.35f, 0.24f, 0.18f, 0.3f, alpha);
		pose.pushPose();
		pose.translate(0f, -0.1f, 0.2f);
		pose.mulPose(Axis.XP.rotationDegrees(jaw));
		p(vc, pose, 0f, 0f, 0.3f, 0.2f, 0.05f, 0.3f, alpha);
		pose.popPose();
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.14f, 0.3f, 0.15f, 0.03f, 0.14f, 0.03f, alpha);
			HardLightDraw.glow(vc, pose, s * 0.13f, 0.12f, 0.55f, 0.04f, 0.03f, 0.02f, alpha);
			// the wings
			pose.pushPose();
			pose.translate(s * 0.28f, 0.15f, -0.6f);
			pose.mulPose(Axis.ZP.rotationDegrees(s * (20f + 35f * Mth.sin(age * 0.45f))));
			p(vc, pose, s * 0.9f, 0f, 0f, 0.9f, 0.025f, 0.15f, alpha);
			for (int k = 0; k < 3; k++) {
				p(vc, pose, s * (0.4f + k * 0.5f), 0f, -0.35f, 0.02f, 0.02f, 0.35f, alpha * 0.8f);
			}
			p(vc, pose, s * 0.9f, -0.01f, -0.35f, 0.85f, 0.01f, 0.35f, alpha * 0.35f);
			pose.popPose();
		}
	}

	// ---- Emerald Mace: a flanged head on a haft, in the ring hand

	private static void mace(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		orient(e, pose, partial);
		pose.mulPose(Axis.XP.rotationDegrees(-60f));
		p(vc, pose, 0f, 0f, 0.35f, 0.04f, 0.04f, 0.45f, alpha);
		pose.translate(0f, 0f, 0.9f);
		p(vc, pose, 0f, 0f, 0f, 0.16f, 0.16f, 0.18f, alpha);
		for (int i = 0; i < 6; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 60f));
			p(vc, pose, 0f, 0.2f, 0f, 0.03f, 0.08f, 0.14f, alpha);
			pose.popPose();
		}
		HardLightDraw.glow(vc, pose, 0f, 0f, 0.2f, 0.05f, 0.05f, 0.03f, alpha);
	}

	// ---- Med Bay: a dome-frame tent over a ring of light, a cross of light on top

	private static void medBay(VertexConsumer vc, PoseStack pose, float age, float alpha) {
		float r = (float) GreenLanternConfig.MEDBAY_RADIUS;
		float grow = Math.min(1f, age / 10f);
		for (int i = 0; i < 32; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 11.25f));
			p(vc, pose, r * grow, 0.05f, 0f, 0.05f, 0.04f, r * 0.1f, alpha * 0.8f);
			pose.popPose();
		}
		for (int k = 0; k < 4; k++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(k * 45f));
			for (int i = 0; i <= 12; i++) {
				double a = Math.PI * i / 12.0;
				float x = (float) (Math.cos(a) * r * grow);
				float y = (float) (Math.sin(a) * 3.0 * grow);
				p(vc, pose, x, y, 0f, 0.06f, 0.06f, 0.06f, alpha * 0.6f);
			}
			pose.popPose();
		}
		pose.translate(0f, 3.4f * grow, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(age * 2f));
		HardLightDraw.glow(vc, pose, 0f, 0f, 0f, 0.35f, 0.1f, 0.06f, alpha);
		HardLightDraw.glow(vc, pose, 0f, 0f, 0f, 0.1f, 0.35f, 0.06f, alpha);
		for (int i = 0; i < 6; i++) {
			float t = ((age * 0.05f + i / 6f) % 1f);
			float rr = r * 0.6f * (i % 3 + 1) / 3f;
			double a = i * 1.1 + age * 0.03;
			HardLightDraw.glow(vc, pose, (float) (Math.cos(a) * rr), -3.4f + t * 3f, (float) (Math.sin(a) * rr), 0.015f, 0.03f, 0.015f,
					alpha * (1f - t) * 0.7f);
		}
	}

	/** Entities the arsenal renderer may look up (kept for symmetry with the base renderer). */
	static Entity target(HardLightConstructEntity e) {
		return e.level().getEntity(e.targetId());
	}
}
