package com.projecthero.mod.spider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.SpiderWebStrandPayload;
import com.projecthero.mod.spider.data.SpiderManState;
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
 * Spider-Man's Combat Mode (v0.12.20). N swaps the Web Swing / Zip / Yank / Shot kit ("Traversal Mode") for:
 *
 * <pre>
 *   R  Web Strike   web a target, get hauled straight at it, hit for 15 + knockback           (3 s cooldown)
 *   G  Web Zip      as ever -- but aimed at a mob/player it rips whatever they hold out of their hands
 *   X  Web-Throw    hold: web a target and whirl it around your head; release: hurl it where you look (3 s)
 *   Z  Impact Web   one heavy web ball: 10 damage, knockback, pins to a wall for 6 s                   (1 s)
 *   V  Web Net / sneak+hold Web Blossom (2 s charge here instead of 3 s)
 *   C  Wall Crawl toggle
 * </pre>
 *
 * Everything is server-driven and re-validated; the per-owner sessions live in static maps and are dropped by
 * {@link #clearSessionState} (see {@code ServerStateReset}) and by {@link #clear} on death / relog.
 */
public final class SpiderCombat {
	public static final String WEB_STRIKE = "web_strike";
	public static final String WEB_THROW = "web_throw";
	public static final String IMPACT_WEB = "impact_web";

	public static final int CD_WEB_STRIKE = 3 * 20;
	public static final int CD_WEB_THROW = 3 * 20;
	public static final int CD_IMPACT_WEB = 20;

	public static final float COST_WEB_STRIKE = 8.0f;
	public static final float COST_WEB_THROW = 10.0f;
	public static final float COST_IMPACT_WEB = 12.0f;

	public static final float STRIKE_DAMAGE = 15.0f;
	public static final double STRIKE_RANGE = 40.0;
	private static final double STRIKE_SPEED = 1.7;
	private static final int STRIKE_MAX_TICKS = 40;

	private static final double THROW_RANGE = 30.0;
	private static final int THROW_MAX_TICKS = 5 * 20;
	private static final double THROW_SPEED = 2.4;

	public static final float IMPACT_DAMAGE = 10.0f;
	public static final double IMPACT_KNOCKBACK = 1.6;
	public static final double IMPACT_SPEED = 2.2;
	public static final int IMPACT_LIFE_TICKS = 30;
	/** How long a target that hits a wall stays pinned to it: six seconds. */
	public static final int IMPACT_PIN_TICKS = 6 * 20;
	/** How long after the hit a knocked-back target is watched for a wall collision. */
	private static final int PIN_WATCH_TICKS = 14;

	/** How long a strand takes to phase out once it lets go (v0.12.20: five seconds). */
	public static final int STRAND_FADE_TICKS = 5 * 20;

	// strand slots (per player; a newer strand with the same slot replaces the old one)
	public static final int SLOT_ZIP = 1;
	public static final int SLOT_STRIKE = 2;
	public static final int SLOT_THROW = 3;
	public static final int SLOT_DISARM = 4;

	private record Strike(int targetId, long startedAt) {
	}

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

	/** A shift-zip in progress: hauled toward {@code target} every tick until it arrives (v0.12.21). */
	private static final class ZipPull {
		final Vec3 target;
		final long startedAt;
		double lastDist = Double.MAX_VALUE;
		int stalled;

		ZipPull(Vec3 target, long startedAt) {
			this.target = target;
			this.startedAt = startedAt;
		}
	}

	private static final Map<UUID, ZipPull> ZIPS = new ConcurrentHashMap<>();

	private record Watch(int targetId, long until, Vec3 dir) {
	}

	private static final Map<UUID, Strike> STRIKES = new ConcurrentHashMap<>();
	private static final Map<UUID, Throw> THROWS = new ConcurrentHashMap<>();
	private static final Map<UUID, List<Watch>> WATCHES = new ConcurrentHashMap<>();

	private SpiderCombat() {
	}

	public static void clearSessionState() {
		STRIKES.clear();
		THROWS.clear();
		WATCHES.clear();
		ZIPS.clear();
	}

	/** Death / relog / dimension change / mode switch: drop everything this player had going. */
	public static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		if (THROWS.remove(id) != null) {
			sendStrand(player, SLOT_THROW, null, player.position(), 0, 0);
		}
		if (STRIKES.remove(id) != null) {
			sendStrand(player, SLOT_STRIKE, null, player.position(), 0, 0);
		}
		WATCHES.remove(id);
		ZIPS.remove(id);
	}

	public static boolean inCombatMode(ServerPlayer player) {
		return SpiderMan.state(player).combatMode;
	}

	// ---------------- N -- mode toggle ----------------

	public static void toggleMode(ServerPlayer player) {
		if (!SpiderMan.hasPower(player)) {
			return;
		}
		clear(player);
		SpiderSwing.detach(player, false);
		SpiderManState c = SpiderMan.state(player).copy();
		c.combatMode = !c.combatMode;
		SpiderMan.save(player, c);
		AbilityHelpers.sound(player, SoundEvents.SPIDER_AMBIENT, 0.5f, c.combatMode ? 0.7f : 1.4f);
		player.displayClientMessage(Component.translatable(c.combatMode
				? "message.projecthero.spider_man.mode_combat" : "message.projecthero.spider_man.mode_traversal"), true);
	}

	// ---------------- strands ----------------

	/** Draw (or replace) a web strand from {@code owner}'s hand; seen by everyone tracking them. */
	public static void sendStrand(ServerPlayer owner, int slot, LivingEntity target, Vec3 pos, int hold, int fade) {
		SpiderWebStrandPayload payload = new SpiderWebStrandPayload(owner.getId(), slot,
				target == null ? -1 : target.getId(), pos.x, pos.y, pos.z, hold, fade, true);
		ServerPlayNetworking.send(owner, payload);
		for (ServerPlayer p : PlayerLookup.tracking(owner)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	private static Vec3 center(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static LivingEntity pickTarget(ServerPlayer player, double range) {
		LivingEntity t = AbilityHelpers.raycastEntity(player, range);
		return t != null ? t : SpiderAbilities.nearestLivingInAim(player, range);
	}

	// ---------------- R -- Web Strike ----------------

	public static void webStrike(ServerPlayer player) {
		if (STRIKES.containsKey(player.getUUID())) {
			return;
		}
		if (!SpiderAbilities.gate(player, WEB_STRIKE, COST_WEB_STRIKE)) {
			return;
		}
		LivingEntity target = pickTarget(player, STRIKE_RANGE);
		if (target == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_target"), true);
			return;
		}
		SpiderSwing.detach(player, false);
		STRIKES.put(player.getUUID(), new Strike(target.getId(), player.level().getGameTime()));
		sendStrand(player, SLOT_STRIKE, target, center(target), STRIKE_MAX_TICKS, 0);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.8f, 1.3f);
		SpiderAbilities.fired(player, WEB_STRIKE, COST_WEB_STRIKE, CD_WEB_STRIKE);
	}

	private static void tickStrike(ServerPlayer player, Strike strike) {
		ServerLevel level = (ServerLevel) player.level();
		Entity e = level.getEntity(strike.targetId());
		if (!(e instanceof LivingEntity target) || !target.isAlive()
				|| level.getGameTime() - strike.startedAt() > STRIKE_MAX_TICKS) {
			endStrike(player, e instanceof LivingEntity le ? le.position() : player.position());
			return;
		}
		Vec3 to = center(target);
		Vec3 me = player.position().add(0, player.getBbHeight() * 0.5, 0);
		Vec3 delta = to.subtract(me);
		double dist = delta.length();
		if (dist <= 2.6) {
			AbilityHelpers.hurtBurst(player, target, STRIKE_DAMAGE);
			AbilityHelpers.knockbackFrom(target, player.position(), 1.5);
			target.setDeltaMovement(target.getDeltaMovement().add(0, 0.3, 0));
			target.hurtMarked = true;
			AbilityHelpers.launchSelf(player, player.getDeltaMovement().scale(0.15));
			level.sendParticles(ParticleTypes.CRIT, to.x, to.y, to.z, 14, 0.3, 0.3, 0.3, 0.2);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, to.x, to.y, to.z, 10, 0.3, 0.3, 0.3, 0.05);
			AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.9f);
			endStrike(player, to);
			return;
		}
		AbilityHelpers.launchSelf(player, delta.normalize().scale(STRIKE_SPEED));
	}

	private static void endStrike(ServerPlayer player, Vec3 lastPos) {
		STRIKES.remove(player.getUUID());
		sendStrand(player, SLOT_STRIKE, null, lastPos, 0, STRAND_FADE_TICKS);
	}

	// ---------------- G -- Web Zip: disarm ----------------

	/**
	 * Combat-mode Web Zip aimed at a mob or player holding something: the webbing rips it out of their hands
	 * and it flies to the caster. Returns false (nothing spent) when there is no armed target, so the caller
	 * falls back to the ordinary zip.
	 */
	public static boolean tryDisarm(ServerPlayer player) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, SpiderAbilities.ZIP_RANGE);
		if (target == null) {
			return false;
		}
		ItemStack main = target.getItemBySlot(EquipmentSlot.MAINHAND);
		ItemStack off = target.getItemBySlot(EquipmentSlot.OFFHAND);
		if (main.isEmpty() && off.isEmpty()) {
			return false;
		}
		if (!SpiderAbilities.gate(player, SpiderAbilities.WEB_ZIP, SpiderWebReserve.COST_WEB_ZIP)) {
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
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.8f, 1.6f);
		SpiderAbilities.fired(player, SpiderAbilities.WEB_ZIP, SpiderWebReserve.COST_WEB_ZIP, SpiderAbilities.CD_WEB_ZIP);
		return true;
	}

	// ---------------- X -- Web-Throw ----------------

	public static void beginThrow(ServerPlayer player) {
		if (THROWS.containsKey(player.getUUID())) {
			return;
		}
		if (!SpiderAbilities.gate(player, WEB_THROW, COST_WEB_THROW)) {
			return;
		}
		LivingEntity target = pickTarget(player, THROW_RANGE);
		if (target == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_target"), true);
			return;
		}
		if (SpiderWebs.isBoss(target) || target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1.0) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.too_heavy"), true);
			return;
		}
		SpiderWebReserve.spend(player, COST_WEB_THROW, true);
		Vec3 rel = target.position().subtract(player.position());
		THROWS.put(player.getUUID(), new Throw(target.getId(), player.level().getGameTime(), Math.atan2(rel.z, rel.x)));
		sendStrand(player, SLOT_THROW, target, center(target), THROW_MAX_TICKS + 10, 0);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.8f, 1.0f);
	}

	/** X released: hurl whatever is on the end of the web along the look direction. */
	public static void releaseThrow(ServerPlayer player) {
		Throw t = THROWS.get(player.getUUID());
		if (t != null) {
			finishThrow(player, t, true);
		}
	}

	private static void finishThrow(ServerPlayer player, Throw t, boolean hurl) {
		THROWS.remove(player.getUUID());
		Entity e = player.level().getEntity(t.targetId);
		Vec3 last = player.position();
		if (e instanceof LivingEntity target && target.isAlive()) {
			last = center(target);
			if (hurl) {
				Vec3 look = player.getLookAngle();
				target.setDeltaMovement(look.scale(THROW_SPEED).add(0, 0.2, 0));
				target.hurtMarked = true;
				target.fallDistance = 0.0f;
				AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
			}
		}
		sendStrand(player, SLOT_THROW, null, last, 0, STRAND_FADE_TICKS);
		SpiderMan.triggerCooldown(player, WEB_THROW, CD_WEB_THROW);
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

	// ---------------- Z -- Impact Web ----------------

	public static void impactWeb(ServerPlayer player) {
		if (!SpiderAbilities.gate(player, IMPACT_WEB, COST_IMPACT_WEB)) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 look = player.getLookAngle();
		ImpactWebEntity web = new ImpactWebEntity(level, player, look.scale(IMPACT_SPEED));
		Vec3 start = player.getEyePosition().add(look.scale(0.9)).subtract(0, 0.15, 0);
		web.setPos(start.x, start.y, start.z);
		level.addFreshEntity(web);
		AbilityHelpers.sound(player, SoundEvents.SLIME_SQUISH, 0.9f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.CROSSBOW_SHOOT, 0.6f, 0.6f);
		SpiderAbilities.fired(player, IMPACT_WEB, COST_IMPACT_WEB, CD_IMPACT_WEB);
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
			SpiderWebs.cocoonFor(player, target, IMPACT_PIN_TICKS);
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtMarked = true;
			Vec3 c = center(target);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 22, 0.4, 0.5, 0.4, 0.04);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.COBWEB_PLACE,
					net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.8f);
			return true;
		});
	}

	// ---------------- shift + Web Zip ----------------

	public static void beginZipPull(ServerPlayer player, Vec3 target) {
		ZIPS.put(player.getUUID(), new ZipPull(target, player.level().getGameTime()));
	}

	private static void tickZip(ServerPlayer player, ZipPull zip) {
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

	// ---------------- tick ----------------

	/** Per-owner server tick, called from {@code SpiderManAbilityManager.serverTick}. */
	public static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		ZipPull zip = ZIPS.get(id);
		if (zip != null) {
			tickZip(player, zip);
		}
		Strike strike = STRIKES.get(id);
		if (strike != null) {
			tickStrike(player, strike);
		}
		Throw t = THROWS.get(id);
		if (t != null) {
			tickThrow(player, t);
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
