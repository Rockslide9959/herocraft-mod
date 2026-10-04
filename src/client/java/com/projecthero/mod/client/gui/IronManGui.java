package com.projecthero.mod.client.gui;

import java.lang.ref.WeakReference;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ironman.ui.IronManUiLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.21: the shared drawing kit for every Iron Man screen and the helmet HUD -- one Stark palette, the user's
 * bar vocabulary (Slab / Gauge / Hairline, see {@code reference_hud_bar_names}), panels with corner brackets,
 * status chips, ring sectors for the weapon wheel, a styled button, and the rotating 3D suit preview (an
 * invisible client-only armour stand wearing the pieces, the same trick the Suit Platform's block renderer
 * uses). Layout numbers live in the common {@link IronManUiLayout} so gametests can check them.
 */
public final class IronManGui {
	// ---- Stark holo palette
	public static final int CYAN = 0xFF7FE9FF;
	public static final int CYAN_DIM = 0xFF3F8FA8;
	public static final int CYAN_FAINT = 0x5537C6E8;
	public static final int GOLD = 0xFFFFC24A;
	public static final int RED = 0xFFFF5555;
	public static final int ORANGE = 0xFFFF9A3C;
	public static final int GREEN = 0xFF66E0A0;
	public static final int BLUE = 0xFF6FA8FF;
	public static final int TEXT = 0xFFDCE6F5;
	public static final int TEXT_DIM = 0xFF9AA6C0;
	public static final int TEXT_MUTED = 0xFF5E6880;
	public static final int PANEL_BG = 0xE00B1018;
	public static final int PANEL_BG_SOFT = 0xA00B1018;
	public static final int PANEL_BORDER = 0xFF29445A;
	public static final int WELL_BG = 0xFF070A10;
	public static final int BAR_BG = 0xC0060A10;
	// ---- Mark I retro palette (amber monochrome)
	public static final int AMBER = 0xFFFFB347;
	public static final int AMBER_DIM = 0xFF8A5A20;
	public static final int AMBER_BG = 0xC0140C04;

	private IronManGui() {
	}

	public static int alpha(int argb, float a) {
		int al = Math.round(((argb >>> 24) & 0xFF) * IronManUiLayout.clamp01(a));
		return (al << 24) | (argb & 0x00FFFFFF);
	}

	public static int lerp(int a, int b, float t) {
		t = IronManUiLayout.clamp01(t);
		int out = 0;
		for (int sh = 0; sh <= 24; sh += 8) {
			int ca = (a >>> sh) & 0xFF;
			int cb = (b >>> sh) & 0xFF;
			out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << sh;
		}
		return out;
	}

	/** {@code s} cut to {@code maxW} px of the real font, with "...". */
	public static String fit(Font font, String s, int maxW) {
		return IronManUiLayout.ellipsize(s, maxW, font::width);
	}

	public static String fit(Font font, Component c, int maxW) {
		return fit(font, c.getString(), maxW);
	}

	// ------------------------------------------------------------------ panels

