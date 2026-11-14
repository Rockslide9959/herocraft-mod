package com.projecthero.mod.client.doctorfate;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.doctorfate.network.FateActionPayload;
import com.projecthero.mod.doctorfate.network.FateBargainPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.17.3 Doctor Fate: one of Nabu's bargains -- a small two-button panel that pauses nothing. The offer and its terms wrap to the
 * panel (the tooltip-wrap rule); a gold bar runs down the ten seconds; ignoring it, Esc or Refuse all refuse. When he's refused
 * before today, a line says the offer got meaner.
 */
public class FateBargainScreen extends Screen {
	private static final int PANEL_W = 236;
	private static final int BG = 0xF0101830;
	private static final int EDGE = 0xFFE0B040;
	private static final int TEXT = 0xFFE8E8F0;
	private static final int GOLD = 0xFFFFD25A;

	private final FateBargainPayload offer;
	private final long openedMs = System.currentTimeMillis();
	private boolean answered;
	private int panelH = 150;

	public FateBargainScreen(FateBargainPayload offer) {
		super(Component.translatable("screen.projecthero.doctor_fate.bargain.title"));
		this.offer = offer;
	}

	private String kindKey() {
		return switch (offer.kind()) {
			case FateState.Bargain.DEATHS_DOOR -> "deaths_door";
			case FateState.Bargain.ORDERS_COMMAND -> "orders_command";
			default -> "forbidden";
		};
	}

	private List<FormattedCharSequence> bodyLines() {
		List<FormattedCharSequence> out = new ArrayList<>();
		String k = "screen.projecthero.doctor_fate.bargain." + kindKey();
		Component offerLine = Component.translatable(k + ".offer");
		Component terms = switch (offer.kind()) {
			case FateState.Bargain.DEATHS_DOOR -> Component.translatable(k + ".terms", Math.round(offer.a()), Math.round(offer.b() / 2f));
			case FateState.Bargain.ORDERS_COMMAND -> Component.translatable(k + ".terms", Math.round(offer.a()), Math.round(offer.b()), Math.round(offer.c()));
			default -> Component.translatable(k + ".terms", Math.round(offer.a()));
		};
		out.addAll(font.split(offerLine, PANEL_W - 24));
		out.add(FormattedCharSequence.EMPTY);
		out.addAll(font.split(terms, PANEL_W - 24));
		if (offer.kind() == FateState.Bargain.DEATHS_DOOR) {
			// v0.17.4 round 2: nothing can hurt him while he decides (DoctorFateDamage)
			out.add(FormattedCharSequence.EMPTY);
			out.addAll(font.split(Component.translatable("screen.projecthero.doctor_fate.bargain.deaths_door.held"), PANEL_W - 24));
		}
		if (offer.refusals() > 0) {
			out.add(FormattedCharSequence.EMPTY);
			out.addAll(font.split(Component.translatable("screen.projecthero.doctor_fate.bargain.meaner", offer.refusals()), PANEL_W - 24));
		}
		return out;
	}

	@Override
	protected void init() {
		panelH = 64 + bodyLines().size() * 10;
		int x0 = (width - PANEL_W) / 2;
		int y0 = (height - panelH) / 2;
		int bw = (PANEL_W - 36) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.doctor_fate.bargain.accept"), b -> answer(true))
				.bounds(x0 + 12, y0 + panelH - 28, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.doctor_fate.bargain.refuse"), b -> answer(false))
				.bounds(x0 + PANEL_W - 12 - bw, y0 + panelH - 28, bw, 20).build());
	}

	private void answer(boolean accept) {
		if (!answered) {
			answered = true;
			ClientPlayNetworking.send(new FateActionPayload(accept ? FateActionPayload.Action.BARGAIN_ACCEPT : FateActionPayload.Action.BARGAIN_REFUSE,
					offer.kind(), BlockPos.ZERO));
		}
		super.onClose();
	}

	private float left() {
		return Math.max(0f, offer.seconds() - (System.currentTimeMillis() - openedMs) / 1000f);
	}

	@Override
	public void tick() {
		super.tick();
		if (left() <= 0f) {
			answer(false); // ignoring it counts as refusing
		}
	}

	@Override
	public void onClose() {
		answer(false);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		// no dim, no blur: the fight behind stays visible
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		int x0 = (width - PANEL_W) / 2;
		int y0 = (height - panelH) / 2;
		g.fill(x0 - 1, y0 - 1, x0 + PANEL_W + 1, y0 + panelH + 1, EDGE);
		g.fill(x0, y0, x0 + PANEL_W, y0 + panelH, BG);
		Component heading = Component.translatable("screen.projecthero.doctor_fate.bargain." + kindKey() + ".title");
		int y = y0 + 8;
		for (FormattedCharSequence line : font.split(heading, PANEL_W - 24)) {
			g.drawString(font, line, x0 + (PANEL_W - font.width(line)) / 2, y, GOLD, true);
			y += 10;
		}
		// the ten seconds
		float frac = left() / Math.max(1, offer.seconds());
		g.fill(x0 + 12, y + 1, x0 + PANEL_W - 12, y + 3, 0xFF2A2A40);
		g.fill(x0 + 12, y + 1, x0 + 12 + Math.round((PANEL_W - 24) * frac), y + 3, frac < 0.3f ? 0xFFFF7A30 : 0xFFFFC93A);
		y += 8;
		for (FormattedCharSequence line : bodyLines()) {
			g.drawString(font, line, x0 + 12, y, TEXT, false);
			y += 10;
		}
		super.render(g, mouseX, mouseY, partial);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
