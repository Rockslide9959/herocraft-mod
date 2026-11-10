package com.projecthero.mod.undead;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user): Undead's blood moves, all built on {@link UndeadEject}.
 * <ul>
 *   <li><b>Blood Spray</b> (hold G): both hands blow off with half the forearms and a 2-block-wide stream of blood pours out of
 *       the stumps along the aim, up to {@link UndeadEjectConfig#SPRAY_RANGE} (a wall stops it). Everything in it takes
 *       {@link UndeadEjectConfig#SPRAY_DAMAGE} every {@link UndeadEjectConfig#SPRAY_HIT_EVERY} ticks; sprayed for longer than
 *       1.5 s it is slowed and blinded. Held as long as he likes; let go: {@link UndeadEjectConfig#SPRAY_COOLDOWN}.</li>
 *   <li><b>Blood Whirlwind</b> (Shift+G): hands and feet off, the feet's blast lifts him {@link UndeadEjectConfig#WHIRL_RISE}
 *       blocks, then he spins with his arms out, two streams of blood sweeping round: {@link UndeadEjectConfig#WHIRL_DAMAGE} to
 *       everything within {@link UndeadEjectConfig#WHIRL_RADIUS} (once each, as a stream passes it).</li>
 *   <li><b>Deadline</b> (hold C): one hand off and a thin pressurised stream of blood from the stump:
 *       {@link UndeadEjectConfig#DEADLINE_DAMAGE} every {@link UndeadEjectConfig#DEADLINE_HIT_EVERY} ticks, him slowed 80% while
 *       it runs, at most 8 s; let go (or out of time): {@link UndeadEjectConfig#DEADLINE_COOLDOWN}. It reuses the old Dead Line's
 *       synced flag ({@link UndeadState#FLAG_DEAD_LINE}) so every viewer draws the beam.</li>
 * </ul>
 * Static state: dropped by {@link #clearSessionState} / {@link #forget} (from {@link UndeadAbilityManager}).
 */
public final class UndeadBloodMoves {
	public static final String SPRAY = "blood_spray";
	public static final String WHIRL = "blood_whirlwind";
	public static final String DEADLINE = "deadline";

	private static final ResourceLocation DEADLINE_SLOW = PowerToggles.id("undead_deadline_slow");

	/** Blood Spray: when it started, and per creature in the stream {first contact, last contact}. */
	private static final class Spray {
		final long start;
		final Map<Integer, long[]> contact = new HashMap<>();

		Spray(long start) {
			this.start = start;
		}
	}

	/** Blood Whirlwind: when it started, the height it rises from, the spin angle last tick, who it has hit. */
	private static final class Whirl {
		final long start;
		final double baseY;
		final float yaw0;
		double lastAngle = Double.NaN;
		final Set<Integer> hit = new HashSet<>();

		Whirl(long start, double baseY, float yaw0) {
			this.start = start;
			this.baseY = baseY;
			this.yaw0 = yaw0;
		}
	}

	private static final Map<UUID, Spray> SPRAYS = new ConcurrentHashMap<>();
	private static final Map<UUID, Whirl> WHIRLS = new ConcurrentHashMap<>();

	private UndeadBloodMoves() {
	}

	static void clearSessionState() {
		SPRAYS.clear();
		WHIRLS.clear();
	}

	static void forget(UUID id) {
		SPRAYS.remove(id);
		WHIRLS.remove(id);
	}

	// ---------------------------------------------------------------- shared

	private static boolean ready(ServerPlayer p, String id) {
		if (!Undead.canAct(p)) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, id);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + id),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		return true;
	}

	private static void cooldown(ServerPlayer p, String id, int ticks) {
		UndeadState n = Undead.state(p).copy();
		n.abilityReadyAt.put(id, p.level().getGameTime() + ticks);
		Undead.save(p, n);
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static boolean anyArm(ServerPlayer p) {
		return !Undead.missing(p, UndeadState.RIGHT_ARM) || !Undead.missing(p, UndeadState.LEFT_ARM);
	}

	/**
	 * Where a forearm stump held out along the aim is (client-safe; the server uses the body's facing): off the eye to the arm's
	 * side, a little down and forward. Both streams start here; {@code arm} -1 is between the two (the spray's axis).
	 */
	public static Vec3 stumpOrigin(Player p, int arm, Vec3 eye, Vec3 look) {
		Vec3 r = UndeadParts.right(p);
		double side = arm == UndeadState.RIGHT_ARM ? 1 : arm == UndeadState.LEFT_ARM ? -1 : 0;
		return eye.add(r.scale(0.3 * side)).add(0, -0.34, 0).add(look.scale(0.42));
	}

	/** Where the spray's axis starts and ends this tick (the first block along the aim stops it), from the eye's aim. */
	public static Vec3[] sprayAxis(Player p, Vec3 eye, Vec3 look) {
		Vec3 from = stumpOrigin(p, -1, eye, look);
		// onto the crosshair (the stumps sit below the eye), straight along the look when that is too close
		Vec3 aim = crosshair(p, eye, look, UndeadEjectConfig.SPRAY_RANGE).subtract(from);
		Vec3 dir = aim.lengthSqr() < 1.0 || aim.normalize().dot(look) < 0.7 ? look : aim.normalize();
		Vec3 far = from.add(dir.scale(UndeadEjectConfig.SPRAY_RANGE));
		HitResult hit = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return new Vec3[] { from, hit.getType() == HitResult.Type.MISS ? far : hit.getLocation() };
	}

	/** The stream's half-width {@code t} of the way along it (0 at the stumps .. 1 at full range). */
	public static double sprayRadius(double t) {
		return UndeadEjectConfig.SPRAY_RADIUS_NEAR + (UndeadEjectConfig.SPRAY_RADIUS - UndeadEjectConfig.SPRAY_RADIUS_NEAR) * Mth.clamp(t, 0.0, 1.0);
	}

	// ---------------------------------------------------------------- G: Blood Spray

	public static boolean spraying(Player p) {
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.flag(UndeadState.FLAG_BLOOD_SPRAY);
	}

	public static boolean startSpray(ServerPlayer p) {
		if (spraying(p) || !ready(p, SPRAY)) {
			return false;
		}
		if (!anyArm(p)) {
			Undead.say(p, "message.projecthero.undead.no_arm", ChatFormatting.GRAY);
			return false;
		}
		if (UndeadLaunch.launching(p) || WHIRLS.containsKey(p.getUUID())) {
			return false;
		}
		stopDeadline(p);
		UndeadEject.hold(p, UndeadEject.Hold.SPRAY);
		UndeadEject.eject(p, UndeadEject.HANDS, true);
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadState.FLAG_BLOOD_SPRAY, true);
		Undead.save(p, n);
		SPRAYS.put(p.getUUID(), new Spray(p.level().getGameTime()));
		UndeadSfx.jetStart(p.level(), p.getEyePosition());
		return true;
	}

	/** G let go (or the spray could not go on): the stream stops and the cooldown starts. */
	public static void stopSpray(ServerPlayer p) {
		Spray sp = SPRAYS.remove(p.getUUID());
		UndeadState s = Undead.state(p);
		boolean was = s.flag(UndeadState.FLAG_BLOOD_SPRAY) || sp != null;
		if (s.flag(UndeadState.FLAG_BLOOD_SPRAY)) {
			UndeadState n = s.copy();
			n.setFlag(UndeadState.FLAG_BLOOD_SPRAY, false);
			Undead.save(p, n);
		}
		UndeadEject.release(p, UndeadEject.Hold.SPRAY);
		if (was) {
			cooldown(p, SPRAY, UndeadEjectConfig.SPRAY_COOLDOWN);
			UndeadSfx.jetStop(p.level(), p.getEyePosition());
		}
	}

	private static void tickSpray(ServerPlayer p, UndeadState s, long now) {
		Spray sp = SPRAYS.get(p.getUUID());
		if (!s.flag(UndeadState.FLAG_BLOOD_SPRAY) || sp == null) {
			if (sp != null || s.flag(UndeadState.FLAG_BLOOD_SPRAY) || UndeadEject.holding(p, UndeadEject.Hold.SPRAY)) {
				stopSpray(p); // one side went missing (a shatter cleared the flag): tidy up
			}
			return;
		}
		if (!p.isAlive() || p.isSpectator() || s.reformUntil > now || !anyArm(p) || s.broken(UndeadState.HEAD)) {
			stopSpray(p);
			return;
		}
		long age = now - sp.start;
		Vec3 look = p.getLookAngle();
		Vec3[] axis = sprayAxis(p, p.getEyePosition(), look);
		Vec3 from = axis[0];
		double len = from.distanceTo(axis[1]);
		if (len > 1.0e-3) {
			look = axis[1].subtract(from).scale(1.0 / len);
		}
		boolean hitTick = age > 0 && age % UndeadEjectConfig.SPRAY_HIT_EVERY == 0;
		AABB box = new AABB(from, axis[1]).inflate(UndeadEjectConfig.SPRAY_RADIUS + 0.5);
		for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, box,
				e -> e != p && e.isAlive() && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e))) {
			if (!inStream(p, e, from, look, len)) {
				continue;
			}
			long[] c = sp.contact.get(e.getId());
			if (c == null || now - c[1] > UndeadEjectConfig.SPRAY_CONTACT_GAP) {
				c = new long[] { now, now };
				sp.contact.put(e.getId(), c);
			}
			c[1] = now;
			if (hitTick) {
				UndeadAbilities.strike(p, e, from, UndeadEjectConfig.SPRAY_DAMAGE, 0.12, 0.0);
			}
			if (now - c[0] >= UndeadEjectConfig.SPRAY_AFFLICT_AFTER) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, UndeadEjectConfig.SPRAY_EFFECT_TICKS, UndeadEjectConfig.SPRAY_SLOWNESS_LEVEL);
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, UndeadEjectConfig.SPRAY_EFFECT_TICKS, 0);
			}
		}
		if (age % 40 == 0) {
			sp.contact.values().removeIf(c -> now - c[1] > UndeadEjectConfig.SPRAY_CONTACT_GAP);
		}
		// the hiss and patter of the stream (quiet; the stream itself is drawn client-side)
		if (age % 7 == 0) {
			p.level().playSound(null, axis[1].x, axis[1].y, axis[1].z, UndeadSoundEvents.BLOOD_SPRAY, SoundSource.PLAYERS, 0.32f,
					0.85f + p.getRandom().nextFloat() * 0.2f);
		}
	}

	/** {@code e}'s box reaches into the stream (a cone from the stumps along the aim, cut off at {@code len}), in plain sight. */
	static boolean inStream(ServerPlayer p, LivingEntity e, Vec3 from, Vec3 dir, double len) {
		AABB b = e.getBoundingBox();
		Vec3 c = b.getCenter();
		double t = c.subtract(from).dot(dir);
		if (t < -0.3 || t > len + 0.5) {
			return false;
		}
		Vec3 onAxis = from.add(dir.scale(Mth.clamp(t, 0.0, len)));
		Vec3 nearest = new Vec3(Mth.clamp(onAxis.x, b.minX, b.maxX), Mth.clamp(onAxis.y, b.minY, b.maxY), Mth.clamp(onAxis.z, b.minZ, b.maxZ));
		double r = sprayRadius(t / UndeadEjectConfig.SPRAY_RANGE);
		if (nearest.distanceToSqr(onAxis) > r * r) {
			return false;
		}
		return p.level().clip(new ClipContext(onAxis, nearest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() == HitResult.Type.MISS;
	}

	// ---------------------------------------------------------------- Shift+G: Blood Whirlwind

	public static boolean whirling(ServerPlayer p) {
		return WHIRLS.containsKey(p.getUUID());
	}

	public static boolean startWhirl(ServerPlayer p) {
		if (WHIRLS.containsKey(p.getUUID()) || !ready(p, WHIRL)) {
			return false;
		}
		UndeadState s = Undead.state(p);
		if (s.broken(UndeadState.HEAD) || s.broken(UndeadState.BODY)) {
			Undead.say(p, "message.projecthero.undead.no_body", ChatFormatting.GRAY);
			return false;
		}
		if (!anyArm(p)) {
			Undead.say(p, "message.projecthero.undead.no_arm", ChatFormatting.GRAY);
			return false;
		}
		if (UndeadLaunch.launching(p)) {
			return false;
		}
		UndeadFlight.stop(p);
		stopSpray(p);
		stopDeadline(p);
		UndeadEject.hold(p, UndeadEject.Hold.WHIRL);
		UndeadEject.eject(p, UndeadEject.HANDS | UndeadEject.FEET, true);
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.abilityReadyAt.put(WHIRL, now + UndeadEjectConfig.WHIRL_COOLDOWN);
		n.animId = UndeadState.ANIM_BLOOD_WHIRL;
		n.animStart = now;
		Undead.save(p, n);
		WHIRLS.put(p.getUUID(), new Whirl(now, p.getY(), p.yBodyRot));
		p.resetFallDistance();
		ServerLevel level = level(p);
		Vec3 feet = p.position().add(0, 0.2, 0);
		level.sendParticles(Undead.BLOOD, feet.x, feet.y, feet.z, 10, 0.3, 0.05, 0.3, 0.0);
		UndeadSfx.launch(level, feet);
		UndeadSfx.jetStart(level, p.position().add(0, 1.0, 0));
		return true;
	}

	/** The spin: 0 before it starts, rising to {@link UndeadEjectConfig#WHIRL_TURNS} turns (radians) by the end. Client-safe. */
	public static float whirlAngle(float age) {
		float spin = UndeadEjectConfig.WHIRL_TICKS - UndeadEjectConfig.WHIRL_RISE_TICKS;
		float f = Mth.clamp((age - UndeadEjectConfig.WHIRL_RISE_TICKS * 0.5f) / spin, 0f, 1f);
		// eases in, runs flat out, eases off a little at the end
		float e = f < 0.15f ? f * f / 0.3f : f > 0.9f ? 1f - (1f - f) * (1f - f) / 0.2f - 0.025f : f - 0.075f;
		return Mth.TWO_PI * UndeadEjectConfig.WHIRL_TURNS * e / 0.95f;
	}

	private static void endWhirl(ServerPlayer p) {
		if (WHIRLS.remove(p.getUUID()) != null) {
			UndeadSfx.jetStop(p.level(), p.position().add(0, 1.0, 0));
		}
		UndeadEject.release(p, UndeadEject.Hold.WHIRL);
		p.resetFallDistance();
	}

	private static void tickWhirl(ServerPlayer p, UndeadState s, long now) {
		Whirl w = WHIRLS.get(p.getUUID());
		if (w == null) {
			if (UndeadEject.holding(p, UndeadEject.Hold.WHIRL)) {
				UndeadEject.release(p, UndeadEject.Hold.WHIRL);
			}
			return;
		}
		long age = now - w.start;
		if (age >= UndeadEjectConfig.WHIRL_TICKS || !p.isAlive() || p.isSpectator() || s.reformUntil > now || s.broken(UndeadState.HEAD)) {
			endWhirl(p);
			return;
		}
		// the feet's blast lifts him WHIRL_RISE and holds him there (the server sets his velocity each tick, as the launches do)
		float k = Mth.clamp((age + 1) / (float) UndeadEjectConfig.WHIRL_RISE_TICKS, 0f, 1f);
		double wantY = w.baseY + UndeadEjectConfig.WHIRL_RISE * (1 - (1 - k) * (1 - k));
		double vy = Mth.clamp(wantY - p.getY(), -0.3, 0.6);
		AbilityHelpers.launchSelf(p, new Vec3(0, vy, 0));
		p.resetFallDistance();
		// the two streams sweep round: everything they pass (once each, in plain sight) is hit
		double angle = Math.toRadians(w.yaw0) + whirlAngle(age);
		if (age >= UndeadEjectConfig.WHIRL_RISE_TICKS / 2 && !Double.isNaN(w.lastAngle)) {
			double step = Math.abs(angle - w.lastAngle);
			Vec3 chest = p.position().add(0, 1.1, 0);
			double r = UndeadEjectConfig.WHIRL_RADIUS;
			for (LivingEntity e : level(p).getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 4.0, r),
					e -> e != p && e.isAlive() && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, e) && !w.hit.contains(e.getId()))) {
				Vec3 c = e.getBoundingBox().getCenter();
				Vec3 flat = new Vec3(c.x - chest.x, 0, c.z - chest.z);
				if (flat.lengthSqr() > r * r) {
					continue;
				}
				boolean swept = flat.lengthSqr() < 1.2;
				if (!swept) {
					// the arms point along the body's sides: yaw + 90 (left) and yaw - 90 (right) -- either one counts
					double bearing = Math.atan2(-flat.x, flat.z);
					for (int arm = 0; arm < 2 && !swept; arm++) {
						double armAt = angle + Math.PI * 0.5 + arm * Math.PI;
						double d = Mth.wrapDegrees(Math.toDegrees(armAt - bearing));
						swept = Math.abs(d) <= Math.toDegrees(step) + 14.0;
					}
				}
				if (!swept || p.level().clip(new ClipContext(chest, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getType() != HitResult.Type.MISS) {
					continue;
				}
				w.hit.add(e.getId());
				UndeadAbilities.strike(p, e, chest, UndeadEjectConfig.WHIRL_DAMAGE, 0.8, 0.2);
			}
		}
		w.lastAngle = angle;
		if (age % 5 == 0) {
			p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), UndeadSoundEvents.BLOOD_SPRAY, SoundSource.PLAYERS, 0.3f, 0.8f + age * 0.01f);
		}
	}

	// ---------------------------------------------------------------- C: Deadline

	public static boolean deadlining(Player p) {
		UndeadState s = Undead.peek(p);
		return s != null && s.hasPower && s.flag(UndeadState.FLAG_DEAD_LINE);
	}

	public static boolean startDeadline(ServerPlayer p) {
		if (deadlining(p) || !ready(p, DEADLINE)) {
			return false;
		}
		int hand = UndeadEject.oneHand(p);
		if (hand < 0) {
			Undead.say(p, "message.projecthero.undead.no_arm", ChatFormatting.GRAY);
			return false;
		}
		if (UndeadLaunch.launching(p) || WHIRLS.containsKey(p.getUUID()) || spraying(p)) {
			return false;
		}
		UndeadEject.hold(p, UndeadEject.Hold.DEADLINE);
		UndeadEject.eject(p, UndeadEject.ONE_HAND, true);
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadState.FLAG_DEAD_LINE, true);
		n.setFlag(UndeadState.FLAG_OFF_ARM, UndeadState.limbOf(hand) != Undead.mainArm(p));
		n.holdStart = p.level().getGameTime();
		Undead.save(p, n);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, DEADLINE_SLOW, UndeadEjectConfig.DEADLINE_SELF_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), UndeadSoundEvents.BLOOD_JET_START, SoundSource.PLAYERS, 0.5f, 1.5f);
		return true;
	}

	/** C let go (or 8 s up): the stream stops and the cooldown starts. */
	public static void stopDeadline(ServerPlayer p) {
		UndeadState s = Undead.state(p);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, DEADLINE_SLOW);
		boolean was = s.flag(UndeadState.FLAG_DEAD_LINE);
		if (was) {
			UndeadState n = s.copy();
			n.setFlag(UndeadState.FLAG_DEAD_LINE, false);
			n.abilityReadyAt.put(DEADLINE, p.level().getGameTime() + UndeadEjectConfig.DEADLINE_COOLDOWN);
			Undead.save(p, n);
			p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), UndeadSoundEvents.BLOOD_JET_STOP, SoundSource.PLAYERS, 0.45f, 1.5f);
		}
		UndeadEject.release(p, UndeadEject.Hold.DEADLINE);
	}

	/** The arm the Deadline fires from (client-safe): the one {@link UndeadState#FLAG_OFF_ARM} names. */
	public static int deadlineArm(Player p, UndeadState s) {
		return s.flag(UndeadState.FLAG_OFF_ARM) ? Undead.otherArm(Undead.mainArm(p)) : Undead.mainArm(p);
	}

	/**
	 * Where the crosshair is (client-safe): the first block or creature along the eye's look within {@code range}, else the far
	 * end of the look. A stream from a stump off to the side aims here, so it lands on the crosshair rather than beside it.
	 */
	public static Vec3 crosshair(Player p, Vec3 eye, Vec3 look, double range) {
		Vec3 far = eye.add(look.scale(range));
		HitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && e != p && !e.isSpectator() && e.isPickable() && !(e instanceof UndeadCloneEntity), eye.distanceToSqr(end));
		return ehr != null ? ehr.getLocation() : end;
	}

	/** The Deadline's direction from its stump: onto the crosshair (straight along the look when that is too close). */
	public static Vec3 deadlineDir(Player p, Vec3 from, Vec3 eye, Vec3 look) {
		Vec3 d = crosshair(p, eye, look, UndeadEjectConfig.DEADLINE_RANGE).subtract(from);
		if (d.lengthSqr() < 1.0) {
			return look;
		}
		d = d.normalize();
		return d.dot(look) < 0.7 ? look : d;
	}

	/** The stream this tick: from the stump to the first block or creature along its line onto the crosshair. */
	public static Vec3[] deadlineBeam(ServerPlayer p, LivingEntity[] hitOut) {
		Vec3 look = p.getLookAngle();
		Vec3 eye = p.getEyePosition();
		Vec3 from = stumpOrigin(p, deadlineArm(p, Undead.state(p)), eye, look);
		Vec3 far = from.add(deadlineDir(p, from, eye, look).scale(UndeadEjectConfig.DEADLINE_RANGE));
		HitResult bhr = p.level().clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, from, end, new AABB(from, end).inflate(1.0),
				e -> e instanceof LivingEntity le && !(e instanceof UndeadCloneEntity) && HeroTargets.canHarm(p, le) && e.isPickable(), from.distanceToSqr(end));
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			end = ehr.getLocation();
			hitOut[0] = le;
		}
		return new Vec3[] { from, end };
	}

	private static void tickDeadline(ServerPlayer p, UndeadState s, long now) {
		if (!s.flag(UndeadState.FLAG_DEAD_LINE)) {
			if (UndeadEject.holding(p, UndeadEject.Hold.DEADLINE)) {
				stopDeadline(p); // the flag went some other way (a lost arm, a shatter): tidy up
			} else if (p.tickCount % 20 == 0) {
				PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, DEADLINE_SLOW);
			}
			return;
		}
		long age = now - s.holdStart;
		int arm = deadlineArm(p, s);
		if (age >= UndeadEjectConfig.DEADLINE_MAX || s.broken(arm) || !p.isAlive() || s.reformUntil > now) {
			stopDeadline(p);
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 from = stumpOrigin(p, arm, p.getEyePosition(), look);
		com.projecthero.mod.beamclash.BeamClash.report(p, from, deadlineDir(p, from, p.getEyePosition(), look), UndeadEjectConfig.DEADLINE_RANGE, 1.0f, 0xC0101A);
		LivingEntity[] hit = new LivingEntity[1];
		Vec3[] seg = deadlineBeam(p, hit);
		if (hit[0] != null && age > 0 && age % UndeadEjectConfig.DEADLINE_HIT_EVERY == 0 && !com.projecthero.mod.beamclash.BeamClash.inClash(p)) {
			UndeadAbilities.strike(p, hit[0], from, UndeadEjectConfig.DEADLINE_DAMAGE, 0.12, 0.0);
		}
		if (age % 3 == 0) {
			level(p).sendParticles(Undead.BLOOD, seg[1].x, seg[1].y, seg[1].z, 2, 0.08, 0.08, 0.08, 0.0);
		}
		if (age % 8 == 0) {
			p.level().playSound(null, seg[0].x, seg[0].y, seg[0].z, UndeadSoundEvents.BLOOD_SPRAY, SoundSource.PLAYERS, 0.22f, 1.7f);
		}
	}

	// ---------------------------------------------------------------- tick

	/** From {@link UndeadAbilityManager#tickHold}. */
	static void tick(ServerPlayer p) {
		long now = p.level().getGameTime();
		tickSpray(p, Undead.state(p), now);
		tickWhirl(p, Undead.state(p), now);
		tickDeadline(p, Undead.state(p), now);
	}

	/** Ends every blood move at once (the Bone Sword drawn, a screen's release gone missing). */
	static void stopAll(ServerPlayer p) {
		stopSpray(p);
		stopDeadline(p);
	}
}
