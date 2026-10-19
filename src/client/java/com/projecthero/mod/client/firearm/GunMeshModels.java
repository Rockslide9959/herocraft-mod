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
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
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
			ProjectHeroMod.id("punisher_sniper"), ProjectHeroMod.id("meshes/punisher_sniper.json"),
			// v0.15.16: the user's new assault rifle (scratchpad/convert_rifle_obj.js)
			ProjectHeroMod.id("punisher_assault_rifle"), ProjectHeroMod.id("meshes/punisher_assault_rifle.json"),
			// v0.16.5: the user's new pistol (scratchpad/convert_pistol_obj.js)
			ProjectHeroMod.id("punisher_pistol"), ProjectHeroMod.id("meshes/punisher_pistol.json"));

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
			float[][] quads = load(mesh, true);
			if (quads == null) {
				return model;
			}
			// v0.16.7 (perf): a low-detail copy (scratchpad/gen_gun_mesh_lod.js) for guns you only see from outside
			ResourceLocation lodId = ResourceLocation.fromNamespaceAndPath(mesh.getNamespace(), mesh.getPath().replace(".json", "_lod.json"));
			float[][] lod = load(lodId, false);
			return new MeshModel(model, quads, lod != null ? lod : quads);
		}));
	}

	/** Each entry: swatch index, then 4 x (x, y, z) in item-model units (0..16). */
	private static float[][] load(ResourceLocation id, boolean required) {
		if (!required && Minecraft.getInstance().getResourceManager().getResource(id).isEmpty()) {
			return null;
		}
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

	/**
	 * v0.16.7 (perf, user: "frame stuttering ... I think it was the gun enemies"): the user's pistol mesh is 18,656 quads,
	 * and every Syndicate gunman holding one re-emitted (sprite-baked, normal-computed) all of them every frame -- into
	 * the depth-SORTED translucent item buffer, too. Now the quads are baked once into a Fabric {@link Mesh} per palette
	 * sprite, drawn on the cutout buffer (the palette is opaque, so it looks the same and needs no sorting), and a gun
	 * seen from outside -- in a mob's or another player's hand, on the ground, in a frame -- draws the low-detail copy
	 * (~2.5k quads for the pistol); your own first-person gun and the GUI draw the full mesh.
	 */
	private static final class MeshModel extends ForwardingBakedModel {
		private final float[][] quads;
		private final float[][] lodQuads;
		private TextureAtlasSprite bakedFor;
		private Mesh full;
		private Mesh lod;

		MeshModel(BakedModel wrapped, float[][] quads, float[][] lodQuads) {
			this.wrapped = wrapped;
			this.quads = quads;
			this.lodQuads = lodQuads;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		private static Mesh bake(float[][] quads, TextureAtlasSprite palette) {
			Renderer renderer = RendererAccess.INSTANCE.getRenderer();
			if (renderer == null) {
				return null;
			}
			RenderMaterial cutout = renderer.materialFinder().blendMode(BlendMode.CUTOUT).find();
			MeshBuilder builder = renderer.meshBuilder();
			QuadEmitter emitter = builder.getEmitter();
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
				emitter.material(cutout);
				emitter.spriteBake(palette, MutableQuadView.BAKE_NORMALIZED);
				emitter.emit();
			}
			return builder.build();
		}

		@Override
		public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
			TextureAtlasSprite palette = wrapped.getParticleIcon();
			if (palette != bakedFor || full == null) {
				// (re)bake when the atlas changed (resource reload)
				bakedFor = palette;
				full = bake(quads, palette);
				lod = lodQuads == quads ? full : bake(lodQuads, palette);
			}
			ItemDisplayContext mode = context.itemTransformationMode();
			// the GUI keeps the full mesh too (big previews in the Punisher's arsenal screen)
			boolean close = mode == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || mode == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
					|| mode == ItemDisplayContext.GUI;
			Mesh mesh = close ? full : lod;
			if (mesh != null) {
				mesh.outputTo(context.getEmitter());
			}
			super.emitItemQuads(stack, randomSupplier, context);
		}
	}
}
