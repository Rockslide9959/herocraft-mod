package com.projecthero.mod.gametest;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixRiding;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 rides ({@link OmnitrixRiding}): anyone right-clicking Wildmutt climbs onto his back (seated behind his middle, turned
 * with him, safe from his own moves and from falls, off with Sneak, thrown off by Quill Roll or when he turns back); Grey Matter
 * right-clicking a human hops onto the shoulder nearer the click (a second one takes the other shoulder), not onto an alien, not
 * while sneaking (that is still Quick Read), off with Sneak or when he turns back. The clicks go through the real use-entity hook.
 */
public class OmnitrixRidingGameTests implements FabricGameTest {
	private static ServerPlayer mock(GameTestHelper helper, double x, double y, double z) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
		p.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		p.yBodyRotO = 0f;
		try {
			java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
			f.setAccessible(true);
			f.setInt(p, 0);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return p;
	}

	private static ServerPlayer alien(GameTestHelper helper, String id, double x, double z) {
		ServerPlayer p = mock(helper, x, 2, z);
		helper.assertTrue(Omnitrix.grant(p), "the Omnitrix goes on");
		Omnitrix.select(p, id);
		helper.assertTrue(Omnitrix.transform(p), "transformed into " + id);
		p.setOnGround(true);
		p.refreshDimensions();
		p.yBodyRot = 0f;
		p.yBodyRotO = 0f;
		return p;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x <= 7; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
			}
		}
	}

	private static InteractionResult click(ServerPlayer who, ServerPlayer target, Vec3 at) {
		EntityHitResult hit = at == null ? null : new EntityHitResult(target, at);
		return UseEntityCallback.EVENT.invoker().interact(who, who.level(), InteractionHand.MAIN_HAND, target, hit);
	}

	private static void sneakOff(ServerPlayer rider) {
		rider.setShiftKeyDown(true);
		rider.rideTick(); // vanilla Player.rideTick: a sneaking passenger gets off
		rider.setShiftKeyDown(false);
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void anyoneRightClickingWildmuttRidesHisBackFacingHisWayAndSneakGetsOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer wild = alien(helper, WildmuttKit.ID, 3.5, 3.5);
		ServerPlayer rider = mock(helper, 3.5, 2, 5.5);
		{
			helper.assertTrue(click(rider, wild, null).consumesAction(), "the click is taken");
			helper.assertTrue(rider.getVehicle() == wild, "the rider is on his back");
			helper.assertTrue(OmnitrixRiding.ridingWildmutt(rider), "and knows it (the riding pose)");
			helper.assertTrue(OmnitrixRiding.riding(rider) == OmnitrixRiding.Kind.WILDMUTT, "the server tracks the ride");
			helper.assertTrue(click(rider, wild, null).consumesAction() && rider.getVehicle() == wild, "the click's second packet is a no-op");

			wild.yBodyRot = 0f; // facing +Z: his back is -Z
			Vec3 seat = wild.getPassengerRidingPosition(rider).subtract(wild.position());
			helper.assertTrue(seat.z < -0.1 && Math.abs(seat.x) < 0.05 && seat.y > 1.2 && seat.y < 1.6, "seated on his back, behind his middle: " + seat);
			wild.yBodyRot = 90f; // facing -X: the seat turns with him
			seat = wild.getPassengerRidingPosition(rider).subtract(wild.position());
			helper.assertTrue(seat.x > 0.1 && Math.abs(seat.z) < 0.05, "turned west the seat turns too: " + seat);
			wild.positionRider(rider);
			helper.assertTrue(Math.abs(rider.yBodyRot - wild.yBodyRot) < 0.01f, "the rider faces the way he does");

			ServerPlayer other = mock(helper, 1.5, 2, 3.5);
			click(other, wild, null);
			helper.assertTrue(other.getVehicle() == null, "one rider at a time");
			helper.assertFalse(AlienKit.around(wild, wild.position(), 4).contains(rider), "his own area moves never catch his rider");

			float hp = rider.getHealth();
			rider.hurt(rider.damageSources().fall(), 6f);
			helper.assertTrue(rider.getHealth() == hp, "no fall damage while riding");

			sneakOff(rider);
			helper.assertTrue(rider.getVehicle() == null, "Sneak gets off");
			// the per-tick check notices the rider got off: still safe from a fall for a moment
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(OmnitrixRiding.riding(rider) == null, "the ride record is gone");
				float hp2 = rider.getHealth();
				rider.hurt(rider.damageSources().fall(), 6f);
				helper.assertTrue(rider.getHealth() == hp2, "no fall damage just after getting off");
				helper.succeed();
			});
		}
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void wildmuttsRiderIsThrownByQuillRollAndOffWhenHeTurnsBack(GameTestHelper helper) {
		floor(helper);
		ServerPlayer wild = alien(helper, WildmuttKit.ID, 3.5, 3.5);
		ServerPlayer rider = mock(helper, 3.5, 2, 5.5);
		WildmuttKit kit = (WildmuttKit) Aliens.kit(WildmuttKit.ID);
		helper.assertTrue(OmnitrixRiding.mountWildmutt(rider, wild) && rider.getVehicle() == wild, "on his back");
		helper.assertTrue(kit.roll(wild), "Quill Roll");
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(rider.getVehicle() == null, "Quill Roll throws the rider off");
			kit.setFlag(wild, WildmuttKit.F_ROLL, false);
			helper.assertTrue(OmnitrixRiding.mountWildmutt(rider, wild) && rider.getVehicle() == wild, "back on");
			Omnitrix.revert(wild, Omnitrix.Reason.REVOKE);
			helper.assertTrue(rider.getVehicle() == null, "off when he turns back");
			ServerPlayer wild2 = alien(helper, WildmuttKit.ID, 5.5, 3.5);
			ServerPlayer mutt = alien(helper, WildmuttKit.ID, 1.5, 3.5);
			helper.assertFalse(OmnitrixRiding.mountWildmutt(mutt, wild2), "a Wildmutt does not ride a Wildmutt");
			helper.succeed();
		});
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void greyMatterHopsOntoTheShoulderNearerHisClickAndGetsOff(GameTestHelper helper) {
		floor(helper);
		ServerPlayer human = mock(helper, 3.5, 2, 3.5);
		ServerPlayer gm = alien(helper, GreyMatterKit.ID, 3.5, 5.0);
		ServerPlayer gm2 = alien(helper, GreyMatterKit.ID, 2.0, 5.0);
		human.yBodyRot = 0f; // facing +Z: his left is +X
		// sneaking: Quick Read, no ride
		gm.setShiftKeyDown(true);
		click(gm, human, human.position().add(0.3, 1.4, 0));
		gm.setShiftKeyDown(false);
		helper.assertTrue(gm.getVehicle() == null, "Sneak + use on a player is still Quick Read");

		helper.assertTrue(click(gm, human, human.position().add(0.3, 1.4, 0)).consumesAction(), "the click is taken");
		helper.assertTrue(gm.getVehicle() == human, "Grey Matter rides the human");
		helper.assertTrue(OmnitrixRiding.onShoulder(gm) && OmnitrixRiding.leftShoulder(gm), "on the LEFT shoulder (clicked on that side)");
		Vec3 seat = human.getPassengerRidingPosition(gm).subtract(human.position());
		helper.assertTrue(seat.x > 0.3 && seat.x < 0.45 && seat.y > 1.3 && seat.y < 1.45, "on top of his left shoulder: " + seat);
		human.yBodyRot = 90f; // facing -X (west): his left is +Z (south)
		seat = human.getPassengerRidingPosition(gm).subtract(human.position());
		helper.assertTrue(seat.z > 0.3 && Math.abs(seat.x) < 0.1, "stays on the same shoulder as he turns: " + seat);
		human.positionRider(gm);
		helper.assertTrue(Math.abs(gm.yBodyRot - human.yBodyRot) < 0.01f, "Grey Matter faces the way he does");
		human.yBodyRot = 0f;

		helper.assertTrue(click(gm2, human, human.position().add(0.3, 1.4, 0)).consumesAction() && gm2.getVehicle() == human,
				"a second Grey Matter fits");
		helper.assertFalse(OmnitrixRiding.leftShoulder(gm2), "on the other (right) shoulder");
		sneakOff(gm2);
		helper.assertTrue(gm2.getVehicle() == null, "Sneak gets off");

		// never onto an alien
		ServerPlayer wild = alien(helper, WildmuttKit.ID, 6.0, 1.5);
		ServerPlayer heat = alien(helper, "heatblast", 1.5, 1.5);
		click(gm2, heat, null);
		helper.assertTrue(gm2.getVehicle() == null, "not onto an alien's shoulder");
		click(gm2, wild, null);
		helper.assertTrue(gm2.getVehicle() == wild, "but he can ride Wildmutt's back like anyone");
		gm2.stopRiding();

		Omnitrix.revert(gm, Omnitrix.Reason.REVOKE);
		helper.assertTrue(gm.getVehicle() == null, "off the shoulder when he turns back");
		helper.succeed();
	}
}
