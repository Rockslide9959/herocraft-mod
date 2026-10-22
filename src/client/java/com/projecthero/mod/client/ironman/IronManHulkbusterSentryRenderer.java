package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.entity.IronManEntityTypes;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;

import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: a Mark 8 standing in Sentry Mode is drawn as the Hulkbuster body (not the armour on a stand): back panels open
 * = {@code sentry_idle}, closing = {@code sentry_close}, otherwise flying / walking / idle like the worn body.
 */
public class IronManHulkbusterSentryRenderer extends GeoReplacedEntityRenderer<IronManSentryEntity, IronManHulkbusterSentryRenderer.Body> {
	public IronManHulkbusterSentryRenderer(EntityRendererProvider.Context context) {
		super(context, new IronManHulkbusterModel<>(), Body.INSTANCE);
		this.shadowRadius = 1.0f;
		addRenderLayer(new AutoGlowingGeoLayer<>(this));
	}

	@Override
	public void preRender(com.mojang.blaze3d.vertex.PoseStack poseStack, Body animatable, software.bernie.geckolib.cache.object.BakedGeoModel model,
			net.minecraft.client.renderer.MultiBufferSource bufferSource, com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender,
			float partialTick, int packedLight, int packedOverlay, int colour) {
		if (!isReRender) {
			for (software.bernie.geckolib.cache.object.GeoBone bone : model.topLevelBones()) {
				IronManHulkbusterRenderer.showAll(bone);
			}
		}
		super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
	}

	/**
	 * v0.16.7 (user) Hulkbuster fixes: face the sentry's own yaw. The sentry is a plain {@code Entity}, for which GeckoLib
	 * passes a body yaw of 0 -- so the body always faced south, and an owner stepping out "backwards" (along the suit's
	 * facing) came out of its FRONT whenever they had not been facing south.
	 */
	@Override
	protected void applyRotations(Body animatable, com.mojang.blaze3d.vertex.PoseStack poseStack, float ageInTicks, float rotationYaw,
			float partialTick, float nativeScale) {
		float yaw = currentEntity == null ? rotationYaw : currentEntity.getViewYRot(partialTick);
		super.applyRotations(animatable, poseStack, ageInTicks, yaw, partialTick, nativeScale);
	}

	/** The sentry's animatable (one singleton, keyed per entity by GeckoLib). */
	public static final class Body implements GeoReplacedEntity {
		static final Body INSTANCE = new Body();
		private static final String P = "animation.hulkbuster_body.";
		private static final RawAnimation OPEN = RawAnimation.begin().thenLoop(P + "sentry_idle");
		private static final RawAnimation CLOSE = RawAnimation.begin().thenPlay(P + "sentry_close");
		private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(P + "idle");
		private static final RawAnimation WALK = RawAnimation.begin().thenLoop(P + "walk");
		private static final RawAnimation HOVER = RawAnimation.begin().thenLoop(P + "jet_hover");
		/** v0.16.7 (user): a wrecked Hulkbuster staggers, drops to one knee and stays down, smoking. */
		private static final RawAnimation WRECKED = RawAnimation.begin().thenPlay(P + "wreck_collapse").thenLoop(P + "wrecked");
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

		@Override
		public EntityType<?> getReplacingEntityType() {
			return IronManEntityTypes.SENTRY;
		}

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
			controllers.add(new AnimationController<>(this, "main", 4, this::main));
		}

		private PlayState main(AnimationState<Body> state) {
			if (!(state.getData(DataTickets.ENTITY) instanceof IronManSentryEntity s)) {
				return PlayState.STOP;
			}
			if (s.wrecked()) {
				return state.setAndContinue(WRECKED); // v0.16.7 (user): down on one knee until it reboots
			}
			if (s.isOpen()) {
				return state.setAndContinue(OPEN);
			}
			if (s.openProgress(state.getPartialTick()) > 0.02f) {
				return state.setAndContinue(CLOSE);
			}
			if (!s.onGround()) {
				return state.setAndContinue(HOVER);
			}
			if (s.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
				return state.setAndContinue(WALK);
			}
			return state.setAndContinue(IDLE);
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}
	}
}
