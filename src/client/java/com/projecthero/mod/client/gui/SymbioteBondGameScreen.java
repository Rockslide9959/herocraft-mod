package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.network.SymbioteBondResultPayload;
import com.projecthero.mod.symbiote.SymbioteBondGame;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The Symbiote bonding minigame (v0.12.25). A marker sweeps across a bar; press Space / click / Enter to
 * stop it inside the glowing zone, three times in a row, faster and tighter each round. The maths lives in
 * {@link SymbioteBondGame} (shared with the server, which replays the press times from the same seed); this
 * screen only draws and records when the player pressed. One miss ends it; Esc backs out.
 *
 * <p>v0.13.15: smooth. The marker used to move once per game tick (20 steps a second, so it visibly skipped); it is now
 * drawn at the exact fractional tick every frame, with a short motion trail, and a press is timed to that same
 * fractional tick ({@link SymbioteBondGame#SUBTICKS}) so it lands exactly where the player saw the marker. The zone
 * breathes instead of blinking, and hits / misses fade out instead of popping.
 */
public class SymbioteBondGameScreen extends Screen {
	private static final int BAR_W = 260;
	private static final int BAR_H = 18;
	/** Ticks the result stays up before the screen closes. */
	private static final int RESULT_HOLD = 26;
	/** Motion trail: ghost markers this many ticks apart behind the real one. */
	private static final int TRAIL = 6;
	private static final float TRAIL_STEP = 0.35f;

	private final int seed;
	/** Press times in 1/{@link SymbioteBondGame#SUBTICKS} of a tick since the screen opened. */
	private final List<Integer> presses = new ArrayList<>();
	private int tick;
	/** Start of the current round, in ticks (fractional: it follows a fractional press). */
	private double roundStart;
	private int round;
	/** Once decided: 1 = won, -1 = failed, 0 = still playing. */
	private int outcome;
	private int outcomeTick;
	private boolean sent;
	/** Position (0..1) the marker was frozen at by the last press, for the feedback flash. */
	private double frozen = -1.0;
	private double flashAt = -100.0;
	private boolean flashHit;
	/** Which round the frozen marker belongs to (drawn in the gap before the next round starts). */
	private int frozenRound = -1;

	public SymbioteBondGameScreen(int seed) {
		super(Component.translatable("screen.projecthero.symbiote_bond_game"));
		this.seed = seed;
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

	@Override
	public void tick() {
		tick++;
		if (outcome == 0 && tick - roundStart > SymbioteBondGame.ROUND_TIMEOUT - 20) {
			finish(-1); // too slow
		}
		if (outcome != 0 && tick - outcomeTick > RESULT_HOLD) {
			onClose();
		}
	}

	private void press() {
		double now = clock(partialNow());
		if (outcome != 0 || now - roundStart < 3.0) {
			return;
		}
		int stamp = (int) Math.floor(now * SymbioteBondGame.SUBTICKS);
		double t = stamp / (double) SymbioteBondGame.SUBTICKS - roundStart;
		boolean ok = SymbioteBondGame.hit(seed, round, t);
		presses.add(stamp);
		frozen = SymbioteBondGame.marker(seed, round, t);
		frozenRound = round;
		flashAt = now;
		flashHit = ok;
		if (!ok) {
			finish(-1);
			return;
		}
		round++;
		roundStart = stamp / (double) SymbioteBondGame.SUBTICKS + SymbioteBondGame.ROUND_GAP;
		if (round >= SymbioteBondGame.ROUNDS) {
			finish(1);
		}
	}

	private void finish(int result) {
		outcome = result;
		outcomeTick = tick;
		send(presses);
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
			if (outcome == 0) {
				send(List.of()); // backed out
				outcome = -1;
				outcomeTick = -RESULT_HOLD; // close at once
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
			send(List.of());
		}
		super.onClose();
	}

	private static int argb(float a, int rgb) {
		return (Mth.clamp(Math.round(a * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		double now = clock(partialTick);
		g.fill(0, 0, width, height, 0xB0100418);
		int cx = width / 2;
		int top = height / 2 - 40;
		g.drawCenteredString(font, Component.translatable("screen.projecthero.symbiote_bond_game")
				.withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.BOLD), cx, top - 22, 0xFFFFFFFF);
		g.drawCenteredString(font, Component.translatable("screen.projecthero.symbiote_bond_game.hint"), cx, top - 8, 0xFFB0A0C0);

		// round pips: the one in play breathes
		float breathe = 0.5f + 0.5f * Mth.sin((float) now * 0.35f);
		for (int r = 0; r < SymbioteBondGame.ROUNDS; r++) {
			int px = cx - 24 + r * 24;
			int col;
			if (r < round) {
				col = 0xFF6A2BB0;
			} else if (r == round && outcome == 0) {
				col = argb(1.0f, lerpColor(0x7A58A8, 0xC8A8F0, breathe));
			} else {
				col = 0xFF3A2A4A;
			}
			g.fill(px - 6, top + 6, px + 6, top + 18, col);
		}

		int x0 = cx - BAR_W / 2;
		int y0 = top + 34;
		g.fill(x0 - 2, y0 - 2, x0 + BAR_W + 2, y0 + BAR_H + 2, 0xFF000000);
		g.fill(x0, y0, x0 + BAR_W, y0 + BAR_H, 0xFF221430);

		boolean inGap = outcome == 0 && now < roundStart && frozenRound >= 0;
		int shown = inGap ? frozenRound : Math.min(round, SymbioteBondGame.ROUNDS - 1);
		if (outcome != 0 && frozenRound >= 0) {
			shown = frozenRound;
		}
		double centre = SymbioteBondGame.zoneCenter(seed, shown);
		double half = SymbioteBondGame.HALF_ZONE[shown];
		int zl = x0 + (int) Math.round((centre - half) * BAR_W);
		int zr = x0 + (int) Math.round((centre + half) * BAR_W);
		// the zone breathes (a smooth glow, not a blink), with bright edges so its bounds are easy to read
		int zoneCol = lerpColor(0x2E9F55, 0x4FD07E, breathe);
		g.fill(zl, y0, zr, y0 + BAR_H, argb(1.0f, zoneCol));
		g.fill(zl, y0, zl + 1, y0 + BAR_H, 0xFFB8FFD0);
		g.fill(zr - 1, y0, zr, y0 + BAR_H, 0xFFB8FFD0);

		boolean live = outcome == 0 && !inGap;
		double t = now - roundStart;
		if (live) {
			// motion trail: fading ghosts where the marker just was
			for (int i = TRAIL; i >= 1; i--) {
				double tt = t - i * TRAIL_STEP;
				if (tt < 0.0) {
					continue;
				}
				int gx = x0 + (int) Math.round(SymbioteBondGame.marker(seed, shown, tt) * BAR_W);
				float a = 0.28f * (1.0f - i / (float) (TRAIL + 1));
				g.fill(gx - 1, y0 + 1, gx + 1, y0 + BAR_H - 1, argb(a, 0xE0D0FF));
			}
		}
		double m = live ? SymbioteBondGame.marker(seed, shown, t) : (frozen >= 0.0 ? frozen : SymbioteBondGame.marker(seed, shown, 0.0));
		int mx = x0 + (int) Math.round(m * BAR_W);
		boolean over = Math.abs(m - centre) <= half + SymbioteBondGame.EDGE_GRACE;
		g.fill(mx - 2, y0 - 6, mx + 2, y0 + BAR_H + 6, 0xFF0A0A0A);
		g.fill(mx - 1, y0 - 4, mx + 1, y0 + BAR_H + 4, live && over ? 0xFFF4FFF8 : 0xFFE0D0FF);

		// press feedback: a band that fades out, green on a hit, red on a miss
		double since = now - flashAt;
		if (since >= 0.0 && since < 10.0) {
			float a = 1.0f - (float) (since / 10.0);
			int col = flashHit ? 0x3FBF6A : 0xC03030;
			g.fill(x0, y0 + BAR_H + 10, x0 + BAR_W, y0 + BAR_H + 13, argb(a, col));
			g.renderOutline(x0 - 3, y0 - 3, BAR_W + 6, BAR_H + 6, argb(a, col));
		}
		if (outcome > 0) {
			g.drawCenteredString(font, Component.translatable("screen.projecthero.symbiote_bond_game.won")
					.withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD), cx, y0 + BAR_H + 22, 0xFFFFFFFF);
		} else if (outcome < 0) {
			g.drawCenteredString(font, Component.translatable("screen.projecthero.symbiote_bond_game.lost")
					.withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD), cx, y0 + BAR_H + 22, 0xFFFFFFFF);
		} else {
			g.drawCenteredString(font, Component.translatable("screen.projecthero.symbiote_bond_game.round", round + 1,
					SymbioteBondGame.ROUNDS), cx, y0 + BAR_H + 22, 0xFF9080A8);
		}
	}

	private static int lerpColor(int a, int b, float f) {
		int r = Math.round(Mth.lerp(f, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int gg = Math.round(Mth.lerp(f, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int bl = Math.round(Mth.lerp(f, a & 0xFF, b & 0xFF));
		return (r << 16) | (gg << 8) | bl;
	}
}
