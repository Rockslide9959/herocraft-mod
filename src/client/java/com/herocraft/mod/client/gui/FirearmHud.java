package com.herocraft.mod.client.gui;

import com.herocraft.mod.client.firearm.FirearmClient;
import com.herocraft.mod.firearm.AmmoKind;
import com.herocraft.mod.firearm.FirearmData;
import com.herocraft.mod.firearm.FirearmStack;
import com.herocraft.mod.firearm.Firearms;
import com.herocraft.mod.item.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The firearm HUD: weapon name, magazine / capacity, reserve (a count, or ∞ for a Punisher), a
 * reload progress bar and a brief "HEADSHOT" flash by the crosshair. Only drawn while a firearm is
 * held; deliberately compact and anchored to the middle of the right edge (v0.9.2 -- was bottom-right,
 * which now belongs to the Punisher ability HUD) so it never covers the play area (spec section 39).
 */
public final class FirearmHud {
	private static final int COLOR_NAME = 0xFFE8E8E8;
	private static final int COLOR_MAG = 0xFFFFFFFF;
	private static final int COLOR_MAG_LOW = 0xFFFF5555;
	private static final int COLOR_RESERVE = 0xFFB0B0B0;
	private static final int COLOR_BAR_BG = 0xC0202020;
	private static final int COLOR_BAR = 0xFFE0C060;

	private static long headshotUntil;

	private FirearmHud() {
	}

	public static void flashHeadshot() {
		headshotUntil = Minecraft.getInstance().level != null
				? Minecraft.getInstance().level.getGameTime() + 14 : 0;
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || mc.level == null) {
			return;
		}
		ItemStack stack = mc.player.getMainHandItem();
		FirearmData data = Firearms.of(stack);
		if (data == null) {
			return;
		}

		int mag = FirearmStack.magazine(stack, data);
		long now = mc.level.getGameTime();
		Long reloadEnd = stack.get(ModDataComponents.FIREARM_RELOAD_END);
		boolean reloading = reloadEnd != null && reloadEnd > now;

		int sw = g.guiWidth();
		int sh = g.guiHeight();
		int x = sw - 6;
		int y = sh / 2 - 24;

		Component name = Component.translatable("item.herocraft." + data.id);
		g.drawString(mc.font, name, x - mc.font.width(name), y, COLOR_NAME, true);

		String magStr = mag + " / " + data.magazineSize;
		int magColor = mag == 0 ? COLOR_MAG_LOW : COLOR_MAG;
		g.pose().pushPose();
		g.pose().translate(x, y + 12, 0);
		g.pose().scale(1.6f, 1.6f, 1f);
		g.drawString(mc.font, magStr, -mc.font.width(magStr), 0, magColor, true);
		g.pose().popPose();

		boolean infiniteReserve = com.herocraft.mod.punisher.Punisher.hasPower(mc.player);
		String reserve = infiniteReserve ? "∞ RESERVE"
				: (reserveCount(mc, data.ammo) + " reserve");
		g.drawString(mc.font, reserve, x - mc.font.width(reserve), y + 30, COLOR_RESERVE, true);

		if (reloading) {
			long reloadStart = reloadEnd - Math.max(1, data.reloadTicks);
			float p = Math.max(0f, Math.min(1f, (now - reloadStart) / (float) Math.max(1, data.reloadTicks)));
			int bw = 90;
			int bx = x - bw;
			int by = y + 42;
			g.fill(bx, by, bx + bw, by + 4, COLOR_BAR_BG);
			g.fill(bx, by, bx + (int) (bw * p), by + 4, COLOR_BAR);
			Component rl = Component.translatable("firearm.herocraft.hud.reloading").withStyle(ChatFormatting.GRAY);
			g.drawString(mc.font, rl, bx - mc.font.width(rl) - 4, by - 2, 0xFFBBBBBB, true);
		}

		Long lastFired = stack.get(ModDataComponents.FIREARM_LAST_FIRED);
		if (!reloading && lastFired != null && data.cycleTicks > 0 && now - lastFired < data.cycleTicks) {
			Component cy = Component.translatable("firearm.herocraft.hud.cycling").withStyle(ChatFormatting.GRAY);
			g.drawString(mc.font, cy, x - mc.font.width(cy), y + 42, 0xFF999999, true);
		}

		if (headshotUntil > now) {
			Component hs = Component.translatable("firearm.herocraft.hud.headshot").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
			g.drawString(mc.font, hs, sw / 2 - mc.font.width(hs) / 2, sh / 2 - 24, 0xFFFF4040, true);
		}
	}

	private static int reserveCount(Minecraft mc, AmmoKind kind) {
		if (kind.item() == null) {
			return 0;
		}
		int total = 0;
		var inv = mc.player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(kind.item())) {
				total += s.getCount();
			}
		}
		return total;
	}
}
