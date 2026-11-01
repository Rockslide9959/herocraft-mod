package com.projecthero.mod.client.stand;

import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.data.StandState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * v0.17.2: Star Platinum's body poses, set straight on the Stand's own player model by {@link StandRender} (never through
 * {@code setupAnim}, so no player pose mixin leaks onto it). Negative {@code xRot} swings an arm forward / up.
 * <ul>
 *   <li><b>Rest</b>: arms folded, legs trailing, a slow float.</li>
 *   <li><b>Punches</b>: one fist driven straight out (the 3rd, heavy blow with a twist of the body).</li>
 *   <li><b>ORA Rush</b>: both fists pistoning (and {@link StandRender} blurs extra arms round them).</li>
 *   <li><b>Star Finger</b>: two fingers thrust out; <b>Grab / Throw / Flick</b>: the right hand.</li>
 *   <li><b>Star Breaker</b>: wound back while charging, then the full-body punch.</li>
 *   <li><b>The World</b>: arms flung wide, head back. <b>Guard</b>: forearms crossed in front. <b>Inhale</b>: head back,
 *       chest out. <b>Uppercut</b>: low to high.</li>
 * </ul>
 */
public final class StandPoses {
	private StandPoses() {
	}

	static void apply(HumanoidModel<?> m, StandState s, float age, float t, long now) {
		// rest: arms folded across the chest, legs dangling
		pose(m.rightArm, 1f, -1.05f, -0.55f, 0.05f);
		pose(m.leftArm, 1f, -1.0f, 0.55f, -0.05f);
		pose(m.rightLeg, 1f, 0.12f + 0.05f * Mth.sin(t * 0.08f), 0f, 0.04f);
		pose(m.leftLeg, 1f, -0.05f + 0.05f * Mth.sin(t * 0.08f + 1.3f), 0f, -0.04f);

		if (s.rushing) {
			float ph = t * 2.6f;
			pose(m.rightArm, 1f, -Mth.HALF_PI + 0.45f * Mth.sin(ph), -0.15f, 0f);
			pose(m.leftArm, 1f, -Mth.HALF_PI + 0.45f * Mth.sin(ph + Mth.PI), 0.15f, 0f);
			m.body.xRot = 0.15f;
			m.body.yRot = 0.12f * Mth.sin(ph);
			return;
		}
		if (s.guarding) {
			pose(m.rightArm, 1f, -1.75f, -0.75f, 0f);
			pose(m.leftArm, 1f, -1.6f, 0.75f, 0f);
			m.body.xRot = 0.1f;
			return;
		}
		if (s.chargeStart != 0L) {
			float c = Mth.clamp((now - s.chargeStart) / (float) StandConfig.BREAKER_FULL_CHARGE, 0f, 1f);
			pose(m.rightArm, 1f, Mth.lerp(c, -0.4f, 0.75f), 0.2f, 0.35f);
			pose(m.leftArm, 1f, -1.3f, 0.3f, 0f);
			m.body.yRot = 0.45f * c;
			m.rightArm.x += 0.6f * c * Mth.sin(t * 3f); // trembling with the held power
			return;
		}
		if (s.grabbedEntity != -1 || s.grabbedBlock != 0) {
			pose(m.rightArm, 1f, -1.45f, -0.1f, 0f);
			pose(m.leftArm, 1f, -1.3f, 0.2f, 0f);
			return;
		}
		if (s.inhaleUntil != 0L) {
			pose(m.rightArm, 1f, -0.3f, 0f, 0.7f);
			pose(m.leftArm, 1f, -0.3f, 0f, -0.7f);
			m.head.xRot = -0.55f;
			m.body.xRot = -0.12f;
			return;
		}

		int len = StandRender.animLength(s.animId);
		if (s.animId == StandState.ANIM_NONE || len == 0 || age < 0f || age >= len) {
			return;
		}
		float w = Math.min(1f, age / 2f) * Math.min(1f, (len - age) / 3f);
		float p = Mth.clamp(age / Math.max(2f, len * 0.35f), 0f, 1f);
		switch (s.animId) {
			case StandState.ANIM_PUNCH_R -> pose(m.rightArm, w, -Mth.HALF_PI, -0.1f, 0f);
			case StandState.ANIM_PUNCH_L -> pose(m.leftArm, w, -Mth.HALF_PI, 0.1f, 0f);
			case StandState.ANIM_PUNCH_HEAVY, StandState.ANIM_RUSH_FINISH, StandState.ANIM_BREAKER -> {
				pose(m.rightArm, w, Mth.lerp(p, 0.6f, -Mth.HALF_PI), Mth.lerp(p, 0.3f, -0.05f), 0f);
				pose(m.leftArm, w, -0.4f, 0.2f, -0.2f);
				m.body.yRot = Mth.lerp(w, 0f, Mth.lerp(p, 0.4f, -0.35f));
				m.body.xRot = 0.15f * w;
			}
			case StandState.ANIM_FINGER -> {
				pose(m.rightArm, w, -Mth.HALF_PI - 0.05f, 0f, 0f);
				pose(m.leftArm, w, 0.1f, 0f, -0.15f);
			}
			case StandState.ANIM_GRAB, StandState.ANIM_CATCH -> pose(m.rightArm, w, -1.55f, -0.05f, 0f);
			case StandState.ANIM_THROW -> {
				pose(m.rightArm, w, Mth.lerp(p, -2.9f, -1.1f), -0.1f, 0f);
				m.body.xRot = 0.2f * p * w;
			}
			case StandState.ANIM_FLICK -> pose(m.rightArm, w, -1.5f, 0.15f, 0f);
			case StandState.ANIM_UPPERCUT -> {
				pose(m.rightArm, w, Mth.lerp(p, -0.2f, -2.9f), -0.1f, 0.1f);
				m.body.xRot = Mth.lerp(p, 0.25f, -0.15f) * w;
			}
			case StandState.ANIM_THE_WORLD -> {
				pose(m.rightArm, w, -0.4f, 0f, 1.35f);
				pose(m.leftArm, w, -0.4f, 0f, -1.35f);
				m.head.xRot = Mth.lerp(w, m.head.xRot, -0.45f);
				m.body.xRot = -0.1f * w;
			}
			case StandState.ANIM_SUMMON -> {
				pose(m.rightArm, w, -0.2f, 0f, 0.35f);
				pose(m.leftArm, w, -0.2f, 0f, -0.35f);
			}
			default -> {
			}
		}
	}

	static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
