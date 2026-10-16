package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p08.PyroBoltEntity;
import com.projecthero.mod.hero.power.p08.PyroFxEntity;
import com.projecthero.mod.hero.power.p08.PyrokinesisHandlers;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Pyrokinesis rework (docs/V0165_POWERS_PROPOSAL.md, the Pyrokinesis section): six keys (+ Shift versions) on a
 * 0-100 Heat meter that every move raises -- the Heat rules (gain, cooling, the bonus, held fire pausing the cooling), the
 * overheat (forced nova, 5 s lockout), the damage numbers and the passives. Mock players are not reliably ticked by the
 * server, so the tests drive {@link ExperimentalPowers#serverTick} themselves. Target dummies are no-AI iron golems (100
 * HP, no armour, so the damage numbers come through exactly; each number is read straight after the hit, before the
 * burning it leaves can tick).
 */
public class PyrokinesisV0165GameTests implements FabricGameTest {
	private static final String K = PyrokinesisHandlers.KEY;

	// ---------------- helpers ----------------

	/** A survival mock pyrokinetic at (2.5, 2, 1.5) on a stone floor, facing +Z, with a clear 5x4x9 room. */
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

	private static void press(ServerPlayer p, int slot) {
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
	}

	private static void shiftPress(ServerPlayer p, int slot) {
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, slot, true);
		AbilityRouter.handleInput(p, slot, false);
		p.setShiftKeyDown(false);
	}

	/** A no-AI iron golem dummy (100 HP, no armour) at relative (x, 2, z). */
	private static IronGolem dummy(GameTestHelper helper, int x, int z) {
		IronGolem g = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
		g.setPersistenceRequired();
		g.setNoAi(true);
		return g;
	}

	private static void assertNear(GameTestHelper helper, float expected, float got, String what) {
		helper.assertTrue(Math.abs(expected - got) < 0.05f, what + ": expected " + expected + ", got " + got);
	}

	// ---------------- registry / HUD ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void sixKeysHeatMeterAndEnabled(GameTestHelper helper) {
		Power power = Powers.byKey(K);
		helper.assertTrue(Powers.isEnabled(power) && Powers.isMutation(power), "Pyrokinesis is an enabled mutation again");
		helper.assertTrue(power.abilities().size() == 6, "six abilities (no H / N), got " + power.abilities().size());
		String[] ids = { "fireball", "fire_whip", "flame_dash", "meteor", "flame_wall", "heat_wave" };
		for (int i = 0; i < ids.length; i++) {
			Ability a = power.ability(AbilitySlot.byNumber(i + 1));
			helper.assertTrue(a != null && a.id().equals(ids[i]), "slot " + (i + 1) + " should be " + ids[i]);
			helper.assertTrue(AbilityHandlers.has(power, a), ids[i] + " has a handler");
		}
		var heat = MutationMeters.get(K, "heat");
		helper.assertTrue(heat != null && heat.always() && heat.style() == MutationMeters.Style.HAIRLINE, "Heat is an always-on Hairline");
		for (String flag : new String[] { "p08.glow", "p08.flame_body", "p08.jet", "p08.thrower", "p08.charging", "p08.whip",
				"p08.locked" }) {
			helper.assertTrue(MutationVisuals.registeredFlags().contains(flag), flag + " is synced");
		}
		helper.succeed();
	}

	// ---------------- Heat rules ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void heatRisesWithUseAndCoolsTenASecond(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		helper.assertTrue(PyrokinesisHandlers.heat(p) < 0.01f, "Heat starts at 0");
		press(p, 1); // R: Fireball
		assertNear(helper, PyrokinesisHandlers.HEAT_FIREBALL, PyrokinesisHandlers.heat(p), "a Fireball adds 8 Heat");
		PyrokinesisHandlers.setHeat(p, 50.0f);
		tick(p, 20);
		assertNear(helper, 40.0f, PyrokinesisHandlers.heat(p), "Heat cools 10 a second");
		// held / toggled fire holds the Heat up: Flame Body adds 4 a second and nothing cools meanwhile
		shiftPress(p, 5);
		helper.assertTrue(PyrokinesisHandlers.flameBodyActive(p), "Shift+V lights Flame Body");
		tick(p, 20);
		assertNear(helper, 44.0f, PyrokinesisHandlers.heat(p), "Flame Body: +4 a second, no cooling");
		shiftPress(p, 5);
		helper.assertFalse(PyrokinesisHandlers.flameBodyActive(p), "Shift+V again puts it out");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hotterFireHitsHarder(GameTestHelper helper) {
		assertNear(helper, 1.0f, PyrokinesisHandlers.heatMultiplier(0.0f), "cold: x1.0");
		assertNear(helper, 1.2f, PyrokinesisHandlers.heatMultiplier(40.0f), "40 Heat: x1.2");
		assertNear(helper, 1.4f, PyrokinesisHandlers.heatMultiplier(80.0f), "80 Heat: the full +40%");
		assertNear(helper, 1.4f, PyrokinesisHandlers.heatMultiplier(100.0f), "no more past 80");
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		PyrokinesisHandlers.setHeat(p, 80.0f);
		float before = g.getHealth();
		PyrokinesisHandlers.fireballImpact(p, helper.getLevel(), g.position(), g, PyrokinesisHandlers.dmg(p, PyrokinesisHandlers.FIREBALL_DAMAGE), null);
		assertNear(helper, 19.6f, before - g.getHealth(), "a Fireball at 80 Heat: 14 x 1.4");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void overheatNovaThenNoMovesForFiveSeconds(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem near = dummy(helper, 4, 2); // beside the hero, out of the fireball's path
		PyrokinesisHandlers.setHeat(p, 95.0f);
		float before = near.getHealth();
		press(p, 1); // +8 Heat: 100
		helper.assertTrue(PyrokinesisHandlers.locked(p), "reaching 100 Heat overheats");
		assertNear(helper, PyrokinesisHandlers.OVERHEAT_DAMAGE, before - near.getHealth(), "the forced nova: 12 to what is near");
		helper.assertTrue(PyrokinesisHandlers.heat(p) < 0.01f, "the nova vents the Heat");
		helper.assertFalse(helper.getLevel().getEntitiesOfClass(PyroFxEntity.class, p.getBoundingBox().inflate(3)).isEmpty(),
				"the nova has its ring-of-fire model");
		AbilityRouter.handleInput(p, 4, true); // Z: refused while overheated
		helper.assertFalse(PyrokinesisHandlers.charging(p), "no moves while overheated");
		AbilityRouter.handleInput(p, 4, false);
		tick(p, PyrokinesisHandlers.LOCK_TICKS - 5);
		helper.assertTrue(PyrokinesisHandlers.locked(p), "still locked just before 5 s");
		tick(p, 6);
		helper.assertFalse(PyrokinesisHandlers.locked(p), "after 5 s: moves work again");
		helper.succeed();
	}

	// ---------------- R: Fireball / Flamethrower ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireballAndFlamethrowerNumbers(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 1);
		var bolts = helper.getLevel().getEntitiesOfClass(PyroBoltEntity.class, p.getBoundingBox().inflate(4));
		helper.assertTrue(bolts.size() == 1 && !bolts.get(0).meteor(), "R throws a Fireball model");
		assertNear(helper, PyrokinesisHandlers.FIREBALL_DAMAGE, bolts.get(0).damage(), "cold Fireball: 14");
		bolts.get(0).discard();
		IronGolem g = dummy(helper, 2, 5);
		float before = g.getHealth();
		int hit = PyrokinesisHandlers.throwerBurst(p, helper.getLevel());
		helper.assertTrue(hit == 1, "the Flamethrower cone reaches 4 blocks out");
		float expect = PyrokinesisHandlers.THROWER_DAMAGE * PyrokinesisHandlers.heatMultiplier(PyrokinesisHandlers.HEAT_FIREBALL);
		assertNear(helper, expect, before - g.getHealth(), "a Flamethrower burst: 4 (+Heat)");
		helper.assertTrue(g.getRemainingFireTicks() > 0, "and sets it burning");
		// held: +10 Heat a second
		PyrokinesisHandlers.setHeat(p, 0.0f);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, 1, true);
		p.setShiftKeyDown(false);
		helper.assertTrue(PyrokinesisHandlers.throwing(p), "Shift+R held: the Flamethrower runs");
		tick(p, 20);
		assertNear(helper, 10.0f, PyrokinesisHandlers.heat(p), "the Flamethrower: +10 Heat a second");
		AbilityRouter.handleInput(p, 1, false);
		helper.assertFalse(PyrokinesisHandlers.throwing(p), "letting go stops it");
		helper.succeed();
	}

	// ---------------- G: Fire Whip / Fire Lasso ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void whipHitsAndPullsLassoBinds(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 7);
		float before = g.getHealth();
		press(p, 2); // G: Fire Whip
		assertNear(helper, PyrokinesisHandlers.WHIP_DAMAGE, before - g.getHealth(), "the Fire Whip: 12");
		helper.assertTrue(g.getDeltaMovement().z < -0.3, "and yanks the target in, v=" + g.getDeltaMovement());
		assertNear(helper, PyrokinesisHandlers.HEAT_WHIP, PyrokinesisHandlers.heat(p), "+10 Heat");
		g.setDeltaMovement(Vec3.ZERO);
		// Shift+G: the lasso
		IronGolem h = dummy(helper, 2, 5);
		g.discard();
		PyrokinesisHandlers.setHeat(p, 0.0f);
		shiftPress(p, 2);
		helper.assertTrue(Math.round(PyrokinesisHandlers.lassoId(p)) == h.getId(), "Shift+G lassoes the creature aimed at");
		assertNear(helper, PyrokinesisHandlers.HEAT_LASSO, PyrokinesisHandlers.heat(p), "+20 Heat");
		float h0 = h.getHealth();
		h.invulnerableTime = 0;
		PyrokinesisHandlers.lassoBurn(p, h);
		assertNear(helper, PyrokinesisHandlers.LASSO_DAMAGE_PER_SECOND, h0 - h.getHealth(), "the lasso burns 3 a second");
		Vec3 pinned = h.position();
		h.setDeltaMovement(0.5, 0, 0.5);
		tick(p, 2);
		helper.assertTrue(h.position().distanceTo(pinned) < 0.6 && Math.abs(h.getDeltaMovement().x) < 1.0e-3, "a lassoed creature can't move");
		tick(p, PyrokinesisHandlers.LASSO_TICKS);
		helper.assertTrue(PyrokinesisHandlers.lassoId(p) < 0.5f, "the lasso lets go after 3 s");
		helper.succeed();
	}

	// ---------------- X: Flame Dash / Jet Flight ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void flameDashAndJetFlight(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 3); // X: Flame Dash
		helper.assertTrue(PyrokinesisHandlers.dashing(p), "X bursts into a Flame Dash");
		helper.assertTrue(p.getDeltaMovement().z > 1.5, "10 blocks in 5 ticks along the aim, v=" + p.getDeltaMovement());
		assertNear(helper, PyrokinesisHandlers.HEAT_DASH, PyrokinesisHandlers.heat(p), "+10 Heat");
		tick(p, 6);
		helper.assertFalse(PyrokinesisHandlers.dashing(p), "the burst is over");
		PyrokinesisHandlers.setHeat(p, 0.0f);
		shiftPress(p, 3); // Shift+X: Jet Flight on
		helper.assertTrue(PyrokinesisHandlers.jetting(p) && p.getAbilities().flying && p.getAttachedOrElse(ModAttachments.HERO_FLYING, false),
				"Shift+X lights the jets: flying");
		p.setOnGround(false);
		tick(p, 20);
		assertNear(helper, PyrokinesisHandlers.HEAT_JET_PER_SECOND, PyrokinesisHandlers.heat(p), "the jets: +5 Heat a second, no cooling");
		helper.assertTrue(PyrokinesisHandlers.jetting(p), "the jets stay lit (a toggle)");
		shiftPress(p, 3);
		helper.assertFalse(PyrokinesisHandlers.jetting(p) || p.getAbilities().mayfly, "Shift+X again: the jets go out");
		helper.succeed();
	}

	// ---------------- Z: Meteor / Inferno ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void meteorChargesTo30(GameTestHelper helper) {
		assertNear(helper, 16.0f, PyrokinesisHandlers.meteorDamage(0.0f), "a tapped Meteor: 16");
		assertNear(helper, 30.0f, PyrokinesisHandlers.meteorDamage(1.0f), "fully charged: 30");
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5);
		float before = g.getHealth();
		int hit = PyrokinesisHandlers.meteorImpact(p, helper.getLevel(), g.position().add(1.5, 0, 0), 16.0f, 1.0f, null);
		helper.assertTrue(hit == 1, "the blast reaches 1.5 blocks out");
		assertNear(helper, 16.0f, before - g.getHealth(), "the Meteor blast");
		g.discard();
		AbilityRouter.handleInput(p, 4, true); // hold Z
		helper.assertTrue(PyrokinesisHandlers.charging(p), "holding Z charges a Meteor");
		helper.runAfterDelay(PyrokinesisHandlers.METEOR_CHARGE_TICKS + 2, () -> {
			AbilityRouter.handleInput(p, 4, false);
			var bolts = helper.getLevel().getEntitiesOfClass(PyroBoltEntity.class, p.getBoundingBox().inflate(5));
			helper.assertTrue(bolts.size() == 1 && bolts.get(0).meteor(), "letting go throws the Meteor");
			assertNear(helper, 30.0f, bolts.get(0).damage(), "a full 2 s charge: 30");
			assertNear(helper, PyrokinesisHandlers.HEAT_METEOR, PyrokinesisHandlers.heat(p), "+25 Heat");
			bolts.get(0).discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void infernoDumpsTheHeat(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 6);
		PyrokinesisHandlers.setHeat(p, 5.0f);
		shiftPress(p, 4);
		helper.assertTrue(PyrokinesisHandlers.heat(p) > 4.0f, "Inferno needs some Heat (10)");
		PyrokinesisHandlers.setHeat(p, 50.0f);
		float before = g.getHealth();
		shiftPress(p, 4); // Shift+Z: Inferno
		assertNear(helper, 15.0f, before - g.getHealth(), "50 Heat: 15 in 8 blocks (30 at full)");
		helper.assertTrue(PyrokinesisHandlers.heat(p) < 0.01f, "Heat to 0");
		assertNear(helper, 30.0f, PyrokinesisHandlers.infernoDamage(100.0f), "full Heat: 30");
		helper.succeed();
	}

	// ---------------- V: Flame Wall / Flame Body ----------------

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void flameWallBurnsCrossersAndArrows(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		press(p, 5); // V: Flame Wall
		var walls = helper.getLevel().getEntitiesOfClass(PyroFxEntity.class, p.getBoundingBox().inflate(6), w -> w.kind() == PyroFxEntity.WALL);
		helper.assertTrue(walls.size() == 1, "V raises a Flame Wall");
		PyroFxEntity wall = walls.get(0);
		assertNear(helper, PyrokinesisHandlers.HEAT_WALL, PyrokinesisHandlers.heat(p), "+15 Heat");
		helper.assertTrue(Math.abs(wall.acrossWall(p.position()) + 3.0) < 0.3, "3 blocks ahead, across the aim");
		IronGolem g = dummy(helper, 2, 4);
		g.setPos(wall.getX(), wall.getY(), wall.getZ());
		float before = g.getHealth();
		int hit = PyrokinesisHandlers.wallBurn(p, helper.getLevel(), wall, PyrokinesisHandlers.WALL_DAMAGE);
		helper.assertTrue(hit == 1, "what stands in the wall burns");
		assertNear(helper, PyrokinesisHandlers.WALL_DAMAGE, before - g.getHealth(), "3 a half second");
		g.discard();
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setPos(wall.getX() + 1.5, wall.getY() + 1.2, wall.getZ());
		arrow.setNoGravity(true);
		helper.getLevel().addFreshEntity(arrow);
		helper.runAfterDelay(3, () -> {
			helper.assertFalse(arrow.isAlive(), "arrows burn up in the wall");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flameBodyAndFireImmunity(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 2);
		var melee = p.damageSources().mobAttack(g);
		assertNear(helper, 1.0f, PyrokinesisHandlers.damageFactor(p, melee), "no melee cut without Flame Body");
		shiftPress(p, 5);
		assertNear(helper, PyrokinesisHandlers.BODY_MELEE_FACTOR, PyrokinesisHandlers.damageFactor(p, melee), "Flame Body: -30% melee");
		helper.assertTrue(PyrokinesisHandlers.damageFactor(p, p.damageSources().lava()) < 0.0f, "lava immune");
		helper.assertTrue(PyrokinesisHandlers.damageFactor(p, p.damageSources().onFire()) < 0.0f, "fire immune");
		float hp = p.getHealth();
		p.hurt(p.damageSources().lava(), 4.0f);
		assertNear(helper, hp, p.getHealth(), "lava does nothing");
		p.setRemainingFireTicks(200);
		tick(p, 1);
		helper.assertTrue(p.getRemainingFireTicks() <= 0, "a pyrokinetic never burns");
		helper.succeed();
	}

	// ---------------- C: Heat Wave / Cauterize ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void heatWavePushesMeltsAndSmelts(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		IronGolem g = dummy(helper, 2, 5);
		BlockPos snow = helper.absolutePos(new BlockPos(3, 2, 4));
		helper.getLevel().setBlock(snow, Blocks.SNOW_BLOCK.defaultBlockState(), 3);
		ItemEntity beef = new ItemEntity(helper.getLevel(), p.getX(), p.getY() + 1.0, p.getZ() + 3.0, new ItemStack(Items.BEEF, 5));
		beef.setNoGravity(true);
		helper.getLevel().addFreshEntity(beef);
		press(p, 6); // C: Heat Wave
		helper.assertTrue(g.getDeltaMovement().z > 0.2, "the wave throws what is in the cone back, v=" + g.getDeltaMovement());
		assertNear(helper, PyrokinesisHandlers.HEAT_WAVE, PyrokinesisHandlers.heat(p), "+10 Heat");
		helper.assertTrue(beef.getItem().is(Items.COOKED_BEEF) && beef.getItem().getCount() == 5, "dropped food is cooked, the whole stack");
		helper.assertTrue(helper.getLevel().getBlockState(snow).isAir(), "snow melts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cauterizeBurnsHeatToHeal(GameTestHelper helper) {
		ServerPlayer p = hero(helper);
		p.setHealth(4.0f);
		PyrokinesisHandlers.setHeat(p, 30.0f);
		shiftPress(p, 6);
		assertNear(helper, 4.0f, p.getHealth(), "needs 40 Heat");
		PyrokinesisHandlers.setHeat(p, 50.0f);
		shiftPress(p, 6); // Shift+C: Cauterize
		assertNear(helper, 20.0f, p.getHealth(), "heals 8 hearts");
		assertNear(helper, 10.0f, PyrokinesisHandlers.heat(p), "burns 40 Heat");
		helper.assertTrue(res(p, "cauterize_cd") > 19 * 20, "20 s cooldown");
		p.setHealth(4.0f);
		PyrokinesisHandlers.setHeat(p, 60.0f);
		shiftPress(p, 6);
		assertNear(helper, 4.0f, p.getHealth(), "not again while it cools down");
		helper.succeed();
	}
}
