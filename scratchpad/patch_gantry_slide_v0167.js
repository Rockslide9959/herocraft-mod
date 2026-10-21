// v0.16.7 (user): the Stark Gantry suit-up -- the base plates SLIDE OUT from under the part the arm placed (torso bottom
// down from the chest top, arms up from the gauntlets, thighs down from the thigh tops) and the outer layer builds on
// texel by texel with NO glow (the white-hot / cyan seam is gone from the gantry scheme).
'use strict';
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/ironman/IronManGantryBuild.java';
let s = fs.readFileSync(f, 'utf8');
const crlf = s.includes('\r\n');
s = s.replace(/\r\n/g, '\n');
function r(a, b) { if (!s.includes(a)) throw new Error('miss: ' + a.slice(0, 80)); s = s.replace(a, b); }

// Parts carries the slide columns
r(`	/** Per texel of a piece: its stage (-1 = not this piece) and build position. */
	private record Parts(int w, int h, byte[] stage, float[] k) {
	}`,
`	/**
	 * Per texel of a piece: its stage (-1 = not this piece) and build position. v0.16.7: for the gantry's slide stages
	 * ({@link #slides}) every base texel also has a column ({@code col}, -1 = none) -- the texels of one vertical strip of
	 * the plate, nearest the seam first -- and its place in it ({@code row}).
	 */
	private record Parts(int w, int h, byte[] stage, float[] k, int[] col, int[] row, int[][] cols) {
		Parts(int w, int h, byte[] stage, float[] k) {
			this(w, h, stage, k, null, null, null);
		}
	}

	/**
	 * v0.16.7, user request: on the gantry the self-built BASE plates no longer build on texel by texel -- they slide out
	 * from under the part the arm placed (the torso bottom down from the chest top, each arm up out of its gauntlet, the
	 * thighs down from the thigh tops). Drawn in texture space: at progress p a column shows its far-end rows in the
	 * rows nearest the seam, so the plate's leading edge comes out first and the strip moves along until it is home.
	 */
	static boolean slides(int stage) {
		return stage == GantryTimeline.TORSO_BOTTOM || stage == GantryTimeline.R_ARM || stage == GantryTimeline.L_ARM
				|| stage == GantryTimeline.THIGH_BOTTOM;
	}

	/** Distance (model pixels) of a base texel at height {@code y} from its slide stage's seam. */
	private static float fromSeam(int stage, float y) {
		return stage == GantryTimeline.R_ARM || stage == GantryTimeline.L_ARM ? y - 17f
				: stage == GantryTimeline.TORSO_BOTTOM ? 18f - y : 8f - y;
	}

	/** 0..1 how far slide stage {@code s} has come at frame {@code f} (NaN if the plan has no such stage). */
	private static float slideProgress(Timing timing, int s, float f) {
		float a = timing.appear(s, 0f), b = timing.appear(s, 1f);
		if (Float.isNaN(a) || Float.isNaN(b)) {
			return Float.NaN;
		}
		return b <= a ? (f >= a ? 1f : 0f) : Math.max(0f, Math.min(1f, (f - a) / (b - a)));
	}`);

// classify: build the columns for the gantry scheme
r(`		return new Parts(w, h, stage, k);
	}

	/** {stage, k} of a texel`,
`		if (scheme != S_GANTRY) {
			return new Parts(w, h, stage, k);
		}
		// v0.16.7: the slide columns -- base texels of the slide stages, grouped by bone + their spot round the plate
		Map<String, List<float[]>> byCol = new HashMap<>();
		for (IronManAssemblyReveal.Sample s : samples) {
			int i = s.index();
			if (s.cube() != 0 || !slides(stage[i])) {
				continue;
			}
			String key = s.bone() + "|" + Math.round(s.pos().x * 4f) + "|" + Math.round(s.pos().z * 4f);
			byCol.computeIfAbsent(key, x -> new ArrayList<>()).add(new float[] { i, fromSeam(stage[i], s.pos().y) });
		}
		int[] col = new int[w * h], row = new int[w * h];
		Arrays.fill(col, -1);
		List<int[]> cols = new ArrayList<>();
		for (List<float[]> c : byCol.values()) {
			c.sort((a, b) -> Float.compare(a[1], b[1]));
			int[] ids = c.stream().mapToInt(a -> (int) a[0]).distinct().filter(i -> col[i] < 0).toArray();
			if (ids.length == 0) {
				continue;
			}
			for (int j = 0; j < ids.length; j++) {
				col[ids[j]] = cols.size();
				row[ids[j]] = j;
			}
			cols.add(ids);
		}
		return new Parts(w, h, stage, k, col, row, cols.toArray(new int[0][]));
	}

	/** {stage, k} of a texel`);

