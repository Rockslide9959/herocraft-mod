package com.projecthero.mod.gametest;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.crime.ArsonCrime;
import com.projecthero.mod.crime.BreakInCrime;
import com.projecthero.mod.crime.CrimeAI;
import com.projecthero.mod.crime.CrimeConfig;
import com.projecthero.mod.crime.CrimeEvent;
import com.projecthero.mod.crime.CrimeScheduler;
import com.projecthero.mod.crime.CrimeType;
import com.projecthero.mod.crime.CrimeVillages;
import com.projecthero.mod.crime.Crimes;
import com.projecthero.mod.crime.GetawayCrime;
import com.projecthero.mod.crime.MuggingCrime;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.EventTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: street crime ({@code com.projecthero.mod.crime}). Each test drives its crime directly with
 * {@link CrimeEvent#stepForTest} and ends it before returning, so concurrent tests never see each other's crooks.
 */
public class CrimeV0162GameTests implements FabricGameTest {

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
			}
		}
	}

	private static Villager villager(GameTestHelper helper) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(helper.absoluteVec(new Vec3(4.5, 2, 4.5)), 0f, 0f);
		v.setNoAi(true);
		helper.getLevel().addFreshEntity(v);
		return v;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
		return p;
	}

	private static void end(ServerLevel level, CrimeEvent crime) {
		if (crime.state().active()) {
			crime.abort(level);
		}
		EventSavedData.get(level).remove(crime.id());
	}

	private static void kill(GameTestHelper helper, Mob mob, ServerPlayer by) {
		mob.invulnerableTime = 0;
		mob.hurt(helper.getLevel().damageSources().playerAttack(by), 1000f);
	}

	// ---------------------------------------------------------------- config + cooldowns

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeConfigDefaultsAndMigration(GameTestHelper helper) {
		CrimeConfig c = CrimeConfig.fresh();
		helper.assertTrue(c.configVersion != null && c.configVersion == CrimeConfig.SPEC.currentVersion(), "fresh config is stamped");
		helper.assertTrue(c.trigger.enabled, "natural crime is on by default");
		helper.assertTrue(c.trigger.minIntervalMinutes == 10.0 && c.trigger.maxIntervalMinutes == 20.0, "10-20 minutes a village");
		helper.assertTrue(c.trigger.villageSearchRadius == 96, "within 96 blocks of a bell");
		helper.assertTrue(c.mugging.minThugs == 1 && c.mugging.maxThugs == 3, "1-3 muggers");
		helper.assertTrue(c.breakIn.minCrooks == 2 && c.breakIn.maxCrooks == 4, "2-4 burglars");
		helper.assertTrue(c.getaway.minGunmen == 1 && c.getaway.maxGunmen == 2, "1-2 gunmen cover a getaway");
		CrimeConfig edited = CrimeConfig.SPEC.fromJson("{\"trigger\": {\"enabled\": false, \"minIntervalMinutes\": 3.0}}");
		helper.assertFalse(edited.trigger.enabled, "the server's toggle survives a load");
		helper.assertTrue(edited.trigger.minIntervalMinutes == 3.0, "the server's frequency survives a load");
		helper.assertTrue(edited.rewards.emeraldsMax == c.rewards.emeraldsMax && edited.arson.maxFires == c.arson.maxFires,
				"missing sections get today's defaults");
		for (CrimeType t : CrimeType.values()) {
			helper.assertTrue(EventTypes.isRegistered(t.typeId()), t.typeId() + " is a registered world-event type");
			helper.assertTrue(CrimeType.byId(t.id()) == t, "byId round-trips " + t.id());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeCooldownsGatePlayersAndVillages(GameTestHelper helper) {
		UUID who = UUID.randomUUID();
		BlockPos bell = new BlockPos(12345, 70, -6789);
		long now = 1_000_000L;
		helper.assertFalse(CrimeScheduler.playerOnCooldown(who, now), "a new player is not on cooldown");
		CrimeScheduler.noteCrime(who, bell, now, RandomSource.create(7));
		long playerTicks = Math.round(CrimeConfig.trigger().playerCooldownMinutes * 1200);
		helper.assertTrue(CrimeScheduler.playerOnCooldown(who, now + 1), "on cooldown right after a crime");
		helper.assertFalse(CrimeScheduler.playerOnCooldown(who, now + playerTicks), "off cooldown after playerCooldownMinutes");
		helper.assertTrue(CrimeScheduler.villageOnCooldown(bell, now + 1), "the village is on cooldown");
		long min = Math.round(CrimeConfig.trigger().minIntervalMinutes * 1200);
		long max = Math.round(CrimeConfig.trigger().maxIntervalMinutes * 1200);
		helper.assertTrue(CrimeScheduler.villageOnCooldown(bell, now + min - 1), "a village waits at least the minimum");
		helper.assertFalse(CrimeScheduler.villageOnCooldown(bell, now + max), "and never more than the maximum");
		RandomSource r = RandomSource.create(42);
		for (int i = 0; i < 100; i++) {
			long t = CrimeScheduler.rollVillageInterval(r);
			helper.assertTrue(t >= min && t <= max, "interval " + t + " within [" + min + ", " + max + "]");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeAlertDirectionsAndVillageNames(GameTestHelper helper) {
		helper.assertTrue(CrimeVillages.directionKey(0, 0, 0, -10).endsWith(".n"), "-Z is north");
		helper.assertTrue(CrimeVillages.directionKey(0, 0, 10, 0).endsWith(".e"), "+X is east");
		helper.assertTrue(CrimeVillages.directionKey(0, 0, 10, -10).endsWith(".ne"), "+X -Z is north-east");
		helper.assertTrue(CrimeVillages.directionKey(0, 0, -10, 10).endsWith(".sw"), "-X +Z is south-west");
		helper.assertTrue(CrimeVillages.roundedDistance(41) == 40 && CrimeVillages.roundedDistance(1) == 5, "distances round to 5 m");
		BlockPos p = new BlockPos(300, 64, -1200);
		helper.assertTrue(CrimeVillages.nameFor(p).equals(CrimeVillages.nameFor(p)) && !CrimeVillages.nameFor(p).isEmpty(),
				"a village's name is stable");
		helper.succeed();
	}

	// ---------------------------------------------------------------- mugging

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeMuggingSpawnsThugsAndCleansUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(helper);
		Villager v = villager(helper);
		CrimeEvent crime = Crimes.start(level, CrimeType.MUGGING, v.blockPosition(), false, false, v);
		helper.assertTrue(crime instanceof MuggingCrime, "a mugging starts on a villager");
		crime.stepForTest(level);
		helper.assertTrue(crime.state() == EventState.RUNNING, "it is running, got " + crime.state());
		List<Mob> thugs = crime.crooks(level);
		helper.assertTrue(thugs.size() >= 1 && thugs.size() <= 3, "1-3 thugs, got " + thugs.size());
		for (Mob m : thugs) {
			helper.assertTrue(m.getTags().contains(CrimeAI.CROOK_TAG), "each thug is tagged a crook");
			helper.assertTrue(m.getTarget() == v, "each thug goes for the victim");
			helper.assertTrue(m.isPersistenceRequired(), "event crooks never despawn mid-crime");
		}
		helper.assertTrue(v.getTags().contains(CrimeAI.VICTIM_TAG), "the villager is the victim");
		end(level, crime);
		for (Mob m : thugs) {
			helper.assertTrue(m.isRemoved(), "the thugs are gone when the crime ends");
		}
		helper.assertFalse(v.getTags().contains(CrimeAI.VICTIM_TAG), "the victim is released");
		helper.assertTrue(v.isAlive(), "and unharmed");
		v.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeMuggingStoppedPaysTheTopHelper(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(helper);
		Villager v = villager(helper);
		ServerPlayer hero = player(helper);
		CrimeEvent crime = Crimes.start(level, CrimeType.MUGGING, v.blockPosition(), false, false, v);
		crime.stepForTest(level);
		for (Mob m : crime.crooks(level)) {
			kill(helper, m, hero);
		}
		helper.assertTrue(crime.contributionOf(hero.getUUID()) > 0, "putting the thugs down counts as helping");
		crime.stepForTest(level);
		helper.assertTrue(crime.state() == EventState.COMPLETED, "every thug down -> stopped, got " + crime.state());
		int emeralds = hero.getInventory().countItem(Items.EMERALD);
		int least = Math.min(CrimeConfig.rewards().emeraldsMin, CrimeConfig.rewards().emeraldsMax) + CrimeConfig.rewards().topHelperBonusEmeralds;
		helper.assertTrue(emeralds >= least, "the top helper gets at least " + least + " emeralds, got " + emeralds);
		helper.assertTrue(hero.hasEffect(MobEffects.HERO_OF_THE_VILLAGE), "and Hero of the Village");
		helper.assertTrue(v.getGossips().getReputation(hero.getUUID(), t -> t == GossipType.MINOR_POSITIVE) > 0,
				"and the villagers speak well of them");
		helper.assertFalse(v.getTags().contains(CrimeAI.VICTIM_TAG), "the victim is released");
		end(level, crime);
		v.discard();
		hero.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeMuggingFailedKnocksOutNeverKills(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(helper);
		Villager v = villager(helper);
		MuggingCrime crime = (MuggingCrime) Crimes.start(level, CrimeType.MUGGING, v.blockPosition(), false, false, v);
		crime.stepForTest(level);
		Mob thug = crime.crooks(level).get(0);
		float health = v.getHealth();
		int before = crime.hitsLeft();
		v.invulnerableTime = 0;
		v.hurt(level.damageSources().mobAttack(thug), 5f);
		helper.assertTrue(crime.hitsLeft() == before - 1, "a thug's hit wears the victim down by one");
		helper.assertTrue(v.getHealth() == health, "but deals no real damage");
		crime.setHitsLeftForTest(1);
		v.invulnerableTime = 0;
		v.hurt(level.damageSources().mobAttack(thug), 500f);
		helper.assertTrue(v.isAlive() && v.getHealth() == health, "a knocked-out villager is never killed");
		helper.assertTrue(v.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "it is knocked out (can't move for a while)");
		helper.assertTrue(crime.state() == EventState.FAILED, "the mugging failed, got " + crime.state());
		helper.assertTrue(thug.isRemoved(), "and the thugs ran off");
		end(level, crime);
		v.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- break-in, getaway, arson

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeBreakInSackSpillsWhenTheCrookIsStopped(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(helper);
		ServerPlayer hero = player(helper);
		BreakInCrime crime = (BreakInCrime) Crimes.start(level, CrimeType.BREAK_IN, helper.absolutePos(new BlockPos(4, 2, 4)), false, false);
		crime.stepForTest(level);
		List<Mob> crooks = crime.crooks(level);
		helper.assertTrue(crooks.size() >= 2 && crooks.size() <= 4, "2-4 burglars, got " + crooks.size());
		helper.assertTrue(crooks.stream().allMatch(m -> m.getTarget() == null), "burglars never pick a fight");
		crime.fleeAllForTest(level);
		Mob first = crooks.get(0);
		Vec3 at = first.position();
		kill(helper, first, hero);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0), e -> e.getItem().is(Items.EMERALD)
				|| e.getItem().is(Items.BREAD));
		helper.assertTrue(!drops.isEmpty(), "the stolen goods spill where he fell");
		helper.assertTrue(crime.stopped() == 1, "one stopped");
		for (Mob m : crime.crooks(level)) {
			kill(helper, m, hero);
		}
		crime.stepForTest(level);
		helper.assertTrue(crime.state() == EventState.COMPLETED, "all stopped -> the break-in is stopped, got " + crime.state());
		helper.assertTrue(hero.getInventory().countItem(Items.EMERALD) > 0, "and the helper is paid");
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(16.0))) {
			e.discard();
		}
		end(level, crime);
		hero.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeGetawayEscapeFailsAndCleansUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(helper);
		BlockPos centre = helper.absolutePos(new BlockPos(4, 2, 4));
		GetawayCrime crime = (GetawayCrime) Crimes.start(level, CrimeType.GETAWAY, centre, false, false);
		crime.stepForTest(level);
		List<Mob> crew = crime.crooks(level);
		helper.assertTrue(crew.size() >= 2 && crew.size() <= 3, "a runner and 1-2 gunmen, got " + crew.size());
		Mob runner = (Mob) level.getEntity(crime.runnerId());
		helper.assertTrue(runner != null && runner.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND).is(Items.CHEST),
				"the runner carries the loot sack");
		crime.runForTest(level);
		helper.assertTrue(crime.running(), "the run is on");
		// move the village far behind him: he is now well past the escape radius
		crime.setVillage(centre.offset(CrimeConfig.getaway().escapeRadius + 20, 0, 0), false);
		crime.stepForTest(level);
		helper.assertTrue(crime.state() == EventState.FAILED, "past the escape radius -> he got away, got " + crime.state());
		for (Mob m : crew) {
			helper.assertTrue(m.isRemoved(), "the whole crew is gone");
		}
		end(level, crime);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crimeArsonOnlyLightsSafeGroundAndTimesOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		floor(helper);
		BlockPos spot = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.assertTrue(ArsonCrime.safeFireSpot(level, spot), "bare stone, nothing flammable near: safe");
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.OAK_PLANKS);
		helper.assertFalse(ArsonCrime.safeFireSpot(level, spot), "planks a block away: never");
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.OAK_PLANKS);
		helper.assertFalse(ArsonCrime.safeFireSpot(level, spot), "a wooden floor: never");
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);

		ArsonCrime crime = (ArsonCrime) Crimes.start(level, CrimeType.ARSON, helper.absolutePos(new BlockPos(4, 2, 4)), false, false);
		crime.stepForTest(level);
		helper.assertTrue(crime.crooks(level).size() == 1, "one firebug");
		Mob bug = crime.crooks(level).get(0);
		helper.assertTrue(bug.getMainHandItem().is(Items.FLINT_AND_STEEL), "with a flint and steel");
		// run the clock out: he gets away
		for (int i = 0; i < CrimeConfig.arson().timeLimitSeconds * 2 + 2 && crime.state().active(); i++) {
			crime.stepForTest(level);
		}
		helper.assertTrue(crime.state() == EventState.FAILED, "time up -> failed, got " + crime.state());
		helper.assertTrue(bug.isRemoved(), "and he is gone");
		helper.assertTrue(crime.fires().isEmpty(), "every fire he lit is out");
		end(level, crime);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the natural trigger (own batch: it looks for any event near)

	@GameTest(template = EMPTY_STRUCTURE, batch = "crime_natural")
	public void crimeNaturalTriggerNeedsABellAndNotPeaceful(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		ServerPlayer p = player(helper);
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		helper.assertTrue(CrimeScheduler.tryStartNear(level, p, true) == null, "no village bell nearby -> no crime");
		helper.setBlock(new BlockPos(4, 2, 4), Blocks.BELL);
		level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
		helper.assertTrue(CrimeScheduler.tryStartNear(level, p, true) == null, "never on Peaceful");
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		CrimeEvent crime = CrimeScheduler.tryStartNear(level, p, true);
		helper.assertTrue(crime != null, "a bell nearby on Normal -> a crime starts");
		helper.assertTrue(crime.crimeType() != CrimeType.MUGGING, "no villagers about -> never a mugging");
		helper.assertTrue(crime.village().equals(helper.absolutePos(new BlockPos(4, 2, 4))), "in that bell's village");
		helper.assertTrue(CrimeScheduler.playerOnCooldown(p.getUUID(), level.getGameTime()), "the player is now on cooldown");
		helper.assertTrue(CrimeScheduler.tryStartNear(level, p, true) == null, "no second crime right on top of the first");
		end(level, crime);
		p.discard();
		helper.succeed();
	}
}
