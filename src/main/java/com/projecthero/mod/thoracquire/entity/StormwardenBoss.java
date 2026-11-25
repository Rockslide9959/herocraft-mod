package com.projecthero.mod.thoracquire.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.thoracquire.ThorAcquire;
import com.projecthero.mod.thoracquire.TrialOfAsgard;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
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
 * v0.17.7: <b>The Stormwarden</b>, the guardian of the Trial of Asgard -- an original Asgardian construct, a towering suit of
 * storm-forged armour (dark Uru plate, gold trim, a horned crested helm with a lightning-blue visor slit, a storm core in the
 * chest and a great rune-carved maul) with nothing inside but lightning. Custom GeckoLib body, about 3.6 blocks tall.
 * {@link #MAX_HEALTH} health, 12 armour; tuned for a well-geared player WITHOUT powers (the trial must be faced unpowered):
 * hard but fair, a few minutes.
 *
 * <p>Every attack is telegraphed:
 * <ul>
 * <li><b>Skyfall</b>: the maul goes up and crackling rings mark {@link #skyfallCount()} spots on the ground (the target's
 * feet and around) for {@link #SKYFALL_WINDUP} ticks, then lightning hits each: {@link #SKYFALL_DAMAGE} within 2 blocks.</li>
 * <li><b>Storm Charge</b> (6-18 blocks): a crouch while a line of sparks marks the path ({@link #CHARGE_WINDUP} ticks), then
 * a shoulder charge at {@link #CHARGE_SPEED} b/t -- {@link #CHARGE_DAMAGE} and thrown -- and a short stagger at the end
 * (the opening to punish).</li>
 * <li><b>Thunder Slam</b>: both hands on the maul overhead while a ring of sparks marks the reach ({@link #SLAM_WINDUP}
 * ticks), then a slam whose shockwave rolls out to {@link #SLAM_RADIUS} blocks -- {@link #SLAM_DAMAGE} to anyone ON THE
 * GROUND as the wave passes (jump it). Phase 2 sends a second wave.</li>
 * <li><b>Storm Spirits</b>: arms spread, crackling wisps form -- {@link #spiritCount()} {@link StormSpiritEntity}s that dart
 * at you ({@link StormSpiritEntity#DAMAGE} each, one hit kills them).</li>
 * <li>Maul swing in melee: 9.</li>
 * </ul>
 * <p><b>Phase 2</b> at half health: a 2 s surge (it can't be hurt) while lightning pours into it, then it is <b>Unbound</b> --
 * faster, cooldowns cut by 30%, five Skyfall bolts, a double Thunder Slam, four spirits.
 *
 * <p>Shared {@link BossThreat} table (switches to whoever hurts it most), boss bar within {@link #BAR_RANGE}, leashed to its
 * crater. Only damage from unpowered players (and their arrows / pets) hurts it -- borrowed power can't win the trial for
 * you. A trial boss that loses its trial (or is loaded with none running) dissipates; one from a spawn egg is free.
 */
public class StormwardenBoss extends Monster implements GeoEntity {
	public static final double MAX_HEALTH = 420.0;
	public static final double BAR_RANGE = 56.0;
	public static final double TETHER = 28.0;

	public static final float SKYFALL_DAMAGE = 9.0f;
	public static final float CHARGE_DAMAGE = 12.0f;
	public static final float SLAM_DAMAGE = 10.0f;
	public static final double CHARGE_SPEED = 1.0;
	public static final double SLAM_RADIUS = 8.0;

	public static final byte MOVE_NONE = 0;
	public static final byte MOVE_SKYFALL = 1;
	public static final byte MOVE_CHARGE = 2;
	public static final byte MOVE_SLAM = 3;
	public static final byte MOVE_SPIRITS = 4;
	public static final byte MOVE_SURGE = 5;
	public static final byte MOVE_FORM = 6;

	public static final int SKYFALL_WINDUP = 25;
	public static final int CHARGE_WINDUP = 16;
	public static final int CHARGE_RUN = 16;
	public static final int CHARGE_STAGGER = 18;
	public static final int SLAM_WINDUP = 24;
	public static final int SPIRIT_WINDUP = 20;
	public static final int SURGE_TICKS = 40;
	public static final int FORM_TICKS = 40;

	private static final EntityDataAccessor<Byte> DATA_MOVE = SynchedEntityData.defineId(StormwardenBoss.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(StormwardenBoss.class, EntityDataSerializers.BYTE);
	private static final DustParticleOptions MARK = new DustParticleOptions(new Vector3f(0.55f, 0.8f, 1f), 1.0f);
	private static final DustParticleOptions MARK_HOT = new DustParticleOptions(new Vector3f(0.95f, 0.98f, 1f), 1.25f);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final BossThreat threat = new BossThreat(this).range(48).holdWhile(() -> this.move() != MOVE_NONE);
	private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.projecthero.stormwarden").withStyle(ChatFormatting.AQUA),
			BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private BlockPos home;
	private boolean trialBound;

	int moveTicks;
	int skyfallCd = 60;
	int chargeCd = 90;
	int slamCd = 140;
	int spiritCd = 220;
	int gap = 20;
	private final List<Vec3> marks = new ArrayList<>();
	private Vec3 chargeDir = Vec3.ZERO;
	private final Set<LivingEntity> struck = new HashSet<>();
	private int movesUsed;

	public StormwardenBoss(EntityType<? extends StormwardenBoss> type, Level level) {
		super(type, level);
		this.xpReward = 250;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MOVE, MOVE_NONE);
		builder.define(DATA_PHASE, (byte) 1);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new MovesGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true,
				e -> !(e instanceof Player p) || (!p.getAbilities().instabuild && !p.isSpectator())));
	}

	// ---------------------------------------------------------------- state

	public byte move() {
		return entityData.get(DATA_MOVE);
	}

	private void setMove(byte m) {
		entityData.set(DATA_MOVE, m);
	}

	public int moveTicks() {
		return moveTicks;
	}

	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	public BossThreat threat() {
		return threat;
	}

	public ServerBossEvent bar() {
		return bar;
	}

	public int movesUsed() {
		return movesUsed;
	}

	public BlockPos home() {
		return home;
	}

	public void setHome(BlockPos home) {
		this.home = home == null ? null : home.immutable();
		if (home != null) {
			restrictTo(home, 16);
		}
	}

	public boolean trialBound() {
		return trialBound;
	}

	/** Marks this boss as the guardian of a running trial (it dissipates if the trial stops owning it). */
	public void bindToTrial(BlockPos crater) {
		this.trialBound = true;
		setHome(crater);
		begin(MOVE_FORM);
	}

	public int skyfallCount() {
		return phase() >= 2 ? 5 : 3;
	}

	public int spiritCount() {
		return phase() >= 2 ? 4 : 2;
	}

	private int cd(int base) {
		return phase() >= 2 ? (int) (base * 0.7) : base;
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	// ---------------------------------------------------------------- damage

	/** Only an unpowered player's own hits count: borrowed power can't win the trial. */
	public static boolean countsAgainst(DamageSource source) {
		Entity by = source.getEntity();
		if (by instanceof OwnableEntity pet && pet.getOwner() instanceof LivingEntity owner) {
			by = owner;
		}
		if (by instanceof ServerPlayer p) {
			return p.getAbilities().instabuild || !com.projecthero.mod.hero.HeroTiers.hasAnyPower(p);
		}
		return false;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return super.hurt(source, amount);
		}
		if (move() == MOVE_SURGE || move() == MOVE_FORM || source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING)) {
			return false;
		}
		if (!countsAgainst(source)) {
			if (source.getEntity() instanceof ServerPlayer p && tickCount % 10 == 0) {
				p.displayClientMessage(Component.translatable("message.projecthero.trial.borrowed_power").withStyle(ChatFormatting.GRAY), true);
			}
			if (level() instanceof ServerLevel s) {
				s.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2, getZ(), 6, 0.5, 0.8, 0.5, 0.1);
			}
			return false;
		}
		if (move() == MOVE_CHARGE && moveTicks >= CHARGE_WINDUP + CHARGE_RUN) {
			amount *= 1.25f; // the stagger after a charge: the opening
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server && isAlive()) {
			threat.record(source, amount);
			if (phase() == 1 && getHealth() <= getMaxHealth() * 0.5f) {
				startSurge(server);
			}
		}
		return hurt;
	}

	/** The second gate: whatever path a hit takes into the health (some power hooks bypass {@link #hurt}), borrowed power does nothing. */
	@Override
	protected void actuallyHurt(DamageSource source, float amount) {
		if (!source.is(DamageTypes.GENERIC_KILL) && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && source.getEntity() != null && !countsAgainst(source)) {
			return;
		}
		super.actuallyHurt(source, amount);
	}

	/** Half health: the surge into phase 2 (2 s, untouchable). */
	public void startSurge(ServerLevel level) {
		entityData.set(DATA_PHASE, (byte) 2);
		begin(MOVE_SURGE);
		marks.clear();
		bar.setColor(BossEvent.BossBarColor.PURPLE);
		bar.setName(Component.translatable("projecthero.trial.stormwarden.unbound").withStyle(ChatFormatting.LIGHT_PURPLE));
		getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.31);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 1.8f);
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(this) < BAR_RANGE * BAR_RANGE)) {
			p.displayClientMessage(Component.translatable("projecthero.trial.stormwarden.phase2").withStyle(ChatFormatting.LIGHT_PURPLE), false);
		}
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (trialBound && tickCount > 40 && !TrialOfAsgard.owns(this)) {
			dissipate();
			return;
		}
		threat.tick();
		if (skyfallCd > 0) {
			skyfallCd--;
		}
		if (chargeCd > 0) {
			chargeCd--;
		}
		if (slamCd > 0) {
			slamCd--;
		}
		if (spiritCd > 0) {
			spiritCd--;
		}
		if (gap > 0) {
			gap--;
		}
		if (move() == MOVE_SURGE || move() == MOVE_FORM) {
			tickMove(server); // these run without a target
		}
		if (tickCount % 3 == 0) {
			// lightning crackling over the plates; more of it once Unbound
			server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.4 + random.nextDouble() * 2.0, getZ(),
					phase() >= 2 ? 3 : 1, 0.5, 0.4, 0.5, 0.05);
		}
		bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (tickCount % 10 == 0) {
			refreshViewers(server);
		}
		if (home != null && tickCount % 20 == 0) {
			leash();
		}
	}

	private void refreshViewers(ServerLevel level) {
		viewers.removeIf(p -> {
			boolean keep = p.isAlive() && !p.isRemoved() && p.level() == level && p.distanceToSqr(this) <= (BAR_RANGE + 8) * (BAR_RANGE + 8);
			if (!keep) {
				bar.removePlayer(p);
			}
			return !keep;
		});
		for (ServerPlayer p : level.players()) {
			if (!viewers.contains(p) && !p.isSpectator() && p.distanceToSqr(this) <= BAR_RANGE * BAR_RANGE) {
				viewers.add(p);
				bar.addPlayer(p);
			}
		}
	}

	private void leash() {
		Vec3 h = Vec3.atBottomCenterOf(home);
		if (position().distanceTo(h) > TETHER || getY() < home.getY() - 10) {
			moveTo(h.x + 2, h.y, h.z + 2, getYRot(), 0f);
			setDeltaMovement(Vec3.ZERO);
			getNavigation().stop();
			if (move() == MOVE_CHARGE) {
				endMove();
			}
		}
	}

	/** The guardian fades into the storm (a lost trial, or no trial owns it). */
	public void dissipate() {
		if (level() instanceof ServerLevel s) {
			s.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.8, getZ(), 80, 0.8, 1.4, 0.8, 0.06);
			s.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.8, getZ(), 60, 0.8, 1.4, 0.8, 0.3);
			s.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.0f, 1.4f);
			s.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2f, 0.5f);
		}
		bar.removeAllPlayers();
		viewers.clear();
		discard();
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel s) {
			bar.removeAllPlayers();
			viewers.clear();
			s.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 2.0f, 0.6f);
			s.sendParticles(ParticleTypes.FLASH, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
			for (Entity e : s.getEntitiesOfClass(StormSpiritEntity.class, getBoundingBox().inflate(48))) {
				e.discard();
			}
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		viewers.remove(player);
		bar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		bar.removeAllPlayers();
		viewers.clear();
		super.remove(reason);
	}

	// ---------------------------------------------------------------- moves

	static boolean victim(LivingEntity e, LivingEntity self) {
		return e != self && e.isAlive() && !(e instanceof StormwardenBoss) && !(e instanceof StormSpiritEntity)
				&& !(e instanceof Player p && (p.getAbilities().instabuild || p.isSpectator()));
	}

	private void begin(byte which) {
		setMove(which);
		moveTicks = 0;
		movesUsed++;
		getNavigation().stop();
	}

	void endMove() {
		setMove(MOVE_NONE);
		moveTicks = 0;
		marks.clear();
		gap = phase() >= 2 ? 14 : 22;
	}

	private float scaled(float base) {
		return phase() >= 2 ? base * 1.15f : base;
	}

	/** Starts Skyfall now: marks the target's feet and spots around it (test hook too). */
	public void startSkyfall() {
		begin(MOVE_SKYFALL);
		marks.clear();
		LivingEntity t = getTarget();
		Vec3 c = t != null ? t.position() : position().add(getViewVector(1f).scale(6));
		marks.add(ground(c));
		for (int i = 1; i < skyfallCount(); i++) {
			double a = random.nextDouble() * Math.PI * 2;
			double r = 2.5 + random.nextDouble() * 3.0;
			marks.add(ground(c.add(Math.cos(a) * r, 0, Math.sin(a) * r)));
		}
		skyfallCd = cd(120);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 1.5f, 0.5f);
	}

	private Vec3 ground(Vec3 at) {
		int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
		if (Math.abs(y - at.y) > 6) {
			y = Mth.floor(at.y);
		}
		return new Vec3(at.x, y, at.z);
	}

	public List<Vec3> marks() {
		return marks;
	}

	public void startCharge() {
		begin(MOVE_CHARGE);
		struck.clear();
		LivingEntity t = getTarget();
		Vec3 to = t != null ? t.position().subtract(position()) : getViewVector(1f);
		to = new Vec3(to.x, 0, to.z);
		chargeDir = to.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : to.normalize();
		chargeCd = cd(100);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2f, 0.7f);
	}

	public void startSlam() {
		begin(MOVE_SLAM);
		struck.clear();
		slamCd = cd(160);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.2f, 1.3f);
	}

	public void startSpirits() {
		begin(MOVE_SPIRITS);
		spiritCd = cd(320);
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.4f, 0.6f);
	}

	void tickMove(ServerLevel level) {
		int t = ++moveTicks;
		switch (move()) {
			case MOVE_SKYFALL -> tickSkyfall(level, t);
			case MOVE_CHARGE -> tickCharge(level, t);
			case MOVE_SLAM -> tickSlam(level, t);
			case MOVE_SPIRITS -> tickSpirits(level, t);
			case MOVE_SURGE -> tickSurge(level, t);
			case MOVE_FORM -> tickForm(level, t);
			default -> endMove();
		}
	}

	private void faceTarget() {
		LivingEntity t = getTarget();
		if (t != null) {
			getLookControl().setLookAt(t, 30f, 30f);
			double dx = t.getX() - getX();
			double dz = t.getZ() - getZ();
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			setYRot(yaw);
			setYBodyRot(yaw);
			setYHeadRot(yaw);
		}
	}

	private void tickSkyfall(ServerLevel level, int t) {
		faceTarget();
		if (t <= SKYFALL_WINDUP) {
			if (t % 2 == 0) {
				float k = t / (float) SKYFALL_WINDUP;
				for (Vec3 m : marks) {
					ring(level, m, 2.0, k > 0.7f ? MARK_HOT : MARK, 14);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, m.x, m.y + 0.2, m.z, 2, 0.6, 0.05, 0.6, 0.05);
				}
			}
			// a spark climbing the raised maul
			if (t % 3 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 4.4, getZ(), 4, 0.15, 0.3, 0.15, 0.1);
			}
			return;
		}
		if (t == SKYFALL_WINDUP + 1) {
			for (Vec3 m : marks) {
				com.projecthero.mod.thoracquire.MjolnirLift.bolt(level, m);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(m, m).inflate(2.0, 3.0, 2.0),
						e -> victim(e, this) && e.position().distanceToSqr(m) <= 2.0 * 2.0 + 1.0)) {
					e.hurt(damageSources().mobAttack(this), scaled(SKYFALL_DAMAGE));
				}
			}
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2.0f, 0.9f);
		}
		if (t >= SKYFALL_WINDUP + 12) {
			endMove();
		}
	}

	private void tickCharge(ServerLevel level, int t) {
		if (t <= CHARGE_WINDUP) {
			faceTarget();
			LivingEntity tg = getTarget();
			if (tg != null) {
				Vec3 to = tg.position().subtract(position());
				to = new Vec3(to.x, 0, to.z);
				if (to.lengthSqr() > 1.0e-3) {
					chargeDir = to.normalize();
				}
			}
			if (t % 2 == 0) {
				for (double d = 1.5; d < CHARGE_RUN * CHARGE_SPEED; d += 1.0) {
					Vec3 p = position().add(chargeDir.scale(d));
					level.sendParticles(t > CHARGE_WINDUP - 5 ? MARK_HOT : MARK, p.x, p.y + 0.15, p.z, 1, 0.15, 0.02, 0.15, 0);
				}
			}
			return;
		}
		if (t <= CHARGE_WINDUP + CHARGE_RUN) {
			setDeltaMovement(chargeDir.x * CHARGE_SPEED, getDeltaMovement().y, chargeDir.z * CHARGE_SPEED);
			hurtMarked = true;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 6, 0.6, 0.8, 0.6, 0.15);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8), e -> victim(e, this))) {
				if (struck.add(e)) {
					e.hurt(damageSources().mobAttack(this), scaled(CHARGE_DAMAGE));
					e.push(chargeDir.x * 1.4, 0.45, chargeDir.z * 1.4);
					e.hurtMarked = true;
					level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8f, 0.6f);
				}
			}
			if (horizontalCollision) {
				moveTicks = CHARGE_WINDUP + CHARGE_RUN; // hit a wall: straight into the stagger
			}
			return;
		}
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (t >= CHARGE_WINDUP + CHARGE_RUN + CHARGE_STAGGER) {
			endMove();
		}
	}

	private void tickSlam(ServerLevel level, int t) {
		faceTarget();
		if (t <= SLAM_WINDUP) {
			if (t % 3 == 0) {
				ring(level, position(), SLAM_RADIUS, t > SLAM_WINDUP - 8 ? MARK_HOT : MARK, 40);
			}
			return;
		}
		int wave1 = t - SLAM_WINDUP;
		int wave2 = phase() >= 2 ? t - SLAM_WINDUP - 14 : -1;
		if (wave1 == 1 || wave2 == 1) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.7f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.8f, 0.7f);
			level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.6, 0.1, 0.6, 0);
			com.projecthero.mod.thoracquire.MjolnirLift.shake(level, this, 0.6f, 10);
			struck.clear();
		}
		for (int w : new int[] { wave1, wave2 }) {
			if (w < 1 || w > 11) {
				continue;
			}
			double r = w * (SLAM_RADIUS / 10.0);
			ring(level, position(), r, MARK_HOT, (int) (r * 6) + 6);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.2, getZ(), 6, r * 0.5, 0.05, r * 0.5, 0.05);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r + 1, 2, r + 1), e -> victim(e, this))) {
				double d = Math.sqrt(e.distanceToSqr(getX(), e.getY(), getZ()));
				if (d >= r - 1.2 && d <= r + 0.6 && e.onGround() && Math.abs(e.getY() - getY()) < 2.0 && struck.add(e)) {
					e.hurt(damageSources().mobAttack(this), scaled(SLAM_DAMAGE));
					Vec3 away = e.position().subtract(position()).multiply(1, 0, 1);
					away = away.lengthSqr() < 1.0e-3 ? new Vec3(0, 0, 1) : away.normalize();
					e.push(away.x * 0.7, 0.55, away.z * 0.7);
					e.hurtMarked = true;
				}
			}
		}
		int end = SLAM_WINDUP + (phase() >= 2 ? 14 : 0) + 18;
		if (t >= end) {
			endMove();
		}
	}

	private void tickSpirits(ServerLevel level, int t) {
		faceTarget();
		if (t < SPIRIT_WINDUP) {
			if (t % 2 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.6, getZ(), 6, 1.6, 0.6, 1.6, 0.1);
			}
			return;
		}
		if (t == SPIRIT_WINDUP) {
			int n = spiritCount();
			for (int i = 0; i < n; i++) {
				StormSpiritEntity s = ThorAcquire.STORM_SPIRIT.create(level);
				if (s == null) {
					continue;
				}
				double a = Math.PI * 2 * i / n + getYRot() * Mth.DEG_TO_RAD;
				s.moveTo(getX() + Math.cos(a) * 1.8, getY() + 3.0, getZ() + Math.sin(a) * 1.8, 0f, 0f);
				s.setTarget(getTarget());
				level.addFreshEntity(s);
				level.sendParticles(ParticleTypes.FLASH, s.getX(), s.getY(), s.getZ(), 1, 0, 0, 0, 0);
			}
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.4f, 0.8f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 0.8f, 1.6f);
		}
		if (t >= SPIRIT_WINDUP + 12) {
			endMove();
		}
	}

	private void tickSurge(ServerLevel level, int t) {
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (t % 8 == 1) {
			com.projecthero.mod.thoracquire.MjolnirLift.bolt(level, position());
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.8, getZ(), 8, 0.7, 1.2, 0.7, 0.3);
		if (t >= SURGE_TICKS) {
			endMove();
		}
	}

	private void tickForm(ServerLevel level, int t) {
		setDeltaMovement(0, getDeltaMovement().y, 0);
		if (t % 2 == 0) {
			level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.8, getZ(), 6, 0.8, 1.2, 0.8, 0.02);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.8, getZ(), 6, 0.6, 1.3, 0.6, 0.2);
		}
		if (t >= FORM_TICKS) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6f, 0.5f);
			endMove();
		}
	}

	/** A dust ring of {@code n} points, radius {@code r}, on the ground at {@code c}. */
	static void ring(ServerLevel level, Vec3 c, double r, DustParticleOptions dust, int n) {
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			level.sendParticles(dust, c.x + Math.cos(a) * r, c.y + 0.12, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
		}
	}

	// ---------------------------------------------------------------- save

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (home != null) {
			tag.put("StormHome", NbtUtils.writeBlockPos(home));
		}
		tag.putBoolean("TrialBound", trialBound);
		tag.putByte("Phase", (byte) phase());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setHome(NbtUtils.readBlockPos(tag, "StormHome").orElse(null));
		trialBound = tag.getBoolean("TrialBound");
		if (tag.getByte("Phase") >= 2) {
			entityData.set(DATA_PHASE, (byte) 2);
		}
	}

	// ---------------------------------------------------------------- GeckoLib

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "main", 3, this::predicate));
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop("animation.stormwarden." + name);
	}

	private static RawAnimation once(String name) {
		return RawAnimation.begin().thenPlayAndHold("animation.stormwarden." + name);
	}

	private PlayState predicate(AnimationState<StormwardenBoss> state) {
		if (isDeadOrDying()) {
			return state.setAndContinue(once("death"));
		}
		switch (move()) {
			case MOVE_SKYFALL -> {
				return state.setAndContinue(once("skyfall"));
			}
			case MOVE_CHARGE -> {
				return state.setAndContinue(once("charge"));
			}
			case MOVE_SLAM -> {
				return state.setAndContinue(once(phase() >= 2 ? "slam_double" : "slam"));
			}
			case MOVE_SPIRITS -> {
				return state.setAndContinue(once("spirits"));
			}
			case MOVE_SURGE -> {
				return state.setAndContinue(once("surge"));
			}
			case MOVE_FORM -> {
				return state.setAndContinue(once("form"));
			}
			default -> {
			}
		}
		if (swinging) {
			return state.setAndContinue(once("swing"));
		}
		if (state.isMoving() || state.getLimbSwingAmount() > 0.05f) {
			return state.setAndContinue(loop("walk"));
		}
		return state.setAndContinue(loop("idle"));
	}

	/** Every move as one goal above the melee, so they never overlap. */
	static final class MovesGoal extends Goal {
		private final StormwardenBoss mob;

		MovesGoal(StormwardenBoss mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (mob.move() != MOVE_NONE) {
				return true;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || mob.gap > 0 || !mob.onGround()) {
				return false;
			}
			double d = mob.distanceTo(t);
			boolean sees = mob.getSensing().hasLineOfSight(t);
			if (mob.spiritCd <= 0 && mob.level() instanceof ServerLevel s
					&& s.getEntitiesOfClass(StormSpiritEntity.class, mob.getBoundingBox().inflate(32)).size() < 2) {
				mob.startSpirits();
				return true;
			}
			if (mob.slamCd <= 0 && d <= SLAM_RADIUS - 1) {
				mob.startSlam();
				return true;
			}
			if (mob.chargeCd <= 0 && d >= 6 && d <= 18 && sees && mob.random.nextInt(2) == 0) {
				mob.startCharge();
				return true;
			}
			if (mob.skyfallCd <= 0 && d <= 28 && sees) {
				mob.startSkyfall();
				return true;
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return mob.move() != MOVE_NONE && mob.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (mob.level() instanceof ServerLevel level && mob.move() != MOVE_SURGE && mob.move() != MOVE_FORM) {
				mob.tickMove(level);
			}
		}

		@Override
		public void stop() {
			if (mob.move() != MOVE_NONE && mob.move() != MOVE_SURGE && mob.move() != MOVE_FORM) {
				mob.endMove();
			}
		}
	}
}
