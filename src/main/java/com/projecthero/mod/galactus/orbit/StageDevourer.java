package com.projecthero.mod.galactus.orbit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;
import com.projecthero.mod.galactus.entity.GalactusEntityTypes;
import com.projecthero.mod.galactus.entity.PunisherRobotEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;

/**
 * v0.17.2 Galactus raid v2, <b>Stage 3: the Devourer</b> -- the real fight. He stands 100 blocks tall beside the
 * Worldship's deck (it is at his chest), drifting round it toward the party, and every part of him is its own hit-box
 * ({@link DevourerPartEntity}): the <b>head</b> takes double (triple while he gags), the <b>arms</b> can be broken (a
 * gauntlet that takes enough goes limp for 25 s), a <b>hand</b> hit mid-move interrupts it, and the <b>chest core</b> is
 * sealed until both gauntlets have broken once. His moves: Hammer Fist (the hand stays down: the window on his arms),
 * Palm Sweep (jump it, fly it, or hide behind a pillar), Eye Beams, the Grab (allies break the grip on the hand; the victim
 * can hit his face), DEVOUR (his head comes down to the deck and inhales -- hit it to choke him into a stun), Meteor
 * Rain, Punisher drops, and below 40% the PLANET CRACKER (break both hands before the sphere fires). At 50% his helmet
 * cracks and he speeds up; at 5% he kneels for the Ultimate Nullifier.
 */
public final class StageDevourer {
	public enum Move { NONE, HAMMER, SWEEP, BEAM, GRAB, DEVOUR, METEORS, PUNISHERS, CRACKER }

	public static final double BODY_R = 50.0;
	public static final double BODY_Y = -64.0;
	public static final float HEIGHT = 100f;

	public double health;
	public double maxHealth;
	public double bodyAngle = Math.PI / 2;
	public boolean cracked;
	public boolean[] armBroke = new boolean[2];
	public long[] limpUntil = new long[2];
	public double[] armDamage = new double[2];
	public long lastCracker = -1;
	public boolean finale;

	public Move move = Move.NONE;
	public long moveAt;
	public int cooldown = 60;
	private int moves;
	private boolean right = true;
	private Vec3 target = Vec3.ZERO, from = Vec3.ZERO, to = Vec3.ZERO;
	private UUID victim;
	private double grip;
	private double choke;
	private boolean gag;
	private double crackR, crackL;
	private boolean crackBrokeR, crackBrokeL;
	private final List<Vec3> meteors = new ArrayList<>();
	private final Set<UUID> hitOnce = new HashSet<>();
	private final java.util.Map<BlockPos, Integer> pillarHits = new java.util.HashMap<>();
	private float raise;

	// ---------------------------------------------------------------- setup

