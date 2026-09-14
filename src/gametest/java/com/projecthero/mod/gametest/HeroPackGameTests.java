package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorAbility;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Regression coverage for the HeroPack experimental-power framework (batch 1): the six-slot router,
 * experimental player data isolation, cooldown persistence across power switches, and -- critically --
 * that Thor still receives the six slots unchanged.
 */
public class HeroPackGameTests implements FabricGameTest {

	private static ServerPlayer survivalMockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Power power(String key) {
		return Powers.byKey(key);
	}

	// ---------------- registry completeness ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void allPowersRegisteredWithSixAbilities(GameTestHelper helper) {
		helper.assertTrue(Powers.count() == 27, "Expected 27 experimental powers, got " + Powers.count());
		for (Power p : Powers.all()) {
			helper.assertTrue(p.abilities().size() == 6, p.key() + " must have exactly 6 abilities");
			for (AbilitySlot slot : AbilitySlot.values()) {
				Ability a = p.ability(slot);
				helper.assertTrue(a != null && a.slot() == slot, p.key() + " slot " + slot + " mismapped");
			}
		}
		helper.succeed();
	}

	// ---------------- Thor still owns the six slots ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void thorContextStillReceivesSlots(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));

		helper.assertTrue(AbilityRouter.hasThorContext(player),
				"A worthy player holding Mjolnir must be in Thor context");

		// Slot 2 == G == Lightning Strike. Firing it must put the EXISTING Thor cooldown on.
		boolean readyBefore = player.getAttachedOrCreate(ModAttachments.COOLDOWNS)
				.isReady(ThorAbility.LIGHTNING_STRIKE, player.level().getGameTime());
		helper.assertTrue(readyBefore, "Lightning Strike should start ready");

		AbilityRouter.handleInput(player, 2, true);

		boolean readyAfter = player.getAttachedOrCreate(ModAttachments.COOLDOWNS)
				.isReady(ThorAbility.LIGHTNING_STRIKE, player.level().getGameTime());
		helper.assertFalse(readyAfter, "Slot 2 in Thor context must trigger the real Thor Lightning Strike cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void experimentalNeverStealsSlotsFromThor(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));

		Power strength = power("power_01_super_strength");
		ExperimentalPowers.grant(player, strength);
		ExperimentalPowers.setActive(player, strength);

		// Even with an active experimental power, Thor context wins while Mjolnir is held.
		helper.assertTrue(AbilityRouter.hasThorContext(player), "Thor context must take priority over an active mutation");
		helper.succeed();
	}

	// ---------------- experimental data isolation + capacity ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantRespectsCapacity(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		int cap = ExperimentalPowers.capacity();
		int granted = 0;
		for (Power p : Powers.all()) {
			if (ExperimentalPowers.grant(player, p)) {
				granted++;
			}
		}
		helper.assertTrue(granted == cap, "grant() should stop at capacity (" + cap + "), granted " + granted);
		helper.assertTrue(ExperimentalPowers.ownedCount(player) == cap, "owned count must equal capacity");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void regrantingNeverDuplicates(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power strength = power("power_01_super_strength");
		helper.assertTrue(ExperimentalPowers.grant(player, strength), "first grant succeeds");
		helper.assertFalse(ExperimentalPowers.grant(player, strength), "second grant of the same power must be a no-op");
		helper.assertTrue(ExperimentalPowers.ownedCount(player) == 1, "owning the same power twice is impossible");
		helper.succeed();
	}

	// ---------------- power switching: cooldowns AND toggled modes both survive (v0.9.3 stacking) ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void switchingActivePowerKeepsCooldownsAndKeepsOtherPowersModes(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power strength = power("power_01_super_strength");
		Power durability = power("power_13_super_durability");
		ExperimentalPowers.grant(player, strength);
		ExperimentalPowers.grant(player, durability);

		ExperimentalPowers.setActive(player, strength);
		Ability groundSlam = strength.ability(AbilitySlot.SLOT_1);
		Ability tankMode = durability.ability(AbilitySlot.SLOT_6);

		ExperimentalPowers.triggerCooldown(player, strength, groundSlam, 200);
		ExperimentalPowers.setToggled(player, durability, tankMode, true);
		helper.assertFalse(ExperimentalPowers.cooldownReady(player, strength, groundSlam), "cooldown should be active");
		helper.assertTrue(ExperimentalPowers.isToggled(player, durability, tankMode), "tank mode toggle should be on");

		// Select the other owned power, then switch back.
		ExperimentalPowers.setActive(player, durability);
		helper.assertTrue(ExperimentalPowers.isToggled(player, durability, tankMode),
				"v0.9.3: selecting another power must NOT turn an owned power's toggled mode off");

		ExperimentalPowers.setActive(player, strength);
		helper.assertFalse(ExperimentalPowers.cooldownReady(player, strength, groundSlam),
				"switching active power must NOT reset cooldowns");
		helper.assertTrue(ExperimentalPowers.isToggled(player, durability, tankMode),
				"the toggled mode is still on after switching back");
		helper.succeed();
	}

	// ---------------- mutation framework (batch 2) ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void serumMarksPendingPowerButDoesNotGrant(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power durability = power("power_13_super_durability");
		int amp = com.projecthero.mod.hero.mutation.ModSerums.amplifierFor(durability);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION, 1200, amp, false, true, true));

		com.projecthero.mod.hero.mutation.MutationManager.serverTick(player);

		helper.assertTrue(durability.key().equals(ExperimentalPowers.state(player).pendingMutationPower),
				"drinking the serum should mark a pending power");
		helper.assertFalse(ExperimentalPowers.owns(player, durability),
				"drinking the serum alone must NOT grant the power");
		helper.assertTrue(ExperimentalPowers.researchStage(player, durability).atLeast(
				com.projecthero.mod.hero.data.ResearchStage.SERUM_STABILIZED), "research should reach SERUM_STABILIZED");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void exposureEventGrantsPermanentPower(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power durability = power("power_13_super_durability"); // trigger kind = EXPLOSION
		int amp = com.projecthero.mod.hero.mutation.ModSerums.amplifierFor(durability);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION, 1200, amp, false, true, true));
		com.projecthero.mod.hero.mutation.MutationManager.serverTick(player);

		com.projecthero.mod.hero.mutation.MutationManager.triggerExposure(player,
				com.projecthero.mod.hero.MutationTrigger.Kind.EXPLOSION);

		helper.assertTrue(ExperimentalPowers.owns(player, durability), "surviving the exposure event should unlock the power");
		helper.assertTrue(player.getEffect(com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION) == null,
				"the unstable effect should be consumed on success");
		helper.assertTrue(ExperimentalPowers.researchStage(player, durability)
				== com.projecthero.mod.hero.data.ResearchStage.MUTATION_CONFIRMED, "research should be MUTATION_CONFIRMED");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wrongExposureKindDoesNotGrant(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power durability = power("power_13_super_durability"); // wants EXPLOSION
		int amp = com.projecthero.mod.hero.mutation.ModSerums.amplifierFor(durability);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION, 1200, amp, false, true, true));
		com.projecthero.mod.hero.mutation.MutationManager.serverTick(player);

		com.projecthero.mod.hero.mutation.MutationManager.triggerExposure(player,
				com.projecthero.mod.hero.MutationTrigger.Kind.POWDER_SNOW);

		helper.assertFalse(ExperimentalPowers.owns(player, durability), "the wrong exposure kind must not unlock the power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void researchNoteAdvancesResearch(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power flight = power("power_03_flight");
		com.projecthero.mod.hero.mutation.MutationManager.studyResearchNote(player, flight);
		helper.assertTrue(ExperimentalPowers.researchStage(player, flight)
				.atLeast(com.projecthero.mod.hero.data.ResearchStage.RESEARCH_FOUND), "studying a note should record research");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void capacityBlocksFurtherMutation(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		int cap = ExperimentalPowers.capacity();
		int i = 0;
		for (Power p : Powers.all()) {
			if (i++ >= cap) {
				break;
			}
			ExperimentalPowers.grant(player, p);
		}
		helper.assertTrue(ExperimentalPowers.atCapacity(player), "should be at capacity");

		Power extra = power("power_25_water_manipulation");
		int amp = com.projecthero.mod.hero.mutation.ModSerums.amplifierFor(extra);
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				com.projecthero.mod.hero.mutation.ModMobEffects.UNSTABLE_MUTATION, 1200, amp, false, true, true));
		com.projecthero.mod.hero.mutation.MutationManager.serverTick(player);
		com.projecthero.mod.hero.mutation.MutationManager.triggerExposure(player,
				com.projecthero.mod.hero.MutationTrigger.Kind.SUBMERSION);

		helper.assertFalse(ExperimentalPowers.owns(player, extra), "a mutation beyond capacity must not be granted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void guideChaptersBuildFromRegistry(GameTestHelper helper) {
		var chapters = com.projecthero.mod.hero.guide.HeroPackGuide.chapters();
		// 15 framing chapters (overview, mutation, structures, devices, combos, Thor, Iron Man,
		// Spider-Man, Max Steel, Punisher, Symbiote, Zombie Raid, Supervillain Raid, Titan, Squads) +
		// one per power
		helper.assertTrue(chapters.size() == 15 + Powers.count(),
				"guide should have a chapter per power plus framing chapters, got " + chapters.size());
		for (var ch : chapters) {
			helper.assertFalse(ch.lines().isEmpty(), "chapter '" + ch.title().getString() + "' has no content");
		}

		// The navigation outline: every clickable row must point at a real chapter, and every power
		// chapter must be reachable from exactly one outline row.
		var index = com.projecthero.mod.hero.guide.HeroPackGuide.index();
		helper.assertFalse(index.isEmpty(), "guide outline must not be empty");
		int links = 0;
		for (var entry : index) {
			if (entry.isHeading()) {
				continue;
			}
			links++;
			helper.assertTrue(entry.chapterIndex() >= 0 && entry.chapterIndex() < chapters.size(),
					"outline row '" + entry.label().getString() + "' points at missing chapter " + entry.chapterIndex());
		}
		helper.assertTrue(links == chapters.size(),
				"outline should link every chapter exactly once, got " + links + " links for " + chapters.size() + " chapters");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void researchSiteAndDevicesAreRegistered(GameTestHelper helper) {
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.STRUCTURE_TYPE.containsKey(
				com.projecthero.mod.ProjectHeroMod.id("research_site")), "research_site structure type must be registered");
		for (String d : new String[]{"overloaded_redstone_coil", "geological_resonance_chamber", "electromagnetic_coil"}) {
			helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(
					com.projecthero.mod.ProjectHeroMod.id(d)), d + " device block must be registered");
		}
		helper.assertTrue(com.projecthero.mod.hero.item.HeroPackItems.GUIDE != null, "HeroPack Guide item must exist");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyAbilityHasAHandler(GameTestHelper helper) {
		int missing = 0;
		StringBuilder sb = new StringBuilder();
		for (Power p : Powers.all()) {
			for (Ability a : p.abilities()) {
				if (!com.projecthero.mod.hero.AbilityHandlers.has(p, a)) {
					missing++;
					sb.append(p.key()).append('/').append(a.id()).append(' ');
				}
			}
		}
		helper.assertTrue(missing == 0, "all 162 abilities should have handlers; missing: " + sb);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void inactivePowerSlotDoesNothing(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power a = power("power_01_super_strength");
		Power b = power("power_05_geokinesis");
		ExperimentalPowers.grant(player, a);
		ExperimentalPowers.grant(player, b);
		ExperimentalPowers.setActive(player, a);

		// b is owned but not active -> its slots must not fire
		AbilityRouter.handleInput(player, 1, true);
		Ability bR = b.ability(AbilitySlot.SLOT_1);
		helper.assertTrue(ExperimentalPowers.cooldownReady(player, b, bR),
				"an owned-but-inactive power's abilities must not fire");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ownedExperimentalPowersStackTheirPassives(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		var atkAttr = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
		var stepAttr = net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT;
		Power strength = power("power_01_super_strength");
		Power speed = power("power_04_super_speed");
		double atkBase = player.getAttributeValue(atkAttr);
		double stepBase = player.getAttributeValue(stepAttr);

		ExperimentalPowers.grant(player, strength);
		ExperimentalPowers.grant(player, speed);
		// Select Super Speed -- Super Strength is owned but NOT selected.
		ExperimentalPowers.setActive(player, speed);

		helper.assertTrue(player.getAttributeValue(atkAttr) > atkBase + 0.001,
				"Super Strength's attack passive should be live even though Super Speed is selected");
		helper.assertTrue(player.getAttributeValue(stepAttr) > stepBase + 0.001,
				"Super Speed's own step-height passive should be live too -- both stack");
		helper.succeed();
	}

	// ---------------- batch 3: powers 01-05 ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void superStrengthGroundSlamHitsNearby(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power strength = power("power_01_super_strength");
		ExperimentalPowers.grant(player, strength);
		ExperimentalPowers.setActive(player, strength);
		net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos(2, 2, 2);
		net.minecraft.world.phys.Vec3 pp = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(origin));
		player.setPos(pp.x, pp.y, pp.z);
		net.minecraft.world.entity.monster.Zombie z = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, origin.offset(1, 0, 0));
		float before = z.getHealth();

		player.setOnGround(true); // on the ground -> the immediate slam, not the air dive
		AbilityRouter.handleInput(player, 1, true); // R = Ground Slam

		helper.assertTrue(z.getHealth() < before, "Ground Slam should damage a nearby entity");
		Ability slam = strength.ability(AbilitySlot.SLOT_1);
		helper.assertFalse(ExperimentalPowers.cooldownReady(player, strength, slam), "Ground Slam should be on cooldown after use");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void superStrengthPassiveGrantsAttackBonus(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power strength = power("power_01_super_strength");
		Power flight = power("power_03_flight");
		double base = player.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		ExperimentalPowers.grant(player, strength);
		ExperimentalPowers.setActive(player, strength);
		helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) > base,
				"Super Strength should raise attack damage while owned");

		// v0.9.3: the passive is tied to OWNERSHIP, not selection -- selecting a different owned power
		// must leave Super Strength's attack bonus in place.
		ExperimentalPowers.grant(player, flight);
		ExperimentalPowers.setActive(player, flight);
		helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) > base,
				"selecting another power must NOT remove an owned power's passive");

		// Only forgetting the power removes it.
		ExperimentalPowers.forget(player, strength);
		helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) <= base + 0.001,
				"forgetting the power should remove the attack bonus");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightToggleEngagesIndependentHeroFlight(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power flight = power("power_03_flight");
		ExperimentalPowers.grant(player, flight);
		ExperimentalPowers.setActive(player, flight);

		AbilityRouter.handleInput(player, 3, true); // H = flight_toggle
		helper.assertTrue(com.projecthero.mod.hero.power.HeroFlight.isFlying(player), "Flight toggle should engage hero flight");
		helper.assertTrue(player.getAbilities().mayfly, "hero flight should grant mayfly");
		helper.assertFalse(com.projecthero.mod.power.ThorPowers.isFlying(player), "hero flight must be independent of Thor flight");

		AbilityRouter.handleInput(player, 3, true);
		helper.assertFalse(com.projecthero.mod.hero.power.HeroFlight.isFlying(player), "toggling again should disable hero flight");
		helper.succeed();
	}

	// ---------------- batch 4: powers 06-10 ----------------

	private static net.minecraft.world.entity.monster.Zombie zombieInFront(GameTestHelper helper, ServerPlayer player, int dist) {
		net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos(2, 2, 2);
		net.minecraft.world.phys.Vec3 pp = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(origin));
		player.setPos(pp.x, pp.y, pp.z);
		net.minecraft.world.entity.monster.Zombie z = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, origin.offset(0, 0, dist));
		player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		return z;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void electrokinesisBoltDamagesButStaysWeak(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power electro = power("power_07_electrokinesis");
		ExperimentalPowers.grant(player, electro);
		ExperimentalPowers.setActive(player, electro);
		net.minecraft.world.entity.monster.Zombie z = zombieInFront(helper, player, 4);
		float before = z.getHealth();

		AbilityRouter.handleInput(player, 1, true); // R = electric_bolt

		float dealt = before - z.getHealth();
		helper.assertTrue(dealt > 0, "Electric Bolt should damage the target");
		// Buffed to 8 (armour still applies -- PLAYER_ATTACK type), and it should static-shock the target.
		helper.assertTrue(dealt <= 12.0f, "Electric Bolt should not deal Thor-tier damage, got " + dealt);
		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
				"Electric Bolt should leave the target static-shocked (slowed)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void pyrokinesisFireballSpawns(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power pyro = power("power_08_pyrokinesis");
		ExperimentalPowers.grant(player, pyro);
		ExperimentalPowers.setActive(player, pyro);
		zombieInFront(helper, player, 6);

		AbilityRouter.handleInput(player, 1, true); // R = fireball

		long fireballs = helper.getLevel().getEntitiesOfClass(
				net.minecraft.world.entity.projectile.Fireball.class,
				player.getBoundingBox().inflate(6.0)).size();
		helper.assertTrue(fireballs >= 1, "fireball ability should spawn a projectile");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cryokinesisIceBoltFreezes(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power cryo = power("power_09_cryokinesis");
		ExperimentalPowers.grant(player, cryo);
		ExperimentalPowers.setActive(player, cryo);
		net.minecraft.world.entity.monster.Zombie z = zombieInFront(helper, player, 4);

		AbilityRouter.handleInput(player, 1, true); // R = ice_bolt

		helper.assertTrue(z.getTicksFrozen() > 0, "Ice Bolt should build freeze on the target");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalPrisonAppliesHardSlow(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power crystal = power("power_06_crystalkinesis");
		ExperimentalPowers.grant(player, crystal);
		ExperimentalPowers.setActive(player, crystal);
		net.minecraft.world.entity.monster.Zombie z = zombieInFront(helper, player, 5);

		AbilityRouter.handleInput(player, 5, true); // X = crystal_prison

		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
				"Crystal Prison should immobilise the target with a strong slow");
		helper.succeed();
	}

	// ---------------- batch 5: powers 11-15 ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void teleportBlinkRespectsWalls(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power tele = power("power_11_teleportation");
		ExperimentalPowers.grant(player, tele);
		ExperimentalPowers.setActive(player, tele);
		net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos(3, 2, 3);
		net.minecraft.world.phys.Vec3 pp = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(origin));
		player.setPos(pp.x, pp.y, pp.z);
		player.setYRot(0); // face +Z
		player.setXRot(0);
		// solid wall 2 blocks ahead
		helper.setBlock(origin.offset(0, 0, 2), net.minecraft.world.level.block.Blocks.OBSIDIAN);
		helper.setBlock(origin.offset(0, 1, 2), net.minecraft.world.level.block.Blocks.OBSIDIAN);
		double zBefore = player.getZ();

		AbilityRouter.handleInput(player, 1, true); // R = blink (hold to aim)
		AbilityRouter.handleInput(player, 1, false); // release to teleport

		helper.assertTrue(player.getZ() < zBefore + 2.0,
				"Blink must not teleport the player through a wall (moved to z=" + player.getZ() + " from " + zBefore + ")");
		helper.assertTrue(player.level().noCollision(player, player.getBoundingBox()),
				"Blink destination must be collision-free");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void superRegenerationRapidHealRestoresHealth(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power heal = power("power_12_super_regeneration");
		ExperimentalPowers.grant(player, heal);
		ExperimentalPowers.setActive(player, heal);
		player.setHealth(6.0f);

		AbilityRouter.handleInput(player, 1, true); // R = rapid_heal

		helper.assertTrue(player.getHealth() > 6.0f, "Rapid Heal should restore health");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void superDurabilityUnbreakableGrantsResistance(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power dur = power("power_13_super_durability");
		ExperimentalPowers.grant(player, dur);
		ExperimentalPowers.setActive(player, dur);

		AbilityRouter.handleInput(player, 4, true); // Z = unbreakable

		helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE),
				"Unbreakable should grant strong Resistance");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void invisibilityHolyLightChannels(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power inv = power("power_15_invisibility_light_manipulation");
		ExperimentalPowers.grant(player, inv);
		ExperimentalPowers.setActive(player, inv);

		AbilityRouter.handleInput(player, 4, true); // Z = perfect_cloak -> Holy Light (now a 5s hold-to-charge)

		// The charge-up needs 100 real ticks to elapse (the mod's normal per-player tick already drives
		// this ability's onServerTick every tick), so check after it has had time to complete.
		helper.runAfterDelay(105, () -> {
			helper.assertTrue(ExperimentalPowers.getResource(player, inv, "holy_ticks") > 0.5f,
					"Holy Light should start its beam channel once fully charged");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sonicScreamFocusedScreamHitsDistantTarget(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power sonic = power("power_14_sonic_scream");
		ExperimentalPowers.grant(player, sonic);
		ExperimentalPowers.setActive(player, sonic);
		net.minecraft.world.entity.monster.Zombie z = zombieInFront(helper, player, 5);
		float before = z.getHealth();

		AbilityRouter.handleInput(player, 2, true); // G = focused_scream

		helper.assertTrue(z.getHealth() < before, "Focused Scream should reach its target");
		helper.succeed();
	}

	// ---------------- batch 6: powers 16-20 ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void elasticityStretchPunchHasLongReach(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power elastic = power("power_17_elasticity");
		ExperimentalPowers.grant(player, elastic);
		ExperimentalPowers.setActive(player, elastic);
		net.minecraft.world.entity.monster.Zombie z = zombieInFront(helper, player, 5); // beyond normal melee reach
		float before = z.getHealth();

		AbilityRouter.handleInput(player, 1, true); // R = stretch_punch (charge start)
		AbilityRouter.handleInput(player, 1, false); // release -> fire the base punch

		helper.assertTrue(z.getHealth() < before, "Stretch Punch should reach a target well beyond melee range");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void densityAnchorRaisesDensityAndModifiers(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power density = power("power_18_density_manipulation");
		ExperimentalPowers.grant(player, density);
		ExperimentalPowers.setActive(player, density);
		var atk = net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE;
		var spd = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
		double atkBase = player.getAttributeValue(atk);
		double spdBase = player.getAttributeValue(spd);

		AbilityRouter.handleInput(player, 3, true); // X = Density Anchor -> 300%
		ExperimentalPowers.serverTick(player);

		helper.assertTrue(ExperimentalPowers.getResource(player, density, "density") >= 299.0f,
				"Density Anchor should push density to the maximum");
		helper.assertTrue(player.getAttributeValue(atk) > atkBase + 0.001,
				"maximum density should raise attack damage");
		helper.assertTrue(player.getAttributeValue(spd) < spdBase - 0.0001,
				"maximum density should slow the player");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void energyBlastSpendsTheMeter(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power energy = power("power_20_energy_absorption");
		ExperimentalPowers.grant(player, energy);
		ExperimentalPowers.setActive(player, energy);
		ExperimentalPowers.setResource(player, energy, "energy", 50.0f, 100.0f);

		AbilityRouter.handleInput(player, 1, true); // R = energy_blast (costs 15)

		helper.assertTrue(ExperimentalPowers.getResource(player, energy, "energy") < 50.0f,
				"Energy Blast should spend from the meter");

		ExperimentalPowers.setResource(player, energy, "energy", 5.0f, 100.0f);
		AbilityRouter.handleInput(player, 1, true);
		helper.assertTrue(ExperimentalPowers.getResource(player, energy, "energy") == 5.0f,
				"Energy Blast with an empty meter must not spend anything");
		helper.succeed();
	}

	// ---------------- batch 7: powers 21-27 ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void gravityZeroGReducesGravityAttribute(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power grav = power("power_23_gravity_manipulation");
		ExperimentalPowers.grant(player, grav);
		ExperimentalPowers.setActive(player, grav);
		double base = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY);

		AbilityRouter.handleInput(player, 3, true); // H = zero_g toggle

		helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY) < base,
				"Zero-G should reduce the player's gravity");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sizeTinyFormTogglesScale(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power size = power("power_27_size_manipulation");
		ExperimentalPowers.grant(player, size);
		ExperimentalPowers.setActive(player, size);
		var attr = net.minecraft.world.entity.ai.attributes.Attributes.SCALE;
		double base = player.getAttributeValue(attr);

		AbilityRouter.handleInput(player, 3, true); // X = Tiny Form on
		helper.assertTrue(player.getAttributeValue(attr) < base, "Tiny Form should shrink the player");

		AbilityRouter.handleInput(player, 3, true); // X = Tiny Form off
		helper.assertTrue(Math.abs(player.getAttributeValue(attr) - base) < 1.0e-4,
				"toggling Tiny Form off should restore normal size");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterAquaticFormGivesWaterBreathing(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power water = power("power_25_water_manipulation");
		ExperimentalPowers.grant(player, water);
		ExperimentalPowers.setActive(player, water);

		AbilityRouter.handleInput(player, 6, true); // C = aquatic_form toggle
		com.projecthero.mod.hero.ExperimentalPowers.serverTick(player); // run one toggle tick

		helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.WATER_BREATHING),
				"Aquatic Form should grant Water Breathing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void plantThornShotPoisons(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power plant = power("power_22_plant_manipulation_chlorokinesis");
		ExperimentalPowers.grant(player, plant);
		ExperimentalPowers.setActive(player, plant);
		net.minecraft.world.entity.monster.Zombie z = zombieInFront(helper, player, 4);

		AbilityRouter.handleInput(player, 1, true); // R = thorn_shot

		helper.assertTrue(z.hasEffect(net.minecraft.world.effect.MobEffects.POISON) || z.getHealth() < z.getMaxHealth(),
				"Thorn Shot should hit and poison the target");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void magneticCrushNeedsMetalOnTarget(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power magnetic = power("power_26_magnetic_manipulation");
		ExperimentalPowers.grant(player, magnetic);
		ExperimentalPowers.setActive(player, magnetic);
		Ability crush = magnetic.ability(AbilitySlot.SLOT_5);

		// bare zombie: Magnetic Crush must do nothing and must NOT spend its cooldown
		net.minecraft.world.entity.monster.Zombie bare = zombieInFront(helper, player, 3);
		AbilityRouter.handleInput(player, 5, true);
		helper.assertTrue(ExperimentalPowers.cooldownReady(player, magnetic, crush),
				"Magnetic Crush on a target with no magnetic metal must not trigger a cooldown");
		bare.discard();

		// armoured zombie: Magnetic Crush lands
		net.minecraft.world.entity.monster.Zombie armoured = zombieInFront(helper, player, 3);
		armoured.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
				new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		float before = armoured.getHealth();
		AbilityRouter.handleInput(player, 5, true);
		helper.assertTrue(armoured.getHealth() < before || armoured.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
				"Magnetic Crush should hit an iron-armoured target");
		helper.assertFalse(ExperimentalPowers.cooldownReady(player, magnetic, crush),
				"Magnetic Crush should be on cooldown after a successful hit");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ferrousShotConsumesNearbyMetalDrop(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power magnetic = power("power_26_magnetic_manipulation");
		ExperimentalPowers.grant(player, magnetic);
		ExperimentalPowers.setActive(player, magnetic);
		Ability shot = magnetic.ability(AbilitySlot.SLOT_1);

		net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos(2, 2, 2);
		net.minecraft.world.phys.Vec3 pp = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(origin));
		player.setPos(pp.x, pp.y, pp.z);
		net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
				helper.getLevel(), pp.x + 1.0, pp.y, pp.z, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3));
		helper.getLevel().addFreshEntity(drop);

		AbilityRouter.handleInput(player, 1, true); // R = ferrous_shot

		helper.assertFalse(ExperimentalPowers.cooldownReady(player, magnetic, shot),
				"Ferrous Shot should go on cooldown when it launches a nearby metal drop");
		helper.assertTrue(drop.getItem().getCount() == 2,
				"Ferrous Shot should consume exactly one item from the drop it launches");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void switchingPowerEndsHeroFlight(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power flight = power("power_03_flight");
		Power strength = power("power_01_super_strength");
		ExperimentalPowers.grant(player, flight);
		ExperimentalPowers.grant(player, strength);
		ExperimentalPowers.setActive(player, flight);
		AbilityRouter.handleInput(player, 3, true);
		helper.assertTrue(com.projecthero.mod.hero.power.HeroFlight.isFlying(player), "should be flying");

		ExperimentalPowers.setActive(player, strength);
		com.projecthero.mod.hero.power.HeroFlight.tick(player);
		helper.assertFalse(com.projecthero.mod.hero.power.HeroFlight.isFlying(player),
				"switching away from Flight must end hero flight");
		helper.assertFalse(player.getAbilities().mayfly, "mayfly should be cleared");
		helper.succeed();
	}

	// ================================================================================
	// Iron Man / Tony Stark
	// ================================================================================

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManSuitsAndRecipesRegistered(GameTestHelper helper) {
		// "changes 12" added Mark 1 / Mark 2 (primitive, non-Fabricator prototypes) alongside the
		// original five Fabricator-gated marks.
		// "changes 17": Mark XLII / Mark L removed -> 7 suits.
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.all().size() == 7,
				"expected 7 Iron Man suits");
		for (String id : new String[]{"mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii"}) {
			helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.byId(id) != null, id + " missing");
		}
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_42") == null
				&& com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_50") == null,
				"Mark XLII / Mark L must be removed");
		helper.assertTrue(com.projecthero.mod.ironman.fabricator.FabricatorRecipes.all().size() >= 24,
				"expected the full Fabricator recipe set");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.MENU.containsKey(
				com.projecthero.mod.ProjectHeroMod.id("stark_fabricator")), "fabricator menu type must be registered");
		helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(
				com.projecthero.mod.ProjectHeroMod.id("stark_fabricator")), "stark_fabricator block must be registered");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void arcReactorGrantsTonyStarkOnceAndIsConsumed(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.hasPower(player), "new player has no Tony Stark power");

		ItemStack reactor = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.ARC_REACTOR, 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, reactor);
		com.projecthero.mod.ironman.item.IronManItems.ARC_REACTOR.use(player.level(), player, InteractionHand.MAIN_HAND);

		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.hasPower(player), "Arc Reactor must grant Tony Stark");
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "one Arc Reactor must be consumed");

		// second use: no duplication, no consumption
		com.projecthero.mod.ironman.item.IronManItems.ARC_REACTOR.use(player.level(), player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.getMainHandItem().getCount() == 1,
				"a second Arc Reactor must NOT be consumed when the power is already owned");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManArmorRejectsNonTonyStark(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		var chest = com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
				net.minecraft.world.item.ArmorItem.Type.CHESTPLATE);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(chest));

		com.projecthero.mod.ironman.IronManArmor.enforce(player);
		helper.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
				"Iron Man armour must be ejected from a player without the Tony Stark power");
		helper.assertTrue(player.getInventory().contains(new ItemStack(chest)),
				"the ejected piece must be returned to the inventory, never destroyed");
		helper.assertFalse(com.projecthero.mod.ironman.ability.IronManAbilityManager.hasContext(player),
				"no Iron Man ability context without the Tony Stark power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void tonyStarkPlayerCanEquipAndOperateSuit(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.unlockTech(player, 5);
		var chest = com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
				net.minecraft.world.item.ArmorItem.Type.CHESTPLATE);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(chest));

		com.projecthero.mod.ironman.IronManArmor.enforce(player);
		helper.assertFalse(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
				"a Tony Stark player keeps the suit on");
		helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilityManager.hasContext(player),
				"Tony Stark + suit => Iron Man ability context");

		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 5000f);
		float before = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, AbilitySlot.SLOT_1, false); // tap R -> Repulsor Blast fires on release
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii") < before,
				"firing a repulsor must spend suit energy");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chargedRepulsorCostsMoreThanTapRepulsor(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.unlockTech(player, 5);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
						net.minecraft.world.item.ArmorItem.Type.CHESTPLATE)));

		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 10_000f);
		float start = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");

		// tap R -> ordinary Repulsor Blast
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, AbilitySlot.SLOT_1, false);
		float afterTap = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");

		// hold R for >2s then release -> Charged Repulsor (simulate the hold by backdating the start tick)
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.TonyStark.state(player).chargeStartTick = player.level().getGameTime() - 60L;
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, AbilitySlot.SLOT_1, false);
		float afterCharge = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");

		helper.assertTrue((afterTap - afterCharge) > (start - afterTap),
				"charged repulsor (held R) must spend more energy than a tap repulsor");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitPlatformDeployRechargesAndRepairs(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.unlockTech(player, 5);

		net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(2, 2, 2);
		helper.setBlock(pos, com.projecthero.mod.ironman.IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		var be = (com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity)
				helper.getBlockEntity(pos);
		for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[] {
				net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
				net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			be.store(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
		}
		be.addEnergy(5000);
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(player, "mark_iii", 10f);

		helper.assertTrue(be.deployTo(player), "platform must deploy the stored suit");
		helper.assertTrue(com.projecthero.mod.ironman.IronManArmor.wearingFullSuit(player, "mark_iii"),
				"player must be wearing the full Mark III after deploy");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii") >= 5000f,
				"deploy must transfer the platform's stored charge");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.integrity(player, "mark_iii") >= 99f,
				"deploy must repair suit integrity");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void starkFabricatorConsumesReactorCoreForEnergy(GameTestHelper helper) {
		net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(2, 2, 2);
		helper.setBlock(pos, com.projecthero.mod.ironman.IronManBlocks.STARK_FABRICATOR);
		var be = (com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity) helper.getBlockEntity(pos);
		int before = be.energy();
		be.setItem(0, new ItemStack(com.projecthero.mod.ironman.item.IronManItems.REACTOR_CORE, 2));
		com.projecthero.mod.ironman.fabricator.StarkFabricatorBlockEntity.serverTick(
				helper.getLevel(), helper.absolutePos(pos), be.getBlockState(), be);
		helper.assertTrue(be.energy() > before, "a Reactor Core in an input slot must charge the buffer");
		helper.assertTrue(be.getItem(0).getCount() == 1, "one Reactor Core must be consumed");
		helper.succeed();
	}

	// ================================================================================
	// changes 8
	// ================================================================================

	@GameTest(template = EMPTY_STRUCTURE)
	public void chlorokinesisDetectsNatureGround(GameTestHelper helper) {
		net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(2, 2, 2);
		helper.setBlock(p.below(), net.minecraft.world.level.block.Blocks.OAK_LEAVES);
		helper.assertTrue(com.projecthero.mod.hero.power.p22.PlantManipulationHandlers.onNatureGround(
				helper.getLevel(), helper.absolutePos(p)), "standing on leaves counts as nature ground");
		helper.setBlock(p.below(), net.minecraft.world.level.block.Blocks.STONE);
		helper.assertFalse(com.projecthero.mod.hero.power.p22.PlantManipulationHandlers.onNatureGround(
				helper.getLevel(), helper.absolutePos(p)), "stone does not");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sizeSmallFormWeakensGiantPunch(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power size = power("power_27_size_manipulation");
		ExperimentalPowers.grant(player, size);
		ExperimentalPowers.setActive(player, size);
		AbilityRouter.handleInput(player, 3, true); // X = shrink (Tiny)
		net.minecraft.world.entity.monster.Zombie z = zombieInFront(helper, player, 2);
		float before = z.getHealth();
		AbilityRouter.handleInput(player, 1, true); // R = giant_punch
		float dealt = before - z.getHealth();
		helper.assertTrue(dealt > 0 && dealt < 6.0f,
				"Giant Punch in Tiny form should land but for far less than normal, got " + dealt);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sizeLargeFormPrefillsHeartsWhenAtFullHp(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power size = power("power_27_size_manipulation");
		ExperimentalPowers.grant(player, size);
		ExperimentalPowers.setActive(player, size);
		net.minecraft.world.phys.Vec3 mid = net.minecraft.world.phys.Vec3.atBottomCenterOf(
				helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4)));
		player.setPos(mid.x, mid.y, mid.z);
		player.setHealth(player.getMaxHealth());
		float normalMax = player.getMaxHealth();
		AbilityRouter.handleInput(player, 6, true); // C = large_form
		helper.assertTrue(player.getMaxHealth() > normalMax, "Large form must raise max health");
		helper.assertTrue(player.getHealth() >= player.getMaxHealth() - 0.01f,
				"the new hearts must come pre-filled when the player was at full HP");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManCallArmorContextWithoutWearingASuit(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		helper.assertFalse(com.projecthero.mod.ironman.ability.IronManAbilityManager.hasContext(player),
				"no Iron Man context before any suit is developed");
		com.projecthero.mod.ironman.TonyStark.markBuilt(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III);
		helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilityManager.hasContext(player),
				"having developed a suit gives the C-slot call-armour context even unarmoured");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void arcReactorRemovalReturnsPowerAndItem(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.hasPower(player), "granted");

		// removal is blocked while a suit is on
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
						net.minecraft.world.item.ArmorItem.Type.CHESTPLATE)));
		helper.assertTrue(com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(player),
				"the /superhero arcreactor remove guard should see the worn suit");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);

		com.projecthero.mod.ironman.TonyStark.revoke(player);
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.hasPower(player),
				"revoke must clear the Tony Stark power");
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.techLevel(player) == 0
				&& com.projecthero.mod.ironman.TonyStark.state(player).builtSuits.isEmpty(),
				"revoke must reset tech + built suits");

		ItemStack back = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.ARC_REACTOR);
		helper.assertTrue(player.getInventory().add(back), "the player gets an Arc Reactor item back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitCallLaunchesCouriersFromInventory(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		net.minecraft.world.phys.Vec3 mid = net.minecraft.world.phys.Vec3.atBottomCenterOf(
				helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4)));
		player.setPos(mid.x, mid.y, mid.z);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.markBuilt(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III);
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			if (com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t) != null) {
				player.getInventory().add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
			}
		}
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuitSummonManager.summonBest(player),
				"summonBest should succeed with the pieces in the inventory");
		long couriers = helper.getLevel().getEntitiesOfClass(
				com.projecthero.mod.ironman.entity.IronManSuitPartEntity.class,
				player.getBoundingBox().inflate(9),
				e -> "mark_iii".equals(e.suitId())).size();
		helper.assertTrue(couriers == 4, "four armour couriers should be in flight, got " + couriers);
		helper.assertTrue(player.getInventory().countItem(
				com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", net.minecraft.world.item.ArmorItem.Type.HELMET)) == 0,
				"the pieces should have left the inventory");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitCallLaunchesCouriersFromNearbyPlatform(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		net.minecraft.core.BlockPos platPos = new net.minecraft.core.BlockPos(2, 2, 2);
		net.minecraft.world.phys.Vec3 pp = net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 6)));
		player.setPos(pp.x, pp.y, pp.z);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.markBuilt(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III);

		helper.setBlock(platPos, com.projecthero.mod.ironman.IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		var be = (com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity) helper.getBlockEntity(platPos);
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			if (com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t) != null) {
				be.store(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
			}
		}
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuitSummonManager.summonBest(player),
				"summonBest should pull the suit from the nearby platform");
		// couriers spawn at (platform centre + up 1), same tick as the call
		long couriers = helper.getLevel().getEntitiesOfClass(
				com.projecthero.mod.ironman.entity.IronManSuitPartEntity.class,
				net.minecraft.world.phys.AABB.ofSize(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(platPos)), 6, 6, 6),
				e -> "mark_iii".equals(e.suitId())).size();
		helper.assertTrue(couriers == 4, "four couriers should launch from the platform, got " + couriers);
		helper.assertTrue(be.isEmptyPlatform(), "the platform should now be empty");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void repulsorBarrierBlocksMostDamage(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.unlockTech(player, 5);
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			if (com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t) != null) {
				player.setItemSlot(com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotFor(t),
						new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
			}
		}
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 10_000f);
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(player, "mark_iii", 100f);

		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, AbilitySlot.SLOT_2, true); // hold barrier down
		helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilities.barrierActive(player),
				"holding slot 2 must raise the Repulsor Barrier");

		// it drains energy every tick it is held
		float e0 = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");
		com.projecthero.mod.ironman.ability.IronManAbilities.tickBarrier(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III);
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii") < e0,
				"the barrier must drain suit energy while held");

		// taking the suit off must cut the barrier (and everything else) even while it is still held
		for (net.minecraft.world.entity.EquipmentSlot es : new net.minecraft.world.entity.EquipmentSlot[] {
				net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
			player.setItemSlot(es, ItemStack.EMPTY);
		}
		com.projecthero.mod.ironman.IronManSuitTicker.tick(player);
		helper.assertFalse(com.projecthero.mod.ironman.ability.IronManAbilities.barrierActive(player),
				"taking the suit off must cut the barrier");

		// releasing the key when it is up starts the 6 s cooldown
		com.projecthero.mod.ironman.TonyStark.state(player).barrierHeld = true;
		com.projecthero.mod.ironman.ability.IronManAbilities.stopBarrier(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III, true);
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.abilityReady(player, "mark_iii",
				com.projecthero.mod.ironman.ability.IronManAbilities.REPULSOR_BARRIER),
				"dropping the barrier must start its 6 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitChargeTravelsWithTheArmour(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.markBuilt(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III);
		// a Mark III chestplate that has been drained to 30%
		ItemStack chest = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
				net.minecraft.world.item.ArmorItem.Type.CHESTPLATE));
		com.projecthero.mod.ironman.IronManEnergy.stampStack(chest, 3_000f, 55f);
		player.getInventory().add(chest);
		for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[] {
				net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.LEGGINGS,
				net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			player.getInventory().add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
		}

		com.projecthero.mod.ironman.suit.IronManSuitUpManager.beginSuitUp(player, "mark_iii");
		float pool = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");
		helper.assertTrue(Math.abs(pool - 3_000f) < 1f,
				"suit-up must adopt the chestplate's carried charge (3000), got " + pool);
		helper.assertTrue(Math.abs(com.projecthero.mod.ironman.IronManEnergy.integrity(player, "mark_iii") - 55f) < 1f,
				"suit-up must adopt the chestplate's carried integrity (55)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void callSuitFromInventoryEquipsImmediately(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.markBuilt(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III);
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			if (com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t) != null) {
				player.getInventory().add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
			}
		}
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuitCall.fullyInInventory(player, "mark_iii"),
				"the four pieces are in the pack");
		com.projecthero.mod.ironman.suit.IronManSuitCall.execute(player, "mark_iii",
				com.projecthero.mod.network.IronManSuitListPayload.SOURCE_INVENTORY);
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuitUpManager.inTransition(player),
				"picking an inventory suit must start an immediate staged suit-up");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void callBestPullsASuitOffABoundPlatform(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		net.minecraft.world.phys.Vec3 pp = net.minecraft.world.phys.Vec3.atBottomCenterOf(
				helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 6)));
		player.setPos(pp.x, pp.y, pp.z);
		com.projecthero.mod.ironman.TonyStark.grant(player);

		net.minecraft.core.BlockPos platPos = new net.minecraft.core.BlockPos(2, 2, 2);
		helper.setBlock(platPos, com.projecthero.mod.ironman.IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		var be = (com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity) helper.getBlockEntity(platPos);
		be.bindTo(player.getUUID());
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			if (com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t) != null) {
				be.store(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
			}
		}
		// no built suit, no pieces on the player -- only the bound platform. C must still call it.
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuitCall.callBest(player),
				"callBest must find the suit on the bound platform with nothing else developed/carried");
		helper.assertTrue(be.isEmptyPlatform(), "the platform must be emptied by the call");
		long couriers = helper.getLevel().getEntitiesOfClass(
				com.projecthero.mod.ironman.entity.IronManSuitPartEntity.class,
				player.getBoundingBox().inflate(140),
				e -> "mark_iii".equals(e.suitId())).size();
		helper.assertTrue(couriers == 4, "four couriers should be inbound, got " + couriers);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void suitAutoRecoversToPlatformOnDeath(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		net.minecraft.world.phys.Vec3 pp = net.minecraft.world.phys.Vec3.atBottomCenterOf(
				helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 6)));
		player.setPos(pp.x, pp.y, pp.z);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			if (com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t) != null) {
				player.setItemSlot(com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotFor(t),
						new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
			}
		}
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 4200f);
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(player, "mark_iii",
				com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_iii"));

		helper.getLevel().getServer().getGameRules()
				.getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
				.set(false, helper.getLevel().getServer());

		net.minecraft.core.BlockPos platPos = new net.minecraft.core.BlockPos(2, 2, 2);
		helper.setBlock(platPos, com.projecthero.mod.ironman.IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		var be = (com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity) helper.getBlockEntity(platPos);
		be.bindTo(player.getUUID());

		com.projecthero.mod.ironman.suit.IronManSuitCall.recoverSuitOnDeath(player);

		helper.assertTrue(!com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(player),
				"the suit must come off the player on death");
		helper.assertTrue("mark_iii".equals(be.storedSuitId()) && be.isFull(),
				"the whole suit must be docked at the bound platform");
		// "changes 14": a 50%-of-this-suit's-max integrity crash hit. Mark III's pool is 600 ("changes
		// 18"), so a full suit lands at ~300.
		float half = com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_iii") * 0.5f;
		helper.assertTrue(be.suitIntegrity() > half - 15f && be.suitIntegrity() < half + 15f,
				"the recovered suit lost 50% of max integrity (~" + half + "), got " + be.suitIntegrity());
		helper.assertTrue(be.suitEnergy() > 4000f,
				"the recovered suit keeps its charge, got " + be.suitEnergy());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManSuitGrantsStrengthProRata(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		var atk = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);

		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
						net.minecraft.world.item.ArmorItem.Type.CHESTPLATE)));
		com.projecthero.mod.ironman.IronManPassives.tick(player);
		double oneOfFour = atk.getModifier(com.projecthero.mod.ProjectHeroMod.id("iron_man_strength")).amount();
		helper.assertTrue(oneOfFour > 0.7 && oneOfFour < 2.0,
				"one Mark III piece should add ~1/4 of the +5 strength ('changes 15'), got " + oneOfFour);

		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
						net.minecraft.world.item.ArmorItem.Type.HELMET)));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS,
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
						net.minecraft.world.item.ArmorItem.Type.LEGGINGS)));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
						net.minecraft.world.item.ArmorItem.Type.BOOTS)));
		com.projecthero.mod.ironman.IronManPassives.tick(player);
		double full = atk.getModifier(com.projecthero.mod.ProjectHeroMod.id("iron_man_strength")).amount();
		helper.assertTrue(full > 5.0 && full < 7.0, "a full Mark III should add +6 strength ('changes 18'), got " + full);

		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, ItemStack.EMPTY);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, ItemStack.EMPTY);
		com.projecthero.mod.ironman.IronManPassives.tick(player);
		helper.assertTrue(atk.getModifier(com.projecthero.mod.ProjectHeroMod.id("iron_man_strength")) == null,
				"strength must be gone once the suit is off");
		helper.succeed();
	}

	// ---------------- Iron Man: the suit-up sequence must never eat items ----------------

	private static void giveWholeSuit(ServerPlayer player, String suitId) {
		for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[] {
				net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
				net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			player.getInventory().add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor(suitId, t)));
		}
	}

	/**
	 * Pieces of {@code suitId} carried in the <b>pack</b>. Deliberately walks {@code Inventory.items}
	 * rather than {@code getItem(0..getContainerSize())}: the latter's last five indices are the four
	 * armour slots plus the offhand, so it would count the suit the player is <em>wearing</em> as
	 * still being carried.
	 */
	private static int countIronManPieces(ServerPlayer player, String suitId) {
		int n = 0;
		for (ItemStack stack : player.getInventory().items) {
			if (stack.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem p
					&& p.suitId().equals(suitId)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static boolean inventoryHas(ServerPlayer player, net.minecraft.world.item.Item item) {
		for (ItemStack stack : player.getInventory().items) {
			if (stack.is(item)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A suit-up interrupted by a logout / crash must not destroy the armour. The transition fields are
	 * transient, so anything taken out of the inventory before the sequence finishes would have nothing
	 * to put it back -- the pieces must therefore stay in the pack until the tick that equips them.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void interruptedSuitUpKeepsThePiecesInTheInventory(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.markBuilt(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III);
		giveWholeSuit(player, "mark_iii");

		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuitUpManager.beginSuitUp(player, "mark_iii"),
				"suit-up should start");
		helper.assertTrue(countIronManPieces(player, "mark_iii") == 4,
				"beginSuitUp must only RESERVE the pieces, not remove them -- found "
						+ countIronManPieces(player, "mark_iii") + " of 4 still in the pack");

		// Simulate the relog: the transient transition state is gone, nothing else changes.
		var s = com.projecthero.mod.ironman.TonyStark.state(player);
		s.transitionSuit = "";
		s.transitionTicks = 0;
		s.transitionMask = 0;

		helper.assertTrue(countIronManPieces(player, "mark_iii") == 4,
				"an interrupted suit-up must leave all four pieces in the inventory");
		helper.succeed();
	}

	/**
	 * Suiting up over ordinary armour must hand that armour back, not overwrite it --
	 * {@code setItemSlot} silently discards whatever was in the slot.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void suitUpEvictsOrdinaryArmourInsteadOfDeletingIt(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.markBuilt(player, com.projecthero.mod.ironman.suit.IronManSuits.MARK_III);
		giveWholeSuit(player, "mark_iii");
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
				new ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));

		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuitUpManager.beginSuitUp(player, "mark_iii"),
				"suit-up should start");
		for (int i = 0; i < 200 && com.projecthero.mod.ironman.suit.IronManSuitUpManager.inTransition(player); i++) {
			com.projecthero.mod.ironman.suit.IronManSuitUpManager.tick(player);
		}

		helper.assertTrue(com.projecthero.mod.ironman.IronManArmor.wearingFullSuit(player, "mark_iii"),
				"the whole Mark III should be on at the end of the sequence");
		helper.assertTrue(inventoryHas(player, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE),
				"the diamond chestplate it replaced must come back to the inventory, not vanish");
		helper.assertTrue(countIronManPieces(player, "mark_iii") == 0,
				"every reserved piece should have moved from the pack onto the body exactly once");
		helper.succeed();
	}

	/**
	 * Dying in a partial suit with a Suit Platform waiting in an unloaded chunk sends the Iron Man
	 * pieces home -- and must leave every other armour slot completely alone.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void deathRecoveryOnlyTakesTheIronManPieces(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
						net.minecraft.world.item.ArmorItem.Type.CHESTPLATE)));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
				new ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));

		// A dock the player owns, far enough away that the loaded-chunk scan can't see it -- this is
		// the "fly itself home" path, the one that used to blank all four armour slots.
		var level = helper.getLevel();
		var registry = com.projecthero.mod.ironman.data.StarkPlatformRegistry.get(level);
		net.minecraft.core.BlockPos far = player.blockPosition().offset(4000, 0, 4000);
		registry.put(level, far, java.util.Optional.of(player.getUUID()), "", 0, 0f,
				com.projecthero.mod.ironman.IronManEnergy.MAX_INTEGRITY);
		try {
			com.projecthero.mod.ironman.suit.IronManSuitCall.recoverSuitOnDeath(player);

			helper.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(),
					"the Iron Man chestplate should have left for the platform");
			helper.assertTrue(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)
					.is(net.minecraft.world.item.Items.DIAMOND_HELMET),
					"the diamond helmet is not Stark property -- it must still be on the player's head to drop normally");
		} finally {
			registry.remove(level, far);
		}
		helper.succeed();
	}

	/**
	 * Breaking a Suit Platform must hand the racked armour back. Its loot table only drops the block,
	 * so before {@code onRemove} dropped the contents a mined platform deleted the whole suit.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void breakingASuitPlatformDropsTheSuitItHeld(GameTestHelper helper) {
		net.minecraft.core.BlockPos platPos = new net.minecraft.core.BlockPos(3, 2, 3);
		helper.setBlock(platPos, com.projecthero.mod.ironman.IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		var be = (com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity) helper.getBlockEntity(platPos);
		var chestItem = com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
				net.minecraft.world.item.ArmorItem.Type.CHESTPLATE);
		helper.assertTrue(be.store(new ItemStack(chestItem)), "the platform should accept the chestplate");

		helper.setBlock(platPos, net.minecraft.world.level.block.Blocks.AIR);

		long dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				net.minecraft.world.phys.AABB.ofSize(
						net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(platPos)), 8, 8, 8),
				e -> e.getItem().is(chestItem)).size();
		helper.assertTrue(dropped == 1, "the racked chestplate must drop when the platform is broken, got " + dropped);
		helper.succeed();
	}

	/**
	 * One rack, one suit. Mixing marks made "the stored suit's" energy / integrity / registry entry
	 * describe whichever piece sat in the lowest slot, and deploying it deleted the odd one out.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aSuitPlatformRefusesToMixMarks(GameTestHelper helper) {
		net.minecraft.core.BlockPos platPos = new net.minecraft.core.BlockPos(5, 2, 5);
		helper.setBlock(platPos, com.projecthero.mod.ironman.IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		var be = (com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity) helper.getBlockEntity(platPos);

		helper.assertTrue(be.store(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
				net.minecraft.world.item.ArmorItem.Type.CHESTPLATE))), "first piece should be accepted");
		helper.assertFalse(be.store(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_v",
				net.minecraft.world.item.ArmorItem.Type.HELMET))), "a different mark must be refused");
		helper.assertTrue(be.store(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
				net.minecraft.world.item.ArmorItem.Type.HELMET))), "the matching mark's helmet still fits");
		helper.assertTrue("mark_iii".equals(be.storedSuitId()), "the rack should read as a Mark III");
		helper.succeed();
	}

	// ================= "changes 12" =================

	private static void giveFullSuit(ServerPlayer player, String suitId) {
		for (net.minecraft.world.item.ArmorItem.Type t : net.minecraft.world.item.ArmorItem.Type.values()) {
			var item = com.projecthero.mod.ironman.item.IronManItems.armor(suitId, t);
			if (item != null) {
				player.setItemSlot(com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotFor(t), new ItemStack(item));
			}
		}
	}

	private static int freeMainInvSlots(ServerPlayer player) {
		int free = 0;
		var items = player.getInventory().items;
		for (int i = 9; i < 36; i++) {
			if (items.get(i).isEmpty()) {
				free++;
			}
		}
		return free;
	}

	private static void fillMainInventory(ServerPlayer player) {
		var items = player.getInventory().items;
		for (int i = 9; i < 36; i++) {
			items.set(i, new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
		}
	}

	// ---- storing the suit goes to the main inventory, never the hotbar, and refuses without room ----

	@GameTest(template = EMPTY_STRUCTURE)
	public void storingSuitRefusesWithoutFourFreeMainSlots(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_iii");
		fillMainInventory(player);

		helper.assertFalse(com.projecthero.mod.ironman.suit.IronManSuitUpManager.beginSuitDown(player, "mark_iii"),
				"suit-down must refuse when the main inventory has no room");
		helper.assertTrue(com.projecthero.mod.ironman.IronManArmor.wearingFullSuit(player, "mark_iii"),
				"the suit must still be fully worn -- nothing should have been touched");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void storingSuitLandsInMainInventoryNotHotbar(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_iii");
		helper.assertTrue(freeMainInvSlots(player) >= 4, "test setup needs room in the main inventory");

		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuitUpManager.beginSuitDown(player, "mark_iii"),
				"suit-down should start with room available");
		for (int i = 0; i < 200 && com.projecthero.mod.ironman.suit.IronManSuitUpManager.inTransition(player); i++) {
			com.projecthero.mod.ironman.suit.IronManSuitUpManager.tick(player);
		}
		helper.assertTrue(!com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(player), "the suit should be off");
		var hotbar = player.getInventory().items;
		boolean anyInHotbar = false;
		for (int i = 0; i < 9; i++) {
			if (hotbar.get(i).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				anyInHotbar = true;
			}
		}
		helper.assertFalse(anyInHotbar, "no stored piece should have landed in the hotbar");
		int inMain = 0;
		for (int i = 9; i < 36; i++) {
			if (hotbar.get(i).getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				inMain++;
			}
		}
		helper.assertTrue(inMain == 4, "all four pieces should have landed in the main inventory, got " + inMain);
		helper.succeed();
	}

	// ---- the new 90/10 -> 80/20 integrity-split damage model ----
	//
	// Not covered by a gametest: `player.hurt(...)` on a GameTest mock ServerPlayer never reaches
	// Fabric's ALLOW_DAMAGE event at all (confirmed by instrumenting IronManDamage.onAllowDamage
	// directly -- it fires for every other entity in the suite, e.g. the zombies other tests hit, but
	// never once for a mock player, through several combinations of clearing
	// Entity#invulnerable / Abilities#invulnerable / invulnerableTime). This is a pre-existing gap in
	// what this harness can exercise -- the OLD damageReduction-based mitigation was never
	// gametest-covered either, for the same reason. Verified by code review instead:
	// IronManDamage.onAllowDamage computes the split directly off `amount` before ever calling
	// player.hurt() again, so the arithmetic is exercised the same way regardless of which branch a
	// future refactor takes.

	@GameTest(template = EMPTY_STRUCTURE)
	public void integrityFailureAppliesSlownessAndWeakness(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_iii");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 5000f);
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(player, "mark_iii", 0f);

		com.projecthero.mod.ironman.IronManSuitTicker.tick(player);

		helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
				"a suit with failed integrity should slow its wearer");
		helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS),
				"a suit with failed integrity should weaken its wearer");
		helper.succeed();
	}

	// ---- death recovery also reaches a suit that was only ever carried, never worn ----

	@GameTest(template = EMPTY_STRUCTURE)
	public void carriedOnlySuitIsRecoveredOnDeathToo(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		net.minecraft.world.phys.Vec3 pp = net.minecraft.world.phys.Vec3.atBottomCenterOf(
				helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 6)));
		player.setPos(pp.x, pp.y, pp.z);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[] { net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			ItemStack stack = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t));
			com.projecthero.mod.ironman.IronManEnergy.stampStack(stack, 5000f,
					com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_iii"));
			player.getInventory().add(stack);
		}
		helper.assertTrue(!com.projecthero.mod.ironman.IronManArmor.wearingAnyIronMan(player),
				"test setup: the suit must NOT be worn, only carried");

		helper.getLevel().getServer().getGameRules()
				.getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY)
				.set(false, helper.getLevel().getServer());
		net.minecraft.core.BlockPos platPos = new net.minecraft.core.BlockPos(2, 2, 2);
		helper.setBlock(platPos, com.projecthero.mod.ironman.IronManBlocks.IRON_MAN_SUIT_PLATFORM);
		var be = (com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity) helper.getBlockEntity(platPos);
		be.bindTo(player.getUUID());

		com.projecthero.mod.ironman.suit.IronManSuitCall.recoverSuitOnDeath(player);

		helper.assertTrue("mark_iii".equals(be.storedSuitId()) && be.isFull(),
				"the carried-only suit must still have flown to the platform");
		float half3 = com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_iii") * 0.5f;
		helper.assertTrue(be.suitIntegrity() > half3 - 15f && be.suitIntegrity() < half3 + 15f,
				"it should carry the same flat 50%-of-max crash damage as a worn suit, got " + be.suitIntegrity());
		int leftInPack = 0;
		for (ItemStack s : player.getInventory().items) {
			if (s.getItem() instanceof com.projecthero.mod.ironman.item.IronManArmorItem) {
				leftInPack++;
			}
		}
		helper.assertTrue(leftInPack == 0, "every carried piece should have left the inventory, got " + leftInPack);
		helper.succeed();
	}

	// ---- Mark 1 / Mark 2 ----

	@GameTest(template = EMPTY_STRUCTURE)
	public void mark1And2AreRegisteredAndCraftableAtTechZero(GameTestHelper helper) {
		var mark1 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_1");
		var mark2 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_2");
		helper.assertTrue(mark1 != null && mark2 != null, "Mark 1 and Mark 2 must be registered");
		helper.assertTrue(mark1.techLevel() == 0 && mark2.techLevel() == 0,
				"both are tech level 0 (no tech-level gate). 'changes 19': only the Mark 1 is table-craftable;"
						+ " the Mark 2 is Fabricator-built.");
		helper.assertTrue(mark1.scale() == 1.25f, "Mark 1 is 25% bigger, got scale " + mark1.scale());
		helper.assertTrue(mark2.altitudeCeiling() == 150.0, "Mark 2's altitude ceiling should be Y150");
		for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[] { net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			helper.assertTrue(com.projecthero.mod.ironman.item.IronManItems.armor("mark_1", t) != null, "mark_1 " + t + " item missing");
			helper.assertTrue(com.projecthero.mod.ironman.item.IronManItems.armor("mark_2", t) != null, "mark_2 " + t + " item missing");
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mark1And2GrantFiveUnarmedDamageFullySuited(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		var atk = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		giveFullSuit(player, "mark_1");
		com.projecthero.mod.ironman.IronManPassives.tick(player);
		double total = 1.0 /* vanilla base fist */ + atk.getModifier(com.projecthero.mod.ProjectHeroMod.id("iron_man_strength")).amount();
		helper.assertTrue(total > 4.5 && total < 5.5, "Mark 1 fist damage should total ~5, got " + total);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mark1IsTwentyFivePercentBiggerFullySuited(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		var scaleAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
		giveFullSuit(player, "mark_1");
		com.projecthero.mod.ironman.IronManPassives.tick(player);
		helper.assertTrue(Math.abs(scaleAttr.getValue() - 1.25) < 0.01,
				"a fully-suited Mark 1 wearer should be scaled to 1.25x, got " + scaleAttr.getValue());

		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
		com.projecthero.mod.ironman.IronManPassives.tick(player);
		helper.assertTrue(Math.abs(scaleAttr.getValue() - 1.0) < 0.01,
				"losing a piece should drop the scale bonus (only full suits get it), got " + scaleAttr.getValue());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mark2WindupRepulsorDoesNotFireInstantly(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_2");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_2", 5000f);
		float before = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_2");

		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false);
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_2") == before,
				"a Mark 2 tap must not spend energy (or fire) before its 1s windup elapses");

		// Simulate the 1s windup elapsing by backdating it, same trick chargedRepulsorCostsMoreThanTapRepulsor
		// uses for the hold timer -- gametest ticks don't advance real world time between direct calls.
		var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_2");
		com.projecthero.mod.ironman.TonyStark.state(player).repulsorWindupAt = player.level().getGameTime();
		com.projecthero.mod.ironman.ability.IronManAbilities.tickRepulsorWindup(player, suit);
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_2") < before,
				"the ordinary blast should have fired once the windup elapsed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mark2SystemsFreezeAboveAltitudeCeiling(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_2");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_2", 5000f);
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(player, "mark_2", 500f);
		player.setAttached(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_FLYING, true);
		player.setPos(player.getX(), 160.0, player.getZ());

		com.projecthero.mod.ironman.IronManSuitTicker.tick(player);

		helper.assertFalse(com.projecthero.mod.ironman.IronManFlight.isFlying(player),
				"flight must cut out above Mark 2's Y150 altitude ceiling");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rocketAbilityDealsFifteenDamage(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_1");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_1", 5000f);

		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_4, true);

		long rockets = helper.getLevel().getEntitiesOfClass(com.projecthero.mod.ironman.entity.IronManMissileEntity.class,
				player.getBoundingBox().inflate(8)).size();
		helper.assertTrue(rockets == 1, "the Rocket ability should launch exactly one projectile, got " + rockets);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void mobHighlightTogglePersistsUntilPressedAgain(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_1");

		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.state(player).mobHighlightOn, "starts off");
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.state(player).mobHighlightOn, "V should toggle it on");
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.state(player).mobHighlightOn, "a second V toggles it back off");
		helper.succeed();
	}

	// ---- "changes 13" ----

	/** Mark 1 must not be able to start repulsor flight from the double-tap-jump gesture. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void mark1CannotStartRepulsorFlightFromDoubleJump(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_1");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_1", 3000f);

		com.projecthero.mod.ironman.IronManFlight.toggle(player); // the double-tap-jump entry point
		helper.assertFalse(com.projecthero.mod.ironman.IronManFlight.isFlying(player),
				"Mark 1 must not take off from double-tap jump -- only via its flight ability");

		// its own X flight ability still works
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_3, true);
		helper.assertTrue(com.projecthero.mod.ironman.IronManFlight.isFlying(player),
				"the Mark 1 flight ability must still start flight");
		helper.succeed();
	}

	/** A tap Repulsor costs a flat 50, a Charged Repulsor a flat 100, for every mark. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void repulsorEnergyCostsAreFlatFiftyAndHundred(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.TonyStark.unlockTech(player, 5);
		// "changes 15": the Mark V now has a repulsor spin-up (no charged variant), so this test uses the
		// Mark III, whose slot 1 is the plain tap / hold-for-Charged Repulsor.
		giveFullSuit(player, "mark_iii");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 5000f);

		float a = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false);
		float b = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");
		helper.assertTrue(Math.abs((a - b) - com.projecthero.mod.ironman.ability.IronManAbilities.REPULSOR_ENERGY) < 0.01f,
				"a tap Repulsor must cost exactly REPULSOR_ENERGY (80, 'changes 18'), spent " + (a - b));

		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.TonyStark.state(player).chargeStartTick = player.level().getGameTime() - 60L;
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false);
		float c = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");
		helper.assertTrue(Math.abs((b - c) - com.projecthero.mod.ironman.ability.IronManAbilities.CHARGED_REPULSOR_ENERGY) < 0.01f,
				"a Charged Repulsor must cost exactly CHARGED_REPULSOR_ENERGY (250, 'changes 18'), spent " + (b - c));
		helper.succeed();
	}

	/** Marks 1-3 have small condition pools (200/200/300 -- "changes 14"); the other marks stay on the 500 default. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void markThreeIntegrityPoolIsFifteenHundred(GameTestHelper helper) {
		// "changes 18": per-mark condition pools rebalanced.
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_1") == 300f,
				"Mark 1 max integrity must be 300");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_2") == 420f,
				"Mark 2 max integrity must be 420");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_iii") == 600f,
				"Mark III max integrity must be 600");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_4") == 700f,
				"Mark 4 max integrity must be 700");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_v") == 550f,
				"Mark V max integrity must be 550");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_6") == 800f,
				"Mark 6 max integrity must be 800");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_vii") == 950f,
				"Mark VII max integrity must be 950");
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("unknown_suit") == 500f,
				"an unknown suit id falls back to the 500 default");

		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(player, "mark_iii", 9000f); // over-set -> must clamp to 600
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.integrity(player, "mark_iii") == 600f,
				"integrity must clamp to the per-suit max, got "
						+ com.projecthero.mod.ironman.IronManEnergy.integrity(player, "mark_iii"));
		helper.succeed();
	}

	/** Every advanced mark carries the Mob Highlight toggle in slot 5 (Targeting Mode was dropped) --
	 *  except the "changes 16" Mark VII, whose slot 5 is the weapon wheel. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void advancedMarksCarryMobHighlightInsteadOfTargeting(GameTestHelper helper) {
		for (String id : new String[]{"mark_iii", "mark_v", "mark_4", "mark_6"}) {
			var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId(id);
			helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilities.MOB_HIGHLIGHT_TOGGLE.equals(suit.abilityInSlot(5)),
					id + " slot 5 must be the mob-highlight toggle");
			for (int slot = 1; slot <= 6; slot++) {
				helper.assertFalse(com.projecthero.mod.ironman.ability.IronManAbilities.TARGETING_MODE.equals(suit.abilityInSlot(slot)),
						id + " must not carry Targeting Mode any more");
			}
		}
		helper.succeed();
	}

	/** The mob-highlight toggle clears itself when the suit powers down. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void mobHighlightClearsWhenSuitLosesPower(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_iii");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 5000f);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_5, true);
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.state(player).mobHighlightOn, "toggled on");

		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 0f); // depleted
		com.projecthero.mod.ironman.IronManSuitTicker.tick(player);
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.state(player).mobHighlightOn,
				"a depleted suit must clear the mob-highlight toggle");
		helper.succeed();
	}

	// ---------------- "changes 15" ----------------

	/** Mark 4 exists with Mark III's ability layout, its own stats, and the wrist-laser flag. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void markFourIsRegisteredWithWristLaser(GameTestHelper helper) {
		var m4 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_4");
		helper.assertTrue(m4 != null, "mark_4 must be registered");
		helper.assertTrue(m4.hasWristLaser(), "mark_4 must carry the wrist laser");
		helper.assertTrue(m4.energyCapacity() == 8_500f, "mark_4 energy capacity must be 8500 ('changes 18')");
		helper.assertTrue(m4.maxIntegrity() == 700f, "mark_4 integrity must be 700 ('changes 18')");
		var m3 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_iii");
		for (int s = 1; s <= 6; s++) {
			helper.assertTrue(java.util.Objects.equals(m4.abilityInSlot(s), m3.abilityInSlot(s)),
					"mark_4 slot " + s + " must match Mark III");
		}
		helper.assertTrue(com.projecthero.mod.ironman.item.IronManItems.armor("mark_4",
				net.minecraft.world.item.ArmorItem.Type.CHESTPLATE) != null, "mark_4 chestplate item missing");
		helper.assertTrue(com.projecthero.mod.ironman.item.IronManItems.MARK_4_BLUEPRINT != null, "mark_4 blueprint missing");
		helper.succeed();
	}

	/** Mark V was redefined to the movie Mark 5 -- Mark 2 ability set, 250 integrity, 6000 energy, suitcase build. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void markVRedefinedAsMovieMarkFive(GameTestHelper helper) {
		var mv = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_v");
		var m2 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_2");
		helper.assertTrue(mv.energyCapacity() == 8_000f, "mark_v energy must be 8000 ('changes 18')");
		helper.assertTrue(mv.maxIntegrity() == 550f, "mark_v integrity must be 550 ('changes 18')");
		for (int s = 1; s <= 6; s++) {
			if (s == 3) {
				// "changes 19": slot 3 is the gauntlet Blade toggle on the Mark 5 (Flare on the Mark 2).
				helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilities.BLADE.equals(mv.abilityInSlot(3)),
						"mark_v slot 3 must be the Blade ability");
				continue;
			}
			helper.assertTrue(java.util.Objects.equals(mv.abilityInSlot(s), m2.abilityInSlot(s)),
					"mark_v slot " + s + " must match Mark 2");
		}
		helper.assertTrue(mv.suitUpType() == com.projecthero.mod.ironman.suit.SuitUpType.SUITCASE_MOVIE,
				"mark_v must use the suitcase-movie build");
		helper.succeed();
	}

	/** The Mark 1 flight burst goes on a 13 s cooldown once it ends. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void markOneFlightBurstGoesOnCooldown(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_1");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_1", 3000f);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_3, true);
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.state(player).timedFlightUntil > 0L, "burst must start");
		com.projecthero.mod.ironman.ability.IronManAbilities.endTimedFlight(player, "mark_1");
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.state(player).timedFlightUntil == 0L, "burst must clear");
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.abilityReady(player, "mark_1",
				com.projecthero.mod.ironman.ability.IronManAbilities.TIMED_FLIGHT), "flight burst must be on cooldown after it ends");
		helper.succeed();
	}

	/** An ability refused for lack of energy tells the player how much it needs (no exception, no spend). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void abilityWithoutEnergyIsRefusedCleanly(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_iii");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 10f); // less than a repulsor's 50
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false);
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii") == 10f,
				"a refused ability must not spend anything");
		helper.succeed();
	}

	/** "changes 18": the worn Arc Reactor trickle is a flat per-mark energy/second figure. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void wornReactorTrickleIsTenthOfAPercent(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_iii");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 100f);
		var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_iii");
		com.projecthero.mod.ironman.IronManEnergy.tickRecharge(player, suit);
		float gained = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii") - 100f;
		float expected = suit.energyRegenPerSecond() / 20f; // Mark III = 1.5/s -> 0.075/tick
		helper.assertTrue(Math.abs(gained - expected) < 0.001f,
				"worn trickle must be energyRegenPerSecond/20 (" + expected + "/tick), got " + gained);
		helper.assertTrue(suit.energyRegenPerSecond() == 1.5f, "Mark III worn regen must be 1.5/s");
		// "changes 18": Mark III+ also self-repair integrity slowly while worn.
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(player, "mark_iii", 100f);
		com.projecthero.mod.ironman.IronManEnergy.tickArmorRegen(player, suit);
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.integrity(player, "mark_iii") > 100f,
				"a worn Mark III should slowly self-repair integrity");
		helper.succeed();
	}

	/** "changes 16": Mark 6 / Mark 7 -- new/redefined suits with the full-body shield + weapon wheel. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void markSixAndSevenAreConfigured(GameTestHelper helper) {
		var m6 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_6");
		helper.assertTrue(m6 != null && m6.fullBodyShield(), "mark_6 must have a full-body shield");
		helper.assertTrue(m6.maxFlightSpeedMps() == 30.0, "mark_6 flight cap must be 30 m/s");
		helper.assertTrue(m6.energyCostMultiplier() < 1.0f, "mark_6 must cost less energy");
		helper.assertTrue(m6.maxIntegrity() == 800f && m6.energyCapacity() == 10_500f, "mark_6 pools ('changes 18')");
		helper.assertTrue(com.projecthero.mod.ironman.item.IronManItems.armor("mark_6",
				net.minecraft.world.item.ArmorItem.Type.CHESTPLATE) != null, "mark_6 chest item");

		var m7 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_vii");
		helper.assertTrue(m7.hasWeaponWheel(), "mark_vii must have the weapon wheel");
		// "changes 17": entity highlight is now a coloured weapon-wheel toggle, not an always-on passive.
		helper.assertTrue(m7.coloredEntityGlow() && m7.toggleableHighlight(),
				"mark_vii entity glow must be a coloured toggle");
		helper.assertTrue(m7.airTankSeconds() == 420, "mark_vii air tank must be 7 minutes");
		helper.assertTrue(m7.maxIntegrity() == 950f && m7.energyCapacity() == 12_000f, "mark_vii pools ('changes 18')");
		helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilities.WEAPON_WHEEL_SLOT.equals(m7.abilityInSlot(3)),
				"mark_vii slot 3 must be the weapon-wheel slot");
		helper.succeed();
	}

	/** "changes 16": a Mark 6-style cheap suit spends half the energy on a repulsor. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void cheapSuitSpendsLessEnergy(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_6");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_6", 5000f);
		float before = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_6");
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false);
		float spent = before - com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_6");
		float expected = com.projecthero.mod.ironman.ability.IronManAbilities.REPULSOR_ENERGY
				* com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_6").energyCostMultiplier();
		helper.assertTrue(Math.abs(spent - expected) < 0.01f, "mark_6 repulsor should cost " + expected + ", spent " + spent);
		helper.succeed();
	}

	/** "changes 16": an overloaded Mark 4 must not fire a repulsor on the key-release edge. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void overloadedSuitCannotFireRepulsor(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_4");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_4", 5000f);
		com.projecthero.mod.ironman.TonyStark.setOverloadUntil(player, player.level().getGameTime() + 200L);
		float before = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_4");
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, true);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_1, false);
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_4") == before,
				"an overloaded suit must not spend energy / fire on either key edge");
		helper.succeed();
	}

	/** "changes 17": the Mark 1 prototype -- no Night Vision helmet, only 80% fall reduction, heavy in water. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void markOneIsAHeavyPrototype(GameTestHelper helper) {
		var m1 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_1");
		helper.assertFalse(m1.helmetNightVision(), "Mark 1 helmet must NOT give Night Vision");
		helper.assertTrue(Math.abs(m1.fallDamageFraction() - 0.20f) < 1e-4f,
				"Mark 1 must still take 20% of fall damage (not full immunity)");
		helper.assertTrue(m1.waterMoveMultiplier() == 0.5, "Mark 1 must be 50% slower in water");
		helper.assertTrue(m1.airTankSeconds() == 0, "Mark 1 has no air tank");
		for (String id : new String[]{"mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii"}) {
			helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.byId(id).helmetNightVision(),
					id + " helmet must give Night Vision");
		}
		helper.succeed();
	}

	/** "changes 17": built-in air tanks -- Mark 2 = 2 min, Marks 3/4 = 3 min, Mark 6 = 5 min, Mark 7 = 7 min. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void airTanksAreConfigured(GameTestHelper helper) {
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_2").airTankSeconds() == 120, "mark_2 air");
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_iii").airTankSeconds() == 180, "mark_iii air");
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_4").airTankSeconds() == 180, "mark_4 air");
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_6").airTankSeconds() == 300, "mark_6 air");
		helper.assertTrue(com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_vii").airTankSeconds() == 420, "mark_vii air");
		helper.succeed();
	}

	/** "changes 17": supersonic flight -- 20 s burst, 10 s cooldown. ("changes 18": flight energy is now
	 *  a tiered per-second cost -- supersonic 45/s -- rather than a multiple of a per-mark constant.) */
	@GameTest(template = EMPTY_STRUCTURE)
	public void supersonicFlightRework(GameTestHelper helper) {
		helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilities.SUPERSONIC_TICKS == 400,
				"supersonic burst must be 20 s");
		helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilities.SUPERSONIC_COOLDOWN_TICKS == 200,
				"supersonic cooldown must be 10 s");
		helper.succeed();
	}

	/** "changes 18": staggered micro-missiles -- one shot arms a per-tick volley instead of spawning
	 *  the whole salvo at once. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void microMissilesFireOneAtATime(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_iii");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_iii", 5000f);
		var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_iii");
		float before = com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii");
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_3, true);
		int queued = com.projecthero.mod.ironman.TonyStark.state(player).pendingMissiles;
		helper.assertTrue(queued == suit.missileCount(),
				"pressing Micro-Missiles must queue missileCount() missiles at once, got " + queued);
		helper.assertTrue(before - com.projecthero.mod.ironman.IronManEnergy.energy(player, "mark_iii")
				== suit.missileEnergyCost(), "the whole volley is paid for up front");
		// pressing again while the volley is still launching does nothing
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_3, true);
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.state(player).pendingMissiles == queued,
				"a second press must not re-arm the volley");
		// the ticker's shutdown must clear a pending volley
		com.projecthero.mod.ironman.IronManSuitTicker.shutDownAllSystems(player,
				com.projecthero.mod.ironman.TonyStark.state(player), suit);
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.state(player).pendingMissiles == 0,
				"shutting the suit down clears a pending micro-missile volley");
		helper.succeed();
	}

	/** "changes 18": per-mark energy regen + flight-drain multipliers. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void changes18SuitTuning(GameTestHelper helper) {
		var m1 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_1");
		var m7 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_vii");
		helper.assertTrue(m1.energyRegenPerSecond() == 0.7f && m7.energyRegenPerSecond() == 3.0f,
				"Mark 1 / Mark 7 worn energy regen");
		helper.assertTrue(m1.armorRegenPerSecond() == 0f && m7.armorRegenPerSecond() == 0.06f,
				"Mark 1 has no worn armour regen; Mark 7 is 0.06/s");
		helper.assertTrue(Math.abs(m1.flightDrainMultiplier() - 0.55f) < 1e-4f
				&& Math.abs(m7.flightDrainMultiplier() - 1.15f) < 1e-4f, "flight-drain multipliers");
		helper.assertTrue(com.projecthero.mod.ironman.ability.IronManAbilities.CHARGED_REPULSOR_DAMAGE_MULTIPLIER == 3.0f,
				"a Charged Repulsor hits 3x as hard as a plain blast");
		helper.succeed();
	}

	/**
	 * v0.6.2: a Suit Platform charges and repairs a docked suit at a flat <b>0.1% of the mark's pool
	 * per second</b> -- 0.1% of energy capacity into energy, 0.1% of max integrity into integrity --
	 * so a full refill / repair of any mark takes ~1000 s regardless of pool size. (This replaces the
	 * "changes 22" model of a 3x/5x multiple of the worn Arc Reactor.)
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void changes22PlatformRegen(GameTestHelper helper) {
		var m1 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_1");
		var m2 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_2");
		var m7 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_vii");

		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.PLATFORM_FRACTION_PER_SECOND == 0.001f,
				"a platform charges a flat 0.1% per second");
		for (var suit : new com.projecthero.mod.ironman.suit.IronManSuit[] { m1, m2, m7 }) {
			helper.assertTrue(Math.abs(com.projecthero.mod.ironman.IronManEnergy.platformEnergyPerSecond(suit)
					- suit.energyCapacity() * 0.001f) < 1e-4f,
					"platform energy regen is 0.1% of capacity for " + suit.id());
			helper.assertTrue(Math.abs(com.projecthero.mod.ironman.IronManEnergy.platformIntegrityPerSecond(suit)
					- suit.maxIntegrity() * 0.001f) < 1e-4f,
					"platform integrity regen is 0.1% of max integrity for " + suit.id());
		}
		// every mark -- including the prototypes -- now gets a positive repair rate on a rack.
		helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.platformIntegrityPerSecond(m1) > 0f
				&& com.projecthero.mod.ironman.IronManEnergy.platformIntegrityPerSecond(m2) > 0f,
				"Mark 1 / Mark 2 get a platform repair rate");
		helper.succeed();
	}

	/**
	 * "changes 22": the Mark 1 flamethrower's heat ceiling is 300, and every armour recipe costs the
	 * Stark Fabricator's entire buffer (so one piece per full recharge).
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void changes22FlamethrowerAndFabricator(GameTestHelper helper) {
		var m1 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_1");
		helper.assertTrue(Math.abs(
				com.projecthero.mod.ironman.ability.IronManAbilities.flamethrowerMaxHeat(m1) - 300f) < 1e-3f,
				"Mark 1 flamethrower heat capacity is 300");

		var chest = com.projecthero.mod.ironman.fabricator.FabricatorRecipes.byId("iron_man_mark_iii_chestplate");
		helper.assertTrue(chest != null && chest.energyCost()
				== com.projecthero.mod.ironman.fabricator.FabricatorRecipes.MAX_ENERGY,
				"an armour recipe costs 100% of the Fabricator's charge");
		// a component recipe must NOT -- otherwise the whole tree would be paced by the recharge
		var circuit = com.projecthero.mod.ironman.fabricator.FabricatorRecipes.byId("stark_circuit");
		helper.assertTrue(circuit != null
				&& circuit.energyCost() < com.projecthero.mod.ironman.fabricator.FabricatorRecipes.MAX_ENERGY,
				"components are still cheap to fabricate");
		helper.assertTrue(circuit.result().getCount() == 2,
				"components yield more per run after the 'changes 22' cost pass");
		helper.succeed();
	}

	/**
	 * "changes 22": the Repulsor component is wearable in the boots slot and grants flight at half the
	 * Mark 2's speed, with no suit and no Tony Stark power -- and it must stay a stackable component,
	 * because the armour recipes consume it two and four at a time.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void changes22RepulsorBoots(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ItemStack repulsor = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.REPULSOR);

		helper.assertTrue(repulsor.getMaxStackSize() > 1,
				"the Repulsor must still stack -- the armour recipes need several at once");
		helper.assertTrue(com.projecthero.mod.ironman.item.IronManItems.REPULSOR
				instanceof com.projecthero.mod.ironman.item.RepulsorItem r
				&& r.getEquipmentSlot() == net.minecraft.world.entity.EquipmentSlot.FEET,
				"the Repulsor equips into the boots slot");

		helper.assertTrue(!com.projecthero.mod.ironman.RepulsorBoots.worn(player),
				"no boots on -- not wearing a Repulsor");
		helper.assertTrue(!com.projecthero.mod.ironman.RepulsorBoots.toggle(player),
				"the flight gesture does nothing without the boots on");

		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, repulsor);
		helper.assertTrue(com.projecthero.mod.ironman.RepulsorBoots.worn(player), "Repulsor is worn");
		// no Tony Stark power granted anywhere in this test -- that is the point
		helper.assertTrue(!com.projecthero.mod.ironman.TonyStark.hasPower(player),
				"repulsor flight must not need the Tony Stark power");
		helper.assertTrue(com.projecthero.mod.ironman.RepulsorBoots.toggle(player)
				&& com.projecthero.mod.ironman.RepulsorBoots.isFlying(player), "boots flight engages");
		helper.assertTrue(com.projecthero.mod.ironman.RepulsorBoots.toggle(player)
				&& !com.projecthero.mod.ironman.RepulsorBoots.isFlying(player), "and toggles back off");

		// exactly half the Mark 2's flight numbers
		var m2 = com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_2");
		helper.assertTrue(Math.abs(com.projecthero.mod.ironman.RepulsorBoots.FLIGHT_SPEED
				- m2.flightSpeed() / 2f) < 1e-5f, "boots fly at 50% of the Mark 2's speed");
		helper.assertTrue(Math.abs(com.projecthero.mod.ironman.RepulsorBoots.FLIGHT_ACCELERATION
				- m2.flightAcceleration() / 2f) < 1e-5f, "and half its acceleration");
		helper.succeed();
	}

	/** "changes 19": faceplate opens/closes on toggle and closes when the armour comes off;
	 *  the Mark 5 Blade toggle adds +4 melee and retracts when the suit powers down. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void changes19FaceplateAndBlades(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		giveFullSuit(player, "mark_v");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_v", 5000f);

		// faceplate
		com.projecthero.mod.ironman.IronManFaceplate.toggle(player);
		helper.assertTrue(com.projecthero.mod.ironman.IronManFaceplate.isOpen(player), "H opens the faceplate");
		for (net.minecraft.world.entity.EquipmentSlot s : new net.minecraft.world.entity.EquipmentSlot[] {
				net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET }) {
			player.setItemSlot(s, ItemStack.EMPTY);
		}
		com.projecthero.mod.ironman.IronManFaceplate.reconcile(player);
		helper.assertFalse(com.projecthero.mod.ironman.IronManFaceplate.isOpen(player),
				"taking the armour off closes the faceplate");

		// blades
		giveFullSuit(player, "mark_v");
		com.projecthero.mod.ironman.IronManEnergy.setEnergy(player, "mark_v", 5000f);
		var atk = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
		com.projecthero.mod.ironman.ability.IronManAbilityManager.handle(player, com.projecthero.mod.hero.AbilitySlot.SLOT_3, true);
		helper.assertTrue(com.projecthero.mod.ironman.IronManBlade.active(player), "X extends the blades");
		com.projecthero.mod.ironman.IronManBlade.tick(player);
		var mod = atk.getModifier(com.projecthero.mod.ProjectHeroMod.id("iron_man_blade_strength"));
		helper.assertTrue(mod != null && Math.abs(mod.amount() - 4.0) < 1e-4, "blades add +4 melee");
		// suit shutdown retracts them + clears the modifier
		com.projecthero.mod.ironman.IronManSuitTicker.shutDownAllSystems(player,
				com.projecthero.mod.ironman.TonyStark.state(player), com.projecthero.mod.ironman.suit.IronManSuits.byId("mark_v"));
		com.projecthero.mod.ironman.IronManBlade.tick(player);
		helper.assertFalse(com.projecthero.mod.ironman.IronManBlade.active(player), "shutdown retracts the blades");
		helper.assertTrue(atk.getModifier(com.projecthero.mod.ProjectHeroMod.id("iron_man_blade_strength")) == null,
				"the blade melee modifier is removed");
		helper.succeed();
	}

	/** "changes 19": plain C puts on a full inventory suit; every mark except the Mark 1 is Fabricator-built. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void changes19CallAndFabricatorRecipes(GameTestHelper helper) {
		for (String id : new String[] { "mark_2", "mark_4", "mark_6", "mark_iii", "mark_v", "mark_vii" }) {
			helper.assertTrue(com.projecthero.mod.ironman.fabricator.FabricatorRecipes.hasArmorRecipes(id),
					id + " must have Fabricator armour recipes");
		}
		helper.assertFalse(com.projecthero.mod.ironman.fabricator.FabricatorRecipes.hasArmorRecipes("mark_1"),
				"the Mark 1 is table-only, not Fabricator-built");

		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[] {
				net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
				net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			player.getInventory().add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_1", t)));
		}
		boolean equipped = com.projecthero.mod.ironman.suit.IronManSuitCall.autoEquipInventorySuit(player);
		helper.assertTrue(equipped, "a full suit in the inventory auto-equips on plain C");
		helper.succeed();
	}

	/** "changes 18": the Power Suppressor strips every superpower on a sneak-use. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void powerSuppressorStripsEverything(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		com.projecthero.mod.worthiness.Worthiness.setScore(player, 100);
		com.projecthero.mod.hero.ExperimentalPowers.grant(player,
				com.projecthero.mod.hero.Powers.all().iterator().next());
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.hasPower(player)
				&& com.projecthero.mod.worthiness.Worthiness.isWorthy(player)
				&& !com.projecthero.mod.hero.ExperimentalPowers.state(player).ownedPowers.isEmpty(),
				"player must start with all three power kinds");
		player.setShiftKeyDown(true);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new ItemStack(com.projecthero.mod.item.ModItems.POWER_SUPPRESSOR));
		com.projecthero.mod.item.ModItems.POWER_SUPPRESSOR.use(player.level(), player,
				net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.hasPower(player), "Tony Stark power removed");
		helper.assertFalse(com.projecthero.mod.worthiness.Worthiness.isWorthy(player), "worthiness reset");
		helper.assertTrue(com.projecthero.mod.hero.ExperimentalPowers.state(player).ownedPowers.isEmpty(),
				"experimental powers cleared");
		helper.succeed();
	}

	/** "changes 17": Protocol Phoenix cooldown is persistent and the incapacitated flag gates input. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void protocolPhoenixCooldownState(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.phoenixReady(player), "Phoenix starts ready");
		helper.assertFalse(com.projecthero.mod.ironman.ProtocolPhoenix.incapacitated(player), "not incapacitated by default");
		com.projecthero.mod.ironman.TonyStark.setPhoenixReadyAt(player,
				player.level().getGameTime() + com.projecthero.mod.ironman.ProtocolPhoenix.COOLDOWN_TICKS);
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.phoenixReady(player), "Phoenix on cooldown after activation");
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.phoenixCooldownRemaining(player) > 250 * 20,
				"cooldown must be ~5 minutes");
		// The cooldown survives a state copy() (the codec/sync round-trip the attachment does on every write).
		var copied = com.projecthero.mod.ironman.TonyStark.state(player).copy();
		helper.assertTrue(copied.phoenixReadyAt == com.projecthero.mod.ironman.TonyStark.state(player).phoenixReadyAt,
				"phoenixReadyAt must survive a state copy");
		helper.succeed();
	}

	// ---------------- "changes 20": Iron Man ----------------

	/**
	 * Builds a real {@link net.minecraft.world.inventory.CraftingMenu}, lays the Mark 1 helmet pattern
	 * into it and lets vanilla resolve the recipe, so this exercises the actual
	 * {@code CraftingMenuMixin} gate rather than just the policy predicate.
	 */
	private static net.minecraft.world.inventory.CraftingMenu markOneHelmetGrid(GameTestHelper helper,
			ServerPlayer player) {
		net.minecraft.world.inventory.CraftingMenu menu = new net.minecraft.world.inventory.CraftingMenu(
				1, player.getInventory(),
				net.minecraft.world.inventory.ContainerLevelAccess.create(player.level(), player.blockPosition()));
		ItemStack iron = new ItemStack(net.minecraft.world.item.Items.IRON_INGOT);
		ItemStack circuit = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.BASIC_CIRCUIT);
		// "IBI" / "I I" -- slot 0 is the result, 1..9 are the 3x3 grid row-major.
		menu.getSlot(1).set(iron.copy());
		menu.getSlot(2).set(circuit.copy());
		menu.getSlot(3).set(iron.copy());
		menu.getSlot(4).set(iron.copy());
		menu.getSlot(6).set(iron.copy());
		menu.slotsChanged(menu.getSlot(1).container);
		return menu;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManSuitIsNotCraftableWithoutTonyStark(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		helper.assertFalse(com.projecthero.mod.ironman.TonyStark.hasPower(player), "no power yet");

		net.minecraft.world.inventory.CraftingMenu menu = markOneHelmetGrid(helper, player);
		helper.assertTrue(menu.getSlot(0).getItem().isEmpty(),
				"a Mark 1 helmet must not be craftable without the Tony Stark power");

		// Granting the power makes the very same grid produce the helmet.
		com.projecthero.mod.ironman.TonyStark.grant(player);
		menu.slotsChanged(menu.getSlot(1).container);
		helper.assertTrue(menu.getSlot(0).getItem().getItem()
						== com.projecthero.mod.ironman.item.IronManItems.armor("mark_1",
								net.minecraft.world.item.ArmorItem.Type.HELMET),
				"with the Tony Stark power the same grid must craft the Mark 1 helmet");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManCraftingGateCoversSuitsOnly(GameTestHelper helper) {
		// Every suit piece of every mark is gated...
		for (String id : new String[]{"mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii"}) {
			for (net.minecraft.world.item.ArmorItem.Type type : net.minecraft.world.item.ArmorItem.Type.values()) {
				var piece = com.projecthero.mod.ironman.item.IronManItems.armor(id, type);
				if (piece == null) {
					continue;
				}
				helper.assertTrue(com.projecthero.mod.ironman.IronManCrafting.requiresTonyStark(new ItemStack(piece)),
						id + " " + type + " must require the Tony Stark power to craft");
			}
		}
		helper.assertTrue(com.projecthero.mod.ironman.IronManCrafting.requiresTonyStark(
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.MARK_V_SUITCASE)),
				"the Mark V suitcase is a suit and must be gated");

		// ...but the pre-power build-up is deliberately left open, above all the Arc Reactor, which is
		// what grants the power in the first place. Gating it would make the tree unreachable.
		for (net.minecraft.world.item.Item open : new net.minecraft.world.item.Item[]{
				com.projecthero.mod.ironman.item.IronManItems.ARC_REACTOR,
				com.projecthero.mod.ironman.item.IronManItems.BASIC_CIRCUIT,
				com.projecthero.mod.ironman.item.IronManItems.BLANK_BLUEPRINT}) {
			helper.assertFalse(com.projecthero.mod.ironman.IronManCrafting.requiresTonyStark(new ItemStack(open)),
					open + " must stay craftable without the Tony Stark power");
		}
		helper.succeed();
	}

	/**
	 * "changes 21": the Blank Blueprint is the sole progression gate. A mark's blueprint is only
	 * obtainable once the whole previous mark's suit is built; {@code stampBlueprint} enforces it and
	 * completing the fourth piece of a mark promotes it into {@code builtSuits}.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void blankBlueprintProgressionIsLinear(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);

		// The power hands exactly one Blank Blueprint, no mark blueprint.
		helper.assertTrue(player.getInventory().countItem(com.projecthero.mod.ironman.item.IronManItems.BLANK_BLUEPRINT) == 1,
				"gaining the power gives one Blank Blueprint");

		// Mark 1 has no prerequisite -- stamping it works immediately.
		player.getInventory().add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.BLANK_BLUEPRINT));
		com.projecthero.mod.ironman.TonyStark.stampBlueprint(player, "mark_1");
		helper.assertTrue(player.getInventory().countItem(com.projecthero.mod.ironman.item.IronManItems.MARK_1_BLUEPRINT) == 1,
				"Mark 1 blueprint stamps with no prerequisite");

		// Mark 2 is locked until the entire Mark 1 suit is built.
		player.getInventory().add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.BLANK_BLUEPRINT));
		com.projecthero.mod.ironman.TonyStark.stampBlueprint(player, "mark_2");
		helper.assertTrue(player.getInventory().countItem(com.projecthero.mod.ironman.item.IronManItems.MARK_2_BLUEPRINT) == 0,
				"Mark 2 blueprint must be locked until the full Mark 1 suit is built");

		for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[] {
				net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
				net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			helper.assertFalse(com.projecthero.mod.ironman.TonyStark.hasFabricatedFullSuit(player, "mark_1"),
					"Mark 1 is not complete before its 4th piece");
			com.projecthero.mod.ironman.TonyStark.recordSuitPiece(player, "mark_1", t);
		}
		helper.assertTrue(com.projecthero.mod.ironman.TonyStark.hasBuilt(player, "mark_1"),
				"a full set of pieces promotes Mark 1 into builtSuits");

		com.projecthero.mod.ironman.TonyStark.stampBlueprint(player, "mark_2");
		helper.assertTrue(player.getInventory().countItem(com.projecthero.mod.ironman.item.IronManItems.MARK_2_BLUEPRINT) == 1,
				"Mark 2 blueprint unlocks once the whole Mark 1 suit is built");

		// Skipping ahead is still blocked: Mark III needs the full Mark 2.
		player.getInventory().add(new ItemStack(com.projecthero.mod.ironman.item.IronManItems.BLANK_BLUEPRINT));
		com.projecthero.mod.ironman.TonyStark.stampBlueprint(player, "mark_iii");
		helper.assertTrue(player.getInventory().countItem(com.projecthero.mod.ironman.item.IronManItems.MARK_III_BLUEPRINT) == 0,
				"Mark III is still gated on a full Mark 2 suit");
		helper.succeed();
	}

	/**
	 * Server half of the faceplate ("changes 19"/"changes 20"). The visual half -- that retracting the
	 * helmet actually uncovers the wearer's skin -- lives in {@code SuperheroArmorRenderer} and
	 * {@code PlayerModelMixin} and cannot be asserted from a headless gametest server; what is
	 * checkable here is that the state this rendering reads is only ever set while a suit is worn.
	 */
	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManFaceplateOnlyOpensWhileArmored(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);

		com.projecthero.mod.ironman.IronManFaceplate.toggle(player);
		helper.assertFalse(com.projecthero.mod.ironman.IronManFaceplate.isOpen(player),
				"H must do nothing with no Iron Man helmet on");

		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
				new ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii",
						net.minecraft.world.item.ArmorItem.Type.HELMET)));
		com.projecthero.mod.ironman.IronManFaceplate.toggle(player);
		helper.assertTrue(com.projecthero.mod.ironman.IronManFaceplate.isOpen(player),
				"H must retract the helmet while an Iron Man helmet is worn");
		com.projecthero.mod.ironman.IronManFaceplate.toggle(player);
		helper.assertFalse(com.projecthero.mod.ironman.IronManFaceplate.isOpen(player), "H again closes it");

		// An open faceplate must not survive the helmet coming off, or the renderer would keep the
		// wearer's head bare under a suit they are no longer wearing.
		com.projecthero.mod.ironman.IronManFaceplate.toggle(player);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
		com.projecthero.mod.ironman.IronManFaceplate.reconcile(player);
		helper.assertFalse(com.projecthero.mod.ironman.IronManFaceplate.isOpen(player),
				"taking the helmet off must close the faceplate state");
		helper.succeed();
	}

	// ---------------- v0.6.23: power-tier exclusivity ----------------

	/** A player who is already a Hero-Tier hero cannot pick up an experimental mutation. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void heroTierBlocksExperimentalGrant(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(player);
		helper.assertTrue(com.projecthero.mod.hero.HeroTiers.hasHeroTier(player), "Tony Stark = hero tier");

		// The mutation manager funnels every grant through attemptExposure, which now refuses while
		// hasHeroTier; grant() itself is the shortcut the *command* uses (which wipes first), so here
		// we assert the invariant helper directly.
		helper.assertFalse(com.projecthero.mod.hero.HeroTiers.hasExperimental(player),
				"a hero-tier player starts with no experimental powers");
		helper.succeed();
	}

	/** A command grant of any tier replaces every power the player had, of any tier. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void commandGrantWipesEveryTier(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ExperimentalPowers.grant(player, power("power_01_super_strength"));
		com.projecthero.mod.ironman.TonyStark.grant(player);

		com.projecthero.mod.hero.HeroTiers.wipeAll(player);
		helper.assertFalse(com.projecthero.mod.hero.HeroTiers.hasExperimental(player), "experimental wiped");
		helper.assertFalse(com.projecthero.mod.hero.HeroTiers.hasHeroTier(player), "hero-tier wiped");

		ExperimentalPowers.grant(player, power("power_05_geokinesis"));
		helper.assertTrue(ExperimentalPowers.owns(player, power("power_05_geokinesis")), "new power granted");
		helper.assertFalse(ExperimentalPowers.owns(player, power("power_01_super_strength")),
				"the previous power did not survive the replace");
		helper.succeed();
	}

	/** Becoming Spider-Man consumes every experimental power, not just Spider Adhesion. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void spiderEvolutionConsumesAllMutations(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		ExperimentalPowers.grant(player, power(com.projecthero.mod.spider.SpiderMan.SPIDER_ADHESION_KEY));
		ExperimentalPowers.grant(player, power("power_03_flight"));

		helper.assertTrue(com.projecthero.mod.spider.SpiderMan.evolveFromAdhesion(player), "evolution succeeds");
		helper.assertFalse(com.projecthero.mod.hero.HeroTiers.hasExperimental(player),
				"every mutation is gone after becoming Spider-Man");
		helper.assertTrue(com.projecthero.mod.spider.SpiderMan.hasPower(player), "and Spider-Man is granted");
		helper.succeed();
	}

	/** The Teleportation return marker records the dimension it was set in. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void teleportMarkerRemembersDimension(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Power teleport = power("power_11_teleportation");
		ExperimentalPowers.grant(player, teleport);
		ExperimentalPowers.setMarker(player, teleport, "return", player.blockPosition());

		var dim = ExperimentalPowers.getMarkerDimension(player, teleport, "return");
		helper.assertTrue(dim != null && dim.equals(player.level().dimension()),
				"marker dimension should be the overworld it was set in");
		ExperimentalPowers.clearMarker(player, teleport, "return");
		helper.assertTrue(ExperimentalPowers.getMarkerDimension(player, teleport, "return") == null,
				"clearing the marker clears its dimension too");
		helper.succeed();
	}
}
