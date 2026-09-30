package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.PillagerSpy;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.raid.PillagerSpySpawner;
import com.projecthero.mod.event.raid.SupervillainRaid;
import com.projecthero.mod.event.raid.SupervillainRaidStarter;
import com.projecthero.mod.event.raid.SupervillainVillages;
import com.projecthero.mod.event.raid.ZombieRaid;
import com.projecthero.mod.event.raid.ZombieRaidStarter;
import com.projecthero.mod.grave.CurseSource;
import com.projecthero.mod.grave.CursedGraveBlock;
import com.projecthero.mod.grave.GraveboundCurse;
import com.projecthero.mod.grave.item.GraveItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4 -- "the raids are supposed to be repeatable". Both raids are run through their whole real
 * lifecycle twice: trigger -> the raid starts -> it is won through its real completion path -> the
 * record is gone -> the same trigger again -> a second, new raid starts.
 *
 * <p>The Supervillain test uses a village with a bell and <em>no villagers</em> on purpose: that is
 * exactly what a village looks like after its first Supervillain Raid, and it is what used to make a
 * second mark impossible (vanilla {@code isVillage} only counts POIs claimed by living villagers).
 *
 * <p>The two long-running lifecycle tests and the spawner test each get their own batch: raids are
 * world-global, so a live raid from one test could otherwise block (or be joined by) another test's
 * raid, and the spawner test's "no raid near" rule would see the lifecycle test's raid.
 */
public class RaidRepeatGameTests implements FabricGameTest {

	private static ServerPlayer survivor(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		return p;
	}

	/** A village's bell on a stone plinth, and nothing else -- no villagers, no beds. */
	private static BlockPos placeBell(GameTestHelper helper, BlockPos rel) {
		helper.setBlock(rel.below(), Blocks.STONE);
		helper.setBlock(rel, Blocks.BELL);
		return helper.absolutePos(rel);
	}

	private static GameTestAssertException notYet(String what) {
		return new GameTestAssertException("waiting: " + what);
	}

	private static void cleanup(ServerLevel level, EventInstance raid) {
		if (raid != null && EventSavedData.get(level).byId(raid.id()) != null) {
			raid.abort(level);
			EventSavedData.get(level).remove(raid.id());
		}
	}

