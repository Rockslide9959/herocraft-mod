package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.entity.HardLightConstructEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.3: draws every {@link HardLightConstructEntity} shape out of glowing hard-light boxes ({@link HardLightDraw}):
 * bolt and beam streaks, the flying fist, the war hammer's overhead swing, missiles, the spinning buzzsaw, the anvil,
 * the giant hand closing round its prisoner, chains, the launch pad's spring and the Emerald Warrior. Models are built
 * facing +Z and turned to the entity's yaw / pitch.
 */
public class HardLightConstructRenderer extends EntityRenderer<HardLightConstructEntity> {
	public HardLightConstructRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0f;
	}

	@Override
	public boolean shouldRender(HardLightConstructEntity entity, Frustum frustum, double x, double y, double z) {
		return true; // streaks and the hammer reach well outside the tiny entity box
	}

	@Override
	public ResourceLocation getTextureLocation(HardLightConstructEntity entity) {
		return TurboDraw.WHITE;
	}

	@Override
	public void render(HardLightConstructEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		float age = e.tickCount + partial;
		float alpha = e.fade(partial);
		if (alpha <= 0.01f) {
			return;
		}
		pose.pushPose();
		switch (e.shape()) {
			case BOLT -> bolt(e, vc, pose, age, alpha, partial);
			case BEAM -> beam(e, vc, pose, age, partial);
			case FIST -> fist(e, vc, pose, age, alpha, partial);
			case HAMMER -> hammer(e, vc, pose, age, alpha);
			case MISSILE -> missile(e, vc, pose, age, alpha, partial);
			case BUZZSAW -> buzzsaw(e, vc, pose, age, alpha);
			case ANVIL -> anvil(e, vc, pose, age, alpha);
			case HAND -> hand(e, vc, pose, age, alpha, partial);
			case CHAINS -> chains(e, vc, pose, age, alpha, partial);
			case LAUNCH_PAD -> pad(e, vc, pose, age, alpha);
			case WARRIOR -> warrior(e, vc, pose, age, alpha, partial);
		}
		pose.popPose();
	}

	/** Turns the pose so +Z points along the entity's yaw / pitch. */
	private static void orient(HardLightConstructEntity e, PoseStack pose, float partial) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getViewYRot(partial)));
		pose.mulPose(Axis.XP.rotationDegrees(e.getViewXRot(partial)));
	}

	/** Turns the pose so +Z points from the entity towards {@code target} (world space); returns the distance. */
	private static double aim(HardLightConstructEntity e, PoseStack pose, Vec3 target, float partial) {
		Vec3 start = e.getPosition(partial);
		Vec3 d = target.subtract(start);
		float ry = (float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG);
		float rx = (float) (Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
		pose.mulPose(Axis.YP.rotationDegrees(ry));
		pose.mulPose(Axis.XP.rotationDegrees(-rx));
		return d.length();
	}

	// ---------------------------------------------------------------- streaks

	private static void bolt(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		double len = aim(e, pose, e.end(), partial);
		if (len < 0.05) {
			return;
		}
		float w = 0.13f * e.scale();
		float flare = Math.min(1f, age / 1.5f);
		HardLightDraw.streak(vc, pose, (float) len, w * (0.6f + 0.4f * flare), age, alpha);
		HardLightDraw.glow(vc, pose, 0, 0, 0, w * 0.35f, w * 0.35f, w * 0.35f, alpha);
		pose.translate(0f, 0f, (float) len);
		HardLightDraw.glow(vc, pose, 0, 0, 0, w * 1.2f, w * 1.2f, w * 1.2f, alpha * 0.8f);
	}

	private static void beam(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float partial) {
		double len = aim(e, pose, e.end(), partial);
		if (len < 0.05) {
			return;
		}
		float pulse = 1f + 0.12f * Mth.sin(age * 1.3f);
		float w = 0.2f * pulse;
		HardLightDraw.streak(vc, pose, (float) len, w, age, 1f);
		int rings = Math.max(2, (int) (len / 2.5));
		for (int k = 0; k < rings; k++) {
			float z = (float) (((k / (float) rings) + age * 0.07f) % 1.0f * len);
			pose.pushPose();
			pose.translate(0f, 0f, z);
			pose.mulPose(Axis.ZP.rotationDegrees(age * 30f + k * 40f));
			for (int s = 0; s < 4; s++) {
				pose.pushPose();
				pose.mulPose(Axis.ZP.rotationDegrees(s * 90f));
				pose.translate(w * 1.5f, 0f, 0f);
				TurboDraw.box(vc, pose.last(), 0.02f, w * 1.1f, 0.02f, HardLightDraw.PALE, 0.7f);
				pose.popPose();
			}
			pose.popPose();
		}
		HardLightDraw.glow(vc, pose, 0, 0, 0, w * 0.3f, w * 0.3f, w * 0.3f, 1f);
		pose.translate(0f, 0f, (float) len);
		HardLightDraw.glow(vc, pose, 0, 0, 0, w * 1.6f * pulse, w * 1.6f * pulse, w * 1.6f * pulse, 0.9f);
	}

	// ---------------------------------------------------------------- the flying fist (G)

	private static void fist(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		orient(e, pose, partial);
		float grow = Math.min(1f, age / 3f);
		float burst = e.actionTick() >= 0 ? 1f + (e.tickCount - e.actionTick() + partial) * 0.12f : 1f;
		float s = (0.55f + 0.45f * grow) * burst * e.scale();
		pose.scale(s, s, s);
		// back of the hand + palm
		HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.34f, 0.34f, 0.28f, alpha);
		// four curled fingers: knuckle row in front, second joints tucked under
		for (int i = 0; i < 4; i++) {
			float x = -0.255f + i * 0.17f;
			HardLightDraw.part(vc, pose, x, 0.14f, 0.36f, 0.075f, 0.11f, 0.09f, alpha);
			HardLightDraw.part(vc, pose, x, -0.1f, 0.32f, 0.075f, 0.1f, 0.08f, alpha);
		}
		// thumb across the front of the fingers
		HardLightDraw.part(vc, pose, 0.12f, -0.22f, 0.3f, 0.2f, 0.07f, 0.08f, alpha);
		// the ring on the ring finger
		HardLightDraw.glow(vc, pose, 0.085f, 0.14f, 0.46f, 0.05f, 0.05f, 0.02f, alpha);
		// wrist and a fading forearm streaming behind
		HardLightDraw.part(vc, pose, 0f, 0f, -0.42f, 0.24f, 0.25f, 0.16f, alpha * 0.85f);
		HardLightDraw.part(vc, pose, 0f, 0f, -0.82f, 0.21f, 0.22f, 0.26f, alpha * 0.45f);
		HardLightDraw.part(vc, pose, 0f, 0f, -1.3f, 0.18f, 0.19f, 0.24f, alpha * 0.2f);
	}

	// ---------------------------------------------------------------- the war hammer (Shift+G)

	private static void hammer(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		// pivots at the caster's shoulder, three blocks back, and comes down in an accelerating arc
		float swing = GreenLanternConfig.HAMMER_SWING_TICKS;
		float t = Math.min(1f, age / swing);
		float impact = 18.4f;
		float theta = -125f + (impact + 125f) * t * t;
		float grow = Math.min(1f, age / 3f);
		pose.translate(0f, 1.5f, -3.0f);
		pose.mulPose(Axis.XP.rotationDegrees(theta));
		float len = 3.16f;
		// handle with two grip bands
		HardLightDraw.part(vc, pose, 0f, 0f, len * 0.5f - 0.3f, 0.08f, 0.08f, len * 0.5f - 0.1f, alpha * grow);
		HardLightDraw.part(vc, pose, 0f, 0f, 0.35f, 0.11f, 0.11f, 0.12f, alpha * grow);
		HardLightDraw.part(vc, pose, 0f, 0f, 0.75f, 0.11f, 0.11f, 0.06f, alpha * grow);
		// the head: a block of light twice a man's width, faces rimmed, the emblem on its side
		pose.translate(0f, 0f, len);
		float hs = 0.55f + 0.45f * grow;
		pose.scale(hs, hs, hs);
		HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.6f, 0.95f, 0.6f, alpha);
		HardLightDraw.part(vc, pose, 0f, 0.95f, 0f, 0.66f, 0.1f, 0.66f, alpha);
		HardLightDraw.part(vc, pose, 0f, -0.95f, 0f, 0.66f, 0.1f, 0.66f, alpha);
		pose.pushPose();
		pose.translate(0.62f, 0f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(90f));
		HardLightDraw.emblem(vc, pose, 0.9f, alpha);
		pose.popPose();
		pose.pushPose();
		pose.translate(-0.62f, 0f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(-90f));
		HardLightDraw.emblem(vc, pose, 0.9f, alpha);
		pose.popPose();
		if (e.actionTick() >= 0) {
			// the shockwave flash where it lands
			float k = (age - e.actionTick()) / 8f;
			if (k < 1f) {
				HardLightDraw.glow(vc, pose, 0f, -1.0f, 0f, 0.9f + k * 2f, 0.08f, 0.9f + k * 2f, (1f - k) * alpha);
			}
		}
	}

	// ---------------------------------------------------------------- missiles (Shift+C)

	private static void missile(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		orient(e, pose, partial);
		HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.1f, 0.1f, 0.3f, alpha);
		HardLightDraw.part(vc, pose, 0f, 0f, 0.36f, 0.07f, 0.07f, 0.07f, alpha);
		HardLightDraw.glow(vc, pose, 0f, 0f, 0.45f, 0.035f, 0.035f, 0.035f, alpha);
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(age * 20f));
		for (int i = 0; i < 4; i++) {
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 90f));
			HardLightDraw.part(vc, pose, 0f, 0.16f, -0.24f, 0.015f, 0.08f, 0.09f, alpha);
			pose.popPose();
		}
		pose.popPose();
		float flicker = 0.8f + 0.2f * Mth.sin(age * 3.1f);
		HardLightDraw.glow(vc, pose, 0f, 0f, -0.36f, 0.07f * flicker, 0.07f * flicker, 0.06f, alpha);
		TurboDraw.box(vc, pose.last(), 0.04f, 0.04f, 0.3f, HardLightDraw.GREEN, 0.25f * alpha);
	}

	// ---------------------------------------------------------------- buzzsaw

	private static void buzzsaw(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		pose.translate(0f, 0.1f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		pose.mulPose(Axis.XP.rotationDegrees(12f));
		pose.mulPose(Axis.YP.rotationDegrees(age * 48f));
		for (int k = 0; k < 3; k++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(k * 30f));
			HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.5f, 0.035f, 0.5f, alpha * 0.8f);
			pose.popPose();
		}
		for (int i = 0; i < 12; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 30f));
			pose.translate(0.68f, 0f, 0f);
			pose.mulPose(Axis.YP.rotationDegrees(35f));
			HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.13f, 0.03f, 0.05f, alpha);
			pose.popPose();
		}
		HardLightDraw.glow(vc, pose, 0f, 0f, 0f, 0.13f, 0.07f, 0.13f, alpha);
	}

	// ---------------------------------------------------------------- anvil

	private static void anvil(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		float s = e.scale();
		if (e.actionTick() >= 0) {
			float k = Math.min(1f, (age - e.actionTick()) / 6f);
			float squash = 1f - 0.18f * Mth.sin(k * Mth.PI);
			pose.scale(1f + (1f - squash) * 0.6f, squash, 1f + (1f - squash) * 0.6f);
		} else {
			float wobble = Mth.sin(age * 0.6f) * 4f;
			pose.mulPose(Axis.ZP.rotationDegrees(wobble));
		}
		pose.scale(s, s, s);
		HardLightDraw.part(vc, pose, 0f, 0.1f, 0f, 0.52f, 0.1f, 0.34f, alpha);      // foot
		HardLightDraw.part(vc, pose, 0f, 0.27f, 0f, 0.36f, 0.07f, 0.24f, alpha);    // step
		HardLightDraw.part(vc, pose, 0f, 0.5f, 0f, 0.2f, 0.16f, 0.17f, alpha);      // waist
		HardLightDraw.part(vc, pose, 0f, 0.8f, 0f, 0.56f, 0.14f, 0.28f, alpha);     // face
		HardLightDraw.part(vc, pose, 0.72f, 0.84f, 0f, 0.18f, 0.09f, 0.16f, alpha); // horn
		HardLightDraw.part(vc, pose, 0.95f, 0.87f, 0f, 0.08f, 0.05f, 0.09f, alpha);
		HardLightDraw.part(vc, pose, -0.62f, 0.8f, 0f, 0.07f, 0.12f, 0.22f, alpha); // heel
		pose.pushPose();
		pose.translate(0f, 0.8f, 0.29f);
		HardLightDraw.emblem(vc, pose, 0.3f, alpha);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the giant hand (H)

	private static void hand(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		Entity held = e.level().getEntity(e.targetId());
		float thrown = e.actionTick() >= 0 ? (age - e.actionTick()) : -1f;
		if (thrown > 8f) {
			return;
		}
		float fade = thrown >= 0 ? alpha * (1f - thrown / 8f) : alpha;
		float s = e.scale();
		// centred on what it holds; turned to face the way the Lantern looks
		if (held != null && thrown < 0) {
			Vec3 c = held.getPosition(partial).add(0, held.getBbHeight() * 0.5, 0).subtract(e.getPosition(partial));
			pose.translate(c.x, c.y, c.z);
		}
		orient(e, pose, partial);
		float close = thrown >= 0 ? 1f - Math.min(1f, thrown / 4f) : Math.min(1f, age / 5f);
		float squeeze = thrown < 0 && age > 5f ? 0.03f * Mth.sin(age * 0.9f) : 0f;
		pose.scale(s * (1f - squeeze), s * (1f - squeeze), s * (1f - squeeze));
		// palm over the top, the ring glowing on it
		HardLightDraw.part(vc, pose, 0f, 0.72f, 0f, 0.52f, 0.1f, 0.44f, fade);
		HardLightDraw.glow(vc, pose, 0.2f, 0.84f, -0.25f, 0.08f, 0.03f, 0.08f, fade);
		// forearm reaching back up toward the Lantern
		HardLightDraw.part(vc, pose, 0f, 1.05f, 0.35f, 0.28f, 0.24f, 0.3f, fade * 0.7f);
		HardLightDraw.part(vc, pose, 0f, 1.35f, 0.75f, 0.24f, 0.2f, 0.3f, fade * 0.35f);
		// four fingers curling down the near side, a thumb down the far side
		float open = (1f - close) * 55f;
		for (int i = 0; i < 4; i++) {
			float x = -0.36f + i * 0.24f;
			finger(vc, pose, x, 0.66f, -0.44f, open, -1f, fade);
		}
		finger(vc, pose, 0f, 0.66f, 0.44f, open, 1f, fade);
	}

	/** One two-jointed finger hanging from its knuckle at (x, y, z); {@code side} -1 = near side, 1 = far side. */
	private static void finger(VertexConsumer vc, PoseStack pose, float x, float y, float z, float open, float side, float alpha) {
		pose.pushPose();
		pose.translate(x, y, z);
		pose.mulPose(Axis.XP.rotationDegrees(side * -open));
		HardLightDraw.part(vc, pose, 0f, -0.28f, 0f, 0.09f, 0.28f, 0.09f, alpha);
		pose.translate(0f, -0.56f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(side * (22f + open * 0.4f)));
		HardLightDraw.part(vc, pose, 0f, -0.24f, 0f, 0.085f, 0.24f, 0.085f, alpha);
		pose.popPose();
	}

	// ---------------------------------------------------------------- chains

	private static void chains(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		Entity held = e.level().getEntity(e.targetId());
		float h = held != null ? held.getBbHeight() : 1.8f;
		float r = Math.max(0.35f, e.scale() * 0.62f);
		float grow = Math.min(1f, age / 6f);
		float ringY = h * 0.45f;
		// the shackle ring round the waist
		pose.pushPose();
		pose.translate(0f, ringY, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(age * 3f));
		for (int i = 0; i < 12; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 30f));
			pose.translate(r, 0f, 0f);
			HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.04f, i % 2 == 0 ? 0.07f : 0.03f, r * 0.28f, alpha * grow);
			pose.popPose();
		}
		pose.popPose();
		// four chains from stakes of light in the ground
		float stakeR = r + 1.1f;
		for (int k = 0; k < 4; k++) {
			double a = Math.toRadians(45 + k * 90);
			Vec3 stake = new Vec3(Math.cos(a) * stakeR, 0.05, Math.sin(a) * stakeR);
			Vec3 top = new Vec3(Math.cos(a) * r, ringY, Math.sin(a) * r);
			HardLightDraw.part(vc, pose, (float) stake.x, 0.12f, (float) stake.z, 0.07f, 0.18f, 0.07f, alpha);
			Vec3 d = top.subtract(stake);
			double len = d.length() * grow;
			pose.pushPose();
			pose.translate(stake.x, stake.y, stake.z);
			pose.mulPose(Axis.YP.rotationDegrees((float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG)));
			pose.mulPose(Axis.XP.rotationDegrees((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG)));
			int links = Math.max(2, (int) (len / 0.16));
			for (int i = 0; i < links; i++) {
				pose.pushPose();
				pose.translate(0f, 0f, (float) (i * len / links));
				pose.mulPose(Axis.ZP.rotationDegrees(i % 2 == 0 ? 0f : 90f));
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.055f, 0.018f, 0.09f, alpha);
				pose.popPose();
			}
			pose.popPose();
		}
	}

	// ---------------------------------------------------------------- launch pad

	private static void pad(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha) {
		pose.mulPose(Axis.YP.rotationDegrees(-e.getYRot()));
		float grow = Math.min(1f, age / 6f);
		float since = e.actionTick() >= 0 ? age - e.actionTick() : 99f;
		float extend = since < 8f ? Mth.sin(Math.min(1f, since / 8f) * Mth.PI) : 0f;
		float top = 0.42f + 0.38f * extend;
		HardLightDraw.part(vc, pose, 0f, 0.05f, 0f, 0.9f * grow, 0.05f, 0.9f * grow, alpha);
		for (int c = 0; c < 3; c++) {
			float y = 0.12f + (top - 0.12f) * (c + 0.5f) / 3f;
			float w = 0.5f - c * 0.06f;
			HardLightDraw.part(vc, pose, 0f, y, w, w, 0.025f, 0.025f, alpha);
			HardLightDraw.part(vc, pose, 0f, y, -w, w, 0.025f, 0.025f, alpha);
			HardLightDraw.part(vc, pose, w, y, 0f, 0.025f, 0.025f, w, alpha);
			HardLightDraw.part(vc, pose, -w, y, 0f, 0.025f, 0.025f, w, alpha);
		}
		HardLightDraw.part(vc, pose, 0f, top, 0f, 0.75f * grow, 0.045f, 0.75f * grow, alpha);
		// an up-arrow of light on the pad, pulsing
		float p = 0.6f + 0.4f * Mth.sin(age * 0.25f);
		HardLightDraw.glow(vc, pose, 0f, top + 0.06f, 0.05f, 0.05f, 0.015f, 0.3f, alpha * p);
		pose.pushPose();
		pose.translate(0f, top + 0.06f, 0.25f);
		pose.mulPose(Axis.YP.rotationDegrees(45f));
		HardLightDraw.glow(vc, pose, 0.12f, 0f, 0f, 0.14f, 0.015f, 0.04f, alpha * p);
		pose.popPose();
		pose.pushPose();
		pose.translate(0f, top + 0.06f, 0.25f);
		pose.mulPose(Axis.YP.rotationDegrees(-45f));
		HardLightDraw.glow(vc, pose, -0.12f, 0f, 0f, 0.14f, 0.015f, 0.04f, alpha * p);
		pose.popPose();
	}

	// ---------------------------------------------------------------- the Emerald Warrior

	private static void warrior(HardLightConstructEntity e, VertexConsumer vc, PoseStack pose, float age, float alpha, float partial) {
		float form = Math.min(1f, age / 10f);
		float bob = Mth.sin(age * 0.15f) * 0.06f;
		pose.translate(0f, 0.15f + bob, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(-e.getViewYRot(partial)));
		float a = alpha * form;
		// legs, drifting a little as it floats
		float stride = Mth.sin(age * 0.2f) * 10f;
		limb(vc, pose, 0.13f, 0.72f, 0f, stride, 0.12f, 0.36f, a);
		limb(vc, pose, -0.13f, 0.72f, 0f, -stride, 0.12f, 0.36f, a);
		// torso, belt, chest plate with the emblem
		HardLightDraw.part(vc, pose, 0f, 1.08f, 0f, 0.26f, 0.36f, 0.14f, a);
		HardLightDraw.part(vc, pose, 0f, 0.78f, 0f, 0.27f, 0.05f, 0.15f, a);
		HardLightDraw.part(vc, pose, 0.3f, 1.4f, 0f, 0.1f, 0.07f, 0.16f, a);
		HardLightDraw.part(vc, pose, -0.3f, 1.4f, 0f, 0.1f, 0.07f, 0.16f, a);
		pose.pushPose();
		pose.translate(0f, 1.18f, 0.15f);
		HardLightDraw.emblem(vc, pose, 0.3f, a);
		pose.popPose();
		// helmet with a crest and a glowing visor
		HardLightDraw.part(vc, pose, 0f, 1.66f, 0f, 0.2f, 0.21f, 0.2f, a);
		HardLightDraw.part(vc, pose, 0f, 1.92f, -0.02f, 0.03f, 0.08f, 0.2f, a);
		HardLightDraw.glow(vc, pose, 0f, 1.68f, 0.2f, 0.14f, 0.025f, 0.01f, a);
		// sword arm: a swing when it strikes, otherwise held at the ready
		float since = e.actionTick() >= 0 ? age - e.actionTick() : 99f;
		float swing;
		if (since < 3f) {
			swing = Mth.lerp(since / 3f, -60f, -170f);
		} else if (since < 8f) {
			swing = Mth.lerp((since - 3f) / 5f, -170f, 10f);
		} else {
			swing = -55f + Mth.sin(age * 0.1f) * 4f;
		}
		pose.pushPose();
		pose.translate(-0.37f, 1.38f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(swing));
		HardLightDraw.part(vc, pose, 0f, -0.3f, 0f, 0.09f, 0.32f, 0.09f, a);
		// the sword out of the fist
		pose.translate(0f, -0.62f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(90f));
		HardLightDraw.part(vc, pose, 0f, 0f, 0.06f, 0.2f, 0.03f, 0.04f, a);
		HardLightDraw.part(vc, pose, 0f, 0f, 0.62f, 0.05f, 0.015f, 0.55f, a);
		HardLightDraw.glow(vc, pose, 0f, 0f, 1.2f, 0.03f, 0.01f, 0.06f, a);
		pose.popPose();
		// shield arm
		pose.pushPose();
		pose.translate(0.37f, 1.38f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(-35f));
		HardLightDraw.part(vc, pose, 0f, -0.3f, 0f, 0.09f, 0.32f, 0.09f, a);
		HardLightDraw.part(vc, pose, 0.1f, -0.38f, 0.12f, 0.04f, 0.34f, 0.26f, a);
		pose.pushPose();
		pose.translate(0.15f, -0.38f, 0.12f);
		pose.mulPose(Axis.YP.rotationDegrees(90f));
		HardLightDraw.emblem(vc, pose, 0.34f, a);
		pose.popPose();
		pose.popPose();
	}

	private static void limb(VertexConsumer vc, PoseStack pose, float x, float hip, float z, float angle, float hw, float hh,
			float alpha) {
		pose.pushPose();
		pose.translate(x, hip, z);
		pose.mulPose(Axis.XP.rotationDegrees(angle));
		HardLightDraw.part(vc, pose, 0f, -hh, 0f, hw, hh, hw, alpha);
		pose.popPose();
	}
}
