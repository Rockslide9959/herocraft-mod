package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.network.SymbioteBondResultPayload;
import com.projecthero.mod.symbiote.SymbioteBondGame;
import com.projecthero.mod.symbiote.SymbioteBondGame.State;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The Symbiote bonding minigame screen.
 *
 * <p>v0.17.7 redesign (user: "change the bonding UI and bonding minigame, make it harder to beat"): a struggle in three
 * phases -- Contact, Resistance, Merge -- on a dark violet panel in the house style (the colour screen's gradient panel,
 * dim accent frame and divider). A phase strip across the top, a one-line tip for the phase, the track with the zone and
 * its bright perfect core, the Bond meter (a Slab bar) that the Symbiote drains, and the strike pips. Hits / perfects /
 * misses float under the track; a miss shakes the panel and the meter flashes red when it is nearly empty.
 *
 * <p>The rules all live in {@link SymbioteBondGame#simulate} (shared with the server, which replays the press times from
 * the same seed). This screen only records when the player struck and draws the simulation's state every frame, so what
 * the player sees is exactly what the server will judge. Presses are timed to the fractional tick
 * ({@link SymbioteBondGame#SUBTICKS}) at which the smoothly drawn marker was seen. Every text line is word-wrapped to the
 * panel.
 */
public class SymbioteBondGameScreen extends Screen {
	private static final int PANEL_MAX_W = 300;
	private static final int TRACK_H = 20;
	private static final int METER_H = 8;
	/** Ticks the result stays up before the screen closes. */
	private static final int RESULT_HOLD = 34;
	/** Motion trail: ghost markers this many ticks apart behind the real one. */
	private static final int TRAIL = 6;
	private static final float TRAIL_STEP = 0.35f;
	private static final int SHAKE_TICKS = 8;

	private static final int ACCENT = 0xFFEDE6F7;
	private static final int ACCENT_DIM = 0xFF3B2A4C;
	private static final int MUTED = 0xFFA898BC;
	private static final int PANEL_TOP = 0xF2140C1E;
	private static final int PANEL_BOTTOM = 0xF207040A;
	private static final int TRACK_BG = 0xFF1C1028;
	private static final int ZONE_DIM = 0x2E9F55;
	private static final int ZONE_BRIGHT = 0x4FD07E;
	private static final int CORE = 0xB8FFD0;
	private static final int METER_LO = 0x4A1E7A;
	private static final int METER_HI = 0xA866F0;
	private static final int DANGER = 0xD03040;

	private final int seed;
	/** Press times in 1/{@link SymbioteBondGame#SUBTICKS} of a tick since the screen opened. */
	private final List<Integer> presses = new ArrayList<>();
	private int tick;
	/** The decided state once the game is over (null while playing). */
	private State end;
	/** Whether the player let go with Esc (shown as its own reason). */
	private boolean gaveUp;
	private int outcomeTick;
	private boolean sent;

	private int panelW;
	private int left;
	private int top;

	public SymbioteBondGameScreen(int seed) {
		super(Component.translatable("screen.projecthero.symbiote_bond_game"));
		this.seed = seed;
	}

	@Override
	protected void init() {
		panelW = Math.min(PANEL_MAX_W, width - 16);
		left = (width - panelW) / 2;
		top = Math.max(8, height / 2 - 84);
	}

	@Override
	public boolean isPauseScreen() {
		return false; // the marker runs on ticks; pausing would freeze it in single player
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // handled in keyPressed so the back-out is always reported
	}

	/** The screen's clock right now, in (fractional) ticks. */
	private double clock(float partialTick) {
		return tick + Mth.clamp(partialTick, 0.0f, 0.999f);
	}

	private static float partialNow() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	private State state(double now) {
		return end != null ? end : SymbioteBondGame.simulate(seed, presses, now);
	}

	@Override
	public void tick() {
		tick++;
		if (end == null) {
			State st = SymbioteBondGame.simulate(seed, presses, tick);
			if (!st.playing()) {
				finish(st); // drained or too slow
			}
		} else if (tick - outcomeTick > RESULT_HOLD) {
			onClose();
		}
	}

	private void press() {
		double now = clock(partialNow());
		State st = state(now);
		if (end != null || !st.accepts(now) || presses.size() >= SymbioteBondGame.MAX_PRESSES) {
			return; // over, between phases, or the marker is still flinching from the last strike
		}
		int stamp = (int) Math.floor(now * SymbioteBondGame.SUBTICKS);
		if (stamp / (double) SymbioteBondGame.SUBTICKS < st.phaseStart()) {
			return;
		}
		presses.add(stamp);
		State after = SymbioteBondGame.simulate(seed, presses, stamp / (double) SymbioteBondGame.SUBTICKS);
		if (!after.playing()) {
			finish(after);
		}
	}

	private void finish(State st) {
		end = st;
		outcomeTick = tick;
		List<Integer> report = new ArrayList<>(presses);
		if (st.outcome() != SymbioteBondGame.WON) {
			report.add(SymbioteBondGame.GAVE_UP);
		}
		send(report);
	}

	private void send(List<Integer> list) {
		if (!sent) {
			sent = true;
			ClientPlayNetworking.send(new SymbioteBondResultPayload(new ArrayList<>(list)));
		}
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == GLFW.GLFW_KEY_ESCAPE) {
			if (end == null) {
				if (presses.isEmpty() && tick <= SymbioteBondGame.FREE_BACK_OUT) {
					send(List.of()); // backed out before it began: free
				} else {
					gaveUp = true;
					finish(SymbioteBondGame.simulate(seed, presses, clock(partialNow())));
				}
			}
			onClose();
			return true;
		}
		if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			press();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean mouseClicked(double x, double y, int button) {
		press();
		return true;
	}

	@Override
	public void onClose() {
		if (!sent) {
			if (end == null && presses.isEmpty() && tick <= SymbioteBondGame.FREE_BACK_OUT) {
				send(List.of());
			} else {
				List<Integer> report = new ArrayList<>(presses);
				if (end == null || end.outcome() != SymbioteBondGame.WON) {
					report.add(SymbioteBondGame.GAVE_UP);
				}
				send(report);
			}
		}
		super.onClose();
	}

	// ------------------------------------------------------------------ drawing

	private static int argb(float a, int rgb) {
		return (Mth.clamp(Math.round(a * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	private static int lerpColor(int a, int b, float f) {
		int r = Math.round(Mth.lerp(f, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int gg = Math.round(Mth.lerp(f, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int bl = Math.round(Mth.lerp(f, a & 0xFF, b & 0xFF));
		return (r << 16) | (gg << 8) | bl;
	}

	private List<FormattedCharSequence> wrap(Component c, int w) {
		return font.split(c, Math.max(40, w));
	}

	private int centred(GuiGraphics g, List<FormattedCharSequence> lines, int cx, int y, int colour) {
		for (FormattedCharSequence line : lines) {
			g.drawCenteredString(font, line, cx, y, colour);
			y += 10;
		}
		return y;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// the world stays visible behind a violet wash; the panel itself is drawn in render (it shakes)
		g.fill(0, 0, width, height, 0x9A0C0414);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		double now = clock(partialTick);
		State st = state(now);
		boolean over = end != null;
		boolean inGap = !over && now < st.phaseStart() && st.phase() > 0;
		int phase = over ? Math.max(0, Math.min(SymbioteBondGame.PHASES - 1, st.lastPhase() < 0 ? st.phase() : st.lastPhase()))
				: inGap ? st.phase() - 1 : st.phase();
		double meter = over ? st.meter() : inGap ? 1.0 : st.meter();
		float breathe = 0.5f + 0.5f * Mth.sin((float) now * 0.35f);

		// a miss shakes the panel
		double sinceStrike = now - st.lastAt();
		int shake = 0;
		if (st.lastResult() == SymbioteBondGame.RESULT_MISS && sinceStrike >= 0.0 && sinceStrike < SHAKE_TICKS) {
			shake = Math.round(Mth.sin((float) sinceStrike * 2.6f) * 3.0f * (1.0f - (float) sinceStrike / SHAKE_TICKS));
		}
		// danger: the screen edges pulse red as the meter nears empty
		if (!over && !inGap && meter < 0.3) {
			float a = (float) ((0.3 - meter) / 0.3) * (0.25f + 0.2f * breathe);
			g.fillGradient(0, 0, width, 40, argb(a, DANGER), argb(0.0f, DANGER));
			g.fillGradient(0, height - 40, width, height, argb(0.0f, DANGER), argb(a, DANGER));
		}

		int inner = panelW - 24;
		int cx = left + panelW / 2 + shake;
		int x0 = left + 12 + shake;
		List<FormattedCharSequence> tip = wrap(Component.translatable("screen.projecthero.symbiote_bond_game.phase." + phase + ".tip"), inner);
		List<FormattedCharSequence> controls = wrap(Component.translatable("screen.projecthero.symbiote_bond_game.controls"), inner);
		int tipH = Math.min(2, tip.size()) * 10;
		int panelH = 6 + 13 + 6 + 14 + 6 + tipH + 8 + TRACK_H + 6 + 12 + 12 + METER_H + 8 + controls.size() * 10 + 6;

		// panel (house style: dim frame, violet gradient, a highlight line, a divider under the title)
		int px = left + shake;
		g.fill(px - 1, top - 1, px + panelW + 1, top + panelH + 1, ACCENT_DIM);
		g.fillGradient(px, top, px + panelW, top + panelH, PANEL_TOP, PANEL_BOTTOM);
		g.fill(px + 2, top + 2, px + panelW - 2, top + 3, 0x30FFFFFF);
		int y = top + 6;
		g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), cx, y, ACCENT);
		y += 13;
		g.fill(px + 10, y, px + panelW - 10, y + 1, ACCENT_DIM);
		y += 6;

		// phase strip
		int segW = inner / SymbioteBondGame.PHASES;
		int donePhases = over && st.outcome() == SymbioteBondGame.WON ? SymbioteBondGame.PHASES : inGap ? st.phase() : phase;
		for (int p = 0; p < SymbioteBondGame.PHASES; p++) {
			int sx = x0 + p * segW;
			int col;
			int text;
			if (p < donePhases) {
				col = 0xFF8A4FD0;
				text = ACCENT;
			} else if (p == phase && !over) {
				col = argb(1.0f, lerpColor(0x5A3A88, 0xB890F0, breathe));
				text = ACCENT;
			} else if (p == phase && st.outcome() == SymbioteBondGame.FAILED) {
				col = 0xFF8A2A34;
				text = 0xFFE0A0A8;
			} else {
				col = 0xFF2E2240;
				text = 0xFF6E6080;
			}
			g.fill(sx + 2, y + 11, sx + segW - 2, y + 14, col);
			Component name = Component.translatable("screen.projecthero.symbiote_bond_game.phase." + p);
			List<FormattedCharSequence> nm = wrap(name, segW - 4);
			if (!nm.isEmpty()) {
				g.drawCenteredString(font, nm.get(0), sx + segW / 2, y + 1, text);
			}
		}
		y += 14 + 6;

		// the phase's tip (at most two wrapped lines)
		centred(g, tip.subList(0, Math.min(2, tip.size())), cx, y, MUTED);
		y += tipH + 8;

		// ---- the track ----
		int ty = y;
		g.fill(x0 - 2, ty - 2, x0 + inner + 2, ty + TRACK_H + 2, 0xFF000000);
		g.fill(x0, ty, x0 + inner, ty + TRACK_H, TRACK_BG);
		boolean live = !over && !inGap;
		double t = now - st.phaseStart();
		double centre;
		double half;
		if (live) {
			centre = SymbioteBondGame.zoneCenter(seed, phase, t);
			half = SymbioteBondGame.halfZone(phase, meter);
		} else if (st.lastPhase() >= 0) {
			centre = st.lastCentre();
			half = st.lastHalf();
		} else {
			centre = SymbioteBondGame.zoneCenter(seed, phase, 0.0);
			half = SymbioteBondGame.halfZone(phase, meter);
		}
		int zl = x0 + (int) Math.round((centre - half) * inner);
		int zr = x0 + (int) Math.round((centre + half) * inner);
		g.fill(zl, ty, zr, ty + TRACK_H, argb(1.0f, lerpColor(ZONE_DIM, ZONE_BRIGHT, breathe)));
		g.fill(zl, ty, zl + 1, ty + TRACK_H, argb(1.0f, CORE));
		g.fill(zr - 1, ty, zr, ty + TRACK_H, argb(1.0f, CORE));
		int cl = x0 + (int) Math.round((centre - half * SymbioteBondGame.CORE) * inner);
		int cr = x0 + (int) Math.round((centre + half * SymbioteBondGame.CORE) * inner);
		g.fill(cl, ty + 3, Math.max(cl + 1, cr), ty + TRACK_H - 3, argb(0.55f + 0.25f * breathe, CORE));

		boolean flinch = live && sinceStrike >= 0.0 && sinceStrike < SymbioteBondGame.LOCKOUT;
		if (live && !flinch) {
			for (int i = TRAIL; i >= 1; i--) {
				double tt = t - i * TRAIL_STEP;
				if (tt < 0.0) {
					continue;
				}
				int gx = x0 + (int) Math.round(SymbioteBondGame.marker(seed, phase, tt) * inner);
				float a = 0.28f * (1.0f - i / (float) (TRAIL + 1));
				g.fill(gx - 1, ty + 1, gx + 1, ty + TRACK_H - 1, argb(a, 0xE0D0FF));
			}
		}
		double m = live ? SymbioteBondGame.marker(seed, phase, t) : st.lastPos() >= 0.0 ? st.lastPos() : SymbioteBondGame.marker(seed, phase, 0.0);
		int mx = x0 + (int) Math.round(m * inner);
		boolean inside = Math.abs(m - centre) <= half + SymbioteBondGame.EDGE_GRACE;
		int jolt = flinch ? Math.round(Mth.sin((float) sinceStrike * 4.0f) * 2.0f) : 0;
		g.fill(mx - 2, ty - 6 + jolt, mx + 2, ty + TRACK_H + 6 + jolt, 0xFF0A0A0A);
		int markerCol = flinch ? 0xFF7A6A90 : live && inside ? 0xFFF4FFF8 : 0xFFE0D0FF;
		g.fill(mx - 1, ty - 4 + jolt, mx + 1, ty + TRACK_H + 4 + jolt, markerCol);
		y = ty + TRACK_H + 6;

		// strike feedback under the track, fading out
		if (st.lastResult() != SymbioteBondGame.RESULT_NONE && sinceStrike >= 0.0 && sinceStrike < 14.0 && !over) {
			float a = 1.0f - (float) (sinceStrike / 14.0);
			String key = st.lastResult() == SymbioteBondGame.RESULT_PERFECT ? "perfect"
					: st.lastResult() == SymbioteBondGame.RESULT_HIT ? "hit" : "miss";
			int col = st.lastResult() == SymbioteBondGame.RESULT_PERFECT ? CORE : st.lastResult() == SymbioteBondGame.RESULT_HIT ? ZONE_BRIGHT : DANGER;
			Component word = Component.translatable("screen.projecthero.symbiote_bond_game." + key).withStyle(ChatFormatting.BOLD);
			int fx = Mth.clamp(x0 + (int) Math.round(st.lastPos() * inner), x0 + font.width(word) / 2, x0 + inner - font.width(word) / 2);
			g.drawCenteredString(font, word, fx, y, argb(Math.max(0.05f, a), col));
			g.renderOutline(x0 - 3, ty - 3, inner + 6, TRACK_H + 6, argb(a, col));
		} else if (inGap) {
			g.drawCenteredString(font, Component.translatable("screen.projecthero.symbiote_bond_game.phase_clear")
					.withStyle(ChatFormatting.BOLD), cx, y, argb(1.0f, CORE));
		}
		y += 12;

		// ---- the Bond meter and the strike pips ----
		int pct = Mth.clamp((int) Math.round(meter * 100.0), 0, 100);
		Component bond = Component.translatable("screen.projecthero.symbiote_bond_game.bond", pct);
		Component strikesLabel = Component.translatable("screen.projecthero.symbiote_bond_game.strikes");
		int pipsW = SymbioteBondGame.MAX_STRIKES * 9;
		int strikesW = font.width(strikesLabel) + 4 + pipsW;
		List<FormattedCharSequence> bondLine = wrap(bond, inner - strikesW - 8);
		if (!bondLine.isEmpty()) {
			g.drawString(font, bondLine.get(0), x0, y, ACCENT);
		}
		int sx = x0 + inner - strikesW;
		g.drawString(font, strikesLabel, sx, y, MUTED);
		for (int i = 0; i < SymbioteBondGame.MAX_STRIKES; i++) {
			int pxs = sx + font.width(strikesLabel) + 4 + i * 9;
			boolean used = i < st.strikes();
			g.fill(pxs, y, pxs + 7, y + 7, used ? 0xFF000000 | DANGER : 0xFF2E2240);
			g.fill(pxs + 1, y + 1, pxs + 6, y + 6, used ? 0xFFF06070 : 0xFF3E3050);
		}
		y += 12;
		boolean danger = !over && !inGap && meter < 0.25;
		g.fill(x0 - 1, y - 1, x0 + inner + 1, y + METER_H + 1, danger ? argb(0.6f + 0.4f * breathe, DANGER) : 0xFF000000);
		g.fill(x0, y, x0 + inner, y + METER_H, 0xFF1A1024);
		int fillW = (int) Math.round(meter * inner);
		if (fillW > 0) {
			g.fillGradient(x0, y, x0 + fillW, y + METER_H, argb(1.0f, METER_HI), argb(1.0f, METER_LO));
			g.fill(x0, y, x0 + fillW, y + 1, 0x50FFFFFF);
		}
		// quarter ticks
		for (int q = 1; q < 4; q++) {
			int qx = x0 + inner * q / 4;
			g.fill(qx, y, qx + 1, y + METER_H, 0x40000000);
		}
		y += METER_H + 8;

		// controls
		centred(g, controls, cx, y, 0xFF7E7092);

		// the verdict, over the track
		if (over) {
			boolean won = st.outcome() == SymbioteBondGame.WON;
			float age = Mth.clamp((float) (now - outcomeTick) / 6.0f, 0.0f, 1.0f);
			g.fill(x0, ty, x0 + inner, ty + TRACK_H, argb(0.75f * age, won ? 0x1A0830 : 0x200608));
			Component verdict = Component.translatable(won ? "screen.projecthero.symbiote_bond_game.won"
					: "screen.projecthero.symbiote_bond_game.lost").withStyle(ChatFormatting.BOLD);
			g.drawCenteredString(font, verdict, cx, ty + (TRACK_H - 8) / 2, argb(Math.max(0.05f, age), won ? 0xE0B8FF : 0xFF6070));
			if (!won) {
				String reason = gaveUp ? "gave_up" : switch (st.reason()) {
					case SymbioteBondGame.REASON_STRIKES -> "strikes";
					case SymbioteBondGame.REASON_TIMEOUT -> "timeout";
					default -> "drained";
				};
				List<FormattedCharSequence> why = wrap(Component.translatable("screen.projecthero.symbiote_bond_game.reason." + reason), inner);
				if (!why.isEmpty()) {
					g.drawCenteredString(font, why.get(0), cx, ty + TRACK_H + 6, argb(Math.max(0.05f, age), 0xE0A0A8));
				}
			}
		}
	}
}
