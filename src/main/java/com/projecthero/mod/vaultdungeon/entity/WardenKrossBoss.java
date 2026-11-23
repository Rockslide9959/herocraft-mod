package com.projecthero.mod.vaultdungeon.entity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;

import com.projecthero.mod.beamclash.BeamClash;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.gear.GearItems;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.syndicate.entity.SyndicateEnforcer;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;
import com.projecthero.mod.vaultdungeon.VaultClearing;
import com.projecthero.mod.vaultdungeon.VaultGuards;

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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5: <b>Warden Kross</b>, master of The Vault -- the Tier 4 dungeon's boss. A huge man (x1.7) in a charcoal
 * greatcoat with a stun baton and a riot shield, {@link #MAX_HEALTH} health, 14 armour. His riot shield cuts frontal
 * hits by {@link #SHIELD_CUT} (by {@link #BRACE_CUT} while he braces or charges) -- flank him.
 * <ul>
 * <li><b>Riot Shield Charge</b> (5-16 blocks, in sight): braces 0.8 s, then charges a straight line for a second;
 * whoever he hits takes 12 (14 enraged), is thrown and stunned (Slowness III 1.5 s). Into a wall: he is dazed
 * 1.8 s and takes 50% more damage.</li>
 * <li><b>Stun Baton Shockwave</b> (target within reach): baton raised for a second while a spark ring marks the reach
 * (7 blocks, 8.5 enraged), then a slam: 10 (13) to everyone on the ground inside, Slowness IV + Weakness 2 s. Jump it.</li>
 * <li><b>Taser Line</b> (6-18 blocks, in sight): half a second of aim, then a crackling taser line latched to the target
 * for 1.5 s (2 s enraged): 2.5 every quarter second, Slowness II, reeled towards him. Breaking line of sight (or
 * getting 20 blocks away) snaps it. A beam: it reports to {@link BeamClash} every live tick.</li>
 * <li><b>Lockdown</b> (enraged only): sirens for 1.5 s, then blast shutters slam over the arena's doorways and two riot
 * wardens and a riot gunner (+1 warden per extra player, max 5) drop in. Lasts {@link #LOCKDOWN_TICKS}; again every
 * 45 s.</li>
 * </ul>
 * <b>Phase change</b> at half health: he roars, turns faster, his cooldowns shorten by a third and the first Lockdown
 * starts at once. v0.17.7 <b>phase 2</b> adds the <b>Riot Gas Volley</b> (three canisters, choking clouds that hurt,
 * blind and slow) and the <b>Breach Slam</b> (a leap onto your spot and a shockwave on landing).
 * Shared {@link BossThreat} threat table (switches to whoever hurts him most), boss bar within
 * {@link #BAR_RANGE}, leashed to his arena ({@link #home}). Dying clears the dungeon ({@link VaultClearing}).
 */
public class WardenKrossBoss extends SyndicateEnforcer {
	public static final float SCALE = 1.7f;
	public static final double MAX_HEALTH = 900.0;
	public static final float SHIELD_CUT = 0.25f;
	public static final float BRACE_CUT = 0.7f;
	public static final double BAR_RANGE = 48.0;
	public static final double LEASH = 18.0;
	public static final double TETHER = 40.0;
	public static final int LOCKDOWN_TICKS = 600;
	public static final int LOCKDOWN_REPEAT = 900;

	static final byte MOVE_NONE = 0;
	public static final byte MOVE_CHARGE = 1;
	public static final byte MOVE_SHOCK = 2;
	public static final byte MOVE_TASER = 3;
	public static final byte MOVE_LOCKDOWN = 4;
	/** v0.17.7 phase 2: Riot Gas Volley. */
	public static final byte MOVE_GAS = 5;
	/** v0.17.7 phase 2: Breach Slam. */
	public static final byte MOVE_SLAM = 6;

	static final int GAS_WINDUP = 14;
	static final int GAS_FLIGHT = 14;
	public static final int GAS_TICKS = 160;
	public static final double GAS_RADIUS = 2.8;
	static final int SLAM_CROUCH = 14;
	static final int SLAM_AIR_MAX = 34;
	public static final double SLAM_RADIUS = 6.0;

	static final int CHARGE_BRACE = 16;
	static final int CHARGE_RUN = 20;
	static final int SHOCK_WINDUP = 20;
	static final int TASER_AIM = 10;
	static final int LOCKDOWN_WINDUP = 30;

	private static final EntityDataAccessor<Integer> DATA_TASER = SynchedEntityData.defineId(WardenKrossBoss.class, EntityDataSerializers.INT);
	private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1f, 0.1f, 0.08f), 1.6f);
	private static final DustParticleOptions GAS = new DustParticleOptions(new Vector3f(0.62f, 0.78f, 0.32f), 1.8f);

	private final BossThreat threat = new BossThreat(this).range(48).holdWhile(() -> this.move != MOVE_NONE);
	private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.projecthero.warden_kross").withStyle(ChatFormatting.RED),
			BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private BlockPos home;
	private boolean enraged;
	private boolean reported;

	byte move;
	int moveTicks;
	int chargeCd = 60;
	int shockCd = 80;
	int taserCd = 40;
	int lockdownCd;
	int gap;
	int lockdownTicks;
	int krossDazed;
	private Vec3 chargeDir = Vec3.ZERO;
	private final Set<LivingEntity> struck = new HashSet<>();
	private LivingEntity taserVictim;
	private int movesUsed;
	// v0.17.7 phase 2
	int gasCd = 30;
	int slamCd = 70;
	private final java.util.List<Vec3> gasTargets = new java.util.ArrayList<>();
	/** Live gas clouds: {x, y, z, ticksLeft} (transient, owned by this boss). */
	private final java.util.List<double[]> gasClouds = new java.util.ArrayList<>();
	private Vec3 slamTarget;
	private boolean slamAirborne;
	private int slamAir;

	public WardenKrossBoss(EntityType<? extends WardenKrossBoss> type, Level level) {
		super(type, level);
		this.xpReward = 250;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.ATTACK_DAMAGE, 12.0)
				.add(Attributes.ATTACK_KNOCKBACK, 1.4)
				.add(Attributes.ARMOR, 14.0)
				.add(Attributes.ARMOR_TOUGHNESS, 6.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TASER, -1);
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.VAULT_WARDEN_KROSS;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(SyndicateSkin.VAULT_WARDEN_KROSS);
		arm(new ItemStack(GearItems.ALLOY_BATON != null ? GearItems.ALLOY_BATON : Items.IRON_SWORD));
		setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		return out;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		// the shared threat table decides who he hits (BossThreat); vanilla's hurt-by retarget would fight it
		targetSelector.removeAllGoals(g -> g instanceof HurtByTargetGoal);
	}

	@Override
	protected void registerCombatGoals() {
		goalSelector.addGoal(1, new MovesGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
	}

	// ---------------------------------------------------------------- state

	public BlockPos home() {
		return home;
	}

	public void setHome(BlockPos home) {
		this.home = home == null ? null : home.immutable();
		if (home != null) {
			restrictTo(home, (int) LEASH);
		}
	}

	public BossThreat threat() {
		return threat;
	}

	public ServerBossEvent bar() {
		return bar;
	}

	public boolean enraged() {
		return enraged;
	}

	public boolean inLockdown() {
		return lockdownTicks > 0;
	}

	public byte move() {
		return move;
	}

	/** How many moves he has started (test / harness seam). */
	public int movesUsed() {
		return movesUsed;
	}

	/** Client + server: the entity id of whoever the taser line is latched to, or -1. */
	public int taserTargetId() {
		return entityData.get(DATA_TASER);
	}

	/** Where the taser line leaves him: his right hand, at {@code partialTick}. */
	public Vec3 taserFrom(float partialTick) {
		Vec3 at = new Vec3(Mth.lerp(partialTick, xo, getX()), Mth.lerp(partialTick, yo, getY()), Mth.lerp(partialTick, zo, getZ()));
		float yaw = Mth.lerp(partialTick, yBodyRotO, yBodyRot) * Mth.DEG_TO_RAD;
		Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
		Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
		float s = getScale();
		return at.add(0, 1.25 * s, 0).add(fwd.scale(0.55 * s)).add(right.scale(-0.35 * s));
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide()) {
			if (krossDazed > 0) {
				amount *= 1.5f;
			} else if (!getOffhandItem().isEmpty() && RiotWarden.fromFront(this, source)) {
				boolean braced = move == MOVE_CHARGE;
				amount *= 1f - (braced ? BRACE_CUT : SHIELD_CUT);
				level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, braced ? 0.6f : 0.8f);
			}
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			threat.record(source, amount);
			if (!enraged && isAlive() && getHealth() <= getMaxHealth() * 0.5f) {
				enrage(server);
			}
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (krossDazed > 0) {
			return false;
		}
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity l) {
			l.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
		}
		return hit;
	}

	/** Half health: the phase change. */
	void enrage(ServerLevel level) {
		enraged = true;
		var speed = getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.setBaseValue(0.31);
		}
		bar.setColor(BossEvent.BossBarColor.PURPLE);
		bar.setName(Component.translatable("projecthero.vault.kross.enraged_bar").withStyle(ChatFormatting.DARK_RED));
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.6f);
		level.sendParticles(RED, getX(), getY() + 1.5, getZ(), 60, 1.2, 1.4, 1.2, 0.1);
		for (ServerPlayer p : VaultClearing.playersNear(level, blockPosition(), 48)) {
			p.displayClientMessage(Component.translatable("projecthero.vault.kross.enraged").withStyle(ChatFormatting.RED), false);
		}
		lockdownCd = 0;
		chargeCd = Math.min(chargeCd, 40);
		// v0.17.7: phase 2 -- the gas volley and the breach slam join his kit
		gasCd = 60;
		slamCd = 120;
		for (ServerPlayer p : VaultClearing.playersNear(level, blockPosition(), 48)) {
			p.displayClientMessage(Component.translatable("projecthero.vault.kross.phase2").withStyle(ChatFormatting.GOLD), true);
		}
	}

	/** Phase 2 test / harness seam: the live gas clouds. */
	public int gasClouds() {
		return gasClouds.size();
	}

	/** Phase 2: a gas cloud now at {@code at} (test seam; the volley lands three). */
	public void addGasCloud(Vec3 at) {
		gasClouds.add(new double[] { at.x, at.y, at.z, GAS_TICKS });
	}

	/** Ticks the gas clouds: every 10 ticks, 2 damage + Blindness + Slowness to everyone inside but his own men. */
	private void tickGas(ServerLevel level) {
		if (gasClouds.isEmpty()) {
			return;
		}
		java.util.Iterator<double[]> it = gasClouds.iterator();
		while (it.hasNext()) {
			double[] c = it.next();
			c[3]--;
			if (tickCount % 3 == 0) {
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, c[0], c[1] + 0.4, c[2], 3, GAS_RADIUS * 0.45, 0.35, GAS_RADIUS * 0.45, 0.004);
				level.sendParticles(GAS, c[0], c[1] + 0.6, c[2], 6, GAS_RADIUS * 0.5, 0.5, GAS_RADIUS * 0.5, 0.0);
			}
			if (((int) c[3]) % 10 == 0) {
				AABB box = new AABB(c[0] - GAS_RADIUS, c[1] - 0.5, c[2] - GAS_RADIUS, c[0] + GAS_RADIUS, c[1] + 2.5, c[2] + GAS_RADIUS);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> victim(e, this))) {
					double dx = e.getX() - c[0];
					double dz = e.getZ() - c[2];
					if (dx * dx + dz * dz > GAS_RADIUS * GAS_RADIUS) {
						continue;
					}
					e.hurt(damageSources().indirectMagic(this, this), 2f);
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), this);
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), this);
				}
			}
			if (c[3] <= 0) {
				it.remove();
			}
		}
	}

	private int cd(int base, int spread) {
		int v = base + random.nextInt(spread);
		return enraged ? (int) (v * 0.65f) : v;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		threat.tick();
		if (chargeCd > 0) {
			chargeCd--;
		}
		if (shockCd > 0) {
			shockCd--;
		}
		if (taserCd > 0) {
			taserCd--;
		}
		if (lockdownCd > 0 && lockdownTicks == 0) {
			lockdownCd--;
		}
		if (gap > 0) {
			gap--;
		}
		if (gasCd > 0) {
			gasCd--;
		}
		if (slamCd > 0) {
			slamCd--;
		}
		tickGas(server);
		if (krossDazed > 0) {
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (tickCount % 6 == 0) {
				server.sendParticles(ParticleTypes.CRIT, getX(), getEyeY() + 0.4, getZ(), 3, 0.4, 0.1, 0.4, 0.0);
			}
			if (--krossDazed == 0) {
				setAction(ACTION_NONE);
			}
		}
		if (lockdownTicks > 0) {
			if (tickCount % 40 == 0) {
				server.playSound(null, getX(), getY(), getZ(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.5f, 0.5f);
			}
			if (--lockdownTicks == 0) {
				endLockdown(server);
			}
		}
		if (enraged && tickCount % 10 == 0) {
			server.sendParticles(RED, getX(), getY() + 1.2 * SCALE, getZ(), 2, 0.4, 0.6, 0.4, 0.0);
		}
		bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (tickCount % 10 == 0) {
			refreshViewers(server);
		}
		if (home != null && tickCount % 40 == 0) {
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
		double d = Math.sqrt(distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5));
		LivingEntity t = getTarget();
		boolean engaged = t != null && t.isAlive() && t.distanceToSqr(this) < 14 * 14;
		if (d > TETHER) {
			moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, getYRot(), 0f);
			getNavigation().stop();
			setTarget(null);
			threat.clear();
		} else if (d > LEASH && !engaged) {
			getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
		}
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel server) {
			bar.removeAllPlayers();
			viewers.clear();
			setTaser(null);
			if (!reported) {
				reported = true;
				if (lockdownTicks > 0) {
					lockdownTicks = 0;
				}
				VaultClearing.lift(server, home != null ? home : blockPosition());
				if (home != null) {
					VaultClearing.bossDefeated(server, home);
				}
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

	private static boolean victim(LivingEntity e, LivingEntity self) {
		return e != self && e.isAlive() && !(e instanceof SyndicateCriminal) && !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	private void begin(byte which) {
		move = which;
		moveTicks = 0;
		movesUsed++;
		getNavigation().stop();
	}

	/** Starts the Riot Shield Charge now (test / harness hook too). */
	public void startCharge() {
		begin(MOVE_CHARGE);
		struck.clear();
		LivingEntity t = getTarget();
		Vec3 to = t != null ? t.position().subtract(position()) : getViewVector(1f);
		chargeDir = new Vec3(to.x, 0, to.z).lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : new Vec3(to.x, 0, to.z).normalize();
		setAction(ACTION_WINDUP);
	}

	/** Starts the Stun Baton Shockwave now. */
	public void startShock() {
		begin(MOVE_SHOCK);
		setAction(ACTION_SLAM);
	}

	/** Starts the Taser Line now (on the current target). */
	public void startTaser() {
		begin(MOVE_TASER);
		taserVictim = getTarget();
	}

	/** Starts a Lockdown now. */
	public void startLockdown() {
		begin(MOVE_LOCKDOWN);
	}

	/** Phase 2: starts the Riot Gas Volley now -- three canisters at the target and either side of it. */
	public void startGas() {
		begin(MOVE_GAS);
		gasTargets.clear();
		LivingEntity t = getTarget();
		Vec3 c = t != null ? t.position() : position().add(getViewVector(1f).multiply(6, 0, 6));
		Vec3 to = c.subtract(position());
		Vec3 side = new Vec3(-to.z, 0, to.x);
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		gasTargets.add(c);
		gasTargets.add(c.add(side.scale(3.5)));
		gasTargets.add(c.subtract(side.scale(3.5)));
		setAction(ACTION_WINDUP);
	}

	/** Phase 2: starts the Breach Slam now -- a crouch, a leap onto the target's spot, a crater-wide shockwave. */
	public void startSlam() {
		begin(MOVE_SLAM);
		LivingEntity t = getTarget();
		slamTarget = t != null ? t.position() : position().add(getViewVector(1f).multiply(5, 0, 5));
		slamAirborne = false;
		slamAir = 0;
		setAction(ACTION_WINDUP);
	}

	public double shockRadius() {
		return enraged ? 8.5 : 7.0;
	}

	private void setTaser(LivingEntity e) {
		int id = e == null ? -1 : e.getId();
		if (entityData.get(DATA_TASER) != id) {
			entityData.set(DATA_TASER, id);
		}
	}

	void endMove() {
		switch (move) {
			case MOVE_CHARGE -> chargeCd = cd(120, 40);
			case MOVE_SHOCK -> shockCd = cd(150, 60);
			case MOVE_TASER -> taserCd = cd(130, 40);
			case MOVE_GAS -> gasCd = cd(240, 60);
			case MOVE_SLAM -> slamCd = cd(220, 60);
			case MOVE_LOCKDOWN -> {
			}
			default -> {
			}
		}
		move = MOVE_NONE;
		moveTicks = 0;
		gap = enraged ? 16 : 25;
		setTaser(null);
		taserVictim = null;
		if (krossDazed <= 0) {
			setAction(ACTION_NONE);
		}
	}

	void tickMove(ServerLevel level) {
		moveTicks++;
		LivingEntity t = getTarget();
		switch (move) {
			case MOVE_CHARGE -> tickCharge(level, t);
			case MOVE_SHOCK -> tickShock(level, t);
			case MOVE_TASER -> tickTaser(level);
			case MOVE_LOCKDOWN -> tickLockdown(level);
			case MOVE_GAS -> tickGasVolley(level);
			case MOVE_SLAM -> tickSlam(level);
			default -> endMove();
		}
	}

	private void tickGasVolley(ServerLevel level) {
		getNavigation().stop();
		if (!gasTargets.isEmpty()) {
			getLookControl().setLookAt(gasTargets.get(0).x, gasTargets.get(0).y + 1, gasTargets.get(0).z);
		}
		Vec3 hand = position().add(0, 2.4, 0);
		if (moveTicks < GAS_WINDUP) {
			if (moveTicks % 4 == 0) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1.0f, 0.6f);
				level.sendParticles(GAS, hand.x, hand.y, hand.z, 4, 0.2, 0.2, 0.2, 0.0);
			}
			return;
		}
		if (moveTicks == GAS_WINDUP) {
			setAction(ACTION_SLAM);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_THROW, SoundSource.HOSTILE, 1.6f, 0.5f);
		}
		int k = moveTicks - GAS_WINDUP;
		if (k < GAS_FLIGHT) {
			double f = k / (double) GAS_FLIGHT;
			for (Vec3 t : gasTargets) {
				Vec3 p = hand.lerp(t.add(0, 0.3, 0), f).add(0, Math.sin(f * Math.PI) * 3.0, 0);
				level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.03, 0.03, 0.03, 0.0);
				level.sendParticles(GAS, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
			}
			return;
		}
		for (Vec3 t : gasTargets) {
			addGasCloud(t);
			level.playSound(null, t.x, t.y, t.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.4f, 0.6f);
			level.playSound(null, t.x, t.y, t.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 1.0f, 0.5f);
			level.sendParticles(ParticleTypes.CLOUD, t.x, t.y + 0.5, t.z, 30, 1.2, 0.4, 1.2, 0.05);
		}
		gasTargets.clear();
		endMove();
	}

	private void tickSlam(ServerLevel level) {
		if (moveTicks <= SLAM_CROUCH) {
			getNavigation().stop();
			LivingEntity t = getTarget();
			if (t != null) {
				getLookControl().setLookAt(t, 40f, 40f);
				slamTarget = t.position();
			}
			if (moveTicks % 3 == 0) {
				level.sendParticles(RED, getX(), getY() + 0.2, getZ(), 8, 0.8, 0.1, 0.8, 0.0);
			}
			if (moveTicks == SLAM_CROUCH) {
				// the leap: a ballistic arc landing on the target's spot (~18 ticks in the air)
				Vec3 to = slamTarget.subtract(position());
				double flight = 18.0;
				double g = 0.08;
				double vx = to.x / flight;
				double vz = to.z / flight;
				double vy = (to.y + 0.5 * g * flight * flight) / flight;
				double h = Math.sqrt(vx * vx + vz * vz);
				if (h > 1.2) { // never more than ~20 blocks
					vx *= 1.2 / h;
					vz *= 1.2 / h;
				}
				setDeltaMovement(vx, Math.min(1.6, vy), vz);
				hasImpulse = true;
				hurtMarked = true;
				setAction(ACTION_CHARGE);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.8f);
				level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.4, 0.1, 0.4, 0.0);
			}
			return;
		}
		slamAir++;
		if (!onGround()) {
			slamAirborne = true;
			if (slamAir % 2 == 0) {
				level.sendParticles(RED, getX(), getY() + 1.0, getZ(), 4, 0.5, 0.6, 0.5, 0.0);
			}
		}
		boolean landed = slamAirborne && onGround() && slamAir > 3;
		if (landed || slamAir >= SLAM_AIR_MAX) {
			slamImpact(level);
			setDeltaMovement(0, getDeltaMovement().y, 0);
			endMove();
		}
	}

	/** The Breach Slam lands: a shockwave of {@link #SLAM_RADIUS}. Returns how many it hit. */
	public int slamImpact(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.7f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.4f, 0.5f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.2, getZ(), 1, 0, 0, 0, 0);
		var floor = level.getBlockState(blockPosition().below());
		for (int ring = 1; ring <= (int) SLAM_RADIUS; ring++) {
			int n = 7 * ring;
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor.isAir() ? net.minecraft.world.level.block.Blocks.STONE.defaultBlockState() : floor),
						getX() + Math.cos(a) * ring, getY() + 0.15, getZ() + Math.sin(a) * ring, 3, 0.1, 0.15, 0.1, 0.15);
			}
			for (int i = 0; i < n / 2; i++) {
				double a = i * Math.PI * 4 / n;
				level.sendParticles(RED, getX() + Math.cos(a) * ring, getY() + 0.3, getZ() + Math.sin(a) * ring, 1, 0, 0.05, 0, 0.0);
			}
		}
		int hit = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SLAM_RADIUS, 2.0, SLAM_RADIUS), e -> victim(e, this))) {
			double d = Math.sqrt(e.distanceToSqr(this));
			if (d > SLAM_RADIUS) {
				continue;
			}
			float dmg = (float) Math.max(6.0, 16.0 * (1.0 - d / (SLAM_RADIUS + 1.0)));
			e.hurt(damageSources().mobAttack(this), dmg);
			Vec3 out = e.position().subtract(position());
			Vec3 h = new Vec3(out.x, 0, out.z).lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(out.x, 0, out.z).normalize();
			e.push(h.x * 0.9, 0.75, h.z * 0.9);
			e.hurtMarked = true;
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
			hit++;
		}
		return hit;
	}

	private void tickCharge(ServerLevel level, LivingEntity t) {
		if (moveTicks <= CHARGE_BRACE) {
			getNavigation().stop();
			if (t != null) {
				getLookControl().setLookAt(t, 40f, 40f);
				Vec3 to = t.position().subtract(position());
				if (to.x * to.x + to.z * to.z > 1.0e-4) {
					chargeDir = new Vec3(to.x, 0, to.z).normalize();
				}
			}
			if (moveTicks % 4 == 0) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(blockPosition().below())), getX(), getY() + 0.1,
						getZ(), 10, 0.5, 0.05, 0.5, 0.1);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_STEP, SoundSource.HOSTILE, 1.0f, 0.7f);
			}
			if (moveTicks == CHARGE_BRACE) {
				setAction(ACTION_CHARGE);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.2f, 1.2f);
			}
			return;
		}
		setYRot((float) Math.toDegrees(Math.atan2(-chargeDir.x, chargeDir.z)));
		setYBodyRot(getYRot());
		setYHeadRot(getYRot());
		Vec3 v = chargeDir.scale(0.9);
		setDeltaMovement(v.x, getDeltaMovement().y, v.z);
		float dmg = enraged ? 14f : 12f;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.7), e -> victim(e, this))) {
			if (struck.add(e)) {
				e.hurt(damageSources().mobAttack(this), dmg);
				e.push(chargeDir.x * 1.6, 0.55, chargeDir.z * 1.6);
				e.hurtMarked = true;
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2), this);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.4f, 0.5f);
				level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.4, 0.3, 0.3);
			}
		}
		if (horizontalCollision && moveTicks > CHARGE_BRACE + 2) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.4f, 0.6f);
			level.sendParticles(ParticleTypes.CRIT, getX(), getEyeY(), getZ(), 16, 0.4, 0.4, 0.4, 0.2);
			setDeltaMovement(0, getDeltaMovement().y, 0);
			krossDazed = 36;
			setAction(ACTION_DAZED);
			endMove();
			return;
		}
		if (moveTicks >= CHARGE_BRACE + CHARGE_RUN) {
			setDeltaMovement(0, getDeltaMovement().y, 0);
			endMove();
		}
	}

	private void tickShock(ServerLevel level, LivingEntity t) {
		getNavigation().stop();
		if (t != null) {
			getLookControl().setLookAt(t, 40f, 40f);
		}
		double r = shockRadius();
		if (moveTicks < SHOCK_WINDUP && moveTicks % 3 == 0) {
			int n = (int) (r * 5);
			for (int i = 0; i < n; i++) {
				double a = i * Math.PI * 2 / n + moveTicks * 0.05;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * r, getY() + 0.15, getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0.0);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 2.6 * SCALE / 1.7, getZ(), 6, 0.2, 0.3, 0.2, 0.15);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.BEEHIVE_WORK, SoundSource.HOSTILE, 1.5f, 1.8f);
		}
		if (moveTicks == SHOCK_WINDUP) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.2f, 1.4f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.3f);
			for (int ring = 1; ring <= (int) r; ring++) {
				int n = 6 * ring;
				for (int i = 0; i < n; i++) {
					double a = i * Math.PI * 2 / n;
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX() + Math.cos(a) * ring, getY() + 0.2, getZ() + Math.sin(a) * ring, 2, 0.1, 0.1, 0.1, 0.2);
				}
			}
			float dmg = enraged ? 13f : 10f;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 1.5, r), e -> victim(e, this) && e.onGround())) {
				if (e.distanceToSqr(this) > r * r) {
					continue;
				}
				e.hurt(damageSources().mobAttack(this), dmg);
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3), this);
				e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), this);
				Vec3 out = e.position().subtract(position());
				Vec3 h = new Vec3(out.x, 0, out.z).lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : new Vec3(out.x, 0, out.z).normalize();
				e.push(h.x * 0.6, 0.45, h.z * 0.6);
				e.hurtMarked = true;
			}
		}
		if (moveTicks >= SHOCK_WINDUP + 10) {
			endMove();
		}
	}

	private void tickTaser(ServerLevel level) {
		getNavigation().stop();
		LivingEntity v = taserVictim;
		int live = enraged ? 40 : 30;
		if (v == null || !v.isAlive() || v.isRemoved() || v.level() != level || v.distanceToSqr(this) > 20 * 20 || !hasLineOfSight(v)) {
			endMove();
			return;
		}
		getLookControl().setLookAt(v, 40f, 40f);
		Vec3 from = taserFrom(1f);
		Vec3 to = v.position().add(0, v.getBbHeight() * 0.55, 0);
		if (moveTicks <= TASER_AIM) {
			if (moveTicks % 2 == 0) {
				Vec3 d = to.subtract(from);
				int n = (int) (d.length() * 1.5);
				for (int i = 1; i < n; i += 2) {
					Vec3 p = from.add(d.scale(i / (double) n));
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
				}
			}
			if (moveTicks == TASER_AIM) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.HOSTILE, 0.5f, 2.0f);
			}
			return;
		}
		setTaser(v);
		BeamClash.report(this, from, to.subtract(from), 18, 0.9f, 0x66CCFF); // every live tick
		int k = moveTicks - TASER_AIM;
		if (k % 5 == 1) {
			v.hurt(damageSources().mobAttack(this), 2.5f);
			v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1), this);
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.BEEHIVE_WORK, SoundSource.HOSTILE, 1.2f, 2.0f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 8, 0.3, 0.4, 0.3, 0.2);
		}
		Vec3 pull = position().subtract(v.position());
		Vec3 h = new Vec3(pull.x, 0, pull.z);
		if (h.lengthSqr() > 9.0) {
			h = h.normalize().scale(0.11);
			v.push(h.x, 0.0, h.z);
			v.hurtMarked = true;
		}
		if (k >= live) {
			endMove();
		}
	}

	private void tickLockdown(ServerLevel level) {
		getNavigation().stop();
		if (moveTicks < LOCKDOWN_WINDUP) {
			if (moveTicks % 10 == 1) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.5f, 1.4f);
			}
			if (moveTicks % 3 == 0) {
				for (int i = 0; i < 16; i++) {
					double a = i * Math.PI / 8 + moveTicks * 0.2;
					level.sendParticles(RED, getX() + Math.cos(a) * 2.2, getY() + 0.3 + moveTicks * 0.06, getZ() + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
				}
			}
			return;
		}
		if (moveTicks == LOCKDOWN_WINDUP) {
			BlockPos centre = home != null ? home : blockPosition();
			int sealed = VaultClearing.seal(level, centre);
			lockdownTicks = LOCKDOWN_TICKS;
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 3.0f, 0.5f);
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5f, 0.6f);
			var players = VaultClearing.playersNear(level, centre, 40);
			int squad = Math.min(5, 3 + Math.max(0, players.size() - 1));
			for (int i = 0; i < squad; i++) {
				BlockPos spot = VaultGuards.findSpot(level, centre, 5, 10, random);
				if (spot == null) {
					continue;
				}
				Mob m = VaultGuards.spawn(level, i == 2 ? VaultGuards.Role.GUNNER : VaultGuards.Role.WARDEN, spot, random.nextFloat() * 360f, 16);
				if (m != null) {
					if (!players.isEmpty()) {
						m.setTarget(players.get(random.nextInt(players.size())));
					}
					level.sendParticles(ParticleTypes.POOF, spot.getX() + 0.5, spot.getY() + 0.6, spot.getZ() + 0.5, 10, 0.3, 0.5, 0.3, 0.02);
				}
			}
			Component msg = Component.translatable("projecthero.vault.lockdown", sealed).withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
			for (ServerPlayer p : players) {
				p.displayClientMessage(msg, true);
			}
		}
		if (moveTicks >= LOCKDOWN_WINDUP + 10) {
			endMove();
		}
	}

	void endLockdown(ServerLevel level) {
		lockdownTicks = 0;
		lockdownCd = LOCKDOWN_REPEAT;
		VaultClearing.lift(level, home != null ? home : blockPosition());
		for (ServerPlayer p : VaultClearing.playersNear(level, blockPosition(), 40)) {
			p.displayClientMessage(Component.translatable("projecthero.vault.lockdown_lifted").withStyle(ChatFormatting.YELLOW), true);
		}
	}

	private int crowd() {
		return level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(shockRadius()), p -> victim(p, this)).size();
	}

	// ---------------------------------------------------------------- save

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (home != null) {
			tag.put("VaultHome", NbtUtils.writeBlockPos(home));
		}
		tag.putBoolean("Enraged", enraged);
		tag.putInt("LockdownTicks", lockdownTicks);
		tag.putInt("LockdownCd", lockdownCd);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setHome(NbtUtils.readBlockPos(tag, "VaultHome").orElse(null));
		enraged = tag.getBoolean("Enraged");
		lockdownTicks = tag.getInt("LockdownTicks");
		lockdownCd = tag.getInt("LockdownCd");
		if (enraged) {
			bar.setColor(BossEvent.BossBarColor.PURPLE);
			bar.setName(Component.translatable("projecthero.vault.kross.enraged_bar").withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** All four moves as one goal above the melee, so they never overlap. */
	static final class MovesGoal extends Goal {
		private final WardenKrossBoss mob;

		MovesGoal(WardenKrossBoss mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (mob.move != MOVE_NONE) {
				return true;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || mob.krossDazed > 0 || mob.gap > 0 || !mob.onGround()) {
				return false;
			}
			double d = mob.distanceTo(t);
			boolean sees = mob.getSensing().hasLineOfSight(t);
			if (mob.enraged && mob.lockdownTicks == 0 && mob.lockdownCd <= 0) {
				mob.startLockdown();
				return true;
			}
			// v0.17.7 phase 2: the slam for anyone keeping their distance, the gas for a target in the open
			if (mob.enraged && mob.slamCd <= 0 && d >= 6 && d <= 18 && sees && mob.random.nextInt(2) == 0) {
				mob.startSlam();
				return true;
			}
			if (mob.enraged && mob.gasCd <= 0 && d >= 4 && d <= 16 && sees) {
				mob.startGas();
				return true;
			}
			if (mob.shockCd <= 0 && d < mob.shockRadius() - 0.8 && t.onGround() && (mob.crowd() >= 2 || mob.random.nextInt(2) == 0)) {
				mob.startShock();
				return true;
			}
			if (mob.chargeCd <= 0 && d >= 5 && d <= 16 && sees && mob.random.nextInt(2) == 0) {
				mob.startCharge();
				return true;
			}
			if (mob.taserCd <= 0 && d >= 6 && d <= 18 && sees) {
				mob.startTaser();
				return true;
			}
			if (mob.chargeCd <= 0 && d >= 5 && d <= 16 && sees) {
				mob.startCharge();
				return true;
			}
			return false;
		}

		@Override
		public boolean canContinueToUse() {
			return mob.move != MOVE_NONE && mob.isAlive();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			if (mob.level() instanceof ServerLevel level) {
				mob.tickMove(level);
			}
		}

		@Override
		public void stop() {
			if (mob.move != MOVE_NONE) {
				mob.endMove();
			}
		}
	}
}
