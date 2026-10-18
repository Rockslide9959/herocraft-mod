package com.projecthero.mod.client.quirk.darkshadow;

import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.darkshadow.DarkShadow;
import com.projecthero.mod.quirk.data.QuirkState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: Tokoyami's poses (ids 80..99 from the synced {@code animId / animStart}, plus the running moves): he commands
 * Dark Shadow with a pointing arm (Strike, Flurry, Counter, Grab), flings the arm for a Throw, crosses his arms behind
 * the Guard, spreads them for the Fallen Angel's flight, raises a hand for Calm's flash, and hunches clutching his head
 * while it goes berserk or rampages. Negative {@code xRot} swings a limb forward / up.
 */
public final class DarkShadowPose {
	private DarkShadowPose() {
	}

	static int length(int id) {
		return switch (id) {
			case DarkShadow.ANIM_STRIKE, DarkShadow.ANIM_COUNTER -> 10;
			case DarkShadow.ANIM_FLURRY -> 5;
			case DarkShadow.ANIM_GRAB, DarkShadow.ANIM_THROW -> 12;
			case DarkShadow.ANIM_SWALLOW, DarkShadow.ANIM_ANKH, DarkShadow.ANIM_ANGEL -> 14;
			case DarkShadow.ANIM_CALM -> 20;
			case DarkShadow.ANIM_DIVE -> 10;
			default -> 0;
		};
	}

	private static void pose(ModelPart part, float w, float x, float y, float z) {
		part.xRot = Mth.lerp(w, part.xRot, x);
		part.yRot = Mth.lerp(w, part.yRot, y);
		part.zRot = Mth.lerp(w, part.zRot, z);
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	public static void apply(Player player, HumanoidModel<?> model) {
		if (!DarkShadow.is(player)) {
			return;
		}
		QuirkState s = Quirks.peek(player);
		if (s == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float t = player.tickCount + partial;
		ModelPart r = model.rightArm, l = model.leftArm;
		if (DarkShadow.berserk(player) || DarkShadow.rampaging(player)) {
			// losing control: hunched, clutching his head
			float shake = 0.06f * Mth.sin(t * 1.7f);
			model.body.xRot = Mth.lerp(0.8f, model.body.xRot, 0.35f);
			pose(r, 1f, -2.5f + shake, -0.55f, 0.15f);
			pose(l, 1f, -2.5f - shake, 0.55f, -0.15f);
			model.rightLeg.z += 2.5f;
			model.leftLeg.z += 2.5f;
			model.rightLeg.y -= 0.6f;
			model.leftLeg.y -= 0.6f;
			return;
		}
		if (DarkShadow.guarding(player)) {
			pose(r, 1f, -1.25f, -0.75f, 0f);
			pose(l, 1f, -1.1f, 0.75f, 0f);
		}
		if (DarkShadow.angel(player) && !player.onGround()) {
			pose(r, 0.9f, 0.35f, 0f, 0.55f);
			pose(l, 0.9f, 0.35f, 0f, -0.55f);
			model.rightLeg.xRot = 0.15f;
			model.leftLeg.xRot = 0.05f;
		}
		if (DarkShadow.on(player, DarkShadow.V_SWALLOW) || DarkShadow.on(player, DarkShadow.V_GRAB)) {
			pose(r, 1f, -1.5f + model.head.xRot, model.head.yRot, 0f);
		}
		if (player.level() == null) {
			return;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		int len = length(s.animId);
		if (len == 0 || age < 0f || age >= len) {
			return;
		}
		float w = Math.min(1f, age / 2f) * Math.min(1f, (len - age) / 3f);
		switch (s.animId) {
			case DarkShadow.ANIM_STRIKE, DarkShadow.ANIM_FLURRY, DarkShadow.ANIM_COUNTER, DarkShadow.ANIM_GRAB, DarkShadow.ANIM_SWALLOW ->
				// "Dark Shadow!" -- the arm thrown out at the target
				pose(r, w, -1.55f + model.head.xRot, model.head.yRot - 0.1f, 0f);
			case DarkShadow.ANIM_THROW -> {
				float k = ease((age - 2f) / 5f);
				pose(r, w, Mth.lerp(k, -1.5f, -0.4f), Mth.lerp(k, 0.4f, -0.9f), 0f);
				model.body.yRot = Mth.lerp(w, model.body.yRot, Mth.lerp(k, 0.3f, -0.35f));
			}
			case DarkShadow.ANIM_ANGEL -> {
				pose(r, w, 0.2f, 0f, 1.0f);
				pose(l, w, 0.2f, 0f, -1.0f);
			}
			case DarkShadow.ANIM_ANKH -> {
				// the armour closing over him: arms out, fists clenched
				pose(r, w, -0.3f, 0f, 0.9f);
				pose(l, w, -0.3f, 0f, -0.9f);
			}
			case DarkShadow.ANIM_CALM -> pose(l, w, -2.9f, 0.1f, -0.15f);
			case DarkShadow.ANIM_DIVE -> {
				model.body.xRot = Mth.lerp(w, model.body.xRot, 0.45f);
				pose(r, w, 0.9f, 0f, 0.3f);
				pose(l, w, 0.9f, 0f, -0.3f);
			}
			default -> {
			}
		}
	}
}
