package com.projecthero.mod.client.galactus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.frostgiant.FrostModelFit;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.entity.GalactusEntity;
import com.projecthero.mod.galactus.entity.GalactusHandEntity;
import com.projecthero.mod.galactus.entity.GalactusMob;
import com.projecthero.mod.galactus.entity.PowerPylonEntity;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;
import com.projecthero.mod.galactus.entity.SurferBoardEntity;
import com.projecthero.mod.galactus.entity.WorldshipEntity;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.Color;

/**
 * v0.16.1: Galactus: The Devourer on the client. Every model is a GeckoLib asset at {@code geo/galactus/<name>.geo.json}
 * (+ texture, glowmask, animations), drawn at a size measured from its rest pose ({@link FrostModelFit}: the placeholders
 * today, the real models later, no code change): Galactus to {@link GalactusConfig.Colossus#renderHeight} (~60 blocks,
 * render only -- his hit-box is far smaller), the Surfer / Punishers / converters to their hit-box height, the grabbing
 * hand to 9 blocks, the Worldship to 40. Heads follow their look target through the never-keyed {@code neck} / {@code head}
 * bones; hurt flinches go on {@code flinch}. The weak points draw nothing (the server marks them with particles); the Power
 * Pylons are drawn with blocks.
 */
public final class GalactusRenderers {
	private GalactusRenderers() {
	}

	public static ResourceLocation geo(String model) {
		return ProjectHeroMod.id("geo/galactus/" + model + ".geo.json");
	}

	public static ResourceLocation texture(String model) {
		return ProjectHeroMod.id("textures/entity/galactus/" + model + ".png");
	}

	public static ResourceLocation animation(String model) {
		return ProjectHeroMod.id("animations/galactus/" + model + ".animation.json");
	}

	/** A model at a fixed asset name. */
	public static final class Fixed<T extends GeoAnimatable> extends GeoModel<T> {
		private final String name;

		public Fixed(String name) {
			this.name = name;
		}

		@Override
		public ResourceLocation getModelResource(T animatable) {
			return geo(name);
		}

		@Override
		public ResourceLocation getTextureResource(T animatable) {
			return texture(name);
		}

		@Override
		public ResourceLocation getAnimationResource(T animatable) {
			return animation(name);
		}
	}

	/** The per-entity model: its own asset name, the head look and the hurt flinch. */
	static final class MobModel<T extends GalactusMob> extends GeoModel<T> {
		@Override
		public ResourceLocation getModelResource(T animatable) {
			return geo(animatable.model());
		}

		@Override
		public ResourceLocation getTextureResource(T animatable) {
			return texture(animatable.model());
		}

		@Override
		public ResourceLocation getAnimationResource(T animatable) {
			return animation(animatable.model());
		}

		@Override
		public void setCustomAnimations(T mob, long instanceId, AnimationState<T> state) {
			EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
			float yaw = data == null ? 0.0f : Mth.clamp(data.netHeadYaw(), -50.0f, 50.0f);
			float pitch = data == null ? 0.0f : Mth.clamp(data.headPitch(), -30.0f, 30.0f);
			float w = mob.isDeadOrDying() ? 0f : mob.clientClip() != null ? 0.35f : 1.0f;
			GeoBone neck = getAnimationProcessor().getBone("neck");
			if (neck != null) {
				neck.setRotY(yaw * w * 0.5f * Mth.DEG_TO_RAD);
				neck.setRotX(pitch * w * 0.5f * Mth.DEG_TO_RAD);
			}
			GeoBone head = getAnimationProcessor().getBone("head");
			if (head != null) {
				head.setRotY(yaw * w * 0.5f * Mth.DEG_TO_RAD);
				head.setRotX(pitch * w * 0.5f * Mth.DEG_TO_RAD);
			}
			GeoBone flinch = getAnimationProcessor().getBone("flinch");
			if (flinch != null) {
				float k = 0.0f;
				if (mob.hurtTime > 0 && !mob.isDeadOrDying()) {
					float t = (10.0f - mob.hurtTime + state.getPartialTick()) / 10.0f;
					k = Mth.sin(Mth.clamp(t, 0.0f, 1.0f) * Mth.PI) * (1.0f - t * 0.5f);
				}
				flinch.setRotX(k * 6.0f * Mth.DEG_TO_RAD);
				flinch.setRotZ(k * 2.0f * Mth.DEG_TO_RAD);
			}
		}
	}

	// ---------------------------------------------------------------- the mobs

	public static class Mob<T extends GalactusMob> extends GeoEntityRenderer<T> {
		private final float fixedHeight;

		/** {@code fixedHeight} &lt;= 0: fit to the hit-box height. */
		public Mob(EntityRendererProvider.Context context, float shadow, float fixedHeight) {
			super(context, new MobModel<>());
			this.shadowRadius = shadow;
			this.fixedHeight = fixedHeight;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		protected float targetHeight(T animatable) {
			return fixedHeight > 0 ? fixedHeight : animatable.getBbHeight();
		}

		@Override
		public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
				boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
			if (!isReRender) {
				float s = FrostModelFit.fit(model, targetHeight(animatable));
				poseStack.scale(s, s, s);
			}
		}

		@Override
		protected float getDeathMaxRotation(T animatable) {
			return 0.0f;
		}

