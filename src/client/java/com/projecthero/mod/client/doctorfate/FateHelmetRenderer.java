package com.projecthero.mod.client.doctorfate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.doctorfate.helmet.FateHelmetEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * v0.17.4 the Helmet of Fate set down in the world: the suit skin's helmet (the same model as on his head), hovering a hand's width
 * above where it rests and bobbing, turning slowly, lit gold whatever the light, with a faint glow pulsing over it. A fallen helmet
 * tumbles as it drops. The flat golden ankh glowing under it is drawn by {@link FateWorldRender}.
 */
public class FateHelmetRenderer extends EntityRenderer<FateHelmetEntity> {
	public FateHelmetRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.25f;
		this.shadowStrength = 0.5f;
	}

	@Override
	public void render(FateHelmetEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		float t = e.tickCount + partial;
		pose.pushPose();
		float hover = e.falling() ? 0f : 0.22f + 0.06f * Mth.sin(t * 0.09f);
		pose.translate(0, hover + 0.25, 0);
		pose.mulPose(Axis.YP.rotationDegrees(180f - e.getYRot() + t * 1.2f));
		if (e.falling()) {
			pose.mulPose(Axis.XP.rotationDegrees(t * 14f));
		} else {
			pose.mulPose(Axis.ZP.rotationDegrees(3f * Mth.sin(t * 0.05f)));
		}
		pose.translate(0, -0.25, 0);
		pose.scale(-1f, -1f, 1f);
		float glow = 0.35f + 0.25f * (0.5f + 0.5f * Mth.sin(t * 0.12f));
		FateSuitLayer.renderHelmetAlone(pose, buffers, LightTexture.FULL_BRIGHT, 1f, glow);
		pose.popPose();
		super.render(e, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(FateHelmetEntity entity) {
		return FateSuitLayer.SUIT;
	}
}
