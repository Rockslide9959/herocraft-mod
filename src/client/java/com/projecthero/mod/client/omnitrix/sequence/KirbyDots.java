package com.projecthero.mod.client.omnitrix.sequence;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;

import static com.projecthero.mod.client.omnitrix.sequence.ShowMath.hash;

/**
 * v0.17.3 (user: "for the background of the transformation sequences make the kirby dots better, in the show there are lines of tons
 * of clumped together kirby dots in every frame and angle, make ours look like that"): the show's Kirby-dot field, for any sequence's
 * stage. Studied from the compilation video: in nearly every shot the olive / green field is crossed by a few broad STREAMS of black
 * dots -- solid where the dots crowd together, breaking up toward the stream's edges into clumps and then single dots of every size --
 * sweeping across the frame in long, gently curved bands, all roughly the same way within a shot; the edges and corners of the frame
 * are crowded with bigger clumps (a dotty vignette), and the middle, where the body is, stays mostly clear.
 * <p>
 * Drawn in screen space on the {@link Stage}, so it fills the frame from any camera angle. Deterministic per seed: pass
 * {@code stage.shotSeed(track.shot(t))} so each shot gets its own layout (like the show's cuts) and keeps it while it flows. Every dot
 * drifts along its stream ({@link Style#flow}) and "boils" -- a tiny re-jitter every two ticks, like hand-drawn frames.
 * <pre>
 *   KirbyDots.draw(st, st.shotSeed(TRACK.shot(t)), t - TRACK.shotStart(t), KirbyDots.Style.show());
 *   KirbyDots.draw(st, seed, t, KirbyDots.Style.show().bands(2).angle(30).rgb(0x0D3F0B).clear(0.45f));
 * </pre>
 */
public final class KirbyDots {
	private KirbyDots() {
	}

	/** The look of a field. Start from {@link #show()} and change what the shot needs. */
	public static final class Style {
		int rgb = 0x050505;
		int alpha = 245;
		int bands = 3;
		float density = 1f, size = 1f, width = 1f, rim = 1f, clear = 0.34f, flow = 1f, angle = Float.NaN, spread = 22f, bend = 1f, popIn = 0f;

		/** The show's default: three black streams, a dotty rim (heavier on one side), the middle thinned out. */
		public static Style show() {
			return new Style();
		}

		public Style copy() {
			Style s = new Style();
			s.rgb = rgb;
			s.alpha = alpha;
			s.bands = bands;
			s.density = density;
			s.size = size;
			s.width = width;
			s.rim = rim;
			s.clear = clear;
			s.flow = flow;
			s.angle = angle;
			s.spread = spread;
			s.bend = bend;
			s.popIn = popIn;
			return s;
		}

		/** The dots' colour (default near-black). */
		public Style rgb(int rgb) {
			this.rgb = rgb;
			return this;
		}

		/** The dots' opacity 0-255 (default 245; overlapping dots go solid). */
		public Style alpha(int alpha) {
			this.alpha = alpha;
			return this;
		}

		/** How many streams cross the frame (0 = only the rim). */
		public Style bands(int bands) {
			this.bands = bands;
			return this;
		}

		/** Clumps per length of stream / rim (1 = the show's). */
		public Style density(float density) {
			this.density = density;
			return this;
		}

		/** Dot size (1 = the show's). */
		public Style size(float size) {
			this.size = size;
			return this;
		}

		/** Stream thickness (1 = the show's). */
		public Style width(float width) {
			this.width = width;
			return this;
		}

		/** The edge and corner clumps (0 = none, 1 = the show's). */
		public Style rim(float rim) {
			this.rim = rim;
			return this;
		}

		/** Radius (screen heights) of the thinned-out middle round the body (0 = dots everywhere). */
		public Style clear(float clear) {
			this.clear = clear;
			return this;
		}

		/** Drift speed along the streams (1 = the show's; negative = the other way; 0 = still, boiling only). */
		public Style flow(float flow) {
			this.flow = flow;
			return this;
		}

		/** The streams' direction on screen in degrees (0 = across, 90 = up and down); unset = picked per shot from the seed. */
		public Style angle(float degrees) {
			this.angle = degrees;
			return this;
		}

		/** How far (degrees) each stream may turn from that direction. */
		public Style spread(float degrees) {
			this.spread = degrees;
			return this;
		}

		/** How much the streams curve (0 = straight). */
		public Style bend(float bend) {
			this.bend = bend;
			return this;
		}

