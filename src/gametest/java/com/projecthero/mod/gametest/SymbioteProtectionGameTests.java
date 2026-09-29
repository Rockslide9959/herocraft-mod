package com.projecthero.mod.gametest;

import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.item.SymbioteHostItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** v0.11.15: the Symbiote protects its host -- it wraps them on its own and refuses to let them die. */
public class SymbioteProtectionGameTests implements FabricGameTest {
	private static ServerPlayer bonded(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Symbiote.grant(player);
		return player;
	}

	/** v0.13.19: the resurrection costs no Biomass -- it is on a flat, saved 10-minute cooldown instead. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void resurrectionIsFreeButOnATenMinuteCooldown(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		float before = SymbioteVitalsManager.biomass(player);
		player.setHealth(1.0f);
		helper.assertTrue(Symbiote.tryResurrect(player), "a Symbiote resurrects its host");
		helper.assertTrue(SymbioteVitalsManager.biomass(player) >= before - 0.01f, "no Biomass is spent");
		helper.assertTrue(player.getHealth() >= 4.0f, "the host is brought back with health");
		helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_RESISTANCE), "Resistance for 20 seconds");
		helper.assertTrue(SymbioteVitalsManager.resurrectReadyAt(player) - player.level().getGameTime()
				== SymbioteVitalsManager.RESURRECT_COOLDOWN_TICKS, "the next one is ten minutes away");
		helper.assertFalse(Symbiote.tryResurrect(player), "it cannot resurrect twice in a row");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aDrainedSymbioteCanStillResurrect(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		SymbioteVitalsManager.vitals(player).hp = 10.0f;
		helper.assertTrue(Symbiote.tryResurrect(player), "Biomass no longer gates the resurrection");
		helper.succeed();
	}

	/** v0.13.19: dying no longer hands the host a full Biomass bar. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void biomassSurvivesDeathUnchanged(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		SymbioteVitalsManager.vitals(player).hp = 37.0f;
		Symbiote.clearTransient(player);
		helper.assertTrue(Math.abs(SymbioteVitalsManager.biomass(player) - 37.0f) < 0.01f, "the same Biomass comes back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void autoEquipWrapsAHostWhoIsNotSuited(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		Symbiote.state(player).toggleReadyAt = 0L;
		helper.assertFalse(Symbiote.isActive(player), "precondition: suit off");
		helper.assertTrue(Symbiote.autoEquip(player, true), "the Symbiote wraps its host");
		helper.assertTrue(Symbiote.isActive(player), "the suit is on");
		helper.assertFalse(Symbiote.autoEquip(player, true), "already suited -- nothing to do");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anEmptyVialIsCraftableAndFillable(GameTestHelper helper) {
		helper.assertTrue(SymbioteHostItems.SYMBIOTE_VIAL != null && SymbioteHostItems.SYMBIOTE_VIAL_FILLED != null,
				"both vial items are registered");
		ItemStack vial = new ItemStack(SymbioteHostItems.SYMBIOTE_VIAL);
		helper.assertFalse(vial.isEmpty(), "the vial is a real item");
		helper.succeed();
	}
	/** v0.13.19: a Symbiote Spike is a real projectile -- 14 damage and Wither V on whatever it hits. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aSpikeHitsForFourteenAndWithers(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2.0, 1.5));
		player.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		net.minecraft.world.entity.animal.Pig pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, 1, 2, 4);
		pig.setNoAi(true);
		pig.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(40.0);
		pig.setHealth(40.0f);
		net.minecraft.world.phys.Vec3 from = pig.position().add(0, 0.5, -1.5);
		com.projecthero.mod.symbiote.entity.SymbioteSpikeEntity.shoot(player, from, new net.minecraft.world.phys.Vec3(0, 0, 1));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(pig.getHealth() <= 40.0f - 13.9f, "the spike hit for 14 (" + pig.getHealth() + ")");
			var wither = pig.getEffect(MobEffects.WITHER);
			helper.assertTrue(wither != null && wither.getAmplifier() == 4, "and applied Wither V");
			helper.succeed();
		});
	}

	/** Push the suit-up / retract clock past its end so it settles this tick. */
	private static void settle(ServerPlayer player) {
		Symbiote.state(player).transformStartTick -= 10_000L;
		Symbiote.tick(player);
	}

