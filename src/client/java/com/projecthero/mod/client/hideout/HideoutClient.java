package com.projecthero.mod.client.hideout;

import com.projecthero.mod.client.syndicate.SyndicateRenderer;
import com.projecthero.mod.hideout.HideoutEntityTypes;
import com.projecthero.mod.hideout.item.CortexShardItem;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * v0.17.5: the villain hideouts' client side -- the mini-boss renderers (each reuses its faction's existing renderer or
 * model), and the Ultron Cortex Shard's reveal: a local timer only this viewer has; {@code EntityGlowMixin} asks
 * {@link #cortexReveals} (the v0.15.4 privacy rule: nothing is ever set on the mob).
 */
public final class HideoutClient {
	private static int revealTicks;

	private HideoutClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(HideoutEntityTypes.KINGPIN_ENFORCER, SyndicateRenderer::new);
		EntityRendererRegistry.register(HideoutEntityTypes.FORGE_OVERSEER, com.projecthero.mod.client.ultron.UltronRobotRenderer::new);
		EntityRendererRegistry.register(HideoutEntityTypes.PARADEMON_COMMANDER, ParademonCommanderRenderer::new);
		EntityRendererRegistry.register(HideoutEntityTypes.HIVE_MOTHER, HiveMotherRenderer::new);
		EntityRendererRegistry.register(HideoutEntityTypes.FROST_GIANT_JARL, ctx -> new com.projecthero.mod.client.frostgiant.FrostGiantRenderer<>(ctx, 1.6f));
		// v0.17.7: the three new hideouts' mini-bosses and soldiers (all player models)
		EntityRendererRegistry.register(HideoutEntityTypes.CRYPT_LORD, ctx -> new MiniBossRenderer<>(ctx, "crypt_lord", MiniBossRenderer::cryptLord));
		EntityRendererRegistry.register(HideoutEntityTypes.BROOD_SPAWN, ctx -> new MiniBossRenderer<>(ctx, "brood_spawn", MiniBossRenderer::broodSpawn));
		EntityRendererRegistry.register(HideoutEntityTypes.OATHBOUND_CHAMPION, ctx -> new MiniBossRenderer<>(ctx, "oathbound_champion", MiniBossRenderer::champion));
		EntityRendererRegistry.register(HideoutEntityTypes.CRIMSON_THRALL, HideoutSoldierRenderer::new);
		EntityRendererRegistry.register(HideoutEntityTypes.OATH_KNIGHT, HideoutSoldierRenderer::new);
		net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(com.projecthero.mod.hideout.block.HideoutBlocks.BALLISTA,
				net.minecraft.client.renderer.RenderType.cutout());
		CortexShardItem.clientReveal = () -> revealTicks = CortexShardItem.REVEAL_TICKS;
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (revealTicks > 0) {
				revealTicks--;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> revealTicks = 0);
	}

	/** True while this viewer's Cortex Shard reveal runs and {@code e} is a living hostile within range. */
	public static boolean cortexReveals(Entity e) {
		if (revealTicks <= 0 || !(e instanceof Enemy) || !(e instanceof LivingEntity living) || !living.isAlive()) {
			return false;
		}
		LocalPlayer viewer = Minecraft.getInstance().player;
		return viewer != null && viewer.distanceToSqr(e) <= CortexShardItem.RANGE * CortexShardItem.RANGE;
	}

	/** Test / harness seam. */
	public static int revealTicks() {
		return revealTicks;
	}
}
