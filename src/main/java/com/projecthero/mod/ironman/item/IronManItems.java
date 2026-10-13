package com.projecthero.mod.ironman.item;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Every Iron Man item: Stark components (basic + advanced), the Arc Reactor, machine power cores,
 * suit blueprints, the Mark V Suitcase, and all 20 armor pieces (helmet/chest/legs/boots for the five
 * marks of the first content pack).
 *
 * <p>Registered separately from Thor's {@code ModItems} and HeroPack's {@code HeroPackItems};
 * appended to the shared {@code projecthero:superheroes} creative tab from {@code ModCreativeTab}.
 */
public final class IronManItems {
	// NOTE: these lookup maps MUST be declared before any item field below -- static fields initialise
	// in source order, and register() writes into ALL during that init.
	private static final Map<String, EnumMap<ArmorItem.Type, IronManArmorItem>> ARMOR = new LinkedHashMap<>();
	private static final Map<String, Item> ALL = new LinkedHashMap<>();

	/** suit ids of the first content pack, tech tier order (Stark Fabricator + blueprint gated).
	 *  "changes 17": Mark XLII / Mark L removed. */
	public static final List<String> SUIT_IDS = List.of("mark_iii", "mark_v", "mark_vii", "mark_8");
	/**
	 * Mark 1 / Mark 2 ("changes 12"): primitive first-generation prototypes. Deliberately NOT in
	 * {@link #SUIT_IDS} -- they skip the Stark Fabricator / blueprint / tech-level gate entirely and
	 * are craftable at a normal table the moment a player has the Tony Stark power, the same way the
	 * Arc Reactor itself is. See {@code data/projecthero/recipe/iron_man_mark_1_*.json} / {@code mark_2_*}.
	 */
	public static final List<String> PRIMITIVE_SUIT_IDS = List.of("mark_1", "mark_2", "mark_4", "mark_6");

	// ---- basic components (normal crafting table) ----
	public static final Item COPPER_WIRING = simple("copper_wiring");
	public static final Item METAL_PLATING = simple("metal_plating");
	public static final Item BASIC_CIRCUIT = simple("basic_circuit");
	public static final Item MECHANICAL_PARTS = simple("mechanical_parts");

	// ---- advanced Stark components (Stark Fabricator only) ----
	public static final Item TITANIUM_GOLD_ALLOY = simple("titanium_gold_alloy");
	public static final Item TITANIUM_GOLD_PLATE = simple("titanium_gold_plate");
	public static final Item SERVO_MOTOR = simple("servo_motor");
	public static final Item MICRO_THRUSTER = simple("micro_thruster");
	/**
	 * "changes 22": no longer a plain component. Still crafted, stacked and consumed by the Fabricator
	 * exactly as before, but it is now a {@link RepulsorItem}: wearable in the boots slot for repulsor
	 * flight at half the Mark 2's speed, and right-click-and-hold to fire the Mark 2's repulsor blast.
	 */
	public static final Item REPULSOR = register("repulsor",
			new RepulsorItem(IronManArmorMaterials.REPULSOR_BOOTS, new Item.Properties()));
	public static final Item FLIGHT_STABILIZER = simple("flight_stabilizer");
	public static final Item TARGETING_MODULE = simple("targeting_module");
	public static final Item STARK_CIRCUIT = simple("stark_circuit");
	public static final Item SUIT_COMPUTER = simple("suit_computer");
	public static final Item ADVANCED_ARC_REACTOR = simple("advanced_arc_reactor");
	public static final Item MISSILE_MODULE = simple("missile_module");
	public static final Item MODULAR_ARMOR_CONTROLLER = simple("modular_armor_controller");
	public static final Item NANOTECH_MATRIX = simple("nanotech_matrix");

	/**
	 * Machine power core. This -- not a power-granting Arc Reactor -- is what the Stark Fabricator
	 * recipe and the Suit Platform recipe consume, so the player's original Arc Reactor stays
	 * specifically the thing that unlocked Tony Stark.
	 */
	public static final Item REACTOR_CORE = simple("reactor_core");

