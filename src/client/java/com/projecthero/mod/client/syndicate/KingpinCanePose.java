package com.projecthero.mod.client.syndicate;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.syndicate.KingpinCaneSwing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.14.31: plays the Kingpin's Cane strikes ({@link KingpinCaneSwing}) over vanilla's arm swing.
 * <ul>
 *   <li>Every client-side main-hand {@code swing} of an entity holding the cane ({@code LivingEntityCaneSwingMixin}) --
 *       the local player's own click, another player's or the Kingpin's broadcast swing -- starts the next strike of
 *       the chain for that entity ({@link #onSwing}).</li>
 *   <li>Third person: the keyframes are blended over the humanoid model (players from {@code HumanoidModelMixin}, the
 *       Kingpin from {@link SyndicateModel}), and the held cane is turned to follow the arm by the frame's grip
 *       ({@link #grip}, applied by {@code ItemInHandLayerCaneMixin}).</li>
 *   <li>First person: the cane is moved about the hand ({@link #firstPerson}, from {@code ItemInHandRendererComboMixin},
 *       which holds vanilla's own swing and post-hit lowering off by {@link #firstPersonWeight} meanwhile).</li>
 * </ul>
 * Timed off each entity's own client tick count. The per-entity state is weakly keyed and cleared on disconnect.
 */
public final class KingpinCanePose {
	private static final class Track {
		int style = -1;
		long start = -1;
		float grip;
	}

	private static final Map<LivingEntity, Track> TRACKS = new WeakHashMap<>();

	private KingpinCanePose() {
	}

	public static void init() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	/** Client-side {@code LivingEntity.swing}: a main-hand swing of the cane starts the next strike. */
	public static void onSwing(LivingEntity entity, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || !KingpinCaneSwing.wielding(entity)) {
			return;
		}
		Track t = TRACKS.computeIfAbsent(entity, e -> new Track());
		long now = entity.tickCount;
		if (!KingpinCaneSwing.isNewStrike(t.start, now)) {
			return;
		}
		t.style = KingpinCaneSwing.nextStyle(t.style, t.start, now);
		t.start = now;
	}

	/** Ticks into {@code entity}'s playing strike, or -1 if none is playing (or it put the cane away). */
	private static float age(LivingEntity entity, float partial) {
		Track t = TRACKS.get(entity);
		if (t == null || t.style < 0 || !KingpinCaneSwing.wielding(entity)) {
			return -1f;
		}
		float age = entity.tickCount - t.start + partial;
		return age < 0 || age > KingpinCaneSwing.SWING_TICKS ? -1f : age;
	}

	/** The strike {@code entity} is playing (only meaningful while {@link #age} is not -1). */
	private static int style(LivingEntity entity) {
		Track t = TRACKS.get(entity);
		return t == null ? KingpinCaneSwing.OVERHEAD : t.style;
	}

	/** The strike {@code entity} is playing for tests / the harness, or -1. */
	public static int playing(LivingEntity entity) {
		return age(entity, 0f) < 0 ? -1 : style(entity);
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/** Third person: blend the playing strike over {@code m}. Run after vanilla's own setupAnim. */
	public static void apply(LivingEntity entity, HumanoidModel<?> m) {
		float tick = age(entity, partial());
		Track track = TRACKS.get(entity);
		if (tick < 0) {
			if (track != null) {
				track.grip = 0f;
			}
			return;
		}
		float[] p = KingpinCaneSwing.sample(KingpinCaneSwing.body(track.style), tick);
		if (p == null) {
			track.grip = 0f;
			return;
		}
		float w = KingpinCaneSwing.weight(tick);
		boolean left = entity.getMainArm() == HumanoidArm.LEFT;
		float sgn = left ? -1f : 1f;
		ModelPart wArm = left ? m.leftArm : m.rightArm;
		ModelPart fArm = left ? m.rightArm : m.leftArm;
		lerp(wArm, w, p[1], p[2] * sgn, p[3] * sgn);
		lerp(fArm, w, p[4], p[5] * sgn, p[6] * sgn);
		m.body.xRot = Mth.lerp(w, m.body.xRot, p[7]);
		m.body.yRot = Mth.lerp(w, m.body.yRot, p[8] * sgn);
		ModelPart frontLeg = left ? m.leftLeg : m.rightLeg;
		ModelPart backLeg = left ? m.rightLeg : m.leftLeg;
		frontLeg.xRot = Mth.lerp(w, frontLeg.xRot, frontLeg.xRot * 0.3f + p[9]);
		backLeg.xRot = Mth.lerp(w, backLeg.xRot, backLeg.xRot * 0.3f + p[10]);
		m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + p[11]);
		m.hat.copyFrom(m.head);
		// the shoulders turn with the torso (as vanilla's own attack swing does), so a twisted body keeps its arms on
		float bodyY = m.body.yRot;
		m.rightArm.z = Mth.sin(bodyY) * 5.0f;
		m.rightArm.x = -Mth.cos(bodyY) * 5.0f;
		m.leftArm.z = -Mth.sin(bodyY) * 5.0f;
		m.leftArm.x = Mth.cos(bodyY) * 5.0f;
		track.grip = p[12] * w;
	}

	/** How far (0..1) the held cane should be turned to follow the arm for {@code entity}'s current strike. */
	public static float grip(LivingEntity entity) {
		Track t = TRACKS.get(entity);
		return t == null ? 0f : t.grip;
	}

	/** First person: how much (0..1) a strike holds vanilla's swing and post-hit lowering off. */
	public static float firstPersonWeight(LivingEntity entity, float partial) {
		float tick = age(entity, partial);
		return tick < 0 ? 0f : Mth.clamp((KingpinCaneSwing.SWING_TICKS - tick) / 3f, 0f, 1f);
	}

	/** First person, just before the held cane is drawn (in the hand's space): chop / swipe / thrust it. */
	public static void firstPerson(LivingEntity entity, HumanoidArm arm, float partial, PoseStack pose) {
		float tick = age(entity, partial);
		if (tick < 0) {
			return;
		}
		float[] v = KingpinCaneSwing.sample(KingpinCaneSwing.firstPerson(style(entity)), tick);
		if (v == null) {
			return;
		}
		float sg = arm == HumanoidArm.RIGHT ? 1f : -1f;
		pose.translate(sg * v[1], v[2], v[3]);
		pose.mulPose(Axis.YP.rotationDegrees(sg * v[5]));
		pose.mulPose(Axis.XP.rotationDegrees(v[4]));
		pose.mulPose(Axis.ZP.rotationDegrees(sg * v[6]));
	}

	private static void lerp(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	/** World unload: forget every entity's chain. */
	public static void clear() {
		TRACKS.clear();
	}
}
