package com.projecthero.mod.syndicate.entity;

import java.util.EnumSet;

import com.projecthero.mod.firearm.item.FirearmItems;
import com.projecthero.mod.syndicate.SyndicateGunfire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Shots are hit-scan ({@link SyndicateGunfire}); a raised shield blocks them.
 */
public class SyndicateGunman extends SyndicateCriminal {
	public enum Kind {
		PISTOL(SyndicateSkin.GUNMAN, 22.0, 4.0f, 0.035, 1, 6, 6, 18, 50, 5.0, 14.0),
		SHOTGUN(SyndicateSkin.MASKED, 12.0, 2.5f, 0.11, 6, 10, 1, 0, 30, 0.0, 6.0),
		SNIPER(SyndicateSkin.HOOD, 48.0, 12.0f, 0.004, 1, 30, 1, 0, 70, 0.0, 48.0);

		final SyndicateSkin skin;
		final double range;
		final float damage;
		final double spread;
		final int pellets;
		final int windup;
		final int magazine;
		final int shotGap;
		final int reload;
		final double minDist;
		final double maxDist;

		Kind(SyndicateSkin skin, double range, float damage, double spread, int pellets, int windup, int magazine, int shotGap,
				int reload, double minDist, double maxDist) {
			this.skin = skin;
			this.range = range;
			this.damage = damage;
			this.spread = spread;
			this.pellets = pellets;
			this.windup = windup;
			this.magazine = magazine;
			this.shotGap = shotGap;
			this.reload = reload;
			this.minDist = minDist;
			this.maxDist = maxDist;
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

	public SyndicateGunman(EntityType<? extends SyndicateGunman> type, Level level) {
		super(type, level);
		this.xpReward = 8;
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
			mob.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
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
				SyndicateGunfire.laser(level, mob, aimAt);
				if (aiming == 1) {
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SPYGLASS_USE, SoundSource.HOSTILE, 1.2f, 0.7f);
				}
			}
			if (aiming < k.windup) {
				return;
			}
			SyndicateGunfire.fire(level, mob, aimAt, k.range, k.damage, k.spread, k.pellets, k.report());
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
					mob.getNavigation().moveTo(target, 0.9);
				} else {
					mob.getNavigation().stop();
				}
				return;
			}
			if (!sees || dist > k.maxDist) {
				mob.getNavigation().moveTo(target, k == Kind.SHOTGUN ? 1.15 : 1.0);
				return;
			}
			if (k == Kind.SHOTGUN) {
				if (dist > 3.0) {
					mob.getNavigation().moveTo(target, 1.1);
				} else {
					mob.getNavigation().stop();
				}
				return;
			}
			mob.getNavigation().stop();
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
