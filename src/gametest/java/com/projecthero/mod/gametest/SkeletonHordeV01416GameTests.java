package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.horde.HordeBlocks;
import com.projecthero.mod.horde.HordeKind;
import com.projecthero.mod.horde.HordeRaid;
import com.projecthero.mod.horde.HordeRewards;
import com.projecthero.mod.horde.Hordes;
import com.projecthero.mod.horde.entity.BoneTyrant;
import com.projecthero.mod.horde.entity.BoneTyrantCombat;
import com.projecthero.mod.horde.entity.BoneTyrantCombat.Attack;
import com.projecthero.mod.horde.entity.HordeEntityTypes;
import com.projecthero.mod.horde.entity.skeleton.BlastArrow;
import com.projecthero.mod.horde.entity.skeleton.BlightArcher;
import com.projecthero.mod.horde.entity.skeleton.BoneBomber;
import com.projecthero.mod.horde.entity.skeleton.BoneBrute;
import com.projecthero.mod.horde.entity.skeleton.BoneKnight;
import com.projecthero.mod.horde.entity.skeleton.BoneRunner;
import com.projecthero.mod.horde.entity.skeleton.HordeSkeleton;
import com.projecthero.mod.horde.entity.skeleton.Necromancer;
import com.projecthero.mod.horde.entity.skeleton.SkeletonHordeEntityTypes;
import com.projecthero.mod.horde.entity.skeleton.SkeletonHordeRoster;
import com.projecthero.mod.titan.entity.TitanEntity;
import com.projecthero.mod.titan.entity.TitanEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16 Skeleton Horde: the scattered reward chest, the six horde skeletons and their traits, and the rebuilt Bone
 * Tyrant -- stronger than the Titan, every attack running to completion, the phase roar, the attack cycle, and a real
 * Skeleton Horde (fast-forwarded) ending in victory when he falls.
 */
public class SkeletonHordeV01416GameTests implements FabricGameTest {

	private static void hard(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // monsters need a non-peaceful world (NORMAL, as the rest of the suite leaves it)
	}

