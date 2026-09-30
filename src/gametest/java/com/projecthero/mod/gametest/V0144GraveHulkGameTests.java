package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.grave.TrophyHeadBlockEntity;
import com.projecthero.mod.grave.TrophyHeadWallBlock;
import com.projecthero.mod.grave.TrophyHeads;
import com.projecthero.mod.grave.item.BossTrophyItem;
import com.projecthero.mod.grave.item.GraveComponents;
import com.projecthero.mod.grave.item.GraveItems;
import com.projecthero.mod.grave.item.TrophyRecord;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.14.4: the boss trophy heads (worn in the head slot, placed on floor / wall keeping their power and kill record,
 * the undead-detection perk) and a rampaging Hulk's squad being able to fight back.
 */
public class V0144GraveHulkGameTests implements FabricGameTest {
	private static ItemStack champion(String power) {
		ItemStack stack = BossTrophyItem.of(GraveItems.FINAL_BOSS_TROPHY, power);
		stack.set(GraveComponents.TROPHY_RECORD, new TrophyRecord("Steve", 42L));
		return stack;
	}

	// ---------------------------------------------------------------- trophy heads

	@GameTest(template = EMPTY_STRUCTURE)
	public void trophyHeadsGoInTheHeadSlot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArmorStand stand = new ArmorStand(level, 0, 0, 0);
		for (ItemStack stack : List.of(new ItemStack(GraveItems.BOSS_TROPHY), new ItemStack(GraveItems.FINAL_BOSS_TROPHY))) {
			Equipable equipable = Equipable.get(stack);
			helper.assertTrue(equipable != null && equipable.getEquipmentSlot() == EquipmentSlot.HEAD,
					stack.getItem() + " must be head-slot equipment");
			helper.assertTrue(stand.getEquipmentSlotForItem(stack) == EquipmentSlot.HEAD,
					"an armour stand / mob must wear " + stack.getItem() + " on its head");
		}
		helper.assertTrue(((BossTrophyItem) GraveItems.FINAL_BOSS_TROPHY).isFinalBoss()
				&& !((BossTrophyItem) GraveItems.BOSS_TROPHY).isFinalBoss(), "which head is the Champion's");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aPlacedTrophyKeepsItsPowerAndRecord(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String power = BossPowers.eligibleKeys().get(0);
		ItemStack stack = champion(power);
		for (Block block : List.of(GraveItems.GRAVE_CHAMPION_HEAD, GraveItems.GRAVE_CHAMPION_WALL_HEAD)) {
			BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
			BlockState state = block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);
			level.setBlockAndUpdate(pos, state);
			helper.assertTrue(level.getBlockEntity(pos) instanceof TrophyHeadBlockEntity, block + " must have a block entity");
			TrophyHeadBlockEntity be = (TrophyHeadBlockEntity) level.getBlockEntity(pos);
			be.applyComponentsFromItemStack(stack); // what BlockItem#place does
			helper.assertTrue(power.equals(be.powerKey()), "the placed head remembers the power");
			helper.assertTrue(be.record() != null && "Steve".equals(be.record().slayer()) && be.record().day() == 42L,
					"the placed head remembers the kill record");
			helper.assertTrue(be.glowColor() == TrophyHeads.glowColor(power, true), "glow follows the power's family");

			// breaking it gives back the same trophy (the loot table's copy_components)
			List<ItemStack> drops = Block.getDrops(state, level, pos, be);
			helper.assertTrue(drops.size() == 1 && drops.get(0).is(GraveItems.FINAL_BOSS_TROPHY),
					block + " must drop the Grave Champion Head, got " + drops);
			ItemStack drop = drops.get(0);
			helper.assertTrue(power.equals(drop.get(GraveComponents.POWER_KEY)), "the dropped head keeps its power");
			helper.assertTrue(stack.get(GraveComponents.TROPHY_RECORD).equals(drop.get(GraveComponents.TROPHY_RECORD)),
					"the dropped head keeps its record");
			helper.assertTrue(drop.getHoverName().getString().equals(stack.getHoverName().getString()),
					"the dropped head is still named for its power");
			level.removeBlock(pos, false);
		}
		helper.assertTrue(GraveItems.GRAVE_CHAMPION_WALL_HEAD instanceof TrophyHeadWallBlock, "wall twin exists");
		helper.assertTrue(TrophyHeads.recordLine(new TrophyRecord("Alex", 7L)).getString().contains("Alex"),
				"the tooltip line names the slayer");
		helper.assertTrue(TrophyRecord.dayOf(0L) == 1L && TrophyRecord.dayOf(24000L * 3 + 5) == 4L, "day counting");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void trophyHeadsHideYouFromTheUndead(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ArmorStand wearer = new ArmorStand(level, 0, 0, 0);
		var zombie = EntityType.ZOMBIE.create(level);
		var skeleton = EntityType.SKELETON.create(level);
		var spider = EntityType.SPIDER.create(level);

		helper.assertTrue(wearer.getVisibilityPercent(zombie) == 1.0, "bare-headed: fully visible");

		wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GraveItems.BOSS_TROPHY));
		helper.assertTrue(wearer.getVisibilityPercent(zombie) == 0.5, "Empowered Zombie Head: zombies at half range");
		helper.assertTrue(wearer.getVisibilityPercent(skeleton) == 1.0, "Empowered Zombie Head: skeletons unaffected");

		wearer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(GraveItems.FINAL_BOSS_TROPHY));
		helper.assertTrue(wearer.getVisibilityPercent(zombie) == 0.5, "Grave Champion Head: zombies at half range");
		helper.assertTrue(wearer.getVisibilityPercent(skeleton) == 0.5, "Grave Champion Head: skeletons at half range");
		helper.assertTrue(wearer.getVisibilityPercent(spider) == 1.0, "Grave Champion Head: the living are unaffected");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Hulk

	@GameTest(template = EMPTY_STRUCTURE)
	public void aRampagingHulksSquadCanFightBack(GameTestHelper helper) {
		ServerPlayer hulk = helper.makeMockServerPlayerInLevel();
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		ServerPlayer other = helper.makeMockServerPlayerInLevel();
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("v0144test" + hulk.getUUID().toString().substring(0, 6), hulk.getUUID());
		squads.addMember(squad, mate.getUUID());
		squads.addMember(squad, other.getUUID());
		try {
			helper.assertTrue(Squads.shields(mate, hulk) && Squads.areAllies(mate, hulk), "normally the Hulk is protected");
			helper.assertTrue(Squads.isFriendlyFire(hulk, hulk.damageSources().playerAttack(mate)),
					"normally a squad-mate's hit on the Hulk is friendly fire");

			HulkState s = hulk.getAttachedOrCreate(ModAttachments.HULK_STATE).copy();
			s.hasPower = true;
			s.hulk = true;
			s.combat.rampageUntil = helper.getLevel().getGameTime() + 200;
			hulk.setAttached(ModAttachments.HULK_STATE, s);

			// both directions open up between the Hulk and his squad...
			helper.assertFalse(Squads.shields(hulk, mate), "the rampaging Hulk hits his squad-mates");
			helper.assertFalse(Squads.shields(mate, hulk), "his squad-mates can hit him back");
			helper.assertFalse(Squads.isFriendlyFire(hulk, hulk.damageSources().playerAttack(mate)),
					"a squad-mate's hit on the rampaging Hulk is not vetoed");
			helper.assertFalse(Squads.areAllies(mate, hulk) || Squads.areAllies(hulk, mate),
					"ally-skipping abilities no longer skip the rampaging Hulk");
			// ...but nobody else loses their squad
			helper.assertTrue(Squads.shields(mate, other) && Squads.areAllies(mate, other),
					"the rest of the squad still protects each other");
			helper.assertTrue(Squads.isFriendlyFire(other, other.damageSources().playerAttack(mate)),
					"squad-mate on squad-mate is still friendly fire");

			// the rampage ends: back to normal
			HulkState calm = hulk.getAttached(ModAttachments.HULK_STATE).copy();
			calm.combat.rampageUntil = 0L;
			hulk.setAttached(ModAttachments.HULK_STATE, calm);
			helper.assertTrue(Squads.shields(mate, hulk) && Squads.areAllies(hulk, mate), "calm again: protected again");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}
}
