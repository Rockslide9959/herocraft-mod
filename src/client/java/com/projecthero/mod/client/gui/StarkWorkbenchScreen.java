package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.render.workbench.WorkbenchHolo;
import com.projecthero.mod.client.render.workbench.WorkbenchHoloRenderType;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.fabricator.FabricationRecipe;
import com.projecthero.mod.ironman.fabricator.StarkFabricatorMenu;
import com.projecthero.mod.ironman.fabricator.StarkWorkbench;
import com.projecthero.mod.ironman.item.BlueprintItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;
import com.projecthero.mod.ironman.ui.IronManUiLayout.Rect;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.7 (user): the Stark Workbench screen -- a new front end for the unchanged Fabricator logic (recipes, machine
 * energy, progress, the blueprint and nine input slots), laid out like the approved mockup (geometry in
 * {@link IronManUiLayout}, 320 x 236 so it fits a 320 x 240 GUI):
 * <pre>
 *   STARK WORKBENCH                                [ERA I][ERA II][ERA III][ERA IV]   (II-IV locked, "coming soon")
 *   suit list           | rotating hologram preview      | stat bars: Armour / Integrity / Reactor / Flight
 *   (owned / buildable  | (drag to spin; the same mesh    | "Helmet needs:"                    [View more]
 *    / locked)          |  as the in-world hologram)     | recipe slots: icon + have/need (missing in red)
 *                       | [helm][chest][legs][boots]     | [Build] =====progress=====
 *   player inventory                                     | blueprint  3x3 inputs  -> output   energy
 *                                                        | status / energy
 * </pre>
 * Browsing the list and the piece toggles is local to the screen; Build sends the suit + piece to the server
 * ({@link StarkWorkbench#build}), which loads the blueprint and the components from the inventory into the machine.
 * "have" counts what the grid + your inventory hold, i.e. what Build can load. The old screen's "View more" panel (every
 * component, cost, the suit's stats and moves) is kept, and the machine slots stay usable by hand. A suit outside Era I
 * that sits in the blueprint slot (an older save) still shows and builds here, so nothing is stranded.
 */
public class StarkWorkbenchScreen extends AbstractContainerScreen<StarkFabricatorMenu> {
	private static final String[] PIECE_KEYS = { "helmet", "chestplate", "leggings", "boots" };
	private static final ArmorItem.Type[] PIECE_TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
			ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final String[] ERA_NUM = { "I", "II", "III", "IV" };
	private static final String[] STAT_KEYS = { "armour", "integrity", "reactor", "flight" };
	private static final int[] STAT_COLORS = { 0xFF7FE9FF, 0xFFE05A4A, 0xFF58C8FF, 0xFFFFC24A };

	private static final int BG = 0xF2070C12;
	private static final int SECTION = 0xC00A141E;
	private static final int LINE = 0xFF1F5A70;
	private static final int MISSING = 0xFFFF6A6A;

	private int era = 1;
	private String browseSuit;
	private int browsePiece;
	private int listScroll;
	private float yaw = 200f;
	private boolean dragging;
	private long lastDragMs;
	private long lastFrameMs;
	private boolean showInfo;
	private int infoScroll;
	private int infoScrollMax;
	private List<Component> pendingTip;
	private final boolean[] holoBuilt = new boolean[4];

	public StarkWorkbenchScreen(StarkFabricatorMenu menu, Inventory inv, Component title) {
		super(menu, inv, title);
		this.imageWidth = IronManUiLayout.FAB_W;
		this.imageHeight = IronManUiLayout.FAB_H;
	}

	// ------------------------------------------------------------------ state

	private TonyStarkState stark() {
		return StarkWorkbench.state(minecraft != null ? minecraft.player : null);
	}

	private String jobSuit() {
		return menu.blueprintStack().getItem() instanceof BlueprintItem bp ? bp.suitId() : null;
	}

	/** The suits the current era tab lists (plus an off-era suit that is in the blueprint slot, so it is never stranded). */
	private List<String> listed() {
		List<String> out = StarkWorkbench.eraSuits(era);
		String job = jobSuit();
		if (job != null && !out.contains(job) && IronManSuits.byId(job) != null) {
			out.add(job);
		}
		return out;
	}

	private void ensureBrowse() {
		if (browseSuit != null) {
			return;
		}
		List<String> list = listed();
		String job = jobSuit();
		TonyStarkState s = stark();
		if (job != null && list.contains(job)) {
			browseSuit = job;
			browsePiece = menu.selectedPiece() >= 1 ? menu.selectedPiece() : StarkWorkbench.firstUnbuiltPiece(s, job);
			return;
		}
		for (String id : list) {
			if (StarkWorkbench.status(s, id) == StarkWorkbench.Status.BUILDABLE) {
				browseSuit = id;
				break;
			}
		}
		if (browseSuit == null && !list.isEmpty()) {
			browseSuit = list.get(0);
		}
		browsePiece = StarkWorkbench.firstUnbuiltPiece(s, browseSuit);
	}

	private FabricationRecipe recipe() {
		return StarkWorkbench.recipe(browseSuit, browsePiece);
	}

	private boolean browsingJob() {
		return browseSuit != null && browseSuit.equals(jobSuit());
	}

	/** Grid + inventory: what Build could load. */
	private int have(Item item) {
		int n = 0;
		for (int i = 0; i < 9; i++) {
			ItemStack s = menu.slots.get(i).getItem();
			if (s.is(item)) {
				n += s.getCount();
			}
		}
		return n + StarkWorkbench.countInInventory(playerInventoryOf(), item);
	}

	private Inventory playerInventoryOf() {
		return minecraft.player.getInventory();
	}

	private boolean missingAny(FabricationRecipe recipe) {
		for (FabricationRecipe.Input in : recipe.inputs()) {
			if (have(in.item()) < in.count()) {
				return true;
			}
		}
		return false;
	}

	private float progressFrac() {
		return menu.maxProgress() > 0 ? IronManUiLayout.clamp01(menu.progress() / (float) menu.maxProgress()) : 0f;
	}

	private static boolean in(Rect r, double mx, double my, int ox, int oy) {
		return r.contains(mx - ox, my - oy);
	}

	private Component pieceName(int piece) {
		return Component.translatable("screen.projecthero.stark_fabricator.piece." + PIECE_KEYS[piece - 1]);
	}

	private Component suitName(String id) {
		IronManSuit suit = IronManSuits.byId(id);
		return suit != null ? Component.translatable(suit.nameKey()) : Component.literal(String.valueOf(id));
	}

	// ------------------------------------------------------------------ background

	@Override
	protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
		ensureBrowse();
		int x = leftPos;
		int y = topPos;
		IronManGui.panel(g, x, y, imageWidth, imageHeight, BG, LINE, IronManGui.CYAN);
		g.fill(x + 4, y + 19, x + imageWidth - 4, y + 20, IronManGui.alpha(IronManGui.CYAN_DIM, 0.8f));
		// column sections + the machine area
		section(g, x + IronManUiLayout.FAB_LIST_X, y + IronManUiLayout.FAB_LIST_Y, IronManUiLayout.FAB_LIST_W, IronManUiLayout.FAB_LIST_H);
		section(g, x + IronManUiLayout.FAB_HOLO_X, y + IronManUiLayout.FAB_HOLO_Y, IronManUiLayout.FAB_HOLO_W, IronManUiLayout.FAB_HOLO_H);
		section(g, x + IronManUiLayout.FAB_RIGHT_X, y + IronManUiLayout.FAB_STATS_Y, IronManUiLayout.FAB_RIGHT_W, 43);
		section(g, x + IronManUiLayout.FAB_BOX_X, y + IronManUiLayout.FAB_BOX_Y, IronManUiLayout.FAB_BOX_W, IronManUiLayout.FAB_BOX_H);
		g.fill(x + 4, y + 149, x + imageWidth - 4, y + 150, IronManGui.alpha(IronManGui.CYAN_DIM, 0.5f));
		for (int i = 0; i < 36; i++) {
			var sl = menu.slots.get(11 + i);
			IronManGui.well(g, x + sl.x - 1, y + sl.y - 1, 18, 18);
		}
		for (int i = 0; i < 11; i++) {
			var sl = menu.slots.get(i);
			IronManGui.well(g, x + sl.x - 1, y + sl.y - 1, 18, 18);
			g.renderOutline(x + sl.x - 1, y + sl.y - 1, 18, 18, i >= 9 ? IronManGui.alpha(IronManGui.GOLD, 0.6f) : LINE);
		}
		renderEraTabs(g, x, y, mouseX, mouseY);
		renderList(g, x, y, mouseX, mouseY);
		renderHologram(g, x, y);
		renderToggles(g, x, y, mouseX, mouseY);
		renderStats(g, x, y, mouseX, mouseY);
		renderRecipe(g, x, y, mouseX, mouseY);
		renderBuild(g, x, y, mouseX, mouseY);
		renderMachine(g, x, y, mouseX, mouseY);
	}

	private static void section(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, SECTION);
		g.renderOutline(x, y, w, h, LINE);
		IronManGui.brackets(g, x, y, w, h, 4, IronManGui.CYAN_DIM);
	}

	private void renderEraTabs(GuiGraphics g, int x, int y, int mx, int my) {
		for (int i = 0; i < StarkWorkbench.ERA_COUNT; i++) {
			Rect r = IronManUiLayout.fabEraTab(i);
			boolean live = i < StarkWorkbench.LIVE_ERAS;
			boolean sel = era == i + 1;
			boolean hover = live && in(r, mx, my, x, y);
			g.fill(x + r.x(), y + r.y(), x + r.right(), y + r.bottom(), sel ? 0xF0123040 : hover ? 0xF0102230 : 0xD0080E14);
			g.renderOutline(x + r.x(), y + r.y(), r.w(), r.h(), sel ? IronManGui.CYAN : live ? LINE : 0xFF1A222C);
			String label = Component.translatable("screen.projecthero.stark_workbench.era", ERA_NUM[i]).getString();
			int col = sel ? IronManGui.CYAN : live ? IronManGui.TEXT : IronManGui.TEXT_MUTED;
			int lockW = live ? 0 : 7;
			String t = IronManGui.fit(font, label, r.w() - 4 - lockW);
			int tx = x + r.x() + (r.w() - font.width(t) - lockW) / 2;
			g.drawString(font, t, tx, y + r.y() + 3, col, false);
			if (!live) {
				lockIcon(g, tx + font.width(t) + 2, y + r.y() + 3, IronManGui.TEXT_MUTED);
			}
		}
		String title = IronManGui.fit(font, Component.translatable("screen.projecthero.stark_workbench.title").getString()
				.toUpperCase(Locale.ROOT), IronManUiLayout.FAB_ERA_X - 12);
		g.drawString(font, title, x + 8, y + 6, IronManGui.CYAN, false);
	}

	/** A 5 x 7 padlock. */
	private static void lockIcon(GuiGraphics g, int x, int y, int c) {
		g.fill(x + 1, y, x + 4, y + 1, c);
		g.fill(x, y + 1, x + 1, y + 3, c);
		g.fill(x + 4, y + 1, x + 5, y + 3, c);
		g.fill(x, y + 3, x + 5, y + 7, c);
		g.fill(x + 2, y + 4, x + 3, y + 6, 0xFF070C12);
	}

	/** A 7 x 6 check mark. */
	private static void checkIcon(GuiGraphics g, int x, int y, int c) {
		g.fill(x, y + 3, x + 1, y + 4, c);
		g.fill(x + 1, y + 4, x + 2, y + 5, c);
		g.fill(x + 2, y + 5, x + 3, y + 6, c);
		g.fill(x + 3, y + 4, x + 4, y + 5, c);
		g.fill(x + 4, y + 3, x + 5, y + 4, c);
		g.fill(x + 5, y + 2, x + 6, y + 3, c);
		g.fill(x + 6, y + 1, x + 7, y + 2, c);
	}

	/** A 7 x 7 hammer (buildable). */
	private static void hammerIcon(GuiGraphics g, int x, int y, int c) {
		g.fill(x, y, x + 6, y + 2, c);
		g.fill(x + 2, y + 2, x + 4, y + 7, c);
	}

	private void renderList(GuiGraphics g, int x, int y, int mx, int my) {
		String head = Component.translatable("screen.projecthero.stark_workbench.suits").getString().toUpperCase(Locale.ROOT);
		g.drawString(font, IronManGui.fit(font, head, IronManUiLayout.FAB_LIST_W - 8), x + IronManUiLayout.FAB_LIST_X + 4,
				y + IronManUiLayout.FAB_LIST_Y + 3, IronManGui.CYAN_DIM, false);
		if (era > StarkWorkbench.LIVE_ERAS) {
			return;
		}
		List<String> list = listed();
		TonyStarkState s = stark();
		int visible = (IronManUiLayout.FAB_LIST_H - 13) / IronManUiLayout.FAB_LIST_ROW_H;
		listScroll = Math.max(0, Math.min(listScroll, Math.max(0, list.size() - visible)));
		for (int row = 0; row < visible && row + listScroll < list.size(); row++) {
			String id = list.get(row + listScroll);
			Rect r = IronManUiLayout.fabListRow(row);
			boolean sel = id.equals(browseSuit);
			boolean hover = in(r, mx, my, x, y);
			StarkWorkbench.Status st = StarkWorkbench.status(s, id);
			int rx = x + r.x();
			int ry = y + r.y();
			g.fill(rx, ry, rx + r.w(), ry + r.h(), sel ? 0xE0123446 : hover ? 0xC0102434 : 0x60081018);
			if (sel) {
				g.fill(rx, ry, rx + 2, ry + r.h(), IronManGui.CYAN);
			}
			int iconCol = switch (st) {
				case OWNED -> IronManGui.GREEN;
				case BUILDABLE -> IronManGui.GOLD;
				default -> IronManGui.TEXT_MUTED;
			};
			switch (st) {
				case OWNED -> checkIcon(g, rx + 4, ry + 6, iconCol);
				case BUILDABLE -> hammerIcon(g, rx + 4, ry + 6, iconCol);
				default -> lockIcon(g, rx + 5, ry + 6, iconCol);
			}
			int textCol = st == StarkWorkbench.Status.LOCKED ? IronManGui.TEXT_MUTED : sel ? 0xFFFFFFFF : IronManGui.TEXT;
			List<FormattedCharSequence> lines = font.split(suitName(id), r.w() - 16);
			int n = Math.min(2, lines.size());
			int ty = ry + (r.h() - n * 9) / 2 + 1;
			for (int i = 0; i < n; i++) {
				FormattedCharSequence line = lines.get(i);
				if (i == 1 && lines.size() > 2) {
					line = FormattedCharSequence.forward(IronManGui.fit(font, toString(lines.get(1)) + " " + toString(lines.get(2)),
							r.w() - 16), net.minecraft.network.chat.Style.EMPTY);
				}
				g.drawString(font, line, rx + 14, ty + i * 9, textCol, false);
			}
		}
		if (list.size() > visible) {
			int top = y + IronManUiLayout.FAB_LIST_ROW_Y;
			int h = visible * IronManUiLayout.FAB_LIST_ROW_H;
			int tx = x + IronManUiLayout.FAB_LIST_X + IronManUiLayout.FAB_LIST_W - 3;
			g.fill(tx, top, tx + 1, top + h, 0xFF14202C);
			int th = Math.max(8, h * visible / list.size());
			int ty = top + (h - th) * listScroll / Math.max(1, list.size() - visible);
			g.fill(tx, ty, tx + 1, ty + th, IronManGui.CYAN_DIM);
		}
	}

	private static String toString(FormattedCharSequence seq) {
		StringBuilder sb = new StringBuilder();
		seq.accept((idx, style, cp) -> {
			sb.appendCodePoint(cp);
			return true;
		});
		return sb.toString();
	}

	/** The hologram preview: the same mesh as the in-world hologram, additive cyan, turning slowly (drag to spin). */
	private void renderHologram(GuiGraphics g, int x, int y) {
		int x0 = x + IronManUiLayout.FAB_HOLO_X;
		int y0 = y + IronManUiLayout.FAB_HOLO_Y;
		int w = IronManUiLayout.FAB_HOLO_W;
		int h = IronManUiLayout.FAB_HOLO_H;
		// scan-line backdrop + a projector disc
		for (int ly = y0 + 2; ly < y0 + h - 1; ly += 3) {
			g.fill(x0 + 1, ly, x0 + w - 1, ly + 1, 0x14_37C6E8);
		}
		g.fill(x0 + w / 2 - 16, y0 + h - 8, x0 + w / 2 + 16, y0 + h - 6, IronManGui.alpha(IronManGui.CYAN_DIM, 0.8f));
		g.fill(x0 + w / 2 - 10, y0 + h - 9, x0 + w / 2 + 10, y0 + h - 8, IronManGui.CYAN);
		if (era > StarkWorkbench.LIVE_ERAS) {
			Component soon = Component.translatable("screen.projecthero.stark_workbench.coming_soon");
			List<FormattedCharSequence> lines = font.split(soon, w - 12);
			int ty = y0 + h / 2 - lines.size() * 5;
			for (FormattedCharSequence l : lines) {
				g.drawString(font, l, x0 + (w - font.width(l)) / 2, ty, IronManGui.TEXT_DIM, false);
				ty += 10;
			}
			return;
		}
		long now = Util.getMillis();
		if (!dragging && now - lastDragMs > 1500 && lastFrameMs > 0) {
			yaw += Math.min(100, now - lastFrameMs) * 0.03f; // a slow turntable (30 degrees a second)
		}
		lastFrameMs = now;
		TonyStarkState s = stark();
		for (int p = 0; p < 4; p++) {
			holoBuilt[p] = StarkWorkbench.pieceBuilt(s, browseSuit, p + 1);
		}
		int building = browsingJob() && menu.maxProgress() > 0 && menu.selectedPiece() >= 1 ? menu.selectedPiece() - 1 : -1;
		float sweep = (Util.getMillis() / 40f) % 44f - 6f;

		g.flush();
		g.enableScissor(x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1);
		PoseStack ps = g.pose();
		ps.pushPose();
		float sc = 2.25f;
		ps.translate(x0 + w / 2f, y0 + h - 9, 150);
		ps.scale(sc, -sc, sc);
		ps.mulPose(Axis.XP.rotationDegrees(-10f));
		ps.mulPose(Axis.YP.rotationDegrees(yaw));
		VertexConsumer vc = g.bufferSource().getBuffer(WorkbenchHoloRenderType.HOLO);
		WorkbenchHolo.beam(vc, ps.last().pose(), 3f, 4.5f, 3f, 1f);
		WorkbenchHolo.ring(vc, ps.last().pose(), Util.getMillis() / 1200f, 12f, 19f, 0.9f);
		ps.translate(0, 0.6f, 0);
		WorkbenchHolo.suit(vc, ps.last().pose(), holoBuilt, building, progressFrac(), 1.25f, sweep);
		g.flush();
		ps.popPose();
		g.disableScissor();
		String hint = IronManGui.fit(font, Component.translatable("screen.projecthero.stark_workbench.drag"), w - 6);
		g.pose().pushPose();
		g.pose().translate(x0 + 3, y0 + 3, 0);
		g.pose().scale(0.75f, 0.75f, 1f);
		g.drawString(font, hint, 0, 0, IronManGui.TEXT_MUTED, false);
		g.pose().popPose();
	}

	private void renderToggles(GuiGraphics g, int x, int y, int mx, int my) {
		if (era > StarkWorkbench.LIVE_ERAS || browseSuit == null) {
			return;
		}
		IronManSuit suit = IronManSuits.byId(browseSuit);
		TonyStarkState s = stark();
		for (int i = 0; i < 4; i++) {
			Rect r = IronManUiLayout.fabTab(i);
			boolean sel = browsePiece == i + 1;
			boolean hover = in(r, mx, my, x, y);
			boolean built = StarkWorkbench.pieceBuilt(s, browseSuit, i + 1);
			int rx = x + r.x();
			int ry = y + r.y();
			g.fill(rx, ry, rx + r.w(), ry + r.h(), sel ? 0xF0183044 : hover ? 0xE0142230 : 0xD00C121A);
			g.renderOutline(rx, ry, r.w(), r.h(), sel ? IronManGui.GOLD : hover ? IronManGui.CYAN : LINE);
			if (suit != null && suit.armor(PIECE_TYPES[i]) != null) {
				g.renderFakeItem(new ItemStack(suit.armor(PIECE_TYPES[i])), rx + (r.w() - 16) / 2, ry + 2);
			}
			String label = Component.translatable(built ? "screen.projecthero.stark_workbench.built"
					: "screen.projecthero.stark_workbench.not_built").getString();
			String t = IronManGui.fit(font, label, r.w() - 2);
			g.drawString(font, t, rx + (r.w() - font.width(t)) / 2, ry + 19, built ? IronManGui.GREEN : IronManGui.TEXT_DIM, false);
		}
	}

	private void renderStats(GuiGraphics g, int x, int y, int mx, int my) {
		IronManSuit suit = IronManSuits.byId(browseSuit);
		float[] bars = suit != null && era <= StarkWorkbench.LIVE_ERAS ? StarkWorkbench.statBars(suit) : new float[4];
		int rx = x + IronManUiLayout.FAB_RIGHT_X + 4;
		int bw = IronManUiLayout.FAB_RIGHT_W - 8 - IronManUiLayout.FAB_STAT_LABEL_W;
		for (int i = 0; i < 4; i++) {
			int ry = y + IronManUiLayout.FAB_STATS_Y + 3 + i * IronManUiLayout.FAB_STAT_ROW_H - (i > 0 ? 1 : 0);
			String label = IronManGui.fit(font, Component.translatable("screen.projecthero.stark_workbench.stat." + STAT_KEYS[i]),
					IronManUiLayout.FAB_STAT_LABEL_W - 2);
			g.drawString(font, label, rx, ry, IronManGui.TEXT_DIM, false);
			int bx = rx + IronManUiLayout.FAB_STAT_LABEL_W;
			g.fill(bx, ry + 2, bx + bw, ry + 6, 0xFF0A1822);
			g.renderOutline(bx - 1, ry + 1, bw + 2, 6, LINE);
			g.fill(bx, ry + 2, bx + Math.round(bw * bars[i]), ry + 6, STAT_COLORS[i]);
			if (suit != null && mx >= bx - 1 && mx < bx + bw + 1 && my >= ry && my < ry + 9) {
				float[] raw = StarkWorkbench.rawStats(suit);
				String v = i == 3 && !suit.manualFlight()
						? Component.translatable("screen.projecthero.stark_workbench.no_flight").getString()
						: i == 3 ? String.format(Locale.ROOT, "%.0f b/s", raw[3]) : String.format(Locale.ROOT, "%.0f", raw[i]);
				pendingTip = List.of(Component.translatable("screen.projecthero.stark_workbench.stat." + STAT_KEYS[i]),
						Component.literal(v).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	private void renderRecipe(GuiGraphics g, int x, int y, int mx, int my) {
		int bx = x + IronManUiLayout.FAB_BOX_X;
		int by = y + IronManUiLayout.FAB_BOX_Y;
		int tw = IronManUiLayout.FAB_BOX_W - 8;
		if (era > StarkWorkbench.LIVE_ERAS || browseSuit == null) {
			return;
		}
		Rect more = IronManUiLayout.fabMore();
		boolean moreHover = in(more, mx, my, x, y);
		g.fill(x + more.x(), y + more.y(), x + more.right(), y + more.bottom(), moreHover ? 0xF0183044 : 0xE00C121A);
		g.renderOutline(x + more.x(), y + more.y(), more.w(), more.h(), moreHover ? IronManGui.CYAN : LINE);
		String ms = IronManGui.fit(font, Component.translatable("screen.projecthero.stark_fabricator.view_more"), more.w() - 4);
		g.pose().pushPose();
		g.pose().translate(x + more.x() + more.w() / 2f, y + more.y() + 3, 0);
		g.pose().scale(0.75f, 0.75f, 1f);
		g.drawString(font, IronManGui.fit(font, ms, (int) ((more.w() - 4) / 0.75f)), -font.width(ms) / 2, 0,
				moreHover ? 0xFFFFFFFF : IronManGui.TEXT, false);
		g.pose().popPose();

		TonyStarkState s = stark();
		StarkWorkbench.Status st = StarkWorkbench.status(s, browseSuit);
		FabricationRecipe recipe = recipe();
		if (st == StarkWorkbench.Status.LOCKED || recipe == null) {
			Component msg = st == StarkWorkbench.Status.LOCKED
					? Component.translatable("screen.projecthero.stark_workbench.locked_hint",
							suitName(IronManItems.prerequisiteSuit(browseSuit)))
					: Component.translatable("screen.projecthero.stark_workbench.table_hint");
			int ty = by + 15;
			for (FormattedCharSequence line : font.split(msg, tw)) {
				if (ty > by + IronManUiLayout.FAB_BOX_H - 9) {
					break;
				}
				g.drawString(font, line, bx + 4, ty, st == StarkWorkbench.Status.LOCKED ? IronManGui.ORANGE : IronManGui.TEXT_DIM, false);
				ty += 10;
			}
			return;
		}
		Component header = Component.translatable("screen.projecthero.stark_fabricator.needs_short", pieceName(browsePiece));
		g.drawString(font, IronManGui.fit(font, header, tw - more.w() - 2), bx + 4, by + 2, IronManGui.GOLD, false);
		List<FabricationRecipe.Input> inputs = recipe.inputs();
		int max = IronManUiLayout.FAB_CHECK_PER_ROW * 3;
		for (int i = 0; i < inputs.size() && i < max; i++) {
			FabricationRecipe.Input inp = inputs.get(i);
			Rect c = IronManUiLayout.checklistCell(i, IronManUiLayout.FAB_BOX_X + 1, IronManUiLayout.FAB_CHECK_TOP,
					IronManUiLayout.FAB_CHECK_PER_ROW, IronManUiLayout.FAB_CHECK_CELL_W, IronManUiLayout.FAB_CHECK_ROW_H);
			int have = have(inp.item());
			boolean ok = have >= inp.count();
			int cx = x + c.x();
			int cy = y + c.y();
			g.fill(cx, cy, cx + c.w() - 1, cy + c.h() - 1, ok ? 0x6010303A : 0x60401010);
			g.renderOutline(cx, cy, c.w() - 1, c.h() - 1, ok ? LINE : 0xFF7A2A2A);
			ItemStack stack = new ItemStack(inp.item());
			g.renderFakeItem(stack, cx + 1, cy);
			String t = Math.min(have, 999) + "/" + inp.count();
			g.pose().pushPose();
			g.pose().translate(cx + 17, cy + 6, 200);
			g.pose().scale(0.7f, 0.7f, 1f);
			g.drawString(font, IronManGui.fit(font, t, (int) ((c.w() - 18) / 0.7f)), 0, 0, ok ? IronManGui.GREEN : MISSING, false);
			g.pose().popPose();
			if (mx >= cx && mx < cx + c.w() - 1 && my >= cy && my < cy + c.h() - 1) {
				pendingTip = List.of(stack.getHoverName(),
						Component.translatable("screen.projecthero.stark_fabricator.have_need", have + " / " + inp.count())
								.withStyle(ok ? ChatFormatting.GRAY : ChatFormatting.RED));
			}
		}
		if (inputs.size() > max) {
			g.drawString(font, "+" + (inputs.size() - max), bx + IronManUiLayout.FAB_BOX_W - 16, by + IronManUiLayout.FAB_BOX_H - 9,
					IronManGui.TEXT_DIM, false);
		}
	}

	private boolean canBuild() {
		return era <= StarkWorkbench.LIVE_ERAS && browseSuit != null && recipe() != null
				&& StarkWorkbench.status(stark(), browseSuit) != StarkWorkbench.Status.LOCKED;
	}

	private void renderBuild(GuiGraphics g, int x, int y, int mx, int my) {
		Rect b = IronManUiLayout.fabBuild();
		boolean active = canBuild();
		boolean hover = active && in(b, mx, my, x, y);
		int bx = x + b.x();
		int by = y + b.y();
		g.fill(bx, by, bx + b.w(), by + b.h(), !active ? 0xC0101318 : hover ? 0xF0204A5E : 0xF0143444);
		g.renderOutline(bx, by, b.w(), b.h(), !active ? 0xFF262C36 : hover ? 0xFFFFFFFF : IronManGui.CYAN);
		String t = IronManGui.fit(font, Component.translatable("screen.projecthero.stark_workbench.build").getString()
				.toUpperCase(Locale.ROOT), b.w() - 4);
		g.drawString(font, t, bx + (b.w() - font.width(t)) / 2, by + 2, active ? (hover ? 0xFFFFFFFF : IronManGui.CYAN) : IronManGui.TEXT_MUTED, false);
		// the fabrication progress bar (the real job)
		int px = bx + b.w() + 4;
		int pw = IronManUiLayout.FAB_RIGHT_X + IronManUiLayout.FAB_RIGHT_W - b.right() - 4;
		int py = by + 4;
		g.fill(px - 1, py - 1, px + pw + 1, py + 5, LINE);
		g.fill(px, py, px + pw, py + 4, 0xFF081018);
		float pf = progressFrac();
		int fw = Math.round(pw * pf);
		g.fill(px, py, px + fw, py + 4, IronManGui.CYAN);
		if (menu.maxProgress() > 0) {
			long ms = Util.getMillis();
			int glow = (int) ((ms / 8) % (pw + 20)) - 10;
			for (int k = -5; k <= 5; k++) {
				int gx = px + glow + k;
				if (gx >= px && gx < px + fw) {
					g.fill(gx, py, gx + 1, py + 4, ((Math.round(140 * (1f - Math.abs(k) / 6f))) << 24) | 0xFFFFFF);
				}
			}
		}
		if (in(b, mx, my, x, y) && !active && browseSuit != null) {
			Component why = StarkWorkbench.status(stark(), browseSuit) == StarkWorkbench.Status.LOCKED
					? Component.translatable("screen.projecthero.stark_workbench.locked_hint", suitName(IronManItems.prerequisiteSuit(browseSuit)))
					: Component.translatable("screen.projecthero.stark_workbench.table_hint");
			pendingTip = List.of(why);
		} else if (in(b, mx, my, x, y) && active) {
			pendingTip = List.of(Component.translatable("screen.projecthero.stark_workbench.build_tip", pieceName(browsePiece),
					suitName(browseSuit)));
		}
	}

	private void renderMachine(GuiGraphics g, int x, int y, int mx, int my) {
		// arrow from the grid to the output, filling with progress
		int ax = x + 258;
		int ay = y + 178;
		g.fill(ax, ay, ax + 18, ay + 4, 0xFF0A1822);
		g.fill(ax, ay, ax + Math.round(18 * progressFrac()), ay + 4, IronManGui.CYAN);
		g.fill(ax + 18, ay - 2, ax + 19, ay + 6, LINE);
		g.fill(ax + 19, ay - 1, ax + 20, ay + 5, LINE);
		g.fill(ax + 20, ay, ax + 21, ay + 4, LINE);
		// energy gauge
		int gx = x + IronManUiLayout.FAB_GAUGE_X;
		int gt = y + IronManUiLayout.FAB_GAUGE_Y;
		int gb = gt + IronManUiLayout.FAB_GAUGE_H;
		g.fill(gx - 1, gt - 1, gx + 9, gb + 1, LINE);
		g.fill(gx, gt, gx + 8, gb, 0xFF06101A);
		float ef = IronManUiLayout.clamp01(menu.energy() / (float) Math.max(1, menu.maxEnergy()));
		int lvl = Math.round((gb - gt) * ef);
		g.fillGradient(gx, gb - lvl, gx + 8, gb, 0xFF6FC8FF, 0xFF2A6FC0);
		// empty blueprint slot ghost
		if (menu.blueprintStack().isEmpty()) {
			var sl = menu.slots.get(9);
			g.renderFakeItem(new ItemStack(IronManItems.BLANK_BLUEPRINT), x + sl.x, y + sl.y);
			g.fill(x + sl.x, y + sl.y, x + sl.x + 16, y + sl.y + 16, 0xC0070C12);
		}
		// status + energy
		int sx = x + IronManUiLayout.FAB_STATUS_X;
		int sw = IronManUiLayout.FAB_W - IronManUiLayout.FAB_STATUS_X - 6;
		String status;
		int col;
		FabricationRecipe job = StarkWorkbench.recipe(jobSuit(), menu.selectedPiece());
		if (menu.maxProgress() > 0) {
			status = Component.translatable("screen.projecthero.stark_fabricator.fabricating", Math.round(100f * progressFrac())).getString();
			col = IronManGui.GREEN;
		} else if (job != null && !jobMissing(job)) {
			boolean low = menu.energy() < job.energyCost();
			status = Component.translatable(low ? "screen.projecthero.stark_fabricator.status.low_energy"
					: "screen.projecthero.stark_fabricator.status.ready").getString();
			col = low ? IronManGui.ORANGE : IronManGui.CYAN;
		} else if (job != null) {
			status = Component.translatable("screen.projecthero.stark_fabricator.status.missing").getString();
			col = IronManGui.TEXT_DIM;
		} else {
			status = Component.translatable("screen.projecthero.stark_fabricator.status.idle").getString();
			col = IronManGui.TEXT_DIM;
		}
		g.drawString(font, IronManGui.fit(font, status, sw), sx, y + IronManUiLayout.FAB_STATUS_Y, col, false);
		String energy = Component.translatable("screen.projecthero.stark_fabricator.energy", menu.energy(), menu.maxEnergy()).getString();
		g.drawString(font, IronManGui.fit(font, energy, sw), sx, y + IronManUiLayout.FAB_STATUS_Y + 11, IronManGui.TEXT_MUTED, false);
		if (mx >= gx - 1 && mx < gx + 9 && my >= gt - 1 && my < gb + 1) {
			pendingTip = List.of(Component.translatable("screen.projecthero.stark_fabricator.energy", menu.energy(), menu.maxEnergy()));
		}
	}

	/** The job (what is in the slots) is missing something that sits in the grid -- the machine's own rule. */
	private boolean jobMissing(FabricationRecipe recipe) {
		List<ItemStack> grid = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) {
			grid.add(menu.slots.get(i).getItem());
		}
		for (FabricationRecipe.Input in : recipe.inputs()) {
			if (IronManUiLayout.have(grid, in.item()) < in.count()) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
		// everything is drawn in renderBg (panel-relative); no vanilla title / "Inventory" labels
	}

	// ------------------------------------------------------------------ render + tooltips + modal

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		pendingTip = null;
		super.render(g, mouseX, mouseY, partialTick);
		if (showInfo) {
			renderInfoOverlay(g);
			return;
		}
		if (pendingTip != null) {
			renderWrapped(g, pendingTip, mouseX, mouseY);
			return;
		}
		int x = leftPos;
		int y = topPos;
		for (int i = 0; i < StarkWorkbench.ERA_COUNT; i++) {
			if (in(IronManUiLayout.fabEraTab(i), mouseX, mouseY, x, y)) {
				renderWrapped(g, List.of(Component.translatable("screen.projecthero.stark_workbench.era", ERA_NUM[i]),
						Component.translatable(i < StarkWorkbench.LIVE_ERAS ? "screen.projecthero.stark_workbench.era_live"
								: "screen.projecthero.stark_workbench.coming_soon").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
				return;
			}
		}
		if (era <= StarkWorkbench.LIVE_ERAS) {
			List<String> list = listed();
			for (int row = 0; row + listScroll < list.size(); row++) {
				Rect r = IronManUiLayout.fabListRow(row);
				if (r.bottom() > IronManUiLayout.FAB_LIST_Y + IronManUiLayout.FAB_LIST_H) {
					break;
				}
				if (in(r, mouseX, mouseY, x, y)) {
					String id = list.get(row + listScroll);
					StarkWorkbench.Status st = StarkWorkbench.status(stark(), id);
					Component line = switch (st) {
						case OWNED -> Component.translatable("screen.projecthero.stark_workbench.owned").withStyle(ChatFormatting.GREEN);
						case BUILDABLE -> Component.translatable("screen.projecthero.stark_workbench.buildable").withStyle(ChatFormatting.GOLD);
						default -> Component.translatable("screen.projecthero.stark_workbench.locked_hint",
								suitName(IronManItems.prerequisiteSuit(id))).withStyle(ChatFormatting.GRAY);
					};
					renderWrapped(g, List.of(suitName(id), line), mouseX, mouseY);
					return;
				}
			}
			for (int i = 0; i < 4 && browseSuit != null; i++) {
				if (in(IronManUiLayout.fabTab(i), mouseX, mouseY, x, y)) {
					boolean built = StarkWorkbench.pieceBuilt(stark(), browseSuit, i + 1);
					renderWrapped(g, List.of(pieceName(i + 1), Component.translatable(built ? "screen.projecthero.stark_workbench.piece_built"
							: "screen.projecthero.stark_workbench.piece_not_built").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
					return;
				}
			}
		}
		if (menu.blueprintStack().isEmpty() && in(slotRect(9), mouseX, mouseY, x, y)) {
			renderWrapped(g, List.of(Component.translatable("screen.projecthero.stark_fabricator.blueprint"),
					Component.translatable("screen.projecthero.stark_workbench.blueprint_tip").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
			return;
		}
		if (menu.slots.get(10).getItem().isEmpty() && in(slotRect(10), mouseX, mouseY, x, y)) {
			renderWrapped(g, List.of(Component.translatable("screen.projecthero.stark_fabricator.output")), mouseX, mouseY);
			return;
		}
		this.renderTooltip(g, mouseX, mouseY);
	}

	private Rect slotRect(int i) {
		var sl = menu.slots.get(i);
		return new Rect(sl.x, sl.y, 16, 16);
	}

	/** Tooltips wrap at {@link TooltipWrap#MAX_WIDTH}. */
	private void renderWrapped(GuiGraphics g, List<Component> lines, int mx, int my) {
		List<FormattedCharSequence> out = new ArrayList<>();
		for (Component c : lines) {
			out.addAll(font.split(c, TooltipWrap.MAX_WIDTH));
		}
		g.renderTooltip(font, out, mx, my);
	}

	/** The View-more modal: drawn above everything, opaque, scrollable. */
	private void renderInfoOverlay(GuiGraphics g) {
		var pose = g.pose();
		pose.pushPose();
		pose.translate(0, 0, 400);
		g.fill(0, 0, this.width, this.height, 0xCC05080C);
		int w = Math.min(240, this.width - 20);
		int h = Math.min(200, this.height - 20);
		int rx = (this.width - w) / 2;
		int ry = (this.height - h) / 2;
		int hdr = 16;
		IronManGui.panel(g, rx, ry, w, h, 0xFF0B1018, IronManGui.PANEL_BORDER, IronManGui.CYAN);
		g.fill(rx + 1, ry + 1, rx + w - 1, ry + hdr, 0xFF111A26);
		g.fill(rx + 1, ry + hdr, rx + w - 1, ry + hdr + 1, IronManGui.alpha(IronManGui.CYAN_DIM, 0.8f));
		IronManSuit suit = IronManSuits.byId(browseSuit);
		g.drawString(font, IronManGui.fit(font, suitName(browseSuit), w - 30), rx + 6, ry + 4, IronManGui.CYAN, false);
		g.drawString(font, "x", rx + w - 10, ry + 4, IronManGui.TEXT_DIM, false);
		int top = ry + hdr + 4;
		int bot = ry + h - 4;
		int visible = bot - top;
		List<FormattedCharSequence> lines = new ArrayList<>();
		for (Component c : buildInfoLines(suit)) {
			lines.addAll(font.split(c, w - 18));
		}
		int contentH = lines.size() * 10;
		infoScrollMax = Math.max(0, contentH - visible);
		infoScroll = Math.max(0, Math.min(infoScroll, infoScrollMax));
		g.enableScissor(rx, top, rx + w, bot);
		int yy = top - infoScroll;
		for (FormattedCharSequence c : lines) {
			if (yy + 10 >= top && yy <= bot) {
				g.drawString(font, c, rx + 7, yy, IronManGui.TEXT, false);
			}
			yy += 10;
		}
		g.disableScissor();
		if (infoScrollMax > 0) {
			int trackX = rx + w - 5;
			g.fill(trackX, top, trackX + 2, bot, 0xFF1A2230);
			int thumbH = Math.max(14, visible * visible / contentH);
			int thumbY = top + (visible - thumbH) * infoScroll / infoScrollMax;
			g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, IronManGui.CYAN_DIM);
		}
		pose.popPose();
	}

	private List<Component> buildInfoLines(IronManSuit suit) {
		List<Component> out = new ArrayList<>();
		FabricationRecipe recipe = recipe();
		if (recipe != null) {
			out.add(Component.translatable("screen.projecthero.stark_fabricator.needs_for", pieceName(browsePiece)).withStyle(ChatFormatting.GOLD));
			for (FabricationRecipe.Input in : recipe.inputs()) {
				int have = have(in.item());
				boolean ok = have >= in.count();
				MutableComponent line = Component.literal(ok ? " ✓ " : " ✗ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
						.append(Component.literal(in.count() + "x ").withStyle(ChatFormatting.GRAY))
						.append(new ItemStack(in.item()).getHoverName().copy().withStyle(ChatFormatting.WHITE))
						.append(Component.literal(ok ? "" : "  (" + have + ")").withStyle(ChatFormatting.DARK_GRAY));
				out.add(line);
			}
			out.add(Component.translatable("screen.projecthero.stark_fabricator.cost", recipe.energyCost(),
					String.format(Locale.ROOT, "%.1f", recipe.timeTicks() / 20.0)).withStyle(ChatFormatting.DARK_GRAY));
			out.add(Component.empty());
		} else {
			out.add(Component.translatable("screen.projecthero.stark_workbench.table_hint").withStyle(ChatFormatting.GRAY));
			out.add(Component.empty());
		}
		if (suit != null) {
			out.add(Component.translatable("screen.projecthero.stark_fabricator.suit_stats").withStyle(ChatFormatting.GOLD));
			for (com.projecthero.mod.ironman.ui.IronManComicText.Row r : com.projecthero.mod.ironman.ui.IronManComicText.stats(suit)) {
				out.add(Component.literal(" ").append(r.label().copy().withStyle(ChatFormatting.GRAY))
						.append(Component.literal("  " + r.value()).withStyle(ChatFormatting.WHITE)));
			}
			out.add(Component.translatable("screen.projecthero.ironman_comic.moves").withStyle(ChatFormatting.GOLD));
			for (Component line : com.projecthero.mod.ironman.ui.IronManComicText.moves(suit, new String[] { "R", "G", "X", "Z", "V" })) {
				out.add(Component.literal(" ").append(line.copy().withStyle(ChatFormatting.WHITE)));
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseScrolled(double mx, double my, double dx, double dy) {
		if (showInfo) {
			infoScroll = Math.max(0, Math.min(infoScrollMax, infoScroll - (int) Math.round(dy * 12)));
			return true;
		}
		if (new Rect(IronManUiLayout.FAB_LIST_X, IronManUiLayout.FAB_LIST_Y, IronManUiLayout.FAB_LIST_W, IronManUiLayout.FAB_LIST_H)
				.contains(mx - leftPos, my - topPos)) {
			listScroll = Math.max(0, listScroll - (int) Math.signum(dy));
			return true;
		}
		return super.mouseScrolled(mx, my, dx, dy);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (showInfo) {
			showInfo = false;
			infoScroll = 0;
			return true;
		}
		int x = leftPos;
		int y = topPos;
		if (button == 0) {
			for (int i = 0; i < StarkWorkbench.LIVE_ERAS; i++) {
				if (in(IronManUiLayout.fabEraTab(i), mx, my, x, y)) {
					era = i + 1;
					return true;
				}
			}
			for (int i = StarkWorkbench.LIVE_ERAS; i < StarkWorkbench.ERA_COUNT; i++) {
				if (in(IronManUiLayout.fabEraTab(i), mx, my, x, y)) {
					return true; // locked: "coming soon" (tooltip)
				}
			}
			if (era <= StarkWorkbench.LIVE_ERAS) {
				List<String> list = listed();
				for (int row = 0; row + listScroll < list.size(); row++) {
					Rect r = IronManUiLayout.fabListRow(row);
					if (r.bottom() > IronManUiLayout.FAB_LIST_Y + IronManUiLayout.FAB_LIST_H) {
						break;
					}
					if (in(r, mx, my, x, y)) {
						browseSuit = list.get(row + listScroll);
						browsePiece = browsingJob() && menu.selectedPiece() >= 1 ? menu.selectedPiece()
								: StarkWorkbench.firstUnbuiltPiece(stark(), browseSuit);
						click();
						return true;
					}
				}
				for (int i = 0; i < 4; i++) {
					if (in(IronManUiLayout.fabTab(i), mx, my, x, y)) {
						browsePiece = i + 1;
						click();
						return true;
					}
				}
				if (in(IronManUiLayout.fabMore(), mx, my, x, y) && browseSuit != null) {
					showInfo = true;
					infoScroll = 0;
					return true;
				}
				if (in(IronManUiLayout.fabBuild(), mx, my, x, y) && canBuild()) {
					int index = IronManItems.MARK_ORDER.indexOf(browseSuit);
					if (index >= 0 && minecraft != null && minecraft.gameMode != null) {
						minecraft.gameMode.handleInventoryButtonClick(menu.containerId, StarkWorkbench.buildButtonId(index, browsePiece));
					}
					click();
					return true;
				}
				if (in(new Rect(IronManUiLayout.FAB_HOLO_X, IronManUiLayout.FAB_HOLO_Y, IronManUiLayout.FAB_HOLO_W, IronManUiLayout.FAB_HOLO_H),
						mx, my, x, y)) {
					dragging = true;
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	private void click() {
		if (minecraft != null) {
			minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
					net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.35f));
		}
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging && button == 0) {
			yaw += (float) dx * 2.5f;
			lastDragMs = Util.getMillis();
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		if (dragging && button == 0) {
			dragging = false;
			lastDragMs = Util.getMillis();
			return true;
		}
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (showInfo && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
			showInfo = false;
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}
}
