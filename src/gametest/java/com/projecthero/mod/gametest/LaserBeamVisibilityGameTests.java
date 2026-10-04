package com.projecthero.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import com.projecthero.mod.hero.AbilityRouter;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.p02.LaserBeams;
import com.projecthero.mod.hero.power.p02.LaserVisionHandlers;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.network.LaserBeamPayload;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Bug fix: "when lasers shoot at a player sometimes they can't see the lasers being shot at them".
 *
 * <p>Laser Vision beams are drawn client-side from the shooter's synced animation, which only reaches clients that
 * track the shooter -- and a viewer's tracking range follows their render distance, often less than the beams'
 * 100 blocks. Those viewers (and everyone near a Laser Vision boss, whose beam was only a particle line) now get an
 * explicit {@link LaserBeamPayload}. Also: a stance pose (carry / rush) no longer replaces a held beam's animation.
 * The rendering itself cannot be gametested; these tests pin down who is sent what.
 */
public class LaserBeamVisibilityGameTests implements FabricGameTest {
	private static ServerPlayer hero(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		BlockPos base = BlockPos.containing(at);
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, 0, -1), base.offset(3, 3, 3))) {
			helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
		}
		for (BlockPos pos : BlockPos.betweenClosed(base.offset(-1, -1, -1), base.offset(3, -1, 3))) {
			helper.getLevel().setBlock(pos, Blocks.STONE.defaultBlockState(), 2);
		}
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYRot(0);
		p.setXRot(0);
		p.setYHeadRot(0);
		Power power = Powers.byKey(LaserVisionHandlers.KEY);
		ExperimentalPowers.grant(p, power);
		ExperimentalPowers.setActive(p, power);
		p.setOnGround(true);
		return p;
	}

	private record Sent(ServerPlayer to, LaserBeamPayload payload) {
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void recipientsAreUntrackedPlayersNearTheBeam(GameTestHelper helper) {
		ServerPlayer shooter = hero(helper);
		Vec3 a = shooter.getEyePosition();
		Vec3 b = a.add(0, 0, 100);
		ServerPlayer near = helper.makeMockServerPlayerInLevel();
		near.moveTo(a.x + 3, a.y - 1.6, a.z + 70, 180f, 0f); // being shot at, 70 blocks down the beam
		ServerPlayer far = helper.makeMockServerPlayerInLevel();
		far.moveTo(a.x + 400, a.y, a.z, 0f, 0f);

		List<ServerPlayer> untracked = LaserBeams.recipients(helper.getLevel(), shooter, a, b, Set.of());
		helper.assertTrue(untracked.contains(near), "a player near the beam who does not track the shooter is sent it");
		helper.assertFalse(untracked.contains(shooter), "never the shooter (they draw their own beam)");
		helper.assertFalse(untracked.contains(far), "nobody 400 blocks from the beam");

		List<ServerPlayer> tracked = LaserBeams.recipients(helper.getLevel(), shooter, a, b, Set.of(near));
		helper.assertFalse(tracked.contains(near), "a player tracking the shooter draws the synced beam, no payload");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv_visibility_pierce")
	public void piercingBlastIsSentToAViewerOutsideTrackingRange(GameTestHelper helper) {
		ServerPlayer shooter = hero(helper);
		Vec3 eye = shooter.getEyePosition();
		// the target stands 60 blocks down the line of fire: past a mock player's 2-chunk tracking range
		ServerPlayer target = helper.makeMockServerPlayerInLevel();
		target.setGameMode(GameType.SURVIVAL);
		target.moveTo(eye.x + 1.0, eye.y - 1.62, eye.z + 60, 180f, 0f);
		// whether a fresh mock player already tracks the shooter depends on where it spawned (tracking is only
		// re-evaluated on a server tick): either way it must end up seeing the beam exactly once
		boolean tracks = PlayerLookup.tracking(shooter).contains(target);

		List<Sent> sent = new ArrayList<>();
		BiConsumer<ServerPlayer, LaserBeamPayload> prev = LaserBeams.swapSender((to, payload) -> sent.add(new Sent(to, payload)));
		try {
			shooter.setShiftKeyDown(true);
			AbilityRouter.handleInput(shooter, 1, true); // Shift+R Piercing Blast
			AbilityRouter.handleInput(shooter, 1, false);
			shooter.setShiftKeyDown(false);
		} finally {
			LaserBeams.swapSender(prev);
		}
		helper.assertTrue(LaserVisionHandlers.ANIM_PIERCE.equals(MutationVisuals.anim(shooter)),
				"trackers get the synced Piercing Blast animation the renderer draws from");
		helper.assertTrue(sent.stream().noneMatch(s -> s.to() == shooter), "the shooter is not sent their own beam");
		Sent toTarget = sent.stream().filter(s -> s.to() == target).findFirst().orElse(null);
		if (tracks) {
			helper.assertTrue(toTarget == null, "a tracking viewer draws the synced beam -- no duplicate payload");
			helper.succeed();
			return;
		}
		helper.assertTrue(toTarget != null, "the player being shot, out of tracking range, is sent the beam");
		helper.assertTrue(toTarget.payload().kind() == LaserBeamPayload.KIND_PIERCE, "as a Piercing Blast");
		helper.assertTrue(toTarget.payload().ticks() == LaserVisionHandlers.oneShotTicks(LaserVisionHandlers.ANIM_PIERCE),
				"for as long as the shooter's own clients show it");
		helper.assertTrue(toTarget.payload().start().distanceTo(eye) < 0.01, "from the shooter's eyes");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv_visibility_hold")
	public void heldBeamRefreshesForAViewerOutsideTrackingRange(GameTestHelper helper) {
		ServerPlayer shooter = hero(helper);
		Vec3 eye = shooter.getEyePosition();
		ServerPlayer target = helper.makeMockServerPlayerInLevel();
		target.moveTo(eye.x - 2.0, eye.y - 1.62, eye.z + 55, 180f, 0f);
		boolean tracks = PlayerLookup.tracking(shooter).contains(target);

		List<Sent> sent = new ArrayList<>();
		BiConsumer<ServerPlayer, LaserBeamPayload> prev = LaserBeams.swapSender((to, payload) -> sent.add(new Sent(to, payload)));
		try {
			AbilityRouter.handleInput(shooter, 1, true); // hold R
			for (int i = 0; i < 4; i++) {
				ExperimentalPowers.serverTick(shooter);
			}
			AbilityRouter.handleInput(shooter, 1, false);
		} finally {
			LaserBeams.swapSender(prev);
		}
		long toTarget = sent.stream().filter(s -> s.to() == target && s.payload().kind() == LaserBeamPayload.KIND_BEAM).count();
		if (tracks) {
			helper.assertTrue(toTarget == 0, "a tracking viewer draws the synced beam -- no duplicate payloads");
			helper.succeed();
			return;
		}
		helper.assertTrue(toTarget >= 4, "a held beam is refreshed every tick for the untracked viewer, got " + toTarget);
		helper.assertTrue(sent.stream().allMatch(s -> s.payload().ticks() == LaserBeamPayload.REFRESH_TICKS),
				"each refresh lives just long enough to bridge to the next");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bossBeamsGoToEveryoneNearby(GameTestHelper helper) {
		Zombie boss = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Vec3 a = boss.getEyePosition();
		player.moveTo(a.x, a.y - 1.62, a.z + 10, 180f, 0f);
		// a boss has no synced beam at all, so even its trackers must be sent the payload
		List<ServerPlayer> to = LaserBeams.recipients(helper.getLevel(), boss, a, player.getEyePosition());
		helper.assertTrue(to.contains(player), "the player a boss shoots is sent its beam");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE, batch = "lv_visibility_stance")
	public void stanceNeverReplacesAHeldBeam(GameTestHelper helper) {
		ServerPlayer shooter = hero(helper);
		AbilityRouter.handleInput(shooter, 1, true); // hold R
		helper.assertTrue(LaserVisionHandlers.ANIM_BEAM.equals(MutationVisuals.anim(shooter)), "the beam animation plays");
		MutationVisuals.advanceAnimation(shooter, 60); // the beam has been held for 3 s
		BatchA.stance(shooter, "power_01_super_strength", "carry_overhead");
		BatchA.stance(shooter, "power_04_super_speed", "p04.carry");
		helper.assertTrue(LaserVisionHandlers.ANIM_BEAM.equals(MutationVisuals.anim(shooter)),
				"a carry stance must not replace the beam animation the renderer draws from, got " + MutationVisuals.anim(shooter));
		helper.assertTrue(BatchA.isChannel(LaserVisionHandlers.ANIM_MAX) && BatchA.isChannel(LaserVisionHandlers.ANIM_MAX_CHARGE),
				"Maximum Output and its charge-up are channels too");
		AbilityRouter.handleInput(shooter, 1, false);
		helper.succeed();
	}
}
