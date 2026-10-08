package com.projecthero.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.maxsteel.TurboDraw;

/**
 * v0.14.13: ONE ring finger for every power ring -- the Green Lantern's Power Ring and the Flash Ring are drawn by the
 * same code at the same spot, in both views (third person through their render layers, first person through
 * {@code GreenLanternRingHandMixin}), so they always sit the same way on the hand.
 *
 * <p>Geometry is in the right arm bone's pixel space (y runs down the arm, the fist ends at y = 10, the front face is
 * z = -2, the INNER face -- the side of the hand closest to the body -- is x = +1 on both wide and slim arms). The ring
 * sits on the knuckles right at the body side of the fist, its band wrapping round onto the inner face: in third person
 * it is on the side of the hand nearest the body, and in first person (where the knuckle face is the one you see on the
 * left of your own arm) it sits at the top of the hand, in plain view.
 */
public final class HandRing {
	/** Where the band sits down the arm (y) -- just above the end of the fist. */
	public static final float RING_Y = 8.5f;
	/** How far in from the inner edge of the fist the finger's centre is (on the knuckle face). */
	public static final float FINGER_IN = 0.75f;

	/**
	 * A ring's colours: band, band highlight, bezel, gem (core) and the gem's rim. v0.14.23: plus its id, which keys its
	 * {@link RingPlacement}.
	 */
	public record Palette(String id, int band, int highlight, int bezel, int gemCore, int gem) {
	}

	public static final Palette GREEN_LANTERN = new Palette("green_lantern", 0x1F9A48, 0x9CFFB8, 0x0B3D1C, 0xEFFFF2, 0x5CFF8E);
	public static final Palette FLASH = new Palette("flash", 0xD9A521, 0xFFE58A, 0xC41A1A, 0xFFF4B0, 0xFFD21E);

	/** The ring's lift off bare skin. */
	public static final float SKIN_GAP = 0.08f;
	/**
	 * Set while the first-person hand is drawn ({@code GreenLanternRingHandMixin}). The first-person suit sleeve
	 * ({@code SuperheroFirstPersonArm}) is flush with the arm, not puffed out like the third-person suit model, so there the
	 * ring always sits at {@link #SKIN_GAP}.
	 */
	public static boolean firstPerson;

	/**
	 * v0.14.24: whether the arm being drawn is a slim (Alex) arm. Its outer face is x = -2, a wide (Steve) arm's is x = -3,
	 * so a {@link RingPlacement} tuned on one is moved by that pixel on the other (callers set this from the player's skin
	 * via {@link #arm}).
	 */
	public static boolean slimArm;

	/** v0.14.24: sets {@link #slimArm} for {@code player}'s skin before their ring is drawn. */
	public static void arm(net.minecraft.client.player.AbstractClientPlayer player) {
		slimArm = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
	}

	private HandRing() {
	}

	private static float lift(float gap) {
		return firstPerson ? SKIN_GAP : gap;
	}

	/**
	 * v0.15.15: x of the fist's inner face as the ring's pivot sees it. Both shipped {@link RingPlacement}s turn the ring
	 * 90 degrees about Y, which puts the band on the OUTER face of the hand, whose outward normal is -x -- so a thicker
	 * suit shell has to move the pivot toward -x. (Before this the gap was added toward +x, burying the ring inside a
	 * suit's gauntlet -- "the ring doesn't look like it's on the player's body" when suited.) Identical to the old value
	 * for bare skin (gap = SKIN_GAP).
	 */
	private static float innerFace(float gap) {
		return 1f + 2f * SKIN_GAP - gap;
	}

	/**
	 * Draws the ring. {@code pose} must already be in the arm's space ({@code arm.translateAndRotate}); {@code gap} lifts
	 * it off a suit's gauntlet. Callers add their own extras (the Lantern's halo) round {@link #gemCentre}.
	 */
	public static void draw(PoseStack pose, VertexConsumer vc, float gap, Palette c) {
		gap = lift(gap);
		pose.pushPose();
		pose.scale(1f / 16f, 1f / 16f, 1f / 16f);
		float inner = innerFace(gap);     // the fist's inner face (toward the body), see innerFace
		float front = -2f - gap;          // its front (knuckle) face
		float fx = inner - FINGER_IN;     // finger centre, x: on the knuckles, right by the inner edge
		float y = RING_Y;
		RingPlacement.apply(pose, c.id(), fx, y, front);
		float t = 0.22f;                  // band thickness
		float frontOut = fx - 0.6f;       // outer end of the knuckle run
		float innerBack = front + 1.3f;   // back end of the run round the inner side
		// the band: across the knuckles and round onto the inner side, with a bright top edge (built from its edges so
		// the two runs always meet at the corner, whatever the lift)
		band(vc, pose, frontOut, inner + t, y, 0.3f, front - t, front, c.band());
		band(vc, pose, inner, inner + t, y, 0.3f, front - t, innerBack, c.band());
		band(vc, pose, frontOut - 0.02f, inner + t + 0.04f, y - 0.28f, 0.04f, front - t - 0.04f, front - t + 0.28f, c.highlight());
		band(vc, pose, inner + t - 0.28f, inner + t + 0.04f, y - 0.28f, 0.04f, front - t - 0.04f, innerBack + 0.02f, c.highlight());
		// the bezel on the knuckles, then the gem set proud of it
		box(vc, pose, fx, y, front - 0.36f, 0.5f, 0.5f, 0.14f, c.bezel(), 1f);
		box(vc, pose, fx, y, front - 0.54f, 0.28f, 0.28f, 0.06f, c.gemCore(), 1f);
		box(vc, pose, fx, y, front - 0.52f, 0.38f, 0.38f, 0.05f, c.gem(), 0.9f);
		pose.popPose();
	}

	/**
	 * v0.14.23: for extras drawn round the gem ({@link #gemCentre}) -- applies {@code c}'s hand-tuned placement so they
	 * move with the ring. {@code pose} must already be scaled to arm pixels.
	 */
	public static void place(PoseStack pose, Palette c, float gap) {
		gap = lift(gap);
		RingPlacement.apply(pose, c.id(), innerFace(gap) - FINGER_IN, RING_Y, -2f - gap);
	}

	/** The centre of the gem, in arm pixels (x, y, z), for extras drawn round it. */
	public static float[] gemCentre(float gap) {
		gap = lift(gap);
		return new float[] { innerFace(gap) - FINGER_IN, RING_Y, -2f - gap - 0.5f };
	}

	public static VertexConsumer buffer(net.minecraft.client.renderer.MultiBufferSource buffers) {
		return TurboDraw.buffer(buffers);
	}

	/** A band segment given by its x / z extents (centre y, half-height hy). */
	private static void band(VertexConsumer vc, PoseStack pose, float x0, float x1, float y, float hy, float z0, float z1, int rgb) {
		box(vc, pose, (x0 + x1) / 2f, y, (z0 + z1) / 2f, (x1 - x0) / 2f, hy, (z1 - z0) / 2f, rgb, 1f);
	}

	public static void box(VertexConsumer vc, PoseStack pose, float x, float y, float z, float hx, float hy, float hz, int rgb,
			float alpha) {
		pose.pushPose();
		pose.translate(x, y, z);
		TurboDraw.box(vc, pose.last(), hx, hy, hz, rgb, alpha);
		pose.popPose();
	}
}
