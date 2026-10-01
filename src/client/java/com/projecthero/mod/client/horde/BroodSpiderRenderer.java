package com.projecthero.mod.client.horde;

import java.util.EnumMap;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.horde.entity.BroodSpider;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.16: the Spider Horde's brood variants -- the vanilla spider mesh with each variant's own recoloured texture
 * ({@code textures/entity/brood_spider/<variant>.png}); the size comes from the {@code SCALE} attribute, which the
 * living-entity renderer applies on its own. A hidden Shadow Stalker is invisible, so only its glowing eyes show.
 */
public class BroodSpiderRenderer extends SpiderRenderer<BroodSpider> {
	private static final Map<BroodSpider.Variant, ResourceLocation> TEXTURES = new EnumMap<>(BroodSpider.Variant.class);

	static {
		for (BroodSpider.Variant v : BroodSpider.Variant.values()) {
			TEXTURES.put(v, ProjectHeroMod.id("textures/entity/brood_spider/" + v.id() + ".png"));
		}
	}

	public BroodSpiderRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public ResourceLocation getTextureLocation(BroodSpider entity) {
		return TEXTURES.get(entity.variant());
	}
}
