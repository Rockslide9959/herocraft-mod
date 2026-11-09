package com.projecthero.mod.client.andy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyBoneSword;
import com.projecthero.mod.andy.AndyConfig;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.client.render.VanillaSkeleton;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * v0.17.3: the Bone Sword ({@link AndyBoneSword}) -- his own torn-off arm held as a sword. The blade is the arm's bone (the
 * vanilla skeleton's arm bone, the same one the regrowth pushes out of a stump, drawn longer) with jagged bone edges and a
 * point; dark blood streaks run along it, drops swell on its blood and fall off to splat on the ground; the crossguard is a
 * glossy, blobby lump of congealed blood with drops hanging from it; the grip is wrapped in the arm's torn skin with raw flesh
 * showing between, the knuckle of the bone as the pommel.
 *
 * <p>Sword space: model px / 16 units, +y down, the grip round the origin and the blade running toward -z. It is held exactly
 * as a vanilla sword is ({@link #held}: the iron sword's own display transforms, first and third person), drawn in his hand
 * ({@link #thirdPerson}), in first person ({@link #firstPerson}) and as the thrown Impale Throw ({@link #mesh}, from
 * {@link AndyPartRenderer}). Also the hold-V draw -- reaching over, struggling, the arm torn off and gripped until it bursts
 * round the blade -- and the Rib Cage Guard's ribs ({@link #ribs}).
 */
public final class AndyBoneSwordRender {
	// colours
	static final int BONE = 0xE6DFC8, BONE_SHADE = 0xCFC4A6, BONE_DARK = 0xA89A7C;
	static final int BLOOD = 0x6E0A12, BLOOD_DARK = 0x45040A, BLOOD_GLOSS = 0xC4222E, BLOOD_WET = 0x9A1018;
	static final int SKIN = 0xC48C6A, SKIN_DARK = 0x9E6A4E, FLESH = 0x8C1C22;

	/** The palette's plain white texel: everything here is tinted from it. */
	private static final float WU0 = 5.3f / 16f, WV0 = 0.3f / 16f, WU1 = 5.7f / 16f, WV1 = 0.7f / 16f;

	/** The blade's tip in sword space (px). */
	static final float TIP_Z = -24.2f;

	/** The vanilla sword whose display transforms the Bone Sword is held by. */
	private static final ItemStack REFERENCE = new ItemStack(Items.IRON_SWORD);
	/**
	 * Item space (the 16 px item cube, 0..1) to sword space: the grip where a sword sprite's hand is, the blade up the sprite's
	 * diagonal (toward +x +y), its flat facing the item's front like the sprite's, its bottom edge (+y down) to the lower right.
	 */
	static final float GRIP_X = 0.24f, GRIP_Y = 0.24f, HELD_SCALE = 0.78f;
	private static final Quaternionf ITEM_TO_SWORD;

	static {
		float k = (float) (1.0 / Math.sqrt(2.0));
		// columns: sword x -> item +z, sword y -> (-1,1,0)/sqrt2 (its bottom edge, the blood runs and guard drops, hangs down when
		// held in third person), sword z -> (-1,-1,0)/sqrt2
		ITEM_TO_SWORD = new Quaternionf().setFromNormalized(new Matrix3f(0f, 0f, 1f, -k, k, 0f, -k, -k, 0f));
	}

	private AndyBoneSwordRender() {
	}

	// ---------------------------------------------------------------- the poses

	/** Hold V: how hard the wrench is at {@code age} ticks into the struggle (0..1): pulses that come quicker and harder. */
	public static float strugglePull(float age) {
		if (age < 6f) {
			return 0f;
		}
		float t = age - 6f;
		float beat = Mth.sin(t * (0.62f + t * 0.012f) - 0.4f);
		return Math.max(0f, beat) * Math.max(0f, beat) * Mth.clamp(0.45f + t / 40f, 0f, 1f);
	}

	private static float struggleShake(float age) {
		float s = Mth.clamp((age - 4f) / 26f, 0f, 1f);
		return (Mth.sin(age * 2.9f) * 0.07f + Mth.sin(age * 5.3f + 1f) * 0.045f) * (0.25f + 0.75f * s);
	}

	/**
	 * From {@link AndyPose#apply}: the sword arm while the Bone Sword is out (vanilla's held-item arm, the attack swing kept), the
	 * hold-V struggle, the rip and the grip, Tear Off, the put-away, and each sword move. True if it set the pose.
	 */
	static boolean pose(Player player, AndyState s, net.minecraft.client.model.HumanoidModel<?> model, float now) {
		float age = now - s.animStart;
		int handPart = s.boneSword() ? AndyBoneSword.hand(s) : Andy.mainArm(player);
		ModelPart arm = handPart == AndyState.RIGHT_ARM ? model.rightArm : model.leftArm;
		ModelPart other = handPart == AndyState.RIGHT_ARM ? model.leftArm : model.rightArm;
		float sd = handPart == AndyState.RIGHT_ARM ? 1f : -1f;
		float aim = model.head.xRot - Mth.HALF_PI;
		if (!s.boneSword()) {
			if (s.animId == AndyState.ANIM_SWORD_STRUGGLE && age >= 0f && age < AndyConfig.BONE_SWORD_HOLD + 4f) {
				// the sword hand reaches across his chest to the off shoulder and wrenches; the off arm is dragged forward and
				// across, the body twisting into each pull, the head turned down to it
				float r = ease(age / 6f), st = Mth.clamp((age - 6f) / 28f, 0f, 1f), pull = strugglePull(age), shake = struggleShake(age);
				set(arm, r, -1.3f - 0.18f * pull + shake, -sd * (0.95f + 0.12f * pull), sd * (0.05f + 0.2f * pull));
				set(other, r, -0.45f - 0.4f * pull + shake * 0.7f, sd * (0.25f + 0.25f * pull), -sd * (0.18f - 0.12f * pull) + shake * 0.5f);
				model.body.yRot += sd * (0.22f * r + 0.16f * pull);
				model.head.xRot += 0.35f * r;
				model.head.yRot -= sd * (0.4f * r - 0.1f * pull);
				model.head.zRot += sd * 0.12f * pull;
				return true;
			}
			if (s.animId == AndyState.ANIM_TEAR_OFF && age >= 0f && age < 10f) {
				tearOffPose(player, s, model, age);
				return true;
			}
			if (s.animId == AndyState.ANIM_SWORD_IMPALE && age >= 0f && age < 10f) {
				float t = ease(age / 4f);
				set(arm, fade(age, 10f), Mth.lerp(t, -2.9f, aim + 0.3f), model.head.yRot, 0f); // over the shoulder and thrown
				model.body.yRot -= 0.3f * sd * fade(age, 10f) * t;
				return true;
			}
			if (s.animId == AndyState.ANIM_SWORD_AWAY && age >= 0f && age < 8f) {
				set(arm, fade(age, 8f), -1.2f, 0.2f * sd, -0.1f * sd); // the hand opening as it bursts
				return true;
			}
			return false;
		}
		if (s.animId == AndyState.ANIM_SWORD_RIP && age >= 0f && age < AndyConfig.BONE_SWORD_REVEAL + 6f) {
			// torn off: the arm swung round in front of him and gripped hard, shaking, until it bursts; then it settles into the
			// held pose
			float e = ease(age / 4f);
			float w = age < AndyConfig.BONE_SWORD_REVEAL ? 1f : fade(age - AndyConfig.BONE_SWORD_REVEAL + 3f, 9f);
			float k = Mth.clamp(age / AndyConfig.BONE_SWORD_REVEAL, 0f, 1f);
			float tremble = (Mth.sin(age * 3.7f) * 0.05f + Mth.sin(age * 6.1f) * 0.03f) * k;
			float jolt = age >= AndyConfig.BONE_SWORD_REVEAL ? 0.35f * fade(age - AndyConfig.BONE_SWORD_REVEAL + 3f, 6f) : 0f;
			set(arm, w, Mth.lerp(e, -1.4f, -1.05f) + tremble - jolt, Mth.lerp(e, -sd * 1.0f, -sd * 0.2f), sd * 0.08f);
			model.body.yRot += Mth.lerp(e, sd * 0.35f, 0f) * w;
			model.head.xRot += 0.3f * w;
			return true;
		}
		// held: up off the hip like a held item (added, so vanilla's attack swing stays)
		arm.xRot -= 0.3f;
		if (age < 0f) {
			return true;
		}
		switch (s.animId) {
			case AndyState.ANIM_SWORD_SLASH1, AndyState.ANIM_SWORD_SLASH2, AndyState.ANIM_SWORD_COUNTER -> {
				if (age < 8f) {
					float t = ease(age / 4f);
					float dir = s.animId == AndyState.ANIM_SWORD_SLASH2 ? -1f : 1f;
					// the forehand comes from high on the sword side across and down; the backhand back the other way
					set(arm, fade(age, 8f), Mth.lerp(t, -2.3f, -1.1f), Mth.lerp(t, 0.9f, -1.0f) * sd * dir, Mth.lerp(t, 0.3f, -0.2f) * sd * dir);
					model.body.yRot += Mth.lerp(t, 0.3f, -0.35f) * sd * dir * fade(age, 8f);
				}
			}
			case AndyState.ANIM_SWORD_SLASH3 -> {
				if (age < 12f) {
					float t = ease(age / 6f);
					set(arm, fade(age, 12f), -1.6f, Mth.lerp(t, 1.5f, -1.6f) * sd, 0f);
					model.body.yRot += Mth.lerp(t, 0.6f, -0.7f) * sd * fade(age, 12f);
				}
			}
			case AndyState.ANIM_SWORD_THRUST, AndyState.ANIM_SWORD_DASH -> {
				if (age < 10f) {
					float w = fade(age, 10f);
					set(arm, w, aim, model.head.yRot, 0f);
					model.body.xRot += 0.3f * w;
					model.body.yRot -= 0.3f * sd * w;
				}
			}
			case AndyState.ANIM_SWORD_EDGE -> {
				if (age < 12f) {
					float t = ease(age / 5f);
					set(arm, fade(age, 12f), Mth.lerp(t, -3.0f, -0.45f), model.head.yRot, 0f);
					model.body.xRot += 0.25f * t * fade(age, 12f);
				}
			}
			case AndyState.ANIM_SWORD_GUARD, AndyState.ANIM_SWORD_PARRY -> {
				float len = s.animId == AndyState.ANIM_SWORD_GUARD ? AndyBoneSword.GUARD_TICKS : AndyBoneSword.PARRY_TICKS + 4f;
				if (age < len) {
					set(arm, Math.min(ease(age / 2f), fade(age, len)), -1.45f, 0.75f * sd, 0.3f * sd); // the blade across the chest
				}
			}
			case AndyState.ANIM_SWORD_SPLINTER -> {
				if (age < 10f) {
					set(arm, fade(age, 10f), aim + Mth.sin(age * 3f) * 0.08f, model.head.yRot, 0f);
				}
			}
			default -> {
			}
		}
		return true;
	}

	/** Tap V: the hand that does it grabs the limb (across the chest, or down at the hip) and wrenches it off and away. */
	private static void tearOffPose(Player player, AndyState s, net.minecraft.client.model.HumanoidModel<?> model, float age) {
		int limb = (int) s.ex(AndyState.X_TORN);
		int main = Andy.mainArm(player);
		int ripper = limb == main ? Andy.otherArm(main) : main;
		ModelPart arm = ripper == AndyState.RIGHT_ARM ? model.rightArm : model.leftArm;
		float q = ripper == AndyState.RIGHT_ARM ? 1f : -1f;
		float grab = ease(age / 2.5f), fling = ease((age - 3f) / 3f), w = fade(age, 10f);
		boolean armLimb = limb == AndyState.RIGHT_ARM || limb == AndyState.LEFT_ARM;
		if (armLimb) {
			set(arm, w * grab, Mth.lerp(fling, -1.35f, -1.0f), Mth.lerp(fling, -q * 0.95f, q * 0.9f), Mth.lerp(fling, 0f, q * 0.6f));
			model.body.yRot += Mth.lerp(fling, -q * 0.15f, q * 0.35f) * w;
		} else {
			boolean same = (limb == AndyState.RIGHT_LEG) == (ripper == AndyState.RIGHT_ARM);
			set(arm, w * grab, Mth.lerp(fling, -0.25f, -1.6f), Mth.lerp(fling, same ? 0f : -q * 0.4f, q * 0.5f), Mth.lerp(fling, same ? q * 0.1f : -q * 0.2f,
					q * 0.7f));
			model.body.xRot += 0.35f * (1f - fling) * w;
		}
		model.head.xRot += 0.25f * w;
	}

	private static void set(ModelPart part, float weight, float x, float y, float z) {
		part.xRot = Mth.lerp(weight, part.xRot, x);
		part.yRot = Mth.lerp(weight, part.yRot, y);
		part.zRot = Mth.lerp(weight, part.zRot, z);
	}

	// ---------------------------------------------------------------- held like a vanilla sword

	/**
	 * Applies the vanilla iron sword's display transform for {@code context} (as {@code ItemRenderer} does before drawing an item)
	 * and then the item-to-sword placement: after this the pose is at sword space with the Bone Sword lying where a vanilla
	 * sword's sprite would.
	 */
	static void held(PoseStack pose, Player player, ItemDisplayContext context, boolean left) {
		BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(REFERENCE, player.level(), player, 0);
		model.getTransforms().getTransform(context).apply(left, pose);
		pose.translate(-0.5f, -0.5f, -0.5f);
		pose.translate(GRIP_X, GRIP_Y, 0.5f);
		pose.mulPose(ITEM_TO_SWORD);
		pose.scale(HELD_SCALE, HELD_SCALE, HELD_SCALE);
	}

	// ---------------------------------------------------------------- where it is drawn

	/** In his hand (from {@link AndyRender#renderLayer}, the pose at the model), plus the Rib Cage Guard. */
	static void thirdPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, AndyState s, float now) {
		float age = now - s.animStart;
		if (s.animId == AndyState.ANIM_SWORD_GUARD && age >= 0f && age < AndyBoneSword.GUARD_TICKS) {
			pose.pushPose();
			model.body.translateAndRotate(pose);
			ribs(pose, buffers, light, age);
			pose.popPose();
		}
		if (!s.boneSword()) {
			return;
		}
		int hand = AndyBoneSword.hand(s);
		if (s.broken(hand)) {
			return;
		}
		boolean left = hand == AndyState.LEFT_ARM;
		pose.pushPose();
		// exactly as ItemInHandLayer places a held item
		model.translateToHand(left ? HumanoidArm.LEFT : HumanoidArm.RIGHT, pose);
		pose.mulPose(Axis.XP.rotationDegrees(-90f));
		pose.mulPose(Axis.YP.rotationDegrees(180f));
		pose.translate((left ? -1f : 1f) / 16f, 0.125f, -0.625f);
		held(pose, player, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left);
		drawHeld(pose, buffers, light, player, s, age, now, new Vector3f(0f, -1f, 0f), false);
		pose.popPose();
	}

	/**
	 * The thing in the sword hand at sword space: while the torn arm is gripped (the first {@link AndyConfig#BONE_SWORD_REVEAL}
	 * ticks of the rip) the arm itself, squeezed and shaking; then the sword, popping out of the burst; its drips.
	 * {@code down}: which way world-down is in the pose's own frame (before this pose), for the drops.
	 */
	private static void drawHeld(PoseStack pose, MultiBufferSource buffers, int light, Player player, AndyState s, float age, float now, Vector3f down,
			boolean firstPerson) {
		if (s.animId == AndyState.ANIM_SWORD_RIP && age >= 0f && age < AndyConfig.BONE_SWORD_REVEAL) {
			float k = Mth.clamp(age / AndyConfig.BONE_SWORD_REVEAL, 0f, 1f);
			pose.pushPose();
			// the grip tightening: it shakes harder and swells as it is crushed
			pose.mulPose(Axis.XP.rotationDegrees((Mth.sin(age * 4.3f) * 2.5f + Mth.sin(age * 7.7f) * 1.5f) * k));
			pose.mulPose(Axis.YP.rotationDegrees((Mth.sin(age * 5.1f + 1f) * 2f) * k));
			severedArm(pose, buffers, light, player, s.boneSwordArm(), 1f + 0.06f * Mth.sin(age * 2.6f) * k + 0.14f * k * k, k);
			pose.popPose();
			return;
		}
		if (s.animId == AndyState.ANIM_SWORD_RIP && age >= AndyConfig.BONE_SWORD_REVEAL && age < AndyConfig.BONE_SWORD_REVEAL + 5f) {
			float t = (age - AndyConfig.BONE_SWORD_REVEAL) / 5f;
			float pop = 1f + 0.3f * (1f - t) * (1f - t);
			pose.scale(pop, pop, pop);
		}
		mesh(pose, buffers, light, now, true);
		drips(pose, buffers, light, player, now, down, firstPerson);
	}

	/**
	 * First person, from {@link AndyFirstPerson#arms}: the sword held exactly where vanilla holds a sword, swinging with the attack
	 * and with each sword move; the hold-V struggle (both hands); the gripped arm. True if it drew (the caller cancels vanilla's
	 * hand).
	 */
	static boolean firstPerson(ItemInHandRenderer renderer, AbstractClientPlayer player, AndyState s, float partial, InteractionHand hand, PoseStack pose,
			MultiBufferSource buffers, int light) {
		float now = player.level().getGameTime() + partial;
		float age = now - s.animStart;
		if (!s.boneSword()) {
			if (s.animId == AndyState.ANIM_SWORD_STRUGGLE && age >= 0f && age < AndyConfig.BONE_SWORD_HOLD + 4f) {
				if (hand == InteractionHand.MAIN_HAND) {
					struggleFirstPerson(renderer, player, age, pose, buffers, light);
				}
				return true;
			}
			return false;
		}
		int arm = AndyBoneSword.hand(s);
		boolean mainHand = arm == Andy.mainArm(player);
		if ((hand == InteractionHand.MAIN_HAND) != mainHand) {
			return false; // the other hand (the torn-off arm's) is vanilla's / the missing-hand rule's
		}
		boolean right = arm == AndyState.RIGHT_ARM;
		float sd = right ? 1f : -1f;
		float sw = player.swingingArm == hand ? player.getAttackAnim(partial) : 0f;
		float sq = Mth.sqrt(sw);
		pose.pushPose();
		// the torn arm swung in from where the hand grabbed it
		if (s.animId == AndyState.ANIM_SWORD_RIP && age >= 0f && age < AndyConfig.BONE_SWORD_REVEAL + 8f) {
			// torn off: swung in from where the hand grabbed it, then held up into view, gripped hard, until it bursts; then
			// down into the held place
			float in = 1f - ease(age / 4f);
			float up = age < AndyConfig.BONE_SWORD_REVEAL ? ease(age / 5f) : 1f - ease((age - AndyConfig.BONE_SWORD_REVEAL - 1f) / 7f);
			pose.translate(-sd * (0.5f * in + 0.2f * up), 0.12f * in + 0.24f * up, 0.05f * in - 0.12f * up);
			pose.mulPose(Axis.ZP.rotationDegrees(sd * (30f * in + 12f * up)));
			pose.mulPose(Axis.XP.rotationDegrees(-10f * up));
		}
		// vanilla's renderArmWithItem for a held item: the swing, applyItemArmTransform, applyItemArmAttackTransform
		pose.translate(sd * -0.4f * Mth.sin(sq * Mth.PI), 0.2f * Mth.sin(sq * Mth.TWO_PI), -0.2f * Mth.sin(sw * Mth.PI));
		pose.translate(sd * 0.56f, -0.52f, -0.72f);
		float f = Mth.sin(sw * sw * Mth.PI);
		pose.mulPose(Axis.YP.rotationDegrees(sd * (45f + f * -20f)));
		float g = Mth.sin(sq * Mth.PI);
		pose.mulPose(Axis.ZP.rotationDegrees(sd * g * -20f));
		pose.mulPose(Axis.XP.rotationDegrees(g * -80f));
		pose.mulPose(Axis.YP.rotationDegrees(sd * -45f));
		// the sword moves
		moveOffsets(pose, s.animId, age, sd);
		held(pose, player, right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !right);
		drawHeld(pose, buffers, light, player, s, age, now, viewDown(), true);
		pose.popPose();
		return true;
	}

	/**
	 * First person, hold V: the sword hand comes across to grab the off arm (raised in the other corner) and wrenches at it,
	 * both shaking with the strain.
	 */
	private static void struggleFirstPerson(ItemInHandRenderer renderer, AbstractClientPlayer player, float age, PoseStack pose, MultiBufferSource buffers,
			int light) {
		HumanoidArm mainSide = player.getMainArm();
		float sd = mainSide == HumanoidArm.RIGHT ? 1f : -1f;
		float r = ease(age / 6f), pull = strugglePull(age), shake = struggleShake(age);
		// the off arm: held out into view in its corner, dragged in toward the middle on each wrench
		// (the moves across the view are made before fpArm, in the view's own axes; the turns at the shoulder inside it)
		pose.pushPose();
		pose.translate(sd * (STRUGGLE_OFF_SHIFT * r + 0.04f * pull) + shake * 0.04f, STRUGGLE_OFF_LIFT * r + 0.02f * pull, 0f);
		fpArm(player, pose, buffers, light, mainSide.getOpposite(), p -> {
			p.mulPose(Axis.YP.rotationDegrees(-sd * (STRUGGLE_OFF_Y * r + 9f * pull)));
			p.mulPose(Axis.XP.rotationDegrees(STRUGGLE_OFF_X * r + shake * 20f));
		});
		pose.popPose();
		// the sword hand: swung across from its corner onto the off arm, wrenching it
		pose.pushPose();
		pose.translate(-sd * (STRUGGLE_MAIN_SHIFT * r - 0.05f * pull) + shake * 0.05f, (STRUGGLE_MAIN_LIFT - 0.03f * pull) * r, 0f);
		fpArm(player, pose, buffers, light, mainSide, p -> {
			p.mulPose(Axis.YP.rotationDegrees(sd * (STRUGGLE_MAIN_Y * r - 8f * pull)));
			p.mulPose(Axis.XP.rotationDegrees(STRUGGLE_MAIN_X * r + shake * 25f));
			p.mulPose(Axis.ZP.rotationDegrees(sd * STRUGGLE_MAIN_Z * r));
		});
		pose.popPose();
	}

	/**
	 * The first-person struggle (picked from filmed sets): the off arm moved in and up a little and turned in toward the middle;
	 * the sword hand moved across onto it, turned to grip it.
	 */
	private static final float STRUGGLE_OFF_SHIFT = 0.12f, STRUGGLE_OFF_LIFT = 0.08f, STRUGGLE_OFF_Y = 25f, STRUGGLE_OFF_X = -12f;
	private static final float STRUGGLE_MAIN_SHIFT = 0.45f, STRUGGLE_MAIN_LIFT = 0.0f, STRUGGLE_MAIN_Y = 45f, STRUGGLE_MAIN_X = 10f, STRUGGLE_MAIN_Z = -20f;


	/**
	 * Vanilla's {@code renderPlayerArm} (an empty hand in first person) with {@code custom} turned in at its shoulder (after its
	 * first placement, before it lays the arm out), so an arm can reach about from where it sits.
	 */
	private static void fpArm(AbstractClientPlayer player, PoseStack pose, MultiBufferSource buffers, int light, HumanoidArm side,
			java.util.function.Consumer<PoseStack> custom) {
		Object r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
		if (!(r instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr)) {
			return;
		}
		float f = side == HumanoidArm.RIGHT ? 1f : -1f;
		pose.pushPose();
		pose.translate(f * 0.64000005f, -0.6f, -0.71999997f);
		pose.mulPose(Axis.YP.rotationDegrees(f * 45f));
		custom.accept(pose);
		pose.translate(f * -1.0f, 3.6f, 3.5f);
		pose.mulPose(Axis.ZP.rotationDegrees(f * 120f));
		pose.mulPose(Axis.XP.rotationDegrees(200f));
		pose.mulPose(Axis.YP.rotationDegrees(f * -135f));
		pose.translate(f * 5.6f, 0f, 0f);
		if (side == HumanoidArm.RIGHT) {
			pr.renderRightHand(pose, buffers, light, player);
		} else {
			pr.renderLeftHand(pose, buffers, light, player);
		}
		pose.popPose();
	}

	/** First person: each sword move's swing on top of the held pose (blended in and out over its length). */
	private static void moveOffsets(PoseStack pose, int anim, float age, float sd) {
		if (age < 0f) {
			return;
		}
		switch (anim) {
			case AndyState.ANIM_SWORD_SLASH1, AndyState.ANIM_SWORD_SLASH2, AndyState.ANIM_SWORD_COUNTER -> {
				if (age < 7f) {
					float t = ease(age / 4f);
					float w = fade(age, 7f);
					float dir = anim == AndyState.ANIM_SWORD_SLASH2 ? -1f : 1f;
					pose.translate(-sd * dir * Mth.lerp(t, -0.15f, 0.3f) * w, 0.08f * w, -0.12f * w);
					pose.mulPose(Axis.YP.rotationDegrees(sd * dir * Mth.lerp(t, -40f, 55f) * w));
					pose.mulPose(Axis.ZP.rotationDegrees(sd * dir * Mth.lerp(t, -30f, 40f) * w));
				}
			}
			case AndyState.ANIM_SWORD_SLASH3, AndyState.ANIM_SWORD_EDGE -> {
				if (age < 10f) {
					float t = ease(age / 6f);
					float w = fade(age, 10f);
					if (anim == AndyState.ANIM_SWORD_EDGE) {
						pose.translate(0f, 0.1f * w, -0.1f * w);
						pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(t, 50f, -60f) * w)); // over the head and down
					} else {
						pose.translate(-sd * Mth.lerp(t, -0.2f, 0.45f) * w, 0.05f * w, -0.1f * w);
						pose.mulPose(Axis.YP.rotationDegrees(sd * Mth.lerp(t, -70f, 80f) * w)); // a wide flat sweep
						pose.mulPose(Axis.ZP.rotationDegrees(sd * 60f * w));
					}
				}
			}
			case AndyState.ANIM_SWORD_THRUST, AndyState.ANIM_SWORD_DASH -> {
				if (age < 10f) {
					float t = ease(age / 3f);
					float w = fade(age, 10f);
					pose.translate(-sd * 0.12f * w, 0.08f * w, -0.35f * t * w);
					pose.mulPose(Axis.XP.rotationDegrees(-25f * w));
				}
			}
			case AndyState.ANIM_SWORD_GUARD, AndyState.ANIM_SWORD_PARRY -> {
				float len = anim == AndyState.ANIM_SWORD_GUARD ? AndyBoneSword.GUARD_TICKS : AndyBoneSword.PARRY_TICKS + 4f;
				if (age < len) {
					float w = Math.min(ease(age / 3f), fade(age, len));
					// the blade held across in front
					pose.translate(-sd * 0.3f * w, 0.1f * w, 0f);
					pose.mulPose(Axis.ZP.rotationDegrees(sd * 45f * w));
				}
			}
			case AndyState.ANIM_SWORD_SPLINTER -> {
				if (age < 8f) {
					float w = fade(age, 8f);
					pose.mulPose(Axis.XP.rotationDegrees((-20f + Mth.sin(age * 3f) * 5f) * w));
					pose.translate(0f, 0f, -0.15f * w);
				}
			}
			default -> {
			}
		}
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	private static float fade(float age, float len) {
		return Mth.clamp((len - age) / 3f, 0f, 1f);
	}

	// ---------------------------------------------------------------- the torn arm in his grip

	private static PlayerModel<?> playerModel(Player player) {
		Object r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
		return r instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr ? pr.getModel() : null;
	}

	/**
	 * The torn-off arm in sword space, held by its hand end (where the sword's grip will be), the torn shoulder end up the
	 * blade: his own skin, a raw red end with the bone stub, blood seeping out of it as it is crushed ({@code crush} 0..1).
	 * {@code swell}: how fat the squeezing makes it.
	 */
	static void severedArm(PoseStack pose, MultiBufferSource buffers, int light, Player player, int armPart, float swell, float crush) {
		PlayerModel<?> model = playerModel(player);
		boolean slim = AndyRender.slim(player);
		float[] b = AndyTexels.box(armPart, slim);
		float handEnd = b[1] + b[4];
		if (model != null && (armPart == AndyState.RIGHT_ARM || armPart == AndyState.LEFT_ARM)) {
			ModelPart base = AndyRender.part(model, armPart), over = AndyRender.overlay(model, armPart);
			PartPose pb = base.storePose(), po = over.storePose();
			boolean vb = base.visible, vo = over.visible;
			base.resetPose();
			over.resetPose();
			base.x = base.y = base.z = 0f;
			over.x = over.y = over.z = 0f;
			base.visible = over.visible = true;
			pose.pushPose();
			// arm space (+y shoulder to hand) into sword space: the hand end in the fist at z +4, the shoulder end up at -8
			pose.translate(-(b[0] + b[3] * 0.5f) * swell / 16f, 0f, (4f - handEnd) / 16f);
			pose.mulPose(Axis.XP.rotationDegrees(90f));
			pose.scale(swell, 1f, swell);
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(AndyRender.skin(player)));
			base.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			over.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			base.loadPose(pb);
			over.loadPose(po);
			base.visible = vb;
			over.visible = vo;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		PoseStack.Pose p = pose.last();
		float top = 4f - b[4]; // the torn shoulder end (z)
		float hw = b[3] * 0.5f * swell + 0.05f, hd = b[5] * 0.5f * swell + 0.05f;
		// the raw end: torn muscle, ragged, and the bone stub sticking out of it
		box(vc, p, -hw, -hd, top - 0.25f, hw, hd, top + 0.3f, FLESH, light);
		box(vc, p, -hw * 0.7f, -hd * 0.7f, top - 0.6f, hw * 0.4f, hd * 0.5f, top - 0.2f, BLOOD, light);
		box(vc, p, -0.6f, -0.6f, top - 1.9f, 0.6f, 0.6f, top - 0.2f, BONE, light);
		box(vc, p, -0.75f, -0.75f, top - 2.3f, 0.75f, 0.75f, top - 1.8f, BONE_SHADE, light);
		// blood welling out of it as it is crushed: runs down its sides, more and longer as the grip tightens
		float[][] runs = { { 1, 0.2f, 0.3f }, { -1, -0.4f, 0.45f }, { 1, -0.9f, 0.6f }, { -1, 0.7f, 0.75f }, { 1, 1.1f, 0.85f } };
		for (float[] r : runs) {
			if (crush < r[2] - 0.3f) {
				continue;
			}
			float len = Mth.clamp((crush - r[2] + 0.3f) / 0.4f, 0f, 1f) * 7f;
			float x = r[0] * hw;
			box(vc, p, x - 0.12f * r[0] - 0.08f, r[1] - 0.25f, top, x + 0.04f, r[1] + 0.25f, top + len, BLOOD_WET, light);
		}
	}

	// ---------------------------------------------------------------- the drips

	/**
	 * Where drops form on the blade, in sword space (on its blood: the tip, the three runs off the bottom edge, the two drops
	 * under the guard) and how many ticks each takes to swell and fall.
	 */
	private static final float[][] DRIP_AT = { { 0f, 0.6f, TIP_Z + 0.78f, 0.4f }, { 0f, 1.88f, -4.2f, 1.6f }, { 0f, 1.63f, -9.8f, 1.1f },
			{ 0f, 1.375f, -15.4f, 1.4f }, { 0f, 5.5f, -2.05f, 1.2f }, { 0.67f, 1.7f, -1.8f, 1.2f } };
	private static final int[] DRIP_EVERY = { 47, 63, 55, 71, 52, 79 };
	private static final Map<UUID, int[]> DRIP_SEEN = new HashMap<>();

	/**
	 * First person: world-down in the hand renderer's pose space. (Its pose stack starts with the camera's rotation undone --
	 * GameRenderer.renderItemInHand -- so it is world-aligned round the camera, like the world's: down is down.)
	 */
	private static Vector3f viewDown() {
		return new Vector3f(0f, -1f, 0f);
	}

	/**
	 * The drops on the blade: each swells on its blood, hanging straight down whatever way the blade points, until it lets go and
	 * falls ({@link BloodFx#bladeDrip}) to splat on the ground; then the next one starts. The pose is at sword space;
	 * {@code down} is world-down in the frame the pose maps into (world-aligned in third person, the view in first person).
	 */
	private static void drips(PoseStack pose, MultiBufferSource buffers, int light, Player player, float now, Vector3f down, boolean firstPerson) {
		Matrix4f m = pose.last().pose();
		Matrix4f inv = new Matrix4f(m).invert();
		Vector3f d = inv.transformDirection(new Vector3f(down)).normalize(); // world-down in sword space
		Minecraft mc = Minecraft.getInstance();
		int[] seen = DRIP_SEEN.computeIfAbsent(player.getUUID(), k -> new int[DRIP_AT.length]);
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		for (int i = 0; i < DRIP_AT.length; i++) {
			float period = DRIP_EVERY[i];
			float tt = now + i * 17.3f;
			int cycle = (int) Math.floor(tt / period);
			float c = (tt - cycle * period) / period; // 0 .. 1 through this drop's swelling
			float[] a = DRIP_AT[i];
			float len = a[3] + 1.9f * c * c; // the run's own length, then the drop swelling at its end
			float w = 0.17f + 0.13f * c;
			bead(vc, pose.last(), a[0], a[1], a[2], d, len, w, c, light);
			if (cycle != seen[i]) {
				boolean first = seen[i] == 0;
				seen[i] = cycle;
				if (!first && !mc.isPaused()) {
					// it let go: the drop falls from the bead's end, in the world
					float reach = a[3] + 1.9f;
					Vector4f end = m.transform(new Vector4f((a[0] + d.x * reach) / 16f, (a[1] + d.y * reach) / 16f, (a[2] + d.z * reach) / 16f, 1f));
					Vec3 at = firstPerson ? viewToWorld(end) : cameraRelative(end);
					BloodFx.bladeDrip(at, player.getDeltaMovement(), 0.055f + 0.015f * (i % 3));
				}
			}
		}
	}

	/** A drop hanging from (x, y, z) along {@code d}: a neck that thins as it fills, a bead at its end ({@code c} 0..1 grown). */
	private static void bead(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, Vector3f d, float len, float w, float c, int light) {
		Vector3f a = new Vector3f(d).cross(Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0)).normalize();
		Vector3f b = new Vector3f(d).cross(a).normalize();
		float neck = c > 0.7f ? w * (1f - (c - 0.7f) / 0.3f * 0.6f) : w;
		orientedBox(vc, pose, x, y, z, d, a, b, -0.2f, Math.min(len * 0.45f, 0.9f), 0.3f, BLOOD, light); // where it runs off the blade
		orientedBox(vc, pose, x, y, z, d, a, b, 0f, len - w * 1.2f, neck * 0.7f, BLOOD_WET, light);
		float ball = w * (1f + 0.6f * c);
		orientedBox(vc, pose, x, y, z, d, a, b, len - ball * 1.4f, len, ball, c > 0.5f ? BLOOD_DARK : BLOOD_WET, light);
		if (c > 0.3f) {
			// a wet glint on the bead
			orientedBox(vc, pose, x, y, z, d, a, b, len - ball * 1.2f, len - ball * 0.7f, ball * 1.04f, BLOOD_GLOSS, light);
		}
	}

	/** A box along {@code d} from {@code t0} to {@code t1} (px from the anchor), {@code h} px either side across {@code a} and {@code b}. */
	private static void orientedBox(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z, Vector3f d, Vector3f a, Vector3f b, float t0, float t1,
			float h, int rgb, int light) {
		Matrix4f m = pose.pose();
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			float t = (i & 4) == 0 ? t0 : t1;
			float sa = (i & 1) == 0 ? -h : h, sb = (i & 2) == 0 ? -h : h;
			c[i] = new Vector3f(x + d.x * t + a.x * sa + b.x * sb, y + d.y * t + a.y * sa + b.y * sb, z + d.z * t + a.z * sa + b.z * sb).div(16f);
		}
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, bl = rgb & 0xFF;
		int[][] faces = { { 0, 1, 3, 2 }, { 4, 6, 7, 5 }, { 0, 4, 5, 1 }, { 2, 3, 7, 6 }, { 0, 2, 6, 4 }, { 1, 5, 7, 3 } };
		for (int[] f : faces) {
			for (int k = 0; k < 4; k++) {
				Vector3f v = c[f[k]];
				vc.addVertex(m, v.x, v.y, v.z).setColor(r, g, bl, 255).setUv(k == 1 || k == 2 ? WU1 : WU0, k >= 2 ? WV1 : WV0)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, d.x, d.y, d.z);
			}
		}
	}

	/** Third person: the pose maps to camera-relative world space. */
	private static Vec3 cameraRelative(Vector4f v) {
		Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		return new Vec3(v.x() + cam.x, v.y() + cam.y, v.z() + cam.z);
	}

	/**
	 * First person: the hand renderer's view space (drawn at a fixed 70 degree field of view) to the world, matched to where the
	 * world's own field of view puts the same spot on screen.
	 */
	private static Vec3 viewToWorld(Vector4f v) {
		Minecraft mc = Minecraft.getInstance();
		Camera cam = mc.gameRenderer.getMainCamera();
		double fov = mc.options.fov().get();
		if (mc.player != null) {
			fov *= mc.player.getFieldOfViewModifier();
		}
		double k = Math.tan(Math.toRadians(fov) * 0.5) / Math.tan(Math.toRadians(70.0) * 0.5);
		Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
		// the pose is world-aligned round the camera (see viewDown): into the camera's own axes, the sideways and up parts
		// scaled for the field of view, and back
		double l = (v.x() * left.x() + v.y() * left.y() + v.z() * left.z()) * k;
		double u = (v.x() * up.x() + v.y() * up.y() + v.z() * up.z()) * k;
		double f = v.x() * look.x() + v.y() * look.y() + v.z() * look.z();
		Vec3 p = cam.getPosition();
		return new Vec3(p.x + left.x() * l + up.x() * u + look.x() * f, p.y + left.y() * l + up.y() * u + look.y() * f,
				p.z + left.z() * l + up.z() * u + look.z() * f);
	}

	// ---------------------------------------------------------------- the mesh

	/** The whole sword in sword space ({@code light} for everything; {@code wet}: held, its drops drawn by {@link #drips}). */
	static void mesh(PoseStack pose, MultiBufferSource buffers, int light, float now, boolean wet) {
		// the blade: the arm's bone, drawn long (the vanilla skeleton's 2 x 12 x 2 arm bone, stretched to 19 px)
		pose.pushPose();
		pose.translate(0f, 0f, -5.8f / 16f);
		pose.mulPose(Axis.XP.rotationDegrees(-90f));
		pose.scale(0.8f, 1.6f, 1.0f);
		VanillaSkeleton.draw(pose.last(), buffers.getBuffer(RenderType.entityCutoutNoCull(VanillaSkeleton.TEXTURE)), VanillaSkeleton.RIGHT_ARM, -2f, 10f,
				light, OverlayTexture.NO_OVERLAY, -1);
		pose.popPose();

		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		PoseStack.Pose p = pose.last();
		// the jagged bone edges, top and bottom, tapering to the point (alternate segments stand proud: splintered)
		float z = -2.7f;
		for (int k = 0; k < 9; k++) {
			float len = 2.1f;
			float taper = 1f - k / 10f;
			float jag = (k % 2 == 0) ? 0.35f : 0f;
			float jag2 = (k % 3 == 1) ? 0.3f : 0f;
			int c = k % 2 == 0 ? BONE : BONE_SHADE;
			box(vc, p, -0.32f, -1.0f - (1.2f * taper + jag), z - len, 0.32f, -0.95f, z, c, light);
			box(vc, p, -0.32f, 0.95f, z - len, 0.32f, 1.0f + (0.95f * taper + jag2), z, k % 2 == 0 ? BONE_SHADE : BONE, light);
			// a notch of darker bone where the segments meet
			box(vc, p, -0.34f, -1.0f - 0.25f, z - 0.25f, 0.34f, -0.95f, z, BONE_DARK, light);
			z -= len;
		}
		// the point
		box(vc, p, -0.38f, -0.85f, -23.0f, 0.38f, 0.85f, -21.6f, BONE_SHADE, light);
		box(vc, p, -0.26f, -0.45f, TIP_Z, 0.26f, 0.45f, -22.9f, BONE, light);
		// blood: streaks along both flats (just proud of the bone), darker at their ends
		float[][] streaks = { { 1, -3.2f, -9.5f, -0.25f, 0.25f }, { -1, -4.5f, -12.5f, 0.05f, 0.6f }, { 1, -11.5f, -16.5f, -0.75f, -0.3f },
				{ -1, -14f, -20.5f, -0.4f, 0.05f }, { 1, -17.5f, -22.2f, 0.2f, 0.55f } };
		for (float[] st : streaks) {
			float x = st[0] * 0.82f;
			float x0 = st[0] > 0 ? x - 0.05f : x - 0.06f, x1 = st[0] > 0 ? x + 0.06f : x + 0.05f;
			box(vc, p, x0, st[3], st[2], x1, st[4], st[1], BLOOD, light);
			box(vc, p, x0 - 0.01f, st[3] - 0.08f, st[1] - 0.8f, x1 + 0.01f, st[4] + 0.08f, st[1], BLOOD_WET, light);
			box(vc, p, x0 - 0.01f, st[3] + 0.05f, st[2], x1 + 0.01f, st[4] - 0.05f, st[2] + 0.6f, BLOOD_DARK, light);
		}
		// runs of blood down the bottom edge, hanging off it
		float[][] runs = { { -4.2f, 1.6f }, { -9.8f, 1.1f }, { -15.4f, 1.4f } };
		for (float[] r : runs) {
			float edge = 1.0f + 0.95f * (1f - (-r[0] - 2.7f) / 2.1f / 10f);
			box(vc, p, -0.36f, edge - 0.2f, r[0] - 0.7f, 0.36f, edge + 0.15f, r[0] + 0.5f, BLOOD, light);
			if (!wet) {
				// (held, the run hangs toward the ground and drips: drips())
				box(vc, p, -0.2f, edge, r[0] - 0.2f, 0.2f, edge + r[1], r[0] + 0.2f, BLOOD_WET, light);
				box(vc, p, -0.28f, edge + r[1] - 0.1f, r[0] - 0.28f, 0.28f, edge + r[1] + 0.4f, r[0] + 0.28f, BLOOD_DARK, light);
			}
		}
		// the tip: blood over the point and a drop that swells and falls
		box(vc, p, -0.4f, -0.5f, -23.2f, 0.4f, 0.9f, -22.2f, BLOOD, light);
		if (!wet) {
			// (held, the drops swell and fall: drips(); thrown, one hangs off the point)
			box(vc, p, -0.16f, 0.6f, TIP_Z + 0.6f, 0.16f, 1.7f, TIP_Z + 0.95f, BLOOD_WET, light);
			box(vc, p, -0.24f, 1.6f, TIP_Z + 0.5f, 0.24f, 2.08f, TIP_Z + 1.05f, BLOOD_DARK, light);
		}

		// the crossguard: congealed blood, blobby, glossy, a few drops hanging under it
		box(vc, p, -1.15f, -4.0f, -2.75f, 1.15f, 4.0f, -1.35f, BLOOD, light);
		box(vc, p, -1.6f, -1.7f, -3.05f, 1.6f, 1.7f, -1.05f, BLOOD_DARK, light); // the middle swells round the blade
		box(vc, p, -1.45f, -5.1f, -2.95f, 1.45f, -3.3f, -1.15f, BLOOD, light); // the two ends: lumps
		box(vc, p, -1.45f, 3.3f, -2.95f, 1.45f, 5.1f, -1.15f, BLOOD, light);
		box(vc, p, -1.2f, -5.6f, -2.7f, 1.2f, -4.9f, -1.4f, BLOOD_WET, light);
		box(vc, p, -1.2f, 4.9f, -2.7f, 1.2f, 5.5f, -1.4f, BLOOD_WET, light);
		box(vc, p, -1.0f, -2.4f, -3.2f, 1.0f, 2.4f, -2.9f, BLOOD_WET, light); // a glossy lip on the blade side
		// the gloss: bright wet highlights on the top and the faces
		box(vc, p, -0.5f, -5.68f, -2.4f, 0.3f, -5.55f, -1.7f, BLOOD_GLOSS, light);
		box(vc, p, 1.46f, -4.6f, -2.5f, 1.52f, -3.6f, -2.0f, BLOOD_GLOSS, light);
		box(vc, p, -1.52f, 3.7f, -2.6f, -1.46f, 4.5f, -2.1f, BLOOD_GLOSS, light);
		box(vc, p, 1.61f, -0.9f, -2.7f, 1.67f, 0.4f, -2.3f, BLOOD_GLOSS, light);
		box(vc, p, -1.67f, -0.2f, -2.5f, -1.61f, 0.9f, -2.1f, BLOOD_GLOSS, light);
		if (!wet) {
			// drops hanging from the guard (held, they hang toward the ground and fall: drips())
			box(vc, p, -0.22f, 5.5f, -2.25f, 0.22f, 6.7f, -1.85f, BLOOD_WET, light);
			box(vc, p, -0.3f, 6.5f, -2.32f, 0.3f, 7.05f, -1.78f, BLOOD_DARK, light);
			box(vc, p, 0.5f, 1.7f, -2.0f, 0.85f, 2.9f, -1.6f, BLOOD_WET, light);
			box(vc, p, 0.45f, 2.75f, -2.05f, 0.9f, 3.2f, -1.55f, BLOOD_DARK, light);
		}

		// the grip: the arm's skin wrapped round, torn, raw flesh between
		box(vc, p, -0.85f, -0.85f, -1.4f, 0.85f, 0.85f, 3.0f, SKIN, light);
		box(vc, p, -0.95f, -0.95f, -0.3f, 0.95f, 0.95f, 0.35f, FLESH, light);
		box(vc, p, -0.95f, -0.95f, 1.5f, 0.95f, 0.95f, 2.0f, FLESH, light);
		box(vc, p, -0.9f, 0.6f, 0.35f, 0.9f, 0.92f, 1.5f, SKIN_DARK, light); // a fold of skin
		box(vc, p, 0.86f, -0.3f, 2.0f, 0.96f, 0.5f, 2.9f, BLOOD, light);
		// the pommel: the knuckle end of the bone, flesh still clinging
		box(vc, p, -1.05f, -1.05f, 3.0f, 1.05f, 1.05f, 4.1f, BONE_SHADE, light);
		box(vc, p, -0.75f, -0.75f, 4.1f, 0.75f, 0.75f, 4.5f, BONE, light);
		box(vc, p, -1.12f, 0.4f, 2.9f, 1.12f, 1.12f, 3.5f, FLESH, light);
	}

	/**
	 * Rib Cage Guard: curved ribs of bone springing up round his chest (the pose at the body: model px, +y down, front -z),
	 * snapping out over the first ticks and crumbling back at the end.
	 */
	static void ribs(PoseStack pose, MultiBufferSource buffers, int light, float age) {
		float out = Mth.clamp(age / 4f, 0f, 1f);
		float in = Mth.clamp((AndyBoneSword.GUARD_TICKS - age) / 5f, 0f, 1f);
		float k = Math.min(out * out * (3f - 2f * out), in);
		if (k <= 0.01f) {
			return;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		PoseStack.Pose p = pose.last();
		for (int r = 0; r < 6; r++) {
			float y = -1f + r * 2.3f;
			float rad = (8.5f - Math.abs(r - 2.5f) * 0.6f) * (0.5f + 0.5f * k);
			int segs = 9;
			for (int i = 0; i < segs; i++) {
				float a0 = Mth.lerp(i / (float) segs, -1.25f, 1.25f) * k;
				float a1 = Mth.lerp((i + 1) / (float) segs, -1.25f, 1.25f) * k;
				float x0 = Mth.sin(a0) * rad, z0 = -Mth.cos(a0) * rad;
				float x1 = Mth.sin(a1) * rad, z1 = -Mth.cos(a1) * rad;
				float droop = (i - segs * 0.5f) * (i - segs * 0.5f) * 0.03f;
				box(vc, p, Math.min(x0, x1) - 0.35f, y + droop - 0.45f, Math.min(z0, z1) - 0.35f, Math.max(x0, x1) + 0.35f, y + droop + 0.45f,
						Math.max(z0, z1) + 0.35f, (i + r) % 3 == 0 ? BONE_SHADE : BONE, light);
			}
			// blood where the rib broke out of him
			box(vc, p, -0.7f, y - 0.6f, -2.6f, 0.7f, y + 0.6f, -2.0f, BLOOD, light);
		}
		// the sternum down the middle, in front
		float zs = -8.6f * (0.5f + 0.5f * k);
		box(vc, p, -0.7f, -1.4f, zs - 0.5f, 0.7f, 11.5f, zs + 0.5f, BONE_SHADE, light);
	}

	/** A shard of the Splinter Volley, pointing along +z (the pose at the shard's middle). */
	static void splinter(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(AndyRender.PALETTE));
		PoseStack.Pose p = pose.last();
		box(vc, p, -0.45f, -0.45f, -1.6f, 0.45f, 0.45f, 0.8f, BONE, light);
		box(vc, p, -0.28f, -0.28f, 0.8f, 0.28f, 0.28f, 1.9f, BONE_SHADE, light);
		box(vc, p, -0.5f, -0.5f, -2.1f, 0.5f, 0.5f, -1.5f, BLOOD, light);
	}

	// ---------------------------------------------------------------- boxes

	private static final int[][] FACES = { { 0, 4, 6, 2 }, { 1, 3, 7, 5 }, { 0, 1, 5, 4 }, { 2, 6, 7, 3 }, { 0, 2, 3, 1 }, { 4, 5, 7, 6 } };
	private static final float[][] NORMALS = { { -1, 0, 0 }, { 1, 0, 0 }, { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 } };

	/** A box in model px tinted {@code rgb} (shaded a little per face so the blobs read in flat light). */
	static void box(VertexConsumer vc, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, int light) {
		Matrix4f m = pose.pose();
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f(((i & 1) == 0 ? x0 : x1) / 16f, ((i & 2) == 0 ? y0 : y1) / 16f, ((i & 4) == 0 ? z0 : z1) / 16f);
		}
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		for (int f = 0; f < 6; f++) {
			int[] fi = FACES[f];
			float[] n = NORMALS[f];
			float shade = f == 2 ? 1.08f : f == 3 ? 0.8f : 1f;
			int rr = Math.min(255, (int) (r * shade)), gg = Math.min(255, (int) (g * shade)), bb = Math.min(255, (int) (b * shade));
			for (int k = 0; k < 4; k++) {
				Vector3f v = c[fi[k]];
				vc.addVertex(m, v.x, v.y, v.z).setColor(rr, gg, bb, 255).setUv(k == 1 || k == 2 ? WU1 : WU0, k >= 2 ? WV1 : WV0)
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
			}
		}
	}
}
