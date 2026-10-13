package com.projecthero.mod.galactus.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.galactus.GalactusAnims;
import com.projecthero.mod.galactus.GalactusAnims.Clip;
import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: <b>Galactus</b>, Devourer of Worlds -- a ~60-block colossus ({@link GalactusConfig.Colossus#renderHeight}, render
 * scale only: his body hit-box is 10 x 40) standing on the arena's edge. He never walks; he turns to face his target.
 *
 * <h2>He cannot be killed</h2>
 * His health never drops below 1 ({@link #setHealth}); he holds there until a fighter uses the Ultimate Nullifier on him.
 * His <b>body</b> takes only {@link GalactusConfig.Colossus#bodyDamageMultiplier} of every hit. His four <b>weak points</b>
 * ({@link GalactusWeakPointEntity}: chest emblem, helmet crest, left / right gauntlet) are separate part entities he moves
 * onto himself every tick; their damage comes off him in full and breaking one takes a chunk more. They are sealed while
 * any Power Pylon stands.
 *
 * <h2>Moves</h2> (one at a time; targets from the shared {@link BossThreat} table, else the nearest fighter)
 * <ul>
 *   <li><b>Power Cosmic Eye Beams</b> -- a line across the ground glows for 2 s, then the beam sweeps along it.</li>
 *   <li><b>Hand Grab</b> -- a giant hand ({@link GalactusHandEntity}) comes down over a 1.5 s shadow; caught, the victim is
 *       lifted to his face and flung. Allies break the grip by hitting the hand.</li>
 *   <li><b>Matter Transmutation</b> -- the ground under up to three fighters is marked, then turns to cosmic hazard
 *       (tracked + reverted by the event).</li>
 *   <li><b>Disintegration Wave</b> -- a ring races out from his feet; jump or fly over it.</li>
 *   <li><b>Punisher Summons</b> -- two Punishers drop from the Worldship (capped).</li>
 * </ul>
 */
public class GalactusEntity extends GalactusMob {
	public enum Move { NONE, EYE_BEAM, GRAB, TRANSMUTE, WAVE, SUMMON, RETREAT }

	private final BossThreat threat = new BossThreat(this).holdWhile(this::busy);
	private final UUID[] weakIds = new UUID[GalactusWeakPointEntity.KINDS];
	private UUID handId;
	private UUID grabbedId;
	private Move move = Move.NONE;
	private int moveTick;
	private int grabStage;
	private int grabStageTick;
	private Vec3 grabShadow = Vec3.ZERO;
	private Vec3 beamFrom = Vec3.ZERO;
	private Vec3 beamTo = Vec3.ZERO;
	private final Set<UUID> moveHit = new HashSet<>();
	private final List<Vec3> patches = new ArrayList<>();
	private int cooldown = 80;
	private int arriveTicks;
	// the Disintegration Wave (independent of the move once it has left his feet)
	private double waveRadius = -1;
	private Vec3 waveOrigin = Vec3.ZERO;
	private final Set<UUID> waveHit = new HashSet<>();
	/** Test hook: moves never start on their own. */
	public boolean noAutoMoves;

	public GalactusEntity(EntityType<? extends GalactusEntity> type, Level level) {
		super(type, level);
		setNoGravity(true);
		noPhysics = true;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, GalactusConfig.galactus().health)
				.add(Attributes.ARMOR, 0.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, GalactusConfig.galactus().range);
	}

	@Override
	public String model() {
		return GalactusAnims.GALACTUS;
	}

	@Override
	protected Clip idleClip() {
		return GalactusAnims.GALACTUS_IDLE;
	}

	public void configure(double health) {
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		super.setHealth((float) health);
		threat.range(GalactusConfig.galactus().range);
	}

	public BossThreat threat() {
		return threat;
	}

	public Move move() {
		return move;
	}

	public boolean busy() {
		return move != Move.NONE;
	}

	/** Model scale relative to the 60-block contract (weak points and the hand move with it). */
	private double s() {
		return GalactusConfig.galactus().renderHeight / 60.0;
	}

	public void beginArrival(int ticks) {
		arriveTicks = ticks;
	}

	// ---------------------------------------------------------------- health: never below 1, the body shrugs most of it off

	@Override
	public void setHealth(float health) {
		super.setHealth(Math.max(1.0f, health));
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.DROWN)
				|| source.is(DamageTypes.FALL) || super.isInvulnerableTo(source);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (move == Move.RETREAT) {
			return false;
		}
		if (source.getEntity() instanceof LivingEntity a && GalactusCombat.isCosmic(a)) {
			return false;
		}
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			amount *= (float) GalactusConfig.galactus().bodyDamageMultiplier;
		}
		float before = getHealth();
		boolean hurt = super.hurt(source, amount);
		if (hurt) {
			threat.record(source, Math.max(0.5f, before - getHealth()));
			afterDamage();
		}
		return hurt;
	}

	/** A weak point took {@code dealt}: it comes off him in full (pass-through), and goes on the threat table. */
	public void onWeakPointHit(ServerLevel level, GalactusWeakPointEntity part, DamageSource source, float dealt) {
		setHealth(getHealth() - (float) (dealt * GalactusConfig.weakPoints().passThrough));
		threat.record(source, dealt * 2f);
		hurtTime = 10;
		hurtDuration = 10;
		GalactusEvent e = event();
		if (e != null) {
			e.setWeakPointHealth(part.kind(), part.getHealth());
		}
		afterDamage();
	}

	public void onWeakPointBroken(ServerLevel level, GalactusWeakPointEntity part) {
		int kind = part.kind();
		weakIds[kind] = null;
		setHealth(getHealth() - (float) (getMaxHealth() * GalactusConfig.weakPoints().breakDamageFraction));
		GalactusFx.burst(level, part.position(), 4.0);
		GalactusFx.shake(level, position(), 1.2f, 30, 160);
		level.playSound(null, part.getX(), part.getY(), part.getZ(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 4.0f, 0.4f);
		if (move == Move.NONE) {
			setAnim(GalactusAnims.GALACTUS_WEAKPOINT_HURT);
		}
		GalactusEvent e = event();
		if (e != null) {
			e.onWeakPointBroken(level, kind);
		}
		afterDamage();
	}

	private void afterDamage() {
		GalactusEvent e = event();
		if (e != null && level() instanceof ServerLevel server) {
			e.checkNullifierMoment(server);
		}
	}

	// ---------------------------------------------------------------- the weak points and the hand

	/** Where weak point {@code kind} sits right now (world space). */
	public Vec3 weakPointPos(int kind) {
		double s = s();
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 left = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
		Vec3 base = position();
		Vec3 local = switch (kind) {
			case GalactusWeakPointEntity.KIND_EMBLEM -> new Vec3(0, 39, 7);
			case GalactusWeakPointEntity.KIND_CREST -> new Vec3(0, 58, 1);
			case GalactusWeakPointEntity.KIND_LEFT -> new Vec3(13, 22, 5);
			default -> new Vec3(-13, 22, 5);
		};
		if (kind == GalactusWeakPointEntity.KIND_LEFT && move == Move.TRANSMUTE) {
			// the palm goes down to the ground in front of him
			double t = Mth.clamp(moveTick / (double) Math.max(1, GalactusAnims.GALACTUS_TRANSMUTE.hit()), 0, 1);
			double back = moveTick > GalactusAnims.GALACTUS_TRANSMUTE.hit() ? Mth.clamp((moveTick - GalactusAnims.GALACTUS_TRANSMUTE.hit()) / 16.0, 0, 1) : 0;
			t = t * (1 - back);
			local = local.lerp(new Vec3(9, 1.5, 16), t);
		}
		if (kind == GalactusWeakPointEntity.KIND_RIGHT && move == Move.GRAB && hand() != null) {
			return hand().position().add(0, 1.0, 0);
		}
		Vec3 l = local.scale(s);
		return base.add(left.scale(l.x)).add(0, l.y, 0).add(fwd.scale(l.z));
	}

	/** His face (where the grab lifts its victim to). */
	public Vec3 facePos() {
		double s = s();
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		return position().add(0, 50 * s, 0).add(fwd.scale(11 * s));
	}

	public Vec3 eyePos() {
		double s = s();
		float yaw = yBodyRot * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		return position().add(0, 53 * s, 0).add(fwd.scale(4 * s));
	}

	public GalactusWeakPointEntity weakPoint(int kind) {
		UUID id = weakIds[kind];
		return id != null && level() instanceof ServerLevel s && s.getEntity(id) instanceof GalactusWeakPointEntity w && w.isAlive() ? w : null;
	}

	public GalactusHandEntity hand() {
		return handId != null && level() instanceof ServerLevel s && s.getEntity(handId) instanceof GalactusHandEntity h && h.isAlive() ? h : null;
	}

	public LivingEntity grabbedEntity(ServerLevel level) {
		return grabbedId != null && level.getEntity(grabbedId) instanceof LivingEntity l ? l : null;
	}

	/** Creates / places the unbroken weak points (every server tick; public for the gametests). */
	public void keepWeakPoints(ServerLevel level) {
		GalactusEvent e = event();
		int fighters = e != null ? e.fighterCount() : 1;
		boolean sealed = e != null && e.pylonsStanding(level) > 0;
		for (int k = 0; k < GalactusWeakPointEntity.KINDS; k++) {
			if (e != null && e.weakPointBroken(k)) {
				continue;
			}
			GalactusWeakPointEntity w = weakPoint(k);
			if (w == null) {
				w = com.projecthero.mod.galactus.entity.GalactusEntityTypes.WEAK_POINT.create(level);
				if (w == null) {
					continue;
				}
				double max = GalactusConfig.weakPointHealthFor(k, fighters);
				float hp = e != null && e.weakPointHealth(k) > 0 ? e.weakPointHealth(k) : (float) max;
				Vec3 at = weakPointPos(k);
				w.moveTo(at.x, at.y - w.getBbHeight() * 0.5, at.z, 0f, 0f);
				w.configure(getUUID(), k, max, hp);
				level.addFreshEntity(w);
				weakIds[k] = w.getUUID();
				if (e != null) {
					e.setWeakPointHealth(k, hp);
				}
			}
			Vec3 at = weakPointPos(k);
			w.setPos(at.x, at.y - w.getBbHeight() * 0.5, at.z);
			w.setSealed(sealed);
		}
	}

	private void discardParts(ServerLevel level) {
		for (int k = 0; k < GalactusWeakPointEntity.KINDS; k++) {
			GalactusWeakPointEntity w = weakPoint(k);
			if (w != null) {
				w.discard();
			}
			weakIds[k] = null;
		}
		GalactusHandEntity h = hand();
		if (h != null) {
			h.discard();
		}
		handId = null;
	}

	@Override
	public void remove(RemovalReason reason) {
		if (level() instanceof ServerLevel server) {
			releaseGrab(server, false);
			discardParts(server);
		}
		super.remove(reason);
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void aiStep() {
		setDeltaMovement(Vec3.ZERO);
		super.aiStep();
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		ServerLevel level = (ServerLevel) level();
		keepWeakPoints(level);
		tickWave(level);
		if (tickCount % 10 == 0) {
			GalactusFx.forced(level, GalactusFx.COSMIC_SMALL, eyePos().x, eyePos().y, eyePos().z, 2, 0.6, 0.2, 0.6, 0.0);
		}
		if (arriveTicks > 0) {
			arriveTicks--;
			if (arriveTicks % 3 == 0) {
				GalactusFx.forced(level, GalactusFx.COSMIC, getX(), getY() + 30 * s(), getZ(), 20, 5, 25 * s(), 5, 0.05);
			}
			return;
		}
		if (move == Move.RETREAT) {
			return;
		}
		threat.tick();
		LivingEntity target = getTarget();
		GalactusEvent e = event();
		if (target == null || !GalactusCombat.canTarget(target) || e != null && target instanceof Player p && !e.isFighter(p.getUUID())) {
			Vec3 c = e != null ? Vec3.atCenterOf(e.center()) : position();
			Player p = GalactusCombat.nearestFighter(level, c, GalactusConfig.galactus().range);
			target = p != null && (e == null || e.isFighter(p.getUUID())) ? p : null;
			setTarget(target);
		}
		if (target != null) {
			faceTowards(target.position(), move == Move.NONE ? 3.0f : 1.0f);
		}
		if (move != Move.NONE) {
			tickMove(level);
			return;
		}
		if (noAutoMoves || target == null || --cooldown > 0) {
			return;
		}
		chooseMove(level, target);
	}

	private void chooseMove(ServerLevel level, LivingEntity target) {
		GalactusEvent e = event();
		boolean canSummon = e == null || e.punishersAlive(level) < GalactusConfig.summons().cap;
		int roll = random.nextInt(100);
		if (roll < 28) {
			startMove(level, Move.EYE_BEAM, target);
		} else if (roll < 50) {
			startMove(level, Move.GRAB, target);
		} else if (roll < 70) {
			startMove(level, Move.TRANSMUTE, target);
		} else if (roll < 88 || !canSummon) {
			startMove(level, Move.WAVE, target);
		} else {
			startMove(level, Move.SUMMON, target);
		}
	}

	/** Starts {@code m} on {@code target} now (also the command / harness / gametest hook). */
	public void startMove(ServerLevel level, Move m, LivingEntity target) {
		move = m;
		moveTick = 0;
		moveHit.clear();
		setTarget(target);
		switch (m) {
			case EYE_BEAM -> {
				setAnim(GalactusAnims.GALACTUS_EYE_BEAM);
				Vec3 p = target != null ? target.position() : position();
				Vec3 away = p.subtract(position()).multiply(1, 0, 1);
				if (away.lengthSqr() < 1.0e-3) {
					away = new Vec3(1, 0, 0);
				}
				Vec3 perp = new Vec3(-away.z, 0, away.x).normalize();
				double half = GalactusConfig.eyeBeam().length * 0.5;
				beamFrom = clampToArena(p.subtract(perp.scale(half)));
				beamTo = clampToArena(p.add(perp.scale(half)));
				level.playSound(null, getX(), getY() + 50, getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 4.0f, 0.4f);
			}
			case GRAB -> {
				setAnim(GalactusAnims.GALACTUS_REACH);
				grabStage = 0;
				grabStageTick = 0;
				grabShadow = target != null ? target.position() : position().add(0, 0, 10);
				GalactusHandEntity h = GalactusEntityTypes.HAND.create(level);
				if (h != null) {
					GalactusEvent e = event();
					h.configure(getUUID(), GalactusConfig.gripHealthFor(e != null ? e.fighterCount() : 1));
					h.moveTo(grabShadow.x, grabShadow.y + 30, grabShadow.z, yBodyRot, 0f);
					level.addFreshEntity(h);
					handId = h.getUUID();
				}
			}
			case TRANSMUTE -> {
				setAnim(GalactusAnims.GALACTUS_TRANSMUTE);
				patches.clear();
				GalactusEvent e = event();
				List<Player> victims = e != null ? new ArrayList<>(e.fighterPlayers(level)) : new ArrayList<>();
				if (victims.isEmpty() && target instanceof Player tp) {
					victims.add(tp);
				}
				java.util.Collections.shuffle(victims, new java.util.Random(random.nextLong()));
				for (Player v : victims) {
					if (patches.size() >= GalactusConfig.transmute().maxPatches) {
						break;
					}
					patches.add(new Vec3(v.getX(), GalactusCombat.groundY(level, v.getX(), v.getY(), v.getZ()), v.getZ()));
				}
				if (patches.isEmpty() && target != null) {
					patches.add(target.position());
				}
			}
			case WAVE -> setAnim(GalactusAnims.GALACTUS_DISINTEGRATE);
			case SUMMON -> setAnim(GalactusAnims.GALACTUS_SUMMON);
			case RETREAT -> setAnim(GalactusAnims.GALACTUS_RETREAT);
			default -> setAnim(null);
		}
	}

	private Vec3 clampToArena(Vec3 p) {
		GalactusEvent e = event();
		if (e == null) {
			return p;
		}
		Vec3 c = Vec3.atBottomCenterOf(e.center());
		Vec3 d = p.subtract(c).multiply(1, 0, 1);
		double max = GalactusEvent.arena() - 2;
		if (d.horizontalDistance() > max) {
			d = d.scale(max / d.horizontalDistance());
		}
		return new Vec3(c.x + d.x, p.y, c.z + d.z);
	}

	private void endMove() {
		move = Move.NONE;
		moveTick = 0;
		cooldown = GalactusConfig.galactus().moveCooldownTicks + random.nextInt(30);
	}

	private void tickMove(ServerLevel level) {
		moveTick++;
		switch (move) {
			case EYE_BEAM -> tickEyeBeam(level);
			case GRAB -> tickGrab(level);
			case TRANSMUTE -> tickTransmute(level);
			case WAVE -> {
				Clip c = GalactusAnims.GALACTUS_DISINTEGRATE;
				if (moveTick < c.hit() && moveTick % 3 == 0) {
					GalactusFx.ring(level, GalactusFx.VOID, position().add(0, 0.3, 0), 8 * s() + moveTick * 0.1, 40);
				}
				if (moveTick == c.hit()) {
					waveRadius = 6 * s();
					waveOrigin = position();
					waveHit.clear();
					GalactusFx.shake(level, position(), 1.0f, 30, 160);
					level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 5.0f, 0.5f);
				}
				if (moveTick >= c.length()) {
					endMove();
				}
			}
			case SUMMON -> {
				Clip c = GalactusAnims.GALACTUS_SUMMON;
				if (moveTick == c.hit()) {
					GalactusEvent e = event();
					if (e != null) {
						e.summonPunishers(level, GalactusConfig.summons().perCast, null);
					}
					level.playSound(null, getX(), getY() + 40, getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 5.0f, 0.4f);
				}
				if (moveTick >= c.length()) {
					endMove();
				}
			}
			default -> {
			}
		}
	}

	private void tickEyeBeam(ServerLevel level) {
		Clip c = GalactusAnims.GALACTUS_EYE_BEAM;
		GalactusConfig.EyeBeam cfg = GalactusConfig.eyeBeam();
		int hit = c.hit();
		if (moveTick < hit) {
			if (moveTick % 4 == 0) {
				GalactusFx.line(level, GalactusFx.COSMIC, beamFrom.add(0, 0.2, 0), beamTo.add(0, 0.2, 0), 1.0);
			}
			if (moveTick % 6 == 0) {
				Vec3 eye = eyePos();
				GalactusFx.forced(level, ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 6, 1.5, 0.4, 1.5, 0.02);
			}
		} else if (moveTick < c.length()) {
			double t = Mth.clamp((moveTick - hit) / (double) Math.max(1, c.length() - hit - 6), 0, 1);
			Vec3 at = beamFrom.lerp(beamTo, t);
			double gy = GalactusCombat.groundY(level, at.x, at.y + 4, at.z);
			at = new Vec3(at.x, gy, at.z);
			if (moveTick % 2 == 0) {
				GalactusFx.line(level, GalactusFx.COSMIC, eyePos(), at, 2.0);
				GalactusFx.forced(level, ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 8, cfg.width * 0.4, 0.2, cfg.width * 0.4, 0.04);
				GalactusFx.forced(level, GalactusFx.EMBER, at.x, at.y + 0.5, at.z, 6, cfg.width * 0.5, 0.4, cfg.width * 0.5, 0.02);
			}
			if (moveTick == hit) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GUARDIAN_ATTACK, SoundSource.HOSTILE, 5.0f, 0.4f);
			}
			for (LivingEntity e : GalactusCombat.targetsNear(level, at.add(0, 1.0, 0), cfg.width + 1.0)) {
				double dy = e.getY() - at.y;
				if (dy > -2 && dy < 4.5 && moveHit.add(e.getUUID()) && GalactusCombat.hit(level, this, e, cfg.damage)) {
					e.igniteForSeconds(cfg.setFireSeconds);
				}
			}
		}
		if (moveTick >= c.length()) {
			endMove();
		}
	}

	private void tickGrab(ServerLevel level) {
		GalactusConfig.Grab cfg = GalactusConfig.grab();
		GalactusHandEntity h = hand();
		if (h == null && grabStage < 3) {
			// the grip was broken (or the hand is gone): end it
			releaseGrab(level, true);
			endMove();
			return;
		}
		grabStageTick++;
		switch (grabStage) {
			case 0 -> { // the hand comes down over the shadow
				Clip c = GalactusAnims.GALACTUS_REACH;
				double t = Mth.clamp(moveTick / (double) c.hit(), 0, 1);
				double y = grabShadow.y + 30 * (1 - t * t);
				h.setPos(grabShadow.x, y, grabShadow.z);
				if (moveTick % 2 == 0) {
					double r = cfg.radius * (0.4 + 0.6 * t);
					GalactusFx.ring(level, GalactusFx.VOID, grabShadow.add(0, 0.15, 0), r, 18 + (int) (r * 4));
					GalactusFx.forced(level, GalactusFx.VOID, grabShadow.x, grabShadow.y + 0.15, grabShadow.z, (int) (r * 3), r * 0.5, 0, r * 0.5, 0);
				}
				if (moveTick >= c.hit()) {
					LivingEntity v = null;
					LivingEntity target = getTarget();
					double best = Double.MAX_VALUE;
					for (LivingEntity e : GalactusCombat.targetsNear(level, grabShadow.add(0, 1, 0), cfg.radius + 1)) {
						double dy = e.getY() - grabShadow.y;
						if (dy > -2 && dy < 4) {
							double d = e.distanceToSqr(grabShadow) - (e == target ? 1 : 0);
							if (d < best) {
								best = d;
								v = e;
							}
						}
					}
					GalactusFx.shake(level, grabShadow, 0.7f, 12, 48);
					level.playSound(null, grabShadow.x, grabShadow.y, grabShadow.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3.0f, 0.3f);
					if (v != null) {
						grabbedId = v.getUUID();
						h.setGripping(true);
						grabStage = 1;
						grabStageTick = 0;
						setAnim(GalactusAnims.GALACTUS_GRAB_LIFT);
						GalactusEvent e = event();
						if (e != null) {
							e.onGrabbed(level, v);
						}
					} else {
						for (LivingEntity e : GalactusCombat.targetsNear(level, grabShadow, cfg.radius + 2)) {
							GalactusCombat.hit(level, this, e, 6.0f);
						}
						grabStage = 3;
						grabStageTick = 0;
					}
				}
			}
			case 1, 2 -> { // lift to his face, hold, squeeze
				LivingEntity v = grabbedEntity(level);
				if (v == null || !v.isAlive()) {
					releaseGrab(level, false);
					grabStage = 3;
					grabStageTick = 0;
					return;
				}
				Vec3 face = facePos();
				Vec3 at;
				if (grabStage == 1) {
					double t = Mth.clamp(grabStageTick / (double) cfg.liftTicks, 0, 1);
					at = grabShadow.lerp(face, t * t * (3 - 2 * t));
					if (grabStageTick >= cfg.liftTicks) {
						grabStage = 2;
						grabStageTick = 0;
					}
				} else {
					at = face;
					if (grabStageTick == cfg.holdTicks) {
						setAnim(GalactusAnims.GALACTUS_FLING);
					}
					if (grabStageTick == cfg.holdTicks + GalactusAnims.GALACTUS_FLING.hit()) {
						fling(level, v);
						grabStage = 3;
						grabStageTick = 0;
						return;
					}
				}
				h.setPos(at.x, at.y - 2.0, at.z);
				pin(v, at);
				if (grabStageTick % 20 == 10) {
					GalactusCombat.hit(level, level.damageSources().mobAttack(this), v, cfg.squeezeDamagePerSecond);
				}
			}
			default -> { // the hand withdraws
				h = hand();
				if (h != null) {
					h.setGripping(false);
					h.setPos(h.getX(), h.getY() + 1.5, h.getZ());
					if (grabStageTick > 16) {
						h.discard();
						handId = null;
					}
				}
				if (grabStageTick > 16) {
					endMove();
				}
			}
		}
	}

	private static void pin(LivingEntity v, Vec3 at) {
		v.setDeltaMovement(Vec3.ZERO);
		v.fallDistance = 0;
		if (v instanceof ServerPlayer p) {
			p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
		} else {
			v.teleportTo(at.x, at.y, at.z);
		}
	}

	private void fling(ServerLevel level, LivingEntity v) {
		GalactusConfig.Grab cfg = GalactusConfig.grab();
		grabbedId = null;
		GalactusHandEntity h = hand();
		if (h != null) {
			h.setGripping(false);
		}
		Vec3 out = v.position().subtract(position()).multiply(1, 0, 1);
		if (out.lengthSqr() < 1.0e-3) {
			out = new Vec3(1, 0, 0);
		}
		GalactusEvent e = event();
		if (e != null) {
			// fling towards the arena's middle so nobody is thrown out of it
			out = Vec3.atBottomCenterOf(e.center()).subtract(position()).multiply(1, 0, 1);
		}
		Vec3 vel = out.normalize().scale(cfg.flingSpeed).add(0, 0.6, 0);
		GalactusCombat.hit(level, this, v, cfg.flingDamage);
		v.setDeltaMovement(vel);
		v.hurtMarked = true;
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 3.0f, 0.4f);
	}

	/** The grip was broken (the hand's health ran out): the victim drops free, slowly. */
	public void onGripBroken(ServerLevel level) {
		LivingEntity v = grabbedEntity(level);
		releaseGrab(level, true);
		handId = null;
		if (move == Move.GRAB) {
			setAnim(GalactusAnims.GALACTUS_WEAKPOINT_HURT);
			endMove();
		}
		GalactusEvent e = event();
		if (e != null && v != null) {
			e.onGripBroken(level, v);
		}
	}

	private void releaseGrab(ServerLevel level, boolean gently) {
		LivingEntity v = grabbedEntity(level);
		grabbedId = null;
		if (v != null && gently) {
			v.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, true));
		}
	}

	/** Test / command hook: is {@code e} in his hand right now? */
	public boolean isHolding(Entity e) {
		return e != null && e.getUUID().equals(grabbedId);
	}

	private void tickTransmute(ServerLevel level) {
		Clip c = GalactusAnims.GALACTUS_TRANSMUTE;
		GalactusConfig.Transmute cfg = GalactusConfig.transmute();
		if (moveTick < c.hit() && moveTick % 3 == 0) {
			for (Vec3 p : patches) {
				GalactusFx.ring(level, GalactusFx.COSMIC, p.add(0, 0.2, 0), cfg.radius + 0.5, 20);
				GalactusFx.forced(level, ParticleTypes.REVERSE_PORTAL, p.x, p.y + 0.3, p.z, 10, cfg.radius * 0.5, 0.1, cfg.radius * 0.5, 0.02);
			}
		}
		if (moveTick == c.hit()) {
			GalactusEvent e = event();
			for (Vec3 p : patches) {
				if (e != null) {
					e.transmute(level, p, cfg.radius, cfg.lifeTicks);
				}
				GalactusFx.forced(level, ParticleTypes.FLASH, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
			}
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 4.0f, 0.4f);
		}
		if (moveTick >= c.length()) {
			endMove();
		}
	}

	private void tickWave(ServerLevel level) {
		if (waveRadius < 0) {
			return;
		}
		GalactusConfig.Wave cfg = GalactusConfig.wave();
		waveRadius += cfg.speed;
		if (waveRadius > cfg.reach) {
			waveRadius = -1;
			return;
		}
		if (tickCount % 2 == 0) {
			int points = (int) Math.min(120, 12 + waveRadius * 1.2);
			GalactusFx.ring(level, GalactusFx.VOID, waveOrigin.add(0, 0.4, 0), waveRadius, points);
			GalactusFx.ring(level, GalactusFx.COSMIC, waveOrigin.add(0, 0.9, 0), waveRadius, points / 2);
		}
		double band = Math.max(1.2, cfg.speed + 0.4);
		AABB box = new AABB(waveOrigin, waveOrigin).inflate(waveRadius + band, 40, waveRadius + band);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, GalactusCombat::canTarget)) {
			double d = Math.sqrt(e.distanceToSqr(waveOrigin.x, e.getY(), waveOrigin.z));
			if (Math.abs(d - waveRadius) > band || waveHit.contains(e.getUUID())) {
				continue;
			}
			double ground = GalactusCombat.groundY(level, e.getX(), e.getY(), e.getZ());
			if (e.getY() - ground > cfg.clearHeight) {
				continue; // over it
			}
			waveHit.add(e.getUUID());
			if (GalactusCombat.hit(level, this, e, cfg.damage)) {
				Vec3 push = e.position().subtract(waveOrigin).multiply(1, 0, 1).normalize().scale(0.9);
				e.push(push.x, 0.4, push.z);
				e.hurtMarked = true;
			}
		}
	}

	public double waveRadius() {
		return waveRadius;
	}

	/** Gametest hook: a Disintegration Wave already {@code r} blocks out. */
	public void debugWave(double r) {
		waveRadius = r;
		waveOrigin = position();
		waveHit.clear();
	}

	// ---------------------------------------------------------------- the retreat

	/** The Nullifier: everything stops, he raises a hand (the event speaks) and is gone at the clip's end. */
	public void beginRetreat(ServerLevel level) {
		releaseGrab(level, true);
		GalactusHandEntity h = hand();
		if (h != null) {
			h.discard();
		}
		handId = null;
		waveRadius = -1;
		startMove(level, Move.RETREAT, null);
	}

	@Override
	public AABB getBoundingBoxForCulling() {
		double s = s();
		return getBoundingBox().inflate(24 * s, 0, 24 * s).expandTowards(0, 24 * s, 0);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 400 * 400;
	}
}
