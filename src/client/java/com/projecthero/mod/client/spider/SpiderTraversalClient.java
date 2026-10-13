package com.projecthero.mod.client.spider;

import java.util.UUID;

import com.projecthero.mod.network.SpiderActionPayload;
import com.projecthero.mod.spider.SpiderMan;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderMoves;
import com.projecthero.mod.spider.SpiderSwing;
import com.projecthero.mod.spider.data.SpiderMoveState;
import com.projecthero.mod.spider.web.WebGeometry;
import com.projecthero.mod.spider.web.WebStructureEntity;
import com.projecthero.mod.spider.web.WebStructures;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: Spider-Man's key-free traversal on the owning client.
 *
 * <ul>
 *   <li><b>Hold right-click with an empty main hand</b> = Web Swing (release lets go). Not when the crosshair is on an
 *       entity or an interactable block in reach -- doors, chests, buttons and villagers still work.</li>
 *   <li><b>Left-click in Weave Mode</b> = one radial strand (the click never reaches vanilla).</li>
 *   <li><b>Left-click one of his own webs</b> (nearer than any block he aims at) = cut it.</li>
 *   <li>Standing on one of his tightropes he is <b>balanced</b>: drifting off the line is gently corrected.</li>
 *   <li>A web he may NOT use (a stranger's) sticks him fast while he touches it (the server does the same for mobs).</li>
 * </ul>
 */
public final class SpiderTraversalClient {
	private static boolean useWasDown;
	private static boolean rightClickSwing;

	private SpiderTraversalClient() {
	}

	public static void reset() {
		useWasDown = false;
		rightClickSwing = false;
		WebStructures.clearClient();
	}

	/** START of the client tick: claim left-clicks for Weave Mode / cutting before vanilla's key handling sees them. */
	public static void startTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.screen != null || !SpiderMan.hasPower(player) || player.isSpectator()) {
			return;
		}
		boolean weaving = SpiderMoves.state(player).has(SpiderMoveState.FLAG_WEAVING);
		if (weaving) {
			boolean any = false;
			while (client.options.keyAttack.consumeClick()) {
				any = true;
			}
			if (client.options.keyAttack.isDown()) {
				client.options.keyAttack.setDown(false); // no block breaking while weaving
			}
			if (any) {
				ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.WEAVE_STRAND));
				player.swing(InteractionHand.MAIN_HAND);
			}
			return;
		}
		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.ENTITY) {
			return;
		}
		if (!client.options.keyAttack.isDown() || !aimingAtOwnWeb(client, player)) {
			return;
		}
		boolean clicked = false;
		while (client.options.keyAttack.consumeClick()) {
			clicked = true;
		}
		if (clicked) {
			client.options.keyAttack.setDown(false);
			ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.CUT_WEB));
			player.swing(InteractionHand.MAIN_HAND);
		}
	}

	/** True when one of his own webs is on his aim, nearer than the block he is looking at. */
	private static boolean aimingAtOwnWeb(Minecraft client, LocalPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = eye.add(player.getLookAngle().scale(6.0));
		double blockT = 1.0;
		if (client.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
			blockT = bh.getLocation().distanceTo(eye) / 6.0;
		}
		UUID me = player.getUUID();
		for (WebStructureEntity w : WebStructures.in(player.level())) {
			if (!me.equals(w.owner())) {
				continue;
			}
			double t = w.geometry().rayHit(eye, to, 0.35);
			if (t >= 0.0 && t < blockT) {
				return true;
			}
		}
		return false;
	}

	/** END of the client tick. */
	public static void endTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		boolean spider = SpiderMan.hasPower(player);
		// a stranger's web holds him fast (his own and his squad's never do)
		if (!player.isSpectator() && !player.getAbilities().flying) {
			WebStructureEntity trap = WebStructures.trappingWeb(player);
			if (trap != null) {
				double slow = SpiderManConfig.webs().strangerSlowFactor;
				player.makeStuckInBlock(Blocks.STRUCTURE_VOID.defaultBlockState(), new Vec3(slow, 0.04, slow));
			}
		}
		if (!spider || player.isSpectator()) {
			useWasDown = false;
			rightClickSwing = false;
			return;
		}
		handleRightClickSwing(client, player);
		balanceOnRope(player);
	}

	private static void handleRightClickSwing(Minecraft client, LocalPlayer player) {
		boolean down = client.screen == null && client.options.keyUse.isDown();
		boolean pressed = down && !useWasDown;
		useWasDown = down;
		boolean emptyHand = player.getMainHandItem().isEmpty();
		if (rightClickSwing) {
			if (!down || !emptyHand) {
				rightClickSwing = false;
				if (SpiderSwing.isSwinging(player)) {
					ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.SWING_STOP));
				}
			}
			return;
		}
		if (!pressed || !emptyHand || player.isPassenger() || player.isInWater()
				|| SpiderSwing.isSwinging(player) || interactableInReach(client, player)) {
			return;
		}
		rightClickSwing = true;
		ClientPlayNetworking.send(new SpiderActionPayload(SpiderActionPayload.Action.SWING_START));
	}

	/** The crosshair is on an entity, or on a block a right-click would use (door, chest, button, bed, ...). */
	private static boolean interactableInReach(Minecraft client, LocalPlayer player) {
		HitResult hit = client.hitResult;
		if (hit == null || hit.getType() == HitResult.Type.MISS) {
			return false;
		}
		if (hit.getType() == HitResult.Type.ENTITY) {
			return true;
		}
		BlockPos pos = ((BlockHitResult) hit).getBlockPos();
		BlockState state = player.level().getBlockState(pos);
		Block b = state.getBlock();
		return state.hasBlockEntity() || state.getMenuProvider(player.level(), pos) != null
				|| b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock
				|| b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof BedBlock || b instanceof BellBlock
				|| b instanceof NoteBlock || b instanceof RepeaterBlock || b instanceof ComparatorBlock
				|| b instanceof DaylightDetectorBlock || b instanceof CakeBlock || b instanceof ComposterBlock
				|| b instanceof RespawnAnchorBlock;
	}

	/** On his tightrope the line keeps him centred: sideways drift is damped and pulled back over the rope. */
	private static void balanceOnRope(LocalPlayer player) {
		if (!player.onGround() || player.isShiftKeyDown()) {
			return;
		}
		// v0.16.1: at a junction (two or more ropes underfoot) follow the one running most nearly the way he is going
		java.util.List<WebStructureEntity> ropes = WebStructures.ropesUnderfoot(player);
		if (ropes.isEmpty()) {
			return;
		}
		Vec3 heading = player.getDeltaMovement().horizontalDistanceSqr() > 1.0e-3 ? player.getDeltaMovement() : player.getLookAngle();
		WebStructureEntity rope = ropes.get(0);
		double bestAlign = -1.0;
		for (WebStructureEntity r : ropes) {
			Vec3 d = r.geometry().b(0).subtract(r.geometry().a(0));
			Vec3 h = new Vec3(d.x, 0, d.z);
			if (h.lengthSqr() < 1.0e-4) {
				continue;
			}
			double align = Math.abs(h.normalize().dot(new Vec3(heading.x, 0, heading.z).normalize()));
			if (align > bestAlign) {
				bestAlign = align;
				rope = r;
			}
		}
		WebGeometry g = rope.geometry();
		Vec3 a = g.a(0);
		Vec3 b = g.b(0);
		Vec3 along = new Vec3(b.x - a.x, 0, b.z - a.z);
		if (along.lengthSqr() < 1.0e-4) {
			return;
		}
		along = along.normalize();
		Vec3 feet = player.position();
		Vec3 onRope = WebGeometry.closestOnSegment(a, b, feet);
		Vec3 off = new Vec3(feet.x - onRope.x, 0, feet.z - onRope.z);
		Vec3 side = new Vec3(-along.z, 0, along.x);
		double lateral = off.dot(side);
		Vec3 v = player.getDeltaMovement();
		double vSide = v.dot(side);
		// damp the sideways velocity and pull back over the line
		Vec3 fix = side.scale(-vSide * 0.6 - lateral * 0.35);
		player.setDeltaMovement(v.add(fix.x, 0, fix.z));
	}
}
