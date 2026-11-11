package com.projecthero.mod.client.undead;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadStages;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.client.mixin.UndeadItemInHandInvoker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: Undead's own view. Flying: both forearm stumps swept down into the bottom corners, a blood jet out of each, and -- when
 * boosting -- a FOV kick and a slight shake. Dragging himself: the arm reaching out ahead and pulling back. Walking on his
 * hands: both hands stepping in turn. Winding up the stretch punch: the arm drawn back and down out of the way, trembling.
 */
public final class UndeadFirstPerson {
	private static float boost, prevBoost;

	private UndeadFirstPerson() {
	}

	/** From the client tick (the local player). */
	static void tick(UndeadState s) {
		prevBoost = boost;
		// v0.17.3: the kick is the Combustion Punch's rush now (the boost is gone)
		boolean on = s != null && s.hasPower && s.animId == UndeadState.ANIM_COMBUSTION;
		boost = on ? Math.min(1f, boost + 0.25f) : Math.max(0f, boost - 0.15f);
	}

	public static double fovScale(float partial) {
		float b = Mth.lerp(partial, prevBoost, boost);
		return 1.0 + 0.16 * b * b * (3f - 2f * b);
	}

	public static void shake(PoseStack pose, float partial) {
		float b = Mth.lerp(partial, prevBoost, boost);
		Minecraft mc = Minecraft.getInstance();
		if (b <= 0.001f || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
			return;
		}
		float t = mc.level.getGameTime() + partial;
		pose.mulPose(Axis.ZP.rotationDegrees((Mth.sin(t * 1.9f) * 0.45f + Mth.sin(t * 4.3f) * 0.2f) * b));
		pose.mulPose(Axis.XP.rotationDegrees((Mth.sin(t * 2.6f) * 0.3f + Mth.sin(t * 5.1f) * 0.15f) * b));
	}

