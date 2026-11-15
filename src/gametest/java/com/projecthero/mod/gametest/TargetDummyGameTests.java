package com.projecthero.mod.gametest;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.event.boss.BossTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.targetdummy.DummyDamageKind;
import com.projecthero.mod.targetdummy.TargetDummyEntity;
import com.projecthero.mod.targetdummy.TargetDummyMod;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: the Target Dummy -- never dies under huge damage, records totals / DPS / hits, rapid hits all register (and
 * the hit-cooldown mode throttles them like a mob), Sneak + right-click returns the item with its name and armour,
 * explosions / fire / punches never remove or move it, armour changes the mitigated number, and the targeting rules
 * (powers' "hostile" picks it, bosses and grabs do not).
 */
public class TargetDummyGameTests implements FabricGameTest {
	private static final BlockPos AT = new BlockPos(3, 1, 3);

	private static void floor(GameTestHelper helper) {
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
			}
		}
	}

	private static TargetDummyEntity dummy(GameTestHelper helper) {
		floor(helper);
		return helper.spawn(TargetDummyMod.TARGET_DUMMY, AT);
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(AT);
		p.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 180.0f, 0.0f);
		return p;
	}

	private static boolean near(float a, float b) {
		return Math.abs(a - b) < 0.01f;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void neverDiesUnderHugeDamage(GameTestHelper helper) {
		TargetDummyEntity d = dummy(helper);
		ServerLevel level = helper.getLevel();
		d.hurt(level.damageSources().generic(), 1_000_000.0f);
		d.hurt(level.damageSources().magic(), Float.MAX_VALUE / 4.0f);
		d.setHealth(0.0f); // an "execute" effect
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(d.isAlive() && !d.isRemoved(), "the dummy is still standing");
			helper.assertTrue(near(d.getHealth(), d.getMaxHealth()), "at full health, got " + d.getHealth());
			helper.assertTrue(d.hits() == 3, "all three hits recorded (incl. the health set), got " + d.hits());
			helper.assertTrue(d.totalDamage() >= 1_000_000.0f, "the huge hit counts, got " + d.totalDamage());
			helper.assertTrue(d.lastKind() == DummyDamageKind.DIRECT, "the health set shows as true damage");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 240)
	public void recordsTotalsDpsAndHits(GameTestHelper helper) {
		TargetDummyEntity d = dummy(helper);
		ServerLevel level = helper.getLevel();
		d.hurt(level.damageSources().generic(), 4.0f);
		d.hurt(level.damageSources().generic(), 6.0f);
		d.hurt(level.damageSources().generic(), 10.0f);
		helper.assertTrue(d.hits() == 3 && near(d.totalDamage(), 20.0f) && near(d.biggestHit(), 10.0f), "3 hits, 20 total, 10 biggest: "
				+ d.hits() + " / " + d.totalDamage() + " / " + d.biggestHit());
		helper.assertTrue(near(d.dps(), 20.0f), "a combat under a second counts as one second: DPS 20, got " + d.dps());
		helper.assertTrue(d.inCombat(), "in combat");
		helper.runAfterDelay(40, () -> {
			d.hurt(level.damageSources().generic(), 10.0f);
			helper.assertTrue(d.hits() == 4 && near(d.totalDamage(), 30.0f), "the 4th hit adds up");
			// assert on the step: total / (ticks between first and last hit / 20)
			float expected = 30.0f / Math.max(1.0f, d.combatTicks() / 20.0f);
			helper.assertTrue(d.combatTicks() >= 39 && near(d.dps(), expected), "DPS over the combat: " + d.dps() + " vs " + expected
					+ " over " + d.combatTicks() + " ticks");
			helper.runAfterDelay(TargetDummyEntity.COMBAT_TIMEOUT + 5, () -> {
				helper.assertFalse(d.inCombat(), "the combat ends 5 s after the last hit");
				d.hurt(level.damageSources().generic(), 3.0f);
				helper.assertTrue(d.hits() == 1 && near(d.totalDamage(), 3.0f), "the next hit starts a fresh combat");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void rapidHitsAllRegister(GameTestHelper helper) {
		TargetDummyEntity d = dummy(helper);
		ServerPlayer p = player(helper);
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 6; i++) {
			d.hurt(level.damageSources().mobAttack(p), 2.0f); // a multi-hit power: six hits in one tick
		}
		helper.assertTrue(d.hits() == 6 && near(d.totalDamage(), 12.0f), "every hit in the same tick counts, got " + d.hits());
		helper.assertTrue(d.lastKind() == DummyDamageKind.POWER, "a player's mob-attack hit is a power");
		// the hit-cooldown mode: like a mob, only the first of six equal hits lands
		d.clearStats();
		d.setHitCooldown(true);
		for (int i = 0; i < 6; i++) {
			d.hurt(level.damageSources().mobAttack(p), 2.0f);
		}
		helper.assertTrue(d.hits() == 1, "with the hit cooldown on, equal hits inside it are ignored, got " + d.hits());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void meleeSwingIsNamedAndNeverMovesIt(GameTestHelper helper) {
		TargetDummyEntity d = dummy(helper);
		ServerPlayer p = player(helper);
		helper.runAfterDelay(2, () -> {
			Vec3 before = d.position();
			p.resetAttackStrengthTicker();
			d.markMelee(p); // what AttackEntityCallback does before the swing
			p.attack(d);
			helper.assertTrue(d.hits() == 1, "the punch landed");
			helper.assertTrue(d.lastLabel().getContents() instanceof TranslatableContents t && t.getKey().endsWith(".fist"),
					"an empty-handed swing is a Fist, got " + d.lastLabel().getString());
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(d.position().distanceTo(before) < 0.01, "no knockback: it never moves");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sneakRightClickReturnsTheItemWithNameAndArmour(GameTestHelper helper) {
		TargetDummyEntity d = dummy(helper);
		ServerPlayer p = player(helper);
		d.setCustomName(Component.literal("Bob"));
		// plain right-click with a helmet equips it
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_HELMET));
		d.interactAt(p, new Vec3(0, 1.8, 0), InteractionHand.MAIN_HAND);
		helper.assertTrue(d.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "right-clicking armour puts it on");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "and takes it from the hand");
		// a plain empty-handed right-click on a bare spot does not remove it (switches the cooldown)
		d.interactAt(p, new Vec3(0, 1.0, 0), InteractionHand.MAIN_HAND);
		helper.assertTrue(!d.isRemoved() && d.hitCooldown(), "a plain right-click never picks it up");
		// sneak + right-click picks it up
		p.setShiftKeyDown(true);
		InteractionResult r = d.interactAt(p, new Vec3(0, 1.0, 0), InteractionHand.MAIN_HAND);
		helper.assertTrue(r.consumesAction() && d.isRemoved(), "sneak + right-click picks it up");
		ItemStack got = p.getInventory().items.stream().filter(s -> s.is(TargetDummyMod.ITEM)).findFirst().orElse(ItemStack.EMPTY);
		helper.assertFalse(got.isEmpty(), "the dummy item is in the inventory");
		helper.assertTrue(got.has(DataComponents.CUSTOM_NAME) && got.getHoverName().getString().equals("Bob"), "it keeps its name");
		helper.assertTrue(got.has(DataComponents.CONTAINER), "and its armour");
		// placing it again gives everything back
		p.setShiftKeyDown(false);
		p.getInventory().clearContent();
		p.setItemInHand(InteractionHand.MAIN_HAND, got);
		BlockPos ground = helper.absolutePos(new BlockPos(5, 0, 5));
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
		InteractionResult placed = p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
		helper.assertTrue(placed.consumesAction(), "the item places a dummy");
		helper.runAfterDelay(1, () -> {
			TargetDummyEntity again = helper.getLevel().getEntitiesOfClass(TargetDummyEntity.class, new net.minecraft.world.phys.AABB(ground.above()).inflate(1))
					.stream().findFirst().orElse(null);
			helper.assertTrue(again != null, "a dummy stands on the clicked block");
			helper.assertTrue(again.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "wearing its helmet again");
			helper.assertTrue(again.hasCustomName() && again.getCustomName().getString().equals("Bob"), "with its name");
			helper.assertTrue(again.hitCooldown(), "and its cooldown setting");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void explosionsFireAndLavaNeverRemoveIt(GameTestHelper helper) {
		TargetDummyEntity d = dummy(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> {
			Vec3 before = d.position();
			level.explode(null, d.getX() + 1.0, d.getY() + 0.5, d.getZ(), 3.0f, Level.ExplosionInteraction.NONE);
			helper.assertTrue(d.hits() >= 1 && !d.isRemoved(), "the explosion is recorded, the dummy stays");
			d.hurt(level.damageSources().lava(), 4.0f);
			d.igniteForSeconds(4.0f);
			helper.runAfterDelay(50, () -> {
				helper.assertTrue(d.isAlive() && !d.isRemoved(), "fire and lava never remove it");
				helper.assertTrue(d.hits() >= 3, "burning ticks count too, got " + d.hits());
				helper.assertTrue(d.position().distanceTo(before) < 0.01, "the blast never moved it");
				helper.assertTrue(near(d.getHealth(), d.getMaxHealth()), "full health");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void armourChangesTheMitigatedDamage(GameTestHelper helper) {
		TargetDummyEntity d = dummy(helper);
		ServerPlayer p = player(helper);
		ServerLevel level = helper.getLevel();
		d.hurt(level.damageSources().playerAttack(p), 10.0f);
		helper.assertTrue(near(d.lastRaw(), 10.0f) && near(d.lastDealt(), 10.0f), "bare: raw = dealt = 10, got " + d.lastRaw() + " / " + d.lastDealt());
		d.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		d.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		d.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		d.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		helper.runAfterDelay(2, () -> { // armour attributes apply on the next tick
			helper.assertTrue(d.getArmorValue() == 20, "full diamond = 20 armour, got " + d.getArmorValue());
			d.hurt(level.damageSources().playerAttack(p), 10.0f);
			helper.assertTrue(near(d.lastRaw(), 10.0f), "raw stays 10");
			helper.assertTrue(d.lastDealt() < 5.0f && d.lastDealt() > 0.5f, "diamond armour takes most of it, got " + d.lastDealt());
			helper.assertTrue(d.totalRaw() > d.totalDamage() + 4.0f, "the totals show the difference");
			ItemStack chest = d.getItemBySlot(EquipmentSlot.CHEST);
			helper.assertTrue(chest.getDamageValue() == 0, "a test dummy never wears armour down");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powersTargetItBossesAndGrabsDoNot(GameTestHelper helper) {
		TargetDummyEntity d = dummy(helper);
		ServerPlayer p = player(helper);
		helper.assertTrue(HeroTargets.canHarm(p, d), "aimed powers can hit it");
		helper.assertTrue(HeroTargets.isHostile(p, d), "auto-targeting powers count it as hostile");
		helper.assertFalse(BossTargets.isVictim(null, d), "bosses never go after it");
		helper.assertFalse(AbilityHelpers.isValidGrabTarget(d, p), "grabs can't carry it off");
		helper.succeed();
	}
}
