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
