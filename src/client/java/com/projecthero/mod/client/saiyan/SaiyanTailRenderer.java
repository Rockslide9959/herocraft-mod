package com.projecthero.mod.client.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanForm;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "Give Saiyans a brown monkey tail that changes colour when they transform"): a furry monkey tail on every
 * Saiyan, for every viewer. {@link #SEGMENTS} tapering segments from the lower back (the body's back face, belt height) that
 * hang back and curl up at the tip, sway gently at rest, stream back when he runs, lean against a strafe and lift as he falls.
 * Crouching wraps it round the waist (eased in and out). The colour follows the form, eased over about a second: brown in Base,
 * gold as a Super Saiyan, silver in Ultra Instinct, red under Kaioken; a transformed tail glows (light 13+).
 *
 * <p>Drawn from {@code SaiyanLayer} (a render layer: never in first person; nobody's own view sees it) in the body part's space,
 * so it follows the body's bend and lean for both the wide and the slim model (their bodies are the same). Fur: each segment is
 * a cel-shaded core plus a soft translucent shell 1.3x its size, with alternating light / dark bands and a fluffy tip.
 * The per-player ease values are client-only floats keyed by UUID (no world or entity is held), dropped on disconnect.
 */
public final class SaiyanTailRenderer {
	private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("projecthero", "textures/entity/saiyan/white.png");
	public static final int SEGMENTS = 7;
	/** Segment length and half-widths, model pixels. */
	private static final float SEG_LEN = 2.15f;
	private static final float R_BASE = 1.05f;
	private static final float R_END = 0.62f;
	/** The tail's colours by form (RGB). */
	public static final int BROWN = 0x6B4423;
	public static final int GOLD = 0xFFCF3A;
	public static final int SILVER = 0xCDD6E2;
	public static final int KAIOKEN = 0xC0352C;

	/** Per player: {r, g, b, crouch wrap 0..1, last age}. */
	private static final Map<UUID, float[]> EASE = new ConcurrentHashMap<>();

	private SaiyanTailRenderer() {
	}

	public static void reset() {
		EASE.clear();
	}

	/** The colour the tail is heading for. */
	public static int targetColour(Player p) {
		if (SaiyanFx.kaioken(p)) {
			return KAIOKEN;
		}
		SaiyanForm f = SaiyanFx.form(p);
		return f == SaiyanForm.ULTRA ? SILVER : f == SaiyanForm.SSJ ? GOLD : BROWN;
	}

	public static boolean shows(Player p) {
		return Saiyan.hasPower(p) && !p.isInvisible() && !p.isSpectator();
	}

	/** From SaiyanLayer. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, float partial,
			float age) {
		if (!shows(player) || !model.body.visible) {
			return;
		}
		int target = targetColour(player);
		float[] e = EASE.computeIfAbsent(player.getUUID(), k -> new float[] { ((target >> 16) & 255) / 255f, ((target >> 8) & 255) / 255f,
				(target & 255) / 255f, player.isCrouching() ? 1f : 0f, age });
		float dt = Mth.clamp(age - e[4], 0f, 10f);
		e[4] = age;
		float kc = 1f - (float) Math.pow(0.9, dt); // colour: ~1 s to settle
		e[0] += (((target >> 16) & 255) / 255f - e[0]) * kc;
		e[1] += (((target >> 8) & 255) / 255f - e[1]) * kc;
		e[2] += ((target & 255) / 255f - e[2]) * kc;
		float kw = 1f - (float) Math.pow(0.78, dt);
		e[3] += ((player.isCrouching() ? 1f : 0f) - e[3]) * kw;
		float wrap = e[3] * e[3] * (3f - 2f * e[3]);
		boolean glows = target != BROWN;
		int lit = glows ? LightTexture.pack(Math.max(LightTexture.block(light), 13), Math.max(LightTexture.sky(light), 13)) : light;

		Vec3[] pts = points(player, partial, age, wrap);
		pose.pushPose();
		model.body.translateAndRotate(pose);
		PoseStack.Pose last = pose.last();
		VertexConsumer core = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
		for (int i = 0; i < SEGMENTS; i++) {
			float band = i % 2 == 0 ? 1f : 0.88f;
			if (i == SEGMENTS - 1) {
				band = 1.08f; // the fluffy tip, a shade lighter
			}
			box(core, last, pts[i], pts[i + 1], radius(i), radius(i + 1), e, band, 255, lit, i == 0, i == SEGMENTS - 1);
		}
		VertexConsumer fur = buffers.getBuffer(RenderType.entityTranslucent(WHITE));
		for (int i = 0; i < SEGMENTS; i++) {
			float s = i == SEGMENTS - 1 ? 1.5f : 1.3f;
			box(fur, last, pts[i], pts[i + 1], radius(i) * s, radius(i + 1) * s, e, 1.1f, 80, lit, i == 0, i == SEGMENTS - 1);
		}
		pose.popPose();
	}

	/** Half-width at joint {@code j} (0 = the root): tapering, the tip a little fuller. */
	static float radius(int j) {
		float t = j / (float) SEGMENTS;
		float r = Mth.lerp(t, R_BASE, R_END);
		return j >= SEGMENTS - 1 ? r * 1.2f : r;
	}

	/**
	 * The {@link #SEGMENTS}+1 joints in body-model pixels (x right, y DOWN, z BACK): the free tail (hang, curl, sway, movement)
	 * blended into the waist wrap by {@code wrap}.
	 */
	static Vec3[] points(Player p, float partial, float age, float wrap) {
		Vec3[] out = new Vec3[SEGMENTS + 1];
		Vec3 root = new Vec3(0, 10.6, 2.05);
		// movement in the body's frame: forward speed, sideways, vertical
		Vec3 v = p.getDeltaMovement();
		float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
		float fwd = (float) (-v.x * Math.sin(yaw) + v.z * Math.cos(yaw));
		float side = (float) (v.x * Math.cos(yaw) + v.z * Math.sin(yaw));
		float run = Mth.clamp(Math.abs(fwd) * 4f, 0f, 1f);
		float fall = Mth.clamp((float) -v.y * 2f, -0.5f, 1f);
		float t = age;
		// pitch: angle below the horizontal (positive = down, as y is down); yaw: to the right (+x)
		float pitch = (38f - 26f * run - 20f * fall) * Mth.DEG_TO_RAD;
		float yawA = Mth.clamp(-side * 60f, -25f, 25f) * Mth.DEG_TO_RAD;
		float amp = (9f + 5f * Mth.sin(t * 0.031f)) * (1f - 0.5f * run);
		Vec3 at = root;
		out[0] = root;
		for (int i = 0; i < SEGMENTS; i++) {
			float k = i / (float) (SEGMENTS - 1);
			// the curl: each joint bends up, more toward the tip; a running tail curls less
			pitch -= (10f + 9f * k) * (1f - 0.55f * run) * Mth.DEG_TO_RAD;
			pitch += Mth.sin(t * 0.09f - i * 0.55f) * 3.0f * Mth.DEG_TO_RAD;
			yawA += Mth.sin(t * 0.11f - i * 0.7f) * amp * (0.35f + 0.65f * k) * Mth.DEG_TO_RAD / 2.2f;
			Vec3 d = new Vec3(Mth.sin(yawA) * Mth.cos(pitch), Mth.sin(pitch), Mth.cos(yawA) * Mth.cos(pitch));
			at = at.add(d.scale(SEG_LEN));
			out[i + 1] = at;
		}
		if (wrap > 0.001f) {
			// crouched: round the waist from the back, to his right, round the front and back along the left (ellipse outside the body)
			double rx = 5.0, rz = 3.0, y = 11.2;
			double span = Math.toRadians(320.0);
			for (int i = 0; i <= SEGMENTS; i++) {
				double phi = Math.PI / 2 - span * i / SEGMENTS + Math.sin(t * 0.05) * 0.03 * i;
				Vec3 w = new Vec3(Math.cos(phi) * rx, y + (i == 0 ? -0.4 : 0.0) + i * 0.08, Math.sin(phi) * rz);
				if (i == 0) {
					w = new Vec3(0, 10.6, 2.05).lerp(w, 0.5);
				}
				out[i] = out[i].lerp(w, wrap);
			}
		}
		return out;
	}

	/** A tapered box from {@code a} to {@code b} (pixels), slightly overlapping its neighbours so the bends have no gaps. */
	private static void box(VertexConsumer vc, PoseStack.Pose last, Vec3 a, Vec3 b, float ra, float rb, float[] col, float band, int alpha,
			int lit, boolean capStart, boolean capEnd) {
		Vec3 d = b.subtract(a);
		double len = d.length();
		if (len < 1.0e-4) {
			return;
		}
		Vec3 dir = d.scale(1.0 / len);
		a = a.subtract(dir.scale(0.3));
		b = b.add(dir.scale(0.3));
		Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, -1, 0);
		Vec3 u = dir.cross(ref).normalize();
		Vec3 w = dir.cross(u).normalize();
		Vec3[] ca = corners(a, u, w, ra);
		Vec3[] cb = corners(b, u, w, rb);
		for (int f = 0; f < 4; f++) {
			int g = (f + 1) % 4;
			Vec3 n = ca[f].add(ca[g]).scale(0.5).subtract(a).normalize();
			quad(vc, last, ca[f], ca[g], cb[g], cb[f], n, col, band, alpha, lit);
		}
		if (capStart) {
			quad(vc, last, ca[3], ca[2], ca[1], ca[0], dir.scale(-1), col, band, alpha, lit);
		}
		if (capEnd) {
			quad(vc, last, cb[0], cb[1], cb[2], cb[3], dir, col, band, alpha, lit);
		}
	}

	private static Vec3[] corners(Vec3 c, Vec3 u, Vec3 w, float r) {
		return new Vec3[] { c.add(u.scale(r)).add(w.scale(r)), c.add(u.scale(-r)).add(w.scale(r)), c.add(u.scale(-r)).add(w.scale(-r)),
				c.add(u.scale(r)).add(w.scale(-r)) };
	}

	private static void quad(VertexConsumer vc, PoseStack.Pose last, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, Vec3 n, float[] col, float band,
			int alpha, int lit) {
		// baked cel shading: faces turned up (model -y) bright, down dark
		float shade = Mth.clamp((float) (0.8 - 0.25 * n.y), 0.55f, 1.05f) * band;
		int r = Mth.clamp((int) (col[0] * shade * 255f), 0, 255);
		int g = Mth.clamp((int) (col[1] * shade * 255f), 0, 255);
		int bl = Mth.clamp((int) (col[2] * shade * 255f), 0, 255);
		Vector3f nn = new Vector3f(0f, -1f, 0f);
		for (Vec3 p : new Vec3[] { p0, p1, p2, p3 }) {
			vc.addVertex(last.pose(), (float) p.x / 16f, (float) p.y / 16f, (float) p.z / 16f).setColor(r, g, bl, alpha).setUv(0.5f, 0.5f)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(last, nn.x, nn.y, nn.z);
		}
	}
}
