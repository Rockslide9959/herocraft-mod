package com.projecthero.mod.client.miraculous.ladybug;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.LadybugConfig;
import com.projecthero.mod.miraculous.ladybug.LadybugKeys;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6: Ladybug's keyframed poses on the vanilla player model (from {@code MiraculousClient.applyPose}, the END of
 * setupAnim; her suit layer copies it). Driven by the synced state, so every viewer sees the same. Negative {@code xRot}
 * swings an arm forward / up. The Yo-yo is always in the RIGHT hand.
 * <ul>
 *   <li>Held moves: the swing / zip arm reaching up the line to the hook; the Spinning Shield arm out in front, the
 *       wrist turning; the bind arm held out toward the wrapped creature; both hands up on the parachute's cords.</li>
 *   <li>Strike: wound back over the shoulder, whipped out along the look. Ricochet: thrown, then swept across.</li>
 *   <li>Bind: a loop overhead, then the throw. Yank: hauled back, leaning. Lasso Fling: a big sweep across and up.</li>
 *   <li>Pole Vault: both hands high on the planted string, knees tucked. Trip Wire: a flick toward the block.</li>
 *   <li>Scan / Phone: the Yo-yo opened like a compact in front of her face, head down to read it.</li>
 *   <li>Lucky Charm: the Yo-yo thrown straight up, head back, the other arm out. Miraculous Ladybug: the charm tossed
 *       high with both arms, then arms flung wide.</li>
 * </ul>
 */
