package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.network.SymbioteBondResultPayload;
import com.projecthero.mod.symbiote.SymbioteBondGame;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Symbiote bonding minigame (v0.12.25). A marker sweeps across a bar; press Space / click / Enter to
 * stop it inside the glowing zone, three times in a row, faster and tighter each round. The maths lives in
 * {@link SymbioteBondGame} (shared with the server, which replays the press ticks from the same seed); this
 * screen only draws and records when the player pressed. One miss ends it; Esc backs out.
 */
public class SymbioteBondGameScreen extends Screen {
	private static final int BAR_W = 240;
	private static final int BAR_H = 16;
	/** Ticks the result stays up before the screen closes. */
	private static final int RESULT_HOLD = 24;

	private final int seed;
	private final List<Integer> presses = new ArrayList<>();
	private int tick;
	private int roundStart;
	private int round;
	/** Once decided: 1 = won, -1 = failed, 0 = still playing. */
	private int outcome;
	private int outcomeTick;
	private boolean sent;
	/** Position (0..1) the marker was frozen at by the last press, for the feedback flash. */
	private double frozen = -1.0;
	private int flashTick = -100;
	private boolean flashHit;

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
		if (outcome != 0 || tick - roundStart < 3) {
			return;
		}
		int t = tick - roundStart;
		boolean ok = SymbioteBondGame.hit(seed, round, t);
		presses.add(tick);
		frozen = SymbioteBondGame.marker(seed, round, t);
		flashTick = tick;
		flashHit = ok;
		if (!ok) {
			finish(-1);
			return;
		}
		round++;
		roundStart = tick + SymbioteBondGame.ROUND_GAP;
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

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		g.fill(0, 0, width, height, 0xB0100418);
		int cx = width / 2;
		int top = height / 2 - 40;
		g.drawCenteredString(font, Component.translatable("screen.projecthero.symbiote_bond_game")
				.withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.BOLD), cx, top - 22, 0xFFFFFFFF);
		g.drawCenteredString(font, Component.translatable("screen.projecthero.symbiote_bond_game.hint"), cx, top - 8, 0xFFB0A0C0);

		// round pips
		for (int r = 0; r < SymbioteBondGame.ROUNDS; r++) {
			int px = cx - 24 + r * 24;
			int col = r < round ? 0xFF6A2BB0 : (r == round && outcome == 0 ? 0xFFB088E0 : 0xFF3A2A4A);
			g.fill(px - 6, top + 6, px + 6, top + 18, col);
		}

		int x0 = cx - BAR_W / 2;
		int y0 = top + 34;
		g.fill(x0 - 2, y0 - 2, x0 + BAR_W + 2, y0 + BAR_H + 2, 0xFF000000);
		g.fill(x0, y0, x0 + BAR_W, y0 + BAR_H, 0xFF221430);

		int shown = Math.min(round, SymbioteBondGame.ROUNDS - 1);
		double centre = SymbioteBondGame.zoneCenter(seed, shown);
		double half = SymbioteBondGame.HALF_ZONE[shown];
		int zl = x0 + (int) ((centre - half) * BAR_W);
		int zr = x0 + (int) ((centre + half) * BAR_W);
		boolean pulse = (tick / 4) % 2 == 0;
		g.fill(zl, y0, zr, y0 + BAR_H, pulse ? 0xFF3FBF6A : 0xFF2E9F55);

		double m;
		if (outcome != 0 && frozen >= 0.0) {
			m = frozen;
		} else {
			m = SymbioteBondGame.marker(seed, shown, tick - roundStart);
			if (tick - roundStart < 0) {
				m = SymbioteBondGame.marker(seed, shown, 0);
			}
		}
		int mx = x0 + (int) (m * BAR_W);
		g.fill(mx - 2, y0 - 6, mx + 2, y0 + BAR_H + 6, 0xFF0A0A0A);
		g.fill(mx - 1, y0 - 4, mx + 1, y0 + BAR_H + 4, 0xFFE0D0FF);

		if (tick - flashTick < 8) {
			g.fill(x0, y0 + BAR_H + 10, x0 + BAR_W, y0 + BAR_H + 13, flashHit ? 0xFF3FBF6A : 0xFFC03030);
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
}
