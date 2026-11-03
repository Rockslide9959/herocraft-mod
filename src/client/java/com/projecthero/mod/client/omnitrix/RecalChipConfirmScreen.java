package com.projecthero.mod.client.omnitrix;

import java.util.List;

import com.projecthero.mod.omnitrix.network.OmnitrixActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.17.2 (user: "the player can right click the chip and then an option to choose to recalibrate or not will pop up, if the player
 * says no nothing happens but if they say yes then the player recalibrates"): the Recalibration Chip's Yes / No, Omnitrix-styled -- a
 * black panel with a green edge and the hourglass, the question, a wrapped line saying it takes 3 minutes and can't be stopped, and
 * the two buttons. No (or Escape) closes it and nothing happens; Yes asks the server ({@code OmnitrixActionPayload.Action.RECAL_CHIP}),
 * which checks everything again and starts the insert.
 */
public final class RecalChipConfirmScreen extends Screen {
	private static final int W = 230;
	private static final int GREEN = 0xFF4CE04A, GREEN_DIM = 0xFF1E6A1C, PANEL = 0xF0060A06, TEXT = 0xFFE2E2E2, SUB = 0xFFA8C8A4;
	private static final ResourceLocation FACE = com.projecthero.mod.ProjectHeroMod.id("textures/entity/omnitrix/face_morph.png");

	private List<FormattedCharSequence> title;
	private List<FormattedCharSequence> body;
	private int px, py, ph;

	public RecalChipConfirmScreen() {
		super(Component.translatable("screen.projecthero.recal_chip.title"));
	}

	@Override
	protected void init() {
		// v0.14.7 TooltipWrap rule: every line wraps to the panel (never sprawls)
		int inner = W - 20;
		title = font.split(Component.translatable("screen.projecthero.recal_chip.title"), inner - 30);
		body = font.split(Component.translatable("screen.projecthero.recal_chip.body"), inner);
		ph = 12 + Math.max(24, title.size() * 11) + 6 + body.size() * 10 + 10 + 20 + 10;
		px = (width - W) / 2;
		py = (height - ph) / 2;
		int by = py + ph - 30;
		int bw = (W - 30) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.recal_chip.yes"), b -> {
			ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.RECAL_CHIP, ""));
			onClose();
		}).bounds(px + 10, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.recal_chip.no"), b -> onClose())
				.bounds(px + W - 10 - bw, by, bw, 20).build());
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.renderBackground(g, mouseX, mouseY, partial);
		g.fill(px - 1, py - 1, px + W + 1, py + ph + 1, GREEN);
		g.fill(px, py, px + W, py + ph, PANEL);
		g.fill(px + 2, py + 2, px + W - 2, py + 3, GREEN_DIM);
		g.fill(px + 2, py + ph - 3, px + W - 2, py + ph - 2, GREEN_DIM);
		// the hourglass (frame 0 of the face sheet) in the top-left corner
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		g.blit(FACE, px + 10, py + 10, 24, 24, 0f, 0f, 64, 64, 256, 256);
		com.mojang.blaze3d.systems.RenderSystem.disableBlend();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int y = py + 12;
		int tx = px + 40;
		int th = Math.max(24, title.size() * 11);
		int ty = y + (th - title.size() * 11) / 2 + 1;
		for (FormattedCharSequence line : title) {
			g.drawString(font, line, tx, ty, GREEN, true);
			ty += 11;
		}
		y += th + 6;
		for (FormattedCharSequence line : body) {
			g.drawString(font, line, px + 10, y, SUB, false);
			y += 10;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
