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

	public static void apply(Player player, HumanoidModel<?> m) {
		MiraculousState s = Miraculous.peek(player);
		if (s == null || player.level() == null) {
			return;
		}
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		// the ready stance: the sword arm a little forward, whenever nothing else moves it
		if (RyukoRender.cover(player, partial) > 0.99f && player.getAttackAnim(partial) <= 0f) {
			set(m.rightArm, 0.65f, -0.5f, 0f, 0.05f);
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
