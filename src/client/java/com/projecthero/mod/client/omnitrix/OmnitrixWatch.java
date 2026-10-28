package com.projecthero.mod.client.omnitrix;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1: the Omnitrix on the left wrist -- a chunky little model (a banded strap, a raised dial housing and the core with
 * its face), drawn in the left arm's space in both third and first person.
 *
 * <p>Alien Evo style (user): V raises the wrist; the core pops up out of the housing and its face changes from the green
 * hourglass to the selected alien's silhouette, with a green hologram of it hovering over the dial. The scroll wheel turns the
 * dial (the core twists a notch), a click slaps the core back down. The face goes red while the watch recharges, when V is
 * refused, and flashing in a transformation's last 15 s.
 */
public final class OmnitrixWatch {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/omnitrix/watch.png");
	public static final ResourceLocation TEX_RED = ProjectHeroMod.id("textures/entity/omnitrix/watch_red.png");
	private static final float T = 1f / 32f;

	private OmnitrixWatch() {
	}

	public static ResourceLocation silhouette(String alienId) {
		return ProjectHeroMod.id("textures/gui/omnitrix/alien_" + alienId + ".png");
	}

	/** 0 (down) .. 1 (up and the core popped) -- the selection animation, eased. */
	public static float raise(Player p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		if (!s.hasPower || s.transformed()) {
			return 0f;
		}
		float t = p.level().getGameTime() + partial - s.selectStart;
		float r = s.selecting ? Mth.clamp(t / 6f, 0f, 1f) : (s.selectStart == 0 ? 0f : 1f - Mth.clamp(t / 5f, 0f, 1f));
		return r * r * (3 - 2 * r);
	}

	private static float since(Player p, OmnitrixState s, float partial, int anim) {
		return s.animId == anim ? p.level().getGameTime() + partial - s.animStart : 9999f;
	}

	/** Whether the face shows red now. */
	public static boolean red(Player p, float partial) {
		OmnitrixState s = Omnitrix.state(p);
		long now = p.level().getGameTime();
		if (since(p, s, partial, Omnitrix.ANIM_DENIED) < 30f || since(p, s, partial, Omnitrix.ANIM_TIMEOUT) < 30f) {
			return true;
		}
		if (s.transformed()) {
			long left = s.endsAt - now;
			return left <= OmnitrixConfig.WARN_TICKS && (now / 5) % 2 == 0;
		}
		return now < s.rechargeUntil;
	}

