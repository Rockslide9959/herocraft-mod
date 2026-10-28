package com.projecthero.mod.omnitrix.entity;

import org.joml.Vector3f;

import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixConfig;
import com.projecthero.mod.omnitrix.OmnitrixEntities;
import com.projecthero.mod.omnitrix.OmnitrixSounds;
import com.projecthero.mod.omnitrix.OmnitrixWorld;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Omnitrix's pod in its crater. It smokes for {@link OmnitrixConfig#POD_OPEN_DELAY} ticks, then its halves hiss
 * open and the watch inside glows; the first player who touches it (walks into it or right-clicks it) gets the watch -- it
 * jumps onto their wrist ({@code Omnitrix.grant}: it replaces whatever power they had and locks). The empty husk crumbles a
 * while later. Saved, indestructible (except by {@code /kill}).
 */
public class OmnitrixPodEntity extends Entity {
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(OmnitrixPodEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> EMPTY = SynchedEntityData.defineId(OmnitrixPodEntity.class, EntityDataSerializers.BOOLEAN);
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.3f), 1.2f);
	/** The empty husk crumbles this long after the watch left it. */
	private static final int HUSK_TICKS = 20 * 60;

	private int emptyFor;

	public OmnitrixPodEntity(EntityType<? extends OmnitrixPodEntity> type, Level level) {
		super(type, level);
	}

	public static OmnitrixPodEntity place(ServerLevel level, Vec3 at) {
		OmnitrixPodEntity pod = OmnitrixEntities.OMNITRIX_POD.create(level);
		if (pod == null) {
			return null;
		}
		pod.moveTo(at.x, at.y, at.z, level.random.nextFloat() * 360f, 0f);
		level.addFreshEntity(pod);
		return pod;
	}

	public int age() {
		return entityData.get(AGE);
	}

	public boolean open() {
		return age() >= OmnitrixConfig.POD_OPEN_DELAY;
	}

	public boolean empty() {
		return entityData.get(EMPTY);
	}

	/** 0 shut .. 1 fully open (the halves swing over 20 ticks), for the renderer. */
	public float openAmount(float partial) {
		return Math.max(0f, Math.min(1f, (age() + partial - OmnitrixConfig.POD_OPEN_DELAY) / 20f));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(AGE, 0);
		builder.define(EMPTY, false);
	}

	@Override
	public void tick() {
		super.tick();
		if (!onGround()) {
			setDeltaMovement(getDeltaMovement().add(0, -0.08, 0).scale(0.9));
			move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
		}
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		int age = age() + 1;
		entityData.set(AGE, Math.min(age, 100000));
		if (age < OmnitrixConfig.POD_OPEN_DELAY) {
			if (age % 4 == 0) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.8, getZ(), 2, 0.3, 0.3, 0.3, 0.02);
			}
			return;
		}
		if (age == OmnitrixConfig.POD_OPEN_DELAY) {
			server.playSound(null, getX(), getY(), getZ(), OmnitrixSounds.POD_OPEN, SoundSource.BLOCKS, 1.5f, 1.0f);
			server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 30, 0.5, 0.4, 0.5, 0.05);
		}
		if (empty()) {
			if (++emptyFor > HUSK_TICKS) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 20, 0.4, 0.4, 0.4, 0.02);
				discard();
			}
			return;
		}
		if (age % 5 == 0) {
			server.sendParticles(GREEN, getX(), getY() + 0.7, getZ(), 2, 0.2, 0.15, 0.2, 0.0);
		}
		if (age % 40 == 0) {
			server.playSound(null, getX(), getY(), getZ(), OmnitrixSounds.WARNING, SoundSource.BLOCKS, 0.4f, 1.6f);
		}
		for (Player pl : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.35))) {
			if (pl instanceof ServerPlayer sp && !sp.isSpectator() && touch(sp)) {
				return;
			}
		}
	}

	/** The watch jumps onto {@code p}. True if it did (the pod is empty from then on). */
	public boolean touch(ServerPlayer p) {
		if (empty() || !open() || Omnitrix.hasPower(p)) {
			return false;
		}
		if (!OmnitrixWorld.putOnLoose(p)) {
			return false;
		}
		entityData.set(EMPTY, true);
		ServerLevel server = (ServerLevel) level();
		Vec3 from = position().add(0, 0.7, 0);
		Vec3 to = p.position().add(0, 1.0, 0);
		for (int i = 0; i <= 12; i++) {
			Vec3 at = from.lerp(to, i / 12.0).add(0, Math.sin(i / 12.0 * Math.PI) * 0.8, 0);
			server.sendParticles(GREEN, at.x, at.y, at.z, 3, 0.05, 0.05, 0.05, 0.0);
		}
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			return touch(sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return open() && !empty() ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		entityData.set(AGE, tag.getInt("pod_age"));
		entityData.set(EMPTY, tag.getBoolean("pod_empty"));
		emptyFor = tag.getInt("empty_for");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putInt("pod_age", age());
		tag.putBoolean("pod_empty", empty());
		tag.putInt("empty_for", emptyFor);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160 * 160;
	}

	/** Not a solid obstacle for movement (touching it is the point). */
	@Override
	public boolean isPushable() {
		return false;
	}
}
