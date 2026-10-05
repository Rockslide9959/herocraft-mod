package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;
import com.projecthero.mod.network.IronManCallSuitPayload;
import com.projecthero.mod.network.IronManSuitListPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

/**
 * The "call armour" picker (spec "changes 9"; v0.14.21 redesign): a scrollable grid of suit cards. Each card shows
 * a slowly turning 3D preview of that mark, its name, CHARGE and INTEGRITY Slab bars with the exact values, and
 * where it is (in your pack / on a platform N m away / on its way). Suits you have built that the server did not
 * list (out of reach in this dimension) are shown greyed out and cannot be picked.
 *
 * <p>Mouse: click a card, wheel scrolls. Keyboard: arrows move, Enter / Space calls, Esc cancels. Picking sends
 * the same {@link IronManCallSuitPayload} as before. Layout numbers: {@link IronManUiLayout} (gametested to fit
 * 320 x 240).
 */
public final class IronManSuitCallScreen extends Screen {
	private record Card(String suitId, int source, float energyFrac, float integrityFrac, int distance, boolean available) {
	}

	private final List<IronManSuitListPayload.Option> options;
	private final List<Card> cards = new ArrayList<>();
	private int scroll;
	private int focused = -1;
	private int hovered = -1;
	private final long openedAt = Util.getMillis();

	public IronManSuitCallScreen(List<IronManSuitListPayload.Option> options) {
		super(Component.translatable("screen.projecthero.suit_call.title"));
		this.options = options;
	}

	@Override
	protected void init() {
		cards.clear();
		Set<String> listed = new HashSet<>();
		for (IronManSuitListPayload.Option o : options) {
			cards.add(new Card(o.suitId(), o.source(), o.energyFrac(), o.integrityFrac(), o.distance(), true));
			listed.add(o.suitId());
		}
		// built suits the server could not reach: shown, greyed, unpickable
		TonyStarkState state = minecraft != null && minecraft.player != null
				? minecraft.player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null) : null;
		if (state != null) {
			for (String id : state.builtSuits) {
				IronManSuit suit = IronManSuits.byId(id);
				if (suit == null || listed.contains(id)) {
					continue;
				}
				float cap = Math.max(1f, suit.energyCapacity());
				float e = state.suitEnergy.containsKey(id) ? state.suitEnergy.get(id) / cap : -1f;
				float maxI = IronManEnergy.maxIntegrity(id);
				float i = state.suitIntegrity.containsKey(id) ? state.suitIntegrity.get(id) / maxI : -1f;
				cards.add(new Card(id, -1, e, i, 0, false));
			}
		}
		cards.sort((a, b) -> {
			if (a.available != b.available) {
				return a.available ? -1 : 1;
			}
			return Integer.compare(mark(a.suitId), mark(b.suitId));
		});
		if (focused < 0 || focused >= cards.size() || !cards.get(focused).available) {
			focused = cards.isEmpty() || !cards.get(0).available ? -1 : 0;
		}
		scroll = Math.max(0, Math.min(scroll, IronManUiLayout.gridMaxScroll(cards.size(), width, height)));

