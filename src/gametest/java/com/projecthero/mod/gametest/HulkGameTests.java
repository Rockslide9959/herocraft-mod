package com.projecthero.mod.gametest;

import com.mojang.serialization.JsonOps;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkDamage;
import com.projecthero.mod.hulk.data.HulkState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side coverage for the Hulk, Phase 1 (v0.13.11): the grant, rage from damage, the 75-rage manual change
 * and the forced change at 100, the stats going on and coming off cleanly, the exhausted reversion at 0, the
 * fists-only rule, respawn and persistence. Mock players are not reliably ticked by the server, so each test
 * drives {@link Hulk#tick} itself.
 */
public class HulkGameTests implements FabricGameTest {
	private static ServerPlayer gamma(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		// the Hulk grows to 3.2 blocks: open a room so the growth is never blocked by the test's barrier walls
		var base = net.minecraft.core.BlockPos.containing(at);
		for (var pos : net.minecraft.core.BlockPos.betweenClosed(base.offset(-2, 0, -2), base.offset(2, 5, 2))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		helper.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		return p;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void grantGivesTheGammaPowerAsAHeroTierPower(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		helper.assertTrue(Hulk.hasPower(p), "has the Gamma power");
		helper.assertFalse(Hulk.isHulk(p), "but starts as Banner");
		helper.assertTrue(Hulk.rage(p) == 0.0f, "with no rage");
		helper.assertTrue(HeroTiers.holdsHero(p, Hulk.KEY), "it is a Hero-Tier power");
		helper.assertFalse(Hulk.grant(p), "granting twice does nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void takingDamageBuildsRage(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		// v0.13.17: Banner gets nothing for the damage he deals; every point he takes is 1%
		Hulk.onDealt(p, 5.0f);
		helper.assertTrue(Hulk.rage(p) == 0.0f, "Banner's own hits build no rage, got " + Hulk.rage(p));
		// player.hurt() on a mock player never reaches the damage events (see HeroPackGameTests): drive the hook directly
		Hulk.onHurt(p, 5.0f);
		helper.assertTrue(Math.abs(Hulk.rage(p) - 5.0f) < 0.01f, "5 damage = 5% rage, got " + Hulk.rage(p));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyHulkHitAddsTwoRage(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 50.0f);
		Hulk.onDealt(p, 20.0f);
		Hulk.onDealt(p, 3.0f);
		helper.assertTrue(Math.abs(Hulk.rage(p) - 54.0f) < 0.01f, "two hits = +4, whatever they did, got " + Hulk.rage(p));
		Hulk.onHurt(p, 6.0f);
		helper.assertTrue(Math.abs(Hulk.rage(p) - 60.0f) < 0.01f, "and 6 damage taken = +6, got " + Hulk.rage(p));
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void theHulkOnlyBurnsRageOutOfCombat(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var n = Hulk.state(p).copy();
		n.formChangedAt = -10_000L;
		n.rage = 50.0f;
		n.lastCombatAt = helper.getLevel().getGameTime();
		p.setAttached(ModAttachments.HULK_STATE, n);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(Hulk.rage(p) >= 50.0f - 1.0e-3f, "in a fight (hit 2 s ago) he keeps it all, got " + Hulk.rage(p));
			var z = Hulk.state(p).copy();
			z.lastCombatAt = -10_000L;
			p.setAttached(ModAttachments.HULK_STATE, z);
		});
		helper.runAfterDelay(85, () -> {
			float r = Hulk.rage(p);
			helper.assertTrue(r < 50.0f && r > 40.0f, "out of combat it burns slowly (0.75/s), got " + r);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void bannerCoolsOffFiveSecondsAfterTheLastHit(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		var n = Hulk.state(p).copy();
		n.rage = 50.0f;
		n.combat.lastHurtAt = helper.getLevel().getGameTime();
		n.lastCombatAt = n.combat.lastHurtAt;
		p.setAttached(ModAttachments.HULK_STATE, n);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(Hulk.rage(p) >= 50.0f - 1.0e-3f, "hurt 2 s ago: no cooling yet, got " + Hulk.rage(p));
			Hulk.onDealt(p, 10.0f); // his own hits don't keep him angry...
			var z = Hulk.state(p).copy();
			z.combat.lastHurtAt = -10_000L; // ...only getting hurt does
			p.setAttached(ModAttachments.HULK_STATE, z);
		});
		helper.runAfterDelay(85, () -> {
			float r = Hulk.rage(p);
			helper.assertTrue(r <= 48.0f && r >= 30.0f, "5 s unhurt: 2/s off, got " + r);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHulkCarriesASquadMate(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		ServerPlayer mate = helper.makeMockServerPlayerInLevel();
		mate.setGameMode(GameType.SURVIVAL);
		mate.moveTo(p.getX(), p.getY(), p.getZ() + 2.0, 180.0f, 0.0f);
		var squads = com.projecthero.mod.squad.SquadManager.get(helper.getLevel().getServer());
		var squad = squads.create("HulkTestSquad" + helper.getLevel().getGameTime(), p.getUUID());
		squads.addMember(squad, mate.getUUID());
		try {
			p.moveTo(p.getX(), p.getY(), p.getZ(), 0.0f, 0.0f);
			com.projecthero.mod.hulk.HulkGrab.press(p, false);
			helper.assertTrue(com.projecthero.mod.hulk.HulkGrab.holding(p), "a squad-mate can be picked up");
			com.projecthero.mod.hulk.HulkGrab.press(p, true);
			helper.assertFalse(com.projecthero.mod.hulk.HulkGrab.holding(p), "Shift+V sets them down");
			helper.assertTrue(mate.isAlive() && mate.getHealth() >= mate.getMaxHealth() - 0.01f, "unhurt -- never crushed");
			helper.assertFalse(HulkDamage.allowDamage(mate, mate.damageSources().fall(), 6.0f), "and the landing doesn't hurt");
		} finally {
			squads.disband(squad);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void websAreSoftAndTheRampageStaysOnTheSurface(GameTestHelper helper) {
		var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
		helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 2);
		helper.assertTrue(com.projecthero.mod.hulk.HulkCombat.breakable(helper.getLevel(), pos, helper.getLevel().getBlockState(pos)),
				"cobweb is a soft block to the Hulk");
		helper.assertTrue(com.projecthero.mod.hulk.HulkControl.RAMPAGE_RANGE >= 100.0, "rampage hunts out to 100 blocks");
		var pig = net.minecraft.world.entity.EntityType.PIG.create(helper.getLevel());
		int surface = helper.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
		pig.moveTo(pos.getX() + 0.5, surface, pos.getZ() + 0.5);
		helper.assertTrue(com.projecthero.mod.hulk.HulkControl.aboveGround(pig), "a pig on the surface is fair game");
		pig.moveTo(pos.getX() + 0.5, surface - 12.0, pos.getZ() + 0.5);
		helper.assertFalse(com.projecthero.mod.hulk.HulkControl.aboveGround(pig), "one down in a cave is not");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theGuidebookIsJustTheGuidebook(GameTestHelper helper) {
		var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(com.projecthero.mod.hero.item.HeroPackItems.GUIDE);
		helper.assertTrue("projecthero:guidebook".equals(key.toString()), "/give id is projecthero:guidebook, got " + key);
		helper.assertTrue(com.projecthero.mod.oathbreaker.OathbreakerTuning.PHASE_3_DAMAGE_MULTIPLIER
				> com.projecthero.mod.oathbreaker.OathbreakerTuning.PHASE_2_DAMAGE_MULTIPLIER
				&& com.projecthero.mod.oathbreaker.OathbreakerTuning.PHASE_2_DAMAGE_MULTIPLIER > 1.0f, "the Oathbreaker hits harder each phase");
		helper.assertTrue(HulkConfig.abilities().thunderclapRange >= 25.0, "Thunderclap reaches 25 blocks");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void manualChangeNeedsSeventyFiveRage(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.setRage(p, 50.0f);
		Hulk.tryTransform(p);
		helper.assertFalse(Hulk.isHulk(p), "50 rage is not enough");
		Hulk.setRage(p, 80.0f);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p), "80 rage lets the Hulk out");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 180)
	public void fullRageForcesTheChangeAndTheStatsGoOn(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.setRage(p, HulkConfig.RAGE_MAX);
		Hulk.tick(p);
		helper.assertTrue(Hulk.isHulk(p), "100 rage forces the change");
		helper.assertTrue(Math.abs(p.getMaxHealth() - (20.0f + HulkConfig.HEALTH_BONUS)) < 0.01f, "max health 60, got " + p.getMaxHealth());
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) >= 1.0 + HulkConfig.ATTACK_BONUS - 0.01, "attack bonus on");
		helper.assertTrue(p.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= HulkConfig.KNOCKBACK_RESISTANCE - 0.01, "knockback resistance on");
		// reconcile is idempotent: running it again must not stack anything
		Hulk.reconcile(p);
		Hulk.reconcile(p);
		helper.assertTrue(Math.abs(p.getMaxHealth() - (20.0f + HulkConfig.HEALTH_BONUS)) < 0.01f, "no stacking");
		// v0.13.15: the forced change is the unwilling one -- on his knees, pinned, untouchable, growing slowly
		helper.assertTrue(Hulk.state(p).combat.unwilling, "100 rage is the unwilling change");
		helper.assertTrue(Hulk.changing(p), "and he is changing");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(HulkConfig.FORCED_KNEEL_TICKS - 4, () -> {
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.SCALE) - 1.0) < 0.01, "nothing grows while he drops to his knees");
			helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1.0e-6, "pinned in place, got " + p.getAttributeValue(Attributes.MOVEMENT_SPEED));
			helper.assertFalse(HulkDamage.allowDamage(p, p.damageSources().generic(), 4.0f), "nothing hurts him mid-change");
		});
		helper.runAfterDelay(HulkConfig.FORCED_CHANGE_TICKS + 10, () -> {
			helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.SCALE) - (1.0 + HulkConfig.SCALE_BONUS)) < 0.01,
					"grown to 1.8x, got " + p.getAttributeValue(Attributes.SCALE));
			helper.assertFalse(Hulk.changing(p), "the change is over");
			helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.1, "and he can move again");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void theWillingHulkNeverFightsForControl(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		helper.assertFalse(Hulk.state(p).combat.unwilling, "H is the willing change");
		helper.assertFalse(Hulk.changing(p), "which is not the slow kneeling one");
		var n = Hulk.state(p).copy();
		n.formChangedAt = -10_000L;
		n.combat.lastDealtAt = -10_000L;
		p.setAttached(ModAttachments.HULK_STATE, n);
		helper.onEachTick(() -> {
			Hulk.setRage(p, 100.0f);
			Hulk.tick(p);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(Hulk.state(p).combat.control >= 100.0f, "control never slips, got " + Hulk.state(p).combat.control);
			helper.assertTrue(Hulk.state(p).combat.promptKey == 0, "no keep-control prompts");
			helper.assertFalse(com.projecthero.mod.hulk.HulkControl.rampaging(p), "and no rampage");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHulkPhasesOnAndOff(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		helper.assertTrue(Hulk.visibility(p, 0.0f) == 0.0f, "a fresh Banner shows no Hulk");
		Hulk.setRage(p, 90.0f);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.visibility(p, 0.0f) < 0.05f, "the Hulk starts to phase on as he grows");
		var n = Hulk.state(p).copy();
		n.formChangedAt -= HulkConfig.GROWTH_TICKS / 2;
		p.setAttached(ModAttachments.HULK_STATE, n);
		float mid = Hulk.visibility(p, 0.0f);
		helper.assertTrue(mid > 0.3f && mid < 0.7f, "half way through the growth he is half there, got " + mid);
		n = Hulk.state(p).copy();
		n.formChangedAt -= HulkConfig.GROWTH_TICKS;
		p.setAttached(ModAttachments.HULK_STATE, n);
		helper.assertTrue(Hulk.visibility(p, 0.0f) > 0.999f, "then fully the Hulk");
		Hulk.revert(p, false);
		helper.assertTrue(Hulk.visibility(p, 0.0f) > 0.95f, "shrinking back starts from the Hulk");
		n = Hulk.state(p).copy();
		n.formChangedAt -= HulkConfig.GROWTH_TICKS;
		p.setAttached(ModAttachments.HULK_STATE, n);
		helper.assertTrue(Hulk.visibility(p, 0.0f) < 0.001f, "and phases off him completely");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBondingMarkerMovesSmoothly(GameTestHelper helper) {
		int seed = 0x5EED1234;
		for (int round = 0; round < com.projecthero.mod.symbiote.SymbioteBondGame.ROUNDS; round++) {
			double a = com.projecthero.mod.symbiote.SymbioteBondGame.marker(seed, round, 10.0);
			double b = com.projecthero.mod.symbiote.SymbioteBondGame.marker(seed, round, 10.5);
			double c = com.projecthero.mod.symbiote.SymbioteBondGame.marker(seed, round, 11.0);
			// between whole ticks the marker is somewhere in between -- not stuck on the last tick
			helper.assertTrue(Math.abs(b - (a + c) / 2.0) < 0.02, "sub-tick position interpolates, round " + round);
			helper.assertTrue(Math.abs(b - a) > 1.0e-4, "and actually moves, round " + round);
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void zeroRageRevertsExhaustedAndClearsTheStats(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.setRage(p, 90.0f);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p), "precondition: Hulk");
		Hulk.setRage(p, 0.0f);
		Hulk.tick(p);
		helper.assertFalse(Hulk.isHulk(p), "0 rage shrinks him back");
		helper.assertTrue(p.hasEffect(MobEffects.WEAKNESS) && p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "exhausted: Weakness + Slowness");
		helper.assertTrue(Hulk.exhausted(p), "and marked exhausted");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 0.01f, "max health back to 20");
		// (Weakness itself lowers attack, so only check the Hulk bonus is gone)
		helper.assertTrue(p.getAttributeValue(Attributes.ATTACK_DAMAGE) <= 1.0 + 0.01, "attack bonus gone");
		// an exhausted Banner builds no rage and cannot change
		Hulk.onHurt(p, 2.0f);
		helper.assertTrue(Hulk.rage(p) == 0.0f, "no rage while exhausted");
		Hulk.setRage(p, 100.0f);
		Hulk.tick(p);
		helper.assertFalse(Hulk.isHulk(p), "and no forced change while exhausted");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHulkUsesHisFists(GameTestHelper helper) {
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.DIAMOND_SWORD)), "no swords");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.IRON_PICKAXE)), "no tools");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.BOW)), "no bows");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.CROSSBOW)), "no crossbows");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(Items.TRIDENT)), "no tridents");
		helper.assertTrue(HulkDamage.isForbidden(new ItemStack(com.projecthero.mod.firearm.item.FirearmItems.PUNISHER_PISTOL)), "no guns");
		helper.assertFalse(HulkDamage.isForbidden(new ItemStack(Items.BREAD)), "food is fine");
		helper.assertFalse(HulkDamage.isForbidden(ItemStack.EMPTY), "bare hands are the point");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void respawnAndRevokeLeaveNothingBehind(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.setRage(p, 100.0f);
		Hulk.tick(p);
		Hulk.onPlayerRespawn(p);
		helper.assertFalse(Hulk.isHulk(p), "a respawn is a calm Banner");
		helper.assertTrue(Hulk.rage(p) == 0.0f, "with no rage");
		helper.assertTrue(Hulk.hasPower(p), "but keeps the Gamma power");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 0.01f, "stats cleared");

		Hulk.setRage(p, 100.0f);
		Hulk.tick(p);
		Hulk.revoke(p);
		helper.assertFalse(Hulk.hasPower(p), "revoked");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.SCALE) - 1.0) < 0.001, "normal size at once");
		helper.assertTrue(Math.abs(p.getMaxHealth() - 20.0f) < 0.01f, "no health bonus left");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void stateSurvivesSaveAndLoad(GameTestHelper helper) {
		HulkState s = new HulkState(true, true, 42.5f, 10L, 20L, 30L);
		var json = HulkState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		HulkState back = HulkState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(back.hasPower && back.hulk && back.rage == 42.5f && back.exhaustedUntil == 20L, "round-trips");
		helper.assertTrue(ModAttachments.HULK_STATE != null, "the attachment is registered");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.13.12: Phases 2-5

	/** A Gamma player already out as the Hulk, standing in a cleared room facing +Z. */
	private static ServerPlayer hulk(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		var base = net.minecraft.core.BlockPos.containing(p.position());
		for (var pos : net.minecraft.core.BlockPos.betweenClosed(base.offset(-4, 0, -6), base.offset(4, 5, 8))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		// v0.13.15: the willing change (H) -- quick, abilities straight away; the unwilling one kneels for 5 s first
		Hulk.setRage(p, 100.0f);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p), "precondition: Hulk");
		return p;
	}

	/** A husk: a zombie that does not burn in daylight, so the only damage it can take is the ability under test. */
	private static net.minecraft.world.entity.monster.Husk zombie(GameTestHelper helper, Vec3 at) {
		var z = net.minecraft.world.entity.EntityType.HUSK.create(helper.getLevel());
		z.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		z.setNoAi(true);
		helper.getLevel().addFreshEntity(z);
		return z;
	}

	// own batch: no neighbouring test's shockwave can reach these mobs
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_thunderclap")
	public void thunderclapHitsWhatIsInFrontOnly(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var front = zombie(helper, p.position().add(0, 0, 4));
		var behind = zombie(helper, p.position().add(0, 0, -4));
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, front.getEyePosition());
		com.projecthero.mod.hulk.HulkAbilities.thunderclap(p);
		helper.assertTrue(com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(p, com.projecthero.mod.hulk.HulkAbilities.THUNDERCLAP) > 0,
				"Thunderclap goes on cooldown");
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(com.projecthero.mod.hulk.HulkAbilities.CLAP_IMPACT_TICKS + 4, () -> {
			helper.assertTrue(front.getHealth() < front.getMaxHealth(), "the zombie in front is hit");
			helper.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the one behind is not");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_ground_smash")
	public void groundSmashHitsAllRound(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var side = zombie(helper, p.position().add(3, 0, 0));
		var back = zombie(helper, p.position().add(0, 0, -3));
		com.projecthero.mod.hulk.HulkAbilities.groundSmash(p);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(com.projecthero.mod.hulk.HulkAbilities.SMASH_IMPACT_TICKS + 4, () -> {
			helper.assertTrue(side.getHealth() < side.getMaxHealth() && back.getHealth() < back.getMaxHealth(), "a ring all round him");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bannerCannotUseTheAbilities(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		com.projecthero.mod.hulk.HulkAbilities.groundSmash(p);
		helper.assertTrue(com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(p, com.projecthero.mod.hulk.HulkAbilities.GROUND_SMASH) == 0,
				"Banner's smash does nothing (no cooldown spent)");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_leap")
	public void superLeapChargesAndLaunches(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		p.setOnGround(true);
		com.projecthero.mod.hulk.HulkAbilities.beginLeap(p);
		helper.assertTrue(Hulk.state(p).leapChargeStart > 0L, "holding X charges");
		com.projecthero.mod.hulk.HulkAbilities.releaseLeap(p);
		helper.assertTrue(Hulk.state(p).leaping, "releasing launches him");
		helper.assertTrue(Hulk.state(p).leapChargeStart == 0L, "and ends the charge");
		helper.assertTrue(p.getDeltaMovement().length() > 0.3, "with real velocity");
		helper.assertTrue(com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(p, com.projecthero.mod.hulk.HulkAbilities.SUPER_LEAP) > 0,
				"and a cooldown");
		helper.succeed();
	}

	// ---------------------------------------------------------------- v0.13.14: the new kit and systems

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "hulk_power_punch")
	public void powerPunchHitsInFrontOnly(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var front = zombie(helper, p.position().add(0, 0, 4));
		var behind = zombie(helper, p.position().add(0, 0, -4));
		p.setYRot(0.0f);
		com.projecthero.mod.hulk.HulkAbilities.powerPunch(p);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(com.projecthero.mod.hulk.HulkAbilities.PUNCH_IMPACT_TICKS + 4, () -> {
			helper.assertTrue(front.getHealth() < front.getMaxHealth(), "the mob in front is punched");
			helper.assertTrue(behind.getHealth() == behind.getMaxHealth(), "the one behind is not");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_grab")
	public void grabThenCrush(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var mob = zombie(helper, p.position().add(0, 0, 3));
		p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, mob.position().add(0, mob.getBbHeight() * 0.5, 0));
		com.projecthero.mod.hulk.HulkGrab.press(p, false);
		helper.assertTrue(Hulk.state(p).combat.holding, "V picks the mob up");
		com.projecthero.mod.hulk.HulkGrab.press(p, true);
		helper.assertFalse(Hulk.state(p).combat.holding, "Shift+V crushes it and lets go");
		helper.assertTrue(mob.getHealth() < mob.getMaxHealth(), "the crush hurts");
		helper.assertTrue(com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(p, com.projecthero.mod.hulk.HulkAbilities.GRAB) > 0,
				"and starts the 8 s cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_grab")
	public void shiftVTearsUpAChunkOfEarth(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var floor = net.minecraft.core.BlockPos.containing(p.position()).below();
		for (var pos : net.minecraft.core.BlockPos.betweenClosed(floor.offset(-3, 0, -3), floor.offset(3, 0, 3))) {
			helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
		}
		com.projecthero.mod.hulk.HulkGrab.press(p, true);
		helper.assertTrue(Hulk.state(p).combat.holding, "he is holding the earth");
		boolean boulder = !helper.getLevel().getEntitiesOfClass(com.projecthero.mod.hulk.entity.HulkBoulderEntity.class,
				p.getBoundingBox().inflate(6.0)).isEmpty();
		helper.assertTrue(boulder, "a boulder entity is over his head");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "hulk_smash")
	public void hulkSmashNeedsTheFullHoldAndHitsHard(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var mob = zombie(helper, p.position().add(0, 0, 5));
		com.projecthero.mod.hulk.HulkAbilities.beginHulkSmash(p);
		helper.assertTrue(Hulk.state(p).combat.smashChargeStart > 0L, "Shift+Z starts the wind-up");
		com.projecthero.mod.hulk.HulkAbilities.releaseHulkSmash(p);
		helper.assertTrue(Hulk.state(p).combat.smashChargeStart == 0L, "letting go early calls it off");
		helper.assertTrue(com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(p, com.projecthero.mod.hulk.HulkAbilities.HULK_SMASH) == 0,
				"with no cooldown spent");
		com.projecthero.mod.hulk.HulkAbilities.beginHulkSmash(p);
		helper.onEachTick(() -> {
			Hulk.setRage(p, 100.0f);
			Hulk.tick(p);
		});
		helper.runAfterDelay(com.projecthero.mod.hulk.HulkConfig.abilities().hulkSmashChargeTicks + 20, () -> {
			helper.assertTrue(com.projecthero.mod.hulk.HulkAbilities.cooldownRemaining(p, com.projecthero.mod.hulk.HulkAbilities.HULK_SMASH) > 0,
					"a full hold fires it");
			helper.assertTrue(mob.isDeadOrDying() || mob.getHealth() < mob.getMaxHealth() * 0.5f, "and it hits hard");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void whatHurtsTheHulk(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var src = p.damageSources();
		helper.assertFalse(com.projecthero.mod.hulk.HulkDamage.allowDamage(p, src.fall(), 10.0f), "no fall damage");
		helper.assertFalse(com.projecthero.mod.hulk.HulkDamage.allowDamage(p, src.onFire(), 2.0f), "fire does nothing");
		helper.assertFalse(com.projecthero.mod.hulk.HulkDamage.allowDamage(p, src.inFire(), 2.0f), "nor standing in it");
		var arrow = new net.minecraft.world.entity.projectile.Arrow(helper.getLevel(), p.getX(), p.getY() + 3, p.getZ(),
				new ItemStack(Items.ARROW), null);
		helper.assertFalse(com.projecthero.mod.hulk.HulkDamage.allowDamage(p, src.arrow(arrow, null), 6.0f), "arrows bounce off");
		helper.assertFalse(com.projecthero.mod.hulk.HulkDamage.allowDamage(p, src.lava(), 4.0f), "lava is reduced (re-applied smaller)");
		helper.assertTrue(com.projecthero.mod.hulk.HulkDamage.allowDamage(p, src.generic(), 4.0f), "ordinary damage lands");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHulkRefusesToDie(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		p.getAbilities().invulnerable = false;
		helper.assertTrue(Hulk.deathSaveReady(p), "Banner is protected");
		helper.assertTrue(Hulk.tryDeathSave(p, p.damageSources().generic()), "a fatal hit is refused");
		helper.assertTrue(Hulk.isHulk(p), "and the Hulk comes out");
		helper.assertTrue(p.getHealth() >= p.getMaxHealth() - 0.01f, "at full health");
		// v0.13.17: no cooldown -- but the Hulk himself can be beaten
		helper.assertFalse(Hulk.deathSaveReady(p), "the Hulk is not protected");
		helper.assertFalse(Hulk.tryDeathSave(p, p.damageSources().generic()), "so killing the Hulk kills him");
		Hulk.revert(p, true);
		helper.assertTrue(Hulk.tryDeathSave(p, p.damageSources().generic()), "back as Banner, it saves him again at once -- no cooldown");
		Hulk.revert(p, false);
		helper.assertFalse(Hulk.tryDeathSave(p, p.damageSources().genericKill()), "and /kill always works");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
	public void controlSlipsIntoARampage(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var n = Hulk.state(p).copy();
		n.formChangedAt = -10_000L;
		n.combat.lastDealtAt = -10_000L;
		n.combat.control = 30.0f;
		n.combat.unwilling = true; // v0.13.15: only a Hulk who came out on his own fights for control
		p.setAttached(ModAttachments.HULK_STATE, n);
		helper.onEachTick(() -> {
			Hulk.setRage(p, 100.0f);
			Hulk.tick(p);
		});
		helper.runAfterDelay(5, () -> {
			int key = Hulk.state(p).combat.promptKey;
			helper.assertTrue(key > 0, "a keep-control prompt appears");
			float before = Hulk.state(p).combat.control;
			com.projecthero.mod.hulk.HulkControl.answer(p, key);
			helper.assertTrue(Hulk.state(p).combat.control > before, "the right key wins control back");
			var z = Hulk.state(p).copy();
			z.combat.control = 0.5f;
			z.combat.promptKey = 0;
			p.setAttached(ModAttachments.HULK_STATE, z);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(com.projecthero.mod.hulk.HulkControl.rampaging(p), "at 0 control he rampages");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void breathingCalmsHimDown(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 20.0f);
		var n = Hulk.state(p).copy();
		n.lastCombatAt = -10_000L;
		p.setAttached(ModAttachments.HULK_STATE, n);
		com.projecthero.mod.hulk.HulkCalm.start(p);
		helper.assertTrue(Hulk.state(p).combat.calming, "out of combat, N starts the calm-down");
		helper.runAfterDelay(22, () -> com.projecthero.mod.hulk.HulkCalm.report(p, 20, 0));
		helper.runAfterDelay(44, () -> com.projecthero.mod.hulk.HulkCalm.report(p, 20, 0));
		helper.runAfterDelay(50, () -> {
			helper.assertFalse(Hulk.isHulk(p), "breathing in rhythm drained the rage and he changed back");
			helper.assertFalse(Hulk.exhausted(p), "calmly -- no exhaustion");
			helper.assertFalse(Hulk.state(p).combat.calming, "the session is over");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void calmingNeedsToBeOutOfCombat(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.onHurt(p, 2.0f); // just got hit
		com.projecthero.mod.hulk.HulkCalm.start(p);
		helper.assertFalse(Hulk.state(p).combat.calming, "no calming down in the middle of a fight");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void armourTearsOffWhenHeChanges(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		Hulk.setRage(p, 100.0f);
		Hulk.tick(p);
		helper.assertTrue(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty(), "the chestplate comes off");
		var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, p.getBoundingBox().inflate(4.0));
		helper.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(Items.IRON_CHESTPLATE) && e.getItem().getDamageValue() == 50),
				"and lands on the ground 50 durability down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void hulkStatsV01314(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 20.0) < 0.01, "punches land 20");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR) >= 20.0 - 0.01, "diamond-level armour of his own");
		helper.assertTrue(p.getAttributeValue(Attributes.ARMOR_TOUGHNESS) >= 8.0 - 0.01, "and toughness");
		helper.assertTrue(p.getAttributeValue(Attributes.MOVEMENT_SPEED) > 0.1 * 1.4, "faster");
		helper.assertTrue(com.projecthero.mod.hulk.HulkBareHands.applies(p)
				&& com.projecthero.mod.hulk.HulkBareHands.correctToolForDrops(net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState()),
				"stone-tool hands: iron ore drops");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theHulkCannotLiftMjolnirAndIsNeverThor(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		p.getAbilities().instabuild = false;
		p.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 600, 0));
		helper.assertFalse(com.projecthero.mod.worthiness.Worthiness.canLift(p), "the Hulk cannot lift Mjolnir, even a Hero of the Village");
		helper.assertFalse(com.projecthero.mod.worthiness.Worthiness.wouldAscend(p), "and never becomes Thor by lifting it");

		// becoming the Hulk takes Thor away, and becoming Thor takes the Hulk away
		ServerPlayer thor = helper.makeMockServerPlayerInLevel();
		thor.setGameMode(GameType.SURVIVAL);
		com.projecthero.mod.worthiness.Worthiness.setScore(thor, com.projecthero.mod.worthiness.Worthiness.TEST_WORTHY_SCORE);
		helper.assertTrue(Hulk.grant(thor), "Gamma granted");
		helper.assertFalse(com.projecthero.mod.worthiness.Worthiness.isWorthy(thor), "no longer worthy of Mjolnir");
		HeroTiers.claimPrimary(thor, "thor");
		helper.assertFalse(Hulk.hasPower(thor), "claiming Thor removes the Gamma power");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
	public void aGammaReactorFeedsTheRage(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		var at = net.minecraft.core.BlockPos.containing(p.position()).offset(2, 0, 0);
		helper.getLevel().setBlock(at, com.projecthero.mod.hulk.item.HulkItems.GAMMA_REACTOR.defaultBlockState(), 2);
		helper.onEachTick(() -> Hulk.tick(p));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(Hulk.rage(p) > 0.0f, "standing by a reactor builds rage, got " + Hulk.rage(p));
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void drinkingTheGammaSerumDosesButDoesNotGrant(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getAbilities().instabuild = false;
		ItemStack serum = new ItemStack(com.projecthero.mod.hulk.item.HulkItems.GAMMA_SERUM);
		ItemStack left = serum.getItem().finishUsingItem(serum, helper.getLevel(), p);
		helper.assertFalse(Hulk.hasPower(p), "v0.13.21: the serum alone no longer gives the power");
		helper.assertTrue(com.projecthero.mod.hulk.GammaOverload.isDosed(p), "it doses the drinker");
		helper.assertTrue(left.is(Items.GLASS_BOTTLE), "and leaves an empty bottle");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void aDosedPlayerOverloadsTheReactor(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		var at = net.minecraft.core.BlockPos.containing(p.position()).offset(2, 0, 0);
		helper.getLevel().setBlock(at, com.projecthero.mod.hulk.item.HulkItems.GAMMA_REACTOR.defaultBlockState(), 2);
		com.projecthero.mod.hulk.GammaOverload.onReactorUsed(p, at);
		helper.assertFalse(com.projecthero.mod.hulk.GammaOverload.overloading(at), "no serum in the blood: nothing happens");
		com.projecthero.mod.hulk.GammaOverload.setDosed(p, true);
		com.projecthero.mod.hulk.GammaOverload.onReactorUsed(p, at);
		boolean started = com.projecthero.mod.hulk.GammaOverload.overloading(at);
		// never let it actually go off in the test world
		com.projecthero.mod.hulk.GammaOverload.cancel(at);
		helper.assertTrue(started, "dosed: the reactor goes critical");
		helper.assertFalse(com.projecthero.mod.hulk.GammaOverload.isDosed(p), "the dose is spent");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void relogComesBackAsBannerWithTheRageKept(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		Hulk.setRage(p, 60.0f);
		Hulk.onPlayerJoin(p);
		helper.assertFalse(Hulk.isHulk(p), "logging back in ends the Hulk");
		helper.assertTrue(Math.abs(Hulk.rage(p) - 60.0f) < 0.01f, "but keeps the rage");
		helper.assertTrue(Math.abs(p.getAttributeValue(Attributes.SCALE) - 1.0) < 0.001, "normal size");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theConfigHasSaneDefaults(GameTestHelper helper) {
		var a = com.projecthero.mod.hulk.HulkConfig.abilities();
		helper.assertTrue(a.thunderclapCooldownTicks > 0 && a.groundSmashCooldownTicks > 0 && a.leapCooldownTicks > 0, "cooldowns");
		helper.assertTrue(a.leapMaxBlocks > a.leapMinBlocks, "leap range");
		helper.assertTrue(com.projecthero.mod.hulk.HulkConfig.world().maxBreakableHardness < 50.0f, "obsidian is never sprint-smashed");
		helper.succeed();
	}
}
