package com.projecthero.mod.syndicate.entity;

import java.util.EnumSet;

import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.syndicate.SyndicateGunfire;

import org.joml.Vector3f;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: the Syndicate's shooters, in three {@link Kind}s, each holding the matching Punisher gun:
 * <ul>
 *   <li><b>Pistol</b> -- keeps 5-14 blocks off and strafes, a six-round magazine then a reload you can hear;</li>
 *   <li><b>Shotgun</b> -- walks straight at you and fires a six-pellet blast up close, then pumps;</li>
 *   <li><b>Sniper</b> -- holds his perch (the warehouse catwalk) and paints you with a red laser for 1.5 s before a
 *       heavy shot: break line of sight while the laser is on you.</li>
 * </ul>
 * v0.16.2: shots are real bullets ({@link SyndicateBulletEntity}) aimed the way a skeleton aims -- at where you are when
 * he fires, no lead, scattered by the skeleton's difficulty inaccuracy (Easy 10 / Normal 6 / Hard 2) scaled per kind --
 * and fast but visible, so you can strafe out of the way; a raised shield blocks them.
 */
public class SyndicateGunman extends SyndicateCriminal {
	public enum Kind {
		// bullet speed (blocks/tick; a skeleton's arrow is 1.6) and inaccuracy = skeleton inaccuracy x scale + add:
		// pistol ~ a skeleton, the shotgun's six pellets a wide cone, the sniper the tightest (his laser gives him away)
		PISTOL(SyndicateSkin.GUNMAN, 22.0, 4.0f, 2.4f, 1.0f, 0.0f, 1, 6, 6, 18, 50, 5.0, 14.0),
		SHOTGUN(SyndicateSkin.MASKED, 12.0, 2.5f, 2.0f, 1.0f, 12.0f, 6, 10, 1, 0, 30, 0.0, 6.0),
		SNIPER(SyndicateSkin.HOOD, 48.0, 12.0f, 3.5f, 0.35f, 0.0f, 1, 30, 1, 0, 70, 0.0, 48.0);

		final SyndicateSkin skin;
		final double range;
		final float damage;
		final float speed;
		final float spreadScale;
		final float spreadAdd;
		final int pellets;
		final int windup;
		final int magazine;
		final int shotGap;
		final int reload;
		final double minDist;
		final double maxDist;

		Kind(SyndicateSkin skin, double range, float damage, float speed, float spreadScale, float spreadAdd, int pellets, int windup, int magazine, int shotGap,
				int reload, double minDist, double maxDist) {
			this.skin = skin;
			this.range = range;
			this.damage = damage;
			this.speed = speed;
			this.spreadScale = spreadScale;
			this.spreadAdd = spreadAdd;
			this.pellets = pellets;
			this.windup = windup;
			this.magazine = magazine;
			this.shotGap = shotGap;
			this.reload = reload;
			this.minDist = minDist;
			this.maxDist = maxDist;
		}

		/** Bullet speed in blocks a tick. */
		public float speed() {
			return speed;
		}

		public double range() {
			return range;
		}

		public float damage() {
			return damage;
		}

		public int pellets() {
			return pellets;
		}

		/** Vanilla {@code Projectile.shoot} inaccuracy at {@code difficulty}: the skeleton's, scaled for this gun. */
		public float inaccuracy(Difficulty difficulty) {
			return SyndicateGunfire.skeletonInaccuracy(difficulty) * spreadScale + spreadAdd;
		}

		Item gun() {
			return switch (this) {
				case PISTOL -> FirearmItems.PUNISHER_PISTOL;
				case SHOTGUN -> FirearmItems.PUNISHER_SHOTGUN;
				case SNIPER -> FirearmItems.PUNISHER_SNIPER;
			};
		}

		SyndicateGunfire.Report report() {
			return switch (this) {
				case PISTOL -> SyndicateGunfire.Report.PISTOL;
				case SHOTGUN -> SyndicateGunfire.Report.SHOTGUN;
				case SNIPER -> SyndicateGunfire.Report.SNIPER;
			};
		}
	}

	private Kind kind = Kind.PISTOL;

