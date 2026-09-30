package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.network.PowerSelectPayload;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The HeroPack power wheel: a radial menu for switching which owned mutation occupies ability slots
 * R/G/X/Z/V/C. Opened by the dedicated power key (default H). Point at a segment and either click it or
 * release H to select; release H over the centre (or press Escape) to cancel. Switching here never
 * touches cooldowns (enforced server-side in {@code ExperimentalPowers.setActive}).
 *
 * <p>The "no mutation" choice is the pill under the ring: for a plain player it is powers-off; for a Hero
 * Class player (Spider-Man, etc.) it is "hand the slots back to the Hero Class", so it is labelled with that
 * class's name rather than reading as switching powers off.
 *
 * <p>v0.14.7 redesign: a monochrome ring in the same black / charcoal / grey family as the ability HUD's
 * "mono" palette ({@code AbilityHud.MONO_*}). A player owns at most a few mutations (capacity 3 by default),
 * so the ring is built for that: one owned power is a whole ring, two are halves, three are thirds -- big
 * segments with clean gaps and crisp 1px grey outlines. The hovered segment lifts to a lighter grey with a white
 * outline and eases outward; the active power carries a bright rim and a small white notch. The centre disc
 * names the hovered power, its category and ability count; pips above the ring show used / empty mutation
 * slots. The whole wheel scales and fades in over ~150 ms.
 */
public final class PowerWheelScreen extends Screen {
	// ---- palette: the AbilityHud mono family
	private static final int DIM_TOP = 0x98000000;
	private static final int DIM_BOTTOM = 0xC8000000;
	private static final int SEG_FILL = 0xF0121212;
	private static final int SEG_FILL_ALT = 0xF0181818;
	private static final int SEG_FILL_HOVER = 0xF84E4E4E;
	private static final int SEG_FILL_ACTIVE = 0xF0262626;
	private static final int BORDER = 0xFF4A4A4A;
	private static final int BORDER_HOVER = 0xFFFFFFFF;
	private static final int BORDER_ACTIVE = 0xFFC8C8C8;
	private static final int CENTRE_FILL = 0xF0080808;
	private static final int TRACK = 0xD0050505;
	private static final int EMPTY_DASH = 0xFF3A3A3A;
	private static final int TEXT_NAME = 0xFFE2E2E2;
	private static final int TEXT_IDLE = 0xFFB4B4B4;
	private static final int TEXT_LABEL = 0xFF9A9A9A;
	private static final int TEXT_DIM = 0xFF808080;
	private static final int WHITE = 0xFFFFFFFF;

	/** Mouse dead zone (px from centre) inside which nothing is hovered -- releasing there cancels. */
	private static final int DEAD_ZONE = 24;
	/** Open animation length in client ticks (3 ticks = 150 ms). */
	private static final float OPEN_TICKS = 3f;
	/** Most slot pips ever drawn above the ring, whatever the server's capacity says. */
	private static final int MAX_PIPS = 8;

	/** Entry 0 is always the "no mutation" pill; entries 1.. are the owned powers, in ring order. */
	private final List<Entry> entries = new ArrayList<>();
	private int hovered = -1;
	/** Per-entry hover easing, 0..1, eased towards the hover target each frame. */
	private float[] hoverAnim = new float[0];
	private long lastFrameNanos = 0L;
	/** The pill's on-screen rectangle this frame (unscaled screen coordinates). */
	private int pillX0;
	private int pillY0;
	private int pillX1;
	private int pillY1;

	/** @param power the mutation, or {@code null} for the "no mutation" pill */
	private record Entry(String key, Component label, boolean active, Power power) {
	}

	/** A segment label layout: its lines, text scale, and the angle it sits at (slid along the segment if needed). */
	private record Fit(List<String> lines, float scale, double angle) {
	}

	private int ticksOpen = 0;
	/** Once true, the open key is treated as a click-to-select menu, not a hold-and-release wheel. */
	private boolean menuMode = false;
	private boolean heroClassNone = false;

	public PowerWheelScreen() {
		super(Component.translatable("screen.projecthero.power_select"));
	}

