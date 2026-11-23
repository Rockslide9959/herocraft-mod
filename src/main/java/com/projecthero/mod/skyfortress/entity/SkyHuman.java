package com.projecthero.mod.skyfortress.entity;

import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7: a Sky Fortress person drawn with the player model in its own detailed skin (troopers, gunners, the Pad
 * Sergeant, the Deck Officer). The skins live in {@code textures/entity/sky_fortress/}, painted by
 * {@code scratchpad/v0177/gen_sky_fortress.js}; the client renderer reads {@link #skyTexture()}.
 */
public interface SkyHuman {
	ResourceLocation skyTexture();
}
