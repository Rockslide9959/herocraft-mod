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
 * <ul>
 *   <li>Speed Mode: the classic sprint -- torso leaned ~27 degrees forward from the hips, head up looking ahead, arms
 *       pumping hard close to the body, long strides at twice vanilla's cadence.</li>
 *   <li>Overdrive: deeper (~40 degree) lean, arms swept straight back, strides faster still.</li>
 * </ul>
 * Blends in / out over ~4 ticks ({@link #tick}). Walking, standing, sneaking, swimming, riding and phasing keep
 * vanilla's animation, and a move animation ({@code MutationPose}) always wins. The first-person hand is left alone.
 *
 * <p>Model space: +Y is down, -Z is forward; the body leans from the hip point, so the neck (and the head and
 * shoulders with it) swings forward and down -- the same technique as {@code HulkPose}. Arm / leg X positive swings
 * the limb back.
 */
public final class SpeedRunPose {
	private static final float LEAN_SPEED = 0.48f;
	private static final float LEAN_OVERDRIVE = 0.70f;
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

	/** Called at the tail of {@code HumanoidModel.setupAnim}, before the mutation move poses. */
	public static void apply(Player player, HumanoidModel<?> m, float limbSwing) {
		if (MoonKnightPose.firstPersonHand) {
			return;
		}
		float w;
		boolean od;
		if (!Float.isNaN(overrideWeight)) {
			w = overrideWeight;
			od = overrideOverdrive;
		} else {
			if (moveAnimPlaying(player)) {
				return;
			}
			w = weight(player, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
			od = overdrive(player);
		}
		if (w <= 0.001f) {
			return;
		}
		w = w * w * (3f - 2f * w);
		float lean = (od ? LEAN_OVERDRIVE : LEAN_SPEED) * w;
		float phase = limbSwing * 0.6662f * (od ? 2.6f : 2.0f);
		float stride = Mth.cos(phase);

		// the body leans from the hips: the neck swings forward and down
		float neckY = 12.0f - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);
		m.body.xRot = lean;
		m.body.y = neckY;
		m.body.z = neckZ;
		m.body.yRot = Mth.lerp(w, m.body.yRot, Mth.sin(phase) * 0.08f);
		m.head.y = neckY;
		m.head.z = neckZ;
		m.hat.copyFrom(m.head);

		// shoulders 2 px below the neck, in the leaning body's frame
		float shoulderY = neckY + 2.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 2.0f * Mth.sin(lean);
		m.rightArm.y = shoulderY;
		m.leftArm.y = shoulderY;
		m.rightArm.z = shoulderZ;
		m.leftArm.z = shoulderZ;
		if (od) {
			// arms swept straight back, flickering
			float jitter = Mth.sin(phase * 1.5f) * 0.1f;
			m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, 1.15f + lean + jitter);
			m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, 1.15f + lean - jitter);
			m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, 0.0f);
			m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0.0f);
			m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.28f);
			m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.28f);
		} else {
			// arms pumping hard, opposite to the legs, held a little forward like bent elbows
			m.rightArm.xRot = Mth.lerp(w, m.rightArm.xRot, -stride * 1.15f - 0.3f + lean);
			m.leftArm.xRot = Mth.lerp(w, m.leftArm.xRot, stride * 1.15f - 0.3f + lean);
			m.rightArm.yRot = Mth.lerp(w, m.rightArm.yRot, -0.12f);
			m.leftArm.yRot = Mth.lerp(w, m.leftArm.yRot, 0.12f);
			m.rightArm.zRot = Mth.lerp(w, m.rightArm.zRot, 0.1f);
			m.leftArm.zRot = Mth.lerp(w, m.leftArm.zRot, -0.1f);
		}

		// long, fast strides
		float legAmp = od ? 1.15f : 1.0f;
		m.rightLeg.xRot = Mth.lerp(w, m.rightLeg.xRot, stride * legAmp - 0.1f * lean);
		m.leftLeg.xRot = Mth.lerp(w, m.leftLeg.xRot, -stride * legAmp - 0.1f * lean);
		m.rightLeg.yRot = Mth.lerp(w, m.rightLeg.yRot, 0.0f);
		m.leftLeg.yRot = Mth.lerp(w, m.leftLeg.yRot, 0.0f);
		m.rightLeg.zRot = Mth.lerp(w, m.rightLeg.zRot, 0.0f);
		m.leftLeg.zRot = Mth.lerp(w, m.leftLeg.zRot, 0.0f);
	}
}