	@Override
	public void tick() {
		ticksOpen++;
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (!menuMode && com.projecthero.mod.client.ModKeyBindings.POWER_SELECT.matches(keyCode, scanCode)) {
			if (ticksOpen <= 4) {
				// A quick tap: keep it open as a click-to-select menu rather than closing instantly.
				menuMode = true;
			} else {
				// Held, then released: weapon-wheel style select-on-release.
				confirmAndClose();
			}
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	protected void init() {
		entries.clear();
		Minecraft mc = Minecraft.getInstance();
		String active = "";
		ExperimentalState state = mc.player == null ? null
				: mc.player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (state != null) {
			active = state.activePower;
		}

		boolean spiderMan = mc.player != null && com.projecthero.mod.spider.SpiderMan.hasPower(mc.player);
		heroClassNone = spiderMan;
		Component noneLabel = spiderMan
				? Component.translatable("projecthero.spider_man.name")
				: Component.translatable("screen.projecthero.power_select.none");
		entries.add(new Entry("", noneLabel, active.isEmpty(), null));

		if (state != null) {
			for (Power p : Powers.enabled()) {
				if (state.ownedPowers.contains(p.key())) {
					entries.add(new Entry(p.key(), Component.translatable(p.nameKey()), p.key().equals(active), p));
				}
			}
		}
		if (hoverAnim.length != entries.size()) {
			hoverAnim = new float[entries.size()];
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float a = openEase(partialTick);
		g.fillGradient(0, 0, this.width, this.height, fade(DIM_TOP, a), fade(DIM_BOTTOM, a));
	}

	private float openEase(float partialTick) {
		float t = Mth.clamp((ticksOpen + partialTick) / OPEN_TICKS, 0f, 1f);
		return 1f - (1f - t) * (1f - t) * (1f - t); // ease-out cubic
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		if (entries.isEmpty()) {
			return;
		}
		int n = entries.size() - 1; // ring segments = owned powers
		float open = openEase(partialTick);

		// ---- geometry (fits any GUI scale / window size; room is left for the pips above and the pill below)
		float outer = Math.max(52f, Math.min(118f, Math.min(this.width * 0.34f, (this.height - 78) / 2f)));
		float inner = outer * 0.42f;
		float lift = Math.max(4f, outer * 0.05f);
		int cxi = this.width / 2;
		int cyi = this.height / 2 - 6;
		float cx = cxi;
		float cy = cyi;

		Entry none = entries.get(0);
		int pillW = Math.min(this.font.width(none.label), Math.max(40, this.width - 60)) + 26;
		int pillH = 16;
		pillX0 = cxi - pillW / 2;
		pillX1 = pillX0 + pillW;
		pillY0 = Math.min(this.height - 32, Math.round(cy + outer + lift + 8));
		pillY1 = pillY0 + pillH;

		// ---- hover: the pill by rectangle, otherwise the pointing direction picks the segment (segment 0 is
		// centred at the top); the small dead zone round the centre cancels
		hovered = -1;
		double dist = Math.hypot(mouseX - cx, mouseY - cy);
		if (mouseX >= pillX0 && mouseX < pillX1 && mouseY >= pillY0 && mouseY < pillY1) {
			hovered = 0;
		} else if (n > 0 && dist > DEAD_ZONE) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = 1 + (int) (norm / (Math.PI * 2) * n) % n;
		}

		// ---- per-frame hover easing (frame-rate independent)
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = now;
		if (hoverAnim.length != entries.size()) {
			hoverAnim = new float[entries.size()];
		}
		float k = 1f - (float) Math.exp(-dt * 18f);
		for (int i = 0; i < entries.size(); i++) {
			hoverAnim[i] += ((i == hovered ? 1f : 0f) - hoverAnim[i]) * k;
		}

		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		float s = 0.82f + 0.18f * open;
		g.pose().scale(s, s, 1f);
		g.pose().translate(-cx, -cy, 0);

		double seg = n == 0 ? Math.PI * 2 : Math.PI * 2 / n;
		// half the (constant-width, parallel-sided) gap between segments, in px
		double pad = n <= 1 ? 0 : 2.5;
		float disc = inner - 6f;

		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();

		// a dark track behind the whole ring, closing the gap to the centre disc
		sector(vc, m, cx, cy, disc - 1f, outer + 3f, 0, Math.PI * 2, fade(TRACK, open));
		sector(vc, m, cx, cy, outer + 2f, outer + 3f, 0, Math.PI * 2, fade(0xFF1E1E1E, open));

		if (n == 0) {
			// nothing owned yet: a dim dashed ring of empty slots
			int dashes = 48;
			double step = Math.PI * 2 / dashes;
			for (int d = 0; d < dashes; d++) {
				double a0 = d * step;
				sector(vc, m, cx, cy, outer - 1f, outer, a0, a0 + step * 0.5, fade(EMPTY_DASH, open));
				sector(vc, m, cx, cy, inner, inner + 1f, a0, a0 + step * 0.5, fade(EMPTY_DASH, open));
			}
		}
		for (int j = 0; j < n; j++) {
			if (j + 1 != hovered) {
				drawSegment(vc, m, j, n, cx, cy, inner, outer, lift, seg, pad, open);
			}
		}
		if (hovered >= 1) {
			drawSegment(vc, m, hovered - 1, n, cx, cy, inner, outer, lift, seg, pad, open);
		}

		// the centre disc with a crisp rim
		sector(vc, m, cx, cy, 0f, disc, 0, Math.PI * 2, fade(CENTRE_FILL, open));
		sector(vc, m, cx, cy, disc - 1f, disc, 0, Math.PI * 2, fade(BORDER, open));
		sector(vc, m, cx, cy, disc - 4f, disc - 3f, 0, Math.PI * 2, fade(0x18FFFFFF, open));
		g.flush();

		// ---- segment labels: one shared text size for every name that fits, so the ring reads evenly
		Fit[] fitted = new Fit[n];
		float common = 1f;
		for (int j = 0; j < n; j++) {
			fitted[j] = fitLabel(j, n, inner, outer, seg, pad, -1f);
			if (fitted[j] != null) {
				common = Math.min(common, fitted[j].scale);
			}
		}
		for (int j = 0; j < n; j++) {
			if (fitted[j] != null && fitted[j].scale > common + 0.001f) {
				// re-seat at the shared size: it may then sit nearer the middle of its segment
				Fit again = fitLabel(j, n, inner, outer, seg, pad, common);
				fitted[j] = again != null ? again : fitted[j];
			}
			drawSegmentLabel(g, j, n, cx, cy, inner, outer, lift, fitted[j], common, open);
		}
		if (n == 0) {
			Component empty = Component.translatable("screen.projecthero.power_select.empty");
			g.drawString(this.font, empty, cxi - this.font.width(empty) / 2, Math.round(cy - (inner + outer) / 2f) - 4,
					fade(TEXT_DIM, open), false);
		}

		// ---- centre text
		drawCentre(g, cx, cy, disc, open);
		g.pose().popPose();

		// ---- slot pips above the ring (owned / empty mutation capacity) and the pill + hint below it
		drawPips(g, cxi, Math.round(cy - outer - lift) - 17, n, open);
		drawPill(g, none, open);
		Component hint = Component.translatable("screen.projecthero.power_select.hint");
		// wrapped to the window (two lines at most), never running off the sides
		List<FormattedCharSequence> hintLines = this.font.split(hint, Math.max(60, Math.min(320, this.width - 24)));
		int hintCount = Math.min(2, hintLines.size());
		int hy = Math.min(this.height - 1 - hintCount * 10, pillY1 + 6);
		for (int h = 0; h < hintCount; h++) {
			FormattedCharSequence l = hintLines.get(h);
			g.drawString(this.font, l, cxi - this.font.width(l) / 2, hy + h * 10, fade(TEXT_DIM, open), false);
		}
	}

	private void drawSegment(VertexConsumer vc, Matrix4f m, int j, int n, float cx, float cy, float inner, float outer,
			float lift, double seg, double pad, float open) {
		int i = j + 1;
		Entry e = entries.get(i);
		float he = smooth(hoverAnim.length > i ? hoverAnim[i] : 0f);
		double mid = -Math.PI / 2 + j * seg;
		boolean full = n == 1;
		double e0 = full ? 0 : mid - seg / 2;
		double e1 = full ? Math.PI * 2 : mid + seg / 2;
		float gap = (float) pad;
		// the hovered segment eases outward and grows a touch (a whole-ring segment just breathes a little)
		float push = lift * he;
		float r0 = inner + (full ? 0f : push * 0.6f);
		float r1 = outer + push * (full ? 0.6f : 1.4f);
		int base = e.active ? SEG_FILL_ACTIVE : (j % 2 == 0 ? SEG_FILL : SEG_FILL_ALT);
		int fill = lerpColour(base, SEG_FILL_HOVER, full ? he * 0.6f : he);
		int edge = lerpColour(e.active ? 0xFF6A6A6A : BORDER, BORDER_HOVER, he);
		band(vc, m, cx, cy, r0, r1, e0, e1, gap, fade(fill, open));
		// a soft sheen along the outer edge
		band(vc, m, cx, cy, r1 - 7f, r1 - 1f, e0, e1, gap + 1f, fade(0x0EFFFFFF, open));
		// crisp 1px outline: outer arc, inner arc and the two straight side edges
		band(vc, m, cx, cy, r1 - 1f, r1, e0, e1, gap, fade(edge, open));
		band(vc, m, cx, cy, r0, r0 + 1f, e0, e1, gap, fade(edge, open));
		if (!full) {
			side(vc, m, cx, cy, r0, r1, e0, gap, 1f, fade(edge, open));
			side(vc, m, cx, cy, r0, r1, e1, gap, -1f, fade(edge, open));
		}
		if (e.active) {
			// the active power: a bright 3px rim and a small white notch just outside the ring, at its label
			int rim = fade(lerpColour(BORDER_ACTIVE, WHITE, he), open);
			band(vc, m, cx, cy, r1 - 3f, r1, e0, e1, gap, rim);
			double notchHalf = 6.0 / outer;
			sector(vc, m, cx, cy, r1 + 3f, r1 + 6f, mid - notchHalf, mid + notchHalf, fade(WHITE, open));
		}
	}

	private void drawSegmentLabel(GuiGraphics g, int j, int n, float cx, float cy, float inner, float outer,
			float lift, Fit fit, float scale, float open) {
		int i = j + 1;
		Entry e = entries.get(i);
		float he = smooth(hoverAnim.length > i ? hoverAnim[i] : 0f);
		double mid = fit != null ? fit.angle : -Math.PI / 2 + j * (Math.PI * 2 / n);
		float push = lift * he * (n == 1 ? 0.3f : 1f);
		float rm = (inner + outer) / 2f + push;
		float lx = cx + (float) Math.cos(mid) * rm;
		float ly = cy + (float) Math.sin(mid) * rm;
		int colour = fade(lerpColour(e.active ? TEXT_NAME : TEXT_IDLE, WHITE, he), open);
		if (fit != null) {
			float blockH = fit.lines.size() * 9f;
			g.pose().pushPose();
			g.pose().translate(lx, ly, 0);
			g.pose().scale(scale, scale, 1f);
			float y = -blockH / 2f + 1f;
			for (String l : fit.lines) {
				g.drawString(this.font, l, -this.font.width(l) / 2, Math.round(y), colour, false);
				y += 9f;
			}
			g.pose().popPose();
			return;
		}
		// no room at all (a very small window): a two-letter monogram; the centre disc spells the name on hover
		String mono = monogram(e);
		g.drawString(this.font, mono, Math.round(lx) - this.font.width(mono) / 2, Math.round(ly) - 4, colour, false);
	}

	/**
	 * The best way to fit segment {@code j}'s name inside it -- one line, or two split on a word boundary -- at the
	 * largest pixel-exact text scale (or exactly {@code onlyScale} when that is positive), preferring the segment's
	 * middle but sliding along it towards where the ring runs level when that fits better. {@code null} if it does
	 * not fit anywhere.
	 */
	private Fit fitLabel(int j, int n, float inner, float outer, double seg, double pad, float onlyScale) {
		Entry e = entries.get(j + 1);
		double mid = -Math.PI / 2 + j * seg;
		// the test uses the resting geometry so a label never jumps between layouts as the segment lifts
		float rm = (inner + outer) / 2f;
		double halfSpan = n == 1 ? Math.PI : seg / 2;
		float[] scales = onlyScale > 0 ? new float[] {onlyScale} : labelScales();
		List<List<String>> layouts = layouts(shortName(e), 2);
		Fit best = null;
		double[] offsets = n == 1 ? new double[] {0} : new double[] {0, 0.1, -0.1, 0.2, -0.2, 0.3, -0.3, 0.4, -0.4, 0.5, -0.5};
		for (double f : offsets) {
			double a = mid + f * halfSpan;
			float x = (float) Math.cos(a) * rm;
			float y = (float) Math.sin(a) * rm;
			for (List<String> lines : layouts) {
				int widest = 0;
				for (String l : lines) {
					widest = Math.max(widest, this.font.width(l));
				}
				float blockH = lines.size() * 9f;
				for (float sc : scales) {
					if (best != null && sc <= best.scale + 0.001f) {
						break; // cannot beat what we already have (offsets are tried nearest-the-middle first)
					}
					if (fits(x, y, (widest * sc) / 2f + 2f, (blockH * sc) / 2f + 1f, inner, outer, mid, halfSpan, pad)) {
						best = new Fit(lines, sc, a);
						break;
					}
				}
			}
		}
		return best;
	}

	/** The name shown inside a segment: for a "Name / Alias" power, just the first name (the centre spells it all). */
	private static String shortName(Entry e) {
		String name = e.label.getString();
		int slash = name.indexOf(" / ");
		return slash > 0 ? name.substring(0, slash) : name;
	}

	/** Text scales, largest first, that land the font on whole screen pixels at the current GUI scale. */
	private float[] labelScales() {
		int gs = Math.max(1, (int) Math.round(this.minecraft == null ? 2 : this.minecraft.getWindow().getGuiScale()));
		if (gs == 2) {
			return new float[] {1f, 0.75f, 0.5f}; // 0.75 is off-grid by half a pixel, but 0.5 alone is too big a drop
		}
		int min = Math.max(1, (int) Math.ceil(gs * 0.5));
		float[] out = new float[gs - min + 1];
		for (int k = gs; k >= min; k--) {
			out[gs - k] = k / (float) gs;
		}
		return out;
	}

	/** The scale for small secondary text (category, ability count): three-quarters, snapped to whole pixels. */
	private float smallScale() {
		int gs = Math.max(1, (int) Math.round(this.minecraft == null ? 2 : this.minecraft.getWindow().getGuiScale()));
		if (gs <= 2) {
			return 1f;
		}
		return Math.max(1, (int) Math.floor(gs * 0.75f + 1e-3)) / (float) gs;
	}

	/**
	 * The name on one line, plus -- for a multi-word name -- the most balanced split over two (and, if allowed, three)
	 * lines on word boundaries. A lone "/" stays on the end of the line before it.
	 */
	private List<List<String>> layouts(String name, int maxLines) {
		List<List<String>> out = new ArrayList<>();
		out.add(List.of(name));
		List<String> words = new ArrayList<>();
		for (String w : name.split(" ")) {
			if (w.equals("/") && !words.isEmpty()) {
				words.set(words.size() - 1, words.get(words.size() - 1) + " /");
			} else if (!w.isEmpty()) {
				words.add(w);
			}
		}
		int nw = words.size();
		if (nw > 1 && maxLines >= 2) {
			List<String> best = null;
			int bestW = Integer.MAX_VALUE;
			for (int c = 1; c < nw; c++) {
				List<String> cand = List.of(String.join(" ", words.subList(0, c)), String.join(" ", words.subList(c, nw)));
				int w = widest(cand);
				if (w < bestW) {
					bestW = w;
					best = cand;
				}
			}
			out.add(best);
		}
		if (nw > 2 && maxLines >= 3) {
			List<String> best = null;
			int bestW = Integer.MAX_VALUE;
			for (int c1 = 1; c1 < nw - 1; c1++) {
				for (int c2 = c1 + 1; c2 < nw; c2++) {
					List<String> cand = List.of(String.join(" ", words.subList(0, c1)), String.join(" ", words.subList(c1, c2)),
							String.join(" ", words.subList(c2, nw)));
					int w = widest(cand);
					if (w < bestW) {
						bestW = w;
						best = cand;
					}
				}
			}
			out.add(best);
		}
		return out;
	}

	private int widest(List<String> lines) {
		int w = 0;
		for (String l : lines) {
			w = Math.max(w, this.font.width(l));
		}
		return w;
	}

	/**
	 * Whether a box of half-size {@code hw x hh} centred at ({@code x}, {@code y}) (relative to the wheel centre)
	 * lies inside the ring segment around angle {@code mid} (sides pulled in by {@code gap} px), with a 2px margin.
	 */
	private static boolean fits(float x, float y, float hw, float hh, float r0, float r1, double mid, double halfSpan,
			double gap) {
		for (int sx = -1; sx <= 1; sx++) {
			for (int sy = -1; sy <= 1; sy++) {
				if (sx == 0 && sy == 0) {
					continue;
				}
				double px = x + sx * hw;
				double py = y + sy * hh;
				double r = Math.hypot(px, py);
				if (r < r0 + 2 || r > r1 - 2) {
					return false;
				}
				if (halfSpan < Math.PI) {
					double d = Math.atan2(py, px) - mid;
					d = Math.atan2(Math.sin(d), Math.cos(d));
					double room = halfSpan - Math.abs(d);
					if (room <= 0 || r * Math.sin(Math.min(Math.PI / 2, room)) < gap + 2) {
						return false;
					}
				}
			}
		}
		return true;
	}

	private void drawCentre(GuiGraphics g, float cx, float cy, float disc, float open) {
		int x = Math.round(cx);
		int textW = Math.max(30, Math.round(disc * 1.8f));
		String kicker;
		String name;
		String alias = null;
		String info;
		boolean active = false;
		int nameColour = WHITE;
		if (hovered >= 1 && hovered < entries.size()) {
			Entry e = entries.get(hovered);
			kicker = Component.translatable(e.power.category().translationKey()).getString();
			name = shortName(e);
			String full = e.label.getString();
			if (full.length() > name.length() + 3) {
				alias = full.substring(name.length() + 3);
			}
			info = (e.power.abilities().isEmpty()
					? Component.translatable("screen.projecthero.power_select.passive_only")
					: Component.translatable("screen.projecthero.power_select.abilities", e.power.abilities().size())).getString();
			active = e.active;
		} else if (hovered == 0) {
			Entry e = entries.get(0);
			kicker = Component.translatable(heroClassNone
					? "screen.projecthero.power_select.hero_class"
					: "screen.projecthero.power_select.mutation").getString();
			name = Component.translatable(heroClassNone
					? "projecthero.spider_man.name"
					: "screen.projecthero.power_select.none_short").getString();
			info = Component.translatable(heroClassNone
					? "screen.projecthero.power_select.none_hero_desc"
					: "screen.projecthero.power_select.none_desc").getString();
			active = e.active;
		} else {
			kicker = null;
			name = this.title.getString().toUpperCase(Locale.ROOT);
			nameColour = TEXT_NAME;
			info = Component.translatable(entries.size() > 1
					? "screen.projecthero.power_select.cancel"
					: "screen.projecthero.power_select.no_mutations").getString();
		}

		// the name sets the size; the secondary lines are never larger than it
		List<Line> nameLines = new ArrayList<>();
		float nameScale = addName(nameLines, name, textW, nameColour);
		float small = Math.min(smallScale(), nameScale);
		List<Line> block = new ArrayList<>();
		if (kicker != null) {
			block.add(new Line(kicker.toUpperCase(Locale.ROOT), TEXT_LABEL, fitSmall(kicker.toUpperCase(Locale.ROOT), small, textW)));
		}
		block.addAll(nameLines);
		if (alias != null) {
			block.add(new Line(alias, TEXT_NAME, fitSmall(alias, small, textW)));
		}
		block.add(new Line(info, TEXT_IDLE, fitSmall(info, small, textW)));
		if (active) {
			String a = Component.translatable("screen.projecthero.power_select.active").getString().toUpperCase(Locale.ROOT);
			block.add(new Line(a, WHITE, fitSmall(a, small, textW)));
		}

		// every line is width-capped to the disc: wrapped onto at most two rows, the second cut with "..." if needed
		List<List<FormattedCharSequence>> rows = new ArrayList<>();
		float total = 0f;
		for (Line l : block) {
			int maxW = (int) Math.floor(textW / l.scale);
			// greedy wrap on word boundaries only -- a word is never split; one too long for a row is cut with "..."
			List<String> parts = new ArrayList<>();
			String row = "";
			for (String word : l.text.getString().split(" ")) {
				String next = row.isEmpty() ? word : row + " " + word;
				if (!row.isEmpty() && this.font.width(Component.literal(next).withStyle(l.text.getStyle())) > maxW) {
					parts.add(row);
					row = word;
				} else {
					row = next;
				}
			}
			parts.add(row);
			if (parts.size() > 2) {
				parts = List.of(parts.get(0), String.join(" ", parts.subList(1, parts.size())));
			}
			List<FormattedCharSequence> seqs = new ArrayList<>();
			for (String part : parts) {
				seqs.add(capped(Component.literal(part).withStyle(l.text.getStyle()), maxW));
			}
			rows.add(seqs);
			total += 10f * l.scale * seqs.size();
		}
		float y = cy - total / 2f + 1f;
		for (int li = 0; li < block.size(); li++) {
			Line l = block.get(li);
			for (FormattedCharSequence seq : rows.get(li)) {
				g.pose().pushPose();
				g.pose().translate(x, y, 0);
				g.pose().scale(l.scale, l.scale, 1f);
				int w = this.font.width(seq);
				g.drawString(this.font, seq, -w / 2, 0, fade(l.colour, open), false);
				g.pose().popPose();
				y += 10f * l.scale;
			}
		}
	}

	/** {@code base}, or the next smaller whole-pixel scale that fits {@code text} into {@code textW}. */
	private float fitSmall(String text, float base, int textW) {
		int w = this.font.width(text);
		float out = base;
		for (float sc : labelScales()) {
			if (sc > base + 0.001f) {
				continue;
			}
			out = sc;
			if (w * sc <= textW) {
				break;
			}
		}
		return out;
	}

	/**
	 * Adds a bold name, never broken mid-word: one line or a balanced two-line split, shrunk to fit the disc.
	 *
	 * @return the (whole-pixel) scale it was set at
	 */
	private float addName(List<Line> block, String name, int textW, int colour) {
		List<String> bestLines = List.of(name);
		float bestScale = 0f;
		for (List<String> lines : layouts(name, 2)) {
			int widest = 0;
			for (String l : lines) {
				widest = Math.max(widest, this.font.width(Component.literal(l).withStyle(ChatFormatting.BOLD)));
			}
			float sc = Math.min(1f, textW / (float) Math.max(1, widest));
			if (sc > bestScale + 0.001f) {
				bestScale = sc;
				bestLines = lines;
			}
		}
		// snap down to a whole-pixel scale so the bold name stays crisp
		float snapped = 1f;
		for (float sc : labelScales()) {
			snapped = sc;
			if (sc <= bestScale + 0.001f) {
				break;
			}
		}
		for (String l : bestLines) {
			block.add(new Line(Component.literal(l).withStyle(ChatFormatting.BOLD), colour, snapped));
		}
		return snapped;
	}

	/** The "no mutation" choice: a pill under the ring (white-rimmed when hovered, bright-rimmed when active). */
	private void drawPill(GuiGraphics g, Entry none, float open) {
		float he = smooth(hoverAnim.length > 0 ? hoverAnim[0] : 0f);
		int fill = lerpColour(none.active ? SEG_FILL_ACTIVE : SEG_FILL, SEG_FILL_HOVER, he);
		int edge = lerpColour(none.active ? BORDER_ACTIVE : BORDER, BORDER_HOVER, he);
		g.fill(pillX0 + 1, pillY0, pillX1 - 1, pillY1, fade(fill, open));
		g.fill(pillX0, pillY0 + 1, pillX0 + 1, pillY1 - 1, fade(fill, open));
		g.fill(pillX1 - 1, pillY0 + 1, pillX1, pillY1 - 1, fade(fill, open));
		// 1px outline with clipped corners
		g.fill(pillX0 + 2, pillY0, pillX1 - 2, pillY0 + 1, fade(edge, open));
		g.fill(pillX0 + 2, pillY1 - 1, pillX1 - 2, pillY1, fade(edge, open));
		g.fill(pillX0, pillY0 + 2, pillX0 + 1, pillY1 - 2, fade(edge, open));
		g.fill(pillX1 - 1, pillY0 + 2, pillX1, pillY1 - 2, fade(edge, open));
		g.fill(pillX0 + 1, pillY0 + 1, pillX0 + 2, pillY0 + 2, fade(edge, open));
		g.fill(pillX1 - 2, pillY0 + 1, pillX1 - 1, pillY0 + 2, fade(edge, open));
		g.fill(pillX0 + 1, pillY1 - 2, pillX0 + 2, pillY1 - 1, fade(edge, open));
		g.fill(pillX1 - 2, pillY1 - 2, pillX1 - 1, pillY1 - 1, fade(edge, open));
		// a small marker dot: white when this is the active choice, grey otherwise
		int dotY = (pillY0 + pillY1) / 2 - 1;
		g.fill(pillX0 + 7, dotY, pillX0 + 10, dotY + 3, fade(none.active ? WHITE : BORDER, open));
		int colour = fade(lerpColour(none.active ? TEXT_NAME : TEXT_IDLE, WHITE, he), open);
		g.drawString(this.font, capped(none.label, pillX1 - pillX0 - 26), pillX0 + 15, pillY0 + 4, colour, false);
	}

	/** One pip per mutation slot: filled for an owned power, a dim dashed outline for an empty slot. */
	private void drawPips(GuiGraphics g, int cx, int y, int owned, float open) {
		int capacity = Mth.clamp(HeroConfig.get().mutationCapacity, 1, MAX_PIPS);
		int slots = Math.max(owned, capacity);
		if (slots > MAX_PIPS) {
			slots = owned;
		}
		Component label = Component.translatable("screen.projecthero.power_select.slots", owned, slots);
		int size = 5;
		int gap = 4;
		int pipsW = slots * size + (slots - 1) * gap;
		int labelW = this.font.width(label);
		int totalW = labelW + 8 + pipsW;
		int x = cx - totalW / 2;
		y = Math.max(2, y);
		g.drawString(this.font, label, x, y, fade(TEXT_LABEL, open), false);
		int px = x + labelW + 8;
		int py = y + 1;
		for (int k = 0; k < slots; k++) {
			int x0 = px + k * (size + gap);
			if (k < owned) {
				g.fill(x0, py, x0 + size, py + size, fade(BORDER_ACTIVE, open));
			} else {
				// dashed outline: every other pixel round the square
				for (int d = 0; d < size; d += 2) {
					g.fill(x0 + d, py, x0 + d + 1, py + 1, fade(0xFF5A5A5A, open));
					g.fill(x0 + d, py + size - 1, x0 + d + 1, py + size, fade(0xFF5A5A5A, open));
					g.fill(x0, py + d, x0 + 1, py + d + 1, fade(0xFF5A5A5A, open));
					g.fill(x0 + size - 1, py + d, x0 + size, py + d + 1, fade(0xFF5A5A5A, open));
				}
			}
		}
	}

	private static final class Line {
		final Component text;
		final int colour;
		final float scale;

		Line(String text, int colour, float scale) {
			this(Component.literal(text), colour, scale);
		}

		Line(Component text, int colour, float scale) {
			this.text = text;
			this.colour = colour;
			this.scale = scale;
		}
	}

	/** {@code text} cut to at most {@code maxW} px with a trailing "..." if it would run wider -- nothing sprawls. */
	private FormattedCharSequence capped(Component text, int maxW) {
		if (this.font.width(text) <= maxW) {
			return text.getVisualOrderText();
		}
		String dots = "...";
		net.minecraft.network.chat.FormattedText cut = this.font.substrByWidth(text, Math.max(0, maxW - this.font.width(dots)));
		return net.minecraft.locale.Language.getInstance().getVisualOrder(
				net.minecraft.network.chat.FormattedText.composite(cut, net.minecraft.network.chat.FormattedText.of(dots)));
	}

	private static String monogram(Entry e) {
		String name = e.label.getString().trim();
		String[] words = name.split("[\\s\\-/]+");
		StringBuilder b = new StringBuilder();
		for (String w : words) {
			if (!w.isEmpty() && b.length() < 2) {
				b.append(Character.toUpperCase(w.charAt(0)));
			}
		}
		if (b.length() < 2 && name.length() >= 2) {
			b.setLength(0);
			b.append(Character.toUpperCase(name.charAt(0))).append(Character.toLowerCase(name.charAt(1)));
		}
		return b.toString();
	}

	private static float smooth(float h) {
		return h * h * (3f - 2f * h);
	}

	/** Scales a colour's alpha by {@code a} (kept above the font renderer's "treat as opaque" threshold). */
	private static int fade(int argb, float a) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(a, 0f, 1f));
		return (Math.max(5, alpha) << 24) | (argb & 0xFFFFFF);
	}