		/** The dots pop in over this many ticks from {@code t} = 0 (0 = already there, as after a cut). */
		public Style popIn(float ticks) {
			this.popIn = ticks;
			return this;
		}
	}

	/** The show's default field. */
	public static void draw(Stage st, long seed, float t) {
		draw(st, seed, t, Style.show());
	}

	/** A field for {@code seed} at {@code t} ticks into the shot. */
	public static void draw(Stage st, long seed, float t, Style s) {
		Field f = new Field(st, seed, t, s);
		for (int i = 0; i < s.bands; i++) {
			f.band(i);
		}
		if (s.rim > 0f) {
			f.rim();
		}
	}

	/** One frame's drawing state. */
	private static final class Field {
		final Stage st;
		final VertexConsumer vc;
		final PoseStack.Pose p;
		final long seed;
		final float t, aspect;
		final int frame;
		final Style s;

		Field(Stage st, long seed, float t, Style s) {
			this.st = st;
			this.vc = st.dots();
			this.p = st.last();
			this.seed = seed;
			this.t = t;
			this.s = s;
			this.aspect = st.aspect();
			this.frame = (int) Math.floor(t / 2f);
		}

		/** One stream: a long, gently curved band of clumps, crowded and big in the middle, breaking into small dots at its edges. */
		void band(int i) {
			long bs = seed * 31L + i * 977L + 5L;
			float base = Float.isNaN(s.angle) ? hash(seed, 1) * Mth.PI : s.angle * Mth.DEG_TO_RAD;
			float theta = base + (hash(bs, 2) - 0.5f) * 2f * s.spread * Mth.DEG_TO_RAD;
			float dx = Mth.cos(theta), dy = Mth.sin(theta), px = -dy, py = dx;
			// spread the streams across the frame, a little uneven
			float off = s.bands <= 1 ? (hash(bs, 3) - 0.5f) * 1.2f : Mth.lerp((i + 0.5f) / s.bands, -1.15f, 1.15f) + (hash(bs, 3) - 0.5f) * 0.4f;
			float half = (float) Math.sqrt(aspect * aspect + 1f) + 0.45f;
			float bendK = s.bend * (hash(bs, 4) - 0.5f) * 1.1f;
			float w = (0.18f + 0.24f * hash(bs, 5)) * s.width;
			// every stream of a shot drifts the same way, at its own pace
			float dir = hash(seed, 7) < 0.5f ? 1f : -1f;
			float speed = (0.0055f + 0.0045f * hash(bs, 6)) * s.flow * dir;
			float step = 0.028f / Math.max(0.05f, s.density);
			int n = (int) (2f * half / step);
			for (int j = 0; j < n; j++) {
				int id = i * 4096 + j;
				float u = -half + (j + hash(bs, j * 11 + 20)) * step + speed * t;
				u = u - 2f * half * (float) Math.floor((u + half) / (2f * half));
				// across the stream: crowded in the middle (a triangular spread), out to 1.25 widths
				float v = (hash(bs, j * 11 + 21) + hash(bs, j * 11 + 22) - 1f) * w * 1.25f;
				float core = Mth.clamp(1f - Math.abs(v) / (w * 1.25f), 0f, 1f);
				float bow = bendK * ((u / half) * (u / half) - 0.35f);
				float x = dx * u + px * (off + bow + v), y = dy * u + py * (off + bow + v);
				// big and overlapping (solid) in the middle of the stream, small at its edges
				float big = s.size * (0.016f + 0.062f * (float) Math.pow(core, 1.5) + 0.014f * hash(bs, j * 11 + 23));
				clump(bs, id, x, y, big, 1 + (int) (hash(bs, j * 11 + 24) * 4.99f));
				// the spray off the stream's edges: small dots further out, smaller the further they flew
				for (int q = 0; q < 2; q++) {
					if (hash(bs, j * 11 + 25 + q * 100) < 0.38f) {
						float side = hash(bs, j * 11 + 26 + q * 100) < 0.5f ? -1f : 1f;
						float far = hash(bs, j * 11 + 27 + q * 100);
						float vv = side * w * (1.3f + 1.6f * far);
						float sx = dx * u + px * (off + bow + vv), sy = dy * u + py * (off + bow + vv);
						dot(id * 11 + 7 + q, sx, sy, s.size * (0.011f + 0.02f * (1f - far) * hash(bs, j * 11 + 28 + q * 100)));
					}
				}
			}
		}

