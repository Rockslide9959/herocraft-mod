package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.firearm.AmmoKind;
import com.projecthero.mod.firearm.FirearmData;
import com.projecthero.mod.firearm.FirearmStack;
import com.projecthero.mod.firearm.Firearms;
import com.projecthero.mod.item.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The firearm HUD: weapon name, magazine / capacity, reserve (an inventory count, or the Punisher's personal per-gun reserve
 * pool shown as {@code have / capacity}), the reload progress and a brief "HEADSHOT" flash by the crosshair. Only drawn while a
 * firearm is held. v0.17.3: on the vanilla pieces --
 * <ul>
 *   <li><b>the Punisher</b>: the gun goes into his own bottom-right column ({@link PunisherHud} calls {@link #stackRows}):
 *       "Mag" and "Reloading" meter rows above his keys, the gun's name / reserve / CYCLE as status tags. Nothing is drawn
 *       mid-right then, so the two never overlap.</li>
 *   <li><b>anyone else</b>: a compact column anchored to the middle of the right edge (v0.9.2), clear of every power's
 *       bottom-right HUD: the name, the "Mag" meter, the reserve, the reload meter.</li>
 * </ul>
 */
public final class FirearmHud {
	private static final int COLOR_NAME = 0xFFE8E8E8;
	private static final int COLOR_MAG = 0xFFFFFFFF;
	private static final int COLOR_MAG_LOW = 0xFFFF5555;
	private static final int COLOR_RESERVE = 0xFFB0B0B0;
	private static final int COLOR_BAR = 0xFFE0C060;
	private static final int COLOR_MAG_BAR = 0xFFE8E8E8;
	private static final int WIDTH = VanillaHud.width(6);

	private static long headshotUntil;

	private FirearmHud() {
	}

	public static void flashHeadshot() {
		headshotUntil = Minecraft.getInstance().level != null
				? Minecraft.getInstance().level.getGameTime() + 14 : 0;
	}

	private static boolean punisher(Minecraft mc) {
		var s = mc.player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.PUNISHER_STATE, null);
		return s != null && s.hasPower;
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
		long now = mc.level.getGameTime();
		int sw = g.guiWidth();
		int sh = g.guiHeight();
		if (headshotUntil > now) {
			Component hs = Component.translatable("firearm.projecthero.hud.headshot").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
			g.drawString(mc.font, hs, sw / 2 - mc.font.width(hs) / 2, sh / 2 - 24, 0xFFFF4040, true);
		}
		if (punisher(mc)) {
			return; // the gun rides in the Punisher's own column (PunisherHud -> stackRows)
		}

		int mag = FirearmStack.magazine(stack, data);
		Long reloadEnd = stack.get(ModDataComponents.FIREARM_RELOAD_END);
		boolean reloading = reloadEnd != null && reloadEnd > now;
		int x0 = sw - 6 - WIDTH;
		int y = sh / 2 - 24;
		// the name (cut to the column), the Mag meter, the reserve, then the reload meter or CYCLE
		String name = Component.translatable("item.projecthero." + data.id).getString();
		g.drawString(mc.font, mc.font.plainSubstrByWidth(name, WIDTH), x0, y, COLOR_NAME, true);
		y += 14;
		VanillaHud.meter(g, x0, y, WIDTH, Component.translatable("firearm.projecthero.hud.mag"),
				data.magazineSize > 0 ? mag / (float) data.magazineSize : 0f, COLOR_MAG_BAR, mag + "/" + data.magazineSize,
				mag == 0 ? COLOR_MAG_LOW : COLOR_MAG);
		y += 10;
		String reserve = reserveCount(mc, data.ammo) + " reserve";
		g.drawString(mc.font, mc.font.plainSubstrByWidth(reserve, WIDTH), x0, y, COLOR_RESERVE, true);
		y += 14;
		if (reloading) {
			VanillaHud.meter(g, x0, y, WIDTH, Component.translatable("firearm.projecthero.hud.reloading"), reloadProgress(data, reloadEnd, now),
					COLOR_BAR, pct(reloadProgress(data, reloadEnd, now)), VanillaHud.GREY);
		} else if (cycling(stack, data, now)) {
			g.drawString(mc.font, Component.translatable("firearm.projecthero.hud.cycling"), x0, y - 2, 0xFF999999, true);
		}
	}

	/**
	 * v0.17.3: the held gun inside the Punisher's column -- the "Reloading" meter (while reloading) and the "Mag" meter go onto
	 * {@code st}; returns the status tags (the gun's name, the reserve, CYCLE) for the column's tag line. Nothing (and no tags)
	 * when no firearm is held.
	 */
	public static List<VanillaHud.Tag> stackRows(VanillaHud.Stack st) {
		Minecraft mc = Minecraft.getInstance();
		List<VanillaHud.Tag> tags = new ArrayList<>();
		if (mc.player == null || mc.level == null) {
			return tags;
		}
		ItemStack stack = mc.player.getMainHandItem();
		FirearmData data = Firearms.of(stack);
		if (data == null) {
			return tags;
		}
		long now = mc.level.getGameTime();
		int mag = FirearmStack.magazine(stack, data);
		Long reloadEnd = stack.get(ModDataComponents.FIREARM_RELOAD_END);
		boolean reloading = reloadEnd != null && reloadEnd > now;
		st.meter(Component.translatable("firearm.projecthero.hud.mag"), data.magazineSize > 0 ? mag / (float) data.magazineSize : 0f,
				COLOR_MAG_BAR, mag + "/" + data.magazineSize, mag == 0 ? COLOR_MAG_LOW : COLOR_MAG);
		if (reloading) {
			float p = reloadProgress(data, reloadEnd, now);
			st.meter(Component.translatable("firearm.projecthero.hud.reloading"), p, COLOR_BAR, pct(p), VanillaHud.GREY);
		}
		tags.add(new VanillaHud.Tag(Component.translatable("item.projecthero." + data.id).getString(), COLOR_NAME));
		// v0.9.22: personal per-gun reserve (3 magazines, regenerating) instead of the old infinity
		var punisherState = mc.player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.PUNISHER_STATE, null);
		String reserve = punisherState != null && punisherState.hasPower
				? com.projecthero.mod.punisher.PunisherAmmoReserve.count(punisherState, data.ammo) + "/"
						+ com.projecthero.mod.punisher.PunisherAmmoReserve.capacity(data.ammo) + " reserve"
				: reserveCount(mc, data.ammo) + " reserve";
		tags.add(new VanillaHud.Tag(reserve, COLOR_RESERVE));
		if (!reloading && cycling(stack, data, now)) {
			tags.add(new VanillaHud.Tag(Component.translatable("firearm.projecthero.hud.cycling").getString(), 0xFF999999));
		}
		return tags;
	}

	private static float reloadProgress(FirearmData data, long reloadEnd, long now) {
		long reloadStart = reloadEnd - Math.max(1, data.reloadTicks);
		return Math.max(0f, Math.min(1f, (now - reloadStart) / (float) Math.max(1, data.reloadTicks)));
	}

	private static boolean cycling(ItemStack stack, FirearmData data, long now) {
		Long lastFired = stack.get(ModDataComponents.FIREARM_LAST_FIRED);
		return lastFired != null && data.cycleTicks > 0 && now - lastFired < data.cycleTicks;
	}

	private static String pct(float p) {
		return Math.round(p * 100f) + "%";
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
