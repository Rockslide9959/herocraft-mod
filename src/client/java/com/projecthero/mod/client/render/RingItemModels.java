package com.projecthero.mod.client.render;

import java.util.Set;
import java.util.function.Supplier;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.6: item models with only SOME parts glowing -- the Power Ring's Lantern emblem burns full-bright while the band
 * and bezel stay lit like normal metal. Vanilla 1.21.1 block-model elements have no per-element light, so the glowing
 * faces are marked in the model JSON with {@code "tintindex": 15} ({@link #GLOW_TINT}); after baking, the listed models
 * are wrapped and every quad carrying that tint index is switched to an emissive, unshaded material (and its tint
 * cleared). Every other quad is left exactly as baked. Without a Fabric Renderer API implementation the model is left
 * as-is (the marked faces just render lit, and the unregistered tint colour is white, so nothing changes colour).
 *
 * <p>To give another item a glowing part: mark its faces with tint index 15 and add its model id to {@link #MODELS} and its item id to {@link #ITEMS}.
 */
public final class RingItemModels {
	/** The tint index that marks a full-bright face. */
	public static final int GLOW_TINT = 15;

	private static final Set<ResourceLocation> MODELS = Set.of(ProjectHeroMod.id("item/power_ring"));
	/** The same, by item id (an item's model is baked as a top-level model keyed by the item). */
	private static final Set<ResourceLocation> ITEMS = Set.of(ProjectHeroMod.id("power_ring"));

	private RingItemModels() {
	}

	public static void init() {
		ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
			if (model == null || model instanceof GlowParts) {
				return model;
			}
			// resourceId() is null for top-level models, and Set.of(...).contains(null) throws
			boolean match = (context.resourceId() != null && MODELS.contains(context.resourceId()))
					|| (context.topLevelId() != null && ITEMS.contains(context.topLevelId().id()));
			if (!match) {
				return model;
			}
			Renderer renderer = RendererAccess.INSTANCE.getRenderer();
			if (renderer == null) {
				return model;
			}
			RenderMaterial glow = renderer.materialFinder().emissive(true).disableDiffuse(true).find();
			return new GlowParts(model, glow);
		}));
	}

	private static final class GlowParts extends ForwardingBakedModel {
		private final RenderMaterial glow;

		GlowParts(BakedModel wrapped, RenderMaterial glow) {
			this.wrapped = wrapped;
			this.glow = glow;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
			context.pushTransform(quad -> {
				if (quad.colorIndex() == GLOW_TINT) {
					quad.material(glow);
					quad.colorIndex(-1);
				}
				return true;
			});
			super.emitItemQuads(stack, randomSupplier, context);
			context.popTransform();
		}
	}
}
