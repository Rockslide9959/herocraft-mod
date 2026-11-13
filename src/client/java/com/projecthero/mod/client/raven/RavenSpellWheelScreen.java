package com.projecthero.mod.client.raven;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.PowerWheelScreen;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenSpell;
import com.projecthero.mod.raven.RavenSpells;
import com.projecthero.mod.raven.network.RavenActionPayload;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.3 Raven revamp: the <b>Spell Wheel</b>. Hold V (a tap casts the picked spell instead, {@code RavenClient}): the ten spells
 * round a black ring in Azarath violet (the power wheel's ring geometry, {@link PowerWheelScreen#band}), combat spells on the right
 * half, utility on the left. Each slice shows the spell's icon, name, a few words of what it does, its Dark Energy cost and its
 * cooldown; the one pointed at is described in full under the wheel (wrapped). Point and let go of V to pick it (a click picks
 * too); let go over the centre, or Escape, to keep the old pick. The game keeps running.
 */
public final class RavenSpellWheelScreen extends Screen {
	public static final ResourceLocation ICONS = ProjectHeroMod.id("textures/gui/raven/spells.png");
	private static final int DIM_TOP = 0x80000000;
	private static final int DIM_BOTTOM = 0xC0000000;
	private static final int SEG_FILL = 0xF00C0812;
	private static final int SEG_FILL_ALT = 0xF0120C1A;
	private static final int SEG_FILL_HOVER = 0xF83A1E5C;
	private static final int SEG_FILL_UTILITY = 0xF00A0C14;
	private static final int TRACK = 0xD0040306;
	private static final int VIOLET = 0xFFA66CF0;
	private static final int VIOLET_DIM = 0xFF4A2A72;
	private static final int CENTRE_FILL = 0xF0060409;
	private static final int TEXT = 0xFFE4D8FF;
	private static final int TEXT_DIM = 0xFF9A8CB4;
	private static final int COST = 0xFFB98CF0;
	private static final int COOL = 0xFFFF8A8A;
	private static final int DEAD_ZONE = 22;
	private static final float OPEN_TICKS = 3f;

	private final RavenSpell[] spells = RavenSpell.values();
	private int hovered = -1;
	private float[] hoverAnim = new float[0];
	private long lastFrameNanos = 0L;
	private int ticksOpen = 0;
	private boolean held = false;
	private boolean picked = false;
	/** Set only by the client screenshot harness: the wheel ignores the V key (stays open until closed). */
	public static volatile boolean stayOpen;
	/** Set only by the client screenshot harness: hover this slice (-1 = the mouse decides). */
	public static volatile int forceHover = -1;

	public RavenSpellWheelScreen() {
		super(Component.translatable("screen.projecthero.raven.wheel"));
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
		if (mc.player == null || !Raven.hasPower(mc.player) || !mc.player.isAlive()) {
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

	/** The pointed-at spell becomes the pick (nothing is sent over the centre or for the same spell). */
	private void pickAndClose() {
		if (picked) {
			return;
		}
		picked = true;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && hovered >= 0 && hovered < spells.length && spells[hovered] != Raven.selectedSpell(mc.player)) {
			ClientPlayNetworking.send(new RavenActionPayload(RavenActionPayload.Action.PICK_SPELL, hovered));
			mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.4f, 0.7f);
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

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		int n = spells.length;
		float open = openEase(partialTick);
		// sized to leave room for the hint above and three lines of description below, even in a small window
		float outer = Math.max(80f, Math.min(138f, Math.min(this.width * 0.38f, (this.height - 92) / 2f)));
		float inner = outer * 0.40f;
		float lift = Math.max(4f, outer * 0.05f);
		float cx = this.width / 2f;
		float cy = Math.max(outer + lift + 18f, this.height / 2f - 20f);
		RavenSpell cur = Raven.selectedSpell(mc.player);

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
		float disc = inner - 5f;
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 1f, outer + 3f, 0, Math.PI * 2, fade(TRACK, open));
		PowerWheelScreen.sector(vc, m, cx, cy, outer + 2f, outer + 3f, 0, Math.PI * 2, fade(VIOLET_DIM, open));
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			RavenSpell sp = spells[j];
			boolean cooling = Raven.cooldownRemaining(mc.player, sp.id) > 0;
			double mid = -Math.PI / 2 + j * seg;
			double e0 = mid - seg / 2, e1 = mid + seg / 2;
			float r0 = inner + lift * he * 0.6f;
			float r1 = outer + lift * he * 1.4f;
			int base = sp.combat ? (j % 2 == 0 ? SEG_FILL : SEG_FILL_ALT) : (j % 2 == 0 ? SEG_FILL_UTILITY : SEG_FILL_ALT);
			int fill = lerp(base, cooling ? 0xF8302040 : SEG_FILL_HOVER, he);
			int edge = lerp(sp == cur ? VIOLET : VIOLET_DIM, 0xFFFFFFFF, he);
			PowerWheelScreen.band(vc, m, cx, cy, r0, r1, e0, e1, gap, fade(fill, open));
			PowerWheelScreen.band(vc, m, cx, cy, r1 - 1f, r1, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.band(vc, m, cx, cy, r0, r0 + 1f, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e0, gap, 1f, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e1, gap, -1f, fade(edge, open));
			if (sp == cur) {
				PowerWheelScreen.band(vc, m, cx, cy, r1 - 3f, r1, e0, e1, gap, fade(VIOLET, open));
			}
		}
		boolean centre = hovered < 0;
		PowerWheelScreen.sector(vc, m, cx, cy, 0f, disc, 0, Math.PI * 2, fade(centre ? 0xF01A1028 : CENTRE_FILL, open));
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 2f, disc, 0, Math.PI * 2, fade(VIOLET_DIM, open));
		g.flush();

		// each slice: icon, name, a few words, cost and cooldown
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			RavenSpell sp = spells[j];
			double mid = -Math.PI / 2 + j * seg;
			float r = (inner + outer) / 2f + lift * he;
			float fx = cx + (float) Math.cos(mid) * r, fy = cy + (float) Math.sin(mid) * r;
			float maxW = (float) (r * seg) - 6f;
			int cd = Raven.cooldownRemaining(mc.player, sp.id);
			// the icon
			float iconScale = (outer >= 110f ? 1.25f : 1.0f) * (1f + 0.12f * he);
			int alpha = Math.round(255 * open);
			g.pose().pushPose();
			g.pose().translate(fx, fy - 16f * iconScale + 1f, 0);
			g.pose().scale(iconScale, iconScale, 1f);
			com.mojang.blaze3d.systems.RenderSystem.enableBlend();
			g.setColor(cd > 0 ? 0.55f : 1f, cd > 0 ? 0.55f : 1f, cd > 0 ? 0.6f : 1f, alpha / 255f);
			g.blit(ICONS, -8, -8, sp.ordinal() * 16, 0, 16, 16, 16 * RavenSpell.count(), 16);
			g.setColor(1f, 1f, 1f, 1f);
			g.pose().popPose();
			// the name
			Component name = Component.translatable(sp.langKey());
			float fit = Math.min(0.9f, maxW / Math.max(1, this.font.width(name))) * (0.92f + 0.08f * he);
			g.pose().pushPose();
			g.pose().translate(fx, fy - 2f, 0);
			g.pose().scale(fit, fit, 1f);
			int col = sp == cur ? 0xFFD8B8FF : lerp(TEXT, 0xFFFFFFFF, he);
			g.drawString(this.font, name, Math.round(-this.font.width(name) / 2f), 0, fade(col, open), true);
			g.pose().popPose();
			// a few words of what it does (wrapped to two lines at most)
			float ws = 0.55f;
			List<FormattedCharSequence> words = this.font.split(Component.translatable(sp.langKey() + ".short"), Math.max(10, Math.round(maxW / ws)));
			float y = fy + 7f;
			for (int li = 0; li < Math.min(2, words.size()); li++) {
				FormattedCharSequence line = words.get(li);
				g.pose().pushPose();
				g.pose().translate(fx, y, 0);
				g.pose().scale(ws, ws, 1f);
				g.drawString(this.font, line, Math.round(-this.font.width(line) / 2f), 0, fade(TEXT_DIM, open), false);
				g.pose().popPose();
				y += 5.5f;
			}
			// cost and cooldown
			String costText = Math.round(RavenSpells.costNow(mc.player, sp)) + "";
			String cdText = cd > 0 ? "  " + ((cd + 19) / 20) + "s" : "  " + (sp.cooldown / 20) + "s cd";
			String meta = costText + cdText;
			float msc = Math.min(0.62f, maxW / Math.max(1, this.font.width(meta)));
			g.pose().pushPose();
			g.pose().translate(fx, y + 1f, 0);
			g.pose().scale(msc, msc, 1f);
			int mx = Math.round(-this.font.width(meta) / 2f);
			g.drawString(this.font, costText, mx, 0, fade(COST, open), false);
			g.drawString(this.font, cdText, mx + this.font.width(costText), 0, fade(cd > 0 ? COOL : TEXT_DIM, open), false);
			g.pose().popPose();
		}
		// the centre: the pointed-at spell (or the current pick), its group
		RavenSpell shown = hovered >= 0 ? spells[hovered] : cur;
		Component label = Component.translatable(shown.langKey());
		float lf = Math.min(1f, (disc * 2f - 10f) / Math.max(1, this.font.width(label)));
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().scale(lf, lf, 1f);
		g.drawString(this.font, label, Math.round(-this.font.width(label) / 2f), -10, fade(VIOLET, open), true);
		g.pose().popPose();
		Component group = Component.translatable(hovered >= 0 ? (shown.combat ? "screen.projecthero.raven.wheel.combat" : "screen.projecthero.raven.wheel.utility")
				: "screen.projecthero.raven.wheel.current");
		float gf = Math.min(0.75f, (disc * 2f - 10f) / Math.max(1, this.font.width(group)));
		g.pose().pushPose();
		g.pose().translate(cx, cy + 2f, 0);
		g.pose().scale(gf, gf, 1f);
		g.drawString(this.font, group, Math.round(-this.font.width(group) / 2f), 0, fade(TEXT_DIM, open), false);
		g.pose().popPose();
		g.pose().popPose();

		// the full description of the pointed-at spell, wrapped under the wheel
		int boxW = Math.min(300, this.width - 24);
		int ty = Math.round(cy + outer + lift + 8);
		List<FormattedCharSequence> desc = this.font.split(Component.translatable(shown.langKey() + ".desc"), boxW);
		int lines = Math.min(desc.size(), Math.max(1, (this.height - 14 - ty) / 10));
		if (lines > 0) {
			g.fill(Math.round(cx - boxW / 2f) - 4, ty - 3, Math.round(cx + boxW / 2f) + 4, ty + lines * 10 + 1, fade(0xB0080510, open));
		}
		for (int i = 0; i < lines; i++) {
			FormattedCharSequence line = desc.get(i);
			g.drawString(this.font, line, Math.round(cx - this.font.width(line) / 2f), ty + i * 10, fade(TEXT, open), false);
		}
		Component hint = Component.translatable("screen.projecthero.raven.wheel.hint");
		String h = this.font.plainSubstrByWidth(hint.getString(), this.width - 16);
		g.drawString(this.font, h, Math.round(cx - this.font.width(h) / 2f), 6, fade(TEXT_DIM, open), false);
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
