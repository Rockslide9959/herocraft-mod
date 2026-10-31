package com.projecthero.mod.client.omnitrix;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.ModKeyBindings;
import com.projecthero.mod.client.gui.PowerWheelScreen;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.network.OmnitrixActionPayload;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * v0.17.2 (user: "add an alien wheel picker when player has master control they can activate by holding H, when they press H it
 * opens and it stays open by holding H, when player releases H it closes, or closes when player clicks on an alien"): the
 * Master Control alien wheel. Holding H with Master Control opens it -- every playable alien round a black ring in the
 * Omnitrix's green, each on its own dial face. Clicking one turns the wearer straight into it (from human or from another
 * alien: {@code OmnitrixActionPayload.Action.MASTER_PICK}); letting go of H (or Escape) closes it without a pick. Drawn with
 * the power wheel's ring geometry ({@link PowerWheelScreen#band}).
 */
public final class AlienWheelScreen extends Screen {
	private static final int DIM_TOP = 0x88000000;
	private static final int DIM_BOTTOM = 0xC0000000;
	private static final int SEG_FILL = 0xF0101210;
	private static final int SEG_FILL_ALT = 0xF0161916;
	private static final int SEG_FILL_HOVER = 0xF8244A26;
	private static final int TRACK = 0xD0050605;
	private static final int GREEN = 0xFF4CF05A;
	private static final int GREEN_DIM = 0xFF2E6E34;
	private static final int CENTRE_FILL = 0xF0070907;
	private static final int TEXT_DIM = 0xFF8FA090;
	/** Mouse dead zone (px from centre) inside which nothing is hovered. */
	private static final int DEAD_ZONE = 24;
	private static final float OPEN_TICKS = 3f;

	private final List<Aliens.Alien> aliens = Aliens.playable();
	private int hovered = -1;
	private float[] hoverAnim = new float[0];
	private long lastFrameNanos = 0L;
	private int ticksOpen = 0;
	/** H has been seen held while open (only then does the key being up close it). */
	private boolean held = false;

	public AlienWheelScreen() {
		super(Component.translatable("screen.projecthero.omnitrix.alien_wheel"));
	}

	/** H with Master Control, not sneaking: the wheel opens (instead of whatever else H does). Client-safe. */
	public static boolean wants(net.minecraft.world.entity.player.Player p) {
		return p != null && !Screen.hasShiftDown() && Omnitrix.masterControl(p) && !Aliens.playable().isEmpty();
	}

