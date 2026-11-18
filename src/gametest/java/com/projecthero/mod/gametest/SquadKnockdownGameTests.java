package com.projecthero.mod.gametest;

import com.projecthero.mod.event.boss.BossTargets;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadKnockdown;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 squad knockdowns: a killing blow knocks a squad member down when a squadmate is standing (and only then), the
 * knocked player is untouchable and invisible to mobs and bosses, the countdown runs (paused while being revived) and bleeds
 * them out, the 5 s revive hold, give-up, the team wipe, the shorter re-knock, and that /kill and a totem are left alone.
 *
 * <p>Mock players are never ticked by the server, but the knockdown runs off the server tick, so the countdown and the revive
 * progress move on their own; the revive hold is driven by calling {@link SquadKnockdown#holdRevive} the way the interact
 * packets would. Countdown checks compare one tick with the next (step sizes), never elapsed totals.
 */
public class SquadKnockdownGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL); // mock players report creative otherwise
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static Squad squad(GameTestHelper helper, ServerPlayer leader, ServerPlayer... mates) {
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("ko" + leader.getUUID().toString().substring(0, 8), leader.getUUID());
		for (ServerPlayer m : mates) {
			squads.addMember(squad, m.getUUID());
		}
		return squad;
	}

	private static void done(GameTestHelper helper, Squad squad, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			SquadKnockdown.clear(p);
		}
		if (squad != null) {
			SquadManager.get(helper.getLevel().getServer()).disband(squad);
		}
		helper.succeed();
	}

	private static void fatal(ServerPlayer p) {
		p.hurt(p.damageSources().generic(), 1000.0f);
	}

	private static boolean dead(Player p) {
		return p.isDeadOrDying() || !p.isAlive();
	}

	private static Pose poseNow(ServerPlayer p) {
		try {
			java.lang.reflect.Method m = Player.class.getDeclaredMethod("updatePlayerPose");
			m.setAccessible(true);
			m.invoke(p);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p.getPose();
	}

	// ---------------------------------------------------------------- knocking down

	@GameTest(template = EMPTY_STRUCTURE)
	public void fatalHitKnocksDownWhenASquadmateIsUp(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 5.5, 5.5);
		Squad squad = squad(helper, a, b);
		fatal(a);
		helper.assertTrue(SquadKnockdown.isKnocked(a), "knocked down, not dead");
		helper.assertTrue(!dead(a) && a.getHealth() == SquadKnockdown.KNOCKED_HEALTH, "alive on a sliver: " + a.getHealth());
		helper.assertTrue(SquadKnockdown.totalTicks(a) == SquadKnockdown.KNOCK_TICKS, "75 s on the clock");
		helper.assertTrue(poseNow(a) == Pose.SWIMMING, "crawling (the swim / crawl pose), got " + a.getPose());
		helper.assertTrue(a.getBbHeight() < 1.0f, "the low box: " + a.getBbHeight());
		helper.assertTrue(!SquadKnockdown.isKnocked(b), "the squadmate is untouched");
		int[] seen = new int[1];
		helper.runAfterDelay(2, () -> seen[0] = SquadKnockdown.ticksLeft(a));
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(seen[0] - SquadKnockdown.ticksLeft(a) == 1, "one tick off per tick: " + seen[0] + " -> " + SquadKnockdown.ticksLeft(a));
			done(helper, squad, a, b);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aPlayerWithNobodyToReviveThemDiesNormally(GameTestHelper helper) {
		ServerPlayer solo = mock(helper, 2.5, 2.5);
		ServerPlayer stranger = mock(helper, 5.5, 5.5); // online, but not in the squad
		ServerPlayer loner = mock(helper, 4.5, 2.5); // in no squad at all
		Squad squad = squad(helper, solo);
		fatal(solo);
		helper.assertTrue(!SquadKnockdown.isKnocked(solo) && dead(solo), "alone in the squad: a real death");
		fatal(loner);
		helper.assertTrue(!SquadKnockdown.isKnocked(loner) && dead(loner), "no squad: a real death");
		done(helper, squad, solo, stranger, loner);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aHeldTotemAndKillAreLeftAlone(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 5.5, 5.5);
		ServerPlayer c = mock(helper, 4.5, 4.5);
		Squad squad = squad(helper, a, b, c);
		a.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		fatal(a);
		helper.assertTrue(!SquadKnockdown.isKnocked(a) && !dead(a), "the totem saved them");
		helper.assertTrue(a.getOffhandItem().isEmpty(), "and was used up");
		b.kill();
		helper.assertTrue(!SquadKnockdown.isKnocked(b) && dead(b), "/kill on a standing member is a real death");
		SquadKnockdown.knock(c);
		helper.assertTrue(SquadKnockdown.isKnocked(c), "knocked");
		c.kill();
		helper.assertTrue(!SquadKnockdown.isKnocked(c) && dead(c), "/kill on a knocked member is a real death");
		done(helper, squad, a, b, c);
	}

	// ---------------------------------------------------------------- while knocked

	@GameTest(template = EMPTY_STRUCTURE)
	public void knockedPlayersTakeNoDamageAndNothingTargetsThem(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 5.5, 5.5);
		Squad squad = squad(helper, a, b);
		SquadKnockdown.knock(a);
		helper.assertTrue(!a.hurt(a.damageSources().generic(), 5.0f) && a.getHealth() == SquadKnockdown.KNOCKED_HEALTH, "no damage");
		helper.assertTrue(!a.hurt(a.damageSources().onFire(), 5.0f), "not even fire");
		a.heal(5.0f);
		helper.assertTrue(a.getHealth() == SquadKnockdown.KNOCKED_HEALTH, "and no healing: only a revive stands them up");

		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 4.5));
		z.moveTo(at.x, at.y, at.z, 0f, 0f);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		z.setTarget(a);
		helper.assertTrue(z.getTarget() == null, "a mob cannot be pointed at them");
		helper.assertTrue(!a.canBeSeenAsEnemy(), "target goals skip them");
		helper.assertTrue(!TargetingConditions.forCombat().ignoreLineOfSight().test(z, a), "the targeting rules skip them");
		z.setTarget(b);
		helper.assertTrue(z.getTarget() == b, "and go for someone standing");
		helper.assertTrue(!BossTargets.isVictim(z, a), "no boss aims at them");
		BossThreat threat = new BossThreat(z);
		threat.add(a, 50.0f);
		helper.assertTrue(threat.strongest() == null, "and they pull no threat");
		helper.assertTrue(!z.hurt(a.damageSources().playerAttack(a), 5.0f), "they deal no damage either");
		z.discard();
		done(helper, squad, a, b);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void theCountdownRunningOutBleedsThemOut(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 5.5, 5.5);
		Squad squad = squad(helper, a, b);
		SquadKnockdown.knock(a);
		SquadKnockdown.setTicksLeft(a, 3);
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(!SquadKnockdown.isKnocked(a) && dead(a), "bled out");
			helper.assertTrue(a.getLastDamageSource() != null && a.getLastDamageSource().is(SquadKnockdown.BLED_OUT), "\"bled out\"");
			done(helper, squad, a, b);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void givingUpIsARealDeath(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 5.5, 5.5);
		Squad squad = squad(helper, a, b);
		SquadKnockdown.knock(a);
		helper.assertTrue(SquadKnockdown.giveUp(a), "given up");
		helper.assertTrue(!SquadKnockdown.isKnocked(a) && dead(a), "dead");
		helper.assertTrue(!SquadKnockdown.giveUp(b), "only a knocked player can give up");
		done(helper, squad, a, b);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = SquadKnockdown.NO_REVIVER_GRACE_TICKS + 40)
	public void whenTheWholeSquadIsDownEveryoneBleedsOut(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 5.5, 5.5);
		Squad squad = squad(helper, a, b);
		fatal(a);
		helper.assertTrue(SquadKnockdown.isKnocked(a), "a is down, b still up");
		fatal(b);
		helper.assertTrue(!SquadKnockdown.isKnocked(b) && dead(b), "the last one standing dies outright: nobody to revive them");
		helper.runAfterDelay(SquadKnockdown.NO_REVIVER_GRACE_TICKS / 2, () ->
				helper.assertTrue(SquadKnockdown.isKnocked(a), "a short grace (a squadmate stepping through a portal)"));
		helper.runAfterDelay(SquadKnockdown.NO_REVIVER_GRACE_TICKS + 5, () -> {
			helper.assertTrue(!SquadKnockdown.isKnocked(a) && dead(a), "then a bleeds out too: team wipe");
			done(helper, squad, a, b);
		});
	}

	// ---------------------------------------------------------------- reviving

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = SquadKnockdown.REVIVE_TICKS + 40)
	public void holdingRightClickForFiveSecondsRevivesWithTheBuffs(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 3.5, 2.5); // a block away
		Squad squad = squad(helper, a, b);
		SquadKnockdown.knock(a);
		helper.onEachTick(() -> SquadKnockdown.holdRevive(b, a));
		int[] seen = new int[2];
		helper.runAfterDelay(10, () -> {
			seen[0] = SquadKnockdown.ticksLeft(a);
			seen[1] = SquadKnockdown.reviveProgress(a);
		});
		helper.runAfterDelay(11, () -> {
			helper.assertTrue(SquadKnockdown.ticksLeft(a) == seen[0], "the countdown is paused while being revived");
			helper.assertTrue(SquadKnockdown.reviveProgress(a) - seen[1] == 1, "the revive fills a tick per tick");
			helper.assertTrue(SquadKnockdown.isReviving(b), "b is reviving");
		});
		helper.runAfterDelay(SquadKnockdown.REVIVE_TICKS + 5, () -> {
			helper.assertTrue(!SquadKnockdown.isKnocked(a) && !dead(a), "back on their feet");
			helper.assertTrue(a.getHealth() == SquadKnockdown.REVIVE_HEALTH || a.getHealth() > SquadKnockdown.REVIVE_HEALTH - 0.01f,
					"6 hearts: " + a.getHealth());
			helper.assertTrue(a.hasEffect(MobEffects.DAMAGE_RESISTANCE) && a.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 1,
					"Resistance II");
			helper.assertTrue(a.hasEffect(MobEffects.REGENERATION), "Regeneration");
			helper.assertTrue(SquadKnockdown.shielded(a) && !a.hurt(a.damageSources().generic(), 5.0f), "2 s untouchable");
			done(helper, squad, a, b);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void lettingGoEarlyResetsTheRevive(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 3.5, 2.5);
		Squad squad = squad(helper, a, b);
		SquadKnockdown.knock(a);
		int[] t = new int[1];
		helper.onEachTick(() -> {
			if (t[0]++ < 30) {
				SquadKnockdown.holdRevive(b, a);
			}
		});
		helper.runAfterDelay(25, () -> helper.assertTrue(SquadKnockdown.reviveProgress(a) > 15, "filling while held"));
		int[] seen = new int[1];
		helper.runAfterDelay(30 + SquadKnockdown.HOLD_GRACE_TICKS + 5, () -> {
			helper.assertTrue(SquadKnockdown.isKnocked(a), "still down");
			helper.assertTrue(SquadKnockdown.reviveProgress(a) == 0, "let go: the revive starts over");
			seen[0] = SquadKnockdown.ticksLeft(a);
		});
		helper.runAfterDelay(30 + SquadKnockdown.HOLD_GRACE_TICKS + 6, () -> {
			helper.assertTrue(seen[0] - SquadKnockdown.ticksLeft(a) == 1, "and the countdown runs again");
			done(helper, squad, a, b);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlySquadmatesInReachCanRevive(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 3.5, 2.5);
		ServerPlayer far = mock(helper, 7.5, 7.5);
		ServerPlayer stranger = mock(helper, 2.5, 3.5);
		Squad squad = squad(helper, a, b, far);
		SquadKnockdown.knock(a);
		helper.assertTrue(SquadKnockdown.holdRevive(stranger, a) == net.minecraft.world.InteractionResult.PASS, "not a squadmate");
		SquadKnockdown.holdRevive(far, a);
		helper.assertTrue(!SquadKnockdown.isReviving(far), "out of reach");
		SquadKnockdown.holdRevive(b, a);
		SquadKnockdown.holdRevive(far, a);
		helper.assertTrue(SquadKnockdown.isReviving(b) && !SquadKnockdown.isReviving(far), "one reviver at a time");
		done(helper, squad, a, b, far, stranger);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aQuickReKnockShortensTheCountdown(GameTestHelper helper) {
		ServerPlayer a = mock(helper, 2.5, 2.5);
		ServerPlayer b = mock(helper, 5.5, 5.5);
		Squad squad = squad(helper, a, b);
		int[] expected = {SquadKnockdown.CHAIN_TICKS[0], SquadKnockdown.CHAIN_TICKS[1], SquadKnockdown.CHAIN_TICKS[2], SquadKnockdown.CHAIN_TICKS[2]};
		for (int i = 0; i < expected.length; i++) {
			SquadKnockdown.knock(a);
			helper.assertTrue(SquadKnockdown.totalTicks(a) == expected[i], "knock " + (i + 1) + ": " + SquadKnockdown.totalTicks(a));
			SquadKnockdown.revive(a, b);
		}
		helper.assertTrue(SquadKnockdown.CHAIN_TICKS[1] == 45 * 20 && SquadKnockdown.CHAIN_TICKS[2] == 25 * 20, "75 -> 45 -> 25 s");
		done(helper, squad, a, b);
	}
}
