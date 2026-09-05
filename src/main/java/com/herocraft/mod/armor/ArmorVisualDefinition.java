package com.herocraft.mod.armor;

import net.minecraft.resources.ResourceLocation;

/**
 * The three GeckoLib assets that decide how one superhero armour <em>set</em> looks: its geometry,
 * its texture and its animation file. Every current set shares one geometry + animation file and
 * differs only by texture, but this record is per-set precisely so a future suit (a nanotech Mark L,
 * a War Machine) can be handed a completely different {@code geometry} / {@code animation} without
 * touching {@link com.herocraft.mod.client.render.SuperheroArmorModel} or the renderer.
 *
 * @param geometry  {@code herocraft:geo/<name>.geo.json}
 * @param texture   {@code herocraft:textures/armor/<name>.png}
 * @param animation {@code herocraft:animations/<name>.animation.json}
 */
public record ArmorVisualDefinition(ResourceLocation geometry, ResourceLocation texture, ResourceLocation animation) {
}
