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
 */
public final class SpeedForceMenuScreen extends Screen {
	private static final int SWATCH = 22;
	private static final int SWATCH_GAP = 4;
	private static final int PANEL_W = 5 * SWATCH + 4 * SWATCH_GAP + 40;
	private static final int PANEL_H = 204;
	/** v0.17.1: the Suit row's top (label) -- five Flash Suit style tiles under it. */
	private static final int SUIT_ROW = 130;

	private Button stickButton;
	private boolean stick;
	private int colour;
	/** The Flash Suit style shown as chosen; -1 when the player has no Flash Suit (worn, in the worn ring, or held). */
	private int suitStyle = -1;

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
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		stickButton = addRenderableWidget(Button.builder(stickLabel(), b -> {
			stick = !stick;
			send(-1);
			b.setMessage(stickLabel());
		}).bounds(left + 20, top + 26, PANEL_W - 40, 20)
				.tooltip(Tooltip.create(Component.translatable("screen.projecthero.speed_force_menu.ground_stick.tip"))).build());
		int sx = left + 20;
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
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(left + PANEL_W / 2 - 40, top + PANEL_H - 26, 80, 20).build());
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
				Component.translatable("screen.projecthero.speed_force_menu.colour." + SpeedForceColours.id(colour))), left + 20, top + 60, 0xFFFFFFFF, true);
		g.drawString(font, suitStyle < 0
				? Component.translatable("screen.projecthero.speed_force_menu.suit.none")
				: Component.translatable("screen.projecthero.speed_force_menu.suit", FlashSuitStyle.byOrdinal(suitStyle).displayName()),
				left + 20, top + SUIT_ROW, suitStyle < 0 ? 0xFF909090 : 0xFFFFFFFF, true);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.renderBackground(g, mouseX, mouseY, partial);
		int left = (width - PANEL_W) / 2;
		int top = (height - PANEL_H) / 2;
		g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xE0140808);
		g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFFB01E1E);
		g.fill(left + 1, top + 1, left + PANEL_W - 1, top + 20, 0xC0601010);
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
