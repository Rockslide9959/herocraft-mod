package com.herocraft.mod.client.gui;

import java.util.List;
import java.util.Locale;

import com.herocraft.mod.ironman.fabricator.FabricationRecipe;
import com.herocraft.mod.ironman.fabricator.FabricatorRecipes;
import com.herocraft.mod.ironman.fabricator.StarkFabricatorMenu;
import com.herocraft.mod.ironman.item.BlueprintItem;
import com.herocraft.mod.ironman.suit.IronManSuit;
import com.herocraft.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Stark Fabricator screen. Procedural flat panel -- no background texture.
 *
 * <p>"changes 18": with a Fabricator-buildable suit blueprint in the blueprint slot, a button band
 * below the energy readout lets the operator pick which piece to build (Helmet / Chest / Legs /
 * Boots). The 3x3 grid then shows the components that piece needs as greyed "ghost" stacks in the
 * blanks, and a <b>View more</b> button opens a panel listing every component in words plus the
 * finished suit's stats. Slot geometry matches {@link StarkFabricatorMenu}.
 */
public class StarkFabricatorScreen extends AbstractContainerScreen<StarkFabricatorMenu> {
	private static final int BG = 0xFF181C24;
	private static final int PANEL = 0xFF20242E;
	private static final int PANEL_BORDER = 0xFF3C4658;
	private static final int CELL_BG = 0xFF12151C;
	private static final int CELL_BORDER = 0xFF3C4658;
	private static final int LABEL = 0xFF7FA8D8;
	private static final int BTN = 0xFF2B3242;
	private static final int BTN_HOVER = 0xFF3A4152;
	private static final int BTN_SEL = 0xFF3F6B4A;

	private static final String[] PIECE_KEYS = { "helmet", "chestplate", "leggings", "boots" };

	// button band geometry (screen-relative). The whole lower readout stack was widened + given more
	// vertical room so the status line can wrap instead of spilling past the panel edge.
	private static final int PIECE_Y = 120;
	private static final int PIECE_W = 44;
	private static final int PIECE_GAP = 2;
	private static final int PIECE_H = 12;
	private static final int INFO_Y = 134;
	private static final int INFO_W = 70;

	/** Panel inner geometry, screen-relative. */
	private static final int PANEL_TOP = 16;
	private static final int PANEL_BOT = 148;
	/** Left inset for text drawn inside the panel, and the width text may occupy before it must wrap. */
	private static final int TEXT_X = 10;

	private boolean showInfo;

	public StarkFabricatorScreen(StarkFabricatorMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = 200;
		this.imageHeight = 248;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = 8;
		this.inventoryLabelY = 152;
	}

	private int textWrapWidth() {
		return imageWidth - TEXT_X - 6;
	}

	private boolean hasSuitBlueprint() {
		return menu.blueprintStack().getItem() instanceof BlueprintItem bp && bp.suitId() != null
				&& FabricatorRecipes.hasArmorRecipes(bp.suitId());
	}

	private String blueprintSuitId() {
		return menu.blueprintStack().getItem() instanceof BlueprintItem bp ? bp.suitId() : null;
	}

	private FabricationRecipe selectedRecipe() {
		String suitId = blueprintSuitId();
		int piece = menu.selectedPiece();
		if (suitId == null || piece < 1 || piece > 4) {
			return null;
		}
		var type = FabricatorRecipes.pieceType(piece);
		return type == null ? null : FabricatorRecipes.byId("iron_man_" + suitId + "_" + type.getName());
	}

