package com.projecthero.mod.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.p05.GeoTerrain;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.renarouge.MirageIllusion;
import com.projecthero.mod.miraculous.renarouge.RenaMirage;
import com.projecthero.mod.miraculous.renarouge.RenaRouge;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.miraculous.renarouge.RenaRougeMoves;
import com.projecthero.mod.miraculous.renarouge.entity.MirageFxEntity;
import com.projecthero.mod.miraculous.renarouge.entity.RenaFoxEntity;
import com.projecthero.mod.miraculous.renarouge.entity.RenaIllusionEntity;
import com.projecthero.mod.punisher.PunisherControl;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Rena Rouge's Mirage wheel: the wheel (nothing spent without a pick, the 2-minute cooldown from the pick,
 * the countdown at the pop, the 30 s), then one test per illusion -- its server effect, with a boss among the mobs
 * (bosses are never immune) -- the foxes' 6 and their squad rule, the Pitfall dug and filled back exactly, and the daze at
 * the end. Every test runs in its own batch: the illusions reach 20-30 blocks, over the neighbouring test areas.
 * Mock players are not ticked by the server, so the Miraculous tick is driven by {@link #pump}.
 */
public class RenaMirageGameTests implements FabricGameTest {
	private static final float EPS = 0.6f;

	// ---------------------------------------------------------------- helpers

	@SuppressWarnings("removal")
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		return p;
	}

	private static ServerPlayer rena(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.TRIXX), "Trixx is granted");
		Miraculous.transform(p);
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> {
			n.formChangeAt = now - 200L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
		helper.assertTrue(Miraculous.canAct(p), "the transformation has finished");
		return p;
	}

	private static void pump(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> {
			p.tickCount++;
			Miraculous.tick(p);
		});
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z, double health, boolean ai) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	/** A "boss" (400 max health, so {@code RenaRougeCombat.isBoss}). */
	private static Zombie boss(GameTestHelper helper, double x, double y, double z) {
		return zombie(helper, x, y, z, 400, true);
	}

	/** Passes, ending her illusion first: a cast left running would reach into the next batch's test (same spot). */
	private static void finish(GameTestHelper helper, ServerPlayer p) {
		RenaMirage.end(p, false);
		helper.succeed();
	}

	private static Vec3 rel(GameTestHelper helper, double x, double y, double z) {
		return helper.absoluteVec(new Vec3(x, y, z));
	}

	// ---------------------------------------------------------------- the wheel

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_wheel_escape")
	public void escapeOnTheWheelSpendsNothing(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 3.5);
		helper.assertFalse(RenaRougeMoves.chooseMirage(p, MirageIllusion.MONSTER.ordinal()), "no pick without the wheel open");
		helper.assertTrue(RenaRougeMoves.mirage(p), "Shift+Z opens the wheel");
		helper.assertTrue(RenaRougeMoves.mirageMenuOpen(p), "the offer is open");
		helper.assertTrue(Miraculous.ready(p, RenaRougeConfig.MIRAGE), "opening it spends nothing");
		helper.assertFalse(RenaRougeMoves.chooseMirage(p, -1), "Escape: no illusion");
		helper.assertTrue(Miraculous.ready(p, RenaRougeConfig.MIRAGE), "still no cooldown");
		helper.assertFalse(RenaRougeMoves.miragePending(p), "no melody, no bubble");
		helper.assertTrue(Miraculous.countdownLeft(p) == 0, "no countdown");
		helper.assertFalse(RenaRougeMoves.chooseMirage(p, 0), "the offer closed with the Escape");
		finish(helper, p);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_wheel_pick", timeoutTicks = 120)
	public void aPickCostsTwoMinutesAndThePopStartsThirtySecondsAndTheCountdown(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 3.5);
		pump(helper, p);
		helper.assertTrue(RenaRougeMoves.mirage(p), "the wheel opens");
		helper.assertTrue(RenaRougeMoves.chooseMirage(p, MirageIllusion.ECLIPSE.ordinal()), "Eclipse is picked");
		int cd = Miraculous.cooldownRemaining(p, RenaRougeConfig.MIRAGE);
		helper.assertTrue(cd >= RenaRougeConfig.MIRAGE_COOLDOWN - 2 && RenaRougeConfig.MIRAGE_COOLDOWN == 2400, "2 minutes of cooldown from the pick, got " + cd);
		helper.assertTrue(Miraculous.countdownLeft(p) == 0, "no countdown until the bubble pops");
		helper.assertTrue(RenaRougeMoves.miragePending(p), "the melody plays");
		helper.assertFalse(RenaRougeMoves.mirage(p), "no second wheel while one is on its way");
		int pop = RenaRougeConfig.MIRAGE_PLAY_TICKS + RenaRougeConfig.MIRAGE_BUBBLE_TICKS + 8;
		helper.runAfterDelay(pop, () -> {
			helper.assertTrue(RenaMirage.running(p) && RenaMirage.runningKind(p) == MirageIllusion.ECLIPSE, "the Eclipse runs after the pop");
			long left = RenaMirage.until(p) - helper.getLevel().getGameTime();
			helper.assertTrue(left > RenaRougeConfig.MIRAGE_TICKS - 12 && left <= RenaRougeConfig.MIRAGE_TICKS && RenaRougeConfig.MIRAGE_TICKS == 600,
					"30 s from the pop, left " + left);
			helper.assertTrue(Miraculous.countdownLeft(p) > 0, "the countdown started at the pop");
			helper.assertTrue(Miraculous.state(p).val(RenaRouge.V_MIRAGE) == RenaMirage.until(p), "synced for the HUD");
			finish(helper, p);
		});
	}

	// ---------------------------------------------------------------- 1. Army of Foxes

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_foxes", timeoutTicks = 80)
	public void armyOfFoxesBitesForSixAndNeverTheSquad(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 1.5);
		ServerPlayer mate = mock(helper, 4.5, 2.0, 1.5);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("RenaFoxes" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		Zombie z = zombie(helper, 3.5, 2.0, 5.5, 100, false);
		Zombie b = boss(helper, 1.5, 2.0, 6.5);
		b.setTarget(p);
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.FOXES, p.position());
		List<RenaFoxEntity> foxes = helper.getLevel().getEntitiesOfClass(RenaFoxEntity.class, p.getBoundingBox().inflate(16),
				f -> p.getUUID().equals(f.getOwnerUUID()));
		helper.assertTrue(foxes.size() == RenaRougeConfig.FOX_COUNT, "8 foxes, got " + foxes.size());
		long copies = helper.getLevel().getEntitiesOfClass(RenaIllusionEntity.class, p.getBoundingBox().inflate(16),
				e -> e.kind() == RenaIllusionEntity.MIRAGE).size();
		helper.assertTrue(copies == RenaRougeConfig.MIRAGE_COPIES, "and the five copies, got " + copies);
		helper.assertTrue(b.getTarget() instanceof RenaIllusionEntity, "the boss is fooled onto a copy");
		RenaFoxEntity fox = foxes.get(0);
		float before = z.getHealth();
		helper.assertTrue(fox.doHurtTarget(z), "the fox bites the zombie");
		helper.assertTrue(Math.abs((before - z.getHealth()) - RenaRougeConfig.FOX_DAMAGE) < EPS, "6 a bite, took " + (before - z.getHealth()));
		helper.assertFalse(fox.doHurtTarget(mate), "never her squadmate");
		helper.assertFalse(fox.doHurtTarget(p), "never her");
		helper.assertFalse(fox.hurt(mate.damageSources().playerAttack(mate), 5f), "a squadmate's swing passes through a fox");
		// a copy hit by a mob bursts into mist that blinds it
		RenaIllusionEntity copy = helper.getLevel().getEntitiesOfClass(RenaIllusionEntity.class, p.getBoundingBox().inflate(16),
				e -> e.kind() == RenaIllusionEntity.MIRAGE).get(0);
		copy.hurt(z.damageSources().mobAttack(z), 3f);
		helper.assertTrue(copy.dissolving(), "the copy bursts at the first hit");
		helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS) && PunisherControl.refusesTarget(z), "the hitter is blinded and confused");
		helper.runAfterDelay(30, () -> {
			try {
				for (RenaFoxEntity f : helper.getLevel().getEntitiesOfClass(RenaFoxEntity.class, p.getBoundingBox().inflate(32))) {
					helper.assertTrue(f.getTarget() != mate && f.getTarget() != p, "no fox goes for her or her squad");
				}
				helper.assertTrue(foxes.stream().anyMatch(f -> f.getTarget() == z || f.getTarget() == b), "the foxes went for the enemies");
			} finally {
				squads.disband(squad);
			}
			finish(helper, p);
		});
	}

	// ---------------------------------------------------------------- 2. Monster

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_monster", timeoutTicks = 60)
	public void monsterPanicsMobsAndBosses(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 1.5);
		Zombie z = zombie(helper, 3.5, 2.0, 5.5, 100, true);
		Zombie b = boss(helper, 5.5, 2.0, 6.5);
		Zombie rooted = boss(helper, 1.5, 2.0, 6.5);
		rooted.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0); // a Galactus stand-in: it can't walk
		for (Mob m : new Mob[] { z, b, rooted }) {
			m.setTarget(p);
		}
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.MONSTER, p.position());
		Vec3 beast = RenaMirage.center(p);
		double zBefore = z.distanceToSqr(beast);
		helper.assertTrue(!helper.getLevel().getEntitiesOfClass(MirageFxEntity.class, p.getBoundingBox().inflate(16),
				e -> e.kind() == MirageFxEntity.MONSTER).isEmpty(), "the beast is there for everyone to see");
		helper.runAfterDelay(12, () -> {
			for (Mob m : new Mob[] { z, b, rooted }) {
				helper.assertTrue(m.getTarget() == null, "nobody is after her");
				m.setTarget(p);
				helper.assertTrue(m.getTarget() == null, "and can't pick her again (bosses too)");
				helper.assertTrue(RenaMirage.affected(p).contains(m.getId()), "fooled");
			}
			helper.assertTrue(z.getNavigation().isInProgress() || z.distanceToSqr(beast) > zBefore + 0.5, "it runs from the beast");
			finish(helper, p);
		});
	}

	// ---------------------------------------------------------------- 3. Vanishing Act

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_vanish", timeoutTicks = 40)
	public void vanishingActHidesHerAndSendsRunners(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 1.5);
		Zombie z = zombie(helper, 3.5, 2.0, 5.5, 100, true);
		Zombie b = boss(helper, 5.5, 2.0, 6.5);
		z.setTarget(p);
		b.setTarget(p);
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.VANISH, p.position());
		helper.assertTrue(z.getTarget() != p && b.getTarget() != p, "they lose her");
		z.setTarget(p);
		b.setTarget(p);
		helper.assertTrue(z.getTarget() != p && b.getTarget() != p, "and can't pick her again (bosses too)");
		long runners = helper.getLevel().getEntitiesOfClass(RenaIllusionEntity.class, p.getBoundingBox().inflate(16),
				e -> e.kind() == RenaIllusionEntity.RUNNER).size();
		helper.assertTrue(runners == RenaRougeConfig.VANISH_RUNNERS, "three runners, got " + runners);
		z.setLastHurtByMob(p);
		z.setTarget(p);
		helper.assertTrue(z.getTarget() == p, "unless she hits it");
		finish(helper, p);
	}

	// ---------------------------------------------------------------- 4. Mirage Zone

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_zone", timeoutTicks = 40)
	public void mirageZoneSendsMobsOut(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 1.5, 2.0, 1.5);
		Zombie z = zombie(helper, 4.5, 2.0, 4.5, 100, true);
		Zombie b = boss(helper, 5.5, 2.0, 5.5);
		z.setTarget(p);
		b.setTarget(p);
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.ZONE, rel(helper, 4.5, 2.0, 4.5));
		helper.runAfterDelay(8, () -> {
			for (Mob m : new Mob[] { z, b }) {
				helper.assertTrue(RenaMirage.affected(p).contains(m.getId()), "inside: fooled");
				helper.assertTrue(m.getTarget() == null, "drops everything to get out");
			}
			finish(helper, p);
		});
	}

	// ---------------------------------------------------------------- 5. Golden Lure

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_lure", timeoutTicks = 80)
	public void goldenLureDrawsEveryMob(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 1.5, 2.0, 6.5);
		Zombie z = zombie(helper, 1.5, 2.0, 1.5, 100, true);
		Zombie b = boss(helper, 2.5, 2.0, 1.5);
		z.setTarget(p);
		b.setTarget(p);
		Vec3 prize = rel(helper, 6.5, 2.0, 6.5);
		double before = z.distanceToSqr(prize);
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.LURE, prize);
		helper.runAfterDelay(40, () -> {
			for (Mob m : new Mob[] { z, b }) {
				helper.assertTrue(m.getTarget() == null, "drops its target");
				helper.assertTrue(RenaMirage.affected(p).contains(m.getId()), "lured (bosses too)");
			}
			helper.assertTrue(z.distanceToSqr(prize) < before - 1.0, "walks to the prize: " + Math.sqrt(before) + " -> " + z.distanceTo(p));
			finish(helper, p);
		});
	}

	// ---------------------------------------------------------------- 6. Pitfall

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_pitfall", timeoutTicks = 40)
	public void pitfallDigsARealPitAndFillsItBackExactly(GameTestHelper helper) {
		if (!HeroConfig.get().abilityTerrainDamage) {
			helper.succeed(); // the server forbids terrain changes: nothing to dig
			return;
		}
		// three layers of natural ground (a coarse-dirt marker at the bottom to check it comes back exactly)
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				for (int y = 1; y <= 3; y++) {
					helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)),
							(y == 1 ? Blocks.COARSE_DIRT : y == 3 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 3);
				}
			}
		}
		ServerPlayer p = rena(helper, 0.5, 4.0, 0.5);
		ServerPlayer mate = mock(helper, 5.5, 4.0, 5.5);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("RenaPit" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		Map<BlockPos, BlockState> was = new HashMap<>();
		for (int x = 2; x <= 5; x++) {
			for (int z = 2; z <= 5; z++) {
				for (int y = 1; y <= 3; y++) {
					BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
					was.put(at, helper.getLevel().getBlockState(at));
				}
			}
		}
		Zombie z = zombie(helper, 3.5, 4.0, 0.5, 100, true);
		pump(helper, p);
		try {
			RenaRougeMoves.mirageNow(p, MirageIllusion.PITFALL, rel(helper, 4.5, 4.0, 4.5));
			List<BlockPos> cells = RenaMirage.pitCells(p);
			helper.assertTrue(cells.size() == (RenaRougeConfig.PIT_SIZE * RenaRougeConfig.PIT_SIZE - 1) * RenaRougeConfig.PIT_DEPTH,
					"a 4x4 pit 3 deep, minus the squadmate's column: " + cells.size());
			for (BlockPos c : cells) {
				helper.assertTrue(helper.getLevel().getBlockState(c).isAir(), "dug at " + c);
			}
			BlockPos mateColumn = helper.absolutePos(new BlockPos(5, 3, 5));
			helper.assertTrue(!helper.getLevel().getBlockState(mateColumn).isAir(), "never under a squadmate");
			helper.assertTrue(!helper.getLevel().getEntitiesOfClass(MirageFxEntity.class, p.getBoundingBox().inflate(16),
					e -> e.kind() == MirageFxEntity.PIT).isEmpty(), "the illusory floor over it");
		} catch (RuntimeException e) {
			squads.disband(squad);
			throw e;
		}
		// something fell in
		Zombie fallen = zombie(helper, 3.5, 1.0, 3.5, 100, false);
		helper.runAfterDelay(10, () -> {
			RenaMirage.endSoonForTest(p);
		});
		helper.runAfterDelay(14, () -> {
			try {
				helper.assertFalse(RenaMirage.running(p), "it ended");
				for (Map.Entry<BlockPos, BlockState> e : was.entrySet()) {
					helper.assertTrue(helper.getLevel().getBlockState(e.getKey()) == e.getValue(), "filled back exactly at " + e.getKey());
					helper.assertFalse(GeoTerrain.isTemp(helper.getLevel(), e.getKey()), "nothing left in the ledger");
				}
				helper.assertTrue(fallen.getY() >= helper.absoluteVec(new Vec3(0, 4.0, 0)).y - 0.1, "what fell in is lifted out, y " + fallen.getY());
			} finally {
				squads.disband(squad);
			}
			finish(helper, p);
		});
	}

	// ---------------------------------------------------------------- 7. Eclipse

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_eclipse", timeoutTicks = 40)
	public void eclipseBlindsEnemiesBeyondFourBlocks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 1.5, 2.0, 1.5);
		Zombie far = zombie(helper, 1.5, 2.0, 7.5, 100, true);
		Zombie b = boss(helper, 6.5, 2.0, 6.5);
		Zombie near = zombie(helper, 3.5, 2.0, 1.5, 100, true);
		far.setTarget(p);
		b.setTarget(p);
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.ECLIPSE, p.position());
		helper.assertTrue(far.getTarget() == null && b.getTarget() == null, "past 4 blocks they lose her (bosses too)");
		far.setTarget(p);
		helper.assertTrue(far.getTarget() == null, "and can't find her again");
		near.setTarget(p);
		helper.assertTrue(near.getTarget() == p, "close by, she is still seen");
		// their shots fly wide
		boolean scattered = false;
		for (int i = 0; i < 6 && !scattered; i++) {
			Arrow a = new Arrow(EntityType.ARROW, helper.getLevel());
			a.setOwner(far);
			a.setPos(far.getX(), far.getEyeY(), far.getZ());
			Vec3 v = new Vec3(0, 0, -1.6);
			a.setDeltaMovement(v);
			helper.getLevel().addFreshEntity(a);
			Vec3 out = a.getDeltaMovement();
			helper.assertTrue(Math.abs(out.length() - v.length()) < 1.0e-3, "same speed");
			scattered = out.normalize().dot(v.normalize()) < Math.cos(Math.toRadians(1.0));
			a.discard();
		}
		helper.assertTrue(scattered, "an arrow loosed in the night flies off course");
		finish(helper, p);
	}

	// ---------------------------------------------------------------- 8. Hall of Mirrors

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_mirrors", timeoutTicks = 40)
	public void hallOfMirrorsTurnsThemOnEachOther(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 1.5, 2.0, 1.5);
		Zombie a = zombie(helper, 3.5, 2.0, 5.5, 100, true);
		Zombie c = zombie(helper, 5.5, 2.0, 5.5, 100, true);
		Zombie b = boss(helper, 6.5, 2.0, 2.5);
		for (Mob m : new Mob[] { a, c, b }) {
			m.setTarget(p);
		}
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.MIRRORS, p.position());
		helper.runAfterDelay(RenaRougeConfig.MIRRORS_RETHINK + 2, () -> {
			for (Mob m : new Mob[] { a, c, b }) {
				helper.assertTrue(m.getTarget() instanceof Zombie && m.getTarget() != m, "each one is on another (bosses too): " + m + " -> " + m.getTarget());
				m.setTarget(p);
				helper.assertTrue(m.getTarget() != p, "and never on her");
			}
			finish(helper, p);
		});
	}

	// ---------------------------------------------------------------- the end

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_end", timeoutTicks = 40)
	public void theEndShattersAndDazes(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 1.5, 2.0, 1.5);
		Zombie z = zombie(helper, 5.5, 2.0, 5.5, 100, true);
		z.setTarget(p);
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.LURE, rel(helper, 6.5, 2.0, 6.5));
		helper.assertTrue(RenaMirage.affected(p).contains(z.getId()), "the zombie is lured");
		RenaMirage.endSoonForTest(p);
		helper.runAfterDelay(3, () -> {
			helper.assertFalse(RenaMirage.running(p), "the illusion ended");
			helper.assertTrue(Miraculous.state(p).val(RenaRouge.V_MIRAGE) == 0L, "the HUD knows");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(MirageFxEntity.class, p.getBoundingBox().inflate(16)).isEmpty(), "the prize is gone");
			helper.assertTrue(z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && PunisherControl.refusesTarget(z), "the zombie is dazed: slow, no target");
			finish(helper, p);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "rena_mirage_detransform", timeoutTicks = 40)
	public void detransformingEndsTheIllusion(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 3.5);
		pump(helper, p);
		RenaRougeMoves.mirageNow(p, MirageIllusion.FOXES, p.position());
		Miraculous.detransform(p, false);
		helper.runAfterDelay(RenaRougeConfig.DISSOLVE_TICKS + 4, () -> {
			helper.assertFalse(RenaMirage.running(p), "no illusion runs");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(RenaFoxEntity.class, p.getBoundingBox().inflate(32)).isEmpty(), "the foxes are gone");
			finish(helper, p);
		});
	}
}
