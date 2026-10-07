package com.projecthero.mod.client.nova;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.nova.data.NovaState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.13: Nova's keyframed poses, set on the vanilla humanoid model from {@code HumanoidModelMixin} (the suit layer
 * copies it, so the uniform follows). Driven by the synced {@link NovaState}, so every viewer sees the same thing.
 * Negative {@code xRot} swings an arm forward / up.
 * <ul>
 *   <li><b>Flight</b>: as the body leans into it, the main fist drives forward overhead, the other arm along the side.</li>
 *   <li><b>Nova Blast</b> (held): the main arm extended straight along the look, the other hand braced at the chest.</li>
 *   <li><b>Force Shield</b> (while it is up): both forearms crossed in front.</li>
 *   <li><b>Comet Dash</b>: the main fist forward, the other arm swept back.</li>
 *   <li><b>Gravity Slam</b>: both fists over the head through the dive, then the landing crouch.</li>
 *   <li><b>NOVA OVERLOAD</b>: arms flung wide and head thrown back; the burst at its end, the same wider.</li>
 *   <li>Short keyframes for the volley, pulse, launch, well, lock, scan and transfer.</li>
 * </ul>
 */
public final class NovaPose {
	private NovaPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		NovaState s = player.getAttachedOrElse(ModAttachments.NOVA_STATE, null);
		if (s == null || !s.hasPower || !s.suited || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		long now = player.level().getGameTime();
		boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart fist = rightMain ? model.rightArm : model.leftArm;
		ModelPart other = rightMain ? model.leftArm : model.rightArm;
		float side = rightMain ? 1f : -1f;

		if (s.flying) {
			float w = Mth.clamp((Math.abs(FlightPoseHelper.lean(player, partial)) - 10f) / 60f, 0f, 1f);
			if (w > 0f) {
				pose(fist, w, -(float) Math.PI, 0f, 0f);
				pose(other, w, 0.15f, 0f, 0.1f * side);
			}
		}
		if (s.shieldUntil > now) {
			// both forearms crossed in front of the chest
			pose(model.rightArm, 1f, -1.35f, -0.55f, 0f);
			pose(model.leftArm, 1f, -1.35f, 0.55f, 0f);
		}
		if (s.blasting) {
			// the main arm straight along the look, palm out; the other braced at the chest
			fist.xRot = model.head.xRot - (float) Math.PI / 2f;
			fist.yRot = model.head.yRot;
			fist.zRot = 0f;
			pose(other, 1f, -0.9f, 0.6f * side, 0f);
		}
		if (s.slamming) {
			pose(model.rightArm, 1f, -2.95f, 0f, -0.12f);
			pose(model.leftArm, 1f, -2.95f, 0f, 0.12f);
		}
		if (s.overloadUntil > now && s.animId != NovaState.ANIM_OVERLOAD) {
			// the Overload hums through him: arms held a little away from the body
			float b = 0.25f + 0.05f * Mth.sin((now + partial) * 0.4f);
			model.rightArm.zRot += b;
			model.leftArm.zRot -= b;
		}

		if (s.animId == NovaState.ANIM_NONE) {
			return;
		}
		float age = (now - s.animStart) + partial;
		int length = length(s.animId);
		if (age < 0f || age >= length) {
			return;
		}
		float w = Math.min(1f, age / 3f) * Math.min(1f, (length - age) / 4f);
		switch (s.animId) {
			case NovaState.ANIM_VOLLEY -> {
				// both hands thrust forward, fingers spread
				pose(model.rightArm, w, -1.55f, -0.25f, 0f);
				pose(model.leftArm, w, -1.55f, 0.25f, 0f);
			}
			case NovaState.ANIM_PULSE -> {
				// fists driven down and out: the pulse / the landing crouch
				pose(model.rightArm, w, -0.35f, 0f, 0.75f);
				pose(model.leftArm, w, -0.35f, 0f, -0.75f);
				model.head.xRot = Mth.lerp(w, model.head.xRot, 0.35f);
				model.hat.copyFrom(model.head);
			}
			case NovaState.ANIM_SLAM -> {
				pose(model.rightArm, w, -2.95f, 0f, -0.12f);
				pose(model.leftArm, w, -2.95f, 0f, 0.12f);
			}
			case NovaState.ANIM_OVERLOAD, NovaState.ANIM_BURST -> {
				float wide = s.animId == NovaState.ANIM_BURST ? 1.9f : 1.45f;
				pose(model.rightArm, w, -0.25f, 0f, wide);
				pose(model.leftArm, w, -0.25f, 0f, -wide);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.55f);
				model.hat.copyFrom(model.head);
			}
			case NovaState.ANIM_DASH -> {
				pose(fist, w, -2.9f, 0f, 0f);
				pose(other, w, 0.7f, 0f, 0.2f * side);
			}
			case NovaState.ANIM_LAUNCH -> {
				pose(model.rightArm, w, -2.7f, 0f, 0.3f);
				pose(model.leftArm, w, -2.7f, 0f, -0.3f);
			}
			case NovaState.ANIM_WELL -> {
				// the main hand pushed out, palm open, as the singularity opens
				pose(fist, w, -1.6f, -0.15f * side, 0f);
				pose(other, w, -0.4f, 0f, 0.35f * side);
			}
			case NovaState.ANIM_LOCK -> {
				float up = Math.min(1f, age / 8f);
				pose(model.rightArm, w, Mth.lerp(up, -0.4f, -2.4f), 0f, 0.6f);
				pose(model.leftArm, w, Mth.lerp(up, -0.4f, -2.4f), 0f, -0.6f);
			}
			case NovaState.ANIM_SCAN -> {
				// two fingers to the temple
				pose(fist, w, -2.5f, -0.6f * side, -0.1f * side);
				model.head.xRot = Mth.lerp(w, model.head.xRot, -0.15f);
				model.hat.copyFrom(model.head);
			}
			case NovaState.ANIM_TRANSFER -> {
				pose(model.rightArm, w, -1.0f, 0f, 0.9f);
				pose(model.leftArm, w, -1.0f, 0f, -0.9f);
			}
			default -> {
			}
		}
	}

	private static int length(int animId) {
		return switch (animId) {
			case NovaState.ANIM_VOLLEY -> 10;
			case NovaState.ANIM_PULSE -> 12;
			case NovaState.ANIM_SLAM -> 40;
			case NovaState.ANIM_OVERLOAD -> 30;
			case NovaState.ANIM_BURST -> 18;
			case NovaState.ANIM_DASH -> 14;
			case NovaState.ANIM_LAUNCH -> 30;
			case NovaState.ANIM_WELL -> 14;
			case NovaState.ANIM_LOCK -> 20;
			case NovaState.ANIM_SCAN -> 16;
			case NovaState.ANIM_TRANSFER -> 16;
			default -> 0;
		};
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
