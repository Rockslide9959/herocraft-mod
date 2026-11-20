package com.projecthero.mod.client.symbiote;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.PowerWheelScreen;
import com.projecthero.mod.client.gui.TooltipWrap;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.network.SymbioteKitPayload;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.5 Symbiote moveset v3 (user: "holding V for 0.5 seconds open a weapon wheel where you can choose what form you want to be in"):
 * the <b>Form wheel</b>. Every Form of V's cycle (Normal, Blade, Axe, Mace, Whip, Thorns) round a black ring in the Symbiote's purple
 * (the power wheel's ring geometry, {@link PowerWheelScreen#band}, as Beast Boy's Animal Wheel); each slice a small drawn glyph and the
 * name. Point at one and let go of V (or click) to take that Form; let go over the centre to keep the current one. Escape closes without a
 * pick. The server re-checks the Form rules (a weapon needs an empty main hand and Biomass). Does not pause the game.
 */
public final class SymbioteFormWheelScreen extends Screen {
	private static final int DIM_TOP = 0x88000000;
	private static final int DIM_BOTTOM = 0xC0000000;
	private static final int SEG_FILL = 0xF0100C16;
	private static final int SEG_FILL_ALT = 0xF015101C;
	private static final int SEG_FILL_HOVER = 0xF83A2460;
	private static final int TRACK = 0xD006040A;
	private static final int PURPLE = 0xFFB090F0;
	private static final int PURPLE_DIM = 0xFF4A3470;
	private static final int CENTRE_FILL = 0xF008060C;
	private static final int TEXT_DIM = 0xFF9A90B0;
	private static final int DEAD_ZONE = 22;
	private static final float OPEN_TICKS = 3f;

	/** The wheel's Forms, clockwise from the top (V's cycle order). */
	private static final int[] FORMS = SymbioteVitalsManager.FORM_CYCLE;

	/** Each Form's glyph, 9 x 9 ('#' = a pixel). */
	private static final String[][] GLYPHS = {
			{ // Normal: the Symbiote's head, white eyes
					"..#####..", ".#######.", "##.###.##", "#...#...#", "##.###.##", ".#######.", "..#####..", "...###...", "....#...." },
			{ // Blade: the crescent scythe
					".###.....", "...##....", "....##...", "....###..", "....###..", "....##...", "...##....", ".###.....", "##......." },
			{ // Axe: a bearded head on a haft
					"..####...", ".######..", "########.", ".#####.#.", "..##...#.", "......#..", ".....#...", "....#....", "...#....." },
			{ // Mace: a spiked ball
					"....#....", "..#.#.#..", "...###...", ".#######.", "#########", ".#######.", "...###...", "..#.#.#..", "....#...." },
			{ // Whip: a lashing curl
					"....####.", "...#....#", "...#.....", "....#....", ".....##..", ".......#.", ".......#.", "......#..", "#####...." },
			{ // Thorns: spikes bursting out
					"#...#...#", ".#..#..#.", "..#.#.#..", "...###...", "#########", "...###...", "..#.#.#..", ".#..#..#.", "#...#...#" },
	};

	private int hovered = -1;
	private float[] hoverAnim = new float[FORMS.length];
	private long lastFrameNanos = 0L;
	private int ticksOpen = 0;
	private boolean held = false;
	private boolean picked = false;
	/** Set only by the client screenshot harness: the wheel ignores the V key (stays open until closed). */
	public static volatile boolean stayOpen;
	/** Set only by the client screenshot harness: hover this slice (-1 = the mouse decides). */
	public static volatile int forceHover = -1;

	public SymbioteFormWheelScreen() {
		super(Component.translatable("screen.projecthero.symbiote.form_wheel"));
	}

