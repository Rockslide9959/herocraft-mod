package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ultron.contagion.Assimilation;
import com.projecthero.mod.ultron.contagion.Contagion;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.ContagionRewards;
import com.projecthero.mod.ultron.contagion.ContagionState;
import com.projecthero.mod.ultron.contagion.NaniteCorruption;
import com.projecthero.mod.ultron.contagion.SokoviaFinale;
import com.projecthero.mod.ultron.contagion.SuitHijack;
import com.projecthero.mod.ultron.contagion.block.ContagionBlocks;
import com.projecthero.mod.ultron.contagion.entity.ContagionEntityTypes;
import com.projecthero.mod.ultron.contagion.entity.HijackedMarkEntity;
import com.projecthero.mod.ultron.contagion.entity.PurgeChargeEntity;
import com.projecthero.mod.ultron.contagion.entity.UltronNexusEntity;
import com.projecthero.mod.ultron.contagion.entity.UltronOmegaEntity;
import com.projecthero.mod.ultron.contagion.entity.UplinkAntennaEntity;
import com.projecthero.mod.ultron.contagion.item.ContagionItems;
import com.projecthero.mod.ultron.contagion.item.FirewallChip;
import com.projecthero.mod.ultron.contagion.item.VibraniumPlatingII;
import com.projecthero.mod.ultron.item.UltronItems;
import com.projecthero.mod.ultron.item.VibraniumPlating;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the Ultron Contagion. The world-wide state ({@link ContagionState}) is shared by the whole test server, so
 * every test that touches it runs in its own batch, snapshots what it changes and puts it back. Nothing here waits for the
 * real timers: each test drives one rule directly. Corruption is only ever applied to blocks the test placed itself.
 */
public class UltronContagionV0161GameTests implements FabricGameTest {

	// ---------------------------------------------------------------- registration and config

