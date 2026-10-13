package com.projecthero.mod.gametest;

import com.mojang.serialization.JsonOps;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.IronManBlocks;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.item.SuitcaseContents;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** v0.15.6: numbered Iron Man item ids (+ old ids still load), repulsor cooldowns, the Mark 5 Suitcase tab. */
public class IronManV0156GameTests implements FabricGameTest {
	private static final ArmorItem.Type[] TYPES = {
			ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };

	@GameTest(template = EMPTY_STRUCTURE)
	public void armourItemIdsUseTheMarkNumber(GameTestHelper h) {
		String[][] ids = { { "mark_iii", "mark_3" }, { "mark_v", "mark_5" }, { "mark_vii", "mark_7" }, { "mark_1", "mark_1" } };
		for (String[] pair : ids) {
			for (ArmorItem.Type t : TYPES) {
				var item = IronManItems.armor(pair[0], t);
				String want = "iron_man_" + pair[1] + "_" + t.getName();
				h.assertTrue(BuiltInRegistries.ITEM.getKey(item).getPath().equals(want), pair[0] + " " + t.getName() + " is " + want
						+ ", got " + BuiltInRegistries.ITEM.getKey(item));
			}
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void oldRomanNumeralIdsStillLoad(GameTestHelper h) {
		var legs = IronManItems.armor("mark_vii", ArmorItem.Type.LEGGINGS);
		h.assertTrue(BuiltInRegistries.ITEM.get(ProjectHeroMod.id("iron_man_mark_vii_leggings")) == legs, "old id -> the Mark 7 leggings");
		h.assertTrue(BuiltInRegistries.ITEM.get(ProjectHeroMod.id("iron_man_mark_iii_helmet")) == IronManItems.armor("mark_iii",
				ArmorItem.Type.HELMET), "old Mark III id");
		h.assertTrue(BuiltInRegistries.ITEM.get(ProjectHeroMod.id("iron_man_mark_v_boots")) == IronManItems.armor("mark_v",
				ArmorItem.Type.BOOTS), "old Mark V id");
		// a stack saved before the rename (old id in its data) decodes as the renamed item
		var json = com.google.gson.JsonParser.parseString("{\"id\":\"projecthero:iron_man_mark_vii_leggings\",\"count\":1}");
		ItemStack old = ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
				.result().orElse(ItemStack.EMPTY);
		h.assertTrue(old.is(legs), "a saved old-id stack loads as the Mark 7 leggings, got " + old);
		h.assertTrue(BuiltInRegistries.ITEM.get(ProjectHeroMod.id("iron_man_mark_ix_helmet")) != legs, "unknown ids are untouched");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void repulsorCooldowns(GameTestHelper h) {
		h.assertTrue(IronManSuits.MARK_4.repulsorTapCooldownTicks() == 20, "Mark 4 tap: 1 s, no more spam");
		h.assertTrue(IronManSuits.MARK_VII.repulsorTapCooldownTicks() == 10, "Mark 7 tap: 0.5 s");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitcaseTabPacksTheRackedMark5(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos pos = new BlockPos(2, 2, 2);
		h.setBlock(pos, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		net.minecraft.world.phys.Vec3 near = h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2.0, 2.5));
		p.moveTo(near.x, near.y, near.z); // v0.15.8: the case is handed over to an owner within 24 blocks
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(pos);
		be.bindTo(p.getUUID());
		h.assertFalse(be.packSuitcase(p), "an empty platform has no suitcase to give");
		for (ArmorItem.Type t : TYPES) {
			be.store(new ItemStack(IronManItems.armor("mark_v", t)));
		}
		h.assertTrue(be.packSuitcase(p), "a racked Mark 5 packs into its case");
		// v0.15.8: it folds itself up first -- locked meanwhile, the case handed over at the end
		h.assertTrue(be.packing() && be.storedSuitId() == null && be.removeItem(1, 1).isEmpty()
				&& !be.canPlaceItem(0, new ItemStack(IronManItems.armor("mark_v", ArmorItem.Type.HELMET))), "the rack is locked while it folds");
		h.assertFalse(be.packSuitcase(p), "and cannot be packed twice");
		be.finishPack(h.getLevel());
		h.assertTrue(be.isEmptyPlatform() && !be.packing(), "the rack is empty afterwards");
		ItemStack found = ItemStack.EMPTY;
		for (ItemStack s : p.getInventory().items) {
			if (s.is(IronManItems.MARK_V_SUITCASE)) {
				found = s;
			}
		}
		h.assertTrue(!found.isEmpty(), "the suitcase is in the pack");
		h.assertTrue(SuitcaseContents.nonEmpty(found).size() == 4, "with all four pieces inside");
		// and it unfolds back onto the rack: nothing lost, nothing duplicated
		h.assertTrue(be.storeSuitcase(p, found) && be.pieceMask() == 15, "the case unfolds onto the rack again");
		for (int i = 0; i < 4; i++) {
			be.setItem(i, ItemStack.EMPTY);
		}
		be.store(new ItemStack(IronManItems.armor("mark_vii", ArmorItem.Type.HELMET)));
		h.assertFalse(be.packSuitcase(p), "only a Mark 5 packs into a suitcase");
		h.getLevel().getServer().getPlayerList().remove(p);
		h.succeed();
	}

	/** v0.15.8: the fold plays out on the platform tick and then hands the case over. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void suitcaseFoldFinishesOnItsOwn(GameTestHelper h) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		BlockPos pos = new BlockPos(2, 2, 2);
		h.setBlock(pos, IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		net.minecraft.world.phys.Vec3 near = h.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 2.0, 2.5));
		p.moveTo(near.x, near.y, near.z); // v0.15.8: the case is handed over to an owner within 24 blocks
		IronManSuitPlatformBlockEntity be = (IronManSuitPlatformBlockEntity) h.getBlockEntity(pos);
		be.bindTo(p.getUUID());
		for (ArmorItem.Type t : TYPES) {
			be.store(new ItemStack(IronManItems.armor("mark_v", t)));
		}
		h.assertTrue(be.packSuitcase(p), "the fold starts");
		h.runAfterDelay(IronManSuitPlatformBlockEntity.PACK_TICKS / 2, () -> h.assertTrue(be.packing() && be.pieceMask() == 15,
				"halfway: still folding, every piece still racked"));
		h.runAfterDelay(IronManSuitPlatformBlockEntity.PACK_TICKS + 5, () -> {
			h.assertTrue(!be.packing() && be.isEmptyPlatform(), "done: the rack is empty");
			h.assertTrue(p.getInventory().countItem(IronManItems.MARK_V_SUITCASE) == 1, "and the case is in the pack");
			h.getLevel().getServer().getPlayerList().remove(p);
			h.succeed();
		});
	}
}
