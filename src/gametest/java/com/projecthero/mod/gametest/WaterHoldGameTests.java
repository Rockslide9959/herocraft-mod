package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HoldTap;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p25.RainCallEntity;
import com.projecthero.mod.hero.power.p25.TidalWaveEntity;
import com.projecthero.mod.hero.power.p25.TsunamiEntity;
import com.projecthero.mod.hero.power.p25.WaterBoltEntity;
import com.projecthero.mod.hero.power.p25.WaterCloneEntity;
import com.projecthero.mod.hero.power.p25.WaterDragonEntity;
import com.projecthero.mod.hero.power.p25.WaterFxEntity;
import com.projecthero.mod.hero.power.p25.WaterHandlers;
import com.projecthero.mod.hero.power.p25.WhirlpoolEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Water Manipulation hold moves (HoldTap): every key's hold move -- Water Dragon (R), Whirlpool (G), Hydro Jets
 * (X), Tsunami (Z), Water Clone (V), Rain Call (C) -- its cost, cooldown and server effect; a tap still doing the old
 * move; Shift+hold doing nothing new; the Water Shield now a toggle; and the hold moves' cleanup. A key is held by
 * pressing it through {@link AbilityRouter#dispatchExperimental}, letting real ticks pass (HoldTap times the hold on game
 * time) and ticking {@link AbilityRouter#serverTick}, then releasing it. Mock players are not ticked by the server, so
 * the tests drive the ticks themselves. Dummies are no-AI iron golems (100 HP, no armour).
 */
public class WaterHoldGameTests implements FabricGameTest {
	private static final String K = WaterHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock water bender at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x4x9 room. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -1), base.offset(2, 4, 7))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -1), base.offset(2, -1, 7))) {
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
		tick(p, 1);
		return p;
	}

	private static float res(ServerPlayer p, String name) {
		return ExperimentalPowers.getResource(p, Powers.byKey(K), name);
	}

	private static void tick(ServerPlayer p, int n) {
		for (int i = 0; i < n; i++) {
			ExperimentalPowers.serverTick(p);
		}
	}

	private static void down(ServerPlayer p, int slot) {
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), true);
	}

	private static void up(ServerPlayer p, int slot) {
		AbilityRouter.dispatchExperimental(p, AbilitySlot.byNumber(slot), false);
	}

	/** A quick tap: down and straight back up. */
	private static void tap(ServerPlayer p, int slot) {
		down(p, slot);
		up(p, slot);
	}

	/**
	 * Holds {@code slot}: the key goes down now, real ticks pass for the hold time, one router tick fires the hold move,
	 * the key comes up, then {@code then} runs (the meter is exactly what the hold move left: no refill tick in between).
	 */
	private static void hold(GameTestHelper helper, ServerPlayer p, int slot, Runnable then) {
		down(p, slot);
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1, () -> {
			AbilityRouter.serverTick(p);
			up(p, slot);
			then.run();
		});
	}

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	/** A dummy big enough to count as a boss (300 max health): hit, never dragged, carried or lured. */
	private static IronGolem bossDummy(GameTestHelper helper, int x, int z) {
		IronGolem g = dummy(helper, x, z);
		g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(300);
		g.setHealth(300);
		return g;
	}

	private static <T extends WaterFxEntity> List<T> bodies(ServerPlayer p, Class<T> type) {
		return p.serverLevel().getEntitiesOfClass(type, p.getBoundingBox().inflate(48), e -> e.ownerId() == p.getId() && e.isAlive());
	}

	private static List<WaterCloneEntity> clones(ServerPlayer p) {
		return p.serverLevel().getEntitiesOfClass(WaterCloneEntity.class, p.getBoundingBox().inflate(48),
				c -> c.isAlive() && c.ownerId().map(p.getUUID()::equals).orElse(false));
	}

	/**
	 * The meter (full at the start) is down by {@code cost} -- allowing for the tick or two of refill the server may have
	 * run since (the mock player is ticked by the server too).
	 */
	private static void assertSpent(GameTestHelper helper, ServerPlayer p, float cost, String what) {
		float spent = 100.0f - WaterHandlers.water(p);
		helper.assertTrue(spent > cost - 1.1f && spent < cost + 0.05f, what + ": spent " + spent);
	}

	private static void assertNear(GameTestHelper helper, float expected, float got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05f, what + ": expected " + expected + ", got " + got);
	}

	private static int waterBlocks(ServerLevel level, BlockPos around) {
		int n = 0;
		for (BlockPos b : BlockPos.betweenClosed(around.offset(-8, -4, -4), around.offset(8, 12, 24))) {
			if (level.getFluidState(b).is(net.minecraft.tags.FluidTags.WATER)) {
				n++;
			}
		}
		return n;
	}

	// ---------------- registry ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAHoldMove(GameTestHelper helper) {
		for (String id : new String[] { "water_shot", "water_whip", "riptide", "tidal_wave", "water_shield", "aquatic_form" }) {
			helper.assertTrue(HoldTap.hasHold(K, id), id + " has a hold move");
		}
		helper.assertTrue(com.projecthero.mod.hero.visual.MutationVisuals.registeredFlags().contains("p25.hjets"), "the jets are synced");
		helper.succeed();
	}

	// ---------------- tap / Shift unchanged ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void aTapStillFiresTheOldMove(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tap(p, 1);
		assertSpent(helper, p, WaterHandlers.COST_SHOT, "a tap of R is the Water Shot (8 Water)");
		helper.assertTrue(bodies(p, WaterBoltEntity.class).size() == 1, "a bolt");
		helper.assertTrue(bodies(p, WaterDragonEntity.class).isEmpty(), "no dragon");
		// held for less than the hold time it is still a tap
		down(p, 1);
		helper.runAfterDelay(5, () -> {
			AbilityRouter.serverTick(p);
			float before = WaterHandlers.water(p);
			up(p, 1);
			assertNear(helper, before - WaterHandlers.COST_SHOT, WaterHandlers.water(p), "a short hold is still a tap: a second Water Shot");
			helper.assertTrue(bodies(p, WaterDragonEntity.class).isEmpty(), "and still no dragon");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftHoldDoesNothingNew(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setShiftKeyDown(true);
		down(p, 1); // Shift+R: the Hydro Jet, held
		p.setShiftKeyDown(false);
		helper.assertTrue(WaterHandlers.jetting(p), "Shift+R pours the jet at once, as before");
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 2, () -> {
			AbilityRouter.serverTick(p);
			helper.assertTrue(bodies(p, WaterDragonEntity.class).isEmpty(), "holding Shift+R never makes a dragon");
			helper.assertTrue(WaterHandlers.jetting(p), "the jet is still pouring");
			up(p, 1);
			helper.assertFalse(WaterHandlers.jetting(p), "and letting go stops it");
			helper.succeed();
		});
	}

	// ---------------- R: Water Dragon ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void waterDragonRamsForTwenty(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		IronGolem near = dummy(helper, 4, 6);
		hold(helper, p, 1, () -> {
			assertSpent(helper, p, WaterHandlers.COST_DRAGON, "25 Water");
			helper.assertTrue(res(p, "dragon_cd") >= WaterHandlers.DRAGON_CD - 2.5f, "an 8 s cooldown of its own");
			helper.assertTrue(bodies(p, WaterBoltEntity.class).isEmpty(), "the hold is not a tap: no bolt");
			List<WaterDragonEntity> ds = bodies(p, WaterDragonEntity.class);
			helper.assertTrue(ds.size() == 1 && ds.get(0).targetId() == g.getId(), "a dragon, sent at the aimed golem");
			WaterDragonEntity d = ds.get(0);
			Vec3 m = g.position().add(0, g.getBbHeight() * 0.5, 0);
			d.setPos(m.x, m.y, m.z);
			int n = d.strike(helper.getLevel(), p, g);
			assertNear(helper, 80.0f, g.getHealth(), "it rams for 20");
			assertNear(helper, 90.0f, near.getHealth(), "and the splash hits what is beside it for half");
			helper.assertTrue(n == 2, "two hit, got " + n);
			Vec3 v = g.getDeltaMovement();
			helper.assertTrue(Math.sqrt(v.x * v.x + v.z * v.z) > 1.0 && v.y > 0.4, "a big knockback, v=" + v);
			helper.assertTrue(d.hitFor() >= 0, "and it collapses");
			// a second hold inside the cooldown is refused
			hold(helper, p, 1, () -> {
				helper.assertTrue(WaterHandlers.water(p) > 100.0f - WaterHandlers.COST_DRAGON - 0.05f,
						"refused on its cooldown: no Water spent, got " + WaterHandlers.water(p));
				helper.assertTrue(bodies(p, WaterDragonEntity.class).size() <= 1, "no second dragon");
				helper.assertTrue(bodies(p, WaterBoltEntity.class).isEmpty(), "and no bolt either");
				helper.succeed();
			});
		});
	}

	// ---------------- G: Whirlpool ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void whirlpoolDragsInAndDrowns(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		IronGolem side = dummy(helper, 3, 7);
		IronGolem boss = bossDummy(helper, 1, 5);
		hold(helper, p, 2, () -> {
			assertSpent(helper, p, WaterHandlers.COST_WHIRLPOOL, "35 Water");
			helper.assertTrue(res(p, "whirl_cd") >= WaterHandlers.WHIRLPOOL_CD - 2.5f, "a 15 s cooldown of its own");
			List<WhirlpoolEntity> ws = bodies(p, WhirlpoolEntity.class);
			helper.assertTrue(ws.size() == 1, "a whirlpool");
			WhirlpoolEntity w = ws.get(0);
			helper.assertTrue(Math.abs(w.getX() - g.getX()) < 0.6 && Math.abs(w.getZ() - g.getZ()) < 0.6 && Math.abs(w.getY() - g.getY()) < 0.6,
					"opened on the ground under the aimed target");
			helper.assertTrue(w.size() * 2 == 8.0f && WaterHandlers.WHIRLPOOL_TICKS == 120, "8 across, 6 s");
			for (IronGolem e : new IronGolem[] { g, side, boss }) {
				e.setDeltaMovement(Vec3.ZERO);
			}
			int moved = w.spin(p);
			Vec3 in = w.position().subtract(side.position());
			helper.assertTrue(side.getDeltaMovement().dot(new Vec3(in.x, 0, in.z)) > 0.01, "the one off-centre is dragged in, v="
					+ side.getDeltaMovement());
			helper.assertTrue(boss.getDeltaMovement().lengthSqr() < 1.0e-6, "a boss is never dragged");
			helper.assertTrue(moved == 3, "all three are on it, got " + moved);
			int hurt = w.drown(p);
			assertNear(helper, 97.0f, g.getHealth(), "3 drowning damage a second");
			assertNear(helper, 97.0f, side.getHealth(), "to everything on it");
			assertNear(helper, 297.0f, boss.getHealth(), "bosses too");
			helper.assertTrue(hurt == 3, "three hurt, got " + hurt);
			w.discard();
			helper.succeed();
		});
	}

	// ---------------- X: Hydro Jets ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void hydroJetsFlyTenSeconds(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		hold(helper, p, 3, () -> {
			assertSpent(helper, p, WaterHandlers.COST_HYDRO_JETS, "30 Water");
			helper.assertTrue(res(p, "hjet") >= WaterHandlers.HYDRO_JETS_TICKS - 2.5f, "10 s of flight");
			helper.assertTrue(res(p, "hjet_cd") >= WaterHandlers.HYDRO_JETS_CD - 2.5f, "its own cooldown, from take-off");
			helper.assertTrue(p.getAbilities().mayfly && p.getAbilities().flying, "flying");
			helper.assertTrue(p.getAttachedOrElse(ModAttachments.HERO_FLYING, false), "on the shared directional flight");
			helper.assertTrue(WaterHandlers.hydroJetting(p), "the jets are on");
			helper.assertFalse(p.isAutoSpinAttack(), "the hold is not a tap: no Riptide");
			p.setOnGround(false);
			float left = res(p, "hjet");
			tick(p, 20);
			assertNear(helper, left - 20, res(p, "hjet"), "a second later");
			helper.assertTrue(p.getAbilities().flying, "still flying in the air");
			tick(p, WaterHandlers.HYDRO_JETS_TICKS);
			helper.assertFalse(p.getAbilities().flying || p.getAbilities().mayfly, "the jets run out after 10 s");
			helper.assertFalse(p.getAttachedOrElse(ModAttachments.HERO_FLYING, false), "and the flight flag is gone");
			helper.assertTrue(res(p, "nofall") > 0.5f, "with no fall damage from the drop");
			helper.succeed();
		});
	}

	// ---------------- Z: Tsunami ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void tsunamiTwentyTwoAndSweeps(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ServerLevel level = helper.getLevel();
		BlockPos at = p.blockPosition();
		int before = waterBlocks(level, at);
		IronGolem g = dummy(helper, 2, 4);
		IronGolem boss = bossDummy(helper, 1, 4);
		hold(helper, p, 4, () -> {
			assertSpent(helper, p, WaterHandlers.COST_TSUNAMI, "60 Water");
			helper.assertTrue(res(p, "tsunami_cd") >= WaterHandlers.TSUNAMI_CD - 2.5f, "a 20 s cooldown of its own");
			List<TidalWaveEntity> waves = bodies(p, TidalWaveEntity.class);
			helper.assertTrue(waves.size() == 1 && waves.get(0) instanceof TsunamiEntity, "a tsunami (and no Tidal Wave)");
			TsunamiEntity w = (TsunamiEntity) waves.get(0);
			helper.assertTrue(w.halfWidth() * 2 == 12.0 && w.height() == 6.0 && Math.abs(w.speed() * w.run() - 30.0) < 1e-6,
					"12 wide, 6 tall, 30 blocks");
			w.moveTo(g.getX(), g.getY(), g.getZ() - 0.5);
			g.setRemainingFireTicks(200);
			g.setDeltaMovement(Vec3.ZERO);
			boss.setDeltaMovement(Vec3.ZERO);
			w.sweep(level, p);
			assertNear(helper, 78.0f, g.getHealth(), "it hits for 22");
			helper.assertFalse(g.isOnFire(), "puts out fire");
			helper.assertTrue(g.getDeltaMovement().z >= w.speed(), "and carries it along, vz=" + g.getDeltaMovement().z);
			assertNear(helper, 278.0f, boss.getHealth(), "a boss takes the hit");
			helper.assertTrue(Math.abs(boss.getDeltaMovement().z) < 1.0e-6, "but is not carried");
			w.sweep(level, p);
			assertNear(helper, 78.0f, g.getHealth(), "only once");
			helper.assertTrue(waterBlocks(level, at) == before, "no water blocks");
			w.discard();
			helper.succeed();
		});
	}

	// ---------------- V: the Water Shield toggle, and the Water Clone ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void waterShieldIsAToggle(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		WaterHandlers.setWater(p, 50);
		tap(p, 5);
		helper.assertTrue(WaterHandlers.shielding(p), "a tap raises it -- and it stays up after the key is released");
		tick(p, 20);
		assertNear(helper, 50.0f - WaterHandlers.COST_SHIELD_PER_SECOND, WaterHandlers.water(p), "the same 6 a second, no refill");
		p.setShiftKeyDown(true);
		tap(p, 5); // Shift+V: Healing Water
		p.setShiftKeyDown(false);
		helper.assertTrue(WaterHandlers.shielding(p), "Shift+V (and its release) leaves the shield up");
		tap(p, 5);
		helper.assertFalse(WaterHandlers.shielding(p), "a second tap drops it");
		WaterHandlers.setWater(p, 50);
		tap(p, 5);
		helper.assertTrue(WaterHandlers.shielding(p), "up again");
		WaterHandlers.setWater(p, 0.2f);
		tick(p, 1);
		helper.assertFalse(WaterHandlers.shielding(p), "and it drops on its own when the Water runs out");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void waterCloneFightsAndBursts(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 2, 6));
		z.setNoAi(true);
		z.setTarget(p);
		hold(helper, p, 5, () -> {
			assertSpent(helper, p, WaterHandlers.COST_CLONE, "40 Water");
			helper.assertFalse(WaterHandlers.shielding(p), "the hold is not a tap: no shield");
			helper.assertTrue(res(p, "clone_cd") >= WaterHandlers.CLONE_CD - 2.5f, "a 25 s cooldown of its own");
			helper.assertTrue(res(p, "clone") >= WaterHandlers.CLONE_TICKS - 2.5f, "15 s");
			List<WaterCloneEntity> cs = clones(p);
			helper.assertTrue(cs.size() == 1, "a clone");
			WaterCloneEntity c = cs.get(0);
			helper.assertTrue(c.getMaxHealth() == WaterHandlers.CLONE_HEALTH, "20 health");
			helper.assertFalse(HeroTargets.canHarm(p, c), "your own moves never hit it");
			helper.assertTrue(c.lure(p) >= 0 && z.getTarget() == c, "the zombie hunting you turns on it");
			float expect = (float) (p.getAttributeValue(Attributes.ATTACK_DAMAGE) * WaterHandlers.CLONE_DAMAGE_FACTOR);
			helper.assertTrue(c.doHurtTarget(g), "it hits");
			assertNear(helper, 100.0f - expect, g.getHealth(), "for half your melee damage");
			c.setLife(WaterHandlers.CLONE_TICKS);
			c.tick();
			helper.assertTrue(c.isRemoved(), "it bursts when its 15 s are up");
			helper.assertTrue(z.getTarget() != c, "and the zombie lets it go");
			z.discard();
			helper.succeed();
		});
	}

	// ---------------- C: Rain Call ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void rainCallRefillsAndHeals(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		ServerLevel level = helper.getLevel();
		boolean raining = level.isRaining();
		hold(helper, p, 6, () -> {
			assertSpent(helper, p, WaterHandlers.COST_RAIN, "30 Water");
			helper.assertFalse(WaterHandlers.aquaticActive(p), "the hold is not a tap: Aquatic Form stays off");
			helper.assertTrue(res(p, "rain") >= WaterHandlers.RAIN_TICKS - 2.5f, "60 s of rain");
			helper.assertTrue(res(p, "rain_cd") >= WaterHandlers.RAIN_CD - 2.5f, "its own cooldown");
			List<RainCallEntity> rs = bodies(p, RainCallEntity.class);
			helper.assertTrue(rs.size() == 1 && rs.get(0).size() == 16.0f, "a 16-block shower over you");
			helper.assertTrue(level.isRaining() == raining, "the world's weather is not touched");
			helper.assertTrue(WaterHandlers.inRainCall(p), "you stand in it");
			helper.assertTrue(WaterHandlers.regenPerSecond(p) == 2 * WaterHandlers.REGEN_PER_SECOND, "the refill doubles: "
					+ WaterHandlers.regenPerSecond(p));
			p.setHealth(10.0f);
			tick(p, WaterHandlers.RAIN_HEAL_EVERY);
			assertNear(helper, 12.0f, p.getHealth(), "a heart every 5 s");
			// step out from under it
			Vec3 home = p.position();
			p.moveTo(home.x + 20, home.y, home.z);
			helper.assertFalse(WaterHandlers.inRainCall(p), "20 blocks away you are out of it");
			helper.assertTrue(WaterHandlers.regenPerSecond(p) == WaterHandlers.REGEN_PER_SECOND, "and the refill is back to 10");
			p.moveTo(home.x, home.y, home.z);
			helper.succeed();
		});
	}

	// ---------------- cleanup ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void holdMovesEndWhenThePowerGoes(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		hold(helper, p, 6, () -> hold(helper, p, 5, () -> hold(helper, p, 3, () -> {
			helper.assertTrue(bodies(p, RainCallEntity.class).size() == 1 && clones(p).size() == 1 && WaterHandlers.hydroJetting(p),
					"the rain, the clone and the jets are all going");
			ExperimentalPowers.forget(p, Powers.byKey(K));
			helper.assertTrue(bodies(p, RainCallEntity.class).isEmpty(), "the rain stops");
			helper.assertTrue(clones(p).isEmpty(), "the clone bursts");
			helper.assertFalse(p.getAbilities().flying || p.getAbilities().mayfly, "the jets are gone");
			helper.assertFalse(p.getAttachedOrElse(ModAttachments.HERO_FLYING, false), "and the flight flag");
			helper.succeed();
		})));
	}
}
