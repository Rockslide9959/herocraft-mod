package com.projecthero.mod.client.mutation.d;

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
 * v0.13.22: full-bright ("emissive") item models via the Fabric Renderer API. The listed item models are wrapped after
 * baking so every quad they emit uses an emissive, unshaded material -- the Hard-Light Blade glows in a dark cave, in
 * your hand, in other players' hands and in the inventory alike. If no FRAPI renderer is present the model is left
 * as-is (it still renders, just lit normally).
 */
public final class EmissiveItemModels {
	private static final Set<ResourceLocation> MODELS = Set.of(ProjectHeroMod.id("item/hard_light_blade"));
	private static final Set<ResourceLocation> ITEMS = Set.of(ProjectHeroMod.id("hard_light_blade"));

	private EmissiveItemModels() {
	}

	public static void init() {
		ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
			if (model == null || model instanceof Emissive) {
				return model;
			}
			boolean match = MODELS.contains(context.resourceId())
					|| (context.topLevelId() != null && ITEMS.contains(context.topLevelId().id()));
			if (!match) {
				return model;
			}
			Renderer renderer = RendererAccess.INSTANCE.getRenderer();
			if (renderer == null) {
				return model;
			}
			RenderMaterial glow = renderer.materialFinder().emissive(true).disableDiffuse(true).find();
			return new Emissive(model, glow);
		}));
	}

	private static final class Emissive extends ForwardingBakedModel {
		private final RenderMaterial glow;

		Emissive(BakedModel wrapped, RenderMaterial glow) {
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
				quad.material(glow);
				return true;
			});
			super.emitItemQuads(stack, randomSupplier, context);
			context.popTransform();
		}
	}
}
