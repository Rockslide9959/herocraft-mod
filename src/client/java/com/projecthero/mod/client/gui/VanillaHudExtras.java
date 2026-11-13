package com.projecthero.mod.client.gui;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.3: small extras on top of {@link VanillaHud.Stack} for the HUDs moved onto the Minecraft-style look (Iron Man, Thor,
 * Spider-Man, Symbiote): a cooldown-length tracker for powers that only sync "ticks left", a third cooldown bar along the top of a
 * key cell (Spider-Man's hold moves), an item icon in a key cell (Iron Man), and threshold ticks on a meter's bar.
 */
public final class VanillaHudExtras {
	/** key -> the longest "ticks left" seen since that cooldown started. */
	private static final Map<String, Integer> PEAK = new HashMap<>();

	private VanillaHudExtras() {
	}

	/**
	 * 0..1 of a cooldown still to run, for powers that sync only the ticks left: the first (largest) value seen after the
	 * cooldown starts is taken as its full length.
	 */
	public static float frac(String key, int remaining) {
		if (remaining <= 0) {
			PEAK.remove(key);
			return 0f;
		}
		Integer peak = PEAK.get(key);
		if (peak == null || remaining > peak) {
			peak = remaining;
			PEAK.put(key, peak);
		}
		return Mth.clamp(remaining / (float) peak, 0f, 1f);
	}

	/** The 16x16 item cell of key {@code i}: its top-left corner. */
	public static int cellX(VanillaHud.Stack s, int i) {
		return s.stripX() + 3 + i * VanillaHud.SLOT;
	}

	public static int cellY(VanillaHud.Stack s) {
		return s.stripY() + 3;
	}

	/**
	 * A third cooldown on key {@code i}: a 1-px bar along the top of the cell like the durability bar along its bottom (red
	 * when it has just gone, green as it comes back). {@code cooldown}: 0..1 still to run.
	 */
	public static void topBar(VanillaHud.Stack s, int i, float cooldown) {
		if (cooldown <= 0f) {
			return;
		}
		int ix = cellX(s, i), iy = cellY(s);
		float left = 1f - Mth.clamp(cooldown, 0f, 1f);
		int w = Math.max(1, Math.round(13f * left));
		s.g.pose().pushPose();
		s.g.pose().translate(0f, 0f, 200f);
		s.g.fill(RenderType.guiOverlay(), ix + 2, iy, ix + 15, iy + 2, 0xFF000000);
		s.g.fill(RenderType.guiOverlay(), ix + 2, iy, ix + 2 + w, iy + 1, 0xFF000000 | Mth.hsvToRgb(left / 3f, 1f, 1f));
		s.g.pose().popPose();
	}

	/** A 2x2 colour mark in the top-left corner of key {@code i}'s cell (e.g. a key the black suit has swapped). */
	public static void corner(VanillaHud.Stack s, int i, int argb) {
		int ix = cellX(s, i), iy = cellY(s);
		s.g.pose().pushPose();
		s.g.pose().translate(0f, 0f, 200f);
		s.g.fill(ix - 1, iy - 1, ix + 2, iy + 2, 0xFF000000);
		s.g.fill(ix - 1, iy - 1, ix + 1, iy + 1, argb);
		s.g.pose().popPose();
	}

	/** A translucent wash over key {@code i}'s cell (drawn over an icon). */
	public static void tint(VanillaHud.Stack s, int i, int argb) {
		int ix = cellX(s, i), iy = cellY(s);
		s.g.pose().pushPose();
		s.g.pose().translate(0f, 0f, 200f);
		s.g.fill(RenderType.guiOverlay(), ix, iy, ix + 16, iy + 16, argb);
		s.g.pose().popPose();
	}

	/** An item icon in key {@code i}'s cell, where a hotbar item sits. */
	public static void icon(VanillaHud.Stack s, int i, ItemStack stack) {
		if (!stack.isEmpty()) {
			s.g.renderFakeItem(stack, cellX(s, i), cellY(s));
		}
	}

	/**
	 * Key {@code i}'s slot when the cell holds an item icon: the overlays (cooldown sweep, seconds, durability bar, selection
	 * frame) are lifted over the item, and the key letter sits in the cell's top-left corner instead of over the icon.
	 */
	public static void iconSlot(VanillaHud.Stack s, int i, String label, boolean usable, boolean shifted, float cooldown, int seconds,
			float other, boolean active) {
		s.g.pose().pushPose();
		s.g.pose().translate(0f, 0f, 200f);
		s.slot(i, "", usable, shifted, cooldown, seconds, other, active);
		int colour = !usable ? VanillaHud.DARK_GREY : shifted ? VanillaHud.YELLOW : VanillaHud.WHITE;
		s.g.drawString(s.font, label, cellX(s, i) - 1, cellY(s) - 1, colour, true);
		s.g.pose().popPose();
	}

	/**
	 * A white threshold tick over the bar of the meter row just drawn with {@code label} / {@code value} (the same geometry as
	 * {@link VanillaHud#meter}). {@code at}: 0..1 along the bar.
	 */
	public static void tick(VanillaHud.Stack s, Component label, String value, float at) {
		Font font = s.font;
		int valueW = Math.max(font.width("0:00"), font.width(value));
		int room = s.width - valueW - 3 - 4 - 20;
		int labelW = font.width(label) <= room ? font.width(label)
				: font.width(font.plainSubstrByWidth(label.getString(), room - font.width(".")) + ".");
		int barX = s.x0 + labelW + 4;
		int barW = Math.max(20, s.width - labelW - 4 - valueW - 3);
		int by = s.top() + 2; // the meter row's bar top + 1
		int tx = barX + Math.round(barW * Mth.clamp(at, 0f, 1f));
		s.g.fill(tx, by - 2, tx + 1, by + 5, 0xFFFFFFFF);
	}
}
