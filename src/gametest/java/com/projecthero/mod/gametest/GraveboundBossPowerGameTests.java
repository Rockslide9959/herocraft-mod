package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.event.boss.BossPowerController;
import com.projecthero.mod.event.boss.BossPowers;
import com.projecthero.mod.event.boss.BossTargets;
import com.projecthero.mod.event.entity.EmpoweredZombie;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.entity.RaidZombie;
import com.projecthero.mod.event.boss.power.CryokinesisBoss;
import com.projecthero.mod.event.boss.power.ElectrokinesisBoss;
import com.projecthero.mod.event.boss.power.SuperRegenerationBoss;
import com.projecthero.mod.event.boss.power.SuperStrengthBoss;
import com.projecthero.mod.hero.Ability;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p07.ElectrokinesisHandlers;
import com.projecthero.mod.hero.power.p09.FrostStacks;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.21: Gravebound power bosses run the revamped mutation kits.
 *
 * <ul>
 * <li>every mutation id maps to a boss controller, whose ability ids are the power's real (current) ability ids;</li>
 * <li>boss power selection can pick mutations players cannot currently obtain;</li>
 * <li>a boss with a given power uses several of its abilities against a target in range, and damages / stacks a
 * player's pet (asserted on the pet: mock players never see ALLOW_DAMAGE);</li>
 * <li>bosses never harm the raid's own mobs;</li>
 * <li>the boss shows its power on its nameplate.</li>
 * </ul>
 *
 * <p>The abilities are driven synchronously through the controller -- the same calls {@code EmpoweredZombie#aiStep}
 * makes (per-tick {@code serverTick}, then the 10-tick cadence) -- with the boss NoAI so pathing never moves it. Each
 * test works at its own far-off position so the 30-block area queries never reach a neighbouring test's mobs.
 */
public class GraveboundBossPowerGameTests implements FabricGameTest {

	// ---------------------------------------------------------------- helpers

	/** A far-away, per-test spot (gametests sit side by side; abilities reach ~30 blocks). */
	private static BlockPos arena(GameTestHelper helper, int slot) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		return new BlockPos(origin.getX() + 4000 + slot * 200, origin.getY() + 1, origin.getZ() + 4000);
	}

	/**
	 * Force-load the chunks around {@code center}, then run {@code body} a few ticks later once they are entity-ticking
	 * (entities in a merely-generated chunk are invisible to area queries). The chunks are released by {@link #release}.
	 */
	private static void inArena(GameTestHelper helper, BlockPos center, int radius, Runnable body) {
		ServerLevel level = helper.getLevel();
		for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
			for (int cz = (center.getZ() - radius - 48) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		helper.runAfterDelay(10, body);
	}

	private static void release(ServerLevel level, BlockPos center, int radius) {
		for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++) {
			for (int cz = (center.getZ() - radius - 48) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
	}

	private static void floor(ServerLevel level, BlockPos center, int radius) {
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				level.setBlockAndUpdate(center.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				for (int y = 0; y < 6; y++) {
					level.setBlockAndUpdate(center.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	/** Clear the fight's corridor (the boss's own spikes / cages / pillars from the last distance stay up otherwise). */
	private static void clearCorridor(ServerLevel level, BlockPos at) {
		for (int x = -4; x <= 21; x++) {
			for (int z = -4; z <= 4; z++) {
				for (int y = 0; y < 7; y++) {
					BlockPos p = at.offset(x, y, z);
					if (!level.getBlockState(p).isAir()) {
						level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	private static EmpoweredZombie boss(ServerLevel level, BlockPos at, String power) {
		EmpoweredZombie boss = RaidEntityTypes.EMPOWERED_ZOMBIE.create(level);
		boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
		boss.setNoAi(true);
		level.addFreshEntity(boss);
		boss.configure(power, null, false, 1);
		return boss;
	}

	/** A player's tamed pet: an ally, so a valid boss victim. Tough, so it lives through every ability. */
	private static Wolf pet(ServerLevel level, BlockPos at) {
		Wolf wolf = EntityType.WOLF.create(level);
		wolf.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
		wolf.setNoAi(true);
		wolf.setTame(true, false);
		wolf.setOwnerUUID(UUID.randomUUID());
		wolf.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0);
		wolf.setHealth(1000.0f);
		level.addFreshEntity(wolf);
		return wolf;
	}

	private static RaidZombie raidMob(ServerLevel level, Vec3 at) {
		RaidZombie z = RaidEntityTypes.RAID_ZOMBIE.create(level);
		z.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		z.setNoAi(true);
		z.setPersistenceRequired(); // no player is near the arena: a plain monster would despawn at once
		level.addFreshEntity(z);
		return z;
	}

	/**
	 * Drive {@code cycles} ability cycles exactly as the boss entity does: ten per-tick {@code serverTick}s, then the
	 * cooldown / scheduled / cast / tick cadence. Both entities are put back at their spots every cycle so blinks, leaps
	 * and knockbacks never walk the fight out of range.
	 */
	private static void drive(ServerLevel level, EmpoweredZombie boss, LivingEntity target, Vec3 bossAt, Vec3 targetAt,
			int cycles, Runnable eachCycle) {
		BossPowerController c = boss.primaryController();
		for (int i = 0; i < cycles; i++) {
			boss.teleportTo(bossAt.x, bossAt.y, bossAt.z);
			boss.setDeltaMovement(Vec3.ZERO);
			target.teleportTo(targetAt.x, targetAt.y, targetAt.z);
			target.setDeltaMovement(Vec3.ZERO);
			boss.setNoGravity(false);
			for (int t = 0; t < 10; t++) {
				boss.tickCount++;
				c.serverTick(level, target);
			}
			c.tickCooldowns(10);
			c.tickScheduled();
			if (!c.resolvePendingCast(level, target)) {
				c.tick(level, target);
			}
			if (eachCycle != null) {
				eachCycle.run();
			}
		}
	}

	/**
	 * v0.14.27: {@link #drive} until {@code done} holds (or {@code maxCycles} run out). Boss casts pick abilities and
	 * line-of-sight rolls at random, so a fixed cycle count made bossAbilitiesHurtAndMarkAPlayersPet flaky in CI.
	 */
	private static void driveUntil(ServerLevel level, EmpoweredZombie boss, LivingEntity target, Vec3 bossAt, Vec3 targetAt,
			int maxCycles, Runnable eachCycle, java.util.function.BooleanSupplier done) {
		for (int i = 0; i < maxCycles && !done.getAsBoolean(); i++) {
			drive(level, boss, target, bossAt, targetAt, 1, eachCycle);
		}
	}

	private static boolean mentionsKey(Component c, String key) {
		if (c == null) {
			return false;
		}
		if (c.getContents() instanceof TranslatableContents tc && tc.getKey().equals(key)) {
			return true;
		}
		for (Component sibling : c.getSiblings()) {
			if (mentionsKey(sibling, key)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- roster

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyMutationHasABossControllerWithItsRealAbilities(GameTestHelper helper) {
		helper.assertTrue(BossPowers.missingPowers().isEmpty(), "mutations with no boss controller: " + BossPowers.missingPowers());
		EmpoweredZombie host = RaidEntityTypes.EMPOWERED_ZOMBIE.create(helper.getLevel());
		List<String> problems = new ArrayList<>();
		for (Power power : Powers.all()) {
			String key = power.key();
			helper.assertTrue(BossPowers.isEligible(key), key + " is not boss-capable");
			BossPowerController c = BossPowers.create(key, host);
			helper.assertTrue(c != null && c.powerKey().equals(key), "controller for " + key);
			Set<String> ids = new HashSet<>(c.abilityIds());
			if (ids.size() < 3) {
				problems.add(key + " uses only " + ids);
			}
			if (power.abilities().isEmpty()) {
				continue; // passive-only (Super Regeneration): its ids are its passives
			}
			Set<String> real = new HashSet<>();
			for (Ability a : power.abilities()) {
				real.add(a.id());
			}
			for (String id : ids) {
				if (!real.contains(id)) {
					problems.add(key + ": '" + id + "' is not one of the power's current abilities " + real);
				}
			}
		}
		host.discard();
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossPowerSelectionIncludesPlayerDisabledMutations(GameTestHelper helper) {
		List<String> disabled = new ArrayList<>();
		for (String key : BossPowers.eligibleKeys()) {
			if (!Powers.isEnabled(key)) {
				disabled.add(key);
			}
		}
		helper.assertTrue(!disabled.isEmpty(), "the boss roster must include mutations players cannot currently get");
		RandomSource random = RandomSource.create(1421L);
		Set<String> rolled = new HashSet<>();
		Set<String> rolledSupervillain = new HashSet<>();
		for (int i = 0; i < 2000; i++) {
			rolled.add(BossPowers.randomKey(random));
			rolledSupervillain.add(BossPowers.randomSupervillainKey(random));
		}
		helper.assertTrue(rolled.stream().anyMatch(disabled::contains), "the Zombie Raid never rolled a player-disabled mutation");
		helper.assertTrue(rolledSupervillain.stream().anyMatch(disabled::contains),
				"the Supervillain Raid never rolled a player-disabled mutation");
		helper.assertTrue(rolled.size() == BossPowers.eligibleKeys().size(),
				"2000 rolls should reach every one of the " + BossPowers.eligibleKeys().size() + " powers, got " + rolled.size());
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossNameplateShowsItsPower(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerLevel level = helper.getLevel();
		BlockPos at = arena(helper, 0);
		inArena(helper, at, 4, () -> {
			floor(level, at, 3);
			String key = "power_17_elasticity"; // a mutation players cannot currently obtain
			EmpoweredZombie boss = boss(level, at, key);
			String nameKey = Powers.byKey(key).nameKey();
			Component name = boss.getCustomName();
			boss.discard();
			release(level, at, 4);
			helper.assertTrue(mentionsKey(name, nameKey), "the boss nameplate should name its power (" + nameKey + "), got " + name);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- abilities in use

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void everyBossUsesSeveralOfItsAbilities(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerLevel level = helper.getLevel();
		BlockPos at = arena(helper, 1);
		inArena(helper, at, 26, () -> {
			floor(level, at, 24);
			Vec3 bossAt = Vec3.atBottomCenterOf(at);
			List<String> problems = new ArrayList<>();
			for (String key : BossPowers.eligibleKeys()) {
				floor(level, at, 24); // a fresh arena: the last boss's spikes / pillars / cages are still standing
				EmpoweredZombie boss = boss(level, at, key);
				boss.setHealth(boss.getMaxHealth() * 0.6f);
				boss.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
				Wolf wolf = pet(level, at.offset(3, 0, 0));
				try {
					for (double dist : new double[] { 3.0, 9.0, 18.0 }) {
						clearCorridor(level, at);
						drive(level, boss, wolf, bossAt, bossAt.add(dist, 0, 0), 24, () -> wolf.setHealth(wolf.getMaxHealth()));
					}
					BossPowerController c = boss.primaryController();
					if (c instanceof SuperRegenerationBoss) {
						// the passive kit: Regen and Cleanse ran above; Revive answers a killing blow
						boss.hurt(level.damageSources().generic(), 100000.0f);
						if (!boss.isAlive()) {
							problems.add(key + ": Revive did not catch a killing blow");
						}
					}
					Set<String> used = c.usedAbilities();
					if (used.size() < 3) {
						problems.add(key + " used only " + used);
					}
				} catch (RuntimeException e) {
					problems.add(key + " threw " + e);
				}
				boss.discard();
				wolf.discard();
			}
			release(level, at, 26);
			helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400)
	public void bossAbilitiesHurtAndMarkAPlayersPet(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerLevel level = helper.getLevel();
		BlockPos at = arena(helper, 2);
		inArena(helper, at, 26, () -> {
			floor(level, at, 24);
			Vec3 bossAt = Vec3.atBottomCenterOf(at);

			// Electrokinesis: Electric Bolt / Chain Lightning hurt the pet and leave the power's real static stacks
			EmpoweredZombie electro = boss(level, at, ElectrokinesisBoss.POWER_KEY);
			Wolf a = pet(level, at.offset(9, 0, 0));
			boolean[] stacked = { false };
			driveUntil(level, electro, a, bossAt, bossAt.add(9, 0, 0), 48, () -> stacked[0] |= ElectrokinesisHandlers.stacks(a) > 0,
					() -> stacked[0] && a.getHealth() < a.getMaxHealth());
			boolean electroHurt = a.getHealth() < a.getMaxHealth();
			electro.discard();
			a.discard();

			// Cryokinesis: Ice Bolt / Freeze Beam add the power's real frost stacks. v0.15.12: up to 3 fresh attempts -- in
			// a loaded CI batch one boss can roll a run where nothing connects (seen with LOS true and every move used)
			boolean[] frosted = { false };
			boolean cryoHurt = false;
			String cryoInfo = "";
			for (int attempt = 0; attempt < 3 && !(cryoHurt && frosted[0]); attempt++) {
				clearCorridor(level, at);
				EmpoweredZombie cryo = boss(level, at, CryokinesisBoss.POWER_KEY);
				Wolf b = pet(level, at.offset(9, 0, 0));
				driveUntil(level, cryo, b, bossAt, bossAt.add(9, 0, 0), 64, () -> {
					frosted[0] |= FrostStacks.stacks(b) > 0 || FrostStacks.frozen(b);
					clearCorridor(level, at); // its own Ice Wall blocks the next Freeze Beam's line of sight
				}, () -> frosted[0] && b.getHealth() < b.getMaxHealth());
				cryoHurt = b.getHealth() < b.getMaxHealth();
				cryoInfo = " (attempt " + (attempt + 1) + ", used " + cryo.primaryController().usedAbilities() + ", pet " + b.getHealth()
						+ "/" + b.getMaxHealth() + ", sees " + cryo.hasLineOfSight(b) + ", boss removed " + cryo.isRemoved()
						+ ", difficulty " + level.getDifficulty() + ")";
				cryo.discard();
				b.discard();
			}

			// Super Strength: the Haymaker combo / Ground Slam at melee range
			clearCorridor(level, at);
			EmpoweredZombie brute = boss(level, at, SuperStrengthBoss.POWER_KEY);
			Wolf w = pet(level, at.offset(3, 0, 0));
			driveUntil(level, brute, w, bossAt, bossAt.add(3, 0, 0), 24, null, () -> w.getHealth() < w.getMaxHealth()
					&& (brute.primaryController().usedAbilities().contains("haymaker") || brute.primaryController().usedAbilities().contains("ground_slam")));
			boolean bruteHurt = w.getHealth() < w.getMaxHealth();
			Set<String> bruteUsed = new HashSet<>(brute.primaryController().usedAbilities());
			brute.discard();
			w.discard();
			release(level, at, 26);

			helper.assertTrue(electroHurt, "an Electrokinesis boss should hurt a player's pet in range");
			helper.assertTrue(stacked[0], "an Electrokinesis boss should leave static stacks on its victim");
			helper.assertTrue(cryoHurt, "a Cryokinesis boss should hurt a player's pet in range" + cryoInfo);
			helper.assertTrue(frosted[0], "a Cryokinesis boss should add frost stacks to its victim" + cryoInfo);
			helper.assertTrue(bruteHurt, "a Super Strength boss should hurt a player's pet at melee range");
			helper.assertTrue(bruteUsed.contains("haymaker") || bruteUsed.contains("ground_slam"),
					"it should have used Haymaker or Ground Slam, used " + bruteUsed);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600)
	public void bossesNeverHarmTheRaidsOwnMobs(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerLevel level = helper.getLevel();
		BlockPos at = arena(helper, 3);
		helper.assertFalse(BossTargets.isVictim(null, RaidEntityTypes.RAID_ZOMBIE.create(level)), "a raid zombie is never a victim");
		inArena(helper, at, 26, () -> {
			floor(level, at, 24);
			Vec3 bossAt = Vec3.atBottomCenterOf(at);
			// horde all around the fight: beside the boss, beside the pet, and in between
			List<RaidZombie> horde = new ArrayList<>();
			for (Vec3 off : new Vec3[] { new Vec3(0, 0, 2.5), new Vec3(0, 0, -2.5), new Vec3(3, 0, 1.5), new Vec3(9, 0, 1.5),
					new Vec3(6, 0, -1.5), new Vec3(18, 0, 1.5) }) {
				horde.add(raidMob(level, bossAt.add(off)));
			}
			List<EmpoweredZombie> bosses = new ArrayList<>();
			int n = 0;
			for (String key : BossPowers.eligibleKeys()) {
				floor(level, at, 24); // a fresh arena: the last boss's spikes / pillars / cages are still standing
				EmpoweredZombie boss = boss(level, at, key);
				boss.setHealth(boss.getMaxHealth() * 0.3f); // low health: the ultimates fire too
				Wolf wolf = pet(level, at.offset(3, 0, 0));
				for (double dist : new double[] { 3.0, 9.0, 18.0 }) {
					clearCorridor(level, at);
					drive(level, boss, wolf, bossAt, bossAt.add(dist, 0, 0), 16, () -> {
						wolf.setHealth(wolf.getMaxHealth());
						for (RaidZombie z : horde) {
							z.setDeltaMovement(Vec3.ZERO);
						}
					});
				}
				wolf.discard();
				// park the boss (alive, so its in-flight effects keep running) in a row well clear of the horde
				boss.setNoGravity(true);
				boss.teleportTo(bossAt.x - 20 + (n % 13) * 3, bossAt.y + 4, bossAt.z - 40 - (n / 13) * 3);
				n++;
				bosses.add(boss);
			}
			// let projectiles, waves and storms already in flight finish on real server ticks, then check the horde
			helper.runAfterDelay(100, () -> {
				List<String> hurt = new ArrayList<>();
				for (RaidZombie z : horde) {
					if (!z.isAlive() || z.getHealth() < z.getMaxHealth()) {
						hurt.add("zombie at " + z.blockPosition() + " has " + z.getHealth() + "/" + z.getMaxHealth());
					}
					for (MobEffectInstance e : z.getActiveEffects()) {
						if (!e.getEffect().value().isBeneficial()) {
							hurt.add("zombie at " + z.blockPosition() + " got " + e.getEffect().getRegisteredName());
						}
					}
					if (z.isOnFire()) {
						hurt.add("zombie at " + z.blockPosition() + " was set on fire");
					}
				}
				bosses.forEach(EmpoweredZombie::discard);
				horde.forEach(RaidZombie::discard);
				release(level, at, 26);
				helper.assertTrue(hurt.isEmpty(), "boss powers hit the raid's own mobs: " + String.join("; ", hurt));
				helper.succeed();
			});
		});
	}
}
