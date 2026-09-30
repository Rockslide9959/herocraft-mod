package com.projecthero.mod.gametest;

import com.projecthero.mod.titan.entity.TitanEntity;
import com.projecthero.mod.titan.entity.TitanEntityTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.4 Titan boss: threat-based target switching, the melee swat hitting non-targets, and the two new moves
 * (Leaping Slam, Grave Roar). Each test runs in its own batch -- an 18-block boss with a 150-block detection
 * range must not wander into a neighbouring Titan test.
 */
public class TitanBossV0144GameTests implements FabricGameTest {

	private static void normal(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
	}

	/**
	 * Every GameTest structure is encased in barrier walls and a ceiling, and the grid of earlier tests' cages
	 * surrounds this one -- an 18-block Titan placed at ground level is wedged inside that and can't move. The
	 * ticking tests therefore run on a temporary stone deck well above every cage ({@link #DECK_Y}), removed again
	 * on cleanup.
	 */
	private static final int DECK_Y = 24;
	private static final int DECK_MIN_X = -16;
	private static final int DECK_MAX_X = 24;
	private static final int DECK_MIN_Z = -16;
	private static final int DECK_MAX_Z = 30;

	/**
	 * Chunks this class force-loaded, with how many running tests need each. A test area only guarantees ticking around
	 * its own small structure, and an 18-block Titan leaping across the deck could cross into a chunk that was not
	 * entity-ticking and freeze there mid-flight -- a flake that depended on where the test landed in the grid. The
	 * deck's chunks (plus a margin) are forced while a test uses them; chunks something else already forced are left alone.
	 */
	private static final java.util.Map<Long, Integer> FORCED = new java.util.HashMap<>();
	private static final int DECK_CHUNK_MARGIN = 16;

	private static synchronized void forceDeckChunks(GameTestHelper helper, boolean on) {
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		net.minecraft.core.BlockPos lo = helper.absolutePos(new net.minecraft.core.BlockPos(DECK_MIN_X - DECK_CHUNK_MARGIN, DECK_Y, DECK_MIN_Z - DECK_CHUNK_MARGIN));
		net.minecraft.core.BlockPos hi = helper.absolutePos(new net.minecraft.core.BlockPos(DECK_MAX_X + DECK_CHUNK_MARGIN, DECK_Y, DECK_MAX_Z + DECK_CHUNK_MARGIN));
		int x0 = Math.min(lo.getX(), hi.getX()) >> 4, x1 = Math.max(lo.getX(), hi.getX()) >> 4;
		int z0 = Math.min(lo.getZ(), hi.getZ()) >> 4, z1 = Math.max(lo.getZ(), hi.getZ()) >> 4;
		for (int cx = x0; cx <= x1; cx++) {
			for (int cz = z0; cz <= z1; cz++) {
				long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
				if (on) {
					Integer n = FORCED.get(key);
					if (n != null) {
						FORCED.put(key, n + 1);
					} else if (!level.getForcedChunks().contains(key)) {
						level.setChunkForced(cx, cz, true);
						FORCED.put(key, 1);
					}
				} else {
					Integer n = FORCED.get(key);
					if (n == null) {
						continue;
					}
					if (n <= 1) {
						FORCED.remove(key);
						level.setChunkForced(cx, cz, false);
					} else {
						FORCED.put(key, n - 1);
					}
				}
			}
		}
	}

