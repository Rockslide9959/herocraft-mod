package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadBlocks;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeEmp;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeTakeover;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Omnitrix wave A: XLR8, Diamondhead, Upgrade and Ghostfreak -- each one transformed into through the framework, its
 * body (attributes), every move's damage and cooldown, its passives (Diamondhead's reflection, Ghostfreak's intangibility and
 * its timer, Upgrade's slide hitbox and merges) and the clean-up when it turns back (no stuck intangibility, size or
 * invisibility). Mock players are not ticked by the server, so tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixWaveAGameTests implements FabricGameTest {
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

	private static ServerPlayer as(GameTestHelper helper, String alien) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, alien), alien + " is playable");
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + alien);
		helper.assertTrue(Omnitrix.state(p).active.equals(alien), "the framework says " + alien);
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
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.05f;
	}

	// ================================================================ XLR8

	/** v0.17.2 (user: "make xlr8 run through leaves like the other speedsters"): in Speed Mode leaves let him through. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8RunsThroughLeavesInSpeedMode(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		helper.assertFalse(com.projecthero.mod.hero.power.p04.SpeedLeaves.active(p), "not before Speed Mode");
		helper.assertTrue(kit.toggleSpeedMode(p) && kit.flag(p, Xlr8Kit.F_SPEED), "V: Speed Mode");
		helper.assertTrue(com.projecthero.mod.hero.power.p04.SpeedLeaves.active(p), "leaves let him through");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(com.projecthero.mod.hero.power.p04.SpeedLeaves.active(p), "not after turning back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8IsFastLightAndSprintsFaster(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		helper.assertTrue(near(p.getMaxHealth(), 22f), "22 health");
		double walk = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(walk > 0.1 * 1.8, "very fast: " + walk);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ARMOR) - 15.0) < 1.0e-6, "v0.17.2: iron-level armour (15)");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_SPEED) > 4.0 + 1.5, "fast hands");
		p.setSprinting(true);
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime());
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > walk * 1.3, "sprinting is faster still");
		// v0.17.1 revamp: V's Speed Mode (wall and water running came with it)
		double sprinting = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(kit.toggleSpeedMode(p) && kit.flag(p, Xlr8Kit.F_SPEED), "Speed Mode on");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > sprinting * 1.8, "Speed Mode: twice as fast again");
		helper.assertTrue(Xlr8Kit.visorDown(p), "the visor is down");
		helper.assertTrue(kit.standsOnFluid(p, Fluids.WATER.getSource(false)), "runs on water");
		helper.assertFalse(kit.standsOnFluid(p, Fluids.LAVA.getSource(false)), "not on lava");
		p.setSprinting(false); // vanilla's own sprint boost off too
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.1) < 1.0e-4, "the speed comes off with the body (sprint bonus too)");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - 4.0) < 1.0e-4, "and the fast hands");
		helper.assertFalse(kit.flag(p, Xlr8Kit.F_SPEED), "Speed Mode is off after turning back");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) < 0.6 + 1.0e-4, "and its step assist");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void xlr8MovesHitAndCoolDown(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		Zombie z = zombie(helper, 3.5, 2, 5.5, 120f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(AlienKit.lookTarget(p, 14) == z, "the zombie is in sight");
		// v0.17.2 revamp: G is Raptor Strike (the borrowed Blitz on his own 14) -- and it trips the target
		helper.assertTrue(kit.raptorStrike(p), "Raptor Strike");
		helper.assertTrue(Xlr8Kit.tripped(p, z), "the zombie is tripped");
		var trip = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(trip != null && trip.getAmplifier() >= 9, "rooted where it fell");
		z.removeAllEffects(); // (so the Time Burst below is the slow that shows)
		helper.assertTrue(near(z.getHealth(), 120f - Xlr8Kit.BLITZ_HIT), "14 damage: " + z.getHealth());
		helper.assertFalse(kit.raptorStrike(p), "then cools down");
		helper.assertTrue(p.distanceTo(z) < 2.5, "he closed the gap");
		z.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180f, 0f);
		z.setDeltaMovement(Vec3.ZERO);
		float before = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(kit.tailSpin(p), "Tail Spin");
		helper.assertTrue(near(before - z.getHealth(), Xlr8Kit.TAIL_SPIN_DAMAGE), "8 damage");
		helper.assertFalse(kit.tailSpin(p), "it cools down");
		helper.assertTrue(kit.timeBurst(p), "Time Burst");
		var slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() >= 5 && slow.getDuration() > Xlr8Kit.BURST_TICKS - 5, "the zombie crawls");
		helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.BURST) > 500, "a long cooldown");
		// the snatch
		z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(kit.onUseEntity(p, InteractionHand.MAIN_HAND, z) == net.minecraft.world.InteractionResult.SUCCESS && z.getMainHandItem().isEmpty() && p.getInventory().contains(new ItemStack(Items.IRON_SWORD)), "the sword is his");
		// Speed Mine: a stone wall ahead
		p.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 1.5)).x, p.getY(), helper.absoluteVec(new Vec3(3.5, 2, 1.5)).z, 0f, 0f);
		for (int i = 1; i <= 4; i++) {
			helper.getLevel().setBlock(p.blockPosition().south(i), Blocks.STONE.defaultBlockState(), 3);
			helper.getLevel().setBlock(p.blockPosition().south(i).above(), Blocks.DIRT.defaultBlockState(), 3);
		}
		helper.getLevel().setBlock(p.blockPosition().south(5), Blocks.OBSIDIAN.defaultBlockState(), 3); // too hard: the tunnel stops here
		z.moveTo(p.getX() + 2.5, p.getY(), p.getZ(), 180f, 0f);
		int mined = kit.speedMine(p);
		helper.assertTrue(mined == 8 && helper.getLevel().getBlockState(p.blockPosition().south(2)).isAir(), "the tunnel is mined: " + mined);
		helper.assertTrue(p.getInventory().countItem(Items.COBBLESTONE) == 4 && p.getInventory().countItem(Items.DIRT) == 4, "the drops are in his pockets");
		helper.assertTrue(kit.speedMine(p) == 0, "it cools down");
		// the flurry and the kicks over ticks, then the tornado and the dash
		z.moveTo(p.getX(), p.getY(), p.getZ() + 1.8, 180f, 0f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		float hp = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(kit.blurPunches(p) == 1, "Blur Punches hit the zombie");
		helper.assertTrue(hp - z.getHealth() >= Xlr8Kit.ASSAULT_PUNCH * Xlr8Kit.ASSAULT_PUNCHES - 0.1f, "six punches of 2.5: " + (hp - z.getHealth()));
		float afterAssault = z.getHealth();
		helper.assertTrue(kit.kickBarrage(p), "Kick Barrage");
		p.setDeltaMovement(Vec3.ZERO);
		pump(helper, p);
		helper.runAfterDelay(18, () -> {
			helper.assertTrue(z.getHealth() < afterAssault - Xlr8Kit.KICK_HIT * 4, "the kicks landed many hits: " + (afterAssault - z.getHealth()));
			helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.PUNCHES) > 0 && kit.cooldownLeft(p, Xlr8Kit.KICKS) > 0, "both cool down");
			helper.assertTrue(kit.tornadoRun(p) && kit.flag(p, Xlr8Kit.F_TORNADO), "Tornado Run");
			helper.assertTrue(kit.blurDash(p) && kit.flag(p, Xlr8Kit.F_DASH), "Blur Dash");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertFalse(kit.flag(p, Xlr8Kit.F_TORNADO) || kit.flag(p, Xlr8Kit.F_DASH), "turning back stops the tornado and the dash");
			z.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8sBlurDashGoesWhereHeAims(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		// aimed steeply up and off to the side: the dash goes that way, not flat ahead
		p.setYRot(90f);
		p.setXRot(-60f);
		Vec3 aim = p.getLookAngle();
		helper.assertTrue(kit.blurDash(p), "Blur Dash");
		Vec3 v = p.getDeltaMovement();
		helper.assertTrue(v.normalize().dot(aim) > 0.99, "it follows the aim: " + v + " vs " + aim);
		helper.assertTrue(v.y > 1.5, "aimed up, he dashes up: " + v.y);
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.2 XLR8 revamp

	/** Kinetic Charge fills while he runs flat out in Speed Mode, drains standing still, and the Sonic Punch spends it. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8KineticChargeFillsAndSonicPunchSpendsIt(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		long t0 = helper.getLevel().getGameTime();
		Vec3 start = p.position();
		// not in Speed Mode: running does not charge
		for (int i = 1; i <= 10; i++) {
			p.setPos(start.x, start.y, start.z + 0.5 * i);
			kit.tick(p, Omnitrix.state(p), t0 + i);
		}
		helper.assertTrue(Xlr8Kit.kineticCharge(p) == 0f, "no charge outside Speed Mode");
		helper.assertTrue(kit.toggleSpeedMode(p), "Speed Mode");
		for (int i = 11; i <= 70; i++) {
			p.setPos(start.x, start.y, start.z + 0.5 * i);
			kit.tick(p, Omnitrix.state(p), t0 + i);
		}
		float c = Xlr8Kit.kineticCharge(p);
		helper.assertTrue(Math.abs(c - 60 * Xlr8Kit.CHARGE_FILL) < 1.5f, "3 s flat out: half a bar: " + c);
		helper.assertTrue(Math.abs(Xlr8Kit.charge(p) - c / 100f) < 0.045f, "synced for the HUD: " + Xlr8Kit.charge(p));
		// a second standing still drains 10
		for (int i = 71; i <= 90; i++) {
			kit.tick(p, Omnitrix.state(p), t0 + i);
		}
		helper.assertTrue(Math.abs(Xlr8Kit.kineticCharge(p) - (c - 10f)) < 0.6f, "drains 10 a second still: " + Xlr8Kit.kineticCharge(p));
		// the punch: a full bar = 35, and the bar empties
		Zombie z = zombie(helper, 3.5, 2, 3.0, 200f);
		p.moveTo(start.x, start.y, start.z, 0f, 10f); // back inside the test's own space (the run took him 35 blocks off)
		z.moveTo(p.getX(), p.getY(), p.getZ() + 1.8, 180f, 0f);
		kit.setCharge(p, Xlr8Kit.CHARGE_MAX);
		float hp = z.getHealth();
		helper.assertTrue(kit.sonicPunch(p) > 0.99f, "Sonic Punch spends a full bar");
		helper.assertTrue(near(hp - z.getHealth(), Xlr8Kit.SONIC_HIT_FULL), "35 at a full bar: " + (hp - z.getHealth()));
		helper.assertTrue(Xlr8Kit.kineticCharge(p) == 0f && Xlr8Kit.charge(p) == 0f, "the bar is empty");
		helper.assertTrue(kit.sonicPunch(p) < 0f, "then it cools down");
		kit.cooldown(p, Xlr8Kit.SONIC, 0);
		z.moveTo(p.getX(), p.getY(), p.getZ() + 1.8, 180f, 0f);
		z.setDeltaMovement(Vec3.ZERO);
		z.invulnerableTime = 0;
		hp = z.getHealth();
		helper.assertTrue(kit.sonicPunch(p) == 0f, "no charge to spend");
		helper.assertTrue(near(hp - z.getHealth(), Xlr8Kit.SONIC_HIT), "20 with an empty bar: " + (hp - z.getHealth()));
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Xlr8Kit.kineticCharge(p) == 0f, "the charge goes with the body");
		z.discard();
		helper.succeed();
	}

	/** Tail Pin: a root drops him out of Speed Mode and locks it; Friction: ice makes him slick and stops the wall run. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8TailPinAndFriction(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		long t0 = helper.getLevel().getGameTime();
		helper.assertTrue(kit.toggleSpeedMode(p), "Speed Mode");
		// Friction: on ice he loses grip
		helper.getLevel().setBlock(p.getOnPos(), Blocks.BLUE_ICE.defaultBlockState(), 3);
		kit.tick(p, Omnitrix.state(p), t0 + 1);
		helper.assertTrue(kit.flag(p, Xlr8Kit.F_SLICK), "slick on blue ice");
		p.horizontalCollision = true;
		p.setXRot(-70f);
		helper.assertFalse(kit.climbs(p), "no wall run off the ice");
		p.horizontalCollision = false;
		p.setXRot(0f);
		helper.getLevel().setBlock(p.getOnPos(), Blocks.STONE.defaultBlockState(), 3);
		kit.tick(p, Omnitrix.state(p), t0 + 2);
		helper.assertFalse(kit.flag(p, Xlr8Kit.F_SLICK), "grip back on stone");
		// Tail Pin
		kit.setCharge(p, 80f);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 2));
		kit.tick(p, Omnitrix.state(p), t0 + 3);
		helper.assertFalse(kit.flag(p, Xlr8Kit.F_SPEED), "pinned: out of Speed Mode");
		helper.assertTrue(Xlr8Kit.kineticCharge(p) == 0f, "the charge is gone");
		p.removeAllEffects();
		kit.cooldown(p, Xlr8Kit.MODE, 0);
		helper.assertFalse(kit.toggleSpeedMode(p), "and Speed Mode stays locked for 3 s");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	/** Reflexes, Tail Balance and Raptor Rush's untouchability. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8ReflexesAndBalance(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		Zombie archer = zombie(helper, 3.5, 2, 6.5, 40f);
		Arrow a = new Arrow(EntityType.ARROW, helper.getLevel());
		a.setOwner(archer);
		var shot = p.damageSources().arrow(a, archer);
		helper.assertFalse(kit.dodges(p, shot, 0.0f), "no Reflexes outside Speed Mode");
		helper.assertTrue(kit.toggleSpeedMode(p), "Speed Mode");
		helper.assertTrue(kit.dodges(p, shot, 0.1f), "a lucky roll side-steps the arrow");
		helper.assertFalse(kit.dodges(p, shot, 0.5f), "an unlucky one does not");
		helper.assertFalse(kit.dodges(p, p.damageSources().mobAttack(archer), 0.0f), "a blow is no projectile");
		// Tail Balance: running in Speed Mode, no knockback
		long t0 = helper.getLevel().getGameTime();
		Vec3 start = p.position();
		for (int i = 1; i <= 3; i++) {
			p.setPos(start.x, start.y, start.z + 0.5 * i);
			kit.tick(p, Omnitrix.state(p), t0 + i);
		}
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "no knockback on the run");
		for (int i = 4; i <= 5; i++) {
			kit.tick(p, Omnitrix.state(p), t0 + i);
		}
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0, "standing still he can be pushed");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0, "it comes off with the body");
		archer.discard();
		helper.succeed();
	}

	/** Friction Zero: low enough for a 1-block gap, it bowls over what it hits; Shift+G again lets the twister loose. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void xlr8FrictionZeroAndLooseTwister(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		float standing = p.getBbHeight();
		helper.assertTrue(kit.frictionZero(p) && kit.flag(p, Xlr8Kit.F_SKATE), "Friction Zero");
		p.refreshDimensions();
		helper.assertTrue(p.getBbHeight() < 1.0f && standing > 1.5f, "low to the ground: " + p.getBbHeight());
		helper.assertTrue(Xlr8Kit.visorDown(p), "the visor is down");
		Zombie z = zombie(helper, 3.5, 2, 2.5, 60f);
		z.moveTo(p.getX(), p.getY(), p.getZ() + 0.8, 180f, 0f);
		float hp = z.getHealth();
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime() + 1);
		helper.assertTrue(near(hp - z.getHealth(), Xlr8Kit.SKATE_HIT), "6 to what he hits: " + (hp - z.getHealth()));
		kit.press(p, AbilitySlot.SLOT_3, true);
		helper.assertFalse(kit.flag(p, Xlr8Kit.F_SKATE), "Shift+X again stands him up");
		p.refreshDimensions();
		helper.assertTrue(p.getBbHeight() > 1.5f, "full height again");
		// the Tornado Run, then Shift+G again: the twister goes on without him
		helper.assertTrue(kit.tornadoRun(p) && kit.flag(p, Xlr8Kit.F_TORNADO), "Tornado Run");
		kit.press(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(kit.flag(p, Xlr8Kit.F_LOOSE) && !kit.flag(p, Xlr8Kit.F_TORNADO), "Shift+G again lets it loose");
		pump(helper, p);
		helper.runAfterDelay(Xlr8Kit.LOOSE_TICKS / 2 + 2, () -> {
			helper.assertTrue(kit.flag(p, Xlr8Kit.F_LOOSE), "still travelling");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertFalse(kit.flag(p, Xlr8Kit.F_LOOSE) || kit.flag(p, Xlr8Kit.F_SKATE), "turning back ends it all");
			z.discard();
			helper.succeed();
		});
	}

	/** Rescue Carry: Use on a player scoops them onto his back; Use again sets them down. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8RescueCarry(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		ServerPlayer ally = mock(helper, 4.5, 2, 1.5);
		helper.assertTrue(kit.onUseEntity(p, InteractionHand.MAIN_HAND, ally) == net.minecraft.world.InteractionResult.SUCCESS, "Rescue Carry");
		helper.assertTrue(ally.getVehicle() == p && Xlr8Kit.carried(p) == ally, "on his back");
		helper.assertTrue(kit.flag(p, Xlr8Kit.F_CARRY), "the carry flag");
		ally.fallDistance = 20f;
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime() + 1);
		helper.assertTrue(ally.fallDistance == 0f, "no fall damage while carried");
		helper.assertTrue(kit.onUse(p, InteractionHand.MAIN_HAND) == net.minecraft.world.InteractionResult.SUCCESS, "Use again");
		helper.assertTrue(ally.getVehicle() == null && Xlr8Kit.carried(p) == null, "set down");
		// any attack sets them down too
		kit.cooldown(p, Xlr8Kit.CARRY, 0);
		helper.assertTrue(kit.rescueCarry(p, ally) && ally.getVehicle() == p, "carried again");
		kit.press(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(ally.getVehicle() == null, "an attack sets them down");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	// ================================================================ Diamondhead

	@GameTest(template = EMPTY_STRUCTURE)
	public void diamondheadIsArmouredSlowAndReflects(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		helper.assertTrue(near(p.getMaxHealth(), 34f), "34 health");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 12 - 1.0e-6, "heavy armour");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.1, "a little slow");
		helper.assertTrue(Math.abs(p.getScale() - 1.1f) < 0.01f, "a touch bigger");
		Zombie archer = zombie(helper, 3.5, 2, 6.5, 40f);
		Arrow a = new Arrow(EntityType.ARROW, helper.getLevel());
		a.setOwner(archer);
		a.moveTo(p.getX(), p.getY() + 1.2, p.getZ() + 2.0);
		a.setDeltaMovement(0, 0, -1.5);
		helper.getLevel().addFreshEntity(a);
		helper.assertTrue(kit.tryReflect(p, a, 0.0f), "a lucky roll reflects");
		helper.assertTrue(a.getOwner() == p && a.getDeltaMovement().z > 0, "the arrow is his now and heads back the way it came");
		Arrow b = new Arrow(EntityType.ARROW, helper.getLevel());
		b.setOwner(archer);
		b.moveTo(p.getX(), p.getY() + 1.2, p.getZ() + 2.0);
		b.setDeltaMovement(0, 0, -1.5);
		helper.assertFalse(kit.tryReflect(p, b, 0.99f), "an unlucky roll does not");
		helper.assertTrue(DiamondheadKit.REFLECT_CHANCE > 0.2f && DiamondheadKit.REFLECT_CHANCE < 0.6f, "a share, not all");
		a.discard();
		archer.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadsMovesHitAndCoolDown(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(kit.shardVolley(p), "Shard Volley");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(4)).size() == 5, "five shards");
		helper.assertFalse(kit.shardVolley(p), "then cools down");
		helper.assertTrue(kit.crystalSpear(p) && kit.cooldownLeft(p, DiamondheadKit.SPEAR) > 0, "Crystal Spear");
		// v0.17.2 revamp: the wall moved to Shift+V (V is Combat Mode, the dome is gone)
		kit.press(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.WALL) > 0 && !kit.flag(p, DiamondheadKit.F_COMBAT), "Shift+V raises the wall, not Combat Mode");
		helper.assertTrue(helper.getLevel().getBlockState(BlockPos.containing(p.position().add(0, 0.5, 2.5))).is(DiamondheadBlocks.CRYSTAL), "of crystal");
		helper.assertTrue(kit.wall(p) == 0, "the wall cools down");
		BlockPos face = p.blockPosition().west(2);
		helper.assertTrue(kit.placeCrystal(p, face) && helper.getLevel().getBlockState(face).is(DiamondheadBlocks.CRYSTAL), "Use places a crystal block");
		helper.assertTrue(kit.barrage(p), "Diamond Barrage");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	/**
	 * v0.17.2 revamp: the keys -- G (held) the Crystal Shield, Shift+G the Shield Bash, V Combat Mode, Shift+V the wall, Z still
	 * the Crystal Eruption: rings of spikes all round him that reach a zombie behind him and one beside him (12 each, thrown up,
	 * slowed), once each.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadsKeysAndCrystalEruptionHitsAllAround(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		helper.assertTrue(DiamondheadKit.SHATTER.equals(kit.moveOn(AbilitySlot.SLOT_2, false, false).id()), "G is the Spike Line");
		helper.assertTrue(DiamondheadKit.PILLAR.equals(kit.moveOn(AbilitySlot.SLOT_2, true, false).id()), "Shift+G the Crystal Pillar");
		helper.assertTrue(DiamondheadKit.TETHER.equals(kit.moveOn(AbilitySlot.SLOT_6, true, false).id()), "Shift+C the Crystal Tether");
		helper.assertTrue(kit.move("prism_beam") == null && kit.move(DiamondheadKit.BRIDGE) == null, "the Prism Beam is gone; the bridge is the Construct's");
		helper.assertTrue(DiamondheadKit.COMBAT.equals(kit.moveOn(AbilitySlot.SLOT_5, false, false).id()), "V Combat Mode");
		helper.assertTrue(DiamondheadKit.WALL.equals(kit.moveOn(AbilitySlot.SLOT_5, true, false).id()), "Shift+V the Crystal Construct");
		helper.assertTrue(DiamondheadKit.ERUPT.equals(kit.moveOn(AbilitySlot.SLOT_4, false, false).id()), "Z is the Crystal Eruption");
		helper.assertTrue(DiamondheadKit.BARRAGE.equals(kit.moveOn(AbilitySlot.SLOT_4, true, false).id()), "Shift+Z is still the Barrage");
		helper.assertTrue(kit.move("crystal_dome") == null && kit.move("blade_arm") == null, "the dome and the old Blade Arm are gone");
		Vec3 mid = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		p.moveTo(mid.x, mid.y, mid.z, 0f, 0f); // facing +z
		Zombie behind = zombie(helper, 3.5, 2, 0.5, 60f);
		Zombie beside = zombie(helper, 7.0, 2, 3.5, 60f);
		kit.press(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.ERUPT) > DiamondheadKit.ERUPT_COOLDOWN - 5, "Z: the Crystal Eruption, on cooldown");
		helper.assertFalse(kit.eruption(p), "and not again until it cools");
		pump(helper, p);
		helper.runAfterDelay(DiamondheadKit.ERUPT_RINGS + 4, () -> {
			helper.assertTrue(near(behind.getHealth(), 60f - DiamondheadKit.ERUPT_DAMAGE), "the zombie behind him: 12, once: " + behind.getHealth());
			helper.assertTrue(near(beside.getHealth(), 60f - DiamondheadKit.ERUPT_DAMAGE), "the one beside him (3.5 out): " + beside.getHealth());
			helper.assertTrue(behind.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
			behind.discard();
			beside.discard();
			helper.succeed();
		});
	}

	/**
	 * v0.17.2 revamp: G held raises the Crystal Shield -- a frontal blow is blocked (it chips the shield, the attacker is cut),
	 * a frontal arrow goes back at its archer, a blow from behind still lands; G up lowers it; past 40 it shatters and must regrow.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadShieldBlocksReflectsAndShatters(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f); // facing +z
		kit.onUse(p, InteractionHand.MAIN_HAND);
		helper.assertFalse(kit.flag(p, DiamondheadKit.F_SHIELD), "out of Combat Mode a right-click raises no shield");
		kit.press(p, AbilitySlot.SLOT_5, false);
		double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		kit.onUse(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(kit.flag(p, DiamondheadKit.F_SHIELD), "in Combat Mode the right-click raises the shield");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed - 1.0e-4, "and slows him");
		Zombie front = zombie(helper, 3.5, 2, 3.5, 40f);
		Zombie behind = zombie(helper, 3.5, 2, 0.4, 40f);
		Zombie archer = zombie(helper, 3.5, 2, 7.5, 40f);
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(front), 10f);
		helper.assertTrue(near(p.getHealth(), hp), "a blow from in front is blocked: " + p.getHealth());
		// (the blow as it lands: Hard difficulty scales a mob's 10 up to 15)
		float chipped = DiamondheadKit.SHIELD_HP - kit.shieldHp(p);
		helper.assertTrue(chipped >= 10f - 0.05f && chipped <= 15f + 0.05f, "it chips the shield: " + kit.shieldHp(p));
		helper.assertTrue(near(front.getHealth(), 40f - DiamondheadKit.CUT_DAMAGE), "and the attacker is cut on the crystal: " + front.getHealth());
		Arrow a = new Arrow(EntityType.ARROW, helper.getLevel());
		a.setOwner(archer);
		a.moveTo(p.getX(), p.getY() + 1.2, p.getZ() + 2.0);
		a.setDeltaMovement(0, 0, -1.5);
		helper.getLevel().addFreshEntity(a);
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime());
		helper.assertTrue(a.getOwner() == p && a.getDeltaMovement().z > 0, "a frontal arrow goes back at the archer, every time");
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(behind), 6f);
		helper.assertTrue(p.getHealth() < hp - 0.1f, "a blow from behind still lands: " + p.getHealth());
		kit.lowerShield(p);
		helper.assertFalse(kit.flag(p, DiamondheadKit.F_SHIELD), "letting go lowers it");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1.0e-4, "and the slow goes with it");
		helper.assertTrue(kit.raiseShield(p), "up again");
		float before = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(front), 35f);
		helper.assertTrue(near(p.getHealth(), before), "the breaking blow is still blocked");
		helper.assertTrue(kit.shieldBroken(p) && !kit.flag(p, DiamondheadKit.F_SHIELD), "past 40 it shatters");
		helper.assertFalse(kit.raiseShield(p), "and can not go up while it regrows");
		a.discard();
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "shield bash can happen when he holds out the shield and runs into enemies"): behind the raised shield, running
	 * into a foe batters it (10) and staggers it -- once, not again inside the re-hit window; standing still bashes nothing.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadShieldBashStaggers(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f); // facing +z
		Zombie z = zombie(helper, 3.5, 2, 2.6, 60f);
		kit.press(p, AbilitySlot.SLOT_5, false);
		kit.onUse(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(kit.flag(p, DiamondheadKit.F_SHIELD), "the shield is up");
		long now = helper.getLevel().getGameTime();
		p.xo = p.getX();
		p.zo = p.getZ();
		kit.tick(p, Omnitrix.state(p), now);
		helper.assertTrue(near(z.getHealth(), 60f), "standing still bashes nothing: " + z.getHealth());
		// running forward into it
		p.xo = p.getX();
		p.zo = p.getZ() - 0.25;
		kit.onUse(p, InteractionHand.MAIN_HAND);
		kit.tick(p, Omnitrix.state(p), now);
		helper.assertTrue(near(z.getHealth(), 60f - DiamondheadKit.BASH_DAMAGE), "running into it: 10: " + z.getHealth());
		helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && z.hasEffect(MobEffects.WEAKNESS), "staggered");
		z.moveTo(p.getX(), p.getY(), p.getZ() + 1.1, 180f, 0f);
		kit.tick(p, Omnitrix.state(p), now + 1);
		helper.assertTrue(near(z.getHealth(), 60f - DiamondheadKit.BASH_DAMAGE), "not again straight away: " + z.getHealth());
		z.discard();
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "i want him to lift up 3 giant diamond shards from the ground and then throw them at his target, this has to
	 * have a big of a chargeup"): Shift+Z raises three held Titan Shards round him over the charge (none flying yet, he is slowed),
	 * then throws them at the zombie he looks at -- it takes the strikes and the move ends with nothing left behind.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "diamondhead_titan") // alone: the shards fly 8+ blocks
	public void diamondheadTitanShardsRiseThenStrike(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
		p.moveTo(at.x, at.y, at.z, 0f, 4f); // looking at the zombie
		Zombie z = zombie(helper, 3.5, 2, 9.5, 200f);
		double speed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(DiamondheadKit.BARRAGE.equals(kit.moveOn(AbilitySlot.SLOT_4, true, false).id()), "Shift+Z is the Titan Shards");
		helper.assertTrue(kit.barrage(p), "Shift+Z starts the Titan Shards");
		helper.assertFalse(kit.barrage(p), "not twice at once");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed * 0.5, "he plants himself for the charge");
		pump(helper, p);
		helper.runAfterDelay(DiamondheadKit.TITAN_RISE_AT[2] + 6, () -> {
			var shards = helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(8), e -> e.kind().equals("diamondhead_titan"));
			helper.assertTrue(shards.size() == 3 && shards.stream().allMatch(AlienProjectileEntity::held), "three shards rising, held: " + shards.size());
			helper.assertTrue(Math.abs(z.getHealth() - 200f) < 0.01f, "nothing thrown during the charge");
		});
		helper.runAfterDelay(DiamondheadKit.TITAN_THROW_AT[2] + 30, () -> {
			helper.assertTrue(z.getHealth() <= 200f - DiamondheadKit.TITAN_DAMAGE * 2, "the shards struck the zombie: " + z.getHealth());
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1.0e-4, "free to move again");
			z.discard();
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(40),
					e -> e.kind().equals("diamondhead_titan")).isEmpty(), "no shard left behind");
			helper.succeed();
		});
	}

	/**
	 * v0.17.2 (user: "replace his g and Shift+G with other moves that he does in the show"): G's Crystal Tether cuts a foe it strikes
	 * and hauls it toward him; Shift+G's Crystal Bridge grows a 3-wide span ahead; breaking one block of it brings the whole bridge
	 * down (user: "the entire thing falls down and disappears"); and Diamondhead breaks his crystal as fast as dirt.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "diamondhead_bridge") // alone: the bridge and the tether reach 20+ blocks
	public void diamondheadTetherBridgeAndCollapse(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f); // facing +z, level
		Zombie z = zombie(helper, 3.5, 2, 5.5, 60f);
		kit.press(p, AbilitySlot.SLOT_6, true);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.TETHER) > 0, "Shift+C: the Crystal Tether");
		helper.assertTrue(near(z.getHealth(), 60f - DiamondheadKit.TETHER_DAMAGE), "it cuts the zombie: " + z.getHealth());
		helper.assertTrue(z.getDeltaMovement().z < -0.2, "and hauls it in: " + z.getDeltaMovement());
		z.discard();
		// v0.17.2 (user: "looking down builds the bridge"): Shift+V looking down; a level 3-wide span at his feet
		p.setXRot(DiamondheadKit.CONSTRUCT_PITCH + 5f);
		kit.press(p, AbilitySlot.SLOT_5, true);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.WALL) > 0, "Shift+V looking down: the Crystal Bridge");
		int span = 0;
		for (int dz = 7; dz <= 12; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (helper.getLevel().getBlockState(BlockPos.containing(at.x + dx, at.y - 1, at.z + dz)).is(DiamondheadBlocks.CRYSTAL)) {
					span++;
				}
			}
		}
		helper.assertTrue(span >= 9, "a 3-wide span at his level (the floor is in the way of its first row): " + span);
		BlockPos one = BlockPos.containing(at.x, at.y - 1, at.z + 10);
		helper.assertTrue(helper.getLevel().getBlockState(one).is(DiamondheadBlocks.CRYSTAL), "crystal ahead");
		var crystal = helper.getLevel().getBlockState(one);
		float fast = crystal.getDestroyProgress(p, helper.getLevel(), one);
		helper.assertTrue(Math.abs(fast - p.getDestroySpeed(crystal) / 0.5f / 30f) < 1.0e-4, "Diamondhead breaks it as fast as dirt: " + fast);
		int fell = com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadRise.collapse(helper.getLevel(), one);
		helper.getLevel().setBlock(one, Blocks.AIR.defaultBlockState(), 3);
		helper.assertTrue(fell >= span, "breaking one brings the rest down: " + fell);
		int left = 0;
		for (int dz = 1; dz <= DiamondheadKit.BRIDGE_LENGTH + 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (helper.getLevel().getBlockState(BlockPos.containing(at.x + dx, at.y - 1, at.z + dz)).is(DiamondheadBlocks.CRYSTAL)) {
					left++;
				}
			}
		}
		helper.assertTrue(left == 0, "the whole bridge is gone: " + left);
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "shift+G a different attack move"; "looking up builds the dome"; "make them shoot from his hands"): Shift+G's
	 * Crystal Pillar bursts up under the zombie he looks at (14, thrown up); Shift+V looking up raises a crystal dome round him;
	 * his shards leave from beside him at chest height, not his eyes.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "diamondhead_dome")
	public void diamondheadPillarDomeAndHandShots(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
		p.moveTo(at.x, at.y, at.z, 0f, 8f);
		Zombie z = zombie(helper, 3.5, 2, 7.5, 60f);
		kit.press(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.PILLAR) > 0, "Shift+G: the Crystal Pillar");
		helper.assertTrue(near(z.getHealth(), 60f - DiamondheadKit.PILLAR_DAMAGE), "it strikes the zombie: " + z.getHealth());
		helper.assertTrue(z.getDeltaMovement().y > 0.8, "and throws it up: " + z.getDeltaMovement());
		z.discard();
		kit.press(p, AbilitySlot.SLOT_1, false);
		var shards = helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(4), e -> e.kind().equals("diamondhead_shard"));
		helper.assertTrue(shards.size() == 5, "five shards: " + shards.size());
		for (AlienProjectileEntity s : shards) {
			helper.assertTrue(s.getY() < p.getEyeY() - 0.25, "from his hands, below his eyes: " + (p.getEyeY() - s.getY()));
		}
		shards.forEach(AlienProjectileEntity::discard);
		p.setXRot(-60f);
		int placed = kit.wall(p);
		helper.assertTrue(placed > 40, "Shift+V looking up: a dome: " + placed);
		helper.assertTrue(helper.getLevel().getBlockState(p.blockPosition().above(3)).is(DiamondheadBlocks.CRYSTAL)
				|| helper.getLevel().getBlockState(p.blockPosition().above(4)).is(DiamondheadBlocks.CRYSTAL), "capped over his head");
		helper.assertTrue(helper.getLevel().getBlockState(p.blockPosition().east(3)).is(DiamondheadBlocks.CRYSTAL), "and walled round him");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	/**
	 * v0.17.2 revamp: V is Combat Mode -- +6 melee, +1 reach, faster swings; R / Shift+R / Z become the Blade Flurry, the Crystal
	 * Cyclone and the Shatter Line (the HUD's live keys say so) and Z runs the Shatter Line (16 down a 12-block line); V again
	 * puts the blades away.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadCombatModeSwapsKeysAndShatterLine(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		double dmg = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		double reach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		double swing = p.getAttributeValue(Attributes.ATTACK_SPEED);
		helper.assertTrue(DiamondheadKit.VOLLEY.equals(kit.liveMoveOn(p, AbilitySlot.SLOT_1, false, false).id()), "R is the Shard Volley out of Combat Mode");
		kit.press(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(kit.flag(p, DiamondheadKit.F_COMBAT) && kit.keyActive(p, AbilitySlot.SLOT_5), "V: Combat Mode on");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - dmg - DiamondheadKit.BLADE_BONUS) < 1.0e-4, "+6 melee");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - reach - DiamondheadKit.BLADE_REACH) < 1.0e-4, "+1 reach");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_SPEED) > swing + 0.5, "faster swings");
		helper.assertTrue(DiamondheadKit.FLURRY.equals(kit.liveMoveOn(p, AbilitySlot.SLOT_1, false, false).id()), "R: the Blade Flurry");
		helper.assertTrue(DiamondheadKit.CYCLONE.equals(kit.liveMoveOn(p, AbilitySlot.SLOT_1, true, false).id()), "Shift+R: the Crystal Cyclone");
		helper.assertTrue(DiamondheadKit.ERUPT.equals(kit.liveMoveOn(p, AbilitySlot.SLOT_4, false, false).id()), "Z stays the Crystal Eruption");
		helper.assertTrue(DiamondheadKit.BARRAGE.equals(kit.liveMoveOn(p, AbilitySlot.SLOT_4, true, false).id()), "Shift+Z stays the Barrage");
		helper.assertTrue(DiamondheadKit.SHATTER.equals(kit.liveMoveOn(p, AbilitySlot.SLOT_2, false, false).id()), "G stays the Spike Line");
		Zombie z = zombie(helper, 3.5, 2, 6.5, 60f);
		kit.press(p, AbilitySlot.SLOT_2, false);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.SHATTER) > 0 && kit.cooldownLeft(p, DiamondheadKit.ERUPT) == 0, "G ran the Spike Line");
		pump(helper, p);
		helper.runAfterDelay(24, () -> {
			helper.assertTrue(z.getHealth() <= 60f - DiamondheadKit.SPIKE_DAMAGE + 0.05f, "the spikes reached the zombie: " + z.getHealth());
			kit.press(p, AbilitySlot.SLOT_5, false);
			helper.assertFalse(kit.flag(p, DiamondheadKit.F_COMBAT), "V again: the blades go");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - dmg) < 1.0e-4, "and their bonus");
			helper.assertTrue(DiamondheadKit.VOLLEY.equals(kit.liveMoveOn(p, AbilitySlot.SLOT_1, false, false).id()), "R is the Shard Volley again");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertFalse(kit.flag(p, DiamondheadKit.F_COMBAT), "turning back ends Combat Mode");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - swing) < 1.0e-4, "and its swing speed");
			z.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 revamp: Combat Mode R chains -- slash 8, slash 8, then the stab 14, each within 1.5 s; the cooldown follows the stab. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadBladeFlurryChainsToAStab(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(kit.toggleCombat(p), "Combat Mode");
		Zombie z = zombie(helper, 3.5, 2, 3.5, 60f);
		helper.assertTrue(kit.bladeFlurry(p) == 0, "the first slash");
		helper.assertTrue(near(z.getHealth(), 60f - DiamondheadKit.FLURRY_SLASH_DAMAGE), "8: " + z.getHealth());
		helper.assertTrue(kit.bladeFlurry(p) == -1, "not twice in the same swing");
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.FLURRY) == 0, "no cooldown mid-chain");
		helper.runAfterDelay(DiamondheadKit.FLURRY_GAP + 1, () -> {
			helper.assertTrue(kit.flurryStep(p) == 1 && kit.bladeFlurry(p) == 1, "R again: the back-slash");
			helper.assertTrue(near(z.getHealth(), 60f - 2 * DiamondheadKit.FLURRY_SLASH_DAMAGE), "8 more: " + z.getHealth());
		});
		helper.runAfterDelay(2 * DiamondheadKit.FLURRY_GAP + 2, () -> {
			helper.assertTrue(kit.bladeFlurry(p) == 2, "and the stab");
			helper.assertTrue(near(z.getHealth(), 60f - 2 * DiamondheadKit.FLURRY_SLASH_DAMAGE - DiamondheadKit.FLURRY_STAB_DAMAGE), "14: " + z.getHealth());
			helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.FLURRY) > 0, "then it cools down");
			helper.assertTrue(kit.flurryStep(p) == 0, "and starts over");
		});
		helper.runAfterDelay(3 * DiamondheadKit.FLURRY_GAP + 3, () -> {
			helper.assertTrue(kit.bladeFlurry(p) == -1, "nothing until it is ready");
			z.discard();
			helper.succeed();
		});
	}

	/** v0.17.2 revamp: Combat Mode Shift+R -- the Crystal Cyclone cuts everything round him (12 each) and slows it. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadCrystalCycloneHitsAllRound(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		Vec3 mid = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		p.moveTo(mid.x, mid.y, mid.z, 0f, 0f);
		helper.assertTrue(kit.toggleCombat(p), "Combat Mode");
		Zombie[] zs = { zombie(helper, 3.5, 2, 1.0, 60f), zombie(helper, 3.5, 2, 6.0, 60f), zombie(helper, 1.0, 2, 3.5, 60f), zombie(helper, 6.0, 2, 3.5, 60f) };
		kit.press(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.CYCLONE) > 0 && kit.cooldownLeft(p, DiamondheadKit.SPEAR) == 0, "Shift+R: the Cyclone, not the Spear");
		for (Zombie z : zs) {
			helper.assertTrue(near(z.getHealth(), 60f - DiamondheadKit.CYCLONE_DAMAGE), "every side: 12: " + z.getHealth());
			helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and slowed");
			z.discard();
		}
		helper.assertTrue(kit.cyclone(p) == -1, "then it cools down");
		helper.succeed();
	}

	/**
	 * v0.17.2 revamp (user: "the blocks needs to summon out of the ground and raise up"): a construct's crystal is solid at once but
	 * hidden (rising) while a display of it slides up out of the ground; then it shows and the display goes. Removed, it sinks.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadConstructsRiseOutOfTheGroundAndSink(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		int placed = kit.wall(p);
		BlockPos mid = BlockPos.containing(p.position().add(0, 0.5, 2.5));
		var l = helper.getLevel();
		helper.assertTrue(placed >= 10 && l.getBlockState(mid).is(DiamondheadBlocks.CRYSTAL), "the wall is there (solid) at once");
		helper.assertTrue(l.getBlockState(mid).getValue(com.projecthero.mod.omnitrix.alien.diamondhead.PetrosapienCrystalBlock.RISING), "but hidden while it rises");
		java.util.function.Supplier<Integer> displays = () -> l.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,
				p.getBoundingBox().inflate(8), d -> d.getTags().contains(com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadRise.TAG)).size();
		helper.assertTrue(displays.get() == placed, "a rising crystal for every block: " + displays.get());
		helper.runAfterDelay(com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadRise.RISE_TICKS + 12, () -> {
			helper.assertFalse(l.getBlockState(mid).getValue(com.projecthero.mod.omnitrix.alien.diamondhead.PetrosapienCrystalBlock.RISING), "risen: shown");
			helper.assertTrue(displays.get() == 0, "and the displays gone: " + displays.get());
			l.setBlock(mid, Blocks.AIR.defaultBlockState(), 3);
			helper.assertTrue(displays.get() == 1, "a crystal going sinks back into the ground");
		});
		helper.runAfterDelay(com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadRise.RISE_TICKS * 2 + 18, () -> {
			helper.assertTrue(displays.get() == 0, "and is gone");
			helper.succeed();
		});
	}

	// ================================================================ Upgrade

	/** v0.17.2: the Crystal Slide is the Ice Slide in crystal -- a toggle, a ramp grows under him, X again steps off. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadsCrystalSlideIsAToggleRideOnARamp(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		Vec3 up = helper.absoluteVec(new Vec3(3.5, 4, 3.5));
		p.moveTo(up.x, up.y, up.z, 0f, 0f);
		p.setOnGround(false);
		double step = p.getAttributeValue(Attributes.STEP_HEIGHT); // his body's own step-up (above a human's)
		helper.assertTrue(kit.slide(p) == 1 && kit.flag(p, DiamondheadKit.F_SLIDING), "X starts the slide");
		helper.assertTrue(p.getAttributeValue(Attributes.STEP_HEIGHT) > step + 0.5, "the slide steps up a block");
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.6, p.getZ());
			helper.assertTrue(helper.getLevel().getBlockState(under).is(com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadBlocks.CRYSTAL),
					"crystal grows in under him");
			helper.assertTrue(kit.flag(p, DiamondheadKit.F_SLIDING), "and he keeps riding (no time limit)");
			helper.assertTrue(kit.slide(p) == 0 && !kit.flag(p, DiamondheadKit.F_SLIDING), "X again steps off");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.STEP_HEIGHT) - step) < 1.0e-4, "the slide's step-up goes with it");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradesLiquidFormIsAToggleThatSeepsAndEndsOnOtherMoves(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		helper.assertTrue(near(p.getMaxHealth(), 28f), "28 health");
		helper.assertTrue(kit.liquidSlide(p) && kit.flag(p, UpgradeKit.F_SLIDE), "X melts him into the Liquid Form");
		helper.assertTrue(p.getBbHeight() < 0.5f, "a puddle: fits under a half-block gap (" + p.getBbHeight() + ")");
		p.setDeltaMovement(Vec3.ZERO);
		// soft blocks and slabs have no collision for him from the side; stone still does
		BlockPos at = p.blockPosition();
		net.minecraft.world.phys.shapes.CollisionContext ctx = net.minecraft.world.phys.shapes.CollisionContext.of(p);
		BlockPos side = at.east();
		helper.getLevel().setBlock(side, Blocks.OAK_LEAVES.defaultBlockState(), 3);
		helper.assertTrue(helper.getLevel().getBlockState(side).getCollisionShape(helper.getLevel(), side, ctx).isEmpty(), "seeps through leaves");
		helper.getLevel().setBlock(side, Blocks.STONE_SLAB.defaultBlockState(), 3);
		helper.assertTrue(helper.getLevel().getBlockState(side).getCollisionShape(helper.getLevel(), side, ctx).isEmpty(), "seeps through slabs");
		helper.getLevel().setBlock(side, Blocks.STONE.defaultBlockState(), 3);
		helper.assertFalse(helper.getLevel().getBlockState(side).getCollisionShape(helper.getLevel(), side, ctx).isEmpty(), "but not stone");
		// climbing: only pressed into the wall while looking up
		p.horizontalCollision = true;
		p.setXRot(0f);
		helper.assertFalse(kit.climbs(p), "looking ahead: no climb");
		p.setXRot(-70f);
		helper.assertTrue(kit.climbs(p), "looking up: flows up the wall");
		p.horizontalCollision = false;
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(kit.flag(p, UpgradeKit.F_SLIDE), "no time limit");
			// a ceiling one block up: no room to rise
			BlockPos ceiling = p.blockPosition().above();
			helper.getLevel().setBlock(ceiling, Blocks.STONE.defaultBlockState(), 3);
			helper.assertFalse(kit.liquidSlide(p), "no room to rise");
			helper.getLevel().setBlock(ceiling, Blocks.AIR.defaultBlockState(), 3);
			// any other move rises him first
			kit.opticBeam(p);
			helper.assertFalse(kit.flag(p, UpgradeKit.F_SLIDE), "another move ends the Liquid Form");
			helper.assertTrue(Math.abs(p.getBbHeight() - 1.8f) < 0.01f, "full height again");
			kit.cooldown(p, UpgradeKit.SLIDE, 0);
			kit.liquidSlide(p);
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(Math.abs(p.getBbHeight() - 1.8f) < 0.01f && Math.abs(p.getScale() - 1f) < 1.0e-4, "turning back: full size");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradeMergesWithAFurnaceAndPilotsAGolem(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		BlockPos furnace = helper.absolutePos(new BlockPos(3, 2, 4));
		helper.getLevel().setBlock(furnace, Blocks.FURNACE.defaultBlockState(), 3);
		helper.assertTrue(kit.mergeWith(p, furnace), "merged with the furnace");
		helper.assertTrue(UpgradeKit.merge(p) != null && UpgradeKit.merge(p).kind() == UpgradeKit.MergeKind.FURNACE && kit.flag(p, UpgradeKit.F_MERGED),
				"inside the furnace");
		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "unseen");
		helper.assertFalse(kit.mergeWith(p, furnace), "only one merge at a time");
		kit.endMerge(p, false);
		helper.assertTrue(UpgradeKit.merge(p) == null && !p.hasEffect(MobEffects.INVISIBILITY) && !kit.flag(p, UpgradeKit.F_MERGED), "out again, seen");
		// the golem
		IronGolem golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		Vec3 g = helper.absoluteVec(new Vec3(3.5, 2, 3.5));
		golem.moveTo(g.x, g.y, g.z, 0f, 0f);
		golem.setPersistenceRequired();
		helper.getLevel().addFreshEntity(golem);
		kit.cooldown(p, UpgradeKit.MERGE, 0);
		helper.assertTrue(kit.mergeWith(p, golem), "merged with the golem");
		float hp = p.getHealth();
		float ghp = golem.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 6f);
		helper.assertTrue(p.getHealth() == hp && golem.getHealth() < ghp, "inside the golem: it takes the hit");
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			p.teleportTo(p.getX() + 1.5, p.getY(), p.getZ());
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(golem.distanceTo(p) < 0.5, "the golem goes where he goes");
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.assertTrue(UpgradeKit.merge(p) == null && !p.hasEffect(MobEffects.INVISIBILITY), "turning back leaves the merge, seen again");
				golem.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradesMovesHitCoolDownAndJamRedstone(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 4.5, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 10f);
		helper.assertTrue(kit.opticBeam(p), "Optic Beam");
		helper.assertTrue(near(z.getHealth(), 100f - UpgradeKit.BEAM_DAMAGE), "8 damage");
		helper.assertFalse(kit.opticBeam(p), "then cools down");
		helper.assertTrue(kit.engulf(p, z) && near(z.getHealth(), 100f - UpgradeKit.BEAM_DAMAGE - UpgradeKit.GRAB_DAMAGE), "v0.17.2 revamp: Liquid Engulf (6)");
		var slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		helper.assertTrue(slow != null && slow.getAmplifier() >= 9, "stunned");
		float before = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(kit.fireChargedBeam(p) >= 1 && near(before - z.getHealth(), UpgradeKit.CHARGED_DAMAGE), "the charged beam pierces for 18");
		// v0.17.2 (user: "change the damage on the ben 10 aliens to be on par with the elemental power abilities"): the EMP falls off
		helper.assertTrue(near(UpgradeKit.empDamage(0), UpgradeKit.EMP_CENTER) && near(UpgradeKit.empDamage(UpgradeKit.EMP_RADIUS), UpgradeKit.EMP_EDGE)
				&& near(UpgradeKit.empDamage(UpgradeKit.EMP_RADIUS / 2), (UpgradeKit.EMP_CENTER + UpgradeKit.EMP_EDGE) / 2f), "EMP: 60 at the centre, 50 halfway, 40 at the edge");
		// Upgrade Tool
		ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
		pick.setDamageValue(200);
		p.setItemInHand(InteractionHand.MAIN_HAND, pick);
		helper.assertTrue(kit.upgradeTool(p) && p.getMainHandItem().getDamageValue() == 200 - pick.getMaxDamage() / 2, "half the wear repaired");
		ItemStack fresh = new ItemStack(Items.IRON_PICKAXE);
		p.setItemInHand(InteractionHand.MAIN_HAND, fresh);
		kit.cooldown(p, UpgradeKit.REPAIR, 0);
		var unbreaking = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.UNBREAKING);
		helper.assertTrue(kit.upgradeTool(p) && EnchantmentHelper.getItemEnchantmentLevel(unbreaking, p.getMainHandItem()) == 1, "a whole tool gains Unbreaking");
		helper.assertFalse(kit.upgradeTool(p), "it cools down");
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		// the EMP jams redstone: a lamp lit by a redstone block goes dark, then comes back
		BlockPos lamp = helper.absolutePos(new BlockPos(6, 2, 6));
		helper.getLevel().setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), 3);
		helper.getLevel().setBlock(lamp.east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
		helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "the lamp is lit");
		UpgradeEmp.jam(helper.getLevel(), lamp, 2, 20);
		helper.assertTrue(UpgradeEmp.jammed(helper.getLevel(), lamp.east()), "the redstone block is jammed");
		helper.runAfterDelay(10, () -> {
			helper.assertFalse(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "jammed: the lamp goes dark");
			helper.runAfterDelay(20, () -> {
				helper.assertFalse(UpgradeEmp.jammed(helper.getLevel(), lamp.east()), "the jam lapses");
				helper.assertTrue(helper.getLevel().getBlockState(lamp).getValue(RedstoneLampBlock.LIT), "and the lamp lights again");
				z.discard();
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- v0.17.2 Upgrade revamp (user: "Yes, build them")

	private static IronGolem golem(GameTestHelper helper, double x, double y, double z) {
		IronGolem g = EntityType.IRON_GOLEM.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		g.moveTo(at.x, at.y, at.z, 180f, 0f);
		g.setPersistenceRequired();
		helper.getLevel().addFreshEntity(g);
		return g;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradeStretchFistPunchesOutEightBlocks(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		helper.assertTrue(kit.moveOn(AbilitySlot.SLOT_1, true, false).id().equals(UpgradeKit.FIST), "Shift+R is the Stretch Fist");
		helper.assertTrue(kit.moveOn(AbilitySlot.SLOT_6, true, false).id().equals(UpgradeKit.TAKEOVER), "Shift+C is Tech Takeover");
		helper.assertTrue(kit.moveOn(AbilitySlot.SLOT_5, true, false).id().equals(UpgradeKit.REPAIR), "Shift+V is the Upgrade Tool");
		Zombie far = zombie(helper, 3.5, 2, 7.0, 100f); // 5.5 blocks ahead
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 8f);
		helper.assertTrue(kit.stretchFist(p), "Shift+R: the fist shoots out");
		helper.assertTrue(near(far.getHealth(), 100f - UpgradeKit.FIST_DAMAGE), "10 damage at 5.5 blocks: " + far.getHealth());
		helper.assertTrue(far.getDeltaMovement().z > 0.3, "slammed back: " + far.getDeltaMovement());
		helper.assertFalse(kit.stretchFist(p), "then it cools down");
		far.discard();
		Zombie tooFar = zombie(helper, 3.5, 2, 11.5, 100f); // 10 blocks: out of reach
		kit.cooldown(p, UpgradeKit.FIST, 0);
		helper.assertTrue(kit.stretchFist(p) && near(tooFar.getHealth(), 100f), "past 8 blocks it punches air");
		tooFar.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void upgradeTechTakeoverTurnsMachinesAndShortsTheLiving(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		IronGolem g1 = golem(helper, 3.5, 2, 5.5);
		Zombie foe = zombie(helper, 6.5, 2, 6.5, 100f);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
		helper.assertTrue(UpgradeTakeover.mechanical(g1) && !UpgradeTakeover.mechanical(foe), "a golem is a machine, a zombie is not");
		helper.assertTrue(kit.techTakeover(p), "Shift+C: the nanite bolt");
		helper.assertTrue(UpgradeTakeover.controlledBy(g1, p), "the golem is his");
		helper.assertTrue(UpgradeTakeover.until(g1) - helper.getLevel().getGameTime() == UpgradeTakeover.TICKS, "for 15 s");
		helper.assertFalse(kit.techTakeover(p), "it cools down");
		// it never hurts him
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		g1.doHurtTarget(p);
		helper.assertTrue(p.getHealth() == hp, "his machine can't hurt him");
		// max 2 (Shift+C): a third lets the oldest go
		IronGolem g2 = golem(helper, 1.5, 2, 5.5);
		IronGolem g3 = golem(helper, 5.5, 2, 3.5);
		helper.assertTrue(UpgradeTakeover.take(p, g2, UpgradeTakeover.TICKS, true) && UpgradeTakeover.count(p, true) == 2, "two held");
		helper.assertTrue(UpgradeTakeover.take(p, g3, UpgradeTakeover.TICKS, true), "a third");
		helper.assertTrue(UpgradeTakeover.count(p, true) == 2 && !UpgradeTakeover.controlledBy(g1, p) && UpgradeTakeover.controlledBy(g3, p),
				"the oldest lets go");
		UpgradeTakeover.release(g2);
		UpgradeTakeover.release(g3);
		g2.discard();
		g3.discard();
		UpgradeTakeover.take(p, g1, UpgradeTakeover.TICKS, true);
		// a living target is shorted out instead (the old Overload)
		Zombie z = zombie(helper, 3.5, 2, 4.0, 100f);
		kit.cooldown(p, UpgradeKit.TAKEOVER, 0);
		helper.assertTrue(kit.techTakeover(p), "Shift+C on a zombie");
		helper.assertTrue(near(z.getHealth(), 100f - UpgradeKit.OVERLOAD_DAMAGE) && z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && z.hasEffect(MobEffects.WEAKNESS),
				"shorted out: 8, slowed and weakened");
		helper.assertFalse(UpgradeTakeover.controlledBy(z, p), "never taken over");
		z.discard();
		// the held golem turns on his foe: a bolt a second in his name
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(UpgradeTakeover.controlledBy(g1, p), "still his (mock player in the player list: " + (helper.getLevel().getServer().getPlayerList().getPlayer(p.getUUID()) != null) + ")");
			helper.assertTrue(foe.getHealth() < 100f, "the golem fights his foe: " + foe.getHealth());
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(UpgradeTakeover.controller(g1) == null && UpgradeTakeover.count(p, false) == 0, "turning back lets every machine go");
			g1.discard();
			foe.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradeEmpBurstTakesOverMachinesInRange(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		IronGolem g = golem(helper, 3.5, 2, 5.5);
		Zombie z = zombie(helper, 1.5, 2, 3.5, 100f);
		float ghp = g.getHealth();
		helper.assertTrue(kit.empBurst(p), "Shift+Z: EMP Burst");
		helper.assertTrue(g.isAlive() && g.getHealth() < ghp - UpgradeKit.EMP_EDGE * 2f + 0.1f, "machines short out twice as hard: " + (ghp - g.getHealth()));
		helper.assertTrue(UpgradeTakeover.controlledBy(g, p) && UpgradeTakeover.until(g) - helper.getLevel().getGameTime() == UpgradeTakeover.EMP_TICKS,
				"and are his for 10 s");
		helper.assertTrue(UpgradeTakeover.count(p, true) == 0, "EMP holds don't count towards Tech Takeover's 2");
		helper.assertFalse(UpgradeTakeover.controlledBy(z, p), "a zombie only shorts out");
		helper.assertFalse(g.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "his machine shakes the short-out off");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(UpgradeTakeover.controller(g) == null, "released on turning back");
		g.discard();
		z.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradeLiquidFormSeepsThroughBarsAndGeysersUp(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		helper.assertTrue(kit.liquidSlide(p), "X: the Liquid Form");
		p.setDeltaMovement(Vec3.ZERO);
		net.minecraft.world.phys.shapes.CollisionContext ctx = net.minecraft.world.phys.shapes.CollisionContext.of(p);
		BlockPos side = p.blockPosition().west();
		for (net.minecraft.world.level.block.state.BlockState s : new net.minecraft.world.level.block.state.BlockState[] { Blocks.IRON_BARS.defaultBlockState(),
				Blocks.OAK_FENCE.defaultBlockState(), Blocks.IRON_DOOR.defaultBlockState() }) {
			helper.getLevel().setBlock(side, s, 3);
			helper.assertTrue(helper.getLevel().getBlockState(side).getCollisionShape(helper.getLevel(), side, ctx).isEmpty(), "seeps through " + s);
		}
		helper.getLevel().setBlock(side, Blocks.AIR.defaultBlockState(), 3);
		// no foe over the puddle: R is just the Optic Beam (it rises him)
		helper.assertFalse(kit.geyser(p), "nothing to geyser through");
		// a foe over the puddle: any attack key is the Geyser
		Zombie z = zombie(helper, 4.5, 2, 1.5, 100f);
		kit.press(p, AbilitySlot.SLOT_1, false);
		helper.assertFalse(kit.flag(p, UpgradeKit.F_SLIDE), "he rises out of the puddle");
		helper.assertTrue(near(z.getHealth(), 100f - UpgradeKit.GEYSER_DAMAGE), "Geyser: 8 -- " + z.getHealth());
		helper.assertTrue(z.getDeltaMovement().y > 0.5, "launched: " + z.getDeltaMovement());
		helper.assertTrue(kit.cooldownLeft(p, UpgradeKit.GEYSER) > 0 && kit.cooldownLeft(p, UpgradeKit.BEAM) == 0, "the Geyser cools down, not the beam");
		z.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void upgradeAllyUpgradeArmoursASquadMateOnly(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		ServerPlayer mate = mock(helper, 3.5, 2, 3.5);
		ServerPlayer stranger = mock(helper, 5.5, 2, 1.5);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("UpgradeAlly" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			helper.assertFalse(kit.mergeWithAlly(p, stranger), "not a squad mate: refused");
			double armor = mate.getAttributeValue(Attributes.ARMOR);
			double tough = mate.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
			helper.assertTrue(kit.mergeWithAlly(p, mate), "C on a squad mate: Ally Upgrade");
			helper.assertTrue(UpgradeKit.merge(p).kind() == UpgradeKit.MergeKind.ALLY && p.getVehicle() == mate, "riding them as their armour");
			helper.assertTrue(Math.abs(mate.getAttributeValue(Attributes.ARMOR) - armor - UpgradeKit.ALLY_ARMOR) < 1.0e-4
					&& Math.abs(mate.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - tough - UpgradeKit.ALLY_TOUGHNESS) < 1.0e-4, "+8 armour, +2 toughness");
			helper.assertTrue(UpgradeKit.allySeat(mate, p) != null, "seated on their back");
			float hp = p.getHealth();
			p.invulnerableTime = 0;
			p.hurt(helper.getLevel().damageSources().magic(), 6f);
			helper.assertTrue(p.getHealth() == hp, "he takes no damage");
			Zombie z = zombie(helper, 3.5, 2, 5.5, 100f);
			helper.assertTrue(kit.allyAttack(mate, z) && near(z.getHealth(), 100f - UpgradeKit.ALLY_BEAM_DAMAGE), "their hit fires his beam (6)");
			helper.assertFalse(kit.allyAttack(mate, z), "once every 2 s");
			helper.assertFalse(kit.allyAttack(stranger, z), "not for anyone else");
			z.discard();
			kit.endMerge(p, false);
			helper.assertTrue(Math.abs(mate.getAttributeValue(Attributes.ARMOR) - armor) < 1.0e-4 && p.getVehicle() == null, "C again: off, their armour back");
			// turning back mid-merge lets go too
			kit.cooldown(p, UpgradeKit.MERGE, 0);
			helper.assertTrue(kit.mergeWithAlly(p, mate), "again");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertTrue(Math.abs(mate.getAttributeValue(Attributes.ARMOR) - armor) < 1.0e-4 && p.getVehicle() == null && UpgradeKit.merge(p) == null,
					"turning back ends it cleanly");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void upgradeOverclockBoostsArrowsGunsAndShields(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		ItemStack bow = new ItemStack(Items.BOW);
		p.setItemInHand(InteractionHand.MAIN_HAND, bow);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(kit.overclock(p) && UpgradeKit.overclocked(p), "V: Overclock the bow");
		Arrow arrow = new Arrow(helper.getLevel(), p, new ItemStack(Items.ARROW), bow);
		arrow.setDeltaMovement(0, 0, 2.0);
		helper.getLevel().addFreshEntity(arrow);
		helper.assertTrue(arrow.getPierceLevel() >= 1, "the arrow pierces one");
		helper.assertTrue(Math.abs(arrow.getDeltaMovement().length() - 2.0 * UpgradeKit.ARROW_SPEED) < 1.0e-3, "and flies 50% faster: " + arrow.getDeltaMovement());
		arrow.discard();
		// a disabled shield is ready again at once
		p.getCooldowns().addCooldown(Items.SHIELD, 100);
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime());
		helper.assertFalse(p.getCooldowns().isOnCooldown(Items.SHIELD), "the shield shrugs off the disabling hit");
		// the mod's guns: 50% faster and never reload
		ItemStack gun = new ItemStack(com.projecthero.mod.firearm.item.FirearmItems.PUNISHER_PISTOL);
		p.setItemInHand(InteractionHand.MAIN_HAND, gun);
		com.projecthero.mod.firearm.FirearmStack.setMagazine(gun, 0);
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime() + 1);
		var data = com.projecthero.mod.firearm.Firearms.of(gun);
		helper.assertTrue(com.projecthero.mod.firearm.FirearmStack.magazine(gun, data) == data.magazineSize, "the magazine stays full");
		helper.assertTrue(com.projecthero.mod.firearm.FirearmHooks.get().fireIntervalFactor(p) < 0.7f, "fires 50% faster: "
				+ com.projecthero.mod.firearm.FirearmHooks.get().fireIntervalFactor(p));
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(com.projecthero.mod.firearm.FirearmHooks.get().fireIntervalFactor(p) > 0.99f, "back to normal after");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void upgradeToolTakesTheOffhandAndWornArmour(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.setDamageValue(200);
		p.setItemInHand(InteractionHand.OFF_HAND, sword);
		helper.assertTrue(kit.upgradeTool(p) && sword.getDamageValue() == 200 - sword.getMaxDamage() / 2, "empty main hand: the offhand item is repaired");
		p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		ItemStack chest = new ItemStack(Items.IRON_CHESTPLATE);
		chest.setDamageValue(150);
		p.setItemSlot(EquipmentSlot.CHEST, chest);
		kit.cooldown(p, UpgradeKit.REPAIR, 0);
		p.setXRot(0f);
		helper.assertFalse(kit.upgradeTool(p), "looking ahead with empty hands: nothing");
		p.setXRot(70f);
		helper.assertTrue(kit.upgradeTool(p) && p.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() < 150, "looking down: the worn chestplate");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void upgradeElectricShockWaterAndLiquidMetalBody(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		UpgradeKit kit = (UpgradeKit) Aliens.kit(UpgradeKit.ID);
		var bolt = helper.getLevel().damageSources().lightningBolt();
		helper.assertTrue(near(kit.incomingDamage(p, bolt, 4f), 6f), "Electric Shock: lightning x1.5");
		helper.assertTrue(near(kit.incomingDamage(p, helper.getLevel().damageSources().magic(), 4f), 4f), "magic as usual");
		BlockPos furnace = helper.absolutePos(new BlockPos(3, 2, 4));
		helper.getLevel().setBlock(furnace, Blocks.FURNACE.defaultBlockState(), 3);
		helper.assertTrue(kit.mergeWith(p, furnace), "merged with a furnace");
		kit.allowDamage(p, bolt, 4f);
		helper.assertTrue(UpgradeKit.merge(p) == null && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a shock throws him out, stunned");
		// Liquid Metal Body: one in four arrows / thrown things passes through (picked by the projectile's id, both sides agree)
		Arrow through = new Arrow(EntityType.ARROW, helper.getLevel());
		through.setUUID(new java.util.UUID(7L, 8L));
		Arrow hits = new Arrow(EntityType.ARROW, helper.getLevel());
		hits.setUUID(new java.util.UUID(7L, 9L));
		helper.assertTrue(p.deflection(through) == UpgradeKit.PASS_THROUGH, "this arrow passes through");
		helper.assertTrue(p.deflection(hits) == net.minecraft.world.entity.projectile.ProjectileDeflection.NONE, "this one hits");
		var fireball = new net.minecraft.world.entity.projectile.SmallFireball(EntityType.SMALL_FIREBALL, helper.getLevel());
		fireball.setUUID(new java.util.UUID(7L, 8L));
		helper.assertTrue(p.deflection(fireball) == net.minecraft.world.entity.projectile.ProjectileDeflection.NONE, "a fireball always hits");
		// Water: the Liquid Form is 40% slower in it
		p.removeAllEffects();
		kit.cooldown(p, UpgradeKit.SLIDE, 0);
		helper.assertTrue(kit.liquidSlide(p), "the Liquid Form");
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime() + 5);
		double dry = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		try {
			java.lang.reflect.Field f = net.minecraft.world.entity.Entity.class.getDeclaredField("wasTouchingWater");
			f.setAccessible(true);
			f.setBoolean(p, true);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		kit.tick(p, Omnitrix.state(p), helper.getLevel().getGameTime() + 6);
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < dry * 0.65, "in water: 40% slower (" + dry + " -> " + p.getAttributeValue(Attributes.MOVEMENT_SPEED) + ")");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.1) < 1.0e-4, "and it all comes off");
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "give every alien resistance level 1"): a transformed alien carries a hidden Resistance I (no icon, no
	 * particles), Ghostfreak loses it in True Form, and turning back takes it off.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void everyAlienHasHiddenResistanceOne(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			var res = p.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE);
			helper.assertTrue(res != null && res.getAmplifier() == 0 && !res.showIcon() && !res.isVisible(), "Resistance I, hidden");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertFalse(p.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE), "gone on turning back");
			helper.succeed();
		});
	}

	/** v0.17.2 (user: "when the player transforms into a ben 10 alien make their name change to the alien's name"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aTransformedPlayerGoesByTheAliensName(GameTestHelper helper) {
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		var name = p.getDisplayName().getContents();
		helper.assertTrue(name instanceof net.minecraft.network.chat.contents.TranslatableContents tc && tc.getKey().equals("projecthero.omnitrix.alien.diamondhead"),
				"the name is Diamondhead: " + p.getDisplayName());
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(p.getDisplayName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
				&& tc.getKey().startsWith("projecthero.omnitrix.alien."), "his own name again after turning back");
		helper.succeed();
	}

	// ================================================================ Ghostfreak

	@GameTest(template = EMPTY_STRUCTURE)
	public void ghostfreakFloatsAndHalvesPhysicalBlows(GameTestHelper helper) {
		ServerPlayer p = as(helper, GhostfreakKit.ID);
		helper.assertTrue(near(p.getMaxHealth(), 26f), "26 health");
		helper.assertTrue(p.getAbilities().mayfly, "v0.17.2: always ready to fly (a double-tap of jump; no key)");
		helper.assertTrue(kitHasNoFlightKey(), "no flight key: Ghost Flight is the double-tap");
		// v0.17.2 (user: "remove the slow fall from ghostfreak but dont make him take fall damage"): normal gravity, no fall damage
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.GRAVITY) - 0.08) < 1.0e-4, "he falls at normal speed");
		float before = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().fall(), 40f);
		helper.assertTrue(near(p.getHealth(), before), "and never takes fall damage: " + p.getHealth());
		Zombie z = zombie(helper, 3.5, 2, 3.5, 20f);
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // no difficulty scaling on the blow
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 8f);
		float lost = hp - p.getHealth();
		helper.assertTrue(lost > 0f && lost < 8f * 0.6f, "a blow does half: " + lost);
		hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().magic(), 4f);
		helper.assertTrue(near(hp - p.getHealth(), 4f), "magic does it all");
		hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().drown(), 4f);
		helper.assertTrue(p.getHealth() == hp, "he never drowns");
		helper.assertTrue(GhostfreakKit.physical(helper.getLevel().damageSources().fall()) && !GhostfreakKit.physical(helper.getLevel().damageSources().inFire()),
				"a fall is physical, fire is not");
		z.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void ghostfreaksPhaseIsTimedAndLeavesNothingBehind(GameTestHelper helper) {
		ServerPlayer p = as(helper, GhostfreakKit.ID);
		GhostfreakKit kit = (GhostfreakKit) Aliens.kit(GhostfreakKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 20f);
		helper.assertTrue(kit.phase(p) && kit.phasing(p) && AlienKit.activeKit(p).phasing(p), "intangible");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 8f);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().inWall(), 2f);
		helper.assertTrue(p.getHealth() == hp, "nothing physical touches him (walls neither)");
		pump(helper, p);
		helper.runAfterDelay(GhostfreakKit.PHASE_TICKS + 3, () -> {
			helper.assertFalse(kit.phasing(p), "the phase runs out after 5 s");
			helper.assertTrue(kit.cooldownLeft(p, GhostfreakKit.PHASE) > GhostfreakKit.PHASE_COOLDOWN - 10, "and then cools down");
			// phased and faded, then turned back: nothing stuck
			kit.cooldown(p, GhostfreakKit.PHASE, 0);
			helper.assertTrue(kit.phase(p) && kit.fade(p) && p.hasEffect(MobEffects.INVISIBILITY), "phased and invisible");
			p.getAbilities().flying = true;
			Omnitrix.tick(p);
			helper.assertTrue(kit.flag(p, GhostfreakKit.F_FLY), "flying (the double-tap)");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertFalse(kit.phasing(p), "no stuck intangibility");
			helper.assertTrue(AlienKit.activeKit(p) == null, "human again");
			helper.assertFalse(p.hasEffect(MobEffects.INVISIBILITY), "no stuck invisibility");
			helper.assertTrue(Math.abs(p.getAbilities().getFlyingSpeed() - 0.05f) < 1.0e-4f, "no stuck ghost flight (the mock player is creative, so mayfly itself stays)");
			z.discard();
			helper.succeed();
		});
	}

	/** v0.17.2: Ghostfreak's blows are +20% in the dark (Creature of the Dark), and a test world may be dark. */
	private static boolean ghostDmg(float got, float base) {
		return near(got, base) || near(got, base * GhostfreakKit.DARK_DAMAGE_FACTOR);
	}

	private static boolean kitHasNoFlightKey() {
		return Aliens.kit(GhostfreakKit.ID).moves().stream().noneMatch(m -> GhostfreakKit.FLIGHT.equals(m.id())); // v0.17.2: a passive now, no entry at all
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ghostfreaksMovesHitScareAndPossess(GameTestHelper helper) {
		ServerPlayer p = as(helper, GhostfreakKit.ID);
		GhostfreakKit kit = (GhostfreakKit) Aliens.kit(GhostfreakKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5, 100f); // under 150: not a boss
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 15f);
		// v0.17.2 revamp: R is a three-swipe combo -- 10, 10, then the Reaping Rake (18)
		helper.assertTrue(kit.claw(p) == 1 && ghostDmg(100f - z.getHealth(), GhostfreakKit.CLAW_DAMAGE), "Spectral Claw: 5");
		helper.assertTrue(z.hasEffect(MobEffects.WITHER), "it withers");
		helper.assertTrue(kit.claw(p) == 0, "a short gap between swipes");
		helper.assertTrue(GhostfreakKit.comboStep(p) == 1, "the combo carries on");
		kit.cooldown(p, GhostfreakKit.CLAW, 0);
		z.invulnerableTime = 0;
		kit.claw(p);
		kit.cooldown(p, GhostfreakKit.CLAW, 0);
		float before = z.getHealth();
		z.invulnerableTime = 0;
		helper.assertTrue(GhostfreakKit.comboStep(p) == 2 && kit.claw(p) == 1, "the third swipe: the Reaping Rake");
		helper.assertTrue(ghostDmg(before - z.getHealth(), GhostfreakKit.RAKE_DAMAGE), "the Rake: 9, got " + (before - z.getHealth()));
		helper.assertTrue(GhostfreakKit.comboStep(p) == 0 && kit.cooldownLeft(p, GhostfreakKit.CLAW) > GhostfreakKit.CLAW_GAP, "then a longer rest");
		z.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
		z.invulnerableTime = 0;
		helper.assertTrue(kit.scream(p) == 1 && GhostfreakKit.fleeing(p, z), "Wail: it flees");
		helper.assertTrue(kit.scream(p) == 0, "the wail cools down");
		z.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
		// possession: he dives INTO a second zombie, which fights for him
		Zombie host = zombie(helper, 5.5, 2, 1.5, 40f);
		helper.assertTrue(kit.possess(p, host) && GhostfreakKit.possessed(p) == host && kit.flag(p, GhostfreakKit.F_POSSESS), "possessed");
		helper.assertTrue(p.getVehicle() == host && GhostfreakKit.insideHost(p), "riding inside it");
		helper.assertTrue(p.hasEffect(MobEffects.INVISIBILITY), "unseen inside");
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(z), 8f);
		helper.assertTrue(p.getHealth() == hp, "nothing reaches him inside");
		helper.assertFalse(kit.possess(p, z), "one host at a time");
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(host.getTarget() != p, "the host never turns on him");
			// v0.17.2 revamp 2: he steers it now -- it hunts nothing on its own (OmnitrixGhostfreakGameTests drives it)
			helper.assertTrue(host.getTarget() == null, "it does nothing he does not tell it to: " + host.getTarget());
			// G again bursts him out through it
			kit.press(p, com.projecthero.mod.hero.AbilitySlot.SLOT_2, false);
			helper.assertTrue(GhostfreakKit.possessed(p) == null && p.getVehicle() == null, "G again: out");
			helper.assertTrue(host.getHealth() < 40f - GhostfreakKit.BURST_DAMAGE + 2f, "the burst tears through the host: " + host.getHealth());
			helper.assertTrue(kit.cooldownLeft(p, GhostfreakKit.POSSESS) > GhostfreakKit.POSSESS_COOLDOWN - 10, "possession cools down after");
			helper.assertFalse(p.hasEffect(MobEffects.INVISIBILITY), "seen again");
			Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
			p.moveTo(at.x, at.y, at.z, 0f, 15f);
			helper.assertTrue(kit.tentacles(p) >= 1 && GhostfreakKit.gripped(p, z), "Chest Tentacles grab the zombie in front");
			// any other move bursts him out too
			kit.cooldown(p, GhostfreakKit.POSSESS, 0);
			helper.assertTrue(kit.possess(p, host), "back inside");
			// v0.17.2 revamp 2: Shift+G is Phantom Grasp (Soul Siphon is gone)
			helper.assertTrue(kit.phantomGrasp(p, z) > 0f, "Phantom Grasp");
			helper.assertTrue(GhostfreakKit.possessed(p) == null && p.getVehicle() == null, "another move burst him out");
			helper.assertTrue(z.hasEffect(MobEffects.DIG_SLOWDOWN) && z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the grasp's slowness and mining fatigue");
			helper.assertTrue(kit.frightAura(p) && kit.flag(p, GhostfreakKit.F_AURA), "Fright Aura");
			helper.runAfterDelay(10, () -> {
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.assertTrue(host.getTarget() == null, "turning back frees the host");
				helper.assertFalse(GhostfreakKit.fleeing(p, z), "and the fear lifts");
				z.discard();
				host.discard();
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void ghostfreaksEctoRushAndFadeAmbush(GameTestHelper helper) {
		ServerPlayer p = as(helper, GhostfreakKit.ID);
		GhostfreakKit kit = (GhostfreakKit) Aliens.kit(GhostfreakKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 1.5, 100f); // right where he stands: the rush goes through it
		helper.assertTrue(kit.ectoRush(p) && kit.phasing(p) && kit.flag(p, GhostfreakKit.F_RUSH), "Shift+X: intangible, rushing");
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(ghostDmg(100f - z.getHealth(), GhostfreakKit.RUSH_DAMAGE) && z.hasEffect(MobEffects.WEAKNESS), "passed through: 10, weakened: " + z.getHealth());
			helper.runAfterDelay(GhostfreakKit.RUSH_TICKS + 2, () -> {
				helper.assertFalse(kit.phasing(p) || kit.flag(p, GhostfreakKit.F_RUSH), "the rush ends solid");
				helper.assertTrue(ghostDmg(100f - z.getHealth(), GhostfreakKit.RUSH_DAMAGE), "once per rush");
				// Fade, then an Ambush out of it
				helper.assertTrue(kit.fade(p) && p.hasEffect(MobEffects.INVISIBILITY), "faded");
				float hp = z.getHealth();
				z.invulnerableTime = 0;
				kit.onAttack(p, z);
				helper.assertTrue(ghostDmg(hp - z.getHealth(), GhostfreakKit.TOUCH_DAMAGE + GhostfreakKit.AMBUSH_DAMAGE), "Ambush: +12, got " + (hp - z.getHealth()));
				helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS) && !kit.flag(p, GhostfreakKit.F_INVIS), "blinded, and the fade is over");
				hp = z.getHealth();
				z.invulnerableTime = 0;
				kit.onAttack(p, z);
				helper.assertTrue(ghostDmg(hp - z.getHealth(), GhostfreakKit.TOUCH_DAMAGE), "an ordinary touch after: +2");
				z.discard();
				helper.succeed();
			});
		});
	}
}
