package com.projecthero.mod.ironman.fabricator;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ironman.item.IronManItems;

import net.minecraft.world.Container;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import static com.projecthero.mod.ironman.fabricator.FabricationRecipe.in;
import static com.projecthero.mod.ironman.fabricator.FabricationRecipe.of;

/**
 * Every Stark Fabricator recipe, in code (spec sections 10-12, 18, 19). Ordered advanced components
 * first, then blueprints, then the 20 armour pieces. Armour pieces are tech-gated and blueprint-gated
 * so a player cannot skip straight to Mark 50; completing a mark's <em>chestplate</em> advances the
 * technology tree to that mark.
 */
public final class FabricatorRecipes {
	public static final int MAX_ENERGY = 50_000;

	private static final List<FabricationRecipe> ALL = new ArrayList<>();

	private FabricatorRecipes() {
	}

	public static List<FabricationRecipe> all() {
		if (ALL.isEmpty()) {
			build();
		}
		return ALL;
	}

	/**
	 * The recipe currently satisfied by the container, honouring the blueprint slot (slot 10) tier and
	 * the operator's technology level. Returns null if none match.
	 */
	public static FabricationRecipe find(Container container, int operatorTechLevel) {
		return find(container, operatorTechLevel, 0);
	}

	/**
	 * "changes 18": as {@link #find(Container, int)}, but if the blueprint slot holds a suit blueprint
	 * and {@code selectedPiece} (1 = helmet, 2 = chestplate, 3 = leggings, 4 = boots) picks a piece,
	 * that piece's armour recipe is the only armour recipe considered. Component / blueprint recipes are
	 * still matched as a fallback, so the Fabricator can also build parts while a suit blueprint sits in
	 * the slot.
	 */
	public static FabricationRecipe find(Container container, int operatorTechLevel, int selectedPiece) {
		ItemStack blueprint = container.getItem(10);
		int blueprintTier = blueprint.getItem() instanceof com.projecthero.mod.ironman.item.BlueprintItem bp
				? bp.techLevel() : 0;
		FabricationRecipe selected = selectedArmorRecipe(container, selectedPiece);
		if (selected != null && selected.requiredTechLevel() <= operatorTechLevel
				&& selected.requiredTechLevel() <= blueprintTier && selected.matches(container)) {
			return selected;
		}
		for (FabricationRecipe recipe : all()) {
			if (recipe.requiredTechLevel() > operatorTechLevel) {
				continue;
			}
			if (recipe.requiredTechLevel() > blueprintTier) {
				continue;
			}
			// "changes 21": armour is ONLY ever produced via the selected-piece path above, which requires
			// the matching mark's blueprint in the slot. The fallback loop handles components / blueprints
			// only, so a tray of components can never accidentally fabricate an armour piece.
			if (recipe.result().getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				continue;
			}
			if (recipe.matches(container)) {
				return recipe;
			}
		}
		return null;
	}

	/** The armour recipe for the blueprint's suit and the selected piece, or null. */
	public static FabricationRecipe selectedArmorRecipe(Container container, int selectedPiece) {
		if (selectedPiece < 1 || selectedPiece > 4) {
			return null;
		}
		if (!(container.getItem(10).getItem() instanceof com.projecthero.mod.ironman.item.BlueprintItem bp)
				|| bp.suitId() == null) {
			return null;
		}
		ArmorItem.Type type = pieceType(selectedPiece);
		return type == null ? null : byId("iron_man_" + bp.suitId() + "_" + type.getName());
	}

	public static ArmorItem.Type pieceType(int selectedPiece) {
		return switch (selectedPiece) {
			case 1 -> ArmorItem.Type.HELMET;
			case 2 -> ArmorItem.Type.CHESTPLATE;
			case 3 -> ArmorItem.Type.LEGGINGS;
			case 4 -> ArmorItem.Type.BOOTS;
			default -> null;
		};
	}

	public static FabricationRecipe byId(String id) {
		for (FabricationRecipe r : all()) {
			if (r.id().equals(id)) {
				return r;
			}
		}
		return null;
	}

	private static void add(FabricationRecipe recipe) {
		ALL.add(recipe);
	}

	private static ItemStack stack(Item item) {
		return new ItemStack(item);
	}