	public void begin(GalactusEvent ev, int fighters) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		maxHealth = c.health + c.healthPerExtraFighter * Math.max(0, fighters - 1);
		health = maxHealth;
	}

	public static Vec3 bodyAt(BlockPos o, double angle, double raise) {
		return new Vec3(o.getX() + 0.5 + Math.cos(angle) * BODY_R, o.getY() + BODY_Y + raise, o.getZ() + 0.5 + Math.sin(angle) * BODY_R);
	}

	public void place(BlockPos o, DevourerEntity d) {
		Vec3 at = bodyAt(o, bodyAngle, raise);
		d.setFeet(at.x, at.y, at.z);
		d.setFacing(StageIslands.facing(bodyAngle));
	}

	public static void buildDeck(ServerLevel orbit, BlockPos o) {
		for (int i = 0; i < OrbitArena.PILLARS; i++) {
			OrbitArena.buildPillar(orbit, OrbitArena.pillar(o, i));
		}
	}

	public double fraction() {
		return maxHealth <= 0 ? 1 : health / maxHealth;
	}

	private boolean limp(int side, long now) {
		return limpUntil[side] > now;
	}

	private boolean armFree(boolean r, long now) {
		return !limp(r ? 0 : 1, now);
	}

	// ---------------------------------------------------------------- tick

	public void tick(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, long now) {
		if (d == null) {
			return;
		}
		BlockPos o = GalactusOrbit.origin(ev.orbitSlot());
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		List<ServerPlayer> fighters = ev.orbitFighters(orbit);
		d.setFlag(DevourerEntity.FLAG_CRACKED, cracked);
		d.setFlag(DevourerEntity.FLAG_CORE_OPEN, armBroke[0] && armBroke[1]);
		if (finale) {
			finaleTick(ev, orbit, d, o, now);
			return;
		}
		long in = now - moveAt;
		// limp arms hang
		for (int side = 0; side < 2; side++) {
			if (limp(side, now) && now % 5 == 0) {
				Vec3 f = side == 0 ? d.pose().fistR : d.pose().fistL;
				GalactusFx.forced(orbit, ParticleTypes.ELECTRIC_SPARK, f.x, f.y, f.z, 6, 2, 2, 2, 0.1);
			}
		}
		switch (move) {
			case NONE -> {
				raise = Mth.lerp(0.05f, raise, 0f);
				drift(ev, d, o, fighters);
				rest(d, 0.2f);
				LivingEntity t = ev.devourerTarget(orbit, d);
				d.setLook(t != null ? t.getEyePosition() : Vec3.atCenterOf(o).add(0, 2, 0));
				d.setEyes(Mth.lerp(0.05f, d.eyes(), cracked ? 0.85f : 0.55f));
				if (--cooldown <= 0 && !fighters.isEmpty()) {
					choose(ev, orbit, d, o, fighters, now);
				}
			}
			case HAMMER -> hammer(ev, orbit, d, o, in, now);
			case SWEEP -> sweep(ev, orbit, d, o, in, now);
			case BEAM -> beam(ev, orbit, d, o, in, now);
			case GRAB -> grab(ev, orbit, d, o, in, now);
			case DEVOUR -> devour(ev, orbit, d, o, fighters, in, now);
			case METEORS -> meteorRain(ev, orbit, d, in, now);
			case PUNISHERS -> {
				rest(d, 0.4f);
				d.setArm(true, d.pose().face.add(0, 30, 0), Mth.clamp(in / 20f, 0, 1));
				if (in == 20) {
					dropPunishers(ev, orbit, o, 2);
				}
				if (in >= 40) {
					end(now, c.moveCooldownTicks);
				}
			}
			case CRACKER -> cracker(ev, orbit, d, o, fighters, in, now);
		}
		place(o, d);
	}

	private void drift(GalactusEvent ev, DevourerEntity d, BlockPos o, List<ServerPlayer> fighters) {
		double cx = 0, cz = 0;
		for (ServerPlayer p : fighters) {
			cx += p.getX() - (o.getX() + 0.5);
			cz += p.getZ() - (o.getZ() + 0.5);
		}
		if (!fighters.isEmpty() && cx * cx + cz * cz > 9 * fighters.size() * fighters.size()) {
			double want = Math.atan2(cz, cx);
			double diff = Mth.wrapDegrees((want - bodyAngle) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
			bodyAngle += Mth.clamp(diff, -0.006, 0.006);
		}
	}

	/** Both arms down (unless limp: they hang lower), the lean easing toward {@code lean}. */
	private void rest(DevourerEntity d, float lean) {
		d.setArm(true, null, 0f);
		d.setArm(false, null, 0f);
		d.setLean(Mth.lerp(0.1f, d.lean(), lean));
		d.setFlag(DevourerEntity.FLAG_FIST_R, false);
		d.setFlag(DevourerEntity.FLAG_FIST_L, false);
		d.setFlag(DevourerEntity.FLAG_STUCK_R, false);
		d.setFlag(DevourerEntity.FLAG_STUCK_L, false);
		d.setFlag(DevourerEntity.FLAG_MOUTH, false);
		d.setCharge(0f);
	}

	private void end(long now, int cd) {
		move = Move.NONE;
		moveAt = now;
		cooldown = (int) (cd * (cracked ? GalactusConfig.stage3().enragedCooldownScale : 1.0));
		hitOnce.clear();
		victim = null;
	}

	private void start(Move m, long now) {
		move = m;
		moveAt = now;
		moves++;
		hitOnce.clear();
	}

	private void choose(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, BlockPos o, List<ServerPlayer> fighters, long now) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		// the Planet Cracker comes first when it is due
		if (fraction() <= c.crackerFraction && (lastCracker < 0 || now - lastCracker >= c.crackerRepeatTicks) && armFree(true, now) && armFree(false, now)) {
			lastCracker = now;
			crackR = crackL = 0;
			crackBrokeR = crackBrokeL = false;
			start(Move.CRACKER, now);
			ev.titleOrbit(orbit, Component.translatable("title.projecthero.galactus.cracker").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.galactus.cracker.sub").withStyle(ChatFormatting.GOLD));
			return;
		}
		LivingEntity t = ev.devourerTarget(orbit, d);
		if (t == null) {
			t = fighters.get(orbit.random.nextInt(fighters.size()));
		}
		target = new Vec3(t.getX(), GalactusOrbit.PLANE_Y + 1, t.getZ());
		boolean onDeck = OrbitArena.onHull(o, t.position()) && Math.abs(t.getY() - (GalactusOrbit.PLANE_Y + 1)) < 4;
		boolean reach = StageIslands.reachable(d, target.add(0, 2, 0));
		right = side(d, target) >= 0 ? armFree(true, now) : !armFree(false, now);
		boolean anyArm = armFree(true, now) || armFree(false, now);
		if (!armFree(right, now)) {
			right = !right;
		}
		List<Move> pool = new ArrayList<>();
		if (anyArm && onDeck && reach) {
			pool.add(Move.HAMMER);
			pool.add(Move.HAMMER);
			pool.add(Move.HAMMER);
			pool.add(Move.GRAB);
		}
		if (anyArm) {
			pool.add(Move.SWEEP);
			pool.add(Move.SWEEP);
		}
		pool.add(Move.BEAM);
		pool.add(Move.BEAM);
		pool.add(Move.METEORS);
		if (moves % 5 == 4 || cracked && moves % 4 == 3) {
			pool.clear();
			pool.add(Move.DEVOUR);
		}
		if (ev.orbitPunishers(orbit).size() < c.punisherCap && moves % 6 == 5) {
			pool.add(Move.PUNISHERS);
		}
		Move m = pool.get(orbit.random.nextInt(pool.size()));
		start(m, now);
		switch (m) {
			case SWEEP -> {
				double a = bodyAngle + (right ? -1 : 1) * 1.15;
				double b = bodyAngle - (right ? -1 : 1) * 1.15;
				from = OrbitArena.hullSpot(o, 30, a).add(0, 0.5, 0);
				to = OrbitArena.hullSpot(o, 30, b).add(0, 0.5, 0);
				// he only sweeps the near side of the deck
				Vec3 near = OrbitArena.hullSpot(o, 12, bodyAngle);
				from = from.lerp(near, 0.15);
				to = to.lerp(near, 0.15);
				orbit.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 5.0f, 0.5f);
			}
			case METEORS -> {
				meteors.clear();
				int n = c.meteorCount;
				for (int i = 0; i < n; i++) {
					ServerPlayer near = fighters.get(orbit.random.nextInt(fighters.size()));
					Vec3 at = new Vec3(near.getX() + (orbit.random.nextDouble() - 0.5) * 14, GalactusOrbit.PLANE_Y + 1,
							near.getZ() + (orbit.random.nextDouble() - 0.5) * 14);
					if (!OrbitArena.onHull(o, at)) {
						at = OrbitArena.hullSpot(o, orbit.random.nextDouble() * 28, orbit.random.nextDouble() * Math.PI * 2);
					}
					meteors.add(at);
				}
			}
			case DEVOUR -> {
				choke = 0;
				gag = false;
				Vec3 face = d.pose().face;
				orbit.playSound(null, face.x, face.y, face.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0f, 0.35f);
				ev.titleOrbit(orbit, Component.translatable("title.projecthero.galactus.devour").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
						Component.translatable("title.projecthero.galactus.devour.sub").withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			default -> {
			}
		}
	}

	private static double side(DevourerEntity d, Vec3 p) {
		float y = d.getYRot() * Mth.DEG_TO_RAD;
		double rx = -Mth.cos(y), rz = -Mth.sin(y);
		return (p.x - d.getX()) * rx + (p.z - d.getZ()) * rz;
	}

	// ---------------------------------------------------------------- Hammer Fist

	private void hammer(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, BlockPos o, long in, long now) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		int wind = 30, strike = 6, stuck = 60, recoil = 16;
		Vec3 toward = new Vec3(d.getX() - target.x, 0, d.getZ() - target.z).normalize();
		Vec3 up = target.add(toward.scale(6)).add(0, 22, 0);
		float lean = GalactusRig.leanToward(d.height(), Math.sqrt(target.distanceToSqr(d.getX(), target.y, d.getZ())), 0.9f) + 0.1f;
		d.setLean(Mth.lerp(0.15f, d.lean(), lean));
		d.setLook(target);
		d.setFlag(right ? DevourerEntity.FLAG_FIST_R : DevourerEntity.FLAG_FIST_L, true);
		if (in < wind) {
			d.setArm(right, up, Mth.clamp(in / 14f, 0, 1));
			if (now % 2 == 0) {
				double r = 2 + 3 * (in / (double) wind);
				GalactusFx.ring(orbit, GalactusFx.EMBER, target.add(0, 0.2, 0), r, 28);
			}
		} else if (in < wind + strike) {
			double k = (in - wind) / (double) strike;
			d.setArm(right, up.lerp(target.add(0, 1, 0), k * k), 1f);
		} else if (in == wind + strike) {
			d.setArm(right, target.add(0, 1, 0), 1f);
			d.setFlag(right ? DevourerEntity.FLAG_STUCK_R : DevourerEntity.FLAG_STUCK_L, true);
			for (ServerPlayer p : ev.orbitFighters(orbit)) {
				if (p.distanceToSqr(target.x, p.getY(), target.z) < 5 * 5 && Math.abs(p.getY() - target.y) < 4) {
					GalactusCombat.hit(orbit, d, p, (float) c.hammerDamage);
					Vec3 away = p.position().subtract(target).multiply(1, 0, 1).normalize();
					p.setDeltaMovement(away.x * 1.0, 0.8, away.z * 1.0);
					p.hurtMarked = true;
				}
			}
			orbit.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 5.0f, 0.5f);
			orbit.playSound(null, target.x, target.y, target.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 4.0f, 0.4f);
			GalactusFx.forced(orbit, ParticleTypes.EXPLOSION, target.x, target.y, target.z, 8, 3, 0.5, 3, 0);
			GalactusFx.ring(orbit, GalactusFx.COSMIC, target, 7, 48);
			QuirkCamera.shakeAround(orbit, target, 40, 6f, 14);
		} else if (in < wind + strike + stuck) {
			d.setArm(right, target.add(0, 1, 0), 1f);
			if (in % 5 == 0) {
				Vec3 f = right ? d.pose().fistR : d.pose().fistL;
				GalactusFx.forced(orbit, GalactusFx.EMBER, f.x, f.y + 2, f.z, 8, 3, 2, 3, 0.02);
			}
		} else if (in < wind + strike + stuck + recoil) {
			d.setFlag(right ? DevourerEntity.FLAG_STUCK_R : DevourerEntity.FLAG_STUCK_L, false);
			d.setArm(right, up, 1f - (in - wind - strike - stuck) / (float) recoil);
		} else {
			end(now, c.moveCooldownTicks);
		}
	}

	// ---------------------------------------------------------------- Palm Sweep

	private void sweep(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, BlockPos o, long in, long now) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		int mark = 26, swing = 18;
		d.setFlag(right ? DevourerEntity.FLAG_FIST_R : DevourerEntity.FLAG_FIST_L, false);
		d.setLean(Mth.lerp(0.12f, d.lean(), 0.75f));
		if (in < mark) {
			d.setArm(right, from.add(0, 10, 0), Mth.clamp(in / 16f, 0, 1));
			d.setLook(from.lerp(to, 0.5));
			if (now % 3 == 0) {
				GalactusFx.line(orbit, GalactusFx.EMBER, from.add(0, 0.8, 0), to.add(0, 0.8, 0), 1.2);
			}
		} else if (in < mark + swing) {
			double k = (in - mark) / (double) swing;
			Vec3 hand = from.lerp(to, k).add(0, 1.5, 0);
			Vec3 prev = from.lerp(to, Math.max(0, (in - mark - 1) / (double) swing)).add(0, 1.5, 0);
			d.setArm(right, hand.add(0, 2, 0), 1f);
			if (in % 2 == 0) {
				orbit.playSound(null, hand.x, hand.y, hand.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 2.0f, 0.4f);
			}
			for (ServerPlayer p : ev.orbitFighters(orbit)) {
				if (hitOnce.contains(p.getUUID())) {
					continue;
				}
				double dx = p.getX() - hand.x, dz = p.getZ() - hand.z;
				if (dx * dx + dz * dz < 4.5 * 4.5 && p.getY() < GalactusOrbit.PLANE_Y + 3.5) {
					hitOnce.add(p.getUUID());
					BlockPos pillar = pillarBetween(orbit, o, prev, p.position().add(0, 1, 0));
					if (pillar != null) {
						hitPillar(orbit, pillar);
						continue;
					}
					GalactusCombat.hit(orbit, d, p, (float) c.sweepDamage);
					Vec3 dir = to.subtract(from).normalize();
					p.setDeltaMovement(dir.x * 1.5, 0.6, dir.z * 1.5);
					p.hurtMarked = true;
				}
			}
		} else if (in < mark + swing + 16) {
			d.setArm(right, to.add(0, 10, 0), 1f - (in - mark - swing) / 16f);
		} else {
			end(now, c.moveCooldownTicks);
		}
	}

	/** A cover pillar between {@code a} and {@code b}, or null. */
	private BlockPos pillarBetween(ServerLevel orbit, BlockPos o, Vec3 a, Vec3 b) {
		BlockHitResult r = orbit.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (r.getType() != HitResult.Type.BLOCK || r.getBlockPos().getY() <= GalactusOrbit.PLANE_Y) {
			return null;
		}
		for (int i = 0; i < OrbitArena.PILLARS; i++) {
			BlockPos base = OrbitArena.pillar(o, i);
			BlockPos hit = r.getBlockPos();
			if (hit.getX() >= base.getX() && hit.getX() <= base.getX() + 1 && hit.getZ() >= base.getZ() && hit.getZ() <= base.getZ() + 1) {
				return base;
			}
		}
		return null;
	}

	private void hitPillar(ServerLevel orbit, BlockPos base) {
		int n = pillarHits.merge(base, 1, Integer::sum);
		orbit.playSound(null, base.getX() + 1, base.getY() + 2, base.getZ() + 1, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 3.0f, 0.5f);
		GalactusFx.forced(orbit, ParticleTypes.EXPLOSION, base.getX() + 1, base.getY() + 2, base.getZ() + 1, 3, 1, 1, 1, 0);
		if (n >= 2 && orbit.getBlockState(base).is(Blocks.POLISHED_BLACKSTONE_BRICKS)) {
			OrbitArena.collapsePillar(orbit, base);
		}
	}

	// ---------------------------------------------------------------- Eye Beams

	private void beam(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, BlockPos o, long in, long now) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		int tele = 36, sweep = 22;
		if (in == 0) {
			Vec3 fromHim = target.subtract(d.getX(), target.y, d.getZ()).normalize();
			Vec3 perp = new Vec3(-fromHim.z, 0, fromHim.x);
			from = target.add(perp.scale(18)).add(0, 0.1, 0);
			to = target.add(perp.scale(-18)).add(0, 0.1, 0);
			orbit.playSound(null, target.x, target.y, target.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0f, 0.5f);
		}
		rest(d, 0.35f);
		if (in < tele) {
			d.setEyes(Mth.lerp(0.1f, d.eyes(), 0.9f));
			d.setLook(from);
			if (now % 3 == 0) {
				GalactusFx.line(orbit, GalactusFx.EMBER, from, to, 1.0);
			}
		} else if (in < tele + sweep) {
			double k = (in - tele) / (double) sweep;
			Vec3 point = from.lerp(to, k);
			Vec3 face = d.pose().face;
			d.setEyes(1f);
			d.setLook(point);
			GalactusFx.line(orbit, GalactusFx.COSMIC, face, point, 3.0);
			GalactusFx.forced(orbit, ParticleTypes.FLAME, point.x, point.y + 0.5, point.z, 8, 0.6, 0.2, 0.6, 0.02);
			for (ServerPlayer p : ev.orbitFighters(orbit)) {
				if (hitOnce.contains(p.getUUID())) {
					continue;
				}
				double dx = p.getX() - point.x, dz = p.getZ() - point.z;
				if (dx * dx + dz * dz < 2.5 * 2.5 && Math.abs(p.getY() - point.y) < 4) {
					hitOnce.add(p.getUUID());
					GalactusCombat.hit(orbit, d, p, (float) c.beamDamage);
					p.igniteForSeconds(4);
				}
			}
			if (in % 4 == 0) {
				orbit.playSound(null, point.x, point.y, point.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0f, 0.5f);
			}
		} else {
			end(now, c.moveCooldownTicks);
		}
	}

	// ---------------------------------------------------------------- the Grab

	private void grab(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, BlockPos o, long in, long now) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		int wind = 25, strike = 6, lift = 40, hold = 30;
		Vec3 toward = new Vec3(d.getX() - target.x, 0, d.getZ() - target.z).normalize();
		Vec3 over = target.add(toward.scale(4)).add(0, 14, 0);
		Vec3 face = d.pose().face;
		Vec3 show = face.add(new Vec3(target.x - face.x, 0, target.z - face.z).normalize().scale(9 * d.pose().k * 1.6)).add(0, -3, 0);
		d.setLook(victim != null ? show : target);
		float lean = GalactusRig.leanToward(d.height(), Math.sqrt(target.distanceToSqr(d.getX(), target.y, d.getZ())), 0.9f) + 0.1f;
		if (in < wind) {
			d.setLean(Mth.lerp(0.15f, d.lean(), lean));
			d.setFlag(right ? DevourerEntity.FLAG_FIST_R : DevourerEntity.FLAG_FIST_L, false);
			d.setArm(right, over, Mth.clamp(in / 14f, 0, 1));
			if (now % 2 == 0) {
				GalactusFx.ring(orbit, GalactusFx.VOID, target.add(0, 0.2, 0), 3.5, 24);
			}
		} else if (in < wind + strike) {
			double k = (in - wind) / (double) strike;
			d.setArm(right, over.lerp(target.add(0, 1, 0), k), 1f);
		} else if (in == wind + strike) {
			ServerPlayer caught = null;
			for (ServerPlayer p : ev.orbitFighters(orbit)) {
				if (p.distanceToSqr(target.x, p.getY(), target.z) < 4.5 * 4.5 && Math.abs(p.getY() - target.y) < 3.5) {
					caught = p;
					break;
				}
			}
			if (caught == null) {
				orbit.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.8f);
				moveAt = now - (wind + strike + lift + hold); // straight to the recoil
				return;
			}
			victim = caught.getUUID();
			grip = 0;
			d.setFlag(right ? DevourerEntity.FLAG_FIST_R : DevourerEntity.FLAG_FIST_L, true);
			caught.displayClientMessage(Component.translatable("message.projecthero.galactus.grabbed").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
			ev.broadcastOrbit(orbit, Component.translatable("message.projecthero.galactus.grab_ally", caught.getDisplayName()).withStyle(ChatFormatting.GOLD));
		} else if (in < wind + strike + lift + hold) {
			ServerPlayer v = victim(orbit);
			double k = Mth.clamp((in - wind - strike) / (double) lift, 0, 1);
			k = GalactusFx_ease(k);
			Vec3 hand = target.add(0, 1, 0).lerp(show, k);
			d.setArm(right, hand, 1f);
			d.setLean(Mth.lerp(0.08f, d.lean(), 0.25f));
			if (v != null) {
				Vec3 f = right ? d.pose().fistR : d.pose().fistL;
				v.teleportTo(f.x, f.y - 1.0, f.z);
				v.setDeltaMovement(Vec3.ZERO);
				v.fallDistance = 0;
				if (in % 20 == 0) {
					GalactusCombat.hit(orbit, d, v, (float) c.grabSqueezePerSecond);
				}
			}
		} else if (in == wind + strike + lift + hold) {
			ServerPlayer v = victim(orbit);
			if (v != null) {
				Vec3 dir = Vec3.atCenterOf(o).subtract(v.position()).multiply(1, 0, 1).normalize();
				v.setDeltaMovement(dir.x * 1.6, 0.6, dir.z * 1.6);
				v.hurtMarked = true;
				GalactusCombat.hit(orbit, d, v, (float) c.flingDamage);
			}
			victim = null;
		} else if (in < wind + strike + lift + hold + 20) {
			d.setArm(right, show, 1f - (in - wind - strike - lift - hold) / 20f);
		} else {
			end(now, c.moveCooldownTicks);
		}
	}

	private static double GalactusFx_ease(double k) {
		return k * k * (3 - 2 * k);
	}

	private ServerPlayer victim(ServerLevel orbit) {
		return victim != null && orbit.getPlayerByUUID(victim) instanceof ServerPlayer p && p.isAlive() ? p : null;
	}

	// ---------------------------------------------------------------- DEVOUR

	private void devour(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, BlockPos o, List<ServerPlayer> fighters, long in, long now) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		int lean = 30, pull = c.devourTicks;
		int n = fighters.size();
		double chokeNeeded = c.chokeHealth + c.chokePerExtraFighter * Math.max(0, n - 1);
		d.setArm(true, null, 0f);
		d.setArm(false, null, 0f);
		if (gag) {
			// stunned: his head on the deck, x3 damage
			raise = Mth.lerp(0.1f, raise, 8f);
			d.setLean(Mth.lerp(0.1f, d.lean(), 1.2f));
			d.setEyes(0.15f + 0.1f * Mth.sin(now * 0.5f));
			d.setFlag(DevourerEntity.FLAG_MOUTH, false);
			if (now % 6 == 0) {
				Vec3 h = d.pose().head;
				GalactusFx.forced(orbit, ParticleTypes.ELECTRIC_SPARK, h.x, h.y + 6, h.z, 10, 5, 4, 5, 0.1);
			}
			if (in >= lean + pull + c.gagTicks) {
				gag = false;
				end(now, c.moveCooldownTicks);
			}
			return;
		}
		Vec3 face = d.pose().face;
		d.setLook(Vec3.atCenterOf(o).add(0, 1, 0));
		if (in < lean) {
			raise = Mth.lerp(0.08f, raise, 8f);
			d.setLean(Mth.lerp(0.1f, d.lean(), 1.15f));
			d.setEyes(1f);
		} else if (in < lean + pull) {
			raise = Mth.lerp(0.08f, raise, 8f);
			d.setLean(1.15f);
			d.setFlag(DevourerEntity.FLAG_MOUTH, true);
			if (in % 10 == 0) {
				orbit.playSound(null, face.x, face.y, face.z, SoundEvents.ELYTRA_FLYING, SoundSource.HOSTILE, 4.0f, 0.3f);
			}
			if (in % 2 == 0) {
				for (int i = 0; i < 6; i++) {
					double a = orbit.random.nextDouble() * Math.PI * 2, r = 8 + orbit.random.nextDouble() * 20;
					Vec3 p0 = face.add(Math.cos(a) * r, (orbit.random.nextDouble() - 0.3) * 8, Math.sin(a) * r);
					Vec3 v = face.subtract(p0).normalize().scale(1.2);
					orbit.sendParticles(ParticleTypes.CLOUD, p0.x, p0.y, p0.z, 0, v.x, v.y, v.z, 1.0);
				}
			}
			for (ServerPlayer p : fighters) {
				Vec3 toFace = face.subtract(p.position());
				double dist = toFace.length();
				if (dist < 5.5) {
					if (!hitOnce.contains(p.getUUID())) {
						hitOnce.add(p.getUUID());
						GalactusCombat.hit(orbit, d, p, (float) c.devourDamage);
						Vec3 out = Vec3.atCenterOf(o).subtract(face).multiply(1, 0, 1).normalize();
						p.setDeltaMovement(out.x * 2.2, 0.8, out.z * 2.2);
						p.hurtMarked = true;
						orbit.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_BURP, SoundSource.HOSTILE, 3.0f, 0.4f);
					}
					continue;
				}
				if (dist > 60) {
					continue;
				}
				// behind a pillar: sheltered
				if (pillarBetween(orbit, o, face, p.position().add(0, 1, 0)) != null) {
					continue;
				}
				Vec3 pullV = toFace.normalize().scale(0.055 + 0.5 / Math.max(6, dist));
				p.setDeltaMovement(p.getDeltaMovement().add(pullV.x, Math.min(0.02, pullV.y), pullV.z));
				p.hurtMarked = true;
			}
			if (choke >= chokeNeeded) {
				gag = true;
				moveAt = now - lean - pull;
				orbit.playSound(null, face.x, face.y, face.z, SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 8.0f, 0.4f);
				orbit.playSound(null, face.x, face.y, face.z, SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 8.0f, 0.5f);
				ev.titleOrbit(orbit, Component.translatable("title.projecthero.galactus.gag").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
						Component.translatable("title.projecthero.galactus.gag.sub").withStyle(ChatFormatting.YELLOW));
			}
		} else {
			raise = Mth.lerp(0.06f, raise, 0f);
			d.setLean(Mth.lerp(0.08f, d.lean(), 0.2f));
			d.setFlag(DevourerEntity.FLAG_MOUTH, false);
			if (in >= lean + pull + 30) {
				end(now, c.moveCooldownTicks);
			}
		}
	}

	// ---------------------------------------------------------------- Meteor Rain

	private void meteorRain(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, long in, long now) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		rest(d, 0.15f);
		d.setArm(true, d.pose().face.add(0, 40, 0), Mth.clamp(in / 16f, 0, 1) * (in < 50 ? 1 : 0));
		d.setLook(d.pose().face.add(0, 40, 0));
		int tele = 40;
		if (in < tele && now % 3 == 0) {
			for (Vec3 m : meteors) {
				GalactusFx.ring(orbit, GalactusFx.EMBER, m.add(0, 0.2, 0), 3, 20);
			}
		}
		if (in == tele - 12) {
			for (Vec3 m : meteors) {
				Vec3 sky = m.add((orbit.random.nextDouble() - 0.5) * 20, 50, (orbit.random.nextDouble() - 0.5) * 20);
				OrbitDebris.fly(orbit, orbit.random.nextBoolean() ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState(), sky,
						m.add(-1, 0, -1), 2.5f, new Quaternionf(), new Quaternionf().rotateXYZ(3, 2, 1), 1, 12, 0);
			}
		}
		if (in == tele + 1) {
			for (Vec3 m : meteors) {
				orbit.playSound(null, m.x, m.y, m.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.9f);
				GalactusFx.forced(orbit, ParticleTypes.EXPLOSION, m.x, m.y, m.z, 3, 1.5, 0.5, 1.5, 0);
				GalactusFx.forced(orbit, ParticleTypes.LAVA, m.x, m.y, m.z, 8, 1.5, 0.5, 1.5, 0);
				for (ServerPlayer p : ev.orbitFighters(orbit)) {
					if (p.distanceToSqr(m.x, p.getY(), m.z) < 3 * 3 && Math.abs(p.getY() - m.y) < 3) {
						GalactusCombat.hit(orbit, d, p, (float) c.meteorDamage);
					}
				}
			}
		}
		if (in >= tele + 20) {
			end(now, c.moveCooldownTicks);
		}
	}

	private void dropPunishers(GalactusEvent ev, ServerLevel orbit, BlockPos o, int n) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		for (int i = 0; i < n && ev.orbitPunishers(orbit).size() < c.punisherCap; i++) {
			Vec3 at = OrbitArena.hullSpot(o, 6 + orbit.random.nextDouble() * 20, orbit.random.nextDouble() * Math.PI * 2).add(0, 30, 0);
			PunisherRobotEntity p = GalactusEntityTypes.PUNISHER.create(orbit);
			if (p == null) {
				return;
			}
			p.moveTo(at.x, at.y, at.z, orbit.random.nextFloat() * 360f, 0f);
			p.finalizeSpawn(orbit, orbit.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
			p.configure(GalactusConfig.punisherHealthFor(ev.fighterCount()));
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
			ev.adoptOrbit(p);
			orbit.addFreshEntity(p);
			GalactusFx.forced(orbit, ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 0.3, 6, 0.3, 0.02);
		}
	}

	// ---------------------------------------------------------------- PLANET CRACKER

	public double crackerNeeded(int fighters) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		return c.crackerHandHealth + c.crackerPerExtraFighter * Math.max(0, fighters - 1);
	}

	public float crackerProgress(long now) {
		return move == Move.CRACKER ? (float) Mth.clamp((now - moveAt) / (double) GalactusConfig.stage3().crackerTicks, 0, 1) : 0f;
	}

	private void cracker(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, BlockPos o, List<ServerPlayer> fighters, long in, long now) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		Vec3 toCentre = Vec3.atCenterOf(o).subtract(d.position()).multiply(1, 0, 1).normalize();
		Vec3 sideV = new Vec3(-toCentre.z, 0, toCentre.x);
		Vec3 core = d.pose().chest.add(toCentre.scale(16)).add(0, 4, 0);
		d.setLean(Mth.lerp(0.08f, d.lean(), 0.1f));
		d.setLook(core);
		d.setEyes(1f);
		float k = Mth.clamp(in / 30f, 0, 1);
		Vec3 hr = core.add(sideV.scale(side(d, core.add(sideV)) > 0 ? 5 : -5));
		Vec3 hl = core.add(sideV.scale(side(d, core.add(sideV)) > 0 ? -5 : 5));
		d.setArm(true, crackBrokeR ? hr.add(0, -16, 0) : hr, crackBrokeR ? 0.5f : k);
		d.setArm(false, crackBrokeL ? hl.add(0, -16, 0) : hl, crackBrokeL ? 0.5f : k);
		d.setFlag(DevourerEntity.FLAG_FIST_R, false);
		d.setFlag(DevourerEntity.FLAG_FIST_L, false);
		float prog = crackerProgress(now);
		d.setCharge(crackBrokeR && crackBrokeL ? Math.max(0f, d.charge() - 0.08f) : prog);
		if (now % 8 == 0) {
			orbit.playSound(null, core.x, core.y, core.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 6.0f, 0.5f + prog);
		}
		if (crackBrokeR && crackBrokeL) {
			// broken: the sphere implodes and he reels
			if (d.charge() <= 0.01f) {
				GalactusFx.forced(orbit, GalactusFx.COSMIC, core.x, core.y, core.z, 200, 6, 6, 6, 0.4);
				orbit.playSound(null, core.x, core.y, core.z, SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 8.0f, 0.4f);
				ev.titleOrbit(orbit, Component.translatable("title.projecthero.galactus.cracker_broken").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), null);
				end(now, 100);
			}
			return;
		}
		if (in >= c.crackerTicks) {
			// it fires
			GalactusFx.forced(orbit, ParticleTypes.FLASH, core.x, core.y, core.z, 6, 2, 2, 2, 0);
			GalactusFx.forced(orbit, ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 6, 6, 2, 6, 0);
			orbit.playSound(null, core.x, core.y, core.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 10.0f, 0.3f);
			orbit.playSound(null, core.x, core.y, core.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 10.0f, 0.5f);
			for (ServerPlayer p : fighters) {
				QuirkCamera.vignette(p, 0xFFFFFF, 1f, 20);
				QuirkCamera.shake(p, 10f, 30);
				p.hurt(orbit.damageSources().mobAttack(d), (float) (p.getMaxHealth() * c.crackerMaxHealthFraction));
			}
			end(now, c.moveCooldownTicks);
		}
	}

	// ---------------------------------------------------------------- damage

	/** A hit on one of his parts. True if it took. */
	public boolean onHit(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, DevourerPartEntity.Kind kind, DamageSource source, float amount) {
		GalactusConfig.Stage3 c = GalactusConfig.stage3();
		if (finale) {
			return false;
		}
		long now = orbit.getGameTime();
		int n = Math.max(1, ev.orbitFighters(orbit).size());
		double mult;
		switch (kind) {
			case HEAD -> mult = gag ? c.headGagMultiplier : c.headMultiplier;
			case CHEST -> {
				if (!(armBroke[0] && armBroke[1])) {
					if (source.getEntity() instanceof ServerPlayer p) {
						p.displayClientMessage(Component.translatable("message.projecthero.galactus.core_sealed").withStyle(ChatFormatting.AQUA), true);
					}
					Vec3 ch = d.pose().chest;
					orbit.sendParticles(GalactusFx.BLUE, ch.x, ch.y, ch.z, 14, 3, 3, 3, 0.05);
					orbit.playSound(null, ch.x, ch.y, ch.z, SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 2.0f, 0.5f);
					return false;
				}
				mult = c.coreMultiplier;
			}
			case HAND_R, HAND_L -> mult = c.handMultiplier;
			default -> mult = c.armMultiplier;
		}
		double dealt = amount * mult;
		// Devour: head hits choke him
		if (kind == DevourerPartEntity.Kind.HEAD && move == Move.DEVOUR && !gag) {
			choke += dealt;
		}
		// the grip: hits on the grabbing hand
		if (move == Move.GRAB && victim != null && kind.hand() && (kind == DevourerPartEntity.Kind.HAND_R) == right) {
			grip += amount;
			if (grip >= c.gripHealth + c.gripPerExtraFighter * Math.max(0, n - 1)) {
				ServerPlayer v = victim(orbit);
				if (v != null) {
					v.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
					v.displayClientMessage(Component.translatable("message.projecthero.galactus.freed").withStyle(ChatFormatting.GREEN), true);
				}
				victim = null;
				orbit.playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 4.0f, 0.5f);
				moveAt = now - (25 + 6 + 40 + 30) - 1;
			}
		}
		// the Planet Cracker: break both hands
		if (move == Move.CRACKER && kind.hand()) {
			double need = crackerNeeded(n);
			if (kind == DevourerPartEntity.Kind.HAND_R && !crackBrokeR) {
				crackR += amount;
				if (crackR >= need) {
					crackBrokeR = true;
					handBreakFx(orbit, d.pose().fistR);
				}
			} else if (kind == DevourerPartEntity.Kind.HAND_L && !crackBrokeL) {
				crackL += amount;
				if (crackL >= need) {
					crackBrokeL = true;
					handBreakFx(orbit, d.pose().fistL);
				}
			}
		}
		// a hand hit mid-wind interrupts a hammer
		if (move == Move.HAMMER && kind.hand() && now - moveAt < 30 && (kind == DevourerPartEntity.Kind.HAND_R) == right && amount >= 8) {
			moveAt = now - (30 + 6 + 60);
			orbit.playSound(null, d.getX(), d.getY() + 60, d.getZ(), SoundEvents.ANVIL_PLACE, SoundSource.HOSTILE, 3.0f, 0.5f);
		}
		// gauntlets
		if (kind.rightArm() || kind.leftArm()) {
			int s = kind.rightArm() ? 0 : 1;
			if (!limp(s, now)) {
				armDamage[s] += dealt;
				if (armDamage[s] >= c.gauntletBreak + c.gauntletPerExtraFighter * Math.max(0, n - 1)) {
					armDamage[s] = 0;
					limpUntil[s] = now + c.limpTicks;
					boolean first = !armBroke[s];
					armBroke[s] = true;
					Vec3 f = s == 0 ? d.pose().fistR : d.pose().fistL;
					handBreakFx(orbit, f);
					ev.titleOrbit(orbit, Component.translatable(s == 0 ? "title.projecthero.galactus.gauntlet_r" : "title.projecthero.galactus.gauntlet_l")
							.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), null);
					if (first && armBroke[0] && armBroke[1]) {
						ev.titleOrbit(orbit, Component.translatable("title.projecthero.galactus.core_open").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD),
								Component.translatable("title.projecthero.galactus.core_open.sub").withStyle(ChatFormatting.WHITE));
					}
					if (move == Move.HAMMER || move == Move.SWEEP || move == Move.GRAB) {
						if ((s == 0) == right) {
							if (victim != null) {
								ServerPlayer v = victim(orbit);
								if (v != null) {
									v.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
								}
							}
							end(now, 40);
						}
					}
				}
			}
		}
		health = Math.max(0, health - dealt);
		ev.devourerThreat(orbit, d, source, (float) dealt);
		if (!cracked && fraction() <= 0.5) {
			cracked = true;
			Vec3 face = d.pose().face;
			orbit.playSound(null, face.x, face.y, face.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 10.0f, 0.3f);
			GalactusFx.forced(orbit, ParticleTypes.EXPLOSION, face.x, face.y + 6, face.z, 6, 4, 3, 4, 0);
			ev.titleOrbit(orbit, Component.translatable("title.projecthero.galactus.cracked").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
					Component.translatable("title.projecthero.galactus.cracked.sub").withStyle(ChatFormatting.GRAY));
		}
		if (fraction() <= c.finaleFraction) {
			health = maxHealth * c.finaleFraction;
			beginFinale(ev, orbit, d, now);
		}
		return true;
	}

	private void handBreakFx(ServerLevel orbit, Vec3 at) {
		GalactusFx.forced(orbit, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 6, 3, 3, 3, 0);
		GalactusFx.forced(orbit, ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 40, 4, 4, 4, 0.3);
		orbit.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.HOSTILE, 6.0f, 0.4f);
		orbit.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_HURT, SoundSource.HOSTILE, 6.0f, 0.6f);
	}

	// ---------------------------------------------------------------- the finale

	private void beginFinale(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, long now) {
		if (finale) {
			return;
		}
		finale = true;
		ServerPlayer v = victim(orbit);
		if (v != null) {
			v.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0));
		}
		victim = null;
		move = Move.NONE;
		moveAt = now;
		d.setCharge(0f);
		ev.onFinale(orbit);
	}

	private void finaleTick(GalactusEvent ev, ServerLevel orbit, DevourerEntity d, BlockPos o, long now) {
		raise = Mth.lerp(0.05f, raise, 4f);
		place(o, d);
		d.setLean(Mth.lerp(0.05f, d.lean(), 0.55f));
		// both hands on the deck's rim, holding himself up
		double a = bodyAngle;
		d.setArm(true, OrbitArena.hullSpot(o, 31, a - 0.3).add(0, 0.5, 0), 1f);
		d.setArm(false, OrbitArena.hullSpot(o, 31, a + 0.3).add(0, 0.5, 0), 1f);
		d.setFlag(DevourerEntity.FLAG_FIST_R, false);
		d.setFlag(DevourerEntity.FLAG_FIST_L, false);
		d.setEyes(0.3f + 0.25f * Mth.sin(now * 0.3f));
		ServerPlayer holder = ev.nullifierHolder(orbit);
		d.setLook(holder != null ? holder.getEyePosition() : Vec3.atCenterOf(o));
		if (holder != null && now % 70 == 0) {
			OrbitRules.beam(orbit, d, holder, (float) GalactusConfig.stage3().finaleBeamDamage);
		}
	}

	// ---------------------------------------------------------------- save

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putDouble("Health", health);
		t.putDouble("Max", maxHealth);
		t.putDouble("Angle", bodyAngle);
		t.putBoolean("Cracked", cracked);
		t.putBoolean("BrokeR", armBroke[0]);
		t.putBoolean("BrokeL", armBroke[1]);
		t.putBoolean("Finale", finale);
		return t;
	}

	public void load(CompoundTag t) {
		health = t.getDouble("Health");
		maxHealth = t.getDouble("Max");
		bodyAngle = t.contains("Angle") ? t.getDouble("Angle") : Math.PI / 2;
		cracked = t.getBoolean("Cracked");
		armBroke[0] = t.getBoolean("BrokeR");
		armBroke[1] = t.getBoolean("BrokeL");
		finale = t.getBoolean("Finale");
		move = Move.NONE;
		cooldown = 60;
	}

	/** For the HUD: the Planet Cracker's two hand meters (0..1 each), or null. */
	public float[] crackerHands(int fighters) {
		if (move != Move.CRACKER) {
			return null;
		}
		double need = crackerNeeded(fighters);
		return new float[] { crackBrokeR ? 1f : (float) Mth.clamp(crackR / need, 0, 1), crackBrokeL ? 1f : (float) Mth.clamp(crackL / need, 0, 1) };
	}

	public double choke() {
		return choke;
	}

	public boolean gagging() {
		return gag;
	}
}
