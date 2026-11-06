package com.projecthero.mod.omnitrix.alien.alienx;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;

import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "when the player transforms into alien x i want his body to remain in place, make alien x 2.5 blocks tall by
 * the way, but the player gets transported to that dimension where he talks to the two heads"): Alien X's body, left standing
 * where Ben transformed while Ben himself is in the Celestial Council's hall ({@link AlienXCouncil}). v0.17.2 round 4: the hall is a
 * mind palace, so he is really standing inside this body, hidden, and this is all the world sees of him. Frozen like the show: it
 * never moves on its own, cannot be hurt, pushed or blown away, is never saved, never falls out of the world, and removes itself
 * the moment its owner's council session is gone. Drawn with the Alien X skin, 2.5 blocks tall ({@code AlienXBodyRenderer}); {@link #ACT} is the tick of the
 * last carried motion (the body raises a hand and its eyes flare as reality bends round it).
 */
public class AlienXBodyEntity extends LivingEntity {
	/** 2.5 blocks tall (user), a player's proportions. */
	public static final float HEIGHT = 2.5f, SCALE = HEIGHT / 1.8f;

	public static final EntityType<AlienXBodyEntity> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("alien_x_body")),
			EntityType.Builder.<AlienXBodyEntity>of(AlienXBodyEntity::new, MobCategory.MISC)
					.sized(0.6f * SCALE, HEIGHT)
					.clientTrackingRange(10)
					.updateInterval(2)
					.noSave()
					.noSummon()
					.fireImmune()
					.build("alien_x_body"));

	private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(AlienXBodyEntity.class,
			EntityDataSerializers.OPTIONAL_UUID);
	public static final EntityDataAccessor<Integer> ACT = SynchedEntityData.defineId(AlienXBodyEntity.class, EntityDataSerializers.INT);

	public AlienXBodyEntity(EntityType<? extends LivingEntity> type, Level level) {
		super(type, level);
		setInvulnerable(true);
	}

	/** Called once from {@link AlienXKit#register} (the static initialiser registers the type). */
	static void initialize() {
		net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TYPE, createAttributes());
	}

	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(OWNER, Optional.empty());
		builder.define(ACT, -1000);
	}

	public void setOwner(UUID owner) {
		this.entityData.set(OWNER, Optional.of(owner));
	}

	public Optional<UUID> ownerId() {
		return this.entityData.get(OWNER);
	}

	/** A motion was carried: the hand comes up, the eyes flare. */
	public void act() {
		this.entityData.set(ACT, this.tickCount);
	}

	public int lastAct() {
		return this.entityData.get(ACT);
	}

	/** Where the body is looking (its eyes, along its facing and the pitch it was left at). */
	public Vec3 lookEnd(double range) {
		Vec3 eye = getEyePosition();
		Vec3 end = eye.add(getViewVector(1f).scale(range));
		var hit = level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, this));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hit.getLocation();
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0); // frozen in place (it may still settle onto the ground)
		if (getY() < level().getMinBuildHeight() + 2) {
			// standing over the void: it hangs there (its owner is held here, and the void is one of the few things that can kill him)
			setNoGravity(true);
			setDeltaMovement(Vec3.ZERO);
		}
		if (level() instanceof ServerLevel && this.tickCount % 5 == 0 && !AlienXCouncil.bodyLive(ownerId().orElse(null), getUUID())) {
			discard(); // the owner came back (or left the game): no orphans
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean canBeSeenAsEnemy() {
		return false;
	}

	@Override
	public boolean attackable() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return NonNullList.withSize(4, ItemStack.EMPTY);
	}

	@Override
	public Iterable<ItemStack> getHandSlots() {
		return Collections.emptyList();
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot slot) {
		return ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}
}
