package com.projecthero.mod.client.vaultdungeon;

import com.projecthero.mod.client.syndicate.SyndicateRenderer;
import com.projecthero.mod.vaultdungeon.VaultEntityTypes;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * v0.17.5: The Vault's client side -- its people are drawn by the Syndicate player-model renderer in their own skins;
 * Warden Kross adds his Taser Line ({@link WardenKrossRenderer}). No client state.
 */
public final class VaultDungeonClient {
	private VaultDungeonClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(VaultEntityTypes.INMATE, SyndicateRenderer::new);
		EntityRendererRegistry.register(VaultEntityTypes.RIOT_WARDEN, SyndicateRenderer::new);
		EntityRendererRegistry.register(VaultEntityTypes.RIOT_GUNNER, SyndicateRenderer::new);
		EntityRendererRegistry.register(VaultEntityTypes.WARDEN_KROSS, WardenKrossRenderer::new);
		// v0.17.7: the Riot Captain (player model, his own skin) and the see-through security fields
		EntityRendererRegistry.register(VaultEntityTypes.RIOT_CAPTAIN, SyndicateRenderer::new);
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(com.projecthero.mod.vaultdungeon.block.VaultBlocks.BARRIER,
				net.minecraft.client.renderer.RenderType.translucent());
	}
}
