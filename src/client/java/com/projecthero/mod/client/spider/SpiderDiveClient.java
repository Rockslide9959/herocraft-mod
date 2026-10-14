package com.projecthero.mod.client.spider;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.SpiderWebWings;
import com.projecthero.mod.spider.data.SpiderMoveState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.3 (user): the head-first dive -- a Spider-Man falling a long way (not swinging, not gliding on Web Wings, not
 * on a wall, not mid-move) tips over head-first, arms swept back along his sides and legs together, and rights himself
 * again the moment he fires a web, opens his wings or lands. Purely visual: no change to the fall or the controls.
 *
 * <p>Seen by every viewer: each client watches each Spider-Man's own height (fall distance is not synced), counts the
 * drop from the top of his fall and eases the pose in once he has dropped {@link #DIVE_AFTER} blocks.
 */
public final class SpiderDiveClient {
	/** Blocks fallen (from the top of the fall) before he tips into the dive. */
	public static final double DIVE_AFTER = 8.0;
	/** He has to be falling at least this fast (blocks / tick) -- a slow drift is not a dive. */
	private static final double MIN_FALL_SPEED = 0.45;
	/** Degrees the body is pitched forward at full dive (180 = straight down head-first). */
	private static final float DIVE_PITCH = 150f;

	private static final class Anim {
		double peakY;
		float amt;
		float prevAmt;
	}

	private static final Map<Integer, Anim> ANIM = new HashMap<>();

	private SpiderDiveClient() {
	}

	public static void reset() {
		ANIM.clear();
	}

	/** Not swinging, gliding, crawling, hanging, flying, swimming or riding -- just falling. */
	private static boolean freeFalling(Player p) {
		if (p.onGround() || p.isInWater() || p.isInLava() || p.isPassenger() || p.isFallFlying() || p.getAbilities().flying
				|| p.onClimbable() || p.isSpectator()) {
			return false;
		}
		if (SpiderSwing.isSwinging(p) || SpiderWebWings.isGliding(p) || SpiderWebWingsClient.amount(p, 1f) > 0f
				|| SpiderClimb.attached(p)) {
			return false;
		}
		SpiderMoveState s = p.getAttachedOrElse(ModAttachments.SPIDER_MOVE_STATE, null);
		if (s != null && (s.has(SpiderMoveState.FLAG_HANGING)
				|| s.animId != 0 && p.level().getGameTime() - s.animStart < 30)) {
			return false; // a move's animation owns the body
		}
		return true;
	}

	/** End of every client tick: track each Spider-Man's fall and ease his dive in / out. */
	public static void clientTick(Minecraft mc) {
		if (mc.level == null) {
			ANIM.clear();
			return;
		}
		if (mc.level.getGameTime() % 100 == 0) {
			ANIM.keySet().removeIf(id -> !(mc.level.getEntity(id) instanceof Player));
		}
		for (Player p : mc.level.players()) {
			if (!SpiderMan.hasPower(p)) {
				ANIM.remove(p.getId());
				continue;
			}
			Anim a = ANIM.computeIfAbsent(p.getId(), id -> new Anim());
			boolean falling = freeFalling(p);
			if (!falling || p.getY() > a.peakY) {
				a.peakY = p.getY(); // the top of the fall: wherever he last was not falling, or the highest point since
			}
			double vy = p.getY() - p.yo;
			boolean on = falling && a.peakY - p.getY() >= DIVE_AFTER && vy < -MIN_FALL_SPEED;
			a.prevAmt = a.amt;
			// tips over gently, rights himself fast (a web, the wings or the ground take over)
			a.amt = on ? Math.min(1f, a.amt + 0.08f) : falling ? Math.max(0f, a.amt - 0.12f) : Math.max(0f, a.amt - 0.35f);
		}
	}

	/** How far into the dive this player is (0..1). */
	public static float amount(Player p, float partial) {
		Anim a = ANIM.get(p.getId());
		return a == null ? 0f : smooth(Mth.lerp(partial, a.prevAmt, a.amt));
	}

	private static float smooth(float x) {
		return x * x * (3f - 2f * x);
	}

	/** From {@code PlayerRenderer.setupRotations}: pitch the body over, head-first, about his middle. */
	public static void applyBodyRotation(Player p, PoseStack pose, float partial) {
		float a = amount(p, partial);
		if (a <= 0f) {
			return;
		}
		// a slow wobble, so the long drop reads as air rushing past rather than a frozen statue
		float t = p.tickCount + partial;
		float wobble = Mth.sin(t * 0.35f) * 3f * a;
		pose.translate(0.0f, 0.9f, 0.0f);
		pose.mulPose(Axis.ZP.rotationDegrees(wobble));
		pose.mulPose(Axis.XP.rotationDegrees(-DIVE_PITCH * a));
		pose.translate(0.0f, -0.9f, 0.0f);
	}

	/** From {@code HumanoidModelMixin}: arms swept back along his sides, legs together, head up toward the ground. */
	public static void applyPose(Player p, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float a = amount(p, partial);
		if (a <= 0f) {
			return;
		}
		float flutter = Mth.sin((p.tickCount + partial) * 1.1f) * 0.04f;
		// the head lifts against the pitched body, so he looks where he is going (down)
		m.head.xRot = Mth.lerp(a, m.head.xRot, -0.75f);
		m.head.yRot = Mth.lerp(a, m.head.yRot, 0f);
		m.hat.copyFrom(m.head);
		m.rightArm.xRot = Mth.lerp(a, m.rightArm.xRot, 0.35f + flutter);
		m.rightArm.yRot = Mth.lerp(a, m.rightArm.yRot, 0.1f);
		m.rightArm.zRot = Mth.lerp(a, m.rightArm.zRot, 0.22f);
		m.leftArm.xRot = Mth.lerp(a, m.leftArm.xRot, 0.35f - flutter);
		m.leftArm.yRot = Mth.lerp(a, m.leftArm.yRot, -0.1f);
		m.leftArm.zRot = Mth.lerp(a, m.leftArm.zRot, -0.22f);
		m.rightLeg.xRot = Mth.lerp(a, m.rightLeg.xRot, 0.05f - flutter * 0.5f);
		m.rightLeg.yRot = Mth.lerp(a, m.rightLeg.yRot, 0f);
		m.rightLeg.zRot = Mth.lerp(a, m.rightLeg.zRot, 0.03f);
		m.leftLeg.xRot = Mth.lerp(a, m.leftLeg.xRot, 0.05f + flutter * 0.5f);
		m.leftLeg.yRot = Mth.lerp(a, m.leftLeg.yRot, 0f);
		m.leftLeg.zRot = Mth.lerp(a, m.leftLeg.zRot, -0.03f);
	}
}
