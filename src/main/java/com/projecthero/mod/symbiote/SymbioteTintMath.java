package com.projecthero.mod.symbiote;

/**
 * v0.17.5: the Symbiote suit's colour maths -- one texel of a tint mask ({@code textures/armor/symbiote_host*_tint.png}: R shade,
 * G gloss, B channel, A coverage) to an RGB in the player's colours. Moved out of the client's {@code SymbioteSuitTint} (which calls
 * it) so the gametests can check it.
 *
 * <p>Channels (mask B): {@code >= 192} eyes, {@code >= 64} emblem, {@code >= 16} mouth (tongue, throat, the floor of the mouth),
 * else body.
 *
 * <p>Shade 0.5 is the chosen colour exactly; darker shades scale it toward black.
 * <ul>
 *   <li><b>Body and mouth</b>: shades above 0.5 lift toward a pale tint of the hue and the gloss lays a near-white of the hue on top,
 *       so a black suit still has wet highlights and a white one keeps its muscle shading (unchanged from the first colour build).</li>
 *   <li><b>Emblem and eyes</b> (user: "you cant make the emblem fully black or the eyes fully black ... make sure the symbiote suit is
 *       fully customizable"): their texels sit at shade 1.0 with some gloss, and the lift + near-white gloss turned black into a light
 *       grey -- no dark emblem or eyes were possible. Now a shade above 0.5 is the chosen colour itself (never lifted) and the gloss
 *       only brightens in proportion to the colour's own brightness ({@code c + (1 - c) * 0.3 * max}), so pure black is pure black,
 *       pure white is pure white and every RGB in between comes out as picked.</li>
 * </ul>
 */
public final class SymbioteTintMath {
	public static final int BODY = 0;
	public static final int EMBLEM = 1;
	public static final int EYES = 2;
	public static final int MOUTH = 3;
	/** The shade that is the chosen colour exactly: mask value 128 (the 8-bit "0.5"), so a texel at 128 bakes to the exact RGB. */
	private static final float MID = 128f / 255f;

	private SymbioteTintMath() {
	}

	/** Which colour a mask texel's B value selects ({@link #BODY} .. {@link #MOUTH}, the same order as {@link SymbioteColours#channel}). */
	public static int channelOf(int maskBlue) {
		return maskBlue >= 192 ? EYES : maskBlue >= 64 ? EMBLEM : maskBlue >= 16 ? MOUTH : BODY;
	}

	/** One mask texel ({@code shade}, {@code gloss}, {@code maskBlue}, {@code alpha}, each 0..255) to {@code 0xRRGGBB} (0 when transparent). */
	public static int bake(int shade, int gloss, int maskBlue, int alpha, SymbioteColours colours) {
		if (alpha == 0) {
			return 0;
		}
		int kind = channelOf(maskBlue);
		int base = colours.channel(kind);
		float[] c = { ((base >> 16) & 0xFF) / 255f, ((base >> 8) & 0xFF) / 255f, (base & 0xFF) / 255f };
		float s = shade / 255f;
		float g = gloss / 255f;
		float m = Math.max(c[0], Math.max(c[1], c[2]));
		boolean exact = kind == EMBLEM || kind == EYES;
		float[] hue = m < 0.03f ? new float[] { 0.82f, 0.86f, 1.0f } : new float[] { c[0] / m, c[1] / m, c[2] / m };
		float[] o = new float[3];
		for (int i = 0; i < 3; i++) {
			if (s <= MID) {
				o[i] = c[i] * (s / MID);
			} else if (exact) {
				o[i] = c[i];
			} else {
				float t = (s - MID) / (1f - MID);
				float lift = (0.5f + 0.5f * hue[i]) * 0.86f;
				o[i] = c[i] + (Math.max(lift, c[i]) - c[i]) * t;
			}
			float spec = exact ? c[i] + (1f - c[i]) * 0.3f * m : 0.72f + 0.28f * hue[i];
			o[i] = o[i] + (Math.max(spec, o[i]) - o[i]) * g;
		}
		return Math.round(clamp(o[0]) * 255f) << 16 | Math.round(clamp(o[1]) * 255f) << 8 | Math.round(clamp(o[2]) * 255f);
	}

	/**
	 * The additive eye-glow strength (0..1) for an eye colour: 40% for a bright colour, fading to nothing for a dark one, so the glow
	 * never brightens dark eyes past the colour that was picked (it only helps light eyes read at night).
	 */
	public static float eyeGlowStrength(int eyes) {
		float m = Math.max(((eyes >> 16) & 0xFF), Math.max(((eyes >> 8) & 0xFF), (eyes & 0xFF))) / 255f;
		float t = clamp((m - 0.35f) / 0.4f);
		return 0.4f * t * t * (3f - 2f * t);
	}

	private static float clamp(float v) {
		return Math.max(0f, Math.min(1f, v));
	}
}
