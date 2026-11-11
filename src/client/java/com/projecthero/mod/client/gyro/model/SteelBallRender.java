package com.projecthero.mod.client.gyro.model;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;

/**
 * v0.17.3 Gyro Zeppeli: draws one Zeppeli Steel Ball. CONTRACT shared by the two build agents -- the ART agent replaces this stub
 * with the real model (green metal ball, raised hexagon plate, curved grooves; see {@code docs/reference/gyro/}); the POWER agent
 * calls it from every place a ball is drawn: the thrown-ball entity renderer, the hand, the belt, Scan, Spin Shield orbit, the
 * Golden Spin shot. Keep the signature.
 */
public final class SteelBallRender {
	private SteelBallRender() {
	}

	/**
	 * Draws a ball centred on the pose's origin.
	 *
	 * @param pose     positioned at the ball's centre
	 * @param buffers  the render buffers
	 * @param light    packed light
	 * @param radius   ball radius in blocks (about 0.11 in the hand / on the belt, larger for effects)
	 * @param spinDeg  current spin angle in degrees (the caller advances it; the ball spins about its local Y axis)
	 * @param golden   true while the ball carries a Golden Spin (gold glow / sheen)
	 */
	public static void render(PoseStack pose, MultiBufferSource buffers, int light, float radius, float spinDeg, boolean golden) {
		// stub: the ART agent draws the model here
	}
}
