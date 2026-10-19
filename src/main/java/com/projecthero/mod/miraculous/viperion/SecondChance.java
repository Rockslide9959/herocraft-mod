package com.projecthero.mod.miraculous.viperion;

import java.util.ArrayList;
import java.util.HashSet;
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

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Viperion's special, SECOND CHANCE (Shift+Z). The first press sets a CHECKPOINT and starts the Miraculous
 * countdown; for {@link ViperionConfig#SECOND_CHANCE_WINDOW} (90 s, user) Shift+Z again REWINDS everything near it back
 * to that moment, {@link ViperionConfig#SECOND_CHANCE_REWINDS} times, then it is spent:
 * <ul>
 *   <li>him: position, rotation, health, absorption, hunger / saturation, effects, fire, air (not his inventory);</li>
 *   <li>his squadmates who were within {@link ViperionConfig#SECOND_CHANCE_RADIUS}: position, rotation, health (other
 *       players are never moved -- fair in multiplayer);</li>
 *   <li>every creature that was within the radius: back where it was with its health and effects then; one that died
 *       since comes back (rebuilt from its saved data -- never a boss); hostile mobs that appeared since are removed,
 *       and so are the item drops / XP orbs that appeared since (not items a player threw);</li>
 *   <li>the blocks broken, placed or changed within the radius since: put back (the same hook as Ladybug's
 *       {@code LadybugRestore}: {@code CityLevelChunkMixin}, first old state per spot).</li>
 * </ul>
 * A killing blow while a checkpoint is live rewinds instead ({@link #cheatDeath}, Fabric's {@code ALLOW_DEATH}), using
 * one rewind. {@code /kill} still kills. The checkpoint itself lives here (server only); the HUD reads the synced
 * {@code vp_cp_*} values.
 */
public final class SecondChance {
	private record PlayerSnap(UUID id, Vec3 pos, float yRot, float xRot, float health, float absorption, int food, float saturation,
			List<MobEffectInstance> effects, int fire, int air) {
	}

	private record MobSnap(UUID id, Vec3 pos, float yRot, float xRot, float health, boolean boss, @Nullable CompoundTag tag, List<MobEffectInstance> effects) {
	}

	static final class Checkpoint {
		final ResourceKey<Level> dim;
		final Vec3 center;
		long until;
		int left;
		long lastRewind;
		final PlayerSnap self;
		final List<PlayerSnap> mates;
		final List<MobSnap> mobs;
		/** Every creature / item / orb that existed at the checkpoint (what appeared since is new). */
		final Set<UUID> known;
		final Long2ObjectLinkedOpenHashMap<BlockState> blocks = new Long2ObjectLinkedOpenHashMap<>();

		Checkpoint(ResourceKey<Level> dim, Vec3 center, long until, PlayerSnap self, List<PlayerSnap> mates, List<MobSnap> mobs, Set<UUID> known) {
			this.dim = dim;
			this.center = center;
			this.until = until;
			this.left = ViperionConfig.SECOND_CHANCE_REWINDS;
			this.self = self;
			this.mates = mates;
			this.mobs = mobs;
			this.known = known;
		}
	}

	private static final Map<UUID, Checkpoint> CHECKPOINTS = new ConcurrentHashMap<>();
	private static int restoring;

	private SecondChance() {
	}

	static void clearSessionState() {
		CHECKPOINTS.clear();
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

	/** Gametests: how many block spots the live checkpoint remembers. */
	public static int trackedBlocks(ServerPlayer p) {
		Checkpoint cp = CHECKPOINTS.get(p.getUUID());
		return cp == null ? 0 : cp.blocks.size();
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
		return new PlayerSnap(p.getUUID(), p.position(), p.getYRot(), p.getXRot(), p.getHealth(), p.getAbsorptionAmount(), p.getFoodData().getFoodLevel(),
				p.getFoodData().getSaturationLevel(), effects(p), p.getRemainingFireTicks(), p.getAirSupply());
	}

	private static MobSnap snap(LivingEntity e) {
		boolean boss = ViperionMoves.isBoss(e);
		CompoundTag tag = null;
		if (!boss && e.getType().canSerialize()) {
			tag = new CompoundTag();
			tag.putString("id", EntityType.getKey(e.getType()).toString());
			e.saveWithoutId(tag);
			tag.remove("Passengers");
		}
		return new MobSnap(e.getUUID(), e.position(), e.getYRot(), e.getXRot(), e.getHealth(), boss, tag, effects(e));
	}

	/** First Shift+Z: the checkpoint, here and now. Starts the Miraculous countdown. */
	public static boolean checkpoint(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		Vec3 c = p.position();
		double r = ViperionConfig.SECOND_CHANCE_RADIUS;
		AABB box = new AABB(c, c).inflate(r);
		Set<UUID> known = new HashSet<>();
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e.isAlive() && !(e instanceof Player) && e.distanceToSqr(c) <= r * r && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand));
		near.sort((a, b) -> Double.compare(a.distanceToSqr(c), b.distanceToSqr(c)));
		List<MobSnap> mobs = new ArrayList<>();
		for (LivingEntity e : near) {
			known.add(e.getUUID());
			if (mobs.size() < ViperionConfig.SECOND_CHANCE_MAX_MOBS) {
				mobs.add(snap(e));
			}
		}
		for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof Mob)) {
			known.add(e.getUUID());
		}
		List<PlayerSnap> mates = new ArrayList<>();
		for (ServerPlayer o : level.getEntitiesOfClass(ServerPlayer.class, box, o -> o != p && o.isAlive() && !o.isSpectator() && Squads.areAllies(p, o))) {
			mates.add(snap(o));
		}
		Checkpoint cp = new Checkpoint(level.dimension(), c, now + ViperionConfig.SECOND_CHANCE_WINDOW, snap(p), mates, mobs, known);
		CHECKPOINTS.put(p.getUUID(), cp);
		Miraculous.edit(p, n -> {
			n.vals.put(Viperion.V_CP_UNTIL, cp.until);
			n.vals.put(Viperion.V_CP_LEFT, (long) cp.left);
			n.vals.put(Viperion.V_CHECKPOINT_AT, now);
			n.vecs.put(Viperion.X_CP, List.of(c.x, c.y, c.z));
		});
		Miraculous.setAnim(p, Viperion.ANIM_CHECKPOINT);
		Miraculous.setCooldown(p, ViperionConfig.SECOND_CHANCE, ViperionConfig.SECOND_CHANCE_COOLDOWN);
		Miraculous.startCountdown(p);
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

	/** Shift+Z again (or, {@code auto}, a killing blow): everything back to the checkpoint. False if there was nothing to rewind to. */
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
		Vec3 from = p.position();
		// 1. the blocks (first: the creatures are put back on the old ground)
		restoreBlocks(level, cp);
		// 2. the creatures
		double r = ViperionConfig.SECOND_CHANCE_RADIUS;
		Set<UUID> placed = new HashSet<>();
		for (MobSnap m : cp.mobs) {
			Entity e = level.getEntity(m.id());
			if (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved()) {
				restore(le, m);
				placed.add(le.getUUID());
				continue;
			}
			if (m.boss() || m.tag() == null) {
				continue;
			}
			if (e != null) {
				e.discard(); // still in its death fall
			}
			Entity back = EntityType.create(m.tag().copy(), level).orElse(null);
			if (!(back instanceof LivingEntity bl)) {
				continue;
			}
			if (level.getEntity(bl.getUUID()) != null) {
				bl.setUUID(UUID.randomUUID());
			}
			bl.moveTo(m.pos().x, m.pos().y, m.pos().z, m.yRot(), m.xRot());
			bl.deathTime = 0;
			bl.setHealth(Math.max(1f, m.health()));
			if (level.addFreshEntity(bl)) {
				placed.add(bl.getUUID());
				level.sendParticles(ViperionMoves.GREEN, bl.getX(), bl.getY() + bl.getBbHeight() * 0.5, bl.getZ(), 16, 0.3, 0.5, 0.3, 0.0);
			}
		}
		// what appeared since: new hostile mobs, new drops and orbs (never items a player threw)
		AABB box = new AABB(cp.center, cp.center).inflate(r);
		for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> !cp.known.contains(e.getUUID()) && !placed.contains(e.getUUID()))) {
			boolean gone = false;
			if (e instanceof Mob m && m instanceof Enemy && !ViperionMoves.isBoss(m) && !(m instanceof OwnableEntity o && o.getOwnerUUID() != null)) {
				gone = true;
			} else if (e instanceof ItemEntity item && item.getOwner() == null) {
				gone = true;
			} else if (e instanceof ExperienceOrb) {
				gone = true;
			}
			if (gone) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + 0.5, e.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
				e.discard();
			}
		}
		// 3. the squad, then him
		for (PlayerSnap s : cp.mates) {
			if (level.getEntity(s.id()) instanceof ServerPlayer o && o.isAlive() && !o.isSpectator() && Squads.areAllies(p, o)) {
				restorePlayer(level, o, s, false);
			}
		}
		restorePlayer(level, p, cp.self, true);
		cp.left--;
		cp.lastRewind = now;
		int left = cp.left;
		Miraculous.edit(p, n -> {
			n.vals.put(Viperion.V_REWIND_AT, now);
			if (left > 0) {
				n.vals.put(Viperion.V_CP_LEFT, (long) left);
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

	private static void restore(LivingEntity e, MobSnap m) {
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
		e.removeAllEffects();
		for (MobEffectInstance i : m.effects()) {
			e.addEffect(new MobEffectInstance(i));
		}
		if (e instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		ServerLevel level = (ServerLevel) e.level();
		level.sendParticles(ViperionMoves.GREEN, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
	}

	private static void restorePlayer(ServerLevel level, ServerPlayer o, PlayerSnap s, boolean full) {
		if (o.isPassenger()) {
			o.stopRiding();
		}
		o.teleportTo(level, s.pos().x, s.pos().y, s.pos().z, s.yRot(), s.xRot());
		o.setDeltaMovement(Vec3.ZERO);
		o.hurtMarked = true;
		o.resetFallDistance();
		o.setHealth(Math.min(o.getMaxHealth(), Math.max(1f, s.health())));
		if (full) {
			o.setAbsorptionAmount(s.absorption());
			o.getFoodData().setFoodLevel(s.food());
			o.getFoodData().setSaturation(s.saturation());
			o.removeAllEffects();
			for (MobEffectInstance i : s.effects()) {
				o.addEffect(new MobEffectInstance(i));
			}
			o.setRemainingFireTicks(s.fire());
			o.setAirSupply(s.air());
		}
	}

	private static void restoreBlocks(ServerLevel level, Checkpoint cp) {
		List<long[]> keys = new ArrayList<>();
		synchronized (cp.blocks) {
			for (var en : cp.blocks.long2ObjectEntrySet()) {
				keys.add(new long[] { en.getLongKey() });
			}
		}
		restoring++;
		try {
			for (long[] k : keys) {
				BlockPos bp = BlockPos.of(k[0]);
				BlockState old = cp.blocks.get(k[0]);
				if (old == null || !level.isLoaded(bp) || level.getBlockState(bp) == old) {
					continue;
				}
				level.setBlock(bp, old, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (level.random.nextInt(4) == 0) {
					level.sendParticles(ViperionMoves.TEAL, bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.0);
				}
			}
		} finally {
			restoring--;
		}
		synchronized (cp.blocks) {
			cp.blocks.clear();
		}
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

	// ---------------------------------------------------------------- ticks and the block hook

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

	static void serverTick(MinecraftServer server) {
		if (CHECKPOINTS.isEmpty()) {
			return;
		}
		CHECKPOINTS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/** From {@code CityLevelChunkMixin}: a block changed in a loaded chunk. Must stay cheap. */
	public static void onBlockChanged(Level level, BlockPos pos, BlockState old, BlockState neu) {
		if (CHECKPOINTS.isEmpty() || restoring > 0 || level.isClientSide() || old == neu) {
			return;
		}
		ResourceKey<Level> dim = level.dimension();
		long now = level.getGameTime();
		double r2 = ViperionConfig.SECOND_CHANCE_RADIUS * ViperionConfig.SECOND_CHANCE_RADIUS;
		for (Checkpoint cp : CHECKPOINTS.values()) {
			if (cp.dim != dim || now >= cp.until || cp.center.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > r2) {
				continue;
			}
			synchronized (cp.blocks) {
				long key = pos.asLong();
				if (!cp.blocks.containsKey(key) && cp.blocks.size() < ViperionConfig.SECOND_CHANCE_MAX_BLOCKS) {
					cp.blocks.put(key, old);
				}
			}
		}
	}
}
