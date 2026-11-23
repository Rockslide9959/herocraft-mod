package com.projecthero.mod.temple.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.temple.TempleClearing;
import com.projecthero.mod.temple.TempleEffects;
import com.projecthero.mod.temple.TempleGuards;
import com.projecthero.mod.temple.block.CanopicJarBlock;
import com.projecthero.mod.temple.block.TempleBlocks;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.17.7: <b>Ankhatep, the Bound King</b> -- the Ancient Temple's boss. An original undead sorcerer-king, 3.2 blocks
 * tall in his own GeckoLib body (a gilded death-mask under a striped nemes, a lapis-and-gold broad collar over grave
 * wrappings, a crook-headed staff with a green soul gem, shackles chained to the floor). {@link #MAX_HEALTH} health.
 *
 * <p><b>Phase 1, Bound</b>: chained before his throne -- he never strays more than {@link #CHAIN_REACH} from it.
 * <ul>
 * <li><b>Curse of the Bound King</b>: cast when the fight starts and kept up on everyone within {@link #CURSE_RANGE}
 * while any canopic jar stands ({@link TempleEffects#BOUND_CURSE}: 1 health every 2 s, no armour, never kills); each
 * drain heals him. Break the four jars round the arena and the curse lifts and he staggers ({@link #STAGGER_TICKS},
 * +50% damage taken).</li>
 * <li><b>Sand Wave</b>: staff raised 1.2 s while sand swirls, then a ring of sand races out to {@link #WAVE_RADIUS}:
 * 9 + thrown + Blindness to anyone on the ground it passes. Jump it.</li>
 * <li><b>Sand Lances</b>: 0.8 s, then three lines of erupting sand march toward his target (five in phase 2): 8 + launched.
 * Step sideways.</li>
 * <li><b>Raise the Guardians</b>: two temple guardians claw up out of the sand (three in phase 2, at most
 * {@link #MAX_MINIONS} at once).</li>
 * <li>A staff strike (12) when someone stands close.</li>
 * </ul>
 * <b>Phase 2, Unbound</b> (half health): he tears his chains apart (2.5 s, a shockwave), two canopic jars re-form if any
 * were broken and the curse comes back stronger, he walks free and faster, the Sand Wave comes twice, his cooldowns shorten.
 *
 * <p>Shared {@link BossThreat} table (switches to whoever hurts him most), boss bar within {@link #BAR_RANGE}. His death
 * (a 3 s collapse into sand) clears the temple ({@link TempleClearing#bossDefeated}). No static state.
 */
public class AnkhatepBoss extends Monster implements GeoEntity {
	public static final double MAX_HEALTH = 1100.0;
	public static final double CHAIN_REACH = 6.0;
	public static final double FREE_REACH = 22.0;
	public static final double CURSE_RANGE = 30.0;
	public static final double BAR_RANGE = 48.0;
	public static final double WAVE_RADIUS = 14.0;
	public static final int MAX_MINIONS = 4;
	public static final int STAGGER_TICKS = 100;
	public static final int DEATH_TICKS = 60;

	public static final byte MOVE_NONE = 0;
	public static final byte MOVE_WAVE = 1;
	public static final byte MOVE_LANCE = 2;
	public static final byte MOVE_SUMMON = 3;
	public static final byte MOVE_CURSE = 4;
	public static final byte MOVE_STRIKE = 5;
	public static final byte MOVE_CHAINS = 6;

	public static final int WAVE_WINDUP = 24;
	public static final int WAVE_RUN = 24;
	public static final int LANCE_WINDUP = 16;
	public static final int LANCE_RUN = 18;
	public static final int SUMMON_WINDUP = 30;
	public static final int CURSE_WINDUP = 20;
	public static final int STRIKE_WINDUP = 10;
	public static final int CHAINS_TICKS = 50;
	public static final int CHAINS_SNAP = 30;

	private static final EntityDataAccessor<Byte> DATA_PHASE = SynchedEntityData.defineId(AnkhatepBoss.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_BUSY = SynchedEntityData.defineId(AnkhatepBoss.class, EntityDataSerializers.BOOLEAN);
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.55f, 0.95f, 0.45f), 1.3f);
	private static final DustParticleOptions SAND = new DustParticleOptions(new Vector3f(0.93f, 0.8f, 0.5f), 1.8f);

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
	private final BossThreat threat = new BossThreat(this).range(48).holdWhile(() -> this.move != MOVE_NONE);
	private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.projecthero.ankhatep").withStyle(ChatFormatting.GOLD),
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private final List<UUID> minions = new ArrayList<>();
	private final List<BlockPos> jarSpots = new ArrayList<>();
	private final Set<UUID> struck = new HashSet<>();
	private BlockPos home;
	private boolean awake;
	private boolean cursing;
	private boolean jarsScanned;
	private boolean reported;
	private int stagger;

	byte move;
	int moveTicks;
	int waveCd = 80;
	int lanceCd = 50;
	int summonCd = 200;
	int strikeCd;
	private Vec3 lanceOrigin = Vec3.ZERO;
	private final List<Vec3> lanceDirs = new ArrayList<>();
	public int movesUsed;

	public AnkhatepBoss(EntityType<? extends AnkhatepBoss> type, Level level) {
		super(type, level);
		this.xpReward = 500;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.24)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ARMOR, 14.0)
				.add(Attributes.ARMOR_TOUGHNESS, 8.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.FOLLOW_RANGE, 48.0)
				.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, (byte) 1);
		builder.define(DATA_BUSY, false);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24f));
		// no HurtByTargetGoal: the shared threat table decides who he fights
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	// ---------------------------------------------------------------- state

	public int phase() {
		return entityData.get(DATA_PHASE);
	}

	/** Client + server: still chained (phase 1, or the chain-breaking clip not yet at its snap). */
	public boolean chained() {
		return phase() == 1;
	}

	public boolean isBusy() {
		return entityData.get(DATA_BUSY);
	}

	public BlockPos home() {
		return home;
	}

	public void setHome(BlockPos home) {
		this.home = home == null ? null : home.immutable();
	}

	public BossThreat threat() {
		return threat;
	}

	public byte move() {
		return move;
	}

	public boolean cursing() {
		return cursing;
	}

	public boolean staggered() {
		return stagger > 0;
	}

	public List<BlockPos> jarSpots() {
		return List.copyOf(jarSpots);
	}

	/** Canopic jars still standing on his jar spots. */
	public int jarsLeft() {
		int n = 0;
		for (BlockPos p : jarSpots) {
			if (level().getBlockState(p).is(TempleBlocks.CANOPIC_JAR)) {
				n++;
			}
		}
		return n;
	}

	private double reach() {
		return phase() == 1 ? CHAIN_REACH : FREE_REACH;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (move == MOVE_CHAINS && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return false;
		}
		if (stagger > 0) {
			amount *= 1.5f;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			threat.record(source, amount);
			if (!awake) {
				awaken(server);
			}
			if (phase() == 1 && isAlive() && getHealth() <= getMaxHealth() * 0.5f && move != MOVE_CHAINS) {
				startMove(MOVE_CHAINS);
			}
		}
		return hurt;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean canBeAffected(MobEffectInstance effect) {
		return !effect.is(MobEffects.WITHER) && !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
	}

	@Override
	public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (home == null) {
			home = blockPosition();
		}
		if (!jarsScanned) {
			scanJars(server);
		}
		threat.tick();
		bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (tickCount % 10 == 0) {
			refreshViewers(server);
			minions.removeIf(id -> !(server.getEntity(id) instanceof LivingEntity l) || !l.isAlive());
		}
		LivingEntity target = getTarget();
		if (!awake && target != null && target.isAlive() && distanceToSqr(target) < 20 * 20 && hasLineOfSight(target)) {
			awaken(server);
		}
		if (awake) {
			tickCurse(server);
		}
		waveCd = Math.max(0, waveCd - 1);
		lanceCd = Math.max(0, lanceCd - 1);
		summonCd = Math.max(0, summonCd - 1);
		strikeCd = Math.max(0, strikeCd - 1);
		if (stagger > 0) {
			stagger--;
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (tickCount % 5 == 0) {
				server.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.2, getZ(), 4, 0.4, 0.1, 0.4, 0.0);
			}
			syncBusy();
			return;
		}
		if (move != MOVE_NONE) {
			tickMove(server);
			syncBusy();
			return;
		}
		syncBusy();
		tickMovement(server, target);
		if (!awake || target == null || !target.isAlive()) {
			return;
		}
		double d = distanceTo(target);
		if (strikeCd == 0 && d < 3.6) {
			startMove(MOVE_STRIKE);
		} else if (waveCd == 0 && d < WAVE_RADIUS + 2) {
			startMove(MOVE_WAVE);
		} else if (summonCd == 0 && minions.size() < MAX_MINIONS) {
			startMove(MOVE_SUMMON);
		} else if (lanceCd == 0 && d < 24 && hasLineOfSight(target)) {
			startMove(MOVE_LANCE);
		}
	}

	/** Test seam: one server AI step now (tickCount advances too, as in a real tick). */
	public void stepForTest(int ticks) {
		for (int i = 0; i < ticks; i++) {
			tickCount++;
			customServerAiStep();
		}
	}

	/** Walks toward his target, never past his chains (phase 1) or his arena (phase 2). */
	private void tickMovement(ServerLevel level, LivingEntity target) {
		if (tickCount % 8 != 0) {
			return;
		}
		Vec3 h = Vec3.atBottomCenterOf(home);
		double fromHome = Math.sqrt(distanceToSqr(h));
		if (target == null || !target.isAlive()) {
			if (fromHome > 1.5) {
				getNavigation().moveTo(h.x, h.y, h.z, 0.9);
			}
			return;
		}
		Vec3 want = target.position();
		Vec3 off = want.subtract(h).multiply(1, 0, 1);
		double reach = reach() - 0.5;
		if (off.length() > reach) {
			want = h.add(off.normalize().scale(reach));
		}
		if (want.distanceToSqr(position()) > 2.5 * 2.5) {
			getNavigation().moveTo(want.x, h.y, want.z, phase() == 1 ? 0.8 : 1.1);
		} else {
			getNavigation().stop();
		}
		if (fromHome > reach() + 2) {
			getNavigation().moveTo(h.x, h.y, h.z, 1.0);
		}
		if (fromHome > reach() + 12) {
			moveTo(h.x, h.y, h.z, getYRot(), 0f); // pushed far away: back to the throne
		}
	}

	/** The fight begins: his first Curse. */
	public void awaken(ServerLevel level) {
		if (awake) {
			return;
		}
		awake = true;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.4f, 0.7f);
		for (ServerPlayer p : TempleClearing.playersNear(level, blockPosition(), 48)) {
			p.displayClientMessage(Component.translatable("projecthero.temple.ankhatep.awake").withStyle(ChatFormatting.GOLD), false);
		}
		startMove(MOVE_CURSE);
	}

	/** Finds the canopic jars round his home (retried every 5 s until some are found or the fight starts). */
	private void scanJars(ServerLevel level) {
		if (!jarSpots.isEmpty() || (tickCount % 100 != 1 && !awake)) {
			jarsScanned = !jarSpots.isEmpty();
			return;
		}
		for (BlockPos p : BlockPos.betweenClosed(home.offset(-16, -3, -16), home.offset(16, 4, 16))) {
			if (level.getBlockState(p).is(TempleBlocks.CANOPIC_JAR)) {
				jarSpots.add(p.immutable());
			}
		}
		jarsScanned = !jarSpots.isEmpty() || awake;
	}

	/** Test seam: forget the jar spots and look again now. */
	public int rescanJars() {
		jarSpots.clear();
		if (level() instanceof ServerLevel server && home != null) {
			for (BlockPos p : BlockPos.betweenClosed(home.offset(-16, -3, -16), home.offset(16, 4, 16))) {
				if (server.getBlockState(p).is(TempleBlocks.CANOPIC_JAR)) {
					jarSpots.add(p.immutable());
				}
			}
		}
		jarsScanned = true;
		return jarSpots.size();
	}

	private void tickCurse(ServerLevel level) {
		if (!cursing || tickCount % 20 != 0) {
			return;
		}
		if (jarsLeft() == 0) {
			liftCurse(level);
			return;
		}
		int amp = phase() >= 2 ? 1 : 0;
		int drained = 0;
		for (ServerPlayer p : TempleClearing.playersNear(level, blockPosition(), CURSE_RANGE)) {
			if (p.isCreative() || p.isSpectator() || !hasLineOfSight(p)) {
				continue; // only those in his hall (not behind its walls)
			}
			p.addEffect(new MobEffectInstance(TempleEffects.BOUND_CURSE, 80, amp, false, true, true), this);
			drained++;
			if (tickCount % 40 == 0) {
				level.sendParticles(GREEN, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.3, 0.5, 0.3, 0.0);
			}
		}
		if (drained > 0 && tickCount % 40 == 0 && getHealth() < getMaxHealth()) {
			heal(drained * (amp + 1));
			for (BlockPos j : jarSpots) {
				if (level.getBlockState(j).is(TempleBlocks.CANOPIC_JAR)) {
					level.sendParticles(ParticleTypes.SOUL, j.getX() + 0.5, j.getY() + 0.9, j.getZ() + 0.5, 2, 0.1, 0.2, 0.1, 0.02);
				}
			}
		}
	}

	/** Every jar is broken: the curse ends and he staggers. */
	public void liftCurse(ServerLevel level) {
		if (!cursing) {
			return;
		}
		cursing = false;
		stagger = STAGGER_TICKS;
		move = MOVE_NONE;
		triggerAnim("action", "stagger");
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 1.4f);
		level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 2, getZ(), 40, 0.8, 1.0, 0.8, 0.08);
		for (ServerPlayer p : TempleClearing.playersNear(level, blockPosition(), 64)) {
			p.removeEffect(TempleEffects.BOUND_CURSE);
			if (p.distanceToSqr(this) < 48 * 48) {
				p.displayClientMessage(Component.translatable("projecthero.temple.ankhatep.curse_lifted").withStyle(ChatFormatting.GREEN), false);
			}
		}
	}

	// ---------------------------------------------------------------- moves

	/** Starts a move (test seam too). */
	public void startMove(byte which) {
		if (!(level() instanceof ServerLevel level)) {
			return;
		}
		move = which;
		moveTicks = 0;
		movesUsed++;
		struck.clear();
		getNavigation().stop();
		String clip = switch (which) {
			case MOVE_WAVE -> "sand_wave";
			case MOVE_LANCE -> "lance";
			case MOVE_SUMMON -> "summon";
			case MOVE_CURSE -> "curse";
			case MOVE_STRIKE -> "strike";
			case MOVE_CHAINS -> "break_chains";
			default -> null;
		};
		if (clip != null) {
			triggerAnim("action", clip);
		}
		switch (which) {
			case MOVE_WAVE -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 2.0f, 0.5f);
			case MOVE_LANCE -> {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.6f, 0.6f);
				LivingEntity t = getTarget();
				Vec3 aim = t != null ? t.position().subtract(position()).multiply(1, 0, 1) : getLookAngle().multiply(1, 0, 1);
				aim = aim.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : aim.normalize();
				lanceOrigin = position();
				lanceDirs.clear();
				int lines = phase() >= 2 ? 5 : 3;
				for (int i = 0; i < lines; i++) {
					double a = (i - (lines - 1) / 2.0) * Math.toRadians(phase() >= 2 ? 16 : 20);
					lanceDirs.add(new Vec3(aim.x * Math.cos(a) - aim.z * Math.sin(a), 0, aim.x * Math.sin(a) + aim.z * Math.cos(a)));
				}
			}
			case MOVE_SUMMON -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.6f, 0.6f);
			case MOVE_CURSE -> level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.4f, 0.6f);
			case MOVE_CHAINS -> {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0f, 0.7f);
				for (ServerPlayer p : TempleClearing.playersNear(level, blockPosition(), 48)) {
					p.displayClientMessage(Component.translatable("projecthero.temple.ankhatep.chains").withStyle(ChatFormatting.GOLD), false);
				}
			}
			default -> {
			}
		}
		syncBusy();
	}

	private int cd(int base, int spread) {
		int v = base + random.nextInt(spread);
		return phase() >= 2 ? (int) (v * 0.7f) : v;
	}

	private void tickMove(ServerLevel level) {
		moveTicks++;
		getNavigation().stop();
		setDeltaMovement(0, getDeltaMovement().y, 0);
		LivingEntity t = getTarget();
		if (t != null) {
			getLookControl().setLookAt(t, 30f, 30f);
		}
		switch (move) {
			case MOVE_WAVE -> tickWave(level);
			case MOVE_LANCE -> tickLance(level);
			case MOVE_SUMMON -> {
				if (moveTicks % 3 == 0) {
					level.sendParticles(GREEN, getX(), getY() + 2.6, getZ(), 6, 0.5, 0.4, 0.5, 0.0);
				}
				if (moveTicks == SUMMON_WINDUP) {
					summon(level, phase() >= 2 ? 3 : 2);
				}
				if (moveTicks >= SUMMON_WINDUP + 10) {
					endMove();
					summonCd = cd(320, 120);
				}
			}
			case MOVE_CURSE -> {
				if (moveTicks % 2 == 0) {
					double a = moveTicks * 0.4;
					for (int k = 0; k < 3; k++) {
						double b = a + k * Math.PI * 2 / 3;
						level.sendParticles(GREEN, getX() + Math.cos(b) * 1.4, getY() + 0.3 + moveTicks * 0.12, getZ() + Math.sin(b) * 1.4, 1, 0, 0, 0, 0);
					}
				}
				if (moveTicks == CURSE_WINDUP) {
					cursing = jarsLeft() > 0;
					if (cursing) {
						for (ServerPlayer p : TempleClearing.playersNear(level, blockPosition(), CURSE_RANGE)) {
							p.displayClientMessage(Component.translatable("projecthero.temple.ankhatep.cursed").withStyle(ChatFormatting.DARK_GREEN), false);
						}
					}
					level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2, getZ(), 30, 2.0, 1.0, 2.0, 0.05);
				}
				if (moveTicks >= CURSE_WINDUP + 10) {
					endMove();
				}
			}
			case MOVE_STRIKE -> {
				if (moveTicks == STRIKE_WINDUP && t != null && t.isAlive() && distanceTo(t) < 4.2) {
					t.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
					Vec3 away = t.position().subtract(position()).multiply(1, 0, 1).normalize();
					t.push(away.x * 0.9, 0.35, away.z * 0.9);
					t.hurtMarked = true;
					level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.2f, 0.6f);
				}
				if (moveTicks >= STRIKE_WINDUP + 8) {
					endMove();
					strikeCd = cd(30, 20);
				}
			}
			case MOVE_CHAINS -> tickChains(level);
			default -> endMove();
		}
	}

	private void endMove() {
		move = MOVE_NONE;
		moveTicks = 0;
	}

	private void tickWave(ServerLevel level) {
		if (moveTicks < WAVE_WINDUP) {
			if (moveTicks % 2 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), getX(), getY() + 3.6, getZ(), 6,
						0.8, 0.3, 0.8, 0.0);
				double a = moveTicks * 0.5;
				level.sendParticles(SAND, getX() + Math.cos(a) * 1.8, getY() + 0.4, getZ() + Math.sin(a) * 1.8, 2, 0.1, 0.1, 0.1, 0);
			}
			return;
		}
		int k = moveTicks - WAVE_WINDUP;
		ring(level, k);
		if (phase() >= 2 && k >= 10) {
			ring(level, k - 10); // the second ring, 0.5 s behind the first
		}
		int end = WAVE_RUN + (phase() >= 2 ? 10 : 0);
		if (k >= end) {
			endMove();
			waveCd = cd(150, 60);
		}
		if (k == 0 || (phase() >= 2 && k == 10)) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.5f);
		}
	}

	/** Wave radius {@code k} ticks after it left. */
	public static double waveRadius(int k) {
		return 1.0 + k * (WAVE_RADIUS - 1.0) / WAVE_RUN;
	}

	private void ring(ServerLevel level, int k) {
		if (k < 0 || k > WAVE_RUN) {
			return;
		}
		double r = waveRadius(k);
		int n = (int) (r * 5);
		BlockParticleOption sand = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n;
			double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
			level.sendParticles(sand, x, getY() + 0.3, z, 1, 0.05, 0.15, 0.05, 0.05);
			if (i % 3 == 0) {
				level.sendParticles(SAND, x, getY() + 0.6, z, 1, 0.05, 0.2, 0.05, 0);
				level.sendParticles(ParticleTypes.POOF, x, getY() + 0.4, z, 1, 0.05, 0.1, 0.05, 0.01); // a white crest so the front reads on sand
			}
		}
		for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(r + 1, 2, r + 1),
				p -> enemy(p) && p.onGround() && !struck.contains(p.getUUID()))) {
			double d = Math.sqrt(p.distanceToSqr(getX(), p.getY(), getZ()));
			if (Math.abs(d - r) <= 0.8 && Math.abs(p.getY() - getY()) < 2.5) {
				struck.add(p.getUUID());
				p.hurt(damageSources().mobAttack(this), 9f);
				Vec3 away = p.position().subtract(position()).multiply(1, 0, 1).normalize();
				p.push(away.x * 1.1, 0.5, away.z * 1.1);
				p.hurtMarked = true;
				p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, 0), this);
			}
		}
		if (k == WAVE_RUN) {
			struck.clear(); // the second ring may hit again
		}
	}

	private void tickLance(ServerLevel level) {
		if (moveTicks < LANCE_WINDUP) {
			if (moveTicks % 2 == 0) {
				for (Vec3 d : lanceDirs) {
					Vec3 at = lanceOrigin.add(d.scale(1.6));
					level.sendParticles(SAND, at.x, at.y + 0.2, at.z, 2, 0.1, 0.05, 0.1, 0);
				}
			}
			return;
		}
		int step = moveTicks - LANCE_WINDUP + 2;
		BlockParticleOption sand = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
		for (Vec3 d : lanceDirs) {
			Vec3 at = lanceOrigin.add(d.scale(step));
			BlockPos feet = BlockPos.containing(at.x, lanceOrigin.y + 0.5, at.z);
			if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) {
				continue; // into a wall: that line is spent
			}
			level.sendParticles(sand, at.x, lanceOrigin.y + 0.4, at.z, 10, 0.25, 0.6, 0.25, 0.15);
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, lanceOrigin.y + 0.8, at.z, 1, 0.1, 0.3, 0.1, 0.01);
			for (Player p : level.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(at.x - 1.1, lanceOrigin.y - 1, at.z - 1.1, at.x + 1.1,
					lanceOrigin.y + 2.5, at.z + 1.1), p -> enemy(p) && !struck.contains(p.getUUID()))) {
				struck.add(p.getUUID());
				p.hurt(damageSources().mobAttack(this), 8f);
				p.push(0, 0.75, 0);
				p.hurtMarked = true;
			}
		}
		if (step % 3 == 0) {
			level.playSound(null, lanceOrigin.x + lanceDirs.get(0).x * step, lanceOrigin.y, lanceOrigin.z + lanceDirs.get(0).z * step,
					SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
		}
		if (moveTicks >= LANCE_WINDUP + LANCE_RUN) {
			endMove();
			lanceCd = cd(90, 50);
		}
	}

	private void tickChains(ServerLevel level) {
		if (moveTicks < CHAINS_SNAP && moveTicks % 3 == 0) {
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.4, getZ(), 6, 0.8, 0.8, 0.8, 0.02);
		}
		if (moveTicks == CHAINS_SNAP) {
			entityData.set(DATA_PHASE, (byte) 2);
			var speed = getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.setBaseValue(0.3);
			}
			bar.setColor(BossEvent.BossBarColor.RED);
			bar.setName(Component.translatable("projecthero.temple.ankhatep.unbound_bar").withStyle(ChatFormatting.RED));
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.HOSTILE, 3.0f, 0.5f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_DESTROY, SoundSource.HOSTILE, 2.0f, 0.6f);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHAIN.defaultBlockState()), getX(), getY() + 1.2, getZ(), 60,
					1.0, 0.8, 1.0, 0.3);
			level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
			for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(8), AnkhatepBoss::enemy)) {
				p.hurt(damageSources().mobAttack(this), 6f);
				Vec3 away = p.position().subtract(position()).multiply(1, 0, 1).normalize();
				p.push(away.x * 1.6, 0.6, away.z * 1.6);
				p.hurtMarked = true;
			}
			// two jars re-form (if any were broken) and the curse returns, stronger
			int reformed = 0;
			for (BlockPos j : jarSpots) {
				if (reformed >= 2) {
					break;
				}
				BlockState s = level.getBlockState(j);
				if (s.isAir() || s.canBeReplaced()) {
					level.setBlock(j, TempleBlocks.CANOPIC_JAR.defaultBlockState().setValue(CanopicJarBlock.HEAD, (j.getX() + j.getZ()) & 3), 3);
					level.sendParticles(ParticleTypes.SOUL, j.getX() + 0.5, j.getY() + 0.6, j.getZ() + 0.5, 20, 0.2, 0.4, 0.2, 0.05);
					reformed++;
				}
			}
			if (jarsLeft() > 0) {
				cursing = true;
			}
			waveCd = Math.min(waveCd, 30);
		}
		if (moveTicks >= CHAINS_TICKS) {
			endMove();
		}
	}

	/** Raises up to {@code count} temple guardians round him (never past {@link #MAX_MINIONS}). Returns how many. */
	public int summon(ServerLevel level, int count) {
		int raised = 0;
		for (int i = 0; i < count && minions.size() < MAX_MINIONS; i++) {
			BlockPos spot = TempleGuards.findSpot(level, blockPosition(), 3, 7, random);
			if (spot == null) {
				continue;
			}
			Mob m = TempleGuards.spawn(level, TempleGuards.Role.GUARDIAN, spot, random.nextFloat() * 360f, 20, null, false);
			if (m != null) {
				TempleGuards.sandRise(level, spot);
				minions.add(m.getUUID());
				if (getTarget() != null) {
					m.setTarget(getTarget());
				}
				raised++;
			}
		}
		return raised;
	}

	public int liveMinions() {
		return minions.size();
	}

	static boolean enemy(Player p) {
		return p.isAlive() && !p.isCreative() && !p.isSpectator();
	}

	private void syncBusy() {
		boolean busy = move != MOVE_NONE || stagger > 0 || isDeadOrDying();
		if (entityData.get(DATA_BUSY) != busy) {
			entityData.set(DATA_BUSY, busy);
		}
	}

	// ---------------------------------------------------------------- boss bar

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

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		viewers.remove(player);
		bar.removePlayer(player);
	}

	// ---------------------------------------------------------------- death

	@Override
	public void die(DamageSource source) {
		if (dead || isRemoved()) {
			return;
		}
		move = MOVE_NONE;
		super.die(source);
		getNavigation().stop();
		triggerAnim("action", "death");
		syncBusy();
		bar.removeAllPlayers();
		viewers.clear();
		if (level() instanceof ServerLevel server) {
			cursing = false;
			for (ServerPlayer p : TempleClearing.playersNear(server, blockPosition(), 64)) {
				p.removeEffect(TempleEffects.BOUND_CURSE);
			}
			for (UUID id : minions) {
				if (server.getEntity(id) instanceof Mob m && m.isAlive()) {
					TempleGuards.sandRise(server, m.blockPosition());
					m.discard();
				}
			}
			minions.clear();
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0f, 0.6f);
			if (!reported) {
				reported = true;
				TempleClearing.bossDefeated(server, home != null ? home : blockPosition());
			}
		}
	}

	@Override
	protected void tickDeath() {
		++deathTime;
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (deathTime % 3 == 0) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), getX(), getY() + 1.6, getZ(), 8,
					0.5, 1.0, 0.5, 0.0);
		}
		if (deathTime >= DEATH_TICKS && !isRemoved()) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), getX(), getY() + 1.2, getZ(), 160,
					0.6, 1.2, 0.6, 0.2);
			server.sendParticles(ParticleTypes.SOUL, getX(), getY() + 2, getZ(), 30, 0.6, 1.0, 0.6, 0.06);
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.SAND_BREAK, SoundSource.HOSTILE, 2.5f, 0.4f);
			level().broadcastEntityEvent(this, (byte) 60);
			remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		bar.removeAllPlayers();
		viewers.clear();
		super.remove(reason);
	}

	// ---------------------------------------------------------------- sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.HUSK_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.HUSK_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.HUSK_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.5f;
	}

	@Override
	protected float getSoundVolume() {
		return 2.0f;
	}

	// ---------------------------------------------------------------- save

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (home != null) {
			tag.put("TempleHome", NbtUtils.writeBlockPos(home));
		}
		tag.putByte("KingPhase", (byte) phase());
		tag.putBoolean("Awake", awake);
		tag.putBoolean("Cursing", cursing);
		long[] spots = new long[jarSpots.size()];
		for (int i = 0; i < spots.length; i++) {
			spots[i] = jarSpots.get(i).asLong();
		}
		tag.putLongArray("JarSpots", spots);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		NbtUtils.readBlockPos(tag, "TempleHome").ifPresent(this::setHome);
		int phase = Math.max(1, Math.min(2, tag.getByte("KingPhase")));
		entityData.set(DATA_PHASE, (byte) phase);
		if (phase == 2) {
			var speed = getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.setBaseValue(0.3);
			}
			bar.setColor(BossEvent.BossBarColor.RED);
			bar.setName(Component.translatable("projecthero.temple.ankhatep.unbound_bar").withStyle(ChatFormatting.RED));
		}
		awake = tag.getBoolean("Awake");
		cursing = tag.getBoolean("Cursing");
		jarSpots.clear();
		for (long l : tag.getLongArray("JarSpots")) {
			jarSpots.add(BlockPos.of(l));
		}
		jarsScanned = !jarSpots.isEmpty();
	}

	// ---------------------------------------------------------------- GeckoLib

	public static final String[] ACTION_CLIPS = { "sand_wave", "lance", "summon", "curse", "strike", "break_chains", "stagger", "death" };

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		AnimationController<AnkhatepBoss> action = new AnimationController<>(this, "action", 3, state -> PlayState.STOP);
		for (String name : ACTION_CLIPS) {
			action.triggerableAnim(name, RawAnimation.begin().thenPlay("animation.ankhatep." + name));
		}
		controllers.add(action);
		controllers.add(new AnimationController<>(this, "main", 5, this::mainPredicate));
	}

	private PlayState mainPredicate(AnimationState<AnkhatepBoss> state) {
		if (isDeadOrDying() || entityData.get(DATA_BUSY)) {
			return PlayState.STOP;
		}
		if (state.getLimbSwingAmount() > 0.04f) {
			return state.setAndContinue(RawAnimation.begin().thenLoop(chained() ? "animation.ankhatep.walk_bound" : "animation.ankhatep.walk"));
		}
		return state.setAndContinue(RawAnimation.begin().thenLoop(chained() ? "animation.ankhatep.idle_bound" : "animation.ankhatep.idle"));
	}
}