	/**
	 * v0.16.7 (perf): where a sniper's laser sight points (world coordinates), or {@link #LASER_OFF}. The laser used to be
	 * up to 80 dust particles -- 80 packets -- a tick per sniper; now this one synced value drives a client-drawn beam
	 * ({@code client.syndicate.SyndicateRenderer}).
	 */
	private static final EntityDataAccessor<Vector3f> DATA_LASER = SynchedEntityData.defineId(SyndicateGunman.class,
			EntityDataSerializers.VECTOR3);
	public static final Vector3f LASER_OFF = new Vector3f(0, -1.0e7f, 0);

	public SyndicateGunman(EntityType<? extends SyndicateGunman> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_LASER, new Vector3f(LASER_OFF));
	}

	/** The laser sight's aim point, or null when it is off. */
	public Vec3 laserAim() {
		Vector3f v = entityData.get(DATA_LASER);
		return v.y < -1.0e6f ? null : new Vec3(v.x, v.y, v.z);
	}

	/** Points the laser sight at {@code aim} (null switches it off). Synced only when it changes. */
	public void setLaserAim(Vec3 aim) {
		Vector3f v = aim == null ? LASER_OFF : new Vector3f((float) aim.x, (float) aim.y, (float) aim.z);
		if (!v.equals(entityData.get(DATA_LASER))) {
			entityData.set(DATA_LASER, new Vector3f(v));
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 22.0)
				.add(Attributes.MOVEMENT_SPEED, 0.29)
				.add(Attributes.ATTACK_DAMAGE, 3.0)
				.add(Attributes.ARMOR, 2.0)
				.add(Attributes.FOLLOW_RANGE, 56.0);
	}

	public Kind kind() {
		return kind;
	}

	/** Sets the kind (and with it the skin and gun). Call before adding to the world. */
	public void setKind(Kind kind) {
		this.kind = kind;
		setSkin(kind.skin);
		arm(new ItemStack(kind.gun() != null ? kind.gun() : Items.CROSSBOW));
	}

	/**
	 * v0.16.2: one trigger pull at {@code aim} (the target's body where it stands now): this kind's bullets, speed,
	 * damage and difficulty-scaled spread, with the muzzle flash and report. Returns the bullets fired.
	 */
	public int shootAt(ServerLevel level, Vec3 aim) {
		Kind k = kind;
		return SyndicateGunfire.fireBullets(level, this, aim, k.range + 1.0, k.damage, k.speed, k.inaccuracy(level.getDifficulty()), k.pellets,
				k.report());
	}

	@Override
	protected SyndicateSkin defaultSkin() {
		return SyndicateSkin.GUNMAN;
	}

	@Override
	protected void registerCombatGoals() {
		goalSelector.addGoal(2, new GunGoal(this));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, SpawnGroupData data) {
		SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
		if (getMainHandItem().isEmpty()) {
			setKind(kind);
		}
		return out;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, source, recentlyHit);
		if (recentlyHit && random.nextFloat() < 0.35f) {
			Item ammo = switch (kind) {
				case PISTOL -> FirearmItems.PISTOL_AMMO;
				case SHOTGUN -> FirearmItems.SHOTGUN_SHELL;
				case SNIPER -> FirearmItems.SNIPER_AMMO;
			};
			spawnAtLocation(new ItemStack(ammo != null ? ammo : Items.GUNPOWDER, 2 + random.nextInt(5)));
		}
		if (recentlyHit && random.nextFloat() < 0.08f && FirearmItems.WEAPON_PARTS != null) {
			spawnAtLocation(new ItemStack(FirearmItems.WEAPON_PARTS));
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("GunKind", kind.name());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		try {
			kind = Kind.valueOf(tag.getString("GunKind"));
		} catch (IllegalArgumentException e) {
			kind = Kind.PISTOL;
		}
	}

	/** Movement and shooting in one goal, so a gunman never walks off mid-aim. */
	static final class GunGoal extends Goal {
		private final SyndicateGunman mob;
		private int cooldown;
		private int aiming;
		private int rounds;
		private int strafeTicks;
		private boolean strafeLeft;
		private int blindTicks;

		GunGoal(SyndicateGunman mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity t = mob.getTarget();
			return t != null && t.isAlive();
		}

		@Override
		public void start() {
			rounds = mob.kind.magazine;
			cooldown = 10 + mob.random.nextInt(15);
		}

		@Override
		public void stop() {
			aiming = 0;
			mob.setAction(ACTION_NONE);
			mob.setLaserAim(null);
			mob.getNavigation().stop();
			lastPathTarget = null;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		/** Set by {@link #aimAndShoot} when the sniper's laser is on this tick. */
		private boolean lasing;
		/** v0.16.7 (perf): where the last path was aimed and when (re-path every 10 ticks or when the target moved). */
		private Vec3 lastPathTarget;
		private int lastPathTick;

		@Override
		public void tick() {
			lasing = false;
			aimAndShoot();
			if (!lasing) {
				mob.setLaserAim(null);
			}
		}

		/** v0.16.7 (perf): {@code moveTo(target)} at most every 10 ticks, sooner if the target moved > 1.5 blocks. */
		private void chase(LivingEntity target, double speed) {
			Vec3 at = target.position();
			boolean stale = lastPathTarget == null || mob.tickCount - lastPathTick >= 10 || at.distanceToSqr(lastPathTarget) > 1.5 * 1.5;
			if (stale) {
				mob.getNavigation().moveTo(target, speed);
				lastPathTarget = at;
				lastPathTick = mob.tickCount;
			}
		}

		private void halt() {
			if (lastPathTarget != null || !mob.getNavigation().isDone()) {
				mob.getNavigation().stop();
				lastPathTarget = null;
			}
		}

		private void aimAndShoot() {
			LivingEntity target = mob.getTarget();
			if (target == null || !(mob.level() instanceof ServerLevel level)) {
				return;
			}
			Kind k = mob.kind;
			double dist = mob.distanceTo(target);
			boolean sees = mob.getSensing().hasLineOfSight(target);
			blindTicks = sees ? 0 : blindTicks + 1;
			mob.getLookControl().setLookAt(target, 30f, 30f);
			move(target, k, dist, sees);

			if (cooldown > 0) {
				cooldown--;
				return;
			}
			if (!sees || dist > k.range) {
				if (aiming > 0) {
					aiming = 0;
					mob.setAction(ACTION_NONE);
				}
				return;
			}
			mob.setAction(ACTION_AIM);
			aiming++;
			Vec3 aimAt = target.position().add(0, target.getBbHeight() * 0.6, 0);
			if (k == Kind.SNIPER) {
				mob.setLaserAim(SyndicateGunfire.laserEnd(mob, aimAt));
				lasing = true;
				if (aiming == 1) {
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.HOSTILE, 1.2f, 0.7f);
				}
			}
			if (aiming < k.windup) {
				return;
			}
			mob.shootAt(level, aimAt);
			aiming = 0;
			rounds--;
			if (rounds <= 0) {
				rounds = k.magazine;
				cooldown = k.reload;
				mob.setAction(ACTION_NONE);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.HOSTILE, 0.8f,
						k == Kind.SHOTGUN ? 0.7f : 1.3f);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.HOSTILE, 0.7f, 1.6f);
			} else {
				cooldown = k.shotGap;
				aiming = k.windup - 2; // the next round of a magazine comes quicker than the first
			}
		}

		private void move(LivingEntity target, Kind k, double dist, boolean sees) {
			if (k == Kind.SNIPER) {
				// a sniper holds his perch; only after 5 s without a shot does he go looking for one
				if (blindTicks > 100) {
					chase(target, 0.9);
				} else {
					halt();
				}
				return;
			}
			if (!sees || dist > k.maxDist) {
				chase(target, k == Kind.SHOTGUN ? 1.15 : 1.0);
				return;
			}
			if (k == Kind.SHOTGUN) {
				if (dist > 3.0) {
					chase(target, 1.1);
				} else {
					halt();
				}
				return;
			}
			halt();
			if (++strafeTicks >= 30) {
				strafeTicks = 0;
				if (mob.random.nextFloat() < 0.4f) {
					strafeLeft = !strafeLeft;
				}
			}
			float forward = dist < k.minDist ? -0.6f : (dist > k.maxDist * 0.8 ? 0.4f : 0.0f);
			mob.getMoveControl().strafe(forward, strafeLeft ? 0.45f : -0.45f);
		}
	}
}
