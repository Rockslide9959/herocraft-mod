// v0.17.3: Andy -- regrowth builds from the inside out: bone, then muscle thickening outward from the bone, then skin over it.
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/andy/AndyRegrow.java';
let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function rep(a, b) { if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 100)); t = t.replace(a, b); }

// --- limb fronts: muscle thickness per row instead of a lengthwise front
rep(`		float fb = from + Math.max(0f, BONE_REACH - from) * qb * qb * (3f - 2f * qb);
		float fm = from + (lb.fleshEnd - from) * qm;
		if (qb < 1f) {
			fm = Math.min(fm, Math.max(from, fb - 0.2f)); // the muscle never passes the bone's tip
		}
		float fs = from + (lb.end - from) * qs;
		if (qm < 1f) {
			fs = Math.min(fs, Math.max(from, fm - 0.3f)); // nor the skin the muscle's front
		}`,
`		float fb = from + Math.max(0f, BONE_REACH - from) * qb * qb * (3f - 2f * qb);
		// v0.17.3 (user: "make the regrowth start inwards and regenerate outwards ... the muscle first builds from the bone out
		// and then gets covered by skin"): the muscle thickens outward from the bone's surface, every row at once (the rows
		// near the joint a little ahead), and the skin only closes over rows whose muscle is full thickness
		float span = Math.max(1f, lb.h - from);
		float fs = from + (lb.end - from) * qs;
		if (qm < 1f) {
			float fullRow = from + span * (qm * (1f + MUSCLE_LAG) - 1f) / MUSCLE_LAG;
			fs = Math.min(fs, Math.max(from, fullRow - 0.3f));
		}`);
rep(`		if (fb > from + 0.05f) {
			limbBone(pose, buffers, light, slim, part, Math.max(from, fm - 1.5f), fb);
		}`,
`		if (fb > from + 0.05f) {
			limbBone(pose, buffers, light, slim, part, from, fb);
		}
		float ax = box0(lb) + lb.w * 0.5f, az = lb.box[2] + lb.d * 0.5f;
		float boneR = slim && (part == AndyState.RIGHT_ARM || part == AndyState.LEFT_ARM) ? 0.8f : 1.0f;`);
rep(`			float key = lb.fleshKey[i];
			boolean face = from > 0f && key < from; // the cut face itself
			if (!face && (!building || key >= fm)) {
				continue; // not built yet
			}`,
`			float key = lb.fleshKey[i];
			boolean face = from > 0f && key < from; // the cut face itself
			int row = (i / lb.w) % lb.h;
			float mr = 0f;
			if (!face) {
				if (!building || row + 0.5f > fb) {
					continue; // no muscle yet, or no bone here yet to build on
				}
				float u = Math.max(0f, (row + 0.5f - from) / span);
				mr = Math.max(0f, Math.min(1f, qm * (1f + MUSCLE_LAG) - MUSCLE_LAG * u + lb.jo[i] * 0.35f));
				if (mr <= 0f) {
					continue;
				}
			}`);
rep(`			} else {
				float g = Math.min(1f, (fm - key) / LIMB_SWELL);
				s = lb.js[i] * (g >= 1f ? 1f : 0.2f + 0.8f * ease(g));
				float d = Math.min(1f, (fm - key) / 2.5f); // fresh at the front (tapered in) .. settled (a hair proud)
				out = -0.45f + 0.6f * d + lb.jo[i];
			}
			float x0 = box[0] + i % lb.w, y0 = box[1] + (i / lb.w) % lb.h, z0 = box[2] + i / (lb.w * lb.h);`,
`			} else if (mr < 1f) {
				// still thickening: the cell is the part of it inside the muscle's radius round the bone, so the muscle
				// grows out from the bone's surface (cells wholly inside the bone are the bone)
				float x0 = box[0] + i % lb.w, y0 = box[1] + row, z0 = box[2] + i / (lb.w * lb.h);
				float e = mr * mr * (3f - 2f * mr);
				float rx = boneR + 0.08f + (lb.w * 0.5f - boneR + 0.1f) * e;
				float rz = boneR + 0.08f + (lb.d * 0.5f - boneR + 0.1f) * e;
				float cx0 = Math.max(x0, ax - rx), cx1 = Math.min(x0 + 1f, ax + rx);
				float cz0 = Math.max(z0, az - rz), cz1 = Math.min(z0 + 1f, az + rz);
				boolean inBone = x0 >= ax - boneR - 0.01f && x0 + 1f <= ax + boneR + 0.01f && z0 >= az - boneR - 0.01f
						&& z0 + 1f <= az + boneR + 0.01f;
				if (inBone || cx1 - cx0 < 0.04f || cz1 - cz0 < 0.04f) {
					continue;
				}
				// fresh muscle is wetter and darker, settling to its own red as it reaches full thickness
				int rgb = e < 0.6f ? LIMB_FLESH[Math.max(0, java.util.Arrays.binarySearch(LIMB_FLESH, lb.rgb[i]) - 1)] : lb.rgb[i];
				flatBox(vc, last, cx0, y0 + 0.02f, cz0, cx1, y0 + 0.98f, cz1, rgb, light);
				continue;
			} else {
				s = lb.js[i];
				out = 0.15f + lb.jo[i];
			}
			float x0 = box[0] + i % lb.w, y0 = box[1] + row, z0 = box[2] + i / (lb.w * lb.h);`);
rep(`	/** Where the bone's tip stops (rows from the joint). */`,
`	/** How far behind the joint's rows the far rows' muscle starts thickening (in muscle progress: 0 = all rows at once). */
	static final float MUSCLE_LAG = 0.5f;

	private static float box0(Limb lb) {
		return lb.box[0];
	}

	/** Where the bone's tip stops (rows from the joint). */`);

// --- head and body: the muscle builds outward from the skull / ribcage, then the skin covers it
rep(`						// the torso's muscle builds from the neck down, row by row (the shoulders first, the hips last)
						c.key = part == AndyState.BODY ? Math.max(0f, Math.min(1f, 0.84f * a + 0.08f * radial + jitter * 0.5f - 0.02f))
								: Math.max(0f, Math.min(1f, 0.62f * a + 0.22f * radial + jitter - 0.04f));`,
`						// v0.17.3 (user: "start inwards and regenerate outwards"): the muscle builds outward from the ribcage / skull
						// (the torso still a little from the neck down, so the shoulders are ready first)
						c.key = part == AndyState.BODY ? Math.max(0f, Math.min(1f, 0.30f * a + 0.62f * radial + jitter * 0.4f - 0.02f))
								: Math.max(0f, Math.min(1f, 0.15f * a + 0.75f * radial + jitter * 0.6f - 0.04f));`);
fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
