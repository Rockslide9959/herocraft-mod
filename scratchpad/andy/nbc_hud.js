// v0.17.3 NBC-style regeneration: the HUD -- ten segments on the little body (bone / flesh / skin), Regen: Auto / Manual,
// the Regen Menu.
const fs = require('fs');
const f = 'src/client/java/com/projecthero/mod/client/andy/AndyHud.java';
let t = fs.readFileSync(f, 'utf8'); const crlf = t.includes('\r\n'); t = t.replace(/\r\n/g, '\n');
function rep(a, b) { if (!t.includes(a)) throw new Error('missing: ' + a.slice(0, 90)); t = t.replace(a, b); }
rep(`	/** The little body: x, y, w, h (in HUD px) of head, body, right arm, left arm, right leg, left leg -- seen from the front. */
	private static final int[][] FIGURE = { { 4, 0, 8, 8 }, { 4, 8, 8, 12 }, { 0, 8, 4, 12 }, { 12, 8, 4, 12 }, { 4, 20, 4, 12 }, { 8, 20, 4, 12 } };
	private static final String[] SPEEDS = { "slow", "normal", "fast", "burst" };`,
`	/**
	 * The little body: x, y, w, h (in HUD px) of the ten segments, seen from the front -- head, body, right arm, left arm, right
	 * leg, left leg (each down to the wrist / ankle), right hand, left hand, right foot, left foot.
	 */
	private static final int[][] FIGURE = { { 4, 0, 8, 8 }, { 4, 8, 8, 12 }, { 0, 8, 4, 8 }, { 12, 8, 4, 8 }, { 4, 20, 4, 8 }, { 8, 20, 4, 8 },
			{ 0, 16, 4, 4 }, { 12, 16, 4, 4 }, { 4, 28, 4, 4 }, { 8, 28, 4, 4 } };
	/** A regrowing segment's fill: bone, then flesh, then skin (NBC's stages). */
	private static final int BONE = 0xFFE4DCC4, FLESH = 0xFFA11E26, SKIN = 0xFFFFD8D0;`);
rep(`		float regrow = -1f;
		for (int i = 0; i < AndyState.PARTS; i++) {
			if (s.broken(i)) {
				regrow = Math.max(regrow, s.regrowth(i, now));
			}
		}
		// v0.17.3: a part that could not pay its food regrows at half Normal (Andy.payForPart): the number turns red and the
		// speed line says why
		boolean starved = false;
		for (int i = 0; i < AndyState.PARTS; i++) {
			starved |= s.regrowth(i, now) < 1f && Andy.starvedRegrowth(s, i);
		}
		if (regrow >= 0f) {
			VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.andy.regrowth"), regrow, REGROWTH,
					Math.round(regrow * 100f) + "%", starved ? VanillaHud.RED : VanillaHud.WHITE);
			by -= 11;
		}`,
`		// v0.17.3 (NBC's segments): the whole body's regrowth, frames grown of frames gone
		float grown = 0f, gone = 0f;
		for (int i = 0; i < AndyState.SEGS; i++) {
			if (s.broken(i)) {
				grown += s.frame(i, now);
				gone += AndyConfig.FRAMES[i];
			}
		}
		if (gone > 0f) {
			float regrow = grown / gone;
			VanillaHud.meter(g, x0, by, WIDTH, Component.translatable("hud.projecthero.andy.regrowth"), regrow, REGROWTH,
					Math.round(regrow * 100f) + "%", VanillaHud.WHITE);
			by -= 11;
		}`);
rep(`		// v0.17.3: Off (Shift+V) and an empty stomach (nothing grows back) say so
		boolean off = s.flag(AndyState.FLAG_REGEN_OFF);
		starved |= !off && Andy.emptyStomach(p);
		Component speed = off ? Component.translatable("projecthero.andy.speed.off")
				: starved ? Component.translatable(Andy.emptyStomach(p) ? "projecthero.andy.speed.empty" : "projecthero.andy.speed.starving")
				: Component.translatable("projecthero.andy.speed." + SPEEDS[Math.max(0, Math.min(SPEEDS.length - 1, s.regenSpeed))]);
		g.drawString(mc.font, mc.font.plainSubstrByWidth(Component.translatable("hud.projecthero.andy.regen", speed).getString(), textW), x0,
				ty + 11, starved ? VanillaHud.RED : VanillaHud.GREY, true);`,
`		// v0.17.3: auto-regrowth on or off (Shift+V, Toggle Regen); the Regen Menu open says so in gold
		boolean off = s.flag(AndyState.FLAG_REGEN_OFF);
		Component mode = Component.translatable(off ? "projecthero.andy.regen.manual" : "projecthero.andy.regen.auto");
		g.drawString(mc.font, mc.font.plainSubstrByWidth(Component.translatable("hud.projecthero.andy.regen", mode).getString(), textW), x0,
				ty + 11, off ? VanillaHud.RED : VanillaHud.GREY, true);
		if (s.flag(AndyState.FLAG_REGEN_MENU) && s.reformUntil <= now) {
			g.drawString(mc.font, mc.font.plainSubstrByWidth(Component.translatable("hud.projecthero.andy.menu").getString(), textW), x0, ty - 11,
					VanillaHud.GOLD, true);
		}`);
