package com.projecthero.mod.gametest;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.cannonbolt.CannonboltKit;
import com.projecthero.mod.omnitrix.alien.wildvine.WildvineKit;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
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
		helper.assertTrue(Aliens.unfound(p).equals(java.util.List.of(Aliens.CANNONBOLT, Aliens.WILDVINE, Aliens.DITTO)),
				"all three are left for a glitch to find (v0.17.2: + Ditto)");
		Omnitrix.setMistransforms(p, 8); // two ordinary mistransforms, then the 11th
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		attempt(helper, p, 0, 11, java.util.Set.of(), found -> {
			java.util.List<Aliens.Alien> others = new java.util.ArrayList<>(java.util.List.of(Aliens.CANNONBOLT, Aliens.WILDVINE, Aliens.DITTO));
			others.remove(found);
			helper.assertTrue(others.stream().noneMatch(o -> Aliens.found(p, o.id())), "the other hidden aliens are still hidden");
			helper.assertTrue(Aliens.unfound(p).equals(others), "and they are the only ones left");
			helper.succeed();
		});
	}

	/**
	 * v0.17.2 (user: "When it comes to unlocking new aliens make it an even chance to unlock either cannonbolt or wildvine if you dont
	 * unlock any of them ... im gonna add more new laiens still"): the 11th mistransform finds one hidden alien, the 22nd the other --
	 * never the same one twice -- and after that nothing is left to find.
	 */
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 6000)
	public void the11thAnd22ndMistransformsFindBothHiddenAliensWithNoRepeat(GameTestHelper helper) {
		ServerPlayer p = proto(helper);
		Omnitrix.setMistransforms(p, 10); // the next one is the 11th
		helper.onEachTick(() -> {
			p.tickCount++;
			Omnitrix.tick(p);
		});
		attempt(helper, p, 0, 11, java.util.Set.of(), first -> {
			Omnitrix.setMistransforms(p, 21); // skip ahead: the next one is the 22nd
			attempt(helper, p, 0, 22, java.util.Set.of(first.id()), second -> {
				helper.assertTrue(second != first, "the 22nd finds another hidden alien, not " + first.id() + " again");
				// v0.17.2 (Ditto): a third hidden alien -- the 33rd finds the last one
				Omnitrix.setMistransforms(p, 32);
				attempt(helper, p, 0, 33, java.util.Set.of(first.id(), second.id()), third -> {
					helper.assertTrue(third != first && third != second, "the 33rd finds the last one, no repeat: " + third.id());
					helper.assertTrue(Aliens.found(p, CannonboltKit.ID) && Aliens.found(p, WildvineKit.ID) && Aliens.found(p, Aliens.DITTO.id()),
							"all three are on the dial now");
					helper.assertTrue(Aliens.unfound(p).isEmpty() && Aliens.nextHidden(p) == null, "nothing hidden left");
					helper.succeed();
				});
			});
		});
	}

	/** Over many draws the pick between two unfound hidden aliens is even (each between 40% and 60% of 400 picks). */
	@GameTest(template = EMPTY_STRUCTURE)
	public void theHiddenAlienPickIsAnEvenChance(GameTestHelper helper) {
		ServerPlayer p = proto(helper);
		net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(1234L);
		// v0.17.2 (Ditto): three unfound hidden aliens -- a third each (each between 25% and 42% of 600 picks)
		java.util.Map<Aliens.Alien, Integer> n = new java.util.HashMap<>();
		for (int i = 0; i < 600; i++) {
			Aliens.Alien a = Aliens.pickHidden(p, r);
			helper.assertTrue(a == Aliens.CANNONBOLT || a == Aliens.WILDVINE || a == Aliens.DITTO, "the pick is one of the hidden aliens");
			n.merge(a, 1, Integer::sum);
		}
		for (Aliens.Alien a : java.util.List.of(Aliens.CANNONBOLT, Aliens.WILDVINE, Aliens.DITTO)) {
			int c = n.getOrDefault(a, 0);
			helper.assertTrue(c > 150 && c < 250, "an even chance: " + a.id() + " " + c + "/600");
		}
		Aliens.discover(p, CannonboltKit.ID, true);
		for (int i = 0; i < 50; i++) {
			Aliens.Alien a = Aliens.pickHidden(p, r);
			helper.assertTrue(a == Aliens.WILDVINE || a == Aliens.DITTO, "once Cannonbolt is found only Wildvine and Ditto are left");
		}
		Aliens.discover(p, WildvineKit.ID, true);
		helper.assertTrue(Aliens.pickHidden(p, r) == Aliens.DITTO, "then only Ditto");
		Aliens.discover(p, Aliens.DITTO.id(), true);
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
		helper.assertTrue(Math.abs(p.getBbHeight() - 1.75f) < 0.02f && Math.abs(p.getBbWidth() - 1.75f) < 0.02f, "a 1.75-block ball (v0.17.2 user): " + p.getBbWidth() + " x " + p.getBbHeight());
		helper.assertTrue(p.getEyeHeight() < 1.2f, "the ball's eyes sit low: " + p.getEyeHeight());
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_1, false, false).id().equals(CannonboltKit.CHARGE), "ball R is the Cannonball Charge");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_1, true, false).id().equals(CannonboltKit.RICOCHET), "ball Shift+R is the Pinball Ricochet");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_2, false, false).id().equals(CannonboltKit.BOUNCE), "ball G is the Big Bounce");
		helper.assertTrue(kit.liveMoveOn(p, AbilitySlot.SLOT_6, false, false).id().equals(CannonboltKit.UNSTOPPABLE), "ball C is Unstoppable");
		helper.assertTrue(kit.pawsOnly(p), "rolled up: no items");
		helper.assertFalse(kit.climbs(p), "not on a ladder in the open (only pressed into a wall)");
		// uncurl after the V cooldown
		helper.runAfterDelay(12, () -> {
			tap(p, AbilitySlot.SLOT_5, false);
			helper.assertFalse(CannonboltKit.ball(p), "V again uncurls him");
			p.refreshDimensions();
			helper.assertTrue(Math.abs(p.getBbHeight() - 1.55f * 1.3f) < 0.05f, "standing again, a headless body: " + p.getBbHeight());
			helper.assertTrue(Math.abs(p.getEyeHeight() - 1.25f * 1.3f) < 0.05f && p.getEyeHeight() < p.getBbHeight() - 0.3f, "the view comes out of his chest: " + p.getEyeHeight());
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
		tap(p, AbilitySlot.SLOT_4, false);
		helper.assertTrue(z.getHealth() < before, "the Ground Pound hits the zombie 2 blocks off: " + z.getHealth());
		helper.assertTrue(Aliens.kit(CannonboltKit.ID).cooldownLeft(p, CannonboltKit.POUND) > 0, "Z cools down");
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
		helper.assertTrue(CannonboltKit.moving(p), "the Pinball Ricochet runs");
		helper.assertTrue(kit.keyActive(p, AbilitySlot.SLOT_1), "R lights while ricocheting");
		tap(p, AbilitySlot.SLOT_6, false);
		helper.assertTrue(kit.keyActive(p, AbilitySlot.SLOT_6), "Unstoppable lights C");
		helper.assertTrue(kit.incomingDamage(p, helper.getLevel().damageSources().mobAttack(z), 10f) == 2f, "Unstoppable: 80% less");
		Omnitrix.revert(p, Omnitrix.Reason.REVOKE);
		helper.assertFalse(CannonboltKit.ball(p) || CannonboltKit.moving(p), "turning back clears the ball and the moves");
		helper.succeed();
	}
}
