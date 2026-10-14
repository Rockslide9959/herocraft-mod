package com.projecthero.mod.spider.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;

import com.projecthero.mod.network.SpiderWebAlarmPayload;
import com.projecthero.mod.spider.SpiderManConfig;
import com.projecthero.mod.spider.SpiderWebs;
import com.projecthero.mod.spider.entity.SpiderEntityTypes;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * v0.16.1: the registry and the rules of Spider-Man's web structures ({@link WebStructureEntity}).
 *
 * <p>The registry is two plain sets (server / client side) of the live web entities; queries filter by level and prune
 * anything removed, and {@link #clearSessionState} (from {@code ServerStateReset}) drops the server set, so nothing here
 * can outlive a world. Webs are few by construction ({@code maxStructuresPerPlayer}), so every query is a short loop
 * with an AABB reject first.
 */
public final class WebStructures {
	private static final Set<WebStructureEntity> SERVER = ConcurrentHashMap.newKeySet();
	private static final Set<WebStructureEntity> CLIENT = ConcurrentHashMap.newKeySet();

	/**
	 * Client hook: is the local player allowed onto a web owned by this UUID (themselves, or a squadmate per the
	 * client's squad roster)? Set by the client initialiser; the server uses {@code SquadManager} directly.
	 */
	public static volatile BiPredicate<Entity, UUID> clientMayUse = (e, owner) -> e.getUUID().equals(owner);

	private WebStructures() {
	}

	static void track(WebStructureEntity web) {
		(web.level().isClientSide ? CLIENT : SERVER).add(web);
	}

	public static void clearSessionState() {
		SERVER.clear();
		PULLED_FREE.clear();
	}

	/** v0.16.2: entity id -> game time until which webs let it slip through (just pulled off one). Server only. */
	private static final java.util.Map<Integer, Long> PULLED_FREE = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long PULL_GRACE_TICKS = 20L;

	/** True while {@code e} was just pulled off a web and webs must not grab it again. */
	public static boolean pulledFree(Entity e) {
		Long until = PULLED_FREE.get(e.getId());
		if (until == null) {
			return false;
		}
		if (e.level().getGameTime() >= until) {
			PULLED_FREE.remove(e.getId());
			return false;
		}
		return true;
	}

	public static void clearClient() {
		CLIENT.clear();
	}

	/** Every live web in {@code level} (pruning dead and stale entries as it goes). */
	public static List<WebStructureEntity> in(Level level) {
		Set<WebStructureEntity> set = level.isClientSide ? CLIENT : SERVER;
		if (set.isEmpty()) {
			return Collections.emptyList();
		}
		List<WebStructureEntity> out = new ArrayList<>();
		for (WebStructureEntity w : set) {
			if (w.isRemoved() || (level.isClientSide && w.level() != level)) {
				set.remove(w);
				continue;
			}
			if (w.level() == level && w.geometry() != null) {
				out.add(w);
			}
		}
		return out;
	}

	/** Webs owned by {@code owner} on the server, oldest first. */
	public static List<WebStructureEntity> ownedBy(UUID owner) {
		List<WebStructureEntity> out = new ArrayList<>();
		for (WebStructureEntity w : SERVER) {
			if (!w.isRemoved() && owner.equals(w.owner())) {
				out.add(w);
			}
		}
		out.sort((a, b) -> Long.compare(a.bornAt(), b.bornAt()));
		return out;
	}

	// ---------------- who may use a web ----------------

	/** Spider-Man himself and (players in) his squad can stand on, climb and swing from his webs; nobody else. */
	public static boolean mayUse(Entity e, UUID owner) {
		if (owner == null || !(e instanceof Player)) {
			return false;
		}
		if (e.getUUID().equals(owner)) {
			return true;
		}
		if (e.level().isClientSide) {
			return clientMayUse.test(e, owner);
		}
		var server = e.getServer();
		if (server == null) {
			return false;
		}
		ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(owner);
		if (ownerPlayer != null) {
			return com.projecthero.mod.squad.Squads.areAllies(ownerPlayer, e);
		}
		return com.projecthero.mod.squad.SquadManager.get(server).sameSquad(owner, e.getUUID());
	}

	// ---------------- collision ----------------

	/** The web boxes {@code e} may stand on inside {@code query} -- empty for anyone not allowed. */
	public static List<VoxelShape> collisionShapes(Entity e, AABB query) {
		if (!(e instanceof Player)) {
			return Collections.emptyList();
		}
		List<WebStructureEntity> webs = in(e.level());
		if (webs.isEmpty()) {
			return Collections.emptyList();
		}
		List<VoxelShape> out = null;
		boolean climbing = com.projecthero.mod.spider.SpiderClimb.attached((Player) e);
		// on a line (swinging, or hanging under a tightrope) the strands never snag him -- only a wall panel is solid
		boolean swinging = com.projecthero.mod.spider.SpiderSwing.isSwinging((Player) e);
		for (WebStructureEntity w : webs) {
			if (!w.geometry().bounds.intersects(query) || !mayUse(e, w.owner())) {
				continue;
			}
			if (out == null) {
				out = new ArrayList<>();
			}
			// crawling on a web it is solid from every side; walking, a strand is a one-way platform
			w.geometry().collisionBoxes(query, out, swinging ? Double.NEGATIVE_INFINITY
					: climbing ? Double.NaN : e.getBoundingBox().minY);
		}
		return out == null ? Collections.emptyList() : out;
	}

	/** Whether {@code box} is in contact with a web {@code e} may climb (the wall-crawl engine's grip test). */
	public static boolean gripAt(Entity e, AABB box) {
		for (WebStructureEntity w : in(e.level())) {
			if (w.geometry().bounds.intersects(box) && mayUse(e, w.owner())) {
				List<VoxelShape> shapes = new ArrayList<>();
				w.geometry().collisionBoxes(box, shapes, Double.NaN);
				if (!shapes.isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	/** A web {@code e} is touching but may NOT use, or null (strangers get stuck in these). */
	public static WebStructureEntity trappingWeb(Entity e) {
		for (WebStructureEntity w : in(e.level())) {
			if (w.geometry().touches(e.getBoundingBox()) && !mayUse(e, w.owner()) && !pulledFree(e)) {
				return w;
			}
		}
		return null;
	}

	/** The tightrope {@code player} is standing on (feet on it), or null. */
	public static WebStructureEntity ropeUnderfoot(Player player) {
		AABB feet = player.getBoundingBox();
		AABB probe = new AABB(feet.minX, feet.minY - 0.12, feet.minZ, feet.maxX, feet.minY + 0.05, feet.maxZ);
		for (WebStructureEntity w : in(player.level())) {
			if (w.kind() == WebGeometry.TIGHTROPE && w.geometry().bounds.intersects(probe) && mayUse(player, w.owner())) {
				List<VoxelShape> shapes = new ArrayList<>();
				w.geometry().collisionBoxes(probe, shapes, player.getBoundingBox().minY);
				if (!shapes.isEmpty()) {
					return w;
				}
			}
		}
		return null;
	}

	/** The nearest point on a web {@code player} may use within {@code radius} of {@code at}, or null. */
	public static Vec3 nearestPoint(Player player, Vec3 at, double radius) {
		Vec3 best = null;
		double bestD = radius * radius;
		for (WebStructureEntity w : in(player.level())) {
			if (!w.geometry().bounds.inflate(radius).contains(at) || !mayUse(player, w.owner())) {
				continue;
			}
			Vec3 c = w.geometry().closestPoint(at);
			if (c != null && c.distanceToSqr(at) <= bestD) {
				bestD = c.distanceToSqr(at);
				best = c;
			}
		}
		return best;
	}

	/**
	 * Web Swing onto a web: the best point on one of {@code player}'s usable webs above and ahead of him, scored with
	 * {@code scorer} (the block anchor search's own scoring), or null.
	 */
	public static Vec3 bestSwingPoint(Player player, Vec3 eye, java.util.function.ToDoubleFunction<Vec3> scorer, double[] scoreOut) {
		Vec3 best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (WebStructureEntity w : in(player.level())) {
			if (!mayUse(player, w.owner()) || w.geometry().bounds.getCenter().distanceTo(eye) > 64.0) {
				continue;
			}
			WebGeometry g = w.geometry();
			for (int i = 0; i < g.segmentCount(); i += Math.max(1, g.segmentCount() / 40)) {
				Vec3 a = g.a(i);
				Vec3 b = g.b(i);
				for (int k = 0; k <= 4; k++) {
					Vec3 p = a.lerp(b, k / 4.0);
					double s = scorer.applyAsDouble(p);
					if (s > bestScore) {
						bestScore = s;
						best = p;
					}
				}
			}
			if (g.wallBox != null) {
				Vec3 top = new Vec3(g.wallBox.getCenter().x, g.wallBox.maxY, g.wallBox.getCenter().z);
				double s = scorer.applyAsDouble(top);
				if (s > bestScore) {
					bestScore = s;
					best = top;
				}
			}
		}
		if (scoreOut != null) {
			scoreOut[0] = bestScore;
		}
		return best;
	}

	/** True when {@code point} sits on (within half a block of) a web -- a swing anchored there is still valid. */
	public static boolean onWeb(Level level, Vec3 point) {
		for (WebStructureEntity w : in(level)) {
			if (w.geometry().bounds.contains(point)) {
				Vec3 c = w.geometry().closestPoint(point);
				if (c != null && c.distanceToSqr(point) < 0.6 * 0.6) {
					return true;
				}
			}
		}
		return false;
	}

	/** v0.16.1: a strand of one of his (or his squad's) webs on the aim: the web, the exact point on the strand, the distance. */
	public record StrandHit(WebStructureEntity web, Vec3 point, double distance, int segment) {
	}

	/** The nearest usable web strand (tightrope or woven web -- never a wall panel) on {@code player}'s aim, or null. */
	public static StrandHit raycastStrand(Player player, double range) {
		return raycastStrand(player, range, 0.3);
	}

	/** v0.16.2: the same, with a custom aim tolerance {@code radius} (Web Pin is generous: a strand near the aim counts). */
	public static StrandHit raycastStrand(Player player, double range, double radius) {
		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getLookAngle();
		Vec3 to = eye.add(dir.scale(range));
		StrandHit best = null;
		for (WebStructureEntity w : in(player.level())) {
			if (w.geometry().segmentCount() == 0 || !mayUse(player, w.owner())) {
				continue;
			}
			double t = w.geometry().strandRayHit(eye, to, radius);
			if (t < 0.0 || (best != null && t * range >= best.distance())) {
				continue;
			}
			Vec3 along = eye.add(dir.scale(t * range));
			int seg = w.geometry().nearestSegment(along);
			Vec3 p = WebGeometry.closestOnSegment(w.geometry().a(seg), w.geometry().b(seg), along);
			best = new StrandHit(w, p, p.distanceTo(eye), seg);
		}
		return best;
	}

	/**
	 * v0.16.1 the upside-down hang: the strand of one of his (or his squad's) tightropes / woven webs that {@code player}
	 * is standing on or clinging to -- its nearest point within reach of his feet or middle -- or null.
	 */
	public static StrandHit strandAtFeet(Player player) {
		Vec3 feet = player.position();
		Vec3 mid = feet.add(0, player.getBbHeight() * 0.5, 0);
		StrandHit best = null;
		for (WebStructureEntity w : in(player.level())) {
			if ((w.kind() != WebGeometry.TIGHTROPE && w.kind() != WebGeometry.WEAVE) || w.geometry().segmentCount() == 0
					|| !mayUse(player, w.owner()) || !w.geometry().bounds.inflate(1.5).contains(mid)) {
				continue;
			}
			for (Vec3 probe : new Vec3[] {feet, mid}) {
				int seg = w.geometry().nearestSegment(probe);
				if (seg < 0) {
					continue;
				}
				Vec3 p = WebGeometry.closestOnSegment(w.geometry().a(seg), w.geometry().b(seg), probe);
				double d = p.distanceTo(probe);
				if (d <= 1.1 && (best == null || d < best.distance())) {
					best = new StrandHit(w, p, d, seg);
				}
			}
		}
		return best;
	}

	/** Every tightrope {@code player} is standing on (at a junction there are two or more). */
	public static List<WebStructureEntity> ropesUnderfoot(Player player) {
		AABB feet = player.getBoundingBox();
		AABB probe = new AABB(feet.minX, feet.minY - 0.12, feet.minZ, feet.maxX, feet.minY + 0.05, feet.maxZ);
		List<WebStructureEntity> out = new ArrayList<>();
		for (WebStructureEntity w : in(player.level())) {
			if (w.kind() == WebGeometry.TIGHTROPE && w.geometry().bounds.intersects(probe) && mayUse(player, w.owner())) {
				List<VoxelShape> shapes = new ArrayList<>();
				w.geometry().collisionBoxes(probe, shapes, player.getBoundingBox().minY);
				if (!shapes.isEmpty()) {
					out.add(w);
				}
			}
		}
		return out;
	}

	// ---------------- spawning / removing ----------------

	/** Build a web for {@code owner}, enforcing his cap (his oldest web goes first). */
	public static WebStructureEntity spawn(ServerPlayer owner, WebGeometry geometry, Vec3 at) {
		ServerLevel level = (ServerLevel) owner.level();
		List<WebStructureEntity> mine = ownedBy(owner.getUUID());
		int cap = Math.max(1, SpiderManConfig.webs().maxStructuresPerPlayer);
		for (int i = 0; i <= mine.size() - cap; i++) {
			mine.get(i).dissolve(false);
		}
		WebStructureEntity web = new WebStructureEntity(SpiderEntityTypes.WEB_STRUCTURE, level);
		web.setPos(at.x, at.y, at.z);
		web.setOwner(owner.getUUID());
		long now = level.getGameTime();
		web.setLifetime(now, now + Math.max(20L, SpiderManConfig.webs().fadeSeconds * 20L));
		web.setGeometry(geometry);
		level.addFreshEntity(web);
		return web;
	}

	/** Spider-Man attacks one of his own webs: the first of his webs on his aim within 6 blocks is cut. */
	public static boolean cut(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = eye.add(player.getLookAngle().scale(6.0));
		WebStructureEntity best = null;
		double bestT = Double.MAX_VALUE;
		for (WebStructureEntity w : in(player.level())) {
			if (!player.getUUID().equals(w.owner())) {
				continue;
			}
			double t = w.geometry().rayHit(eye, to, 0.35);
			if (t >= 0.0 && t < bestT) {
				bestT = t;
				best = w;
			}
		}
		if (best == null) {
			return false;
		}
		best.dissolve(true);
		if (!com.projecthero.mod.spider.SpiderMoves.soundsOff(player)) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHEEP_SHEAR,
					SoundSource.PLAYERS, 0.8f, 1.3f);
		}
		return true;
	}

	// ---------------- v0.16.1: pulled free (the stuck-pull rule) ----------------

	/**
	 * A Spider-Man pull (Web Yank, Grab &amp; Hurl, Swing Throw, Web Whip) on {@code e}: if it is stuck on one of the web
	 * structures (a tightrope, a woven web, a wall -- anything it is not allowed on) or in his temporary cobwebs, or wrapped
	 * on a Weave web, it is torn loose: every web holding it snaps (dissolved, with a small tear -- a few web shreds and a
	 * snap) and its stuck-in-web slowdown for this tick is cleared, so the pull's velocity actually moves it. Returns true
	 * when anything was torn.
	 */
	public static boolean tearFree(Entity e) {
		if (!(e.level() instanceof ServerLevel level)) {
			return false;
		}
		boolean freed = com.projecthero.mod.spider.SpiderWrap.release(e, false);
		Vec3 centre = e.position().add(0, e.getBbHeight() * 0.5, 0);
		AABB box = e.getBoundingBox().inflate(0.1);
		for (WebStructureEntity w : in(level)) {
			if (w.isRemoved() || !w.geometry().bounds.intersects(box) || mayUse(e, w.owner()) || !w.geometry().touches(box)) {
				continue;
			}
			// v0.16.2 (user): the tightrope / woven web stays up -- the target is just pulled off it, and slips through
			// the webs for a moment so they don't catch it again on the way out
			Vec3 at = w.geometry().closestPoint(centre);
			tearEffect(level, at == null ? centre : at);
			freed = true;
		}
		if (freed) {
			PULLED_FREE.put(e.getId(), level.getGameTime() + PULL_GRACE_TICKS);
		}
		if (com.projecthero.mod.spider.SpiderCobwebs.tearIn(level, e.getBoundingBox()) > 0) {
			tearEffect(level, centre);
			freed = true;
		}
		if (freed) {
			// Entity.move applies (and then clears) the stuck multiplier set by the web's tick this tick -- zero it now
			e.makeStuckInBlock(Blocks.AIR.defaultBlockState(), Vec3.ZERO);
		}
		return freed;
	}

	/** The web tearing: a few shreds of web and a snap. */
	public static void tearEffect(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 6, 0.15, 0.15, 0.15, 0.06);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.9f, 1.5f);
	}

	// ---------------- v0.16.1: what holds a body (the Web Shot cocoon) ----------------

	/** The tightrope or woven web {@code e} is stuck on (touching, and not one it may use), or null. */
	public static WebStructureEntity strandHolding(Entity e) {
		for (WebStructureEntity w : in(e.level())) {
			if ((w.kind() == WebGeometry.WEAVE || w.kind() == WebGeometry.TIGHTROPE) && w.geometry().touches(e.getBoundingBox())
					&& !mayUse(e, w.owner())) {
				return w;
			}
		}
		return null;
	}

	// ---------------- v0.16.1: Swing Throw onto a Weave web ----------------

	/**
	 * Swing Throw aim assist: the point on a Weave web that {@code player} is aiming at -- straight down the aim, or the
	 * nearest web point inside a {@code coneDeg} cone -- within {@code range}, or null.
	 */
	public static Vec3 weaveAimPoint(Player player, double range, double coneDeg) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 best = null;
		double bestAngle = Math.toRadians(coneDeg);
		for (WebStructureEntity w : in(player.level())) {
			if (w.kind() != WebGeometry.WEAVE || w.geometry().segmentCount() == 0
					|| w.geometry().bounds.getCenter().distanceTo(eye) > range + 8.0) {
				continue;
			}
			double t = w.geometry().strandRayHit(eye, eye.add(look.scale(range)), 0.8);
			if (t >= 0.0) {
				Vec3 p = eye.add(look.scale(t * range));
				Vec3 c = w.geometry().closestPoint(p);
				return c != null ? c : p;
			}
			Vec3 centre = w.geometry().bounds.getCenter();
			double along = Math.max(1.0, centre.subtract(eye).dot(look));
			Vec3 p = w.geometry().closestPoint(eye.add(look.scale(along)));
			if (p == null || p.distanceTo(eye) > range) {
				continue;
			}
			double angle = Math.acos(Math.max(-1.0, Math.min(1.0, p.subtract(eye).normalize().dot(look))));
			if (angle <= bestAngle) {
				bestAngle = angle;
				best = p;
			}
		}
		return best;
	}

	/**
	 * A thrown body moving {@code from -> to} (its centre) this tick: if that passes through a Weave web it is not allowed
	 * on, it is caught right there -- set on the strand and stuck fast, exactly as if it had walked in. Returns the web, or
	 * null.
	 */
	public static WebStructureEntity catchOnWeave(LivingEntity e, Vec3 from, Vec3 to) {
		double radius = 0.45 + e.getBbWidth() * 0.5;
		for (WebStructureEntity w : in(e.level())) {
			if (w.kind() != WebGeometry.WEAVE || mayUse(e, w.owner())
					|| !w.geometry().bounds.inflate(radius + from.distanceTo(to)).contains(from.lerp(to, 0.5))) {
				continue;
			}
			double t = w.geometry().rayHit(from, to, radius);
			if (t < 0.0) {
				continue;
			}
			Vec3 at = from.lerp(to, t);
			e.teleportTo(at.x, at.y - e.getBbHeight() * 0.5, at.z);
			e.setDeltaMovement(Vec3.ZERO);
			e.hurtMarked = true;
			e.resetFallDistance();
			PULLED_FREE.remove(e.getId()); // v0.16.2: thrown onto a web on purpose -- it sticks, grace or not
			e.makeStuckInBlock(Blocks.STRUCTURE_VOID.defaultBlockState(),
					new Vec3(SpiderManConfig.webs().strangerSlowFactor, 0.04, SpiderManConfig.webs().strangerSlowFactor));
			if (e.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.02);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
			}
			return w;
		}
		return null;
	}

	// ---------------- per-web server tick ----------------

	static void serverTick(ServerLevel level, WebStructureEntity web) {
		WebGeometry g = web.geometry();
		UUID owner = web.owner();
		double slow = SpiderManConfig.webs().strangerSlowFactor;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, g.bounds,
				x -> x.isAlive() && !x.isSpectator())) {
			if (mayUse(e, owner) || SpiderWebs.ignoresWebs(e) || pulledFree(e) || !g.touches(e.getBoundingBox())) {
				continue;
			}
			// stuck fast: far worse than a cobweb (players are slowed by their own client, see SpiderTraversalClient)
			// not COBWEB: EntityWebMixin lets Spider-Men and the Hulk wade through cobweb, but his web holds everyone not allowed
			e.makeStuckInBlock(Blocks.STRUCTURE_VOID.defaultBlockState(), new Vec3(slow, 0.04, slow));
			e.resetFallDistance();
			// v0.16.1: and the web debuff stays on them 18 s after they get loose (a boss: the short weak version)
			if (level.getGameTime() % 20 == 0) {
				com.projecthero.mod.hero.power.AbilityHelpers.applyControl(e, net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
						SpiderWebs.debuffTicks(e), SpiderWebs.isBoss(e) ? 0 : 2);
			}
			if (e instanceof net.minecraft.world.entity.Mob mob && level.getGameTime() % 10 == 0) {
				mob.getNavigation().stop();
			}
			if (!com.projecthero.mod.spider.SpiderWrap.isWrapped(e)) {
				// v0.16.2 (user): a mob he has already cocooned on his web is caught -- it never raises the alarm
				alarm(level, web, e.position().add(0, e.getBbHeight() * 0.5, 0));
			}
		}
		if (g.wallBox != null) {
			blockProjectiles(level, web, g.wallBox);
		}
		if (level.getGameTime() % 40 == 0 && web.expiresAt() - level.getGameTime() < 200) {
			// the last ten seconds: a few drifting strands as it frays
			Vec3 c = g.bounds.getCenter();
			level.sendParticles(ParticleTypes.ITEM_COBWEB, c.x, c.y, c.z, 4, g.bounds.getXsize() * 0.25,
					g.bounds.getYsize() * 0.25, g.bounds.getZsize() * 0.25, 0.0);
		}
	}

	/** A web wall stops every projectile not fired by its owner or his squad. */
	private static void blockProjectiles(ServerLevel level, WebStructureEntity web, AABB wall) {
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, wall.inflate(4.0), Entity::isAlive)) {
			Entity shooter = p.getOwner();
			if (shooter != null && mayUse(shooter, web.owner())) {
				continue;
			}
			Vec3 from = p.position();
			Vec3 to = from.add(p.getDeltaMovement());
			if (wall.inflate(0.2).contains(from) || wall.inflate(0.2).clip(from, to).isPresent()) {
				level.sendParticles(ParticleTypes.ITEM_COBWEB, from.x, from.y, from.z, 8, 0.15, 0.15, 0.15, 0.02);
				level.playSound(null, from.x, from.y, from.z, SoundEvents.WOOL_HIT, SoundSource.PLAYERS, 0.7f, 0.8f);
				p.discard();
				alarm(level, web, from);
			}
		}
	}

	/** Ping the owner's Spider-Sense (throttled per web): a sound and a HUD arrow at {@code at}. */
	static void alarm(ServerLevel level, WebStructureEntity web, Vec3 at) {
		long now = level.getGameTime();
		if (now - web.lastAlarm < SpiderManConfig.webs().alarmCooldownTicks || web.owner() == null) {
			return;
		}
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(web.owner());
		if (owner == null || owner.level() != level) {
			return;
		}
		double range = SpiderManConfig.webs().alarmRange;
		if (owner.position().distanceToSqr(at) > range * range) {
			return;
		}
		web.lastAlarm = now;
		web.lastAlarmAt = at;
		if (ServerPlayNetworking.canSend(owner, SpiderWebAlarmPayload.TYPE)) {
			ServerPlayNetworking.send(owner, new SpiderWebAlarmPayload(at.x, at.y, at.z));
		}
	}
}
