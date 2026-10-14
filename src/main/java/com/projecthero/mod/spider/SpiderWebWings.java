package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
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
 * <p><b>Flight:</b> {@link #step} -- he glides the way he is looking at about {@link #CRUISE} b/t, sinking about
 * {@link #SINK} b/t; turns bank in at most {@link #TURN_DEG} degrees a tick; looking down dives and gains speed (up to
 * {@link #MAX_SPEED}); looking up bleeds it off (never below {@link #MIN_SPEED}, and he always sinks a little). Letting go
 * of Jump ends it; firing a swing from a glide keeps the momentum.
 *
 * <p>Like the rest of his movement it is client-simulated ({@code client.spider.SpiderWebWingsClient}) and
 * server-authorised: the client asks to start / stop ({@code SpiderGlidePayload}), the server checks {@link #canGlide},
 * owns {@link ModAttachments#SPIDER_WEB_GLIDING} (synced to every viewer, so everyone sees the wings) and, while it is
 * set, keeps his fall distance at zero; the moment the rules fail (he lands, grabs a wall, fires a line) it clears it.
 */
public final class SpiderWebWings {
	public static final int HOLD_TICKS = 6;
	public static final double START_FALL_SPEED = 0.25;
	public static final double CRUISE = 0.8;
	public static final double SINK = 0.10;
	public static final double MIN_SPEED = 0.5;
	public static final double MAX_SPEED = 1.6;
	/** The least he sinks, pitched right up (keeps him honestly falling -- vanilla's "floating" check never trips). */
	public static final double MIN_SINK = 0.05;
	public static final double DIVE_ACCEL = 0.06;
	public static final double CLIMB_DRAG = 0.05;
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

	/** The speed a glide starts at, from the velocity he had when it began. */
	public static Vec3 entryVelocity(Vec3 v, float yawDeg) {
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		Vec3 dir = h > 0.05 ? new Vec3(v.x / h, 0, v.z / h) : Vec3.directionFromRotation(0f, yawDeg);
		// a fall turns into forward speed: what he had, or a fair share of his drop
		double s = Mth.clamp(Math.max(h, -v.y * 0.8), MIN_SPEED, MAX_SPEED);
		return new Vec3(dir.x * s, -SINK, dir.z * s);
	}

	/**
	 * One tick of the glide: from velocity {@code v}, looking along {@code yawDeg} / {@code pitchDeg} (vanilla's: pitch
	 * positive = looking down). Shared by the client simulation and the tests.
	 */
	public static Vec3 step(Vec3 v, float yawDeg, float pitchDeg) {
		double h = Math.sqrt(v.x * v.x + v.z * v.z);
		float heading = h > 1.0e-3 ? (float) (Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG) : yawDeg;
		float turn = Mth.clamp(Mth.wrapDegrees(yawDeg - heading), -TURN_DEG, TURN_DEG);
		heading += turn;
		float pitch = Mth.clamp(pitchDeg, -40f, 80f);
		double dive = Math.sin(Math.max(pitch, 0f) * Mth.DEG_TO_RAD);
		double climb = Math.sin(Math.max(-pitch, 0f) * Mth.DEG_TO_RAD);
		double s = Math.max(h, MIN_SPEED);
		s += (CRUISE - s) * 0.04 * (1.0 - dive) + dive * DIVE_ACCEL - climb * CLIMB_DRAG;
		s = Mth.clamp(s, MIN_SPEED, MAX_SPEED);
		double vy = -(SINK + dive * s * 0.55) + climb * 0.05;
		vy = Math.min(vy, -MIN_SINK);
		Vec3 dir = Vec3.directionFromRotation(0f, heading);
		return new Vec3(dir.x * s, vy, dir.z * s);
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
