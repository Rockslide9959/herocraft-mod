package com.projecthero.mod.client.gui;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.network.StarkGantryActionPayload;
import com.projecthero.mod.network.StarkGantryMenuPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.4: the Stark Gantry menu (H on a complete gantry floor). Unsuited: one card per suit racked on a Suit Platform
 * within 20 blocks (v0.15.9: and per suit carried in the pack) -- a turning 3D preview, the mark's name, CHARGE and
 * INTEGRITY Slab bars with the exact values, how many pieces and where -- click (or arrows + Enter) to have the gantry
 * suit you up.
 *
 * <p>v0.15.9, explicit user request: suited, it first asks what to do -- two buttons, <b>Remove Suit</b> (as before: the
 * suit comes off and is racked) and <b>Swap Suit</b>, which opens the same suit list (minus the worn suit); picking one
 * has the gantry take the worn suit off, send it home and put the picked one on, in one sequence. Keyboard: Left / Right
 * / Tab move between the buttons, Enter picks, R / S are shortcuts; Esc cancels (in the list, Esc / Backspace go back).
 * Every text line is clipped or word-wrapped to the panel. The server re-validates whatever is picked.
 */
public final class StarkGantryScreen extends Screen {
	private static final int ROW_H = 46;
	private static final int ROW_GAP = 4;
	private static final int LIST_TOP = 44;

	/** What the screen is showing: the unsuited pick list, the suited Remove / Swap choice, or the swap pick list. */
	private enum View { PICK, CHOICE, SWAP }

	private final StarkGantryMenuPayload menu;
	private final long openedAt = Util.getMillis();
	private View view;
	private int scroll;
	private int focused;
	private int hovered = -1;
	private IronManGui.StarkButton removeButton;
	private IronManGui.StarkButton swapButton;

	public StarkGantryScreen(StarkGantryMenuPayload menu) {
		super(Component.translatable("screen.projecthero.gantry.title"));
		this.menu = menu;
		this.view = menu.wornSuit().isEmpty() ? View.PICK : View.CHOICE;
	}

	private boolean listing() {
		return view != View.CHOICE;
	}

	private int panelW() {
		return Math.min(300, width - 16);
	}

	private int panelX() {
		return (width - panelW()) / 2;
	}

	private int listBottom() {
		return height - 30;
	}

	private int maxScroll() {
		int content = menu.entries().size() * (ROW_H + ROW_GAP);
		return Math.max(0, content - (listBottom() - LIST_TOP));
	}

	@Override
	protected void init() {
		int bw = Math.min(120, width - 20);
		removeButton = null;
		swapButton = null;
		if (view == View.CHOICE) {
			// two buttons side by side under the worn-suit card; each greyed (with the reason on the card) when it can't run
			int cw = Math.max(40, Math.min(110, (panelW() - 10) / 2));
			int y = LIST_TOP + ROW_H + 10;
			removeButton = new IronManGui.StarkButton(width / 2 - cw - 5, y, cw, 20,
					Component.translatable("screen.projecthero.gantry.choice_remove"), this::remove);
			removeButton.accent = IronManGui.GOLD;
			removeButton.active = menu.canRemove();
			swapButton = new IronManGui.StarkButton(width / 2 + 5, y, cw, 20,
					Component.translatable("screen.projecthero.gantry.choice_swap"), this::openSwap);
			swapButton.active = !menu.entries().isEmpty();
			addRenderableWidget(removeButton);
			addRenderableWidget(swapButton);
			if (removeButton.active || swapButton.active) {
				setInitialFocus(removeButton.active ? removeButton : swapButton);
			}
		}
		addRenderableWidget(new IronManGui.StarkButton(width / 2 - bw / 2, height - 22, bw, 16,
				Component.translatable(view == View.SWAP ? "screen.projecthero.gantry.back" : "gui.cancel"),
				view == View.SWAP ? this::backToChoice : this::onClose));
		scroll = Math.max(0, Math.min(scroll, maxScroll()));
		focused = Math.max(0, Math.min(focused, menu.entries().size() - 1));
	}

	private void remove() {
		if (menu.canRemove()) {
			ClientPlayNetworking.send(new StarkGantryActionPayload(StarkGantryActionPayload.UNEQUIP, BlockPos.ZERO));
			onClose();
		}
	}

	private void openSwap() {
		if (!menu.entries().isEmpty()) {
			view = View.SWAP;
			scroll = 0;
			focused = 0;
			rebuildWidgets();
		}
	}

	private void backToChoice() {
		view = View.CHOICE;
		rebuildWidgets();
	}

