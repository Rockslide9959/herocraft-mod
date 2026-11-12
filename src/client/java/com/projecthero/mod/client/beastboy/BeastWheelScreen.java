package com.projecthero.mod.client.beastboy;

import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.beastboy.BeastBoy;
import com.projecthero.mod.beastboy.BeastBoyForms;
import com.projecthero.mod.beastboy.BeastForm;
import com.projecthero.mod.beastboy.network.BeastBoyPickPayload;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.PowerWheelScreen;
import com.projecthero.mod.hero.AbilitySlot;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Beast Boy: the <b>Animal Wheel</b>. Hold V: every sustained form round a black ring in Sakutia green (the power wheel's
 * ring geometry, {@link PowerWheelScreen#band}); point at one and let go of V to become it, let go over the centre (Human) to
 * turn back. A click picks too; Escape closes without a pick. A form he was knocked out of shows its lockout, greyed.
 */
public final class BeastWheelScreen extends Screen {
	private static final int DIM_TOP = 0x88000000;
	private static final int DIM_BOTTOM = 0xC0000000;
	private static final int SEG_FILL = 0xF0101410;
	private static final int SEG_FILL_ALT = 0xF0151A14;
	private static final int SEG_FILL_HOVER = 0xF8305A22;
	private static final int TRACK = 0xD0050605;
	private static final int GREEN = 0xFF58C83C;
	private static final int GREEN_DIM = 0xFF2E5E22;
	private static final int CENTRE_FILL = 0xF0070907;
	private static final int TEXT_DIM = 0xFF8FA090;
	private static final int DEAD_ZONE = 24;
	private static final float OPEN_TICKS = 3f;

	private final List<BeastForm> forms = BeastBoyForms.WHEEL;
	private int hovered = -1;
	private float[] hoverAnim = new float[0];
	private long lastFrameNanos = 0L;
	private int ticksOpen = 0;
	private boolean held = false;
	private boolean picked = false;
	/** Set only by the client screenshot harness: the wheel ignores the V key (stays open until closed). */
	public static volatile boolean stayOpen;
	/** Set only by the client screenshot harness: hover this segment (-1 = the mouse decides). */
	public static volatile int forceHover = -1;

	public BeastWheelScreen() {
		super(Component.translatable("screen.projecthero.beast_boy.wheel"));
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
		if (mc.player == null || !BeastBoy.hasPower(mc.player) || BeastBoy.isBeast(mc.player)) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.ABILITY_SLOTS[AbilitySlot.SLOT_5.ordinal()].matches(keyCode, scanCode)) {
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

	/** The hovered form, or Human over the centre (only sent when it changes something). */
	private void pickAndClose() {
		if (picked) {
			return;
		}
		picked = true;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			BeastForm cur = BeastBoy.form(mc.player);
			BeastForm want = hovered >= 0 && hovered < forms.size() ? forms.get(hovered) : BeastForm.HUMAN;
			if (want != cur) {
				ClientPlayNetworking.send(new BeastBoyPickPayload(want.ordinal()));
			}
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

	static String formKey(BeastForm f) {
		return "projecthero.beast_boy.form." + f.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int n = forms.size();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		float open = openEase(partialTick);
		float outer = Math.max(78f, Math.min(130f, Math.min(this.width * 0.36f, (this.height - 50) / 2f)));
		float inner = outer * 0.46f;
		float lift = Math.max(4f, outer * 0.05f);
		float cx = this.width / 2f;
		float cy = this.height / 2f - 4f;
		BeastForm cur = BeastBoy.form(mc.player);

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
		if (hoverAnim.length != n) {
			hoverAnim = new float[n];
		}
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
		PowerWheelScreen.sector(vc, m, cx, cy, outer + 2f, outer + 3f, 0, Math.PI * 2, fade(GREEN_DIM, open));
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			BeastForm f = forms.get(j);
			boolean locked = BeastBoy.formLockout(mc.player, f) > 0;
			double mid = -Math.PI / 2 + j * seg;
			double e0 = mid - seg / 2, e1 = mid + seg / 2;
			float r0 = inner + lift * he * 0.6f;
			float r1 = outer + lift * he * 1.4f;
			int fill = lerp(j % 2 == 0 ? SEG_FILL : SEG_FILL_ALT, locked ? 0xF83A2020 : SEG_FILL_HOVER, he);
			int edge = lerp(f == cur ? GREEN : GREEN_DIM, 0xFFFFFFFF, he);
			PowerWheelScreen.band(vc, m, cx, cy, r0, r1, e0, e1, gap, fade(fill, open));
			PowerWheelScreen.band(vc, m, cx, cy, r1 - 1f, r1, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.band(vc, m, cx, cy, r0, r0 + 1f, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e0, gap, 1f, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e1, gap, -1f, fade(edge, open));
			if (f == cur) {
				PowerWheelScreen.band(vc, m, cx, cy, r1 - 3f, r1, e0, e1, gap, fade(GREEN, open));
			}
		}
		boolean centre = hovered < 0;
		PowerWheelScreen.sector(vc, m, cx, cy, 0f, disc, 0, Math.PI * 2, fade(centre ? 0xF0183014 : CENTRE_FILL, open));
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 2f, disc, 0, Math.PI * 2, fade(cur == BeastForm.HUMAN ? GREEN : GREEN_DIM, open));
		g.flush();

		// each form's name (and health) on its segment
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			BeastForm f = forms.get(j);
			double mid = -Math.PI / 2 + j * seg;
			float r = (inner + outer) / 2f + lift * he;
			float fx = cx + (float) Math.cos(mid) * r, fy = cy + (float) Math.sin(mid) * r;
			int lock = BeastBoy.formLockout(mc.player, f);
			Component name = Component.translatable(formKey(f));
			float maxW = (float) (r * seg) - 6f;
			float fit = Math.min(1f, maxW / Math.max(1, this.font.width(name))) * (0.9f + 0.1f * he);
			g.pose().pushPose();
			g.pose().translate(fx, fy - 5f, 0);
			g.pose().scale(fit, fit, 1f);
			int col = lock > 0 ? 0xFF9A5A5A : (f == cur ? 0xFFB8FF8A : lerp(0xFFCFE8C8, 0xFFFFFFFF, he));
			g.drawString(this.font, name, Math.round(-this.font.width(name) / 2f), 0, fade(col, open), true);
			g.pose().popPose();
			String sub = lock > 0 ? ((lock + 19) / 20) + "s" : Math.round(f.formHealth) + " HP";
			float sf = 0.75f * Math.min(1f, maxW / Math.max(1, this.font.width(sub)));
			g.pose().pushPose();
			g.pose().translate(fx, fy + 5f, 0);
			g.pose().scale(sf, sf, 1f);
			g.drawString(this.font, sub, Math.round(-this.font.width(sub) / 2f), 0, fade(lock > 0 ? 0xFFFF7070 : TEXT_DIM, open), false);
			g.pose().popPose();
		}
		// the centre: the hovered form, else Human
		Component label = hovered >= 0 ? Component.translatable(formKey(forms.get(hovered))) : Component.translatable("projecthero.beast_boy.form.human");
		float fit = Math.min(1f, (disc * 2f - 14f) / Math.max(1, this.font.width(label)));
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().scale(fit, fit, 1f);
		g.drawString(this.font, label, Math.round(-this.font.width(label) / 2f), -9, fade(GREEN, open), true);
		g.pose().popPose();
		if (hovered >= 0) {
			BeastForm f = forms.get(hovered);
			String drain = Component.translatable("screen.projecthero.beast_boy.wheel.drain", fmt(BeastBoyForms.drainPerSecond(f))).getString();
			float df = Math.min(0.8f, (disc * 2f - 10f) / Math.max(1, this.font.width(drain)));
			g.pose().pushPose();
			g.pose().translate(cx, cy + 3f, 0);
			g.pose().scale(df, df, 1f);
			g.drawString(this.font, drain, Math.round(-this.font.width(drain) / 2f), 0, fade(TEXT_DIM, open), false);
			g.pose().popPose();
		}
		g.pose().popPose();
		Component hint = Component.translatable("screen.projecthero.beast_boy.wheel.hint");
		String h = this.font.plainSubstrByWidth(hint.getString(), this.width - 16);
		int hy = Math.min(this.height - 12, Math.round(cy + outer + lift + 10));
		g.drawString(this.font, h, Math.round(cx - this.font.width(h) / 2f), hy, fade(TEXT_DIM, open), false);
	}

	private static String fmt(float v) {
		return v == Math.round(v) ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
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
