package com.projecthero.mod.client.hulk;

import java.util.List;

import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.hulk.gladiator.GladiatorGear;
import com.projecthero.mod.hulk.gladiator.GladiatorGearLayout;
import com.projecthero.mod.hulk.gladiator.GladiatorGearMenu;
import com.projecthero.mod.hulk.gladiator.GladiatorItems;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.3: the Gladiator Gear screen (tap N as Banner). Drawn in code in a Sakaar-arena style -- dark iron panels with
 * gold brackets, green for the Hulk: five armour slots on the left, the hammer and axe on the right, "N/7 equipped" and
 * a hint that wraps inside its panel. Layout numbers: {@link GladiatorGearLayout} (gametested to fit).
 */
public class GladiatorGearScreen extends AbstractContainerScreen<GladiatorGearMenu> {
	private static final int BG = 0xE8100E0C;
	private static final int BG_SOFT = 0xB0171410;
	private static final int BORDER = 0xFF4A3B26;
	private static final int GOLD = 0xFFE0B048;
	private static final int GOLD_DIM = 0xFF8A6A2C;
	private static final int GREEN = 0xFF7BE05A;
	private static final int RED = 0xFFE05A4A;
	private static final int TEXT = 0xFFEDE3D0;
	private static final int TEXT_DIM = 0xFFB0A58E;

	public GladiatorGearScreen(GladiatorGearMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = GladiatorGearLayout.W;
		this.imageHeight = GladiatorGearLayout.H;
		this.titleLabelX = 8;
		this.titleLabelY = 5;
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		IronManGui.panel(g, x, y, imageWidth, imageHeight, BG, BORDER, GOLD_DIM);
		g.fill(x + 6, y + 14, x + imageWidth - 6, y + 15, IronManGui.alpha(GOLD_DIM, 0.7f));
		IronManGui.panel(g, x + GladiatorGearLayout.LEFT_X, y + GladiatorGearLayout.PANEL_Y, GladiatorGearLayout.LEFT_W,
				GladiatorGearLayout.PANEL_H, BG_SOFT, BORDER, GOLD_DIM);
		IronManGui.panel(g, x + GladiatorGearLayout.RIGHT_X, y + GladiatorGearLayout.PANEL_Y, GladiatorGearLayout.RIGHT_W,
				GladiatorGearLayout.PANEL_H, BG_SOFT, BORDER, GOLD_DIM);

		for (Slot slot : menu.slots) {
			IronManGui.well(g, x + slot.x - 1, y + slot.y - 1, 18, 18);
		}
		for (int i = GladiatorGearMenu.GEAR_START; i < GladiatorGearMenu.GEAR_END; i++) {
			Slot gear = menu.slots.get(i);
			int rim = gear.hasItem() ? GREEN : GOLD;
			g.renderOutline(x + gear.x - 2, y + gear.y - 2, 20, 20, IronManGui.alpha(rim, gear.hasItem() ? 0.85f : 0.55f));
			if (!gear.hasItem()) {
				// a ghost of the piece shows what goes here
				Item ghost = GladiatorItems.forSlot(i);
				if (ghost != null) {
					g.renderItem(new ItemStack(ghost), x + gear.x, y + gear.y);
					g.pose().pushPose();
					g.pose().translate(0, 0, 200);
					g.fill(x + gear.x, y + gear.y, x + gear.x + 16, y + gear.y + 16, 0xB0100E0C);
					g.pose().popPose();
				}
			}
		}
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, IronManGui.fit(font, title, imageWidth - 16), titleLabelX, titleLabelY, GOLD, false);
		for (int i = 0; i < GladiatorGear.SLOTS; i++) {
			Slot gear = menu.slots.get(i);
			String label = IronManGui.fit(font, Component.translatable(GladiatorGearLayout.labelKey(i)), GladiatorGearLayout.labelW(i));
			g.drawString(font, label, GladiatorGearLayout.labelX(i), gear.y + 4, gear.hasItem() ? TEXT : TEXT_DIM, false);
		}

		int count = 0;
		for (int i = 0; i < GladiatorGear.SLOTS; i++) {
			if (menu.slots.get(i).hasItem()) {
				count++;
			}
		}
		boolean full = count >= GladiatorGear.SLOTS;
		String status = Component.translatable(GladiatorGearLayout.EQUIPPED_KEY, count).getString();
		IronManGui.chip(g, font, GladiatorGearLayout.STATUS_X, GladiatorGearLayout.STATUS_Y, GladiatorGearLayout.STATUS_W,
				status, full ? GREEN : count == 0 ? RED : GOLD);
		List<FormattedCharSequence> hint = font.split(Component.translatable(GladiatorGearLayout.hintKey(count)),
				GladiatorGearLayout.STATUS_W);
		for (int i = 0; i < Math.min(GladiatorGearLayout.HINT_LINES, hint.size()); i++) {
			g.drawString(font, hint.get(i), GladiatorGearLayout.STATUS_X, GladiatorGearLayout.HINT_Y + i * GladiatorGearLayout.LINE_H,
					full ? GREEN : TEXT_DIM, false);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		this.renderTooltip(g, mouseX, mouseY);
		for (int i = GladiatorGearMenu.GEAR_START; i < GladiatorGearMenu.GEAR_END; i++) {
			Slot gear = menu.slots.get(i);
			Item piece = GladiatorItems.forSlot(i);
			if (!gear.hasItem() && piece != null && menu.getCarried().isEmpty() && isHovering(gear.x, gear.y, 16, 16, mouseX, mouseY)) {
				g.renderTooltip(font, font.split(Component.translatable("screen.projecthero.gladiator_gear.slot_tip",
						new ItemStack(piece).getHoverName()), 160), mouseX, mouseY);
			}
		}
	}
}
