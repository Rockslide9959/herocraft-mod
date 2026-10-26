package com.projecthero.mod.gametest;

import java.util.List;
import java.util.Optional;

import com.projecthero.mod.speedforce.BlackFlash;
import com.projecthero.mod.speedforce.SpeedForceAttachments;
import com.projecthero.mod.speedforce.SpeedForceConfig;
import com.projecthero.mod.speedforce.SpeedForceMod;
import com.projecthero.mod.speedforce.SpeedForcePerks;
import com.projecthero.mod.speedforce.SpeedForceRaces;
import com.projecthero.mod.speedforce.SpeedForceSpeedster;
import com.projecthero.mod.speedforce.SpeedForceWorks;
import com.projecthero.mod.speedforce.SpeedsterPrefs;
import com.projecthero.mod.speedforce.data.SpeedForceMemories;
import com.projecthero.mod.speedforce.data.SpeedForceRace;
import com.projecthero.mod.speedforce.entity.BlackFlashEntity;
import com.projecthero.mod.speedforce.network.SpeedBuildPayload;
import com.projecthero.mod.speedforce.network.SpeedsterPrefsPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Speed Force Speedster run assists and speed work: the speed cap, the turn assist, the static charge's chain
 * lightning, speed farming, vein mining, Speed Build, Speed Smelt, the Auto-Courier, race timing + the ghost's track, and
 * the Black Flash Lure. Mock players never move or tick on their own: runs go through {@link SpeedForceSpeedster#stepRun}
 * (the cage is 8 blocks wide), and mock players report {@code isCreative()} so each test sets Survival.
 */
public class SpeedForceSurvivalGameTests implements FabricGameTest {
	private static ServerPlayer flash(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		SpeedForceSpeedster.grant(p);
		SpeedForceSpeedster.setMomentum(p, 0f);
		SpeedForceSpeedster.resetRun(p);
		p.removeAllEffects();
		return p;
	}

	/** {@code ticks} ticks of flat-out running along +X at whatever the current momentum is worth. */
	private static void run(ServerPlayer p, int ticks) {
		p.setSprinting(true);
		for (int i = 0; i < ticks; i++) {
			double perTick = SpeedForceSpeedster.targetPerTick(p);
			SpeedForceSpeedster.stepRun(p, new Vec3(Math.max(0.5, perTick * 1.1), 0, 0));
		}
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
		m.setHealth(200f);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedCapHoldsMomentum(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		SpeedsterPrefs.apply(p, new SpeedsterPrefsPayload(false, -1, -1, SpeedsterPrefsPayload.assists(true, true, 5)));
		helper.assertTrue(SpeedForceSpeedster.state(p).speedCap == 5, "the cap is saved: 50%");
		run(p, 30 * 20);
		float m = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(m <= 0.5f + 1.0e-4f && m >= 0.45f, "30 s flat out builds to the cap and no further: " + m);
		SpeedForceSpeedster.setMomentum(p, 1f);
		helper.assertTrue(SpeedForceSpeedster.momentum(p) <= 0.5f + 1.0e-4f, "nothing else pushes past it either");
		SpeedsterPrefs.apply(p, new SpeedsterPrefsPayload(false, -1, -1, SpeedsterPrefsPayload.assists(true, true, 10)));
		run(p, 15 * 20);
		helper.assertTrue(SpeedForceSpeedster.momentum(p) > 0.6f, "back at 100%: it builds on past");
		// the flags survive the codec
		var copy = SpeedForceSpeedster.state(p).copy();
		helper.assertTrue(copy.speedCap == 10 && copy.autoSlow && copy.veinMine, "the run assists round-trip");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void turnAssistKeepsMomentum(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		p.setSprinting(true);
		SpeedForceSpeedster.setMomentum(p, 0.6f);
		double per = SpeedForceSpeedster.targetPerTick(p);
		for (int i = 0; i < 5; i++) {
			SpeedForceSpeedster.stepRun(p, new Vec3(per, 0, 0));
		}
		float before = SpeedForceSpeedster.momentum(p);
		SpeedForceSpeedster.stepRun(p, new Vec3(0, 0, per)); // a 90-degree turn in one tick
		float after = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(after >= before - 1.0e-5f, "a sharp turn at speed costs nothing: " + before + " -> " + after);
		before = after;
		SpeedForceSpeedster.stepRun(p, new Vec3(0, 0, -per)); // a reversal
		after = SpeedForceSpeedster.momentum(p);
		helper.assertTrue(after < before && before - after <= SpeedForceConfig.TURN_ASSIST_HARD_LOSS + 1.0e-4,
				"a reversal still costs, but only a little: " + before + " -> " + after);
		// below the assist's momentum the old turn loss still applies
		SpeedForceSpeedster.resetRun(p);
		SpeedForceSpeedster.setMomentum(p, 0.2f);
		per = SpeedForceSpeedster.targetPerTick(p);
		for (int i = 0; i < 5; i++) {
			SpeedForceSpeedster.stepRun(p, new Vec3(per, 0, 0));
		}
		before = SpeedForceSpeedster.momentum(p);
		SpeedForceSpeedster.stepRun(p, new Vec3(0, 0, per));
		helper.assertTrue(SpeedForceSpeedster.momentum(p) < before, "slow, a sharp turn costs as before");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chargeReleasesChainLightning(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 1.5, 2.0, 1.5);
		run(p, 40);
		helper.assertTrue(SpeedForcePerks.charge(p) > 0f, "running in Speed Mode builds static");
		helper.assertTrue(p.getAttached(SpeedForceAttachments.STATIC_CHARGE) != null, "synced for the HUD");
		SpeedForcePerks.setCharge(p, SpeedForceConfig.STATIC_MAX);
		Zombie a = zombie(helper, 3.5, 2.0, 3.5);
		Zombie b = zombie(helper, 5.5, 2.0, 3.5);
		Zombie c = zombie(helper, 5.5, 2.0, 5.5);
		Zombie d = zombie(helper, 3.5, 2.0, 6.5);
		int struck = SpeedForcePerks.onMelee(p, a);
		helper.assertTrue(struck == 4, "the struck foe and 3 more: " + struck);
		helper.assertTrue(a.getHealth() < 200f && b.getHealth() < 200f && c.getHealth() < 200f && d.getHealth() < 200f, "every one took the bolt");
		helper.assertTrue(SpeedForcePerks.charge(p) == 0f, "the charge is spent");
		helper.assertTrue(SpeedForcePerks.onMelee(p, a) == 0, "no charge, no discharge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cropsHarvestedAndReplanted(GameTestHelper helper) {
		BlockPos crop = new BlockPos(3, 2, 3);
		helper.setBlock(crop.below(), Blocks.FARMLAND);
		helper.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
		BlockPos young = new BlockPos(4, 2, 3);
		helper.setBlock(young.below(), Blocks.FARMLAND);
		helper.setBlock(young, ((CropBlock) Blocks.WHEAT).getStateForAge(3));
		ServerPlayer p = flash(helper, 3.5, 2.0, 5.5);
		p.setSprinting(true);
		SpeedForceSpeedster.setMomentum(p, 0.3f);
		SpeedForceSpeedster.stepRun(p, new Vec3(0, 0, 4.0)); // from z 1.5 through the crops to 5.5
		var level = helper.getLevel();
		var st = level.getBlockState(helper.absolutePos(crop));
		helper.assertTrue(st.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(st) == 0, "the grown wheat is replanted: " + st);
		var ys = level.getBlockState(helper.absolutePos(young));
		helper.assertTrue(ys.is(Blocks.WHEAT) && ((CropBlock) Blocks.WHEAT).getAge(ys) == 3, "the young wheat is left alone");
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(crop)).inflate(2));
		helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.WHEAT)), "the wheat dropped");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void veinMiningBreaksConnectedOres(GameTestHelper helper) {
		BlockPos[] ores = { new BlockPos(2, 3, 4), new BlockPos(3, 3, 4), new BlockPos(4, 3, 4), new BlockPos(4, 4, 5), new BlockPos(5, 4, 5) };
		for (BlockPos o : ores) {
			helper.setBlock(o, Blocks.IRON_ORE);
		}
		helper.setBlock(new BlockPos(1, 6, 1), Blocks.IRON_ORE); // apart from the vein
		ServerPlayer p = flash(helper, 3.5, 2.0, 2.5);
		ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
		p.setItemInHand(InteractionHand.MAIN_HAND, pick);
		helper.assertTrue(p.gameMode.destroyBlock(helper.absolutePos(ores[0])), "the first ore breaks");
		for (BlockPos o : ores) {
			helper.assertBlockPresent(Blocks.AIR, o);
		}
		helper.assertBlockPresent(Blocks.IRON_ORE, new BlockPos(1, 6, 1));
		helper.assertTrue(p.getMainHandItem().getDamageValue() >= ores.length, "the tool wore per block: " + p.getMainHandItem().getDamageValue());
		// vein mining off: one at a time
		SpeedsterPrefs.apply(p, new SpeedsterPrefsPayload(false, -1, -1, SpeedsterPrefsPayload.assists(true, false, 10)));
		helper.setBlock(ores[0], Blocks.IRON_ORE);
		helper.setBlock(ores[1], Blocks.IRON_ORE);
		p.gameMode.destroyBlock(helper.absolutePos(ores[0]));
		helper.assertBlockPresent(Blocks.IRON_ORE, ores[1]);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedBuildLaysBridge(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 1.5, 4.0, 3.5);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 64));
		Vec3 from = helper.absoluteVec(new Vec3(1.5, 4.0, 3.5));
		Vec3 to = helper.absoluteVec(new Vec3(6.5, 4.0, 3.5));
		int floorY = (int) Math.floor(from.y);
		int placed = SpeedForceWorks.buildAlong(p, helper.getLevel(), SpeedBuildPayload.BRIDGE, floorY, from, to);
		helper.assertTrue(placed >= 5, "a bridge under the run: " + placed);
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(4, 3, 3));
		helper.assertTrue(p.getOffhandItem().getCount() == 64 - placed, "each block used up");
		int wall = SpeedForceWorks.buildAlong(p, helper.getLevel(), SpeedBuildPayload.WALL, floorY, from, to);
		helper.assertTrue(wall >= 8, "sneaking: a 2-high wall beside the run: " + wall);
		helper.assertTrue(SpeedForceWorks.buildAlong(p, helper.getLevel(), SpeedBuildPayload.BRIDGE, floorY, from, from.add(0.1, 0, 0)) == 0,
				"not running: nothing laid");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void speedSmeltCooksTheStack(GameTestHelper helper) {
		BlockPos at = new BlockPos(3, 2, 3);
		helper.setBlock(at, Blocks.FURNACE);
		AbstractFurnaceBlockEntity f = (AbstractFurnaceBlockEntity) helper.getBlockEntity(at);
		f.setItem(0, new ItemStack(Items.RAW_IRON, 12));
		f.setItem(1, new ItemStack(Items.COAL, 1)); // 1600 ticks: 8 items
		int made = SpeedForceWorks.smeltAll(helper.getLevel(), f);
		helper.assertTrue(made == 8, "one coal cooks 8: " + made);
		helper.assertTrue(f.getItem(2).is(Items.IRON_INGOT) && f.getItem(2).getCount() == 8, "8 ingots out");
		helper.assertTrue(f.getItem(1).isEmpty() && f.getItem(0).getCount() == 4, "the coal burned, 4 raw iron left");
		f.setItem(1, new ItemStack(Items.COAL, 4));
		helper.assertTrue(SpeedForceWorks.smeltAll(helper.getLevel(), f) == 4 && f.getItem(1).getCount() == 3, "the rest, from one more coal");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void courierDeliversToTheChest(GameTestHelper helper) {
		BlockPos chest = new BlockPos(5, 2, 5);
		helper.setBlock(chest, Blocks.CHEST);
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		BlockPos abs = helper.absolutePos(chest);
		Vec3 spot = helper.absoluteVec(new Vec3(5.5, 2.0, 4.5));
		p.setAttached(SpeedForceAttachments.MEMORIES, SpeedForceMemories.EMPTY.remember(new SpeedForceMemories.Place(
				p.level().dimension().location().toString(), spot.x, spot.y, spot.z, 0f, Optional.of(abs))));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 16));
		float meter = SpeedForceSpeedster.meter(p);
		helper.assertTrue(SpeedForceWorks.courier(p, 0), "sent");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the hand is empty");
		ChestBlockEntity c = (ChestBlockEntity) helper.getBlockEntity(chest);
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).is(Items.DIAMOND)) {
				n += c.getItem(i).getCount();
			}
		}
		helper.assertTrue(n == 16, "all 16 in the chest: " + n);
		helper.assertTrue(Math.abs(meter - SpeedForceConfig.COURIER_COST - SpeedForceSpeedster.meter(p)) < 0.01f, "it cost the Speed Force");
		helper.assertFalse(SpeedForceWorks.courier(p, 0), "an empty hand sends nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void raceTimesAndSavesTheGhost(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 1.5, 2.0, 2.5);
		BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos finish = start.offset(24, 0, 0);
		String dim = p.level().dimension().location().toString();
		p.setAttached(SpeedForceAttachments.RACE, SpeedForceRace.startAt(dim, start).withFinish(finish));
		Vec3 s = Vec3.atBottomCenterOf(start);
		p.moveTo(s.x + 2, s.y, s.z);
		SpeedForceRaces.stepForTest(p, new Vec3(s.x - 2, s.y, s.z));
		helper.assertTrue(SpeedForceRaces.racing(p), "through the start: the clock runs");
		helper.runAfterDelay(20, () -> {
			Vec3 f = Vec3.atBottomCenterOf(finish);
			Vec3 before = p.position();
			p.moveTo(f.x + 2, f.y, f.z);
			if (SpeedForceRaces.racing(p)) {
				SpeedForceRaces.stepForTest(p, before);
			}
			helper.assertFalse(SpeedForceRaces.racing(p), "through the finish: done");
			SpeedForceRace race = SpeedForceRaces.of(p);
			helper.assertTrue(race.best().size() == 1 && race.bestTime() > 0 && race.bestTime() <= 21, "the time is kept: " + race.best());
			SpeedForceRace.Ghost g = p.getAttached(SpeedForceAttachments.RACE_GHOST);
			helper.assertTrue(g != null && g.track().size() >= 2, "the best run's track is saved for its ghost");
			// a second run: the ghost races it
			p.moveTo(s.x + 2, s.y, s.z);
			SpeedForceRaces.stepForTest(p, new Vec3(s.x - 2, s.y, s.z));
			helper.assertTrue(SpeedForceRaces.ghostOf(p) >= 0, "the ghost runs beside them");
			p.setAttached(SpeedForceAttachments.RACE, SpeedForceRace.EMPTY);
			SpeedForceRaces.stepForTest(p, p.position());
			helper.assertFalse(SpeedForceRaces.racing(p), "clearing the course drops the race");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void lureSummonsATougherBlackFlash(GameTestHelper helper) {
		ServerPlayer p = flash(helper, 2.5, 2.0, 2.5);
		ItemStack lure = new ItemStack(SpeedForceMod.BLACK_FLASH_LURE, 2);
		p.setItemInHand(InteractionHand.MAIN_HAND, lure);
		lure.getItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(BlackFlash.fightOf(p) == null && p.getMainHandItem().getCount() == 2, "not at 100%: refused, not used");
		SpeedForceSpeedster.setMomentum(p, 1f);
		p.getMainHandItem().getItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		BlackFlashEntity bf = BlackFlash.fightOf(p);
		helper.assertTrue(bf != null && bf.isLured(), "he comes, lured");
		helper.assertTrue(p.getMainHandItem().getCount() == 1, "one lure used up");
		helper.assertTrue(Math.abs(bf.getMaxHealth() - com.projecthero.mod.speedforce.BlackFlashConfig.get().boss.health * SpeedForceConfig.LURE_HEALTH_MULTIPLIER) < 0.5,
				"+50% health: " + bf.getMaxHealth());
		BlackFlash.onKilledBy(p, bf);
		helper.assertTrue(SpeedForceSpeedster.powerLost(p) == 0 && SpeedForceSpeedster.drained(p) > 0, "he won a lured fight: drained, the Speed Force kept");
		bf.discard();
		helper.succeed();
	}
}
