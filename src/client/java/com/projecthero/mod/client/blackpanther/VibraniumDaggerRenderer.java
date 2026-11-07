package com.projecthero.mod.client.blackpanther;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.blackpanther.entity.VibraniumDaggerEntity;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** v0.17.3: the thrown Vibranium Dagger: the dagger sprite spinning end over end along its flight. */
public class VibraniumDaggerRenderer extends EntityRenderer<VibraniumDaggerEntity> {
	public VibraniumDaggerRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0f;
	}

	@Override
	public void render(VibraniumDaggerEntity entity, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.translate(0f, 0.15f, 0f);
		pose.mulPose(Axis.YP.rotationDegrees(-this.entityRenderDispatcher.camera.getYRot()));
		pose.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partial) * 50f));
		pose.scale(1.1f, 1.1f, 1.1f);
		Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(BlackPantherItems.DAGGER), ItemDisplayContext.GROUND, light,
				OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
		pose.popPose();
		super.render(entity, yaw, partial, pose, buffers, light);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ResourceLocation getTextureLocation(VibraniumDaggerEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
