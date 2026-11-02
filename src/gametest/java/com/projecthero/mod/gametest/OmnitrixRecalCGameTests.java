package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.brainstorm.BrainstormKit;
import com.projecthero.mod.omnitrix.alien.goop.GoopKit;
import com.projecthero.mod.omnitrix.alien.spidermonkey.SpidermonkeyKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 the Recalibrated Omnitrix, wave C (docs/OMNITRIX_RECALIBRATED_0172.md): Brainstorm, Spidermonkey and Goop -- each on a
 * RECALIBRATED watch: the body, the signature (Brainstorm's chain lightning jumping, Spidermonkey's web rooting a mob and his
 * wall crawl, Goop's half physical damage and the projector knock-off), one move's damage and cooldown, and that turning back
 * leaves nothing behind. Mock players are not ticked: tests drive {@link Omnitrix#tick} or the kit's tick themselves.
 */
public final class OmnitrixRecalCGameTests implements FabricGameTest {
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

	/** A mock on the RECALIBRATED watch, transformed into {@code alien}. */
	private static ServerPlayer as(GameTestHelper helper, String alien) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.recalibrated(p), "it is the recalibrated watch");
		Omnitrix.select(p, alien);
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + alien);
		helper.assertTrue(alien.equals(Omnitrix.active(p)), "the active alien is " + alien);
		p.setOnGround(true);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, float health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void floor(GameTestHelper helper, int depth) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= depth; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static boolean rooted(LivingEntity e) {
		var s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9;
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05f, what + " (expected " + expected + ", got " + actual + ")");
	}

	private static void noHN(GameTestHelper helper, com.projecthero.mod.omnitrix.alien.AlienKit kit) {
		helper.assertTrue(kit.moves().stream().noneMatch(m -> m.slot() == AbilitySlot.SLOT_7 || m.slot() == AbilitySlot.SLOT_8), kit.id() + ": nothing on H / N");
		helper.assertTrue(kit.moveOn(AbilitySlot.SLOT_5, false, false) != null, kit.id() + ": a V move");
	}

	// ================================================================ Brainstorm

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormBodyAndLightningImmunity(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, BrainstormKit.ID);
		BrainstormKit kit = (BrainstormKit) Aliens.kit(BrainstormKit.ID);
		noHN(helper, kit);
		near(helper, p.getMaxHealth(), 22f, "22 health");
		near(helper, p.getScale(), 0.9f, "0.9x the size");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 6, "armour 6 (" + p.getAttributeValue(Attributes.ARMOR) + ")");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().lightningBolt(), 6f);
		near(helper, p.getHealth(), hp, "lightning never hurts him");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormChainLightningJumpsFromFoeToFoe(GameTestHelper helper) {
		floor(helper, 13);
		ServerPlayer p = as(helper, BrainstormKit.ID);
		BrainstormKit kit = (BrainstormKit) Aliens.kit(BrainstormKit.ID);
		Zombie a = zombie(helper, 3.5, 2, 4.5, 140f);
		Zombie b = zombie(helper, 3.5, 2, 8.0, 140f);
		Zombie c = zombie(helper, 3.5, 2, 11.5, 140f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(com.projecthero.mod.omnitrix.alien.AlienKit.lookTarget(p, BrainstormKit.BOLT_RANGE) == a, "the first zombie is under the crosshair");
		helper.assertTrue(kit.chainLightning(p), "Chain Lightning");
		near(helper, a.getHealth(), 140f - BrainstormKit.CHAIN_FIRST, "the first takes 14");
		near(helper, b.getHealth(), 140f - BrainstormKit.CHAIN_DAMAGE, "it jumps to the second: 12");
		near(helper, c.getHealth(), 140f - BrainstormKit.CHAIN_DAMAGE, "and on to the third: 12");
		helper.assertFalse(kit.chainLightning(p), "it cools down");
		helper.assertTrue(kit.cooldownLeft(p, BrainstormKit.CHAIN_LIGHTNING) == 160, "an 8 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormBoltHitsAndCoolsDown(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, BrainstormKit.ID);
		BrainstormKit kit = (BrainstormKit) Aliens.kit(BrainstormKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 5.0, 140f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(kit.lightningBolt(p), "Lightning Bolt");
		near(helper, z.getHealth(), 140f - BrainstormKit.BOLT_DAMAGE, "the bolt hits for 7");
		helper.assertFalse(kit.lightningBolt(p), "it cools down");
		helper.assertTrue(kit.cooldownLeft(p, BrainstormKit.BOLT) == 20, "a 1 s cooldown");
		// analysed: his moves hit it 25% harder
		helper.assertTrue(kit.analyze(p) && BrainstormKit.marked(p, z), "Genius Analysis marks it");
		float before = z.getHealth();
		z.invulnerableTime = 0;
		BrainstormKit.zap(p, z, 8f);
		// v0.17.2 revamp: the bolt left one Static charge; an analysed creature Overloads at 2 (+6 on that hit)
		near(helper, z.getHealth(), before - (8f + BrainstormKit.OVERLOAD_DAMAGE) * BrainstormKit.MARK_FACTOR, "+25% on an analysed creature (and an Overload)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormForceFieldStopsShotsAndRevertLeavesNothing(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, BrainstormKit.ID);
		BrainstormKit kit = (BrainstormKit) Aliens.kit(BrainstormKit.ID);
		helper.assertTrue(kit.forceField(p) && kit.flag(p, BrainstormKit.F_FIELD), "the Force Field goes up");
		var shot = helper.getLevel().damageSources().thrown(p, null);
		helper.assertFalse(kit.allowDamage(p, shot, 5f), "projectiles never get through");
		near(helper, kit.incomingDamage(p, helper.getLevel().damageSources().magic(), 10f), 10f * BrainstormKit.FIELD_TAKEN, "other damage cut by 60%");
		helper.assertTrue(kit.electricStorm(p) && BrainstormKit.storming(p), "the Electric Storm gathers");
		helper.assertTrue(kit.megaBolt(p) && kit.flag(p, BrainstormKit.F_CHARGE), "the Mega Bolt charges");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
		helper.assertFalse(BrainstormKit.storming(p), "the storm is gone");
		helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags left");
		near(helper, p.getScale(), 1f, "full size again");
		near(helper, p.getMaxHealth(), 20f, "a human's health");
		helper.succeed();
	}

	// ================================================================ Spidermonkey

	@GameTest(template = EMPTY_STRUCTURE)
	public void spidermonkeyBodyAndWallCrawl(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, SpidermonkeyKit.ID);
		SpidermonkeyKit kit = (SpidermonkeyKit) Aliens.kit(SpidermonkeyKit.ID);
		noHN(helper, kit);
		near(helper, p.getMaxHealth(), 22f, "22 health");
		near(helper, p.getScale(), 0.85f, "0.85x the size");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.1 * 1.2, "quick on his feet");
		helper.assertTrue(kit.crawlOn(p), "Wall Crawl is on when he transforms");
		p.horizontalCollision = true;
		helper.assertTrue(kit.climbs(p) && p.onClimbable(), "pressed into a wall he climbs it");
		p.horizontalCollision = false;
		helper.assertFalse(kit.climbs(p), "on open ground he does not hang like on a ladder");
		helper.assertTrue(kit.wallCrawl(p) && !kit.crawlOn(p), "V turns it off");
		p.horizontalCollision = true;
		helper.assertFalse(kit.climbs(p), "off: no climbing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void spidermonkeyWebShotRootsAMob(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, SpidermonkeyKit.ID);
		SpidermonkeyKit kit = (SpidermonkeyKit) Aliens.kit(SpidermonkeyKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 5.0, 140f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 12f);
		helper.assertTrue(kit.webShot(p), "Web Shot");
		helper.assertFalse(kit.webShot(p), "it cools down");
		helper.assertTrue(kit.cooldownLeft(p, SpidermonkeyKit.WEB_SHOT) == 20, "a 1 s cooldown");
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(rooted(z), "the web stuck the zombie in place: " + z.getActiveEffects());
			near(helper, z.getHealth(), 140f - SpidermonkeyKit.SHOT_DAMAGE, "the web shot hits for 6");
			// the flurry's fists, one at a time (up close)
			z.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180f, 0f);
			p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 15f);
			z.invulnerableTime = 0;
			helper.assertTrue(kit.flurryHit(p, 2) == 1, "a Quad Flurry fist lands");
			// v0.17.2 revamp: Sticky -- the zombie is still webbed, so the fist hits it for 1 more
			near(helper, z.getHealth(), 140f - SpidermonkeyKit.SHOT_DAMAGE - SpidermonkeyKit.FLURRY_HIT - SpidermonkeyKit.STICKY_BONUS, "3.5 a fist (+1 Sticky)");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void spidermonkeyRevertLeavesNothing(GameTestHelper helper) {
		floor(helper, 7);
		// a ceiling to swing from
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 8, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = as(helper, SpidermonkeyKit.ID);
		SpidermonkeyKit kit = (SpidermonkeyKit) Aliens.kit(SpidermonkeyKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 5.0, 140f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 12f);
		helper.assertTrue(kit.cocoonCrush(p) && SpidermonkeyKit.cocooned(z) && rooted(z), "Cocoon Crush wraps the zombie");
		helper.assertTrue(kit.webShield(p), "Web Shield");
		helper.assertTrue(kit.webTrap(p), "Web Trap");
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, -20f);
		helper.assertTrue(kit.webSwing(p) && SpidermonkeyKit.swinging(p) && kit.flag(p, SpidermonkeyKit.F_SWING), "Web Swing from the ceiling");
		helper.assertTrue(SpidermonkeyKit.leftovers(p) >= 4, "everything is running: " + SpidermonkeyKit.leftovers(p));
		pump(helper, p);
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(z.getHealth() < 140f - SpidermonkeyKit.COCOON_PULSE_DAMAGE + 0.05f, "the cocoon squeezes: " + z.getHealth());
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
			helper.assertTrue(SpidermonkeyKit.leftovers(p) == 0, "no swing, trap, shield or cocoon left: " + SpidermonkeyKit.leftovers(p));
			helper.assertFalse(SpidermonkeyKit.cocooned(z), "the cocoon is gone");
			helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags left");
			near(helper, p.getScale(), 1f, "full size again");
			p.horizontalCollision = true;
			helper.assertFalse(p.onClimbable(), "no wall crawling as a human");
			helper.succeed();
		});
	}

	// ================================================================ Goop

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopHalvesPhysicalDamage(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, GoopKit.ID);
		GoopKit kit = (GoopKit) Aliens.kit(GoopKit.ID);
		noHN(helper, kit);
		near(helper, p.getMaxHealth(), 26f, "26 health");
		Zombie z = zombie(helper, 3.5, 2, 5.0, 40f);
		var src = helper.getLevel().damageSources();
		near(helper, kit.incomingDamage(p, src.mobAttack(z), 6f), 3f, "a zombie's blow lands at half");
		near(helper, kit.incomingDamage(p, src.magic(), 6f), 6f, "magic is not halved");
		near(helper, kit.incomingDamage(p, src.inFire(), 4f), 6f, "fire dries him out: x1.5");
		helper.assertFalse(kit.allowDamage(p, src.fall(), 10f), "no fall damage");
		helper.assertFalse(kit.projectorOff(p), "small hits leave the projector on");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopsProjectorIsKnockedOffAndPickedUp(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, GoopKit.ID);
		GoopKit kit = (GoopKit) Aliens.kit(GoopKit.ID);
		p.hurt(helper.getLevel().damageSources().magic(), 10f);
		helper.assertTrue(kit.projectorOff(p), "a big hit knocks the projector off");
		Vec3 at = GoopKit.projectorAt(p);
		helper.assertTrue(at != null, "it lies on the ground somewhere");
		helper.assertTrue(p.getScale() < 0.5f, "he slumps into a puddle (" + p.getScale() + ")");
		helper.assertFalse(kit.acidSpit(p), "no moves without it");
		helper.assertFalse(kit.slimeSlam(p), "none at all");
		// flow back over it
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		kit.tick(p, Omnitrix.state(p), p.level().getGameTime());
		helper.assertFalse(kit.projectorOff(p), "back under the projector");
		near(helper, p.getScale(), 1f, "back to his size");
		helper.assertTrue(GoopKit.leftovers(p) == 0, "the dropped projector is gone");
		helper.assertTrue(kit.acidSpit(p), "and the moves work again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopsSlamHitsAndCoolsDownAndSqueezeShrinks(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, GoopKit.ID);
		GoopKit kit = (GoopKit) Aliens.kit(GoopKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 140f);
		helper.assertTrue(kit.slimeSlam(p), "Slime Slam");
		near(helper, z.getHealth(), 140f - GoopKit.SLAM_DAMAGE, "the slam hits for 17");
		helper.assertFalse(kit.slimeSlam(p), "it cools down");
		helper.assertTrue(kit.cooldownLeft(p, GoopKit.SLIME_SLAM) == 200, "a 10 s cooldown");
		helper.assertTrue(kit.squeeze(p) && kit.flag(p, GoopKit.F_SQUEEZE), "Squeeze");
		p.refreshDimensions();
		helper.assertTrue(p.getBbHeight() < 1.0f, "short enough for a one-block gap (" + p.getBbHeight() + ")");
		helper.assertTrue(kit.squeeze(p) && !kit.flag(p, GoopKit.F_SQUEEZE), "Shift+X again pops him back");
		near(helper, p.getScale(), 1f, "full size");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopRevertLeavesNothing(GameTestHelper helper) {
		floor(helper, 7);
		ServerPlayer p = as(helper, GoopKit.ID);
		GoopKit kit = (GoopKit) Aliens.kit(GoopKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.0, 140f);
		helper.assertTrue(kit.acidFlood(p), "Acid Flood");
		helper.assertTrue(kit.pulsePuddlesNow(p) >= 1, "the flood burns the zombie standing in it");
		helper.assertTrue(z.getHealth() < 140f, "it took acid damage");
		helper.assertTrue(kit.squeeze(p), "Squeeze");
		p.hurt(helper.getLevel().damageSources().magic(), 10f);
		helper.assertTrue(kit.projectorOff(p), "projector knocked off");
		helper.assertTrue(GoopKit.leftovers(p) >= 2, "a puddle and a projector on the ground");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
		helper.assertTrue(GoopKit.leftovers(p) == 0, "no puddles, engulfed creature or projector left");
		helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags left");
		near(helper, p.getScale(), 1f, "no squeeze or puddle scale left");
		near(helper, (float) p.getAttributeValue(Attributes.MOVEMENT_SPEED), (float) p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED),
				"no speed modifier left");
		helper.succeed();
	}
}