	/**
	 * Draws the watch in the LEFT arm's space (call after {@code arm.translateAndRotate}, or inside a first-person
	 * {@code renderHand}). {@code slim}: the 3-pixel arm.
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player p, float partial, boolean slim, boolean firstPerson) {
		OmnitrixState s = Omnitrix.state(p);
		float L = slim ? 2f : 3f;
		float raise = raise(p, partial);
		boolean red = red(p, partial);
		ResourceLocation tex = red ? TEX_RED : TEX;
		VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
		// the strap: a dark band with a green stripe, and the clasp on the inner side
		AlienParts.box(pose, solid, -1.35f, 6.9f, -2.35f, L + 0.35f, 9.5f, 2.35f, 4 * T, 28 * T, 8 * T, 32 * T, 0xFFFFFFFF, light);
		AlienParts.box(pose, solid, -1.6f, 7.6f, -1.0f, -1.3f, 8.8f, 1.0f, 0, 28 * T, 4 * T, 32 * T, 0xFFFFFFFF, light);
		// the dial housing: a black block with green-lit corners
		AlienParts.box(pose, solid, L + 0.35f, 6.4f, -1.85f, L + 1.3f, 10.0f, 1.85f, 0, 28 * T, 4 * T, 32 * T, 0xFFFFFFFF, light);
		for (float[] c : new float[][] { { 6.4f, -1.85f }, { 9.4f, -1.85f }, { 6.4f, 1.25f }, { 9.4f, 1.25f } }) {
			AlienParts.box(pose, solid, L + 0.9f, c[0], c[1], L + 1.45f, c[0] + 0.6f, c[1] + 0.6f, 8 * T, 28 * T, 12 * T, 32 * T, 0xFFFFFFFF,
					LightTexture.FULL_BRIGHT);
		}
		// the core: pops up while picking, slammed down by the slap, twists a notch per dial turn
		float slap = since(p, s, partial, Omnitrix.ANIM_SLAP);
		float h = 0.55f + 1.6f * raise;
		if (slap < Omnitrix.SLAP_TICKS + 2) {
			h = Mth.lerp(Mth.clamp(slap / 2f, 0f, 1f), h, 0.25f);
		}
		float turn = since(p, s, partial, Omnitrix.ANIM_TURN);
		float twist = turn < 5f ? (1f - turn / 5f) * 40f : 0f;
		float cy = 8.2f;
		pose.pushPose();
		pose.translate((L + 1.3f) / 16f, cy / 16f, 0f);
		pose.mulPose(Axis.XP.rotationDegrees(twist));
		pose.translate(-(L + 1.3f) / 16f, -cy / 16f, 0f);
		float x0 = L + 1.3f;
		float x1 = x0 + h;
		int lit = Math.max(light, 0xA000A0);
		AlienParts.box(pose, solid, x0, cy - 1.1f, -1.1f, x1, cy + 1.1f, 1.1f, 4 * T, 28 * T, 8 * T, 32 * T, 0xFFFFFFFF, light);
		// a lit ring around the core's rim
		AlienParts.box(pose, solid, x1 - 0.35f, cy - 1.25f, -1.25f, x1 - 0.05f, cy + 1.25f, 1.25f, 8 * T, 28 * T, 12 * T, 32 * T, 0xFFFFFFFF, lit);
		// the face: the hourglass, or the plain lit disc with the alien's silhouette on it while picking
		VertexConsumer face = buffers.getBuffer(RenderType.eyes(tex));
		float fx = x1 + 0.02f;
		PoseStack.Pose last = pose.last();
		float sx = fx / 16f, a = (cy - 1.05f) / 16f, b = (cy + 1.05f) / 16f, za = -1.05f / 16f, zb = 1.05f / 16f;
		boolean picking = raise > 0.5f;
		float u0 = picking ? 0 : 16 * T, v0 = 16 * T, u1 = picking ? 12 * T : 1f, v1 = picking ? 28 * T : 1f;
		// picking: the face dims to a dark green screen and the silhouette glows on it, a raised copy hovering just over it
		AlienParts.quad(face, last, picking ? 0xFF1C3A1C : 0xFFFFFFFF, LightTexture.FULL_BRIGHT, sx, a, za, sx, a, zb, sx, b, zb, sx, b, za, 1, 0, 0, u0, v0,
				u1, v1);
		if (picking && !s.selected.isEmpty()) {
			VertexConsumer sil = buffers.getBuffer(RenderType.entityTranslucentEmissive(silhouette(s.selected)));
			int glow = red ? 0xFFFF6A5A : 0xFF9CFF8A;
			for (float[] layer : new float[][] { { 0.003f, 0.95f, 1f }, { 0.45f, 1.25f, 0.35f } }) {
				float sx2 = sx + layer[0] / 16f;
				float m = layer[1] / 16f;
				int c = 0xFF000000 | (Math.round(((glow >> 16) & 0xFF) * layer[2]) << 16) | (Math.round(((glow >> 8) & 0xFF) * layer[2]) << 8)
						| Math.round((glow & 0xFF) * layer[2]);
				// the figure stands upright as the wearer sees it (+z is up on the raised wrist in first person, -z seen from outside)
				float up = firstPerson ? m : -m;
				float y0 = cy / 16f - m, y1 = cy / 16f + m;
				AlienParts.quad(sil, last, c, LightTexture.FULL_BRIGHT, sx2, y0, up, sx2, y1, up, sx2, y1, -up, sx2, y0, -up, 1, 0, 0, 0, 0, 1, 1);
			}
		}
		pose.popPose();
		// the hologram over the dial
		if (!firstPerson && raise > 0.05f && !s.selected.isEmpty() && slap > Omnitrix.SLAP_TICKS) {
			float age = p.tickCount + partial;
			float hx = (x1 + 1.2f + 2.2f * raise + Mth.sin(age * 0.15f) * 0.15f) / 16f;
			float size = (2.4f + 1.4f * raise) / 16f;
			int alpha = (int) (200 * raise);
			int col = red ? 0xFF5040 : 0x7CFF6A;
			int hcol = (((((col >> 16) & 0xFF) * alpha / 255)) << 16) | (((((col >> 8) & 0xFF) * alpha / 255)) << 8) | ((col & 0xFF) * alpha / 255);
			VertexConsumer holo = buffers.getBuffer(RenderType.eyes(silhouette(s.selected)));
			PoseStack.Pose pl = pose.last();
			float c0 = cy / 16f;
			// it floats up off the dial ("up" on the arm is +z raised to the eye in first person, -z on the raised arm seen from outside)
			// in first person it hangs straight out of the core toward the eye (it reads over the face); seen from outside it floats
			// up off the dial
			float zc = firstPerson ? 0f : -(2.6f + 1.4f * raise) / 16f;
			hx = (x1 + (firstPerson ? 2.2f + 2.0f * raise : 0.8f + 1.2f * raise)) / 16f;
			if (firstPerson) {
				size *= 0.8f;
			}
			AlienParts.quad(holo, pl, 0xFF000000 | hcol, LightTexture.FULL_BRIGHT, hx, c0 + size, zc - size, hx, c0 + size, zc + size, hx, c0 - size, zc + size, hx,
					c0 - size, zc - size, 1, 0, 0, 0, 0, 1, 1);
			// a faint second copy a little further out: the hologram's depth
			float hx2 = hx + 0.25f / 16f;
			float s2 = size * 1.08f;
			AlienParts.quad(holo, pl, 0xFF000000 | ((hcol >> 2) & 0x3F3F3F), LightTexture.FULL_BRIGHT, hx2, c0 + s2, zc - s2, hx2, c0 + s2, zc + s2, hx2, c0 - s2,
					zc + s2, hx2, c0 - s2, zc - s2, 1, 0, 0, 0, 0, 1, 1);
			// the beam of light from the core up to it
			VertexConsumer beam = buffers.getBuffer(RenderType.eyes(TEX));
			float bw = 0.9f / 16f;
			int ba = (int) (90 * raise);
			int bcol = (((((col >> 16) & 0xFF) * ba / 255)) << 16) | (((((col >> 8) & 0xFF) * ba / 255)) << 8) | ((col & 0xFF) * ba / 255);
			AlienParts.quad(beam, pl, 0xFF000000 | bcol, LightTexture.FULL_BRIGHT, x1 / 16f, c0, -bw, hx, c0, zc - size, hx, c0, zc + size, x1 / 16f, c0, bw, 0, 1, 0,
					12 * T, 28 * T, 16 * T, 32 * T);
			AlienParts.quad(beam, pl, 0xFF000000 | bcol, LightTexture.FULL_BRIGHT, x1 / 16f, c0 - bw, 0, hx, c0 - size, zc, hx, c0 + size, zc, x1 / 16f,
					c0 + bw, 0, 0, 0, 1, 12 * T, 28 * T, 16 * T, 32 * T);
		}
	}
}
