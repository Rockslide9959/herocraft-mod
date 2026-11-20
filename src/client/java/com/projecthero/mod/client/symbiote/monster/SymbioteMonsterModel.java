package com.projecthero.mod.client.symbiote.monster;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;

/**
 * v0.17.5 Symbiote Monster form: {@code geo/symbiote_monster.geo.json} + {@code animations/symbiote_monster.animation.json}, made by
 * {@code scratchpad/symbiote/gen_symbiote_monster_v0175.js}. The texture is the host's colour bake ({@link SymbioteMonsterTint}; the
 * renderer picks it per player). The never-keyed {@code neck} bone turns the head toward the look (on top of its hunched rest pose).
 */
public class SymbioteMonsterModel<T extends GeoAnimatable> extends GeoModel<T> {
	public static final ResourceLocation GEO = ProjectHeroMod.id("geo/symbiote_monster.geo.json");
	public static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/symbiote_monster.animation.json");
	static final float NECK_YAW_LIMIT = 50f, NECK_PITCH_LIMIT = 32f;

	@Override
	public ResourceLocation getModelResource(T animatable) {
		return GEO;
	}

	@Override
	public ResourceLocation getTextureResource(T animatable) {
		return SymbioteMonsterTint.CLASSIC;
	}

	@Override
	public ResourceLocation getAnimationResource(T animatable) {
		return ANIMATION;
	}

	@Override
	public void setCustomAnimations(T animatable, long instanceId, AnimationState<T> state) {
		float yawDeg = 0f, pitchDeg = 0f;
		if (state.getData(DataTickets.ENTITY) instanceof LivingEntity le) {
			float pt = state.getPartialTick();
			float body = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot);
			float headYaw = Mth.rotLerp(pt, le.yHeadRotO, le.yHeadRot);
			yawDeg = Mth.clamp(Mth.wrapDegrees(headYaw - body), -NECK_YAW_LIMIT, NECK_YAW_LIMIT);
			pitchDeg = Mth.clamp(Mth.wrapDegrees(Mth.lerp(pt, le.xRotO, le.getXRot())), -NECK_PITCH_LIMIT, NECK_PITCH_LIMIT);
		}
		final float yawRad = -yawDeg * Mth.DEG_TO_RAD, pitchRad = -pitchDeg * Mth.DEG_TO_RAD;
		getBone("neck").ifPresent(neck -> {
			var init = neck.getInitialSnapshot();
			neck.setRotX(init.getRotX() + pitchRad * 0.8f);
			neck.setRotY(init.getRotY());
			neck.setRotZ(init.getRotZ());
		});
		// the yaw goes on the head (its frame is upright; the neck's leans with the hunch). Every clip keys the head, so GeckoLib
		// re-sets it each frame and this stays an offset.
		getBone("head").ifPresent(head -> head.setRotY(head.getRotY() + yawRad));
	}
}
