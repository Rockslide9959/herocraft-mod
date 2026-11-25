package com.projecthero.mod.client.thoracquire;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.thoracquire.Valor;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * v0.17.7: looking at a free Mjolnir as an outsider shows your Valor just under the crosshair -- a small "Valor 43%" label
 * over a thin gold Meter -- and, once it is full, what the hammer still wants (a thunderstorm; then the Trial of Asgard).
 */
public final class ValorHud {
	private static final int W = 82;

	private ValorHud() {
	}

	static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui || Worthiness.isWorthy(mc.player)) {
			return;
		}
		HitResult hit = mc.hitResult;
		if (!(hit instanceof EntityHitResult eh) || !(eh.getEntity() instanceof MjolnirEntity hammer)
				|| hammer.getState() != MjolnirEntity.State.RESTING) {
			return;
		}
		Font font = mc.font;
		int v = Valor.get(mc.player);
		int pct = Valor.percentOf(v);
		int cx = g.guiWidth() / 2;
		int y = g.guiHeight() / 2 + 12;
		Component label = Component.translatable("projecthero.hud.valor", pct);
		g.drawString(font, label, cx - font.width(label) / 2, y, 0xFFE8C46A, true);
		int x0 = cx - W / 2;
		int by = y + 11;
		g.fill(x0 - 1, by - 1, x0 + W + 1, by + 3, 0xC0101018);
		int fill = Math.round(W * pct / 100f);
		g.fill(x0, by, x0 + fill, by + 2, 0xFFE8B030);
		g.fill(x0, by, x0 + fill, by + 1, 0xFFFFE08A);
		if (v >= Valor.MAX) {
			boolean storm = mc.level.isThundering();
			Component hint = Component.translatable(storm ? "projecthero.hud.valor.trial" : "projecthero.hud.valor.storm");
			for (var line : font.split(hint, 180)) {
				by += 10;
				g.drawString(font, line, cx - font.width(line) / 2, by + 2, 0xFFB8D8FF, true);
			}
		}
	}
}
