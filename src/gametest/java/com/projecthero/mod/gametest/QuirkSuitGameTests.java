package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.item.QuirkSuitItem;
import com.projecthero.mod.quirk.item.QuirkSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user): the Quirk hero suits are craftable armour and a Quirk no longer changes the player's look -- granting
 * a Quirk puts nothing on; the 28 pieces exist with the right slot, armour and durability; every recipe loads, outputs
 * its piece and is matched by its armour-shaped grid; anyone (no Quirk) can wear a piece.
 */
public class QuirkSuitGameTests implements FabricGameTest {
	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	private static final EquipmentSlot[] ARMOUR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantingAQuirkPutsNothingOn(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		for (Quirk q : Quirk.values()) {
			helper.assertTrue(Quirks.grant(p, q), "grant " + q);
			for (EquipmentSlot slot : ARMOUR) {
				helper.assertTrue(p.getItemBySlot(slot).isEmpty(), q + " put something in " + slot);
			}
			helper.assertFalse(QuirkSuits.anyWorn(p), q + ": no suit is worn just by holding the Quirk");
			Quirks.revoke(p);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void the28PiecesExist(GameTestHelper helper) {
		int[] defense = { 2, 6, 5, 2 };
		// v0.16.7: the helmetless Ingenium suit (the old helmet is retired: 0) -- 7 / 6 / 2 with 1 toughness a piece
		int[] ingenium = { 0, 7, 6, 2 };
		int n = 0;
		for (QuirkSuits.Suit s : QuirkSuits.Suit.values()) {
			for (int i = 0; i < QuirkSuits.TYPES.length; i++) {
				ArmorItem.Type t = QuirkSuits.TYPES[i];
				String id = QuirkSuits.itemId(s, t);
				Item item = BuiltInRegistries.ITEM.get(ProjectHeroMod.id(id));
				helper.assertTrue(item instanceof QuirkSuitItem, id + " is registered (old saves keep their pieces)");
				QuirkSuitItem piece = (QuirkSuitItem) item;
				boolean ing = s == QuirkSuits.Suit.INGENIUM;
				helper.assertTrue(piece == QuirkSuits.piece(s.quirk, t) && piece.suit == s, id + " is " + s + "'s " + t);
				helper.assertTrue(piece.getEquipmentSlot() == ARMOUR[i], id + " goes in " + ARMOUR[i]);
				int want = ing ? ingenium[i] : defense[i];
				helper.assertTrue(piece.getDefense() == want, id + " armour " + piece.getDefense() + " want " + want);
				float tough = ing && !piece.retired ? 1f : 0f;
				helper.assertTrue(piece.getToughness() == tough, id + " toughness " + piece.getToughness() + " want " + tough);
				helper.assertTrue(piece.retired == (ing && t == ArmorItem.Type.HELMET), id + " retired only for the Ingenium helmet");
				int iron = t.getDurability(15);
				int max = new ItemStack(piece).getMaxDamage();
				helper.assertTrue(max > iron && max <= iron * 5 / 4, id + " durability " + max + " (iron " + iron + ")");
				helper.assertTrue(piece.getMaterial().value().repairIngredient().get().test(new ItemStack(Items.IRON_INGOT)),
						id + " is repaired with iron");
				helper.assertFalse(piece.getMaterial() == ArmorMaterials.IRON, id + " is its own material");
				n++;
			}
		}
		helper.assertTrue(n == 28, "28 pieces, got " + n);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ingeniumHasNoHelmetAndKeepsItsProtection(GameTestHelper helper) {
		QuirkSuits.Suit ing = QuirkSuits.Suit.INGENIUM;
		helper.assertTrue(ing.pieces().length == 3 && !ing.has(ArmorItem.Type.HELMET), "chestplate, leggings, boots");
		int total = 0;
		float tough = 0f;
		for (ArmorItem.Type t : ing.pieces()) {
			total += QuirkSuits.piece(Quirk.ENGINE, t).getDefense();
			tough += QuirkSuits.piece(Quirk.ENGINE, t).getToughness();
		}
		int old = 2 + 6 + 5 + 2;
		helper.assertTrue(total == old, "the three pieces add up to the old four-piece set's " + old + ", got " + total);
		helper.assertTrue(tough >= 3f, "and are tougher: " + tough);
		// the old helmet: still an item (saves keep it), but it does nothing and isn't part of the suit
		ServerPlayer p = mock(helper);
		QuirkSuitItem helmet = QuirkSuits.piece(Quirk.ENGINE, ArmorItem.Type.HELMET);
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
		helper.assertTrue(QuirkSuits.wornIn(p, EquipmentSlot.HEAD) == null, "a worn old helmet is not read as the suit (never drawn)");
		helper.assertTrue(helmet.getDefense() == 0, "and gives no protection");
		helper.assertFalse(QuirkSuits.fullSetWorn(p, ing), "the helmet alone is no set");
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(QuirkSuits.piece(Quirk.ENGINE, ArmorItem.Type.CHESTPLATE)));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(QuirkSuits.piece(Quirk.ENGINE, ArmorItem.Type.LEGGINGS)));
		helper.assertFalse(QuirkSuits.fullSetWorn(p, ing), "two pieces are not the set");
		p.setItemSlot(EquipmentSlot.FEET, new ItemStack(QuirkSuits.piece(Quirk.ENGINE, ArmorItem.Type.BOOTS)));
		helper.assertTrue(QuirkSuits.fullSetWorn(p, ing), "chestplate + leggings + boots are the full set (no helmet needed)");
		p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		helper.assertTrue(QuirkSuits.fullSetWorn(p, ing), "any helmet can go with it");
		// no recipe makes it any more; it salvages into iron
		var recipes = helper.getLevel().getServer().getRecipeManager();
		helper.assertTrue(recipes.byKey(ProjectHeroMod.id(QuirkSuits.itemId(ing, ArmorItem.Type.HELMET))).isEmpty(), "the helmet recipe is gone");
		var salvage = recipes.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(1, 1, List.of(new ItemStack(helmet))), helper.getLevel());
		helper.assertTrue(salvage.isPresent() && salvage.get().value().assemble(CraftingInput.of(1, 1, List.of(new ItemStack(helmet))),
				helper.getLevel().registryAccess()).is(Items.IRON_INGOT), "the old helmet crafts back into iron");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void ingeniumSuitHalvesEngineHeat(GameTestHelper helper) {
		ServerPlayer bare = engineRunner(helper, 1.5);
		ServerPlayer suited = engineRunner(helper, 6.5);
		for (ArmorItem.Type t : QuirkSuits.Suit.INGENIUM.pieces()) {
			suited.setItemSlot(t.getSlot(), new ItemStack(QuirkSuits.piece(Quirk.ENGINE, t)));
		}
		helper.assertTrue(com.projecthero.mod.quirk.engine.Engine.suitWorn(suited) && !com.projecthero.mod.quirk.engine.Engine.suitWorn(bare),
				"the suit is read as worn");
		helper.onEachTick(() -> {
			for (ServerPlayer p : new ServerPlayer[] { bare, suited }) {
				p.setPos(p.getX(), p.getY(), p.getZ() + 0.4);
				p.setOnGround(true);
				p.tickCount++;
				Quirks.tick(p);
			}
		});
		helper.runAfterDelay(40, () -> {
			float a = Quirks.meter(bare), b = Quirks.meter(suited);
			helper.assertTrue(a > 4f && Math.abs(b - a * 0.5f) < a * 0.08f, "the suit heats half as fast: bare " + a + ", suited " + b);
			// a kick's heat is halved too
			Quirks.setMeter(bare, 0f);
			Quirks.setMeter(suited, 0f);
			com.projecthero.mod.quirk.engine.EngineMoves.heat(bare, 10f);
			com.projecthero.mod.quirk.engine.EngineMoves.heat(suited, 10f);
			helper.assertTrue(Math.abs(Quirks.meter(bare) - 10f) < 0.01f && Math.abs(Quirks.meter(suited) - 5f) < 0.01f, "10 heat is 5 in the suit");
			helper.succeed();
		});
	}

	@SuppressWarnings("removal")
	private static ServerPlayer engineRunner(GameTestHelper helper, double x) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, 0.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		helper.assertTrue(Quirks.grant(p, Quirk.ENGINE), "Engine is granted");
		com.projecthero.mod.quirk.engine.EngineMoves.setGear(p, 3);
		p.setSprinting(true);
		return p;
	}

