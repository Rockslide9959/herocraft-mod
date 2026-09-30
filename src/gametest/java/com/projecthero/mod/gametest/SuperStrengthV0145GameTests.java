package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p01.SuperStrengthHandlers;
import com.projecthero.mod.hero.revamp.batcha.ThrownChunkEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.5 Super Strength rework: the six-key kit (no H / N), the three remaining passives, the new numbers, and the
 * Shift variants' own cooldowns. Mock players are not reliably ticked, so the tests drive
 * {@link ExperimentalPowers#serverTick} themselves; every entity stays inside the 8x8x8 test cage.
 */
public class SuperStrengthV0145GameTests implements FabricGameTest {
	// ---------------- helpers ----------------

	/** A survival mock Super Strength player on a stone floor near the middle of the cage, facing +Z. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, 0), base.offset(2, 3, 4))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, 0), base.offset(2, -1, 4))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0);
		p.setXRot(0);
		p.setYHeadRot(0);
		Power power = Powers.byKey(SuperStrengthHandlers.KEY);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	/** A no-AI, unarmoured 100-health zombie {@code dist} blocks in front of {@code p}, so exact damage is readable. */
	private static Zombie dummy(GameTestHelper helper, ServerPlayer p, double dist) {
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(p.getX(), p.getY(), p.getZ() + dist, 180f, 0f);
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		z.setHealth(100f);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static Power power() {
		return Powers.byKey(SuperStrengthHandlers.KEY);
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, power(), name);
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static AttributeModifier mod(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
			net.minecraft.resources.ResourceLocation id) {
		AttributeInstance inst = p.getAttribute(attr);
		return inst == null ? null : inst.getModifier(id);
	}

	// ---------------- the kit ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerIsRegistered(GameTestHelper helper) {
		helper.assertTrue(Powers.byKey(SuperStrengthHandlers.KEY) != null, "Super Strength is registered");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixAbilitiesInTheNewOrderAndNoUtilityKeys(GameTestHelper helper) {
		Power power = power();
		helper.assertTrue(power.abilities().size() == 6, "exactly six abilities, has " + power.abilities().size());
		List<String> ids = List.of("haymaker", "ground_slam", "power_leap", "bull_rush", "grab_throw", "maximum_effort");
		for (int i = 0; i < ids.size(); i++) {
			AbilitySlot slot = AbilitySlot.byNumber(i + 1);
			helper.assertTrue(ids.get(i).equals(power.ability(slot).id()),
					"slot " + slot.defaultKey() + " should be " + ids.get(i) + ", is " + power.ability(slot).id());
		}
		helper.assertFalse(power.hasSlot(AbilitySlot.SLOT_7) || power.hasSlot(AbilitySlot.SLOT_8), "no H / N abilities");
		helper.assertTrue(power.ability(AbilitySlot.SLOT_6).cooldownTicks() == 1400, "Maximum Effort: 70 s cooldown");
		helper.assertTrue(power.passiveKeys().equals(List.of(
				"projecthero.power." + SuperStrengthHandlers.KEY + ".passive.melee",
				"projecthero.power." + SuperStrengthHandlers.KEY + ".passive.knockback",
				"projecthero.power." + SuperStrengthHandlers.KEY + ".passive.charged")), "exactly the three passives");
		helper.succeed();
	}

	// ---------------- passives ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyTheListedPassivesApply(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setSprinting(true);
		tick(p, 2);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - (p.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + 10.0)) < 1e-3,
				"bare hands: exactly +10 attack damage, got " + p.getAttributeValue(Attributes.ATTACK_DAMAGE));
		AttributeModifier kb = mod(p, Attributes.ATTACK_KNOCKBACK, SuperStrengthHandlers.PASSIVE_ATK_KB);
		helper.assertTrue(kb != null && Math.abs(kb.amount() - SuperStrengthHandlers.KNOCKBACK_BONUS) < 1e-6, "150% knockback modifier");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.JUMP_STRENGTH) - p.getAttributeBaseValue(Attributes.JUMP_STRENGTH)) < 1e-6,
				"no jump boost");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1e-6, "no knockback resistance");
		helper.assertTrue(p.getAttribute(Attributes.MOVEMENT_SPEED).getModifiers().stream()
				.noneMatch(m -> m.id().getNamespace().equals("projecthero")), "no sprint-speed bonus (vanilla sprinting only)");
		helper.assertTrue(p.getAttribute(Attributes.STEP_HEIGHT).getModifiers().stream()
				.noneMatch(m -> m.id().getNamespace().equals("projecthero")), "no step assist outside Bull Rush");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY)
				- p.getAttributeBaseValue(Attributes.WATER_MOVEMENT_EFFICIENCY)) < 1e-6, "no swim bonus");
		helper.assertFalse(p.hasCorrectToolForDrops(Blocks.STONE.defaultBlockState()), "bare hands are not a stone pickaxe any more");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void unarmedBonusOnlyWithoutAWeapon(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tick(p, 1);
		helper.assertTrue(mod(p, Attributes.ATTACK_DAMAGE, SuperStrengthHandlers.PASSIVE_ATK) != null, "bare-handed: +10");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		tick(p, 1);
		helper.assertTrue(mod(p, Attributes.ATTACK_DAMAGE, SuperStrengthHandlers.PASSIVE_ATK) == null, "a sword brings its own damage");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE));
		tick(p, 1);
		helper.assertTrue(mod(p, Attributes.ATTACK_DAMAGE, SuperStrengthHandlers.PASSIVE_ATK) != null, "a block in hand still punches");
		ExperimentalPowers.forget(p, power());
		helper.assertTrue(mod(p, Attributes.ATTACK_DAMAGE, SuperStrengthHandlers.PASSIVE_ATK) == null
				&& mod(p, Attributes.ATTACK_KNOCKBACK, SuperStrengthHandlers.PASSIVE_ATK_KB) == null, "forgetting clears both");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0145_strength_punch")
	public void chargedPunchIsMeleePlusFifteenWithATwoSecondCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tick(p, 1);
		Zombie z = dummy(helper, p, 2.0);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		float expected = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) + 15f;
		helper.assertTrue(Math.abs(expected - 26f) < 1e-3, "bare-handed Charged Punch = 11 + 15, got " + expected);
		SuperStrengthHandlers.performChargedPunch(p);
		helper.assertTrue(Math.abs((100f - z.getHealth()) - expected) < 0.5f, "dealt " + (100f - z.getHealth()) + ", expected " + expected);
		helper.assertTrue(res(p, "charged_cd") == SuperStrengthHandlers.CHARGED_CD_TICKS, "2 s cooldown");
		helper.assertTrue(SuperStrengthHandlers.CHARGED_HOLD_TICKS == 20, "1 s wind-up");
		float after = z.getHealth();
		SuperStrengthHandlers.performChargedPunch(p);
		helper.assertTrue(z.getHealth() == after, "no second punch during the cooldown");
		helper.succeed();
	}

	// ---------------- G / Shift+G ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0145_strength_slam")
	public void groundSlamDealsTwenty(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = dummy(helper, p, 2.0);
		AbilityRouter.handleInput(p, 2, true);
		AbilityRouter.handleInput(p, 2, false);
		helper.assertTrue(Math.abs((100f - z.getHealth()) - SuperStrengthHandlers.SLAM_DAMAGE) < 0.5f,
				"Ground Slam hit for " + (100f - z.getHealth()) + ", expected 20");
		helper.assertTrue(ExperimentalPowers.cooldownRemainingTicks(p, power(), power().ability(AbilitySlot.SLOT_2)) > 100,
				"Ground Slam goes on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0145_strength_clap")
	public void thunderclapIgnoresTheSlamCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ExperimentalPowers.triggerCooldown(p, power(), power().ability(AbilitySlot.SLOT_2), 200);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 2, true);
		helper.assertTrue(res(p, "clap_cd") > 100f, "Shift+G claps even while Ground Slam recharges");
		float cd = res(p, "clap_cd");
		tick(p, 5);
		helper.assertTrue(res(p, "clap_cd") < cd, "the clap cooldown counts down");
		helper.succeed();
	}

	// ---------------- C ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0145_strength_effort")
	public void maximumEffortLastsThirtySecondsWithASeventySecondCooldown(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 6, true); // C
		helper.assertTrue(SuperStrengthHandlers.maxEffortActive(p), "Maximum Effort runs");
		helper.assertTrue(res(p, "effort_left") == 600f, "30 s");
		int cd = ExperimentalPowers.cooldownRemainingTicks(p, power(), power().ability(AbilitySlot.SLOT_6));
		helper.assertTrue(cd == HeroConfig.get().scaledCooldown(1400), "70 s cooldown, got " + cd);
		helper.succeed();
	}

	// ---------------- Z ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0145_strength_rush")
	public void bullRushIsOnZ(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		AbilityRouter.handleInput(p, 4, true); // Z: start charging
		helper.assertTrue(res(p, "z_charge") > 0.5f, "Z starts the Bull Rush charge");
		AbilityRouter.handleInput(p, 4, false); // released early
		helper.assertTrue(res(p, "z_charge") == 0f && res(p, "z_run_end") == 0f, "releasing early cancels");
		helper.succeed();
	}

	// ---------------- V / Shift+V ----------------

	@GameTest(template = EMPTY_STRUCTURE, batch = "v0145_strength_rip", timeoutTicks = 100)
	public void ripAndHurlOnShiftVThrowsHarder(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ExperimentalPowers.triggerCooldown(p, power(), power().ability(AbilitySlot.SLOT_5), 200);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 5, true);
		int id = (int) res(p, "rip_id");
		helper.assertTrue(id != 0, "Shift+V tears up a boulder even while Grab & Throw recharges");
		helper.assertTrue(res(p, "rip_cd") > 100f, "Rip & Hurl has its own cooldown");
		ThrownChunkEntity chunk = helper.getLevel().getEntity(id) instanceof ThrownChunkEntity c ? c : null;
		helper.assertTrue(chunk != null, "the boulder exists");
		for (int i = 0; i < 20 && res(p, "rip_id") != 0f; i++) {
			tick(p, 1);
		}
		helper.assertTrue(res(p, "rip_id") == 0f, "after rising it is hurled");
		helper.assertTrue(chunk.flying(), "and flies");
		helper.assertTrue(chunk.getDeltaMovement().horizontalDistance() > 3.0,
				"thrown hard: " + chunk.getDeltaMovement().horizontalDistance() + " blocks/tick");
		helper.succeed();
	}
}