	/** A dark panel with a 1 px border and short bright corner brackets. */
	public static void panel(GuiGraphics g, int x, int y, int w, int h, int bg, int border, int bracket) {
		g.fill(x, y, x + w, y + h, bg);
		g.renderOutline(x, y, w, h, border);
		brackets(g, x, y, w, h, Math.min(8, Math.min(w, h) / 3), bracket);
	}

	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		panel(g, x, y, w, h, PANEL_BG, PANEL_BORDER, CYAN_DIM);
	}

	/** L-shaped corner brackets of arm length {@code len}. */
	public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int c) {
		g.fill(x, y, x + len, y + 1, c);
		g.fill(x, y, x + 1, y + len, c);
		g.fill(x + w - len, y, x + w, y + 1, c);
		g.fill(x + w - 1, y, x + w, y + len, c);
		g.fill(x, y + h - 1, x + len, y + h, c);
		g.fill(x, y + h - len, x + 1, y + h, c);
		g.fill(x + w - len, y + h - 1, x + w, y + h, c);
		g.fill(x + w - 1, y + h - len, x + w, y + h, c);
	}

	/** A sunken 1 px-bordered well (preview boxes, slots drawn in code). */
	public static void well(GuiGraphics g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, WELL_BG);
		g.fill(x, y, x + w, y + 1, 0xFF03050A);
		g.fill(x, y, x + 1, y + h, 0xFF03050A);
		g.fill(x, y + h - 1, x + w, y + h, 0xFF2A3A4C);
		g.fill(x + w - 1, y, x + w, y + h, 0xFF2A3A4C);
	}

	// ------------------------------------------------------------------ bars (the user's vocabulary)

	/**
	 * Gauge bar: label (left) and value (right, ellipsized against the label) on the 9 px line, a 3 px bar with
	 * a 1 px border under it. Total height {@link IronManUiLayout#GAUGE_ROW_H} - 2.
	 */
	public static void gauge(GuiGraphics g, Font font, int x, int y, int w, String label, int labelColor, String value,
			int valueColor, float frac, int fill) {
		g.drawString(font, label, x, y, labelColor, false);
		int lw = font.width(label) + 6;
		String v = fit(font, value, Math.max(0, w - lw));
		g.drawString(font, v, x + w - font.width(v), y, valueColor, false);
		gaugeBar(g, x, y + 10, w, frac, fill, BAR_BG, PANEL_BORDER);
	}

	public static void gaugeBar(GuiGraphics g, int x, int y, int w, float frac, int fill, int bg, int border) {
		g.fill(x - 1, y - 1, x + w + 1, y + 4, border);
		g.fill(x, y, x + w, y + 3, bg);
		int fw = Math.round(w * IronManUiLayout.clamp01(frac));
		g.fill(x, y, x + fw, y + 3, fill);
		if (fw > 1) {
			g.fill(x + fw - 1, y, x + fw, y + 3, 0xFFFFFFFF & (fill | 0x60606060)); // bright leading edge
		}
	}

	/** Segmented Gauge for the Mark I retro HUD: ten amber cells. */
	public static void cells(GuiGraphics g, int x, int y, int w, float frac, int on, int off) {
		int n = 10;
		int cw = (w - (n - 1)) / n;
		int lit = (int) Math.ceil(IronManUiLayout.clamp01(frac) * n - 1e-4);
		for (int c = 0; c < n; c++) {
			int cx = x + c * (cw + 1);
			g.fill(cx, y, cx + cw, y + 4, c < lit ? on : off);
		}
	}

	/** Slab bar: 9 px tall, 1 px border, the text centred INSIDE it. */
	public static void slab(GuiGraphics g, Font font, int x, int y, int w, float frac, int fill, String text) {
		g.fill(x - 1, y - 1, x + w + 1, y + 10, PANEL_BORDER);
		g.fill(x, y, x + w, y + 9, BAR_BG);
		int fw = Math.round(w * IronManUiLayout.clamp01(frac));
		g.fill(x, y, x + fw, y + 9, alpha(fill, 0.72f));
		g.fill(x, y, x + fw, y + 1, alpha(0xFFFFFFFF, 0.25f));
		if (text != null && !text.isEmpty()) {
			String t = fit(font, text, w - 4);
			g.drawString(font, t, x + (w - font.width(t)) / 2, y + 1, 0xFFFFFFFF, true);
		}
	}

	/** Hairline bar: 2-3 px, no border, no text. */
	public static void hairline(GuiGraphics g, int x, int y, int w, int h, float frac, int fill, int bg) {
		g.fill(x, y, x + w, y + h, bg);
		g.fill(x, y, x + Math.round(w * IronManUiLayout.clamp01(frac)), y + h, fill);
	}

	/** A status chip: an 11 px pill with a coloured left tick. Returns its width. */
	public static int chip(GuiGraphics g, Font font, int x, int y, int maxW, String text, int color) {
		String t = fit(font, text, maxW - 7);
		int w = font.width(t) + 7;
		g.fill(x, y, x + w, y + 11, 0xB0081018);
		g.fill(x, y, x + 2, y + 11, color);
		g.fill(x + 2, y + 10, x + w, y + 11, alpha(color, 0.35f));
		g.drawString(font, t, x + 4, y + 2, color, false);
		return w;
	}

	public static int chipWidth(Font font, String text, int maxW) {
		return Math.min(maxW, font.width(text) + 7);
	}

	// ------------------------------------------------------------------ ring sectors (weapon wheel)

	/** An annular sector between radii r0..r1 and angles a0..a1 (radians, screen space). Same winding as PowerWheelScreen. */
	public static void sector(VertexConsumer vc, Matrix4f m, float cx, float cy, float r0, float r1, double a0, double a1,
			int argb) {
		int steps = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI / 72)));
		for (int k = 0; k < steps; k++) {
			double t0 = a0 + (a1 - a0) * k / steps;
			double t1 = a0 + (a1 - a0) * (k + 1) / steps;
			float c0 = (float) Math.cos(t0);
			float s0 = (float) Math.sin(t0);
			float c1 = (float) Math.cos(t1);
			float s1 = (float) Math.sin(t1);
			vc.addVertex(m, cx + c0 * r1, cy + s0 * r1, 0).setColor(argb);
			vc.addVertex(m, cx + c0 * r0, cy + s0 * r0, 0).setColor(argb);
			vc.addVertex(m, cx + c1 * r0, cy + s1 * r0, 0).setColor(argb);
			vc.addVertex(m, cx + c1 * r1, cy + s1 * r1, 0).setColor(argb);
		}
	}

	public static VertexConsumer guiBuffer(GuiGraphics g) {
		return g.bufferSource().getBuffer(RenderType.gui());
	}

	// ------------------------------------------------------------------ 3D suit preview

	private static WeakReference<ArmorStand> standRef = new WeakReference<>(null);
	private static final EquipmentSlot[] ARMOR = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	private static ArmorStand stand() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		ArmorStand s = standRef.get();
		if (s == null || s.level() != level) {
			s = new ArmorStand(level, 0, 0, 0);
			s.setInvisible(true); // only the armour shows
			s.setNoBasePlate(true);
			s.setShowArms(true);
			s.setNoGravity(true);
			standRef = new WeakReference<>(s);
		}
		return s;
	}

	/** Pieces of {@code suitId} in helmet / chest / legs / boots order (empty stacks for an unknown suit). */
	public static ItemStack[] suitPieces(String suitId) {
		ItemStack[] out = { ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY };
		com.projecthero.mod.ironman.suit.IronManSuit suit = com.projecthero.mod.ironman.suit.IronManSuits.byId(suitId);
		if (suit == null) {
			return out;
		}
		net.minecraft.world.item.ArmorItem.Type[] types = {
				net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
				net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS };
		for (int i = 0; i < 4; i++) {
			var item = suit.armor(types[i]);
			out[i] = item == null ? ItemStack.EMPTY : new ItemStack(item);
		}
		return out;
	}

	/**
	 * Draws the four {@code pieces} on an invisible armour stand, standing in the box (x0,y0)-(x1,y1), turned to
	 * {@code yawDeg}. Scissored to the box. Never throws: a renderer failure just leaves the box empty.
	 */
	public static void suitPreview(GuiGraphics g, int x0, int y0, int x1, int y1, ItemStack[] pieces, float yawDeg) {
		ArmorStand s = stand();
		if (s == null) {
			return;
		}
		boolean any = false;
		for (int i = 0; i < 4; i++) {
			ItemStack p = i < pieces.length && pieces[i] != null ? pieces[i] : ItemStack.EMPTY;
			s.setItemSlot(ARMOR[i], p);
			any |= !p.isEmpty();
		}
		if (!any) {
			return;
		}
		s.setYRot(yawDeg);
		s.yRotO = yawDeg;
		s.yBodyRot = yawDeg;
		s.yBodyRotO = yawDeg;
		s.yHeadRot = yawDeg;
		s.yHeadRotO = yawDeg;
		s.setXRot(0f);
		float h = s.getBbHeight();
		float scale = (y1 - y0) * 0.86f / Math.max(0.5f, h);
		float cx = (x0 + x1) / 2f;
		float cy = (y0 + y1) / 2f;
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf cam = new Quaternionf().rotateX((float) Math.toRadians(-8));
		pose.mul(cam);
		g.enableScissor(x0, y0, x1, y1);
		try {
			InventoryScreen.renderEntityInInventory(g, cx, cy, scale, new Vector3f(0f, h / 2f, 0f), pose, cam, s);
		} catch (RuntimeException ignored) {
			// a third-party armour renderer misbehaving must never take the screen down with it
		}
		g.disableScissor();
	}

	/** A slow turntable angle for previews: 180 = facing the viewer, swinging +-35 degrees. */
	public static float turntable(float seconds, float phase) {
		return 180f + 35f * (float) Math.sin(seconds * 0.9f + phase);
	}

	// ------------------------------------------------------------------ styled button

	/** A flat Stark-style button: dark fill, cyan rim on hover/focus, gold label when highlighted, greyed when inactive. */
	public static class StarkButton extends AbstractButton {
		private final Runnable action;
		public boolean highlighted;
		public int accent = CYAN;

		public StarkButton(int x, int y, int w, int h, Component label, Runnable action) {
			super(x, y, w, h, label);
			this.action = action;
		}

		@Override
		public void onPress() {
			action.run();
		}

		@Override
		protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
			Font font = Minecraft.getInstance().font;
			int x = getX();
			int y = getY();
			boolean hot = active && isHoveredOrFocused();
			int bg = !active ? 0xC0101318 : hot ? 0xF0183044 : 0xE0101A26;
			g.fill(x, y, x + width, y + height, bg);
			g.renderOutline(x, y, width, height, !active ? 0xFF262C36 : hot ? accent : PANEL_BORDER);
			if (active) {
				g.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, alpha(accent, hot ? 0.9f : 0.35f));
			}
			String t = fit(font, getMessage(), width - 6);
			int col = !active ? TEXT_MUTED : highlighted ? GOLD : hot ? 0xFFFFFFFF : TEXT;
			g.drawString(font, t, x + (width - font.width(t)) / 2, y + (height - 8) / 2, col, false);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput out) {
			defaultButtonNarrationText(out);
		}
	}
}
