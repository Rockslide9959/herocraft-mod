package com.projecthero.mod.client.saiyan.hair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.3 (PREVIEW, not shipped): draws {@link SsjHairModel} on a player's head, following the head's yaw and pitch.
 * Off unless {@link #PREVIEW} is set (only the filming harness sets it, or {@code -Dprojecthero.ssjHairPreview=true}), so
 * nothing changes for players until the user accepts the look and it is wired to the SSJ form.
 */
public final class SsjHairRenderer {
	public static volatile boolean PREVIEW = Boolean.getBoolean("projecthero.ssjHairPreview");

	private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("projecthero", "textures/entity/saiyan/white.png");

	private SsjHairRenderer() {
	}

	public static boolean shows(Player player) {
		return PREVIEW && !player.isInvisible() && !player.isSpectator();
	}

	/** From PlayerModelMixin (setupAnim TAIL): the wearer's own hat layer goes while the hair covers the head. */
	public static void hideHat(Player player, PlayerModel<?> model) {
		if (shows(player)) {
			model.hat.visible = false;
		}
	}

	/** From SaiyanLayer. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model) {
		if (!shows(player) || !model.head.visible) {
			return;
		}
		// Super Saiyan hair glows: never darker than block light 13
		int lit = LightTexture.pack(Math.max(LightTexture.block(light), 13), Math.max(LightTexture.sky(light), 13));
		pose.pushPose();
		model.head.translateAndRotate(pose);
		PoseStack.Pose last = pose.last();
		// the toon outline first: an inside-out hull with back faces culled, so only its rim round each lock shows
		emit(buffers.getBuffer(RenderType.entityCutout(WHITE)), last, SsjHairModel.outline(), lit);
		emit(buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), last, SsjHairModel.mesh(), lit);
		pose.popPose();
	}

	private static void emit(VertexConsumer vc, PoseStack.Pose last, java.util.List<SsjHairModel.Quad> quads, int lit) {
		for (SsjHairModel.Quad q : quads) {
			float[] p = q.xyz();
			int[] c = q.rgb();
			for (int k = 0; k < 4; k++) {
				int rgb = c[k];
				// the colours are baked cel shading; an upward normal keeps the game's own lighting flat over them
				vc.addVertex(last.pose(), p[k * 3] / 16f, p[k * 3 + 1] / 16f, p[k * 3 + 2] / 16f)
						.setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255).setUv(0.5f, 0.5f)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(last, 0f, -1f, 0f);
			}
		}
	}
}
