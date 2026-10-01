package com.projecthero.mod.gametest;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.flash.FlashSuit;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;
import com.projecthero.mod.horde.HordeBlock;
import com.projecthero.mod.horde.HordeBlocks;
import com.projecthero.mod.horde.HordeKind;
import com.projecthero.mod.horde.HordeRaid;
import com.projecthero.mod.horde.HordeRewards;
import com.projecthero.mod.horde.Hordes;
import com.projecthero.mod.horde.entity.BoneTyrant;
import com.projecthero.mod.horde.entity.BroodQueen;
import com.projecthero.mod.horde.entity.HordeEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** v0.14.12: the Horde blocks, the Super Speed "stands alone" rule and Overdrive's toll on ordinary armour. */
public class HordeGameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE)
	public void hordeBlockRecipesFillTheGrid(GameTestHelper helper) {
		String[][] recipes = { { "zombie_horde", "minecraft:rotten_flesh" }, { "skeleton_horde", "minecraft:bone_block" },
				{ "spider_horde", "minecraft:spider_eye" } };
		for (String[] r : recipes) {
			var holder = helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(r[0]));
			helper.assertTrue(holder.isPresent(), "a recipe for " + r[0]);
			var recipe = holder.get().value();
			helper.assertTrue(recipe.getIngredients().size() == 9, r[0] + " fills the whole grid");
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(r[1]));
			for (var ingredient : recipe.getIngredients()) {
				helper.assertTrue(ingredient.test(new ItemStack(item)), r[0] + " is made of " + r[1]);
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "horde_start", timeoutTicks = 60)
	public void wakingAHordeBlockStartsItsRaid(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.getLevel().setBlock(pos, HordeBlocks.SKELETON_HORDE.defaultBlockState(), 3);
		helper.assertTrue(Hordes.start(helper.getLevel(), pos, HordeKind.SKELETON), "the horde wakes");
		helper.assertTrue(helper.getLevel().getBlockState(pos).getValue(HordeBlock.ACTIVE), "the block glows while it runs");
		helper.assertTrue(EventManager.at(helper.getLevel(), pos) instanceof HordeRaid raid && raid.kind() == HordeKind.SKELETON,
				"a Skeleton Horde is running on it");
		helper.assertFalse(Hordes.start(helper.getLevel(), pos, HordeKind.SKELETON), "only one of a kind at a time here");
		// clean up: stop it
		HordeRaid raid = (HordeRaid) EventManager.at(helper.getLevel(), pos);
		raid.abort(helper.getLevel());
		com.projecthero.mod.event.EventSavedData.get(helper.getLevel()).remove(raid.id());
		helper.assertFalse(helper.getLevel().getBlockState(pos).getValue(HordeBlock.ACTIVE), "and it goes back to sleep when stopped");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wavesGrowAndScaleWithFighters(GameTestHelper helper) {
		helper.assertTrue(HordeRaid.waveSize(1, 1) == 18 && HordeRaid.waveSize(8, 1) == 60, "18 to 60 alone");
		int total = 0;
		for (int w = 1; w <= HordeRaid.WAVES; w++) {
			total += HordeRaid.waveSize(w, 1);
		}
		helper.assertTrue(total >= 300, "several hundred mobs in a horde, got " + total);
		helper.assertTrue(HordeRaid.waveSize(4, 3) == 2 * HordeRaid.waveSize(4, 1), "half again per extra fighter");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBossesAreGiantsWhoScaleWithFighters(GameTestHelper helper) {
		BoneTyrant tyrant = HordeEntityTypes.BONE_TYRANT.create(helper.getLevel());
		BroodQueen queen = HordeEntityTypes.BROOD_QUEEN.create(helper.getLevel());
		helper.assertTrue(tyrant.getAttributeValue(Attributes.SCALE) == BoneTyrant.SCALE, "the Tyrant is three times the size");
		helper.assertTrue(queen.getAttributeValue(Attributes.SCALE) == BroodQueen.SCALE, "the Queen is three and a half");
		tyrant.configure(1);
		float solo = tyrant.getMaxHealth();
		tyrant.configure(3);
		helper.assertTrue(tyrant.getMaxHealth() > solo, "more fighters, more health");
		queen.configure(20);
		helper.assertTrue(queen.getMaxHealth() <= 1024f, "never past vanilla's health cap");
		helper.assertTrue(HordeEntityTypes.HORDE_SPIDER.create(helper.getLevel()).getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.3,
				"horde spiders are faster than vanilla's");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void harderHordesPayBetter(GameTestHelper helper) {
		int[] minDiamonds = new int[3];
		for (int k = 0; k < 3; k++) {
			HordeKind kind = HordeKind.values()[k];
			int least = Integer.MAX_VALUE;
			for (int i = 0; i < 20; i++) {
				int d = 0;
				for (ItemStack s : HordeRewards.roll(helper.getLevel(), kind, 1)) {
					if (s.is(Items.DIAMOND)) {
						d += s.getCount();
					}
				}
				least = Math.min(least, d);
			}
			minDiamonds[k] = least;
		}
		helper.assertTrue(minDiamonds[0] >= 3 && minDiamonds[1] >= 5 && minDiamonds[2] >= 8,
				"zombie >= 3, skeleton >= 5, spider >= 8 diamonds: " + java.util.Arrays.toString(minDiamonds));
		boolean notch = HordeRewards.roll(helper.getLevel(), HordeKind.SPIDER, 1).stream().anyMatch(s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
		helper.assertTrue(notch, "the spider chest always has an enchanted golden apple");
		helper.assertTrue(HordeRewards.roll(helper.getLevel(), HordeKind.ZOMBIE, 1).size() <= 27, "fits a chest");
		helper.assertTrue(HordeRewards.roll(helper.getLevel(), HordeKind.SPIDER, 4).size() <= 27, "fits a chest with four winners");
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		HordeRewards.placeChest(helper.getLevel(), pos, HordeKind.ZOMBIE, 1);
		helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity chest && !chest.isEmpty(),
				"a beaten horde block becomes a full chest");
		helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Super Speed stands alone

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void superSpeedNeverStacksWithAnotherPower(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		var strength = Powers.byKey("power_01_super_strength");
		var speed = Powers.byKey(SuperSpeedHandlers.KEY);
		var laser = Powers.byKey("power_02_laser_vision");
		helper.assertTrue(PowerGrants.grantExperimental(p, strength), "a normal mutation");
		helper.assertTrue(PowerGrants.grantExperimental(p, speed), "then Super Speed");
		helper.assertTrue(ExperimentalPowers.owns(p, speed) && !ExperimentalPowers.owns(p, strength), "Super Speed burned the other away");
		helper.assertTrue(PowerGrants.grantExperimental(p, laser), "then another mutation");
		helper.assertTrue(ExperimentalPowers.owns(p, laser) && !ExperimentalPowers.owns(p, speed), "which burns Super Speed away");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void overdriveWearsOrdinaryArmourButNotTheFlashSuit(GameTestHelper helper) {
		ServerPlayer p = player(helper);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		p.setItemSlot(EquipmentSlot.LEGS, new ItemStack(FlashSuit.LEGGINGS));
		SuperSpeedHandlers.wearArmour(p);
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == SuperSpeedHandlers.OVERDRIVE_ARMOUR_WEAR,
				"iron loses 10 a second, got " + p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue());
		helper.assertTrue(p.getItemBySlot(EquipmentSlot.LEGS).getDamageValue() == 0, "the Flash Suit takes nothing");
		helper.succeed();
	}
}
