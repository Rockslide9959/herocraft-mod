package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.brainstorm.BrainstormKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Brainstorm revamp (docs/OMNITRIX_REVAMP_BRAINSTORM.md): the keys (ultimate on Shift+Z, Mega Bolt on Shift+C, Field Lift on
 * Hold X, nothing on H / N), Static charges and the Overload (and at 2 on an analysed creature), the Electric Storm's pulses and
 * finale, the Field Lift (hover, ends after 3 s, safe landing), the Force Field sheltering his pets, Ball Lightning's arcs, the sonic
 * headache, and that turning back leaves nothing. Mock players are not ticked: tests drive the kit themselves.
 */
public class OmnitrixBrainstormGameTests implements FabricGameTest {
	private static ServerPlayer brainstorm(GameTestHelper helper, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 1.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, pitch);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, BrainstormKit.ID), "selected Brainstorm");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Brainstorm");
		p.setOnGround(true);
		return p;
	}

	private static BrainstormKit kit() {
		return (BrainstormKit) Aliens.kit(BrainstormKit.ID);
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static Villager prey(GameTestHelper helper, double x, double y, double z, float health) {
		Villager m = EntityType.VILLAGER.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth(health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void near(GameTestHelper helper, float actual, float expected, String what) {
		helper.assertTrue(Math.abs(actual - expected) < 0.05f, what + " (expected " + expected + ", got " + actual + ")");
	}

	private static boolean stunned(LivingEntity e) {
		var s = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
		return s != null && s.getAmplifier() >= 9 && e.hasEffect(MobEffects.WEAKNESS);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormKeysUltOnShiftZ(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = brainstorm(helper, 0f);
		BrainstormKit k = kit();
		AlienMove ult = k.moveOn(AbilitySlot.SLOT_4, true, false);
		helper.assertTrue(ult != null && BrainstormKit.ELECTRIC_STORM.equals(ult.id()), "the ultimate is on Shift+Z");
		AlienMove mega = k.moveOn(AbilitySlot.SLOT_6, true, false);
		helper.assertTrue(mega != null && BrainstormKit.MEGA_BOLT.equals(mega.id()), "the Mega Bolt is on Shift+C");
		AlienMove lift = k.moveOn(AbilitySlot.SLOT_3, false, true);
		helper.assertTrue(lift != null && BrainstormKit.FIELD_LIFT.equals(lift.id()) && k.holdable(AbilitySlot.SLOT_3), "Field Lift is Hold X");
		helper.assertTrue(k.moves().stream().noneMatch(m -> m.slot() == AbilitySlot.SLOT_7 || m.slot() == AbilitySlot.SLOT_8), "nothing on H / N");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormStaticOverloadsOnTheThirdCharge(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = brainstorm(helper, 0f);
		Villager v = prey(helper, 3.5, 2, 5.5, 140f);
		BrainstormKit.zap(p, v, 5f);
		helper.assertTrue(BrainstormKit.charges(p, v) == 1, "one Static charge");
		BrainstormKit.zap(p, v, 5f);
		helper.assertTrue(BrainstormKit.charges(p, v) == 2, "two");
		near(helper, v.getHealth(), 130f, "5 + 5, no Overload yet");
		helper.assertFalse(stunned(v), "not stunned yet");
		BrainstormKit.zap(p, v, 5f);
		near(helper, v.getHealth(), 130f - 5f - BrainstormKit.OVERLOAD_DAMAGE, "the third Overloads: +6");
		helper.assertTrue(BrainstormKit.charges(p, v) == 0, "the charges are spent");
		helper.assertTrue(stunned(v), "and it is stunned");
		// storm pulses never charge
		BrainstormKit.zap(p, v, 1f, false);
		helper.assertTrue(BrainstormKit.charges(p, v) == 0, "a no-charge hit adds none");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormStormPulsesThenFinale(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = brainstorm(helper, 10f);
		Villager v = prey(helper, 3.5, 2, 6.5, 140f);
		helper.assertTrue(kit().electricStorm(p) && BrainstormKit.storming(p), "the Electric Storm gathers");
		helper.assertTrue(kit().keyActive(p, AbilitySlot.SLOT_4), "Z is lit while it rages");
		helper.assertTrue(kit().stormPulseNow(p) >= 1, "a pulse strikes the villager");
		near(helper, v.getHealth(), 140f - BrainstormKit.STORM_DAMAGE, "4 a strike");
		helper.assertTrue(BrainstormKit.charges(p, v) == 0, "the storm adds no Static");
		v.invulnerableTime = 0;
		helper.assertTrue(kit().stormFinaleNow(p) >= 1, "the finale");
		near(helper, v.getHealth(), 140f - BrainstormKit.STORM_DAMAGE - BrainstormKit.STORM_FINALE, "22 at the end");
		helper.assertFalse(BrainstormKit.storming(p), "the storm is spent");
		helper.assertFalse(kit().keyActive(p, AbilitySlot.SLOT_4), "Z is dark again");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormFieldLiftHoversAndLandsSoftly(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = brainstorm(helper, 0f);
		BrainstormKit k = kit();
		long now = p.level().getGameTime();
		helper.assertTrue(k.fieldLift(p) && k.lifting(p), "on the lift disc");
		helper.assertFalse(k.staticHop(p), "no hop while riding it");
		helper.assertFalse(k.allowDamage(p, helper.getLevel().damageSources().fall(), 6f), "no fall damage");
		k.tick(p, Omnitrix.state(p), now + 1);
		helper.assertTrue(k.lifting(p) && p.getDeltaMovement().y > -0.1, "it holds him up");
		k.tick(p, Omnitrix.state(p), now + BrainstormKit.LIFT_MAX + 1);
		helper.assertFalse(k.lifting(p), "it winks out after 3 s");
		helper.assertFalse(k.allowDamage(p, helper.getLevel().damageSources().fall(), 6f), "still no fall damage until he lands");
		helper.assertTrue(k.cooldownLeft(p, BrainstormKit.FIELD_LIFT) > 0, "the lift cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormForceFieldSheltersHisPets(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = brainstorm(helper, 0f);
		Wolf w = EntityType.WOLF.create(helper.getLevel());
		w.moveTo(p.getX() + 1.0, p.getY(), p.getZ(), 0f, 0f);
		w.setNoAi(true);
		w.tame(p);
		helper.getLevel().addFreshEntity(w);
		Villager v = prey(helper, 4.8, 2, 1.5, 40f);
		helper.assertTrue(kit().forceField(p), "the Force Field goes up");
		kit().fieldNow(p);
		var res = w.getEffect(MobEffects.DAMAGE_RESISTANCE);
		helper.assertTrue(res != null && res.getAmplifier() == 1, "his wolf inside gets Resistance II");
		helper.assertFalse(v.hasEffect(MobEffects.DAMAGE_RESISTANCE), "a stranger doesn't");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormSonicHeadacheBreaksHisFocus(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = brainstorm(helper, 0f);
		Villager v = prey(helper, 3.5, 2, 6.5, 40f);
		helper.assertTrue(kit().megaBolt(p) && kit().flag(p, BrainstormKit.F_CHARGE), "the Mega Bolt charges");
		float got = kit().incomingDamage(p, helper.getLevel().damageSources().sonicBoom(v), 4f);
		near(helper, got, 4f * BrainstormKit.SONIC_FACTOR, "sonic hurts 1.5x");
		helper.assertFalse(kit().flag(p, BrainstormKit.F_CHARGE), "and the charge is broken");
		near(helper, kit().incomingDamage(p, helper.getLevel().damageSources().magic(), 4f), 4f, "magic is not");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void brainstormBallLightningArcsAtWhatItPasses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = brainstorm(helper, 0f);
		Villager v = prey(helper, 5.5, 2, 3.0, 140f);
		helper.assertTrue(kit().ballLightning(p), "Ball Lightning");
		helper.assertTrue(kit().ballPulse(p, helper.getLevel()) >= 1, "the orb arcs at the villager within 3 of it");
		near(helper, v.getHealth(), 140f - BrainstormKit.BALL_ZAP, "4 an arc");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void brainstormRevertLeavesNothing(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = brainstorm(helper, 10f);
		BrainstormKit k = kit();
		Villager v = prey(helper, 3.5, 2, 6.5, 140f);
		helper.assertTrue(k.analyze(p) && BrainstormKit.marked(p, v), "analysed");
		BrainstormKit.zap(p, v, 2f);
		k.tick(p, Omnitrix.state(p), p.level().getGameTime());
		helper.assertTrue(k.electricStorm(p), "the storm");
		helper.assertTrue(k.fieldLift(p), "the lift");
		helper.assertTrue(BrainstormKit.leftovers(p) >= 3, "state is held: " + BrainstormKit.leftovers(p));
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
		helper.assertTrue(BrainstormKit.leftovers(p) == 0, "no storm, marks, charges or tick guard left: " + BrainstormKit.leftovers(p));
		OmnitrixState s = Omnitrix.state(p);
		helper.assertTrue(s.kitFlags == 0, "no kit flags left");
		helper.assertTrue(s.readyAt(BrainstormKit.ID + "." + BrainstormKit.LIFT_AT) <= 0, "no lift timer left");
		helper.succeed();
	}
}
