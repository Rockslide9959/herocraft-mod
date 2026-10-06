package com.projecthero.mod.client.gui;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.gear.StarkGear;
import com.projecthero.mod.ironman.gear.StarkGearLayout;
import com.projecthero.mod.ironman.gear.StarkGearMenu;
import com.projecthero.mod.ironman.item.IronManItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.1: the Stark Gear screen (Sneak + N as Tony Stark). Drawn in code in the Iron Man UI style: a 3D preview of you
 * wearing the glasses, the Stark Gear slot, and a status panel -- suit calling, Night Vision, Protocol Phoenix -- whose
 * text wraps inside the panel. Layout numbers: {@link StarkGearLayout} (gametested to fit).
 */
public class StarkGearScreen extends AbstractContainerScreen<StarkGearMenu> {
	public StarkGearScreen(StarkGearMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = StarkGearLayout.W;
		this.imageHeight = StarkGearLayout.H;
		this.titleLabelX = 8;
		this.titleLabelY = 5;
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		IronManGui.panel(g, x, y, imageWidth, imageHeight, IronManGui.PANEL_BG, IronManGui.PANEL_BORDER, IronManGui.CYAN_DIM);
		g.fill(x + 6, y + 14, x + imageWidth - 6, y + 15, IronManGui.alpha(IronManGui.CYAN_DIM, 0.6f));

		// the preview well: you, wearing the glasses (the render layer draws them)
		int px = x + StarkGearLayout.PREVIEW_X;
		int py = y + StarkGearLayout.PREVIEW_Y;
		IronManGui.well(g, px, py, StarkGearLayout.PREVIEW_W, StarkGearLayout.PREVIEW_H);
		if (minecraft != null && minecraft.player != null) {
			InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 1, py + 1, px + StarkGearLayout.PREVIEW_W - 1,
					py + StarkGearLayout.PREVIEW_H - 1, 33, 0.0625f, mouseX, mouseY, minecraft.player);
		}

		// slot wells: the gear slot (gold-rimmed) and the inventory
		for (Slot slot : menu.slots) {
			IronManGui.well(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
		}
		Slot gear = menu.slots.get(StarkGearMenu.GEAR_SLOT);
		g.renderOutline(x + gear.x - 2, y + gear.y - 2, 20, 20, IronManGui.alpha(IronManGui.GOLD, 0.8f));
		if (!gear.hasItem()) {
			// a ghost of the glasses shows what goes here
			g.renderItem(new ItemStack(IronManItems.STARK_GLASSES), x + gear.x, y + gear.y);
			g.pose().pushPose();
			g.pose().translate(0, 0, 200);
			g.fill(x + gear.x, y + gear.y, x + gear.x + 16, y + gear.y + 16, 0xB0070A10);
			g.pose().popPose();
		}

		IronManGui.panel(g, x + StarkGearLayout.PANEL_X, y + StarkGearLayout.PANEL_Y, StarkGearLayout.PANEL_W,
				StarkGearLayout.PANEL_H, IronManGui.PANEL_BG_SOFT, IronManGui.PANEL_BORDER, IronManGui.CYAN_DIM);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, IronManGui.fit(font, title, imageWidth - 16), titleLabelX, titleLabelY, IronManGui.CYAN, false);
		g.drawString(font, IronManGui.fit(font, Component.translatable("screen.projecthero.stark_gear.slot"),
				StarkGearLayout.SLOT_LABEL_W), StarkGearLayout.SLOT_LABEL_X, StarkGearLayout.SLOT_Y + 4, IronManGui.GOLD, false);

		boolean glasses = minecraft != null && minecraft.player != null && StarkGear.canCall(minecraft.player); // v0.15.4: or the bracelets
		boolean nightVision = minecraft != null && minecraft.player != null && StarkGear.hasGlasses(minecraft.player);
		TonyStarkState st = minecraft == null || minecraft.player == null ? null
				: minecraft.player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		long now = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
		StarkGearLayout.Row[] rows = StarkGearLayout.rows(glasses, nightVision, st == null ? 0L : st.phoenixReadyAt, now);

		int tx = StarkGearLayout.TEXT_X;
		int tw = StarkGearLayout.TEXT_W;
		int ry = StarkGearLayout.ROW_Y;
		for (StarkGearLayout.Row row : rows) {
			int colour = switch (row.tone()) {
				case GOOD -> IronManGui.GREEN;
				case BAD -> IronManGui.RED;
				case WARN -> IronManGui.GOLD;
				case OFF -> IronManGui.TEXT_MUTED;
			};
			String value = Component.translatable(row.valueKey(), row.valueArgs()).getString();
			int cw = IronManGui.chipWidth(font, value, tw / 2);
			IronManGui.chip(g, font, tx + tw - cw, ry, tw / 2, value, colour);
			String label = IronManGui.fit(font, Component.translatable(row.labelKey()), tw - cw - StarkGearLayout.LABEL_GAP);
			g.drawString(font, label, tx, ry + 2, IronManGui.TEXT, false);
			List<FormattedCharSequence> hint = font.split(Component.translatable(row.hintKey()), tw);
			for (int i = 0; i < Math.min(StarkGearLayout.HINT_LINES, hint.size()); i++) {
				g.drawString(font, hint.get(i), tx, ry + 13 + i * StarkGearLayout.LINE_H, IronManGui.TEXT_DIM, false);
			}
			ry += StarkGearLayout.ROW_H;
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
		Slot gear = menu.slots.get(StarkGearMenu.GEAR_SLOT);
		if (!gear.hasItem() && menu.getCarried().isEmpty() && isHovering(gear.x, gear.y, 16, 16, mouseX, mouseY)) {
			g.renderTooltip(font, font.split(Component.translatable("screen.projecthero.stark_gear.slot_tip"), 160), mouseX, mouseY);
		}
	}
}
