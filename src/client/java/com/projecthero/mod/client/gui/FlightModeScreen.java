package com.projecthero.mod.client.gui;

import java.util.List;

import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.flight.FlightSettings;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.16.2: the {@code ,} Flight Mode menu -- pick <b>Regular Flight</b> (the directional flight every hero has flown since
 * v0.14.16) or <b>Physics Flight</b> (Do a Barrel Roll-style: momentum, banking, camera roll), each with a one-line
 * explanation, plus the Physics options (mouse X roll / yaw, roll sensitivity, invert roll, auto-level, camera roll).
 * Saved per player on this computer ({@link FlightSettings}); applies to every hero flight. Drawn like the Bifrost and
 * Thor weapon menus (gold on night blue); every line of prose is word-wrapped to the panel.
 */
public final class FlightModeScreen extends Screen {
	private static final int PANEL_W = 300;
	private static final int TEXT_W = PANEL_W - 24;
	private static final int BUTTON_W = 62;
	/** A mode's explanation runs full width under its name (the button sits on the name line). */
	private static final int ROW_TEXT_W = PANEL_W - 32;

	private static final int PANEL_TOP = 0xF2141A2E;
	private static final int PANEL_BOTTOM = 0xF20A0D18;
	private static final int GOLD = 0xFFE8C766;
	private static final int GOLD_DIM = 0xFF8C7536;
	private static final int TEXT = 0xFFE8ECF8;
	private static final int MUTED = 0xFF8D94AD;
	private static final int ACTIVE = 0xFF7FD8FF;
	private static final int ROW_ON = 0x60283A60;
	private static final int ROW_OFF = 0x40181C2C;

	private List<FormattedCharSequence> intro = List.of();
	private List<FormattedCharSequence> regularText = List.of();
	private List<FormattedCharSequence> physicsText = List.of();
	private Button regularButton;
	private Button physicsButton;
	private Button mouseButton;
	private Button invertButton;
	private Button levelButton;
	private Button cameraButton;
	private int panelH;
	private int regularH;
	private int physicsH;

	public FlightModeScreen() {
		super(Component.translatable("screen.projecthero.flight_mode.title"));
	}

