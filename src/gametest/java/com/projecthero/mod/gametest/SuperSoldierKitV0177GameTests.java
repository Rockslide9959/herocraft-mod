package com.projecthero.mod.gametest;

import com.projecthero.mod.supersoldier.SuperSoldier;
import com.projecthero.mod.supersoldier.SuperSoldierAbilities;
import com.projecthero.mod.supersoldier.SuperSoldierAbilityManager;
import com.projecthero.mod.supersoldier.SuperSoldierConfig;
import com.projecthero.mod.supersoldier.SuperSoldierSerum;
import com.projecthero.mod.supersoldier.data.SuperSoldierState;
import com.projecthero.mod.supersoldier.item.SuperSoldierItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Super Soldier kit pass (the user's list): the Captain America suit at diamond durability; passive Regeneration I and
 * Speed I (beside Resistance I), never cutting a stronger one; the Combo Strike at Black Panther's 5 / 7 / 10, the Flying Kick 12, the
 * Judo Takedown 15; X the Dash (follows the pitch, stops at a wall, no fall damage); Shift+X the Shoulder Bash (10) / Shield Bash (14)
 * with invulnerability and knockback along the charge; no fall damage while blocking with a shield and looking down (but looking
 * ahead it hurts); V the Spinning Roundhouse (11 in a frontal arc); Assemble's level II buffs for 50 s on him and a squadmate (not on
 * an outsider) and its 100 s cooldown. Mock players are not moved by their velocity, so the charges are checked through the velocity
 * they are given and the swept hit box.
 */
public class SuperSoldierKitV0177GameTests implements FabricGameTest {
	private static ServerPlayer soldier(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		helper.assertTrue(SuperSoldierSerum.drink(p, true, 0.99f) == SuperSoldierSerum.Outcome.GRANTED, "the refined serum always takes");
		return p;
	}

