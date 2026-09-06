package com.projecthero.mod.spider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.TempBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Every temporary thing Spider-Man's webbing leaves in the world: the Web Net platforms and the
 * cocoons wrapped around individual mobs. Both are strictly temporary and both clean themselves up
 * without needing anybody to still be online, holding the power or standing nearby.
 *
 * <h2>Nets</h2>
 * A net is real cobweb placed through {@link TempBlocks}, which is the mod's existing answer to
 * "put blocks in the world for a while and then put back exactly what was there". That gets the
 * restore-on-expiry, the replaceable-only placement rule, the global cap and the drop-everything-on-
 * server-stop behaviour for free rather than reimplementing all four. The list kept here is only the
 * <em>area</em> of each net, so mobs standing in one can be slowed; the blocks themselves are not
 * this class's problem.
 *
 * <h2>Cocoons</h2>
 * A cocoon is a timer against an entity id plus per-tick upkeep, in the same shape as the mod's other
 * marker maps. Bosses get a much weaker version of it -- a boss that can be completely switched off
 * for five seconds is not a boss.
 *
 * <p>Both collections are {@code static}, so both are registered with {@code ServerStateReset}: a
 * static map that outlives its server pins that entire world in memory, which is the single most
 * expensive mistake this codebase has had to fix.
 */
public final class SpiderWebs {
	/** Spec section 19: a net lasts about half a minute and then dissolves. */
	public static final int NET_LIFETIME_TICKS = 25 * 20;
	/** Spec section 18: a cocooned ordinary mob is out of the fight for five seconds (default). */
	public static final int COCOON_TICKS = 5 * 20;
	/** v0.6.20: the pin that fires automatically on the third Web Shot stickiness stack -- 12 seconds. */
	public static final int WEB_SHOT_COCOON_TICKS = 12 * 20;

	/**
	 * v0.6.20: Web Shot stickiness. Each hit adds a stack (max {@link #MAX_STICK_STACKS}); the window is
	 * anchored to the <em>first</em> hit and does not extend, so the follow-ups have to land inside
	 * {@link #STICK_WINDOW_TICKS} of it or the count starts over.
	 */
	public static final int MAX_STICK_STACKS = 3;
	/** Three seconds from the first stickiness hit to land the next one. */
	public static final int STICK_WINDOW_TICKS = 3 * 20;
	/** How long each stickiness application slows / weighs down the target. */
	public static final int STICK_EFFECT_TICKS = 6 * 20;
	/** What a boss gets instead: heavily hampered, briefly, but never switched off. */
	public static final int BOSS_COCOON_TICKS = 40;

	/**
	 * At or above this max health an entity is treated as a boss and resists full restraint. Set to
	 * match the same 200 HP line {@code AbilityHelpers.isValidGrabTarget} already draws, so "what counts
	 * as too big to manhandle" means one thing across the whole mod. The three vanilla bosses are named
	 * explicitly as well, since the Ender Dragon sits exactly on the boundary.
	 */
	public static final float BOSS_HEALTH = 200.0f;

	private record Net(ServerLevel level, Vec3 centre, double radius, long expiresAt, int ownerId) {
	}

	private static final List<Net> NETS = new ArrayList<>();
	private static final int MAX_NETS = 64;

	/** entity id -> absolute game-time the cocoon breaks. */
	private static final Map<Integer, Long> COCOONED = new ConcurrentHashMap<>();

	/** entity id -> {current stack count, absolute game-time the stickiness window closes}. */
	private static final Map<Integer, long[]> WEB_STICK = new ConcurrentHashMap<>();

	private SpiderWebs() {
	}

	// ---------------- nets ----------------

