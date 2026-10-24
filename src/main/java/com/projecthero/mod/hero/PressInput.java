package com.projecthero.mod.hero;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the WASD keys the client held when it pressed an ability key, valid only while that press is being handled
 * (set and cleared around {@link AbilityRouter#handleInput} in {@code ModNetworking}). A plain int on the server thread,
 * so nothing outlives the press -- no per-player map to leak.
 */
public final class PressInput {
	public static final int FORWARD = 1;
	public static final int BACK = 2;
	public static final int LEFT = 4;
	public static final int RIGHT = 8;

	/** The bits for the press being handled right now (0 = no keys, or not inside a press). */
	public static int move;

	private PressInput() {
	}

	public static int bits(boolean forward, boolean back, boolean left, boolean right) {
		return (forward ? FORWARD : 0) | (back ? BACK : 0) | (left ? LEFT : 0) | (right ? RIGHT : 0);
	}

	/**
	 * The flat direction the held keys point relative to where the player faces, or {@code fallback} when none are held
	 * (or they cancel out).
	 */
	public static Vec3 flatDirection(ServerPlayer p, int bits, Vec3 fallback) {
		double fwd = ((bits & FORWARD) != 0 ? 1 : 0) - ((bits & BACK) != 0 ? 1 : 0);
		double left = ((bits & LEFT) != 0 ? 1 : 0) - ((bits & RIGHT) != 0 ? 1 : 0);
		if (fwd == 0 && left == 0) {
			return fallback;
		}
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		// vanilla's moveRelative: a left impulse moves along (cos yaw, 0, sin yaw)
		Vec3 l = new Vec3(f.z, 0, -f.x);
		return f.scale(fwd).add(l.scale(left)).normalize();
	}
}
