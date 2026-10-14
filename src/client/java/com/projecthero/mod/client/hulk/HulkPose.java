package com.projecthero.mod.client.hulk;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.GammaOverload;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.13.15: Banner's side of the unwilling change -- he drops to his knees, hunched over and clutching his head, shaking,
 * while the Hulk phases onto him ({@code animation.hulk.transform_forced} holds the same pose on the Hulk model, so the
 * two bodies line up through the cross-fade). Applied to the vanilla humanoid model after its own animation, from
 * {@code HumanoidModelMixin}.
 *
 * <p>The vanilla body pivots at the neck; to lean from the hips like the Hulk rig does, the neck is moved forward and
 * down around a fixed hip point, and the head and shoulders follow it. Model space: +Y is down, -Z is forward, leg X
 * positive swings the foot back, arm X negative raises the arm forward.
 *
 * <p>v0.16.4: the same kneel for the Gamma origin -- soaking up a reactor's flash ({@link #applyAbsorb}) and the body left
 * behind while he is in his mind ({@link #applyHeadache}).
 */
public final class HulkPose {
	/**
	 * Pose numbers: forward lean (rad), head bow (rad). v0.16.4 (user): "the player should be standing up not having there legs
	 * lay backwards" -- every one of these poses (the unwilling change, the Gamma absorb, the mind body) now STANDS: no hip
	 * drop, legs straight in a braced, slightly wide stance; the hunch from the hips, the hands and the shiver stay.
	 */
	private static final float LEAN = 0.38f;
	/** Feet a little apart (rad of leg roll each side) -- braced against it. */
	private static final float STANCE = 0.12f;
	private static final float HEAD_BOW = 0.6f;
	/** Arms raised to clutch the sides of the head (in the body's frame) and angled in. */
	private static final float ARM_UP = -2.55f;
	private static final float ARM_IN = 0.45f;
	/** Ticks to ease into the pose. */
	private static final float DROP_TICKS = 6.0f;

	private HulkPose() {
	}

	/** 0..1: how far into the kneel Banner is right now (0 unless the unwilling change is under way). */
	public static float kneel(Player player, float partialTick) {
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		if (s == null || !Hulk.changing(s, player.level().getGameTime())) {
			return 0.0f;
		}
		float t = (player.level().getGameTime() - s.formChangedAt) + partialTick;
		float f = Math.max(0.0f, Math.min(1.0f, t / DROP_TICKS));
		// he is gone (the Hulk is solid) by the time the Hulk stands, so Banner never has to get back up
		return f * f * (3.0f - 2.0f * f);
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float k = kneel(player, partial);
		if (k <= 0.0f) {
			// v0.16.1: body.z / head.z / hat.z are reset for every player by HumanoidModelMixin#projecthero$resetTorsoZ,
			// first, before any pose (resetting them here clobbered the hip-bent poses applied before this one)
			applyAbsorb(player, m, partial);
			return;
		}
		float age = player.tickCount + partial;
		// fighting it: a shiver that gets worse as the growth takes hold
		HulkState s = player.getAttachedOrElse(ModAttachments.HULK_STATE, null);
		float grow = s == null ? 0.0f
				: Math.max(0.0f, Math.min(1.0f, ((player.level().getGameTime() - s.formChangedAt) + partial - HulkConfig.FORCED_KNEEL_TICKS)
						/ HulkConfig.FORCED_GROWTH_TICKS));
		float shake = (0.03f + 0.06f * grow) * k;
		float jx = Mth.sin(age * 2.7f) * shake;
		float jz = Mth.cos(age * 3.3f) * shake;
		pose(m, k, LEAN * k + jx * 0.5f, HEAD_BOW, ARM_UP, ARM_IN, jx, jz);
	}

	/**
	 * v0.16.4 (user): "during that flash the player needs to fall to their knees and absorb the gamma radiation into their
	 * body" -- {@code GammaOverload.KNEEL_AT} ticks into a reactor's flash he braces where he stands (v0.16.4 user: standing,
	 * not kneeling), arms held a little out from his sides, his head tipping back and his body arching as the radiation
	 * builds, a tremble growing with it. Driven by the synced {@code GAMMA_ABSORB} state, so every viewer sees it.
	 */
	private static void applyAbsorb(Player player, HumanoidModel<?> m, float partial) {
		com.projecthero.mod.hulk.data.GammaAbsorb a = player.getAttachedOrElse(ModAttachments.GAMMA_ABSORB, null);
		if (a == null) {
			return;
		}
		float t = (player.level().getGameTime() - a.startedAt()) + partial;
		float k = Math.max(0.0f, Math.min(1.0f, (t - GammaOverload.KNEEL_AT) / DROP_TICKS));
		if (k <= 0.0f) {
			return;
		}
		k = k * k * (3.0f - 2.0f * k);
		float build = Math.max(0.0f, Math.min(1.0f, (t - GammaOverload.ABSORB_START) / (GammaOverload.FLASH_TICKS - GammaOverload.ABSORB_START)));
		float age = player.tickCount + partial;
		float shake = (0.012f + 0.05f * build) * k;
		float jx = Mth.sin(age * 2.9f) * shake;
		float jz = Mth.cos(age * 3.7f) * shake;
		// standing braced at first, then arching back as it fills him
		float lean = (0.12f - 0.32f * build) * k + jx * 0.5f;
		float head = 0.25f - 1.0f * build;
		pose(m, k, lean, head, -0.35f - 0.25f * build, 0.5f + 0.2f * build, jx, jz);
	}

	/**
	 * v0.16.4: the mind body's pose ({@code HulkMindBodyRenderer}) -- standing, hunched, both hands clutching his head,
	 * looking down at the ground, shaking. {@code age} drives the shiver.
	 */
	public static void applyHeadache(HumanoidModel<?> m, float age) {
		float shake = 0.035f;
		float jx = Mth.sin(age * 2.7f) * shake;
		float jz = Mth.cos(age * 3.3f) * shake;
		pose(m, 1.0f, LEAN + 0.08f + jx * 0.5f, HEAD_BOW + 0.25f, ARM_UP, ARM_IN, jx, jz);
	}

	/**
	 * The pose itself, {@code k} 0..1 of the way into it: standing braced (v0.16.4), the body leans
	 * {@code lean} from the hips (the neck swings forward and down round a fixed hip point, head and shoulders with it), the
	 * head bows to {@code headX}, the arms go to {@code armX} (on top of the lean) and out by {@code armZ}; {@code jx}/{@code jz}
	 * is the shiver.
	 */
	private static void pose(HumanoidModel<?> m, float k, float lean, float headX, float armX, float armZ, float jx, float jz) {
		float hipY = 12.0f;
		// the body leans from the hips: the neck swings forward and down
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);
		m.body.xRot = lean;
		m.body.yRot = Mth.lerp(k, m.body.yRot, 0.0f);
		m.body.y = neckY;
		m.body.z = neckZ;

		m.head.y = neckY;
		m.head.z = neckZ;
		m.head.xRot = Mth.lerp(k, m.head.xRot, headX) + jz;
		m.head.yRot = Mth.lerp(k, m.head.yRot, 0.0f);
		m.head.zRot = jx;
		m.hat.copyFrom(m.head);

		// shoulders sit 2 px below the neck, in the leaning body's frame
		float shoulderY = neckY + 2.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 2.0f * Mth.sin(lean);
		m.rightArm.y = Mth.lerp(k, m.rightArm.y, shoulderY);
		m.leftArm.y = Mth.lerp(k, m.leftArm.y, shoulderY);
		m.rightArm.z = Mth.lerp(k, m.rightArm.z, shoulderZ);
		m.leftArm.z = Mth.lerp(k, m.leftArm.z, shoulderZ);
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, armX + lean) + jz;
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, armX + lean) - jz;
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0.0f);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, armZ);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -armZ);

		// v0.16.4 (user): standing, legs straight and braced a little apart (was on his knees, legs folded back)
		m.rightLeg.y = hipY;
		m.leftLeg.y = hipY;
		m.rightLeg.z = 0.0f;
		m.leftLeg.z = 0.0f;
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, 0.0f);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, 0.0f);
		m.rightLeg.yRot = Mth.lerp(k, m.rightLeg.yRot, 0.0f);
		m.leftLeg.yRot = Mth.lerp(k, m.leftLeg.yRot, 0.0f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, STANCE);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -STANCE);
	}
}
