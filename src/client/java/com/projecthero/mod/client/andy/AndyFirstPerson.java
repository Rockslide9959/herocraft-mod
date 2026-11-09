package com.projecthero.mod.client.andy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.andy.Andy;
import com.projecthero.mod.andy.AndyStages;
import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.client.mixin.AndyItemInHandInvoker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * v0.17.3: Andy's own view. Flying: both forearm stumps swept down into the bottom corners, a blood jet out of each, and -- when
 * boosting -- a FOV kick and a slight shake. Dragging himself: the arm reaching out ahead and pulling back. Walking on his
 * hands: both hands stepping in turn. Winding up the stretch punch: the arm drawn back and down out of the way, trembling.
 */
public final class AndyFirstPerson {
	private static float boost, prevBoost;

	private AndyFirstPerson() {
	}

	/** From the client tick (the local player). */
	static void tick(AndyState s) {
		prevBoost = boost;
		boolean on = s != null && s.hasPower && s.flag(AndyState.FLAG_PROPELLING) && s.flag(AndyState.FLAG_BOOST);
		boost = on ? Math.min(1f, boost + 0.2f) : Math.max(0f, boost - 0.12f);
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
	 * From {@code renderArmWithItem} HEAD: true if Andy's own view took this hand over (the caller cancels vanilla's). Only the
	 * main hand's call draws, both arms at once.
	 */
	public static boolean arms(ItemInHandRenderer renderer, AbstractClientPlayer player, float partial, InteractionHand hand, PoseStack pose,
			MultiBufferSource buffers, int light) {
		AndyState s = Andy.peek(player);
		if (s == null || !s.hasPower) {
			return false;
		}
		float now = player.level().getGameTime() + partial;
		boolean flying = s.flag(AndyState.FLAG_PROPELLING);
		int stage = AndyStages.stage(s, now);
		boolean punch = s.flag(AndyState.FLAG_PUNCH);
		if (!flying && stage != AndyStages.CRAWL && stage != AndyStages.ARM_WALK && !punch) {
			return false;
		}
		if (punch && !flying && stage != AndyStages.CRAWL && stage != AndyStages.ARM_WALK) {
			if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() && !s.flag(AndyState.FLAG_OFF_ARM)) {
				return false; // a held item stays vanilla's; the punch arm is the bare main one
			}
			int arm = s.flag(AndyState.FLAG_OFF_ARM) ? Andy.otherArm(Andy.mainArm(player)) : Andy.mainArm(player);
			HumanoidArm side = arm == AndyState.RIGHT_ARM ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
			float e = Mth.clamp((now - s.ex(AndyState.X_PUNCH_START)) / 6f, 0f, 1f);
			pose.pushPose();
			float sd = side == HumanoidArm.RIGHT ? 1f : -1f;
			pose.translate(0.12f * sd * e + Mth.sin(now * 3.1f) * 0.006f * e, -0.38f * e, 0.32f * e);
			pose.mulPose(Axis.XP.rotationDegrees(28f * e));
			((AndyItemInHandInvoker) renderer).projecthero$renderPlayerArm(pose, buffers, light, 0f, 0f, side);
			pose.popPose();
			return true;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return true; // the main hand's call draws both
		}
		float swing = player.walkAnimation.position(partial);
		float amount = Math.min(1f, player.walkAnimation.speed(partial) * 2.2f);
		for (HumanoidArm side : HumanoidArm.values()) {
			int arm = side == HumanoidArm.RIGHT ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM;
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
			} else if (stage == AndyStages.CRAWL) {
				if (arm != Andy.mainArm(player) && !s.has(arm, now) && !AndyStages.growing(s, arm, now)) {
					pose.popPose();
					continue;
				}
				// reach ahead, plant, drag back toward him; two arms take turns, half a stroke apart (AndyPose's belly crawl)
				float c = (swing * 0.16f + (arm != Andy.mainArm(player) ? 0.5f : 0f)) % 1f;
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
			((AndyItemInHandInvoker) renderer).projecthero$renderPlayerArm(pose, buffers, light, 0f, 0f, side);
			pose.popPose();
		}
		return true;
	}

	/** After a cut arm is drawn in first person (the pose at the hand renderer's arm space): its jet while flying. */
	public static void afterArm(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, int part, ModelPart arm, AndyCuts.Cut cut) {
		AndyState s = Andy.peek(player);
		if (s == null || !s.flag(AndyState.FLAG_PROPELLING)) {
			return;
		}
		float now = player.level().getGameTime() + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		boolean boost = s.flag(AndyState.FLAG_BOOST);
		pose.pushPose();
		arm.translateAndRotate(pose);
		float[] b = AndyTexels.box(part, AndyRender.slim(player));
		pose.translate((b[0] + b[3] * 0.5f) / 16f, (b[1] + cut.rows()) / 16f, (b[2] + b[5] * 0.5f) / 16f);
		Matrix4f m = pose.last().pose();
		// the jet streams back along the forearm, past the bottom of the view (a sheath wider than the arm)
		Vector3f dir = new Vector3f(0, -1, 0), a = new Vector3f(1, 0, 0), c = new Vector3f(0, 0, 1);
		float flick = 0.85f + 0.15f * Mth.sin(now * 2.9f + part) + 0.1f * Mth.sin(now * 6.3f + part * 2f);
		float len = (boost ? 1.3f : 0.85f) * flick, rad = boost ? 0.09f : 0.07f;
		AndyEffectsRenderer.jetConePublic(buffers.getBuffer(RenderType.lightning()), m, dir, a, c, len, rad * 1.7f, 0.55f, 0.02f, 0.03f, 0.5f);
		AndyEffectsRenderer.jetConePublic(buffers.getBuffer(RenderType.debugQuads()), m, dir, a, c, len * 0.8f, rad, 0.6f, 0.03f, 0.05f, 0.95f);
		AndyEffectsRenderer.jetConePublic(buffers.getBuffer(RenderType.lightning()), m, dir, a, c, len * 0.5f, rad * 0.45f, 1.0f, 0.25f, 0.2f, 0.8f);
		pose.popPose();
	}

	/** First person, flying: the spray off the jets streaming back past the bottom corners of the view (world droplets). */
	static void spray(AbstractClientPlayer player, AndyState s) {
		Vec3 look = player.getLookAngle();
		Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
		Vec3 down = right.cross(look).normalize().scale(-1);
		Vec3 eye = player.getEyePosition();
		float power = s.flag(AndyState.FLAG_BOOST) ? 1.6f : 1f;
		for (int sd = -1; sd <= 1; sd += 2) {
			int arm = sd > 0 ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM;
			if (s.broken(arm)) {
				continue;
			}
			Vec3 at = eye.add(look.scale(0.55)).add(right.scale(0.42 * sd)).add(down.scale(0.5));
			BloodFx.jet(at, look.scale(-1).add(down.scale(0.4)).normalize(), player.getDeltaMovement(), power * 0.6f);
		}
	}
}
