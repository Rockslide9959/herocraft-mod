package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ghostrider.GhostRider;
import com.projecthero.mod.ghostrider.GhostRiderAbilities;
import com.projecthero.mod.ghostrider.GhostRiderConfig;
import com.projecthero.mod.ghostrider.GhostRiderDamage;
import com.projecthero.mod.ghostrider.data.GhostRiderState;
import com.projecthero.mod.ghostrider.entity.HellCycleEntity;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 Ghost Rider: the grant (one Primary, the blood price), the change and its passives, one smoke test per move
 * (the core effect and its number), the guilt rules and the bike. Mock players are not ticked by the server, so tests
 * that run over time drive {@link GhostRider#tick} themselves ({@link #pump}). Hostile mobs need a non-peaceful
 * difficulty, so those tests set HARD.
 */
public class GhostRiderGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

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

	/** A Ghost Rider facing +Z with the change already finished (the moves wait for the burn-in). */
	private static ServerPlayer rider(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		GhostRider.grant(p);
		GhostRider.transform(p);
		GhostRiderState n = GhostRider.state(p).copy();
		n.formChangeAt = helper.getLevel().getGameTime() - 200L;
		n.animId = GhostRiderState.ANIM_NONE;
		p.setAttached(ModAttachments.GHOST_RIDER_STATE, n);
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p, boolean move) {
		helper.onEachTick(() -> {
			p.tickCount++;
			if (move) {
				p.move(MoverType.SELF, p.getDeltaMovement());
			}
			GhostRider.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		// a pumpkin on the head: daylight never sets a zombie alight
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static void hellfireFull(ServerPlayer p) {
		GhostRider.setHellfire(p, GhostRiderConfig.HELLFIRE_MAX);
	}

	// ---------------------------------------------------------------- the grant, the blood price and the change

	@GameTest(template = EMPTY_STRUCTURE)
	public void contractReplacesThePrimaryAndTakesItsPrice(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("ghost_rider") && PowerGrants.HERO_TIER_KEYS.contains("ghost_rider"),
				"ghost_rider is a Hero-Tier key");
		Kryptonian.grant(p);
		helper.assertTrue(GhostRider.grant(p), "the Spirit is granted");
		helper.assertTrue(GhostRider.hasPower(p) && !Kryptonian.hasPower(p), "Ghost Rider replaced the Kryptonian (one Primary)");
		helper.assertTrue(HeroTiers.heroCount(p) == 1 && HeroTiers.holdsHero(p, "ghost_rider"), "exactly one hero held");
		helper.assertFalse(GhostRider.grant(p), "a second grant does nothing");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 10f) < 0.01f, "the blood price: 5 hearts owed, max " + p.getMaxHealth());
		GhostRider.transform(p);
		helper.assertTrue(GhostRider.transformed(p), "H lets the Spirit out");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 26f) < 0.01f, "price paid back and +6 while changed, max " + p.getMaxHealth());
		GhostRider.revert(p);
		helper.assertTrue(!GhostRider.transformed(p) && Math.abs(p.getMaxHealth() - 20f) < 0.01f, "human again: 20 max, got " + p.getMaxHealth());
		Kryptonian.grant(p);
		helper.assertTrue(Kryptonian.hasPower(p) && !GhostRider.hasPower(p), "a new hero replaces Ghost Rider");
		ServerPlayer plain = mock(helper, 4.5, 2.0, 2.5);
		Kryptonian.grant(plain);
		helper.assertTrue(Math.abs(p.getMaxHealth() - plain.getMaxHealth()) < 0.01f, "no Ghost Rider modifier left behind: "
				+ p.getMaxHealth() + " vs " + plain.getMaxHealth());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fireAndWaterCannotTouchHim(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		GhostRider.grant(p);
		float hp = p.getHealth();
		p.hurt(p.damageSources().inFire(), 4f);
		p.hurt(p.damageSources().lava(), 4f);
		helper.assertTrue(p.getHealth() == hp, "human: fire and lava do nothing");
		GhostRider.transform(p);
		p.setHealth(p.getMaxHealth());
		hp = p.getHealth();
		p.hurt(p.damageSources().drown(), 4f);
		p.hurt(p.damageSources().onFire(), 4f);
		helper.assertTrue(p.getHealth() == hp, "changed: no drowning, no burning");
		p.hurt(p.damageSources().generic(), 4f);
		helper.assertTrue(p.getHealth() < hp, "anything else still hurts");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void movesNeedTheSpiritOut(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		GhostRider.grant(p);
		GhostRiderAbilities.shield(p);
		helper.assertFalse(GhostRider.shieldUp(p), "no Soul Shield while human");
		helper.assertTrue(GhostRider.hellfire(p) == GhostRiderConfig.HELLFIRE_MAX, "nothing spent");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the moves

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void hellfireBlastBoltsHitForTwenty(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		hellfireFull(p);
		pump(helper, p, false);
		GhostRiderAbilities.startBlast(p);
		helper.runAfterDelay(8, () -> {
			GhostRiderAbilities.stopBlast(p);
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= GhostRiderConfig.BLAST_DAMAGE - EPS && taken <= GhostRiderConfig.BLAST_DAMAGE + 2f,
					"one bolt: 20 (plus a little burning), took " + taken);
			helper.assertTrue(z.isOnFire(), "the bolt sets it alight");
			helper.assertTrue(Math.abs(GhostRider.hellfire(p) - (GhostRiderConfig.HELLFIRE_MAX - GhostRiderConfig.BLAST_COST)) < 3f, // (it refills a little meanwhile)
					"10 Hellfire a bolt, left " + GhostRider.hellfire(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void hellfireWaveRollsForward(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100);
		pump(helper, p, false);
		GhostRiderAbilities.wave(p);
		helper.runAfterDelay(14, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= GhostRiderConfig.WAVE_DAMAGE - EPS, "the wave hits for 30, took " + taken);
			helper.assertTrue(GhostRider.cooldownRemaining(p, GhostRiderAbilities.WAVE) > 0, "on cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chainWhipHitsAndYanks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100);
		GhostRiderAbilities.whip(p);
		float taken = 100f - z.getHealth();
		helper.assertTrue(Math.abs(taken - GhostRiderConfig.WHIP_DAMAGE) < EPS, "the whip hits for 18, took " + taken);
		helper.assertTrue(z.getDeltaMovement().z < -0.1, "and yanks it toward him, v=" + z.getDeltaMovement());
		helper.assertTrue(GhostRider.state(p).chainUntil > helper.getLevel().getGameTime(), "the chain is out (synced for the render)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void chainSpinHitsAllRound(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 3.5, 100);
		pump(helper, p, false);
		GhostRiderAbilities.spin(p);
		helper.runAfterDelay(25, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= GhostRiderConfig.SPIN_DAMAGE * 2 - EPS, "at least two full 13-damage spin hits (no fall-off), took " + taken);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hellCycleComesAndGoes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 3.5);
		GhostRiderAbilities.cycle(p);
		helper.assertTrue(p.getVehicle() instanceof HellCycleEntity, "X summons the bike under him");
		HellCycleEntity bike = (HellCycleEntity) p.getVehicle();
		GhostRiderAbilities.sendCycleBack(p); // v0.16.5 rework: Shift+X (X while riding is the Cycle Jump)
		helper.assertFalse(p.isPassenger(), "Shift+X sends it back");
		helper.assertTrue(bike.isRemoved(), "the bike is gone");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void judgementMarkFindsOnlyTheGuilty(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 5.5, 100);
		var pig = mob(helper, EntityType.PIG, 5.5, 2.0, 5.5, 100);
		helper.assertTrue(GhostRiderDamage.guilt(z) >= 1, "monsters are born guilty");
		helper.assertTrue(GhostRiderDamage.guilt(pig) == 0, "a pig is innocent");
		GhostRiderAbilities.mark(p);
		helper.assertTrue(GhostRiderDamage.isMarked(z) && !GhostRiderDamage.isMarked(pig), "only the guilty are marked");
		float before = z.getHealth();
		z.hurt(p.damageSources().playerAttack(p), 4f);
		float taken = before - z.getHealth();
		helper.assertTrue(Math.abs(taken - 5f) < 0.05f, "+25% from him on a marked creature, took " + taken);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void sinsAddGuilt(GameTestHelper helper) {
		floor(helper);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 2.5, 100);
		Villager v = mob(helper, EntityType.VILLAGER, 4.5, 2.0, 2.5, 100);
		int before = GhostRiderDamage.guilt(z);
		v.hurt(z.damageSources().mobAttack(z), 1f);
		helper.assertTrue(GhostRiderDamage.guilt(z) == before + GhostRiderConfig.GUILT_HURT, "hurting a villager is a sin");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void penanceStareBurnsTheGuilty(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 1.5);
		Vindicator v = mob(helper, EntityType.VINDICATOR, 3.5, 2.0, 5.5, 100);
		GhostRider.setVengeance(p, 50f);
		pump(helper, p, false);
		float expected = Math.min(GhostRiderConfig.STARE_MAX, GhostRiderConfig.STARE_BASE + 50f * GhostRiderConfig.STARE_PER_VENGEANCE
				+ GhostRiderDamage.guilt(v) * GhostRiderConfig.STARE_PER_GUILT);
		GhostRiderAbilities.stare(p);
		helper.assertTrue(GhostRiderAbilities.running(p, GhostRiderAbilities.STARE), "the stare locks on");
		helper.runAfterDelay(GhostRiderConfig.STARE_TICKS + 3, () -> {
			float taken = 100f - v.getHealth();
			helper.assertTrue(taken >= expected - EPS && taken <= expected + 2f, "penance " + expected + ", took " + taken);
			helper.assertTrue(GhostRider.vengeance(p) < 1f, "all Vengeance spent");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void undeadTakeAFlatPenance(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100);
		GhostRider.setVengeance(p, 100f);
		helper.assertTrue(GhostRiderAbilities.penanceDamage(p, z) == GhostRiderConfig.STARE_FLAT, "no soul to burn: a flat 15");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void hellfireRushSmashesThrough(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.5, 100);
		pump(helper, p, true);
		GhostRiderAbilities.rush(p);
		helper.runAfterDelay(12, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= GhostRiderConfig.RUSH_DAMAGE - EPS, "the rush hits for 18, took " + taken);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void hellfireEruptionLaunches(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 3.5, 100);
		pump(helper, p, false);
		GhostRiderAbilities.eruption(p);
		helper.runAfterDelay(9, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= GhostRiderConfig.ERUPTION_DAMAGE * 0.7f, "the pillars hit hard, took " + taken);
			helper.assertTrue(Math.abs(GhostRider.hellfire(p) - (GhostRiderConfig.HELLFIRE_MAX - GhostRiderConfig.ERUPTION_COST)) < 3f,
					"50 Hellfire");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void soulShieldHalvesAndWrathEmpowers(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rider(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.0, 100);
		GhostRiderAbilities.shield(p);
		helper.assertTrue(GhostRider.shieldUp(p), "Soul Shield up");
		p.setHealth(p.getMaxHealth());
		float hp = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(z), 8f);
		float taken = hp - p.getHealth();
		// HARD scales the zombie's 8 to 12 once; the shield halves that
		helper.assertTrue(Math.abs(taken - 6f) < 0.6f, "a hit is halved (and scaled by the difficulty only once), took " + taken);
		helper.assertTrue(z.isOnFire(), "the attacker burns");
		GhostRiderAbilities.wrath(p);
		helper.assertTrue(p.hasEffect(MobEffects.DAMAGE_BOOST) && p.hasEffect(MobEffects.FIRE_RESISTANCE), "Rider's Wrath: Strength + fire resistance");
		helper.succeed();
	}
}