	private int left() {
		return (this.width - PANEL_W) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH) / 2);
	}

	private int regularTop() {
		return top() + 24 + intro.size() * 10 + 2;
	}

	private int physicsTop() {
		return regularTop() + regularH + 3;
	}

	private int optionsTop() {
		return physicsTop() + physicsH + 4;
	}

	@Override
	protected void init() {
		intro = this.font.split(Component.translatable("screen.projecthero.flight_mode.intro"), TEXT_W);
		regularText = this.font.split(Component.translatable("screen.projecthero.flight_mode.regular.desc"), ROW_TEXT_W);
		physicsText = this.font.split(Component.translatable("screen.projecthero.flight_mode.physics.desc"), ROW_TEXT_W);
		regularH = 18 + regularText.size() * 10 + 2;
		physicsH = 18 + physicsText.size() * 10 + 2;
		// title, intro, two mode rows, the options header + 3 rows of option buttons, Done -- compact enough for a
		// 240-pixel-tall GUI (854 x 480 at GUI scale 2)
		panelH = 24 + intro.size() * 10 + 2 + regularH + 3 + physicsH + 4 + 12 + 3 * 20 + 22;

		int l = left();
		int r = l + PANEL_W;
		regularButton = addRenderableWidget(Button.builder(Component.empty(), b -> setPhysics(false))
				.bounds(r - 12 - BUTTON_W, regularTop() + 2, BUTTON_W, 14).build());
		physicsButton = addRenderableWidget(Button.builder(Component.empty(), b -> setPhysics(true))
				.bounds(r - 12 - BUTTON_W, physicsTop() + 2, BUTTON_W, 14).build());

		int half = (TEXT_W - 4) / 2;
		int y = optionsTop() + 12;
		mouseButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			FlightSettings.setMouseXRolls(!FlightSettings.mouseXRolls());
			refresh();
		}).bounds(l + 12, y, half, 18).build());
		invertButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			FlightSettings.setInvertRoll(!FlightSettings.invertRoll());
			refresh();
		}).bounds(l + 12 + half + 4, y, half, 18).build());
		y += 20;
		levelButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			FlightSettings.setAutoLevel(!FlightSettings.autoLevel());
			refresh();
		}).bounds(l + 12, y, half, 18).build());
		cameraButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
			FlightSettings.setCameraRoll(!FlightSettings.cameraRoll());
			refresh();
		}).bounds(l + 12 + half + 4, y, half, 18).build());
		y += 20;
		SensitivitySlider slider = addRenderableWidget(new SensitivitySlider(l + 12, y, TEXT_W, 18));
		slider.setTooltip(Tooltip.create(Component.translatable("screen.projecthero.flight_mode.sensitivity.tip")));

		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(r - 12 - 52, top() + panelH - 20, 52, 16).build());
		refresh();
	}

	private void setPhysics(boolean on) {
		FlightSettings.setPhysics(on);
		refresh();
	}

	private static Component onOff(String key, boolean on) {
		return Component.translatable(key, on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
	}

	private void refresh() {
		boolean physics = FlightSettings.physics();
		regularButton.setMessage(modeLabel(!physics));
		physicsButton.setMessage(modeLabel(physics));
		regularButton.active = physics;
		physicsButton.active = !physics;
		mouseButton.setMessage(Component.translatable(FlightSettings.mouseXRolls()
				? "screen.projecthero.flight_mode.mouse_roll" : "screen.projecthero.flight_mode.mouse_yaw"));
		mouseButton.setTooltip(Tooltip.create(Component.translatable(FlightSettings.mouseXRolls()
				? "screen.projecthero.flight_mode.mouse_roll.tip" : "screen.projecthero.flight_mode.mouse_yaw.tip")));
		invertButton.setMessage(onOff("screen.projecthero.flight_mode.invert", FlightSettings.invertRoll()));
		invertButton.setTooltip(Tooltip.create(Component.translatable("screen.projecthero.flight_mode.invert.tip")));
		levelButton.setMessage(onOff("screen.projecthero.flight_mode.level", FlightSettings.autoLevel()));
		levelButton.setTooltip(Tooltip.create(Component.translatable("screen.projecthero.flight_mode.level.tip")));
		cameraButton.setMessage(onOff("screen.projecthero.flight_mode.camera", FlightSettings.cameraRoll()));
		cameraButton.setTooltip(Tooltip.create(Component.translatable("screen.projecthero.flight_mode.camera.tip")));
	}

	private static Component modeLabel(boolean selected) {
		return selected
				? Component.translatable("screen.projecthero.flight_mode.selected").withStyle(ChatFormatting.AQUA)
				: Component.translatable("screen.projecthero.flight_mode.use");
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int l = left();
		int t = top();
		int r = l + PANEL_W;
		int b = t + panelH;
		g.fill(l - 1, t - 1, r + 1, b + 1, GOLD_DIM);
		g.fillGradient(l, t, r, b, PANEL_TOP, PANEL_BOTTOM);
		g.fill(l + 2, t + 2, r - 2, t + 3, 0x40FFFFFF);
		g.fill(l + 12, t + 19, r - 12, t + 20, GOLD_DIM);
		boolean physics = FlightSettings.physics();
		row(g, l, r, regularTop(), regularH, !physics);
		row(g, l, r, physicsTop(), physicsH, physics);
		int oy = optionsTop();
		g.fill(l + 12, oy + 9, r - 12, oy + 10, GOLD_DIM);
	}

	private static void row(GuiGraphics g, int l, int r, int y, int h, boolean on) {
		g.fill(l + 8, y, r - 8, y + h, on ? ROW_ON : ROW_OFF);
		g.fill(l + 8, y, l + 10, y + h, on ? ACTIVE : GOLD_DIM);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int l = left();
		int t = top();
		g.drawCenteredString(this.font, this.title, l + PANEL_W / 2, t + 7, GOLD);
		int y = t + 24;
		for (FormattedCharSequence line : intro) {
			g.drawString(this.font, line, l + 12, y, MUTED, false);
			y += 10;
		}
		boolean physics = FlightSettings.physics();
		modeText(g, l, regularTop(), "screen.projecthero.flight_mode.regular", regularText, !physics);
		modeText(g, l, physicsTop(), "screen.projecthero.flight_mode.physics", physicsText, physics);
		g.drawString(this.font, Component.translatable("screen.projecthero.flight_mode.options"), l + 12, optionsTop(),
				physics ? GOLD : GOLD_DIM, false);
		g.drawString(this.font, Component.translatable("screen.projecthero.flight_mode.footer"), l + 12,
				t + panelH - 16, MUTED, false);
	}

	private void modeText(GuiGraphics g, int l, int y, String key, List<FormattedCharSequence> desc, boolean on) {
		g.drawString(this.font, Component.translatable(key), l + 16, y + 5, on ? TEXT : MUTED, false);
		int dy = y + 18;
		for (FormattedCharSequence line : desc) {
			g.drawString(this.font, line, l + 16, dy, on ? 0xFFB8C2DC : 0xFF6A6F80, false);
			dy += 10;
		}
	}

	/** {@code ,} again closes it, like the inventory key closes the inventory. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.FLIGHT_MODE.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Roll sensitivity, 0.25x to 2x. */
	private static final class SensitivitySlider extends AbstractSliderButton {
		SensitivitySlider(int x, int y, int w, int h) {
			super(x, y, w, h, Component.empty(), toSlider(FlightSettings.rollSensitivity()));
			updateMessage();
		}

		private static double toSlider(float sensitivity) {
			return (sensitivity - FlightSettings.MIN_ROLL_SENSITIVITY)
					/ (FlightSettings.MAX_ROLL_SENSITIVITY - FlightSettings.MIN_ROLL_SENSITIVITY);
		}

		private float sensitivity() {
			float raw = (float) (FlightSettings.MIN_ROLL_SENSITIVITY
					+ this.value * (FlightSettings.MAX_ROLL_SENSITIVITY - FlightSettings.MIN_ROLL_SENSITIVITY));
			return Math.round(raw * 20f) / 20f;
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("screen.projecthero.flight_mode.sensitivity",
					String.format(java.util.Locale.ROOT, "%.2f", sensitivity())));
		}

		@Override
		protected void applyValue() {
			FlightSettings.setRollSensitivity(sensitivity());
		}
	}
}
