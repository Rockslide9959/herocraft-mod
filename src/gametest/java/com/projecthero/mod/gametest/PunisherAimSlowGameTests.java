package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.firearm.FirearmManager;
import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.punisher.Punisher;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * v0.15.18: aiming a gun down its sights slows the player 30% (a transient MOVEMENT_SPEED modifier, reconciled every
 * tick by {@link FirearmManager#serverTick}), and the slowdown goes as soon as the sights come down or the gun is put
 * away.
 */
public class PunisherAimSlowGameTests implements FabricGameTest {

	@GameTest(template = EMPTY_STRUCTURE, batch = "punisher_aim_slow")
	public void aimingSlowsThirtyPercentAndClears(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Punisher.grant(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FirearmItems.PUNISHER_ASSAULT_RIFLE));
		AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		double base = speed.getValue();

		FirearmManager.serverTick(p);
		helper.assertFalse(speed.hasModifier(FirearmManager.ADS_SLOW_ID), "hip fire must not slow the player");

		p.setAttached(ModAttachments.FIREARM_AIMING, true);
		FirearmManager.serverTick(p);
		helper.assertTrue(speed.hasModifier(FirearmManager.ADS_SLOW_ID), "aiming must add the slowdown");
		helper.assertTrue(Math.abs(speed.getValue() - base * 0.7) < 1.0e-6,
				"aiming should leave 70% speed, got " + speed.getValue() + " of " + base);
		FirearmManager.serverTick(p);
		helper.assertTrue(Math.abs(speed.getValue() - base * 0.7) < 1.0e-6, "the slowdown must not stack");

		p.setAttached(ModAttachments.FIREARM_AIMING, false);
		FirearmManager.serverTick(p);
		helper.assertFalse(speed.hasModifier(FirearmManager.ADS_SLOW_ID), "lowering the sights must clear the slowdown");
		helper.assertTrue(Math.abs(speed.getValue() - base) < 1.0e-6, "speed must be back to normal");

		// still flagged as aiming but the gun is swapped away: cleared (and the stale flag dropped)
		p.setAttached(ModAttachments.FIREARM_AIMING, true);
		FirearmManager.serverTick(p);
		helper.assertTrue(speed.hasModifier(FirearmManager.ADS_SLOW_ID), "re-aiming must slow again");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		FirearmManager.serverTick(p);
		helper.assertFalse(speed.hasModifier(FirearmManager.ADS_SLOW_ID), "swapping off the gun must clear the slowdown");
		helper.assertFalse(p.getAttachedOrElse(ModAttachments.FIREARM_AIMING, false), "the aim flag drops with the gun");
		helper.succeed();
	}
}
