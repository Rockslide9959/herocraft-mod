package com.projecthero.mod.client.gui;

import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.PortableDeploy;
import com.projecthero.mod.ironman.suit.PortableForm;
import com.projecthero.mod.ironman.suit.PortableForms;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user) Iron Man Era II fold polish: the HUD fill for the <b>Shift+C fold hold</b> of a suit with a portable form
 * ({@link PortableDeploy#FOLD_HOLD_TICKS}, 0.5 s). A Gauge beside the C box of the left key column: the packed item's icon,
 * "FOLD INTO MK IX HEAT CASE" (wrapped to the bar's width, never running off), and a cyan bar that fills as you hold. It
 * mirrors the server's timing -- the hold starts on a C press with Shift already down, and lets go when either key does --
 * and disappears the moment the fold starts. The server times the real fold; this only shows it.
 */
public final class IronManFoldHoldBar {
	/** The game time (and partial) this client started the hold, or NaN. */
	private static float heldSince = Float.NaN;
	private static boolean cWasDown;
	/** The vanilla hotbar + hearts / armour / food stack at the bottom centre (gui px). */
	private static final int HOTBAR_ZONE_H = 52;

	private IronManFoldHoldBar() {
	}

	/** 0..1 of the hold at {@code now + partial}, or -1 when nothing is being held (pure: the gametests check it). */
	public static float progress(float heldSince, float now) {
		if (Float.isNaN(heldSince) || now < heldSince) {
			return -1f;
		}
		return Math.min(1f, (now - heldSince) / PortableDeploy.FOLD_HOLD_TICKS);
	}

	/** Called from {@code IronManHud.render} while an Iron Man helmet is worn. */
	public static void render(GuiGraphics g, Font font, Player player, IronManSuit suit, long gameTime, float partial) {
		Minecraft mc = Minecraft.getInstance();
		boolean cDown = ModKeyBindings.ABILITY_SLOTS[5].isDown();
		boolean pressEdge = cDown && !cWasDown;
		cWasDown = cDown;
		PortableForm form = suit == null || suit.eraOne() ? null : PortableForms.byId(suit.id());
		boolean can = form != null && mc.screen == null && player.isShiftKeyDown()
				&& !com.projecthero.mod.client.ironman.IronManPortableClient.active(player);
		float now = gameTime + partial;
		if (!can || !cDown) {
			heldSince = Float.NaN;
			return;
		}
		if (Float.isNaN(heldSince)) {
			if (!pressEdge) {
				return; // Shift came down after C: the server does not start a hold for that either
			}
			heldSince = now;
		}
		float p = progress(heldSince, now);
		if (p < 0f) {
			return;
		}
		draw(g, font, form, p);
	}

	private static void draw(GuiGraphics g, Font font, PortableForm form, float p) {
		ItemStack packed = new ItemStack(form.packedItem().get());
		Rect c = IronManUiLayout.abilityStrip(g.guiWidth(), g.guiHeight())[5];
		int x = c.right() + 6;
		int w = Math.min(IronManUiLayout.HUD_W, g.guiWidth() / 2 - x - 20); // clear of the crosshair
		if (c.bottom() > g.guiHeight() - HOTBAR_ZONE_H) {
			// a short screen pushes the key column down beside the hotbar: stop short of the hotbar, hearts and armour
			w = Math.min(w, g.guiWidth() / 2 - 91 - 6 - x);
		}
		w = Math.max(48, w);
		int textX = x + 20;
		int textW = w - 20;
		List<FormattedCharSequence> lines = font.split(Component.translatable("hud.projecthero.ironman.fold_hold",
				packed.getHoverName()), textW);
		int textH = Math.max(16, lines.size() * 9);
		int h = textH + 9;
		int y = Math.max(2, Math.min(c.y() + (c.h() - h) / 2, g.guiHeight() - h - 2));
		int accent = p >= 1f ? IronManGui.GOLD : IronManGui.CYAN;
		g.fill(x - 3, y - 3, x + w + 3, y + h + 2, 0x8C050B12);
		g.fill(x - 3, y + h + 2, x + w + 3, y + h + 3, IronManGui.alpha(IronManGui.CYAN_DIM, 0.6f));
		IronManGui.brackets(g, x - 3, y - 3, w + 6, h + 6, 4, IronManGui.alpha(accent, 0.8f));
		g.renderFakeItem(packed, x, y + (textH - 16) / 2);
		int ty = y + (textH - lines.size() * 9) / 2;
		for (FormattedCharSequence line : lines) {
			g.drawString(font, line, textX, ty, IronManGui.TEXT, true);
			ty += 9;
		}
		IronManGui.gaugeBar(g, x, y + textH + 4, w, p, accent, IronManGui.BAR_BG, IronManGui.PANEL_BORDER);
	}
}
