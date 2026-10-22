package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.PortableDeploy;
import com.projecthero.mod.ironman.suit.PortableForm;
import com.projecthero.mod.ironman.suit.PortableForms;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;

/**
 * v0.16.7 (user) Iron Man Era II fold polish: every portable form packs from four loose pieces (right-click one), an
 * emptied packed item is filled instead of a new one being made, a backpack never lands in the hand, and every packed
 * item has an EMPTY crafting recipe (a crafted one never conjures a suit).
 */
public class IronManFoldPolishGameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	private static ServerPlayer player(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.getInventory().clearContent();
		return p;
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		for (ItemStack s : p.getInventory().items) {
			if (s.is(item)) {
				n++;
			}
		}
		return n;
	}

	private static ItemStack find(ServerPlayer p, Item item) {
		for (ItemStack s : p.getInventory().items) {
			if (s.is(item)) {
				return s;
			}
		}
		return ItemStack.EMPTY;
	}

	/** The four loose pieces of {@code suit} in slots 0..3; the selected slot is 0 (the helmet). */
	private static void loose(ServerPlayer p, String suit) {
		for (int i = 0; i < 4; i++) {
			p.getInventory().items.set(i, new ItemStack(IronManItems.armor(suit, TYPES[i])));
		}
		p.getInventory().selected = 0;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyPortableFormPacksFromLoosePieces(GameTestHelper h) {
		for (PortableForm form : PortableForms.all()) {
			ServerPlayer p = player(h);
			Item packed = form.packedItem().get();
			for (int i = 0; i < 3; i++) {
				p.getInventory().items.set(i, new ItemStack(IronManItems.armor(form.suitId(), TYPES[i])));
			}
			h.assertFalse(IronManArmorItem.packLoose(p, form.suitId()), form.suitId() + ": three pieces must not pack");
			loose(p, form.suitId());
			var r = p.getInventory().getSelected().getItem().use(h.getLevel(), p, InteractionHand.MAIN_HAND);
			h.assertTrue(r.getResult().consumesAction(), form.suitId() + ": right-clicking a loose piece packs them");
			h.assertTrue(count(p, packed) == 1, form.suitId() + ": one " + BuiltInRegistries.ITEM.getKey(packed) + " made");
			for (ItemStack s : p.getInventory().items) {
				h.assertFalse(s.getItem() instanceof IronManArmorItem, form.suitId() + ": no loose pieces left");
			}
			ItemStack c = find(p, packed);
			h.assertTrue(SuitcaseContents.nonEmpty(c).size() == 4, form.suitId() + ": it holds all four");
			if (form.rest() == PortableForm.Place.BACK) {
				h.assertFalse(p.getInventory().getSelected().is(packed), form.suitId() + ": a pack goes on the back, not in the hand");
			}
			p.discard();
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void packingFillsAnEmptiedPackedItem(GameTestHelper h) {
		for (PortableForm form : PortableForms.all()) {
			ServerPlayer p = player(h);
			Item packed = form.packedItem().get();
			ItemStack empty = new ItemStack(packed);
			SuitcaseContents.write(empty, NonNullList.withSize(SuitcaseContents.SLOTS, ItemStack.EMPTY));
			p.getInventory().items.set(20, empty);
			loose(p, form.suitId());
			ItemStack worn = p.getInventory().items.get(3);
			float half = IronManEnergy.maxIntegrity(form.suitId()) * 0.5f;
			IronManEnergy.stampStack(worn, IronManEnergy.capacity(form.suitId()), half);
			h.assertTrue(IronManArmorItem.packLoose(p, form.suitId()), form.suitId() + ": packs");
			h.assertTrue(count(p, packed) == 1, form.suitId() + ": the emptied one is filled, no second one made");
			ItemStack c = p.getInventory().items.get(20);
			h.assertTrue(c.is(packed) && SuitcaseContents.nonEmpty(c).size() == 4, form.suitId() + ": the emptied one holds the suit");
			h.assertTrue(Math.abs(IronManEnergy.stackIntegrity(c, form.suitId()) - half) < 1f,
					form.suitId() + ": with the most worn piece's integrity");
			p.discard();
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyPackedItemHasAnEmptyRecipe(GameTestHelper h) {
		for (PortableForm form : PortableForms.all()) {
			Item packed = form.packedItem().get();
			String path = BuiltInRegistries.ITEM.getKey(packed).getPath();
			Recipe<?> r = h.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(path)).<Recipe<?>>map(x -> x.value()).orElse(null);
			h.assertTrue(r != null, path + " has a crafting recipe");
			ItemStack out = r.getResultItem(h.getLevel().registryAccess()).copy();
			h.assertTrue(out.is(packed) && out.getCount() == 1, path + ": makes one");
			h.assertFalse(SuitcaseContents.isLegacyEmpty(out), path + ": crafted EMPTY, never a never-used one (a free suit)");
			h.assertTrue(SuitcaseContents.isEmpty(out), path + ": holds nothing");
			// a crafted one alone never deploys a suit
			ServerPlayer p = player(h);
			p.getInventory().items.set(0, out);
			p.getInventory().selected = 0;
			h.assertFalse(PortableDeploy.deployFromC(p), path + ": an empty one deploys nothing");
			h.assertFalse(IronManSuitUpManager.inTransition(p), path + ": no sequence started");
			p.discard();
		}
		h.succeed();
	}
}
