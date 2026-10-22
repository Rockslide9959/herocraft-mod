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
	/** v0.16.7 (user): how far the Hulkbuster's head turns / tips with the look (degrees). */
	static final float HEAD_YAW_LIMIT = 70f, HEAD_PITCH_LIMIT = 50f;

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
		// v0.16.7 (user) "when the hulkbuster suit spawns in the head is upside down or at a weird angle": GeckoLib hands
		// over head - body yaw UNWRAPPED (a body that has just appeared, been teleported or turned round to step out of the
		// suit can be 180 or 360 degrees off its head), so the head spun round / flipped for a moment. Work it out here
		// from the living entity's own interpolated rotations, wrapped to -180..180 and clamped to what the big neck can
		// turn; anything that is not a living body (the Sentry, the crate) looks straight ahead.
		float yawDeg = 0f, pitchDeg = 0f;
		if (state.getData(DataTickets.ENTITY) instanceof LivingEntity le) {
			float pt = state.getPartialTick();
			float body = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
			float headYaw = Mth.rotLerp(pt, le.yHeadRotO, le.yHeadRot);
			yawDeg = Mth.clamp(Mth.wrapDegrees(headYaw - body), -HEAD_YAW_LIMIT, HEAD_YAW_LIMIT);
			pitchDeg = Mth.clamp(Mth.wrapDegrees(Mth.lerp(pt, le.xRotO, le.getXRot())), -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT);
		}
		final float yawRad = -yawDeg * Mth.DEG_TO_RAD, pitchRad = -pitchDeg * Mth.DEG_TO_RAD; // GeckoLib's sign (data is negated)
		getBone("head").ifPresent(head -> {
			head.setRotX(pitchRad); // same sign as the Hulk model
			head.setRotY(yawRad);
			head.setRotZ(0f);
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
