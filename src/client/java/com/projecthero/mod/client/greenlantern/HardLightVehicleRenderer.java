package com.projecthero.mod.client.greenlantern;

import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.entity.HardLightCarEntity;
import com.projecthero.mod.greenlantern.entity.HardLightJetEntity;
import com.projecthero.mod.greenlantern.entity.HardLightVehicleEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.1: the Carry Platform's Car and Jet, built in code out of hard-light boxes ({@link HardLightDraw}: a translucent,
 * full-bright green core with a pale glowing rim). Both models face +Z and are turned to the vehicle's yaw (and, for the
 * Jet, pitch and bank).
 *
 * <p>Build-in (v0.17.1, user: "make it build pixel by pixel piece by piece"): for its first
 * {@link GreenLanternConfig#VEHICLE_BUILD_TICKS} ticks a vehicle is built piece by piece in the order its parts are drawn
 * (chassis first, the wheels / wings / fins and lights last), each piece materialising pixel by pixel out of a faint
 * blueprint outline with a white-hot edge where the light is forming ({@link HardLightDraw#beginBuild}). It cannot be
 * driven until it is whole ({@code HardLightVehicleEntity#built}). It flickers when it is hurt and crackles below a third
 * of its health.
 */
public class HardLightVehicleRenderer extends EntityRenderer<HardLightVehicleEntity> {
	private static final int GREEN = HardLightDraw.GREEN;
	private static final int PALE = HardLightDraw.PALE;
	private static final int DEEP = HardLightDraw.DEEP;

	// per-draw state (rendering is single-threaded)
	private float alpha = 1f;
	/** v0.17.1: how many pieces each model drew last frame (the build's piece count). */
	private int carPieces = 70;
	private int jetPieces = 60;
	/** v0.17.1: the share of the build each piece takes to form. */
	private static final float PIECE_WINDOW = 0.2f;

	public HardLightVehicleRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 1.0f;
		this.shadowStrength = 0.4f;
	}

	@Override
	public boolean shouldRender(HardLightVehicleEntity entity, Frustum frustum, double x, double y, double z) {
		return true; // the wings and nose reach well outside the square collision box
	}

	@Override
	public ResourceLocation getTextureLocation(HardLightVehicleEntity entity) {
		return TurboDraw.WHITE;
	}

	@Override
	public void render(HardLightVehicleEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers,
			int light) {
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		float age = e.tickCount + partial;
		float build = Mth.clamp(age / GreenLanternConfig.VEHICLE_BUILD_TICKS, 0f, 1f);
		boolean building = build < 1f;
		alpha = 1f;
		if (e.hurtTime > 0) {
			alpha = 1.25f + 0.25f * Mth.sin(age * 2.5f); // struck: it flares
		} else if (e.getHealth() < e.getMaxHealth() / 3f) {
			alpha = 0.75f + 0.25f * Mth.sin(age * 1.7f) * Mth.sin(age * 0.43f); // badly hurt: it crackles
		}
		HardLightDraw.golden = e.golden();
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial, e.yRotO, e.getYRot())));
		if (e instanceof HardLightJetEntity jet) {
			if (building) {
				HardLightDraw.beginBuild(build, jetPieces, PIECE_WINDOW);
			}
			jet(jet, vc, pose, age, partial);
			if (building) {
				jetPieces = Math.max(1, HardLightDraw.endBuild());
			}
		} else if (e instanceof HardLightCarEntity car) {
			if (building) {
				HardLightDraw.beginBuild(build, carPieces, PIECE_WINDOW);
			}
			car(car, vc, pose, age, partial);
			if (building) {
				carPieces = Math.max(1, HardLightDraw.endBuild());
			}
		}
		HardLightDraw.endBuild();
		pose.popPose();
		HardLightDraw.golden = false;
		super.render(e, entityYaw, partial, pose, buffers, light);
	}

	/** A body part (formed piece by piece while building, see {@link HardLightDraw#part}). */
	private void p(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz) {
		p(vc, pose, x, y, z, hx, hy, hz, GREEN);
	}

	private void p(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb) {
		HardLightDraw.part(vc, pose, x, y, z, hx, hy, hz, rgb, alpha);
	}

	private void glow(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz) {
		HardLightDraw.glow(vc, pose, x, y, z, hx, hy, hz, Math.min(1f, alpha));
	}

	/** v0.17.1: hand-drawn hard light (glass, dials, flames) shows once its piece is half formed. */
	private static boolean formed() {
		return HardLightDraw.piece() >= 0.5f;
	}

	// ---------------------------------------------------------------- the Car

	/**
	 * A low, open-top sports car about 4.5 blocks long: a wedge nose with a splitter, a long hood with a centre stripe and
	 * the Lantern emblem, a raked windscreen, four bucket seats behind roll hoops, a rear deck with a wing on two struts,
	 * side skirts, white headlights, bright tail lights and four wheels that roll with the road and steer.
	 */
	private void car(HardLightCarEntity e, VertexConsumer vc, PoseStack pose, float age, float partial) {
		float spin = Mth.lerp(partial, e.spinO, e.spin) * Mth.RAD_TO_DEG;
		float steer = Mth.lerp(partial, e.visualTurnO, e.visualTurn) * 24f;
		// lower body and sills
		p(vc, pose, 0f, 0.44f, 0f, 0.95f, 0.18f, 2.05f);
		p(vc, pose, 0f, 0.27f, 0f, 0.8f, 0.05f, 1.95f, DEEP);
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.99f, 0.33f, 0f, 0.04f, 0.07f, 1.5f, PALE);
			// flared wheel arches
			p(vc, pose, s * 0.93f, 0.68f, 1.35f, 0.1f, 0.06f, 0.42f);
			p(vc, pose, s * 0.93f, 0.7f, -1.35f, 0.1f, 0.07f, 0.44f);
			// doors' upper edge
			p(vc, pose, s * 0.9f, 0.68f, -0.3f, 0.05f, 0.06f, 0.75f);
		}
		// the nose: a hood sloping down to a splitter
		pose.pushPose();
		pose.translate(0f, 0.62f, 1.3f);
		pose.mulPose(Axis.XP.rotationDegrees(7f));
		p(vc, pose, 0f, 0f, 0f, 0.88f, 0.06f, 0.75f);
		p(vc, pose, 0f, 0.065f, 0f, 0.11f, 0.008f, 0.72f, PALE); // the stripe
		pose.popPose();
		p(vc, pose, 0f, 0.36f, 2.13f, 0.97f, 0.06f, 0.12f);
		p(vc, pose, 0f, 0.24f, 2.2f, 0.9f, 0.025f, 0.12f, PALE); // splitter
		for (int s = -1; s <= 1; s += 2) {
			glow(vc, pose, s * 0.66f, 0.52f, 2.12f, 0.17f, 0.04f, 0.03f); // headlights
			glow(vc, pose, s * 0.72f, 0.6f, -2.07f, 0.17f, 0.035f, 0.03f); // tail lights
		}
		// the emblem lying on the hood
		if (formed()) {
			pose.pushPose();
			pose.translate(0f, 0.72f, 1.35f);
			pose.mulPose(Axis.XP.rotationDegrees(-83f));
			HardLightDraw.emblem(vc, pose, 0.5f, Math.min(1f, alpha));
			pose.popPose();
		}
		// windscreen and its frame
		pose.pushPose();
		pose.translate(0f, 0.86f, 0.62f);
		pose.mulPose(Axis.XP.rotationDegrees(-58f));
		if (formed()) {
			TurboDraw.box(vc, pose.last(), 0.78f, 0.24f, 0.015f, HardLightDraw.tint(PALE), 0.22f * Math.min(1f, alpha));
			TurboDraw.box(vc, pose.last(), 0.8f, 0.02f, 0.03f, HardLightDraw.tint(GREEN), 0.8f * Math.min(1f, alpha));
		}
		pose.popPose();
		// the cockpit: four bucket seats, a dash, a wheel, roll hoops
		p(vc, pose, 0f, 0.72f, 0.75f, 0.82f, 0.06f, 0.08f, DEEP);
		for (int s = -1; s <= 1; s += 2) {
			for (int row = 0; row < 2; row++) {
				float z = row == 0 ? 0.1f : -0.9f;
				float y = row == 0 ? 0.5f : 0.55f;
				p(vc, pose, s * 0.42f, y, z, 0.24f, 0.05f, 0.24f, DEEP);
				p(vc, pose, s * 0.42f, y + 0.27f, z - 0.25f, 0.24f, 0.26f, 0.04f, DEEP);
			}
			// roll hoops behind the front and back seats
			p(vc, pose, s * 0.42f, 1.05f, -0.2f, 0.2f, 0.03f, 0.03f, PALE);
		}
		pose.pushPose();
		pose.translate(0.42f, 0.82f, 0.55f);
		pose.mulPose(Axis.XP.rotationDegrees(-60f));
		if (formed()) {
			TurboDraw.box(vc, pose.last(), 0.15f, 0.15f, 0.02f, HardLightDraw.tint(PALE), 0.7f * Math.min(1f, alpha));
		}
		pose.popPose();
		// rear deck, diffuser and the wing
		p(vc, pose, 0f, 0.66f, -1.6f, 0.93f, 0.08f, 0.48f);
		p(vc, pose, 0f, 0.3f, -2.08f, 0.85f, 0.08f, 0.06f, DEEP);
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.6f, 0.86f, -1.92f, 0.03f, 0.14f, 0.05f);
		}
		p(vc, pose, 0f, 1.02f, -1.98f, 0.98f, 0.03f, 0.2f);
		p(vc, pose, 0f, 1.06f, -2.05f, 0.98f, 0.01f, 0.12f, PALE);
		// wheels
		for (int s = -1; s <= 1; s += 2) {
			for (int f = -1; f <= 1; f += 2) {
				float z = f * 1.35f;
				pose.pushPose();
				pose.translate(s * 0.9f, 0.33f, z);
				if (f > 0) {
					pose.mulPose(Axis.YP.rotationDegrees(steer));
				}
				pose.mulPose(Axis.XP.rotationDegrees(spin));
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.13f, 0.31f, 0.13f, DEEP, alpha);
				pose.mulPose(Axis.XP.rotationDegrees(45f));
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.13f, 0.31f, 0.13f, DEEP, alpha);
				pose.mulPose(Axis.XP.rotationDegrees(45f));
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.14f, 0.22f, 0.22f, GREEN, alpha);
				HardLightDraw.glow(vc, pose, s * 0.12f, 0f, 0f, 0.02f, 0.1f, 0.1f, Math.min(1f, alpha)); // hub
				pose.popPose();
			}
		}
	}

	// ---------------------------------------------------------------- the Jet

	/**
	 * A fighter about 6.5 blocks long and 6 across: a long fuselage with a pointed nose, a tandem bubble canopy, side
	 * intakes, swept wings with a cannon on each tip, tailplanes, twin canted fins with the emblem and a glowing exhaust
	 * that brightens with speed. Pitched to the nose and banked into turns.
	 */
	private void jet(HardLightJetEntity e, VertexConsumer vc, PoseStack pose, float age, float partial) {
		float pitch = Mth.lerp(partial, e.xRotO, e.getXRot());
		float bank = Mth.lerp(partial, e.visualTurnO, e.visualTurn) * 45f;
		pose.translate(0f, 0.6f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(bank));
		pose.translate(0f, -0.6f, 0f);
		// fuselage and nose
		p(vc, pose, 0f, 0.55f, 0f, 0.42f, 0.36f, 2.3f);
		p(vc, pose, 0f, 0.3f, 0.2f, 0.32f, 0.08f, 1.9f, DEEP);
		p(vc, pose, 0f, 0.55f, 2.65f, 0.3f, 0.26f, 0.4f);
		p(vc, pose, 0f, 0.54f, 3.15f, 0.17f, 0.15f, 0.2f);
		glow(vc, pose, 0f, 0.53f, 3.4f, 0.06f, 0.06f, 0.07f);
		// tandem bubble canopy
		if (formed()) {
			pose.pushPose();
			pose.translate(0f, 1.02f, 0.4f);
			TurboDraw.box(vc, pose.last(), 0.33f, 0.2f, 1.1f, HardLightDraw.tint(PALE), 0.25f * Math.min(1f, alpha));
			TurboDraw.box(vc, pose.last(), 0.035f, 0.21f, 1.12f, HardLightDraw.tint(GREEN), 0.6f * Math.min(1f, alpha));
			pose.popPose();
		}
		// intakes
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.55f, 0.45f, 0.55f, 0.15f, 0.25f, 0.75f);
			glow(vc, pose, s * 0.55f, 0.45f, 1.32f, 0.11f, 0.19f, 0.02f);
		}
		// swept wings, tip cannons
		for (int s = -1; s <= 1; s += 2) {
			{
				pose.pushPose();
				pose.translate(s * 1.75f, 0.45f, -0.45f);
				pose.mulPose(Axis.YP.rotationDegrees(s * 22f));
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 1.45f, 0.05f, 0.75f, GREEN, alpha);
				HardLightDraw.part(vc, pose, 0f, 0.055f, -0.55f, 1.3f, 0.012f, 0.12f, PALE, alpha); // flap line
				pose.popPose();
			}
			p(vc, pose, s * 3.05f, 0.45f, -0.3f, 0.08f, 0.08f, 0.85f);
			glow(vc, pose, s * 3.05f, 0.45f, 0.6f, 0.05f, 0.05f, 0.06f);
			// tailplanes
			p(vc, pose, s * 1.05f, 0.55f, -2.15f, 0.65f, 0.04f, 0.36f);
			// twin fins, canted out
			{
				pose.pushPose();
				pose.translate(s * 0.42f, 1.15f, -2.0f);
				pose.mulPose(Axis.ZP.rotationDegrees(s * -14f));
				HardLightDraw.part(vc, pose, 0f, 0f, 0f, 0.04f, 0.55f, 0.45f, GREEN, alpha);
				pose.translate(s * 0.05f, 0.05f, 0f);
				pose.mulPose(Axis.YP.rotationDegrees(s * 90f));
				HardLightDraw.emblem(vc, pose, 0.45f, Math.min(1f, alpha));
				pose.popPose();
			}
		}
		// the exhaust: brighter and longer the faster it flies, roaring on the afterburner
		float speed = (float) Math.min(1.0, e.speedBps() / GreenLanternConfig.VEHICLE_JET_TOP_BPS);
		float pulse = 0.06f * Mth.sin(Mth.lerp(partial, e.spinO, e.spin) * 3f);
		p(vc, pose, 0f, 0.55f, -2.42f, 0.34f, 0.32f, 0.12f, DEEP);
		glow(vc, pose, 0f, 0.55f, -2.55f, 0.22f + pulse, 0.22f + pulse, 0.05f);
		if (!HardLightDraw.building()) {
			float len = 0.3f + 1.6f * speed + (e.boosting() ? 1.4f : 0f);
			pose.pushPose();
			pose.translate(0f, 0.55f, -2.6f - len / 2f);
			TurboDraw.box(vc, pose.last(), 0.16f, 0.16f, len / 2f, 0xF0FFF4, 0.5f);
			TurboDraw.box(vc, pose.last(), 0.26f + pulse, 0.26f + pulse, len / 2f, HardLightDraw.tint(GREEN), 0.25f);
			pose.popPose();
		}
	}
}
