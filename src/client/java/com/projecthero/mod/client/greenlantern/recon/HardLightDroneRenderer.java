package com.projecthero.mod.client.greenlantern.recon;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.greenlantern.HardLightDraw;
import com.projecthero.mod.client.maxsteel.TurboDraw;
import com.projecthero.mod.greenlantern.recon.GreenLanternReconConfig;
import com.projecthero.mod.greenlantern.recon.HardLightDroneEntity;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.1: the Hard-Light Drone -- a sleek little recon drone of hard light. A teardrop hull (rounded nose, tapering tail
 * with two swept fins and a stabiliser), a big lens "eye" in the nose with the Lantern emblem lit inside it, two rotor
 * rings on short pylons that spin and tilt with the way it flies, a glowing belly emitter, a soft halo round the lot and a
 * fading ribbon of light behind it. It flicks a thin bolt at whatever it zaps, hangs a tractor beam under whatever it
 * carries, and while it hovers on a scout run a scanning ring sweeps the ground under it. Low on HP it flickers.
 * Built facing +Z, turned to the entity's yaw / pitch, units in blocks.
 */
public class HardLightDroneRenderer extends EntityRenderer<HardLightDroneEntity> {
	private static final int GREEN = HardLightDraw.GREEN;
	private static final int PALE = HardLightDraw.PALE;
	private static final int DEEP = HardLightDraw.DEEP;
	private static final int TRAIL = 14;

	/** Per drone: its recent positions (world), newest first, sampled once a tick. */
	private static final Map<HardLightDroneEntity, ArrayDeque<Vec3>> TRAILS = new WeakHashMap<>();
	private static final Map<HardLightDroneEntity, int[]> SAMPLED = new WeakHashMap<>();

	public HardLightDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public boolean shouldRender(HardLightDroneEntity e, Frustum frustum, double x, double y, double z) {
		return true; // the trail, the zap and the scan ring reach outside its little box
	}

	@Override
	public ResourceLocation getTextureLocation(HardLightDroneEntity e) {
		return TurboDraw.WHITE;
	}

