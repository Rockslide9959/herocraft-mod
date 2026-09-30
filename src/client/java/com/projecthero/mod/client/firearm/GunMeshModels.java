package com.projecthero.mod.client.firearm;

import java.io.Reader;
import java.util.Map;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.5: gun items drawn from a polygon mesh instead of JSON cuboids. Vanilla item models can only hold boxes, and
 * the user's Maya-made sniper has slanted, bevelled and cylindrical parts, so the mesh is converted offline
 * ({@code scratchpad/convert_sniper_obj.js}) into {@code assets/projecthero/meshes/<item>.json} and emitted through
 * the Fabric Renderer API. The item's ordinary JSON model is kept (empty elements) for its display transforms,
 * GUI lighting and particle texture; the particle texture doubles as the mesh's colour palette.
 */
public final class GunMeshModels {
	/** item id -> mesh resource */
	private static final Map<ResourceLocation, ResourceLocation> MESHES = Map.of(
			ProjectHeroMod.id("punisher_sniper"), ProjectHeroMod.id("meshes/punisher_sniper.json"));

	private GunMeshModels() {
	}

	public static void initialize() {
		ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
			// resourceId() is null for top-level models (an item's #inventory model); match on topLevelId instead
			if (model == null || model instanceof MeshModel || context.topLevelId() == null) {
				return model;
			}
			ResourceLocation mesh = MESHES.get(context.topLevelId().id());
			if (mesh == null) {
				return model;
			}
			float[][] quads = load(mesh);
			return quads == null ? model : new MeshModel(model, quads);
		}));
	}

	/** Each entry: swatch index, then 4 x (x, y, z) in item-model units (0..16). */
	private static float[][] load(ResourceLocation id) {
		try (Reader r = Minecraft.getInstance().getResourceManager().openAsReader(id)) {
			JsonArray arr = JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("quads");
			float[][] out = new float[arr.size()][];
			for (int i = 0; i < arr.size(); i++) {
				JsonObject q = arr.get(i).getAsJsonObject();
				JsonArray v = q.getAsJsonArray("v");
				float[] f = new float[13];
				f[0] = q.get("s").getAsInt();
				int k = 1;
				for (JsonElement e : v) {
					f[k++] = e.getAsFloat();
				}
				out[i] = f;
			}
			return out;
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.error("[ProjectHero] could not load gun mesh {}", id, e);
			return null;
		}
	}

	private static final class MeshModel extends ForwardingBakedModel {
		private final float[][] quads;

		MeshModel(BakedModel wrapped, float[][] quads) {
			this.wrapped = wrapped;
			this.quads = quads;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
			TextureAtlasSprite palette = wrapped.getParticleIcon();
			QuadEmitter emitter = context.getEmitter();
			for (float[] q : quads) {
				// centre of the 4x4 swatch, as a 0..1 fraction of the 16x16 palette texture
				int s = (int) q[0];
				float u = ((s % 4) * 4 + 2) / 16.0f;
				float v = ((s / 4) * 4 + 2) / 16.0f;
				for (int i = 0; i < 4; i++) {
					emitter.pos(i, q[1 + i * 3] / 16.0f, q[2 + i * 3] / 16.0f, q[3 + i * 3] / 16.0f);
					emitter.uv(i, u, v);
					emitter.color(i, -1);
				}
				emitter.spriteBake(palette, MutableQuadView.BAKE_NORMALIZED);
				emitter.emit();
			}
			super.emitItemQuads(stack, randomSupplier, context);
		}
	}
}
