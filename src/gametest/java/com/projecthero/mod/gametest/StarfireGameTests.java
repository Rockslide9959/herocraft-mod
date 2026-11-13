package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.omnitrix.entity.AlienProjectileEntity;
import com.projecthero.mod.starfire.Starfire;
import com.projecthero.mod.starfire.StarfireAbilityManager;
import com.projecthero.mod.starfire.StarfireConfig;
import com.projecthero.mod.starfire.StarfireDamage;
import com.projecthero.mod.starfire.StarfireFlight;
import com.projecthero.mod.starfire.StarfireLearning;
import com.projecthero.mod.starfire.StarfireMoves;
import com.projecthero.mod.starfire.StarfireRadiance;
import com.projecthero.mod.starfire.data.StarfireState;
import com.projecthero.mod.starfire.item.StarfireItems;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Starfire: the Starstone and the grant, the passives (health, strength, resistance, no fall / drowning / freezing), the solar
 * Starbolt bar (step sizes), the Joy / Fury needle and what it changes, the flight's upkeep, and every move's cost and damage. Mock
 * players are not ticked by the server, so tests over time drive {@link Starfire#tick} themselves (one onEachTick registered at the
 * start; every check a runAfterDelay registered at the start too). Mob hits are asserted as ratios of the expected damage.
 */
