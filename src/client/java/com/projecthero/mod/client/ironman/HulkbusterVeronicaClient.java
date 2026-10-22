package com.projecthero.mod.client.ironman;

import com.projecthero.mod.ironman.HulkbusterParts;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.entity.HulkbusterPodEntity;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Hulkbuster "Veronica" overhaul, client side:
 * <ul>
 *   <li>broken parts smoke and spark at the part (worn bodies and Sentries, everyone sees it);</li>
 *   <li>the Replacement Pod's welding sparks where each plate is being welded on;</li>
 *   <li>the mid-air build: which pieces of the Hulkbuster body round the held player have arrived / are in flight, and
 *       where (offsets for the body's bones, {@link #buildPose}).</li>
 * </ul>
 */
public final class HulkbusterVeronicaClient {
	private HulkbusterVeronicaClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(HulkbusterVeronicaClient::tick);
	}

	// ======================================================================== where a part is

	/** World position of {@code part} on {@code target} (the body's captured bones when fresh, else an estimate). */
	public static Vec3 partWorld(Entity target, int part, float partial) {
		Vec3 base = target.getPosition(partial);
		Vec3 off = HulkbusterLocators.part(target, part);
		if (off != null) {
			return base.add(off);
		}
		return HulkbusterPodEntity.partPos(target, part).subtract(target.position()).add(base);
	}

	/** The way out of {@code part}'s surface (horizontal): the chest / head face forward, limbs face out to their side. */
	public static Vec3 outward(Entity target, int part, float partial) {
		float yaw = target instanceof net.minecraft.world.entity.LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot)
				: target.getViewYRot(partial);
		Vec3 facing = Vec3.directionFromRotation(0f, yaw);
		if (part == HulkbusterParts.HEAD || part == HulkbusterParts.TORSO) {
			return facing;
		}
		Vec3 dest = partWorld(target, part, partial);
		Vec3 out = dest.subtract(target.getPosition(partial)).multiply(1, 0, 1);
		return out.lengthSqr() < 0.04 ? facing : out.normalize();
	}

	/** Where a replacement plate lies while it is welded on: on the OUTSIDE of the part's plating. */
	public static Vec3 plateSpot(Entity target, int part, float partial) {
		double push = part == HulkbusterParts.TORSO ? 0.62 : part == HulkbusterParts.HEAD ? 0.5 : 0.48;
		return partWorld(target, part, partial).add(outward(target, part, partial).scale(push));
	}

	/** The effective part damage of a body being drawn: a worn suit or a Sentry. */
	public static float[] damageOf(Entity e) {
		if (e instanceof Player p) {
			return HulkbusterParts.of(p);
		}
		if (e instanceof IronManSentryEntity s && s.hulkbuster()) {
			float max = IronManEnergy.maxIntegrity(HulkbusterParts.SUIT_ID);
			return HulkbusterParts.of(s.stack(1), s.integrity() / Math.max(1f, max));
		}
		return null;
	}

	// ======================================================================== the build

	/** The build pod working on {@code p}, or null. */
	public static HulkbusterPodEntity buildPod(Player p) {
		if (p == null || !(p.level() instanceof ClientLevel level)) {
			return null;
		}
		if (p.getVehicle() instanceof HulkbusterPodEntity pod && pod.mode() == HulkbusterPodEntity.MODE_BUILD) {
			return pod;
		}
		// just let go: keep drawing the build until the real suit shows (a tick or two)
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof HulkbusterPodEntity pod && pod.mode() == HulkbusterPodEntity.MODE_BUILD && pod.targetId() == p.getId()
					&& pod.age(0f) < HulkbusterPodEntity.BUILD_DONE + 12 && !HulkbusterParts.wearing(p)) {
				return pod;
			}
		}
		return null;
	}

	/** One frame of the build: which parts show and their bone offsets (model px) / extra rotation (radians). */
	public static final class BuildPose {
		public int visible;
		public final float[][] pos = new float[HulkbusterParts.COUNT][3];
		public final float[][] rot = new float[HulkbusterParts.COUNT][3];
	}

	/** The body's rest pivot of each part's root bone (bedrock px: x, y, z) -- see hulkbuster_body.geo.json. */
	private static final float[][] PIVOT = {
			{ 0f, 36f, 0f },    // head
			{ 0f, 27f, 0f },    // chest
			{ 7.5f, 33f, 0f },  // shoulder_l
			{ -7.5f, 33f, 0f }, // shoulder_r
			{ 3f, 18f, 0f },    // thigh_l
			{ -3f, 18f, 0f } }; // thigh_r

	/**
	 * The build pose of {@code p} this frame, or null when no build is running. Each piece leaves the pod's open hatch at
	 * its launch tick, arcs over and settles onto its joint ({@link HulkbusterPodEntity#PIECE_FLIGHT} ticks, eased, with a
	 * decaying tumble), then clamps on with a small settle.
	 */
	public static BuildPose buildPose(Player p, float partial) {
		HulkbusterPodEntity pod = buildPod(p);
		if (pod == null) {
			return null;
		}
		BuildPose out = new BuildPose();
		float age = pod.age(partial);
		Vec3 body = p.getPosition(partial);
		Vec3 hatch = pod.podAt(age).add(0, 1.3, 0);
		float yaw = pod.buildYaw();
		// world -> the body's render space (GeckoLib turns the body by 180 - yaw about +y)
		Vec3 local = hatch.subtract(body).yRot(-(float) Math.toRadians(180f - yaw));
		for (int k = 0; k < HulkbusterPodEntity.BUILD_ORDER.length; k++) {
			int part = HulkbusterPodEntity.BUILD_ORDER[k];
			float launch = HulkbusterPodEntity.pieceLaunch(k);
			if (age < launch) {
				continue;
			}
			out.visible |= 1 << part;
			float u = Mth.clamp((age - launch) / HulkbusterPodEntity.PIECE_FLIGHT, 0f, 1f);
			if (u < 1f) {
				// eased in-out; the piece starts AT the hatch and travels in render space to its rest pivot
				float e = u * u * (3f - 2f * u);
				float rx = -PIVOT[part][0] / 16f, ry = PIVOT[part][1] / 16f, rz = PIVOT[part][2] / 16f; // GeckoLib mirrors x
				double dx = (local.x - rx) * (1f - e);
				double dy = (local.y - ry) * (1f - e) + 0.9 * Mth.sin(u * Mth.PI); // an arc over the top
				double dz = (local.z - rz) * (1f - e);
				out.pos[part][0] = (float) (-dx * 16.0);
				out.pos[part][1] = (float) (dy * 16.0);
				out.pos[part][2] = (float) (dz * 16.0);
				float spin = (1f - e) * (1f - e);
				out.rot[part][0] = spin * (part % 2 == 0 ? 1.6f : -1.4f);
				out.rot[part][1] = spin * 2.2f * (part == HulkbusterParts.HEAD ? 2f : 1f);
				out.rot[part][2] = spin * 0.9f;
			} else {
				// clamped on: a short settle (down a hair and back)
				float s = age - launch - HulkbusterPodEntity.PIECE_FLIGHT;
				if (s < 6f) {
					out.pos[part][1] = -0.7f * Mth.sin(s / 6f * Mth.PI) * (1f - s / 6f);
				}
			}
		}
		return out;
	}

	// ======================================================================== per tick

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused()) {
			return;
		}
		RandomSource r = level.random;
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof HulkbusterPodEntity pod) {
				podFx(level, pod, r);
				continue;
			}
			float[] eff = (e instanceof Player p && HulkbusterParts.wearing(p)) || e instanceof IronManSentryEntity ? damageOf(e) : null;
			if (eff == null || e.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 64 * 64) {
				continue;
			}
			for (int i = 0; i < HulkbusterParts.COUNT; i++) {
				if (!HulkbusterParts.broken(eff[i])) {
					continue;
				}
				// in first person, not from your own head (it would fill the view)
				if (e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson() && i < HulkbusterParts.LLEG) {
					continue;
				}
				Vec3 at = partWorld(e, i, 1f);
				if ((e.tickCount + i * 3) % 4 == 0) {
					level.addParticle(ParticleTypes.SMOKE, at.x + r.nextGaussian() * 0.1, at.y, at.z + r.nextGaussian() * 0.1, 0, 0.03, 0);
				}
				if ((e.tickCount + i) % 9 == 0) {
					level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.1, at.z, 0, 0.02, 0);
				}
				if (r.nextInt(7) == 0) {
					for (int n = 0; n < 3; n++) {
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, r.nextGaussian() * 0.12, 0.05 + r.nextFloat() * 0.1,
								r.nextGaussian() * 0.12);
					}
				}
			}
		}
	}

	private static void podFx(ClientLevel level, HulkbusterPodEntity pod, RandomSource r) {
		float age = pod.age(0f);
		Vec3 at = pod.podAt(age);
		// thrusters under it while it flies in / hovers / leaves
		if (age < pod.departAt() + HulkbusterPodEntity.DEPART_TICKS) {
			// a short thruster flicker under it (not a column: it hovers)
			boolean moving = age < pod.arriveTicks() || age > pod.departAt();
			if (moving || pod.tickCount % 2 == 0) {
				level.addParticle(ParticleTypes.FLAME, at.x + r.nextGaussian() * 0.12, at.y + 0.05, at.z + r.nextGaussian() * 0.12, 0,
						moving ? -0.08 : -0.03, 0);
			}
			if (age < pod.arriveTicks() || age > pod.departAt()) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 0, -0.02, 0);
			}
		}
		if (pod.mode() != HulkbusterPodEntity.MODE_REPAIR || pod.done()) {
			return;
		}
		Entity target = level.getEntity(pod.targetId());
		if (target == null) {
			return;
		}
		for (int j = 0; j < pod.plates(); j++) {
			float w = pod.weld(j, age);
			if (w <= 0f || w >= 1f) {
				continue;
			}
			Vec3 p = plateSpot(target, pod.plateTarget(j), 1f);
			for (int n = 0; n < 3; n++) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x + r.nextGaussian() * 0.12, p.y + r.nextGaussian() * 0.12,
						p.z + r.nextGaussian() * 0.12, r.nextGaussian() * 0.15, 0.08 + r.nextFloat() * 0.12, r.nextGaussian() * 0.15);
			}
			if (r.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, r.nextGaussian() * 0.03, 0.02, r.nextGaussian() * 0.03);
			}
			if (r.nextInt(2) == 0) {
				level.addParticle(ParticleTypes.SMOKE, p.x, p.y + 0.1, p.z, 0, 0.03, 0);
			}
		}
	}
}
