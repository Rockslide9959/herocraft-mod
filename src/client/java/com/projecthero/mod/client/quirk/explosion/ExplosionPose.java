package com.projecthero.mod.client.quirk.explosion;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.explosion.Explosion;
import com.projecthero.mod.quirk.explosion.ExplosionConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Bakugo's keyframed poses, set on the vanilla humanoid model at the end of {@code setupAnim}. Driven by the
 * synced {@code animId / animStart} (his ids 120..139) and his timers (Rush, the Howitzer spin, the Max Shot charge,
 * the Counter guard), so every viewer sees the same thing. Negative {@code xRot} swings a limb forward / up; positive
 * {@code zRot} lifts the right arm out to the side (the left arm mirrors). The Howitzer's whole-body spin is the body
 * yaw ({@link ExplosionClient#spinBodies}); here the head is turned with the body.
 */
public final class ExplosionPose {
	private ExplosionPose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case Explosion.ANIM_AP_SHOT -> 10;
			case Explosion.ANIM_AUTO -> ExplosionConfig.AUTO_SHOTS * ExplosionConfig.AUTO_INTERVAL + 6;
			case Explosion.ANIM_PUNCH -> 10;
			case Explosion.ANIM_UPPERCUT -> 12;
			case Explosion.ANIM_STUN -> 14;
			case Explosion.ANIM_SMOKE -> 14;
			case Explosion.ANIM_RUSH -> 6;
			case Explosion.ANIM_DASH -> 12;
			case Explosion.ANIM_HOWITZER -> ExplosionConfig.HOWITZER_SPIN_TICKS + 4;
			case Explosion.ANIM_MAX_SHOT -> ExplosionConfig.MAX_SHOT_CHARGE + 12;
			case Explosion.ANIM_CLUSTER -> 12;
			case Explosion.ANIM_COUNTER -> ExplosionConfig.COUNTER_WINDOW;
			case Explosion.ANIM_COUNTER_HIT -> 10;
			case Explosion.ANIM_STRAIN -> ExplosionConfig.STRAIN_TICKS;
			default -> 0;
		};
	}

	/** The playing pose and how far into it ({@code [animId, age]}), or null. */
	public static float[] playing(Player player, float partial) {
		QuirkState s = Quirks.peek(player);
		if (s == null || s.animId < 120 || s.animId > 139 || player.level() == null) {
			return null;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age >= length(s.animId)) {
			return null;
		}
		return new float[] { s.animId, age };
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!Explosion.is(player)) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		// Blast Rush: leaning into it, both palms thrown back behind him blasting, the legs trailing
		if (Explosion.rushing(player) && !player.onGround()) {
			float jit = 0.06f * Mth.sin(t * 2.7f);
			float speed = (float) Math.min(1.0, Math.hypot(player.getX() - player.xo, player.getZ() - player.zo) / 0.8);
			model.body.xRot = Mth.lerp(0.8f, model.body.xRot, 0.15f + 0.45f * speed);
			pose(r, 1f, 0.95f + jit, 0f, 0.35f);
			pose(l, 1f, 0.95f - jit, 0f, -0.35f);
			model.rightLeg.xRot = 0.25f + 0.1f * Mth.sin(t * 0.6f);
			model.leftLeg.xRot = 0.45f - 0.1f * Mth.sin(t * 0.6f);
			model.rightLeg.yRot = 0f;
			model.leftLeg.yRot = 0f;
			float lean = 0.45f * speed;
			model.rightLeg.z += 3.0f * lean;
			model.leftLeg.z += 3.0f * lean;
			model.rightLeg.y -= 1.0f * lean;
			model.leftLeg.y -= 1.0f * lean;
			model.head.xRot = Mth.lerp(0.5f, model.head.xRot, model.head.xRot - 0.35f * speed);
		}
		// the Explosive Counter guard: right palm cocked back, left hand out
		if (Explosion.running(player, Explosion.V_COUNTER)) {
			pose(r, 1f, -0.9f, 0.6f, 0.2f);
			pose(l, 1f, -1.3f, 0.3f, -0.1f);
		}
		// the Max Shot charge: both palms together straight ahead, braced
		if (Explosion.charging(player)) {
			float aim = -1.55f + model.head.xRot;
			float shake = 0.03f * Mth.sin(t * 3.1f);
			pose(r, 1f, aim + shake, -0.3f, 0f);
			pose(l, 1f, aim - shake, 0.3f, 0f);
			model.rightLeg.xRot = -0.35f;
			model.leftLeg.xRot = 0.4f;
			model.body.xRot = 0.12f;
		}
		// the Howitzer spin: arms flung wide, blasting; the head turns with the body
		if (Explosion.spinning(player)) {
			pose(r, 1f, -0.1f, 0f, 1.45f + 0.1f * Mth.sin(t * 2f));
			pose(l, 1f, -0.1f, 0f, -1.45f - 0.1f * Mth.sin(t * 2f));
			model.rightLeg.xRot = -0.5f;
			model.leftLeg.xRot = 0.3f;
			model.rightLeg.zRot = 0.15f;
			model.leftLeg.zRot = -0.15f;
			model.head.yRot = 0f;
			model.head.xRot = 0.2f;
			model.body.xRot = 0.1f;
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
			case Explosion.ANIM_AP_SHOT -> {
				// palm out at the target, a kick of recoil
				float kick = age < 1f ? 0f : Math.max(0f, 1f - (age - 1f) / 4f);
				pose(r, w, -1.57f + model.head.xRot - 0.45f * kick, -0.1f + model.head.yRot, 0f);
				pose(l, w, 0.35f, 0f, -0.25f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, -0.25f);
			}
			case Explosion.ANIM_AUTO -> {
				// both palms out, jabbing in turn
				float a = Mth.sin(age * (float) Math.PI / ExplosionConfig.AUTO_INTERVAL);
				float aim = -1.5f + model.head.xRot;
				pose(r, w, aim - 0.25f * Math.max(0f, a), 0f, 0f);
				pose(l, w, aim - 0.25f * Math.max(0f, -a), 0f, 0f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, 0.12f * a);
			}
			case Explosion.ANIM_PUNCH -> {
				// cocked back, then driven out with a twist of the body
				float s = ease(Mth.clamp((age - 1f) / 2.5f, 0f, 1f));
				pose(r, w, Mth.lerp(s, 0.6f, -1.6f + model.head.xRot), Mth.lerp(s, 0.5f, -0.1f), 0.1f);
				pose(l, w, Mth.lerp(s, -0.8f, 0.4f), 0f, -0.2f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(s, 0.45f, -0.4f));
			}
			case Explosion.ANIM_UPPERCUT -> {
				// low and down, then ripping straight up, leaning back
				float s = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(s, 0.5f, -2.9f), 0f, Mth.lerp(s, 0.2f, 0.05f));
				pose(l, w, 0.4f, 0f, -0.35f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, Mth.lerp(s, 0.35f, -0.15f));
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, -0.4f * (1f - s));
			}
			case Explosion.ANIM_STUN -> {
				// palms crossed before the face, then flung out wide in the flash
				float s = ease(Mth.clamp((age - 3f) / 2f, 0f, 1f));
				pose(r, w, Mth.lerp(s, -1.6f, -1.3f), Mth.lerp(s, 0.6f, -0.5f), Mth.lerp(s, 0f, 0.7f));
				pose(l, w, Mth.lerp(s, -1.6f, -1.3f), Mth.lerp(s, -0.6f, 0.5f), Mth.lerp(s, 0f, -0.7f));
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.1f);
			}
			case Explosion.ANIM_SMOKE -> {
				// both palms slammed down at the ground
				float s = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.5f * s);
				pose(r, w, Mth.lerp(s, -1.2f, -0.6f), 0f, 0.45f);
				pose(l, w, Mth.lerp(s, -1.2f, -0.6f), 0f, -0.45f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.4f * s);
			}
			case Explosion.ANIM_RUSH -> {
				// the take-off blast: palms slammed back and down
				pose(r, w, 0.6f, 0f, 0.5f);
				pose(l, w, 0.6f, 0f, -0.5f);
			}
			case Explosion.ANIM_DASH -> {
				// thrown forward off one back-blast: arms back, leaning in
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.5f);
				pose(r, w, 1.2f, 0f, 0.3f);
				pose(l, w, 1.2f, 0f, -0.3f);
				model.rightLeg.xRot = Mth.lerp(w, model.rightLeg.xRot, 0.5f);
				model.leftLeg.xRot = Mth.lerp(w, model.leftLeg.xRot, 0.2f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.4f);
			}
			case Explosion.ANIM_MAX_SHOT -> {
				// after the charge: the recoil throws the arms up
				float since = age - ExplosionConfig.MAX_SHOT_CHARGE;
				if (since >= 0f) {
					float s = Math.max(0f, 1f - since / 8f);
					pose(r, w, -1.9f - 0.5f * s, -0.25f, 0f);
					pose(l, w, -1.9f - 0.5f * s, 0.25f, 0f);
					model.body.xRot = Mth.lerp(w, model.body.xRot, -0.2f * s);
				}
			}
			case Explosion.ANIM_CLUSTER -> {
				// arms swept out wide and low all round
				float s = ease(Mth.clamp((age - 1f) / 3f, 0f, 1f));
				pose(r, w, Mth.lerp(s, -0.5f, 0.1f), 0f, Mth.lerp(s, 0.2f, 1.2f));
				pose(l, w, Mth.lerp(s, -0.5f, 0.1f), 0f, Mth.lerp(s, -0.2f, -1.2f));
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.2f);
				model.rightLeg.zRot = 0.18f * w;
				model.leftLeg.zRot = -0.18f * w;
			}
			case Explosion.ANIM_COUNTER_HIT -> {
				// the answer: right palm thrust into the attacker
				float s = ease(Mth.clamp(age / 2f, 0f, 1f));
				pose(r, w, Mth.lerp(s, -0.9f, -1.6f + model.head.xRot), Mth.lerp(s, 0.6f, 0f), 0f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, -0.3f * s);
			}
			case Explosion.ANIM_STRAIN -> {
				// the arms hang, shaking
				float shake = 0.05f * Mth.sin(t * 4.3f);
				pose(r, w, 0.15f + shake, 0f, 0.08f);
				pose(l, w, 0.15f - shake, 0f, -0.08f);
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.25f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.35f);
			}
			default -> {
			}
		}
		model.hat.copyFrom(model.head);
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
