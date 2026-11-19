package com.projecthero.mod.client.symbiote;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteVitals;
import com.projecthero.mod.symbiote.SymbioteWings;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "fly for a short amount of time by creating giant symbiote wings"): the Symbiote Wings, drawn for every viewer from the
 * synced {@link SymbioteVitals#wingsStart} / {@link SymbioteVitals#wingsLanded} ({@link SymbioteWings}), in the body bone's frame (so a
 * crouch or a lean carries them), mounted outside a worn backpack ({@code BackpackLayer.backClearance}).
 *
 * <p>Bat-like, after the user's references: from a root between the shoulder blades a tendril arm (upper arm, forearm) reaches out to
 * a wrist, from which four long tendril "finger" bones fan out -- up-out, out, down-out, down -- plus the trailing line back to the hip;
 * a web-veined membrane ({@code symbiote_wing_membrane.png}: pale fill tinted in the suit colours, dark web, a ragged scalloped
 * trailing edge and a few tears) stretches between them, billowing back. Small spikes along the leading edge, a claw at the top finger.
 * Span ~4.6 blocks at scale 1 (about 2.5x the host's height).
 *
 * <p>Animation: <b>growing</b> ({@link SymbioteWings#GROW_TICKS}) -- the bones reach out from the root, then the membrane fills in from
 * the wrist outward; <b>flying</b> -- wing beats every 10 ticks (a deep first beat), fingers spreading on the down-stroke and folding on
 * the up-stroke; <b>gliding</b> -- spread with a slight dihedral and a flutter; <b>dissolving</b> ({@link SymbioteWings#DISSOLVE_TICKS})
 * -- the membrane melts back to the bones, then the bones sink back into the back.
 */
public final class SymbioteWingsRenderer {
	static final ResourceLocation MEMBRANE = ProjectHeroMod.id("textures/entity/symbiote_wing_membrane.png");

	private SymbioteWingsRenderer() {
	}

	public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
		public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
				float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			if (player.isInvisible()) {
				return;
			}
			SymbioteVitals v = player.getAttachedOrElse(ModAttachments.SYMBIOTE_VITALS, null);
			if (v == null || v.wingsStart <= 0L) {
				return;
			}
			long now = player.level().getGameTime();
			SymbioteWings.Phase phase = SymbioteWings.phase(v, now);
			if (phase == SymbioteWings.Phase.NONE) {
				return;
			}
			pose.pushPose();
			getParentModel().body.translateAndRotate(pose);
			float out = com.projecthero.mod.client.backpack.BackpackLayer.backClearance(player, 2.0f) / 16f;
			draw(pose, buffers, light, player, v, phase, now, partialTick, out);
			pose.popPose();
		}
	}

	static void draw(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, SymbioteVitals v,
			SymbioteWings.Phase phase, long now, float partial, float backOut) {
		float since = (now - v.wingsStart) + partial;
		float bones; // 0..1 how far the bones have reached out
		float fill;  // 0..1 how far the membrane has filled in from the wrist
		float flap;  // radians, + lifts the tips
		float spread; // 1 = fingers fully spread, < 1 folded toward the wrist
		if (phase == SymbioteWings.Phase.DISSOLVING) {
			float d = Mth.clamp(((now - v.wingsLanded) + partial) / SymbioteWings.DISSOLVE_TICKS, 0f, 1f);
			fill = Mth.clamp(1f - d * 1.7f, 0f, 1f);
			bones = Mth.clamp(1f - (d - 0.35f) / 0.65f, 0f, 1f);
			flap = 0.35f * d;
			spread = 1f - 0.5f * d;
		} else {
			float g = Mth.clamp(since / SymbioteWings.GROW_TICKS, 0f, 1f);
			bones = Mth.clamp(g / 0.6f, 0f, 1f);
			fill = Mth.clamp((g - 0.35f) / 0.65f, 0f, 1f);
			if (phase == SymbioteWings.Phase.FLYING) {
				float beat = since / 10f;
				float deep = since < 12f ? 1.5f : 1f; // the strong flap on take-off
				float s = (float) Math.sin(beat * Math.PI * 2);
				flap = 0.12f + 0.55f * deep * s;
				spread = 0.82f + 0.18f * (-s);
			} else {
				flap = 0.2f + 0.04f * (float) Math.sin(since * 0.9f);
				spread = 1f;
			}
		}
		if (bones <= 0.001f) {
			return;
		}
		SymbioteColours col = SymbioteSuitTint.colours(player);
		int membraneRgb = shade(mix(col.primary(), col.secondary(), 0.3f), 0.78f);
		VertexConsumer bone = buffers.getBuffer(RenderType.entityCutoutNoCull(SymbioteSuitTint.goo(player, SymbioteWeaponRenderer.TEXTURE)));
		VertexConsumer mem = buffers.getBuffer(RenderType.entityCutoutNoCull(MEMBRANE));
		VertexConsumer edge = buffers.getBuffer(RenderType.entityCutoutNoCull(SymbioteWeaponRenderer.EDGE));
		int edgeRgb = mix(col.primary(), col.secondary(), 0.6f);
		for (int sgn = -1; sgn <= 1; sgn += 2) {
			wing(pose.last(), bone, mem, edge, light, sgn, bones, fill, flap, spread, backOut, membraneRgb, edgeRgb, since);
		}
	}

	/** The finger bones' directions from the wrist (x outward, y down) and lengths. */
	private static final double[][] FINGERS = { { 0.55, -0.8, 1.0 }, { 0.97, -0.18, 1.32 }, { 0.86, 0.52, 1.24 }, { 0.42, 0.92, 1.1 } };

	private static void wing(PoseStack.Pose pose, VertexConsumer bone, VertexConsumer mem, VertexConsumer edge, int light, int sgn,
			float bones, float fill, float flap, float spread, float backOut, int membraneRgb, int edgeRgb, float time) {
		double grow = 0.12 + 0.88 * bones;
		Vec3 root = new Vec3(sgn * 0.1, 0.2, 0.17 + backOut);
		Vec3 hip = new Vec3(sgn * 0.14, 0.72, 0.17 + backOut);
		// the wing frame, before the flap: elbow and wrist
		Vec3 elbow = local(root, sgn, 0.5, -0.42, 0.24, grow);
		Vec3 wrist = local(root, sgn, 1.22, -0.58, 0.32, grow);
		Vec3[] tips = new Vec3[FINGERS.length];
		Vec3[] mids = new Vec3[FINGERS.length];
		for (int k = 0; k < FINGERS.length; k++) {
			double[] f = FINGERS[k];
			// folding pulls the lower fingers in toward the top one
			double fold = (1.0 - spread) * (0.25 + 0.25 * k);
			double dx = f[0] * (1 - fold * 0.5), dy = f[1] - fold;
			double len = f[2] * grow * (0.92 + 0.08 * spread);
			Vec3 dir = new Vec3(sgn * dx, dy, 0.18).normalize();
			tips[k] = wrist.add(dir.scale(len)).add(0, 0, 0.12 * grow);
			mids[k] = wrist.add(dir.scale(len * 0.5)).add(0, 0, 0.2 * grow);
		}
		// flap: rotate everything about the root (axis = the body's Z), + lifts the tips on both sides
		double a = flap * sgn;
		elbow = flap(elbow, root, a);
		wrist = flap(wrist, root, a);
		for (int k = 0; k < tips.length; k++) {
			tips[k] = flap(tips[k], root, a);
			mids[k] = flap(mids[k], root, a * 1.06);
		}
		// bones: upper arm and forearm (thick), the fingers (tapering), the hip line
		GooMesh.tube(bone, pose, light, new Vec3[]{root, root.lerp(elbow, 0.5).add(0, -0.03, 0), elbow},
				new float[]{0.07f, 0.06f, 0.055f}, 6, 0.3f);
		GooMesh.tube(bone, pose, light, new Vec3[]{elbow, elbow.lerp(wrist, 0.5).add(0, -0.02, 0), wrist},
				new float[]{0.055f, 0.048f, 0.05f}, 6, 0.3f);
		for (int k = 0; k < tips.length; k++) {
			Vec3[] pts = curve(wrist, mids[k], tips[k], 6);
			float[] r = new float[pts.length];
			for (int i = 0; i < r.length; i++) {
				r[i] = Mth.lerp((float) i / (r.length - 1), 0.04f, 0.01f);
			}
			GooMesh.tube(bone, pose, light, pts, r, 5, 0.3f);
		}
		// the claw at the top finger and spikes along the leading edge
		Vec3 t0 = tips[0];
		Vec3 clawDir = t0.subtract(mids[0]).normalize();
		cone(bone, edge, pose, light, edgeRgb, t0, clawDir.add(0, 0.6, -0.3), 0.14 * grow, 0.025f);
		for (int i = 1; i <= 5; i++) {
			double t = i / 6.0;
			Vec3 on = i <= 2 ? root.lerp(elbow, t * 3) : elbow.lerp(wrist, (t - 0.33) * 1.5);
			cone(bone, edge, pose, light, edgeRgb, on, new Vec3(sgn * 0.3, -1, -0.15), (0.06 + 0.03 * Math.sin(i * 2.1)) * grow, 0.02f);
		}
		if (fill <= 0.001f) {
			return;
		}
		// membrane panels, radial from the wrist: f1-f2, f2-f3, f3-f4, f4-hip, hip-arm
		Vec3[][] lines = new Vec3[6][];
		for (int k = 0; k < 4; k++) {
			lines[k] = curve(wrist, mids[k], tips[k], 9);
		}
		Vec3 hipF = flap(hip, root, a * 0.15);
		lines[4] = curve(wrist, wrist.lerp(hipF, 0.5).add(0, 0, 0.14), hipF, 9);
		lines[5] = curve(wrist, elbow, root, 9);
		for (int k = 0; k < 5; k++) {
			panel(mem, pose, light, membraneRgb, lines[k], lines[k + 1], fill, time + k * 7, k == 4);
		}
	}

	/** A point in the wing frame at (out, down, back) from the root, scaled by the growth. */
	private static Vec3 local(Vec3 root, int sgn, double out, double down, double back, double grow) {
		return root.add(new Vec3(sgn * out, down, back).scale(grow));
	}

	private static Vec3 flap(Vec3 p, Vec3 root, double a) {
		double x = p.x - root.x, y = p.y - root.y;
		double c = Math.cos(a), s = Math.sin(a);
		return new Vec3(root.x + x * c + y * s, root.y - x * s + y * c, p.z);
	}

	/** A quadratic curve from a through (near) m to b, {@code n} points. */
	private static Vec3[] curve(Vec3 a, Vec3 m, Vec3 b, int n) {
		Vec3[] out = new Vec3[n];
		Vec3 ctrl = m.scale(2).subtract(a.add(b).scale(0.5));
		for (int i = 0; i < n; i++) {
			double t = (double) i / (n - 1);
			double u = 1 - t;
			out[i] = a.scale(u * u).add(ctrl.scale(2 * u * t)).add(b.scale(t * t));
		}
		return out;
	}

	private static void cone(VertexConsumer bone, VertexConsumer edge, PoseStack.Pose pose, int light, int edgeRgb, Vec3 base, Vec3 dir,
			double len, float r) {
		if (len <= 0.005) {
			return;
		}
		Vec3 d = dir.normalize();
		Vec3 mid = base.add(d.scale(len * 0.6));
		GooMesh.tube(bone, pose, light, new Vec3[]{base, mid}, new float[]{r, r * 0.45f}, 5, 0f);
		GooMesh.tube(edge, pose, light, edgeRgb, new Vec3[]{mid, base.add(d.scale(len))}, new float[]{r * 0.45f, 0.002f}, 5, 0f);
	}

	/**
	 * One membrane panel between two bone lines that start together at the wrist (v along them, u across), filled out to
	 * {@code fill}, billowing back between the bones and rippling a little.
	 */
	private static void panel(VertexConsumer vc, PoseStack.Pose pose, int light, int rgb, Vec3[] a, Vec3[] b, float fill, float time,
			boolean inner) {
		int nv = a.length - 1;
		int nu = 6;
		int rows = Math.max(1, (int) Math.ceil(nv * fill));
		Vec3[][] g = new Vec3[rows + 1][nu + 1];
		for (int i = 0; i <= rows; i++) {
			double v = Math.min(fill, (double) i / nv) * nv;
			int i0 = Math.min(nv - 1, (int) Math.floor(v));
			double fv = v - i0;
			Vec3 pa = a[i0].lerp(a[i0 + 1], fv);
			Vec3 pb = b[i0].lerp(b[i0 + 1], fv);
			double vv = v / nv;
			for (int j = 0; j <= nu; j++) {
				double u = (double) j / nu;
				double billow = 0.11 * Math.sin(Math.PI * u) * vv * (inner ? 0.6 : 1.0);
				double ripple = 0.015 * Math.sin(time * 0.7 + u * 6 + vv * 8) * vv;
				g[i][j] = pa.lerp(pb, u).add(0, ripple, billow);
			}
		}
		for (int i = 0; i < rows; i++) {
			float v0 = (float) Math.min(fill, (double) i / nv), v1 = (float) Math.min(fill, (double) (i + 1) / nv);
			for (int j = 0; j < nu; j++) {
				float u0 = (float) j / nu, u1 = (float) (j + 1) / nu;
				GooMesh.quad(vc, pose, light, rgb, g[i][j], g[i][j + 1], g[i + 1][j + 1], g[i + 1][j], u0, v0, u1, v1);
			}
		}
	}

	private static int mix(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static int shade(int c, float f) {
		return mix(0, c, f);
	}
}
