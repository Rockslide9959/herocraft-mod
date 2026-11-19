package com.projecthero.mod.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.client.symbiote.SymbioteSuitTint;
import com.projecthero.mod.network.SymbioteColourPayload;
import com.projecthero.mod.symbiote.SymbioteColourPreset;
import com.projecthero.mod.symbiote.SymbioteColours;
import com.projecthero.mod.symbiote.SymbioteSuitColours;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import org.lwjgl.glfw.GLFW;

/**
 * v0.17.5 (user: "make it so that youre able to change the colour"): the Symbiote suit colour screen, opened from the power's
 * own info page ("Your Power", I key -> Suit Colours). The left side previews the pending colours live: the 3D suit while it is
 * worn, else a front and back paper doll. Apply sends {@link SymbioteColourPayload} (re-checked server side) and closes; Cancel /
 * Esc closes unchanged. Every line of prose is word-wrapped to the panel (the TooltipWrap rule) and tooltips wrap at 160 px.
 *
 * <p>v0.17.5 tendril-vfx round (user: "for symbiote customization you cant get the exact colours your want with the eyes and emblem
 * ... make sure the symbiote suit is fully customizable"): four parts -- Body, Emblem, Eyes and <b>Mouth</b> (tongue and throat) --
 * and exact input for each: the bars switch between Hue / Saturation / Brightness and <b>Red / Green / Blue</b> (0-255, the value
 * shown beside each bar), the hovered bar nudges one step with the arrow keys or the mouse wheel, the hex field takes any
 * {@code #RRGGBB}, and two chips set pure black / pure white in one click. The renderer now draws the emblem and eyes in exactly
 * the picked colour ({@code SymbioteTintMath}), so black and white really are black and white. Presets set body / emblem / eyes
 * and keep the mouth.
 */
public final class SymbioteColourScreen extends Screen {
	/** While rendering the 3D preview, the local player's suit is drawn in these colours ({@link SymbioteSuitTint#suit}). */
	public static SymbioteColours previewOverride;

	private static final int ACCENT = 0xFFE8ECF4;
	private static final int ACCENT_DIM = 0xFF3A3F4C;
	private static final int MUTED = 0xFF9AA0AE;
	private static final int PANEL_TOP = 0xF2101218;
	private static final int PANEL_BOTTOM = 0xF207080B;
	private static final int BAR_H = 9;
	private static final int BAR_STEP = 19;
	private static final int SWATCH = 18;
	private static final int CHIP = 12;
	private static final String[] PARTS = { "body", "emblem", "eyes", "mouth" };

	/** Remembered between openings (a view preference only). */
	private static boolean rgbMode;

	private final Screen parent;
	private SymbioteColours pending;
	private SymbioteColours shown;
	private long shownAt;
	private int channel;
	private float hue, sat, val;
	private int dragging = -1;
	private boolean syncingHex;

	private int panelW, panelH, left, top, dollScale, dollW, rightX, rightW;
	private int barsTop, hexY, presetsY, presetCols, chipsX;
	private final List<FormattedCharSequence> hint = new ArrayList<>();
	private final Button[] tabs = new Button[PARTS.length];
	private Button modeButton;
	private EditBox hexBox;