	private static Zombie zombie(GameTestHelper helper, ServerPlayer p, double side, double forward) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		z.moveTo(p.getX() + side, p.getY(), p.getZ() + forward, 180.0f, 0.0f);
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		z.setHealth(200.0f);
		z.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		z.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2000, 0, false, false));
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	private static float took(Zombie z) {
		return 200.0f - z.getHealth();
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 0.05;
	}

	// ---------------------------------------------------------------- the suit, the passives, the numbers

	@GameTest(template = EMPTY_STRUCTURE)
	public void captainAmericaSuitHasDiamondDurability(GameTestHelper helper) {
		helper.assertTrue(SuperSoldierConfig.SUIT_DURABILITY_MULTIPLIER == 33, "diamond multiplier 33");
		Item[] cap = { SuperSoldierItems.HELMET, SuperSoldierItems.CHESTPLATE, SuperSoldierItems.LEGGINGS, SuperSoldierItems.BOOTS };
		Item[] diamond = { Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS };
		ArmorItem.Type[] types = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
		for (int i = 0; i < 4; i++) {
			int max = new ItemStack(cap[i]).getMaxDamage();
			helper.assertTrue(max == new ItemStack(diamond[i]).getMaxDamage() && max == types[i].getDurability(33),
					types[i].getName() + ": " + max + " = diamond's " + new ItemStack(diamond[i]).getMaxDamage());
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void passiveRegenerationAndSpeedOneLikeResistance(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 2.5, 2.0, 2.5);
		SuperSoldierAbilityManager.serverTick(p);
		for (Holder<MobEffect> e : java.util.List.of(MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED)) {
			MobEffectInstance i = p.getEffect(e);
			helper.assertTrue(i != null && i.getAmplifier() == 0 && i.isInfiniteDuration() && i.isAmbient(),
					e.getRegisteredName() + " I, infinite and ambient: " + i);
		}
		helper.assertTrue(p.getEffect(MobEffects.DAMAGE_RESISTANCE) != null, "Resistance I beside them");
		// cleared (milk): back next tick
		p.removeAllEffects();
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertTrue(p.hasEffect(MobEffects.REGENERATION) && p.hasEffect(MobEffects.MOVEMENT_SPEED), "back after being cleared");
		// a stronger potion is left alone
		p.removeAllEffects();
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertTrue(p.getEffect(MobEffects.REGENERATION).getAmplifier() == 2, "Regeneration III potion kept");
		helper.assertTrue(p.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1, "Speed II potion kept");
		// power lost: ours go, and are not given back
		p.removeAllEffects();
		SuperSoldierAbilityManager.serverTick(p);
		SuperSoldier.revoke(p);
		helper.assertFalse(p.hasEffect(MobEffects.REGENERATION) || p.hasEffect(MobEffects.MOVEMENT_SPEED), "revoked: both removed");
		SuperSoldierAbilityManager.serverTick(p);
		helper.assertFalse(p.hasEffect(MobEffects.REGENERATION) || p.hasEffect(MobEffects.MOVEMENT_SPEED), "and not given back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void comboIsFiveSevenTenKickTwelveTakedownFifteen(GameTestHelper helper) {
		helper.assertTrue(SuperSoldierAbilities.comboDamage(0) == 5.0f && SuperSoldierAbilities.comboDamage(1) == 7.0f
				&& SuperSoldierAbilities.comboDamage(2) == 10.0f, "Black Panther's 5 / 7 / 10");
		helper.assertTrue(SuperSoldierConfig.KICK_DAMAGE == 12.0f, "Flying Kick 12");
		helper.assertTrue(SuperSoldierConfig.TAKEDOWN_DAMAGE == 15.0f, "Judo Takedown 15");
		ServerPlayer p = soldier(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, p, 0.0, 2.2);
		helper.onEachTick(() -> SuperSoldierAbilityManager.serverTick(p));
		helper.assertTrue(SuperSoldierAbilities.comboStrike(p), "combo");
		helper.assertTrue(near(took(z), 5.0), "the first punch is 5, took " + took(z));
		ServerPlayer k = soldier(helper, 6.5, 2.0, 1.5);
		Zombie kz = zombie(helper, k, 0.0, 2.0);
		helper.assertTrue(SuperSoldierAbilities.flyingKick(k), "kick");
		helper.assertTrue(near(took(kz), 12.0), "the kick is 12, took " + took(kz));
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(near(took(z), 22.0), "5 + 7 + 10 = 22, took " + took(z)))
				.thenSucceed();
	}

	// ---------------------------------------------------------------- X: the Dash

	@GameTest(template = EMPTY_STRUCTURE)
	public void dashFollowsThePitch(GameTestHelper helper) {
		ServerPlayer up = soldier(helper, 2.5, 2.0, 1.5);
		up.setYRot(20.0f);
		up.setXRot(-35.0f); // looking up and to the side
		Vec3 look = up.getLookAngle();
		long now = helper.getLevel().getGameTime();
		helper.assertTrue(SuperSoldierAbilities.dash(up), "dashed");
		Vec3 v = up.getDeltaMovement();
		helper.assertTrue(near(v.length(), SuperSoldierConfig.DASH_SPEED), "at dash speed, " + v.length());
		helper.assertTrue(v.normalize().dot(look) > 0.999, "along the look, pitch included: " + v + " vs " + look);
		helper.assertTrue(v.y > 0.5, "upward, vy " + v.y);
		helper.assertTrue(SuperSoldierAbilities.charging(up), "still carrying him");
		helper.assertTrue(SuperSoldier.cooldownRemaining(up, SuperSoldierAbilities.DASH) == SuperSoldierConfig.DASH_COOLDOWN, "3 s cooldown");
		helper.assertTrue(SuperSoldier.state(up).noFallUntil >= now + SuperSoldierConfig.DASH_NO_FALL_TICKS, "no fall damage from it");
		helper.assertTrue(SuperSoldier.state(up).animId == SuperSoldierState.ANIM_DASH, "the dash clip");
		helper.assertTrue(SuperSoldierConfig.DASH_SPEED * SuperSoldierConfig.DASH_TICKS >= 7.5
				&& SuperSoldierConfig.DASH_SPEED * SuperSoldierConfig.DASH_TICKS <= 8.5, "about 8 blocks");
		// in the air, looking down: down
		ServerPlayer down = soldier(helper, 5.5, 5.0, 1.5);
		down.setXRot(45.0f);
		helper.assertTrue(SuperSoldierAbilities.dash(down), "dashed down");
		helper.assertTrue(down.getDeltaMovement().y < -0.9, "downward, vy " + down.getDeltaMovement().y);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dashStopsAtAWall(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 2.5, 2.0, 2.5);
		for (int y = 1; y <= 4; y++) {
			for (int x = 1; x <= 4; x++) {
				helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
			}
		}
		helper.assertTrue(SuperSoldierAbilities.dash(p), "dashed into the wall");
		helper.assertFalse(SuperSoldierAbilities.charging(p), "stopped at once");
		helper.assertTrue(p.getDeltaMovement().horizontalDistance() < 0.6, "no speed into the wall, " + p.getDeltaMovement());
		helper.succeed();
	}

	// ---------------------------------------------------------------- Shift+X: the Shoulder Bash / Shield Bash

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shoulderBashTenShieldBashFourteenInvulnerableAndKnockedAlong(GameTestHelper helper) {
		helper.runAfterDelay(70, () -> { // past the spawn invulnerability, so only the bash can protect him
			ServerPlayer bare = soldier(helper, 1.5, 2.0, 1.5);
			Zombie a = zombie(helper, bare, 0.0, 1.6);
			bare.setHealth(bare.getMaxHealth());
			bare.setShiftKeyDown(true);
			com.projecthero.mod.hero.AbilityRouter.handleInput(bare, 3, true);
			bare.setShiftKeyDown(false);
			helper.assertTrue(SuperSoldierAbilities.charging(bare), "Shift+X charges");
			helper.assertTrue(near(took(a), SuperSoldierConfig.SHOULDER_BASH_DAMAGE), "Shoulder Bash 10, took " + took(a));
			helper.assertTrue(SuperSoldierConfig.SHOULDER_BASH_DAMAGE == 10.0f && SuperSoldierConfig.SHIELD_BASH_DAMAGE == 14.0f, "10 / 14");
			helper.assertTrue(a.getDeltaMovement().z > 0.2, "knocked along the charge (+Z), v " + a.getDeltaMovement());
			helper.assertTrue(SuperSoldier.state(bare).animId == SuperSoldierState.ANIM_SHOULDER_BASH, "the shoulder clip");
			helper.assertTrue(SuperSoldierAbilities.invulnerable(bare), "invulnerable while charging");
			bare.hurt(bare.damageSources().generic(), 10.0f);
			helper.assertTrue(bare.getHealth() == bare.getMaxHealth(), "nothing hurts him mid-bash, health " + bare.getHealth());
			long iframes = SuperSoldier.state(bare).iframeUntil - helper.getLevel().getGameTime();
			helper.assertTrue(iframes == SuperSoldierConfig.BASH_TICKS + SuperSoldierConfig.BASH_IFRAME_EXTRA, "the charge + 2 ticks, " + iframes);
			// hit once: the next tick of the same charge does not hit it again
			SuperSoldierAbilityManager.serverTick(bare);
			helper.assertTrue(near(took(a), SuperSoldierConfig.SHOULDER_BASH_DAMAGE), "hit once per bash, took " + took(a));
			int cd = SuperSoldier.cooldownRemaining(bare, SuperSoldierAbilities.SHOULDER_BASH);
			helper.assertTrue(cd > 0 && cd == SuperSoldier.cooldownRemaining(bare, SuperSoldierAbilities.SHIELD_BASH), "one shared cooldown");

			ServerPlayer shielded = soldier(helper, 5.5, 2.0, 1.5);
			shielded.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
			Zombie b = zombie(helper, shielded, 0.0, 1.6);
			helper.assertTrue(SuperSoldierAbilities.bash(shielded), "Shield Bash");
			helper.assertTrue(near(took(b), SuperSoldierConfig.SHIELD_BASH_DAMAGE), "Shield Bash 14, took " + took(b));
			helper.assertTrue(b.getDeltaMovement().z > 0.2, "knocked along, v " + b.getDeltaMovement());
			SuperSoldierState s = SuperSoldier.state(shielded);
			helper.assertTrue(s.animId == SuperSoldierState.ANIM_SHIELD_BASH && s.animOff, "the shield clip, led by the off-hand shield arm");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- landing on the shield

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void blockingWithTheShieldLookingDownCancelsFallDamage(GameTestHelper helper) {
		helper.runAfterDelay(70, () -> {
			ServerPlayer p = soldier(helper, 2.5, 2.0, 2.5);
			p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(SuperSoldierItems.ADAMANTIUM_SHIELD));
			p.startUsingItem(InteractionHand.OFF_HAND);
			helper.assertTrue(p.isUsingItem(), "blocking");
			p.setXRot(75.0f);
			p.setHealth(p.getMaxHealth());
			p.hurt(p.damageSources().fall(), 10.0f);
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "blocking + looking down: no fall damage, health " + p.getHealth());
			// looking ahead: it hurts
			p.setXRot(0.0f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().fall(), 10.0f);
			helper.assertTrue(p.getHealth() < p.getMaxHealth(), "blocking but looking ahead: hurt, health " + p.getHealth());
			// looking down without blocking: it hurts too
			p.setHealth(p.getMaxHealth());
			p.stopUsingItem();
			p.setXRot(80.0f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().fall(), 10.0f);
			helper.assertTrue(p.getHealth() < p.getMaxHealth(), "not blocking: hurt, health " + p.getHealth());
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- V: the Spinning Roundhouse

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void roundhouseHitsTheArcInFrontForEleven(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 3.5, 2.0, 3.5);
		Zombie front = zombie(helper, p, 0.0, 2.0);
		Zombie side = zombie(helper, p, 1.8, 1.0); // ~60 degrees off his facing: inside the 150-degree arc
		Zombie behind = zombie(helper, p, 0.0, -2.0);
		helper.onEachTick(() -> SuperSoldierAbilityManager.serverTick(p));
		com.projecthero.mod.hero.AbilityRouter.handleInput(p, 5, true);
		helper.assertTrue(SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.ROUNDHOUSE) == SuperSoldierConfig.ROUNDHOUSE_COOLDOWN,
				"V is the roundhouse, 6 s cooldown");
		helper.assertTrue(SuperSoldier.state(p).animId == SuperSoldierState.ANIM_ROUNDHOUSE, "the roundhouse clip");
		helper.assertTrue(took(front) == 0.0f, "the kick lands after the chamber, not at once");
		helper.assertTrue(SuperSoldierConfig.ROUNDHOUSE_DAMAGE >= 10.0f && SuperSoldierConfig.ROUNDHOUSE_DAMAGE <= 12.0f, "10-12 damage");
		helper.startSequence()
				.thenWaitUntil(() -> helper.assertTrue(took(front) > 0.0f, "landed"))
				.thenExecute(() -> {
					helper.assertTrue(near(took(front), SuperSoldierConfig.ROUNDHOUSE_DAMAGE), "11 in front, took " + took(front));
					helper.assertTrue(near(took(side), SuperSoldierConfig.ROUNDHOUSE_DAMAGE), "11 to the side, took " + took(side));
					helper.assertTrue(took(behind) == 0.0f, "nothing behind him, took " + took(behind));
					MobEffectInstance slow = front.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
					helper.assertTrue(slow != null && slow.getAmplifier() == 1, "dazed (Slowness II)");
				})
				.thenSucceed();
	}

	// ---------------------------------------------------------------- Shift+V: Assemble

	@GameTest(template = EMPTY_STRUCTURE)
	public void assembleGivesLevelTwoBuffsToHimAndSquadmatesForFiftySeconds(GameTestHelper helper) {
		ServerPlayer p = soldier(helper, 2.5, 2.0, 2.5);
		SuperSoldierAbilityManager.serverTick(p); // the passives on first: Assemble's II must sit over them
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		mate.setGameMode(GameType.SURVIVAL);
		mate.moveTo(p.getX(), p.getY(), p.getZ() + 5.0, 180.0f, 0.0f);
		ServerPlayer outsider = helper.makeMockServerPlayerInLevel();
		outsider.setGameMode(GameType.SURVIVAL);
		outsider.moveTo(p.getX() + 3.0, p.getY(), p.getZ(), 0.0f, 0.0f);
		Zombie z = zombie(helper, p, 0.0, 2.0);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("CapTestSquad" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			p.setShiftKeyDown(true);
			com.projecthero.mod.hero.AbilityRouter.handleInput(p, 5, true);
			p.setShiftKeyDown(false);
			helper.assertTrue(SuperSoldier.assembleActive(p), "Assemble running");
			helper.assertTrue(SuperSoldier.cooldownRemaining(p, SuperSoldierAbilities.ASSEMBLE) == 2000, "100 s cooldown from the call");
			helper.assertTrue(SuperSoldierConfig.ASSEMBLE_DURATION == 1000, "50 s");
			java.util.List<Holder<MobEffect>> buffs = java.util.List.of(MobEffects.DAMAGE_BOOST, MobEffects.DAMAGE_RESISTANCE,
					MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED);
			for (ServerPlayer who : java.util.List.of(p, mate)) {
				for (Holder<MobEffect> e : buffs) {
					MobEffectInstance i = who.getEffect(e);
					helper.assertTrue(i != null && i.getAmplifier() == 1 && i.getDuration() >= 990 && i.getDuration() <= 1000,
							(who == p ? "he" : "the squadmate") + " has " + e.getRegisteredName() + " II for 50 s: " + i);
				}
			}
			for (Holder<MobEffect> e : buffs) {
				helper.assertFalse(outsider.hasEffect(e), "an outsider gets nothing: " + e.getRegisteredName());
			}
			helper.assertTrue(z.getHealth() == 200.0f && !z.hasEffect(MobEffects.GLOWING) && !z.hasEffect(MobEffects.WEAKNESS),
					"no damage, marks or debuffs on enemies any more");
			// the passives never cut Assemble's stronger effects
			SuperSoldierAbilityManager.serverTick(p);
			helper.assertTrue(p.getEffect(MobEffects.REGENERATION).getAmplifier() == 1 && p.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1
					&& p.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() == 1, "still level II after the passive tick");
			// cooling down: a second press does nothing
			SuperSoldierState s = SuperSoldier.state(p).copy();
			s.busyUntil = 0L;
			p.setAttached(com.projecthero.mod.attachment.ModAttachments.SUPER_SOLDIER_STATE, s);
			long ready = SuperSoldier.state(p).abilityReadyAt.get(SuperSoldierAbilities.ASSEMBLE);
			helper.assertFalse(SuperSoldierAbilities.assemble(p), "no second Assemble while cooling down");
			helper.assertTrue(SuperSoldier.state(p).abilityReadyAt.get(SuperSoldierAbilities.ASSEMBLE) == ready, "the cooldown is not restarted");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}
}
