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
 * (+ {@code _glowmask}), {@code animations/ironman/hulkbuster_body.animation.json}, the clips from
 * {@code scratchpad/gen_hulkbuster_body.js}. v0.16.7 (user): the body is a regular Steve player model x1.5 (3 blocks) in
 * the approved Era II skin with a raised plate layer, from {@code scratchpad/gen_hulkbuster_player_model.js}; it keeps the
 * old bone names (limbs cut at the elbow / wrist / knee / ankle) so the clips still play. The never-keyed {@code head} follows the look; the never-keyed {@code flinch}
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
		// v0.16.7 (user) Veronica: the mid-air build -- pieces in flight from the pod onto the body
		if (state.getData(DataTickets.ENTITY) instanceof net.minecraft.world.entity.player.Player player) {
			HulkbusterVeronicaClient.BuildPose build = HulkbusterVeronicaClient.buildPose(player, state.getPartialTick());
			if (build != null) {
				for (int part = 0; part < com.projecthero.mod.ironman.HulkbusterParts.COUNT; part++) {
					final float[] pos = build.pos[part];
					final float[] rot = build.rot[part];
					getBone(HulkbusterPartRender.rootBone(part)).ifPresent(b -> {
						b.setPosX(pos[0]);
						b.setPosY(pos[1]);
						b.setPosZ(pos[2]);
						b.setRotX(b.getRotX() + rot[0]);
						b.setRotY(b.getRotY() + rot[1]);
						b.setRotZ(b.getRotZ() + rot[2]);
					});
				}
			}
		}
	}
}
