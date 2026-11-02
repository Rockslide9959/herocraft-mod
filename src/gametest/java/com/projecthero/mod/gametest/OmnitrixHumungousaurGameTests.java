package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.humungousaur.HumungousaurKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Humungousaur revamp (docs/OMNITRIX_REVAMP_HUMUNGOUSAUR.md, user: "Yes do all this stuff but dont add ultimate aliens
 * yet please"): the size alien -- +10% a size step, hold V / Shift+V to jump to full / base size, Grow refused under a roof, the
 * Earthshaker's three timed stomps (10, 10, 55) with him rooted, the Tail Club at full size, Heavy Hurl's size-scaled lift and
 * thrown impact, the Spike Burst's thorns and armour, the Brace, the Primal Roar's fleeing, the held Stampede, the Hungry Giant --
 * and that turning back leaves nothing behind. Mock players are not ticked: tests over time drive {@link Omnitrix#tick}.
 */
public class OmnitrixHumungousaurGameTests implements FabricGameTest {
	private static ServerPlayer as(GameTestHelper helper, float pitch) {
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
		helper.assertTrue(Omnitrix.select(p, HumungousaurKit.ID), "selected Humungousaur");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Humungousaur");
		p.setOnGround(true);
		return p;
	}

	private static HumungousaurKit kit() {
		return (HumungousaurKit) Aliens.kit(HumungousaurKit.ID);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static Villager prey(GameTestHelper helper, double x, double z, float health) {
		Villager m = EntityType.VILLAGER.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, 2, z));
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

	private static void clearCooldown(ServerPlayer p, String move) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(HumungousaurKit.ID + "." + move);
		Omnitrix.save(p, n);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void humungousaurSizeStepsAreTenPercentHoldsJumpAndARoofStopsGrowth(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, 0f);
		HumungousaurKit kit = kit();
		near(helper, HumungousaurKit.mult(p), 1.0f, "base size: x1.0");
		helper.assertTrue(kit.growFull(p), "hold V: straight to full size");
		helper.assertTrue(HumungousaurKit.stage(p) == 2, "full size in one go");
		near(helper, HumungousaurKit.mult(p), 1.2f, "+10% a step: x1.2 at full size");
		near(helper, p.getScale(), 3.0f, "x3.0");
		helper.assertTrue(kit.shrinkBase(p), "hold Shift+V: straight back to base size");
		helper.assertTrue(HumungousaurKit.stage(p) == 0, "base size again");
		near(helper, p.getScale(), 1.5f, "x1.5");
		// a stone ceiling just over his head: no room to grow (he would bury himself in it)
		helper.getLevel().setBlock(helper.absolutePos(new BlockPos(3, 5, 1)), Blocks.STONE.defaultBlockState(), 3);
		clearCooldown(p, HumungousaurKit.GROW);
		helper.assertFalse(kit.grow(p), "Grow is refused under a roof");
		helper.assertTrue(HumungousaurKit.stage(p) == 0, "still base size");
		helper.assertTrue(kit.cooldownLeft(p, HumungousaurKit.GROW) == 0, "a refused Grow costs no cooldown");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void humungousaurEarthshakerStompsThreeTimesWhileRooted(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, 0f);
		HumungousaurKit kit = kit();
		Villager v = prey(helper, 3.5, 3.0, 200f);
		pump(helper, p);
		helper.assertTrue(kit.earthshaker(p), "Shift+Z: the Earthshaker");
		helper.assertTrue(kit.flag(p, HumungousaurKit.F_QUAKE), "stomping");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1.0e-4, "rooted while he stomps");
		helper.assertFalse(kit.earthshaker(p), "a 30 s cooldown");
		helper.runAfterDelay(HumungousaurKit.QUAKE_STOMPS[0] + 2, () -> {
			helper.assertTrue(v.getHealth() <= 200f - HumungousaurKit.QUAKE_SMALL + 0.05f, "the first stomp hit (" + v.getHealth() + ")");
			helper.assertTrue(v.getHealth() > 200f - HumungousaurKit.QUAKE_SMALL - 1f, "only the first so far (" + v.getHealth() + ")");
		});
		helper.runAfterDelay(HumungousaurKit.QUAKE_TICKS + 3, () -> {
			float lost = 200f - v.getHealth();
			helper.assertTrue(Math.abs(lost - (2 * HumungousaurKit.QUAKE_SMALL + HumungousaurKit.QUAKE_BIG)) < 0.1f,
					"10 + 10 + 55 at base size (lost " + lost + ")");
			helper.assertFalse(kit.flag(p, HumungousaurKit.F_QUAKE), "the stomps are over");
			helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.01, "free to move again");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void humungousaurTailClubAtFullSizeHitsHarderAndKnocksDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, 0f);
		HumungousaurKit kit = kit();
		Villager v = prey(helper, 3.5, 3.5, 100f);
		helper.assertTrue(kit.tailClub(p), "Shift+G at base size");
		near(helper, v.getHealth(), 100f - HumungousaurKit.TAIL_DAMAGE, "13 at base size");
		v.setHealth(100f);
		v.invulnerableTime = 0;
		v.removeAllEffects();
		kit.setStage(p, 2);
		clearCooldown(p, HumungousaurKit.TAIL);
		helper.assertTrue(kit.tailClub(p), "Shift+G at full size: the club");
		near(helper, v.getHealth(), 100f - HumungousaurKit.CLUB_DAMAGE * 1.2f, "16 x1.2 with the club");
		helper.assertTrue(v.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "knocked down");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void humungousaurHeavyHurlLiftsByHisSizeAndTheThrowLandsHard(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, 25f);
		HumungousaurKit kit = kit();
		var golem = EntityType.IRON_GOLEM.create(helper.getLevel());
		var ravager = EntityType.RAVAGER.create(helper.getLevel());
		var wither = EntityType.WITHER.create(helper.getLevel());
		helper.assertFalse(HumungousaurKit.canLift(0, golem), "no iron golem at base size");
		helper.assertTrue(HumungousaurKit.canLift(1, golem), "an iron golem at x2.2");
		helper.assertFalse(HumungousaurKit.canLift(1, ravager), "no ravager at x2.2");
		helper.assertTrue(HumungousaurKit.canLift(2, ravager), "a ravager at x3.0");
		helper.assertFalse(HumungousaurKit.canLift(2, wither), "never a boss");
		Villager v = prey(helper, 3.5, 4.5, 100f);
		Villager other = prey(helper, 5.5, 6.5, 100f);
		helper.assertTrue(kit.hurl(p), "Z: Heavy Hurl");
		helper.assertTrue(HumungousaurKit.thrown(v), "the villager is in flight");
		helper.assertTrue(v.getDeltaMovement().y > 0.3, "thrown up and out");
		kit.thrownImpact(p, v, other, 1f);
		near(helper, v.getHealth(), 100f - HumungousaurKit.HURL_DAMAGE, "18 to the thrown");
		near(helper, other.getHealth(), 100f - HumungousaurKit.HURL_DAMAGE, "18 to what it hit");
		helper.assertFalse(HumungousaurKit.thrown(v), "landed");
		helper.assertFalse(kit.hurl(p), "an 8 s cooldown");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220)
	public void humungousaurSpikeBurstGivesThornsAndArmourFor8s(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, 0f);
		HumungousaurKit kit = kit();
		Villager v = prey(helper, 3.5, 3.0, 100f);
		float armour = p.getArmorValue();
		helper.assertTrue(kit.spikeBurst(p), "Shift+C: Spike Burst");
		near(helper, v.getHealth(), 100f - HumungousaurKit.SPIKE_DAMAGE, "14 around him");
		near(helper, p.getArmorValue(), armour + 3f, "+3 armour while the spikes are out");
		v.setHealth(100f);
		v.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().mobAttack(v), 2f);
		near(helper, v.getHealth(), 100f - HumungousaurKit.THORNS, "a melee attacker takes 3");
		pump(helper, p);
		helper.runAfterDelay(HumungousaurKit.SPIKE_TICKS + 4, () -> {
			helper.assertFalse(kit.flag(p, HumungousaurKit.F_SPIKES), "the spikes sink back after 8 s");
			near(helper, p.getArmorValue(), armour, "the armour goes with them");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void humungousaurBraceCutsFrontalDamageAndDropsWhenLetGo(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, 0f);
		HumungousaurKit kit = kit();
		Villager front = prey(helper, 3.5, 3.5, 100f);
		Villager behind = prey(helper, 3.5, -0.5, 100f);
		helper.assertTrue(kit.onUse(p, InteractionHand.MAIN_HAND) == InteractionResult.SUCCESS, "right-click (empty hand): Brace");
		helper.assertTrue(kit.braced(p), "braced");
		near(helper, kit.incomingDamage(p, helper.getLevel().damageSources().mobAttack(front), 10f), 4f, "a frontal hit: -60%");
		near(helper, kit.incomingDamage(p, helper.getLevel().damageSources().mobAttack(behind), 10f), 10f, "from behind: full");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0, "no knockback");
		pump(helper, p);
		helper.runAfterDelay(HumungousaurKit.BRACE_GRACE + 3, () -> {
			helper.assertFalse(kit.braced(p), "no more right-clicks: the Brace drops");
			helper.assertTrue(kit.cooldownLeft(p, HumungousaurKit.BRACE) > 0, "a 3 s recovery");
			helper.assertTrue(kit.onUse(p, InteractionHand.MAIN_HAND) == InteractionResult.PASS, "not again until it recovers");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void humungousaurRoarScattersTheWeakAndStampedeRunsWhileHeld(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, 0f);
		HumungousaurKit kit = kit();
		Villager weak = prey(helper, 3.0, 5.0, 25f);
		Villager tough = prey(helper, 4.0, 5.0, 100f);
		helper.assertTrue(kit.roar(p), "C: Primal Roar");
		near(helper, tough.getHealth(), 100f - HumungousaurKit.ROAR_DAMAGE, "12 in the cone");
		helper.assertTrue(HumungousaurKit.fleeing(p, weak), "left under 20 health: it flees");
		helper.assertFalse(HumungousaurKit.fleeing(p, tough), "a tough one stands");
		helper.assertTrue(kit.charge(p), "X: Stampede");
		helper.assertTrue(kit.charging(p, 10), "running");
		helper.assertFalse(kit.charging(p, HumungousaurKit.CHARGE_TICKS + 5), "a tap runs 1 s");
		kit.setFlag(p, HumungousaurKit.F_XHELD, true);
		helper.assertTrue(kit.charging(p, HumungousaurKit.CHARGE_TICKS + 5), "held: keeps running");
		helper.assertFalse(kit.charging(p, HumungousaurKit.CHARGE_MAX_TICKS), "up to 2.5 s");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(HumungousaurKit.holds(p), "no Humungousaur state left after turning back");
		helper.assertFalse(kit.flag(p, HumungousaurKit.F_CHARGE), "the run is over");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void humungousaurGetsHungryFasterAtFullSize(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = as(helper, 0f);
		HumungousaurKit kit = kit();
		kit.setStage(p, 2);
		pump(helper, p);
		helper.runAfterDelay(2, () -> {
			helper.runAfterDelay(1, () -> {
				float before = p.getFoodData().getExhaustionLevel();
				p.causeFoodExhaustion(1.0f);
				helper.runAfterDelay(2, () -> {
					float used = p.getFoodData().getExhaustionLevel() - before;
					helper.assertTrue(Math.abs(used - 2.0f) < 0.05f, "x2 hunger at full size (1.0 became " + used + ")");
					Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
					helper.succeed();
				});
			});
		});
	}
}
