package com.projecthero.mod.client.gui;

import com.projecthero.mod.allmight.AllMightLockerMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** The two-slot All Might costume locker: a plain drawn panel (no texture), the costume and the trousers above the inventory. */
public class AllMightLockerScreen extends AbstractContainerScreen<AllMightLockerMenu> {
	private static final int PANEL = 0xFFC6C6C6;
	private static final int SLOT_DARK = 0xFF373737;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;
	private static final int SLOT_FILL = 0xFF8B8B8B;

	public AllMightLockerScreen(AllMightLockerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 132;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelY = 39;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		g.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFF000000);
		g.fill(x + 1, y + 1, x + this.imageWidth - 1, y + this.imageHeight - 1, PANEL);
		for (Slot s : this.menu.slots) {
			int sx = x + s.x - 1;
			int sy = y + s.y - 1;
			g.fill(sx, sy, sx + 18, sy + 18, SLOT_DARK);
			g.fill(sx + 1, sy + 1, sx + 18, sy + 18, SLOT_LIGHT);
			g.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_FILL);
		}
		g.drawString(this.font, Component.translatable("container.projecthero.all_might_locker.costume"),
				x + AllMightLockerMenu.SLOT_X_CHEST + 20, y + AllMightLockerMenu.SLOT_Y + 5, 0xFF404040, false);
		g.drawString(this.font, Component.translatable("container.projecthero.all_might_locker.trousers"),
				x + AllMightLockerMenu.SLOT_X_LEGS + 20, y + AllMightLockerMenu.SLOT_Y + 5, 0xFF404040, false);
	}
}
