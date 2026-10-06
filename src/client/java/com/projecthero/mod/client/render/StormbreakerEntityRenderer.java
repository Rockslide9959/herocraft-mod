package com.projecthero.mod.client.render;

import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.19: the flying Stormbreaker -- its un-rotated 3D model ({@code stormbreaker_thrown.json}, handle-to-head along
 * local +Y like Mjolnir's) aligned to the direction of travel and tumbling end over end like a thrown axe.
 *
 * <p>v0.15.3: a resting axe ({@link StormbreakerEntity#isResting}) lies flat on the ground instead -- handle and head
 * along the yaw it landed with, the blade's broad face down -- the same model, no spin.
 */
public final class StormbreakerEntityRenderer extends EntityRenderer<StormbreakerEntity> {
	/** Degrees of tumble per tick -- a little over two full turns a second. */
	private static final float SPIN_DEGREES_PER_TICK = 40.0f;
	/**
	 * v0.15.3: lying flat, the model's thin Z extent (6.6..9.4 px, centred on 8) is what stands off the ground: lift by
	 * half of it at the FIXED display scale (1.15, {@code stormbreaker_thrown.json}) so it sits on the floor.
	 */
	private static final float FLAT_LIFT = 1.4f / 16.0f * 1.15f + 0.005f;
	/** v0.15.3: the model's handle-to-head centre sits at y 7.44 px, so the axe is slid back this far to centre it on the entity. */
	private static final float FLAT_CENTRE = (7.4375f - 8.0f) / 16.0f * 1.15f;

	private final ItemRenderer itemRenderer;
	private final ItemStack modelStack;

	public StormbreakerEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
		this.modelStack = new ItemStack(ModItems.STORMBREAKER_THROWN);
		this.shadowRadius = 0.25f;
	}

	@Override
	public void render(StormbreakerEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		if (entity.isResting()) {
			// v0.15.3: lying flat -- local +Y (handle -> head) along the landing yaw, the blade's flat (+Z) facing up
			poseStack.translate(0.0f, FLAT_LIFT, 0.0f);
			poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot()));
			poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
			poseStack.translate(0.0f, -FLAT_CENTRE, 0.0f);
			itemRenderer.renderStatic(this.modelStack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
					poseStack, buffer, entity.level(), entity.getId());
			poseStack.popPose();
			super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
			return;
		}
		float yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		float pitch = Mth.rotLerp(partialTicks, entity.xRotO, entity.getXRot());
		// face the way it flies (same frame as MjolnirEntityRenderer: after this, local +Z is the heading)...
		poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
		// ...then tumble end over end about the side-to-side axis (local X), head first over the top. v0.14.20: the model
		// is first turned a quarter about Y so its blade plane (handle +Y, blade +X) stands upright along the heading and
		// its flat (+Z) faces sideways -- a thrown axe spins in its own plane. Before, the spin axis ran through the blade,
		// so the blade stuck out sideways like a propeller while only the handle cartwheeled.
		float spin = (entity.tickCount + partialTicks) * SPIN_DEGREES_PER_TICK;
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0f + spin));
		poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
		itemRenderer.renderStatic(this.modelStack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
				poseStack, buffer, entity.level(), entity.getId());
		poseStack.popPose();
		super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
	}

	@Override
	public ResourceLocation getTextureLocation(StormbreakerEntity entity) {
		// unused -- renderStatic resolves the model's own textures
		return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
	}
}
