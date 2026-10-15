package com.projecthero.mod.client.ghostrider;

import java.util.List;

import com.projecthero.mod.ghostrider.network.GhostRiderActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.16.5: Mephisto's Contract -- a parchment page with the terms (wrapped to the page: the v0.14.7 tooltip-wrap rule) and
 * two choices. "Sign in Blood" asks the server to bind the Spirit of Vengeance ({@code SIGN_CONTRACT}); "Refuse" closes it.
 */
public class MephistoContractScreen extends Screen {
	private static final int PAGE_W = 240;
	private static final int PAGE_H = 200;
	private static final int PARCHMENT = 0xFFD6BE8C;
	private static final int PARCHMENT_EDGE = 0xFF7A5C38;
	private static final int INK = 0xFF3A1C10;
	private static final int BLOOD = 0xFF8C0C08;

	public MephistoContractScreen() {
		super(Component.translatable("screen.projecthero.mephisto_contract.title"));
	}

	@Override
	protected void init() {
		int x0 = (width - PAGE_W) / 2;
		int y0 = (height - PAGE_H) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.mephisto_contract.sign"), b -> {
			ClientPlayNetworking.send(new GhostRiderActionPayload(GhostRiderActionPayload.Action.SIGN_CONTRACT));
			onClose();
		}).bounds(x0 + 16, y0 + PAGE_H - 30, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.mephisto_contract.refuse"), b -> onClose())
				.bounds(x0 + PAGE_W - 116, y0 + PAGE_H - 30, 100, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int x0 = (width - PAGE_W) / 2;
		int y0 = (height - PAGE_H) / 2;
		g.fill(x0 - 2, y0 - 2, x0 + PAGE_W + 2, y0 + PAGE_H + 2, PARCHMENT_EDGE);
		g.fill(x0, y0, x0 + PAGE_W, y0 + PAGE_H, PARCHMENT);
		Component title = Component.translatable("screen.projecthero.mephisto_contract.heading");
		g.drawString(font, title, x0 + (PAGE_W - font.width(title)) / 2, y0 + 12, BLOOD, false);
		int y = y0 + 32;
		for (String key : new String[] { "screen.projecthero.mephisto_contract.terms", "screen.projecthero.mephisto_contract.price" }) {
			List<FormattedCharSequence> lines = font.split(Component.translatable(key), PAGE_W - 32);
			for (FormattedCharSequence line : lines) {
				g.drawString(font, line, x0 + 16, y, INK, false);
				y += 10;
			}
			y += 6;
		}
		// the signature line
		g.fill(x0 + 16, y0 + PAGE_H - 40, x0 + PAGE_W - 16, y0 + PAGE_H - 39, INK);
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
