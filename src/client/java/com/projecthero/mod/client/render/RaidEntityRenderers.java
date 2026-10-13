package com.projecthero.mod.client.render;

import com.projecthero.mod.client.render.raid.AcidGlobRenderer;
import com.projecthero.mod.client.render.raid.RaidPlayerRenderers;
import com.projecthero.mod.event.entity.AcidZombie;
import com.projecthero.mod.event.entity.CursedZombie;
import com.projecthero.mod.event.entity.JuggernautZombie;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.client.render.raid.RaidZombieDispatchRenderer;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/**
 * Client renderers for the Zombie Raid's mobs, the Cursed Zombie and the Pillager Spy.
 *
 * <p>v0.16.1: the basic and baby Risen look exactly like vanilla zombies (the vanilla zombie model and texture); the
 * Armoured Risen, the Acid Zombie, the Juggernaut, the Cursed Zombie, the Sword Skeleton and the Pillager Spy are drawn on
 * the standard player rig with detailed 64x64 skins and a full-bright glow layer
 * ({@link RaidPlayerRenderers}, skins from {@code scratchpad/gen_raid_player_skins.js}); the Powered Zombie Boss keeps its
 * custom GeckoLib model ({@code SupervillainBossRenderer} dispatches: ordinary boss -> {@code raid.PoweredZombieRenderer},
 * Supervillain variants -> {@code SupervillainGeoRenderer}). Only these mod entity types are registered here -- vanilla
 * zombies, baby zombies and every other vanilla mob keep their vanilla renderers.
 */
public final class RaidEntityRenderers {
	private RaidEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(RaidEntityTypes.CURSED_ZOMBIE, context -> new RaidPlayerRenderers.ZombieRenderer<CursedZombie>(context, z -> "cursed_zombie", 1.0f));
		// basic / baby Risen: the vanilla zombie look; the Armoured Risen: its player-rig skin
		EntityRendererRegistry.register(RaidEntityTypes.RAID_ZOMBIE, RaidZombieDispatchRenderer::new);
		EntityRendererRegistry.register(RaidEntityTypes.ACID_ZOMBIE, context -> new RaidPlayerRenderers.ZombieRenderer<AcidZombie>(context, z -> "acid_zombie", 1.0f));
		EntityRendererRegistry.register(RaidEntityTypes.JUGGERNAUT_ZOMBIE, context -> new RaidPlayerRenderers.ZombieRenderer<JuggernautZombie>(context, z -> "juggernaut_zombie", JuggernautZombie.SCALE));
		EntityRendererRegistry.register(RaidEntityTypes.EMPOWERED_ZOMBIE, SupervillainBossRenderer::new);
		EntityRendererRegistry.register(RaidEntityTypes.PILLAGER_SPY, RaidPlayerRenderers.SpyRenderer::new);
		EntityRendererRegistry.register(RaidEntityTypes.SWORD_SKELETON, RaidPlayerRenderers.SkeletonRenderer::new);
		EntityRendererRegistry.register(RaidEntityTypes.ACID_GLOB, AcidGlobRenderer::new);
	}
}
