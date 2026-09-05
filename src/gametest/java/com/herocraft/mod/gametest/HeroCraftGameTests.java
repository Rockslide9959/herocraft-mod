package com.herocraft.mod.gametest;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.entity.MjolnirEntity;
import com.herocraft.mod.item.ModDataComponents;
import com.herocraft.mod.item.ModItems;
import com.herocraft.mod.power.ThorAbility;
import com.herocraft.mod.power.ThorPassives;
import com.herocraft.mod.power.ThorPowers;
import com.herocraft.mod.worldgen.CraterSpawnState;
import com.herocraft.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Real, automated regression coverage for HeroCraft's server-side logic, run inside an actual
 * Minecraft gametest server (see {@code build.gradle}'s {@code fabricApi.configureTests} block and
 * the "How to run" section below) rather than by hand -- this is the answer to "how do I let you
 * test this yourself" for everything that doesn't require a human's eyes or ears.
 *
 * <h2>What this can and cannot verify</h2>
 * Every test here spawns real entities/players inside a real {@code ServerLevel} and drives the
 * mod's actual production code paths (including, where noted, the real globally-registered
 * {@code ServerTickEvents.END_SERVER_TICK} handler from {@code HeroCraftMod} -- not a hand-rolled
 * substitute), so a passing test is real evidence, not a guess. What it deliberately does NOT cover:
 * <ul>
 *   <li>Anything about how something looks or sounds -- gametest has no renderer or audio device.</li>
 *   <li>Full Overworld structure generation (the Mjolnir Crater) -- gametest runs test methods
 *   inside pre-existing, already-generated flat test platforms, not through the real chunk
 *   generation pipeline a structure placement decision runs through. {@link CraterSpawnState}'s own
 *   once-only bookkeeping is tested directly instead (see {@link #craterSpawnStateTracksOnce}),
 *   which is the part of that system that doesn't depend on real world generation.</li>
 * </ul>
 *
 * <h2>How to run</h2>
 * {@code ./gradlew runGameTest} -- also runs automatically as part of {@code ./gradlew build}/
 * {@code check}, so a broken regression here now fails the normal build. Confirmed empirically
 * (the build/run/gameTest/eula.txt this generates has stayed {@code eula=false} throughout, and
 * every run since has still worked): unlike a real dedicated server, {@code GameTestServer} never
 * opens a public port or serves a real player, and vanilla does not gate it on EULA acceptance at
 * all -- the {@code eula=false} in this project's {@code fabricApi.configureTests} block is simply
 * "don't auto-write true for me," not evidence this needs asking permission for. Results print to
 * the console and to a JUnit-style XML report Loom generates under {@code build/}.
 */
public class HeroCraftGameTests implements FabricGameTest {
	private static final BlockPos ORIGIN = new BlockPos(1, 2, 1);

	/**
	 * {@link GameTestHelper#makeMockServerPlayerInLevel()} defaults to creative mode -- which,
	 * unnoticed, silently defeated three of these tests (they asserted flight started and it never
	 * did) because every Thor flight/ability entry point deliberately leaves creative players alone
	 * (see {@code ThorPowers}' own "leave creative/spectator flight alone" checks -- correct
	 * production behavior, just wrong for a test that wants to exercise the survival path). Anything
	 * testing worthiness-gated abilities should go through this instead of the raw factory method.
	 */
	private static ServerPlayer survivalMockPlayer(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	// ---------------- worthiness ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void worthinessThresholdIsRespected(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();

		Worthiness.setScore(player, Worthiness.THRESHOLD - 1);
		helper.assertFalse(Worthiness.isWorthy(player), "Player scoring just below the threshold should not be worthy");

		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);
		helper.assertTrue(Worthiness.isWorthy(player), "Player at the /thor worthy test score should be worthy");

		helper.succeed();
	}

	// ---------------- binding / Power of Thor ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void bindingGrantsAndRevokesPowerOfThor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);

		ItemStack stack = new ItemStack(ModItems.MJOLNIR);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);

		ThorPowers.toggleBinding(player, stack);
		helper.assertValueEqual(player.getUUID(), stack.get(ModDataComponents.BOUND_OWNER),
				"Binding should stamp the caster's UUID onto the stack");
		helper.assertTrue(ThorPassives.hasPowerOfThor(player), "A bound, worthy player should have the Power of Thor");

		ThorPowers.toggleBinding(player, stack);
		helper.assertTrue(stack.get(ModDataComponents.BOUND_OWNER) == null, "Toggling binding again should release it");
		helper.assertFalse(ThorPassives.hasPowerOfThor(player), "Releasing the binding should revoke the Power of Thor");

		helper.succeed();
	}

	// ---------------- resting-hammer pickup (real entity ticking) ----------------

	@GameTest(template = EMPTY_STRUCTURE, setupTicks = 1)
	public void restingHammerIsPickedUpByWorthyPlayer(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);

		MjolnirEntity hammer = MjolnirEntity.createResting(helper.getLevel(), null,
				new ItemStack(ModItems.MJOLNIR), Vec3.atCenterOf(helper.absolutePos(ORIGIN)), Vec3.ZERO, true);
		helper.getLevel().addFreshEntity(hammer);

		// Let the hammer settle onto solid ground first (its own gravity + onHitBlock, real production
		// physics), then -- past its 40-tick pickup delay -- right-click it ("changes 14": a resting
		// hammer is no longer collected by walking over it, only by interacting with it).
		helper.runAfterDelay(55, () -> {
			Vec3 restPos = hammer.position();
			player.setPos(restPos.x, restPos.y, restPos.z);
			hammer.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
			helper.assertTrue(hammer.isRemoved(), "right-clicking a resting hammer as a worthy player should collect it");
			helper.assertTrue(player.getMainHandItem().is(ModItems.MJOLNIR) || player.getOffhandItem().is(ModItems.MJOLNIR),
					"The collected hammer should end up in the player's hand");
			helper.succeed();
		});
	}

	// ---------------- Q-drop physics regression ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void droppedHammerKeepsItsTossVelocity(GameTestHelper helper) {
		// Regression test for the exact bug fixed this session: settleAsResting() used to
		// unconditionally zero the velocity createResting() was handed, so a Q-drop's normal
		// forward-toss impulse from vanilla drop physics was silently discarded and the hammer fell
		// straight down instead of arcing forward. This asserts the velocity survives the call.
		Vec3 tossVelocity = new Vec3(0.3, 0.2, 0.1);
		MjolnirEntity hammer = MjolnirEntity.createResting(helper.getLevel(), null,
				new ItemStack(ModItems.MJOLNIR), Vec3.atCenterOf(helper.absolutePos(ORIGIN)), tossVelocity);
		helper.getLevel().addFreshEntity(hammer);

		Vec3 actual = hammer.getDeltaMovement();
		helper.assertTrue(actual.distanceToSqr(tossVelocity) < 1.0E-6,
				"createResting() should preserve the handed-in toss velocity, not zero it -- got " + actual);

		helper.succeed();
	}

	// ---------------- hammerless flight grace ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void throwingWhileFlyingGrantsHammerlessGrace(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));

		ThorPowers.toggleFlight(player);
		helper.assertTrue(ThorPowers.isFlying(player), "A worthy player holding Mjolnir with Storm Energy should be able to start flying");

		ThorPowers.throwMjolnir(player);
		helper.assertTrue(player.getMainHandItem().isEmpty(), "Throwing should empty the main hand");
		int graceTicks = player.getAttachedOrElse(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0);
		helper.assertTrue(graceTicks > 0, "Throwing Mjolnir while genuinely flying should grant hammerless-flight grace ticks");
		helper.assertTrue(ThorPowers.isFlying(player), "Flight should continue uninterrupted the instant Mjolnir leaves the hand");

		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void throwingWhileGroundedGrantsNoGrace(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));

		helper.assertFalse(ThorPowers.isFlying(player), "A fresh mock player should not already be flying");
		ThorPowers.throwMjolnir(player);
		int graceTicks = player.getAttachedOrElse(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0);
		helper.assertTrue(graceTicks == 0, "Throwing Mjolnir while not flying must never grant hammerless-flight grace");

		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
	public void hammerlessGraceEndsWhenTimerExpires(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));

		ThorPowers.toggleFlight(player);
		ThorPowers.throwMjolnir(player);
		helper.assertTrue(ThorPowers.isFlying(player), "Should still be flying immediately after the throw");

		// Force a short grace window rather than waiting out the real 300-tick one, then let the
		// REAL globally-registered end-of-server-tick handler (HeroCraftMod's
		// ServerTickEvents.END_SERVER_TICK registration, not a hand-rolled substitute) count it down
		// and end flight on its own -- this exercises the production tick path end-to-end.
		player.setAttached(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 2);

		helper.runAfterDelay(10, () -> {
			helper.assertFalse(ThorPowers.isFlying(player), "Flight should end once the hammerless grace timer reaches zero");
			helper.assertTrue(player.getAttachedOrElse(ModAttachments.HAMMERLESS_FLIGHT_TICKS, 0) == 0,
					"The grace counter should not be left at a stale non-zero value after expiring");
			helper.succeed();
		});
	}

	// ---------------- chain lightning ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void chainLightningHitsMultipleNearbyTargets(GameTestHelper helper) {
		ServerPlayer player = survivalMockPlayer(helper);
		Worthiness.setScore(player, Worthiness.TEST_WORTHY_SCORE);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MJOLNIR));
		Vec3 playerPos = Vec3.atBottomCenterOf(helper.absolutePos(ORIGIN));
		player.setPos(playerPos.x, playerPos.y, playerPos.z);

		Zombie first = helper.spawn(EntityType.ZOMBIE, ORIGIN.offset(0, 0, 3));
		Zombie second = helper.spawn(EntityType.ZOMBIE, ORIGIN.offset(2, 0, 3));
		float firstHealthBefore = first.getHealth();
		float secondHealthBefore = second.getHealth();

		// Aim at eye height, not feet -- aiming at a target's feet from a standing player's own eye
		// height angles the ray downward and can clip test-platform floor blocks between them before
		// it ever reaches the entity, which is exactly what made this raycast miss the first time.
		player.lookAt(EntityAnchorArgument.Anchor.EYES, first.getEyePosition());
		ThorPowers.chainLightningCast(player);

		helper.assertTrue(first.getHealth() < firstHealthBefore, "The crosshair target should take chain lightning damage");
		helper.assertTrue(second.getHealth() < secondHealthBefore, "A nearby valid target should be chained into and damaged too");
		boolean ready = player.getAttachedOrCreate(ModAttachments.COOLDOWNS)
				.isReady(ThorAbility.CHAIN_LIGHTNING, player.level().getGameTime());
		helper.assertFalse(ready, "Chain Lightning should be on cooldown immediately after a successful cast");

		helper.succeed();
	}

	// ---------------- crater spawn-once guarantee ----------------

	@GameTest(template = EMPTY_STRUCTURE)
	public void craterSpawnStateTracksOnce(GameTestHelper helper) {
		CraterSpawnState state = CraterSpawnState.get(helper.getLevel());
		// A random key, not a fixed constant: CraterSpawnState is server-global (keyed on the
		// overworld's own SavedData, not scoped to this test's structure), so a hardcoded key could
		// collide with a previous invocation of this same test within one gametest run (retries) --
		// this was actually observed happening. A fresh random key every run sidesteps that
		// regardless of how many times the framework decides to (re-)invoke this method.
		long key = helper.getLevel().random.nextLong();

		helper.assertFalse(state.hasSpawned(key), "An untouched crater key should read as not-yet-spawned");
		state.markSpawned(key);
		helper.assertTrue(state.hasSpawned(key), "Marking a crater spawned should be reflected immediately");
		state.markSpawned(key);
		helper.assertTrue(state.hasSpawned(key), "Marking the same crater spawned twice must stay idempotent, never un-spawn it");

		helper.succeed();
	}
}
