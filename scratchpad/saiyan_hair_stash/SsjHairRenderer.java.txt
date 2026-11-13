package com.projecthero.mod.client.saiyan.hair;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.client.saiyan.SaiyanFx;
import com.projecthero.mod.saiyan.SaiyanForm;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: draws {@link SsjHairModel} (Goku's Super Saiyan hair, accepted by the user after four preview rounds) on every
 * Super Saiyan's head, for every viewer. It follows the head's yaw and pitch, grows out of the scalp over the first
 * {@link #GROW_TICKS} of the transformation, sways a little at rest and leans back with speed. Drawn from
 * {@code SaiyanLayer}, a render layer, so never in first person. The wearer's own hat layer is hidden while it shows
 * ({@link #hideHat}, from PlayerModelMixin).
 */
public final class SsjHairRenderer {
	private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("projecthero", "textures/entity/saiyan/white.png");
	/** How long the hair takes to grow in when the form starts. */
	public static final float GROW_TICKS = 6f;

	private SsjHairRenderer() {
	}

	/** Super Saiyan, visible. */
	public static boolean shows(Player player) {
		return !player.isInvisible() && !player.isSpectator() && SaiyanFx.form(player) == SaiyanForm.SSJ;
	}

	/** From PlayerModelMixin (setupAnim TAIL): the wearer's own hat layer goes while the hair covers the head. */
	public static void hideHat(Player player, PlayerModel<?> model) {
		if (shows(player)) {
			model.hat.visible = false;
		}
	}

	/** How far the hair has grown in (0..1), eased out. */
	public static float grow(Player player, float partial) {
		int tt = SaiyanFx.transformingTicks(player);
		if (tt == Integer.MAX_VALUE) {
			return 1f;
		}
		float g = Mth.clamp((tt + partial) / GROW_TICKS, 0f, 1f);
		float u = 1f - g;
		return 1f - u * u * u;
	}

	/** From SaiyanLayer. */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, float partial,
			float age) {
		if (!shows(player) || !model.head.visible) {
			return;
		}
		float grow = grow(player, partial);
		if (grow < 0.02f) {
			return;
		}
		// Super Saiyan hair glows: never darker than block light 13
		int lit = LightTexture.pack(Math.max(LightTexture.block(light), 13), Math.max(LightTexture.sky(light), 13));
		// the body's forward speed leans the locks back a little; a slow idle sway on top
		Vec3 v = player.getDeltaMovement();
		float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
		float fwd = (float) (-v.x * Math.sin(yaw) + v.z * Math.cos(yaw));
		float lean = Mth.clamp(fwd * 30f, -4f, 10f) + Mth.clamp((float) -v.y * 10f, -4f, 4f);
		float t = age + partial;
		float swayX = Mth.sin(t * 0.07f) * 1.2f;
		float swayZ = Mth.sin(t * 0.053f + 1.3f) * 1.0f;

		pose.pushPose();
		model.head.translateAndRotate(pose);
		// pivot at the top of the scalp (head model space: the head is y -8..0 pixels, up is -y)
		pose.translate(0f, -0.5f, 0f);
		// a positive X turn tips the hair toward the face, so "back" is negative
		pose.mulPose(Axis.XP.rotationDegrees(-lean + swayX));
		pose.mulPose(Axis.ZP.rotationDegrees(swayZ));
		pose.scale(grow, grow, grow);
		pose.translate(0f, 0.5f, 0f);
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
