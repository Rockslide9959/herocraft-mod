package com.projecthero.mod.client.stand;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.StandMove;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.data.StandState;
import com.projecthero.mod.stand.network.StandActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.2: the Stand Menu (Shift+N). Left: the Stand's stat card (Power, Speed, Range, Stamina, Precision, Potential --
 * canon grades with a bar each), the pose picker (what N strikes) and the settings (floating ORA text, rush auto-face).
 * Right: the whole move list with each move's description, wrapped to the column and scrollable (the tooltip-wrap rule: no text runs off the panel).
 * Choices go to the server and are saved. The menu never pauses the game.
 */
public final class StandMenuScreen extends Screen {
	private static final int LEFT_W = 150;
	private static final int RIGHT_W = 220;
	private static final int PANEL_W = LEFT_W + RIGHT_W;
	private static final int PANEL_H = 236;
	private static final int LIST_TOP = 26;
	private static final int LIST_BOTTOM = PANEL_H - 32;
	private static final float SMALL = 0.8f;
	private static final String[] STAT_KEYS = { "power", "speed", "range", "stamina", "precision", "potential" };
	private static final String[] POSES = { "pointing" };
	private static final String[] KEYS = { "R", "Hold R", "Shift+R", "G", "Shift+G", "X", "Shift+X", "Z", "Shift+Z", "V", "Shift+V", "C", "Shift+C" };

	private int pose = 1;
	private boolean oraText = true;
	private boolean autoFace = true;
	/** v0.17.3: the shared scroll logic (VanillaMenu.Scroll): wheel, a draggable bar, click the track to page, keys. */
	private final com.projecthero.mod.client.gui.VanillaMenu.Scroll scroll = new com.projecthero.mod.client.gui.VanillaMenu.Scroll(12, 1).glass(8);
	private List<FormattedCharSequence> listLines = List.of();
	private List<Integer> listColours = List.of();

	public StandMenuScreen() {
		super(Component.translatable("screen.projecthero.stand_menu.title"));
	}

