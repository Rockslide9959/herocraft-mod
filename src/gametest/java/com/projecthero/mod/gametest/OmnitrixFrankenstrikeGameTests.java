package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixInfo;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.frankenstrike.FrankenstrikeKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Frankenstrike (user: "make aliens based on their abilities and stuff they do in the show please"): a hidden prototype alien found
 * by a mistransform (the even draw itself is {@code OmnitrixCannonboltGameTests}, which takes every hidden alien); his dial glyph U+E01D;
 * every key has a move, none on H / N; and his numbers -- the Shock Punch and its chain, the Lightning Bolt (full / static), the Conductor
 * Slam, the Overcharge Storm (Charge-gated, no fire), Grab &amp; Shock, the Charge sources (tap V, hold V, Shift+V, lightning, fists) and the
 * Electro Leap.
 */
public class OmnitrixFrankenstrikeGameTests implements FabricGameTest {
	// ---------------------------------------------------------------- setup (Feedback's helpers)

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

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static ServerPlayer franken(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, FrankenstrikeKit.ID, true); // found by a mistransform (the draw's own tests)
		helper.assertTrue(Omnitrix.select(p, FrankenstrikeKit.ID), "Frankenstrike is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Frankenstrike");
		helper.onEachTick(() -> Omnitrix.tick(p));
		return p;
	}

	private static FrankenstrikeKit kit() {
		return (FrankenstrikeKit) Aliens.kit(FrankenstrikeKit.ID);
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	/** A no-AI zombie with {@code health} and no armour (so the numbers read straight). */
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
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z) {
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void lookAt(ServerPlayer p, LivingEntity z) {
		Vec3 d = z.position().add(0, z.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
		float yaw = (float) (Math.atan2(-d.x, d.z) * 180 / Math.PI);
		float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
		p.setYRot(yaw);
		p.setXRot(pitch);
		p.setYHeadRot(yaw);
	}

	private static void near(GameTestHelper helper, float got, float want, String what) {
		helper.assertTrue(Math.abs(got - want) <= 0.05f, what + ": expected " + want + ", got " + got);
	}

	// ---------------------------------------------------------------- the dial

	@GameTest(template = EMPTY_STRUCTURE)
	public void frankenstrikeIsAHiddenPrototypeAlien(GameTestHelper helper) {
		Aliens.Alien a = Aliens.FRANKENSTRIKE;
		helper.assertTrue(Aliens.byId(FrankenstrikeKit.ID) == a && !a.recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.hidden(a.id()) && !Aliens.itemOnly(a.id()) && Aliens.mistransformFinds().contains(a), "hidden, found by a mistransform (no item)");
		helper.assertTrue(Aliens.dial(false).contains(a) && !Aliens.dial(true).contains(a), "on the prototype's dial only");
		ServerPlayer p = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(p);
		helper.assertFalse(Aliens.playable(p).contains(a), "not on the dial before he is found");
		helper.assertFalse(Omnitrix.select(p, a.id()), "and cannot be dialled");
		helper.assertTrue(Aliens.unfound(p).contains(a), "a mistransform can find him");
		Aliens.discover(p, a.id(), true);
		helper.assertTrue(Aliens.playable(p).contains(a) && !Aliens.unfound(p).contains(a), "found: on the dial, out of the draw");
		String g = OmnitrixInfo.icon(a).getString();
		helper.assertTrue(g.length() == 1 && g.charAt(0) == '', "his dial glyph is U+E01D, got " + (g.isEmpty() ? "none" : Integer.toHexString(g.charAt(0))));
		for (String path : new String[] { "gui/omnitrix/dial_frankenstrike.png", "gui/omnitrix/dial_frankenstrike_red.png", "font/omnitrix_dial/frankenstrike.png",
				"entity/omnitrix/frankenstrike.png", "entity/omnitrix/frankenstrike_glow.png" }) {
			helper.assertTrue(OmnitrixFrankenstrikeGameTests.class.getResource("/assets/projecthero/textures/" + path) != null, "texture " + path);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAMoveAndNoneIsOnHOrN(GameTestHelper helper) {
		AlienKit k = kit();
		for (AbilitySlot s : new AbilitySlot[] { AbilitySlot.SLOT_1, AbilitySlot.SLOT_2, AbilitySlot.SLOT_3, AbilitySlot.SLOT_4, AbilitySlot.SLOT_5,
				AbilitySlot.SLOT_6 }) {
			helper.assertTrue(k.moveOn(s, false, false) != null, "a move on " + s);
		}
		helper.assertTrue(FrankenstrikeKit.PUNCH.equals(k.moveOn(AbilitySlot.SLOT_1, false, false).id()), "R: Shock Punch");
		helper.assertTrue(FrankenstrikeKit.BOLT.equals(k.moveOn(AbilitySlot.SLOT_1, true, false).id()), "Shift+R: Lightning Bolt");
		helper.assertTrue(FrankenstrikeKit.SLAM.equals(k.moveOn(AbilitySlot.SLOT_2, false, false).id()), "G: Conductor Slam");
		helper.assertTrue(FrankenstrikeKit.LEAP.equals(k.moveOn(AbilitySlot.SLOT_3, false, false).id()), "X: Electro Leap");
		helper.assertTrue(FrankenstrikeKit.STORM.equals(k.moveOn(AbilitySlot.SLOT_4, false, false).id()), "Z: Overcharge Storm");
		helper.assertTrue(FrankenstrikeKit.QUICK.equals(k.moveOn(AbilitySlot.SLOT_5, false, false).id()), "V: Recharge (tap)");
		helper.assertTrue(FrankenstrikeKit.RECHARGE.equals(k.moveOn(AbilitySlot.SLOT_5, false, true).id()), "hold V: Recharge");
		helper.assertTrue(FrankenstrikeKit.ROD.equals(k.moveOn(AbilitySlot.SLOT_5, true, false).id()), "Shift+V: Lightning Rod");
		helper.assertTrue(FrankenstrikeKit.GRAB.equals(k.moveOn(AbilitySlot.SLOT_6, false, false).id()), "C: Grab & Shock");
		for (var m : k.moves()) {
			helper.assertTrue(!m.onSlot() || m.slot().ordinal() <= AbilitySlot.SLOT_6.ordinal(), m.id() + " on R/G/X/Z/V/C only");
		}
		helper.succeed();
	}

	/** The numbers sit on the damage scale: basic 5-8, specials 12-17, big 17-20, ultimates 21-36. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void damageNumbersSitOnTheScale(GameTestHelper helper) {
		helper.assertTrue(FrankenstrikeKit.PUNCH_DAMAGE >= 5 && FrankenstrikeKit.PUNCH_DAMAGE <= 8, "Shock Punch basic");
		helper.assertTrue(FrankenstrikeKit.CHAIN_DAMAGE >= 5 && FrankenstrikeKit.CHAIN_DAMAGE <= 8, "the chain basic");
		helper.assertTrue(FrankenstrikeKit.BOLT_DAMAGE >= 12 && FrankenstrikeKit.BOLT_DAMAGE <= 17, "Lightning Bolt special");
		helper.assertTrue(FrankenstrikeKit.SLAM_DAMAGE >= 12 && FrankenstrikeKit.SLAM_DAMAGE <= 20, "Conductor Slam special / big");
		helper.assertTrue(FrankenstrikeKit.STRIKE_DAMAGE >= 21 && FrankenstrikeKit.STRIKE_DAMAGE <= 36, "Overcharge Storm ultimate");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R / Shift+R

	/** R: 8 to the creature ahead, 6 jumping to the nearest other within 6, +6 Charge; half again on a machine. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shockPunchChainsToASecondTarget(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		Zombie a = zombie(helper, 3.5, 2, 4.0, 100f);
		Zombie b = zombie(helper, 6.5, 2, 5.5, 100f);
		Zombie third = zombie(helper, 7.5, 2, 7.5, 100f); // 5.3 from the first: in reach, but the current jumps to the nearest only
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(FrankenstrikeKit.charge(p) == 0, "he starts empty");
			lookAt(p, a);
			tap(p, AbilitySlot.SLOT_1, false);
			near(helper, 100f - a.getHealth(), FrankenstrikeKit.PUNCH_DAMAGE, "the punch");
			near(helper, 100f - b.getHealth(), FrankenstrikeKit.CHAIN_DAMAGE, "the chain");
			near(helper, third.getHealth(), 100f, "one jump only, to the nearest");
			helper.assertTrue(FrankenstrikeKit.charge(p) == FrankenstrikeKit.PUNCH_GAIN, "+6 Charge: " + FrankenstrikeKit.charge(p));
			a.discard();
			b.discard();
			IronGolem g = mob(helper, EntityType.IRON_GOLEM, 3.5, 2, 4.5);
			helper.runAfterDelay(2, () -> {
				kit().cooldown(p, FrankenstrikeKit.PUNCH, 0);
				lookAt(p, g);
				float gh = g.getHealth();
				tap(p, AbilitySlot.SLOT_1, false);
				near(helper, gh - g.getHealth(), FrankenstrikeKit.PUNCH_DAMAGE * FrankenstrikeKit.MACHINE_BONUS, "on a machine (x1.5)");
				helper.succeed();
			});
		});
	}

	/** Shift+R: 15 for 15 Charge; with less, a static bolt (7) that spends nothing. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void lightningBoltSpendsCharge(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		Zombie z = zombie(helper, 3.5, 2, 7.5, 100f);
		helper.runAfterDelay(2, () -> {
			lookAt(p, z);
			tap(p, AbilitySlot.SLOT_1, true);
			near(helper, 100f - z.getHealth(), FrankenstrikeKit.SPARK_DAMAGE, "empty: a static bolt");
			helper.assertTrue(FrankenstrikeKit.charge(p) == 0, "nothing spent");
			kit().fill(p, 30);
			kit().cooldown(p, FrankenstrikeKit.BOLT, 0);
			float h = z.getHealth();
			tap(p, AbilitySlot.SLOT_1, true);
			near(helper, h - z.getHealth(), FrankenstrikeKit.BOLT_DAMAGE, "charged: the Lightning Bolt");
			helper.assertTrue(FrankenstrikeKit.charge(p) == 15, "15 Charge spent: " + FrankenstrikeKit.charge(p));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G

	/** G: 17 to everything within 5.5 for 20 Charge; with less, 9 within 3.5. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void conductorSlamHitsAround(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		Zombie close = zombie(helper, 3.5, 2, 4.0, 100f);
		Zombie mid = zombie(helper, 7.5, 2, 4.5, 100f); // 5 away: inside 5.5, outside 3.5
		helper.runAfterDelay(4, () -> {
			p.setOnGround(true); // (a mock player is not physics-ticked)
			tap(p, AbilitySlot.SLOT_2, false);
			near(helper, 100f - close.getHealth(), FrankenstrikeKit.SLAM_WEAK, "empty: the weak slam");
			near(helper, mid.getHealth(), 100f, "the weak slam's 3.5 blocks");
			kit().fill(p, 25);
			kit().cooldown(p, FrankenstrikeKit.SLAM, 0);
			float c = close.getHealth(), m = mid.getHealth();
			tap(p, AbilitySlot.SLOT_2, false);
			near(helper, c - close.getHealth(), FrankenstrikeKit.SLAM_DAMAGE, "charged: close");
			near(helper, m - mid.getHealth(), FrankenstrikeKit.SLAM_DAMAGE, "charged: 5 blocks out");
			helper.assertTrue(FrankenstrikeKit.charge(p) == 5, "20 Charge spent: " + FrankenstrikeKit.charge(p));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z

	/** Z refuses under 50 Charge (no cooldown spent); with it, strikes land on the creatures round him for 24, and no fire starts. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "frankenstrike_storm_alone") // alone: the storm strikes any creature within 14, other tests' too
	public void overchargeStormNeedsChargeAndStartsNoFire(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		Zombie z = zombie(helper, 5.5, 2, 5.5, 400f);
		helper.runAfterDelay(2, () -> {
			kit().fill(p, 40);
			tap(p, AbilitySlot.SLOT_4, false);
			helper.assertTrue(kit().cooldownLeft(p, FrankenstrikeKit.STORM) == 0 && !kit().flag(p, FrankenstrikeKit.F_STORM) && FrankenstrikeKit.charge(p) == 40,
					"refused under 50, nothing spent");
			kit().fill(p, 60);
			tap(p, AbilitySlot.SLOT_4, false);
			helper.assertTrue(kit().flag(p, FrankenstrikeKit.F_STORM) && FrankenstrikeKit.charge(p) == 10, "the storm is up, 50 Charge spent");
			helper.runAfterDelay(FrankenstrikeKit.STORM_TICKS + 5, () -> {
				helper.assertFalse(kit().flag(p, FrankenstrikeKit.F_STORM), "over after 3 s");
				float dealt = 400f - z.getHealth();
				// the strikes pick among every creature within 14 (the batch's other mock players too): some land on the zombie, 24 each
				helper.assertTrue(dealt >= FrankenstrikeKit.STRIKE_DAMAGE - 0.05f && dealt <= FrankenstrikeKit.STRIKE_DAMAGE * FrankenstrikeKit.STORM_STRIKES + 0.05f && Math.abs(dealt / FrankenstrikeKit.STRIKE_DAMAGE - Math.round(dealt / FrankenstrikeKit.STRIKE_DAMAGE)) < 0.01f, "strikes of 24 (at most eight): " + dealt);
				for (BlockPos q : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 4, 7)))) {
					helper.assertFalse(helper.getLevel().getBlockState(q).is(Blocks.FIRE), "no fire at " + q);
				}
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- C

	/** C: holds the creature up for 2 s, 4 every half second, then throws it (4): 20 in all. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void grabAndShockHoldsShocksAndThrows(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 100f);
		helper.runAfterDelay(2, () -> {
			lookAt(p, z);
			tap(p, AbilitySlot.SLOT_6, false);
			helper.assertTrue(kit().flag(p, FrankenstrikeKit.F_GRAB) && FrankenstrikeKit.holding(p) == z, "he holds it");
			helper.runAfterDelay(5, () -> helper.assertTrue(z.getY() > p.getY() + 0.8, "held up off the ground: " + (z.getY() - p.getY())));
			helper.runAfterDelay(FrankenstrikeKit.GRAB_TICKS + 4, () -> {
				helper.assertTrue(!kit().flag(p, FrankenstrikeKit.F_GRAB) && FrankenstrikeKit.holding(p) == null, "thrown after 2 s");
				near(helper, 100f - z.getHealth(), FrankenstrikeKit.GRAB_DAMAGE * (FrankenstrikeKit.GRAB_TICKS / FrankenstrikeKit.GRAB_EVERY) + FrankenstrikeKit.THROW_DAMAGE,
						"four shocks and the throw");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- V: Charge

	/** Tap V +8; hold V +6 a second; Shift+V +50 and 8 round him; lightning is +50 and never hurts. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void rechargeFillsTheChargeMeter(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 100f);
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_5, false);
			helper.assertTrue(FrankenstrikeKit.charge(p) == FrankenstrikeKit.QUICK_GAIN, "tap V: +8, got " + FrankenstrikeKit.charge(p));
			kit().fill(p, 0);
			helper.assertTrue(FrankenstrikeKit.rechargeRate(p) == 1, "plain stone, clear sky: x1");
			kit().cooldown(p, FrankenstrikeKit.RECHARGE, 0);
			helper.assertTrue(kit().startRecharge(p) && kit().flag(p, FrankenstrikeKit.F_RECHARGE), "hold V: channelling");
			helper.runAfterDelay(40, () -> {
				kit().endRecharge(p);
				int c = FrankenstrikeKit.charge(p);
				helper.assertTrue(c >= 9 && c <= 15, "about 6 a second for 2 s, got " + c);
				helper.assertFalse(kit().flag(p, FrankenstrikeKit.F_RECHARGE), "the release ends it");
				kit().fill(p, 0);
				tap(p, AbilitySlot.SLOT_5, true);
				helper.assertTrue(FrankenstrikeKit.charge(p) == FrankenstrikeKit.ROD_GAIN, "Shift+V: +50, got " + FrankenstrikeKit.charge(p));
				near(helper, 100f - z.getHealth(), FrankenstrikeKit.ROD_DAMAGE, "the Lightning Rod's burst");
				kit().fill(p, 0);
				helper.runAfterDelay(25, () -> {
					float h = p.getHealth();
					p.invulnerableTime = 0;
					helper.assertFalse(p.hurt(p.damageSources().lightningBolt(), 5f), "lightning is refused");
					helper.assertTrue(p.getHealth() >= h && FrankenstrikeKit.charge(p) == FrankenstrikeKit.LIGHTNING_GAIN, "and is +50 Charge: " + FrankenstrikeKit.charge(p));
					helper.succeed();
				});
			});
		});
	}

	/** Recharge is twice as fast by power (a lightning rod within 6). */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void rechargeIsFasterByPower(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(5, 2, 3)), Blocks.LIGHTNING_ROD.defaultBlockState(), 3);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(FrankenstrikeKit.rechargeRate(p) == 2, "x2 by a lightning rod");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X

	/** X: a big leap forward and up, no fall damage while it lasts. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void electroLeapWithNoFallDamage(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		helper.runAfterDelay(2, () -> {
			tap(p, AbilitySlot.SLOT_3, false);
			Vec3 v = p.getDeltaMovement();
			helper.assertTrue(v.z > 1.0 && v.y > 0.8 && kit().flag(p, FrankenstrikeKit.F_LEAP), "the leap: forward and up, " + v);
			p.fallDistance = 10f;
			p.invulnerableTime = 0;
			helper.assertFalse(p.hurt(p.damageSources().fall(), 6f), "no fall damage");
			helper.succeed();
		});
	}

	/** Galvanic Fists: a full-strength punch feeds +4 Charge. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void fistsFeedCharge(GameTestHelper helper) {
		ServerPlayer p = franken(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.0, 100f);
		helper.runAfterDelay(30, () -> {
			lookAt(p, z);
			try { // a full attack-strength meter (a mock player is not ticked)
				java.lang.reflect.Field f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
				f.setAccessible(true);
				f.setInt(p, 100);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException(e);
			}
			kit().onAttack(p, z);
			helper.assertTrue(FrankenstrikeKit.charge(p) == FrankenstrikeKit.FIST_GAIN, "+4 Charge a punch, got " + FrankenstrikeKit.charge(p));
			helper.succeed();
		});
	}
}
