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

	/**
	 * v0.16.7: the overload line the HUD shows instead of "OVERLOADED 6s" (Dark Shadow's Berserk lasts until light, not a
	 * number of seconds); null (the default) keeps the generic line. It is wrapped to the HUD's width.
	 */
	default net.minecraft.network.chat.Component overloadLine(Player player) {
		return null;
	}

	// ---------------------------------------------------------------- v0.17.1 framework hooks

	/**
	 * The kit's own meter widget (a thermometer, a sweat-droplet bar, a light / dark dial...) in place of the generic banded
	 * bar, inside the bar's box {@code v.x(), v.y(), v.w(), v.h()} (it may draw a little outside it). Return true when drawn;
	 * false (the default) keeps the generic banded bar. {@link QuirkHud#drawBands} draws the shared band ticks, shimmer and
	 * pulse onto any rectangle, so a widget can reuse them.
	 */
	default boolean drawMeterWidget(net.minecraft.client.gui.GuiGraphics g, Player player, QuirkHud.MeterView v) {
		return false;
	}

	/** 0..1 of a hold-charge on this key's move right now (the HUD rings the key box), or -1 for none. */
	default float holdCharge(Player player, com.projecthero.mod.hero.AbilitySlot slot) {
		return -1f;
	}

	/**
	 * The Quirk's active "tell" on the player model (the flame beard, palm sparks, fingertip pads...), drawn by the framework
	 * layer for every viewer. Return true when the kit drew its own; false (the default) gets the framework's generic tell:
	 * a soft glow at both hands in the Quirk's colour while the meter is in its danger zone (or pushed past 100), and while
	 * the Plus Ultra C is being held.
	 */
	default boolean renderTell(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
			Player player, net.minecraft.client.model.HumanoidModel<?> model, float partial) {
		return false;
	}

	/** In first person, show the whole body right now (a kit's own big move); the framework adds ultimates and marked anims. */
	default boolean fullBodyFirstPerson(Player player) {
		return false;
	}

	/**
	 * The body pose while the Plus Ultra C is held (0..1 {@code charge}). Return true when the kit posed the model; false
	 * (the default) plays the framework's braced "power up" stance ({@link QuirkClient#ultimateHoldPose}).
	 */
	default boolean ultimateHoldPose(Player player, HumanoidModel<?> model, float charge) {
		return false;
	}
}
