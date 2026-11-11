package com.projecthero.mod.gametest;

import java.util.HashMap;
import java.util.Map;

import com.projecthero.mod.undead.Undead;
import com.projecthero.mod.undead.UndeadEjectConfig;
import com.projecthero.mod.undead.UndeadFlight;
import com.projecthero.mod.undead.UndeadShoulderSword;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Undead flight speeds + boost burn (user: "Make the flight 10 blocks per second cruise and 20 blocks per second sprint,
 * if player holds right click they should still be able to boost, while boosting they move at 32 blocks per second and it takes
 * 10 second for their body to fully burn away from this and when theyre just a head the player moves at 25 blocks per second").
 * The speeds, the burn's order and its step size (one tick of boosting is 1/200 of the body), the burn kept between boosts, a
 * half-burnt stage lost whole on landing and grown back, and right click doing nothing else while flying. Mock players are not
 * ticked by the server: the burn is driven one step at a time ({@link UndeadFlight#burnTick}).
 */
public class UndeadFlightGameTests implements FabricGameTest {
	private static final float EPS = 1.0e-4f;

	private static ServerPlayer undead(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 3.0, 2.5));
		p.moveTo(at.x, at.y, at.z, 0f, 0f);
		Undead.grant(p);
		return p;
	}

	private static void clearPieces(GameTestHelper helper, ServerPlayer p) {
		helper.getLevel().getEntitiesOfClass(UndeadPartEntity.class, new AABB(p.blockPosition()).inflate(32), e -> e.ownerIs(p)).forEach(e -> e.discard());
	}

	private static float now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theFlightSpeedsAreTenTwentyBoostThirtyTwoHeadTwentyFive(GameTestHelper helper) {
		helper.assertTrue(UndeadEjectConfig.FLIGHT_CRUISE_BPS == 10.0, "10 b/s cruising");
		helper.assertTrue(UndeadEjectConfig.FLIGHT_SPRINT_BPS == 20.0, "20 b/s sprinting");
		helper.assertTrue(UndeadEjectConfig.FLIGHT_BOOST_BPS == 32.0, "32 b/s boosting");
		helper.assertTrue(UndeadEjectConfig.FLIGHT_HEAD_BPS == 25.0, "25 b/s as just a head");
		ServerPlayer p = undead(helper);
		UndeadState whole = Undead.state(p);
		helper.assertTrue(UndeadFlight.bps(whole, now(p), false, false) == 10.0, "cruise 10");
		helper.assertTrue(UndeadFlight.bps(whole, now(p), true, false) == 20.0, "sprint 20");
		helper.assertTrue(UndeadFlight.bps(whole, now(p), false, true) == 32.0 && UndeadFlight.bps(whole, now(p), true, true) == 32.0,
				"boost 32, sprinting or not");
		for (int part = UndeadState.BODY; part < UndeadState.PARTS; part++) {
			Undead.breakPart(p, part, UndeadState.KIND_BURNT);
		}
		UndeadState head = Undead.state(p);
		helper.assertTrue(head.headOnly(now(p)), "just a head");
		helper.assertTrue(UndeadFlight.bps(head, now(p), false, false) == 25.0 && UndeadFlight.bps(head, now(p), true, true) == 25.0,
				"just a head: 25, boosting or not");
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theBoostBurnsLegsThenArmsThenBodyInTenSeconds(GameTestHelper helper) {
		helper.assertTrue(UndeadEjectConfig.BURN_TOTAL_TICKS == 200, "10 s of boosting in all");
		int sum = 0;
		for (int t : UndeadEjectConfig.BURN_STAGE_TICKS) {
			sum += t;
		}
		helper.assertTrue(sum == UndeadEjectConfig.BURN_TOTAL_TICKS && UndeadEjectConfig.BURN_STAGE_TICKS.length == 3, "three stages adding up to 10 s");
		ServerPlayer p = undead(helper);
		helper.assertTrue(UndeadFlight.start(p), "takes off");
		UndeadFlight.setBoost(p, true);
		helper.assertTrue(UndeadFlight.boosting(p), "right click held: boosting");
		int legsAt = -1, armsAt = -1, bodyAt = -1;
		float last = UndeadFlight.burnProgress(Undead.state(p));
		helper.assertTrue(last == 0f, "nothing burnt at the start");
		for (int step = 1; step <= UndeadEjectConfig.BURN_TOTAL_TICKS; step++) {
			UndeadFlight.burnTick(p);
			UndeadState s = Undead.state(p);
			float prog = UndeadFlight.burnProgress(s);
			// step size: every tick of boosting eats exactly 1/200 of the body
			helper.assertTrue(Math.abs((prog - last) - 1f / UndeadEjectConfig.BURN_TOTAL_TICKS) < EPS, "step " + step + " burns 1/200 (" + (prog - last) + ")");
			last = prog;
			boolean legs = s.broken(UndeadState.RIGHT_LEG) && s.broken(UndeadState.LEFT_LEG);
			boolean arms = s.broken(UndeadState.RIGHT_ARM) && s.broken(UndeadState.LEFT_ARM);
			boolean body = s.broken(UndeadState.BODY);
			if (legs && legsAt < 0) {
				legsAt = step;
				helper.assertTrue(!s.broken(UndeadState.RIGHT_ARM) && !body, "the legs go first");
				helper.assertTrue(s.kind(UndeadState.RIGHT_LEG) == UndeadState.KIND_BURNT, "burnt away, not burst");
			}
			if (arms && armsAt < 0) {
				armsAt = step;
				helper.assertTrue(legsAt > 0 && !body, "then the arms");
			}
			if (body && bodyAt < 0) {
				bodyAt = step;
				helper.assertTrue(armsAt > 0, "then the body");
			}
			helper.assertTrue(!s.broken(UndeadState.HEAD), "never the head");
		}
		int[] st = UndeadEjectConfig.BURN_STAGE_TICKS;
		helper.assertTrue(legsAt == st[0], "the legs burn through after " + st[0] + " ticks (" + legsAt + ")");
		helper.assertTrue(armsAt - legsAt == st[1], "the arms " + st[1] + " ticks later (" + (armsAt - legsAt) + ")");
		helper.assertTrue(bodyAt - armsAt == st[2], "the body " + st[2] + " ticks after that (" + (bodyAt - armsAt) + ")");
		UndeadState s = Undead.state(p);
		helper.assertTrue(s.headOnly(now(p)) && UndeadFlight.burnProgress(s) == 1f, "just a head after 200 ticks of boosting");
		UndeadFlight.burnTick(p);
		helper.assertTrue(!Undead.state(p).broken(UndeadState.HEAD), "nothing more burns once he is a head");
		Undead.tick(p); // the flight's own tick: a head burnt down by the boost flies on
		helper.assertTrue(UndeadFlight.isFlying(p), "a head burnt down by the boost keeps flying");
		UndeadFlight.stop(p);
		clearPieces(helper, p);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 240)
	public void theBurnAddsUpOverBoostsAndAHalfBurntStageIsLostOnLandingThenRegrows(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadFlight.start(p);
		UndeadFlight.setBoost(p, true);
		for (int i = 0; i < 30; i++) {
			UndeadFlight.burnTick(p);
		}
		UndeadFlight.setBoost(p, false);
		helper.assertTrue(!UndeadFlight.boosting(p), "let go: not boosting");
		float half = UndeadFlight.burnt(Undead.state(p), UndeadState.RIGHT_LEG);
		helper.assertTrue(Math.abs(half - 30f / UndeadEjectConfig.BURN_STAGE_TICKS[0]) < EPS, "30 ticks into the legs");
		helper.assertTrue(UndeadFlight.burnt(Undead.state(p), UndeadState.RIGHT_ARM) == 0f, "the arms untouched");
		Undead.tick(p); // a flight tick without the boost burns nothing
		helper.assertTrue(Math.abs(UndeadFlight.burnt(Undead.state(p), UndeadState.RIGHT_LEG) - half) < EPS, "kept between boosts");
		UndeadFlight.setBoost(p, true);
		for (int i = 30; i < UndeadEjectConfig.BURN_STAGE_TICKS[0]; i++) {
			UndeadFlight.burnTick(p);
		}
		UndeadState s = Undead.state(p);
		helper.assertTrue(s.broken(UndeadState.RIGHT_LEG) && s.broken(UndeadState.LEFT_LEG), "the second boost finishes the legs (67 ticks in all)");
		for (int i = 0; i < 20; i++) {
			UndeadFlight.burnTick(p);
		}
		helper.assertTrue(UndeadFlight.burnt(Undead.state(p), UndeadState.RIGHT_ARM) > 0f, "the arms half burnt");
		UndeadFlight.stop(p); // landing
		s = Undead.state(p);
		helper.assertTrue(s.broken(UndeadState.RIGHT_ARM) && s.broken(UndeadState.LEFT_ARM) && s.kind(UndeadState.RIGHT_ARM) == UndeadState.KIND_BURNT,
				"a stage only part burnt is lost whole on landing");
		helper.assertTrue(s.ex(UndeadState.X_BURN_TICKS) == 0L && !s.flag(UndeadState.FLAG_BOOST), "the burn starts over");
		helper.assertTrue(!s.broken(UndeadState.BODY), "the body was never reached");
		helper.onEachTick(() -> Undead.tick(p));
		helper.succeedWhen(() -> {
			helper.assertTrue(!Undead.state(p).anyBroken(), "everything burnt grows back by the normal regrowth");
			clearPieces(helper, p);
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void rightClickInFlightIsOnlyTheBoost(GameTestHelper helper) {
		ServerPlayer p = undead(helper);
		UndeadFlight.setBoost(p, true);
		helper.assertTrue(!Undead.state(p).flag(UndeadState.FLAG_BOOST), "no boost on the ground");
		UndeadFlight.start(p);
		UndeadState before = Undead.state(p);
		Map<String, Long> cds = new HashMap<>(before.abilityReadyAt);
		int anim = before.animId;
		int flags = before.flags;
		UndeadFlight.setBoost(p, true);
		UndeadState after = Undead.state(p);
		helper.assertTrue(after.abilityReadyAt.equals(cds), "no move fired (no cooldown started)");
		helper.assertTrue(after.animId == anim, "no move's animation started");
		helper.assertTrue(after.flags == (flags | UndeadState.FLAG_BOOST), "only the boost flag changed");
		helper.assertTrue(after.ex(UndeadState.X_EATEN_R) == before.ex(UndeadState.X_EATEN_R) && after.ex(UndeadState.X_LAST_SHOT) == before.ex(UndeadState.X_LAST_SHOT),
				"no Parts Bullet fired");
		// Shift + right click with a sword is the Shoulder Sword on foot -- not in flight
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		p.setShiftKeyDown(true);
		helper.assertTrue(!UndeadShoulderSword.interact(p), "no Shoulder Sword while flying");
		helper.assertTrue(!UndeadShoulderSword.has(p), "the sword stays in the hand");
		UndeadFlight.setBoost(p, false);
		helper.assertTrue(!Undead.state(p).flag(UndeadState.FLAG_BOOST), "let go");
		UndeadFlight.stop(p);
		p.setShiftKeyDown(false);
		clearPieces(helper, p);
		helper.succeed();
	}
}
