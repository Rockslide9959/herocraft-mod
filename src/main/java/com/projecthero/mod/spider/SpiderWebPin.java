package com.projecthero.mod.spider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.spider.data.SpiderMoveState;
import com.projecthero.mod.spider.web.WebStructures;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 (user): <b>Web Pin</b> -- Spider-Man's Shift+V (the Black Suit keeps Ink Shroud there). Two presses:
 *
 * <ol>
 *   <li><b>Mark.</b> Shift+V on a living thing within {@link #MARK_RANGE} (not him, not his squad, not a boss, not too
 *       heavy to throw, not web-immune) attaches a web line from his hand to it. The owner's HUD shows the
 *       "aim at a surface" cue and the target is outlined for him alone (synced through
 *       {@link SpiderMoveState#pinTarget}). The mark lasts {@link #MARK_TICKS}, or until he presses Shift+V on nothing
 *       usable, or the target dies or gets more than {@link #KEEP_RANGE} away.</li>
 *   <li><b>Pin.</b> Shift+V again on one of his woven webs / tightropes or on any solid block face within
 *       {@link #SURFACE_RANGE}: the target is reeled through the air (no gravity, the line from his hand) and set against
 *       it -- back to a wall, standing on a floor, hanging under a ceiling, or with the strand through its chest -- and
 *       cocooned there for {@link #WRAP_TICKS} (25 s, {@link SpiderWrap#wrapFor}). Blocks in the way do not stop it (v0.16.2): the line drags it through to the
 *       obstruction, cocooned. Aiming at another living thing instead re-marks that one.</li>
 * </ol>
 *
 * Web Cocoon's webbing cost and a 3-second cooldown, both on the pin. Sessions are static maps dropped by
 * {@link #clearSessionState} (via {@code SpiderMoves.clearSessionState}) and {@link #clear}.
 */
public final class SpiderWebPin {
	public static final String WEB_PIN = "web_pin";
	public static final double MARK_RANGE = 24.0;
	public static final double KEEP_RANGE = 30.0;
	public static final double SURFACE_RANGE = 30.0;
	public static final int MARK_TICKS = 6 * 20;
	public static final int COOLDOWN = 3 * 20;
	public static final int WRAP_TICKS = 25 * 20;
	public static final double REEL_SPEED = 1.35;
	public static final int REEL_MAX_TICKS = 40;
	/** v0.16.2: how close (blocks) to the aim a strand of his web may be for the pin to take it. */
	public static final double WEB_AIM_RADIUS = 1.0;
	/** The web-line slot (clear of {@link SpiderCombat}'s fixed slots and the Blossom range from 20). */
	public static final int SLOT_PIN = 12;

	/** Where a pinned target goes. */
	public enum Surface {
		WALL, FLOOR, CEILING, WEB
	}

	private record Mark(int targetId, long until) {
	}

	private record Reel(int targetId, long start, Vec3 dest, Surface surface, float yaw, boolean hadNoGravity) {
	}

	private static final Map<UUID, Mark> MARKS = new ConcurrentHashMap<>();
	private static final Map<UUID, Reel> REELS = new ConcurrentHashMap<>();

	private SpiderWebPin() {
	}

	public static void clearSessionState() {
		MARKS.clear();
		REELS.clear();
	}

	/** Death / relog / dimension change / power loss: drop the mark and let a reeled target fall. */
	public static void clear(ServerPlayer player) {
		MARKS.remove(player.getUUID());
		setPinState(player, 0, 0L);
		Reel r = REELS.remove(player.getUUID());
		if (r != null && player.level().getEntity(r.targetId()) instanceof LivingEntity t) {
			t.setNoGravity(r.hadNoGravity());
		}
	}

	/** The entity he has marked right now (tests), or null. */
	public static LivingEntity marked(ServerPlayer player) {
		Mark m = MARKS.get(player.getUUID());
		return m != null && player.level().getEntity(m.targetId()) instanceof LivingEntity le ? le : null;
	}

	/** True while a pinned target is still being reeled in (tests). */
	public static boolean reeling(ServerPlayer player) {
		return REELS.containsKey(player.getUUID());
	}

	/** The kind of surface the current reel is heading for (tests), or null. */
	public static Surface reelSurface(ServerPlayer player) {
		Reel r = REELS.get(player.getUUID());
		return r == null ? null : r.surface();
	}

	/** Whether {@code e} can be marked by {@code player}. */
	public static boolean markable(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && HeroTargets.canHarm(player, e)
				&& !com.projecthero.mod.squad.Squads.areAllies(player, e);
	}

	private static boolean tooBig(LivingEntity e) {
		return SpiderWebs.isBoss(e) || SpiderWebs.tooHeavyToThrow(e) || SpiderWebs.ignoresWebs(e);
	}

	// ---------------- the key ----------------

	/** Shift+V (not the Black Suit): mark, re-mark, pin, or cancel. */
	public static void press(ServerPlayer player) {
		if (REELS.containsKey(player.getUUID())) {
			return; // one reel at a time
		}
		Mark mark = MARKS.get(player.getUUID());
		LivingEntity current = mark == null ? null : marked(player);
		if (current == null && snag(player)) {
			return; // v0.16.2 (user): standing on his tightrope / woven web, Shift+V is still the one-press Web Snag
		}
		if (current == null) {
			MARKS.remove(player.getUUID());
			LivingEntity target = SpiderCombat.pickTarget(player, MARK_RANGE);
			if (target == null || !markable(player, target)) {
				player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_target"), true);
				return;
			}
			tryMark(player, target);
			return;
		}
		// step 2: another living thing on the aim re-marks; otherwise the surface he aims at
		Vec3 eye = player.getEyePosition();
		BlockHitResult block = AbilityHelpers.raycastBlock(player, SURFACE_RANGE);
		double blockDist = block.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : block.getLocation().distanceTo(eye);
		LivingEntity other = otherOnAim(player, current, eye, Math.min(MARK_RANGE, blockDist));
		if (other != null) {
			if (markable(player, other)) {
				tryMark(player, other);
			} else {
				player.displayClientMessage(Component.translatable("message.projecthero.spider_man.no_target"), true);
			}
			return;
		}
		// v0.16.2 (user): a strand near the aim counts (1 block), and wins over a block just behind it
		WebStructures.StrandHit strand = WebStructures.raycastStrand(player, SURFACE_RANGE, WEB_AIM_RADIUS);
		Vec3 dest;
		Surface surface;
		float yaw = current.getYRot();
		if (strand != null && strand.distance() <= blockDist + WEB_AIM_RADIUS) {
			surface = Surface.WEB;
			dest = strand.point().subtract(0, current.getBbHeight() * 0.6, 0);
		} else if (block.getType() != HitResult.Type.MISS) {
			Direction face = block.getDirection();
			Vec3 hit = block.getLocation();
			double h = current.getBbHeight();
			double hw = current.getBbWidth() * 0.5;
			if (face == Direction.UP) {
				surface = Surface.FLOOR;
				dest = hit.add(0, 0.01, 0);
			} else if (face == Direction.DOWN) {
				surface = Surface.CEILING;
				dest = hit.subtract(0, h + 0.02, 0);
			} else {
				surface = Surface.WALL;
				Vec3 n = new Vec3(face.getStepX(), 0, face.getStepZ());
				dest = new Vec3(hit.x, hit.y - h * 0.5, hit.z).add(n.scale(hw + 0.05));
				dest = clearOfBlocks(current, dest);
				yaw = face.toYRot(); // its back to the wall, facing out
			}
		} else {
			cancel(player, true); // nothing usable on the aim: the mark is dropped
			return;
		}
		if (!SpiderAbilities.gate(player, WEB_PIN, cost())) {
			return; // the mark stays; try again when ready
		}
		MARKS.remove(player.getUUID());
		setPinState(player, 0, 0L);
		SpiderWrap.release(current, false); // re-pinning something already wrapped: it comes along
		WebStructures.tearFree(current);
		REELS.put(player.getUUID(), new Reel(current.getId(), player.level().getGameTime(), dest, surface, yaw,
				current.isNoGravity()));
		current.setNoGravity(true);
		SpiderCombat.sendStrand(player, SLOT_PIN, current, SpiderCombat.center(current), REEL_MAX_TICKS + 10, 0);
		SpiderMoves.anim(player, SpiderAnim.COCOON);
		SpiderMoves.thwip(player, 0.8f);
		SpiderAbilities.fired(player, WEB_PIN, cost(), COOLDOWN);
	}

	private static float cost() {
		return SpiderMoves.cfg(SpiderMoves.WEB_COCOON).cost;
	}

	/** A living thing other than the marked one on the aim, nearer than the surface behind it. */
	private static LivingEntity otherOnAim(ServerPlayer player, LivingEntity marked, Vec3 eye, double maxDist) {
		if (maxDist <= 0.0) {
			return null;
		}
		Vec3 end = eye.add(player.getLookAngle().scale(maxDist));
		AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(maxDist)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
				e -> e != player && e != marked && e.isPickable() && e instanceof LivingEntity
						&& !(e instanceof net.minecraft.world.entity.decoration.ArmorStand),
				maxDist * maxDist);
		return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
	}

	private static void tryMark(ServerPlayer player, LivingEntity target) {
		if (tooBig(target)) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.too_heavy"), true);
			return;
		}
		if (!SpiderAbilities.gate(player, WEB_PIN, cost())) {
			return; // on cooldown / out of webbing: nothing is marked
		}
		long until = player.level().getGameTime() + MARK_TICKS;
		MARKS.put(player.getUUID(), new Mark(target.getId(), until));
		setPinState(player, target.getId(), until);
		SpiderCombat.sendStrand(player, SLOT_PIN, target, SpiderCombat.center(target), MARK_TICKS + 10, 0);
		SpiderMoves.anim(player, SpiderAnim.THWIP_BOTH);
		SpiderMoves.thwip(player, 1.1f);
		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.pin_marked"), true);
	}

	/** Drop the mark (the line fades). */
	private static void cancel(ServerPlayer player, boolean say) {
		Mark m = MARKS.remove(player.getUUID());
		setPinState(player, 0, 0L);
		if (m != null) {
			Entity e = player.level().getEntity(m.targetId());
			Vec3 at = e instanceof LivingEntity le ? SpiderCombat.center(le) : player.position();
			SpiderCombat.sendStrand(player, SLOT_PIN, -1, at, 0, 6);
		}
		if (say) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.pin_cancelled"), true);
		}
	}

	private static void setPinState(ServerPlayer player, int target, long until) {
		SpiderMoveState s = SpiderMoves.state(player);
		if (s.pinTarget == target && s.pinUntil == until) {
			return;
		}
		SpiderMoveState c = s.copy();
		c.pinTarget = target;
		c.pinUntil = until;
		player.setAttached(ModAttachments.SPIDER_MOVE_STATE, c);
	}

	/**
	 * v0.16.2 (user): Web Snag is back -- standing on one of his tightropes / woven webs, Shift+V on a creature reels it
	 * straight onto the strand beside him and cocoons it there (one press, Web Pin's cost and cooldown). False when he
	 * is not on a web or nothing valid is aimed at (then the normal Web Pin mark runs).
	 */
	private static boolean snag(ServerPlayer player) {
		WebStructures.StrandHit under = WebStructures.strandAtFeet(player);
		if (under == null) {
			return false;
		}
		LivingEntity target = SpiderCombat.pickTarget(player, MARK_RANGE);
		if (target == null || !markable(player, target)) {
			return false;
		}
		if (tooBig(target)) {
			player.displayClientMessage(Component.translatable("message.projecthero.spider_man.too_heavy"), true);
			return true;
		}
		if (!SpiderAbilities.gate(player, WEB_PIN, cost())) {
			return true;
		}
		Vec3 toward = target.position().subtract(player.position());
		Vec3 flat = new Vec3(toward.x, 0, toward.z);
		flat = flat.lengthSqr() < 1.0e-4 ? player.getLookAngle() : flat.normalize();
		Vec3 onWeb = WebStructures.nearestPoint(player, under.point().add(flat.scale(1.6)), 3.0);
		if (onWeb == null) {
			onWeb = under.point();
		}
		Vec3 dest = onWeb.subtract(0, target.getBbHeight() * 0.6, 0);
		SpiderWrap.release(target, false);
		WebStructures.tearFree(target);
		REELS.put(player.getUUID(), new Reel(target.getId(), player.level().getGameTime(), dest, Surface.WEB, target.getYRot(),
				target.isNoGravity()));
		target.setNoGravity(true);
		SpiderCombat.sendStrand(player, SLOT_PIN, target, SpiderCombat.center(target), REEL_MAX_TICKS + 10, 0);
		SpiderMoves.anim(player, SpiderAnim.THWIP_BOTH);
		SpiderMoves.thwip(player, 0.9f);
		SpiderAbilities.fired(player, WEB_PIN, cost(), COOLDOWN);
		return true;
	}

	/** Nudge a wall spot up (up to two blocks) until the body fits there -- feet off the ground if needed. */
	private static Vec3 clearOfBlocks(LivingEntity e, Vec3 dest) {
		for (int i = 0; i <= 8; i++) {
			Vec3 at = dest.add(0, i * 0.25, 0);
			if (e.level().noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(at))) {
				return at;
			}
		}
		return dest;
	}

	// ---------------- per-tick ----------------

	/** Per-player server tick (from {@code SpiderMoves.tick}). */
	public static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		Mark m = MARKS.get(id);
		if (m != null) {
			Entity e = player.level().getEntity(m.targetId());
			if (!(e instanceof LivingEntity t) || !t.isAlive() || player.level().getGameTime() >= m.until()
					|| t.distanceTo(player) > KEEP_RANGE) {
				cancel(player, false);
			}
		}
		Reel r = REELS.get(id);
		if (r != null) {
			tickReel(player, r);
		}
	}

	private static void tickReel(ServerPlayer player, Reel r) {
		ServerLevel level = (ServerLevel) player.level();
		Entity e = level.getEntity(r.targetId());
		if (!(e instanceof LivingEntity target) || !target.isAlive()) {
			REELS.remove(player.getUUID());
			SpiderCombat.sendStrand(player, SLOT_PIN, -1, player.position(), 0, 6);
			if (e != null) {
				e.setNoGravity(r.hadNoGravity());
			}
			return;
		}
		Vec3 delta = r.dest().subtract(target.position());
		double dist = delta.length();
		long age = level.getGameTime() - r.start();
		if (dist <= REEL_SPEED) {
			target.teleportTo(r.dest().x, r.dest().y, r.dest().z);
			land(player, target, r);
			return;
		}
		Vec3 step = delta.scale(REEL_SPEED / dist);
		// v0.16.2 (user): nothing in the way stops it -- the line drags it through to the surface he picked
		if (age >= REEL_MAX_TICKS) {
			target.teleportTo(r.dest().x, r.dest().y, r.dest().z);
			land(player, target, r);
			return;
		}
		// reeled straight in along the line -- no gravity fighting it
		target.teleportTo(target.getX() + step.x, target.getY() + step.y, target.getZ() + step.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		target.hurtMarked = true;
	}

	private static void land(ServerPlayer player, LivingEntity target, Reel r) {
		REELS.remove(player.getUUID());
		target.setNoGravity(r.hadNoGravity());
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		if (r.surface() == Surface.WALL) {
			target.setYRot(r.yaw());
			target.setYHeadRot(r.yaw());
			target.setYBodyRot(r.yaw());
		}
		SpiderWrap.wrapFor(target, WRAP_TICKS);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = SpiderCombat.center(target);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 30, target.getBbWidth() * 0.5, target.getBbHeight() * 0.5,
				target.getBbWidth() * 0.5, 0.02);
		level.playSound(null, c.x, c.y, c.z, SpiderSounds.SPLAT, SoundSource.PLAYERS, 1.0f, 0.7f);
		SpiderCombat.sendStrand(player, SLOT_PIN, -1, c, 0, 8);
	}
}
