package com.projecthero.mod.temple.block;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * v0.17.7: the Ancient Temple's blocks.
 * <ul>
 * <li>Temple Wards (the alarm framework, themed): {@link WardGongBlock} (a tripwire hooked to it, or a strike, wakes the
 * guardians) and {@link WardPlateBlock} (a glyph tile in the floor; sneak over it or step round it).</li>
 * <li>Traps: {@link DartTrapBlock} (knee or head high: jump or crouch), {@link CrumblingFloorBlock}, the Crushing
 * Corridor ({@link CrusherCoreBlock} under its floor drives {@link #CRUSHER} walls).</li>
 * <li>The Hall of Trials: {@link GlyphPlateBlock} + {@link #GLYPH_CARVING} murals, {@link TempleMirrorBlock} +
 * {@link SunLensBlock}, {@link WeightAltarBlock}, the three {@link TempleSealBlock}s and the {@link TempleGateBlock}s
 * they (and the two bosses) open.</li>
 * <li>The throne room: {@link CanopicJarBlock} (Ankhatep's curse anchors) and {@link TempleVaultSealBlock}.</li>
 * </ul>
 */
public final class TempleBlocks {
	public static WardGongBlock WARD_GONG;
	public static WardPlateBlock WARD_PLATE;
	public static DartTrapBlock DART_TRAP;
	public static CrumblingFloorBlock CRUMBLING_FLOOR;
	public static GlyphPlateBlock GLYPH_PLATE;
	public static GlyphCarvingBlock GLYPH_CARVING;
	public static TempleMirrorBlock MIRROR;
	public static SunLensBlock SUN_LENS;
	public static TempleSealBlock SEAL;
	public static WeightAltarBlock WEIGHT_ALTAR;
	public static TempleGateBlock GATE;
	public static CrusherCoreBlock CRUSHER_CORE;
	public static Block CRUSHER;
	public static CanopicJarBlock CANOPIC_JAR;
	public static TempleVaultSealBlock VAULT_SEAL;

	public static BlockEntityType<DartTrapBlockEntity> DART_TRAP_BE;
	public static BlockEntityType<SunLensBlockEntity> SUN_LENS_BE;
	public static BlockEntityType<TempleSealBlockEntity> SEAL_BE;
	public static BlockEntityType<WeightAltarBlockEntity> WEIGHT_ALTAR_BE;
	public static BlockEntityType<CrusherCoreBlockEntity> CRUSHER_CORE_BE;

	public static BlockItem WARD_GONG_ITEM;
	public static BlockItem WARD_PLATE_ITEM;
	public static BlockItem DART_TRAP_ITEM;
	public static BlockItem CRUMBLING_FLOOR_ITEM;
	public static BlockItem GLYPH_PLATE_ITEM;
	public static BlockItem GLYPH_CARVING_ITEM;
	public static BlockItem MIRROR_ITEM;
	public static BlockItem SUN_LENS_ITEM;
	public static BlockItem SEAL_ITEM;
	public static BlockItem WEIGHT_ALTAR_ITEM;
	public static BlockItem GATE_ITEM;
	public static BlockItem CRUSHER_CORE_ITEM;
	public static BlockItem CRUSHER_ITEM;
	public static BlockItem CANOPIC_JAR_ITEM;
	public static BlockItem VAULT_SEAL_ITEM;

	private TempleBlocks() {
	}

	private static BlockBehaviour.Properties stone(MapColor colour) {
		return BlockBehaviour.Properties.of().mapColor(colour).sound(SoundType.STONE);
	}

	private static BlockBehaviour.Properties unbreakable(MapColor colour) {
		return stone(colour).strength(-1.0f, 3600000.0f).pushReaction(PushReaction.BLOCK).noLootTable();
	}

	public static void initialize() {
		WARD_GONG = block("ward_gong", new WardGongBlock(stone(MapColor.GOLD).strength(3.0f, 9.0f).sound(SoundType.METAL)
				.requiresCorrectToolForDrops().noLootTable()));
		WARD_PLATE = block("ward_plate", new WardPlateBlock(stone(MapColor.SAND).strength(1.5f, 6.0f).noCollission()
				.pushReaction(PushReaction.DESTROY).noLootTable()));
		DART_TRAP = block("dart_trap", new DartTrapBlock(stone(MapColor.SAND).strength(3.0f, 9.0f).requiresCorrectToolForDrops().noLootTable()));
		CRUMBLING_FLOOR = block("crumbling_floor", new CrumblingFloorBlock(stone(MapColor.SAND).strength(-1.0f, 3600000.0f)
				.pushReaction(PushReaction.BLOCK).noLootTable().dynamicShape().noOcclusion()));
		GLYPH_PLATE = block("glyph_plate", new GlyphPlateBlock(unbreakable(MapColor.GOLD).noCollission()
				.lightLevel(s -> s.getValue(GlyphPlateBlock.LIT) ? 9 : 2)));
		GLYPH_CARVING = block("glyph_carving", new GlyphCarvingBlock(stone(MapColor.SAND).strength(2.0f, 6.0f).requiresCorrectToolForDrops()));
		MIRROR = block("temple_mirror", new TempleMirrorBlock(unbreakable(MapColor.METAL).sound(SoundType.METAL).noOcclusion()));
		SUN_LENS = block("sun_lens", new SunLensBlock(unbreakable(MapColor.GOLD).lightLevel(s -> 12)));
		SEAL = block("temple_seal", new TempleSealBlock(unbreakable(MapColor.GOLD)
				.lightLevel(s -> s.getValue(TempleSealBlock.BROKEN) ? 0 : 7).noOcclusion()));
		WEIGHT_ALTAR = block("weight_altar", new WeightAltarBlock(unbreakable(MapColor.GOLD).noOcclusion()
				.lightLevel(s -> s.getValue(WeightAltarBlock.SOLVED) ? 10 : 0)));
		GATE = block("temple_gate", new TempleGateBlock(unbreakable(MapColor.SAND)));
		CRUSHER_CORE = block("crusher_core", new CrusherCoreBlock(unbreakable(MapColor.STONE)));
		CRUSHER = block("temple_crusher", new Block(unbreakable(MapColor.STONE)));
		CANOPIC_JAR = block("canopic_jar", new CanopicJarBlock(stone(MapColor.SAND).strength(0.6f, 3.0f).sound(SoundType.DECORATED_POT)
				.noOcclusion().noLootTable().lightLevel(s -> 5)));
		VAULT_SEAL = block("temple_vault_seal", new TempleVaultSealBlock(unbreakable(MapColor.GOLD).sound(SoundType.VAULT).lightLevel(s -> 6)));

		DART_TRAP_BE = be("dart_trap", BlockEntityType.Builder.of(DartTrapBlockEntity::new, DART_TRAP).build(null));
		SUN_LENS_BE = be("sun_lens", BlockEntityType.Builder.of(SunLensBlockEntity::new, SUN_LENS).build(null));
		SEAL_BE = be("temple_seal", BlockEntityType.Builder.of(TempleSealBlockEntity::new, SEAL).build(null));
		WEIGHT_ALTAR_BE = be("weight_altar", BlockEntityType.Builder.of(WeightAltarBlockEntity::new, WEIGHT_ALTAR).build(null));
		CRUSHER_CORE_BE = be("crusher_core", BlockEntityType.Builder.of(CrusherCoreBlockEntity::new, CRUSHER_CORE).build(null));

		WARD_GONG_ITEM = item("ward_gong", WARD_GONG);
		WARD_PLATE_ITEM = item("ward_plate", WARD_PLATE);
		DART_TRAP_ITEM = item("dart_trap", DART_TRAP);
		CRUMBLING_FLOOR_ITEM = item("crumbling_floor", CRUMBLING_FLOOR);
		GLYPH_PLATE_ITEM = item("glyph_plate", GLYPH_PLATE);
		GLYPH_CARVING_ITEM = item("glyph_carving", GLYPH_CARVING);
		MIRROR_ITEM = item("temple_mirror", MIRROR);
		SUN_LENS_ITEM = item("sun_lens", SUN_LENS);
		SEAL_ITEM = item("temple_seal", SEAL);
		WEIGHT_ALTAR_ITEM = item("weight_altar", WEIGHT_ALTAR);
		GATE_ITEM = item("temple_gate", GATE);
		CRUSHER_CORE_ITEM = item("crusher_core", CRUSHER_CORE);
		CRUSHER_ITEM = item("temple_crusher", CRUSHER);
		CANOPIC_JAR_ITEM = item("canopic_jar", CANOPIC_JAR);
		VAULT_SEAL_ITEM = item("temple_vault_seal", VAULT_SEAL);
	}

	/** The blocks with a JEI info page (the ones a player meets and has to understand). */
	public static Item[] infoItems() {
		return new Item[] { WARD_GONG_ITEM, WARD_PLATE_ITEM, DART_TRAP_ITEM, CRUMBLING_FLOOR_ITEM, GLYPH_PLATE_ITEM, MIRROR_ITEM,
				SUN_LENS_ITEM, SEAL_ITEM, WEIGHT_ALTAR_ITEM, GATE_ITEM, CRUSHER_ITEM, CANOPIC_JAR_ITEM, VAULT_SEAL_ITEM };
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		for (Item i : new Item[] { WARD_GONG_ITEM, WARD_PLATE_ITEM, DART_TRAP_ITEM, GLYPH_PLATE_ITEM, GLYPH_CARVING_ITEM, MIRROR_ITEM,
				SUN_LENS_ITEM, WEIGHT_ALTAR_ITEM, CANOPIC_JAR_ITEM }) {
			output.accept(i);
		}
	}

	private static <B extends Block> B block(String path, B block) {
		return Registry.register(BuiltInRegistries.BLOCK, ResourceKey.create(Registries.BLOCK, ProjectHeroMod.id(path)), block);
	}

	private static <T extends net.minecraft.world.level.block.entity.BlockEntity> BlockEntityType<T> be(String path, BlockEntityType<T> type) {
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ProjectHeroMod.id(path), type);
	}

	private static BlockItem item(String path, Block block) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)),
				new BlockItem(block, new Item.Properties()));
	}
}
