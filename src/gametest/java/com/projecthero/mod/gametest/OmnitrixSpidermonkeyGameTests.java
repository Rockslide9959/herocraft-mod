package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.spidermonkey.SpidermonkeyKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Spidermonkey revamp (docs/OMNITRIX_REVAMP_SPIDERMONKEY.md): the keys (Cocoon Crush on Shift+Z, Web Trap on C, nothing on
 * H / N), the held Web Swing and its Monkey Bars re-catch, the Swing Kick off Web Yank in the air, the cocoon's slam, Ceiling Hang,
 * Four Fists, the fire and Tangled weaknesses, and that turning back leaves nothing. Mock players are not ticked: tests drive the
 * kit themselves.
 */
public class OmnitrixSpidermonkeyGameTests implements FabricGameTest {
	private static ServerPlayer spidermonkey(GameTestHelper helper, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, pitch);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, SpidermonkeyKit.ID), "selected Spidermonkey");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Spidermonkey");
		p.setOnGround(true);
		return p;
	}

	private static SpidermonkeyKit kit() {
		return (SpidermonkeyKit) Aliens.kit(SpidermonkeyKit.ID);
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static void ceiling(GameTestHelper helper, int y) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyKeysUltOnShiftZ(GameTestHelper helper) {
		floor(helper);
		spidermonkey(helper, 0f);
		SpidermonkeyKit k = kit();
		AlienMove ult = k.moveOn(AbilitySlot.SLOT_4, true, false);
		helper.assertTrue(ult != null && SpidermonkeyKit.COCOON_CRUSH.equals(ult.id()), "the ultimate is on Shift+Z");
		AlienMove trap = k.moveOn(AbilitySlot.SLOT_6, false, false);
		helper.assertTrue(trap != null && SpidermonkeyKit.WEB_TRAP.equals(trap.id()), "Web Trap is on C");
		helper.assertTrue(k.move(SpidermonkeyKit.CEILING_HANG) != null && k.move(SpidermonkeyKit.FOUR_FISTS) != null, "Ceiling Hang and Four Fists");
		helper.assertTrue(k.moves().stream().noneMatch(m -> m.slot() == AbilitySlot.SLOT_7 || m.slot() == AbilitySlot.SLOT_8), "nothing on H / N");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyHeldSwingAndMonkeyBars(GameTestHelper helper) {
		floor(helper);
		ceiling(helper, 8);
		ServerPlayer p = spidermonkey(helper, -20f);
		SpidermonkeyKit k = kit();
		helper.assertTrue(k.webSwing(p) && SpidermonkeyKit.swinging(p), "Shift+X down: swinging");
		int cd = k.cooldownLeft(p, SpidermonkeyKit.WEB_SWING);
		helper.assertTrue(cd > 0, "the swing cooled down");
		p.setOnGround(false);
		k.release(p, AbilitySlot.SLOT_3, true, 20);
		helper.assertFalse(SpidermonkeyKit.swinging(p), "letting go of the keys lets go of the line");
		helper.assertTrue(k.webSwing(p) && SpidermonkeyKit.swinging(p), "Monkey Bars: a re-catch in the air works on cooldown");
		helper.assertTrue(k.cooldownLeft(p, SpidermonkeyKit.WEB_SWING) == cd, "and costs no new cooldown");
		k.release(p, AbilitySlot.SLOT_3, true, 20);
		p.setOnGround(true);
		helper.assertFalse(k.webSwing(p), "from the ground it waits for the cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeySwingKickInTheAirYankOnTheGround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = spidermonkey(helper, 8f);
		SpidermonkeyKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 140f);
		p.setOnGround(false);
		helper.assertTrue(k.webYank(p) && SpidermonkeyKit.kicking(p), "in the air Web Yank becomes a Swing Kick");
		near(helper, v.getHealth(), 140f, "nothing lands until he arrives");
		k.kickLands(p, v);
		near(helper, v.getHealth(), 140f - SpidermonkeyKit.KICK_DAMAGE, "the kick lands for 15");
		helper.assertFalse(SpidermonkeyKit.kicking(p), "the kick is over");
		// on the ground: the old reel-in
		Villager w = prey(helper, 1.5, 2, 6.5, 140f);
		ServerPlayer q = spidermonkey(helper, 8f);
		q.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)).x, q.getY(), q.getZ(), 0f, 8f);
		q.setOnGround(true);
		helper.assertTrue(k.webYank(q) && !SpidermonkeyKit.kicking(q), "on the ground Web Yank reels it in");
		near(helper, w.getHealth(), 140f - SpidermonkeyKit.YANK_DAMAGE, "12");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyCocoonEndsInASlam(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = spidermonkey(helper, 8f);
		SpidermonkeyKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 140f);
		long now = p.level().getGameTime();
		helper.assertTrue(k.cocoonCrush(p) && SpidermonkeyKit.cocooned(v), "Cocoon Crush wraps it");
		k.tick(p, Omnitrix.state(p), now + SpidermonkeyKit.COCOON_TICKS);
		helper.assertFalse(SpidermonkeyKit.cocooned(v), "the cocoon is done");
		near(helper, v.getHealth(), 140f - SpidermonkeyKit.COCOON_SLAM, "and slammed for 14");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyHangsFromCeilings(GameTestHelper helper) {
		floor(helper);
		ceiling(helper, 4);
		ServerPlayer p = spidermonkey(helper, 0f);
		SpidermonkeyKit k = kit();
		// hanging just under the ceiling: his head (1.53 tall at 0.85) a quarter-block below it
		p.moveTo(p.getX(), p.getY() + 0.4, p.getZ());
		p.setOnGround(false);
		p.setShiftKeyDown(true);
		helper.assertTrue(k.hangs(p) && k.climbs(p), "sneaking under a ceiling he hangs from it");
		p.setShiftKeyDown(false);
		helper.assertFalse(k.hangs(p), "not without sneaking");
		p.setShiftKeyDown(true);
		helper.assertTrue(k.wallCrawl(p) && !k.hangs(p), "or with Wall Crawl off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyFourFistsEveryFourthPunch(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = spidermonkey(helper, 0f);
		SpidermonkeyKit k = kit();
		Villager v = prey(helper, 3.5, 2, 3.0, 140f);
		for (int i = 0; i < 3; i++) {
			k.onAttack(p, v);
		}
		near(helper, v.getHealth(), 140f, "three plain punches add nothing (vanilla's punch is not part of this)");
		k.onAttack(p, v);
		near(helper, v.getHealth(), 140f - SpidermonkeyKit.FISTS_BONUS, "the fourth: all four fists, +4");
		SpidermonkeyKit.webbed(p, v, 40);
		v.invulnerableTime = 0;
		k.onAttack(p, v);
		near(helper, v.getHealth(), 140f - SpidermonkeyKit.FISTS_BONUS - SpidermonkeyKit.STICKY_BONUS, "Sticky: +1 on a webbed foe");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyFireBurnsWebsAndHeTanglesHimself(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = spidermonkey(helper, 90f);
		SpidermonkeyKit k = kit();
		Villager v = prey(helper, 5.5, 2, 5.5, 40f);
		v.igniteForSeconds(5);
		SpidermonkeyKit.webbed(p, v, 40);
		helper.assertFalse(SpidermonkeyKit.stuck(v), "a burning creature burns through the web");
		near(helper, k.incomingDamage(p, helper.getLevel().damageSources().inFire(), 4f), 6f, "fire x1.5");
		helper.assertTrue(k.webTrap(p), "a Web Trap at his own feet");
		k.tick(p, Omnitrix.state(p), p.level().getGameTime() + 1);
		var slow = p.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() == 1, "Tangled: his own web slows him");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyRevampRevertLeavesNothing(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = spidermonkey(helper, 8f);
		SpidermonkeyKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 140f);
		k.onAttack(p, v);
		p.setOnGround(false);
		helper.assertTrue(k.webYank(p) && SpidermonkeyKit.kicking(p), "a Swing Kick in flight");
		k.tick(p, Omnitrix.state(p), p.level().getGameTime() + 1);
		helper.assertTrue(SpidermonkeyKit.leftovers(p) >= 3, "state is held: " + SpidermonkeyKit.leftovers(p));
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertTrue(SpidermonkeyKit.leftovers(p) == 0, "no kick, punch count or tick guard left: " + SpidermonkeyKit.leftovers(p));
		helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags left");
		helper.succeed();
	}
}
