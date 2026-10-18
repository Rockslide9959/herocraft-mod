package com.projecthero.mod.client.miraculous.catnoir;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.catnoir.CatNoir;
import com.projecthero.mod.miraculous.catnoir.CatNoirConfig;
import com.projecthero.mod.miraculous.data.MiraculousState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: Cat Noir's keyframed poses on the vanilla humanoid model (from {@code HumanoidModelMixin} via the core's
 * {@code KwamiClientKit#applyPose}; his layer copies the model, so the suit follows). Driven by the synced
 * {@code animId} / {@code animStart} on the same game-time clock as the server's hits, so every viewer sees the same
 * thing. Negative {@code xRot} swings a limb forward / up.
 * <ul>
 *   <li><b>Baton Strike</b>: three distinct swings -- a flat forehand cut, a backhand cut the other way, an overhead smash.</li>
 *   <li><b>Low Sweep</b>: dropped into a crouch, the staff swept round at ankle height.</li>
 *   <li><b>Baton Extend</b>: the fist thrust straight along the look, the other arm braced back.</li>
 *   <li><b>Baton Throw</b>: wound back over the shoulder, hurled forward; <b>catch</b>: the fist up to take it.</li>
 *   <li><b>Pole Vault</b>: crouched planting the pole, then hanging on it with both hands, knees tucked, as it shoots up.</li>
 *   <li><b>Baton Spin</b>: the staff whirled in front like a propeller, the other arm out.</li>
 *   <li><b>Cat Leap</b> / <b>Pounce</b>: stretched out flying, arms reaching; the pounce raises both claws and slams down.</li>
 *   <li><b>Night Vision</b>: a hand to the mask. <b>Cat Reflexes</b>: a guard crouch; each <b>dodge</b> a quick lean aside.</li>
 *   <li><b>CATACLYSM</b>: the hand thrown up high ("Cataclysm!"), then held up, claw open, while it crackles; the touch
 *       thrusts it out.</li>
 *   <li><b>Rooftop Run</b> against a wall: climbing hand over hand.</li>
 * </ul>
 */
public final class CatNoirPose {
	private CatNoirPose() {
	}

	/** How long each pose runs, ticks (also how long the baton stays in the hand). */
	public static int length(int animId) {
		return switch (animId) {
			case CatNoir.ANIM_STRIKE -> CatNoirConfig.STRIKE_TICKS;
			case CatNoir.ANIM_SWEEP -> CatNoirConfig.SWEEP_TICKS;
			case CatNoir.ANIM_EXTEND -> CatNoirConfig.EXTEND_OUT_TICKS + CatNoirConfig.EXTEND_HOLD_TICKS + CatNoirConfig.EXTEND_BACK_TICKS;
			case CatNoir.ANIM_THROW -> 8;
			case CatNoir.ANIM_VAULT -> CatNoirConfig.VAULT_POLE_TICKS;
			case CatNoir.ANIM_SPIN -> CatNoirConfig.SPIN_TICKS;
			case CatNoir.ANIM_LEAP -> CatNoirConfig.LEAP_TICKS;
			case CatNoir.ANIM_POUNCE -> 16;
			case CatNoir.ANIM_NIGHT_VISION -> 14;
			case CatNoir.ANIM_REFLEXES -> 12;
			case CatNoir.ANIM_DODGE_LEFT, CatNoir.ANIM_DODGE_RIGHT -> 8;
			case CatNoir.ANIM_CATACLYSM_CALL -> 24;
			case CatNoir.ANIM_CATACLYSM_TOUCH -> 12;
			case CatNoir.ANIM_CATCH -> 8;
			case CatNoir.ANIM_ROOFTOP -> 10;
			default -> 0;
		};
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!CatNoir.active(player) || player.level() == null) {
			return;
		}
		MiraculousState s = Miraculous.peek(player);
		if (s == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart fist = rightMain ? model.rightArm : model.leftArm;
		ModelPart other = rightMain ? model.leftArm : model.rightArm;
		float side = rightMain ? 1f : -1f;
		float t = now + partial;

		// v0.16.6: a held Pole Vault keeps its riding pose for as long as X is held
		boolean vaultHeld = s.animId == CatNoir.ANIM_VAULT && s.val(CatNoir.V_VAULT_ON) != 0L;
		int len = vaultHeld ? Integer.MAX_VALUE / 4 : length(s.animId);
		float age = (now - s.animStart) + partial;
		boolean running = s.animId >= 40 && s.animId < 60 && age >= 0f && age < len;

		// v0.16.7 (user): Rooftop Run, sprinting -- on all fours like a cat (the body is tipped flat by
		// CatNoirAllFoursMixin; here the arms and legs become front and back legs in a bounding gait)
		float fours = running ? 0f : allFours(player, partial);
		if (fours > 0.01f) {
			float phase = player.walkAnimation.position(partial) * 0.55f;
			float front = 0.75f * Mth.sin(phase);
			float back = 0.75f * Mth.sin(phase + (float) Math.PI * 0.8f);
			float down = -(float) Math.PI / 2f;
			pose(model.rightArm, fours, down + front, 0f, 0.08f);
			pose(model.leftArm, fours, down + front * 0.85f, 0f, -0.08f);
			pose(model.rightLeg, fours, down + back, 0f, 0.05f);
			pose(model.leftLeg, fours, down + back * 0.85f, 0f, -0.05f);
			model.body.xRot = Mth.lerp(fours, model.body.xRot, 0f);
			// the head looks ahead (the model lies face down), still nodding with the look a little
			model.head.xRot = Mth.lerp(fours, model.head.xRot, down + model.head.xRot * 0.35f);
			model.hat.copyFrom(model.head);
		}
		// climbing a wall on Rooftop Run: hand over hand
		if (!running && CatNoir.climbing(player) && !player.onGround()) {
			float c = Mth.sin(t * 0.45f);
			pose(model.rightArm, 1f, -2.7f + 0.45f * c, 0f, 0.1f);
			pose(model.leftArm, 1f, -2.7f - 0.45f * c, 0f, -0.1f);
			pose(model.rightLeg, 1f, -0.5f - 0.4f * c, 0f, 0.05f);
			pose(model.leftLeg, 1f, -0.5f + 0.4f * c, 0f, -0.05f);
			model.body.xRot = -0.1f;
			model.head.xRot = Mth.lerp(0.6f, model.head.xRot, -0.5f);
			model.hat.copyFrom(model.head);
		}
		// the charged hand, held up with the claws open, waiting for the touch
		if (CatNoir.charged(player) && !(running && s.animId == CatNoir.ANIM_CATACLYSM_CALL)) {
			float jitter = 0.03f * Mth.sin(t * 2.1f);
			pose(fist, 1f, -1.9f + jitter, -0.25f * side, 0.05f * side);
		}
		if (!running) {
			return;
		}
		float w = Math.min(1f, age / 2f) * Math.min(1f, (len - age) / 3f);
		switch (s.animId) {
			case CatNoir.ANIM_STRIKE -> strike(model, fist, other, side, age, w);
			case CatNoir.ANIM_SWEEP -> {
				float k = smooth(Mth.clamp((age - 1f) / 5f, 0f, 1f));
				pose(fist, w, -0.55f, Mth.lerp(k, 1.3f, -1.3f) * side, 0.35f * side);
				pose(other, w, -0.3f, 0f, -1.1f * side);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.55f);
				crouchLegs(model, w);
			}
			case CatNoir.ANIM_EXTEND -> {
				fist.xRot = Mth.lerp(w, fist.xRot, model.head.xRot - (float) Math.PI / 2f);
				fist.yRot = Mth.lerp(w, fist.yRot, model.head.yRot);
				fist.zRot = Mth.lerp(w, fist.zRot, 0f);
				pose(other, w, 0.5f, 0f, -0.35f * side);
			}
			case CatNoir.ANIM_THROW -> {
				float k = smooth(Mth.clamp((age - 2f) / 3f, 0f, 1f));
				pose(fist, w, Mth.lerp(k, -2.9f, -1.3f), Mth.lerp(k, 0.4f, -0.1f) * side, 0f);
				pose(other, w, -0.8f, 0f, -0.2f * side);
			}
			case CatNoir.ANIM_CATCH -> pose(fist, w, -1.7f, -0.2f * side, 0f);
			case CatNoir.ANIM_VAULT -> {
				float plant = CatNoirConfig.VAULT_PLANT_TICKS;
				if (age < plant) {
					// crouched, both hands driving the pole into the ground in front
					pose(model.rightArm, w, -0.75f, 0.25f, 0f);
					pose(model.leftArm, w, -0.75f, -0.25f, 0f);
					model.body.xRot = Mth.lerp(w, model.body.xRot, 0.45f);
					crouchLegs(model, w);
				} else {
					// riding it up: hands together on the pole at the chest, knees tucked
					pose(model.rightArm, w, -0.45f, 0.5f, 0f);
					pose(model.leftArm, w, -0.45f, -0.5f, 0f);
					pose(model.rightLeg, w, -0.9f, 0f, 0.05f);
					pose(model.leftLeg, w, -0.6f, 0f, -0.05f);
				}
			}
			case CatNoir.ANIM_SPIN -> {
				float wob = 0.06f * Mth.sin(t * 1.3f);
				pose(fist, w, -1.45f + wob, -0.15f * side, 0f);
				pose(other, w, -0.2f, 0f, -0.9f * side);
			}
			case CatNoir.ANIM_LEAP -> {
				pose(model.rightArm, w, -2.5f, 0f, 0.15f);
				pose(model.leftArm, w, -2.5f, 0f, -0.15f);
				pose(model.rightLeg, w, 0.55f, 0f, 0.05f);
				pose(model.leftLeg, w, 0.35f, 0f, -0.05f);
			}
			case CatNoir.ANIM_POUNCE -> {
				float k = smooth(Mth.clamp((age - 6f) / 4f, 0f, 1f));
				pose(model.rightArm, w, Mth.lerp(k, -2.95f, -0.9f), 0f, Mth.lerp(k, -0.25f, 0.1f));
				pose(model.leftArm, w, Mth.lerp(k, -2.95f, -0.9f), 0f, Mth.lerp(k, 0.25f, -0.1f));
				pose(model.rightLeg, w, Mth.lerp(k, 0.4f, -0.4f), 0f, 0.05f);
				pose(model.leftLeg, w, Mth.lerp(k, 0.4f, -0.2f), 0f, -0.05f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35f * k);
			}
			case CatNoir.ANIM_NIGHT_VISION -> {
				pose(fist, w, -2.2f, -0.55f * side, 0f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, model.head.xRot - 0.15f);
				model.hat.copyFrom(model.head);
			}
			case CatNoir.ANIM_REFLEXES -> {
				pose(model.rightArm, w, -1.2f, -0.4f, 0.2f);
				pose(model.leftArm, w, -1.2f, 0.4f, -0.2f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3f);
				crouchLegs(model, w * 0.7f);
			}
			case CatNoir.ANIM_DODGE_LEFT, CatNoir.ANIM_DODGE_RIGHT -> {
				float d = s.animId == CatNoir.ANIM_DODGE_LEFT ? 1f : -1f;
				model.body.zRot = Mth.lerp(w, model.body.zRot, 0.35f * d);
				pose(model.rightArm, w, -0.4f, 0f, 0.9f + 0.2f * d);
				pose(model.leftArm, w, -0.4f, 0f, -0.9f + 0.2f * d);
				pose(model.rightLeg, w, 0f, 0f, 0.3f * d);
				pose(model.leftLeg, w, 0f, 0f, 0.3f * d);
			}
			case CatNoir.ANIM_CATACLYSM_CALL -> {
				// the hand flung straight up, fingers spread: "CATACLYSM!"
				float k = smooth(Mth.clamp(age / 5f, 0f, 1f));
				float settle = smooth(Mth.clamp((age - 16f) / 8f, 0f, 1f));
				float rx = Mth.lerp(settle, Mth.lerp(k, -0.4f, -3.05f), -1.9f);
				pose(fist, Math.min(1f, age / 2f), rx, -0.25f * side * settle, 0.12f * side * (1f - settle));
				pose(other, w, -0.25f, 0f, -0.35f * side);
				model.head.xRot = Mth.lerp(w * (1f - settle), model.head.xRot, -0.55f);
				model.hat.copyFrom(model.head);
			}
			case CatNoir.ANIM_CATACLYSM_TOUCH -> {
				float k = smooth(Mth.clamp(age / 3f, 0f, 1f));
				pose(fist, w, Mth.lerp(k, -1.9f, -1.0f), -0.15f * side, 0f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.25f * k);
			}
			case CatNoir.ANIM_ROOFTOP -> {
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.3f);
				crouchLegs(model, w * 0.6f);
			}
			default -> {
			}
		}
	}

	/** Baton Strike: three distinct swings, each one ending where the next winds up. */
	private static void strike(HumanoidModel<?> model, ModelPart fist, ModelPart other, float side, float age, float w) {
		int[] hits = CatNoirConfig.STRIKE_HITS;
		pose(other, w, -0.5f, 0f, -0.45f * side);
		if (age < hits[0] + 2) {
			// 1: forehand -- out wide to the right, cut flat across the front
			float k = smooth(Mth.clamp((age - (hits[0] - 2)) / 3f, 0f, 1f));
			pose(fist, w, -1.45f, Mth.lerp(k, 1.0f, -0.8f) * side, 0.1f * side);
		} else if (age < hits[1] + 2) {
			// 2: backhand -- from the far left back across, a little higher
			float k = smooth(Mth.clamp((age - (hits[1] - 2)) / 3f, 0f, 1f));
			pose(fist, w, -1.75f, Mth.lerp(k, -0.8f, 0.9f) * side, -0.15f * side);
		} else {
			// 3: the overhead smash -- up high, then driven down in front
			float k = smooth(Mth.clamp((age - (hits[2] - 2)) / 3f, 0f, 1f));
			pose(fist, w, Mth.lerp(k, -3.0f, -0.75f), -0.1f * side, 0f);
			pose(other, w, Mth.lerp(k, -2.6f, -0.6f), 0.1f * side, 0f);
			model.body.xRot = Mth.lerp(w, model.body.xRot, 0.35f * k);
		}
	}

	private static void crouchLegs(HumanoidModel<?> model, float w) {
		pose(model.rightLeg, w, -0.7f, 0.15f, 0.1f);
		pose(model.leftLeg, w, 0.35f, -0.1f, -0.1f);
	}

	/** entity id -> [blend 0..1, the game time (with partial) it was last eased at]. */
	private static final java.util.Map<Integer, float[]> FOURS = new java.util.HashMap<>();

	/**
	 * v0.16.7 (user): how far Cat Noir is into his all-fours run, 0..1, eased in and out (about a quarter second): Rooftop
	 * Run on, sprinting, moving, on (or just above) the ground, not climbing, swimming, flying, riding or mid-move.
	 * Read by the limb pose here and by {@code CatNoirAllFoursMixin}, which tips the whole body flat.
	 */
	public static float allFours(Player player, float partial) {
		if (player.level() == null) {
			return 0f;
		}
		boolean want = false;
		if (CatNoir.active(player) && CatNoir.rooftop(player) && player.isSprinting() && !player.isPassenger()
				&& !player.isSwimming() && !player.isFallFlying() && !player.getAbilities().flying && !CatNoir.climbing(player)) {
			MiraculousState s = Miraculous.peek(player);
			boolean moving = s == null || !(s.animId >= 40 && s.animId < 60
					&& player.level().getGameTime() - s.animStart < Math.max(1, length(s.animId)));
			double speed = Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
			boolean nearGround = player.onGround()
					|| !player.level().noCollision(player, player.getBoundingBox().move(0.0, -1.0, 0.0));
			want = moving && speed > 0.06 && nearGround;
		}
		float now = player.level().getGameTime() + partial;
		float[] st = FOURS.computeIfAbsent(player.getId(), k -> new float[] { 0f, now });
		float dt = Mth.clamp(now - st[1], 0f, 5f);
		st[1] = now;
		st[0] = Mth.clamp(st[0] + (want ? 0.2f : -0.25f) * dt, 0f, 1f);
		if (FOURS.size() > 256) {
			FOURS.clear();
		}
		return smooth(st[0]);
	}

	private static float smooth(float x) {
		return x * x * (3f - 2f * x);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
