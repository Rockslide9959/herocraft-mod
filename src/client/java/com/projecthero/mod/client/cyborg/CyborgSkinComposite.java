package com.projecthero.mod.client.cyborg;

import java.util.Arrays;

/**
 * v0.17.7 (user: "the cyborg head looks too big ... make it normal sized and only make the players original skin poke out on
 * the parts that arent a cyborg"): Cyborg is no longer drawn as a larger box over the player. Each Cyborg player gets ONE
 * player skin made texel by texel out of his own skin and Cyborg's ({@link CyborgSkins}), drawn by vanilla on the normal
 * player model, so nothing of Cyborg is bigger than the player's own layers.
 *
 * <p>Pure pixel maths (no Minecraft classes) so the offline preview harness runs this exact code. Pixels are packed ints with
 * alpha in the top byte; the channel order of the rest does not matter except for {@code tint}, which must use the same order
 * as the pixels.
 *
 * <p>Per base-layer texel (and the second-layer texel over it), {@code mode}:
 * <ul>
 * <li>{@link #CYBORG}: base = Cyborg's base where it is opaque, else the player's base (covered anyway if only Cyborg's outer
 * texel is there). Outer = Cyborg's outer where opaque; else clear where Cyborg's base is opaque (the player's hair / jacket
 * never covers Cyborg's metal); else the player's own outer texel.</li>
 * <li>{@link #PLAIN}: the player's own skin, both layers (the hologram, or where the Mother Box hasn't spread yet).</li>
 * <li>{@link #FLESH}: the raw flesh over the base where it is opaque (outer cleared there), else the player's skin.</li>
 * </ul>
 */
public final class CyborgSkinComposite {
	private CyborgSkinComposite() {
	}

	public static final byte PLAIN = 0, CYBORG = 1, FLESH = 2;

	/** Second-layer part bits (hat, jacket, sleeves, trousers), for the player's skin-customisation options. */
	public static final int HAT = 1, JACKET = 2, RIGHT_SLEEVE = 4, LEFT_SLEEVE = 8, RIGHT_PANTS = 16, LEFT_PANTS = 32, ALL_PARTS = 63;

	/** base u, v, overlay u, v, w (-1 = arm: 4 wide / 3 slim), h, d, overlay part bit. */
	private static final int[][] CUBES = {
			{ 0, 0, 32, 0, 8, 8, 8, HAT },
			{ 16, 16, 16, 32, 8, 12, 4, JACKET },
			{ 40, 16, 40, 32, -1, 12, 4, RIGHT_SLEEVE },
			{ 32, 48, 48, 48, -1, 12, 4, LEFT_SLEEVE },
			{ 0, 16, 0, 32, 4, 12, 4, RIGHT_PANTS },
			{ 16, 48, 0, 48, 4, 12, 4, LEFT_PANTS } };

	private static final int OPAQUE = 25;

	private static int[][] overlayMap = new int[2][], partMap = new int[2][];

	/** For every base-layer texel (index v * 64 + u) the second-layer texel over it, or -1 for texels that aren't on a body part. */
	public static int[] overlayMap(boolean slim) {
		int s = slim ? 1 : 0;
		if (overlayMap[s] == null) {
			int[] map = new int[64 * 64], part = new int[64 * 64];
			Arrays.fill(map, -1);
			for (int[] c : CUBES) {
				int w = c[4] < 0 ? (slim ? 3 : 4) : c[4], h = c[5], d = c[6];
				int du = c[2] - c[0], dv = c[3] - c[1];
				for (int y = c[1]; y < c[1] + d; y++) {
					for (int x = c[0] + d; x < c[0] + d + 2 * w; x++) {
						map[y * 64 + x] = (y + dv) * 64 + x + du;
						part[y * 64 + x] = c[7];
					}
				}
				for (int y = c[1] + d; y < c[1] + d + h; y++) {
					for (int x = c[0]; x < c[0] + 2 * d + 2 * w; x++) {
						map[y * 64 + x] = (y + dv) * 64 + x + du;
						part[y * 64 + x] = c[7];
					}
				}
			}
			overlayMap[s] = map;
			partMap[s] = part;
		}
		return overlayMap[s];
	}

	/** The second-layer part bit of every base-layer texel (0 off the body). */
	public static int[] partMap(boolean slim) {
		overlayMap(slim);
		return partMap[slim ? 1 : 0];
	}

	public static boolean opaque(int px) {
		return (px >>> 24) > OPAQUE;
	}

