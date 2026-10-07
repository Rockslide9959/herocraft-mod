package com.projecthero.mod.gametest;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.nova.Nova;
import com.projecthero.mod.nova.NovaAbilities;
import com.projecthero.mod.nova.NovaAbilityManager;
import com.projecthero.mod.nova.NovaCombat;
import com.projecthero.mod.nova.NovaConfig;
import com.projecthero.mod.nova.NovaFlight;
import com.projecthero.mod.nova.entity.NovaCenturionEntity;
import com.projecthero.mod.nova.item.NovaCorpsHelmetItem;
import com.projecthero.mod.nova.item.NovaItems;
import com.projecthero.mod.squad.Squad;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.13 Nova (Richard Rider): the grant and the one-Primary rule, the uniform on / off and what it switches on, the
 * flight speeds (measured through the shared directional-flight model), the Nova Force refill / fast-flight drain / every
 * move's cost, each move's core effect, the ultimate's full-bar rule and cooldown, squad safety and the Centurion's
 * hand-off. Mock players are not ticked by the server, so tests that run over time drive {@link Nova#tick} themselves
 * ({@link #pump}). Zombies need a non-peaceful difficulty, so those tests set HARD.
 */
public class NovaGameTests implements FabricGameTest {
	private static final float EPS = 0.05f;

	// ---------------------------------------------------------------- helpers

	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
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

	/** A suited Nova facing +Z. */
	private static ServerPlayer nova(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		Nova.grant(p);
		Nova.suitUp(p);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Nova.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, boolean noAi, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(noAi);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0); // whole numbers: no natural armour
		// a pumpkin on the head: the daylight never sets it burning (that would add 1s to every damage reading)
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static Squad squad(GameTestHelper helper, ServerPlayer leader, ServerPlayer mate) {
		SquadManager squads = SquadManager.get(helper.getLevel().getServer());
		Squad squad = squads.create("nova" + leader.getUUID().toString().substring(0, 8), leader.getUUID());
		squads.addMember(squad, mate.getUUID());
		return squad;
	}

	private static void spends(GameTestHelper helper, ServerPlayer p, Runnable move, float expected, String what) {
		Nova.setForce(p, NovaConfig.FORCE_MAX);
		move.run();
		float spent = NovaConfig.FORCE_MAX - Nova.force(p);
		helper.assertTrue(Math.abs(spent - expected) < EPS, what + " costs " + expected + ", spent " + spent);
	}

	// ---------------------------------------------------------------- the grant and the one-Primary rule

	@GameTest(template = EMPTY_STRUCTURE)
	public void novaReplacesThePrimaryAndIsReplaced(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("nova") && PowerGrants.HERO_TIER_KEYS.contains("nova"), "nova is a Hero-Tier key");
		Kryptonian.grant(p);
		helper.assertTrue(Nova.grant(p), "the Nova Force is granted");
		helper.assertTrue(Nova.hasPower(p) && !Kryptonian.hasPower(p), "Nova replaced the Kryptonian (one Primary)");
		helper.assertTrue(HeroTiers.heroCount(p) == 1 && HeroTiers.holdsHero(p, "nova"), "exactly one hero held");
		helper.assertFalse(Nova.grant(p), "a second grant does nothing");
		helper.assertTrue(Nova.force(p) == NovaConfig.FORCE_MAX, "starts with a full bar");
		helper.assertFalse(Nova.suited(p), "the uniform starts off");
		Kryptonian.grant(p);
		helper.assertTrue(Kryptonian.hasPower(p) && !Nova.hasPower(p), "a new hero replaces Nova");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the uniform (H)

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void uniformSwitchesThePowersOnAndOff(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		Nova.grant(p);
		NovaAbilities.pulse(p);
		helper.assertTrue(Nova.force(p) == NovaConfig.FORCE_MAX, "no moves without the uniform");
		NovaFlight.toggle(p);
		helper.assertFalse(Nova.isFlying(p), "no flight without the uniform");
		helper.assertTrue(Nova.damageTakenFactor(p) == 1.0f, "no damage reduction without the uniform");

		Nova.toggleSuit(p); // H
		helper.assertTrue(Nova.suited(p), "H puts the uniform on");
		helper.assertTrue(Math.abs(Nova.damageTakenFactor(p) - 0.4f) < 1.0e-4f, "60% damage reduction while suited");
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 10f);
		helper.assertTrue(Math.abs(p.getHealth() - 16f) < EPS, "10 damage becomes 4, health " + p.getHealth());
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 10f);
		helper.assertTrue(Math.abs(p.getHealth() - 16f) < EPS, "no fall damage while suited, health " + p.getHealth());
		Nova.toggleSuit(p); // a bounce of the key
		helper.assertTrue(Nova.suited(p), "H is debounced");
		helper.runAfterDelay(NovaConfig.SUIT_TOGGLE_COOLDOWN + 2, () -> {
			Nova.toggleSuit(p);
			helper.assertFalse(Nova.suited(p), "H again takes it off");
			helper.assertTrue(Nova.damageTakenFactor(p) == 1.0f, "and the reduction goes with it");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- flight

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightIsTwentyAndFortyBlocksASecond(GameTestHelper helper) {
		for (boolean sprint : new boolean[] { false, true }) {
			DirectionalFlightModel.Tune tune = DirectionalFlightModel.nova(sprint);
			Vec3 v = Vec3.ZERO;
			Vec3 look = new Vec3(0, 0, 1);
			for (int i = 0; i < 200; i++) {
				Vec3 wanted = DirectionalFlightModel.wantedVelocity(look, 0f, 1f, 0f, 0, tune);
				v = DirectionalFlightModel.step(v, wanted, true, tune, false);
			}
			double bps = v.length() * 20.0;
			double expect = sprint ? 40.0 : 20.0;
			helper.assertTrue(Math.abs(bps - expect) < 0.5, (sprint ? "sprint " : "") + "flight settles at " + expect + " b/s, got " + bps);
		}
		ServerPlayer p = nova(helper, 2.5, 4.0, 2.5);
		NovaFlight.start(p);
		helper.assertTrue(Nova.isFlying(p) && p.getAbilities().mayfly && p.getAbilities().flying, "double-tap takes off");
		NovaFlight.toggle(p);
		helper.assertFalse(Nova.isFlying(p), "double-tap again drops out");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Nova Force

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300)
	public void forceRefillsFourASecondAndFastFlightDrainsTwo(GameTestHelper helper) {
		ServerPlayer p = nova(helper, 2.5, 4.0, 2.5);
		Nova.setForce(p, 50f);
		boolean[] fast = { false };
		helper.onEachTick(() -> {
			if (fast[0]) {
				NovaFlight.setMeasuredSpeedForTests(p, 2.0);
			}
			p.tickCount++;
			Nova.tick(p);
		});
		helper.runAfterDelay(100, () -> {
			float f = Nova.force(p);
			helper.assertTrue(f >= 69f && f <= 71f, "5 s of refill: 50 -> 70, got " + f);
			Nova.setForce(p, 50f);
			NovaFlight.start(p);
			fast[0] = true;
			helper.runAfterDelay(100, () -> {
				float g = Nova.force(p);
				helper.assertTrue(Nova.isFlying(p), "still flying");
				helper.assertTrue(g >= 59f && g <= 61f, "5 s of fast flight: +4 -2 a second -> 60, got " + g);
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMoveCostsItsForce(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 2.5, 2.0, 2.5);
		zombie(helper, 3.5, 2.0, 4.0, true, 100);
		spends(helper, p, () -> NovaAbilities.volley(p), 20f, "Shift+R Bolt Volley");
		spends(helper, p, () -> NovaAbilities.pulse(p), 20f, "G Gravimetric Pulse");
		spends(helper, p, () -> NovaAbilities.shield(p), 25f, "Z Force Shield");
		spends(helper, p, () -> NovaAbilities.cometDash(p), 15f, "X Comet Dash");
		spends(helper, p, () -> NovaAbilities.orbitalLaunch(p), 30f, "Shift+X Orbital Launch");
		spends(helper, p, () -> NovaAbilities.gravityWell(p), 30f, "C Gravity Well");
		spends(helper, p, () -> NovaAbilities.gravityLock(p), 35f, "Shift+C Gravity Lock");
		spends(helper, p, () -> NovaAbilities.scan(p), 15f, "V Worldmind Scan");
		spends(helper, p, () -> NovaAbilities.forceTransfer(p), 40f, "Shift+V Nova Force Transfer");
		ServerPlayer high = nova(helper, 5.5, 6.0, 2.5);
		spends(helper, high, () -> NovaAbilities.gravitySlam(high), 25f, "Shift+G Gravity Slam (in the air)");
		ServerPlayer full = nova(helper, 5.5, 2.0, 5.5);
		spends(helper, full, () -> NovaAbilities.overload(full), 100f, "Shift+Z NOVA OVERLOAD (all of it)");
		helper.assertTrue(NovaAbilityManager.cost(NovaAbilities.BLAST) == 6f, "Nova Blast costs 6 a second");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R / Shift+R

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void novaBlastDealsEightASecond(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 2.5, 2.0, 1.5);
		Zombie z = zombie(helper, 2.5, 2.0, 4.5, true, 100);
		pump(helper, p);
		NovaAbilityManager.handle(p, AbilitySlot.SLOT_1, true); // R held
		helper.assertTrue(Nova.blasting(p), "R opens the beam");
		helper.runAfterDelay(41, () -> {
			NovaAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
			float lost = 100f - z.getHealth();
			helper.assertTrue(lost >= 15.5f && lost <= 16.5f, "2 s of beam = 16 damage, got " + lost);
			helper.assertFalse(Nova.blasting(p), "letting go of R ends it");
			helper.assertTrue(Nova.cooldownRemaining(p, NovaAbilities.BLAST) > 0, "then a short cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void boltVolleyHomesIn(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 1.5, 2.0, 1.5);
		Zombie z = zombie(helper, 5.5, 2.0, 5.5, true, 100); // off to the side: the bolts must turn
		pump(helper, p);
		NovaAbilities.volley(p);
		helper.assertTrue(NovaAbilities.running(p, NovaAbilities.VOLLEY), "five bolts are in the air");
		helper.runAfterDelay(50, () -> {
			float lost = 100f - z.getHealth();
			helper.assertTrue(lost >= 6f, "the homing bolts found it (6 each), lost " + lost);
			helper.assertTrue(lost <= 5 * NovaConfig.VOLLEY_DAMAGE + 0.1f, "at most five bolts, lost " + lost);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- G / Shift+G

	@GameTest(template = EMPTY_STRUCTURE)
	public void gravimetricPulseHitsAndKnocksUp(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 2.5, 2.0, 2.5);
		Zombie near = zombie(helper, 5.5, 2.0, 2.5, true, 100);
		NovaAbilities.pulse(p);
		float lost = 100f - near.getHealth();
		helper.assertTrue(lost >= 7f && lost <= 10.01f, "10 damage (70% at the edge), lost " + lost);
		helper.assertTrue(near.getDeltaMovement().y > 0.3, "knocked up, vy " + near.getDeltaMovement().y);
		helper.assertTrue(NovaConfig.PULSE_RADIUS == 6.0 && NovaConfig.PULSE_DAMAGE == 10f, "6 blocks, 10 damage");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void gravitySlamScalesWithTheDrop(GameTestHelper helper) {
		helper.assertTrue(NovaAbilities.slamDamage(0) == 6f && Math.abs(NovaAbilities.slamDamage(10) - 12f) < 1.0e-4f
				&& NovaAbilities.slamDamage(100) == 18f, "6 + 0.6 a block, capped at 18");
		floor(helper);
		ServerPlayer grounded = nova(helper, 5.5, 2.0, 5.5);
		grounded.setOnGround(true);
		Nova.setForce(grounded, 100f);
		NovaAbilities.gravitySlam(grounded);
		helper.assertTrue(Nova.force(grounded) == 100f, "on the ground it does nothing");
		ServerPlayer p = nova(helper, 2.5, 7.0, 2.5);
		Zombie z = zombie(helper, 4.5, 2.0, 2.5, true, 100);
		pump(helper, p);
		NovaAbilities.gravitySlam(p);
		helper.assertTrue(NovaAbilities.running(p, NovaAbilities.SLAM), "the dive starts");
		// the client would fall: land him on the floor
		Vec3 ground = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.setPos(ground.x, ground.y, ground.z);
		helper.runAfterDelay(3, () -> {
			helper.assertFalse(NovaAbilities.running(p, NovaAbilities.SLAM), "landed");
			float lost = 100f - z.getHealth();
			float full = NovaAbilities.slamDamage(5.0);
			helper.assertTrue(lost >= full * 0.69f && lost <= full + 0.01f, "a 5-block drop hits for about " + full + ", lost " + lost);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void forceShieldAbsorbsMeleeAndProjectiles(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 2.5, 2.0, 2.5);
		Zombie z = zombie(helper, 2.5, 2.0, 4.0, true, 20);
		pump(helper, p);
		NovaAbilities.shield(p);
		helper.assertTrue(Nova.shieldUp(p), "the bubble is up");
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(z), 10f);
		helper.assertTrue(p.getHealth() == 20f, "a melee blow is absorbed, health " + p.getHealth());
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 3.0, 3.6));
		arrow.moveTo(at.x, at.y, at.z);
		arrow.setOwner(z);
		arrow.setDeltaMovement(0, 0, -0.5);
		helper.getLevel().addFreshEntity(arrow);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(arrow.isRemoved(), "an arrow inside the bubble is absorbed");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 320)
	public void overloadNeedsAFullBarThenBursts(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 2.5, 2.0, 2.5);
		Zombie z = zombie(helper, 6.5, 2.0, 2.5, true, 200);
		pump(helper, p);
		Nova.setForce(p, 99f);
		NovaAbilities.overload(p);
		helper.assertFalse(Nova.overloaded(p), "99 is not a full bar");
		helper.assertTrue(Nova.force(p) == 99f, "nothing spent");
		Nova.setForce(p, 100f);
		NovaAbilities.overload(p);
		helper.assertTrue(Nova.overloaded(p), "a full bar starts NOVA OVERLOAD");
		helper.assertTrue(Nova.force(p) == 0f, "it takes all of it");
		helper.assertTrue(Nova.cooldownRemaining(p, NovaAbilities.OVERLOAD) >= NovaConfig.OVERLOAD_COOLDOWN - 1, "90 s cooldown");
		helper.assertTrue(Nova.damageMultiplier(p) == 1.5f, "+50% strength");
		NovaAbilities.pulse(p);
		helper.assertTrue(Nova.cooldownRemaining(p, NovaAbilities.PULSE) > 0, "moves still fire with an empty bar (free)");
		helper.assertTrue(Math.abs(Nova.cooldownRemaining(p, NovaAbilities.PULSE) - NovaConfig.PULSE_COOLDOWN / 2) <= 1,
				"cooldowns started in the Overload are halved");
		float afterPulse = z.getHealth();
		helper.runAfterDelay(NovaConfig.OVERLOAD_TICKS + 5, () -> {
			helper.assertFalse(Nova.overloaded(p), "10 s later it is over");
			float burst = afterPulse - z.getHealth();
			helper.assertTrue(burst >= NovaConfig.OVERLOAD_BURST_DAMAGE * 0.69f, "the closing nova burst hits for 30 (falloff), dealt " + burst);
			Nova.setForce(p, 100f);
			NovaAbilities.overload(p);
			helper.assertFalse(Nova.overloaded(p), "still on its 90 s cooldown");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X / Shift+X

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void cometDashRamsEverythingInItsPath(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 2.5, 2.0, 0.6);
		Zombie first = zombie(helper, 2.5, 2.0, 2.4, true, 100);
		Zombie second = zombie(helper, 2.8, 2.0, 6.2, true, 100);
		pump(helper, p);
		NovaAbilities.cometDash(p);
		helper.assertTrue(Math.abs((100f - first.getHealth()) - NovaConfig.DASH_DAMAGE) < 0.1f, "the first in line takes 14");
		// the client would fly along: carry him down the path
		Vec3 end = helper.absoluteVec(new Vec3(2.5, 2.0, 7.2));
		p.setPos(end.x, end.y, end.z);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Math.abs((100f - second.getHealth()) - NovaConfig.DASH_DAMAGE) < 0.1f, "so does the next, lost "
					+ (100f - second.getHealth()));
			helper.assertTrue(Math.abs((100f - first.getHealth()) - NovaConfig.DASH_DAMAGE) < 0.1f, "each only once");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void orbitalLaunchCarriesThenSpikes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 2.5, 2.0, 2.5);
		Zombie z = zombie(helper, 4.5, 2.0, 2.5, false, 200);
		double startY = z.getY();
		pump(helper, p);
		NovaAbilities.orbitalLaunch(p);
		helper.assertTrue(NovaAbilities.launchVictim(p) == z, "the nearest enemy is grabbed");
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(z.getY() >= startY + 18.0, "carried up toward 30 blocks, at +" + (z.getY() - startY));
			helper.succeedWhen(() -> {
				helper.assertTrue(NovaAbilities.launchVictim(p) == null, "spiked into the ground");
				helper.assertTrue(200f - z.getHealth() >= NovaConfig.LAUNCH_SPIKE_DAMAGE, "at least 20 on impact, lost " + (200f - z.getHealth()));
			});
		});
	}

	// ---------------------------------------------------------------- C / Shift+C

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void gravityWellPullsThenCrushes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 1.5, 2.0, 1.5);
		p.setXRot(55f); // looking down at the floor a couple of blocks ahead
		Zombie z = zombie(helper, 5.5, 2.0, 5.5, false, 100);
		pump(helper, p);
		NovaAbilities.gravityWell(p);
		var s = Nova.state(p);
		helper.assertTrue(s.wellPos.size() == 3 && s.wellUntil > 0, "the singularity opens");
		Vec3 c = new Vec3(s.wellPos.get(0), s.wellPos.get(1), s.wellPos.get(2));
		double d0 = z.position().distanceTo(c);
		helper.runAfterDelay(20, () -> {
			double d1 = z.position().distanceTo(c);
			helper.assertTrue(d1 < d0 - 1.0, "dragged in: " + d0 + " -> " + d1);
			helper.runAfterDelay(45, () -> {
				helper.assertTrue(Nova.state(p).wellUntil == 0L, "it collapsed after 3 s");
				float lost = 100f - z.getHealth();
				helper.assertTrue(lost >= NovaConfig.WELL_CRUSH_DAMAGE * 0.69f, "crushed for 20 (falloff), lost " + lost);
				helper.succeed();
			});
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160)
	public void gravityLockLiftsAndFreezes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 1.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, false, 100);
		double y0 = z.getY();
		pump(helper, p);
		NovaAbilities.gravityLock(p);
		helper.assertTrue(NovaAbilities.isLocked(z), "the mob is caught");
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(Math.abs(z.getY() - (y0 + NovaConfig.LOCK_LIFT)) < 0.25, "lifted 3 and frozen, at +" + (z.getY() - y0));
			helper.runAfterDelay(NovaConfig.LOCK_TICKS - 10, () -> {
				helper.assertFalse(NovaAbilities.isLocked(z), "let go after 4 s");
				helper.assertFalse(z.isNoGravity(), "and it falls again");
				helper.succeed();
			});
		});
	}

	// ---------------------------------------------------------------- V / Shift+V

	@GameTest(template = EMPTY_STRUCTURE)
	public void worldmindScanMarksTheStrongest(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 1.5, 2.0, 1.5);
		Zombie weak = zombie(helper, 4.5, 2.0, 2.5, true, 40);
		Zombie strong = zombie(helper, 2.5, 2.0, 5.5, true, 100);
		NovaAbilities.scan(p);
		helper.assertTrue(NovaAbilities.isMarked(strong) && !NovaAbilities.isMarked(weak), "the strongest is marked");
		strong.invulnerableTime = 0;
		strong.hurt(helper.getLevel().damageSources().generic(), 8f);
		helper.assertTrue(Math.abs((100f - strong.getHealth()) - 10f) < EPS, "the mark adds 25%: 8 -> 10, lost " + (100f - strong.getHealth()));
		weak.invulnerableTime = 0;
		weak.hurt(helper.getLevel().damageSources().generic(), 8f);
		helper.assertTrue(Math.abs((40f - weak.getHealth()) - 8f) < EPS, "the unmarked one takes the normal 8");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void forceTransferHealsTheSquadOnly(GameTestHelper helper) {
		ServerPlayer p = nova(helper, 1.5, 2.0, 1.5);
		ServerPlayer mate = mock(helper, 4.5, 2.0, 1.5);
		ServerPlayer stranger = mock(helper, 1.5, 2.0, 4.5);
		Squad squad = squad(helper, p, mate);
		p.setHealth(6f);
		mate.setHealth(6f);
		stranger.setHealth(6f);
		NovaAbilities.forceTransfer(p);
		helper.assertTrue(p.getHealth() == 18f, "6 hearts for the Nova, now " + p.getHealth());
		helper.assertTrue(mate.getHealth() == 18f, "6 hearts for the squadmate, now " + mate.getHealth());
		helper.assertTrue(stranger.getHealth() == 6f, "nothing for a stranger");
		helper.assertTrue(Nova.force(p) == 60f, "40% of the bar");
		SquadManager.get(helper.getLevel().getServer()).disband(squad);
		helper.succeed();
	}

	// ---------------------------------------------------------------- squad safety

	@GameTest(template = EMPTY_STRUCTURE)
	public void squadmatesAndTheirPetsAreSpared(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = nova(helper, 2.5, 2.0, 2.5);
		ServerPlayer mate = mock(helper, 4.5, 2.0, 2.5);
		Squad squad = squad(helper, p, mate);
		Wolf matePet = EntityType.WOLF.create(helper.getLevel());
		Vec3 w = helper.absoluteVec(new Vec3(2.5, 2.0, 4.5));
		matePet.moveTo(w.x, w.y, w.z);
		matePet.tame(mate);
		helper.getLevel().addFreshEntity(matePet);
		Wolf ownPet = EntityType.WOLF.create(helper.getLevel());
		Vec3 o = helper.absoluteVec(new Vec3(0.5, 2.0, 2.5));
		ownPet.moveTo(o.x, o.y, o.z);
		ownPet.tame(p);
		helper.getLevel().addFreshEntity(ownPet);
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Husk husk = EntityType.HUSK.create(helper.getLevel());
		Vec3 h = helper.absoluteVec(new Vec3(2.5, 2.0, 0.5));
		husk.moveTo(h.x, h.y, h.z);
		husk.setNoAi(true);
		helper.getLevel().addFreshEntity(husk);

		helper.assertFalse(NovaCombat.isTarget(p, mate), "never a squadmate");
		helper.assertFalse(NovaCombat.isTarget(p, matePet), "never a squadmate's pet");
		helper.assertFalse(NovaCombat.isTarget(p, ownPet), "never his own pet");
		helper.assertTrue(NovaCombat.isTarget(p, husk), "an enemy still is");
		float mateHp = mate.getHealth();
		float petHp = matePet.getHealth();
		NovaAbilities.pulse(p);
		helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the pulse hits the husk");
		helper.assertTrue(mate.getHealth() == mateHp && mate.getDeltaMovement().lengthSqr() < 1.0e-4, "the squadmate is untouched");
		helper.assertTrue(matePet.getHealth() == petHp && matePet.getDeltaMovement().y < 0.1, "the squadmate's pet is untouched");
		NovaAbilities.gravityLock(p);
		helper.assertFalse(NovaAbilities.isLocked(matePet) || NovaAbilities.isLocked(ownPet), "pets are never lifted");
		helper.assertTrue(NovaAbilities.isLocked(husk), "the husk is");
		NovaAbilities.clear(p);
		SquadManager.get(helper.getLevel().getServer()).disband(squad);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Centurion and the helmet

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void centurionHandsOverTheHelmetAndFades(GameTestHelper helper) {
		floor(helper);
		NovaCenturionEntity centurion = NovaItems.CENTURION.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
		centurion.moveTo(at.x, at.y, at.z);
		helper.getLevel().addFreshEntity(centurion);
		helper.assertFalse(centurion.hurt(helper.getLevel().damageSources().generic(), 100f), "he cannot be hurt");

		ServerPlayer already = nova(helper, 3.5, 2.0, 4.5);
		centurion.interact(already, InteractionHand.MAIN_HAND);
		helper.assertFalse(centurion.fading(), "someone who is already Nova gets a line, not the helmet");
		helper.assertFalse(already.getInventory().contains(new ItemStack(NovaItems.NOVA_CORPS_HELMET)), "no helmet for him");

		ServerPlayer p = mock(helper, 2.5, 2.0, 4.5);
		centurion.interact(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(centurion.fading(), "after the hand-off he fades");
		int slot = p.getInventory().findSlotMatchingItem(new ItemStack(NovaItems.NOVA_CORPS_HELMET));
		helper.assertTrue(slot >= 0, "the Nova Corps Helmet was handed over");
		ItemStack helmet = p.getInventory().getItem(slot);
		helper.assertTrue(NovaCorpsHelmetItem.bond(p, helmet), "using the helmet grants Nova");
		helper.assertTrue(Nova.hasPower(p) && helmet.isEmpty(), "the power is his and the helmet is used up");
		helper.runAfterDelay(NovaConfig.CENTURION_FADE_TICKS + 5, () -> {
			helper.assertTrue(centurion.isRemoved(), "the Centurion has faded away");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void crashSiteIsRegistered(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
		helper.assertTrue(structures.containsKey(com.projecthero.mod.ProjectHeroMod.id("nova_pod_site")), "the structure is registered");
		var sets = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
		helper.assertTrue(sets.get(ResourceKey.create(Registries.STRUCTURE_SET, com.projecthero.mod.ProjectHeroMod.id("nova_pod_site"))) != null,
				"and placed in the world (structure set)");
		helper.succeed();
	}
}