	/**
	 * From {@code renderArmWithItem} HEAD: true if Undead's own view took this hand over (the caller cancels vanilla's). Only the
	 * main hand's call draws, both arms at once.
	 */
	public static boolean arms(ItemInHandRenderer renderer, AbstractClientPlayer player, float partial, InteractionHand hand, PoseStack pose,
			MultiBufferSource buffers, int light) {
		UndeadState s = Undead.peek(player);
		if (s == null || !s.hasPower) {
			return false;
		}
		float now = player.level().getGameTime() + partial;
		if (UndeadHeadThrowRender.firstPersonRip(renderer, player, s, now, hand, pose, buffers, light)) {
			return true; // v0.17.3 hold X: both hands up on his head, wrenching it off
		}
		boolean flying = s.flag(UndeadState.FLAG_PROPELLING);
		int stage = UndeadStages.stage(s, now);
		boolean punch = s.flag(UndeadState.FLAG_PUNCH);
		if (!flying && stage == UndeadStages.NORMAL && UndeadBoneSwordRender.firstPerson(renderer, player, s, partial, hand, pose, buffers, light)) {
			return true; // v0.17.3: the Bone Sword in hand (and the hold-V struggle for it)
		}
		if (!flying && ejectArms(renderer, player, s, now, hand, pose, buffers, light)) {
			return true; // v0.17.3: the ejection moves' own view
		}
		if (!flying && stage != UndeadStages.CRAWL && stage != UndeadStages.ARM_WALK && !punch) {
			return false;
		}
		if (punch && !flying && stage != UndeadStages.CRAWL && stage != UndeadStages.ARM_WALK) {
			if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() && !s.flag(UndeadState.FLAG_OFF_ARM)) {
				return false; // a held item stays vanilla's; the punch arm is the bare main one
			}
			int arm = s.flag(UndeadState.FLAG_OFF_ARM) ? Undead.otherArm(Undead.mainArm(player)) : Undead.mainArm(player);
			HumanoidArm side = arm == UndeadState.RIGHT_ARM ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
			float e = Mth.clamp((now - s.ex(UndeadState.X_PUNCH_START)) / 6f, 0f, 1f);
			pose.pushPose();
			float sd = side == HumanoidArm.RIGHT ? 1f : -1f;
			pose.translate(0.12f * sd * e + Mth.sin(now * 3.1f) * 0.006f * e, -0.38f * e, 0.32f * e);
			pose.mulPose(Axis.XP.rotationDegrees(28f * e));
			((UndeadItemInHandInvoker) renderer).projecthero$renderPlayerArm(pose, buffers, light, 0f, 0f, side);
			pose.popPose();
			return true;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return true; // the main hand's call draws both
		}
		float swing = player.walkAnimation.position(partial);
		float amount = Math.min(1f, player.walkAnimation.speed(partial) * 2.2f);
		for (HumanoidArm side : HumanoidArm.values()) {
			int arm = side == HumanoidArm.RIGHT ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
			if (s.broken(arm) && s.regrowth(arm, now) <= 0f) {
				continue;
			}
			float sd = side == HumanoidArm.RIGHT ? 1f : -1f;
			pose.pushPose();
			if (flying) {
				// the forearm stumps swept down and back into the corners, jets roaring out of them
				float wob = Mth.sin(now * 1.7f + sd) * 0.01f;
				pose.translate(0.06f * sd, -0.36f + wob, 0.16f);
				pose.mulPose(Axis.XP.rotationDegrees(30f));
				pose.mulPose(Axis.ZP.rotationDegrees(14f * sd));
			} else if (stage == UndeadStages.CRAWL) {
				if (arm != Undead.mainArm(player) && !s.has(arm, now) && !UndeadStages.growing(s, arm, now)) {
					pose.popPose();
					continue;
				}
				// reach ahead, plant, drag back toward him; two arms take turns, half a stroke apart (UndeadPose's belly crawl)
				float c = (swing * 0.16f + (arm != Undead.mainArm(player) ? 0.5f : 0f)) % 1f;
				float reach;
				if (amount < 0.05f) {
					reach = 0.6f;
				} else if (c < 0.58f) {
					float k = c / 0.58f;
					reach = 1f - k * k * (3f - 2f * k);
				} else {
					float k = (c - 0.58f) / 0.42f;
					reach = k;
				}
				float lift = amount < 0.05f || c < 0.58f ? 0f : Mth.sin((c - 0.58f) / 0.42f * Mth.PI) * 0.12f;
				pose.translate(0.02f * sd, 0.14f + lift - 0.2f * (1f - reach), -0.6f * reach + 0.05f);
				pose.mulPose(Axis.XP.rotationDegrees(-35f * reach));
			} else {
				// walking on his hands: each hand comes down in turn
				float step = Mth.sin(swing * 0.6662f + (sd > 0 ? 0f : Mth.PI)) * amount;
				pose.translate(0.02f * sd, -0.18f + 0.07f * Math.max(0f, step), -0.12f * step);
				pose.mulPose(Axis.XP.rotationDegrees(30f));
			}
			((UndeadItemInHandInvoker) renderer).projecthero$renderPlayerArm(pose, buffers, light, 0f, 0f, side);
			pose.popPose();
		}
		return true;
	}

	/**
	 * v0.17.3: his own view during the ejection moves (true if one took the hands over; only the main hand's call draws):
	 * <ul>
	 * <li>Blood Spray: both forearm stumps held out low in the bottom corners, the streams leaving them toward the crosshair
	 * ({@link UndeadBloodMovesFx}), braced and shaking;</li>
	 * <li>Deadline: the firing arm's stump out low in its corner;</li>
	 * <li>Blood Whirlwind: the arms are out to his sides -- nothing in front of him;</li>
	 * <li>Combustion Punch: the one arm thrust out ahead, low (the fist leading), and driven forward on the punch.</li>
	 * </ul>
	 */
	private static boolean ejectArms(ItemInHandRenderer renderer, AbstractClientPlayer player, UndeadState s, float now, InteractionHand hand, PoseStack pose,
			MultiBufferSource buffers, int light) {
		float age = now - s.animStart;
		boolean spray = s.flag(UndeadState.FLAG_BLOOD_SPRAY);
		boolean line = s.flag(UndeadState.FLAG_DEAD_LINE) && !s.flag(UndeadState.FLAG_DEAD_ROAD);
		boolean whirl = s.animId == UndeadState.ANIM_BLOOD_WHIRL && age >= 0f && age < com.projecthero.mod.undead.UndeadEjectConfig.WHIRL_TICKS + 4f;
		boolean rush = s.animId == UndeadState.ANIM_COMBUSTION && age >= 0f;
		boolean punch = s.animId == UndeadState.ANIM_COMBUSTION_PUNCH && age >= 0f && age < 14f;
		if (!spray && !line && !whirl && !rush && !punch) {
			return false;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return true;
		}
		if (whirl) {
			return true; // arms spread out to the sides: out of view
		}
		float shake = Mth.sin(now * 3.7f) * 0.006f;
		for (HumanoidArm side : HumanoidArm.values()) {
			int arm = side == HumanoidArm.RIGHT ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
			if (s.broken(arm)) {
				continue;
			}
			int firing = com.projecthero.mod.undead.UndeadBloodMoves.deadlineArm(player, s);
			if ((line || rush || punch) && arm != firing) {
				continue; // one arm only (Deadline: the firing one; Combustion Punch: the one he kept)
			}
			float sd = side == HumanoidArm.RIGHT ? 1f : -1f;
			pose.pushPose();
			if (rush || punch) {
				// the fist out ahead, low and in toward the middle; the punch drives it forward and back
				float drive = punch ? (age < 2f ? age / 2f : Math.max(0f, 1f - (age - 2f) / 10f)) : 0f;
				float tremble = rush ? Mth.sin(now * 3.3f) * 0.008f : 0f;
				pose.translate(-0.1f * sd + tremble, 0.02f, -0.18f - 0.22f * drive);
				pose.mulPose(Axis.XP.rotationDegrees(-14f - 10f * drive));
			} else {
				// the stump held out level, low in its corner, the hand gone (the cut is drawn by UndeadFirstPersonArmMixin)
				pose.translate(-0.06f * sd + shake, 0.2f + shake, -0.32f);
				pose.mulPose(Axis.XP.rotationDegrees(-8f));
				pose.mulPose(Axis.YP.rotationDegrees(4f * sd));
			}
			((UndeadItemInHandInvoker) renderer).projecthero$renderPlayerArm(pose, buffers, light, 0f, 0f, side);
			pose.popPose();
		}
		return true;
	}

	/** After a cut arm is drawn in first person (the pose at the hand renderer's arm space): its jet while flying. */
	public static void afterArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, int part, ModelPart arm, UndeadCuts.Cut cut) {
		UndeadState s = Undead.peek(player);
		if (s == null || !s.flag(UndeadState.FLAG_PROPELLING)) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		float[] b = UndeadTexels.box(part, UndeadRender.slim(player));
		pose.translate((b[0] + b[3] * 0.5f) / 16f, (b[1] + cut.rows()) / 16f, (b[2] + b[5] * 0.5f) / 16f);
		// the spurt erupts out of the cut face and the wind folds it straight back along the forearm (a short fountain at the
		// bottom of the view: it never reaches the camera)
		UndeadJets.firstPersonArm(pose, buffers, light, player, part, 0f, 1f, 0f, 0f, -14f, 0f);
		pose.popPose();
	}

	/** First person, flying: the spray off the jets streaming back past the bottom corners of the view (world droplets). */
	static void spray(AbstractClientPlayer player, UndeadState s) {
		Vec3 look = player.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		Vec3 down = right.cross(look).normalize().scale(-1);
		Vec3 eye = player.getEyePosition();
		for (int sd = -1; sd <= 1; sd += 2) {
			int arm = sd > 0 ? UndeadState.RIGHT_ARM : UndeadState.LEFT_ARM;
			if (s.broken(arm)) {
				continue;
			}
			// (far enough out that BloodFx's near-camera fade lets them show: a few drops at the bottom corners)
			Vec3 at = eye.add(look.scale(1.8)).add(right.scale(0.62 * sd)).add(down.scale(1.0));
			UndeadJets.firstPersonSpray(player, s, at, look.scale(-1).add(down.scale(0.4)).normalize());
		}
	}
}
