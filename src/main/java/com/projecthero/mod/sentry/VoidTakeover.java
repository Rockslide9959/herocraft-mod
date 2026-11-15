package com.projecthero.mod.sentry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: <b>the Void takes over</b> at 100 Void. The body is not the player's any more:
 * <ul>
 *   <li>The Void flies it on its own and attacks <b>everything living</b> around it -- mobs, players, squadmates and pets (user
 *   decision) -- with the Void Form kit at full strength ({@link VoidMoves}) and its own fists. It hunts the nearest living thing within
 *   24; with nothing near it drifts toward players (128) or a village's meeting point, else wanders, so it never just sits. Kills are
 *   absorbed: each heals it 4.</li>
 *   <li>It eats the world ({@link VoidMatter}); the sky goes dark and the clock shows midnight near it (client only).</li>
 *   <li>Getting control back: nothing for the first 30 s; then the Shame Rooms mind screen ({@link ShameRooms}) -- three rooms, a failed
 *   one sends you back a room and adds 20 s. Other players can reach you: holding right click on the body with an empty hand drains the
 *   Void's grip ({@link #help}); at 0 the current room breaks open on its own. Daylight weakens the grip, night strengthens it.</li>
 *   <li>Winning all three rooms throws the Void out: a golden flash, the kneel, Void 40, Shift+V locked for 5 minutes. Death ends it
 *   (Void 60 on respawn); so does logging out (and a server stop: the next join ends it), each time the eaten world comes back.</li>
 * </ul>
 * The player's keys are dead meanwhile ({@code SentryAbilityManager}), his own clicks are refused ({@code SentryMod}), and his client
 * pulls the camera out behind the body. Session data (the brain) is keyed by player id only and cleared on server stop.
 */
public final class VoidTakeover {
	/** Why a takeover ended. */
	public enum End {
		WON, DIED, LOGGED_OUT, REVOKED, COMMAND
	}

	/** What the Void remembers between ticks. */
	static final class Brain {
		final Map<String, Long> ready = new HashMap<>();
		long nextLook;
		long nextSwing;
		long nextWander;
		Vec3 wanderTo;
		UUID helper;
		long helpedAt;
		/** The last move it used (tests). */
		String lastMove;
		int hits;
	}

	private static final Map<UUID, Brain> BRAINS = new ConcurrentHashMap<>();

	/** The takeover's own move cooldowns (ticks), separate from the player's. */
	private static final Map<String, Integer> COOLDOWNS = Map.of(
			SentryAbilityManager.SHADOW_LASH, 16,
			SentryAbilityManager.ERASE, 240,
			SentryAbilityManager.GRASP, 100,
			SentryAbilityManager.VOID_RIFT, 300,
			SentryAbilityManager.NIGHTFALL, 600);

	private static final float FIST_DAMAGE = 18f;

	private VoidTakeover() {
	}

	static void clearSessionState() {
		BRAINS.clear();
	}

	static Brain brain(ServerPlayer p) {
		return BRAINS.computeIfAbsent(p.getUUID(), k -> new Brain());
	}

	/** The last move the Void used for this player (tests). */
	public static String lastMove(ServerPlayer p) {
		Brain b = BRAINS.get(p.getUUID());
		return b == null ? null : b.lastMove;
	}

	/** How many hits the Void has landed this takeover (tests). */
	public static int hits(ServerPlayer p) {
		Brain b = BRAINS.get(p.getUUID());
		return b == null ? 0 : b.hits;
	}

	/** Whom the Void may hurt: everything living -- squadmates and pets too -- but never the body itself, a spectator or a creative player. */
	public static boolean canHit(ServerPlayer owner, Entity target) {
		return target != owner && HeroTargets.canHarm(null, target);
	}

	// ---------------------------------------------------------------- begin / end

	/** The Void reached 100: it takes the body (not for creative players, nor inside spawn protection). True if it began. */
	public static boolean tryBegin(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		if (!s.hasPower || s.inTakeover() || !p.isAlive() || p.isSpectator() || Sentry.free(p)) {
			return false;
		}
		if (p.getServer() != null && p.getServer().isUnderSpawnProtection(p.serverLevel(), p.blockPosition(), p)) {
			SentryState n = s.copy();
			n.voidLevel = Math.min(n.voidLevel, SentryConfig.VOID_MAX - 1f);
			Sentry.save(p, n);
			Sentry.say(p, "message.projecthero.sentry.spawn_held", ChatFormatting.DARK_GRAY);
			return false;
		}
		begin(p);
		return true;
	}

