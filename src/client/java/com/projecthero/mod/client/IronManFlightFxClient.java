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
import com.projecthero.mod.client.ironman.HulkbusterLocators;
import com.projecthero.mod.client.ironman.IronManFlightPose;
import com.projecthero.mod.client.ironman.IronManFlightPose.Kind;
import com.projecthero.mod.client.ironman.IronManThrusterParticleClient;
import com.projecthero.mod.client.sound.IronManThrusterSoundInstance;
import com.projecthero.mod.ironman.IronManFlightLook;
import com.projecthero.mod.ironman.IronManFlightLook.JetState;
import com.projecthero.mod.ironman.IronManFlightLook.ThrusterStyle;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
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
 * <p><b>v0.16.7 (user) VFX upgrade</b> ("upgrade the repulsor flight vfx for the iron man armours"), every flier but the
 * Mk I (its rockets are unchanged), styled per suit by {@link IronManFlightLook#style} / {@link IronManFlightLook#thrustColour}:
 * <ul>
 *   <li>REPULSOR: out of both boots and both palms a white-hot core inside a suit-tinted cone (cyan-blue by default; the
 *   palms full while hovering / braking, backed off in forward flight), a fast short flicker, full-bright tinted thrust
 *   puffs ({@code IronManThrusterParticleClient}) and an exhaust / heat-shimmer trail that stretches with speed;</li>
 *   <li>HYDRO (Mk IV): the same in aqua in the air -- in water a stream of bubbles out of every nozzle (water jets);</li>
 *   <li>AIR (Mk V Stealth): no fire, no glow -- pale translucent air puffs and the faintest shimmer, near-silent;</li>
 *   <li>a stronger take-off burst (a flash at the boots, a tinted ground ring) and a sprint-flight boost burst (a flash and
 *   a ring around the body); hovering low a light dust ripple on the blocks below, skimming water spray and sparks;</li>
 *   <li>the Mk II's X boot hover: boot jets only, smaller, in its gold. First person: the boot jets below you stay drawn,
 *   and the gauntlet's palm repulsor burns brighter while you fly ({@code IronManFirstPersonGauntlets}).</li>
 * </ul>
 * All of it is client-side off the synced flight flag (no packets) and capped per flier per tick.
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
	private static final int MK1_JET = 0xFF7020;

	/**
	 * Up to 6 nozzles: 0/1 = right / left boot, 2/3 = right / left palm, 4 = the Hulkbuster's back thrusters (v0.16.7
	 * (user): drawn as VFX at the body's own locators now that its fx cubes are gone -- {@link HulkbusterLocators}).
	 */
	private static final Vector3f[] POS = { new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f() };
	private static final Vector3f[] DIR = { new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f() };

	/** v0.16.7 (user): a nozzle that always runs full (boots, the Hulkbuster's back pack), not a palm. */
	private static boolean fullJet(int i) {
		return i < 2 || i >= 4;
	}
	private static final float[] LIMBS = new float[12];

	private static final Map<UUID, IronManThrusterSoundInstance[]> SOUNDS = new HashMap<>();
	private static final List<Shock> SHOCKS = new ArrayList<>();
	private static final List<Flash> FLASHES = new ArrayList<>();
	/** v0.16.7 (user): the fliers whose Mk VIII Flight Boost is running (for its kick-in edge). */
	private static final java.util.Set<UUID> BOOSTED = new java.util.HashSet<>();
	/** v0.16.7 (user): when the local player's Flight Boost kicked in (game time), or -1 -- the FOV kick reads it. */
	private static long localBoostStart = -1L;

	/**
	 * v0.16.7 (user): the local player's FOV multiplier from the Mk VIII Flight Boost -- a hard kick out for the first
	 * moment, then a steady widening while it runs (vanilla's own FOV easing smooths both edges).
	 */
	public static float boostFovFactor() {
		Minecraft mc = Minecraft.getInstance();
		if (localBoostStart < 0L || mc.level == null || !BOOSTED.contains(mc.player == null ? null : mc.player.getUUID())) {
			return 1.0f;
		}
		long age = mc.level.getGameTime() - localBoostStart;
		return age < 5 ? 1.16f : 1.08f;
	}

	/** An expanding ring: the supersonic shockwave (in the plane across the flight) or a landing's ground ring. */
	private record Shock(Vec3 center, Vec3 normal, long start, float maxRadius, int life, int rgb, float alpha) {
		Shock(Vec3 center, Vec3 normal, long start, float maxRadius, int life) {
			this(center, normal, start, maxRadius, life, 0xDDEEFF, 0.75f);
		}
	}

	/** v0.16.7 (user): a brief swelling glow -- the take-off / boost ignition at a boot. */
	private record Flash(Vec3 at, long start, float size, int life, int rgb) {
	}

	private IronManFlightFxClient() {
	}

	private static SoundEvent event(String name) {
		return SoundEvent.createVariableRangeEvent(ProjectHeroMod.id(name));
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(IronManFlightFxClient::render);
	}

	/** v0.16.7 (user): this flier's thruster style (the Mk I's rockets keep their old look). */
	private static ThrusterStyle style(IronManFlightPose.Anim a) {
		return a.shownKind == Kind.BOOTS ? ThrusterStyle.REPULSOR : IronManFlightLook.style(a.suitId);
	}

	// ---------------------------------------------------------------- per tick

	public static void clientTick(Minecraft client) {
		ClientLevel level = client.level;
		IronManFlightPose.tick(level);
		HulkbusterLocators.tick(client); // v0.16.7 (user)
		if (level == null || client.player == null) {
			SOUNDS.clear();
			SHOCKS.clear();
			FLASHES.clear();
			return;
		}
		long now = level.getGameTime();
		SHOCKS.removeIf(s -> now - s.start() > s.life() || now < s.start());
		FLASHES.removeIf(f -> now - f.start() > f.life() || now < f.start());
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
			boolean hidden = player.isInvisible(); // v0.16.7 (user): an invisible flier (the Mk V's Active Camo) shows nothing
			if (a.evTakeoff && !hidden) {
				takeoffBurst(level, player, a);
			}
			if (a.evHardLand) {
				landingImpact(level, player);
			} else if (a.evSoftLand && !com.projecthero.mod.ironman.IronManArmor.stealthed(player)) { // v0.16.1: Stealth is silent
				level.playLocalSound(player.getX(), player.getY(), player.getZ(), POWER_DOWN, SoundSource.PLAYERS,
						0.55f, 1.0f, false);
			}
			// v0.16.7 (user): the Mk VIII Flight Boost kicking in -- a sonic-boom ring (and, for you, the FOV kick)
			boolean boosting = a.flying && com.projecthero.mod.ironman.ability.IronManSpaceBoost.boosting(player);
			if (boosting && BOOSTED.add(player.getUUID())) {
				if (!hidden) {
					spaceBoom(level, player, a);
				}
				if (player == client.player) {
					localBoostStart = now;
				}
			} else if (!boosting && BOOSTED.remove(player.getUUID()) && player == client.player) {
				localBoostStart = -1L;
			}
			if (!a.flying) {
				continue;
			}
			if (a.evSupersonic) {
				supersonicBoom(level, player, a);
			}
			if (!hidden) {
				if (com.projecthero.mod.ironman.suit.ThrusterPackFlight.packFlying(player)) {
					packPuffs(client, level, player, a); // v0.16.7 (user): the carried Thruster Pack's own small jets
				} else {
					if (a.evBoost) {
						boostBurst(level, player, a);
					}
					emitJets(client, level, player, a);
					groundWash(level, player, a);
				}
				if (a.supersonic) {
					vapourCone(level, player, a);
				}
			}
			IronManThrusterSoundInstance[] sounds = SOUNDS.get(player.getUUID());
			// v0.16.1: the Mark 5 Stealth flies without a roar -- v0.16.7 (user): only a very quiet air whoosh
			boolean quiet = style(a) == ThrusterStyle.AIR;
			if (sounds == null || sounds[0].isStopped()) {
				sounds = quiet
						? new IronManThrusterSoundInstance[] { new IronManThrusterSoundInstance(player, IronManThrusterSoundInstance.Layer.WHOOSH) }
						: new IronManThrusterSoundInstance[] {
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
		// v0.16.7 (user): a Hulkbuster jets from its own body's boots, palms and back pack, wherever its clip has them
		if ("mark_8".equals(a.suitId)) {
			HulkbusterLocators.Frame hb = HulkbusterLocators.fresh(player);
			if (hb != null) {
				int[] order = { HulkbusterLocators.BOOT_R, HulkbusterLocators.BOOT_L, HulkbusterLocators.PALM_R, HulkbusterLocators.PALM_L,
						HulkbusterLocators.BACK };
				for (int i = 0; i < order.length; i++) {
					Vec3 p = hb.pos[order[i]];
					POS[i].set((float) p.x, (float) p.y, (float) p.z);
					DIR[i].set(hb.dir[order[i]]);
				}
				return 5;
			}
		}
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
		if (kind == Kind.BOOTS || kind == Kind.MARK_ONE || IronManFlightLook.bootsOnly(a.suitId)) {
			return 2; // v0.16.7 (user): the Mk II's boot hover is boots only too
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

	/** v0.16.7 (user): the Hydro's jets run as water jets while it is in water. */
	private static boolean wet(Player player, ThrusterStyle st) {
		return st == ThrusterStyle.HYDRO && player.isInWater();
	}

	private static void emitJets(Minecraft client, ClientLevel level, Player player, IronManFlightPose.Anim a) {
		ThrusterStyle st = style(a);
		if (st == ThrusterStyle.ROCKET) {
			emitRocket(client, level, player, a);
			return;
		}
		int count = nozzles(player, a, 1.0f);
		JetState js = a.jetState();
		// v0.16.7 (user): the Mk VIII Flight Boost runs its jets longer and harder
		boolean boost = BOOSTED.contains(player.getUUID());
		int n = IronManFlightLook.jetParticles(js) + (boost ? 2 : 0);
		double sp = IronManFlightLook.jetSpeed(js) * (boost ? 1.6 : 1.0);
		float scale = player.getScale() * IronManFlightLook.jetScale(a.suitId);
		int tint = IronManFlightLook.thrustColour(a.suitId);
		boolean fp = firstPersonSelf(client, player);
		boolean wet = wet(player, st);
		boolean fast = js == JetState.SPRINT || js == JetState.SUPERSONIC;
		RandomSource r = level.random;
		if (boost && !wet && st != ThrusterStyle.AIR && !fp) {
			// the two back thrusters' plumes
			Vec3[] back = new Vec3[2];
			Vec3 bd = backNozzles(player, a, 1.0f, back);
			for (Vec3 b : back) {
				double x = player.getX() + b.x, y = player.getY() + b.y, z = player.getZ() + b.z;
				for (int k = 0; k < 3; k++) {
					double v = sp * (0.75 + 0.5 * r.nextDouble());
					IronManThrusterParticleClient.thrust(level, x, y, z, bd.x * v + (r.nextDouble() - 0.5) * 0.03,
							bd.y * v + (r.nextDouble() - 0.5) * 0.03, bd.z * v + (r.nextDouble() - 0.5) * 0.03, k == 0 ? IronManFlightLook.CORE_THRUST : tint,
							0.9f, 0.2f * scale, 6);
				}
			}
		}
		// at speed a flier covers more than a block a tick: spread the puffs back along this tick's path so the trail
		// reads as one continuous jet instead of dotted beads
		int steps = Mth.clamp(Mth.ceil(a.speed / 0.4), 1, 4);
		for (int i = 0; i < count; i++) {
			boolean boot = fullJet(i);
			if (fp && !boot) {
				continue; // your own palms are right under the camera in first person
			}
			float w = boot ? 1f : IronManFlightLook.palmWeight(js);
			Vector3f p = POS[i];
			Vector3f d = DIR[i];
			for (int j = 0; j < steps; j++) {
				if (!boot && j > 0 && w < 0.7f) {
					break; // forward-flight palms: a lighter wash, no path fill
				}
				double back = (double) j / steps;
				double x = player.getX() + p.x() - a.vx * back + d.x() * 0.05;
				double y = player.getY() + p.y() - a.vy * back + d.y() * 0.05;
				double z = player.getZ() + p.z() - a.vz * back + d.z() * 0.05;
				int puffs = j == 0 ? (fp ? 1 : n) : 1;
				for (int k = 0; k < puffs; k++) {
					double v = sp * (0.75 + 0.5 * r.nextDouble());
					double vx = d.x() * v + (r.nextDouble() - 0.5) * 0.025;
					double vy = d.y() * v + (r.nextDouble() - 0.5) * 0.025;
					double vz = d.z() * v + (r.nextDouble() - 0.5) * 0.025;
					switch (st) {
						case AIR -> IronManThrusterParticleClient.air(level, x, y, z, vx * 1.3, vy * 1.3, vz * 1.3, IronManFlightLook.AIR_THRUST,
								fast ? 0.42f : 0.36f, 0.24f * scale, 10);
						default -> {
							if (wet) {
								level.addParticle(ParticleTypes.BUBBLE, x, y, z, vx * 1.8, vy * 1.8, vz * 1.8);
								if (k == 0) {
									IronManThrusterParticleClient.thrust(level, x, y, z, vx, vy, vz, tint, 0.35f, 0.08f * scale, 4);
								}
							} else {
								IronManThrusterParticleClient.thrust(level, x, y, z, vx, vy, vz, tint, 0.85f * w, (0.16f + 0.08f * w) * scale, 6);
							}
						}
					}
				}
				if (j == 0 && st != ThrusterStyle.AIR && !wet) {
					// the white-hot core right at the nozzle
					IronManThrusterParticleClient.thrust(level, x, y, z, d.x() * sp * 0.6, d.y() * sp * 0.6, d.z() * sp * 0.6,
							IronManFlightLook.CORE_THRUST, 0.95f, 0.13f * scale * (boot ? 1f : 0.8f), 4);
				}
			}
			// the exhaust trail: faint lingering puffs left behind the boots -- the faster the flight, the longer it stretches
			if (boot && a.speed > 0.25 && r.nextInt(fast ? 1 : 2) == 0) {
				double x = player.getX() + p.x() + d.x() * 0.3;
				double y = player.getY() + p.y() + d.y() * 0.3;
				double z = player.getZ() + p.z() + d.z() * 0.3;
				if (st == ThrusterStyle.AIR) {
					IronManThrusterParticleClient.air(level, x, y, z, d.x() * 0.02, d.y() * 0.02, d.z() * 0.02, IronManFlightLook.AIR_THRUST,
							0.12f, 0.24f * scale, 14);
				} else if (wet) {
					level.addParticle(ParticleTypes.BUBBLE, x, y, z, d.x() * 0.05, d.y() * 0.05, d.z() * 0.05);
				} else {
					IronManThrusterParticleClient.thrust(level, x, y, z, d.x() * 0.02, d.y() * 0.02, d.z() * 0.02, tint, 0.3f,
							0.26f * scale, 14);
				}
			}
		}
	}

	/** The Mk I's crude rocket boots -- the pre-v0.16.7 look, unchanged (v0.16.7 (user): "do not change the Mk I"). */
	private static void emitRocket(Minecraft client, ClientLevel level, Player player, IronManFlightPose.Anim a) {
		int count = nozzles(player, a, 1.0f);
		JetState js = a.jetState();
		int n = IronManFlightLook.jetParticles(js);
		double sp = IronManFlightLook.jetSpeed(js);
		boolean fp = firstPersonSelf(client, player);
		RandomSource r = level.random;
		int steps = Mth.clamp(Mth.ceil(a.speed / 0.4), 1, 5);
		for (int i = 0; i < count; i++) {
			boolean boot = i < 2;
			if (fp && !boot) {
				continue;
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
					ParticleOptions type = boot ? (r.nextInt(3) == 0 ? ParticleTypes.SMOKE : ParticleTypes.FLAME) : ParticleTypes.ELECTRIC_SPARK;
					level.addParticle(type, x, y, z, vx, vy, vz);
				}
			}
			if ((js == JetState.SPRINT || js == JetState.SUPERSONIC) && r.nextInt(3) == 0) {
				double x = player.getX() + p.x() + d.x() * 0.4;
				double y = player.getY() + p.y() + d.y() * 0.4;
				double z = player.getZ() + p.z() + d.z() * 0.4;
				level.addParticle(boot ? ParticleTypes.SMOKE : ParticleTypes.END_ROD, x, y, z,
						d.x() * sp * 0.4, d.y() * sp * 0.4, d.z() * sp * 0.4);
			}
			if (boot && r.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.LARGE_SMOKE, player.getX() + p.x(), player.getY() + p.y(), player.getZ() + p.z(),
						d.x() * 0.05, d.y() * 0.05, d.z() * 0.05);
			}
		}
	}

	/**
	 * v0.16.7 (user): hovering / skimming low -- a light dust ripple on the blocks below (and a faint ring now and then), or
	 * spray and thruster sparks thrown off a water surface. Once per tick per flier, strongest right over the surface.
	 */
	private static void groundWash(ClientLevel level, Player player, IronManFlightPose.Anim a) {
		ThrusterStyle st = style(a);
		if (st == ThrusterStyle.ROCKET || player.isInWater()) {
			return;
		}
		Vec3 feet = player.position();
		BlockHitResult hit = level.clip(new ClipContext(feet, feet.add(0, -IronManFlightLook.GROUND_WASH_HEIGHT, 0),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		float wash = IronManFlightLook.groundWash(feet.y - hit.getLocation().y);
		if (IronManFlightLook.bootsOnly(a.suitId) || a.shownKind == Kind.BOOTS) {
			wash *= 0.6f;
		}
		if (wash <= 0.02f) {
			return;
		}
		RandomSource r = level.random;
		float s = player.getScale();
		double gy = hit.getLocation().y + 0.05;
		BlockPos pos = hit.getBlockPos();
		boolean water = level.getFluidState(pos).is(FluidTags.WATER);
		int tint = IronManFlightLook.thrustColour(a.suitId);
		int puffs = 1 + (int) (wash * 2.5f + r.nextFloat());
		for (int k = 0; k < puffs; k++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double cx = Math.cos(ang);
			double cz = Math.sin(ang);
			double rad = (0.3 + r.nextDouble() * 0.6) * s;
			double out = 0.08 + r.nextDouble() * 0.12 + a.speed * 0.15;
			if (water) {
				// spray and sparks thrown off the water
				level.addParticle(ParticleTypes.SPLASH, feet.x + cx * rad, gy + 0.05, feet.z + cz * rad, cx * out, 0.12 + r.nextDouble() * 0.1,
						cz * out);
				IronManThrusterParticleClient.air(level, feet.x + cx * rad * 0.7, gy + 0.08, feet.z + cz * rad * 0.7, cx * out * 0.8, 0.03,
						cz * out * 0.8, 0xEEF6FF, 0.3f * wash + 0.1f, 0.2f * s, 10);
				if (st == ThrusterStyle.AIR) {
					IronManThrusterParticleClient.air(level, feet.x + cx * rad, gy + 0.1, feet.z + cz * rad, cx * out * 0.6, 0.02, cz * out * 0.6,
							0xF2F8FF, 0.25f * wash + 0.08f, 0.22f * s, 12);
				} else if (r.nextInt(2) == 0) {
					IronManThrusterParticleClient.thrust(level, feet.x + cx * rad * 0.6, gy + 0.08, feet.z + cz * rad * 0.6, cx * out * 1.4,
							0.08 + r.nextDouble() * 0.12, cz * out * 1.4, tint, 0.85f, 0.05f * s, 5);
				}
			} else {
				// a light ring of dust in the ground's own colour
				BlockState ground = level.getBlockState(pos);
				int col = ground.getMapColor(level, pos).col;
				int dust = col == 0 ? 0xB8AE9C : mix(col, 0xD8D2C8, 0.55f);
				IronManThrusterParticleClient.air(level, feet.x + cx * rad, gy + 0.04, feet.z + cz * rad, cx * out, 0.012, cz * out, dust,
						0.3f + 0.4f * wash, 0.22f * s, 14);
				if (wash > 0.45f && r.nextInt(6) == 0 && ground.getRenderShape() != RenderShape.INVISIBLE) {
					level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.x + cx * rad, gy, feet.z + cz * rad,
							cx * 0.1, 0.08, cz * 0.1);
				}
			}
		}
		// a faint ripple ring spreading on the ground now and then (not on water: the glow pass is drawn before the water,
		// whose surface it would punch a dark hole in -- the spray carries it there)
		if (!water && (player.tickCount + (player.getId() & 7)) % 8 == 0) {
			int rgb = st == ThrusterStyle.AIR ? 0xE8ECEF : tint;
			float alpha = (st == ThrusterStyle.AIR ? 0.1f : 0.3f) * wash;
			SHOCKS.add(new Shock(new Vec3(feet.x, gy + 0.02, feet.z), new Vec3(0, 1, 0), level.getGameTime(), (1.2f + 1.6f * wash) * s, 14,
					rgb, alpha));
		}
	}

	private static int mix(int a, int b, float t) {
		int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
		int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
		int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return (r << 16) | (g << 8) | bl;
	}

	private static void takeoffBurst(ClientLevel level, Player player, IronManFlightPose.Anim a) {
		RandomSource r = level.random;
		boolean boots = a.kind == Kind.BOOTS;
		ThrusterStyle st = style(a);
		boolean silent = com.projecthero.mod.ironman.IronManArmor.stealthed(player) || st == ThrusterStyle.AIR; // the Mk V Stealth
		if (!silent) {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), TAKEOFF, SoundSource.PLAYERS,
					boots ? 0.6f : 0.9f, boots ? 1.25f : 1.0f, false);
		} else {
			// v0.16.7 (user): the Stealth's lift-off is one soft rush of air
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.18f, 1.7f, false);
		}
		if (!boots && !silent) {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), TAKEOFF_KICK, SoundSource.PLAYERS,
					0.7f, a.kind == Kind.MARK_ONE ? 0.75f : 1.0f, false);
		}
		int count = nozzles(player, a, 1.0f);
		float scale = player.getScale() * IronManFlightLook.jetScale(a.suitId);
		int tint = IronManFlightLook.thrustColour(a.suitId);
		boolean wet = wet(player, st);
		if (firstPersonSelf(Minecraft.getInstance(), player)) {
			count = Math.min(count, 2); // your own palms sit right under the camera in first person
		}
		for (int i = 0; i < count; i++) {
			Vector3f p = POS[i];
			Vector3f d = DIR[i];
			boolean boot = fullJet(i);
			double x = player.getX() + p.x();
			double y = player.getY() + p.y();
			double z = player.getZ() + p.z();
			if (st == ThrusterStyle.ROCKET) {
				for (int k = 0; k < (boot ? 7 : 3); k++) {
					double v = 0.25 + r.nextDouble() * 0.2;
					level.addParticle(boot ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK, x, y, z,
							d.x() * v + (r.nextDouble() - 0.5) * 0.08, d.y() * v, d.z() * v + (r.nextDouble() - 0.5) * 0.08);
				}
				continue;
			}
			// v0.16.7 (user): a stronger, styled ignition
			for (int k = 0; k < (boot ? 12 : 5); k++) {
				double v = 0.28 + r.nextDouble() * 0.28;
				double vx = d.x() * v + (r.nextDouble() - 0.5) * 0.12;
				double vy = d.y() * v + (r.nextDouble() - 0.5) * 0.04;
				double vz = d.z() * v + (r.nextDouble() - 0.5) * 0.12;
				if (st == ThrusterStyle.AIR) {
					IronManThrusterParticleClient.air(level, x, y, z, vx, vy, vz, IronManFlightLook.AIR_THRUST, 0.3f, 0.2f * scale, 12);
				} else if (wet) {
					level.addParticle(ParticleTypes.BUBBLE, x, y, z, vx * 1.5, vy * 1.5, vz * 1.5);
				} else {
					IronManThrusterParticleClient.thrust(level, x, y, z, vx, vy, vz, k % 3 == 0 ? IronManFlightLook.CORE_THRUST : tint, 0.9f,
							0.14f * scale, 7);
				}
			}
			if (boot && st != ThrusterStyle.AIR && !wet) {
				FLASHES.add(new Flash(new Vec3(x, y, z).add(d.x() * 0.1, d.y() * 0.1, d.z() * 0.1), level.getGameTime(), 0.55f * scale, 7, tint));
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
		if (st != ThrusterStyle.ROCKET) {
			// v0.16.7 (user): the take-off shock ring on the ground
			SHOCKS.add(new Shock(new Vec3(feet.x, gy, feet.z), new Vec3(0, 1, 0), level.getGameTime(), (boots ? 1.8f : 2.8f) * scale, 12,
					st == ThrusterStyle.AIR ? 0xE8ECEF : tint, st == ThrusterStyle.AIR ? 0.18f : 0.6f));
		}
	}

	/** v0.16.7 (user): sprint-flight kicking in -- a flash at the boots, a burst back along the jets and a ring round the body. */
	private static void boostBurst(ClientLevel level, Player player, IronManFlightPose.Anim a) {
		ThrusterStyle st = style(a);
		if (st == ThrusterStyle.ROCKET) {
			return;
		}
		RandomSource r = level.random;
		boolean wet = wet(player, st);
		if (st == ThrusterStyle.AIR) {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.12f, 1.9f, false);
		} else {
			level.playLocalSound(player.getX(), player.getY(), player.getZ(), TAKEOFF_KICK, SoundSource.PLAYERS, 0.45f, 1.35f, false);
		}
		int count = Math.min(2, nozzles(player, a, 1.0f));
		float scale = player.getScale() * IronManFlightLook.jetScale(a.suitId);
		int tint = IronManFlightLook.thrustColour(a.suitId);
		for (int i = 0; i < count; i++) {
			Vector3f p = POS[i];
			Vector3f d = DIR[i];
			double x = player.getX() + p.x();
			double y = player.getY() + p.y();
			double z = player.getZ() + p.z();
			for (int k = 0; k < 8; k++) {
				double v = 0.45 + r.nextDouble() * 0.3;
				double vx = d.x() * v + (r.nextDouble() - 0.5) * 0.06;
				double vy = d.y() * v + (r.nextDouble() - 0.5) * 0.06;
				double vz = d.z() * v + (r.nextDouble() - 0.5) * 0.06;
				if (st == ThrusterStyle.AIR) {
					IronManThrusterParticleClient.air(level, x, y, z, vx, vy, vz, IronManFlightLook.AIR_THRUST, 0.28f, 0.2f * scale, 10);
				} else if (wet) {
					level.addParticle(ParticleTypes.BUBBLE, x, y, z, vx * 1.5, vy * 1.5, vz * 1.5);
				} else {
					IronManThrusterParticleClient.thrust(level, x, y, z, vx, vy, vz, k % 3 == 0 ? IronManFlightLook.CORE_THRUST : tint, 0.9f,
							0.13f * scale, 6);
				}
			}
			if (st != ThrusterStyle.AIR && !wet) {
				FLASHES.add(new Flash(new Vec3(x, y, z), level.getGameTime(), 0.45f * scale, 6, tint));
			}
		}
		Vec3 dir = travel(player, a);
		Vec3 centre = player.position().add(bodyCentre(player, 1.0f)).subtract(dir.scale(0.7 * player.getScale()));
		SHOCKS.add(new Shock(centre, dir, level.getGameTime(), 1.6f * scale, 9, st == ThrusterStyle.AIR ? 0xE8ECEF : tint,
				st == ThrusterStyle.AIR ? 0.14f : 0.55f));
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

	/**
	 * v0.16.7 (user): the Mk VIII Flight Boost kicking in -- a double sonic-boom ring across the flight just behind the
	 * suit (a wide white one and a tighter suit-tinted one), a burst of vapour flung out round it, ignition flashes at
	 * both boots and the boom.
	 */
	private static void spaceBoom(ClientLevel level, Player player, IronManFlightPose.Anim a) {
		Vec3 dir = travel(player, a);
		float s = player.getScale();
		Vec3 centre = player.position().add(bodyCentre(player, 1.0f)).subtract(dir.scale(0.4 * s));
		level.playLocalSound(centre.x, centre.y, centre.z, SONIC_BOOM, SoundSource.PLAYERS, 1.1f, 1.15f, false);
		level.playLocalSound(centre.x, centre.y, centre.z, TAKEOFF_KICK, SoundSource.PLAYERS, 0.7f, 0.8f, false);
		int tint = IronManFlightLook.thrustColour(a.suitId);
		long now = level.getGameTime();
		SHOCKS.add(new Shock(centre, dir, now, 6.0f * s, 14, 0xF2F8FF, 0.85f));
		SHOCKS.add(new Shock(centre.subtract(dir.scale(0.6 * s)), dir, now, 3.6f * s, 10, tint, 0.7f));
		Vec3[] basis = basis(dir);
		RandomSource r = level.random;
		for (int i = 0; i < 40; i++) {
			double ang = (Math.PI * 2 * i) / 40;
			Vec3 radial = basis[0].scale(Math.cos(ang)).add(basis[1].scale(Math.sin(ang)));
			Vec3 at = centre.add(radial.scale(0.7 * s));
			Vec3 v = radial.scale(0.45 + r.nextDouble() * 0.08).subtract(dir.scale(0.1));
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, v.x, v.y, v.z);
		}
		int count = Math.min(2, nozzles(player, a, 1.0f));
		for (int i = 0; i < count; i++) {
			Vector3f p = POS[i];
			FLASHES.add(new Flash(new Vec3(player.getX() + p.x(), player.getY() + p.y(), player.getZ() + p.z()), now, 0.7f * s, 8, tint));
		}
	}

	/**
	 * v0.16.7 (user): the Mk VIII Flight Boost's two back thrusters -- just behind the shoulder blades, firing straight
	 * back along the flight (relative to the feet). Fills {@code out} (2 positions) and returns the jet direction.
	 */
	private static Vec3 backNozzles(Player player, IronManFlightPose.Anim a, float partial, Vec3[] out) {
		Vec3 dir = travel(player, a);
		float s = player.getScale();
		Vec3 c = bodyCentre(player, partial);
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 up = side.cross(dir).normalize();
		Vec3 base = c.add(up.scale(0.22 * s)).subtract(dir.scale(0.2 * s));
		out[0] = base.add(side.scale(0.14 * s));
		out[1] = base.subtract(side.scale(0.14 * s));
		return dir.scale(-1.0);
	}

	/**
	 * v0.16.7 (user): the carried Mk VIII Thruster Pack's two nozzles (relative to the feet), at the bottom of the pack on
	 * the back -- through the same body yaw / lean chain as {@link #bodyCentre}. Returns the (downward) jet direction.
	 */
	private static Vec3 packNozzles(Player player, float partial, Vec3[] out) {
		float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
		float lean = FlightPoseHelper.lean(player, partial);
		Matrix4f m = new Matrix4f()
				.rotateY((180.0f - yaw) * Mth.DEG_TO_RAD)
				.scale(player.getScale())
				.rotateX(-lean * Mth.DEG_TO_RAD)
				.scale(-0.9375f, -0.9375f, 0.9375f)
				.translate(0.0f, -1.501f, 0.0f);
		for (int k = 0; k < 2; k++) {
			Vector3f v = m.transformPosition((k == 0 ? -2.6f : 2.6f) / 16f, 12.5f / 16f, 5.2f / 16f, new Vector3f());
			out[k] = new Vec3(v.x(), v.y(), v.z());
		}
		Vector3f d = m.transformDirection(0f, 1f, 0.25f, new Vector3f()).normalize();
		return new Vec3(d.x(), d.y(), d.z());
	}

	/** v0.16.7 (user): the Thruster Pack's small puffs -- a little blue-white jet and a wisp of vapour from each nozzle. */
	private static void packPuffs(Minecraft client, ClientLevel level, Player player, IronManFlightPose.Anim a) {
		Vec3[] nz = new Vec3[2];
		Vec3 d = packNozzles(player, 1.0f, nz);
		RandomSource r = level.random;
		int tint = IronManFlightLook.thrustColour("mark_vii");
		boolean fp = firstPersonSelf(client, player);
		for (Vec3 n : nz) {
			double x = player.getX() + n.x, y = player.getY() + n.y, z = player.getZ() + n.z;
			int puffs = fp ? 1 : 2;
			for (int k = 0; k < puffs; k++) {
				double v = 0.12 + r.nextDouble() * 0.06;
				IronManThrusterParticleClient.thrust(level, x, y, z, d.x * v + (r.nextDouble() - 0.5) * 0.02, d.y * v,
						d.z * v + (r.nextDouble() - 0.5) * 0.02, k == 0 ? IronManFlightLook.CORE_THRUST : tint, 0.7f, 0.07f, 5);
			}
			if (r.nextInt(3) == 0 && !fp) {
				level.addParticle(ParticleTypes.CLOUD, x + d.x * 0.2, y + d.y * 0.2, z + d.z * 0.2, d.x * 0.03, d.y * 0.03, d.z * 0.03);
			}
		}
	}

	/** v0.16.7 (user): the Thruster Pack's nozzle glows and short flames (drawn instead of the boot jets). */
	private static void renderPack(VertexConsumer vc, PoseStack.Pose pose, Player player, double fx, double fy, double fz, Vec3 cam,
			Vector3f left, Vector3f up, float time, float partial) {
		Vec3[] nz = new Vec3[2];
		Vec3 d = packNozzles(player, partial, nz);
		int tint = IronManFlightLook.thrustColour("mark_vii");
		for (int k = 0; k < 2; k++) {
			float f = IronManFlightLook.flicker(time, 8 + k);
			Vec3 at = new Vec3(fx + nz[k].x, fy + nz[k].y, fz + nz[k].z);
			BeamDraw.segment(vc, pose, at, at.add(d.scale(0.32 * f)), cam, 0.045f * f, 0.01f, tint, 0.6f, 0f);
			BeamDraw.segment(vc, pose, at, at.add(d.scale(0.16 * f)), cam, 0.02f * f, 0.005f, 0xFFFFFF, 0.9f, 0f);
			glow(vc, pose, at, left, up, 0.07f * f, 0xFFFFFF, 0.9f);
			glow(vc, pose, at, left, up, 0.2f * f, tint, 0.45f);
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
			if (a == null || !a.flying || player.isInvisible() || player.distanceToSqr(cam) > 96.0 * 96.0) {
				continue;
			}
			// v0.16.7 (user): your own boot jets stay drawn in first person (below you); the Mk I's rockets do not
			if (firstPersonSelf(client, player) && style(a) == ThrusterStyle.ROCKET) {
				continue;
			}
			fliers.add(player);
		}
		if (fliers.isEmpty() && SHOCKS.isEmpty() && FLASHES.isEmpty()) {
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
			ThrusterStyle st = style(a);
			if (com.projecthero.mod.ironman.suit.ThrusterPackFlight.packFlying(player)) {
				renderPack(vc, pose, player, fx, fy, fz, cam, left, up, time, partial); // v0.16.7 (user)
			} else if (st == ThrusterStyle.ROCKET) {
				renderRocket(vc, pose, player, a, count, fx, fy, fz, cam, left, up, time);
			} else {
				renderJets(client, vc, pose, player, a, st, count, fx, fy, fz, cam, left, up, time);
			}
			if (a.supersonic) {
				cone(vc, pose, player, a, partial, fx, fy, fz, time);
			}
		}
		for (Shock shock : SHOCKS) {
			ring(vc, pose, shock, level.getGameTime() + partial);
		}
		for (Flash f : FLASHES) {
			float t = (level.getGameTime() + partial - f.start()) / f.life();
			if (t < 0f || t > 1f) {
				continue;
			}
			float ease = 1f - (1f - t) * (1f - t);
			float fade = (1f - t) * (1f - t);
			glow(vc, pose, f.at(), left, up, f.size() * (1.0f + 1.6f * ease), f.rgb(), 0.7f * fade);
			glow(vc, pose, f.at(), left, up, f.size() * (0.45f + 0.5f * ease), 0xFFFFFF, 0.95f * fade);
		}
		poseStack.popPose();
	}

	/** v0.16.7 (user): a white-hot core in a suit-tinted cone, a nozzle glow and the exhaust / heat trail, per nozzle. */
	private static void renderJets(Minecraft client, VertexConsumer vc, PoseStack.Pose pose, Player player, IronManFlightPose.Anim a,
			ThrusterStyle st, int count, double fx, double fy, double fz, Vec3 cam, Vector3f left, Vector3f up, float time) {
		boolean fp = firstPersonSelf(client, player);
		float s = player.getScale();
		float scale = s * IronManFlightLook.jetScale(a.suitId);
		JetState js = a.jetState();
		// the drawn cone reads longer than the old v0.14.21 ribbon: at least half a block, half again as long
		float len = Math.max(0.5f, IronManFlightLook.jetLength(js)) * 1.5f * scale;
		int tint = IronManFlightLook.thrustColour(a.suitId);
		boolean wet = wet(player, st);
		Vec3 vel = new Vec3(a.vx, a.vy, a.vz);
		float trail = IronManFlightLook.trailLength(a.speed) * s;
		Vec3 back = vel.lengthSqr() > 1.0e-4 ? vel.normalize().scale(-1.0) : null;
		// v0.16.7 (user): the Mk VIII Flight Boost -- boot flames nearly twice as long and hotter, plus the back thrusters
		boolean boost = BOOSTED.contains(player.getUUID()) && st != ThrusterStyle.AIR && !wet;
		if (boost) {
			len *= 1.9f;
		}
		if (boost && !fp) {
			Vec3[] nz = new Vec3[2];
			float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
			Vec3 bd = backNozzles(player, a, partial, nz);
			for (int k = 0; k < 2; k++) {
				float f = IronManFlightLook.flicker(time, 6 + k);
				Vec3 at = new Vec3(fx + nz[k].x, fy + nz[k].y, fz + nz[k].z);
				float l = len * 0.85f * f;
				Vec3 mid = at.add(bd.scale(l * 0.38));
				BeamDraw.segment(vc, pose, at, mid, cam, 0.09f * scale * f, 0.2f * scale * f, tint, 0.4f, 0.28f);
				BeamDraw.segment(vc, pose, mid, at.add(bd.scale(l)), cam, 0.2f * scale * f, 0.02f * scale, tint, 0.28f, 0f);
				BeamDraw.segment(vc, pose, at, at.add(bd.scale(l * 0.6)), cam, 0.05f * scale * f, 0.01f * scale, 0xFFFFFF, 1f, 0f);
				glow(vc, pose, at, left, up, 0.16f * scale * f, 0xFFFFFF, 1f);
				glow(vc, pose, at, left, up, 0.55f * scale * f, tint, 0.6f);
			}
		}
		for (int i = 0; i < count; i++) {
			boolean boot = fullJet(i);
			if (fp && !boot) {
				continue;
			}
			Vector3f p = POS[i];
			Vector3f d = DIR[i];
			float w = boot ? 1f : IronManFlightLook.palmWeight(js);
			float f = IronManFlightLook.flicker(time, i);
			Vec3 at = new Vec3(fx + p.x() + d.x() * 0.03, fy + p.y() + d.y() * 0.03, fz + p.z() + d.z() * 0.03);
			Vec3 dv = new Vec3(d.x(), d.y(), d.z());
			float l = len * w * f * (boot ? 1f : 0.9f);
			if (st == ThrusterStyle.AIR) {
				// the Stealth: a clear jet of air -- only the faintest shimmer, no flame, no glow
				BeamDraw.segment(vc, pose, at, at.add(dv.scale(l * 1.25)), cam, 0.035f * scale, 0.13f * scale, IronManFlightLook.AIR_THRUST,
						0.085f * f, 0f);
				if (back != null && trail > 0.05f && boot) {
					BeamDraw.segment(vc, pose, at, at.add(back.scale(trail * 0.6)), cam, 0.05f * scale, 0.15f * scale,
							IronManFlightLook.AIR_THRUST, 0.05f, 0f);
				}
				continue;
			}
			float am = (wet ? 0.45f : 1f) * (fp ? 0.75f : 1f);
			Vec3 mid = at.add(dv.scale(l * 0.38));
			Vec3 end = at.add(dv.scale(l));
			// the coloured cone: widening out of the nozzle, then tapering to nothing (two passes: a wide soft sheath, a
			// narrower brighter body)
			BeamDraw.segment(vc, pose, at, mid, cam, 0.08f * scale * f, 0.17f * scale * f, tint, 0.32f * am, 0.22f * am);
			BeamDraw.segment(vc, pose, mid, end, cam, 0.17f * scale * f, 0.02f * scale, tint, 0.22f * am, 0f);
			BeamDraw.segment(vc, pose, at, mid, cam, 0.05f * scale * f, 0.09f * scale * f, tint, 0.7f * am, 0.5f * am);
			BeamDraw.segment(vc, pose, mid, at.add(dv.scale(l * 0.8)), cam, 0.09f * scale * f, 0.012f * scale, tint, 0.5f * am, 0f);
			// the white-hot core
			BeamDraw.segment(vc, pose, at, at.add(dv.scale(l * 0.5)), cam, 0.036f * scale * f, 0.008f * scale, 0xFFFFFF, am, 0f);
			// the nozzle glow: a hot white centre in a soft coloured halo
			glow(vc, pose, at, left, up, 0.13f * scale * f * (boot ? 1f : 0.85f), 0xFFFFFF, 0.95f * am);
			glow(vc, pose, at, left, up, 0.42f * scale * f * (boot ? 1f : 0.8f), tint, 0.5f * am);
			if (boost && boot) {
				// the boost: a hotter, brighter boot -- a longer white core and a wider halo
				BeamDraw.segment(vc, pose, at, at.add(dv.scale(l * 0.7)), cam, 0.06f * scale * f, 0.01f * scale, 0xFFFFFF, am, 0f);
				glow(vc, pose, at, left, up, 0.7f * scale * f, tint, 0.45f * am);
			}
			// the exhaust / heat-shimmer trail, stretching back along the flight with speed
			if (!wet && back != null && trail > 0.05f) {
				float tl = trail * (boot ? 1f : 0.5f);
				BeamDraw.segment(vc, pose, at, at.add(back.scale(tl)), cam, 0.05f * scale, 0.17f * scale, tint, 0.2f * am, 0f);
				BeamDraw.segment(vc, pose, at, at.add(back.scale(tl * 0.5)), cam, 0.016f * scale, 0.03f * scale, 0xFFFFFF, 0.22f * am, 0f);
			}
		}
	}

	/** The Mk I's rocket glow -- the pre-v0.16.7 render, unchanged. */
	private static void renderRocket(VertexConsumer vc, PoseStack.Pose pose, Player player, IronManFlightPose.Anim a, int count, double fx,
			double fy, double fz, Vec3 cam, Vector3f left, Vector3f up, float time) {
		float s = player.getScale();
		float len = IronManFlightLook.jetLength(a.jetState()) * s;
		for (int i = 0; i < count; i++) {
			boolean boot = i < 2;
			Vector3f p = POS[i];
			Vector3f d = DIR[i];
			float flicker = 0.88f + 0.12f * Mth.sin(time * 2.3f + i * 1.7f) + 0.12f * Mth.sin(time * 5.1f + i);
			Vec3 at = new Vec3(fx + p.x() + d.x() * 0.03, fy + p.y() + d.y() * 0.03, fz + p.z() + d.z() * 0.03);
			Vec3 dv = new Vec3(d.x(), d.y(), d.z());
			float l = len * flicker;
			int jet = boot ? MK1_JET : PALM_JET;
			int core = boot ? BOOT_GLOW : PALM_GLOW;
			BeamDraw.segment(vc, pose, at, at.add(dv.scale(l)), cam, 0.075f * s, 0.018f * s, jet, 0.55f, 0f);
			BeamDraw.segment(vc, pose, at, at.add(dv.scale(l * 0.55)), cam, 0.032f * s, 0.008f * s, 0xFFFFFF, 0.9f, 0f);
			float glow = (boot ? 0.12f : 0.11f) * s * flicker;
			billboard(vc, pose, at, left, up, glow, core, 0.85f);
			billboard(vc, pose, at, left, up, glow * 2.3f, jet, 0.22f);
		}
	}

	/** v0.16.7 (user): a soft round glow -- bright in the middle, fading to nothing at {@code radius} (a fan of quads). */
	private static void glow(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, Vector3f left, Vector3f up, float radius, int rgb, float alpha) {
		Matrix4f m = pose.pose();
		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = rgb & 0xFF;
		int a = Mth.clamp((int) (alpha * 255), 0, 255);
		float x = (float) c.x;
		float y = (float) c.y;
		float z = (float) c.z;
		int seg = 12;
		float px = 0f, py = 0f, pz = 0f;
		for (int i = 0; i <= seg; i++) {
			double ang = Math.PI * 2 * i / seg;
			float cs = (float) Math.cos(ang) * radius;
			float sn = (float) Math.sin(ang) * radius;
			float qx = x + left.x() * cs + up.x() * sn;
			float qy = y + left.y() * cs + up.y() * sn;
			float qz = z + left.z() * cs + up.z() * sn;
			if (i > 0) {
				// both windings -- the lightning buffer back-face culls
				vc.addVertex(m, x, y, z).setColor(r, g, b, a);
				vc.addVertex(m, x, y, z).setColor(r, g, b, a);
				vc.addVertex(m, qx, qy, qz).setColor(r, g, b, 0);
				vc.addVertex(m, px, py, pz).setColor(r, g, b, 0);
				vc.addVertex(m, px, py, pz).setColor(r, g, b, 0);
				vc.addVertex(m, qx, qy, qz).setColor(r, g, b, 0);
				vc.addVertex(m, x, y, z).setColor(r, g, b, a);
				vc.addVertex(m, x, y, z).setColor(r, g, b, a);
			}
			px = qx;
			py = qy;
			pz = qz;
		}
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
		float alpha = shock.alpha() * (float) Math.pow(1f - f, 1.5);
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
				quad(vc, m, pi, ni, no, po, shock.rgb(), alpha * 0.35f, alpha);
			}
			pi = ni;
			po = no;
		}
	}

	/** Leaving a world: drop every per-player effect so nothing carries into the next. */
	public static void clear() {
		SOUNDS.clear();
		SHOCKS.clear();
		FLASHES.clear();
		BOOSTED.clear();
		localBoostStart = -1L;
		IronManFlightPose.clear();
	}

}
