package com.projecthero.mod.client.speedforce;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.client.moonknight.MoonKnightPose;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.data.SpeedForceState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.1 (user): the Speed Force Speedster's run -- Ezra Miller's Flash (Justice League / The Flash, 2023). It reads like
 * ice skating, not running: long gliding strides with each leg pushing out sideways-and-back in turn (toes turned out),
 * the body leaned well forward, the arms swinging wide and loose -- the arm on the pushing side flung back and out while
 * the other sweeps forward across the chest -- and the torso rolling and twisting over the support leg, side to side.
 * Only the Speed Force Speedster runs like this (the plain Speedster keeps {@code SpeedRunPose}), and it blends in with
 * the momentum: a plain sprint at a standstill, the full skate from about a third of the way up. The after-images keep
 * the pose they were left in ({@link #overrideWeight}).
 *
 * <p>Model space: +Y is down, -Z is forward; the torso leans / rolls / twists about the hip point, so the neck, head and
 * shoulders are moved to wherever that puts them while the hips (and the legs hung from them) stay put. Positive arm /
 * leg X swings the limb back; positive Z swings a right limb outward (a left limb inward).
 */
public final class SpeedForcePose {
	/** Torso lean forward about the hips at full blend, radians (~37 degrees). */
	static final float LEAN = 0.65f;
	/** Side-to-side roll over the support leg, and the shoulder twist with the arm sweep. */
	static final float ROLL = 0.28f;
	static final float TWIST = 0.30f;
	/** Stride cycles per vanilla walk cycle (vanilla's limb clock tops out at one unit a tick: ~10 ticks a stride). */
	static final float CADENCE = 0.95f;
	/** The pushing leg: back, out to the side and toes turned out; the recovering leg swings forward under the body. */
	static final float LEG_PUSH_BACK = 0.7f;
	static final float LEG_PUSH_OUT = 1.05f;
	static final float LEG_TOE_OUT = 0.45f;
	static final float LEG_RECOVER = 0.55f;
	/** The arm on the pushing side flung back and out; the other sweeping forward across the chest. */
	static final float ARM_BACK = 1.05f;
	static final float ARM_OUT = 0.8f;
	static final float ARM_ACROSS = 1.35f;
	static final float ARM_ACROSS_IN = 0.55f;
	/** The arms always hang a little wide (loose). */
	static final float ARM_WIDE = 0.25f;
	/** The body drops a little at each push (the support knee bending), model pixels. */
	static final float BOB = 0.6f;
	private static final float BLEND_PER_TICK = 0.2f;
	private static final double RUN_SPEED = 0.12;

	/** Per player: {previous weight, current weight}. */
	private static final Map<UUID, float[]> WEIGHTS = new HashMap<>();

	/** While an after-image is drawn: the weight it recorded (NaN = not drawing one). */
	public static float overrideWeight = Float.NaN;

	private SpeedForcePose() {
	}

	public static boolean running(Player p) {
		if (!SpeedForceSpeedster.speedActive(p) || p.isSpectator() || p.isCrouching() || p.isSwimming() || p.isFallFlying()
				|| p.isPassenger() || p.isUnderWater() || p.getAbilities().flying || SpeedForceSpeedster.phasing(p)) {
			return false;
		}
		double dx = p.getX() - p.xo;
		double dy = p.getY() - p.yo;
		double dz = p.getZ() - p.zo;
		return dx * dx + dy * dy + dz * dz > RUN_SPEED * RUN_SPEED;
	}

	/** How much of the skate the momentum is worth: 30% at a standstill, all of it from 35% momentum. */
	public static float momentumBlend(float momentum) {
		return Mth.clamp(0.3f + momentum * 2.0f, 0f, 1f);
	}

	public static void tick(Minecraft mc) {
		if (mc.level == null) {
			WEIGHTS.clear();
			return;
		}
		java.util.Set<UUID> present = new java.util.HashSet<>();
		for (AbstractClientPlayer p : mc.level.players()) {
			UUID id = p.getUUID();
			present.add(id);
			boolean run = running(p);
			float[] w = WEIGHTS.get(id);
			if (w == null) {
				if (!run) {
					continue;
				}
				w = new float[2];
				WEIGHTS.put(id, w);
			}
			w[0] = w[1];
			w[1] = Mth.clamp(w[1] + (run ? BLEND_PER_TICK : -BLEND_PER_TICK), 0f, 1f);
		}
		for (Iterator<Map.Entry<UUID, float[]>> it = WEIGHTS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, float[]> e = it.next();
			if (!present.contains(e.getKey()) || (e.getValue()[0] <= 0f && e.getValue()[1] <= 0f)) {
				it.remove();
			}
		}
	}

	/** The skate's blend for {@code p} at {@code partial}: running blend x momentum blend. */
	public static float weight(Player p, float partial) {
		float[] w = WEIGHTS.get(p.getUUID());
		float run = w == null ? 0f : Mth.lerp(partial, w[0], w[1]);
		return run * momentumBlend(SpeedForceSpeedster.momentum(p));
	}

	/** A move animation owns the body (it wins over the run). */
	private static boolean movePlaying(Player p) {
		SpeedForceState s = SpeedForceSpeedster.peek(p);
		if (s == null || s.animId == SpeedForceState.ANIM_NONE) {
			return false;
		}
		long since = p.level().getGameTime() - s.animStart;
		return s.animId == SpeedForceState.ANIM_TORNADO ? s.tornadoLeft > 0 : since >= 0 && since < moveLength(s.animId);
	}

	static int moveLength(int anim) {
		return switch (anim) {
			case SpeedForceState.ANIM_PUNCHES -> 10;
			case SpeedForceState.ANIM_MASS -> 14;
			case SpeedForceState.ANIM_THROW -> 16;
			case SpeedForceState.ANIM_STEP -> 8;
			case SpeedForceState.ANIM_HEAL -> 16;
			case SpeedForceState.ANIM_SURGE -> 18;
			case SpeedForceState.ANIM_AWAKEN -> 40;
			case SpeedForceState.ANIM_GRAB -> 8;
			default -> 0;
		};
	}

	/** Called at the tail of {@code HumanoidModel.setupAnim} (after the plain Speedster's run, before the move poses). */
	public static void apply(Player player, HumanoidModel<?> m, float limbSwing) {
		if (MoonKnightPose.firstPersonHand) {
			return;
		}
		float w;
		if (!Float.isNaN(overrideWeight)) {
			w = overrideWeight;
		} else {
			if (!SpeedForceSpeedster.hasPower(player)) {
				return;
			}
			if (SpeedForceSpeedster.chargeStart(player) != 0L) {
				chargeStance(player, m);
				return;
			}
			if (movePlaying(player)) {
				applyMove(player, m);
				return;
			}
			w = weight(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
			if (SpeedForceSpeedster.peek(player) != null && SpeedForceSpeedster.peek(player).carryId != 0) {
				skate(m, limbSwing, w);
				carryArms(m);
				return;
			}
		}
		skate(m, limbSwing, w);
	}

	/**
	 * v0.17.1 (user): hold Shift+C -- a sprinter's crouched starting stance, one foot back, leaning in, fists low, the
	 * body shaking harder as the 3 s charge fills.
	 */
	private static void chargeStance(Player player, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float age = player.level().getGameTime() - SpeedForceSpeedster.chargeStart(player) + partial;
		float fill = Mth.clamp(age / com.projecthero.mod.speedforce.SpeedForceConfig.CHARGE_TICKS, 0f, 1f);
		float shake = Mth.sin(age * 2.7f) * 0.03f * fill;
		m.body.xRot = 0.55f + shake;
		m.body.y = 3.2f;
		m.head.y = 4.2f;
		m.head.xRot = -0.45f;
		m.rightArm.y = 5.2f;
		m.leftArm.y = 5.2f;
		m.rightArm.xRot = 0.35f + shake;
		m.leftArm.xRot = -0.9f - shake;
		m.rightArm.zRot = 0.1f;
		m.leftArm.zRot = -0.1f;
		m.rightLeg.z = 4.0f;
		m.leftLeg.z = 4.0f;
		m.rightLeg.y = 12.2f;
		m.leftLeg.y = 12.2f;
		m.rightLeg.xRot = -0.95f;
		m.leftLeg.xRot = 0.55f;
		m.rightLeg.yRot = 0f;
		m.leftLeg.yRot = 0f;
	}

	/** The skating stride at blend {@code w} (0 = vanilla, 1 = the full skate). */
	static void skate(HumanoidModel<?> m, float limbSwing, float w) {
		if (w <= 0.001f) {
			return;
		}
		w = w * w * (3f - 2f * w);
		float phase = limbSwing * 0.6662f * CADENCE;
		float s = Mth.sin(phase);
		// a little hang at each end of the push: the glide
		float glide = Math.signum(s) * (float) Math.pow(Math.abs(s), 0.7);
		float pushR = Math.max(0f, glide);
		float pushL = Math.max(0f, -glide);

		float lean = LEAN * w;
		float roll = ROLL * glide * w;
		float twist = TWIST * glide * w;
		float bob = BOB * Math.abs(glide) * w;

		// the torso pivots about the hip (0, 12, 0): R = Rz(roll) Ry(twist) Rx(lean); the body part's own origin (the
		// neck) goes wherever that puts it, so the hip stays where it was
		// (body-local (0, 12, 0) is the hip, so bodyPos = hip - R*(0, 12, 0))
		float[] hipFromNeck = rotate(0f, 12f, 0f, lean, twist, roll);
		float bx = -hipFromNeck[0];
		float by = 12f - hipFromNeck[1] + bob;
		float bz = -hipFromNeck[2];
		m.body.x = bx;
		m.body.y = by;
		m.body.z = bz;
		m.body.xRot = lean;
		m.body.yRot = twist;
		m.body.zRot = roll;
		m.head.x = bx;
		m.head.y = by;
		m.head.z = bz;
		m.head.zRot = -roll * 0.5f;
		m.hat.copyFrom(m.head);

		// shoulders: body-local (-5, 2, 0) / (5, 2, 0)
		float[] rs = rotate(-5f, 2f, 0f, lean, twist, roll);
		float[] ls = rotate(5f, 2f, 0f, lean, twist, roll);
		m.rightArm.x = bx + rs[0];
		m.rightArm.y = by + rs[1];
		m.rightArm.z = bz + rs[2];
		m.leftArm.x = bx + ls[0];
		m.leftArm.y = by + ls[1];
		m.leftArm.z = bz + ls[2];
		// arms, loose and wide: the pushing side's arm flung back and out, the other sweeping forward across the chest
		float rX = ARM_BACK * pushR - ARM_ACROSS * pushL;
		float rY = -ARM_ACROSS_IN * pushL;
		float rZ = ARM_WIDE + ARM_OUT * pushR - 0.2f * pushL;
		float lX = ARM_BACK * pushL - ARM_ACROSS * pushR;
		float lY = ARM_ACROSS_IN * pushR;
		float lZ = -ARM_WIDE - ARM_OUT * pushL + 0.2f * pushR;
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, rX);
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, rY);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, rZ);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, lX);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, lY);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, lZ);

		// legs from the (unmoved, bobbing) hips: the pushing leg back, out and toes turned out; the other under the body
		m.rightLeg.x = -1.9f;
		m.leftLeg.x = 1.9f;
		m.rightLeg.y = 12f + bob;
		m.leftLeg.y = 12f + bob;
		m.rightLeg.z = 0f;
		m.leftLeg.z = 0f;
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, LEG_PUSH_BACK * pushR - LEG_RECOVER * pushL);
		m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, LEG_TOE_OUT * pushR);
		m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, LEG_PUSH_OUT * pushR - 0.12f * pushL);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, LEG_PUSH_BACK * pushL - LEG_RECOVER * pushR);
		m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, -LEG_TOE_OUT * pushL);
		m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, -LEG_PUSH_OUT * pushL + 0.12f * pushR);
	}

	/** {@code R = Rz(z) Ry(y) Rx(x)} applied to (px, py, pz) -- the order {@code ModelPart} rotates in. */
	static float[] rotate(float px, float py, float pz, float x, float y, float z) {
		float cx = Mth.cos(x), sx = Mth.sin(x);
		float cy = Mth.cos(y), sy = Mth.sin(y);
		float cz = Mth.cos(z), sz = Mth.sin(z);
		// Rx
		float y1 = py * cx - pz * sx;
		float z1 = py * sx + pz * cx;
		float x1 = px;
		// Ry
		float x2 = x1 * cy + z1 * sy;
		float z2 = -x1 * sy + z1 * cy;
		float y2 = y1;
		// Rz
		float x3 = x2 * cz - y2 * sz;
		float y3 = x2 * sz + y2 * cz;
		return new float[] { x3, y3, z2 };
	}

	/** Rescue Run: the arms up, holding what rides on your shoulders. */
	private static void carryArms(HumanoidModel<?> m) {
		m.rightArm.xRot = -2.7f;
		m.leftArm.xRot = -2.7f;
		m.rightArm.yRot = 0f;
		m.leftArm.yRot = 0f;
		m.rightArm.zRot = 0.25f;
		m.leftArm.zRot = -0.25f;
	}

	/** The move poses: short keyed shapes over the move's first ticks. */
	private static void applyMove(Player player, HumanoidModel<?> m) {
		SpeedForceState s = SpeedForceSpeedster.peek(player);
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = player.level().getGameTime() - s.animStart + partial;
		switch (s.animId) {
			case SpeedForceState.ANIM_PUNCHES -> {
				// alternating straights, every 2 ticks
				boolean right = ((int) (t / 2f)) % 2 == 0;
				m.rightArm.xRot = right ? -1.6f : 0.4f;
				m.leftArm.xRot = right ? 0.4f : -1.6f;
				m.rightArm.yRot = right ? -0.1f : 0.2f;
				m.leftArm.yRot = right ? -0.2f : 0.1f;
				m.body.yRot = right ? -0.35f : 0.35f;
			}
			case SpeedForceState.ANIM_MASS -> {
				float k = Mth.clamp((t - 3f) / 2f, 0f, 1f);
				m.rightArm.xRot = Mth.lerp(k, 0.9f, -1.75f);
				m.rightArm.yRot = Mth.lerp(k, 0.4f, -0.05f);
				m.leftArm.xRot = Mth.lerp(k, -0.8f, 0.7f);
				m.body.yRot = Mth.lerp(k, 0.6f, -0.65f);
				m.rightLeg.xRot = Mth.lerp(k, -0.3f, 0.6f);
				m.leftLeg.xRot = Mth.lerp(k, 0.3f, -0.6f);
			}
			case SpeedForceState.ANIM_THROW -> {
				float k = Mth.clamp((t - 7f) / 3f, 0f, 1f);
				m.rightArm.xRot = Mth.lerp(k, -2.9f, -1.3f);
				m.rightArm.zRot = Mth.lerp(k, 0.3f, 0.0f);
				m.leftArm.xRot = Mth.lerp(k, -0.6f, 0.5f);
				m.body.yRot = Mth.lerp(k, 0.4f, -0.4f);
			}
			case SpeedForceState.ANIM_TORNADO -> {
				float a = Mth.sin(t * 1.4f);
				m.rightArm.xRot = 1.0f;
				m.leftArm.xRot = 1.0f;
				m.rightArm.zRot = 0.3f;
				m.leftArm.zRot = -0.3f;
				m.rightLeg.xRot = a;
				m.leftLeg.xRot = -a;
				m.body.zRot = 0.25f;
			}
			case SpeedForceState.ANIM_STEP -> {
				m.rightArm.xRot = 1.2f;
				m.leftArm.xRot = 1.2f;
				m.rightLeg.xRot = 0.7f;
				m.leftLeg.xRot = -0.6f;
			}
			case SpeedForceState.ANIM_HEAL -> {
				m.rightArm.xRot = -1.2f;
				m.leftArm.xRot = -1.2f;
				m.rightArm.yRot = -0.6f;
				m.leftArm.yRot = 0.6f;
			}
			case SpeedForceState.ANIM_SURGE, SpeedForceState.ANIM_AWAKEN -> {
				float k = Mth.clamp(t / 4f, 0f, 1f);
				m.rightArm.zRot = Mth.lerp(k, 0f, 1.2f);
				m.leftArm.zRot = Mth.lerp(k, 0f, -1.2f);
				m.rightArm.xRot = -0.3f * k;
				m.leftArm.xRot = -0.3f * k;
				m.head.xRot = -0.5f * k;
				m.hat.copyFrom(m.head);
			}
			case SpeedForceState.ANIM_GRAB -> carryArms(m);
			default -> {
			}
		}
	}
}