public final class LadybugPose {
	private LadybugPose() {
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	/** The right arm pointed from the shoulder at a world point. */
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

	private static void head(HumanoidModel<?> m, float w, float x) {
		m.head.xRot = Mth.lerp(w, m.head.xRot, x);
		m.hat.copyFrom(m.head);
	}

	public static void apply(Player p, HumanoidModel<?> m) {
		MiraculousState s = LadybugKeys.state(p);
		if (s == null || p.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = p.level().getGameTime();
		float t = now + partial;
		ModelPart arm = m.rightArm, other = m.leftArm;
		// the look as an arm angle (like the Hellfire Blast)
		float aimX = m.head.xRot - Mth.HALF_PI, aimY = m.head.yRot;

		// ---- the held moves
		if (s.val(LadybugKeys.V_CHUTE) != 0L) {
			pose(m.rightArm, 1f, -2.75f, 0f, 0.3f);
			pose(m.leftArm, 1f, -2.75f, 0f, -0.3f);
			pose(m.rightLeg, 1f, -0.25f, 0f, 0.05f);
			pose(m.leftLeg, 1f, 0.15f, 0f, -0.05f);
		}
		Vec3 hook = Miraculous.vec(s, LadybugKeys.P_HOOK);
		if (s.val(LadybugKeys.V_LINE) != 0L && hook != null) {
			pointAt(p, arm, hook, partial, 1f);
			if (s.val(LadybugKeys.V_LINE) == 2L) {
				// zipping: tucked, the other hand on the string too
				pose(other, 1f, arm.xRot + 0.25f, arm.yRot - 0.25f, 0f);
				pose(m.rightLeg, 1f, -0.7f, 0f, 0.05f);
				pose(m.leftLeg, 1f, -0.35f, 0f, -0.05f);
			} else {
				pose(other, 1f, -0.4f + 0.2f * Mth.sin(t * 0.2f), 0f, -0.55f);
				pose(m.rightLeg, 0.6f, 0.35f, 0f, 0f);
				pose(m.leftLeg, 0.6f, -0.45f, 0f, 0f);
			}
		}
		if (s.val(LadybugKeys.V_SHIELD) != 0L) {
			float wob = 0.06f * Mth.sin(t * 1.3f);
			pose(arm, 1f, aimX + 0.15f + wob, aimY - 0.15f, 0.1f);
			pose(other, 1f, -0.9f, 0.45f, 0f);
		}
		int bound = LadybugKeys.bound(p);
		if (bound >= 0) {
			Entity e = p.level().getEntity(bound);
			if (e != null) {
				float since = s.animId == LadybugKeys.ANIM_BIND ? now - s.animStart + partial : 99f;
				pointAt(p, arm, e.getPosition(partial).add(0, e.getBbHeight() * 0.6, 0), partial, Mth.clamp((since - 2f) / 3f, 0f, 1f));
				pose(other, 1f, -1.0f, 0.3f, 0f);
			}
		}

		// ---- the moves' keyframes
		float age = LadybugWorld.animAge(s, now, partial);
		if (age < 0f) {
			return;
		}
		int len = LadybugWorld.length(s.animId);
		float w = Math.min(1f, age / 2f + 0.25f) * Math.min(1f, (len - age) / 3f);
		switch (s.animId) {
			case LadybugKeys.ANIM_STRIKE -> {
				float k = Mth.clamp(age / 2.5f, 0f, 1f);
				pose(arm, w, Mth.lerp(k, -2.7f, aimX), Mth.lerp(k, 0.6f, aimY), Mth.lerp(k, -0.2f, 0f));
				pose(other, w, -0.4f, 0f, -0.4f);
			}
			case LadybugKeys.ANIM_RICOCHET -> {
				float k = Mth.clamp(age / (LadybugConfig.RICOCHET_HOP_TICKS * 4f), 0f, 1f);
				pose(arm, w, aimX, aimY + Mth.lerp(k, 0.5f, -0.7f), 0f);
				pose(other, w, -0.3f, 0f, -0.5f);
			}
			case LadybugKeys.ANIM_BIND -> {
				if (age < 3f) {
					// the loop overhead
					float a = age * 2.1f;
					pose(arm, w, -2.8f, 0.3f * Mth.sin(a), 0.3f * Mth.cos(a));
				}
				pose(other, w, -0.6f, 0f, -0.3f);
			}
			case LadybugKeys.ANIM_YANK -> {
				float k = Mth.clamp(age / 4f, 0f, 1f);
				pose(arm, w, Mth.lerp(k, aimX, -0.35f), Mth.lerp(k, aimY, 0.1f), 0.15f);
				pose(other, w, Mth.lerp(k, -1.2f, -0.3f), 0f, -0.2f);
				m.body.xRot = Mth.lerp(w, m.body.xRot, -0.22f * k);
			}
			case LadybugKeys.ANIM_FLING -> {
				float k = Mth.clamp(age / 6f, 0f, 1f);
				pose(arm, w, Mth.lerp(k, -1.2f, -2.6f), Mth.lerp(k, 0.9f, -0.9f), Mth.lerp(k, 0.4f, -0.3f));
				pose(other, w, -0.5f, 0f, -0.6f);
			}
			case LadybugKeys.ANIM_VAULT -> {
				pose(m.rightArm, w, -2.9f, 0f, 0.12f);
				pose(m.leftArm, w, -2.9f, 0f, -0.12f);
				float k = Mth.clamp((age - 3f) / 5f, 0f, 1f);
				pose(m.rightLeg, w * k, -1.1f, 0f, 0.1f);
				pose(m.leftLeg, w * k, -0.7f, 0f, -0.1f);
				head(m, w, -0.35f);
			}
			case LadybugKeys.ANIM_WIRE -> {
				float k = Mth.clamp(age / 3f, 0f, 1f);
				pose(arm, w, aimX + 0.2f * (1f - k), aimY, 0f);
			}
			case LadybugKeys.ANIM_SCAN -> {
				pose(arm, w, -1.75f, -0.45f, 0.1f);
				pose(other, w, -1.45f, 0.45f, -0.1f);
				head(m, w, 0.45f);
			}
			case LadybugKeys.ANIM_CHARM -> {
				float k = Mth.clamp(age / 4f, 0f, 1f);
				pose(arm, w, Mth.lerp(k, -1.4f, -3.05f), 0f, 0.1f);
				pose(other, w, -0.2f, 0f, -0.9f);
				head(m, w, -0.7f);
			}
			case LadybugKeys.ANIM_MIRACULOUS -> {
				float k = Mth.clamp(age / 6f, 0f, 1f);
				float spread = Mth.clamp((age - 10f) / 6f, 0f, 1f);
				pose(m.rightArm, w, Mth.lerp(spread, Mth.lerp(k, -1.0f, -3.0f), -2.2f), 0f, Mth.lerp(spread, 0.1f, 0.9f));
				pose(m.leftArm, w, Mth.lerp(spread, Mth.lerp(k, -1.0f, -3.0f), -2.2f), 0f, Mth.lerp(spread, -0.1f, -0.9f));
				head(m, w, -0.75f);
				m.body.xRot = Mth.lerp(w, m.body.xRot, -0.12f);
			}
			default -> {
			}
		}
	}
}
