package com.projecthero.mod.client.wolverine;

import com.projecthero.mod.wolverine.Wolverine;

import net.minecraft.world.entity.player.Player;

/**
 * The Claw Dash lunge pose (v0.12.12): body pitched forward, both arms thrust ahead with the claws
 * leading. Keys off the synced {@code dashUntil}, so every viewer sees it.
 */
public final class WolverineDashPose {
	/** Body pitch at full lunge, degrees. */
	public static final float LEAN_DEGREES = 55.0f;

	private WolverineDashPose() {
	}

	/** 0..1 -- how far into the lunge pose the player is (0 when not dashing). */
	public static float amount(Player player) {
		return Wolverine.dashing(player) ? 1.0f : 0.0f;
	}
}
