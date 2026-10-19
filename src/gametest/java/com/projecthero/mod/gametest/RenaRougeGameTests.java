package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.renarouge.RenaRouge;
import com.projecthero.mod.miraculous.renarouge.RenaRougeConfig;
import com.projecthero.mod.miraculous.renarouge.RenaRougeIllusions;
import com.projecthero.mod.miraculous.renarouge.RenaRougeMoves;
import com.projecthero.mod.miraculous.renarouge.entity.FakeWallEntity;
import com.projecthero.mod.miraculous.renarouge.entity.FluteBoomerangEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.6 Rena Rouge (Trixx): one test per move -- the server effect and its number -- and the clean-up on
 * detransformation. Mock players are not ticked by the server, so tests that run over time drive the Miraculous tick
 * themselves ({@link #pump}). Hostile mobs need a non-peaceful difficulty (NORMAL is set by {@link #mob}).
 */
public class RenaRougeGameTests implements FabricGameTest {
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

	/** Rena Rouge facing +Z, the transformation finished, every move ready. */
	private static ServerPlayer rena(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = mock(helper, x, y, z);
		helper.assertTrue(Miraculous.grant(p, Kwami.TRIXX), "Trixx is granted");
		Miraculous.transform(p);
		ready(helper, p);
		helper.assertTrue(Miraculous.canAct(p), "the transformation has finished");
		return p;
	}

	private static void ready(GameTestHelper helper, ServerPlayer p) {
		long now = helper.getLevel().getGameTime();
		Miraculous.edit(p, n -> {
			n.formChangeAt = now - 200L;
			n.animId = MiraculousState.ANIM_NONE;
			n.abilityReadyAt.clear();
		});
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

	private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double x, double y, double z, double health, boolean ai) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		T m = type.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180.0f, 0.0f);
		m.setNoAi(!ai);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		m.setHealth((float) health);
		if (m.getAttribute(Attributes.ARMOR) != null) {
			m.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
		}
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	private static List<RenaIllusionEntity> illusions(GameTestHelper helper, ServerPlayer p) {
		return helper.getLevel().getEntitiesOfClass(RenaIllusionEntity.class, p.getBoundingBox().inflate(48.0),
				e -> p.getUUID().equals(e.getOwnerUUID()));
	}

	// ---------------------------------------------------------------- R

	@GameTest(template = EMPTY_STRUCTURE)
	public void fluteStrikeComboHitsForNine(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.5, 100, false);
		helper.assertTrue(RenaRougeMoves.strike(p) == z, "the strike hits the zombie in front");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - RenaRougeConfig.STRIKE_DAMAGE) < EPS, "9 a hit, took " + (100f - z.getHealth()));
		helper.assertTrue(Miraculous.state(p).val(RenaRouge.V_COMBO) == 1, "combo step 1");
		RenaRougeMoves.strike(p);
		helper.assertTrue(Miraculous.state(p).val(RenaRouge.V_COMBO) == 2, "combo step 2");
		RenaRougeMoves.strike(p);
		helper.assertTrue(Miraculous.state(p).val(RenaRouge.V_COMBO) == 3, "combo step 3 (the overhead finisher)");
		helper.assertTrue(Math.abs((100f - z.getHealth()) - 3 * RenaRougeConfig.STRIKE_DAMAGE) < 3 * EPS, "three hits: 27, took " + (100f - z.getHealth()));
		helper.assertTrue(!Miraculous.ready(p, RenaRougeConfig.STRIKE), "the strike is on its cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void fluteSweepHitsAllRoundForSevenWithKnockback(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 3.5);
		Zombie a = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100, false);
		Zombie b = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 1.5, 100, false);
		Zombie c = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 3.5, 100, false);
		int n = RenaRougeMoves.sweep(p);
		helper.assertTrue(n == 3, "all three hit, got " + n);
		for (Zombie z : new Zombie[] { a, b, c }) {
			helper.assertTrue(Math.abs((100f - z.getHealth()) - RenaRougeConfig.SWEEP_DAMAGE) < EPS, "7 each, took " + (100f - z.getHealth()));
			Vec3 away = z.position().subtract(p.position()).multiply(1, 0, 1).normalize();
			helper.assertTrue(z.getDeltaMovement().dot(away) > 0.2, "knocked back away from her");
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- G

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void fluteThrowHitsAndComesBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 4.5, 2.0, 1.5);
		// v0.16.6 (user: the throw missed what was aimed at): a zombie straight under the crosshair 5 blocks ahead -- the old
		// fixed bow swung 3+ blocks wide of it; the arc now ends on what she aims at
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 6.5, 100, false);
		pump(helper, p);
		FluteBoomerangEntity f = RenaRougeMoves.throwFlute(p);
		helper.assertTrue(f != null && RenaRouge.fluteOut(p), "the flute is out");
		helper.assertTrue(RenaRougeMoves.throwFlute(p) == null, "a second throw is refused while it is out");
		helper.runAfterDelay(80, () -> {
			float taken = 100f - z.getHealth();
			helper.assertTrue(taken >= RenaRougeConfig.THROW_DAMAGE - EPS && taken <= 2 * RenaRougeConfig.THROW_DAMAGE + EPS,
					"10 a pass (out and back), took " + taken);
			helper.assertTrue(f.isRemoved(), "the flute came back and was caught");
			helper.assertTrue(!RenaRouge.fluteOut(p), "the flute is no longer out");
			helper.assertTrue(Miraculous.state(p).abilityReadyAt.containsKey(RenaRougeConfig.THROW), "the throw's cooldown was set on the catch");
			helper.succeed();
		});
	}

	/** v0.16.6: aim assist -- a zombie a few degrees off the crosshair is locked and hit. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void fluteThrowAimAssistLocksANearMiss(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 4.5, 2.0, 1.5);
		// about 4 degrees to her left of the crosshair, 7 blocks out
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.0, 2.0, 8.5, 100, false);
		pump(helper, p);
		helper.assertTrue(RenaRougeMoves.throwFlute(p) != null, "thrown");
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(z.getHealth() <= 100f - RenaRougeConfig.THROW_DAMAGE + EPS, "the near miss was locked and hit, health " + z.getHealth());
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void melodyLureWalksMobsToWhereSheLooks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 1.5, 2.0, 1.5);
		// she looks down at the floor ahead (+Z)
		p.setXRot(35f);
		Zombie z = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 1.5, 100, true);
		pump(helper, p);
		int n = RenaRougeMoves.lure(p);
		helper.assertTrue(n >= 1, "the zombie is lured");
		Vec3 point = RenaRougeMoves.lurePoint(p);
		helper.assertTrue(point != null, "a lure point");
		double before = z.position().distanceTo(point);
		helper.runAfterDelay(60, () -> {
			double after = z.position().distanceTo(point);
			helper.assertTrue(after < before - 1.0, "the zombie walked toward the point: " + before + " -> " + after);
			helper.assertTrue(z.getTarget() == null, "it does not go for anyone meanwhile");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void foxDashLeavesADecoyTheZombieAttacks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 6.5, 2.0, 1.5, 100, false);
		z.setTarget(p);
		pump(helper, p);
		Vec3 start = p.position();
		RenaIllusionEntity decoy = RenaRougeMoves.dash(p);
		helper.assertTrue(decoy != null && decoy.kind() == RenaIllusionEntity.DECOY, "a decoy is left behind");
		helper.assertTrue(decoy.position().distanceTo(start) < 0.1, "where she stood");
		helper.assertTrue(p.position().distanceTo(start) > 2.5, "she darted forward " + p.position().distanceTo(start));
		helper.assertTrue(z.getTarget() == decoy, "the zombie goes for the decoy");
		helper.runAfterDelay(RenaRougeConfig.DASH_DECOY_TICKS + RenaRougeConfig.DISSOLVE_TICKS + 4, () -> {
			helper.assertTrue(decoy.isRemoved(), "the decoy lasts a second, then dissolves");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void illusionSwapTradesPlaces(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 2.5, 2.0, 2.5);
		RenaIllusionEntity d = RenaRougeMoves.illusionDouble(p);
		helper.assertTrue(d != null, "a Double");
		d.setNoAi(true);
		Vec3 there = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
		d.moveTo(there.x, there.y, there.z, 0f, 0f);
		Vec3 here = p.position();
		helper.assertTrue(RenaRougeMoves.swap(p), "swapped");
		helper.assertTrue(p.position().distanceTo(there) < 0.1, "she is where the Double was");
		helper.assertTrue(d.position().distanceTo(here) < 0.1, "the Double is where she was");
		RenaRougeIllusions.dissolveAll(p);
		ready(helper, p);
		helper.runAfterDelay(RenaRougeConfig.DISSOLVE_TICKS + 4, () -> {
			helper.assertFalse(RenaRougeMoves.swap(p), "no illusion: no swap");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void illusionDoubleFightsAndIsTargeted(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 1.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 200, true);
		z.setTarget(p);
		pump(helper, p);
		RenaIllusionEntity d = RenaRougeMoves.illusionDouble(p);
		helper.assertTrue(d != null && d.kind() == RenaIllusionEntity.DOUBLE, "the Double");
		helper.assertTrue(z.getTarget() == d, "the zombie turns on the Double");
		float expect = (float) (p.getAttributeValue(Attributes.ATTACK_DAMAGE) * RenaRougeConfig.DOUBLE_DAMAGE_SHARE);
		helper.assertTrue(Math.abs(d.meleeDamage() - expect) < 0.01f, "half her damage: " + d.meleeDamage() + " vs " + expect);
		helper.runAfterDelay(140, () -> {
			helper.assertTrue(z.getHealth() < 200f, "the Double hit the zombie (200 -> " + z.getHealth() + ")");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 260)
	public void mirageMakesFiveCopiesAWallAndConfusesMobs(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 1.5);
		Zombie[] zs = { mob(helper, EntityType.ZOMBIE, 1.5, 2.0, 6.5, 250, true), mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 6.5, 250, true),
				mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 250, true) };
		for (Zombie z : zs) {
			z.setTarget(p);
		}
		boolean[] infight = { false };
		pump(helper, p);
		helper.onEachTick(() -> {
			for (Zombie z : zs) {
				if (z.getTarget() instanceof Zombie) {
					infight[0] = true;
				}
			}
		});
		helper.assertTrue(RenaRougeMoves.mirage(p, com.projecthero.mod.miraculous.renarouge.MirageIllusion.FOXES), "the Mirage starts (Army of Foxes)");
		helper.assertFalse(RenaRougeMoves.mirage(p, com.projecthero.mod.miraculous.renarouge.MirageIllusion.FOXES), "a second one is refused while it runs");
		helper.assertTrue(Miraculous.countdownLeft(p) == 0, "no countdown until the bubble pops");
		int pop = RenaRougeConfig.MIRAGE_PLAY_TICKS + RenaRougeConfig.MIRAGE_BUBBLE_TICKS + 6;
		helper.runAfterDelay(pop, () -> {
			long copies = illusions(helper, p).stream().filter(e -> e.kind() == RenaIllusionEntity.MIRAGE).count();
			helper.assertTrue(copies == RenaRougeConfig.MIRAGE_COPIES, "five copies, got " + copies);
			helper.assertTrue(Miraculous.countdownLeft(p) > 0, "the countdown started when the bubble popped");
			helper.assertTrue(!helper.getLevel().getEntitiesOfClass(FakeWallEntity.class, p.getBoundingBox().inflate(32)).isEmpty(), "a wall");
			for (Zombie z : zs) {
				helper.assertTrue(RenaRougeMoves.isConfused(p, z), "every zombie is confused");
			}
		});
		helper.runAfterDelay(pop + 150, () -> {
			helper.assertTrue(infight[0], "the confused zombies turned on each other");
			for (Zombie z : zs) {
				helper.assertTrue(z.getTarget() != p, "no zombie is after her");
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- V

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void foxCamouflageHidesHerUntilSheAttacks(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 2.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 4.5, 100, false);
		z.setTarget(p);
		RenaRougeMoves.camouflage(p);
		helper.assertTrue(RenaRouge.camouflaged(p) && p.hasEffect(MobEffects.INVISIBILITY), "camouflaged and invisible");
		helper.assertTrue(z.getTarget() == null, "the zombie lost her");
		helper.assertTrue(Miraculous.ready(p, RenaRougeConfig.CAMO), "no cooldown while it runs");
		RenaRougeMoves.strike(p);
		helper.assertTrue(!RenaRouge.camouflaged(p) && !p.hasEffect(MobEffects.INVISIBILITY), "an attack ends it");
		helper.assertTrue(!Miraculous.ready(p, RenaRougeConfig.CAMO), "its cooldown starts when it ends");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40)
	public void fakeWallBlocksTheMobsSight(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 1.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 6.5, 100, false);
		helper.assertTrue(z.hasLineOfSight(p), "it sees her before the wall");
		FakeWallEntity w = RenaRougeMoves.fakeWall(p);
		helper.assertTrue(w != null, "a wall");
		AABB b = w.wallBox();
		helper.assertTrue(Math.round(Math.max(b.getXsize(), b.getZsize())) == RenaRougeConfig.WALL_WIDTH && Math.round(b.getYsize()) == RenaRougeConfig.WALL_HEIGHT,
				"5 x 3: " + b);
		helper.assertTrue(w.look().is(Blocks.STONE), "made of the ground under it");
		helper.assertFalse(z.hasLineOfSight(p), "the wall blocks its sight");
		helper.assertTrue(p.level().noCollision(z, z.getBoundingBox().move(0, 0, -2.5)), "but nothing solid is there");
		w.discard();
		helper.assertTrue(z.hasLineOfSight(p), "it sees her again when the wall is gone");
		helper.succeed();
	}

	// ---------------------------------------------------------------- C

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void foxPounceLandingStunsMobsBelow(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 4.5, 2.0, 4.5, 100, false);
		Zombie far = mob(helper, EntityType.ZOMBIE, 0.5, 2.0, 7.5, 100, false);
		pump(helper, p);
		RenaRougeMoves.pounce(p);
		helper.assertTrue(p.getDeltaMovement().y > 1.0, "a high leap");
		helper.assertTrue(RenaRouge.val(p, RenaRouge.V_POUNCE) != 0L, "in the air");
		p.setOnGround(false);
		helper.runAfterDelay(6, () -> p.setOnGround(true));
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(RenaRouge.val(p, RenaRouge.V_POUNCE) == 0L, "landed");
			helper.assertTrue(PunisherControl.isStunned(z), "the zombie beside the landing is stunned");
			helper.assertFalse(PunisherControl.isStunned(far), "one 4+ blocks away is not");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void foxFeintSendsTheHitToAnIllusion(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 3.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 3.5, 2.0, 5.5, 100, false);
		p.setHealth(p.getMaxHealth());
		RenaRougeMoves.feint(p);
		float hp = p.getHealth();
		Vec3 stood = p.position();
		boolean hurt = p.hurt(p.damageSources().mobAttack(z), 6f);
		helper.assertFalse(hurt, "the hit is cancelled");
		helper.assertTrue(p.getHealth() == hp, "no damage taken");
		helper.assertTrue(illusions(helper, p).stream().anyMatch(e -> e.kind() == RenaIllusionEntity.FEINT && e.position().distanceTo(stood) < 0.1),
				"an illusion popped in where she stood");
		helper.runAfterDelay(RenaRougeConfig.FEINT_TICKS + 2, () -> {
			p.invulnerableTime = 0;
			float before = p.getHealth();
			p.hurt(p.damageSources().mobAttack(z), 6f);
			helper.assertTrue(p.getHealth() < before, "after 3 s hits land again");
			helper.succeed();
		});
		pump(helper, p);
	}

	// ---------------------------------------------------------------- illusions + clean-up

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void aPlayersHitDissolvesAnIllusionAMobsChipsIt(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 1.5, 2.0, 1.5);
		ServerPlayer other = mock(helper, 6.5, 2.0, 6.5);
		Zombie z = mob(helper, EntityType.ZOMBIE, 5.5, 2.0, 1.5, 100, false);
		RenaIllusionEntity d = RenaRougeMoves.illusionDouble(p);
		helper.assertTrue(d != null, "a Double");
		d.setNoAi(true);
		int hits = d.hitsLeft();
		d.hurt(d.damageSources().mobAttack(z), 4f);
		helper.assertTrue(!d.dissolving() && d.hitsLeft() == hits - 1, "a mob's hit costs one of its hits");
		helper.assertFalse(d.hurt(d.damageSources().playerAttack(p), 4f), "her own hits pass through it");
		d.hurt(d.damageSources().playerAttack(other), 1f);
		helper.assertTrue(d.dissolving(), "another player's hit dissolves it at once");
		helper.runAfterDelay(RenaRougeConfig.DISSOLVE_TICKS + 3, () -> {
			helper.assertTrue(d.isRemoved(), "gone after dissolving into light");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void detransformingDropsEverythingSheConjured(GameTestHelper helper) {
		floor(helper);
		ServerPlayer p = rena(helper, 3.5, 2.0, 1.5);
		pump(helper, p);
		RenaRougeMoves.illusionDouble(p);
		RenaRougeMoves.fakeWall(p);
		RenaRougeMoves.throwFlute(p);
		RenaRougeMoves.feint(p);
		RenaRougeMoves.camouflage(p);
		helper.assertTrue(!illusions(helper, p).isEmpty() && RenaRouge.camouflaged(p) && RenaRouge.fluteOut(p), "set up");
		Miraculous.detransform(p, false);
		helper.assertTrue(!p.hasEffect(MobEffects.INVISIBILITY), "camouflage gone");
		helper.assertTrue(Miraculous.state(p).vals.isEmpty(), "her synced values cleared: " + Miraculous.state(p).vals);
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(FluteBoomerangEntity.class, p.getBoundingBox().inflate(64)).isEmpty(), "the flute gone");
		helper.assertTrue(helper.getLevel().getEntitiesOfClass(FakeWallEntity.class, p.getBoundingBox().inflate(64)).isEmpty(), "the wall gone");
		helper.assertTrue(illusions(helper, p).stream().allMatch(RenaIllusionEntity::dissolving), "every illusion dissolving");
		helper.runAfterDelay(RenaRougeConfig.DISSOLVE_TICKS + 3, () -> {
			helper.assertTrue(illusions(helper, p).isEmpty(), "every illusion gone");
			helper.assertTrue(FakeWallEntity.ACTIVE.stream().noneMatch(w -> p.getUUID().equals(w.ownerId())), "no live wall left in the registry");
			helper.succeed();
		});
	}
}