	// ---- special ----
	public static final Item ARC_REACTOR = register("arc_reactor", new ArcReactorItem(
			new Item.Properties().rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));
	public static final Item MARK_V_SUITCASE = register("mark_v_suitcase", new MarkVSuitcaseItem(
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
	// v0.15.1: Tony Stark's tinted glasses -- worn in the Stark Gear slot (Sneak + N), see ironman.gear.StarkGear
	public static final Item STARK_GLASSES = register("stark_glasses", new com.projecthero.mod.ironman.gear.StarkGlassesItem(
			new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
	// v0.15.4: the Mark 7's Colantotte Bracelets -- from a Suit Platform holding a Mark 7, see ironman.gear.ColantotteBracelets
	public static final Item COLANTOTTE_BRACELETS = register("colantotte_bracelets",
			new com.projecthero.mod.ironman.gear.ColantotteBraceletsItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

	// ---- blueprints ----
	// "changes 15": every craftable mark now has a blueprint item too (craftable at a table, and it
	// enables the Fabricator path). Primitive marks are tier 0 -- the blueprint is not a gate, the
	// armour is still normal-table craftable the moment a player has the Tony Stark power.
	public static final Item MARK_1_BLUEPRINT = blueprint("mark_1_blueprint", 0, "mark_1");
	public static final Item MARK_2_BLUEPRINT = blueprint("mark_2_blueprint", 0, "mark_2");
	public static final Item MARK_III_BLUEPRINT = blueprint("mark_iii_blueprint", 1, "mark_iii");
	public static final Item MARK_4_BLUEPRINT = blueprint("mark_4_blueprint", 0, "mark_4");
	public static final Item MARK_6_BLUEPRINT = blueprint("mark_6_blueprint", 0, "mark_6");
	public static final Item MARK_V_BLUEPRINT = blueprint("mark_v_blueprint", 2, "mark_v");
	public static final Item MARK_VII_BLUEPRINT = blueprint("mark_vii_blueprint", 3, "mark_vii");
	public static final Item MARK_8_BLUEPRINT = blueprint("mark_8_blueprint", 3, "mark_8"); // v0.15.9
	// "changes 17": Mark XLII / Mark L blueprints removed with those suits.

	/**
	 * "changes 21": the one craftable blueprint. Shift-right-click it (see {@link BlankBlueprintItem})
	 * to open a picker and stamp it into a specific mark's blueprint; a mark is offered only once the
	 * whole previous mark's suit is built (the linear Mark 1 -> 2 -> III -> 4 -> V -> 6 -> VII -> 8 gate).
	 */
	public static final Item BLANK_BLUEPRINT = register("blank_blueprint",
			new BlankBlueprintItem(new Item.Properties()));

	/** Every mark in linear progression order (by mark number). Index 0 has no blueprint prerequisite. */
	public static final List<String> MARK_ORDER =
			List.of("mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii", "mark_8");

	/** The blueprint item a Blank Blueprint becomes when stamped for {@code suitId}, or null. */
	public static Item blueprintFor(String suitId) {
		return switch (suitId) {
			case "mark_1" -> MARK_1_BLUEPRINT;
			case "mark_2" -> MARK_2_BLUEPRINT;
			case "mark_iii" -> MARK_III_BLUEPRINT;
			case "mark_4" -> MARK_4_BLUEPRINT;
			case "mark_v" -> MARK_V_BLUEPRINT;
			case "mark_6" -> MARK_6_BLUEPRINT;
			case "mark_vii" -> MARK_VII_BLUEPRINT;
			case "mark_8" -> MARK_8_BLUEPRINT;
			default -> null;
		};
	}

	/** The mark whose full suit must be built before {@code suitId}'s blueprint unlocks, or null for Mark 1. */
	public static String prerequisiteSuit(String suitId) {
		int i = MARK_ORDER.indexOf(suitId);
		return i <= 0 ? null : MARK_ORDER.get(i - 1);
	}

	// ---- armor (populated in initialize) ----

	private IronManItems() {
	}

	public static void initialize() {
		IronManArmorMaterials.initialize();
		for (String suitId : SUIT_IDS) {
			var material = switch (suitId) {
				case "mark_iii" -> IronManArmorMaterials.MARK_III;
				case "mark_v" -> IronManArmorMaterials.MARK_V;
				case "mark_8" -> IronManArmorMaterials.MARK_8;
				default -> IronManArmorMaterials.MARK_VII;
			};
			registerSuitArmor(suitId, material);
		}
		for (String suitId : PRIMITIVE_SUIT_IDS) {
			registerSuitArmor(suitId, switch (suitId) {
				case "mark_1" -> IronManArmorMaterials.MARK_1;
				case "mark_2" -> IronManArmorMaterials.MARK_2;
				case "mark_6" -> IronManArmorMaterials.MARK_6;
				default -> IronManArmorMaterials.MARK_4;
			});
		}
		// v0.14.30: a dispenser just drops Iron Man armour -- it no longer straps it onto whoever stands in front (armour
		// only goes on through C or a suit deploy)
		for (IronManArmorItem piece : armorPiecesByMark()) {
			net.minecraft.world.level.block.DispenserBlock.registerBehavior(piece, new net.minecraft.core.dispenser.DefaultDispenseItemBehavior());
		}
	}

	private static void registerSuitArmor(String suitId, Holder<ArmorMaterial> material) {
		EnumMap<ArmorItem.Type, IronManArmorItem> pieces = new EnumMap<>(ArmorItem.Type.class);
		for (ArmorItem.Type type : new ArmorItem.Type[] {
				ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
			String path = "iron_man_" + itemName(suitId) + "_" + type.getName();
			IronManArmorItem item = register(path, new IronManArmorItem(material, type,
					new Item.Properties().rarity(Rarity.EPIC)
							// v0.14.22, explicit user request: no durability -- suit integrity is the damage model.
							// No max_damage means vanilla never wears the piece down (and an old save's leftover
							// damage value is simply ignored); durability() also set the stack size, so keep it at 1.
							.stacksTo(1)
							.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false), // v0.14.27: no enchanted shimmer on Iron Man armour
					suitId));
			pieces.put(type, item);
		}
		ARMOR.put(suitId, pieces);
	}

	/**
	 * v0.15.6, user request: the armour item ids use the Mark's number -- {@code iron_man_mark_7_leggings} -- while the
	 * suit ids inside the mod (saved state, config, models) keep their old names. Mark 1 / 2 / 4 / 6 were already numbers.
	 */
	public static String itemName(String suitId) {
		return switch (suitId) {
			case "mark_iii" -> "mark_3";
			case "mark_v" -> "mark_5";
			case "mark_vii" -> "mark_7";
			default -> suitId;
		};
	}

	private static final java.util.regex.Pattern LEGACY = java.util.regex.Pattern.compile("iron_man_mark_(iii|v|vii)_(helmet|chestplate|leggings|boots)");

	/** v0.15.6: an old roman-numeral armour item id -> its new numbered id (anything else unchanged). */
	public static net.minecraft.resources.ResourceLocation legacyId(net.minecraft.resources.ResourceLocation id) {
		if (id == null || !ProjectHeroMod.MOD_ID.equals(id.getNamespace()) || !id.getPath().startsWith("iron_man_mark_")) {
			return id;
		}
		java.util.regex.Matcher m = LEGACY.matcher(id.getPath());
		if (!m.matches()) {
			return id;
		}
		return ProjectHeroMod.id("iron_man_" + itemName("mark_" + m.group(1)) + "_" + m.group(2));
	}

	/** v0.15.6: as {@link #legacyId(net.minecraft.resources.ResourceLocation)} for a registry key. */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static net.minecraft.resources.ResourceKey<?> legacyKey(net.minecraft.resources.ResourceKey<?> key) {
		if (key == null) {
			return null;
		}
		net.minecraft.resources.ResourceLocation to = legacyId(key.location());
		return to == key.location() ? key : net.minecraft.resources.ResourceKey.create((net.minecraft.resources.ResourceKey) key.registryKey(), to);
	}

	public static IronManArmorItem armor(String suitId, ArmorItem.Type type) {
		EnumMap<ArmorItem.Type, IronManArmorItem> pieces = ARMOR.get(suitId);
		return pieces == null ? null : pieces.get(type);
	}

	public static boolean isIronManArmor(Item item) {
		return item instanceof IronManArmorItem;
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(ARC_REACTOR);
		output.accept(com.projecthero.mod.ironman.IronManBlocks.STARK_FABRICATOR_ITEM);
		output.accept(com.projecthero.mod.ironman.IronManBlocks.IRON_MAN_SUIT_PLATFORM_ITEM);
		output.accept(com.projecthero.mod.ironman.IronManBlocks.STARK_GANTRY_FLOOR_ITEM); // v0.15.4
		output.accept(com.projecthero.mod.ironman.sorter.StarkSorter.STATION_ITEM); // v0.14.16
		output.accept(REACTOR_CORE);
		output.accept(MARK_V_SUITCASE);
		output.accept(STARK_GLASSES); // v0.15.1
		output.accept(COLANTOTTE_BRACELETS); // v0.15.4

		output.accept(BLANK_BLUEPRINT);
		output.accept(MARK_1_BLUEPRINT);
		output.accept(MARK_2_BLUEPRINT);
		output.accept(MARK_III_BLUEPRINT);
		output.accept(MARK_4_BLUEPRINT);
		output.accept(MARK_V_BLUEPRINT);
		output.accept(MARK_6_BLUEPRINT);
		output.accept(MARK_VII_BLUEPRINT);
		output.accept(MARK_8_BLUEPRINT); // v0.15.9

		for (Item item : List.of(COPPER_WIRING, METAL_PLATING, BASIC_CIRCUIT, MECHANICAL_PARTS,
				TITANIUM_GOLD_ALLOY, TITANIUM_GOLD_PLATE, SERVO_MOTOR, MICRO_THRUSTER, REPULSOR, FLIGHT_STABILIZER,
				TARGETING_MODULE, STARK_CIRCUIT, SUIT_COMPUTER, ADVANCED_ARC_REACTOR, MISSILE_MODULE)) {
			// v0.14.21: MODULAR_ARMOR_CONTROLLER / NANOTECH_MATRIX stay registered (old worlds may hold them) but are no
			// longer listed -- they were for the never-built Mark 42 / Mark 50 and have no use. Also tagged
			// c:hidden_from_recipe_viewers.
			output.accept(item);
		}
		for (IronManArmorItem piece : armorPiecesByMark()) {
			output.accept(piece);
		}
	}

	/**
	 * "changes 17": every Iron Man armour piece, ordered by mark number (Mark I → Mark II → Mark III →
	 * …) and then helmet → chestplate → leggings → boots within a mark. Used by the dedicated Iron Man
	 * Armour creative tab and by the main Superheroes tab, so both list the suits in progression order
	 * rather than the order the marks happened to be added to the registry.
	 */
	public static List<IronManArmorItem> armorPiecesByMark() {
		java.util.List<IronManArmorItem> out = new java.util.ArrayList<>();
		java.util.List<com.projecthero.mod.ironman.suit.IronManSuit> suits =
				new java.util.ArrayList<>(com.projecthero.mod.ironman.suit.IronManSuits.all());
		suits.sort(java.util.Comparator.comparingInt(com.projecthero.mod.ironman.suit.IronManSuit::markNumber));
		for (com.projecthero.mod.ironman.suit.IronManSuit suit : suits) {
			EnumMap<ArmorItem.Type, IronManArmorItem> pieces = ARMOR.get(suit.id());
			if (pieces == null) {
				continue;
			}
			for (ArmorItem.Type type : new ArmorItem.Type[] {
					ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
				out.add(pieces.get(type));
			}
		}
		return out;
	}

	// ---- registration helpers ----

	private static Item simple(String path) {
		return register(path, new Item(new Item.Properties()));
	}

	private static Item blueprint(String path, int techLevel, String suitId) {
		return register(path, new BlueprintItem(new Item.Properties(), techLevel, suitId));
	}

	private static <T extends Item> T register(String path, T item) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path));
		T registered = Registry.register(BuiltInRegistries.ITEM, key, item);
		ALL.put(path, registered);
		return registered;
	}
}