	private static void build() {
		// ---------------- advanced components ----------------
		// "changes 21": components carry no tech-level gate -- the Blank Blueprint progression (which mark's
		// blueprint you hold) is now the sole gate on the whole tree.
		//
		// "changes 22": component costs are eased off. The ingredient lists are unchanged -- what
		// changed is how much one run produces. A full suit needs 30 plates, 12 servo motors, 10 Stark
		// circuits and so on, and at one-per-run the components, not the suit, were the whole build.
		// Doubling the yield of the bulk parts halves their real cost while leaving every recipe's
		// shape (and the tree's shape) exactly as it was. The two capstone parts -- the Suit Computer
		// and the Advanced Arc Reactor -- stay one-per-run because only one of each goes into a piece;
		// they get slightly cheaper inputs instead.
		add(of("titanium_gold_alloy", new ItemStack(IronManItems.TITANIUM_GOLD_ALLOY, 2), 300, 40, 0,
				in(Items.GOLD_INGOT, 3), in(Items.IRON_INGOT, 3), in(IronManItems.METAL_PLATING, 1)));
		add(of("titanium_gold_plate", new ItemStack(IronManItems.TITANIUM_GOLD_PLATE, 3), 250, 30, 0,
				in(IronManItems.TITANIUM_GOLD_ALLOY, 2), in(IronManItems.METAL_PLATING, 1)));
		add(of("servo_motor", new ItemStack(IronManItems.SERVO_MOTOR, 2), 200, 40, 0,
				in(IronManItems.MECHANICAL_PARTS, 2), in(IronManItems.COPPER_WIRING, 1), in(Items.REDSTONE, 1)));
		add(of("micro_thruster", new ItemStack(IronManItems.MICRO_THRUSTER, 2), 400, 60, 0,
				in(IronManItems.SERVO_MOTOR, 1), in(IronManItems.METAL_PLATING, 2), in(Items.BLAZE_POWDER, 1)));
		add(of("stark_circuit", new ItemStack(IronManItems.STARK_CIRCUIT, 2), 300, 40, 0,
				in(IronManItems.BASIC_CIRCUIT, 2), in(Items.GOLD_INGOT, 1), in(Items.REDSTONE, 1)));
		add(of("repulsor", new ItemStack(IronManItems.REPULSOR, 2), 800, 80, 0,
				in(IronManItems.STARK_CIRCUIT, 1), in(Items.REDSTONE_BLOCK, 2), in(Items.DIAMOND, 1)));
		add(of("flight_stabilizer", new ItemStack(IronManItems.FLIGHT_STABILIZER, 2), 600, 70, 0,
				in(IronManItems.SERVO_MOTOR, 2), in(IronManItems.MICRO_THRUSTER, 1), in(Items.AMETHYST_SHARD, 1)));
		add(of("targeting_module", new ItemStack(IronManItems.TARGETING_MODULE, 2), 500, 60, 0,
				in(IronManItems.BASIC_CIRCUIT, 1), in(Items.ENDER_EYE, 1), in(IronManItems.STARK_CIRCUIT, 1)));
		add(of("suit_computer", stack(IronManItems.SUIT_COMPUTER), 1_200, 100, 0,
				in(IronManItems.STARK_CIRCUIT, 2), in(IronManItems.TARGETING_MODULE, 1), in(Items.AMETHYST_SHARD, 1)));
		add(of("advanced_arc_reactor", stack(IronManItems.ADVANCED_ARC_REACTOR), 4_000, 160, 0,
				in(IronManItems.TITANIUM_GOLD_PLATE, 3), in(IronManItems.STARK_CIRCUIT, 2),
				in(Items.DIAMOND_BLOCK, 1), in(Items.GLOWSTONE, 2)));
		add(of("missile_module", new ItemStack(IronManItems.MISSILE_MODULE, 2), 700, 80, 0,
				in(IronManItems.STARK_CIRCUIT, 2), in(Items.GUNPOWDER, 4), in(IronManItems.SERVO_MOTOR, 1),
				in(IronManItems.METAL_PLATING, 2)));

		// ---------------- blueprints ----------------
		// "changes 21": blueprints are no longer fabricated. Every mark's blueprint is made only by
		// stamping a Blank Blueprint (BlankBlueprintItem), and a mark unlocks only once the whole
		// previous mark's suit is built -- the linear Mark 1 -> 2 -> III -> 4 -> V -> 6 -> VII gate.
		// (Mark XLII / Mark L blueprints were already removed with those suits in "changes 17".)

		// ---------------- armour pieces ----------------
		// "changes 21": armour is gated purely by "the matching mark's blueprint is in the slot" -- which
		// the Blank Blueprint progression controls -- so the recipes carry no tech-level requirement. The
		// per-mark number below only scales fabrication time (higher mark = longer build).
		mark2ArmorSet();
		armorSet("mark_4", 1, 0.9f);
		armorSet("mark_6", 2, 1.0f);
		armorSet("mark_iii", 1, 1.0f);
		armorSet("mark_v", 2, 0.7f);
		armorSet("mark_vii", 3, 1.2f);
	}

	/** "changes 18/19": true if this suit has Fabricator armour recipes (i.e. everything except the Mark 1). */
	public static boolean hasArmorRecipes(String suitId) {
		return byId("iron_man_" + suitId + "_helmet") != null;
	}

