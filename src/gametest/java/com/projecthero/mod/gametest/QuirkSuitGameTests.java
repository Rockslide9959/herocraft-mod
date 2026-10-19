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
		int n = 0;
		for (QuirkSuits.Suit s : QuirkSuits.Suit.values()) {
			for (int i = 0; i < QuirkSuits.TYPES.length; i++) {
				ArmorItem.Type t = QuirkSuits.TYPES[i];
				String id = QuirkSuits.itemId(s, t);
				Item item = BuiltInRegistries.ITEM.get(ProjectHeroMod.id(id));
				helper.assertTrue(item instanceof QuirkSuitItem, id + " is registered");
				QuirkSuitItem piece = (QuirkSuitItem) item;
				helper.assertTrue(piece == QuirkSuits.piece(s.quirk, t) && piece.suit == s, id + " is " + s + "'s " + t);
				helper.assertTrue(piece.getEquipmentSlot() == ARMOUR[i], id + " goes in " + ARMOUR[i]);
				helper.assertTrue(piece.getDefense() == defense[i], id + " armour " + piece.getDefense() + " want " + defense[i]);
				helper.assertTrue(piece.getToughness() == 0f, id + " has no toughness");
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
			for (ArmorItem.Type t : QuirkSuits.TYPES) {
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
