package com.projecthero.mod.client.falcon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import org.joml.Matrix4f;

/**
 * v0.17.1: the Falcon's hand-built 3D parts, all box geometry on a plain white texture tinted per box: the EXO-7 Wing Pack
 * (the pack, two jet nozzles, and two steel wings of a spar and six feathers each -- folded down the back on the ground,
 * spread wide in flight) and the Redwing drone. Units are model pixels (1/16 block) in the body part's space (+y down, the
 * back is +z, the wearer's left is +x).
 */
public final class FalconGeometry {
	public static final ResourceLocation WHITE = ProjectHeroMod.id("textures/entity/falcon/white.png");

	private static final int PACK = 0xFF2B2F37;
	private static final int PACK_RED = 0xFFB8142E;
	private static final int SPAR = 0xFF3C424C;
	private static final int FEATHER = 0xFFB9C1CD;
	private static final int FEATHER_LIGHT = 0xFFDCE1E8;
	private static final int FEATHER_EDGE = 0xFFF4F6F9;
	private static final int FEATHER_TIP = 0xFF8E97A5;
	private static final int NOZZLE = 0xFF1A1C21;
	private static final int NOZZLE_HOT = 0xFFFF9A3A;

	private static final int FEATHERS = 6;
	private static final float SPAR_LEN = 15f;

	private FalconGeometry() {
	}

	// ---------------------------------------------------------------- boxes

	/** An axis-aligned box from (x0, y0, z0) to (x1, y1, z1), every face {@code argb}. */
	public static void box(VertexConsumer vc, PoseStack.Pose pose, int light, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
		Matrix4f m = pose.pose();
		face(vc, m, pose, light, argb, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0f, 0f, -1f);
		face(vc, m, pose, light, argb, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0f, 0f, 1f);
		face(vc, m, pose, light, argb, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1f, 0f, 0f);
		face(vc, m, pose, light, argb, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1f, 0f, 0f);
		face(vc, m, pose, light, argb, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0f, -1f, 0f);
		face(vc, m, pose, light, argb, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f);
	}

