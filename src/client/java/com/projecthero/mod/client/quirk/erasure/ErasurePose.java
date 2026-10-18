package com.projecthero.mod.client.quirk.erasure;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.erasure.ErasureConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Eraser Head's poses on the vanilla player model (from {@code QuirkClient.applyPose}, the END of setupAnim),
 * driven by the synced state so every viewer sees the same. Negative {@code xRot} swings an arm forward / up; the scarf
 * is always thrown from the RIGHT hand.
 * <ul>
 *   <li>Held: the stare (head locked forward, shoulders hunched, the scarf hand ready at the chest); the swing / zip
 *       arm reaching up the line; the bind / slam / double-bind hands pulling the scarf taut toward the creature.</li>
 *   <li>Lash / Disarm: wound back then whipped out along the look. Bind / Hoist: a two-handed throw, then a haul.</li>
 *   <li>Goggles: a hand up to the face. Eye Drops: head back, a hand over the eyes. The blink: head dips.</li>
 * </ul>
 */
public final class ErasurePose {
	private ErasurePose() {
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	/** An arm pointed from the shoulder at a world point. */
	private static void pointAt(Player p, ModelPart arm, Vec3 target, float partial, float w) {
		Vec3 shoulder = p.getPosition(partial).add(0, p.getBbHeight() * 0.78, 0);
		Vec3 d = target.subtract(shoulder);
		double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
		float pitch = (float) Math.atan2(d.y, horiz);
		float dirYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
		float body = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
		float rel = Mth.wrapDegrees(dirYaw - body) * Mth.DEG_TO_RAD;
		pose(arm, w, -Mth.HALF_PI - pitch, rel, 0f);
	}

	static int length(int animId) {
		return switch (animId) {
			case ErasureConfig.ANIM_LASH, ErasureConfig.ANIM_DISARM -> 10;
			case ErasureConfig.ANIM_SWING, ErasureConfig.ANIM_ZIP -> 8;
			case ErasureConfig.ANIM_BIND, ErasureConfig.ANIM_HOIST -> 14;
			case ErasureConfig.ANIM_SLAM -> ErasureConfig.SLAM_LIFT_TICKS + 10;
			case ErasureConfig.ANIM_DOUBLE -> 14;
			case ErasureConfig.ANIM_GOGGLES -> 8;
			case ErasureConfig.ANIM_DROPS -> 20;
			case ErasureConfig.ANIM_BLINK -> ErasureConfig.BLINK_TICKS;
			default -> 0;
		};
	}

	private static Entity entity(Player p, QuirkState s, String key) {
		long v = s.val(key);
		return v == 0L ? null : p.level().getEntity((int) (v - 1L));
	}

	public static void apply(Player p, HumanoidModel<?> m) {
		QuirkState s = Quirks.peek(p);
		if (s == null || p.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = p.level().getGameTime();
		float t = now + partial;
		ModelPart arm = m.rightArm, other = m.leftArm;
		float aimX = m.head.xRot - Mth.HALF_PI, aimY = m.head.yRot;

		// ---- the stare: hunched, the scarf hand at the chest
		if (s.val(ErasureConfig.V_ERASE) != 0L) {
			float w = Mth.clamp((now - s.val(ErasureConfig.V_ERASE_AT) + partial) / 4f, 0f, 1f);
			pose(arm, w, -0.9f, -0.45f, 0.1f);
			pose(other, w, -0.25f, 0.1f, -0.15f);
			if (s.val(ErasureConfig.V_ERASE) == 2L) {
				// the glare: both arms out a little, taking everything in
				pose(arm, w, -0.6f, -0.2f, 0.45f);
				pose(other, w, -0.6f, 0.2f, -0.45f);
			}
		}
		// ---- the line
		Vec3 hook = Quirks.vec(s, ErasureConfig.P_HOOK);
		long line = s.val(ErasureConfig.V_LINE);
		if (line != 0L) {
			Entity z = line == 2L ? entity(p, s, ErasureConfig.V_ZIP_TARGET) : null;
			Vec3 to = z != null ? z.getPosition(partial).add(0, z.getBbHeight() * 0.5, 0) : hook;
			if (to != null) {
				pointAt(p, arm, to, partial, 1f);
				if (line == 2L) {
					pose(other, 1f, arm.xRot + 0.2f, arm.yRot - 0.25f, 0f);
					pose(m.rightLeg, 1f, -0.6f, 0f, 0.05f);
					pose(m.leftLeg, 1f, -0.3f, 0f, -0.05f);
				} else {
					pose(other, 1f, -0.4f + 0.2f * Mth.sin(t * 0.2f), 0f, -0.55f);
					pose(m.rightLeg, 0.6f, 0.35f, 0f, 0f);
					pose(m.leftLeg, 0.6f, -0.45f, 0f, 0f);
				}
			}
		}
		// ---- holding a bound / slammed creature: both hands on the scarf
		Entity held = s.val(ErasureConfig.V_BIND_UNTIL) > now ? entity(p, s, ErasureConfig.V_BIND) : null;
		if (held == null) {
			held = entity(p, s, ErasureConfig.V_SLAM);
		}
		if (held != null && line == 0L) {
			Vec3 at = held.getPosition(partial).add(0, held.getBbHeight() * 0.6, 0);
			pointAt(p, arm, at, partial, 1f);
			pose(other, 1f, arm.xRot + 0.35f, arm.yRot + 0.35f, 0f);
		}
		Entity a = entity(p, s, ErasureConfig.V_DB_A), b = entity(p, s, ErasureConfig.V_DB_B);
		if (a != null && b != null) {
			pointAt(p, arm, a.getPosition(partial).add(0, a.getBbHeight() * 0.6, 0), partial, 1f);
			pointAt(p, other, b.getPosition(partial).add(0, b.getBbHeight() * 0.6, 0), partial, 1f);
		}

		// ---- the moves' keyframes
		if (s.animId < 100 || s.animId > 119) {
			return;
		}
		int len = length(s.animId);
		float age = now - s.animStart + partial;
		if (age < 0f || age >= len) {
			return;
		}
		float w = Math.min(1f, age / 2f + 0.25f) * Math.min(1f, (len - age) / 3f);
		switch (s.animId) {
			case ErasureConfig.ANIM_LASH, ErasureConfig.ANIM_DISARM -> {
				float k = Mth.clamp(age / 2.5f, 0f, 1f);
				pose(arm, w, Mth.lerp(k, -2.6f, aimX), Mth.lerp(k, 0.7f, aimY), Mth.lerp(k, -0.3f, 0f));
				pose(other, w, -0.3f, 0f, -0.35f);
				if (s.animId == ErasureConfig.ANIM_DISARM && age > 4f) {
					// the yank back
					pose(arm, w, -0.6f, 0.2f, 0f);
				}
			}
			case ErasureConfig.ANIM_BIND, ErasureConfig.ANIM_HOIST -> {
				float k = Mth.clamp(age / 3f, 0f, 1f);
				pose(arm, w, Mth.lerp(k, -2.8f, aimX), aimY - 0.1f, 0f);
				pose(other, w, Mth.lerp(k, -2.4f, aimX + 0.3f), aimY + 0.2f, 0f);
				if (age > 6f) {
					float up = s.animId == ErasureConfig.ANIM_HOIST ? -2.3f : -1.0f;
					pose(arm, w, up, -0.2f, 0f);
					pose(other, w, up + 0.3f, 0.2f, 0f);
				}
			}
			case ErasureConfig.ANIM_SLAM -> {
				float lift = ErasureConfig.SLAM_LIFT_TICKS;
				if (age < lift) {
					pose(arm, w, -2.9f, 0f, 0.2f);
					pose(other, w, -2.7f, 0f, -0.2f);
				} else {
					float k = Mth.clamp((age - lift) / 3f, 0f, 1f);
					pose(arm, w, Mth.lerp(k, -2.9f, -0.4f), 0f, 0.2f);
					pose(other, w, Mth.lerp(k, -2.7f, -0.3f), 0f, -0.2f);
					m.body.xRot = Mth.lerp(w * k, m.body.xRot, 0.3f);
				}
			}
			case ErasureConfig.ANIM_DOUBLE -> {
				float k = Mth.clamp(age / 3f, 0f, 1f);
				pose(arm, w, -1.6f, Mth.lerp(k, -0.9f, 0.3f), 0f);
				pose(other, w, -1.6f, Mth.lerp(k, 0.9f, -0.3f), 0f);
			}
			case ErasureConfig.ANIM_GOGGLES -> {
				pose(arm, w, -2.6f, -0.5f, 0f);
			}
			case ErasureConfig.ANIM_DROPS -> {
				pose(arm, w, -2.8f, -0.35f, 0f);
				pose(other, w, -0.4f, 0f, -0.2f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.7f);
				m.hat.copyFrom(m.head);
			}
			case ErasureConfig.ANIM_BLINK -> {
				float dip = age < 6f ? 1f : Math.max(0f, 1f - (age - 6f) / 10f);
				m.head.xRot = Mth.lerp(w * dip, m.head.xRot, 0.55f);
				m.hat.copyFrom(m.head);
				pose(arm, w * dip, -1.9f, -0.5f, 0f);
			}
			default -> {
			}
		}
	}
}