		@Override
		public int getPackedOverlay(T animatable, float u, float partialTick) {
			boolean flash = animatable.hurtTime > 0 && !animatable.isDeadOrDying();
			return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(flash));
		}
	}

	public static final class Colossus extends Mob<GalactusEntity> {
		public Colossus(EntityRendererProvider.Context context) {
			super(context, 6.0f, 0f);
		}

		@Override
		protected float targetHeight(GalactusEntity animatable) {
			return (float) GalactusConfig.galactus().renderHeight;
		}

		@Override
		public boolean shouldRender(GalactusEntity entity, Frustum frustum, double x, double y, double z) {
			return true; // far bigger than his hit-box: never cull him
		}

		@Override
		protected int getBlockLightLevel(GalactusEntity e, BlockPos pos) {
			return 12;
		}
	}

	public static final class Surfer extends Mob<SilverSurferEntity> {
		public Surfer(EntityRendererProvider.Context context) {
			super(context, 0.5f, 0f);
		}

		@Override
		public Color getRenderColor(SilverSurferEntity animatable, float partialTick, int packedLight) {
			return animatable.shieldUp() ? Color.ofARGB(255, 210, 230, 255) : super.getRenderColor(animatable, partialTick, packedLight);
		}

		@Override
		protected int getBlockLightLevel(SilverSurferEntity e, BlockPos pos) {
			return Math.max(10, super.getBlockLightLevel(e, pos));
		}
	}

	public static final class Hand extends Mob<GalactusHandEntity> {
		public Hand(EntityRendererProvider.Context context) {
			super(context, 0f, 9.0f);
		}

		@Override
		public boolean shouldRender(GalactusHandEntity entity, Frustum frustum, double x, double y, double z) {
			return true;
		}
	}

	// ---------------------------------------------------------------- the Worldship

	public static final class Worldship extends GeoEntityRenderer<WorldshipEntity> {
		public Worldship(EntityRendererProvider.Context context) {
			super(context, new Fixed<>(com.projecthero.mod.galactus.GalactusAnims.WORLDSHIP));
			this.shadowRadius = 0f;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		public void preRender(PoseStack poseStack, WorldshipEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
				VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
			if (!isReRender) {
				float s = FrostModelFit.fit(model, 40f);
				poseStack.scale(s, s, s);
			}
		}

		@Override
		public boolean shouldRender(WorldshipEntity entity, Frustum frustum, double x, double y, double z) {
			return true;
		}

		@Override
		protected int getBlockLightLevel(WorldshipEntity e, BlockPos pos) {
			return 11;
		}
	}

	// ---------------------------------------------------------------- the board (raw model scale: 16 px = 1 block)

	public static final class Board extends GeoEntityRenderer<SurferBoardEntity> {
		public Board(EntityRendererProvider.Context context) {
			super(context, new Fixed<>(com.projecthero.mod.galactus.GalactusAnims.BOARD));
			this.shadowRadius = 0.4f;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		protected int getBlockLightLevel(SurferBoardEntity e, BlockPos pos) {
			return Math.max(8, super.getBlockLightLevel(e, pos));
		}
	}

	// ---------------------------------------------------------------- the weak points: nothing (particles mark them)

	public static final class Invisible<T extends Entity> extends EntityRenderer<T> {
		public Invisible(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0f;
		}

		@Override
		public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
			return false;
		}

		@Override
		public ResourceLocation getTextureLocation(T entity) {
			return InventoryMenu.BLOCK_ATLAS;
		}
	}

	// ---------------------------------------------------------------- the Power Pylon: crying obsidian, violet glass, amethyst

	public static final class Pylon extends EntityRenderer<PowerPylonEntity> {
		public Pylon(EntityRendererProvider.Context context) {
			super(context);
			this.shadowRadius = 0.8f;
		}

		@Override
		public void render(PowerPylonEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
			var blocks = Minecraft.getInstance().getBlockRenderer();
			float h = e.getBbHeight();
			pose.pushPose();
			pose.translate(-0.7, 0, -0.7);
			pose.scale(1.4f, 1.2f, 1.4f);
			blocks.renderSingleBlock(Blocks.CRYING_OBSIDIAN.defaultBlockState(), pose, buffers, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			float pulse = 1f + 0.05f * Mth.sin((e.tickCount + pt) * 0.3f);
			pose.pushPose();
			pose.translate(-0.4 * pulse, 1.2, -0.4 * pulse);
			pose.scale(0.8f * pulse, h - 2.2f, 0.8f * pulse);
			blocks.renderSingleBlock(Blocks.PURPLE_STAINED_GLASS.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			pose.pushPose();
			pose.translate(-0.15, 1.2, -0.15);
			pose.scale(0.3f, h - 2.2f, 0.3f);
			blocks.renderSingleBlock(Blocks.AMETHYST_BLOCK.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			pose.pushPose();
			pose.translate(-0.5, h - 1.0, -0.5);
			blocks.renderSingleBlock(Blocks.AMETHYST_CLUSTER.defaultBlockState(), pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			super.render(e, yaw, pt, pose, buffers, light);
		}

		@Override
		public ResourceLocation getTextureLocation(PowerPylonEntity e) {
			return InventoryMenu.BLOCK_ATLAS;
		}

		@Override
		protected int getBlockLightLevel(PowerPylonEntity e, BlockPos pos) {
			return 15;
		}
	}
}
