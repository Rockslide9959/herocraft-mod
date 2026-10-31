package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixAbilityManager;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.diamondhead.DiamondheadKit;
import com.projecthero.mod.omnitrix.alien.fourarms.FourArmsKit;
import com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.heatblast.HeatblastKit;
import com.projecthero.mod.omnitrix.alien.ripjaws.RipjawsKit;
import com.projecthero.mod.omnitrix.alien.stinkfly.StinkflyKit;
import com.projecthero.mod.omnitrix.alien.upgrade.UpgradeKit;
import com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 (user: "for the ben 10 power make the detransform keybind N and add abilities to the V key for every alien"): N turns
 * an alien back through the router as the client sends it (and is nothing to a human), V goes to the kit -- one V move per alien
 * pressed through {@link OmnitrixAbilityManager} -- and XLR8's revamp: Speed Mode on V, Phase
 * held on X, the borrowed Speed Sweep. Mock players are not ticked by the server, so tests over time drive {@link Omnitrix#tick}.
 */
public class OmnitrixVKeyGameTests implements FabricGameTest {
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

	private static ServerPlayer as(GameTestHelper helper, String alien) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, alien), alien + " is playable");
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + alien);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	/** A V tap as the client sends it: down, then up. */
	private static void tapV(ServerPlayer p, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_5.number(), true);
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_5.number(), false);
		p.setShiftKeyDown(false);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	// ================================================================ N

	@GameTest(template = EMPTY_STRUCTURE)
	public void nTurnsAnAlienBackAndIsNothingToAHuman(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		AbilityRouter.handleInput(p, 8, true);
		AbilityRouter.handleInput(p, 8, false);
		helper.assertFalse(Omnitrix.selecting(p) || Omnitrix.transformed(p), "N does nothing to a human (R raises the watch)");
		helper.assertTrue(Omnitrix.select(p, HeatblastKit.ID) && Omnitrix.transform(p), "Heatblast");
		AbilityRouter.handleInput(p, 5, true);
		AbilityRouter.handleInput(p, 5, false);
		helper.assertTrue(Omnitrix.transformed(p) && !Omnitrix.poweringDown(p), "V no longer turns him back");
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 8, true);
		AbilityRouter.handleInput(p, 8, false);
		p.setShiftKeyDown(false);
		helper.assertTrue(Omnitrix.poweringDown(p), "Shift+N starts the power-down too");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	// ================================================================ one V move per alien

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void heatblastVHeatAuraBurnsAndSmelts(GameTestHelper helper) {
		ServerPlayer p = as(helper, HeatblastKit.ID);
		AlienKit kit = Aliens.kit(HeatblastKit.ID);
		Zombie z = zombie(helper, 4.5, 2, 2.5);
		Vec3 at = p.position().add(1, 0.2, 0);
		ItemEntity ore = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.RAW_IRON, 3));
		ore.setPickUpDelay(200);
		helper.getLevel().addFreshEntity(ore);
		tapV(p, false);
		helper.assertTrue(kit.flag(p, HeatblastKit.F_AURA), "V: Heat Aura on");
		pump(helper, p);
		helper.runAfterDelay(25, () -> {
			helper.assertTrue(z.isOnFire(), "the zombie beside him burns");
			helper.assertTrue(ore.getItem().is(Items.IRON_INGOT) && ore.getItem().getCount() == 3, "the dropped raw iron came out as ingots: " + ore.getItem());
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.assertFalse(kit.flag(p, HeatblastKit.F_AURA), "the aura goes out with him");
			z.discard();
			ore.discard();
			helper.succeed();
		});
	}

	// ================================================================ the failsafe and Master Control (v0.17.1)

	@GameTest(template = EMPTY_STRUCTURE)
	public void theFailsafeThrowsADyingAlienBackToHuman(GameTestHelper helper) {
		ServerPlayer p = as(helper, HeatblastKit.ID);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().generic(), 999f);
		helper.assertTrue(p.isAlive() && !Omnitrix.transformed(p), "a killing blow throws him back to human instead");
		helper.assertTrue(Math.abs(p.getHealth() - OmnitrixConfig.FAILSAFE_HEALTH) < 1.0e-3f, "on 2 hearts: " + p.getHealth());
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "wrapped in Resistance for a moment");
		helper.assertTrue(Omnitrix.rechargeLeft(p) > OmnitrixConfig.FAILSAFE_RECHARGE_TICKS - 20, "and the long recharge");
		// a second killing blow as an alien straight after: no second save
		var n = Omnitrix.state(p).copy();
		n.rechargeUntil = 0L;
		Omnitrix.save(p, n);
		helper.assertTrue(Omnitrix.select(p, HeatblastKit.ID) && Omnitrix.transform(p), "an alien again");
		helper.assertFalse(Omnitrix.failsafe(p), "the failsafe is spent for a while");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void masterControlTurnsTheDialThenLiftsTheLimits(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertTrue(Omnitrix.activate(p), "the watch up");
		Omnitrix.finishRaise(p);
		helper.assertFalse(Omnitrix.masterControl(p), "no Master Control yet");
		helper.assertTrue(Omnitrix.startMasterSequence(p), "the long Shift+N starts the sequence");
		helper.assertFalse(Omnitrix.cycle(p, 1), "the dial is the watch's while it turns itself");
		pump(helper, p);
		int steps = (Aliens.playable().size() + 2) * OmnitrixConfig.MASTER_STEP_TICKS;
		helper.runAfterDelay(steps, () -> {
			helper.assertTrue(Omnitrix.masterControl(p), "Master Control is on");
			helper.assertFalse(Omnitrix.selecting(p) || Omnitrix.masterSequence(p), "and the watch is down again");
			helper.assertTrue(Omnitrix.select(p, HeatblastKit.ID) && Omnitrix.transform(p), "an alien");
			helper.assertTrue(Omnitrix.revertEarly(p) && !Omnitrix.transformed(p), "N turns him back at once -- no beeps first");
			helper.assertTrue(Omnitrix.rechargeLeft(p) == 0, "no recharge under Master Control");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wildmuttVDigsSoftGroundButNotStone(GameTestHelper helper) {
		ServerPlayer p = as(helper, com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit.ID);
		// a wall of dirt ahead (he faces +z), one stone block in the middle of it
		for (int x = 2; x <= 4; x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = 2; z <= 4; z++) {
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.DIRT.defaultBlockState(), 3);
				}
			}
		}
		BlockPos stone = new BlockPos(3, 3, 3);
		helper.getLevel().setBlock(helper.absolutePos(stone), Blocks.STONE.defaultBlockState(), 3);
		tapV(p, false);
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 2, 2))).isAir(), "V: Dig tore through the dirt in front");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(4, 4, 4))).isAir(), "the tunnel runs three deep and three wide");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(stone)).is(Blocks.STONE), "stone is too hard for his claws");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fourArmsHeldXChargesALeapUpTo200Blocks(GameTestHelper helper) {
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		p.setOnGround(true);
		helper.assertTrue(kit.holdable(AbilitySlot.SLOT_3), "X is holdable");
		kit.holdStart(p, AbilitySlot.SLOT_3);
		helper.assertTrue(kit.flag(p, FourArmsKit.F_LEAP_CHARGE), "holding X crouches to charge");
		kit.holdEnd(p, AbilitySlot.SLOT_3, FourArmsKit.LEAP_CHARGE_TICKS);
		helper.assertFalse(kit.flag(p, FourArmsKit.F_LEAP_CHARGE), "letting go ends the charge");
		helper.assertTrue(kit.flag(p, FourArmsKit.F_LEAPING) && kit.farLeapVelocity(p) != null, "and he is off on the long leap");
		double range = com.projecthero.mod.hulk.HulkLeap.simulateRange(p.getDeltaMovement(), p.getGravity(), 400);
		helper.assertTrue(Math.abs(range - FourArmsKit.LEAP_FULL_BLOCKS) < 12.0, "a full charge carries about 200 blocks: " + range);
		helper.assertTrue(Math.abs(FourArmsKit.leapBlocks(0f) - FourArmsKit.LEAP_TAP_BLOCKS) < 1e-6, "no charge is the short hop");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	/** v0.17.2: a leap toward -x / -z holds that heading too (negative speeds used to be dropped as an unset timer). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void fourArmsLeapHoldsANegativeHeading(GameTestHelper helper) {
		ServerPlayer p = as(helper, FourArmsKit.ID);
		FourArmsKit kit = (FourArmsKit) Aliens.kit(FourArmsKit.ID);
		p.setOnGround(true);
		p.setYRot(135f); // looking toward -x, -z
		p.setXRot(-20f);
		kit.holdStart(p, AbilitySlot.SLOT_3);
		kit.holdEnd(p, AbilitySlot.SLOT_3, FourArmsKit.LEAP_CHARGE_TICKS / 2);
		net.minecraft.world.phys.Vec3 held = kit.farLeapVelocity(p), launched = p.getDeltaMovement();
		helper.assertTrue(held != null && held.x < -0.1 && held.z < -0.1, "the held speed keeps both negative axes: " + held);
		helper.assertTrue(Math.abs(held.x - launched.x) < 1e-4 && Math.abs(held.z - launched.z) < 1e-4, "and matches the launch: " + launched);
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fourArmsVWallClimb(GameTestHelper helper) {
		ServerPlayer p = as(helper, FourArmsKit.ID);
		AlienKit kit = Aliens.kit(FourArmsKit.ID);
		p.horizontalCollision = true;
		helper.assertFalse(kit.climbs(p), "no climbing before V");
		tapV(p, false);
		helper.assertTrue(kit.flag(p, FourArmsKit.F_CLIMB) && kit.climbs(p), "V: pressed into a wall he climbs it");
		p.setShiftKeyDown(true);
		helper.assertFalse(kit.climbs(p), "Shift lets go");
		p.setShiftKeyDown(false);
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(kit.flag(p, FourArmsKit.F_CLIMB), "off after turning back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diamondheadCRegrowthHealsAndHalvesDamage(GameTestHelper helper) {
		// v0.17.2: Regrowth is on C now (V raises the Crystal Wall / Shift+V the Dome)
		ServerPlayer p = as(helper, DiamondheadKit.ID);
		DiamondheadKit kit = (DiamondheadKit) Aliens.kit(DiamondheadKit.ID);
		kit.press(p, AbilitySlot.SLOT_6, false);
		helper.assertFalse(kit.flag(p, DiamondheadKit.F_REGROW), "refused at full health");
		p.setHealth(p.getMaxHealth() - 12f);
		float before = p.getHealth();
		kit.press(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(kit.flag(p, DiamondheadKit.F_REGROW), "C: Crystal Regrowth");
		helper.assertTrue(Math.abs(kit.incomingDamage(p, p.damageSources().generic(), 10f) - 10f * DiamondheadKit.REGROW_FACTOR) < 0.01f,
				"half damage while he regrows");
		pump(helper, p);
		helper.runAfterDelay(DiamondheadKit.REGROW_TICKS + 4, () -> {
			helper.assertTrue(p.getHealth() >= before + 8.9f, "about 9 health back: " + (p.getHealth() - before));
			helper.assertFalse(kit.flag(p, DiamondheadKit.F_REGROW), "and it ends");
			helper.assertTrue(kit.cooldownLeft(p, DiamondheadKit.REGROW) > 0, "it cools down");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ghostfreakVSpiritSight(GameTestHelper helper) {
		ServerPlayer p = as(helper, GhostfreakKit.ID);
		AlienKit kit = Aliens.kit(GhostfreakKit.ID);
		tapV(p, false);
		helper.assertTrue(kit.flag(p, GhostfreakKit.F_SIGHT) && p.hasEffect(MobEffects.NIGHT_VISION), "V: Spirit Sight, night vision");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(kit.flag(p, GhostfreakKit.F_SIGHT) || p.hasEffect(MobEffects.NIGHT_VISION), "both gone after turning back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void stinkflyVLiftsAndDrops(GameTestHelper helper) {
		ServerPlayer p = as(helper, StinkflyKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 3.5);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 20f);
		tapV(p, false);
		helper.assertTrue(StinkflyKit.lifted(p) == z, "V: the zombie is in his legs");
		p.moveTo(p.getX(), p.getY() + 4, p.getZ(), 0f, 0f);
		p.setOnGround(false);
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(z.getY() > p.getY() - z.getBbHeight() - 0.5 && z.getY() < p.getY(), "carried under him: " + (p.getY() - z.getY()));
			tapV(p, false);
			helper.assertTrue(StinkflyKit.lifted(p) == null, "V again drops it");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			z.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void ripjawsVWaterBubbleStopsTheDrying(GameTestHelper helper) {
		ServerPlayer p = as(helper, RipjawsKit.ID);
		RipjawsKit kit = (RipjawsKit) Aliens.kit(RipjawsKit.ID);
		kit.setDryness(p, 600, false, false);
		tapV(p, false);
		helper.assertTrue(kit.flag(p, RipjawsKit.F_BUBBLE), "V: the Water Bubble");
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			int d0 = RipjawsKit.dryness(p, helper.getLevel().getGameTime());
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(RipjawsKit.dryness(p, helper.getLevel().getGameTime()) <= d0, "no drying in the bubble");
				Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void greyMatterVPocketWorkshop(GameTestHelper helper) {
		ServerPlayer p = as(helper, GreyMatterKit.ID);
		tapV(p, false);
		helper.assertTrue(p.containerMenu instanceof net.minecraft.world.inventory.CraftingMenu, "V: a crafting grid opens");
		helper.assertTrue(p.containerMenu.stillValid(p), "with no table near");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "it closes when he turns back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void upgradeVOverclocksTheHeldTool(GameTestHelper helper) {
		ServerPlayer p = as(helper, UpgradeKit.ID);
		AlienKit kit = Aliens.kit(UpgradeKit.ID);
		tapV(p, false);
		helper.assertFalse(kit.flag(p, UpgradeKit.F_OVERCLOCK), "nothing in hand: refused");
		p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
		double dmg = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		double mine = p.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
		tapV(p, false);
		helper.assertTrue(kit.flag(p, UpgradeKit.F_OVERCLOCK), "V: Overclock");
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= dmg + UpgradeKit.OVERCLOCK_DAMAGE - 1.0e-4, "+4 damage");
		helper.assertTrue(p.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) > mine * 1.9, "twice the mining speed");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) - 1.0) < 1.0e-4, "it comes off with the body");
		helper.succeed();
	}

	// ================================================================ XLR8's revamp

	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8VSpeedModeAndShiftVDecoys(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		double walk = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertFalse(Xlr8Kit.visorDown(p), "the visor is up at rest");
		tapV(p, false);
		helper.assertTrue(kit.flag(p, Xlr8Kit.F_SPEED), "V: Speed Mode");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > walk * 1.9, "twice as fast again: " + p.getAttributeValue(Attributes.MOVEMENT_SPEED));
		helper.assertTrue(Xlr8Kit.visorDown(p), "the visor slams down");
		helper.assertTrue(kit.standsOnFluid(p, Fluids.WATER.getSource(false)), "he runs on water");
		p.horizontalCollision = true;
		helper.assertTrue(kit.climbs(p), "and up walls");
		p.horizontalCollision = false;
		Zombie z = zombie(helper, 3.5, 2, 5.5);
		z.setTarget(p);
		tapV(p, true);
		helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.DECOYS) > 0, "Shift+V: the afterimage decoys");
		helper.assertTrue(z.getTarget() != p, "the zombie goes for an afterimage");
		tapV(p, false);
		helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.MODE) > 0 && kit.flag(p, Xlr8Kit.F_SPEED), "the toggle has a short cooldown");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(kit.flag(p, Xlr8Kit.F_SPEED), "Speed Mode is off after turning back");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - 0.1) < 1.0e-4, "and its speed");
		z.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void xlr8HoldVOverdriveAndTapEndsIt(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		helper.assertTrue(kit.holdable(AbilitySlot.SLOT_5), "V is holdable");
		double walk = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double hit = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
		kit.holdStart(p, AbilitySlot.SLOT_5);
		helper.assertTrue(kit.flag(p, Xlr8Kit.F_OVERDRIVE) && kit.flag(p, Xlr8Kit.F_SPEED), "hold V: Overdrive (Speed Mode with it)");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > walk * 5.0, "far faster than Speed Mode alone: " + p.getAttributeValue(Attributes.MOVEMENT_SPEED));
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) > hit * 1.9, "blows twice as hard");
		kit.press(p, AbilitySlot.SLOT_5, false);
		helper.assertFalse(kit.flag(p, Xlr8Kit.F_OVERDRIVE), "a tap of V ends it early");
		helper.assertFalse(kit.flag(p, Xlr8Kit.F_SPEED), "and Speed Mode goes back off (it was off before)");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - walk) < 1.0e-4, "its speed comes off");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void xlr8HoldXPhasesAndLettingGoEndsIt(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		pump(helper, p);
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), true);
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(kit.phasing(p), "holding X: Phase");
			helper.assertFalse(kit.allowDamage(p, p.damageSources().generic(), 5f), "untouchable");
			helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.DASH) == 0, "the hold is not a dash");
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), false);
			helper.assertFalse(kit.phasing(p), "letting go ends it");
			helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.PHASE) > 0, "then it cools down");
			// a tap is still the dash
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), true);
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_3.number(), false);
			helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.DASH) > 0 && kit.flag(p, Xlr8Kit.F_DASH), "a tap of X: Blur Dash");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void xlr8ShiftXSpeedSweepsAndComesHome(GameTestHelper helper) {
		ServerPlayer p = as(helper, Xlr8Kit.ID);
		Xlr8Kit kit = (Xlr8Kit) Aliens.kit(Xlr8Kit.ID);
		Vec3 home = p.position();
		Zombie a = zombie(helper, 1.5, 2, 6.5);
		Zombie b = zombie(helper, 6.5, 2, 6.5);
		float ha = a.getHealth();
		float hb = b.getHealth();
		helper.assertTrue(kit.speedSweep(p), "Shift+X: Speed Sweep");
		pump(helper, p);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(a.getHealth() < ha && b.getHealth() < hb, "both zombies were hit");
			helper.assertTrue(p.position().distanceTo(home) < 0.5, "and he is back where he stood");
			helper.assertTrue(kit.cooldownLeft(p, Xlr8Kit.SPEED_SWEEP) > 0, "it cools down");
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
			a.discard();
			b.discard();
			helper.succeed();
		});
	}
}
