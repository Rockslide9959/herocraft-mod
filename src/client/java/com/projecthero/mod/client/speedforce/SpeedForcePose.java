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
 * v0.17.1: the Speed Force Speedster's body poses. v0.17.1 (user, round 3): the run is the plain Speedster's sprint
 * ({@code SpeedRunPose.pose}, the ice-skate stride removed), kicking in from 10% momentum (an ordinary run below that) and
 * driven harder as the momentum climbs; plus the move poses, the hold-C charge stance and the carry. The after-images keep
 * the pose they were left in ({@link #overrideWeight}).
 */
public final class SpeedForcePose {
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

	/**
	 * v0.17.1 (user: "remove the ice skating animation ... make it kick in at 25% momentum, before that player moves like
	 * normal"; later "make the running animation happen when the player reaches 10% momentum"): how much of the speedster
	 * sprint the momentum is worth -- none below 10%, all of it by 20%.
	 */
	public static float momentumBlend(float momentum) {
		return Mth.clamp((momentum - 0.10f) / 0.10f, 0f, 1f);
	}

	/** The plain Speedster's sprint ({@code SpeedRunPose.pose}), run harder the more momentum there is. */
	private static void sprint(Player player, HumanoidModel<?> m, float limbSwing, float w) {
		float drive = 0.45f + 0.55f * Mth.clamp((SpeedForceSpeedster.momentum(player) - 0.10f) / 0.90f, 0f, 1f);
		com.projecthero.mod.client.mutation.v0145.SpeedRunPose.pose(m, limbSwing, w, drive);
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
		if (s.holdAnim) {
			return true; // a hold move's pose lasts while the key is held
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
			case SpeedForceState.ANIM_BLITZ -> 10;
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
				sprint(player, m, limbSwing, w);
				carryArms(m);
				return;
			}
		}
		sprint(player, m, limbSwing, w);
	}

	/** v0.17.7: ticks to ease from wherever the body was into the set position. */
	static final float CHARGE_EASE_TICKS = 6f;
	/** v0.17.7: the torso tipped ~104 degrees (past flat), so the hips ride above the shoulders. */
	static final float SET_BODY_PITCH = 1.82f;
	/** v0.17.7: shoulder pivot height (model pixels, Y down, the floor at 24) and how far ahead of the feet's centre it sits. */
	static final float SET_SHOULDER_Y = 14.8f;
	static final float SET_SHOULDER_Z = -6.0f;
	/** v0.17.7: the rear foot rests this many pixels up, on the back block (which sends the rear leg further back). */
	static final float SET_REAR_FOOT_LIFT = 3.0f;

	/**
	 * v0.17.7 (user: "make the pose match this pose", a sprinter in the blocks at "set"): both hands flat on the ground just ahead
	 * of the shoulders, arms straight; the torso tipped forward past flat so the hips ride above the shoulders; the front leg
	 * forward under the chest, the rear leg reaching back nearly straight onto the back block; the head down, eyes on the track
	 * just ahead. Eases in over {@link #CHARGE_EASE_TICKS}; a tremble builds with the 3 s charge while the hands stay planted.
	 * The front leg is the off-hand side (left for a right-handed runner), mirrored for left-handers.
	 *
	 * <p>Worked in model space (pixels, Y down, front -Z, floor at y 24): the shoulder is placed, then the neck and hip follow from
	 * the torso's pitch, and each limb is aimed so its end lands on the floor (an arm reaches 10 px below its pivot, a leg 12).
	 */
	private static void chargeStance(Player player, HumanoidModel<?> m) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float age = Math.max(0f, player.level().getGameTime() - SpeedForceSpeedster.chargeStart(player) + partial);
		float fill = Mth.clamp(age / com.projecthero.mod.speedforce.SpeedForceConfig.CHARGE_TICKS, 0f, 1f);
		float e = Mth.clamp(age / CHARGE_EASE_TICKS, 0f, 1f);
		float k = e * e * (3f - 2f * e);
		// the tremble: two shakes beating against each other, growing with the charge
		float tr = fill * fill;
		float shakeY = (Mth.sin(age * 3.1f) * 0.16f + Mth.sin(age * 7.7f) * 0.07f) * tr;
		float shakeP = Mth.sin(age * 4.3f) * 0.03f * tr;
		float shakeZ = Mth.sin(age * 5.9f) * 0.02f * tr;

		float pitch = SET_BODY_PITCH + shakeP;
		float cb = Mth.cos(pitch), sb = Mth.sin(pitch);
		float shoulderY = SET_SHOULDER_Y + shakeY;
		float shoulderZ = SET_SHOULDER_Z;
		// the body's pivot is the neck, the shoulder 2 px down the torso, the hips 12 px down it
		float neckY = shoulderY - 2f * cb;
		float neckZ = shoulderZ - 2f * sb;
		float hipY = neckY + 12f * cb;
		float hipZ = neckZ + 12f * sb;
		// arms straight, hands on the floor just ahead of the shoulders
		float arm = -(float) Math.acos(Mth.clamp((24f - shoulderY) / 10f, -1f, 1f));
		// the front leg forward onto the floor under the chest; the rear one back onto the raised back block
		float front = -(float) Math.acos(Mth.clamp((24f - hipY) / 12f, -1f, 1f));
		float rear = (float) Math.acos(Mth.clamp((24f - SET_REAR_FOOT_LIFT - hipY) / 12f, -1f, 1f));
		boolean rightHanded = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;

		ease(m.body, k, 0f, neckY, neckZ, pitch, 0f, shakeZ);
		ease(m.head, k, 0f, neckY, neckZ, 1.5f + shakeP, 0f, shakeZ);
		m.hat.copyFrom(m.head);
		ease(m.rightArm, k, -5f, shoulderY, shoulderZ, arm, 0f, 0.06f);
		ease(m.leftArm, k, 5f, shoulderY, shoulderZ, arm, 0f, -0.06f);
		ease(m.rightLeg, k, -1.9f, hipY, hipZ, rightHanded ? rear : front, 0f, rightHanded ? 0.04f : 0f);
		ease(m.leftLeg, k, 1.9f, hipY, hipZ, rightHanded ? front : rear, 0f, rightHanded ? 0f : -0.04f);
	}

	/** Moves {@code part} {@code k} of the way from its current pose to the given one. */
	private static void ease(net.minecraft.client.model.geom.ModelPart part, float k, float x, float y, float z, float xRot, float yRot, float zRot) {
		part.x = Mth.lerp(k, part.x, x);
		part.y = Mth.lerp(k, part.y, y);
		part.z = Mth.lerp(k, part.z, z);
		part.xRot = Mth.lerp(k, part.xRot, xRot);
		part.yRot = Mth.lerp(k, part.yRot, yRot);
		part.zRot = Mth.lerp(k, part.zRot, zRot);
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

	/** Hold R: the fist cocked right back behind the shoulder, the body coiled, shaking a little the longer it is held. */
	private static void massWindup(Player player, HumanoidModel<?> m, float partial) {
		float a = player.tickCount + partial;
		float shake = Mth.sin(a * 2.9f) * 0.03f;
		m.rightArm.xRot = 0.95f + shake;
		m.rightArm.yRot = 0.55f;
		m.rightArm.zRot = 0.35f;
		m.leftArm.xRot = -1.1f;
		m.leftArm.yRot = 0.35f;
		m.body.yRot = 0.55f + shake;
		m.rightLeg.xRot = 0.35f;
		m.leftLeg.xRot = -0.45f;
	}

	/** Hold G: the right hand raised high and back, the lightning gathering in it, the left arm out for aim. */
	private static void throwCharge(Player player, HumanoidModel<?> m, float partial) {
		float a = player.tickCount + partial;
		m.rightArm.xRot = -2.85f + Mth.sin(a * 1.7f) * 0.04f;
		m.rightArm.zRot = 0.35f;
		m.leftArm.xRot = -1.35f;
		m.leftArm.yRot = 0.25f;
		m.body.yRot = 0.4f;
	}

	/**
	 * v0.17.1 (user) hold X Tornado Arms: both arms out in front, windmilling round the shoulders so fast they blur (half a
	 * turn apart), the body leaning into the wind it throws.
	 */
	private static void tornadoArms(Player player, HumanoidModel<?> m, float partial) {
		float a = (player.tickCount + partial) * 1.9f;
		m.rightArm.xRot = -1.57f + Mth.cos(a) * 1.15f;
		m.rightArm.zRot = Mth.sin(a) * 0.55f - 0.1f;
		m.leftArm.xRot = -1.57f + Mth.cos(a + (float) Math.PI) * 1.15f;
		m.leftArm.zRot = Mth.sin(a + (float) Math.PI) * 0.55f + 0.1f;
		m.rightArm.yRot = 0f;
		m.leftArm.yRot = 0f;
		m.body.xRot = 0.18f;
		m.rightLeg.xRot = -0.25f;
		m.leftLeg.xRot = 0.35f;
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
			case SpeedForceState.ANIM_BLITZ -> {
				// one big straight right, the body turned into it
				float k = Mth.clamp(t / 2f, 0f, 1f);
				m.rightArm.xRot = Mth.lerp(k, 0.6f, -1.65f);
				m.rightArm.yRot = -0.1f;
				m.leftArm.xRot = 0.6f;
				m.body.yRot = Mth.lerp(k, 0.4f, -0.45f);
			}
			case SpeedForceState.ANIM_MASS_WINDUP -> massWindup(player, m, partial);
			case SpeedForceState.ANIM_THROW_CHARGE -> throwCharge(player, m, partial);
			case SpeedForceState.ANIM_ARMS -> tornadoArms(player, m, partial);
			default -> {
			}
		}
	}
}
