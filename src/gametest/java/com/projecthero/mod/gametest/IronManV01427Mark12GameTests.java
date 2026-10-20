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

	// ---------------------------------------------------------------- damage model

	/** v0.16.1: the suit's integrity absorbs its Mark's share of a hit, 1:1; the wearer takes the rest. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void integrityAbsorbsTheMarksShareOfAHit(GameTestHelper h) {
		String[] ids = { "mark_1", "mark_1_v2", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii", "mark_8" };
		float[] shares = { 0.30f, 0.32f, 0.30f, 0.38f, 0.40f, 0.15f, 0.45f, 0.50f, 0.60f }; // v0.16.7: the Era I table
		for (int i = 0; i < ids.length; i++) {
			IronManSuit suit = IronManSuits.byId(ids[i]);
			h.assertTrue(Math.abs(suit.absorbShare() - shares[i]) < 1e-4f, ids[i] + " absorbs " + shares[i] + ", got " + suit.absorbShare());
			ServerPlayer p = suited(h, ids[i]);
			float integ = IronManEnergy.integrity(p, ids[i]);
			float landed = com.projecthero.mod.ironman.IronManDamage.absorb(p, suit, p.damageSources().magic(), 10f);
			h.assertTrue(Math.abs(landed - 10f * (1f - shares[i])) < 0.01f, ids[i] + ": the wearer takes the rest, got " + landed);
			h.assertTrue(Math.abs((integ - IronManEnergy.integrity(p, ids[i])) - 10f * shares[i]) < 0.01f,
					ids[i] + ": integrity loses the absorbed share, lost " + (integ - IronManEnergy.integrity(p, ids[i])));
		}
		h.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void absorptionIsCappedAndSkipsFallsAndDisabledSuits(GameTestHelper h) {
		ServerPlayer p = suited(h, "mark_8");
		IronManSuit suit = IronManSuits.MARK_8;
		// a fall is never absorbed
		float integ = IronManEnergy.integrity(p, "mark_8");
		h.assertTrue(com.projecthero.mod.ironman.IronManDamage.absorb(p, suit, p.damageSources().fall(), 10f) == 10f,
				"falls are not absorbed");
		h.assertTrue(IronManEnergy.integrity(p, "mark_8") == integ, "and cost no integrity");
		// only what is left of the integrity is absorbed
		IronManEnergy.setIntegrity(p, "mark_8", 2f);
		float landed = com.projecthero.mod.ironman.IronManDamage.absorb(p, suit, p.damageSources().magic(), 10f);
		h.assertTrue(Math.abs(landed - 8f) < 0.01f, "a hit bigger than the integrity lands the rest, got " + landed);
		h.assertTrue(IronManEnergy.integrity(p, "mark_8") == 0f, "integrity bottoms out at 0");
		h.assertTrue(com.projecthero.mod.ironman.IronManIntegrity.disabled(p, "mark_8"), "a 0-integrity suit is disabled");
		// a disabled suit absorbs nothing
		h.assertTrue(com.projecthero.mod.ironman.IronManDamage.absorb(p, suit, p.damageSources().magic(), 10f) == 10f,
				"a disabled suit absorbs nothing");
		h.succeed();
	}

	/** v0.16.1: no suit is arrow / fire immune any more; only the Mark 7 Space armour ignores fire, lava and freezing. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyTheSpaceArmourIgnoresFire(GameTestHelper h) {
		for (IronManSuit suit : IronManSuits.all()) {
			h.assertFalse(suit.arrowFireImmune(), suit.id() + " is no longer arrow / fire immune");
			h.assertTrue(suit.hazardImmune() == "mark_vii".equals(suit.id()), suit.id() + " hazard immunity");
		}
		ServerPlayer p = suited(h, "mark_vii");
		float integ = IronManEnergy.integrity(p, "mark_vii");
		h.assertFalse(com.projecthero.mod.ironman.IronManDamage.onAllowDamage(p, p.damageSources().lava(), 4f),
				"lava is cancelled on the Space armour");
		h.assertFalse(com.projecthero.mod.ironman.IronManDamage.onAllowDamage(p, p.damageSources().inFire(), 4f),
				"fire is cancelled on the Space armour");
		h.assertTrue(IronManEnergy.integrity(p, "mark_vii") == integ, "and costs no integrity");
		ServerPlayer p3 = suited(h, "mark_iii");
		float integ3 = IronManEnergy.integrity(p3, "mark_iii");
		float landed = com.projecthero.mod.ironman.IronManDamage.absorb(p3, IronManSuits.MARK_III, p3.damageSources().inFire(), 4f);
		h.assertTrue(landed < 4f && IronManEnergy.integrity(p3, "mark_iii") < integ3, "fire is absorbed like any hit on a Mark III");
		h.succeed();
	}

	// ---------------------------------------------------------------- Mark 1 kit

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
		h.assertTrue(IronManDash.start(p, 15f, 50f, 160), "the dash fires"); // v0.16.1: no energy cost any more
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
	public void flaresBlindAndSlowAndAdvancedOnesBurn(GameTestHelper h) {
		// v0.16.1: the Mark 2's X is the comic kit now -- only the shared advanced-flare code is driven directly here
		ServerPlayer p3 = suited(h, "mark_iii");
		Zombie z3 = zombieAhead(h, p3, 5.0);
		h.assertTrue(IronManFlares.fire(p3, true, 200), "advanced flares fire");
		h.assertTrue(z3.hasEffect(MobEffects.BLINDNESS), "flares blind");
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

}
