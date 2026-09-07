package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.WaveDefinition;
import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.raid.ZombieRaid;
import com.projecthero.mod.event.raid.ZombieRaidWaves;
import com.projecthero.mod.grave.CurseSource;
import com.projecthero.mod.grave.GraveboundCurse;
import com.projecthero.mod.grave.GraveboundEvents;
import com.projecthero.mod.grave.item.BossTrophyItem;
import com.projecthero.mod.grave.item.GraveComponents;
import com.projecthero.mod.grave.item.GraveItems;
import com.projecthero.mod.grave.item.NecroticBladeItem;
import com.projecthero.mod.grave.item.UndyingTotemItem;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.worldgen.GraveyardStructure;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Regression coverage for the Zombie Raid: the Gravebound Curse's "one curse, never reset" rule, the
 * things that must and must not remove it, the wave table's shape, boss health scaling, the boss
 * power roster, and the artifacts whose whole point is that their counters cannot be exploited.
 *
 * <p>These are the invariants from the design's testing sections that can be checked without a human
 * playing a raid. The ones that genuinely need a live session -- how the boss AI feels, whether the
 * Graveyard reads as creepy -- are listed in the summary as manual checks instead of being faked here.
 */
public class ZombieRaidGameTests implements FabricGameTest {

	private static ServerPlayer survivalMockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	// ---------------- the curse ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void curseAppliesOnceAndNeverResets(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		helper.assertTrue(GraveboundCurse.apply(player, CurseSource.GRAVEYARD), "first curse should apply");

		// Wind the timer down as if some of it had already been spent.
		var state = GraveboundCurse.state(player).copy();
		state.curseTicksLeft = 12345;
		GraveboundCurse.save(player, state);

