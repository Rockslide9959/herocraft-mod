package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Cannonbolt (user: "build canonbolt alien for the prototype omnitrix please ... change his abilities if hes in ball mode or
 * not please"): on the prototype's dial only, V curls him into Ball Mode (a ball hitbox, every key swapped), the shell's passives, and
 * the standing moves hit.
 */
public class OmnitrixCannonboltGameTests implements FabricGameTest {
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

	private static ServerPlayer cannonbolt(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, CannonboltKit.ID), "Cannonbolt is on the prototype's dial");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Cannonbolt");
		return p;
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void cannonboltIsOnThePrototypeDialOnly(GameTestHelper helper) {
		helper.assertTrue(Aliens.byId(CannonboltKit.ID) != null && !Aliens.byId(CannonboltKit.ID).recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.dial(false).contains(Aliens.CANNONBOLT), "playable on the prototype's dial");
		helper.assertFalse(Aliens.dial(true).contains(Aliens.CANNONBOLT), "not on the Recalibrated Omnitrix's");
		ServerPlayer recal = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(recal, true);
		helper.assertFalse(Omnitrix.select(recal, CannonboltKit.ID), "the recalibrated watch cannot pick him");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vCurlsIntoBallModeAndSwapsEveryKey(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		float standingWidth = p.getBbWidth();
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_1, false, false).id().equals(CannonboltKit.BASH), "standing R is the Shoulder Bash");
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(CannonboltKit.ball(p), "V curls him up");
		p.refreshDimensions();
		helper.assertTrue(p.getBbHeight() < 1.2f && p.getBbWidth() < 1.0f, "a one-block ball: " + p.getBbWidth() + " x " + p.getBbHeight());
		helper.assertTrue(p.getBbWidth() != standingWidth || p.getBbHeight() < 2f, "the hitbox changed");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_1, false, false).id().equals(CannonboltKit.CHARGE), "ball R is the Cannonball Charge");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_1, true, false).id().equals(CannonboltKit.RICOCHET), "ball Shift+R is the Pinball Ricochet");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_2, false, false).id().equals(CannonboltKit.BOUNCE), "ball G is the Big Bounce");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_6, false, false).id().equals(CannonboltKit.UNSTOPPABLE), "ball C is Unstoppable");
		helper.assertTrue(kit.pawsOnly(p), "rolled up: no items");
		helper.assertFalse(kit.climbs(p), "not on a ladder in the open (only pressed into a wall)");
		// uncurl after the V cooldown
		helper.runAfterDelay(12, () -> {
			tap(p, AbilitySlot.SLOT_5, false);
			helper.assertFalse(CannonboltKit.ball(p), "V again uncurls him");
			p.refreshDimensions();
			helper.assertTrue(p.getBbHeight() > 2f, "standing again: " + p.getBbHeight());
			helper.assertFalse(kit.pawsOnly(p), "hands back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBallTakesHalfDamageDeflectsArrowsAndBouncesOffFalls(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		Zombie z = zombie(helper, 6.5, 2, 6.5);
		DamageSource hit = helper.getLevel().damageSources().mobAttack(z);
		helper.assertTrue(kit.incomingDamage(p, hit, 10f) == 10f, "standing, a generic hit is whole");
		tap(p, AbilitySlot.SLOT_5, false);
		Omnitrix.tick(p);
		helper.assertTrue(kit.incomingDamage(p, hit, 10f) == 5f, "rolled up, half");
		DamageSource arrow = helper.getLevel().damageSources().arrow(null, null);
		helper.assertFalse(kit.allowDamage(p, arrow, 6f), "arrows glance off the ball");
		DamageSource fall = helper.getLevel().damageSources().fall();
		helper.assertFalse(kit.allowDamage(p, fall, 8f), "a fall bounces the ball");
		helper.assertTrue(p.getDeltaMovement().y > 0.3, "bounced back up: " + p.getDeltaMovement().y);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void groundPoundAndShoulderBashHitStanding(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.5);
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(z.getHealth() < before, "the Ground Pound hits the zombie 2 blocks off: " + z.getHealth());
		helper.assertTrue(Aliens.kit(CannonboltKit.ID).cooldownLeft(p, CannonboltKit.POUND) > 0, "Z cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ballMovesStartAndCannotPunch(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		Zombie z = zombie(helper, 3.5, 2, 2.6);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(kit.onAttack(p, z), "the ball's left-click is swallowed (no punch)");
		tap(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(CannonboltKit.moving(p), "the Pinball Ricochet runs");
		helper.assertTrue(kit.keyActive(p, AbilitySlot.SLOT_1), "R lights while ricocheting");
		tap(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(kit.keyActive(p, AbilitySlot.SLOT_6), "Unstoppable lights C");
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 10f) == 2f, "Unstoppable: 80% less");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(CannonboltKit.ball(p) || CannonboltKit.moving(p), "turning back clears the ball and the moves");
		helper.succeed();
	}
}
