package com.projecthero.mod.client.miraculous.multimouse;

import com.projecthero.mod.client.gui.VanillaHud;
import com.projecthero.mod.client.miraculous.MiraculousHud;
import com.projecthero.mod.miraculous.multimouse.Multimouse;
import com.projecthero.mod.miraculous.multimouse.MultimouseConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * v0.17.1: Multimouse's own rows on the ability HUD (the shared {@link MiraculousHud.Panel}). v0.17.3 (user: "I really like the
 * Minecraft-style HUD ... apply this to all the powers"): vanilla-style meter rows --
 * <pre>
 *   Multitude [boss bar ======---]  3/5      while Multitude runs: its time left, and the copies still fighting / summoned
 *   Scurry    [boss bar ====-----]  4s       while Scurry runs: its time left
 * </pre>
 */
public final class MultimouseHud implements MiraculousHud.Panel {
	private static final int PINK = 0xFFE0559E;

	private MultimouseHud() {
	}

	public static void initialize() {
		MiraculousHud.addPanel(new MultimouseHud());
	}

	@Override
	public int height(LocalPlayer player) {
		return 0; // v0.17.3: drawn as rows (see rows)
	}

	@Override
	public void draw(GuiGraphics g, Minecraft mc, int x, int y, int width, float partial) {
	}

	@Override
	public boolean rows(VanillaHud.Stack stack, LocalPlayer p, float partial) {
		long now = p.level().getGameTime();
		if (Multimouse.multitude(p)) {
			int total = Math.max(1, (int) Multimouse.val(p, Multimouse.V_MULTI_TOTAL));
			int left = Multimouse.copiesLeft(p);
			float frac = Multimouse.multitudeTicksLeft(p) / (float) MultimouseConfig.MULTITUDE_TICKS;
			stack.meter(Component.translatable("hud.projecthero.multimouse.multitude_short"), clamp(frac), PINK, left + "/" + total,
					left > 0 ? VanillaHud.WHITE : VanillaHud.RED);
		} else if (Multimouse.scurrying(p)) {
			long ticks = Math.max(0L, Multimouse.val(p, Multimouse.V_SCURRY) - now);
			float frac = ticks / (float) MultimouseConfig.SCURRY_TICKS;
			stack.meter(Component.translatable("hud.projecthero.multimouse.scurry"), clamp(frac), PINK, VanillaHud.seconds((int) ticks) + "s",
					VanillaHud.WHITE);
		}
		return true;
	}

	private static float clamp(float f) {
		return Math.min(1f, Math.max(0f, f));
	}
}
