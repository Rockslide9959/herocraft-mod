package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.item.HeroPackItems;
import com.projecthero.mod.hero.item.RandomPowerSerumItem;
import com.projecthero.mod.hero.item.ResearchNoteItem;
import com.projecthero.mod.hero.item.ResearchNoteSerumRecipe;
import com.projecthero.mod.hero.mutation.ModSerums;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.p02.LaserCooking;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.syndicate.SyndicateRewards;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.11 misc batch: research notes craft into serums, squadmates' pets are safe, the Kingpin's stash holds redstone,
 * and laser kills drop cooked food. Everything stays inside the 8x8x8 cage; mobs are NoAI.
 */
public class V01511MiscGameTests implements FabricGameTest {
	// ------------------------------------------------------------------ helpers

	private static ServerPlayer player(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(v.x, v.y, v.z, 0f, 0f);
		return p;
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double z) {
		T m = type.create(helper.getLevel());
		Vec3 v = helper.absoluteVec(new Vec3(x, 2.0, z));
		m.moveTo(v.x, v.y, v.z, 0f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static Squad squad(GameTestHelper helper, UUID leader, UUID... mates) {
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("v1511" + leader.toString().substring(0, 8), leader);
		for (UUID m : mates) {
			squads.addMember(squad, m);
		}
		return squad;
	}

	private static ItemStack note(Power power) {
		return power == null ? new ItemStack(HeroPackItems.RESEARCH_NOTE) : ResearchNoteItem.forPower(power, 1);
	}

	/** A 3x3 grid: the given stacks first, the rest empty. */
	private static List<ItemStack> grid(ItemStack... stacks) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : stacks) {
			out.add(s);
		}
		while (out.size() < 9) {
			out.add(ItemStack.EMPTY);
		}
		return out;
	}

	private static ItemStack[] notes(Power power, int n) {
		ItemStack[] out = new ItemStack[n];
		for (int i = 0; i < n; i++) {
			out[i] = note(power);
		}
		return out;
	}

	private static ItemStack[] plus(ItemStack[] a, ItemStack... b) {
		ItemStack[] out = new ItemStack[a.length + b.length];
		System.arraycopy(a, 0, out, 0, a.length);
		System.arraycopy(b, 0, out, a.length, b.length);
		return out;
	}

	private static Power disabledPower() {
		for (Power p : Powers.all()) {
			if (!Powers.isMutation(p) && !Powers.isHeroTier(p)) {
				return p;
			}
		}
		return null;
	}

