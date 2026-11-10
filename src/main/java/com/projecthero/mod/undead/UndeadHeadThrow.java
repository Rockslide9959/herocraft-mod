package com.projecthero.mod.undead;

import net.minecraft.server.level.ServerPlayer;

/**
 * v0.17.3 (user): Hold X (2 s) -- Head Throw. Stub contract shared between the X key routing and the move itself:
 * {@link #start} when the 2 s hold completes, {@link #tickHeld} every tick while X stays held after that, {@link #release} when X
 * is let go, {@link #cancel} if the hold is interrupted. {@link #aiming} is true while the head is held back (the player can't
 * move then).
 */
public final class UndeadHeadThrow {
	private UndeadHeadThrow() {
	}

	public static void start(ServerPlayer p) {
	}

	public static void tickHeld(ServerPlayer p) {
	}

	public static void release(ServerPlayer p) {
	}

	public static void cancel(ServerPlayer p) {
	}

	public static boolean aiming(net.minecraft.world.entity.player.Player p) {
		return false;
	}
}
