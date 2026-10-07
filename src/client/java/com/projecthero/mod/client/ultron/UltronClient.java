package com.projecthero.mod.client.ultron;

import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.block.UltronBlocks;
import com.projecthero.mod.ultron.item.UltronItems;

import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * v0.15.12: the Ultron Uprising's client side -- the robot renderers, the relay pylons, the Ultron Core's turning eye, the
 * red beams, and the Mind Stone's private mob outline.
 */
public final class UltronClient {
	/** How far the Mind Stone's outline reaches. */
	public static final double MIND_STONE_RANGE = 24.0;
	public static final int MIND_STONE_COLOR = 0xF5C542;

	private UltronClient() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(UltronEntityTypes.DRONE, UltronRobotRenderer::new);
		EntityRendererRegistry.register(UltronEntityTypes.SENTINEL_DRONE, UltronRobotRenderer::new);
		EntityRendererRegistry.register(UltronEntityTypes.HEAVY, UltronRobotRenderer::new);
		EntityRendererRegistry.register(UltronEntityTypes.SNIPER, UltronRobotRenderer::new);
		EntityRendererRegistry.register(UltronEntityTypes.PRIME, UltronRobotRenderer::new);
		EntityRendererRegistry.register(UltronEntityTypes.SENTRY, UltronRobotRenderer::new);
		EntityRendererRegistry.register(UltronEntityTypes.PYLON, UltronPylonRenderer::new);
		BlockEntityRendererRegistry.register(UltronBlocks.ULTRON_CORE_BE, UltronCoreRenderer::new);
		UltronBeamClient.initialize();
	}

	/**
	 * The Mind Stone's outline: is {@code self} a hostile within range of a viewer holding the stone in their off hand?
	 * Only ever asked for a client-side entity, from the viewer's own state (the v0.15.4 privacy rule).
	 */
	public static boolean mindStoneOutlines(Entity self) {
		Player viewer = Minecraft.getInstance().player;
		if (viewer == null || self == viewer || !(self instanceof Enemy) || UltronItems.MIND_STONE == null) {
			return false;
		}
		return viewer.getOffhandItem().is(UltronItems.MIND_STONE) && self.distanceToSqr(viewer) <= MIND_STONE_RANGE * MIND_STONE_RANGE;
	}
}
