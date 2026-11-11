package com.projecthero.mod.client.omnitrix.sequence;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.omnitrix.OmnitrixFxRenderType;
import com.projecthero.mod.client.omnitrix.OmnitrixSequenceClient;
import com.projecthero.mod.client.omnitrix.OmnitrixTransformFx;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the beats every Omnitrix transformation in the show shares, ready to drop into a sequence (moved out of the v0.17.2 Four
 * Arms code, unchanged): the slap shot and the dial close-up camera keys, the green flash off the slap, the dial's bloom and light
 * streaks, the golden / white flashes, the whip pan's streaks, and the green flash back out into the world. All {@code t} are ticks
 * since the slap.
 */
public final class ShowShots {
	/** The show's flat transformation green. */
	public static final int FLASH_GREEN = 0x40DC14;
	public static final int GOLD_IN = 0xFFF8DA, GOLD_OUT = 0xF4BC2E;

	private ShowShots() {
	}

	// ================================================================ camera keys

	/** The slap: a front medium shot of the raised watch, pushing in a little ({@link CameraTrack} keys). */
	public static float[][] slapShot(float from, float to) {
		return new float[][] { { from, 1.9f, 12f, 1.35f, 1.15f, 0f, 0.15f }, { to, 1.55f, 10f, 1.32f, 1.15f, 0f, 0.15f } };
	}

	/** The dial close-up: from above and in front of the watch face, pushing in. Starts with a cut ({@code from} = the last key + 0.01). */
	public static float[][] dialCloseUp(float from, float to) {
		float w = CameraTrack.W;
		return new float[][] { { from, 0.46f, 18f, w, w, w, w }, { to, 0.32f, 8f, w, w, w, w } };
	}

	// ================================================================ the screen (Stage)

	/** A flat full-screen flash of {@code rgb}: up from {@code a} over {@code in}, held to {@code b}, down over {@code out}. */
	public static void flash(Stage st, float t, int rgb, float a, float in, float b, float out, float maxAlpha) {
		float e = ShowMath.env(t, a, in, b, out);
		if (e > 0f) {
			st.fill(rgb, maxAlpha * e);
		}
	}

	/** The slap's flash from {@code at}: white-green going flat green, then thinning into the close-up's bloom (about 18 ticks). */
	public static void slapFlash(Stage st, float t, float at) {
		float g1 = ShowMath.env(t, at, 1.5f, at + 7.5f, 7f);
		if (g1 > 0f) {
			st.fill(ShowMath.lerpRgb(ShowMath.smooth((t - at) / 3f), 0xD2FFB4, FLASH_GREEN), g1);
		}
	}

	/** The close-up's green bloom round the dial, {@code from} to {@code to}. */
	public static void dialBloom(Stage st, float t, float from, float to) {
		float bloom = ShowMath.env(t, from, 3f, to - 4f, 4f);
		if (bloom > 0f) {
			st.radial(0xEFFFD8, 0x6CE840, 0x2FB81C, 0.6f * bloom, 0.75f * bloom);
		}
	}

	/** The golden flash the body changes under: a hot white-gold centre going out to amber, {@code from} to {@code to}. */
	public static void goldFlash(Stage st, float t, float from, float to) {
		float gold = ShowMath.env(t, from, 4f, to, 6f);
		if (gold > 0f) {
			st.radial(GOLD_IN, 0xFFE27A, GOLD_OUT, gold, gold);
		}
	}

	/** A whip pan: a white blur and thick black / white streaks racing across, {@code from} to {@code to}. */
	public static void whipStreaks(Stage st, float t, float from, float to) {
		float whip = ShowMath.env(t, from, 1.5f, to - 2.5f, 2.5f);
		if (whip > 0f) {
			VertexConsumer vc = st.flat();
			PoseStack.Pose p = st.last();
			st.fill(vc, p, 0xF2F4F0, 0.25f * whip);
			long seed = st.seed;
			for (int i = 0; i < 14; i++) {
				float y = (ShowMath.hash(seed, i + 300) * 2f - 1f) * 0.95f;
				float h = 0.012f + 0.03f * ShowMath.hash(seed, i + 320);
				float len = 0.5f + 0.9f * ShowMath.hash(seed, i + 340);
				float x0 = 1.4f - ((t - from) * (0.45f + 0.3f * ShowMath.hash(seed, i + 360)) + ShowMath.hash(seed, i + 380) * 2f) % 3.6f;
				int col = i % 3 == 0 ? 0xFFFFFF : 0x060606;
				st.rect(vc, p, x0, y - h, x0 + len, y + h, col, whip * 0.9f, 0f);
			}
		}
	}

	/**
	 * The green flash out of the stage at {@code greenAt}, full by {@code worldAt} (the camera is back in the world), then the green
	 * fading off the world by {@code total}.
	 */
	public static void greenOut(Stage st, float t, float greenAt, float worldAt, float total) {
		float g2 = ShowMath.env(t, greenAt, 3.5f, worldAt, 0.01f);
		if (g2 > 0f) {
			st.fill(FLASH_GREEN, g2);
		}
		if (t >= worldAt) {
			float fade = 1f - ShowMath.smooth((t - worldAt) / (total - worldAt));
			st.fill(ShowMath.lerpRgb(1f - fade, FLASH_GREEN, 0x9CF060), 0.75f * fade);
		}
	}

	// ================================================================ in the world (everyone sees these)

