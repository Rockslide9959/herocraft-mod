package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.alienx.AlienXKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Alien X revamp (docs/OMNITRIX_REVAMP_ALIENX.md): the new key layout (Big Bang on Shift+Z), the real votes (one yes
 * carries, a unanimous vote empowers +25%, Big Bang needs both), Argue (once a debate, a no gets a second chance), the voices'
 * Council Favour (carried motions move it, it drifts back to 50) and the star-born body (no fire or cold). The vote is forced per
 * player; mock players are not ticked, so the tests pump {@link Omnitrix#tick}. The basic kit's tests are
 * OmnitrixRecalAlienXGameTests.
 */
public class OmnitrixAlienXGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer alienX(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, AlienXKit.ID), "Alien X is on its dial");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Alien X");
		p.setOnGround(true);
		p.refreshDimensions();
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static Villager prey(GameTestHelper helper, double x, double y, double z, float health) {
		Villager m = EntityType.VILLAGER.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05f, what + " (expected " + expected + ", got " + actual + ")");
	}

	private static AlienXKit kit() {
		return (AlienXKit) Aliens.kit(AlienXKit.ID);
	}

	private static final int T0 = AlienXKit.OPENING_TICKS + 4;

	@GameTest(template = EMPTY_STRUCTURE)
	public void alienXRevampLayoutVotesAndStarBornBody(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		AlienXKit k = kit();
		helper.assertTrue(AlienXKit.BIG_BANG.equals(k.moveOn(AbilitySlot.SLOT_4, true, false).id()), "Shift+Z is the ultimate, Big Bang");
		helper.assertTrue(AlienXKit.RIFT.equals(k.moveOn(AbilitySlot.SLOT_6, true, false).id()), "Reality Rift moved to Shift+C");
		helper.assertTrue(k.move(AlienXKit.ARGUE) != null && !k.move(AlienXKit.ARGUE).onSlot(), "Argue is a right-click move");
		helper.assertTrue(AlienXKit.kind(AlienXKit.RESTORE) == AlienXKit.KIND_SERENA && AlienXKit.kind(AlienXKit.REALITY_BOLT) == AlienXKit.KIND_BELLICUS
				&& AlienXKit.kind(AlienXKit.BLINK) == AlienXKit.KIND_NEUTRAL, "motion kinds");
		near(helper, AlienXKit.yesChance(true, AlienXKit.RESTORE, 50, false), AlienXKit.YES_OWN, "Serena likes Restore");
		near(helper, AlienXKit.yesChance(true, AlienXKit.REALITY_BOLT, 50, false), AlienXKit.YES_OTHER, "Serena dislikes the bolt");
		near(helper, AlienXKit.yesChance(false, AlienXKit.REALITY_BOLT, 100, false), AlienXKit.YES_OWN + AlienXKit.FAVOUR_SWING,
				"Bellicus at full favour: +20%");
		near(helper, AlienXKit.yesChance(true, AlienXKit.BLINK, 50, true), AlienXKit.YES_NEUTRAL + AlienXKit.ARGUE_BONUS, "arguing: +25%");
		helper.assertTrue(AlienXKit.carried(AlienXKit.BLINK, false, true) && !AlienXKit.carried(AlienXKit.BLINK, false, false), "one yes carries");
		helper.assertTrue(!AlienXKit.carried(AlienXKit.BIG_BANG, true, false) && AlienXKit.carried(AlienXKit.BIG_BANG, true, true),
				"Big Bang needs both");
		helper.assertTrue(AlienXKit.BANG_CORE <= 70f && AlienXKit.BANG_RIM >= 40f, "the ultimate in 40-70");
		var src = helper.getLevel().damageSources();
		helper.assertFalse(k.allowDamage(p, src.inFire(), 4f), "no fire damage");
		helper.assertFalse(k.allowDamage(p, src.freeze(), 4f), "no freezing damage");
		helper.assertTrue(k.allowDamage(p, src.magic(), 4f), "magic still hurts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
	public void alienXUnanimousVoteEmpowersTheMove(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 4.5, 100f);
		pump(helper, p);
		helper.runAtTickTime(T0, () -> {
			AlienXKit.forceVotes(p, true, true);
			kit().press(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(AlienXKit.votes(p) == 3, "both votes synced as yes");
		});
		helper.runAtTickTime(T0 + AlienXKit.DEBATE_TICKS + 3, () -> {
			near(helper, v.getHealth(), 100f - AlienXKit.BOLT_DAMAGE * AlienXKit.UNANIMOUS_BONUS, "a unanimous Reality Bolt hits 25% harder");
			helper.assertTrue(Math.abs(AlienXKit.favour(p, false) - (50 + AlienXKit.FAVOUR_UP)) <= 1, "Bellicus is pleased (" + AlienXKit.favour(p, false) + ")");
			helper.assertTrue(Math.abs(AlienXKit.favour(p, true) - (50 - AlienXKit.FAVOUR_DOWN)) <= 1, "Serena less so (" + AlienXKit.favour(p, true) + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
	public void alienXArguingSwaysANo(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 4.5, 100f);
		pump(helper, p);
		helper.runAtTickTime(T0, () -> {
			helper.assertFalse(kit().argue(p), "nothing to argue outside a debate");
			AlienXKit.forceVotes(p, false, false);
			kit().press(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(AlienXKit.votes(p) == 0, "both said no");
			AlienXKit.forceVotes(p, true, false); // the re-vote after Ben argues
			helper.assertTrue(kit().argue(p), "Ben argues");
			helper.assertTrue(AlienXKit.votes(p) == 1, "Serena is swayed");
			helper.assertTrue(kit().flag(p, AlienXKit.F_ARGUED), "the argued flag is synced");
			helper.assertFalse(kit().argue(p), "only once a debate");
		});
		helper.runAtTickTime(T0 + AlienXKit.DEBATE_TICKS + 3, () -> {
			near(helper, v.getHealth(), 100f - AlienXKit.BOLT_DAMAGE, "the swayed motion is carried: 17");
			helper.assertFalse(kit().flag(p, AlienXKit.F_ARGUED), "the flag goes with the debate");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void alienXBigBangNeedsAUnanimousVote(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 4.5, 200f);
		pump(helper, p);
		helper.runAtTickTime(T0, () -> {
			AlienXKit.forceVotes(p, false, true);
			kit().press(p, AbilitySlot.SLOT_4, true);
			helper.assertTrue(AlienXKit.BIG_BANG.equals(AlienXKit.debatedMove(p)), "Shift+Z puts the Big Bang to a vote");
		});
		int t1 = T0 + AlienXKit.DEBATE_TICKS + 3;
		helper.runAtTickTime(t1, () -> {
			near(helper, v.getHealth(), 200f, "Bellicus alone is not enough");
			int cd = kit().cooldownLeft(p, AlienXKit.BIG_BANG);
			helper.assertTrue(cd > AlienXKit.DENIED_TICKS && cd <= AlienXKit.BANG_DENIED_TICKS, "a refused Big Bang waits 10 s (" + cd + ")");
			kit().cooldown(p, AlienXKit.BIG_BANG, 0);
			AlienXKit.forceVotes(p, true, true);
			kit().press(p, AbilitySlot.SLOT_4, true);
		});
		helper.runAtTickTime(t1 + AlienXKit.DEBATE_TICKS + 3, () -> {
			helper.assertTrue(v.getHealth() <= 200f - AlienXKit.BANG_RIM, "the unanimous Big Bang lands (" + v.getHealth() + ")");
			helper.assertTrue(v.getHealth() >= 200f - AlienXKit.BANG_CORE - 0.05f, "never more than 70 -- no unanimous bonus on the ultimate");
			helper.assertTrue(kit().cooldownLeft(p, AlienXKit.BIG_BANG) > 2000, "its 2 minute cooldown runs");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void alienXFavourRisesWithCarriedMotionsAndDrifts(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		pump(helper, p);
		helper.runAtTickTime(T0, () -> {
			helper.assertTrue(AlienXKit.favour(p, true) == 50 && AlienXKit.favour(p, false) == 50, "both start at 50");
			AlienXKit.forceVotes(p, true, false);
			kit().press(p, AbilitySlot.SLOT_4, false); // Time Stop: Serena's kind
		});
		helper.runAtTickTime(T0 + AlienXKit.DEBATE_TICKS + 3, () -> {
			helper.assertTrue(Math.abs(AlienXKit.favour(p, true) - (50 + AlienXKit.FAVOUR_UP)) <= 1, "Serena is pleased (" + AlienXKit.favour(p, true) + ")");
			helper.assertTrue(Math.abs(AlienXKit.favour(p, false) - (50 - AlienXKit.FAVOUR_DOWN)) <= 1, "Bellicus is not");
			kit().setFavour(p, true, 70);
		});
		helper.runAtTickTime(T0 + AlienXKit.DEBATE_TICKS + 3 + AlienXKit.FAVOUR_DRIFT_TICKS * 2 + 2, () -> {
			int f = AlienXKit.favour(p, true);
			helper.assertTrue(f < 70 && f >= 67, "favour drifts back toward 50 (" + f + ")");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertTrue(Omnitrix.state(p).timers.keySet().stream().noneMatch(k -> k.contains("favour") || k.contains("debate_votes")),
					"turning back clears the council's state");
			helper.succeed();
		});
	}
}
