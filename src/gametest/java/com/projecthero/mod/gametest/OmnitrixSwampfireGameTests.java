package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.swampfire.SwampfireKit;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Swampfire revamp (docs/OMNITRIX_REVAMP_SWAMPFIRE.md): his body and weaknesses (fire immune, cold x2, no regrowth while
 * frozen), the held Methane Torch (pulses, the cooldown from the release), Methane Cloud / Ignite on Z (and a Fire Blast setting
 * the cloud off), Methane Inferno + Methane Surge, Reassembly (mulch, reform, cooldown, never while frozen), the seeds (Seed Scatter
 * mines, 5 at most), Vine Snare's yank, Vine Lash, the Overgrowth ladder, and that turning back leaves no state behind. Mock players
 * are not ticked: tests over time drive {@link Omnitrix#tick} themselves.
 */
public class OmnitrixSwampfireGameTests implements FabricGameTest {
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

	/** A recalibrated wearer, transformed into Swampfire. */
	private static ServerPlayer swampfire(GameTestHelper helper, float pitch) {
		ServerPlayer p = mock(helper, 3.5, 2, 1.5, pitch);
		helper.assertTrue(Omnitrix.grant(p, true), "the Recalibrated Omnitrix goes on");
		helper.assertTrue(Omnitrix.select(p, SwampfireKit.ID), "selected Swampfire");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Swampfire");
		p.setOnGround(true);
		return p;
	}

	private static SwampfireKit kit() {
		return (SwampfireKit) Aliens.kit(SwampfireKit.ID);
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

	private static void clearCooldown(ServerPlayer p, String move) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.remove(SwampfireKit.ID + "." + move);
		Omnitrix.save(p, n);
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void swampfireIsFireImmuneHatesColdAndRegrowsUnlessFrozen(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = swampfire(helper, 0f);
		near(helper, p.getMaxHealth(), 32f, "32 health");
		near(helper, p.getArmorValue(), 6f, "armour 6");
		float hp = p.getHealth();
		p.hurt(helper.getLevel().damageSources().inFire(), 6f);
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().lava(), 6f);
		p.invulnerableTime = 0;
		near(helper, p.getHealth(), hp, "fire and lava never hurt him");
		p.hurt(helper.getLevel().damageSources().freeze(), 2f);
		p.invulnerableTime = 0;
		helper.assertTrue(hp - p.getHealth() > 2.0f, "cold hurts him double (lost " + (hp - p.getHealth()) + " to a 2-damage freeze)");
		p.setHealth(20f);
		pump(helper, p);
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(p.getHealth() >= 21.9f, "he regrew about a health a second (now " + p.getHealth() + ")");
			float h = p.getHealth();
			p.setTicksFrozen(200);
			helper.runAfterDelay(41, () -> {
				helper.assertTrue(p.getHealth() <= h + 0.01f, "frozen, he does not regrow (" + h + " -> " + p.getHealth() + ")");
				p.setTicksFrozen(0);
				Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
				helper.assertFalse(SwampfireKit.holds(p), "no Swampfire state left");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void swampfireTorchPulsesWhileHeldAndCoolsDownFromTheRelease(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = swampfire(helper, 0f);
		SwampfireKit k = kit();
		Villager v = prey(helper, 3.5, 2, 4.0, 100f);
		helper.assertTrue(k.torch(p), "the Methane Torch lights");
		helper.assertTrue(k.flag(p, SwampfireKit.F_TORCH), "and burns while held");
		near(helper, v.getHealth(), 100f - SwampfireKit.TORCH_PULSE_DAMAGE, "the first lick");
		helper.assertTrue(v.isOnFire(), "sets it alight");
		helper.assertFalse(k.torch(p), "a second press does not restart it");
		v.clearFire(); // its own burning must not blur the pulse count
		pump(helper, p);
		helper.runAfterDelay(SwampfireKit.TORCH_PULSE + 1, () -> {
			v.invulnerableTime = 0;
			near(helper, v.getHealth(), 100f - 2 * SwampfireKit.TORCH_PULSE_DAMAGE, "a second lick half a second later");
			k.release(p, AbilitySlot.SLOT_1, true, SwampfireKit.TORCH_PULSE + 1);
			helper.assertFalse(k.flag(p, SwampfireKit.F_TORCH), "let go, it stops");
			int left = k.cooldownLeft(p, SwampfireKit.TORCH);
			helper.assertTrue(left > SwampfireKit.TORCH_COOLDOWN - 3, "the 6 s cooldown runs from the release (" + left + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void swampfireZVentsThenIgnitesAndAFireBlastSetsTheCloudOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = swampfire(helper, 0f);
		SwampfireKit k = kit();
		Villager v = prey(helper, 3.5, 2, 3.5, 100f);
		helper.assertFalse(k.ignite(p), "no cloud, no ignite");
		k.press(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(SwampfireKit.hasCloud(p) && k.flag(p, SwampfireKit.F_CLOUD), "Z vents a methane cloud");
		helper.assertTrue(k.cooldownLeft(p, SwampfireKit.CLOUD) == 0, "the cooldown waits for the blast");
		float before = v.getHealth();
		v.invulnerableTime = 0;
		k.press(p, AbilitySlot.SLOT_4, false);
		helper.assertFalse(SwampfireKit.hasCloud(p), "Z again lights it");
		near(helper, before - v.getHealth(), SwampfireKit.IGNITE_DAMAGE, "Ignite's damage");
		helper.assertTrue(k.cooldownLeft(p, SwampfireKit.CLOUD) > 0, "and the cooldown starts after the blast");
		helper.assertFalse(k.cloud(p), "no new cloud while it cools down");
		// his fire through the gas: a Fire Blast flying into the cloud sets it off (the villager out of its way)
		v.discard();
		clearCooldown(p, SwampfireKit.CLOUD);
		clearCooldown(p, SwampfireKit.FIRE_BLAST);
		helper.assertTrue(k.cloud(p), "a fresh cloud");
		helper.assertTrue(k.fireBlast(p), "a Fire Blast");
		pump(helper, p);
		helper.runAfterDelay(3, () -> {
			helper.assertFalse(SwampfireKit.hasCloud(p), "the fireball set the cloud off");
			helper.assertTrue(k.cooldownLeft(p, SwampfireKit.CLOUD) > 0, "and Z cools down");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void swampfireMethaneInfernoBlastsThenSurges(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = swampfire(helper, 0f);
		SwampfireKit k = kit();
		Villager v = prey(helper, 3.5, 2, 5.5, 200f);
		helper.assertTrue(k.infernoStart(p), "the build-up starts");
		helper.assertTrue(k.flag(p, SwampfireKit.F_CHARGE), "charging");
		helper.assertTrue(k.cooldownLeft(p, SwampfireKit.INFERNO) > 0, "the 60 s cooldown");
		pump(helper, p);
		helper.runAfterDelay(SwampfireKit.CHARGE_TICKS + 2, () -> {
			float lost = 200f - v.getHealth();
			helper.assertTrue(lost >= SwampfireKit.INFERNO_EDGE - 0.1f && lost <= SwampfireKit.INFERNO_DAMAGE + 4f,
					"40-60 from the explosion (lost " + lost + ")");
			helper.assertTrue(v.isOnFire(), "alight");
			helper.assertTrue(k.flag(p, SwampfireKit.F_SURGE), "Methane Surge is on");
			near(helper, k.fireFactor(p), SwampfireKit.SURGE_FACTOR, "his fire +25%");
			// the surge ends on time
			k.setTimer(p, SwampfireKit.SURGE_UNTIL, p.level().getGameTime() + 2);
			helper.runAfterDelay(4, () -> {
				helper.assertFalse(k.flag(p, SwampfireKit.F_SURGE), "the surge ran out");
				near(helper, k.fireFactor(p), 1f, "his fire back to normal");
				Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
				helper.assertFalse(SwampfireKit.holds(p), "no state left");
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void swampfireReassemblesFromMulchOnceThenCoolsDown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = swampfire(helper, 0f);
		SwampfireKit k = kit();
		helper.assertTrue(k.reassemblyReady(p), "Reassembly is ready");
		p.hurt(helper.getLevel().damageSources().generic(), 500f);
		helper.assertTrue(p.isAlive() && Omnitrix.state(p).transformed(), "a lethal hit does not kill him, nor turn him back");
		helper.assertTrue(k.flag(p, SwampfireKit.F_MULCH), "he is a heap of mulch");
		helper.assertFalse(k.reassemblyReady(p), "Reassembly cools down");
		helper.assertTrue(k.cooldownLeft(p, SwampfireKit.REASSEMBLY) > SwampfireKit.REASSEMBLY_COOLDOWN - 5, "for 150 s");
		float h = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(helper.getLevel().damageSources().generic(), 5f);
		near(helper, p.getHealth(), h, "nothing hurts the mulch");
		k.press(p, AbilitySlot.SLOT_1, false);
		helper.assertTrue(k.cooldownLeft(p, SwampfireKit.FIRE_BLAST) == 0, "and the mulch can't act");
		pump(helper, p);
		helper.runAfterDelay(SwampfireKit.MULCH_TICKS + 2, () -> {
			helper.assertFalse(k.flag(p, SwampfireKit.F_MULCH), "he pulled himself together");
			helper.assertTrue(p.getHealth() >= SwampfireKit.REFORM_HEALTH - 0.01f, "on 10 health (" + p.getHealth() + ")");
			helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_RESISTANCE), "with a moment of Resistance");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swampfireFrozenCannotReassemble(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = swampfire(helper, 0f);
		SwampfireKit k = kit();
		p.setTicksFrozen(200);
		helper.assertFalse(k.reassemble(p, helper.getLevel().damageSources().generic()), "a frozen Swampfire stays dead");
		p.setTicksFrozen(0);
		helper.assertFalse(k.reassemble(p, helper.getLevel().damageSources().genericKill()), "/kill still kills");
		helper.assertTrue(k.reassemblyReady(p), "neither spent the cooldown");
		helper.assertTrue(k.reassemble(p, helper.getLevel().damageSources().generic()), "an ordinary death is saved");
		k.reform(p);
		helper.assertFalse(k.reassemble(p, helper.getLevel().damageSources().generic()), "but only once per 150 s");
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void swampfireSeedsVinesAndLash(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = swampfire(helper, 0f);
		SwampfireKit k = kit();
		// Seed Scatter: at most 5 planted; an enemy near one bursts it
		for (int i = 0; i < 7; i++) {
			k.plant(p, helper.absoluteVec(new Vec3(0.5 + i * 0.1, 2, 8.5)));
		}
		helper.assertTrue(SwampfireKit.mines(p) == SwampfireKit.MAX_MINES, "5 seeds at most (" + SwampfireKit.mines(p) + ")");
		Zombie z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 zat = helper.absoluteVec(new Vec3(1.0, 2, 8.5));
		z.moveTo(zat.x, zat.y, zat.z, 0f, 0f);
		z.setNoAi(true);
		z.setPersistenceRequired();
		helper.getLevel().addFreshEntity(z);
		float zh = z.getHealth();
		// Vine Snare + yank
		Villager v = prey(helper, 3.5, 2, 8.0, 100f);
		helper.assertTrue(k.snare(p), "Vine Snare catches the villager");
		helper.assertTrue(k.flag(p, SwampfireKit.F_SNARE), "held");
		double before = v.distanceTo(p);
		k.press(p, AbilitySlot.SLOT_2, true);
		helper.assertFalse(k.flag(p, SwampfireKit.F_SNARE), "Shift+G again yanks and lets go");
		helper.assertTrue(v.distanceTo(p) < before - 2.0, "yanked toward him (" + before + " -> " + v.distanceTo(p) + ")");
		// Vine Lash
		v.invulnerableTime = 0;
		float vh = v.getHealth();
		helper.assertTrue(k.vineLash(p), "Vine Lash");
		near(helper, vh - v.getHealth(), SwampfireKit.LASH_DAMAGE, "the lash's damage");
		helper.assertFalse(k.vineLash(p), "0.8 s between lashes");
		pump(helper, p);
		helper.runAfterDelay(4, () -> {
			helper.assertTrue(zh - z.getHealth() >= SwampfireKit.MINE_DAMAGE - 0.1f, "a seed burst under the zombie (lost " + (zh - z.getHealth()) + ")");
			helper.assertTrue(SwampfireKit.mines(p) < SwampfireKit.MAX_MINES, "and is spent");
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			helper.assertFalse(SwampfireKit.holds(p), "turning back clears the seeds, cloud, roots, tangle and stench");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void swampfireOvergrowthGrowsAVineLadder(GameTestHelper helper) {
		floor(helper);
		for (int y = 2; y <= 9; y++) {
			for (int x = 2; x <= 4; x++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, 4)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = swampfire(helper, 0f);
		int placed = kit().ladder(p);
		if (HeroConfig.get().abilityTerrainDamage) {
			helper.assertTrue(placed >= 6, "a vine ladder up the wall (" + placed + ")");
			helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 3, 3))).is(Blocks.VINE), "real vines on the wall");
		}
		Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
		helper.succeed();
	}
}
