package com.projecthero.mod.stormbreaker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user: "When player selects a destination on the bifrost travel can you make it so that player lifts stormbreaker
 * and then the bifrost appears beaming down on the player and surrounding players, then they all slowly rise up 10 blocks
 * for 3 seconds and get transported back down where they wanted to teleport"): the Bifrost Travel cinematic. {@link
 * Bifrost#travelTo} validates the trip (and spends the cooldown) exactly as before, then hands it here. Ticks from the start:
 * <ol>
 * <li>0..{@link #STRIKE}: the caster raises Stormbreaker to the sky (client pose from the synced {@link #CAST} clock),
 * lightning crackling on the axe; everyone in the beam is held where they stand;</li>
 * <li>{@link #STRIKE}: the Bifrost beams down -- a wide rainbow column ({@link BifrostEntities.Beam} mode LIFT) on the group,
 * the rune circle burned under it, thunder, a camera shake;</li>
 * <li>{@link #RISE_START}..{@link #TRANSPORT} (60 ticks = 3 s): everyone in it rises {@link #RISE_HEIGHT} blocks (eased),
 * held in the beam -- gravity off, fall distance kept at 0, the fly kick waved through ({@code BifrostFlightKickMixin}). A
 * ceiling stops each rider where their head would touch it (never into blocks);</li>
 * <li>{@link #TRANSPORT}: a flash, and everyone is moved to the top of the column at the destination (mode DESCEND, crashing
 * down onto them) -- as high above their landing as the space there allows, up to 10 blocks;</li>
 * <li>{@link #TRANSPORT}..{@link #LAND}: they are lowered gently onto their landings, then the rune circle burns in there,
 * gravity comes back and the trip is over.</li>
 * </ol>
 * Who comes: the caster, every player within {@link #PLAYER_RADIUS} blocks (anyone standing in it), every squadmate within
 * {@link Bifrost#CARRY_RADIUS} -- a player can sneak to stay behind -- and the pets (anything {@link OwnableEntity}) of any of
 * them within {@link #PLAYER_RADIUS} of the caster or of their owner, unless told to sit. Hostile mobs and other creatures
 * never come.
 *
 * <p>Edge cases: a rider who logs out, dies or leaves the level is dropped from the run and, if they logged out, put back
 * on solid ground first (where they stood, or their landing once across) with gravity back on -- so nobody is saved hanging
 * in the air. The server stopping does the same for everyone. The destination chunks are held loaded by a ticket for the
 * whole trip; the landing is checked again at the moment of transport (it may have been built over in those 4 s) and, if
 * nowhere safe is left, the beam lowers everyone back where they started and the cooldown is refunded. Same dimension only,
 * as before.
 *
 * <p>Static state: {@link #RUNS} / {@link #RIDING} hold only UUIDs, dimension keys and positions; cleared by
 * {@code ServerStateReset} (and emptied safely on {@code SERVER_STOPPING}).
 */
public final class BifrostTravel {
	public static final int STRIKE = 14;
	public static final int RISE_START = 18;
	public static final int RISE_TICKS = 60;
	public static final int TRANSPORT = RISE_START + RISE_TICKS;
	public static final int DESCEND_TICKS = 30;
	public static final int LAND = TRANSPORT + DESCEND_TICKS;
	/** The caster lowers Stormbreaker over the last this-many ticks before landing. */
	public static final int POSE_LOWER = 10;
	public static final double RISE_HEIGHT = 10.0;
	public static final double PLAYER_RADIUS = 4.0;
	/** A rider further than this from where the beam holds them (lag, knockback) is snapped back. */
	private static final double SNAP = 1.25;

	/** Game time the caster raised Stormbreaker (0 = not casting). Synced to everyone: drives the raise pose and the axe arcs. */
	public static final AttachmentType<Long> CAST = AttachmentRegistry.create(ProjectHeroMod.id("bifrost_cast"),
			builder -> builder.initializer(() -> 0L).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** Game time the trip this player rides started (0 = not riding). Synced to the owner: drives the screen flashes. */
	public static final AttachmentType<Long> RIDE = AttachmentRegistry.create(ProjectHeroMod.id("bifrost_ride"),
			builder -> builder.initializer(() -> 0L).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	/** Holds the destination chunks while the travellers are on their way (renewed every second). */
	private static final TicketType<ChunkPos> TICKET = TicketType.create("projecthero_bifrost", Comparator.comparingLong(ChunkPos::toLong), 60);

	private static final class Rider {
		final UUID id;
		final boolean player;
		final boolean hadNoGravity;
		final Vec3 start;
		final double up;
		Vec3 landing;
		double down;
		/** Where the beam holds them this tick (the snap-back reference). */
		Vec3 held;

		Rider(Entity e, Vec3 start, double up) {
			this.id = e.getUUID();
			this.player = e instanceof Player;
			this.hadNoGravity = e.isNoGravity();
			this.start = start;
			this.up = up;
			this.held = start;
		}
	}

	private static final class Run {
		final UUID caster;
		final ResourceKey<Level> dimension;
		final long start;
		final Vec3 origin;
		BlockPos landing;
		final float radius;
		final List<Rider> riders = new ArrayList<>();
		boolean struck;
		boolean transported;
		boolean aborted;

		Run(UUID caster, ResourceKey<Level> dimension, long start, Vec3 origin, BlockPos landing, float radius) {
			this.caster = caster;
			this.dimension = dimension;
			this.start = start;
			this.origin = origin;
			this.landing = landing;
			this.radius = radius;
		}
	}

	private static final Map<UUID, Run> RUNS = new LinkedHashMap<>();
	/** Rider UUID -> caster UUID. */
	private static final Map<UUID, UUID> RIDING = new HashMap<>();

	private BifrostTravel() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(BifrostTravel::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLeave(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPING.register(BifrostTravel::settleAll);
	}

	public static void clearSessionState() {
		RUNS.clear();
		RIDING.clear();
	}

	/** In a Bifrost trip right now (casting or riding). */
	public static boolean busy(Entity e) {
		return RIDING.containsKey(e.getUUID());
	}

	/** Held in the beam (gravity off, off the ground): the fly-kick check is waved through and no fall damage builds. */
	public static boolean inBeam(Entity e) {
		return RIDING.containsKey(e.getUUID());
	}

	// ---------------------------------------------------------------- start

	/** Who the beam takes with the caster: see the class javadoc. */
	public static List<Entity> ridersFor(ServerPlayer caster) {
		ServerLevel level = caster.serverLevel();
		List<Entity> out = new ArrayList<>();
		out.add(caster);
		List<ServerPlayer> players = new ArrayList<>();
		players.add(caster);
		double near = PLAYER_RADIUS * PLAYER_RADIUS;
		double squad = Bifrost.CARRY_RADIUS * Bifrost.CARRY_RADIUS;
		for (ServerPlayer other : level.players()) {
			if (other == caster || !other.isAlive() || other.isSpectator() || other.isShiftKeyDown() || busy(other)) {
				continue;
			}
			double d = other.distanceToSqr(caster);
			if (d <= near || (d <= squad && Squads.areAllies(caster, other))) {
				out.add(other);
				players.add(other);
			}
		}
		AABB around = caster.getBoundingBox().inflate(Bifrost.CARRY_RADIUS + PLAYER_RADIUS, 6.0, Bifrost.CARRY_RADIUS + PLAYER_RADIUS);
		for (Entity e : level.getEntities(caster, around, e -> e instanceof OwnableEntity && e.isAlive() && !busy(e))) {
			UUID owner = ((OwnableEntity) e).getOwnerUUID();
			if (owner == null || (e instanceof TamableAnimal t && t.isOrderedToSit())) {
				continue;
			}
			for (ServerPlayer p : players) {
				if (p.getUUID().equals(owner) && (e.distanceToSqr(caster) <= near || e.distanceToSqr(p) <= near)) {
					out.add(e);
					break;
				}
			}
		}
		return out;
	}

	/** Called by {@link Bifrost#travelTo} once the trip is validated (landing found, cooldown spent). */
	static void begin(ServerPlayer caster, BlockPos landing) {
		ServerLevel level = caster.serverLevel();
		long now = level.getGameTime();
		Vec3 origin = caster.position();
		List<Entity> who = ridersFor(caster);
		double spread = 0;
		for (Entity e : who) {
			spread = Math.max(spread, Math.hypot(e.getX() - origin.x, e.getZ() - origin.z) + e.getBbWidth() * 0.5);
		}
		Run run = new Run(caster.getUUID(), level.dimension(), now, origin, landing, (float) Mth.clamp(spread + 1.0, 1.6, 5.5));
		for (Entity e : who) {
			if (e.isPassenger()) {
				e.stopRiding();
			}
			if (e.isVehicle() && !(e instanceof Player)) {
				e.ejectPassengers();
			}
			Rider r = new Rider(e, e.position(), clearance(level, e, e.position(), RISE_HEIGHT));
			run.riders.add(r);
			RIDING.put(r.id, caster.getUUID());
			if (e instanceof ServerPlayer p) {
				p.setAttached(RIDE, now);
				if (p != caster) {
					p.displayClientMessage(Component.translatable("message.projecthero.bifrost.carried", caster.getDisplayName()), true);
				}
			}
		}
		planLandings(level, run);
		caster.setAttached(CAST, now);
		RUNS.put(caster.getUUID(), run);
		holdChunks(level, run);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 0.7f);
		level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.7f, 1.3f);
	}

	/** Each rider's landing: the caster's spot, others keeping their offset where their own column is safe and close. */
	private static void planLandings(ServerLevel level, Run run) {
		for (Rider r : run.riders) {
			Vec3 offset = r.id.equals(run.caster) ? Vec3.ZERO : r.start.subtract(run.origin);
			r.landing = Bifrost.landingFor(level, run.landing, offset);
		}
	}

	/** How far {@code e} can go straight up from {@code at} (up to {@code max}) without its box touching a block. */
	public static double clearance(Level level, Entity e, Vec3 at, double max) {
		AABB box = e.getDimensions(e.getPose()).makeBoundingBox(at);
		double step = 0.125;
		double h = 0;
		while (h + step <= max + 1e-6 && level.noCollision(e, box.move(0, h + step, 0))) {
			h += step;
		}
		return h;
	}

	private static void holdChunks(ServerLevel level, Run run) {
		for (Rider r : run.riders) {
			ChunkPos c = new ChunkPos(BlockPos.containing(r.landing));
			level.getChunkSource().addRegionTicket(TICKET, c, 1, c);
		}
	}

	// ---------------------------------------------------------------- tick

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		Iterator<Run> it = RUNS.values().iterator();
		while (it.hasNext()) {
			Run run = it.next();
			ServerLevel level = server.getLevel(run.dimension);
			int t = level == null ? LAND : (int) (level.getGameTime() - run.start);
			if (level == null || step(level, run, t)) {
				finish(server, run);
				it.remove();
			}
		}
	}

	/** One tick of the trip at {@code t}; true when it is over. */
	private static boolean step(ServerLevel level, Run run, int t) {
		ServerPlayer caster = level.getServer().getPlayerList().getPlayer(run.caster);
		if (t % 20 == 0) {
			holdChunks(level, run);
		}
		fx(level, run, caster, t);
		if (t >= TRANSPORT && !run.transported) { // >=: a jump in game time (/time add) must not skip it
			transport(level, run);
		}
		Iterator<Rider> it = run.riders.iterator();
		while (it.hasNext()) {
			Rider r = it.next();
			Entity e = level.getEntity(r.id);
			if (e == null || !e.isAlive() || e.isRemoved() || e.level() != level || (e instanceof Player p && p.isSpectator())) {
				if (e != null) {
					release(e, r);
				}
				RIDING.remove(r.id);
				it.remove();
				continue;
			}
			if (t >= LAND) {
				land(e, r);
				continue;
			}
			Vec3 at = heldAt(run, r, t);
			Vec3 next = heldAt(run, r, t + 1);
			hold(e, r, at, next);
		}
		if (t >= LAND) {
			landFx(level, run, caster);
			return true;
		}
		return run.riders.isEmpty();
	}

	/** Where the beam holds a rider at tick {@code t}: standing, rising (eased), at the destination top, lowering (eased out). */
	private static Vec3 heldAt(Run run, Rider r, int t) {
		if (t < RISE_START) {
			return r.start;
		}
		if (t < TRANSPORT) {
			float k = Mth.clamp((t - RISE_START + 1) / (float) RISE_TICKS, 0f, 1f);
			return r.start.add(0, r.up * smooth(k), 0);
		}
		if (t >= LAND) {
			return r.landing;
		}
		float k = Mth.clamp((t - TRANSPORT) / (float) DESCEND_TICKS, 0f, 1f);
		float eased = 1f - (1f - k) * (1f - k); // fast at the top, settling softly onto the ground
		return r.landing.add(0, r.down * (1f - eased), 0);
	}

	private static float smooth(float k) {
		return k * k * (3 - 2 * k);
	}

	/** Holds one rider in the beam at {@code at}, moving toward {@code next} -- velocity for a smooth client, position for the server. */
	private static void hold(Entity e, Rider r, Vec3 at, Vec3 next) {
		r.held = at;
		e.setNoGravity(true);
		e.resetFallDistance();
		if (e instanceof ServerPlayer p) {
			if (p.position().distanceTo(at) > SNAP) {
				p.connection.teleport(at.x, at.y, at.z, 0f, 0f, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
			} else {
				p.setPos(at.x, at.y, at.z);
			}
			p.setDeltaMovement(next.subtract(at));
			p.hurtMarked = true;
		} else {
			e.setPos(at.x, at.y, at.z);
			e.setDeltaMovement(Vec3.ZERO);
			if (e instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
	}

	/** The top of the column: re-checks the landing and moves everyone to the top of the beam at the destination. */
	private static void transport(ServerLevel level, Run run) {
		BlockPos landing = run.landing;
		if (!Bifrost.isStandable(level, landing)) {
			BlockPos again = Bifrost.findSafeLanding(level, landing.getX(), landing.getY(), landing.getZ());
			if (again == null) {
				abort(level, run);
				return;
			}
			run.landing = again;
			planLandings(level, run);
		}
		for (Rider r : run.riders) {
			Entity e = level.getEntity(r.id);
			BlockPos own = BlockPos.containing(r.landing);
			if (e != null && !Bifrost.isStandable(level, own)) {
				r.landing = Vec3.atBottomCenterOf(run.landing); // their own spot was built over: share the caster's
			}
			if (e == null) {
				continue;
			}
			r.down = clearance(level, e, r.landing, RISE_HEIGHT);
			Vec3 top = r.landing.add(0, r.down, 0);
			column(level, e.position());
			if (e instanceof ServerPlayer p) {
				p.teleportTo(level, top.x, top.y, top.z, Set.of(), p.getYRot(), p.getXRot());
			} else {
				e.teleportTo(top.x, top.y, top.z);
			}
			e.setDeltaMovement(Vec3.ZERO);
			e.resetFallDistance();
			r.held = top;
		}
		run.transported = true;
		Vec3 to = Vec3.atBottomCenterOf(run.landing);
		BifrostEntities.Beam beam = new BifrostEntities.Beam(BifrostEntities.BEAM, level);
		beam.setPos(to.x, to.y, to.z);
		beam.setMode(BifrostEntities.Beam.DESCEND, run.radius);
		level.addFreshEntity(beam);
		level.playSound(null, run.origin.x, run.origin.y, run.origin.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0f, 1.0f);
		level.playSound(null, to.x, to.y + 6, to.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.25f);
		level.playSound(null, to.x, to.y + 6, to.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.4f, 0.9f);
		level.playSound(null, to.x, to.y + 6, to.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.6f, 1.2f);
		level.sendParticles(ParticleTypes.FLASH, to.x, to.y + 9, to.z, 2, 0.4, 0.4, 0.4, 0);
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 9, to.z, 60, run.radius * 0.4, 1.0, run.radius * 0.4, 0.15);
		shake(level, run, 0.45f, 10);
	}

	/** Nowhere left to land: the beam lowers everyone back down where they started and the cooldown is refunded. */
	private static void abort(ServerLevel level, Run run) {
		run.aborted = true;
		run.transported = true;
		for (Rider r : run.riders) {
			r.landing = r.start;
			r.down = r.up;
		}
		ServerPlayer caster = level.getServer().getPlayerList().getPlayer(run.caster);
		if (caster != null) {
			caster.setAttached(ModAttachments.BIFROST_READY_AT, 0L);
			caster.displayClientMessage(Component.translatable(Bifrost.Result.NO_LANDING.messageKey()), true);
		}
		BifrostEntities.Beam beam = new BifrostEntities.Beam(BifrostEntities.BEAM, level);
		beam.setPos(run.origin.x, run.origin.y, run.origin.z);
		beam.setMode(BifrostEntities.Beam.DESCEND, run.radius);
		level.addFreshEntity(beam);
		level.playSound(null, run.origin.x, run.origin.y, run.origin.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	private static void land(Entity e, Rider r) {
		if (e instanceof ServerPlayer p) {
			p.connection.teleport(r.landing.x, r.landing.y, r.landing.z, 0f, 0f, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
		} else {
			e.setPos(r.landing.x, r.landing.y, r.landing.z);
		}
		release(e, r);
	}

	/** Gravity back, momentum and fall distance gone, off the run. */
	private static void release(Entity e, Rider r) {
		e.setNoGravity(r.hadNoGravity);
		e.setDeltaMovement(Vec3.ZERO);
		e.hurtMarked = true;
		e.resetFallDistance();
		if (e instanceof ServerPlayer p) {
			p.setAttached(RIDE, 0L);
		}
	}

	private static void finish(MinecraftServer server, Run run) {
		ServerLevel level = server.getLevel(run.dimension);
		for (Rider r : run.riders) {
			Entity e = level == null ? null : level.getEntity(r.id);
			if (e != null) {
				release(e, r);
			}
			RIDING.remove(r.id);
		}
		ServerPlayer caster = server.getPlayerList().getPlayer(run.caster);
		if (caster != null) {
			// the pose was lowered over the last POSE_LOWER ticks of the descent
			caster.setAttached(CAST, 0L);
			if (!run.aborted) {
				int carried = run.riders.size() - 1;
				caster.displayClientMessage(carried > 0
						? Component.translatable("message.projecthero.bifrost.travelled_with", carried, Bifrost.cooldownRemaining(caster) / 20)
						: Component.translatable("message.projecthero.bifrost.travelled", Bifrost.cooldownRemaining(caster) / 20), true);
			}
		}
		RIDING.remove(run.caster);
	}

	// ---------------------------------------------------------------- leaving mid-trip

	/** A rider logging out mid-trip: put back on solid ground (where they stood, or their landing once across), gravity on. */
	public static void onLeave(ServerPlayer player) {
		UUID casterId = RIDING.remove(player.getUUID());
		if (casterId == null) {
			return;
		}
		Run run = RUNS.get(casterId);
		if (run == null) {
			return;
		}
		Iterator<Rider> it = run.riders.iterator();
		while (it.hasNext()) {
			Rider r = it.next();
			if (r.id.equals(player.getUUID())) {
				Vec3 safe = run.transported ? r.landing : r.start;
				player.moveTo(safe.x, safe.y, safe.z, player.getYRot(), player.getXRot());
				release(player, r);
				it.remove();
			}
		}
		if (player.getUUID().equals(casterId)) {
			player.setAttached(CAST, 0L);
		}
	}

	/** The server is stopping: everyone in a beam goes down safely before the players are saved. */
	private static void settleAll(MinecraftServer server) {
		for (Run run : RUNS.values()) {
			ServerLevel level = server.getLevel(run.dimension);
			if (level == null) {
				continue;
			}
			for (Rider r : run.riders) {
				Entity e = level.getEntity(r.id);
				if (e != null) {
					Vec3 safe = run.transported ? r.landing : r.start;
					e.moveTo(safe.x, safe.y, safe.z, e.getYRot(), e.getXRot());
					release(e, r);
				}
			}
			ServerPlayer caster = server.getPlayerList().getPlayer(run.caster);
			if (caster != null) {
				caster.setAttached(CAST, 0L);
			}
		}
		clearSessionState();
	}

	// ---------------------------------------------------------------- effects

	private static void fx(ServerLevel level, Run run, ServerPlayer caster, int t) {
		Vec3 o = run.origin;
		if (t < STRIKE && caster != null) {
			// lightning crackling on the raised axe
			Vec3 axe = caster.position().add(0, 2.8, 0);
			float build = t / (float) STRIKE;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, axe.x, axe.y, axe.z, 2 + (int) (build * 6), 0.25, 0.3, 0.25, 0.05 + build * 0.12);
			if (t == 6) {
				level.playSound(null, o.x, o.y, o.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.7f);
				level.playSound(null, o.x, o.y, o.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
			}
			if (t % 4 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, o.x, o.y + 0.1, o.z, 5, 1.2, 0.02, 1.2, 0.03);
			}
		}
		if (t >= STRIKE && !run.struck) {
			run.struck = true;
			BifrostEntities.Beam beam = new BifrostEntities.Beam(BifrostEntities.BEAM, level);
			beam.setPos(o.x, o.y, o.z);
			beam.setMode(BifrostEntities.Beam.LIFT, run.radius);
			level.addFreshEntity(beam);
			Bifrost.burnMark(level, o, run.radius / 3.2f);
			Bifrost.rainbowRing(level, o, run.radius);
			level.playSound(null, o.x, o.y, o.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.3f, 0.9f);
			level.playSound(null, o.x, o.y, o.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2f, 1.1f);
			level.playSound(null, o.x, o.y, o.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 0.9f);
			level.playSound(null, o.x, o.y, o.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.6f, 0.8f);
			level.sendParticles(ParticleTypes.FLASH, o.x, o.y + 1.2, o.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.CLOUD, o.x, o.y + 0.2, o.z, 40, run.radius * 0.6, 0.05, run.radius * 0.6, 0.12);
			shake(level, run, 0.6f, 12);
		}
		if (t == RISE_START) {
			level.playSound(null, o.x, o.y, o.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.55f, 1.25f);
			level.playSound(null, o.x, o.y, o.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
		}
		if (t > RISE_START && t < TRANSPORT && (t - RISE_START) % 10 == 0) {
			// a chime climbing in pitch as they rise, and the beacon's hum under it
			float k = (t - RISE_START) / (float) RISE_TICKS;
			SoundEvent chime = SoundEvents.AMETHYST_BLOCK_CHIME;
			level.playSound(null, o.x, o.y + 5, o.z, chime, SoundSource.PLAYERS, 1.4f, 0.7f + k * 1.1f);
			level.playSound(null, o.x, o.y + 5, o.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.8f + k * 0.9f);
		}
		if (t > STRIKE && t < TRANSPORT && t % 3 == 0) {
			// motes of light streaming up the column round the riders
			level.sendParticles(ParticleTypes.END_ROD, o.x, o.y + 0.5 + (t - STRIKE) * 0.16, o.z, 4, run.radius * 0.45, 1.5, run.radius * 0.45, 0.04);
		}
	}

	private static void landFx(ServerLevel level, Run run, ServerPlayer caster) {
		Vec3 to = Vec3.atBottomCenterOf(run.landing);
		if (run.aborted) {
			to = run.origin;
		}
		Bifrost.burnMark(level, to, run.radius / 3.2f);
		Bifrost.rainbowRing(level, to, run.radius);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8f, 1.1f);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 1.5f);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8f, 1.2f);
		level.sendParticles(ParticleTypes.CLOUD, to.x, to.y + 0.15, to.z, 36, run.radius * 0.5, 0.05, run.radius * 0.5, 0.15);
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 0.4, to.z, 40, run.radius * 0.5, 0.2, run.radius * 0.5, 0.12);
		shake(level, run, 0.35f, 8);
	}

	private static void column(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 16, 0.3, 0.8, 0.3, 0.05);
	}

	private static void shake(ServerLevel level, Run run, float intensity, int ticks) {
		for (Rider r : run.riders) {
			if (r.player && level.getEntity(r.id) instanceof ServerPlayer p && ServerPlayNetworking.canSend(p, TitanShakePayload.TYPE)) {
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity, ticks));
			}
		}
	}

	// ---------------------------------------------------------------- tests

	/** Test hook: plays the rest of {@code caster}'s trip out at once (every remaining tick, in order). */
	public static void finishForTests(ServerPlayer caster) {
		Run run = RUNS.get(caster.getUUID());
		if (run == null) {
			return;
		}
		ServerLevel level = caster.getServer().getLevel(run.dimension);
		int t = (int) (level.getGameTime() - run.start);
		boolean done = false;
		while (!done) {
			done = step(level, run, ++t);
		}
		finish(caster.getServer(), run);
		RUNS.remove(caster.getUUID());
	}
}
