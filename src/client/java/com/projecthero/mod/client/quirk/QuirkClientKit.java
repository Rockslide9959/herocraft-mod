package com.projecthero.mod.client.quirk;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7: one Quirk's client side, registered with {@link QuirkClient#register} from the kit's own client init. The
 * core calls these only for a player holding this kit's Quirk.
 */
public interface QuirkClientKit {
	/** The kit's move poses on the player model; runs at the END of setupAnim. */
	default void applyPose(Player player, HumanoidModel<?> model) {
	}

	/** True while this kit's costume is drawn over the body (the wearer's own second skin layer is hidden). */
	default boolean hidesSkinOverlay(Player player) {
		return false;
	}
}