	/**
	 * @param plain  the player's skin, {@code n} x {@code n} ({@code n} a multiple of 64)
	 * @param cyborg Cyborg's skin, 64 x 64, in the player's arm layout
	 * @param flesh  the raw flesh, 64 x 64 in the player's arm layout (only read for {@link #FLESH} texels; may be null otherwise)
	 * @param shown  the player's shown second-layer parts ({@link #HAT} ...); hidden ones stay clear where the player's skin shows
	 * @param mode   per base texel (64 x 64), {@link #PLAIN} / {@link #CYBORG} / {@link #FLESH}
	 * @param tint   per base texel colour multiplied into both of its layers, or null (white)
	 * @param out    {@code n} x {@code n} result
	 */
	public static void compose(int[] plain, int n, int[] cyborg, int[] flesh, boolean slim, int shown, byte[] mode, int[] tint, int[] out) {
		int s = n / 64;
		int[] map = overlayMap(slim), parts = partMap(slim);
		System.arraycopy(plain, 0, out, 0, n * n);
		for (int b = 0; b < 64 * 64; b++) {
			int ov = map[b];
			if (ov < 0) {
				continue;
			}
			int bu = b & 63, bv = b >> 6, ou = ov & 63, ovv = ov >> 6;
			boolean showOuter = (shown & parts[b]) != 0;
			byte m = mode[b];
			boolean cyBase = opaque(cyborg[b]), cyOuter = opaque(cyborg[ov]);
			boolean fleshBase = m == FLESH && flesh != null && opaque(flesh[b]);
			int t = tint == null ? -1 : tint[b];
			for (int sy = 0; sy < s; sy++) {
				for (int sx = 0; sx < s; sx++) {
					int pb = (bv * s + sy) * n + bu * s + sx, po = (ovv * s + sy) * n + ou * s + sx;
					int base = plain[pb], outer = showOuter ? plain[po] : 0;
					if (m == CYBORG) {
						if (cyBase) {
							base = cyborg[b];
						}
						outer = cyOuter ? cyborg[ov] : cyBase ? 0 : outer;
					} else if (fleshBase) {
						base = flesh[b];
						outer = 0;
					}
					out[pb] = mul(base, t);
					out[po] = mul(outer, t);
				}
			}
		}
	}

	/** Multiplies every channel of {@code px} by {@code tint}'s (same packing); -1 (white) leaves it. */
	public static int mul(int px, int tint) {
		if (tint == -1 || px == 0) {
			return px;
		}
		int r = 0;
		for (int sh = 0; sh < 32; sh += 8) {
			r |= ((((px >>> sh) & 0xFF) * ((tint >>> sh) & 0xFF) + 127) / 255) << sh;
		}
		return r;
	}

	/**
	 * A wide-arm 64x64 skin remapped to the slim (3-pixel arm) layout: the arm column against the body goes from the front,
	 * back, top and bottom faces (the same remap {@code scratchpad/v0177/make_cyborg_skin.js} made {@code cyborg_slim.png} with).
	 */
	public static int[] slimOf(int[] wide) {
		int[] out = wide.clone();
		int[][] arms = { { 40, 16, 1 }, { 40, 32, 1 }, { 32, 48, 0 }, { 48, 48, 0 } };
		for (int[] a : arms) {
			int u = a[0], v = a[1];
			boolean right = a[2] == 1;
			for (int y = v; y < v + 16; y++) {
				for (int x = u; x < u + 16; x++) {
					out[y * 64 + x] = 0;
				}
			}
			int dropFront = right ? 3 : 0, dropBack = right ? 0 : 3, dropTop = right ? 3 : 0;
			for (int y = v; y < v + 4; y++) {
				for (int c = 0, i = 0; c < 4; c++) {
					if (c != dropTop) {
						out[y * 64 + u + 4 + i] = wide[y * 64 + u + 4 + c];
						out[y * 64 + u + 7 + i] = wide[y * 64 + u + 8 + c];
						i++;
					}
				}
			}
			for (int y = v + 4; y < v + 16; y++) {
				for (int i = 0; i < 4; i++) {
					out[y * 64 + u + i] = wide[y * 64 + u + i];
					out[y * 64 + u + 7 + i] = wide[y * 64 + u + 8 + i];
				}
				for (int c = 0, i = 0; c < 4; c++) {
					if (c != dropFront) {
						out[y * 64 + u + 4 + i++] = wide[y * 64 + u + 4 + c];
					}
				}
				for (int c = 0, i = 0; c < 4; c++) {
					if (c != dropBack) {
						out[y * 64 + u + 11 + i++] = wide[y * 64 + u + 12 + c];
					}
				}
			}
		}
		return out;
	}

	/** Nearest-neighbour resample of a {@code w} x {@code h} image to {@code n} x {@code n} (odd-sized skins from other mods). */
	public static int[] resample(int[] src, int w, int h, int n) {
		int[] out = new int[n * n];
		for (int y = 0; y < n; y++) {
			for (int x = 0; x < n; x++) {
				out[y * n + x] = src[(y * h / n) * w + x * w / n];
			}
		}
		return out;
	}
}
