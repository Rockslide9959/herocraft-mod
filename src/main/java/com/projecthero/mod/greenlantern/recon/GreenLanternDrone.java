package com.projecthero.mod.greenlantern.recon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.greenlantern.GreenLantern;
import com.projecthero.mod.greenlantern.GreenLanternBattery;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternVisuals;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.network.GreenLanternReconPayload;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Hard-Light Drone ({@code ConstructType.HARD_LIGHT_DRONE}, Construct Menu &gt; Utility) -- a small recon /
 * support drone of hard light. The key deploys it ({@link GreenLanternReconConfig#DRONE_COST}) and dismisses it; it costs
 * {@link GreenLanternReconConfig#DRONE_UPKEEP_PER_SEC} a second while out. Shift + the key sends it on a scout run.
 * <ul>
 *   <li><b>Follow</b>: it hovers by the bearer's right shoulder and follows smoothly.</li>
 *   <li><b>Sweep</b>: every {@link GreenLanternReconConfig#DRONE_SCAN_INTERVAL_TICKS} ticks it sweeps
 *   {@link GreenLanternReconConfig#DRONE_SCAN_RADIUS} blocks and outlines the hostiles for the bearer alone (the Ring
 *   Scan's private glow, {@link GreenLanternReconPayload#MARKS}).</li>
 *   <li><b>Fetch</b>: dropped items and XP orbs within {@link GreenLanternReconConfig#DRONE_FETCH_RADIUS} of the bearer are
 *   carried to them, one at a time (never something the bearer threw away themselves).</li>
 *   <li><b>Zap</b>: a hostile within {@link GreenLanternReconConfig#DRONE_ZAP_RANGE} that is targeting the bearer takes a
 *   small hard-light bolt ({@link GreenLanternReconConfig#DRONE_ZAP_DAMAGE}) every 1.5 s, if the drone can see it.</li>
 *   <li><b>Scout run</b>: it flies to the spot under the crosshair (up to {@link GreenLanternReconConfig#SCOUT_RANGE}),
 *   hovers there 10 s sweeping {@link GreenLanternReconConfig#SCOUT_RADIUS} blocks (outlines + a summary popup:
 *   hostiles, chests, other creatures), then flies back.</li>
 * </ul>
 * It can be shot down ({@link GreenLanternReconConfig#DRONE_HP} HP; its Lantern and their squad can't hurt it) and
 * dissolves when the ring runs dry, on N, death, logout or a world change. One drone per Lantern ({@link #DRONES}, cleared on
 * server stop).
 */
public final class GreenLanternDrone {
	private static final ParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.208f, 0.941f, 0.459f), 1.2f);
	private static final ParticleOptions SPARK = new DustParticleOptions(new Vector3f(0.7f, 1.0f, 0.78f), 0.8f);

	/** Lantern -> their drone. */
	private static final Map<UUID, HardLightDroneEntity> DRONES = new ConcurrentHashMap<>();

	private GreenLanternDrone() {
	}

	public static void clearSessionState() {
		DRONES.clear();
	}

	public static HardLightDroneEntity droneOf(UUID owner) {
		HardLightDroneEntity d = DRONES.get(owner);
		return d == null || d.isRemoved() ? null : d;
	}

	// ---------------------------------------------------------------- keys

	/** The key: shape the drone, or dismiss the one that is out (free). */
	public static void toggle(ServerPlayer p) {
		if (droneOf(p.getUUID()) != null) {
			dismiss(p.getUUID(), true);
			sound(p.serverLevel(), p.position(), SoundEvents.BEACON_DEACTIVATE, 0.5f, 1.8f);
			return;
		}
		deploy(p);
	}

	/** Shapes the drone at the shoulder. False (nothing spent) if the ring can't pay. */
	public static boolean deploy(ServerPlayer p) {
		if (!GreenLanternEnergy.spend(p, GreenLanternReconConfig.DRONE_COST)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return false;
		}
		GreenLanternBattery.onAbilityUsed(p);
		ServerLevel level = p.serverLevel();
		HardLightDroneEntity d = HardLightDroneEntity.create(level, p, shoulder(p, 0));
		level.addFreshEntity(d);
		DRONES.put(p.getUUID(), d);
		GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_CONSTRUCT);
		Vec3 h = AbilityHelpers.handPosition(p);
		AbilityHelpers.line(level, h, d.position(), SPARK, 4.0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, d.getX(), d.getY(), d.getZ(), 8, 0.15, 0.15, 0.15, 0.03);
		sound(level, d.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
		sound(level, d.position(), SoundEvents.BEACON_POWER_SELECT, 0.5f, 2.0f);
		return true;
	}

	/**
	 * Shift + the key: the scout run to the spot under the crosshair. With no drone out one is shaped first (normal cost);
	 * a second Shift press while it is away calls it straight back.
	 */
	public static void scout(ServerPlayer p) {
		HardLightDroneEntity d = droneOf(p.getUUID());
		if (d != null && d.scouting()) {
			d.setMode(HardLightDroneEntity.SCOUT_BACK);
			sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5f, 1.4f);
			return;
		}
		if (d == null) {
			if (!deploy(p)) {
				return;
			}
			d = droneOf(p.getUUID());
			if (d == null) {
				return;
			}
		}
		releaseCarried(d);
		d.setScoutSpot(scoutSpot(p));
		d.setMode(HardLightDroneEntity.SCOUT_OUT);
		GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_THROW);
		sound(p.serverLevel(), d.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5f, 1.8f);
		sound(p.serverLevel(), d.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 1.9f);
		p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.drone.scouting").withStyle(ChatFormatting.GREEN), true);
	}

	/** Where the scout run hovers: a little back from and above whatever the crosshair finds within reach. */
	public static Vec3 scoutSpot(ServerPlayer p) {
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, GreenLanternReconConfig.SCOUT_RANGE);
		Vec3 eye = p.getEyePosition();
		Vec3 at = hit.getType() == HitResult.Type.MISS ? eye.add(p.getLookAngle().scale(GreenLanternReconConfig.SCOUT_RANGE)) : hit.getLocation();
		Vec3 back = eye.subtract(at);
		if (back.lengthSqr() > 1.0e-4) {
			at = at.add(back.normalize().scale(1.2));
		}
		return at.add(0, 2.5, 0);
	}

	// ---------------------------------------------------------------- per player

	/** Per Lantern tick: the upkeep (an empty ring dissolves it) and a drone that went missing. */
	public static void tick(ServerPlayer p) {
		HardLightDroneEntity d = DRONES.get(p.getUUID());
		if (d == null) {
			return;
		}
		if (d.isRemoved()) {
			DRONES.remove(p.getUUID(), d);
			return;
		}
		if (!GreenLanternEnergy.drainTick(p, GreenLanternReconConfig.DRONE_UPKEEP_PER_SEC / 20f)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			dismiss(p.getUUID(), true);
		}
	}

	/** N / death / logout / an empty ring: the drone dissolves (and drops whatever it carried). */
	public static void dismiss(UUID owner, boolean fx) {
		HardLightDroneEntity d = DRONES.remove(owner);
		if (d == null || d.isRemoved()) {
			return;
		}
		releaseCarried(d);
		if (fx && d.level() instanceof ServerLevel level) {
			level.sendParticles(SPARK, d.getX(), d.getY(), d.getZ(), 18, 0.25, 0.2, 0.25, 0.04);
			sound(level, d.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6f, 1.7f);
		}
		d.discard();
	}

	// ---------------------------------------------------------------- the drone's own tick

	public static void tickDrone(HardLightDroneEntity d) {
		ServerLevel level = (ServerLevel) d.level();
		ServerPlayer owner = d.owner == null ? null : level.getServer().getPlayerList().getPlayer(d.owner);
		if (owner == null || owner.level() != level || !GreenLantern.hasPower(owner) || DRONES.get(d.owner) != d) {
			releaseCarried(d);
			d.discard();
			return;
		}
		switch (d.mode()) {
			case HardLightDroneEntity.SCOUT_OUT, HardLightDroneEntity.SCOUT_HOVER, HardLightDroneEntity.SCOUT_BACK -> tickScout(d, owner, level);
			case HardLightDroneEntity.FETCH -> {
				tickFetch(d, owner, level);
				tickGuard(d, owner, level);
			}
			default -> {
				if (d.position().distanceTo(owner.getEyePosition()) > GreenLanternReconConfig.DRONE_LEASH) {
					blinkHome(d, owner);
				}
				flyTo(d, shoulder(owner, d.tickCount), 1.2, owner.getYRot());
				if (d.tickCount % 5 == 0) {
					Entity loot = findLoot(d, owner, level);
					if (loot != null) {
						d.fetchTargetId = loot.getId();
						d.setMode(HardLightDroneEntity.FETCH);
					}
				}
				tickGuard(d, owner, level);
			}
		}
		if (d.tickCount % 60 == 0) {
			sound(level, d.position(), SoundEvents.BEACON_AMBIENT, 0.18f, 1.9f); // its soft hum
		}
	}

	/** Following / fetching: the sweep for hostiles and the zap at whatever is after the bearer. */
	private static void tickGuard(HardLightDroneEntity d, ServerPlayer owner, ServerLevel level) {
		if (d.tickCount >= d.nextScan) {
			d.nextScan = d.tickCount + GreenLanternReconConfig.DRONE_SCAN_INTERVAL_TICKS;
			int[] ids = hostiles(owner, level, owner.position(), GreenLanternReconConfig.DRONE_SCAN_RADIUS);
			send(owner, new GreenLanternReconPayload(GreenLanternReconPayload.MARKS, ids, GreenLanternReconConfig.DRONE_MARK_TICKS, 0, 0));
		}
		if (d.tickCount < d.nextZap) {
			return;
		}
		Mob best = null;
		double bestD = Double.MAX_VALUE;
		double r = GreenLanternReconConfig.DRONE_ZAP_RANGE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(r), m -> m.isAlive() && m.getTarget() == owner)) {
			double dist = m.distanceToSqr(owner);
			if (dist > r * r || dist >= bestD || !HeroTargets.canHarm(owner, m) || !sees(level, d, m)) {
				continue;
			}
			best = m;
			bestD = dist;
		}
		if (best == null) {
			return;
		}
		d.nextZap = d.tickCount + GreenLanternReconConfig.DRONE_ZAP_INTERVAL_TICKS;
		zap(d, owner, best);
	}

	/** One small hard-light bolt from the drone's lens to {@code target}. Public for tests. */
	public static void zap(HardLightDroneEntity d, ServerPlayer owner, LivingEntity target) {
		ServerLevel level = (ServerLevel) d.level();
		d.markZap(target.getId());
		AbilityHelpers.hurt(owner, target, GreenLanternReconConfig.DRONE_ZAP_DAMAGE);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(SPARK, at.x, at.y, at.z, 6, 0.15, 0.2, 0.15, 0.06);
		sound(level, d.position(), SoundEvents.AMETHYST_CLUSTER_HIT, 0.7f, 2.0f);
		sound(level, at, SoundEvents.AMETHYST_BLOCK_HIT, 0.5f, 1.6f);
	}

	private static boolean sees(ServerLevel level, Entity from, Entity to) {
		Vec3 a = from.position();
		Vec3 b = to.getEyePosition();
		return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, from)).getType() == HitResult.Type.MISS;
	}

	// ---------------------------------------------------------------- fetch

	/** The nearest dropped item / XP orb near the bearer worth fetching (null if none). Never one the bearer threw away. */
	private static Entity findLoot(HardLightDroneEntity d, ServerPlayer owner, ServerLevel level) {
		double r = GreenLanternReconConfig.DRONE_FETCH_RADIUS;
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : level.getEntities((Entity) null, owner.getBoundingBox().inflate(r),
				e -> e.isAlive() && (e instanceof ItemEntity || e instanceof ExperienceOrb))) {
			double dist = e.distanceToSqr(owner);
			if (dist > r * r || dist < 1.8 * 1.8 || dist >= bestD) {
				continue;
			}
			if (e instanceof ItemEntity item && (item.getOwner() == owner && item.getAge() < 2400 || item.getItem().isEmpty())) {
				continue;
			}
			if (e.isNoGravity() && carriedBySomeDrone(level, e)) {
				continue;
			}
			best = e;
			bestD = dist;
		}
		return best;
	}

	private static boolean carriedBySomeDrone(ServerLevel level, Entity e) {
		for (HardLightDroneEntity other : DRONES.values()) {
			if (!other.isRemoved() && other.level() == level && other.carryId() == e.getId()) {
				return true;
			}
		}
		return false;
	}

	private static void tickFetch(HardLightDroneEntity d, ServerPlayer owner, ServerLevel level) {
		int carried = d.carryId();
		if (carried >= 0) {
			Entity e = level.getEntity(carried);
			if (e == null || !e.isAlive()) {
				d.setCarry(-1);
				d.setMode(HardLightDroneEntity.FOLLOW);
				return;
			}
			Vec3 home = owner.position().add(0, owner.getBbHeight() * 0.55, 0);
			flyTo(d, home, GreenLanternReconConfig.DRONE_FETCH_SPEED + 0.2, null);
			e.setPos(d.getX(), d.getY() - 0.45, d.getZ());
			e.setDeltaMovement(Vec3.ZERO);
			e.hurtMarked = true;
			if (d.tickCount % 3 == 0) {
				level.sendParticles(GREEN, e.getX(), e.getY() + 0.2, e.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
			}
			if (d.position().distanceTo(home) <= 1.5) {
				deliver(d, owner, e);
				d.setMode(HardLightDroneEntity.FOLLOW);
			} else if (d.modeTicks > 300) {
				releaseCarried(d);
				d.setMode(HardLightDroneEntity.FOLLOW);
			}
			return;
		}
		Entity target = d.fetchTargetId >= 0 ? level.getEntity(d.fetchTargetId) : null;
		if (target == null || !target.isAlive() || target.distanceToSqr(owner) > Math.pow(GreenLanternReconConfig.DRONE_FETCH_RADIUS + 4, 2)
				|| d.modeTicks > 200) {
			d.fetchTargetId = -1;
			d.setMode(HardLightDroneEntity.FOLLOW);
			return;
		}
		Vec3 over = target.position().add(0, 0.55, 0);
		flyTo(d, over, GreenLanternReconConfig.DRONE_FETCH_SPEED, null);
		if (d.position().distanceTo(over) <= 0.6) {
			d.setCarry(target.getId());
			target.setNoGravity(true);
			level.sendParticles(SPARK, target.getX(), target.getY() + 0.2, target.getZ(), 5, 0.1, 0.1, 0.1, 0.03);
			sound(level, target.position(), SoundEvents.ITEM_PICKUP, 0.4f, 1.6f);
		}
	}

	/** Hands the carried thing over (an item that doesn't fit is set down at the bearer's feet). */
	private static void deliver(HardLightDroneEntity d, ServerPlayer owner, Entity e) {
		d.setCarry(-1);
		e.setNoGravity(false);
		if (e instanceof ItemEntity item) {
			item.setNoPickUpDelay();
			item.playerTouch(owner);
			if (item.isAlive()) {
				item.setPos(owner.getX(), owner.getY() + 0.2, owner.getZ());
			}
		} else if (e instanceof ExperienceOrb orb) {
			owner.takeXpDelay = 0;
			orb.playerTouch(owner);
		}
	}

	private static void releaseCarried(HardLightDroneEntity d) {
		int id = d.carryId();
		if (id >= 0) {
			Entity e = d.level().getEntity(id);
			if (e != null) {
				e.setNoGravity(false);
			}
			d.setCarry(-1);
		}
	}

	// ---------------------------------------------------------------- scout run

	private static void tickScout(HardLightDroneEntity d, ServerPlayer owner, ServerLevel level) {
		Vec3 spot = d.scoutSpot;
		switch (d.mode()) {
			case HardLightDroneEntity.SCOUT_OUT -> {
				flyTo(d, spot, GreenLanternReconConfig.DRONE_SCOUT_SPEED, null);
				if (d.position().distanceTo(spot) <= 0.7 || d.modeTicks > 120) {
					d.setMode(HardLightDroneEntity.SCOUT_HOVER);
					report(d, owner, level, true);
				}
			}
			case HardLightDroneEntity.SCOUT_HOVER -> {
				// a slow lazy circle over the spot, the lens turning to look round
				float a = d.modeTicks * 0.05f;
				Vec3 orbit = spot.add(Mth.cos(a) * 0.8, Mth.sin(d.modeTicks * 0.09f) * 0.15, Mth.sin(a) * 0.8);
				flyTo(d, orbit, 0.3, d.modeTicks * 3.0f);
				if (d.modeTicks % 40 == 0) {
					report(d, owner, level, false);
				}
				if (d.modeTicks >= GreenLanternReconConfig.SCOUT_HOVER_TICKS || owner.distanceToSqr(d) > 110 * 110) {
					d.setMode(HardLightDroneEntity.SCOUT_BACK);
					sound(level, d.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 1.5f);
				}
			}
			default -> {
				Vec3 home = shoulder(owner, d.tickCount);
				flyTo(d, home, GreenLanternReconConfig.DRONE_SCOUT_SPEED, null);
				if (d.position().distanceTo(home) <= 1.2) {
					d.setMode(HardLightDroneEntity.FOLLOW);
				} else if (d.modeTicks > 160) {
					blinkHome(d, owner);
					d.setMode(HardLightDroneEntity.FOLLOW);
				}
			}
		}
	}

	/** The scout sweep at the spot: outlines for the bearer, and (on arrival) the summary popup. */
	private static void report(HardLightDroneEntity d, ServerPlayer owner, ServerLevel level, boolean summary) {
		Vec3 c = d.scoutSpot;
		double r = GreenLanternReconConfig.SCOUT_RADIUS;
		int[] hostile = hostiles(owner, level, c, r);
		if (!summary) {
			send(owner, new GreenLanternReconPayload(GreenLanternReconPayload.MARKS, hostile, 60, 0, 0));
			return;
		}
		int others = AbilityHelpers.living(level, c, r, e -> !(e instanceof Player) && e.isAlive()).size() - hostile.length;
		int chests = containers(level, c, r);
		send(owner, new GreenLanternReconPayload(GreenLanternReconPayload.SCOUT_REPORT, hostile, hostile.length, chests, Math.max(0, others)));
		level.sendParticles(GREEN, c.x, c.y, c.z, 30, 1.5, 0.4, 1.5, 0.05);
		sound(level, c, SoundEvents.BEACON_ACTIVATE, 0.6f, 2.0f);
		sound(level, owner.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 1.9f);
	}

	private static int[] hostiles(ServerPlayer owner, ServerLevel level, Vec3 c, double r) {
		IntArrayList ids = new IntArrayList();
		for (LivingEntity e : AbilityHelpers.living(level, c, r, e -> e != owner && !(e instanceof Player) && e.isAlive())) {
			if (HeroTargets.isHostile(owner, e)) {
				ids.add(e.getId());
			}
		}
		return ids.toIntArray();
	}

	/** Loot containers (chests, barrels, shulker boxes, hoppers...) within {@code r} of {@code c} in loaded chunks. */
	public static int containers(ServerLevel level, Vec3 c, double r) {
		int n = 0;
		int cx0 = Mth.floor(c.x - r) >> 4;
		int cx1 = Mth.floor(c.x + r) >> 4;
		int cz0 = Mth.floor(c.z - r) >> 4;
		int cz1 = Mth.floor(c.z + r) >> 4;
		for (int cx = cx0; cx <= cx1; cx++) {
			for (int cz = cz0; cz <= cz1; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					BlockPos p = be.getBlockPos();
					if (be instanceof Container && !(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity)
							&& c.distanceToSqr(Vec3.atCenterOf(p)) <= r * r) {
						n++;
					}
				}
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- damage

	/** A hit on the drone: its Lantern and their squad can't hurt it; anything else wears it down, and at 0 it is shot down. */
	public static boolean onHurt(HardLightDroneEntity d, DamageSource source, float amount) {
		ServerLevel level = (ServerLevel) d.level();
		ServerPlayer owner = d.owner == null ? null : level.getServer().getPlayerList().getPlayer(d.owner);
		Entity by = source.getEntity();
		if (owner != null && by != null && (by == owner || com.projecthero.mod.squad.Squads.areAllies(owner, by))) {
			return false;
		}
		d.setHp(d.hp() - amount);
		d.hurtFlash = 6;
		level.sendParticles(SPARK, d.getX(), d.getY(), d.getZ(), 6, 0.15, 0.15, 0.15, 0.06);
		sound(level, d.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.9f, 1.3f);
		if (d.hp() <= 0f) {
			level.sendParticles(GREEN, d.getX(), d.getY(), d.getZ(), 30, 0.3, 0.3, 0.3, 0.12);
			sound(level, d.position(), SoundEvents.GLASS_BREAK, 0.9f, 1.4f);
			if (owner != null) {
				owner.displayClientMessage(Component.translatable("message.projecthero.green_lantern.drone.shot_down")
						.withStyle(ChatFormatting.RED), true);
				dismiss(owner.getUUID(), false);
			} else {
				releaseCarried(d);
				d.discard();
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- movement

	/** Over the bearer's right shoulder, a touch behind, bobbing gently. */
	public static Vec3 shoulder(ServerPlayer p, int tick) {
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		double bob = Mth.sin(tick * 0.12f) * 0.08;
		return p.getEyePosition().add(right.scale(0.8)).add(fwd.scale(-0.35)).add(0, 0.38 + bob, 0);
	}

	/**
	 * Eases toward {@code target}: a fraction of the way each tick (smooth when close), never faster than {@code max} blocks a
	 * tick. Faces {@code yaw} when given, else the way it is flying.
	 */
	static void flyTo(HardLightDroneEntity d, Vec3 target, double max, Float yaw) {
		Vec3 pos = d.position();
		Vec3 to = target.subtract(pos);
		double dist = to.length();
		if (dist < 1.0e-4) {
			return;
		}
		double step = Math.min(max, Math.max(Math.min(dist, 0.04), dist * 0.3));
		Vec3 next = pos.add(to.scale(step / dist));
		d.setPos(next.x, next.y, next.z);
		d.setDeltaMovement(next.subtract(pos));
		float want = yaw != null ? yaw : dist > 0.15 ? (float) Math.toDegrees(Math.atan2(-to.x, to.z)) : d.getYRot();
		d.setYRot(d.getYRot() + Mth.wrapDegrees(want - d.getYRot()) * 0.35f);
		float pitch = (float) Mth.clamp(to.y / Math.max(0.2, dist) * -25.0, -25.0, 25.0);
		d.setXRot(d.getXRot() + (pitch * (float) Math.min(1.0, dist) - d.getXRot()) * 0.2f);
	}

	private static void blinkHome(HardLightDroneEntity d, ServerPlayer owner) {
		Vec3 home = shoulder(owner, d.tickCount);
		releaseCarried(d);
		d.setPos(home.x, home.y, home.z);
		d.xo = home.x;
		d.yo = home.y;
		d.zo = home.z;
		((ServerLevel) d.level()).sendParticles(SPARK, home.x, home.y, home.z, 8, 0.15, 0.15, 0.15, 0.03);
	}

	// ---------------------------------------------------------------- helpers

	static void send(ServerPlayer p, GreenLanternReconPayload payload) {
		if (ServerPlayNetworking.canSend(p, GreenLanternReconPayload.TYPE)) {
			ServerPlayNetworking.send(p, payload);
		}
	}

	static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	static void sound(ServerLevel level, Vec3 at, net.minecraft.core.Holder<SoundEvent> s, float vol, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
	}

	/** Tests: a box round the drone. */
	public static AABB around(HardLightDroneEntity d, double r) {
		return d.getBoundingBox().inflate(r);
	}
}
