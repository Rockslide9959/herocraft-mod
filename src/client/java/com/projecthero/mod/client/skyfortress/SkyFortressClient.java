package com.projecthero.mod.client.skyfortress;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.syndicate.SyndicateRenderer;
import com.projecthero.mod.skyfortress.SkyFortressEntityTypes;
import com.projecthero.mod.skyfortress.block.SkyFortressBlocks;
import com.projecthero.mod.skyfortress.entity.SkyHuman;
import com.projecthero.mod.skyfortress.entity.SkyPrisoner;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.7: the Sky Fortress's client side -- its crew drawn by the Syndicate player-model renderer in their own skins
 * ({@link SkyHumanRenderer}), the prisoners on the plain player model, Admiral Kestrel through GeckoLib
 * ({@link AdmiralKestrelRenderer}), her missiles as rockets, the Bridge Shield on the translucent layer. No client state.
 */
public final class SkyFortressClient {
	private SkyFortressClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(SkyFortressEntityTypes.TROOPER, SkyHumanRenderer::new);
		EntityRendererRegistry.register(SkyFortressEntityTypes.GUNNER, SkyHumanRenderer::new);
		EntityRendererRegistry.register(SkyFortressEntityTypes.DECK_OFFICER, SkyHumanRenderer::new);
		EntityRendererRegistry.register(SkyFortressEntityTypes.PRISONER, PrisonerRenderer::new);
		EntityRendererRegistry.register(SkyFortressEntityTypes.ADMIRAL_KESTREL, AdmiralKestrelRenderer::new);
		EntityRendererRegistry.register(SkyFortressEntityTypes.KESTREL_MISSILE, ctx -> new ThrownItemRenderer<>(ctx, 1.1f, true));
		BlockRenderLayerMap.INSTANCE.putBlock(SkyFortressBlocks.SHIELD, RenderType.translucent());
	}

	/** The Syndicate renderer with the Sky Fortress skin of each crewman. */
	public static final class SkyHumanRenderer extends SyndicateRenderer {
		public SkyHumanRenderer(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public ResourceLocation getTextureLocation(SyndicateCriminal entity) {
			return entity instanceof SkyHuman h ? h.skyTexture() : super.getTextureLocation(entity);
		}
	}

	/** A prisoner: the wide player model in his own skin. */
	public static final class PrisonerRenderer extends HumanoidMobRenderer<SkyPrisoner, PlayerModel<SkyPrisoner>> {
		public PrisonerRenderer(EntityRendererProvider.Context ctx) {
			super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
		}

		@Override
		protected void scale(SkyPrisoner entity, PoseStack pose, float partialTick) {
			pose.scale(0.9375f, 0.9375f, 0.9375f);
		}

		@Override
		public ResourceLocation getTextureLocation(SkyPrisoner entity) {
			return entity.texture();
		}
	}
}
