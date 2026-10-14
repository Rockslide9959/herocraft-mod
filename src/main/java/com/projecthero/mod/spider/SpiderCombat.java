package com.projecthero.mod.spider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.SpiderWebStrandPayload;
import com.projecthero.mod.spider.data.SpiderMoveState;
import com.projecthero.mod.spider.entity.ImpactWebEntity;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Spider-Man's web-line combat plumbing (v0.12.20; v0.16.1 the Combat Mode toggle is gone and these are moves of the
 * one 18-move kit, see {@link SpiderMoves}): the web strands drawn from the hand, the Web Yank disarm, the Web
 * Swing-Throw (hold Z), the Impact Web ball (hold R) with its wall pin, and the Point Zip haul.
 *
 * Everything is server-driven and re-validated; the per-owner sessions live in static maps and are dropped by
 * {@link #clearSessionState} (see {@code ServerStateReset}) and by {@link #clear} on death / relog.
 */
public final class SpiderCombat {
	public static final String IMPACT_WEB = "impact_web";

	public static final float IMPACT_DAMAGE = 10.0f;
	public static final double IMPACT_KNOCKBACK = 1.6;
	public static final double IMPACT_SPEED = 2.2;
	public static final int IMPACT_LIFE_TICKS = 30;
	/** How long a target struck by an Impact Web stays cocooned (and, if it hits a wall, pinned to it): twelve seconds. */
	public static final int IMPACT_PIN_TICKS = 18 * 20; // v0.16.1: the default of SpiderManConfig.moves().impactPinTicks (18 s)
	/** How long after the hit a knocked-back target is watched for a wall collision. */
	private static final int PIN_WATCH_TICKS = 14;

	private static final double THROW_RANGE = 30.0;
	private static final int THROW_MAX_TICKS = 5 * 20;
	private static final double THROW_SPEED = 2.4;

	/** How long a strand takes to phase out once it lets go (v0.12.20: five seconds). */
	public static final int STRAND_FADE_TICKS = 5 * 20;

	// strand slots (per player; a newer strand with the same slot replaces the old one)
	public static final int SLOT_ZIP = 1;
	public static final int SLOT_STRIKE = 2;
	public static final int SLOT_THROW = 3;
	public static final int SLOT_DISARM = 4;
	/** v0.16.1 */
	public static final int SLOT_SHOT = 5;
	public static final int SLOT_TIGHTROPE = 6;
	public static final int SLOT_WEAVE = 7;
	public static final int SLOT_GRAB = 8;
	public static final int SLOT_WHIP = 9;
	public static final int SLOT_COUNTER = 10;
	/** v0.16.1: the Web Yank line (hand -> whatever is being hauled, for as long as the pull lasts). */
	public static final int SLOT_YANK = 11;
	/** v0.16.1: Web Blossom's lines -- one slot per caught target, from this base up. */
	public static final int SLOT_BLOSSOM_BASE = 20;
	public static final int BLOSSOM_LINES = 16;

	private static final class Throw {
		final int targetId;
		final long startedAt;
		double angle;

		Throw(int targetId, long startedAt, double angle) {
			this.targetId = targetId;
			this.startedAt = startedAt;
			this.angle = angle;
		}
	}

	/** A Point Zip in progress: hauled toward {@code target} every tick until it arrives (v0.12.21). */
	private static final class ZipPull {
		final Vec3 target;
		final long startedAt;
		double lastDist = Double.MAX_VALUE;
		int stalled;
		/** v0.16.1: zipping onto a rope -- where his feet land on top of it, and the way he faces (along it); null = a block zip. */
		Vec3 landFeet;
		float landYaw;

		ZipPull(Vec3 target, long startedAt) {
			this.target = target;
			this.startedAt = startedAt;
		}
	}

	private static final Map<UUID, ZipPull> ZIPS = new ConcurrentHashMap<>();

	/** v0.16.1: a live web line held on something being pulled (Web Yank): it follows the hand and the target. */
	private record Tether(int slot, int entityId, long start, int maxTicks) {
	}

	private static final Map<UUID, Tether> TETHERS = new ConcurrentHashMap<>();

	/** v0.16.1: a Swing-Thrown body watched for flying into a Weave web (it sticks there). */
	private static final class Flight {
		final int entityId;
		final long until;
		Vec3 last;

		Flight(int entityId, long until, Vec3 last) {
			this.entityId = entityId;
			this.until = until;
			this.last = last;
		}
	}

	private static final Map<UUID, List<Flight>> FLIGHTS = new ConcurrentHashMap<>();
	/** Swing Throw aim assist onto a Weave web: the cone round the aim (degrees). */
	public static final double WEAVE_ASSIST_CONE = 14.0;

	private record Watch(int targetId, long until, Vec3 dir) {
	}

	private static final Map<UUID, Throw> THROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Watch>> WATCHES = new ConcurrentHashMap<>();

	private SpiderCombat() {
	}

	public static void clearSessionState() {
		THROWS.clear();
		WATCHES.clear();
		ZIPS.clear();
		TETHERS.clear();
		FLIGHTS.clear();
	}

	/** Death / relog / dimension change: drop everything this player had going. */
	public static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		if (THROWS.remove(id) != null) {
			sendStrand(player, SLOT_THROW, null, player.position(), 0, 0);
		}
		WATCHES.remove(id);
		ZIPS.remove(id);
		Tether tether = TETHERS.remove(id);
		if (tether != null) {
			sendStrand(player, tether.slot(), -1, player.position(), 0, 0);
		}
		FLIGHTS.remove(id);
	}

	public static boolean isThrowing(ServerPlayer player) {
		return THROWS.containsKey(player.getUUID());
	}

	// ---------------- strands ----------------

	/** Draw (or replace) a web strand from {@code owner}'s hand; seen by everyone tracking them. */
	public static void sendStrand(ServerPlayer owner, int slot, LivingEntity target, Vec3 pos, int hold, int fade) {
		sendStrand(owner, slot, target == null ? -1 : target.getId(), pos, hold, fade);
	}

	/** Same, with any entity (a thrown block, an item) as the far end. */
	public static void sendStrand(ServerPlayer owner, int slot, int targetId, Vec3 pos, int hold, int fade) {
		SpiderWebStrandPayload payload = new SpiderWebStrandPayload(owner.getId(), slot, targetId,
				pos.x, pos.y, pos.z, hold, fade, SpiderMoves.rightHand(owner));
		if (ServerPlayNetworking.canSend(owner, SpiderWebStrandPayload.TYPE)) {
			ServerPlayNetworking.send(owner, payload);
		}
		for (ServerPlayer p : PlayerLookup.tracking(owner)) {
			if (ServerPlayNetworking.canSend(p, SpiderWebStrandPayload.TYPE)) {
				ServerPlayNetworking.send(p, payload);
			}
		}
	}

	/**
	 * v0.16.1: hold a real web line from the hand to {@code end} while it is being pulled -- it follows both ends every
	 * frame on every client -- and let it go (a quick fade) once the pull is over: the two ends have met, the thing has
	 * stopped, it is gone, or {@code maxTicks} have passed.
	 */
	public static void beginTether(ServerPlayer player, int slot, Entity end, int maxTicks) {
		Vec3 at = end.position().add(0, end.getBbHeight() * 0.5, 0);
		TETHERS.put(player.getUUID(), new Tether(slot, end.getId(), player.level().getGameTime(), maxTicks));
		sendStrand(player, slot, end.getId(), at, maxTicks + 5, 0);
	}

	/** True while a pull line is held (tests). */
	public static boolean hasTether(ServerPlayer player) {
		return TETHERS.containsKey(player.getUUID());
	}

	private static void tickTether(ServerPlayer player, Tether t) {
		Entity e = player.level().getEntity(t.entityId());
		long age = player.level().getGameTime() - t.start();
		boolean over = e == null || !e.isAlive() || age > t.maxTicks();
		Vec3 at = e == null ? player.position() : e.position().add(0, e.getBbHeight() * 0.5, 0);
		if (!over && age >= 2) {
			double gap = at.distanceTo(player.position().add(0, player.getBbHeight() * 0.6, 0));
			boolean still = e.getDeltaMovement().lengthSqr() < 0.0025 && player.getDeltaMovement().lengthSqr() < 0.0025;
			over = gap < 1.8 || (age >= 4 && still);
		}
		if (over) {
			TETHERS.remove(player.getUUID());
			sendStrand(player, t.slot(), -1, at, 0, 4);
		}
	}

	static Vec3 center(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	static LivingEntity pickTarget(ServerPlayer player, double range) {
		LivingEntity t = AbilityHelpers.raycastEntity(player, range);
		return t != null ? t : SpiderAbilities.nearestLivingInAim(player, range);
	}

	// ---------------- Shift+G -- Web Yank: disarm ----------------

	/**
	 * Web Yank aimed at a mob or player holding something: the webbing rips it out of their hands and it flies to the
	 * caster. Returns false (nothing spent) when there is no armed target, so the caller falls back to the ordinary
	 * yank.
	 */
	public static boolean tryDisarm(ServerPlayer player) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, SpiderAbilities.YANK_RANGE);
		if (target == null) {
			return false;
		}
		ItemStack main = target.getItemBySlot(EquipmentSlot.MAINHAND);
		ItemStack off = target.getItemBySlot(EquipmentSlot.OFFHAND);
		if (main.isEmpty() && off.isEmpty()) {
			return false;
		}
		SpiderManConfig.Move cfg = SpiderManConfig.moves().webYank;
		if (!SpiderAbilities.gate(player, SpiderMoves.WEB_YANK, cfg.cost)) {
			return true; // handled: told the player why
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 grab = player.getEyePosition().subtract(0, 0.4, 0);
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack held = target.getItemBySlot(slot);
			if (held.isEmpty()) {
				continue;
			}
			target.setItemSlot(slot, ItemStack.EMPTY);
			Vec3 from = target.position().add(0, target.getBbHeight() * 0.65, 0);
			ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, held);
			Vec3 delta = grab.subtract(from);
			double d = Math.max(1.0, delta.length());
			item.setDeltaMovement(delta.normalize().scale(Math.min(3.2, 0.9 + d * 0.13)).add(0, 0.12, 0));
			item.setNoPickUpDelay();
			level.addFreshEntity(item);
		}
		if (target instanceof Mob mob) {
			mob.setTarget(null);
		}
		sendStrand(player, SLOT_DISARM, target, center(target), 10, STRAND_FADE_TICKS);
		AbilityHelpers.burst(level, center(target), ParticleTypes.ITEM_COBWEB, 12, 0.3);
		SpiderMoves.thwip(player, 1.0f);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.8f, 1.6f);
		SpiderMoves.anim(player, SpiderAnim.YANK);
		SpiderAbilities.fired(player, SpiderMoves.WEB_YANK, cfg.cost, cfg.cooldown);
		return true;
	}

	// ---------------- hold Z -- Web Swing-Throw ----------------

	public static boolean beginThrow(ServerPlayer player) {
		if (THROWS.containsKey(player.getUUID())) {
			return false;
		}
		SpiderManConfig.Move cfg = SpiderManConfig.moves().swingThrow;
		if (!SpiderAbilities.gate(player, SpiderMoves.SWING_THROW, cfg.cost)) {
			return false;
		}
		LivingEntity target = pickTarget(player, THROW_RANGE);
		if (target == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_target"), true);
			return false;
		}
		if (SpiderWebs.tooHeavyToThrow(target)) { // v0.16.2: bosses, iron golems, too big
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.too_heavy"), true);
			return false;
		}
		SpiderWebReserve.spend(player, cfg.cost, true);
		com.projecthero.mod.spider.web.WebStructures.tearFree(target); // v0.16.1: stuck on a web -> it comes off, the web snaps
		Vec3 rel = target.position().subtract(player.position());
		THROWS.put(player.getUUID(), new Throw(target.getId(), player.level().getGameTime(), Math.atan2(rel.z, rel.x)));
		sendStrand(player, SLOT_THROW, target, center(target), THROW_MAX_TICKS + 10, 0);
		SpiderMoves.thwip(player, 0.9f);
		SpiderMoves.setFlag(player, SpiderMoveState.FLAG_SWING_THROW, true);
		return true;
	}

	/** Z released: hurl whatever is on the end of the web along the look direction. */
	public static void releaseThrow(ServerPlayer player) {
		Throw t = THROWS.get(player.getUUID());
		if (t != null) {
			finishThrow(player, t, true);
		}
	}

	private static void finishThrow(ServerPlayer player, Throw t, boolean hurl) {
		THROWS.remove(player.getUUID());
		SpiderMoves.setFlag(player, SpiderMoveState.FLAG_SWING_THROW, false);
		Entity e = player.level().getEntity(t.targetId);
		Vec3 last = player.position();
		if (e instanceof LivingEntity target && target.isAlive()) {
			last = center(target);
			if (hurl) {
				Vec3 look = player.getLookAngle();
				// v0.16.1: aimed at (or near) a Weave web -> thrown onto it, where it sticks (see tickFlights)
				Vec3 onWeb = com.projecthero.mod.spider.web.WebStructures.weaveAimPoint(player, THROW_RANGE, WEAVE_ASSIST_CONE);
				target.setDeltaMovement(onWeb != null ? throwOnto(last, onWeb) : look.scale(THROW_SPEED).add(0, 0.2, 0));
				FLIGHTS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
						.add(new Flight(target.getId(), player.level().getGameTime() + 40, last));
				target.hurtMarked = true;
				target.fallDistance = 0.0f;
				AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
				SpiderMoves.watchThrown(player, target, SpiderManConfig.moves().swingThrow.damage);
				SpiderMoves.anim(player, SpiderAnim.SWING_THROW_RELEASE);
			}
		}
		sendStrand(player, SLOT_THROW, null, last, 0, STRAND_FADE_TICKS);
		SpiderMan.triggerCooldown(player, SpiderMoves.SWING_THROW, SpiderManConfig.moves().swingThrow.cooldown);
	}

	/**
	 * The launch velocity that carries a thrown body's centre from {@code from} onto {@code to}: a mob in the air loses 9%
	 * of its horizontal speed a tick and falls 0.08/tick^2, so both are allowed for over the flight time.
	 */
	public static Vec3 throwOnto(Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		int t = (int) Math.max(3, Math.ceil(flat / THROW_SPEED) + 1);
		double h = flat < 1.0e-4 ? 0.0 : flat * 0.09 / (1.0 - Math.pow(0.91, t));
		double vy = (d.y + 0.04 * t * (t - 1)) / t;
		Vec3 v = flat < 1.0e-4 ? new Vec3(0, vy, 0) : new Vec3(d.x / flat * h, vy, d.z / flat * h);
		return v.length() > 4.0 ? v.normalize().scale(4.0) : v;
	}

	/** Swing-Thrown bodies in flight: one that passes through a Weave web is caught on it (stuck, as if it walked in). */
	private static void tickFlights(ServerPlayer player, List<Flight> flights) {
		long now = player.level().getGameTime();
		flights.removeIf(f -> {
			Entity e = player.level().getEntity(f.entityId);
			if (!(e instanceof LivingEntity body) || !body.isAlive() || now > f.until) {
				return true;
			}
			Vec3 c = center(body);
			// this tick's travel plus the next one's, so a fast body cannot skip over a strand between two ticks
			Vec3 ahead = c.add(body.getDeltaMovement());
			if (com.projecthero.mod.spider.web.WebStructures.catchOnWeave(body, f.last, ahead) != null) {
				return true;
			}
			f.last = c;
			return body.onGround() && now > f.until - 36;
		});
	}

	private static void tickThrow(ServerPlayer player, Throw t) {
		ServerLevel level = (ServerLevel) player.level();
		Entity e = level.getEntity(t.targetId);
		if (!(e instanceof LivingEntity target) || !target.isAlive()) {
			finishThrow(player, t, false);
			return;
		}
		if (level.getGameTime() - t.startedAt > THROW_MAX_TICKS) {
			finishThrow(player, t, true);
			return;
		}
		t.angle += 0.55;
		Vec3 head = player.position().add(0, player.getBbHeight() + 0.2, 0);
		Vec3 desired = head.add(Math.cos(t.angle) * 2.3, -target.getBbHeight() * 0.5, Math.sin(t.angle) * 2.3);
		Vec3 v = desired.subtract(target.position()).scale(0.7);
		if (v.length() > 2.2) {
			v = v.normalize().scale(2.2);
		}
		target.setDeltaMovement(v);
		target.hurtMarked = true;
		target.fallDistance = 0.0f;
		if (target instanceof Mob mob) {
			mob.setTarget(null);
		}
	}

	// ---------------- hold R -- Impact Web ----------------

	/** Fire the heavy web ball; {@code charge} 0..1 adds up to half the damage again. */
	public static void impactWeb(ServerPlayer player, float charge) {
		SpiderManConfig.Move cfg = SpiderManConfig.moves().impactWeb;
		if (!SpiderAbilities.gate(player, IMPACT_WEB, cfg.cost)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		ImpactWebEntity web = new ImpactWebEntity(level, player, look.scale(IMPACT_SPEED * (1.0 + charge * 0.3)));
		web.damage = cfg.damage * (1.0f + 0.5f * charge);
		web.knockback = IMPACT_KNOCKBACK * (1.0 + 0.4 * charge);
		Vec3 start = player.getEyePosition().add(look.scale(0.9)).subtract(0, 0.15, 0);
		web.setPos(start.x, start.y, start.z);
		level.addFreshEntity(web);
		SpiderMoves.thwip(player, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 0.9f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.CROSSBOW_SHOOT, 0.6f, 0.6f);
		SpiderMoves.anim(player, SpiderAnim.IMPACT_RELEASE);
		SpiderAbilities.fired(player, IMPACT_WEB, cfg.cost, cfg.cooldown);
	}

	/** Called by {@link ImpactWebEntity} on an entity hit: watch the knocked-back target for a wall collision. */
	public static void watchImpact(ServerPlayer owner, LivingEntity target, Vec3 travel) {
		Vec3 dir = new Vec3(travel.x, 0.0, travel.z);
		if (dir.lengthSqr() < 1.0e-4) {
			return;
		}
		WATCHES.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>())
				.add(new Watch(target.getId(), owner.level().getGameTime() + PIN_WATCH_TICKS, dir.normalize()));
	}

	private static void tickWatches(ServerPlayer player, List<Watch> watches) {
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		watches.removeIf(w -> {
			Entity e = level.getEntity(w.targetId());
			if (!(e instanceof LivingEntity target) || !target.isAlive() || now > w.until()) {
				return true;
			}
			boolean wall = !level.noCollision(target, target.getBoundingBox().move(w.dir().scale(0.4)));
			if (!wall) {
				return false;
			}
			SpiderWebs.cocoonFor(player, target, SpiderManConfig.moves().impactPinTicks);
			SpiderCobwebs.placeAt(player, java.util.List.of(target.blockPosition(), target.blockPosition().above()));
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtMarked = true;
			Vec3 c = center(target);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 22, 0.4, 0.5, 0.4, 0.04);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.COBWEB_PLACE,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
			return true;
		});
	}

	// ---------------- G -- Point Zip haul ----------------

	public static void beginZipPull(ServerPlayer player, Vec3 target) {
		ZIPS.put(player.getUUID(), new ZipPull(target, player.level().getGameTime()));
	}

	/**
	 * v0.16.1 Point Zip onto one of his web strands: hauled at the point and, on arrival (or once the haul stalls with
	 * nothing solid in the way), set down STANDING on top of the strand, balanced and facing along it.
	 */
	public static void beginZipLanding(ServerPlayer player, Vec3 feet, float yaw) {
		ZipPull z = new ZipPull(feet.add(0, player.getEyeHeight(), 0), player.level().getGameTime());
		z.landFeet = feet;
		z.landYaw = yaw;
		ZIPS.put(player.getUUID(), z);
	}

	private static void tickZip(ServerPlayer player, ZipPull zip) {
		if (zip.landFeet != null) {
			tickZipLanding(player, zip);
			return;
		}
		Vec3 delta = zip.target.subtract(player.getEyePosition());
		double dist = delta.length();
		boolean timedOut = player.level().getGameTime() - zip.startedAt > 60;
		zip.stalled = dist > zip.lastDist - 0.05 ? zip.stalled + 1 : 0;
		zip.lastDist = dist;
		if (dist < 1.9 || timedOut || zip.stalled > 5 || SpiderSwing.isSwinging(player) || player.isSpectator()) {
			ZIPS.remove(player.getUUID());
			if (dist < 1.9) {
				AbilityHelpers.launchSelf(player, player.getDeltaMovement().scale(0.2)); // arrive, do not overshoot
			}
			return;
		}
		AbilityHelpers.launchSelf(player, delta.normalize().scale(Math.min(3.0, 1.1 + dist * 0.08)));
	}

	private static void tickZipLanding(ServerPlayer player, ZipPull zip) {
		Vec3 eye = player.getEyePosition();
		Vec3 delta = zip.target.subtract(eye);
		double dist = delta.length();
		boolean timedOut = player.level().getGameTime() - zip.startedAt > 60;
		zip.stalled = dist > zip.lastDist - 0.05 ? zip.stalled + 1 : 0;
		zip.lastDist = dist;
		if (SpiderSwing.isSwinging(player) || player.isSpectator()) {
			ZIPS.remove(player.getUUID());
			return;
		}
		boolean clear = player.level().clip(new net.minecraft.world.level.ClipContext(eye, zip.target,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player))
				.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
		if (dist < 1.3 || ((zip.stalled > 5 || timedOut) && clear)) {
			ZIPS.remove(player.getUUID());
			Vec3 f = zip.landFeet;
			player.connection.teleport(f.x, f.y + 0.03, f.z, zip.landYaw, player.getXRot());
			AbilityHelpers.launchSelf(player, Vec3.ZERO);
			player.resetFallDistance();
			return;
		}
		if (zip.stalled > 5 || timedOut) {
			ZIPS.remove(player.getUUID()); // something solid in the way: just stop
			return;
		}
		AbilityHelpers.launchSelf(player, delta.normalize().scale(Math.min(2.6, 0.9 + dist * 0.08)));
	}

	// ---------------- tick ----------------

	/** Per-owner server tick, called from {@code SpiderManAbilityManager.serverTick}. */
	public static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		ZipPull zip = ZIPS.get(id);
		if (zip != null) {
			tickZip(player, zip);
		}
		Throw t = THROWS.get(id);
		if (t != null) {
			tickThrow(player, t);
		}
		Tether tether = TETHERS.get(id);
		if (tether != null) {
			tickTether(player, tether);
		}
		List<Flight> flights = FLIGHTS.get(id);
		if (flights != null) {
			if (flights.isEmpty()) {
				FLIGHTS.remove(id);
			} else {
				tickFlights(player, flights);
			}
		}
		List<Watch> watches = WATCHES.get(id);
		if (watches != null) {
			if (watches.isEmpty()) {
				WATCHES.remove(id);
			} else {
				tickWatches(player, watches);
			}
		}
	}
}
