package com.projecthero.mod.client.saiyan;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.PowerWheelScreen;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanConfig;
import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.network.SaiyanActionPayload;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.3: the Saiyan form wheel. Holding H opens it -- every form round the ring, each segment edged in its aura colour; a
 * locked form is dark with a lock-grey rim, one that can't be entered right now (Overdrive outside Blue, Instinct above 35%
 * health, too little Ki) is dimmed. Clicking a form sends the pick (the server re-checks); letting go of H (or Escape)
 * closes it without one. Drawn with the power wheel's ring geometry ({@link PowerWheelScreen#band}), like the alien wheel.
 */
public final class SaiyanFormWheelScreen extends Screen {
	private static final int DIM_TOP = 0x88000000;
	private static final int DIM_BOTTOM = 0xC0000000;
	private static final int SEG_FILL = 0xF0121008;
	private static final int SEG_FILL_ALT = 0xF018150C;
	private static final int SEG_LOCKED = 0xE00A0A0A;
	private static final int TRACK = 0xD0060504;
	private static final int CENTRE_FILL = 0xF0080706;
	private static final int TEXT_DIM = 0xFFA09A88;
	private static final int LOCK_GREY = 0xFF3A3A3A;
	private static final int DEAD_ZONE = 24;
	private static final float OPEN_TICKS = 3f;

	private final SaiyanForm[] forms = SaiyanForm.values();
	private int hovered = -1;
	private final float[] hoverAnim = new float[SaiyanForm.values().length];
	private long lastFrameNanos = 0L;
	private int ticksOpen = 0;
	private boolean held = false;

	public SaiyanFormWheelScreen() {
		super(Component.translatable("screen.projecthero.saiyan.form_wheel"));
	}

