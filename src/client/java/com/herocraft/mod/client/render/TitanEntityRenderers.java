package com.herocraft.mod.client.render;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.titan.entity.TitanEntity;
import com.herocraft.mod.titan.entity.TitanEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Client renderers for the Titan boss. Both zombie-shaped forms reuse the shared
 * {@link RaidZombieRenderer} (vanilla zombie model + armour layers, just a different texture/scale)
 * exactly like the Zombie Raid's own bosses -- no bespoke model, per the spec's explicit "temporary
 * placeholder, don't spend time on a custom model yet" instruction.
 *
 * <h2>Where to drop a real Titan texture later</h2>
 * Replace {@code assets/herocraft/textures/entity/titan.png} (a plain 64x64 zombie-skin-layout PNG)
 * with a real piece of art of the same dimensions -- nothing else in this class, or in
 * {@link TitanEntity}, needs to change.
 */
public final class TitanEntityRenderers {
	private static final ResourceLocation DISGUISED_TITAN =
			ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");
	private static final ResourceLocation TITAN = HeroCraftMod.id("textures/entity/titan.png");

	private TitanEntityRenderers() {
	}

	public static void initialize() {
		// Disguised form: deliberately the plain vanilla zombie texture -- it must not look like
		// anything special (spec: "should not reveal that it will transform").
		EntityRendererRegistry.register(TitanEntityTypes.DISGUISED_TITAN,
				context -> new RaidZombieRenderer<>(context, RaidZombieRenderer.fixed(DISGUISED_TITAN), 1.0f));

		EntityRendererRegistry.register(TitanEntityTypes.TITAN,
				context -> new RaidZombieRenderer<>(context, RaidZombieRenderer.fixed(TITAN), TitanEntity.SCALE));

		EntityRendererRegistry.register(TitanEntityTypes.TITAN_BOULDER, ThrownItemRenderer::new);
	}
}
