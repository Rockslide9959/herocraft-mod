package com.projecthero.mod.shazam.journey;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.thoracquire.Valor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: <b>the illusions</b>. Once a player is <b>worthy</b> ({@link #worthy}: full {@link Valor} and
 * no power at all -- the Wizard chooses the pure of heart; being chosen spends no Valor), every minute there is a small chance
 * ({@link #chance}: 4%, 8% at night, +4% in a City) that a ghostly <b>subway stairway</b> appears a short way ahead of them,
 * in view, on open ground: stone steps going down between railings, under a shimmering arch with old "SUBWAY" signage. Only
 * that player is sent it (the client draws it, flickering and whispering). It fades after {@link #LIFE} ticks or once they're
 * {@link #FADE_DISTANCE} blocks away; walking up to it ({@link #APPROACH} blocks) starts the journey
 * ({@link ShazamJourney#begin}). After one, the next can't come for {@link #COOLDOWN} ticks.
 *
 * <p>The live illusions are a static map of player UUIDs (no worlds or entities), cleared by {@code ServerStateReset}.
 */
public final class ShazamIllusions {
	public static final int ROLL_EVERY = 1200;
	public static final int COOLDOWN = 6000;
	public static final int LIFE = 600;
	public static final double APPROACH = 2.0;
	public static final double FADE_DISTANCE = 32.0;
	public static final double BASE_CHANCE = 0.04;
	public static final double NIGHT_BONUS = 0.04;
	public static final double CITY_BONUS = 0.04;

	/** Test hook: a forced chance (1 = always), or null. */
	public static Double chanceOverride;

	/** A live illusion: where it stands, which way it descends, when it came, and where its player stood when it did. */
	public record Live(ResourceKey<Level> dimension, Vec3 pos, float yaw, long start, JourneyPayloads.Return seenFrom) {
	}

	private static final Map<UUID, Live> LIVE = new ConcurrentHashMap<>();

	private ShazamIllusions() {
	}

	public static void clearSessionState() {
		LIVE.clear();
		chanceOverride = null;
	}

	public static Live live(ServerPlayer player) {
		return LIVE.get(player.getUUID());
	}

	/** Worthy in the Wizard's eyes: full Valor, and no power of any kind. */
	public static boolean worthy(ServerPlayer player) {
		return Valor.isFull(player) && !HeroTiers.hasAnyPower(player) && !Shazam.hasPower(player) && !Shazam.isFamily(player);
	}

	/** This minute's chance for {@code player}. */
	public static double chance(ServerPlayer player) {
		if (chanceOverride != null) {
			return chanceOverride;
		}
		ServerLevel level = player.serverLevel();
		double c = BASE_CHANCE;
		if (level.isNight()) {
			c += NIGHT_BONUS;
		}
		try {
			if (com.projecthero.mod.city.CityLocator.isCityChunk(level, player.chunkPosition().toLong())) {
				c += CITY_BONUS;
			}
		} catch (RuntimeException ignored) {
			// no City data: no bonus
		}
		return c;
	}

	/** May an illusion appear to {@code player} right now (worthy, on foot in the Overworld, not busy)? */
	public static boolean canAppear(ServerPlayer player) {
		return worthy(player) && player.isAlive() && !player.isSpectator() && player.level().dimension() == Level.OVERWORLD
				&& player.onGround() && !player.isPassenger() && !player.isInWater() && !player.isSleeping() && !player.isFallFlying()
				&& !ShazamJourney.inJourney(player) && !LIVE.containsKey(player.getUUID());
	}

	// ---------------------------------------------------------------- tick

	public static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 20L == 0L) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				long ready = p.getAttachedOrElse(ShazamJourney.ILLUSION_READY, 0L);
				if (now < ready || !canAppear(p)) {
					continue;
				}
				roll(p, now);
			}
		}
		if (LIVE.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Live>> it = LIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Live> e = it.next();
			Live l = e.getValue();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null) {
				it.remove();
				continue;
			}
			boolean gone = p.level().dimension() != l.dimension() || now - l.start() > LIFE || !worthy(p) || !p.isAlive()
					|| flatDistance(p.position(), l.pos()) > FADE_DISTANCE;
			if (gone) {
				it.remove();
				end(p, l, now);
				continue;
			}
			if (flatDistance(p.position(), l.pos().add(Vec3.directionFromRotation(0, l.yaw()).scale(0.6))) <= APPROACH
					&& Math.abs(p.getY() - l.pos().y) <= 2.5 && !ShazamJourney.inJourney(p)) {
				it.remove();
				ShazamJourney.begin(p, l);
			}
		}
	}

	/** One roll: if it hits, an illusion appears ahead of the player (true). */
	public static boolean roll(ServerPlayer player, long now) {
		if (player.getRandom().nextDouble() >= chance(player)) {
			player.setAttached(ShazamJourney.ILLUSION_READY, now + ROLL_EVERY);
			return false;
		}
		Live l = place(player, now);
		if (l == null) {
			player.setAttached(ShazamJourney.ILLUSION_READY, now + 200); // nowhere to put it: try again in 10 s
			return false;
		}
		player.setAttached(ShazamJourney.ILLUSION_READY, now + COOLDOWN);
		return true;
	}

	/** Test hook: forces an illusion right now (no roll); null if there's nowhere in view to put it. */
	public static Live forceAppear(ServerPlayer player) {
		long now = player.level().getGameTime();
		Live l = place(player, now);
		if (l != null) {
			player.setAttached(ShazamJourney.ILLUSION_READY, now + COOLDOWN);
		}
		return l;
	}

	/** Test hook: puts the illusion exactly at {@code pos} descending toward {@code yaw}. */
	public static Live showAt(ServerPlayer player, Vec3 pos, float yaw) {
		long now = player.level().getGameTime();
		Live l = new Live(player.level().dimension(), pos, yaw, now, ShazamJourney.returnOf(player));
		LIVE.put(player.getUUID(), l);
		send(player, l, true, 0);
		return l;
	}

	private static Live place(ServerPlayer player, long now) {
		ServerLevel level = player.serverLevel();
		Vec3 eye = player.getEyePosition();
		float baseYaw = player.getYRot();
		for (int d : new int[] { 9, 8, 10, 11, 7, 12 }) {
			for (int off : new int[] { 0, -12, 12, -24, 24, -34, 34 }) {
				float yaw = baseYaw + off;
				Vec3 dir = Vec3.directionFromRotation(0, yaw);
				Vec3 probe = player.position().add(dir.scale(d));
				Vec3 spot = ground(level, probe, (int) Math.floor(player.getY()));
				if (spot == null || !footprintClear(level, spot, yaw)) {
					continue;
				}
				// in plain sight
				Vec3 target = spot.add(0, 1.2, 0);
				if (level.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player)).getType() != HitResult.Type.MISS) {
					continue;
				}
				// the stairs descend away from the player
				float descend = (float) (Mth.atan2(-(spot.x - player.getX()), spot.z - player.getZ()) * Mth.RAD_TO_DEG);
				Live l = new Live(level.dimension(), spot, descend, now, ShazamJourney.returnOf(player));
				LIVE.put(player.getUUID(), l);
				send(player, l, true, 0);
				return l;
			}
		}
		return null;
	}

	/** The top of the solid ground near {@code probe} (within 4 of {@code y}), with two blocks of air over it, or null. */
	private static Vec3 ground(ServerLevel level, Vec3 probe, int y) {
		int x = Mth.floor(probe.x), z = Mth.floor(probe.z);
		for (int dy = 3; dy >= -4; dy--) {
			BlockPos below = new BlockPos(x, y + dy - 1, z);
			BlockPos at = below.above();
			if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) && level.getBlockState(at).getCollisionShape(level, at).isEmpty()
					&& level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty() && level.getFluidState(at).isEmpty()) {
				return new Vec3(x + 0.5, at.getY(), z + 0.5);
			}
		}
		return null;
	}

	/** The 3 x 5 footprint descending toward {@code yaw}: ground within a block of the entrance's and room above. */
	private static boolean footprintClear(ServerLevel level, Vec3 spot, float yaw) {
		Vec3 f = Vec3.directionFromRotation(0, yaw), r = new Vec3(-f.z, 0, f.x);
		for (int a = 0; a <= 4; a += 2) {
			for (int b = -1; b <= 1; b++) {
				Vec3 q = spot.add(f.scale(a)).add(r.scale(b));
				BlockPos at = BlockPos.containing(q.x, spot.y, q.z);
				boolean ok = false;
				for (int dy = -1; dy <= 1 && !ok; dy++) {
					BlockPos p = at.offset(0, dy, 0);
					ok = level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP) && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
							&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty();
				}
				if (!ok) {
					return false;
				}
			}
		}
		return true;
	}

	private static void end(ServerPlayer p, Live l, long now) {
		send(p, l, false, (int) (now - l.start()));
	}

	/** Takes {@code player}'s illusion away (it fades on their screen). */
	public static void dismiss(ServerPlayer player) {
		Live l = LIVE.remove(player.getUUID());
		if (l != null) {
			end(player, l, player.level().getGameTime());
		}
	}

	static void send(ServerPlayer p, Live l, boolean active, int age) {
		if (p.connection != null && ServerPlayNetworking.canSend(p, JourneyPayloads.Illusion.TYPE)) {
			ServerPlayNetworking.send(p, new JourneyPayloads.Illusion(active, l.pos().x, l.pos().y, l.pos().z, l.yaw(), age));
		}
	}

	static double flatDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
