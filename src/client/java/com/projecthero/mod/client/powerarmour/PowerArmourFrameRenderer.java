package com.projecthero.mod.client.powerarmour;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.powerarmour.entity.PowerArmourFrameEntity;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** v0.17.5 Power Armour: a standing frame -- the voxel suit in its standing pose, opening and closing with its synced animation. */
public class PowerArmourFrameRenderer extends EntityRenderer<PowerArmourFrameEntity> {
	private final PlayerModel<LivingEntity> model;

	public PowerArmourFrameRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
		this.shadowRadius = 0.45f;
	}

	@Override
	public void render(PowerArmourFrameEntity frame, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float time = frame.level().getGameTime() + partial;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(180f - frame.getYRot()));
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		SuitLooks.standPose(model);
		SuitRenderer.render(pose, buffers, light, OverlayTexture.NO_OVERLAY, SuitLooks.parts(model), SuitLooks.frame(frame, time));
		pose.popPose();
		super.render(frame, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(PowerArmourFrameEntity frame) {
		return SuitRenderer.FRAME_TEX;
	}

	/** Draws a suit in its standing pose at the pose origin (feet), used by the editor preview. */
	public static void renderStanding(PlayerModel<?> model, PoseStack pose, MultiBufferSource buffers, int light, SuitRenderer.Look look) {
		pose.pushPose();
		pose.scale(-1f, -1f, 1f);
		pose.translate(0f, -1.501f, 0f);
		SuitLooks.standPose(model);
		SuitRenderer.render(pose, buffers, light, OverlayTexture.NO_OVERLAY, SuitLooks.parts(model), look);
		pose.popPose();
	}
}
