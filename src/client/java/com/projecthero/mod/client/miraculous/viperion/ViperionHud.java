package com.projecthero.mod.client.miraculous.viperion;

import java.util.List;

import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.client.miraculous.MiraculousHud;
import com.projecthero.mod.miraculous.viperion.Viperion;
import com.projecthero.mod.miraculous.viperion.ViperionConfig;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.16.7: Viperion's Second Chance on his HUD.
 *
 * <p>v0.16.7 (user): stacked right above the ability HUD (a {@link MiraculousHud.Panel}), not floating over the hotbar.
 * v0.17.3 (user: "I really like the Minecraft-style HUD ... apply this to all the powers"): as rows on the vanilla-style column:
 * <pre>
 *   Rewinds 2/3                                   one per rewind left (grey once none are left)
 *   Second Chance [boss bar ======---]  1:24      the window draining, green; gold, then a red blink, in the last 10 s
 * </pre>
 * And the screen's green flashes: a soft one at the checkpoint, a stronger one at every rewind.
 */
public final class ViperionHud implements MiraculousHud.Panel {
	private static final int GREEN = 0xFF3CE0A0;
	private static final int GREEN_HI = 0xFFA8FFD8;

	private ViperionHud() {
	}

	public static void initialize() {
		MiraculousHud.addPanel(new ViperionHud());
	}

	@Override
	public int height(LocalPlayer player) {
		return 0; // v0.17.3: drawn as rows (see rows)
	}

	@Override
	public void draw(GuiGraphics g, Minecraft mc, int x, int y, int w, float partial) {
	}

	@Override
	public boolean rows(VanillaHud.Stack stack, LocalPlayer p, float partial) {
		if (!Viperion.is(p)) {
			return true;
		}
		int left = Viperion.checkpointTicksLeft(p);
		if (left <= 0) {
			return true;
		}
		long now = p.level().getGameTime();
		int rewinds = Viperion.rewindsLeft(p);
		float frac = Mth.clamp((left - partial) / (float) ViperionConfig.SECOND_CHANCE_WINDOW, 0f, 1f);
		boolean late = left <= 200;
		boolean blink = late && (now / 5) % 2 == 0;
		int accent = !late ? GREEN : blink ? VanillaHud.RED : VanillaHud.GOLD;
		int secs = (left + 19) / 20;
		String time = secs / 60 + ":" + String.format("%02d", secs % 60);
		stack.meter(Component.translatable("hud.projecthero.viperion.window"), frac, accent, time, late ? accent : VanillaHud.WHITE);
		stack.tags(List.of(new VanillaHud.Tag(Component.translatable("hud.projecthero.viperion.rewinds_count", rewinds,
				ViperionConfig.SECOND_CHANCE_REWINDS).getString(), rewinds > 0 ? GREEN_HI : VanillaHud.GREY)));
		return true;
	}

	/** The screen's green flashes (the rows themselves are drawn by {@link MiraculousHud}). */
	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || !Viperion.is(mc.player)) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		long now = mc.level.getGameTime();
		flash(g, now, partial, Viperion.val(mc.player, Viperion.V_CHECKPOINT_AT), 10f, 70);
		flash(g, now, partial, Viperion.val(mc.player, Viperion.V_REWIND_AT), 16f, 130);
	}

	private static void flash(GuiGraphics g, long now, float partial, long at, float len, int maxAlpha) {
		if (at <= 0L) {
			return;
		}
		float age = now - at + partial;
		if (age < 0f || age > len) {
			return;
		}
		float a = 1f - age / len;
		int alpha = (int) (Mth.clamp(a, 0f, 1f) * maxAlpha);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0x40E0A0);
	}
}
