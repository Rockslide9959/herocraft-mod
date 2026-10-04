package com.projecthero.mod.ironman;

/**
 * v0.14.21 round two: the pure numbers behind the Mark V's real blades (client {@code IronManBladeClient} keeps one
 * value per player, stepped once per client tick from the synced {@code IRON_MAN_BLADES} flag, and the armour renderer
 * plus the first-person arm scale the {@code right_blade} / {@code left_blade} geometry by {@link #eased}). Common code
 * so the gametests can pin the timing. Gameplay ({@link IronManBlade}) is untouched.
 */
public final class IronManBladeLook {
	/** Ticks for a full extend or retract. */
	public static final int EXTEND_TICKS = 6;
	/** Below this the blade bone is hidden outright (and the housing with it). */
	public static final float HIDDEN_BELOW = 0.01f;

	private IronManBladeLook() {
	}

	/** One client tick: move {@code progress} (0 = stowed .. 1 = fully out) toward {@code out}. */
	public static float step(float progress, boolean out) {
		float d = 1f / EXTEND_TICKS;
		return out ? Math.min(1f, progress + d) : Math.max(0f, progress - d);
	}

	/** Linear progress -> the drawn extension (smoothstep: slides out fast and settles). */
	public static float eased(float progress) {
		float x = Math.max(0f, Math.min(1f, progress));
		return x * x * (3f - 2f * x);
	}

	/** Whether the blade geometry is drawn at all at this (eased) extension. */
	public static boolean visible(float extension) {
		return extension >= HIDDEN_BELOW;
	}

	/** Y scale for the blade bone: never exactly 0 (a zero scale breaks the bone's normals). */
	public static float boneScale(float extension) {
		return Math.max(0.04f, Math.min(1f, extension));
	}
}
