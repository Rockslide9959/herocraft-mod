package com.projecthero.mod.client.quirk.engine;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.engine.Engine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Engine's keyframed poses on the vanilla humanoid model, set at the end of {@code setupAnim}. Driven by the
 * synced {@code animId / animStart} (his ids 60..79) and his gear timers, so every viewer sees the same thing. Negative
 * {@code xRot} swings a limb forward / up; positive {@code zRot} lifts the right arm (or leg) out to the side.
 * <ul>
 *   <li><b>Running in gear</b>: a racer's lean, long strides, arms driving (deeper with each gear);</li>
 *   <li><b>Recipro Kick</b>: right-left-right kicks, the guard up; <b>Spin Kick</b>: one leg swept round, arms wide;</li>
 *   <li><b>Burst / Gear Up</b>: a low crouch as the engines rev; <b>Engine Leap</b>: arms flung up, legs trailing;</li>
 *   <li><b>Air Dash</b>: flat out, arms back; <b>Recipro Extend</b>: the kick leg thrust ahead the whole dash;</li>
 *   <li><b>Afterburner</b>: a high snapping kick; <b>Rescue Carry</b>: arms up holding the load on his shoulders;</li>
 *   <li><b>Rescue Throw</b>: both arms heave forward; <b>Cool Down</b>: arms down and out, head bowed in the steam;</li>
 *   <li><b>Stall</b>: slumped.</li>
 * </ul>
 */
