package com.projecthero.mod.client.quirk.engine;

import static com.projecthero.mod.client.quirk.QuirkKeyframes.f;
import static com.projecthero.mod.client.quirk.QuirkKeyframes.rest;

import com.projecthero.mod.client.HipBend;
import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.engine.Engine;
import com.projecthero.mod.quirk.engine.EngineConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (revamp): Ingenium's poses on the vanilla humanoid model, set at the end of {@code setupAnim}. Two layers:
 * <ul>
 *   <li><b>The run</b> (procedural, from the synced gear / momentum / Recipro Turbo): a racer's forward lean that deepens with
 *       each gear and with the momentum, long driving strides, arms pumping straight like Iida's; the skid of a sliding
 *       turn (braced leg, arms out for balance); the stall (slumped, head down).</li>
 *   <li><b>The moves</b> (keyframed, {@link QuirkKeyframes}, from the synced {@code animId / animStart}, ids 60..79): the
 *       gear-change clutch and brake, the Recipro Turbo sprinter's set and launch, the dash, the speed kick, the spin
 *       kick, the kick barrage, the charged Extend Kick, the flip Rising Kick, the axe kick, the low sweep, the Turbo
 *       Strike (a flying kick) and the three-part finisher.</li>
 * </ul>
 * The whole-body turns (the spin / sweep / finisher turns, the Rising Kick's back flip) are {@link #bodyTurn}, applied by
 * {@code QuirkBodyTurnMixin}.
 */
public final class EnginePose {
	// ---------------------------------------------------------------- keyframes
	//                                           tick  rArm x y z          lArm x y z          body x y        rLeg x y z           lLeg x y z           head
	/** V: Gear Up -- a quick clutch: drop the hips, one arm pumps back, then drive forward as the gear bites. */
	static final float[][] SHIFT_UP = {
			rest(0),
			f(2, 0.9f, 0, 0.15f, -0.7f, 0, -0.15f, 0.45f, 0, -0.5f, 0, 0, 0.55f, 0, 0, -0.2f),
			f(4, -1.1f, 0, 0.1f, 0.9f, 0, -0.1f, 0.55f, 0, 0.55f, 0, 0, -0.75f, 0, 0, -0.3f),
			rest(9) };
	/** Shift+V: Gear Down -- leaning back on the brakes, arms out, the lead leg braced. */
	static final float[][] SHIFT_DOWN = {
			rest(0),
			f(3, -0.35f, 0, 0.55f, -0.35f, 0, -0.55f, -0.22f, 0, -0.55f, 0, 0.05f, 0.25f, 0, -0.05f, 0.15f),
			rest(9) };
	/**
	 * C: Recipro Turbo engaged -- a sprinter's set held low while the calf engines ignite blue and scream, then the
	 * explosive launch into the run.
	 */
	static final float[][] TURBO_ON = {
			rest(0),
			f(3, 1.0f, 0, 0.3f, 1.0f, 0, -0.3f, 0.85f, 0, -1.1f, 0, 0.05f, 0.6f, 0, -0.05f, -0.55f),
			f(9, 1.05f, 0, 0.35f, 1.05f, 0, -0.35f, 0.9f, 0, -1.15f, 0, 0.05f, 0.65f, 0, -0.05f, -0.6f),
			f(11, -1.5f, 0, 0.1f, 1.25f, 0, -0.1f, 0.6f, 0, 0.9f, 0, 0, -1.4f, 0, 0, -0.4f),
			f(14, 1.1f, 0, 0.1f, -1.35f, 0, -0.1f, 0.55f, 0, -1.25f, 0, 0, 0.9f, 0, 0, -0.35f),
			rest(19) };
	/** R: Recipro Kick -- chamber, then a straight driving kick at chest height, the guard up, leaning back into it. */
	static final float[][] KICK = {
			rest(0),
			f(2, -1.1f, -0.3f, 0, -1.25f, 0.35f, 0, 0.1f, 0.25f, -0.9f, 0, 0.1f, 0.2f, 0, 0, 0),
			f(4, -1.2f, -0.35f, 0, 0.6f, 0.2f, -0.3f, -0.35f, 0.35f, -1.85f, -0.15f, 0.1f, 0.3f, 0, -0.05f, 0.15f),
			f(7, -1.2f, -0.35f, 0, 0.6f, 0.2f, -0.3f, -0.3f, 0.3f, -1.75f, -0.15f, 0.1f, 0.3f, 0, -0.05f, 0.1f),
			rest(11) };
	/** Shift+R: Spin Kick -- the kicking leg swung out level, arms flung wide, while the body whips round (bodyTurn). */
	static final float[][] SPIN = {
			rest(0),
			f(2, -0.2f, 0, 0.9f, -0.2f, 0, -0.9f, 0.15f, 0.6f, 0.2f, 0, 0, -0.15f, 0, 0, 0),
			f(4, 0f, 0, 1.4f, 0f, 0, -1.4f, -0.15f, 0, -1.45f, 0, 1.0f, 0.15f, 0, -0.1f, 0),
			f(10, 0f, 0, 1.4f, 0f, 0, -1.4f, -0.15f, 0, -1.45f, 0, 1.0f, 0.15f, 0, -0.1f, 0),
			rest(14) };
	/** The stall: a stumble as the engines seize (then the slumped hold of {@link #applyRun}). */
	static final float[][] STALL = {
			rest(0),
			f(3, -1.0f, 0, 0.8f, -0.4f, 0, -1.0f, 0.6f, 0.2f, -0.6f, 0, 0.1f, 0.5f, 0, 0, 0.3f),
			f(8, 0.5f, 0, 0.2f, 0.5f, 0, -0.2f, 0.4f, 0, 0.1f, 0, 0.05f, -0.2f, 0, -0.05f, 0.45f),
			rest(14) };
	/** X: Recipro Dash -- flat out: the body thrown low and forward, arms swept back, one long stride frozen in the burst. */
	static final float[][] DASH = {
			rest(0),
			f(1, 0.9f, 0, 0.25f, 0.9f, 0, -0.25f, 0.75f, 0, -0.9f, 0, 0, 0.7f, 0, 0, -0.6f),
			f(EngineConfig.DASH_TICKS + 1, 1.05f, 0, 0.35f, 1.05f, 0, -0.35f, 0.85f, 0, -1.05f, 0, 0, 0.85f, 0, 0, -0.7f),
			f(EngineConfig.DASH_TICKS + 4, -0.5f, 0, 0.3f, 0.6f, 0, -0.3f, 0.45f, 0, 0.6f, 0, 0, -0.7f, 0, 0, -0.35f),
			rest(EngineConfig.DASH_TICKS + 9) };
	/** Shift+X: Rising Kick -- a crouch, then a flip kick: the leg swung straight up through the target as he flips back. */
	static final float[][] RISING = {
			rest(0),
			f(2, 0.7f, 0, 0.3f, 0.7f, 0, -0.3f, 0.45f, 0, -0.5f, 0, 0, -0.4f, 0, 0, -0.2f),
			f(5, 0.9f, 0, 0.9f, 0.9f, 0, -0.9f, -0.3f, 0, -2.8f, 0, 0.1f, 0.35f, 0, 0, 0.2f),
			f(9, 0.6f, 0, 0.8f, 0.6f, 0, -0.8f, -0.1f, 0, -1.6f, 0, 0.05f, -0.6f, 0, 0, 0.1f),
			rest(15) };
	/** G: Kick Barrage -- the legs alternate, six snapping kicks, the guard up the whole time. */
	static final float[][] BARRAGE = barrageFrames();
	/** Shift+G: Recipro Extend Kick -- coiled low with the kick leg drawn far back while the engines charge, then the strike. */
	static final float[][] EXTEND_KICK = {
			rest(0),
			f(3, -1.0f, -0.3f, 0.2f, 0.9f, 0, -0.5f, 0.55f, 0.35f, 1.1f, 0, 0.1f, -0.45f, 0, 0, -0.45f),
			f(EngineConfig.EXTEND_KICK_CHARGE, -1.05f, -0.35f, 0.25f, 0.95f, 0, -0.55f, 0.6f, 0.4f, 1.2f, 0, 0.1f, -0.5f, 0, 0, -0.5f),
			f(EngineConfig.EXTEND_KICK_CHARGE + 2, 0.7f, 0, 0.6f, -1.1f, 0.3f, -0.3f, -0.35f, -0.3f, -1.95f, -0.1f, 0.1f, 0.35f, 0, 0, 0.2f),
			f(EngineConfig.EXTEND_KICK_CHARGE + 6, 0.7f, 0, 0.6f, -1.1f, 0.3f, -0.3f, -0.3f, -0.3f, -1.85f, -0.1f, 0.1f, 0.35f, 0, 0, 0.15f),
			rest(EngineConfig.EXTEND_KICK_CHARGE + 11) };
	/** Z: Axe Kick -- the leg swung straight up overhead, leaning back, then the heel brought crashing down. */
	static final float[][] AXE = {
			rest(0),
			f(4, -0.6f, 0, 0.9f, -0.6f, 0, -0.9f, -0.35f, 0, -2.85f, 0, 0.05f, 0.2f, 0, 0, -0.3f),
			f(EngineConfig.AXE_DELAY, -0.7f, 0, 0.8f, -0.7f, 0, -0.8f, 0.55f, 0, -0.35f, 0, 0.05f, 0.35f, 0, 0, 0.4f),
			f(EngineConfig.AXE_DELAY + 5, -0.6f, 0, 0.7f, -0.6f, 0, -0.7f, 0.5f, 0, -0.3f, 0, 0.05f, 0.35f, 0, 0, 0.35f),
			rest(EngineConfig.AXE_DELAY + 10) };
	/** Shift+Z: Sweep Kick -- dropped low on the standing leg, hands near the floor, the other leg swept out level (a half turn). */
	static final float[][] SWEEP = {
			rest(0),
			f(2, -0.9f, 0, 0.5f, -0.9f, 0, -0.5f, 0.7f, 0, -0.5f, 0, 0.4f, -0.9f, 0, 0, -0.4f),
			f(4, -1.1f, 0, 0.7f, -1.1f, 0, -0.7f, 0.8f, 0, -0.3f, 0, 1.35f, -1.0f, 0, 0, -0.5f),
			f(9, -1.1f, 0, 0.7f, -1.1f, 0, -0.7f, 0.8f, 0, -0.3f, 0, 1.35f, -1.0f, 0, 0, -0.5f),
			rest(14) };
	/** X in Turbo: the Turbo Strike -- a low set, then the rush as one flying kick: the lead leg thrust ahead, arms swept back. */
	static final float[][] STRIKE = {
			rest(0),
			f(2, 1.0f, 0, 0.3f, 1.0f, 0, -0.3f, 0.9f, 0, -0.4f, 0, 0, 0.8f, 0, 0, -0.55f),
			f(4, 1.15f, 0, 0.45f, 1.15f, 0, -0.45f, 0.25f, 0, -1.55f, 0, 0.05f, 0.95f, 0, -0.05f, -0.15f),
			f(EngineConfig.STRIKE_TICKS + 3, 1.15f, 0, 0.45f, 1.15f, 0, -0.45f, 0.25f, 0, -1.55f, 0, 0.05f, 0.95f, 0, -0.05f, -0.15f),
			f(EngineConfig.STRIKE_TICKS + 7, -0.4f, 0, 0.5f, -0.4f, 0, -0.5f, 0.55f, 0, -0.5f, 0, 0.1f, 0.4f, 0, -0.1f, 0.2f),
			rest(EngineConfig.STRIKE_TICKS + 12) };
	/** Shift+C: Ingenium Finisher -- a straight kick, a spinning kick (whole-body turn), then the high afterburner kick. */
	static final float[][] FINISHER = {
			rest(0),
			f(2, -1.1f, -0.3f, 0, 0.5f, 0.2f, -0.3f, -0.3f, 0.3f, -1.8f, -0.1f, 0.1f, 0.3f, 0, 0, 0.1f),
			f(5, -0.9f, -0.3f, 0, -1.0f, 0.3f, 0, 0.1f, 0, -0.3f, 0, 0, 0.2f, 0, 0, 0),
			f(EngineConfig.FINISHER_SPIN_AT, 0f, 0, 1.4f, 0f, 0, -1.4f, -0.15f, 0, -1.45f, 0, 1.0f, 0.15f, 0, -0.1f, 0),
			f(EngineConfig.FINISHER_SPIN_AT + 4, 0f, 0, 1.4f, 0f, 0, -1.4f, -0.15f, 0, -1.45f, 0, 1.0f, 0.15f, 0, -0.1f, 0),
			f(EngineConfig.FINISHER_LAUNCH_AT - 1, -0.9f, -0.2f, 0.2f, -1.0f, 0.2f, -0.2f, 0.25f, -0.3f, 0.65f, 0, 0, -0.1f, 0, 0, -0.1f),
			f(EngineConfig.FINISHER_LAUNCH_AT + 1, 0.5f, 0, 0.65f, -1.0f, 0.2f, -0.45f, -0.4f, 0.25f, -2.4f, 0, 0.12f, 0.25f, 0, 0, 0.25f),
			f(EngineConfig.FINISHER_LAUNCH_AT + 4, 0.5f, 0, 0.65f, -1.0f, 0.2f, -0.45f, -0.35f, 0.2f, -2.3f, 0, 0.12f, 0.25f, 0, 0, 0.2f),
			rest(EngineConfig.FINISHER_LAUNCH_AT + 9) };

	private static float[][] barrageFrames() {
		int hits = EngineConfig.BARRAGE_HITS;
		int iv = EngineConfig.BARRAGE_INTERVAL;
		float[][] out = new float[hits * 2 + 2][];
		out[0] = rest(0);
		for (int i = 0; i < hits; i++) {
			boolean right = i % 2 == 0;
			float hi = -1.5f - 0.15f * (i % 3);
			float t = 1 + i * iv;
			out[1 + i * 2] = f(t, -1.25f, -0.3f, 0, -1.3f, 0.3f, 0, right ? -0.25f : -0.2f, right ? 0.25f : -0.25f,
					right ? hi : 0.25f, 0, right ? 0.08f : 0, right ? 0.25f : hi, 0, right ? 0 : -0.08f, 0.1f);
			out[2 + i * 2] = f(t + iv * 0.6f, -1.2f, -0.3f, 0, -1.25f, 0.3f, 0, 0.05f, 0, -0.2f, 0, 0, -0.2f, 0, 0, 0);
		}
		out[hits * 2 + 1] = rest(1 + hits * iv + 5);
		return out;
	}

	private EnginePose() {
	}

	static float[][] frames(int animId) {
		return switch (animId) {
			case Engine.ANIM_SHIFT_UP -> SHIFT_UP;
			case Engine.ANIM_SHIFT_DOWN -> SHIFT_DOWN;
			case Engine.ANIM_TURBO_ON -> TURBO_ON;
			case Engine.ANIM_KICK -> KICK;
			case Engine.ANIM_SPIN -> SPIN;
			case Engine.ANIM_DASH -> DASH;
			case Engine.ANIM_RISING -> RISING;
			case Engine.ANIM_BARRAGE -> BARRAGE;
			case Engine.ANIM_EXTEND_KICK -> EXTEND_KICK;
			case Engine.ANIM_AXE -> AXE;
			case Engine.ANIM_SWEEP -> SWEEP;
			case Engine.ANIM_STRIKE -> STRIKE;
			case Engine.ANIM_FINISHER -> FINISHER;
			case Engine.ANIM_STALL -> STALL;
			default -> null;
		};
	}

	/** Test / harness: the pose exists for this id. */
	public static boolean hasPose(int animId) {
		return frames(animId) != null;
	}

	private static float partial() {
		return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
	}

	/** Ticks into the synced pose, or -1 if no Engine pose is set. */
	private static float age(Player player, QuirkState s, float partial) {
		if (s == null || s.animId < 60 || s.animId > 79) {
			return -1f;
		}
		return player.level().getGameTime() - s.animStart + partial;
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Engine.is(player)) {
			return;
		}
		float partial = partial();
		applyRun(player, model, partial);
		QuirkState s = Quirks.peek(player);
		float age = age(player, s, partial);
		float[][] fr = age < 0f ? null : frames(s.animId);
		if (fr != null) {
			QuirkKeyframes.play(model, fr, age, 1f);
		}
		model.hat.copyFrom(model.head);
	}

	/** The procedural run layer: gear lean and strides, skid, wall run, stall, carry. */
	private static void applyRun(Player player, HumanoidModel<?> model, float partial) {
		ModelPart r = model.rightArm, l = model.leftArm;
		long now = player.level().getGameTime();
		float t = player.tickCount + partial;
		if (Engine.stalled(player)) {
			// seized: slumped forward, arms hanging, head down
			HipBend.pitch(model, 1f, 0.35f);
			model.head.xRot = 0.5f;
			pose(r, 1f, 0.35f + 0.05f * Mth.sin(t * 0.2f), 0f, 0.12f);
			pose(l, 1f, 0.35f - 0.05f * Mth.sin(t * 0.2f), 0f, -0.12f);
			return;
		}
		int gear = Engine.gear(player);
		float mom = Engine.momentum(player);
		float drive = Engine.drive(player);
		float speed = (float) Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
		boolean grounded = player.onGround();
		long slide = Engine.val(player, Engine.V_SLIDE);
		float sinceSlide = slide == 0L ? 99f : now - slide + partial;
		if (gear > 0 && sinceSlide < 9f && grounded) {
			// the skid: braced on the outside leg, leaning back into the turn, arms out for balance
			float w = Math.min(1f, sinceSlide / 2f) * Math.min(1f, (9f - sinceSlide) / 4f);
			HipBend.pitch(model, w, -0.15f);
			model.body.yRot = Mth.lerp(w, model.body.yRot, 0.35f);
			pose(r, w, -0.6f, 0f, 1.05f);
			pose(l, w, -0.3f, 0f, -0.75f);
			legPose(model.rightLeg, w, -0.75f, 0f, 0.25f);
			legPose(model.leftLeg, w, 0.35f, 0f, -0.15f);
			model.head.xRot = Mth.lerp(w, model.head.xRot, 0.1f);
			return;
		}
		if (drive > 0f && speed > 0.12f && grounded && !player.isCrouching() && !player.isSwimming()) {
			// a racer's run: the lean deepens with the gear and the momentum; long strides; straight arms driving
			float lean = 0.18f + 0.09f * Math.min(4f, drive) + 0.12f * mom;
			float freq = 0.85f + 0.12f * Math.min(4f, drive);
			float sw = Mth.sin(t * freq);
			float stride = 0.95f + 0.12f * Math.min(4f, drive);
			HipBend.pitch(model, 1f, lean);
			model.head.xRot = Mth.lerp(0.7f, model.head.xRot, -lean * 0.9f);
			pose(r, 1f, 0.95f * sw - 0.1f, 0f, 0.08f);
			pose(l, 1f, -0.95f * sw - 0.1f, 0f, -0.08f);
			model.rightLeg.xRot = -stride * sw;
			model.leftLeg.xRot = stride * sw;
		}
	}

	// ---------------------------------------------------------------- whole-body turns (QuirkBodyTurnMixin)

	private static float eased(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** {@code [yaw spin, pitch flip]} in degrees for the whole body (Spin Kick: one turn; the wall kick: a back flip). */
	public static float[] bodyTurn(Player player, float partial) {
		if (!Engine.is(player)) {
			return null;
		}
		QuirkState s = Quirks.peek(player);
		float age = age(player, s, partial);
		if (age < 0f) {
			return null;
		}
		if (s.animId == Engine.ANIM_SPIN) {
			float t = (age - 2f) / 8f;
			return t <= 0f || t >= 1f ? null : new float[] { -360f * eased(t), 0f };
		}
		if (s.animId == Engine.ANIM_SWEEP) {
			// the sweep carries him round a full turn, low
			float t = (age - 1f) / 9f;
			return t <= 0f || t >= 1f ? null : new float[] { -360f * eased(t), 0f };
		}
		if (s.animId == Engine.ANIM_FINISHER) {
			float t = (age - (EngineConfig.FINISHER_SPIN_AT - 1)) / 6f;
			return t <= 0f || t >= 1f ? null : new float[] { -360f * eased(t), 0f };
		}
		if (s.animId == Engine.ANIM_RISING) {
			// the flip kick: a back flip as the leg swings up through the target
			float t = (age - 3f) / 10f;
			return t <= 0f || t >= 1f ? null : new float[] { 0f, -360f * eased(t) };
		}
		return null;
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	private static void legPose(ModelPart part, float w, float x, float y, float z) {
		pose(part, w, x, y, z);
	}
}
