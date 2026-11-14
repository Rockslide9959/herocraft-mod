package com.projecthero.mod.saiyan.kit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.saiyan.Saiyan;
import com.projecthero.mod.saiyan.SaiyanSounds;
import com.projecthero.mod.saiyan.kit.network.SpiritBombFxPayload;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * v0.17.3 Saiyan revamp: <b>Shift+Z, the Spirit Bomb</b>. User's spec: he rises 10 blocks, lifts his arms over his head and
 * a bomb charges above them, 10 seconds to its full 20-block radius; 1000 damage and a massive crater where it lands (a real
 * crater, only with the {@code mobGriefing} gamerule on).
 * <ol>
 *   <li><b>Rise</b> ({@link #RISE_TICKS}): a smooth climb of {@link #RISE_HEIGHT} (less under a ceiling), then a hover,
 *   rooted ({@link #busy}), both arms straight up.</li>
 *   <li><b>Charge</b> (key held): the bomb grows from {@link #RADIUS_MIN} to {@link #RADIUS_MAX} over {@link #CHARGE_TICKS},
 *   drawing {@link #KI_TOTAL} Ki a little each tick; out of Ki it stops growing. Full, it waits {@link #FULL_HOLD_TICKS}
 *   then goes on its own.</li>
 *   <li><b>Throw</b> (key up): toward the crosshair at {@link #SPEED} blocks a tick, up to {@link #RANGE} blocks.</li>
 *   <li><b>Impact</b> (a block, a creature, or the end of its range): {@link #damageAt} to everything inside the radius,
 *   never the caster or his squad; a bowl crater in batches (protected / unbreakable / block-entity blocks are kept).</li>
 *   <li>Cooldown {@link #COOLDOWN}. Hit hard ({@link #HIT_HARD} in a tick), dead, or the power gone while charging: it
 *   fizzles, the Ki already drawn is lost, half the cooldown.</li>
 * </ol>
 * Server state lives here (UUID keys, cleared by {@link #clearSessionState}); what clients need is the synced
 * {@link SpiritBombState} on the caster plus {@link SpiritBombFxPayload}s for the thrown bomb.
 */
public final class SaiyanSpiritBomb {
	public static final int NONE = 0, RISE = 1, CHARGE = 2, THROW = 3;

	public static final int RISE_TICKS = 30;
	public static final double RISE_HEIGHT = 10.0;
	public static final int CHARGE_TICKS = 200;
	public static final float RADIUS_MIN = 0.4f, RADIUS_MAX = 20f;
	public static final float RADIUS_PER_TICK = (RADIUS_MAX - RADIUS_MIN) / CHARGE_TICKS;
	public static final float KI_TOTAL = 100f, KI_PER_TICK = KI_TOTAL / CHARGE_TICKS;
	/** Ki needed to start at all. */
	public static final float KI_TO_START = 10f;
	public static final float DAMAGE_MAX = 1000f;
	/** Share of the centre damage left at the very edge of the radius. */
	public static final float EDGE_DAMAGE = 0.35f;
	/** Let go below this charge and nothing is thrown (a short cooldown, no flight). */
	public static final float MIN_CHARGE = 0.05f;
	public static final int FULL_HOLD_TICKS = 200;
	public static final int THROW_TICKS = 10;
	public static final double SPEED = 1.0, RANGE = 80.0;
	public static final int COOLDOWN = 6000, FIZZLE_COOLDOWN = COOLDOWN / 2, CANCEL_COOLDOWN = 40;
	/** Health lost in one tick that breaks the charge. */
	public static final float HIT_HARD = 6f;
	/** The bowl's depth as a share of the radius. */
	public static final double CRATER_DEPTH = 0.55;
	public static final int CRATER_BATCH = 2500;
	/** His raised hands, above his feet: the held bomb sits on them. */
	public static final double HAND_TOP = 2.3;

	private static final class Bomb {
		final UUID owner;
		final int entityId;
		final ServerLevel level;
		final double x, z, startY;
		final double targetY;
		final boolean hadMayfly, wasFlying;
		int phase = RISE;
		long phaseStart;
		long lastTick = -1L;
		int chargeTicks;
		int fullFor;
		boolean released, stalled;
		float lastHealth;

		Bomb(ServerPlayer p, double targetY) {
			owner = p.getUUID();
			entityId = p.getId();
			level = p.serverLevel();
			x = p.getX();
			z = p.getZ();
			startY = p.getY();
			this.targetY = targetY;
			hadMayfly = p.getAbilities().mayfly;
			wasFlying = p.getAbilities().flying;
			lastHealth = p.getHealth();
		}

		float charge() {
			return Math.min(1f, chargeTicks / (float) CHARGE_TICKS);
		}
	}

	private static final class Flight {
		final int id;
		final UUID owner;
		final ServerLevel level;
		Vec3 pos;
		final Vec3 vel;
		final float radius, charge;
		double travelled;
		int age;

		Flight(int id, UUID owner, ServerLevel level, Vec3 pos, Vec3 vel, float radius, float charge) {
			this.id = id;
			this.owner = owner;
			this.level = level;
			this.pos = pos;
			this.vel = vel;
			this.radius = radius;
			this.charge = charge;
		}
	}

	private static final class Crater {
		final ServerLevel level;
		final UUID owner;
		final BlockPos centre;
		final double radius;
		final long[] order;
		int next;

		Crater(ServerLevel level, UUID owner, BlockPos centre, double radius, long[] order) {
			this.level = level;
			this.owner = owner;
			this.centre = centre;
			this.radius = radius;
			this.order = order;
		}
	}

	private static final Map<UUID, Bomb> ACTIVE = new ConcurrentHashMap<>();
	private static final List<Flight> FLIGHTS = new ArrayList<>();
	private static final List<Crater> CRATERS = new ArrayList<>();
	private static int nextId = 1;

	private SaiyanSpiritBomb() {
	}

	/** Called once from {@code ProjectHeroMod.onInitialize}: the attachment, the fx payload, the world tick, the lifecycle. */
	public static void initialize() {
		SpiritBombState.initialize();
		PayloadTypeRegistry.playS2C().register(SpiritBombFxPayload.TYPE, SpiritBombFxPayload.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(SaiyanSpiritBomb::serverTick);
		ServerPlayerEvents.JOIN.register(SaiyanSpiritBomb::onJoin);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Bomb b = ACTIVE.get(handler.getPlayer().getUUID());
			if (b != null) {
				end(handler.getPlayer(), b, false);
			}
		});
	}

	public static void clearSessionState() {
		ACTIVE.clear();
		synchronized (FLIGHTS) {
			FLIGHTS.clear();
		}
		synchronized (CRATERS) {
			CRATERS.clear();
		}
		nextId = 1;
	}

	// ---------------------------------------------------------------- the numbers (shared with the client and the tests)

	/** The bomb's radius at a charge of 0..1. */
	public static float radiusFor(float charge) {
		return RADIUS_MIN + (RADIUS_MAX - RADIUS_MIN) * Math.max(0f, Math.min(1f, charge));
	}

	/** The damage at {@code distFrac} (0 = the centre, 1 = the edge) of a bomb thrown at {@code charge}; 0 outside. */
	public static float damageAt(float charge, double distFrac) {
		if (distFrac > 1.0) {
			return 0f;
		}
		double d = Math.max(0.0, distFrac);
		return (float) (DAMAGE_MAX * Math.max(0f, Math.min(1f, charge)) * (1.0 - (1.0 - EDGE_DAMAGE) * d * d));
	}

	/** How far up he is {@code ticks} into the rise, as a share of the climb (smoothstep, 1 at {@link #RISE_TICKS}). */
	public static double riseShare(int ticks) {
		double k = Math.max(0.0, Math.min(1.0, ticks / (double) RISE_TICKS));
		return k * k * (3.0 - 2.0 * k);
	}

	/** The centre of a held bomb of {@code radius} over a player standing at {@code feet}. */
	public static Vec3 holdPos(Vec3 feet, float radius) {
		return feet.add(0.0, HAND_TOP + radius, 0.0);
	}

	// ---------------------------------------------------------------- the contract

	public static SpiritBombState state(Player p) {
		return p.getAttachedOrElse(SpiritBombState.TYPE, SpiritBombState.NONE);
	}

	private static void save(ServerPlayer p, SpiritBombState s) {
		p.setAttached(SpiritBombState.TYPE, s);
	}

	/** The phase (client-safe). */
	public static int phase(Player p) {
		return state(p).phase();
	}

	public static int cooldownRemaining(Player p) {
		return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, state(p).readyAt() - p.level().getGameTime()));
	}

	public static int maxCooldown() {
		return COOLDOWN;
	}

	/** Rising, charging or throwing: he is rooted in the air and should not use anything else (client-safe). */
	public static boolean busy(Player p) {
		return state(p).phase() != NONE;
	}

	/** The server's charge 0..1 (0 when not charging). */
	public static float charge(Player p) {
		Bomb b = ACTIVE.get(p.getUUID());
		return b == null ? 0f : b.charge();
	}

	/** The held bomb's radius right now (0 when none is held). */
	public static float heldRadius(Player p) {
		Bomb b = ACTIVE.get(p.getUUID());
		return b == null || b.phase != CHARGE ? 0f : radiusFor(b.charge());
	}

	/** The hover height he rises to (NaN when not in the move). */
	public static double hoverY(Player p) {
		Bomb b = ACTIVE.get(p.getUUID());
		return b == null ? Double.NaN : b.targetY;
	}

	public static void press(ServerPlayer p, boolean down) {
		Bomb b = ACTIVE.get(p.getUUID());
		if (!down) {
			if (b != null) {
				b.released = true;
				if (b.phase == CHARGE) {
					release(p, b);
				}
			}
			return;
		}
		if (b != null || !Saiyan.hasPower(p) || !p.isAlive() || p.isSpectator() || p.isPassenger()) {
			return;
		}
		int cd = cooldownRemaining(p);
		if (cd > 0) {
			say(p, "message.projecthero.saiyan.spirit_bomb.cooldown", (cd + 19) / 20);
			return;
		}
		if (!p.getAbilities().instabuild && Saiyan.ki(p) < KI_TO_START) {
			say(p, "message.projecthero.saiyan.spirit_bomb.no_ki");
			return;
		}
		start(p);
	}

	public static void tick(ServerPlayer p) {
		Bomb b = ACTIVE.get(p.getUUID());
		if (b == null) {
			return;
		}
		long now = p.level().getGameTime();
		if (b.lastTick == now) {
			return; // ticked already this tick (the kit and the world tick both call in)
		}
		b.lastTick = now;
		if (p.getId() != b.entityId || p.level() != b.level || !p.isAlive() || p.isSpectator()) {
			end(p, b, false); // died, respawned or changed world: gone, no fx
			if (p.isAlive()) {
				startCooldown(p, FIZZLE_COOLDOWN);
			}
			return;
		}
		if (!Saiyan.hasPower(p)) {
			fizzle(p, b);
			return;
		}
		float hp = p.getHealth();
		if (b.phase != THROW && b.lastHealth - hp >= HIT_HARD) {
			fizzle(p, b);
			return;
		}
		b.lastHealth = hp;
		int age = (int) (now - b.phaseStart);
		switch (b.phase) {
			case RISE -> {
				hold(p, b, b.startY + (b.targetY - b.startY) * riseShare(age + 1), 1.5);
				if (age + 1 >= RISE_TICKS) {
					if (b.released) {
						cancel(p, b); // let go before anything gathered
					} else {
						b.phase = CHARGE;
						b.phaseStart = now;
						sync(p, b, now);
					}
				}
			}
			case CHARGE -> {
				hold(p, b, b.targetY, 0.6);
				if (b.chargeTicks < CHARGE_TICKS) {
					if (drawKi(p)) {
						b.chargeTicks++;
						if (b.stalled) {
							b.stalled = false;
							sync(p, b, now);
						}
					} else if (!b.stalled) {
						b.stalled = true;
						sync(p, b, now);
						say(p, "message.projecthero.saiyan.spirit_bomb.stalled");
					}
					if (b.chargeTicks >= CHARGE_TICKS) {
						sync(p, b, now); // full: stop the client's growth exactly here
					} else if (age % 20 == 0) {
						sync(p, b, now);
					}
				} else if (++b.fullFor >= FULL_HOLD_TICKS) {
					release(p, b);
				}
			}
			case THROW -> {
				hold(p, b, b.targetY, 0.6);
				if (age >= THROW_TICKS) {
					end(p, b, true);
				}
			}
			default -> end(p, b, true);
		}
	}

	// ---------------------------------------------------------------- the move

	private static void start(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		// rise less under a ceiling: stop with 2 blocks of headroom clear
		BlockPos base = p.blockPosition();
		int clear = 0;
		for (int dy = 1; dy <= (int) RISE_HEIGHT + 2; dy++) {
			BlockPos at = base.above(dy);
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				break;
			}
			clear = dy;
		}
		double rise = Math.max(0.0, Math.min(RISE_HEIGHT, clear - 2.0));
		Bomb b = new Bomb(p, p.getY() + rise);
		b.phaseStart = now;
		b.lastTick = -1L;
		ACTIVE.put(p.getUUID(), b);
		if (!p.isSpectator()) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		save(p, new SpiritBombState(RISE, now, 0f, 0f, now, state(p).readyAt()));
		level.playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), SaiyanSounds.SPIRIT_BOMB_RISE, SoundSource.PLAYERS, 0.6f, 1.0f);
	}

	/** Keeps him at ({@code x}, {@code y}, {@code z}): a velocity nudge for a real client, a snap past {@code snap} blocks. */
	private static void hold(ServerPlayer p, Bomb b, double y, double snap) {
		Vec3 want = new Vec3(b.x, y, b.z);
		Vec3 err = want.subtract(p.position());
		if (err.lengthSqr() > snap * snap) {
			p.teleportTo(want.x, want.y, want.z);
			p.setDeltaMovement(Vec3.ZERO);
		} else {
			p.setDeltaMovement(err.x * 0.5, err.y * 0.85, err.z * 0.5);
		}
		p.hurtMarked = true;
		p.resetFallDistance();
	}

	private static boolean drawKi(ServerPlayer p) {
		if (p.getAbilities().instabuild) {
			return true;
		}
		if (Saiyan.ki(p) + 1.0e-4f < KI_PER_TICK) {
			return false;
		}
		return Saiyan.spendKi(p, KI_PER_TICK);
	}

	/** Pushes the held bomb's size and growth to every client. */
	private static void sync(ServerPlayer p, Bomb b, long now) {
		boolean growing = b.phase == CHARGE && !b.stalled && b.chargeTicks < CHARGE_TICKS;
		save(p, new SpiritBombState(b.phase, b.phaseStart, b.phase == CHARGE ? radiusFor(b.charge()) : 0f, growing ? RADIUS_PER_TICK : 0f, now,
				state(p).readyAt()));
	}

	private static void release(ServerPlayer p, Bomb b) {
		float c = b.charge();
		if (c < MIN_CHARGE) {
			cancel(p, b);
			return;
		}
		ServerLevel level = b.level;
		long now = level.getGameTime();
		float radius = radiusFor(c);
		Vec3 from = holdPos(p.position(), radius);
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(160.0));
		BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 target = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
		Vec3 dir = target.subtract(from);
		dir = dir.lengthSqr() < 1.0e-6 ? p.getLookAngle() : dir.normalize();
		Flight f = new Flight(nextId++, p.getUUID(), level, from, dir.scale(SPEED), radius, c);
		synchronized (FLIGHTS) {
			FLIGHTS.add(f);
		}
		broadcast(level, from, new SpiritBombFxPayload(SpiritBombFxPayload.FLY, f.id, f.pos, f.vel, radius, c));
		b.phase = THROW;
		b.phaseStart = now;
		save(p, new SpiritBombState(THROW, now, 0f, 0f, now, now + COOLDOWN));
		level.playSound(null, from.x, from.y, from.z, SaiyanSounds.SPIRIT_BOMB_THROW, SoundSource.PLAYERS, 0.9f, 1.0f);
	}

	/** Let go before enough gathered: nothing thrown, a short cooldown. */
	private static void cancel(ServerPlayer p, Bomb b) {
		float r = b.phase == CHARGE ? radiusFor(b.charge()) : 0f;
		if (r > 0f) {
			Vec3 at = holdPos(p.position(), r);
			broadcast(b.level, at, new SpiritBombFxPayload(SpiritBombFxPayload.FIZZLE, p.getId(), at, Vec3.ZERO, r, b.charge()));
		}
		say(p, "message.projecthero.saiyan.spirit_bomb.too_small");
		end(p, b, true);
		startCooldown(p, CANCEL_COOLDOWN);
	}

	/** Broken off (hit hard, the power gone): the Ki already drawn is lost, half the cooldown. */
	private static void fizzle(ServerPlayer p, Bomb b) {
		float r = b.phase == CHARGE ? radiusFor(b.charge()) : 0f;
		Vec3 at = holdPos(p.position(), Math.max(r, 0.5f));
		broadcast(b.level, at, new SpiritBombFxPayload(SpiritBombFxPayload.FIZZLE, p.getId(), at, Vec3.ZERO, Math.max(r, 0.5f), b.charge()));
		b.level.playSound(null, at.x, at.y, at.z, SaiyanSounds.POWER_DOWN, SoundSource.PLAYERS, 0.45f, 0.7f);
		p.displayClientMessage(Component.translatable("message.projecthero.saiyan.spirit_bomb.fizzle").withStyle(ChatFormatting.GRAY), true);
		end(p, b, true);
		startCooldown(p, FIZZLE_COOLDOWN);
	}

	private static void startCooldown(ServerPlayer p, int ticks) {
		SpiritBombState s = state(p);
		long now = p.level().getGameTime();
		save(p, new SpiritBombState(s.phase(), s.since(), s.radius(), s.rate(), s.syncAt(), now + ticks));
	}

	/** Out of the move: his flight back as it was, and (in the air) a gentle float down. */
	private static void end(ServerPlayer p, Bomb b, boolean floatDown) {
		ACTIVE.remove(b.owner);
		if (p.getId() != b.entityId) {
			SpiritBombState s = state(p);
			save(p, new SpiritBombState(NONE, 0L, 0f, 0f, 0L, s.readyAt()));
			return;
		}
		if (!p.isSpectator() && !p.getAbilities().instabuild && !Saiyan.isFlying(p)) {
			if (!b.hadMayfly) {
				p.getAbilities().mayfly = false;
				p.getAbilities().flying = false;
			} else if (!b.wasFlying) {
				p.getAbilities().flying = false;
			}
			p.onUpdateAbilities();
		} else if (p.getAbilities().instabuild && !b.wasFlying) {
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		p.resetFallDistance();
		if (floatDown && p.isAlive() && !p.onGround() && !Saiyan.isFlying(p)) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, false));
		}
		SpiritBombState s = state(p);
		save(p, new SpiritBombState(NONE, p.level().getGameTime(), 0f, 0f, 0L, s.readyAt()));
	}

	private static void onJoin(ServerPlayer p) {
		SpiritBombState s = state(p);
		if (s.phase() != NONE && !ACTIVE.containsKey(p.getUUID())) {
			// left mid-move: he must not keep the hover's flight
			if (!p.getAbilities().instabuild && !p.isSpectator() && !Saiyan.isFlying(p)) {
				p.getAbilities().mayfly = false;
				p.getAbilities().flying = false;
				p.onUpdateAbilities();
			}
			save(p, new SpiritBombState(NONE, 0L, 0f, 0f, 0L, s.readyAt()));
		}
	}

	private static void say(ServerPlayer p, String key, Object... args) {
		p.displayClientMessage(Component.translatable(key, args).withStyle(ChatFormatting.GRAY), true);
	}

	// ---------------------------------------------------------------- the world tick: casters, flights, craters

	private static void serverTick(MinecraftServer server) {
		if (!ACTIVE.isEmpty()) {
			for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
				ServerPlayer p = server.getPlayerList().getPlayer(id);
				if (p == null) {
					ACTIVE.remove(id);
				} else {
					tick(p);
				}
			}
		}
		tickFlights(server);
		tickCraters(server);
	}

	private static void tickFlights(MinecraftServer server) {
		List<Flight> copy;
		synchronized (FLIGHTS) {
			if (FLIGHTS.isEmpty()) {
				return;
			}
			copy = new ArrayList<>(FLIGHTS);
		}
		for (Flight f : copy) {
			Vec3 boom = step(server, f);
			if (boom != null) {
				synchronized (FLIGHTS) {
					FLIGHTS.remove(f);
				}
				detonate(server, f.level, f.owner, boom, f.radius, f.charge);
			}
		}
	}

	/** Moves a thrown bomb one tick; where it goes off, or null while it flies on. */
	private static Vec3 step(MinecraftServer server, Flight f) {
		f.age++;
		Vec3 dir = f.vel.normalize();
		Vec3 lead = f.pos.add(dir.scale(f.radius * 0.5)); // half sunk in before it goes off, as in the show
		Vec3 next = lead.add(f.vel);
		if (!f.level.isLoaded(BlockPos.containing(next)) || next.y < f.level.getMinBuildHeight() - 16) {
			return lead;
		}
		BlockHitResult hit = f.level.clip(new ClipContext(lead, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.MISS) {
			return hit.getLocation();
		}
		ServerPlayer caster = server.getPlayerList().getPlayer(f.owner);
		double reach = f.radius * 0.5 + 1.0;
		AABB box = new AABB(f.pos, f.pos).inflate(reach + 2.0);
		for (LivingEntity e : f.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (e.getUUID().equals(f.owner) || (caster != null && spared(caster, e)) || e.isSpectator()) {
				continue;
			}
			if (e.getBoundingBox().getCenter().distanceTo(f.pos) <= reach + e.getBbWidth() * 0.5) {
				return lead;
			}
		}
		f.pos = f.pos.add(f.vel);
		f.travelled += f.vel.length();
		if (f.travelled >= RANGE) {
			return f.pos;
		}
		if (f.age % 20 == 0) {
			broadcast(f.level, f.pos, new SpiritBombFxPayload(SpiritBombFxPayload.FLY, f.id, f.pos, f.vel, f.radius, f.charge));
		}
		return null;
	}

	/** Never hurts the caster, his squad, the players his squad rules shield, or his own pets. */
	private static boolean spared(ServerPlayer caster, LivingEntity e) {
		if (e == caster || Squads.areAllies(caster, e)) {
			return true;
		}
		if (e instanceof Player victim && Squads.shields(caster, victim)) {
			return true;
		}
		return e instanceof OwnableEntity o && caster.getUUID().equals(o.getOwnerUUID());
	}

	/** The test hook: the impact of a bomb of {@code radius} thrown at {@code charge}, at {@code centre}. */
	public static void detonateForTest(ServerPlayer caster, Vec3 centre, float radius, float charge) {
		detonate(caster.server, caster.serverLevel(), caster.getUUID(), centre, radius, charge);
	}

	/** The test hook: jumps a charging bomb to {@code charge}. */
	public static void forceChargeForTest(ServerPlayer p, float charge) {
		Bomb b = ACTIVE.get(p.getUUID());
		if (b != null && b.phase == CHARGE) {
			b.chargeTicks = Math.round(Math.max(0f, Math.min(1f, charge)) * CHARGE_TICKS);
			sync(p, b, p.level().getGameTime());
		}
	}

	/** Thrown bombs still in the air (tests). */
	public static int flightsInAir() {
		synchronized (FLIGHTS) {
			return FLIGHTS.size();
		}
	}

	private static void detonate(MinecraftServer server, ServerLevel level, UUID owner, Vec3 centre, float radius, float charge) {
		ServerPlayer caster = server.getPlayerList().getPlayer(owner);
		if (caster != null && caster.level() != level) {
			caster = null;
		}
		// damage, falling off toward the edge, and a shove outward
		AABB box = new AABB(centre, centre).inflate(radius + 2.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			if (e.getUUID().equals(owner) || e.isSpectator() || (caster != null && spared(caster, e))) {
				continue;
			}
			double d = Math.max(0.0, e.getBoundingBox().getCenter().distanceTo(centre) - e.getBbWidth() * 0.5);
			if (d > radius) {
				continue;
			}
			double frac = d / Math.max(0.01, radius);
			float dmg = damageAt(charge, frac);
			if (dmg <= 0f) {
				continue;
			}
			// pure energy, not a swing: no melee bonus (the core scales it by his form), through armour
			DamageSource src = caster != null ? level.damageSources().indirectMagic(caster, caster) : level.damageSources().magic();
			e.invulnerableTime = 0;
			e.hurt(src, dmg);
			Vec3 away = e.position().subtract(centre);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(0, 1, 0) : away.normalize();
			double push = 1.6 * (1.0 - frac);
			e.push(away.x * push, 0.35 + 0.5 * (1.0 - frac), away.z * push);
			e.hurtMarked = true;
		}
		// the look and the sound are the clients'; the shake is the shared tremor packet
		broadcast(level, centre, new SpiritBombFxPayload(SpiritBombFxPayload.IMPACT, 0, centre, Vec3.ZERO, radius, charge));
		double shakeR = radius * 6.0 + 48.0;
		for (ServerPlayer viewer : level.players()) {
			double d2 = viewer.distanceToSqr(centre);
			if (d2 <= shakeR * shakeR) {
				float falloff = (float) (1.0 - Math.sqrt(d2) / shakeR * 0.85);
				float strength = (float) Math.min(1.2, 0.25 + radius * 0.05);
				ServerPlayNetworking.send(viewer, new TitanShakePayload(strength * falloff, 20 + (int) (radius * 1.5)));
			}
		}
		// the crater: the user's rule -- a real one, only with mobGriefing on
		if (level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
			Crater c = crater(level, owner, centre, radius);
			if (c != null) {
				carve(server, c);
				if (c.next < c.order.length) {
					synchronized (CRATERS) {
						CRATERS.add(c);
					}
				} else {
					scorch(server, c);
				}
			}
		}
	}

	// ---------------------------------------------------------------- the crater

	/** The bowl: a radius-wide, {@link #CRATER_DEPTH}-deep half-ellipsoid below the impact, the whole sphere cleared above it. */
	private static Crater crater(ServerLevel level, UUID owner, Vec3 at, double radius) {
		if (radius < 0.75) {
			return null;
		}
		BlockPos c = BlockPos.containing(at);
		int r = (int) Math.ceil(radius);
		double depth = Math.max(1.0, radius * CRATER_DEPTH);
		int d = (int) Math.ceil(depth);
		int span = 2 * r + 1;
		int max = span * span * (r + d + 1);
		long[] keys = new long[max];
		long[] packed = new long[max];
		int n = 0;
		for (int y = -d; y <= r; y++) {
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					double h2 = x * x + z * z;
					// a slightly ragged rim
					double rough = 1.0 + 0.07 * (hash(c.getX() + x, c.getZ() + z) - 0.5);
					double rr = radius * rough;
					boolean in = y < 0 ? h2 / (rr * rr) + (y * y) / (depth * depth) <= 1.0 : h2 + y * y <= rr * rr;
					if (!in) {
						continue;
					}
					int y0 = c.getY() + y;
					if (y0 < level.getMinBuildHeight() || y0 >= level.getMaxBuildHeight()) {
						continue;
					}
					packed[n] = BlockPos.asLong(c.getX() + x, y0, c.getZ() + z);
					keys[n] = ((long) (x * x + y * y + z * z) << 24) | n;
					n++;
				}
			}
		}
		keys = Arrays.copyOf(keys, n);
		Arrays.sort(keys);
		long[] order = new long[n];
		for (int i = 0; i < n; i++) {
			order[i] = packed[(int) (keys[i] & 0xFFFFFF)];
		}
		return new Crater(level, owner, c, radius, order);
	}

	private static double hash(int x, int z) {
		long h = x * 341873128712L + z * 132897987541L;
		h = (h ^ (h >>> 13)) * 0x5bd1e995L;
		return ((h ^ (h >>> 15)) & 0xFFFF) / 65535.0;
	}

	private static boolean mayBreak(ServerLevel level, ServerPlayer caster, BlockPos pos, BlockState st) {
		if (st.isAir() || !st.getFluidState().isEmpty()) {
			return false;
		}
		if (st.getDestroySpeed(level, pos) < 0f || level.getBlockEntity(pos) != null) {
			return false; // bedrock, barriers, portals; chests and the like stay
		}
		return caster == null || level.mayInteract(caster, pos);
	}

	private static void carve(MinecraftServer server, Crater c) {
		ServerPlayer caster = server.getPlayerList().getPlayer(c.owner);
		if (caster != null && caster.level() != c.level) {
			caster = null;
		}
		int end = Math.min(c.order.length, c.next + CRATER_BATCH);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int i = c.next; i < end; i++) {
			pos.set(c.order[i]);
			if (!c.level.isLoaded(pos)) {
				continue;
			}
			BlockState st = c.level.getBlockState(pos);
			if (mayBreak(c.level, caster, pos, st)) {
				c.level.setBlock(pos, air, Block.UPDATE_CLIENTS); // no neighbour updates: no cascades, no drops
			}
		}
		c.next = end;
	}

	/** The bowl's floor scorched: patches of coarse dirt on soil, blackstone on stone. */
	private static void scorch(MinecraftServer server, Crater c) {
		ServerPlayer caster = server.getPlayerList().getPlayer(c.owner);
		if (caster != null && caster.level() != c.level) {
			caster = null;
		}
		double depth = Math.max(1.0, c.radius * CRATER_DEPTH);
		int r = (int) Math.ceil(c.radius);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) {
			for (int z = -r; z <= r; z++) {
				double h2 = (x * x + z * z) / (c.radius * c.radius);
				if (h2 > 1.0) {
					continue;
				}
				int y = (int) Math.floor(-depth * Math.sqrt(1.0 - h2)) - 1;
				pos.set(c.centre.getX() + x, c.centre.getY() + y, c.centre.getZ() + z);
				// walk down to the first solid block under the bowl
				for (int k = 0; k < 3 && c.level.getBlockState(pos).isAir(); k++) {
					pos.move(0, -1, 0);
				}
				if (!c.level.isLoaded(pos)) {
					continue;
				}
				BlockState st = c.level.getBlockState(pos);
				double roll = hash(pos.getX() * 7 + 3, pos.getZ() * 13 + 1);
				if (!mayBreak(c.level, caster, pos, st)) {
					continue;
				}
				if (st.is(BlockTags.DIRT) && roll < 0.6) {
					c.level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
				} else if (st.is(BlockTags.BASE_STONE_OVERWORLD) && roll < 0.3) {
					c.level.setBlock(pos, Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static void tickCraters(MinecraftServer server) {
		synchronized (CRATERS) {
			if (CRATERS.isEmpty()) {
				return;
			}
			Iterator<Crater> it = CRATERS.iterator();
			// one crater at a time keeps a single tick's work bounded
			Crater c = it.next();
			carve(server, c);
			if (c.next >= c.order.length) {
				it.remove();
				scorch(server, c);
			}
		}
	}

	/** Craters still being carved (tests). */
	public static int cratersPending() {
		synchronized (CRATERS) {
			return CRATERS.size();
		}
	}

	private static void broadcast(ServerLevel level, Vec3 at, SpiritBombFxPayload payload) {
		for (ServerPlayer viewer : PlayerLookup.around(level, at, 320.0)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}
}