	/** Starts a takeover outright (the command, the tests). */
	public static void begin(ServerPlayer p) {
		long now = p.level().getGameTime();
		SentryMoves.forget(p.getUUID());
		SentryFlight.stop(p);
		SentryState n = Sentry.state(p).copy();
		n.takeoverStart = Math.max(1L, now);
		n.voidLevel = SentryConfig.VOID_MAX;
		n.voidForm = true;
		n.flying = false;
		n.calming = false;
		n.beamStart = 0L;
		n.sunsStart = 0L;
		n.gripTarget = -1;
		n.room = 0;
		n.roomStartedAt = n.takeoverStart + SentryConfig.MIND_DELAY_TICKS;
		n.mindLockedUntil = n.takeoverStart + SentryConfig.MIND_DELAY_TICKS;
		n.grip = 100f;
		n.takeoverTarget = -1;
		Sentry.save(p, n);
		BRAINS.put(p.getUUID(), new Brain());
		Sentry.reconcile(p);
		if (!p.getAbilities().instabuild) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 80, 0.8, 1.2, 0.8, 0.08);
		level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1.0, p.getZ(), 60, 0.8, 1.2, 0.8, 0.08);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_ROAR, net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 0.5f);
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.projecthero.sentry.takeover.title")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD)));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.projecthero.sentry.takeover.sub")
				.withStyle(ChatFormatting.GOLD)));
		Component warn = Component.translatable("message.projecthero.sentry.takeover.warn", p.getDisplayName()).withStyle(ChatFormatting.DARK_RED);
		for (ServerPlayer other : level.players()) {
			if (other != p && other.distanceToSqr(p) < 96 * 96) {
				other.displayClientMessage(warn, false);
			}
		}
	}

	/** The takeover ends ({@code why}); the eaten world is scheduled to come back. */
	public static void end(ServerPlayer p, End why) {
		SentryState s = Sentry.state(p);
		if (!s.inTakeover()) {
			return;
		}
		long now = p.level().getGameTime();
		SentryState n = s.copy();
		n.takeoverStart = 0L;
		n.voidForm = false;
		n.flying = false;
		n.takeoverTarget = -1;
		n.room = 0;
		n.grip = 100f;
		n.roomStartedAt = 0L;
		n.mindLockedUntil = 0L;
		n.nightfallUntil = 0L;
		switch (why) {
			case WON, COMMAND -> {
				n.voidLevel = SentryConfig.VOID_AFTER_WIN;
				n.letOutLockedUntil = now + SentryConfig.LET_OUT_LOCK_TICKS;
				n.kneelUntil = now + SentryConfig.KNEEL_TICKS;
			}
			default -> n.voidLevel = SentryConfig.VOID_AFTER_DEATH;
		}
		Sentry.save(p, n);
		BRAINS.remove(p.getUUID());
		VoidMoves.forget(p.getUUID());
		if (!p.getAbilities().instabuild && !p.isSpectator()) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		Sentry.reconcile(p);
		ServerLevel level = p.serverLevel();
		VoidMatter.scheduleRestore(level, p);
		if (why == End.WON || why == End.COMMAND) {
			Vec3 c = p.position().add(0, 1.0, 0);
			SentryFx.send(level, SentryFxPayload.THROWN_OUT, c, 8f, 30, null, p.getId());
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.0);
			level.sendParticles(Sentry.dust(Sentry.GOLD, 2.2f), c.x, c.y, c.z, 80, 1.0, 1.2, 1.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 0.8, 1.0, 0.8, 0.15);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 2.0f, 1.2f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.4f);
			p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 20));
			p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.projecthero.sentry.won.title")
					.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
			p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.projecthero.sentry.won.sub")
					.withStyle(ChatFormatting.GRAY)));
			AbilityHelpers.launchSelf(p, new Vec3(0, -0.3, 0));
		}
	}

	// ---------------------------------------------------------------- outside help

	/**
	 * {@code helper} holds right click on {@code body} with an empty hand: the Void's grip drains (more by day, less by night), and the
	 * Void turns on them. True if it counted.
	 */
	public static boolean help(ServerPlayer helper, ServerPlayer body) {
		if (helper == body || !Sentry.inTakeover(body) || !helper.getMainHandItem().isEmpty() || helper.isSpectator() || !helper.isAlive()) {
			return false;
		}
		if (helper.distanceToSqr(body) > 6.0 * 6.0) {
			return false;
		}
		SentryState s = Sentry.state(body);
		float amount = SentryConfig.HELP_PER_CLICK * (body.level().isDay() ? 1.2f : 0.8f);
		Brain b = brain(body);
		b.helper = helper.getUUID();
		b.helpedAt = body.level().getGameTime();
		SentryState n = s.copy();
		n.grip = Math.max(0f, s.grip - amount);
		Sentry.save(body, n);
		ServerLevel level = body.serverLevel();
		if (body.tickCount % 4 == 0) {
			SentryFx.send(level, SentryFxPayload.REACH, helper.getEyePosition().add(0, -0.3, 0), 0.2f, 6,
					body.position().add(0, 1.0, 0), helper.getId());
		}
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.0f), body.getX(), body.getY() + 1.0, body.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
		if (n.grip <= 0f) {
			breakRoom(body);
		}
		return true;
	}

	/** The grip hit 0: the current room breaks open on its own. */
	static void breakRoom(ServerPlayer body) {
		SentryState n = Sentry.state(body).copy();
		n.room = Math.min(SentryConfig.ROOMS, n.room + 1);
		n.grip = SentryConfig.GRIP_AFTER_BREAK;
		long now = body.level().getGameTime();
		n.roomStartedAt = Math.max(now, n.mindLockedUntil);
		Sentry.save(body, n);
		Sentry.say(body, "message.projecthero.sentry.room_broken", ChatFormatting.GOLD);
		if (n.room >= SentryConfig.ROOMS) {
			end(body, End.WON);
		}
	}

	/** A kill by the Void: it absorbs it (heals). */
	static void absorb(ServerPlayer body, LivingEntity victim) {
		body.heal(SentryConfig.ABSORB_HEAL);
		ServerLevel level = body.serverLevel();
		Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.02);
		SentryFx.send(level, SentryFxPayload.TENDRIL, c, 0.2f, 10, body.position().add(0, 1.0, 0), body.getId());
	}

	// ---------------------------------------------------------------- the tick

	public static void tick(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		long now = p.level().getGameTime();
		long ticks = now - s.takeoverStart;
		Brain b = brain(p);
		p.getFoodData().setExhaustion(0f);
		if (!p.getAbilities().instabuild && (!p.getAbilities().mayfly || !p.getAbilities().flying)) {
			p.getAbilities().mayfly = true;
			p.getAbilities().flying = true;
			p.onUpdateAbilities();
		}
		p.resetFallDistance();
		if (p.tickCount % 20 == 0) {
			Sentry.reconcile(p);
			// the grip creeps back (night more than day)
			float regen = p.level().isDay() ? SentryConfig.GRIP_REGEN_DAY : SentryConfig.GRIP_REGEN_NIGHT;
			if (s.grip < 100f) {
				SentryState n = s.copy();
				n.grip = Math.min(100f, s.grip + regen);
				Sentry.save(p, n);
			}
		}
		VoidMoves.tick(p);
		VoidMatter.tickRing(p, ticks);
		fight(p, b, now);
		if (p.tickCount % 2 == 0) {
			ServerLevel level = p.serverLevel();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 2, 0.3, 0.8, 0.3, 0.01);
			level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0.4, 0.4, 0.4, 0.0);
		}
	}

	/** The target: whoever is reaching for it (it takes hits for that), else the nearest living thing within 24. */
	static LivingEntity pickTarget(ServerPlayer p, Brain b, long now) {
		ServerLevel level = p.serverLevel();
		if (b.helper != null && now - b.helpedAt < 60) {
			Player h = level.getPlayerByUUID(b.helper);
			if (h != null && canHit(p, h) && h.distanceToSqr(p) < 16 * 16) {
				return h;
			}
		}
		double r = SentryConfig.TAKEOVER_HUNT_RANGE;
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> canHit(p, e))) {
			double d = e.distanceToSqr(p);
			if (d <= r * r && d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	private static void fight(ServerPlayer p, Brain b, long now) {
		SentryState s = Sentry.state(p);
		LivingEntity target = s.takeoverTarget >= 0 && p.level().getEntity(s.takeoverTarget) instanceof LivingEntity le && le.isAlive()
				&& canHit(p, le) && le.distanceToSqr(p) < 40 * 40 ? le : null;
		if (now >= b.nextLook || target == null) {
			LivingEntity next = pickTarget(p, b, now);
			b.nextLook = now + 10;
			if (next != target) {
				target = next;
				SentryState n = Sentry.state(p).copy();
				n.takeoverTarget = target == null ? -1 : target.getId();
				Sentry.save(p, n);
			}
		}
		if (target == null) {
			wander(p, b, now);
			return;
		}
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
		face(p, at);
		double dist = Math.sqrt(target.distanceToSqr(p));
		// close in to 3 blocks, a little above it
		Vec3 want = target.position().add(0, Math.max(1.0, target.getBbHeight() * 0.6), 0);
		Vec3 to = want.subtract(p.position());
		Vec3 v = dist > 3.0 ? to.normalize().scale(Math.min(SentryConfig.TAKEOVER_SPEED, dist * 0.2)) : Vec3.ZERO;
		AbilityHelpers.launchSelf(p, new Vec3(v.x, Mth.clamp(to.y * 0.15, -0.4, 0.4), v.z));
		// its fists, up close
		if (dist <= 3.5 && now >= b.nextSwing) {
			b.nextSwing = now + 10;
			p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
			if (Sentry.hit(p, target, FIST_DAMAGE, true)) {
				b.hits++;
			}
		}
		// the kit at full strength, picked by range
		String move = null;
		if (dist <= SentryConfig.LASH_RANGE && ready(b, SentryAbilityManager.SHADOW_LASH, now)) {
			move = SentryAbilityManager.SHADOW_LASH;
		} else if (dist <= 20.0 && ready(b, SentryAbilityManager.ERASE, now) && p.hasLineOfSight(target)) {
			move = SentryAbilityManager.ERASE;
		} else if (dist > SentryConfig.LASH_RANGE && dist <= SentryConfig.GRASP_RANGE && ready(b, SentryAbilityManager.GRASP, now)) {
			move = SentryAbilityManager.GRASP;
		} else if (dist <= SentryConfig.RIFT_RANGE && ready(b, SentryAbilityManager.VOID_RIFT, now)) {
			move = SentryAbilityManager.VOID_RIFT;
		} else if (ready(b, SentryAbilityManager.NIGHTFALL, now) && crowd(p) >= 2) {
			move = SentryAbilityManager.NIGHTFALL;
		}
		if (move != null) {
			face(p, at);
			if (VoidMoves.fire(p, move, target)) {
				b.ready.put(move, now + COOLDOWNS.getOrDefault(move, 100));
				b.lastMove = move;
				b.hits++;
			} else {
				b.ready.put(move, now + 20);
			}
		}
	}

	private static boolean ready(Brain b, String id, long now) {
		return b.ready.getOrDefault(id, Long.MIN_VALUE) <= now;
	}

	private static int crowd(ServerPlayer p) {
		double r = SentryConfig.NIGHTFALL_RADIUS;
		return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> canHit(p, e) && e.distanceToSqr(p) <= r * r).size();
	}

	static void face(ServerPlayer p, Vec3 at) {
		Vec3 d = at.subtract(p.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		float yaw = flat < 1.0e-4 ? p.getYRot() : (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0f;
		float pitch = (float) (-Mth.atan2(d.y, Math.max(1.0e-4, flat)) * Mth.RAD_TO_DEG);
		p.setYRot(yaw);
		p.setYHeadRot(yaw);
		p.yBodyRot = yaw;
		p.setXRot(Mth.clamp(pitch, -80f, 80f));
	}

	/** Nothing to hunt: toward a player within 128, else a village's meeting point, else a fresh random heading every 5 s. */
	private static void wander(ServerPlayer p, Brain b, long now) {
		ServerLevel level = p.serverLevel();
		if (now >= b.nextWander || b.wanderTo == null) {
			b.nextWander = now + 100;
			b.wanderTo = null;
			double r = SentryConfig.TAKEOVER_WANDER_RANGE;
			ServerPlayer near = null;
			double best = r * r;
			for (ServerPlayer o : level.players()) {
				if (o != p && canHit(p, o) && o.distanceToSqr(p) < best) {
					near = o;
					best = o.distanceToSqr(p);
				}
			}
			if (near != null) {
				b.wanderTo = near.position();
			} else {
				Optional<BlockPos> village = level.getPoiManager().findClosest(VoidTakeover::meeting, p.blockPosition(), (int) r,
						PoiManager.Occupancy.ANY);
				if (village.isPresent()) {
					b.wanderTo = Vec3.atCenterOf(village.get());
				} else {
					float yaw = p.getRandom().nextFloat() * 360f;
					b.wanderTo = p.position().add(Vec3.directionFromRotation(0f, yaw).scale(24.0));
				}
			}
		}
		Vec3 to = b.wanderTo.subtract(p.position());
		double flat = Math.sqrt(to.x * to.x + to.z * to.z);
		if (flat < 2.0) {
			b.nextWander = now; // arrived: pick again
		}
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
		double dy = (ground + 4.0) - p.getY();
		Vec3 dir = flat < 1.0e-3 ? Vec3.ZERO : new Vec3(to.x / flat, 0, to.z / flat);
		double speed = SentryConfig.TAKEOVER_SPEED * 0.6;
		AbilityHelpers.launchSelf(p, new Vec3(dir.x * speed, Mth.clamp(dy * 0.1, -0.3, 0.3), dir.z * speed));
		if (flat > 1.0e-3) {
			face(p, p.getEyePosition().add(dir));
		}
	}

	private static boolean meeting(Holder<PoiType> type) {
		return type.is(PoiTypes.MEETING);
	}
}
