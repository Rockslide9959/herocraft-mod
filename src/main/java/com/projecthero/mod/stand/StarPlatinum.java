package com.projecthero.mod.stand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.stand.data.StandState;
import com.projecthero.mod.stand.entity.BallBearingEntity;
import com.projecthero.mod.stand.entity.StandEntities;
import com.projecthero.mod.stand.network.StandFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Star Platinum's moves (v0.17.2; v0.17.3 the user's rework) -- the first Stand. Key map in {@link StandAbilityManager};
 * numbers in {@link StandConfig}; every hit through {@link StandCombat#hit} (so a hit inside a time stop is queued). A move
 * pressed while the Stand is away summons it first. Every Stand sound / particle goes to Stand users only.
 */
public final class StarPlatinum {
	/** Something Star Platinum threw: who threw it, how long it is tracked, how hard it hits. */
	private record Thrown(UUID owner, long until, float damage) {
	}

	/** A Star Finger on its way out: where it starts, which way, how long it can get, when, and whom it has run through. */
	private record Finger(Vec3 origin, Vec3 dir, double length, long start, Set<Integer> hit) {
	}

	private static final Map<UUID, Thrown> THROWN = new ConcurrentHashMap<>();
	/** Super Inhale: who has been pulled in (left winded at the end). */
	private static final Map<UUID, Set<Integer>> INHALED = new ConcurrentHashMap<>();
	/** R went down at this game time and is still held (a tap or the start of the barrage). */
	private static final Map<UUID, Long> R_DOWN = new ConcurrentHashMap<>();
	/** ORA Barrage: each victim (entity id) and where it is held still. */
	private static final Map<UUID, Map<Integer, Vec3>> BARRAGED = new ConcurrentHashMap<>();
	/** Ora Rush: the dash direction. */
	private static final Map<UUID, Vec3> RUSH_DIR = new ConcurrentHashMap<>();
	private static final Map<UUID, Finger> FINGERS = new ConcurrentHashMap<>();

	private StarPlatinum() {
	}

	static void clearSessionState() {
		THROWN.clear();
		INHALED.clear();
		R_DOWN.clear();
		BARRAGED.clear();
		RUSH_DIR.clear();
		FINGERS.clear();
	}

	static void forget(UUID id) {
		R_DOWN.remove(id);
		BARRAGED.remove(id);
		RUSH_DIR.remove(id);
		FINGERS.remove(id);
		INHALED.remove(id);
	}

	// ---------------------------------------------------------------- geometry

	public static Vec3 forward(Player p) {
		float r = p.getYRot() * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
	}

	public static Vec3 rightOf(Vec3 fwd) {
		return new Vec3(-fwd.z, 0, fwd.x);
	}

	/**
	 * How far behind its user it floats while it copies them, and how far off the ground (blocks). v0.17.3 (user: "star platinum
	 * shouldn't walk behind the player, star platinum should float"): a little higher, so it reads as hovering.
	 */
	public static final double BEHIND = 1.0, BEHIND_UP = 0.3;

	/** Where the Stand floats at rest: directly behind its user (the model's feet). */
	public static Vec3 restPoint(Player p) {
		Vec3 f = forward(p);
		return p.position().subtract(f.scale(BEHIND)).add(0, BEHIND_UP, 0);
	}

	/** Where it stands when it fights: in front. */
	public static Vec3 frontPoint(Player p) {
		return p.position().add(forward(p).scale(1.0));
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? forward(p) : f.normalize();
	}

	/** Ora Chase: the way the Stand faces while it holds its catch -- away from its user. */
	public static Vec3 chaseFacing(Player p, Vec3 at) {
		Vec3 d = at.subtract(p.position());
		d = new Vec3(d.x, 0, d.z);
		return d.lengthSqr() < 1.0e-4 ? forward(p) : d.normalize();
	}

	/** The target under the crosshair within {@code range} (a little forgiving: else the nearest within 20 degrees). */
	static LivingEntity aimed(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		double maxDist = bh.getType() == HitResult.Type.MISS ? range : bh.getLocation().distanceTo(eye);
		end = eye.add(look.scale(maxDist));
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		LivingEntity cone = null;
		double coneD = Double.MAX_VALUE;
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5))) {
			if (!StandCombat.isTarget(p, e)) {
				continue;
			}
			AABB bb = e.getBoundingBox().inflate(0.3);
			var hit = bb.clip(eye, end);
			if (hit.isPresent() || bb.contains(eye)) {
				double d = hit.map(eye::distanceTo).orElse(0.0);
				if (d < bestD) {
					bestD = d;
					best = (LivingEntity) e;
				}
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d <= maxDist + e.getBbWidth() * 0.5 && to.normalize().dot(look) > 0.94 && d < coneD) {
				coneD = d;
				cone = (LivingEntity) e;
			}
		}
		return best != null ? best : cone;
	}

	/** Every target within Star Platinum's reach in a cone along {@code dir} (flat), up to a body's height off. */
	static List<LivingEntity> inFront(ServerPlayer p, Vec3 dir, double reach, double cos) {
		List<LivingEntity> out = new ArrayList<>();
		Vec3 base = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(reach + 1.0))) {
			if (!StandCombat.isTarget(p, e)) {
				continue;
			}
			Vec3 to = e.getBoundingBox().getCenter().subtract(base);
			if (Math.abs(to.y) > 2.2) {
				continue;
			}
			Vec3 flat = new Vec3(to.x, 0, to.z);
			double d = flat.length();
			if (d > reach + e.getBbWidth() * 0.5) {
				continue;
			}
			if (d < 0.6 || flat.normalize().dot(dir) >= cos) {
				out.add((LivingEntity) e);
			}
		}
		return out;
	}

	/** The barrage's direction: the look, or (auto-face) the nearest enemy in a wide cone. */
	private static Vec3 rushDir(ServerPlayer p, StandState s) {
		Vec3 look = flatLook(p);
		if (!s.autoFace) {
			return look;
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : inFront(p, look, StandConfig.REACH + 1.5, 0.5)) {
			double d = e.distanceToSqr(p);
			if (d < bestD && com.projecthero.mod.combat.HeroTargets.isHostile(p, e)) {
				bestD = d;
				best = e;
			}
		}
		if (best == null) {
			return look;
		}
		Vec3 to = best.position().subtract(p.position());
		to = new Vec3(to.x, 0, to.z);
		return to.lengthSqr() < 1.0e-4 ? look : to.normalize();
	}

	/** Sets a creature flying (its velocity replaced, not added to). Bosses are never thrown about. */
	private static void launch(LivingEntity e, Vec3 velocity) {
		if (StandCombat.isBoss(e)) {
			return;
		}
		e.setDeltaMovement(velocity);
		e.hurtMarked = true;
	}

	// ---------------------------------------------------------------- shared start

	/** Cooldown, Stamina, then the Stand comes out if it was away. False (with a message) if the move can't start. */
	private static boolean begin(ServerPlayer p, String moveId, float cost) {
		if (Stands.state(p).chasing()) {
			return false; // it is off chasing someone: nothing else until it is back
		}
		if (!Stands.ready(p, moveId)) {
			Stands.say(p, "message.projecthero.stand.cooldown", ChatFormatting.GRAY, (Stands.cooldownRemaining(p, moveId) + 19) / 20);
			return false;
		}
		if (!Stands.spendStamina(p, cost)) {
			return false;
		}
		if (!Stands.summoned(p)) {
			Stands.summon(p);
		}
		Stands.breakPose(p);
		return true;
	}

	private static ServerLevel level(ServerPlayer p) {
		return (ServerLevel) p.level();
	}

	private static void fx(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		Stands.sound(level(p), p.position().add(0, 1.0, 0), sound, volume, pitch);
	}

	private static void anim(StandState n, int animId, long now) {
		n.animId = animId;
		n.animStart = now;
	}

	// ---------------------------------------------------------------- empty-hand Stand punches (left click)

	/**
	 * A left-click on a creature with an empty hand while the Stand is out: Star Platinum punches instead -- a 3-hit chain,
	 * the 3rd a knockback blow. Called from {@code AttackEntityCallback} (server side); SUCCESS cancels the vanilla hit.
	 * (A left-click that lands on nothing, or on a block, is a punch the client draws from the swing: {@code StandSwing}.)
	 */
	public static InteractionResult onAttack(ServerPlayer p, Entity target) {
		StandState s = Stands.state(p);
		if (!s.isUser() || !s.summoned || s.chasing() || !p.getMainHandItem().isEmpty() || !(target instanceof LivingEntity le) || TimeStop.frozen(p)) {
			return InteractionResult.PASS;
		}
		long now = p.level().getGameTime();
		int step = now - s.comboAt <= StandConfig.PUNCH_COMBO_WINDOW && now >= s.comboAt ? (s.comboStep + 1) % 3 : 0;
		float strength = p.getAttackStrengthScale(0.5f);
		p.resetAttackStrengthTicker();
		float scale = 0.3f + 0.7f * strength * strength;
		boolean heavy = step == 2;
		float dmg = (heavy ? StandConfig.PUNCH_FINISHER_DAMAGE : StandConfig.PUNCH_DAMAGE) * scale;
		Vec3 knock = heavy ? StandCombat.away(p.position(), le, StandConfig.PUNCH_FINISHER_KNOCKBACK * scale, 0.3)
				: StandCombat.away(p.position(), le, 0.25, 0.05);
		StandCombat.hit(p, le, dmg, knock);
		StandState n = Stands.state(p).copy();
		n.comboStep = step;
		n.comboAt = now;
		anim(n, heavy ? StandState.ANIM_PUNCH_HEAVY : step == 0 ? StandState.ANIM_PUNCH_R : StandState.ANIM_PUNCH_L, now);
		Stands.save(p, n);
		Stands.breakPose(p);
		fx(p, heavy ? StandSounds.ORA_FINISHER : StandSounds.PUNCH, heavy ? 0.6f : 0.45f, 0.9f + p.getRandom().nextFloat() * 0.2f);
		return InteractionResult.SUCCESS;
	}

	// ---------------------------------------------------------------- R: tap = Platinum Smash, hold = ORA Barrage

	/** R down. Holding a creature it is Stand Grab's combo; Shift+R is Star Finger; else a tap or a hold decides on the way. */
	public static void rPressed(ServerPlayer p, boolean shift) {
		StandState s = Stands.state(p);
		if (s.grabbedEntity != -1) {
			grabCombo(p);
			return;
		}
		if (shift) {
			starFinger(p);
			return;
		}
		if (s.rushing || s.chasing()) {
			return;
		}
		R_DOWN.put(p.getUUID(), p.level().getGameTime());
	}

	/** R up: the barrage ends, or a short press was a tap. */
	public static void rReleased(ServerPlayer p) {
		Long down = R_DOWN.remove(p.getUUID());
		if (Stands.state(p).rushing) {
			stopBarrage(p);
			return;
		}
		if (down != null && p.level().getGameTime() - down < StandConfig.HOLD_THRESHOLD) {
			smash(p);
		}
	}

	/** Test hook: the barrage starts as if R had been held past the threshold. */
	public static void holdR(ServerPlayer p) {
		R_DOWN.put(p.getUUID(), p.level().getGameTime() - StandConfig.HOLD_THRESHOLD);
	}

	/** One Platinum Smash hit: 8, 8, 8 (each stuns for 0.5 s) and a 12 knockback finisher; 4 Stamina a hit. */
	public static void smash(ServerPlayer p) {
		StandState s = Stands.state(p);
		long now = p.level().getGameTime();
		if (s.smashStep > 0 && now - s.smashAt < 4 && now >= s.smashAt) {
			return; // the last punch is still landing
		}
		if (!begin(p, StandMove.PLATINUM_SMASH, StandConfig.SMASH_COST)) {
			return;
		}
		s = Stands.state(p);
		int step = s.smashStep > 0 && now - s.smashAt <= StandConfig.SMASH_RESET_TICKS ? Math.min(s.smashStep, StandConfig.SMASH_HITS - 1) : 0;
		boolean last = step == StandConfig.SMASH_HITS - 1;
		Vec3 dir = rushDir(p, s);
		for (LivingEntity e : inFront(p, dir, StandConfig.REACH, 0.5)) {
			if (last) {
				StandCombat.hit(p, e, StandConfig.SMASH_FINAL_DAMAGE, Vec3.ZERO);
				launch(e, dir.scale(StandConfig.SMASH_FINAL_KNOCKBACK).add(0, StandConfig.SMASH_FINAL_LIFT, 0));
			} else if (StandCombat.hit(p, e, StandConfig.SMASH_DAMAGE, Vec3.ZERO)) {
				StandCombat.stun(e, StandConfig.SMASH_STUN_TICKS);
				e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0); // stunned in place: no stagger back
				e.hurtMarked = true;
			}
		}
		StandState n = Stands.state(p).copy();
		n.smashStep = last ? 0 : step + 1;
		n.smashAt = now;
		anim(n, last ? StandState.ANIM_PUNCH_HEAVY : step % 2 == 0 ? StandState.ANIM_PUNCH_R : StandState.ANIM_PUNCH_L, now);
		if (last) {
			n.abilityReadyAt.put(StandMove.PLATINUM_SMASH, now + StandConfig.SMASH_COOLDOWN);
		}
		Stands.save(p, n);
		fx(p, last ? StandSounds.ORA_FINISHER : StandSounds.PUNCH, last ? 0.6f : 0.45f, 0.9f + p.getRandom().nextFloat() * 0.2f);
		Vec3 c = p.position().add(dir.scale(1.7)).add(0, 1.2, 0);
		Stands.vfx(level(p), last ? StandFxPayload.BURST : StandFxPayload.IMPACT, c, dir, last ? 1.0f : 0.6f);
	}

	private static void tickSmash(ServerPlayer p, StandState s, long now) {
		if (s.smashStep > 0 && now - s.smashAt > StandConfig.SMASH_RESET_TICKS) {
			// the combo was left unfinished for 2 s: it goes on cooldown all the same
			StandState n = s.copy();
			n.smashStep = 0;
			n.abilityReadyAt.put(StandMove.PLATINUM_SMASH, now + StandConfig.SMASH_COOLDOWN);
			Stands.save(p, n);
		}
	}

	public static void startBarrage(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.rushing || !begin(p, StandMove.ORA_BARRAGE, 0f)) {
			return;
		}
		if (!TimeStop.isCaster(p) && Stands.state(p).stamina < 1f) {
			Stands.say(p, "message.projecthero.stand.no_stamina", ChatFormatting.GRAY);
			return;
		}
		StandState n = Stands.state(p).copy();
		n.rushing = true;
		n.rushStart = p.level().getGameTime();
		n.guarding = false;
		Stands.save(p, n);
		BARRAGED.put(p.getUUID(), new HashMap<>());
		// v0.17.2 (user's clip: "play this during the barrage"): the ORA ORA ORA runs as long as the barrage, cut when it ends
		fx(p, StandSounds.ORA_BARRAGE, 0.6f, 1.0f);
	}

	/** Cuts the ORA ORA ORA short for the Stand users who hear it (the barrage ended before the clip did). */
	private static void stopBarrageSound(ServerPlayer p) {
		var packet = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(StandSounds.ORA_BARRAGE.getLocation(),
				net.minecraft.sounds.SoundSource.PLAYERS);
		double r2 = StandConfig.FX_RANGE * StandConfig.FX_RANGE;
		for (ServerPlayer viewer : level(p).players()) {
			if (Stands.isStandUser(viewer) && viewer.distanceToSqr(p) < r2) {
				viewer.connection.send(packet);
			}
		}
	}

	/** Letting go of R (or running dry): everyone held in the barrage is launched about 5 blocks. */
	public static void stopBarrage(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (!s.rushing) {
			return;
		}
		Map<Integer, Vec3> held = BARRAGED.remove(p.getUUID());
		Vec3 dir = rushDir(p, s);
		if (held != null) {
			for (int id : held.keySet()) {
				if (p.level().getEntity(id) instanceof LivingEntity e && e.isAlive()) {
					Vec3 away = StandCombat.away(p.position(), e, 1.0, 0.0);
					Vec3 flat = away.lengthSqr() < 1.0e-4 ? dir : away;
					Vec3 v = flat.scale(StandConfig.BARRAGE_LAUNCH_SPEED).add(0, StandConfig.BARRAGE_LAUNCH_LIFT, 0);
					if (TimeStop.shouldQueue(p, e)) {
						TimeStop.queue(p, e, 0f, v);
					} else {
						launch(e, v);
					}
				}
			}
		}
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		n.rushing = false;
		anim(n, StandState.ANIM_RUSH_FINISH, now);
		n.abilityReadyAt.put(StandMove.ORA_BARRAGE, now + StandConfig.BARRAGE_COOLDOWN);
		Stands.save(p, n);
		stopBarrageSound(p);
		fx(p, StandSounds.ORA, 0.7f, 1.0f); // the user's single ORA closes the barrage
		Vec3 c = p.position().add(dir.scale(1.8)).add(0, 1.2, 0);
		Stands.vfx(level(p), StandFxPayload.BURST, c, dir, 1.2f);
	}

	private static void tickBarrage(ServerPlayer p, StandState s, long now) {
		long t = now - s.rushStart;
		float st = TimeStop.isCaster(p) ? s.stamina : s.stamina - StandConfig.BARRAGE_STAMINA_PER_SECOND / 20f;
		StandState n = s.copy();
		n.stamina = Math.max(0f, st);
		Stands.save(p, n);
		if (st <= 0f || t >= StandConfig.BARRAGE_MAX_TICKS) {
			stopBarrage(p);
			return;
		}
		Vec3 dir = rushDir(p, s);
		Map<Integer, Vec3> held = BARRAGED.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
		if (t % StandConfig.BARRAGE_HIT_INTERVAL == 0) {
			for (LivingEntity e : inFront(p, dir, StandConfig.REACH, 0.5)) {
				if (StandCombat.hit(p, e, StandConfig.BARRAGE_HIT_DAMAGE, Vec3.ZERO)) {
					StandCombat.stun(e, StandConfig.BARRAGE_STUN_TICKS);
					held.putIfAbsent(e.getId(), e.position());
				}
			}
		}
		// stuck in place: everyone caught is held where the first fist found them
		held.entrySet().removeIf(en -> {
			Entity e = p.level().getEntity(en.getKey());
			if (!(e instanceof LivingEntity le) || !le.isAlive() || le.distanceToSqr(p) > 64.0) {
				return true;
			}
			if (!TimeStop.frozen(le) && !StandCombat.isBoss(le)) {
				Vec3 at = en.getValue();
				le.setPos(at.x, at.y, at.z);
				le.setDeltaMovement(Vec3.ZERO);
				le.fallDistance = 0f;
				le.hurtMarked = true;
			}
			return false;
		});
		// fists knock the arrows and fireballs aside
		Vec3 eye = p.getEyePosition();
		for (Projectile proj : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(StandConfig.REACH + 0.5))) {
			if (proj.getOwner() == p || proj instanceof BallBearingEntity) {
				continue;
			}
			Vec3 to = proj.position().subtract(eye);
			Vec3 flat = new Vec3(to.x, 0, to.z);
			if (flat.lengthSqr() > 1.0e-4 && flat.normalize().dot(dir) > 0.3) {
				proj.setDeltaMovement(proj.getDeltaMovement().scale(-0.8));
				proj.setOwner(p);
				proj.hurtMarked = true;
				Stands.particles(level(p), ParticleTypes.CRIT, proj.getX(), proj.getY(), proj.getZ(), 5, 0.1, 0.1, 0.1, 0.2);
			}
		}
		if (t % 4 == 0) {
			fx(p, StandSounds.PUNCH, 0.25f, 0.9f + p.getRandom().nextFloat() * 0.4f); // under the clip's voice
			Vec3 c = p.position().add(dir.scale(1.6)).add(0, 1.2, 0);
			var rnd = p.getRandom();
			Stands.vfx(level(p), StandFxPayload.IMPACT, c.add((rnd.nextDouble() - 0.5) * 0.7, (rnd.nextDouble() - 0.5) * 0.6, (rnd.nextDouble() - 0.5) * 0.7), dir,
					0.55f);
		}
	}

	// ---------------------------------------------------------------- Shift+R: Star Finger

	/** The fingers stretch 30 blocks out over 1 s, running through every creature on the way (22 each). */
	public static void starFinger(ServerPlayer p) {
		if (FINGERS.containsKey(p.getUUID()) || !begin(p, StandMove.STAR_FINGER, StandConfig.FINGER_COST)) {
			return;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Vec3 end = eye.add(look.scale(StandConfig.FINGER_RANGE));
		BlockHitResult bh = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			end = bh.getLocation();
		}
		long now = p.level().getGameTime();
		FINGERS.put(p.getUUID(), new Finger(eye, look, end.distanceTo(eye), now, new HashSet<>()));
		StandState st = Stands.state(p).copy();
		anim(st, StandState.ANIM_FINGER, now);
		st.abilityReadyAt.put(StandMove.STAR_FINGER, now + StandConfig.FINGER_COOLDOWN);
		// v0.17.2 (user: "make star finger actually make a finger model stretch out and poke something"): the client stretches
		// the finger mesh (StandFinger) from Star Platinum's hand toward this point, over the same second the server does
		st.fingerEnd = java.util.List.of(end.x, end.y, end.z);
		Stands.save(p, st);
		fx(p, StandSounds.STAR_FINGER, 0.7f, 1.0f);
	}

	/** How far out the fingertip is {@code age} ticks after Star Finger (blocks, before it is cut short by a wall). */
	public static double fingerReach(float age) {
		return StandConfig.FINGER_RANGE * Mth.clamp(age / StandConfig.FINGER_EXTEND_TICKS, 0f, 1f);
	}

	private static void tickFinger(ServerPlayer p, long now) {
		Finger f = FINGERS.get(p.getUUID());
		if (f == null) {
			return;
		}
		long age = now - f.start;
		if (age > StandConfig.FINGER_EXTEND_TICKS + StandConfig.FINGER_HOLD_TICKS + StandConfig.FINGER_RETRACT_TICKS || age < 0) {
			FINGERS.remove(p.getUUID());
			return;
		}
		double reach = Math.min(f.length, fingerReach(age + 1));
		Vec3 tip = f.origin.add(f.dir.scale(reach));
		AABB sweep = new AABB(f.origin, tip).inflate(1.0);
		for (Entity e : p.level().getEntities(p, sweep)) {
			if (f.hit.contains(e.getId()) || !StandCombat.isTarget(p, e)) {
				continue;
			}
			AABB bb = e.getBoundingBox().inflate(0.4);
			if (bb.clip(f.origin, tip).isPresent() || bb.contains(f.origin)) {
				f.hit.add(e.getId());
				if (StandCombat.hit(p, (LivingEntity) e, StandConfig.FINGER_DAMAGE, f.dir.scale(0.3))) {
					Vec3 c = e.getBoundingBox().getCenter();
					Stands.vfx(level(p), StandFxPayload.IMPACT, c, f.dir, 0.7f);
				}
			}
		}
	}

	// ---------------------------------------------------------------- G: Ora Chase

	/** G: Star Platinum flies off along your look (3 b/s, steered by the mouse) to grab the first creature it reaches. */
	public static void oraChase(ServerPlayer p) {
		if (!begin(p, StandMove.ORA_CHASE, StandConfig.CHASE_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		Vec3 start = p.position().add(flatLook(p).scale(0.8)).add(0, 1.2, 0);
		StandState n = Stands.state(p).copy();
		n.chaseStart = now;
		n.chaseGrabAt = 0L;
		n.chaseTarget = -1;
		n.chasePos = List.of(start.x, start.y, start.z);
		n.rushing = false;
		n.guarding = false;
		n.chargeStart = 0L;
		anim(n, StandState.ANIM_NONE, now);
		n.abilityReadyAt.put(StandMove.ORA_CHASE, now + StandConfig.CHASE_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.DASH, 0.5f, 1.1f);
	}

	private static Vec3 chasePos(StandState s) {
		return new Vec3(s.chasePos.get(0), s.chasePos.get(1), s.chasePos.get(2));
	}

	/** Where a creature Ora Chase holds by the neck hangs: its feet, so its neck is in Star Platinum's raised hand. */
	public static Vec3 chaseHoldPoint(Player p, Vec3 at, Entity victim) {
		Vec3 facing = chaseFacing(p, at);
		return at.add(facing.scale(0.25)).add(0, StandConfig.CHASE_LIFT - victim.getEyeHeight() + 0.1, 0);
	}

	private static void tickChase(ServerPlayer p, StandState s, long now) {
		Vec3 at = chasePos(s);
		if (s.chaseGrabAt == 0L) {
			// flying: one step along wherever the user looks right now (the mouse steers it)
			Vec3 next = at.add(p.getLookAngle().scale(StandConfig.CHASE_SPEED));
			if (!p.level().noCollision(new AABB(next, next).inflate(0.3))) {
				endChase(p, false);
				return;
			}
			LivingEntity caught = null;
			double best = Double.MAX_VALUE;
			for (Entity e : p.level().getEntities(p, new AABB(next, next).inflate(StandConfig.CHASE_GRAB_RADIUS + 1.0))) {
				if (!StandCombat.isTarget(p, e) || TimeStop.frozen(e) && !TimeStop.unbound(p)) {
					continue;
				}
				AABB bb = e.getBoundingBox().inflate(StandConfig.CHASE_GRAB_RADIUS);
				if (bb.contains(next) || bb.clip(at, next).isPresent()) {
					double d = e.distanceToSqr(next);
					if (d < best) {
						best = d;
						caught = (LivingEntity) e;
					}
				}
			}
			StandState n = s.copy();
			n.chasePos = List.of(next.x, next.y, next.z);
			if (caught != null) {
				n.chaseGrabAt = now;
				n.chaseTarget = caught.getId();
				Stands.save(p, n);
				fx(p, StandSounds.GRAB, 0.6f, 1.0f);
				Stands.vfx(level(p), StandFxPayload.IMPACT, caught.getEyePosition(), next.subtract(at), 0.6f);
				return;
			}
			Stands.save(p, n);
			if ((now - s.chaseStart) * StandConfig.CHASE_SPEED >= StandConfig.CHASE_RANGE || next.distanceToSqr(p.position()) > 24 * 24) {
				endChase(p, false); // nothing within 10 blocks: it comes back
			}
			return;
		}
		Entity e = p.level().getEntity(s.chaseTarget);
		if (!(e instanceof LivingEntity victim) || !victim.isAlive()) {
			endChase(p, false);
			return;
		}
		long t = now - s.chaseGrabAt;
		if (!StandCombat.isBoss(victim)) {
			Vec3 hold = chaseHoldPoint(p, at, victim);
			victim.setPos(hold.x, hold.y, hold.z);
			victim.setDeltaMovement(Vec3.ZERO);
			victim.fallDistance = 0f;
			victim.hurtMarked = true;
		}
		int every = StandConfig.CHASE_BARRAGE_TICKS / StandConfig.CHASE_BARRAGE_HITS;
		if (t > 0 && t % every == 0 && t <= StandConfig.CHASE_BARRAGE_TICKS) {
			// one-handed: 15 over the 3 s
			StandCombat.hit(p, victim, StandConfig.CHASE_BARRAGE_DAMAGE / StandConfig.CHASE_BARRAGE_HITS, Vec3.ZERO);
			StandCombat.stun(victim, every + 2);
			fx(p, StandSounds.PUNCH, 0.3f, 1.0f + p.getRandom().nextFloat() * 0.3f);
		}
		if (t >= StandConfig.CHASE_BARRAGE_TICKS) {
			// thrown 10 blocks, away from its user; the landing blow is the last 10
			Vec3 facing = chaseFacing(p, at);
			Vec3 v = facing.scale(StandConfig.CHASE_THROW_SPEED).add(0, StandConfig.CHASE_THROW_LIFT, 0);
			StandCombat.hit(p, victim, StandConfig.CHASE_THROW_DAMAGE, Vec3.ZERO);
			if (TimeStop.shouldQueue(p, victim)) {
				TimeStop.queue(p, victim, 0f, v);
			} else {
				launch(victim, v);
			}
			fx(p, StandSounds.THROW, 0.6f, 1.0f);
			Stands.vfx(level(p), StandFxPayload.BURST, victim.getBoundingBox().getCenter(), facing, 1.0f);
			endChase(p, true);
		}
	}

	/** The chase is over (thrown, missed, blocked): Star Platinum flies home. */
	private static void endChase(ServerPlayer p, boolean threw) {
		StandState s = Stands.state(p);
		if (s.chaseStart == 0L) {
			return;
		}
		long now = p.level().getGameTime();
		StandState n = s.copy();
		n.chaseStart = 0L;
		n.chaseGrabAt = 0L;
		n.chaseTarget = -1;
		// chasePos stays: the client flies it home from there (and plays the throw there)
		anim(n, threw ? StandState.ANIM_CHASE_THROW : StandState.ANIM_NONE, now);
		Stands.save(p, n);
	}

	// ---------------------------------------------------------------- Shift+G: Ball Bearing Flick

	/** One steel ball flicked out of Star Platinum's hand: a fast projectile, 20 on whatever it hits. */
	public static void flick(ServerPlayer p) {
		if (!begin(p, StandMove.BEARING_FLICK, StandConfig.FLICK_COST)) {
			return;
		}
		ServerLevel level = level(p);
		Vec3 look = p.getLookAngle();
		BallBearingEntity ball = new BallBearingEntity(StandEntities.BALL_BEARING, level);
		ball.setOwner(p);
		Vec3 from = p.getEyePosition().add(look.scale(0.9)).add(StarPlatinum.rightOf(flatLook(p)).scale(0.25)).subtract(0, 0.2, 0);
		ball.moveTo(from.x, from.y, from.z, p.getYRot(), p.getXRot());
		ball.setDeltaMovement(look.scale(StandConfig.BEARING_SPEED));
		level.addFreshEntity(ball);
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		anim(n, StandState.ANIM_FLICK, now);
		n.abilityReadyAt.put(StandMove.BEARING_FLICK, now + StandConfig.FLICK_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.FLICK, 0.6f, 1.0f);
	}

	// ---------------------------------------------------------------- X: Stand Jump / Shift+X: Ora Rush

	/** X: Star Platinum throws you about 15 blocks the way you look. No fall damage from it. */
	public static void standJump(ServerPlayer p) {
		if (!begin(p, StandMove.STAND_JUMP, StandConfig.JUMP_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		Vec3 v = p.getLookAngle().scale(StandConfig.JUMP_SPEED);
		p.setDeltaMovement(v.x, Math.max(v.y, StandConfig.JUMP_MIN_UP), v.z);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		StandState n = Stands.state(p).copy();
		n.leapRebounds = 0;
		anim(n, StandState.ANIM_LEAP, now);
		n.abilityReadyAt.put(StandMove.STAND_JUMP, now + StandConfig.JUMP_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.LEAP, 0.6f, 1.0f);
		Stands.particles(level(p), ParticleTypes.CLOUD, p.getX(), p.getY() + 0.1, p.getZ(), 10, 0.4, 0.1, 0.4, 0.03);
	}

	/** Shift+X: an 8-block dash behind Star Platinum's fists, ORA hits on everything along the way. */
	public static void oraRush(ServerPlayer p) {
		if (Stands.state(p).dashUntil > p.level().getGameTime() || !begin(p, StandMove.ORA_RUSH, StandConfig.ORA_RUSH_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		Vec3 dir = flatLook(p);
		RUSH_DIR.put(p.getUUID(), dir);
		StandState n = Stands.state(p).copy();
		n.dashUntil = now + StandConfig.ORA_RUSH_TICKS;
		n.leapRebounds = 0;
		n.guarding = false;
		anim(n, StandState.ANIM_ORA_RUSH, now);
		n.abilityReadyAt.put(StandMove.ORA_RUSH, now + StandConfig.ORA_RUSH_COOLDOWN);
		Stands.save(p, n);
		p.setDeltaMovement(dir.scale(StandConfig.ORA_RUSH_SPEED).add(0, 0.1, 0));
		p.hurtMarked = true;
		fx(p, StandSounds.ORA_RUSH, 0.45f, 1.05f);
	}

	private static void tickOraRush(ServerPlayer p, StandState s, long now) {
		Vec3 dir = RUSH_DIR.get(p.getUUID());
		if (dir == null || now >= s.dashUntil) {
			RUSH_DIR.remove(p.getUUID());
			StandState n = s.copy();
			n.dashUntil = 0L;
			Stands.save(p, n);
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.3, 1.0, 0.3));
			p.hurtMarked = true;
			p.fallDistance = 0f;
			return;
		}
		long t = StandConfig.ORA_RUSH_TICKS - (s.dashUntil - now);
		double vy = Math.max(-0.08, Math.min(0.1, p.getDeltaMovement().y));
		p.setDeltaMovement(dir.x * StandConfig.ORA_RUSH_SPEED, vy, dir.z * StandConfig.ORA_RUSH_SPEED);
		p.hurtMarked = true;
		p.fallDistance = 0f;
		if (t % StandConfig.ORA_RUSH_HIT_INTERVAL == 0) {
			boolean any = false;
			for (LivingEntity e : inFront(p, dir, StandConfig.ORA_RUSH_REACH, 0.2)) {
				any |= StandCombat.hit(p, e, StandConfig.ORA_RUSH_HIT_DAMAGE, dir.scale(0.55).add(0, 0.12, 0));
			}
			fx(p, StandSounds.PUNCH, any ? 0.35f : 0.2f, 0.95f + p.getRandom().nextFloat() * 0.3f);
			var rnd = p.getRandom();
			Vec3 c = p.position().add(dir.scale(1.9)).add((rnd.nextDouble() - 0.5) * 0.6, 1.1 + (rnd.nextDouble() - 0.5) * 0.5, (rnd.nextDouble() - 0.5) * 0.6);
			Stands.vfx(level(p), StandFxPayload.IMPACT, c, dir, 0.55f);
		}
	}

	// ---------------------------------------------------------------- Z: Star Breaker (hold)

	public static void startBreaker(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.chargeStart != 0L) {
			return;
		}
		if (!begin(p, StandMove.STAR_BREAKER, 0f)) {
			return;
		}
		if (!TimeStop.isCaster(p) && Stands.state(p).stamina + 1.0e-3f < StandConfig.BREAKER_COST) {
			Stands.say(p, "message.projecthero.stand.no_stamina", ChatFormatting.GRAY);
			return;
		}
		StandState n = Stands.state(p).copy();
		n.chargeStart = p.level().getGameTime();
		Stands.save(p, n);
		fx(p, StandSounds.BREAKER_CHARGE, 0.6f, 1.0f);
	}

	public static void releaseBreaker(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.chargeStart == 0L) {
			return;
		}
		long now = p.level().getGameTime();
		float charge = Mth.clamp((now - s.chargeStart) / (float) StandConfig.BREAKER_FULL_CHARGE, 0f, 1f);
		StandState n0 = s.copy();
		n0.chargeStart = 0L;
		Stands.save(p, n0);
		if (!Stands.spendStamina(p, StandConfig.BREAKER_COST)) {
			return;
		}
		ServerLevel level = level(p);
		Vec3 look = p.getLookAngle();
		Vec3 flat = flatLook(p);
		LivingEntity target = aimed(p, StandConfig.REACH);
		if (target != null) {
			if (target instanceof Player pl && pl.isBlocking()) {
				pl.disableShield(); // it breaks shields
			}
			StandCombat.hit(p, target, StandConfig.BREAKER_DAMAGE,
					flat.scale(1.0 + StandConfig.BREAKER_KNOCKBACK * charge * 0.66).add(0, StandConfig.BREAKER_LIFT * charge + 0.2, 0));
		}
		if (charge >= 0.6f && p.mayBuild() && !TimeStop.isCaster(p)) {
			Vec3 eye = p.getEyePosition();
			Set<BlockPos> done = new HashSet<>();
			for (double d = 1.0; d <= 2.6; d += 0.4) {
				BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
				for (BlockPos q : new BlockPos[] { pos, pos.below() }) {
					if (!done.add(q)) {
						continue;
					}
					BlockState st = level.getBlockState(q);
					float h = st.getDestroySpeed(level, q);
					if (!st.isAir() && !st.hasBlockEntity() && h >= 0f && h <= StandConfig.BREAKER_SMASH_HARDNESS && p.mayInteract(level, q)) {
						level.destroyBlock(q, true, p);
					}
				}
			}
		}
		StandState n = Stands.state(p).copy();
		anim(n, StandState.ANIM_BREAKER, now);
		n.abilityReadyAt.put(StandMove.STAR_BREAKER, now + StandConfig.BREAKER_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.STAR_BREAKER, 0.8f, 1.0f - 0.2f * charge);
		Vec3 c = p.getEyePosition().add(look.scale(2.0));
		Stands.vfx(level, StandFxPayload.BURST, c, look, 1.2f + 0.8f * charge);
	}

	// ---------------------------------------------------------------- Shift+Z: The World

	/** Shift+Z: stops time (or, while your stop runs, starts it again). */
	public static void theWorld(ServerPlayer p) {
		if (TimeStop.isCaster(p)) {
			TimeStop.endEarly(p);
			return;
		}
		if (!Stands.summoned(p)) {
			Stands.summon(p);
		}
		Stands.breakPose(p);
		TimeStop.begin(p);
	}

	// ---------------------------------------------------------------- V: Stand Guard (hold) / Shift+V: Stand Grab

	public static void startGuard(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.guarding || s.rushing || !begin(p, StandMove.GUARD, 0f)) {
			return;
		}
		StandState n = Stands.state(p).copy();
		n.guarding = true;
		Stands.save(p, n);
		fx(p, StandSounds.GUARD, 0.5f, 1.0f);
	}

	/** Letting go of V: the guard drops and goes on its 6 s cooldown. */
	public static void stopGuard(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (!s.guarding) {
			return;
		}
		StandState n = s.copy();
		n.guarding = false;
		n.abilityReadyAt.put(StandMove.GUARD, p.level().getGameTime() + StandConfig.GUARD_COOLDOWN);
		Stands.save(p, n);
	}

	/** Guarding and the hit comes from in front (within 75 degrees either side). */
	public static boolean guardsAgainst(ServerPlayer p, net.minecraft.world.damagesource.DamageSource source) {
		StandState s = Stands.state(p);
		if (!s.guarding || source.getSourcePosition() == null) {
			return false;
		}
		Vec3 to = source.getSourcePosition().subtract(p.position());
		to = new Vec3(to.x, 0, to.z);
		return to.lengthSqr() > 1.0e-4 && to.normalize().dot(flatLook(p)) > 0.25;
	}

	/** Star Platinum plucks incoming projectiles out of the air in front while it guards. */
	private static void catchProjectiles(ServerPlayer p, Vec3 dir, double range, double minDot) {
		for (Projectile proj : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(range))) {
			if (proj.getOwner() == p || proj.getDeltaMovement().lengthSqr() < 0.04 || TimeStop.frozen(proj)) {
				continue;
			}
			Vec3 to = proj.position().subtract(p.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			if (minDot > -1.0 && (flat.lengthSqr() < 1.0e-4 || flat.normalize().dot(dir) < minDot)) {
				continue;
			}
			Stands.vfx(level(p), StandFxPayload.IMPACT, proj.position(), proj.position().subtract(p.position()), 0.5f);
			fx(p, StandSounds.CATCH, 0.5f, 1.2f);
			proj.discard();
			StandState n = Stands.state(p).copy();
			anim(n, StandState.ANIM_CATCH, p.level().getGameTime());
			Stands.save(p, n);
		}
	}

	/** Shift+V: grab the creature in front (no time limit). Shift+V again puts it down gently. */
	public static void grabKey(ServerPlayer p) {
		if (Stands.state(p).grabbedEntity != -1) {
			putDown(p);
		} else {
			grab(p);
		}
	}

	private static void grab(ServerPlayer p) {
		if (!begin(p, StandMove.STAND_GRAB, 0f)) {
			return;
		}
		long now = p.level().getGameTime();
		LivingEntity aimed = aimed(p, StandConfig.REACH);
		StandState n = Stands.state(p).copy();
		if (aimed == null || StandCombat.isBoss(aimed) || aimed instanceof Player) {
			n.abilityReadyAt.put(StandMove.STAND_GRAB, now + 10);
			Stands.save(p, n);
			Stands.say(p, "message.projecthero.stand.grab_nothing", ChatFormatting.GRAY);
			return;
		}
		n.grabbedEntity = aimed.getId();
		n.grabCombo = 0;
		n.guarding = false;
		anim(n, StandState.ANIM_GRAB, now);
		Stands.save(p, n);
		fx(p, StandSounds.GRAB, 0.6f, 1.0f);
	}

	/** Where a held creature hangs: in Star Platinum's hands, in front at chest height. */
	public static Vec3 holdPoint(Player p) {
		return p.getEyePosition().add(p.getLookAngle().scale(2.0)).subtract(0, 0.3, 0);
	}

	private static void tickHold(ServerPlayer p, StandState s) {
		Entity e = p.level().getEntity(s.grabbedEntity);
		if (e == null || !e.isAlive() || e.distanceToSqr(p) > 64.0) {
			StandState n = Stands.state(p).copy();
			n.grabbedEntity = -1;
			n.abilityReadyAt.put(StandMove.STAND_GRAB, p.level().getGameTime() + StandConfig.GRAB_COOLDOWN);
			Stands.save(p, n);
			return;
		}
		if (TimeStop.frozen(e)) {
			return;
		}
		Vec3 at = holdPoint(p).subtract(0, e.getBbHeight() * 0.5, 0);
		e.setPos(at.x, at.y, at.z);
		e.setDeltaMovement(Vec3.ZERO);
		e.fallDistance = 0f;
		e.hurtMarked = true;
	}

	/** Lets go of whatever it holds, where it is (death, the Stand put away). */
	private static void release(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (s.grabbedEntity != -1 || s.grabbedBlock != 0) {
			StandState n = s.copy();
			n.grabbedEntity = -1;
			n.grabbedBlock = 0;
			Stands.save(p, n);
		}
	}

	/** Shift+V while holding: set it down on its feet in front of you, unhurt. */
	public static void putDown(ServerPlayer p) {
		StandState s = Stands.state(p);
		if (!(p.level().getEntity(s.grabbedEntity) instanceof LivingEntity e)) {
			release(p);
			return;
		}
		Vec3 at = p.position().add(flatLook(p).scale(1.8));
		BlockPos floor = BlockPos.containing(at);
		for (int i = 0; i < 4 && p.level().getBlockState(floor).isAir(); i++) {
			floor = floor.below();
		}
		double y = p.level().getBlockState(floor).isAir() ? p.getY() : floor.getY() + 1.0;
		if (!p.level().noCollision(e, e.getBoundingBox().move(at.x - e.getX(), y - e.getY(), at.z - e.getZ()))) {
			at = p.position();
			y = p.getY();
		}
		e.setPos(at.x, y, at.z);
		e.setDeltaMovement(Vec3.ZERO);
		e.fallDistance = 0f;
		e.hurtMarked = true;
		long now = p.level().getGameTime();
		StandState n = s.copy();
		n.grabbedEntity = -1;
		n.grabCombo = 0;
		anim(n, StandState.ANIM_PUT_DOWN, now);
		n.abilityReadyAt.put(StandMove.STAND_GRAB, now + StandConfig.GRAB_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.GRAB, 0.4f, 0.8f);
	}

	/** V while holding: hurl it the way you look. */
	public static void hurl(ServerPlayer p) {
		StandState s = Stands.state(p);
		Entity thrown = p.level().getEntity(s.grabbedEntity);
		long now = p.level().getGameTime();
		if (thrown instanceof LivingEntity le) {
			Vec3 v = p.getLookAngle().scale(StandConfig.THROW_SPEED).add(0, 0.15, 0);
			if (TimeStop.shouldQueue(p, le)) {
				TimeStop.queue(p, le, 0f, v);
			} else {
				le.setDeltaMovement(v);
				le.hurtMarked = true;
			}
			float damage = Mth.clamp(le.getBbWidth() * le.getBbHeight() * 6f, 4f, 14f);
			THROWN.put(le.getUUID(), new Thrown(p.getUUID(), now + StandConfig.THROWN_TRACK_TICKS, damage));
		}
		StandState n = Stands.state(p).copy();
		n.grabbedEntity = -1;
		n.grabbedBlock = 0;
		n.grabCombo = 0;
		anim(n, StandState.ANIM_THROW, now);
		n.abilityReadyAt.put(StandMove.STAND_GRAB, now + StandConfig.GRAB_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.THROW, 0.6f, 1.0f);
	}

	/** R while holding: one punch of the 5-hit combo (6 each, 30 in all); the fifth knocks it out of the hand. */
	public static void grabCombo(ServerPlayer p) {
		StandState s = Stands.state(p);
		long now = p.level().getGameTime();
		if (!(p.level().getEntity(s.grabbedEntity) instanceof LivingEntity e) || now - s.grabComboAt < StandConfig.GRAB_COMBO_GAP && now >= s.grabComboAt) {
			return;
		}
		int hit = s.grabCombo + 1;
		boolean last = hit >= StandConfig.GRAB_COMBO_HITS;
		StandCombat.hit(p, e, StandConfig.GRAB_COMBO_DAMAGE / StandConfig.GRAB_COMBO_HITS, Vec3.ZERO);
		StandState n = Stands.state(p).copy();
		n.grabComboAt = now;
		if (last) {
			Vec3 v = flatLook(p).scale(StandConfig.GRAB_LAST_KNOCKBACK).add(0, 0.35, 0);
			if (TimeStop.shouldQueue(p, e)) {
				TimeStop.queue(p, e, 0f, v);
			} else {
				launch(e, v);
			}
			n.grabbedEntity = -1;
			n.grabCombo = 0;
			anim(n, StandState.ANIM_PUNCH_HEAVY, now);
			n.abilityReadyAt.put(StandMove.STAND_GRAB, now + StandConfig.GRAB_COOLDOWN);
		} else {
			n.grabCombo = hit;
			anim(n, StandState.ANIM_GRAB_PUNCH, now);
		}
		Stands.save(p, n);
		fx(p, last ? StandSounds.ORA_FINISHER : StandSounds.PUNCH, last ? 0.6f : 0.45f, 0.9f + p.getRandom().nextFloat() * 0.2f);
	}

	/** Server tick: thrown creatures hit the first creatures they fly into (and slam into walls). */
	public static void tickThrown(MinecraftServer server) {
		if (THROWN.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Thrown>> it = THROWN.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Thrown> en = it.next();
			Thrown th = en.getValue();
			ServerPlayer owner = server.getPlayerList().getPlayer(th.owner);
			if (owner == null) {
				it.remove();
				continue;
			}
			Entity e = ((ServerLevel) owner.level()).getEntity(en.getKey());
			if (e == null || !e.isAlive() || owner.level().getGameTime() > th.until) {
				it.remove();
				continue;
			}
			if (TimeStop.frozen(e)) {
				continue;
			}
			boolean done = false;
			for (Entity o : owner.level().getEntities(e, e.getBoundingBox().inflate(0.4))) {
				if (o != owner && o instanceof LivingEntity victim && StandCombat.isTarget(owner, victim)) {
					StandCombat.hit(owner, victim, th.damage, e.getDeltaMovement().scale(0.6).add(0, 0.2, 0));
					done = true;
				}
			}
			if (!done && e instanceof LivingEntity le && (e.horizontalCollision || e.verticalCollisionBelow && e.getDeltaMovement().y < -0.5)
					&& e.tickCount > 2) {
				StandCombat.hit(owner, le, th.damage * 0.75f, Vec3.ZERO);
				done = true;
			} else if (done && e instanceof LivingEntity le) {
				StandCombat.hit(owner, le, th.damage * 0.5f, Vec3.ZERO);
			}
			if (done) {
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- C: Super Inhale / Shift+C: ORAAA Uppercut

	public static void inhale(ServerPlayer p) {
		if (!begin(p, StandMove.INHALE, StandConfig.INHALE_COST)) {
			return;
		}
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		n.inhaleUntil = now + StandConfig.INHALE_TICKS;
		anim(n, StandState.ANIM_INHALE, now);
		n.abilityReadyAt.put(StandMove.INHALE, now + StandConfig.INHALE_COOLDOWN);
		Stands.save(p, n);
		INHALED.put(p.getUUID(), new HashSet<>());
		fx(p, StandSounds.INHALE, 0.6f, 1.0f);
		// it clears lingering potion clouds
		for (AreaEffectCloud cloud : p.level().getEntitiesOfClass(AreaEffectCloud.class, p.getBoundingBox().inflate(StandConfig.INHALE_RANGE))) {
			cloud.discard();
		}
	}

	private static void tickInhale(ServerPlayer p, StandState s, long now) {
		Vec3 mouth = restPoint(p).add(0, 1.6, 0);
		Set<Integer> pulled = INHALED.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
		for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(StandConfig.INHALE_RANGE))) {
			Vec3 to = mouth.subtract(e.position());
			double d = to.length();
			if (d < 0.8 || d > StandConfig.INHALE_RANGE) {
				continue;
			}
			Vec3 pull = to.normalize().scale(StandConfig.INHALE_PULL * Math.min(1.0, 3.0 / Math.max(1.0, d) + 0.4));
			if (e instanceof ItemEntity || e instanceof ExperienceOrb) {
				e.setDeltaMovement(pull.scale(1.5));
				e.hurtMarked = true;
			} else if (e instanceof LivingEntity le && StandCombat.isTarget(p, le) && !StandCombat.isBoss(le)) {
				Vec3 v = le.getDeltaMovement().add(pull);
				if (v.length() > 0.8) {
					v = v.normalize().scale(0.8);
				}
				le.setDeltaMovement(v);
				le.hurtMarked = true;
				pulled.add(le.getId());
			} else if (e instanceof AreaEffectCloud cloud) {
				cloud.discard();
			}
		}
		if (now % 2 == 0) {
			var r = p.getRandom();
			for (int i = 0; i < 3; i++) {
				Vec3 from = mouth.add((r.nextDouble() - 0.5) * 8, (r.nextDouble() - 0.3) * 3, (r.nextDouble() - 0.5) * 8);
				Vec3 v = mouth.subtract(from).scale(0.12);
				Stands.particles(level(p), ParticleTypes.CLOUD, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
			}
		}
		if (now + 1 >= s.inhaleUntil) {
			// pulled in and left winded
			for (int id : pulled) {
				if (p.level().getEntity(id) instanceof LivingEntity le && le.isAlive()) {
					le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
				}
			}
			INHALED.remove(p.getUUID());
			StandState n = Stands.state(p).copy();
			n.inhaleUntil = 0L;
			Stands.save(p, n);
		}
	}

	public static void uppercut(ServerPlayer p) {
		if (!begin(p, StandMove.UPPERCUT, StandConfig.UPPERCUT_COST)) {
			return;
		}
		LivingEntity target = aimed(p, StandConfig.REACH);
		if (target != null) {
			StandCombat.hit(p, target, StandConfig.UPPERCUT_DAMAGE, flatLook(p).scale(0.2).add(0, StandConfig.UPPERCUT_LIFT, 0));
		}
		long now = p.level().getGameTime();
		StandState n = Stands.state(p).copy();
		anim(n, StandState.ANIM_UPPERCUT, now);
		n.abilityReadyAt.put(StandMove.UPPERCUT, now + StandConfig.UPPERCUT_COOLDOWN);
		Stands.save(p, n);
		fx(p, StandSounds.UPPERCUT, 0.6f, 1.0f);
		Vec3 c = p.position().add(forward(p).scale(1.4)).add(0, 1.0, 0);
		Stands.vfx(level(p), StandFxPayload.RISE, c, new Vec3(0, 1, 0), 1.0f);
	}

	// ---------------------------------------------------------------- tick / clear

	public static void tick(ServerPlayer p) {
		StandState s = Stands.state(p);
		long now = p.level().getGameTime();
		if (!s.summoned) {
			if (s.rushing || s.guarding || s.chargeStart != 0L || s.grabbedEntity != -1 || s.grabbedBlock != 0 || s.inhaleUntil != 0L
					|| s.chaseStart != 0L || s.dashUntil != 0L) {
				clear(p);
			}
			if (s.leapRebounds >= 0) {
				tickLeap(p, Stands.state(p));
			}
			return;
		}
		Long down = R_DOWN.get(p.getUUID());
		if (down != null && now - down >= StandConfig.HOLD_THRESHOLD && !s.rushing) {
			R_DOWN.remove(p.getUUID());
			startBarrage(p);
			s = Stands.state(p);
		}
		tickSmash(p, s, now);
		s = Stands.state(p);
		if (s.rushing) {
			tickBarrage(p, s, now);
			s = Stands.state(p);
		}
		tickFinger(p, now);
		if (s.chaseStart != 0L) {
			if (s.chasePos.size() == 3) {
				tickChase(p, s, now);
			} else {
				endChase(p, false);
			}
			s = Stands.state(p);
		}
		if (s.chargeStart != 0L && now - s.chargeStart >= StandConfig.BREAKER_AUTO_RELEASE) {
			releaseBreaker(p);
			s = Stands.state(p);
		}
		if (s.guarding) {
			catchProjectiles(p, flatLook(p), StandConfig.REACH, 0.2);
			s = Stands.state(p);
		}
		if (s.grabbedEntity != -1) {
			tickHold(p, s);
			s = Stands.state(p);
		}
		if (s.dashUntil != 0L) {
			tickOraRush(p, s, now);
			s = Stands.state(p);
		}
		if (s.leapRebounds >= 0) {
			tickLeap(p, s);
			s = Stands.state(p);
		}
		if (s.inhaleUntil != 0L) {
			tickInhale(p, s, now);
		}
	}

	private static void tickLeap(ServerPlayer p, StandState s) {
		p.fallDistance = 0f;
		if (p.onGround() && p.level().getGameTime() - s.animStart > 5 && s.dashUntil == 0L) {
			StandState n = s.copy();
			n.leapRebounds = -1;
			Stands.save(p, n);
		}
	}

	/** Everything running stops (no finisher); a held creature is let go where it is, a chase victim dropped. */
	public static void clear(ServerPlayer p) {
		StandState s = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.STAND_STATE, null);
		if (s == null || !s.isUser()) {
			return;
		}
		release(p);
		s = Stands.state(p);
		if (s.rushing) {
			stopBarrageSound(p);
		}
		forget(p.getUUID());
		if (s.rushing || s.guarding || s.chargeStart != 0L || s.inhaleUntil != 0L || s.chaseStart != 0L || s.dashUntil != 0L) {
			StandState n = s.copy();
			n.rushing = false;
			n.guarding = false;
			n.chargeStart = 0L;
			n.inhaleUntil = 0L;
			n.chaseStart = 0L;
			n.chaseGrabAt = 0L;
			n.chaseTarget = -1;
			n.chasePos = List.of();
			n.dashUntil = 0L;
			Stands.save(p, n);
		}
	}
}
