package com.projecthero.mod.client.miraculous;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.6: one Miraculous hero's client side, registered with {@link MiraculousClient#register} from the kit's own
 * client init. The core calls these only for a player holding this kit's Kwami.
 */
public interface KwamiClientKit {
	/** The kit's move poses (and the weapon-holding idle) on the player model; runs at the END of setupAnim. */
	default void applyPose(Player player, HumanoidModel<?> model) {
	}

	/** True while this kit's suit is drawn over the body (the wearer's own second skin layer and worn armour are hidden). */
	default boolean hidesSkinOverlay(Player player) {
		return false;
	}
}
