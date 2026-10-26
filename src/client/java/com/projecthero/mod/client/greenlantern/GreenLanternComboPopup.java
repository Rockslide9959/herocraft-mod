package com.projecthero.mod.client.greenlantern;

import java.util.List;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.1 (user-approved revamp): the Construct Combo popup -- when a combo lands ({@code GreenLanternCombos}) the Lantern
 * sees a small "COMBO" tag and the combo's name in bold green just above the crosshair, with its one-line effect under it
 * (wrapped, never wider than {@link #MAX_W} px). It pops in a little oversized, settles, then fades over
 * {@link #SHOW_MS}. A second combo replaces the first. A bright chord plays for the Lantern (the world sound plays for
 * everyone at the target).
 */
public final class GreenLanternComboPopup {
	public static final int MAX_W = 220;
	private static final long SHOW_MS = 2200L;
	private static final long FADE_MS = 500L;
	private static final int GREEN = 0x5CFF8E;
	private static final int PALE = 0xC8F5D6;

	private static String comboId;
	private static long shownAt;

	private GreenLanternComboPopup() {
	}

	/** Show a combo now (also used by the visual-check harness). */
	public static void show(String id) {
		comboId = id;
		shownAt = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.25f, 1.8f);
			mc.player.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1.5f);
		}
	}

	static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (comboId == null || mc.options.hideGui || mc.player == null) {
			return;
		}
		long age = System.currentTimeMillis() - shownAt;
		if (age > SHOW_MS) {
			comboId = null;
			return;
		}
		float alpha = age > SHOW_MS - FADE_MS ? Math.max(0.04f, (SHOW_MS - age) / (float) FADE_MS) : Math.min(1f, age / 90f + 0.1f);
		// pop: 1.9x at the start, settling to 1.5x over the first 180 ms
		float pop = age < 180 ? Mth.lerp(age / 180f, 1.9f, 1.5f) : 1.5f;
		Font font = mc.font;
		String base = "projecthero.green_lantern.combo." + comboId;
		Component name = Component.translatable(base).withStyle(s -> s.withBold(true));
		Component tag = Component.translatable("hud.projecthero.green_lantern.combo");
		int maxW = Math.min(MAX_W, g.guiWidth() - 24);
		List<FormattedCharSequence> nameLines = font.split(name, Math.max(40, (int) (maxW / pop)));
		List<FormattedCharSequence> desc = font.split(Component.translatable(base + ".desc"), maxW);
		int cx = g.guiWidth() / 2;
		int nameH = Math.round(nameLines.size() * 10 * pop);
		int total = 10 + nameH + 2 + desc.size() * 10;
		int top = Math.max(8, g.guiHeight() / 2 - 22 - total);
		int widest = font.width(tag);
		for (FormattedCharSequence l : nameLines) {
			widest = Math.max(widest, Math.round(font.width(l) * pop));
		}
		for (FormattedCharSequence l : desc) {
			widest = Math.max(widest, font.width(l));
		}
		g.fill(cx - widest / 2 - 8, top - 4, cx + widest / 2 + 8, top + total + 2, GreenLanternRingVoiceClient.argb(0x041A0C, 0.45f * alpha));
		g.fill(cx - widest / 2 - 8, top - 4, cx + widest / 2 + 8, top - 3, GreenLanternRingVoiceClient.argb(GREEN, 0.7f * alpha));
		g.drawString(font, tag, cx - font.width(tag) / 2, top, GreenLanternRingVoiceClient.argb(PALE, alpha), true);
		int y = top + 10;
		for (FormattedCharSequence l : nameLines) {
			g.pose().pushPose();
			g.pose().translate(cx - font.width(l) * pop / 2f, y, 0);
			g.pose().scale(pop, pop, 1f);
			g.drawString(font, l, 0, 0, GreenLanternRingVoiceClient.argb(GREEN, alpha), true);
			g.pose().popPose();
			y += Math.round(10 * pop);
		}
		y += 2;
		for (FormattedCharSequence l : desc) {
			g.drawString(font, l, cx - font.width(l) / 2, y, GreenLanternRingVoiceClient.argb(PALE, alpha * 0.9f), true);
			y += 10;
		}
	}
}
