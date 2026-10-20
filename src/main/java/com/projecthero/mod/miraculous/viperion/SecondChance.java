package com.projecthero.mod.miraculous.viperion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.squad.Squads;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Viperion's special, SECOND CHANCE (Shift+Z). The first press sets a CHECKPOINT and starts the Miraculous
 * countdown; for {@link ViperionConfig#SECOND_CHANCE_WINDOW} (90 s, user) Shift+Z again REWINDS THE WHOLE WORLD back to
 * that moment, {@link ViperionConfig#SECOND_CHANCE_REWINDS} times, then it is spent.
 *
 * <p>v0.16.7 (user): <b>the entire world, every dimension</b> -- not a bubble round the checkpoint. Snapshotting a whole
 * world would freeze the server, so it is lazy:
 * <ul>
 *   <li>at the checkpoint: a cheap note (position, health, effects, fire, air) of every loaded creature in every
 *       dimension -- no save data;</li>
 *   <li>the moment anything is about to DIE ({@code ALLOW_DEATH}, anywhere): its full save data is copied
 *       ({@link #onLethal}), so the rewind can rebuild it -- with its gear, name, villager trades... A creature that was
 *       in an unloaded chunk at the checkpoint and wandered in later comes back too, where it fell;</li>
 *   <li>every block change in every loaded chunk ({@code CityLevelChunkMixin}): the first old state per spot, AND the
 *       block entity's data (a broken chest comes back with what was in it);</li>
 *   <li>everything ADDED to the world since ({@code SecondChanceSpawnMixin} on {@code addFreshEntity}): new hostiles,
 *       drops, XP orbs and arrows are removed by the rewind, wherever they are (never a pet, never an item a player
 *       threw);</li>
 *   <li>a block or a creature whose chunk is unloaded when the rewind comes is put back the moment that chunk loads
 *       ({@link #PENDING}).</li>
 * </ul>
 * Players: him, and every squadmate anywhere (other players are never moved -- fair in multiplayer); position, health,
 * hunger, effects, fire, air and every power timer ({@link TimeRewind}), not inventories. Bosses are put back where they
 * were with the health they had, but a dead boss stays dead.
 *
 * <p>A killing blow while a checkpoint is live rewinds instead ({@link #cheatDeath}, Fabric's {@code ALLOW_DEATH}), using
 * one rewind. {@code /kill} still kills. The checkpoint lives here (server only); the HUD reads the synced
 * {@code vp_cp_*} values.
 */
public final class SecondChance {
	private record PlayerSnap(UUID id, ResourceKey<Level> dim, Vec3 pos, float yRot, float xRot, float health, float absorption, int food,
			float saturation, List<MobEffectInstance> effects, int fire, int air, TimeRewind.Snap powers) {
	}

	/** A creature as it was at the checkpoint (cheap: no save data). */
	private record MobSnap(UUID id, ResourceKey<Level> dim, Vec3 pos, float yRot, float xRot, float health, boolean boss,
			List<MobEffectInstance> effects, int fire, int air) {
	}

	/** A creature that died since: its save data from the moment before it died, and where it fell. */
	private record Dead(ResourceKey<Level> dim, CompoundTag tag, Vec3 pos, float yRot, float xRot) {
	}

	/** A block spot as it was: the state and (if it had one) its block entity's data. */
	record BlockMemo(BlockState state, @Nullable CompoundTag be) {
	}

	static final class Checkpoint {
		final ResourceKey<Level> dim;
		final Vec3 center;
		final long at;
		long until;
		int left;
		long lastRewind;
		final PlayerSnap self;
		final List<PlayerSnap> mates;
		final Map<UUID, MobSnap> mobs;
		final Map<UUID, Dead> dead = new HashMap<>();
		/** Everything added to the world since the checkpoint. */
		final Set<UUID> spawned = new HashSet<>();
		final Map<ResourceKey<Level>, Long2ObjectLinkedOpenHashMap<BlockMemo>> blocks = new HashMap<>();
		int blockCount;

		Checkpoint(ResourceKey<Level> dim, Vec3 center, long at, long until, PlayerSnap self, List<PlayerSnap> mates, Map<UUID, MobSnap> mobs) {
			this.dim = dim;
			this.center = center;
			this.at = at;
			this.until = until;
			this.left = ViperionConfig.SECOND_CHANCE_REWINDS;
			this.self = self;
			this.mates = mates;
			this.mobs = mobs;
		}
	}

	/** What a rewind could not put back yet (its chunk was unloaded): applied the moment the chunk loads. */
	private static final class Pending {
		final Long2ObjectLinkedOpenHashMap<BlockMemo> blocks = new Long2ObjectLinkedOpenHashMap<>();
		final List<Runnable> mobs = new ArrayList<>();
	}

	private record ChunkKey(ResourceKey<Level> dim, long chunk) {
	}

	private static final Map<UUID, Checkpoint> CHECKPOINTS = new ConcurrentHashMap<>();
	private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Pending>> PENDING = new HashMap<>();
	private static final ArrayDeque<ChunkKey> READY = new ArrayDeque<>();
	/** The block entity data of the spot being changed right now (HEAD of {@code setBlockState} -> its RETURN). */
	private static final ArrayDeque<Object[]> BE_STACK = new ArrayDeque<>();
	private static int restoring;

	private SecondChance() {
	}

	static void clearSessionState() {
		CHECKPOINTS.clear();
		PENDING.clear();
		READY.clear();
		BE_STACK.clear();
		restoring = 0;
	}

	static void forget(UUID id) {
		CHECKPOINTS.remove(id);
	}

	/** Detransformation, death, logout: the checkpoint goes. */
	static void drop(ServerPlayer p) {
		CHECKPOINTS.remove(p.getUUID());
	}

	/** Server: a checkpoint is live for him (window open, rewinds left). */
	public static boolean live(ServerPlayer p) {
		Checkpoint cp = CHECKPOINTS.get(p.getUUID());
		return cp != null && cp.left > 0 && p.level().getGameTime() < cp.until && cp.dim == p.level().dimension();
	}

	/** Gametests: age the live checkpoint by {@code ticks} (its window closes that much sooner). */
	public static void ageCheckpoint(ServerPlayer p, long ticks) {
		Checkpoint cp = CHECKPOINTS.get(p.getUUID());
		if (cp != null) {
			cp.until -= ticks;
		}
	}

	/** Gametests: how many block spots the live checkpoint remembers (every dimension). */
	public static int trackedBlocks(ServerPlayer p) {
		Checkpoint cp = CHECKPOINTS.get(p.getUUID());
		return cp == null ? 0 : cp.blockCount;
	}

	/** Gametests: how many creatures the live checkpoint noted (every dimension). */
	public static int trackedMobs(ServerPlayer p) {
		Checkpoint cp = CHECKPOINTS.get(p.getUUID());
		return cp == null ? 0 : cp.mobs.size();
	}

	/** Gametests: chunks still waiting for a rewind's leftovers. */
	public static int pendingChunks() {
		int n = 0;
		for (var m : PENDING.values()) {
			n += m.size();
		}
		return n;
	}

	// ---------------------------------------------------------------- the snapshots

	private static List<MobEffectInstance> effects(LivingEntity e) {
		List<MobEffectInstance> out = new ArrayList<>();
		for (MobEffectInstance i : e.getActiveEffects()) {
			out.add(new MobEffectInstance(i));
		}
		return out;
	}

	private static PlayerSnap snap(ServerPlayer p) {
		return new PlayerSnap(p.getUUID(), p.level().dimension(), p.position(), p.getYRot(), p.getXRot(), p.getHealth(), p.getAbsorptionAmount(),
				p.getFoodData().getFoodLevel(), p.getFoodData().getSaturationLevel(), effects(p), p.getRemainingFireTicks(), p.getAirSupply(),
				TimeRewind.snap(p));
	}

	private static MobSnap snap(LivingEntity e) {
		return new MobSnap(e.getUUID(), e.level().dimension(), e.position(), e.getYRot(), e.getXRot(), e.getHealth(), ViperionMoves.isBoss(e), effects(e),
				e.getRemainingFireTicks(), e.getAirSupply());
	}

	private static boolean noted(Entity e) {
		return e instanceof LivingEntity le && le.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand);
	}

	/** First Shift+Z: the checkpoint, here and now, for the whole world. Starts the Miraculous countdown. */
	public static boolean checkpoint(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		Vec3 c = p.position();
		// every loaded creature, every dimension -- his own dimension first, nearest first, in case of the cap
		Map<UUID, MobSnap> mobs = new LinkedHashMap<>();
		List<LivingEntity> here = new ArrayList<>();
		for (Entity e : level.getAllEntities()) {
			if (noted(e)) {
				here.add((LivingEntity) e);
			}
		}
		here.sort((a, b) -> Double.compare(a.distanceToSqr(c), b.distanceToSqr(c)));
		for (LivingEntity e : here) {
			if (mobs.size() >= ViperionConfig.SECOND_CHANCE_MAX_MOBS) {
				break;
			}
			mobs.put(e.getUUID(), snap(e));
		}
		for (ServerLevel other : p.server.getAllLevels()) {
			if (other == level) {
				continue;
			}
			for (Entity e : other.getAllEntities()) {
				if (mobs.size() >= ViperionConfig.SECOND_CHANCE_MAX_MOBS) {
					break;
				}
				if (noted(e)) {
					mobs.put(e.getUUID(), snap((LivingEntity) e));
				}
			}
		}
		// his squad, wherever they are
		List<PlayerSnap> mates = new ArrayList<>();
		for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
			if (o != p && o.isAlive() && !o.isSpectator() && Squads.areAllies(p, o)) {
				mates.add(snap(o));
			}
		}
		long until = now + ViperionConfig.SECOND_CHANCE_WINDOW;
		Miraculous.edit(p, n -> {
			n.vals.put(Viperion.V_CP_UNTIL, until);
			n.vals.put(Viperion.V_CP_LEFT, (long) ViperionConfig.SECOND_CHANCE_REWINDS);
			n.vals.put(Viperion.V_CHECKPOINT_AT, now);
			n.vecs.put(Viperion.X_CP, List.of(c.x, c.y, c.z));
		});
		Miraculous.setAnim(p, Viperion.ANIM_CHECKPOINT);
		Miraculous.setCooldown(p, ViperionConfig.SECOND_CHANCE, ViperionConfig.SECOND_CHANCE_COOLDOWN);
		Miraculous.startCountdown(p);
		// (him last: his snapshot holds the countdown and the cooldown just started -- a rewind restarts both)
		Checkpoint cp = new Checkpoint(level.dimension(), c, now, until, snap(p), mates, mobs);
		CHECKPOINTS.put(p.getUUID(), cp);
		// the green flash: a clock face of light round him, a chime
		clockRipple(level, c, 2.2);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.4f, 0.8f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.2f, 1.2f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.6f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.viperion.checkpoint",
				ViperionConfig.SECOND_CHANCE_WINDOW / 20, ViperionConfig.SECOND_CHANCE_REWINDS).withStyle(ChatFormatting.GREEN), true);
		return true;
	}

	// ---------------------------------------------------------------- the rewind

	/** Shift+Z again (or, {@code auto}, a killing blow): the whole world back to the checkpoint. False if there was nothing to rewind to. */
	public static boolean rewind(ServerPlayer p, boolean auto) {
		Checkpoint cp = CHECKPOINTS.get(p.getUUID());
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		if (cp == null || cp.left <= 0 || now >= cp.until || cp.dim != level.dimension()) {
			return false;
		}
		if (!auto && cp.lastRewind > 0L && now - cp.lastRewind < ViperionConfig.SECOND_CHANCE_REWIND_GAP) {
			return false;
		}
		MinecraftServer server = p.server;
		Vec3 from = p.position();
		restoring++;
		try {
			// 1. the blocks (first: the creatures are put back on the old ground)
			restoreBlocks(server, cp, p);
			// 2. what was added since: new hostiles, drops, orbs, arrows -- wherever they are
			removeNew(server, cp);
			// 3. the creatures still alive: back where they were, as they were
			for (MobSnap m : cp.mobs.values()) {
				ServerLevel ml = server.getLevel(m.dim());
				if (ml == null) {
					continue;
				}
				if (ml.getEntity(m.id()) instanceof LivingEntity le && le.isAlive() && !le.isRemoved() && ml.hasChunkAt(BlockPos.containing(m.pos()))
						&& le.getPassengers().stream().noneMatch(Player.class::isInstance)) {
					// (never one carrying a player: other players are not moved)
					restore(le, m, near(p, le));
				}
			}
			// 4. the dead: rebuilt (never a boss); an unloaded chunk waits until it loads
			for (var en : new ArrayList<>(cp.dead.entrySet())) {
				UUID id = en.getKey();
				Dead d = en.getValue();
				MobSnap m = cp.mobs.get(id);
				ServerLevel ml = server.getLevel(m != null ? m.dim() : d.dim());
				if (ml == null) {
					continue;
				}
				Vec3 at = m != null ? m.pos() : d.pos();
				cp.dead.remove(id);
				Runnable revive = () -> {
					LivingEntity back = revive(ml, d, m);
					if (back != null && !back.getUUID().equals(id)) {
						// a new id: the next rewind restores this one (never a second copy)
						Checkpoint live = CHECKPOINTS.get(p.getUUID());
						if (live != null && m != null) {
							live.mobs.remove(id);
							live.mobs.put(back.getUUID(), new MobSnap(back.getUUID(), m.dim(), m.pos(), m.yRot(), m.xRot(), m.health(), m.boss(), m.effects(),
									m.fire(), m.air()));
						}
					}
				};
				if (ml.hasChunkAt(BlockPos.containing(at))) {
					revive.run();
				} else {
					pending(ml.dimension(), new ChunkPos(BlockPos.containing(at)).toLong()).mobs.add(revive);
				}
			}
		} finally {
			restoring--;
		}
		cp.spawned.clear();
		// 5. the squad, then him -- and every timer of their powers (v0.16.7, user: as if time itself went back)
		for (PlayerSnap s : cp.mates) {
			ServerPlayer o = server.getPlayerList().getPlayer(s.id());
			ServerLevel sl = server.getLevel(s.dim());
			if (o != null && sl != null && o.isAlive() && !o.isSpectator() && Squads.areAllies(p, o)) {
				restorePlayer(sl, o, s);
			}
		}
		ViperionMoves.forget(p.getUUID()); // what he was doing since is undone (the timers come back from the snapshot)
		restorePlayer(level, p, cp.self);
		cp.left--;
		cp.lastRewind = now;
		// v0.16.7 (user): the window starts again -- a full 90 s from the rewind
		cp.until = now + ViperionConfig.SECOND_CHANCE_WINDOW;
		int left = cp.left;
		long until = cp.until;
		Miraculous.edit(p, n -> {
			n.vals.put(Viperion.V_REWIND_AT, now);
			if (left > 0) {
				n.vals.put(Viperion.V_CP_LEFT, (long) left);
				n.vals.put(Viperion.V_CP_UNTIL, until);
			} else {
				n.vals.remove(Viperion.V_CP_LEFT);
				n.vals.remove(Viperion.V_CP_UNTIL);
				n.vecs.remove(Viperion.X_CP);
			}
		});
		Miraculous.setAnim(p, Viperion.ANIM_REWIND);
		// the time-reverse: a green clock ripple where he was and where he lands, a reversed whoosh
		clockRipple(level, from, 1.6);
		clockRipple(level, cp.center, 3.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, cp.center.x, cp.center.y + 1.0, cp.center.z, 60, 0.6, 1.0, 0.6, 0.15);
		level.playSound(null, cp.center.x, cp.center.y, cp.center.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.5f);
		level.playSound(null, cp.center.x, cp.center.y, cp.center.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, cp.center.x, cp.center.y, cp.center.z, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, cp.center.x, cp.center.y, cp.center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.5f);
		String key = auto ? "message.projecthero.viperion.rewind_auto" : "message.projecthero.viperion.rewind";
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable(left > 0 ? key : key + "_spent", left).withStyle(ChatFormatting.GREEN), true);
		if (left <= 0) {
			CHECKPOINTS.remove(p.getUUID());
		}
		return true;
	}

	/** Sparkles only where someone could see them (the rest of the world rewinds quietly). */
	private static boolean near(ServerPlayer p, Entity e) {
		return e.level() == p.level() && e.distanceToSqr(p) < ViperionConfig.SECOND_CHANCE_FX_RANGE * ViperionConfig.SECOND_CHANCE_FX_RANGE;
	}

	private static void removeNew(MinecraftServer server, Checkpoint cp) {
		for (UUID id : cp.spawned) {
			Entity e = null;
			for (ServerLevel l : server.getAllLevels()) {
				e = l.getEntity(id);
				if (e != null) {
					break;
				}
			}
			if (e == null || e.isRemoved()) {
				continue;
			}
			boolean gone = false;
			if (e instanceof Mob m && m instanceof Enemy && !ViperionMoves.isBoss(m) && !(m instanceof OwnableEntity o && o.getOwnerUUID() != null)) {
				gone = true;
			} else if (e instanceof ItemEntity item && item.getOwner() == null) {
				gone = true;
			} else if (e instanceof ExperienceOrb || e instanceof AbstractArrow) {
				gone = true;
			}
			if (gone) {
				if (e.level() instanceof ServerLevel sl && sl.random.nextInt(3) == 0) {
					sl.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 0.5, e.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
				}
				e.discard();
			}
		}
	}

	/** A dead creature, rebuilt from the save data it had the moment before it died. */
	private static @Nullable LivingEntity revive(ServerLevel level, Dead d, @Nullable MobSnap m) {
		Entity still = level.getEntity(d.tag().hasUUID("UUID") ? d.tag().getUUID("UUID") : UUID.randomUUID());
		if (still != null) {
			if (still.isAlive() && !still.isRemoved()) {
				return null; // it came back some other way
			}
			still.discard(); // still in its death fall
		}
		Entity back = EntityType.create(d.tag().copy(), level).orElse(null);
		if (!(back instanceof LivingEntity bl)) {
			return null;
		}
		if (level.getEntity(bl.getUUID()) != null) {
			bl.setUUID(UUID.randomUUID());
		}
		Vec3 at = m != null ? m.pos() : d.pos();
		bl.moveTo(at.x, at.y, at.z, m != null ? m.yRot() : d.yRot(), m != null ? m.xRot() : d.xRot());
		bl.deathTime = 0;
		bl.hurtTime = 0;
		bl.removeAllEffects();
		bl.clearFire();
		bl.setDeltaMovement(Vec3.ZERO);
		bl.resetFallDistance();
		if (m != null) {
			bl.setHealth(Math.min(bl.getMaxHealth(), Math.max(1f, m.health())));
			for (MobEffectInstance i : m.effects()) {
				bl.addEffect(new MobEffectInstance(i));
			}
			bl.setRemainingFireTicks(m.fire());
			bl.setAirSupply(m.air());
		} else {
			bl.setHealth(bl.getMaxHealth()); // it wandered in after the checkpoint: back as it was before the fight
		}
		restoring++;
		try {
			if (!level.addFreshEntity(bl)) {
				return null;
			}
		} finally {
			restoring--;
		}
		if (level.random.nextInt(2) == 0) {
			level.sendParticles(ViperionMoves.GREEN, bl.getX(), bl.getY() + bl.getBbHeight() * 0.5, bl.getZ(), 16, 0.3, 0.5, 0.3, 0.0);
		}
		return bl;
	}

	private static void restore(LivingEntity e, MobSnap m, boolean fx) {
		if (e.isPassenger()) {
			e.stopRiding();
		}
		e.teleportTo(m.pos().x, m.pos().y, m.pos().z);
		e.setYRot(m.yRot());
		e.setXRot(m.xRot());
		e.setYHeadRot(m.yRot());
		e.setYBodyRot(m.yRot());
		e.setDeltaMovement(Vec3.ZERO);
		e.hurtMarked = true;
		e.resetFallDistance();
		e.clearFire();
		e.setHealth(Math.min(e.getMaxHealth(), Math.max(1f, m.health())));
		if (m.fire() > 0) {
			e.setRemainingFireTicks(m.fire());
		}
		e.setAirSupply(m.air());
		e.hurtTime = 0;
		e.invulnerableTime = 0;
		e.removeAllEffects();
		for (MobEffectInstance i : m.effects()) {
			e.addEffect(new MobEffectInstance(i));
		}
		if (e instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (fx) {
			((ServerLevel) e.level()).sendParticles(ViperionMoves.GREEN, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
		}
	}

	private static void restorePlayer(ServerLevel level, ServerPlayer o, PlayerSnap s) {
		if (o.isPassenger()) {
			o.stopRiding();
		}
		o.teleportTo(level, s.pos().x, s.pos().y, s.pos().z, s.yRot(), s.xRot());
		o.setDeltaMovement(Vec3.ZERO);
		o.hurtMarked = true;
		o.resetFallDistance();
		o.setHealth(Math.min(o.getMaxHealth(), Math.max(1f, s.health())));
		o.setAbsorptionAmount(s.absorption());
		o.getFoodData().setFoodLevel(s.food());
		o.getFoodData().setSaturation(s.saturation());
		o.removeAllEffects();
		for (MobEffectInstance i : s.effects()) {
			o.addEffect(new MobEffectInstance(i));
		}
		o.setRemainingFireTicks(s.fire());
		o.setAirSupply(s.air());
		// the power timers: the Miraculous countdown, every cooldown, the kit timers, the Kwami, a Quirk
		TimeRewind.restore(o, s.powers());
	}

	private static void restoreBlocks(MinecraftServer server, Checkpoint cp, ServerPlayer p) {
		int fx = 0;
		for (var dimEntry : cp.blocks.entrySet()) {
			ServerLevel level = server.getLevel(dimEntry.getKey());
			if (level == null) {
				continue;
			}
			for (var en : dimEntry.getValue().long2ObjectEntrySet()) {
				BlockPos bp = BlockPos.of(en.getLongKey());
				if (!level.hasChunkAt(bp)) {
					pending(level.dimension(), ChunkPos.asLong(SectionPos.blockToSectionCoord(bp.getX()), SectionPos.blockToSectionCoord(bp.getZ())))
							.blocks.put(en.getLongKey(), en.getValue());
					continue;
				}
				putBack(level, bp, en.getValue());
				if (fx < ViperionConfig.SECOND_CHANCE_FX_BLOCKS && level == p.level() && level.random.nextInt(4) == 0
						&& bp.distToCenterSqr(p.position()) < ViperionConfig.SECOND_CHANCE_FX_RANGE * ViperionConfig.SECOND_CHANCE_FX_RANGE) {
					fx++;
					level.sendParticles(ViperionMoves.TEAL, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
				}
			}
		}
		cp.blocks.clear();
		cp.blockCount = 0;
	}

	/** One spot back as it was -- the block and what its block entity held. */
	private static void putBack(ServerLevel level, BlockPos bp, BlockMemo memo) {
		restoring++;
		try {
			if (level.getBlockState(bp) != memo.state()) {
				level.setBlock(bp, memo.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			if (memo.be() != null) {
				BlockEntity be = level.getBlockEntity(bp);
				if (be != null) {
					be.loadWithComponents(memo.be(), level.registryAccess());
					be.setChanged();
					level.sendBlockUpdated(bp, memo.state(), memo.state(), Block.UPDATE_ALL);
				}
			}
		} finally {
			restoring--;
		}
	}

	private static Pending pending(ResourceKey<Level> dim, long chunk) {
		return PENDING.computeIfAbsent(dim, k -> new Long2ObjectOpenHashMap<>()).computeIfAbsent(chunk, k -> new Pending());
	}

	/** A clock face of green light: a ring, twelve marks, two hands. */
	static void clockRipple(ServerLevel level, Vec3 c, double r) {
		ViperionMoves.ring(level, ViperionMoves.GREEN, c.add(0, 0.1, 0), r, 36);
		ViperionMoves.ring(level, ViperionMoves.TEAL, c.add(0, 0.15, 0), r * 0.6, 20);
		for (int i = 0; i < 12; i++) {
			double a = Math.PI * 2 * i / 12;
			level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r * 0.85, c.y + 0.12, c.z + Math.sin(a) * r * 0.85, 1, 0, 0.02, 0, 0.0);
		}
		for (int k = 1; k <= 6; k++) {
			double t = k / 6.0;
			level.sendParticles(ViperionMoves.GREEN, c.x + t * r * 0.7, c.y + 0.14, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ViperionMoves.GREEN, c.x, c.y + 0.14, c.z - t * r * 0.45, 1, 0, 0, 0, 0);
		}
		level.sendParticles(ViperionMoves.GREEN, c.x, c.y + 1.0, c.z, 30, 0.5, 0.9, 0.5, 0.0);
	}

	// ---------------------------------------------------------------- the killing blow

	/** {@code ALLOW_DEATH}: a lethal hit while a checkpoint is live rewinds instead. True if the death was undone. */
	public static boolean cheatDeath(ServerPlayer p, DamageSource source) {
		if (!Miraculous.transformedAs(p, Kwami.SASS) || p.isSpectator() || source.is(DamageTypes.GENERIC_KILL)) {
			return false;
		}
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return false;
		}
		if (!live(p)) {
			return false;
		}
		return rewind(p, true);
	}

	// ---------------------------------------------------------------- the world-wide hooks (each one empty-map check when no checkpoint is live)

	/** {@code ALLOW_DEATH}, any creature anywhere: about to die -- its save data, for the rewind to rebuild it. */
	public static void onLethal(LivingEntity e) {
		if (CHECKPOINTS.isEmpty() || restoring > 0 || e.level().isClientSide() || e instanceof Player || e instanceof ArmorStand
				|| ViperionMoves.isBoss(e) || !e.getType().canSerialize()) {
			return;
		}
		UUID id = e.getUUID();
		long now = e.level().getGameTime();
		CompoundTag tag = null;
		for (Checkpoint cp : CHECKPOINTS.values()) {
			if (now >= cp.until || cp.spawned.contains(id) || cp.dead.containsKey(id)) {
				continue;
			}
			if (tag == null) {
				tag = new CompoundTag();
				tag.putString("id", EntityType.getKey(e.getType()).toString());
				e.saveWithoutId(tag);
				tag.remove("Passengers");
			}
			cp.dead.put(id, new Dead(e.level().dimension(), tag, e.position(), e.getYRot(), e.getXRot()));
		}
	}

	/** {@code SecondChanceSpawnMixin}: something was added to the world (not loaded from disk). */
	public static void onFreshEntity(Entity e) {
		if (CHECKPOINTS.isEmpty() || restoring > 0 || e instanceof Player) {
			return;
		}
		long now = e.level().getGameTime();
		for (Checkpoint cp : CHECKPOINTS.values()) {
			if (now < cp.until && cp.spawned.size() < ViperionConfig.SECOND_CHANCE_MAX_NEW) {
				cp.spawned.add(e.getUUID());
			}
		}
	}

	/** {@code CityLevelChunkMixin}, HEAD of {@code setBlockState}: the spot's block entity data, before it changes. */
	public static void beforeBlockChange(Level level, BlockPos pos) {
		if (CHECKPOINTS.isEmpty() || level.isClientSide()) {
			return;
		}
		CompoundTag be = null;
		if (restoring == 0 && !rememberedEverywhere(level.dimension(), pos.asLong())) {
			BlockEntity b = level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
			if (b != null) {
				be = b.saveWithFullMetadata(level.registryAccess());
			}
		}
		BE_STACK.push(new Object[] { pos.asLong(), be });
	}

	private static boolean rememberedEverywhere(ResourceKey<Level> dim, long key) {
		for (Checkpoint cp : CHECKPOINTS.values()) {
			var m = cp.blocks.get(dim);
			if (m == null || !m.containsKey(key)) {
				return false;
			}
		}
		return true;
	}

	/** {@code CityLevelChunkMixin}, RETURN of {@code setBlockState}: a block changed (old null: nothing did). Must stay cheap. */
	public static void onBlockChanged(Level level, BlockPos pos, @Nullable BlockState old, BlockState neu) {
		CompoundTag be = null;
		if (!BE_STACK.isEmpty() && (long) BE_STACK.peek()[0] == pos.asLong()) {
			be = (CompoundTag) BE_STACK.pop()[1];
		}
		if (old == null || CHECKPOINTS.isEmpty() || restoring > 0 || level.isClientSide() || old == neu) {
			return;
		}
		ResourceKey<Level> dim = level.dimension();
		long now = level.getGameTime();
		long key = pos.asLong();
		for (Checkpoint cp : CHECKPOINTS.values()) {
			if (now >= cp.until || cp.blockCount >= ViperionConfig.SECOND_CHANCE_MAX_BLOCKS) {
				continue;
			}
			var m = cp.blocks.computeIfAbsent(dim, k -> new Long2ObjectLinkedOpenHashMap<>());
			if (!m.containsKey(key)) {
				m.put(key, new BlockMemo(old, be));
				cp.blockCount++;
			}
		}
	}

	// ---------------------------------------------------------------- ticks

	static void tick(ServerPlayer p) {
		Checkpoint cp = CHECKPOINTS.get(p.getUUID());
		if (cp == null) {
			return;
		}
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		if (now >= cp.until || cp.dim != level.dimension()) {
			CHECKPOINTS.remove(p.getUUID());
			Miraculous.edit(p, n -> {
				n.vals.remove(Viperion.V_CP_UNTIL);
				n.vals.remove(Viperion.V_CP_LEFT);
				n.vecs.remove(Viperion.X_CP);
			});
			p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.projecthero.viperion.checkpoint_gone").withStyle(ChatFormatting.GRAY), true);
			p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, 0.6f);
			return;
		}
		// the marker where the checkpoint was set: a slow green ring
		if (now % 10 == 0) {
			double a = now * 0.15;
			for (int i = 0; i < 6; i++) {
				double b = a + i * Math.PI / 3;
				level.sendParticles(ViperionMoves.GREEN, cp.center.x + Math.cos(b) * 0.7, cp.center.y + 0.1, cp.center.z + Math.sin(b) * 0.7, 1, 0, 0, 0, 0);
			}
		}
		long left = cp.until - now;
		if (left > 0 && left <= 200 && left % 20 == 0) {
			p.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.4f, 1.6f);
		}
	}

	/** {@code CHUNK_LOAD}: a chunk with leftovers from a rewind -- put back on the next server tick. */
	static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		if (PENDING.isEmpty()) {
			return;
		}
		var m = PENDING.get(level.dimension());
		if (m != null && m.containsKey(chunk.getPos().toLong())) {
			READY.add(new ChunkKey(level.dimension(), chunk.getPos().toLong()));
		}
	}

	static void serverTick(MinecraftServer server) {
		BE_STACK.clear(); // (always paired within a tick; never let a thrown setBlock leave one behind)
		while (!READY.isEmpty()) {
			ChunkKey k = READY.poll();
			ServerLevel level = server.getLevel(k.dim());
			var m = PENDING.get(k.dim());
			if (level == null || m == null) {
				continue;
			}
			ChunkPos cp = new ChunkPos(k.chunk());
			if (!level.hasChunk(cp.x, cp.z)) {
				continue; // unloaded again before we got to it: it waits for the next load
			}
			Pending pend = m.remove(k.chunk());
			if (m.isEmpty()) {
				PENDING.remove(k.dim());
			}
			if (pend == null) {
				continue;
			}
			for (var en : pend.blocks.long2ObjectEntrySet()) {
				putBack(level, BlockPos.of(en.getLongKey()), en.getValue());
			}
			restoring++;
			try {
				for (Runnable r : pend.mobs) {
					r.run();
				}
			} finally {
				restoring--;
			}
		}
		if (CHECKPOINTS.isEmpty()) {
			return;
		}
		CHECKPOINTS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}
}
