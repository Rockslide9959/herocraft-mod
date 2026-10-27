package com.projecthero.mod.greenlantern.recon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternBattery;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternRingVoice;
import com.projecthero.mod.greenlantern.GreenLanternVisuals;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.miraculous.pegasus.PegasusTeleport;
import com.projecthero.mod.network.GreenLanternGatewayPayload;
import com.projecthero.mod.network.GreenLanternReconPayload;
import com.projecthero.mod.squad.Squads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Hard-Light Gateway ({@code ConstructType.HARD_LIGHT_GATEWAY}, Construct Menu &gt; Mobility) and the ring's
 * waypoints ({@link GreenLanternWaypoints}).
 * <ul>
 *   <li><b>Shift + the key</b>: the Waypoints screen -- save up to {@link GreenLanternReconConfig#MAX_WAYPOINTS} named
 *   places (each in the world it was saved in), rename, delete.</li>
 *   <li><b>The key</b>: the waypoint picker. Picking one starts a {@link GreenLanternReconConfig#GATEWAY_CHANNEL_TICKS}
 *   channel: the ring arm rises and a door frame traces itself out of light in front of you. Taking damage breaks it off
 *   (no cost, no cooldown); so does the key again.</li>
 *   <li>When it completes, the door unfolds and its twin opens at the waypoint, both for
 *   {@link GreenLanternReconConfig#GATEWAY_OPEN_TICKS}. Walking into either sends you out of the other, keeping your
 *   facing relative to the door. The Lantern and their squad only -- other players and mobs can't use it. Both ends keep
 *   their chunk loaded while open.</li>
 *   <li>Same world only (a waypoint saved elsewhere is greyed out). Cost {@link GreenLanternReconConfig#gatewayCost} (100 +
 *   0.2 a block, at most 600), paid when the door opens; then a {@link GreenLanternReconConfig#GATEWAY_COOLDOWN_TICKS}
 *   cooldown.</li>
 * </ul>
 * The teleport reuses the Pegasus (Kaalki) portal helpers ({@link PegasusTeleport}: safe landing, fit checks, the move).
 * Static state ({@link #CHANNELS}, {@link #OPEN}, {@link #PASSED}) is cleared on server stop and per Lantern on clean-up.
 */
public final class GreenLanternGateway {
	private static final ParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.3f);
	private static final ParticleOptions PALE = new DustParticleOptions(new Vector3f(0.75f, 1.0f, 0.82f), 0.9f);

	/** A channel in progress: the door being traced, the waypoint and when it began. */
	public record Channel(HardLightGatewayEntity door, GreenLanternWaypoints.Waypoint waypoint, long start) {
	}

	/** A pair of open doors. */
	public record Doors(HardLightGatewayEntity near, HardLightGatewayEntity far) {
		public boolean gone() {
			return near.isRemoved() && far.isRemoved();
		}
	}

	private static final Map<UUID, Channel> CHANNELS = new ConcurrentHashMap<>();
	private static final Map<UUID, Doors> OPEN = new ConcurrentHashMap<>();
	/** Traveller -> game time before which they may not take a door again. */
	private static final Map<UUID, Long> PASSED = new ConcurrentHashMap<>();
	/** Someone not allowed through -> game time of their last "not yours" spark (so it isn't spammed). */
	private static final Map<UUID, Long> DENIED = new ConcurrentHashMap<>();

	private GreenLanternGateway() {
	}

	public static void clearSessionState() {
		CHANNELS.clear();
		OPEN.clear();
		PASSED.clear();
		DENIED.clear();
	}

	public static Channel channel(UUID owner) {
		return CHANNELS.get(owner);
	}

	public static Doors doors(UUID owner) {
		Doors d = OPEN.get(owner);
		return d == null || d.gone() ? null : d;
	}

	// ---------------------------------------------------------------- waypoints

	public static GreenLanternWaypoints waypoints(ServerPlayer p) {
		return p.getAttachedOrElse(ModAttachments.GREEN_LANTERN_WAYPOINTS, GreenLanternWaypoints.EMPTY);
	}

	private static void save(ServerPlayer p, GreenLanternWaypoints w) {
		p.setAttached(ModAttachments.GREEN_LANTERN_WAYPOINTS, w);
	}

	public static String dimensionId(ServerLevel level) {
		return level.dimension().location().toString();
	}

	/** Saves a waypoint where {@code p} stands. False when the ring already holds five. */
	public static boolean addWaypoint(ServerPlayer p, String name) {
		GreenLanternWaypoints w = waypoints(p);
		if (w.full()) {
			say(p, Component.translatable("message.projecthero.green_lantern.gateway.waypoints_full", GreenLanternReconConfig.MAX_WAYPOINTS),
					ChatFormatting.RED);
			return false;
		}
		String n = GreenLanternWaypoints.clean(name);
		if (n.isEmpty()) {
			n = Component.translatable("screen.projecthero.gl_waypoints.default_name", w.size() + 1).getString();
		}
		save(p, w.with(new GreenLanternWaypoints.Waypoint(n, dimensionId(p.serverLevel()), p.getX(), p.getY(), p.getZ(),
				Mth.wrapDegrees(p.getYRot()))));
		say(p, Component.translatable("message.projecthero.green_lantern.gateway.waypoint_saved", n), ChatFormatting.GREEN);
		GreenLanternDrone.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.7f);
		p.serverLevel().sendParticles(GREEN, p.getX(), p.getY() + 0.1, p.getZ(), 16, 0.4, 0.05, 0.4, 0.02);
		return true;
	}

	public static boolean renameWaypoint(ServerPlayer p, int i, String name) {
		GreenLanternWaypoints w = waypoints(p);
		String n = GreenLanternWaypoints.clean(name);
		if (w.get(i) == null || n.isEmpty()) {
			return false;
		}
		save(p, w.set(i, w.get(i).renamed(n)));
		return true;
	}

	public static boolean deleteWaypoint(ServerPlayer p, int i) {
		GreenLanternWaypoints w = waypoints(p);
		if (w.get(i) == null) {
			return false;
		}
		save(p, w.without(i));
		return true;
	}

	// ---------------------------------------------------------------- keys / screens

	/** The key: the picker -- or, mid-channel, break it off; with the doors open, close them early. */
	public static void openPicker(ServerPlayer p) {
		if (CHANNELS.containsKey(p.getUUID())) {
			cancel(p, "message.projecthero.green_lantern.gateway.cancelled");
			return;
		}
		Doors d = doors(p.getUUID());
		if (d != null) {
			closeDoors(d);
			OPEN.remove(p.getUUID());
			GreenLanternDrone.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.4f);
			return;
		}
		GreenLanternDrone.send(p, new GreenLanternReconPayload(GreenLanternReconPayload.OPEN_PICKER, new int[0], cooldownSeconds(p), 0, 0));
	}

	/** Shift + the key: the Waypoints screen. */
	public static void openWaypoints(ServerPlayer p) {
		GreenLanternDrone.send(p, new GreenLanternReconPayload(GreenLanternReconPayload.OPEN_WAYPOINTS, new int[0], cooldownSeconds(p), 0, 0));
	}

	private static int cooldownSeconds(ServerPlayer p) {
		return (int) Math.ceil(GreenLantern.cooldownRemaining(p, GreenLanternReconConfig.GATEWAY_COOLDOWN_ID) / 20.0);
	}

	public static void handle(ServerPlayer p, GreenLanternGatewayPayload payload) {
		switch (payload.action()) {
			case GreenLanternGatewayPayload.ADD -> addWaypoint(p, payload.name());
			case GreenLanternGatewayPayload.RENAME -> renameWaypoint(p, payload.index(), payload.name());
			case GreenLanternGatewayPayload.DELETE -> deleteWaypoint(p, payload.index());
			case GreenLanternGatewayPayload.OPEN -> {
				if (!com.projecthero.mod.hero.PowerSuppression.isSuppressed(p)) {
					start(p, payload.index());
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- the channel

	/** Begins the channel to waypoint {@code index}. False (nothing spent, no cooldown) when it is refused. */
	public static boolean start(ServerPlayer p, int index) {
		UUID id = p.getUUID();
		if (CHANNELS.containsKey(id) || doors(id) != null) {
			say(p, Component.translatable("message.projecthero.green_lantern.gateway.busy"), ChatFormatting.YELLOW);
			return false;
		}
		if (!GreenLantern.abilityReady(p, GreenLanternReconConfig.GATEWAY_COOLDOWN_ID)) {
			say(p, Component.translatable("message.projecthero.green_lantern.construct_cooldown", cooldownSeconds(p)), ChatFormatting.YELLOW);
			return false;
		}
		GreenLanternWaypoints.Waypoint wp = waypoints(p).get(index);
		if (wp == null) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		if (!wp.dimension().equals(dimensionId(level))) {
			say(p, Component.translatable("message.projecthero.green_lantern.gateway.other_dimension", wp.name()), ChatFormatting.RED);
			return false;
		}
		double dist = wp.distanceTo(p.getX(), p.getY(), p.getZ());
		if (dist < GreenLanternReconConfig.GATEWAY_MIN_DISTANCE) {
			say(p, Component.translatable("message.projecthero.green_lantern.gateway.too_close", wp.name()), ChatFormatting.YELLOW);
			return false;
		}
		float cost = GreenLanternReconConfig.gatewayCost(dist);
		if (!GreenLanternEnergy.canSpend(p, cost)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return false;
		}
		Vec3 flat = flatLook(p);
		Vec3 spot = PegasusTeleport.landing(level, p, p.position().add(flat.scale(GreenLanternReconConfig.GATEWAY_DOOR_DISTANCE)), 1);
		if (spot == null) {
			say(p, Component.translatable("message.projecthero.green_lantern.gateway.no_room"), ChatFormatting.YELLOW);
			return false;
		}
		float faceYaw = Mth.wrapDegrees(yawOf(flat) + 180f); // its front faces the bearer
		HardLightGatewayEntity door = HardLightGatewayEntity.create(level, p, spot, faceYaw, HardLightGatewayEntity.TRACE, false);
		level.addFreshEntity(door);
		CHANNELS.put(id, new Channel(door, wp, level.getGameTime()));
		GreenLanternVisuals.channel(p, GreenLanternFx.CH_GATEWAY, true);
		GreenLanternDrone.sound(level, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.2f);
		GreenLanternDrone.sound(level, spot, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.gateway.channelling", wp.name())
				.withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	/** Per Lantern tick: the channel's hum and its completion; forgets doors that have folded away. */
	public static void tick(ServerPlayer p) {
		UUID id = p.getUUID();
		Doors d = OPEN.get(id);
		if (d != null && d.gone()) {
			OPEN.remove(id, d);
		}
		Channel ch = CHANNELS.get(id);
		if (ch == null) {
			return;
		}
		if (ch.door().isRemoved() || ch.door().level() != p.level()) {
			cancel(p, "message.projecthero.green_lantern.gateway.cancelled");
			return;
		}
		ServerLevel level = p.serverLevel();
		long elapsed = level.getGameTime() - ch.start();
		if (elapsed % 10 == 0) {
			float t = elapsed / (float) GreenLanternReconConfig.GATEWAY_CHANNEL_TICKS;
			GreenLanternDrone.sound(level, ch.door().position().add(0, 1.4, 0), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.8f + t * 1.0f);
			Vec3 h = com.projecthero.mod.hero.power.AbilityHelpers.handPosition(p);
			com.projecthero.mod.hero.power.AbilityHelpers.line(level, h, ch.door().position().add(0, 1.4, 0), PALE, 2.0);
		}
		if (elapsed >= GreenLanternReconConfig.GATEWAY_CHANNEL_TICKS) {
			complete(p, ch);
		}
	}

	/** Taking damage breaks the channel off (no cost, no cooldown). */
	public static void onDamaged(ServerPlayer p) {
		if (CHANNELS.containsKey(p.getUUID())) {
			cancel(p, "message.projecthero.green_lantern.gateway.interrupted");
		}
	}

	/** Ends a channel with nothing spent: the traced outline scatters. */
	public static void cancel(ServerPlayer p, String messageKey) {
		Channel ch = CHANNELS.remove(p.getUUID());
		GreenLanternVisuals.channel(p, GreenLanternFx.CH_GATEWAY, false);
		if (ch == null) {
			return;
		}
		HardLightGatewayEntity door = ch.door();
		if (!door.isRemoved() && door.level() instanceof ServerLevel level) {
			Vec3 c = door.position().add(0, 1.4, 0);
			level.sendParticles(PALE, c.x, c.y, c.z, 30, 0.6, 1.2, 0.1, 0.05);
			GreenLanternDrone.sound(level, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8f, 1.2f);
			door.discard();
		}
		if (messageKey != null) {
			p.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.YELLOW), true);
		}
	}

	/** The channel is done: pay, open the near door, open its twin at the waypoint, start the cooldown. */
	private static void complete(ServerPlayer p, Channel ch) {
		CHANNELS.remove(p.getUUID());
		GreenLanternVisuals.channel(p, GreenLanternFx.CH_GATEWAY, false);
		ServerLevel level = p.serverLevel();
		GreenLanternWaypoints.Waypoint wp = ch.waypoint();
		HardLightGatewayEntity near = ch.door();
		Vec3 target = new Vec3(wp.x(), wp.y(), wp.z());
		BlockPos tp = BlockPos.containing(target);
		Vec3 arrive = null;
		if (level.getWorldBorder().isWithinBounds(tp)) {
			level.getChunk(tp); // load (or generate) the far end before checking it
			arrive = PegasusTeleport.landing(level, p, target.add(0, 0.05, 0), 3);
		}
		if (arrive == null) {
			near.discard();
			say(p, Component.translatable("message.projecthero.green_lantern.gateway.unsafe", wp.name()), ChatFormatting.RED);
			return;
		}
		float cost = GreenLanternReconConfig.gatewayCost(wp.distanceTo(p.getX(), p.getY(), p.getZ()));
		if (!GreenLanternEnergy.spend(p, cost)) {
			near.discard();
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return;
		}
		GreenLanternBattery.onAbilityUsed(p);
		float farYaw = Mth.wrapDegrees(wp.yaw()); // its front faces the way the Lantern looked when they saved the waypoint
		Vec3 farNormal = Vec3.directionFromRotation(0f, farYaw);
		Vec3 farFeet = arrive.subtract(farNormal.scale(1.3));
		HardLightGatewayEntity far = HardLightGatewayEntity.create(level, p, farFeet, farYaw, HardLightGatewayEntity.OPEN, true);
		long now = level.getGameTime();
		near.setState(HardLightGatewayEntity.OPEN, now);
		near.partner = far;
		far.partner = near;
		near.closeAt = far.closeAt = now + GreenLanternReconConfig.GATEWAY_OPEN_TICKS;
		level.addFreshEntity(far);
		OPEN.put(p.getUUID(), new Doors(near, far));
		GreenLantern.triggerCooldown(p, GreenLanternReconConfig.GATEWAY_COOLDOWN_ID, GreenLanternReconConfig.GATEWAY_COOLDOWN_TICKS);
		GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_CONSTRUCT);
		for (HardLightGatewayEntity g : new HardLightGatewayEntity[] { near, far }) {
			Vec3 c = g.position().add(0, 1.45, 0);
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 14, 0.6, 1.2, 0.15, 0.03);
			GreenLanternDrone.sound(level, c, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.5f);
			GreenLanternDrone.sound(level, c, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f); // the whoosh
		}
		GreenLanternRingVoice.say(p, GreenLanternRingVoice.Line.GATEWAY, wp.name());
		p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.gateway.opened", wp.name(),
				GreenLanternReconConfig.GATEWAY_OPEN_TICKS / 20).withStyle(ChatFormatting.GREEN), true);
	}

	/** N / death / logout / power loss: a channel breaks off and open doors fold away. */
	public static void dismiss(UUID owner) {
		Channel ch = CHANNELS.remove(owner);
		if (ch != null && !ch.door().isRemoved()) {
			ch.door().discard();
		}
		Doors d = OPEN.remove(owner);
		if (d != null) {
			closeDoors(d);
		}
		PASSED.remove(owner);
		DENIED.remove(owner);
	}

	private static void closeDoors(Doors d) {
		if (!d.near().isRemoved()) {
			d.near().close();
		}
		if (!d.far().isRemoved()) {
			d.far().close();
		}
	}

	// ---------------------------------------------------------------- travel

	/** From the door's tick while it is open: the Lantern or a squadmate stepping into it goes out of its twin. */
	static void passThrough(HardLightGatewayEntity door, ServerLevel level) {
		HardLightGatewayEntity to = door.partner;
		if (to == null || to.isRemoved()) {
			return;
		}
		Vec3 c = door.position();
		Vec3 n = door.normal();
		Vec3 r = door.right();
		long now = level.getGameTime();
		ServerPlayer owner = door.owner == null ? null : level.getServer().getPlayerList().getPlayer(door.owner);
		AABB box = new AABB(c, c).inflate(2.0, 0, 2.0).expandTowards(0, GreenLanternReconConfig.DOOR_HEIGHT + 0.5, 0);
		for (ServerPlayer sp : level.getEntitiesOfClass(ServerPlayer.class, box, x -> x.isAlive() && !x.isSpectator() && !x.isPassenger())) {
			Vec3 rel = sp.position().subtract(c);
			double lat = rel.dot(r);
			double d1 = rel.dot(n);
			double d0 = new Vec3(sp.xo, sp.yo, sp.zo).subtract(c).dot(n);
			if (Math.abs(lat) > GreenLanternReconConfig.DOOR_HALF_WIDTH || rel.y < -0.6 || rel.y > GreenLanternReconConfig.DOOR_HEIGHT - 1.0) {
				continue;
			}
			boolean into = d0 > 0.0 && d1 <= 0.15 || Math.abs(d1) < 0.3;
			if (!into) {
				continue;
			}
			if (!mayUse(owner, door.owner, sp)) {
				Long last = DENIED.get(sp.getUUID());
				if (last == null || now - last > 20) {
					DENIED.put(sp.getUUID(), now);
					level.sendParticles(PALE, sp.getX(), sp.getY() + 1.0, sp.getZ(), 8, 0.2, 0.4, 0.2, 0.02);
					sp.displayClientMessage(Component.translatable("message.projecthero.green_lantern.gateway.not_yours"), true);
				}
				continue;
			}
			Long until = PASSED.get(sp.getUUID());
			if (until != null && now < until) {
				continue;
			}
			travel(door, to, sp, level);
		}
	}

	/** The Lantern who opened it, or someone in their squad. Never anyone else, never a mob. */
	public static boolean mayUse(ServerPlayer owner, UUID ownerId, ServerPlayer who) {
		if (who.getUUID().equals(ownerId)) {
			return true;
		}
		return owner != null && Squads.areAllies(owner, who);
	}

	/** Sends {@code sp} out of {@code to}'s front, its facing turned by the same angle as the doors are. */
	public static boolean travel(HardLightGatewayEntity from, HardLightGatewayEntity to, ServerPlayer sp, ServerLevel level) {
		Vec3 rel = sp.position().subtract(from.position());
		double lat = Mth.clamp(rel.dot(from.right()), -GreenLanternReconConfig.DOOR_HALF_WIDTH * 0.7, GreenLanternReconConfig.DOOR_HALF_WIDTH * 0.7);
		Vec3 out = to.position().add(to.normal().scale(1.3)).add(to.right().scale(-lat)).add(0, Math.max(0.0, Math.min(1.0, rel.y)), 0);
		ServerLevel dest = (ServerLevel) to.level();
		Vec3 feet = PegasusTeleport.fits(dest, sp, out) ? out : PegasusTeleport.landing(dest, sp, out, 2);
		PASSED.put(sp.getUUID(), level.getGameTime() + GreenLanternReconConfig.GATEWAY_PASS_COOLDOWN);
		if (feet == null) {
			level.sendParticles(ParticleTypes.SMOKE, sp.getX(), sp.getY() + 0.8, sp.getZ(), 8, 0.2, 0.3, 0.2, 0.01);
			return false;
		}
		float turn = to.faceYaw() - (from.faceYaw() + 180f);
		float yaw = Mth.wrapDegrees(sp.getYRot() + turn);
		Vec3 v = sp.getDeltaMovement();
		Vec3 hv = new Vec3(v.x, 0, v.z).yRot(-turn * Mth.DEG_TO_RAD);
		Vec3 start = sp.position();
		boolean moved = PegasusTeleport.move(sp, dest, feet, yaw, sp.getXRot(), hv.add(0, Math.max(0.0, v.y), 0));
		if (moved) {
			level.sendParticles(GREEN, start.x, start.y + 1.0, start.z, 14, 0.25, 0.5, 0.25, 0.02);
			dest.sendParticles(GREEN, feet.x, feet.y + 1.0, feet.z, 14, 0.25, 0.5, 0.25, 0.02);
			GreenLanternDrone.sound(level, start, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
			GreenLanternDrone.sound(dest, feet, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.4f);
			GreenLanternDrone.sound(dest, feet, SoundEvents.ENDERMAN_TELEPORT, 0.3f, 1.7f);
		}
		return moved;
	}

	// ---------------------------------------------------------------- helpers

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	private static float yawOf(Vec3 dir) {
		return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
	}

	private static void say(ServerPlayer p, Component c, ChatFormatting color) {
		p.displayClientMessage(c.copy().withStyle(color), true);
	}
}
