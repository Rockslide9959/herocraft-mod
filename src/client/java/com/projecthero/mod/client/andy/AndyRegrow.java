package com.projecthero.mod.client.andy;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.client.render.VanillaSkeleton;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: how a part of Andy grows back -- the one look, bone and flesh (user: "make this the only one"). Three layers,
 * each from the joint outward, timed by {@link AndyConfig#LAYER_BONE}, {@link AndyConfig#LAYER_MUSCLE} and
 * {@link AndyConfig#LAYER_SKIN} (at Normal: the bone pushes out over 0-3 s, the muscle builds over 2-6 s, the skin over
 * 4-8.5 s; the bone always ahead of the muscle, the muscle ahead of the skin):
 * <ul>
 * <li>an arm or leg ({@link #limb}): nothing pops in; the vanilla skeleton's thin bone pushes slowly out of the joint; ragged
 * dark-red muscle cubes build round it from the joint, swelling in at their front; the player's own skin texels close over
 * the muscle behind a ragged front, the hand / foot end last. The same drawing shows a limb that only lost its end -- the
 * hands and feet after a flight, the bite the finger shots took, a stage half burnt in a boost ({@link #cut}) -- and its
 * regrowth from that cut ({@link #limb} with {@code from});</li>
 * <li>the head and body ({@link #boneAndFlesh}): the vanilla skull / ribcage grows out of the stump, the muscle wraps it, the
 * skin closes over, in the same three windows.</li>
 * </ul>
 * Called from {@link AndyRender} in the part's own space (model px, +y down, -z the front). No glow (but for the embers of a
 * boost burn on a cut face). The flesh is flat-coloured cubes on one white texel of the palette.
 */
public final class AndyRegrow {
	/** A texel's pop-in (share of a whole regrowth) for the head and body. */
	static final float SETTLE = 0.075f;

	// the head and body: when each layer runs (shares of the regrowth)
	static final float BONE_START = AndyConfig.LAYER_BONE[0], BONE_SPAN = AndyConfig.LAYER_BONE[1];
	static final float FLESH_START = AndyConfig.LAYER_MUSCLE[0], FLESH_SPAN = AndyConfig.LAYER_MUSCLE[1];
	static final float SKIN_START = AndyConfig.LAYER_SKIN[0], SKIN_SPAN = AndyConfig.LAYER_SKIN[1];

	/** The palette's plain white texel (column 5): bone and flesh are tinted from it. */
	private static final float WU0 = 5.3f / 16f, WV0 = 0.3f / 16f, WU1 = 5.7f / 16f, WV1 = 0.7f / 16f;

	private AndyRegrow() {
	}

	/** A whole part growing back, {@code p} 0..1. */
	public static void draw(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		if (part >= AndyState.RIGHT_ARM) {
			limb(pose, buffers, light, tex, slim, part, p, 0f, false);
		} else {
			boneAndFlesh(pose, buffers, light, tex, slim, part, p);
		}
	}

	/**
	 * An arm or leg missing its last {@code 12 - rows} rows (its end: the hand / foot, a used-up bit of arm, a burnt stage),
	 * the cut face ragged dark-red muscle -- glowing embers while it burns.
	 */
	public static void cut(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float rows, boolean embers) {
		limb(pose, buffers, light, tex, slim, part, 0f, Math.max(0f, rows), embers);
	}

	// ---------------------------------------------------------------- the head and body

	static void boneAndFlesh(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		if (AndyRegrowStyle.stepped()) { // v0.17.3: the stepped look, picked per viewer (/andystyle)
			AndyRegrowStepped.boneAndFlesh(pose, buffers, light, tex, slim, part, p);
			return;
		}
		VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		PoseStack.Pose last = pose.last();
		boolean skinDone = true;
		Body body = Body.of(part, slim);
		List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
		float[] under = body.skinAfter(texels);
		for (int i = 0; i < texels.size(); i++) {
			AndyTexels.Texel t = texels.get(i);
			if (!SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			// the torso closes from the neck down (shoulders first, hips last: the limbs grow out of it in that order); the
			// head keeps its patchier spread. v0.17.3: a texel only closes over muscle that has reached its full thickness
			float key = part == AndyState.BODY ? 0.9f * t.order + 0.1f * t.grow : 0.7f * t.order + 0.3f * t.grow;
			float appear = Math.max(SKIN_START + key * (SKIN_SPAN - SETTLE), under[i]);
			float f = (p - appear) / SETTLE;
			if (f < 1f) {
				skinDone = false;
			}
			popIn(skin, last, t, light, f);
		}
		if (skinDone || p >= 0.999f) {
			return; // closed over: nothing inside can show
		}
		vanillaBone(pose, buffers, light, slim, part, p);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		Vector3f[] c = corners8();
		Piece[] grid = body.flesh;
		for (int i = 0; i < grid.length; i++) {
			Piece b = grid[i];
			if (b == null) {
				continue;
			}
			float f = fleshF(b, p);
			if (f <= 0f) {
				continue;
			}
			int mask = 63;
			if (f >= 1f) {
				// a face against a settled neighbour can't be seen
				mask = 0;
				for (int face = 0; face < 6; face++) {
					int j = body.neighbour(i, face);
					if (j < 0 || grid[j] == null || fleshF(grid[j], p) < 1f) {
						mask |= 1 << face;
					}
				}
				if (mask == 0) {
					continue;
				}
			}
			if (f >= 1f) {
				piece(vc, last, c, b, 1f, light, mask);
			} else {
				// v0.17.3 (user: "the muscle builds from the inside out"): a thin sheet laid on the ribcage / skull that
				// thickens outward from the bone's side of its cell -- full width along the bone (no slits between
				// neighbours), ending exactly as the settled cell
				float e = f * f * (3f - 2f * f);
				growPiece(vc, last, c, b, 1f, 0.05f + 0.95f * e, light);
			}
		}
	}

	/** A texel growing in place: {@code f} 0..1 from popping in (small, dark, a hair proud) to settled. Nothing below 0. */
	private static void popIn(VertexConsumer skin, PoseStack.Pose last, AndyTexels.Texel t, int light, float f) {
		if (f <= 0f) {
			return;
		}
		float u0 = (t.u + 0.04f) / 64f, v0 = (t.v + 0.04f) / 64f, u1 = (t.u + 0.96f) / 64f, v1 = (t.v + 0.96f) / 64f;
		if (f >= 1f) {
			square(skin, last, t, u0, v0, u1, v1, light, 0f, 0f, 255);
			return;
		}
		// ease out with a touch of overshoot: it swells into place
		float g = f - 1f;
		float s = 1f + 2.2f * g * g * g + 1.2f * g * g;
		float scale = 0.3f + 0.7f * s;
		float shrink = -(1f - scale) * Math.min(t.ha, t.hb);
		int shade = (int) (255 * (0.62f + 0.38f * Math.min(1f, f * 1.3f)));
		square(skin, last, t, u0, v0, u1, v1, light, 0.12f * (1f - f), shrink, shade);
	}

	// ---------------------------------------------------------------- arms and legs

	/** How long (in rows of a front) a texel takes to pop in and settle, and a muscle cube to swell out. */
	static final float LIMB_SETTLE = 0.6f, LIMB_SWELL = 0.9f;
	/** Five dark reds, darkest first. */
	static final int[] LIMB_FLESH = { 0x3D0606, 0x5A0A0A, 0x7A0F12, 0x941A1A, 0xB02020 };
	/** The burning edge of a boost: ember oranges and reds (only on a cut face that is burning). */
	static final int[] EMBER = { 0x7A1A08, 0xB8360C, 0xE0601A, 0xFF8C2A, 0xFFC060 };
	/** How far behind the joint's rows the far rows' muscle starts thickening (in muscle progress: 0 = all rows at once). */
	static final float MUSCLE_LAG = 0.25f;
	/** Muscle strands: each column of cells starts up to this much later (in muscle progress), so it begins as fibres on the bone. */
	static final float MUSCLE_STRANDS = 0.35f;

	/**
	 * How far the muscle's outer faces stay inside the part's skin planes (model px): every settled muscle cell fills its 1 px
	 * cell exactly, so the muscle is one flush block a hair under the skin -- nothing pokes out past the silhouette, and the
	 * skin texels popping in over it never z-fight it.
	 */
	static final float MUSCLE_INSET = 0.035f;

	/** Where the bone's tip stops (rows from the joint). */
	static final float BONE_REACH = 11.9f;

	/** The skin front (rows from the joint) for regrowth progress {@code p} from a cut at {@code from} rows. */
	static float skinFront(int part, boolean slim, float p, float from) {
		Limb lb = Limb.of(part, slim);
		return from + (lb.end - from) * AndyConfig.layer(AndyConfig.LAYER_SKIN, p);
	}

	/**
	 * v0.17.3 (user: "make the bone slowly push out and then the muscle slowly builds and then the skin builds over that"): an
	 * arm or leg growing from {@code from} rows (0: from the joint; more: from a cut, the rows above it whole) to its end. Three
	 * fronts, all in rows from the joint: the bone's tip, the muscle's front and the skin's front, each moving over its own
	 * window of {@code p} and clamped behind the one before it. {@code p} = 0 with {@code from} > 0 is a still cut.
	 */
	static void limb(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p, float from,
			boolean embers) {
		if (AndyRegrowStyle.stepped()) { // v0.17.3: the stepped look, picked per viewer (/andystyle)
			AndyRegrowStepped.limb(pose, buffers, light, tex, slim, part, p, from, embers);
			return;
		}
		Limb lb = Limb.of(part, slim);
		float qb = AndyConfig.layer(AndyConfig.LAYER_BONE, p);
		float qm = AndyConfig.layer(AndyConfig.LAYER_MUSCLE, p);
		float qs = AndyConfig.layer(AndyConfig.LAYER_SKIN, p);
		float fb = from + Math.max(0f, BONE_REACH - from) * qb * qb * (3f - 2f * qb);
		// v0.17.3 (user: "make the regrowth start inwards and regenerate outwards ... the muscle first builds from the bone out
		// and then gets covered by skin"): the muscle thickens outward from the bone's surface, every row at once (the rows
		// near the joint a little ahead), and the skin only closes over rows whose muscle is full thickness
		float span = Math.max(1f, lb.h - from);
		float fs = from + (lb.end - from) * qs;
		if (qm < 1f) {
			float fullRow = from + span * (qm * (1f + MUSCLE_LAG + MUSCLE_STRANDS) - 1f - MUSCLE_STRANDS) / MUSCLE_LAG;
			fs = Math.min(fs, Math.max(from, fullRow - 0.3f));
		}
		PoseStack.Pose last = pose.last();
		VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
		for (int i = 0; i < texels.size(); i++) {
			float key = lb.texelAt[i];
			float g = key < from ? 1f : (fs - key) / LIMB_SETTLE;
			if (g <= 0f) {
				continue;
			}
			AndyTexels.Texel t = texels.get(i);
			if (SkinPixels.shows(tex, t.u, t.v)) {
				popIn(skin, last, t, light, g);
			}
		}
		if (fs >= lb.done) {
			return; // closed over
		}
		if (fb > from + 0.05f) {
			limbBone(pose, buffers, light, slim, part, from, fb);
		}
		float ax = lb.box[0] + lb.w * 0.5f, az = lb.box[2] + lb.d * 0.5f;
		float boneR = slim && (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) ? 0.8f : 1.0f;
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		float[] box = lb.box;
		boolean building = qm > 0f;
		for (int i = 0; i < lb.n; i++) {
			float skinAt = lb.skinAt[i];
			if (skinAt < from) {
				continue; // above the cut: under whole skin
			}
			float key = lb.fleshKey[i];
			boolean face = from > 0f && key < from; // the cut face itself
			int row = (i / lb.w) % lb.h;
			float mr = 0f;
			if (!face) {
				if (!building || row + 0.5f > fb) {
					continue; // no muscle yet, or no bone here yet to build on
				}
				float u = Math.max(0f, (row + 0.5f - from) / span);
				float strand = MUSCLE_STRANDS * AndyTexels.hash(i % lb.w, 0, i / (lb.w * lb.h), 515L + part);
				mr = Math.max(0f, Math.min(1f, qm * (1f + MUSCLE_LAG + MUSCLE_STRANDS) - MUSCLE_LAG * u - strand));
				if (mr <= 0f) {
					continue;
				}
			}
			float cap = lb.capAt[i];
			if (fs > skinAt + LIMB_SETTLE && (Float.isNaN(cap) || fs > cap + LIMB_SETTLE)) {
				continue; // the skin has closed over it
			}
			// v0.17.3 (user: "remove the gaps in between the pixels so that the muscle doesnt poke out"): every cell fills its own
			// 1 px cell exactly -- no size or position jitter -- so neighbours meet with no gap, and the whole block is clamped
			// MUSCLE_INSET inside the limb's skin planes so nothing sticks out past the silhouette or fights the skin over it
			float x0 = box[0] + i % lb.w, y0 = box[1] + row, z0 = box[2] + i / (lb.w * lb.h);
			float cx0 = x0, cx1 = x0 + 1f, cz0 = z0, cz1 = z0 + 1f;
			int rgb = lb.rgb[i];
			int l = light;
			if (fs <= skinAt && !face && mr < 1f) {
				// still thickening: the cell is the part of it inside the muscle's radius round the bone, so the muscle
				// grows out from the bone's surface (cells wholly inside the bone are the bone); full row height, so no slits
				float e = mr * mr * (3f - 2f * mr);
				float rx = boneR + 0.08f + (lb.w * 0.5f - boneR + 0.1f) * e;
				float rz = boneR + 0.08f + (lb.d * 0.5f - boneR + 0.1f) * e;
				cx0 = Math.max(x0, ax - rx);
				cx1 = Math.min(x0 + 1f, ax + rx);
				cz0 = Math.max(z0, az - rz);
				cz1 = Math.min(z0 + 1f, az + rz);
				boolean inBone = x0 >= ax - boneR - 0.01f && x0 + 1f <= ax + boneR + 0.01f && z0 >= az - boneR - 0.01f
						&& z0 + 1f <= az + boneR + 0.01f;
				if (inBone) {
					continue;
				}
				// fresh muscle is wetter and darker, settling to its own red as it reaches full thickness
				if (e < 0.6f) {
					rgb = LIMB_FLESH[Math.max(0, java.util.Arrays.binarySearch(LIMB_FLESH, lb.rgb[i]) - 1)];
				}
			} else if (embers && key < from + 1.6f) {
				// the burning edge: hottest right at the cut
				float heat = 1f - Math.max(0f, (from - key)) / 2.2f;
				rgb = EMBER[Math.max(0, Math.min(EMBER.length - 1, (int) (heat * EMBER.length + lb.jo[i] * 6f)))];
				l = 0xF000F0;
			}
			cx0 = Math.max(cx0, box[0] + MUSCLE_INSET);
			cx1 = Math.min(cx1, box[0] + lb.w - MUSCLE_INSET);
			cz0 = Math.max(cz0, box[2] + MUSCLE_INSET);
			cz1 = Math.min(cz1, box[2] + lb.d - MUSCLE_INSET);
			float cy0 = Math.max(y0, box[1] + MUSCLE_INSET), cy1 = Math.min(y0 + 1f, box[1] + lb.h - MUSCLE_INSET);
			if (cx1 - cx0 < 0.04f || cz1 - cz0 < 0.04f) {
				continue;
			}
			flatBox(vc, last, cx0, cy0, cz0, cx1, cy1, cz1, rgb, l);
		}
	}

	/** The limb's bone: the vanilla skeleton's thin arm / leg ({@link VanillaSkeleton}), centred in it, from {@code top} to its tip. */
	private static void limbBone(PoseStack pose, MultiBufferSource buffers, int light, boolean slim, int part, float top, float reach) {
		reach = Math.min(BONE_REACH, reach);
		if (reach <= top + 0.05f) {
			return;
		}
		float[] box = AndyTexels.box(part, slim);
		pose.pushPose();
		pose.translate((box[0] + box[3] * 0.5f) / 16f, 0f, 0f);
		pose.scale(0.94f, 1f, 0.94f); // a hair inside the muscle cubes round it
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(VanillaSkeleton.TEXTURE));
		VanillaSkeleton.draw(pose.last(), vc, part, box[1] + top, box[1] + reach, light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
	}

	private static final int[][] BOX_FACES = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };
	private static final float[][] BOX_NORMALS = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };

	/** A flat-coloured box (model px) on the palette's white texel, all six faces. No allocation. */
	static void flatBox(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		Matrix4f m = pose.pose();
		for (int f = 0; f < 6; f++) {
			int[] fi = BOX_FACES[f];
			float[] n = BOX_NORMALS[f];
			for (int k = 0; k < 4; k++) {
				int ci = fi[k];
				float x = ((ci & 1) == 0 ? x0 : x1) / 16f, y = ((ci & 2) == 0 ? y0 : y1) / 16f, z = ((ci & 4) == 0 ? z0 : z1) / 16f;
				vc.addVertex(m, x, y, z).setColor(r, g, b, 255).setUv(k == 1 || k == 2 ? WU1 : WU0, k >= 2 ? WV1 : WV0)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}

	/**
	 * The fixed timetable of one limb (cached per part and skin model): for every 1 px cell of the limb (column x, z; row
	 * from the joint) the front at which its muscle builds ({@link #fleshKey}) and the front at which the skin covers it
	 * ({@link #skinAt}), and for every texel the front at which it pops in. Both boundaries are ragged per column (smooth
	 * noise down the rows plus a little per-cell scatter, kept monotonic down each column so nothing un-grows).
	 */
	static final class Limb {
		float[] box;
		int w, h, d, n;
		float[] skinAt, fleshKey, capAt;
		/** Per cell: a little scatter for the embers' colour. */
		float[] jo;
		int[] rgb;
		/** Per texel of {@link AndyTexels#of}. */
		float[] texelAt;
		/** The front past which nothing but finished skin is left; the skin front at the end; the muscle front at the end. */
		float done, end, fleshEnd;

		private static final Limb[][] CACHE = new Limb[2][AndyState.PARTS];

		static Limb of(int part, boolean slim) {
			int s = slim && (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) ? 1 : 0;
			Limb l = CACHE[s][part];
			if (l == null) {
				l = new Limb();
				l.make(part, slim);
				CACHE[s][part] = l;
			}
			return l;
		}

		private int cell(int x, int r, int z) {
			return x + r * w + z * w * h;
		}

		private void make(int part, boolean slim) {
			box = AndyTexels.box(part, slim);
			w = (int) box[3];
			h = (int) box[4];
			d = (int) box[5];
			n = w * h * d;
			skinAt = new float[n];
			fleshKey = new float[n];
			capAt = new float[n];
			jo = new float[n];
			rgb = new int[n];
			java.util.Arrays.fill(capAt, Float.NaN);
			long seed = 4242L + part * 101L + (slim ? 7L : 0L);
			float last = 0f, lastFlesh = 0f;
			for (int z = 0; z < d; z++) {
				for (int x = 0; x < w; x++) {
					float prevS = -9f, prevF = -9f;
					for (int r = 0; r < h; r++) {
						int i = cell(x, r, z);
						float off = AndyTexels.noise(x * 0.85f + 3.1f, r * 0.55f, z * 0.85f + 1.7f, seed) * 1.8f - 0.9f
								+ (AndyTexels.hash(x, r, z, seed + 1) - 0.5f) * 0.5f;
						float s = Math.max(r + 0.5f - off, prevS + 0.3f);
						// the muscle's own ragged front, a little ahead of where the skin will close (so a cut shows a red face)
						float foff = AndyTexels.noise(x * 0.9f + 7.3f, r * 0.6f, z * 0.9f + 4.1f, seed + 9) * 1.4f - 0.7f;
						float fl = Math.max(Math.min(s - 0.6f, r + 0.2f - foff), prevF + 0.3f);
						skinAt[i] = s;
						fleshKey[i] = fl;
						prevS = s;
						prevF = fl;
						jo[i] = (AndyTexels.hash(x, r, z, seed + 3) - 0.5f) * 0.24f;
						// mostly the darker reds, a few bright ones
						float hc = AndyTexels.hash(x, r, z, seed + 5);
						rgb[i] = LIMB_FLESH[Math.min(LIMB_FLESH.length - 1, (int) (hc * hc * 1.25f * LIMB_FLESH.length))];
						if (r == h - 1) {
							capAt[i] = s + 0.9f + 1.1f * AndyTexels.hash(x, r, z, seed + 6);
							last = Math.max(last, capAt[i]);
						}
						last = Math.max(last, s);
						lastFlesh = Math.max(lastFlesh, fl);
					}
				}
			}
			List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
			texelAt = new float[texels.size()];
			float maxT = 0f;
			for (int k = 0; k < texelAt.length; k++) {
				AndyTexels.Texel t = texels.get(k);
				int xi = Math.max(0, Math.min(w - 1, (int) Math.floor(t.x - box[0])));
				int zi = Math.max(0, Math.min(d - 1, (int) Math.floor(t.z - box[2])));
				float at;
				if (t.ny < -0.5f) {
					at = skinAt[cell(xi, 0, zi)] - LIMB_SETTLE; // the joint end
				} else if (t.ny > 0.5f) {
					at = capAt[cell(xi, h - 1, zi)]; // the hand / foot end, last
				} else {
					int ri = Math.max(0, Math.min(h - 1, (int) Math.floor(t.y - box[1])));
					at = skinAt[cell(xi, ri, zi)];
				}
				if (t.overlay) {
					at += 0.35f;
				}
				texelAt[k] = at;
				maxT = Math.max(maxT, at);
			}
			done = Math.max(last, maxT) + LIMB_SETTLE + 0.05f;
			end = done + 0.05f;
			fleshEnd = lastFlesh + LIMB_SWELL + 0.1f;
		}
	}

	/** The texels of a limb whose skin front key lies in [{@code lo}, {@code hi}) rows -- what a cut from hi to lo took away. */
	static java.util.function.Predicate<AndyTexels.Texel> texelsBetween(int part, boolean slim, float lo, float hi) {
		Limb lb = Limb.of(part, slim);
		List<AndyTexels.Texel> texels = AndyTexels.of(part, slim);
		java.util.Set<AndyTexels.Texel> in = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
		for (int i = 0; i < texels.size(); i++) {
			float k = lb.texelAt[i];
			if (k >= lo && k < hi) {
				in.add(texels.get(i));
			}
		}
		return in::contains;
	}

	/**
	 * The head's skull / the body's ribcage ({@link VanillaSkeleton}), drawn in the player part's space, centred in it, and
	 * revealed from the joint outward over the bone window. Drawn a little smaller than the 8 px head / body so the muscle can
	 * close over them.
	 */
	private static void vanillaBone(PoseStack pose, MultiBufferSource buffers, int light, boolean slim, int part, float p) {
		float f = Math.max(0f, Math.min(1f, (p - BONE_START) / BONE_SPAN));
		if (f <= 0f) {
			return;
		}
		f = f * f * (3f - 2f * f);
		float y0, y1;
		pose.pushPose();
		if (part == AndyState.HEAD) {
			// from the neck up
			y0 = -8f * f;
			y1 = 0f;
			pose.translate(0f, -4f / 16f, 0f);
			pose.scale(SKULL_SCALE, SKULL_SCALE, SKULL_SCALE);
			pose.translate(0f, 4f / 16f, 0f);
		} else {
			// from the neck down
			y0 = 0f;
			y1 = 12f * f;
			pose.scale(RIBS_SCALE_X, 1f, RIBS_SCALE_Z);
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(VanillaSkeleton.TEXTURE));
		VanillaSkeleton.draw(pose.last(), vc, part, y0, y1, light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();
	}

	/** The skull and the ribcage are scaled in from the player's 8 px head / body so the flesh closes over them. */
	static final float SKULL_SCALE = 0.8f, RIBS_SCALE_X = 0.8f, RIBS_SCALE_Z = 0.7f;

	/** How long (share of the regrowth) one head / body muscle cell takes to thicken from a sheet on the bone to full. */
	static final float FLESH_GROW = 0.16f;

	private static float fleshF(Piece b, float p) {
		return (p - (FLESH_START + b.key * (FLESH_SPAN - FLESH_GROW))) / FLESH_GROW;
	}

	/** When a cell's muscle reaches full thickness (share of the regrowth). */
	private static float fleshDone(Piece b) {
		return FLESH_START + b.key * (FLESH_SPAN - FLESH_GROW) + FLESH_GROW;
	}

	/**
	 * A muscle cell part-grown: across its outward axes it spans {@code out} of its depth from its anchor (its face against the
	 * bone), along the others {@code along} of its size about its middle. All faces (a growing cell has no settled cover).
	 */
	private static void growPiece(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] c, Piece b, float along, float out, int light) {
		float sx = b.outX ? out : along, sy = b.outY ? out : along, sz = b.outZ ? out : along;
		float x0 = b.ax + (b.x0 - b.ax) * sx, x1 = b.ax + (b.x1 - b.ax) * sx;
		float y0 = b.ay + (b.y0 - b.ay) * sy, y1 = b.ay + (b.y1 - b.ay) * sy;
		float z0 = b.az + (b.z0 - b.az) * sz, z1 = b.az + (b.z1 - b.az) * sz;
		int rgb = b.rgb;
		if (out < 0.6f) {
			// fresh and wet: a shade darker until it has some thickness
			rgb = ((rgb >> 16 & 0xFF) * 3 / 4) << 16 | ((rgb >> 8 & 0xFF) * 3 / 4) << 8 | (rgb & 0xFF) * 3 / 4;
		}
		flatBox(vc, pose, x0, y0, z0, x1, y1, z1, rgb, light);
	}

	static float ease(float f) {
		float g = f - 1f;
		return 1f + 2.2f * g * g * g + 1.2f * g * g;
	}

	/** One bone / flesh box, scaled by {@code s} about its centre, faces in {@code mask} (-x, +x, -y, +y, -z, +z). */
	private static void piece(VertexConsumer vc, PoseStack.Pose pose, Vector3f[] c, Piece b, float s, int light, int mask) {
		float cx = (b.x0 + b.x1) * 0.5f, cy = (b.y0 + b.y1) * 0.5f, cz = (b.z0 + b.z1) * 0.5f;
		float hx = (b.x1 - b.x0) * 0.5f * s, hy = (b.y1 - b.y0) * 0.5f * s, hz = (b.z1 - b.z0) * 0.5f * s;
		for (int i = 0; i < 8; i++) {
			c[i].set((cx + ((i & 1) == 0 ? -hx : hx)) / 16f, (cy + ((i & 2) == 0 ? -hy : hy)) / 16f, (cz + ((i & 4) == 0 ? -hz : hz)) / 16f);
		}
		int r = (b.rgb >> 16) & 0xFF, g = (b.rgb >> 8) & 0xFF, bl = b.rgb & 0xFF;
		Matrix4f m = pose.pose();
		for (int f = 0; f < 6; f++) {
			if ((mask & (1 << f)) == 0) {
				continue;
			}
			int[] fi = BOX_FACES[f];
			float[] n = BOX_NORMALS[f];
			for (int k = 0; k < 4; k++) {
				Vector3f v = c[fi[k]];
				vc.addVertex(m, v.x, v.y, v.z).setColor(r, g, bl, 255).setUv(k == 1 || k == 2 ? WU1 : WU0, k >= 2 ? WV1 : WV0)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}

	// ---------------------------------------------------------------- the body cut (a boost burning it from the bottom up)

	/**
	 * The body with only its top {@code rows} rows (of 12, from the neck) left: the skin above a ragged line, the muscle
	 * grid's cubes at the line as the cut face (embers while burning).
	 */
	public static void bodyCut(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, float rows, boolean embers) {
		if (AndyRegrowStyle.stepped()) { // v0.17.3: the stepped look, picked per viewer (/andystyle)
			AndyRegrowStepped.bodyCut(pose, buffers, light, tex, slim, rows, embers);
			return;
		}
		VertexConsumer skin = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		PoseStack.Pose last = pose.last();
		for (AndyTexels.Texel t : AndyTexels.of(AndyState.BODY, slim)) {
			if (!SkinPixels.shows(tex, t.u, t.v)) {
				continue;
			}
			float line = rows + (AndyTexels.hash(Math.round(t.x * 2f), 0, Math.round(t.z * 2f), 99L) - 0.5f) * 1.4f;
			if (t.y < line || (t.ny < -0.5f)) {
				popIn(skin, last, t, light, 1f);
			}
		}
		Body body = Body.of(AndyState.BODY, slim);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		for (int i = 0; i < body.flesh.length; i++) {
			Piece b = body.flesh[i];
			if (b == null) {
				continue;
			}
			float cy = b.cy();
			float line = rows + (AndyTexels.hash(Math.round((b.x0 + b.x1)), 0, Math.round((b.z0 + b.z1)), 99L) - 0.5f) * 1.4f;
			if (cy > line - 1.4f && cy < line + 0.3f) {
				int rgb = b.rgb;
				int l = light;
				if (embers) {
					float heat = Math.max(0f, Math.min(1f, 1f - (line - cy) / 1.4f));
					rgb = EMBER[Math.max(0, Math.min(EMBER.length - 1, (int) (heat * EMBER.length)))];
					l = 0xF000F0;
				}
				flatBox(vc, last, b.x0, b.y0, b.z0, b.x1, b.y1, b.z1, rgb, l);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	private static Vector3f[] corners8() {
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f();
		}
		return c;
	}

	/** One placed texel square: {@code out} px proud, grown by {@code grow} px (negative: smaller), shade 0..255. */
	private static void square(VertexConsumer vc, PoseStack.Pose pose, AndyTexels.Texel t, float u0, float v0, float u1, float v1, int light, float out,
			float grow, int shade) {
		float[][] p = AndyRender.cornersPublic(t, out, grow);
		Matrix4f m = pose.pose();
		for (int i = 0; i < 4; i++) {
			float uu = (i == 1 || i == 2) ? u1 : u0;
			float vv = (i >= 2) ? v1 : v0;
			vc.addVertex(m, p[i][0] / 16f, p[i][1] / 16f, p[i][2] / 16f).setColor(shade, shade, shade, 255).setUv(uu, vv)
					.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, t.nx, t.ny, t.nz);
		}
	}

	// ---------------------------------------------------------------- the muscle of the head and body

	/** A coloured box in the part's space with its turn ({@code key} 0..1) within its layer. */
	static final class Piece {
		final float x0, y0, z0, x1, y1, z1;
		final int rgb;
		float key;
		/** Where it grows from (its point nearest the bone) and along which axes it thickens outward from there. */
		float ax, ay, az;
		boolean outX, outY, outZ;

		Piece(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
			this.x0 = Math.min(x0, x1);
			this.y0 = Math.min(y0, y1);
			this.z0 = Math.min(z0, z1);
			this.x1 = Math.max(x0, x1);
			this.y1 = Math.max(y0, y1);
			this.z1 = Math.max(z0, z1);
			this.rgb = rgb;
		}

		float cy() {
			return (y0 + y1) * 0.5f;
		}
	}

	private static final int[] FLESH = { 0x4C060C, 0x5E0910, 0x700C15, 0x84111B, 0x9A1A24 };

	/** The flesh grid of one part (cached). */
	static final class Body {
		Piece[] flesh;
		int gw, gh, gd;

		private static final Body[][] CACHE = new Body[2][AndyState.PARTS];

		static Body of(int part, boolean slim) {
			int s = slim && (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) ? 1 : 0;
			Body b = CACHE[s][part];
			if (b == null) {
				b = new Body();
				b.make(part, slim);
				CACHE[s][part] = b;
			}
			return b;
		}

		int neighbour(int i, int face) {
			int x = i % gw, y = (i / gw) % gh, z = i / (gw * gh);
			switch (face) {
				case 0 -> x--;
				case 1 -> x++;
				case 2 -> y--;
				case 3 -> y++;
				case 4 -> z--;
				default -> z++;
			}
			if (x < 0 || y < 0 || z < 0 || x >= gw || y >= gh || z >= gd) {
				return -1;
			}
			return x + y * gw + z * gw * gh;
		}

		/** 0 at the joint, 1 at the far end, for a y in the part's space. */
		private static float along(int part, float[] box, float y) {
			float a = switch (part) {
				case AndyState.HEAD -> -y / 8f;
				case AndyState.RIGHT_ARM, AndyState.LEFT_ARM -> (y - box[1]) / box[4];
				default -> y / box[4];
			};
			return Math.max(0f, Math.min(1f, a));
		}

		private void make(int part, boolean slim) {
			fleshGrid(part, AndyTexels.box(part, slim), slim);
		}

		/**
		 * One flesh cell per px of the part, each filling its 1 px cell exactly (no gaps between neighbours), the outer faces
		 * {@link #MUSCLE_INSET} under the skin; skipping where the bone runs (it shows between).
		 */
		private void fleshGrid(int part, float[] box, boolean slim) {
			gw = (int) box[3];
			gh = (int) box[4];
			gd = (int) box[5];
			flesh = new Piece[gw * gh * gd];
			float inset = MUSCLE_INSET;
			float cx = box[0] + box[3] * 0.5f, cz = box[2] + box[5] * 0.5f;
			long seed = 77L + part * 13L;
			java.util.Random rnd = new java.util.Random(seed);
			for (int z = 0; z < gd; z++) {
				for (int y = 0; y < gh; y++) {
					for (int x = 0; x < gw; x++) {
						float mx = box[0] + x + 0.5f, my = box[1] + y + 0.5f, mz = box[2] + z + 0.5f;
						float x0 = Math.max(box[0] + x, box[0] + inset), x1 = Math.min(box[0] + x + 1f, box[0] + box[3] - inset);
						float y0 = Math.max(box[1] + y, box[1] + inset), y1 = Math.min(box[1] + y + 1f, box[1] + box[4] - inset);
						float z0 = Math.max(box[2] + z, box[2] + inset), z1 = Math.min(box[2] + z + 1f, box[2] + box[5] - inset);
						if (keepOut(part, mx, my, mz, cx, cz, slim)) {
							continue;
						}
						// muscle: darker deep, fibres along the part, a little noise
						float fib = AndyTexels.noise(mx * 0.9f, my * 0.22f, mz * 0.9f, seed);
						float dx = (mx - cx) / (box[3] * 0.5f), dz = (mz - cz) / (box[5] * 0.5f);
						float radial = Math.min(1f, (float) Math.sqrt(dx * dx + dz * dz) / 1.414f);
						if (part == AndyState.HEAD) {
							float dy = (my + 4f) / 4f;
							radial = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 1.732f);
						}
						float band = AndyTexels.hash(Math.round(mx * 1.3f), 0, Math.round(mz * 1.3f), seed + 9) * 0.9f;
						int shade = Math.max(0, Math.min(FLESH.length - 1, (int) (fib * 2.6f + band * 1.6f + radial * 1.2f - 0.6f)));
						int rgb = FLESH[shade];
						if (rnd.nextFloat() < 0.05f) {
							rgb = 0xB8545C; // a pale streak of sinew
						}
						Piece c = new Piece(x0, y0, z0, x1, y1, z1, rgb);
						// v0.17.3 (user: "the muscle builds from the inside out -- outward from the ribcage / skull, then the skin
						// covers it"): each cell grows from its point nearest the bone, and its turn is how far it lies from the bone
						float[] bb = boneBox(part);
						float qx = Math.max(bb[0], Math.min(bb[3], mx)), qy = Math.max(bb[1], Math.min(bb[4], my)), qz = Math.max(bb[2], Math.min(bb[5], mz));
						c.ax = Math.max(c.x0, Math.min(c.x1, qx));
						c.ay = Math.max(c.y0, Math.min(c.y1, qy));
						c.az = Math.max(c.z0, Math.min(c.z1, qz));
						c.outX = qx != mx;
						c.outY = qy != my;
						c.outZ = qz != mz;
						float dist = (float) Math.sqrt((mx - qx) * (mx - qx) + (my - qy) * (my - qy) + (mz - qz) * (mz - qz));
						c.key = dist; // normalised below
						flesh[x + y * gw + z * gw * gh] = c;
					}
				}
			}
			// the turn: nearest the bone first, thickening outward (the edges and corners of the part last); a little noise so
			// it is not a clean sweep; the torso's shoulders a touch ahead so the arms can start
			float maxD = 1e-3f;
			for (Piece c : flesh) {
				if (c != null) {
					maxD = Math.max(maxD, c.key);
				}
			}
			for (Piece c : flesh) {
				if (c == null) {
					continue;
				}
				float mx = (c.x0 + c.x1) * 0.5f, my = c.cy(), mz = (c.z0 + c.z1) * 0.5f;
				float rn = c.key / maxD;
				float jitter = AndyTexels.noise(mx * 0.7f + 11f, my * 0.7f, mz * 0.7f, seed + 5) * 0.22f + rnd.nextFloat() * 0.06f;
				float a = along(part, box, my);
				float k;
				if (part == AndyState.BODY) {
					float shoulder = Math.max(0f, 1f - my / 3.5f) * (Math.abs(mx) > 2f ? 1f : 0.5f);
					k = 0.70f * rn + 0.12f * a + 0.55f * jitter - 0.14f * shoulder;
				} else {
					k = 0.78f * rn + 0.08f * a + 0.55f * jitter - 0.03f;
				}
				c.key = k;
			}
			// spread over the whole muscle window
			float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
			for (Piece c : flesh) {
				if (c != null) {
					lo = Math.min(lo, c.key);
					hi = Math.max(hi, c.key);
				}
			}
			for (Piece c : flesh) {
				if (c != null) {
					c.key = Math.max(0f, Math.min(1f, (c.key - lo) / Math.max(1e-3f, hi - lo)));
				}
			}
		}

		/** The box (min x, y, z, max x, y, z) the bone fills that the muscle grows out from: the ribcage, the skull. */
		private static float[] boneBox(int part) {
			return part == AndyState.HEAD ? new float[] { -3.0f, -7.0f, -3.0f, 3.0f, -1.0f, 3.0f }
					: new float[] { -3.0f, -1f, -1.2f, 3.0f, 13f, 1.2f };
		}

		private final java.util.Map<List<AndyTexels.Texel>, float[]> skinAfter = new java.util.IdentityHashMap<>();

		/**
		 * Per texel of {@code texels}: the earliest its skin may close (share of the regrowth) -- once the muscle cell under it
		 * is full thickness (0 where no muscle lies under it).
		 */
		float[] skinAfter(List<AndyTexels.Texel> texels) {
			float[] r = skinAfter.get(texels);
			if (r != null) {
				return r;
			}
			r = new float[texels.size()];
			float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
			for (Piece c : flesh) {
				if (c != null) {
					x0 = Math.min(x0, c.x0);
					y0 = Math.min(y0, c.y0);
					z0 = Math.min(z0, c.z0);
					x1 = Math.max(x1, c.x1);
					y1 = Math.max(y1, c.y1);
					z1 = Math.max(z1, c.z1);
				}
			}
			for (int k = 0; k < r.length; k++) {
				AndyTexels.Texel t = texels.get(k);
				// a point just under the texel, clamped into the grid
				float px = Math.max(x0 + 0.01f, Math.min(x1 - 0.01f, t.x - t.nx * 0.6f));
				float py = Math.max(y0 + 0.01f, Math.min(y1 - 0.01f, t.y - t.ny * 0.6f));
				float pz = Math.max(z0 + 0.01f, Math.min(z1 - 0.01f, t.z - t.nz * 0.6f));
				float best = 0f;
				for (Piece c : flesh) {
					if (c != null && px >= c.x0 - 0.3f && px <= c.x1 + 0.3f && py >= c.y0 - 0.3f && py <= c.y1 + 0.3f && pz >= c.z0 - 0.3f
							&& pz <= c.z1 + 0.3f) {
						best = Math.max(best, fleshDone(c)); // (and the neighbours it overlaps: no muscle edge shows past the skin)
					}
				}
				r[k] = best;
			}
			skinAfter.put(texels, r);
			return r;
		}

		/** Where the bone runs (no flesh there, so the bone shows until the flesh round it closes over). */
		private static boolean keepOut(int part, float x, float y, float z, float cx, float cz, boolean slim) {
			return switch (part) {
				case AndyState.HEAD -> Math.abs(x) < 3.0f && Math.abs(z) < 3.0f && y > -7.0f && y < -1.0f;
				case AndyState.BODY -> Math.abs(x) < 3.0f && Math.abs(z) < 1.2f; // inside the scaled-in vanilla ribcage
				default -> Math.abs(x - cx) < (slim ? 0.8f : 1.0f) && Math.abs(z - cz) < 1.0f;
			};
		}
	}
}
