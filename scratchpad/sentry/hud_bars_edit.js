// v0.17.5 Sentry rework: swaps the Sun Gauge + Void Hairline rows of SentryHud for the new bevelled vanilla-style bars (one-off edit).
const fs = require('fs');
const path = require('path');
const f = path.join(__dirname, '..', '..', 'src/client/java/com/projecthero/mod/client/sentry/SentryHud.java');
let s = fs.readFileSync(f, 'utf8');
const eol = '\r\n';
s = s.replace(/\r?\n/g, '\n');
const rep = (a, b) => { if (!s.includes(a)) throw new Error('missing ' + a.slice(0, 60)); s = s.replace(a, b); };
rep(`		voidLine(hud, s, now, partial);
		float sunFrac = Mth.clamp(s.sun / SentryConfig.SUN_MAX, 0f, 1f);
		hud.meter(Component.translatable("hud.projecthero.sentry.sun"), sunFrac, 0xFFFFC83A, String.valueOf(Math.round(s.sun)), VanillaHud.WHITE,
				MutationMeters.Style.GAUGE);
`, `		voidLine(hud, s, now, partial);
		sunLine(hud, s, now, partial);
`);
const start = s.indexOf('	/** The Void meter: a Hairline row drawn by hand');
const end = s.indexOf('	/** The Void\'s thresholds over the view');
if (start < 0 || end < 0) throw new Error('voidLine block not found');
s = s.slice(0, start) + `	// ---------------------------------------------------------------- the two bars (v0.17.5 rework: the "Sentry bar")

	/**
	 * v0.17.5 rework (user: "make the sentry bars look better, make the sun bar yellow and make the void bar fill up with a red and the
	 * more full the bar gets the darker it becomes, make them both minecraft style bars"): a new vanilla-style bar, drawn with fills so
	 * it stays crisp at every GUI scale -- the XP bar's look: a 1-px near-black outline, a dark trough (its top row darker still, like a
	 * recess), and a 5-px fill bevelled like vanilla pixel art (a light top row, the body, a dark bottom row), with the XP bar's ten
	 * notches. {@code rgb} is the fill body; {@code marks} are extra notches (0..1) drawn a little stronger (the Void's 50 / 75 / 90).
	 */
	static void bevelBar(GuiGraphics g, int x, int y, int w, float frac, int rgb, float highlight, float[] marks) {
		g.fill(x - 1, y - 1, x + w + 1, y + 6, 0xFF0B0B0B); // outline
		g.fill(x, y, x + w, y + 5, 0xFF2E2A26); // trough
		g.fill(x, y, x + w, y + 1, 0xFF1C1915); // its recessed top row
		int fill = Math.round(w * Mth.clamp(frac, 0f, 1f));
		if (fill > 0) {
			g.fill(x, y, x + fill, y + 5, 0xFF000000 | rgb);
			g.fill(x, y, x + fill, y + 1, 0xFF000000 | scale(rgb, highlight));
			g.fill(x, y + 1, x + fill, y + 2, 0xFF000000 | scale(rgb, 1f + (highlight - 1f) * 0.4f));
			g.fill(x, y + 4, x + fill, y + 5, 0xFF000000 | scale(rgb, 0.62f));
			g.fill(x + fill - 1, y, x + fill, y + 5, 0xFF000000 | scale(rgb, 0.8f)); // the fill's edge
		}
		for (int i = 1; i < 10; i++) { // the XP bar's notches
			int nx = x + Math.round(w * i / 10f);
			g.fill(nx, y + 1, nx + 1, y + 4, nx < x + fill ? 0x40000000 : 0x30FFFFFF);
		}
		for (float m : marks) {
			int nx = x + Math.round(w * m);
			g.fill(nx, y, nx + 1, y + 5, nx < x + fill ? 0x90000000 : 0x60FFFFFF);
		}
	}

	/** {@code rgb} with every channel times {@code k} (clamped). */
	static int scale(int rgb, float k) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k));
		int gg = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k));
		int b = Math.min(255, Math.round((rgb & 0xFF) * k));
		return (r << 16) | (gg << 8) | b;
	}

	static int lerp(int a, int b, float t) {
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int gg = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bb = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (gg << 8) | bb;
	}

	/** The Void's red for a fill fraction: bright red empty-ish, deep crimson half way, near-black red full. */
	static int voidColour(float frac) {
		float t = Mth.clamp(frac, 0f, 1f);
		return t < 0.55f ? lerp(0xE23030, 0x8E0F12, t / 0.55f) : lerp(0x8E0F12, 0x3A0505, (t - 0.55f) / 0.45f);
	}

	/** One labelled bar row: label left, number right, the bar between (11 px tall). */
	private static void barRow(VanillaHud.Stack hud, Component label, String value, int valueColour, float frac, int rgb, float highlight,
			float[] marks) {
		GuiGraphics g = hud.g;
		hud.reserve(11);
		int y = hud.top() + 2;
		int labelW = hud.font.width(label);
		int valueW = Math.max(hud.font.width("100"), hud.font.width(value));
		int barX = hud.x0 + labelW + 5;
		int barW = Math.max(20, hud.width - labelW - 5 - valueW - 4);
		g.drawString(hud.font, label, hud.x0, y, VanillaHud.WHITE, true);
		g.drawString(hud.font, value, hud.x0 + hud.width - hud.font.width(value), y, valueColour, true);
		bevelBar(g, barX, y + 1, barW, frac, rgb, highlight, marks);
	}

	/** The Sun: a warm yellow bar (#F5C518 body, #FFE14D-ish top row, dark-gold bottom row), its highlight pulsing softly when full. */
	private static void sunLine(VanillaHud.Stack hud, SentryState s, long now, float partial) {
		float frac = Mth.clamp(s.sun / SentryConfig.SUN_MAX, 0f, 1f);
		float hi = 1.18f;
		if (frac >= 0.999f) {
			hi += 0.12f * (0.5f + 0.5f * Mth.sin((now + partial) * 0.15f));
		}
		barRow(hud, Component.translatable("hud.projecthero.sentry.sun"), String.valueOf(Math.round(s.sun)), 0xFFFFE14D, frac, 0xF5C518, hi,
				new float[0]);
	}

	/** The Void: a red bar that darkens as it fills (one colour for the whole fill, by the fill), the 50 / 75 / 90 notches marked. */
	private static void voidLine(VanillaHud.Stack hud, SentryState s, long now, float partial) {
		float frac = Mth.clamp(s.voidLevel / SentryConfig.VOID_MAX, 0f, 1f);
		int valueColour = s.voidLevel >= SentryConfig.VOID_DIMMING ? 0xFFFF5555 : s.voidLevel >= SentryConfig.VOID_WHISPERS ? 0xFFFF8A8A : VanillaHud.WHITE;
		float hi = s.voidLevel >= SentryConfig.VOID_DIMMING ? 1.35f + 0.25f * Mth.sin((now + partial) * 0.5f) : 1.35f; // a heartbeat past 90
		barRow(hud, Component.translatable("hud.projecthero.sentry.void"), String.valueOf(Math.round(s.voidLevel)), valueColour, frac,
				voidColour(frac), hi, new float[] { SentryConfig.VOID_WHISPERS / SentryConfig.VOID_MAX, SentryConfig.VOID_MISFIRE / SentryConfig.VOID_MAX,
						SentryConfig.VOID_DIMMING / SentryConfig.VOID_MAX });
	}

` + s.slice(end);
s = s.replace(' *   Sun  [Gauge: notched gold boss bar]  82   fuels the Sentry moves\n *   Void [Hairline: a black 3-px line]   47   the danger (gold edge once past 50, pulsing past 90)\n',
	' *   Sun  [yellow bevelled bar]  82   fuels the Sentry moves (v0.17.5 rework: both vanilla XP-bar style, {@link #bevelBar})\n *   Void [red bevelled bar]  47   the danger: bright red low, darkening to near-black red full; 50/75/90 notches\n');
fs.writeFileSync(f, s.replace(/\n/g, eol));
console.log('ok');
