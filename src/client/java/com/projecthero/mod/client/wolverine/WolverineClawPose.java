package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.wolverine.WolverineBlock;
import com.projecthero.mod.wolverine.WolverineConfig;
import com.projecthero.mod.wolverine.data.WolverineState;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.1: Wolverine's claw animations, third person ({@link #apply}) and first person ({@link #firstPerson}) from one
 * set of key-framed moves, so both views always play the same thing:
 * <ul>
 * <li>left-click combo ({@link WolverineClawCombo}): a diagonal slash with the right claws (high outside to low across),
 * the same with the left, then an overhead cross -- both arms high, brought down crossing in an X;</li>
 * <li>Claw Slash (R): a wide horizontal sweep; Cross Slash (G): two diagonal slashes, one per hand; Frenzy (V): fast
 * alternating slashes; Adamantium Execution (Z): arms drawn back and low while it charges, then a two-claw thrust;
 * Berserker Rage (C): arms flung out and head thrown back in a roar. Claw Dash keeps its own lunge pose.</li>
 * </ul>
 * Everything keys off synced state (the swing packet's hand, {@code WolverineState.lastAction/lastActionTick} and
 * {@code chargeStartedAt}), so every viewer sees the same move.
 */
public final class WolverineClawPose {
	public enum Kind { NONE, SLASH, CROSS, SWEEP, THRUST, CHARGE, ROAR }

	/** The move playing now: what, which arm leads it, and how far through (0..1). */
	public record Move(Kind kind, HumanoidArm arm, float t) {
		static final Move NONE = new Move(Kind.NONE, HumanoidArm.RIGHT, 0.0f);

		boolean both() {
			return kind == Kind.CROSS || kind == Kind.THRUST || kind == Kind.CHARGE || kind == Kind.ROAR;
		}
	}

	private static final int SLASH_TICKS = 7;
	private static final int CROSS_TICKS = 9;
	private static final int SWEEP_TICKS = 8;
	private static final int THRUST_TICKS = 8;
	private static final int FRENZY_TICKS = 4;
	private static final int ROAR_TICKS = 25;

	private WolverineClawPose() {
	}

	// ------------------------------------------------------------------ which move