	/**
	 * Generates the four armour recipes for a mark. Base cost/time from the Mark III spec examples,
	 * scaled by {@code scale}; every mark adds its signature component. The recipes are gated only by
	 * the mark's own blueprint sitting in the blueprint slot (the Blank Blueprint progression), not by
	 * a tech level; {@code timeTier} just stretches the fabrication time for the later marks.
	 */
	private static void armorSet(String suitId, int timeTier, float scale) {
		Item plate = IronManItems.TITANIUM_GOLD_PLATE;
		Item signature = switch (suitId) {
			case "mark_v" -> IronManItems.MICRO_THRUSTER;
			case "mark_vii" -> IronManItems.MISSILE_MODULE;
			default -> IronManItems.STARK_CIRCUIT;
		};

		add(armor(suitId, ArmorItem.Type.HELMET, timeTier, scale, 160,
				in(plate, 6), in(IronManItems.SERVO_MOTOR, 2), in(IronManItems.TARGETING_MODULE, 1),
				in(IronManItems.STARK_CIRCUIT, 2), in(IronManItems.SUIT_COMPUTER, 1), in(signature, 1)));

		add(armor(suitId, ArmorItem.Type.CHESTPLATE, timeTier, scale, 240,
				in(plate, 10), in(IronManItems.SERVO_MOTOR, 4), in(IronManItems.REPULSOR, 1),
				in(IronManItems.FLIGHT_STABILIZER, 1), in(IronManItems.ADVANCED_ARC_REACTOR, 1),
				in(IronManItems.STARK_CIRCUIT, 4), in(signature, 2)));

		add(armor(suitId, ArmorItem.Type.LEGGINGS, timeTier, scale, 200,
				in(plate, 8), in(IronManItems.SERVO_MOTOR, 4), in(IronManItems.STARK_CIRCUIT, 2),
				in(IronManItems.FLIGHT_STABILIZER, 1), in(signature, 1)));

		add(armor(suitId, ArmorItem.Type.BOOTS, timeTier, scale, 200,
				in(plate, 6), in(IronManItems.MICRO_THRUSTER, 2), in(IronManItems.REPULSOR, 2),
				in(IronManItems.FLIGHT_STABILIZER, 1), in(IronManItems.SERVO_MOTOR, 2), in(signature, 1)));
	}

	/**
	 * Mark 2's own fixed component list (v0.11.13, explicit user request) -- replaces the shared
	 * {@link #armorSet} generation entirely. Built from the basic {@code METAL_PLATING} rather than the
	 * advanced {@code TITANIUM_GOLD_PLATE} every other mark's armour uses, in keeping with Mark 2 being
	 * the cheaper primitive-tier suit.
	 */
	private static void mark2ArmorSet() {
		int timeTier = 1;
		float scale = 0.4f;
		Item plate = IronManItems.METAL_PLATING;

		add(armor("mark_2", ArmorItem.Type.HELMET, timeTier, scale, 160,
				in(plate, 6), in(IronManItems.SERVO_MOTOR, 2), in(IronManItems.TARGETING_MODULE, 1),
				in(IronManItems.STARK_CIRCUIT, 3), in(IronManItems.SUIT_COMPUTER, 1)));

		add(armor("mark_2", ArmorItem.Type.CHESTPLATE, timeTier, scale, 240,
				in(plate, 6), in(IronManItems.SERVO_MOTOR, 4), in(IronManItems.REPULSOR, 1),
				in(IronManItems.FLIGHT_STABILIZER, 1), in(IronManItems.STARK_CIRCUIT, 4)));

		add(armor("mark_2", ArmorItem.Type.LEGGINGS, timeTier, scale, 200,
				in(plate, 6), in(IronManItems.SERVO_MOTOR, 4), in(IronManItems.STARK_CIRCUIT, 2),
				in(IronManItems.FLIGHT_STABILIZER, 1)));

		add(armor("mark_2", ArmorItem.Type.BOOTS, timeTier, scale, 200,
				in(plate, 6), in(IronManItems.MICRO_THRUSTER, 2), in(IronManItems.REPULSOR, 2),
				in(IronManItems.FLIGHT_STABILIZER, 1), in(IronManItems.SERVO_MOTOR, 2), in(IronManItems.STARK_CIRCUIT, 1)));
	}

	private static FabricationRecipe armor(String suitId, ArmorItem.Type type, int timeTier, float scale, int baseTime,
			FabricationRecipe.Input... inputs) {
		ItemStack result = new ItemStack(IronManItems.armor(suitId, type));
		// "changes 22": every armour piece costs the Fabricator's ENTIRE buffer. Building one drains the
		// machine flat, and the next piece cannot start until its own reactor has charged all the way
		// back up (StarkFabricatorBlockEntity.SELF_RECHARGE_SECONDS) -- so a four-piece suit is paced by
		// the machine, not just by how many components you can pile in. `scale` now only affects time.
		int energy = MAX_ENERGY;
		int time = Math.round(baseTime * (0.8f + 0.15f * timeTier));
		return new FabricationRecipe("iron_man_" + suitId + "_" + type.getName(),
				List.of(inputs), energy, time, result, 0, null);
	}
}