rep(`					// v0.17.3: V's third move is held, not shifted: "Hold: Bone Sword" (when the sword is not already out)
					Component extra = slot == AbilitySlot.SLOT_5 && !s.boneSword()
							? Component.translatable("hud.projecthero.andy.hold", Component.translatable("projecthero.andy.ability." + AndyBoneSword.BONE_SWORD))
							: null;`,
`					// v0.17.3: V's third move is held, not shifted: "Hold: Bone Sword" (when the sword is not already out); in the
					// Regen Menu "Hold: Regenerate Whole Body"
					boolean menu = s.flag(AndyState.FLAG_REGEN_MENU);
					Component extra = slot == AbilitySlot.SLOT_5 && (menu || !s.boneSword())
							? Component.translatable("hud.projecthero.andy.hold", Component.translatable("projecthero.andy.ability."
									+ (menu ? com.projecthero.mod.andy.AndyRegenMenu.WHOLE : AndyBoneSword.BONE_SWORD)))
							: null;`);
rep(`	/** The little body with a 1-px dark frame behind it, like an item in a slot. */
	private static void figure(GuiGraphics g, AndyState s, float now, boolean victor, int fx, int fy) {
		for (int i = 0; i < AndyState.PARTS; i++) {
			int[] f = FIGURE[i];
			int px = fx + f[0], py = fy + f[1];
			g.fill(px - 1, py - 1, px + f[2] + 1, py + f[3] + 1, 0xFF1A1A1A);
		}
		for (int i = 0; i < AndyState.PARTS; i++) {
			int[] f = FIGURE[i];
			int px = fx + f[0], py = fy + f[1];
			if (!s.broken(i)) {
				g.fill(px, py, px + f[2], py + f[3], partColour(s.hp(i) / AndyConfig.PART_HP[i], victor));
				g.fill(px, py, px + f[2], py + 1, 0x40FFFFFF); // a lit top edge, like the inventory's slot shading
			} else {
				g.fill(px, py, px + f[2], py + f[3], 0xFF3A0A0A);
				float r = s.regrowth(i, now);
				if (r > 0f) {
					int h = Math.round(f[3] * r);
					// from the joint outward: legs and arms fill downward, the head upward
					if (i == AndyState.HEAD) {
						g.fill(px, py + f[3] - h, px + f[2], py + f[3], 0xFFFFD8D0);
					} else {
						g.fill(px, py, px + f[2], py + h, 0xFFFFD8D0);
					}
				}
			}
		}
	}`,
`	/**
	 * The little body with a 1-px dark frame behind it, like an item in a slot: the ten segments, each whole (shaded by its
	 * part's HP), gone (dark) or regrowing -- filling from the joint outward in the colour of its stage (bone, flesh, skin).
	 */
	private static void figure(GuiGraphics g, AndyState s, float now, boolean victor, int fx, int fy) {
		for (int i = 0; i < AndyState.SEGS; i++) {
			int[] f = FIGURE[i];
			int px = fx + f[0], py = fy + f[1];
			g.fill(px - 1, py - 1, px + f[2] + 1, py + f[3] + 1, 0xFF1A1A1A);
		}
		for (int i = 0; i < AndyState.SEGS; i++) {
			int[] f = FIGURE[i];
			int px = fx + f[0], py = fy + f[1];
			if (!s.broken(i)) {
				int hpPart = AndyState.limbOf(i);
				g.fill(px, py, px + f[2], py + f[3], partColour(s.hp(hpPart) / AndyConfig.PART_HP[hpPart], victor));
				g.fill(px, py, px + f[2], py + 1, 0x40FFFFFF); // a lit top edge, like the inventory's slot shading
			} else {
				g.fill(px, py, px + f[2], py + f[3], 0xFF3A0A0A);
				float r = s.regrowth(i, now);
				if (r > 0f) {
					int h = Math.max(1, Math.round(f[3] * r));
					int stage = s.stage(i, now);
					int c = stage == AndyState.STAGE_BONE ? BONE : stage == AndyState.STAGE_FLESH ? FLESH : SKIN;
					// from the joint outward: legs and arms fill downward, the head upward
					if (i == AndyState.HEAD) {
						g.fill(px, py + f[3] - h, px + f[2], py + f[3], c);
					} else {
						g.fill(px, py, px + f[2], py + h, c);
					}
				}
			}
		}
	}`);
fs.writeFileSync(f, crlf ? t.replace(/\n/g, '\r\n') : t);
console.log('ok');
