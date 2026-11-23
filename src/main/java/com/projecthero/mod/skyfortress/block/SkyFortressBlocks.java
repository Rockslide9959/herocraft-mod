package com.projecthero.mod.skyfortress.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.7 Sky Fortress: its blocks -- the Launch Pad (flings a Fortress Pass holder up to the hangar), the four engine
 * Turbines (sabotage all four to drop the bridge shield), the Bridge Shield itself, the sealed Fortress Vault (opens
 * when Admiral Kestrel falls), the Parachute Crate by the hangar mouth and the flight deck's Wind Vents (gusts).
 */
public final class SkyFortressBlocks {
	public static LaunchPadBlock LAUNCH_PAD;
	public static SkyTurbineBlock TURBINE;
	public static BridgeShieldBlock SHIELD;
	public static SkyVaultSealBlock SEAL;
	public static ParachuteCrateBlock PARACHUTE_CRATE;
	public static WindVentBlock WIND_VENT;

	public static BlockEntityType<WindVentBlockEntity> WIND_VENT_BE;

	public static BlockItem LAUNCH_PAD_ITEM;
	public static BlockItem TURBINE_ITEM;
	public static BlockItem SHIELD_ITEM;
	public static BlockItem SEAL_ITEM;
	public static BlockItem PARACHUTE_CRATE_ITEM;
	public static BlockItem WIND_VENT_ITEM;

	private SkyFortressBlocks() {
	}

	public static void initialize() {
		LAUNCH_PAD = block("launch_pad", new LaunchPadBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_YELLOW).strength(5.0f, 12.0f).sound(SoundType.METAL).requiresCorrectToolForDrops()
				.lightLevel(s -> 7)));
		TURBINE = block("sky_turbine", new SkyTurbineBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0f, 3600000.0f).sound(SoundType.METAL)
				.lightLevel(s -> s.getValue(SkyTurbineBlock.RUNNING) ? 12 : 2).pushReaction(PushReaction.BLOCK).noLootTable()));
		SHIELD = block("bridge_shield", new BridgeShieldBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0f, 3600000.0f).sound(SoundType.AMETHYST)
				.lightLevel(s -> 9).noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)
				.isRedstoneConductor((s, l, p) -> false).pushReaction(PushReaction.BLOCK).noLootTable()));
		SEAL = block("sky_vault_seal", new SkyVaultSealBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.DIAMOND).strength(-1.0f, 3600000.0f).sound(SoundType.VAULT).lightLevel(s -> 6)
				.pushReaction(PushReaction.BLOCK).noLootTable()));
		PARACHUTE_CRATE = block("parachute_crate", new ParachuteCrateBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.COLOR_ORANGE).strength(2.5f, 6.0f).sound(SoundType.WOOD).noLootTable()));
		WIND_VENT = block("wind_vent", new WindVentBlock(BlockBehaviour.Properties.of()
				.mapColor(MapColor.METAL).strength(-1.0f, 3600000.0f).sound(SoundType.METAL).pushReaction(PushReaction.BLOCK)
				.noLootTable()));

		WIND_VENT_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("wind_vent"),
				BlockEntityType.Builder.of(WindVentBlockEntity::new, WIND_VENT).build(null));

		LAUNCH_PAD_ITEM = item("launch_pad", LAUNCH_PAD);
		TURBINE_ITEM = item("sky_turbine", TURBINE);
		SHIELD_ITEM = item("bridge_shield", SHIELD);
		SEAL_ITEM = item("sky_vault_seal", SEAL);
		PARACHUTE_CRATE_ITEM = item("parachute_crate", PARACHUTE_CRATE);
		WIND_VENT_ITEM = item("wind_vent", WIND_VENT);
	}

	public static Item[] all() {
		return new Item[] { LAUNCH_PAD_ITEM, TURBINE_ITEM, SHIELD_ITEM, SEAL_ITEM, PARACHUTE_CRATE_ITEM, WIND_VENT_ITEM };
	}

	private static <B extends Block> B block(String path, B block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static BlockItem item(String path, Block block) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)),
				new BlockItem(block, new Item.Properties()));
	}
}
