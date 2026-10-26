package com.projecthero.mod.client.speedforce;

import com.projecthero.mod.client.speed.SpeedsterGroundStick;
import com.projecthero.mod.flash.FlashSuitStyle;
import com.projecthero.mod.speedforce.SpeedForceColours;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.data.SpeedForceState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * v0.17.1 (user: "when speed force speedster presses Shift+N open a menu"): Ground-Stick on / off and the lightning
 * colour, ten swatches. Both are sent to the server and saved (everyone sees your colour); the menu never pauses the game.
 * v0.17.1 (user, Speed Force Memories): a second column -- "Remember this place" and the marked places (up to 5, a sixth
 * replacing the oldest), each with a Forget button. The memory road inside the Speed Force has a doorway to each.
 */
public final class SpeedForceMenuScreen extends Screen {
	private static final int SWATCH = 22;
	private static final int SWATCH_GAP = 4;
	private static final int LEFT_W = 5 * SWATCH + 4 * SWATCH_GAP + 20; // v0.17.1: 10 either side (three columns fit an 854-wide window)
	/** v0.17.1: the memories column, right of the settings. */
	private static final int MEM_W = 150;
	/** v0.17.1 (user): the run column -- Auto-Slowdown, vein mining, the top speed, the race's best times. */
	private static final int RUN_W = 120;
	private static final int PANEL_W = LEFT_W + MEM_W + RUN_W;
	private static final int ROW_H = 16;
	private static final int PANEL_H = 220;
	/** v0.17.1: the Suit row's top (label) -- five Flash Suit style tiles under it. */
	private static final int SUIT_ROW = 130;

	private Button stickButton;
	private boolean stick;
	private int colour;
	/** v0.17.1 (user): the run column's settings as shown. */
	private boolean autoSlow = true;
	private boolean vein = true;
	private int cap = 10;
	/** The Flash Suit style shown as chosen; -1 when the player has no Flash Suit (worn, in the worn ring, or held). */
	private int suitStyle = -1;
	/** The memories the buttons were built from (rebuilt when the synced copy changes). */
	private com.projecthero.mod.speedforce.data.SpeedForceMemories shown;

	public SpeedForceMenuScreen() {
		super(Component.translatable("screen.projecthero.speed_force_menu.title"));
	}

	public static void open() {
		Minecraft.getInstance().setScreen(new SpeedForceMenuScreen());
	}

