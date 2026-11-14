package com.projecthero.mod.gametest;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.PowerGrants;
import com.projecthero.mod.raven.Raven;
import com.projecthero.mod.raven.RavenAbilityManager;
import com.projecthero.mod.raven.RavenAwareness;
import com.projecthero.mod.raven.RavenConfig;
import com.projecthero.mod.raven.RavenFlight;
import com.projecthero.mod.raven.RavenItems;
import com.projecthero.mod.raven.RavenLeak;
import com.projecthero.mod.raven.RavenLearning;
import com.projecthero.mod.raven.RavenMoves;
import com.projecthero.mod.raven.RavenRestore;
import com.projecthero.mod.raven.RavenSpell;
import com.projecthero.mod.raven.RavenSpells;
import com.projecthero.mod.raven.data.RavenState;
import com.projecthero.mod.raven.entity.RavenDebrisEntity;
import com.projecthero.mod.raven.entity.SoulSelfEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Raven (Teen Titans), revamped 2026-11-13 ({@code docs/RAVEN_REVAMP_DESIGN.md}): the book and the grant, the passives
 * (20 health and no fall damage since v0.17.4, Demonic Heritage, Dark Empathy, Mystical Awareness), both bars and the Composure tiers, every main key's core effect
 * and cost, Demonic Ascension's buffs and expiry, the Spell Wheel's pick (saved, and V tapped casts it), every spell's core effect,
 * the misfire rate, Rage halving shields, Trigon's Wrath gated by Rage, and old saved data. Mock players are not ticked by the
 * server, so tests over time drive {@link Raven#tick} themselves (one onEachTick per test, registered at the start; every check is a
 * runAfterDelay registered at the start too). Mob hits are asserted as ratios of the expected damage (the world may be on Hard).
 * Rates are checked per tick (step sizes), not as totals. Gamerule / time changes are made and undone inside one tick.
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

	private static Zombie zombie(GameTestHelper helper, double x, double z, double maxHealth) {
		Zombie zb = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(x, 2.0, z)); // a husk: it never burns in the sun
		zb.setBaby(false); // spawning can roll a baby (a smaller box the bolt passes over) or a chicken jockey
		if (zb.getVehicle() != null) {
			net.minecraft.world.entity.Entity mount = zb.getVehicle();
			zb.stopRiding();
			mount.discard();
		}
		zb.moveTo(helper.absoluteVec(new Vec3(x, 2.0, z)));
		// a hurt zombie can call reinforcements (more likely on Hard, which other tests leave on)
		zb.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).removeModifiers();
		zb.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
		zb.setHealth((float) maxHealth);
		return zb;
	}

	private static void look(ServerPlayer p, LivingEntity at) {
		p.lookAt(EntityAnchorArgument.Anchor.EYES, at.position().add(0, at.getBbHeight() * 0.55, 0));
	}

	private static void face(ServerPlayer p, float yaw, float pitch) {
		p.moveTo(p.getX(), p.getY(), p.getZ(), yaw, pitch);
		p.setYHeadRot(yaw); // (moves aim along the head yaw)
	}

	private static float ratio(float before, LivingEntity e, float expected) {
		return (before - e.getHealth()) / expected;
	}

	private static boolean near(float ratio) {
		return ratio > 0.85f && ratio < 1.15f;
	}

	private static boolean close(float a, float b) {
		return Math.abs(a - b) < 0.02f;
	}

	private static void ticker(GameTestHelper helper, ServerPlayer p) {
		helper.onEachTick(() -> Raven.tick(p));
	}

	private static void griefing(GameTestHelper helper, boolean on) {
		helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(on, helper.getLevel().getServer());
	}

	private static com.projecthero.mod.squad.Squad squad(GameTestHelper helper, ServerPlayer a, ServerPlayer b) {
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var s = squads.create("RavenTestSquad" + helper.getLevel().getGameTime() + a.getId(), a.getUUID());
		squads.addMember(s, b.getUUID());
		return s;
	}

	private static void disband(GameTestHelper helper, com.projecthero.mod.squad.Squad s) {
		com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer()).disband(s);
	}

	// ---------------------------------------------------------------- the power, passives, bars

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
		helper.assertTrue(!Raven.hasPower(p) && Math.abs(p.getMaxHealth() - 20f) < 0.01f, "revoked, back to 20 health");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void twentyHealthResistanceAndDemonicHeritage(GameTestHelper helper) {
		ServerPlayer p = raven(helper);
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20f) < 0.01f, "20 health (v0.17.4), got " + p.getMaxHealth());
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().generic(), 10f);
		helper.assertTrue(Math.abs(p.getHealth() - 11f) < 0.05f, "10% off an ordinary hit (10 -> 9), health " + p.getHealth());
		helper.assertTrue(Raven.composure(p) < RavenConfig.COMPOSURE_MAX - 9f, "being hurt unsettles her, composure " + Raven.composure(p));
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 10f);
		float expect = 20f - 10f * 0.9f * 0.7f;
		helper.assertTrue(Math.abs(p.getHealth() - expect) < 0.05f, "Demonic Heritage: magic is 30% lighter on top (10 -> 6.3), health " + p.getHealth());
		Raven.tick(p);
		helper.assertTrue(!p.hasEffect(MobEffects.SLOW_FALLING), "v0.17.4: no slow fall");
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().fall(), 12f);
		helper.assertTrue(p.getHealth() == 20f, "but falls never hurt her, health " + p.getHealth());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 110)
	public void darkEnergyRefillStepsNormalAscendedAndTitans(GameTestHelper helper) {
		ServerPlayer p = raven(helper);
		Raven.setEnergy(p, 10f);
		float[] last = { -1f };
		boolean[] ok = { true };
		int[] titansTicks = { 0 };
		StringBuilder bad = new StringBuilder();
		helper.onEachTick(() -> {
			// the step this tick must be: 0.25 (0.5 in Demonic Ascension), times Titans Together's 1.2 whenever it's on
			float mult = com.projecthero.mod.titans.TitansTogether.regenMult(p);
			float want = (Raven.ascended(p) ? RavenConfig.ASCEND_REGEN_PER_TICK : RavenConfig.ENERGY_REGEN_PER_TICK) * mult;
			Raven.tick(p);
			float now = Raven.energy(p);
			if (last[0] >= 0f && now < RavenConfig.ENERGY_MAX - 1f && Math.abs(now - last[0] - want) > 1.0e-3f) {
				ok[0] = false;
				bad.append(String.format(" [%.3f want %.3f]", now - last[0], want));
			}
			if (mult > 1f) {
				titansTicks[0]++;
			}
			last[0] = now;
		});
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(ok[0] && !Raven.ascended(p), "every tick adds exactly 0.25 (5 a second)" + bad);
			RavenState n = Raven.state(p).copy();
			n.ascendUntil = helper.getLevel().getGameTime() + 15;
			p.setAttached(ModAttachments.RAVEN_STATE, n);
		});
		ServerPlayer[] star = { null };
		com.projecthero.mod.squad.Squad[] squad = { null };
		helper.runAfterDelay(29, () -> {
			helper.assertTrue(ok[0], "Demonic Ascension: exactly 0.5 a tick (10 a second)" + bad);
			Raven.setEnergy(p, 10f);
			last[0] = -1f;
			// Titans Together: a real Titan squadmate close by (the server re-checks the flag every second, mock players included)
			star[0] = mock(helper, 2.5, 1.5);
			com.projecthero.mod.starfire.Starfire.grant(star[0]);
			squad[0] = squad(helper, p, star[0]);
		});
		helper.runAfterDelay(75, () -> {
			helper.assertTrue(com.projecthero.mod.titans.TitansTogether.active(p) && titansTicks[0] > 5, "Titans Together came on");
			helper.assertTrue(ok[0], "Titans Together: 20% faster (0.3 a tick)" + bad);
			disband(helper, squad[0]);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void composureTiersMultipliersAndWrathGate(GameTestHelper helper) {
		ServerPlayer p = raven(helper);
		helper.assertTrue(Raven.tier(p) == Raven.Tier.CALM && Raven.spellMult(p) == 1f && Raven.shieldMult(p) == 1f, "calm: nothing changes");
		helper.assertTrue(!RavenAbilityManager.allowed(p, RavenAbilityManager.TRIGONS_WRATH), "no Wrath while calm");
		Raven.setComposure(p, 39f);
		helper.assertTrue(Raven.tier(p) == Raven.Tier.UNSETTLED && close(Raven.spellMult(p), RavenConfig.UNSETTLED_MULT) && Raven.shieldMult(p) == 1f,
				"under 40: Unsettled, spells +20%");
		helper.assertTrue(!RavenAbilityManager.allowed(p, RavenAbilityManager.TRIGONS_WRATH), "still no Wrath");
		Raven.setComposure(p, 8f);
		helper.assertTrue(Raven.tier(p) == Raven.Tier.RAGE && close(Raven.spellMult(p), RavenConfig.RAGE_MULT)
				&& close(Raven.shieldMult(p), RavenConfig.RAGE_SHIELD_MULT), "under 10: Rage, spells +40%, shields half");
		helper.assertTrue(RavenAbilityManager.allowed(p, RavenAbilityManager.TRIGONS_WRATH), "the Wrath unlocks");
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
		helper.assertTrue(p != null, "(Raven present)");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void darkEmpathyHealsAndRefillsOnAKill(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 1.5, 4.5, 20);
		p.setHealth(10f);
		Raven.setEnergy(p, 50f);
		helper.assertTrue(Raven.hit(p, z, 200f) && !z.isAlive(), "she kills it");
		helper.assertTrue(Math.abs(p.getHealth() - (10f + RavenConfig.EMPATHY_HEAL)) < 0.01f, "a heart back, health " + p.getHealth());
		helper.assertTrue(Math.abs(Raven.energy(p) - (50f + RavenConfig.EMPATHY_ENERGY)) < 0.01f, "and 5 Dark Energy, " + Raven.energy(p));
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mysticalAwarenessPulsesForHerAlone(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		ServerPlayer other = mock(helper, 2.5, 1.5);
		Zombie z = zombie(helper, 5.5, 5.5, 20);
		Chicken c = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(4.5, 2.0, 2.5));
		helper.assertTrue(RavenAwareness.pulse(0) && RavenAwareness.pulse(RavenConfig.AWARE_TICKS - 1)
				&& !RavenAwareness.pulse(RavenConfig.AWARE_TICKS) && RavenAwareness.pulse(RavenConfig.AWARE_PERIOD), "2 s in every 8");
		boolean on = RavenAwareness.pulse(helper.getLevel().getGameTime());
		helper.assertTrue(RavenAwareness.glows(p, z) == on, "a hostile within 24 glows for her during the pulse");
		helper.assertTrue(!RavenAwareness.glows(p, c), "a chicken never does");
		helper.assertTrue(!RavenAwareness.glows(other, z), "and nobody else sees it");
		helper.succeed();
	}

	// ---------------------------------------------------------------- R / Shift+R

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void darkBoltHitsAndBurstsOnNeighbours(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 0.5);
		Zombie first = zombie(helper, 1.5, 3.5, 100);
		Zombie beside = zombie(helper, 2.7, 3.5, 100); // (on her left: clear of the bolt's path from her right hand)
		Zombie far = zombie(helper, 6.5, 6.5, 100);
		look(p, first);
		float b1 = first.getHealth(), b2 = beside.getHealth(), b3 = far.getHealth();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.DARK_BOLT), "R fires");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.BOLT_COST), "8 Dark Energy");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(near(ratio(b1, first, RavenConfig.BOLT_DAMAGE)), "8 to the one it hit, lost " + (b1 - first.getHealth()));
			helper.assertTrue(near(ratio(b2, beside, RavenConfig.BOLT_BURST_DAMAGE)), "the pop: 4 to the one beside it, lost " + (b2 - beside.getHealth()));
			helper.assertTrue(far.getHealth() == b3, "nothing further out");
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
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.TK_COST), "10 Dark Energy up front");
		float[] e5 = { 0f };
		helper.runAfterDelay(5, () -> e5[0] = Raven.energy(p));
		helper.runAfterDelay(15, () -> {
			helper.assertTrue(Raven.energy(p) < e5[0] - 1.5f, "the hold drains 4 a second (" + e5[0] + " -> " + Raven.energy(p) + ")");
			face(p, 0f, 60f); // throw it down at the floor
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

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 160)
	public void soulShieldAbsorbsReflectsAndBursts(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		Zombie shooter = zombie(helper, 3.5, 7.0, 100);
		Zombie near = zombie(helper, 5.5, 3.5, 100);
		ticker(helper, p);
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SOUL_SHIELD), "G");
		helper.assertTrue(Raven.guarding(p) && close(Raven.state(p).guardHp, RavenConfig.GUARD_ABSORB), "30 to absorb");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.GUARD_COST)
				&& Raven.cooldownRemaining(p, RavenAbilityManager.SOUL_SHIELD) == RavenConfig.GUARD_COOLDOWN, "25 Dark Energy, 16 s cooldown");
		p.setHealth(20f);
		p.invulnerableTime = 0;
		p.hurt(p.damageSources().magic(), 15f);
		helper.assertTrue(p.getHealth() == 20f && close(Raven.state(p).guardHp, 15f), "the shield took 15, health " + p.getHealth());
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(shooter);
		arrow.setPos(p.getX(), p.getY() + 1.0, p.getZ() + 1.9);
		arrow.setDeltaMovement(0, 0, -0.15);
		helper.getLevel().addFreshEntity(arrow);
		int reflectedBefore = RavenMoves.reflected;
		int burstsBefore = RavenMoves.bursts;
		float nearBefore = near.getHealth();
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(RavenMoves.reflected > reflectedBefore && arrow.getOwner() == p, "an arrow is sent back, and it's hers now");
			helper.assertTrue(arrow.getDeltaMovement().z > 0, "flying back toward the shooter");
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().magic(), 6f);
			helper.assertTrue(close(Raven.state(p).guardHp, 9f), "21 soaked so far");
		});
		helper.runAfterDelay(RavenConfig.GUARD_TICKS + 3, () -> {
			helper.assertTrue(!Raven.guarding(p), "gone after 5 s");
			helper.assertTrue(RavenMoves.bursts > burstsBefore, "it soaked 20+, so it burst");
			helper.assertTrue(near(ratio(nearBefore, near, RavenConfig.GUARD_BURST_DAMAGE)), "8 to a creature within 4, lost " + (nearBefore - near.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void rageHalvesTheShields(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		Raven.setComposure(p, 5f);
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SOUL_SHIELD), "G in Rage");
		helper.assertTrue(close(Raven.state(p).guardHp, RavenConfig.GUARD_ABSORB * RavenConfig.RAGE_SHIELD_MULT), "the Soul Shield holds half: 15");
		Raven.setEnergy(p, 100f);
		Raven.setComposure(p, 5f);
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.ASTRAL_SANCTUARY, false), "Astral Sanctuary in Rage");
		helper.assertTrue(close(RavenSpells.sanctuary(p).reduction(), RavenConfig.SANCTUARY_REDUCTION * RavenConfig.RAGE_SHIELD_MULT),
				"its 40% is halved to 20%");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void soulSelfSteersEngulfsAndStepsThrough(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 0.5);
		Zombie z = zombie(helper, 3.5, 3.5, 100);
		face(p, 0f, 0f);
		ticker(helper, p);
		float before = z.getHealth();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SOUL_SELF), "Shift+G");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.SOUL_COST)
				&& Raven.cooldownRemaining(p, RavenAbilityManager.SOUL_SELF) == RavenConfig.SOUL_COOLDOWN, "35 Dark Energy, 18 s cooldown");
		SoulSelfEntity soul = RavenMoves.soul(p);
		helper.assertTrue(soul != null && RavenMoves.soulOut(p), "the raven is out");
		Vec3 start = soul.position();
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(soul.position().z - start.z > 2.5, "steered along her look, moved " + (soul.position().z - start.z));
			helper.assertTrue(near(ratio(before, z, RavenConfig.SOUL_DAMAGE)), "14 to what it touched, lost " + (before - z.getHealth()));
			helper.assertTrue(z.hasEffect(MobEffects.BLINDNESS) && z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Blindness and Slowness");
			Vec3 at = soul.position();
			float e = Raven.energy(p);
			helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SOUL_SELF), "Shift+G again: step through");
			helper.assertTrue(p.position().distanceTo(at) < 2.5, "she is where it was, " + p.position().distanceTo(at) + " away");
			helper.assertTrue(Raven.energy(p) >= e - 0.01f, "stepping through is free");
		});
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(soul.isRemoved() && !RavenMoves.soulOut(p), "and the raven is gone");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 220)
	public void soulSelfFliesHomeAfterFiveSeconds(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		face(p, 0f, -90f); // straight up: out of everyone's way
		ticker(helper, p);
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SOUL_SELF), "Shift+G");
		SoulSelfEntity soul = RavenMoves.soul(p);
		helper.runAfterDelay(RavenConfig.SOUL_STEER_TICKS - 5, () -> {
			helper.assertTrue(!soul.homing() && soul.position().distanceTo(p.position()) <= RavenConfig.SOUL_LEASH + 1.0,
					"still steered, and never past the 40-block leash: " + soul.position().distanceTo(p.position()));
		});
		helper.runAfterDelay(RavenConfig.SOUL_STEER_TICKS + 5, () -> helper.assertTrue(soul.isRemoved() || soul.homing(), "then it turns for home"));
		helper.runAfterDelay(RavenConfig.SOUL_STEER_TICKS + 70, () -> {
			helper.assertTrue(soul.isRemoved(), "and dissolves back into her");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Z / Shift+Z

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80, batch = "raven_crush_alone")
	public void telekineticCrushLiftsTheFrontAndSlams(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 2.5);
		Zombie a = zombie(helper, 2.5, 5.5, 100);
		Zombie b = zombie(helper, 4.5, 5.5, 100);
		Zombie boss = zombie(helper, 3.5, 6.8, 500);
		Zombie behind = zombie(helper, 3.5, 0.5, 100);
		face(p, 0f, 10f);
		ticker(helper, p);
		double baseY = a.getY(), bossY = boss.getY();
		float ba = a.getHealth(), bb = b.getHealth(), bBoss = boss.getHealth(), bBehind = behind.getHealth();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.TELEKINETIC_CRUSH), "Z");
		helper.assertTrue(RavenMoves.lifting(p) == 3, "the three in front are gripped, not the one behind: " + RavenMoves.lifting(p));
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.CRUSH_COST)
				&& Raven.cooldownRemaining(p, RavenAbilityManager.TELEKINETIC_CRUSH) == RavenConfig.CRUSH_COOLDOWN, "30 Dark Energy, 9 s cooldown");
		helper.runAfterDelay(RavenConfig.CRUSH_RISE_TICKS + 6, () -> {
			double up = a.getY() - baseY;
			helper.assertTrue(up > 2.2 && up < 3.6, "held in the air about 3 blocks up, " + up);
			helper.assertTrue(Math.abs(boss.getY() - bossY) < 0.3, "a boss isn't lifted");
			helper.assertTrue(a.getHealth() == ba, "no damage while it hangs");
		});
		helper.runAfterDelay(RavenConfig.CRUSH_RISE_TICKS + RavenConfig.CRUSH_HOLD_TICKS + 5, () -> {
			helper.assertTrue(near(ratio(ba, a, RavenConfig.CRUSH_DAMAGE)) && near(ratio(bb, b, RavenConfig.CRUSH_DAMAGE)),
					"slammed for 14 each, lost " + (ba - a.getHealth()) + " / " + (bb - b.getHealth()));
			helper.assertTrue(near(ratio(bBoss, boss, RavenConfig.CRUSH_DAMAGE)), "the boss takes the 14 too, lost " + (bBoss - boss.getHealth()));
			helper.assertTrue(a.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && RavenMoves.pinned(p, a), "stunned for a second");
			helper.assertTrue(behind.getHealth() == bBehind, "the one behind her is untouched");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400, batch = "raven_wrath_alone") // alone: 12+ blocks of tentacles
	public void trigonsWrathNeedsRageThenDrains(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 5.5, 5.5, 200);
		ticker(helper, p);
		// v0.17.4: 20 max health now; on a Hard world (left by another test) the zombie could kill her mid-Wrath
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 4));
		helper.assertTrue(!RavenMoves.use(p, RavenAbilityManager.TRIGONS_WRATH), "refused while calm");
		Raven.setComposure(p, 30f);
		helper.assertTrue(!RavenMoves.use(p, RavenAbilityManager.TRIGONS_WRATH), "refused while only Unsettled");
		Raven.setComposure(p, 5f);
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.TRIGONS_WRATH), "Shift+Z in Rage");
		helper.assertTrue(Raven.inWrath(p) && Math.abs(p.getScale() - RavenConfig.WRATH_SCALE) < 0.01f, "a giant: 3x, scale " + p.getScale());
		helper.assertTrue(Raven.cooldownRemaining(p, RavenAbilityManager.TRIGONS_WRATH) == RavenConfig.WRATH_COOLDOWN, "3 minute cooldown");
		float[] preSlam = { 0f };
		helper.runAfterDelay(RavenConfig.WRATH_SLAMS[0], () -> preSlam[0] = z.getHealth());
		helper.runAfterDelay(RavenConfig.WRATH_SLAMS[0] + RavenConfig.WRATH_LIFT_TICKS + 12, () -> {
			float lost = preSlam[0] - z.getHealth();
			helper.assertTrue(lost >= RavenConfig.WRATH_DAMAGE * 0.85f && lost <= RavenConfig.WRATH_DAMAGE * 1.15f + 2f,
					"the first grab-and-slam: a flat 30 (no Rage bonus on top), lost " + lost);
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
		face(p, 0f, 0f);
		ticker(helper, p);
		Vec3 from = p.position();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SHADOW_DASH), "X");
		helper.assertTrue(Raven.inShadow(p) && close(Raven.energy(p), 100f - RavenConfig.DASH_COST), "a streak of shadow, 12 Dark Energy");
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
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.STEP_COST), "20 Dark Energy");
		helper.runAfterDelay(RavenConfig.STEP_SINK_TICKS + 2, () -> {
			helper.assertTrue(p.distanceTo(z) < 3.0, "she erupted beside it, " + p.distanceTo(z) + " away");
			helper.assertTrue(near(ratio(before, z, RavenConfig.STEP_DAMAGE)), "12 damage, lost " + (before - z.getHealth()));
			helper.assertTrue(z.getDeltaMovement().y > 0.3 || z.getY() > helper.absoluteVec(new Vec3(0, 2.2, 0)).y, "launched up");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- C / Shift+C

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60, batch = "raven_wave_alone")
	public void darkEnergyWaveChargesThenRolls(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 0.5);
		Zombie ahead = zombie(helper, 3.5, 6.5, 100);
		Zombie aside = zombie(helper, 0.5, 1.5, 100);
		face(p, 0f, 0f);
		ticker(helper, p);
		float ba = ahead.getHealth(), bs = aside.getHealth();
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.ENERGY_WAVE), "C");
		helper.assertTrue(RavenMoves.charging(p) && close(Raven.energy(p), 100f - RavenConfig.WAVE_COST)
				&& Raven.cooldownRemaining(p, RavenAbilityManager.ENERGY_WAVE) == RavenConfig.WAVE_COOLDOWN, "it gathers; 30 Dark Energy, 11 s cooldown");
		helper.runAfterDelay(RavenConfig.WAVE_CHARGE_TICKS - 3, () -> helper.assertTrue(ahead.getHealth() == ba, "nothing yet while it charges"));
		helper.runAfterDelay(RavenConfig.WAVE_CHARGE_TICKS + 10, () -> {
			helper.assertTrue(!RavenMoves.charging(p), "released");
			helper.assertTrue(near(ratio(ba, ahead, RavenConfig.WAVE_DAMAGE)), "12 to what's ahead, lost " + (ba - ahead.getHealth()));
			helper.assertTrue(ahead.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and Slowness");
			helper.assertTrue(aside.getHealth() == bs, "the one off to the side is outside the wave");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 360)
	public void demonicAscensionBuffsAndExpires(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		ticker(helper, p);
		double baseSpeed = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.ASCENSION), "Shift+C");
		helper.assertTrue(Raven.ascended(p) && Raven.casting(p), "ascended, eyes white");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.ASCEND_COST)
				&& Raven.cooldownRemaining(p, RavenAbilityManager.ASCENSION) == RavenConfig.ASCEND_COOLDOWN, "40 Dark Energy, 60 s cooldown");
		helper.assertTrue(close(Raven.composure(p), RavenConfig.COMPOSURE_MAX - RavenConfig.ASCEND_COMPOSURE), "it shakes her: Composure -20");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed * (1 + RavenConfig.ASCEND_SPEED)) < 1.0e-4, "+20% speed");
		helper.assertTrue(close(Raven.spellMult(p), RavenConfig.ASCEND_POWER) && close(Raven.costMult(p), RavenConfig.ASCEND_COST_MULT), "+35% power, -25% costs");
		helper.assertTrue(!RavenMoves.use(p, RavenAbilityManager.ASCENSION), "can't stack it");
		float e = Raven.energy(p);
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.DARK_BOLT), "a bolt");
		helper.assertTrue(close(Raven.energy(p), e - RavenConfig.BOLT_COST * RavenConfig.ASCEND_COST_MULT), "costs 6, not 8");
		Raven.setEnergy(p, 100f);
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SOUL_SHIELD) && close(Raven.state(p).guardHp, RavenConfig.GUARD_ABSORB_ASCENDED), "the Soul Shield holds 50");
		helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.SOUL_SELF) && RavenMoves.soul(p).size() == RavenConfig.SOUL_ASCENDED_MULT, "the Soul-Self is half again as big");
		RavenMoves.soul(p).discard();
		helper.runAfterDelay(RavenConfig.ASCEND_TICKS + 3, () -> {
			helper.assertTrue(!Raven.ascended(p) && Raven.spellMult(p) == 1f, "over after 15 s");
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - baseSpeed) < 1.0e-4, "speed back to normal");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- V: the wheel

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void wheelPickIsSavedAndVTapCastsIt(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		helper.assertTrue(Raven.selectedSpell(p) == RavenSpell.VOID_VORTEX, "the first spell to start");
		RavenAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		RavenAbilityManager.handle(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(Raven.energy(p) == RavenConfig.ENERGY_MAX, "the V slot itself does nothing on the server (the client times tap / hold)");
		RavenSpells.select(p, RavenSpell.SHADOW_VEIL.ordinal());
		helper.assertTrue(Raven.selectedSpell(p) == RavenSpell.SHADOW_VEIL, "picked on the wheel");
		CompoundTag saved = (CompoundTag) RavenState.CODEC.encodeStart(NbtOps.INSTANCE, Raven.state(p)).getOrThrow();
		RavenState loaded = RavenState.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.spell == RavenSpell.SHADOW_VEIL.ordinal(), "and it's saved");
		RavenSpells.select(p, 99);
		helper.assertTrue(Raven.selectedSpell(p) == RavenSpell.SHADOW_VEIL, "a bad pick is ignored");
		helper.assertTrue(RavenSpells.castSelected(p), "V tapped");
		helper.assertTrue(Raven.veiled(p) && close(Raven.energy(p), 100f - RavenConfig.VEIL_COST), "it cast Shadow Veil (20 Dark Energy)");
		helper.assertTrue(Raven.cooldownRemaining(p, RavenSpell.SHADOW_VEIL.id) == RavenConfig.VEIL_COOLDOWN, "on its own cooldown");
		helper.assertTrue(RavenAbilityManager.cooldownRemaining(p, RavenAbilityManager.CAST_SPELL) == RavenConfig.VEIL_COOLDOWN, "which the V box shows");
		helper.assertTrue(!RavenSpells.castSelected(p), "not again until it's ready");
		RavenSpells.breakVeil(p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void misfiresOnlyWhenUnsettledAboutOneInEight(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		RandomSource r = RandomSource.create(42L);
		int calm = 0;
		for (int i = 0; i < 2000; i++) {
			calm += RavenSpells.misfires(p, r) ? 1 : 0;
		}
		helper.assertTrue(calm == 0, "calm: never, got " + calm);
		Raven.setComposure(p, 30f);
		int wild = 0;
		int n = 8000;
		for (int i = 0; i < n; i++) {
			wild += RavenSpells.misfires(p, r) ? 1 : 0;
		}
		float rate = wild / (float) n;
		helper.assertTrue(rate > 0.10f && rate < 0.15f, "Unsettled: about 1 in 8 (0.125), got " + rate);
		// a misfire of a spell that isn't cast at a spot: it slips away, paid for, half the cooldown
		Raven.setComposure(p, 100f);
		int before = RavenSpells.misfired;
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.SOUL_DETONATION, true), "a forced misfire");
		helper.assertTrue(!RavenSpells.detonating(p) && RavenSpells.misfired > before, "the spell itself never happened");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.DETONATE_COST), "but it was paid for");
		helper.assertTrue(Raven.cooldownRemaining(p, RavenSpell.SOUL_DETONATION.id) == RavenConfig.DETONATE_COOLDOWN / 2, "half the cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the spells

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 120, batch = "raven_vortex_alone")
	public void voidVortexPullsHurtsAndBlows(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 0.5);
		Zombie z = zombie(helper, 6.0, 5.0, 100);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(3.5, 2.0, 5.0)));
		ticker(helper, p);
		float before = z.getHealth();
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.VOID_VORTEX, false), "Void Vortex");
		helper.assertTrue(RavenSpells.vortexOpen(p) && close(Raven.energy(p), 100f - RavenConfig.VORTEX_COST), "open; 30 Dark Energy");
		Vec3 eye = helper.absoluteVec(new Vec3(3.5, 3.2, 5.0));
		double d0 = z.position().add(0, z.getBbHeight() * 0.5, 0).distanceTo(eye);
		helper.runAfterDelay(15, () -> {
			double d = z.position().add(0, z.getBbHeight() * 0.5, 0).distanceTo(eye);
			helper.assertTrue(d < d0 - 0.5, "pulled in: " + d0 + " -> " + d);
		});
		helper.runAfterDelay(RavenConfig.VORTEX_TICKS + 4, () -> {
			helper.assertTrue(!RavenSpells.vortexOpen(p), "it blew");
			float lost = before - z.getHealth();
			float most = RavenConfig.VORTEX_TICK_DAMAGE * 4 + RavenConfig.VORTEX_BLAST_DAMAGE;
			helper.assertTrue(lost >= (RavenConfig.VORTEX_BLAST_DAMAGE + RavenConfig.VORTEX_TICK_DAMAGE) * 0.85f && lost <= most * 1.15f,
					"3 a second while caught, then 10: lost " + lost);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void shadowBindingRootsTheArea(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 0.5);
		Zombie a = zombie(helper, 4.0, 5.0, 100);
		Zombie b = zombie(helper, 5.5, 6.0, 100);
		Zombie far = zombie(helper, 0.5, 1.5, 100);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(4.5, 2.0, 5.5)));
		ticker(helper, p);
		float ba = a.getHealth(), bb = b.getHealth(), bf = far.getHealth();
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.SHADOW_BINDING, false), "Shadow Binding");
		helper.assertTrue(near(ratio(ba, a, RavenConfig.BIND_DAMAGE)) && near(ratio(bb, b, RavenConfig.BIND_DAMAGE)),
				"6 to each in the circle, lost " + (ba - a.getHealth()) + " / " + (bb - b.getHealth()));
		helper.assertTrue(far.getHealth() == bf, "nothing outside it");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.BIND_COST), "20 Dark Energy");
		Vec3[] at = { null };
		helper.runAfterDelay(2, () -> {
			at[0] = a.position();
			a.setDeltaMovement(0.8, 0.5, 0.8); // try to break free
		});
		helper.runAfterDelay(30, () -> helper.assertTrue(RavenMoves.pinned(p, a) && a.position().distanceTo(at[0]) < 0.2,
				"rooted, moved " + a.position().distanceTo(at[0])));
		helper.runAfterDelay(RavenConfig.BIND_TICKS + 4, () -> {
			helper.assertTrue(!RavenMoves.pinned(p, a), "free after 3 s");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 80)
	public void soulDetonationMarksThenBlowsAndBreaksOnHits(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		Zombie close = zombie(helper, 3.5, 5.0, 100);
		Zombie edge = zombie(helper, 3.5, 7.5, 100);
		ticker(helper, p);
		float bc = close.getHealth(), be = edge.getHealth();
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.SOUL_DETONATION, false), "Soul Detonation");
		helper.assertTrue(RavenSpells.marks(p).size() == 2, "both within 6 are marked");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.DETONATE_COST), "40 Dark Energy");
		float[] want = { 0f, 0f };
		helper.runAfterDelay(RavenConfig.DETONATE_DELAY - 3, () -> {
			helper.assertTrue(close.getHealth() == bc, "nothing yet");
			want[0] = RavenConfig.DETONATE_MAX * (1f - (1f - RavenConfig.DETONATE_EDGE) * Math.min(1f, close.distanceTo(p) / (float) RavenConfig.DETONATE_RADIUS));
			want[1] = RavenConfig.DETONATE_MAX * (1f - (1f - RavenConfig.DETONATE_EDGE) * Math.min(1f, edge.distanceTo(p) / (float) RavenConfig.DETONATE_RADIUS));
		});
		helper.runAfterDelay(RavenConfig.DETONATE_DELAY + 3, () -> {
			helper.assertTrue(!RavenSpells.detonating(p), "the marks blew");
			helper.assertTrue(near(ratio(bc, close, want[0])) && near(ratio(be, edge, want[1])),
					"more near her: " + (bc - close.getHealth()) + " (want " + want[0] + "), " + (be - edge.getHealth()) + " (want " + want[1] + ")");
			helper.assertTrue(want[0] > want[1], "(and the nearer one took more)");
			// cast again and take 8: broken
			Raven.clearCooldowns(p);
			Raven.setEnergy(p, 100f);
			helper.assertTrue(RavenSpells.cast(p, RavenSpell.SOUL_DETONATION, false) && RavenSpells.detonating(p), "cast again");
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().generic(), 10f); // 10 -> 9 after her 10%
			helper.assertTrue(!RavenSpells.detonating(p), "8+ damage while it counts down breaks it");
		});
		helper.runAfterDelay(RavenConfig.DETONATE_DELAY * 2 + 10, helper::succeed);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void soulDevourDrainsAndHealsHer(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 0.5);
		Zombie z = zombie(helper, 3.5, 5.0, 100);
		look(p, z);
		ticker(helper, p);
		p.setHealth(4f); // v0.17.4: 20 max health -- room for the whole 12 heal
		float before = z.getHealth();
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.SOUL_DEVOUR, false), "Soul Devour");
		helper.assertTrue(RavenSpells.devouring(p) && close(Raven.energy(p), 100f - RavenConfig.DEVOUR_COST), "the beam is on; 35 Dark Energy");
		float total = RavenConfig.DEVOUR_DAMAGE * (RavenConfig.DEVOUR_TICKS / RavenConfig.DEVOUR_PULSE);
		helper.runAfterDelay(RavenConfig.DEVOUR_TICKS + 4, () -> {
			helper.assertTrue(!RavenSpells.devouring(p), "over after 3 s");
			helper.assertTrue(near(ratio(before, z, total)), "4 every half second (24), lost " + (before - z.getHealth()));
			float healed = p.getHealth() - 4f;
			helper.assertTrue(healed >= total * RavenConfig.DEVOUR_HEAL_SHARE * 0.85f && healed <= total * RavenConfig.DEVOUR_HEAL_SHARE + 2f,
					"half of it healed her (12), healed " + healed);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void debrisStormOrbitsCopiesAndFiresOnePerTap(GameTestHelper helper) {
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
			helper.assertTrue(RavenSpells.cast(p, RavenSpell.DEBRIS_STORM, false), "Debris Storm tears up the ground");
		} finally {
			griefing(helper, was);
		}
		int n = RavenMoves.debrisCount(p);
		helper.assertTrue(n == RavenConfig.DEBRIS_COUNT && Raven.state(p).debris == n, "8 blocks orbit her, got " + n);
		helper.assertTrue(helper.getBlockState(new BlockPos(0, 1, 0)).is(Blocks.DIRT) && helper.getBlockState(new BlockPos(5, 1, 3)).is(Blocks.DIRT),
				"griefing off: the ground is untouched (energy copies)");
		float e = Raven.energy(p);
		helper.assertTrue(close(e, 100f - RavenConfig.DEBRIS_COST), "30 Dark Energy");
		helper.assertTrue(RavenSpells.costNow(p, RavenSpell.DEBRIS_STORM) == 0f, "tapping again is free while they orbit");
		float[] targetBefore = { 0f };
		helper.runAfterDelay(10, () -> {
			look(p, target);
			targetBefore[0] = target.getHealth();
			helper.assertTrue(RavenSpells.cast(p, RavenSpell.DEBRIS_STORM, null), "tapped again: fire one");
			helper.assertTrue(RavenMoves.debrisCount(p) == RavenConfig.DEBRIS_COUNT - 1, "one left the orbit");
			helper.assertTrue(Raven.energy(p) >= e, "firing is free");
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(near(ratio(targetBefore[0], target, RavenConfig.DEBRIS_DAMAGE)), "8 damage, lost " + (targetBefore[0] - target.getHealth()));
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 40)
	public void dimensionalRiftStepsThroughAWall(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 0.5);
		for (int x = 0; x < 8; x++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
			}
		}
		face(p, 0f, 0f);
		ticker(helper, p);
		double wallZ = helper.absoluteVec(new Vec3(0, 0, 4.0)).z;
		Vec3 spot = RavenSpells.riftSpot(p);
		helper.assertTrue(spot != null && spot.z > wallZ, "aimed at a wall: the way out is on the far side, " + spot);
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.DIMENSIONAL_RIFT, false), "Dimensional Rift");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.RIFT_COST), "25 Dark Energy");
		helper.runAfterDelay(RavenConfig.RIFT_STEP_TICKS + 2, () -> {
			helper.assertTrue(p.getZ() > wallZ, "she stepped through to the far side, z " + (p.getZ() - wallZ));
			helper.succeed();
		});
	}

	/** v0.17.4: the rift reaches 100 blocks and stays open after her; a squadmate who walks in follows, anyone else doesn't. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60 + RavenConfig.RIFT_OPEN_TICKS)
	public void dimensionalRiftLetsHerSquadFollow(GameTestHelper helper) {
		helper.assertTrue(RavenConfig.RIFT_RANGE >= 100.0, "100-block range");
		ServerPlayer p = raven(helper, 1.5, 1.5);
		face(p, 0f, 30f); // down at the floor a few blocks ahead
		ticker(helper, p);
		// both standing in the entry portal's mouth (0.9 blocks ahead of her)
		ServerPlayer mate = mock(helper, 1.5, 2.5);
		ServerPlayer stranger = mock(helper, 2.0, 2.3);
		var s = squad(helper, p, mate);
		Vec3 spot = RavenSpells.riftSpot(p);
		helper.assertTrue(spot != null && spot.z > p.getZ() + 2.0, "the way out is ahead on the floor, " + spot);
		Vec3 strangerAt = stranger.position();
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.DIMENSIONAL_RIFT, false), "Dimensional Rift");
		helper.runAfterDelay(RavenConfig.RIFT_STEP_TICKS + 3, () -> {
			helper.assertTrue(p.position().distanceTo(spot) < 1.5, "she stepped through");
			helper.assertTrue(RavenSpells.riftOpen(p), "the rift stays open behind her");
			helper.assertTrue(mate.position().distanceTo(spot) < 1.5, "the squadmate followed her out, " + mate.position().distanceTo(spot));
			helper.assertTrue(stranger.position().distanceTo(strangerAt) < 0.01, "a player outside the squad stays put");
		});
		helper.runAfterDelay(RavenConfig.RIFT_STEP_TICKS + RavenConfig.RIFT_OPEN_TICKS + 4, () -> {
			disband(helper, s);
			helper.assertTrue(!RavenSpells.riftOpen(p), "closed after 6 s");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void soulRestorationChannelsAndTakesAMatesPain(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		ticker(helper, p);
		p.setHealth(4f); // v0.17.4: 20 max health -- room for the whole 12 heal
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 400, 0));
		Raven.setComposure(p, 40f);
		face(p, 180f, 0f); // at the wall: no one aimed at
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.SOUL_RESTORATION, false), "Soul Restoration on herself");
		helper.assertTrue(RavenSpells.channelling(p) && close(Raven.energy(p), 100f - RavenConfig.HEAL_COST), "channelling; 45 Dark Energy");
		helper.runAfterDelay(RavenConfig.HEAL_CHANNEL_TICKS + 3, () -> {
			helper.assertTrue(!RavenSpells.channelling(p), "done");
			helper.assertTrue(p.getHealth() >= 4f + RavenConfig.HEAL_SELF - 1.5f, "6 hearts back, health " + p.getHealth());
			helper.assertTrue(!p.hasEffect(MobEffects.POISON), "poison shaken off");
			helper.assertTrue(Raven.composure(p) >= 40f + RavenConfig.HEAL_CALM - 2f, "calmer (+30), " + Raven.composure(p));
			// on a squadmate
			Raven.clearCooldowns(p);
			Raven.setEnergy(p, 100f);
			p.setHealth(20f);
			ServerPlayer mate = mock(helper, 1.5, 5.5);
			mate.setHealth(4f);
			var s = squad(helper, p, mate);
			try {
				face(p, 0f, 0f);
				look(p, mate);
				helper.assertTrue(RavenMoves.healTarget(p) == mate, "aimed at the squadmate");
				helper.assertTrue(RavenSpells.cast(p, RavenSpell.SOUL_RESTORATION, false), "cast on them");
				helper.assertTrue(Math.abs(p.getHealth() - 12f) < 0.01f, "she took 8 of their pain (16 missing, half), health " + p.getHealth());
				helper.assertTrue(Math.abs(mate.getHealth() - 20f) < 0.01f, "and healed them double, health " + mate.getHealth());
			} finally {
				disband(helper, s);
			}
		});
		helper.runAfterDelay(RavenConfig.HEAL_CHANNEL_TICKS + 8, helper::succeed);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void soulRestorationChannelBreaksWhenHit(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 1.5);
		Zombie z = zombie(helper, 5.5, 5.5, 100);
		ticker(helper, p);
		face(p, 180f, 0f);
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.SOUL_RESTORATION, false), "channelling");
		p.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
		helper.runAfterDelay(4, () -> helper.assertTrue(RavenSpells.channelling(p), "a poison tick doesn't break it"));
		helper.runAfterDelay(5, () -> {
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().mobAttack(z), 2f);
			helper.assertTrue(!RavenSpells.channelling(p), "a hit breaks it");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void telekineticPullFlipsLeversYanksAndGathersItems(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 1.5, 0.5);
		BlockPos leverPos = helper.absolutePos(new BlockPos(1, 2, 4));
		helper.getLevel().setBlockAndUpdate(leverPos, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
		Vec3 itemAt = helper.absoluteVec(new Vec3(6.5, 2.2, 6.5));
		ItemEntity item = new ItemEntity(helper.getLevel(), itemAt.x, itemAt.y, itemAt.z, new ItemStack(Items.DIAMOND));
		item.setPickUpDelay(200);
		helper.getLevel().addFreshEntity(item);
		p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(leverPos).add(0, -0.4, 0));
		ticker(helper, p);
		double d0 = item.distanceTo(p);
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.TELEKINETIC_PULL, false), "Telekinetic Pull");
		helper.assertTrue(helper.getLevel().getBlockState(leverPos).getValue(LeverBlock.POWERED), "the lever she looked at flipped");
		helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.PULL_COST), "15 Dark Energy");
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(item.distanceTo(p) < d0 - 2.0, "the loose item flies to her: " + d0 + " -> " + item.distanceTo(p));
			Raven.clearCooldowns(p);
			Chicken c = helper.spawnWithNoFreeWill(EntityType.CHICKEN, new Vec3(5.5, 2.0, 5.5));
			look(p, c);
			helper.assertTrue(RavenSpells.cast(p, RavenSpell.TELEKINETIC_PULL, false), "again, at a chicken");
			Vec3 v = c.getDeltaMovement();
			Vec3 toHer = p.position().subtract(c.position()).multiply(1, 0, 1).normalize();
			helper.assertTrue(v.multiply(1, 0, 1).dot(toHer) > 0.5, "a light creature is yanked toward her, " + v);
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void astralSanctuaryShieldsTheSquadAndKeepsThingsOut(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		ServerPlayer mate = mock(helper, 4.5, 3.5);
		Zombie z = zombie(helper, 5.5, 3.5, 100);
		ticker(helper, p);
		var s = squad(helper, p, mate);
		try {
			helper.assertTrue(RavenSpells.cast(p, RavenSpell.ASTRAL_SANCTUARY, false), "Astral Sanctuary");
			helper.assertTrue(close(Raven.energy(p), 100f - RavenConfig.SANCTUARY_COST)
					&& Raven.cooldownRemaining(p, RavenSpell.ASTRAL_SANCTUARY.id) == RavenConfig.SANCTUARY_COOLDOWN, "60 Dark Energy, 35 s cooldown");
			mate.setHealth(20f);
			mate.invulnerableTime = 0;
			mate.hurt(mate.damageSources().generic(), 10f);
			helper.assertTrue(Math.abs(mate.getHealth() - 14f) < 0.05f, "a squadmate inside takes 40% less (10 -> 6), health " + mate.getHealth());
			p.setHealth(20f);
			p.invulnerableTime = 0;
			p.hurt(p.damageSources().generic(), 10f);
			helper.assertTrue(Math.abs(p.getHealth() - (20f -10f * 0.9f * 0.6f)) < 0.05f, "so does she, after her own 10%: health " + p.getHealth());
		} finally {
			disband(helper, s);
		}
		Vec3 c = p.position();
		double d0 = z.position().distanceTo(c);
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(z);
		arrow.setPos(p.getX() - 2.0, p.getY() + 1.0, p.getZ());
		arrow.setDeltaMovement(0.2, 0, 0);
		helper.getLevel().addFreshEntity(arrow);
		int stopped = RavenSpells.stopped;
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(!arrow.isAlive() && RavenSpells.stopped > stopped, "an arrow from outside stops at the dome");
			helper.assertTrue(z.position().distanceTo(c) > d0 + 0.2, "a hostile creature is pushed back out: " + d0 + " -> " + z.position().distanceTo(c));
		});
		helper.runAfterDelay(10, helper::succeed);
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
	public void shadowVeilHidesHerAndEndsOnAnAttack(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		Zombie z = zombie(helper, 3.5, 6.5, 100);
		z.setTarget(p);
		ticker(helper, p);
		helper.assertTrue(RavenSpells.cast(p, RavenSpell.SHADOW_VEIL, false), "Shadow Veil");
		helper.assertTrue(Raven.veiled(p) && p.hasEffect(MobEffects.INVISIBILITY) && p.isSilent(), "invisible and silent");
		helper.assertTrue(z.getTarget() == null, "the zombie lost her");
		helper.runAfterDelay(RavenConfig.VEIL_TICKS + 2, () -> {
			helper.assertTrue(!Raven.veiled(p) && !p.hasEffect(MobEffects.INVISIBILITY) && !p.isSilent(), "back after 6 s");
			Raven.clearCooldowns(p);
			helper.assertTrue(RavenSpells.cast(p, RavenSpell.SHADOW_VEIL, false) && Raven.veiled(p), "veiled again");
			face(p, 0f, 0f);
			helper.assertTrue(RavenMoves.use(p, RavenAbilityManager.DARK_BOLT), "she attacks");
			helper.assertTrue(!Raven.veiled(p) && !p.hasEffect(MobEffects.INVISIBILITY) && !p.isSilent(), "and the veil is gone");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- Shift+V, flight, keys, old data

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 60)
	public void meditationFloatsRefillsAndBlocksMoves(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		Raven.setComposure(p, 20f);
		Raven.setEnergy(p, 20f);
		ticker(helper, p);
		helper.assertTrue(RavenMoves.startHold(p, RavenAbilityManager.MEDITATE), "Shift+V held");
		helper.assertTrue(Raven.meditating(p) && p.getAbilities().flying, "she floats");
		helper.assertTrue(!RavenMoves.use(p, RavenAbilityManager.DARK_BOLT), "no moves while meditating");
		helper.assertTrue(!RavenSpells.castSelected(p), "no spells either");
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

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void keysFollowTheHouseRules(GameTestHelper helper) {
		helper.assertTrue(RavenAbilityManager.SHADOW_DASH.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_3, false)), "X = Shadow Dash (movement)");
		helper.assertTrue(RavenAbilityManager.SHADOW_STEP.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_3, true)), "Shift+X = Shadow Step Strike");
		helper.assertTrue(RavenAbilityManager.CAST_SPELL.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_5, false)), "V = the spells (utility)");
		helper.assertTrue(RavenAbilityManager.MEDITATE.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_5, true)), "Shift+V = Meditate");
		helper.assertTrue(RavenAbilityManager.SOUL_SHIELD.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_2, false))
				&& RavenAbilityManager.TELEKINETIC_CRUSH.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_4, false))
				&& RavenAbilityManager.ENERGY_WAVE.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_6, false))
				&& RavenAbilityManager.ASCENSION.equals(RavenAbilityManager.moveFor(AbilitySlot.SLOT_6, true)), "G / Z / C / Shift+C as designed");
		helper.assertTrue(RavenAbilityManager.moveFor(AbilitySlot.values()[AbilitySlot.CORE_COUNT], false) == null, "nothing on H / N");
		int moves = 0;
		for (AbilitySlot s : AbilitySlot.values()) {
			moves += RavenAbilityManager.idsOf(s).length;
		}
		helper.assertTrue(moves == 12 && RavenSpell.count() == 10, "twelve moves on six keys and ten spells, got " + moves);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void oldSavedDarkShieldCantLeaveHerStuck(GameTestHelper helper) {
		CompoundTag old = new CompoundTag();
		old.putBoolean("has_power", true);
		old.putFloat("energy", 40f);
		old.putFloat("composure", 70f);
		old.putBoolean("shield", true);
		old.putFloat("shield_hp", 40f);
		RavenState s = RavenState.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(s.hasPower && s.energy == 40f && s.guardUntil == 0L && s.guardHp == 0f, "the old shield flag simply isn't read");
		ServerPlayer p = raven(helper, 3.5, 3.5);
		RavenState n = Raven.state(p).copy();
		long now = helper.getLevel().getGameTime();
		n.veilUntil = now + 100;
		n.ascendUntil = now + 100;
		n.guardUntil = now + 100;
		n.guardHp = 30f;
		p.setAttached(ModAttachments.RAVEN_STATE, n);
		Raven.onPlayerJoin(p);
		helper.assertTrue(!Raven.veiled(p) && !Raven.ascended(p) && !Raven.guarding(p) && !p.isSilent(), "joining clears every timed state");
		helper.succeed();
	}

	/**
	 * v0.17.3 tester round: Raven's superhero landing. Mock players never move, so the descent is the "known movement" and ground
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
	public void sprintDiveLandsInTheRavenStyle(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		RavenFlight.toggle(p);
		helper.assertTrue(Raven.isFlying(p), "precondition: levitating");
		diveAndLand(helper, p, new Vec3(0.0, -0.8, 0.0), q -> {
			com.projecthero.mod.flight.landing.HeroLanding.Active a = com.projecthero.mod.flight.landing.HeroLanding.active(q);
			helper.assertTrue(a != null, "a sprinting dive into the ground is a hero landing (and its effects ran without throwing)");
			helper.assertTrue(a.style() == com.projecthero.mod.flight.landing.HeroLandingStyle.RAVEN, "in her style, was " + a.style());
			helper.assertTrue(a.style().pose == com.projecthero.mod.flight.landing.HeroLandingStyle.Pose.WINGS, "kneeling, arms swept back");
			helper.succeed();
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
	public void calmLevitationTouchdownIsNoLanding(GameTestHelper helper) {
		ServerPlayer p = raven(helper, 3.5, 3.5);
		RavenFlight.toggle(p);
		diveAndLand(helper, p, new Vec3(0.0, -0.45, 0.0), q -> {
			helper.assertFalse(com.projecthero.mod.flight.landing.HeroLanding.landing(q), "a calm descent just lands");
			helper.succeed();
		});
	}
}
