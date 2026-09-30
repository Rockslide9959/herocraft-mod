package com.projecthero.mod.client.supersoldier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

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
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.8: the thrown Soldier's Shield -- the {@code soldier_shield} item model (a round red / white / blue disc with a
 * star, extruded one pixel thick by the generated item model) laid flat, tilted along its flight and spun like a discus.
 */
public class SoldierShieldRenderer extends EntityRenderer<SoldierShieldEntity> {
	private final ItemRenderer items;
	private ItemStack stack;

	public SoldierShieldRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemRenderer();
		this.shadowRadius = 0.15f;
	}

	@Override
	public void render(SoldierShieldEntity e, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
		if (stack == null) {
			stack = new ItemStack(SuperSoldierItems.SOLDIER_SHIELD);
		}
		Vec3 v = e.getDeltaMovement();
		float yaw = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.x, v.z) * Mth.RAD_TO_DEG) : 0.0f;
		float pitch = v.lengthSqr() > 1.0e-6 ? (float) (Mth.atan2(v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG) : 0.0f;
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(-pitch));
		pose.mulPose(Axis.XP.rotationDegrees(90.0f)); // face up: flat like a discus
		pose.mulPose(Axis.ZP.rotationDegrees((e.tickCount + partialTick) * 40.0f));
		pose.scale(1.1f, 1.1f, 1.1f);
		items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, e.level(), e.getId());
		pose.popPose();
		super.render(e, entityYaw, partialTick, pose, buffers, light);
	}

	@Override
	public ResourceLocation getTextureLocation(SoldierShieldEntity e) {
		return TextureAtlas.LOCATION_BLOCKS;
	}
}
