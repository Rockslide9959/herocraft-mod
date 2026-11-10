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
 * <li><b>muscle and skin</b> (v0.17.3, user: "the muscle needs to also appear randomly pixel by pixel and after 60% of the
 * muscle is on the arm skin can start appearing randomly pixel by pixel"): muscle lands on the bone texel by texel in a
 * scattered order ({@link #MUSCLE_END}); once {@link #SKIN_AFTER_MUSCLE} of it is in, the skin lands texel by texel in its own
 * scattered order, only on texels that already have muscle, until the whole part is skin;</li>
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
		float f = p * seg.frames;
		if (f > seg.boneEnd) {
			// v0.17.3 (user: "the muscle needs to also appear randomly pixel by pixel and after 60% of the muscle is on the arm skin
			// can start appearing randomly pixel by pixel"): over the whole bone, muscle lands texel by texel in a scattered order
			// from the end of the bone stage to MUSCLE_END of the regrowth; once SKIN_AFTER_MUSCLE of it is in, the skin lands texel
			// by texel too (its own order), only ever on a texel that already has its muscle, to the end. Smooth in p.
			float muscleEnd = Math.max(seg.boneEnd + 1f, MUSCLE_END * seg.frames);
			float muscle = (f - seg.boneEnd) / (muscleEnd - seg.boneEnd);
			float skinStart = seg.boneEnd + SKIN_AFTER_MUSCLE * (muscleEnd - seg.boneEnd);
			float skin = (f - skinStart) / Math.max(0.5f, seg.frames - skinStart);
			muscleAndSkin(pose, buffers, light, tex, slim, part, from, to, muscle, skin);
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

	/** The muscle stage ends at this share of a segment's frames (it starts at the end of the bone stage). */
	static final float MUSCLE_END = 0.82f;
	/** The skin starts landing once this share of the muscle is in. */
	static final float SKIN_AFTER_MUSCLE = 0.6f;

	/** When a base texel's muscle lands (0..1 through the muscle stage; the same every frame and for every viewer). */
	static float muscleAt(UndeadTexels.Texel base) {
		return UndeadTexels.hash(base.u, base.v, 3, 0x3A5CL);
	}

	/** When a base texel's skin lands (0..1 through the skin stage; a different scatter from the muscle's). */
	static float skinAt(UndeadTexels.Texel base) {
		return UndeadTexels.hash(base.u, base.v, 5, 0x5EEDL);
	}

	/**
	 * The muscle-and-skin stage of rows [from, to): {@code muscle} and {@code skin} run 0..1 (clamped). Each base texel is bone
	 * until its muscle lands ({@link #muscleAt} below {@code muscle}), then raw muscle until its skin lands ({@link #skinAt} below
	 * {@code skin} -- and never before its muscle), then the player's skin. An overlay (hat / sleeve) texel shows with the skin of
	 * the base texel under it (the overlay list mirrors the base list, index for index).
	 */
	private static void muscleAndSkin(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float from,
			float to, float muscle, float skin) {
		muscle = Math.max(0f, Math.min(1f, muscle));
		skin = Math.max(0f, Math.min(1f, skin));
		PoseStack.Pose last = pose.last();
		float h = height(part);
		List<UndeadTexels.Texel> texels = UndeadTexels.of(part, slim);
		int n = texels.size() / 2; // base texels first, then the overlay ones in the same order
		// the skin (translucent, culled: exactly as the vanilla model draws it, so the hand-over at the end is seamless)
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
		for (int i = 0; i < texels.size(); i++) {
			UndeadTexels.Texel t = texels.get(i);
			UndeadTexels.Texel base = i < n ? t : texels.get(i - n);
			if (muscleAt(base) < muscle && skinAt(base) < skin && in(base, row(part, slim, base, h), from, to, h) && SkinPixels.shows(tex, t.u, t.v)) {
				float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
				quad(vc, last, t, 0f, u0, v0, u1, v1, 255, 255, 255, light);
			}
		}
		if (muscle >= 1f && skin >= 1f) {
			return;
		}
		// the muscle and the bone still showing (a second pass: fetching another render type's buffer ends the one before)
		VertexConsumer flat = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		long seed = 7310L + part * 53L, boneSeed = 5150L + part * 37L;
		for (int i = 0; i < n; i++) {
			UndeadTexels.Texel t = texels.get(i);
			boolean hasMuscle = muscleAt(t) < muscle;
			if ((hasMuscle && skinAt(t) < skin) || !in(t, row(part, slim, t, h), from, to, h)) {
				continue;
			}
			int rgb;
			if (hasMuscle) {
				// muscle fibres run along the part: a streaky noise picks the shade, a few pale strands of sinew
				float fibre = UndeadTexels.noise(t.x * 1.6f + 0.7f, t.y * 0.25f, t.z * 1.6f + 2.9f, seed);
				rgb = fibre > 0.74f ? SINEW : FLESH_FILM[Math.min(FLESH_FILM.length - 2, (int) (fibre * (FLESH_FILM.length - 1)))];
			} else {
				rgb = pattern(t, boneSeed); // still the bone it grew from
			}
			quad(flat, last, t, hasMuscle ? 0f : 0.01f, WU0, WV0, WU1, WV1, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, light);
		}
	}

	/**
	 * v0.17.3 (UndeadPartEntity#leaveBehind): rows [from, to) of a part as a piece lying apart from the body, eroding away:
	 * {@code dissolve} 0 = whole, 1 = gone -- every skin texel shrinks to nothing over its own short window, in a scattered order,
	 * so the piece wears away pixel by pixel (nothing bursts or falls). A slice end that is not the part's own end (a cut) is a raw
	 * cross-section that erodes the same way.
	 */
	public static void remnant(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float from, float to,
			float dissolve) {
		float h = height(part);
		PoseStack.Pose last = pose.last();
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
		for (UndeadTexels.Texel t : UndeadTexels.of(part, slim)) {
			if (!in(t, row(part, slim, t, h), from, to, h) || !SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			float s = erode(UndeadTexels.hash(t.u, t.v, t.overlay ? 1 : 0, 0xD155L), dissolve);
			if (s <= 0f) {
				continue;
			}
			float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
			quad(vc, last, t, 0f, -Math.min(t.ha, t.hb) * (1f - s), u0, v0, u1, v1, 255, 255, 255, light);
		}
		if (from > 0.01f) {
			cap(pose, buffers, light, slim, part, from, false, dissolve);
		}
		if (to < h - 0.01f) {
			cap(pose, buffers, light, slim, part, to, false, dissolve);
		}
	}

	/** How much of a pixel is left (1 whole .. 0 gone) at {@code dissolve}, for a pixel whose turn is {@code h} (0..1). */
	static float erode(float h, float dissolve) {
		if (dissolve <= 0f) {
			return 1f;
		}
		float k = Math.max(0f, Math.min(1f, (dissolve - h * 0.7f) / 0.3f));
		return 1f - k * k;
	}

	/** The part's skin texels in rows [from, to); {@code ripe} 1 = as it is, 0 = raw red flesh. */
	private static void skin(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float from, float to,
			float ripe) {
		PoseStack.Pose last = pose.last();
		float h = height(part);
		ripe = Math.max(0f, Math.min(1f, ripe));
		List<UndeadTexels.Texel> texels = UndeadTexels.of(part, slim);
		// raw flesh sits a hair proud and swollen, settling as it ripens
		float out = ripe >= 1f ? 0f : 0.06f * (1f - ripe);
		// v0.17.3 (user: "sometimes the limb flashes when its fully regrown"): drawn as the vanilla player model draws its skin --
		// translucent and back-face culled. Cutout without culling showed the inside walls of the sleeve / jacket layer through its
		// see-through pixels and made half-transparent overlay pixels solid, so the part changed look the moment the vanilla
		// model took over.
		VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
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
		cap(pose, buffers, light, slim, part, at, embers, 0f);
	}

	/** The same, each 1 px cell eroded by {@code dissolve} (0 = whole, 1 = gone; {@link #erode}). */
	private static void cap(PoseStack pose, MultiBufferSource buffers, int light, boolean slim, int part, float at, boolean embers, float dissolve) {
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
				float inset = 0.02f;
				if (dissolve > 0f) {
					float s = erode(UndeadTexels.hash(x, Math.round(at), z, 0xCA9L + part), dissolve);
					if (s <= 0f) {
						continue;
					}
					inset = 0.02f + 0.48f * (1f - s);
				}
				UndeadRegrow.flatBox(vc, last, x0 + inset, y - 0.06f, z0 + inset, x0 + 1f - inset, y + 0.06f, z0 + 1f - inset, rgb, l);
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

	/**
	 * True if a texel square's corners (UndeadRender#cornersPublic: one fixed order per plane) wind clockwise seen from outside, so
	 * they go in reversed: with back-face culling (the translucent skin, like the vanilla model) only the outside face shows.
	 */
	static boolean backwards(UndeadTexels.Texel t) {
		return t.plane == 0 ? t.nx > 0f : t.plane == 1 ? t.ny > 0f : t.nz < 0f;
	}

	/** One flat-coloured square with alpha on the palette's white texel. */
	private static void quadA(VertexConsumer vc, PoseStack.Pose pose, UndeadTexels.Texel t, float out, int r, int g, int b, int a, int light) {
		float[][] p = UndeadRender.cornersPublic(t, out, 0f);
		Matrix4f m = pose.pose();
		boolean flip = backwards(t);
		for (int k = 0; k < 4; k++) {
			int i = flip ? (4 - k) % 4 : k;
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(r, g, b, a).setUv((i == 1 || i == 2) ? WU1 : WU0, (i >= 2) ? WV1 : WV0)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, t.nx, t.ny, t.nz);
		}
	}

	/** One texel square, {@code out} px proud, tinted {@code r g b}. */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, UndeadTexels.Texel t, float out, float u0, float v0, float u1, float v1, int r, int g,
			int b, int light) {
		quad(vc, pose, t, out, 0f, u0, v0, u1, v1, r, g, b, light);
	}

	/** The same, the square grown by {@code grow} px on every side (negative: shrunk about its centre). */
	private static void quad(VertexConsumer vc, PoseStack.Pose pose, UndeadTexels.Texel t, float out, float grow, float u0, float v0, float u1, float v1,
			int r, int g, int b, int light) {
		float[][] p = UndeadRender.cornersPublic(t, out, grow);
		Matrix4f m = pose.pose();
		boolean flip = backwards(t);
		for (int k = 0; k < 4; k++) {
			int i = flip ? (4 - k) % 4 : k;
			float uu = (i == 1 || i == 2) ? u1 : u0;
			float vv = (i >= 2) ? v1 : v0;
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(r, g, b, 255).setUv(uu, vv)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, t.nx, t.ny, t.nz);
		}
	}
}
