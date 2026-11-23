package com.projecthero.mod.skyfortress.entity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.skyfortress.SkyFortressGuards;
import com.projecthero.mod.syndicate.SyndicateGunfire;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;
import com.projecthero.mod.syndicate.entity.SyndicateThug;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
 * v0.17.7: the <b>Deck Officer</b>, the Sky Fortress mid-boss on the Command Bridge -- Admiral Kestrel's second in
 * command in a navy dress tunic with gold braid, a peaked cap and flight goggles (player model, detailed skin, x1.15),
 * a sabre in one hand and a sidearm on the hip. {@link #MAX_HEALTH} health, 12 armour.
 * <ul>
 * <li><b>Sidearm Volley</b> (6-28 blocks, in sight): 0.6 s aim, then three pistol shots (real bullets) 0.3 s apart, 5 each.</li>
 * <li><b>Flashbang</b> (4-20 blocks): lobs a charge at the target's feet -- a white ring marks the 4.5-block blast for
 * a second -- then a flash: 4 damage, Blindness and Slowness II for 3 s. Step out of the ring.</li>
 * <li><b>Sabre Lunge</b> (4-12 blocks): a crouch, then a dash; whoever he cuts takes 11 and is thrown.</li>
 * <li><b>Rally the Deck</b> (once at 60% health, once at 30%): on the radio, two of the crew drop in and every guard
 * within 12 gets Strength for 10 s.</li>
 * </ul>
 * Shared {@link BossThreat} threat table (switches to whoever hurts him most), boss bar within {@link #BAR_RANGE},
 * leashed to the bridge ({@link #home}).
 */
public class DeckOfficerBoss extends SyndicateThug implements SkyHuman {
	public static final double MAX_HEALTH = 320.0;
	public static final float SCALE = 1.15f;
	public static final double BAR_RANGE = 32.0;
	public static final double LEASH = 12.0;
	public static final double TETHER = 30.0;
	public static final double FLASH_RADIUS = 4.5;

	public static final byte MOVE_NONE = 0;
	public static final byte MOVE_VOLLEY = 1;
	public static final byte MOVE_FLASH = 2;
	public static final byte MOVE_LUNGE = 3;
	public static final byte MOVE_RALLY = 4;

	static final int VOLLEY_AIM = 12;
	static final int FLASH_FUSE = 20;
	static final int LUNGE_CROUCH = 8;
	static final int LUNGE_RUN = 10;
	static final int RALLY_TICKS = 20;

	private static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/sky_fortress/deck_officer.png");
	private static final DustParticleOptions WHITE = new DustParticleOptions(new Vector3f(1f, 1f, 0.92f), 1.3f);

	private final BossThreat threat = new BossThreat(this).range(40).holdWhile(() -> this.move != MOVE_NONE);
	private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.projecthero.deck_officer").withStyle(ChatFormatting.GOLD),
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private BlockPos home;
	private int rallies;

	byte move;
	int moveTicks;
	int volleyCd = 40;
	int flashCd = 100;
	int lungeCd = 60;
	int gap;
	private Vec3 flashSpot;
	private Vec3 lungeDir = Vec3.ZERO;
	private final Set<LivingEntity> struck = new HashSet<>();
	private int movesUsed;

	public DeckOfficerBoss(EntityType<? extends DeckOfficerBoss> type, Level level) {
		super(type, level);
		this.xpReward = 120;
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 9.0)
				.add(Attributes.ARMOR, 12.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	public ResourceLocation skyTexture() {
		return TEX;
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.THUG_C;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		setSkin(SyndicateSkin.THUG_C);
		arm(new ItemStack(Items.NETHERITE_SWORD));
		setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		return out;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		targetSelector.removeAllGoals(g -> g instanceof HurtByTargetGoal);
	}

	@Override
	protected void registerCombatGoals() {
		goalSelector.addGoal(1, new MovesGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false));
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

	public byte move() {
		return move;
	}

	public int movesUsed() {
		return movesUsed;
	}

	public int rallies() {
		return rallies;
	}

	public Vec3 flashSpot() {
		return flashSpot;
	}

	// ---------------------------------------------------------------- damage

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel && isAlive()) {
			threat.record(source, amount);
			float f = getHealth() / getMaxHealth();
			if ((rallies == 0 && f <= 0.6f) || (rallies == 1 && f <= 0.3f)) {
				if (move == MOVE_NONE) {
					startRally();
				}
			}
		}
		return hurt;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		threat.tick();
		if (volleyCd > 0) {
			volleyCd--;
		}
		if (flashCd > 0) {
			flashCd--;
		}
		if (lungeCd > 0) {
			lungeCd--;
		}
		if (gap > 0) {
			gap--;
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
			for (ServerPlayer p : server.getPlayers(p -> p.distanceToSqr(this) < 48 * 48)) {
				p.displayClientMessage(Component.translatable("projecthero.sky_fortress.officer_down").withStyle(ChatFormatting.GOLD), false);
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
		return e != self && e.isAlive() && !(e instanceof SyndicateCriminal) && !(e instanceof SkyPrisoner)
				&& !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	private void begin(byte which) {
		move = which;
		moveTicks = 0;
		movesUsed++;
		getNavigation().stop();
	}

	/** Starts the Sidearm Volley now (test hook too). */
	public void startVolley() {
		begin(MOVE_VOLLEY);
		setAction(ACTION_AIM);
	}

	/** Starts a Flashbang at the current target's feet (or in front of him). */
	public void startFlash() {
		begin(MOVE_FLASH);
		LivingEntity t = getTarget();
		flashSpot = t != null ? t.position() : position().add(getViewVector(1f).scale(6));
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0f, 1.6f);
	}

	/** Starts the Sabre Lunge now. */
	public void startLunge() {
		begin(MOVE_LUNGE);
		struck.clear();
		LivingEntity t = getTarget();
		Vec3 to = t != null ? t.position().subtract(position()) : getViewVector(1f);
		to = new Vec3(to.x, 0, to.z);
		lungeDir = to.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : to.normalize();
	}

	/** Starts Rally the Deck now. */
	public void startRally() {
		begin(MOVE_RALLY);
		rallies++;
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 1.5f, 0.7f);
	}

	void endMove() {
		move = MOVE_NONE;
		moveTicks = 0;
		setAction(ACTION_NONE);
		gap = 20 + random.nextInt(20);
	}

	void tickMove(ServerLevel level) {
		moveTicks++;
		LivingEntity t = getTarget();
		switch (move) {
			case MOVE_VOLLEY -> {
				if (t != null) {
					getLookControl().setLookAt(t, 60f, 60f);
				}
				if (moveTicks >= VOLLEY_AIM && (moveTicks - VOLLEY_AIM) % 6 == 0 && t != null && t.isAlive()) {
					SyndicateGunfire.fireBullets(level, this, t.getEyePosition().add(0, -0.4, 0), 30, 5.0f, 2.6f, 2.0f, 1, SyndicateGunfire.Report.PISTOL);
				}
				if (moveTicks >= VOLLEY_AIM + 13) {
					volleyCd = 80 + random.nextInt(30);
					endMove();
				}
			}
			case MOVE_FLASH -> {
				if (flashSpot == null) {
					endMove();
					return;
				}
				if (moveTicks % 3 == 0) {
					for (int i = 0; i < 20; i++) {
						double a = i * Math.PI * 2 / 20;
						level.sendParticles(WHITE, flashSpot.x + Math.cos(a) * FLASH_RADIUS, flashSpot.y + 0.15, flashSpot.z + Math.sin(a) * FLASH_RADIUS,
								1, 0, 0, 0, 0);
					}
					level.sendParticles(ParticleTypes.SMOKE, flashSpot.x, flashSpot.y + 0.2, flashSpot.z, 2, 0.1, 0.05, 0.1, 0.01);
				}
				if (moveTicks >= FLASH_FUSE) {
					flash(level, flashSpot);
					flashCd = 160 + random.nextInt(40);
					endMove();
				}
			}
			case MOVE_LUNGE -> {
				if (moveTicks <= LUNGE_CROUCH) {
					setDeltaMovement(0, getDeltaMovement().y, 0);
					if (moveTicks == 1) {
						level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0f, 0.6f);
					}
					return;
				}
				setYRot((float) (Mth.atan2(lungeDir.z, lungeDir.x) * Mth.RAD_TO_DEG) - 90f);
				setYBodyRot(getYRot());
				setDeltaMovement(lungeDir.x * 0.95, getDeltaMovement().y, lungeDir.z * 0.95);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8), e -> victim(e, this))) {
					if (struck.add(e)) {
						e.hurt(damageSources().mobAttack(this), 11.0f);
						e.knockback(1.2, -lungeDir.x, -lungeDir.z);
						level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
					}
				}
				if (moveTicks >= LUNGE_CROUCH + LUNGE_RUN || horizontalCollision) {
					setDeltaMovement(0, getDeltaMovement().y, 0);
					lungeCd = 100 + random.nextInt(30);
					endMove();
				}
			}
			case MOVE_RALLY -> {
				if (moveTicks % 5 == 0) {
					level.sendParticles(ParticleTypes.NOTE, getX(), getEyeY() + 0.6, getZ(), 1, 0.2, 0.1, 0.2, 0.5);
				}
				if (moveTicks >= RALLY_TICKS) {
					rally(level);
					endMove();
				}
			}
			default -> endMove();
		}
	}

	/** The flash at {@code at}: everyone (not crew) within the radius. Returns how many were caught. */
	public int flash(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.5, at.z, 30, 1.5, 0.6, 1.5, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.HOSTILE, 2.0f, 1.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.8f, 1.8f);
		int n = 0;
		AABB box = new AABB(at, at).inflate(FLASH_RADIUS, 3, FLASH_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> victim(e, this))) {
			if (e.position().distanceToSqr(at) > FLASH_RADIUS * FLASH_RADIUS + 4) {
				continue;
			}
			e.hurt(damageSources().mobAttack(this), 4.0f);
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), this);
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
			n++;
		}
		return n;
	}

	/** Two of the crew drop in; every guard within 12 gets Strength for 10 s. Returns how many came. */
	public int rally(ServerLevel level) {
		int came = 0;
		for (int i = 0; i < 2; i++) {
			BlockPos spot = com.projecthero.mod.hideout.HideoutGuards.findSpot(level, blockPosition(), 2, 6, random);
			if (spot == null) {
				continue;
			}
			Mob m = SkyFortressGuards.reinforcement(level, spot, random);
			if (m != null) {
				came++;
				if (getTarget() != null) {
					m.setTarget(getTarget());
				}
			}
		}
		for (Mob m : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(12), m -> m != this && m instanceof SyndicateCriminal)) {
			m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
		}
		for (ServerPlayer p : level.getPlayers(p -> p.distanceToSqr(this) < 32 * 32)) {
			p.displayClientMessage(Component.translatable("projecthero.sky_fortress.officer_rally").withStyle(ChatFormatting.GOLD), true);
		}
		return came;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (home != null) {
			tag.put("SkyHome", NbtUtils.writeBlockPos(home));
		}
		tag.putInt("Rallies", rallies);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setHome(NbtUtils.readBlockPos(tag, "SkyHome").orElse(null));
		rallies = tag.getInt("Rallies");
	}

	/** The four moves as one goal above the melee. */
	static final class MovesGoal extends Goal {
		private final DeckOfficerBoss mob;

		MovesGoal(DeckOfficerBoss mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (mob.move != MOVE_NONE) {
				return true;
			}
			LivingEntity t = mob.getTarget();
			if (t == null || !t.isAlive() || mob.gap > 0 || !mob.onGround()) {
				return false;
			}
			double d = mob.distanceTo(t);
			boolean sees = mob.getSensing().hasLineOfSight(t);
			if (mob.lungeCd <= 0 && d >= 4 && d <= 12 && sees && mob.random.nextInt(3) == 0) {
				mob.startLunge();
				return true;
			}
			if (mob.flashCd <= 0 && d >= 4 && d <= 20 && mob.random.nextInt(2) == 0) {
				mob.startFlash();
				return true;
			}
			if (mob.volleyCd <= 0 && d >= 6 && d <= 28 && sees) {
				mob.startVolley();
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