	public SymbioteColourScreen(Screen parent) {
		super(Component.translatable("screen.projecthero.symbiote_colours"));
		this.parent = parent;
		Player p = Minecraft.getInstance().player;
		this.pending = p == null ? SymbioteColours.CLASSIC : SymbioteSuitColours.get(p);
		this.shown = pending;
		loadChannel();
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		panelW = Math.min(this.width - 16, 400);
		panelH = Math.min(this.height - 44, 260);
		left = (this.width - panelW) / 2;
		top = Math.max(8, (this.height - 36 - panelH) / 2);
		// the preview column: two dolls (16 x 32 skin px each) or the 3D figure, sized to the panel
		dollScale = Mth.clamp((panelH - 60) / 34, 2, 4);
		dollW = 16 * dollScale * 2 + 10;
		rightX = left + 12 + dollW + 12;
		rightW = left + panelW - 10 - rightX;
		while (rightW < 150 && dollScale > 1) {
			dollScale--;
			dollW = 16 * dollScale * 2 + 10;
			rightX = left + 12 + dollW + 12;
			rightW = left + panelW - 10 - rightX;
		}
		hint.clear();
		hint.addAll(this.font.split(Component.translatable("screen.projecthero.symbiote_colours.hint"), rightW));
		int y = top + 24;
		int tabW = (rightW - 2 * (PARTS.length - 1)) / PARTS.length;
		for (int i = 0; i < PARTS.length; i++) {
			final int ch = i;
			Component label = Component.translatable("screen.projecthero.symbiote_colours." + PARTS[i]);
			Button b = Button.builder(label, btn -> selectChannel(ch)).bounds(rightX + i * (tabW + 2), y, tabW, 18)
					.tooltip(net.minecraft.client.gui.components.Tooltip.create(
							Component.translatable("screen.projecthero.symbiote_colours." + PARTS[i] + ".tip")))
					.build();
			tabs[i] = addRenderableWidget(b);
		}
		barsTop = y + 22;
		hexY = barsTop + 3 * BAR_STEP + 1;
		int hexW = Math.min(64, rightW - 22);
		hexBox = new EditBox(this.font, rightX + 20, hexY, hexW, 16, Component.translatable("screen.projecthero.symbiote_colours.hex"));
		hexBox.setMaxLength(7);
		hexBox.setResponder(this::hexTyped);
		addRenderableWidget(hexBox);
		int modeX = rightX + 20 + hexW + 4;
		modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> toggleMode()).bounds(modeX, hexY - 1, 30, 18)
				.tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.projecthero.symbiote_colours.mode.tip")))
				.build());
		chipsX = modeX + 34;
		presetsY = hexY + 28;
		presetCols = Math.max(1, Math.min(SymbioteColourPreset.values().length, (rightW + 4) / (SWATCH + 4)));
		int by = this.height - 28;
		int bw = Math.min(110, (panelW - 8) / 3);
		int bx = this.width / 2 - (bw * 3 + 8) / 2;
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.symbiote_colours.apply"), b -> apply())
				.bounds(bx, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("screen.projecthero.symbiote_colours.reset"),
				b -> setAll(SymbioteColours.CLASSIC))
				.bounds(bx + bw + 4, by, bw, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
				.bounds(bx + 2 * (bw + 4), by, bw, 20).build());
		refreshWidgets();
	}

	private Component modeLabel() {
		return Component.translatable(rgbMode ? "screen.projecthero.symbiote_colours.mode.rgb" : "screen.projecthero.symbiote_colours.mode.hsb");
	}

	private void refreshWidgets() {
		for (int i = 0; i < PARTS.length; i++) {
			if (tabs[i] != null) {
				tabs[i].active = i != channel;
			}
		}
		if (hexBox != null) {
			syncingHex = true;
			hexBox.setValue(SymbioteColours.hex(pending.channel(channel)));
			syncingHex = false;
		}
		if (modeButton != null) {
			modeButton.setMessage(modeLabel());
		}
	}

	// ------------------------------------------------------------------ state

	private void selectChannel(int ch) {
		channel = ch;
		loadChannel();
		refreshWidgets();
	}

	private void loadChannel() {
		float[] hsv = rgbToHsv(pending.channel(channel));
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
	}

	private void setAll(SymbioteColours c) {
		pending = c;
		loadChannel();
		refreshWidgets();
	}

	private void setChannel(int rgb) {
		pending = pending.withChannel(channel, rgb & 0xFFFFFF);
		loadChannel();
		refreshWidgets();
	}

	private void toggleMode() {
		rgbMode = !rgbMode;
		refreshWidgets();
	}

	private void slidersChanged() {
		pending = pending.withChannel(channel, Mth.hsvToRgb(Mth.clamp(hue, 0f, 0.9999f), sat, val) & 0xFFFFFF);
		refreshWidgets();
	}

	private void hexTyped(String text) {
		if (syncingHex) {
			return;
		}
		SymbioteColours.parseHex(text).ifPresent(rgb -> {
			pending = pending.withChannel(channel, rgb);
			loadChannel();
		});
	}

	private void apply() {
		Player p = Minecraft.getInstance().player;
		if (p != null) {
			ClientPlayNetworking.send(new SymbioteColourPayload(pending));
		}
		onClose();
	}

	@Override
	public void onClose() {
		previewOverride = null;
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ the bars (HSB or RGB)

	/** Bar {@code bar}'s value, 0..1. */
	private float barValue(int bar) {
		if (rgbMode) {
			return ((pending.channel(channel) >> (16 - 8 * bar)) & 0xFF) / 255f;
		}
		return bar == 0 ? hue : bar == 1 ? sat : val;
	}

	/** Bar {@code bar}'s value as the text beside its label (Hue in degrees, S / B in percent, R / G / B 0-255). */
	private String barText(int bar) {
		if (rgbMode) {
			return Integer.toString((pending.channel(channel) >> (16 - 8 * bar)) & 0xFF);
		}
		return bar == 0 ? Math.round(hue * 360f) % 360 + "°" : Math.round((bar == 1 ? sat : val) * 100f) + "%";
	}

	private void setBar(int bar, float t) {
		t = Mth.clamp(t, 0f, 1f);
		if (rgbMode) {
			int shift = 16 - 8 * bar;
			int rgb = pending.channel(channel) & ~(0xFF << shift) | Math.round(t * 255f) << shift;
			setChannel(rgb);
			return;
		}
		if (bar == 0) {
			hue = t;
		} else if (bar == 1) {
			sat = t;
		} else {
			val = t;
		}
		slidersChanged();
	}

	/** One exact step on bar {@code bar}: 1 of 255 in RGB, 1 degree / 1% in HSB. */
	private void nudge(int bar, int dir) {
		float step = rgbMode ? 1f / 255f : bar == 0 ? 1f / 360f : 0.01f;
		setBar(bar, barValue(bar) + dir * step);
	}

	// ------------------------------------------------------------------ input

	private int barAt(double mx, double my) {
		for (int i = 0; i < 3; i++) {
			int by = barsTop + i * BAR_STEP + 9;
			if (mx >= rightX - 2 && mx <= rightX + rightW + 2 && my >= by - 2 && my <= by + BAR_H + 2) {
				return i;
			}
		}
		return -1;
	}

	private void dragBar(int bar, double mx) {
		setBar(bar, (float) ((mx - rightX) / (double) Math.max(1, rightW - 1)));
	}

	private int presetAt(double mx, double my) {
		SymbioteColourPreset[] all = SymbioteColourPreset.values();
		for (int i = 0; i < all.length; i++) {
			int x = rightX + (i % presetCols) * (SWATCH + 4);
			int y = presetsY + (i / presetCols) * (SWATCH + 4);
			if (mx >= x && mx < x + SWATCH && my >= y && my < y + SWATCH) {
				return i;
			}
		}
		return -1;
	}

	/** The black (0) / white (1) chip under the mouse, or -1 (only when both fit beside the hex field). */
	private int chipAt(double mx, double my) {
		if (chipsX + 2 * CHIP + 4 > rightX + rightW) {
			return -1;
		}
		for (int i = 0; i < 2; i++) {
			int x = chipsX + i * (CHIP + 4);
			if (mx >= x && mx < x + CHIP && my >= hexY + 2 && my < hexY + 2 + CHIP) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			int bar = barAt(mx, my);
			if (bar >= 0) {
				dragging = bar;
				dragBar(bar, mx);
				return true;
			}
			int preset = presetAt(mx, my);
			if (preset >= 0) {
				setAll(SymbioteColourPreset.values()[preset].colours().withMouth(pending.mouth()));
				return true;
			}
			int chip = chipAt(mx, my);
			if (chip >= 0) {
				setChannel(chip == 0 ? 0x000000 : 0xFFFFFF);
				return true;
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
		if (dragging >= 0) {
			dragBar(dragging, mx);
			return true;
		}
		return super.mouseDragged(mx, my, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mx, double my, int button) {
		dragging = -1;
		return super.mouseReleased(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
		int bar = barAt(mx, my);
		if (bar >= 0 && scrollY != 0) {
			nudge(bar, scrollY > 0 ? 1 : -1);
			return true;
		}
		return super.mouseScrolled(mx, my, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(int key, int scan, int modifiers) {
		if ((key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) && (hexBox == null || !hexBox.isFocused())) {
			Minecraft mc = Minecraft.getInstance();
			double mx = mc.mouseHandler.xpos() * this.width / mc.getWindow().getScreenWidth();
			double my = mc.mouseHandler.ypos() * this.height / mc.getWindow().getScreenHeight();
			int bar = barAt(mx, my);
			if (bar >= 0) {
				nudge(bar, key == GLFW.GLFW_KEY_RIGHT ? 1 : -1);
				return true;
			}
		}
		return super.keyPressed(key, scan, modifiers);
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, ACCENT_DIM);
		g.fillGradient(left, top, left + panelW, top + panelH, PANEL_TOP, PANEL_BOTTOM);
		g.fill(left + 2, top + 2, left + panelW - 2, top + 3, 0x30FFFFFF);
		g.fill(left + 10, top + 19, left + panelW - 10, top + 20, ACCENT_DIM);
		// the preview well
		g.fill(left + 10, top + 24, left + 12 + dollW, top + panelH - 10, 0x40000000);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(this.font, this.title, left + panelW / 2, top + 6, ACCENT);
		// throttle the live re-bake while a bar is dragged (each new colour is a new cached texture)
		long now = net.minecraft.Util.getMillis();
		if (!shown.equals(pending) && now - shownAt > 90) {
			shown = pending;
			shownAt = now;
		}
		drawPreview(g, mouseX, mouseY);
		// the three bars, each label with its exact value on the right
		String[] labels = rgbMode ? new String[] { "red", "green", "blue" } : new String[] { "hue", "saturation", "brightness" };
		for (int i = 0; i < 3; i++) {
			int by = barsTop + i * BAR_STEP;
			g.drawString(this.font, Component.translatable("screen.projecthero.symbiote_colours." + labels[i]), rightX, by, MUTED, false);
			String v = barText(i);
			g.drawString(this.font, v, rightX + rightW - this.font.width(v), by, ACCENT, false);
			drawBar(g, i, rightX, by + 9, rightW);
			int hx = rightX + Math.round(barValue(i) * (rightW - 1));
			g.fill(hx - 2, by + 7, hx + 3, by + 9 + BAR_H + 2, 0xFF000000);
			g.fill(hx - 1, by + 8, hx + 2, by + 9 + BAR_H + 1, 0xFFFFFFFF);
		}
		// the current channel's swatch beside the hex field
		int cur = pending.channel(channel);
		g.fill(rightX, hexY, rightX + 16, hexY + 16, 0xFF5A6070);
		g.fill(rightX + 1, hexY + 1, rightX + 15, hexY + 15, 0xFF000000 | cur);
		// the black / white chips
		int chipHover = chipAt(mouseX, mouseY);
		if (chipsX + 2 * CHIP + 4 <= rightX + rightW) {
			for (int i = 0; i < 2; i++) {
				int x = chipsX + i * (CHIP + 4);
				g.fill(x - 1, hexY + 1, x + CHIP + 1, hexY + 3 + CHIP, chipHover == i ? 0xFFFFFFFF : 0xFF5A6070);
				g.fill(x, hexY + 2, x + CHIP, hexY + 2 + CHIP, i == 0 ? 0xFF000000 : 0xFFFFFFFF);
			}
		}
		// presets
		g.drawString(this.font, Component.translatable("screen.projecthero.symbiote_colours.presets"), rightX, presetsY - 11, MUTED, false);
		SymbioteColourPreset[] all = SymbioteColourPreset.values();
		int hover = presetAt(mouseX, mouseY);
		SymbioteColourPreset match = SymbioteColourPreset.matching(pending).orElse(null);
		for (int i = 0; i < all.length; i++) {
			int x = rightX + (i % presetCols) * (SWATCH + 4);
			int y = presetsY + (i / presetCols) * (SWATCH + 4);
			SymbioteColours c = all[i].colours();
			int border = all[i] == match ? 0xFFFFFFFF : i == hover ? 0xFFB0B6C4 : 0xFF000000;
			g.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, border);
			g.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | c.primary());
			g.fill(x + 5, y + 5, x + SWATCH - 5, y + SWATCH - 5, 0xFF000000 | c.secondary());
			g.fill(x + 2, y + 2, x + 6, y + 4, 0xFF000000 | c.eyes());
			g.fill(x + SWATCH - 6, y + 2, x + SWATCH - 2, y + 4, 0xFF000000 | c.eyes());
		}
		// the hint under the presets, wrapped to the column
		int rows = (all.length + presetCols - 1) / presetCols;
		int ty = presetsY + rows * (SWATCH + 4) + 4;
		for (FormattedCharSequence line : hint) {
			if (ty + 9 > top + panelH - 4) {
				break;
			}
			g.drawString(this.font, line, rightX, ty, MUTED, false);
			ty += 10;
		}
		if (hover >= 0) {
			g.renderTooltip(this.font, this.font.split(Component.translatable(all[hover].langKey()), 160), mouseX, mouseY);
		} else if (chipHover >= 0) {
			g.renderTooltip(this.font, this.font.split(Component.translatable(chipHover == 0
					? "screen.projecthero.symbiote_colours.black" : "screen.projecthero.symbiote_colours.white"), 160), mouseX, mouseY);
		} else if (barAt(mouseX, mouseY) >= 0 && dragging < 0) {
			g.renderTooltip(this.font, this.font.split(Component.translatable("screen.projecthero.symbiote_colours.bar.tip"), 160), mouseX, mouseY);
		}
	}

	private void drawBar(GuiGraphics g, int bar, int x, int y, int w) {
		g.fill(x - 1, y - 1, x + w + 1, y + BAR_H + 1, 0xFF000000);
		int cur = pending.channel(channel);
		for (int i = 0; i < w; i++) {
			float t = i / (float) Math.max(1, w - 1);
			int rgb;
			if (rgbMode) {
				int shift = 16 - 8 * bar;
				rgb = cur & ~(0xFF << shift) | Math.round(t * 255f) << shift;
			} else {
				rgb = switch (bar) {
					case 0 -> Mth.hsvToRgb(Math.min(t, 0.9999f), 1f, 1f);
					case 1 -> Mth.hsvToRgb(Mth.clamp(hue, 0f, 0.9999f), t, Math.max(val, 0.15f));
					default -> Mth.hsvToRgb(Mth.clamp(hue, 0f, 0.9999f), sat, t);
				};
			}
			g.fill(x + i, y, x + i + 1, y + BAR_H, 0xFF000000 | (rgb & 0xFFFFFF));
		}
	}

	private void drawPreview(GuiGraphics g, int mouseX, int mouseY) {
		int x0 = left + 12;
		int y0 = top + 28;
		int x1 = left + 10 + dollW;
		int y1 = top + panelH - 12;
		Player p = Minecraft.getInstance().player;
		boolean worn = p != null && p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof com.projecthero.mod.symbiote.item.SymbioteHostArmorItem;
		if (worn) {
			// the real suit, in the pending colours, following the mouse
			previewOverride = shown;
			try {
				int size = (int) ((y1 - y0) / (2.2f * Math.max(1f, p.getScale())));
				InventoryScreen.renderEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, Math.max(10, size), 0.0625f, mouseX, mouseY, p);
			} finally {
				previewOverride = null;
			}
			return;
		}
		ResourceLocation tex = SymbioteSuitTint.suit(shown);
		int s = dollScale;
		int dy = y0 + Math.max(0, (y1 - y0 - 32 * s) / 2);
		doll(g, tex, x0, dy, s, false);
		doll(g, tex, x0 + 16 * s + 10, dy, s, true);
	}

	/** A flat paper doll of the suit, front or back, {@code s} GUI px per skin px (the texture is HD; UVs are in 64 space). */
	private static void doll(GuiGraphics g, ResourceLocation tex, int x, int y, int s, boolean back) {
		RenderSystem.enableBlend();
		// {dollX, dollY, w, h, u, v, uShell, vShell}
		int[][] front = {
				{ 4, 0, 8, 8, 8, 8, 40, 8 }, { 4, 8, 8, 12, 20, 20, 20, 36 },
				{ 0, 8, 4, 12, 44, 20, 44, 36 }, { 12, 8, 4, 12, 36, 52, 52, 52 },
				{ 4, 20, 4, 12, 4, 20, 4, 36 }, { 8, 20, 4, 12, 20, 52, 4, 52 } };
		int[][] rear = {
				{ 4, 0, 8, 8, 24, 8, 56, 8 }, { 4, 8, 8, 12, 32, 20, 32, 36 },
				{ 12, 8, 4, 12, 52, 20, 52, 36 }, { 0, 8, 4, 12, 44, 52, 60, 52 },
				{ 8, 20, 4, 12, 12, 20, 12, 36 }, { 4, 20, 4, 12, 28, 52, 12, 52 } };
		for (int layer = 0; layer < 2; layer++) {
			for (int[] p : back ? rear : front) {
				int u = layer == 0 ? p[4] : p[6];
				int v = layer == 0 ? p[5] : p[7];
				g.blit(tex, x + p[0] * s, y + p[1] * s, p[2] * s, p[3] * s, u, v, p[2], p[3], 64, 64);
			}
		}
		RenderSystem.disableBlend();
	}

	// ------------------------------------------------------------------ colour maths

	/** {@code 0xRRGGBB} -> {hue, saturation, value}, each 0..1. */
	static float[] rgbToHsv(int rgb) {
		float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		float h;
		if (d < 1e-6f) {
			h = 0f;
		} else if (max == r) {
			h = ((g - b) / d) / 6f;
		} else if (max == g) {
			h = ((b - r) / d + 2f) / 6f;
		} else {
			h = ((r - g) / d + 4f) / 6f;
		}
		if (h < 0f) {
			h += 1f;
		}
		return new float[] { h, max < 1e-6f ? 0f : d / max, max };
	}
}
