package com.projecthero.mod.client.powerarmour;

import com.projecthero.mod.powerarmour.menu.CoreChargerMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** v0.17.5 Power Armour: the Core Charger -- core above, fuel below, a burn flame between, the charge as a bar beside them. */
public class CoreChargerScreen extends AbstractContainerScreen<CoreChargerMenu> {
	public CoreChargerScreen(CoreChargerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 166;
		this.inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
		int x = leftPos, y = topPos;
		GuiBits.panel(g, x, y, imageWidth, imageHeight);
		GuiBits.slot(g, x + CoreChargerMenu.CORE_X, y + CoreChargerMenu.CORE_Y);
		GuiBits.slot(g, x + CoreChargerMenu.FUEL_X, y + CoreChargerMenu.FUEL_Y);
		for (int r = 0; r < 3; r++) {
			for (int c = 0; c < 9; c++) {
				GuiBits.slot(g, x + 8 + c * 18, y + 84 + r * 18);
			}
		}
		for (int c = 0; c < 9; c++) {
			GuiBits.slot(g, x + 8 + c * 18, y + 142);
		}
		// the burn flame between the slots
		float burn = menu.burnFraction();
		int fx = x + 81, fy = y + 37;
		g.fill(fx, fy, fx + 14, fy + 14, 0xFF6B6B6B);
		int fh = Math.round(13 * burn);
		if (fh > 0) {
			g.fill(fx + 3, fy + 14 - fh, fx + 11, fy + 14, 0xFFFFA020);
			g.fill(fx + 5, fy + 14 - fh, fx + 9, fy + 14, 0xFFFFE070);
		}
		// the charge bar
		int cap = Math.max(1, menu.capacity());
		float frac = menu.capacity() <= 0 ? 0f : menu.charge() / (float) cap;
		GuiBits.bar(g, x + 104, y + 20, 64, 9, frac, 0xFF4FD8FF);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		super.renderLabels(g, mouseX, mouseY);
		Component text = menu.capacity() <= 0 ? Component.translatable("projecthero.power_armour.charger.empty")
				: Component.translatable("projecthero.power_armour.charger.charge", menu.charge(), menu.capacity());
		GuiBits.wrapped(g, font, text, 104, 32, 66, GuiBits.TEXT, false);
		GuiBits.wrapped(g, font, Component.translatable("projecthero.power_armour.charger.fuel"), 104, 56, 66, 0xFF707070, false);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		renderTooltip(g, mouseX, mouseY);
	}
}
