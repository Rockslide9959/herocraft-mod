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
	 * v0.17.7: {@code stormbreaker_thrown.json}'s FIXED display scale. The model is 24 px long (y 0..24), so at 1.0 the
	 * flying / resting / pinned axe is 1.5 blocks, like the held one. Every offset below is derived from it.
	 */
	private static final float FIXED_SCALE = 1.0f;
	/**
	 * v0.16.1: a pinned axe's centre this high above the ground, and its lean. v0.17.7: measured from the ground found
	 * under the axe on the client ({@link #groundY}), not from the entity's y -- the client's copy of a just-pinned axe
	 * can sit up to a block inside the ground (it flew on until the next position sync), which buried the head. The head
	 * (model y 13.8..24) points down, its lowest point 16 px below the model centre (0.97 blocks with the lean), so about
	 * a third of the blade is sunk.
	 */
	private static final float PINNED_LIFT = 0.75f;
	private static final float PINNED_TILT = 14.0f;
	/**
	 * v0.15.3: lying flat, the model's thin Z extent is what stands off the ground: lift by half of it (v0.17.7: the rune
	 * band, 6.5..9.5 px, centred on 8) at the FIXED display scale so it sits on the floor.
	 */
	private static final float FLAT_LIFT = 1.5f / 16.0f * FIXED_SCALE + 0.005f;
	/** v0.15.3: slid back along its length so its middle (v0.17.7: y 12 of 0..24) sits on the entity. */
	private static final float FLAT_CENTRE = (12.0f - 8.0f) / 16.0f * FIXED_SCALE;
	/**
	 * v0.17.7: the tumble turns about this point of the axe (model y 15, where the haft meets the heavy head) instead of
	 * the model's y 8 -- a thrown axe spins round its balance point, and y 8 is a third of the way up the long haft.
	 */
	private static final float SPIN_PIVOT = (15.0f - 8.0f) / 16.0f * FIXED_SCALE;

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
		if (entity.isPinned()) {
			// v0.16.1 Thunder Field: driven blade-first into the ground, the haft standing up out of it, a little tilted
			double renderY = Mth.lerp(partialTicks, entity.yOld, entity.getY());
			poseStack.translate(0.0f, (float) (groundY(entity, renderY) - renderY) + PINNED_LIFT, 0.0f);
			poseStack.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
			poseStack.mulPose(Axis.XP.rotationDegrees(PINNED_TILT));
			poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
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
		poseStack.translate(0.0f, -SPIN_PIVOT, 0.0f);
		itemRenderer.renderStatic(this.modelStack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
				poseStack, buffer, entity.level(), entity.getId());
		poseStack.popPose();
		super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
	}

	/**
	 * v0.17.7: the top of the ground under a pinned axe -- the first air block with a solid one under it, scanning down
	 * from two blocks above the axe (it may be drawn inside the ground). Falls back to the axe's own y.
	 */
	private static double groundY(StormbreakerEntity entity, double y) {
		net.minecraft.core.BlockPos.MutableBlockPos m = net.minecraft.core.BlockPos.containing(entity.getX(), y + 2.0, entity.getZ()).mutable();
		for (int i = 0; i < 6; i++) {
			net.minecraft.core.BlockPos below = m.below();
			if (!entity.level().getBlockState(below).getCollisionShape(entity.level(), below).isEmpty()
					&& entity.level().getBlockState(m).getCollisionShape(entity.level(), m).isEmpty()) {
				return m.getY();
			}
			m.move(0, -1, 0);
		}
		return y;
	}

	@Override
	public ResourceLocation getTextureLocation(StormbreakerEntity entity) {
		// unused -- renderStatic resolves the model's own textures
		return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
	}
}
