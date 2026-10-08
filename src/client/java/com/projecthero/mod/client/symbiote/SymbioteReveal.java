package com.projecthero.mod.client.symbiote;

import java.util.Map;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.symbiote.SymbioteState;
import com.projecthero.mod.symbiote.SymbioteTransform;

import net.minecraft.world.entity.player.Player;

/**
 * The Symbiote's piece-by-piece suit reveal, client side -- the direct parallel to
 * {@code MaxSteelReveal}, sized to the Symbiote's much simpler 8-bone rig. Three stages, in the order
 * the user asked for: <b>chest</b>, then <b>arms and legs together</b>, then <b>head</b> last. Each
 * bone is hidden until {@link SymbioteTransform#effectiveProgress} passes its threshold, so a part's
 * armour only appears once {@code SymbioteFxClient}'s particles have finished sweeping over it.
 *
 * <p>Suit-down runs the exact same thresholds against the same (inverted) progress value, so the parts
 * disappear in reverse: head first, then arms and legs, chest last -- see
 * {@link SymbioteTransform#effectiveProgress}.
 *
 * <p>Driven entirely by the synced {@link SymbioteState}, so every viewer sees the same reveal on a
 * transforming Symbiote, not just the owner.
 *
 * <p>v0.15.15: the suit itself is drawn by {@link SymbioteSpread} (pixel by pixel outward from the chest, head last)
 * from {@link #progress}; the per-bone thresholds here are only kept for {@link #hidden}'s callers.
 */
public final class SymbioteReveal {
	/** bone name -> reveal threshold, in the three stages: chest / arms+legs / head. */
	private static final Map<String, Float> THRESHOLD = Map.ofEntries(
			Map.entry("armorBody", 0.10f),
			Map.entry("armorRightArm", 0.24f),
			Map.entry("armorLeftArm", 0.36f),
			Map.entry("armorRightLeg", 0.48f),
			Map.entry("armorLeftLeg", 0.60f),
			Map.entry("armorRightBoot", 0.72f),
			Map.entry("armorLeftBoot", 0.82f),
			Map.entry("armorHead", 0.92f));

	private SymbioteReveal() {
	}

	public static Iterable<String> boneNames() {
		return THRESHOLD.keySet();
	}

	private static SymbioteState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.SYMBIOTE_STATE, null);
	}

	/** True while a suit-up or suit-down animation is running (so the renderer should apply the reveal). */
	public static boolean isRevealing(Player player) {
		SymbioteState s = state(player);
		return s != null && SymbioteTransform.isAnimating(s);
	}

	/** v0.13.19: how far on the suit looks right now (0 bare .. 1 fully on), smoothed with the partial tick. */
	public static float progress(Player player, float partialTick) {
		SymbioteState s = state(player);
		if (s == null) {
			return 1.0f;
		}
		long now = player.level().getGameTime();
		float a = SymbioteTransform.effectiveProgress(s, now);
		float b = SymbioteTransform.effectiveProgress(s, now + 1);
		return Math.max(0.0f, Math.min(1.0f, a + (b - a) * partialTick));
	}

	/** Whether {@code boneName} should be hidden this frame. */
	public static boolean hidden(Player player, String boneName) {
		SymbioteState s = state(player);
		if (s == null || !SymbioteTransform.isAnimating(s)) {
			return false;
		}
		Float threshold = THRESHOLD.get(boneName);
		if (threshold == null) {
			return false;
		}
		float effective = SymbioteTransform.effectiveProgress(s, player.level().getGameTime());
		return threshold > effective;
	}
}
