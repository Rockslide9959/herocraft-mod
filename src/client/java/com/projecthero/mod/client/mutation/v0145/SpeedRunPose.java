package com.projecthero.mod.client.mutation.v0145;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.client.moonknight.MoonKnightPose;
import com.projecthero.mod.client.mutation.MutationPose;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.hero.revamp.v0145.SuperSpeedV0145;
import com.projecthero.mod.hero.visual.MutationVisualState;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.7: the speedster run, for every player with Speed Mode or Overdrive on (the synced {@code p04.trail} flag,
 * {@code p04.trail_red} = Overdrive) who is actually running -- so every viewer sees it.
 *
 * v0.14.8: one upright sprint for both modes (see {@link #apply}); Overdrive only changes the trail colour.
 * Blends in / out over ~4 ticks ({@link #tick}). Walking, standing, sneaking, swimming, riding and phasing keep
 * vanilla's animation, and a move animation ({@code MutationPose}) always wins. The first-person hand is left alone.
 *
 * <p>Model space: +Y is down, -Z is forward; the body leans from the hip point, so the neck (and the head and
 * shoulders with it) swings forward and down -- the same technique as {@code HulkPose}. Arm / leg X positive swings
 * the limb back.
 */
public final class SpeedRunPose {
	/** v0.14.8: torso lean about the hips, radians (~17 degrees) -- was 27 / 40, which read as a crouch. */
	private static final float LEAN = 0.30f;
	/** Stride cycles per vanilla walk cycle. */
	private static final float CADENCE = 2.0f;
	/** Per-footfall bounce, model pixels. */
	private static final float BOUNCE = 0.7f;
	private static final float ARM_SWING = 1.2f;
	/** The arms swing about a point this far forward of hanging straight down. */
	private static final float ARM_FORWARD = 0.2f;
	private static final float LEG_FORWARD = 1.15f;
	private static final float LEG_BACK = 0.9f;
	private static final float BLEND_PER_TICK = 0.25f;
	/** Blocks per tick (3-D, so a wall run counts) above which a speedster is "running". */
	private static final double RUN_SPEED = 0.12;

	/** Per player: {previous weight, current weight}, advanced every client tick. */
	private static final Map<UUID, float[]> WEIGHTS = new HashMap<>();

	/**
	 * While an after-image is being drawn: the weight / Overdrive flag it recorded (NaN = not drawing one), so the
	 * trail shows the run it was left by rather than the player's pose right now.
	 */
	public static float overrideWeight = Float.NaN;
	public static boolean overrideOverdrive;

	private SpeedRunPose() {
	}

	/** Whether {@code p} should be in the run pose this tick. */
	public static boolean running(Player p) {
		if (!MutationVisuals.hasFlag(p, SuperSpeedV0145.TRAIL) || p.isSpectator() || p.isCrouching() || p.isSwimming()
				|| p.isFallFlying() || p.isPassenger() || p.isUnderWater() || p.getAbilities().flying
				|| SuperSpeedHandlers.phasing(p)) {
			return false;
		}
		double dx = p.getX() - p.xo;
		double dy = p.getY() - p.yo;
		double dz = p.getZ() - p.zo;
		return dx * dx + dy * dy + dz * dz > RUN_SPEED * RUN_SPEED;
	}

	public static boolean overdrive(Player p) {
		return MutationVisuals.state(p).value(SuperSpeedV0145.TRAIL_RED, 0f) > 0.5f;
	}

	/** Advances every visible player's blend weight (client tick). */
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

	/** The run pose's blend weight for {@code p} at {@code partial}. */
	public static float weight(Player p, float partial) {
		float[] w = WEIGHTS.get(p.getUUID());
		return w == null ? 0f : Mth.lerp(partial, w[0], w[1]);
	}

	/** True while a move animation owns the body (it wins over the run). */
	private static boolean moveAnimPlaying(Player p) {
		MutationVisualState s = MutationVisuals.state(p);
		if (s.anim().isEmpty()) {
			return false;
		}
		MutationPose.Def def = MutationPose.get(s.anim());
		if (def == null) {
			return false;
		}
		return def.loops() || p.level().getGameTime() - s.animStart() < def.end();
	}

	/**
	 * Called at the tail of {@code HumanoidModel.setupAnim}, before the mutation move poses.
	 *
	 * <p>v0.14.8 redesign (it read as sneaking: a crouch-deep lean, a lowered head and arms folded back along the torso).
	 * Now an upright athletic sprint, the same in Speed Mode and Overdrive:
	 * <ul>
	 *   <li>the torso leans a modest {@link #LEAN} (~17 degrees) forward <em>about the hips</em> -- the body pivot moves
	 *       to wherever the neck ends up, so the hips (and the legs hung from them) stay exactly where they were and the
	 *       head only moves forward with the neck, never down into a crouch; the head keeps the look pitch (level);</li>
	 *   <li>the arms pump hard in opposition to the legs, in world space (not folded back with the torso): well
	 *       forward-and-up on the drive, back past the hip on the recovery, tucked toward the centre line;</li>
	 *   <li>the legs take long strides with a higher forward knee drive than the push-off behind;</li>
	 *   <li>a small bounce every step and a shoulder twist with the arms; the cadence follows {@code limbSwing}, which
	 *       advances with how fast the player actually moves.</li>
	 * </ul>
	 */
	public static void apply(Player player, HumanoidModel<?> m, float limbSwing) {
		if (MoonKnightPose.firstPersonHand) {
			return;
		}
		float w;
		if (!Float.isNaN(overrideWeight)) {
			w = overrideWeight;
		} else {
			if (moveAnimPlaying(player)) {
				return;
			}
			w = weight(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		}
		pose(m, limbSwing, w, !Float.isNaN(overrideWeight) ? (overrideOverdrive ? 1f : 0.6f) : (overdrive(player) ? 1f : 0.6f));
	}

	/**
	 * v0.17.1 (user: "make the regular super speed running animation better"): the sprint itself, shared by both speedsters
	 * (the Speed Force Speedster drives it from its momentum). {@code w} blends it in (0..1); {@code drive} (0..1) is how
	 * hard they are running -- the lean deepens, the arms pump harder and higher, the knees drive higher, and the shoulders
	 * and hips counter-rotate with every stride, with a little roll, so it reads as a flat-out sprint rather than a jog.
	 */
	public static void pose(HumanoidModel<?> m, float limbSwing, float w, float drive) {
		if (w <= 0.001f) {
			return;
		}
		w = w * w * (3f - 2f * w);
		drive = Mth.clamp(drive, 0f, 1f);
		float lean = (LEAN + 0.16f * drive) * w;
		float armSwing = ARM_SWING + 0.35f * drive;
		float legForward = LEG_FORWARD + 0.3f * drive;
		float legBack = LEG_BACK + 0.15f * drive;
		float phase = limbSwing * 0.6662f * CADENCE;
		float c = Mth.cos(phase);
		float s = Mth.sin(phase);
		// a little bounce twice per stride cycle (every footfall), in model pixels (+Y is down)
		float bob = -Math.abs(s) * BOUNCE * w;

		// the torso leans about the hip point (y 12, z 0): the neck swings forward, barely down
		float neckY = 12.0f - 12.0f * Mth.cos(lean) + bob;
		float neckZ = -12.0f * Mth.sin(lean);
		m.body.xRot = lean;
		m.body.y = neckY;
		m.body.z = neckZ;
		// shoulders twist against the hips with the arms, plus a touch of roll onto the planted foot
		m.body.yRot = Mth.lerp(w, m.body.yRot, s * (0.12f + 0.08f * drive));
		m.body.zRot = Mth.lerp(w, m.body.zRot, c * 0.04f * drive);
		m.head.y = neckY;
		m.head.z = neckZ;
		// the head stays level and steady (eyes forward) -- it only counters the twist a little
		m.head.yRot -= s * 0.06f * w;
		m.hat.copyFrom(m.head);

		// shoulders 2 px down the leaning torso
		m.rightArm.y = neckY + 2.0f * Mth.cos(lean);
		m.leftArm.y = m.rightArm.y;
		m.rightArm.z = neckZ + 2.0f * Mth.sin(lean);
		m.leftArm.z = m.rightArm.z;
		// arms pump opposite the legs (right arm forward while the right leg is back); negative X = forward / up --
		// the forward drive goes higher than the back swing, like a bent elbow punching up to the chin
		float rArm = -c * armSwing - ARM_FORWARD;
		float lArm = c * armSwing - ARM_FORWARD;
		rArm = rArm < 0f ? rArm * (1.0f + 0.15f * drive) : rArm * 0.8f;
		lArm = lArm < 0f ? lArm * (1.0f + 0.15f * drive) : lArm * 0.8f;
		m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, rArm);
		m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, lArm);
		// on the forward drive the hand comes in toward the centre line; on the back swing it opens a little
		m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -Math.max(0f, c) * (0.25f + 0.15f * drive) + Math.max(0f, -c) * 0.08f);
		m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, Math.max(0f, -c) * (0.25f + 0.15f * drive) - Math.max(0f, c) * 0.08f);
		m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.08f + 0.05f * drive);
		m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.08f - 0.05f * drive);

		// legs from the (unmoved) hips: long strides, the knee driven further forward than the push-off goes back
		float right = c > 0f ? c * legBack : c * legForward;
		float left = c > 0f ? -c * legForward : -c * legBack;
		m.rightLeg.y = 12.0f + bob;
		m.leftLeg.y = 12.0f + bob;
		m.rightLeg.z = 0.0f;
		m.leftLeg.z = 0.0f;
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, right);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, left);
		// the hips turn opposite the shoulders
		m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, -s * 0.05f * drive);
		m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, -s * 0.05f * drive);
		m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, 0.0f);
		m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, 0.0f);
	}
}
