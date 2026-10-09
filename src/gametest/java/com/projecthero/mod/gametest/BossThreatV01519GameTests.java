package com.projecthero.mod.gametest;

import java.util.List;
import java.util.function.Function;

import com.projecthero.mod.behemoth.entity.BehemothEntityTypes;
import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;
import com.projecthero.mod.carnage.CarnageEntityTypes;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.horde.entity.BoneTyrant;
import com.projecthero.mod.horde.entity.BroodQueen;
import com.projecthero.mod.horde.entity.HordeEntityTypes;
import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.entity.KingpinEntity;
import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.entity.UltronPrimeEntity;
import com.projecthero.mod.ultron.entity.UltronSentryEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.19: the shared boss threat table ({@link BossThreat}). Bosses used to keep their first target (vanilla
 * HurtByTargetGoal / NearestAttackableTargetGoal never let go), so one player could kite while a friend hit for free.
 *
 * <p>Attackers are {@code makeMockPlayer} players: not added to the level (so no target goal or raid code can find
 * them on its own), but real {@link Player}s for {@link com.projecthero.mod.event.boss.BossTargets#isVictim}. A mock
 * player's abilities are not synced to its game type, so "creative" is faked with {@code abilities.invulnerable}.
 */
public class BossThreatV01519GameTests implements FabricGameTest {

	private static Player attacker(GameTestHelper helper, Mob boss, Vec3 offset) {
		Player p = helper.makeMockPlayer(GameType.SURVIVAL);
		p.moveTo(boss.position().add(offset));
		return p;
	}

	/** {@code who} lands an {@code amount} hit on {@code boss} (hurt-immunity frames cleared first). */
	private static boolean hit(GameTestHelper helper, Mob boss, Player who, float amount) {
		boss.invulnerableTime = 0;
		return boss.hurt(helper.getLevel().damageSources().playerAttack(who), amount);
	}

	private static <T extends Mob> T place(GameTestHelper helper, EntityType<T> type, boolean noAi) {
		T boss = type.create(helper.getLevel());
		boss.moveTo(helper.absoluteVec(new Vec3(2.5, 2, 2.5)), 0f, 0f);
		boss.setNoAi(noAi);
		helper.getLevel().addFreshEntity(boss);
		return boss;
	}

	// ---------------------------------------------------------------- the rule, on one boss

	@GameTest(template = EMPTY_STRUCTURE, batch = "boss_threat_rule")
	public void threatSwitchNeedsTwentyPercentAndDecayLetsTheOldTargetBack(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		BoneTyrant boss = place(helper, HordeEntityTypes.BONE_TYRANT, true);
		BossThreat t = boss.threat();
		Player a = attacker(helper, boss, new Vec3(3, 0, 0));
		Player b = attacker(helper, boss, new Vec3(-3, 0, 0));

		helper.assertTrue(hit(helper, boss, a, 10f), "A's hit lands");
		t.applyNow();
		helper.assertTrue(boss.getTarget() == a, "no target -> the only attacker, got " + boss.getTarget());

		t.ageForTest(BossThreat.SWITCH_LOCKOUT_TICKS); // out of the post-switch lockout, threat barely decayed
		float aNow = t.threatOf(a);
		hit(helper, boss, b, aNow * 1.1f);
		t.applyNow();
		helper.assertTrue(boss.getTarget() == a, "B at 110% of A is inside the 20% margin -- no flip-flop");

		hit(helper, boss, b, aNow * 0.3f); // B now ~140% of A
		t.applyNow();
		helper.assertTrue(boss.getTarget() == b, "B well past A's threat takes the boss, got " + boss.getTarget()
				+ " (A " + t.threatOf(a) + ", B " + t.threatOf(b) + ")");

		hit(helper, boss, a, 100f);
		t.applyNow();
		helper.assertTrue(boss.getTarget() == b, "the 1.5 s lockout holds right after a switch");

		// A goes quiet for a long time while B keeps chipping: A's threat decays away and B is back on top...
		t.ageForTest(BossThreat.HALF_LIFE_TICKS * 5);
		hit(helper, boss, b, 6f);
		t.applyNow();
		helper.assertTrue(boss.getTarget() == b, "B stays");
		// ...and then decay lets A back in with a modest hit once B in turn stops
		t.ageForTest(BossThreat.HALF_LIFE_TICKS * 4);
		hit(helper, boss, a, 8f);
		t.applyNow();
		helper.assertTrue(boss.getTarget() == a, "decayed B (" + t.threatOf(b) + ") loses to A's fresh " + t.threatOf(a));

		float before = t.threatOf(a);
		t.ageForTest(BossThreat.HALF_LIFE_TICKS);
		helper.assertTrue(Math.abs(t.threatOf(a) - before * 0.5f) < 0.01f, "threat halves every half-life");
		boss.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "boss_threat_rule")
	public void creativeOrFarAttackersFallOffTheTable(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		BoneTyrant boss = place(helper, HordeEntityTypes.BONE_TYRANT, true);
		BossThreat t = boss.threat();
		Player a = attacker(helper, boss, new Vec3(3, 0, 0));
		Player b = attacker(helper, boss, new Vec3(-3, 0, 0));
		hit(helper, boss, a, 4f);
		hit(helper, boss, b, 40f);
		t.applyNow();
		helper.assertTrue(boss.getTarget() == b, "B is the big threat");

		b.getAbilities().invulnerable = true; // went creative
		t.ageForTest(BossThreat.SWITCH_LOCKOUT_TICKS);
		t.applyNow();
		helper.assertTrue(boss.getTarget() == a, "a creative B drops off the table, got " + boss.getTarget());
		helper.assertTrue(t.threatOf(b) == 0f, "and is forgotten");

		b.getAbilities().invulnerable = false;
		hit(helper, boss, b, 40f);
		t.ageForTest(BossThreat.SWITCH_LOCKOUT_TICKS);
		t.applyNow();
		helper.assertTrue(boss.getTarget() == b, "back in survival and hitting hard: B again");
		b.moveTo(boss.position().add(t.range() + 20, 0, 0));
		t.applyNow();
		helper.assertTrue(boss.getTarget() == a, "B ran beyond the boss's range -> A, got " + boss.getTarget());
		boss.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "boss_threat_rule")
	public void emptyTableLeavesTheBossesOwnTargetAlone(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		BoneTyrant boss = place(helper, HordeEntityTypes.BONE_TYRANT, true);
		Player a = attacker(helper, boss, new Vec3(3, 0, 0));
		boss.setTarget(a);
		helper.assertTrue(boss.threat().applyNow() == null && boss.getTarget() == a, "nobody hurt him: his own pick stands");
		boss.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- the real tick, with the boss's own AI running

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "boss_threat_carnage")
	public void carnageTurnsOnTheHeavyHitterWhileTheLureRuns(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		CarnageEntity boss = place(helper, CarnageEntityTypes.CARNAGE, false);
		boss.configure(1);
		Player lure = attacker(helper, boss, new Vec3(0, 0, 14));
		Player hitter = attacker(helper, boss, new Vec3(0, 0, -3));
		// the lure pokes him first: the old HurtByTargetGoal started on the lure and then ignored everyone else
		hit(helper, boss, lure, 2f);
		boss.setTarget(lure);
		helper.runAfterDelay(2, () -> hit(helper, boss, hitter, 12f));
		helper.runAfterDelay(5, () -> hit(helper, boss, hitter, 12f));
		helper.succeedWhen(() -> {
			helper.assertTrue(boss.getTarget() == hitter, "Carnage should have turned on the hitter, has " + boss.getTarget());
			boss.discard();
		});
	}

	// ---------------------------------------------------------------- every wired boss

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "boss_threat_all")
	public void everyWiredBossTurnsOnWhoeverHurtsIt(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		record Case(EntityType<? extends Mob> type, Function<Mob, BossThreat> table) {
		}
		List<Case> cases = List.of(
				new Case(CarnageEntityTypes.CARNAGE, m -> ((CarnageEntity) m).threat()),
				new Case(HordeEntityTypes.BONE_TYRANT, m -> ((BoneTyrant) m).threat()),
				new Case(HordeEntityTypes.BROOD_QUEEN, m -> ((BroodQueen) m).threat()),
				new Case(SyndicateEntityTypes.KINGPIN, m -> ((KingpinEntity) m).threat()),
				new Case(UltronEntityTypes.SENTRY, m -> ((UltronSentryEntity) m).threat()),
				new Case(UltronEntityTypes.PRIME, m -> ((UltronPrimeEntity) m).threat()),
				new Case(BehemothEntityTypes.ABYSSAL_BEHEMOTH, m -> ((AbyssalBehemothEntity) m).threat()),
				new Case(RaidEntityTypes.EMPOWERED_ZOMBIE, m -> ((EmpoweredZombie) m).threat()));
		StringBuilder failures = new StringBuilder();
		for (Case c : cases) {
			Mob boss = place(helper, c.type(), true);
			Player lure = attacker(helper, boss, new Vec3(6, 0, 0));
			Player hitter = attacker(helper, boss, new Vec3(-4, 0, 0));
			boss.setTarget(lure);
			boolean landed = hit(helper, boss, hitter, 8f);
			c.table().apply(boss).applyNow();
			if (!landed || boss.getTarget() != hitter) {
				failures.append(EntityType.getKey(c.type())).append(landed ? " kept " + boss.getTarget() : " took no damage").append("; ");
			}
			boss.discard();
		}
		helper.assertTrue(failures.isEmpty(), "bosses that ignored the hitter: " + failures);
		helper.succeed();
	}
}
