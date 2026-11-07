package com.projecthero.mod.gametest;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.doomsday.Doomsday;
import com.projecthero.mod.doomsday.DoomsdayAdaptation;
import com.projecthero.mod.doomsday.DoomsdayAdaptation.Bucket;
import com.projecthero.mod.doomsday.DoomsdayConfig;
import com.projecthero.mod.doomsday.DoomsdayEvent;
import com.projecthero.mod.doomsday.DoomsdayItems;
import com.projecthero.mod.doomsday.entity.DoomsdayEntity;
import com.projecthero.mod.doomsday.entity.DoomsdayEntityTypes;
import com.projecthero.mod.doomsday.item.BoneGauntletItem;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.event.EventTypes;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Doomsday. The adaptation is pure bookkeeping and is driven directly; the boss is spawned without AI and hurt
 * by hand; the event is started {@code instant} and ended (aborted and removed) before each test returns, because only
 * one may run within 256 blocks.
 */
public class DoomsdayGameTests implements FabricGameTest {

	private static DoomsdayEntity spawn(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		DoomsdayEntity d = DoomsdayEntityTypes.DOOMSDAY.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
		d.moveTo(at.x, at.y, at.z, 0f, 0f);
		d.setNoAi(true);
		d.configure(1);
		helper.getLevel().addFreshEntity(d);
		return d;
	}

	private static void kill(ServerLevel level, DoomsdayEntity d) {
		d.invulnerableTime = 0;
		d.hurt(level.damageSources().onFire(), d.getHealth() + 5000f);
	}

	// ---------------------------------------------------------------- registration