		/** The dotty vignette: big clumps crowding the frame's edges, biggest in the corners, smaller further in. */
		void rim() {
			long rs = seed * 131L + 77L;
			float a = aspect;
			float per = 2f * (2f * a + 2f);
			float step = 0.05f / Math.max(0.05f, s.density);
			int n = (int) (per / step);
			// one side of the frame (per shot) is heavier, like the show's lopsided masses
			float heavy = hash(seed, 9) * Mth.TWO_PI, hx = Mth.cos(heavy), hy = Mth.sin(heavy);
			float wob = 0.012f * Mth.sin(t * 0.07f);
			for (int j = 0; j < n; j++) {
				float sAlong = (j + hash(rs, j * 7 + 1)) * step;
				float ex, ey, nx, ny;
				if (sAlong < 2f * a) {
					ex = -a + sAlong;
					ey = 1f;
					nx = 0f;
					ny = -1f;
				} else if (sAlong < 2f * a + 2f) {
					ex = a;
					ey = 1f - (sAlong - 2f * a);
					nx = -1f;
					ny = 0f;
				} else if (sAlong < 4f * a + 2f) {
					ex = a - (sAlong - 2f * a - 2f);
					ey = -1f;
					nx = 0f;
					ny = 1f;
				} else {
					ex = -a;
					ey = -1f + (sAlong - 4f * a - 2f);
					nx = 1f;
					ny = 0f;
				}
				// the sides more than the top and bottom, like the show's frames
				float sideK = nx != 0f ? 1f : 0.7f;
				float hk = 1f + 1.2f * Math.max(0f, -(nx * hx + ny * hy));
				float depth = Math.abs(hash(rs, j * 7 + 2) + hash(rs, j * 7 + 3) - 1f) * 0.32f * s.rim * sideK * hk;
				float x = ex + nx * depth + (nx == 0f ? wob : 0f), y = ey + ny * depth + (ny == 0f ? wob : 0f);
				float big = s.size * s.rim * (0.02f + 0.055f * Math.max(0f, 1f - depth / (0.32f * s.rim * hk)) * hash(rs, j * 7 + 4) + 0.015f * hk) * sideK;
				clump(rs, 100_000 + j, x, y, big, 1 + (int) (hash(rs, j * 7 + 5) * 3.99f));
			}
			// the corners: the heaviest masses
			for (int c = 0; c < 4; c++) {
				float cx = (c & 1) == 0 ? -a : a, cy = (c & 2) == 0 ? -1f : 1f;
				float big = s.size * s.rim * (0.1f + 0.07f * hash(rs, 900 + c));
				clump(rs, 200_000 + c, cx, cy, big, 5);
			}
		}

		/** A clump: one dot of radius {@code big} and {@code kids} smaller ones bubbling off it. */
		void clump(long cs, int id, float x, float y, float big, int kids) {
			dot(id * 11, x, y, big);
			for (int b = 1; b <= kids; b++) {
				float r = big * (0.3f + 0.45f * hash(cs, id * 13 + b * 3));
				float a2 = hash(cs, id * 13 + b * 3 + 1) * Mth.TWO_PI;
				float dist = big + r * (0.1f + 0.6f * hash(cs, id * 13 + b * 3 + 2));
				dot(id * 11 + b, x + Mth.cos(a2) * dist, y + Mth.sin(a2) * dist, r);
			}
		}

		/** One dot at {@code x y} (screen heights from the centre), thinned toward the middle, boiling, culled off screen. */
		void dot(int id, float x, float y, float r) {
			if (s.clear > 0f) {
				float dc = (float) Math.sqrt(x * x * 0.55f + y * y);
				float keep = ShowMath.smooth((dc - s.clear * 0.45f) / (s.clear * 0.9f));
				r *= 0.2f + 0.8f * keep;
			}
			if (s.popIn > 0f) {
				r *= ShowMath.pop(t - hash(seed + 3, id) * s.popIn, 3f);
			}
			// the boil: a tiny re-jitter every other tick
			float jx = (hash(seed + frame, id * 3) - 0.5f) * 0.01f, jy = (hash(seed + frame, id * 3 + 1) - 0.5f) * 0.01f;
			r *= 1f + 0.06f * (hash(seed + frame, id * 3 + 2) - 0.5f);
			if (r < 0.005f) {
				return;
			}
			x += jx;
			y += jy;
			if (Math.abs(x) > aspect + r || Math.abs(y) > 1f + r) {
				return;
			}
			st.dot(vc, p, x / aspect, y, r, s.rgb, s.alpha);
		}
	}
}
