package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.galactus.Galactus;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusTerrain;
import com.projecthero.mod.galactus.entity.ElementConverterEntity;
import com.projecthero.mod.galactus.entity.GalactusEntity;
import com.projecthero.mod.galactus.entity.GalactusHandEntity;
import com.projecthero.mod.galactus.entity.GalactusWeakPointEntity;
import com.projecthero.mod.galactus.entity.PowerPylonEntity;
import com.projecthero.mod.galactus.entity.PunisherRobotEntity;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 Galactus fixes: the Punishers stand outside their converter (not inside its drawn body or in blocks), Galactus
 * walks slowly and stays in his band, the pylons shield him (and say so) and he takes damage everywhere once they are down,
 * the Herald turns, the acts advance. Each test runs its own pinned event ({@code instant}) in its own batch.
 */
public class GalactusV0164GameTests implements FabricGameTest {

	private static GalactusEvent start(GameTestHelper helper, boolean guards) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		// v0.16.4: a test that failed mid-way leaves its event running, and only one Galactus may run within 256 blocks --
		// clear any leftover so one failure can't cascade into every later Galactus test ("Galactus starts")
		for (GalactusEvent old : new java.util.ArrayList<>(GalactusEvent.all(helper.getLevel().getServer()))) {
			if (old.state().active()) {
				old.abort(helper.getLevel());
			}
			GalactusTerrain.get(helper.getLevel()).revertAllNow(helper.getLevel(), old.id());
			EventSavedData.get(helper.getLevel()).remove(old.id());
		}
		GalactusEvent e = Galactus.start(helper.getLevel(), at, null, Galactus.Source.COMMAND, x -> {
			x.instant = true;
			x.converterRingOverride = 3.0;
			x.pylonRingOverride = 3.0;
			x.galactusDistanceOverride = 6.0;
			x.noGuards = !guards;
		});
		helper.assertTrue(e != null, "Galactus starts");
		return e;
	}

	private static void end(ServerLevel level, GalactusEvent e) {
		if (e != null && e.state().active()) {
			e.abort(level);
		}
		if (e != null) {
			GalactusTerrain.get(level).revertAllNow(level, e.id());
			EventSavedData.get(level).remove(e.id());
			level.setBlockAndUpdate(e.center(), Blocks.AIR.defaultBlockState());
		}
	}

	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 4000, 4));
		return p;
	}

	/** A stone floor (y = 1 of the structure) {@code r} blocks round the structure's corner (4, 4), clear air above. */
	private static void floor(GameTestHelper helper, int r) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(new BlockPos(4, 1, 4));
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				level.setBlockAndUpdate(o.offset(x, 0, z), Blocks.STONE.defaultBlockState());
				for (int y = 1; y <= 10; y++) {
					if (!level.getBlockState(o.offset(x, y, z)).isAir()) {
						level.setBlockAndUpdate(o.offset(x, y, z), Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static double flat(Vec3 a, double x, double z) {
		return Math.sqrt((a.x - x) * (a.x - x) + (a.z - z) * (a.z - z));
	}

	// ---------------------------------------------------------------- the Punishers

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus164_guards", timeoutTicks = 120)
	public void punishersSpawnOutsideTheirConverterAndStayOut(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 20);
		GalactusEvent e = start(helper, true);
		ServerPlayer p = survivor(helper);
		e.addFighter(p.getUUID());
		BlockPos c = helper.absolutePos(new BlockPos(4, 2, 4));
		ElementConverterEntity conv = e.spawnConverter(level, 0, c.getX() + 0.5, c.getZ() + 0.5);
		helper.assertTrue(conv != null && conv.landed() && conv.isShielded(), "a converter, down, shielded by its guards");
		List<PunisherRobotEntity> guards = new ArrayList<>();
		for (PunisherRobotEntity pu : level.getEntitiesOfClass(PunisherRobotEntity.class, conv.getBoundingBox().inflate(30))) {
			if (conv.getUUID().equals(pu.guardOf())) {
				guards.add(pu);
			}
		}
		helper.assertTrue(guards.size() == GalactusConfig.converters().guards, "its guards: " + guards.size());
		for (PunisherRobotEntity pu : guards) {
			double d = flat(pu.position(), conv.getX(), conv.getZ());
			helper.assertTrue(d >= pu.converterClearance(), "a guard spawns outside the converter's body: " + d + " < " + pu.converterClearance());
			helper.assertTrue(level.noCollision(pu), "and not inside blocks");
			helper.assertTrue(pu.post() != null && Math.abs(pu.post().y - pu.getY()) < 1.0e-3 && flat(pu.post(), conv.getX(), conv.getZ()) >= pu.converterClearance(),
					"its post is where it stands, on the ground, outside the converter");
		}
		// one shoved into the converter's middle is put back on its edge
		PunisherRobotEntity shoved = guards.get(0);
		shoved.setPos(conv.getX() + 0.3, shoved.getY(), conv.getZ());
		shoved.keepClear(level);
		helper.assertTrue(flat(shoved.position(), conv.getX(), conv.getZ()) >= shoved.converterClearance() - 1.0e-3, "nudged out of the converter");
		helper.runAfterDelay(60, () -> {
			try {
				for (PunisherRobotEntity pu : guards) {
					helper.assertTrue(flat(pu.position(), conv.getX(), conv.getZ()) >= pu.converterClearance() - 0.01,
							"three seconds on, still outside: " + flat(pu.position(), conv.getX(), conv.getZ()));
				}
			} finally {
				end(level, e);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Galactus walks

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus164_walk", timeoutTicks = 640)
	public void galactusWalksSlowlyAndStaysInHisBand(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 14);
		GalactusEvent e = start(helper, false);
		ServerPlayer p = survivor(helper);
		e.addFighter(p.getUUID());
		GalactusEntity g = e.startGalactus(level);
		// he never walks into an unloaded chunk; the GameTest world only loads the chunks under the test, so load his ring
		net.minecraft.world.level.ChunkPos home = new net.minecraft.world.level.ChunkPos(e.center());
		for (int cx = -2; cx <= 2; cx++) {
			for (int cz = -2; cz <= 2; cz++) {
				level.setChunkForced(home.x + cx, home.z + cz, true);
			}
		}
		g.noAutoMoves = true;
		// short pauses for the test (the real 4-10 s pauses make the timing a lottery); restored at the end
		GalactusConfig.Colossus cfg = GalactusConfig.galactus();
		int pauseMin = cfg.walkPauseMinTicks;
		int pauseMax = cfg.walkPauseMaxTicks;
		cfg.walkPauseMinTicks = 10;
		cfg.walkPauseMaxTicks = 20;
		g.debugWalkNow();
		Vec3 start = g.position();
		Vec3 centre = Vec3.atBottomCenterOf(e.center());
		double[] band = e.galactusWalkBand();
		double speed = GalactusConfig.galactus().walkSpeed;
		Vec3[] last = { start };
		double[] most = { 0 };
		String[] problem = { null }; // v0.16.4: recorded, reported after the event is cleaned up (a throw mid-tick leaked it)
		helper.onEachTick(() -> {
			if (problem[0] != null || !g.isAlive()) {
				return;
			}
			Vec3 now = g.position();
			double step = flat(now, last[0].x, last[0].z);
			double r = flat(now, centre.x, centre.z);
			if (step > speed + 1.0e-3) {
				problem[0] = "he walks slowly: a step of " + step;
			}
			if (r < band[0] - 0.25 || r > band[1] + 0.25) {
				problem[0] = "he stays in his band " + band[0] + ".." + band[1] + ", got " + r;
			}
			most[0] = Math.max(most[0], flat(now, start.x, start.z));
			last[0] = now;
		});
		// a leg can start with a long pause: if he has not moved by half-time, set him off again
		helper.runAfterDelay(300, () -> {
			if (most[0] < 0.5 && g.isAlive()) {
				g.debugWalkNow();
			}
		});
		helper.runAfterDelay(450, () -> {
			if (most[0] < 0.5 && g.isAlive()) {
				g.debugWalkNow();
			}
		});
		helper.runAfterDelay(600, () -> {
			try {
				helper.assertTrue(problem[0] == null, String.valueOf(problem[0]));
				helper.assertTrue(most[0] > 1.5, "he walked (furthest " + most[0] + " blocks from where he stood; " + g.walkDebug() + ")");
				for (int k = 0; k < GalactusWeakPointEntity.KINDS; k++) {
					GalactusWeakPointEntity w = g.weakPoint(k);
					if (w != null) {
						Vec3 want = g.weakPointPos(k);
						helper.assertTrue(flat(w.position(), want.x, want.z) < 0.5, "weak point " + k + " rides on him");
					}
				}
			} finally {
				for (int cx = -2; cx <= 2; cx++) {
					for (int cz = -2; cz <= 2; cz++) {
						level.setChunkForced(home.x + cx, home.z + cz, false);
					}
				}
				cfg.walkPauseMinTicks = pauseMin;
				cfg.walkPauseMaxTicks = pauseMax;
				end(level, e);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the damage rules

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus164_damage", timeoutTicks = 40)
	public void pylonsShieldHimThenHisBodyAndWeakPointsTakeDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GalactusEvent e = start(helper, false);
		try {
			ServerPlayer p = survivor(helper);
			e.addFighter(p.getUUID());
			GalactusEntity g = e.startGalactus(level);
			g.noAutoMoves = true;
			g.keepWeakPoints(level);
			helper.assertTrue(g.shielded(), "shielded while the pylons stand");
			float h0 = g.getHealth();
			g.invulnerableTime = 0;
			helper.assertFalse(g.hurt(level.damageSources().playerAttack(p), 50f), "a shielded body takes nothing");
			helper.assertTrue(g.getHealth() == h0, "untouched");
			GalactusWeakPointEntity w = g.weakPoint(GalactusWeakPointEntity.KIND_CREST);
			helper.assertFalse(w.hurt(level.damageSources().playerAttack(p), 50f), "a sealed weak point takes nothing");
			List<PowerPylonEntity> pylons = e.livePylons(level);
			pylons.get(0).kill();
			helper.assertTrue(g.shielded() && e.pylonsStanding(level) == pylons.size() - 1, "one pylon down: still shielded");
			for (PowerPylonEntity py : e.livePylons(level)) {
				py.kill();
			}
			g.keepWeakPoints(level);
			helper.assertFalse(g.shielded(), "every pylon down: open");
			g.setHealth(g.getMaxHealth());
			float h1 = g.getHealth();
			g.invulnerableTime = 0;
			helper.assertTrue(g.hurt(level.damageSources().playerAttack(p), 20f), "a sword on his body lands");
			helper.assertTrue(Math.abs(h1 - g.getHealth() - 20f * (float) GalactusConfig.galactus().bodyDamageMultiplier) < 0.01f,
					"the body takes its share: " + (h1 - g.getHealth()));
			helper.assertTrue(GalactusConfig.galactus().bodyDamageMultiplier >= 0.5, "half of every hit at least");
			// an arrow on him (a projectile reaching his hit-box)
			var arrow = new net.minecraft.world.entity.projectile.Arrow(level, p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW),
					null);
			float h2 = g.getHealth();
			g.invulnerableTime = 0;
			helper.assertTrue(g.hurt(level.damageSources().arrow(arrow, p), 10f), "an arrow lands");
			helper.assertTrue(g.getHealth() < h2, "and hurts him");
			float wb = w.getHealth();
			float gb = g.getHealth();
			w.invulnerableTime = 0;
			helper.assertTrue(w.hurt(level.damageSources().playerAttack(p), 20f), "an open weak point is hurt");
			float wx = 20f * (float) GalactusConfig.weakPoints().damageMultiplier;
			helper.assertTrue(GalactusConfig.weakPoints().damageMultiplier > 1.0 && Math.abs(wb - w.getHealth() - wx) < 0.01f,
					"weak points take extra: " + (wb - w.getHealth()));
			helper.assertTrue(gb - g.getHealth() > 20f * GalactusConfig.galactus().bodyDamageMultiplier, "more off him than a body hit");
		} finally {
			end(level, e);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus164_hand", timeoutTicks = 60)
	public void theGrabbingHandStillBreaksTheGrip(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GalactusEvent e = start(helper, false);
		try {
			ServerPlayer victim = survivor(helper);
			ServerPlayer ally = survivor(helper);
			victim.moveTo(Vec3.atBottomCenterOf(e.center()).add(1, 0, 1));
			e.addFighter(victim.getUUID());
			e.addFighter(ally.getUUID());
			GalactusEntity g = e.startGalactus(level);
			g.noAutoMoves = true;
			g.startMove(level, GalactusEntity.Move.GRAB, victim);
			GalactusHandEntity h = g.hand();
			helper.assertTrue(h != null, "the hand comes down");
			h.setGripping(true);
			h.invulnerableTime = 0;
			helper.assertTrue(h.hurt(level.damageSources().playerAttack(ally), 1.0e5f), "an ally hits the hand -- even with the pylons up");
			helper.assertTrue(g.hand() == null || !g.hand().isAlive(), "the grip breaks");
		} finally {
			end(level, e);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- the acts advance

	@GameTest(template = EMPTY_STRUCTURE, batch = "galactus164_acts", timeoutTicks = 400)
	public void theHeraldTurnsAndTheWorldshipFollows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper, 12);
		GalactusEvent e = start(helper, false);
		ServerPlayer p = survivor(helper);
		e.addFighter(p.getUUID());
		// the event only ticks while a counted fighter stands in it, which a mock player is not reliably -- so the Herald is
		// brought in directly (this test is about the turn and the Worldship, not the arrival)
		SilverSurferEntity herald = e.startHerald(level);
		helper.assertTrue(herald != null && e.phase() == GalactusEvent.Phase.HERALD, "the Herald comes, got " + e.phase());
		boolean[] went = { false };
		helper.onEachTick(() -> {
			if (went[0]) {
				return;
			}
			SilverSurferEntity s = e.surfer(level);
			if (s == null) {
				return;
			}
			went[0] = true;
			// just above the 10% line, then the blow that crosses it (his shield can eat a hit, so a few tries)
			s.setHealth(s.getMaxHealth() * 0.12f);
			for (int i = 0; i < 10 && s.surferState() == SilverSurferEntity.HOSTILE; i++) {
				s.invulnerableTime = 0;
				s.hurt(level.damageSources().playerAttack(p), s.getMaxHealth() * 0.05f);
				s.checkTurn(level);
			}
			helper.assertTrue(s.isAlive() && s.surferState() != SilverSurferEntity.HOSTILE, "at 10% he turns instead of dying");
			s.becomeAlly(level);
			helper.assertTrue(e.phase() == GalactusEvent.Phase.WORLDSHIP, "then the Worldship: " + e.phase());
			helper.assertTrue(!e.liveConverters(level).isEmpty(), "converters crash down");
			s.invulnerableTime = 0;
			helper.assertFalse(s.hurt(level.damageSources().playerAttack(p), 40f), "the ally can't be hurt");
			end(level, e);
			helper.succeed();
		});
	}
}
