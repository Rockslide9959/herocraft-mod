package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.ironman.IronManFlightLook;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.21 flight revamp: Iron Man's own flight limb poses, the take-off crouch and the three-point superhero landing --
 * separate from the generic hero flight pose (other heroes are unchanged). The body lean still comes from
 * {@link FlightPoseHelper#lean} (hover 0 / slow 25 / sprint 90 / back -12 degrees); this class only poses the limbs
 * relative to the body:
 * <ul>
 *   <li><b>hover</b> -- arms held a little out from the body, hands (the repulsors) pointing down, legs slightly apart,
 *   with a small stabilising micro-motion;</li>
 *   <li><b>slow forward</b> -- arms angled back, palms back, legs trailing;</li>
 *   <li><b>sprint</b> -- with the 90-degree lean: arms tight back along the sides, palms back (the classic Iron Man
 *   pose), legs together;</li>
 *   <li><b>backward</b> -- arms thrown forward, palms out, braking; legs forward;</li>
 *   <li><b>take-off</b> -- a brief crouch (knees tucked, arms pulled back) released into the flight pose;</li>
 *   <li><b>hard landing</b> -- one knee and one fist down for ~0.6 s ({@link IronManFlightLook#isHardLanding}); soft
 *   landings just ease out of the pose.</li>
 * </ul>
 * The Mark 1 never leans and gets its own clunky upright stance (arms stiffly out for balance, a heavy sway and a
 * servo jitter). Repulsor Boots wearers (no suit) only get straight legs so the boot jets line up.
 *
 * <p>Every input is state the client has for <em>every</em> player it can see -- the synced flight flags, the synced
 * sprint flag, worn boots, the synced {@code TonyStarkState.supersonicUntil} and the interpolated position (speed and
 * landing speed come from per-tick position deltas, like {@link FlightPoseHelper}) -- so other players see the same
 * poses. All transitions are eased per tick and interpolated across the partial tick.
 */
public final class IronManFlightPose {
	public enum Kind { NONE, SUIT, MARK_ONE, BOOTS }

	/** Tier indices into the blend weights. */
	private static final int HOVER = 0;
	private static final int SLOW = 1;
	private static final int FAST = 2;
	private static final int BACK = 3;

	private static final double HOVER_SPEED_THRESHOLD = 0.03;
	private static final float TIER_EASE = 0.22f;
	private static final float BLEND_EASE = 0.25f;

	/** Model-space part pivots (px) of the default player model -- used by the thruster FX to place the jets. */
	public static final float ARM_PIVOT_X = 5.0f;
	public static final float ARM_PIVOT_Y = 2.0f;
	public static final float LEG_PIVOT_X = 1.9f;
	public static final float LEG_PIVOT_Y = 12.0f;

	private static final Map<UUID, Anim> ANIMS = new HashMap<>();

	private IronManFlightPose() {
	}

	/** Per-player animation + event state. Read by {@code IronManFlightFxClient} and the thruster sound. */
	public static final class Anim {
		public Kind kind = Kind.NONE;
		/** The last non-NONE kind -- what to keep fading out as after flight ends. */
		public Kind shownKind = Kind.NONE;
		public boolean flying;
		public boolean supersonic;
		public boolean moving;
		public boolean backward;
		public boolean sprinting;
		/** 3D speed, blocks/tick, from the position delta. */
		public double speed;
		public double vx;
		public double vy;
		public double vz;

		public long flightStart = Long.MIN_VALUE / 4;
		public long landStart = Long.MIN_VALUE / 4;

		// events raised this tick (consumed by the FX)
		public boolean evTakeoff;
		public boolean evHardLand;
		public boolean evSoftLand;
		public boolean evSupersonic;

		float blend;
		float blendPrev;
		final float[] w = { 1f, 0f, 0f, 0f };
		final float[] wPrev = { 1f, 0f, 0f, 0f };

		private double lastX;
		private double lastY;
		private double lastZ;
		private boolean hasLast;
		private final double[] speeds = new double[IronManFlightLook.LANDING_LOOKBACK_TICKS];
		private final double[] descents = new double[IronManFlightLook.LANDING_LOOKBACK_TICKS];
		private int histIndex;
		/** Ticks this player has been in view -- a flier that comes into range already airborne is not a take-off. */
		private int seenTicks;

		public IronManFlightLook.JetState jetState() {
			return IronManFlightLook.jetState(moving, backward, sprinting, supersonic);
		}
	}

	public static Anim anim(Player player) {
		return ANIMS.get(player.getUUID());
	}

	/** Once per client tick, for every player in the level (called from {@code IronManFlightFxClient}). */
	public static void tick(ClientLevel level) {
		if (level == null) {
			ANIMS.clear();
			return;
		}
		Set<UUID> present = new HashSet<>();
		long now = level.getGameTime();
		for (Player player : level.players()) {
			present.add(player.getUUID());
			tickPlayer(level, player, ANIMS.computeIfAbsent(player.getUUID(), id -> new Anim()), now);
		}
		ANIMS.keySet().retainAll(present);
	}

	public static void clear() {
		ANIMS.clear();
	}

	private static void tickPlayer(ClientLevel level, Player player, Anim a, long now) {
		a.evTakeoff = a.evHardLand = a.evSoftLand = a.evSupersonic = false;
		boolean settled = a.seenTicks >= 5;
		if (a.seenTicks < 1000) {
			a.seenTicks++;
		}

		double dx = a.hasLast ? player.getX() - a.lastX : 0.0;
		double dy = a.hasLast ? player.getY() - a.lastY : 0.0;
		double dz = a.hasLast ? player.getZ() - a.lastZ : 0.0;
		a.lastX = player.getX();
		a.lastY = player.getY();
		a.lastZ = player.getZ();
		a.hasLast = true;
		// a teleport / respawn is not motion
		if (dx * dx + dy * dy + dz * dz > 64.0) {
			dx = dy = dz = 0.0;
		}
		a.vx = dx;
		a.vy = dy;
		a.vz = dz;
		a.speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
		a.speeds[a.histIndex] = a.speed;
		a.descents[a.histIndex] = Math.max(0.0, -dy);
		a.histIndex = (a.histIndex + 1) % a.speeds.length;

		double horizontal = Math.hypot(dx, dz);
		float bodyYaw = player.yBodyRot * Mth.DEG_TO_RAD;
		double forwardSpeed = -dx * Mth.sin(bodyYaw) + dz * Mth.cos(bodyYaw);
		a.moving = horizontal >= HOVER_SPEED_THRESHOLD;
		a.backward = a.moving && DirectionalFlightModel.backward(horizontal, forwardSpeed);
		a.sprinting = player.isSprinting();

		Kind kind = kindOf(player);
		boolean wasFlying = a.flying;
		a.flying = kind != Kind.NONE;
		a.kind = kind;
		if (a.flying) {
			a.shownKind = kind;
		}

		if (a.flying && !wasFlying) {
			// just tracked / just joined mid-flight: no crouch, no burst
			a.flightStart = settled ? now : Long.MIN_VALUE / 4;
			a.evTakeoff = settled;
		} else if (!a.flying && wasFlying) {
			double peakSpeed = 0.0;
			double peakDescent = 0.0;
			for (int i = 0; i < a.speeds.length; i++) {
				peakSpeed = Math.max(peakSpeed, a.speeds[i]);
				peakDescent = Math.max(peakDescent, a.descents[i]);
			}
			boolean grounded = player.onGround()
					|| !level.noCollision(player, player.getBoundingBox().move(0.0, -0.5, 0.0));
			if (a.shownKind != Kind.BOOTS && IronManFlightLook.isHardLanding(peakSpeed, peakDescent, grounded)) {
				a.landStart = now;
				a.evHardLand = true;
			} else {
				a.evSoftLand = true;
			}
		}

		boolean supersonic = false;
		if (kind == Kind.SUIT || kind == Kind.MARK_ONE) {
			TonyStarkState ts = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
			supersonic = ts != null && ts.supersonicUntil > now;
		}
		if (supersonic && !a.supersonic && settled) {
			a.evSupersonic = true;
		}
		a.supersonic = supersonic;

		// tier blend weights
		int tier = !a.moving ? HOVER : a.backward ? BACK : a.sprinting || supersonic ? FAST : SLOW;
		for (int i = 0; i < 4; i++) {
			a.wPrev[i] = a.w[i];
			float target = i == tier ? 1f : 0f;
			a.w[i] += (target - a.w[i]) * TIER_EASE;
		}
		a.blendPrev = a.blend;
		float target = a.flying ? 1f : 0f;
		a.blend += (target - a.blend) * BLEND_EASE;
		if (Math.abs(a.blend - target) < 0.002f) {
			a.blend = target;
		}
	}

	public static Kind kindOf(Player player) {
		if (player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)) {
			IronManSuit suit = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof IronManArmorItem piece
					? IronManSuits.byId(piece.suitId()) : null;
			return suit != null && suit.noFlightLean() ? Kind.MARK_ONE : Kind.SUIT;
		}
		if (player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
			return Kind.BOOTS;
		}
		return Kind.NONE;
	}

	/** True while the superhero landing is playing -- {@link FlightPoseHelper} snaps the body lean upright for it. */
	public static boolean settleLean(Player player) {
		Anim a = ANIMS.get(player.getUUID());
		if (a == null || a.flying) {
			return false;
		}
		float t = player.level().getGameTime() - a.landStart;
		return t >= 0f && t < IronManFlightLook.LANDING_END;
	}

	// ---------------------------------------------------------------- the limb targets

	/**
	 * The flight limb rotations for {@code player} right now, written into {@code out} as
	 * {@code [rArm x,y,z, lArm x,y,z, rLeg x,y,z, lLeg x,y,z]} (radians, model space: +X rot swings a limb back, the
	 * right side's +Z swings it outward). Shared by the model pose and the thruster FX, so the jets come out of the
	 * rendered hands and boots.
	 */
	public static void targets(Anim a, float partial, float age, float[] out) {
		Kind kind = a.flying ? a.kind : a.shownKind;
		float wh = Mth.lerp(partial, a.wPrev[HOVER], a.w[HOVER]);
		float ws = Mth.lerp(partial, a.wPrev[SLOW], a.w[SLOW]);
		float wf = Mth.lerp(partial, a.wPrev[FAST], a.w[FAST]);
		float wb = Mth.lerp(partial, a.wPrev[BACK], a.w[BACK]);
		float sum = wh + ws + wf + wb;
		if (sum > 1.0e-4f) {
			wh /= sum;
			ws /= sum;
			wf /= sum;
			wb /= sum;
		} else {
			wh = 1f;
		}

		float armX;
		float armZ;
		float legRX;
		float legLX;
		float legZ;
		float armRY = 0f;
		if (kind == Kind.MARK_ONE) {
			// clunky prototype: upright, arms stiffly out for balance whatever it's doing
			armX = wh * -0.08f + ws * 0.22f + wf * 0.30f + wb * -0.55f;
			armZ = 0.55f - wf * 0.08f;
			legRX = ws * 0.06f + wf * 0.10f + wb * -0.12f;
			legLX = legRX;
			legZ = 0.045f;
		} else {
			armX = wh * -0.18f + ws * 0.55f + wf * 0.20f + wb * -1.30f;
			armZ = wh * 0.36f + ws * 0.20f + wf * 0.07f + wb * 0.30f;
			armRY = wb * -0.15f;
			legRX = wh * 0.04f + ws * 0.20f + wf * 0.04f + wb * -0.30f;
			legLX = wh * -0.02f + ws * 0.10f + wf * 0.04f + wb * -0.18f;
			legZ = wh * 0.07f + ws * 0.035f + wf * 0.015f + wb * 0.06f;
		}

		// micro-motion: stabilising corrections while hovering, a fine flutter at speed
		float rArmX = armX + Mth.sin(age * 0.12f) * 0.045f * wh + Mth.sin(age * 1.7f) * 0.010f * wf;
		float lArmX = armX + Mth.sin(age * 0.12f + 2.1f) * 0.045f * wh + Mth.sin(age * 1.7f + 1.0f) * 0.010f * wf;
		float rArmZ = armZ + Mth.sin(age * 0.16f + 1.3f) * 0.035f * wh;
		float lArmZ = armZ + Mth.sin(age * 0.16f + 0.4f) * 0.035f * wh;
		float rLegX = legRX + Mth.sin(age * 0.10f) * 0.04f * wh + Mth.sin(age * 1.9f) * 0.012f * wf;
		float lLegX = legLX + Mth.sin(age * 0.10f + 2.6f) * 0.04f * wh + Mth.sin(age * 1.9f + 2.0f) * 0.012f * wf;
		if (kind == Kind.MARK_ONE) {
			// a heavy side-to-side sway with a servo jitter on top
			float sway = Mth.sin(age * 0.07f) * 0.08f;
			float jitter = Mth.sin(age * 1.9f) * 0.02f + Mth.sin(age * 3.1f) * 0.012f;
			rArmZ += sway;
			lArmZ -= sway;
			rArmX += jitter;
			lArmX -= jitter;
		}

		// take-off: a brief crouch -- knees tucked, arms pulled back -- released into the flight pose
		float tFlight = a.flightStart == Long.MIN_VALUE / 4 ? 1.0e6f
				: (Minecraft.getInstance().level == null ? 1.0e6f
						: (Minecraft.getInstance().level.getGameTime() - a.flightStart) + partial);
		// v0.15.1, explicit user request: no take-off crouch (it read as a two-leg kick) -- straight into the flight pose
		float crouch = 0f * tFlight;

		out[0] = Mth.lerp(crouch, rArmX, 0.45f);
		out[1] = Mth.lerp(crouch, armRY, 0f);
		out[2] = Mth.lerp(crouch, rArmZ, 0.30f);
		out[3] = Mth.lerp(crouch, lArmX, 0.45f);
		out[4] = Mth.lerp(crouch, -armRY, 0f);
		out[5] = Mth.lerp(crouch, -lArmZ, -0.30f);
		out[6] = Mth.lerp(crouch, rLegX, -0.95f);
		out[7] = 0f;
		out[8] = Mth.lerp(crouch, legZ, 0.10f);
		out[9] = Mth.lerp(crouch, lLegX, -0.85f);
		out[10] = 0f;
		out[11] = Mth.lerp(crouch, -legZ, -0.10f);
		if (kind == Kind.BOOTS) {
			// no suit: just straight legs, slightly apart, so the boot jets point where they should
			out[6] = 0.02f + Mth.sin(age * 0.10f) * 0.03f * wh;
			out[8] = 0.05f;
			out[9] = 0.02f + Mth.sin(age * 0.10f + 2.6f) * 0.03f * wh;
			out[11] = -0.05f;
		}
	}

	private static final float[] SCRATCH = new float[12];

	/** From {@code HumanoidModelMixin}, after the generic flight pose and before the move poses. */
	public static void apply(Player player, HumanoidModel<?> m, float ageInTicks) {
		Anim a = ANIMS.get(player.getUUID());
		if (a == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float blend = Mth.lerp(partial, a.blendPrev, a.blend);
		if (blend > 0.001f && a.shownKind != Kind.NONE) {
			float[] t = SCRATCH;
			targets(a, partial, ageInTicks, t);
			boolean boots = (a.flying ? a.kind : a.shownKind) == Kind.BOOTS;
			if (!boots) {
				set(m.rightArm, blend, t[0], t[1], t[2]);
				set(m.leftArm, blend, t[3], t[4], t[5]);
			}
			set(m.rightLeg, blend, t[6], t[7], t[8]);
			set(m.leftLeg, blend, t[9], t[10], t[11]);
		}
		float tLand = (player.level().getGameTime() - a.landStart) + partial;
		float k = a.flying ? 0f : IronManFlightLook.landingWeight(tLand);
		if (k > 0f) {
			applyLanding(m, k, tLand);
		}
	}

	private static void set(ModelPart p, float w, float x, float y, float z) {
		p.xRot = Mth.lerp(w, p.xRot, x);
		p.yRot = Mth.lerp(w, p.yRot, y);
		p.zRot = Mth.lerp(w, p.zRot, z);
	}

	/**
	 * The three-point superhero landing: right knee and right fist on the ground, left foot planted forward, left arm
	 * swept out behind, head bowed then lifting just before he rises. Moves bones as well as rotating them (hips drop,
	 * the torso pivots forward around them) -- same construction as {@code StrengthLandingPose}.
	 */
	private static void applyLanding(HumanoidModel<?> m, float k, float t) {
		float in = IronManFlightLook.LANDING_IN;
		float bounce = t > in && t < in + 3f ? Mth.sin((t - in) / 3f * Mth.PI) * 1.3f : 0f;
		float drop = 6.0f * k + bounce;
		float lean = 0.95f * k;
		float hipY = 12.0f + drop;
		float neckY = hipY - 12.0f * Mth.cos(lean);
		float neckZ = -12.0f * Mth.sin(lean);

		m.body.xRot = lean;
		m.body.yRot = Mth.lerp(k, m.body.yRot, 0.10f);
		m.body.y = neckY;
		m.body.z = neckZ;

		float look = t < 10f ? 0.55f : Mth.lerp(IronManFlightLook.smooth((t - 10f) / 3f), 0.55f, -0.3f);
		m.head.y = neckY;
		m.head.z = neckZ;
		m.head.xRot = Mth.lerp(k, m.head.xRot, look);
		m.head.yRot = Mth.lerp(k, m.head.yRot, 0.0f);
		m.head.zRot = 0.0f;
		m.hat.copyFrom(m.head);

		float shoulderY = neckY + 2.0f * Mth.cos(lean);
		float shoulderZ = neckZ + 2.0f * Mth.sin(lean);
		m.rightArm.y = Mth.lerp(k, m.rightArm.y, shoulderY);
		m.leftArm.y = Mth.lerp(k, m.leftArm.y, shoulderY);
		m.rightArm.z = Mth.lerp(k, m.rightArm.z, shoulderZ);
		m.leftArm.z = Mth.lerp(k, m.leftArm.z, shoulderZ);
		// right fist driven into the ground; left arm swept out and back for balance
		m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -0.12f);
		m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, 0.0f);
		m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0.08f);
		m.leftArm.xRot = Mth.lerp(k, m.leftArm.xRot, 0.35f);
		m.leftArm.yRot = Mth.lerp(k, m.leftArm.yRot, 0.0f);
		m.leftArm.zRot = Mth.lerp(k, m.leftArm.zRot, -0.85f);

		m.rightLeg.y = hipY;
		m.leftLeg.y = hipY;
		m.rightLeg.z = Mth.lerp(k, m.rightLeg.z, 0.8f);
		m.leftLeg.z = Mth.lerp(k, m.leftLeg.z, -0.8f);
		m.rightLeg.xRot = Mth.lerp(k, m.rightLeg.xRot, 1.047f);
		m.leftLeg.xRot = Mth.lerp(k, m.leftLeg.xRot, -0.85f);
		m.rightLeg.yRot = Mth.lerp(k, m.rightLeg.yRot, 0.0f);
		m.leftLeg.yRot = Mth.lerp(k, m.leftLeg.yRot, -0.1f);
		m.rightLeg.zRot = Mth.lerp(k, m.rightLeg.zRot, 0.06f);
		m.leftLeg.zRot = Mth.lerp(k, m.leftLeg.zRot, -0.1f);
	}
}