	private static Map<Item, Integer> totals(Iterable<ItemStack> stacks) {
		Map<Item, Integer> out = new HashMap<>();
		for (ItemStack s : stacks) {
			if (!s.isEmpty()) {
				out.merge(s.getItem(), s.getCount(), Integer::sum);
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- the reward chest

	@GameTest(template = EMPTY_STRUCTURE)
	public void hordeLootIsScatteredThroughTheChest(GameTestHelper helper) {
		List<ItemStack> loot = List.of(new ItemStack(Items.DIAMOND, 9), new ItemStack(Items.GOLD_INGOT, 20), new ItemStack(Items.IRON_INGOT, 64),
				new ItemStack(Items.IRON_INGOT, 16), new ItemStack(Items.EMERALD, 20), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
				new ItemStack(Items.EXPERIENCE_BOTTLE, 16));
		Map<Item, Integer> want = totals(loot);
		for (int seed = 0; seed < 10; seed++) {
			SimpleContainer box = new SimpleContainer(27);
			HordeRewards.fill(box, loot, RandomSource.create(seed));
			List<ItemStack> got = new ArrayList<>();
			int used = 0;
			int lastUsed = -1;
			for (int i = 0; i < box.getContainerSize(); i++) {
				got.add(box.getItem(i));
				if (!box.getItem(i).isEmpty()) {
					used++;
					lastUsed = i;
				}
			}
			helper.assertTrue(totals(got).equals(want), "every item survives the scatter: " + totals(got) + " vs " + want);
			helper.assertTrue(used > loot.size() && used >= 15, "stacks are split to fill the chest, " + used + " slots used");
			helper.assertTrue(lastUsed > used - 1, "the loot is scattered, not packed into the first " + used + " slots");
		}
		helper.assertTrue(loot.get(0).getCount() == 9, "the loot list itself is left alone");
		// the real thing, for every horde
		BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
		for (HordeKind kind : HordeKind.values()) {
			HordeRewards.placeChest(helper.getLevel(), pos, kind, 1);
			helper.assertTrue(helper.getLevel().getBlockEntity(pos) instanceof ChestBlockEntity, "a chest for " + kind);
			ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
			int used = 0;
			int firstEmpty = -1;
			int lastUsed = -1;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).isEmpty()) {
					if (firstEmpty < 0) {
						firstEmpty = i;
					}
				} else {
					used++;
					lastUsed = i;
				}
			}
			helper.assertTrue(used >= 14, kind + " chest is well filled, " + used + " slots");
			helper.assertTrue(firstEmpty >= 0 && firstEmpty < lastUsed, kind + " chest has gaps between its stacks");
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the six horde skeletons

	private static <T extends HordeSkeleton> T skeleton(GameTestHelper helper, EntityType<T> type, int wave, Vec3 rel) {
		ServerLevel level = helper.getLevel();
		T mob = type.create(level);
		Vec3 at = helper.absoluteVec(rel);
		mob.moveTo(at.x, at.y, at.z, 0f, 0f);
		mob.setHordeWave(wave);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		mob.setNoAi(true);
		mob.setYRot(0f);
		mob.yBodyRot = 0f;
		level.addFreshEntity(mob);
		return mob;
	}

	private static Pig dummy(GameTestHelper helper, Vec3 rel) {
		Pig pig = EntityType.PIG.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(rel);
		pig.moveTo(at.x, at.y, at.z, 0f, 0f);
		pig.setNoAi(true);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0);
		pig.setHealth(500.0f);
		helper.getLevel().addFreshEntity(pig);
		return pig;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "skeleton_horde_variants", timeoutTicks = 100)
	public void eachHordeSkeletonHasItsOwnTraits(GameTestHelper helper) {
		hard(helper);
		ServerLevel level = helper.getLevel();
		List<net.minecraft.world.entity.Entity> made = new ArrayList<>();

		BoneRunner runner = skeleton(helper, SkeletonHordeEntityTypes.BONE_RUNNER, 1, new Vec3(1.5, 2, 1.5));
		BoneRunner lateRunner = skeleton(helper, SkeletonHordeEntityTypes.BONE_RUNNER, 7, new Vec3(1.5, 2, 6.5));
		made.add(runner);
		made.add(lateRunner);
		helper.assertTrue(runner.getScale() < 0.8f && runner.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.3,
				"the Bone Runner is small and fast");
		helper.assertTrue(runner.getMainHandItem().is(Items.STONE_SWORD), "with a stone dagger");
		helper.assertTrue(lateRunner.getMaxHealth() > runner.getMaxHealth(), "later waves are tougher: " + lateRunner.getMaxHealth());

		BoneKnight knight = skeleton(helper, SkeletonHordeEntityTypes.BONE_KNIGHT, 3, new Vec3(3.5, 2, 3.5));
		made.add(knight);
		helper.assertTrue(knight.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && knight.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
				&& knight.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "the Bone Knight is in iron");
		helper.assertTrue(knight.getOffhandItem().is(Items.SHIELD) && knight.getMainHandItem().is(Items.IRON_AXE), "with an axe and a shield");
		Arrow front = new Arrow(EntityType.ARROW, level);
		front.setPos(knight.getX(), knight.getY() + 1, knight.getZ() + 3);
		Arrow back = new Arrow(EntityType.ARROW, level);
		back.setPos(knight.getX(), knight.getY() + 1, knight.getZ() - 3);
		helper.assertTrue(knight.shieldBlocks(level.damageSources().arrow(front, null)), "an arrow from the front glances off");
		helper.assertFalse(knight.shieldBlocks(level.damageSources().arrow(back, null)), "one from behind does not");
		float hp = knight.getHealth();
		knight.hurt(level.damageSources().arrow(front, null), 6.0f);
		helper.assertTrue(knight.getHealth() == hp, "and does no harm");

		BlightArcher blight = skeleton(helper, SkeletonHordeEntityTypes.BLIGHT_ARCHER, 6, new Vec3(5.5, 2, 1.5));
		made.add(blight);
		AbstractArrow blightArrow = blight.makeArrow();
		PotionContents contents = blightArrow.getPickupItemStackOrigin().get(DataComponents.POTION_CONTENTS);
		boolean wither = false;
		boolean hunger = false;
		if (contents != null) {
			for (MobEffectInstance e : contents.getAllEffects()) {
				wither |= e.is(MobEffects.WITHER) && e.getAmplifier() >= 1;
				hunger |= e.is(MobEffects.HUNGER);
			}
		}
		helper.assertTrue(wither && hunger, "Blight Archer arrows carry Wither II (wave 6+) and Hunger");

		BoneBomber bomber = skeleton(helper, SkeletonHordeEntityTypes.BONE_BOMBER, 3, new Vec3(5.5, 2, 5.5));
		made.add(bomber);
		helper.assertTrue(bomber.getItemBySlot(EquipmentSlot.HEAD).is(Items.TNT), "the Bone Bomber wears its TNT");
		AbstractArrow bomb = bomber.makeArrow();
		helper.assertTrue(bomb instanceof BlastArrow, "and shoots Blast Arrows");
		Pig target = dummy(helper, new Vec3(6.5, 2, 3.5));
		made.add(target);
		((BlastArrow) bomb).burst(target.position());
		helper.assertTrue(target.getHealth() < 500.0f, "which burst on whoever they hit");
		helper.assertTrue(knight.getHealth() == hp, "but never on the horde");

		BoneBrute brute = skeleton(helper, SkeletonHordeEntityTypes.BONE_BRUTE, 5, new Vec3(2.5, 2, 5.5));
		made.add(brute);
		helper.assertTrue(brute.getScale() >= 1.5f && brute.getMaxHealth() >= 80.0f && brute.getMainHandItem().is(Items.MACE),
				"the Bone Brute is huge, tough and swings a mace");
		Pig slammed = dummy(helper, new Vec3(3.5, 2, 6.5));
		made.add(slammed);
		brute.startSlam(level);
		helper.assertTrue(brute.isWindingUp(), "the Ground Slam winds up first");
		for (int i = 0; i < BoneBrute.SLAM_WINDUP; i++) {
			brute.tickSlam(level);
		}
		helper.assertFalse(brute.isWindingUp(), "then lands");
		helper.assertTrue(slammed.getHealth() < 500.0f, "and hurts everyone round it");

		Necromancer necro = skeleton(helper, SkeletonHordeEntityTypes.NECROMANCER, 4, new Vec3(1.5, 2, 4.5));
		made.add(necro);
		helper.assertTrue(necro.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE) && necro.getMainHandItem().is(Items.SOUL_TORCH),
				"the Necromancer is robed and carries a soul torch");
		helper.assertTrue(necro.raise(level) == 2 && necro.liveMinions(level) == 2, "it raises two of the dead");
		List<BoneRunner> raised = level.getEntitiesOfClass(BoneRunner.class, new AABB(necro.blockPosition()).inflate(4),
				r -> r != runner && r != lateRunner);
		helper.assertTrue(raised.size() == 2, "two Bone Runners, got " + raised.size());
		necro.kill();
		helper.assertTrue(raised.stream().allMatch(r -> r.isRemoved()), "they crumble when it dies");

		for (net.minecraft.world.entity.Entity e : made) {
			e.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theSkeletonWavesBringInTheVariants(GameTestHelper helper) {
		RandomSource r = RandomSource.create(7);
		Set<EntityType<?>> early = new HashSet<>();
		Set<EntityType<?>> late = new HashSet<>();
		int earlyCustom = 0;
		int lateCustom = 0;
		for (int i = 0; i < 4000; i++) {
			EntityType<? extends Mob> a = SkeletonHordeRoster.pick(r, 1);
			EntityType<? extends Mob> b = SkeletonHordeRoster.pick(r, 8);
			early.add(a);
			late.add(b);
			earlyCustom += SkeletonHordeEntityTypes.VARIANTS.contains(a) ? 1 : 0;
			lateCustom += SkeletonHordeEntityTypes.VARIANTS.contains(b) ? 1 : 0;
		}
		helper.assertTrue(late.containsAll(SkeletonHordeEntityTypes.VARIANTS), "every horde skeleton shows up by wave 8: " + late);
		helper.assertTrue(late.contains(EntityType.SKELETON) && late.contains(EntityType.WITHER_SKELETON), "alongside the old mix");
		helper.assertFalse(early.contains(SkeletonHordeEntityTypes.BONE_BRUTE) || early.contains(SkeletonHordeEntityTypes.NECROMANCER)
				|| early.contains(SkeletonHordeEntityTypes.BONE_KNIGHT) || early.contains(SkeletonHordeEntityTypes.BONE_BOMBER),
				"wave 1 is only the basics and Bone Runners: " + early);
		helper.assertTrue(lateCustom > earlyCustom * 2, "the specials grow more common with the waves: " + earlyCustom + " -> " + lateCustom);
		helper.assertTrue(SkeletonHordeEntityTypes.VARIANTS.size() == 6, "six of them, one per zombie kind");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Bone Tyrant

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBoneTyrantOutclassesTheTitan(GameTestHelper helper) {
		BoneTyrant tyrant = HordeEntityTypes.BONE_TYRANT.create(helper.getLevel());
		TitanEntity titan = TitanEntityTypes.TITAN.create(helper.getLevel());
		tyrant.configure(1);
		tyrant.refreshDimensions();
		helper.assertTrue(tyrant.getMaxHealth() > titan.getMaxHealth(), "more health than the Titan: " + tyrant.getMaxHealth() + " vs " + titan.getMaxHealth());
		helper.assertTrue(tyrant.getAttributeValue(Attributes.ARMOR) > titan.getAttributeValue(Attributes.ARMOR)
				&& tyrant.getAttributeValue(Attributes.ARMOR_TOUGHNESS) > titan.getAttributeValue(Attributes.ARMOR_TOUGHNESS), "and heavier armour");
		helper.assertTrue(tyrant.getAttributeValue(Attributes.ATTACK_DAMAGE) > titan.getAttributeValue(Attributes.ATTACK_DAMAGE), "and hits harder");
		helper.assertTrue(BoneTyrantCombat.CHARGE_DAMAGE > 34.0f, "his charge beats the Titan's");
		helper.assertTrue(tyrant.getBbHeight() > 6.0f && tyrant.getBbHeight() < 8.0f, "about seven blocks tall, got " + tyrant.getBbHeight());
		float solo = tyrant.getMaxHealth();
		tyrant.configure(4);
		helper.assertTrue(tyrant.getMaxHealth() > solo, "more fighters, more health");
		tyrant.configure(40);
		helper.assertTrue(tyrant.getMaxHealth() <= BoneTyrant.MAX_HEALTH_CAP, "but capped");
		helper.assertTrue(tyrant.fireImmune(), "fire can't touch him");
		int[] perPhase = new int[4];
		for (Attack a : Attack.values()) {
			if (a != Attack.ROAR) {
				perPhase[a.minPhase]++;
			}
		}
		helper.assertTrue(perPhase[1] + perPhase[2] + perPhase[3] >= 10 && perPhase[1] >= 5 && perPhase[2] >= 3 && perPhase[3] >= 1,
				"ten attacks over three phases: " + java.util.Arrays.toString(perPhase));
		helper.succeed();
	}

	/** A stone deck high above the test grid: his quakes and storms reach far past any 8-block test cage. */
	private static final int DECK_Y = 40;
	private static final int DECK_R = 18;

	private static BlockPos deck(GameTestHelper helper, boolean build) {
		BlockPos c = helper.absolutePos(new BlockPos(3, DECK_Y, 3));
		for (int dx = -DECK_R; dx <= DECK_R; dx++) {
			for (int dz = -DECK_R; dz <= DECK_R; dz++) {
				helper.getLevel().setBlock(c.offset(dx, -1, dz), build ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				if (!build) {
					for (int dy = 0; dy < 9; dy++) {
						helper.getLevel().setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
		return c;
	}

	private static BoneTyrant tyrantOnDeck(GameTestHelper helper, BlockPos c) {
		ServerLevel level = helper.getLevel();
		BoneTyrant tyrant = HordeEntityTypes.BONE_TYRANT.create(level);
		tyrant.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0f, 0f);
		tyrant.finalizeSpawn(level, level.getCurrentDifficultyAt(c), MobSpawnType.EVENT, null);
		tyrant.configure(1);
		tyrant.setNoAi(true); // the test drives his combat by hand
		level.addFreshEntity(tyrant);
		return tyrant;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "bone_tyrant_attacks", timeoutTicks = 100)
	public void everyTyrantAttackRunsToTheEnd(GameTestHelper helper) {
		hard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos c = deck(helper, true);
		BoneTyrant tyrant = tyrantOnDeck(helper, c);
		Pig pig = EntityType.PIG.create(level);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20000.0);
		pig.setNoAi(true);
		level.addFreshEntity(pig);
		BoneTyrantCombat combat = tyrant.combat();
		Vec3 spot = new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 4.5); // four blocks in front of him (he faces +z)
		try {
			for (Attack a : Attack.values()) {
				if (a == Attack.ROAR) {
					continue;
				}
				pig.teleportTo(spot.x, spot.y, spot.z);
				pig.setDeltaMovement(Vec3.ZERO);
				pig.setHealth(pig.getMaxHealth());
				pig.invulnerableTime = 0;
				tyrant.setTarget(pig);
				combat.start(level, a, pig);
				helper.assertTrue(combat.current() == a && tyrant.isBusy(), a + " starts and holds the busy flag");
				int ticks = 0;
				while (combat.isAttacking() && ticks < a.length + 5) {
					combat.tick(level);
					ticks++;
					if (a != Attack.GRAVE_STEP) {
						pig.teleportTo(spot.x, spot.y, spot.z);
					}
				}
				helper.assertFalse(combat.isAttacking(), a + " finishes, took " + ticks + " ticks");
				if (a == Attack.SWEEP || a == Attack.QUAKE || a == Attack.CLEAVE) {
					helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), a + " hurts what stands in front of him");
				}
				// let the lingering spikes / storms play out before the next one
				for (int i = 0; i < 80; i++) {
					combat.tick(level);
					if (combat.isAttacking()) {
						break;
					}
				}
				combat.cancel(true);
				tyrant.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0f, 0f);
			}
			helper.assertTrue(combat.used().size() == Attack.values().length - 1, "all ten were started: " + combat.used());
		} finally {
			tyrant.dismissMinions(level);
			tyrant.discard();
			pig.discard();
			for (Arrow a : level.getEntitiesOfClass(Arrow.class, new AABB(c).inflate(40))) {
				a.discard();
			}
			deck(helper, false);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "bone_tyrant_cycle", timeoutTicks = 100)
	public void theTyrantRoarsIntoNewPhasesAndCyclesHisAttacks(GameTestHelper helper) {
		hard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos c = deck(helper, true);
		BoneTyrant tyrant = tyrantOnDeck(helper, c);
		Pig pig = EntityType.PIG.create(level);
		pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20000.0);
		pig.setHealth(20000.0f);
		pig.setNoAi(true);
		Vec3 spot = new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 6.9);
		pig.moveTo(spot.x, spot.y, spot.z, 0f, 0f);
		level.addFreshEntity(pig);
		tyrant.setTarget(pig);
		BoneTyrantCombat combat = tyrant.combat();
		try {
			helper.assertTrue(combat.phase() == 1, "he starts in phase 1");
			tyrant.setHealth(tyrant.getMaxHealth() * 0.5f);
			combat.tick(level);
			helper.assertTrue(combat.phase() == 2 && combat.isRoaring(), "at half health he roars into phase 2");
			float before = tyrant.getHealth();
			tyrant.hurt(level.damageSources().generic(), 50.0f);
			helper.assertTrue(tyrant.getHealth() == before, "and can't be hurt while he roars");
			for (int i = 0; i < Attack.ROAR.length + 2; i++) {
				combat.tick(level);
			}
			helper.assertFalse(combat.isRoaring(), "the roar ends");
			helper.assertTrue(tyrant.liveMinions(level) > 0, "and the dead answer it");
			tyrant.setHealth(tyrant.getMaxHealth() * 0.2f);
			for (int i = 0; i < Attack.ROAR.length + 2; i++) {
				combat.tick(level);
			}
			helper.assertTrue(combat.phase() == 3, "below a third he is enraged");
			helper.assertTrue(tyrant.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.32, "and faster");
			tyrant.dismissMinions(level); // room for Raise the Dead again
			Set<Attack> seen = EnumSet.noneOf(Attack.class);
			Attack previous = null;
			for (int i = 0; i < 4000; i++) {
				if (i % 200 == 0) {
					for (Arrow a : level.getEntitiesOfClass(Arrow.class, new AABB(c).inflate(40))) {
						a.discard();
					}
				}
				pig.teleportTo(spot.x, spot.y, spot.z);
				pig.setHealth(pig.getMaxHealth());
				tyrant.setHealth(tyrant.getMaxHealth() * 0.2f);
				combat.tick(level);
				Attack now = combat.current();
				if (now != null && now != previous) {
					seen.add(now);
				}
				previous = now;
			}
			seen.remove(Attack.ROAR);
			helper.assertTrue(seen.size() >= 5, "left to himself he cycles through his moves: " + seen);
		} finally {
			tyrant.dismissMinions(level);
			tyrant.discard();
			pig.discard();
			for (Arrow a : level.getEntitiesOfClass(Arrow.class, new AABB(c).inflate(40))) {
				a.discard();
			}
			deck(helper, false);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- a whole Skeleton Horde

	@GameTest(template = EMPTY_STRUCTURE, batch = "skeleton_horde_raid", timeoutTicks = 1200)
	public void aSkeletonHordeIsWonWhenTheTyrantFalls(GameTestHelper helper) {
		hard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		level.setBlock(pos, HordeBlocks.SKELETON_HORDE.defaultBlockState(), 3);
		// a creative watcher keeps the event ticking (present) without ever being a fighter or a target
		helper.makeMockServerPlayerInLevel();
		helper.assertTrue(Hordes.start(level, pos, HordeKind.SKELETON), "the horde wakes");
		HordeRaid raid = (HordeRaid) EventManager.at(level, pos);
		BoneTyrant[] boss = new BoneTyrant[1];
		helper.onEachTick(() -> {
			if (raid.state().finished()) {
				return;
			}
			if (!raid.bossPhase()) {
				if (helper.getTick() % 5 == 0) {
					raid.debugAdvance(level);
				}
				return;
			}
			if (boss[0] == null) {
				List<BoneTyrant> found = level.getEntitiesOfClass(BoneTyrant.class, new AABB(pos).inflate(48));
				if (!found.isEmpty()) {
					boss[0] = found.get(0);
					boss[0].kill();
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(boss[0] != null, "the Bone Tyrant came out");
			helper.assertTrue(raid.state() == EventState.COMPLETED, "the horde is beaten, state " + raid.state());
			helper.assertTrue(level.getBlockEntity(pos) instanceof ChestBlockEntity chest && !chest.isEmpty(), "and its block is a chest of loot");
			for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(48), m -> !(m instanceof BoneTyrant))) {
				m.discard();
			}
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		});
	}
}
