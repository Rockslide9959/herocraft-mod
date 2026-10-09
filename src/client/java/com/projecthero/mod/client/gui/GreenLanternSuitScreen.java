package com.projecthero.mod.client.gui;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLanternSuitStyle;
import com.projecthero.mod.greenlantern.data.GreenLanternState;
import com.projecthero.mod.network.GreenLanternActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.15.15, explicit user request ("players can press N to choose between different suits"): the Green Lantern's suit
 * screen. One card per {@link GreenLanternSuitStyle}, each a front-view paper doll of YOU in that suit (your own skin
 * under the suit's texture, so the bare face / hands read exactly as they will in the world). Click a card (or press
 * 1-4) to pick it: the choice is sent to the server ({@link GreenLanternActionPayload}, re-validated there), stored in the
 * synced {@link GreenLanternState#suitStyle} and -- if the suit is on -- re-formed out of the ring at once. N or Esc
 * closes. Opened with Shift+N; the Remove Ring button along the bottom (two clicks) replaced Shift + hold N. Four cards in a row, or a 2 x 2 grid when the GUI is too narrow; all prose is word-wrapped to its card.
 */
public final class GreenLanternSuitScreen extends Screen {
	private static final int GREEN = 0xFF5CFF8E;
	private static final int GREEN_DIM = 0xFF1F7A3E;
	private static final int TEXT = 0xFFE8F8EC;
	private static final int MUTED = 0xFF8DAD98;
	private static final int PANEL_TOP = 0xF20E1F15;
	private static final int PANEL_BOTTOM = 0xF2060D09;
	private static final int CARD = 0x50183A24;
	private static final int CARD_HOVER = 0x80245A36;
	private static final int CARD_ON = 0x90307A48;

	private static final int CARD_W = 72;
	private static final int GAP = 6;
	private static final int PAD = 10;
	/** v0.15.15: the Remove Ring button along the bottom (it replaced Shift + hold N). */
	private static final int BUTTON_ROW = 24;
	private Button removeButton;
	private long removeArmedUntil;

	private int cols;
	private int scale;
	private int panelW;
	private int panelH;
	private int cardH;
	private List<FormattedCharSequence> intro = List.of();
	private List<FormattedCharSequence> footer = List.of();
	private final List<List<FormattedCharSequence>> descs = new java.util.ArrayList<>();
	private final List<List<FormattedCharSequence>> names = new java.util.ArrayList<>();
	private int nameLines;

	public GreenLanternSuitScreen() {
		super(Component.translatable("screen.projecthero.green_lantern_suit.title"));
	}

	private static int current() {
		AbstractClientPlayer p = Minecraft.getInstance().player;
		GreenLanternState s = p == null ? null : p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		return s == null ? 0 : GreenLanternSuitStyle.byOrdinal(s.suitStyle).ordinal();
	}

	private boolean fits(int columns) {
		return this.width >= columns * CARD_W + (columns - 1) * GAP + 2 * PAD + 8;
	}

	@Override
	protected void init() {
		int n = GreenLanternSuitStyle.values().length;
		// as many columns as fit: all in one row, else three (two rows of the six suits), else two
		cols = fits(n) ? n : fits(3) ? 3 : 2;
		int rows = (n + cols - 1) / cols;
		panelW = cols * CARD_W + (cols - 1) * GAP + 2 * PAD;
		intro = this.font.split(Component.translatable("screen.projecthero.green_lantern_suit.intro"), panelW - 2 * PAD);
		footer = this.font.split(Component.translatable("screen.projecthero.green_lantern_suit.footer"), panelW - 2 * PAD);
		descs.clear();
		names.clear();
		int descLines = 0;
		nameLines = 1;
		for (GreenLanternSuitStyle style : GreenLanternSuitStyle.values()) {
			List<FormattedCharSequence> nm = this.font.split(Component.literal((style.ordinal() + 1) + ". ")
					.append(Component.translatable(style.nameKey())), CARD_W - 4);
			names.add(nm);
			nameLines = Math.max(nameLines, nm.size());
			List<FormattedCharSequence> d = this.font.split(Component.translatable(style.descKey()), CARD_W - 6);
			descs.add(d);
			descLines = Math.max(descLines, d.size());
		}
		// the doll is 16 x 32 skin pixels; shrink it until the whole panel fits the screen
		scale = 3;
		while (true) {
			cardH = 6 + 32 * scale + 6 + nameLines * 10 + descLines * 9 + 6;
			panelH = 28 + intro.size() * 10 + 6 + rows * cardH + (rows - 1) * GAP + 10 + footer.size() * 10 + BUTTON_ROW;
			if (panelH <= this.height - 8 || scale == 1) {
				break;
			}
			scale--;
		}
		removeArmedUntil = 0;
		int bw = Math.min(150, panelW - 2 * PAD);
		removeButton = addRenderableWidget(Button.builder(removeLabel(), b -> removeRing())
				.bounds(left() + PAD, top() + panelH - BUTTON_ROW + 2, bw, 18).build());
		removeButton.setTooltip(Tooltip.create(Component.translatable("screen.projecthero.green_lantern_suit.remove_ring.tip")));
	}

	/** First click arms it (the label asks for a second click), the second within 3 s takes the ring off. */
	private void removeRing() {
		long now = System.currentTimeMillis();
		if (now < removeArmedUntil) {
			ClientPlayNetworking.send(new GreenLanternActionPayload(GreenLanternActionPayload.Action.REMOVE_RING));
			onClose();
			return;
		}
		removeArmedUntil = now + 3000L;
		removeButton.setMessage(removeLabel());
	}

	private Component removeLabel() {
		return System.currentTimeMillis() < removeArmedUntil
				? Component.translatable("screen.projecthero.green_lantern_suit.remove_ring.confirm").withStyle(ChatFormatting.RED)
				: Component.translatable("screen.projecthero.green_lantern_suit.remove_ring").withStyle(ChatFormatting.GOLD);
	}

	private int left() {
		return (this.width - panelW) / 2;
	}

	private int top() {
		return Math.max(4, (this.height - panelH) / 2);
	}

	private int cardX(int i) {
		return left() + PAD + (i % cols) * (CARD_W + GAP);
	}

	private int cardY(int i) {
		return top() + 28 + intro.size() * 10 + 6 + (i / cols) * (cardH + GAP);
	}

	private int cardAt(double mx, double my) {
		for (int i = 0; i < GreenLanternSuitStyle.values().length; i++) {
			int x = cardX(i);
			int y = cardY(i);
			if (mx >= x && mx < x + CARD_W && my >= y && my < y + cardH) {
				return i;
			}
		}
		return -1;
	}

	private void pick(int i) {
		if (i >= 0 && i < GreenLanternSuitStyle.values().length) {
			ClientPlayNetworking.send(new GreenLanternActionPayload(GreenLanternActionPayload.Action.forSuitStyle(i)));
			onClose();
		}
	}

	@Override
	public void tick() {
		super.tick();
		AbstractClientPlayer p = Minecraft.getInstance().player;
		GreenLanternState s = p == null ? null : p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_STATE, null);
		if (s == null || !s.hasPower) {
			onClose();
			return;
		}
		if (removeButton != null) {
			removeButton.setMessage(removeLabel());
		}
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.renderBackground(g, mouseX, mouseY, partialTick);
		int l = left();
		int t = top();
		g.fill(l - 1, t - 1, l + panelW + 1, t + panelH + 1, GREEN_DIM);
		g.fillGradient(l, t, l + panelW, t + panelH, PANEL_TOP, PANEL_BOTTOM);
		g.fill(l + 2, t + 2, l + panelW - 2, t + 3, 0x40FFFFFF);
		g.fill(l + PAD, t + 22, l + panelW - PAD, t + 23, GREEN_DIM);
		int hover = cardAt(mouseX, mouseY);
		int cur = current();
		for (int i = 0; i < GreenLanternSuitStyle.values().length; i++) {
			int x = cardX(i);
			int y = cardY(i);
			g.fill(x, y, x + CARD_W, y + cardH, i == cur ? CARD_ON : i == hover ? CARD_HOVER : CARD);
			if (i == cur || i == hover) {
				int c = i == cur ? GREEN : 0xFF3FA862;
				g.fill(x, y, x + CARD_W, y + 1, c);
				g.fill(x, y + cardH - 1, x + CARD_W, y + cardH, c);
				g.fill(x, y, x + 1, y + cardH, c);
				g.fill(x + CARD_W - 1, y, x + CARD_W, y + cardH, c);
			}
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int l = left();
		int t = top();
		g.drawCenteredString(this.font, this.title, l + panelW / 2, t + 9, GREEN);
		int y = t + 28;
		for (FormattedCharSequence line : intro) {
			g.drawString(this.font, line, l + PAD, y, MUTED, false);
			y += 10;
		}
		AbstractClientPlayer player = Minecraft.getInstance().player;
		int cur = current();
		GreenLanternSuitStyle[] all = GreenLanternSuitStyle.values();
		for (int i = 0; i < all.length; i++) {
			int x = cardX(i);
			int cy = cardY(i);
			if (player != null) {
				doll(g, player.getSkin(), all[i].texture(), x + (CARD_W - 16 * scale) / 2, cy + 6, scale);
			}
			int ty = cy + 6 + 32 * scale + 6;
			for (FormattedCharSequence line : names.get(i)) {
				g.drawCenteredString(this.font, line, x + CARD_W / 2, ty, i == cur ? GREEN : TEXT);
				ty += 10;
			}
			ty = cy + 6 + 32 * scale + 6 + nameLines * 10;
			for (FormattedCharSequence line : descs.get(i)) {
				g.drawCenteredString(this.font, line, x + CARD_W / 2, ty, MUTED);
				ty += 9;
			}
		}
		int fy = t + panelH - BUTTON_ROW - 2 - footer.size() * 10;
		for (FormattedCharSequence line : footer) {
			g.drawString(this.font, line, l + PAD, fy, MUTED, false);
			fy += 10;
		}
	}

	/**
	 * A front-view paper doll, {@code s} GUI pixels per skin pixel: the player's own skin (base, then overlay) with the
	 * suit's two layers over it.
	 */
	private static void doll(GuiGraphics g, PlayerSkin skin, ResourceLocation suit, int x, int y, int s) {
		RenderSystem.enableBlend();
		boolean slim = skin.model() == PlayerSkin.Model.SLIM;
		ResourceLocation st = skin.texture();
		// base layer, then outer layer of the skin
		layer(g, st, x, y, s, slim, false);
		layer(g, st, x, y, s, slim, true);
		// the suit is always the wide-armed rig
		layer(g, suit, x, y, s, false, false);
		layer(g, suit, x, y, s, false, true);
		RenderSystem.disableBlend();
	}

	/** One layer of the front faces: head, body, both arms, both legs. {@code outer} picks the overlay UVs. */
	private static void layer(GuiGraphics g, ResourceLocation tex, int x, int y, int s, boolean slim, boolean outer) {
		int aw = slim ? 3 : 4;
		// {dollX, dollY, w, h, u(base), v(base), u(outer), v(outer)}
		int[][] parts = {
				{ 4, 0, 8, 8, 8, 8, 40, 8 },                    // head
				{ 4, 8, 8, 12, 20, 20, 20, 36 },                // body
				{ 4 - aw, 8, aw, 12, 44, 20, 44, 36 },          // right arm (viewer's left)
				{ 12, 8, aw, 12, 36, 52, 52, 52 },              // left arm (viewer's right)
				{ 4, 20, 4, 12, 4, 20, 4, 36 },                 // right leg
				{ 8, 20, 4, 12, 20, 52, 4, 52 } };              // left leg
		for (int[] p : parts) {
			int u = outer ? p[6] : p[4];
			int v = outer ? p[7] : p[5];
			g.blit(tex, x + p[0] * s, y + p[1] * s, p[2] * s, p[3] * s, u, v, p[2], p[3], 64, 64);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			int i = cardAt(mouseX, mouseY);
			if (i >= 0) {
				pick(i);
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	/** 1-4 pick a suit; N (Shift+N) again closes, like the inventory key closes the inventory. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (com.projecthero.mod.client.ModKeyBindings.MAX_STEEL_TRANSFORM.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode < org.lwjgl.glfw.GLFW.GLFW_KEY_1 + GreenLanternSuitStyle.values().length) {
			pick(keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
