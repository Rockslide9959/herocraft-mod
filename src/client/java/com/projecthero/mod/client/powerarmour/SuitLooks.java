package com.projecthero.mod.client.powerarmour;

import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.SuitData;
import com.projecthero.mod.powerarmour.entity.PowerArmourFrameEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** v0.17.5 Power Armour: turns a frame's or a wearer's synced state into a {@link SuitRenderer.Look} (openness, glow, effects). */
public final class SuitLooks {
	private SuitLooks() {
	}

	/** A standing frame: dark visor (a faint standby glow when it has charge), opening / closing with its synced animation. */
	public static SuitRenderer.Look frame(PowerArmourFrameEntity frame, float time) {
		SuitData suit = frame.suit();
		SuitRenderer.Look look = new SuitRenderer.Look(suit);
		float t = time - frame.animStart();
		switch (frame.anim()) {
			case PowerArmourFrameEntity.OPENING -> look.sequence(Mth.clamp(t / PowerArmourConfig.FRAME_OPEN_TICKS, 0f, 1f), true);
			case PowerArmourFrameEntity.OPEN -> look.allOpen(1f);
			case PowerArmourFrameEntity.CLOSING -> look.sequence(Mth.clamp(t / PowerArmourConfig.FRAME_CLOSE_TICKS, 0f, 1f), false);
			default -> look.allOpen(0f);
		}
		look.glow = suit.powered() ? 0.18f + 0.05f * Mth.sin(time * 0.08f) : 0f;
		look.time = time;
		return look;
	}

	/** The suit on a player, or null when they are not wearing one. */
	public static SuitRenderer.Look wearer(Player player, float time) {
		PowerArmourState s = com.projecthero.mod.powerarmour.PowerArmour.state(player);
		if (s == null || !s.wearing()) {
			return null;
		}
		SuitRenderer.Look look = new SuitRenderer.Look(s.suit);
		float t = time - s.phaseStart;
		boolean powered = s.suit.powered();
		switch (s.phase) {
			case PowerArmourState.SEALING -> {
				float p = Mth.clamp(t / PowerArmourConfig.SEAL_TICKS, 0f, 1f);
				look.sequence(p, false);
				look.glow = powered ? Mth.clamp((p - 0.93f) / 0.07f, 0f, 1f) : 0f;
			}
			case PowerArmourState.UNSEALING -> {
				float p = Mth.clamp(t / PowerArmourConfig.UNSEAL_TICKS, 0f, 1f);
				look.sequence(p, true);
				look.glow = powered ? Math.max(0f, 1f - p * 3f) : 0f;
			}
			default -> {
				look.allOpen(0f);
				look.glow = powered ? 1f : 0.06f;
			}
		}
		look.time = time;
		look.thrust = s.thrusting;
		long now = player.level().getGameTime();
		look.jets = s.anim == com.projecthero.mod.powerarmour.kit.PowerArmourAbilities.ANIM_JETS && now - s.animStart < 14;
		look.firing = s.charge == 4;
		look.backDocked = s.droneUntil <= now && !com.projecthero.mod.powerarmour.kit.PowerArmourAbilities.turretOut(s, now);
		return look;
	}

	/** The six parts of a humanoid model in {@link SuitPanel.Part} order. */
	public static ModelPart[] parts(HumanoidModel<?> model) {
		return new ModelPart[] { model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg };
	}

	/** A frame's standing pose (arms a little out from the body, everything else straight). */
	public static void standPose(PlayerModel<?> model) {
		for (ModelPart p : parts(model)) {
			p.resetPose();
			p.xRot = 0f;
			p.yRot = 0f;
			p.zRot = 0f;
		}
		model.rightArm.zRot = 0.09f;
		model.leftArm.zRot = -0.09f;
		model.rightArm.xRot = -0.04f;
		model.leftArm.xRot = -0.04f;
		model.rightLeg.zRot = 0.02f;
		model.leftLeg.zRot = -0.02f;
	}
}
