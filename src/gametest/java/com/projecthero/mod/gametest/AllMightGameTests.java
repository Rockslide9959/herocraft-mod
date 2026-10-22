package com.projecthero.mod.gametest;

import com.mojang.serialization.JsonOps;

import com.projecthero.mod.allmight.AllMight;
import com.projecthero.mod.allmight.AllMightAbilities;
import com.projecthero.mod.allmight.AllMightConfig;
import com.projecthero.mod.allmight.data.AllMightState;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.HeroTiers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for All Might / One For All (v0.12.34): the grant, the two forms and their stats (no stacking),
 * the cooldown gates (v0.16.6: no OFA timer -- Output / Strain are in AllMightRevampGameTests), Detroit and United States of Smash landing on real mobs at their impact
 * frame, Full Cowl not stacking, the damage factors, persistence and revoke. Mock players are not reliably ticked by the
 * server, so each test drives {@link AllMight#tick} itself, exactly as the server tick would.
 */
public class AllMightGameTests implements FabricGameTest {
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		// the test volume is walled in with barriers: open a room in front for the Smashes
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -3), base.offset(3, 5, 8))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		AllMight.grant(p);
		return p;
	}

	/** Puts the hero straight into the Power Form (no transformation window) for tests of the abilities. */
	private static void powerForm(ServerPlayer p) {
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_STATE, s);
		AllMight.reconcile(p);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> AllMight.tick(p));
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void costumeLockerEquipsOnTransformAndReturnsOnRevert(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		helper.assertFalse(com.projecthero.mod.allmight.AllMightSuit.wearing(p), "nobody starts in the costume");
		helper.assertTrue(p.getInventory().isEmpty(), "nothing in the inventory at the start");
		// two costume pieces in the locker
		p.setAttached(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_LOCKER,
				net.minecraft.world.item.component.ItemContainerContents.fromItems(java.util.List.of(
						new net.minecraft.world.item.ItemStack(com.projecthero.mod.allmight.item.AllMightItems.CHESTPLATE),
						new net.minecraft.world.item.ItemStack(com.projecthero.mod.allmight.item.AllMightItems.LEGGINGS))));
		AllMight.toggleForm(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(com.projecthero.mod.allmight.item.AllMightItems.CHESTPLATE), "the costume goes on when he transforms");
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).is(com.projecthero.mod.allmight.item.AllMightItems.LEGGINGS), "and the trousers");
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(), "no helmet");
		helper.runAfterDelay(AllMightConfig.TRANSFORM_TICKS + 5, () -> {
			AllMight.toggleForm(p);
			helper.assertFalse(com.projecthero.mod.allmight.AllMightSuit.wearing(p), "the costume comes off in the Base Form");
			helper.assertTrue(p.getInventory().isEmpty(), "it went back into the locker, not the inventory");
			var locker = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.ALL_MIGHT_LOCKER,
					net.minecraft.world.item.component.ItemContainerContents.EMPTY);
			helper.assertTrue(locker.nonEmptyStream().count() == 2, "both pieces are back in the locker");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void powerFormTakesNoFallDamage(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		float before = p.getHealth();
		p.hurt(p.damageSources().fall(), 10.0f);
		helper.assertTrue(p.getHealth() >= before, "Power Form takes no fall damage, health " + before + " -> " + p.getHealth());
		helper.succeed();
	}

	private static Zombie zombieAhead(GameTestHelper helper, ServerPlayer p, double distance) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
		z.moveTo(p.getX() + fwd.x * distance, p.getY(), p.getZ() + fwd.z * distance);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantGivesThePowerAsAPlainBaseForm(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		helper.assertFalse(AllMight.hasPower(p), "no power yet");
		helper.assertTrue(AllMight.grant(p), "first grant works");
		helper.assertFalse(AllMight.grant(p), "second grant refused");
		helper.assertTrue(AllMight.hasPower(p) && HeroTiers.holdsHero(p, AllMight.KEY), "a registered Hero-Tier Primary power");
		helper.assertTrue(AllMight.limit(p) == 20f && AllMight.state(p).broken == 0 && AllMight.state(p).limb(AllMightState.Limb.RIGHT_ARM) == 0f && AllMight.state(p).limb(AllMightState.Limb.LEFT_ARM) == 0f, "v0.16.7: a 20% safe limit and healthy limbs");
		helper.assertFalse(AllMight.isFullPower(p), "starts in the Base Form");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 1e-6, "Base Form melee is a plain 1");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0) < 1e-6, "Base Form has 20 max health");
		helper.assertTrue(AllMight.damageTakenFactor(p) == 1.0f, "Base Form takes full damage");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void hTogglesTheFormWithoutStackingModifiers(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		pump(helper, p);
		AllMight.toggleForm(p);
		helper.assertTrue(AllMight.isFullPower(p), "H -> Power Form");
		AllMight.toggleForm(p); // pressed again inside the debounce / transformation window: ignored
		helper.assertTrue(AllMight.isFullPower(p), "a spammed H does nothing while transforming");
		helper.assertTrue(AllMight.transforming(p), "damage-proof while transforming");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 13.0) < 1e-6,
				"Power Form melee is 13, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		helper.assertTrue(Math.abs(p.getMaxHealth() - 40.0) < 1e-6, "Power Form has 40 max health, got " + p.getMaxHealth());
		helper.runAfterDelay(AllMightConfig.TRANSFORM_TICKS + 5, () -> {
			AllMight.reconcile(p);
			AllMight.reconcile(p);
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 13.0) < 1e-6, "reconciling never stacks");
			helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)
					&& p.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED).getAmplifier() == 2, "Speed III");
			helper.assertFalse(p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "v0.12.39: a slow 1 HP / 4 s self-heal, no Regeneration effect");
			double grown = p.getAttributeValue(Attributes.SCALE);
			helper.assertTrue(Math.abs(grown - 1.5) < 0.02, "grown to 1.5x (2.7 blocks), got " + grown);
			helper.runAfterDelay(AllMightConfig.TRANSFORM_TICKS + 5, () -> {
				AllMight.toggleForm(p);
				helper.assertFalse(AllMight.isFullPower(p), "H again -> Base Form");
				helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 1e-6, "back to plain stats");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void healthPercentageCarriesAcrossForms(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setHealth(10.0f); // 50%
		AllMight.toggleForm(p);
		helper.assertTrue(Math.abs(p.getHealth() - 20.0f) < 0.01f, "50% of 20 becomes 50% of 40, got " + p.getHealth());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void formDamageFactors(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(AllMight.damageTakenFactor(p) == 1.0f, "Base Form takes 100%");
		powerForm(p);
		helper.assertTrue(Math.abs(AllMight.damageTakenFactor(p) - 0.5f) < 1e-4f, "Power Form takes 50%");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void baseFormCannotUseAbilities(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 1, true);
		AllMightAbilities.leap(p);
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.LEAP) == 0 && AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) == 0, "nothing started in the Base Form");
		helper.succeed();
	}


	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void detroitSmashHitsAtTheImpactFrameAndCostsOfaAndCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		pump(helper, p);
		Zombie z = zombieAhead(helper, p, 3.0);
		float before = z.getHealth();
		AbilityRouter.handleInput(p, 1, true); // R = Detroit Smash
		helper.assertTrue(AllMight.state(p).limb(AllMightState.Limb.RIGHT_ARM) == 0f && AllMight.state(p).limb(AllMightState.Limb.LEFT_ARM) == 0f, "Detroit Smash inside the limit costs nothing (v0.16.7: no limb strain)");
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.DETROIT) > 0, "on cooldown");
		helper.assertTrue(z.getHealth() == before, "nothing lands before the wind-up ends");
		helper.runAfterDelay(AllMightConfig.DETROIT_WINDUP + 4, () -> {
			helper.assertTrue(z.getHealth() < before || !z.isAlive(), "the punch hurt the zombie at the impact frame");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void unitedStatesOfSmashNeedsAFiveSecondHold(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		pump(helper, p);
		Zombie z = zombieAhead(helper, p, 5.0);
		z.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 1000, 0, false, false));
		AbilityRouter.handleInput(p, 4, true); // Z held
		helper.assertTrue(AllMight.state(p).limb(AllMightState.Limb.RIGHT_ARM) == 0f && AllMight.state(p).limb(AllMightState.Limb.LEFT_ARM) == 0f, "nothing is spent while charging (v0.12.38)");
		helper.assertTrue(AllMight.state(p).chargeStart > 0L, "the synced charge start drives the HUD bar");
		helper.assertTrue(z.isAlive() && z.getHealth() == z.getMaxHealth(), "the charge is not an instant hit");
		helper.runAfterDelay(AllMightConfig.UNITED_STATES_CHARGE_TICKS - 10, () -> {
			// v0.15.8: read the charge itself, not the zombie -- on CI a neighbouring test could nick it
			helper.assertTrue(AllMight.state(p).chargeStart > 0L
					&& AllMight.cooldownRemaining(p, AllMightAbilities.UNITED_STATES) == 0, "still charging before 5 s");
		});
		helper.runAfterDelay(AllMightConfig.UNITED_STATES_CHARGE_TICKS + 6, () -> {
			helper.assertTrue(z.getHealth() < z.getMaxHealth() || !z.isAlive(), "75 damage lands when the charge completes");
			float arm = AllMight.state(p).limb(AllMightState.Limb.RIGHT_ARM);
			helper.assertTrue(arm > 80.0f, "v0.16.7: 100% past a 20% limit tears at the right arm (1.5 x 80 x 0.75 = 90), arm=" + arm);
			helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.UNITED_STATES) > 60 * 20, "the 75 s cooldown starts once it is cast");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void releasingZEarlyCancelsAndRefunds(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		AbilityRouter.handleInput(p, 4, true);
		helper.assertTrue(AllMight.state(p).limb(AllMightState.Limb.RIGHT_ARM) == 0f && AllMight.state(p).limb(AllMightState.Limb.LEFT_ARM) == 0f, "nothing spent while charging");
		AbilityRouter.handleInput(p, 4, false);
		helper.assertTrue(AllMight.state(p).limb(AllMightState.Limb.RIGHT_ARM) == 0f && AllMight.state(p).limb(AllMightState.Limb.LEFT_ARM) == 0f, "still nothing after an early release");
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.UNITED_STATES) == 0, "no cooldown for a cancelled charge");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stateSurvivesTheCodecAndRevokeCleansUp(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AllMightState s = AllMight.state(p).copy();
		s.fullPower = true;
		s.plusUltra = true;
		s.outputPct = 45f;
		s.mastery = 33.5f;
		s.limbs[AllMightState.Limb.LEFT_ARM.ordinal()] = 42f;
		s.broken = AllMightState.Limb.LEGS.bit();
		s.loanRole = AllMightState.LOAN_LENDER;
		s.loanPartner = "abc";
		s.lendReadyAt = 77L;
		s.abilityReadyAt.put(AllMightAbilities.TEXAS, 999L);
		var json = AllMightState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		AllMightState back = AllMightState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.hasPower && back.fullPower && back.plusUltra && back.outputPct == 45f && back.mastery == 33.5f
				&& back.limb(AllMightState.Limb.LEFT_ARM) == 42f && back.isBroken(AllMightState.Limb.LEGS) && !back.isBroken(AllMightState.Limb.LEFT_ARM)
				&& back.loanRole == AllMightState.LOAN_LENDER && "abc".equals(back.loanPartner) && back.lendReadyAt == 77L && back.abilityReadyAt.get(AllMightAbilities.TEXAS) == 999L,
				"power, form, Output, limit, limbs, loan and cooldowns persist");
		// migration: a pre-0.16.6 save (the 300-point "ofa" key) or a 0.16.6 one (3-level "output", whole-body "strain", "recoil_until")
		// loads cleanly: Output 0, the 20% starting limit, healthy limbs
		com.google.gson.JsonObject old = new com.google.gson.JsonObject();
		old.addProperty("has_power", true);
		old.addProperty("ofa", 123.0f);
		old.addProperty("output", 2);
		old.addProperty("strain", 88.0f);
		old.addProperty("recoil_until", 12345L);
		AllMightState migrated = AllMightState.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(migrated.hasPower && migrated.outputPct == 0f && migrated.mastery == 20f && migrated.broken == 0
				&& migrated.limb(AllMightState.Limb.RIGHT_ARM) == 0f, "the old OFA / Strain / recoil keys are ignored");
		powerForm(p);
		AllMight.revoke(p);
		helper.assertFalse(AllMight.hasPower(p), "revoked");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0) < 1e-6, "every modifier removed");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0) < 1e-6, "max health back to normal");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void leapIsCheapAndOnCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		AbilityRouter.handleInput(p, 3, true); // X = Leap
		helper.assertTrue(AllMight.state(p).limb(AllMightState.Limb.RIGHT_ARM) == 0f && AllMight.state(p).limb(AllMightState.Limb.LEFT_ARM) == 0f, "the Leap is free");
		helper.assertTrue(AllMight.cooldownRemaining(p, AllMightAbilities.LEAP) > 0 && AllMight.cooldownRemaining(p, AllMightAbilities.LEAP) <= 30, "1.5 s cooldown");
		helper.assertTrue(p.getDeltaMovement().y > 0.5 && p.getDeltaMovement().z > 1.5, "launched along the look direction, v=" + p.getDeltaMovement());
		helper.succeed();
	}

	// ---------------- v0.12.43: the Power Form only wears the All Might armour ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void transformingTearsRegularArmourOffAndDropsIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		net.minecraft.world.item.ItemStack iron = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, iron);
		AllMight.toggleForm(p);
		helper.assertTrue(AllMight.isFullPower(p), "transformed");
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "the regular armour unequipped itself");
		var drops = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(4.0),
				e -> e.getItem().is(net.minecraft.world.item.Items.IRON_CHESTPLATE));
		helper.assertTrue(drops.size() == 1, "and dropped onto the ground");
		helper.assertTrue(drops.get(0).getItem().getDamageValue() == 50, "after taking 50 durability damage, has " + drops.get(0).getItem().getDamageValue());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void thePowerFormBouncesRegularArmourButKeepsAllMightArmour(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		powerForm(p);
		p.tickCount = 5;
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(com.projecthero.mod.allmight.item.AllMightItems.CHESTPLATE));
		AllMight.tick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(), "regular armour is refused in the Power Form");
		helper.assertTrue(p.getInventory().contains(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET)), "and goes back to the inventory");
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(com.projecthero.mod.allmight.item.AllMightItems.CHESTPLATE), "the All Might armour stays on");
		helper.succeed();
	}
}
