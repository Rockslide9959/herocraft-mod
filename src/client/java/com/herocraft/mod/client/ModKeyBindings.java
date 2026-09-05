package com.herocraft.mod.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

/**
 * The six <em>universal</em> HeroPack ability keybindings, plus the power-selection key.
 *
 * <p>There is deliberately exactly one logical set of six ability inputs -- R, G, X, Z, V, C
 * (Primary / Secondary / Movement / Ultimate / Utility-Control / Special Mode) -- shown in
 * Options &gt; Controls under the category "Heropack Abilties".
 * What each slot actually <em>does</em> is contextual and resolved server-side by
 * {@link com.herocraft.mod.hero.AbilityRouter}: when the player currently has Thor's control context
 * (worthy + Mjolnir), the slots route to Thor's existing ability handlers with no behaviour change;
 * otherwise they route to the player's active experimental power. This is why there are six mappings
 * here and not 162.
 *
 * <p>Flight (for Thor) still has no mapping of its own: it is a double-tap of the vanilla jump key
 * while holding Mjolnir -- see {@link HeroCraftModClient}. Experimental Flight is a slot ability like
 * any other.
 *
 * <p>The old Thor-specific bindings (Lightning Strike, Call Mjolnir, ...) are gone from this class;
 * their behaviour is unchanged and now reached through the slot router. Mjolnir's tooltip reads the
 * new slot keys via {@link com.herocraft.mod.item.MjolnirTooltip}.
 */
public final class ModKeyBindings {
	/** Exact display string required by the spec (note the intentional spelling). */
	private static final String CATEGORY = "key.category.herocraft.abilities";

	public static final KeyMapping ABILITY_1 = new KeyMapping(
			"key.herocraft.ability_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
	public static final KeyMapping ABILITY_2 = new KeyMapping(
			"key.herocraft.ability_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
	public static final KeyMapping ABILITY_3 = new KeyMapping(
			"key.herocraft.ability_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
	public static final KeyMapping ABILITY_4 = new KeyMapping(
			"key.herocraft.ability_4", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
	public static final KeyMapping ABILITY_5 = new KeyMapping(
			"key.herocraft.ability_5", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
	public static final KeyMapping ABILITY_6 = new KeyMapping(
			"key.herocraft.ability_6", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY);

	/** All six, indexed 0..5 == slot 1..6. */
	public static final KeyMapping[] ABILITY_SLOTS = {
			ABILITY_1, ABILITY_2, ABILITY_3, ABILITY_4, ABILITY_5, ABILITY_6
	};

	/**
	 * Opens the HeroPack power-selection wheel (switch which owned experimental power occupies the six
	 * slots). Deliberately NOT one of R/G/X/Z/V/C. {@code H} is free once slot 3 (Movement) moved to
	 * {@code X}, and has no vanilla collision in 1.21.1.
	 */
	public static final KeyMapping POWER_SELECT = new KeyMapping(
			"key.herocraft.power_select", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

	/**
	 * Opens the read-only "Your Power" info screen for the active experimental power (its description
	 * and every ability/passive, as in the HeroPack Guide). Default {@code I}; free of vanilla
	 * collisions in 1.21.1 (the inventory is {@code E}).
	 */
	public static final KeyMapping POWER_INFO = new KeyMapping(
			"key.herocraft.power_info", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, CATEGORY);

	/**
	 * Max Steel: Go Turbo / power down. A dedicated toggle so the suit does not depend on holding an
	 * ability key. Default {@code N} -- free of vanilla collisions in 1.21.1. Only does anything for a
	 * player who has bonded with Steel.
	 */
	public static final KeyMapping MAX_STEEL_TRANSFORM = new KeyMapping(
			"key.herocraft.max_steel_transform", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

	private ModKeyBindings() {
	}

	public static void initialize() {
		for (KeyMapping slot : ABILITY_SLOTS) {
			KeyBindingHelper.registerKeyBinding(slot);
		}
		KeyBindingHelper.registerKeyBinding(POWER_SELECT);
		KeyBindingHelper.registerKeyBinding(POWER_INFO);
		KeyBindingHelper.registerKeyBinding(MAX_STEEL_TRANSFORM);
	}
}
