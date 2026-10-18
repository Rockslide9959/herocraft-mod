package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HoldTap;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p09.CryoFieldEntity;
import com.projecthero.mod.hero.power.p09.CryoHoldMoves;
import com.projecthero.mod.hero.power.p09.CryokinesisHandlers;
import com.projecthero.mod.hero.power.p09.FrostStacks;
import com.projecthero.mod.hero.power.p09.IceBoltEntity;
import com.projecthero.mod.hero.power.p09.IceGolemEntity;
import com.projecthero.mod.hero.power.p09.IceJavelinEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Cryokinesis hold moves (HoldTap): every key held 0.6 s fires its hold move -- Ice Javelin, Frozen Ground, Ice
 * Wings, Blizzard, Ice Fortress, Ice Golem -- with its own Cold cost and cooldown; a quick tap still does the old move;
 * the Freeze Beam is a toggle now. A hold is simulated the way the client sends it: key-down, the server ticking past
 * {@link HoldTap#HOLD_TICKS} (real game ticks -- HoldTap reads the level's game time), then key-up.
 */
public class CryokinesisHoldGameTests implements FabricGameTest {
	private static final String K = CryokinesisHandlers.KEY;

	/** A survival mock cryokinetic at (3.5, 2, 1.5) on a stone floor, facing +Z, in a room cleared inside the test's own 8x8 area. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(3.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -1), base.offset(3, 5, 6))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, -1, -1), base.offset(3, -1, 6))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		Power power = Powers.byKey(K);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		CryokinesisHandlers.setCold(p, CryokinesisHandlers.MAX_COLD);
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static AbilitySlot slot(int n) {
		return AbilitySlot.byNumber(n);
	}

	/** A quick tap: key-down and key-up in the same tick. */
	private static void tap(ServerPlayer p, int n) {
		AbilityRouter.dispatchExperimental(p, slot(n), true);
		AbilityRouter.dispatchExperimental(p, slot(n), false);
	}

	/**
	 * Holds key {@code n}: down now, the server ticks past the hold time, then {@code check} runs with the key still down,
	 * then key-up.
	 */
	private static void hold(GameTestHelper helper, ServerPlayer p, int n, Runnable check) {
		AbilityRouter.dispatchExperimental(p, slot(n), true);
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1, () -> {
			for (int i = 0; i < 12; i++) {
				AbilityRouter.serverTick(p); // the hold fires on the first of these (if the server has not fired it already)
			}
			check.run();
			AbilityRouter.dispatchExperimental(p, slot(n), false);
		});
	}

	private static IronGolem dummy(GameTestHelper helper, double x, double z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new Vec3(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z) {
		Zombie zb = helper.spawn(EntityType.ZOMBIE, new Vec3(x, 2, z));
		zb.setPersistenceRequired();
		zb.setNoAi(true);
		zb.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false)); // no burning in the test sun
		return zb;
	}

	private static <T extends net.minecraft.world.entity.Entity> List<T> near(GameTestHelper helper, ServerPlayer p, Class<T> c, double r) {
		return helper.getLevel().getEntitiesOfClass(c, new AABB(p.blockPosition()).inflate(r));
	}

	private static void assertCold(GameTestHelper helper, ServerPlayer p, float expected, String what) {
		// the server may tick the mock player itself, so a tick or so of refill can sneak in after the cast
		float got = CryokinesisHandlers.cold(p);
		helper.assertTrue(got >= expected - 0.05f && got <= expected + 12.0f, what + ": expected ~" + expected + " Cold, got " + got);
	}

	// ---------------- registry ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAHoldMove(GameTestHelper helper) {
		for (String id : new String[] { "ice_bolt", "freeze_beam", "ice_slide", "glacier_spikes", "glacier_wall", "ice_blade" }) {
			helper.assertTrue(HoldTap.hasHold(K, id), id + " has a hold move");
		}
		helper.succeed();
	}

	// ---------------- taps are unchanged ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void tapRStillThrowsTheIceBolt(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tap(p, 1);
		helper.assertTrue(!near(helper, p, IceBoltEntity.class, 4).isEmpty(), "a tap of R throws an Ice Bolt");
		helper.assertTrue(near(helper, p, IceJavelinEntity.class, 4).isEmpty(), "and no javelin");
		helper.assertTrue(Math.abs(CryokinesisHandlers.cold(p) - (100 - CryokinesisHandlers.COST_BOLT)) < 0.05f, "8 Cold, as before");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void freezeBeamIsAToggle(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 3.5, 6.0);
		tap(p, 2);
		helper.assertTrue(CryokinesisHandlers.beaming(p), "a tap of G starts the beam");
		for (int i = 0; i < 20; i++) {
			ExperimentalPowers.serverTick(p);
		}
		helper.assertTrue(CryokinesisHandlers.beaming(p), "it keeps going with the key up");
		helper.assertTrue(FrostStacks.stacks(g) == 2, "1 Frost every half second, got " + FrostStacks.stacks(g));
		helper.assertTrue(Math.abs(CryokinesisHandlers.cold(p) - 90) < 0.05f, "10 Cold a second, got " + CryokinesisHandlers.cold(p));
		tap(p, 2);
		helper.assertFalse(CryokinesisHandlers.beaming(p), "a second tap stops it");
		// and it stops when the Cold runs out
		tap(p, 2);
		CryokinesisHandlers.setCold(p, 0.3f);
		for (int i = 0; i < 10; i++) {
			ExperimentalPowers.serverTick(p);
		}
		helper.assertFalse(CryokinesisHandlers.beaming(p), "out of Cold ends it");
		helper.succeed();
	}

	// ---------------- R: Ice Javelin ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void holdRJavelinPiercesFour(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie[] line = new Zombie[5];
		for (int i = 0; i < line.length; i++) {
			line[i] = zombie(helper, 3.5, 3.4 + i * 1.0);
		}
		hold(helper, p, 1, () -> {
			helper.assertTrue(near(helper, p, IceJavelinEntity.class, 5).size() == 1, "holding R forms an Ice Javelin");
			helper.assertTrue(near(helper, p, IceBoltEntity.class, 5).isEmpty(), "and no Ice Bolt");
			assertCold(helper, p, 100 - CryokinesisHandlers.COST_JAVELIN, "25 Cold");
			helper.assertTrue(res(p, "javelin_cd") > 0, "its own cooldown runs");
			// a second hold right away is refused
			float before = CryokinesisHandlers.cold(p);
			CryoHoldMoves.javelin(new com.projecthero.mod.hero.AbilityContext(p, Powers.byKey(K),
					Powers.byKey(K).ability(slot(1)), true));
			helper.assertTrue(CryokinesisHandlers.cold(p) >= before - 0.01f, "on cooldown: nothing spent");
		});
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1 + IceJavelinEntity.CHARGE_TICKS + 20, () -> {
			for (int i = 0; i < 4; i++) {
				// 18 less the zombie's armour (and any freeze sting): at least 15 off
				helper.assertTrue(line[i].getHealth() < 20.0f - 15.0f,
						"zombie " + i + " takes 18 (less armour), has " + line[i].getHealth() + " all: " + java.util.Arrays.stream(line).map(z -> z.getHealth() + "/" + FrostStacks.stacks(z)).toList());
				helper.assertTrue(FrostStacks.stacks(line[i]) == CryokinesisHandlers.JAVELIN_FROST, "zombie " + i + " takes 3 Frost, has "
						+ FrostStacks.stacks(line[i]));
			}
			helper.assertTrue(line[4].getHealth() >= 19.99f, "the fifth is past the pierce limit, has " + line[4].getHealth());
			helper.succeed();
		});
	}

	// ---------------- G: Frozen Ground ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void holdGFreezesTheGround(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 3.5, 5.5);
		hold(helper, p, 2, () -> {
			helper.assertFalse(CryokinesisHandlers.beaming(p), "holding G does not beam");
			List<CryoFieldEntity> f = near(helper, p, CryoFieldEntity.class, 3);
			helper.assertTrue(f.size() == 1 && f.get(0).kind() == CryoFieldEntity.GROUND, "a Frozen Ground sheet under you");
			helper.assertTrue(Math.abs(f.get(0).radius() - 10) < 0.01f && f.get(0).life() == 160, "10 blocks round, 8 s");
			assertCold(helper, p, 100 - CryokinesisHandlers.COST_GROUND, "30 Cold");
			helper.assertTrue(res(p, "ground_cd") > 0, "its own cooldown");
		});
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1 + 45, () -> {
			MobEffectInstance slow = z.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			helper.assertTrue(slow != null && slow.getAmplifier() == 1, "the zombie on the ice is slowed (Slowness II)");
			helper.assertTrue(FrostStacks.stacks(z) >= 1, "and takes Frost every 2 s, has " + FrostStacks.stacks(z));
			helper.assertTrue(p.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null, "never the caster");
			helper.succeed();
		});
	}

	// ---------------- X: Ice Wings ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void holdXGrowsIceWings(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		hold(helper, p, 3, () -> {
			helper.assertTrue(CryokinesisHandlers.winged(p), "holding X grows the wings");
			helper.assertFalse(CryokinesisHandlers.sliding(p), "and does not start the slide");
			helper.assertTrue(p.getAbilities().flying && p.getAttachedOrElse(ModAttachments.HERO_FLYING, false),
					"flying on the shared directional flight");
			helper.assertTrue(res(p, "wings") > CryokinesisHandlers.WINGS_TICKS - 30, "10 s of it, " + res(p, "wings"));
			assertCold(helper, p, 100 - CryokinesisHandlers.COST_WINGS, "30 Cold");
			CryoHoldMoves.stopWings(p);
			helper.assertFalse(CryokinesisHandlers.winged(p) || p.getAbilities().flying || p.getAbilities().mayfly,
					"the wings melt: no flight left");
			helper.assertTrue(res(p, "wings_cd") > 0, "cooldown");
			helper.succeed();
		});
	}

	// ---------------- Z: Blizzard ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void holdZCallsABlizzard(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 3.5, 6.0);
		hold(helper, p, 4, () -> {
			List<CryoFieldEntity> f = helper.getLevel().getEntitiesOfClass(CryoFieldEntity.class, g.getBoundingBox().inflate(3));
			helper.assertTrue(f.size() == 1 && f.get(0).kind() == CryoFieldEntity.BLIZZARD, "a Blizzard where you aim");
			helper.assertTrue(near(helper, p, com.projecthero.mod.hero.power.p09.IceSpikeEntity.class, 14).isEmpty(), "no Glacier Spikes");
			assertCold(helper, p, 100 - CryokinesisHandlers.COST_BLIZZARD, "45 Cold");
		});
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1 + 25, () -> {
			MobEffectInstance slow = g.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
			helper.assertTrue(slow != null && slow.getAmplifier() == 2, "Slowness III in the storm");
			helper.assertTrue(g.hasEffect(MobEffects.BLINDNESS), "blinding snow");
			helper.assertTrue(FrostStacks.stacks(g) >= 1, "1 Frost a second, has " + FrostStacks.stacks(g));
			helper.succeed();
		});
	}

	// ---------------- V: Ice Fortress ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void holdVRaisesAnIceFortress(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 5.5, 1.5);
		BlockPos centre = p.blockPosition();
		BlockPos[] wallCell = new BlockPos[1];
		hold(helper, p, 5, () -> {
			assertCold(helper, p, 100 - CryokinesisHandlers.COST_FORTRESS, "30 Cold");
			helper.assertTrue(res(p, "fortress_cd") > 0, "cooldown");
			helper.assertTrue(z.getDeltaMovement().x > 0.1 || z.getX() > helper.absoluteVec(new Vec3(5.6, 0, 0)).x,
					"the zombie inside is shoved out, " + z.getDeltaMovement());
		});
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1 + 16, () -> {
			helper.assertTrue(CryoHoldMoves.fortressCells(p) > 60, "a dome of ice, " + CryoHoldMoves.fortressCells(p) + " cells");
			int ring = 0;
			List<BlockPos> row0 = CryoHoldMoves.fortressCellList(p);
			for (BlockPos c : row0) {
				BlockState st = helper.getLevel().getBlockState(c);
				if (st.is(Blocks.PACKED_ICE) || st.is(Blocks.BLUE_ICE)) {
					ring++;
					wallCell[0] = c;
				}
			}
			helper.assertTrue(ring == row0.size() && wallCell[0] != null, "every claimed cell is ice, " + ring + " of " + row0.size() + " (player " + p.blockPosition() + ", start " + centre + ")");
			BlockState top = helper.getLevel().getBlockState(centre.above(4));
			helper.assertTrue(top.is(Blocks.BLUE_ICE), "a blue-ice crown, got " + top);
			helper.assertTrue(helper.getLevel().getBlockState(centre).isAir() && helper.getLevel().getBlockState(centre.above()).isAir(),
					"room for you inside");
			ExperimentalPowers.forget(p, Powers.byKey(K)); // dropping the power melts it
		});
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1 + 20, () -> {
			helper.assertTrue(helper.getLevel().getBlockState(wallCell[0]).isAir(), "melted back to air");
			helper.assertTrue(helper.getLevel().getBlockState(centre.offset(0, -1, 3)).is(Blocks.STONE), "the floor untouched");
			helper.succeed();
		});
	}

	// ---------------- C: Ice Golem ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void holdCSummonsAnIceGolem(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		Zombie z = zombie(helper, 3.5, 6.0);
		hold(helper, p, 6, () -> {
			helper.assertFalse(CryokinesisHandlers.bladed(p), "holding C does not draw the blade");
			IceGolemEntity g = CryoHoldMoves.golem(p);
			helper.assertTrue(g != null && g.isAlive() && p.getUUID().equals(g.getOwnerUUID()), "an Ice Golem of yours");
			assertCold(helper, p, 100 - CryokinesisHandlers.COST_GOLEM, "50 Cold");
			helper.assertTrue(Math.abs(g.getMaxHealth() - CryokinesisHandlers.GOLEM_HEALTH) < 0.01, "60 health");
			helper.assertFalse(com.projecthero.mod.combat.HeroTargets.canHarm(p, g), "your moves never hurt it");
			helper.assertFalse(g.mayTarget(p), "it never targets you");
			helper.assertFalse(g.doHurtTarget(p), "or hits you");
			helper.assertTrue(g.mayTarget(z), "it fights a zombie");
			g.doHurtTarget(z);
			helper.assertTrue(FrostStacks.stacks(z) == CryokinesisHandlers.GOLEM_FROST || FrostStacks.frozen(z),
					"its punch adds Frost, has " + FrostStacks.stacks(z));
			ExperimentalPowers.forget(p, Powers.byKey(K));
			helper.assertTrue(g.isRemoved(), "dropping the power shatters it");
			helper.succeed();
		});
	}

	// ---------------- cleanup ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void switchingPowerDropsTheFields(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		hold(helper, p, 2, () -> {
			List<CryoFieldEntity> f = near(helper, p, CryoFieldEntity.class, 3);
			helper.assertTrue(f.size() == 1, "a Frozen Ground sheet");
			CryoHoldMoves.endAll(p);
			helper.assertTrue(f.get(0).isRemoved(), "dropped with the caster's other summons");
			helper.assertTrue(CryoHoldMoves.golem(p) == null, "no golem left");
			helper.succeed();
		});
	}
}
