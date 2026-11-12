package com.projecthero.mod.client.titans;

import com.projecthero.mod.titans.TitansTogether;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3 Titans Together: a small red-and-gold "T" badge just right of the hotbar while the bonus is on (a Titan squadmate is
 * within 32 blocks).
 */
public final class TitansTogetherHud {
	private TitansTogetherHud() {
	}

	public static void initialize() {
		HudRenderCallback.EVENT.register((g, delta) -> render(g));
	}

	private static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !TitansTogether.active(mc.player)) {
			return;
		}
		int x = g.guiWidth() / 2 + 91 + 4;
		int y = g.guiHeight() - 19;
		if (!mc.player.getOffhandItem().isEmpty() && mc.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.LEFT) {
			x += 29; // the off-hand slot sits on this side for left-handed players
		}
		g.fill(x, y, x + 16, y + 16, 0xCC2A0A0A);
		g.renderOutline(x, y, 16, 16, 0xFFE0B020);
		g.fill(x + 1, y + 1, x + 15, y + 15, 0xCCB01818);
		Component t = Component.translatable("projecthero.titans_together.badge");
		g.drawString(mc.font, t, x + 8 - mc.font.width(t) / 2, y + 4, 0xFFFFD84A, true);
	}
}