public final class EnginePose {
	private EnginePose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case Engine.ANIM_KICK -> 13;
			case Engine.ANIM_SPIN -> 10;
			case Engine.ANIM_BURST, Engine.ANIM_GEAR, Engine.ANIM_TURBO -> 10;
			case Engine.ANIM_LEAP -> 14;
			case Engine.ANIM_AIR_DASH -> 10;
			case Engine.ANIM_EXTEND -> 10;
			case Engine.ANIM_AFTERBURNER -> 12;
			case Engine.ANIM_CARRY -> 8;
			case Engine.ANIM_THROW -> 10;
			case Engine.ANIM_COOL -> 30;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		QuirkState s = Quirks.peek(player);
		if (s == null || s.animId < 60 || s.animId > 79 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Engine.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float ageTicks = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		float speed = (float) Math.hypot(player.getX() - player.xo, player.getZ() - player.zo);
		boolean grounded = player.onGround();
		if (Engine.stalled(player)) {
			lean(model, 0.35f);
			model.head.xRot = 0.45f;
			pose(r, 0.8f, -0.25f, 0f, 0.15f);
			pose(l, 0.8f, -0.25f, 0f, -0.15f);
		} else {
			int gear = Engine.gear(player);
			if (gear > 0 && speed > 0.15f && grounded && !player.isCrouching()) {
				// a racer's lean, deeper in each gear; long strides, the arms driving
				float lean = 0.25f + 0.12f * gear;
				float sw = Mth.sin(ageTicks * (0.9f + 0.15f * gear));
				lean(model, lean);
				model.head.xRot = Mth.lerp(0.6f, model.head.xRot, -lean * 0.8f);
				pose(r, 0.85f, 0.9f * sw, 0f, 0.12f);
				pose(l, 0.85f, -0.9f * sw, 0f, -0.12f);
				model.rightLeg.xRot = -1.2f * sw;
				model.leftLeg.xRot = 1.2f * sw;
			}
			if (Engine.carrying(player)) {
				// the load on his shoulders: both hands up holding it
				pose(r, 1f, -2.75f, 0f, 0.3f);
				pose(l, 1f, -2.75f, 0f, -0.3f);
			}
			if (Engine.running(player, Engine.V_EXTEND)) {
				// Recipro Extend: the kick leg thrust ahead the whole dash
				lean(model, -0.2f);
				model.rightLeg.xRot = -1.45f;
				model.leftLeg.xRot = 0.55f;
				pose(r, 1f, 0.7f, 0f, 0.35f);
				pose(l, 1f, 0.7f, 0f, -0.35f);
			}
		}
		float[] p = playing(player, partial);
		if (p == null) {
			model.hat.copyFrom(model.head);
			return;
		}
		int id = (int) p[0];
		float age = p[1];
		int len = length(id);
		float w = Math.min(1f, age / 2f) * Math.min(1f, (len - age) / 3f);
		switch (id) {
			case Engine.ANIM_KICK -> {
				// right, left, right -- each kick snaps out and back over four ticks
				int k = Math.min(2, (int) (age / 4f));
				float t = (age - k * 4f) / 4f;
				float snap = Mth.sin(Mth.clamp(t, 0f, 1f) * (float) Math.PI);
				ModelPart leg = k == 1 ? model.leftLeg : model.rightLeg;
				leg.xRot = Mth.lerp(w, leg.xRot, -1.7f * snap - (k == 2 ? 0.3f * snap : 0f));
				pose(r, w, -1.3f, -0.3f, 0f);
				pose(l, w, -1.3f, 0.3f, 0f);
				lean(model, -0.12f * w);
			}
			case Engine.ANIM_SPIN -> {
				// one leg swept round at hip height, arms flung wide for balance
				float t = age / len;
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -1.4f);
				model.rightLeg.zRot = Mth.lerp(w, model.rightLeg.zRot, 0.9f);
				model.rightLeg.yRot = Mth.lerp(w, model.rightLeg.yRot, Mth.lerp(t, 1.4f, -1.4f));
				pose(r, w, 0f, 0f, 1.3f);
				pose(l, w, 0f, 0f, -1.3f);
			}
			case Engine.ANIM_BURST, Engine.ANIM_GEAR, Engine.ANIM_TURBO -> {
				// a sprinter's set as the engines rev
				float d = id == Engine.ANIM_TURBO ? 0.75f : id == Engine.ANIM_GEAR ? 0.6f : 0.5f;
				lean(model, d * w);
				pose(r, w, 0.9f, 0f, 0.2f);
				pose(l, w, -0.8f, 0f, -0.2f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.6f);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.7f);
			}
			case Engine.ANIM_LEAP -> {
				// arms flung up, legs trailing the exhaust
				pose(r, w, -2.9f, 0f, 0.35f);
				pose(l, w, -2.9f, 0f, -0.35f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, 0.45f);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.35f);
			}
			case Engine.ANIM_AIR_DASH -> {
				lean(model, 0.7f * w);
				pose(r, w, 0.9f, 0f, 0.3f);
				pose(l, w, 0.9f, 0f, -0.3f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, 0.6f);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.5f);
			}
			case Engine.ANIM_EXTEND -> {
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -1.45f);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.55f);
			}
			case Engine.ANIM_AFTERBURNER -> {
				// a high snapping kick, the engines flaring
				float t = Mth.clamp((age - 1f) / 3f, 0f, 1f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, Mth.lerp(ease(t), 0.5f, -2.3f));
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.2f);
				pose(r, w, 0.4f, 0f, 0.6f);
				pose(l, w, -1.0f, 0.2f, -0.4f);
				lean(model, -0.25f * w);
			}
			case Engine.ANIM_CARRY -> {
				float t = ease(Mth.clamp(age / 4f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -1.2f, -2.75f), 0f, 0.3f);
				pose(l, w, Mth.lerp(t, -1.2f, -2.75f), 0f, -0.3f);
			}
			case Engine.ANIM_THROW -> {
				float t = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(t, -2.9f, -1.3f), 0f, 0.15f);
				pose(l, w, Mth.lerp(t, -2.9f, -1.3f), 0f, -0.15f);
				lean(model, Mth.lerp(t, -0.15f, 0.3f) * w);
			}
			case Engine.ANIM_COOL -> {
				// arms down and out, head bowed in the steam
				pose(r, w, -0.15f, 0f, 0.55f);
				pose(l, w, -0.15f, 0f, -0.55f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.5f);
				model.rightLeg.zRot = Mth.lerp(w, model.rightLeg.zRot, 0.12f);
				model.leftLeg.zRot = Mth.lerp(w, model.leftLeg.zRot, -0.12f);
			}
			default -> {
			}
		}
		model.hat.copyFrom(model.head);
	}

	/**
	 * Leans the body forward (or back, negative). The body pivots at the neck, so the hips -- and the legs with them --
	 * swing back (and up) by the torso's 12 px.
	 */
	private static void lean(HumanoidModel<?> model, float lean) {
		if (model.crouching) {
			return;
		}
		model.body.xRot = lean;
		float dz = Mth.sin(lean) * 12f;
		float dy = (1f - Mth.cos(lean)) * 12f;
		model.rightLeg.z = 0.1f + dz;
		model.leftLeg.z = 0.1f + dz;
		model.rightLeg.y = 12f - dy;
		model.leftLeg.y = 12f - dy;
	}

	private static float ease(float t) {
		return t * t * (3f - 2f * t);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
