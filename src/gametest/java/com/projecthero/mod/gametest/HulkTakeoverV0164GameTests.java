package com.projecthero.mod.gametest;

import com.mojang.serialization.JsonOps;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.Hulk;
import com.projecthero.mod.hulk.HulkConfig;
import com.projecthero.mod.hulk.HulkControl;
import com.projecthero.mod.hulk.HulkTakeover;
import com.projecthero.mod.hulk.data.HulkState;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.4 (user): the rampage AI's aim and moves, the full takeover (3 lost warnings in a row: 5 minutes, a cave, then he
 * wakes as Banner), Banner's own fight with the Hulk (10 minutes, then every 2; lost = a 3-minute forced rampage), the death
 * change's rampage, the hard ends, the watchdog and the relog. Time is simulated by moving the stored times; mock players
 * are not reliably ticked by the server, so each test drives {@link Hulk#tick} itself.
 */
public class HulkTakeoverV0164GameTests implements FabricGameTest {
	private static ServerPlayer gamma(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		p.getAbilities().invulnerable = false;
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-8, 0, -8), base.offset(8, 8, 8))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		helper.assertTrue(Hulk.grant(p), "the Gamma power is granted");
		return p;
	}

	private static ServerPlayer hulk(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		Hulk.tryTransform(p);
		helper.assertTrue(Hulk.isHulk(p) && !Hulk.changing(p), "precondition: the Hulk, ready");
		return p;
	}

	private static void edit(ServerPlayer p, java.util.function.Consumer<HulkState> change) {
		HulkState n = Hulk.state(p).copy();
		change.accept(n);
		p.setAttached(ModAttachments.HULK_STATE, n);
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	private static <T extends LivingEntity> T mob(GameTestHelper helper, EntityType<T> type, Vec3 at) {
		T e = type.create(helper.getLevel());
		e.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		if (e instanceof net.minecraft.world.entity.Mob m) {
			m.setNoAi(true);
		}
		helper.getLevel().addFreshEntity(e);
		return e;
	}

	/** The Hulk-form warning comes up and runs out. */
	private static void loseWarning(GameTestHelper helper, ServerPlayer p) {
		edit(p, n -> n.rules.nextRampageAt = now(p));
		Hulk.tick(p);
		helper.assertTrue(HulkControl.warning(Hulk.state(p), now(p)), "the warning is up");
		edit(p, n -> n.rules.warnUntil = now(p) - 1);
		Hulk.tick(p);
		helper.assertTrue(HulkControl.rampaging(p), "lost: he rampages");
	}

	private static void typeWarning(ServerPlayer p) {
		HulkState s = Hulk.state(p);
		for (int i = 0; i < s.rules.warnLen; i++) {
			HulkControl.answer(p, HulkControl.arrowAt(s.rules.warnSeq, i));
		}
	}

	/** The rampage's hard end comes (simulated): the next tick ends it. */
	private static void runOut(ServerPlayer p) {
		edit(p, n -> n.combat.rampageUntil = now(p));
		Hulk.tick(p);
	}

	// ---------------------------------------------------------------- the AI

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_punch")
	public void aRampagePunchLandsOnAPigBesideHim(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		LivingEntity pig = mob(helper, EntityType.PIG, p.position().add(0.0, 0.0, 2.2));
		HulkControl.startRampage(p);
		p.setYRot(180.0f); // looking the other way: the aim must not depend on where the player looked
		float before = pig.getHealth();
		String check = HulkControl.testPunchCheck(p, pig);
		helper.assertTrue("ok".equals(check), "in reach, nothing in the way: " + check);
		// (this is the bug the user saw: Fabric's attack callback inside player.attack used to refuse every rampage punch)
		helper.assertTrue(HulkControl.testPunch(p, pig), "the aimed punch lands");
		// the player's own clicks are still refused while he rampages
		net.minecraft.world.InteractionResult click = net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker()
				.interact(p, p.level(), net.minecraft.world.InteractionHand.MAIN_HAND, pig, null);
		helper.assertTrue(click == net.minecraft.world.InteractionResult.FAIL, "the player's own click is refused, got " + click);
		helper.assertTrue(!pig.isAlive() || pig.getHealth() < before, "and hurts the pig");
		pig.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "hulk_takeover_ai")
	public void theRampagingHulkPoundsWhatIsInReach(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		LivingEntity cow = mob(helper, EntityType.COW, p.position().add(1.8, 0.0, 0.0));
		HulkControl.startRampage(p);
		edit(p, n -> n.wild.startedAt = now(p) - 200L); // past the opening roar
		float before = cow.getHealth();
		helper.onEachTick(() -> {
			p.setOnGround(true);
			Hulk.tick(p);
		});
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(!cow.isAlive() || cow.getHealth() < before, "the cow got hit");
			helper.assertTrue(HulkControl.punchesLanded(p) > 0 || HulkControl.lastMove(p) != null, "by a punch or a move");
			cow.discard();
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 160, batch = "hulk_takeover_moves")
	public void theRampagingHulkUsesHisMoves(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		var golem = mob(helper, EntityType.IRON_GOLEM, p.position().add(0.0, 0.0, 6.0));
		HulkControl.startRampage(p);
		edit(p, n -> n.wild.startedAt = now(p) - 200L);
		helper.onEachTick(() -> {
			p.setOnGround(true);
			Hulk.tick(p);
		});
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(HulkControl.lastMove(p) != null, "within 5 s he used a move");
			golem.discard();
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- the takeover

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_streak")
	public void threeLostWarningsInARowAndTheHulkTakesOver(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		loseWarning(helper, p);
		helper.assertTrue(Hulk.state(p).wild.failStreak == 1 && Hulk.state(p).wild.kind == HulkState.RAMPAGE_NORMAL, "one lost: a normal rampage");
		runOut(p);
		loseWarning(helper, p);
		helper.assertTrue(Hulk.state(p).wild.failStreak == 2, "two lost in a row");
		runOut(p);
		// a passed warning breaks the streak
		edit(p, n -> n.rules.nextRampageAt = now(p));
		Hulk.tick(p);
		typeWarning(p);
		helper.assertFalse(HulkControl.rampaging(p), "typed in time");
		helper.assertTrue(Hulk.state(p).wild.failStreak == 0, "a pass resets the count");
		loseWarning(helper, p);
		runOut(p);
		loseWarning(helper, p);
		runOut(p);
		helper.assertTrue(Hulk.state(p).wild.kind == HulkState.RAMPAGE_NORMAL, "two more: still normal rampages");
		loseWarning(helper, p);
		HulkState s = Hulk.state(p);
		helper.assertTrue(s.wild.kind == HulkState.RAMPAGE_TAKEOVER, "the third in a row: the takeover");
		helper.assertTrue(s.combat.rampageUntil - now(p) == HulkConfig.control().takeoverTicks, "5 minutes long");
		helper.assertTrue(s.noRing(now(p)), "with no ring");
		HulkControl.ringResult(p, true);
		helper.assertTrue(Hulk.state(p).rules.ringStreak == 0, "Space does nothing");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_end")
	public void theTakeoverEndsOnTimeWithBannerAwake(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		edit(p, n -> n.wild.failStreak = 3);
		HulkControl.startRampage(p, HulkState.RAMPAGE_TAKEOVER);
		long end = Hulk.state(p).combat.rampageUntil;
		helper.assertTrue(end - now(p) == 5L * 60L * 20L, "5 minutes, got " + (end - now(p)));
		// a few minutes in (the phases follow the clock), then the end
		edit(p, n -> n.wild.startedAt = now(p) - 4L * 60L * 20L);
		Hulk.tick(p);
		helper.assertTrue(HulkControl.rampaging(p) && Hulk.state(p).combat.rampageUntil == end, "still running, the end never moves");
		Hulk.pressH(p);
		helper.assertTrue(Hulk.isHulk(p), "H does nothing");
		runOut(p);
		helper.assertFalse(Hulk.isHulk(p), "the end: the player wakes up as Banner");
		HulkState s = Hulk.state(p);
		helper.assertFalse(s.rampaging(now(p)), "nothing running");
		helper.assertTrue(s.wild.failStreak == 0, "the streak starts over");
		helper.assertTrue(Hulk.holdCooldown(p) == 0, "and no hold-H cooldown");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_hardend")
	public void everyRampageHasAHardEnd(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		HulkControl.startRampage(p, HulkState.RAMPAGE_NORMAL);
		runOut(p);
		helper.assertTrue(Hulk.isHulk(p) && !HulkControl.rampaging(p) && Hulk.state(p).combat.control >= 100.0f, "normal: control is back");
		HulkControl.startRampage(p, HulkState.RAMPAGE_FORCED);
		helper.assertTrue(Hulk.state(p).combat.rampageUntil - now(p) == 3L * 60L * 20L, "forced: 3 minutes");
		runOut(p);
		helper.assertTrue(Hulk.isHulk(p) && !HulkControl.rampaging(p), "forced: the player has the Hulk back");
		// the watchdog: a rampage left 10 s past its end (the tick never got to it) is force-released
		HulkControl.startRampage(p, HulkState.RAMPAGE_NORMAL);
		edit(p, n -> n.combat.rampageUntil = now(p) - HulkControl.WATCHDOG_SLACK_TICKS - 5L);
		HulkControl.watchdog(p);
		helper.assertTrue(Hulk.state(p).combat.rampageUntil == 0L && Hulk.isHulk(p), "the watchdog lets go");
		HulkControl.startRampage(p, HulkState.RAMPAGE_TAKEOVER);
		edit(p, n -> n.combat.rampageUntil = now(p) - HulkControl.WATCHDOG_SLACK_TICKS - 5L);
		HulkControl.watchdog(p);
		helper.assertFalse(Hulk.isHulk(p), "an overdue takeover ends as Banner");
		helper.assertTrue(Hulk.state(p).combat.rampageUntil == 0L, "and clean");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_relog")
	public void aRelogResumesTheTakeoverOrCleansUpAnExpiredOne(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		HulkControl.startRampage(p, HulkState.RAMPAGE_TAKEOVER);
		long end = Hulk.state(p).combat.rampageUntil;
		Hulk.clearTransient(p); // logging out
		Hulk.onPlayerJoin(p); // and back
		HulkState s = Hulk.state(p);
		helper.assertTrue(s.hulk && s.combat.rampageUntil == end && s.wild.kind == HulkState.RAMPAGE_TAKEOVER, "resumed with the same end");
		edit(p, n -> {
			n.combat.rampageUntil = now(p) - 5L;
			n.wild.failStreak = 3;
		});
		Hulk.clearTransient(p);
		Hulk.onPlayerJoin(p);
		s = Hulk.state(p);
		helper.assertTrue(!s.hulk && s.combat.rampageUntil == 0L, "expired while away: Banner, cleaned up");
		helper.assertTrue(s.wild.failStreak == 0, "the takeover counts as over");
		helper.succeed();
	}

	// ---------------------------------------------------------------- the cave

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_cave")
	public void heCarvesACaveIntoAHill(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		BlockPos feet = p.blockPosition();
		var level = helper.getLevel();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-11, 0, -11), feet.offset(11, 2, 11))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); // nothing else within his 10-block look-round
		}
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-3, -1, -4), feet.offset(9, -1, 4))) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(2, 0, -4), feet.offset(9, 6, 4))) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2); // the hill, 2 blocks east of him
		}
		HulkTakeover.Plan plan = new HulkTakeover.Plan();
		HulkTakeover.planHere(p, plan);
		helper.assertFalse(plan.pit, "a wall near: no pit");
		helper.assertTrue(plan.dir == Direction.EAST && plan.mouth.equals(feet.east(2)), "into the hill's face, got " + plan.dir + " " + plan.mouth);
		int broke = HulkTakeover.carveAll(p, plan);
		helper.assertTrue(broke >= 3 * 4 * 6, "a 3 x 4 x 6 tunnel at least, broke " + broke);
		for (int k = 2; k <= 7; k++) {
			for (int h = 0; h < 4; h++) {
				helper.assertTrue(level.getBlockState(feet.east(k).above(h)).isAir(), "air in the cave at +" + k + " / " + h);
			}
		}
		helper.assertTrue(level.getBlockState(feet.east(4).below()).is(Blocks.STONE), "the floor stays");
		helper.assertTrue(level.getBlockState(feet.east(4).above(5)).is(Blocks.STONE), "and the roof");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_pit")
	public void onFlatGroundHeDigsAPitShelter(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		BlockPos feet = p.blockPosition();
		var level = helper.getLevel();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-11, 0, -11), feet.offset(11, 2, 11))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); // nothing else within his 10-block look-round
		}
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-3, -7, -3), feet.offset(3, -1, 3))) {
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		BlockPos bedrock = feet.below(3).east();
		level.setBlock(bedrock, Blocks.BEDROCK.defaultBlockState(), 2);
		HulkTakeover.Plan plan = new HulkTakeover.Plan();
		HulkTakeover.planHere(p, plan);
		helper.assertTrue(plan.pit, "nothing to dig into: a pit");
		int broke = HulkTakeover.carveAll(p, plan);
		helper.assertTrue(broke > 20, "the pit was dug, broke " + broke);
		helper.assertTrue(level.getBlockState(feet.below(2)).isAir() && level.getBlockState(feet.below(plan.height)).isAir(), "air down the pit");
		helper.assertTrue(level.getBlockState(feet.below(plan.height + 1)).is(Blocks.STONE), "no deeper than his height");
		helper.assertTrue(level.getBlockState(bedrock).is(Blocks.BEDROCK), "never bedrock");
		helper.succeed();
	}

	// ---------------------------------------------------------------- Banner's fight, the death change

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_banner")
	public void tenMinutesAsBannerAndTheHulkFightsToGetOut(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		HulkConfig.Control cfg = HulkConfig.control();
		helper.assertTrue(cfg.bannerFirstWarnTicks == 10 * 60 * 20 && cfg.bannerRepeatWarnTicks == 2 * 60 * 20, "10 minutes, then every 2");
		edit(p, n -> n.wild.bannerTicks = cfg.bannerFirstWarnTicks - 150);
		Hulk.tick(p);
		helper.assertFalse(HulkControl.warning(Hulk.state(p), now(p)), "not yet");
		edit(p, n -> n.wild.bannerTicks = cfg.bannerFirstWarnTicks);
		Hulk.tick(p);
		HulkState s = Hulk.state(p);
		helper.assertTrue(HulkControl.warning(s, now(p)) && s.wild.warnBanner && !s.hulk, "10 minutes as Banner: the warning, as Banner");
		typeWarning(p);
		s = Hulk.state(p);
		helper.assertFalse(Hulk.isHulk(p) || HulkControl.warning(s, now(p)), "held back");
		helper.assertTrue(s.wild.bannerNextAt == s.wild.bannerTicks + cfg.bannerRepeatWarnTicks, "the next try 2 minutes on");
		edit(p, n -> n.wild.bannerTicks = n.wild.bannerNextAt);
		Hulk.tick(p);
		helper.assertTrue(HulkControl.warning(Hulk.state(p), now(p)), "and he tries again");
		edit(p, n -> n.rules.warnUntil = now(p) - 1);
		Hulk.tick(p);
		helper.assertTrue(Hulk.isHulk(p) && Hulk.changing(p), "lost: he forces the change");
		helper.assertTrue(Hulk.state(p).wild.pending == HulkState.RAMPAGE_FORCED + 1, "a forced rampage is owed");
		helper.assertTrue(Hulk.state(p).wild.failStreak == 0, "Banner's fights don't count toward the takeover");
		edit(p, n -> n.formChangedAt = now(p) - Hulk.changeTicks(n) - 1);
		Hulk.tick(p);
		s = Hulk.state(p);
		helper.assertTrue(s.rampaging(now(p)) && s.wild.kind == HulkState.RAMPAGE_FORCED, "the change over: the forced rampage");
		helper.assertTrue(s.combat.rampageUntil - now(p) == 3L * 60L * 20L, "3 minutes");
		helper.assertTrue(s.noRing(now(p)), "no ring");
		runOut(p);
		helper.assertTrue(Hulk.isHulk(p) && !HulkControl.rampaging(p), "then the player has the Hulk back");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_takeover_death")
	public void bannersDeathChangeEndsInARampage(GameTestHelper helper) {
		ServerPlayer p = gamma(helper);
		helper.assertTrue(Hulk.tryDeathSave(p, p.damageSources().generic()), "the death is caught");
		// v0.16.4 (user): the death change's rampage is the mind screen, not the ring
		helper.assertTrue(Hulk.isHulk(p) && Hulk.state(p).wild.pending == HulkState.RAMPAGE_MIND + 1, "a mind rampage is owed");
		Hulk.tick(p);
		helper.assertFalse(HulkControl.rampaging(p), "not mid-change");
		edit(p, n -> n.formChangedAt = now(p) - Hulk.changeTicks(n) - 1);
		Hulk.tick(p);
		HulkState s = Hulk.state(p);
		helper.assertTrue(s.rampaging(now(p)) && s.wild.kind == HulkState.RAMPAGE_MIND && s.inMindScreen(now(p)), "straight into the mind rampage");
		helper.assertTrue(s.noRing(now(p)), "no ring minigame for it");
		helper.assertTrue(s.combat.rampageUntil - now(p) == HulkControl.durationTicks(HulkState.RAMPAGE_MIND), "with its hard end");
		helper.assertTrue(s.wild.bannerTicks == 0, "and the change reset Banner's clock");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_mind_screen")
	public void reachingTheMindScreenEndsOnlyAMindRampage(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		// not during a normal (ring) rampage
		HulkControl.startRampage(p, HulkState.RAMPAGE_NORMAL);
		edit(p, n -> n.wild.startedAt = now(p) - 10_000L);
		helper.assertFalse(HulkControl.mindScreenReached(p), "a ring rampage ignores the screen");
		helper.assertTrue(HulkControl.rampaging(p), "and keeps going");
		runOut(p);
		// a mind rampage: not straight away (the walk takes longer), then yes
		HulkControl.startRampage(p, HulkState.RAMPAGE_MIND);
		helper.assertTrue(Hulk.state(p).inMindScreen(now(p)), "in the mind");
		helper.assertTrue(Hulk.state(p).combat.rampageUntil - now(p) == 4L * 60L * 20L, "4 minutes at most");
		helper.assertFalse(HulkControl.mindScreenReached(p), "too soon to have walked 100 blocks");
		helper.assertTrue(HulkControl.rampaging(p), "still rampaging");
		edit(p, n -> n.wild.startedAt = now(p) - HulkConfig.control().mindMinTicks - 1L);
		helper.assertTrue(HulkControl.mindScreenReached(p), "reaching the screen ends it");
		HulkState s = Hulk.state(p);
		helper.assertTrue(Hulk.isHulk(p) && !s.rampaging(now(p)) && s.combat.control >= 100.0f, "the player has the Hulk back");
		helper.assertTrue(s.wild.kind == HulkState.RAMPAGE_NORMAL && s.combat.rampageUntil == 0L, "and the rampage is clean");
		helper.assertTrue(s.rules.nextRampageAt > now(p), "the minute's clock starts again");
		helper.assertFalse(HulkControl.mindScreenReached(p), "nothing to end any more");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "hulk_mind_screen_end")
	public void theMindRampageStillHasAHardEnd(GameTestHelper helper) {
		ServerPlayer p = hulk(helper);
		HulkControl.startRampage(p, HulkState.RAMPAGE_MIND);
		runOut(p);
		helper.assertTrue(Hulk.isHulk(p) && !HulkControl.rampaging(p), "the clock lets him go");
		HulkControl.startRampage(p, HulkState.RAMPAGE_MIND);
		edit(p, n -> n.combat.rampageUntil = now(p) - HulkControl.WATCHDOG_SLACK_TICKS - 5L);
		HulkControl.watchdog(p);
		helper.assertTrue(Hulk.state(p).combat.rampageUntil == 0L && Hulk.isHulk(p), "the watchdog lets go too");
		// a relog resumes it with the same end
		HulkControl.startRampage(p, HulkState.RAMPAGE_MIND);
		long end = Hulk.state(p).combat.rampageUntil;
		Hulk.clearTransient(p);
		Hulk.onPlayerJoin(p);
		helper.assertTrue(Hulk.state(p).inMindScreen(now(p)) && Hulk.state(p).combat.rampageUntil == end, "resumed after a relog");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theWildStateSurvivesSaveAndLoad(GameTestHelper helper) {
		HulkState s = new HulkState(true, true, 10.0f, 1L, 2L, 3L);
		s.wild.failStreak = 2;
		s.wild.kind = HulkState.RAMPAGE_TAKEOVER;
		s.wild.pending = 2;
		s.wild.phase = HulkState.TAKEOVER_SIT;
		s.wild.target = 77;
		s.wild.faceYaw = 45.0f;
		s.wild.startedAt = 1234L;
		s.wild.bannerTicks = 5000;
		s.wild.bannerNextAt = 7400;
		s.wild.warnBanner = true;
		var json = HulkState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		HulkState b = HulkState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		helper.assertTrue(b.wild.failStreak == 2 && b.wild.kind == HulkState.RAMPAGE_TAKEOVER && b.wild.pending == 2
				&& b.wild.phase == HulkState.TAKEOVER_SIT && b.wild.target == 77 && b.wild.faceYaw == 45.0f && b.wild.startedAt == 1234L
				&& b.wild.bannerTicks == 5000 && b.wild.bannerNextAt == 7400 && b.wild.warnBanner, "the wild group round-trips");
		var old = json.getAsJsonObject().deepCopy();
		old.remove("wild");
		helper.assertTrue(HulkState.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow().wild.target == -1, "an old save loads with a fresh group");
		helper.succeed();
	}
}
