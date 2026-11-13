package com.projecthero.mod.client.gui;

import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.attachment.ModAttachments;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The All Might HUD (bottom-right). v0.16.7: One For All is a Quirk now -- a holder's HUD (the One For All timer, the form, the
 * six keys) is the Quirk HUD ({@code QuirkHud} + {@code OneForAllClientKit}). This only remains for a <b>lender</b>, who holds no
 * power while their One For All is out on loan. v0.17.3: on the vanilla pieces ({@link VanillaHud.Stack}), the lines wrapped to
 * the column:
 * <pre>
 *   One For All
 *   Lent to Steve -- 3:12
 *   Exhausted without it
 * </pre>
 */
public final class AllMightHud {
	private AllMightHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		AllMightState s = mc.player.getAttachedOrElse(ModAttachments.ALL_MIGHT_STATE, null);
		if (s == null || s.hasPower || s.loanRole != AllMightState.LOAN_LENDER) {
			return;
		}
		long now = mc.level.getGameTime();
		VanillaHud.Stack.bottomRight(g, 6)
				.wrapped(Component.translatable("hud.projecthero.all_might.exhausted"), VanillaHud.RED)
				.wrapped(Component.translatable("hud.projecthero.all_might.lent_to", s.loanPartnerName, clock(s.loanUntil - now)), VanillaHud.YELLOW)
				.title(Component.translatable("hud.projecthero.all_might.title"), VanillaHud.GREY);
	}

	private static String clock(long ticks) {
		long secs = Math.max(0L, (ticks + 19) / 20);
		return String.format(java.util.Locale.ROOT, "%d:%02d", secs / 60, secs % 60);
	}
}
