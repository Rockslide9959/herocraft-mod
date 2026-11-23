package com.projecthero.mod.vaultdungeon.entity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.gear.GearItems;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.syndicate.entity.SyndicateEnforcer;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;
import com.projecthero.mod.vaultdungeon.VaultClearing;
import com.projecthero.mod.vaultdungeon.VaultGuards;
import com.projecthero.mod.vaultdungeon.VaultSecurity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: <b>the Riot Captain</b> -- The Vault's mid-boss, holding the Maximum Security hall. A big riot officer
 * (x1.25, player model in a detailed white-striped riot kit) with a stun baton and a tower shield, {@link #MAX_HEALTH}
 * health. His shield takes {@link RiotWarden#SHIELD_CUT} off frontal hits.
 * <ul>
 * <li><b>Shield Rush</b> (4-12 blocks, in sight): braces half a second, then rushes 0.75 blocks a tick for 0.7 s;
 * 9 damage, a shove and Slowness II. Into a wall: dazed 1.5 s, +50% damage taken.</li>
 * <li><b>Flashbang</b> (5-16 blocks, in sight): lobs a grenade at you; 0.8 s later it pops -- everyone within
 * {@link #FLASH_RADIUS} with a clear line to it is blinded 3 s and slowed, and takes 3.</li>
 * <li><b>Rally</b> (once, at half health): a whistle; two shield-bearing riot wardens arrive and every guard within
 * 12 gets Strength for 10 s; he gets Resistance for 5 s.</li>
 * </ul>
 * Shared {@link BossThreat} threat table, boss bar within {@link #BAR_RANGE}, leashed to his post. His death drops the
 * red security field on the hall's far door ({@link VaultSecurity#captainDown}).
 */
public class RiotCaptainBoss extends SyndicateEnforcer {
	public static final float SCALE = 1.25f;
	public static final double MAX_HEALTH = 380.0;
	public static final double BAR_RANGE = 40.0;
	public static final double LEASH = 14.0;
	public static final double TETHER = 32.0;
	public static final double FLASH_RADIUS = 6.0;

	public static final byte MOVE_NONE = 0;
	public static final byte MOVE_RUSH = 1;
	public static final byte MOVE_FLASH = 2;

	static final int RUSH_BRACE = 10;
	static final int RUSH_RUN = 14;
	static final int FLASH_WIND = 8;
	static final int FLASH_FLIGHT = 16;

	private final BossThreat threat = new BossThreat(this).range(40).holdWhile(() -> this.move != MOVE_NONE);
	private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.projecthero.riot_captain").withStyle(ChatFormatting.GOLD),
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private BlockPos home;
	private boolean rallied;
	private boolean reported;

	byte move;
	int moveTicks;
	int rushCd = 50;
	int flashCd = 90;
	int gap;
	int dazed;
	private Vec3 rushDir = Vec3.ZERO;
	private Vec3 flashAt;
	private final Set<LivingEntity> struck = new HashSet<>();
	private int movesUsed;

	public RiotCaptainBoss(EntityType<? extends RiotCaptainBoss> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.29)
				.add(Attributes.ATTACK_DAMAGE, 8.0)
				.add(Attributes.ATTACK_KNOCKBACK, 0.8)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.VAULT_RIOT_CAPTAIN;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(SyndicateSkin.VAULT_RIOT_CAPTAIN);
		arm(new ItemStack(GearItems.ALLOY_BATON != null ? GearItems.ALLOY_BATON : Items.IRON_SWORD));
		setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		setDropChance(EquipmentSlot.OFFHAND, 0.0f);
		return out;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		targetSelector.removeAllGoals(g -> g instanceof HurtByTargetGoal); // the threat table picks targets
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

	public boolean rallied() {
		return rallied;
	}

	public byte move() {
		return move;
	}

	public int movesUsed() {
		return movesUsed;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide()) {
			if (dazed > 0) {
				amount *= 1.5f;
			} else if (!getOffhandItem().isEmpty() && RiotWarden.fromFront(this, source)) {
				amount *= 1f - RiotWarden.SHIELD_CUT;
				level().playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0f, 0.85f);
			}
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			threat.record(source, amount);
			if (!rallied && isAlive() && getHealth() <= getMaxHealth() * 0.5f) {
				rally(server);
			}
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (dazed > 0) {
			return false;
		}
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity l) {
			l.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 1), this);
		}
		return hit;
	}

	/** Half health, once: the Rally. Returns how many wardens arrived. */
	public int rally(ServerLevel level) {
		if (rallied) {
			return 0;
		}
		rallied = true;
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.5f, 1.8f);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 1.5f, 0.6f);
		addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
		BlockPos centre = home != null ? home : blockPosition();
		var players = VaultClearing.playersNear(level, centre, 32);
		int n = 0;
		for (int i = 0; i < 2; i++) {
			BlockPos spot = VaultGuards.findSpot(level, centre, 3, 8, random);
			if (spot == null) {
				continue;
			}
			Mob m = VaultGuards.spawn(level, VaultGuards.Role.WARDEN, spot, random.nextFloat() * 360f, 14, true,
					com.projecthero.mod.alarm.GuardRole.SHIELD);
			if (m != null) {
				n++;
				level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 0.8, m.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				if (!players.isEmpty()) {
					m.setTarget(players.get(random.nextInt(players.size())));
				}
			}
		}
		for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(12), m -> m != this && VaultGuards.isGuard(m))) {
			m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
		}
		Component msg = Component.translatable("projecthero.vault.riot_captain.rally").withStyle(ChatFormatting.GOLD);
		for (ServerPlayer p : players) {
			p.displayClientMessage(msg, true);
		}
		bar.setColor(BossEvent.BossBarColor.RED);
		return n;
	}

	private int cd(int base, int spread) {
		int v = base + random.nextInt(spread);
		return rallied ? (int) (v * 0.75f) : v;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		threat.tick();
		if (rushCd > 0) {
			rushCd--;
		}
		if (flashCd > 0) {
			flashCd--;
		}
		if (gap > 0) {
			gap--;
		}
		if (dazed > 0) {
			getNavigation().stop();
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (tickCount % 6 == 0) {
				server.sendParticles(ParticleTypes.CRIT, getX(), getEyeY() + 0.3, getZ(), 3, 0.3, 0.1, 0.3, 0.0);
			}
			if (--dazed == 0) {
				setAction(ACTION_NONE);
			}
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
		boolean engaged = t != null && t.isAlive() && t.distanceToSqr(this) < 12 * 12;
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
			if (!reported) {
				reported = true;
				VaultSecurity.captainDown(server, home != null ? home : blockPosition());
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

	/** Starts the Shield Rush now (test / harness hook too). */
	public void startRush() {
		begin(MOVE_RUSH);
		struck.clear();
		LivingEntity t = getTarget();
		Vec3 to = t != null ? t.position().subtract(position()) : getViewVector(1f);
		rushDir = new Vec3(to.x, 0, to.z).lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : new Vec3(to.x, 0, to.z).normalize();
		setAction(ACTION_WINDUP);
	}

	/** Lobs a Flashbang at the current target (or 6 blocks ahead) now. */
	public void startFlash() {
		begin(MOVE_FLASH);
		LivingEntity t = getTarget();
		flashAt = t != null ? t.position() : position().add(getViewVector(1f).multiply(6, 0, 6));
	}

	void endMove() {
		switch (move) {
			case MOVE_RUSH -> rushCd = cd(100, 40);
			case MOVE_FLASH -> flashCd = cd(160, 60);
			default -> {
			}
		}
		move = MOVE_NONE;
		moveTicks = 0;
		gap = rallied ? 14 : 22;
		if (dazed <= 0) {
			setAction(ACTION_NONE);
		}
	}

	void tickMove(ServerLevel level) {
		moveTicks++;
		switch (move) {
			case MOVE_RUSH -> tickRush(level);
			case MOVE_FLASH -> tickFlash(level);
			default -> endMove();
		}
	}

	private void tickRush(ServerLevel level) {
		LivingEntity t = getTarget();
		if (moveTicks <= RUSH_BRACE) {
			getNavigation().stop();
			if (t != null) {
				getLookControl().setLookAt(t, 40f, 40f);
				Vec3 to = t.position().subtract(position());
				if (to.x * to.x + to.z * to.z > 1.0e-4) {
					rushDir = new Vec3(to.x, 0, to.z).normalize();
				}
			}
			if (moveTicks == RUSH_BRACE) {
				setAction(ACTION_CHARGE);
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.VINDICATOR_CELEBRATE, SoundSource.HOSTILE, 1.4f, 0.7f);
			}
			return;
		}
		setYRot((float) Math.toDegrees(Math.atan2(-rushDir.x, rushDir.z)));
		setYBodyRot(getYRot());
		setYHeadRot(getYRot());
		Vec3 v = rushDir.scale(0.75);
		setDeltaMovement(v.x, getDeltaMovement().y, v.z);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), e -> victim(e, this))) {
			if (struck.add(e)) {
				e.hurt(damageSources().mobAttack(this), 9f);
				e.push(rushDir.x * 1.2, 0.4, rushDir.z * 1.2);
				e.hurtMarked = true;
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1), this);
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.3f, 0.6f);
			}
		}
		if (horizontalCollision && moveTicks > RUSH_BRACE + 2) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.2f, 0.8f);
			setDeltaMovement(0, getDeltaMovement().y, 0);
			dazed = 30;
			setAction(ACTION_DAZED);
			endMove();
			return;
		}
		if (moveTicks >= RUSH_BRACE + RUSH_RUN) {
			setDeltaMovement(0, getDeltaMovement().y, 0);
			endMove();
		}
	}

	private void tickFlash(ServerLevel level) {
		getNavigation().stop();
		if (flashAt == null) {
			endMove();
			return;
		}
		getLookControl().setLookAt(flashAt.x, flashAt.y + 1, flashAt.z);
		Vec3 from = position().add(0, 1.6 * SCALE, 0);
		if (moveTicks == FLASH_WIND) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.0f, 0.5f);
		}
		if (moveTicks > FLASH_WIND && moveTicks < FLASH_WIND + FLASH_FLIGHT) {
			// the grenade's arc
			double k = (moveTicks - FLASH_WIND) / (double) FLASH_FLIGHT;
			Vec3 p = from.lerp(flashAt.add(0, 0.2, 0), k).add(0, Math.sin(k * Math.PI) * 2.5, 0);
			level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
		}
		if (moveTicks == FLASH_WIND + FLASH_FLIGHT) {
			pop(level, flashAt.add(0, 0.4, 0));
			flashAt = null;
			endMove();
		}
	}

	/** The flashbang goes off at {@code at}. Returns how many were caught by it. */
	public int pop(ServerLevel level, Vec3 at) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 2.5f, 1.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.6f, 2.0f);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.25);
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(FLASH_RADIUS), e -> victim(e, this))) {
			Vec3 eye = e.getEyePosition();
			if (eye.distanceToSqr(at) > FLASH_RADIUS * FLASH_RADIUS) {
				continue;
			}
			if (level.clip(new ClipContext(at, eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.BLOCK) {
				continue;
			}
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), this);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), this);
			e.hurt(damageSources().mobAttack(this), 3f);
			n++;
		}
		return n;
	}

	// ---------------------------------------------------------------- save

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (home != null) {
			tag.put("CaptainHome", NbtUtils.writeBlockPos(home));
		}
		tag.putBoolean("Rallied", rallied);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setHome(NbtUtils.readBlockPos(tag, "CaptainHome").orElse(null));
		rallied = tag.getBoolean("Rallied");
		if (rallied) {
			bar.setColor(BossEvent.BossBarColor.RED);
		}
	}

	/** The moves as one goal above the melee, so they never overlap. */
	static final class MovesGoal extends Goal {
		private final RiotCaptainBoss mob;

		MovesGoal(RiotCaptainBoss mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (mob.move != MOVE_NONE) {
				return true;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || mob.dazed > 0 || mob.gap > 0 || !mob.onGround()) {
				return false;
			}
			double d = mob.distanceTo(t);
			boolean sees = mob.getSensing().hasLineOfSight(t);
			if (mob.flashCd <= 0 && d >= 5 && d <= 16 && sees && mob.random.nextInt(2) == 0) {
				mob.startFlash();
				return true;
			}
			if (mob.rushCd <= 0 && d >= 4 && d <= 12 && sees) {
				mob.startRush();
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
