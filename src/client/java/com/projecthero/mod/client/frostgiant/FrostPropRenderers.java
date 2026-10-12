package com.projecthero.mod.client.frostgiant;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.frostgiant.FrostGiantAnims;
import com.projecthero.mod.frostgiant.entity.IceCasketEntity;
import com.projecthero.mod.frostgiant.entity.IcePrisonEntity;
import com.projecthero.mod.frostgiant.entity.RuneBrazierEntity;
import com.projecthero.mod.frostgiant.entity.YmirArmEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * v0.16.1: the Frost Giant Assault's props on the client -- the Rune Brazier (a carved deepslate stand with a campfire
 * that goes cold when it is out), the ice prison (a translucent block of ice sized to its prisoner), the floating Casket
 * (GeckoLib, brighter while it can be hurt) and Ymir's arm (GeckoLib, ~40 blocks, never culled).
 */
public final class FrostPropRenderers {
	private FrostPropRenderers() {
	}

	// ---------------------------------------------------------------- the Rune Brazier

	public static final class Brazier extends EntityRenderer<RuneBrazierEntity> {
		public Brazier(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0.5f;
		}

		@Override
		public void render(RuneBrazierEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
			var blocks = Minecraft.getInstance().getBlockRenderer();
			int lit = e.isLit() ? LightTexture.FULL_BRIGHT : light;
			pose.pushPose();
			pose.translate(-0.4, 0, -0.4);
			pose.scale(0.8f, 1.0f, 0.8f);
			blocks.renderSingleBlock(Blocks.CHISELED_DEEPSLATE.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			pose.pushPose();
			pose.translate(-0.5, 1.0, -0.5);
			BlockState fire = (e.isLit() ? Blocks.CAMPFIRE : Blocks.SOUL_CAMPFIRE).defaultBlockState().setValue(CampfireBlock.LIT, e.isLit());
			blocks.renderSingleBlock(fire, pose, buffers, lit, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			// rune bands round the stand, brighter while lit
			if (e.isLit()) {
				pose.pushPose();
				pose.translate(-0.42, 0.45, -0.42);
				pose.scale(0.84f, 0.08f, 0.84f);
				blocks.renderSingleBlock(Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT,
						OverlayTexture.NO_OVERLAY);
				pose.popPose();
			}
			super.render(e, yaw, pt, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(RuneBrazierEntity e) {
			return InventoryMenu.BLOCK_ATLAS;
		}

		@Override
		protected int getBlockLightLevel(RuneBrazierEntity e, BlockPos pos) {
			return e.isLit() ? 15 : super.getBlockLightLevel(e, pos);
		}
	}

	// ---------------------------------------------------------------- the ice prison

	public static final class Prison extends EntityRenderer<IcePrisonEntity> {
		public Prison(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0.0f;
		}

		@Override
		public void render(IcePrisonEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
			var blocks = Minecraft.getInstance().getBlockRenderer();
			float w = e.getBbWidth();
			float h = e.getBbHeight();
			// it closes over the first few ticks
			float grow = Mth.clamp((e.tickCount + pt) / 4f, 0.2f, 1f);
			BlockState ice = e.crack() > 0.6f ? Blocks.FROSTED_ICE.defaultBlockState() : Blocks.ICE.defaultBlockState();
			pose.pushPose();
			pose.translate(-w / 2, 0, -w / 2);
			pose.scale(w, h * grow, w);
			blocks.renderSingleBlock(ice, pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			super.render(e, yaw, pt, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(IcePrisonEntity e) {
			return InventoryMenu.BLOCK_ATLAS;
		}
	}

	// ---------------------------------------------------------------- the floating Casket

	public static final class Casket extends GeoEntityRenderer<IceCasketEntity> {
		public static final float SCALE = 1.6f;

		public Casket(EntityRendererProvider.Context context) {
			super(context, new FixedModel<>(FrostGiantAnims.CASKET));
			this.shadowRadius = 0.0f;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		public void preRender(PoseStack poseStack, IceCasketEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
				VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
			if (!isReRender) {
				float pulse = animatable.isVulnerable() ? 1f + 0.06f * Mth.sin((animatable.tickCount + partialTick) * 0.6f) : 1f;
				poseStack.scale(SCALE * pulse, SCALE * pulse, SCALE * pulse);
				poseStack.mulPose(Axis.YP.rotationDegrees((animatable.tickCount + partialTick) * 1.5f));
			}
		}

		@Override
		public Color getRenderColor(IceCasketEntity animatable, float partialTick, int packedLight) {
			return animatable.isVulnerable() ? Color.ofARGB(255, 255, 255, 255) : Color.ofARGB(255, 170, 200, 255);
		}

		@Override
		protected int getBlockLightLevel(IceCasketEntity e, BlockPos pos) {
			return 15;
		}

		@Override
		protected float getDeathMaxRotation(IceCasketEntity animatable) {
			return 0f;
		}
	}

	// ---------------------------------------------------------------- Ymir's arm

	public static final class Ymir extends GeoEntityRenderer<YmirArmEntity> {
		public Ymir(EntityRendererProvider.Context context) {
			super(context, new FixedModel<>(FrostGiantAnims.YMIR));
			this.shadowRadius = 0.0f;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		public boolean shouldRender(YmirArmEntity entity, Frustum frustum, double x, double y, double z) {
			return true; // far bigger than its hit-box: never cull it
		}

		@Override
		protected int getBlockLightLevel(YmirArmEntity e, BlockPos pos) {
			return 12;
		}
	}

	/** A GeckoLib model at a fixed asset name (not per-entity). */
	static final class FixedModel<T extends software.bernie.geckolib.animatable.GeoAnimatable> extends GeoModel<T> {
		private final String name;

		FixedModel(String name) {
			this.name = name;
		}

		@Override
		public ResourceLocation getModelResource(T animatable) {
			return FrostModelFit.geo(name);
		}

		@Override
		public ResourceLocation getTextureResource(T animatable) {
			return FrostModelFit.texture(name);
		}

		@Override
		public ResourceLocation getAnimationResource(T animatable) {
			return FrostModelFit.animation(name);
		}
	}

}
