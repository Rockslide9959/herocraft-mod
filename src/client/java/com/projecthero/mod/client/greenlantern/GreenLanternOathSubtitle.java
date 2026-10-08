package com.projecthero.mod.client.greenlantern;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLanternBattery;
import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.15: the Green Lantern Oath as a centred subtitle in the upper part of the screen -- the line being recited while
 * charging at the Power Battery (synced {@link GreenLanternFx#CH_CHARGE}) or holding X for "Green Lantern's Light!"
 * (the reciting attachment), then "The Oath is spoken" once a charge completes. It used to go to the action bar, which
 * sits right on top of the GL HUD's labels. Placed at 28% of the screen height (well above the HUD and the hotbar, below
 * the boss bars), wrapped to the window width, each line fading in, on a soft dark backing.
 */
public final class GreenLanternOathSubtitle {
	private static final int GREEN = 0x5CFF8E;
	private static final int COMPLETE_TICKS = 50;

	private GreenLanternOathSubtitle() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register(GreenLanternOathSubtitle::render);
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		GreenLanternFx fx = p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FX, GreenLanternFx.EMPTY);
		Component text = null;
		float age;
		if (fx.has(GreenLanternFx.CH_CHARGE) && fx.chargeStart() != 0L) {
			float t = now - fx.chargeStart() + partial;
			int line = Mth.clamp((int) (t / GreenLanternConfig.OATH_LINE_TICKS), 0, GreenLanternBattery.OATH_LINES.length - 1);
			text = Component.translatable(GreenLanternBattery.OATH_LINES[line]).withStyle(ChatFormatting.ITALIC);
			age = t - line * GreenLanternConfig.OATH_LINE_TICKS;
		} else if (fx.anim() == GreenLanternFx.ANIM_CHARGED && now - fx.animStart() < COMPLETE_TICKS) {
			text = Component.translatable("message.projecthero.green_lantern.oath.complete").withStyle(ChatFormatting.BOLD);
			age = now - fx.animStart() + partial;
			float left = COMPLETE_TICKS - age;
			age = Math.min(age, left); // fade out at the end too
		} else {
			long since = p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_RECITING_SINCE, 0L);
			if (since <= 0L || p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_OATH_UNTIL, 0L) > now) {
				return;
			}
			float t = now - since + partial;
			int line = Mth.clamp((int) (t / GreenLanternConfig.OATH_MODE_LINE_TICKS), 0, GreenLanternBattery.OATH_LINES.length - 1);
			text = Component.translatable(GreenLanternBattery.OATH_LINES[line]).withStyle(ChatFormatting.ITALIC);
			age = t - line * GreenLanternConfig.OATH_MODE_LINE_TICKS;
		}
		float alpha = Mth.clamp(age / 5f, 0f, 1f);
		if (alpha <= 0.02f) {
			return;
		}
		int wrap = Math.max(80, Math.min(g.guiWidth() - 24, 320));
		List<FormattedCharSequence> lines = mc.font.split(text, wrap);
		int lineH = mc.font.lineHeight + 2;
		int top = Math.round(g.guiHeight() * 0.28f);
		int widest = 0;
		for (FormattedCharSequence l : lines) {
			widest = Math.max(widest, mc.font.width(l));
		}
		int cx = g.guiWidth() / 2;
		int a = Math.round(alpha * 255f);
		// a soft backing: two stacked translucent bars, the outer one fainter
		int h = lines.size() * lineH;
		g.fill(cx - widest / 2 - 8, top - 5, cx + widest / 2 + 8, top + h + 3, (Math.round(alpha * 0x30) << 24));
		g.fill(cx - widest / 2 - 4, top - 3, cx + widest / 2 + 4, top + h + 1, (Math.round(alpha * 0x38) << 24));
		int y = top;
		for (FormattedCharSequence l : lines) {
			g.drawString(mc.font, l, cx - mc.font.width(l) / 2, y, (Math.max(4, a) << 24) | GREEN, true);
			y += lineH;
		}
	}
}
