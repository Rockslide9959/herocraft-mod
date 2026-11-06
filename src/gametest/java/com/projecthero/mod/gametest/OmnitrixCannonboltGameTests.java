package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltKit;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltRoll;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltStow;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;
import com.projecthero.mod.squad.SquadManager;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Cannonbolt (user: "build canonbolt alien for the prototype omnitrix please ... change his abilities if hes in ball mode or
 * not please"): on the prototype's dial only, V curls him into Ball Mode (a ball hitbox, every key swapped), the shell's passives, and
 * the standing moves hit.
 */
public class OmnitrixCannonboltGameTests implements FabricGameTest {
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

	private static ServerPlayer cannonbolt(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Aliens.discover(p, CannonboltKit.ID, true); // found by a mistransform (its own test below)
		helper.assertTrue(Omnitrix.select(p, CannonboltKit.ID), "Cannonbolt is on the prototype's dial once found");
		helper.assertTrue(Omnitrix.transform(p), "transformed into Cannonbolt");
		return p;
	}

	private static void tap(ServerPlayer p, AbilitySlot slot, boolean shift) {
		p.setShiftKeyDown(shift);
		AbilityRouter.handleInput(p, slot.number(), true);
		AbilityRouter.handleInput(p, slot.number(), false);
		p.setShiftKeyDown(false);
	}

	private static Zombie zombie(GameTestHelper helper, double x, double y, double z) {
		helper.getLevel().getServer().setDifficulty(Difficulty.HARD, true);
		Zombie m = EntityType.ZOMBIE.create(helper.getLevel());
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		m.moveTo(at.x, at.y, at.z, 180f, 0f);
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
		helper.getLevel().addFreshEntity(m);
		return m;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void cannonboltIsOnThePrototypeDialOnly(GameTestHelper helper) {
		helper.assertTrue(Aliens.byId(CannonboltKit.ID) != null && !Aliens.byId(CannonboltKit.ID).recalibrated(), "declared on the prototype");
		helper.assertTrue(Aliens.dial(false).contains(Aliens.CANNONBOLT), "playable on the prototype's dial");
		helper.assertFalse(Aliens.dial(true).contains(Aliens.CANNONBOLT), "not on the Recalibrated Omnitrix's");
		ServerPlayer recal = mock(helper, 1.5, 2, 1.5);
		Omnitrix.grant(recal, true);
		helper.assertFalse(Omnitrix.select(recal, CannonboltKit.ID), "the recalibrated watch cannot pick him");
		helper.succeed();
	}

	/**
	 * v0.17.2 (user: "for the canonbolt alien i want ben to accidentally transform into the alien like a mistransformation"): not on
	 * the dial at first; v0.17.2 (user: "on the 11th mistransformation the player unlocks a new alien"), and since Wildvine (user: "make
	 * it an even chance to unlock either cannonbolt or wildvine if you dont unlock any of them"): mistransforms 9 and 10 land on dial
	 * aliens, the 11th on one of the two hidden ones -- on the dial for good, the dial staying where it was, the other one still hidden.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 3000)
	public void aMistransformFindsAHiddenAlienAndPutsItOnTheDial(GameTestHelper helper) {
		ServerPlayer p = proto(helper);
		helper.assertFalse(Aliens.playable(p).contains(Aliens.CANNONBOLT), "Cannonbolt is not on the dial before he is found");
		helper.assertFalse(Aliens.playable(p).contains(Aliens.WILDVINE), "nor Wildvine");
		helper.assertFalse(Omnitrix.select(p, CannonboltKit.ID) || Omnitrix.select(p, WildvineKit.ID), "and neither can be dialled");
		// v0.17.2 (Buzzshock): the pool is every hidden alien a mistransform can find -- Cannonbolt, Wildvine, Ditto, Upchuck, Buzzshock (and any
		// added later), never an item-only one (Way Big)
		java.util.List<Aliens.Alien> pool = Aliens.mistransformFinds();
		helper.assertTrue(pool.containsAll(java.util.List.of(Aliens.CANNONBOLT, Aliens.WILDVINE, Aliens.DITTO, Aliens.UPCHUCK, Aliens.BUZZSHOCK, Aliens.FEEDBACK)) && !pool.contains(Aliens.WAY_BIG),
				"Cannonbolt, Wildvine, Ditto, Upchuck, Buzzshock and Feedback are all a mistransform's to find, Way Big is not: " + pool);
		helper.assertTrue(Aliens.unfound(p).equals(pool), "all of them are left for a glitch to find: " + Aliens.unfound(p));
		Omnitrix.setMistransforms(p, 8); // two ordinary mistransforms, then the 11th
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		attempt(helper, p, 0, 11, java.util.Set.of(), found -> {
			java.util.List<Aliens.Alien> others = new java.util.ArrayList<>(pool);
			others.remove(found);
			helper.assertTrue(others.stream().noneMatch(o -> Aliens.found(p, o.id())), "the other hidden aliens are still hidden");
			helper.assertTrue(Aliens.unfound(p).equals(others), "and they are the only ones left");
			helper.succeed();
		});
	}

	/**
	 * v0.17.2 (user: "When it comes to unlocking new aliens make it an even chance to unlock either cannonbolt or wildvine if you dont
	 * unlock any of them ... im gonna add more new laiens still"): the 11th mistransform finds one hidden alien, the 22nd another, the
	 * 33rd the next, and so on for every hidden alien (six with Upchuck, Buzzshock and Feedback) -- never the same one twice -- and after that nothing is left.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 12000)
	public void every11thMistransformFindsEachHiddenAlienOnceWithNoRepeat(GameTestHelper helper) {
		ServerPlayer p = proto(helper);
		java.util.List<Aliens.Alien> pool = Aliens.mistransformFinds();
		helper.assertTrue(pool.size() >= 6 && pool.contains(Aliens.BUZZSHOCK) && pool.contains(Aliens.UPCHUCK) && pool.contains(Aliens.FEEDBACK), "six or more to find (v0.17.2: + Buzzshock, Feedback): " + pool);
		Omnitrix.setMistransforms(p, 10); // the next one is the 11th
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		findAll(helper, p, pool, new java.util.LinkedHashSet<>());
	}

	private static void findAll(GameTestHelper helper, ServerPlayer p, java.util.List<Aliens.Alien> pool, java.util.Set<String> found) {
		int target = 11 * (found.size() + 1);
		attempt(helper, p, 0, target, java.util.Set.copyOf(found), next -> {
			helper.assertTrue(!found.contains(next.id()) && pool.contains(next), "#" + target + " finds a new one, not a repeat: " + next.id());
			found.add(next.id());
			if (found.size() == pool.size()) {
				for (Aliens.Alien a : pool) {
					helper.assertTrue(Aliens.found(p, a.id()), a.id() + " is on the dial now");
				}
				helper.assertTrue(Aliens.unfound(p).isEmpty() && Aliens.nextHidden(p) == null, "nothing hidden left");
				helper.succeed();
				return;
			}
			Omnitrix.setMistransforms(p, target + 10); // skip ahead: the next one is the next 11th
			findAll(helper, p, pool, found);
		});
	}

	/** Over many draws the pick among the unfound hidden aliens is even; a found one leaves the draw, until none is left. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theHiddenAlienPickIsAnEvenChance(GameTestHelper helper) {
		ServerPlayer p = proto(helper);
		net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(1234L);
		java.util.List<Aliens.Alien> left = new java.util.ArrayList<>(Aliens.mistransformFinds());
		helper.assertTrue(left.contains(Aliens.BUZZSHOCK) && left.contains(Aliens.FEEDBACK), "Buzzshock and Feedback are in the draw");
		while (!left.isEmpty()) {
			java.util.Map<Aliens.Alien, Integer> n = new java.util.HashMap<>();
			int draws = 400 * left.size();
			for (int i = 0; i < draws; i++) {
				Aliens.Alien a = Aliens.pickHidden(p, r);
				helper.assertTrue(left.contains(a), "the pick is one of the hidden aliens still unfound: " + a);
				n.merge(a, 1, Integer::sum);
			}
			for (Aliens.Alien a : left) {
				double share = n.getOrDefault(a, 0) / (double) draws;
				helper.assertTrue(Math.abs(share - 1.0 / left.size()) < 0.07, "an even chance: " + a.id() + " " + share + " of " + left.size());
			}
			Aliens.Alien gone = left.remove(0);
			Aliens.discover(p, gone.id(), true);
		}
		helper.assertTrue(Aliens.pickHidden(p, r) == null, "and then none");
		helper.succeed();
	}

	private static ServerPlayer proto(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
		ServerPlayer p = mock(helper, 3.5, 2, 1.5);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		return p;
	}

	/**
	 * Slap Heatblast until mistransform #{@code target} (50% a slap): the ones before it are dial aliens (never a hidden one, nothing
	 * found), #{@code target} is a hidden alien not in {@code before} -- found, on the dial, the dial left on Heatblast. Then {@code then}.
	 */
	private static void attempt(GameTestHelper helper, ServerPlayer p, int n, int target, java.util.Set<String> before,
			java.util.function.Consumer<Aliens.Alien> then) {
		if (n >= 120) {
			helper.fail("only " + Omnitrix.mistransforms(p) + " mistransforms in 120 slaps");
			return;
		}
		if (Omnitrix.transformed(p)) {
			Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		}
		Omnitrix.setRecharge(p, 0);
		Omnitrix.activate(p);
		Omnitrix.finishRaise(p);
		Omnitrix.select(p, Aliens.HEATBLAST.id());
		Omnitrix.slap(p);
		helper.runAfterDelay(Omnitrix.SLAP_TICKS + 4, () -> {
			String active = Omnitrix.state(p).active;
			if (!active.isEmpty() && !active.equals(Aliens.HEATBLAST.id())) {
				int count = Omnitrix.mistransforms(p);
				if (count < target) {
					helper.assertFalse(Aliens.hidden(active) && !before.contains(active), "mistransform #" + count + " finds nothing yet, got " + active);
					attempt(helper, p, n + 1, target, before, then);
					return;
				}
				Aliens.Alien found = Aliens.byId(active);
				helper.assertTrue(count == target && Aliens.hidden(active) && !before.contains(active),
						"mistransform #" + target + " lands on a hidden alien not found before, got #" + count + " " + active);
				helper.assertTrue(Aliens.found(p, active) && Aliens.playable(p).contains(found), active + " is on the dial now");
				helper.assertTrue(Omnitrix.state(p).selected.equals(Aliens.HEATBLAST.id()), "the dial stays on Heatblast");
				then.accept(found);
				return;
			}
			attempt(helper, p, n + 1, target, before, then);
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void vCurlsIntoBallModeAndSwapsEveryKey(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		float standingWidth = p.getBbWidth();
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_1, false, false).id().equals(CannonboltKit.BASH), "standing R is the Shoulder Bash");
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(CannonboltKit.ball(p), "V curls him up");
		p.refreshDimensions();
		helper.assertTrue(Math.abs(p.getBbHeight() - 2f) < 0.02f && Math.abs(p.getBbWidth() - 2f) < 0.02f, "a 2-block ball (v0.17.2 user): " + p.getBbWidth() + " x " + p.getBbHeight());
		helper.assertTrue(p.getEyeHeight() < 1.2f, "the ball's eyes sit low: " + p.getEyeHeight());
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_1, false, false).id().equals(CannonboltKit.CHARGE), "ball R is the Cannonball Charge");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_1, true, false).id().equals(CannonboltKit.RAMPAGE), "ball Shift+R is the Rolling Rampage");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_2, false, false).id().equals(CannonboltKit.BOUNCE), "ball G is the Big Bounce");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_6, false, false).id().equals(CannonboltKit.SPIN_CHARGE), "C is the Spin Charge in both forms");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_6, true, false).id().equals(CannonboltKit.CURL_SMASH), "Shift+C is the Curl Smash in both forms");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_4, true, false).id().equals(CannonboltKit.UNSTOPPABLE), "Shift+Z is the ultimate in both forms");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_5, true, false).id().equals(CannonboltKit.SPIN), "ball Shift+V is the Shell Spin");
		helper.assertTrue(kit.pawsOnly(p), "rolled up: no items");
		helper.assertFalse(kit.climbs(p), "the ball never climbs walls (v0.17.2 user)");
		helper.assertTrue(p.maxUpStep() >= 3.99f, "the ball rolls up 4-block ledges: " + p.maxUpStep());
		// uncurl after the V cooldown
		helper.runAfterDelay(12, () -> {
			tap(p, AbilitySlot.SLOT_5, false);
			helper.assertFalse(CannonboltKit.ball(p), "V again uncurls him");
			p.refreshDimensions();
			helper.assertTrue(Math.abs(p.getBbHeight() - 2f) < 0.02f, "standing again, a 2-block headless body (v0.17.2 user): " + p.getBbHeight());
			helper.assertTrue(Math.abs(CannonboltKit.SCALE * 1.5f * 0.9375f - 2f) < 0.001f, "the model drawn, shoulders at 2 blocks: " + CannonboltKit.SCALE);
			helper.assertTrue(Math.abs(p.getEyeHeight() - CannonboltKit.CHEST_EYE * CannonboltKit.SCALE) < 0.05f && p.getEyeHeight() < p.getBbHeight() - 0.3f, "the view comes out of his chest: " + p.getEyeHeight());
			helper.assertFalse(kit.pawsOnly(p), "hands back");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theBallTakesHalfDamageDeflectsArrowsAndBouncesOffFalls(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		Zombie z = zombie(helper, 6.5, 2, 6.5);
		DamageSource hit = helper.getLevel().damageSources().mobAttack(z);
		helper.assertTrue(kit.incomingDamage(p, hit, 10f) == 10f, "standing, a generic hit is whole");
		tap(p, AbilitySlot.SLOT_5, false);
		Omnitrix.tick(p);
		helper.assertTrue(kit.incomingDamage(p, hit, 10f) == 5f, "rolled up, half");
		DamageSource arrow = helper.getLevel().damageSources().arrow(null, null);
		helper.assertFalse(kit.allowDamage(p, arrow, 6f), "arrows glance off the ball");
		DamageSource fall = helper.getLevel().damageSources().fall();
		helper.assertFalse(kit.allowDamage(p, fall, 8f), "a fall bounces the ball");
		helper.assertTrue(p.getDeltaMovement().y > 0.3, "bounced back up: " + p.getDeltaMovement().y);
		helper.succeed();
	}

	/** v0.17.2 (user: "if he falls from a great distance the ball form should bounce a bit first"): a run of bounces that always ends. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void aHighFallStartsBouncesThatAlwaysSettle(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		tap(p, AbilitySlot.SLOT_5, false);
		Omnitrix.tick(p);
		helper.assertFalse(kit.allowDamage(p, helper.getLevel().damageSources().fall(), 20f), "no fall damage rolled up");
		helper.assertTrue(CannonboltKit.bouncesLeft(p) == CannonboltKit.FALL_BOUNCES - 1, "the first bounce, more to come: " + CannonboltKit.bouncesLeft(p));
		helper.assertTrue(p.getDeltaMovement().y > 0.9, "a big fall's first bounce is high: " + p.getDeltaMovement().y);
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		helper.runAfterDelay(130, () -> {
			helper.assertTrue(CannonboltKit.bouncesLeft(p) == 0, "the bounces never run on forever");
			helper.succeed();
		});
	}

	/** v0.17.2 (user: "when he sprint jumps its more like a forward launch kind of thing"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void aSprintJumpRolledUpIsAForwardLaunch(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		tap(p, AbilitySlot.SLOT_5, false);
		p.setOnGround(true);
		Omnitrix.tick(p); // on the ground
		helper.runAfterDelay(1, () -> {
			p.setSprinting(true);
			p.setOnGround(false);
			p.setPos(p.getX(), p.getY() + 0.42, p.getZ()); // the jump
			Omnitrix.tick(p);
			helper.assertTrue(CannonboltKit.moving(p), "the sprint jump fired him forward");
			Vec3 v = p.getDeltaMovement();
			helper.assertTrue(Math.hypot(v.x, v.z) >= CannonboltKit.LAUNCH_SPEED - 0.01 && v.y > 0.3, "a long, low arc: " + v);
			helper.assertTrue(v.z > 0.5, "the way he faces (yaw 0 = +z): " + v);
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void groundPoundAndShoulderBashHitStanding(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		Zombie z = zombie(helper, 3.5, 2, 3.5);
		float before = z.getHealth();
		tap(p, AbilitySlot.SLOT_2, true);
		helper.assertTrue(z.getHealth() < before, "the Ground Pound hits the zombie 2 blocks off: " + z.getHealth());
		helper.assertTrue(Aliens.kit(CannonboltKit.ID).cooldownLeft(p, CannonboltKit.POUND) > 0, "Shift+G cools down");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ballMovesStartAndCannotPunch(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		Zombie z = zombie(helper, 3.5, 2, 2.6);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(kit.onAttack(p, z), "the ball's left-click is swallowed (no punch)");
		tap(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(CannonboltKit.moving(p), "the Rolling Rampage runs");
		helper.assertTrue(kit.keyActive(p, AbilitySlot.SLOT_1), "R lights while it runs");
		helper.assertTrue(kit.flag(p, CannonboltKit.F_DRIVEN), "and his keys are not his own");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(CannonboltKit.ball(p) || CannonboltKit.moving(p), "turning back clears the ball and the moves");
		helper.succeed();
	}

	// ---------------------------------------------------------------- round 3

	/** Stands in for the owner's client, which is what really moves a player: this tick's velocity, as a move, and no further. */
	private static void roll(ServerPlayer p) {
		p.setPos(p.position().add(p.getDeltaMovement()));
		p.tickCount++;
		Omnitrix.tick(p);
	}

	/** v0.17.2 round 3 (user: "at 0 momentum he moves at 10 blocks per second, at max momentum he moves at 25 ... make it 10 at 0 momentum and 20 at max momentum ... like 3 seconds of sprinting"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theRollsNumbersAreTheOnesAskedFor(GameTestHelper helper) {
		helper.assertTrue(Math.abs(CannonboltRoll.speedAt(0) * 20 - 10) < 1.0e-6 && Math.abs(CannonboltRoll.speedAt(1) * 20 - 30) < 1.0e-6, "10 to 30 blocks a second");
		helper.assertTrue(CannonboltRoll.ramDamage(0) == 10f && CannonboltRoll.ramDamage(1) == 20f && CannonboltRoll.ramDamage(0.5) == 15f, "the ram goes 10 to 20 with Momentum");
		double m = 0;
		for (int i = 0; i < 59; i++) {
			m = CannonboltRoll.step(m, true, true, true, false);
		}
		helper.assertTrue(m < 1.0, "not there before 3 s: " + m);
		m = CannonboltRoll.step(m, true, true, true, false);
		helper.assertTrue(m > 0.999, "full after 3 s of sprinting: " + m);
		helper.assertTrue(CannonboltRoll.step(m, true, false, true, false) == m, "rolling on without sprinting keeps it");
		helper.assertTrue(CannonboltRoll.step(m, true, true, false, false) == 1.0, "in the air it keeps it");
		helper.assertTrue(CannonboltRoll.step(m, false, false, false, false) == m, "falling with no keys it keeps it");
		helper.assertTrue(CannonboltRoll.step(m, false, false, true, false) < m, "let go on the ground and it bleeds away");
		helper.assertTrue(CannonboltRoll.step(m, true, true, true, true) == 0, "a wall ends it");
		Vec3 turned = CannonboltRoll.turn(new Vec3(1, 0, 0), new Vec3(0, 0, 1));
		helper.assertTrue(Math.abs(turned.length() - 1) < 1.0e-6, "a turn is a change of heading, never of speed: " + turned.length());
		helper.assertTrue(Math.abs(CannonboltRoll.momentumFromSpeed(1.0) - 0.5) < 1.0e-6, "the server reads Momentum back off his speed");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void theRamHitsByMomentumFromHisRealSpeed(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		Zombie z = zombie(helper, 3.5, 2, 12);
		z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
		z.setHealth(100f);
		tap(p, AbilitySlot.SLOT_5, false);
		p.setDeltaMovement(0, 0, 1.5); // top speed straight at the zombie, 30 blocks a second
		helper.onEachTick(() -> {
			if (z.getHealth() >= 100f) {
				roll(p);
			}
		});
		helper.runAfterDelay(14, () -> {
			helper.assertTrue(Math.abs(z.getHealth() - 80f) < 0.5f, "20 at full Momentum, less a zombie's bit of armour: " + z.getHealth());
			helper.succeed();
		});
	}

	/** v0.17.2 round 3 (user: "replace the C ability with a ball charge move, player holds c and goes into ball form and begins spinning on the ground"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void holdingCCurlsHimUpAndWindsUpUntilHeLetsGo(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_6.number(), true);
		helper.assertTrue(CannonboltKit.ball(p) && kit.flag(p, CannonboltKit.F_CHARGING), "holding C from standing curls him and starts the spin");
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		helper.runAfterDelay(CannonboltKit.SPIN_CHARGE_FILL + 5, () -> {
			helper.assertTrue(kit.flag(p, CannonboltKit.F_CHARGING), "it lasts as long as C is held");
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_6.number(), false);
			helper.assertFalse(kit.flag(p, CannonboltKit.F_CHARGING), "letting go ends it");
			helper.assertTrue(kit.cooldownLeft(p, CannonboltKit.SPIN_CHARGE) > 0, "and starts its cooldown");
			helper.assertTrue(CannonboltKit.ball(p), "he stays in Ball Mode");
			helper.succeed();
		});
	}

	/** v0.17.2 round 3 (user: "Make holding shift+V a block ability when not in ball form"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theBlockLastsAsLongAsShiftVIsHeld(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		AlienKit kit = Aliens.kit(CannonboltKit.ID);
		p.setShiftKeyDown(true);
		AbilityRouter.handleInput(p, AbilitySlot.SLOT_5.number(), true);
		helper.assertTrue(kit.flag(p, CannonboltKit.F_GUARD), "held Shift+V blocks");
		helper.assertFalse(CannonboltKit.ball(p), "and does not curl him");
		helper.onEachTick(() -> {
			p.tickCount++;
			p.setShiftKeyDown(true);
			Omnitrix.tick(p);
		});
		helper.runAfterDelay(90, () -> {
			helper.assertTrue(kit.flag(p, CannonboltKit.F_GUARD), "still up past the old 3 s");
			AbilityRouter.handleInput(p, AbilitySlot.SLOT_5.number(), false);
			helper.assertFalse(kit.flag(p, CannonboltKit.F_GUARD), "letting go drops it");
			helper.assertTrue(kit.cooldownLeft(p, CannonboltKit.GUARD) > 0, "and the cooldown starts then");
			helper.succeed();
		});
	}

	/** v0.17.2 round 3: the 20 nearest near his level, and he rolls every block of the way (brief: "no teleporting"). */
	@GameTest(template = EMPTY_STRUCTURE, batch = "omnitrixcannonbolt_rampage1")
	public void theRampagePicksTheNearestAndRollsBetweenThem(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		java.util.List<Zombie> near = new java.util.ArrayList<>();
		for (int i = 0; i < 22; i++) {
			near.add(zombie(helper, 3.5 + (i % 2 == 0 ? 3 : -3), 2, 3 + i)); // (the Rampage reaches 30 blocks)
		}
		Zombie high = zombie(helper, 3.5, 12, 4);
		tap(p, AbilitySlot.SLOT_5, false);
		java.util.List<net.minecraft.world.entity.LivingEntity> marks = CannonboltKit.rampageTargets(p);
		helper.assertTrue(marks.size() == CannonboltKit.RAMPAGE_TARGETS, "20 at most: " + marks.size());
		helper.assertFalse(marks.contains(high), "never one 10 blocks over his head");
		for (int i = 0; i + 1 < marks.size(); i++) {
			helper.assertTrue(marks.get(i).distanceToSqr(p) <= marks.get(i + 1).distanceToSqr(p), "the nearest first");
		}
		helper.succeed();
	}

	/** Brief: "must physically roll the path: no teleporting". */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "omnitrixcannonbolt_rampage2") // its own batch: another test's zombies 40 blocks off are enemies to him too
	public void theRampageRollsEveryBlockBetweenEnemies(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		java.util.List<Zombie> marks = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			marks.add(zombie(helper, i % 2 == 0 ? 1.5 : 6.5, 2, 4 + i));
		}
		tap(p, AbilitySlot.SLOT_5, false);
		tap(p, AbilitySlot.SLOT_1, true);
		helper.assertTrue(CannonboltKit.moving(p), "off he goes");
		double[] worst = { 0 };
		helper.onEachTick(() -> {
			Vec3 before = p.position();
			p.setOnGround(true); // the floor the mock's plain setPos doesn't have
			p.setDeltaMovement(CannonboltKit.moving(p) ? p.getDeltaMovement().multiply(1, 0, 1) : Vec3.ZERO);
			roll(p);
			worst[0] = Math.max(worst[0], p.position().distanceTo(before));
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(CannonboltKit.moving(p), "still rolling");
			long dead = marks.stream().filter(m -> !m.isAlive() || m.getHealth() < 1f).count(); // 20 less a zombie's bit of armour
			helper.assertTrue(dead == marks.size(), "every mark rolled over: " + dead);
			helper.assertTrue(worst[0] <= CannonboltRoll.SPEED_MAX + 0.15, "he only ever rolled, never jumped ahead: " + worst[0]);
			helper.assertTrue(Aliens.kit(CannonboltKit.ID).cooldownLeft(p, CannonboltKit.RAMPAGE) > 300, "the cooldown runs from the end");
		});
	}

	/** v0.17.2 round 3: the Z move rebounds off its mark three times, 12, 12 and 17. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120)
	public void theReboundStrikeHitsThreeTimes(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		Zombie z = zombie(helper, 3.5, 2, 8);
		z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
		z.setHealth(100f);
		p.setXRot(0f);
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(CannonboltKit.ball(p), "Z curls him up");
		helper.onEachTick(() -> {
			if (p.getY() > helper.absoluteVec(new Vec3(0, 2, 0)).y + 0.01 || p.getDeltaMovement().y > 0) {
				p.setDeltaMovement(p.getDeltaMovement().add(0, -0.08, 0)); // the client's gravity
			}
			roll(p);
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(CannonboltKit.moving(p), "still rebounding");
			helper.assertTrue(Math.abs(z.getHealth() - 59f) < 1.2f, "12 + 12 + 17, less a zombie's bit of armour: " + z.getHealth());
		});
	}

	/** Shift+Z: dash at the target for 30, up, and down on it for another 30; 80% less damage until he lands. */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200)
	public void ultimateDashesThenSlams(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		CannonboltKit kit = (CannonboltKit) Aliens.kit(CannonboltKit.ID);
		Zombie z = zombie(helper, 3.5, 2, 8);
		z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0);
		z.setHealth(200f);
		p.setXRot(0f);
		tap(p, AbilitySlot.SLOT_4, true);
		helper.assertTrue(CannonboltKit.ball(p) && kit.flag(p, CannonboltKit.F_SPIKES), "Shift+Z: curled, spikes out");
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 10f) == 2f, "80% less");
		helper.assertTrue(CannonboltKit.moving(p), "and he goes");
		float[] atLanding = { -1f };
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
			if (atLanding[0] < 0f && !kit.flag(p, CannonboltKit.F_SPIKES)) {
				atLanding[0] = 200f - z.getHealth();
			}
			p.setPos(p.position().add(p.getDeltaMovement()));
			if (p.getY() < helper.absoluteVec(new Vec3(0, 2, 0)).y) {
				p.setPos(p.getX(), helper.absoluteVec(new Vec3(0, 2, 0)).y, p.getZ());
				p.setOnGround(true);
			} else {
				p.setOnGround(false);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(kit.flag(p, CannonboltKit.F_SPIKES), "spikes away once he lands");
			helper.assertTrue(atLanding[0] >= 57f && atLanding[0] <= 63f, "30 + 30 by the time he lands (a shoved zombie may land hard), took " + atLanding[0]);
		});
	}

	/** v0.17.2 round 3 (user: "when in ball form player can roll through leaves like a speedster"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theBallRollsThroughLeaves(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		helper.assertFalse(com.projecthero.mod.hero.power.p04.SpeedLeaves.active(p), "not standing");
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertTrue(com.projecthero.mod.hero.power.p04.SpeedLeaves.active(p), "rolled up, as a speedster does");
		helper.succeed();
	}

	private static ServerPlayer mate(GameTestHelper helper, ServerPlayer host, boolean squad) {
		ServerPlayer m = mock(helper, 1.5, 2, 4.5);
		if (squad) {
			SquadManager squads = SquadManager.get(helper.getLevel().getServer());
			com.projecthero.mod.squad.Squad s = squads.squadOf(host.getUUID());
			if (s == null) {
				s = squads.create("cb" + host.getUUID().toString().substring(0, 8), host.getUUID());
			}
			squads.addMember(s, m.getUUID());
		}
		return m;
	}

	/** v0.17.2 round 3 (user: "up to 2 squad mates can right click the player to climb inside them, they are only released when player uncurls and get nausea"). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void twoSquadMatesRideInsideAndComeOutDizzyWhenHeUncurls(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		ServerPlayer a = mate(helper, p, true), b = mate(helper, p, true), c = mate(helper, p, true), stranger = mate(helper, p, false);
		helper.assertFalse(CannonboltStow.climbIn(a, p), "not while he is standing");
		tap(p, AbilitySlot.SLOT_5, false);
		helper.assertFalse(CannonboltStow.climbIn(stranger, p) && stranger.isPassenger(), "a stranger stays out");
		helper.assertTrue(CannonboltStow.climbIn(a, p) && CannonboltStow.climbIn(b, p), "two squad mates climb in");
		helper.assertTrue(CannonboltStow.stowed(a) && CannonboltStow.stowed(b), "they are inside");
		CannonboltStow.climbIn(c, p);
		helper.assertFalse(c.isPassenger(), "and a third does not fit");
		helper.assertFalse(a.isPickable(), "no hitbox of their own in there");
		a.setShiftKeyDown(true);
		a.rideTick();
		a.setShiftKeyDown(false);
		helper.assertTrue(a.isPassenger(), "Sneak does not get them out");
		helper.assertFalse(a.hurt(helper.getLevel().damageSources().generic(), 5f), "nothing can hurt them");
		helper.assertFalse(com.projecthero.mod.omnitrix.alien.AlienKit.around(p, p.position(), 6.0).contains(a), "and his own moves pass them by");
		helper.runAfterDelay(12, () -> {
			tap(p, AbilitySlot.SLOT_5, false);
			helper.assertFalse(CannonboltKit.ball(p), "he uncurls");
			helper.assertFalse(a.isPassenger() || b.isPassenger(), "and they are out");
			helper.assertTrue(a.hasEffect(MobEffects.CONFUSION) && b.hasEffect(MobEffects.CONFUSION), "dizzy");
			helper.assertTrue(a.isPickable(), "and solid again");
			helper.succeed();
		});
	}

	/** Brief: never lost or stuck -- turning back or dying lets them out too, and the ride holds no state of its own to go stale. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void matesInsideGetOutWhenHeTurnsBackOrDies(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		ServerPlayer a = mate(helper, p, true);
		tap(p, AbilitySlot.SLOT_5, false);
		CannonboltStow.climbIn(a, p);
		helper.assertTrue(a.isPassenger(), "inside");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(a.isPassenger(), "turning back lets them out");
		helper.assertTrue(a.hasEffect(MobEffects.CONFUSION), "dizzy");
		helper.assertTrue(a.isAlive() && a.position().distanceTo(p.position()) < 6, "beside him: " + a.position());
		helper.succeed();
	}

	/** A low ceiling keeps the ball shut: mates aren't dropped into a wall, and they get out the moment he can stand. */
	@GameTest(template = EMPTY_STRUCTURE)
	public void whenThereIsRoomMatesAreSetDownClearOfWalls(GameTestHelper helper) {
		ServerPlayer p = cannonbolt(helper);
		ServerPlayer a = mate(helper, p, true);
		for (int y = 2; y <= 4; y++) {
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(5, y, 1)), Blocks.STONE.defaultBlockState(), 3);
			helper.getLevel().setBlock(helper.absolutePos(new BlockPos(1, y, 1)), Blocks.STONE.defaultBlockState(), 3);
		}
		tap(p, AbilitySlot.SLOT_5, false);
		CannonboltStow.climbIn(a, p);
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertTrue(helper.getLevel().noCollision(a, a.getBoundingBox().deflate(1.0e-3)), "not stuck in a block: " + a.position());
		helper.succeed();
	}
}
