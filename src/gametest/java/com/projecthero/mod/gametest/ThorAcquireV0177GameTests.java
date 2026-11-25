package com.projecthero.mod.gametest;

import java.util.List;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.item.ModDataComponents;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;
import com.projecthero.mod.thoracquire.CraterGuard;
import com.projecthero.mod.thoracquire.LivingBranch;
import com.projecthero.mod.thoracquire.MjolnirLift;
import com.projecthero.mod.thoracquire.Storms;
import com.projecthero.mod.thoracquire.ThorAcquire;
import com.projecthero.mod.thoracquire.ThorAcquireAttachments;
import com.projecthero.mod.thoracquire.TrialOfAsgard;
import com.projecthero.mod.thoracquire.Valor;
import com.projecthero.mod.thoracquire.entity.StormwardenBoss;
import com.projecthero.mod.thoracquire.forge.StarForge;
import com.projecthero.mod.thoracquire.forge.StarForgeBlockEntity;
import com.projecthero.mod.worldgen.CraterAmbience;
import com.projecthero.mod.worldgen.MjolnirCraterPiece;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: how players get Thor and Stormbreaker -- Valor (every source, saved, copied on death, nothing while powered), the
 * lift gate (unworthy / no storm / trial / lift + bind), the Trial of Asgard (unpowered only, a win lets you lift, a loss blocks
 * the rest of the storm, gaining a power mid-fight fails it, the Stormwarden's threat table), Mjolnir's Crater (the plinth and
 * hammer, the protection), the Star Forge (the ring, the 10 s channel and breaking it, finishing), the Living Branch and old
 * Unforged Stormbreakers.
 *
 * <p>The weather is forced through {@link Storms#forced} (a shared static), so the storm-on and storm-off tests run in batches
 * of their own and reset it; the crater is built 60 blocks up in a batch of its own (it is 25 blocks across).
 */
public class ThorAcquireV0177GameTests implements FabricGameTest {
	private static final String STORM_ON = "thor_acquire_storm_on_v0177";
	private static final String STORM_OFF = "thor_acquire_storm_off_v0177";
	private static final String CRATER = "thor_acquire_crater_v0177";

	private static ServerPlayer player(GameTestHelper helper, Vec3 rel) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(rel);
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		return p;
	}

	private static void leave(GameTestHelper helper, ServerPlayer... players) {
		for (ServerPlayer p : players) {
			if (!p.isRemoved()) {
				helper.getLevel().getServer().getPlayerList().remove(p);
			}
		}
	}

	private static MjolnirEntity hammer(GameTestHelper helper, Vec3 rel) {
		Vec3 at = helper.absoluteVec(rel);
		MjolnirEntity m = MjolnirEntity.createResting(helper.getLevel(), null, new ItemStack(ModItems.MJOLNIR), at, Vec3.ZERO, true);
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	/** The Stormwarden of {@code p}'s running trial (by its UUID: neighbouring tests' trials are close by). */
	private static StormwardenBoss guardian(GameTestHelper helper, ServerPlayer p) {
		TrialOfAsgard.Trial t = TrialOfAsgard.running(p.getUUID());
		return t == null || t.boss == null ? null : helper.getLevel().getEntity(t.boss) instanceof StormwardenBoss b ? b : null;
	}

	private static void clearWorldStuff(GameTestHelper helper) {
		AABB box = helper.getBounds().inflate(1);
		for (var e : helper.getLevel().getEntitiesOfClass(StormwardenBoss.class, box)) {
			e.discard();
		}
		for (var e : helper.getLevel().getEntitiesOfClass(MjolnirEntity.class, box)) {
			e.discard();
		}
	}

	// ---------------------------------------------------------------- Valor

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF)
	public void valorFillsFromEverySourceAndIsSavedAndKeptThroughDeath(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		helper.assertTrue(Valor.get(p) == 0, "a new player starts at 0 Valor");

		// Hero of the Village (a raid won) -- through the effect mixin
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 600, 0));
		helper.assertTrue(Valor.get(p) == 150, "Hero of the Village gives 150, got " + Valor.get(p));
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0));
		helper.assertTrue(Valor.get(p) == 150, "refreshing the effect is not a new deed");
		// raids, bosses, clears
		helper.assertTrue(Valor.add(p, Valor.Source.SUPERVILLAIN_RAID) == 150, "a Supervillain Raid gives 150");
		helper.assertTrue(Valor.add(p, Valor.Source.ZOMBIE_RAID) == 150, "a Zombie Raid gives 150");
		helper.assertTrue(Valor.add(p, Valor.Source.MINI_BOSS) == 75, "a mini-boss gives 75");
		helper.assertTrue(Valor.add(p, Valor.Source.CLEARED) == 100, "a clear gives 100");
		int before = Valor.get(p);
		// a curing (the vanilla trigger, through the mixin)
		Zombie zv = EntityType.ZOMBIE.create(level);
		Villager v = EntityType.VILLAGER.create(level);
		CriteriaTriggers.CURED_ZOMBIE_VILLAGER.trigger(p, zv, v);
		helper.assertTrue(Valor.get(p) == before + 40, "curing a zombie villager gives 40");

		// a real boss kill: a 200-health zombie killed by the player
		Zombie boss = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
		boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
		boss.setHealth(1f);
		before = Valor.get(p);
		boss.hurt(level.damageSources().playerAttack(p), 50f);
		helper.assertTrue(!boss.isAlive(), "the boss died");
		helper.assertTrue(Valor.get(p) == before + 200, "a boss kill gives 200, got +" + (Valor.get(p) - before));

		// a saved villager: kill the zombie that is going for it
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 5));
		Zombie z = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 6));
		z.setTarget(villager);
		before = Valor.get(p);
		z.hurt(level.damageSources().playerAttack(p), 100f);
		helper.assertTrue(Valor.get(p) == before + 15, "saving a villager gives 15, got +" + (Valor.get(p) - before));
		Zombie z2 = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 6));
		z2.setTarget(villager);
		z2.hurt(level.damageSources().playerAttack(p), 100f);
		helper.assertTrue(Valor.get(p) == before + 15, "but only once every 30 s");
		villager.discard();

		// capped, saved and copied on death
		Valor.set(p, 5000);
		helper.assertTrue(Valor.get(p) == Valor.MAX && Valor.isFull(p), "Valor is capped at 1000");
		Valor.set(p, 640);
		CompoundTag tag = new CompoundTag();
		p.saveWithoutId(tag);
		ServerPlayer loaded = player(helper, new Vec3(1.5, 2, 1.5));
		loaded.load(tag);
		helper.assertTrue(Valor.get(loaded) == 640, "Valor survives a save/load, got " + Valor.get(loaded));
		helper.assertTrue(ThorAcquireAttachments.VALOR.copyOnDeath() && ThorAcquireAttachments.VALOR.isPersistent(),
				"and is kept through death (persistent, copied on death)");
		leave(helper, p, loaded);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF)
	public void aPoweredPlayerEarnsNoValorAndAnUnpoweredOneDoes(GameTestHelper helper) {
		ServerPlayer powered = player(helper, new Vec3(2.5, 2, 2.5));
		ServerPlayer plain = player(helper, new Vec3(4.5, 2, 4.5));
		Valor.set(powered, 300);
		helper.assertTrue(com.projecthero.mod.hulk.Hulk.grant(powered), "the Hulk power granted");
		helper.assertTrue(com.projecthero.mod.hero.HeroTiers.hasAnyPower(powered), "counts as powered");
		helper.assertTrue(Valor.add(powered, Valor.Source.BOSS) == 0, "a powered player earns no Valor");
		helper.assertTrue(Valor.get(powered) == 300, "but keeps what they had");
		helper.assertFalse(com.projecthero.mod.hero.HeroTiers.hasAnyPower(plain), "a plain player is unpowered");
		helper.assertTrue(Valor.add(plain, Valor.Source.BOSS) == 200, "an unpowered player earns it");
		leave(helper, powered, plain);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the lift gate

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF)
	public void theHammerRefusesTheUnworthyAndWaitsForAStorm(GameTestHelper helper) {
		Storms.forced = false;
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		MjolnirEntity m = hammer(helper, new Vec3(3.5, 2, 3.5));
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.NOT_WORTHY, "no Valor: not worthy");
		helper.assertFalse(m.isRemoved() || Worthiness.isWorthy(p), "the hammer won't budge");
		p.setAttached(ThorAcquireAttachments.LIFT_FEEDBACK_READY, 0L);
		Valor.set(p, Valor.MAX);
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.NO_STORM, "full Valor but no storm: the storm has not answered");
		helper.assertFalse(m.isRemoved() || Worthiness.isWorthy(p), "still on the ground");
		p.setAttached(ThorAcquireAttachments.TRIAL_PASSED, true);
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.NO_STORM, "even a trial winner waits for the storm");
		Storms.forced = null;
		m.discard();
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_ON, timeoutTicks = 200)
	public void aTrialWinnerLiftsAndBindsInAStorm(GameTestHelper helper) {
		Storms.forced = true;
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		MjolnirEntity m = hammer(helper, new Vec3(4.5, 2, 4.5));
		Valor.set(p, Valor.MAX);
		p.setAttached(ThorAcquireAttachments.TRIAL_PASSED, true);
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.LIFTING, "worthy, storming, trial won: the lift starts");
		helper.assertTrue(m.isRemoved() && p.getMainHandItem().is(ModItems.MJOLNIR), "the hammer is in the hand");
		helper.assertFalse(Worthiness.isWorthy(p), "not bound until the cutscene's bind beat");
		helper.runAfterDelay(MjolnirLift.DURATION + 4, () -> {
			helper.assertTrue(Worthiness.isWorthy(p), "bound: the player is Thor");
			helper.assertTrue(com.projecthero.mod.power.ThorPassives.hasPowerOfThor(p), "Thor is the Primary power");
			ItemStack held = p.getMainHandItem();
			helper.assertTrue(held.is(ModItems.MJOLNIR) && p.getUUID().equals(held.get(ModDataComponents.BOUND_OWNER)), "the hammer is bound to them");
			helper.assertFalse(MjolnirLift.isLifting(p), "the cutscene is over");
			com.projecthero.mod.thorarmor.ThorArmor.strip(p);
			leave(helper, p);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Trial of Asgard

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_ON, timeoutTicks = 120)
	public void fullValorInAStormStartsTheTrialInsteadOfBinding(GameTestHelper helper) {
		Storms.forced = true;
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		MjolnirEntity m = hammer(helper, new Vec3(4.5, 2, 4.5));
		Valor.set(p, Valor.MAX);
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.TRIAL_STARTED, "the trial starts");
		helper.assertFalse(Worthiness.isWorthy(p) || m.isRemoved(), "nothing is bound and the hammer stays put");
		helper.assertTrue(TrialOfAsgard.running(p.getUUID()) != null, "a trial is running for the player");
		helper.runAfterDelay(TrialOfAsgard.INTRO_TICKS + 6, () -> {
			StormwardenBoss boss = guardian(helper, p);
			helper.assertTrue(boss != null && boss.isAlive(), "the Stormwarden formed");
			helper.assertTrue(boss.trialBound() && TrialOfAsgard.owns(boss), "bound to the trial");
			TrialOfAsgard.loseForTest(helper.getLevel(), p, "fled");
			clearWorldStuff(helper);
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_ON, timeoutTicks = 300)
	public void winningTheTrialLiftsTheHammer(GameTestHelper helper) {
		Storms.forced = true;
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		MjolnirEntity m = hammer(helper, new Vec3(4.5, 2, 4.5));
		Valor.set(p, Valor.MAX);
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.TRIAL_STARTED, "the trial starts");
		helper.runAfterDelay(TrialOfAsgard.INTRO_TICKS + 6, () -> {
			StormwardenBoss boss = guardian(helper, p);
			helper.assertTrue(boss != null, "the Stormwarden formed");
			boss.kill();
		});
		helper.runAfterDelay(TrialOfAsgard.INTRO_TICKS + 10, () -> {
			helper.assertTrue(p.getAttachedOrElse(ThorAcquireAttachments.TRIAL_PASSED, false), "the win is saved");
		});
		helper.runAfterDelay(TrialOfAsgard.INTRO_TICKS + 8 + TrialOfAsgard.LIFT_DELAY + MjolnirLift.DURATION + 6, () -> {
			helper.assertTrue(Worthiness.isWorthy(p), "the hammer answered and bound the winner");
			helper.assertTrue(TrialOfAsgard.running(p.getUUID()) == null, "the trial is over");
			com.projecthero.mod.thorarmor.ThorArmor.strip(p);
			clearWorldStuff(helper);
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_ON, timeoutTicks = 100)
	public void aLostTrialBlocksRetryingUntilTheNextStorm(GameTestHelper helper) {
		Storms.forced = true;
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		MjolnirEntity m = hammer(helper, new Vec3(4.5, 2, 4.5));
		Valor.set(p, Valor.MAX);
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.TRIAL_STARTED, "the trial starts");
		TrialOfAsgard.loseForTest(helper.getLevel(), p, "died");
		long storm = Storms.current(helper.getLevel().getServer());
		helper.assertTrue(p.getAttachedOrElse(ThorAcquireAttachments.TRIAL_FAILED_STORM, -1L) == storm, "the loss is saved against this storm");
		p.setAttached(ThorAcquireAttachments.LIFT_FEEDBACK_READY, 0L);
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.RETRY_NEXT_STORM, "no second try in the same storm");
		Storms.beginNewStormForTest(helper.getLevel().getServer());
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.TRIAL_STARTED, "the next storm allows another attempt");
		TrialOfAsgard.loseForTest(helper.getLevel(), p, "fled");
		helper.runAfterDelay(2, () -> {
			clearWorldStuff(helper);
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_ON)
	public void aPoweredPlayerIsRefusedAndKeepsTheirAttempt(GameTestHelper helper) {
		Storms.forced = true;
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		MjolnirEntity m = hammer(helper, new Vec3(4.5, 2, 4.5));
		Valor.set(p, Valor.MAX);
		helper.assertTrue(com.projecthero.mod.hulk.Hulk.grant(p), "a power (the Hulk) granted");
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.POWERED, "the trial refuses a powered player");
		helper.assertTrue(TrialOfAsgard.running(p.getUUID()) == null, "no trial started");
		helper.assertTrue(p.getAttachedOrElse(ThorAcquireAttachments.TRIAL_FAILED_STORM, -1L) == -1L, "and no attempt was used");
		m.discard();
		leave(helper, p);
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_ON, timeoutTicks = 120)
	public void gainingAPowerMidTrialFailsIt(GameTestHelper helper) {
		Storms.forced = true;
		ServerPlayer p = player(helper, new Vec3(2.5, 2, 2.5));
		MjolnirEntity m = hammer(helper, new Vec3(4.5, 2, 4.5));
		Valor.set(p, Valor.MAX);
		helper.assertTrue(MjolnirLift.tryLift(p, m) == MjolnirLift.Result.TRIAL_STARTED, "the trial starts");
		java.util.UUID[] bossId = new java.util.UUID[1];
		helper.runAfterDelay(TrialOfAsgard.INTRO_TICKS + 6, () -> {
			StormwardenBoss boss = guardian(helper, p);
			helper.assertTrue(boss != null, "the Stormwarden formed");
			bossId[0] = boss.getUUID();
			helper.assertTrue(com.projecthero.mod.hulk.Hulk.grant(p), "a serum mid-fight");
		});
		helper.runAfterDelay(TrialOfAsgard.INTRO_TICKS + 10, () -> {
			helper.assertTrue(TrialOfAsgard.running(p.getUUID()) == null, "the trial ended");
			long storm = Storms.current(helper.getLevel().getServer());
			helper.assertTrue(p.getAttachedOrElse(ThorAcquireAttachments.TRIAL_FAILED_STORM, -1L) == storm, "as a loss");
			helper.assertTrue(helper.getLevel().getEntity(bossId[0]) == null, "the Stormwarden dissipated");
			helper.assertFalse(m.isRemoved(), "the hammer stays");
			clearWorldStuff(helper);
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF)
	public void theStormwardenUsesTheThreatTable(GameTestHelper helper) {
		helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		StormwardenBoss boss = helper.spawn(ThorAcquire.STORMWARDEN, new BlockPos(4, 2, 4));
		ServerPlayer a = player(helper, new Vec3(1.5, 2, 1.5));
		ServerPlayer b = player(helper, new Vec3(6.5, 2, 6.5));
		boss.hurt(helper.getLevel().damageSources().playerAttack(a), 6f);
		boss.threat().applyNow();
		helper.assertTrue(boss.getTarget() == a, "the only attacker takes it, got " + boss.getTarget());
		helper.assertTrue(boss.threat().threatOf(a) > 0, "A is on the table");
		boss.threat().ageForTest(com.projecthero.mod.event.boss.BossThreat.SWITCH_LOCKOUT_TICKS);
		boss.invulnerableTime = 0;
		boss.hurt(helper.getLevel().damageSources().playerAttack(b), 30f);
		boss.threat().applyNow();
		helper.assertTrue(boss.getTarget() == b, "B hit far harder and takes the boss, got " + boss.getTarget());
		// a powered attacker can't hurt it (and never gets on the table)
		ServerPlayer c = player(helper, new Vec3(4.5, 2, 1.5));
		helper.assertTrue(com.projecthero.mod.hulk.Hulk.grant(c) && com.projecthero.mod.hero.HeroTiers.hasAnyPower(c), "C is powered");
		float hp = boss.getHealth();
		boss.invulnerableTime = 0;
		var src = helper.getLevel().damageSources().playerAttack(c);
		helper.assertFalse(StormwardenBoss.countsAgainst(src), "a powered player's hit does not count");
		boolean landed = boss.hurt(src, 20f);
		helper.assertFalse(landed, "hurt refuses it");
		helper.assertTrue(boss.getHealth() == hp && boss.threat().threatOf(c) == 0f, "borrowed power can't hurt it: no damage, no threat (" + hp + " -> " + boss.getHealth() + ")");
		boss.discard();
		leave(helper, a, b, c);
		helper.succeed();
	}

	// ---------------------------------------------------------------- Mjolnir's Crater

	@GameTest(template = EMPTY_STRUCTURE, batch = CRATER, timeoutTicks = 60)
	public void theCraterBuildsWithItsPlinthAndHammerAndIsProtected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(4, 60, 4));
		int surface = base.getY();
		for (BlockPos q : BlockPos.betweenClosed(base.offset(-14, -8, -14), base.offset(14, 0, 14))) {
			level.setBlock(q, q.getY() == surface ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
		}
		BlockPos spot = MjolnirCraterPiece.buildForTest(level, base.getX(), surface, base.getZ());
		helper.assertTrue(spot.getY() == MjolnirCraterPiece.plinthTop(surface) + 1, "the hammer spot is on the plinth");
		helper.assertTrue(level.getBlockState(spot.below()).is(Blocks.CHISELED_STONE_BRICKS), "a chiseled pillar block on top, got "
				+ level.getBlockState(spot.below()));
		helper.assertTrue(level.getBlockState(spot.below(2)).is(Blocks.CRACKED_STONE_BRICKS), "on a cracked stone base");
		helper.assertTrue(level.getBlockState(spot.below(2).east()).is(Blocks.STONE_BRICK_STAIRS), "stepped with stairs");
		helper.assertTrue(level.getBlockState(spot).isAir() && level.getBlockState(spot.above()).isAir(), "open above the plinth");
		helper.assertTrue(level.getBlockState(new BlockPos(base.getX() + 5, surface, base.getZ())).isAir(), "the bowl is dug out");
		BlockPos floor = new BlockPos(base.getX() + 2, spot.getY() - 3, base.getZ() + 2);
		var fs = level.getBlockState(floor);
		helper.assertTrue(fs.is(Blocks.BLACKSTONE) || fs.is(Blocks.BASALT) || fs.is(Blocks.COAL_BLOCK) || fs.is(Blocks.POLISHED_BASALT)
				|| fs.is(Blocks.MAGMA_BLOCK) || fs.is(Blocks.CRACKED_DEEPSLATE_TILES), "a scorched core floor, got " + fs);

		MjolnirEntity m = CraterAmbience.placeHammerForTest(level, spot);
		helper.assertTrue(m.getState() == MjolnirEntity.State.RESTING && m.blockPosition().equals(spot), "the hammer rests on the plinth");
		helper.assertTrue(CraterGuard.isProtected(level, spot.below()), "the plinth is protected while the hammer is in it");
		helper.assertTrue(CraterGuard.isProtected(level, floor), "and the bowl");
		helper.assertFalse(CraterGuard.isProtected(level, spot.offset(20, 0, 0)), "but not the land around it");
		m.discard();
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(CraterGuard.isProtected(level, spot.below()), "once the hammer is gone it is ordinary ground");
			for (BlockPos q : BlockPos.betweenClosed(base.offset(-14, -8, -14), base.offset(14, 14, 14))) {
				level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
			}
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the Star Forge

	private static BlockPos forge(GameTestHelper helper, boolean ring) {
		BlockPos f = new BlockPos(4, 2, 4);
		helper.setBlock(f, ThorAcquire.STAR_FORGE);
		if (ring) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dz != 0) {
						helper.setBlock(f.offset(dx, 0, dz), dx == dz ? Blocks.GILDED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS);
					}
				}
			}
		}
		return helper.absolutePos(f);
	}

	private static StarForgeBlockEntity be(GameTestHelper helper, BlockPos abs) {
		return (StarForgeBlockEntity) helper.getLevel().getBlockEntity(abs);
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF)
	public void theForgeNeedsItsBlackstoneRing(GameTestHelper helper) {
		BlockPos f = forge(helper, false);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(StarForge.missingRing(level, f).size() == 8, "no ring yet: 8 missing");
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if ((dx != 0 || dz != 0) && !(dx == 1 && dz == 1)) {
					level.setBlockAndUpdate(f.offset(dx, 0, dz), Blocks.BLACKSTONE.defaultBlockState());
				}
			}
		}
		helper.assertTrue(StarForge.missingRing(level, f).size() == 1 && !StarForge.ringComplete(level, f), "one gap");
		ServerPlayer p = player(helper, new Vec3(1.5, 2, 1.5));
		StarForge.use(level, f, be(helper, f), p, new ItemStack(ModItems.UNFORGED_STORMBREAKER));
		helper.assertTrue(be(helper, f).head().isEmpty(), "an incomplete forge takes nothing");
		level.setBlockAndUpdate(f.offset(1, 0, 1), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
		helper.assertTrue(StarForge.ringComplete(level, f), "any blackstone completes it");
		level.setBlockAndUpdate(f.offset(1, 0, 1), Blocks.STONE.defaultBlockState());
		helper.assertFalse(StarForge.ringComplete(level, f), "plain stone does not");
		leave(helper, p);
		helper.succeed();
	}

	private static ServerPlayer thor(GameTestHelper helper) {
		ServerPlayer p = player(helper, new Vec3(4.5, 2, 2.0));
		Worthiness.setScore(p, Worthiness.TEST_WORTHY_SCORE);
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));
		p.getAbilities().invulnerable = true; // a mock player has no Thor health; the burn itself is not what this checks
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF, timeoutTicks = 300)
	public void channellingTenSecondsForgesTheHead(GameTestHelper helper) {
		BlockPos f = forge(helper, true);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = thor(helper);
		StarForge.use(level, f, be(helper, f), p, new ItemStack(ModItems.UNFORGED_STORMBREAKER));
		helper.assertTrue(be(helper, f).head().is(ModItems.UNFORGED_STORMBREAKER), "the head is set in");
		helper.assertTrue(StarForge.ping(level, f, be(helper, f), p), "a worthy Thor with Mjolnir starts channelling");
		int[] t = { 0 };
		helper.onEachTick(() -> {
			if (++t[0] % 4 == 0 && !StarForge.isForged(be(helper, f).head())) {
				StarForge.ping(level, f, be(helper, f), p);
			}
		});
		helper.runAfterDelay(StarForge.CHANNEL_TICKS - 20, () ->
				helper.assertFalse(StarForge.isForged(be(helper, f).head()), "not forged before 10 s"));
		helper.runAfterDelay(StarForge.CHANNEL_TICKS + 6, () -> {
			helper.assertTrue(StarForge.isForged(be(helper, f).head()), "forged after 10 s of channelling");
			helper.assertFalse(be(helper, f).channelling(), "the channel is done");
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF, timeoutTicks = 200)
	public void lettingGoBreaksTheChannel(GameTestHelper helper) {
		BlockPos f = forge(helper, true);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = thor(helper);
		ServerPlayer unworthy = player(helper, new Vec3(3.5, 2, 2.0));
		unworthy.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		StarForge.insertForTest(be(helper, f), new ItemStack(ModItems.UNFORGED_STORMBREAKER));
		helper.assertFalse(StarForge.ping(level, f, be(helper, f), unworthy), "the unworthy can't channel");
		StarForge.ping(level, f, be(helper, f), p);
		int[] t = { 0 };
		helper.onEachTick(() -> {
			if (++t[0] < 100 && t[0] % 4 == 0) {
				StarForge.ping(level, f, be(helper, f), p);
			}
		});
		helper.runAfterDelay(90, () -> helper.assertTrue(be(helper, f).channel() > 60, "channelling, at " + be(helper, f).channel()));
		helper.runAfterDelay(100 + StarForge.PING_GRACE + 4, () -> {
			helper.assertFalse(be(helper, f).channelling(), "the channel broke when the button was let go");
			helper.assertTrue(be(helper, f).channel() == 0, "and the progress reset");
			helper.assertFalse(StarForge.isForged(be(helper, f).head()), "the head cooled unforged");
			leave(helper, p, unworthy);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF, timeoutTicks = 60)
	public void theLivingBranchFinishesStormbreaker(GameTestHelper helper) {
		BlockPos f = forge(helper, true);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = player(helper, new Vec3(4.5, 2, 2.0));
		StarForge.insertForTest(be(helper, f), new ItemStack(ModItems.UNFORGED_STORMBREAKER));
		ItemStack branch = new ItemStack(ThorAcquire.LIVING_BRANCH);
		StarForge.use(level, f, be(helper, f), p, branch);
		helper.assertTrue(branch.getCount() == 1 && be(helper, f).finish() == 0, "an unforged head refuses the branch");
		StarForge.insertForTest(be(helper, f), StarForge.forgedHead());
		StarForge.use(level, f, be(helper, f), p, branch);
		helper.assertTrue(branch.isEmpty() && be(helper, f).finish() > 0, "a forged head takes the branch and finishing starts");
		StarForge.finishNowForTest(level, f, be(helper, f));
		helper.assertTrue(be(helper, f).head().isEmpty() && be(helper, f).finish() == 0, "the forge is empty again");
		helper.runAfterDelay(2, () -> {
			AABB box = new AABB(f).inflate(4);
			boolean item = !level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(ModItems.STORMBREAKER)).isEmpty();
			boolean axe = !level.getEntitiesOfClass(StormbreakerEntity.class, box).isEmpty();
			helper.assertTrue(item || axe, "Stormbreaker rose out of the forge");
			for (var e : level.getEntitiesOfClass(StormbreakerEntity.class, box)) {
				e.discard();
			}
			leave(helper, p);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF)
	public void oldUnforgedStormbreakersAreHeads(GameTestHelper helper) {
		BlockPos f = forge(helper, true);
		ServerLevel level = helper.getLevel();
		ServerPlayer p = player(helper, new Vec3(4.5, 2, 2.0));
		// an old save's item: the same id, no data at all
		ItemStack old = new ItemStack(ModItems.UNFORGED_STORMBREAKER);
		helper.assertFalse(StarForge.isForged(old), "an old Unforged Stormbreaker is an unforged head");
		StarForge.use(level, f, be(helper, f), p, old);
		helper.assertTrue(old.isEmpty() && be(helper, f).head().is(ModItems.UNFORGED_STORMBREAKER), "it goes straight into the forge");
		var holder = level.getRecipeManager().byKey(com.projecthero.mod.ProjectHeroMod.id("unforged_stormbreaker"));
		helper.assertTrue(holder.isPresent() && holder.get().value().getResultItem(level.registryAccess()).is(ModItems.UNFORGED_STORMBREAKER),
				"the head recipe still makes the same item");
		leave(helper, p);
		helper.succeed();
	}

	// ---------------------------------------------------------------- the Living Branch

	@GameTest(template = EMPTY_STRUCTURE, batch = STORM_OFF, timeoutTicks = 40)
	public void lightningOnAJungleTreeDropsALivingBranch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(new BlockPos(2, y, 2), Blocks.JUNGLE_LOG);
		}
		helper.setBlock(new BlockPos(2, 6, 2), Blocks.JUNGLE_LEAVES);
		helper.setBlock(new BlockPos(6, 2, 6), Blocks.OAK_LOG);
		LivingBranch.chanceOverride = 0f;
		helper.assertTrue(LivingBranch.onStrike(level, helper.absolutePos(new BlockPos(2, 7, 2))) == null, "chance 0: nothing");
		LivingBranch.chanceOverride = 1f;
		helper.assertTrue(LivingBranch.onStrike(level, helper.absolutePos(new BlockPos(6, 3, 6))) == null, "an oak is not a jungle tree");
		ItemEntity drop = LivingBranch.onStrike(level, helper.absolutePos(new BlockPos(2, 7, 2)));
		helper.assertTrue(drop != null && drop.getItem().is(ThorAcquire.LIVING_BRANCH), "a struck jungle tree gives a Living Branch");
		drop.discard();
		// and through a real (visual-only, like Thor's) lightning bolt
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 7, 2.5));
		bolt.moveTo(at.x, at.y, at.z);
		bolt.setVisualOnly(true);
		level.addFreshEntity(bolt);
		helper.runAfterDelay(3, () -> {
			LivingBranch.chanceOverride = null;
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(new BlockPos(2, 4, 2))).inflate(5),
					e -> e.getItem().is(ThorAcquire.LIVING_BRANCH));
			helper.assertTrue(!drops.isEmpty(), "a lightning bolt on the tree dropped one");
			drops.forEach(ItemEntity::discard);
			helper.succeed();
		});
	}
}
