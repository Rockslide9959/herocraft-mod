package com.projecthero.mod.client.undead;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadBoneSword;
import com.projecthero.mod.undead.UndeadConfig;
import com.projecthero.mod.undead.UndeadKitConfig;
import com.projecthero.mod.undead.UndeadShoulderSword;
import com.projecthero.mod.undead.data.UndeadState;
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
 * v0.17.3: Kurikara, the bone katana ({@link UndeadBoneSword}) -- his own torn-off arm's bone, drawn out into a long, slender,
 * slightly curved single-edged blade: a pale spine, a white cutting edge with a wavy line of blood (the hamon) along it, a
 * blood groove down the flat, a collar of blood at its root, an oval bone guard (tsuba), a long grip wrapped in dark red over
 * white ray-skin bone, and a bone cap at its end. Drops of blood swell on the edge and tip and fall.
 *
 * <p>Sword space: model px / 16 units, +y down (the cutting edge's side), the grip round the origin and the blade running toward
 * -z, bowing toward -y (the spine) as it goes. It is held exactly as a vanilla sword is ({@link #held}: the iron sword's own
 * display transforms, first and third person), drawn in his hand ({@link #thirdPerson}), in first person ({@link #firstPerson})
 * and as a thrown sword ({@link #mesh}, from {@link UndeadPartRenderer}). Also the hold-V draw -- reaching over, struggling,
 * the arm torn off and gripped until it bursts round the blade --, each sword skill's pose, the v0.17.3 kit's poses (Ten Twists,
 * the Unluck Meteor, the Rib Cage Guard) and the sword stuck in his shoulder ({@link #shoulderSword}).
 */
public final class UndeadBoneSwordRender {
	// colours
	static final int BONE = 0xE6DFC8, BONE_SHADE = 0xCFC4A6, BONE_DARK = 0xA89A7C;
	static final int BLOOD = 0x6E0A12, BLOOD_DARK = 0x45040A, BLOOD_GLOSS = 0xC4222E, BLOOD_WET = 0x9A1018;
	static final int SKIN = 0xC48C6A, SKIN_DARK = 0x9E6A4E, FLESH = 0x8C1C22;

	/** The palette's plain white texel: everything here is tinted from it. */
	private static final float WU0 = 5.3f / 16f, WV0 = 0.3f / 16f, WU1 = 5.7f / 16f, WV1 = 0.7f / 16f;

	/** The blade's tip in sword space (px). */
	static final float TIP_Z = -29.4f;

	/** The vanilla sword whose display transforms the Bone Sword is held by. */
	private static final ItemStack REFERENCE = new ItemStack(Items.IRON_SWORD);
	/**
	 * Item space (the 16 px item cube, 0..1) to sword space: the grip where a sword sprite's hand is, the blade up the sprite's
	 * diagonal (toward +x +y), its flat facing the item's front like the sprite's, its bottom edge (+y down) to the lower right.
	 */
	static final float GRIP_X = 0.24f, GRIP_Y = 0.24f, HELD_SCALE = 0.72f;
	private static final Quaternionf ITEM_TO_SWORD;

	static {
		float k = (float) (1.0 / Math.sqrt(2.0));
		// columns: sword x -> item +z, sword y -> (-1,1,0)/sqrt2 (its bottom edge, the blood runs and guard drops, hangs down when
		// held in third person), sword z -> (-1,-1,0)/sqrt2
		ITEM_TO_SWORD = new Quaternionf().setFromNormalized(new Matrix3f(0f, 0f, 1f, -k, k, 0f, -k, -k, 0f));
	}

	private UndeadBoneSwordRender() {
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
	 * From {@link UndeadPose#apply}: the sword arm while the Bone Sword is out (vanilla's held-item arm, the attack swing kept), the
	 * hold-V struggle, the rip and the grip, Reform, the put-away, and each sword move. True if it set the pose.
	 */
	static boolean pose(Player player, UndeadState s, net.minecraft.client.model.HumanoidModel<?> model, float now) {
		float age = now - s.animStart;
		int handPart = s.boneSword() ? UndeadBoneSword.hand(s) : Undead.mainArm(player);
		ModelPart arm = handPart == UndeadState.RIGHT_ARM ? model.rightArm : model.leftArm;
		ModelPart other = handPart == UndeadState.RIGHT_ARM ? model.leftArm : model.rightArm;
		float sd = handPart == UndeadState.RIGHT_ARM ? 1f : -1f;
		float aim = model.head.xRot - Mth.HALF_PI;
		if (!s.boneSword()) {
			if (s.animId == UndeadState.ANIM_SWORD_STRUGGLE && age >= 0f && age < UndeadConfig.BONE_SWORD_HOLD + 4f) {
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
			if (s.animId == UndeadState.ANIM_REFORM && age >= 0f && age < UndeadConfig.REFORM_ANIM) {
				reformPose(model, age);
				return true;
			}
			if (s.animId == UndeadState.ANIM_SWORD_IMPALE && age >= 0f && age < 10f) {
				float t = ease(age / 4f);
				set(arm, fade(age, 10f), Mth.lerp(t, -2.9f, aim + 0.3f), model.head.yRot, 0f); // over the shoulder and thrown
				model.body.yRot -= 0.3f * sd * fade(age, 10f) * t;
				return true;
			}
			if (s.animId == UndeadState.ANIM_SWORD_AWAY && age >= 0f && age < 8f) {
				set(arm, fade(age, 8f), -1.2f, 0.2f * sd, -0.1f * sd); // the hand opening as it bursts
				return true;
			}
			float draw = shoulderDraw(player, now - (float) Math.floor(now));
			if (draw >= 0f) {
				shoulderDrawPose(model, arm, sd, aim, draw);
				return true;
			}
			return kitPose(player, s, model, now, age, arm, other, sd, aim);
		}
		if (s.animId == UndeadState.ANIM_SWORD_RIP && age >= 0f && age < UndeadConfig.BONE_SWORD_REVEAL + 6f) {
			// torn off: the arm swung round in front of him and gripped hard, shaking, until it bursts; then it settles into the
			// held pose
			float e = ease(age / 4f);
			float w = age < UndeadConfig.BONE_SWORD_REVEAL ? 1f : fade(age - UndeadConfig.BONE_SWORD_REVEAL + 3f, 9f);
			float k = Mth.clamp(age / UndeadConfig.BONE_SWORD_REVEAL, 0f, 1f);
			float tremble = (Mth.sin(age * 3.7f) * 0.05f + Mth.sin(age * 6.1f) * 0.03f) * k;
			float jolt = age >= UndeadConfig.BONE_SWORD_REVEAL ? 0.35f * fade(age - UndeadConfig.BONE_SWORD_REVEAL + 3f, 6f) : 0f;
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
			case UndeadKitConfig.ANIM_K_CRESCENT -> {
				if (age < 10f) {
					// the draw-cut: from across the body out wide in one flat sweep, the body turning into it
					float t = ease(age / 3f);
					float w = fade(age, 10f);
					set(arm, w, -1.45f, Mth.lerp(t, 0.95f, -1.25f) * sd, Mth.lerp(t, 0.25f, -0.15f) * sd);
					model.body.yRot += Mth.lerp(t, 0.35f, -0.4f) * sd * w;
				}
			}
			case UndeadKitConfig.ANIM_K_GREAT -> {
				float wind = UndeadKitConfig.K_GREAT_WINDUP;
				if (age < wind + 10f) {
					// raised high overhead (a tremble as the blood gathers), then brought down hard to the front
					float x = age < wind ? Mth.lerp(ease(age / 4f), -0.3f, -3.05f) + Mth.sin(age * 3f) * 0.03f
							: Mth.lerp(ease((age - wind) / 2.5f), -3.05f, -0.55f);
					float w = age < wind ? 1f : fade(age - wind, 10f);
					set(arm, w, x, -0.15f * sd, 0.05f * sd);
					model.body.xRot += (age < wind ? -0.08f : 0.35f * ease((age - wind) / 2.5f)) * w;
				}
			}
			case UndeadKitConfig.ANIM_K_FLASH -> {
				float len = UndeadKitConfig.K_FLASH_TICKS + UndeadKitConfig.K_FLASH_DELAY + 4f;
				if (age < len) {
					// iaido: low at the hip, then the draw out across and forward, held out wide behind him until the cuts open
					float t = ease(age / 2f);
					float w = Math.min(1f, (len - age) / 4f);
					set(arm, w, Mth.lerp(t, -0.35f, -1.35f), Mth.lerp(t, 0.85f, -1.45f) * sd, Mth.lerp(t, 0.1f, -0.35f) * sd);
					model.body.yRot += Mth.lerp(t, 0.4f, -0.45f) * sd * w;
					model.body.xRot += 0.2f * w;
				}
			}
			case UndeadKitConfig.ANIM_K_WHIRL -> {
				float len = UndeadKitConfig.K_WHIRL_TICKS + 3f;
				if (age < len) {
					// the blade held out flat, swept round and round with the body swinging into each turn
					float turn = Mth.clamp(age / (UndeadKitConfig.K_WHIRL_TICKS - 1f), 0f, 1f);
					float ph = (1f - (1f - turn) * (1f - turn)) * Mth.TWO_PI * 2f;
					float w = fade(age, len);
					set(arm, w, -1.5f, Mth.sin(ph) * 1.3f * sd - 0.2f * sd, -0.2f * sd);
					model.body.yRot += Mth.sin(ph) * 0.55f * sd * w;
				}
			}
			default -> {
			}
		}
		return true;
	}

	/**
	 * The v0.17.3 kit's poses with the katana not out: Ten Twists (the arm thrust out and wrenched round by the other hand, then
	 * the follow-through as it flies), the Unluck Meteor (both arms raised to the sky, trembling as it builds; then one thrown
	 * down at the target), the Rib Cage Guard (both hands hauling the ribs open), the shoulder sword going in and out. True if
	 * one set the pose.
	 */
	private static boolean kitPose(Player player, UndeadState s, net.minecraft.client.model.HumanoidModel<?> model, float now, float age, ModelPart main,
			ModelPart off, float sd, float aim) {
		float partial = now - (float) Math.floor(now);
		if (s.animId == UndeadKitConfig.ANIM_VORTEX_TWIST && age >= 0f && age < UndeadKitConfig.VORTEX_WINDUP + 1f) {
			boolean useOff = s.flag(UndeadState.FLAG_OFF_ARM);
			ModelPart arm = useOff ? off : main, other = useOff ? main : off;
			float as = useOff ? -sd : sd;
			float k = Mth.clamp(age / UndeadKitConfig.VORTEX_WINDUP, 0f, 1f);
			// each twist a wrench: the arm ratchets round its own length (zRot, with the arm pointing ahead), a jolt each time
			float twists = k * UndeadKitConfig.VORTEX_TWISTS;
			float step = twists - (float) Math.floor(twists);
			float wrench = ((float) Math.floor(twists) + ease(step * 1.6f)) * 0.62f;
			float jolt = Mth.sin(step * Mth.PI) * 0.06f;
			set(arm, ease(age / 2f), aim + jolt, model.head.yRot, -as * wrench);
			// the other hand grips the wrist and cranks it
			set(other, ease(age / 2f), aim + 0.25f, model.head.yRot + 0.55f * as, as * (0.4f + Mth.sin(twists * Mth.TWO_PI) * 0.25f));
			model.body.yRot += 0.18f * as * k;
			return true;
		}
		if (s.animId == UndeadKitConfig.ANIM_VORTEX_FIRE && age >= 0f && age < 8f) {
			// the follow-through: the shoulder thrown forward after the arm, the other arm swinging back
			float w = Mth.sin(Math.min(1f, age / 8f) * Mth.PI);
			boolean useOff = s.flag(UndeadState.FLAG_OFF_ARM);
			ModelPart other = useOff ? main : off;
			float as = useOff ? -sd : sd;
			model.body.yRot -= 0.3f * as * w;
			set(other, w, 0.7f, 0f, -0.2f * as);
			return true;
		}
		float k = UndeadKitFx.charge(player, partial);
		if (k >= 0f) {
			// calling the meteor: both arms raised high to the sky, spread, trembling harder as it builds; the head thrown back
			float e = ease(k * 6f);
			float shake = Mth.sin(now * 2.7f) * 0.06f * k;
			set(main, e, -2.75f + shake, 0.15f * sd, 0.45f * sd);
			set(off, e, -2.75f - shake, -0.15f * sd, -0.45f * sd);
			model.head.xRot = Mth.lerp(e * 0.6f, model.head.xRot, -0.7f) + shake * 0.5f;
			model.body.xRot -= 0.08f * e;
			return true;
		}
		if (s.animId == UndeadKitConfig.ANIM_METEOR_CALL && age >= 0f && age < 16f) {
			// thrown down at the target
			float t = ease(age / 3f);
			float w = fade(age, 16f);
			set(main, w, Mth.lerp(t, -2.9f, aim + 0.1f), model.head.yRot, 0f);
			set(off, w, Mth.lerp(t, -2.75f, 0.4f), -0.15f * sd, -0.3f * sd);
			model.body.xRot += 0.2f * t * w;
			return true;
		}
		float ribs = UndeadKitFx.ribs(player, partial);
		if (ribs > 0.01f) {
			// both hands at his chest, hauling the ribs apart
			set(model.rightArm, ribs, -1.15f, -0.55f, 0.35f);
			set(model.leftArm, ribs, -1.15f, 0.55f, -0.35f);
			model.body.xRot -= 0.1f * ribs;
			return true;
		}
		if ((s.animId == UndeadKitConfig.ANIM_SHOULDER_IN || s.animId == UndeadKitConfig.ANIM_SHOULDER_OUT) && age >= 0f && age < 10f) {
			// the hand across to the other shoulder: driving it down in, or wrenching it up out
			boolean in = s.animId == UndeadKitConfig.ANIM_SHOULDER_IN;
			float t = ease(age / 4f);
			float w = fade(age, 10f);
			float x = in ? Mth.lerp(t, -3.0f, -2.3f) : Mth.lerp(t, -2.3f, -3.0f);
			set(main, w, x, -0.75f * sd, -0.2f * sd);
			model.head.yRot -= 0.35f * sd * w;
			return true;
		}
		return false;
	}

	/**
	 * The quick draw's arm: across to the off shoulder and gripping the hilt, wrenched up and out over the head, cut down and across
	 * in front, then snapped back to the shoulder to drive it home. (Main arm only; the body twists a little with the cut.)
	 */
	private static void shoulderDrawPose(net.minecraft.client.model.HumanoidModel<?> model, ModelPart arm, float sd, float aim, float k) {
		float x, y, z;
		if (k < DRAW_OUT) { // the reach: to the hilt on the other shoulder
			float t = ease(k / DRAW_OUT);
			x = Mth.lerp(t, arm.xRot, -2.4f);
			y = Mth.lerp(t, arm.yRot, -0.8f * sd);
			z = Mth.lerp(t, arm.zRot, -0.15f * sd);
		} else if (k < DRAW_IN) { // out and up, then the cut down across the front
			float t = (k - DRAW_OUT) / (DRAW_IN - DRAW_OUT);
			float up = ease(Math.min(1f, t / 0.3f)), cut = ease(Math.max(0f, (t - 0.3f) / 0.7f));
			x = Mth.lerp(cut, Mth.lerp(up, -2.4f, -2.9f), aim + 0.9f);
			y = Mth.lerp(cut, Mth.lerp(up, -0.8f * sd, 0.15f * sd), -0.45f * sd);
			z = Mth.lerp(cut, 0.1f * sd, -0.1f * sd);
			model.body.yRot += sd * (0.25f * up - 0.45f * cut);
		} else { // back across to the shoulder to drive it in
			float t = ease((k - DRAW_IN) / (1f - DRAW_IN));
			x = Mth.lerp(t, aim + 0.9f, -2.3f);
			y = Mth.lerp(t, -0.45f * sd, -0.75f * sd);
			z = Mth.lerp(t, -0.1f * sd, -0.2f * sd);
		}
		arm.xRot = x;
		arm.yRot = y;
		arm.zRot = z;
	}

	/** Tap V, Reform: he jolts as the body slams back together -- arms flung out, chest and head thrown back -- and settles. */
	private static void reformPose(net.minecraft.client.model.HumanoidModel<?> model, float age) {
		float open = ease(age / 1.5f) * (1f - ease((age - 2f) / (UndeadConfig.REFORM_ANIM - 2f)));
		set(model.rightArm, open, -0.25f, 0.1f, 1.05f);
		set(model.leftArm, open, -0.25f, -0.1f, -1.05f);
		model.head.xRot -= 0.4f * open;
		model.body.xRot -= 0.12f * open;
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

	/** In his hand (from {@link UndeadRender#renderLayer}, the pose at the model), plus the Rib Cage Guard. */
	static void thirdPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, UndeadState s, float now) {
		float age = now - s.animStart;
		shoulderSword(pose, buffers, light, player, model, s);
		if (!s.boneSword()) {
			return;
		}
		int hand = UndeadBoneSword.hand(s);
		if (s.broken(hand)) {
			return;
		}
		boolean left = hand == UndeadState.LEFT_ARM;
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

	// ---------------------------------------------------------------- the shoulder sword's quick draw (v0.17.3)

	/** Draw phases of one empty-hand swing (fractions of vanilla's attack swing): reach to the shoulder, out and slashing, back in. */
	static final float DRAW_OUT = 0.22f, DRAW_IN = 0.82f;

	/**
	 * v0.17.3 (user: "When a player stabs a sword inside them and then left clicks with an empty hand the player pulls the sword out
	 * and then hits and then puts it back in again, this must all be a really quick animation"): 0..1 through the current swing when
	 * that is a shoulder-sword hit (a sword in his shoulder, nothing in the swinging main hand, no Bone Sword out); -1 otherwise. It
	 * rides vanilla's attack swing (6 ticks), so the whole draw, cut and re-sheathe takes about a third of a second.
	 */
	static float shoulderDraw(Player player, float partial) {
		if (UndeadShoulderSword.stored(player).isEmpty() || !player.getMainHandItem().isEmpty() || !player.swinging
				|| player.swingingArm != InteractionHand.MAIN_HAND) {
			return -1f;
		}
		UndeadState s = Undead.peek(player);
		if (s == null || s.boneSword() || s.broken(UndeadState.BODY)) {
			return -1f;
		}
		return player.getAttackAnim(partial);
	}

	/** True while the sword is out of the shoulder in his hand (the middle of the draw). */
	static boolean swordDrawn(float k) {
		return k >= DRAW_OUT && k < DRAW_IN;
	}

	/** Third person: the drawn sword in the main hand, exactly where ItemInHandLayer puts a held item. */
	private static void drawnSwordThirdPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model) {
		boolean left = player.getMainArm() == HumanoidArm.LEFT;
		pose.pushPose();
		model.translateToHand(left ? HumanoidArm.LEFT : HumanoidArm.RIGHT, pose);
		pose.mulPose(Axis.XP.rotationDegrees(-90f));
		pose.mulPose(Axis.YP.rotationDegrees(180f));
		pose.translate((left ? -1f : 1f) / 16f, 0.125f, -0.625f);
		Minecraft.getInstance().getItemRenderer().renderStatic(player, UndeadShoulderSword.stored(player),
				left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, pose, buffers, player.level(), light,
				OverlayTexture.NO_OVERLAY, player.getId());
		pose.popPose();
	}

	/** Where the shoulder sword sits on the body (model px, the body's frame: +y down, front -z) and how it is turned (degrees). */
	static float SHOULDER_X = 4.4f, SHOULDER_Y = -1.6f, SHOULDER_Z = 0.3f, SHOULDER_ROT_Z = -45f, SHOULDER_ROT_Y = 0f, SHOULDER_TILT = 14f,
			SHOULDER_SINK = 0f, SHOULDER_SCALE = 0.85f;

	/**
	 * The sword stuck in his off shoulder ({@link UndeadShoulderSword}): driven in from above at a slant, point down into the
	 * shoulder, the hilt and the top of the blade standing up out of it, a little blood round where it went in. The real item
	 * (its own model and enchantment glint).
	 */
	static void shoulderSword(PoseStack pose, MultiBufferSource buffers, int light, Player player, PlayerModel<?> model, UndeadState s) {
		ItemStack sword = UndeadShoulderSword.stored(player);
		if (sword.isEmpty() || s.broken(UndeadState.BODY)) {
			return;
		}
		float draw = shoulderDraw(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (swordDrawn(draw)) {
			drawnSwordThirdPerson(pose, buffers, light, player, model); // out of the shoulder, in his hand for the cut
			return;
		}
		int off = Undead.otherArm(Undead.mainArm(player));
		float side = off == UndeadState.LEFT_ARM ? 1f : -1f; // the left arm sits at +x on the model
		pose.pushPose();
		model.body.translateAndRotate(pose);
		pose.translate(side * SHOULDER_X / 16f, SHOULDER_Y / 16f, SHOULDER_Z / 16f);
		// a little blood where it went in
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		float top = -SHOULDER_Y; // the top of the shoulder, where it went in
		box(vc, pose.last(), -1.3f, top - 0.25f, -1.2f, 1.3f, top + 0.25f, 1.2f, BLOOD, light);
		box(vc, pose.last(), side * 1.0f - 0.25f, top, -0.3f, side * 1.0f + 0.25f, top + 2.4f, 0.2f, BLOOD_WET, light);
		// the item, in his front plane: its point driven down and in through the top of the shoulder, the hilt leaning out and a little back
		pose.mulPose(Axis.YP.rotationDegrees(side * SHOULDER_ROT_Y));
		pose.mulPose(Axis.XP.rotationDegrees(SHOULDER_TILT));
		pose.mulPose(Axis.ZP.rotationDegrees(SHOULDER_ROT_Z + side * 22f)); // the point down and in, the hilt out past the head
		pose.translate(SHOULDER_SINK, SHOULDER_SINK, 0f); // pushed in along its own diagonal
		pose.scale(SHOULDER_SCALE, SHOULDER_SCALE, SHOULDER_SCALE);
		Minecraft.getInstance().getItemRenderer().renderStatic(sword, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
				player.level(), player.getId());
		pose.popPose();
	}

	/**
	 * The thing in the sword hand at sword space: while the torn arm is gripped (the first {@link UndeadConfig#BONE_SWORD_REVEAL}
	 * ticks of the rip) the arm itself, squeezed and shaking; then the sword, popping out of the burst; its drips.
	 * {@code down}: which way world-down is in the pose's own frame (before this pose), for the drops.
	 */
	private static void drawHeld(PoseStack pose, MultiBufferSource buffers, int light, Player player, UndeadState s, float age, float now, Vector3f down,
			boolean firstPerson) {
		if (s.animId == UndeadState.ANIM_SWORD_RIP && age >= 0f && age < UndeadConfig.BONE_SWORD_REVEAL) {
			float k = Mth.clamp(age / UndeadConfig.BONE_SWORD_REVEAL, 0f, 1f);
			pose.pushPose();
			// the grip tightening: it shakes harder and swells as it is crushed
			pose.mulPose(Axis.XP.rotationDegrees((Mth.sin(age * 4.3f) * 2.5f + Mth.sin(age * 7.7f) * 1.5f) * k));
			pose.mulPose(Axis.YP.rotationDegrees((Mth.sin(age * 5.1f + 1f) * 2f) * k));
			severedArm(pose, buffers, light, player, s.boneSwordArm(), 1f + 0.06f * Mth.sin(age * 2.6f) * k + 0.14f * k * k, k);
			pose.popPose();
			return;
		}
		if (s.animId == UndeadState.ANIM_SWORD_RIP && age >= UndeadConfig.BONE_SWORD_REVEAL && age < UndeadConfig.BONE_SWORD_REVEAL + 5f) {
			float t = (age - UndeadConfig.BONE_SWORD_REVEAL) / 5f;
			float pop = 1f + 0.3f * (1f - t) * (1f - t);
			pose.scale(pop, pop, pop);
		}
		mesh(pose, buffers, light, now, true);
		drips(pose, buffers, light, player, now, down, firstPerson);
	}

	/**
	 * First person, from {@link UndeadFirstPerson#arms}: the sword held exactly where vanilla holds a sword, swinging with the attack
	 * and with each sword move; the hold-V struggle (both hands); the gripped arm. True if it drew (the caller cancels vanilla's
	 * hand).
	 */
	static boolean firstPerson(ItemInHandRenderer renderer, AbstractClientPlayer player, UndeadState s, float partial, InteractionHand hand, PoseStack pose,
			MultiBufferSource buffers, int light) {
		float now = player.level().getGameTime() + partial;
		float age = now - s.animStart;
		if (!s.boneSword()) {
			if (s.animId == UndeadState.ANIM_SWORD_STRUGGLE && age >= 0f && age < UndeadConfig.BONE_SWORD_HOLD + 4f) {
				if (hand == InteractionHand.MAIN_HAND) {
					struggleFirstPerson(renderer, player, age, pose, buffers, light);
				}
				return true;
			}
			if (s.animId == UndeadKitConfig.ANIM_VORTEX_TWIST && age >= 0f && age < UndeadKitConfig.VORTEX_WINDUP + 1f) {
				if (hand == InteractionHand.MAIN_HAND) {
					twistFirstPerson(player, s, age, pose, buffers, light);
				}
				return true;
			}
			float draw = shoulderDraw(player, partial);
			if (hand == InteractionHand.OFF_HAND) {
				if (!swordDrawn(draw)) {
					shoulderFirstPerson(player, partial, pose, buffers, light);
				}
				return false;
			}
			if (swordDrawn(draw)) {
				drawnSwordFirstPerson(renderer, player, partial, draw, pose, buffers, light);
				return true;
			}
			return false;
		}
		int arm = UndeadBoneSword.hand(s);
		boolean mainHand = arm == Undead.mainArm(player);
		if ((hand == InteractionHand.MAIN_HAND) != mainHand) {
			return false; // the other hand (the torn-off arm's) is vanilla's / the missing-hand rule's
		}
		boolean right = arm == UndeadState.RIGHT_ARM;
		float sd = right ? 1f : -1f;
		float sw = player.swingingArm == hand ? player.getAttackAnim(partial) : 0f;
		float sq = Mth.sqrt(sw);
		pose.pushPose();
		// the torn arm swung in from where the hand grabbed it
		if (s.animId == UndeadState.ANIM_SWORD_RIP && age >= 0f && age < UndeadConfig.BONE_SWORD_REVEAL + 8f) {
			// torn off: swung in from where the hand grabbed it, then held up into view, gripped hard, until it bursts; then
			// down into the held place
			float in = 1f - ease(age / 4f);
			float up = age < UndeadConfig.BONE_SWORD_REVEAL ? ease(age / 5f) : 1f - ease((age - UndeadConfig.BONE_SWORD_REVEAL - 1f) / 7f);
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
	/**
	 * First person, Ten Twists: the twisted arm thrust out ahead into the middle of the view, ratcheting round with each wrench;
	 * the other hand on its wrist, cranking.
	 */
	private static void twistFirstPerson(AbstractClientPlayer player, UndeadState s, float age, PoseStack pose, MultiBufferSource buffers, int light) {
		HumanoidArm mainSide = player.getMainArm();
		boolean useOff = s.flag(UndeadState.FLAG_OFF_ARM);
		HumanoidArm armSide = useOff ? mainSide.getOpposite() : mainSide;
		float sd = armSide == HumanoidArm.RIGHT ? 1f : -1f;
		float r = ease(age / 2f);
		float k = Mth.clamp(age / UndeadKitConfig.VORTEX_WINDUP, 0f, 1f);
		float twists = k * UndeadKitConfig.VORTEX_TWISTS;
		float step = twists - (float) Math.floor(twists);
		float wrench = ((float) Math.floor(twists) + ease(step * 1.6f)) * 36f;
		float jolt = Mth.sin(step * Mth.PI);
		// (just the arm, thrust a little further out and shaking with each wrench; the red spiral round it is drawn in the world)
		pose.pushPose();
		pose.translate(-sd * 0.06f * r + Mth.sin(twists * Mth.TWO_PI) * 0.012f, 0.05f * r + 0.02f * jolt, -0.12f * r);
		fpArm(player, pose, buffers, light, armSide, p -> {
			p.mulPose(Axis.XP.rotationDegrees(-6f * r + jolt * 4f));
			p.mulPose(Axis.ZP.rotationDegrees(-sd * (wrench % 36f) * 0.25f));
		});
		pose.popPose();
	}

	/**
	 * First person, the sword in his shoulder: its hilt standing up out of the bottom corner of the view on the off side (where
	 * the shoulder is), swaying a little with him. Drawn from the off hand's turn (vanilla then draws whatever that hand holds).
	 */
	/** First person, the drawn sword: up from the off shoulder, a cut down across the view, the way vanilla swings a held sword. */
	private static void drawnSwordFirstPerson(ItemInHandRenderer renderer, AbstractClientPlayer player, float partial, float k, PoseStack pose,
			MultiBufferSource buffers, int light) {
		boolean right = player.getMainArm() == HumanoidArm.RIGHT;
		float sd = right ? 1f : -1f;
		float t = (k - DRAW_OUT) / (DRAW_IN - DRAW_OUT);
		float in = 1f - ease(Math.min(1f, t / 0.25f)); // just pulled from the other shoulder: it sweeps in from that side
		float sw = ease(Math.max(0f, (t - 0.2f) / 0.8f)); // then the cut
		float sq = Mth.sqrt(sw);
		pose.pushPose();
		pose.translate(-sd * 0.7f * in, 0.25f * in, 0f);
		pose.translate(sd * -0.4f * Mth.sin(sq * Mth.PI), 0.2f * Mth.sin(sq * Mth.TWO_PI), -0.2f * Mth.sin(sw * Mth.PI));
		pose.translate(sd * 0.56f, -0.52f, -0.72f);
		float f = Mth.sin(sw * sw * Mth.PI);
		pose.mulPose(Axis.YP.rotationDegrees(sd * (45f + f * -20f)));
		float g = Mth.sin(sq * Mth.PI);
		pose.mulPose(Axis.ZP.rotationDegrees(sd * g * -20f));
		pose.mulPose(Axis.XP.rotationDegrees(g * -80f));
		pose.mulPose(Axis.YP.rotationDegrees(sd * -45f));
		renderer.renderItem(player, UndeadShoulderSword.stored(player), right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
				!right, pose, buffers, light);
		pose.popPose();
	}

	private static void shoulderFirstPerson(AbstractClientPlayer player, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
		ItemStack sword = UndeadShoulderSword.stored(player);
		if (sword.isEmpty() || !player.getOffhandItem().isEmpty()) {
			return;
		}
		float sd = player.getMainArm() == HumanoidArm.RIGHT ? -1f : 1f; // the off side
		float bob = Mth.sin((player.walkDist + partial) * 2.2f) * 0.012f;
		pose.pushPose();
		pose.translate(sd * FP_SHOULDER_X, FP_SHOULDER_Y + bob, FP_SHOULDER_Z);
		pose.mulPose(Axis.ZP.rotationDegrees(sd * FP_SHOULDER_ROLL));
		pose.mulPose(Axis.XP.rotationDegrees(FP_SHOULDER_PITCH));
		pose.mulPose(Axis.ZP.rotationDegrees(-135f + (sd > 0 ? 0f : 0f)));
		pose.scale(0.62f, 0.62f, 0.62f);
		Minecraft.getInstance().getItemRenderer().renderStatic(sword, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
				player.level(), player.getId());
		pose.popPose();
	}

	/** The first-person shoulder hilt's place: off to the side, low, a little ahead. */
	static float FP_SHOULDER_X = 0.62f, FP_SHOULDER_Y = -0.62f, FP_SHOULDER_Z = -0.62f, FP_SHOULDER_ROLL = 18f, FP_SHOULDER_PITCH = -20f;

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

	/** First person: each sword skill's swing on top of the held pose (blended in and out over its length). */
	private static void moveOffsets(PoseStack pose, int anim, float age, float sd) {
		if (age < 0f) {
			return;
		}
		switch (anim) {
			case UndeadKitConfig.ANIM_K_CRESCENT -> {
				if (age < 9f) {
					// a flat draw-cut across the view
					float t = ease(age / 3f);
					float w = fade(age, 9f);
					pose.translate(-sd * Mth.lerp(t, -0.25f, 0.5f) * w, 0.06f * w, -0.15f * w);
					pose.mulPose(Axis.YP.rotationDegrees(sd * Mth.lerp(t, -65f, 75f) * w));
					pose.mulPose(Axis.ZP.rotationDegrees(sd * 70f * w));
				}
			}
			case UndeadKitConfig.ANIM_K_GREAT -> {
				float wind = UndeadKitConfig.K_GREAT_WINDUP;
				if (age < wind + 10f) {
					// raised up out of view, then down through the middle
					float up = age < wind ? ease(age / 4f) : 1f - ease((age - wind) / 2.5f);
					float down = age < wind ? 0f : ease((age - wind) / 2.5f) * fade(age - wind, 10f);
					pose.translate(-sd * 0.2f * (up + down), 0.35f * up - 0.1f * down, -0.15f * down);
					pose.mulPose(Axis.XP.rotationDegrees(55f * up - 70f * down));
				}
			}
			case UndeadKitConfig.ANIM_K_FLASH -> {
				float len = UndeadKitConfig.K_FLASH_TICKS + UndeadKitConfig.K_FLASH_DELAY + 4f;
				if (age < len) {
					// drawn low from the hip and out across, then held out to the side
					float t = ease(age / 2f);
					float w = Math.min(1f, (len - age) / 4f);
					pose.translate(-sd * Mth.lerp(t, -0.15f, 0.55f) * w, Mth.lerp(t, -0.15f, 0.02f) * w, -0.1f * w);
					pose.mulPose(Axis.YP.rotationDegrees(sd * Mth.lerp(t, -40f, 85f) * w));
					pose.mulPose(Axis.ZP.rotationDegrees(sd * Mth.lerp(t, 10f, 80f) * w));
				}
			}
			case UndeadKitConfig.ANIM_K_WHIRL -> {
				float len = UndeadKitConfig.K_WHIRL_TICKS + 3f;
				if (age < len) {
					float turn = Mth.clamp(age / (UndeadKitConfig.K_WHIRL_TICKS - 1f), 0f, 1f);
					float ph = (1f - (1f - turn) * (1f - turn)) * Mth.TWO_PI * 2f;
					float w = fade(age, len);
					pose.translate(-sd * Mth.sin(ph) * 0.45f * w, 0.05f * w, -0.1f * w);
					pose.mulPose(Axis.YP.rotationDegrees(sd * Mth.sin(ph) * 80f * w));
					pose.mulPose(Axis.ZP.rotationDegrees(sd * 75f * w));
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
		boolean slim = UndeadRender.slim(player);
		float[] b = UndeadTexels.box(armPart, slim);
		float handEnd = b[1] + b[4];
		if (model != null && (armPart == UndeadState.RIGHT_ARM || armPart == UndeadState.LEFT_ARM)) {
			ModelPart base = UndeadRender.part(model, armPart), over = UndeadRender.overlay(model, armPart);
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
			VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(UndeadRender.skin(player)));
			base.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			over.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
			pose.popPose();
			base.loadPose(pb);
			over.loadPose(po);
			base.visible = vb;
			over.visible = vo;
		}
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
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
	private static final float[][] DRIP_AT = { { 0f, -0.9f, TIP_Z + 1.2f, 0.5f }, { 0f, 0.62f, -8.5f, 1.2f }, { 0f, 0.45f, -16.5f, 0.9f },
			{ 0f, 0.1f, -23.0f, 1.1f }, { 0f, 2.3f, -1.8f, 1.0f } };
	private static final int[] DRIP_EVERY = { 47, 63, 55, 71, 79 };
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
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		for (int i = 0; i < DRIP_AT.length; i++) {
			float period = DRIP_EVERY[i];
			float tt = now + i * 17.3f;
			int cycle = (int) Math.floor(tt / period);
			float c = (tt - cycle * period) / period; // 0 .. 1 through this drop's swelling
			float[] a = DRIP_AT[i];
			float len = a[3] + 1.9f * c * c; // the run's own length, then the drop swelling at its end
			float w = 0.11f + 0.09f * c;
			bead(vc, pose.last(), a[0], a[1], a[2], d, len, w, c, light);
			if (cycle != seen[i]) {
				boolean first = seen[i] == 0;
				seen[i] = cycle;
				if (!first && !mc.isPaused()) {
					// it let go: the drop falls from the bead's end, in the world
					float reach = a[3] + 1.9f;
					Vector4f end = m.transform(new Vector4f((a[0] + d.x * reach) / 16f, (a[1] + d.y * reach) / 16f, (a[2] + d.z * reach) / 16f, 1f));
					Vec3 at = firstPerson ? viewToWorld(end) : cameraRelative(end);
					BloodFx.bladeDrip(at, player.getDeltaMovement(), 0.035f + 0.01f * (i % 3));
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

	/** The katana's cutting edge: near-white bone, sharpened thin. */
	static final int EDGE = 0xF6F2E8;
	/** Where the blade starts (the collar) and how far it bows toward the spine at the tip (px). */
	static final float BLADE_Z0 = -2.6f, SORI = 1.7f;

	/** How far the blade bows toward its spine (-y) at {@code u} (0 at the collar, 1 at the tip). */
	static float curve(float u) {
		return -SORI * u * u;
	}

	/**
	 * The whole katana in sword space ({@code light} for everything; {@code wet}: held, its drops drawn by {@link #drips}). The blade
	 * is built from short straight pieces stepping round its curve, each a stack of strips from the spine to the edge.
	 */
	static void mesh(PoseStack pose, MultiBufferSource buffers, int light, float now, boolean wet) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
		PoseStack.Pose p = pose.last();
		float len = BLADE_Z0 - TIP_Z;
		int n = 26;
		float kissaki = 0.88f;
		for (int i = 0; i < n; i++) {
			float u0 = i / (float) n * kissaki, u1 = (i + 1) / (float) n * kissaki, um = (u0 + u1) * 0.5f;
			float z0 = BLADE_Z0 - u0 * len, z1 = BLADE_Z0 - u1 * len;
			float y = curve(um);
			float spine = y - 0.9f + 0.14f * um, edge = y + 0.9f - 0.2f * um;
			float th = 0.26f - 0.08f * um; // thinner toward the tip
			// the spine (mune), the flat, the blood groove (hi) down it, the hamon (a wavy line of blood), the white edge
			box(vc, p, -th, spine, z1, th, spine + 0.34f, z0, BONE_SHADE, light);
			box(vc, p, -th * 0.82f, spine + 0.34f, z1, th * 0.82f, edge - 0.44f, z0, BONE, light);
			if (um < 0.72f) {
				box(vc, p, -th * 0.82f - 0.012f, spine + 0.44f, z1, th * 0.82f + 0.012f, spine + 0.64f, z0, BLOOD_DARK, light);
			}
			float wave = Mth.sin(i * 1.35f) * 0.09f;
			box(vc, p, -th * 0.7f, edge - 0.44f + wave, z1, th * 0.7f, edge - 0.21f, z0, i % 2 == 0 ? BLOOD_GLOSS : BLOOD_WET, light);
			box(vc, p, -0.09f, edge - 0.21f, z1, 0.09f, edge, z0, EDGE, light);
		}
		// the yokote: the line where the point begins
		float yk = curve(kissaki), zk = BLADE_Z0 - kissaki * len;
		box(vc, p, -0.21f, yk - 0.66f, zk - 0.08f, 0.21f, yk + 0.6f, zk + 0.08f, BONE_DARK, light);
		// the point (kissaki): the edge sweeps up to meet the spine
		int kn = 5;
		for (int k = 0; k < kn; k++) {
			float a0 = k / (float) kn, a1 = (k + 1) / (float) kn;
			float u0 = kissaki + (1f - kissaki) * a0, u1 = kissaki + (1f - kissaki) * a1;
			float z0 = BLADE_Z0 - u0 * len, z1 = BLADE_Z0 - u1 * len;
			float y = curve((u0 + u1) * 0.5f);
			float spine = y - 0.62f;
			float depth = 1.3f * (1f - a1 * a1) + 0.18f;
			float th = 0.18f * (1f - a0 * 0.6f);
			box(vc, p, -th, spine, z1, th, spine + depth * 0.62f, z0, BONE, light);
			box(vc, p, -0.08f, spine + depth * 0.62f, z1, 0.08f, spine + depth, z0, EDGE, light);
			box(vc, p, -th - 0.01f, spine, z1, th + 0.01f, spine + 0.22f, z0, BONE_SHADE, light);
		}
		// fresh blood on the blade: runs down the flats from the collar, and a smear near the point
		float[][] smears = { { 1, 0.02f, 0.22f, 0.15f }, { -1, 0.0f, 0.3f, -0.05f }, { 1, 0.38f, 0.52f, 0.05f }, { -1, 0.6f, 0.78f, 0.2f },
				{ 1, 0.8f, 0.92f, -0.1f } };
		for (float[] sm : smears) {
			float zA = BLADE_Z0 - sm[1] * len, zB = BLADE_Z0 - sm[2] * len;
			float y = curve((sm[1] + sm[2]) * 0.5f) + sm[3];
			float x = sm[0] * 0.235f;
			box(vc, p, Math.min(x, x + sm[0] * 0.03f) - 0.01f, y - 0.16f, zB, Math.max(x, x + sm[0] * 0.03f) + 0.01f, y + 0.16f, zA, BLOOD, light);
			box(vc, p, Math.min(x, x + sm[0] * 0.03f) - 0.012f, y - 0.2f, zB - 0.01f, Math.max(x, x + sm[0] * 0.03f) + 0.012f, y + 0.2f, zB + 0.5f, BLOOD_DARK,
					light);
		}
		if (!wet) {
			// thrown: a drop hanging off the point
			box(vc, p, -0.14f, 0.0f, TIP_Z + 0.5f, 0.14f, 1.0f, TIP_Z + 0.8f, BLOOD_WET, light);
		}

		// the collar (habaki) of congealed blood round the blade's root, glossy
		box(vc, p, -0.44f, -0.86f, -3.5f, 0.44f, 0.86f, -2.4f, BLOOD, light);
		box(vc, p, -0.46f, -0.6f, -3.3f, 0.46f, -0.2f, -2.6f, BLOOD_GLOSS, light);
		box(vc, p, -0.5f, -1.0f, -2.4f, 0.5f, 1.0f, -2.2f, BONE_DARK, light); // a bone washer
		// the guard (tsuba): an oval of bone, a rim, four small openings and a bead of blood
		box(vc, p, -1.3f, -2.1f, -2.2f, 1.3f, 2.1f, -1.62f, BONE_SHADE, light);
		box(vc, p, -1.65f, -1.7f, -2.2f, 1.65f, 1.7f, -1.62f, BONE_SHADE, light);
		box(vc, p, -1.9f, -1.1f, -2.2f, 1.9f, 1.1f, -1.62f, BONE_SHADE, light);
		box(vc, p, -0.95f, -1.45f, -2.27f, 0.95f, 1.45f, -1.55f, BONE, light);
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				box(vc, p, sx * 1.25f - 0.22f, sy * 1.25f - 0.22f, -2.24f, sx * 1.25f + 0.22f, sy * 1.25f + 0.22f, -1.58f, BONE_DARK, light);
			}
		}
		box(vc, p, -0.3f, 1.55f, -2.3f, 0.3f, 2.0f, -1.5f, BLOOD, light);
		box(vc, p, -0.5f, -1.0f, -1.62f, 0.5f, 1.0f, -1.42f, BONE_DARK, light); // a bone washer
		// the grip (tsuka): a collar, white ray-skin bone wrapped in dark red, crossed so diamonds of bone show between
		box(vc, p, -0.74f, -1.0f, -1.42f, 0.74f, 1.0f, -0.8f, BONE_DARK, light);
		box(vc, p, -0.62f, -0.88f, -0.8f, 0.62f, 0.88f, 6.6f, BONE, light);
		for (int i = 0; i < 7; i++) {
			float z = -0.62f + i * 1.04f;
			box(vc, p, -0.69f, -0.95f, z, 0.69f, 0.95f, z + 0.6f, BLOOD_DARK, light);
			box(vc, p, -0.7f, -0.25f, z + 0.12f, 0.7f, 0.25f, z + 0.48f, BLOOD, light); // the crossing on the flats
		}
		box(vc, p, 0.66f, -0.32f, 2.3f, 0.8f, 0.32f, 3.2f, BONE_SHADE, light); // the ornaments under the wrap
		box(vc, p, -0.8f, -0.32f, 2.9f, -0.66f, 0.32f, 3.8f, BONE_SHADE, light);
		// the end cap (kashira) of bone
		box(vc, p, -0.76f, -1.02f, 6.6f, 0.76f, 1.02f, 7.4f, BONE_SHADE, light);
		box(vc, p, -0.56f, -0.78f, 7.4f, 0.56f, 0.78f, 7.75f, BONE_DARK, light);
	}

	/** A shard of the Splinter Volley, pointing along +z (the pose at the shard's middle). */
	static void splinter(PoseStack pose, MultiBufferSource buffers, int light) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(UndeadRender.PALETTE));
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