	@GameTest(template = EMPTY_STRUCTURE)
	public void contagionIsRegisteredWithItsRecipesAndDefaults(GameTestHelper helper) {
		for (String r : new String[] { "purge_charge", "firewall_beacon", "override_spike", "override_spike_redstone", "firewall_chip",
				"vibranium_plating_2", "vibranium_shield", "firewall_chip_install", "vibranium_plating_2_apply" }) {
			helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id(r)).isPresent(), "recipe " + r);
		}
		helper.assertTrue(ContagionItems.VIBRANIUM_SHIELD instanceof com.projecthero.mod.supersoldier.item.AdamantiumShieldItem,
				"the Vibranium Shield is a better Adamantium Shield");
		ContagionConfig cfg = new ContagionConfig();
		helper.assertTrue(cfg.delayDaysMin == 3 && cfg.delayDaysMax == 6, "3-6 days");
		helper.assertTrue(cfg.nexus.maxNexus == 4 && cfg.nexus.maxRadius == 32 && cfg.nexus.health == 800.0, "Nexus defaults");
		helper.assertTrue(cfg.omega.health == 4000.0 && cfg.omega.maxHealth == 12000.0, "Omega defaults");
		helper.assertTrue(ContagionConfig.omegaHealthFor(1, 1) == 4000.0 && ContagionConfig.omegaHealthFor(3, 1) == 5800.0
				&& ContagionConfig.omegaHealthFor(50, 1) == 12000.0, "Omega health scales and caps");
		helper.assertTrue(Math.abs(ContagionConfig.omegaHealthFor(1, 3) - 4800.0) < 0.01, "v3 = +20% Omega health");
		helper.assertTrue(ContagionConfig.maxNexusFor(2) == 5, "each version adds a Nexus");
		helper.assertTrue(ContagionConfig.maxNexusFor(99) == 4 + 4, "capped at v5");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the start

	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_start")
	public void contagionStartsAfterTheDelay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		ContagionState st = ContagionState.get(level);
		ContagionState.Stage stage = st.stage;
		long startAt = st.startAt;
		try {
			long now = level.getGameTime();
			Contagion.schedule(level, st, now);
			helper.assertTrue(st.stage == ContagionState.Stage.SCHEDULED, "scheduled");
			helper.assertTrue(st.startAt >= now + 3 * 24000L && st.startAt < now + 7 * 24000L, "3-6 days later, got " + (st.startAt - now));
			// nobody online in the overworld besides mock players: picking patient zero must never throw (it may find nothing
			// loaded 80-140 blocks out in a test world -- it never loads a chunk to look)
			Contagion.choosePatientZero(level);
		} finally {
			st.stage = stage;
			st.startAt = startAt;
			st.setDirty();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the corruption: record, revert, never a block entity

	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_corrupt")
	public void nexusRecordsAndRevertsItsBlocksExactly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ContagionState st = ContagionState.get(level);
		UUID id = UUID.randomUUID();
		BlockPos grass = helper.absolutePos(new BlockPos(1, 2, 1));
		BlockPos log = helper.absolutePos(new BlockPos(2, 2, 1));
		BlockPos leaves = helper.absolutePos(new BlockPos(3, 2, 1));
		BlockPos placedLeaves = helper.absolutePos(new BlockPos(4, 2, 1));
		BlockPos chest = helper.absolutePos(new BlockPos(5, 2, 1));
		BlockPos glass = helper.absolutePos(new BlockPos(1, 2, 3));
		BlockPos replaced = helper.absolutePos(new BlockPos(2, 2, 3));
		BlockState logX = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
		level.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
		level.setBlock(log, logX, 3);
		level.setBlock(leaves, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false), 3);
		level.setBlock(placedLeaves, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true), 3);
		level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
		level.setBlock(glass, Blocks.GLASS.defaultBlockState(), 3);
		level.setBlock(replaced, Blocks.STONE.defaultBlockState(), 3);
		ContagionState.NexusRecord rec = st.addNexus(id, grass, level.getGameTime());
		try {
			helper.assertTrue(NaniteCorruption.corruptAt(level, st, rec, grass), "grass corrupts");
			helper.assertTrue(NaniteCorruption.corruptAt(level, st, rec, log), "a natural log corrupts");
			helper.assertTrue(NaniteCorruption.corruptAt(level, st, rec, leaves), "natural leaves corrupt");
			helper.assertTrue(NaniteCorruption.corruptAt(level, st, rec, replaced), "stone corrupts");
			helper.assertFalse(NaniteCorruption.corruptAt(level, st, rec, placedLeaves), "player-placed leaves never");
			helper.assertFalse(NaniteCorruption.corruptAt(level, st, rec, chest), "a block entity never");
			helper.assertFalse(NaniteCorruption.corruptAt(level, st, rec, glass), "a non-natural block never");
			helper.assertTrue(NaniteCorruption.nearBlockEntity(level, glass, 4), "the chest keeps its surroundings clean");
			helper.assertTrue(level.getBlockState(grass).is(ContagionBlocks.CORRUPTED_CIRCUITRY), "grass -> circuitry");
			helper.assertTrue(level.getBlockState(log).is(ContagionBlocks.CORRUPTED_LOG)
					&& level.getBlockState(log).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "log keeps its axis");
			helper.assertTrue(level.getBlockState(replaced).is(ContagionBlocks.CORRUPTED_STONE), "stone -> corrupted stone");
			helper.assertTrue(rec.blocks.size() == 4, "4 blocks recorded, got " + rec.blocks.size());
			// the player replaces one corrupted block: the revert must leave it alone
			level.setBlock(replaced, Blocks.GLASS.defaultBlockState(), 3);
			NaniteCorruption.queueRevertWave(st, id);
			for (int i = 0; i < 20 && !st.pendingPos.isEmpty(); i++) {
				NaniteCorruption.tickRevert(level, st, 10000);
			}
			helper.assertTrue(level.getBlockState(grass).is(Blocks.GRASS_BLOCK), "grass is back");
			helper.assertTrue(level.getBlockState(log).equals(logX), "the log is back exactly (axis X)");
			helper.assertTrue(level.getBlockState(leaves).is(Blocks.OAK_LEAVES), "leaves are back");
			helper.assertTrue(level.getBlockState(replaced).is(Blocks.GLASS), "a block changed since is never touched");
			helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST) && level.getBlockState(placedLeaves).getValue(LeavesBlock.PERSISTENT),
					"the chest and the placed leaves were never touched");
		} finally {
			st.nexus.remove(id);
			st.setDirty();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_firewall")
	public void firewallStopsSpreadSpawnsAndResurgence(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		ServerLevel here = helper.getLevel();
		ContagionState st = ContagionState.get(level);
		BlockPos beacon = helper.absolutePos(new BlockPos(4, 1, 4));
		BlockPos grass = helper.absolutePos(new BlockPos(1, 2, 1));
		here.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
		UUID id = UUID.randomUUID();
		long now = level.getGameTime();
		java.util.Map<BlockPos, Long> saved = new java.util.HashMap<>(st.beacons);
		st.beacons.clear();
		try {
			helper.assertFalse(Contagion.resurgenceBlocked(level, st, now), "no beacon: Ultron may resurface");
			st.beacons.put(beacon, now + 24000);
			helper.assertTrue(st.protectedByBeacon(grass, now), "inside the 48-block radius");
			helper.assertFalse(st.protectedByBeacon(beacon.offset(60, 0, 0), now), "outside it");
			helper.assertTrue(Contagion.resurgenceBlocked(level, st, now), "a fuelled beacon blocks resurgence");
			ContagionState.NexusRecord rec = st.addNexus(id, grass, now);
			helper.assertFalse(NaniteCorruption.corruptAt(here, st, rec, grass), "no corruption inside the dome");
			helper.assertTrue(here.getBlockState(grass).is(Blocks.GRASS_BLOCK), "the grass is untouched");
			st.beacons.put(beacon, now - 1);
			helper.assertFalse(st.protectedByBeacon(grass, now), "an unfuelled beacon protects nothing");
			helper.assertFalse(Contagion.resurgenceBlocked(level, st, now), "nor blocks resurgence");
		} finally {
			st.nexus.remove(id);
			st.beacons.clear();
			st.beacons.putAll(saved);
			st.setDirty();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Purge Charge

	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_purge")
	public void purgeChargeRevertsUncompromisesAndStrips(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		ContagionState st = ContagionState.get(level);
		UUID id = UUID.randomUUID();
		BlockPos grass = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos machine = helper.absolutePos(new BlockPos(4, 2, 2));
		level.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
		ContagionState.NexusRecord rec = st.addNexus(id, grass, level.getGameTime());
		Zombie zombie = EntityType.ZOMBIE.create(level);
		try {
			helper.assertTrue(NaniteCorruption.corruptAt(level, st, rec, grass), "corrupted");
			st.compromised.put(machine, new ContagionState.Machine());
			zombie.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
			zombie.setNoAi(true);
			level.addFreshEntity(zombie);
			Assimilation.assimilate(zombie, st);
			helper.assertTrue(Assimilation.isAssimilated(zombie), "assimilated");
			PurgeChargeEntity.burst(level, Vec3.atCenterOf(grass));
			helper.assertTrue(level.getBlockState(grass).is(Blocks.GRASS_BLOCK), "the corrupted block is back");
			helper.assertFalse(rec.blocks.containsKey(grass.asLong()), "and no longer recorded");
			helper.assertFalse(st.compromised.containsKey(machine), "the machine is free");
			helper.assertFalse(Assimilation.isAssimilated(zombie), "the plating is stripped");
		} finally {
			zombie.discard();
			st.nexus.remove(id);
			st.compromised.remove(machine);
			st.setDirty();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void assimilatedMobsGetArmourAndIgnorePoison(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie zombie = EntityType.ZOMBIE.create(level);
		zombie.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
		zombie.setNoAi(true);
		level.addFreshEntity(zombie);
		double before = zombie.getAttributeValue(Attributes.ARMOR);
		Assimilation.assimilate(zombie, ContagionState.get(level));
		helper.assertTrue(zombie.getAttributeValue(Attributes.ARMOR) == before + ContagionConfig.assimilation().armor, "+4 armour");
		helper.assertFalse(zombie.addEffect(new MobEffectInstance(MobEffects.POISON, 100)), "poison doesn't take");
		Assimilation.strip(zombie);
		helper.assertTrue(zombie.getAttributeValue(Attributes.ARMOR) == before, "stripped: armour back");
		zombie.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Override capture

	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_capture", timeoutTicks = 80)
	public void captureTimerNeedsAPlayerInside(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		ContagionConfig cfg = ContagionConfig.get();
		boolean terrain = cfg.terrainCorruption;
		int waves = cfg.nexus.waveIntervalTicks;
		cfg.terrainCorruption = false;
		cfg.nexus.waveIntervalTicks = Integer.MAX_VALUE / 2;
		helper.assertTrue(UltronNexusEntity.rate(0, false) == 0.0 && UltronNexusEntity.rate(1, false) == 1.0 && UltronNexusEntity.rate(2, false) == 2.0
				&& Math.abs(UltronNexusEntity.rate(1, true) - 1.4) < 1e-9, "rate: none / one / two+ / Mind Stone");
		ContagionState st = ContagionState.get(level);
		BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
		UltronNexusEntity nexus = ContagionEntityTypes.ULTRON_NEXUS.create(level);
		nexus.moveTo(Vec3.atBottomCenterOf(at));
		nexus.configure(1);
		nexus.finishGrowing();
		st.addNexus(nexus.getUUID(), at, level.getGameTime());
		level.addFreshEntity(nexus);
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(nexus.getX() + 30, nexus.getY(), nexus.getZ());
		helper.assertTrue(nexus.startCapture(p), "the spike starts the capture");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(nexus.captureTicks() == 0f, "nobody inside: the timer doesn't run, got " + nexus.captureTicks());
			p.moveTo(nexus.getX() + 3, nexus.getY(), nexus.getZ());
		});
		helper.runAfterDelay(25, () -> {
			try {
				helper.assertTrue(nexus.captureTicks() > 5f, "a player inside: it runs, got " + nexus.captureTicks());
				nexus.hurt(level.damageSources().playerAttack(p), 50f);
				helper.assertTrue(nexus.getHealth() == nexus.getMaxHealth(), "shielded until Overridden");
				nexus.forceOverride();
				nexus.invulnerableTime = 0;
				nexus.hurt(level.damageSources().playerAttack(p), 50f);
				helper.assertTrue(nexus.getHealth() < nexus.getMaxHealth(), "Overridden: it can be hurt");
			} finally {
				st.nexus.remove(nexus.getUUID());
				nexus.discard();
				cfg.terrainCorruption = terrain;
				cfg.nexus.waveIntervalTicks = waves;
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Ultron Omega

	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_legion", timeoutTicks = 40)
	public void legionDecoysHealTheRealOmega(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronOmegaEntity real = ContagionEntityTypes.ULTRON_OMEGA.create(level);
		real.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
		real.configure(1, 1);
		level.addFreshEntity(real);
		real.setHealth(real.getMaxHealth() * 0.7f);
		UltronOmegaEntity decoy = real.spawnDecoyForTest(level, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		try {
			float before = real.getHealth();
			helper.assertFalse(decoy.hurt(level.damageSources().playerAttack(p), 20f), "a decoy never shows a hit");
			float healed = real.getHealth() - before;
			float want = (float) (real.getMaxHealth() * ContagionConfig.omega().decoyHealFraction);
			helper.assertTrue(Math.abs(healed - want) < 0.5f, "each decoy hit heals the real one 0.5%, got " + healed);
			helper.assertTrue(decoy.isDecoy() && !real.isDecoy(), "flags");
			// 10% of his health dealt to the real one collapses them
			real.invulnerableTime = 0;
			real.hurt(level.damageSources().genericKill(), real.getMaxHealth() * 0.15f);
			helper.assertTrue(real.decoyIds().isEmpty(), "the decoys collapse");
		} finally {
			decoy.discard();
			real.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_vision_stone", timeoutTicks = 120)
	public void visionMomentWithTheMindStone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronOmegaEntity o = ContagionEntityTypes.ULTRON_OMEGA.create(level);
		o.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
		o.configure(1, 1);
		level.addFreshEntity(o);
		o.hurt(level.damageSources().genericKill(), 1_000_000f);
		helper.assertTrue(o.isAlive() && o.inVision(), "at 0 health he kneels instead of dying");
		helper.assertFalse(o.hurt(level.damageSources().genericKill(), 1_000_000f), "kneeling: nothing but the beam");
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(o.visionStruck(level, p), "the Mind Stone Beam strikes");
		helper.succeedWhen(() -> helper.assertTrue(!o.isAlive() && o.killedByMindStone(), "he disintegrates"));
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_vision_none", timeoutTicks = 200)
	public void visionMomentWithoutAMindStoneSelfDestructs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronOmegaEntity o = ContagionEntityTypes.ULTRON_OMEGA.create(level);
		o.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
		o.configure(1, 1);
		level.addFreshEntity(o);
		o.enterVisionForTest();
		o.advanceVision(ContagionConfig.omega().selfDestructTicks);
		helper.succeedWhen(() -> helper.assertTrue(!o.isAlive() && !o.killedByMindStone(), "no Mind Stone: he self-destructs and still dies"));
	}

	/**
	 * v0.16.7 fix: the upload is faked as having started {@code uploadTicks + 1} ago -- which is a negative game time (read
	 * as "no upload") when this runs early in a fresh test server, so it waits until the world clock is far enough along.
	 */
	@GameTest(template = EMPTY_STRUCTURE, batch = "contagion_upload", timeoutTicks = 900)
	public void uploadFinishingMeansHeEscapes(GameTestHelper helper) {
		long need = ContagionConfig.omega().uploadTicks + 2L;
		long wait = Math.max(0L, need - helper.getLevel().getGameTime());
		helper.runAfterDelay(wait, () -> uploadFinishes(helper));
	}

	private static void uploadFinishes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel().getServer().overworld();
		ServerLevel here = helper.getLevel();
		ContagionState st = ContagionState.get(level);
		ContagionState.Stage stage = st.stage;
		double sat = st.saturation;
		int traces = st.traces;
		SokoviaFinale saved = st.finale;
		try {
			SokoviaFinale f = new SokoviaFinale();
			f.setSiteForTest(helper.absolutePos(new BlockPos(4, 1, 4)).below(ContagionConfig.finale().height));
			f.phase = SokoviaFinale.Phase.FIGHT;
			f.omegaSpawned = true;
			f.phaseStart = here.getGameTime();
			st.finale = f;
			st.stage = ContagionState.Stage.FINALE;
			st.traces = 3;
			UplinkAntennaEntity ant = ContagionEntityTypes.UPLINK_ANTENNA.create(here);
			ant.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))));
			here.addFreshEntity(ant);
			f.antennae.add(ant.getUUID());
			f.uploadStart = here.getGameTime() - ContagionConfig.omega().uploadTicks - 1;
			helper.assertTrue(f.uploadStart >= 0, "the faked upload start is a real game time");
			if (here == level) {
				SokoviaFinale.tick(level, st);
				helper.assertTrue(f.outcome == SokoviaFinale.Outcome.ESCAPE, "the upload finished: he escaped, got " + f.outcome);
				helper.assertTrue(st.saturation == ContagionConfig.saturation().resetTo && st.traces == 2, "Saturation 50%, one Trace lost");
				helper.assertTrue(st.stage == ContagionState.Stage.ACTIVE, "the Contagion goes on");
			}
			ant.discard();
		} finally {
			st.finale = saved;
			st.stage = stage;
			st.saturation = sat;
			st.traces = traces;
			st.setDirty();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- rewards, items, Iron Legion

	@GameTest(template = EMPTY_STRUCTURE)
	public void firstWinRewardsAreGrantedOnce(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.getInventory().add(new ItemStack(UltronItems.MIND_STONE));
		helper.assertTrue(ContagionRewards.grantFirstWin(p), "first win");
		ItemStack stone = ItemStack.EMPTY; // the empowered stone carries a component, so match by item, not by exact stack
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(UltronItems.MIND_STONE)) {
				stone = p.getInventory().getItem(i);
			}
		}
		helper.assertTrue(ContagionItems.empowered(stone),
				"the Mind Stone is empowered");
		helper.assertFalse(ContagionRewards.grantFirstWin(p), "second win: nothing again");
		helper.assertTrue(p.getInventory().countItem(UltronItems.MIND_STONE) == 1, "still exactly one Mind Stone");
		helper.assertTrue(ContagionRewards.wins(p) == 2, "both wins counted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void firewallChipAndPlatingTwo(GameTestHelper helper) {
		var helmet = com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", ArmorItem.Type.HELMET);
		var chest = com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", ArmorItem.Type.CHESTPLATE);
		ItemStack h = new ItemStack(helmet);
		helper.assertTrue(FirewallChip.canInstall(h), "a helmet takes a chip");
		helper.assertFalse(FirewallChip.canInstall(new ItemStack(chest)), "only a helmet");
		ItemStack chipped = FirewallChip.install(h);
		helper.assertTrue(FirewallChip.hasChip(chipped) && !FirewallChip.canInstall(chipped), "installed once");
		ItemStack c = new ItemStack(chest);
		double base = VibraniumPlating.armorValue(c);
		ItemStack p1 = VibraniumPlating.plate(c, helper.getLevel().registryAccess());
		ItemStack p2 = VibraniumPlatingII.plate(p1);
		helper.assertTrue(Math.abs(VibraniumPlating.armorValue(p2) - (base + VibraniumPlatingII.ARMOR_BONUS)) < 1e-6,
				"plating II replaces plating I (+3, not +5), got " + (VibraniumPlating.armorValue(p2) - base));
		helper.assertTrue(!VibraniumPlating.isPlated(p2) && VibraniumPlatingII.isPlated(p2), "the components swap");
		helper.assertFalse(VibraniumPlating.canPlate(p2), "plating I can't go back on");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void legionSuitsTakeTheSameDamageEveryHit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		HijackedMarkEntity m = ContagionEntityTypes.HIJACKED_MARK.create(level);
		m.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
		m.configure("mark_iii", 150, 1f);
		m.setNoAi(true);
		level.addFreshEntity(m);
		float last = -1f;
		for (int i = 0; i < 5; i++) {
			float before = m.getHealth();
			m.invulnerableTime = 0;
			m.hurt(level.damageSources().generic(), 10f);
			float dealt = before - m.getHealth();
			if (last >= 0) {
				helper.assertTrue(Math.abs(dealt - last) < 0.01f, "no adaptation: hit " + i + " did " + dealt + ", the first did " + last);
			}
			last = dealt;
		}
		m.powerDown();
		helper.assertTrue(m.isRemoved(), "a Purge Charge powers it down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hijackAttemptEatsSuitKeys(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		SuitHijack.startAttemptForTest(p);
		helper.assertTrue(SuitHijack.attempting(p), "an attempt runs");
		helper.assertTrue(SuitHijack.blocksAbilities(p, true), "a key press during an attempt only fights it");
		helper.assertFalse(SuitHijack.blocksFlight(p), "flight only locks once the attempt completes");
		SuitHijack.clearSessionState();
		helper.succeed();
	}

	/** v0.16.1: a Proximity Mine's EMP no longer drains suit energy -- it locks a suited Iron Man out for empLockoutTicks. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void proximityEmpLocksASuitOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		p.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
		com.projecthero.mod.ironman.TonyStark.grant(p);
		for (ArmorItem.Type t : new ArmorItem.Type[] { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
				ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS }) {
			p.setItemSlot(com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotFor(t),
					new net.minecraft.world.item.ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
		}
		float integ = com.projecthero.mod.ironman.IronManEnergy.integrity(p, "mark_iii");
		int ticks = ContagionConfig.assimilation().empLockoutTicks;
		helper.assertTrue(ticks == 60, "the EMP lockout is 3 s");
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.overloaded(p), "not locked out before the EMP");
		Assimilation.proximityEmp(level, p.position());
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.overloaded(p), "the EMP locks the suit out");
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.state(p).overloadUntil == level.getGameTime() + ticks,
				"for empLockoutTicks");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.integrity(p, "mark_iii") == integ, "and costs no integrity");
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}
}
