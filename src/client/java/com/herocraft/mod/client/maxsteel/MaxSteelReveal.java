package com.herocraft.mod.client.maxsteel;

import java.util.Map;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.maxsteel.data.MaxSteelState;

import net.minecraft.world.entity.player.Player;

/**
 * The pixel-by-pixel suit reveal, client side. Rather than a fragment-shader UV-threshold discard
 * (this mod has no shader pipeline), the reveal runs at the granularity of the converted geometry's
 * ~28 addressable bones: each bone has a threshold in {@code [0,1]} and is hidden until the reveal
 * clock passes it. The thresholds follow the spec's order -- chest / core, then torso, shoulders,
 * arms, legs, boots, helmet, faceplate -- so the suit spreads out from the chest and seals last at
 * the face. Suit-down runs the same order in reverse.
 *
 * <p>Driven entirely by the synced {@link MaxSteelState} ({@code transformDir},
 * {@code transformStartTick}, {@code transformDurationTicks}), so every viewer sees the same reveal on
 * a transforming Max Steel, not just the owner.
 */
public final class MaxSteelReveal {
	/** bone name -> reveal threshold. Base ("armor*") bones come before the plates on top of them. */
	private static final Map<String, Float> THRESHOLD = Map.ofEntries(
			Map.entry("armorBody", 0.00f),
			Map.entry("turbo_core", 0.04f),
			Map.entry("chest_plate", 0.09f),
			Map.entry("body_shell", 0.15f),
			Map.entry("abdomen_plate", 0.20f),
			Map.entry("back_plate", 0.24f),
			Map.entry("armorRightArm", 0.32f),
			Map.entry("armorLeftArm", 0.34f),
			Map.entry("right_shoulder", 0.38f),
			Map.entry("left_shoulder", 0.40f),
			Map.entry("right_arm_shell", 0.43f),
			Map.entry("left_arm_shell", 0.45f),
			Map.entry("right_forearm", 0.48f),
			Map.entry("left_forearm", 0.50f),
			Map.entry("armorRightLeg", 0.56f),
			Map.entry("armorLeftLeg", 0.58f),
			Map.entry("right_thigh_plate", 0.61f),
			Map.entry("left_thigh_plate", 0.63f),
			Map.entry("right_leg_shell", 0.65f),
			Map.entry("left_leg_shell", 0.67f),
			Map.entry("right_knee", 0.69f),
			Map.entry("left_knee", 0.70f),
			Map.entry("armorRightBoot", 0.74f),
			Map.entry("armorLeftBoot", 0.75f),
			Map.entry("right_boot", 0.77f),
			Map.entry("left_boot", 0.78f),
			Map.entry("armorHead", 0.82f),
			Map.entry("head_shell", 0.86f),
			Map.entry("helmet_crown", 0.90f),
			Map.entry("chin_guard", 0.93f),
			Map.entry("faceplate", 0.97f));

	private MaxSteelReveal() {
	}

	public static Iterable<String> boneNames() {
		return THRESHOLD.keySet();
	}

	private static MaxSteelState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.MAX_STEEL_STATE, null);
	}

	/** True while a suit-up or suit-down animation is running (so the renderer should apply the reveal). */
	public static boolean isRevealing(Player player) {
		MaxSteelState s = state(player);
		return s != null && s.transformDir != MaxSteelState.DIR_IDLE;
	}

	/** True while the player is in Turbo Stealth -- the suit model should not render. */
	public static boolean isStealthed(Player player) {
		MaxSteelState s = state(player);
		return s != null && s.transformed
				&& s.modeEnum() == com.herocraft.mod.maxsteel.MaxSteelMode.STEALTH;
	}

	/**
	 * Whether {@code boneName} should be hidden this frame. During suit-up a bone is hidden until the
	 * clock reaches its threshold; during suit-down the effective progress is inverted, which retracts
	 * the suit from the face down to the chest.
	 */
	public static boolean hidden(Player player, String boneName) {
		MaxSteelState s = state(player);
		if (s == null || s.transformDir == MaxSteelState.DIR_IDLE) {
			return false;
		}
		Float threshold = THRESHOLD.get(boneName);
		if (threshold == null) {
			return false;
		}
		long elapsed = player.level().getGameTime() - s.transformStartTick;
		float raw = Math.max(0f, Math.min(1f, (float) elapsed / Math.max(1, s.transformDurationTicks)));
		float effective = s.transformDir == MaxSteelState.DIR_SUITING_DOWN ? 1f - raw : raw;
		return threshold > effective;
	}
}
