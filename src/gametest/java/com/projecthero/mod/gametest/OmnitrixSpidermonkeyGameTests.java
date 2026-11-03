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
		k.press(p, AbilitySlot.SLOT_3, false);
		helper.assertTrue(SpidermonkeyKit.swinging(p), "X down: swinging");
		int cd = k.cooldownLeft(p, SpidermonkeyKit.WEB_SWING);
		helper.assertTrue(cd > 0, "the swing cooled down");
		helper.assertTrue(SpidermonkeyKit.lineMode(p) == 1 && SpidermonkeyKit.hook(p) != null && SpidermonkeyKit.rope(p) >= 3.0,
				"the swing line is synced for his client to swing on");
		helper.assertTrue(k.keyActive(p, AbilitySlot.SLOT_3), "X lights up while he swings");
		p.setOnGround(false);
		k.release(p, AbilitySlot.SLOT_3, false, 20);
		helper.assertFalse(SpidermonkeyKit.swinging(p), "letting go of X lets go of the line");
		helper.assertTrue(SpidermonkeyKit.lineMode(p) == 0 && SpidermonkeyKit.hook(p) == null, "and the synced line is gone");
		helper.assertTrue(k.webSwing(p) && SpidermonkeyKit.swinging(p), "Monkey Bars: a re-catch in the air works on cooldown");
		helper.assertTrue(k.cooldownLeft(p, SpidermonkeyKit.WEB_SWING) == cd, "and costs no new cooldown");
		k.release(p, AbilitySlot.SLOT_3, false, 20);
		p.setOnGround(true);
		helper.assertFalse(k.webSwing(p), "from the ground it waits for the cooldown");
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "make the swing x and web zip shift+X please ... increase his web zip range to 75 blocks please and make the pull
	 * stronger aswell, also make the web swings range further aswell"): the keys, the ranges and the zip's pull.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeySwingOnXZipOnShiftX(GameTestHelper helper) {
		floor(helper);
		spidermonkey(helper, 0f);
		SpidermonkeyKit k = kit();
		AlienMove x = k.moveOn(AbilitySlot.SLOT_3, false, false), sx = k.moveOn(AbilitySlot.SLOT_3, true, false);
		helper.assertTrue(x != null && SpidermonkeyKit.WEB_SWING.equals(x.id()), "Web Swing is on X");
		helper.assertTrue(sx != null && SpidermonkeyKit.WEB_ZIP.equals(sx.id()), "Web Zip is on Shift+X");
		helper.assertTrue(SpidermonkeyKit.ZIP_RANGE == 75.0, "the zip reaches 75 blocks");
		helper.assertTrue(SpidermonkeyKit.SWING_RANGE >= com.projecthero.mod.miraculous.ladybug.LadybugConfig.SWING_RANGE
				&& SpidermonkeyKit.SWING_RANGE >= 45.0, "the swing reaches at least as far as Ladybug's (45): " + SpidermonkeyKit.SWING_RANGE);
		Vec3 eye = new Vec3(0, 64, 0);
		Vec3 pull = SpidermonkeyKit.zipVelocity(eye, eye.add(0, 10, 70));
		near(helper, (float) pull.length(), (float) SpidermonkeyKit.ZIP_SPEED, "the zip pulls at full speed all the way");
		helper.assertTrue(pull.length() > com.projecthero.mod.miraculous.ladybug.LadybugConfig.ZIP_SPEED && pull.length() >= 2.0,
				"a stronger pull than Ladybug's zip line (and the old 2.4 shove that died off): " + pull.length());
		helper.assertTrue(SpidermonkeyKit.ZIP_SPEED * SpidermonkeyKit.ZIP_MAX >= SpidermonkeyKit.ZIP_RANGE, "a full-range zip arrives in time");
		helper.assertTrue(SpidermonkeyKit.zipVelocity(eye, eye.add(0, 0, 1)).lengthSqr() == 0.0, "it lets go at the hook");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyZipLineRunsToTheHook(GameTestHelper helper) {
		floor(helper);
		// a wall at the far end to zip to
		for (int x = 0; x <= 7; x++) {
			for (int y = 2; y <= 6; y++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 9)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = spidermonkey(helper, -5f);
		SpidermonkeyKit k = kit();
		k.press(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(SpidermonkeyKit.zipping(p) && !SpidermonkeyKit.swinging(p), "Shift+X: on a zip line");
		helper.assertTrue(SpidermonkeyKit.lineMode(p) == 2, "synced as a zip");
		Vec3 hook = SpidermonkeyKit.hook(p);
		helper.assertTrue(hook != null && hook.distanceTo(p.getEyePosition()) < 9.0 && hook.distanceTo(p.getEyePosition()) > 6.0,
				"hooked on the wall 7.5 ahead: " + (hook == null ? null : helper.relativeVec(hook)));
		helper.assertTrue(k.cooldownLeft(p, SpidermonkeyKit.WEB_ZIP) == SpidermonkeyKit.ZIP_COOLDOWN, "2 s cooldown");
		helper.assertTrue(k.allowDamage(p, helper.getLevel().damageSources().fall(), 10f) == false, "no fall damage on the line");
		k.release(p, AbilitySlot.SLOT_3, true, 2);
		helper.assertTrue(SpidermonkeyKit.zipping(p), "letting go of the keys doesn't stop a zip");
		// he arrives (the client moves him; here he is put there)
		p.moveTo(hook.x, hook.y - p.getEyeHeight(), hook.z - 1.0);
		k.tick(p, Omnitrix.state(p), p.level().getGameTime() + 3);
		helper.assertFalse(SpidermonkeyKit.zipping(p), "the zip ends at the hook");
		helper.assertTrue(SpidermonkeyKit.lineMode(p) == 0, "and the synced line is gone");
		helper.assertFalse(k.webZip(p), "a second zip waits for the cooldown");
		helper.succeed();
	}

	/** v0.17.2 (user: "make spidermonkeys webs shoot out from his tail, thats how it works in the show"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyWebsLeaveFromHisTail(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = spidermonkey(helper, 0f);
		p.yBodyRot = 0f; // facing +z
		SpidermonkeyKit k = kit();
		Vec3 tip = SpidermonkeyKit.tailTip(p);
		helper.assertTrue(tip.z < p.getZ() - 0.2, "the tail tip is behind him: " + (tip.z - p.getZ()));
		helper.assertTrue(tip.y > p.getY() + 0.75 * p.getScale() * 0.9, "above his hips: " + (tip.y - p.getY()));
		helper.assertTrue(tip.y < p.getEyeY(), "below his eyes");
		helper.assertTrue(k.webShot(p), "Web Shot");
		var shots = helper.getLevel().getEntitiesOfClass(com.projecthero.mod.omnitrix.entity.AlienProjectileEntity.class, p.getBoundingBox().inflate(3),
				e -> "spidermonkey_web".equals(e.kind()));
		helper.assertTrue(shots.size() == 1, "one glob: " + shots.size());
		helper.assertTrue(shots.get(0).position().distanceTo(tip) < 0.05, "it leaves his tail tip, not his eyes: " + shots.get(0).position().distanceTo(tip));
		helper.assertTrue(shots.get(0).getDeltaMovement().z > 1.5, "flying where he looks");
		shots.get(0).discard();
		helper.assertTrue(k.webBarrage(p), "Web Barrage");
		var fan = helper.getLevel().getEntitiesOfClass(com.projecthero.mod.omnitrix.entity.AlienProjectileEntity.class, p.getBoundingBox().inflate(3),
				e -> "spidermonkey_web".equals(e.kind()));
		helper.assertTrue(fan.size() == 6 && fan.stream().allMatch(e -> e.position().distanceTo(tip) < 0.05), "all six from the tail tip");
		fan.forEach(net.minecraft.world.entity.Entity::discard);
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
		near(helper, v.getHealth(), 140f - SpidermonkeyKit.KICK_DAMAGE, "the kick lands for 26");
		helper.assertFalse(SpidermonkeyKit.kicking(p), "the kick is over");
		// on the ground: the old reel-in
		Villager w = prey(helper, 1.5, 2, 6.5, 140f);
		ServerPlayer q = spidermonkey(helper, 8f);
		q.moveTo(helper.absoluteVec(new Vec3(1.5, 2, 1.5)).x, q.getY(), q.getZ(), 0f, 8f);
		q.setOnGround(true);
		helper.assertTrue(k.webYank(q) && !SpidermonkeyKit.kicking(q), "on the ground Web Yank reels it in");
		near(helper, w.getHealth(), 140f - SpidermonkeyKit.YANK_DAMAGE, "20");
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
		near(helper, v.getHealth(), 140f - SpidermonkeyKit.COCOON_SLAM, "and slammed for 25");
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
		near(helper, v.getHealth(), 140f - SpidermonkeyKit.FISTS_BONUS, "the fourth: all four fists, +6");
		SpidermonkeyKit.webbed(p, v, 40);
		v.invulnerableTime = 0;
		k.onAttack(p, v);
		near(helper, v.getHealth(), 140f - SpidermonkeyKit.FISTS_BONUS - SpidermonkeyKit.STICKY_BONUS, "Sticky: +2 on a webbed foe");
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