	@Override
	public void tick() {
		ticksOpen++;
		Minecraft mc = Minecraft.getInstance();
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.POWER_SELECT);
		if (key.getType() == InputConstants.Type.KEYSYM) {
			if (InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue())) {
				held = true;
			} else if (held) {
				onClose();
			}
		}
		if (mc.player == null || !Saiyan.hasPower(mc.player)) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.POWER_SELECT.matches(keyCode, scanCode)) {
			onClose();
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < forms.length) {
			ClientPlayNetworking.send(new SaiyanActionPayload(SaiyanActionPayload.Action.PICK_FORM, forms[hovered].ordinal()));
			onClose();
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
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

	/** Why the form can't be picked right now (null = it can), for the dimming and the centre hint. Client-side guess only. */
	private static String blocked(LocalPlayer p, SaiyanForm f) {
		if (!Saiyan.unlocked(p, f)) {
			return "screen.projecthero.saiyan.locked";
		}
		SaiyanForm now = Saiyan.form(p);
		if (f == now) {
			return "screen.projecthero.saiyan.current";
		}
		if (f == SaiyanForm.BASE) {
			return null;
		}
		if (Saiyan.exhausted(p)) {
			return "screen.projecthero.saiyan.exhausted";
		}
		if (f == SaiyanForm.BLUE_OVERDRIVE && now != SaiyanForm.BLUE) {
			return "screen.projecthero.saiyan.from_blue";
		}
		if (f == SaiyanForm.INSTINCT && p.getHealth() > p.getMaxHealth() * SaiyanConfig.INSTINCT_HEALTH) {
			return "screen.projecthero.saiyan.low_hp";
		}
		if (!p.getAbilities().instabuild && Saiyan.ki(p) + 1.0e-3f < Math.max(f.cost, f == SaiyanForm.INSTINCT ? SaiyanConfig.INSTINCT_KI : 0f)) {
			return "screen.projecthero.saiyan.no_ki";
		}
		return null;
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) {
			return;
		}
		int n = forms.length;
		float open = openEase(partialTick);
		float outer = Math.max(70f, Math.min(130f, Math.min(this.width * 0.36f, (this.height - 50) / 2f)));
		float inner = outer * 0.46f;
		float lift = Math.max(4f, outer * 0.05f);
		float cx = this.width / 2f;
		float cy = this.height / 2f - 4f;
		SaiyanForm current = Saiyan.form(p);

		hovered = -1;
		if (Math.hypot(mouseX - cx, mouseY - cy) > DEAD_ZONE) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
		}
		long nowNs = System.nanoTime();
		float dt = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (nowNs - lastFrameNanos) / 1.0e9f);
		lastFrameNanos = nowNs;
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
		float gap = 2.5f;
		float disc = inner - 6f;
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		int ringColour = 0xFF000000 | (current.auraColor & 0xFFFFFF);
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 1f, outer + 3f, 0, Math.PI * 2, fade(TRACK, open));
		PowerWheelScreen.sector(vc, m, cx, cy, outer + 2f, outer + 3f, 0, Math.PI * 2, fade(dim(ringColour, 0.55f), open));
		for (int j = 0; j < n; j++) {
			SaiyanForm f = forms[j];
			boolean locked = !Saiyan.unlocked(p, f);
			boolean usable = blocked(p, f) == null || f == current;
			float he = smooth(hoverAnim[j]);
			double mid = -Math.PI / 2 + j * seg;
			double e0 = mid - seg / 2, e1 = mid + seg / 2;
			float r0 = inner + lift * he * 0.6f;
			float r1 = outer + lift * he * 1.4f;
			int aura = 0xFF000000 | (f.auraColor & 0xFFFFFF);
			int fill = locked ? SEG_LOCKED : lerp(j % 2 == 0 ? SEG_FILL : SEG_FILL_ALT, dim(aura, 0.35f), he * 0.8f);
			int edge = locked ? LOCK_GREY : lerp(usable ? dim(aura, 0.7f) : dim(aura, 0.3f), 0xFFFFFFFF, he * (usable ? 1f : 0.3f));
			PowerWheelScreen.band(vc, m, cx, cy, r0, r1, e0, e1, gap, fade(fill, open));
			PowerWheelScreen.band(vc, m, cx, cy, r1 - 1f, r1, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.band(vc, m, cx, cy, r0, r0 + 1f, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e0, gap, 1f, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e1, gap, -1f, fade(edge, open));
			if (f == current) {
				PowerWheelScreen.band(vc, m, cx, cy, r1 - 3f, r1, e0, e1, gap, fade(aura, open));
			}
		}
		PowerWheelScreen.sector(vc, m, cx, cy, 0f, disc, 0, Math.PI * 2, fade(CENTRE_FILL, open));
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 2f, disc, 0, Math.PI * 2, fade(ringColour, open));
		g.flush();

		// each form's name on its segment: a hair-colour swatch above a short name
		for (int j = 0; j < n; j++) {
			SaiyanForm f = forms[j];
			boolean locked = !Saiyan.unlocked(p, f);
			float he = smooth(hoverAnim[j]);
			double mid = -Math.PI / 2 + j * seg;
			float r = (inner + outer) / 2f + lift * he;
			int fx = Math.round(cx + (float) Math.cos(mid) * r);
			int fy = Math.round(cy + (float) Math.sin(mid) * r);
			int hair = 0xFF000000 | (f.hairColor & 0xFFFFFF);
			if (!locked) {
				g.fill(fx - 6, fy - 11, fx + 6, fy - 5, fade(0xFF000000, open));
				g.fill(fx - 5, fy - 10, fx + 5, fy - 6, fade(hair, open));
			}
			Component name = Component.translatable(f.nameKey() + ".short");
			int colour = locked ? 0xFF6A6A6A : blocked(p, f) == null || f == current ? 0xFFFFFFFF : 0xFF9A9A9A;
			g.drawString(this.font, name, fx - this.font.width(name) / 2, fy - 2, fade(colour, open), true);
		}
		// centre: the hovered form, its cost, and why it can't be picked (if it can't)
		Component label;
		int colour;
		Component sub = null;
		if (hovered >= 0) {
			SaiyanForm f = forms[hovered];
			label = Component.translatable(f.nameKey());
			colour = Saiyan.unlocked(p, f) ? 0xFF000000 | (f.hairColor == SaiyanForm.BASE.hairColor ? 0xFFFFFF : f.hairColor & 0xFFFFFF) : 0xFF8A8A8A;
			String why = blocked(p, f);
			sub = why != null && f != current ? Component.translatable(why)
					: f.cost > 0 ? Component.translatable("screen.projecthero.saiyan.cost", Math.round(f.cost)) : null;
		} else {
			label = Component.translatable("screen.projecthero.saiyan.form_wheel");
			colour = 0xFFFFE066;
		}
		float fit = Math.min(1f, (disc * 2f - 14f) / Math.max(1, this.font.width(label)));
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().scale(fit, fit, 1f);
		g.drawString(this.font, label, Math.round(-this.font.width(label) / 2f), sub == null ? -4 : -9, fade(colour, open), true);
		g.pose().popPose();
		if (sub != null) {
			float fit2 = Math.min(0.8f, (disc * 2f - 10f) / Math.max(1, this.font.width(sub)));
			g.pose().pushPose();
			g.pose().translate(cx, cy + 4, 0);
			g.pose().scale(fit2, fit2, 1f);
			g.drawString(this.font, sub, Math.round(-this.font.width(sub) / 2f), 0, fade(TEXT_DIM, open), true);
			g.pose().popPose();
		}
		g.pose().popPose();
		Component hint = Component.translatable("screen.projecthero.saiyan.form_wheel.hint");
		int hy = Math.min(this.height - 12, Math.round(cy + outer + lift + 10));
		g.drawString(this.font, hint, Math.round(cx - this.font.width(hint) / 2f), hy, fade(TEXT_DIM, open), false);
	}

	private float openEase(float partial) {
		float t = Mth.clamp((ticksOpen + partial) / OPEN_TICKS, 0f, 1f);
		return 1f - (1f - t) * (1f - t);
	}

	private static float smooth(float h) {
		h = Mth.clamp(h, 0f, 1f);
		return h * h * (3f - 2f * h);
	}

	private static int dim(int argb, float f) {
		int r = Math.round(((argb >> 16) & 0xFF) * f), gg = Math.round(((argb >> 8) & 0xFF) * f), b = Math.round((argb & 0xFF) * f);
		return (argb & 0xFF000000) | (r << 16) | (gg << 8) | b;
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