	/** The grid the recipe JSONs use (I = iron, A = the suit's main item, B = its accent). */
	private static String[] pattern(ArmorItem.Type t) {
		return switch (t) {
			case HELMET -> new String[] { "IAI", "B B", "   " };
			case CHESTPLATE -> new String[] { "I I", "ABA", "III" };
			case LEGGINGS -> new String[] { "IAI", "B B", "I I" };
			default -> new String[] { "B B", "I I", "   " };
		};
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyRecipeCraftsItsPiece(GameTestHelper helper) {
		var level = helper.getLevel();
		var recipes = level.getServer().getRecipeManager();
		for (QuirkSuits.Suit s : QuirkSuits.Suit.values()) {
			for (ArmorItem.Type t : s.pieces()) { // v0.16.7: not the retired Ingenium helmet
				String id = QuirkSuits.itemId(s, t);
				Item piece = QuirkSuits.piece(s.quirk, t);
				var holder = recipes.byKey(ProjectHeroMod.id(id));
				helper.assertTrue(holder.isPresent(), id + " recipe loads");
				helper.assertTrue(holder.get().value().getResultItem(level.registryAccess()).is(piece), id + " recipe outputs the piece");
				List<ItemStack> grid = new ArrayList<>();
				for (String row : pattern(t)) {
					for (char c : row.toCharArray()) {
						grid.add(switch (c) {
							case 'I' -> new ItemStack(Items.IRON_INGOT);
							case 'A' -> new ItemStack(s.main);
							case 'B' -> new ItemStack(s.accent);
							default -> ItemStack.EMPTY;
						});
					}
				}
				var match = recipes.getRecipeFor(RecipeType.CRAFTING, CraftingInput.of(3, 3, grid), level);
				helper.assertTrue(match.isPresent() && match.get().value().assemble(CraftingInput.of(3, 3, grid), level.registryAccess()).is(piece),
						id + ": its armour-shaped grid crafts it");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anyoneCanWearAPiece(GameTestHelper helper) {
		ServerPlayer p = mock(helper);
		helper.assertFalse(Quirks.hasPower(p), "no Quirk");
		for (Quirk q : new Quirk[] { Quirk.HELLFLAME, Quirk.ZERO_GRAVITY }) {
			for (int i = 0; i < QuirkSuits.TYPES.length; i++) {
				Item piece = QuirkSuits.piece(q, QuirkSuits.TYPES[i]);
				p.setItemSlot(ARMOUR[i], ItemStack.EMPTY);
				p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(piece));
				var result = piece.use(p.level(), p, InteractionHand.MAIN_HAND);
				helper.assertTrue(result.getResult().consumesAction(), q + " " + QuirkSuits.TYPES[i] + ": right-click wears it");
				helper.assertTrue(p.getItemBySlot(ARMOUR[i]).is(piece), q + " " + QuirkSuits.TYPES[i] + " is worn in " + ARMOUR[i]);
				helper.assertTrue(QuirkSuits.wornIn(p, ARMOUR[i]) == q, "and is read back as " + q + "'s suit");
			}
			helper.assertFalse(Quirks.hasPower(p), "wearing the suit gives no Quirk");
		}
		helper.succeed();
	}
}
