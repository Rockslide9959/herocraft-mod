package com.projecthero.mod.client.undead;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.data.UndeadState;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix4f;

/**
 * v0.17.3 (user: "do both styles and give the player the option to pick"): the second regrowth look, the stepped one -- a
 * part grows back in a few chunky frames instead of the 3D bone / muscle / skin of {@link UndeadRegrow}:
 * <ol>
 * <li><b>bone</b>: the part's own surface, full size, painted in a bone-and-blood pattern (ivory bone, dark blood streaks,
 * pale sinew), revealed frame by frame from the joint outward behind a ragged line (a procedural mask, re-cut every frame);
 * the open end is capped with a cross-section (marrow, bone ring, raw red flesh);</li>
 * <li><b>flesh</b>: the part in the player's own skin, raw and red, the red fading frame by frame;</li>
 * <li><b>skin</b>: the part as it was.</li>
 * </ol>
 * Frames are whole steps (one frame a tick at full speed), so the change from frame to frame is visible, not smooth. The
 * frame counts and stage boundaries are per segment ({@link Seg}): head 17 (bone to 9), body 20 (to 12), arm 18 (to 10), hand
 * 12 (to 6), legs 20 (to 11), feet 11 (to 4); progress {@code p} 0..1 maps onto them.
 *
 * <p>Picked with {@link UndeadRegrowStyle} (always stepped now); {@link UndeadRegrow}'s drawing roots
 * hand over here when it says so. Drawn in the part's own space (model px, +y down), like UndeadRegrow. The bone and flesh
 * colours are flat colours on the palette's white texel; the flesh stage tints the player's own skin texture.
 */
public final class UndeadRegrowStepped {
	/** The palette's plain white texel (column 5), as in UndeadRegrow. */
	private static final float WU0 = 5.3f / 16f, WV0 = 0.3f / 16f, WU1 = 5.7f / 16f, WV1 = 0.7f / 16f;

	// the bone-and-blood pattern
	private static final int[] BONE = { 0xEFE6CF, 0xE4D9BD, 0xD6C8A6, 0xC5B48D };
	private static final int[] BLOOD = { 0x4A0709, 0x6A0B0F, 0x8A1216, 0xA8191E };
	private static final int SINEW = 0xC0606A;
	/** The raw flesh film: muscle reds, a few pale sinew strands. */
	private static final int[] FLESH_FILM = { 0x8E1418, 0xA01A1E, 0xB52226, 0x7A0F13, 0xC24A50 };
	private static final int WET_EDGE = 0xC41A1F, WET_EDGE_HOT = 0xE0302E;
	// the cross-section cap
	private static final int MARROW = 0x9C2A22, CAP_BONE = 0xE8DCC0, CAP_FLESH = 0x8E1418, CAP_RIM = 0x5A0A0D;

	private UndeadRegrowStepped() {
	}

	/** One segment's frames: all of them, and the last bone frame (flesh follows until the last, then skin). */
	public record Seg(int frames, int boneEnd) {
		public static final Seg HEAD = new Seg(17, 9), BODY = new Seg(20, 12), ARM = new Seg(18, 10), HAND = new Seg(12, 6),
				LEGS = new Seg(20, 11), FEET = new Seg(11, 4);

		/** The whole frame {@code p} (0..1) is on. */
		int frame(float p) {
			return Math.max(0, Math.min(frames, (int) Math.floor(p * frames + 1.0e-4f)));
		}
	}

	/** The segment that grows rows [{@code from}, 12) of an arm or leg: the hand / feet if it starts at the wrist / ankle. */
	public static Seg limbSeg(int part, float from) {
		boolean arm = part == UndeadState.RIGHT_ARM || part == UndeadState.LEFT_ARM;
		boolean end = from >= 12 - UndeadConfig.HAND_ROWS - 0.01f;
		return arm ? (end ? Seg.HAND : Seg.ARM) : (end ? Seg.FEET : Seg.LEGS);
	}

	// ---------------------------------------------------------------- the drawing roots (mirroring UndeadRegrow's)