	private void pick(StarkGantryMenuPayload.Entry e) {
		int action = view == View.SWAP ? StarkGantryActionPayload.SWAP : StarkGantryActionPayload.EQUIP;
		ClientPlayNetworking.send(new StarkGantryActionPayload(action, e.pack() ? BlockPos.ZERO : e.platform(),
				e.pack() ? e.suitId() : ""));
		onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int px = panelX();
		int pw = panelW();
		Component heading = view == View.SWAP ? Component.translatable("screen.projecthero.gantry.title_swap") : title;
		g.drawCenteredString(font, Component.literal(IronManGui.fit(font, heading.copy().withStyle(s -> s.withBold(true)).getString(), pw)),
				width / 2, 8, IronManGui.CYAN);
		g.fill(px, 19, px + pw, 20, IronManGui.alpha(IronManGui.CYAN_DIM, 0.6f));
		String subKey = switch (view) {
			case PICK -> "screen.projecthero.gantry.sub_pick";
			case CHOICE -> "screen.projecthero.gantry.sub_choice";
			case SWAP -> menu.canRemove() ? "screen.projecthero.gantry.sub_swap" : "screen.projecthero.gantry.sub_swap_pack";
		};
		int sy = 24;
		for (FormattedCharSequence line : font.split(Component.translatable(subKey), pw)) {
			if (sy > LIST_TOP - 10) {
				break;
			}
			g.drawCenteredString(font, line, width / 2, sy, IronManGui.TEXT_DIM);
			sy += 10;
		}
		float secs = (Util.getMillis() - openedAt) / 1000f;

		if (view == View.CHOICE) {
			renderWorn(g, px, LIST_TOP, pw, secs);
			int hy = LIST_TOP + ROW_H + 36;
			if (menu.entries().isEmpty()) {
				for (FormattedCharSequence line : font.split(Component.translatable("screen.projecthero.gantry.no_swap"), pw - 20)) {
					g.drawCenteredString(font, line, width / 2, hy, IronManGui.ORANGE);
					hy += 10;
				}
			}
			g.drawCenteredString(font, IronManGui.fit(font, Component.translatable("screen.projecthero.gantry.choice_hint").getString(), pw),
					width / 2, hy + 2, IronManGui.TEXT_MUTED);
			return;
		}
		List<StarkGantryMenuPayload.Entry> entries = menu.entries();
		if (entries.isEmpty()) {
			int y = LIST_TOP + 20;
			for (FormattedCharSequence line : font.split(Component.translatable("screen.projecthero.gantry.none"), pw - 20)) {
				g.drawCenteredString(font, line, width / 2, y, IronManGui.TEXT_MUTED);
				y += 10;
			}
			return;
		}
		hovered = -1;
		g.enableScissor(0, LIST_TOP, width, listBottom());
		for (int i = 0; i < entries.size(); i++) {
			int y = LIST_TOP + i * (ROW_H + ROW_GAP) - scroll;
			if (y + ROW_H < LIST_TOP || y > listBottom()) {
				continue;
			}
			boolean hot = mouseX >= px && mouseX < px + pw && mouseY >= y && mouseY < y + ROW_H && mouseY >= LIST_TOP
					&& mouseY < listBottom();
			if (hot) {
				hovered = i;
			}
			renderEntry(g, entries.get(i), px, y, pw, hot || i == focused, secs + i * 0.7f);
		}
		g.disableScissor();
		int max = maxScroll();
		if (max > 0) {
			int gx = px + pw + 3;
			int view = listBottom() - LIST_TOP;
			g.fill(gx, LIST_TOP, gx + 2, listBottom(), 0x80202A36);
			int thumb = Math.max(12, view * view / (view + max));
			int ty = LIST_TOP + (view - thumb) * scroll / max;
			g.fill(gx, ty, gx + 2, ty + thumb, IronManGui.CYAN_DIM);
		}
	}

	private void renderEntry(GuiGraphics g, StarkGantryMenuPayload.Entry e, int x, int y, int w, boolean hot, float secs) {
		IronManGui.panel(g, x, y, w, ROW_H, hot ? 0xF0102030 : IronManGui.PANEL_BG, hot ? IronManGui.CYAN : IronManGui.PANEL_BORDER,
				hot ? IronManGui.CYAN : IronManGui.CYAN_DIM);
		ItemStack[] pieces = IronManGui.suitPieces(e.suitId());
		for (int i = 0; i < 4; i++) {
			if ((e.mask() & (1 << i)) == 0) {
				pieces[i] = ItemStack.EMPTY;
			}
		}
		IronManGui.well(g, x + 3, y + 3, 30, ROW_H - 6);
		IronManGui.suitPreview(g, x + 4, y + 4, x + 32, y + ROW_H - 4, pieces, IronManGui.turntable(secs, 0f));

		IronManSuit suit = IronManSuits.byId(e.suitId());
		int ix = x + 38;
		int iw = w - 44;
		Component name = suit != null ? Component.translatable(suit.nameKey()) : Component.literal(e.suitId());
		g.drawString(font, IronManGui.fit(font, name.copy().withStyle(s -> s.withBold(true)).getString(), iw / 2),
				ix, y + 4, hot ? 0xFFFFFFFF : IronManGui.TEXT, false);
		int count = Integer.bitCount(e.mask() & 15);
		String where = e.pack() ? Component.translatable("screen.projecthero.gantry.where_pack", count).getString()
				: Component.translatable("screen.projecthero.gantry.where", count, e.distance()).getString();
		where = IronManGui.fit(font, where, iw / 2 - 4);
		g.drawString(font, where, ix + iw - font.width(where), y + 4, e.pack() ? IronManGui.GOLD : IronManGui.CYAN, false);

		int half = (iw - 6) / 2;
		float cap = suit == null ? 0f : suit.energyCapacity();
		float maxI = IronManEnergy.maxIntegrity(e.suitId());
		bar(g, ix, y + 17, half, Component.translatable("screen.projecthero.suit_call.charge").getString(), e.energyFrac(), cap,
				e.energyFrac() < 0.15f ? IronManGui.ORANGE : e.energyFrac() < 0.5f ? IronManGui.GOLD : IronManGui.BLUE);
		bar(g, ix + half + 6, y + 17, half, Component.translatable("screen.projecthero.suit_call.integrity").getString(),
				e.integrityFrac(), maxI, e.integrityFrac() < 0.3f ? IronManGui.RED : IronManGui.GREEN);
	}