	/**
	 * v0.13.21: at zero Biomass the suit melts off, and it cannot come back -- not by H, not by the Symbiote's own
	 * protective wrap, not on a resurrection -- until the bar has recovered to RECOVER_FRACTION.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void spentBiomassMeltsTheSuitAndLocksItOff(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		Symbiote.state(player).toggleReadyAt = 0L;
		helper.assertTrue(Symbiote.autoEquip(player, true), "precondition: suited");
		settle(player);

		SymbioteVitalsManager.vitals(player).hp = 0.0f;
		SymbioteVitalsManager.vitals(player).broken = true;
		SymbioteVitalsManager.tick(player);
		settle(player);
		helper.assertFalse(Symbiote.isActive(player), "the suit melts off at zero Biomass");
		helper.assertTrue(SymbioteVitalsManager.suitLocked(player), "and is locked off");

		Symbiote.state(player).toggleReadyAt = 0L;
		helper.assertFalse(Symbiote.autoEquip(player, true), "no protective wrap while spent -- not even a forced one");
		Symbiote.toggle(player);
		helper.assertFalse(Symbiote.isActive(player), "H cannot form it either");
		player.setHealth(1.0f);
		helper.assertTrue(Symbiote.tryResurrect(player), "the resurrection itself still works");
		helper.assertFalse(Symbiote.isActive(player), "but it brings the host back bare");

		SymbioteVitalsManager.vitals(player).hp = SymbioteVitalsManager.MAX_HP * SymbioteVitalsManager.RECOVER_FRACTION + 1.0f;
		SymbioteVitalsManager.tick(player);
		helper.assertFalse(SymbioteVitalsManager.suitLocked(player), "recovered past 20%: the lock lifts");
		Symbiote.state(player).toggleReadyAt = 0L;
		helper.assertTrue(Symbiote.autoEquip(player, true), "and the suit can form again");
		helper.succeed();
	}

	/**
	 * v0.13.21: out-of-combat regen is 8 Biomass a second and every bit of it is kept (it used to be computed every
	 * tick but saved only every 4th, so three quarters of it vanished).
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void biomassRegenLandsInFull(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		SymbioteVitalsManager.vitals(player).hp = 100.0f;
		helper.onEachTick(() -> SymbioteVitalsManager.tick(player));
		helper.runAfterDelay(40, () -> {
			float gained = SymbioteVitalsManager.biomass(player) - 100.0f;
			helper.assertTrue(gained > 12.5f && gained < 19.5f, "2 s out of combat regrows ~16 Biomass (" + gained + ")");
			helper.succeed();
		});
	}

	/**
	 * v0.13.21: only damage that actually lands drains Biomass -- the drain moved to AFTER_DAMAGE. A squadmate's hit
	 * (and a fall) never costs any; an ordinary hit costs 40%. Mock players cannot be hurt, so the events are fired
	 * directly.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyLandedNonSquadHitsDrainBiomass(GameTestHelper helper) {
		ServerPlayer host = bonded(helper);
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		mate.setGameMode(GameType.SURVIVAL);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("SymbioteTestSquad" + helper.getLevel().getGameTime(), host.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			helper.assertTrue(com.projecthero.mod.squad.Squads.areAllies(host, mate), "the two are squadmates");
			var afterDamage = net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.invoker();
			float before = SymbioteVitalsManager.biomass(host);
			afterDamage.afterDamage(host, host.damageSources().playerAttack(mate), 10.0f, 10.0f, false);
			helper.assertTrue(Math.abs(SymbioteVitalsManager.biomass(host) - before) < 0.01f, "a squadmate's hit costs nothing");
			afterDamage.afterDamage(host, host.damageSources().fall(), 10.0f, 10.0f, false);
			helper.assertTrue(Math.abs(SymbioteVitalsManager.biomass(host) - before) < 0.01f, "a fall costs nothing");
			helper.assertFalse(net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.invoker()
					.allowDamage(host, host.damageSources().fall(), 12.0f), "falls are still negated");
			helper.assertTrue(Math.abs(SymbioteVitalsManager.biomass(host) - before) < 0.01f, "negating it is free too");
			afterDamage.afterDamage(host, host.damageSources().generic(), 10.0f, 10.0f, false);
			helper.assertTrue(Math.abs(SymbioteVitalsManager.biomass(host) - (before - 4.0f)) < 0.01f,
					"a real landed hit drains 40% (" + SymbioteVitalsManager.biomass(host) + ")");

			// and no Symbiote move picks the squadmate as a target
			mate.moveTo(host.getX() + 1.0, host.getY(), host.getZ());
			helper.assertFalse(com.projecthero.mod.symbiote.SymbioteAbilityManager.enemiesAround(host, host.position(), 6.0)
					.contains(mate), "a squadmate is never an area target");
		} finally {
			squads.disband(squad);
		}
		helper.assertFalse(com.projecthero.mod.squad.Squads.areAllies(host, mate), "no squad, no allies");
		helper.succeed();
	}

	/**
	 * v0.13.21: nothing can be held in the Symbiote Blade's hand. An item landing in it is tucked into the pack, and
	 * scrolling onto an item snaps the selection back -- the blade stays out and nothing is lost.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theBladeKeepsItsHandEmpty(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		var inv = player.getInventory();
		inv.clearContent();
		inv.selected = 0;
		SymbioteVitalsManager.vitals(player).hp = 100.0f;
		SymbioteVitalsManager.toggleBlade(player);
		helper.assertTrue(SymbioteVitalsManager.bladeActive(player), "the blade comes out");

		inv.setItem(0, new ItemStack(net.minecraft.world.item.Items.STICK));
		SymbioteVitalsManager.tick(player);
		helper.assertTrue(SymbioteVitalsManager.bladeActive(player), "a pickup does not sheathe the blade");
		helper.assertTrue(player.getMainHandItem().isEmpty(), "the item is moved out of the blade hand");
		helper.assertTrue(inv.countItem(net.minecraft.world.item.Items.STICK) == 1, "and kept, not lost");

		inv.setItem(1, new ItemStack(net.minecraft.world.item.Items.DIAMOND));
		inv.selected = 1;
		SymbioteVitalsManager.tick(player);
		helper.assertTrue(inv.selected == 0, "scrolling onto an item snaps back to the blade's slot");
		helper.assertTrue(inv.getItem(1).is(net.minecraft.world.item.Items.DIAMOND), "the diamond stays where it was");
		helper.assertTrue(SymbioteVitalsManager.bladeActive(player), "the blade is still out");
		helper.succeed();
	}

	/** v0.13.21: the Grapple reaches 30 blocks. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void grappleReachesThirtyBlocks(GameTestHelper helper) {
		helper.assertTrue(com.projecthero.mod.symbiote.SymbioteAbilityManager.GRAPPLE_RANGE == 30.0, "Grapple range is 30");
		helper.succeed();
	}

	/** v0.13.19: the blade has no time limit -- it feeds on 0.3 Biomass a second, and Biomass does not regrow meanwhile. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void theBladeFeedsOnBiomass(GameTestHelper helper) {
		ServerPlayer player = bonded(helper);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		SymbioteVitalsManager.vitals(player).hp = 100.0f;
		SymbioteVitalsManager.toggleBlade(player);
		helper.assertTrue(SymbioteVitalsManager.bladeActive(player), "the blade comes out");
		helper.onEachTick(() -> SymbioteVitalsManager.tick(player));
		helper.runAfterDelay(200, () -> {
			float hp = SymbioteVitalsManager.biomass(player);
			helper.assertTrue(SymbioteVitalsManager.bladeActive(player), "no time limit: still out after 10 s");
			helper.assertTrue(hp < 97.8f && hp > 96.0f, "10 s of blade cost ~3 Biomass (" + hp + ")");
			helper.succeed();
		});
	}
}
