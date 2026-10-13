package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

/**
 * v0.16.1: the Hulkbuster body -- {@code geo/ironman/hulkbuster_body.geo.json}, {@code textures/armor/hulkbuster_body.png}
 * (+ {@code _glowmask}), {@code animations/ironman/hulkbuster_body.animation.json}, all from
 * {@code scratchpad/gen_hulkbuster_body.js}. The never-keyed {@code head} follows the look; the never-keyed {@code flinch}
 * jolts back when the wearer is hit. Shared by the worn body, the Sentry and the Rocket Fist.
 */
public class IronManHulkbusterModel<T extends GeoAnimatable> extends GeoModel<T> {
	public static final ResourceLocation GEO = ProjectHeroMod.id("geo/ironman/hulkbuster_body.geo.json");
	public static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/armor/hulkbuster_body.png");
	public static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/ironman/hulkbuster_body.animation.json");

	@Override
	public ResourceLocation getModelResource(T animatable) {
		return GEO;
	}

	@Override
	public ResourceLocation getTextureResource(T animatable) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(T animatable) {
		return ANIMATION;
	}

	@Override
	public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
		EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
		// head and flinch are never keyed by any clip, so they are set absolutely each frame
		getBone("head").ifPresent(head -> {
			head.setRotX(data == null ? 0f : data.headPitch() * Mth.DEG_TO_RAD); // same sign as the Hulk model
			head.setRotY(data == null ? 0f : data.netHeadYaw() * Mth.DEG_TO_RAD);
		});
		getBone("flinch").ifPresent(flinch -> {
			float jolt = 0f;
			if (state.getData(DataTickets.ENTITY) instanceof LivingEntity living && living.hurtTime > 0) {
				jolt = living.hurtTime / (float) Math.max(1, living.hurtDuration);
			}
			flinch.setRotX(0.22f * jolt);
		});
	}
}