	/**
	 * The dial bursting with light, {@code from} to {@code to} (fading over 8 more ticks): additive glows on the watch and thin light
	 * streaks turning slowly round it.
	 */
	public static void dialLight(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, long seed, float from, float to) {
		Vec3 cp = cam.getPosition();
		Vector3f left = new Vector3f(cam.getLeftVector()), up = new Vector3f(cam.getUpVector());
		VertexConsumer glow = buffers.getBuffer(RenderType.eyes(OmnitrixTransformFx.GLOW));
		PoseStack.Pose last = pose.last();
		float dial = ShowMath.env(t, from, 2f, to, 8f);
		Vec3 w = OmnitrixSequenceClient.wrist(p);
		if (dial > 0f && w != null) {
			Vec3 at = w.subtract(cp);
			float pulse = 1f + 0.12f * Mth.sin(t * 1.9f);
			glowQuad(glow, last, at, 0.75f * pulse, ShowMath.scaleRgb(0x48E83E, dial * 0.85f), left, up);
			glowQuad(glow, last, at, 0.28f * pulse, ShowMath.scaleRgb(0xE2FFD2, dial), left, up);
			VertexConsumer flat = buffers.getBuffer(OmnitrixFxRenderType.FLAT);
			for (int i = 0; i < 9; i++) {
				float ang = (i + ShowMath.hash(seed, i) * 0.6f) / 9f * Mth.TWO_PI + t * 0.03f;
				float len = (0.55f + 0.5f * ShowMath.hash(seed, i + 20)) * (0.6f + 0.4f * dial) * pulse;
				kite(flat, last, at, left, up, ang, 0.06f, len, 0.05f, 0xF0FFD8, 0x48E83E, 0.55f * dial);
			}
		}
	}

	/** A big soft glow round the body ({@code outer} / {@code inner} colours), {@code from} to {@code to}: the golden glow of a change. */
	public static void bodyGlow(PoseStack pose, MultiBufferSource buffers, Camera cam, Player p, float t, float partial, float from, float to, int outer,
			int inner) {
		float gold = ShowMath.env(t, from, 4f, to, 7f);
		if (gold > 0f) {
			Vec3 cp = cam.getPosition();
			Vector3f left = new Vector3f(cam.getLeftVector()), up = new Vector3f(cam.getUpVector());
			VertexConsumer glow = buffers.getBuffer(RenderType.eyes(OmnitrixTransformFx.GLOW));
			PoseStack.Pose last = pose.last();
			Vec3 at = p.getPosition(partial).add(0, p.getBbHeight() * 0.55, 0).subtract(cp);
			float s = p.getBbHeight() / 1.8f;
			glowQuad(glow, last, at, 2.2f * s, ShowMath.scaleRgb(outer, gold * 0.8f), left, up);
			glowQuad(glow, last, at, 1.0f * s, ShowMath.scaleRgb(inner, gold), left, up);
		}
	}

	/** A camera-facing additive glow quad of radius {@code r} at {@code at} (camera-relative). */
	public static void glowQuad(VertexConsumer vc, PoseStack.Pose p, Vec3 at, float r, int argb, Vector3f left, Vector3f up) {
		int rr = (argb >> 16) & 255, gg = (argb >> 8) & 255, bb = argb & 255;
		float lx = left.x * r, ly = left.y * r, lz = left.z * r, ux = up.x * r, uy = up.y * r, uz = up.z * r;
		float x = (float) at.x, y = (float) at.y, z = (float) at.z;
		float[][] q = { { lx + ux, ly + uy, lz + uz, 0, 0 }, { lx - ux, ly - uy, lz - uz, 0, 1 }, { -lx - ux, -ly - uy, -lz - uz, 1, 1 },
				{ -lx + ux, -ly + uy, -lz + uz, 1, 0 } };
		for (float[] v : q) {
			vc.addVertex(p, x + v[0], y + v[1], z + v[2]).setColor(rr, gg, bb, 255).setUv(v[3], v[4]).setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(LightTexture.FULL_BRIGHT).setNormal(p, 0, 0, 1);
		}
	}

	/** A light streak in the camera plane round {@code at}: from {@code r0} out to {@code len}, {@code w} wide a third of the way. */
	public static void kite(VertexConsumer vc, PoseStack.Pose p, Vec3 at, Vector3f ax, Vector3f ay, float ang, float r0, float len, float w, int rgbIn,
			int rgbOut, float a) {
		float c = Mth.cos(ang), s = Mth.sin(ang);
		float dx = ax.x * c + ay.x * s, dy = ax.y * c + ay.y * s, dz = ax.z * c + ay.z * s;
		float qx = -ax.x * s + ay.x * c, qy = -ax.y * s + ay.y * c, qz = -ax.z * s + ay.z * c;
		float x = (float) at.x, y = (float) at.y, z = (float) at.z;
		float rm = r0 + (len - r0) * 0.3f;
		put(vc, p, x + dx * r0, y + dy * r0, z + dz * r0, rgbIn, a);
		put(vc, p, x + dx * rm + qx * w, y + dy * rm + qy * w, z + dz * rm + qz * w, rgbIn, a);
		put(vc, p, x + dx * len, y + dy * len, z + dz * len, rgbOut, 0f);
		put(vc, p, x + dx * rm - qx * w, y + dy * rm - qy * w, z + dz * rm - qz * w, rgbIn, a);
	}

	private static void put(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, int rgb, float a) {
		vc.addVertex(p, x, y, z).setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(Mth.clamp(a, 0f, 1f) * 255));
	}
}
