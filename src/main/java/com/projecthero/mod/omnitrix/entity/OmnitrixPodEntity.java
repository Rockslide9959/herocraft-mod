package com.projecthero.mod.omnitrix.entity;

import org.jetbrains.annotations.Nullable;
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
 * v0.17.1: the Omnitrix's pod in its crater -- the show's capsule (a dark gunmetal sphere banded by curved seams, the round hub
 * where its halves meet; drawn by {@code OmnitrixRenderers.Pod}).
 * <ul>
 *   <li><b>Hot</b>: it smokes for {@link OmnitrixConfig#POD_OPEN_DELAY} ticks after landing.</li>
 *   <li><b>Shut</b> until a player right-clicks it (v0.17.1 user: "it must ONLY open when the player right-clicks it"): then the
 *       top half hinges back over {@link OmnitrixConfig#POD_LID_TICKS} with a hiss, showing the dark interior and the watch
 *       glowing green inside.</li>
 *   <li><b>The leap</b>: once open, the first player (not already wearing it) within {@link OmnitrixConfig#POD_LATCH_RANGE}
 *       blocks makes the watch's cuff spring open and the watch leap at them -- an arc of {@link OmnitrixConfig#POD_JUMP_TICKS}
 *       onto their left wrist ({@link #jumpTarget}, drawn client-side). On landing it latches ({@code Omnitrix.latch}: the
 *       power, the user's activation sound and the shake-off). A refused grant (unique powers) sends it back into the pod.</li>
 *   <li><b>Empty</b>: the husk crumbles a minute later.</li>
 * </ul>
 * Saved, indestructible (except by {@code /kill}).
 */
public class OmnitrixPodEntity extends Entity {
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(OmnitrixPodEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> EMPTY = SynchedEntityData.defineId(OmnitrixPodEntity.class, EntityDataSerializers.BOOLEAN);
	/** v0.17.1: the age the lid was opened at (-1 = still shut). */
	private static final EntityDataAccessor<Integer> OPEN_AT = SynchedEntityData.defineId(OmnitrixPodEntity.class, EntityDataSerializers.INT);
	/** v0.17.1: the age the watch leapt at (-1 = none) and at whom (an entity id). */
	private static final EntityDataAccessor<Integer> JUMP_AT = SynchedEntityData.defineId(OmnitrixPodEntity.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(OmnitrixPodEntity.class, EntityDataSerializers.INT);
	private static final DustParticleOptions GREEN = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.3f), 1.2f);
	/** The empty husk crumbles this long after the watch left it. */
	private static final int HUSK_TICKS = 20 * 60;
	/** A leap whose grant was refused: the watch waits this long before trying anyone again. */
	private static final int RETRY_TICKS = 40;

	private int emptyFor;
	private int retryAt;
	/** Client: the age counted locally (smooth between the server's updates, re-synced when it drifts). */
	private int clientAge;

	public OmnitrixPodEntity(EntityType<? extends OmnitrixPodEntity> type, Level level) {
		super(type, level);
	}

	/** Sets the pod down at {@code at}, its front hub turned toward the nearest player. */
	public static OmnitrixPodEntity place(ServerLevel level, Vec3 at) {
		OmnitrixPodEntity pod = OmnitrixEntities.OMNITRIX_POD.create(level);
		if (pod == null) {
			return null;
		}
		float yaw = level.random.nextFloat() * 360f;
		Player near = level.getNearestPlayer(at.x, at.y, at.z, 96, false);
		if (near != null) {
			yaw = (float) Math.toDegrees(Math.atan2(near.getZ() - at.z, near.getX() - at.x)) - 90f;
		}
		pod.moveTo(at.x, at.y, at.z, yaw, 0f);
		level.addFreshEntity(pod);
		return pod;
	}

	public int age() {
		return entityData.get(AGE);
	}

	/** The age for drawing (the client's smooth count). */
	public float age(float partial) {
		return (level().isClientSide ? clientAge : age()) + partial;
	}

	/** v0.17.1: the lid has been opened (it may still be swinging up). */
	public boolean opened() {
		return entityData.get(OPEN_AT) >= 0;
	}

	/** v0.17.1: the lid is fully up (the watch can leap). */
	public boolean open() {
		return opened() && age() - entityData.get(OPEN_AT) >= OmnitrixConfig.POD_LID_TICKS;
	}

	public boolean empty() {
		return entityData.get(EMPTY);
	}

	public boolean hot() {
		return age() < OmnitrixConfig.POD_OPEN_DELAY;
	}

	/** 0 shut .. 1 fully open (the lid swings over {@link OmnitrixConfig#POD_LID_TICKS}), for the renderer. */
	public float openAmount(float partial) {
		int at = entityData.get(OPEN_AT);
		return at < 0 ? 0f : Math.max(0f, Math.min(1f, (age(partial) - at) / OmnitrixConfig.POD_LID_TICKS));
	}

	/** v0.17.1: ticks since the watch leapt ({@code -1} when it has not). */
	public float jumpTime(float partial) {
		int at = entityData.get(JUMP_AT);
		return at < 0 ? -1f : age(partial) - at;
	}

	/** v0.17.1: the player the watch is leaping at (null when none). */
	@Nullable
	public Player jumpTarget() {
		int id = entityData.get(TARGET);
		return entityData.get(JUMP_AT) < 0 || id < 0 ? null : level().getEntity(id) instanceof Player p ? p : null;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(AGE, 0);
		builder.define(EMPTY, false);
		builder.define(OPEN_AT, -1);
		builder.define(JUMP_AT, -1);
		builder.define(TARGET, -1);
	}

	@Override
	public void tick() {
		super.tick();
		if (!onGround()) {
			setDeltaMovement(getDeltaMovement().add(0, -0.08, 0).scale(0.9));
			move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
		}
		if (!(level() instanceof ServerLevel server)) {
			clientAge++;
			if (Math.abs(clientAge - age()) > 3) {
				clientAge = age();
			}
			return;
		}
		int age = age() + 1;
		entityData.set(AGE, Math.min(age, 1000000));
		if (age < OmnitrixConfig.POD_OPEN_DELAY && age % 3 == 0) {
			// still hot from the fall
			server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.9, getZ(), 2, 0.3, 0.25, 0.3, 0.02);
			if (age % 9 == 0) {
				server.sendParticles(ParticleTypes.LAVA, getX(), getY() + 0.6, getZ(), 1, 0.3, 0.2, 0.3, 0.0);
			}
		} else if (age % 12 == 0 && !opened()) {
			server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 1, 0.25, 0.1, 0.25, 0.01);
		}
		if (empty()) {
			if (++emptyFor > HUSK_TICKS) {
				server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 20, 0.4, 0.4, 0.4, 0.02);
				discard();
			}
			return;
		}
		if (!opened()) {
			return;
		}
		int since = age - entityData.get(OPEN_AT);
		if (since == OmnitrixConfig.POD_LID_TICKS) {
			server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.9, getZ(), 12, 0.35, 0.2, 0.35, 0.02);
		}
		if (since < OmnitrixConfig.POD_LID_TICKS) {
			return;
		}
		if (age % 5 == 0 && entityData.get(JUMP_AT) < 0) {
			server.sendParticles(GREEN, getX(), getY() + 0.7, getZ(), 2, 0.2, 0.15, 0.2, 0.0);
		}
		if (age % 40 == 0 && entityData.get(JUMP_AT) < 0) {
			OmnitrixSounds.play(server, position(), OmnitrixSounds.POD_BEACON, 1.0f, 1.0f, SoundSource.BLOCKS);
		}
		int jumpAt = entityData.get(JUMP_AT);
		if (jumpAt < 0) {
			ServerPlayer who = age >= retryAt ? nearestTaker(server) : null;
			if (who != null) {
				entityData.set(JUMP_AT, age);
				entityData.set(TARGET, who.getId());
				OmnitrixSounds.play(server, position().add(0, 0.6, 0), OmnitrixSounds.POD_JUMP, 1.0f, 1.0f, SoundSource.BLOCKS);
				server.sendParticles(GREEN, getX(), getY() + 0.7, getZ(), 16, 0.2, 0.1, 0.2, 0.0);
			}
			return;
		}
		if (age - jumpAt < OmnitrixConfig.POD_JUMP_TICKS) {
			return;
		}
		// the landing: on the wrist, or (refused / gone) back into the pod
		Entity t = server.getEntity(entityData.get(TARGET));
		boolean ok = t instanceof ServerPlayer sp && sp.isAlive() && !sp.isSpectator() && sp.distanceToSqr(this) < 12 * 12 && latchOnto(sp);
		if (!ok) {
			entityData.set(JUMP_AT, -1);
			entityData.set(TARGET, -1);
			retryAt = age + RETRY_TICKS;
		}
	}

	/** The nearest player who could take the watch, within {@link OmnitrixConfig#POD_LATCH_RANGE} of the pod's centre. */
	@Nullable
	private ServerPlayer nearestTaker(ServerLevel server) {
		Vec3 c = position().add(0, 0.5, 0);
		double r = OmnitrixConfig.POD_LATCH_RANGE;
		ServerPlayer best = null;
		double bestD = r * r;
		for (Player pl : server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(r + 1.0))) {
			if (!(pl instanceof ServerPlayer sp) || sp.isSpectator() || !sp.isAlive() || Omnitrix.hasPower(sp)) {
				continue;
			}
			// to the nearest point of the body (feet to head), so a player standing over the pod counts
			double y = Math.max(sp.getY(), Math.min(c.y, sp.getY() + sp.getBbHeight()));
			double d = c.distanceToSqr(sp.getX(), y, sp.getZ());
			if (d <= bestD) {
				bestD = d;
				best = sp;
			}
		}
		return best;
	}

	/**
	 * The watch lands on {@code p} at once (the leap's end; gametests). True if it latched (the pod is empty from then on).
	 */
	public boolean latchOnto(ServerPlayer p) {
		if (empty() || !open() || Omnitrix.hasPower(p)) {
			return false;
		}
		if (!OmnitrixWorld.putOnLoose(p, true)) {
			return false;
		}
		entityData.set(EMPTY, true);
		entityData.set(TARGET, p.getId());
		ServerLevel server = (ServerLevel) level();
		Vec3 wrist = p.position().add(0, 0.95, 0);
		server.sendParticles(GREEN, wrist.x, wrist.y, wrist.z, 24, 0.25, 0.25, 0.25, 0.0);
		server.sendParticles(ParticleTypes.END_ROD, wrist.x, wrist.y, wrist.z, 6, 0.1, 0.1, 0.1, 0.06);
		return true;
	}

	/** v0.17.1: the right-click that opens the lid. True if it opened now. */
	public boolean openLid(ServerPlayer by) {
		if (opened() || empty()) {
			return false;
		}
		entityData.set(OPEN_AT, age());
		ServerLevel server = (ServerLevel) level();
		OmnitrixSounds.play(server, position(), OmnitrixSounds.POD_OPEN, 1.2f, 1.0f, SoundSource.BLOCKS);
		server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.75, getZ(), 24, 0.45, 0.1, 0.45, 0.04);
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp) {
			return openLid(sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		return !opened() && !empty() ? InteractionResult.SUCCESS : InteractionResult.PASS;
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
		// v0.17.1: an old save's pod (no lid field) is shut until right-clicked; a leap in flight is never saved
		entityData.set(OPEN_AT, tag.contains("open_at") ? tag.getInt("open_at") : -1);
		emptyFor = tag.getInt("empty_for");
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putInt("pod_age", age());
		tag.putBoolean("pod_empty", empty());
		tag.putInt("open_at", entityData.get(OPEN_AT));
		tag.putInt("empty_for", emptyFor);
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 160 * 160;
	}

	/** Not a solid obstacle for movement. */
	@Override
	public boolean isPushable() {
		return false;
	}
}