	private void cell(GuiGraphics g, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, CELL_BORDER);
		g.fill(x, y, x + 16, y + 16, CELL_BG);
	}

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;

		g.fill(x, y, x + imageWidth, y + imageHeight, BG);
		g.fill(x + 4, y + PANEL_TOP, x + imageWidth - 4, y + PANEL_BOT, PANEL);
		g.fill(x + 4, y + PANEL_TOP, x + imageWidth - 4, y + PANEL_TOP + 1, PANEL_BORDER);
		g.fill(x + 4, y + PANEL_BOT - 1, x + imageWidth - 4, y + PANEL_BOT, PANEL_BORDER);

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				cell(g, x + 44 + col * 18, y + 17 + row * 18);
			}
		}
		cell(g, x + 12, y + 35);   // blueprint
		cell(g, x + 120, y + 35);  // output

		int ay = y + 40;
		g.fill(x + 102, ay + 2, x + 116, ay + 4, PANEL_BORDER);
		g.fill(x + 113, ay - 1, x + 115, ay + 7, PANEL_BORDER);

		int gx = x + imageWidth - 24, gTop = y + 17, gBot = y + 71;
		g.fill(gx - 1, gTop - 1, gx + 11, gBot + 1, CELL_BORDER);
		g.fill(gx, gTop, gx + 10, gBot, 0xFF10141C);
		int maxE = Math.max(1, menu.maxEnergy());
		int lvl = Math.round((gBot - gTop) * (menu.energy() / (float) maxE));
		g.fill(gx, gBot - lvl, gx + 10, gBot, 0xFF4FA8FF);

		if (menu.maxProgress() > 0) {
			int bx = x + 12, bw = imageWidth - 24, by = y + 76;
			g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, CELL_BORDER);
			g.fill(bx, by, bx + bw, by + 5, 0xFF10141C);
			int pw = Math.round(bw * (menu.progress() / (float) menu.maxProgress()));
			g.fill(bx, by, bx + pw, by + 5, 0xFF66E0A0);
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				cell(g, x + 8 + col * 18, y + 164 + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			cell(g, x + 8 + col * 18, y + 224);
		}

		renderGhostInputs(g, x, y);
		if (hasSuitBlueprint()) {
			renderPieceButtons(g, x, y, mouseX, mouseY);
		}
	}

	private void renderGhostInputs(GuiGraphics g, int x, int y) {
		FabricationRecipe recipe = selectedRecipe();
		if (recipe == null) {
			return;
		}
		List<FabricationRecipe.Input> inputs = recipe.inputs();
		for (int i = 0; i < inputs.size() && i < 9; i++) {
			FabricationRecipe.Input in = inputs.get(i);
			ItemStack present = menu.slots.get(i).getItem();
			if (present.is(in.item()) && present.getCount() >= in.count()) {
				continue;
			}
			int cx = x + 44 + (i % 3) * 18;
			int cy = y + 17 + (i / 3) * 18;
			g.renderFakeItem(new ItemStack(in.item()), cx, cy);
			g.fill(cx, cy, cx + 16, cy + 16, 0xB0181C24);
			String n = String.valueOf(in.count());
			g.pose().pushPose();
			g.pose().translate(0, 0, 250);
			g.drawString(this.font, n, cx + 17 - this.font.width(n), cy + 9, 0xFF9AA6D0, true);
			g.pose().popPose();
		}
	}

	private void renderPieceButtons(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
		int sel = menu.selectedPiece();
		for (int i = 0; i < 4; i++) {
			int bx = x + 8 + i * (PIECE_W + PIECE_GAP);
			boolean hover = inRect(mouseX, mouseY, bx, y + PIECE_Y, PIECE_W, PIECE_H);
			int col = (sel == i + 1) ? BTN_SEL : (hover ? BTN_HOVER : BTN);
			g.fill(bx, y + PIECE_Y, bx + PIECE_W, y + PIECE_Y + PIECE_H, col);
			Component label = Component.translatable("screen.herocraft.stark_fabricator.piece." + PIECE_KEYS[i]);
			g.drawString(this.font, label, bx + (PIECE_W - this.font.width(label)) / 2, y + PIECE_Y + 2,
					sel == i + 1 ? 0xFFCFEAD3 : 0xFFB8C0E0, false);
		}
		boolean infoHover = inRect(mouseX, mouseY, x + 8, y + INFO_Y, INFO_W, PIECE_H);
		g.fill(x + 8, y + INFO_Y, x + 8 + INFO_W, y + INFO_Y + PIECE_H,
				showInfo ? BTN_SEL : (infoHover ? BTN_HOVER : BTN));
		g.drawString(this.font, Component.translatable("screen.herocraft.stark_fabricator.view_more"),
				x + 12, y + INFO_Y + 2, 0xFFB8C0E0, false);
	}

	private static boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
		return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		super.renderLabels(g, mouseX, mouseY);

		g.drawString(this.font, Component.translatable("screen.herocraft.stark_fabricator.blueprint"), 8, 27, LABEL, false);
		g.drawString(this.font, Component.translatable("screen.herocraft.stark_fabricator.output"), 108, 27, LABEL, false);
		g.drawString(this.font, Component.translatable("screen.herocraft.stark_fabricator.energy_label"),
				imageWidth - 48, 6, LABEL, false);

		Component status;
		int statusColor;
		if (menu.maxProgress() > 0) {
			int pct = Math.round(100f * menu.progress() / menu.maxProgress());
			status = Component.translatable("screen.herocraft.stark_fabricator.fabricating", pct)
					.withStyle(ChatFormatting.GREEN);
			statusColor = 0xFFB8FFD0;
		} else if (hasSuitBlueprint() && menu.selectedPiece() == 0) {
			status = Component.translatable("screen.herocraft.stark_fabricator.pick_piece").withStyle(ChatFormatting.GRAY);
			statusColor = 0xFF8A93A8;
		} else {
			status = Component.translatable("screen.herocraft.stark_fabricator.idle_hint").withStyle(ChatFormatting.GRAY);
			statusColor = 0xFF8A93A8;
		}
		int sy = 84;
		for (FormattedCharSequence line : this.font.split(status, textWrapWidth())) {
			g.drawString(this.font, line, TEXT_X, sy, statusColor, false);
			sy += 10;
		}

		int maxE = Math.max(1, menu.maxEnergy());
		String pctText = String.format(Locale.ROOT, "%.1f", 100.0 * menu.energy() / maxE);
		g.drawString(this.font, Component.literal(menu.energy() + " / " + menu.maxEnergy() + "  (" + pctText + "%)")
				.withStyle(ChatFormatting.AQUA), TEXT_X, 106, 0xFF5FB0FF, false);
	}

	// ---- View-more overlay: a self-contained modal drawn ABOVE everything (item icons render at a
	//      high Z, so the panel is pushed to an even higher layer -- see the screenshot bug), fully
	//      opaque, and scrollable so its content can never spill past its own frame. ----
	private int infoScroll;
	private int infoScrollMax;

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		if (showInfo && hasSuitBlueprint()) {
			renderInfoOverlay(g, mouseX, mouseY);
		} else {
			this.renderTooltip(g, mouseX, mouseY);
		}
	}

	private void renderInfoOverlay(GuiGraphics g, int mouseX, int mouseY) {
		var pose = g.pose();
		pose.pushPose();
		pose.translate(0, 0, 400); // above item icons (~232) and the rest of the container screen

		g.fill(0, 0, this.width, this.height, 0xCC0A0C12); // dim the screen behind the modal

		int w = Math.min(224, this.width - 20);
		int h = Math.min(190, this.height - 20);
		int rx = (this.width - w) / 2;
		int ry = (this.height - h) / 2;
		int hdr = 15;
		g.fill(rx - 1, ry - 1, rx + w + 1, ry + h + 1, 0xFF6E7C92);
		g.fill(rx, ry, rx + w, ry + h, 0xFF0E1118); // fully opaque
		g.fill(rx, ry + hdr, rx + w, ry + hdr + 1, 0xFF3C4658);

		IronManSuit suit = IronManSuits.byId(blueprintSuitId());
		g.drawString(this.font, (suit != null
				? Component.translatable(suit.nameKey()) : Component.literal("Blueprint"))
				.copy().withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), rx + 6, ry + 4, 0xFF7FE9FF, false);
		g.drawString(this.font, Component.literal("[x]").withStyle(ChatFormatting.GRAY),
				rx + w - 16, ry + 4, 0xFFB8C0E0, false);

		int top = ry + hdr + 3;
		int bot = ry + h - 3;
		int visible = bot - top;

		List<Component> lines = buildInfoLines(suit);
		int contentH = lines.size() * 10;
		infoScrollMax = Math.max(0, contentH - visible);
		infoScroll = Math.max(0, Math.min(infoScroll, infoScrollMax));

		g.enableScissor(rx, top, rx + w, bot);
		int y = top - infoScroll;
		for (Component c : lines) {
			if (y + 10 >= top && y <= bot) {
				g.drawString(this.font, c, rx + 6, y, 0xFFCFCFE6, false);
			}
			y += 10;
		}
		g.disableScissor();

		if (infoScrollMax > 0) {
			int trackX = rx + w - 4;
			g.fill(trackX, top, trackX + 3, bot, 0xFF20242E);
			int thumbH = Math.max(14, visible * visible / contentH);
			int thumbY = top + (visible - thumbH) * infoScroll / infoScrollMax;
			g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFF5A6478);
		}

		pose.popPose();
	}

	private List<Component> buildInfoLines(IronManSuit suit) {
		List<Component> out = new java.util.ArrayList<>();
		FabricationRecipe recipe = selectedRecipe();
		if (recipe != null) {
			int piece = menu.selectedPiece();
			out.add(Component.translatable("screen.herocraft.stark_fabricator.needs_for",
					Component.translatable("screen.herocraft.stark_fabricator.piece." + PIECE_KEYS[piece - 1]))
					.withStyle(ChatFormatting.GOLD));
			for (FabricationRecipe.Input in : recipe.inputs()) {
				out.add(Component.literal(" " + in.count() + "x ").withStyle(ChatFormatting.GRAY)
						.append(new ItemStack(in.item()).getHoverName().copy().withStyle(ChatFormatting.WHITE)));
			}
			out.add(Component.translatable("screen.herocraft.stark_fabricator.cost", recipe.energyCost(),
					String.format(Locale.ROOT, "%.1f", recipe.timeTicks() / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
			out.add(Component.empty());
		} else {
			out.add(Component.translatable("screen.herocraft.stark_fabricator.pick_piece").withStyle(ChatFormatting.GRAY));
			out.add(Component.empty());
		}
		if (suit != null) {
			out.add(Component.translatable("screen.herocraft.stark_fabricator.suit_stats").withStyle(ChatFormatting.GOLD));
			out.add(statLine("armour_integrity", String.valueOf(Math.round(suit.maxIntegrity()))));
			out.add(statLine("energy_capacity", String.valueOf(Math.round(suit.energyCapacity()))));
			out.add(statLine("energy_regen", fmt(suit.energyRegenPerSecond())));
			if (suit.armorRegenPerSecond() > 0f) {
				out.add(statLine("armour_regen", fmt(suit.armorRegenPerSecond())));
			}
			out.add(statLine("melee_bonus", "+" + (int) suit.strengthBonus()));
			out.add(statLine("flight_drain", fmt(suit.flightDrainMultiplier())));
		}
		return out;
	}

	private static String fmt(float v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	private Component statLine(String key, String value) {
		return Component.literal(" ")
				.append(Component.translatable("screen.herocraft.stark_fabricator.stat." + key))
				.append(": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(ChatFormatting.WHITE));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		if (showInfo && hasSuitBlueprint()) {
			infoScroll = Math.max(0, Math.min(infoScrollMax, infoScroll - (int) Math.round(dy * 12)));
			return true;
		}
		return super.mouseScrolled(mx, my, dx, dy);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (showInfo && hasSuitBlueprint()) {
			// modal: any click closes it (clicking inside just dismisses too -- there's nothing to click)
			showInfo = false;
			infoScroll = 0;
			return true;
		}
		if (button == 0 && hasSuitBlueprint()) {
			int x = this.leftPos;
			int y = this.topPos;
			for (int i = 0; i < 4; i++) {
				int bx = x + 8 + i * (PIECE_W + PIECE_GAP);
				if (inRect(mx, my, bx, y + PIECE_Y, PIECE_W, PIECE_H)) {
					if (this.minecraft != null && this.minecraft.gameMode != null) {
						this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i + 1);
					}
					return true;
				}
			}
			if (inRect(mx, my, x + 8, y + INFO_Y, INFO_W, PIECE_H)) {
				showInfo = true;
				infoScroll = 0;
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}
}
