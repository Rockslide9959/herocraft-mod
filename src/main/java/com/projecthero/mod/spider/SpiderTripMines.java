package com.projecthero.mod.spider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.spider.entity.SpiderTripMineEntity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: the Spider-Man Suit v2's Trip Mine (Shift+C, replacing the Web Wall while the v2 suit is worn).
 *
 * <ul>
 *   <li><b>Placing:</b> Shift+C sticks a mine on the block face he is looking at (within {@link #PLACE_RANGE} blocks),
 *       or, looking at nothing, on the ground at his feet. {@link #COST} webbing, {@link #COOLDOWN} ticks.</li>
 *   <li><b>The laser:</b> a thin web line straight out from the surface, {@link #BEAM_RANGE} blocks or to the first
 *       block (re-measured every half-second, so a block put in its way cuts it short).</li>
 *   <li><b>Triggering:</b> the first living thing to cross it that he could hurt -- not him, not his squad, not a boss
 *       -- is reeled onto the mine's surface and cocooned there ({@link SpiderWrap#wrapFor}, {@link #COCOON_TICKS}).
 *       The mine is spent.</li>
 *   <li><b>Limits:</b> a mine lasts {@link #LIFETIME_TICKS} (10 minutes); sneak + right-click picks it back up (the
 *       webbing comes back); he can have {@link #MAX_PER_PLAYER} out -- a seventh removes the oldest.</li>
 * </ul>
 */
public final class SpiderTripMines {
	public static final String TRIP_MINE = "trip_mine";
	public static final double PLACE_RANGE = 5.0;
	public static final double BEAM_RANGE = 8.0;
	public static final float COST = 10.0f;
	public static final int COOLDOWN = 40;
	public static final int LIFETIME_TICKS = 10 * 60 * 20;
	public static final int MAX_PER_PLAYER = 6;
	public static final int COCOON_TICKS = 500;
	/** Reeling a caught creature onto the mine: this fast (blocks / tick), at most this long. */
	public static final double REEL_SPEED = 1.4;
	public static final int REEL_MAX_TICKS = 14;

	public static final EntityType<SpiderTripMineEntity> TRIP_MINE_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("spider_trip_mine"),
			FabricEntityTypeBuilder.<SpiderTripMineEntity>create(MobCategory.MISC, SpiderTripMineEntity::new)
					.dimensions(EntityDimensions.fixed(SpiderTripMineEntity.SIZE, SpiderTripMineEntity.SIZE))
					.trackRangeBlocks(96)
					.trackedUpdateRate(10)
					.fireImmune()
					.disableSummon()
					.build());

	/** Each player's mines, oldest first (UUIDs, so a mine in an unloaded chunk still counts). */
	private static final Map<UUID, Deque<UUID>> OWNED = new ConcurrentHashMap<>();

	private SpiderTripMines() {
	}

	public static void initialize() {
		// loading the class registers the entity type
	}

	public static void clearSessionState() {
		OWNED.clear();
	}

	/** How many mines {@code owner} has out (as far as this session knows). */
	public static int count(UUID owner) {
		Deque<UUID> q = OWNED.get(owner);
		return q == null ? 0 : q.size();
	}

	// ---------------- placing ----------------

	/** Shift+C in the v2 suit. Returns the mine placed, or null. */
	public static SpiderTripMineEntity place(ServerPlayer player) {
		if (!SpiderAbilities.gate(player, TRIP_MINE, COST)) {
			return null;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(PLACE_RANGE)),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			// nothing in reach: the ground at his feet
			Vec3 feet = player.position().add(0, 0.1, 0);
			hit = level.clip(new ClipContext(feet, feet.subtract(0, 3.0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
					player));
			if (hit.getType() != HitResult.Type.BLOCK) {
				player.displayClientMessage(Component.translatable("message.projecthero.spider_man.trip_mine.no_surface"), true);
				return null;
			}
		}
		SpiderTripMineEntity mine = placeAt(player, hit.getLocation(), hit.getDirection());
		SpiderMoves.anim(player, SpiderAnim.WALL_CAST);
		SpiderMoves.thwip(player, 1.2f);
		SpiderAbilities.fired(player, TRIP_MINE, COST, COOLDOWN);
		return mine;
	}

	/** Stick one of {@code owner}'s mines on the face point {@code at} (no cost, no cooldown -- the tests use it too). */
	public static SpiderTripMineEntity placeAt(ServerPlayer owner, Vec3 at, Direction face) {
		ServerLevel level = (ServerLevel) owner.level();
		SpiderTripMineEntity mine = new SpiderTripMineEntity(TRIP_MINE_ENTITY, level);
		mine.setOwner(owner.getUUID());
		mine.place(at.add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.01)), face);
		mine.setBeamLength(mine.measureBeam());
		level.addFreshEntity(mine);
		Deque<UUID> q = OWNED.computeIfAbsent(owner.getUUID(), k -> new ArrayDeque<>());
		synchronized (q) {
			q.addLast(mine.getUUID());
			while (q.size() > MAX_PER_PLAYER) {
				UUID oldest = q.pollFirst();
				Entity e = find(level, oldest);
				if (e != null) {
					e.discard();
				}
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.6f, 1.8f);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.02);
		return mine;
	}

	private static Entity find(ServerLevel any, UUID id) {
		for (ServerLevel l : any.getServer().getAllLevels()) {
			Entity e = l.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	/** A mine is gone (spent, picked up, expired, knocked off, replaced): stop counting it. */
	public static void forget(SpiderTripMineEntity mine) {
		if (mine.owner() == null) {
			return;
		}
		Deque<UUID> q = OWNED.get(mine.owner());
		if (q != null) {
			synchronized (q) {
				q.remove(mine.getUUID());
			}
		}
	}

	// ---------------- picking up / breaking ----------------

	/** Sneak + right-click: his own mine comes back, and so does its webbing. */
	public static boolean pickUp(ServerPlayer player, SpiderTripMineEntity mine) {
		if (mine.isRemoved() || mine.owner() == null || !mine.owner().equals(player.getUUID())) {
			return false;
		}
		if (SpiderMan.hasPower(player)) {
			SpiderMan.setWebReserve(player, Math.min(SpiderWebReserve.maxFor(player), SpiderWebReserve.get(player) + COST));
		}
		Vec3 a = mine.anchor();
		player.level().playSound(null, a.x, a.y, a.z, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6f, 1.6f);
		mine.discard();
		player.displayClientMessage(Component.translatable("message.projecthero.spider_man.trip_mine.picked_up"), true);
		return true;
	}

	public static void breakMine(ServerLevel level, SpiderTripMineEntity mine) {
		Vec3 a = mine.anchor();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, a.x, a.y, a.z, 10, 0.15, 0.15, 0.15, 0.05);
		level.playSound(null, a.x, a.y, a.z, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 1.4f);
		mine.discard();
	}

	// ---------------- the laser ----------------

	public static void tick(ServerLevel level, SpiderTripMineEntity mine) {
		mine.age++;
		if (mine.age > LIFETIME_TICKS) {
			breakMine(level, mine);
			return;
		}
		// still stuck to something? (the block it was on is gone -> it falls off)
		Direction face = mine.face();
		BlockPos on = BlockPos.containing(mine.anchor().subtract(Vec3.atLowerCornerOf(face.getNormal()).scale(0.1)));
		if (mine.age % 10 == 0 && level.getBlockState(on).getCollisionShape(level, on).isEmpty()) {
			breakMine(level, mine);
			return;
		}
		int reel = mine.reelTarget();
		if (reel >= 0) {
			tickReel(level, mine, level.getEntity(reel));
			return;
		}
		if (mine.age % 10 == 0) {
			mine.setBeamLength(mine.measureBeam());
		}
		LivingEntity crossing = crossing(level, mine);
		if (crossing != null) {
			catchTarget(level, mine, crossing);
		}
	}

	/** The first living thing crossing the laser that the mine may catch, or null. */
	public static LivingEntity crossing(ServerLevel level, SpiderTripMineEntity mine) {
		Vec3 start = mine.beamStart();
		Vec3 end = mine.beamEnd();
		if (start.distanceToSqr(end) < 1.0e-4) {
			return null;
		}
		ServerPlayer owner = mine.owner() == null ? null : level.getServer().getPlayerList().getPlayer(mine.owner());
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(1.0),
				e -> catchable(owner, mine.owner(), e));
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : near) {
			AABB box = e.getBoundingBox();
			var clip = box.clip(start, end);
			if (box.contains(start) || clip.isPresent()) {
				double d = clip.map(v -> v.distanceToSqr(start)).orElse(0.0);
				if (d < bestD) {
					bestD = d;
					best = e;
				}
			}
		}
		return best;
	}

	/** May a mine owned by {@code owner} (online player, or null) / {@code ownerId} catch {@code e}? */
	public static boolean catchable(ServerPlayer owner, UUID ownerId, LivingEntity e) {
		if (!e.isAlive() || e.isSpectator() || e.getUUID().equals(ownerId) || SpiderWebs.isBoss(e) || SpiderWrap.isWrapped(e)
				|| e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
			return false;
		}
		if (owner == null) {
			return !(e instanceof Player); // his squad can't be told apart while he is away: players walk through
		}
		return HeroTargets.canHarm(owner, e) && !com.projecthero.mod.squad.Squads.areAllies(owner, e);
	}

	private static void catchTarget(ServerLevel level, SpiderTripMineEntity mine, LivingEntity target) {
		mine.setReelTarget(target.getId());
		mine.reelTicks = 0;
		com.projecthero.mod.spider.web.WebStructures.tearFree(target);
		Vec3 a = mine.anchor();
		level.playSound(null, a.x, a.y, a.z, SpiderSounds.THWIP, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, a.x, a.y, a.z, SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1.0f, 1.2f);
		level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.15f, 0.15f), 1.0f), a.x, a.y, a.z, 8, 0.1, 0.1,
				0.1, 0.0);
		tickReel(level, mine, target);
	}

	/** Where a caught creature ends up: pressed against the mine's surface. */
	public static Vec3 restingSpot(SpiderTripMineEntity mine, LivingEntity target) {
		Vec3 a = mine.anchor();
		Direction face = mine.face();
		Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
		Vec3 spot = switch (face) {
			case UP -> a;
			case DOWN -> a.subtract(0, target.getBbHeight(), 0);
			default -> a.add(n.scale(target.getBbWidth() * 0.5 + 0.05)).subtract(0, target.getBbHeight() * 0.5, 0);
		};
		// a low wall mine: lift the catch until its feet are clear of the floor (up to 2 blocks)
		if (face.getAxis().isHorizontal()) {
			for (int i = 0; i < 8; i++) {
				AABB box = target.getDimensions(target.getPose()).makeBoundingBox(spot);
				if (target.level().noCollision(target, box)) {
					break;
				}
				spot = spot.add(0, 0.25, 0);
			}
		}
		return spot;
	}

	private static void tickReel(ServerLevel level, SpiderTripMineEntity mine, Entity e) {
		if (!(e instanceof LivingEntity target) || !target.isAlive()) {
			breakMine(level, mine);
			return;
		}
		mine.reelTicks++;
		Vec3 dest = restingSpot(mine, target);
		Vec3 delta = dest.subtract(target.position());
		double dist = delta.length();
		if (dist <= REEL_SPEED || mine.reelTicks >= REEL_MAX_TICKS) {
			target.teleportTo(dest.x, dest.y, dest.z);
			target.setDeltaMovement(Vec3.ZERO);
			target.resetFallDistance();
			SpiderWrap.wrapFor(target, COCOON_TICKS);
			Vec3 a = mine.anchor();
			level.playSound(null, a.x, a.y, a.z, SpiderSounds.SPLAT, SoundSource.PLAYERS, 1.0f, 1.0f);
			mine.discard(); // spent
			return;
		}
		Vec3 step = delta.scale(REEL_SPEED / dist);
		target.teleportTo(target.getX() + step.x, target.getY() + step.y, target.getZ() + step.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		target.hurtMarked = true;
	}
}
