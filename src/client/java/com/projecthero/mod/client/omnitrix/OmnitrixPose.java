package com.projecthero.mod.client.omnitrix;

import com.projecthero.mod.client.quirk.QuirkKeyframes;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;

/**
 * v0.17.1: third-person poses -- the framework's (the raised wrist while picking an alien, the slap, the arms flung out as the
 * transformation burst plays) and then the alien kit's ({@link AlienClientKit#pose}). Called from {@code OmnitrixPoseMixin} at the end of
 * {@code HumanoidModel.setupAnim}; skipped inside the first-person {@code renderHand} (it re-runs setupAnim on the arm model).
 */
public final class OmnitrixPose {
	private static final float[][] TRANSFORM = {
			QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.15f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.3f),
			QuirkKeyframes.f(10, -0.2f, 0, 1.4f, -0.2f, 0, -1.4f, -0.2f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.45f),
			QuirkKeyframes.rest(16) };

	/*
	 * The raised-watch pose (x, y, z rotations), solved with scratchpad/omnitrix_pose_solver.js: the left arm points forward,
	 * across and down (the watch in front of the chest), rolled so the face points up toward the eyes; the right hand's palm
	 * gripping the dial's near rim (1.5 px back toward the elbow, so the face shows), 2.2 px off it, 5.5 px (wound up for the
	 * slap) and 1.6 px (pressing the core in). Minecraft's
	 * arm has no elbow, so the watch arm's shoulder drops 6 px (the other 2.5) (the forearm reads level at chest height instead of the whole
	 * arm pointing up at the chin).
	 */
	private static final float[] WATCH_ARM = { -2.169f, 0.040f, -1.809f };
	private static final float[] HAND_ON = { -2.440f, -0.057f, 1.359f };
	private static final float[] HAND_UP = { -2.453f, 0.211f, 1.076f };
	private static final float[] HAND_PRESS = { -2.445f, -0.111f, 1.416f };
	private static final float SHOULDER_DROP = 6f, HAND_DROP = 2.5f;
	private static final float HEAD_DOWN = 0.42f;

	private OmnitrixPose() {
	}

	private static void set(net.minecraft.client.model.geom.ModelPart part, float w, float[] r) {
		part.xRot = Mth.lerp(w, part.xRot, r[0]);
		part.yRot = Mth.lerp(w, part.yRot, r[1]);
		part.zRot = Mth.lerp(w, part.zRot, r[2]);
	}

	public static void apply(AbstractClientPlayer p, HumanoidModel<?> m) {
		if (OmnitrixFirstPerson.inHandRender || !Omnitrix.hasPower(p)) {
			return;
		}
		OmnitrixState s = Omnitrix.state(p);
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = p.level().getGameTime() + partial - s.animStart;
		// the transformation burst: arms flung wide
		if ((s.animId == Omnitrix.ANIM_TRANSFORM || s.animId == Omnitrix.ANIM_TIMEOUT || s.animId == Omnitrix.ANIM_REVERT) && t < 16) {
			QuirkKeyframes.play(m, TRANSFORM, t, 1f);
		}
		// the raised wrist (v0.17.1 polish): the left forearm across in front of the chest with the face turned up to the eyes, the
		// head bowed to look at it, the right hand over the dial -- twisting it on each scroll, slapping the core on the click
		float raise = OmnitrixWatch.raise(p, partial);
		if (raise > 0f) {
			set(m.leftArm, raise, WATCH_ARM);
			m.leftArm.y += SHOULDER_DROP * raise;
			m.head.xRot = Mth.lerp(raise, m.head.xRot, HEAD_DOWN);
			m.head.yRot = Mth.lerp(raise, m.head.yRot, 0f);
			m.hat.copyFrom(m.head);
			// the right hand arrives a beat after the wrist
			float hand = Mth.clamp((raise - 0.25f) / 0.75f, 0f, 1f);
			hand = hand * hand * (3f - 2f * hand);
			float[] target = HAND_ON.clone();
			float slap = s.animId == Omnitrix.ANIM_SLAP ? t : 99f;
			if (slap < Omnitrix.SLAP_TICKS + 4) {
				// wind up off the dial, then slam down onto the core and press it in
				float up = slap < 1.5f ? Mth.sin(slap / 1.5f * Mth.HALF_PI) : 0f;
				float press = slap >= 1.5f ? 1f - Mth.clamp((slap - 2.5f) / (Omnitrix.SLAP_TICKS + 1.5f), 0f, 1f) : 0f;
				for (int i = 0; i < 3; i++) {
					target[i] = HAND_ON[i] + (HAND_UP[i] - HAND_ON[i]) * up + (HAND_PRESS[i] - HAND_ON[i]) * press;
				}
			}
			float turn = s.animId == Omnitrix.ANIM_TURN ? t : 99f;
			if (turn < 5f) {
				// the fingers grip the dial and turn it: a small roll of the hand and back
				float k = Mth.sin(turn / 5f * Mth.PI);
				target[1] += 0.32f * k;
				target[2] -= 0.14f * k;
				target[0] -= 0.06f * k;
			}
			set(m.rightArm, hand, target);
			m.rightArm.y += HAND_DROP * hand;
		}
		// the alien kit
		AlienClientKit kit = AlienClients.shownKit(p);
		if (kit != null && s.transformed()) {
			kit.pose(p, m, s.animId, t, p.tickCount + partial);
		}
	}
}