	@Override
	protected void init() {
		StandState s = minecraft.player == null ? null : Stands.peek(minecraft.player);
		if (s != null) {
			pose = Mth.clamp(s.pose, 1, POSES.length);
			oraText = s.oraText;
			autoFace = s.autoFace;
		}
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		for (int i = 0; i < POSES.length; i++) {
			int index = i + 1;
			int bx = left + 10;
			int by = top + 128 + i * 22;
			addRenderableWidget(Button.builder(poseLabel(index), b -> {
				pose = index;
				ClientPlayNetworking.send(new StandActionPayload(StandActionPayload.Action.SET_POSE, index));
				rebuildWidgets();
			}).bounds(bx, by, LEFT_W - 20, 20).tooltip(Tooltip.create(Component.translatable("screen.projecthero.stand_menu.pose." + POSES[i] + ".tip"))).build());
		}
		addRenderableWidget(Button.builder(toggleLabel("ora_text", oraText), b -> {
			oraText = !oraText;
			sendSettings();
			b.setMessage(toggleLabel("ora_text", oraText));
		}).bounds(left + 10, top + 184, LEFT_W - 20, 18).tooltip(Tooltip.create(Component.translatable("screen.projecthero.stand_menu.ora_text.tip"))).build());
		addRenderableWidget(Button.builder(toggleLabel("auto_face", autoFace), b -> {
			autoFace = !autoFace;
			sendSettings();
			b.setMessage(toggleLabel("auto_face", autoFace));
		}).bounds(left + 10, top + 206, LEFT_W - 20, 18).tooltip(Tooltip.create(Component.translatable("screen.projecthero.stand_menu.auto_face.tip"))).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + LEFT_W + RIGHT_W / 2 - 40, top + PANEL_H - 26, 80, 20).build());
		buildList();
	}

	private Component poseLabel(int index) {
		Component name = Component.translatable("screen.projecthero.stand_menu.pose." + POSES[index - 1]);
		return index == pose ? Component.literal("» ").append(name).withStyle(ChatFormatting.LIGHT_PURPLE) : name;
	}

	private Component toggleLabel(String key, boolean on) {
		return Component.translatable("screen.projecthero.stand_menu." + key,
				Component.translatable(on ? "options.on" : "options.off"));
	}

	private void sendSettings() {
		ClientPlayNetworking.send(new StandActionPayload(StandActionPayload.Action.SET_SETTINGS, (oraText ? 1 : 0) | (autoFace ? 2 : 0)));
	}

	/** The move list, wrapped once to the column at the small scale. */
	private void buildList() {
		int wrap = (int) ((RIGHT_W - 22) / SMALL);
		List<FormattedCharSequence> lines = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		String[] ids = new String[StandMove.STAR_PLATINUM.size()];
		for (int i = 0; i < ids.length; i++) {
			ids[i] = StandMove.STAR_PLATINUM.get(i).id();
		}
		addLines(lines, colours, Component.literal("H  ").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable("projecthero.stand.move.summon").withStyle(ChatFormatting.WHITE)), wrap, 0xFFFFFFFF);
		addLines(lines, colours, Component.translatable("projecthero.stand.move.summon.desc"), wrap, 0xFFB0A8C0);
		addLines(lines, colours, Component.literal("N  ").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable("projecthero.stand.move.pose").withStyle(ChatFormatting.WHITE)), wrap, 0xFFFFFFFF);
		addLines(lines, colours, Component.translatable("projecthero.stand.move.pose.desc"), wrap, 0xFFB0A8C0);
		// v0.17.3: the left mouse button's two jobs (punching, mining)
		for (String lmb : new String[] { "punch", "mining" }) {
			addLines(lines, colours, Component.literal("LMB  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.stand.move." + lmb).withStyle(ChatFormatting.WHITE)), wrap, 0xFFFFFFFF);
			addLines(lines, colours, Component.translatable("projecthero.stand.move." + lmb + ".desc"), wrap, 0xFFB0A8C0);
		}
		for (int i = 0; i < ids.length; i++) {
			addLines(lines, colours, Component.literal(KEYS[i] + "  ").withStyle(ChatFormatting.GOLD)
					.append(Component.translatable("projecthero.stand.move." + ids[i]).withStyle(ChatFormatting.WHITE)), wrap, 0xFFFFFFFF);
			addLines(lines, colours, Component.translatable("projecthero.stand.move." + ids[i] + ".desc"), wrap, 0xFFB0A8C0);
		}
		listLines = lines;
		listColours = colours;
	}

	private void addLines(List<FormattedCharSequence> lines, List<Integer> colours, Component text, int wrap, int colour) {
		for (FormattedCharSequence l : font.split(text, wrap)) {
			lines.add(l);
			colours.add(colour);
		}
	}

	private int listHeight() {
		return (int) (listLines.size() * 10 * SMALL);
	}

	/** Points the scroll at the move list (its column, its height, the bar at the column's right edge). */
	private void syncScroll() {
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		int rx = left + LEFT_W + 6;
		scroll.layout(rx, top + LIST_TOP, rx + RIGHT_W - 12, top + LIST_BOTTOM, rx + RIGHT_W - 12);
		scroll.content(listHeight());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
		syncScroll();
		return scroll.mouseScrolled(dy) || super.mouseScrolled(mouseX, mouseY, dx, dy);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		syncScroll();
		return scroll.mouseClicked(mx, my, button) || super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		return scroll.mouseDragged(mx, my, button) || super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		boolean was = scroll.mouseReleased(button);
		return super.mouseReleased(mx, my, button) || was;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		syncScroll();
		return scroll.keyPressed(key, 10) || super.keyPressed(key, scan, mods);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		var player = minecraft.player;
		StandState s = player == null ? null : Stands.peek(player);
		StandType type = player == null ? null : Stands.type(player);
		g.drawCenteredString(font, title, width / 2, top + 7, 0xFFD8C2FF);
		if (s == null || type == null) {
			return;
		}
		// ---- the stat card
		g.drawString(font, Component.translatable(type.nameKey()).withStyle(ChatFormatting.BOLD), left + 10, top + 26, 0xFFFFFFFF, true);
		String[] grades = type.stats();
		for (int i = 0; i < 6; i++) {
			int y = top + 40 + i * 12;
			g.drawString(font, Component.translatable("screen.projecthero.stand_menu.stat." + STAT_KEYS[i]), left + 10, y, 0xFFB8B0C8, false);
			g.drawString(font, grades[i], left + 80, y, 0xFFFFE070, true);
			int segs = gradeSegments(grades[i]);
			for (int k = 0; k < 5; k++) {
				int sx = left + 92 + k * 9;
				g.fill(sx, y + 1, sx + 7, y + 7, k < segs ? 0xFF9A5CFF : 0xFF2A2236);
			}
		}
		g.drawString(font, Component.translatable("screen.projecthero.stand_menu.pose"), left + 10, top + 116, 0xFFD8C2FF, true);
		g.drawString(font, Component.translatable("screen.projecthero.stand_menu.settings"), left + 10, top + 174, 0xFFD8C2FF, false);

		// ---- the move list (scrolls)
		int rx = left + LEFT_W + 6;
		g.enableScissor(rx, top + LIST_TOP, rx + RIGHT_W - 12, top + LIST_BOTTOM);
		g.pose().pushPose();
		syncScroll();
		double shown = scroll.eased();
		g.pose().translate(rx, top + LIST_TOP - (float) shown, 0);
		g.pose().scale(SMALL, SMALL, 1f);
		for (int i = 0; i < listLines.size(); i++) {
			g.drawString(font, listLines.get(i), 0, i * 10, listColours.get(i), false);
		}
		g.pose().popPose();
		g.disableScissor();
		scroll.renderGlass(g, 0xFFB07CFF, mouseX, mouseY);
	}

	/** A canon grade as bar segments: A 5 .. E 1 (anything else, "None" / "?", 0). */
	private static int gradeSegments(String grade) {
		return switch (grade) {
			case "A" -> 5;
			case "B" -> 4;
			case "C" -> 3;
			case "D" -> 2;
			case "E" -> 1;
			default -> 0;
		};
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.renderBackground(g, mouseX, mouseY, partial);
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0100A18);
		g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF7A4CD0);
		g.fill(left + 1, top + 1, left + PANEL_W - 1, top + 20, 0xC03A1F66);
		g.fill(left + LEFT_W - 2, top + 24, left + LEFT_W - 1, top + PANEL_H - 30, 0x807A4CD0);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public static void open() {
		Minecraft.getInstance().setScreen(new StandMenuScreen());
	}
}
