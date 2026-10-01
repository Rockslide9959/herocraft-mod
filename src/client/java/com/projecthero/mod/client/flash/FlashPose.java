package com.projecthero.mod.client.flash;

import com.projecthero.mod.flash.FlashFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.11: the arms through a Flash Ring transition (set on the vanilla humanoid model from {@code HumanoidModelMixin};
 * the armour copies it). Suit-up: the ring fist comes up in front of the face and the thumb pops the catch, then both
 * arms flare out from the body as the suit wraps on and the whirl spins, then settle. Suit-down: the ring fist held
 * out in front, the suit streaming back into it, the other arm swept back. Negative {@code xRot} swings an arm forward.
 */
public final class FlashPose {
	private FlashPose() {
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = FlashSuitReveal.age(player, partial);
		if (t < 0f) {
			return;
		}
		if (FlashSuitReveal.dir(player) == FlashFx.UP) {
			// ring fist up (0-5), flare out (5-24), settle (24-30)
			float raise = Math.min(1f, t / 3f) * (1f - FlashSuitReveal.smooth((t - FlashSuitReveal.EMERGE) / 4f));
			float flare = FlashSuitReveal.smooth((t - FlashSuitReveal.EMERGE + 1f) / 4f)
					* (1f - FlashSuitReveal.smooth((t - FlashSuitReveal.REVEAL_END) / 5f));
			pose(model.rightArm, raise, -1.9f, -0.55f, 0.1f);
			model.head.xRot = Mth.lerp(raise, model.head.xRot, 0.25f); // eyes on the ring
			pose(model.rightArm, flare, -0.25f, 0f, 0.95f);
			pose(model.leftArm, flare, -0.25f, 0f, -0.95f);
		} else {
			float w = Math.min(1f, t / 3f) * Math.min(1f, (FlashSuitReveal.DOWN_END + 2f - t) / 3f);
			pose(model.rightArm, w, -1.55f, -0.2f, 0f);
			pose(model.leftArm, w, 0.45f, 0f, -0.2f);
		}
		model.hat.copyFrom(model.head);
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		if (w <= 0f) {
			return;
		}
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}
}
