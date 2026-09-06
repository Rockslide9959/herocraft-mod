package com.projecthero.mod.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * One running world event. The base class owns everything that is not specific to a particular
 * event: identity, where it is, its lifecycle state, who is taking part, the entities it created,
 * the "everyone left" abandon logic, and save/load. Subclasses supply the actual content by
 * implementing {@link #onTick}.
 *
 * <h2>Performance contract</h2>
 * Nothing in this class scans the world. Specifically:
 * <ul>
 *   <li>participants come from {@link ServerLevel#players()}, a list the server already maintains;</li>
 *   <li>event-owned mobs are tracked <em>by UUID</em>, and liveness is resolved with
 *       {@link ServerLevel#getEntity(UUID)}, which is a hash lookup into the level's entity index --
 *       never an AABB query and never a chunk walk;</li>
 *   <li>{@link #onTick} runs on the framework's slow cadence
 *       ({@link EventConfig.Framework#tickIntervalTicks}), not every game tick;</li>
 *   <li>the event never force-loads a chunk. If its area is unloaded there are no players in it, so
 *       it pauses instead of doing work in chunks nobody is standing in.</li>
 * </ul>
 *
 * <h2>Persistence</h2>
 * Instances live in {@link EventSavedData}, so they are written with the world and reloaded with it.
 * Everything needed to resume mid-event -- current wave, which mobs are still owed, which rewards
 * have already been handed out -- is saved, so a restart can neither duplicate a reward nor replay a
 * finished wave (spec section 26).
 */
public abstract class EventInstance {
	/** Scoreboard tag stamped on every event-owned mob, so a stray one is identifiable in-game. */
	public static final String EVENT_TAG = "projecthero_event_mob";

	private final UUID id;
	private ResourceKey<Level> dimension;
	private BlockPos center;
	private EventState state = EventState.PENDING;
	/** Ticks this event has been alive (counted in framework ticks x interval). */
	private long ageTicks;
	private final EventParticipants participants = new EventParticipants();
	/** Mobs this event created and is responsible for cleaning up. */
	private final Set<UUID> ownedMobs = new LinkedHashSet<>();
	/** Game time nobody has been present since; {@link Long#MIN_VALUE} while somebody is. */
	private long emptySinceTick = Long.MIN_VALUE;

	protected EventInstance(UUID id) {
		this.id = id;
	}

	// ---------------- identity ----------------

	/** Stable type id, matching the key this event was registered under in {@link EventTypes}. */
	public abstract String typeId();

	/** Name for chat / HUD / boss bar. */
	public abstract Component displayName();

	public final UUID id() {
		return id;
	}

	public final ResourceKey<Level> dimension() {
		return dimension;
	}

	public final BlockPos center() {
		return center;
	}

	public final EventState state() {
		return state;
	}

	public final long ageTicks() {
		return ageTicks;
	}

	public final EventParticipants participants() {
		return participants;
	}

	public double radius() {
		return EventConfig.framework().eventRadius;
	}

	protected final void setState(EventState newState) {
		this.state = newState;
	}

	public final void placeAt(ServerLevel level, BlockPos pos) {
		this.dimension = level.dimension();
		this.center = pos.immutable();
	}

	public final boolean isAt(ServerLevel level, BlockPos pos, double within) {
		return dimension == level.dimension() && center != null
				&& center.distSqr(pos) <= within * within;
	}

	// ---------------- lifecycle ----------------

	/**
	 * Driven by {@link EventManager}. Handles presence, the abandon timers and pause/resume, then
	 * hands off to {@link #onTick}. Returns false when this event is finished and should be dropped.
	 */
	public final boolean tick(ServerLevel level) {
		if (state.finished() || center == null) {
			return false;
		}
		EventConfig.Framework cfg = EventConfig.framework();
		int interval = Math.max(1, cfg.tickIntervalTicks);
		participants.refresh(level, center, cfg.abandonRadius, state == EventState.RUNNING);

		long now = level.getGameTime();
		if (participants.presentCount() > 0) {
			emptySinceTick = Long.MIN_VALUE;
			if (state == EventState.PAUSED) {
				setState(EventState.RUNNING);
				onResumed(level);
			}
		} else {
			if (emptySinceTick == Long.MIN_VALUE) {
				emptySinceTick = now;
			}
			long empty = now - emptySinceTick;
			if (empty >= cfg.failAfterEmptyTicks) {
				fail(level, Component.translatable("event.projecthero.abandoned", displayName()));
				return false;
			}
			if (empty >= cfg.pauseAfterEmptyTicks && state == EventState.RUNNING) {
				setState(EventState.PAUSED);
				onPaused(level);
			}
			// A paused event does no work at all -- this is what stops an abandoned raid from running
			// wave logic or spawning anything into chunks nobody is standing in.
			ageTicks += interval;
			return true;
		}

		ageTicks += interval;
		warnStragglers(level, cfg);
		onTick(level);
		return !state.finished();
	}

	/** Subclass content. Runs on the framework cadence, only while present players exist. */
	protected abstract void onTick(ServerLevel level);

	protected void onPaused(ServerLevel level) {
		broadcast(level, Component.translatable("event.projecthero.paused", displayName()));
	}

	protected void onResumed(ServerLevel level) {
		broadcast(level, Component.translatable("event.projecthero.resumed", displayName()));
	}

	/** Called exactly once, from {@link #complete} / {@link #fail}, after state has been set. */
	protected void onFinished(ServerLevel level, boolean success) {
	}

	public final void complete(ServerLevel level) {
		if (state.finished()) {
			return;
		}
		setState(EventState.COMPLETED);
		onFinished(level, true);
		cleanupOwnedMobs(level);
	}

	public final void fail(ServerLevel level, Component reason) {
		if (state.finished()) {
			return;
		}
		setState(EventState.FAILED);
		broadcast(level, reason);
		onFinished(level, false);
		cleanupOwnedMobs(level);
	}

	/** Force-stop without success or failure bookkeeping -- used by the debug commands. */
	public final void abort(ServerLevel level) {
		setState(EventState.FAILED);
		onAborted(level);
		cleanupOwnedMobs(level);
	}

	/** Called from {@link #abort} only. Subclasses use it to tear down UI ({@code EventBossBar} etc.)
	 *  that {@link #onFinished} would otherwise handle -- {@code abort} deliberately skips that hook. */
	protected void onAborted(ServerLevel level) {
	}

	/** Nudge anyone who has wandered past the warning radius but is still counted as present. */
	private void warnStragglers(ServerLevel level, EventConfig.Framework cfg) {
		double warnSq = cfg.warnRadius * cfg.warnRadius;
		for (ServerPlayer player : participants.present()) {
			double d = player.distanceToSqr(center.getX() + 0.5, player.getY(), center.getZ() + 0.5);
			if (d > warnSq) {
				player.displayClientMessage(Component.translatable("event.projecthero.leaving", displayName()), true);
			}
		}
	}

	// ---------------- owned mobs ----------------

	/**
	 * Register a mob as belonging to this event. Event mobs are marked persistent so a wave cannot
	 * quietly despawn out from under the counter, which is exactly why they must also be cleaned up
	 * when the event ends -- "leave abandoned raid entities loaded forever" is on the spec's
	 * do-not list (section 46).
	 */
	public final void own(Mob mob) {
		mob.setPersistenceRequired();
		mob.addTag(EVENT_TAG);
		ownedMobs.add(mob.getUUID());
	}

	/** Live count, pruning anything that has died or gone. Cheap: one UUID lookup per entry. */
	public final int ownedAlive(ServerLevel level) {
		int alive = 0;
		Iterator<UUID> it = ownedMobs.iterator();
		while (it.hasNext()) {
			Entity e = level.getEntity(it.next());
			if (e == null || !e.isAlive()) {
				it.remove();
				continue;
			}
			alive++;
		}
		return alive;
	}

	public final boolean owns(UUID entityId) {
		return ownedMobs.contains(entityId);
	}

	public final void disown(UUID entityId) {
		ownedMobs.remove(entityId);
	}

	protected final List<Mob> liveOwnedMobs(ServerLevel level) {
		List<Mob> out = new ArrayList<>();
		for (UUID uuid : ownedMobs) {
			if (level.getEntity(uuid) instanceof Mob mob && mob.isAlive()) {
				out.add(mob);
			}
		}
		return out;
	}

	/**
	 * Keep this event's mobs near its centre. A raider that has drifted (or been kited) more than
	 * {@code radius} blocks from the centre is walked back toward it; one that has ended up much
	 * further out -- stuck, pathing at nothing, or chasing someone who ran off -- is pulled straight
	 * back to a fresh spawn position and has its target dropped. A mob still actively fighting a
	 * player who is themselves near the village is left alone.
	 *
	 * <p>Cheap by construction: it iterates only the handful of live owned mobs, never the world.
	 */
	protected final void tetherOwnedMobs(ServerLevel level, double radius) {
		if (center == null) {
			return;
		}
		double cx = center.getX() + 0.5;
		double cz = center.getZ() + 0.5;
		double softSq = radius * radius;
		double hardSq = (radius + 40.0) * (radius + 40.0);
		double targetLeashSq = (radius + 16.0) * (radius + 16.0);
		for (Mob mob : liveOwnedMobs(level)) {
			// Leave bosses to their own AI -- teleporting one mid-fight is worse than a long chase.
			if (mob.getMaxHealth() >= 150.0f) {
				continue;
			}
			double d = mob.distanceToSqr(cx, mob.getY(), cz);
			if (d <= softSq) {
				continue;
			}
			Entity target = mob.getTarget();
			boolean chasingSomeoneNearby = target != null && target.isAlive()
					&& target.distanceToSqr(cx, target.getY(), cz) <= targetLeashSq;
			if (chasingSomeoneNearby && d <= hardSq) {
				continue;
			}
			if (d > hardSq) {
				BlockPos back = EventSpawns.findSpawn(level, center, participants.present(), level.getRandom());
				if (back != null) {
					mob.teleportTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5);
				}
				mob.getNavigation().stop();
				mob.setTarget(null);
			} else {
				mob.getNavigation().moveTo(cx, center.getY(), cz, 1.15);
			}
		}
	}

	/** Remove every mob this event still owns, and stop tracking them. */
	protected final void cleanupOwnedMobs(ServerLevel level) {
		for (UUID uuid : ownedMobs) {
			if (level.getEntity(uuid) instanceof Mob mob) {
				mob.discard();
			}
		}
		ownedMobs.clear();
	}

	// ---------------- messaging ----------------

	public final void broadcast(ServerLevel level, Component message) {
		for (ServerPlayer player : participants.onlineEligible(level)) {
			player.sendSystemMessage(message);
		}
	}

	public final void broadcastTitle(ServerLevel level, Component title, Component subtitle) {
		for (ServerPlayer player : participants.onlineEligible(level)) {
			player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
			if (subtitle != null) {
				player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
			}
			player.connection.send(new ClientboundSetTitleTextPacket(title));
		}
	}

	// ---------------- persistence ----------------

	public final CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putString("Type", typeId());
		tag.putUUID("Id", id);
		if (dimension != null) {
			tag.putString("Dimension", dimension.location().toString());
		}
		if (center != null) {
			tag.put("Center", NbtUtils.writeBlockPos(center));
		}
		tag.putString("State", state.name());
		tag.putLong("Age", ageTicks);
		tag.putLong("EmptySince", emptySinceTick);
		ListTag mobs = new ListTag();
		for (UUID uuid : ownedMobs) {
			mobs.add(NbtUtils.createUUID(uuid));
		}
		tag.put("OwnedMobs", mobs);
		participants.save(tag);
		saveExtra(tag);
		return tag;
	}

	public final void load(CompoundTag tag) {
		if (tag.contains("Dimension")) {
			ResourceLocation dim = ResourceLocation.tryParse(tag.getString("Dimension"));
			dimension = dim == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, dim);
		}
		if (tag.contains("Center")) {
			center = NbtUtils.readBlockPos(tag, "Center").orElse(BlockPos.ZERO);
		}
		try {
			state = EventState.valueOf(tag.getString("State"));
		} catch (IllegalArgumentException e) {
			state = EventState.RUNNING;
		}
		ageTicks = tag.getLong("Age");
		emptySinceTick = tag.contains("EmptySince") ? tag.getLong("EmptySince") : Long.MIN_VALUE;
		ownedMobs.clear();
		ListTag mobs = tag.getList("OwnedMobs", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < mobs.size(); i++) {
			ownedMobs.add(NbtUtils.loadUUID(mobs.get(i)));
		}
		participants.load(tag);
		loadExtra(tag);
	}

	protected void saveExtra(CompoundTag tag) {
	}

	protected void loadExtra(CompoundTag tag) {
	}
}
