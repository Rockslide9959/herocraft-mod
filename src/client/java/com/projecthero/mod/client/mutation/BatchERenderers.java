package com.projecthero.mod.client.mutation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hero.power.p22.ThornProjectile;
import com.projecthero.mod.hero.power.p22.ThornSentryEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

/**
 * v0.13.22 (batch E): entity renderers for Plant Manipulation's Thorn Sentry and its thorns. The sentry is built from
 * real block models (moss base, cactus stem, flowering-azalea head with a cactus "barrel") so it reads instantly as a
 * plant and matches every resource pack; it grows in over half a second, turns its head to its target, pulses on each
 * shot and withers away at the end of its life.
 */
public final class BatchERenderers {
	private BatchERenderers() {
	}

	public static final class ThornSentryRenderer extends EntityRenderer<ThornSentryEntity> {
		public ThornSentryRenderer(EntityRendererProvider.Context ctx) {
			super(ctx);
			this.shadowRadius = 0.4f;
		}

		@Override
		public void render(ThornSentryEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
			float size = e.sizeFactor(partial);
			if (size <= 0.01f) {
				return;
			}
			float age = e.tickCount + partial;
			// a quick swell each time it fires (SHOTS is bumped on the server; age-based pulse keeps it cheap)
			float pulse = 1.0f + 0.08f * Math.max(0.0f, 1.0f - ((age % ThornSentryEntity.FIRE_INTERVAL) / 3.0f)) * (e.shots() > 0 ? 1 : 0);
			pose.pushPose();
			pose.scale(size, size, size);

			// moss base
			pose.pushPose();
			pose.translate(-0.45, 0.0, -0.45);
			pose.scale(0.9f, 0.18f, 0.9f);
			blocks.renderSingleBlock(Blocks.MOSS_BLOCK.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();

			// cactus stem, swaying a little
			float sway = Mth.sin(age * 0.12f) * 2.0f;
			pose.pushPose();
			pose.mulPose(Axis.ZP.rotationDegrees(sway));
			pose.pushPose();
			pose.translate(-0.2, 0.15, -0.2);
			pose.scale(0.4f, 0.72f, 0.4f);
			blocks.renderSingleBlock(Blocks.CACTUS.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();

			// head: turns to face the target
			float headYaw = Mth.rotLerp(partial, e.yRotO, e.getYRot());
			pose.translate(0.0, 0.95, 0.0);
			pose.mulPose(Axis.YP.rotationDegrees(-headYaw));
			pose.scale(pulse, pulse, pulse);
			pose.pushPose();
			pose.translate(-0.3, -0.05, -0.3);
			pose.scale(0.6f, 0.45f, 0.6f);
			blocks.renderSingleBlock(Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			// the barrel -- a short cactus snout pointing forward (+Z after the yaw)
			pose.pushPose();
			pose.translate(-0.09, 0.08, 0.2);
			pose.scale(0.18f, 0.18f, 0.35f);
			blocks.renderSingleBlock(Blocks.CACTUS.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			pose.popPose();

			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(ThornSentryEntity e) {
			return TextureAtlas.LOCATION_BLOCKS;
		}
	}

	public static final class ThornRenderer extends EntityRenderer<ThornProjectile> {
		private static ModelPart thorn;

		public ThornRenderer(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public void render(ThornProjectile e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
			if (thorn == null) {
				thorn = MutationRender.bake(CubeListBuilder.create()
						.texOffs(0, 0).addBox(-0.6f, -0.6f, -3.5f, 1.2f, 1.2f, 7.0f)
						.texOffs(0, 0).addBox(-0.3f, -0.3f, -5.0f, 0.6f, 0.6f, 1.5f), 4, 4);
			}
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, e.yRotO, e.getYRot())));
			pose.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partial, e.xRotO, e.getXRot())));
			thorn.render(pose, buffers.getBuffer(RenderType.entitySolid(MutationRender.WHITE)), light, OverlayTexture.NO_OVERLAY,
					MutationRender.argb(255, 86, 132, 48));
			pose.popPose();
			super.render(e, yaw, partial, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(ThornProjectile e) {
			return MutationRender.WHITE;
		}
	}
}
