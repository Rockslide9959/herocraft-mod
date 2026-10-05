package com.projecthero.mod.client.sorter;

import com.projecthero.mod.ironman.sorter.SortingStationBlockEntity;
import com.projecthero.mod.ironman.sorter.SortingStationLayout;
import com.projecthero.mod.ironman.sorter.SortingStationMenu;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * v0.14.16: the Stark Sorting Station screen. A procedural flat panel in the Fabricator's style (no background
 * texture) with Stark red/gold trim: the 6x9 store, a status band with the job's progress bar and the
 * <b>Sort</b> and (v0.14.20) <b>Tidy</b> buttons, then the player inventory. v0.14.21 adds a supply column on the
 * right: three sign slots, three chest slots (ghost icons while empty) and what the room still needs ("Add 3
 * signs"), wrapped to the column. Slot geometry matches {@link SortingStationMenu}.
 *
 * <p>v0.14.29: the panel was 262 px tall and clipped at GUI scale 3 on a 720p window. The status band (status, progress
 * bar, Sort and Tidy side by side) moved to the foot of the right column, so the panel is now 272 x 222 and fits a
 * 320 x 240 scaled screen. Every coordinate comes from {@link SortingStationLayout} (checked by
 * {@code SortingStationLayoutGameTests}).
 */
public class SortingStationScreen extends AbstractContainerScreen<SortingStationMenu> {
	private static final int BG = 0xFF181C24;
	private static final int PANEL = 0xFF20242E;
	private static final int GOLD = 0xFFD9A43A;
	private static final int RED = 0xFF9E2026;
	private static final int CELL_BG = 0xFF12151C;
	private static final int CELL_BORDER = 0xFF3C4658;
	private static final int ARC = 0xFF5FD8FF;
	private static final int TEXT = 0xFFB8C0E0;

	private static final int HINT_WIDTH = 180;
	// v0.14.29: everything below is SortingStationLayout -- the band now lives in the right column
	private static final int MAIN_W = SortingStationLayout.MAIN_W;
	private static final int NEEDS_X = SortingStationLayout.SIDE_X;
	private static final int NEEDS_Y = SortingStationLayout.NEEDS_Y;
	private static final int NEEDS_W = SortingStationLayout.SIDE_TEXT_W;
	private static final int NEEDS_BOTTOM = SortingStationLayout.NEEDS_BOTTOM;

	public SortingStationScreen(SortingStationMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = SortingStationLayout.W;
		this.imageHeight = SortingStationLayout.H;
		this.titleLabelX = SortingStationLayout.TITLE_X;
		this.titleLabelY = SortingStationLayout.TITLE_Y;
		this.inventoryLabelX = SortingStationLayout.STORE_X;
		this.inventoryLabelY = SortingStationLayout.INV_LABEL_Y;
	}

	private static final ItemStack GHOST_SIGN = new ItemStack(Items.OAK_SIGN);
	private static final ItemStack GHOST_CHEST = new ItemStack(Items.CHEST);

	/** A faded item in an empty supply slot, so it is obvious what goes there. */
	private void ghost(GuiGraphics g, int slotIndex, int x, int y, ItemStack icon) {
		if (menu.getSlot(slotIndex).hasItem()) {
			return;
		}
		g.renderFakeItem(icon, x, y);
		g.pose().pushPose();
		g.pose().translate(0, 0, 300);
		g.fill(x, y, x + 16, y + 16, 0xB012151C);
		g.pose().popPose();
	}

	/** v0.14.21: the "what the room still needs" lines for the supply column. */
	private java.util.List<Component> needsLines() {
		java.util.List<Component> out = new java.util.ArrayList<>();
		if (menu.signsShort() > 0) {
			out.add(Component.translatable("screen.projecthero.stark_sorting_station.add_signs", menu.signsShort())
					.withStyle(ChatFormatting.GOLD));
		}
		if (menu.chestsShort() > 0) {
			out.add(Component.translatable("screen.projecthero.stark_sorting_station.add_chests", menu.chestsShort())
					.withStyle(ChatFormatting.GOLD));
		}
		if (menu.noSignFace() > 0) {
			out.add(Component.translatable("screen.projecthero.stark_sorting_station.no_sign_face", menu.noSignFace())
					.withStyle(ChatFormatting.GRAY));
		}
		if (out.isEmpty()) {
			out.add(Component.translatable("screen.projecthero.stark_sorting_station.supply_ok").withStyle(ChatFormatting.GREEN));
		}
		return out;
	}

	private boolean overNeeds(double mx, double my) {
		return SortingStationLayout.needs().contains(mx - leftPos, my - topPos);
	}

	private void cell(GuiGraphics g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, CELL_BORDER);
		g.fill(x, y, x + 16, y + 16, CELL_BG);
	}

	private boolean overButton(double mx, double my, Rect btn) {
		return btn.contains(mx - leftPos, my - topPos);
	}

	/** One of the two band buttons; {@code active} = this button's job is the one running. */
	private void button(GuiGraphics g, Rect btn, String labelKey, boolean active, int mouseX, int mouseY) {
		boolean busy = menu.running();
		boolean hover = !busy && overButton(mouseX, mouseY, btn);
		int bxx = leftPos + btn.x();
		int byy = topPos + btn.y();
		g.fill(bxx - 1, byy - 1, bxx + btn.w() + 1, byy + btn.h() + 1, busy ? CELL_BORDER : GOLD);
		g.fill(bxx, byy, bxx + btn.w(), byy + btn.h(), busy ? 0xFF2B3242 : (hover ? 0xFFB8282F : RED));
		Component label = Component.translatable(busy && active ? "screen.projecthero.stark_sorting_station.busy" : labelKey);
		g.drawString(font, label, bxx + (btn.w() - font.width(label)) / 2, byy + (btn.h() - 8) / 2 + 1,
				busy ? 0xFF8A93A8 : 0xFFFFE9B0, true);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = leftPos;
		int y = topPos;
		g.fill(x, y, x + imageWidth, y + imageHeight, BG);
		// red/gold Stark trim, and the divider between the two columns
		g.fill(x, y, x + imageWidth, y + 2, RED);
		g.fill(x, y + 2, x + imageWidth, y + 3, GOLD);
		g.fill(x, y + imageHeight - 2, x + imageWidth, y + imageHeight, RED);
		g.fill(x + MAIN_W, y + 3, x + MAIN_W + 1, y + imageHeight - 2, CELL_BORDER);

		// v0.14.21: supply slots, with a faded sign / chest while empty
		for (int i = 0; i < SortingStationBlockEntity.SUPPLY_SLOTS; i++) {
			int[] xy = SortingStationLayout.supplySlot(i);
			cell(g, x + xy[0], y + xy[1]);
			ghost(g, SortingStationMenu.SUPPLY_START + i, x + xy[0], y + xy[1], i < 3 ? GHOST_SIGN : GHOST_CHEST);
		}
		for (int i = 0; i < SortingStationBlockEntity.SIZE; i++) {
			int[] xy = SortingStationLayout.storeSlot(i);
			cell(g, x + xy[0], y + xy[1]);
		}
		for (int i = 0; i < 36; i++) {
			int[] xy = SortingStationLayout.playerSlot(i);
			cell(g, x + xy[0], y + xy[1]);
		}

		// status band (v0.14.29: the foot of the right column)
		Rect band = SortingStationLayout.band();
		int bx0 = x + band.x();
		int by0 = y + band.y();
		g.fill(bx0, by0, bx0 + band.w(), by0 + band.h(), PANEL);
		g.fill(bx0, by0, bx0 + band.w(), by0 + 1, GOLD);
		g.fill(bx0, by0 + band.h() - 1, bx0 + band.w(), by0 + band.h(), CELL_BORDER);

		Rect bar = SortingStationLayout.bar();
		int bx = x + bar.x();
		int by = y + bar.y();
		g.fill(bx - 1, by - 1, bx + bar.w() + 1, by + 5, CELL_BORDER);
		g.fill(bx, by, bx + bar.w(), by + 4, 0xFF10141C);
		int total = menu.total();
		if (total > 0) {
			float f = Math.min(1f, menu.done() / (float) total);
			g.fill(bx, by, bx + Math.round(bar.w() * f), by + 4, menu.running() ? ARC : 0xFF66E0A0);
		}

		// the Sort and Tidy buttons, side by side
		button(g, SortingStationLayout.sortButton(), "screen.projecthero.stark_sorting_station.sort", !menu.tidyMode(), mouseX, mouseY);
		button(g, SortingStationLayout.tidyButton(), "screen.projecthero.stark_sorting_station.tidy", menu.tidyMode(), mouseX, mouseY);
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		g.drawString(font, title, titleLabelX, titleLabelY, GOLD, false);
		g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF7FA8D8, false);

		Component status;
		boolean tidy = menu.tidyMode();
		if (menu.running()) {
			status = Component.translatable(tidy ? "screen.projecthero.stark_sorting_station.tidying"
					: "screen.projecthero.stark_sorting_station.sorting", menu.done(), menu.total());
		} else if (menu.total() > 0) {
			status = Component.translatable(tidy ? "screen.projecthero.stark_sorting_station.tidied"
					: "screen.projecthero.stark_sorting_station.finished", menu.done(), menu.total());
		} else {
			status = Component.translatable("screen.projecthero.stark_sorting_station.idle");
		}
		// v0.14.21: the supply column -- headings, then what the room still needs, wrapped to the column
		g.drawString(font, Component.translatable("screen.projecthero.stark_sorting_station.supplies"), NEEDS_X,
				SortingStationLayout.TITLE_Y, GOLD, false);
		g.drawString(font, Component.translatable("screen.projecthero.stark_sorting_station.signs"), SortingStationMenu.SUPPLY_X,
				SortingStationMenu.SIGN_Y - 10, 0xFF7FA8D8, false);
		g.drawString(font, Component.translatable("screen.projecthero.stark_sorting_station.chests"), SortingStationMenu.SUPPLY_X,
				SortingStationMenu.CHEST_Y - 10, 0xFF7FA8D8, false);
		int ny = NEEDS_Y;
		for (Component need : needsLines()) {
			for (FormattedCharSequence line : font.split(need, NEEDS_W)) {
				if (ny + 9 > NEEDS_BOTTOM) {
					break;
				}
				g.drawString(font, line, NEEDS_X, ny, 0xFFFFFFFF, false);
				ny += 10;
			}
			ny += 2;
		}

		// stays inside the band: wrap to the column, at most STATUS_LINES lines above the bar
		var lines = font.split(status, SortingStationLayout.SIDE_TEXT_W);
		for (int i = 0; i < Math.min(SortingStationLayout.STATUS_LINES, lines.size()); i++) {
			FormattedCharSequence line = lines.get(i);
			g.drawString(font, line, SortingStationLayout.SIDE_X, SortingStationLayout.STATUS_Y + i * 10,
					menu.running() ? ARC : TEXT, false);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		String hint = null;
		if (!menu.running() && overButton(mouseX, mouseY, SortingStationLayout.sortButton())) {
			hint = "screen.projecthero.stark_sorting_station.sort_hint";
		} else if (!menu.running() && overButton(mouseX, mouseY, SortingStationLayout.tidyButton())) {
			hint = "screen.projecthero.stark_sorting_station.tidy_hint";
		}
		if (hint == null && overNeeds(mouseX, mouseY)) {
			java.util.List<FormattedCharSequence> lines = new java.util.ArrayList<>();
			lines.addAll(font.split(Component.translatable("screen.projecthero.stark_sorting_station.needs_detail",
					menu.signsNeeded(), menu.chestsNeeded()), HINT_WIDTH));
			lines.addAll(font.split(Component.translatable("screen.projecthero.stark_sorting_station.supply_hint"), HINT_WIDTH));
			g.renderTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (hint == null && hoveredSlot instanceof SortingStationMenu.SupplySlot supply && !supply.hasItem()) {
			hint = supply.isSignSlot() ? "screen.projecthero.stark_sorting_station.sign_slot"
					: "screen.projecthero.stark_sorting_station.chest_slot";
		}
		if (hint != null) {
			g.renderTooltip(font, font.split(Component.translatable(hint), HINT_WIDTH), mouseX, mouseY);
		} else {
			renderTooltip(g, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		int id = overButton(mx, my, SortingStationLayout.sortButton()) ? SortingStationMenu.BUTTON_SORT
				: overButton(mx, my, SortingStationLayout.tidyButton()) ? SortingStationMenu.BUTTON_TIDY : -1;
		if (button == 0 && id >= 0 && !menu.running() && minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0f));
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}
}