	private static int lerpColour(int c0, int c1, float t) {
		int a = Math.round(Mth.lerp(t, (c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF));
		int r = Math.round(Mth.lerp(t, (c0 >> 16) & 0xFF, (c1 >> 16) & 0xFF));
		int gg = Math.round(Mth.lerp(t, (c0 >> 8) & 0xFF, (c1 >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, c0 & 0xFF, c1 & 0xFF));
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}

	/** An annular sector (a donut wedge) from angle {@code a0} to {@code a1}, radii {@code r0..r1}, as GUI quads. */
	private static void sector(VertexConsumer vc, Matrix4f m, float cx, float cy, float r0, float r1, double a0, double a1,
			int argb) {
		int steps = Math.max(2, (int) Math.ceil((a1 - a0) / (Math.PI / 72)));
		for (int k = 0; k < steps; k++) {
			double t0 = a0 + (a1 - a0) * k / steps;
			double t1 = a0 + (a1 - a0) * (k + 1) / steps;
			float c0 = (float) Math.cos(t0);
			float s0 = (float) Math.sin(t0);
			float c1 = (float) Math.cos(t1);
			float s1 = (float) Math.sin(t1);
			vc.addVertex(m, cx + c0 * r1, cy + s0 * r1, 0).setColor(argb);
			vc.addVertex(m, cx + c0 * r0, cy + s0 * r0, 0).setColor(argb);
			vc.addVertex(m, cx + c1 * r0, cy + s1 * r0, 0).setColor(argb);
			vc.addVertex(m, cx + c1 * r1, cy + s1 * r1, 0).setColor(argb);
		}
	}

	/**
	 * A ring segment between the nominal edge angles {@code e0..e1}, radii {@code r0..r1}, whose two sides are
	 * pulled in by a constant {@code gap} px (so neighbouring segments leave a parallel-sided gap, not a wedge).
	 */
	private static void band(VertexConsumer vc, Matrix4f m, float cx, float cy, float r0, float r1, double e0, double e1,
			float gap, int argb) {
		if (r1 <= r0 || r0 < 0) {
			return;
		}
		double gi = r0 <= gap ? Math.PI / 2 : Math.asin(Math.min(1, gap / r0));
		double go = Math.asin(Math.min(1, gap / r1));
		double i0 = e0 + gi;
		double i1 = e1 - gi;
		double o0 = e0 + go;
		double o1 = e1 - go;
		if (o1 <= o0) {
			return;
		}
		if (i1 < i0) {
			i0 = i1 = (e0 + e1) / 2;
		}
		int steps = Math.max(2, (int) Math.ceil((o1 - o0) / (Math.PI / 72)));
		for (int k = 0; k < steps; k++) {
			double t0 = (double) k / steps;
			double t1 = (double) (k + 1) / steps;
			double ai0 = i0 + (i1 - i0) * t0;
			double ai1 = i0 + (i1 - i0) * t1;
			double ao0 = o0 + (o1 - o0) * t0;
			double ao1 = o0 + (o1 - o0) * t1;
			vc.addVertex(m, cx + (float) Math.cos(ao0) * r1, cy + (float) Math.sin(ao0) * r1, 0).setColor(argb);
			vc.addVertex(m, cx + (float) Math.cos(ai0) * r0, cy + (float) Math.sin(ai0) * r0, 0).setColor(argb);
			vc.addVertex(m, cx + (float) Math.cos(ai1) * r0, cy + (float) Math.sin(ai1) * r0, 0).setColor(argb);
			vc.addVertex(m, cx + (float) Math.cos(ao1) * r1, cy + (float) Math.sin(ao1) * r1, 0).setColor(argb);
		}
	}

	/**
	 * The 1px straight side outline of a {@link #band} along nominal edge angle {@code e}, sitting {@code gap} px
	 * inside it; {@code dir} is +1 for a segment's start edge and -1 for its end edge.
	 */
	private static void side(VertexConsumer vc, Matrix4f m, float cx, float cy, float r0, float r1, double e, float gap,
			float dir, int argb) {
		float c = (float) Math.cos(e);
		float s = (float) Math.sin(e);
		float tx = -s * dir;
		float ty = c * dir;
		float d0 = (float) Math.sqrt(Math.max(0, r0 * r0 - gap * gap));
		float d1 = (float) Math.sqrt(Math.max(0, r1 * r1 - gap * gap));
		float ox = tx * gap;
		float oy = ty * gap;
		vc.addVertex(m, cx + c * d1 + ox, cy + s * d1 + oy, 0).setColor(argb);
		vc.addVertex(m, cx + c * d0 + ox, cy + s * d0 + oy, 0).setColor(argb);
		vc.addVertex(m, cx + c * d0 + ox + tx, cy + s * d0 + oy + ty, 0).setColor(argb);
		vc.addVertex(m, cx + c * d1 + ox + tx, cy + s * d1 + oy + ty, 0).setColor(argb);
	}

	/** Called when the open key is released (weapon-wheel style): pick the hovered choice, or cancel. */
	public void confirmAndClose() {
		if (hovered >= 0 && hovered < entries.size()) {
			ClientPlayNetworking.send(new PowerSelectPayload(entries.get(hovered).key()));
		}
		onClose();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < entries.size()) {
			ClientPlayNetworking.send(new PowerSelectPayload(entries.get(hovered).key()));
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
