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
 * helix runs) and then the alien kit's ({@link AlienClientKit#pose}). Called from {@code OmnitrixPoseMixin} at the end of
 * {@code HumanoidModel.setupAnim}; skipped inside the first-person {@code renderHand} (it re-runs setupAnim on the arm model).
 */
public final class OmnitrixPose {
	private static final float[][] TRANSFORM = {
			QuirkKeyframes.rest(0),
			QuirkKeyframes.f(3, -0.4f, 0, 0.9f, -0.4f, 0, -0.9f, -0.15f, 0, 0, 0, 0.12f, 0, 0, -0.12f, -0.3f),
			QuirkKeyframes.f(10, -0.2f, 0, 1.4f, -0.2f, 0, -1.4f, -0.2f, 0, 0, 0, 0.2f, 0, 0, -0.2f, -0.45f),
			QuirkKeyframes.rest(16) };

	private OmnitrixPose() {
	}

	public static void apply(AbstractClientPlayer p, HumanoidModel<?> m) {
		if (OmnitrixFirstPerson.inHandRender || !Omnitrix.hasPower(p)) {
			return;
		}
		OmnitrixState s = Omnitrix.state(p);
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = p.level().getGameTime() + partial - s.animStart;
		// the helix: arms flung wide
		if ((s.animId == Omnitrix.ANIM_TRANSFORM || s.animId == Omnitrix.ANIM_TIMEOUT || s.animId == Omnitrix.ANIM_REVERT) && t < 16) {
			QuirkKeyframes.play(m, TRANSFORM, t, 1f);
		}
		// the raised wrist: the left forearm across in front of the chest, the face turned up to the eyes
		float raise = OmnitrixWatch.raise(p, partial);
		if (raise > 0f) {
			m.leftArm.xRot = Mth.lerp(raise, m.leftArm.xRot, -1.75f + m.head.xRot * 0.5f);
			m.leftArm.yRot = Mth.lerp(raise, m.leftArm.yRot, 0.85f);
			m.leftArm.zRot = Mth.lerp(raise, m.leftArm.zRot, 0.25f);
			m.head.xRot = Mth.lerp(raise * 0.5f, m.head.xRot, 0.45f);
			float slap = s.animId == Omnitrix.ANIM_SLAP ? t : 99f;
			if (slap < Omnitrix.SLAP_TICKS + 3) {
				// the right hand comes over and slaps the core down
				float k = slap < 2 ? slap / 2f : 1f - Mth.clamp((slap - 2) / (Omnitrix.SLAP_TICKS + 1), 0f, 1f);
				m.rightArm.xRot = Mth.lerp(k, m.rightArm.xRot, -1.55f);
				m.rightArm.yRot = Mth.lerp(k, m.rightArm.yRot, -0.75f);
				m.rightArm.zRot = Mth.lerp(k, m.rightArm.zRot, 0f);
			}
		}
		// the alien kit
		AlienClientKit kit = AlienClients.shownKit(p);
		if (kit != null && s.transformed()) {
			kit.pose(p, m, s.animId, t, p.tickCount + partial);
		}
	}
}
