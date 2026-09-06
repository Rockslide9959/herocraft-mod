package com.projecthero.mod.maxsteel.entity;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.maxsteel.MaxSteelBonding;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Steel -- the alien Ultralink that bonds with a player to grant the Max Steel Hero-Tier power. Found
 * floating at the centre of a {@code steel_crash_site}.
 *
 * <h2>Deliberately not a mob</h2>
 * It extends {@link Entity} directly, not {@code Mob}: no pathfinding, no goals, no despawn timer, no
 * loot table. All it does is hover in place, bob gently, turn slowly toward the nearest player, trail
 * a few cyan particles, and respond to a right-click by trying to bond. That is exactly the "simple
 * floating/idle logic is enough" the brief asks for, and it is why Steel costs almost nothing to tick.
 *
 * <h2>Indestructible until bonded</h2>
 * It ignores every damage source, does not burn, drown, or take fall/void damage, and cannot be
 * pushed. It only leaves the world when it successfully bonds ({@link #discard()} from
 * {@link MaxSteelBonding}) or an operator removes it. A natural crash-site Steel also does not despawn,
 * so a player can always come back for it.
 *
 * <h2>Bonding is server-authoritative and single-claim</h2>
 * {@link #bondingPlayer} is a soft lock held for the duration of the ~4s first-bond cut-scene: while
 * it is set to someone else, a second player's right-click is refused. The lock clears itself if that
 * player logs out or wanders off (see {@link #tick()}).
 */
public class SteelEntity extends Entity implements GeoEntity {
	private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.steel_entity.idle");

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	/** Y the Steel hovers around -- the bob oscillates about this. Set on spawn, persisted. */
	private double hoverY = Double.NaN;
	/** The player currently in the bonding cut-scene, or null. A soft lock, not persisted. */
	private UUID bondingPlayer;
	/** Game-time the current bond lock expires (failsafe if the player vanishes mid-scene). */
	private long bondingExpiresAt;
	/** Per-player interaction cooldown so Steel can't be spammed. Not persisted. */
	private final java.util.Map<UUID, Long> interactCooldown = new java.util.HashMap<>();

	public SteelEntity(EntityType<? extends SteelEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	// ---------------- Entity plumbing ----------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// Nothing synced beyond position/rotation -- the visual is uniform and bonding is server-only.
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		if (tag.contains("HoverY")) {
			hoverY = tag.getDouble("HoverY");
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		if (!Double.isNaN(hoverY)) {
			tag.putDouble("HoverY", hoverY);
		}
	}

	@Override
	public boolean isPickable() {
		return true; // so a right-click ray hits it
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true; // indestructible until it bonds
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}

	/** Natural crash-site Steel never despawns; nothing here starts a despawn timer anyway. */
	@Override
	public boolean shouldBeSaved() {
		return true;
	}

	// ---------------- behaviour ----------------

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(Vec3.ZERO);

		if (Double.isNaN(hoverY)) {
			hoverY = getY();
		}

		Level level = level();
		Player nearest = level.getNearestPlayer(this, 24.0);

		// gentle bob about the hover height
		double bob = Math.sin((tickCount + getId() * 13) * 0.08) * 0.14;
		double targetY = hoverY + bob;
		setPos(getX(), targetY, getZ());

		// slow turn toward the nearest player
		if (nearest != null) {
			double dx = nearest.getX() - getX();
			double dz = nearest.getZ() - getZ();
			float wanted = (float) (Math.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
			setYRot(approachAngle(getYRot(), wanted, 4.0f));
		}
		this.yRotO = getYRot();

		if (!(level instanceof ServerLevel server)) {
			return;
		}

		if (tickCount % 6 == 0) {
			server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					getX(), getY() + 1.4, getZ(), 1, 0.28, 0.35, 0.28, 0.0);
		}
		if (tickCount % 20 == 0) {
			server.sendParticles(ParticleTypes.END_ROD,
					getX(), getY() + 1.0, getZ(), 2, 0.35, 0.5, 0.35, 0.005);
		}

		// bond lock failsafe: drop it if the claimer left or the timer ran out
		if (bondingPlayer != null) {
			ServerPlayer claimer = server.getServer().getPlayerList().getPlayer(bondingPlayer);
			if (claimer == null || claimer.isRemoved() || server.getGameTime() > bondingExpiresAt
					|| claimer.distanceToSqr(this) > 64.0) {
				bondingPlayer = null;
			}
		}
	}

	private static float approachAngle(float current, float target, float maxStep) {
		float delta = net.minecraft.util.Mth.wrapDegrees(target - current);
		delta = net.minecraft.util.Mth.clamp(delta, -maxStep, maxStep);
		return net.minecraft.util.Mth.wrapDegrees(current + delta);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResult.sidedSuccess(level().isClientSide);
		}
		long now = level().getGameTime();
		Long readyAt = interactCooldown.get(sp.getUUID());
		if (readyAt != null && now < readyAt) {
			return InteractionResult.CONSUME;
		}
		interactCooldown.put(sp.getUUID(), now + 20L);

		if (bondingPlayer != null && !bondingPlayer.equals(sp.getUUID())) {
			sp.displayClientMessage(net.minecraft.network.chat.Component
					.translatable("message.projecthero.max_steel.steel_busy"), true);
			return InteractionResult.CONSUME;
		}

		MaxSteelBonding.attempt(sp, this);
		return InteractionResult.CONSUME;
	}

	// ---------------- bond lock ----------------

	/** Try to claim the bond lock for this player. Returns false if someone else already holds it. */
	public boolean claimBond(ServerPlayer player, int durationTicks) {
		if (bondingPlayer != null && !bondingPlayer.equals(player.getUUID())) {
			return false;
		}
		bondingPlayer = player.getUUID();
		bondingExpiresAt = level().getGameTime() + durationTicks;
		return true;
	}

	public void releaseBond() {
		bondingPlayer = null;
	}

	// ---------------- GeckoLib ----------------

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>(this, "idle", 4, state -> {
			state.setAndContinue(IDLE);
			return PlayState.CONTINUE;
		}));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return this.cache;
	}

	/** Convenience for the crash-site spawner: place a Steel and remember its hover height. */
	public static SteelEntity spawn(ServerLevel level, double x, double y, double z) {
		SteelEntity steel = MaxSteelEntityTypes.STEEL.create(level);
		if (steel == null) {
			return null;
		}
		steel.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
		steel.hoverY = y;
		level.addFreshEntity(steel);
		return steel;
	}

	/** Steel entities near {@code center}, used by the crash-site spawner to avoid duplicates. */
	public static List<SteelEntity> near(ServerLevel level, double x, double y, double z, double r) {
		return level.getEntitiesOfClass(SteelEntity.class,
				new net.minecraft.world.phys.AABB(x - r, y - r, z - r, x + r, y + r, z + r));
	}
}
