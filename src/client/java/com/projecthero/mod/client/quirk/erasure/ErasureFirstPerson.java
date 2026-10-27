package com.projecthero.mod.client.quirk.erasure;

import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.clip;
import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.f;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.erasure.ErasureConfig;

/**
 * v0.17.1 Erasure revamp: Eraser Head's first-person arms ({@link QuirkFirstPersonPose}), one clip per move matched to his
 * third-person keyframes ({@link ErasurePose}). The scarf is always thrown from the RIGHT (main) hand; the cloth itself is
 * drawn from the first-person hand by {@link ErasureWorld}. Held moves (the stare, the swing, Reel Lock) have long clips that
 * the kit ends when the key is let go. The Evade Flip and the takedown's kicks show his whole body.
 * <pre>
 *   columns: tick, mainPitch, mainYaw, mainRoll, mainX, mainY, mainZ, offPitch, offYaw, offRoll, offX, offY, offZ
 * </pre>
 */
public final class ErasureFirstPerson {
	private ErasureFirstPerson() {
	}

	private static float[] z(float tick) {
		return f(tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static void register() {
		Quirk q = Quirk.ERASURE;
		// R Capture Bind: the scarf circled overhead, thrown, then both hands tug the wraps tight
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_BIND, clip(z(0),
				f(3, -78, -10, -30, 0.02f, 0.55f, 0.2f, 0, 0, 0, 0, 0, 0),
				f(5, -32, 18, -15, 0.12f, 0.2f, -0.45f, 0, 0, 0, 0, 0, 0),
				f(9, -25, 25, 0, 0.18f, 0.15f, -0.2f, -22, 30, 0, 0.18f, 0.12f, -0.15f),
				f(12, -15, 22, 0, 0.16f, 0.08f, 0.05f, -14, 28, 0, 0.16f, 0.06f, 0.08f), z(16)).twoHanded());
		// Shift+R Capture Pull: whip out, then a two-handed yank back
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_PULL, clip(z(0),
				f(2, -60, -5, -20, 0.05f, 0.4f, 0.15f, 0, 0, 0, 0, 0, 0),
				f(4, -30, 18, -12, 0.12f, 0.18f, -0.45f, 0, 0, 0, 0, 0, 0),
				f(7, -5, 25, 10, 0.15f, -0.05f, 0.22f, -8, 30, 10, 0.16f, -0.02f, 0.18f),
				f(10, -6, 24, 10, 0.15f, -0.04f, 0.2f, -8, 30, 10, 0.16f, -0.02f, 0.16f), z(14)).twoHanded());
		// hold R Reel Lock: both hands hauling the line up, hand over hand (held while R is)
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_REEL, clip(z(0),
				f(4, -50, 20, 0, 0.14f, 0.38f, -0.1f, -30, 26, 0, 0.16f, 0.2f, -0.05f),
				f(10, -30, 22, 0, 0.14f, 0.2f, 0.05f, -52, 24, 0, 0.16f, 0.4f, -0.12f),
				f(16, -50, 20, 0, 0.14f, 0.38f, -0.1f, -30, 26, 0, 0.16f, 0.2f, -0.05f),
				f(22, -30, 22, 0, 0.14f, 0.2f, 0.05f, -52, 24, 0, 0.16f, 0.4f, -0.12f),
				f(28, -50, 20, 0, 0.14f, 0.38f, -0.1f, -30, 26, 0, 0.16f, 0.2f, -0.05f),
				f(34, -30, 22, 0, 0.14f, 0.2f, 0.05f, -52, 24, 0, 0.16f, 0.4f, -0.12f),
				f(40, -50, 20, 0, 0.14f, 0.38f, -0.1f, -30, 26, 0, 0.16f, 0.2f, -0.05f),
				f(130, -42, 21, 0, 0.14f, 0.3f, -0.05f, -42, 25, 0, 0.16f, 0.3f, -0.08f), z(134)).twoHanded());
		// G Close Combat: jab, elbow, knee (guard), the spinning kick (guard)
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_COMBO, clip(z(0),
				f(1, -10, 20, 0, 0.1f, 0.05f, 0.1f, -20, 22, 0, 0.1f, 0.1f, 0.05f),
				f(2, -28, 8, -18, 0.12f, 0.16f, -0.48f, -20, 22, 0, 0.1f, 0.1f, 0.05f),
				f(4, -20, 22, 0, 0.1f, 0.1f, 0.05f, -45, 60, 30, 0.32f, 0.25f, -0.25f),
				f(6, -24, 26, 0, 0.12f, 0.12f, 0.0f, -26, 26, 0, 0.12f, 0.12f, 0.0f),
				f(10, -30, 30, 0, 0.14f, 0.18f, -0.05f, -30, 30, 0, 0.14f, 0.18f, -0.05f),
				f(16, -20, -40, -30, -0.2f, 0.12f, 0.0f, -20, -40, -30, -0.2f, 0.12f, 0.0f), z(22)).twoHanded());
		// Shift+G Counter: forearms crossed in a low guard
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_COUNTER, clip(z(0),
				f(2, -40, 45, 25, 0.32f, 0.25f, -0.1f, -38, 45, 25, 0.32f, 0.22f, -0.08f),
				f(12, -40, 45, 25, 0.32f, 0.25f, -0.1f, -38, 45, 25, 0.32f, 0.22f, -0.08f), z(15)).twoHanded());
		// the riposte: the guard opens into a palm strike
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_COUNTER_HIT, clip(
				f(0, -40, 45, 25, 0.32f, 0.25f, -0.1f, -38, 45, 25, 0.32f, 0.22f, -0.08f),
				f(3, -30, 10, -20, 0.12f, 0.18f, -0.5f, 10, 10, 0, 0, -0.1f, 0.1f),
				f(8, -28, 10, -18, 0.12f, 0.17f, -0.46f, 10, 10, 0, 0, -0.1f, 0.1f), z(14)).twoHanded());
		// X Scarf Pull: both hands snap up the line as he is reeled in
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_ZIP, clip(z(0),
				f(3, -70, 10, 0, 0.06f, 0.5f, -0.2f, -62, 14, 0, 0.08f, 0.45f, -0.15f),
				f(9, -66, 10, 0, 0.06f, 0.46f, -0.2f, -60, 14, 0, 0.08f, 0.42f, -0.15f), z(13)).twoHanded());
		// hold X Scarf Swing: one hand up on the line, the other out for balance (held while X is)
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_SWING, clip(z(0),
				f(3, -78, 12, 0, 0.05f, 0.58f, -0.2f, 10, -35, -20, -0.15f, -0.05f, 0.05f),
				f(40, -80, 14, 0, 0.05f, 0.6f, -0.2f, 6, -38, -20, -0.15f, -0.04f, 0.05f),
				f(80, -78, 12, 0, 0.05f, 0.58f, -0.2f, 10, -35, -20, -0.15f, -0.05f, 0.05f),
				f(400, -78, 12, 0, 0.05f, 0.58f, -0.2f, 10, -35, -20, -0.15f, -0.05f, 0.05f), z(404)).twoHanded());
		// Shift+X Evade Flip: the arms fling up as he springs back
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_FLIP, clip(z(0),
				f(2, -75, -15, 0, -0.05f, 0.55f, 0.1f, -75, -15, 0, -0.05f, 0.55f, 0.1f), z(10)).twoHanded());
		// Z Bind and Spin: both hands out on the scarf, leaning against the weight
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_SPIN, clip(z(0),
				f(3, -38, 18, 0, 0.12f, 0.24f, -0.3f, -34, 26, 0, 0.14f, 0.2f, -0.25f),
				f(28, -38, 18, 0, 0.12f, 0.24f, -0.3f, -34, 26, 0, 0.14f, 0.2f, -0.25f),
				f(31, -10, -30, -20, -0.1f, 0.1f, -0.4f, 0, 0, 0, 0, 0, 0), z(36)).twoHanded());
		// Shift+Z Disarm Throw: the whip at the hand, then the rip back over the shoulder
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_DISARM, clip(z(0),
				f(3, -30, 15, -12, 0.12f, 0.18f, -0.42f, 0, 0, 0, 0, 0, 0),
				f(7, -82, -15, -25, -0.02f, 0.58f, 0.3f, 0, 0, 0, 0, 0, 0),
				f(9, -80, -15, -25, -0.02f, 0.56f, 0.28f, 0, 0, 0, 0, 0, 0), z(14)));
		// C Scarf Whip: overhead, then a snapping crack down along the look
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_WHIP, clip(z(0),
				f(2, -88, -18, -30, -0.05f, 0.62f, 0.25f, 0, 0, 0, 0, 0, 0),
				f(4, -18, 20, -10, 0.14f, 0.05f, -0.5f, 0, 0, 0, 0, 0, 0),
				f(6, -14, 20, -8, 0.13f, 0.03f, -0.46f, 0, 0, 0, 0, 0, 0), z(11)));
		// Shift+C Multi-Bind: wound across, then the wide fanning sweep, then both fists cinch the ties
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_MULTI, clip(z(0),
				f(3, -32, 55, 10, 0.4f, 0.2f, -0.1f, 0, 0, 0, 0, 0, 0),
				f(7, -30, -45, -25, -0.28f, 0.2f, -0.4f, 0, 0, 0, 0, 0, 0),
				f(11, -20, 20, 0, 0.14f, 0.1f, 0.05f, -20, 28, 0, 0.16f, 0.1f, 0.05f),
				f(14, -18, 20, 0, 0.14f, 0.08f, 0.08f, -18, 28, 0, 0.16f, 0.08f, 0.08f), z(18)).twoHanded());
		// Shift+V Goggles: both hands up to the goggles
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_GOGGLES, clip(z(0),
				f(3, -78, 30, 0, 0.18f, 0.55f, 0.28f, -78, 30, 0, 0.18f, 0.55f, 0.28f),
				f(6, -72, 30, 0, 0.18f, 0.5f, 0.25f, -72, 30, 0, 0.18f, 0.5f, 0.25f), z(10)).twoHanded());
		// Eye Drops (the item): the bottle held over one eye, then the other
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_DROPS, clip(z(0),
				f(4, -80, 35, 0, 0.22f, 0.6f, 0.3f, 0, 0, 0, 0, 0, 0),
				f(9, -80, 35, 0, 0.22f, 0.6f, 0.3f, 0, 0, 0, 0, 0, 0),
				f(12, -80, 15, 0, 0.12f, 0.6f, 0.3f, 0, 0, 0, 0, 0, 0),
				f(17, -80, 15, 0, 0.12f, 0.6f, 0.3f, 0, 0, 0, 0, 0, 0), z(22)));
		// eyes forced shut: a hand rubs them
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_BLINK, clip(z(0),
				f(4, -72, 40, 10, 0.3f, 0.5f, 0.25f, 0, 0, 0, 0, 0, 0),
				f(8, -70, 30, 10, 0.24f, 0.48f, 0.25f, 0, 0, 0, 0, 0, 0),
				f(12, -72, 40, 10, 0.3f, 0.5f, 0.25f, 0, 0, 0, 0, 0, 0),
				f(16, -70, 30, 10, 0.24f, 0.48f, 0.25f, 0, 0, 0, 0, 0, 0), z(22)));
		// hold V Erasure Stare: both hands at the chest holding the scarf ready (held while V is)
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_STARE, clip(z(0),
				f(4, -22, 30, 10, 0.2f, 0.12f, 0.05f, -18, 34, 10, 0.2f, 0.1f, 0.06f),
				f(600, -22, 30, 10, 0.2f, 0.12f, 0.05f, -18, 34, 10, 0.2f, 0.1f, 0.06f), z(604)).twoHanded());
		// Underground Takedown: the goggles snap down, then the right hand flicks the scarf out at the first one
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_TAKEDOWN, clip(z(0),
				f(2, -78, 30, 0, 0.18f, 0.55f, 0.28f, -78, 30, 0, 0.18f, 0.55f, 0.28f),
				f(4, -74, 30, 0, 0.18f, 0.52f, 0.26f, -74, 30, 0, 0.18f, 0.52f, 0.26f),
				f(8, -30, 15, -12, 0.12f, 0.18f, -0.45f, -15, 30, 0, 0.12f, 0.05f, 0.1f), z(13)).twoHanded());
		QuirkFirstPersonPose.register(q, ErasureConfig.ANIM_TD_KICK, clip(z(0),
				f(2, 15, -30, -10, -0.15f, -0.1f, 0.1f, -30, 35, 0, 0.2f, 0.15f, -0.1f), z(7)).twoHanded());
		// whole-body first person: the flip, the spin, the takedown's kicks
		QuirkFirstPersonPose.fullBody(q, ErasureConfig.ANIM_FLIP);
		QuirkFirstPersonPose.fullBody(q, ErasureConfig.ANIM_TD_KICK);
	}
}
