package com.projecthero.mod.hideout.mob;

import org.joml.Vector3f;

import com.projecthero.mod.carnage.entity.CarnageAttackEntity;
import com.projecthero.mod.carnage.entity.CarnageEntity;
import com.projecthero.mod.carnage.entity.CrimsonSpawnEntity;
import com.projecthero.mod.hideout.HideoutBoss;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: the two new hideout factions' rank and file, on the player model with a detailed skin per {@link Kind}
 * (CLAUDE.md: regular mobs are player models, GeckoLib bodies are for bosses):
 * <ul>
 * <li><b>Crimson Thralls</b> (Carnage Nest, entity {@code crimson_thrall}): people half-swallowed by Carnage's red
 * symbiote. Thrall (melee), Brute (big, heavy hits), Stalker (fast, leaps), Spitter (lobs Carnage's goo globs). Their
 * hits add a short Wither; fire hurts them half again as much.</li>
 * <li><b>Oath-bound Knights</b> (Oathbreaker Keep, entity {@code oath_knight}): the Oathbreaker's sworn men, still in
 * their soul-scorched kit. Knight (sword + shield), Squire (light, fast), Arbalist (crossbow bolts), Oathsworn (a big
 * greatsword knight whose hits set you alight with soul fire).</li>
 * </ul>
 * The kind is synced and saved ({@code Kind}); stats come from {@link #setKind}. No natural spawns: hideouts place them,
 * and the Oath knights also ride out on night patrols ({@link com.projecthero.mod.hideout.OathPatrols}).
 */
public class HideoutSoldier extends Monster implements RangedAttackMob {
	public enum Kind {
		THRALL, BRUTE, STALKER, SPITTER, KNIGHT, SQUIRE, ARBALIST, OATHSWORN;

		public boolean crimson() {
			return ordinal() <= SPITTER.ordinal();
		}

		public boolean ranged() {
			return this == SPITTER || this == ARBALIST;
		}

		/** {@code textures/entity/hideout/soldier/<skin>.png}. */
		public String skin() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		public static Kind byId(int id) {
			Kind[] v = values();
			return id >= 0 && id < v.length ? v[id] : THRALL;
		}
	}

	private static final EntityDataAccessor<Byte> DATA_KIND = SynchedEntityData.defineId(HideoutSoldier.class, EntityDataSerializers.BYTE);
	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75f, 0.04f, 0.06f), 0.9f);

	public HideoutSoldier(EntityType<? extends HideoutSoldier> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.ATTACK_DAMAGE, 6.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.ARMOR, 4.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_KIND, (byte) 0);
	}

	public Kind kind() {
		return Kind.byId(entityData.get(DATA_KIND));
	}

	/** Sets the kind and its stats / kit (call before the mob joins the level). */
	public void setKind(Kind kind) {
		entityData.set(DATA_KIND, (byte) kind.ordinal());
		double hp;
		double dmg;
		double speed;
		double armor;
		double scale = 1.0;
		switch (kind) {
			case BRUTE -> {
				hp = 60;
				dmg = 10;
				speed = 0.24;
				armor = 6;
				scale = 1.3;
			}
			case STALKER -> {
				hp = 26;
				dmg = 6;
				speed = 0.34;
				armor = 2;
			}
			case SPITTER -> {
				hp = 26;
				dmg = 4;
				speed = 0.26;
				armor = 2;
			}
			case KNIGHT -> {
				hp = 36;
				dmg = 7;
				speed = 0.26;
				armor = 8;
			}
			case SQUIRE -> {
				hp = 26;
				dmg = 5;
				speed = 0.31;
				armor = 4;
			}
			case ARBALIST -> {
				hp = 28;
				dmg = 4;
				speed = 0.26;
				armor = 5;
			}
			case OATHSWORN -> {
				hp = 70;
				dmg = 12;
				speed = 0.23;
				armor = 12;
				scale = 1.2;
			}
			default -> { // THRALL
				hp = 34;
				dmg = 7;
				speed = 0.28;
				armor = 3;
			}
		}
		base(Attributes.MAX_HEALTH, hp);
		base(Attributes.ATTACK_DAMAGE, dmg);
		base(Attributes.MOVEMENT_SPEED, speed);
		base(Attributes.ARMOR, armor);
		base(Attributes.SCALE, scale);
		base(Attributes.KNOCKBACK_RESISTANCE, kind == Kind.BRUTE || kind == Kind.OATHSWORN ? 0.6 : 0.1);
		setHealth(getMaxHealth());
		ItemStack main = switch (kind) {
			case KNIGHT -> new ItemStack(Items.IRON_SWORD);
			case SQUIRE -> new ItemStack(Items.STONE_SWORD);
			case ARBALIST -> new ItemStack(Items.CROSSBOW);
			case OATHSWORN -> new ItemStack(Items.NETHERITE_SWORD);
			default -> ItemStack.EMPTY;
		};
		setItemSlot(EquipmentSlot.MAINHAND, main);
		setItemSlot(EquipmentSlot.OFFHAND, kind == Kind.KNIGHT ? new ItemStack(Items.SHIELD) : ItemStack.EMPTY);
		setDropChance(EquipmentSlot.MAINHAND, 0f);
		setDropChance(EquipmentSlot.OFFHAND, 0f);
		refreshDimensions();
	}

	private void base(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> a, double v) {
		AttributeInstance i = getAttribute(a);
		if (i != null) {
			i.setBaseValue(v);
		}
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45f) {
			@Override
			public boolean canUse() {
				return kind() == Kind.STALKER && super.canUse();
			}
		});
		goalSelector.addGoal(3, new RangedAttackGoal(this, 1.0, 40, 60, 16f) {
			@Override
			public boolean canUse() {
				return kind().ranged() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return kind().ranged() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false) {
			@Override
			public boolean canUse() {
				return !kind().ranged() && super.canUse();
			}

			@Override
			public boolean canContinueToUse() {
				return !kind().ranged() && super.canContinueToUse();
			}
		});
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this, HideoutSoldier.class).setAlertOthers(HideoutSoldier.class));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	/** Same side: soldiers of one faction, and their bosses / Carnage's brood. */
	@Override
	public boolean isAlliedTo(Entity other) {
		if (other instanceof HideoutSoldier s) {
			return s.kind().crimson() == kind().crimson();
		}
		if (kind().crimson() && (other instanceof CarnageEntity || other instanceof CrimsonSpawnEntity)) {
			return true;
		}
		if (other instanceof HideoutBoss) {
			return true;
		}
		return super.isAlliedTo(other);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker != null && attacker != this && isAlliedTo(attacker)) {
			return false;
		}
		if (kind().crimson() && source.is(DamageTypeTags.IS_FIRE)) {
			amount *= 1.5f;
		}
		return super.hurt(source, amount);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean hit = super.doHurtTarget(target);
		if (hit && target instanceof LivingEntity l) {
			if (kind().crimson()) {
				l.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
			} else if (kind() == Kind.OATHSWORN) {
				l.igniteForSeconds(3f);
			}
		}
		return hit;
	}

	@Override
	public void performRangedAttack(LivingEntity target, float power) {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (kind() == Kind.SPITTER) {
			Vec3 from = position().add(0, getBbHeight() * 0.85, 0).add(getLookAngle().scale(0.6));
			CarnageAttackEntity.glob(server, this, from, target.position());
			playSound(SoundEvents.SLIME_ATTACK, 1.0f, 0.6f);
			return;
		}
		// the Arbalist: a heavy crossbow bolt
		Arrow arrow = new Arrow(server, this, new ItemStack(Items.ARROW), new ItemStack(Items.CROSSBOW));
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		arrow.setBaseDamage(3.5);
		double dx = target.getX() - getX();
		double dy = target.getY(0.33) - arrow.getY();
		double dz = target.getZ() - getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		arrow.shoot(dx, dy + flat * 0.12, dz, 2.4f, 4f);
		server.addFreshEntity(arrow);
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.HOSTILE, 1.0f, 0.9f);
	}

	@Override
	public void tick() {
		super.tick();
		if (level() instanceof ServerLevel server && kind().crimson() && tickCount % 10 == 0) {
			server.sendParticles(BLOOD, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.25, 0.3, 0.25, 0.0);
		}
		if (level() instanceof ServerLevel server && kind() == Kind.OATHSWORN && tickCount % 12 == 0) {
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0.3, 0.4, 0.3, 0.01);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return kind().crimson() ? SoundEvents.SLIME_SQUISH : null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return kind().crimson() ? SoundEvents.SLIME_HURT : SoundEvents.PLAYER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return kind().crimson() ? SoundEvents.SLIME_DEATH : SoundEvents.PLAYER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return kind().crimson() ? 0.7f : (kind() == Kind.OATHSWORN ? 0.75f : 0.9f);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("Kind", (byte) kind().ordinal());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_KIND, tag.getByte("Kind")); // stats are base values (saved by vanilla)
	}
}
