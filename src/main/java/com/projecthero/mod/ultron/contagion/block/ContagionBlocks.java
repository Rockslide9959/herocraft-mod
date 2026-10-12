package com.projecthero.mod.ultron.contagion.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.16.1: the Ultron Contagion's blocks.
 * <ul>
 *   <li><b>Corrupted Circuitry</b> (ground / stone / log / leaves): what a Nexus turns natural surface blocks into. Never
 *       placed by anything else, never dropped: purging the Nexus (or a Purge Charge) puts the recorded original back.</li>
 *   <li><b>Citadel Plating / Citadel Rock / Rising Rock</b>: the sky-citadel and its stepping stones. Unbreakable, no drops,
 *       every one tracked and removed when the finale ends.</li>
 *   <li><b>Firewall Beacon</b> ({@link FirewallBeaconBlock}) and the <b>Omega Core</b> trophy ({@link OmegaCoreBlock}).</li>
 * </ul>
 */
public final class ContagionBlocks {
	/** Blocks a Nexus may corrupt: natural surface blocks only (data tag, see the reference doc). */
	public static final TagKey<Block> CORRUPTIBLE = TagKey.create(Registries.BLOCK, ProjectHeroMod.id("contagion_corruptible"));

	public static Block CORRUPTED_CIRCUITRY;
	public static Block CORRUPTED_STONE;
	public static Block CORRUPTED_LOG;
	public static Block CORRUPTED_LEAVES;
	public static Block CITADEL_PLATING;
	public static Block CITADEL_ROCK;
	public static Block RISING_ROCK;
	/** v0.16.1 polish: the citadel's red light strips and dangling cables (ours, so they are tracked and removed too). */
	public static Block CITADEL_LIGHT;
	public static Block CITADEL_CABLE;
	public static FirewallBeaconBlock FIREWALL_BEACON;
	public static OmegaCoreBlock OMEGA_CORE;
	public static BlockEntityType<FirewallBeaconBlockEntity> FIREWALL_BEACON_BE;
	public static BlockEntityType<OmegaCoreBlockEntity> OMEGA_CORE_BE;

	private ContagionBlocks() {
	}

	private static BlockBehaviour.Properties corrupted() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.8f, 6.0f).sound(SoundType.NETHERITE_BLOCK)
				// emissive: drawn full-bright like magma, so the red veins glow at night (the dark metal stays dark)
				.noLootTable().lightLevel(s -> 4).emissiveRendering((s, l, p) -> true);
	}

	private static BlockBehaviour.Properties citadel() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1.0f, 3600000.0f).sound(SoundType.NETHERITE_BLOCK)
				.noLootTable().pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false);
	}

	public static void initialize() {
		CORRUPTED_CIRCUITRY = register("corrupted_circuitry", new CorruptedGroundBlock(corrupted()));
		CORRUPTED_STONE = register("corrupted_circuitry_stone", new Block(corrupted().strength(2.5f, 6.0f)));
		CORRUPTED_LOG = register("corrupted_circuitry_log", new RotatedPillarBlock(corrupted()));
		CORRUPTED_LEAVES = register("corrupted_circuitry_leaves", new Block(corrupted().strength(0.4f).noOcclusion()
				.isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isValidSpawn((s, l, p, t) -> false)));
		CITADEL_PLATING = register("citadel_plating", new Block(citadel().lightLevel(s -> 3)));
		CITADEL_ROCK = register("citadel_rock", new Block(citadel().lightLevel(s -> 2)));
		RISING_ROCK = register("rising_rock", new Block(citadel().lightLevel(s -> 6)));
		CITADEL_LIGHT = register("citadel_light", new Block(citadel().lightLevel(s -> 11).emissiveRendering((s, l, p) -> true)));
		CITADEL_CABLE = register("citadel_cable", new net.minecraft.world.level.block.ChainBlock(citadel().noOcclusion().sound(SoundType.CHAIN)));
		FIREWALL_BEACON = register("firewall_beacon", new FirewallBeaconBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
				.strength(5.0f, 1200.0f).requiresCorrectToolForDrops().sound(SoundType.NETHERITE_BLOCK).noOcclusion()
				.lightLevel(s -> s.getValue(FirewallBeaconBlock.LIT) ? 12 : 2)));
		OMEGA_CORE = register("ultron_omega_core", new OmegaCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY)
				.strength(3.0f, 600.0f).sound(SoundType.NETHERITE_BLOCK).noOcclusion().lightLevel(s -> 5)));
		FIREWALL_BEACON_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("firewall_beacon"),
				BlockEntityType.Builder.of(FirewallBeaconBlockEntity::new, FIREWALL_BEACON).build(null));
		OMEGA_CORE_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("ultron_omega_core"),
				BlockEntityType.Builder.of(OmegaCoreBlockEntity::new, OMEGA_CORE).build(null));
	}

	/** Is {@code block} one of the corruption's own blocks? */
	public static boolean isCorruption(Block block) {
		return block == CORRUPTED_CIRCUITRY || block == CORRUPTED_STONE || block == CORRUPTED_LOG || block == CORRUPTED_LEAVES;
	}

	/** Is {@code block} a sky-citadel / stepping-stone block? */
	public static boolean isCitadel(Block block) {
		return block == CITADEL_PLATING || block == CITADEL_ROCK || block == RISING_ROCK || block == CITADEL_LIGHT || block == CITADEL_CABLE;
	}

	private static <T extends Block> T register(String path, T block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}
}
