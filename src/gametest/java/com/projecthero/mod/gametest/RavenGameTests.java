package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenAbilityManager;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.RavenFlight;
import com.projecthero.mod.raven.RavenItems;
import com.projecthero.mod.raven.RavenLeak;
import com.projecthero.mod.raven.RavenLearning;
import com.projecthero.mod.raven.RavenMoves;
import com.projecthero.mod.raven.RavenRestore;
import com.projecthero.mod.raven.data.RavenState;
import com.projecthero.mod.raven.entity.DarkBoltEntity;
import com.projecthero.mod.raven.entity.RavenDebrisEntity;
import com.projecthero.mod.raven.entity.SoulSelfEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven (Teen Titans): the book and the grant, the passives, both bars, the leak and Rage, every move's damage / cost / effect,
 * flight and meditation, the shield, Trigon's Wrath, the block rules (energy copies with mob griefing off, real blocks restored with
 * it on) and the cloak's colours. Mock players are not ticked by the server, so tests over time drive {@link Raven#tick} themselves
 * (one onEachTick per test, registered at the start; every check is a runAfterDelay registered at the start too). Mob hits are asserted
 * as ratios of the expected damage. Gamerule / time changes are made and undone inside one tick.
 */
public class RavenGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double z) {
		// a solid floor right under y = 2: without it the creatures (ticked) drop to the structure's floor while the mock player
		// (not ticked) floats, and bolts / the soul-self fly over their heads
		for (int fx = 0; fx < 8; fx++) {
			for (int fz = 0; fz < 8; fz++) {
				if (helper.getBlockState(new BlockPos(fx, 1, fz)).isAir()) {
					helper.setBlock(new BlockPos(fx, 1, fz), Blocks.STONE);
				}
			}
		}
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, 2.0, z));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		p.setYHeadRot(0f); // a living entity looks along its head, which a mock player never turns by itself
		try { // a fresh mock player is spawn-protected
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer raven(GameTestHelper helper) {
		return raven(helper, 1.5, 1.5);
	}

	private static ServerPlayer raven(GameTestHelper helper, double x, double z) {
		ServerPlayer p = mock(helper, x, z);
		helper.assertTrue(Raven.grant(p), "the grant works");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<RavenState> change) {
		RavenState n = Raven.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.RAVEN_STATE, n);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, 2.0, z)); // a husk: it never burns in the sun
		zb.setBaby(false); // spawning can roll a baby (a smaller box the bolt passes over) or a chicken jockey
		if (zb.getVehicle() != null) {
			net.minecraft.world.entity.Entity mount = zb.getVehicle();
			zb.stopRiding();
			mount.discard();
		}
		zb.moveTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		// a hurt zombie can call reinforcements (more likely on Hard, which other tests leave on): a new zombie in a bolt's path
		zb.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).removeModifiers(); // (spawning adds a random bonus)
		zb.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
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

	private static void ticker(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> Raven.tick(p));
	}

	private static void griefing(GameTestHelper helper, boolean on) {
		helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(on, helper.getLevel().getServer());
	}

	// ---------------------------------------------------------------- the power

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bookOfAzarathWorksOnlyAtNight(GameTestHelper helper) {
		ServerPlayer p = mock(helper, 1.5, 1.5);
		com.projecthero.mod.kryptonian.Kryptonian.grant(p);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RavenItems.BOOK_OF_AZARATH));
		var level = helper.getLevel();
		long time = level.getDayTime();
		try {
			level.setDayTime(6000L);
			level.updateSkyBrightness();
			helper.assertTrue(!RavenLearning.learn(p, InteractionHand.MAIN_HAND) && !Raven.hasPower(p), "by day the pages are blank");
			helper.assertTrue(RavenLearning.isBook(p.getMainHandItem()), "and the book is kept");
			level.setDayTime(18000L);
			level.updateSkyBrightness();
			helper.assertTrue(RavenLearning.learn(p, InteractionHand.MAIN_HAND), "at night it teaches Raven");
		} finally {
			level.setDayTime(time);
			level.updateSkyBrightness();
		}
		helper.assertTrue(Raven.hasPower(p) && HeroTiers.holdsHero(p, Raven.KEY), "a Hero-Tier power");
		helper.assertTrue(p.getMainHandItem().isEmpty(), "the book is spent");
		helper.assertTrue(!com.projecthero.mod.kryptonian.Kryptonian.hasPower(p), "it replaced the Kryptonian (one Primary)");
		helper.assertTrue(PowerGrants.NOT_FROM_SERUMS.contains(Raven.KEY) && PowerGrants.HERO_TIER_KEYS.contains(Raven.KEY),
				"grantable, but random serums never give it");
		helper.assertTrue(Raven.energy(p) == RavenConfig.ENERGY_MAX && Raven.composure(p) == RavenConfig.COMPOSURE_MAX, "full and calm");
		Raven.revoke(p);
		helper.assertTrue(!Raven.hasPower(p), "revoked");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void passivesTwentyHealthTenPercentAndSlowFall(GameTestHelper helper) {
		ServerPlayer p = raven(helper);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "20 health, got " + p.getMaxHealth());
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 10f);
		helper.assertTrue(Math.abs(p.getHealth() - 11f) < 0.05f, "10% off a hit (10 -> 9), health " + p.getHealth());
		helper.assertTrue(Raven.composure(p) < RavenConfig.COMPOSURE_MAX - 9f, "being hurt unsettles her, composure " + Raven.composure(p));
		Raven.tick(p);
		helper.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "slow fall is on when she isn't flying");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void darkEnergyRefillsFiveASecond(GameTestHelper helper) {
		ServerPlayer p = raven(helper);
		Raven.setEnergy(p, 10f);
		float[] last = { -1f };
		boolean[] ok = { true };
		helper.onEachTick(() -> {
			Raven.tick(p);
			float now = Raven.energy(p);
			if (last[0] >= 0f && now < RavenConfig.ENERGY_MAX && Math.abs(now - last[0] - RavenConfig.ENERGY_REGEN_PER_TICK) > 1.0e-3f) {
				ok[0] = false;
			}
			last[0] = now;
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(ok[0], "every tick adds exactly 0.25 (5 a second)");
			helper.assertTrue(Raven.energy(p) > 10f, "and it's filling");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void composureLeakAndRageThresholds(GameTestHelper helper) {
		ServerPlayer p = raven(helper);
		helper.assertTrue(!Raven.leaking(p) && !Raven.rage(p), "calm to start");
		helper.assertTrue(!RavenAbilityManager.allowed(p, RavenAbilityManager.TRIGONS_WRATH), "no Wrath while calm");
		Raven.setComposure(p, 25f);
		helper.assertTrue(Raven.leaking(p) && !Raven.rage(p) && Raven.boltMult(p) == RavenConfig.LEAK_BOLT_BONUS, "under 30: leaking, bolts +25%");
		Raven.setComposure(p, 8f);
		helper.assertTrue(Raven.rage(p) && RavenAbilityManager.allowed(p, RavenAbilityManager.TRIGONS_WRATH), "under 10: Rage, the Wrath unlocks");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void leakCracksGlassOnlyWithMobGriefing(GameTestHelper helper) {
		ServerPlayer p = raven(helper);
		BlockPos glass = helper.absolutePos(new BlockPos(4, 2, 4));
		helper.getLevel().setBlockAndUpdate(glass, Blocks.GLASS.defaultBlockState());
		helper.assertTrue(RavenLeak.fragile(Blocks.GLASS.defaultBlockState()) && RavenLeak.fragile(Blocks.LANTERN.defaultBlockState())
				&& !RavenLeak.fragile(Blocks.STONE.defaultBlockState()), "glass and lamps are what cracks");
		boolean was = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		try {
			griefing(helper, false);
			RavenLeak.crack(helper.getLevel(), glass);
			helper.assertTrue(helper.getLevel().getBlockState(glass).is(Blocks.GLASS), "griefing off: just the shatter, the glass stays");
			griefing(helper, true);
			RavenLeak.crack(helper.getLevel(), glass);
			helper.assertTrue(helper.getLevel().getBlockState(glass).isAir(), "griefing on: it really breaks");
		} finally {
			griefing(helper, was);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- R / Shift+R

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void darkBoltPassesThroughOneCreature(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		Zombie first = zombie(helper, 1.5, 3.5, 100);
		Zombie second = zombie(helper, 1.5, 5.5, 100);
		Zombie third = zombie(helper, 1.5, 6.6, 100);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 8f); p.setYHeadRot(0f); // (moves aim along the head yaw)
		float b1 = first.getHealth(), b2 = second.getHealth(), b3 = third.getHealth();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.DARK_BOLT), "R fires");
		helper.assertTrue(Math.abs(Raven.energy(p) - (100f - RavenConfig.BOLT_COST)) < 0.01f, "8 Dark Energy");
		DarkBoltEntity bolt = helper.getLevel().getEntitiesOfClass(DarkBoltEntity.class, p.getBoundingBox().inflate(4)).get(0);
		StringBuilder trace = new StringBuilder(); // (what the bolt did, for a failure message)
		helper.onEachTick(() -> trace.append(String.format(" [%.2f %.2f %.2f%s h%d s%.1f]", bolt.getX() - p.getX(), bolt.getY() - p.getY(), bolt.getZ() - p.getZ(),
				bolt.isRemoved() ? " gone" : "", bolt.hits, second.getHealth())));
		helper.runAfterDelay(12, () -> {
			helper.assertTrue(near(ratio(b1, first, RavenConfig.BOLT_DAMAGE)), "8 to the first, lost " + (b1 - first.getHealth()));
			helper.assertTrue(near(ratio(b2, second, RavenConfig.BOLT_DAMAGE)), "it passed through into the second, lost " + (b2 - second.getHealth()) + "; second at " + second.position().subtract(p.position())
					+ ", first at " + first.position().subtract(p.position()) + ", near it: " + helper.getLevel().getEntities(second, second.getBoundingBox().inflate(2.5)).stream().map(e -> e.getType().toShortString()).toList() + trace);
			helper.assertTrue(third.getHealth() == b3, "and stopped there");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(DarkBoltEntity.class, p.getBoundingBox().inflate(30)).isEmpty(), "the bolt is gone");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120)
	public void telekinesisLiftsAndThrowsACreature(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 1.5, 5.5, 100);
		look(p, z);
		ticker(helper, p);
		float before = z.getHealth();
		helper.assertTrue(RavenMoves.startHold(p, RavenAbilityManager.TELEKINESIS), "Shift+R grabs the zombie");
		helper.assertTrue(RavenMoves.held(p) == z && Raven.state(p).tkTarget == z.getId(), "it's held");
		helper.assertTrue(Math.abs(Raven.energy(p) - (100f - RavenConfig.TK_COST)) < 0.01f, "10 Dark Energy up front");
		float[] e5 = { 0f };
		helper.runAfterDelay(5, () -> e5[0] = Raven.energy(p));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(Raven.energy(p) < e5[0] - 1.5f, "the hold drains 4 a second (" + e5[0] + " -> " + Raven.energy(p) + ")");
			p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 60f); p.setYHeadRot(0f); // (moves aim along the head yaw) // throw it down at the floor
			RavenMoves.release(p, RavenAbilityManager.TELEKINESIS);
			helper.assertTrue(RavenMoves.held(p) == null && Raven.state(p).tkTarget == -1, "released");
		});
		helper.runAfterDelay(50, () -> {
			float lost = before - z.getHealth();
			helper.assertTrue(lost >= RavenConfig.TK_THROW_DAMAGE * 0.85f && lost <= (RavenConfig.TK_THROW_DAMAGE + RavenConfig.TK_IMPACT_MAX) * 1.2f + 4f,
					"10 + impact when it lands, lost " + lost);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void telekinesisRefusesBosses(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		Zombie boss = zombie(helper, 1.5, 4.5, 500);
		look(p, boss);
		helper.assertTrue(Raven.isBoss(boss), "a 500-health creature counts as a boss");
		helper.assertTrue(!RavenMoves.startHold(p, RavenAbilityManager.TELEKINESIS), "too strong to lift");
		helper.assertTrue(Raven.energy(p) == RavenConfig.ENERGY_MAX, "and it cost nothing");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void telekinesisBlocksAreCopiesOrRestored(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		BlockPos block = helper.absolutePos(new BlockPos(1, 2, 5));
		helper.getLevel().setBlockAndUpdate(block, Blocks.COBBLESTONE.defaultBlockState());
		p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(block));
		boolean was = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		try {
			griefing(helper, false);
			helper.assertTrue(RavenMoves.startHold(p, RavenAbilityManager.TELEKINESIS), "a block can be lifted");
			helper.assertTrue(RavenMoves.held(p) instanceof RavenDebrisEntity d && d.energyCopy() && d.blockState().is(Blocks.COBBLESTONE),
					"griefing off: an energy copy");
			helper.assertTrue(helper.getLevel().getBlockState(block).is(Blocks.COBBLESTONE), "the real block never moved");
			RavenMoves.release(p, RavenAbilityManager.TELEKINESIS);
			Raven.clearCooldowns(p);
			griefing(helper, true);
			helper.assertTrue(RavenMoves.startHold(p, RavenAbilityManager.TELEKINESIS), "lifted again");
			helper.assertTrue(RavenMoves.held(p) instanceof RavenDebrisEntity d && !d.energyCopy(), "griefing on: the real block");
			helper.assertTrue(helper.getLevel().getBlockState(block).isAir(), "it left the ground");
			RavenMoves.release(p, RavenAbilityManager.TELEKINESIS);
			RavenRestore.restoreAll();
			helper.assertTrue(helper.getLevel().getBlockState(block).is(Blocks.COBBLESTONE), "and it goes back where it was");
		} finally {
			griefing(helper, was);
		}
		helper.succeed();
	}

	// ---------------------------------------------------------------- G / Shift+G

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void shadowGraspHoldsForTwoSeconds(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 1.5, 5.5, 100);
		look(p, z);
		ticker(helper, p);
		float before = z.getHealth();
		Vec3[] at = { null };
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SHADOW_GRASP), "G");
		helper.assertTrue(near(ratio(before, z, RavenConfig.GRASP_DAMAGE)), "6 damage, lost " + (before - z.getHealth()));
		helper.assertTrue(Math.abs(Raven.energy(p) - (100f - RavenConfig.GRASP_COST)) < 0.01f, "15 Dark Energy");
		helper.runAfterDelay(2, () -> {
			at[0] = z.position();
			z.setDeltaMovement(0.8, 0.5, 0.8); // try to break free
		});
		helper.runAfterDelay(20, () -> helper.assertTrue(z.position().distanceTo(at[0]) < 0.2, "held in place, moved " + z.position().distanceTo(at[0])));
		helper.runAfterDelay(RavenConfig.GRASP_HOLD_TICKS + 4, helper::succeed);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100, batch = "raven_soul_alone") // alone: it flies 25 blocks into other tests
	public void soulSelfEngulfsEverythingItPasses(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 0.5);
		Zombie a = zombie(helper, 1.5, 3.5, 100);
		Zombie b = zombie(helper, 1.5, 6.0, 100);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f); p.setYHeadRot(0f); // (moves aim along the head yaw)
		float ba = a.getHealth(), bb = b.getHealth();
		// it flies 25 blocks: keep the chunks along its path ticking
		java.util.Set<Long> forced = new java.util.HashSet<>();
		for (int d = 0; d <= 32; d += 4) {
			for (int dx = -3; dx <= 3; dx += 3) { // (its path can sit just across a chunk line in x)
				int cx = ((int) Math.floor(p.getX() + dx)) >> 4, cz = ((int) Math.floor(p.getZ() + d)) >> 4;
				if (forced.add(net.minecraft.world.level.ChunkPos.asLong(cx, cz))) {
					helper.getLevel().setChunkForced(cx, cz, true);
				}
			}
		}
		// cast only after the forced chunks have loaded (on a busy machine they load a few ticks late, and the raven froze there)
		SoulSelfEntity[] soul = { null };
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SOUL_SELF), "Shift+G");
			helper.assertTrue(Math.abs(Raven.energy(p) - (100f - RavenConfig.SOUL_COST)) < 0.01f, "35 Dark Energy");
			helper.assertTrue(Raven.cooldownRemaining(p, RavenAbilityManager.SOUL_SELF) == RavenConfig.SOUL_COOLDOWN, "8 s cooldown");
			var found = helper.getLevel().getEntitiesOfClass(SoulSelfEntity.class, p.getBoundingBox().inflate(8));
			helper.assertTrue(found.size() == 1, "one raven flies out");
			soul[0] = found.get(0);
		});
		helper.runAfterDelay(32, () -> {
			helper.assertTrue(near(ratio(ba, a, RavenConfig.SOUL_DAMAGE)) && near(ratio(bb, b, RavenConfig.SOUL_DAMAGE)),
					"16 to each, lost " + (ba - a.getHealth()) + " / " + (bb - b.getHealth()) );
			helper.assertTrue(a.hasEffect(MobEffects.BLINDNESS) && a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Blindness and Slowness");
			helper.assertTrue(soul[0].isAlive(), "the raven is still flying out");
		});
		helper.runAfterDelay(70, () -> {
			helper.assertTrue(soul[0].isRemoved(), "and it dissolves after 25 blocks (fade " + soul[0].fade() + ", " + soul[0].position().distanceTo(p.position()) + " out)");
			for (long c : forced) {
				helper.getLevel().setChunkForced(net.minecraft.world.level.ChunkPos.getX(c), net.minecraft.world.level.ChunkPos.getZ(c), false);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void darkShieldSoaksFortyAndSwallowsArrows(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		ticker(helper, p);
		helper.assertTrue(RavenMoves.startHold(p, RavenAbilityManager.DARK_SHIELD), "Z raises the dome");
		helper.assertTrue(Raven.shielding(p) && Raven.state(p).shieldHp == RavenConfig.SHIELD_ABSORB, "40 to absorb");
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 15f);
		helper.assertTrue(p.getHealth() == 20f && Math.abs(Raven.state(p).shieldHp - 25f) < 0.01f, "the dome took 15");
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 30f);
		helper.assertTrue(!Raven.shielding(p), "30 more breaks it");
		helper.assertTrue(Math.abs(p.getHealth() - (20f - 5f * 0.9f)) < 0.1f, "the 5 that got through is 10% lighter, health " + p.getHealth());
		helper.assertTrue(Raven.cooldownRemaining(p, RavenAbilityManager.DARK_SHIELD) > 0, "broken: can't raise it for a moment");
		Raven.clearCooldowns(p);
		helper.assertTrue(RavenMoves.startHold(p, RavenAbilityManager.DARK_SHIELD), "raised again");
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setPos(p.getX() + 1.5, p.getY() + 1.0, p.getZ());
		helper.getLevel().addFreshEntity(arrow);
		int before = RavenMoves.absorbed;
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!arrow.isAlive() && RavenMoves.absorbed > before, "an arrow inside the dome is swallowed");
			float e = Raven.energy(p);
			RavenMoves.release(p, RavenAbilityManager.DARK_SHIELD);
			helper.assertTrue(!Raven.shielding(p) && e < RavenConfig.ENERGY_MAX, "letting go drops it (it drained energy)");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "raven_wrath_alone") // alone: 12+ blocks of tentacles
	public void trigonsWrathNeedsRageThenDrains(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 5.5, 5.5, 200);
		ticker(helper, p);
		helper.assertTrue(!RavenMoves.use(p, RavenAbilityManager.TRIGONS_WRATH), "refused while calm");
		Raven.setComposure(p, 5f);
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.TRIGONS_WRATH), "Shift+Z in Rage");
		helper.assertTrue(Raven.inWrath(p) && Math.abs(p.getScale() - RavenConfig.WRATH_SCALE) < 0.01f, "a giant: 3x, scale " + p.getScale());
		helper.assertTrue(Raven.cooldownRemaining(p, RavenAbilityManager.TRIGONS_WRATH) == RavenConfig.WRATH_COOLDOWN, "3 minute cooldown");
		StringBuilder hp = new StringBuilder(); // (each hit it takes, for a failure message)
		float[] last = { z.getHealth() };
		helper.onEachTick(() -> {
			if (z.getHealth() != last[0]) {
				hp.append(String.format(" [t%d -%.1f %s y%.1f]", helper.getTick(), last[0] - z.getHealth(),
						z.getLastDamageSource() == null ? "?" : z.getLastDamageSource().getMsgId(), z.getY() - p.getY()));
				last[0] = z.getHealth();
			}
		});
		float[] preSlam = { 0f };
		helper.runAfterDelay(RavenConfig.WRATH_SLAMS[0], () -> preSlam[0] = z.getHealth()); // (only the slam: anything else on the field is not ours)
		helper.runAfterDelay(RavenConfig.WRATH_SLAMS[0] + RavenConfig.WRATH_LIFT_TICKS + 12, () -> {
			float lost = preSlam[0] - z.getHealth();
			helper.assertTrue(lost >= RavenConfig.WRATH_DAMAGE * 0.85f && lost <= RavenConfig.WRATH_DAMAGE * 1.15f + 2f,
					"the first grab-and-slam: 30, lost " + lost + hp);
		});
		helper.runAfterDelay(RavenConfig.WRATH_TICKS + 3, () -> {
			helper.assertTrue(!Raven.inWrath(p) && Math.abs(p.getScale() - 1.0) < 0.01f, "back to size");
			helper.assertTrue(Raven.composure(p) >= RavenConfig.COMPOSURE_MAX - 1f && Raven.energy(p) < 1f, "drained: calm, no Dark Energy");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- X / Shift+X

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 30)
	public void shadowDashStreaksAndCantBeHurt(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 0.5);
		p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f); p.setYHeadRot(0f); // (moves aim along the head yaw) // along the room
		ticker(helper, p);
		Vec3 from = p.position();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SHADOW_DASH), "X");
		helper.assertTrue(Raven.inShadow(p), "a streak of shadow");
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 8f);
		helper.assertTrue(p.getHealth() == 20f, "nothing hurts her during the dash");
		helper.runAfterDelay(RavenConfig.DASH_TICKS + 3, () -> {
			double moved = p.position().distanceTo(from);
			helper.assertTrue(moved > 4.0 && moved <= RavenConfig.DASH_DISTANCE + 0.01, "shot along her look (room-limited), moved " + moved);
			helper.assertTrue(!Raven.inShadow(p), "solid again");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void shadowStepStrikeErruptsAndLaunches(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 0.5);
		Zombie z = zombie(helper, 4.5, 5.5, 100);
		look(p, z);
		ticker(helper, p);
		float before = z.getHealth();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SHADOW_STEP), "Shift+X");
		helper.runAfterDelay(RavenConfig.STEP_SINK_TICKS + 2, () -> {
			helper.assertTrue(p.distanceTo(z) < 3.0, "she erupted beside it, " + p.distanceTo(z) + " away");
			helper.assertTrue(near(ratio(before, z, RavenConfig.STEP_DAMAGE)), "12 damage, lost " + (before - z.getHealth()));
			helper.assertTrue(z.getDeltaMovement().y > 0.3 || z.getY() > helper.absoluteVec(new Vec3(0, 2.2, 0)).y, "launched up");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C / Shift+C

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80, batch = "raven_wave_alone") // alone: 8 blocks round her
	public void telekineticWaveLiftsHoldsAndSlams(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		Zombie a = zombie(helper, 1.5, 1.5, 100);
		Zombie b = zombie(helper, 6.5, 5.5, 100);
		ticker(helper, p);
		double baseY = a.getY();
		float ba = a.getHealth(), bb = b.getHealth();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.TELEKINETIC_WAVE), "C");
		helper.assertTrue(Math.abs(Raven.energy(p) - (100f - RavenConfig.WAVE_COST)) < 0.01f, "30 Dark Energy");
		helper.runAfterDelay(RavenConfig.WAVE_RISE_TICKS + 5, () -> {
			double up = a.getY() - baseY;
			helper.assertTrue(up > 3.0 && up < 4.5, "lifted about 4 blocks, " + up);
			helper.assertTrue(a.getHealth() == ba, "no damage while it hangs");
		});
		helper.runAfterDelay(RavenConfig.WAVE_RISE_TICKS + RavenConfig.WAVE_HOLD_TICKS + 14, () -> {
			// (lower bounds only: a neighbouring test's Trigon's Wrath reaches 12+ blocks and can land on these too)
			helper.assertTrue(ratio(ba, a, RavenConfig.WAVE_DAMAGE) > 0.85f && ratio(bb, b, RavenConfig.WAVE_DAMAGE) > 0.85f,
					"slammed for 12 each, lost " + (ba - a.getHealth()) + " / " + (bb - b.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void debrisStormOrbitsCopiesAndFiresOnePerPress(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
			}
		}
		Zombie target = zombie(helper, 3.5, 6.8, 100);
		ticker(helper, p);
		boolean was = helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		try {
			griefing(helper, false);
			helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.DEBRIS_STORM), "Shift+C tears up the ground");
		} finally {
			griefing(helper, was);
		}
		int n = RavenMoves.debrisCount(p);
		helper.assertTrue(n == RavenConfig.DEBRIS_COUNT && Raven.state(p).debris == n, "8 blocks orbit her, got " + n);
		helper.assertTrue(helper.getBlockState(new BlockPos(0, 1, 0)).is(Blocks.DIRT) && helper.getBlockState(new BlockPos(5, 1, 3)).is(Blocks.DIRT),
				"griefing off: the ground is untouched (energy copies)");
		float[] targetBefore = { 0f };
		float e = Raven.energy(p);
		helper.assertTrue(Math.abs(e - (100f - RavenConfig.DEBRIS_COST)) < 0.01f, "30 Dark Energy");
		helper.runAfterDelay(10, () -> {
			look(p, target);
			float before = target.getHealth();
			helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.DEBRIS_STORM), "pressed again: fire one");
			helper.assertTrue(RavenMoves.debrisCount(p) == RavenConfig.DEBRIS_COUNT - 1, "one left the orbit");
			helper.assertTrue(Raven.energy(p) >= e, "firing is free");
			targetBefore[0] = before;
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(near(ratio(targetBefore[0], target, RavenConfig.DEBRIS_DAMAGE)), "8 damage, lost " + (targetBefore[0] - target.getHealth()));
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- V / Shift+V

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void empathHealOnHerselfAndASquadmate(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		p.setHealth(10f);
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 0));
		p.moveTo(p.getX(), p.getY(), p.getZ(), 180f, 0f); p.setYHeadRot(180f); // (moves aim along the head yaw)
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.EMPATH_HEAL), "V with no one aimed at");
		helper.assertTrue(Math.abs(p.getHealth() - 14f) < 0.01f && !p.hasEffect(MobEffects.POISON), "heals 4 and cures, health " + p.getHealth());
		helper.assertTrue(Raven.cooldownRemaining(p, RavenAbilityManager.EMPATH_HEAL) == RavenConfig.HEAL_COOLDOWN, "10 s cooldown");
		Raven.clearCooldowns(p);
		p.setHealth(20f);
		ServerPlayer mate = mock(helper, 1.5, 5.5);
		mate.setHealth(4f);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("RavenTestSquad" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f); p.setYHeadRot(0f); // (moves aim along the head yaw)
			look(p, mate);
			helper.assertTrue(RavenMoves.healTarget(p) == mate, "aimed at the squadmate");
			helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.EMPATH_HEAL), "V on them");
			helper.assertTrue(Math.abs(p.getHealth() - 12f) < 0.01f, "she took 8 of their pain (16 missing, half), health " + p.getHealth());
			helper.assertTrue(Math.abs(mate.getHealth() - 20f) < 0.01f, "and healed them double, health " + mate.getHealth());
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void meditationFloatsRefillsAndBlocksMoves(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		Raven.setComposure(p, 20f);
		Raven.setEnergy(p, 20f);
		ticker(helper, p);
		helper.assertTrue(RavenMoves.startHold(p, RavenAbilityManager.MEDITATE), "Shift+V held");
		helper.assertTrue(Raven.meditating(p) && p.getAbilities().flying, "she floats");
		helper.assertTrue(!RavenMoves.use(p, RavenAbilityManager.DARK_BOLT), "no moves while meditating");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(Raven.composure(p) > 20f + 9f, "Composure back fast (about 12 a second), " + Raven.composure(p));
			helper.assertTrue(Raven.energy(p) > 20f + 20f, "Dark Energy back fast, " + Raven.energy(p));
			RavenMoves.release(p, RavenAbilityManager.MEDITATE);
			helper.assertTrue(!Raven.meditating(p) && !p.getAbilities().flying, "letting go ends it");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void doubleTapSpaceLevitation(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		Raven.tick(p);
		RavenFlight.toggle(p);
		helper.assertTrue(Raven.isFlying(p) && p.getAbilities().mayfly && p.getAbilities().flying, "takes off");
		helper.assertTrue(!p.hasEffect(MobEffects.SLOW_FALLING), "slow fall is off while flying");
		Raven.clearCooldowns(p);
		RavenFlight.toggle(p);
		helper.assertTrue(!Raven.isFlying(p) && !p.getAbilities().flying, "lands");
		helper.succeed();
	}

	// ---------------------------------------------------------------- keys, cloak

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keysFollowTheHouseRules(GameTestHelper helper) {
		helper.assertTrue(RavenAbilityManager.SHADOW_DASH.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_3, false)), "X = Shadow Dash (movement)");
		helper.assertTrue(RavenAbilityManager.EMPATH_HEAL.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_5, false)), "V = Empath Heal (utility)");
		helper.assertTrue(RavenAbilityManager.moveFor(AbilitySlot.values()[AbilitySlot.CORE_COUNT], false) == null, "nothing on H / N");
		int moves = 0;
		for (AbilitySlot s : AbilitySlot.values()) {
			moves += RavenAbilityManager.idsOf(s).length;
		}
		helper.assertTrue(moves == 12, "twelve moves on six keys, got " + moves);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void cloakColourFollowsComposure(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RavenItems.CLOAK));
		helper.assertTrue(RavenItems.wearsCloak(p), "worn");
		helper.assertTrue(RavenItems.cloakColour(p) == RavenItems.CLOAK_WHITE, "very calm: white");
		Raven.setComposure(p, 60f);
		helper.assertTrue(RavenItems.cloakColour(p) == RavenItems.CLOAK_BLUE, "blue");
		Raven.setComposure(p, 5f);
		helper.assertTrue(RavenItems.cloakColour(p) == RavenItems.CLOAK_RED, "Rage: dark red");
		ServerPlayer other = mock(helper, 5.5, 5.5);
		other.setItemSlot(EquipmentSlot.CHEST, new ItemStack(RavenItems.CLOAK));
		helper.assertTrue(RavenItems.cloakColour(other) == RavenItems.CLOAK_BLUE, "anyone else's is blue");
		helper.succeed();
	}
}