// frame(): no glow on the gantry; slide stages drawn shifted
r(`		int fi = (int) Math.floor(f);
		if (fi >= t.last + GLOW_TICKS) {
			return base;
		}
		boolean hot = false;
		for (int i = 0; i < t.appear.length; i++) {
			float a = t.appear[i];
			if (!Float.isNaN(a) && a <= fi && fi - a < GLOW_TICKS && !timing.whole(parts.stage()[i])) {
				hot = true;
				break;
			}
		}
		String sig = hot ? "t" + fi : "c" + t.countAtOrBefore(fi);`,
`		int fi = (int) Math.floor(f);
		// v0.16.7 (user): the gantry builds on without the glowing seam
		boolean glow = timing.scheme() != S_GANTRY;
		if (fi >= t.last + (glow ? GLOW_TICKS : 0)) {
			return base;
		}
		boolean hot = false;
		for (int i = 0; glow && i < t.appear.length; i++) {
			float a = t.appear[i];
			if (!Float.isNaN(a) && a <= fi && fi - a < GLOW_TICKS && !timing.whole(parts.stage()[i])) {
				hot = true;
				break;
			}
		}
		// v0.16.7: a plate mid-slide changes every tick
		float[] slide = new float[GantryTimeline.STAGES];
		boolean sliding = false;
		if (parts.cols() != null) {
			for (int st = 0; st < GantryTimeline.STAGES; st++) {
				slide[st] = slides(st) ? slideProgress(timing, st, fi) : Float.NaN;
				sliding |= slide[st] > 0f && slide[st] < 1f;
			}
		}
		String sig = hot || sliding ? "t" + fi : "c" + t.countAtOrBefore(fi);`);

r(`			int x = i % parts.w();
			int y = i / parts.w();
			if (fi < a) {`,
`			int x = i % parts.w();
			int y = i / parts.w();
			int c = parts.col() == null ? -1 : parts.col()[i];
			if (c >= 0 && !Float.isNaN(slide[parts.stage()[i]])) {
				float p = slide[parts.stage()[i]];
				int[] strip = parts.cols()[c];
				int n = strip.length, on = Math.round(p * n), j = parts.row()[i];
				if (j >= on) {
					img.setPixelRGBA(x, y, 0);
				} else if (on < n) {
					int from = strip[j + n - on];
					img.setPixelRGBA(x, y, src.getPixelRGBA(from % parts.w(), from / parts.w()));
				}
				continue;
			}
			if (fi < a) {`);
r(`			if (((orig >>> 24) & 0xFF) == 0 || timing.whole(parts.stage()[i])) {`,
`			if (!glow || ((orig >>> 24) & 0xFF) == 0 || timing.whole(parts.stage()[i])) {`);

s = s.replace(' * texels flashing a white-hot then cyan seam for a few ticks;', ' * texels flashing a white-hot then cyan seam for a few ticks (v0.16.7: not on the gantry any more -- its base plates slide\n * out from under the carried parts and the outer layer builds on plain, see {@link #slides});');
fs.writeFileSync(f, crlf ? s.replace(/\n/g, '\r\n') : s);
console.log('patched');