	@Override
	public void render(HardLightDroneEntity e, float entityYaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = HardLightDraw.buffer(buffers);
		float age = e.tickCount + partial;
		float grow = Math.min(1f, age / 6f);
		float hpFrac = Mth.clamp(e.hp() / GreenLanternReconConfig.DRONE_HP, 0f, 1f);
		float alpha = 1f;
		if (hpFrac < 0.3f) {
			alpha = 0.55f + 0.45f * (Mth.sin(age * 1.7f) > -0.2f ? 1f : 0.2f); // a failing construct stutters
		}
		HardLightDraw.golden = e.golden();
		Vec3 here = e.getPosition(partial);
		trail(e, vc, pose, here, alpha);
		zap(e, vc, pose, here, age, partial, alpha);
		carry(e, vc, pose, here, age, alpha);
		if (e.mode() == HardLightDroneEntity.SCOUT_HOVER) {
			scanRing(vc, pose, age, alpha);
		}

		pose.pushPose();
		float yaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
		float pitch = Mth.lerp(partial, e.xRotO, e.getXRot());
		Vec3 vel = here.subtract(new Vec3(e.xo, e.yo, e.zo));
		// bank into turns: how far the motion runs across the nose
		float yawRad = yaw * Mth.DEG_TO_RAD;
		double side = vel.x * -Mth.cos(yawRad) + vel.z * -Mth.sin(yawRad);
		float bank = (float) Mth.clamp(side * 120.0, -28.0, 28.0);
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(bank));
		pose.translate(0f, Mth.sin(age * 0.21f) * 0.02f, 0f);
		pose.scale(grow, grow, grow);
		hull(vc, pose, age, alpha);
		lens(vc, pose, age, alpha, e);
		rotors(vc, pose, age, alpha, (float) vel.length());
		halo(vc, pose, age, alpha);
		pose.popPose();
		HardLightDraw.golden = false;
	}

	private static void p(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb, float a) {
		HardLightDraw.part(vc, pose, x, y, z, hx, hy, hz, rgb, a);
	}

	// ---------------------------------------------------------------- the body

	private static void hull(VertexConsumer vc, PoseStack pose, float age, float a) {
		// the teardrop: stacked slices, widest just behind the nose, tapering to the tail
		float[][] slices = { // z, half width, half height
				{ 0.17f, 0.11f, 0.085f }, { 0.11f, 0.15f, 0.105f }, { 0.04f, 0.16f, 0.11f }, { -0.04f, 0.145f, 0.1f },
				{ -0.12f, 0.115f, 0.08f }, { -0.2f, 0.08f, 0.06f }, { -0.27f, 0.05f, 0.04f } };
		for (float[] s : slices) {
			p(vc, pose, 0f, 0f, s[0], s[1], s[2], 0.045f, GREEN, a * 0.55f);
		}
		// a dorsal spine and a belly keel round the silhouette off
		p(vc, pose, 0f, 0.1f, -0.02f, 0.05f, 0.025f, 0.17f, PALE, a * 0.7f);
		p(vc, pose, 0f, -0.1f, 0.0f, 0.06f, 0.02f, 0.13f, DEEP, a * 0.8f);
		// the rounded shell, two boxes turned 45 degrees on the long axis
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(45f));
		p(vc, pose, 0f, 0f, 0f, 0.1f, 0.1f, 0.2f, DEEP, a * 0.4f);
		pose.popPose();
		// swept tail fins and the stabiliser
		for (int s = -1; s <= 1; s += 2) {
			pose.pushPose();
			pose.translate(s * 0.06f, 0.035f, -0.25f);
			pose.mulPose(Axis.ZP.rotationDegrees(s * -38f));
			pose.mulPose(Axis.XP.rotationDegrees(-20f));
			p(vc, pose, 0f, 0.075f, 0f, 0.012f, 0.075f, 0.06f, PALE, a);
			pose.popPose();
		}
		p(vc, pose, 0f, -0.01f, -0.3f, 0.13f, 0.01f, 0.035f, PALE, a * 0.9f);
		// glowing seams along the flanks and the tail light
		for (int s = -1; s <= 1; s += 2) {
			HardLightDraw.glow(vc, pose, s * 0.158f, 0.0f, 0.03f, 0.006f, 0.012f, 0.09f, a * 0.8f);
		}
		float tail = 0.6f + 0.4f * Mth.sin(age * 0.5f);
		HardLightDraw.glow(vc, pose, 0f, 0f, -0.31f, 0.022f, 0.022f, 0.01f, a * tail);
		// belly emitter
		HardLightDraw.glow(vc, pose, 0f, -0.12f, 0.03f, 0.03f, 0.01f, 0.03f, a * (0.7f + 0.3f * Mth.sin(age * 0.3f)));
	}

	/** The big lens in the nose, the Lantern emblem lit inside it, a slow iris pulse. */
	private static void lens(VertexConsumer vc, PoseStack pose, float age, float a, HardLightDroneEntity e) {
		pose.pushPose();
		pose.translate(0f, 0.005f, 0.215f);
		float r = 0.1f;
		int segs = 14;
		for (int i = 0; i < segs; i++) { // the bezel ring
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(i * 360f / segs));
			p(vc, pose, 0f, r, 0f, (float) (Math.PI * r / segs) + 0.006f, 0.014f, 0.018f, PALE, a);
			pose.popPose();
		}
		boolean zapping = e.tickCount - e.zapTick() < 4;
		float pulse = zapping ? 1f : 0.75f + 0.25f * Mth.sin(age * 0.18f);
		// the glass: a dark emerald disc, a bright core, the emblem floating in front of it
		pose.pushPose();
		pose.mulPose(Axis.ZP.rotationDegrees(22.5f));
		TurboDraw.box(vc, pose.last(), r * 0.92f, r * 0.38f, 0.008f, DEEP, 0.85f * a);
		TurboDraw.box(vc, pose.last(), r * 0.38f, r * 0.92f, 0.008f, DEEP, 0.85f * a);
		pose.mulPose(Axis.ZP.rotationDegrees(45f));
		TurboDraw.box(vc, pose.last(), r * 0.92f, r * 0.38f, 0.008f, DEEP, 0.85f * a);
		TurboDraw.box(vc, pose.last(), r * 0.38f, r * 0.92f, 0.008f, DEEP, 0.85f * a);
		pose.popPose();
		TurboDraw.box(vc, pose.last(), r * 0.75f, r * 0.75f, 0.01f, HardLightDraw.tint(GREEN), 0.35f * a * pulse);
		TurboDraw.box(vc, pose.last(), r * 0.42f, r * 0.42f, 0.012f, HardLightDraw.tint(PALE), 0.5f * a * pulse); // the lit iris
		pose.translate(0f, 0f, 0.016f);
		HardLightDraw.emblem(vc, pose, 0.16f, a * pulse);
		if (zapping) {
			TurboDraw.box(vc, pose.last(), r * 1.4f, r * 1.4f, 0.01f, 0xFFFFFF, 0.35f * a);
		}
		pose.popPose();
	}

	/** Two rotor rings on short pylons, spinning; they tip forward when it flies fast. */
	private static void rotors(VertexConsumer vc, PoseStack pose, float age, float a, float speed) {
		float spin = age * 38f;
		float tip = Mth.clamp(speed * 60f, 0f, 22f);
		for (int s = -1; s <= 1; s += 2) {
			p(vc, pose, s * 0.21f, 0.01f, -0.03f, 0.06f, 0.014f, 0.025f, PALE, a); // the pylon
			pose.pushPose();
			pose.translate(s * 0.33f, 0.01f, -0.03f);
			pose.mulPose(Axis.XP.rotationDegrees(tip));
			float rr = 0.115f;
			int segs = 16;
			for (int i = 0; i < segs; i++) { // the ring
				pose.pushPose();
				pose.mulPose(Axis.YP.rotationDegrees(i * 360f / segs));
				p(vc, pose, 0f, 0f, rr, (float) (Math.PI * rr / segs) + 0.008f, 0.016f, 0.014f, GREEN, a);
				pose.popPose();
			}
			// the blur of the blades inside it, and a hub
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(s * spin));
			for (int k = 0; k < 3; k++) {
				pose.mulPose(Axis.YP.rotationDegrees(60f));
				TurboDraw.box(vc, pose.last(), rr * 0.92f, 0.004f, 0.022f, HardLightDraw.tint(PALE), 0.5f * a);
			}
			pose.popPose();
			TurboDraw.box(vc, pose.last(), rr * 0.9f, 0.003f, rr * 0.9f, HardLightDraw.tint(GREEN), 0.12f * a);
			HardLightDraw.glow(vc, pose, 0f, 0f, 0f, 0.022f, 0.022f, 0.022f, a);
			pose.popPose();
		}
	}

	private static void halo(VertexConsumer vc, PoseStack pose, float age, float a) {
		float b = 0.9f + 0.1f * Mth.sin(age * 0.15f);
		TurboDraw.sphere(vc, pose, 0.3f * b, HardLightDraw.tint(GREEN), 0.07f * a);
		TurboDraw.sphere(vc, pose, 0.46f * b, HardLightDraw.tint(GREEN), 0.035f * a);
	}

	// ---------------------------------------------------------------- effects (world-relative)

	/** A ribbon of fading light over its last few positions. */
	private static void trail(HardLightDroneEntity e, VertexConsumer vc, PoseStack pose, Vec3 here, float a) {
		ArrayDeque<Vec3> pts = TRAILS.computeIfAbsent(e, k -> new ArrayDeque<>());
		int[] last = SAMPLED.computeIfAbsent(e, k -> new int[] { -1 });
		if (last[0] != e.tickCount) {
			last[0] = e.tickCount;
			pts.addFirst(e.position());
			while (pts.size() > TRAIL) {
				pts.removeLast();
			}
		}
		Vec3 prev = here;
		int i = 0;
		for (Vec3 pt : pts) {
			i++;
			Vec3 seg = pt.subtract(prev);
			double len = seg.length();
			if (len > 0.02 && len < 4.0) {
				float k = 1f - i / (float) (TRAIL + 1);
				pose.pushPose();
				Vec3 mid = prev.add(seg.scale(0.5)).subtract(here);
				pose.translate(mid.x, mid.y, mid.z);
				face(pose, seg);
				float w = 0.05f * k + 0.01f;
				TurboDraw.box(vc, pose.last(), w, w, (float) len / 2f + 0.01f, HardLightDraw.tint(GREEN), 0.3f * k * a);
				TurboDraw.box(vc, pose.last(), w * 0.4f, w * 0.4f, (float) len / 2f, HardLightDraw.tint(PALE), 0.45f * k * a);
				pose.popPose();
			}
			prev = pt;
		}
	}

	/** The last zap: a thin white-hot bolt from the lens to the target for a few ticks. */
	private static void zap(HardLightDroneEntity e, VertexConsumer vc, PoseStack pose, Vec3 here, float age, float partial, float a) {
		float since = e.tickCount - e.zapTick() + partial;
		if (since > 5f || e.zapTarget() < 0) {
			return;
		}
		Entity t = e.level().getEntity(e.zapTarget());
		if (t == null) {
			return;
		}
		Vec3 to = t.getPosition(partial).add(0, t.getBbHeight() * 0.6, 0).subtract(here);
		float len = (float) to.length();
		if (len < 0.1f) {
			return;
		}
		float k = 1f - since / 5f;
		pose.pushPose();
		face(pose, to);
		HardLightDraw.streak(vc, pose, len, 0.07f, age, k * a);
		pose.translate(0f, 0f, len);
		TurboDraw.orb(vc, pose, 0.2f * (0.6f + 0.6f * (1f - k)), HardLightDraw.tint(GREEN), age * 20f, k * a);
		pose.popPose();
	}

	/** A tractor beam down to whatever it is carrying. */
	private static void carry(HardLightDroneEntity e, VertexConsumer vc, PoseStack pose, Vec3 here, float age, float a) {
		if (e.carryId() < 0) {
			return;
		}
		Entity t = e.level().getEntity(e.carryId());
		if (t == null) {
			return;
		}
		Vec3 to = t.position().add(0, 0.15, 0).subtract(here);
		float len = (float) to.length();
		if (len < 0.05f) {
			return;
		}
		pose.pushPose();
		face(pose, to);
		pose.translate(0f, 0f, len / 2f);
		for (int i = 0; i < 3; i++) {
			float w = 0.03f + i * 0.04f;
			TurboDraw.box(vc, pose.last(), w, w, len / 2f, HardLightDraw.tint(i == 0 ? PALE : GREEN), (0.4f - i * 0.11f) * a);
		}
		pose.popPose();
		pose.pushPose();
		pose.translate(to.x, to.y, to.z);
		pose.mulPose(Axis.YP.rotationDegrees(age * 6f));
		for (int i = 0; i < 8; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 45f));
			pose.translate(0f, 0f, 0.24f);
			TurboDraw.box(vc, pose.last(), 0.05f, 0.01f, 0.015f, HardLightDraw.tint(PALE), 0.6f * a);
			pose.popPose();
		}
		pose.popPose();
	}

	/** Scouting: a ring of light sweeping out over the ground below, and a faint cone down to it. */
	private static void scanRing(VertexConsumer vc, PoseStack pose, float age, float a) {
		float t = (age % 30f) / 30f;
		float r = 0.4f + t * 3.6f;
		float fade = (1f - t) * a;
		pose.pushPose();
		pose.translate(0f, -2.3f, 0f);
		int segs = 28;
		for (int i = 0; i < segs; i++) {
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(i * 360f / segs + age * 2f));
			pose.translate(0f, 0f, r);
			TurboDraw.box(vc, pose.last(), (float) (Math.PI * r / segs) + 0.01f, 0.02f, 0.03f, HardLightDraw.tint(PALE), 0.6f * fade);
			TurboDraw.box(vc, pose.last(), (float) (Math.PI * r / segs) + 0.02f, 0.04f, 0.08f, HardLightDraw.tint(GREEN), 0.2f * fade);
			pose.popPose();
		}
		pose.popPose();
		// the cone: four thin rays from the belly to the rim
		for (int i = 0; i < 4; i++) {
			float ang = (i * 90f + age * 4f) * Mth.DEG_TO_RAD;
			Vec3 to = new Vec3(Mth.sin(ang) * r, -2.3, Mth.cos(ang) * r);
			pose.pushPose();
			face(pose, to);
			pose.translate(0f, 0f, (float) to.length() / 2f);
			TurboDraw.box(vc, pose.last(), 0.008f, 0.008f, (float) to.length() / 2f, HardLightDraw.tint(GREEN), 0.3f * fade);
			pose.popPose();
		}
	}

	/** Turns the pose so local +Z runs along {@code dir}. */
	private static void face(PoseStack pose, Vec3 dir) {
		Vector3f d = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z).normalize();
		pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), d));
	}
}
