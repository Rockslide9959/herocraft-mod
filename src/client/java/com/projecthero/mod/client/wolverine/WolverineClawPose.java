package com.projecthero.mod.client.wolverine;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.wolverine.WolverineBlock;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.WolverineState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Wolverine's body language (v0.16.1), third person ({@link #apply}) and first person ({@link #firstPerson}), all keyed
 * off synced state so every viewer sees the same thing. Over vanilla's own pose, lowest first:
 * <ol>
 * <li><b>Claw deploy</b> ({@code clawsChangedAt}, H only -- see {@link WolverineClawsModel#manualDeploy}): fists to the
 * chest, then the arms flung out to the sides, chest out and head back, the blades popping out at full extension.
 * Retracting has no arm motion: the blades just slide back in.</li>
 * <li><b>Moves</b>: the left-click combo ({@link WolverineClawCombo}) -- a rising diagonal slash with the right claws,
 * a horizontal slash with the left, then a finisher with both arms raised high outside the shoulders raking down
 * into an X across the chest -- and the ability moves (R sweep, G slashes, V fast slashes, Z charge + thrust, C roar).
 * Each strike is key-framed in ticks: a short anticipation (arm cocked, torso wound the other way), a snap through the
 * arc with a strong ease-out, follow-through, recovery. The wrists turn so the blade edges lead the cut, the off arm
 * guards or counter-swings, the torso twists and bends from the hips and the legs take a small step.</li>
 * </ol>
 * Arms are posed as aim directions + edge directions in torso space and converted to the model's Euler angles, so
 * the blades always cut edge-first. The torso twist/lean keeps the hips planted (the neck moves instead, like
 * {@code HipBend}, generalised to a twisted torso) and carries the shoulders and head with it. While a claw move plays
 * vanilla's own punch swing is switched off ({@code HumanoidModelMixin} zeroes {@code attackTime}) so the move fully
 * owns the arms.
 */
public final class WolverineClawPose {
	public enum Kind { NONE, DIAG, HORIZ, CROSS, THRUST, CHARGE, ROAR }

	/** A move playing now: which, the arm that leads it, its age in game ticks and its playback speed. */
	public record Move(Kind kind, HumanoidArm arm, float age, float speed) {
		static final Move NONE = new Move(Kind.NONE, HumanoidArm.RIGHT, 0.0f, 1.0f);

		/** Time into the move's clip, in clip ticks. */
		public float clipTime() {
			return age * speed;
		}

		/** 0..1 through the move. */
		public float t() {
			return kind == Kind.NONE ? 0.0f : clipTime() / clipDuration(kind);
		}

		boolean both() {
			return kind == Kind.CROSS || kind == Kind.THRUST || kind == Kind.CHARGE || kind == Kind.ROAR;
		}
	}

	/** Set while vanilla draws the first-person hand (see {@code PlayerRendererClawsMixin}). */
	public static boolean inFirstPersonHand;

	public static final float DEPLOY_TICKS = 20.0f;
	private static final float CHARGE_HOLD = 100000.0f;

	private static final int SMOOTH = 0;
	private static final int SNAP = 1;
	private static final int OUT = 2;

	private WolverineClawPose() {
	}

	// ================================================================== maths

	private static Vector3f v(float x, float y, float z) {
		return new Vector3f(x, y, z);
	}

	private static float smooth(float e0, float e1, float x) {
		if (e1 <= e0) {
			return x >= e1 ? 1.0f : 0.0f;
		}
		float t = Mth.clamp((x - e0) / (e1 - e0), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	private static float ease(int type, float x) {
		x = Mth.clamp(x, 0.0f, 1.0f);
		return switch (type) {
			case SNAP -> 1.0f - (1.0f - x) * (1.0f - x) * (1.0f - x) * (1.0f - x); // a hard snap, settling into the hit
			case OUT -> 1.0f - (1.0f - x) * (1.0f - x);
			default -> x * x * (3.0f - 2.0f * x);
		};
	}

	/**
	 * The arm's rotation (model space, before any torso turn) that points it along {@code dir} (from the shoulder) with
	 * the claws' cutting edge -- the inside of their curve, the arm's local +X on the right arm, -X on the left --
	 * facing {@code edge}.
	 */
	private static Quaternionf aim(Vector3f dir, Vector3f edge, boolean rightArm) {
		Vector3f y = new Vector3f(dir).normalize();
		Vector3f x = new Vector3f(edge).sub(new Vector3f(y).mul(edge.dot(y)));
		if (x.lengthSquared() < 1.0e-6f) {
			x = new Vector3f(0, 0, -1).sub(new Vector3f(y).mul(-y.z));
			if (x.lengthSquared() < 1.0e-6f) {
				x = new Vector3f(1, 0, 0);
			}
		}
		x.normalize();
		if (!rightArm) {
			x.negate();
		}
		Vector3f z = new Vector3f(x).cross(y);
		return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
	}

	private static Quaternionf of(ModelPart part) {
		return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
	}

	/**
	 * The part's Euler angles for {@code q}, matching {@code ModelPart}'s {@code rotationZYX(zRot, yRot, xRot)} (R = Rz Ry Rx).
	 * Extracted from the matrix by hand: JOML 1.10.5's {@code getEulerAnglesZYX} does not round-trip with
	 * {@code rotationZYX} (checked: a sideways arm came back pointing the other way).
	 */
	private static void set(ModelPart part, Quaternionf q) {
		Matrix3f m = new Matrix3f().set(q);
		// JOML fields are mColRow: m02 = row 2 col 0 = -sin(y), m12 = cos(y) sin(x), m22 = cos(y) cos(x), m01 = cos(y) sin(z)
		part.xRot = (float) Math.atan2(m.m12, m.m22);
		part.yRot = (float) Math.asin(Mth.clamp(-m.m02, -1.0f, 1.0f));
		part.zRot = (float) Math.atan2(m.m01, m.m00);
	}

	/**
	 * Twist ({@code yaw}) and lean ({@code pitch}) the torso about the hips: the hip point stays where it is and the
	 * neck moves (the head with it), and the shoulders turn with the torso. For a plain lean this is exactly
	 * {@code HipBend}; the twist is what this adds.
	 */
	private static Quaternionf torso(HumanoidModel<?> m, float yaw, float pitch) {
		Quaternionf q = new Quaternionf().rotationZYX(0.0f, yaw, pitch);
		Vector3f bottom = q.transform(new Vector3f(0, 12, 0));
		float px = -bottom.x;
		float py = 12.0f - bottom.y;
		float pz = -bottom.z;
		m.body.x = px;
		m.body.y = py;
		m.body.z = pz;
		m.body.xRot = pitch;
		m.body.yRot = yaw;
		m.body.zRot = 0.0f;
		m.head.x = px;
		m.head.y = py;
		m.head.z = pz;
		m.hat.x = px;
		m.hat.y = py;
		m.hat.z = pz;
		Vector3f rs = q.transform(new Vector3f(-5, 2, 0));
		Vector3f ls = q.transform(new Vector3f(5, 2, 0));
		m.rightArm.x = px + rs.x;
		m.rightArm.y = py + rs.y;
		m.rightArm.z = pz + rs.z;
		m.leftArm.x = px + ls.x;
		m.leftArm.y = py + ls.y;
		m.leftArm.z = pz + ls.z;
		return q;
	}

	// ================================================================== third-person clips

	/** One key pose: torso twist / lean, both arms (aim + edge), legs (pitch), head pitch (NaN = leave it). */
	private record Key(float time, int ease, float yaw, float pitch, Vector3f rDir, Vector3f rEdge, Vector3f lDir,
			Vector3f lEdge, float rLeg, float lLeg, float head) {
		Key mirror() {
			return new Key(time, ease, -yaw, pitch, v(-lDir.x, lDir.y, lDir.z), v(-lEdge.x, lEdge.y, lEdge.z),
					v(-rDir.x, rDir.y, rDir.z), v(-rEdge.x, rEdge.y, rEdge.z), lLeg, rLeg, head);
		}
	}

	private static Key k(float time, int ease, float yaw, float pitch, Vector3f rDir, Vector3f rEdge, Vector3f lDir,
			Vector3f lEdge, float rLeg, float lLeg, float head) {
		return new Key(time, ease, yaw, pitch, rDir, rEdge, lDir, lEdge, rLeg, lLeg, head);
	}

	private static final class Clip {
		final float duration;
		final float in;
		final Key[] keys;
		final Quaternionf[] r;
		final Quaternionf[] l;

		Clip(float duration, float in, Key... keys) {
			this.duration = duration;
			this.in = in;
			this.keys = keys;
			this.r = new Quaternionf[keys.length];
			this.l = new Quaternionf[keys.length];
			for (int i = 0; i < keys.length; i++) {
				r[i] = aim(keys[i].rDir(), keys[i].rEdge(), true);
				l[i] = aim(keys[i].lDir(), keys[i].lEdge(), false);
			}
		}

		Clip mirror() {
			Key[] m = new Key[keys.length];
			for (int i = 0; i < keys.length; i++) {
				m[i] = keys[i].mirror();
			}
			return new Clip(duration, in, m);
		}
	}

	/** A sampled clip: its blend weight and the pose. */
	private static final class Sample {
		float w;
		float yaw;
		float pitch;
		float rLeg;
		float lLeg;
		float head = Float.NaN;
		Quaternionf r;
		Quaternionf l;
	}

	private static Sample sample(Clip c, float time) {
		Sample s = new Sample();
		Key[] ks = c.keys;
		Key last = ks[ks.length - 1];
		// out: an ease-out drop back to the base pose (recovery is quick, the arm does not hang in the air)
		float out = Mth.clamp((time - last.time()) / (c.duration - last.time()), 0.0f, 1.0f);
		s.w = smooth(0.0f, c.in, time) * (1.0f - out) * (1.0f - out);
		int i = 0;
		while (i < ks.length - 1 && time > ks[i + 1].time()) {
			i++;
		}
		Key a = ks[i];
		if (time <= a.time() || i == ks.length - 1) {
			s.yaw = a.yaw();
			s.pitch = a.pitch();
			s.rLeg = a.rLeg();
			s.lLeg = a.lLeg();
			s.head = a.head();
			s.r = new Quaternionf(c.r[i]);
			s.l = new Quaternionf(c.l[i]);
			return s;
		}
		Key b = ks[i + 1];
		float f = ease(b.ease(), (time - a.time()) / (b.time() - a.time()));
		s.yaw = Mth.lerp(f, a.yaw(), b.yaw());
		s.pitch = Mth.lerp(f, a.pitch(), b.pitch());
		s.rLeg = Mth.lerp(f, a.rLeg(), b.rLeg());
		s.lLeg = Mth.lerp(f, a.lLeg(), b.lLeg());
		s.head = Float.isNaN(a.head()) || Float.isNaN(b.head()) ? (Float.isNaN(b.head()) ? (f < 0.5f ? a.head() : Float.NaN) : b.head())
				: Mth.lerp(f, a.head(), b.head());
		s.r = new Quaternionf(c.r[i]).slerp(c.r[i + 1], f);
		s.l = new Quaternionf(c.l[i]).slerp(c.l[i + 1], f);
		return s;
	}

	private static final float NO = Float.NaN;

	/** Combo 1 (lead: right): a rising diagonal slash, low outside to high inside; torso winds right, snaps left; left arm guards then pulls back. */
	private static final Clip DIAG = new Clip(8.0f, 1.0f,
			k(1.5f, SMOOTH, 0.40f, 0.12f, v(-0.55f, 0.65f, 0.52f), v(0.5f, -0.7f, -0.5f),
					v(0.2f, 0.35f, -0.9f), v(0, -1, 0), 0.0f, 0.0f, NO),
			k(3.5f, SNAP, -0.45f, 0.22f, v(0.35f, -0.55f, -0.76f), v(0.4f, -0.9f, 0.2f),
					v(0.25f, 0.55f, 0.8f), v(0, 0, 1), 0.15f, -0.35f, NO),
			k(5.5f, OUT, -0.55f, 0.2f, v(0.55f, -0.75f, -0.37f), v(0.3f, -0.6f, 0.7f),
					v(0.25f, 0.55f, 0.8f), v(0, 0, 1), 0.15f, -0.35f, NO));

	/** Combo 2 (lead: left): a horizontal slash at chest height, outside-left across to the right; torso twists right. */
	private static final Clip HORIZ = new Clip(8.0f, 1.0f,
			k(1.5f, SMOOTH, -0.45f, 0.12f, v(-0.2f, 0.35f, -0.9f), v(0, -1, 0),
					v(0.85f, 0.0f, 0.5f), v(-0.4f, 0, -0.9f), 0.0f, 0.0f, NO),
			k(3.5f, SNAP, 0.5f, 0.2f, v(-0.25f, 0.55f, 0.8f), v(0, 0, 1),
					v(-0.5f, -0.08f, -0.85f), v(-1, 0, 0.3f), -0.35f, 0.15f, NO),
			k(5.5f, OUT, 0.6f, 0.2f, v(-0.25f, 0.55f, 0.8f), v(0, 0, 1),
					v(-0.78f, -0.05f, -0.6f), v(-0.6f, 0, 0.8f), -0.35f, 0.15f, NO));

	/** Combo 3: both arms high and outside the shoulders, then raked down and in across the chest in an X, crunching forward. */
	private static final Clip CROSS = new Clip(10.0f, 1.5f,
			k(2.5f, SMOOTH, 0.0f, -0.18f, v(-0.8f, -0.55f, 0.12f), v(0.4f, 0.6f, -0.7f),
					v(0.8f, -0.55f, 0.12f), v(-0.4f, 0.6f, -0.7f), 0.0f, 0.0f, -0.2f),
			k(5.0f, SNAP, 0.0f, 0.38f, v(0.45f, -0.5f, -0.74f), v(0.6f, 0.6f, 0.3f),
					v(-0.42f, -0.43f, -0.8f), v(-0.6f, 0.6f, 0.3f), -0.3f, 0.2f, 0.25f),
			k(7.0f, OUT, 0.0f, 0.42f, v(0.55f, -0.3f, -0.78f), v(0.5f, 0.7f, 0.5f),
					v(-0.55f, -0.25f, -0.8f), v(-0.5f, 0.7f, 0.5f), -0.3f, 0.2f, 0.25f));

	/** Z: both arms drawn back, then a two-claw thrust. */
	private static final Clip THRUST = new Clip(8.0f, 1.0f,
			k(2.0f, SMOOTH, 0.0f, 0.05f, v(-0.25f, 0.55f, 0.8f), v(0, 0, -1), v(0.25f, 0.55f, 0.8f), v(0, 0, -1), 0, 0, NO),
			k(3.8f, SNAP, 0.0f, 0.3f, v(0.06f, 0.15f, -1), v(0, -1, 0), v(-0.06f, 0.15f, -1), v(0, -1, 0), -0.3f, 0.15f, NO),
			k(5.5f, OUT, 0.0f, 0.3f, v(0.06f, 0.15f, -1), v(0, -1, 0), v(-0.06f, 0.15f, -1), v(0, -1, 0), -0.3f, 0.15f, NO));

	/** Z held: crouched forward, arms drawn back low, claws forward. */
	private static final Clip CHARGE = new Clip(CHARGE_HOLD, 3.0f,
			k(0.0f, SMOOTH, 0.0f, 0.25f, v(-0.35f, 0.7f, 0.6f), v(0, 0, -1), v(0.35f, 0.7f, 0.6f), v(0, 0, -1), -0.2f, 0.2f, NO),
			k(CHARGE_HOLD - 1.0f, SMOOTH, 0.0f, 0.25f, v(-0.35f, 0.7f, 0.6f), v(0, 0, -1), v(0.35f, 0.7f, 0.6f), v(0, 0, -1),
					-0.2f, 0.2f, NO));

	/** C: the Berserker roar -- arms flung out and down, chest out, head thrown back. */
	private static final Clip ROAR = new Clip(25.0f, 2.0f,
			k(3.0f, SNAP, 0.0f, -0.22f, v(-0.85f, 0.5f, 0.1f), v(0, 1, 0), v(0.85f, 0.5f, 0.1f), v(0, 1, 0), 0, 0, -0.6f),
			k(20.0f, SMOOTH, 0.0f, -0.2f, v(-0.85f, 0.5f, 0.1f), v(0, 1, 0), v(0.85f, 0.5f, 0.1f), v(0, 1, 0), 0, 0, -0.55f));

	/**
	 * H (manual only), claws out: both forearms brought up in front of the face, fists at eye height, knuckles forward
	 * (~5 ticks), then thrown down and out to his sides -- arms angled down and slightly out from the hips, chest out --
	 * the blades popping out at the end of the throw (~9.5 ticks), pointing down and outward; then he settles. Arm roll is
	 * the natural one for each pose (blade edge inward), so the fists never read upside down.
	 */
	private static final Clip DEPLOY = new Clip(DEPLOY_TICKS, 1.5f,
			k(5.0f, SMOOTH, 0.0f, 0.05f, v(0.32f, -0.6f, -0.73f), v(1, 0, 0), v(-0.32f, -0.6f, -0.73f), v(-1, 0, 0), 0, 0, NO),
			k(9.5f, SNAP, 0.0f, -0.12f, v(-0.45f, 0.88f, -0.14f), v(1, 0.3f, 0), v(0.45f, 0.88f, -0.14f), v(-1, 0.3f, 0), 0, 0, -0.15f),
			k(13.0f, SMOOTH, 0.0f, -0.08f, v(-0.4f, 0.9f, -0.12f), v(1, 0.3f, 0), v(0.4f, 0.9f, -0.12f), v(-1, 0.3f, 0), 0, 0, -0.1f));

	private static final Map<Kind, Clip> MIRRORED = new EnumMap<>(Kind.class);

	/** The canonical lead arm of each clip (the other arm leads the mirrored copy). */
	private static HumanoidArm canonical(Kind kind) {
		return kind == Kind.HORIZ ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
	}

	private static Clip clipOf(Kind kind) {
		return switch (kind) {
			case DIAG -> DIAG;
			case HORIZ -> HORIZ;
			case CROSS -> CROSS;
			case THRUST -> THRUST;
			case CHARGE -> CHARGE;
			case ROAR -> ROAR;
			default -> null;
		};
	}

	private static Clip clip(Kind kind, HumanoidArm lead) {
		Clip c = clipOf(kind);
		if (c == null || lead == canonical(kind) || kind == Kind.CROSS || kind == Kind.THRUST || kind == Kind.CHARGE
				|| kind == Kind.ROAR) {
			return c;
		}
		return MIRRORED.computeIfAbsent(kind, k -> clipOf(k).mirror());
	}

	static float clipDuration(Kind kind) {
		Clip c = clipOf(kind);
		return c == null ? 1.0f : c.duration;
	}

	// ================================================================== which move

	public static Move current(Player player, float partialTick) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (s == null || !s.hasPower) {
			return Move.NONE;
		}
		float now = player.level().getGameTime() + partialTick;
		if (s.chargeStartedAt != 0L) {
			return new Move(Kind.CHARGE, player.getMainArm(), now - s.chargeStartedAt, 1.0f);
		}
		HumanoidArm swingArm = arm(player, player.swingingArm);
		if (s.lastActionTick > 0L) {
			float age = now - s.lastActionTick;
			Move m = switch (s.lastAction) {
				case 1 -> timed(Kind.HORIZ, swingArm, age, 1.0f);
				case 2 -> timed(Kind.DIAG, swingArm, age, 1.0f);
				case 4 -> timed(Kind.THRUST, swingArm, age, 1.0f);
				case 5 -> timed(Kind.DIAG, swingArm, age, 2.0f);
				case 6 -> timed(Kind.ROAR, player.getMainArm(), age, 1.0f);
				default -> Move.NONE;
			};
			if (m.kind() != Kind.NONE) {
				return m;
			}
		}
		if (!(player instanceof AbstractClientPlayer cp) || !WolverineClawsModel.visible(cp, partialTick)) {
			return Move.NONE;
		}
		WolverineClawCombo.Swing sw = WolverineClawCombo.last(player);
		if (sw == null) {
			return Move.NONE;
		}
		float age = now - sw.tick();
		Kind kind = sw.step() == WolverineClawCombo.STEP_CROSS ? Kind.CROSS
				: sw.step() == WolverineClawCombo.STEP_SECOND ? Kind.HORIZ : Kind.DIAG;
		return timed(kind, arm(player, sw.hand()), age, 1.0f);
	}

	private static Move timed(Kind kind, HumanoidArm arm, float age, float speed) {
		return age < 0.0f || age * speed >= clipDuration(kind) ? Move.NONE : new Move(kind, arm, age, speed);
	}

	private static HumanoidArm arm(Player player, InteractionHand hand) {
		return hand == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
	}

	/** Ticks a combo step needs before the next click may cut it off (its impact). */
	public static float commitTicks(int step) {
		return step == WolverineClawCombo.STEP_CROSS ? 5.0f : 3.5f;
	}

	/** The snap -- the fast part of the cut -- in clip ticks: {from, to}. */
	private static float[] snap(Kind kind) {
		return switch (kind) {
			case DIAG, HORIZ -> new float[] {1.5f, 4.2f};
			case CROSS -> new float[] {2.5f, 5.6f};
			case THRUST -> new float[] {2.0f, 4.4f};
			default -> null;
		};
	}

	/** True while {@code arm}'s claws are in the snap of a move -- when they leave a slash trail. */
	public static boolean trailing(Player player, float partialTick, HumanoidArm arm) {
		Move m = current(player, partialTick);
		float[] w = snap(m.kind());
		if (w == null || (!m.both() && m.arm() != arm)) {
			return false;
		}
		return m.clipTime() >= w[0] && m.clipTime() <= w[1];
	}

	/** The fraction of the move at which the claws strike home (sparks, sweep). */
	public static float impactAt(Kind kind) {
		float tick = switch (kind) {
			case CROSS -> 5.0f;
			case THRUST -> 3.8f;
			default -> 3.5f;
		};
		return tick / clipDuration(kind);
	}

	/** True when a claw move is playing and should own the arms (vanilla's punch swing switched off). */
	public static boolean ownsArms(Player player, float partialTick) {
		return player instanceof AbstractClientPlayer cp && WolverineClawsModel.handsShown(cp, partialTick);
	}

	// ================================================================== third person helpers

	/** Whether the torso may be twisted / bent (standing upright: not crouching, riding, swimming, gliding...). */
	private static boolean free(Player player) {
		return !player.isCrouching() && !player.isPassenger() && !player.isSwimming() && !player.isFallFlying()
				&& !player.isSleeping() && !player.isAutoSpinAttack() && !player.isUsingItem();
	}

	// ================================================================== third person

	/** Pose the whole body. Called at the tail of {@code setupAnim} for players. */
	public static void apply(Player player, HumanoidModel<?> m) {
		if (inFirstPersonHand) {
			return; // the first-person hand: firstPerson() poses it
		}
		WolverineState st = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (st == null || !st.hasPower || WolverineBlock.isBlocking(player) || st.dashUntil > player.level().getGameTime()) {
			return;
		}
		// vanilla never resets these (the torso twist moves them): start every frame from the rest position
		m.body.x = 0.0f;
		m.head.x = 0.0f;
		m.hat.x = 0.0f;
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float now = player.level().getGameTime() + partial;
		boolean free = free(player);

		Quaternionf r = of(m.rightArm);
		Quaternionf l = of(m.leftArm);
		float yaw = 0.0f;
		float pitch = 0.0f;
		float rLeg = m.rightLeg.xRot;
		float lLeg = m.leftLeg.xRot;
		float headPitch = Float.NaN;
		float headPitchW = 0.0f;
		boolean any = false;

		// ---- claw deploy (H only)
		float age = now - st.clawsChangedAt;
		Clip dc = age < DEPLOY_TICKS && WolverineClawsModel.manualDeploy(st) ? DEPLOY : null;
		if (dc != null && st.clawsChangedAt > 0L && age >= 0.0f) {
			Sample s = sample(dc, age);
			if (s.w > 0.001f) {
				any = true;
				r.slerp(s.r, s.w);
				l.slerp(s.l, s.w);
				yaw = Mth.lerp(s.w, yaw, s.yaw);
				pitch = Mth.lerp(s.w, pitch, s.pitch);
				if (!Float.isNaN(s.head)) {
					headPitch = s.head;
					headPitchW = s.w;
				}
			}
		}

		// ---- moves
		Move mv = current(player, partial);
		if (mv.kind() != Kind.NONE) {
			Clip c = clip(mv.kind(), mv.arm());
			Sample s = sample(c, mv.clipTime());
			if (mv.kind() == Kind.CHARGE) {
				float tremble = 0.035f * Mth.sin(now * 2.1f) * Math.min(1.0f, mv.age() / WolverineConfig.EXECUTION_CHARGE_TICKS);
				s.r.rotateX(tremble);
				s.l.rotateX(-tremble);
			}
			if (s.w > 0.001f) {
				any = true;
				r.slerp(s.r, s.w);
				l.slerp(s.l, s.w);
				yaw = Mth.lerp(s.w, yaw, s.yaw);
				pitch = Mth.lerp(s.w, pitch, s.pitch);
				rLeg = Mth.lerp(s.w, rLeg, s.rLeg);
				lLeg = Mth.lerp(s.w, lLeg, s.lLeg);
				if (!Float.isNaN(s.head)) {
					headPitch = Float.isNaN(headPitch) ? s.head : Mth.lerp(s.w, headPitch, s.head);
					headPitchW = Math.max(headPitchW, s.w);
				}
			}
		}

		if (!any) {
			return;
		}
		Quaternionf tq = free ? torso(m, yaw, pitch) : new Quaternionf();
		set(m.rightArm, new Quaternionf(tq).mul(r));
		set(m.leftArm, new Quaternionf(tq).mul(l));
		if (free) {
			m.rightLeg.xRot = rLeg;
			m.leftLeg.xRot = lLeg;
		}
		if (!Float.isNaN(headPitch)) {
			m.head.xRot = Mth.lerp(headPitchW, m.head.xRot, headPitch);
		}
		m.hat.copyFrom(m.head);
	}

	// ================================================================== first person

	/** First-person key: per arm (tx, ty, tz in view units, rx, ry, rz in degrees about the hand), written for the right arm. */
	private record FKey(float time, int ease, float[] lead, float[] off) {
	}

	private static final class FClip {
		final float duration;
		final float in;
		final FKey[] keys;
		/** Both arms play the lead track (mirrored), e.g. the cross. */
		final boolean symmetric;

		FClip(float duration, float in, boolean symmetric, FKey... keys) {
			this.duration = duration;
			this.in = in;
			this.symmetric = symmetric;
			this.keys = keys;
		}
	}

	private static final float[] OFF_GUARD = {0.0f, -0.05f, 0.08f, 0.0f, 0.0f, 0.0f};
	private static final float[] OFF_BACK = {0.02f, -0.12f, 0.14f, 6.0f, 0.0f, 0.0f};

	private static FKey fk(float time, int ease, float[] lead, float[] off) {
		return new FKey(time, ease, lead, off);
	}

	private static final FClip F_DIAG = new FClip(8.0f, 1.0f, false,
			fk(1.5f, SMOOTH, new float[] {0.12f, -0.2f, 0.05f, -20.0f, -20.0f, -20.0f}, OFF_GUARD),
			fk(3.5f, SNAP, new float[] {-0.46f, 0.2f, -0.15f, 25.0f, 30.0f, 25.0f}, OFF_BACK),
			fk(5.5f, OUT, new float[] {-0.58f, 0.28f, -0.1f, 32.0f, 38.0f, 30.0f}, OFF_BACK));
	/** Written for a right-arm lead sweeping right to left; the combo plays it mirrored on the left arm. */
	private static final FClip F_HORIZ = new FClip(8.0f, 1.0f, false,
			fk(1.5f, SMOOTH, new float[] {0.34f, 0.04f, 0.06f, 0.0f, -25.0f, 55.0f}, OFF_GUARD),
			fk(3.5f, SNAP, new float[] {-0.48f, 0.1f, -0.15f, 0.0f, 35.0f, 70.0f}, OFF_BACK),
			fk(5.5f, OUT, new float[] {-0.6f, 0.1f, -0.1f, 0.0f, 45.0f, 75.0f}, OFF_BACK));
	private static final FClip F_CROSS = new FClip(10.0f, 1.5f, true,
			fk(2.5f, SMOOTH, new float[] {0.22f, 0.45f, 0.12f, 30.0f, -25.0f, 0.0f}, null),
			fk(5.0f, SNAP, new float[] {-0.3f, -0.02f, -0.2f, -15.0f, 35.0f, 0.0f}, null),
			fk(7.0f, OUT, new float[] {-0.38f, -0.1f, -0.15f, -25.0f, 40.0f, 0.0f}, null));
	private static final FClip F_THRUST = new FClip(8.0f, 1.0f, true,
			fk(2.0f, SMOOTH, new float[] {0.02f, -0.06f, 0.15f, 0.0f, 0.0f, 0.0f}, null),
			fk(3.8f, SNAP, new float[] {-0.08f, 0.05f, -0.4f, -6.0f, 6.0f, 0.0f}, null),
			fk(5.5f, OUT, new float[] {-0.08f, 0.04f, -0.36f, -5.0f, 5.0f, 0.0f}, null));
	private static final FClip F_CHARGE = new FClip(CHARGE_HOLD, 3.0f, true,
			fk(0.0f, SMOOTH, new float[] {0.0f, -0.15f, 0.15f, 12.0f, 0.0f, 0.0f}, null),
			fk(CHARGE_HOLD - 1.0f, SMOOTH, new float[] {0.0f, -0.15f, 0.15f, 12.0f, 0.0f, 0.0f}, null));
	/** H deploy, first person: fists raised in front of the face, then thrown down and out to the lower screen edges as the claws pop. */
	private static final FClip F_DEPLOY = new FClip(DEPLOY_TICKS, 1.0f, true,
			fk(5.0f, SMOOTH, new float[] {-0.22f, 0.32f, 0.04f, 25.0f, 18.0f, 0.0f}, null),
			fk(9.5f, SNAP, new float[] {0.22f, -0.12f, 0.04f, -12.0f, -14.0f, 0.0f}, null),
			fk(13.0f, SMOOTH, new float[] {0.18f, -0.08f, 0.04f, -8.0f, -10.0f, 0.0f}, null));

	private static FClip fclip(Kind kind) {
		return switch (kind) {
			case DIAG -> F_DIAG;
			case HORIZ -> F_HORIZ;
			case CROSS -> F_CROSS;
			case THRUST -> F_THRUST;
			case CHARGE -> F_CHARGE;
			default -> null;
		};
	}

	/** One arm's sampled first-person offset (right-arm convention) and the clip's weight in [6]. */
	private static float[] fsample(FClip c, float time, boolean lead) {
		FKey[] ks = c.keys;
		float[] out = new float[7];
		out[6] = smooth(0.0f, c.in, time) * (1.0f - smooth(ks[ks.length - 1].time(), c.duration, time));
		int i = 0;
		while (i < ks.length - 1 && time > ks[i + 1].time()) {
			i++;
		}
		float[] a = track(c, ks[i], lead);
		if (time <= ks[i].time() || i == ks.length - 1) {
			System.arraycopy(a, 0, out, 0, 6);
			return out;
		}
		float[] b = track(c, ks[i + 1], lead);
		float f = ease(ks[i + 1].ease(), (time - ks[i].time()) / (ks[i + 1].time() - ks[i].time()));
		for (int j = 0; j < 6; j++) {
			out[j] = Mth.lerp(f, a[j], b[j]);
		}
		return out;
	}

	private static float[] track(FClip c, FKey k, boolean lead) {
		return lead || c.symmetric || k.off() == null ? k.lead() : k.off();
	}

	/**
	 * Apply this frame's first-person claw motion to {@code pose} (before vanilla's own arm placement): the move, the
	 * H deploy throw. Returns true when Wolverine's hands are shown (the
	 * caller then draws the arm without vanilla's punch swing).
	 */
	public static boolean firstPerson(AbstractClientPlayer player, HumanoidArm arm, float partialTick, PoseStack pose) {
		WolverineState st = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (st == null || !st.hasPower) {
			return false;
		}
		float f = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		float now = player.level().getGameTime() + partialTick;
		float[] v = new float[6];

		// claw deploy (H only)
		float age = now - st.clawsChangedAt;
		FClip dc = age < F_DEPLOY.duration && WolverineClawsModel.manualDeploy(st) ? F_DEPLOY : null;
		if (dc != null && st.clawsChangedAt > 0L && age >= 0.0f) {
			float[] d = fsample(dc, age, true);
			for (int j = 0; j < 6; j++) {
				v[j] = Mth.lerp(d[6], v[j], d[j]);
			}
		}

		// the move
		Move m = current(player, partialTick);
		FClip c = m.kind() == Kind.NONE ? null : fclip(m.kind());
		if (c != null) {
			boolean lead = m.both() || m.arm() == arm;
			// written for a right-arm lead: a left-arm lead plays it mirrored; the off arm keeps its own side
			float[] d = fsample(c, m.clipTime(), lead);
			if (m.kind() == Kind.CHARGE) {
				d[1] += 0.006f * Mth.sin(now * 2.3f) * Math.min(1.0f, m.age() / WolverineConfig.EXECUTION_CHARGE_TICKS);
			}
			for (int j = 0; j < 6; j++) {
				v[j] = Mth.lerp(d[6], v[j], d[j]);
			}
			// the off arm of a cross / thrust staggers back a hair so the crossing never z-fights
			if (m.both() && arm != player.getMainArm()) {
				v[2] -= 0.03f * d[6];
			}
		}

		// mirror to this arm (the tracks are written for the right arm) and pivot the rotations about the hand
		float px = f * 0.45f;
		pose.translate(f * v[0], v[1], v[2]);
		pose.translate(px, -0.45f, -0.7f);
		pose.mulPose(Axis.ZP.rotationDegrees(f * v[5]));
		pose.mulPose(Axis.YP.rotationDegrees(f * v[4]));
		pose.mulPose(Axis.XP.rotationDegrees(v[3]));
		pose.translate(-px, 0.45f, 0.7f);
		return true;
	}
}
