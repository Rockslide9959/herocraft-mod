package com.projecthero.mod.client.temple;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.syndicate.SyndicateRenderer;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.temple.TempleEntityTypes;
import com.projecthero.mod.temple.block.TempleBlocks;
import com.projecthero.mod.temple.entity.AnkhatepBoss;
import com.projecthero.mod.temple.entity.TempleMob;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * v0.17.7: the Ancient Temple's client side. The temple's people are drawn by the Syndicate player-model renderer in
 * their own skins ({@code textures/entity/temple/}); Ankhatep through GeckoLib (his death-mask eyes, staff gem, collar
 * gems and curse runes glow via {@code ankhatep_glowmask.png}; his chains are hidden once he breaks them). Cut-out layers
 * for the see-through blocks. No client state.
 */
public final class TempleClient {
	private TempleClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(TempleEntityTypes.GUARDIAN, TempleMobRenderer::new);
		EntityRendererRegistry.register(TempleEntityTypes.REVENANT, TempleMobRenderer::new);
		EntityRendererRegistry.register(TempleEntityTypes.ACOLYTE, TempleMobRenderer::new);
		EntityRendererRegistry.register(TempleEntityTypes.TOMB_SENTINEL, TempleMobRenderer::new);
		EntityRendererRegistry.register(TempleEntityTypes.ANKHATEP, AnkhatepRenderer::new);
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), TempleBlocks.WARD_PLATE, TempleBlocks.GLYPH_PLATE, TempleBlocks.MIRROR,
				TempleBlocks.SEAL, TempleBlocks.WEIGHT_ALTAR, TempleBlocks.CANOPIC_JAR, TempleBlocks.CRUMBLING_FLOOR);
	}

	/** The Syndicate player-model renderer with the temple skin. */
	public static final class TempleMobRenderer extends SyndicateRenderer {
		public TempleMobRenderer(EntityRendererProvider.Context ctx) {
			super(ctx);
		}

		@Override
		public ResourceLocation getTextureLocation(SyndicateCriminal entity) {
			return entity instanceof TempleMob t ? t.templeSkin().texture() : super.getTextureLocation(entity);
		}
	}

	/** Ankhatep: the geo model is authored at his full 3.2 blocks. */
	public static final class AnkhatepRenderer extends GeoEntityRenderer<AnkhatepBoss> {
		public AnkhatepRenderer(EntityRendererProvider.Context context) {
			super(context, new AnkhatepModel());
			this.shadowRadius = 0.9f;
			addRenderLayer(new AutoGlowingGeoLayer<>(this));
		}

		@Override
		public void preRender(PoseStack poseStack, AnkhatepBoss animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
				boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
			boolean chained = animatable.chained();
			for (String n : new String[] { "chain_r", "chain_l" }) {
				model.getBone(n).ifPresent(b -> b.setHidden(!chained));
			}
			for (String n : new String[] { "broken_r", "broken_l" }) {
				model.getBone(n).ifPresent(b -> b.setHidden(chained));
			}
			super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
		}

		@Override
		protected float getDeathMaxRotation(AnkhatepBoss animatable) {
			return 0.0f; // the death clip collapses him instead
		}
	}

	static final class AnkhatepModel extends GeoModel<AnkhatepBoss> {
		private static final ResourceLocation MODEL = ProjectHeroMod.id("geo/ankhatep.geo.json");
		private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/ankhatep.png");
		private static final ResourceLocation TEXTURE_UNBOUND = ProjectHeroMod.id("textures/entity/ankhatep_unbound.png");
		private static final ResourceLocation ANIMATION = ProjectHeroMod.id("animations/ankhatep.animation.json");

		@Override
		public ResourceLocation getModelResource(AnkhatepBoss animatable) {
			return MODEL;
		}

		@Override
		public ResourceLocation getTextureResource(AnkhatepBoss animatable) {
			return animatable.chained() ? TEXTURE : TEXTURE_UNBOUND;
		}

		@Override
		public ResourceLocation getAnimationResource(AnkhatepBoss animatable) {
			return ANIMATION;
		}
	}
}