		helper.assertFalse(GraveboundCurse.apply(player, CurseSource.CURSED_ZOMBIE),
				"a second source must not apply a second curse");
		helper.assertTrue(GraveboundCurse.remainingTicks(player) == 12345,
				"a second source must not reset or extend the timer, got "
						+ GraveboundCurse.remainingTicks(player));
		helper.assertTrue(GraveboundCurse.state(player).source() == CurseSource.GRAVEYARD,
				"the original curse source must be preserved");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cursedZombieCanReCurseAfterTheRaidIsBeaten(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		// First curse, from a Graveyard, then run it all the way out (this is the state a player is in
		// once their Zombie Raid has finished -- curse timer at zero, not cursed any more).
		helper.assertTrue(GraveboundCurse.apply(player, CurseSource.GRAVEYARD), "first curse should apply");
		var spent = GraveboundCurse.state(player).copy();
		spent.curseTicksLeft = 0;
		GraveboundCurse.save(player, spent);
		helper.assertFalse(GraveboundCurse.isCursed(player), "an expired curse must leave the player un-cursed");

		// A Cursed Zombie hits them again -> the Gravebound Curse (and so the raid) is fully repeatable.
		helper.assertTrue(GraveboundCurse.apply(player, CurseSource.CURSED_ZOMBIE),
				"a Cursed Zombie must be able to curse a player who already beat a raid");
		helper.assertTrue(GraveboundCurse.isCursed(player), "the fresh curse should be active");
		helper.assertTrue(GraveboundCurse.state(player).source() == CurseSource.CURSED_ZOMBIE,
				"the new curse should record the new source");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void milkAndEffectClearingDoNotRemoveCurse(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		GraveboundCurse.apply(player, CurseSource.CURSED_ZOMBIE);
		// Drinking milk is exactly this call. The curse is attachment data, so it cannot be reached.
		player.removeAllEffects();
		helper.assertTrue(GraveboundCurse.isCursed(player), "milk must not remove the Gravebound Curse");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyEnchantedGoldenAppleBreaksCurse(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		GraveboundCurse.apply(player, CurseSource.GRAVEYARD);

		GraveboundEvents.onFinishedEating(player, new ItemStack(Items.GOLDEN_APPLE));
		helper.assertTrue(GraveboundCurse.isCursed(player), "a plain golden apple must not break the curse");

		GraveboundEvents.onFinishedEating(player, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
		helper.assertFalse(GraveboundCurse.isCursed(player),
				"an Enchanted Golden Apple must break the curse");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void killingTheCursedZombieDoesNotLiftTheCurse(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Mob source = RaidEntityTypes.CURSED_ZOMBIE.create(helper.getLevel());
		helper.assertTrue(source != null, "Cursed Zombie must be creatable");
		GraveboundCurse.apply(player, CurseSource.CURSED_ZOMBIE);
		source.discard();
		helper.assertTrue(GraveboundCurse.isCursed(player),
				"the curse must outlive the mob that applied it");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void clearedCurseSchedulesNoRaid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int before = EventManager.active(level.getServer()).size();
		ServerPlayer player = survivalMockPlayer(helper);

		GraveboundCurse.apply(player, CurseSource.GRAVEYARD);
		GraveboundCurse.clear(player, false);
		// Ticking a cleared curse must do nothing at all -- in particular it must not start a raid.
		for (int i = 0; i < 40; i++) {
			GraveboundCurse.tick(player);
		}
		helper.assertTrue(EventManager.active(level.getServer()).size() == before,
				"a broken curse must not leave a raid scheduled");
		helper.succeed();
	}

	// ---------------- the wave table ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void waveTableHasTwelveWavesWithBossesOnFourEightTwelve(GameTestHelper helper) {
		helper.assertTrue(ZombieRaidWaves.count() == 12,
				"expected 12 waves, got " + ZombieRaidWaves.count());
		for (int wave = 1; wave <= 12; wave++) {
			WaveDefinition definition = ZombieRaidWaves.get(wave);
			helper.assertTrue(definition.number() == wave, "wave " + wave + " is misnumbered");
			helper.assertFalse(definition.spawns().isEmpty(), "wave " + wave + " spawns nothing");
			boolean expectBoss = wave == 4 || wave == 8 || wave == 12;
			helper.assertTrue(definition.bossWave() == expectBoss,
					"wave " + wave + " boss flag should be " + expectBoss);
			for (WaveDefinition.Spawn spawn : definition.spawns()) {
				helper.assertTrue(spawn.type().get() != null, "wave " + wave + " has an unregistered spawn type");
				helper.assertTrue(spawn.baseCount() > 0, "wave " + wave + " has a zero-count entry");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waveSizesMatchTheDoubledTable(GameTestHelper helper) {
		double per = EventConfig.raid().mobCountPerExtraPlayer;
		int[] expectedSolo = { 12, 18, 16, 20, 18, 20, 14, 34, 26, 30, 52, 34 };
		for (int wave = 1; wave <= 12; wave++) {
			int total = ZombieRaidWaves.get(wave).totalCount(1, per);
			helper.assertTrue(total == expectedSolo[wave - 1],
					"wave " + wave + " solo size should be " + expectedSolo[wave - 1] + ", got " + total);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossMeleeDamageMatchesTheConfiguredValues(GameTestHelper helper) {
		String power = BossPowers.eligibleKeys().get(0);
		EmpoweredZombie mid = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		EmpoweredZombie last = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		helper.assertTrue(mid != null && last != null, "Empowered Zombie must be creatable");
		mid.configure(power, null, false, 1);
		last.configure(power, null, true, 1);
		double midDmg = mid.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		double lastDmg = last.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		helper.assertTrue(Math.abs(midDmg - EventConfig.raid().bossMeleeDamage) < 0.01,
				"a wave 4/8 boss should hit for " + EventConfig.raid().bossMeleeDamage + ", got " + midDmg);
		helper.assertTrue(Math.abs(lastDmg - EventConfig.raid().finalBossMeleeDamage) < 0.01,
				"the wave 12 boss should hit for " + EventConfig.raid().finalBossMeleeDamage + ", got " + lastDmg);
		mid.discard();
		last.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waveCountsScaleWithGroupSizeButNotUnbounded(GameTestHelper helper) {
		double perPlayer = EventConfig.raid().mobCountPerExtraPlayer;
		WaveDefinition wave = ZombieRaidWaves.get(11);
		int solo = wave.totalCount(1, perPlayer);
		int four = wave.totalCount(4, perPlayer);
		helper.assertTrue(four > solo, "a group should face more mobs than a solo player");
		helper.assertTrue(four <= solo * 3,
				"group scaling should stay well under linear-per-player, got " + solo + " -> " + four);
		// The doubled waves can ask for more than the live-mob ceiling for a large group; that is fine,
		// the raid drips spawns and holds at the ceiling. It must not ask for an absurd multiple of it.
		helper.assertTrue(four <= EventConfig.framework().maxLiveMobs * 2,
				"a wave's requested count must stay within 2x the live-mob ceiling, got " + four);
		helper.succeed();
	}

	// ---------------- bosses ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyBossPowerMapsToARealExperimentalPower(GameTestHelper helper) {
		helper.assertFalse(BossPowers.eligibleKeys().isEmpty(), "there must be boss-capable powers");
		EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		helper.assertTrue(boss != null, "Empowered Zombie must be creatable");
		for (String key : BossPowers.eligibleKeys()) {
			helper.assertTrue(Powers.byKey(key) != null,
					"boss power " + key + " does not match an Experimental Power");
			BossPowerController controller = BossPowers.create(key, boss);
			helper.assertTrue(controller != null, "no controller built for " + key);
			helper.assertTrue(controller.powerKey().equals(key),
					"controller for " + key + " reports " + controller.powerKey());
			helper.assertTrue(controller.preferredRange() > 0.0, key + " must declare a preferred range");
		}
		boss.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossHealthFollowsTheScalingTable(GameTestHelper helper) {
		String power = BossPowers.eligibleKeys().get(0);
		// The design's table up to four players. Beyond that the requested health exceeds vanilla's
		// 1024 clamp on MAX_HEALTH, so the surplus is deliberately converted into armour instead --
		// see EmpoweredZombie#applyScaledHealth -- and that is what the six-player case below checks.
		double[] expected = { 400.0, 600.0, 800.0, 1000.0 };
		for (int players = 1; players <= 4; players++) {
			EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
			helper.assertTrue(boss != null, "Empowered Zombie must be creatable");
			boss.configure(power, null, false, players);
			float health = boss.getMaxHealth();
			helper.assertTrue(Math.abs(health - expected[players - 1]) < 1.0f,
					players + " players should give " + expected[players - 1] + " HP, got " + health);
			boss.discard();
		}

		EmpoweredZombie four = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		EmpoweredZombie six = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		helper.assertTrue(four != null && six != null, "Empowered Zombie must be creatable");
		four.configure(power, null, false, 4);
		six.configure(power, null, false, 6);
		helper.assertTrue(six.getMaxHealth() <= 1024.0f,
				"boss health must respect vanilla's MAX_HEALTH clamp, got " + six.getMaxHealth());
		helper.assertTrue(six.getArmorValue() > four.getArmorValue(),
				"past the health clamp, a larger group must still face a tougher boss (armour "
						+ four.getArmorValue() + " -> " + six.getArmorValue() + ")");
		four.discard();
		six.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void finalBossIsStrongerAndCanCarryTwoPowers(GameTestHelper helper) {
		EmpoweredZombie normal = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		EmpoweredZombie last = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		helper.assertTrue(normal != null && last != null, "Empowered Zombie must be creatable");
		String first = BossPowers.eligibleKeys().get(0);
		BossPowerController controller = BossPowers.create(first, last);
		String second = BossPowers.randomCompatibleKey(helper.getLevel().random, controller);

		normal.configure(first, null, false, 1);
		last.configure(first, second, true, 1);

		helper.assertTrue(last.getMaxHealth() > normal.getMaxHealth(),
				"the final boss must be tougher than an ordinary one");
		helper.assertTrue(last.cooldownScale() < normal.cooldownScale(),
				"the final boss must act more often");
		helper.assertTrue(second == null || !second.equals(first),
				"a dual-power boss must not be given the same power twice");
		normal.discard();
		last.discard();
		helper.succeed();
	}

	// ---------------- artifacts ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void undyingTotemSpendsExactlyThreeCharges(GameTestHelper helper) {
		ItemStack totem = UndyingTotemItem.fresh(GraveItems.UNDYING_TOTEM);
		int max = UndyingTotemItem.maxCharges();
		helper.assertTrue(UndyingTotemItem.charges(totem) == max,
				"a fresh totem should carry " + max + " charges");
		for (int i = 0; i < max; i++) {
			helper.assertTrue(UndyingTotemItem.consumeCharge(totem), "charge " + (i + 1) + " should be spendable");
		}
		helper.assertTrue(totem.isEmpty(), "the totem must be destroyed once its charges are gone");
		helper.assertFalse(UndyingTotemItem.consumeCharge(totem), "a spent totem must not save anyone again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void undyingTotemWithoutComponentIsTreatedAsFresh(GameTestHelper helper) {
		// A totem from /give or an older save has no component; it must still work rather than be inert.
		ItemStack bare = new ItemStack(GraveItems.UNDYING_TOTEM);
		helper.assertTrue(UndyingTotemItem.charges(bare) == UndyingTotemItem.maxCharges(),
				"a component-less totem should default to full charges");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void necroticBladeStacksAreClampedAndExpire(GameTestHelper helper) {
		ItemStack blade = new ItemStack(GraveItems.NECROTIC_BLADE);
		long now = helper.getLevel().getGameTime();
		for (int i = 0; i < 50; i++) {
			NecroticBladeItem.addStack(blade, now);
		}
		int max = NecroticBladeItem.maxStacks();
		helper.assertTrue(NecroticBladeItem.stacks(blade, now) == max,
				"blade stacks must clamp at " + max + ", got " + NecroticBladeItem.stacks(blade, now));

		long afterWindow = now + EventConfig.raid().necroticStackTicks + 1;
		helper.assertTrue(NecroticBladeItem.stacks(blade, afterWindow) == 0,
				"blade stacks must expire on their own");
		helper.assertTrue(NecroticBladeItem.bonusDamage(blade, afterWindow) == 0.0f,
				"an expired blade must give no bonus damage");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossTrophyRemembersItsPower(GameTestHelper helper) {
		String power = BossPowers.eligibleKeys().get(0);
		String other = BossPowers.eligibleKeys().get(1);
		ItemStack trophy = BossTrophyItem.of(GraveItems.BOSS_TROPHY, power);
		ItemStack otherTrophy = BossTrophyItem.of(GraveItems.BOSS_TROPHY, other);

		helper.assertTrue(power.equals(trophy.get(GraveComponents.POWER_KEY)),
				"a trophy must remember the power it came from");
		helper.assertFalse(trophy.getHoverName().getString().equals(otherTrophy.getHoverName().getString()),
				"trophies from different powers must be named differently");
		helper.succeed();
	}

	// ---------------- registration ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void raidEntitiesAreRegisteredWithAttributes(GameTestHelper helper) {
		var types = new net.minecraft.world.entity.EntityType<?>[] {
				RaidEntityTypes.CURSED_ZOMBIE, RaidEntityTypes.RAID_ZOMBIE, RaidEntityTypes.ACID_ZOMBIE,
				RaidEntityTypes.SWORD_SKELETON, RaidEntityTypes.JUGGERNAUT_ZOMBIE,
				RaidEntityTypes.EMPOWERED_ZOMBIE };
		for (var type : types) {
			var entity = type.create(helper.getLevel());
			helper.assertTrue(entity instanceof Mob, type + " should create a mob");
			Mob mob = (Mob) entity;
			helper.assertTrue(mob.getMaxHealth() > 0.0f, type + " has no max health -- attributes not registered");
			mob.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void graveyardStructureIsRegisteredAndLocatable(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
		helper.assertTrue(structures.get(GraveyardStructure.KEY) != null,
				"projecthero:graveyard must exist in the structure registry for /locate to find it");
		var sets = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
		helper.assertTrue(sets.containsKey(com.projecthero.mod.ProjectHeroMod.id("graveyard")),
				"projecthero:graveyard needs a structure_set to generate at all");
		helper.succeed();
	}

	// ---------------- raid lifecycle ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void raidStartsRunsAndCleansUpWithoutLeakingMobs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalMockPlayer(helper);

		ZombieRaid raid = new ZombieRaid(UUID.randomUUID());
		helper.assertTrue(EventManager.start(level, raid, player.blockPosition()), "the raid should start");
		helper.assertTrue(raid.state() == EventState.PENDING, "a fresh raid should be pending");

		// Two ticks: the first begins it, the second runs the first wave's countdown.
		raid.tick(level);
		helper.assertTrue(raid.state() == EventState.RUNNING, "the raid should be running after its first tick");
		raid.tick(level);

		raid.debugJumpToWave(level, 12);
		helper.assertTrue(raid.wave() == 12, "the raid should be on wave 12, got " + raid.wave());

		raid.abort(level);
		helper.assertTrue(raid.ownedAlive(level) == 0, "an aborted raid must leave no mobs behind");
		EventSavedData.get(level).remove(raid.id());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void twoRaidsCannotBeStartedOnTopOfEachOther(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalMockPlayer(helper);

		ZombieRaid first = new ZombieRaid(UUID.randomUUID());
		ZombieRaid second = new ZombieRaid(UUID.randomUUID());
		helper.assertTrue(EventManager.start(level, first, player.blockPosition()), "the first raid should start");
		helper.assertFalse(EventManager.start(level, second, player.blockPosition()),
				"a second raid must be refused at the same place");

		first.abort(level);
		EventSavedData.get(level).remove(first.id());
		helper.succeed();
	}
}