	/** A whole head or body growing back, {@code p} 0..1 (UndeadRegrow.boneAndFlesh). */
	public static void boneAndFlesh(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		float h = part == UndeadState.HEAD ? 8f : 12f;
		segment(pose, buffers, light, tex, slim, part, p, 0f, h, part == UndeadState.HEAD ? Seg.HEAD : Seg.BODY, false);
	}

	/**
	 * An arm or leg cut at {@code from} rows (the rows above it whole) growing back to its end, or just cut when {@code p} is 0
	 * (UndeadRegrow.limb / cut): the skin above the cut, then {@link #segment} from the cut to the end.
	 */
	public static void limb(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p, float from,
			boolean embers) {
		if (from > 0.01f) {
			skin(pose, buffers, light, tex, slim, part, 0f, from, 1f);
		}
		segment(pose, buffers, light, tex, slim, part, p, from, 12f, limbSeg(part, from), embers);
	}

	/** The body with only its top {@code rows} rows left (a boost burning it from the bottom up, or growing back): UndeadRegrow.bodyCut. */
	public static void bodyCut(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, float rows, boolean embers) {
		rows = Math.max(0f, Math.min(12f, rows));
		if (rows >= 11.99f) {
			skin(pose, buffers, light, tex, slim, UndeadState.BODY, 0f, 12f, 1f);
			return;
		}
		skin(pose, buffers, light, tex, slim, UndeadState.BODY, 0f, rows, 1f);
		cap(pose, buffers, light, slim, UndeadState.BODY, rows, embers);
	}

	/**
	 * The stepped regrowth of rows [{@code from}, {@code to}) of a part (rows from the joint: an arm / leg / body from the top,
	 * the head from the neck up), {@code p} 0..1 through {@code seg}'s frames. Only those rows are drawn, so an arm can stop at
	 * the wrist ({@code to} = 8) and its hand grow separately ({@code from} = 8, {@code to} = 12). With {@code p} 0 it is an
	 * open cut at {@code from} (glowing embers while it burns).
	 */
	public static void segment(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p, float from,
			float to, Seg seg, boolean embers) {
		int frame = seg.frame(p);
		if (frame >= seg.frames) {
			skin(pose, buffers, light, tex, slim, part, from, to, 1f);
			return;
		}
		if (frame > seg.boneEnd) {
			// flesh: the part in the player's own skin, raw red fading out over the flesh frames
			float t = (frame - seg.boneEnd) / (float) (seg.frames - seg.boneEnd);
			skin(pose, buffers, light, tex, slim, part, from, to, t);
			return;
		}
		// bone: revealed from the joint outward, a ragged line re-cut every frame
		float reach = seg.boneEnd <= 0 ? to : from + (to - from) * (frame / (float) seg.boneEnd);
		if (frame > 0) {
			bone(pose, buffers, light, slim, part, from, reach, to, frame);
		}
		if (reach < to - 0.01f || frame == 0) {
			cap(pose, buffers, light, slim, part, frame == 0 ? from : reach, embers && frame == 0);
		}
	}

	// ---------------------------------------------------------------- the layers

	/** The part's skin texels in rows [from, to); {@code ripe} 1 = as it is, 0 = raw red flesh. */
	private static void skin(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float from, float to,
			float ripe) {
		PoseStack.Pose last = pose.last();
		float h = height(part);
		ripe = Math.max(0f, Math.min(1f, ripe));
		List<UndeadTexels.Texel> texels = UndeadTexels.of(part, slim);
		// raw flesh sits a hair proud and swollen, settling as it ripens
		float out = ripe >= 1f ? 0f : 0.06f * (1f - ripe);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		int r = 255, g = (int) (105 + 150 * ripe), b = (int) (100 + 155 * ripe);
		for (UndeadTexels.Texel t : texels) {
			if (in(t, row(part, slim, t, h), from, to, h) && SkinPixels.shows(tex, t.u, t.v)) {
				float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
				quad(vc, last, t, out, u0, v0, u1, v1, r, g, b, light);
			}
		}
		if (ripe >= 1f) {
			return;
		}
		// raw flesh: a wet red film over the skin (alpha, so it reads on any skin tone), fading as it ripens -- a second pass,
		// after the skin's buffer is done with (fetching another render type's buffer ends the one before)
		VertexConsumer film = buffers.getBuffer(RenderType.entityTranslucent(UndeadRender.PALETTE));
		int a = (int) (215 * (1f - ripe) * (1f - ripe) + 25 * (1f - ripe));
		for (UndeadTexels.Texel t : texels) {
			if (in(t, row(part, slim, t, h), from, to, h) && SkinPixels.shows(tex, t.u, t.v)) {
				int rgb = FLESH_FILM[Math.min(FLESH_FILM.length - 1, (int) (t.seed * FLESH_FILM.length))];
				quadA(film, last, t, out + 0.015f, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, a, light);
			}
		}
	}

