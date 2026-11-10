// v0.17.3 NBC-style regeneration: AndyRegrow -- an arm / leg segment grows only to the wrist / ankle, the hand / foot segment
// from there; one dispatch point (drawPart / drawEnd) both looks can branch from.
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/andy/AndyRegrow.java';
let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function rep(a, b) { if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
rep(`	/** A whole part growing back, {@code p} 0..1. */
	public static void draw(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		if (part >= AndyState.RIGHT_ARM) {
			limb(pose, buffers, light, tex, slim, part, p, 0f, false);
		} else {
			boneAndFlesh(pose, buffers, light, tex, slim, part, p);
		}
	}
`, `	/** A whole part growing back, {@code p} 0..1 (an arm / leg with its hand / foot not started). */
	public static void draw(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p) {
		drawPart(pose, buffers, light, tex, slim, part, p, 0f);
	}

	/**
	 * v0.17.3 (NBC's segments) -- THE dispatch point for a broken part regrowing, in the part's own space: {@code p} is the
	 * segment's own progress (frame / its frames, {@code AndyState#regrowth}); for an arm or leg {@code endP} is its hand's /
	 * foot's ({@code AndyState#extremity}), which grows from the wrist / ankle once the limb has
	 * {@code AndyConfig#HAND_NEEDS_ARM} frames. The stage of each is {@code AndyState#stage}. Both looks branch from here.
	 */
	public static void drawPart(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p,
			float endP) {
		if (part >= AndyState.RIGHT_ARM) {
			limb(pose, buffers, light, tex, slim, part, p, 0f, AndyCuts.WRIST, false, false);
			if (endP > 0f) {
				limb(pose, buffers, light, tex, slim, part, endP, AndyCuts.WRIST, 12f, false, false);
			}
		} else {
			boneAndFlesh(pose, buffers, light, tex, slim, part, p);
		}
	}

	/**
	 * v0.17.3 -- the dispatch point for a part that is there but missing its end ({@link AndyCuts}: the hand / foot segment gone
	 * or regrowing, a stage half burnt, a bitten arm), in the part's own space: cut at {@code cut.rows()}, growing back from
	 * there by {@code cut.q()}.
	 */
	public static void drawEnd(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, AndyCuts.Cut cut) {
		if (part == AndyState.BODY) {
			float rows = cut.rows() + (12f - cut.rows()) * ease(Math.min(1f, cut.q()));
			bodyCut(pose, buffers, light, tex, slim, Math.min(12.5f, rows), cut.embers());
		} else {
			limb(pose, buffers, light, tex, slim, part, cut.q(), cut.rows(), cut.embers());
		}
	}
`);
rep(`	static void limb(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p, float from,
			boolean embers) {
		Limb lb = Limb.of(part, slim);
		float qb = AndyConfig.layer(AndyConfig.LAYER_BONE, p);
		float qm = AndyConfig.layer(AndyConfig.LAYER_MUSCLE, p);
		float qs = AndyConfig.layer(AndyConfig.LAYER_SKIN, p);
		float fb = from + Math.max(0f, BONE_REACH - from) * qb * qb * (3f - 2f * qb);`,
`	static void limb(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p, float from,
			boolean embers) {
		limb(pose, buffers, light, tex, slim, part, p, from, 12f, embers, true);
	}

	/**
	 * The same, growing only rows {@code from}..{@code to} (v0.17.3, NBC's segments: an arm grows to the wrist, its hand from the
	 * wrist). {@code aboveWhole}: the rows above {@code from} are drawn as whole skin (a cut); false, they are left to another
	 * drawing (the limb still growing above its hand). Short of the end ({@code to} < 12) the bottom stays an open, red end.
	 */
	static void limb(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, float p, float from,
			float to, boolean embers, boolean aboveWhole) {
		Limb lb = Limb.of(part, slim);
		boolean bounded = to < lb.h - 0.01f;
		float qb = AndyConfig.layer(AndyConfig.LAYER_BONE, p);
		float qm = AndyConfig.layer(AndyConfig.LAYER_MUSCLE, p);
		float qs = AndyConfig.layer(AndyConfig.LAYER_SKIN, p);
		float reach = bounded ? Math.min(BONE_REACH, to - 0.05f) : BONE_REACH;
		float fb = from + Math.max(0f, reach - from) * qb * qb * (3f - 2f * qb);`);
rep(`		float span = Math.max(1f, lb.h - from);
		float fs = from + (lb.end - from) * qs;`, `		float span = Math.max(1f, (bounded ? to : lb.h) - from);
		float endTo = bounded ? to + 1.6f : lb.end;
		float fs = from + (endTo - from) * qs;`);
rep(`			float key = lb.texelAt[i];
			float g = key < from ? 1f : (fs - key) / LIMB_SETTLE;
			if (g <= 0f) {
				continue;
			}`, `			float key = lb.texelAt[i];
			if ((key < from && !aboveWhole) || (bounded && key >= to)) {
				continue; // another drawing's (the limb above), or past this one's end (the hand / foot below)
			}
			float g = key < from ? 1f : (fs - key) / LIMB_SETTLE;
			if (g <= 0f) {
				continue;
			}`);
rep(`		if (fs >= lb.done) {
			return; // closed over
		}`, `		if (!bounded && fs >= lb.done) {
			return; // closed over
		}`);
rep(`			float key = lb.fleshKey[i];
			boolean face = from > 0f && key < from; // the cut face itself
			int row = (i / lb.w) % lb.h;`, `			float key = lb.fleshKey[i];
			boolean face = from > 0f && key < from; // the cut face itself
			int row = (i / lb.w) % lb.h;
			if ((face && !aboveWhole) || (bounded && row + 0.5f > to)) {
				continue; // the limb above draws its own end; past this one's end
			}
			// short of the end the skin never closes over the last ragged rows: they are the open, red end
			boolean openEnd = bounded && skinAt >= to;`);
rep(`			float cap = lb.capAt[i];
			if (fs > skinAt + LIMB_SETTLE && (Float.isNaN(cap) || fs > cap + LIMB_SETTLE)) {
				continue; // the skin has closed over it
			}`, `			float cap = lb.capAt[i];
			if (!openEnd && fs > skinAt + LIMB_SETTLE && (Float.isNaN(cap) || fs > cap + LIMB_SETTLE)) {
				continue; // the skin has closed over it
			}`);
fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
// AndyRender: the broken limb draws its hand / foot too; the cut goes through drawEnd
const r = 'src/client/java/com/projecthero/mod/client/andy/AndyRender.java';
let u = fs.readFileSync(r, 'utf8'); const crlf2 = u.includes('\r\n'); u = u.replace(/\r\n/g, '\n');
function rep2(a, b) { if (!u.includes(a)) throw new Error('render missing: ' + a.slice(0, 90)); u = u.replace(a, b); }
rep2(`				pose.pushPose();
				part(model, i).translateAndRotate(pose);
				regrow(pose, buffers, light, tex, slim, i, Math.min(1f, p));
				pose.popPose();`, `				pose.pushPose();
				part(model, i).translateAndRotate(pose);
				int end = AndyState.extremity(i);
				AndyRegrow.drawPart(pose, buffers, light, tex, slim, i, Math.min(1f, p), end < 0 ? 0f : s.regrowth(end, now));
				pose.popPose();`);
rep2(`	public static void drawCut(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, AndyCuts.Cut cut) {
		if (part == AndyState.BODY) {
			float rows = cut.rows() + (12f - cut.rows()) * AndyRegrow.ease(Math.min(1f, cut.q()));
			AndyRegrow.bodyCut(pose, buffers, light, tex, slim, Math.min(12.5f, rows), cut.embers());
		} else {
			AndyRegrow.limb(pose, buffers, light, tex, slim, part, cut.q(), cut.rows(), cut.embers());
		}
	}`, `	public static void drawCut(PoseStack pose, MultiBufferSource buffers, int light, ResourceLocation tex, boolean slim, int part, AndyCuts.Cut cut) {
		AndyRegrow.drawEnd(pose, buffers, light, tex, slim, part, cut);
	}`);
fs.writeFileSync(r, crlf2 ? u.replace(/\n/g, '\r\n') : u);
console.log('ok');
