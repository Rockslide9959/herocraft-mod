package com.projecthero.mod.client.doctorfate;

import java.util.List;

import com.projecthero.mod.doctorfate.network.FateActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.17.3 Doctor Fate: Nabu's question when you take up the Helmet of Fate -- "Will you serve Order?" with <b>Accept</b> and
 * <b>Refuse</b>. Refusing leaves the helmet where it was (on its pedestal for someone else). Text wraps to the panel.
 */
public class FateServeScreen extends Screen {
	private static final int PANEL_W = 240;
	private static final int BG = 0xF0101830;
	private static final int EDGE = 0xFFE0B040;

	private final BlockPos pos;
	private final boolean fromHand;
	private boolean answered;
	private int panelH = 150;

	public FateServeScreen(BlockPos pos, boolean fromHand) {
		super(Component.translatable("screen.projecthero.doctor_fate.serve.title"));
		this.pos = pos;
		this.fromHand = fromHand;
	}

	private List<FormattedCharSequence> body() {
		return font.split(Component.translatable("screen.projecthero.doctor_fate.serve.body"), PANEL_W - 24);
	}

	@Override
	protected void init() {
		panelH = 70 + body().size() * 10;
		int x0 = (width - PANEL_W) / 2;
		int y0 = (height - panelH) / 2;
		int bw = (PANEL_W - 36) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.doctor_fate.serve.accept"), b -> answer(true))
				.bounds(x0 + 12, y0 + panelH - 28, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.doctor_fate.serve.refuse"), b -> answer(false))
				.bounds(x0 + PANEL_W - 12 - bw, y0 + panelH - 28, bw, 20).build());
	}

	private void answer(boolean accept) {
		if (!answered) {
			answered = true;
			ClientPlayNetworking.send(new FateActionPayload(accept ? FateActionPayload.Action.SERVE_ACCEPT : FateActionPayload.Action.SERVE_REFUSE,
					fromHand ? 1 : 0, pos));
		}
		super.onClose();
	}

	@Override
	public void onClose() {
		answer(false);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int x0 = (width - PANEL_W) / 2;
		int y0 = (height - panelH) / 2;
		g.fill(x0 - 1, y0 - 1, x0 + PANEL_W + 1, y0 + panelH + 1, EDGE);
		g.fill(x0, y0, x0 + PANEL_W, y0 + panelH, BG);
		int y = y0 + 10;
		for (FormattedCharSequence line : font.split(Component.translatable("screen.projecthero.doctor_fate.serve.question"), PANEL_W - 24)) {
			g.drawString(font, line, x0 + (PANEL_W - font.width(line)) / 2, y, 0xFFFFD25A, true);
			y += 11;
		}
		y += 6;
		for (FormattedCharSequence line : body()) {
			g.drawString(font, line, x0 + 12, y, 0xFFE8E8F0, false);
			y += 10;
		}
		for (var child : children()) {
			if (child instanceof Button b) {
				b.render(g, mouseX, mouseY, partial);
			}
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
