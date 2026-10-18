package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.QuirkKits;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.data.QuirkState;
import com.projecthero.mod.quirk.zerogravity.ZeroGravity;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityConfig;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityDebris;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityFloat;
import com.projecthero.mod.quirk.zerogravity.ZeroGravityMoves;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 Zero Gravity (Uravity): one test per move -- the server effect and its number -- plus the rules that matter
 * most: gravity always comes back (Release, stopAll, revoke, Nausea, the load-time safety net), lifted ground is put back
 * exactly, bosses never float. Mock players are not ticked by the server, so tests that run over time drive the Quirk
 * tick themselves ({@link #pump}).
 */
public class QuirkZeroGravityGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
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

	/** Uravity facing +Z, every move ready. */
	private static ServerPlayer uravity(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Quirks.grant(p, Quirk.ZERO_GRAVITY), "Zero Gravity is granted");
		ready(p);
		return p;
	}

	private static void ready(ServerPlayer p) {
		Quirks.edit(p, n -> {
			n.animId = QuirkState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Quirks.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static boolean floated(Mob m) {
		return m.isNoGravity() && m.getTags().contains(ZeroGravity.TAG_FLOAT);
	}

	private static boolean grounded(Mob m) {
		return !m.isNoGravity() && !m.getTags().contains(ZeroGravity.TAG_FLOAT);
	}

	// ---------------------------------------------------------------- R / Shift+R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void floatTouchFloatsTheTargetAndReleaseGivesGravityBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 3.5, 100);
		double y0 = z.getY();
		pump(helper, p);
		helper.assertTrue(ZeroGravityMoves.floatTouch(p) == z, "the touch lands on the zombie");
		helper.assertTrue(floated(z) && ZeroGravityFloat.count(p) == 1, "it floats");
		helper.assertTrue(100f - z.getHealth() >= ZeroGravityConfig.TOUCH_DAMAGE - EPS, "the touch hurts a little");
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(z.getY() > y0 + 0.5, "it has risen, y +" + (z.getY() - y0));
			helper.assertTrue(z.getNavigation().isDone(), "it cannot path");
			ready(p);
			helper.assertTrue(ZeroGravityMoves.release(p) == 1, "Release lets it go");
			helper.assertTrue(grounded(z) && ZeroGravityFloat.count(p) == 0, "its gravity is back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void floatTouchCapsAtFive(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		for (int i = 0; i < ZeroGravityConfig.TOUCH_MAX; i++) {
			Zombie z = zombie(helper, 0.5 + i * 1.5, 2.0, 6.5, 100);
			helper.assertTrue(ZeroGravityFloat.start(p, z, false), "floats #" + i);
		}
		Zombie sixth = zombie(helper, 4.5, 2.0, 3.0, 100);
		helper.assertTrue(ZeroGravityMoves.floatTouch(p) == null, "a sixth is refused");
		helper.assertTrue(grounded(sixth), "the sixth keeps its gravity");
		ZeroGravityMoves.endAll(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void floatFlingFloatsAndHurlsItTheWaySheLooks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 3.5, 100);
		helper.assertTrue(ZeroGravityMoves.floatFling(p) == z, "the fling lands");
		helper.assertTrue(floated(z), "it floats");
		helper.assertTrue(z.getDeltaMovement().z > ZeroGravityConfig.FLING_SPEED * 0.8, "flung along +Z: " + z.getDeltaMovement());
		ZeroGravityMoves.endAll(p);
		helper.assertTrue(grounded(z), "stopAll gives gravity back");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G / Shift+G

	@GameTest(template = EMPTY_STRUCTURE)
	public void releaseWithNothingFloatingDoesNothing(GameTestHelper helper) {
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		helper.assertTrue(ZeroGravityMoves.release(p) == 0, "nothing to release");
		helper.assertTrue(Quirks.ready(p, ZeroGravityConfig.RELEASE), "no cooldown spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void meteorSlamHurlsFloatersIntoTheGround(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, 100);
		ZeroGravityFloat.start(p, z, false);
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(ZeroGravityMoves.meteorSlam(p) == 1, "the slam takes it");
			helper.assertTrue(grounded(z), "gravity back at once");
			helper.runAfterDelay(30, () -> {
				float taken = 100f - z.getHealth();
				helper.assertTrue(taken >= ZeroGravityConfig.SLAM_DAMAGE - EPS, "8 on impact, took " + taken);
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- X / Shift+X

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 360)
	public void meteorShowerRainsRubbleAndTheGroundComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 7.0, 100);
		z.setNoAi(false);
		List<BlockPos> taken = new ArrayList<>();
		pump(helper, p);
		int n = ZeroGravityMoves.meteorShower(p, taken);
		helper.assertTrue(n == ZeroGravityConfig.SHOWER_PIECES, "8 pieces, got " + n);
		helper.assertTrue(!taken.isEmpty(), "real ground was lifted");
		for (BlockPos bp : taken) {
			helper.assertTrue(helper.getLevel().getBlockState(bp).isAir() && GeoTerrain.isTemp(helper.getLevel(), bp), "the hole is temporary");
		}
		helper.assertTrue(ZeroGravityDebris.displays(p).size() == n, "the rubble is block displays");
		helper.runAfterDelay(90, () -> {
			float dmg = 100f - z.getHealth();
			helper.assertTrue(dmg >= ZeroGravityConfig.SHOWER_DAMAGE - EPS, "the rubble hit it, took " + dmg);
			helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "every piece is gone after landing");
		});
		helper.runAfterDelay(ZeroGravityConfig.RUBBLE_RESTORE_TICKS + 20, () -> {
			for (BlockPos bp : taken) {
				helper.assertTrue(!helper.getLevel().getBlockState(bp).isAir() && !GeoTerrain.isTemp(helper.getLevel(), bp), "restored at " + bp);
			}
			for (int x = 0; x <= 7; x++) {
				for (int zz = 0; zz <= 7; zz++) {
					helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 1, zz));
				}
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void debrisShieldStopsProjectiles(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		pump(helper, p);
		ZeroGravityMoves.debrisShield(p, null);
		helper.assertTrue(ZeroGravityDebris.shieldUp(p), "the shield orbits");
		Arrow a = EntityType.ARROW.create(helper.getLevel());
		Vec3 from = helper.absoluteVec(new Vec3(4.5, 3.0, 7.0));
		a.moveTo(from.x, from.y, from.z, 0f, 0f);
		a.setDeltaMovement(0, 0, -1.2);
		helper.getLevel().addFreshEntity(a);
		float dmg = QuirkKits.of(Quirk.ZERO_GRAVITY).modifyIncomingDamage(p, helper.getLevel().damageSources().arrow(a, null), 6f);
		helper.assertTrue(dmg == 0f, "projectile damage is stopped");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(a.isRemoved(), "the arrow was caught by the rubble");
			helper.assertTrue(p.getHealth() >= p.getMaxHealth() - 0.01f, "she was not hit");
			ZeroGravityMoves.endAll(p);
			helper.assertTrue(ZeroGravityDebris.displays(p).isEmpty(), "stopAll drops the rubble");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroGSelfTogglesHerOwnGravity(GameTestHelper helper) {
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		helper.assertTrue(ZeroGravityMoves.zeroGSelf(p), "on");
		helper.assertTrue(p.isNoGravity() && ZeroGravity.selfFloating(p), "she floats");
		ready(p);
		helper.assertTrue(!ZeroGravityMoves.zeroGSelf(p), "off");
		helper.assertTrue(!p.isNoGravity() && !p.getTags().contains(ZeroGravity.TAG_FLOAT), "her gravity is back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void floatLeapCutsGravityUntilShesDone(GameTestHelper helper) {
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		ZeroGravityMoves.floatLeap(p);
		double g = p.getAttributeValue(Attributes.GRAVITY);
		helper.assertTrue(g < 0.08 * (1.0 - ZeroGravityConfig.LEAP_GRAVITY_CUT) + 0.001, "almost weightless: " + g);
		helper.assertTrue(p.getDeltaMovement().y >= ZeroGravityConfig.LEAP_UP - 0.01 && p.getDeltaMovement().z > 1.0, "a big leap: " + p.getDeltaMovement());
		helper.assertTrue(ZeroGravity.leaping(p), "the leap shows");
		ZeroGravityMoves.endAll(p);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1.0e-6, "gravity back after stopAll");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V / Shift+V

	@GameTest(template = EMPTY_STRUCTURE)
	public void cometFloatsTheAreaButNeverABoss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 4.5);
		Zombie a = zombie(helper, 1.5, 2.0, 1.5, 100);
		Zombie b = zombie(helper, 7.0, 2.0, 7.0, 100);
		Zombie c = zombie(helper, 1.5, 2.0, 7.0, 100);
		Zombie boss = zombie(helper, 7.0, 2.0, 1.5, 500);
		int n = ZeroGravityMoves.comet(p);
		helper.assertTrue(n == 3, "three float, got " + n);
		helper.assertTrue(floated(a) && floated(b) && floated(c), "the zombies float");
		helper.assertTrue(grounded(boss), "the boss is immune");
		ZeroGravityMoves.endAll(p);
		helper.assertTrue(grounded(a) && grounded(b) && grounded(c), "all back down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossesAreImmuneToFloatTouch(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie boss = zombie(helper, 4.5, 2.0, 3.5, 500);
		helper.assertTrue(ZeroGravityMoves.floatTouch(p) == null, "refused");
		helper.assertTrue(grounded(boss) && !ZeroGravityFloat.start(p, boss, false), "a boss never floats");
		helper.assertTrue(500f - boss.getHealth() <= 500f * ZeroGravityConfig.BOSS_MAX_FRACTION_PER_HIT + EPS, "capped hit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void uravityMeteorDropsTheSlabAndTheGroundComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 7.0, 100);
		List<BlockPos> taken = new ArrayList<>();
		pump(helper, p);
		int n = ZeroGravityMoves.uravityMeteor(p, taken);
		helper.assertTrue(n == 9, "a 3x3 slab, got " + n);
		helper.assertTrue(taken.size() == 9, "nine blocks lifted, got " + taken.size());
		helper.runAfterDelay(80, () -> {
			float dmg = 100f - z.getHealth();
			helper.assertTrue(dmg >= ZeroGravityConfig.METEOR_DAMAGE - EPS, "14 on impact, took " + dmg);
			helper.assertTrue(!ZeroGravityDebris.meteorRunning(p), "the slab is gone");
		});
		helper.runAfterDelay(ZeroGravityConfig.METEOR_RESTORE_TICKS + 20, () -> {
			for (BlockPos bp : taken) {
				helper.assertTrue(helper.getLevel().getBlockState(bp).is(Blocks.STONE), "stone again at " + bp);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C / Shift+C

	@GameTest(template = EMPTY_STRUCTURE)
	public void gunheadThrowSendsAFloaterMuchFurther(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie plain = zombie(helper, 4.5, 2.0, 3.5, 100);
		helper.assertTrue(ZeroGravityMoves.gunheadThrow(p) == plain, "throws the plain one");
		double plainSpeed = plain.getDeltaMovement().length();
		helper.assertTrue(100f - plain.getHealth() >= ZeroGravityConfig.THROW_DAMAGE - EPS, "4 damage");
		plain.discard();
		ready(p);
		Zombie fl = zombie(helper, 4.5, 2.0, 3.5, 100);
		ZeroGravityFloat.start(p, fl, false);
		fl.invulnerableTime = 0;
		helper.assertTrue(ZeroGravityMoves.gunheadThrow(p) == fl, "throws the floater");
		double flSpeed = fl.getDeltaMovement().length();
		helper.assertTrue(flSpeed > plainSpeed * 1.8 && floated(fl), "much further: " + flSpeed + " vs " + plainSpeed);
		ZeroGravityMoves.endAll(p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void gunheadComboLandsThreeHits(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 3.0, 100);
		z.setNoAi(true);
		pump(helper, p);
		helper.assertTrue(ZeroGravityMoves.gunheadCombo(p) == z, "the combo starts");
		helper.runAfterDelay(20, () -> {
			float dmg = 100f - z.getHealth();
			float total = 0f;
			for (float d : ZeroGravityConfig.COMBO_DAMAGE) {
				total += d;
			}
			helper.assertTrue(dmg >= total - EPS, "4 + 4 + 6, took " + dmg);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the meter, the safety nets

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void nauseaRisesWithWeightAndFallsWhenNothingFloats(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		for (int i = 0; i < 3; i++) {
			ZeroGravityFloat.start(p, zombie(helper, 1.5 + i * 2.0, 2.0, 5.5, 100), false);
		}
		pump(helper, p);
		helper.runAfterDelay(40, () -> {
			float m = Quirks.meter(p);
			helper.assertTrue(m > 1.5f, "nausea rose: " + m);
			ZeroGravityMoves.release(p);
			helper.runAfterDelay(30, () -> {
				helper.assertTrue(Quirks.meter(p) < m, "and falls again: " + Quirks.meter(p));
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void fullNauseaDropsEverythingAndMakesHerSick(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 5.5, 100);
		ZeroGravityFloat.start(p, z, false);
		ZeroGravityMoves.zeroGSelf(p);
		Quirks.setMeter(p, 99.99f);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(Quirks.overloaded(p), "she is sick");
			helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.CONFUSION), "Nausea effect");
			helper.assertTrue(grounded(z) && !p.isNoGravity(), "everything dropped, her too");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void revokeAndStopAllAlwaysGiveGravityBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = uravity(helper, 4.5, 2.0, 1.5);
		Zombie a = zombie(helper, 2.5, 2.0, 5.5, 100);
		Zombie b = zombie(helper, 6.5, 2.0, 5.5, 100);
		ZeroGravityFloat.start(p, a, false);
		ZeroGravityFloat.start(p, b, true);
		ZeroGravityMoves.zeroGSelf(p);
		helper.assertTrue(floated(a) && floated(b) && p.isNoGravity(), "all floating");
		QuirkKits.of(Quirk.ZERO_GRAVITY).stopAll(p);
		helper.assertTrue(grounded(a) && grounded(b) && !p.isNoGravity(), "stopAll: all back");
		ready(p);
		ZeroGravityFloat.start(p, a, false);
		ZeroGravityMoves.zeroGSelf(p);
		Quirks.revoke(p);
		helper.assertTrue(grounded(a) && !p.isNoGravity() && !p.getTags().contains(ZeroGravity.TAG_FLOAT), "revoke: all back");
		helper.assertTrue(!ZeroGravityFloat.isFloating(a), "nothing is left in the books");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anOrphanedFloaterGetsItsGravityBackOnLoad(GameTestHelper helper) {
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, 100);
		// what a chunk saved mid-float looks like when it is loaded again
		z.setNoGravity(true);
		z.addTag(ZeroGravity.TAG_FLOAT);
		ZeroGravityFloat.cleanup(z);
		helper.assertTrue(grounded(z), "its gravity is back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zerogravityKitRegistered(GameTestHelper helper) {
		helper.assertTrue(QuirkKits.of(Quirk.ZERO_GRAVITY).idsOf(com.projecthero.mod.hero.AbilitySlot.SLOT_1).length == 2, "the kit has its moves");
		for (com.projecthero.mod.hero.AbilitySlot s : new com.projecthero.mod.hero.AbilitySlot[] { com.projecthero.mod.hero.AbilitySlot.SLOT_1,
				com.projecthero.mod.hero.AbilitySlot.SLOT_2, com.projecthero.mod.hero.AbilitySlot.SLOT_3, com.projecthero.mod.hero.AbilitySlot.SLOT_4,
				com.projecthero.mod.hero.AbilitySlot.SLOT_5, com.projecthero.mod.hero.AbilitySlot.SLOT_6 }) {
			for (String id : QuirkKits.of(Quirk.ZERO_GRAVITY).idsOf(s)) {
				helper.assertTrue(QuirkKits.of(Quirk.ZERO_GRAVITY).maxCooldown(id) > 0, "a cooldown for " + id);
			}
		}
		helper.succeed();
	}
}
