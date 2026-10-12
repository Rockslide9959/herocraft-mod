package com.projecthero.mod.ultron.contagion.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ultron.UltronCombat;
import com.projecthero.mod.ultron.UltronEntityTypes;
import com.projecthero.mod.ultron.UltronFx;
import com.projecthero.mod.ultron.contagion.Contagion;
import com.projecthero.mod.ultron.contagion.ContagionConfig;
import com.projecthero.mod.ultron.contagion.ContagionState;
import com.projecthero.mod.ultron.contagion.ContagionUnits;
import com.projecthero.mod.ultron.contagion.OmegaAnims;
import com.projecthero.mod.ultron.contagion.SokoviaFinale;
import com.projecthero.mod.ultron.entity.UltronRobot;
import com.projecthero.mod.ultron.entity.UltronSkin;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: <b>Ultron Omega</b> -- the Sokovia Protocol's final form. 4 blocks tall, flies; a custom GeckoLib model
 * ({@code geo/ultron/ultron_omega.geo.json}, clips in {@link OmegaAnims}). 4,000 health + 900 per extra fighter (cap
 * 12,000), 16 armour / 10 toughness, no adaptation; the shared {@link BossThreat} table picks his target.
 *
 * <ol>
 *   <li><b>Omega</b> (100-75%): Vibranium Strike (3 hits, 8 / 8 / 14), Concussive Blast (a red cone, 12, thrown toward the
 *       edge), Encephalo Lattice (three beams rotating round him for 4 s) and Core Siphon (every 60 s: 5 s at the Core,
 *       +5% charge -- deal 8% of his health in that window to interrupt).</li>
 *   <li><b>Legion</b> (75-50%): he splits into four; the three decoys deal half damage, never show a hit, and every hit on
 *       one heals the real one 0.5%. Tells: the Mind Stone holder sees the real one outlined gold, an Iron Man scanner
 *       reads the decoys as "Signal Echo", and the real one's eyes flicker with the Core's pulse (all client-side). 10% of
 *       his health dealt to the real one collapses them.</li>
 *   <li><b>Iron Legion</b> (50-25%): 3 + fighters Hijacked Marks; shielded while 3 or more stand.</li>
 *   <li><b>Upload</b> (25-0%): three Uplink Antennae and a 35 s upload; shielded, spamming drones and blasts. All three
 *       down in time: stranded (x1.5 damage, faster). Otherwise he escapes.</li>
 *   <li><b>The Vision moment</b>: at 0 health he kneels. A fighter holding the Mind Stone can hold right-click for 3 s to
 *       fire the Mind Stone Beam; with none, he self-destructs after 10 s (a 3 s telegraph, 6-block blast). Either way he
 *       dies.</li>
 * </ol>
 */
public class UltronOmegaEntity extends UltronRobot implements GeoEntity {
	public enum Move { NONE, STRIKE, BLAST, LATTICE, SIPHON, SPLIT, OVERRIDE, UPLOAD, KNEEL, DISINTEGRATE, SELF_DESTRUCT }

	/**
	 * Client hook (set by the client): is the local viewer looking at this Omega with a Mind Stone in hand (within range)?
	 * Then only the real one is outlined, in gold -- the Legion tell. A client-side decision only (privacy rule).
	 */
	public static Predicate<UltronOmegaEntity> clientGoldOutline = e -> false;
	public static final int GOLD = 0xF5C542;