	@Override
	public void tick() {
		ticksOpen++;
		// a key let go while the screen had no focus never reaches keyReleased: check the real key too
		Minecraft mc = Minecraft.getInstance();
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(ModKeyBindings.POWER_SELECT);
		if (key.getType() == InputConstants.Type.KEYSYM) {
			if (InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue())) {
				held = true;
			} else if (held) {
				onClose();
			}
		}
		if (mc.player == null || !Omnitrix.masterControl(mc.player)) {
			onClose();
		}
	}

	@Override
	public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
		if (ModKeyBindings.POWER_SELECT.matches(keyCode, scanCode)) {
			onClose(); // letting go of H closes it without a pick
			return true;
		}
		return super.keyReleased(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (hovered >= 0 && hovered < aliens.size()) {
			ClientPlayNetworking.send(new OmnitrixActionPayload(OmnitrixActionPayload.Action.MASTER_PICK, aliens.get(hovered).id()));
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

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		int n = aliens.size();
		if (n == 0) {
			return;
		}
		float open = openEase(partialTick);
		float outer = Math.max(70f, Math.min(130f, Math.min(this.width * 0.36f, (this.height - 50) / 2f)));
		float inner = outer * 0.46f;
		float lift = Math.max(4f, outer * 0.05f);
		float cx = this.width / 2f;
		float cy = this.height / 2f - 4f;
		Minecraft mc = Minecraft.getInstance();
		String active = mc.player == null ? "" : Omnitrix.active(mc.player);

		// hover: the pointing direction picks the alien (alien 0 centred at the top), the dead zone round the centre none
		hovered = -1;
		if (Math.hypot(mouseX - cx, mouseY - cy) > DEAD_ZONE) {
			double ang = Math.atan2(mouseY - cy, mouseX - cx);
			double norm = (ang + Math.PI / 2 + Math.PI / n + Math.PI * 2) % (Math.PI * 2);
			hovered = (int) (norm / (Math.PI * 2) * n) % n;
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
		float gap = 2.5f;
		float disc = inner - 6f;
		VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
		Matrix4f m = g.pose().last().pose();
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 1f, outer + 3f, 0, Math.PI * 2, fade(TRACK, open));
		PowerWheelScreen.sector(vc, m, cx, cy, outer + 2f, outer + 3f, 0, Math.PI * 2, fade(GREEN_DIM, open));
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			boolean isActive = aliens.get(j).id().equals(active);
			double mid = -Math.PI / 2 + j * seg;
			double e0 = mid - seg / 2, e1 = mid + seg / 2;
			float r0 = inner + lift * he * 0.6f;
			float r1 = outer + lift * he * 1.4f;
			int fill = lerp(j % 2 == 0 ? SEG_FILL : SEG_FILL_ALT, SEG_FILL_HOVER, he);
			int edge = lerp(isActive ? GREEN : GREEN_DIM, 0xFFFFFFFF, he);
			PowerWheelScreen.band(vc, m, cx, cy, r0, r1, e0, e1, gap, fade(fill, open));
			PowerWheelScreen.band(vc, m, cx, cy, r1 - 1f, r1, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.band(vc, m, cx, cy, r0, r0 + 1f, e0, e1, gap, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e0, gap, 1f, fade(edge, open));
			PowerWheelScreen.side(vc, m, cx, cy, r0, r1, e1, gap, -1f, fade(edge, open));
			if (isActive) {
				// the alien he is now: a bright green rim
				PowerWheelScreen.band(vc, m, cx, cy, r1 - 3f, r1, e0, e1, gap, fade(GREEN, open));
			}
		}
		// the centre disc: the Omnitrix face
		PowerWheelScreen.sector(vc, m, cx, cy, 0f, disc, 0, Math.PI * 2, fade(CENTRE_FILL, open));
		PowerWheelScreen.sector(vc, m, cx, cy, disc - 2f, disc, 0, Math.PI * 2, fade(GREEN, open));
		g.flush();

		// each alien's dial face on its segment
		for (int j = 0; j < n; j++) {
			float he = smooth(hoverAnim[j]);
			double mid = -Math.PI / 2 + j * seg;
			float r = (inner + outer) / 2f + lift * he;
			int size = Math.round(Math.min(32f, (outer - inner) * 0.62f) * (1f + 0.15f * he));
			int ix = Math.round(cx + (float) Math.cos(mid) * r) - size / 2;
			int iy = Math.round(cy + (float) Math.sin(mid) * r) - size / 2;
			com.mojang.blaze3d.systems.RenderSystem.enableBlend();
			com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, open * (0.75f + 0.25f * he));
			g.blit(OmnitrixWatch.icon(aliens.get(j).id(), false), ix, iy, 0, 0, size, size, size, size);
			com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		}
		// the centre: the hovered alien's name in its colour, else the title
		Component label = hovered >= 0 ? Component.translatable(aliens.get(hovered).nameKey())
				: Component.translatable("screen.projecthero.omnitrix.alien_wheel");
		int colour = hovered >= 0 ? 0xFF000000 | aliens.get(hovered).colour() : GREEN;
		// shrunk to fit inside the disc (never past its green rim)
		float fit = Math.min(1f, (disc * 2f - 14f) / Math.max(1, this.font.width(label)));
		g.pose().pushPose();
		g.pose().translate(cx, cy, 0);
		g.pose().scale(fit, fit, 1f);
		g.drawString(this.font, label, Math.round(-this.font.width(label) / 2f), -4, fade(colour, open), true);
		g.pose().popPose();
		g.pose().popPose();
		Component hint = Component.translatable("screen.projecthero.omnitrix.alien_wheel.hint");
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
