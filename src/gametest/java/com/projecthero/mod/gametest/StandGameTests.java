package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.kryptonian.Kryptonian;
import com.projecthero.mod.stand.StandCombat;
import com.projecthero.mod.stand.StandConfig;
import com.projecthero.mod.stand.StandMove;
import com.projecthero.mod.stand.StandType;
import com.projecthero.mod.stand.Stands;
import com.projecthero.mod.stand.StarPlatinum;
import com.projecthero.mod.stand.TimeStop;
import com.projecthero.mod.stand.data.StandState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Stands (Star Platinum): the grant (one Primary, never from serums), summon / dismiss and the punch reach, the
 * visibility rule (the state syncs only to Stand users), one smoke test per move (its core effect and cooldown) and the
 * time stop (a zombie frozen, the queued damage landing on resume). Mock players are not ticked by the server, so tests
 * drive {@link StarPlatinum#tick} themselves. The time stop runs alone in its own batch with a small radius
 * ({@link TimeStop#testRadius}) so it never freezes another test.
 */
public class StandGameTests implements FabricGameTest {
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

	/** A Star Platinum user facing +Z with the Stand out and full Stamina. */
	private static ServerPlayer user(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		Stands.grant(p, StandType.STAR_PLATINUM);
		Stands.summon(p);
		return p;
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
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static boolean onCooldown(ServerPlayer p, String move) {
		return Stands.cooldownRemaining(p, move) > 0;
	}

	// ---------------------------------------------------------------- grant / revoke / summon

	@GameTest(template = EMPTY_STRUCTURE)
	public void standReplacesThePrimaryAndSummons(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 2.5, 2.0, 2.5);
		helper.assertTrue(HeroTiers.HERO_KEYS.contains("stand_star_platinum") && PowerGrants.HERO_TIER_KEYS.contains("stand_star_platinum"), "stand is a Hero-Tier key");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains("stand_star_platinum"), "random serums never give a Stand");
		Kryptonian.grant(p);
		helper.assertTrue(Stands.grant(p, StandType.STAR_PLATINUM), "the Stand awakens");
		double baseReach = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		helper.assertTrue(Stands.hasPower(p) && !Kryptonian.hasPower(p), "the Stand replaced the Kryptonian (one Primary)");
		helper.assertTrue(HeroTiers.heroCount(p) == 1 && HeroTiers.holdsHero(p, "stand_star_platinum"), "exactly one hero held");
		helper.assertFalse(Stands.grant(p, StandType.STAR_PLATINUM), "a second grant does nothing");
		helper.assertTrue(Stands.stamina(p) >= StandConfig.STAMINA_MAX - 0.01f, "full Stamina to start");
		helper.assertFalse(Stands.summoned(p), "the Stand starts away");
		Stands.toggleSummon(p);
		helper.assertTrue(Stands.summoned(p), "H summons it");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach - StandConfig.PUNCH_REACH_BONUS) < 0.01,
				"+1 punch reach while it is out");
		Stands.dismiss(p);
		helper.assertFalse(Stands.summoned(p), "dismissed");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach) < 0.01, "reach back to normal");
		Stands.summon(p);
		Stands.revoke(p);
		helper.assertTrue(!Stands.hasPower(p) && !Stands.summoned(p), "revoked");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - baseReach) < 0.01, "no reach left behind");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void onlyStandUsersReceiveTheStand(GameTestHelper helper) {
		ServerPlayer a = user(helper, 1.5, 2.0, 1.5);
		ServerPlayer b = mock(helper, 3.5, 2.0, 1.5);
		ServerPlayer c = user(helper, 5.5, 2.0, 1.5);
		helper.assertTrue(Stands.SYNC_PREDICATE.test(a, a), "the owner gets their own Stand");
		helper.assertFalse(Stands.SYNC_PREDICATE.test(a, b), "a non-Stand user never receives it");
		helper.assertTrue(Stands.SYNC_PREDICATE.test(a, c), "another Stand user does");
		helper.assertTrue(Stands.SYNC_PREDICATE.test(b, b), "everyone gets their own (empty) state");
		Stands.grant(b, StandType.STAR_PLATINUM);
		helper.assertTrue(Stands.SYNC_PREDICATE.test(a, b), "once awakened, b receives the others' Stands");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void poseToggles(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 2.5);
		Stands.setPoseChoice(p, 4);
		Stands.togglePose(p);
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.STAND_POSE, null).pose() == 1, "N strikes the one pose, whatever an old save chose");
		Stands.togglePose(p);
		helper.assertTrue(p.getAttachedOrElse(ModAttachments.STAND_POSE, null).pose() == 0, "N again drops it");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the moves

	@GameTest(template = EMPTY_STRUCTURE)
	public void starFingerPiercesAndBleeds(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z1 = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 4.5, 40);
		Zombie z2 = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 7.0, 40);
		StarPlatinum.starFinger(p);
		helper.assertTrue(z1.getHealth() <= 40 - StandConfig.FINGER_DAMAGE + 0.6f, "the first zombie is hit, health " + z1.getHealth());
		helper.assertTrue(z2.getHealth() <= 40 - StandConfig.FINGER_DAMAGE + 0.6f, "it pierces the second, health " + z2.getHealth());
		helper.assertTrue(StandCombat.bleeding(z1), "the fingers leave it bleeding");
		helper.assertTrue(onCooldown(p, StandMove.STAR_FINGER), "Star Finger is on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void uppercutLaunches(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 40);
		StarPlatinum.uppercut(p);
		helper.assertTrue(z.getHealth() <= 40 - StandConfig.UPPERCUT_DAMAGE + 0.6f, "the uppercut lands, health " + z.getHealth());
		helper.assertTrue(z.getDeltaMovement().y > 0.8, "it launches the target, vy " + z.getDeltaMovement().y);
		helper.assertTrue(onCooldown(p, StandMove.UPPERCUT), "on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void starBreakerChargedHitsHard(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 60);
		StarPlatinum.startBreaker(p);
		StandState n = Stands.state(p).copy();
		helper.assertTrue(n.chargeStart != 0L, "Z starts the charge");
		n.chargeStart = helper.getLevel().getGameTime() - StandConfig.BREAKER_FULL_CHARGE;
		p.setAttached(ModAttachments.STAND_STATE, n);
		StarPlatinum.releaseBreaker(p);
		helper.assertTrue(z.getHealth() <= 60 - StandConfig.BREAKER_MAX_DAMAGE + 0.6f, "a full charge hits for the most, health " + z.getHealth());
		helper.assertTrue(z.getDeltaMovement().horizontalDistance() > 0.8, "and launches it");
		helper.assertTrue(Stands.state(p).chargeStart == 0L && onCooldown(p, StandMove.STAR_BREAKER), "released and on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void oraRushHitsRepeatedlyThenFinishes(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 200);
		StarPlatinum.startRush(p);
		helper.assertTrue(Stands.state(p).rushing, "R starts the rush");
		helper.onEachTick(() -> StarPlatinum.tick(p));
		helper.runAfterDelay(12, () -> {
			float during = z.getHealth();
			helper.assertTrue(during <= 200 - StandConfig.RUSH_HIT_DAMAGE * 3, "several rush hits landed, health " + during);
			helper.assertTrue(Stands.stamina(p) < StandConfig.STAMINA_MAX - 5f, "the rush drains Stamina");
			StarPlatinum.stopRush(p);
			helper.assertTrue(z.getHealth() <= during - StandConfig.RUSH_FINISHER_DAMAGE + 0.6f, "the ORAAA finisher lands");
			helper.assertTrue(!Stands.state(p).rushing && onCooldown(p, StandMove.ORA_RUSH), "the rush ended on cooldown");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void powerGrabHoldsThenHurls(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 40);
		StarPlatinum.grabKey(p);
		helper.assertTrue(Stands.state(p).grabbedEntity == z.getId(), "G grabs the zombie");
		StarPlatinum.tick(p);
		Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(2.0));
		helper.assertTrue(z.position().distanceTo(hold) < 2.0, "it hangs in the Stand's hand");
		StandState n = Stands.state(p).copy();
		n.abilityReadyAt.clear(); // the hurl ignores the grab cooldown anyway; keep the test independent of it
		p.setAttached(ModAttachments.STAND_STATE, n);
		StarPlatinum.grabKey(p);
		helper.assertTrue(Stands.state(p).grabbedEntity == -1, "G again lets go");
		helper.assertTrue(z.getDeltaMovement().z > 1.0, "and hurls it forward, vz " + z.getDeltaMovement().z);
		helper.assertTrue(onCooldown(p, StandMove.POWER_GRAB), "on cooldown after the throw");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bearingFlickShoots(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 0.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 7.5, 40);
		p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.IRON_NUGGET, 2));
		StarPlatinum.flick(p);
		helper.assertTrue(z.getHealth() <= 40 - StandConfig.BEARING_DAMAGE + 0.6f, "a bearing hits 7 blocks out, health " + z.getHealth());
		helper.assertTrue(p.getInventory().countItem(net.minecraft.world.item.Items.IRON_NUGGET) == 1, "one iron nugget spent");
		helper.assertTrue(Stands.cooldownRemaining(p, StandMove.BEARING_FLICK) <= StandConfig.FLICK_COOLDOWN, "the short cooldown (had a nugget)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void leapLaunchesAndAllowsRebounds(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 2.5);
		StarPlatinum.leapKey(p);
		helper.assertTrue(p.getDeltaMovement().y >= StandConfig.LEAP_UP - 0.01, "a huge leap, vy " + p.getDeltaMovement().y);
		helper.assertTrue(Stands.state(p).leapRebounds == StandConfig.LEAP_REBOUNDS, "3 wall rebounds ready");
		helper.assertTrue(onCooldown(p, StandMove.LEAP), "on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void inhalePullsMobsIn(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 7.5, 40);
		z.setDeltaMovement(Vec3.ZERO);
		StarPlatinum.inhale(p);
		StarPlatinum.tick(p);
		helper.assertTrue(z.getDeltaMovement().z < -0.1, "the zombie is pulled toward the user, vz " + z.getDeltaMovement().z);
		helper.assertTrue(onCooldown(p, StandMove.INHALE), "on cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void guardBlocksMostFrontalDamage(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.0, 40);
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		StarPlatinum.startGuard(p);
		helper.assertTrue(Stands.state(p).guarding, "V raises the guard");
		p.setHealth(20f);
		p.hurt(p.damageSources().mobAttack(z), 10f);
		float guarded = 20f - p.getHealth();
		StarPlatinum.stopGuard(p);
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().mobAttack(z), 10f);
		float open = 20f - p.getHealth();
		helper.assertTrue(open > 0f && guarded > 0f, "both hits landed: " + guarded + " / " + open);
		float ratio = guarded / open;
		helper.assertTrue(ratio > 0.2f && ratio < 0.42f, "the guard takes about 70% off: " + guarded + " vs " + open);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void dashCatchesTheFirstHit(GameTestHelper helper) {
		ServerPlayer p = user(helper, 2.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 4.5, 40);
		StarPlatinum.dash(p);
		helper.assertTrue(p.getDeltaMovement().horizontalDistance() > 1.0, "a quick burst forward");
		p.setHealth(20f);
		p.hurt(p.damageSources().mobAttack(z), 6f);
		helper.assertTrue(p.getHealth() >= 19.99f, "the Stand caught the hit, health " + p.getHealth());
		helper.assertTrue(Stands.state(p).dashUntil == 0L, "only the first hit");
		helper.assertTrue(onCooldown(p, StandMove.DASH), "on cooldown");
		helper.succeed();
	}

	/** v0.17.2 (user: "hands ... in his face or just in his head a lot of the time"): every frame of every move and held pose. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void handsStayClearOfTheHead(GameTestHelper helper) {
		java.util.List<String> bad = com.projecthero.mod.stand.StandClips.clearanceProblems();
		bad.forEach(line -> System.out.println("[STAND-POSE] " + line));
		helper.assertTrue(bad.isEmpty(), bad.size() + " frames with a fist in the head or face, first: " + (bad.isEmpty() ? "" : bad.get(0)));
		helper.succeed();
	}

	// ---------------------------------------------------------------- the time stop

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "stand_time_stop")
	public void timeStopFreezesAndQueuesDamage(GameTestHelper helper) {
		TimeStop.clear();
		TimeStop.testRadius = 8.0;
		TimeStop.testTicks = 20; // (the real stop runs 10 s)
		ServerPlayer p = user(helper, 2.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 2.5, 2.0, 3.5, 60);
		helper.assertTrue(TimeStop.begin(p), "Shift+Z stops time");
		TimeStop.testRadius = 0.0;
		TimeStop.testTicks = 0;
		// v0.17.2: the voice line plays first; time stops on its burst
		helper.assertTrue(TimeStop.windingUp() && !TimeStop.active() && !TimeStop.frozen(z), "the voice line first, nothing frozen yet");
		helper.assertFalse(TimeStop.begin(p), "no second stop while the line plays");
		TimeStop.skipWindup(helper.getLevel().getServer());
		helper.assertTrue(TimeStop.active() && TimeStop.isCaster(p), "the stop is running, cast by the user");
		helper.assertTrue(TimeStop.frozen(z) && !TimeStop.frozen(p), "the zombie is frozen, the caster is not");
		helper.assertTrue(onCooldown(p, StandMove.THE_WORLD), "90 s cooldown");
		helper.assertFalse(TimeStop.begin(p), "only one stop at a time");
		int ticksBefore = z.tickCount;
		StarPlatinum.uppercut(p);
		helper.assertTrue(Math.abs(z.getHealth() - 60f) < 0.01f, "inside the stop the hit doesn't land yet");
		helper.assertTrue(TimeStop.queued(z) >= StandConfig.UPPERCUT_DAMAGE - 0.01f, "it is queued: " + TimeStop.queued(z));
		helper.assertTrue(Stands.stamina(p) > StandConfig.STAMINA_MAX - 1f, "moves are free inside your own stop");
		helper.runAfterDelay(8, () -> {
			helper.assertTrue(TimeStop.active(), "still stopped after 8 ticks");
			helper.assertTrue(z.tickCount == ticksBefore, "the zombie hasn't ticked: " + z.tickCount + " vs " + ticksBefore);
			helper.assertTrue(Math.abs(z.getHealth() - 60f) < 0.01f, "nothing landed yet");
		});
		helper.runAfterDelay(26, () -> {
			helper.assertFalse(TimeStop.active(), "time resumed");
			helper.assertTrue(z.getHealth() <= 60f - StandConfig.UPPERCUT_DAMAGE + 0.6f, "the queued hit landed on resume, health " + z.getHealth());
			helper.assertTrue(z.tickCount > ticksBefore, "the zombie ticks again");
			helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the caster is winded");
			float st = Stands.stamina(p);
			helper.assertTrue(st < StandConfig.STAMINA_MAX - StandConfig.TIME_STOP_COST + 5f && st > 0f, "the stop is paid for as time resumes, not the whole bar: " + st);
			helper.succeed();
		});
	}
}
