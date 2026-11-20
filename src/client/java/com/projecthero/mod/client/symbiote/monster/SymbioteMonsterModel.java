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
	/** v0.17.6: jaw drop past its rest (degrees) where the tongue starts to come out, and where it is all the way out. */
	static final float TONGUE_OPEN_FROM = 8f, TONGUE_OPEN_FULL = 20f;

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

	/** Rotates {@code bone} by {@code angle} about the model's up axis (expressed in its parent's frame), keeping its other rotation. */
	private static void turnAboutWorldUp(software.bernie.geckolib.cache.object.GeoBone bone, float angle) {
		org.joml.Quaternionf parents = new org.joml.Quaternionf();
		java.util.ArrayDeque<software.bernie.geckolib.cache.object.GeoBone> chain = new java.util.ArrayDeque<>();
		for (var b = bone.getParent(); b != null; b = b.getParent()) {
			chain.push(b);
		}
		for (var b : chain) { // root first: model <- root <- ... <- parent, each Rz * Ry * Rx (GeckoLib's order)
			parents.rotateZ(b.getRotZ()).rotateY(b.getRotY()).rotateX(b.getRotX());
		}
		org.joml.Vector3f up = parents.conjugate(new org.joml.Quaternionf()).transform(new org.joml.Vector3f(0, 1, 0));
		org.joml.Quaternionf own = new org.joml.Quaternionf().rotateZ(bone.getRotZ()).rotateY(bone.getRotY()).rotateX(bone.getRotX());
		org.joml.Quaternionf turned = new org.joml.Quaternionf().rotateAxis(angle, up.x, up.y, up.z).mul(own);
		org.joml.Vector3f e = turned.getEulerAnglesZYX(new org.joml.Vector3f());
		bone.setRotX(e.x);
		bone.setRotY(e.y);
		bone.setRotZ(e.z);
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
		// the yaw turns the head about the world's up axis -- its parent frames lean forward with the hunch, so a plain rotY would roll
		// it sideways. Every clip keys the head, so GeckoLib re-sets it each frame and this stays an offset on top of the clip.
		if (Math.abs(yawRad) > 1.0e-4) {
			getBone("head").ifPresent(head -> turnAboutWorldUp(head, yawRad));
		}
		tongueFollowsJaw();
	}

	/**
	 * v0.17.6 (user: "make the tongue only come out when the mouth opens like with the regular form"): the tongue's out-of-mouth chain
	 * ({@code tongue_1} and everything hung off it) is scaled by how far the jaw has dropped past its resting gape, so it grows out of the
	 * mouth as the jaw opens (roar, Devour, the attacks) and draws back behind the teeth as it shuts. Idle / walk / run only breathe the
	 * jaw a few degrees, so there the tongue stays in. {@code tongue_0}, the root lying on the jaw floor, is always there.
	 */
	private void tongueFollowsJaw() {
		var jawOpt = getBone("jaw");
		var outOpt = getBone("tongue_1");
		if (jawOpt.isEmpty() || outOpt.isEmpty()) {
			return;
		}
		var jaw = jawOpt.get();
		float rest = jaw.getInitialSnapshot().getRotX();
		float openDeg = (jaw.getRotX() - rest) * Math.signum(rest == 0f ? 1f : rest) * Mth.RAD_TO_DEG; // + = wider than the resting gape
		float out = Mth.clamp((openDeg - TONGUE_OPEN_FROM) / (TONGUE_OPEN_FULL - TONGUE_OPEN_FROM), 0f, 1f);
		out = out * out * (3f - 2f * out);
		var tongue = outOpt.get();
		tongue.setHidden(out < 0.02f);
		float sc = Math.max(out, 0.02f);
		tongue.setScaleX(sc);
		tongue.setScaleY(sc);
		tongue.setScaleZ(sc);
	}

}