	@Override
	protected void init() {
		SpeedForceState s = minecraft.player == null ? null : SpeedForceSpeedster.peek(minecraft.player);
		stick = s != null && s.groundStick;
		colour = s == null ? 0 : SpeedForceColours.clamp(s.colour);
		autoSlow = s == null || s.autoSlow;
		vein = s == null || s.veinMine;
		cap = s == null ? 10 : Math.max(1, Math.min(10, s.speedCap));
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		stickButton = addRenderableWidget(Button.builder(stickLabel(), b -> {
			stick = !stick;
			send(-1);
			b.setMessage(stickLabel());
		}).bounds(left + 10, top + 26, LEFT_W - 20, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.projecthero.speed_force_menu.ground_stick.tip"))).build());
		int sx = left + 10;
		int sy = top + 74;
		for (int i = 0; i < SpeedForceColours.COUNT; i++) {
			int col = i % 5;
			int row = i / 5;
			addRenderableWidget(new Swatch(sx + col * (SWATCH + SWATCH_GAP), sy + row * (SWATCH + SWATCH_GAP), i));
		}
		// v0.17.1 (user): the Flash Suit's look -- one tile per style, showing that suit's cowl
		FlashSuitStyle now = minecraft.player == null ? null : FlashSuitStyle.current(minecraft.player);
		suitStyle = now == null ? -1 : now.ordinal();
		FlashSuitStyle[] styles = FlashSuitStyle.values();
		for (int i = 0; i < styles.length; i++) {
			SuitTile tile = addRenderableWidget(new SuitTile(sx + i * (SWATCH + SWATCH_GAP), top + SUIT_ROW + 14, styles[i]));
			tile.active = now != null;
		}
		// v0.17.1 (user): the memories column
		shown = memories();
		int mx = left + LEFT_W;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.speed_force_menu.remember"), b -> memory(
				com.projecthero.mod.speedforce.network.SpeedForceMemoryPayload.REMEMBER, -1)).bounds(mx, top + 38, MEM_W - 12, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.projecthero.speed_force_menu.remember.tip"))).build());
		for (int i = 0; i < shown.marked().size(); i++) {
			int index = i;
			addRenderableWidget(Button.builder(Component.literal("x"), b -> memory(com.projecthero.mod.speedforce.network.SpeedForceMemoryPayload.FORGET, index))
					.bounds(mx + MEM_W - 28, top + 64 + i * ROW_H, 14, 14)
					.tooltip(Tooltip.create(Component.translatable("screen.projecthero.speed_force_menu.forget.tip"))).build());
			// v0.17.1 (user): the Auto-Courier -- the stack in hand, run there
			var place = shown.marked().get(i);
			addRenderableWidget(Button.builder(Component.literal("»"), b -> memory(com.projecthero.mod.speedforce.network.SpeedForceMemoryPayload.SEND, index))
					.bounds(mx + MEM_W - 44, top + 64 + i * ROW_H, 14, 14)
					.tooltip(Tooltip.create(Component.translatable(place.chest().isPresent() ? "screen.projecthero.speed_force_menu.send.tip_chest"
							: "screen.projecthero.speed_force_menu.send.tip", Math.round(com.projecthero.mod.speedforce.SpeedForceConfig.COURIER_COST)))).build());
		}
		// v0.17.1 (user): the run column
		int rx = left + LEFT_W + MEM_W;
		addRenderableWidget(Button.builder(autoSlowLabel(), b -> {
			autoSlow = !autoSlow;
			sendAssists();
			b.setMessage(autoSlowLabel());
		}).bounds(rx, top + 38, RUN_W - 12, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.projecthero.speed_force_menu.auto_slow.tip"))).build());
		addRenderableWidget(Button.builder(veinLabel(), b -> {
			vein = !vein;
			sendAssists();
			b.setMessage(veinLabel());
		}).bounds(rx, top + 62, RUN_W - 12, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.projecthero.speed_force_menu.vein.tip",
						com.projecthero.mod.speedforce.SpeedForceConfig.VEIN_MAX))).build());
		CapSlider slider = addRenderableWidget(new CapSlider(rx, top + 86, RUN_W - 12, 20));
		slider.setTooltip(Tooltip.create(Component.translatable("screen.projecthero.speed_force_menu.cap.tip")));
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + PANEL_W / 2 - 40, top + PANEL_H - 26, 80, 20).build());
	}

	private static com.projecthero.mod.speedforce.data.SpeedForceMemories memories() {
		var p = Minecraft.getInstance().player;
		return p == null ? com.projecthero.mod.speedforce.data.SpeedForceMemories.EMPTY
				: p.getAttachedOrElse(com.projecthero.mod.speedforce.SpeedForceAttachments.MEMORIES, com.projecthero.mod.speedforce.data.SpeedForceMemories.EMPTY);
	}

	private void memory(int action, int index) {
		if (minecraft.player != null) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new com.projecthero.mod.speedforce.network.SpeedForceMemoryPayload(action, index));
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!memories().equals(shown)) {
			rebuildWidgets(); // the server saved (or forgot) one: the list follows
		}
	}

	private Component autoSlowLabel() {
		return Component.translatable(autoSlow ? "screen.projecthero.speed_force_menu.auto_slow.on" : "screen.projecthero.speed_force_menu.auto_slow.off");
	}

	private Component veinLabel() {
		return Component.translatable(vein ? "screen.projecthero.speed_force_menu.vein.on" : "screen.projecthero.speed_force_menu.vein.off");
	}

	private void sendAssists() {
		if (minecraft.player != null) {
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload(
					stick, -1, -1, com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload.assists(autoSlow, vein, cap)));
		}
	}

	/** v0.17.1 (user): the top speed, 10% to 100% of the momentum in 10% steps (100% = no cap). */
	private final class CapSlider extends net.minecraft.client.gui.components.AbstractSliderButton {
		CapSlider(int x, int y, int w, int h) {
			super(x, y, w, h, Component.empty(), (cap - 1) / 9.0);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(cap >= 10 ? Component.translatable("screen.projecthero.speed_force_menu.cap.none")
					: Component.translatable("screen.projecthero.speed_force_menu.cap", cap * 10));
		}

		@Override
		protected void applyValue() {
			int v = 1 + (int) Math.round(value * 9.0);
			if (v != cap) {
				cap = v;
				sendAssists();
			}
		}
	}

	private Component stickLabel() {
		return Component.translatable(stick ? "screen.projecthero.speed_force_menu.ground_stick.on" : "screen.projecthero.speed_force_menu.ground_stick.off");
	}

	private void send(int newColour) {
		if (minecraft.player != null) {
			SpeedsterGroundStick.set(minecraft.player, stick, newColour);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		g.drawCenteredString(font, title, width / 2, top + 8, 0xFFFFD23A);
		g.drawString(font, Component.translatable("screen.projecthero.speed_force_menu.colour",
				Component.translatable("screen.projecthero.speed_force_menu.colour." + SpeedForceColours.id(colour))), left + 10, top + 60, 0xFFFFFFFF, true);
		g.drawString(font, suitStyle < 0
				? Component.translatable("screen.projecthero.speed_force_menu.suit.none")
				: Component.translatable("screen.projecthero.speed_force_menu.suit", FlashSuitStyle.byOrdinal(suitStyle).displayName()),
				left + 10, top + SUIT_ROW, suitStyle < 0 ? 0xFF909090 : 0xFFFFFFFF, true);
		// v0.17.1: the memories column
		int mx = left + LEFT_W;
		g.drawString(font, Component.translatable("screen.projecthero.speed_force_menu.memories",
				shown.marked().size(), com.projecthero.mod.speedforce.data.SpeedForceMemories.MARKED_MAX), mx, top + 26, 0xFFFFD23A, true);
		for (int i = 0; i < shown.marked().size(); i++) {
			var place = shown.marked().get(i);
			Component row = Component.literal((i + 1) + ". " + (place.chest().isPresent() ? "▣ " : ""))
					.append(com.projecthero.mod.speedforce.SpeedForceMemory.describe(place));
			// small (0.8x) so the whole place fits beside its Send and Forget buttons (a chest marked there: the box sign)
			g.pose().pushPose();
			g.pose().translate(mx, top + 68 + i * ROW_H, 0);
			g.pose().scale(0.8f, 0.8f, 1f);
			g.drawString(font, font.plainSubstrByWidth(row.getString(), (int) ((MEM_W - 48) / 0.8f)), 0, 0, 0xFF8FE8FF, true);
			g.pose().popPose();
		}
		if (shown.marked().isEmpty()) {
			g.drawString(font, Component.translatable("screen.projecthero.speed_force_menu.memories.none"), mx, top + 67, 0xFF909090, true);
		}
		int hy = top + 64 + Math.max(1, shown.marked().size()) * ROW_H + 6;
		g.pose().pushPose();
		g.pose().translate(mx, hy, 0);
		g.pose().scale(0.8f, 0.8f, 1f);
		int ly = 0;
		for (var line : font.split(Component.translatable("screen.projecthero.speed_force_menu.memories.hint"), (int) ((MEM_W - 12) / 0.8f))) {
			if (hy + ly * 0.8f > top + PANEL_H - 34) {
				break;
			}
			g.drawString(font, line, 0, ly, 0xFFB0A090, true);
			ly += 10;
		}
		g.pose().popPose();
		renderRunColumn(g, left + LEFT_W + MEM_W, top);
	}

	/** v0.17.1 (user): the run column's title, and the race course with its best times. */
	private void renderRunColumn(GuiGraphics g, int rx, int top) {
		g.drawString(font, Component.translatable("screen.projecthero.speed_force_menu.run"), rx, top + 26, 0xFFFFD23A, true);
		g.drawString(font, Component.translatable("screen.projecthero.speed_force_menu.race"), rx, top + 114, 0xFFFFD23A, true);
		var p = minecraft.player;
		com.projecthero.mod.speedforce.data.SpeedForceRace race = p == null ? com.projecthero.mod.speedforce.data.SpeedForceRace.EMPTY
				: p.getAttachedOrElse(com.projecthero.mod.speedforce.SpeedForceAttachments.RACE, com.projecthero.mod.speedforce.data.SpeedForceRace.EMPTY);
		int wrap = (int) ((RUN_W - 12) / 0.8f);
		g.pose().pushPose();
		g.pose().translate(rx, top + 126, 0);
		g.pose().scale(0.8f, 0.8f, 1f);
		int ly = 0;
		int maxY = (int) ((PANEL_H - 34 - 126) / 0.8f);
		if (!race.ready() || race.best().isEmpty()) {
			String key = race.start().isEmpty() ? "screen.projecthero.speed_force_menu.race.none"
					: race.finish().isEmpty() ? "screen.projecthero.speed_force_menu.race.no_finish" : "screen.projecthero.speed_force_menu.race.no_times";
			for (var line : font.split(Component.translatable(key), wrap)) {
				if (ly > maxY) {
					break;
				}
				g.drawString(font, line, 0, ly, 0xFFB0A090, true);
				ly += 10;
			}
		} else {
			for (int i = 0; i < race.best().size() && ly <= maxY; i++) {
				String t = (i + 1) + ". " + com.projecthero.mod.speedforce.SpeedForceRaces.format(race.best().get(i));
				g.drawString(font, t, 0, ly, i == 0 ? 0xFFFFE070 : 0xFF8FE8FF, true);
				ly += 10;
			}
			for (var line : font.split(Component.translatable("screen.projecthero.speed_force_menu.race.ghost"), wrap)) {
				if (ly > maxY) {
					break;
				}
				g.drawString(font, line, 0, ly + 2, 0xFFB0A090, true);
				ly += 10;
			}
		}
		g.pose().popPose();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.renderBackground(g, mouseX, mouseY, partial);
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0140808);
		g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFFB01E1E);
		g.fill(left + 1, top + 1, left + PANEL_W - 1, top + 20, 0xC0601010);
		g.fill(left + LEFT_W - 8, top + 24, left + LEFT_W - 7, top + PANEL_H - 30, 0x80B01E1E); // between the columns
		g.fill(left + LEFT_W + MEM_W - 8, top + 24, left + LEFT_W + MEM_W - 7, top + PANEL_H - 30, 0x80B01E1E);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** v0.17.1: one Flash Suit style -- its cowl (head front + second layer) drawn big, outlined white when chosen. */
	private final class SuitTile extends AbstractButton {
		private final FlashSuitStyle style;

		SuitTile(int x, int y, FlashSuitStyle style) {
			super(x, y, SWATCH, SWATCH, style.displayName());
			this.style = style;
			setTooltip(Tooltip.create(Component.translatable("screen.projecthero.speed_force_menu.suit.tip", style.displayName())));
		}

		@Override
		public void onPress() {
			suitStyle = style.ordinal();
			net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload(stick, -1, style.ordinal()));
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
			int x = getX();
			int y = getY();
			boolean chosen = suitStyle == style.ordinal();
			g.fill(x, y, x + width, y + height, 0xFF000000 | 0x241010);
			// the cowl's face: base (8,8) then the second layer (40,8), 8x8 texels each, at 2x
			g.blit(style.texture(), x + 3, y + 3, 16, 16, 8f, 8f, 8, 8, 64, 64);
			com.mojang.blaze3d.systems.RenderSystem.enableBlend();
			g.blit(style.texture(), x + 3, y + 3, 16, 16, 40f, 8f, 8, 8, 64, 64);
			com.mojang.blaze3d.systems.RenderSystem.disableBlend();
			if (!active) {
				g.fill(x, y, x + width, y + height, 0xA0000000);
			}
			g.renderOutline(x - 1, y - 1, width + 2, height + 2, chosen ? 0xFFFFFFFF : isHovered() && active ? 0xFFB0B0B0 : 0xFF303030);
			if (chosen) {
				g.renderOutline(x - 2, y - 2, width + 4, height + 4, 0xFFFFFFFF);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}
	}

	/** One colour: a little lightning-coloured tile, outlined white when chosen. */
	private final class Swatch extends AbstractButton {
		private final int index;

		Swatch(int x, int y, int index) {
			super(x, y, SWATCH, SWATCH, Component.translatable("screen.projecthero.speed_force_menu.colour." + SpeedForceColours.id(index)));
			this.index = index;
			setTooltip(Tooltip.create(getMessage()));
		}

		@Override
		public void onPress() {
			colour = index;
			send(index);
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
			int x = getX();
			int y = getY();
			boolean chosen = colour == index;
			g.fill(x, y, x + width, y + height, 0xFF000000 | SpeedForceColours.tone(index, 0));
			g.fill(x + 3, y + 3, x + width - 3, y + height - 3, 0xFF000000 | SpeedForceColours.tone(index, 1));
			// a little bolt across the tile in the bright tone and the core
			int c2 = 0xFF000000 | SpeedForceColours.tone(index, 2);
			int c3 = 0xFF000000 | SpeedForceColours.tone(index, 3);
			g.fill(x + 12, y + 4, x + 14, y + 9, c2);
			g.fill(x + 9, y + 9, x + 14, y + 11, c2);
			g.fill(x + 9, y + 11, x + 11, y + 18, c2);
			g.fill(x + 12, y + 5, x + 13, y + 9, c3);
			g.fill(x + 10, y + 12, x + 11, y + 17, c3);
			g.renderOutline(x - 1, y - 1, width + 2, height + 2, chosen ? 0xFFFFFFFF : isHovered() ? 0xFFB0B0B0 : 0xFF303030);
			if (chosen) {
				g.renderOutline(x - 2, y - 2, width + 4, height + 4, 0xFFFFFFFF);
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}
	}
}
