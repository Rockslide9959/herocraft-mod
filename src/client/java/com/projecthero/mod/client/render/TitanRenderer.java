package com.projecthero.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.titan.entity.TitanBoulderEntity;
import com.projecthero.mod.titan.entity.TitanEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.14.4: the Titan's own renderer (it used to share {@link RaidZombieRenderer}). Same vanilla zombie
 * rig, texture and 18-block scale, but with the animated {@link TitanModel} and a layer that puts the
 * torn-up boulder in its hands while it winds up a throw.
 */
public class TitanRenderer extends AbstractZombieRenderer<TitanEntity, ZombieModel<TitanEntity>> {
	private final ResourceLocation texture;

	public TitanRenderer(EntityRendererProvider.Context context, ResourceLocation texture) {
		super(context,
				new TitanModel(context.bakeLayer(ModelLayers.ZOMBIE)),
				new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
				new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)));
		this.texture = texture;
		this.shadowRadius *= TitanEntity.SCALE;
		addLayer(new HeldBoulderLayer(this));
	}

	@Override
	public ResourceLocation getTextureLocation(Zombie entity) {
		return texture;
	}

	@Override
	protected void scale(TitanEntity entity, PoseStack poseStack, float partialTick) {
		super.scale(entity, poseStack, partialTick);
		poseStack.scale(TitanEntity.SCALE, TitanEntity.SCALE, TitanEntity.SCALE);
	}

	/** The rock, held in the hands from the scoop (tick ~11) until the release (tick 40) of the Boulder clip. */
	static final class HeldBoulderLayer extends RenderLayer<TitanEntity, ZombieModel<TitanEntity>> {
		HeldBoulderLayer(RenderLayerParent<TitanEntity, ZombieModel<TitanEntity>> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffers, int light, TitanEntity entity,
				float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
				float headPitch) {
			if (entity.clientAnim() != TitanEntity.Anim.BOULDER) {
				return;
			}
			float t = TitanAnimations.clipTime(entity, ageInTicks);
			if (t < 11.0f || t >= 40.0f) {
				return;
			}
			Block block = Block.byItem(TitanBoulderEntity.itemForGround(
					entity.level().getBlockState(entity.blockPosition().below())));
			BlockState state = block == Blocks.AIR ? Blocks.COBBLESTONE.defaultBlockState() : block.defaultBlockState();
			poseStack.pushPose();
			getParentModel().rightArm.translateAndRotate(poseStack);
			// From the right shoulder down to the fist, then across toward the left hand (both hold it).
			poseStack.translate(4.0f / 16.0f, 11.0f / 16.0f, 0.0f);
			float s = 0.5f;
			poseStack.scale(s, s, s);
			poseStack.translate(-0.5f, -0.5f, -0.5f);
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, poseStack, buffers, light,
					OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}
	}
}
