package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.ElytraPhysics;
import com.projecthero.mod.spider.data.SpiderMoveState;
import com.projecthero.mod.spider.item.SpiderManV2ArmorItem;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: the Spider-Man Suit v2's Web Wings -- the webbed membranes under his arms that let him glide.
 *
 * <p><b>Trigger:</b> HOLD Jump while falling -- airborne, not on a line, not wall-crawling, not hanging, and falling
 * faster than {@link #START_FALL_SPEED} b/t -- for {@link #HOLD_TICKS}+ ticks (a TAP of Jump after a swing release is a
 * swing trick, not this). Needs the Spider-Man power and the v2 suit on the chest (alone or with the full set).
 *
 * <p><b>Flight (v0.16.4, user: "function more like elytra"):</b> {@link #step} is vanilla's elytra flight
 * ({@link com.projecthero.mod.hero.power.ElytraPhysics}) -- look down to dive and gain speed, pull up to trade that speed
 * for height (hold it too long and he stalls and drops), level off to glide a long way while slowly losing height, and
 * turn by looking where he wants to go (the glide eases toward the look 10% a tick; the body banks into the turn).
 * Letting go of Jump ends it with the momentum kept (the air momentum of {@code SpiderAirControl} carries it on);
 * firing a swing from a glide keeps it too. No fall damage while gliding (and Spider-Man takes none anyway).
 *
 * <p>Like the rest of his movement it is client-simulated ({@code client.spider.SpiderWebWingsClient}) and
 * server-authorised: the client asks to start / stop ({@code SpiderGlidePayload}), the server checks {@link #canGlide},
 * owns {@link ModAttachments#SPIDER_WEB_GLIDING} (synced to every viewer, so everyone sees the wings) and, while it is
 * set, keeps his fall distance at zero; the moment the rules fail (he lands, grabs a wall, fires a line) it clears it.
 */
public final class SpiderWebWings {
	public static final int HOLD_TICKS = 6;
	public static final double START_FALL_SPEED = 0.25;
	/** A look turn of this many degrees in a tick reads as a full bank (the visual roll only). */
	public static final float TURN_DEG = 7.0f;

	private SpiderWebWings() {
	}

	/** The Spider-Man Suit v2 is on his chest (alone or with the rest of the set). */
	public static boolean wearingV2(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SpiderManV2ArmorItem;
	}

	/** Synced: is he gliding (every viewer reads this for the wings and the pose)? */
	public static boolean isGliding(Player player) {
		return player.getAttachedOrElse(ModAttachments.SPIDER_WEB_GLIDING, false);
	}

	/** May he be gliding right now (ignoring the Jump hold / fall speed that start it)? */
	public static boolean canGlide(Player player) {
		if (!SpiderMan.hasPower(player) || !wearingV2(player) || player.onGround() || player.isInWater() || player.isInLava()
				|| player.isPassenger() || player.getAbilities().flying || player.isFallFlying() || player.isSpectator()) {
			return false;
		}
		if (SpiderSwing.isSwinging(player) || SpiderClimb.attached(player)) {
			return false;
		}
		return !SpiderMoves.state(player).has(SpiderMoveState.FLAG_HANGING);
	}

	/** Should a glide start: Jump held {@code heldTicks}, falling at {@code vy} b/t, and {@link #canGlide}? */
	public static boolean canStart(Player player, double vy, int heldTicks) {
		return heldTicks >= HOLD_TICKS && vy < -START_FALL_SPEED && canGlide(player);
	}

	/** The velocity a glide starts with: exactly what he had (an elytra opens into the fall it is in). */
	public static Vec3 entryVelocity(Vec3 v, float yawDeg) {
		return v;
	}

	/**
	 * One tick of the glide: from the velocity he moved with last tick, looking along {@code yawDeg} / {@code pitchDeg}
	 * (vanilla's: pitch positive = looking down) -- vanilla elytra flight. Shared by the client simulation and the tests.
	 */
	public static Vec3 step(Vec3 v, float yawDeg, float pitchDeg) {
		return ElytraPhysics.step(v, yawDeg, pitchDeg);
	}

	/** How hard he is banking for a turn of {@code turnDeg} this tick (-1..1, + = turning right). */
	public static float bank(float turnDeg) {
		return Mth.clamp(turnDeg / TURN_DEG, -1f, 1f);
	}

	// ---------------- server ----------------

	/** The client's request ({@code SpiderGlidePayload}). Returns the flag he ends up with. */
	public static boolean request(ServerPlayer player, boolean gliding) {
		boolean ok = gliding && canGlide(player);
		set(player, ok);
		if (ok) {
			player.resetFallDistance();
		}
		return ok;
	}

	private static void set(ServerPlayer player, boolean on) {
		if (isGliding(player) != on) {
			player.setAttached(ModAttachments.SPIDER_WEB_GLIDING, on);
		}
	}

	/** Every server tick: a glide the rules no longer allow ends; one still going takes no fall damage. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player);
		}
	}

	public static void tickPlayer(ServerPlayer player) {
		if (!isGliding(player)) {
			return;
		}
		if (!canGlide(player)) {
			set(player, false);
		}
		player.resetFallDistance(); // gliding -- or a glide that just touched down -- builds no fall damage
	}
}
