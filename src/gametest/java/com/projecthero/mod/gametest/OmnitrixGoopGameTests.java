package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.goop.GoopKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Goop revamp (docs/OMNITRIX_REVAMP_GOOP.md): the kit's shape and numbers, the held Squeeze (climbs, cooldown from the
 * release, blows land in full while squeezed), Acid Spray's pulses, Acid Tide (the surge, untouchable, then the lake), Projector
 * Fling (pours, untouchable, reforms), Dissolve (soft blocks only, the terrain rule, the block comes back), Engulf + spit out,
 * Slime Bounce's rebounds, Acid Skin, and that turning back leaves nothing. Mock players are not ticked: tests over time drive
 * {@link Omnitrix#tick} themselves.
 */
public class OmnitrixGoopGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z, float pitch) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, pitch);
		p.setYHeadRot(0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	/** A recalibrated wearer, transformed into Goop. */
	private static ServerPlayer goop(GameTestHelper helper, float pitch) {
		floor(helper);
		ServerPlayer p = mock(helper, 3.5, 2, 1.5, pitch);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, GoopKit.ID), "selected Goop");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Goop");
		p.setOnGround(true);
		return p;
	}

	private static GoopKit kit() {
		return (GoopKit) Aliens.kit(GoopKit.ID);
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 9; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	/** A no-AI villager to hit (not a hostile, so no other test's area sweep lands on it). */
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

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopRevampKitShapeAndNumbers(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 0f);
		GoopKit k = kit();
		helper.assertTrue(GoopKit.ACID_TIDE.equals(k.moveOn(AbilitySlot.SLOT_4, true, false).id()), "Shift+Z is the ultimate, Acid Tide");
		helper.assertTrue(GoopKit.SQUEEZE.equals(k.moveOn(AbilitySlot.SLOT_3, true, false).id()), "Shift+X is Squeeze (movement)");
		helper.assertTrue(GoopKit.BOUNCE.equals(k.moveOn(AbilitySlot.SLOT_3, false, false).id()), "X is Slime Bounce (movement)");
		helper.assertTrue(GoopKit.FLING.equals(k.moveOn(AbilitySlot.SLOT_5, true, false).id()), "Shift+V is Projector Fling");
		helper.assertTrue(k.moves().stream().noneMatch(m -> m.onSlot() && (m.slot() == AbilitySlot.SLOT_7 || m.slot() == AbilitySlot.SLOT_8)),
				"no H or N moves");
		helper.assertTrue(k.moves().stream().filter(AlienMove::onSlot).count() == 12, "12 key moves");
		helper.assertTrue(GoopKit.SPIT_DAMAGE >= 5 && GoopKit.SPIT_DAMAGE <= 8, "the spit is a quick hit");
		helper.assertTrue(GoopKit.WHIP_DAMAGE >= 12 && GoopKit.SLAM_DAMAGE <= 17, "normal moves 12-17");
		helper.assertTrue(GoopKit.CANNON_DAMAGE >= 17 && GoopKit.CANNON_DAMAGE <= 20, "the cannon is a big hit");
		helper.assertTrue(GoopKit.TIDE_DAMAGE >= 40 && GoopKit.TIDE_DAMAGE <= 70, "the ultimate sits in 40-70");
		near(helper, p.getMaxHealth(), 26f, "26 health");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopSqueezeIsHeldClimbsAndTakesBlowsInFull(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 0f);
		GoopKit k = kit();
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		var src = helper.getLevel().damageSources();
		near(helper, k.incomingDamage(p, src.mobAttack(z), 6f), 3f, "a blow lands at half normally");
		k.press(p, AbilitySlot.SLOT_3, true);
		helper.assertTrue(k.flag(p, GoopKit.F_SQUEEZE), "Shift+X down: squeezed");
		helper.assertTrue(k.climbs(p), "squeezed, he climbs walls");
		p.refreshDimensions();
		helper.assertTrue(p.getBbHeight() < 0.8f, "small enough for a one-block gap (" + p.getBbHeight() + ")");
		near(helper, k.incomingDamage(p, src.mobAttack(z), 6f), 6f, "squeezed thin, the blow lands in full");
		k.release(p, AbilitySlot.SLOT_3, true, 30);
		helper.assertFalse(k.flag(p, GoopKit.F_SQUEEZE), "letting go pops him back");
		helper.assertFalse(k.climbs(p), "no climbing at full size");
		near(helper, p.getScale(), 1f, "full size");
		helper.assertTrue(k.cooldownLeft(p, GoopKit.SQUEEZE) == GoopKit.SQUEEZE_COOLDOWN, "the 3 s cooldown runs from the release");
		helper.assertFalse(k.squeeze(p), "and it is refused until then");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void goopAcidSprayPulsesForASecondAndAHalf(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 0f);
		Villager v = prey(helper, 3.5, 2, 4.5, 100f);
		pump(helper, p);
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(kit().acidSpray(p), "Acid Spray");
			helper.assertTrue(kit().flag(p, GoopKit.F_SPRAY), "it keeps spraying");
		});
		helper.runAtTickTime(2 + GoopKit.SPRAY_TICKS + 4, () -> {
			helper.assertFalse(kit().flag(p, GoopKit.F_SPRAY), "it stops after 1.5 s");
			near(helper, v.getHealth(), 100f - 4 * GoopKit.SPRAY_DAMAGE, "four licks of 3.5 (14)");
			helper.assertTrue(v.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "weakened");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void goopAcidTideSurgesUntouchableThenLeavesALake(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 0f);
		Villager v = prey(helper, 3.5, 2, 3.0, 200f);
		pump(helper, p);
		helper.runAtTickTime(2, () -> {
			k(helper, p);
		});
		helper.runAtTickTime(2 + GoopKit.TIDE_TICKS + 3, () -> {
			helper.assertFalse(kit().flag(p, GoopKit.F_TIDE), "the wave breaks after half a second");
			helper.assertTrue(v.getHealth() <= 200f - GoopKit.TIDE_DAMAGE, "the wave hit for 45 (" + v.getHealth() + ")");
			helper.assertTrue(kit().pulsePuddlesNow(p) >= 0 && GoopKit.leftovers(p) >= 1, "an acid lake where it broke");
			near(helper, p.getScale(), 1f, "he stands back up");
			helper.assertTrue(kit().cooldownLeft(p, GoopKit.ACID_TIDE) > 1000, "the 60 s ultimate cooldown");
			helper.succeed();
		});
	}

	private static void k(GameTestHelper helper, ServerPlayer p) {
		helper.assertTrue(kit().acidTide(p), "Acid Tide");
		helper.assertTrue(kit().flag(p, GoopKit.F_TIDE), "he is a wave");
		helper.assertFalse(kit().allowDamage(p, helper.getLevel().damageSources().magic(), 5f), "a wave cannot be hit");
		helper.assertFalse(kit().acidSpit(p), "no other move mid-surge");
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void goopProjectorFlingPoursAndReforms(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 30f);
		Vec3 start = p.position();
		pump(helper, p);
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(kit().projectorFling(p), "Projector Fling");
			helper.assertTrue(GoopKit.pouring(p), "he pours after it");
			helper.assertFalse(kit().allowDamage(p, helper.getLevel().damageSources().magic(), 5f), "a puddle on the move cannot be hit");
			helper.assertTrue(p.getScale() < 0.6f, "a puddle");
		});
		helper.runAtTickTime(16, () -> {
			helper.assertFalse(GoopKit.pouring(p), "the pour is over");
			helper.assertTrue(p.position().distanceTo(start) > 2.0, "he reformed where it landed (" + p.position().distanceTo(start) + ")");
			near(helper, p.getScale(), 1f, "full size again");
			helper.assertTrue(GoopKit.leftovers(p) == 0, "no projector left lying about");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void goopDissolvesSoftBlocksNotObsidianAndTheyComeBack(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 0f);
		BlockPos stone = new BlockPos(3, 2, 3), obsidian = new BlockPos(4, 2, 3);
		helper.getLevel().setBlock(helper.absolutePos(stone), Blocks.STONE.defaultBlockState(), 3);
		helper.getLevel().setBlock(helper.absolutePos(obsidian), Blocks.OBSIDIAN.defaultBlockState(), 3);
		boolean terrain = HeroConfig.get().abilityTerrainDamage;
		try {
			HeroConfig.get().abilityTerrainDamage = false;
			helper.assertFalse(kit().dissolve(p, helper.absolutePos(stone)), "with terrain damage off, nothing dissolves");
			HeroConfig.get().abilityTerrainDamage = true;
			helper.assertFalse(kit().dissolve(p, helper.absolutePos(obsidian)), "obsidian is too hard");
			helper.assertTrue(kit().dissolve(p, helper.absolutePos(stone)), "Dissolve eats the stone");
		} finally {
			HeroConfig.get().abilityTerrainDamage = terrain;
		}
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(stone)).isAir(), "the stone is gone");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(obsidian)).is(Blocks.OBSIDIAN), "the obsidian stands");
		helper.runAtTickTime(GoopKit.DISSOLVE_TICKS + 20, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(stone)).is(Blocks.STONE), "the stone comes back after 10 s");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopEngulfsThenSpitsOutOnShiftG(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 0f);
		Villager v = prey(helper, 3.5, 2, 3.5, 100f);
		helper.assertTrue(kit().engulf(p), "Engulf");
		helper.assertTrue(GoopKit.engulfing(p), "the villager is inside him");
		kit().press(p, AbilitySlot.SLOT_2, true);
		helper.assertFalse(GoopKit.engulfing(p), "Shift+G again spits it out");
		near(helper, v.getHealth(), 100f - GoopKit.SPIT_OUT_DAMAGE, "spat out for 6");
		helper.assertTrue(v.getDeltaMovement().horizontalDistance() > 1.0, "thrown");
		helper.assertFalse(kit().flag(p, GoopKit.F_ENGULF), "the engulf flag is off");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void goopSlimeBounceReboundsTwice(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 0f);
		pump(helper, p);
		helper.runAtTickTime(2, () -> {
			helper.assertTrue(kit().slimeBounce(p), "Slime Bounce");
			helper.assertTrue(GoopKit.bouncesLeft(p) == GoopKit.BOUNCES, "two rebounds to come");
		});
		// a mock player never leaves the ground, so every few ticks counts as a landing
		helper.runAtTickTime(8, () -> helper.assertTrue(GoopKit.bouncesLeft(p) == 1, "the first rebound (" + GoopKit.bouncesLeft(p) + ")"));
		helper.runAtTickTime(20, () -> {
			helper.assertTrue(GoopKit.bouncesLeft(p) == -1, "the bouncing is over after two");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopAcidSkinBurnsWhatHitsHim(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 0f);
		Villager z = prey(helper, 3.5, 2, 2.5, 40f);
		kit().incomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 4f);
		near(helper, z.getHealth(), 40f - GoopKit.ACID_SKIN, "the hand that hit him is burnt for 2");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void goopRevampRevertLeavesNothing(GameTestHelper helper) {
		ServerPlayer p = goop(helper, 30f);
		GoopKit k = kit();
		helper.assertTrue(k.acidTide(p), "Acid Tide");
		k.breakTide(p);
		helper.assertTrue(k.slimeBounce(p), "Slime Bounce");
		helper.assertTrue(k.acidSpray(p), "Acid Spray");
		helper.assertTrue(k.squeeze(p), "Squeeze");
		helper.assertTrue(GoopKit.leftovers(p) >= 2, "a lake and a bounce");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.assertFalse(Omnitrix.state(p).transformed(), "turned back");
		helper.assertFalse(GoopKit.holds(p), "nothing left in the static maps");
		helper.assertTrue(Omnitrix.state(p).kitFlags == 0, "no kit flags");
		helper.assertTrue(Omnitrix.state(p).timers.keySet().stream().noneMatch(s -> s.startsWith(GoopKit.ID + ".squeeze_start")
				|| s.startsWith(GoopKit.ID + ".spray_start") || s.startsWith(GoopKit.ID + ".tide_start")), "no move timers");
		near(helper, p.getScale(), 1f, "no squeeze or puddle scale left");
		helper.succeed();
	}
}
