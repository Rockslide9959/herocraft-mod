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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Alien X on the Recalibrated Omnitrix (docs/OMNITRIX_RECALIBRATED_0172.md) -- his body, the 5 s opening debate (frozen,
 * no move works, and it ends), a debated move both ways (the vote is forced per player: carried = the move's damage and its
 * cooldown, denied = nothing happens and a short cooldown), and that turning back leaves no freeze behind. Mock players are not
 * ticked by the server, so the tests pump {@link Omnitrix#tick} themselves (the kit runs once per game tick either way).
 */
public class OmnitrixRecalAlienXGameTests implements FabricGameTest {
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

	/** A recalibrated watch, Alien X selected and transformed. */
	private static ServerPlayer alienX(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.recalibrated(p), "it is the recalibrated watch");
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

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** A no-AI villager to hit (not a hostile: no other test's sweep lands on it). */
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

	private static boolean rooted(LivingEntity e) {
		var s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9;
	}

	private static AlienXKit kit() {
		return (AlienXKit) Aliens.kit(AlienXKit.ID);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void alienXBodyIsTallToughAndStarfilled(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		helper.assertTrue(AlienXKit.ID.equals(Omnitrix.active(p)), "Alien X is the active alien");
		near(helper, p.getMaxHealth(), 60f, "60 health");
		near(helper, (float) p.getAttributeValue(Attributes.ARMOR), 14f, "armour 14");
		near(helper, p.getScale(), 1.15f, "a little taller than a player");
		helper.assertTrue(kit().moves().stream().noneMatch(m -> m.onSlot() && (m.slot() == AbilitySlot.SLOT_7 || m.slot() == AbilitySlot.SLOT_8)),
				"no H or N moves");
		helper.assertTrue(kit().moveOn(AbilitySlot.SLOT_5, false, false) != null, "a V move");
		helper.assertTrue(kit().move(AlienXKit.BIG_BANG) != null && AlienXKit.BANG_CORE >= 40f && AlienXKit.BANG_CORE <= 70f
				&& AlienXKit.BANG_RIM >= 40f, "the ultimate sits in 40-70");
		// a Celestialsapien breathes nothing and falls without harm
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().fall(), 10f);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().drown(), 4f);
		near(helper, p.getHealth(), hp, "no fall or drowning damage");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 4f);
		helper.assertTrue(p.getHealth() < hp, "other damage still hurts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void theOpeningDebateFreezesHimBlocksMovesAndEnds(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 4.5, 100f);
		helper.assertTrue(AlienXKit.debating(p) && AlienXKit.openingDebate(p), "transforming opens with a debate");
		helper.assertTrue(AlienXKit.frozen(p), "frozen while it runs");
		near(helper, (float) p.getAttributeValue(Attributes.MOVEMENT_SPEED), 0f, "no walking");
		helper.assertTrue(kit().onAttack(p, v), "no punches either (the hit is swallowed)");
		pump(helper, p);
		helper.runAtTickTime(20, () -> {
			kit().press(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(AlienXKit.openingDebate(p) && AlienXKit.debatedMove(p) == null, "a move pressed mid-opening is refused");
			helper.assertTrue(kit().cooldownLeft(p, AlienXKit.REALITY_BOLT) == 0, "and costs nothing");
		});
		helper.runAtTickTime(AlienXKit.OPENING_TICKS + 4, () -> {
			helper.assertFalse(AlienXKit.debating(p), "the opening debate ends after 5 s");
			helper.assertFalse(AlienXKit.frozen(p), "the freeze comes off");
			helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.05, "he can walk again");
			near(helper, v.getHealth(), 100f, "the refused bolt never landed");
			helper.assertFalse(kit().onAttack(p, v), "punches work again");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void aDebatedMoveIsCarriedOrDenied(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 4.5, 100f);
		pump(helper, p);
		int t0 = AlienXKit.OPENING_TICKS + 4;
		// carried: Reality Bolt
		helper.runAtTickTime(t0, () -> {
			helper.assertFalse(AlienXKit.debating(p), "the opening debate is over");
			AlienXKit.forceNextRoll(p, true);
			kit().press(p, AbilitySlot.SLOT_1, false);
			helper.assertTrue(AlienXKit.debating(p) && !AlienXKit.openingDebate(p), "the bolt is put to a debate");
			helper.assertTrue(AlienXKit.REALITY_BOLT.equals(AlienXKit.debatedMove(p)), "the debated move is synced");
			helper.assertTrue(AlienXKit.frozen(p), "frozen while they argue");
			kit().press(p, AbilitySlot.SLOT_6, false);
			helper.assertTrue(AlienXKit.REALITY_BOLT.equals(AlienXKit.debatedMove(p)), "a second key mid-debate is refused");
		});
		helper.runAtTickTime(t0 + AlienXKit.DEBATE_TICKS - 5, () -> near(helper, v.getHealth(), 100f, "nothing happens until the vote"));
		helper.runAtTickTime(t0 + AlienXKit.DEBATE_TICKS + 3, () -> {
			helper.assertFalse(AlienXKit.debating(p) || AlienXKit.frozen(p), "the debate ends and the freeze comes off");
			near(helper, v.getHealth(), 100f - AlienXKit.BOLT_DAMAGE, "the carried Reality Bolt hits for 17");
			int cd = kit().cooldownLeft(p, AlienXKit.REALITY_BOLT);
			helper.assertTrue(cd > 40 && cd <= 50, "Reality Bolt's own 2.5 s cooldown runs (" + cd + ")");
			helper.assertTrue(kit().cooldownLeft(p, AlienXKit.SHOCKWAVE) == 0, "the refused key cost nothing");
			// denied: Time Stop
			AlienXKit.forceNextRoll(p, false);
			kit().press(p, AbilitySlot.SLOT_4, false);
			helper.assertTrue(AlienXKit.TIME_STOP.equals(AlienXKit.debatedMove(p)), "Time Stop is put to a debate");
		});
		helper.runAtTickTime(t0 + 2 * AlienXKit.DEBATE_TICKS + 8, () -> {
			helper.assertFalse(AlienXKit.debating(p) || AlienXKit.frozen(p), "a denied motion ends the debate too");
			helper.assertFalse(rooted(v), "the denied Time Stop froze nothing");
			int cd = kit().cooldownLeft(p, AlienXKit.TIME_STOP);
			helper.assertTrue(cd > 0 && cd <= AlienXKit.DENIED_TICKS, "a short cooldown, not Time Stop's 25 s (" + cd + ")");
			helper.assertTrue(Omnitrix.state(p).animId == AlienXKit.ANIM_DENIED, "the slump of a denied motion");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void timeStopFreezesCreaturesButBossesBriefly(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		Villager v = prey(helper, 3.5, 2, 5.5, 40f);
		Villager boss = prey(helper, 6.5, 2, 3.5, 400f);
		pump(helper, p);
		int t0 = AlienXKit.OPENING_TICKS + 4;
		helper.runAtTickTime(t0, () -> {
			AlienXKit.forceNextRoll(p, true);
			kit().press(p, AbilitySlot.SLOT_4, false);
		});
		helper.runAtTickTime(t0 + AlienXKit.DEBATE_TICKS + 3, () -> {
			helper.assertTrue(rooted(v), "a creature in range is frozen");
			helper.assertTrue(rooted(boss), "a boss too, for now");
			var bs = boss.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			var vs = v.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			helper.assertTrue(bs.getDuration() < vs.getDuration() / 2, "but the boss only briefly");
			helper.assertTrue(kit().cooldownLeft(p, AlienXKit.TIME_STOP) > AlienXKit.DENIED_TICKS, "Time Stop's long cooldown runs");
			helper.assertTrue(AlienXKit.pos(p, AlienXKit.P_STOP) != null, "the frozen moment's centre is synced for the clock-face");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void turningBackLeavesNoFreezeBehind(GameTestHelper helper) {
		ServerPlayer p = alienX(helper);
		double plainSpeed = p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		pump(helper, p);
		helper.runAtTickTime(10, () -> {
			helper.assertTrue(AlienXKit.frozen(p), "mid opening debate: frozen");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
			helper.assertFalse(AlienXKit.frozen(p), "the debate freeze is gone");
			var jump = p.getAttribute(Attributes.JUMP_STRENGTH);
			helper.assertTrue(jump.getModifiers().stream().noneMatch(m -> m.id().getPath().contains("alien_x")), "no jump freeze left");
			near(helper, (float) p.getAttributeValue(Attributes.MOVEMENT_SPEED), (float) plainSpeed, "a plain player's speed");
			helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags");
			helper.assertTrue(Omnitrix.state(p).timers.keySet().stream().noneMatch(k -> k.startsWith(AlienXKit.ID + ".debate")),
					"no debate timers");
			helper.assertFalse(p.getAbilities().mayfly, "no flight left");
		});
		helper.runAtTickTime(30, () -> {
			helper.assertFalse(AlienXKit.frozen(p), "and nothing freezes him later");
			helper.succeed();
		});
	}
}
