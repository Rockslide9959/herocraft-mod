package com.projecthero.mod.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.darkseid.BeamDraw;
import com.projecthero.mod.client.ironman.IronManFlightPose;
import com.projecthero.mod.client.ironman.IronManFlightPose.Kind;
import com.projecthero.mod.client.sound.IronManThrusterSoundInstance;
import com.projecthero.mod.ironman.IronManFlightLook;
import com.projecthero.mod.ironman.IronManFlightLook.JetState;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.21 flight revamp -- everything Iron Man flight looks and sounds like, client-side for every flier the client can
 * see (own and others). The server no longer sprays particles at the feet centre; this draws:
 * <ul>
 *   <li><b>Thruster jets at the real nozzles</b> -- each palm and each boot sole, every flight state (hover: short
 *   downward stabilising jets; forward: the boots jet back; sprint / supersonic: long strong jets). Particles every
 *   tick plus, at render time, a fullbright additive glow billboard and a tapering jet ribbon per nozzle. The Mark 1 is
 *   boot rockets only (orange, smoky); Repulsor Boots (no suit) get boot jets only.</li>
 *   <li><b>Take-off burst</b> -- a downward blast from the nozzles, a dust ring on the ground below, ignition sounds.</li>
 *   <li><b>Superhero landing impact</b> -- block debris, dust, a ground shock ring and a heavy slam (no block damage).
 *   Soft landings just power down.</li>
 *   <li><b>Supersonic</b> -- a shockwave ring and boom when the burst engages, then a vapour cone around the body.</li>
 *   <li><b>The thruster loop</b> -- {@link IronManThrusterSoundInstance}, three layers per flier.</li>
 * </ul>
 *
 * <h2>Where the nozzles are</h2>
 * Rederived end to end from the model, not guessed: the same chain {@code LivingEntityRenderer} applies -- yaw
 * {@code 180 - bodyYaw}, the entity scale, {@code PlayerRendererMixin}'s {@code Axis.XP.rotationDegrees(-lean)}, the
 * {@code scale(-1,-1,1)}, the player renderer's {@code 0.9375}, {@code translate(0,-1.501,0)} -- then the limb's own
 * pivot and the very rotations {@link IronManFlightPose#targets} poses it with, then the hand end (px {@code (∓1, 11)},
 * the suit's dilated arm) / boot sole (px {@code (0, 13)}). The jet points along the limb's +Y (out of the hand end /
 * sole). Scaled by {@link Player#getScale()} so the 1.25x Mark 1 lines up.
 */
public final class IronManFlightFxClient {
	private static final SoundEvent TAKEOFF = event("ironman_takeoff");
	private static final SoundEvent TAKEOFF_KICK = event("ironman_takeoff_kick");
	private static final SoundEvent LAND_IMPACT = event("ironman_land_impact");
	private static final SoundEvent LAND_BOOM = event("ironman_land_boom");
	private static final SoundEvent SONIC_BOOM = event("ironman_sonic_boom");
	private static final SoundEvent POWER_DOWN = event("ironman_power_down");

	private static final int PALM_GLOW = 0xBFE6FF;
	private static final int PALM_JET = 0x5AB4FF;
	private static final int BOOT_GLOW = 0xFFE6C4;
	private static final int BOOT_JET = 0xFF9A4A;
	private static final int MK1_JET = 0xFF7020;

	/** Up to 4 nozzles: 0/1 = right / left boot, 2/3 = right / left palm. */
	private static final Vector3f[] POS = { new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f() };
	private static final Vector3f[] DIR = { new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f() };
	private static final float[] LIMBS = new float[12];

	private static final Map<UUID, IronManThrusterSoundInstance[]> SOUNDS = new HashMap<>();
	private static final List<Shock> SHOCKS = new ArrayList<>();

	/** An expanding ring: the supersonic shockwave (in the plane across the flight) or a landing's ground ring. */
	private record Shock(Vec3 center, Vec3 normal, long start, float maxRadius, int life) {
	}

	private IronManFlightFxClient() {
	}

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(ProjectHeroMod.id(name));
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(IronManFlightFxClient::render);
	}

	// ---------------------------------------------------------------- per tick

	public static void clientTick(Minecraft client) {
		ClientLevel level = client.level;
		IronManFlightPose.tick(level);
		if (level == null || client.player == null) {
			SOUNDS.clear();
			SHOCKS.clear();
			return;
		}
		long now = level.getGameTime();
		SHOCKS.removeIf(s -> now - s.start() > s.life() || now < s.start());
		SOUNDS.keySet().removeIf(id -> {
			IronManThrusterSoundInstance[] s = SOUNDS.get(id);
			return s == null || s[0].isStopped();
		});

		for (Player player : level.players()) {
			IronManFlightPose.Anim a = IronManFlightPose.anim(player);
			if (a == null) {
				continue;
			}
			if (player.distanceToSqr(client.player) > 128.0 * 128.0) {
				continue;
			}
			if (a.evTakeoff) {
				takeoffBurst(level, player, a);
			}
			if (a.evHardLand) {
				landingImpact(level, player);
			} else if (a.evSoftLand) {
				level.playLocalSound(player.getX(), player.getY(), player.getZ(), POWER_DOWN, SoundSource.PLAYERS,
						0.55f, 1.0f, false);
			}
			if (!a.flying) {
				continue;
			}
			if (a.evSupersonic) {
				supersonicBoom(level, player, a);
			}
			emitJets(client, level, player, a);
			if (a.supersonic) {
				vapourCone(level, player, a);
			}
			IronManThrusterSoundInstance[] sounds = SOUNDS.get(player.getUUID());
			if (sounds == null || sounds[0].isStopped()) {
				sounds = new IronManThrusterSoundInstance[] {
						new IronManThrusterSoundInstance(player, IronManThrusterSoundInstance.Layer.ROAR),
						new IronManThrusterSoundInstance(player, IronManThrusterSoundInstance.Layer.WHINE),
						new IronManThrusterSoundInstance(player, IronManThrusterSoundInstance.Layer.WIND) };
				for (IronManThrusterSoundInstance s : sounds) {
					client.getSoundManager().play(s);
				}
				SOUNDS.put(player.getUUID(), sounds);
			}
		}
	}

	/** Fills {@link #POS} (relative to the feet) / {@link #DIR} for this flier's nozzles; returns how many (2 or 4). */
	private static int nozzles(Player player, IronManFlightPose.Anim a, float partial) {
		Kind kind = a.flying ? a.kind : a.shownKind;
		IronManFlightPose.targets(a, partial, player.tickCount + partial, LIMBS);
		float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		float lean = FlightPoseHelper.lean(player, partial);
		float s = player.getScale();
		Matrix4f base = new Matrix4f()
				.rotateY((180.0f - yaw) * Mth.DEG_TO_RAD)
				.scale(s)
				// v0.15.9: the Repulsor Boots bank, in the same order as PlayerRendererMixin#leanWhileFlying
				.rotateZ(-IronManFlightPose.bootsRoll(player, partial) * Mth.DEG_TO_RAD)
				.rotateX(-lean * Mth.DEG_TO_RAD)
				.scale(-0.9375f, -0.9375f, 0.9375f)
				.translate(0.0f, -1.501f, 0.0f);
		// v0.14.21 round two: bare Repulsor Boots jet from just under the worn boot model's lit sole (RepulsorBootsLayer)
		float sole = kind == Kind.BOOTS ? com.projecthero.mod.client.ironman.RepulsorBootsLayer.SOLE_Y + 0.1f : 13.0f;
		limb(base, -IronManFlightPose.LEG_PIVOT_X, IronManFlightPose.LEG_PIVOT_Y, LIMBS[6], LIMBS[7], LIMBS[8], 0f, sole, 0);
		limb(base, IronManFlightPose.LEG_PIVOT_X, IronManFlightPose.LEG_PIVOT_Y, LIMBS[9], LIMBS[10], LIMBS[11], 0f, sole, 1);
		if (kind == Kind.BOOTS || kind == Kind.MARK_ONE) {
			return 2;
		}
		limb(base, -IronManFlightPose.ARM_PIVOT_X, IronManFlightPose.ARM_PIVOT_Y, LIMBS[0], LIMBS[1], LIMBS[2], -1f, 11f, 2);
		limb(base, IronManFlightPose.ARM_PIVOT_X, IronManFlightPose.ARM_PIVOT_Y, LIMBS[3], LIMBS[4], LIMBS[5], 1f, 11f, 3);
		return 4;
	}

	private static void limb(Matrix4f base, float pivotX, float pivotY, float xRot, float yRot, float zRot, float endX,
			float endY, int slot) {
		Matrix4f m = new Matrix4f(base)
				.translate(pivotX / 16f, pivotY / 16f, 0f)
				.rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot));
		m.transformPosition(endX / 16f, endY / 16f, 0f, POS[slot]);
		m.transformDirection(0f, 1f, 0f, DIR[slot]).normalize();
	}

	private static boolean firstPersonSelf(Minecraft client, Player player) {
		return player == client.getCameraEntity() && client.options.getCameraType().isFirstPerson();
	}

	private static void emitJets(Minecraft client, ClientLevel level, Player player, IronManFlightPose.Anim a) {
		int count = nozzles(player, a, 1.0f);
		JetState js = a.jetState();
		int n = IronManFlightLook.jetParticles(js);
		double sp = IronManFlightLook.jetSpeed(js);
		boolean markOne = a.kind == Kind.MARK_ONE;
		boolean fp = firstPersonSelf(client, player);
		RandomSource r = level.random;
		// at speed a flier covers more than a block a tick: spread the puffs back along this tick's path so the trail
		// reads as one continuous jet instead of dotted beads
		int steps = Mth.clamp(Mth.ceil(a.speed / 0.4), 1, 5);
		for (int i = 0; i < count; i++) {
			boolean boot = i < 2;
			if (fp && !boot) {
				continue; // your own palms are right under the camera in first person
			}
			Vector3f p = POS[i];
			Vector3f d = DIR[i];
			for (int j = 0; j < steps; j++) {
				double back = (double) j / steps;
				double x = player.getX() + p.x() - a.vx * back + d.x() * 0.05;
				double y = player.getY() + p.y() - a.vy * back + d.y() * 0.05;
				double z = player.getZ() + p.z() - a.vz * back + d.z() * 0.05;
				int puffs = j == 0 ? (fp ? 1 : n) : 1;
				for (int k = 0; k < puffs; k++) {
					double v = sp * (0.7 + 0.6 * r.nextDouble());
					double vx = d.x() * v + (r.nextDouble() - 0.5) * 0.03;
					double vy = d.y() * v + (r.nextDouble() - 0.5) * 0.03;
					double vz = d.z() * v + (r.nextDouble() - 0.5) * 0.03;
					ParticleOptions type;
					if (boot) {
						type = markOne && r.nextInt(3) == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME;
					} else {
						type = ParticleTypes.ELECTRIC_SPARK;
					}
					level.addParticle(type, x, y, z, vx, vy, vz);
				}
			}
			// long-burn extras: sparkle in the repulsor wash, smoke behind the boots
			if ((js == JetState.SPRINT || js == JetState.SUPERSONIC) && r.nextInt(3) == 0) {
				double x = player.getX() + p.x() + d.x() * 0.4;
				double y = player.getY() + p.y() + d.y() * 0.4;
				double z = player.getZ() + p.z() + d.z() * 0.4;
				level.addParticle(boot ? ParticleTypes.SMOKE : ParticleTypes.END_ROD, x, y, z,
						d.x() * sp * 0.4, d.y() * sp * 0.4, d.z() * sp * 0.4);
			}
			if (markOne && boot && r.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, player.getX() + p.x(), player.getY() + p.y(), player.getZ() + p.z(),
						d.x() * 0.05, d.y() * 0.05, d.z() * 0.05);
			}
		}
	}

	private static void takeoffBurst(ClientLevel level, Player player, IronManFlightPose.Anim a) {
		RandomSource r = level.random;
		boolean boots = a.kind == Kind.BOOTS;
		level.playLocalSound(player.getX(), player.getY(), player.getZ(), TAKEOFF, SoundSource.PLAYERS,
				boots ? 0.6f : 0.9f, boots ? 1.25f : 1.0f, false);
		if (!boots) {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), TAKEOFF_KICK, SoundSource.PLAYERS,
					0.7f, a.kind == Kind.MARK_ONE ? 0.75f : 1.0f, false);
		}
		int count = nozzles(player, a, 1.0f);
		for (int i = 0; i < count; i++) {
			Vector3f p = POS[i];
			Vector3f d = DIR[i];
			for (int k = 0; k < (i < 2 ? 7 : 3); k++) {
				double v = 0.25 + r.nextDouble() * 0.2;
				level.addParticle(i < 2 ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK,
						player.getX() + p.x(), player.getY() + p.y(), player.getZ() + p.z(),
						d.x() * v + (r.nextDouble() - 0.5) * 0.08, d.y() * v, d.z() * v + (r.nextDouble() - 0.5) * 0.08);
			}
		}
		// the dust ring kicked up on the ground below
		Vec3 feet = player.position();
		BlockHitResult hit = level.clip(new ClipContext(feet, feet.add(0, -4.0, 0), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		double gy = hit.getLocation().y + 0.08;
		BlockState ground = level.getBlockState(hit.getBlockPos());
		int ring = boots ? 12 : 22;
		double strength = boots ? 0.16 : 0.24;
		for (int i = 0; i < ring; i++) {
			double ang = (Math.PI * 2 * i) / ring + r.nextDouble() * 0.2;
			double cx = Math.cos(ang);
			double cz = Math.sin(ang);
			level.addParticle(ParticleTypes.POOF, feet.x + cx * 0.4, gy, feet.z + cz * 0.4, cx * strength, 0.01, cz * strength);
			if (i % 2 == 0 && ground.getRenderShape() != RenderShape.INVISIBLE) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.x + cx * 0.6, gy, feet.z + cz * 0.6,
						cx * 0.2, 0.12, cz * 0.2);
			}
		}
	}

	private static void landingImpact(ClientLevel level, Player player) {
		RandomSource r = level.random;
		Vec3 feet = player.position();
		float s = player.getScale();
		level.playLocalSound(feet.x, feet.y, feet.z, LAND_IMPACT, SoundSource.PLAYERS, 1.0f, 1.0f, false);
		level.playLocalSound(feet.x, feet.y, feet.z, LAND_BOOM, SoundSource.PLAYERS, 0.5f, 1.0f, false);
		BlockState ground = level.getBlockState(player.blockPosition().below());
		boolean debris = !ground.isAir() && ground.getRenderShape() != RenderShape.INVISIBLE;
		for (int i = 0; i < 28; i++) {
			double ang = (Math.PI * 2 * i) / 28 + r.nextDouble() * 0.15;
			double cx = Math.cos(ang);
			double cz = Math.sin(ang);
			level.addParticle(ParticleTypes.POOF, feet.x + cx * 0.5, feet.y + 0.1, feet.z + cz * 0.5,
					cx * 0.3, 0.02, cz * 0.3);
			if (debris) {
				level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.x + cx * 0.7, feet.y + 0.1,
						feet.z + cz * 0.7, cx * 0.25, 0.2 + r.nextDouble() * 0.15, cz * 0.25);
			}
		}
		level.addParticle(ParticleTypes.EXPLOSION, feet.x, feet.y + 0.3, feet.z, 0, 0, 0);
		// the fist hits right in front of the right knee -- a little crater puff there
		level.addParticle(ParticleTypes.DUST_PLUME, feet.x, feet.y + 0.1, feet.z, 0, 0.05, 0);
		SHOCKS.add(new Shock(new Vec3(feet.x, feet.y + 0.06, feet.z), new Vec3(0, 1, 0), level.getGameTime(), 3.4f * s, 10));
	}

	private static Vec3 travel(Player player, IronManFlightPose.Anim a) {
		Vec3 v = new Vec3(a.vx, a.vy, a.vz);
		return v.lengthSqr() > 0.01 ? v.normalize() : player.getLookAngle();
	}

	/** Body centre (relative to feet) -- the model's mid-torso point through the same lean / scale chain. */
	private static Vec3 bodyCentre(Player player, float partial) {
		float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		float lean = FlightPoseHelper.lean(player, partial);
		Vector3f c = new Matrix4f()
				.rotateY((180.0f - yaw) * Mth.DEG_TO_RAD)
				.scale(player.getScale())
				.rotateX(-lean * Mth.DEG_TO_RAD)
				.scale(-0.9375f, -0.9375f, 0.9375f)
				.translate(0.0f, -1.501f, 0.0f)
				.transformPosition(0f, 6f / 16f, 0f, new Vector3f());
		return new Vec3(c.x(), c.y(), c.z());
	}

	private static void supersonicBoom(ClientLevel level, Player player, IronManFlightPose.Anim a) {
		Vec3 dir = travel(player, a);
		Vec3 centre = player.position().add(bodyCentre(player, 1.0f)).add(dir.scale(0.8));
		level.playLocalSound(centre.x, centre.y, centre.z, SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.0f, false);
		level.playLocalSound(centre.x, centre.y, centre.z, LAND_BOOM, SoundSource.PLAYERS, 0.6f, 0.8f, false);
		SHOCKS.add(new Shock(centre, dir, level.getGameTime(), 5.0f * player.getScale(), 12));
		Vec3[] basis = basis(dir);
		RandomSource r = level.random;
		for (int i = 0; i < 32; i++) {
			double ang = (Math.PI * 2 * i) / 32;
			Vec3 radial = basis[0].scale(Math.cos(ang)).add(basis[1].scale(Math.sin(ang)));
			Vec3 at = centre.add(radial.scale(0.6));
			Vec3 v = radial.scale(0.35 + r.nextDouble() * 0.05);
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	private static void vapourCone(ClientLevel level, Player player, IronManFlightPose.Anim a) {
		Vec3 dir = travel(player, a);
		float s = player.getScale();
		Vec3 centre = player.position().add(bodyCentre(player, 1.0f)).add(dir.scale(0.4 * s));
		Vec3[] basis = basis(dir);
		RandomSource r = level.random;
		for (int i = 0; i < 5; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			Vec3 radial = basis[0].scale(Math.cos(ang)).add(basis[1].scale(Math.sin(ang)));
			Vec3 at = centre.add(radial.scale(0.85 * s));
			Vec3 v = new Vec3(a.vx, a.vy, a.vz).scale(0.25).add(radial.scale(0.05));
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, v.x, v.y, v.z);
		}
	}

	/** Two unit vectors perpendicular to {@code n} and each other. */
	private static Vec3[] basis(Vec3 n) {
		Vec3 helper = Math.abs(n.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
		Vec3 u = n.cross(helper).normalize();
		Vec3 v = n.cross(u).normalize();
		return new Vec3[] { u, v };
	}

	// ---------------------------------------------------------------- render

	private static void render(WorldRenderContext context) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		PoseStack poseStack = context.matrixStack();
		MultiBufferSource consumers = context.consumers();
		if (level == null || poseStack == null || consumers == null) {
			return;
		}
		Camera camera = context.camera();
		Vec3 cam = camera.getPosition();
		float partial = context.tickCounter().getGameTimeDeltaPartialTick(false);
		List<Player> fliers = new ArrayList<>();
		for (Player player : level.players()) {
			IronManFlightPose.Anim a = IronManFlightPose.anim(player);
			if (a == null || !a.flying || player.isInvisible() || firstPersonSelf(client, player)
					|| player.distanceToSqr(cam) > 96.0 * 96.0) {
				continue;
			}
			fliers.add(player);
		}
		if (fliers.isEmpty() && SHOCKS.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(-cam.x, -cam.y, -cam.z);
		PoseStack.Pose pose = poseStack.last();
		VertexConsumer vc = consumers.getBuffer(RenderType.lightning());
		Vector3f left = camera.getLeftVector();
		Vector3f up = camera.getUpVector();
		float time = level.getGameTime() + partial;

		for (Player player : fliers) {
			IronManFlightPose.Anim a = IronManFlightPose.anim(player);
			int count = nozzles(player, a, partial);
			double fx = Mth.lerp(partial, player.xo, player.getX());
			double fy = Mth.lerp(partial, player.yo, player.getY());
			double fz = Mth.lerp(partial, player.zo, player.getZ());
			float s = player.getScale();
			JetState js = a.jetState();
			float len = IronManFlightLook.jetLength(js) * s;
			boolean markOne = a.kind == Kind.MARK_ONE;
			for (int i = 0; i < count; i++) {
				boolean boot = i < 2;
				Vector3f p = POS[i];
				Vector3f d = DIR[i];
				float flicker = 0.88f + 0.12f * Mth.sin(time * 2.3f + i * 1.7f) + (markOne ? 0.12f * Mth.sin(time * 5.1f + i) : 0f);
				Vec3 at = new Vec3(fx + p.x() + d.x() * 0.03, fy + p.y() + d.y() * 0.03, fz + p.z() + d.z() * 0.03);
				Vec3 dv = new Vec3(d.x(), d.y(), d.z());
				float l = len * flicker;
				int jet = boot ? (markOne ? MK1_JET : BOOT_JET) : PALM_JET;
				int core = boot ? BOOT_GLOW : PALM_GLOW;
				BeamDraw.segment(vc, pose, at, at.add(dv.scale(l)), cam, 0.075f * s, 0.018f * s, jet, 0.55f, 0f);
				BeamDraw.segment(vc, pose, at, at.add(dv.scale(l * 0.55)), cam, 0.032f * s, 0.008f * s, 0xFFFFFF, 0.9f, 0f);
				float glow = (boot ? 0.12f : 0.11f) * s * flicker;
				billboard(vc, pose, at, left, up, glow, core, 0.85f);
				billboard(vc, pose, at, left, up, glow * 2.3f, jet, 0.22f);
			}
			if (a.supersonic) {
				cone(vc, pose, player, a, partial, fx, fy, fz, time);
			}
		}
		for (Shock shock : SHOCKS) {
			ring(vc, pose, shock, level.getGameTime() + partial);
		}
		poseStack.popPose();
	}

	private static void billboard(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, Vector3f left, Vector3f up, float size, int rgb,
			float alpha) {
		Matrix4f m = pose.pose();
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		float lx = left.x() * size;
		float ly = left.y() * size;
		float lz = left.z() * size;
		float ux = up.x() * size;
		float uy = up.y() * size;
		float uz = up.z() * size;
		float x = (float) c.x;
		float y = (float) c.y;
		float z = (float) c.z;
		// both windings -- the lightning buffer back-face culls
		vc.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setColor(r, g, b, a);
		vc.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setColor(r, g, b, a);
		vc.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setColor(r, g, b, a);
		vc.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setColor(r, g, b, a);
		vc.addVertex(m, x + lx - ux, y + ly - uy, z + lz - uz).setColor(r, g, b, a);
		vc.addVertex(m, x - lx - ux, y - ly - uy, z - lz - uz).setColor(r, g, b, a);
		vc.addVertex(m, x - lx + ux, y - ly + uy, z - lz + uz).setColor(r, g, b, a);
		vc.addVertex(m, x + lx + ux, y + ly + uy, z + lz + uz).setColor(r, g, b, a);
	}

	private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float alphaAB, float alphaCD) {
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int bl = rgb & 0xFF;
		int x = Mth.clamp((int) (alphaAB * 255), 0, 255);
		int y = Mth.clamp((int) (alphaCD * 255), 0, 255);
		vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, x);
		vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, x);
		vc.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, y);
		vc.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(r, g, bl, y);
		vc.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(r, g, bl, y);
		vc.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, y);
		vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, x);
		vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, x);
	}

	/** The supersonic vapour cone: a faint white veil flaring back from just ahead of the body. */
	private static void cone(VertexConsumer vc, PoseStack.Pose pose, Player player, IronManFlightPose.Anim a, float partial,
			double fx, double fy, double fz, float time) {
		Vec3 dir = travel(player, a);
		float s = player.getScale();
		Vec3 centre = bodyCentre(player, partial).add(fx, fy, fz);
		Vec3 apex = centre.add(dir.scale(1.15 * s));
		Vec3 baseC = centre.subtract(dir.scale(0.25 * s));
		Vec3[] basis = basis(dir);
		int seg = 20;
		float radius = 0.95f * s;
		float alpha = 0.16f + 0.05f * Mth.sin(time * 1.9f);
		Vec3 prev = null;
		Matrix4f m = pose.pose();
		for (int i = 0; i <= seg; i++) {
			double ang = (Math.PI * 2 * i) / seg;
			Vec3 p = baseC.add(basis[0].scale(Math.cos(ang) * radius)).add(basis[1].scale(Math.sin(ang) * radius));
			if (prev != null) {
				quad(vc, m, apex, apex, p, prev, 0xE8F2FF, 0.0f, alpha);
			}
			prev = p;
		}
	}

	private static void ring(VertexConsumer vc, PoseStack.Pose pose, Shock shock, float now) {
		float age = now - shock.start();
		if (age < 0f || age > shock.life()) {
			return;
		}
		float f = age / shock.life();
		float ease = 1f - (1f - f) * (1f - f);
		float outer = 0.4f + (shock.maxRadius() - 0.4f) * ease;
		float width = 0.45f * (1f - f) + 0.12f;
		float inner = Math.max(0f, outer - width);
		float alpha = 0.75f * (float) Math.pow(1f - f, 1.5);
		Vec3[] basis = basis(shock.normal());
		int seg = 40;
		Matrix4f m = pose.pose();
		Vec3 pi = null;
		Vec3 po = null;
		for (int i = 0; i <= seg; i++) {
			double ang = (Math.PI * 2 * i) / seg;
			Vec3 radial = basis[0].scale(Math.cos(ang)).add(basis[1].scale(Math.sin(ang)));
			Vec3 ni = shock.center().add(radial.scale(inner));
			Vec3 no = shock.center().add(radial.scale(outer));
			if (pi != null) {
				quad(vc, m, pi, ni, no, po, 0xDDEEFF, alpha * 0.35f, alpha);
			}
			pi = ni;
			po = no;
		}
	}

	/** Leaving a world: drop every per-player effect so nothing carries into the next. */
	public static void clear() {
		SOUNDS.clear();
		SHOCKS.clear();
		IronManFlightPose.clear();
	}

}
