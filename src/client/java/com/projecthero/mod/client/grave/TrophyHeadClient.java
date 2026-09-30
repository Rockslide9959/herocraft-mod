package com.projecthero.mod.client.grave;

import java.util.Set;
import java.util.function.Supplier;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.grave.TrophyHeadBlockEntity;
import com.projecthero.mod.grave.TrophyHeads;
import com.projecthero.mod.grave.item.BossTrophyItem;
import com.projecthero.mod.grave.item.GraveComponents;
import com.projecthero.mod.grave.item.GraveItems;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.14.4: client side of the boss trophy heads.
 * <ul>
 *   <li><b>Cutout layer</b> -- the glow decals (eyes, sigil, skull crack) have see-through texels.</li>
 *   <li><b>Power colour</b> -- every face with {@code tintindex 0} is a glow decal; it is tinted with the head's power
 *       family colour ({@link TrophyHeads#glowColor}), from the item's component or the placed block's entity.</li>
 *   <li><b>Glow</b> -- those same faces are drawn full-bright and unshaded through the Fabric Renderer API, so the
 *       eyes burn in the dark on a wall, in a hand, in the inventory and on a head alike. With no FRAPI renderer the
 *       models are left as they are (still tinted, just lit normally).</li>
 * </ul>
 */
public final class TrophyHeadClient {
	private static final Set<ResourceLocation> MODELS = Set.of(
			ProjectHeroMod.id("block/grave_champion_head"), ProjectHeroMod.id("block/grave_champion_wall_head"),
			ProjectHeroMod.id("block/empowered_zombie_head"), ProjectHeroMod.id("block/empowered_zombie_wall_head"),
			ProjectHeroMod.id("item/final_boss_trophy"), ProjectHeroMod.id("item/boss_trophy"));
	private static final Set<ResourceLocation> ITEMS = Set.of(ProjectHeroMod.id("final_boss_trophy"), ProjectHeroMod.id("boss_trophy"));

	private TrophyHeadClient() {
	}

	public static void initialize() {
		BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), GraveItems.GRAVE_CHAMPION_HEAD,
				GraveItems.GRAVE_CHAMPION_WALL_HEAD, GraveItems.EMPOWERED_ZOMBIE_HEAD, GraveItems.EMPOWERED_ZOMBIE_WALL_HEAD);

		ColorProviderRegistry.ITEM.register((stack, tintIndex) -> tintIndex != 0 ? -1
				: 0xFF000000 | TrophyHeads.glowColor(stack.get(GraveComponents.POWER_KEY),
						stack.getItem() instanceof BossTrophyItem t && t.isFinalBoss()),
				GraveItems.BOSS_TROPHY, GraveItems.FINAL_BOSS_TROPHY);
		ColorProviderRegistry.BLOCK.register(TrophyHeadClient::blockColor, GraveItems.GRAVE_CHAMPION_HEAD,
				GraveItems.GRAVE_CHAMPION_WALL_HEAD, GraveItems.EMPOWERED_ZOMBIE_HEAD, GraveItems.EMPOWERED_ZOMBIE_WALL_HEAD);

		ModelLoadingPlugin.register(plugin -> plugin.modifyModelAfterBake().register((model, context) -> {
			// resourceId() is null for top-level models (an item's #inventory model), and Set.of(..).contains(null) throws
			boolean match = (context.resourceId() != null && MODELS.contains(context.resourceId()))
					|| (context.topLevelId() != null && ITEMS.contains(context.topLevelId().id()));
			if (model == null || model instanceof Glowing || !match) {
				return model;
			}
			Renderer renderer = RendererAccess.INSTANCE.getRenderer();
			if (renderer == null) {
				return model;
			}
			RenderMaterial glow = renderer.materialFinder().emissive(true).disableDiffuse(true)
					.ambientOcclusion(TriState.FALSE).find();
			return new Glowing(model, glow);
		}));
	}

	private static int blockColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex) {
		if (tintIndex != 0) {
			return -1;
		}
		boolean champion = state.is(GraveItems.GRAVE_CHAMPION_HEAD) || state.is(GraveItems.GRAVE_CHAMPION_WALL_HEAD);
		if (level != null && pos != null && level.getBlockEntity(pos) instanceof TrophyHeadBlockEntity head) {
			return 0xFF000000 | head.glowColor();
		}
		return 0xFF000000 | TrophyHeads.glowColor(null, champion);
	}

	/** Draws the tinted (tintindex 0) quads with the full-bright material, everything else untouched. */
	private static final class Glowing extends ForwardingBakedModel {
		private final RenderMaterial glow;

		Glowing(BakedModel wrapped, RenderMaterial glow) {
			this.wrapped = wrapped;
			this.glow = glow;
		}

		@Override
		public boolean isVanillaAdapter() {
			return false;
		}

		@Override
		public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos,
				Supplier<RandomSource> randomSupplier, RenderContext context) {
			context.pushTransform(quad -> {
				if (quad.colorIndex() == 0) {
					quad.material(glow);
				}
				return true;
			});
			super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
			context.popTransform();
		}

		@Override
		public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
			context.pushTransform(quad -> {
				if (quad.colorIndex() == 0) {
					quad.material(glow);
				}
				return true;
			});
			super.emitItemQuads(stack, randomSupplier, context);
			context.popTransform();
		}
	}
}
