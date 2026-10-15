package com.projecthero.mod.client.bluebeetle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.bluebeetle.entity.ScarabDroneEntity;
import com.projecthero.mod.bluebeetle.item.BlueBeetleItems;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** v0.16.5: a Scarab Swarm drone -- the Scarab item drawn small, full-bright, nose-first and buzzing. */
public class ScarabDroneRenderer extends EntityRenderer<ScarabDroneEntity> {
	private final ItemRenderer items;

	public ScarabDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemRenderer();
		this.shadowRadius = 0f;
	}

	@Override
	public void render(ScarabDroneEntity drone, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		float age = drone.tickCount + partial;
		pose.translate(0, 0.15 + 0.05 * Mth.sin(age * 0.9f), 0);
		pose.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, drone.yRotO, drone.getYRot())));
		pose.mulPose(Axis.XP.rotationDegrees(90f)); // lying flat, head first
		pose.mulPose(Axis.ZP.rotationDegrees(8f * Mth.sin(age * 2.2f)));
		pose.scale(0.55f, 0.55f, 0.55f);
		items.renderStatic(new ItemStack(BlueBeetleItems.SCARAB), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				pose, buffers, drone.level(), drone.getId());
		pose.popPose();
		super.render(drone, yaw, partial, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(ScarabDroneEntity entity) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