	private static InputConstants.Key key() {
		return KeyBindingHelper.getBoundKeyOf(ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()]);
	}

	@Override
	public void tick() {
		ticksOpen++;
		Minecraft mc = Minecraft.getInstance();
		InputConstants.Key key = key();
		if (!stayOpen && key.getType() == InputConstants.Type.KEYSYM) {
			if (InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue())) {
				held = true;
			} else if (held || ticksOpen > 2) {
				pickAndClose();
			}
		}
		if (mc.player == null || !Symbiote.isNormalHost(mc.player) || !mc.player.isAlive()) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (!stayOpen && ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()].matches(keyCode, scanCode)) {
			pickAndClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		pickAndClose();
		return true;
	}

	/** The hovered Form (only sent when it changes something); the centre keeps the current one. */
	private void pickAndClose() {
		if (picked) {
			return;
		}
		picked = true;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && hovered >= 0 && hovered < FORMS.length && FORMS[hovered] != SymbioteVitalsManager.form(mc.player)) {
			ClientPlayNetworking.send(new SymbioteKitPayload(SymbioteKitPayload.PICK_FORM, FORMS[hovered]));
		}
		onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		float open = openEase(partialTick);
		g.fillGradient(0, 0, this.width, this.height, fade(DIM_TOP, open), fade(DIM_BOTTOM, open));
	}

	static String formKey(int form) {
		return "projecthero.symbiote.form." + SymbioteVitalsManager.formName(form);
	}

	/** A weapon cannot grow while something is in the main hand (the server refuses it too). */
	private static boolean blocked(Minecraft mc, int form) {
		return SymbioteVitalsManager.weaponOf(form) != null && mc.player != null && !mc.player.getMainHandItem().isEmpty();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int n = FORMS.length;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		float open = openEase(partialTick);
		// leave room under the wheel for up to three wrapped description rows and the hint
		float outer = Math.max(60f, Math.min(124f, Math.min(this.width * 0.36f, (this.height - 104) / 2f)));
		float inner = outer * 0.46f;
		float lift = Math.max(4f, outer * 0.05f);
		float cx = this.width / 2f;
		float cy = Math.max(outer + 10f, this.height / 2f - 26f);
		int cur = SymbioteVitalsManager.form(mc.player);

		hovered = -1;
		if (Math.hypot(mouseX - cx, mouseY - cy) > DEAD_ZONE) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
		}
		if (forceHover >= 0 && forceHover < n) {
			hovered = forceHover;
		}
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = now;
		float k = 1f - (float) Math.exp(-dt * 18f);
		for (int i = 0; i < n; i++) {
			hoverAnim[i] += ((i == hovered ? 1f : 0f) - hoverAnim[i]) * k;
		}

		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		float s = 0.82f + 0.18f * open;
		g.pose().scale(s, s, 1f);
		g.pose().translate(-cx, -cy, 0);
		double seg = Math.PI * 2 / n;
		float gap = 2.0f;
		float disc = inner - 6f;
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 1f, outer + 3f, 0, Math.PI * 2, fade(TRACK, open));
		PowerWheelScreen.sector(vc, m, cx, cy, outer + 2f, outer + 3f, 0, Math.PI * 2, fade(PURPLE_DIM, open));
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			boolean no = blocked(mc, FORMS[j]);
			double mid = -Math.PI / 2 + j * seg;
			double e0 = mid - seg / 2, e1 = mid + seg / 2;
			float r0 = inner + lift * he * 0.6f;
			float r1 = outer + lift * he * 1.4f;
			int fill = lerp(j % 2 == 0 ? SEG_FILL : SEG_FILL_ALT, no ? 0xF83A2020 : SEG_FILL_HOVER, he);
			int edge = lerp(FORMS[j] == cur ? PURPLE : PURPLE_DIM, 0xFFFFFFFF, he);
			PowerWheelScreen.band(vc, m, cx, cy, r0, r1, e0, e1, gap, fade(fill, open));
			PowerWheelScreen.band(vc, m, cx, cy, r1 - 1f, r1, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.band(vc, m, cx, cy, r0, r0 + 1f, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e0, gap, 1f, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e1, gap, -1f, fade(edge, open));
			if (FORMS[j] == cur) {
				PowerWheelScreen.band(vc, m, cx, cy, r1 - 3f, r1, e0, e1, gap, fade(PURPLE, open));
			}
		}
		boolean centre = hovered < 0;
		PowerWheelScreen.sector(vc, m, cx, cy, 0f, disc, 0, Math.PI * 2, fade(centre ? 0xF0201630 : CENTRE_FILL, open));
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 2f, disc, 0, Math.PI * 2, fade(PURPLE_DIM, open));
		g.flush();

		// each slice: its glyph over its name
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			int form = FORMS[j];
			boolean no = blocked(mc, form);
			double mid = -Math.PI / 2 + j * seg;
			float r = (inner + outer) / 2f + lift * he;
			float fx = cx + (float) Math.cos(mid) * r, fy = cy + (float) Math.sin(mid) * r;
			int glyphCol = no ? 0xFF7A5A5A : (form == cur ? 0xFFE0D0FF : lerp(0xFFB8A8D8, 0xFFFFFFFF, he));
			float px = Math.max(1.4f, Math.min(2.4f, (outer - inner) / 22f)) * (0.95f + 0.1f * he);
			glyph(g, GLYPHS[j], fx, fy - 4f - 4.5f * px, px, fade(glyphCol, open));
			Component name = Component.translatable(formKey(form));
			float maxW = (float) (r * seg) - 8f;
			float fit = Math.min(1f, maxW / Math.max(1, this.font.width(name))) * (0.9f + 0.1f * he);
			g.pose().pushPose();
			g.pose().translate(fx, fy + 3f, 0);
			g.pose().scale(fit, fit, 1f);
			int col = no ? 0xFF9A5A5A : (form == cur ? 0xFFD8C4FF : lerp(0xFFD0C8E8, 0xFFFFFFFF, he));
			g.drawString(this.font, name, Math.round(-this.font.width(name) / 2f), 0, fade(col, open), true);
			g.pose().popPose();
		}
		// the centre: the hovered Form, else the current one
		int shownForm = hovered >= 0 ? FORMS[hovered] : cur;
		Component label = Component.translatable(formKey(shownForm));
		float fit = Math.min(1f, (disc * 2f - 14f) / Math.max(1, this.font.width(label)));
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().scale(fit, fit, 1f);
		g.drawString(this.font, label, Math.round(-this.font.width(label) / 2f), -9, fade(PURPLE, open), true);
		g.pose().popPose();
		String sub = Component.translatable(hovered < 0 ? "screen.projecthero.symbiote.form_wheel.keep"
				: shownForm == cur ? "screen.projecthero.symbiote.form_wheel.current" : "screen.projecthero.symbiote.form_wheel.pick").getString();
		float sf = Math.min(0.8f, (disc * 2f - 10f) / Math.max(1, this.font.width(sub)));
		g.pose().pushPose();
		g.pose().translate(cx, cy + 3f, 0);
		g.pose().scale(sf, sf, 1f);
		g.drawString(this.font, sub, Math.round(-this.font.width(sub) / 2f), 0, fade(TEXT_DIM, open), false);
		g.pose().popPose();
		g.pose().popPose();

		// under the wheel: what the hovered Form does (wrapped by TooltipWrap), then the controls hint
		int y = Math.round(cy + outer + lift + 8);
		if (hovered >= 0) {
			Component desc = Component.translatable("screen.projecthero.symbiote.form_wheel.desc." + SymbioteVitalsManager.formName(shownForm));
			if (blocked(mc, shownForm)) {
				desc = Component.translatable("message.projecthero.symbiote.form.hand_full").withStyle(ChatFormatting.RED);
			}
			List<Component> rows = TooltipWrap.wrap(this.font, desc);
			for (Component row : rows) {
				if (y > this.height - 22) {
					break;
				}
				g.drawString(this.font, row, Math.round(cx - this.font.width(row) / 2f), y, fade(0xFFE6E0F4, open), false);
				y += 10;
			}
		}
		Component hint = Component.translatable("screen.projecthero.symbiote.form_wheel.hint");
		String h = this.font.plainSubstrByWidth(hint.getString(), this.width - 16);
		int hy = Math.min(this.height - 12, Math.max(y + 2, Math.round(cy + outer + lift + 10)));
		g.drawString(this.font, h, Math.round(cx - this.font.width(h) / 2f), hy, fade(TEXT_DIM, open), false);
	}

	/** Draws a 9 x 9 glyph centred on (x, y) at {@code px} GUI pixels a cell. */
	private static void glyph(GuiGraphics g, String[] rows, float x, float y, float px, int colour) {
		float x0 = x - 4.5f * px;
		float y0 = y - 4.5f * px;
		g.pose().pushPose();
		g.pose().translate(x0, y0, 0);
		g.pose().scale(px, px, 1f);
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			for (int c = 0; c < row.length(); c++) {
				if (row.charAt(c) == '#') {
					g.fill(c, r, c + 1, r + 1, colour);
				}
			}
		}
		g.pose().popPose();
	}

	private float openEase(float partial) {
		float t = Mth.clamp((ticksOpen + partial) / OPEN_TICKS, 0f, 1f);
		return 1f - (1f - t) * (1f - t);
	}

	private static float smooth(float h) {
		h = Mth.clamp(h, 0f, 1f);
		return h * h * (3f - 2f * h);
	}

	private static int fade(int argb, float a) {
		int alpha = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(a, 0f, 1f));
		return (alpha << 24) | (argb & 0xFFFFFF);
	}

	private static int lerp(int c0, int c1, float t) {
		int a = Math.round(Mth.lerp(t, (c0 >>> 24) & 0xFF, (c1 >>> 24) & 0xFF));
		int r = Math.round(Mth.lerp(t, (c0 >> 16) & 0xFF, (c1 >> 16) & 0xFF));
		int gg = Math.round(Mth.lerp(t, (c0 >> 8) & 0xFF, (c1 >> 8) & 0xFF));
		int b = Math.round(Mth.lerp(t, c0 & 0xFF, c1 & 0xFF));
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}
}
