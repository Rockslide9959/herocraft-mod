package com.herocraft.mod.client.symbiote;

import java.util.Map;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.symbiote.SymbioteState;
import com.herocraft.mod.symbiote.SymbioteTransform;

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
 */
public final class SymbioteReveal {
	/** bone name -> reveal threshold, in the three stages: chest / arms+legs / head. */
	private static final Map<String, Float> THRESHOLD = Map.ofEntries(
			Map.entry("armorBody", 0.32f),
			Map.entry("armorRightArm", 0.60f),
			Map.entry("armorLeftArm", 0.62f),
			Map.entry("armorRightLeg", 0.64f),
			Map.entry("armorLeftLeg", 0.66f),
			Map.entry("armorRightBoot", 0.68f),
			Map.entry("armorLeftBoot", 0.70f),
			Map.entry("armorHead", 0.95f));

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