	/**
	 * Weave a disc of webbing centred on {@code centre}. Returns how many blocks actually took, which
	 * is zero when the spot is solid or when the server has terrain modification switched off.
	 */
	public static int weaveNet(ServerPlayer owner, ServerLevel level, Vec3 centre, int radius) {
		int placed = 0;
		BlockPos base = BlockPos.containing(centre);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				BlockPos pos = base.offset(dx, 0, dz);
				if (TempBlocks.place(level, pos, Blocks.COBWEB.defaultBlockState(), NET_LIFETIME_TICKS)) {
					placed++;
				}
			}
		}
		if (placed > 0) {
			if (NETS.size() >= MAX_NETS) {
				NETS.remove(0);
			}
			NETS.add(new Net(level, centre, radius + 0.5, level.getGameTime() + NET_LIFETIME_TICKS,
					owner.getId()));
		}
		return placed;
	}

	// ---------------- cocoons ----------------

	public static boolean isBoss(LivingEntity target) {
		return target.getMaxHealth() >= BOSS_HEALTH
				|| target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
				|| target instanceof net.minecraft.world.entity.boss.wither.WitherBoss
				|| target instanceof net.minecraft.world.entity.monster.warden.Warden;
	}

	/** Wrap a target for the default duration. Returns the number of ticks it will actually be held for. */
	public static int cocoon(ServerPlayer owner, LivingEntity target) {
		return cocoonFor(owner, target, COCOON_TICKS);
	}

	/** Wrap a target for a specific ordinary-mob duration (bosses still get the capped weak version). */
	public static int cocoonFor(ServerPlayer owner, LivingEntity target, int ordinaryTicks) {
		boolean boss = isBoss(target);
		int ticks = boss ? BOSS_COCOON_TICKS : ordinaryTicks;
		if (target instanceof Player) {
			// PvP restraint is halved and never total, exactly as AbilityHelpers.applyControl does for
			// every other hard crowd-control ability in the mod.
			ticks = Math.max(10, ticks / 2);
		}
		long until = target.level().getGameTime() + ticks;
		COCOONED.merge(target.getId(), until, Math::max);

		com.projecthero.mod.hero.power.AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN,
				ticks, boss ? 2 : 6);
		com.projecthero.mod.hero.power.AbilityHelpers.applyControl(target, MobEffects.WEAKNESS,
				ticks, boss ? 0 : 2);
		com.projecthero.mod.hero.power.AbilityHelpers.applyControl(target, MobEffects.DIG_SLOWDOWN, ticks, 3);
		if (!boss && target instanceof net.minecraft.world.entity.Mob mob) {
			mob.setTarget(null);
		}
		return ticks;
	}

	// ---------------- Web Shot stickiness (v0.6.20) ----------------

	/**
	 * Record another Web Shot hit on {@code target} and return the resulting stack count (1..
	 * {@link #MAX_STICK_STACKS}). The window is anchored to the first hit: a follow-up inside
	 * {@link #STICK_WINDOW_TICKS} increments the count without moving the deadline, and one after it
	 * simply starts a fresh stack of 1.
	 */
	public static int addStickStack(LivingEntity target, long now) {
		long[] e = WEB_STICK.get(target.getId());
		if (e != null && e[1] > now) {
			int stacks = (int) Math.min(MAX_STICK_STACKS, e[0] + 1);
			WEB_STICK.put(target.getId(), new long[] { stacks, e[1] });
			return stacks;
		}
		WEB_STICK.put(target.getId(), new long[] { 1L, now + STICK_WINDOW_TICKS });
		return 1;
	}

	/** Forget the stickiness stacks on a target -- called once a full cocoon takes over. */
	public static void clearStickStacks(LivingEntity target) {
		WEB_STICK.remove(target.getId());
	}

	public static boolean isCocooned(Entity entity) {
		Long until = COCOONED.get(entity.getId());
		return until != null && entity.level().getGameTime() < until;
	}

	/** Whether {@code entity} currently has one or more live Web Shot stickiness stacks. */
	public static boolean isWebbed(Entity entity) {
		long[] e = WEB_STICK.get(entity.getId());
		return e != null && e[1] > entity.level().getGameTime();
	}

	/**
	 * v0.6.22: "wrapped up in webbing" in any form -- a live Web Shot stack, a full cocoon, or simply
	 * standing in a cobweb block. Spider-Man players deal +40% damage to anything this is true of.
	 */
	public static boolean isWebImpaired(Entity entity) {
		if (isWebbed(entity) || isCocooned(entity)) {
			return true;
		}
		net.minecraft.core.BlockPos feet = entity.blockPosition();
		return entity.level().getBlockState(feet).is(Blocks.COBWEB)
				|| entity.level().getBlockState(feet.above()).is(Blocks.COBWEB);
	}

	// ---------------- upkeep ----------------

	/**
	 * Once-per-server-tick upkeep for both collections. Cheap by construction: the loops are over a
	 * handful of live nets and cocoons, never over the world, and both do their real work only every
	 * few ticks.
	 */
	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();

		if (!WEB_STICK.isEmpty() && server.getTickCount() % 20 == 0) {
			WEB_STICK.values().removeIf(e -> e[1] <= now);
		}

		if (!COCOONED.isEmpty()) {
			COCOONED.entrySet().removeIf(e -> {
				if (e.getValue() <= now) {
					return true;
				}
				// Find and pin the entity. Looking it up in each level is only done every other tick.
				if (server.getTickCount() % 2 != 0) {
					return false;
				}
				for (ServerLevel level : server.getAllLevels()) {
					Entity entity = level.getEntity(e.getKey());
					if (entity == null) {
						continue;
					}
					if (!entity.isAlive()) {
						return true;
					}
					if (entity instanceof LivingEntity living && !isBoss(living)) {
						// held in place: horizontal motion zeroed, vertical left alone so it still falls
						Vec3 v = entity.getDeltaMovement();
						entity.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
						entity.hurtMarked = true;
					}
					emitCocoonParticles(level, entity);
					return false;
				}
				return true; // entity is not in any loaded level -- stop tracking it
			});
		}

		if (NETS.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}
		NETS.removeIf(net -> {
			if (net.expiresAt() <= net.level().getGameTime()) {
				return true;
			}
			AABB box = AABB.ofSize(net.centre(), net.radius() * 2, 3.0, net.radius() * 2);
			for (LivingEntity entity : net.level().getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
				if (entity.getId() == net.ownerId() || movesFreelyThroughWebbing(entity)) {
					continue;
				}
				entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, true, true));
			}
			return false;
		});
	}

	/**
	 * The visible "this thing is wrapped up and stuck" cue: a cloud of cobweb bits clinging to the
	 * whole body, plus a few white sticky-strand flecks, so a player can tell at a glance that a
	 * cocooned skeleton or pillager is out of the fight even before it fails to shoot at them.
	 */
	private static void emitCocoonParticles(ServerLevel level, Entity entity) {
		double h = Math.max(0.6, entity.getBbHeight());
		double w = Math.max(0.4, entity.getBbWidth());
		level.sendParticles(ParticleTypes.ITEM_COBWEB,
				entity.getX(), entity.getY() + h * 0.5, entity.getZ(),
				9, w * 0.55, h * 0.45, w * 0.55, 0.0);
		level.sendParticles(ParticleTypes.ITEM_COBWEB,
				entity.getX(), entity.getY() + h * 0.9, entity.getZ(),
				3, w * 0.4, h * 0.12, w * 0.4, 0.0);
	}

	/**
	 * Spider-Man moves through his own webbing as though it were not there. The entity-side half of
	 * this (not being stuck by the cobweb blocks themselves) lives in {@code EntityWebMixin}.
	 */
	public static boolean movesFreelyThroughWebbing(Entity entity) {
		return entity instanceof Player player && SpiderMan.hasPower(player);
	}

	/**
	 * Drop everything, without restoring nets. Called only once the owning server has already stopped,
	 * where restoring would be meaningless and holding the entries would pin the dead level forever.
	 * The blocks themselves are owned by {@link TempBlocks}, which is cleared alongside this.
	 */
	public static void clearSessionState() {
		NETS.clear();
		COCOONED.clear();
		WEB_STICK.clear();
	}

	/** Expire cocoon / stickiness markers even when nothing else is running -- see {@code ServerStateReset}. */
	public static void pruneExpired(long now) {
		if (!COCOONED.isEmpty()) {
			COCOONED.values().removeIf(expiry -> expiry <= now);
		}
		if (!WEB_STICK.isEmpty()) {
			WEB_STICK.values().removeIf(e -> e[1] <= now);
		}
	}

	/** Live net count, for tests and diagnostics. */
	public static int activeNetCount() {
		return NETS.size();
	}

	/** Live cocoon count, for tests and diagnostics. */
	public static int activeCocoonCount() {
		return COCOONED.size();
	}

	/** Forget every net and cocoon belonging to {@code player} -- called when they disconnect. */
	public static void clearFor(ServerPlayer player) {
		NETS.removeIf(net -> net.ownerId() == player.getId());
	}
}
