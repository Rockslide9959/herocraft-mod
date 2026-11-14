package com.projecthero.mod.client.miraculous.ryuko;

import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ryuko.Ryuko;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** v0.17.3: Ryuko's move poses on the humanoid model. Negative {@code xRot} swings an arm forward / up. */
public final class RyukoPose {
	private RyukoPose() {
	}

	public static int length(int animId) {
		return switch (animId) {
			case Ryuko.ANIM_STRIKE, Ryuko.ANIM_STRIKE_2, Ryuko.ANIM_STRIKE_3 -> 9;
			case Ryuko.ANIM_LUNGE -> 10;
			case Ryuko.ANIM_ELEMENT -> 10;
			case Ryuko.ANIM_BURST -> 16;
			case Ryuko.ANIM_THROW -> 10;
			case Ryuko.ANIM_PARRY -> 12;
			case Ryuko.ANIM_RIPOSTE -> 10;
			case Ryuko.ANIM_FORM -> 18;
			default -> 0;
		};
	}

	/** The two-handed guard: right arm forward and turned in, left arm reaching across to the hilt. */
	static final float STANCE_RX = -0.9f, STANCE_RY = -0.8f, STANCE_LX = -0.6f, STANCE_LY = 1.03f, STANCE_LZ = 0.22f;
	/** The wrist's turn about the arm in the guard (radians), so the blade points ahead rather than across her body. */
	static final float STANCE_WRIST_YAW = 0.45f;

	/** True while she stands in the two-handed guard: suited, sword out, and no swing or move of hers playing. */
	public static boolean stance(Player player, float partial) {
		if (RyukoRender.cover(player, partial) <= 0.99f || Ryuko.swordAway(player) || !RyukoSequenceClient.swordOut(player, partial)
				|| player.getAttackAnim(partial) > 0f) {
			return false;
		}
		MiraculousState s = Miraculous.peek(player);
		if (s != null && s.animId >= 200 && s.animId <= 219 && player.level() != null) {
			float age = player.level().getGameTime() - s.animStart + partial;
			if (age >= 0f && age < length(s.animId)) {
				return false;
			}
		}
		return true;
	}

	public static void apply(Player player, HumanoidModel<?> m) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		// the ready stance (v0.17.4, user: "ryuko doesnt hold the sword properly"): a two-handed chudan guard whenever nothing else
		// moves her arms -- both fists forward in front of her belt, the right at the guard, the left closed on the hilt behind it,
		// the blade pointing up and forward at the opponent's throat. Arm angles solved so the left fist lands on the tsuka
		// for wide and slim arms alike (scratchpad grip solver); the wrist yaw that keeps the blade on her centre line is in
		// RyukoLayer.placeInHand.
		if (stance(player, partial)) {
			set(m.rightArm, 1f, STANCE_RX, STANCE_RY, 0f);
			set(m.leftArm, 1f, STANCE_LX, STANCE_LY, STANCE_LZ);
		}
		if (s.animId < 200 || s.animId > 219) {
			return;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		int len = length(s.animId);
		if (age < 0f || age >= len) {
			return;
		}
		float t = age / len;
		float w = Mth.sin(t * Mth.PI);
		switch (s.animId) {
			case Ryuko.ANIM_STRIKE -> set(m.rightArm, 1f, Mth.lerp(t, -2.6f, -0.6f), Mth.lerp(t, 0.7f, -0.6f), 0f);
			case Ryuko.ANIM_STRIKE_2 -> set(m.rightArm, 1f, Mth.lerp(t, -1.5f, -1.3f), Mth.lerp(t, -0.9f, 0.9f), 0f);
			case Ryuko.ANIM_STRIKE_3 -> {
				set(m.rightArm, 1f, Mth.lerp(t, -3.0f, -0.4f), 0f, 0f);
				set(m.leftArm, w, -1.2f, 0.4f, 0f);
			}
			case Ryuko.ANIM_LUNGE, Ryuko.ANIM_RIPOSTE -> {
				set(m.rightArm, w, m.head.xRot - 1.55f, m.head.yRot, 0f);
				set(m.leftArm, w, 0.9f, 0f, -0.3f);
				set(m.rightLeg, w, -0.7f, 0f, 0f);
				set(m.leftLeg, w, 0.6f, 0f, 0f);
			}
			case Ryuko.ANIM_ELEMENT -> set(m.rightArm, w, m.head.xRot - 1.55f, m.head.yRot - 0.3f, 0f);
			case Ryuko.ANIM_BURST -> {
				set(m.rightArm, w, 0f, 0f, 1.45f);
				set(m.leftArm, w, 0f, 0f, -1.45f);
			}
			case Ryuko.ANIM_THROW -> set(m.rightArm, 1f, Mth.lerp(t, -2.8f, -0.9f), 0f, 0f);
			case Ryuko.ANIM_PARRY -> set(m.rightArm, w, -1.5f, -0.7f, 0f);
			case Ryuko.ANIM_FORM -> {
				set(m.rightArm, w, -2.9f, 0f, 0.15f);
				set(m.leftArm, w, -2.9f, 0f, -0.15f);
			}
			default -> {
			}
		}
	}

	private static void set(ModelPart part, float weight, float x, float y, float z) {
		part.xRot = Mth.lerp(weight, part.xRot, x);
		part.yRot = Mth.lerp(weight, part.yRot, y);
		part.zRot = Mth.lerp(weight, part.zRot, z);
	}
}
