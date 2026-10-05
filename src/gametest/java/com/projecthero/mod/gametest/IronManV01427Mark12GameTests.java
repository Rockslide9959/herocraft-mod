package com.projecthero.mod.gametest;

import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.ability.IronManDash;
import com.projecthero.mod.ironman.ability.IronManFlares;
import com.projecthero.mod.ironman.ability.IronManSonicClap;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.27 (agent C): the Mark 1 / Mark 2 kits, the Mark III stat + repulsor retune, the 50/50 integrity split with
 * arrow + fire immunity, and the shared repulsor dash / sonic clap / flares / supersonic boost.
 */
public class IronManV01427Mark12GameTests implements FabricGameTest {

	private static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		p.setYRot(0f);
		p.setYHeadRot(0f);
		p.setXRot(0f);
		TonyStark.grant(p);
		for (ArmorItem.Type t : ArmorItem.Type.values()) {
			var item = IronManItems.armor(suitId, t);
			if (item != null) {
				p.setItemSlot(IronManSuitUpManager.slotFor(t), new ItemStack(item));
			}
		}
		IronManEnergy.setEnergy(p, suitId, IronManEnergy.capacity(suitId));
		IronManEnergy.setIntegrity(p, suitId, IronManEnergy.maxIntegrity(suitId));
		return p;
	}

	/** A fresh ServerPlayer ignores damage for its first 60 ticks -- clear that so hits land. */
	private static void clearSpawnInvulnerability(ServerPlayer p) {
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** A no-AI zombie {@code distance} blocks straight ahead of the player (who faces +Z). */
	private static Zombie zombieAhead(GameTestHelper h, ServerPlayer p, double distance) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie z = EntityType.ZOMBIE.create(h.getLevel());
		z.moveTo(p.getX(), p.getY(), p.getZ() + distance);
		z.setNoAi(true);
		h.getLevel().addFreshEntity(z);
		return z;
	}

	// ---------------------------------------------------------------- stats

	@GameTest(template = EMPTY_STRUCTURE)
	public void markOneStats(GameTestHelper h) {
		IronManSuit m = IronManSuits.MARK_1;
		h.assertTrue(m.energyCapacity() == 500f, "Mark 1 energy 500");
		h.assertTrue(m.energyRegenPerSecond() == 2f, "Mark 1 regen 2/s");
		h.assertTrue(m.maxIntegrity() == 750f, "Mark 1 integrity 750");
		h.assertTrue(m.integrityPlayerShare() == 0.5f, "Mark 1 splits hits 50/50");
		h.assertTrue(m.arrowFireImmune(), "Mark 1 is immune to arrows and fire");
		h.assertTrue(m.strengthBonus() == 6f, "Mark 1 melee +6");
		h.assertFalse(m.autoFeed(), "Mark 1 doesn't auto-feed");
		h.assertFalse(m.waterBreathing() || m.airTankSeconds() > 0, "Mark 1 can't breathe underwater");
		h.assertTrue(IronManItems.armor("mark_1", ArmorItem.Type.CHESTPLATE).getDefense() == 8, "Mark 1 is diamond-level armour");
		h.assertTrue(IronManAbilities.PUNCH_DAMAGE == 15f && IronManAbilities.PUNCH_ENERGY_COST == 10f
				&& IronManAbilities.PUNCH_COOLDOWN_TICKS == 20, "Strong Punch 15 dmg / 10 energy / 1 s");
		h.assertTrue(IronManAbilities.MARK_1_ROCKET_DAMAGE == 30f && IronManAbilities.MARK_1_ROCKET_COOLDOWN_TICKS == 200,
				"Rocket 30 dmg / 10 s");
		h.assertTrue(IronManAbilities.flamethrowerMaxHeat(m) == 100f && m.flamethrowerHeatPerSecond() == 5f
				&& m.flamethrowerVentPerSecond() == 5f && m.flamethrowerVentDelayTicks() == 60
				&& m.flamethrowerEnergyPerSecond() == 6f && m.flamethrowerDamagePerSecond() == 8f,
				"Flamethrower: 8 dmg/s, 6 energy/s, heat +5/s to 100, seeps 5/s after 3 s");
		h.assertTrue(IronManAbilities.TIMED_FLIGHT_TICKS == 400 && m.flightCruiseMps() == 8.0 && !m.sprintFlight()
				&& m.hoverFloor() == 0.5, "Flight burst 20 s, 8 b/s, no sprint, 0.5 hover floor");
		h.assertTrue(m.mobHighlightEnergy() == 10f, "V costs 10");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markTwoAndThreeStats(GameTestHelper h) {
		IronManSuit m2 = IronManSuits.MARK_2;
		h.assertTrue(m2.energyCapacity() == 1250f && m2.energyRegenPerSecond() == 3f && m2.maxIntegrity() == 1000f,
				"Mark 2 1250 energy, 3/s, 1000 integrity");
		h.assertTrue(m2.integrityPlayerShare() == 0.5f && m2.arrowFireImmune() && m2.resistanceAmplifier() == 0
				&& m2.strengthBonus() == 6f && !m2.autoFeed() && m2.airTankSeconds() == 0 && !m2.waterBreathing(),
				"Mark 2 split / immune / Resistance I / +6 / no feed / no air");
		h.assertTrue(IronManTargeting.hasTargeting(m2), "Mark 2 has the targeting system");
		h.assertTrue(m2.repulsorDamage() == 10f && m2.repulsorTapEnergy() == 10f && m2.repulsorTapCooldownTicks() == 20
				&& m2.repulsorWindupTicks() == 20, "Mark 2 repulsor 10 dmg, 1 s windup, 1 s cd, 10 energy");
		h.assertTrue(m2.chargedRepulsorDamage() == 18f && m2.chargedRepulsorEnergy() == 50f
				&& m2.chargedRepulsorCooldownTicks() == 60 && m2.chargeHoldTicks() == 20, "Mark 2 charged 18 / 50 / 3 s, hold 1 s");
		h.assertTrue(m2.dashDamage() == 15f && m2.dashEnergy() == 50f && m2.dashCooldownTicks() == 160, "Mark 2 dash 15 / 50 / 8 s");
		h.assertTrue(m2.sonicClapDamage() == 15f && m2.sonicClapEnergy() == 50f && m2.sonicClapCooldownTicks() == 160,
				"Mark 2 sonic clap 15 / 50 / 8 s");
		h.assertTrue(m2.unibeamChannelTicks() == 120 && m2.unibeamDamagePerTick() == 20f && m2.unibeamTotalEnergy() == 300f
				&& m2.unibeamCooldownTicks() == 400, "Mark 2 unibeam 6 s / 20 / 300 / 20 s");
		h.assertTrue(IronManSuits.MARK_2.abilityInSlot(2).equals(IronManAbilities.SONIC_CLAP)
				&& IronManSuits.MARK_2.abilityInSlot(3).equals(IronManAbilities.FLARE)
				&& IronManSuits.MARK_2.abilityInSlot(4).equals(IronManAbilities.UNIBEAM), "Mark 2 G clap, X flares, Z unibeam");

		IronManSuit m3 = IronManSuits.MARK_III;
		h.assertTrue(m3.energyCapacity() == 2000f && m3.energyRegenPerSecond() == 5f && m3.maxIntegrity() == 1000f,
				"Mark III 2000 energy, 5/s, 1000 integrity");
		h.assertTrue(m3.integrityPlayerShare() == 0.5f && m3.arrowFireImmune() && m3.resistanceAmplifier() == 0
				&& m3.strengthBonus() == 7f && m3.autoFeed() && m3.waterBreathing(), "Mark III defence / +7 / feeds / breathes");
		h.assertTrue(m3.repulsorDamage() == 15f && m3.repulsorTapEnergy() == 10f && m3.repulsorTapCooldownTicks() == 20
				&& m3.repulsorWindupTicks() == 0, "Mark III tap 15 / 10 / 1 s, no windup");
		h.assertTrue(m3.chargedRepulsorDamage() == 20f && m3.chargedRepulsorEnergy() == 50f
				&& m3.chargedRepulsorCooldownTicks() == 60 && m3.chargeHoldTicks() == 20, "Mark III charged 20 / 50 / 3 s");
		h.assertTrue(m3.dashDamage() == 20f && m3.dashEnergy() == 50f && m3.dashCooldownTicks() == 160, "Mark III dash 20 / 50 / 8 s");
		h.succeed();
	}

	// ---------------------------------------------------------------- damage model

	@GameTest(template = EMPTY_STRUCTURE)
	public void integrityAbsorbsHalfOfEveryHit(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_1");
		clearSpawnInvulnerability(p);
		float hp = p.getHealth();
		float integ = IronManEnergy.integrity(p, "mark_1");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 10f); // magic bypasses armour points, so the split is exact
		h.assertTrue(Math.abs((hp - p.getHealth()) - 5f) < 0.01f, "the wearer takes half: " + (hp - p.getHealth()));
		h.assertTrue(Math.abs((integ - IronManEnergy.integrity(p, "mark_1")) - 5f) < 0.01f,
				"integrity absorbs the other half: " + (integ - IronManEnergy.integrity(p, "mark_1")));
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void arrowsAndFireDoNothing(GameTestHelper h) {
		for (String id : new String[] { "mark_1", "mark_2", "mark_iii" }) {
			ServerPlayer p = suited(h, id);
			clearSpawnInvulnerability(p);
			float hp = p.getHealth();
			float integ = IronManEnergy.integrity(p, id);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().inFire(), 4f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().lava(), 4f);
			Arrow arrow = new Arrow(EntityType.ARROW, h.getLevel());
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().arrow(arrow, null), 6f);
			h.assertTrue(p.getHealth() == hp, id + ": arrows / fire must not hurt the wearer");
			h.assertTrue(IronManEnergy.integrity(p, id) == integ, id + ": arrows / fire must not drain integrity");
		}
		h.succeed();
	}

	// ---------------------------------------------------------------- Mark 1 kit

	@GameTest(template = EMPTY_STRUCTURE)
	public void strongPunchAndRocketSpendTheirEnergy(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_1");
		float e0 = IronManEnergy.energy(p, "mark_1");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		h.assertTrue(Math.abs(e0 - IronManEnergy.energy(p, "mark_1") - 10f) < 0.01f, "the punch costs 10");
		h.assertFalse(TonyStark.abilityReady(p, "mark_1", IronManAbilities.STRONG_PUNCH), "the punch is on cooldown");
		float e1 = IronManEnergy.energy(p, "mark_1");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_4, true);
		h.assertTrue(Math.abs(e1 - IronManEnergy.energy(p, "mark_1") - 100f) < 0.01f, "the rocket costs 100");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flightBurstTogglesAndHalvesRegen(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_1");
		IronManEnergy.setEnergy(p, "mark_1", 200f);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		long now = p.level().getGameTime();
		h.assertTrue(TonyStark.state(p).timedFlightUntil == now + IronManAbilities.TIMED_FLIGHT_TICKS, "X starts a 20 s burst");
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_1") - 150f) < 0.01f, "the burst costs 50");
		h.assertTrue(IronManEnergy.regenPerSecond(p, IronManSuits.MARK_1) == 1f, "regen is halved (2 -> 1) during the burst");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		h.assertTrue(TonyStark.state(p).timedFlightUntil == 0L, "pressing X again switches it off");
		h.assertTrue(IronManEnergy.regenPerSecond(p, IronManSuits.MARK_1) == 2f, "regen back to 2 afterwards");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftXLaunchesThenStartsTheBurst(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_1");
		IronManEnergy.setEnergy(p, "mark_1", 200f);
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		p.setShiftKeyDown(false);
		h.assertTrue(Math.abs(IronManEnergy.energy(p, "mark_1") - 150f) < 0.01f, "the launch costs 50");
		h.assertTrue(IronManAbilities.flightBurstPending(TonyStark.state(p), "mark_1"), "the burst is queued");
		h.assertTrue(TonyStark.state(p).timedFlightUntil == 0L, "not flying-burst yet");
		h.assertTrue(p.getDeltaMovement().length() > 2.0, "the launch throws the wearer: " + p.getDeltaMovement().length());
		// three seconds later
		TonyStark.state(p).abilityReadyAt.put("mark_1/flight_burst_pending_at", p.level().getGameTime());
		IronManAbilities.tickPendingFlightBurst(p, IronManSuits.MARK_1);
		h.assertTrue(TonyStark.state(p).timedFlightUntil > p.level().getGameTime(), "the burst switched itself on");
		h.assertFalse(IronManAbilities.flightBurstPending(TonyStark.state(p), "mark_1"), "and is no longer pending");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hoverFloorSitsHalfABlockOverTheGround(GameTestHelper h) {
		BlockPos ground = h.absolutePos(new BlockPos(1, 1, 1));
		h.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
		Vec3 feet = new Vec3(ground.getX() + 0.5, ground.getY() + 1.2, ground.getZ() + 0.5);
		double floor = DirectionalFlightModel.hoverFloorY(h.getLevel(), feet, 0.5);
		h.assertTrue(Math.abs(floor - (ground.getY() + 1.5)) < 1e-6, "floor is 0.5 above the stone top, got " + floor);
		double none = DirectionalFlightModel.hoverFloorY(h.getLevel(), feet.add(0, 20, 0), 0.5);
		h.assertTrue(none == Double.NEGATIVE_INFINITY, "no floor high above the ground");
		var tune = DirectionalFlightModel.ironManSuit(0.75f, 0.055f, 0.0, 8.0, false, false, 1.0);
		h.assertTrue(Math.abs(tune.speed() - 0.4) < 1e-6, "the Mark 1 burst cruises at 8 b/s (0.4 b/tick), got " + tune.speed());
		var boosted = DirectionalFlightModel.ironManSuit(1.0f, 0.08f, 0.0, 0.0, false, false, 2.0);
		var plain = DirectionalFlightModel.ironManSuit(1.0f, 0.08f, 0.0, 0.0, false, false, 1.0);
		h.assertTrue(Math.abs(boosted.speed() - plain.speed() * 2.0) < 1e-6, "the supersonic boost doubles the speed");
		h.succeed();
	}

	// ---------------------------------------------------------------- shared moves

	@GameTest(template = EMPTY_STRUCTURE)
	public void dashHitsEachEnemyOnce(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_2");
		Zombie z = zombieAhead(h, p, 2.0);
		float e0 = IronManEnergy.energy(p, "mark_2");
		h.assertTrue(IronManDash.start(p, 15f, 50f, 160), "the dash fires");
		h.assertTrue(Math.abs(e0 - IronManEnergy.energy(p, "mark_2") - 50f) < 0.01f, "the dash costs 50");
		h.assertFalse(IronManDash.start(p, 15f, 50f, 160), "and is on cooldown");
		IronManDash.tick(p);
		float after = z.getHealth();
		h.assertTrue(after <= z.getMaxHealth() - 14f, "the dash hit the zombie for 15 (less its armour), health " + after);
		z.invulnerableTime = 0;
		IronManDash.tick(p);
		h.assertTrue(z.getHealth() == after, "but only once");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void shiftRDashesOnMarkTwo(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_2");
		float e0 = IronManEnergy.energy(p, "mark_2");
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_1, true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_1, false);
		p.setShiftKeyDown(false);
		h.assertTrue(IronManDash.dashing(p), "Shift+R starts the dash");
		h.assertTrue(Math.abs(e0 - IronManEnergy.energy(p, "mark_2") - 50f) < 0.01f, "and only the dash is paid for");
		h.assertTrue(TonyStark.state(p).repulsorWindupAt == 0L, "the release must not queue a repulsor shot");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sonicClapHitsTheCone(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_2");
		Zombie front = zombieAhead(h, p, 6.0);
		Zombie behind = zombieAhead(h, p, -4.0);
		float e0 = IronManEnergy.energy(p, "mark_2");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_2, true);
		h.assertTrue(Math.abs(e0 - IronManEnergy.energy(p, "mark_2") - 50f) < 0.01f, "the clap costs 50");
		h.assertTrue(front.getHealth() <= front.getMaxHealth() - 14f, "the zombie in front takes 15 (less its armour)");
		h.assertTrue(front.getDeltaMovement().z > 0.1, "and is knocked away");
		h.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the one behind is outside the cone");
		h.assertFalse(TonyStark.abilityReady(p, "mark_2", IronManSonicClap.ABILITY_ID), "8 s cooldown");
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void flaresBlindAndSlowAndAdvancedOnesBurn(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_2");
		Zombie z = zombieAhead(h, p, 5.0);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		h.assertTrue(z.hasEffect(MobEffects.BLINDNESS) && z.getEffect(MobEffects.BLINDNESS).getDuration() == 160,
				"flares blind for 8 s");
		h.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
				&& z.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() == 3, "with Slowness IV");
		h.assertTrue(IronManFlares.homingInFlight(p) == 0, "plain flares don't home");

		ServerPlayer p3 = suited(h, "mark_iii");
		Zombie z3 = zombieAhead(h, p3, 5.0);
		h.assertTrue(IronManFlares.fire(p3, true, 200), "advanced flares fire");
		// both zombies stand in front of this spot, so one homing flare for each
		h.assertTrue(IronManFlares.homingInFlight(p3) == IronManFlares.targets(p3).size(), "one homing flare per target");
		for (int i = 0; i < 10 && IronManFlares.homingInFlight(p3) > 0; i++) {
			IronManFlares.tick(p3);
		}
		h.assertTrue(IronManFlares.burning(p3, z3), "the homing flare reached and is burning the target");
		h.assertTrue(z3.isOnFire(), "set alight");
		// the first 5-damage burn lands the moment the flare strikes (zombie armour shaves a sliver off)
		h.assertTrue(z3.getHealth() <= z3.getMaxHealth() - 4.5f, "5 burn damage, health " + z3.getHealth());
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markTwoShiftXBoostsWhileFlying(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_2");
		com.projecthero.mod.ironman.IronManFlight.setFlying(p, true);
		float e0 = IronManEnergy.energy(p, "mark_2");
		p.setShiftKeyDown(true);
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		p.setShiftKeyDown(false);
		h.assertTrue(IronManFlares.boosting(TonyStark.state(p), "mark_2", p.level().getGameTime()), "Shift+X in the air boosts");
		h.assertTrue(IronManFlares.boostUntil(TonyStark.state(p), "mark_2") == p.level().getGameTime() + 600, "for 30 s");
		h.assertTrue(Math.abs(e0 - IronManEnergy.energy(p, "mark_2") - 50f) < 0.01f, "for 50 energy");
		h.succeed();
	}
}
