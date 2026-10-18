package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.HoldTap;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p08.PyroBoltEntity;
import com.projecthero.mod.hero.power.p08.PyroFxEntity;
import com.projecthero.mod.hero.power.p08.PyroSerpentEntity;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 (user: "give all the elemental powers extra moves that are triggered by holding down the ability key"):
 * Pyrokinesis's six hold moves ({@link HoldTap}) -- Blue Flame, Flame Serpent, Phoenix Dive, Meteor Shower, Ring of Fire,
 * Solar Flare -- plus a tap still doing the old move and the Z Meteor turned into a toggle. Tap vs hold is driven the way
 * the keys are: {@link AbilityRouter#dispatchExperimental} down, {@link AbilityRouter#serverTick} once the hold time has
 * passed (by hand too, in case the server does not tick the mock), then up. Dummies are no-AI iron golems (100 HP, no armour) so the
 * numbers come through exactly; every number is read straight after the hit.
 */
public class PyrokinesisHoldGameTests implements FabricGameTest {
	private static final String K = PyrokinesisHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock pyrokinetic at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 9x5x9 room. */
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 1.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, 0, -1), base.offset(4, 4, 7))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-2, -1, -1), base.offset(4, -1, 7))) {
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

	private static AbilitySlot slot(int n) {
		return AbilitySlot.byNumber(n);
	}

	/** A quick tap: down and up in the same tick. */
	private static void tap(ServerPlayer p, int n) {
		AbilityRouter.dispatchExperimental(p, slot(n), true);
		AbilityRouter.dispatchExperimental(p, slot(n), false);
	}

	/** Holds key {@code n} past the hold time, then lets go and runs {@code then}. */
	private static void hold(GameTestHelper helper, ServerPlayer p, int n, Runnable then) {
		AbilityRouter.dispatchExperimental(p, slot(n), true);
		helper.runAfterDelay(HoldTap.HOLD_TICKS + 1, () -> {
			AbilityRouter.serverTick(p); // held 0.6 s: the hold move fires here if the server has not already (HoldTap counts game time)
			AbilityRouter.dispatchExperimental(p, slot(n), false);
			then.run();
		});
	}

	private static IronGolem dummy(GameTestHelper helper, double x, double z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new Vec3(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static List<PyroBoltEntity> bolts(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(PyroBoltEntity.class, p.getBoundingBox().inflate(64), b -> b.getOwner() == p);
	}

	/**
	 * The Heat a hold move added: the server may also tick the mock once between the move and the check, cooling it by up
	 * to a tick's worth (0.5).
	 */
	private static void assertHeat(GameTestHelper helper, float expected, ServerPlayer p, String what) {
		float got = PyrokinesisHandlers.heat(p);
		helper.assertTrue(got <= expected + 0.05f && got >= expected - 1.05f, what + ": expected ~" + expected + ", got " + got);
	}

	private static void assertNear(GameTestHelper helper, float expected, float got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05f, what + ": expected " + expected + ", got " + got);
	}

	// ---------------- tap vs hold ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyKeyHasAHoldAndATapIsTheOldMove(GameTestHelper helper) {
		for (String id : new String[] { "fireball", "fire_whip", "flame_dash", "meteor", "flame_wall", "heat_wave" }) {
			helper.assertTrue(HoldTap.hasHold(K, id), id + " has a hold move");
		}
		ServerPlayer p = hero(helper);
		AbilityRouter.dispatchExperimental(p, slot(1), true);
		helper.assertTrue(bolts(helper, p).isEmpty(), "nothing fires on key-down any more");
		AbilityRouter.dispatchExperimental(p, slot(1), false);
		List<PyroBoltEntity> b = bolts(helper, p);
		helper.assertTrue(b.size() == 1 && !b.get(0).blue() && !b.get(0).meteor(), "a tap of R is still the Fireball");
		assertNear(helper, PyrokinesisHandlers.FIREBALL_DAMAGE, b.get(0).damage(), "the tapped Fireball: 14");
		assertNear(helper, PyrokinesisHandlers.HEAT_FIREBALL, PyrokinesisHandlers.heat(p), "+8 Heat as before");
		b.forEach(PyroBoltEntity::discard);
		helper.succeed();
	}

	// ---------------- R: Blue Flame ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void holdRBlueFlameIgnoresFireResistance(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		hold(helper, p, 1, () -> {
			helper.assertTrue(bolts(helper, p).isEmpty() || bolts(helper, p).stream().allMatch(PyroBoltEntity::blue),
					"a hold doesn't throw the plain Fireball");
			helper.assertTrue(res(p, "blue_charge") > 0.5f, "holding R winds up a Blue Flame");
			tick(p, PyrokinesisHandlers.BLUE_WINDUP); // the half-second wind-up
			List<PyroBoltEntity> b = bolts(helper, p);
			helper.assertTrue(b.size() == 1 && b.get(0).blue(), "holding R throws a Blue Flame, got " + b.size());
			assertNear(helper, PyrokinesisHandlers.BLUE_DAMAGE, b.get(0).damage(), "the Blue Flame: 24");
			assertHeat(helper, PyrokinesisHandlers.HEAT_BLUE, p, "+20 Heat");
			helper.assertTrue(res(p, "blue_cd") > 0.5f, "it has its own cooldown");
			b.forEach(PyroBoltEntity::discard);
			IronGolem g = dummy(helper, 2.5, 5.5);
			g.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 400, 0));
			float before = g.getHealth();
			PyrokinesisHandlers.blueImpact(p, helper.getLevel(), g.position(), g, PyrokinesisHandlers.BLUE_DAMAGE, null);
			assertNear(helper, PyrokinesisHandlers.BLUE_DAMAGE, before - g.getHealth(), "24 through Fire Resistance");
			helper.assertTrue(g.getRemainingFireTicks() >= PyrokinesisHandlers.BLUE_BURN_TICKS - 2, "and 8 s of burning");
			// the tap is not on that cooldown
			PyrokinesisHandlers.setHeat(p, 0.0f);
			tap(p, 1);
			helper.assertTrue(bolts(helper, p).stream().anyMatch(x -> !x.blue()), "the Fireball tap still works meanwhile");
			bolts(helper, p).forEach(PyroBoltEntity::discard);
			helper.succeed();
		});
	}

	// ---------------- G: Flame Serpent ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
	public void holdGFlameSerpentHuntsAndStrikes(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2.5, 7.5);
		float before = g.getHealth();
		hold(helper, p, 2, () -> {
			List<PyroSerpentEntity> s = helper.getLevel().getEntitiesOfClass(PyroSerpentEntity.class, p.getBoundingBox().inflate(16));
			helper.assertTrue(s.size() == 1, "holding G looses a Flame Serpent, got " + s.size());
			assertNear(helper, PyrokinesisHandlers.SERPENT_DAMAGE, s.get(0).damage(), "it strikes for 18");
			assertHeat(helper, PyrokinesisHandlers.HEAT_SERPENT, p, "+25 Heat");
			helper.assertTrue(g.getHealth() == before, "the plain Fire Whip didn't crack (that was a hold)");
			helper.succeedWhen(() -> {
				float lost = before - g.getHealth();
				helper.assertTrue(lost > 0.5f, "the serpent hasn't reached it yet");
				helper.assertTrue(Math.abs(lost - PyrokinesisHandlers.SERPENT_DAMAGE) < 1.05f, "the serpent's strike: 18, got " + lost);
				helper.assertTrue(g.getRemainingFireTicks() > 0, "and sets it burning");
				helper.assertTrue(helper.getLevel().getEntitiesOfClass(PyroSerpentEntity.class, p.getBoundingBox().inflate(16)).isEmpty(),
						"the serpent bursts on the strike");
			});
		});
	}

	// ---------------- X: Phoenix Dive ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void holdXPhoenixDiveRisesDivesAndBlasts(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem near = dummy(helper, 4.5, 3.5); // ~2.8 blocks off: inside the 4-block blast
		IronGolem far = dummy(helper, 2.5, 8.5); // 7 off: outside it
		float n0 = near.getHealth();
		float f0 = far.getHealth();
		hold(helper, p, 3, () -> {
			helper.assertTrue(PyrokinesisHandlers.phoenix(p) && !PyrokinesisHandlers.dashing(p), "holding X: the Phoenix rises (not the Flame Dash)");
			assertHeat(helper, PyrokinesisHandlers.HEAT_PHOENIX, p, "+25 Heat");
			// the rise, the turn and -- the mock never leaves the ground -- the landing
			tick(p, PyrokinesisHandlers.PHOENIX_RISE_TICKS + 2);
			helper.assertFalse(PyrokinesisHandlers.phoenix(p), "the dive has landed");
			assertNear(helper, PyrokinesisHandlers.PHOENIX_DAMAGE, n0 - near.getHealth(), "the Phoenix blast: 20");
			helper.assertTrue(near.getRemainingFireTicks() > 0, "it burns");
			helper.assertTrue(far.getHealth() == f0, "nothing 7 blocks off is caught");
			helper.assertTrue(res(p, "nofall") > 0.5f, "no fall damage after it");
			helper.assertFalse(helper.getLevel().getEntitiesOfClass(PyroFxEntity.class, p.getBoundingBox().inflate(4)).isEmpty(),
					"the landing has its blast model");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void phoenixRunsItsPhasesAndAnOverheatEndsIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setOnGround(false);
		hold(helper, p, 3, () -> {
			tick(p, PyrokinesisHandlers.PHOENIX_RISE_TICKS + 1);
			helper.assertTrue(PyrokinesisHandlers.phoenixDiving(p), "airborne after the rise: diving");
			helper.assertTrue(p.getDeltaMovement().y < -0.3, "the dive heads down along the aim, v=" + p.getDeltaMovement());
			PyrokinesisHandlers.setHeat(p, 99.5f);
			PyrokinesisHandlers.addHeat(p, 1.0f); // overheat
			helper.assertFalse(PyrokinesisHandlers.phoenix(p), "an overheat ends the Phoenix");
			helper.succeed();
		});
	}

	// ---------------- Z: Meteor toggle / Meteor Shower ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void zMeteorIsAToggle(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tap(p, 4);
		helper.assertTrue(PyrokinesisHandlers.charging(p), "a tap of Z starts the Meteor charging");
		helper.assertTrue(bolts(helper, p).isEmpty(), "and it stays over your head after the key comes up");
		helper.runAfterDelay(PyrokinesisHandlers.METEOR_CHARGE_TICKS + 2, () -> {
			tick(p, 1);
			helper.assertTrue(PyrokinesisHandlers.charging(p), "still charging with the key up");
			tap(p, 4);
			List<PyroBoltEntity> b = bolts(helper, p);
			helper.assertTrue(b.size() == 1 && b.get(0).meteor(), "a second tap throws it");
			assertNear(helper, 30.0f, b.get(0).damage(), "a full 2 s charge: 30");
			helper.assertFalse(PyrokinesisHandlers.charging(p), "and the charge is spent");
			b.forEach(PyroBoltEntity::discard);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void meteorThrowsItselfAtFullCharge(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		tap(p, 4);
		helper.runAfterDelay(PyrokinesisHandlers.METEOR_AUTO_THROW_TICKS + 1, () -> {
			tick(p, 1);
			List<PyroBoltEntity> b = bolts(helper, p);
			helper.assertTrue(b.size() == 1 && b.get(0).meteor(), "after 5 s it throws itself");
			assertNear(helper, 30.0f, b.get(0).damage(), "at full charge: 30");
			b.forEach(PyroBoltEntity::discard);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void holdZMeteorShower(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		hold(helper, p, 4, () -> {
			helper.assertFalse(PyrokinesisHandlers.charging(p), "a hold doesn't start the big Meteor");
			List<PyroBoltEntity> b = bolts(helper, p);
			helper.assertTrue(b.size() == PyrokinesisHandlers.SHOWER_COUNT, "six meteors, got " + b.size());
			for (PyroBoltEntity m : b) {
				helper.assertTrue(m.meteor() && Math.abs(m.blastRadius() - PyrokinesisHandlers.SHOWER_BLAST) < 1.0e-3, "small meteors");
				assertNear(helper, PyrokinesisHandlers.SHOWER_DAMAGE, m.damage(), "10 each");
				helper.assertTrue(m.getY() > p.getY() + 10 && m.getDeltaMovement().y < 0, "falling out of the sky");
			}
			assertHeat(helper, PyrokinesisHandlers.HEAT_SHOWER, p, "+35 Heat");
			b.forEach(PyroBoltEntity::discard);
			IronGolem in = dummy(helper, 4.5, 6.5);
			IronGolem out = dummy(helper, 0.5, 3.0); // ~3.6 from the blast to its box
			float i0 = in.getHealth();
			float o0 = out.getHealth();
			Vec3 at = in.position().add(-1.0, 0, 0);
			int hit = PyrokinesisHandlers.meteorImpact(p, helper.getLevel(), at, PyrokinesisHandlers.SHOWER_DAMAGE, 0.55f, null,
					PyrokinesisHandlers.SHOWER_BLAST);
			assertNear(helper, PyrokinesisHandlers.SHOWER_DAMAGE, i0 - in.getHealth(), "a shower meteor's blast: 10");
			helper.assertTrue(hit == 1 && out.getHealth() == o0, "a small 2.5-block blast, hit " + hit);
			helper.succeed();
		});
	}

	// ---------------- V: Ring of Fire ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void holdVRingOfFireBurnsWhatCrossesIt(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem onRing = dummy(helper, 2.5, 7.5); // 6 blocks out: in the flames
		IronGolem inside = dummy(helper, 4.5, 3.5); // ~2.8 out: inside the circle, not in the fire
		float r0 = onRing.getHealth();
		float i0 = inside.getHealth();
		hold(helper, p, 5, () -> {
			List<PyroFxEntity> rings = helper.getLevel().getEntitiesOfClass(PyroFxEntity.class, p.getBoundingBox().inflate(2),
					e -> e.kind() == PyroFxEntity.RING);
			helper.assertTrue(rings.size() == 1, "holding V lights a Ring of Fire");
			helper.assertTrue(Math.abs(rings.get(0).size() - PyrokinesisHandlers.RING_RADIUS) < 1.0e-3, "6 blocks round");
			helper.assertTrue(rings.get(0).life() == PyrokinesisHandlers.RING_TICKS, "for 10 s");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(PyroFxEntity.class, p.getBoundingBox().inflate(8),
					e -> e.kind() == PyroFxEntity.WALL).isEmpty(), "not the plain Flame Wall");
			assertHeat(helper, PyrokinesisHandlers.HEAT_RING, p, "+20 Heat");
			onRing.invulnerableTime = 0;
			float before = onRing.getHealth();
			int hit = PyrokinesisHandlers.ringBurn(p, helper.getLevel(), rings.get(0), PyrokinesisHandlers.RING_DAMAGE);
			helper.assertTrue(hit == 1, "only what stands in the flames burns, hit " + hit);
			assertNear(helper, PyrokinesisHandlers.RING_DAMAGE, before - onRing.getHealth(), "4 a half second");
			helper.assertTrue(inside.getHealth() == i0, "the inside of the circle is safe");
			helper.assertTrue(p.getHealth() == p.getMaxHealth(), "the caster is immune");
			helper.runAfterDelay(25, () -> {
				helper.assertTrue(r0 - onRing.getHealth() > PyrokinesisHandlers.RING_DAMAGE + 1.0f, "the ring keeps burning on its own");
				rings.get(0).discard();
				helper.succeed();
			});
		});
	}

	// ---------------- C: Solar Flare ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void holdCSolarFlareBlindsAndIgnites(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // a husk stays
		Husk husk = helper.spawn(EntityType.HUSK, new Vec3(2.5, 2, 6.5));
		husk.setNoAi(true);
		husk.setPersistenceRequired();
		husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
		husk.setHealth(100);
		IronGolem golem = dummy(helper, 4.5, 3.5); // not a threat: left alone
		hold(helper, p, 6, () -> {
			helper.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "the flare blinds a threat");
			helper.assertTrue(husk.getEffect(MobEffects.BLINDNESS).getDuration() > PyrokinesisHandlers.FLARE_BLIND_TICKS - 20,
					"for 4 s");
			helper.assertTrue(husk.getRemainingFireTicks() > 0, "and sets it alight");
			helper.assertFalse(golem.hasEffect(MobEffects.BLINDNESS), "a golem is no threat");
			helper.assertFalse(helper.getLevel().getEntitiesOfClass(PyroFxEntity.class, p.getBoundingBox().inflate(3),
					e -> e.kind() == PyroFxEntity.FLARE).isEmpty(), "the sun-burst model");
			assertHeat(helper, PyrokinesisHandlers.HEAT_FLARE, p, "+15 Heat (and no Heat Wave's 10)");
			helper.assertTrue(res(p, "flare_cd") > 0.5f, "its own cooldown");
			helper.succeed();
		});
	}
}
