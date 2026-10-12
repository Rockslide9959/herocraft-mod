package com.projecthero.mod.frostgiant.entity;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.frostgiant.FrostGiantAssault;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.1: a block of ice round something -- three uses, one entity.
 * <ul>
 *   <li>{@link #PRISON} -- Laufey's <b>Frozen Grasp</b>: a fighter is encased (can't move or attack) for a few seconds.
 *       The prisoner cracks it by mashing attack (one crack a hit); allies break it faster (two cracks a hit). Broken
 *       quickly enough, it staggers Laufey.</li>
 *   <li>{@link #WARD} -- a Frost Shaman's <b>Rime Ward</b> round a wounded giant: it is invulnerable inside and healed
 *       when the ice melts -- unless the fighters break the ice first.</li>
 *   <li>{@link #CASKET} -- the Casket of Ancient Winters' <b>Ice Prison</b> on a mob.</li>
 * </ul>
 * It follows its prisoner, is drawn by the client as a translucent ice block sized to its hit-box, and is never saved
 * ({@link #shouldBeSaved}) -- a reload simply frees whoever was inside. Who is imprisoned is tracked in {@link #IMPRISONED}
 * (cleared by {@code ServerStateReset}) so their attacks can be cancelled.
 */
public class IcePrisonEntity extends Entity {
	public static final byte PRISON = 0, WARD = 1, CASKET = 2;

	private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(IcePrisonEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(IcePrisonEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> DATA_CRACK = SynchedEntityData.defineId(IcePrisonEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(IcePrisonEntity.class, EntityDataSerializers.BYTE);

	/** Everything inside a prison right now (server). Static so damage hooks can cancel prisoners' attacks cheaply. */
	private static final Set<UUID> IMPRISONED = Collections.synchronizedSet(new HashSet<>());

	private UUID prisoner;
	private UUID assaultId;
	private int duration = 100;
	private int hitsNeeded = 8;
	private int hits;
	private boolean fromGrasp;
	private Vec3 anchor;

	public IcePrisonEntity(EntityType<? extends IcePrisonEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_WIDTH, 1.4f);
		builder.define(DATA_HEIGHT, 2.4f);
		builder.define(DATA_CRACK, 0f);
		builder.define(DATA_MODE, PRISON);
	}

	/** Encase {@code target}: {@code mode}, for {@code ticks}, broken after {@code hitsNeeded} cracks. */
	public static IcePrisonEntity encase(ServerLevel level, LivingEntity target, byte mode, int ticks, int hitsNeeded, UUID assaultId,
			boolean fromGrasp) {
		IcePrisonEntity prison = com.projecthero.mod.frostgiant.FrostGiantEntityTypes.ICE_PRISON.create(level);
		if (prison == null) {
			return null;
		}
		prison.prisoner = target.getUUID();
		prison.assaultId = assaultId;
		prison.duration = ticks;
		prison.hitsNeeded = Math.max(1, hitsNeeded);
		prison.fromGrasp = fromGrasp;
		prison.anchor = target.position();
		prison.entityData.set(DATA_MODE, mode);
		prison.entityData.set(DATA_WIDTH, target.getBbWidth() + 0.5f);
		prison.entityData.set(DATA_HEIGHT, target.getBbHeight() + 0.4f);
		prison.refreshDimensions();
		prison.moveTo(target.getX(), target.getY() - 0.1, target.getZ(), 0f, 0f);
		level.addFreshEntity(prison);
		IMPRISONED.add(target.getUUID());
		target.setDeltaMovement(Vec3.ZERO);
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_PLACE, SoundSource.HOSTILE, 1.6f, 0.5f);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.HOSTILE, 1.2f, 0.7f);
		FrostFx.shatter(level, target.position().add(0, target.getBbHeight() * 0.5, 0), 0.6);
		if (target instanceof ServerPlayer sp && mode == PRISON) {
			sp.displayClientMessage(Component.translatable("message.projecthero.frost_giants.imprisoned").withStyle(ChatFormatting.AQUA), true);
		}
		return prison;
	}

	/** Is {@code e} inside an ice prison (server)? */
	public static boolean isImprisoned(Entity e) {
		return e != null && IMPRISONED.contains(e.getUUID());
	}

	public static void clearSessionState() {
		IMPRISONED.clear();
	}

	public byte mode() {
		return entityData.get(DATA_MODE);
	}

	public float crack() {
		return entityData.get(DATA_CRACK);
	}

	public UUID prisoner() {
		return prisoner;
	}

	public int hits() {
		return hits;
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return EntityDimensions.scalable(entityData.get(DATA_WIDTH), entityData.get(DATA_HEIGHT));
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key)) {
			refreshDimensions();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		Entity inside = prisoner == null ? null : server.getEntity(prisoner);
		if (!(inside instanceof LivingEntity living) || !living.isAlive() || living.isRemoved()) {
			release(false);
			return;
		}
		if (assaultId != null && tickCount % 20 == 0 && FrostGiantAssault.find(server, assaultId) == null) {
			release(false);
			return;
		}
		// hold them where they were caught
		if (anchor == null) {
			anchor = living.position();
		}
		if (living.position().distanceToSqr(anchor) > 0.04) {
			if (living instanceof ServerPlayer sp) {
				sp.teleportTo(anchor.x, anchor.y, anchor.z);
			} else {
				living.setPos(anchor.x, anchor.y, anchor.z);
			}
		}
		living.setDeltaMovement(0, Math.min(0, living.getDeltaMovement().y), 0);
		if (living instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (mode() != WARD && tickCount % 10 == 0) {
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 6, false, false));
			FrostCombat.freeze(living, 12);
		}
		setPos(anchor.x, anchor.y - 0.1, anchor.z);
		if (tickCount % 8 == 0) {
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() * 0.5, getZ(), 2,
					getBbWidth() * 0.4, getBbHeight() * 0.4, getBbWidth() * 0.4, 0.0);
		}
		if (tickCount >= duration) {
			release(false);
		}
	}

	/**
	 * Lets the prisoner go. {@code broken} = cracked open by hits (shatters loudly; a quick enough break of Laufey's grasp
	 * staggers him) rather than melted away.
	 */
	public void release(boolean broken) {
		if (isRemoved()) {
			return;
		}
		if (prisoner != null) {
			IMPRISONED.remove(prisoner);
		}
		if (level() instanceof ServerLevel server) {
			FrostFx.shatter(server, position().add(0, getBbHeight() * 0.5, 0), Math.max(0.6, getBbHeight() * 0.35));
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, broken ? 1.8f : 1.0f, broken ? 0.7f : 1.1f);
			if (broken && mode() == WARD && prisoner != null && server.getEntity(prisoner) instanceof FrostGiantEntity giant) {
				giant.breakWard();
			}
			if (broken && fromGrasp && assaultId != null) {
				FrostGiantAssault a = FrostGiantAssault.find(server, assaultId);
				if (a != null) {
					a.onGraspBroken(server, tickCount);
				}
			}
		}
		discard();
	}

	// ---------------------------------------------------------------- breaking it

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isAttackable() {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (level().isClientSide() || isRemoved()) {
			return false;
		}
		Entity attacker = source.getEntity();
		if (attacker == null || FrostCombat.isFrost(attacker)) {
			return false;
		}
		int add;
		if (prisoner != null && prisoner.equals(attacker.getUUID())) {
			add = 1; // mashing from inside
		} else if (attacker instanceof Player || attacker instanceof LivingEntity) {
			add = 2; // an ally on the outside
		} else {
			return false;
		}
		hits += add;
		entityData.set(DATA_CRACK, Math.min(1f, hits / (float) hitsNeeded));
		if (level() instanceof ServerLevel server) {
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_HIT, SoundSource.HOSTILE, 1.2f, 0.6f + hits * 0.05f);
			server.sendParticles(FrostFx.ICE, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, getBbWidth() * 0.4, getBbHeight() * 0.3,
					getBbWidth() * 0.4, 0.1);
		}
		if (hits >= hitsNeeded) {
			release(true);
		}
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public void remove(RemovalReason reason) {
		if (prisoner != null && !level().isClientSide()) {
			IMPRISONED.remove(prisoner);
		}
		super.remove(reason);
	}
}
