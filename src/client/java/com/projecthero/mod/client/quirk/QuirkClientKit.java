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

	/**
	 * v0.16.7: the kit draws its own status block on the Quirk HUD instead of the generic one-line meter (One For All: Output, the
	 * safe limit and the three limbs). Draw upward from {@code bottom} (exclusive), at most {@code width} wide, and return the top
	 * y of what was drawn; -1 (the default) keeps the generic meter.
	 */
	default int drawStatus(net.minecraft.client.gui.GuiGraphics g, Player player, int x0, int bottom, int width) {
		return -1;
	}
}
