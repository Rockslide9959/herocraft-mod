package com.projecthero.mod.moonknight.temple;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Moon Knight Phase 7: the Temple of Khonshu's registry objects -- the Scarab of Khonshu (loot-only: found in the
 * Temple's hidden chamber, never crafted), the Altar of Khonshu block + block entity -- and the two server hooks the
 * ritual needs (no damage while "dead", clean up on disconnect). The structure itself is registered in
 * {@code worldgen/ModStructureTypes} / {@code ModStructurePieceTypes} like every other mod structure.
 *
 * <p>The altar is <b>unbreakable in survival</b> (bedrock-like strength, pistons cannot move it): a spent altar must
 * stay spent, and a mined-and-replaced altar would otherwise come back fresh.
 */
public final class KhonshuTemple {
	public static final Item SCARAB_OF_KHONSHU = Registry.register(BuiltInRegistries.ITEM,
			ResourceKey.create(Registries.ITEM, ProjectHeroMod.id("scarab_of_khonshu")),
			new ScarabOfKhonshuItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

	public static final Block KHONSHU_ALTAR = Registry.register(BuiltInRegistries.BLOCK,
			ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id("khonshu_altar")),
			new KhonshuAltarBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.SAND).strength(-1.0f, 3_600_000.0f).noLootTable().sound(SoundType.STONE)
					.pushReaction(PushReaction.BLOCK).lightLevel(s -> s.getValue(KhonshuAltarBlock.SPENT) ? 0 : 6)));

	public static final Item KHONSHU_ALTAR_ITEM = registerBlockItem("khonshu_altar",
			new BlockItem(KHONSHU_ALTAR, new Item.Properties().rarity(Rarity.EPIC)));

	public static final BlockEntityType<KhonshuAltarBlockEntity> KHONSHU_ALTAR_BE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id("khonshu_altar"),
			BlockEntityType.Builder.of(KhonshuAltarBlockEntity::new, KHONSHU_ALTAR).build(null));

	private KhonshuTemple() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}; loading the class registers everything above. */
	public static void initialize() {
		// a player being "reborn" on the altar takes no damage for those two seconds
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayer p && KhonshuRitual.isBeingReborn(p)));
		// logging out mid-ritual: the altar notices the player is gone on its next tick and returns the scarab;
		// here we only drop the rebirth bookkeeping and the invisibility
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> KhonshuRitual.onDisconnect(handler.getPlayer()));
	}

	/** Creative tab entries (the Superheroes tab). */
	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(SCARAB_OF_KHONSHU);
		output.accept(KHONSHU_ALTAR_ITEM);
	}

	private static Item registerBlockItem(String path, BlockItem item) {
		Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
		item.registerBlocks(Item.BY_BLOCK, item);
		return item;
	}
}