	/** The bone-and-blood surface in rows [from, reach) (the ragged mask), the newest row wet and red. */
	private static void bone(PoseStack pose, MultiBufferSource buffers, int light, boolean slim, int part, float from, float reach, float to, int frame) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		PoseStack.Pose last = pose.last();
		float h = height(part);
		long seed = 5150L + part * 37L;
		for (UndeadTexels.Texel t : UndeadTexels.of(part, slim)) {
			if (t.overlay) {
				continue; // the pattern is on the base surface only
			}
			float row = row(part, slim, t, h);
			if (!in(t, row, from, to, h)) {
				continue;
			}
			// the mask: a ragged line, a little different every frame (like a hand-drawn mask per frame)
			float jag = (UndeadTexels.noise(t.x * 0.9f + 2.3f, frame * 0.37f, t.z * 0.9f + 5.1f, seed) - 0.5f) * 1.6f
					+ (UndeadTexels.hash(Math.round(t.x * 2f), frame, Math.round(t.z * 2f), seed + 1) - 0.5f) * 0.5f;
			float key = row + (row <= 0.01f ? -9f : jag);
			if (key >= reach && reach < to - 0.01f) {
				continue;
			}
			int rgb = pattern(t, seed);
			float edge = reach - key;
			if (reach < to - 0.01f && edge < 0.9f) {
				rgb = edge < 0.4f ? WET_EDGE_HOT : WET_EDGE; // the growing edge, wet
			}
			quad(vc, last, t, 0.01f, WU0, WV0, WU1, WV1, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, light);
		}
	}

	/** The bone-and-blood colour of one texel: mostly ivory bone, blood running in streaks along the part, a little sinew. */
	private static int pattern(UndeadTexels.Texel t, long seed) {
		float streak = UndeadTexels.noise(t.x * 1.3f + 1.1f, t.y * 0.38f, t.z * 1.3f + 3.7f, seed + 7);
		float speck = UndeadTexels.hash(Math.round(t.x * 4f), Math.round(t.y * 4f), Math.round(t.z * 4f), seed + 9);
		if (streak > 0.68f) {
			return BLOOD[Math.min(BLOOD.length - 1, (int) ((streak - 0.68f) * 12f + speck * 1.5f))];
		}
		if (streak > 0.63f) {
			return speck < 0.5f ? SINEW : BLOOD[3];
		}
		if (speck > 0.93f) {
			return BLOOD[1]; // a fleck of blood on the bone
		}
		return BONE[Math.min(BONE.length - 1, (int) (speck * BONE.length))];
	}

	/**
	 * The open end at {@code at} rows: a cross-section, 1 px cells -- marrow in the middle, a bone ring round it, raw flesh out
	 * to the edge, a dark rim. Glowing embers while it burns.
	 */
	private static void cap(PoseStack pose, MultiBufferSource buffers, int light, boolean slim, int part, float at, boolean embers) {
		float[] box = UndeadTexels.box(part, slim);
		float h = height(part);
		if (at >= h - 0.01f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		PoseStack.Pose last = pose.last();
		float y = part == UndeadState.HEAD ? box[1] + box[4] - at : box[1] + at;
		int w = (int) box[3], d = (int) box[5];
		float cx = (w - 1) * 0.5f, cz = (d - 1) * 0.5f;
		float half = Math.min(w, d) * 0.5f;
		int l = embers ? 0xF000F0 : light;
		for (int z = 0; z < d; z++) {
			for (int x = 0; x < w; x++) {
				float rx = Math.abs(x - cx), rz = Math.abs(z - cz);
				float r = Math.max(rx, rz) / Math.max(0.5f, half - 0.5f);
				int rgb;
				if (embers) {
					rgb = UndeadRegrow.EMBER[Math.max(0, Math.min(UndeadRegrow.EMBER.length - 1,
							(int) ((1f - r) * 4f + UndeadTexels.hash(x, 0, z, 77L) * 1.5f)))];
				} else if (part != UndeadState.HEAD && part != UndeadState.BODY && r < 0.3f) {
					rgb = MARROW;
				} else if (part != UndeadState.HEAD && part != UndeadState.BODY && r < 0.65f) {
					rgb = CAP_BONE;
				} else if ((part == UndeadState.HEAD || part == UndeadState.BODY) && rx < cx * 0.6f && rz < cz * 0.5f) {
					rgb = (x + z) % 2 == 0 ? CAP_BONE : MARROW; // the spine / skull's base
				} else {
					rgb = r > 0.95f ? CAP_RIM : CAP_FLESH;
				}
				float x0 = box[0] + x, z0 = box[2] + z;
				UndeadRegrow.flatBox(vc, last, x0 + 0.02f, y - 0.06f, z0 + 0.02f, x0 + 0.98f, y + 0.06f, z0 + 0.98f, rgb, l);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	private static float height(int part) {
		return part == UndeadState.HEAD ? 8f : 12f;
	}

	/** A texel's row from the joint (0 at the joint end, {@code h} at the far end): the head counts up from the neck. */
	private static float row(int part, boolean slim, UndeadTexels.Texel t, float h) {
		float[] box = UndeadTexels.box(part, slim);
		if (part == UndeadState.HEAD) {
			if (t.ny > 0.5f) {
				return 0f; // the underside, at the neck
			}
			if (t.ny < -0.5f) {
				return h; // the crown, last
			}
			return Math.max(0f, Math.min(h, box[1] + box[4] - t.y));
		}
		if (t.ny < -0.5f) {
			return 0f; // the top face, at the joint
		}
		if (t.ny > 0.5f) {
			return h; // the far end (the hand, the foot, the hips)
		}
		return Math.max(0f, Math.min(h, t.y - box[1]));
	}

	/** In rows [from, to): the joint face belongs to the first segment, the far face to the last. */
	private static boolean in(UndeadTexels.Texel t, float row, float from, float to, float h) {
		if (row <= 0.001f) {
			return from <= 0.001f;
		}
		if (row >= h - 0.001f) {
			return to >= h - 0.001f;
		}
		return row >= from && row < to;
	}

	/** One flat-coloured square with alpha on the palette's white texel. */
	private static void quadA(VertexConsumer vc, PoseStack.Pose pose, UndeadTexels.Texel t, float out, int r, int g, int b, int a, int light) {
		float[][] p = UndeadRender.cornersPublic(t, out, 0f);
		Matrix4f m = pose.pose();
		for (int i = 0; i < 4; i++) {
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(r, g, b, a).setUv((i == 1 || i == 2) ? WU1 : WU0, (i >= 2) ? WV1 : WV0)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, t.nx, t.ny, t.nz);
		}
	}

	/** One texel square, {@code out} px proud, tinted {@code r g b}. */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, UndeadTexels.Texel t, float out, float u0, float v0, float u1, float v1, int r, int g,
			int b, int light) {
		float[][] p = UndeadRender.cornersPublic(t, out, 0f);
		Matrix4f m = pose.pose();
		for (int i = 0; i < 4; i++) {
			float uu = (i == 1 || i == 2) ? u1 : u0;
			float vv = (i >= 2) ? v1 : v0;
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(r, g, b, 255).setUv(uu, vv)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, t.nx, t.ny, t.nz);
		}
	}
}