	private static void face(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, int light, int argb, float ax, float ay, float az, float bx,
			float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
		vc.addVertex(m, ax, ay, az).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, bx, by, bz).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, cx, cy, cz).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, dx, dy, dz).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	public static VertexConsumer solid(MultiBufferSource buffers) {
		return buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
	}

	// ---------------------------------------------------------------- the Wing Pack

	/**
	 * The pack and both wings, in the body part's space (already {@code body.translateAndRotate}d, in blocks). {@code spread}
	 * 0 = folded down the back, 1 = spread for flight; {@code wrap} 0..1 swings them round in front (the Wing Shield); {@code flap} -1..1 sweeps the spread wings; {@code jets} 0..1 the jet
	 * glow and flame (boosting).
	 */
	public static void wingPack(PoseStack pose, MultiBufferSource buffers, int light, float spread, float wrap, float flap, float jets, float age) {
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		VertexConsumer vc = solid(buffers);
		PoseStack.Pose last = pose.last();
		// the pack: a slim armoured backplate with a red spine
		box(vc, last, light, -3.5f, 0.5f, 2.0f, 3.5f, 9.5f, 3.6f, PACK);
		box(vc, last, light, -0.8f, 1.0f, 3.6f, 0.8f, 9.0f, 3.9f, PACK_RED);
		box(vc, last, light, -3.0f, 0.2f, 2.2f, 3.0f, 1.2f, 3.4f, SPAR);
		// two jet nozzles under it
		int hot = jets > 0.05f ? NOZZLE_HOT : NOZZLE;
		int nozzleLight = jets > 0.05f ? LightTexture.FULL_BRIGHT : light;
		for (int k = -1; k <= 1; k += 2) {
			float cx = 2.0f * k;
			box(vc, last, light, cx - 1.0f, 9.5f, 2.2f, cx + 1.0f, 11.0f, 3.6f, NOZZLE);
			box(solid(buffers), last, nozzleLight, cx - 0.6f, 11.0f, 2.5f, cx + 0.6f, 11.3f, 3.3f, hot);
		}
		// the wings: the left one built along +x, the right one its mirror image
		for (int k = -1; k <= 1; k += 2) {
			pose.pushPose();
			pose.translate(3.0f * k, 1.5f, 3.2f);
			if (k < 0) {
				pose.scale(-1f, 1f, 1f);
			}
			wing(pose, buffers, light, spread, wrap, flap);
			pose.popPose();
		}
		if (jets > 0.05f) {
			for (int k = -1; k <= 1; k += 2) {
				jet(pose, buffers, 2.0f * k, 11.2f, 2.9f, 1.1f, 6f + 10f * jets, age + k * 3f);
			}
		}
		pose.popPose();
	}

	/** One wing (the left), its root at the origin: a spar and six feathers. */
	private static void wing(PoseStack pose, MultiBufferSource buffers, int light, float spread, float wrap, float flap) {
		// folded: the spar runs down and a little out along the back, the feathers hang straight down beside it
		float foldAngle = 72f;
		float spreadAngle = -12f + 18f * flap;
		float a = Mth.lerp(spread, foldAngle, spreadAngle);
		float lift = Mth.lerp(spread, 8f, -6f); // folded wings tuck in against the back; spread ones tilt a little up
		float len = Mth.lerp(spread, 0.6f, 1f) * SPAR_LEN;
		pose.mulPose(Axis.YP.rotationDegrees(lift + 95f * wrap)); // the Wing Shield wraps them round in front
		pose.mulPose(Axis.ZP.rotationDegrees(a));
		VertexConsumer vc = solid(buffers);
		PoseStack.Pose last = pose.last();
		box(vc, last, light, 0f, -1.0f, -0.6f, len, 0.9f, 0.6f, SPAR);
		box(vc, last, light, len - 1.0f, -1.3f, -0.7f, len + 1.2f, 0.6f, 0.7f, FEATHER_TIP);
		for (int i = 0; i < FEATHERS; i++) {
			float x = len * (i + 0.4f) / FEATHERS;
			// the feathers lengthen toward the tip, and fold to hang down when the wing is folded
			float fl = Mth.lerp(spread, 0.5f, 1f) * (11f + 1.8f * i);
			pose.pushPose();
			pose.translate(x, 0.4f, 0f);
			pose.mulPose(Axis.ZP.rotationDegrees(-a * (1f - spread) + (8f - 3f * i) * spread));
			// spread: the trailing feathers tilt up out of the back, so the wing reads from behind and above
			pose.mulPose(Axis.XP.rotationDegrees(32f * spread));
			PoseStack.Pose fp = pose.last();
			int base = (i & 1) == 0 ? FEATHER : FEATHER_LIGHT;
			box(vc, fp, light, -1.6f, 0f, -0.25f - 0.05f * i, 1.6f, fl, 0.25f - 0.05f * i, base);
			box(vc, fp, light, 1.3f, 0.5f, -0.2f - 0.05f * i, 1.7f, fl - 0.5f, 0.2f - 0.05f * i, FEATHER_EDGE);
			box(vc, fp, light, -1.1f, fl, -0.2f - 0.05f * i, 1.1f, fl + 0.8f, 0.2f - 0.05f * i, FEATHER_TIP);
			pose.popPose();
		}
	}

	/** A jet flame out of point ({@code x}, {@code y}, {@code z}) along +y: crossed tapering sheets, orange round a pale core. */
	private static void jet(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float width, float length, float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		Matrix4f m = pose.last().pose();
		float flick = 0.85f + 0.15f * Mth.sin(age * 4.3f + x);
		for (int layer = 0; layer < 2; layer++) {
			float lw = layer == 0 ? width : width * 0.45f;
			float len = (layer == 0 ? length : length * 0.6f) * flick;
			float r = 1f, g = layer == 0 ? 0.55f : 0.92f, b = layer == 0 ? 0.15f : 0.7f;
			float al = layer == 0 ? 0.6f : 0.9f;
			for (int k = 0; k < 2; k++) {
				float ox = k == 0 ? lw : 0f, oz = k == 0 ? 0f : lw;
				sheet(vc, m, x - ox, y, z - oz, x + ox, y, z + oz, x, y + len, z, r, g, b, al);
				sheet(vc, m, x + ox, y, z + oz, x - ox, y, z - oz, x, y + len, z, r, g, b, al);
			}
		}
	}

	private static void sheet(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float tx, float ty, float tz,
			float r, float g, float b, float a) {
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
	}

	// ---------------------------------------------------------------- Redwing

	/** The Redwing drone centred on the origin, nose along +z, upright (y up), in blocks. {@code attack}: its lights burn. */
	public static void redwing(PoseStack pose, MultiBufferSource buffers, int light, float age, boolean attack) {
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f); // model pixels, y up (entity space)
		VertexConsumer vc = solid(buffers);
		PoseStack.Pose last = pose.last();
		int red = 0xFFC8102E, dark = 0xFF1E2026, steel = 0xFF8C95A3;
		// the body: a sleek bird -- a dark fuselage with a red back, a beak-like nose and a tail
		box(vc, last, light, -1.5f, -1.2f, -4f, 1.5f, 1.0f, 3.5f, dark);
		box(vc, last, light, -1.2f, 1.0f, -3.5f, 1.2f, 1.6f, 3.0f, red);
		box(vc, last, light, -0.8f, -0.6f, 3.5f, 0.8f, 0.6f, 5.5f, steel);
		box(vc, last, light, -2.0f, -0.3f, -6.5f, 2.0f, 0.3f, -4f, red);
		// the wings, swept back, flexing a little
		float flex = 6f * Mth.sin(age * 0.6f);
		for (int k = -1; k <= 1; k += 2) {
			pose.pushPose();
			pose.translate(1.4f * k, 0.6f, 0.5f);
			if (k < 0) {
				pose.scale(-1f, 1f, 1f);
			}
			pose.mulPose(Axis.ZP.rotationDegrees(flex));
			pose.mulPose(Axis.YP.rotationDegrees(18f)); // swept back (the nose is +z)
			PoseStack.Pose wp = pose.last();
			box(vc, wp, light, 0f, -0.3f, -1.5f, 8.5f, 0.3f, 1.5f, red);
			box(vc, wp, light, 0f, -0.35f, 1.5f, 7.5f, 0.25f, 2.2f, dark);
			box(vc, wp, light, 8.5f, -0.3f, -1.2f, 9.5f, 0.3f, 1.0f, steel);
			pose.popPose();
		}
		// the eye / laser lens and the running light
		int lens = attack ? 0xFFFF2A2A : 0xFFFF7070;
		box(vc, last, LightTexture.FULL_BRIGHT, -0.6f, -0.4f, 5.5f, 0.6f, 0.4f, 6.0f, lens);
		boolean blink = attack || ((int) age / 10) % 2 == 0;
		box(vc, last, blink ? LightTexture.FULL_BRIGHT : light, -0.4f, 1.6f, 0f, 0.4f, 2.0f, 0.8f, blink ? 0xFFFF3030 : 0xFF601010);
		pose.popPose();
	}
}
