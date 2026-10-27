package com.projecthero.mod.client.quirk.zerogravity;

import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.clip;
import static com.projecthero.mod.client.quirk.QuirkFirstPersonPose.f;

import com.projecthero.mod.client.quirk.QuirkFirstPersonPose;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.zerogravity.ZeroGravity;

/**
 * v0.17.1 Zero Gravity revamp: Uravity's first-person arms ({@link QuirkFirstPersonPose}) -- every move's own clip, matched to
 * its third-person keyframes ({@link ZeroGravityPose}): the palm thrust of the touch, the fingertips meeting for "Release!", the
 * heave and drive of the throws, the Gunhead three-hit set, the arms up for Catch (held as long as V is), the swept-back
 * arms of the Weightless Push, the slab heaved overhead, and the spread and clap of Full Release. The Full Release opens in the
 * full-body view (the framework's ultimate rule) and the clap shows her whole body too.
 * <pre>
 *   columns: tick, mainPitch, mainYaw, mainRoll, mainX, mainY, mainZ, offPitch, offYaw, offRoll, offX, offY, offZ
 * </pre>
 */
public final class ZeroGravityFirstPerson {
	private ZeroGravityFirstPerson() {
	}

	private static float[] z(float tick) {
		return f(tick, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public static void register() {
		Quirk q = Quirk.ZERO_GRAVITY;
		// V Touch: the right palm thrust out, fingers spread
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_TOUCH, clip(z(0),
				f(2, -14, 8, 0, 0.02f, 0.05f, 0.08f, 0, 0, 0, 0, 0, 0),
				f(4, -36, 16, -22, 0.12f, 0.2f, -0.36f, 0, 0, 0, 0, 0, 0),
				f(7, -34, 15, -20, 0.12f, 0.19f, -0.33f, 0, 0, 0, 0, 0, 0), z(11)));
		// Shift+V Release: both hands in before the chest, the fingertips of each pressed together
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_RELEASE, clip(z(0),
				f(3, -30, 20, 10, 0.12f, 0.18f, 0.0f, -30, 20, 10, 0.12f, 0.18f, 0.0f),
				f(6, -44, 38, 18, 0.3f, 0.3f, -0.12f, -44, 38, 18, 0.3f, 0.3f, -0.12f),
				f(13, -44, 38, 18, 0.3f, 0.3f, -0.12f, -44, 38, 18, 0.3f, 0.3f, -0.12f), z(18)).twoHanded());
		// rain: hands thrown up, fingertips together high, then swept down as the cloud falls
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_RAIN, clip(z(0),
				f(4, -75, 25, 0, 0.18f, 0.55f, 0.05f, -75, 25, 0, 0.18f, 0.55f, 0.05f),
				f(8, -80, 34, 12, 0.26f, 0.6f, 0.0f, -80, 34, 12, 0.26f, 0.6f, 0.0f),
				f(12, -12, -8, 0, -0.05f, 0.05f, -0.2f, -12, -8, 0, -0.05f, 0.05f, -0.2f),
				f(16, -12, -8, 0, -0.05f, 0.05f, -0.2f, -12, -8, 0, -0.05f, 0.05f, -0.2f), z(20)).twoHanded());
		// hold X Float Self: a tap of the fingertips to her chest, then the arms drift out
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_SELF, clip(z(0),
				f(4, -30, 30, 15, 0.2f, 0.15f, 0.1f, -30, 30, 15, 0.2f, 0.15f, 0.1f),
				f(10, -8, -25, -20, -0.12f, 0.04f, -0.05f, -8, -25, -20, -0.12f, 0.04f, -0.05f),
				f(16, -10, -22, -18, -0.1f, 0.06f, -0.05f, -10, -22, -18, -0.1f, 0.06f, -0.05f), z(22)).twoHanded());
		// X Zero-G Leap: crouch, then both arms flung up
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_LEAP, clip(z(0),
				f(3, 18, 0, 0, 0, -0.18f, 0.1f, 18, 0, 0, 0, -0.18f, 0.1f),
				f(7, -70, -6, 0, 0, 0.5f, 0.0f, -70, -6, 0, 0, 0.5f, 0.0f), z(14)).twoHanded());
		// C Meteor Shower: palms to the ground, then the arms rise with the rubble
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_SHOWER, clip(z(0),
				f(6, 20, 10, 0, 0.05f, -0.2f, -0.05f, 20, 10, 0, 0.05f, -0.2f, -0.05f),
				f(14, -65, -10, 0, -0.04f, 0.5f, -0.05f, -65, -10, 0, -0.04f, 0.5f, -0.05f),
				f(24, -72, -12, 0, -0.05f, 0.55f, -0.05f, -72, -12, 0, -0.05f, 0.55f, -0.05f), z(30)).twoHanded());
		// Shift+Z Debris Shield: the arms circle out
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_SHIELD, clip(z(0),
				f(4, -30, 25, 30, 0.15f, 0.2f, -0.1f, -30, 25, 30, 0.15f, 0.2f, -0.1f),
				f(9, -20, -35, -30, -0.15f, 0.15f, -0.05f, -20, -35, -30, -0.15f, 0.15f, -0.05f), z(18)).twoHanded());
		// R Comet Toss: wound up high behind, then a full-body throw along the aim
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_COMET, clip(z(0),
				f(4, -80, -10, -20, -0.05f, 0.55f, 0.25f, 10, 0, 0, 0, -0.1f, 0.1f),
				f(7, -28, 22, -10, 0.15f, 0.12f, -0.45f, 15, 0, 0, 0, -0.12f, 0.1f),
				f(10, -25, 20, -10, 0.14f, 0.1f, -0.42f, 12, 0, 0, 0, -0.1f, 0.1f), z(14)).twoHanded());
		// Shift+G Slam: the touch, both arms heave it up, then haul it down
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_SLAM, clip(z(0),
				f(3, -34, 14, -15, 0.1f, 0.18f, -0.33f, 0, 0, 0, 0, 0, 0),
				f(7, -85, 10, 0, 0.1f, 0.62f, -0.05f, -85, 10, 0, 0.1f, 0.62f, -0.05f),
				f(12, 12, 15, 0, 0.12f, -0.2f, -0.15f, 12, 15, 0, 0.12f, -0.2f, -0.15f), z(18)).twoHanded());
		// G Gunhead Martial Arts: guard, the right palm strike, the left fingertips' touch, the grab, the arm-lock throw
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_GUNHEAD, clip(z(0),
				f(1, -25, 25, 0, 0.12f, 0.14f, 0.05f, -22, 25, 0, 0.12f, 0.12f, 0.05f),
				f(3, -32, 12, -25, 0.16f, 0.2f, -0.45f, -22, 28, 0, 0.14f, 0.12f, 0.05f),
				f(5, -25, 25, 0, 0.12f, 0.14f, 0.05f, -22, 25, 0, 0.12f, 0.12f, 0.05f),
				f(8, -22, 28, 0, 0.14f, 0.12f, 0.05f, -34, 14, 20, 0.16f, 0.2f, -0.42f),
				f(11, -38, 30, 0, 0.22f, 0.25f, -0.25f, -38, 30, 0, 0.22f, 0.25f, -0.25f),
				f(13, -75, 15, 0, 0.1f, 0.5f, 0.05f, -70, 20, 0, 0.12f, 0.48f, 0.05f),
				f(16, 25, 30, 0, 0.15f, -0.3f, -0.1f, 25, 30, 0, 0.15f, -0.3f, -0.1f),
				f(19, 22, 28, 0, 0.14f, -0.28f, -0.1f, 22, 28, 0, 0.14f, -0.28f, -0.1f), z(24)).twoHanded());
		// Z Field: both palms pushed out, then spread wide
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_FIELD, clip(z(0),
				f(4, -35, 18, 0, 0.12f, 0.2f, -0.35f, -35, 18, 0, 0.12f, 0.2f, -0.35f),
				f(8, -30, -30, -15, -0.12f, 0.18f, -0.3f, -30, -30, -15, -0.12f, 0.18f, -0.3f),
				f(15, -30, -30, -15, -0.12f, 0.18f, -0.3f, -30, -30, -15, -0.12f, 0.18f, -0.3f), z(20)).twoHanded());
		// hold V Catch: both arms up, reaching for whoever falls (held while V is)
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_CATCH, clip(z(0),
				f(4, -56, -8, 0, -0.02f, 0.36f, -0.1f, -56, -8, 0, -0.02f, 0.36f, -0.1f),
				f(40, -62, -10, 4, -0.03f, 0.47f, -0.1f, -58, -6, -4, -0.01f, 0.43f, -0.1f),
				f(80, -58, -6, -4, -0.01f, 0.43f, -0.1f, -62, -10, 4, -0.03f, 0.47f, -0.1f),
				f(600, -56, -8, 0, -0.02f, 0.36f, -0.1f, -56, -8, 0, -0.02f, 0.36f, -0.1f), z(604)).twoHanded());
		// sick: a hand over her mouth
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_SICK, clip(z(0),
				f(6, -55, 42, 10, 0.32f, 0.32f, 0.12f, 0, 0, 0, 0, 0, 0),
				f(44, -56, 44, 10, 0.33f, 0.33f, 0.12f, 0, 0, 0, 0, 0, 0), z(50)));
		// Shift+R Debris Barrage: arms flung wide, then both palms driven forward
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_BARRAGE, clip(z(0),
				f(3, -45, -45, -30, -0.25f, 0.3f, 0.05f, -45, -45, -30, -0.25f, 0.3f, 0.05f),
				f(6, -32, 18, 0, 0.12f, 0.18f, -0.42f, -32, 18, 0, 0.12f, 0.18f, -0.42f),
				f(11, -30, 16, 0, 0.12f, 0.17f, -0.4f, -30, 16, 0, 0.12f, 0.17f, -0.4f), z(15)).twoHanded());
		// Shift+X Weightless Push: the arms swept back and down, a swimmer's kick-off
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_PUSH, clip(z(0),
				f(2, 35, -20, -10, -0.1f, -0.35f, 0.25f, 35, -20, -10, -0.1f, -0.35f, 0.25f),
				f(9, 30, -18, -10, -0.08f, -0.3f, 0.2f, 30, -18, -10, -0.08f, -0.3f, 0.2f), z(13)).twoHanded());
		// Shift+C Gravity Bomb: palms to the ground, then the slab heaved up overhead
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_BOMB, clip(z(0),
				f(5, 25, 15, 0, 0.08f, -0.3f, -0.1f, 25, 15, 0, 0.08f, -0.3f, -0.1f),
				f(12, -88, -6, 0, -0.02f, 0.65f, 0.05f, -88, -6, 0, -0.02f, 0.65f, 0.05f),
				f(26, -86, -6, 0, -0.02f, 0.64f, 0.05f, -86, -6, 0, -0.02f, 0.64f, 0.05f), z(30)).twoHanded());
		// the slab's drop: brought down hard and forward, fingertips together
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_BOMB_DROP, clip(
				f(0, -86, -6, 0, -0.02f, 0.64f, 0.05f, -86, -6, 0, -0.02f, 0.64f, 0.05f),
				f(4, -36, 36, 16, 0.28f, 0.22f, -0.3f, -36, 36, 16, 0.28f, 0.22f, -0.3f),
				f(12, -36, 36, 16, 0.28f, 0.22f, -0.3f, -36, 36, 16, 0.28f, 0.22f, -0.3f), z(16)).twoHanded());
		// Full Release: fingertips spread, arms wide and up as everything rises
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_FULL_RELEASE, clip(z(0),
				f(5, -62, -18, -22, -0.1f, 0.42f, 0.05f, -62, -18, -22, -0.1f, 0.42f, 0.05f),
				f(20, -74, -20, -20, -0.12f, 0.55f, 0.05f, -74, -20, -20, -0.12f, 0.55f, 0.05f),
				f(80, -76, -20, -20, -0.12f, 0.56f, 0.05f, -76, -20, -20, -0.12f, 0.56f, 0.05f), z(84)).twoHanded());
		// "Release!": the hands swing in and the fingertips meet before her chest
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_CLAP, clip(
				f(0, -76, -20, -20, -0.12f, 0.56f, 0.05f, -76, -20, -20, -0.12f, 0.56f, 0.05f),
				f(3, -46, 40, 18, 0.32f, 0.32f, -0.15f, -46, 40, 18, 0.32f, 0.32f, -0.15f),
				f(18, -45, 39, 18, 0.31f, 0.31f, -0.14f, -45, 39, 18, 0.31f, 0.31f, -0.14f), z(24)).twoHanded());
		// Shift + hold V Settle: hands resting on her knees
		QuirkFirstPersonPose.register(q, ZeroGravity.ANIM_SETTLE, clip(z(0),
				f(6, 20, 20, 0, 0.1f, -0.3f, 0.05f, 20, 20, 0, 0.1f, -0.3f, 0.05f),
				f(116, 20, 20, 0, 0.1f, -0.3f, 0.05f, 20, 20, 0, 0.1f, -0.3f, 0.05f), z(120)).twoHanded());
		// the whole body in first person for the clap (the ultimate's first second is the framework's)
		QuirkFirstPersonPose.fullBody(q, ZeroGravity.ANIM_CLAP);
	}
}