	private static final EntityDataAccessor<Boolean> DATA_DECOY = SynchedEntityData.defineId(UltronOmegaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_SHIELDED = SynchedEntityData.defineId(UltronOmegaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_BUSY = SynchedEntityData.defineId(UltronOmegaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_VISION = SynchedEntityData.defineId(UltronOmegaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_STRANDED = SynchedEntityData.defineId(UltronOmegaEntity.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Float> DATA_LATTICE_YAW = SynchedEntityData.defineId(UltronOmegaEntity.class, EntityDataSerializers.FLOAT);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final ServerBossEvent bossBar = new ServerBossEvent(Component.translatable("entity.projecthero.ultron_omega"), BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.NOTCHED_20);
	private final BossThreat threat = new BossThreat(this).range(80).filter(UltronCombat::canTarget).holdWhile(() -> move() != Move.NONE);

	private Move move = Move.NONE;
	private int moveTick;
	private int cooldown = 60;
	private int lastMove = -1;
	private long lastLine = -1000;
	private boolean introduced;
	private boolean splitDone;
	private boolean overrideDone;
	private boolean uploadDone;
	private boolean uploading;
	private boolean realDeath;
	private boolean byMindStone;
	private float damageSinceSplit;
	private float siphonDamage;
	private long nextSiphonAt = -1;
	private int visionTicks;
	private float latticeBase;
	private UUID realId;
	private final List<UUID> decoys = new ArrayList<>();
	private final List<UUID> legion = new ArrayList<>();
	private float damageScale = 1f;
	private int version = 1;
	private int fighters = 1;
	private final float bobPhase;

	public UltronOmegaEntity(EntityType<? extends UltronOmegaEntity> type, Level level) {
		super(type, level);
		this.xpReward = 1000;
		this.bobPhase = random.nextFloat() * 100f;
		setNoGravity(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		ContagionConfig.Omega cfg = ContagionConfig.omega();
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, cfg.health)
				.add(Attributes.ARMOR, cfg.armor)
				.add(Attributes.ARMOR_TOUGHNESS, cfg.armorToughness)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FLYING_SPEED, 0.6)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 80.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DECOY, false);
		builder.define(DATA_SHIELDED, false);
		builder.define(DATA_BUSY, false);
		builder.define(DATA_VISION, false);
		builder.define(DATA_STRANDED, false);
		builder.define(DATA_LATTICE_YAW, Float.NaN);
	}

	@Override
	public UltronSkin skin() {
		return UltronSkin.PRIME;
	}

	@Override
	protected void registerGoals() {
	}

	/** Health and damage for {@code fighters} fighters at Ultron {@code version}. */
	public void configure(int fighters, int version) {
		this.fighters = Math.max(1, fighters);
		this.version = Math.max(1, version);
		double hp = ContagionConfig.omegaHealthFor(this.fighters, this.version);
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);
		damageScale = ContagionConfig.omegaDamageScale(this.version);
		if (version > 1) {
			bossBar.setName(Component.translatable("entity.projecthero.ultron_omega.version", version));
		}
	}

	// ---------------------------------------------------------------- accessors

	public BossThreat threat() {
		return threat;
	}

	public Move move() {
		return move;
	}

	public boolean isDecoy() {
		return entityData.get(DATA_DECOY);
	}

	public boolean shielded() {
		return entityData.get(DATA_SHIELDED);
	}

	public boolean inVision() {
		return entityData.get(DATA_VISION);
	}

	public boolean stranded() {
		return entityData.get(DATA_STRANDED);
	}

	public boolean uploading() {
		return uploading;
	}

	/** The Lattice's base yaw while it spins (NaN otherwise), for the client's beams. */
	public float latticeYaw() {
		return entityData.get(DATA_LATTICE_YAW);
	}

	/** The fight phase from his health: 1 Omega, 2 Legion, 3 Iron Legion, 4 Upload. */
	public int phase() {
		float f = getHealth() / getMaxHealth();
		return f > 0.75f ? 1 : f > 0.5f ? 2 : f > 0.25f ? 3 : 4;
	}

	public List<UUID> decoyIds() {
		return decoys;
	}

	public List<UUID> legionIds() {
		return legion;
	}

	public int visionTicks() {
		return visionTicks;
	}

	public boolean killedByMindStone() {
		return byMindStone;
	}

	private SokoviaFinale finale() {
		return level() instanceof ServerLevel server ? ContagionState.get(server).finale : null;
	}

	// ---------------------------------------------------------------- tick

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server) || isDeadOrDying() || isNoAi()) {
			return;
		}
		setNoGravity(true);
		fallDistance = 0;
		if (isDecoy()) {
			Entity real = realId == null ? null : server.getEntity(realId);
			if (!(real instanceof UltronOmegaEntity r) || !r.isAlive() || !r.decoys.contains(getUUID()) || r.inVision()) {
				collapse(server);
				return;
			}
		} else {
			bossBar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		}
		entityData.set(DATA_BUSY, move != Move.NONE || uploading);
		if (inVision()) {
			tickVision(server);
			return;
		}
		if (isStunned()) {
			setDeltaMovement(getDeltaMovement().add(0, -0.03, 0));
			return;
		}
		LivingEntity target = getTarget();
		if (target != null && (!UltronCombat.canTarget(target) || distanceToSqr(target) > 80 * 80)) {
			setTarget(null);
			target = null;
		}
		LivingEntity hot = threat.tick();
		if (hot != null) {
			target = hot;
			setTarget(hot);
		}
		if (target == null && tickCount % 10 == 0) {
			Player p = UltronCombat.nearestPlayer(server, position(), 64);
			if (p != null) {
				setTarget(p);
				target = p;
			}
		}
		if (!isDecoy()) {
			if (!introduced && target instanceof Player) {
				introduced = true;
				say(server, version > 1 ? "intro_version" : "intro", true);
			}
			updateShield(server);
			if (move == Move.NONE) {
				phaseGates(server);
			}
			if (uploading) {
				tickUpload(server, target);
				return;
			}
		}
		if (move != Move.NONE) {
			tickMove(server, target);
		} else {
			hover(server, target);
			if (target != null && --cooldown <= 0) {
				pickMove(server, target);
			}
		}
		if (tickCount % 8 == 0) {
			server.sendParticles(UltronFx.RED_SMALL, getX(), getY() + 0.1, getZ(), 1, 0.2, 0.02, 0.2, 0.0);
		}
	}

	private void phaseGates(ServerLevel server) {
		float f = getHealth() / getMaxHealth();
		if (!splitDone && f <= 0.75f) {
			startMove(server, Move.SPLIT, getTarget());
		} else if (!overrideDone && f <= 0.5f) {
			startMove(server, Move.OVERRIDE, getTarget());
		} else if (!uploadDone && f <= 0.25f) {
			startUpload(server);
		}
	}

	/** Shielded while uploading or while 3+ Iron Legion suits stand. */
	private void updateShield(ServerLevel server) {
		legion.removeIf(id -> !(server.getEntity(id) instanceof LivingEntity le) || !le.isAlive());
		boolean on = uploading || legion.size() >= ContagionConfig.omega().legionShieldAt;
		if (shielded() != on) {
			entityData.set(DATA_SHIELDED, on);
			server.playSound(null, getX(), getY(), getZ(), on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0f,
					on ? 0.6f : 1.4f);
		}
		if (on && tickCount % 12 == 0) {
			UltronFx.ring(server, UltronFx.RED_SMALL, position().add(0, getBbHeight() * 0.5, 0), 1.6, 8);
		}
	}

	/** Holds a point ~8 blocks off the target, kept over the citadel. */
	private void hover(ServerLevel server, LivingEntity target) {
		Vec3 want;
		if (target != null) {
			Vec3 away = position().subtract(target.position());
			double ang = Math.atan2(away.z, away.x) + 0.01 * Mth.sin((tickCount + bobPhase) * 0.02f) * 20;
			want = new Vec3(target.getX() + Math.cos(ang) * 8, target.getY() + 3.0 + Math.sin((tickCount + bobPhase) * 0.06) * 0.8,
					target.getZ() + Math.sin(ang) * 8);
			face(target.getEyePosition());
		} else {
			want = position();
		}
		steer(clampToCitadel(want), stranded() ? 0.5 : 0.38);
	}

	private Vec3 clampToCitadel(Vec3 want) {
		SokoviaFinale f = finale();
		if (f == null || f.centre() == null) {
			return want;
		}
		Vec3 c = f.centre();
		double r = ContagionConfig.finale().radius - 3;
		Vec3 d = new Vec3(want.x - c.x, 0, want.z - c.z);
		if (d.lengthSqr() > r * r) {
			d = d.normalize().scale(r);
		}
		double y = Math.max(c.y + 1.0, Math.min(c.y + 14, want.y));
		return new Vec3(c.x + d.x, y, c.z + d.z);
	}

	private void steer(Vec3 want, double speed) {
		Vec3 dir = want.subtract(position());
		double len = dir.length();
		Vec3 desired = len < 0.3 ? Vec3.ZERO : dir.scale(Math.min(speed, len * 0.25) / len);
		setDeltaMovement(getDeltaMovement().lerp(desired, 0.2));
	}

	private void face(Vec3 at) {
		double dx = at.x - getX(), dz = at.z - getZ(), dy = at.y - getEyeY();
		float yaw = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
		setYRot(yaw);
		yBodyRot = yaw;
		yHeadRot = yaw;
		setXRot((float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0 / Math.PI)));
	}

	// ---------------------------------------------------------------- moves

	private void pickMove(ServerLevel server, LivingEntity target) {
		List<Move> options = new ArrayList<>(List.of(Move.STRIKE, Move.BLAST));
		if (!isDecoy()) {
			options.add(Move.LATTICE);
			SokoviaFinale f = finale();
			long now = server.getGameTime();
			if (nextSiphonAt < 0) {
				nextSiphonAt = now + ContagionConfig.omega().siphonIntervalTicks;
			}
			if (f != null && f.coreTop() != null && now >= nextSiphonAt && phase() <= 3 && !shielded()) {
				nextSiphonAt = now + ContagionConfig.omega().siphonIntervalTicks;
				startMove(server, Move.SIPHON, target);
				return;
			}
		}
		if (options.size() > 1 && lastMove >= 0) {
			options.remove(Move.values()[lastMove]);
		}
		startMove(server, options.get(random.nextInt(options.size())), target);
	}

	/** Starts {@code m} now (also the gametests' / harness's hook). */
	public void startMove(ServerLevel server, Move m, LivingEntity target) {
		move = m;
		moveTick = 0;
		lastMove = m.ordinal();
		switch (m) {
			case STRIKE -> anim(OmegaAnims.STRIKE);
			case BLAST -> {
				anim(OmegaAnims.BLAST);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0f, 1.3f);
			}
			case LATTICE -> {
				anim(OmegaAnims.LATTICE);
				latticeBase = getYRot();
				say(server, "lattice", false);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5f, 0.5f);
			}
			case SIPHON -> {
				siphonDamage = 0f;
				say(server, "siphon", false);
			}
			case SPLIT -> {
				anim(OmegaAnims.SPLIT);
				say(server, "split", true);
			}
			case OVERRIDE -> {
				anim(OmegaAnims.OVERRIDE);
				say(server, "override", true);
			}
			default -> {
			}
		}
	}

	private void endMove() {
		move = Move.NONE;
		entityData.set(DATA_LATTICE_YAW, Float.NaN);
		int base = ContagionConfig.omega().attackCooldownTicks + random.nextInt(25);
		cooldown = (int) (base * (stranded() ? 0.55 : 1.0));
	}

	private void anim(String clip) {
		triggerAnim("action", clip);
	}

	private void tickMove(ServerLevel server, LivingEntity target) {
		moveTick++;
		switch (move) {
			case STRIKE -> tickStrike(server, target);
			case BLAST -> tickBlast(server, target);
			case LATTICE -> tickLattice(server);
			case SIPHON -> tickSiphon(server);
			case SPLIT -> {
				setDeltaMovement(getDeltaMovement().scale(0.6));
				if (moveTick == OmegaAnims.SPLIT_IMPACT) {
					split(server);
				}
				if (moveTick >= OmegaAnims.SPLIT_TICKS) {
					endMove();
				}
			}
			case OVERRIDE -> {
				setDeltaMovement(getDeltaMovement().scale(0.6));
				if (moveTick == OmegaAnims.OVERRIDE_IMPACT) {
					ironLegion(server);
				}
				if (moveTick >= OmegaAnims.OVERRIDE_TICKS) {
					endMove();
				}
			}
			default -> endMove();
		}
	}

	private float dmg(float base) {
		return base * damageScale * (isDecoy() ? (float) ContagionConfig.omega().decoyDamageFraction : 1f);
	}

	private void tickStrike(ServerLevel server, LivingEntity target) {
		if (target != null && moveTick < OmegaAnims.STRIKE_HITS[0]) {
			face(target.getEyePosition());
			Vec3 to = target.position().add(target.position().subtract(position()).normalize().scale(-2.0)).add(0, 0.3, 0);
			steer(clampToCitadel(to), 0.9);
		} else {
			setDeltaMovement(getDeltaMovement().scale(0.7));
		}
		float[] dmgs = ContagionConfig.omega().strikeDamage;
		for (int i = 0; i < OmegaAnims.STRIKE_HITS.length; i++) {
			if (moveTick == OmegaAnims.STRIKE_HITS[i]) {
				boolean last = i == OmegaAnims.STRIKE_HITS.length - 1;
				Vec3 look = getViewVector(1f).multiply(1, 0, 1).normalize();
				Vec3 c = position().add(0, 1.5, 0).add(look.scale(1.6));
				for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.8), UltronCombat::canTarget)) {
					Vec3 to = e.position().subtract(position()).multiply(1, 0, 1);
					if (to.lengthSqr() > 0.01 && to.normalize().dot(look) < 0.2) {
						continue;
					}
					UltronCombat.hit(server, this, e, dmg(dmgs[Math.min(i, dmgs.length - 1)]));
					Vec3 push = to.normalize().scale(last ? 1.4 : 0.5);
					e.push(push.x, last ? 0.55 : 0.2, push.z);
					e.hurtMarked = true;
				}
				server.sendParticles(last ? ParticleTypes.EXPLOSION : ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				server.sendParticles(UltronFx.RED, c.x, c.y, c.z, 8, 0.4, 0.4, 0.4, 0.05);
				server.playSound(null, getX(), getY(), getZ(), last ? SoundEvents.ANVIL_LAND : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE,
						1.6f, last ? 0.6f : 0.8f);
			}
		}
		if (moveTick >= OmegaAnims.STRIKE_TICKS) {
			endMove();
		}
	}

	private void tickBlast(ServerLevel server, LivingEntity target) {
		ContagionConfig.Omega cfg = ContagionConfig.omega();
		setDeltaMovement(getDeltaMovement().scale(0.6));
		if (target != null && moveTick < OmegaAnims.BLAST_IMPACT - 4) {
			face(target.getEyePosition());
		}
		Vec3 look = getViewVector(1f);
		Vec3 from = position().add(0, getBbHeight() * 0.6, 0).add(look.scale(0.8));
		if (moveTick < OmegaAnims.BLAST_IMPACT && moveTick % 3 == 0) {
			server.sendParticles(UltronFx.RED, from.x, from.y, from.z, 4, 0.2, 0.2, 0.2, 0.02);
		}
		if (moveTick == OmegaAnims.BLAST_IMPACT) {
			concussiveBlast(server, from, look);
		}
		if (moveTick >= OmegaAnims.BLAST_TICKS) {
			endMove();
		}
	}

	/** The red cone: {@link ContagionConfig.Omega#blastDamage}, thrown towards the citadel's edge. */
	private void concussiveBlast(ServerLevel server, Vec3 from, Vec3 look) {
		ContagionConfig.Omega cfg = ContagionConfig.omega();
		SokoviaFinale f = finale();
		for (int i = 1; i <= 8; i++) {
			Vec3 p = from.add(look.scale(i * cfg.blastRange / 8));
			server.sendParticles(UltronFx.RED_BIG, p.x, p.y, p.z, 3, i * 0.18, i * 0.18, i * 0.18, 0.02);
		}
		server.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 2.0f, 1.2f);
		for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cfg.blastRange), UltronCombat::canTarget)) {
			Vec3 to = e.getEyePosition().subtract(from);
			if (to.length() > cfg.blastRange || to.normalize().dot(look) < 0.75) {
				continue;
			}
			UltronCombat.hit(server, this, e, dmg(cfg.blastDamage));
			Vec3 out = f != null && f.centre() != null ? e.position().subtract(f.centre()).multiply(1, 0, 1) : to.multiply(1, 0, 1);
			if (out.lengthSqr() < 0.01) {
				out = look.multiply(1, 0, 1);
			}
			out = out.normalize().scale(1.7);
			e.setDeltaMovement(out.x, 0.45, out.z);
			e.hurtMarked = true;
		}
	}

	private void tickLattice(ServerLevel server) {
		ContagionConfig.Omega cfg = ContagionConfig.omega();
		SokoviaFinale f = finale();
		Vec3 floor = f != null && f.centre() != null ? new Vec3(getX(), f.centre().y + 0.2, getZ()) : position();
		steer(new Vec3(getX(), floor.y, getZ()), 0.3);
		if (moveTick < OmegaAnims.LATTICE_WINDUP) {
			if (moveTick % 2 == 0) {
				for (int k = 0; k < 3; k++) {
					Vec3 dir = Vec3.directionFromRotation(0, latticeBase + k * 120f);
					Vec3 a = position().add(0, 1.1, 0);
					UltronFx.beam(server, a, a.add(dir.scale(cfg.latticeLength)), UltronFx.TELEGRAPH, 3);
				}
			}
			return;
		}
		float yaw = latticeBase + (moveTick - OmegaAnims.LATTICE_WINDUP) * 4.5f;
		entityData.set(DATA_LATTICE_YAW, yaw);
		Vec3 a = position().add(0, 1.1, 0);
		for (int k = 0; k < 3; k++) {
			Vec3 dir = Vec3.directionFromRotation(0, yaw + k * 120f);
			Vec3 b = a.add(dir.scale(cfg.latticeLength));
			if (moveTick % 2 == 0) {
				UltronFx.beam(server, a, b, UltronFx.ENCEPHALO, 3);
			}
			if (moveTick % 5 == 0) {
				for (LivingEntity e : UltronCombat.alongLine(server, a, b, 0.6)) {
					UltronCombat.hit(server, this, e, dmg(cfg.latticeDamage));
				}
			}
		}
		if (moveTick >= OmegaAnims.LATTICE_TICKS) {
			endMove();
		}
	}

	private void tickSiphon(ServerLevel server) {
		ContagionConfig.Omega cfg = ContagionConfig.omega();
		SokoviaFinale f = finale();
		Vec3 top = f == null ? null : f.coreTop();
		if (top == null) {
			endMove();
			return;
		}
		Vec3 spot = top.add(0, 1.5, 0);
		if (moveTick < 1000 && distanceToSqr(spot) > 2.5 * 2.5 && moveTick < 200) {
			steer(spot, 0.8);
			face(top);
			if (moveTick == 199) {
				endMove();
			}
			return;
		}
		if (moveTick < 1000) {
			moveTick = 1000; // arrived: the channel starts
			anim(OmegaAnims.SIPHON);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3.0f, 0.4f);
		}
		setDeltaMovement(Vec3.ZERO);
		int t = moveTick - 1000;
		if (t % 2 == 0) {
			UltronFx.beam(server, top, position().add(0, 2.2, 0), UltronFx.STREAK, 3);
		}
		if (siphonDamage >= getMaxHealth() * cfg.siphonInterruptFraction) {
			say(server, "siphon_broken", true);
			UltronFx.wreck(server, position().add(0, 2, 0), 1.0);
			stun(40);
			anim(OmegaAnims.HURT);
			endMove();
			return;
		}
		if (t >= OmegaAnims.SIPHON_TICKS) {
			if (f != null) {
				f.addCharge(server, cfg.siphonCharge);
			}
			anim(OmegaAnims.HURT);
			endMove();
		}
	}

	// ---------------------------------------------------------------- Legion

	private void split(ServerLevel server) {
		splitDone = true;
		damageSinceSplit = 0f;
		SokoviaFinale f = finale();
		Vec3 c = f != null && f.centre() != null ? f.centre().add(0, 2, 0) : position();
		int n = Math.max(1, ContagionConfig.omega().decoys) + 1;
		int realSlot = random.nextInt(n);
		double a0 = random.nextDouble() * Math.PI * 2;
		for (int i = 0; i < n; i++) {
			double a = a0 + i * Math.PI * 2 / n;
			Vec3 at = c.add(Math.cos(a) * 7, 0, Math.sin(a) * 7);
			UltronFx.forced(server, UltronFx.RED_BIG, at.x, at.y + 2, at.z, 20, 0.5, 1.0, 0.5, 0.02);
			if (i == realSlot) {
				moveTo(at.x, at.y, at.z, getYRot(), 0f);
				continue;
			}
			UltronOmegaEntity d = ContagionEntityTypes.ULTRON_OMEGA.create(server);
			if (d == null) {
				continue;
			}
			d.entityData.set(DATA_DECOY, true);
			d.realId = getUUID();
			d.version = version;
			d.damageScale = damageScale;
			d.getAttribute(Attributes.MAX_HEALTH).setBaseValue(getMaxHealth());
			d.setHealth(getHealth());
			d.moveTo(at.x, at.y, at.z, getYRot(), 0f);
			d.introduced = true;
			d.cooldown = 20 + random.nextInt(40);
			d.addTag(Contagion.UNIT_TAG);
			server.addFreshEntity(d);
			decoys.add(d.getUUID());
		}
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 3.0f, 0.6f);
	}

	/** The real one took 10%: every decoy collapses. */
	public void collapseDecoys(ServerLevel server) {
		for (UUID id : new ArrayList<>(decoys)) {
			if (server.getEntity(id) instanceof UltronOmegaEntity d) {
				d.collapse(server);
			}
		}
		decoys.clear();
		say(server, "legion_collapse", true);
	}

	private void collapse(ServerLevel server) {
		UltronFx.forced(server, UltronFx.RED, getX(), getY() + 2, getZ(), 30, 0.5, 1.2, 0.5, 0.1);
		server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2, getZ(), 20, 0.5, 1.2, 0.5, 0.2);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0f, 1.6f);
		discard();
	}

	// ---------------------------------------------------------------- Iron Legion

	private void ironLegion(ServerLevel server) {
		overrideDone = true;
		ContagionConfig.Omega cfg = ContagionConfig.omega();
		SokoviaFinale f = finale();
		List<ServerPlayer> fighters = f != null ? f.fighterPlayers(server) : List.of();
		int n = cfg.legionBase + Math.max(1, fighters.size());
		List<String> suits = new ArrayList<>();
		for (ServerPlayer p : fighters) {
			String worn = TonyStark.hasPower(p) ? IronManArmor.wornSuitId(p) : null;
			if (worn != null && !suits.contains(worn)) {
				suits.add(worn); // at least one copies each suited fighter's own Mark
			}
		}
		while (suits.size() < n) {
			suits.add(HijackedMarkEntity.LEGION_SUITS.get(random.nextInt(HijackedMarkEntity.LEGION_SUITS.size())));
		}
		Vec3 c = f != null && f.centre() != null ? f.centre() : position();
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			Vec3 at = c.add(Math.cos(a) * (ContagionConfig.finale().radius - 4), 4 + random.nextDouble() * 3, Math.sin(a) * (ContagionConfig.finale().radius - 4));
			HijackedMarkEntity m = ContagionEntityTypes.HIJACKED_MARK.create(server);
			if (m == null) {
				continue;
			}
			m.moveTo(at.x, at.y, at.z, random.nextFloat() * 360f, 0f);
			m.configure(suits.get(i), cfg.legionHealth * ContagionConfig.unitHealthScale(version), damageScale);
			m.addTag(Contagion.UNIT_TAG);
			server.addFreshEntity(m);
			legion.add(m.getUUID());
			UltronFx.forced(server, ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
			UltronFx.beam(server, position().add(0, 3.6, 0), at.add(0, 1, 0), UltronFx.STREAK, 10);
		}
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0f, 0.5f);
		for (ServerPlayer p : fighters) {
			com.projecthero.mod.ironman.JarvisDialogue.speak(p, "contagion_legion");
		}
	}

	// ---------------------------------------------------------------- Upload

	private void startUpload(ServerLevel server) {
		uploadDone = true;
		uploading = true;
		move = Move.NONE;
		anim(OmegaAnims.UPLOAD);
		say(server, "upload", true);
		SokoviaFinale f = finale();
		if (f != null) {
			f.startUpload(server, this);
		}
	}

	private void tickUpload(ServerLevel server, LivingEntity target) {
		SokoviaFinale f = finale();
		Vec3 c = f != null && f.centre() != null ? f.centre().add(0, 9, 0) : position();
		steer(c, 0.4);
		if (target != null) {
			face(target.getEyePosition());
		}
		if (tickCount % 60 == 0 && ContagionUnits.countNear(server, position(), 40) < 8) {
			for (int i = 0; i < 2; i++) {
				Vec3 at = position().add(random.nextGaussian() * 3, 1, random.nextGaussian() * 3);
				ContagionUnits.spawn(server, UltronEntityTypes.DRONE, at, target, version);
			}
		}
		if (tickCount % 90 == 45 && target != null) {
			Vec3 look = target.getEyePosition().subtract(getEyePosition()).normalize();
			concussiveBlast(server, position().add(0, getBbHeight() * 0.6, 0).add(look.scale(0.8)), look);
			anim(OmegaAnims.BLAST);
		}
		if (tickCount % 4 == 0) {
			UltronFx.beam(server, position().add(0, 3.6, 0), position().add(0, 60, 0), UltronFx.TETHER, 5);
		}
	}

	/** The finale: every antenna fell in time. Stranded: no shield, x1.5 damage taken, faster. */
	public void strand(ServerLevel server) {
		uploading = false;
		entityData.set(DATA_STRANDED, true);
		bossBar.setColor(BossEvent.BossBarColor.PURPLE);
		say(server, "stranded", true);
		cooldown = 20;
		updateShield(server);
	}

	/** The finale: the upload completed -- he is gone (a red streak into the sky). */
	public void escape(ServerLevel server) {
		say(server, "escaped", true);
		UltronFx.beam(server, position().add(0, 2, 0), position().add(0, 120, 0), UltronFx.CANNON, 30);
		UltronFx.forced(server, UltronFx.RED_BIG, getX(), getY() + 2, getZ(), 60, 0.6, 1.5, 0.6, 0.05);
		for (UUID id : decoys) {
			if (server.getEntity(id) instanceof UltronOmegaEntity d) {
				d.discard();
			}
		}
		bossBar.removeAllPlayers();
		discard();
	}

	// ---------------------------------------------------------------- the Vision moment

	private void enterVision(ServerLevel server) {
		entityData.set(DATA_VISION, true);
		uploading = false;
		move = Move.KNEEL;
		moveTick = 0;
		visionTicks = 0;
		setHealth(1f);
		entityData.set(DATA_SHIELDED, false);
		entityData.set(DATA_LATTICE_YAW, Float.NaN);
		anim(OmegaAnims.KNEEL);
		say(server, "vision", true);
		bossBar.setColor(BossEvent.BossBarColor.YELLOW);
		bossBar.setName(Component.translatable("entity.projecthero.ultron_omega.vision"));
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 3.0f, 0.4f);
		collapseDecoys(server);
	}

	private void tickVision(ServerLevel server) {
		ContagionConfig.Omega cfg = ContagionConfig.omega();
		SokoviaFinale f = finale();
		double floor = f != null && f.centre() != null ? f.centre().y : getY();
		setDeltaMovement(0, getY() > floor + 0.1 ? -0.15 : 0, 0);
		visionTicks++;
		moveTick++;
		if (move == Move.DISINTEGRATE) {
			if (moveTick % 2 == 0) {
				UltronFx.forced(server, ParticleTypes.END_ROD, getX(), getY() + 2, getZ(), 6, 0.5, 1.2, 0.5, 0.05);
				server.sendParticles(UltronFx.STEEL, getX(), getY() + 2, getZ(), 6, 0.6, 1.3, 0.6, 0.05);
			}
			if (moveTick >= OmegaAnims.DISINTEGRATE_TICKS) {
				finalDeath(server);
			}
			return;
		}
		if (move == Move.SELF_DESTRUCT) {
			double r = cfg.selfDestructRadius * Math.min(1.0, moveTick / (double) cfg.selfDestructTelegraphTicks);
			if (moveTick % 3 == 0) {
				UltronFx.ring(server, UltronFx.RED_BIG, position().add(0, 0.3, 0), r, (int) (12 + r * 6));
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.HOSTILE, 2.0f, 0.5f + moveTick / 60f);
			}
			if (moveTick >= Math.max(cfg.selfDestructTelegraphTicks, OmegaAnims.SELF_DESTRUCT_TICKS)) {
				UltronFx.forced(server, ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 3, 1, 1, 1, 0);
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0f, 0.5f);
				for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(cfg.selfDestructRadius), UltronCombat::canTarget)) {
					double d = e.distanceTo(this);
					if (d <= cfg.selfDestructRadius) {
						UltronCombat.hit(server, this, e, (float) (cfg.selfDestructDamage * (1.0 - 0.6 * d / cfg.selfDestructRadius)));
					}
				}
				finalDeath(server);
			}
			return;
		}
		if (visionTicks % 10 == 0) {
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2, getZ(), 6, 0.4, 0.8, 0.4, 0.1);
		}
		if (visionTicks >= cfg.selfDestructTicks) {
			move = Move.SELF_DESTRUCT;
			moveTick = 0;
			anim(OmegaAnims.SELF_DESTRUCT);
			say(server, "self_destruct", true);
		}
	}

	/** The Mind Stone Beam struck him while he knelt: he disintegrates. False if he isn't kneeling (or already going). */
	public boolean visionStruck(ServerLevel server, ServerPlayer by) {
		if (!inVision() || move == Move.DISINTEGRATE || move == Move.SELF_DESTRUCT) {
			return false;
		}
		move = Move.DISINTEGRATE;
		moveTick = 0;
		byMindStone = true;
		anim(OmegaAnims.DISINTEGRATE);
		say(server, "disintegrate", true);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0f, 0.4f);
		return true;
	}

	private void finalDeath(ServerLevel server) {
		realDeath = true;
		hurt(server.damageSources().genericKill(), Float.MAX_VALUE);
		if (isAlive()) {
			setHealth(0f);
			super.die(server.damageSources().genericKill());
		}
	}

	/** Test hook: one decoy of this Omega, {@code offset} away (as the Legion split makes them). */
	public UltronOmegaEntity spawnDecoyForTest(ServerLevel server, Vec3 at) {
		UltronOmegaEntity d = ContagionEntityTypes.ULTRON_OMEGA.create(server);
		if (d == null) {
			return null;
		}
		d.entityData.set(DATA_DECOY, true);
		d.realId = getUUID();
		d.getAttribute(Attributes.MAX_HEALTH).setBaseValue(getMaxHealth());
		d.setHealth(getMaxHealth());
		d.moveTo(at.x, at.y, at.z, 0f, 0f);
		d.introduced = true;
		d.addTag(Contagion.UNIT_TAG);
		server.addFreshEntity(d);
		decoys.add(d.getUUID());
		splitDone = true;
		return d;
	}

	/** Test hook: go straight to the kneel. */
	public void enterVisionForTest() {
		if (level() instanceof ServerLevel server) {
			enterVision(server);
		}
	}

	/** Test hook: skip to the self-destruct. */
	public void advanceVision(int ticks) {
		visionTicks += ticks;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide()) {
			return super.hurt(source, amount);
		}
		ServerLevel server = (ServerLevel) level();
		if (realDeath) {
			return super.hurt(source, amount);
		}
		if (inVision()) {
			return false;
		}
		if (isDecoy()) {
			if (source.getEntity() instanceof LivingEntity && realId != null && server.getEntity(realId) instanceof UltronOmegaEntity real) {
				real.healFromDecoy();
				server.sendParticles(UltronFx.RED_SMALL, getX(), getY() + 2, getZ(), 6, 0.4, 0.8, 0.4, 0.02);
			}
			return false;
		}
		if (shielded() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			if (tickCount % 4 == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.2f, 0.5f);
				if (source.getEntity() instanceof ServerPlayer p) {
					p.displayClientMessage(Component.translatable(uploading ? "message.projecthero.contagion.omega_uploading"
							: "message.projecthero.contagion.omega_legion_shield").withStyle(ChatFormatting.RED), true);
				}
			}
			return false;
		}
		if (stranded()) {
			amount *= (float) ContagionConfig.omega().strandedMultiplier;
		}
		float before = getHealth();
		boolean hurt = super.hurt(source, amount);
		if (hurt) {
			threat.record(source, amount);
			float dealt = Math.max(0f, before - Math.max(getHealth(), 0f));
			if (!decoys.isEmpty()) {
				damageSinceSplit += dealt;
				if (damageSinceSplit >= getMaxHealth() * ContagionConfig.omega().legionCollapseFraction) {
					collapseDecoys(server);
				}
			}
			if (move == Move.SIPHON) {
				siphonDamage += dealt;
			}
		}
		return hurt;
	}

	/** A hit on a decoy heals the real one 0.5%. */
	void healFromDecoy() {
		if (!inVision()) {
			heal((float) (getMaxHealth() * ContagionConfig.omega().decoyHealFraction));
		}
	}

	@Override
	public void die(DamageSource source) {
		if (!level().isClientSide() && !realDeath && !isDecoy() && level() instanceof ServerLevel server) {
			enterVision(server); // not dead: he kneels
			return;
		}
		super.die(source);
		bossBar.removeAllPlayers();
		if (!isDecoy() && level() instanceof ServerLevel server) {
			SokoviaFinale.onOmegaDefeated(server, this, byMindStone);
		}
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (level() instanceof ServerLevel server && !isRemoved() && deathTime >= 10) {
			UltronFx.wreck(server, position().add(0, 2, 0), 3.0);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	/** Only the real one shows his name to an Iron Man scanner; a decoy reads as a signal echo. */
	@Override
	public Component getName() {
		if (level().isClientSide() && isDecoy()) {
			return Component.translatable("entity.projecthero.ultron_omega.echo");
		}
		return super.getName();
	}

	/** The Mind Stone tell: decided on the client from the viewer's own state, never a server glowing flag. */
	@Override
	public boolean isCurrentlyGlowing() {
		if (level().isClientSide()) {
			// a viewer with the Mind Stone sees only the real one outlined (gold); anyone else's outline treats all four alike
			if (clientGoldOutline.test(this)) {
				return !isDecoy();
			}
			return super.isCurrentlyGlowing();
		}
		return super.isCurrentlyGlowing();
	}

	@Override
	public int getTeamColor() {
		if (level().isClientSide() && !isDecoy() && clientGoldOutline.test(this)) {
			return GOLD;
		}
		return super.getTeamColor();
	}

	private void say(ServerLevel server, String key, boolean force) {
		long now = server.getGameTime();
		if (!force && now - lastLine < 160) {
			return;
		}
		lastLine = now;
		Component line = Component.translatable("boss.projecthero.ultron.chat",
				Component.translatable("entity.projecthero.ultron_omega").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
				Component.translatable("boss.projecthero.ultron_omega.say." + key, version).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		for (ServerPlayer p : server.players()) {
			if (p.distanceToSqr(this) < 96 * 96) {
				p.sendSystemMessage(line);
			}
		}
	}

	// ---------------------------------------------------------------- flight, bar, save

	@Override
	public void travel(Vec3 input) {
		if (isControlledByLocalInstance()) {
			if (isDeadOrDying()) {
				super.travel(input);
				return;
			}
			move(MoverType.SELF, getDeltaMovement());
			setDeltaMovement(getDeltaMovement().scale(0.91));
		}
		calculateEntityAnimation(false);
	}

	@Override
	protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
	}

	@Override
	public boolean onClimbable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (!isDecoy()) {
			bossBar.addPlayer(player);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.removeAllPlayers();
		super.remove(reason);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("Decoy", isDecoy());
		if (realId != null) {
			tag.putUUID("Real", realId);
		}
		tag.putBoolean("SplitDone", splitDone);
		tag.putBoolean("OverrideDone", overrideDone);
		tag.putBoolean("UploadDone", uploadDone);
		tag.putBoolean("Uploading", uploading);
		tag.putBoolean("Stranded", stranded());
		tag.putBoolean("Vision", inVision());
		tag.putInt("VisionTicks", visionTicks);
		tag.putInt("Version", version);
		tag.putInt("Fighters", fighters);
		tag.putFloat("DamageScale", damageScale);
		ListTag d = new ListTag();
		for (UUID u : decoys) {
			d.add(NbtUtils.createUUID(u));
		}
		tag.put("Decoys", d);
		ListTag l = new ListTag();
		for (UUID u : legion) {
			l.add(NbtUtils.createUUID(u));
		}
		tag.put("Legion", l);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_DECOY, tag.getBoolean("Decoy"));
		realId = tag.hasUUID("Real") ? tag.getUUID("Real") : null;
		splitDone = tag.getBoolean("SplitDone");
		overrideDone = tag.getBoolean("OverrideDone");
		uploadDone = tag.getBoolean("UploadDone");
		uploading = tag.getBoolean("Uploading");
		entityData.set(DATA_STRANDED, tag.getBoolean("Stranded"));
		entityData.set(DATA_VISION, tag.getBoolean("Vision"));
		if (inVision()) {
			move = Move.KNEEL;
		}
		visionTicks = tag.getInt("VisionTicks");
		version = Math.max(1, tag.getInt("Version"));
		fighters = Math.max(1, tag.getInt("Fighters"));
		damageScale = tag.contains("DamageScale") ? tag.getFloat("DamageScale") : 1f;
		decoys.clear();
		for (Tag t : tag.getList("Decoys", Tag.TAG_INT_ARRAY)) {
			decoys.add(NbtUtils.loadUUID(t));
		}
		legion.clear();
		for (Tag t : tag.getList("Legion", Tag.TAG_INT_ARRAY)) {
			legion.add(NbtUtils.loadUUID(t));
		}
		if (stranded()) {
			bossBar.setColor(BossEvent.BossBarColor.PURPLE);
		}
		setNoGravity(true);
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<UltronOmegaEntity> action = new AnimationController<>(this, "action", OmegaAnims.TRANSITION, state -> PlayState.STOP);
		for (String name : OmegaAnims.ONCE) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay(OmegaAnims.PREFIX + name));
		}
		for (String name : OmegaAnims.HOLDS) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlayAndHold(OmegaAnims.PREFIX + name));
		}
		for (String name : OmegaAnims.LOOPS) {
			action.triggerableAnim(name, RawAnimation.begin().thenLoop(OmegaAnims.PREFIX + name));
		}
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", OmegaAnims.TRANSITION, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<UltronOmegaEntity> state) {
		if (isDeadOrDying() || entityData.get(DATA_BUSY) || inVision()) {
			return PlayState.STOP;
		}
		double speed = getDeltaMovement().horizontalDistance();
		return state.setAndContinue(RawAnimation.begin().thenLoop(OmegaAnims.PREFIX + (speed > 0.08 ? OmegaAnims.FLY : OmegaAnims.IDLE)));
	}

}
