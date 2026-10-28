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
 * v0.17.1: the Omnitrix on the left wrist -- the classic watch built in voxels by {@link OmnitrixModel} (the faceted cuff, the
 * raised bezel and core, the white prongs, the button), drawn in the left arm's space in both third and first person.
 *
 * <p>Alien Evo style (user): V raises the wrist; the core pops up out of the housing and its face changes from the green
 * hourglass to the selected alien's silhouette glowing on a dark screen, a faint copy hovering just over the face (v0.17.1 polish:
 * the big third-person hologram is gone -- it floated over the shoulder and fought the raised-watch pose). The scroll wheel turns the
 * dial (the core twists a notch), a click slaps the core back down. The face goes red while the watch recharges, when V is
 * refused, and flashing in a transformation's last 15 s.
 */
public final class OmnitrixWatch {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/omnitrix/watch.png");
	public static final ResourceLocation TEX_RED = ProjectHeroMod.id("textures/entity/omnitrix/watch_red.png");

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
		// the core: pops up while picking, slammed down by the slap, twists a notch per dial turn
		float slap = since(p, s, partial, Omnitrix.ANIM_SLAP);
		float lift = 1.0f * raise;
		if (slap < Omnitrix.SLAP_TICKS + 2) {
			lift = Mth.lerp(Mth.clamp(slap / 2f, 0f, 1f), lift, -0.18f);
		}
		float turn = since(p, s, partial, Omnitrix.ANIM_TURN);
		float twist = turn < 5f ? (1f - turn / 5f) * 40f : 0f;
		boolean picking = raise > 0.5f;
		OmnitrixModel.draw(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, L, lift, twist, picking);
		float x1 = OmnitrixModel.coreTop(L, lift);
		float cy = OmnitrixModel.CY;
		// picking: the face is a dark green screen and the alien's icon glows lime on it (its dark outline drawn), a faint additive
		// copy hovering just over it
		if (picking && !s.selected.isEmpty()) {
			pose.pushPose();
			pose.translate(0f, cy / 16f, 0f);
			pose.mulPose(Axis.XP.rotationDegrees(twist));
			pose.translate(0f, -cy / 16f, 0f);
			PoseStack.Pose last = pose.last();
			float sx = (x1 + 0.01f) / 16f;
			int glow = red ? 0xFFFF5A48 : 0xFF8CFF3C;
			float cr = OmnitrixModel.CORE_R;
			for (float[] layer : new float[][] { { 0.004f, cr * 0.88f, 1f }, { 0.3f, cr * 0.92f, 0.26f } }) {
				float sx2 = sx + layer[0] / 16f;
				float m = layer[1] / 16f;
				int c = 0xFF000000 | (Math.round(((glow >> 16) & 0xFF) * layer[2]) << 16) | (Math.round(((glow >> 8) & 0xFF) * layer[2]) << 8)
						| Math.round((glow & 0xFF) * layer[2]);
				// the figure stands upright as the wearer sees it (+z is up on the raised wrist in first person, -z seen from outside)
				float up = firstPerson ? m : -m;
				float y0 = cy / 16f - m, y1 = cy / 16f + m;
				// (each buffer fetched just before it is written: fetching another may end the last one)
				VertexConsumer sil = buffers.getBuffer(layer[0] > 0.1f ? RenderType.eyes(silhouette(s.selected))
						: RenderType.entityTranslucentEmissive(silhouette(s.selected)));
				AlienParts.quad(sil, last, c, LightTexture.FULL_BRIGHT, sx2, y0, up, sx2, y1, up, sx2, y1, -up, sx2, y0, -up, 1, 0, 0, 0, 0, 1, 1);
			}
			pose.popPose();
		}
	}
}
