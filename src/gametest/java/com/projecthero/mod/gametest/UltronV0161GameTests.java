package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.ultron.Ultron;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronConfig;
import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.UltronJam;
import com.projecthero.mod.ultron.UltronUprising;
import com.projecthero.mod.ultron.UltronWorldState;
import com.projecthero.mod.ultron.entity.UltronDroneEntity;
import com.projecthero.mod.ultron.entity.UltronHijackerEntity;
import com.projecthero.mod.ultron.entity.UltronLancerEntity;
import com.projecthero.mod.ultron.entity.UltronPylonEntity;
import com.projecthero.mod.ultron.entity.UltronSentryEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: the harder Ultron Uprising (Part A of docs/ULTRON_CONTAGION_MASTER_PROMPT.md) -- no adaptation, the config v2
 * migration, the pylon guard shield, the Hijacker's latch, the Lancer's front shield, the Sentry's re-shield / Reconstruct,
 * the tracking cannon's turn rate, the global x1.3, and the world's first-defeat flag. Like the v0.15.12 tests, nothing
 * here runs the timed raid; the one test that starts an uprising has its own batch and removes it before it ends.
 */
public class UltronV0161GameTests implements FabricGameTest {

	private static ServerPlayer survivalPlayer(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	// ---------------------------------------------------------------- A1: no adaptation

	@GameTest(template = EMPTY_STRUCTURE)
	public void repeatedHitsOfOneTypeAlwaysDoTheSameDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronDroneEntity drone = helper.spawn(UltronEntityTypes.DRONE, new BlockPos(2, 2, 2));
		try {
			drone.setNoAi(true);
			drone.getAttribute(Attributes.ARMOR).setBaseValue(0);
			drone.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
			drone.setHealth(1000f);
			for (int i = 0; i < 12; i++) {
				drone.invulnerableTime = 0;
				float before = drone.getHealth();
				drone.hurt(level.damageSources().generic(), 5f);
				float lost = before - drone.getHealth();
				helper.assertTrue(Math.abs(lost - 5f) < 0.01f, "generic hit " + (i + 1) + " still does 5, did " + lost);
			}
			for (int i = 0; i < 8; i++) {
				drone.invulnerableTime = 0;
				float before = drone.getHealth();
				drone.hurt(level.damageSources().lightningBolt(), 4f);
				float lost = before - drone.getHealth();
				helper.assertTrue(Math.abs(lost - 6f) < 0.01f, "lightning hit " + (i + 1) + " still does x1.5 = 6, did " + lost);
			}
		} finally {
			drone.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyUltronHitIsThirtyPercentHarder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronDroneEntity drone = helper.spawn(UltronEntityTypes.DRONE, new BlockPos(2, 2, 2));
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		try {
			drone.setNoAi(true);
			zombie.setNoAi(true);
			zombie.getAttribute(Attributes.ARMOR).setBaseValue(0);
			helper.assertTrue(UltronConfig.damage().globalMultiplier == 1.3, "damage.globalMultiplier is 1.3");
			helper.assertTrue(Math.abs(UltronCombat.scaleOutgoing(level.damageSources().mobAttack(drone), 10f) - 13f) < 1.0e-4,
					"a robot's 10 becomes 13");
			helper.assertTrue(UltronCombat.scaleOutgoing(level.damageSources().mobAttack(zombie), 10f) == 10f, "anyone else's hit is untouched");
			float before = zombie.getHealth();
			zombie.invulnerableTime = 0;
			zombie.hurt(level.damageSources().mobAttack(drone), 5f);
			float lost = before - zombie.getHealth();
			helper.assertTrue(Math.abs(lost - 6.5f) < 0.01f, "a 5-damage Ultron hit lands as 6.5, did " + lost);
		} finally {
			drone.discard();
			zombie.discard();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- A2: config v2

	@GameTest(template = EMPTY_STRUCTURE)
	public void configV2MigrationRewritesEveryChangedKey(GameTestHelper helper) {
		helper.assertTrue(UltronConfig.SPEC.currentVersion() >= 2, "the Ultron config is at v2+");
		// a v1 file (v0.15.12) holding the old, easy numbers -- plus a server toggle and an unchanged key it customised
		String v1 = "{\"configVersion\":1,\"trigger\":{\"naturalTriggerEnabled\":true},"
				+ "\"arena\":{\"pylons\":3,\"pylonRing\":22.0,\"pylonHealth\":300.0,\"pylonHealthPerExtraFighter\":100.0,\"repairPerSecond\":2.0,"
				+ "\"reservePylonHealthFraction\":0.5,\"radius\":44.0},"
				+ "\"waves\":{\"waveCount\":5,\"enemyCap\":30,\"drones\":[5,7,5,5,6],\"sentinelDrones\":[0,0,4,3,4],\"heavies\":[0,0,0,1,2],"
				+ "\"snipers\":[0,0,0,2,2]},"
				+ "\"drone\":{\"health\":30.0,\"boltDamage\":4.0},\"sentinelDrone\":{\"health\":45.0},\"heavy\":{\"health\":120.0},"
				+ "\"sniper\":{\"shotDamage\":14.0},"
				+ "\"prime\":{\"health\":900.0,\"healthPerExtraFighter\":300.0,\"maxHealth\":3000.0},"
				+ "\"sentry\":{\"health\":1500.0,\"healthPerExtraFighter\":400.0,\"maxHealth\":4000.0,\"shieldDrones\":3},"
				+ "\"rewards\":{\"plateBase\":5}}";
		UltronConfig c = UltronConfig.SPEC.fromJson(v1);
		helper.assertTrue(c.configVersion == UltronConfig.SPEC.currentVersion(), "stamped with the current version");
		helper.assertTrue(c.arena.pylons == 4 && c.arena.pylonRing == 24.0, "4 pylons on a 24-block ring");
		helper.assertTrue(c.arena.pylonHealth == 450.0 && c.arena.pylonHealthPerExtraFighter == 150.0, "pylon 450 + 150");
		helper.assertTrue(c.arena.repairPerSecond == 3.0, "repair 3/s");
		helper.assertTrue(Math.abs(c.arena.pylonHealth * c.arena.reservePylonHealthFraction - 300.0) < 1.0e-6, "reserve pylons 300 solo");
		helper.assertTrue(c.waves.waveCount == 7 && c.waves.enemyCap == 36, "7 waves, cap 36");
		helper.assertTrue(c.waves.drones.length == 7 && c.waves.drones[0] == 6 && c.waves.hijackers[6] == 2, "the new wave table");
		helper.assertTrue(c.drone.health == 40.0 && c.drone.boltDamage == 5f, "drone 40 / 5");
		helper.assertTrue(c.sentinelDrone.health == 60.0, "sentinel drone 60");
		helper.assertTrue(c.heavy.health == 180.0 && c.heavy.barrageMissiles == 4, "heavy 180 / 4 missiles");
		helper.assertTrue(c.sniper.shotDamage == 16f && c.sniper.relocateAfterShot, "sniper 16, relocates");
		helper.assertTrue(c.prime.health == 1600.0 && c.prime.healthPerExtraFighter == 450.0 && c.prime.maxHealth == 5000.0, "Prime 1600 + 450 / 5000");
		helper.assertTrue(c.sentry.health == 2600.0 && c.sentry.healthPerExtraFighter == 600.0 && c.sentry.maxHealth == 7000.0, "Sentry 2600 + 600 / 7000");
		helper.assertTrue(c.sentry.shieldDrones == 4, "4 shield drones");
		helper.assertTrue(c.damage.globalMultiplier == 1.3, "x1.3");
		helper.assertTrue(c.lancer.health == 90.0 && c.lancer.armor == 8.0 && c.lancer.lanceDamage == 10f && c.lancer.lanceReach == 4.0,
				"the Lancer section arrives");
		helper.assertTrue(c.hijacker.health == 50.0 && c.hijacker.latchTicks == 80 && c.hijacker.jamTicks == 120, "the Hijacker section arrives");
		// what the server chose and the balance pass never touched is kept
		helper.assertTrue(c.trigger.naturalTriggerEnabled, "a server toggle is kept");
		helper.assertTrue(c.arena.radius == 44.0, "an unchanged key keeps the server's value, got " + c.arena.radius);
		helper.assertTrue(c.rewards.plateBase == 5, "an unchanged rewards key is kept");
		// a file with no version at all (pre-VersionedConfig) also gets the new numbers
		UltronConfig v0 = UltronConfig.SPEC.fromJson("{\"prime\":{\"health\":900.0},\"waves\":{\"waveCount\":5}}");
		helper.assertTrue(v0.prime.health == 1600.0 && v0.waves.waveCount == 7, "a v0 file is migrated too");
		helper.succeed();
	}

	// ---------------------------------------------------------------- pylon guard shield

	@GameTest(template = EMPTY_STRUCTURE)
	public void pylonIsShieldedWhileThreeUnitsGuardIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronPylonEntity pylon = helper.spawn(UltronEntityTypes.PYLON, new BlockPos(4, 2, 4));
		List<UltronDroneEntity> guards = new ArrayList<>();
		try {
			pylon.configure(pylon.blockPosition(), 450, false);
			for (int i = 0; i < 3; i++) {
				UltronDroneEntity d = helper.spawn(UltronEntityTypes.DRONE, new BlockPos(2 + i, 2, 2));
				d.setNoAi(true);
				guards.add(d);
			}
			helper.assertTrue(pylon.guards(level) == 3, "three guards counted, got " + pylon.guards(level));
			helper.assertTrue(pylon.refreshShield(level) && pylon.shielded(), "three guards: shielded");
			float h0 = pylon.getHealth();
			pylon.invulnerableTime = 0;
			pylon.hurt(level.damageSources().generic(), 50f);
			helper.assertTrue(pylon.getHealth() == h0, "the shield takes the hit");
			guards.remove(0).discard();
			helper.assertFalse(pylon.refreshShield(level), "two guards: the shield drops");
			pylon.invulnerableTime = 0;
			pylon.hurt(level.damageSources().generic(), 50f);
			helper.assertTrue(pylon.getHealth() < h0, "unshielded, it takes damage");
		} finally {
			for (UltronDroneEntity d : guards) {
				d.discard();
			}
			pylon.discard();
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Hijacker

	@GameTest(template = EMPTY_STRUCTURE)
	public void hijackerLatchJamsOneAbilityAndBreaksWhenHit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		ServerPlayer p = survivalPlayer(helper);
		UltronHijackerEntity h = helper.spawn(UltronEntityTypes.HIJACKER, new BlockPos(3, 2, 3));
		try {
			helper.assertTrue(h.cloaked(), "it starts cloaked");
			UltronJam.clear(p);
			helper.assertTrue(UltronJam.jammedCount(p) == 0, "nothing jammed yet");
			h.latch(level, p);
			helper.assertTrue(h.isLatched() && !h.cloaked(), "latched on, and visible");
			helper.assertTrue(UltronJam.jammedCount(p) == 1, "exactly one ability key jammed, got " + UltronJam.jammedCount(p));
			int slot = -1;
			for (int s = 1; s <= 6; s++) {
				if (UltronJam.isJammed(p, s)) {
					slot = s;
				}
			}
			helper.assertTrue(slot >= 1, "one of the six universal slots");
			helper.assertTrue(UltronJam.blocks(p, slot, true), "a press on the jammed key is refused");
			helper.assertFalse(UltronJam.blocks(p, slot, false), "a release always goes through");
			h.invulnerableTime = 0;
			h.hurt(level.damageSources().playerAttack(p), 2f);
			helper.assertFalse(h.isLatched(), "hitting it breaks the latch");
			helper.assertTrue(UltronJam.isJammed(p, slot), "the jam itself runs its 6 s");
		} finally {
			UltronJam.clear(p);
			h.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void networkOverrideJamsEveryKey(GameTestHelper helper) {
		ServerPlayer p = survivalPlayer(helper);
		try {
			UltronJam.jam(p, UltronJam.ALL, UltronConfig.prime().overrideJamTicks, true);
			helper.assertTrue(UltronJam.jammedCount(p) == 8, "all eight ability keys dead, got " + UltronJam.jammedCount(p));
			helper.assertTrue(UltronConfig.prime().overrideJamTicks == 80 && UltronConfig.prime().overrideTelegraphTicks == 20,
					"4 s jam after a 1 s reticle");
		} finally {
			UltronJam.clear(p);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Lancer

	@GameTest(template = EMPTY_STRUCTURE)
	public void lancerShieldBlocksFrontalProjectilesOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronLancerEntity l = helper.spawn(UltronEntityTypes.LANCER, new BlockPos(4, 2, 4));
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 5));
		try {
			l.setNoAi(true);
			z.setNoAi(true);
			l.setYRot(0f);
			l.setYBodyRot(0f); // facing +Z
			l.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
			l.setHealth(1000f);
			helper.assertTrue(l.getMaxHealth() == 1000f && UltronConfig.lancer().health == 90.0, "(the Lancer's own health is 90)");
			helper.assertTrue(l.shieldUp(), "the shield starts up");
			Vec3 c = l.position();
			// from the front
			float h0 = l.getHealth();
			l.invulnerableTime = 0;
			l.hurt(level.damageSources().arrow(arrowAt(level, c.add(0, 1.2, 4)), null), 6f);
			helper.assertTrue(l.getHealth() == h0, "a frontal arrow is blocked");
			// from behind
			l.invulnerableTime = 0;
			l.hurt(level.damageSources().arrow(arrowAt(level, c.add(0, 1.2, -4)), null), 6f);
			helper.assertTrue(l.getHealth() < h0, "an arrow from behind lands");
			// from straight above
			float h1 = l.getHealth();
			l.invulnerableTime = 0;
			l.hurt(level.damageSources().arrow(arrowAt(level, c.add(0, 8, 1)), null), 6f);
			helper.assertTrue(l.getHealth() < h1, "an arrow from above lands");
			// melee from the front still lands
			float h2 = l.getHealth();
			z.moveTo(c.x, c.y, c.z + 1.2);
			l.invulnerableTime = 0;
			l.hurt(level.damageSources().mobAttack(z), 4f);
			helper.assertTrue(l.getHealth() < h2, "a melee hit from the front lands");
			// shield down after a lunge: a frontal arrow lands
			l.dropShield(UltronConfig.lancer().shieldDownTicks);
			float h3 = l.getHealth();
			l.invulnerableTime = 0;
			l.hurt(level.damageSources().arrow(arrowAt(level, c.add(0, 1.2, 4)), null), 6f);
			helper.assertTrue(l.getHealth() < h3, "with the shield down a frontal arrow lands");
		} finally {
			l.discard();
			z.discard();
		}
		helper.succeed();
	}

	private static Arrow arrowAt(ServerLevel level, Vec3 at) {
		Arrow a = new Arrow(EntityType.ARROW, level);
		a.setPos(at.x, at.y, at.z);
		return a;
	}

	// ---------------------------------------------------------------- the Sentry

	@GameTest(template = EMPTY_STRUCTURE, batch = "ultron_v0161_sentry", timeoutTicks = 40)
	public void sentryReconstructsThenShieldsWithFourDrones(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronSentryEntity s = helper.spawn(UltronEntityTypes.SENTRY, new BlockPos(4, 2, 4));
		try {
			s.setNoAi(true);
			s.configure(1);
			helper.assertTrue(s.getMaxHealth() == 2600f, "Sentry solo 2600");
			// crossing half health starts Reconstruct (not the shield yet)
			s.invulnerableTime = 0;
			s.hurt(level.damageSources().generic(), s.getMaxHealth() * 0.6f);
			helper.assertTrue(s.move() == UltronSentryEntity.Move.RECONSTRUCT, "at 50% he channels Reconstruct, got " + s.move());
			helper.assertFalse(s.shielded(), "the shield waits for the channel");
			// each drone that flies in heals 2%
			UltronDroneEntity d = helper.spawn(UltronEntityTypes.DRONE, new BlockPos(2, 2, 2));
			float h0 = s.getHealth();
			s.absorb(level, d, UltronConfig.sentry());
			helper.assertTrue(Math.abs(s.getHealth() - h0 - s.getMaxHealth() * 0.02f) < 0.5f, "absorbed drone heals 2%: " + h0 + " -> " + s.getHealth());
			helper.assertTrue(s.absorbed() == 1 && d.isRemoved(), "the drone is gone into him");
			// the shield: four carriers
			s.raiseShield(level);
			helper.assertTrue(s.shielded() && s.shieldDrones().size() == 4, "four shield drones, got " + s.shieldDrones().size());
			helper.assertTrue(UltronConfig.sentry().reshieldAt == 0.25, "it comes back once at 25%");
		} finally {
			for (UltronDroneEntity d : level.getEntitiesOfClass(UltronDroneEntity.class, new AABB(s.blockPosition()).inflate(48))) {
				d.discard();
			}
			s.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void chestCannonTurnsTwentyDegreesASecond(GameTestHelper helper) {
		double perTick = Math.toRadians(UltronConfig.sentry().cannonTurnDegreesPerSecond / 20.0);
		Vec3 dir = new Vec3(1, 0, 0);
		Vec3 after = UltronSentryEntity.turnToward(dir, new Vec3(0, 0, 1), perTick);
		double turned = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, after.dot(dir)))));
		helper.assertTrue(Math.abs(turned - 1.0) < 1.0e-3, "1 degree a tick (20 a second), turned " + turned);
		Vec3 second = dir;
		for (int i = 0; i < 20; i++) {
			second = UltronSentryEntity.turnToward(second, new Vec3(0, 0, 1), perTick);
		}
		double inSecond = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, second.dot(dir)))));
		helper.assertTrue(Math.abs(inSecond - 20.0) < 0.01, "20 degrees in a second, got " + inSecond);
		Vec3 close = UltronSentryEntity.turnToward(dir, new Vec3(1, 0, 0.001), perTick);
		helper.assertTrue(close.distanceTo(new Vec3(1, 0, 0.001).normalize()) < 1.0e-6, "a small gap snaps on");
		helper.succeed();
	}

	// ---------------------------------------------------------------- A5: the first-defeat flag

	@GameTest(template = EMPTY_STRUCTURE, batch = "ultron_v0161_defeat", timeoutTicks = 40)
	public void firstDefeatFlagIsSetOnceAndNeverMoves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		UltronWorldState.resetForTest(level);
		UltronUprising u = null;
		try {
			helper.assertTrue(UltronWorldState.firstDefeatTime(level) == -1L && !UltronWorldState.defeatedOnce(level), "never beaten");
			BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
			u = Ultron.start(level, at, null, x -> {
				x.pylonRingOverride = 3.0;
				x.arrivalRadiusOverride = 1.5;
				x.arrivalHeightOverride = 1.0;
			});
			helper.assertTrue(u != null, "the uprising starts");
			UltronSentryEntity s = u.startSentry(level);
			helper.assertTrue(s != null, "the Sentry stands");
			long before = level.getServer().overworld().getGameTime();
			s.kill();
			long first = UltronWorldState.firstDefeatTime(level);
			helper.assertTrue(first >= before && UltronWorldState.defeatedOnce(level), "his fall in a running uprising sets the flag, got " + first);
			helper.assertTrue(UltronWorldState.defeats(level) == 1, "one defeat");
			helper.assertFalse(UltronWorldState.recordDefeat(level), "a second defeat is not a first");
			helper.assertTrue(UltronWorldState.firstDefeatTime(level) == first, "the flag never moves");
			helper.assertTrue(UltronWorldState.defeats(level) == 2, "but it is counted");
			// an unbound Sentry (a spawn egg) never counts
			UltronSentryEntity loose = helper.spawn(UltronEntityTypes.SENTRY, new BlockPos(1, 2, 1));
			loose.kill();
			helper.assertTrue(UltronWorldState.defeats(level) == 2, "a spawn-egg Sentry is not a defeat");
		} finally {
			if (u != null) {
				u.abort(level);
				EventSavedData.get(level).remove(u.id());
			}
			UltronWorldState.resetForTest(level); // leave the gametest world as it was: never beaten
		}
		helper.succeed();
	}

	/** v0.16.1: on a suited Tony Stark the latch tears 5% of max integrity off the hull and locks the suit out for 2 s. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void hijackerLatchTearsTheHullAndLocksTheSuitOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		ServerPlayer p = survivalPlayer(helper);
		com.projecthero.mod.ironman.TonyStark.grant(p);
		for (net.minecraft.world.item.ArmorItem.Type t : new net.minecraft.world.item.ArmorItem.Type[] {
				net.minecraft.world.item.ArmorItem.Type.HELMET, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
				net.minecraft.world.item.ArmorItem.Type.LEGGINGS, net.minecraft.world.item.ArmorItem.Type.BOOTS }) {
			p.setItemSlot(com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotFor(t),
					new net.minecraft.world.item.ItemStack(com.projecthero.mod.ironman.item.IronManItems.armor("mark_iii", t)));
		}
		float max = com.projecthero.mod.ironman.IronManEnergy.maxIntegrity("mark_iii");
		com.projecthero.mod.ironman.IronManEnergy.setIntegrity(p, "mark_iii", max);
		UltronConfig.Hijacker cfg = UltronConfig.hijacker();
		helper.assertTrue(cfg.ironManIntegrityDrain == 0.05 && cfg.ironManLockoutTicks == 40, "the v0.16.1 Hijacker numbers");
		UltronHijackerEntity h = helper.spawn(UltronEntityTypes.HIJACKER, new BlockPos(3, 2, 3));
		try {
			helper.assertFalse(com.projecthero.mod.ironman.TonyStark.overloaded(p), "not locked out before the latch");
			h.latch(level, p);
			float lost = max - com.projecthero.mod.ironman.IronManEnergy.integrity(p, "mark_iii");
			helper.assertTrue(Math.abs(lost - (float) (max * cfg.ironManIntegrityDrain)) < 0.01f,
					"the latch tears 5% of max integrity off the hull, lost " + lost);
			helper.assertTrue(com.projecthero.mod.ironman.IronManEnergy.energy(p, "mark_iii") == com.projecthero.mod.ironman.IronManEnergy.capacity("mark_iii"), // v0.16.7: Era I capacity
					"no energy drain any more");
			helper.assertTrue(com.projecthero.mod.ironman.TonyStark.overloaded(p), "the suit is locked out");
			helper.assertTrue(com.projecthero.mod.ironman.TonyStark.state(p).overloadUntil == level.getGameTime() + cfg.ironManLockoutTicks,
					"for the configured 40 ticks");
		} finally {
			UltronJam.clear(p);
			h.discard();
		}
		helper.succeed();
	}
}