	public static Move current(Player player, float partialTick) {
		WolverineState s = player.getAttachedOrElse(ModAttachments.WOLVERINE_STATE, null);
		if (s == null || !s.hasPower) {
			return Move.NONE;
		}
		float now = player.level().getGameTime() + partialTick;
		if (s.chargeStartedAt != 0L) {
			return new Move(Kind.CHARGE, player.getMainArm(),
					Mth.clamp((now - s.chargeStartedAt) / WolverineConfig.EXECUTION_CHARGE_TICKS, 0.0f, 1.0f));
		}
		HumanoidArm swingArm = arm(player, player.swingingArm);
		if (s.lastActionTick > 0L) {
			float age = now - s.lastActionTick;
			Move m = switch (s.lastAction) {
				case 1 -> timed(Kind.SWEEP, swingArm, age, SWEEP_TICKS);
				case 2 -> timed(Kind.SLASH, swingArm, age, SLASH_TICKS);
				case 4 -> timed(Kind.THRUST, swingArm, age, THRUST_TICKS);
				case 5 -> timed(Kind.SLASH, swingArm, age, FRENZY_TICKS);
				case 6 -> timed(Kind.ROAR, player.getMainArm(), age, ROAR_TICKS);
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
		return sw.step() == WolverineClawCombo.STEP_CROSS ? timed(Kind.CROSS, arm(player, sw.hand()), age, CROSS_TICKS)
				: timed(Kind.SLASH, arm(player, sw.hand()), age, SLASH_TICKS);
	}

	private static Move timed(Kind kind, HumanoidArm arm, float age, int ticks) {
		return age < 0.0f || age >= ticks ? Move.NONE : new Move(kind, arm, age / ticks);
	}

	private static HumanoidArm arm(Player player, InteractionHand hand) {
		return hand == InteractionHand.OFF_HAND ? player.getMainArm().getOpposite() : player.getMainArm();
	}

	/** True while {@code arm}'s claws are in the cutting part of a move -- when they leave a slash trail. */
	public static boolean trailing(Player player, float partialTick, HumanoidArm arm) {
		Move m = current(player, partialTick);
		if (m.kind() == Kind.NONE || m.kind() == Kind.CHARGE || m.kind() == Kind.ROAR) {
			return false;
		}
		if (!m.both() && m.arm() != arm) {
			return false;
		}
		return m.t() >= 0.22f && m.t() <= 0.8f;
	}

	/** The fraction of the move at which the claws strike home (sparks, sweep). */
	public static float impactAt(Kind kind) {
		return switch (kind) {
			case THRUST -> 0.45f;
			case CROSS -> 0.55f;
			default -> 0.5f;
		};
	}

	// ------------------------------------------------------------------ easing helpers

	private static float smooth(float e0, float e1, float x) {
		float t = Mth.clamp((x - e0) / (e1 - e0), 0.0f, 1.0f);
		return t * t * (3.0f - 2.0f * t);
	}

	/** Key-framed value at {@code t}: {@code keys} are (time, value...) rows, eased between rows. */
	private static float[] keyed(float t, float[][] keys) {
		int n = keys[0].length - 1;
		float[] out = new float[n];
		if (t <= keys[0][0]) {
			System.arraycopy(keys[0], 1, out, 0, n);
			return out;
		}
		for (int i = 0; i < keys.length - 1; i++) {
			float[] a = keys[i];
			float[] b = keys[i + 1];
			if (t <= b[0]) {
				float f = smooth(a[0], b[0], t);
				for (int j = 0; j < n; j++) {
					out[j] = a[j + 1] + (b[j + 1] - a[j + 1]) * f;
				}
				return out;
			}
		}
		System.arraycopy(keys[keys.length - 1], 1, out, 0, n);
		return out;
	}

	/** Blend weight: in over the first part of the move, out over the end. */
	private static float weight(Move m) {
		return switch (m.kind()) {
			case CHARGE -> smooth(0.0f, 0.08f, m.t());
			case ROAR -> smooth(0.0f, 0.1f, m.t()) * (1.0f - smooth(0.75f, 1.0f, m.t()));
			default -> smooth(0.0f, 0.18f, m.t()) * (1.0f - smooth(0.7f, 1.0f, m.t()));
		};
	}

	// ------------------------------------------------------------------ third person

	/** Arm rotations (xRot, yRot, zRot) for one arm, {@code o} = +1 right arm / -1 left, {@code lead} = this arm leads. */
	private static float[] thirdPersonArm(Move m, float o, boolean lead, float ageTicks) {
		float t = m.t();
		return switch (m.kind()) {
			case SLASH -> lead
					? keyed(t, new float[][] {{0.0f, -2.6f, 0.35f * o, 0.45f * o}, {0.3f, -2.6f, 0.35f * o, 0.45f * o},
							{0.6f, -0.55f, -0.85f * o, -0.25f * o}, {1.0f, -0.45f, -0.75f * o, -0.2f * o}})
					: new float[] {-0.55f, 0.15f * o, 0.1f * o};
			case CROSS -> keyed(t, new float[][] {{0.0f, -2.85f, 0.25f * o, 0.5f * o}, {0.35f, -2.85f, 0.25f * o, 0.5f * o},
					{0.65f, -0.75f, -0.95f * o, -0.1f * o}, {1.0f, -0.65f, -0.85f * o, -0.1f * o}});
			case SWEEP -> lead
					? keyed(t, new float[][] {{0.0f, -1.45f, 1.15f * o, 0.0f}, {0.28f, -1.45f, 1.15f * o, 0.0f},
							{0.62f, -1.4f, -1.0f * o, -0.15f * o}, {1.0f, -1.35f, -0.9f * o, -0.1f * o}})
					: new float[] {-0.6f, 0.2f * o, 0.15f * o};
			case THRUST -> keyed(t, new float[][] {{0.0f, -0.85f, 0.2f * o, 0.1f * o}, {0.3f, -0.85f, 0.2f * o, 0.1f * o},
					{0.48f, -1.65f, -0.12f * o, 0.0f}, {1.0f, -1.6f, -0.1f * o, 0.0f}});
			case CHARGE -> new float[] {0.45f + 0.03f * Mth.sin(ageTicks * 2.1f) * t, 0.2f * o, 0.35f * o};
			case ROAR -> new float[] {-0.25f, 0.0f, 0.8f * o};
			default -> null;
		};
	}

	/** Pose the arms (and the head, for the roar) over vanilla's result. Called at the tail of {@code setupAnim}. */
	public static void apply(Player player, HumanoidModel<?> model) {
		if (WolverineBlock.isBlocking(player)) {
			return; // the guard pose wins
		}
		float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		Move m = current(player, partial);
		if (m.kind() == Kind.NONE) {
			return;
		}
		float w = weight(m);
		float age = player.level().getGameTime() + partial;
		pose(model.rightArm, thirdPersonArm(m, 1.0f, m.both() || m.arm() == HumanoidArm.RIGHT, age), w);
		pose(model.leftArm, thirdPersonArm(m, -1.0f, m.both() || m.arm() == HumanoidArm.LEFT, age), w);
		if (m.kind() == Kind.ROAR) {
			model.head.xRot = Mth.lerp(w, model.head.xRot, -0.6f);
			model.hat.xRot = model.head.xRot;
		}
	}

	private static void pose(ModelPart arm, float[] r, float w) {
		if (r == null) {
			return;
		}
		arm.xRot = Mth.lerp(w, arm.xRot, r[0]);
		arm.yRot = Mth.lerp(w, arm.yRot, r[1]);
		arm.zRot = Mth.lerp(w, arm.zRot, r[2]);
	}

	// ------------------------------------------------------------------ first person

	/**
	 * The first-person offset for one arm (view space: tx, ty, tz in view units, then rx, ry, rz in degrees), {@code f}
	 * = +1 for the right arm, -1 for the left.
	 */
	private static float[] firstPersonArm(Move m, float f, boolean lead, float ageTicks) {
		float t = m.t();
		return switch (m.kind()) {
			case SLASH -> lead
					? keyed(t, new float[][] {{0.0f, 0.08f * f, 0.22f, 0.08f, -8.0f, 0.0f, 38.0f * f},
							{0.3f, 0.08f * f, 0.22f, 0.08f, -8.0f, 0.0f, 38.0f * f},
							{0.6f, -0.38f * f, -0.12f, -0.18f, 0.0f, 18.0f * f, -42.0f * f},
							{1.0f, -0.34f * f, -0.16f, -0.12f, 0.0f, 14.0f * f, -38.0f * f}})
					: new float[] {0.0f, -0.06f, 0.06f, 0.0f, 0.0f, 0.0f};
			case CROSS -> keyed(t, new float[][] {{0.0f, 0.05f * f, 0.3f, 0.1f, -12.0f, 0.0f, 22.0f * f},
					{0.35f, 0.05f * f, 0.3f, 0.1f, -12.0f, 0.0f, 22.0f * f},
					{0.65f, -0.36f * f, -0.15f, -0.2f, 0.0f, 12.0f * f, -30.0f * f},
					{1.0f, -0.32f * f, -0.18f, -0.14f, 0.0f, 10.0f * f, -26.0f * f}});
			case SWEEP -> lead
					? keyed(t, new float[][] {{0.0f, 0.1f * f, 0.05f, 0.0f, 0.0f, -38.0f * f, 0.0f},
							{0.28f, 0.1f * f, 0.05f, 0.0f, 0.0f, -38.0f * f, 0.0f},
							{0.62f, -0.2f * f, 0.0f, -0.1f, 0.0f, 42.0f * f, -15.0f * f},
							{1.0f, -0.18f * f, -0.02f, -0.08f, 0.0f, 36.0f * f, -12.0f * f}})
					: new float[] {0.0f, -0.06f, 0.06f, 0.0f, 0.0f, 0.0f};
			case THRUST -> keyed(t, new float[][] {{0.0f, 0.02f * f, -0.08f, 0.2f, 0.0f, 0.0f, 0.0f},
					{0.3f, 0.02f * f, -0.08f, 0.2f, 0.0f, 0.0f, 0.0f},
					{0.48f, -0.1f * f, 0.05f, -0.42f, -6.0f, 6.0f * f, 0.0f},
					{1.0f, -0.08f * f, 0.04f, -0.36f, -5.0f, 5.0f * f, 0.0f}});
			case CHARGE -> new float[] {0.0f, -0.15f + 0.006f * Mth.sin(ageTicks * 2.3f) * t, 0.15f, 12.0f, 0.0f, 0.0f};
			default -> null;
		};
	}

	/**
	 * Apply this frame's first-person claw move to {@code pose} (before vanilla's own arm placement). Returns true if a
	 * move is playing (the caller then draws the arm without vanilla's own swing).
	 */
	public static boolean firstPerson(AbstractClientPlayer player, HumanoidArm arm, float partialTick, PoseStack pose) {
		Move m = current(player, partialTick);
		if (m.kind() == Kind.NONE || m.kind() == Kind.ROAR) {
			return false;
		}
		float f = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		float[] r = firstPersonArm(m, f, m.both() || m.arm() == arm, player.level().getGameTime() + partialTick);
		if (r == null) {
			return false;
		}
		float w = weight(m);
		// both arms in one move: the off arm sits a hair further back so the crossing never z-fights
		float stagger = m.both() && arm != player.getMainArm() ? -0.03f : 0.0f;
		pose.translate(r[0] * w, r[1] * w, r[2] * w + stagger * w);
		pose.mulPose(Axis.ZP.rotationDegrees(r[5] * w));
		pose.mulPose(Axis.YP.rotationDegrees(r[4] * w));
		pose.mulPose(Axis.XP.rotationDegrees(r[3] * w));
		return true;
	}
}