	private static int dropped(GameTestHelper helper, Vec3 at, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0))) {
			if (e.getItem().is(item)) {
				n += e.getItem().getCount();
			}
		}
		return n;
	}

	private static void clearDrops(GameTestHelper helper, Vec3 at) {
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4.0))) {
			e.discard();
		}
	}

	// ------------------------------------------------------------------ 1: research notes -> serum

	@GameTest(template = EMPTY_STRUCTURE)
	public void fourNotesAndABottleMakeThatPowersSerum(GameTestHelper helper) {
		Power laser = Powers.byKey(LaserVisionHandlers.KEY);
		ItemStack out = ResearchNoteSerumRecipe.result(grid(plus(notes(laser, 4), new ItemStack(Items.GLASS_BOTTLE))));
		helper.assertTrue(out.is(Items.POTION), "4 Laser Vision notes + a bottle make a potion, got " + out);
		PotionContents contents = out.get(DataComponents.POTION_CONTENTS);
		helper.assertTrue(contents != null && contents.potion().isPresent()
				&& contents.potion().get().value() == ModSerums.serum(laser).value(), "and it is Laser Vision's serum");

		// the real crafting table finds it too (the JSON recipe is loaded with its serializer)
		CraftingInput input = CraftingInput.of(3, 3, grid(new ItemStack(Items.GLASS_BOTTLE), note(laser), ItemStack.EMPTY,
				note(laser), ItemStack.EMPTY, note(laser), ItemStack.EMPTY, note(laser)));
		var found = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
		helper.assertTrue(found.isPresent(), "the crafting grid matches the notes in any slots");
		ItemStack crafted = found.get().value().assemble(input, helper.getLevel().registryAccess());
		helper.assertTrue(ItemStack.isSameItemSameComponents(crafted, out), "and crafts the same serum");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void blankNotesMakeAMutagenicSerum(GameTestHelper helper) {
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		ItemStack out = ResearchNoteSerumRecipe.result(grid(plus(notes(null, 4), bottle.copy())));
		helper.assertTrue(out.is(RandomPowerSerumItem.EXPERIMENTAL_SERUM), "4 blank notes + a bottle = Mutagenic Serum, got " + out);
		Power off = disabledPower();
		if (off != null) {
			ItemStack mixed = ResearchNoteSerumRecipe.result(grid(note(off), note(off), note(null), note(null), bottle.copy()));
			helper.assertTrue(mixed.is(RandomPowerSerumItem.EXPERIMENTAL_SERUM),
					"notes for a disabled power (" + off.key() + ") count as blank");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wrongNoteGridsDoNotCraft(GameTestHelper helper) {
		Power laser = Powers.byKey(LaserVisionHandlers.KEY);
		Power strength = Powers.byKey("power_01_super_strength");
		ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
		helper.assertTrue(ResearchNoteSerumRecipe.result(grid(plus(notes(laser, 3), bottle.copy()))).isEmpty(), "3 notes are not enough");
		helper.assertTrue(ResearchNoteSerumRecipe.result(grid(plus(notes(laser, 5), bottle.copy()))).isEmpty(), "5 notes are too many");
		helper.assertTrue(ResearchNoteSerumRecipe.result(grid(notes(laser, 4))).isEmpty(), "no bottle, no serum");
		helper.assertTrue(ResearchNoteSerumRecipe.result(grid(plus(notes(laser, 4), bottle.copy(), bottle.copy()))).isEmpty(),
				"two bottles do not match");
		helper.assertTrue(ResearchNoteSerumRecipe.result(grid(note(laser), note(laser), note(laser), note(strength), bottle.copy()))
				.isEmpty(), "notes about different powers do not mix");
		helper.assertTrue(ResearchNoteSerumRecipe.result(grid(note(laser), note(laser), note(laser), note(null), bottle.copy()))
				.isEmpty(), "a blank note does not complete a written set");
		helper.assertTrue(ResearchNoteSerumRecipe.result(grid(plus(notes(laser, 4), bottle.copy(), new ItemStack(Items.DIRT))))
				.isEmpty(), "anything else in the grid spoils it");
		helper.succeed();
	}

	// ------------------------------------------------------------------ 2: squadmates' pets

	@GameTest(template = EMPTY_STRUCTURE)
	public void squadmatesPetsTakeNoDamageFromYou(GameTestHelper helper) {
		ServerPlayer you = player(helper, 1.5, 1.5);
		ServerPlayer mate = player(helper, 5.5, 1.5);
		ServerPlayer stranger = player(helper, 1.5, 5.5);
		squad(helper, you.getUUID(), mate.getUUID());
		Wolf wolf = mob(helper, EntityType.WOLF, 3.5, 3.5);
		wolf.setTame(true, false);
		wolf.setOwnerUUID(mate.getUUID());
		var sources = helper.getLevel().damageSources();

		float hp = wolf.getHealth();
		helper.assertFalse(wolf.hurt(sources.playerAttack(you), 4.0f), "your sword does not land on a squadmate's wolf");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(you);
		helper.assertFalse(wolf.hurt(sources.arrow(arrow, you), 4.0f), "nor your arrow");
		helper.assertFalse(AbilityHelpers.hurt(you, wolf, AbilityHelpers.fire(you), 4.0f), "nor your powers");
		helper.assertTrue(wolf.getHealth() == hp, "the wolf is unhurt");
		helper.assertFalse(HeroTargets.canHarm(you, wolf), "aimed powers pass it by");
		helper.assertFalse(HeroTargets.isHostile(you, wolf), "auto-aim never picks it");

		wolf.invulnerableTime = 0;
		helper.assertTrue(wolf.hurt(sources.playerAttack(stranger), 1.0f), "someone outside the squad can still hit it");
		helper.assertTrue(wolf.getHealth() < hp, "and it is hurt");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKindOfPetIsCoveredEvenWithTheOwnerOffline(GameTestHelper helper) {
		ServerPlayer you = player(helper, 1.5, 1.5);
		UUID offlineMate = UUID.randomUUID();
		squad(helper, you.getUUID(), offlineMate);
		Cat cat = mob(helper, EntityType.CAT, 3.5, 2.5);
		cat.setTame(true, false);
		cat.setOwnerUUID(offlineMate);
		Horse horse = mob(helper, EntityType.HORSE, 5.0, 5.0);
		horse.setTamed(true);
		horse.setOwnerUUID(offlineMate);
		Wolf wild = mob(helper, EntityType.WOLF, 2.5, 5.0);
		var sources = helper.getLevel().damageSources();

		helper.assertTrue(Squads.protectsPet(you, offlineMate), "an offline squadmate's pets are still protected");
		helper.assertFalse(cat.hurt(sources.playerAttack(you), 2.0f), "the cat");
		helper.assertFalse(horse.hurt(sources.playerAttack(you), 2.0f), "the horse");
		helper.assertTrue(wild.hurt(sources.playerAttack(you), 1.0f), "a wild wolf is not anyone's pet");
		helper.assertFalse(HeroTargets.canHarm(you, horse), "powers skip the horse too");
		helper.assertTrue(HeroTargets.isFriendlyPet(you, cat), "the shared friendly-pet check agrees");
		helper.succeed();
	}

	// ------------------------------------------------------------------ 3: Kingpin's stash

	@GameTest(template = EMPTY_STRUCTURE)
	public void kingpinsStashHoldsRedstone(GameTestHelper helper) {
		for (int party = 1; party <= 6; party++) {
			for (int seed = 0; seed < 4; seed++) {
				List<ItemStack> loot = SyndicateRewards.roll(RandomSource.create(party * 31L + seed),
						helper.getLevel().registryAccess(), party);
				int dust = 0;
				for (ItemStack s : loot) {
					if (s.is(Items.REDSTONE)) {
						dust += s.getCount();
					}
				}
				helper.assertTrue(dust >= 16, "party of " + party + ": at least 16 redstone, got " + dust);
				helper.assertTrue(loot.size() <= 27, "the haul still fits a chest (" + loot.size() + ")");
			}
		}
		helper.succeed();
	}

	// ------------------------------------------------------------------ 5: cooked by the laser

	@GameTest(template = EMPTY_STRUCTURE)
	public void laserKillsDropCookedMeat(GameTestHelper helper) {
		ServerPlayer p = player(helper, 1.5, 1.5);
		Cow cow = mob(helper, EntityType.COW, 4.0, 4.0);
		Pig pig = mob(helper, EntityType.PIG, 2.0, 5.5);
		Chicken chicken = mob(helper, EntityType.CHICKEN, 5.5, 2.0);
		Vec3 centre = helper.absoluteVec(new Vec3(4.0, 2.0, 4.0));
		clearDrops(helper, centre);

		helper.assertTrue(LaserCooking.hurt(p, cow, AbilityHelpers.fire(p), 100f), "the laser kills the cow");
		helper.assertTrue(LaserCooking.hurt(p, pig, AbilityHelpers.fire(p), 100f), "and the pig");
		helper.assertTrue(LaserCooking.hurt(p, chicken, AbilityHelpers.fire(p), 100f), "and the chicken");
		helper.assertTrue(dropped(helper, centre, Items.COOKED_BEEF) > 0, "the cow drops steak");
		helper.assertTrue(dropped(helper, centre, Items.COOKED_PORKCHOP) > 0, "the pig drops cooked porkchops");
		helper.assertTrue(dropped(helper, centre, Items.COOKED_CHICKEN) > 0, "the chicken drops cooked chicken");
		helper.assertTrue(dropped(helper, centre, Items.BEEF) + dropped(helper, centre, Items.PORKCHOP)
				+ dropped(helper, centre, Items.CHICKEN) == 0, "nothing raw");
		helper.assertFalse(LaserCooking.active(), "the laser window closes after the hit");

		// the control: the same kill outside a laser is raw, so the cooking really is the laser's doing
		clearDrops(helper, centre);
		Cow control = mob(helper, EntityType.COW, 4.0, 4.0);
		AbilityHelpers.hurt(p, control, helper.getLevel().damageSources().playerAttack(p), 100f);
		helper.assertTrue(dropped(helper, centre, Items.BEEF) > 0 && dropped(helper, centre, Items.COOKED_BEEF) == 0,
				"an ordinary kill drops raw beef");
		clearDrops(helper, centre);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void igniteKillDropsCookedBeefWithoutLightingTheGround(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 3, 5))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(1, -1, 5))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 20.0f); // facing +Z, a little down at the cow
		p.setYHeadRot(0f);
		p.setOnGround(true);
		Power laser = Powers.byKey(LaserVisionHandlers.KEY);
		ExperimentalPowers.grant(p, laser);
		ExperimentalPowers.setActive(p, laser);
		Cow cow = mob(helper, EntityType.COW, 2.5, 3.5);
		cow.setHealth(1.0f);
		Vec3 centre = helper.absoluteVec(new Vec3(2.5, 2.0, 3.5));
		clearDrops(helper, centre);

		AbilityRouter.handleInput(p, 5, true); // V: Ignite -- 2 fire damage
		helper.assertTrue(cow.isDeadOrDying(), "Ignite kills the 1-health cow");
		helper.assertTrue(dropped(helper, centre, Items.COOKED_BEEF) > 0 && dropped(helper, centre, Items.BEEF) == 0,
				"and it drops steak, not raw beef");
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(1, 2, 5))) {
			helper.assertFalse(helper.getLevel().getBlockState(pos).is(Blocks.FIRE), "no fire block at " + pos);
		}
		clearDrops(helper, centre);
		helper.succeed();
	}
}
