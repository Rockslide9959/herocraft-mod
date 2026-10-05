package com.projecthero.mod.client.gui;

import com.projecthero.mod.ironman.drone.IronManDrones;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;
import com.projecthero.mod.network.IronManDroneDeployPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * v0.14.29 Remote Pilot: the small PILOT button drawn over the bottom of a suit card's 3D preview in the Call Armour
 * picker ({@link IronManSuitCallScreen}). Only on cards for a full suit in the pack or on a platform with charge left.
 * Clicking it sends {@link IronManDroneDeployPayload}; the server re-validates everything. Kept in its own class so the
 * picker only carries a two-line hook (render + click).
 */
public final class IronManDroneCardButton {
	private static final int H = 11;

	private IronManDroneCardButton() {
	}

	/** Can this card be remote-piloted at all? */
	public static boolean shown(boolean available, int source, float energyFrac) {
		return available && IronManDrones.pilotableSource(source) && energyFrac > 0f;
	}

	/** The button's rectangle inside card {@code r}: across the bottom of the preview well. */
	public static Rect rect(Rect r) {
		return new Rect(r.x() + 4, r.y() + r.h() - 4 - H, IronManUiLayout.CARD_PREVIEW_W - 2, H);
	}

	/** Draw the button (and queue its wrapped tooltip when hovered). Call while the card grid is drawn. */
	public static void render(Screen screen, GuiGraphics g, Font font, Rect card, boolean available, int source,
			float energyFrac, int mouseX, int mouseY) {
		if (!shown(available, source, energyFrac)) {
			return;
		}
		Rect b = rect(card);
		boolean hot = b.contains(mouseX, mouseY);
		g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hot ? 0xF0183048 : 0xD0081420);
		int border = hot ? IronManGui.CYAN : IronManGui.CYAN_DIM;
		g.fill(b.x(), b.y(), b.x() + b.w(), b.y() + 1, border);
		g.fill(b.x(), b.y() + b.h() - 1, b.x() + b.w(), b.y() + b.h(), border);
		g.fill(b.x(), b.y(), b.x() + 1, b.y() + b.h(), border);
		g.fill(b.x() + b.w() - 1, b.y(), b.x() + b.w(), b.y() + b.h(), border);
		String label = IronManGui.fit(font, Component.translatable("screen.projecthero.suit_call.remote_pilot"), b.w() - 4);
		g.drawString(font, label, b.x() + (b.w() - font.width(label)) / 2, b.y() + 2, hot ? 0xFFFFFFFF : IronManGui.CYAN, false);
		if (hot) {
			screen.setTooltipForNextRenderPass(font.split(
					Component.translatable("screen.projecthero.suit_call.remote_pilot.tooltip"), 180));
		}
	}

	/** Handle a click on card {@code card}; true if it hit the button (the deploy request has then been sent). */
	public static boolean click(Rect card, String suitId, boolean available, int source, float energyFrac, double mx, double my) {
		if (!shown(available, source, energyFrac) || !rect(card).contains((int) mx, (int) my)) {
			return false;
		}
		ClientPlayNetworking.send(new IronManDroneDeployPayload(suitId, source));
		return true;
	}
}
