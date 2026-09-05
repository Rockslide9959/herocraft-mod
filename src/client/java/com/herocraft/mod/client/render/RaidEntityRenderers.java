package com.herocraft.mod.client.render;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.event.entity.JuggernautZombie;
import com.herocraft.mod.event.entity.RaidEntityTypes;
import com.herocraft.mod.event.entity.RaidZombie;
import com.herocraft.mod.event.entity.SwordSkeleton;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Client renderers for the Zombie Raid's mobs.
 *
 * <p>Every zombie-shaped mob goes through the shared {@link RaidZombieRenderer} with its own texture
 * and scale; the Sword Skeleton reuses vanilla's skeleton renderer with a different texture; the acid
 * glob uses vanilla's thrown-item renderer. Nothing here defines a new model or a new render layer,
 * which is what keeps the raid's client cost the same as an ordinary mob's -- worth caring about when
 * a late wave can have fifty of them on screen.
 *
 * <p>The basic and baby {@code RaidZombie} variants deliberately use the <b>vanilla</b> zombie texture:
 * they are supposed to read as ordinary zombies, and pointing at Minecraft's own file means they match
 * whatever resource pack the player is using. Only the mobs that must be told apart at a glance carry
 * their own art.
 */
public final class RaidEntityRenderers {
	private static final ResourceLocation VANILLA_ZOMBIE =
			ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

	private static final ResourceLocation CURSED_ZOMBIE = HeroCraftMod.id("textures/entity/cursed_zombie.png");
	private static final ResourceLocation ARMOURED_ZOMBIE = HeroCraftMod.id("textures/entity/armoured_zombie.png");
	private static final ResourceLocation ACID_ZOMBIE = HeroCraftMod.id("textures/entity/acid_zombie.png");
	private static final ResourceLocation JUGGERNAUT_ZOMBIE = HeroCraftMod.id("textures/entity/juggernaut_zombie.png");
	private static final ResourceLocation SWORD_SKELETON = HeroCraftMod.id("textures/entity/sword_skeleton.png");
	private static final ResourceLocation PILLAGER_SPY = HeroCraftMod.id("textures/entity/pillager_spy.png");

	private RaidEntityRenderers() {
	}

	public static void initialize() {
		EntityRendererRegistry.register(RaidEntityTypes.CURSED_ZOMBIE,
				context -> new RaidZombieRenderer<>(context, RaidZombieRenderer.fixed(CURSED_ZOMBIE), 1.0f));

		EntityRendererRegistry.register(RaidEntityTypes.RAID_ZOMBIE,
				context -> new RaidZombieRenderer<RaidZombie>(context,
						zombie -> zombie.variant() == RaidZombie.Variant.ARMOURED ? ARMOURED_ZOMBIE : VANILLA_ZOMBIE,
						1.0f));

		EntityRendererRegistry.register(RaidEntityTypes.ACID_ZOMBIE,
				context -> new RaidZombieRenderer<>(context, RaidZombieRenderer.fixed(ACID_ZOMBIE), 1.0f));

		EntityRendererRegistry.register(RaidEntityTypes.JUGGERNAUT_ZOMBIE,
				context -> new RaidZombieRenderer<>(context, RaidZombieRenderer.fixed(JUGGERNAUT_ZOMBIE),
						JuggernautZombie.SCALE));

		// Empowered Zombie / Supervillain Raid boss: one renderer, texture + scale switch on the
		// (cosmetic) Supervillain variant, falling through to the plain empowered_zombie look.
		EntityRendererRegistry.register(RaidEntityTypes.EMPOWERED_ZOMBIE, SupervillainBossRenderer::new);

		EntityRendererRegistry.register(RaidEntityTypes.PILLAGER_SPY, context ->
				new PillagerRenderer(context) {
					@Override
					public ResourceLocation getTextureLocation(net.minecraft.world.entity.monster.Pillager entity) {
						return PILLAGER_SPY;
					}
				});

		EntityRendererRegistry.register(RaidEntityTypes.SWORD_SKELETON, context ->
				new SkeletonRenderer<SwordSkeleton>(context) {
					@Override
					public ResourceLocation getTextureLocation(SwordSkeleton entity) {
						return SWORD_SKELETON;
					}
				});

		EntityRendererRegistry.register(RaidEntityTypes.ACID_GLOB, ThrownItemRenderer::new);
	}
}
