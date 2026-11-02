package com.projecthero.mod.omnitrix;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.greymatter.GreyMatterKit;
import com.projecthero.mod.omnitrix.alien.wildmutt.WildmuttKit;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: players riding Omnitrix players.
 * <ul>
 * <li><b>Wildmutt's back</b> (user: "make it so that players can ride wildmutts back if they right click him ... they follow the
 * direction he is looking in"): anyone but another Wildmutt right-clicks him and climbs on astride, just behind his shoulders.
 * The rider is carried wherever he goes, their body faces his heading and their view turns with him (they can still look round,
 * up to 120 degrees off his facing). One rider at a time; Quill Roll throws them off.</li>
 * <li><b>Grey Matter's shoulder ride</b> (user: "grey matter players can ride regular players by right clicking them, he then
 * sits on their shoulder"): Grey Matter right-clicks a player who is not an alien (and not the Hulk) and hops onto the shoulder
 * nearer his click (the right by default; the other one if a second Grey Matter already sits there). He turns with their body
 * and looks round freely. Sneak + right-click still gives Quick Read.</li>
 * </ul>
 * Both: Sneak gets off (vanilla {@code Player.rideTick}); a ride ends when either one changes alien or back, dies, logs out or
 * changes dimension; the rider takes no fall damage while riding or for {@value #SAFE_TICKS} ticks after getting off. The seats
 * are {@link #seat} ({@code EntityOmnitrixRideMixin}, both sides, worked out from the synced Omnitrix state so every client
 * agrees); the vehicle's own client is told its passengers by {@code EntityHulkRideMixin}. Static state is dropped through
 * {@code OmnitrixMod.clearSessionState}.
 */
public final class OmnitrixRiding {
	/** Wildmutt's seat: the rider's hips sit on his back ~1.5 blocks up, a little behind his middle (behind the front quills). */
	public static final float WILDMUTT_SEAT_Y = 1.40f, WILDMUTT_SEAT_BACK = 0.24f;
	/** The shoulder seat as a fraction of the carrier's box: the top of the arm (1.375 of 1.8) and 0.37 of 0.6 out to the side. */
	public static final float SHOULDER_Y = 0.764f, SHOULDER_OUT = 0.62f, SHOULDER_FWD = 0.02f;
	/** No fall damage for this long after getting off (mid-leap, say). */
	public static final int SAFE_TICKS = 100;
	/** The Wildmutt rider's view stays within this many degrees of his facing. */
	public static final float LOOK_LIMIT = 120.0f;

	public enum Kind {
		WILDMUTT, SHOULDER
	}

	private record Ride(UUID host, Kind kind) {
	}

	/** rider -> who they ride and how. */
	private static final Map<UUID, Ride> RIDES = new ConcurrentHashMap<>();
	/** a rider who just got off -> no fall damage until this game time. */
	private static final Map<UUID, Long> SAFE_UNTIL = new ConcurrentHashMap<>();

	private OmnitrixRiding() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(OmnitrixRiding::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p
				&& source.is(DamageTypeTags.IS_FALL) && safe(p)));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				endRides(p);
			}
		});
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> endRides(player));
		// before PlayerList.remove: a player riding another PLAYER who logs out must get off first -- vanilla "removes the mount"
		// of a lone player passenger, and that mount is a player here (it would be dropped from the world)
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer p = handler.getPlayer();
			if (p != null) {
				endRides(p);
				if (p.getVehicle() instanceof Player) {
					p.stopRiding();
				}
			}
		});
	}

	public static void clearSessionState() {
		RIDES.clear();
		SAFE_UNTIL.clear();
	}

	// ---------------------------------------------------------------- who rides how (both sides)

	/** Is {@code p} Wildmutt right now? Client-safe. */
	public static boolean isWildmutt(@Nullable Entity p) {
		return p instanceof Player pl && AlienKit.activeKit(pl) instanceof WildmuttKit;
	}

	/** Is {@code p} Grey Matter right now? Client-safe. */
	public static boolean isGreyMatter(@Nullable Entity p) {
		return p instanceof Player pl && AlienKit.activeKit(pl) instanceof GreyMatterKit;
	}

	/** A player Grey Matter can sit on: not an alien, not the Hulk. Client-safe. */
	public static boolean shoulderHost(@Nullable Entity host) {
		return host instanceof Player h && AlienKit.activeKit(h) == null && !com.projecthero.mod.hulk.Hulk.isHulk(h);
	}

	/** Is {@code rider} sitting on Wildmutt's back? Client-safe (the riding pose). */
	public static boolean ridingWildmutt(Entity rider) {
		return rider instanceof Player && isWildmutt(rider.getVehicle());
	}

	/** Is {@code rider} Grey Matter sitting on a player's shoulder? Client-safe (the shoulder pose). */
	public static boolean onShoulder(Entity rider) {
		return isGreyMatter(rider) && !isWildmutt(rider.getVehicle()) && shoulderHost(rider.getVehicle());
	}

	/** Which shoulder (true = the carrier's left), from Grey Matter's synced kit flag. */
	public static boolean leftShoulder(Player gm) {
		AlienKit k = AlienKit.activeKit(gm);
		return k != null && k.flag(gm, GreyMatterKit.F_LEFT_SHOULDER);
	}

	/**
	 * Where {@code passenger} sits on {@code vehicle} (relative to its feet, turned with its body), or null for vanilla's seat.
	 * From {@code EntityOmnitrixRideMixin}, both sides.
	 */
	@Nullable
	public static Vec3 seat(Entity vehicle, Entity passenger, EntityDimensions dims) {
		if (com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit.insideHost(passenger)) {
			// v0.17.2: Ghostfreak's possession -- inside the host's body (a mob's, or a player's), his eyes at its eyes
			return new Vec3(0.0, Math.max(0.0, vehicle.getEyeHeight() - passenger.getEyeHeight()), 0.0);
		}
		if (!(vehicle instanceof Player host)) {
			return null;
		}
		float yaw = host.yBodyRot * Mth.DEG_TO_RAD;
		if (isWildmutt(host)) {
			// crouched he is lower (1.3 of 1.55)
			float y = WILDMUTT_SEAT_Y * dims.height() / WildmuttKit.BODY_HEIGHT;
			return new Vec3(0.0, y, -WILDMUTT_SEAT_BACK).yRot(-yaw);
		}
		if (passenger instanceof Player gm && isGreyMatter(gm) && shoulderHost(host)) {
			float side = leftShoulder(gm) ? 1f : -1f; // local +x is the carrier's left
			Pose pose = host.getPose();
			if (pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK) {
				// lying flat: he clings to the upper back
				return new Vec3(side * 0.18, dims.height() * 0.9, -0.05).yRot(-yaw);
			}
			float scale = dims.height() / (pose == Pose.CROUCHING ? 1.5f : 1.8f);
			double y = 1.8f * scale * SHOULDER_Y;
			double fwd = SHOULDER_FWD * scale;
			if (pose == Pose.CROUCHING) {
				// the crouch tips the shoulders down and forward
				y -= 0.27 * scale;
				fwd += 0.28 * scale;
			}
			return new Vec3(side * dims.width() * SHOULDER_OUT, y, fwd).yRot(-yaw);
		}
		// v0.17.2 (XLR8 revamp): Rescue Carry -- the player he scooped up rides on his back
		return com.projecthero.mod.omnitrix.alien.xlr8.Xlr8Kit.carrySeat(host, passenger, dims);
	}

	/**
	 * Both sides, right after the vehicle placed its rider ({@code EntityOmnitrixRideMixin}): the rider's body faces the way the
	 * carrier's does, and the rider's own camera turns with the carrier's body (they can still look round).
	 */
	public static void carryRider(Player host, Entity passenger) {
		if (!(passenger instanceof LivingEntity rider) || com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit.insideHost(passenger)) {
			return; // v0.17.2: a possessing Ghostfreak steers the body he is in; it never turns him
		}
		boolean wild = isWildmutt(host);
		if (!wild && !(passenger instanceof Player p0 && isGreyMatter(p0) && shoulderHost(host))) {
			return;
		}
		rider.yBodyRot = host.yBodyRot;
		rider.yBodyRotO = host.yBodyRotO;
		if (rider instanceof Player p && p.isLocalPlayer()) {
			float turn = Mth.wrapDegrees(host.yBodyRot - host.yBodyRotO);
			if (turn != 0f) {
				p.setYRot(p.getYRot() + turn);
				p.yRotO += turn;
				p.setYHeadRot(p.getYHeadRot() + turn);
			}
			if (wild) {
				clampLook(host, p);
			}
		}
	}

	/** The Wildmutt rider's view stays within {@value #LOOK_LIMIT} degrees of his facing. */
	public static void clampLook(LivingEntity host, Entity rider) {
		if (!isWildmutt(host) || com.projecthero.mod.omnitrix.alien.ghostfreak.GhostfreakKit.insideHost(rider)) {
			return;
		}
		float rel = Mth.wrapDegrees(rider.getYRot() - host.yBodyRot);
		float clamped = Mth.clamp(rel, -LOOK_LIMIT, LOOK_LIMIT);
		if (clamped != rel) {
			float fix = clamped - rel;
			rider.yRotO += fix;
			rider.setYRot(rider.getYRot() + fix);
			rider.setYHeadRot(rider.getYRot());
		}
	}

	// ---------------------------------------------------------------- getting on (server)

	/**
	 * Right-click on a player, from anyone ({@code OmnitrixMod}'s use-entity hook, before the clicker's own alien kit). Returns
	 * PASS when it was not a ride (so Quick Read, a held item and the rest still happen).
	 */
	public static InteractionResult onUse(ServerPlayer clicker, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (!(target instanceof ServerPlayer host) || host == clicker || clicker.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (clicker.getVehicle() == host) {
			return InteractionResult.SUCCESS; // the same click's second packet (interact-at, then interact, then the off hand)
		}
		if (hand != InteractionHand.MAIN_HAND || clicker.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (isWildmutt(host)) {
			return mountWildmutt(clicker, host) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (isGreyMatter(clicker) && !com.projecthero.mod.hero.PowerSuppression.isSuppressed(clicker) && shoulderHost(host)) {
			boolean left = hit != null && hit.getLocation().subtract(host.position()).dot(leftOf(host)) > 0.02;
			return shoulder(clicker, host, left) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return InteractionResult.PASS;
	}

	/** The carrier's left, horizontally (local +x of the seat). */
	private static Vec3 leftOf(LivingEntity host) {
		float yaw = host.yBodyRot * Mth.DEG_TO_RAD;
		return new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
	}

	/** {@code rider} climbs onto Wildmutt {@code host}'s back. Returns false if the click should fall through (not a ride). */
	public static boolean mountWildmutt(ServerPlayer rider, ServerPlayer host) {
		if (rider == host || isWildmutt(rider) || rider.isSpectator() || host.isSpectator() || !host.isAlive() || !rider.isAlive()) {
			return false;
		}
		WildmuttKit k = (WildmuttKit) AlienKit.activeKit(host);
		if (!host.getPassengers().isEmpty()) {
			rider.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ride.full").withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		if (k.flag(host, WildmuttKit.F_ROLL) || k.flag(host, WildmuttKit.F_POUNCE) || k.flag(host, WildmuttKit.F_COIL)) {
			rider.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ride.busy").withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		if (rider.isPassenger()) {
			rider.stopRiding();
		}
		if (!rider.startRiding(host, true)) {
			return false;
		}
		RIDES.put(rider.getUUID(), new Ride(host.getUUID(), Kind.WILDMUTT));
		rider.resetFallDistance();
		rider.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ride.wildmutt_on").withStyle(ChatFormatting.GREEN), true);
		host.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ride.wildmutt_host", rider.getDisplayName())
				.withStyle(ChatFormatting.GOLD), true);
		host.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.HORSE_SADDLE, SoundSource.PLAYERS, 0.6f, 0.8f);
		OmnitrixSounds.play((net.minecraft.server.level.ServerLevel) host.level(), host.position(), WildmuttKit.GROWL, 0.5f, 1.3f);
		return true;
	}

	/** Grey Matter {@code gm} hops onto {@code host}'s shoulder ({@code left} = their left, if free). */
	public static boolean shoulder(ServerPlayer gm, ServerPlayer host, boolean left) {
		if (gm == host || !isGreyMatter(gm) || !shoulderHost(host) || host.isSpectator() || host.isSleeping() || !host.isAlive() || !gm.isAlive()) {
			return false;
		}
		boolean leftTaken = false, rightTaken = false;
		for (Entity e : host.getPassengers()) {
			if (e instanceof Player o && isGreyMatter(o)) {
				if (leftShoulder(o)) {
					leftTaken = true;
				} else {
					rightTaken = true;
				}
			} else {
				leftTaken = rightTaken = true; // someone else rides them
			}
		}
		if (left ? leftTaken : rightTaken) {
			left = !left;
		}
		if (left ? leftTaken : rightTaken) {
			gm.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ride.shoulder_full").withStyle(ChatFormatting.GRAY), true);
			return true;
		}
		GreyMatterKit k = (GreyMatterKit) AlienKit.activeKit(gm);
		k.setFlag(gm, GreyMatterKit.F_LEFT_SHOULDER, left); // before the seat is worked out
		if (gm.isPassenger()) {
			gm.stopRiding();
		}
		if (!gm.startRiding(host, true)) {
			return false;
		}
		RIDES.put(gm.getUUID(), new Ride(host.getUUID(), Kind.SHOULDER));
		gm.resetFallDistance();
		gm.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ride.shoulder_on", host.getDisplayName())
				.withStyle(ChatFormatting.AQUA), true);
		host.displayClientMessage(Component.translatable("message.projecthero.omnitrix.ride.shoulder_host").withStyle(ChatFormatting.AQUA), true);
		OmnitrixSounds.play((net.minecraft.server.level.ServerLevel) host.level(), host.position(), GreyMatterKit.LEAP, 0.6f, 1.5f);
		return true;
	}

	// ---------------------------------------------------------------- keeping it up / getting off (server)

	/** Is {@code rider} on one of these rides right now (the server's record)? */
	@Nullable
	public static Kind riding(ServerPlayer rider) {
		Ride r = RIDES.get(rider.getUUID());
		return r != null && rider.getVehicle() != null && rider.getVehicle().getUUID().equals(r.host()) ? r.kind() : null;
	}

	private static boolean safe(ServerPlayer p) {
		if (RIDES.containsKey(p.getUUID())) {
			return true;
		}
		Long until = SAFE_UNTIL.get(p.getUUID());
		if (until == null) {
			return false;
		}
		if (until <= p.level().getGameTime()) {
			SAFE_UNTIL.remove(p.getUUID());
			return false;
		}
		return true;
	}

	private static void protect(ServerPlayer rider) {
		rider.resetFallDistance();
		SAFE_UNTIL.put(rider.getUUID(), rider.level().getGameTime() + SAFE_TICKS);
	}

	/** Ends every ride {@code p} is part of, as the rider or the one ridden. */
	public static void endRides(ServerPlayer p) {
		if (RIDES.isEmpty()) {
			return;
		}
		Ride mine = RIDES.remove(p.getUUID());
		if (mine != null) {
			if (p.getVehicle() != null && p.getVehicle().getUUID().equals(mine.host())) {
				p.stopRiding();
			}
			protect(p);
		}
		for (Entity e : p.getPassengers()) {
			if (e instanceof ServerPlayer rider && RIDES.remove(rider.getUUID()) != null) {
				rider.stopRiding();
				protect(rider);
			}
		}
	}

	private static void throwOff(ServerPlayer rider, ServerPlayer host, String why) {
		RIDES.remove(rider.getUUID());
		rider.stopRiding();
		protect(rider);
		if (why != null) {
			rider.displayClientMessage(Component.translatable(why).withStyle(ChatFormatting.GOLD), true);
			// tumble off to the side
			Vec3 away = rider.position().subtract(host.position());
			Vec3 h = new Vec3(away.x, 0, away.z);
			h = h.lengthSqr() < 1.0e-4 ? leftOf(host) : h.normalize();
			rider.setDeltaMovement(h.x * 0.45, 0.35, h.z * 0.45);
			rider.hurtMarked = true;
		}
	}

	/** Why the ride must end now (a lang key, "" for a quiet end), or null while it holds. */
	@Nullable
	private static String broken(ServerPlayer rider, ServerPlayer host, Kind kind) {
		if (!rider.isAlive() || !host.isAlive() || host.isSpectator() || rider.isSpectator()) {
			return "";
		}
		if (kind == Kind.WILDMUTT) {
			if (!isWildmutt(host) || isWildmutt(rider)) {
				return "";
			}
			WildmuttKit k = (WildmuttKit) AlienKit.activeKit(host);
			return k.flag(host, WildmuttKit.F_ROLL) ? "message.projecthero.omnitrix.ride.rolled_off" : null;
		}
		return !isGreyMatter(rider) || !shoulderHost(host) || host.isSleeping() ? "" : null;
	}

	private static void tick(MinecraftServer server) {
		if (RIDES.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Ride> en : RIDES.entrySet()) {
			ServerPlayer rider = server.getPlayerList().getPlayer(en.getKey());
			if (rider == null) {
				RIDES.remove(en.getKey());
				continue;
			}
			Ride r = en.getValue();
			ServerPlayer host = server.getPlayerList().getPlayer(r.host());
			if (host == null || rider.getVehicle() != host) {
				RIDES.remove(en.getKey()); // got off (Sneak) or was pulled off
				protect(rider);
				continue;
			}
			String why = broken(rider, host, r.kind());
			if (why != null) {
				throwOff(rider, host, why.isEmpty() ? null : why);
				continue;
			}
			rider.resetFallDistance();
			if (server.getTickCount() % 40 == 0) {
				// safety net: the carrier's own client may only just have loaded the rider (log-in, chunk load)
				host.connection.send(new net.minecraft.network.protocol.game.ClientboundSetPassengersPacket(host));
			}
		}
	}

	/** Whether {@code a} and {@code b} are riding one another (moves never hit or target your own mount or rider). */
	public static boolean partners(Entity a, Entity b) {
		return a != null && b != null && (a.getVehicle() == b || b.getVehicle() == a);
	}
}
