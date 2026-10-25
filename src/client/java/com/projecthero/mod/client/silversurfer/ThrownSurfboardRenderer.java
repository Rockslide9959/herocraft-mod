package com.projecthero.mod.client.silversurfer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.client.galactus.GalactusRenderers;
import com.projecthero.mod.silversurfer.ThrownSurfboardEntity;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** v0.17.1: G Board Strike's thrown board -- the Galactus board model spinning flat like a boomerang, tipped a little. */
public class ThrownSurfboardRenderer extends EntityRenderer<ThrownSurfboardEntity> {
	public ThrownSurfboardRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.3f;
	}

	@Override
	public void render(ThrownSurfboardEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees((e.tickCount + pt) * 47f));
		pose.mulPose(Axis.XP.rotationDegrees(12f));
		pose.translate(-0.5, -0.51, -0.5);
		SilverSurferRender.board().render(pose, GalactusRenderers.BoardAnimatable.INSTANCE, buffers, null, null,
				Math.max(light, LightTexture.pack(12, 12)), pt);
		pose.popPose();
		super.render(e, yaw, pt, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ThrownSurfboardEntity e) {
		return InventoryMenu.BLOCK_ATLAS;
	}
}