	@GameTest(template = EMPTY_STRUCTURE)
	public void doomsdayIsRegistered(GameTestHelper helper) {
		helper.assertTrue(EventTypes.isRegistered(DoomsdayEvent.TYPE_ID), "doomsday is a registered event type");
		helper.assertTrue(EventTypes.create(DoomsdayEvent.TYPE_ID, UUID.randomUUID()) instanceof DoomsdayEvent, "loads back as a Doomsday event");
		helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id("calatonian_casket")).isPresent(), "the casket recipe");
		helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ProjectHeroMod.id("phantom_zone_projector")).isPresent(), "the projector recipe");
		helper.assertTrue(DoomsdayConfig.lifeHealth(1) == 3000.0 && DoomsdayConfig.lifeHealth(3) == 4500.0, "3000 a life, +750 per extra fighter");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the adaptation, by hand

	@GameTest(template = EMPTY_STRUCTURE)
	public void adaptationLearnsInStepsAndCaps(GameTestHelper helper) {
		DoomsdayAdaptation a = new DoomsdayAdaptation();
		helper.assertTrue(a.multiplier(Bucket.FIRE) == 1f, "fresh: full damage");
		helper.assertFalse(a.record(Bucket.FIRE, 599f, 3000), "under 20% of the life: nothing learned");
		helper.assertTrue(a.record(Bucket.FIRE, 1f, 3000), "20% of the life: one step");
		helper.assertTrue(Math.abs(a.multiplier(Bucket.FIRE) - 0.75f) < 1e-4, "-25%");
		a.record(Bucket.FIRE, 600f * 10, 3000);
		helper.assertTrue(Math.abs(a.multiplier(Bucket.FIRE) - 0.25f) < 1e-4, "capped at -75%: " + a.multiplier(Bucket.FIRE));
		helper.assertTrue(a.multiplier(null) == 1f, "kryptonite / kill: never adapted");
		helper.assertTrue(a.onDeath(Bucket.FIRE) && a.immune(Bucket.FIRE) && a.multiplier(Bucket.FIRE) == 0f, "killed by fire: immune to fire");
		a.onDeath(Bucket.MELEE);
		helper.assertFalse(a.immune(Bucket.MELEE), "melee is never an immunity");
		helper.assertTrue(Math.abs(a.multiplier(Bucket.MELEE) - 0.25f) < 1e-4, "melee only reaches -75%");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void adaptationKeepsAFloor(GameTestHelper helper) {
		DoomsdayAdaptation a = new DoomsdayAdaptation();
		Bucket[] order = { Bucket.FIRE, Bucket.PROJECTILE, Bucket.EXPLOSION, Bucket.ENERGY, Bucket.MAGIC };
		for (Bucket b : order) {
			a.onDeath(b);
		}
		int hardened = 0;
		for (Bucket b : DoomsdayAdaptation.BUCKETS) {
			if (a.multiplier(b) <= 0.25f + 1e-4) {
				hardened++;
			}
		}
		helper.assertTrue(hardened == DoomsdayConfig.adaptation().maxHardened, "at most 4 hardened: " + hardened);
		helper.assertTrue(a.multiplier(Bucket.FIRE) == 1f, "the oldest (fire) decayed back to nothing");
		helper.assertTrue(a.immune(Bucket.MAGIC), "the newest stays");
		DoomsdayAdaptation copy = new DoomsdayAdaptation();
		copy.load(a.save());
		helper.assertTrue(copy.mask() == a.mask(), "saves and loads");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void damageIsSortedIntoBuckets(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DoomsdayEntity d = spawn(helper);
		var s = level.damageSources();
		helper.assertTrue(DoomsdayAdaptation.classify(s.onFire(), d) == Bucket.FIRE, "fire");
		helper.assertTrue(DoomsdayAdaptation.classify(s.explosion(null, null), d) == Bucket.EXPLOSION, "explosion");
		helper.assertTrue(DoomsdayAdaptation.classify(s.magic(), d) == Bucket.MAGIC, "magic");
		helper.assertTrue(DoomsdayAdaptation.classify(s.lightningBolt(), d) == Bucket.ENERGY, "lightning");
		helper.assertTrue(DoomsdayAdaptation.classify(s.genericKill(), d) == null, "/kill is never adapted to");
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.moveTo(d.getX() + 1.5, d.getY(), d.getZ());
		helper.assertTrue(DoomsdayAdaptation.classify(s.playerAttack(p), d) == Bucket.MELEE, "a punch up close is melee");
		p.moveTo(d.getX() + 20, d.getY(), d.getZ());
		helper.assertTrue(DoomsdayAdaptation.classify(s.playerAttack(p), d) == Bucket.POWER, "a 'player attack' from 20 blocks is a hero power");
		p.moveTo(d.getX() + 1.5, d.getY(), d.getZ());
		if (com.projecthero.mod.kryptonian.item.KryptonianItems.KRYPTONITE_SHARD != null) {
			p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.projecthero.mod.kryptonian.item.KryptonianItems.KRYPTONITE_SHARD));
			helper.assertTrue(DoomsdayAdaptation.classify(s.playerAttack(p), d) == null, "kryptonite in hand: never adapted to");
		}
		d.discard();
		helper.succeed();
	}

	// ---------------------------------------------------------------- the boss: death, the corpse, revival

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void deathLeavesACorpseThatRevivesAdapted(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DoomsdayEntity d = spawn(helper);
		try {
			helper.assertTrue(d.life() == 1 && d.bound(), "life 1, bound");
			helper.assertTrue(d.getMaxHealth() == 3000f, "3000 health");
			double scale1 = d.getAttributeValue(Attributes.SCALE);
			kill(level, d);
			helper.assertTrue(d.isAlive() && d.isCorpse(), "he drops as a corpse, not dead");
			helper.assertTrue(d.deaths() == 1 && !d.finalDeath(), "one death, not the last");
			helper.assertTrue(d.adaptation().immune(Bucket.FIRE), "killed by fire: immune to fire");
			helper.assertFalse(d.hurt(level.damageSources().magic(), 100f), "a corpse can't be hurt");
			d.debugFinishRevival();
			d.setNoAi(false);
			helper.runAfterDelay(5, () -> {
				helper.assertTrue(d.life() == 2 && !d.isCorpse(), "he rises for life 2: " + d.life() + " " + d.action());
				helper.assertFalse(d.bound(), "the cables are gone");
				helper.assertTrue(d.getAttributeValue(Attributes.SCALE) > scale1, "bigger");
				helper.assertTrue(Math.abs(d.getHealth() - d.getMaxHealth()) < 1f, "full health");
				d.invulnerableTime = 0;
				helper.assertFalse(d.hurt(level.damageSources().onFire(), 50f), "fire does nothing now");
				d.discard();
				helper.succeed();
			});
		} catch (RuntimeException ex) {
			d.discard();
			throw ex;
		}
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void revivalIsSlowerInTheDark(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DoomsdayEntity d = spawn(helper);
		// a roof over him: no sunlight
		for (int x = 0; x < 9; x++) {
			for (int z = 0; z < 9; z++) {
				level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 9, z)), Blocks.STONE.defaultBlockState());
			}
		}
		kill(level, d);
		d.setNoAi(false);
		double start = d.debugRevival()[0];
		helper.runAfterDelay(20, () -> {
			double gained = d.debugRevival()[0] - start;
			double expected = 20.0 * DoomsdayConfig.revival().daySeconds / DoomsdayConfig.revival().darkSeconds;
			helper.assertTrue(gained > expected * 0.6 && gained < expected * 1.4, "half speed in the dark: " + gained + " vs " + expected);
			d.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void onlyAFinalCorpseCanBeBanished(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DoomsdayEntity d = spawn(helper);
		helper.assertFalse(d.banish(level, null), "not while he stands");
		kill(level, d);
		helper.assertFalse(d.banish(level, null), "not while he has lives left");
		d.discard();
		DoomsdayEntity f = spawn(helper);
		f.debugSetLife(3);
		kill(level, f);
		helper.assertTrue(f.isCorpse() && f.finalDeath(), "the third death is final");
		helper.assertTrue(f.banish(level, null), "the Projector takes");
		f.setNoAi(false);
		helper.runAfterDelay(DoomsdayEntity.BANISH_TICKS + 10, () -> {
			helper.assertTrue(f.isRemoved(), "pulled into the Phantom Zone");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the event

	@GameTest(template = EMPTY_STRUCTURE, batch = "doomsday_event", timeoutTicks = 60)
	public void eventOpensTheCasketAndHandsOverTheProjector(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		for (DoomsdayEvent old : new java.util.ArrayList<>(DoomsdayEvent.all(level.getServer()))) {
			old.abort(level);
			EventSavedData.get(level).remove(old.id());
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 2000, 4));
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		DoomsdayEvent e = Doomsday.start(level, at, p, Doomsday.Source.CASKET, 0f, x -> x.instant = true);
		helper.assertTrue(e != null && e.state() == EventState.RUNNING && e.phase() == DoomsdayEvent.Phase.IMPACT, "it starts, casket first");
		try {
			helper.assertTrue(Doomsday.start(level, at.east(3), p, Doomsday.Source.COMMAND, 0f) == null, "a second one nearby is refused");
			e.debugAdvance(level, p);
			DoomsdayEntity d = e.doomsday(level);
			helper.assertTrue(d != null && e.phase() == DoomsdayEvent.Phase.FIGHT, "he is out");
			helper.assertTrue(d.eventId() != null && d.bound() && d.life() == 1, "bound to the event, still bound in cables");
			helper.assertTrue(d.action() == DoomsdayEntity.ACTION_EMERGE && !d.hurt(level.damageSources().magic(), 50f),
					"he tears his way out untouchable");
			d.setAction(DoomsdayEntity.ACTION_NONE);
			d.debugSetLife(3);
			kill(level, d);
			helper.assertTrue(d.finalDeath() && e.projectorGiven(), "the third death hands the Projector over");
			// other tests' mock players can stand inside the event too, and the nearest fighter gets it
			helper.assertTrue(level.players().stream().mapToInt(x -> x.getInventory().countItem(DoomsdayItems.PROJECTOR)).sum() == 1, "exactly one fighter holds it");
			helper.assertTrue(p.getInventory().countItem(DoomsdayItems.PHANTOM_SHARD) == 0, "no shards left over");
		} finally {
			if (e.state().active()) {
				e.abort(level);
			}
			EventSavedData.get(level).remove(e.id());
		}
		helper.assertTrue(p.getInventory().countItem(DoomsdayItems.PROJECTOR) == 0, "the Projector never outlives the event");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the reward

	/**
	 * v0.17.2 (user: "give him a staggered stance where he takes 300% more damage, make the staggered stance last 15 seconds"): once
	 * per life at half health -- 4x damage that teaches him nothing, no regeneration, over after 15 s.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void halfHealthStaggersHimForFifteenSecondsAtFourTimesTheDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DoomsdayEntity d = spawn(helper);
		d.debugUnbind();
		d.invulnerableTime = 0;
		d.hurt(level.damageSources().magic(), 1400f);
		helper.assertFalse(d.staggered(), "above half health: no stagger");
		d.invulnerableTime = 0;
		d.hurt(level.damageSources().magic(), 400f);
		helper.assertTrue(d.staggered(), "below half health: staggered (" + d.getHealth() + ")");
		float before = d.getHealth();
		d.invulnerableTime = 0;
		d.hurt(level.damageSources().onFire(), 10f);
		helper.assertTrue(Math.abs(before - d.getHealth() - 40f) < 0.5f, "4x damage: took " + (before - d.getHealth()));
		helper.assertTrue(d.adaptation().multiplier(Bucket.FIRE) >= 0.999f, "the staggered hits teach him nothing");
		float held = d.getHealth();
		d.setNoAi(false);
		helper.runAfterDelay(100, () -> helper.assertTrue(d.staggered() && d.getHealth() <= held + 0.01f, "still reeling, no regeneration"));
		helper.runAfterDelay(DoomsdayConfig.boss().staggerSeconds * 20 + 20, () -> {
			try {
				helper.assertFalse(d.staggered(), "back up after 15 s");
				d.setNoAi(true);
				d.invulnerableTime = 0;
				d.hurt(level.damageSources().magic(), 300f);
				helper.assertFalse(d.staggered(), "only once a life");
			} finally {
				d.discard();
			}
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void boneGauntletRemembersWhatItKills(GameTestHelper helper) {
		ItemStack g = new ItemStack(DoomsdayItems.BONE_GAUNTLET);
		BoneGauntletItem.learn(g, EntityType.ZOMBIE);
		BoneGauntletItem.learn(g, EntityType.ZOMBIE);
		helper.assertTrue(BoneGauntletItem.learned(g).size() == 1, "each kind once");
		for (EntityType<?> t : new EntityType<?>[] { EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.WITCH }) {
			BoneGauntletItem.learn(g, t);
		}
		helper.assertTrue(BoneGauntletItem.learned(g).size() == DoomsdayConfig.rewards().gauntletMaxTypes, "remembers 4");
		helper.assertFalse(BoneGauntletItem.learned(g).contains("minecraft:zombie"), "the oldest is forgotten");
		helper.assertTrue(new ItemStack(Items.STICK).getCount() == 1, "sanity");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.17.2 round 2: the new moves

	private static net.minecraft.world.entity.monster.Zombie dummy(GameTestHelper helper, Vec3 rel) {
		var z = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(rel);
		z.moveTo(at.x, at.y, at.z, 0f, 0f);
		z.setNoAi(true);
		z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		z.setHealth(1000f);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	/** Ticks a NoAI Doomsday's brain by hand until the move is over (or a cap). Returns the ticks it took. */
	private static int runMove(DoomsdayEntity d, int cap) {
		int n = 0;
		do {
			d.debugTickBrain();
			n++;
		} while (!d.debugMoveIdle() && n < cap);
		return n;
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void everyNewMoveStartsAndFinishes(GameTestHelper helper) {
		DoomsdayEntity d = spawn(helper);
		var z = dummy(helper, new Vec3(4.5, 2, 13.5));
		try {
			d.debugUnbind();
			d.debugSetLife(3);
			d.adaptation().onDeath(Bucket.FIRE);
			d.setTarget(z);
			for (String m : new String[] { "CLAP", "HURL", "NOVA", "RAMPAGE", "SKYFALL" }) {
				helper.assertTrue(d.debugStartMove(m), m + " starts");
				int took = runMove(d, 400);
				helper.assertTrue(d.debugMoveIdle(), m + " finishes (" + took + " ticks)");
				helper.assertTrue(!d.isNoGravity(), m + ": gravity is back afterwards");
			}
		} finally {
			d.discard();
			z.discard();
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void novaUnleashesWhatHeAdaptedToMost(GameTestHelper helper) {
		DoomsdayEntity d = spawn(helper);
		var z = dummy(helper, new Vec3(4.5, 2, 8.5));
		try {
			d.debugUnbind();
			d.debugSetLife(3);
			d.setTarget(z);
			helper.assertTrue(d.strongestAdaptation() == null, "nothing learned yet");
			d.adaptation().record(Bucket.PROJECTILE, 700f, 3000);
			d.adaptation().onDeath(Bucket.ENERGY);
			helper.assertTrue(d.strongestAdaptation() == Bucket.ENERGY, "an immunity outranks a resistance");
			helper.assertTrue(d.debugStartMove("NOVA") && d.novaElement() == Bucket.ENERGY, "the Nova channels energy");
			runMove(d, 200);
			helper.assertTrue(d.novaElement() == null, "and lets go of it");
		} finally {
			d.discard();
			z.discard();
		}
		helper.succeed();
	}

	/** Rampage obeys abilityTerrainDamage: off, a dirt wall stops him dead (he crashes); on, he ploughs through it. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void rampageOnlyBreaksBlocksWhenTerrainDamageIsOn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var cfg = com.projecthero.mod.hero.HeroConfig.get();
		boolean was = cfg.abilityTerrainDamage;
		java.util.List<BlockPos> wall = new java.util.ArrayList<>();
		for (int x = 1; x <= 7; x++) {
			for (int y = 1; y <= 5; y++) {
				wall.add(helper.absolutePos(new BlockPos(x, y, 5)));
			}
		}
		DoomsdayEntity d = spawn(helper);
		var z = dummy(helper, new Vec3(4.5, 1, 7.5));
		// inside the test area (its edge is a barrier wall he can't break either)
		Vec3 home = helper.absoluteVec(new Vec3(4.5, 1, 1.5));
		d.teleportTo(home.x, home.y, home.z);
		try {
			d.debugUnbind();
			d.setTarget(z);
			for (BlockPos p : wall) {
				level.setBlockAndUpdate(p, Blocks.DIRT.defaultBlockState());
			}
			cfg.abilityTerrainDamage = false;
			helper.assertTrue(d.debugStartMove("RAMPAGE"), "rampage starts");
			boolean crashed = false;
			for (int i = 0; i < 200 && !d.debugMoveIdle(); i++) {
				d.debugTickBrain();
				crashed |= d.crashed();
			}
			helper.assertTrue(crashed, "terrain damage off: the wall stops him and he reels");
			for (BlockPos p : wall) {
				helper.assertTrue(level.getBlockState(p).is(Blocks.DIRT), "terrain damage off: the wall stands at " + p);
			}
			cfg.abilityTerrainDamage = true;
			level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING).set(true, level.getServer());
			helper.assertTrue(DoomsdayEntity.griefing(level), "terrain damage + mob griefing on");
			d.teleportTo(home.x, home.y, home.z);
			helper.assertTrue(d.debugStartMove("RAMPAGE"), "rampage starts again");
			runMove(d, 200);
			int gone = 0;
			for (BlockPos p : wall) {
				if (level.getBlockState(p).isAir()) {
					gone++;
				}
			}
			helper.assertTrue(gone > 0, "terrain damage on: he ploughs a hole through the wall (he got to " + d.position() + ", crashed " + d.crashed() + ")");
		} finally {
			cfg.abilityTerrainDamage = was;
			for (BlockPos p : wall) {
				level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
			}
			d.discard();
			z.discard();
		}
		helper.succeed();
	}
}