	// ------------------------------------------------------------------ Supervillain Raid

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "v0144_raid_repeat_supervillain")
	public void supervillainRaidCanBeMarkedAgainAfterItIsBeaten(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		placeBell(helper, new BlockPos(1, 2, 1));
		ServerPlayer player = survivor(helper, new Vec3(4.5, 2, 4.5));

		int[] stage = {0};
		SupervillainRaid[] raids = new SupervillainRaid[2];
		helper.succeedWhen(() -> {
			try {
				switch (stage[0]) {
					case 0 -> {
						// The bell's POI is registered a tick after the block is placed.
						if (!PillagerSpy.insideVillage(level, player.blockPosition())) {
							throw notYet("the bell to register as a village POI");
						}
						helper.assertFalse(level.isVillage(player.blockPosition()),
								"a villager-less village is not a vanilla 'village' -- the case that used to block a re-mark");
						raids[0] = spyHits(helper, level, player);
						stage[0] = 1;
						throw notYet("the first raid to begin");
					}
					case 1 -> {
						if (raids[0].state() != EventState.RUNNING) {
							throw notYet("the first raid to begin");
						}
						raids[0].debugSkipToBoss(level);
						stage[0] = 2;
						throw notYet("the Supervillain to arrive");
					}
					case 2 -> {
						if (!"BOSS_ACTIVE".equals(raids[0].phaseName())) {
							throw notYet("the Supervillain to arrive");
						}
						EmpoweredZombie boss = findBoss(level, raids[0]);
						helper.assertTrue(boss != null, "the raid's Supervillain should be in the world");
						// The real victory path: the boss dies -> death hook -> raid completes on its next tick.
						boss.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
						if (boss.isAlive()) {
							boss.kill();
						}
						stage[0] = 3;
						throw notYet("the first raid to finish");
					}
					case 3 -> {
						if (EventSavedData.get(level).byId(raids[0].id()) != null) {
							throw notYet("the first raid to finish");
						}
						helper.assertTrue(raids[0].state() == EventState.COMPLETED,
								"the first raid should have been WON, got " + raids[0].state());
						helper.assertTrue(player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE),
								"the victory rewards (Champion of the Village) should have been handed out");
						helper.assertTrue(SupervillainVillages.get(level).cooldownSecondsRemaining(level, raids[0].center()) > 0,
								"the legacy post-raid cooldown record exists -- and must not block the next mark");

						// Same village, same kind of trigger: a second raid must start.
						raids[1] = spyHits(helper, level, player);
						helper.assertTrue(!raids[1].id().equals(raids[0].id()), "the second raid must be a new record");
						helper.assertTrue(raids[1].state().active(), "the second raid must be active");
						cleanup(level, raids[1]);
						stage[0] = 4;
					}
					default -> {
					}
				}
			} catch (GameTestAssertException e) {
				throw e;
			} catch (RuntimeException e) {
				cleanup(level, raids[0]);
				cleanup(level, raids[1]);
				throw e;
			}
		});
	}

	/** A Pillager Spy lands a hit on {@code player}; returns the Supervillain Raid that marked the village. */
	private static SupervillainRaid spyHits(GameTestHelper helper, ServerLevel level, ServerPlayer player) {
		PillagerSpy spy = RaidEntityTypes.PILLAGER_SPY.create(level);
		helper.assertTrue(spy != null, "the spy must create");
		Vec3 at = helper.absoluteVec(new Vec3(6.5, 2, 6.5));
		spy.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		SupervillainRaidStarter.onSpyHitPlayer(level, spy, player);
		spy.discard();
		EventInstance raid = EventManager.at(level, player.blockPosition());
		helper.assertTrue(raid instanceof SupervillainRaid,
				"the spy's hit should have marked the village (got " + raid + ")");
		return (SupervillainRaid) raid;
	}

	private static EmpoweredZombie findBoss(ServerLevel level, SupervillainRaid raid) {
		for (EmpoweredZombie z : level.getEntitiesOfClass(EmpoweredZombie.class,
				new AABB(raid.center()).inflate(96.0), z -> z.isAlive() && z.variant() != null)) {
			if (EventManager.owning(z) == raid) {
				return z;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ Zombie Raid / Gravebound Curse

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "v0144_raid_repeat_zombie")
	public void graveboundCurseAndZombieRaidRepeatAfterVictory(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivor(helper, new Vec3(3.5, 2, 3.5));
		GraveboundCurse.clear(player, false);

		int[] stage = {0};
		ZombieRaid[] raids = new ZombieRaid[2];
		helper.succeedWhen(() -> {
			try {
				switch (stage[0]) {
					case 0 -> {
						// A Cursed Zombie's hit (the same call CursedZombie#doHurtTarget makes), then the timer runs out.
						helper.assertTrue(GraveboundCurse.apply(player, CurseSource.CURSED_ZOMBIE), "the first curse applies");
						GraveboundCurse.expireNow(player);
						stage[0] = 1;
						throw notYet("the curse to expire and the raid to start");
					}
					case 1 -> {
						if (raids[0] == null) {
							if (!(EventManager.at(level, player.blockPosition()) instanceof ZombieRaid r)) {
								throw notYet("the curse to expire and the raid to start");
							}
							raids[0] = r;
						}
						if (raids[0].state() != EventState.RUNNING) {
							throw notYet("the first raid to begin");
						}
						helper.assertFalse(GraveboundCurse.isCursed(player), "the served curse is gone once the raid starts");
						// Straight to the last wave, clear it: the raid then finishes through completeWave -> complete.
						raids[0].debugJumpToWave(level, raids[0].totalWaves());
						raids[0].debugClearWave(level);
						stage[0] = 2;
						throw notYet("the first raid to finish");
					}
					case 2 -> {
						if (EventSavedData.get(level).byId(raids[0].id()) != null) {
							throw notYet("the first raid to finish");
						}
						helper.assertTrue(raids[0].state() == EventState.COMPLETED,
								"the first raid should have been WON, got " + raids[0].state());
						helper.assertTrue(GraveboundCurse.state(player).raidsCompleted >= 1,
								"the victory should be recorded on the player");

						// Every source can curse them again -- here the Cursed Zombie one -- and the raid comes again.
						helper.assertTrue(GraveboundCurse.apply(player, CurseSource.CURSED_ZOMBIE),
								"a player who beat a Zombie Raid must be cursable again");
						GraveboundCurse.expireNow(player);
						stage[0] = 3;
						throw notYet("the second raid to start");
					}
					case 3 -> {
						EventInstance e = EventManager.at(level, player.blockPosition());
						if (!(e instanceof ZombieRaid r) || r.id().equals(raids[0].id())) {
							throw notYet("the second raid to start");
						}
						raids[1] = r;
						helper.assertTrue(r.state().active(), "the second raid must be active");
						cleanup(level, raids[1]);
						GraveboundCurse.clear(player, false);
						stage[0] = 4;
					}
					default -> {
					}
				}
			} catch (GameTestAssertException e) {
				throw e;
			} catch (RuntimeException e) {
				cleanup(level, raids[0]);
				cleanup(level, raids[1]);
				GraveboundCurse.clear(player, false);
				throw e;
			}
		});
	}

	/** Every source reaches the same {@code apply}, and none of them remembers a finished raid. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyCurseSourceWorksAgainAfterRaidsWereBeaten(GameTestHelper helper) {
		ServerPlayer player = survivor(helper, new Vec3(2.5, 2, 2.5));
		var veteran = GraveboundCurse.state(player).copy();
		veteran.curseTicksLeft = 0;
		veteran.raidsCompleted = 7;
		veteran.heartOfTheGraveGranted = true;
		GraveboundCurse.save(player, veteran);
		for (CurseSource source : new CurseSource[] {CurseSource.GRAVEYARD, CurseSource.CURSED_ZOMBIE, CurseSource.RITUAL}) {
			helper.assertTrue(GraveboundCurse.apply(player, source), source + " must curse a raid veteran");
			GraveboundCurse.clear(player, false);
		}
		helper.succeed();
	}

	/** A curse that runs out beside someone else's running raid is kept, not thrown away. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void servedCurseBlockedByAnotherRaidIsDeferredNotLost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer defender = survivor(helper, new Vec3(2.5, 2, 2.5));
		ServerPlayer cursed = survivor(helper, new Vec3(152.5, 2, 2.5));
		ZombieRaid other = new ZombieRaid(UUID.randomUUID());
		try {
			helper.assertTrue(EventManager.start(level, other, defender.blockPosition()), "the other raid starts");
			other.tick(level);
			helper.assertTrue(other.state() == EventState.RUNNING, "the other raid is being fought");

			helper.assertTrue(GraveboundCurse.apply(cursed, CurseSource.CURSED_ZOMBIE), "the curse applies");
			GraveboundCurse.expireNow(cursed);
			GraveboundCurse.tick(cursed); // the curse runs out 150 blocks from a running raid

			helper.assertTrue(EventManager.at(level, cursed.blockPosition()) == null, "no second raid could start there");
			helper.assertTrue(GraveboundCurse.isCursed(cursed), "the served curse must be held, not thrown away");
			helper.assertTrue(GraveboundCurse.remainingTicks(cursed) <= ZombieRaidStarter.DEFER_TICKS,
					"it comes back shortly, got " + GraveboundCurse.remainingTicks(cursed));
		} finally {
			cleanup(level, other);
			GraveboundCurse.clear(cursed, false);
		}
		helper.succeed();
	}

	/** A used Cursed Grave does not stay "spent" forever. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void usedCursedGraveRekindles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos rel = new BlockPos(1, 2, 1);
		helper.setBlock(rel, GraveItems.CURSED_GRAVE.defaultBlockState().setValue(CursedGraveBlock.LIT, true));
		BlockPos pos = helper.absolutePos(rel);
		BlockState lit = level.getBlockState(pos);
		helper.assertTrue(lit.isRandomlyTicking(), "a lit (used) grave random-ticks");
		helper.assertFalse(lit.setValue(CursedGraveBlock.LIT, false).isRandomlyTicking(), "an idle grave costs nothing");
		lit.randomTick(level, pos, level.random);
		helper.assertFalse(level.getBlockState(pos).getValue(CursedGraveBlock.LIT), "the grave rekindles");
		helper.succeed();
	}

	// ------------------------------------------------------------------ Pillager Spy spawner

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "v0144_raid_repeat_spawner")
	public void spySpawnerEligibilityIgnoresPastVictories(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos bell = placeBell(helper, new BlockPos(1, 2, 1));
		BlockPos wanderer = bell.offset(100, 0, 0);     // 64-160 from the bell: outside the village, near it
		BlockPos inVillage = bell.offset(10, 0, 10);
		ServerPlayer veteran = survivor(helper, new Vec3(3.5, 2, 3.5));
		boolean[] victoriesApplied = {false};

		helper.succeedWhen(() -> {
			if (!PillagerSpy.insideVillage(level, inVillage)) {
				throw notYet("the bell to register as a village POI");
			}
			helper.assertFalse(level.isVillage(inVillage), "no villagers -> not a vanilla village");
			helper.assertTrue(PillagerSpySpawner.villageToScout(level, inVillage) == null,
					"no spy for a player already standing in the village");
			helper.assertTrue(PillagerSpySpawner.villageToScout(level, wanderer) != null,
					"a player near a village should be eligible for a spy");

			if (!victoriesApplied[0]) {
				victoriesApplied[0] = true;
				// Everything a won raid leaves behind: the global completion count, the village's legacy
				// cooldown record, and the player's Champion of the Village effect / Gravebound record.
				EventSavedData.get(level).noteCompleted();
				SupervillainVillages.get(level).startCooldown(level, bell);
				veteran.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 36000, 2));
				var state = GraveboundCurse.state(veteran).copy();
				state.raidsCompleted = 3;
				GraveboundCurse.save(veteran, state);
			}
			helper.assertTrue(PillagerSpySpawner.villageToScout(level, wanderer) != null,
					"past victories must never stop spies coming");
		});
	}
}
