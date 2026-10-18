package com.projecthero.mod.client.allmight;

import java.util.List;

import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.AllMightActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.16.6: sneak + right-click another player with an empty hand -- hand One For All on. Two choices, each explained (wrapped
 * to the panel: the v0.14.7 tooltip-wrap rule): <b>Pass On</b> (for good) and <b>Lend (5 min)</b> (greyed out with the seconds left
 * while the lend cooldown runs). Either only sends an offer; the other player must accept it in chat.
 */
public class AllMightOfferScreen extends Screen {
	private static final int PANEL_W = 250;
	private static final int PANEL_H = 176;
	private static final int BG = 0xF0141C2C;
	private static final int EDGE = 0xFFD8A830;
	private static final int TEXT = 0xFFE8E8F0;
	private static final int GOLD = 0xFFFFD25A;

	private final int targetId;
	private final Component targetName;
	private Button lend;

	public AllMightOfferScreen(int targetId, Component targetName) {
		super(Component.translatable("screen.projecthero.all_might_offer.title"));
		this.targetId = targetId;
		this.targetName = targetName;
	}

	@Override
	protected void init() {
		int x0 = (width - PANEL_W) / 2;
		int y0 = (height - PANEL_H) / 2;
		int bw = (PANEL_W - 40) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.all_might_offer.pass_on"), b -> {
			ClientPlayNetworking.send(new AllMightActionPayload(AllMightActionPayload.Action.PASS_ON, targetId));
			onClose();
		}).bounds(x0 + 14, y0 + PANEL_H - 52, bw, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.projecthero.all_might_offer.pass_on.tip"))).build());
		lend = addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.all_might_offer.lend", AllMightConfig.LOAN_TICKS / 1200), b -> {
			ClientPlayNetworking.send(new AllMightActionPayload(AllMightActionPayload.Action.LEND, targetId));
			onClose();
		}).bounds(x0 + PANEL_W - 14 - bw, y0 + PANEL_H - 52, bw, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.projecthero.all_might_offer.lend.tip"))).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(x0 + (PANEL_W - 80) / 2, y0 + PANEL_H - 28, 80, 20).build());
		refreshLend();
	}

	/** Lending is greyed out while the 60 s lend cooldown runs (the server checks again anyway). */
	private void refreshLend() {
		Minecraft mc = Minecraft.getInstance();
		if (lend == null || mc.player == null || mc.level == null) {
			return;
		}
		AllMightState s = mc.player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		long left = s == null ? 0L : s.lendReadyAt - mc.level.getGameTime();
		lend.active = left <= 0L;
		if (left > 0L) {
			lend.setMessage(Component.translatable("screen.projecthero.all_might_offer.lend_wait", (left + 19) / 20));
		} else {
			lend.setMessage(Component.translatable("screen.projecthero.all_might_offer.lend", AllMightConfig.LOAN_TICKS / 1200));
		}
	}

	@Override
	public void tick() {
		super.tick();
		refreshLend();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int x0 = (width - PANEL_W) / 2;
		int y0 = (height - PANEL_H) / 2;
		g.fill(x0 - 1, y0 - 1, x0 + PANEL_W + 1, y0 + PANEL_H + 1, EDGE);
		g.fill(x0, y0, x0 + PANEL_W, y0 + PANEL_H, BG);
		Component heading = Component.translatable("screen.projecthero.all_might_offer.heading", targetName);
		int y = y0 + 10;
		for (FormattedCharSequence line : font.split(heading, PANEL_W - 24)) {
			g.drawString(font, line, x0 + (PANEL_W - font.width(line)) / 2, y, GOLD, false);
			y += 10;
		}
		y += 4;
		for (String key : new String[] { "screen.projecthero.all_might_offer.pass_on.body", "screen.projecthero.all_might_offer.lend.body" }) {
			List<FormattedCharSequence> lines = font.split(Component.translatable(key, AllMightConfig.LOAN_TICKS / 1200), PANEL_W - 24);
			for (FormattedCharSequence line : lines) {
				if (y > y0 + PANEL_H - 60) {
					break;
				}
				g.drawString(font, line, x0 + 12, y, TEXT, false);
				y += 10;
			}
			y += 4;
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
