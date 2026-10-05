package com.projecthero.mod.syndicate.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.25: the Syndicate's faces -- player skins from The Skindex (minecraftskins.com), credited in
 * {@code docs/SYNDICATE_REFERENCE.md}. {@code slim} skins have 3-pixel arms (the renderer swaps to the slim player model).
 */
public enum SyndicateSkin {
	/** "Malak gang thug 1" by Bakugo417: a bald bruiser in a flannel. */
	THUG_A("thug_a", false),
	/** "Malak gang thug 5" by Bakugo417: a bald heavy in a blue track jacket. */
	THUG_C("thug_c", false),
	/** "thug" by KINGJS189: beanie, black jacket. The pistol gunman. */
	GUNMAN("gunman", true),
	/** "thug" by hell1234568789: balaclava and a striped jumper. The shotgunner. */
	MASKED("masked", false),
	/** "thug" by Danroc: a dark hood. The sniper. */
	HOOD("hood", false),
	/** "Wilson Fisk - MCU" by wolflywood01: the white suit. */
	KINGPIN("kingpin", false);

	private final ResourceLocation texture;
	private final boolean slim;

	SyndicateSkin(String name, boolean slim) {
		this.texture = ProjectHeroMod.id("textures/entity/syndicate/" + name + ".png");
		this.slim = slim;
	}

	public ResourceLocation texture() {
		return texture;
	}

	public boolean slim() {
		return slim;
	}
}
