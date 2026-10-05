package com.projecthero.mod.ironman;

/**
 * v0.14.21: how the H faceplate looks while it lifts (pure math, shared by the client and the gametests). The faceplate
 * bone swings on a hinge at its own top-front edge, against the top-front edge of the helmet, over {@link #LIFT_TICKS}
 * ticks: out and up, and (v0.14.22) it stops at 90 degrees, level with the brow and still in view, with a small
 * mechanical settle. It stays there while the faceplate is open; the rest of the helmet stays on
 * and the face shows through (the helmet's front faces are skipped while raised). Closing runs it backwards and seals.
 * Being a sibling of {@code helmet} under {@code armorHead}, it follows the head at every angle.
 *
 * <p>Linear progress {@code x} (0 shut .. 1 raised) is stepped once per client tick from the synced
 * {@code IRON_MAN_FACEPLATE_OPEN} flag ({@link #step}); {@link #angle} shapes it. Every value is continuous in x.
 */
public final class IronManFaceplateLook {
	public static final int LIFT_TICKS = 10;
	/**
	 * Raised angle about the hinge (degrees, forward = out and up first). v0.14.22, explicit user request: 90 -- the plate
	 * swings up level with the brow and stops there, so it stays in view instead of disappearing over the top of the head
	 * (v0.14.21 used 270, flat on the crown).
	 */
	public static final float RAISED_DEG = 90f;
	/** On reaching the stop the plate dips back by this much and settles again. */
	public static final float SETTLE_DEG = 6f;
	/**
	 * The hinge, bedrock geometry coordinates: exactly the faceplate cube's own top-front edge (origin y 24.15 + size
	 * 7.75 + inflate 0.2 = 32.1; z -4.72 - inflate 0.2 = -4.92; identical in all seven marks), which sits against the
	 * helmet's front. Rotating about it, that edge never moves -- no gap at any angle.
	 */
	public static final float[] HINGE = { 0f, 32.1f, -4.92f };
	/** Above this much lift the helmet's front face is dropped so the face shows (the plate no longer covers it). */
	public static final float FACE_OPEN_AT = 0.12f;
	private static final float LAND_AT = 0.8f;

	private IronManFaceplateLook() {
	}

	public static float step(float x, boolean open) {
		float d = 1f / LIFT_TICKS;
		return open ? Math.min(1f, x + d) : Math.max(0f, x - d);
	}

	/**
	 * The swing angle (degrees) at linear progress {@code x}: a smoothstep swing that lands on the crown at 80%, then a
	 * small bounce back up ({@link #SETTLE_DEG}) and down again -- it never swings past {@link #RAISED_DEG} into the helmet.
	 */
	public static float angle(float x) {
		if (x <= 0f) {
			return 0f;
		}
		if (x >= 1f) {
			return RAISED_DEG;
		}
		if (x < LAND_AT) {
			float u = x / LAND_AT;
			return u * u * (3f - 2f * u) * RAISED_DEG;
		}
		float u = (x - LAND_AT) / (1f - LAND_AT);
		return RAISED_DEG - SETTLE_DEG * (float) Math.sin(Math.PI * u);
	}

	/** Does the face show at progress {@code x}? */
	public static boolean faceOpen(float x) {
		return x > FACE_OPEN_AT;
	}
}
