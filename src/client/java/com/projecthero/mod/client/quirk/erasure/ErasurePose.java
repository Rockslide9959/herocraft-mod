package com.projecthero.mod.client.quirk.erasure;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.fc;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.restC;

import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.erasure.ErasureConfig;
import com.projecthero.mod.quirk.erasure.ErasureMoves;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 revamp: Eraser Head's poses on the vanilla player model (from {@code QuirkClient.applyPose}, the END of
 * setupAnim), driven by the synced state so every viewer sees the same; keyframed like the One For All poses.
 *
 * <p>Two layers: <b>held poses</b> read straight off the running state (the stare's hunch, the arm up the scarf line,
 * both hands hauling a bound creature, the Bind and Spin wind-up, the eyes-shut rub), then the <b>move keyframes</b>
 * (v0.17.1: the shared {@link QuirkKeyframes} 16 columns -- full leg rotation -- plus the 17th {@code crouch} column,
 * smoothstepped, faded in / out). Bind and Spin and the Close Combat finisher also turn the whole body ({@link #spinDegrees},
 * {@code ErasureSpinMixin}). The scarf is always thrown from the RIGHT hand; negative arm X swings forward / up.
 */
public final class ErasurePose {
	// Shift+R Capture Pull: whip out, then a two-handed yank back
	private static final float[][] PULL = {
			restC(0),
			fc(2, -2.6f, 0.4f, -0.2f, -0.3f, 0, -0.3f, -0.05f, 0.3f, 0.2f, 0, 0, -0.2f, 0, 0, 0, 0),
			fc(4, -1.55f, -0.05f, 0, -1.3f, 0.3f, 0, 0.12f, -0.2f, -0.3f, 0, 0, 0.3f, 0, 0, 0, 0.1f),
			fc(7, 0.45f, -0.1f, 0.15f, 0.25f, 0.2f, -0.15f, -0.2f, 0.25f, 0.35f, 0, 0, -0.35f, 0, 0, -0.1f, 0.25f),
			fc(10, 0.4f, -0.1f, 0.15f, 0.2f, 0.2f, -0.15f, -0.18f, 0.25f, 0.3f, 0, 0, -0.3f, 0, 0, -0.1f, 0.2f),
			restC(14) };
	// R Capture Bind: the scarf circled overhead, thrown, then both hands tug the wraps tight
	private static final float[][] BIND = {
			restC(0),
			fc(3, -2.8f, 0.7f, -0.25f, -0.4f, 0, -0.35f, -0.08f, 0.35f, 0.15f, 0, 0, -0.15f, 0, 0, -0.1f, 0),
			fc(5, -1.6f, -0.1f, 0, -1.25f, 0.35f, 0, 0.15f, -0.25f, -0.35f, 0, 0, 0.35f, 0, 0, 0, 0.15f),
			fc(9, -1.0f, -0.25f, 0, -1.05f, 0.45f, 0, 0.05f, 0, -0.15f, 0, 0, 0.15f, 0, 0, 0, 0.1f),
			fc(11, -0.75f, -0.25f, 0, -0.8f, 0.45f, 0, -0.08f, 0, 0.1f, 0, 0, -0.1f, 0, 0, 0, 0.1f),
			restC(16) };
	// G Close Combat: jab, elbow, knee, then the spinning kick (the body turn is spinDegrees)
	private static final float[][] COMBO = {
			restC(0),
			fc(1, 0.3f, 0, 0, -0.9f, 0.3f, 0, 0.05f, 0.35f, 0.2f, 0, 0, -0.2f, 0, 0, 0, 0.2f),
			fc(2, -1.62f, -0.1f, 0, 0.35f, 0.2f, -0.1f, 0.15f, -0.45f, -0.35f, 0, 0, 0.3f, 0, 0, 0, 0.2f),
			fc(4, -0.9f, -0.3f, 0, -0.6f, 0.4f, 0, 0.1f, -0.1f, -0.2f, 0, 0, 0.2f, 0, 0, 0, 0.2f),
			fc(6, -0.2f, 0, 0.2f, -1.45f, -0.95f, -0.35f, 0.15f, 0.55f, 0.2f, 0, 0, -0.35f, 0, 0, 0, 0.2f),
			fc(8, -0.6f, -0.3f, 0, -0.9f, 0.2f, 0, 0.1f, 0.1f, 0, 0, 0, 0, 0, 0, 0, 0.1f),
			fc(10, -1.1f, -0.2f, 0.2f, -1.1f, 0.2f, -0.2f, 0.35f, 0, -1.55f, 0, 0, 0.25f, 0, 0, 0.2f, 0),
			fc(12, -0.5f, 0, 0.5f, -0.5f, 0, -0.5f, 0.1f, 0, -0.3f, 0, 0, 0.2f, 0, 0, 0, 0.15f),
			fc(15, -0.2f, 0, 1.15f, -0.2f, 0, -1.15f, -0.2f, 0, -1.5f, 0, 0, 0.35f, 0, 0, -0.1f, 0),
			fc(17, -0.2f, 0, 1.15f, -0.2f, 0, -1.15f, -0.2f, 0, -1.45f, 0, 0, 0.35f, 0, 0, -0.1f, 0),
			restC(22) };
	// Shift+G Counter: forearms crossed in a low guard, weight back, held through the window
	private static final float[][] COUNTER = {
			restC(0),
			fc(2, -1.35f, -0.65f, 0, -1.35f, 0.65f, 0, 0.18f, 0, -0.35f, 0, 0, 0.25f, 0, 0, 0.15f, 0.45f),
			fc(12, -1.35f, -0.65f, 0, -1.35f, 0.65f, 0, 0.18f, 0, -0.35f, 0, 0, 0.25f, 0, 0, 0.15f, 0.45f),
			restC(15) };
	// the riposte: guard opens into a palm strike, the scarf snapping out with the other hand
	private static final float[][] COUNTER_HIT = {
			fc(0, -1.35f, -0.65f, 0, -1.35f, 0.65f, 0, 0.18f, 0, -0.35f, 0, 0, 0.25f, 0, 0, 0.15f, 0.45f),
			fc(3, -1.65f, -0.1f, 0, 0.5f, 0.2f, -0.4f, 0.2f, -0.55f, -0.5f, 0, 0, 0.4f, 0, 0, 0, 0.2f),
			fc(8, -1.6f, -0.1f, 0, 0.5f, 0.2f, -0.4f, 0.18f, -0.5f, -0.45f, 0, 0, 0.4f, 0, 0, 0, 0.2f),
			restC(14) };
	// C Scarf Whip: overhead, then a snapping crack down along the look
	private static final float[][] WHIP = {
			restC(0),
			fc(2, -2.95f, 0.3f, -0.15f, -0.3f, 0, -0.25f, -0.1f, 0.3f, 0.15f, 0, 0, -0.15f, 0, 0, -0.1f, 0),
			fc(4, -1.25f, -0.25f, 0, -0.2f, 0, -0.25f, 0.2f, -0.35f, -0.3f, 0, 0, 0.3f, 0, 0, 0.05f, 0.1f),
			fc(6, -1.1f, -0.3f, 0, -0.2f, 0, -0.25f, 0.18f, -0.35f, -0.3f, 0, 0, 0.3f, 0, 0, 0.05f, 0.1f),
			restC(11) };
	// Shift+Z Disarm Throw: whip at the hand, then rip it back over the shoulder
	private static final float[][] DISARM = {
			restC(0),
			fc(3, -1.6f, -0.05f, 0, -0.3f, 0, -0.25f, 0.12f, -0.2f, -0.2f, 0, 0, 0.2f, 0, 0, 0, 0),
			fc(7, -2.4f, 0.5f, 0.2f, -0.3f, 0, -0.25f, -0.15f, 0.45f, 0.25f, 0, 0, -0.25f, 0, 0, -0.15f, 0),
			fc(9, -2.3f, 0.5f, 0.2f, -0.3f, 0, -0.25f, -0.12f, 0.4f, 0.2f, 0, 0, -0.2f, 0, 0, -0.15f, 0),
			restC(14) };
	// Shift+C Goggles: both hands up to the goggles
	private static final float[][] GOGGLES = {
			restC(0),
			fc(3, -2.55f, -0.5f, 0, -2.55f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.1f, 0),
			fc(6, -2.4f, -0.5f, 0, -2.4f, 0.5f, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.15f, 0),
			restC(10) };
	// Shift+V Eye Drops: head back, the bottle held over one eye, then the other
	private static final float[][] DROPS = {
			restC(0),
			fc(4, -2.8f, -0.45f, 0, -0.5f, 0, -0.2f, -0.15f, 0, 0, 0, 0, 0, 0, 0, -0.7f, 0),
			fc(9, -2.8f, -0.45f, 0, -0.5f, 0, -0.2f, -0.15f, 0, 0, 0, 0, 0, 0, 0, -0.7f, 0),
			fc(12, -2.8f, -0.15f, 0, -0.5f, 0, -0.2f, -0.15f, 0, 0, 0, 0, 0, 0, 0, -0.7f, 0),
			fc(17, -2.8f, -0.15f, 0, -0.5f, 0, -0.2f, -0.15f, 0, 0, 0, 0, 0, 0, 0, -0.7f, 0),
			restC(22) };
	// Shift+X Scarf Pull: both hands snap up the line as he is reeled in (the held line pose takes over)
	private static final float[][] ZIP = {
			restC(0),
			fc(3, -2.6f, -0.2f, 0, -2.4f, 0.2f, 0, -0.1f, 0, -0.5f, 0, 0, 0.2f, 0, 0, -0.2f, 0),
			restC(8) };

	private ErasurePose() {
	}

	static float[][] frames(int animId) {
		return switch (animId) {
			case ErasureConfig.ANIM_PULL -> PULL;
			case ErasureConfig.ANIM_BIND -> BIND;
			case ErasureConfig.ANIM_COMBO -> COMBO;
			case ErasureConfig.ANIM_COUNTER -> COUNTER;
			case ErasureConfig.ANIM_COUNTER_HIT -> COUNTER_HIT;
			case ErasureConfig.ANIM_WHIP -> WHIP;
			case ErasureConfig.ANIM_DISARM -> DISARM;
			case ErasureConfig.ANIM_GOGGLES -> GOGGLES;
			case ErasureConfig.ANIM_DROPS -> DROPS;
			case ErasureConfig.ANIM_ZIP -> ZIP;
			default -> null;
		};
	}

	static float[] sample(float[][] f, float tick) {
		return QuirkKeyframes.sample(f, tick);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	/** An arm pointed from the shoulder at a world point. */
	private static void pointAt(Player p, ModelPart arm, Vec3 target, float partial, float w) {
		Vec3 shoulder = p.getPosition(partial).add(0, p.getBbHeight() * 0.78, 0);
		Vec3 d = target.subtract(shoulder);
		double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
		float pitch = (float) Math.atan2(d.y, horiz);
		float dirYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
		float body = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float rel = Mth.wrapDegrees(dirYaw - body) * Mth.DEG_TO_RAD;
		pose(arm, w, -Mth.HALF_PI - pitch, rel, 0f);
	}

	private static Entity entity(Player p, QuirkState s, String key) {
		long v = s.val(key);
		return v == 0L ? null : p.level().getEntity((int) (v - 1L));
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	public static void apply(Player p, HumanoidModel<?> m) {
		QuirkState s = Quirks.peek(p);
		if (s == null || p.level() == null) {
			return;
		}
		float partial = partial();
		long now = p.level().getGameTime();
		float t = now + partial;
		ModelPart arm = m.rightArm, other = m.leftArm;

		// ---- the stare: hunched forward, head locked on, both hands holding the scarf ready at the chest
		if (s.val(ErasureConfig.V_ERASE) != 0L) {
			float w = Mth.clamp((now - s.val(ErasureConfig.V_ERASE_AT) + partial) / 4f, 0f, 1f);
			pose(arm, w, -0.95f, -0.5f, 0.05f);
			pose(other, w, -0.7f, 0.45f, -0.05f);
			com.projecthero.mod.client.HipBend.pitch(m, w, 0.14f);
		}
		// ---- eyes forced shut: the head drops and a hand rubs the eyes
		if (s.val(ErasureConfig.V_SHUT) != 0L) {
			float age = now - s.val(ErasureConfig.V_SHUT_AT) + partial;
			float w = Mth.clamp(age / 4f, 0f, 1f);
			pose(arm, w, -2.15f + 0.08f * Mth.sin(t * 0.6f), -0.65f, 0f);
			m.head.xRot = Mth.lerp(w, m.head.xRot, 0.45f);
		}
		// ---- the line
		Vec3 hook = Quirks.vec(s, ErasureConfig.P_HOOK);
		long line = s.val(ErasureConfig.V_LINE);
		if (line != 0L) {
			Entity z = line == 2L ? entity(p, s, ErasureConfig.V_ZIP_TARGET) : null;
			Vec3 to = z != null ? z.getPosition(partial).add(0, z.getBbHeight() * 0.5, 0) : hook;
			if (to != null) {
				pointAt(p, arm, to, partial, 1f);
				if (line != 1L) {
					// reeled in: both hands on the line, legs tucked for the landing
					pose(other, 1f, arm.xRot + 0.2f, arm.yRot - 0.25f, 0f);
					pose(m.rightLeg, 1f, -0.7f, 0f, 0.05f);
					pose(m.leftLeg, 1f, -0.35f, 0f, -0.05f);
				} else {
					// the swing: the free arm out for balance, legs trailing
					pose(other, 1f, -0.4f + 0.2f * Mth.sin(t * 0.2f), 0f, -0.55f);
					pose(m.rightLeg, 0.6f, 0.35f, 0f, 0f);
					pose(m.leftLeg, 0.6f, -0.45f, 0f, 0f);
				}
			}
		}
		// ---- holding a bound creature: both hands on the scarf, hauling
		Entity held = s.val(ErasureConfig.V_BIND_UNTIL) > now ? entity(p, s, ErasureConfig.V_BIND) : null;
		if (held != null && line == 0L) {
			Vec3 at = held.getPosition(partial).add(0, held.getBbHeight() * 0.6, 0);
			pointAt(p, arm, at, partial, 1f);
			pose(other, 1f, arm.xRot + 0.35f, arm.yRot + 0.35f, 0f);
		}
		// ---- Bind and Spin: leaning back against the weight, both hands out on the scarf (the body turns with it)
		if (s.val(ErasureConfig.V_SPIN) != 0L) {
			float age = now - s.val(ErasureConfig.V_SPIN_AT) + partial;
			float w = Mth.clamp(age / 3f, 0f, 1f);
			pose(arm, w, -1.45f, -0.15f, 0.25f);
			pose(other, w, -1.3f, 0.35f, -0.1f);
			com.projecthero.mod.client.HipBend.pitch(m, w, -0.22f);
			pose(m.rightLeg, w, -0.35f, 0f, 0.1f);
			pose(m.leftLeg, w, 0.35f, 0f, -0.1f);
		}

		// ---- the moves' keyframes
		float[][] f = s.animId >= 100 && s.animId <= 119 ? frames(s.animId) : null;
		if (f != null) {
			float tick = now - s.animStart + partial;
			float[] v = sample(f, tick);
			if (v != null) {
				float end = f[f.length - 1][0];
				float w = Math.max(0f, Math.min(1f, Math.min(tick / 1.5f + 0.25f, (end - tick) / 3f)));
				pose(arm, w, v[1], v[2], v[3]);
				pose(other, w, v[4], v[5], v[6]);
				com.projecthero.mod.client.HipBend.pitch(m, w, v[7]);
				m.body.yRot = Mth.lerp(w, m.body.yRot, v[8]);
				m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, v[QuirkKeyframes.R_LEG_X]);
				m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, v[QuirkKeyframes.L_LEG_X]);
				QuirkKeyframes.legTwist(m, v, w);
				m.head.xRot = Mth.lerp(w, m.head.xRot, m.head.xRot + v[QuirkKeyframes.HEAD_X]);
				QuirkKeyframes.crouch(m, w * v[QuirkKeyframes.CROUCH]);
			}
		}
		m.hat.copyFrom(m.head);
	}

	private static float eased(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}

	/**
	 * Degrees to turn the whole body about its vertical axis ({@code ErasureSpinMixin}): Bind and Spin turns him with the
	 * creature on his scarf (two turns, matching the server's orbit); the Close Combat finisher is one spinning kick.
	 */
	public static float spinDegrees(Player p, float partial) {
		if (!Quirks.holds(p, Quirk.ERASURE)) {
			return 0f;
		}
		QuirkState s = Quirks.peek(p);
		if (s == null) {
			return 0f;
		}
		long now = p.level().getGameTime();
		if (s.val(ErasureConfig.V_SPIN) != 0L) {
			float age = now - s.val(ErasureConfig.V_SPIN_AT) + partial;
			return (float) -Math.toDegrees(ErasureMoves.spinAngle(age));
		}
		if (s.animId == ErasureConfig.ANIM_COMBO) {
			float age = now - s.animStart + partial;
			float k = (age - 12f) / 5f;
			return k <= 0f || k >= 1f ? 0f : -360f * eased(k);
		}
		return 0f;
	}
}
