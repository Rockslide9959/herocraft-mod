package com.projecthero.mod.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.network.SpiderReflexFxPayload;
import com.projecthero.mod.network.SpiderReflexPayload;
import com.projecthero.mod.spider.web.WebStructures;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 (user-approved): the <b>Inverted Takedown</b>. Hanging upside down on his line (the Z hang), above or near a
 * mob that has not noticed him -- not hunting him, not a boss, not too heavy -- within about 6 blocks below and in
 * front, a <b>left-click</b> drops him down the line: he snatches it and hauls it back up to hang on the line beside
 * him, cocooned ({@link SpiderWrap}). From one of his own webs (tightrope / woven web) the cocoon is the permanent web
 * cocoon; from a ceiling it is timed ({@link #TIMED_WRAP_TICKS}).
 *
 * <ul>
 * <li>Silent: no damage is dealt, so {@link SpiderStealth} never exposes him -- he stays hidden.</li>
 * <li>8 s cooldown ({@link #COOLDOWN}); no webbing cost.</li>
 * <li>The server picks the valid target every few ticks while he hangs and tells only his client
 * ({@link SpiderReflexFxPayload#TAKEDOWN_TARGET}), which draws the "Takedown" prompt and claims the left-click (it
 * never also punches).</li>
 * <li>The drop-and-snatch is {@link SpiderAnim#TAKEDOWN}, posed by {@code client.spider.SpiderHang}.</li>
 * </ul>
 */
public final class SpiderTakedown {
	public static final String TAKEDOWN = "inverted_takedown";
	public static final int COOLDOWN = 160;
	/** A ceiling-hang takedown's cocoon (a web strand's is permanent). */
	public static final int TIMED_WRAP_TICKS = 500;
	/** Reach: how far below his head, how far out, and the straight-line limit. */
	public static final double RANGE_DOWN = 7.0;
	public static final double RANGE_OUT = 5.0;
	public static final double RANGE = 7.5;
	/** The snatch: the hand reaches it at {@link #GRAB_AT}, it is up beside him by {@link #UP_AT}. */
	static final int GRAB_AT = 4;
	static final int UP_AT = 12;

	private record Snatch(int targetId, long start, Vec3 grabbedAt, Vec3 dest, boolean permanent) {
	}

	private static final Map<UUID, Snatch> SNATCHES = new ConcurrentHashMap<>();
	/** Player -> the target id last sent to his client (-1 none). */
	private static final Map<UUID, Integer> SENT = new ConcurrentHashMap<>();

	private SpiderTakedown() {
	}

	public static void initialize() {
		// the payload types are registered here (this runs before ModNetworking registers its receivers)
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(SpiderReflexPayload.TYPE, SpiderReflexPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(SpiderReflexFxPayload.TYPE, SpiderReflexFxPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SpiderReflexPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (com.projecthero.mod.hero.PowerSuppression.refuse(player)) { // v0.16.7: Erased
				return;
			}
			if (payload.action() == SpiderReflexPayload.TAKEDOWN) {
				request(player);
			} else if (payload.action() == SpiderReflexPayload.DODGE) {
				SpiderPerfectDodge.arm(player);
			}
		});
	}

	public static void clearSessionState() {
		SNATCHES.clear();
		SENT.clear();
	}

	public static void clear(ServerPlayer player) {
		SNATCHES.remove(player.getUUID());
		SENT.remove(player.getUUID());
	}

	/** True while a snatch is hauling something up (tests). */
	public static boolean snatching(ServerPlayer player) {
		return SNATCHES.containsKey(player.getUUID());
	}

	// ---------------- targeting ----------------

	/** Has {@code mob} not noticed him (not hunting him, he has not hurt it)? */
	public static boolean unaware(Mob mob, ServerPlayer player) {
		return mob.getTarget() != player && mob.getLastHurtByMob() != player;
	}

	/**
	 * Can {@code e} be taken down from a hang with his head at {@code head} facing {@code yaw}: an unaware, harmable,
	 * light-enough mob (no boss), not already cocooned, about 6 blocks below and in front, in plain sight.
	 */
	public static boolean validTarget(ServerPlayer player, LivingEntity e, Vec3 head, float yaw) {
		if (!(e instanceof Mob mob) || !e.isAlive() || e == player || e.isPassenger() || e.isVehicle()
				|| !HeroTargets.canHarm(player, e) || com.projecthero.mod.squad.Squads.areAllies(player, e)
				|| SpiderWrap.isWrapped(e) || !unaware(mob, player)
				|| SpiderWebs.isBoss(e) || SpiderWebs.tooHeavyToThrow(e) || SpiderWebs.ignoresWebs(e)) {
			return false;
		}
		// below him: its top no higher than a little over his head, its feet no more than RANGE_DOWN under it
		double top = e.getY() + e.getBbHeight();
		if (top > head.y + 0.6 || e.getY() < head.y - RANGE_DOWN) {
			return false;
		}
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
		Vec3 flat = new Vec3(c.x - head.x, 0, c.z - head.z);
		double out = flat.length();
		if (out > RANGE_OUT || c.distanceTo(head) > RANGE) {
			return false;
		}
		// in front (or close to straight below him)
		if (out > 1.4) {
			Vec3 fwd = Vec3.directionFromRotation(0.0f, yaw);
			if (fwd.dot(flat.scale(1.0 / out)) < 0.25) {
				return false;
			}
		}
		HitResult hit = player.level().clip(new ClipContext(head, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(c) < 0.36;
	}

	/** His head while hanging upside down: the bottom of his (standing) box. */
	static Vec3 head(ServerPlayer player) {
		return new Vec3(player.getX(), player.getY() + 0.1, player.getZ());
	}

	/** The best takedown target below him right now (nearest to where he faces), or null. */
	public static LivingEntity findTarget(ServerPlayer player) {
		Vec3 head = head(player);
		float yaw = player.getYRot();
		AABB box = new AABB(head.x - RANGE_OUT - 1, head.y - RANGE_DOWN - 1, head.z - RANGE_OUT - 1,
				head.x + RANGE_OUT + 1, head.y + 1.0, head.z + RANGE_OUT + 1);
		Vec3 fwd = Vec3.directionFromRotation(0.0f, yaw);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Mob m : player.level().getEntitiesOfClass(Mob.class, box, x -> validTarget(player, x, head, yaw))) {
			Vec3 to = m.position().subtract(head);
			double score = to.length() - 1.5 * fwd.dot(new Vec3(to.x, 0, to.z).normalize());
			if (score < bestScore) {
				bestScore = score;
				best = m;
			}
		}
		return best;
	}

	/** May he take down right now -- Spider-Man, hanging upside down, not mid-snatch, off cooldown? */
	private static boolean ready(ServerPlayer player) {
		return SpiderMan.hasPower(player) && SpiderMoves.hangingUpsideDown(player) && SpiderMoves.hangAnchor(player) != null
				&& !SNATCHES.containsKey(player.getUUID()) && !player.isSpectator();
	}

	// ---------------- the move ----------------

	/** Left-click while hanging (from his client): take down the mob under him. Returns true when it started. */
	public static boolean request(ServerPlayer player) {
		if (!ready(player)) {
			return false;
		}
		LivingEntity target = findTarget(player);
		if (target == null) {
			return false;
		}
		if (!SpiderAbilities.gate(player, TAKEDOWN, 0.0f)) {
			return false;
		}
		return start(player, target, SpiderMoves.hangAnchor(player), SpiderMoves.hangFromWeb(player));
	}

	/**
	 * Start the snatch on {@code target} for a hang tied on at {@code anchor} ({@code fromWeb}: one of his webs, so the
	 * cocoon is permanent). Public for the gametests, which have no real hang line.
	 */
	public static boolean start(ServerPlayer player, LivingEntity target, Vec3 anchor, boolean fromWeb) {
		if (!(player.level() instanceof ServerLevel level) || SNATCHES.containsKey(player.getUUID())) {
			return false;
		}
		Vec3 dest = null;
		boolean permanent = false;
		Vec3 side = Vec3.directionFromRotation(0.0f, player.getYRot() + 90.0f);
		if (fromWeb) {
			// on the strand beside him, the line through its chest (like Web Snag), so the web holds the cocoon
			for (Vec3 s : new Vec3[] {side, side.reverse()}) {
				Vec3 onWeb = WebStructures.nearestPoint(player, anchor.add(s.scale(1.3)), 2.5);
				if (onWeb != null && onWeb.distanceToSqr(anchor) > 0.36) {
					Vec3 d = onWeb.subtract(0, target.getBbHeight() * 0.6, 0);
					if (fits(target, d)) {
						dest = d;
						permanent = true;
						break;
					}
				}
			}
		}
		if (dest == null) {
			// hung beside him from the ceiling / anchor: its top just under it, a body-width to the side
			double w = target.getBbWidth() * 0.5 + 0.75;
			Vec3 fwd = Vec3.directionFromRotation(0.0f, player.getYRot());
			for (Vec3 s : new Vec3[] {side, side.reverse(), fwd, fwd.reverse()}) {
				Vec3 d = new Vec3(anchor.x + s.x * w, anchor.y - target.getBbHeight() - 0.05, anchor.z + s.z * w);
				if (fits(target, d)) {
					dest = d;
					break;
				}
			}
		}
		if (dest == null) {
			dest = target.position(); // nowhere to hang it: cocooned where it stands
		}
		WebStructures.tearFree(target);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		SNATCHES.put(player.getUUID(), new Snatch(target.getId(), level.getGameTime(), target.position(), dest, permanent));
		SpiderMoves.anim(player, SpiderAnim.TAKEDOWN);
		// the line from his hands to it while he hauls it up (a quiet move: no thwip)
		SpiderCombat.sendStrand(player, SpiderCombat.SLOT_GRAB, target, SpiderCombat.center(target), UP_AT + 2, 6);
		SpiderMan.triggerCooldown(player, TAKEDOWN, COOLDOWN);
		sendTarget(player, -1);
		return true;
	}

	private static boolean fits(LivingEntity e, Vec3 at) {
		AABB box = e.getDimensions(e.getPose()).makeBoundingBox(at);
		return e.level().noCollision(e, box.deflate(0.02));
	}

	// ---------------- per tick ----------------

	public static void serverTick(ServerPlayer player) {
		Snatch sn = SNATCHES.get(player.getUUID());
		if (sn != null) {
			tickSnatch(player, sn);
		}
		// the HUD prompt: who (if anyone) a left-click would take down -- only re-sent when it changes
		if (player.tickCount % 4 == 0) {
			int id = -1;
			if (ready(player) && SpiderMan.abilityReady(player, TAKEDOWN)) {
				LivingEntity t = findTarget(player);
				id = t == null ? -1 : t.getId();
			}
			sendTarget(player, id);
		}
	}

	private static void sendTarget(ServerPlayer player, int id) {
		Integer last = SENT.get(player.getUUID());
		if ((last == null ? -1 : last) == id) {
			return;
		}
		SENT.put(player.getUUID(), id);
		if (ServerPlayNetworking.canSend(player, SpiderReflexFxPayload.TYPE)) {
			ServerPlayNetworking.send(player, new SpiderReflexFxPayload(SpiderReflexFxPayload.TAKEDOWN_TARGET, id, 0f));
		}
	}

	/** Advance a snatch: it waits while his hand drops, then is hauled up the line beside him and cocooned. */
	static void tickSnatch(ServerPlayer player, Snatch sn) {
		ServerLevel level = (ServerLevel) player.level();
		Entity e = level.getEntity(sn.targetId());
		long age = level.getGameTime() - sn.start();
		if (!(e instanceof LivingEntity target) || !target.isAlive() || age > UP_AT + 20) {
			SNATCHES.remove(player.getUUID());
			return;
		}
		if (target instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		if (age < GRAB_AT) {
			// his hand is still on the way down: it stays where it is (it has not noticed a thing)
			target.setDeltaMovement(Vec3.ZERO);
			target.hurtMarked = true;
			return;
		}
		if (age == GRAB_AT) {
			Vec3 c = SpiderCombat.center(target);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 10, target.getBbWidth() * 0.4,
					target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.02);
		}
		float t = Mth.clamp((age - GRAB_AT) / (float) (UP_AT - GRAB_AT), 0f, 1f);
		// eased: a hard yank that settles in beside him
		float k = 1f - (1f - t) * (1f - t);
		Vec3 at = sn.grabbedAt().lerp(sn.dest(), k);
		target.teleportTo(at.x, at.y, at.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		target.hurtMarked = true;
		if (t >= 1f) {
			SNATCHES.remove(player.getUUID());
			if (sn.permanent()) {
				SpiderWrap.wrap(target);
			} else {
				SpiderWrap.wrapFor(target, TIMED_WRAP_TICKS);
			}
		}
	}
}
