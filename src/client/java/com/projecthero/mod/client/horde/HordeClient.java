package com.projecthero.mod.client.horde;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.horde.entity.BoneTyrant;
import com.projecthero.mod.horde.entity.BroodQueen;
import com.projecthero.mod.horde.entity.HordeEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.14.12: the Horde mobs' renderers -- vanilla meshes (the bosses are scaled up by their {@code SCALE} attribute, which
 * the renderer applies on its own) with the bosses' own recoloured textures.
 */
public final class HordeClient {
	private static final ResourceLocation TYRANT = ProjectHeroMod.id("textures/entity/bone_tyrant.png");
	private static final ResourceLocation QUEEN = ProjectHeroMod.id("textures/entity/brood_queen.png");

	private HordeClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(HordeEntityTypes.HORDE_SPIDER, SpiderRenderer::new);
		EntityRendererRegistry.register(HordeEntityTypes.BONE_TYRANT, context -> new SkeletonRenderer<BoneTyrant>(context) {
			@Override
			public ResourceLocation getTextureLocation(BoneTyrant entity) {
				return TYRANT;
			}
		});
		EntityRendererRegistry.register(HordeEntityTypes.BROOD_QUEEN, context -> new SpiderRenderer<BroodQueen>(context) {
			@Override
			public ResourceLocation getTextureLocation(BroodQueen entity) {
				return QUEEN;
			}
		});
		EntityRendererRegistry.register(HordeEntityTypes.WEB_SHOT, ThrownItemRenderer::new);
	}
}
