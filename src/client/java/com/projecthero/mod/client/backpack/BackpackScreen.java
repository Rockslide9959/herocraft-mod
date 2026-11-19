package com.projecthero.mod.client.backpack;

import com.projecthero.mod.backpack.BackpackMenu;
import com.projecthero.mod.client.ModKeyBindings;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * v0.17.5 backpacks: the open backpack -- vanilla's chest screen, as many rows as the tier has, the player's inventory below.
 * The backpack's own slot is shaded (it cannot be moved while open), the top right reads how many slots are in use, and the
 * Open Backpack key closes it again.
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {
	private static final ResourceLocation CHEST = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

	private final int rows;

	public BackpackScreen(BackpackMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		rows = menu.rows();
		imageHeight = 114 + rows * 18;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		// the backpack itself: shaded, it stays put while it is open
		for (Slot s : menu.slots) {
			if (s.container instanceof Inventory && s.getContainerSlot() == menu.invSlot()) {
				g.fill(leftPos + s.x, topPos + s.y, leftPos + s.x + 16, topPos + s.y + 16, 400, 0xA0101010);
			}
		}
		renderTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
		int x = (width - imageWidth) / 2;
		int y = (height - imageHeight) / 2;
		g.blit(CHEST, x, y, 0, 0, imageWidth, rows * 18 + 17);
		g.blit(CHEST, x, y + rows * 18 + 17, 0, 126, imageWidth, 96);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		super.renderLabels(g, mouseX, mouseY);
		int used = 0;
		int size = menu.container().getContainerSize();
		for (int i = 0; i < size; i++) {
			if (!menu.container().getItem(i).isEmpty()) {
				used++;
			}
		}
		Component fill = Component.translatable("container.projecthero.backpack.fill", used, size).withStyle(used >= size ? ChatFormatting.RED : ChatFormatting.DARK_GRAY);
		g.drawString(font, fill, imageWidth - 8 - font.width(fill), titleLabelY, 0x404040, false);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.OPEN_BACKPACK.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}
}
