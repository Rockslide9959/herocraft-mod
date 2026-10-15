package com.projecthero.mod.hulk;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hulk.data.HulkState;
import com.projecthero.mod.hulk.entity.HulkBoulderEntity;
import com.projecthero.mod.hulk.entity.HulkEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * V -- the Hulk's hands (v0.16.4 (user) rebuilt).
 * <ul>
 *   <li><b>V</b> picks up the mob you are looking at ({@code grabRange}) and holds it overhead. v0.16.4 (user): "make the
 *       hulk have to charge up his throw by holding v, at max charge he throws entities 200 blocks away" -- HOLD V to charge
 *       ({@code throwChargeTicks}, 1.75 s; a bar over his ability boxes fills), let go to throw: {@code throwTapBlocks} (20)
 *       for a tap up to {@code throwFarBlocks} (300) at full charge, along your look -- a drag-free arc the server holds it on
 *       all the way (its chunks are kept ticking ahead of it), and it smashes into whatever it hits. The ground debris
 *       charges the same way.</li>
 *   <li><b>Shift+V</b> while looking at a target within {@code lokiRange} (10): the "puny god" slam -- he grabs it and
 *       smashes it into the ground four times, overhead left-right-left-right, the server swinging it along those arcs
 *       in time with {@code ANIM_LOKI_SLAM} (impacts at {@link #slamImpactTick}). Shift+V while already holding a mob
 *       slams that one.</li>
 *   <li><b>Shift+V</b> otherwise rips a chunk of the ground he stands on out ({@link HulkBoulderEntity}, built from those
 *       very blocks); <b>V</b> throws it and it explodes on impact.</li>
 * </ul>
 * Bosses and anything too big (wider than 2.5 blocks or taller than 3.5) cannot be picked up; enemy players only with PvP
 * on. v0.13.17: a <b>squad-mate</b> can always be picked up -- carried safely overhead, set down with Shift+V (or by
 * sneaking), or thrown (they bowl over enemies and land unhurt).
 */
public final class HulkGrab {
	/** Who is holding what (entity id -- a mob or a boulder). */
	private static final Map<UUID, Integer> HELD = new ConcurrentHashMap<>();

	/** A far throw in flight: the server holds it on its drag-free arc. */
	private static final class Thrown {
		final int entityId;
		final long since;
		final Set<Integer> hit;
		final double vx;
		final double vz;
		double vy;
		final double gravity;
		/** v0.16.4: how far the throw was meant to go (100+ craters the landing). */
		final double blocks;
		Vec3 last;

		Thrown(int entityId, long since, Set<Integer> hit, Vec3 v, double gravity, Vec3 at, double blocks) {
			this.blocks = blocks;
			this.entityId = entityId;
			this.since = since;
			this.hit = hit;
			this.vx = v.x;
			this.vy = v.y;
			this.vz = v.z;
			this.gravity = gravity;
			this.last = at;
		}
	}

	/** Mobs in flight after a throw, per thrower. */
	private static final Map<UUID, java.util.List<Thrown>> THROWN = new ConcurrentHashMap<>();

	/** The Loki slam in progress, per Hulk. */
	private static final class Slam {
		final int victimId;
		final long start;
		final Vec3 fwd;
		final Vec3 grabbedAt;
		int impacts;
		/** The last slam tick handled (the player can be ticked twice in one game tick). */
		long lastT = -1L;

		Slam(int victimId, long start, Vec3 fwd, Vec3 grabbedAt) {
			this.victimId = victimId;
			this.start = start;
			this.fwd = fwd;
			this.grabbedAt = grabbedAt;
		}
	}

	private static final Map<UUID, Slam> SLAMS = new ConcurrentHashMap<>();
	/** How many impacts the last finished slam landed (tests). */
	private static final Map<UUID, Integer> LAST_SLAM_IMPACTS = new ConcurrentHashMap<>();

	/**
	 * Loki slam timing (ticks from animStart), matching ANIMS' loki_slam clip (3.0 s; client: HulkAnimatable.LOKI_SLAM_IMPACT_TICKS):
	 * hauled overhead by tick 9, then down into the floor at 15 / 27 / 39 / 51 (front-left, front-right, front-left,
	 * front-right), back overhead 6 ticks after each, let go at 60.
	 */
	public static final int[] LOKI_SLAM_IMPACT_TICKS = { 15, 27, 39, 51 };
	public static final int LOKI_OVERHEAD_TICKS = 9;
	/** Ticks from overhead down to the floor (and back up). */
	public static final int LOKI_HALF_SWING_TICKS = 6;
	public static final int LOKI_SLAMS = 4;
	public static final int LOKI_TOTAL_TICKS = 60;

	/** Keeps the far-thrown mob's chunks ticking as it flies (a short-lived ticket, renewed on the way). */
	private static final TicketType<ChunkPos> THROW_TICKET = TicketType.create("projecthero_hulk_throw",
			Comparator.comparingLong(ChunkPos::toLong), 60);

	private HulkGrab() {
	}

	/** v0.13.17: squad-mates the Hulk carried or threw take no fall damage until this game time. */
	private static final Map<UUID, Long> SAFE_LANDING = new ConcurrentHashMap<>();
	private static final int SAFE_LANDING_TICKS = 8 * 20;

	public static void clearSessionState() {
		HELD.clear();
		THROWN.clear();
		SAFE_LANDING.clear();
		SLAMS.clear();
		LAST_SLAM_IMPACTS.clear();
		THROWING.clear();
		RAMPAGE_HELD.clear();
	}

	// ---------------------------------------------------------------- v0.16.4 (user): the charged throw

	/** Charge 0..1 -> blocks: a tap {@code throwTapBlocks} (20) up to {@code throwFarBlocks} (300), a straight line between. */
	public static double throwBlocksFor(float charge) {
		HulkConfig.Abilities c = HulkConfig.abilities();
		double t = Math.max(0.0, Math.min(1.0, charge));
		return c.throwTapBlocks + (c.throwFarBlocks - c.throwTapBlocks) * t;
	}

	/** Charge 0..1 of the throw V is winding up right now (0 when not charging). Client-safe. */
	public static float throwCharge(Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		if (s == null || !s.hulk || s.moves.throwChargeStart <= 0L) {
			return 0.0f;
		}
		return Math.max(0.0f, Math.min(1.0f, (player.level().getGameTime() - s.moves.throwChargeStart)
				/ (float) Math.max(1, HulkConfig.abilities().throwChargeTicks)));
	}

	/** V is held, charging a throw. Client-safe. */
	public static boolean chargingThrow(Player player) {
		HulkState s = player.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.HULK_STATE, null);
		return s != null && s.hulk && s.combat.holding && s.moves.throwChargeStart > 0L;
	}

	/** v0.16.4: Hulks holding something the rampage AI grabbed (kept through the rampage, unlike a grab from before it). */
	private static final Set<UUID> RAMPAGE_HELD = ConcurrentHashMap.newKeySet();

	/** v0.13.17: was this player just carried or thrown by a squad-mate Hulk (so the landing doesn't hurt)? */
	public static boolean safeLanding(ServerPlayer player) {
		Long until = SAFE_LANDING.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.level().getGameTime() > until) {
			SAFE_LANDING.remove(player.getUUID());
			return false;
		}
		return true;
	}

	static void protectLanding(ServerPlayer mate) {
		SAFE_LANDING.put(mate.getUUID(), mate.level().getGameTime() + SAFE_LANDING_TICKS + 20 * 10);
		mate.resetFallDistance();
	}

	private static boolean isTeammate(ServerPlayer hulk, Entity e) {
		return e instanceof ServerPlayer mate && HulkCombat.ally(hulk, mate);
	}

	/** v0.13.17: put a carried squad-mate down gently in front of him. */
	private static void setDown(ServerPlayer player, ServerPlayer mate) {
		HELD.remove(player.getUUID());
		setHolding(player, false);
		HulkAbilities.anim(player, HulkState.ANIM_PICKUP);
		Vec3 at = player.position().add(HulkAbilities.flatLook(player).scale(1.6));
		mate.teleportTo(mate.serverLevel(), at.x, player.getY(), at.z, mate.getYRot(), mate.getXRot());
		mate.setDeltaMovement(Vec3.ZERO);
		mate.hurtMarked = true;
		protectLanding(mate);
	}

	/** Death / logout / dimension change / revert: let go of whatever he holds (a boulder crumbles, a slam stops). */
	public static void release(ServerPlayer player) {
		Integer id = HELD.remove(player.getUUID());
		THROWN.remove(player.getUUID());
		Slam slam = SLAMS.remove(player.getUUID());
		THROWING.remove(player.getUUID());
		RAMPAGE_HELD.remove(player.getUUID());
		ServerLevel level = (ServerLevel) player.level();
		if (id != null) {
			Entity e = level.getEntity(id);
			if (e instanceof HulkBoulderEntity boulder) {
				boulder.crumble();
			} else if (e instanceof ServerPlayer mate) {
				protectLanding(mate); // dropped from overhead: no fall damage
			}
		}
		if (slam != null && level.getEntity(slam.victimId) instanceof LivingEntity v) {
			v.setDeltaMovement(Vec3.ZERO);
			v.hurtMarked = true;
		}
		setHolding(player, false);
	}

	public static boolean holding(ServerPlayer player) {
		return HELD.containsKey(player.getUUID());
	}

	/** In the middle of a Loki slam. */
	public static boolean slamming(ServerPlayer player) {
		return SLAMS.containsKey(player.getUUID());
	}

	/** Impacts the last finished Loki slam landed (tests). */
	public static int lastSlamImpacts(ServerPlayer player) {
		return LAST_SLAM_IMPACTS.getOrDefault(player.getUUID(), 0);
	}

	/** The tick (from the slam's start) of impact {@code n} (1..4). */
	public static int slamImpactTick(int n) {
		return LOKI_SLAM_IMPACT_TICKS[Math.max(0, Math.min(LOKI_SLAMS - 1, n - 1))];
	}

	private static void setHolding(ServerPlayer player, boolean holding) {
		HulkState s = Hulk.state(player);
		if (s.combat.holding != holding || (!holding && s.moves.throwChargeStart != 0L)) {
			HulkState n = s.copy();
			n.combat.holding = holding;
			if (!holding) {
				n.moves.throwChargeStart = 0L; // nothing in his hands: no throw to charge
			}
			Hulk.save(player, n);
		}
	}

	/** V pressed ({@code shift}: Shift+V). */
	public static void press(ServerPlayer player, boolean shift) {
		if (!HulkAbilities.canAct(player) || slamming(player) || THROWING.contains(player.getUUID())) {
			return;
		}
		Entity held = heldEntity(player);
		if (held != null) {
			if (shift && isTeammate(player, held)) {
				setDown(player, (ServerPlayer) held); // v0.13.17: a squad-mate is set down, never slammed
			} else if (shift && held instanceof LivingEntity mob && (HulkAbilities.drivenNow(player) || HulkAbilities.ready(player, HulkAbilities.LOKI_SLAM))) {
				HELD.remove(player.getUUID());
				setHolding(player, false);
				startSlam(player, mob);
			} else if (HulkAbilities.drivenNow(player)) {
				throwHeld(player, held, 1.0f); // v0.16.4: the rampage AI throws at once, full strength
			} else {
				startThrowCharge(player); // v0.16.4 (user): hold V to charge, let go to throw (the debris too)
			}
			return;
		}
		if (HulkAbilities.busy(player)) {
			return;
		}
		if (shift) {
			LivingEntity target = AbilityHelpers.raycastEntity(player, HulkConfig.abilities().lokiRange);
			if (target != null && HulkAbilities.ready(player, HulkAbilities.LOKI_SLAM) && !isTeammate(player, target)) {
				if (grabbable(player, target)) {
					startSlam(player, target);
					return;
				}
				Hulk.say(player, "message.projecthero.hulk.grab_refused", ChatFormatting.GRAY);
			}
			if (HulkAbilities.ready(player, HulkAbilities.GRAB)) {
				tearEarth(player);
			}
		} else if (HulkAbilities.drivenNow(player) || HulkAbilities.ready(player, HulkAbilities.GRAB)) { // v0.16.4: the rampage AI keeps its own cooldowns
			grab(player);
		}
	}

	private static Entity heldEntity(ServerPlayer player) {
		Integer id = HELD.get(player.getUUID());
		if (id == null) {
			return null;
		}
		Entity e = ((ServerLevel) player.level()).getEntity(id);
		if (e == null || e.isRemoved() || (e instanceof LivingEntity l && !l.isAlive())) {
			HELD.remove(player.getUUID());
			setHolding(player, false);
			return null;
		}
		return e;
	}

	// ---------------------------------------------------------------- grab / throw

	private static void grab(ServerPlayer player) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, HulkConfig.abilities().grabRange);
		if (target == null || !target.isAlive()) {
			return;
		}
		// v0.13.17: a squad-mate can be picked up too -- carried safely, set down with Shift+V, or thrown with no harm to them
		boolean teammate = target instanceof ServerPlayer mate && HulkCombat.ally(player, mate);
		if (com.projecthero.mod.titanshifter.TitanCombat.isBoss(target) || target.getBbWidth() > 2.5f || target.getBbHeight() > 3.5f
				|| isHeld(target)
				|| (!teammate && HulkCombat.targets(player, target.getBoundingBox().inflate(0.1)).stream().noneMatch(e -> e == target))) {
			Hulk.say(player, "message.projecthero.hulk.grab_refused", ChatFormatting.GRAY);
			return;
		}
		if (teammate) {
			((ServerPlayer) target).displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.hulk.carried", player.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
		}
		target.stopRiding();
		target.ejectPassengers();
		HELD.put(player.getUUID(), target.getId());
		if (HulkAbilities.drivenNow(player)) {
			RAMPAGE_HELD.add(player.getUUID());
		}
		setHolding(player, true);
		HulkAbilities.anim(player, HulkState.ANIM_GRAB);
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.2, target.getZ(), 10, 0.4, 0.1, 0.4, 0.03);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 0.9f, 0.8f);
	}

	/** v0.16.4 (user): V went down with something in his hands -- the throw starts charging (a repeat while held is ignored). */
	private static void startThrowCharge(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (s.moves.throwChargeStart > 0L) {
			return;
		}
		HulkState n = s.copy();
		n.moves.throwChargeStart = player.level().getGameTime();
		Hulk.save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.5f);
	}

	/** v0.16.4 (user): V let go -- a charging throw flies, as far as the charge says. */
	public static void releaseV(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (s.moves.throwChargeStart <= 0L) {
			return;
		}
		float charge = throwCharge(player);
		HulkState n = s.copy();
		n.moves.throwChargeStart = 0L;
		Hulk.save(player, n);
		Entity held = heldEntity(player);
		if (held == null || !Hulk.isHulk(player) || !player.isAlive() || slamming(player) || THROWING.contains(player.getUUID())) {
			return;
		}
		throwHeld(player, held, charge);
	}

	/** Hulks mid-throw (the wind-up before the release frame): V is ignored until it leaves his hands. */
	private static final Set<UUID> THROWING = ConcurrentHashMap.newKeySet();

	/**
	 * V let go with something overhead: the throw animation, and the release on its frame (it stays overhead until then).
	 * {@code charge} 0..1 sets how far ({@link #throwBlocksFor}).
	 */
	private static void throwHeld(ServerPlayer player, Entity held, float charge) {
		double blocks = throwBlocksFor(charge);
		HulkAbilities.cooldown(player, HulkAbilities.GRAB, HulkConfig.abilities().grabCooldownTicks);
		boolean debris = held instanceof HulkBoulderEntity;
		HulkAbilities.anim(player, debris ? HulkState.ANIM_DEBRIS_THROW : HulkState.ANIM_THROW_FAR);
		THROWING.add(player.getUUID());
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 1.2f);
		HulkAbilities.schedule(player, debris ? HulkAbilities.DEBRIS_THROW_RELEASE_TICKS : HulkAbilities.THROW_FAR_RELEASE_TICKS, () -> {
			THROWING.remove(player.getUUID());
			Integer id = HELD.get(player.getUUID());
			if (id == null || id != held.getId() || held.isRemoved() || !Hulk.isHulk(player) || !player.isAlive()) {
				return;
			}
			letGo(player, held, blocks);
		});
	}

	private static void letGo(ServerPlayer player, Entity held, double blocks) {
		HELD.remove(player.getUUID());
		RAMPAGE_HELD.remove(player.getUUID());
		setHolding(player, false);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4f, 0.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
		Vec3 hand = held.position().add(0, held.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, hand.x, hand.y, hand.z, 1, 0, 0, 0, 0);
		if (held instanceof HulkBoulderEntity boulder) {
			// v0.16.4 (user): the debris charges like a mob -- the same drag-free arc (its own gravity), as far as the charge says
			Vec3 bv = aimedThrowVelocity(player, held, blocks, HulkBoulderEntity.GRAVITY);
			boulder.launch(bv, blocks);
			keepLoaded(level, boulder.position(), bv);
			return;
		}
		double g = held instanceof LivingEntity l ? l.getGravity() : 0.08;
		Vec3 v = aimedThrowVelocity(player, held, blocks, g);
		held.setDeltaMovement(v);
		held.hurtMarked = true;
		held.fallDistance = 0.0f;
		if (isTeammate(player, held)) {
			protectLanding((ServerPlayer) held); // v0.13.17: a thrown squad-mate lands unhurt (and still bowls over enemies)
		}
		Set<Integer> hit = new HashSet<>();
		hit.add(held.getId());
		THROWN.computeIfAbsent(player.getUUID(), k -> new java.util.ArrayList<>())
				.add(new Thrown(held.getId(), level.getGameTime(), hit, v, g, held.position(), blocks));
		keepLoaded(level, held.position(), v);
	}

	/**
	 * v0.16.4 (user: "make it throw where hes aiming"): the throw goes where he looks -- at the block, mob or ground his aim
	 * meets within the charge's reach, or, aiming at open air, to the point that far along his exact look line (up or down).
	 * Solved for the drag-free hold (velocity minus {@code gravity} each tick): it arrives at that point. Faster for short
	 * throws, never above twice the far-throw speed, so it reads as a hurl rather than a lob.
	 */
	public static Vec3 aimedThrowVelocity(ServerPlayer player, Entity held, double blocks, double gravity) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(blocks));
		Vec3 aim = end;
		net.minecraft.world.phys.BlockHitResult block = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end,
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
			aim = block.getLocation();
		}
		net.minecraft.world.phys.EntityHitResult mob = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, aim,
				new net.minecraft.world.phys.AABB(eye, aim).inflate(1.5),
				e -> e != player && e != held && e.isPickable() && !e.isSpectator() && e.isAlive(), eye.distanceToSqr(aim));
		if (mob != null) {
			aim = mob.getEntity().getBoundingBox().getCenter();
		}
		Vec3 start = held.position().add(0, held.getBbHeight() * 0.5, 0);
		Vec3 d = aim.subtract(start);
		double speed = Math.max(1.0, HulkConfig.abilities().throwFarSpeed);
		double dist = d.length();
		int ticks = Math.max(3, (int) Math.ceil(dist / (speed * 1.6)));
		Vec3 v = Vec3.ZERO;
		for (int i = 0; i < 40; i++) {
			// p(T) = start + v*T - g*T*(T+1)/2  ->  v = d/T + (0, g*(T+1)/2, 0)
			v = d.scale(1.0 / ticks).add(0, gravity * (ticks + 1) * 0.5, 0);
			if (v.length() <= speed * 2.0) {
				break;
			}
			ticks = (int) Math.ceil(ticks * 1.15) + 1;
		}
		return v;
	}

	/** The V throw's launch: a drag-free arc that comes down {@code blocks} away along the look's heading. */
	public static Vec3 farThrowVelocity(Vec3 look, double blocks, double gravity) {
		return HulkLeap.dragFreeArc(look, blocks, gravity, 12.0, 45.0, HulkConfig.abilities().throwFarSpeed, 6.0);
	}

	public static void keepLoaded(ServerLevel level, Vec3 at, Vec3 v) {
		// here, and where it will be in a second, so it never freezes at the edge of the loaded world
		for (int ahead : new int[] { 0, 20 }) {
			ChunkPos cp = new ChunkPos(BlockPos.containing(at.x + v.x * ahead, at.y, at.z + v.z * ahead));
			level.getChunkSource().addRegionTicket(THROW_TICKET, cp, 2, cp);
		}
	}

	// ---------------------------------------------------------------- Shift+V: the Loki slam

	private static void startSlam(ServerPlayer player, LivingEntity victim) {
		victim.stopRiding();
		victim.ejectPassengers();
		long now = player.level().getGameTime();
		SLAMS.put(player.getUUID(), new Slam(victim.getId(), now, HulkAbilities.flatLook(player), victim.position()));
		HulkState n = Hulk.state(player).copy();
		n.animId = HulkState.ANIM_LOKI_SLAM;
		n.animStart = now;
		n.abilityReadyAt.put(HulkAbilities.LOKI_SLAM, now + LOKI_TOTAL_TICKS + HulkConfig.abilities().lokiCooldownTicks);
		Hulk.save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6f, 0.7f);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.2f, 0.5f);
	}

	private static void tickSlam(ServerPlayer player) {
		Slam slam = SLAMS.get(player.getUUID());
		if (slam == null) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long t = level.getGameTime() - slam.start;
		if (t == slam.lastT) {
			return;
		}
		slam.lastT = t;
		Entity e = level.getEntity(slam.victimId);
		HulkState s = Hulk.state(player);
		if (!(e instanceof LivingEntity victim) || !victim.isAlive() || !s.hulk || s.rampaging(level.getGameTime())
				|| victim.distanceToSqr(player) > 400.0) {
			endSlam(player, slam, e instanceof LivingEntity l ? l : null);
			return;
		}
		double h = player.getBbHeight();
		double groundY = player.getY() + 0.1;
		Vec3 fwd = slam.fwd;
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		Vec3 pivot = player.position().add(fwd.scale(player.getBbWidth() * 0.5 + 0.6)).add(0, h * 0.8, 0);
		double radius = h * 0.6 + victim.getBbHeight() * 0.5;
		// the angle (in the plane across his front: 0 = his right, 90 = overhead, 180 = his left) where the swing meets the ground
		double below = Math.toDegrees(Math.asin(Math.min(1.0, (pivot.y - groundY - victim.getBbHeight() * 0.3) / radius)));
		double leftGround = 180.0 + below;
		double rightGround = -below;
		Vec3 point;
		if (t < LOKI_OVERHEAD_TICKS) {
			// grabbed (0.2-0.3 s) and hauled in and up overhead (0.45 s)
			double u = Math.max(0.0, (t - 3) / (double) (LOKI_OVERHEAD_TICKS - 3));
			Vec3 overhead = pivot.add(0, radius, 0);
			Vec3 from = slam.grabbedAt.add(0, victim.getBbHeight() * 0.5, 0);
			point = from.add(overhead.subtract(from).scale(u * u));
		} else {
			// half-swings of 6 ticks: overhead -> floor (accelerating), floor -> overhead (slowing); sides L R L R
			long j = Math.min(2L * LOKI_SLAMS - 1, (t - LOKI_OVERHEAD_TICKS) / LOKI_HALF_SWING_TICKS);
			double u = Math.min(1.0, ((t - LOKI_OVERHEAD_TICKS) - j * LOKI_HALF_SWING_TICKS) / (double) LOKI_HALF_SWING_TICKS);
			int k = (int) (j / 2);
			double ground = k % 2 == 0 ? leftGround : rightGround;
			double deg = j % 2 == 0 ? 90.0 + (ground - 90.0) * u * u : ground + (90.0 - ground) * (1.0 - (1.0 - u) * (1.0 - u));
			if (t >= LOKI_SLAM_IMPACT_TICKS[LOKI_SLAMS - 1]) {
				deg = ground; // after the last slam it stays down
			}
			double a = Math.toRadians(deg);
			// a little in front of him at the floor (front-left / front-right)
			double forward = Math.max(0.0, -Math.sin(a)) * 0.8;
			point = pivot.add(right.scale(Math.cos(a) * radius)).add(0, Math.sin(a) * radius, 0).add(fwd.scale(forward));
		}
		double feetY = Math.max(groundY, point.y - victim.getBbHeight() * 0.5);
		move(victim, point.x, feetY, point.z);
		for (int n = 1; n <= LOKI_SLAMS; n++) {
			if (t == slamImpactTick(n)) {
				slamImpact(player, slam, victim);
			}
		}
		if (t >= LOKI_TOTAL_TICKS) {
			endSlam(player, slam, victim);
		}
	}

	private static void move(LivingEntity victim, double x, double y, double z) {
		if (victim instanceof ServerPlayer sp) {
			sp.teleportTo(sp.serverLevel(), x, y, z, sp.getYRot(), sp.getXRot());
		} else {
			victim.moveTo(x, y, z, victim.getYRot(), victim.getXRot());
		}
		victim.setDeltaMovement(Vec3.ZERO);
		victim.fallDistance = 0.0f;
		victim.hurtMarked = true;
	}

	private static void slamImpact(ServerPlayer player, Slam slam, LivingEntity victim) {
		slam.impacts++;
		ServerLevel level = (ServerLevel) player.level();
		victim.invulnerableTime = 0;
		AbilityHelpers.hurtLands(player, victim, HulkConfig.abilities().lokiSlamDamage);
		Vec3 at = victim.position();
		// the floor gives under it every time
		GroundCracks.star(level, at, (2.5 + slam.impacts * 0.5) * WorldBreaker.aoe(player), 6, 140);
		BlockPos s = GroundCracks.surface(level, at.x, at.y, at.z);
		BlockState ground = s == null ? Blocks.DIRT.defaultBlockState() : level.getBlockState(s);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, 40, 0.9, 0.2, 0.9, 0.3);
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), at.x, at.y + 0.1, at.z, 16, 0.8, 0.0, 0.8, 0.3);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y + victim.getBbHeight() * 0.5, at.z, 12, 0.4, 0.4, 0.4, 0.3);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.0f, 0.6f + slam.impacts * 0.05f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
		HulkCombat.shake(level, at, 0.6f + slam.impacts * 0.1f, 8, 20.0);
	}

	private static void endSlam(ServerPlayer player, Slam slam, LivingEntity victim) {
		SLAMS.remove(player.getUUID());
		LAST_SLAM_IMPACTS.put(player.getUUID(), slam.impacts);
		if (victim != null && victim.isAlive()) {
			victim.setDeltaMovement(new Vec3(0, 0.2, 0));
			victim.hurtMarked = true;
			victim.fallDistance = 0.0f;
		}
	}

	// ---------------------------------------------------------------- Shift+V: the debris

	private static void tearEarth(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		// the ground he is standing on (the column under his feet, then just in front)
		BlockPos under = GroundCracks.surface(level, player.getX(), player.getY() - 0.5, player.getZ());
		if (under == null) {
			Vec3 front = player.position().add(HulkAbilities.flatLook(player).scale(1.5));
			under = GroundCracks.surface(level, front.x, player.getY() - 0.5, front.z);
		}
		BlockState ground = under == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(under);
		if (under == null || ground.isAir() || !ground.getFluidState().isEmpty()) {
			Hulk.say(player, "message.projecthero.hulk.no_earth", ChatFormatting.GRAY);
			return;
		}
		BlockState mid = solidOr(level, under.below(), ground);
		BlockState deep = solidOr(level, under.below(2), mid);
		HulkBoulderEntity boulder = HulkEntities.BOULDER.create(level);
		if (boulder == null) {
			return;
		}
		boulder.setOwner(player);
		boulder.setBlocks(debrisState(ground), debrisState(mid), debrisState(deep));
		Vec3 at = holdPoint(player, boulder);
		boulder.moveTo(at.x, at.y, at.z, player.getYRot(), 0.0f);
		level.addFreshEntity(boulder);
		HELD.put(player.getUUID(), boulder.getId());
		setHolding(player, true);
		HulkAbilities.anim(player, HulkState.ANIM_DEBRIS_RIP);
		// rip it out of the ground: a pit where it came from, cracks round it
		if (HulkCombat.canBreakBlocks(level)) {
			BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
			BlockPos front = BlockPos.containing(player.position().add(HulkAbilities.flatLook(player).scale(player.getBbWidth() * 0.5 + 1.2)))
					.atY(under.getY());
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy >= -1; dy--) {
						p.set(front.getX() + dx, front.getY() + dy, front.getZ() + dz);
						if (Math.abs(dx) + Math.abs(dz) + (-dy) <= 2 && level.mayInteract(player, p)
								&& HulkCombat.breakable(level, p, level.getBlockState(p))) {
							level.destroyBlock(p, false, player);
						}
					}
				}
			}
		}
		GroundCracks.star(level, Vec3.atBottomCenterOf(under.above()), 3.5, 7, 120);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), under.getX() + 0.5, under.getY() + 1.0, under.getZ() + 0.5,
				60, 1.0, 0.4, 1.0, 0.25);
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, mid), under.getX() + 0.5, under.getY() + 1.0, under.getZ() + 0.5,
				20, 0.8, 0.0, 0.8, 0.3);
		level.playSound(null, under.getX(), under.getY(), under.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.8f, 0.5f);
		level.playSound(null, under.getX(), under.getY(), under.getZ(), ground.getSoundType().getBreakSound(), SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, under.getX(), under.getY(), under.getZ(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.5f, 0.6f);
		HulkCombat.shake(level, player.position(), 0.4f, 8, 16.0);
	}

	private static BlockState solidOr(ServerLevel level, BlockPos pos, BlockState fallback) {
		BlockState st = level.getBlockState(pos);
		return st.isAir() || !st.getFluidState().isEmpty() || st.hasBlockEntity() ? fallback : st;
	}

	/** A block entity, a plant or a non-full block would render oddly as a chunk of rock: use the nearest earthy block. */
	private static BlockState debrisState(BlockState st) {
		if (st.hasBlockEntity() || st.canBeReplaced() || st.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
			return Blocks.DIRT.defaultBlockState();
		}
		return st;
	}

	/** The block states of the debris he is holding (tests): surface, middle, deep -- or null. */
	public static BlockState[] heldDebris(ServerPlayer player) {
		Integer id = HELD.get(player.getUUID());
		if (id != null && ((ServerLevel) player.level()).getEntity(id) instanceof HulkBoulderEntity b) {
			return new BlockState[] { b.block(0), b.block(1), b.block(2) };
		}
		return null;
	}

	/** Where a held thing sits: above his head, a little forward. */
	private static Vec3 holdPoint(ServerPlayer player, Entity held) {
		Vec3 fwd = HulkAbilities.flatLook(player);
		return player.position().add(fwd.scale(0.3)).add(0, player.getBbHeight() + 0.35, 0);
	}

	// ---------------------------------------------------------------- tick

	/** Every Gamma player, every tick (from {@link Hulk#tick}). */
	static void tick(ServerPlayer player) {
		Integer id = HELD.get(player.getUUID());
		if (id != null) {
			HulkState s = Hulk.state(player);
			Entity held = heldEntity(player);
			long now = player.level().getGameTime();
			if (held != null && (!s.hulk || (s.rampaging(now) && !RAMPAGE_HELD.contains(player.getUUID())) || !player.isAlive()
					|| held.distanceToSqr(player) > 144.0)) {
				release(player);
			} else if (held instanceof ServerPlayer mate && mate.isShiftKeyDown() && isTeammate(player, mate)) {
				setDown(player, mate); // v0.13.17: a carried squad-mate sneaks to get down
			} else if (held != null) {
				Vec3 at = holdPoint(player, held);
				if (held instanceof ServerPlayer sp) {
					sp.teleportTo(sp.serverLevel(), at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
				} else {
					held.moveTo(at.x, at.y, at.z, held.getYRot(), held.getXRot());
				}
				held.setDeltaMovement(Vec3.ZERO);
				held.fallDistance = 0.0f;
				held.hurtMarked = true;
			}
		}
		tickThrowCharge(player);
		tickSlam(player);
		tickThrown(player);
	}

	/** v0.16.4 (user): the throw's wind-up -- gamma gathering in his arms, a ring at full charge; a lost release throws itself. */
	private static void tickThrowCharge(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		if (s.moves.throwChargeStart <= 0L) {
			return;
		}
		long now = player.level().getGameTime();
		long t = now - s.moves.throwChargeStart;
		int full = Math.max(1, HulkConfig.abilities().throwChargeTicks);
		if (!s.hulk || !HELD.containsKey(player.getUUID())) {
			HulkState n = s.copy();
			n.moves.throwChargeStart = 0L;
			Hulk.save(player, n);
			return;
		}
		if (t < 0L || t > full + 100L) {
			releaseV(player); // the key-up was lost: it goes at full strength
			return;
		}
		Integer id = HELD.get(player.getUUID());
		Entity held = id == null ? null : ((ServerLevel) player.level()).getEntity(id);
		ServerLevel level = (ServerLevel) player.level();
		float c = Math.min(1.0f, t / (float) full);
		if (held != null && now % 2L == 0L) {
			Vec3 at = held.position().add(0, held.getBbHeight() * 0.5, 0);
			level.sendParticles(HulkAbilities.GAMMA, at.x, at.y, at.z, 1 + Math.round(c * 4.0f), held.getBbWidth() * 0.6, held.getBbHeight() * 0.4,
					held.getBbWidth() * 0.6, 0.01);
		}
		if (t % 8L == 0L && t < full) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.7f + c * 0.6f,
					0.7f + c * 0.4f);
		}
		if (t == full) {
			// fully wound up: a flash of gamma and a grunt
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 1.4f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 0.5f);
			if (held != null) {
				Vec3 at = held.position().add(0, held.getBbHeight() * 0.5, 0);
				level.sendParticles(HulkAbilities.GAMMA_BRIGHT, at.x, at.y, at.z, 20, held.getBbWidth() * 0.7, held.getBbHeight() * 0.5,
						held.getBbWidth() * 0.7, 0.05);
			}
		}
	}

	private static void tickThrown(ServerPlayer player) {
		java.util.List<Thrown> list = THROWN.get(player.getUUID());
		if (list == null || list.isEmpty()) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Iterator<Thrown> it = list.iterator();
		while (it.hasNext()) {
			Thrown t = it.next();
			Entity e = level.getEntity(t.entityId);
			if (e == null || e.isRemoved() || now - t.since > 400) {
				it.remove();
				continue;
			}
			if (now - t.since < 2) {
				continue;
			}
			LivingEntity struck = null;
			for (LivingEntity other : HulkCombat.targets(player, e.getBoundingBox().inflate(0.4))) {
				if (other != e && !t.hit.contains(other.getId())) {
					struck = other;
					break;
				}
			}
			boolean landed = e.horizontalCollision || (e.onGround() && now - t.since > 3) || e.isInWater();
			if (struck == null && !landed) {
				// hold it on its drag-free arc (a chunk that is not ticking yet holds it still: wait for it)
				boolean moved = e.position().distanceToSqr(t.last) > 1.0e-4;
				if (moved) {
					t.vy -= t.gravity;
				}
				t.last = e.position();
				e.setDeltaMovement(t.vx, t.vy, t.vz);
				e.hurtMarked = true;
				e.fallDistance = 0.0f;
				if (now % 10 == 0) {
					keepLoaded(level, e.position(), new Vec3(t.vx, 0, t.vz));
				}
				if (now % 2 == 0) {
					level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, 0.15, 0.15, 0.15, 0.0);
				}
				continue;
			}
			it.remove();
			impact(player, e, t.blocks);
		}
	}

	/** True while {@code e} is still flying from this Hulk's far throw (tests). */
	public static boolean inFlight(ServerPlayer player, Entity e) {
		java.util.List<Thrown> list = THROWN.get(player.getUUID());
		return list != null && list.stream().anyMatch(t -> t.entityId == e.getId());
	}

	/** A thrown mob hits something: it and everything round the impact take the hit. */
	private static void impact(ServerPlayer player, Entity thrown, double blocks) {
		ServerLevel level = (ServerLevel) player.level();
		// v0.16.4 (user): a throw of 100+ blocks hits harder (up to +50% at 200) and craters where it lands (below)
		float dmg = HulkConfig.abilities().throwFarImpactDamage * longThrowDamageScale(blocks);
		Vec3 at = thrown.position().add(0, thrown.getBbHeight() * 0.5, 0);
		HulkCombat.Hit hit = new HulkCombat.Hit(dmg, 1.4, 0.35);
		if (thrown instanceof LivingEntity l && !isTeammate(player, l)) {
			l.invulnerableTime = 0;
			HulkCombat.strike(player, l, at, hit, 1.0f);
		}
		thrown.fallDistance = 0.0f;
		double wb = WorldBreaker.aoe(player); // v0.16.4 (user): World Breaker doubles the impact
		HulkCombat.radial(player, at, 3.0 * wb, hit, false);
		GroundCracks.star(level, thrown.position(), 3.0 * wb, 6, 120);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3, 0.3, 0.3, 0);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 16, 0.6, 0.3, 0.6, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 1.6f, 0.5f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1.6f, 0.7f);
		HulkCombat.shake(level, at, 0.4f, 8, 16.0);
		longThrowImpact(player, thrown.position(), blocks, hit);
	}

	// ---------------------------------------------------------------- v0.16.4 (user): the long throw's crater

	/**
	 * 0..1: how far past {@code longThrowMinBlocks} (100) a throw meant to go -- (planned - 100) / 200, clamped. Negative
	 * (-1) for a throw under 100 blocks: no destruction at all.
	 */
	public static double longThrowT(double blocks) {
		HulkConfig.Abilities c = HulkConfig.abilities();
		if (blocks < c.longThrowMinBlocks) {
			return -1.0;
		}
		return Math.max(0.0, Math.min(1.0, (blocks - c.longThrowMinBlocks) / Math.max(1.0, c.longThrowRampBlocks)));
	}

	/** The impact damage multiplier: 1 under 100 blocks, up to 1 + {@code longThrowDamageBonus} at t = 1. */
	public static float longThrowDamageScale(double blocks) {
		double t = longThrowT(blocks);
		return t < 0.0 ? 1.0f : (float) (1.0 + HulkConfig.abilities().longThrowDamageBonus * t);
	}

	/**
	 * v0.16.4 (user): "if he throws an entity 100 blocks or more it causes destruction where the entity lands, make the
	 * destruction more based on how far the player throws the entity past 100 blocks". At {@code feet} (where the mob or the
	 * debris came down): a crater ({@link HulkCombat#crater} -- the Hulk's block rules, mobGriefing switch, loaded chunks only,
	 * drops per {@code dropBrokenBlocks}, World Breaker's x2; broken by the Hulk, so it is his), ground cracks, a shockwave
	 * ring, an explosion burst, dust and debris and a heavy boom -- radius {@code longThrowCraterRadiusMin..Max} and up to
	 * {@code longThrowCraterBlocksMin..Max} blocks, growing with {@link #longThrowT}. Under 100 blocks: nothing. Returns the
	 * blocks broken (0 for a short throw).
	 */
	public static int longThrowImpact(ServerPlayer player, Vec3 feet, double blocks, HulkCombat.Hit hit) {
		double t = longThrowT(blocks);
		if (t < 0.0 || player == null) {
			return 0;
		}
		HulkConfig.Abilities c = HulkConfig.abilities();
		ServerLevel level = (ServerLevel) player.level();
		double radius = c.longThrowCraterRadiusMin + (c.longThrowCraterRadiusMax - c.longThrowCraterRadiusMin) * t;
		int cap = (int) Math.round(c.longThrowCraterBlocksMin + (c.longThrowCraterBlocksMax - c.longThrowCraterBlocksMin) * t);
		double shock = (c.longThrowShockRadiusMin + (c.longThrowShockRadiusMax - c.longThrowShockRadiusMin) * t) * WorldBreaker.aoe(player);
		BlockState ground = level.getBlockState(BlockPos.containing(feet).below());
		if (ground.isAir()) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		// the shockwave: everything round the landing is knocked flat (whatever the throw itself already hit is skipped)
		if (hit != null) {
			HulkCombat.Hit wave = new HulkCombat.Hit(hit.damage * 0.5f, 1.4 + 1.2 * t, 0.4 + 0.4 * t);
			wave.hit.addAll(hit.hit);
			HulkCombat.radial(player, feet.add(0, 0.5, 0), shock, wave, true);
		}
		int broken = HulkCombat.crater(player, feet.add(0, -0.5, 0), radius, cap);
		GroundCracks.star(level, feet, radius * 1.4 * WorldBreaker.destruction(player), 8 + (int) (6 * t), 160);
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (double r = 1.5; r <= shock; r += 1.5) {
			HulkCombat.ring(level, dust, feet.add(0, 0.2, 0), r, (int) (r * 6));
		}
		level.sendParticles(t > 0.5 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION, feet.x, feet.y + 0.5, feet.z,
				t > 0.5 ? 1 : 4, 0.6, 0.3, 0.6, 0.0);
		level.sendParticles(dust, feet.x, feet.y + 0.5, feet.z, 60 + (int) (80 * t), radius * 0.4, 0.8, radius * 0.4, 0.35);
		level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), feet.x, feet.y + 0.2, feet.z,
				30 + (int) (40 * t), radius * 0.5, 0.1, radius * 0.5, 0.4);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, feet.x, feet.y + 0.5, feet.z, 10 + (int) (20 * t), radius * 0.4, 0.4,
				radius * 0.4, 0.03);
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, feet.x, feet.y + 0.3, feet.z, 1, 0, 0, 0, 0);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f + 2.0f * (float) t, 0.6f);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 2.5f, 0.5f);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.8f, 0.5f);
		level.playSound(null, feet.x, feet.y, feet.z, ground.getSoundType().getBreakSound(), SoundSource.PLAYERS, 2.0f, 0.6f);
		HulkCombat.shake(level, feet, 0.6f + 0.6f * (float) t, 14, 32.0 + 24.0 * t);
		return broken;
	}

	/**
	 * v0.15.3: may the Hulk lay hands on {@code target} to hurt it -- V's rules for an enemy (no bosses, nothing wider than
	 * 2.5 or taller than 3.5 blocks, only what {@link HulkCombat#targets} allows). Shared with the Gladiator's Arena Grapple.
	 */
	public static boolean grabbable(ServerPlayer player, LivingEntity target) {
		return target != null && target.isAlive() && !com.projecthero.mod.titanshifter.TitanCombat.isBoss(target)
				&& target.getBbWidth() <= 2.5f && target.getBbHeight() <= 3.5f && !isHeld(target)
				&& HulkCombat.targets(player, target.getBoundingBox().inflate(0.1)).stream().anyMatch(e -> e == target);
	}

	/** True if {@code e} is currently held overhead (or being slammed) by some Hulk (for the rider / AI checks). */
	public static boolean isHeld(Entity e) {
		if (HELD.containsValue(e.getId())) {
			return true;
		}
		for (Slam s : SLAMS.values()) {
			if (s.victimId == e.getId()) {
				return true;
			}
		}
		return false;
	}

	/** Used by the HUD / animations through {@link HulkState.Combat#holding}; kept for tests. */
	static boolean holdingSomething(Player player) {
		return player instanceof ServerPlayer sp && holding(sp);
	}
}
