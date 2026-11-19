package com.projecthero.mod.gametest;

import com.projecthero.mod.galactus.item.GalactusItems;
import com.projecthero.mod.galactus.item.PowerCosmicItem;
import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p07.ElectrokinesisHandlers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "if i use shift+R on symbiote i cant use regular R while Shift+R is on cooldown ... fix it for every power"):
 * a key's Shift move keeps its own cooldown entry, so it never blocks the plain move, and the plain move never blocks it.
 * Mutations keep the Shift entry under {@code power/ability#shift} ({@link ExperimentalPowers#SHIFT}).
 */
public class SplitCooldownPowersGameTests implements FabricGameTest {
	private static final String ELECTRO = "power_07_electrokinesis";
	private static final String CRYSTAL = "power_06_crystalkinesis";
	private static final String LIGHT = "power_15_invisibility_light_manipulation";
	private static final String SHOCK = "power_21_shockwave_manipulation";

	private static ServerPlayer hero(GameTestHelper helper, String key) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Power power = Powers.byKey(key);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		p.setPos(at.x, at.y, at.z);
		p.setYRot(0.0f);
		p.setXRot(0.0f);
		p.setYHeadRot(0.0f);
		return p;
	}

	private static Zombie zombieAhead(GameTestHelper helper, ServerPlayer p, int dist) {
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2 + dist));
		z.setNoAi(true);
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
		return z;
	}

	private static void tap(ServerPlayer p, int slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	private static boolean plainReady(ServerPlayer p, Power pw, Ability a) {
		return ExperimentalPowers.cooldownReady(p, pw, a);
	}

	private static boolean shiftReady(ServerPlayer p, Power pw, Ability a) {
		return ExperimentalPowers.cooldownReady(p, pw, a, ExperimentalPowers.SHIFT);
	}

	// ---------------- Electrokinesis G: Chain Lightning / Shift+G EMP ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void electroEmpDoesNotBlockChainLightning(GameTestHelper helper) {
		ServerPlayer p = hero(helper, ELECTRO);
		Power pw = Powers.byKey(ELECTRO);
		Ability g = pw.ability(AbilitySlot.SLOT_2);
		ExperimentalPowers.setResource(p, pw, "ecell", ElectrokinesisHandlers.MAX_CHARGE, ElectrokinesisHandlers.MAX_CHARGE);
		tap(p, 2, true); // EMP
		helper.assertFalse(shiftReady(p, pw, g), "Shift+G EMP should start G's Shift cooldown");
		helper.assertTrue(plainReady(p, pw, g), "Shift+G EMP must not put plain G (Chain Lightning) on cooldown");
		zombieAhead(helper, p, 5);
		tap(p, 2, false); // Chain Lightning
		helper.assertFalse(plainReady(p, pw, g), "plain G should still fire while the EMP cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void electroChainLightningDoesNotBlockEmp(GameTestHelper helper) {
		ServerPlayer p = hero(helper, ELECTRO);
		Power pw = Powers.byKey(ELECTRO);
		Ability g = pw.ability(AbilitySlot.SLOT_2);
		ExperimentalPowers.setResource(p, pw, "ecell", ElectrokinesisHandlers.MAX_CHARGE, ElectrokinesisHandlers.MAX_CHARGE);
		ExperimentalPowers.triggerCooldown(p, pw, g, 400); // Chain Lightning cooling
		tap(p, 2, true);
		helper.assertFalse(shiftReady(p, pw, g), "Shift+G EMP should fire while plain G cools down (router no longer gates it)");
		helper.succeed();
	}

	// ---------------- Crystalkinesis R: Crystal Shard / Shift+R Volley ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalVolleyDoesNotBlockShard(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		Power pw = Powers.byKey(CRYSTAL);
		Ability r = pw.ability(AbilitySlot.SLOT_1);
		ExperimentalPowers.triggerCooldown(p, pw, r, ExperimentalPowers.SHIFT, 400); // Volley cooling
		tap(p, 1, false);
		helper.assertFalse(plainReady(p, pw, r), "plain R Crystal Shard should fire while Shift+R Volley cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crystalShardDoesNotBlockVolley(GameTestHelper helper) {
		ServerPlayer p = hero(helper, CRYSTAL);
		Power pw = Powers.byKey(CRYSTAL);
		Ability r = pw.ability(AbilitySlot.SLOT_1);
		ExperimentalPowers.triggerCooldown(p, pw, r, 400); // Shard cooling
		tap(p, 1, true);
		helper.assertFalse(shiftReady(p, pw, r), "Shift+R Volley should fire while plain R cools down");
		helper.succeed();
	}

	// ---------------- Light G: Flash / Shift+G Radiant Lance ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void lightLanceAndFlashKeepSeparateCooldowns(GameTestHelper helper) {
		ServerPlayer p = hero(helper, LIGHT);
		Power pw = Powers.byKey(LIGHT);
		Ability g = pw.ability(AbilitySlot.SLOT_2);
		tap(p, 2, true); // Radiant Lance
		helper.assertFalse(shiftReady(p, pw, g), "Shift+G Radiant Lance should start G's Shift cooldown");
		helper.assertTrue(plainReady(p, pw, g), "Radiant Lance must not put Flash on cooldown");
		tap(p, 2, false); // Flash
		helper.assertFalse(plainReady(p, pw, g), "Flash should still fire while Radiant Lance cools down");
		helper.succeed();
	}

	// ---------------- Shockwave G: Ground Wave / Shift+G burst ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void shockwaveBurstAndGroundWaveKeepSeparateCooldowns(GameTestHelper helper) {
		ServerPlayer p = hero(helper, SHOCK);
		Power pw = Powers.byKey(SHOCK);
		Ability g = pw.ability(AbilitySlot.SLOT_2);
		ExperimentalPowers.triggerCooldown(p, pw, g, ExperimentalPowers.SHIFT, 200); // burst cooling
		tap(p, 2, false);
		helper.assertFalse(plainReady(p, pw, g), "plain G Ground Wave should fire while the Shift burst cools down");
		ServerPlayer q = hero(helper, SHOCK);
		ExperimentalPowers.triggerCooldown(q, pw, g, 200); // Ground Wave cooling
		tap(q, 2, true);
		helper.assertFalse(shiftReady(q, pw, g), "Shift+G burst should fire while Ground Wave cools down");
		helper.succeed();
	}

	// ---------------- Power Cosmic: right-click blast / Sneak+right-click shield ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerCosmicShieldDoesNotBlockBlast(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		ItemStack s = new ItemStack(GalactusItems.POWER_COSMIC);
		p.setItemInHand(InteractionHand.MAIN_HAND, s);
		long now = helper.getLevel().getGameTime();
		p.setShiftKeyDown(true);
		s.getItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(PowerCosmicItem.readyAt(s, PowerCosmicItem.SHIELD_READY) > now, "the shield should start its own cooldown");
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "the shield should apply");
		p.setShiftKeyDown(false);
		InteractionResultHolder<ItemStack> blast = s.getItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertTrue(blast.getResult().consumesAction(), "the blast should fire while the shield cools down");
		helper.assertTrue(PowerCosmicItem.readyAt(s, PowerCosmicItem.BLAST_READY) > now, "the blast should start its own cooldown");
		p.setShiftKeyDown(true);
		InteractionResultHolder<ItemStack> again = s.getItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND);
		helper.assertFalse(again.getResult().consumesAction(), "a second shield straight away is still on its own cooldown");
		helper.succeed();
	}
}
