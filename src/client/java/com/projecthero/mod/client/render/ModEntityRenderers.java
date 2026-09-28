package com.projecthero.mod.client.render;

import com.projecthero.mod.entity.ModEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public final class ModEntityRenderers {
	private ModEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(ModEntityTypes.MJOLNIR, MjolnirEntityRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.maxsteel.entity.MaxSteelEntityTypes.STEEL,
				com.projecthero.mod.client.maxsteel.SteelRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.titanshifter.entity.TitanShifterEntities.TITAN_FORM,
				com.projecthero.mod.client.titanshifter.TitanFormRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.titanshifter.entity.TitanShifterEntities.TITAN_CORPSE,
				com.projecthero.mod.client.titanshifter.TitanCorpseRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.hulk.entity.HulkEntities.BOULDER,
				com.projecthero.mod.client.hulk.HulkBoulderRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.titanshifter.entity.TitanShifterEntities.TITAN_LIGHTNING,
				com.projecthero.mod.client.titanshifter.TitanLightningRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.maxsteel.entity.MaxSteelEntityTypes.TURBO_BOLT,
				TurboBoltRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.spider.entity.SpiderEntityTypes.IMPACT_WEB,
				com.projecthero.mod.client.spider.ImpactWebRenderer::new);
		EntityRendererRegistry.register(com.projecthero.mod.symbiote.entity.SymbioteEntityTypes.SYMBIOTE,
				com.projecthero.mod.client.symbiote.SymbioteEntityRenderer::new);
		// Geokinesis' Colossal Rock -- a heavily over-scaled stone block billboard.
		EntityRendererRegistry.register(com.projecthero.mod.hero.power.p05.GeoEntityTypes.COLOSSAL_ROCK,
				ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer(ctx, 5.0f, false));
		EntityRendererRegistry.register(com.projecthero.mod.behemoth.entity.BehemothEntityTypes.ABYSSAL_BEHEMOTH,
				com.projecthero.mod.client.behemoth.BehemothRenderer::new);
		// Renders as a spinning, full-bright, over-scaled fire charge -- exactly how vanilla itself renders
		// a Ghast's own fireball (ThrownItemRenderer(ctx, 3.0f, true)), just bigger.
		EntityRendererRegistry.register(com.projecthero.mod.behemoth.entity.BehemothEntityTypes.BEHEMOTH_FIREBALL,
				ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 4.5f, true));
		EntityRendererRegistry.register(com.projecthero.mod.oathbreaker.entity.OathbreakerEntityTypes.OATHBREAKER,
				com.projecthero.mod.client.oathbreaker.OathbreakerRenderer::new);
	}
}
