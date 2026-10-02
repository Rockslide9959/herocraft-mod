package com.projecthero.mod.hero.revamp.d;

import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 (Light / Invisibility, V): a Mirror Image -- a refracted copy of its caster. It wears the caster's skin
 * (drawn by the client with the owner's own player skin) and a copy of their visible gear, wanders off on its own,
 * and lures hostile mobs that were after the caster onto itself. Any hit shatters it in a burst of light that
 * blinds whoever struck it. It never saves, never drops anything, and lives {@link #LIFETIME} ticks at most.
 *
 * <p>The gear it shows is stamped as an illusion ({@link #ILLUSION_TAG}); an illusion stack that ever reaches the
 * world as an item entity is deleted on load (see {@code BatchDContent}), so a decoy can never be farmed for items.
 */
public class MirrorImageEntity extends PathfinderMob {
	public static final int LIFETIME = 10 * 20;
	public static final String ILLUSION_TAG = "projecthero_illusion";
	private static final double LURE_RANGE = 16.0;

	private static final EntityDataAccessor<Optional<UUID>> OWNER =
			SynchedEntityData.defineId(MirrorImageEntity.class, EntityDataSerializers.OPTIONAL_UUID);

	private int life;

	public MirrorImageEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			setDropChance(slot, 0.0f);
		}
		setCanPickUpLoot(false);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 2.0)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 1.15, 1.0f));
		goalSelector.addGoal(2, new RandomLookAroundGoal(this));
	}

	public Optional<UUID> ownerId() {
		return entityData.get(OWNER);
	}

	/** Binds this image to {@code owner} and dresses it in illusion copies of their visible gear. */
	public void copyFrom(ServerPlayer owner) {
		entityData.set(OWNER, Optional.of(owner.getUUID()));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot == EquipmentSlot.BODY) {
				continue;
			}
			ItemStack worn = owner.getItemBySlot(slot);
			setItemSlot(slot, worn.isEmpty() ? ItemStack.EMPTY : illusion(worn));
		}
		setCustomName(owner.getName());
		setCustomNameVisible(false);
	}

	/** A display-only copy of {@code stack}, stamped so it is deleted if it ever becomes an item in the world. */
	public static ItemStack illusion(ItemStack stack) {
		ItemStack copy = stack.copyWithCount(1);
		CustomData.update(DataComponents.CUSTOM_DATA, copy, tag -> tag.putBoolean(ILLUSION_TAG, true));
		return copy;
	}

	public static boolean isIllusion(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean(ILLUSION_TAG);
	}

	public ServerPlayer owner() {
		if (!(level() instanceof ServerLevel sl)) {
			return null;
		}
		return ownerId().map(id -> sl.getServer().getPlayerList().getPlayer(id)).orElse(null);
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			return;
		}
		life++;
		ServerPlayer owner = owner();
		if (life > LIFETIME || owner == null || !owner.isAlive() || owner.level() != level()) {
			shatter(null);
			return;
		}
		if (life % 10 == 0) {
			lure(owner);
		}
		if (life % 5 == 0 && level() instanceof ServerLevel sl) {
			sl.sendParticles(BatchDFx.LIGHT, getX(), getY() + 1.0, getZ(), 2, 0.3, 0.5, 0.3, 0.0);
		}
		if (life > LIFETIME - 30 && life % 4 == 0 && level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.0, getZ(), 3, 0.3, 0.6, 0.3, 0.01);
		}
	}

	/** Hostile mobs that are after the caster (or idle and close) turn on this image instead, most of the time. */
	private void lure(ServerPlayer owner) {
		for (Mob mob : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(LURE_RANGE),
				m -> com.projecthero.mod.combat.HeroTargets.isHostile(owner, m) && !(m instanceof MirrorImageEntity))) {
			LivingEntity target = mob.getTarget();
			boolean afterOwner = target == owner;
			boolean idleNear = target == null && mob.distanceToSqr(this) < 10.0 * 10.0;
			if ((afterOwner || idleNear) && random.nextFloat() < 0.65f) {
				mob.setTarget(this);
			}
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide || isRemoved()) {
			return false;
		}
		if (isInvulnerableTo(source)) {
			return false;
		}
		shatter(source.getEntity() instanceof LivingEntity le ? le : null);
		return true;
	}

	/** The image bursts into light; whoever struck it (never the caster or their squad) is blinded briefly. */
	public void shatter(LivingEntity attacker) {
		if (isRemoved()) {
			return;
		}
		if (level() instanceof ServerLevel sl) {
			Vec3 c = BatchDFx.centre(this);
			sl.sendParticles(BatchDFx.LIGHT, c.x, c.y, c.z, 30, 0.35, 0.6, 0.35, 0.05);
			sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 16, 0.3, 0.5, 0.3, 0.08);
			sl.sendParticles(BatchDFx.PRISM, c.x, c.y, c.z, 12, 0.4, 0.6, 0.4, 0.0);
			sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.0f, 1.5f);
			ServerPlayer owner = owner();
			if (attacker != null && attacker != owner && attacker.isAlive()
					&& (owner == null || !Squads.areAllies(owner, attacker))
					&& attacker.distanceToSqr(this) < 6.0 * 6.0) {
				AbilityHelpers.applyControl(attacker, MobEffects.BLINDNESS, 60, 0);
				if (attacker instanceof Mob mob && mob.getTarget() == this) {
					mob.setTarget(null);
				}
			}
		}
		discard();
	}

	// ---- never a real creature: no loot, no xp, no pickup, no leash, no despawn surprises ----

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
	}

	@Override
	protected void dropFromLootTable(DamageSource source, boolean recentlyHit) {
	}

	@Override
	protected int getBaseExperienceReward() {
		return 0;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		return false;
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
		if (other instanceof Player p && ownerId().map(p.getUUID()::equals).orElse(false)) {
			return true;
		}
		return super.isAlliedTo(other);
	}
}