	/** Label + percentage, then a Slab bar with the exact amount inside. */
	private void bar(GuiGraphics g, int x, int y, int w, String label, float frac, float max, int fill) {
		String pct = IronManUiLayout.pct(frac);
		g.drawString(font, IronManGui.fit(font, label, w - font.width(pct) - 4), x, y, IronManGui.TEXT_DIM, false);
		g.drawString(font, pct, x + w - font.width(pct), y, IronManGui.TEXT, false);
		IronManGui.slab(g, font, x + 1, y + 11, w - 2, frac, fill, max <= 0 ? "" : IronManUiLayout.exact(frac * max, max));
	}

	private void renderWorn(GuiGraphics g, int x, int y, int w, float secs) {
		IronManGui.panel(g, x, y, w, ROW_H, IronManGui.PANEL_BG, IronManGui.PANEL_BORDER, IronManGui.CYAN_DIM);
		IronManGui.well(g, x + 3, y + 3, 30, ROW_H - 6);
		ItemStack[] pieces = minecraft == null || minecraft.player == null ? IronManGui.suitPieces(menu.wornSuit())
				: new ItemStack[] { minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
						minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
						minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS),
						minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET) };
		IronManGui.suitPreview(g, x + 4, y + 4, x + 32, y + ROW_H - 4, pieces, IronManGui.turntable(secs, 0f));
		IronManSuit suit = IronManSuits.byId(menu.wornSuit());
		int ix = x + 38;
		int iw = w - 44;
		Component name = suit != null ? Component.translatable(suit.nameKey()) : Component.literal(menu.wornSuit());
		g.drawString(font, IronManGui.fit(font, name.copy().withStyle(s -> s.withBold(true)).getString(), iw), ix, y + 5,
				IronManGui.TEXT, false);
		Component dest = menu.canRemove()
				? Component.translatable("screen.projecthero.gantry.remove_to", menu.removeDistance())
				: Component.translatable("screen.projecthero.gantry.no_room");
		int ty = y + 17;
		for (FormattedCharSequence line : font.split(dest, iw)) {
			if (ty > y + ROW_H - 10) {
				break;
			}
			g.drawString(font, line, ix, ty, menu.canRemove() ? IronManGui.CYAN : IronManGui.ORANGE, false);
			ty += 10;
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && listing() && hovered >= 0 && hovered < menu.entries().size()) {
			pick(menu.entries().get(hovered));
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(dy * 20)));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (view == View.SWAP && (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_BACKSPACE)) {
			backToChoice();
			return true;
		}
		if (view == View.CHOICE) {
			if (key == GLFW.GLFW_KEY_R && removeButton != null && removeButton.active) {
				remove();
				return true;
			}
			if (key == GLFW.GLFW_KEY_S && swapButton != null && swapButton.active) {
				openSwap();
				return true;
			}
			if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) && removeButton != null && removeButton.active
					&& swapButton.active) {
				setFocused(key == GLFW.GLFW_KEY_LEFT ? removeButton : swapButton);
				return true;
			}
			// Tab / Enter / Space on the focused button are handled by the vanilla widget focus
			return super.keyPressed(key, scan, mods);
		}
		if (!menu.entries().isEmpty()) {
			int n = menu.entries().size();
			if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
				focused = Math.max(0, Math.min(n - 1, focused + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
				int top = focused * (ROW_H + ROW_GAP);
				int view = listBottom() - LIST_TOP;
				if (top < scroll) {
					scroll = top;
				} else if (top + ROW_H > scroll + view) {
					scroll = Math.min(maxScroll(), top + ROW_H - view);
				}
				return true;
			}
			if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
				pick(menu.entries().get(focused));
				return true;
			}
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