public class StarfireGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer starfire(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		helper.assertTrue(Starfire.grant(p), "the grant works");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<StarfireState> change) {
		StarfireState n = Starfire.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.STARFIRE_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(x, 2.0, z));
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		return zb;
	}

	private static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	private static float ratio(float before, LivingEntity e, float expected) {
		return (before - e.getHealth()) / expected;
	}

	private static boolean near(float ratio) {
		return ratio > 0.85f && ratio < 1.15f;
	}

	private static boolean close(double a, double b) {
		return Math.abs(a - b) < 1.0e-3;
	}

	// ---------------------------------------------------------------- the power

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starstoneMakesStarfireTheOnePrimary(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(StarfireItems.STARSTONE));
		boolean sun = StarfireLearning.daylight(p);
		boolean learned = StarfireLearning.learn(p, InteractionHand.MAIN_HAND);
		helper.assertTrue(learned == sun, "the Starstone only wakes in daylight (daylight here: " + sun + ")");
		if (!sun) {
			helper.assertTrue(!Starfire.hasPower(p) && StarfireLearning.isStarstone(p.getMainHandItem()), "in the dark nothing happens and the stone stays");
			helper.assertTrue(Starfire.grant(p), "the grant works anyway");
		} else {
			helper.assertTrue(!StarfireLearning.isStarstone(p.getMainHandItem()), "the stone is spent");
		}
		helper.assertTrue(Starfire.hasPower(p) && HeroTiers.holdsHero(p, Starfire.KEY), "Starfire is a Hero-Tier power");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(!PowerGrants.missingHeroTiers(p).contains(Starfire.KEY) && PowerGrants.NOT_FROM_SERUMS.contains(Starfire.KEY),
				"random serums never give it");
		helper.assertTrue(!Starfire.grant(p), "a second grant is refused");
		Starfire.revoke(p);
		helper.assertTrue(!Starfire.hasPower(p) && p.getMaxHealth() == 20f, "revoked: no power, 20 health again");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starstoneLiesInMeteorCraters(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		ItemEntity e = StarfireLearning.dropInCrater(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)));
		helper.assertTrue(e.getItem().is(StarfireItems.STARSTONE), "a Starstone lies in the crater");
		helper.assertTrue(StarfireLearning.CRATER_ODDS == 3 && StarfireLearning.END_CITY_ODDS == 8, "1 in 3 craters, 1 in 8 End City chests");
		e.discard();
		p.discard();
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void tamaraneanBody(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		helper.assertTrue(p.getMaxHealth() == 30f && p.getHealth() == 30f, "30 health");
		helper.assertTrue(close(p.getAttributeValue(Attributes.ATTACK_DAMAGE), 1.0 + StarfireConfig.MELEE_BONUS), "+6 melee");
		helper.assertTrue(StarfireDamage.immune(p, p.damageSources().fall()) && StarfireDamage.immune(p, p.damageSources().drown())
				&& StarfireDamage.immune(p, p.damageSources().freeze()), "no fall damage, no drowning, no freezing");
		helper.assertTrue(!StarfireDamage.immune(p, p.damageSources().inWall()), "suffocating is only off in the Galactus orbit");
		float before = p.getHealth();
		p.hurt(p.damageSources().fall(), 10f);
		helper.assertTrue(p.getHealth() == before, "a fall doesn't hurt");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 10f);
		helper.assertTrue(close(before - p.getHealth(), 10f * (1f - StarfireConfig.DAMAGE_RESIST)), "20% off a hit: " + (before - p.getHealth()));
		p.setAirSupply(0);
		Starfire.tick(p);
		helper.assertTrue(p.getAirSupply() == p.getMaxAirSupply(), "she never runs out of air");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void solarChargeStepSizes(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		helper.assertTrue(Starfire.regenPerSecond(Starfire.Sun.DIRECT) == 3f * Starfire.regenPerSecond(Starfire.Sun.SHADE),
				"direct sunlight refills 3x faster than shade");
		helper.assertTrue(Starfire.regenPerSecond(Starfire.Sun.NIGHT) < Starfire.regenPerSecond(Starfire.Sun.SHADE)
				&& Starfire.regenPerSecond(Starfire.Sun.DARK) <= Starfire.regenPerSecond(Starfire.Sun.NIGHT), "slow at night and underground");
		Starfire.setStarbolt(p, 40f);
		float before = Starfire.starbolt(p);
		float step = Starfire.regenPerSecond(Starfire.sun(p)) / 20f;
		Starfire.tick(p);
		helper.assertTrue(close(Starfire.starbolt(p) - before, step), "one tick refills regen/20: " + (Starfire.starbolt(p) - before) + " vs " + step);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void joyAndFuryMoveTheNeedle(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Starfire.setMood(p, 0f);
		edit(p, s -> s.flying = true);
		Starfire.tick(p);
		helper.assertTrue(Starfire.mood(p) <= -StarfireConfig.JOY_FLYING / 20f + 1.0e-3f, "flying builds Joy (needle left): " + Starfire.mood(p));
		edit(p, s -> s.flying = false);
		Starfire.setMood(p, 0f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 5f);
		float dealt = 5f * (1f - StarfireConfig.DAMAGE_RESIST);
		helper.assertTrue(close(Starfire.mood(p), dealt * StarfireConfig.FURY_PER_DAMAGE), "a hit builds Fury: " + Starfire.mood(p));
		// what the needle does
		Starfire.setMood(p, 100f);
		helper.assertTrue(Starfire.fullFury(p) && close(Starfire.boltMult(p), 1.6), "full Fury: +60% starbolts");
		helper.assertTrue(close(Starfire.flightSpeedMult(p), 0.7) && close(Starfire.flightCostPerSecond(p), 1.6), "full Fury: heavier flight");
		Starfire.setMood(p, -100f);
		helper.assertTrue(close(Starfire.flightSpeedMult(p), 1.4) && close(Starfire.flightCostPerSecond(p), 0.4) && close(Starfire.boltMult(p), 1.0),
				"full Joy: 40% faster, 60% cheaper flight");
		// eating builds Joy
		// (one Starfire tick per game tick: the eating ticks come on the next two game ticks)
		float[] m0 = new float[1];
		helper.runAfterDelay(1, () -> {
			Starfire.setMood(p, 0f);
			p.getFoodData().setFoodLevel(10);
			Starfire.tick(p);
			m0[0] = Starfire.mood(p);
			p.getFoodData().setFoodLevel(14);
		});
		helper.runAfterDelay(2, () -> {
			Starfire.tick(p);
			helper.assertTrue(Starfire.mood(p) <= m0[0] - 4 * StarfireConfig.JOY_PER_FOOD + 0.2f, "eating 4 food builds 16 Joy: " + m0[0] + " -> " + Starfire.mood(p));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void flightTakesOffAndCostsStarbolt(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Starfire.setMood(p, 0f);
		StarfireFlight.start(p);
		helper.assertTrue(Starfire.isFlying(p) && p.getAbilities().mayfly && p.getAbilities().flying, "double-tap Space: flying");
		Starfire.setStarbolt(p, 50f);
		float before = Starfire.starbolt(p);
		float mood = Starfire.mood(p);
		Starfire.tick(p);
		// (the flight's cost is read after the tick's Joy moved the needle)
		float expected = Starfire.regenPerSecond(Starfire.sun(p)) / 20f - Starfire.flightCostPerSecond(p) / 20f;
		helper.assertTrue(Math.abs((Starfire.starbolt(p) - before) - expected) < 0.01f, "one tick: regen minus upkeep: "
				+ (Starfire.starbolt(p) - before) + " vs " + expected + " (mood " + mood + ")");
		StarfireFlight.stop(p);
		helper.assertTrue(!Starfire.isFlying(p) && !p.getAbilities().mayfly, "landed");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R / Shift+R

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starboltCostsFiveAndHitsForSeven(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Starfire.setMood(p, 0f);
		helper.assertTrue(StarfireAbilityManager.moveFor(AbilitySlot.SLOT_1, false).equals(StarfireAbilityManager.STARBOLT), "R is Starbolt");
		Starfire.setStarbolt(p, 50f);
		helper.assertTrue(StarfireMoves.starbolt(p), "fires");
		helper.assertTrue(close(Starfire.starbolt(p), 45f), "5 Starbolt");
		helper.assertTrue(Starfire.cooldownRemaining(p, StarfireAbilityManager.STARBOLT) == StarfireConfig.BOLT_COOLDOWN, "a short cooldown: tap to keep firing");
		List<AlienProjectileEntity> bolts = helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(4),
				e -> StarfireMoves.BOLT_KIND.equals(e.kind()));
		helper.assertTrue(bolts.size() == 1 && StarfireMoves.hasBolt(bolts.get(0).getUUID()), "a green starbolt is in the air");
		bolts.forEach(AlienProjectileEntity::discard);
		Zombie a = zombie(helper, 4.5, 4.5, 100);
		Zombie b = zombie(helper, 5.3, 4.5, 100);
		float ha = a.getHealth(), hb = b.getHealth();
		StarfireMoves.boltDamageAt(p, a.position().add(0, 1, 0), a, StarfireMoves.boltDamage(p), StarfireConfig.BOLT_BLAST_RADIUS, false);
		helper.assertTrue(near(ratio(ha, a, 7f)), "7 on a direct hit: " + (ha - a.getHealth()));
		helper.assertTrue(near(ratio(hb, b, 3.5f)), "half in the small blast: " + (hb - b.getHealth()));
		Starfire.setMood(p, 100f);
		helper.assertTrue(close(StarfireMoves.boltDamage(p), 7f * 1.6f), "full Fury: 11.2");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starBurstChargesToTwentyEight(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		helper.assertTrue(close(StarfireMoves.burstDamage(0), 10f) && close(StarfireMoves.burstDamage(20), 19f) && close(StarfireMoves.burstDamage(40), 28f)
				&& close(StarfireMoves.burstDamage(400), 28f), "10 to 28 over 2 s");
		helper.assertTrue(close(StarfireMoves.burstRadius(0), 2f) && close(StarfireMoves.burstRadius(40), 4f), "2 to 4 block blast");
		Starfire.setStarbolt(p, 100f);
		helper.assertTrue(StarfireMoves.startHold(p, StarfireAbilityManager.STAR_BURST), "Shift+R starts charging");
		helper.assertTrue(Starfire.charging(p) == StarfireState.CHARGE_BURST, "charging");
		StarfireMoves.release(p, StarfireAbilityManager.STAR_BURST);
		helper.assertTrue(close(Starfire.starbolt(p), 75f) && Starfire.charging(p) == StarfireState.CHARGE_NONE, "the release throws it: 25 Starbolt");
		List<AlienProjectileEntity> bursts = helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(4),
				e -> StarfireMoves.BURST_KIND.equals(e.kind()));
		helper.assertTrue(bursts.size() == 1, "a Star Burst is in the air");
		bursts.forEach(AlienProjectileEntity::discard);
		Zombie z = zombie(helper, 4.5, 4.5, 100);
		Zombie edge = zombie(helper, 4.5, 6.5, 100);
		float hz = z.getHealth(), he = edge.getHealth();
		StarfireMoves.boltDamageAt(p, z.position(), z, 28f, 4f, true);
		helper.assertTrue(near(ratio(hz, z, 28f)), "28 on the target: " + (hz - z.getHealth()));
		helper.assertTrue(edge.getHealth() < he && he - edge.getHealth() < 28f, "less further out in the blast");
		helper.succeed();
	}

	/** v0.17.3 animation round: the synced animation bits (which hand threw, the carry) that every viewer's poses read. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void animationBitsFollowTheHandsAndTheCarry(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Starfire.setStarbolt(p, 100f);
		helper.assertTrue(StarfireMoves.use(p, StarfireAbilityManager.STARBOLT), "R throws");
		boolean first = (Starfire.state(p).anim & StarfireState.ANIM_RIGHT_HAND) != 0;
		Starfire.clearCooldowns(p);
		helper.assertTrue(StarfireMoves.use(p, StarfireAbilityManager.STARBOLT), "R throws again");
		boolean second = (Starfire.state(p).anim & StarfireState.ANIM_RIGHT_HAND) != 0;
		helper.assertTrue(first != second, "the throwing hand alternates and is synced");
		helper.assertTrue(close(Starfire.starbolt(p), 100f - 2 * StarfireConfig.BOLT_COST), "still 5 Starbolt a throw");
		helper.getLevel().getEntitiesOfClass(AlienProjectileEntity.class, p.getBoundingBox().inflate(8)).forEach(AlienProjectileEntity::discard);
		Zombie z = zombie(helper, 1.5, 3.5, 20);
		look(p, z);
		helper.assertTrue(StarfireMoves.use(p, StarfireAbilityManager.GRAB), "G grabs");
		helper.assertTrue((Starfire.state(p).anim & StarfireState.ANIM_CARRYING) != 0, "the carry is synced");
		StarfireMoves.use(p, StarfireAbilityManager.GRAB);
		helper.assertTrue((Starfire.state(p).anim & StarfireState.ANIM_CARRYING) == 0, "and cleared on the hurl");
		StarfireState copy = StarfireState.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
				StarfireState.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, Starfire.state(p)).getOrThrow()).getOrThrow();
		helper.assertTrue(copy.anim == Starfire.state(p).anim && copy.hasPower, "the bits round-trip through the codec");
		helper.succeed();
	}

	// ---------------------------------------------------------------- G / Shift+G

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void grabCarriesThenHurls(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Zombie z = zombie(helper, 1.5, 3.5, 20);
		look(p, z);
		Starfire.setStarbolt(p, 50f);
		helper.assertTrue(StarfireMoves.use(p, StarfireAbilityManager.GRAB), "the key fires the move");
		helper.assertTrue(StarfireMoves.grabbed(p) == z, "G grabs the creature in reach");
		helper.assertTrue(close(Starfire.starbolt(p), 35f), "15 Starbolt");
		Starfire.tick(p);
		helper.assertTrue(z.position().distanceTo(p.position()) < 3.0, "carried in front of her");
		StarfireMoves.use(p, StarfireAbilityManager.GRAB);
		helper.assertTrue(StarfireMoves.grabbed(p) == null, "G again lets go");
		helper.assertTrue(z.getDeltaMovement().length() > StarfireConfig.HURL_SPEED * 0.9, "hurled: " + z.getDeltaMovement().length());
		helper.assertTrue(Starfire.cooldownRemaining(p, StarfireAbilityManager.GRAB) > 0, "then a short cooldown");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eyeBeamsBurnAndDrain(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Zombie z = zombie(helper, 4.5, 4.5, 100);
		Starfire.setStarbolt(p, 80f);
		helper.assertTrue(StarfireMoves.startHold(p, StarfireAbilityManager.EYE_BEAMS) && Starfire.beaming(p), "Shift+G: the beams burn");
		float before = Starfire.starbolt(p);
		float regen = Starfire.regenPerSecond(Starfire.sun(p)) / 20f;
		Starfire.tick(p);
		helper.assertTrue(Math.abs((Starfire.starbolt(p) - before) - (regen - StarfireConfig.BEAM_COST_PER_SECOND / 20f)) < 0.01f,
				"8 Starbolt a second while held: " + (Starfire.starbolt(p) - before));
		float hz = z.getHealth();
		helper.assertTrue(StarfireMoves.beamHit(p, z), "a beam hit lands");
		helper.assertTrue(near(ratio(hz, z, StarfireConfig.BEAM_DAMAGE)) && z.isOnFire(), "6 and on fire: " + (hz - z.getHealth()));
		StarfireMoves.release(p, StarfireAbilityManager.EYE_BEAMS);
		helper.assertTrue(!Starfire.beaming(p) && Starfire.cooldownRemaining(p, StarfireAbilityManager.EYE_BEAMS) > 0, "let go: off, short cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void barrageSprays12BoltsOverOneAndAHalfSeconds(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		helper.assertTrue(StarfireMoves.barrageDue(0) == 1 && StarfireMoves.barrageDue(StarfireConfig.BARRAGE_TICKS - 1) == 12
				&& StarfireMoves.barrageDue(400) == 12, "1 bolt at once, all 12 by 1.5 s");
		helper.assertTrue(close(StarfireMoves.barrageYaw(0), -35f) && close(StarfireMoves.barrageYaw(11), 35f), "a 70 degree arc");
		Starfire.setStarbolt(p, 100f);
		helper.assertTrue(StarfireMoves.use(p, StarfireAbilityManager.BARRAGE), "the key fires the move");
		helper.assertTrue(close(Starfire.starbolt(p), 65f) && Starfire.cooldownRemaining(p, StarfireAbilityManager.BARRAGE) == StarfireConfig.BARRAGE_COOLDOWN,
				"35 Starbolt, 6 s cooldown");
		long start = helper.getLevel().getGameTime();
		helper.onEachTick(() -> Starfire.tick(p));
		helper.runAfterDelay(12, () -> {
			int fired = StarfireMoves.runOf(p).barrageFired();
			long e = helper.getLevel().getGameTime() - start;
			helper.assertTrue(fired > 1 && fired < 12, "part-way through: " + fired + " bolts after " + e + " ticks");
		});
		helper.runAfterDelay(StarfireConfig.BARRAGE_TICKS + 6, () -> {
			helper.assertTrue(StarfireMoves.runOf(p).barrageFired() == -1, "all 12 out and done");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void supernovaNeedsFullFury(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Starfire.setStarbolt(p, 100f);
		Starfire.setMood(p, 60f);
		helper.assertTrue(!StarfireMoves.use(p, StarfireAbilityManager.SUPERNOVA) && Starfire.charging(p) == StarfireState.CHARGE_NONE
				&& !StarfireAbilityManager.allowed(p, StarfireAbilityManager.SUPERNOVA), "not without full Fury");
		Starfire.setMood(p, 100f);
		StarfireMoves.use(p, StarfireAbilityManager.SUPERNOVA);
		helper.assertTrue(Starfire.charging(p) == StarfireState.CHARGE_NOVA && Starfire.starbolt(p) < 1f, "full Fury: it charges (100 Starbolt)");
		helper.assertTrue(Starfire.cooldownRemaining(p, StarfireAbilityManager.SUPERNOVA) == StarfireConfig.NOVA_COOLDOWN, "a 2 minute cooldown");
		Zombie inside = zombie(helper, 5.5, 5.5, 100);
		float hi = inside.getHealth();
		int hit = StarfireMoves.novaBlast(p);
		helper.assertTrue(hit >= 1 && near(ratio(hi, inside, StarfireConfig.NOVA_DAMAGE)), "45 within 10 blocks: " + (hi - inside.getHealth()));
		helper.assertTrue(inside.isOnFire() && inside.hasEffect(MobEffects.BLINDNESS), "set on fire and blinded");
		helper.assertTrue(Starfire.mood(p) == -100f && Starfire.charging(p) == StarfireState.CHARGE_NONE, "Fury empties into Joy");
		helper.succeed();
	}

	// ---------------------------------------------------------------- X / Shift+X

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starDashTenBlocksWithIframes(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		helper.assertTrue(StarfireAbilityManager.moveFor(AbilitySlot.SLOT_3, false).equals(StarfireAbilityManager.STAR_DASH)
				&& StarfireAbilityManager.moveFor(AbilitySlot.SLOT_3, true).equals(StarfireAbilityManager.COMET_DIVE), "X dash, Shift+X Comet Dive");
		helper.assertTrue(close(StarfireMoves.dashStep() * StarfireConfig.DASH_TICKS, 10.0), "10 blocks");
		Starfire.setStarbolt(p, 50f);
		StarfireMoves.use(p, StarfireAbilityManager.STAR_DASH);
		helper.assertTrue(close(Starfire.starbolt(p), 40f) && Starfire.dashing(p), "10 Starbolt, dashing");
		float before = p.getHealth();
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 6f);
		helper.assertTrue(p.getHealth() == before, "brief i-frames");
		helper.assertTrue(Starfire.cooldownRemaining(p, StarfireAbilityManager.STAR_DASH) == StarfireConfig.DASH_COOLDOWN, "1 s cooldown");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void cometDiveHitsForEighteen(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Starfire.setStarbolt(p, 50f);
		StarfireMoves.use(p, StarfireAbilityManager.COMET_DIVE);
		helper.assertTrue(close(Starfire.starbolt(p), 30f) && Starfire.diving(p), "20 Starbolt, wrapped in green fire");
		Zombie z = zombie(helper, 2.5, 2.5, 100);
		Zombie far = zombie(helper, 6.5, 6.5, 100);
		float hz = z.getHealth(), hf = far.getHealth();
		StarfireMoves.cometImpact(p);
		helper.assertTrue(near(ratio(hz, z, StarfireConfig.DIVE_DAMAGE)), "18 on impact: " + (hz - z.getHealth()));
		helper.assertTrue(far.getHealth() == hf, "not past 3 blocks");
		helper.assertTrue(z.getDeltaMovement().horizontalDistance() > 0.5, "knocked back");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C / Shift+C

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void chargeCarriesIntoAWall(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Starfire.setStarbolt(p, 50f);
		StarfireMoves.use(p, StarfireAbilityManager.CHARGE);
		helper.assertTrue(close(Starfire.starbolt(p), 30f), "20 Starbolt");
		Zombie z = zombie(helper, 1.5, 3.5, 100);
		float hz = z.getHealth();
		StarfireMoves.Run r = StarfireMoves.runOf(p);
		StarfireMoves.chargeHit(p, r, z);
		helper.assertTrue(near(ratio(hz, z, StarfireConfig.CHARGE_DAMAGE)), "8 on the tackle");
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(new BlockPos(1, y, 4), Blocks.STONE);
		}
		z.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, 3.4)));
		helper.assertTrue(StarfireMoves.chargeWall(helper.getLevel(), z, new Vec3(0, 0, 1)), "a wall right ahead of it (" + helper.relativeVec(z.position()) + ")");
		z.invulnerableTime = 0;
		float h2 = z.getHealth();
		StarfireMoves.chargeWallHit(p, r);
		helper.assertTrue(near(ratio(h2, z, StarfireConfig.CHARGE_WALL_BONUS)), "+10 slammed into the wall: " + (h2 - z.getHealth()));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void groundPoundLaunches(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Zombie z = zombie(helper, 4.5, 4.5, 100);
		Zombie far = zombie(helper, 7.5, 7.5, 100);
		float hz = z.getHealth(), hf = far.getHealth();
		StarfireMoves.poundSlam(p);
		helper.assertTrue(near(ratio(hz, z, StarfireConfig.POUND_DAMAGE)), "12 within 5 blocks: " + (hz - z.getHealth()));
		helper.assertTrue(z.getDeltaMovement().y > 0.5, "launched up");
		helper.assertTrue(far.getHealth() == hf, "not past 5 blocks");
		helper.succeed();
	}

	// ---------------------------------------------------------------- V / Shift+V

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void languageKissVillagerTradesAndGifts(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(1.5, 2.0, 3.0));
		helper.assertTrue(StarfireMoves.kissVillager(p, v), "the first kiss: a gift");
		helper.assertTrue(v.getGossips().getReputation(p.getUUID(), t -> true) >= StarfireConfig.KISS_GOSSIP, "better trades with that villager");
		helper.assertTrue(!StarfireMoves.kissVillager(p, v), "only one gift a day");
		helper.assertTrue(Starfire.state(p).villagers.containsKey(v.getUUID().toString()), "she remembers the villager");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void languageKissPlayerBondsSixtySeconds(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		ServerPlayer other = mock(helper, 1.5, 3.0);
		StarfireMoves.kissPlayer(p, other);
		helper.assertTrue(other.getUUID().equals(Starfire.bondedWith(p)), "bonded");
		helper.assertTrue(Starfire.state(p).bondUntil - helper.getLevel().getGameTime() == StarfireConfig.BOND_TICKS, "for 60 s");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void gloriousRadianceLightsAndCosts(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		helper.assertTrue(close(Starfire.boltCost(p, 10f), 10f), "bolts at their price");
		StarfireMoves.use(p, StarfireAbilityManager.RADIANCE);
		helper.assertTrue(Starfire.radiant(p) && close(Starfire.boltCost(p, 10f), 12.5f), "on: bolts cost 25% more");
		BlockPos light = StarfireRadiance.lightPos(p.getUUID());
		helper.assertTrue(light != null && helper.getLevel().getBlockState(light).is(Blocks.LIGHT), "her body lights the area");
		Starfire.setMood(p, 0f);
		edit(p, s -> s.flying = true);
		Starfire.tick(p);
		helper.assertTrue(Starfire.mood(p) <= -2f * StarfireConfig.JOY_FLYING / 20f + 1.0e-3f, "Joy builds twice as fast: " + Starfire.mood(p));
		edit(p, s -> s.flying = false);
		Starfire.clearCooldowns(p);
		StarfireMoves.use(p, StarfireAbilityManager.RADIANCE);
		helper.assertTrue(!Starfire.radiant(p) && !helper.getLevel().getBlockState(light).is(Blocks.LIGHT), "off: the light goes out");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void squadmatesAreSafe(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		ServerPlayer mate = mock(helper, 3.5, 3.5);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("Titans" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		float before = mate.getHealth();
		StarfireMoves.poundSlam(p);
		helper.assertTrue(mate.getHealth() == before, "her moves never hurt a squadmate");
		Starfire.setMood(p, 0f);
		mate.invulnerableTime = 0;
		mate.hurt(mate.damageSources().generic(), 5f);
		helper.assertTrue(close(Starfire.mood(p), 5f * StarfireConfig.FURY_PER_SQUAD_DAMAGE), "a squadmate hurt nearby builds her Fury: " + Starfire.mood(p));
		squads.disband(squad);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deathClearsTheMovesButKeepsCooldowns(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		StarfireFlight.start(p);
		StarfireMoves.startHold(p, StarfireAbilityManager.EYE_BEAMS);
		Starfire.startCooldown(p, StarfireAbilityManager.BARRAGE, 100);
		Starfire.clearTransient(p);
		helper.assertTrue(!Starfire.isFlying(p) && !Starfire.beaming(p), "the flight and the beams end");
		helper.assertTrue(Starfire.cooldownRemaining(p, StarfireAbilityManager.BARRAGE) > 0, "cooldowns stay");
		helper.succeed();
	}

	/**
	 * v0.17.3 tester round: Starfire's superhero landing. Mock players never move, so the descent is the "known movement" and ground
	 * contact is setOnGround; HeroLanding.tick is driven directly (as HeroLandingGameTests does).
	 */
	private static void diveAndLand(GameTestHelper helper, ServerPlayer p, Vec3 motion, java.util.function.Consumer<ServerPlayer> then) {
		for (int i = 0; i < 4; i++) {
			helper.runAfterDelay(i, () -> {
				p.setOnGround(false);
				p.setKnownMovement(motion);
				com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			});
		}
		helper.runAfterDelay(4, () -> {
			p.setOnGround(true);
			com.projecthero.mod.flight.landing.HeroLanding.tick(p);
			then.accept(p);
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void diveLandsInTheStarfireStyle(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		StarfireFlight.start(p);
		helper.assertTrue(Starfire.isFlying(p), "precondition: flying");
		diveAndLand(helper, p, new Vec3(0.0, -0.8, 0.0), q -> {
			com.projecthero.mod.flight.landing.HeroLanding.Active a = com.projecthero.mod.flight.landing.HeroLanding.active(q);
			helper.assertTrue(a != null, "a cruise-speed dive into the ground is a hero landing (and its effects ran without throwing)");
			helper.assertTrue(a.style() == com.projecthero.mod.flight.landing.HeroLandingStyle.STARFIRE, "in her style, was " + a.style());
			helper.assertTrue(a.style().pose == com.projecthero.mod.flight.landing.HeroLandingStyle.Pose.FIST, "fist into the ground");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void gentleTouchdownIsNoLanding(GameTestHelper helper) {
		ServerPlayer p = starfire(helper);
		StarfireFlight.start(p);
		diveAndLand(helper, p, new Vec3(0.05, -0.2, 0.0), q -> {
			helper.assertFalse(com.projecthero.mod.flight.landing.HeroLanding.landing(q), "settling down slowly just lands");
			helper.succeed();
		});
	}
}
