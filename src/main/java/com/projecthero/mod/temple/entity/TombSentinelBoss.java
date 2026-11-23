package com.projecthero.mod.temple.entity;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.event.boss.BossThreat;
import com.projecthero.mod.temple.TempleClearing;
import com.projecthero.mod.temple.TempleGuards;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * v0.17.7: the <b>Tomb Sentinel</b>, the Ancient Temple's mid-boss in the Flooded Crypt -- a giant (x1.6) temple guard in
 * gold-banded lapis-and-limestone armour and a nemes headdress, with a great halberd (a netherite axe). Player model,
 * detailed skin. {@link #MAX_HEALTH} health, 12 armour.
 * <ul>
 * <li><b>Crypt Slam</b> (target within 6): the halberd raised for a second while a gold ring marks {@link #SLAM_RADIUS};
 * then 10 to everyone on the ground inside, thrown up and back. Jump it, or get out.</li>
 * <li><b>Riptide Charge</b> (4-16 blocks): turns to the target for 0.6 s, then ploughs a straight line through the water
 * for 0.9 s; 9 + a hard shove to each player in the way. Stops at walls.</li>
 * <li><b>Rise, servants</b> (once, at half health): two wrapped revenants claw up out of the water; his moves come 30%
 * faster after it.</li>
 * </ul>
 * Shared {@link BossThreat} table (switches to whoever hurts him most), boss bar within 40, leashed to the crypt. His death
 * opens the crypt's far gate ({@link TempleClearing#sentinelDefeated}).
 */
public class TombSentinelBoss extends TempleMob {
	public static final float SCALE = 1.6f;
	public static final double MAX_HEALTH = 480.0;
	public static final double SLAM_RADIUS = 5.0;
	public static final double BAR_RANGE = 40.0;
	public static final int SLAM_WINDUP = 20;
	public static final int CHARGE_WINDUP = 12;
	public static final int CHARGE_RUN = 18;

	public static final byte MOVE_NONE = 0;
	public static final byte MOVE_SLAM = 1;
	public static final byte MOVE_CHARGE = 2;

	private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1f, 0.8f, 0.25f), 1.4f);

	private final BossThreat threat = new BossThreat(this).range(40).holdWhile(() -> this.move != MOVE_NONE);
	private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.projecthero.tomb_sentinel").withStyle(ChatFormatting.GOLD),
			BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private final Set<LivingEntity> struck = new HashSet<>();
	private BlockPos home;
	private boolean risen;
	private boolean reported;
	byte move;
	int moveTicks;
	int slamCd = 60;
	int chargeCd = 100;
	private Vec3 chargeDir = Vec3.ZERO;
	public int movesUsed;

	public TombSentinelBoss(EntityType<? extends TombSentinelBoss> type, Level level) {
		super(type, level);
		this.xpReward = 120;
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
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.STEP_HEIGHT, 1.0)
				.add(Attributes.SCALE, SCALE);
	}

	@Override
	protected Skin defaultTempleSkin() {
		return Skin.SENTINEL;
	}

	@Override
	protected void outfit() {
		setTempleSkin(Skin.SENTINEL);
		arm(new ItemStack(Items.NETHERITE_AXE));
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		targetSelector.removeAllGoals(g -> g instanceof HurtByTargetGoal); // the shared threat table decides
	}

	@Override
	protected void registerCombatGoals() {
		goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
	}

	public BlockPos home() {
		return home;
	}

	public void setHome(BlockPos home) {
		this.home = home == null ? null : home.immutable();
		if (home != null) {
			restrictTo(home, 14);
		}
	}

	public BossThreat threat() {
		return threat;
	}

	public byte move() {
		return move;
	}

	public boolean risen() {
		return risen;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			threat.record(source, amount);
			if (!risen && isAlive() && getHealth() <= getMaxHealth() * 0.5f) {
				rise(server);
			}
		}
		return hurt;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		return move == MOVE_NONE && super.doHurtTarget(target);
	}

	/** Half health: two revenants rise and his moves quicken. */
	public void rise(ServerLevel level) {
		risen = true;
		bar.setColor(BossEvent.BossBarColor.RED);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 0.6f);
		for (ServerPlayer p : TempleClearing.playersNear(level, blockPosition(), 40)) {
			p.displayClientMessage(Component.translatable("projecthero.temple.sentinel.rise").withStyle(ChatFormatting.GOLD), false);
		}
		for (int i = 0; i < 2; i++) {
			BlockPos spot = TempleGuards.findSpot(level, blockPosition(), 3, 7, random);
			if (spot != null) {
				var m = TempleGuards.spawn(level, TempleGuards.Role.REVENANT, spot, random.nextFloat() * 360f, 16, null, false);
				if (m != null) {
					level.sendParticles(ParticleTypes.SPLASH, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 30, 0.4, 0.3, 0.4, 0.2);
					if (getTarget() != null) {
						m.setTarget(getTarget());
					}
				}
			}
		}
	}

	private int cd(int base, int spread) {
		int v = base + random.nextInt(spread);
		return risen ? (int) (v * 0.7f) : v;
	}

	@Override
	protected void customServerAiStep() {
		super.customServerAiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		LivingEntity switched = threat.tick();
		if (switched != null) {
			setTarget(switched);
		}
		bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
		if (tickCount % 10 == 0) {
			refreshViewers(server);
		}
		if (home != null && tickCount % 40 == 0 && distanceToSqr(Vec3.atBottomCenterOf(home)) > 32 * 32) {
			moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, getYRot(), 0f);
			getNavigation().stop();
		}
		if (slamCd > 0) {
			slamCd--;
		}
		if (chargeCd > 0) {
			chargeCd--;
		}
		if (move != MOVE_NONE) {
			tickMove(server);
			return;
		}
		LivingEntity t = getTarget();
		if (t == null || !t.isAlive()) {
			return;
		}
		double d = distanceTo(t);
		if (slamCd == 0 && d < 6.0) {
			startSlam();
		} else if (chargeCd == 0 && d > 4.0 && d < 16.0 && hasLineOfSight(t)) {
			startCharge(t);
		}
	}

	public void startSlam() {
		move = MOVE_SLAM;
		moveTicks = 0;
		movesUsed++;
		getNavigation().stop();
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 1.3f);
	}

	public void startCharge(LivingEntity t) {
		move = MOVE_CHARGE;
		moveTicks = 0;
		movesUsed++;
		struck.clear();
		getNavigation().stop();
		Vec3 d = t.position().subtract(position()).multiply(1, 0, 1);
		chargeDir = d.lengthSqr() < 1.0e-4 ? getLookAngle().multiply(1, 0, 1).normalize() : d.normalize();
		level().playSound(null, getX(), getY(), getZ(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.6f);
	}

	private void tickMove(ServerLevel level) {
		moveTicks++;
		getNavigation().stop();
		if (move == MOVE_SLAM) {
			setDeltaMovement(0, getDeltaMovement().y, 0);
			if (moveTicks % 4 == 0 && moveTicks < SLAM_WINDUP) {
				for (int i = 0; i < 28; i++) {
					double a = i * Math.PI * 2 / 28;
					level.sendParticles(GOLD, getX() + Math.cos(a) * SLAM_RADIUS, getY() + 0.15, getZ() + Math.sin(a) * SLAM_RADIUS, 1, 0, 0, 0, 0);
				}
			}
			if (moveTicks == SLAM_WINDUP) {
				level.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4f, 0.6f);
				level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 120, SLAM_RADIUS * 0.5, 0.2, SLAM_RADIUS * 0.5, 0.4);
				level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, 1.5, 0.1, 1.5, 0);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SLAM_RADIUS, 2, SLAM_RADIUS),
						e -> e instanceof Player p && enemy(p) && e.onGround() && e.distanceToSqr(this) <= SLAM_RADIUS * SLAM_RADIUS)) {
					e.hurt(damageSources().mobAttack(this), 10f);
					Vec3 away = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.9);
					e.push(away.x, 0.7, away.z);
					e.hurtMarked = true;
				}
			}
			if (moveTicks >= SLAM_WINDUP + 10) {
				move = MOVE_NONE;
				slamCd = cd(90, 40);
			}
		} else if (move == MOVE_CHARGE) {
			if (moveTicks <= CHARGE_WINDUP) {
				setYRot((float) (Mth.atan2(chargeDir.z, chargeDir.x) * Mth.RAD_TO_DEG) - 90f);
				setYBodyRot(getYRot());
				setYHeadRot(getYRot());
				setDeltaMovement(0, getDeltaMovement().y, 0);
				if (moveTicks % 3 == 0) {
					level.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.4, getZ(), 8, 0.6, 0.2, 0.6, 0.05);
				}
				return;
			}
			setDeltaMovement(chargeDir.x * 0.85, getDeltaMovement().y, chargeDir.z * 0.85);
			level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.3, getZ(), 12, 0.6, 0.2, 0.6, 0.2);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8),
					e -> e instanceof Player p && enemy(p) && !struck.contains(e))) {
				struck.add(e);
				e.hurt(damageSources().mobAttack(this), 9f);
				e.push(chargeDir.x * 1.3, 0.45, chargeDir.z * 1.3);
				e.hurtMarked = true;
				level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2f, 0.7f);
			}
			if (horizontalCollision || moveTicks >= CHARGE_WINDUP + CHARGE_RUN) {
				setDeltaMovement(0, getDeltaMovement().y, 0);
				move = MOVE_NONE;
				chargeCd = cd(140, 60);
			}
		}
	}

	static boolean enemy(Player p) {
		return !p.isCreative() && !p.isSpectator();
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

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		viewers.remove(player);
		bar.removePlayer(player);
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		bar.removeAllPlayers();
		viewers.clear();
		if (level() instanceof ServerLevel server && !reported) {
			reported = true;
			TempleClearing.sentinelDefeated(server, home != null ? home : blockPosition());
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		bar.removeAllPlayers();
		viewers.clear();
		super.remove(reason);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSq) {
		return false;
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		if (home != null) {
			tag.put("TempleHome", NbtUtils.writeBlockPos(home));
		}
		tag.putBoolean("Risen", risen);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		NbtUtils.readBlockPos(tag, "TempleHome").ifPresent(this::setHome);
		risen = tag.getBoolean("Risen");
		if (risen) {
			bar.setColor(BossEvent.BossBarColor.RED);
		}
	}
}