	private static void deck(GameTestHelper helper, boolean build) {
		if (build) {
			forceDeckChunks(helper, true);
		}
		var state = build ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
				: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
		for (int x = DECK_MIN_X; x <= DECK_MAX_X; x++) {
			for (int z = DECK_MIN_Z; z <= DECK_MAX_Z; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new net.minecraft.core.BlockPos(x, DECK_Y, z)), state, 2);
			}
		}
		if (!build) {
			forceDeckChunks(helper, false);
		}
	}

	/** A position standing on the deck. */
	private static Vec3 onDeck(double x, double z) {
		return new Vec3(x, DECK_Y + 1, z);
	}

	private static TitanEntity titan(GameTestHelper helper, Vec3 rel) {
		TitanEntity titan = TitanEntityTypes.TITAN.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(rel);
		titan.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(titan);
		return titan;
	}

	/** A survival mock player at {@code rel} with 200 max health and no spawn invulnerability, so hits register. */
	private static ServerPlayer player(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		p.setHealth(200.0f);
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static void hitBy(TitanEntity titan, ServerPlayer attacker, float amount) {
		titan.invulnerableTime = 0;
		titan.hurt(titan.damageSources().playerAttack(attacker), amount);
	}

	private static void cleanup(GameTestHelper helper, TitanEntity titan) {
		for (Zombie z : helper.getLevel().getEntitiesOfClass(Zombie.class, titan.getBoundingBox().inflate(96.0),
				z -> z.getTags().contains(TitanEntity.MINION_TAG))) {
			z.discard();
		}
		titan.discard();
		deck(helper, false);
	}

	private static double flatDist(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---------------------------------------------------------------- threat

	@GameTest(template = EMPTY_STRUCTURE, batch = "titan_v0144_threat")
	public void biggerDamageDealerStealsAggroAndCreativeIsIgnored(GameTestHelper helper) {
		normal(helper);
		deck(helper, true);
		TitanEntity titan = titan(helper, onDeck(1, 1));
		ServerPlayer near = player(helper, onDeck(1, 9));
		ServerPlayer far = player(helper, onDeck(1, 25));
		ServerPlayer creative = player(helper, onDeck(1, 4));
		creative.setGameMode(GameType.CREATIVE);

		titan.setTarget(near);
		hitBy(titan, near, 5.0f);
		hitBy(titan, far, 80.0f);
		helper.assertTrue(titan.threatOf(far) > titan.threatOf(near),
				"80 damage must build more threat than 5: far=" + titan.threatOf(far) + " near=" + titan.threatOf(near));

		titan.reevaluateTarget(helper.getLevel(), true);
		helper.assertTrue(titan.getTarget() == far,
				"the player who dealt far more damage must take aggro from the (closer) current target, got "
						+ titan.getTarget());
		helper.assertFalse(titan.isValidTarget(creative), "a creative player is never a valid target");

		far.setGameMode(GameType.CREATIVE);
		titan.reevaluateTarget(helper.getLevel(), true);
		helper.assertTrue(titan.getTarget() == near,
				"once the top-threat player goes creative the Titan must fall back to a valid player, got "
						+ titan.getTarget());
		cleanup(helper, titan);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "titan_v0144_threat_live", timeoutTicks = 200)
	public void liveRetargetPicksTheDamageDealer(GameTestHelper helper) {
		normal(helper);
		deck(helper, true);
		TitanEntity titan = titan(helper, onDeck(1, 1));
		ServerPlayer near = player(helper, onDeck(1, 9));
		ServerPlayer far = player(helper, onDeck(1, 21));
		titan.setTarget(near);
		hitBy(titan, far, 200.0f);
		// No direct reevaluate call: the Titan's own periodic threat check must do the switch while ticking.
		helper.succeedWhen(() -> {
			helper.assertTrue(titan.getTarget() == far, "the ticking Titan should switch to the damage dealer, target="
					+ titan.getTarget());
			cleanup(helper, titan);
		});
	}

	// ---------------------------------------------------------------- melee swat hits everyone in reach

	@GameTest(template = EMPTY_STRUCTURE, batch = "titan_v0144_swat", timeoutTicks = 200)
	public void swatHitsAPlayerWhoIsNotTheTarget(GameTestHelper helper) {
		normal(helper);
		deck(helper, true);
		TitanEntity titan = titan(helper, onDeck(1, 1));
		ServerPlayer target = player(helper, onDeck(1, 27));
		ServerPlayer bystander = player(helper, onDeck(1, 7));
		hitBy(titan, target, 150.0f);
		titan.setTarget(target);
		helper.succeedWhen(() -> {
			helper.assertTrue(bystander.getHealth() < 200.0f,
					"a player standing at the Titan's feet must get swatted even though they aren't its target, health="
							+ bystander.getHealth());
			helper.assertTrue(titan.getTarget() == target, "aggro should still be on the big damage dealer, target="
					+ titan.getTarget());
			cleanup(helper, titan);
		});
	}

	// ---------------------------------------------------------------- Grave Roar

	@GameTest(template = EMPTY_STRUCTURE, batch = "titan_v0144_roar", timeoutTicks = 100)
	public void graveRoarSlowsDamagesAndRaisesHusks(GameTestHelper helper) {
		normal(helper);
		deck(helper, true);
		TitanEntity titan = titan(helper, onDeck(1, 1));
		ServerPlayer a = player(helper, onDeck(1, 16));
		ServerPlayer b = player(helper, onDeck(-12, 8));
		hitBy(titan, a, 50.0f);
		titan.setTarget(a);
		helper.assertTrue(titan.debugBeginAttack("ROAR", true), "sanity: ROAR is a real attack");
		helper.runAfterDelay(6, () -> {
			for (ServerPlayer p : new ServerPlayer[]{a, b}) {
				helper.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the roar must slow every player in range");
				helper.assertTrue(p.getHealth() < 200.0f, "the roar must damage every player in range, health=" + p.getHealth());
			}
			int minions = helper.getLevel().getEntitiesOfClass(Zombie.class, titan.getBoundingBox().inflate(64.0),
					z -> z.getTags().contains(TitanEntity.MINION_TAG)).size();
			helper.assertTrue(minions >= 1, "the roar must raise at least one Husk, got " + minions);
			helper.assertTrue(minions <= com.projecthero.mod.titan.TitanConfig.attacks().roarMaxMinions,
					"never more Husks than the cap, got " + minions);
			cleanup(helper, titan);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Leaping Slam

	@GameTest(template = EMPTY_STRUCTURE, batch = "titan_v0144_leap", timeoutTicks = 240)
	public void leapingSlamLandsOnTargetAndRingHitsBystander(GameTestHelper helper) {
		normal(helper);
		deck(helper, true);
		TitanEntity titan = titan(helper, onDeck(1, 1));
		ServerPlayer target = player(helper, onDeck(1, 17));
		ServerPlayer bystander = player(helper, onDeck(13, 17));
		hitBy(titan, target, 100.0f);
		titan.setTarget(target);
		double before = flatDist(titan.position(), target.position());
		helper.assertTrue(titan.debugBeginAttack("LEAP", true), "sanity: LEAP is a real attack");
		helper.succeedWhen(() -> {
			double after = flatDist(titan.position(), target.position());
			helper.assertTrue(after < before - 8.0, "the Titan must have leapt onto its target: " + before + " -> " + after
					+ " (titan y=" + titan.getY() + ", target y=" + target.getY() + ")");
			helper.assertTrue(titan.debugHitByLeap(target.getUUID()), "the landing must crush the target underneath: "
					+ titan.debugLeapInfo() + " target=" + target.position());
			helper.assertTrue(titan.debugHitByLeap(bystander.getUUID()),
					"the landing ring must hit a grounded bystander 12 blocks away (titan y=" + titan.getY()
							+ ", bystander y=" + bystander.getY() + ")");
			helper.assertTrue(bystander.getHealth() < 200.0f, "the ring must damage the bystander, health="
					+ bystander.getHealth());
			cleanup(helper, titan);
		});
	}
}