		int bw = Math.min(120, width - 2 * IronManUiLayout.GRID_MARGIN);
		addRenderableWidget(new IronManGui.StarkButton(width / 2 - bw / 2, height - 22, bw, 16,
				Component.translatable("gui.cancel"), this::onClose));
	}

	private static int mark(String suitId) {
		IronManSuit s = IronManSuits.byId(suitId);
		return s == null ? 99 : s.markNumber();
	}

	private void pick(Card c) {
		if (!c.available) {
			return;
		}
		ClientPlayNetworking.send(new IronManCallSuitPayload(c.suitId, c.source));
		onClose();
	}

	private int viewTop() {
		return IronManUiLayout.GRID_TOP;
	}

	private int viewBottom() {
		return IronManUiLayout.GRID_TOP + IronManUiLayout.gridViewportHeight(height);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(this.font, this.title.copy().withStyle(s -> s.withBold(true)), this.width / 2, 8, IronManGui.CYAN);
		int lineW = Math.min(IronManUiLayout.gridWidth(IronManUiLayout.gridColumns(width)), width - 12);
		g.fill(width / 2 - lineW / 2, 19, width / 2 + lineW / 2, 20, IronManGui.alpha(IronManGui.CYAN_DIM, 0.6f));

		if (cards.isEmpty()) {
			Component none = Component.translatable("screen.projecthero.suit_call.none");
			int y = height / 2 - 10;
			for (var line : this.font.split(none, Math.min(300, width - 20))) {
				g.drawCenteredString(this.font, line, width / 2, y, IronManGui.TEXT_DIM);
				y += 10;
			}
			return;
		}

		hovered = -1;
		float secs = (Util.getMillis() - openedAt) / 1000f;
		g.enableScissor(0, viewTop(), width, viewBottom());
		for (int i = 0; i < cards.size(); i++) {
			Rect r = IronManUiLayout.card(i, width, scroll);
			if (r.bottom() < viewTop() || r.y() > viewBottom()) {
				continue;
			}
			boolean hot = r.contains(mouseX, mouseY) && mouseY >= viewTop() && mouseY < viewBottom();
			if (hot) {
				hovered = i;
			}
			renderCard(g, cards.get(i), r, hot || i == focused, secs + i * 0.7f);
		}
		g.disableScissor();

		int max = IronManUiLayout.gridMaxScroll(cards.size(), width, height);
		if (max > 0) {
			int gx = IronManUiLayout.card(0, width, 0).x() + IronManUiLayout.gridWidth(IronManUiLayout.gridColumns(width)) + 2;
			gx = Math.min(gx, width - 4);
			int top = viewTop();
			int view = viewBottom() - top;
			g.fill(gx, top, gx + 2, top + view, 0x80202A36);
			int thumb = Math.max(12, view * view / (view + max));
			int ty = top + (view - thumb) * scroll / max;
			g.fill(gx, ty, gx + 2, ty + thumb, IronManGui.CYAN_DIM);
		}
		String hint = IronManGui.fit(font, Component.translatable("screen.projecthero.suit_call.hint"), width - 12);
		g.drawCenteredString(this.font, hint, width / 2, height - 35, IronManGui.TEXT_MUTED);
	}

	private void renderCard(GuiGraphics g, Card c, Rect r, boolean hot, float secs) {
		IronManSuit suit = IronManSuits.byId(c.suitId);
		int x = r.x();
		int y = r.y();
		int bg = c.available ? (hot ? 0xF0102030 : IronManGui.PANEL_BG) : 0xC0080A0E;
		int border = !c.available ? 0xFF22282F : hot ? IronManGui.CYAN : IronManGui.PANEL_BORDER;
		IronManGui.panel(g, x, y, r.w(), r.h(), bg, border, c.available ? (hot ? IronManGui.CYAN : IronManGui.CYAN_DIM) : 0xFF2A3038);

		// 3D preview
		int px0 = x + 3;
		int py0 = y + 3;
		int px1 = px0 + IronManUiLayout.CARD_PREVIEW_W;
		int py1 = y + r.h() - 3;
		IronManGui.well(g, px0, py0, px1 - px0, py1 - py0);
		IronManGui.suitPreview(g, px0 + 1, py0 + 1, px1 - 1, py1 - 1, IronManGui.suitPieces(c.suitId),
				IronManGui.turntable(secs, 0f));
		if (!c.available) {
			g.fill(px0 + 1, py0 + 1, px1 - 1, py1 - 1, 0x90080A0E);
		}

		int ix = x + IronManUiLayout.CARD_INFO_X;
		int iw = IronManUiLayout.CARD_INFO_W;
		Component name = suit != null ? Component.translatable(suit.nameKey()) : Component.literal(c.suitId);
		g.drawString(font, IronManGui.fit(font, name.copy().withStyle(s -> s.withBold(true)).getString(), iw),
				ix, y + 4, c.available ? (hot ? 0xFFFFFFFF : IronManGui.TEXT) : IronManGui.TEXT_MUTED, false);

		float cap = suit == null ? 0f : suit.energyCapacity();
		float maxI = IronManEnergy.maxIntegrity(c.suitId);
		int labelCol = c.available ? IronManGui.TEXT_DIM : IronManGui.TEXT_MUTED;
		row(g, ix, y + 15, iw, Component.translatable("screen.projecthero.suit_call.charge").getString(), c.energyFrac, cap,
				c.energyFrac < 0.15f ? IronManGui.ORANGE : c.energyFrac < 0.5f ? IronManGui.GOLD : IronManGui.BLUE,
				labelCol, c.available);
		row(g, ix, y + 37, iw, Component.translatable("screen.projecthero.suit_call.integrity").getString(), c.integrityFrac, maxI,
				c.integrityFrac < 0.3f ? IronManGui.RED : IronManGui.GREEN, labelCol, c.available);

		String where = c.available
				? Component.translatable(IronManUiLayout.locationKey(c.source), c.distance).getString()
				: Component.translatable("screen.projecthero.suit_call.unreachable").getString();
		int whereCol = !c.available ? IronManGui.TEXT_MUTED
				: c.source == IronManSuitListPayload.SOURCE_INVENTORY ? IronManGui.GREEN
				: c.source == IronManSuitListPayload.SOURCE_SEND_BACK ? IronManGui.GOLD : IronManGui.CYAN;
		g.fill(ix, y + 61, ix + 3, y + 64, whereCol);
		g.drawString(font, IronManGui.fit(font, where, iw - 6), ix + 6, y + 59, whereCol, false);
	}

	/** Label + percentage line, then a Slab bar with the exact amount inside. A negative fraction = unknown. */
	private void row(GuiGraphics g, int x, int y, int w, String label, float frac, float max, int fill, int labelCol,
			boolean available) {
		g.drawString(font, label, x, y, labelCol, false);
		String pct = frac < 0 ? "--" : IronManUiLayout.pct(frac);
		g.drawString(font, pct, x + w - font.width(pct), y, available ? IronManGui.TEXT : IronManGui.TEXT_MUTED, false);
		float f = Math.max(0f, frac);
		IronManGui.slab(g, font, x + 1, y + 10, w - 2, f, available ? fill : 0xFF3A4048,
				frac < 0 || max <= 0 ? "" : IronManUiLayout.exact(f * max, max));
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0 && hovered >= 0 && hovered < cards.size()) {
			pick(cards.get(hovered));
			return true;
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		int max = IronManUiLayout.gridMaxScroll(cards.size(), width, height);
		scroll = Math.max(0, Math.min(max, scroll - (int) Math.round(dy * 20)));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (!cards.isEmpty()) {
			int cols = IronManUiLayout.gridColumns(width);
			int step = switch (key) {
				case GLFW.GLFW_KEY_RIGHT -> 1;
				case GLFW.GLFW_KEY_LEFT -> -1;
				case GLFW.GLFW_KEY_DOWN -> cols;
				case GLFW.GLFW_KEY_UP -> -cols;
				default -> 0;
			};
			if (step != 0) {
				int avail = 0;
				for (Card c : cards) {
					avail += c.available ? 1 : 0;
				}
				if (avail > 0) {
					int next = focused < 0 ? 0 : Math.max(0, Math.min(avail - 1, focused + step));
					focused = next; // available cards are sorted first, so 0..avail-1 are all pickable
					scroll = IronManUiLayout.scrollToShow(focused, width, height, scroll, cards.size());
				}
				return true;
			}
			if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE)
					&& focused >= 0 && focused < cards.size()) {
				pick(cards.get(focused));
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
