package com.projecthero.mod.ultron.entity;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.resources.ResourceLocation;

/**
 * v0.15.12: the Ultron robots' faces -- player skins from The Skindex (minecraftskins.com), the ids credited in
 * {@code docs/ULTRON_REFERENCE.md}. Each has an {@code _eyes} companion texture: the red eye / mouth texels alone, drawn
 * full-bright so they glow in the dark. {@code slim} skins have 3-pixel arms (the renderer swaps to the slim model).
 */
public enum UltronSkin {
	/** Skindex 6870675: Ultron Prime. */
	PRIME("prime", false),
	/** Skindex 23814202: the Ultron Sentry giant (slim arms). */
	SENTRY("sentry", true),
	/** Skindex 23128749: an Ultron Drone. */
	DRONE("drone", false),
	/** Skindex 5830525: an Ultron Sentinel Drone. */
	SENTINEL_DRONE("sentinel_drone", false),
	/** Skindex 6722436: an Ultron Heavy. */
	HEAVY("heavy", false),
	/** Skindex 23173990: an Ultron Sniper Frame. */
	SNIPER("sniper", false);

	private final ResourceLocation texture;
	private final ResourceLocation eyes;
	private final boolean slim;

	UltronSkin(String name, boolean slim) {
		this.texture = ProjectHeroMod.id("textures/entity/ultron/" + name + ".png");
		this.eyes = ProjectHeroMod.id("textures/entity/ultron/" + name + "_eyes.png");
		this.slim = slim;
	}

	public ResourceLocation texture() {
		return texture;
	}

	public ResourceLocation eyes() {
		return eyes;
	}

	public boolean slim() {
		return slim;
	}
}
