package com.projecthero.mod.client.city;

import com.projecthero.mod.city.bank.SafeDepositMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * v0.16.2 City part B: the Safe Deposit screen -- the 3-row chest frame, with the vault's emerald value and the
 * interest earned so far (out of the cap) in the title bar.
 */
public class SafeDepositScreen extends AbstractContainerScreen<SafeDepositMenu> {
	private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

	public SafeDepositScreen(SafeDepositMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		imageHeight = 114 + SafeDepositMenu.ROWS * 18;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		renderTooltip(g, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = (width - imageWidth) / 2;
		int y = (height - imageHeight) / 2;
		g.blit(TEXTURE, x, y, 0, 0, imageWidth, SafeDepositMenu.ROWS * 18 + 17);
		g.blit(TEXTURE, x, y + SafeDepositMenu.ROWS * 18 + 17, 0, 126, imageWidth, 96);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
		Component info = Component.translatable("container.projecthero.safe_deposit.info", menu.value(), menu.interestPaid(), menu.interestCap());
		int w = font.width(info);
		g.drawString(font, info, imageWidth - 8 - w, titleLabelY, 0x1F7A3A, false);
		g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
	}
}
