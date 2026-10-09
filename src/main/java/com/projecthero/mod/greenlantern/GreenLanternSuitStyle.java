package com.projecthero.mod.greenlantern;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.15, explicit user request ("players can press N to choose between different suits"): the suits a Power Ring can
 * form, picked on the N suit screen and kept in {@link com.projecthero.mod.greenlantern.data.GreenLanternState#suitStyle}.
 *
 * <p>All four are drawn on the same {@code geo/green_lantern.geo.json} armour (a 64x64 player-skin rig, head included since
 * v0.15.15), so they share the suit-up sweep, the first-person sleeve and everything else -- only the texture changes. The
 * three new ones are the user's own skins with the wearer's skin and hair stripped out, so the player's own face and hands
 * show through: the Corps uniform and Classic Hal Jordan keep a green domino mask over the eyes, John Stewart has none and
 * bare hands.
 */
public enum GreenLanternSuitStyle {
	DEFAULT("default", "textures/armor/green_lantern.png"),
	CORPS("corps", "textures/entity/green_lantern/suits/corps.png"),
	STEWART("stewart", "textures/entity/green_lantern/suits/stewart.png"),
	CLASSIC("classic", "textures/entity/green_lantern/suits/classic.png"),
	/** v0.15.16: the user's two new skins, stripped the same way (scratchpad/v01516_gl) -- Kyle Rayner (black suit, green mask)... */
	MIDNIGHT("midnight", "textures/entity/green_lantern/suits/midnight.png"),
	/** ...and Guy Gardner (green / black / white plated suit, no mask, gloved). */
	ARMORED("armored", "textures/entity/green_lantern/suits/armored.png");

	private final String id;
	private final ResourceLocation texture;

	GreenLanternSuitStyle(String id, String texture) {
		this.id = id;
		this.texture = ProjectHeroMod.id(texture);
	}

	public String id() {
		return id;
	}

	/** The armour texture (64x64 player-skin layout). */
	public ResourceLocation texture() {
		return texture;
	}

	/** {@code screen.projecthero.green_lantern_suit.<id>} -- the suit's display name. */
	public String nameKey() {
		return "screen.projecthero.green_lantern_suit." + id;
	}

	/** {@code screen.projecthero.green_lantern_suit.<id>.desc} -- one short line under the name. */
	public String descKey() {
		return nameKey() + ".desc";
	}

	/** Out-of-range ordinals (an old or tampered save) fall back to the default suit. */
	public static GreenLanternSuitStyle byOrdinal(int ordinal) {
		GreenLanternSuitStyle[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : DEFAULT;
	}
}
