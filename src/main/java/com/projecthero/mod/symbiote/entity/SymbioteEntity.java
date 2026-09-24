package com.projecthero.mod.symbiote.entity;

import java.util.List;
import java.util.UUID;

import com.projecthero.mod.symbiote.SymbioteBonding;

import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A free-floating Symbiote -- the writhing black organism that bonds with a Spider-Man to grant the
 * Symbiote upgrade. Found:
 * <ul>
 *   <li>at the centre of a {@code symbiote_meteor} impact crater (it rode the rock down),</li>
 *   <li>inside the containment cell of a {@code symbiote_lab},</li>
 *   <li>where a rare {@link com.projecthero.mod.symbiote.SymbioteHost Symbiote Host} mob died
 *       (the Symbiote leaps clear of its dead host, looking for a new one).</li>
 * </ul>
 *
 * <h2>Deliberately not a mob, and not modelled</h2>
 * Extends {@link Entity} directly like {@code SteelEntity}: no pathfinding, no goals, no despawn timer,
 * no loot table. It has <b>no mesh at all</b> -- the visual is entirely its own dense particle cloud
 * (squid ink / smoke / reverse-portal), rendered by a no-op renderer. That keeps it cheap and needs no
 * geo/texture asset.
 *
 * <h2>Indestructible until it bonds</h2>
 * Ignores every damage source, does not burn, drown or fall. It only leaves the world when a
 * Spider-Man bonds with it ({@link #discard()} from {@link SymbioteBonding}) or an operator removes it.
 *
 * <h2>Bonding is server-authoritative and single-claim</h2>
 * {@link #bondingPlayer} is a soft lock, exactly like Steel's, so two players cannot claim one
 * Symbiote at once; it clears itself if the claimer logs out or walks away.
 */
public class SymbioteEntity extends Entity {
	/** Y the blob hovers around; the writhe oscillates about this. Persisted. */
	private double hoverY = Double.NaN;
	private UUID bondingPlayer;
	private long bondingExpiresAt;
	private final java.util.Map<UUID, Long> interactCooldown = new java.util.HashMap<>();
	/** Recoil timer after refusing a non-Spider-Man: brief "get away from me" pulse, no gameplay effect. */
	private int recoilTicks;

	public SymbioteEntity(EntityType<? extends SymbioteEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
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
		return true;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return true;
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

	@Override
	public boolean shouldBeSaved() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		setDeltaMovement(Vec3.ZERO);
		if (Double.isNaN(hoverY)) {
			hoverY = getY();
		}
		double writhe = Math.sin((tickCount + getId() * 7) * 0.12) * 0.10;
		setPos(getX(), hoverY + writhe, getZ());
		this.yRotO = getYRot();
		setYRot(getYRot() + 3.0f);
		if (recoilTicks > 0) {
			recoilTicks--;
		}

		if (!(level() instanceof ServerLevel server)) {
			return;
		}

		emitCloud(server);

		if (bondingPlayer != null) {
			ServerPlayer claimer = server.getServer().getPlayerList().getPlayer(bondingPlayer);
			if (claimer == null || claimer.isRemoved() || server.getGameTime() > bondingExpiresAt
					|| claimer.distanceToSqr(this) > 64.0) {
				bondingPlayer = null;
			}
		}
	}

	private void emitCloud(ServerLevel server) {
		int n = recoilTicks > 0 ? 10 : 4;
		double spread = recoilTicks > 0 ? 0.9 : 0.55;
		for (int i = 0; i < n; i++) {
			ParticleOptions type = switch (i % 3) {
				case 0 -> ParticleTypes.SQUID_INK;
				case 1 -> ParticleTypes.SMOKE;
				default -> ParticleTypes.REVERSE_PORTAL;
			};
			server.sendParticles(type, getX(), getY() + 0.5, getZ(), 1,
					spread, spread * 0.7, spread, recoilTicks > 0 ? 0.04 : 0.005);
		}
		if (tickCount % 20 == 0) {
			server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 2, 0.3, 0.2, 0.3, 0.0);
		}
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
					.translatable("message.projecthero.symbiote.entity_busy"), true);
			return InteractionResult.CONSUME;
		}

		// v0.11.15: an empty Symbiote Vial in hand bottles the organism instead of bonding with it.
		if (player.getMainHandItem().is(com.projecthero.mod.symbiote.item.SymbioteHostItems.SYMBIOTE_VIAL)) {
			com.projecthero.mod.symbiote.item.SymbioteVialItem.capture(sp, this);
			return InteractionResult.CONSUME;
		}

		SymbioteBonding.attempt(sp, this);
		return InteractionResult.CONSUME;
	}

	// ---------------- bond lock ----------------

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

	/** Play the "recoil" pulse after refusing an unworthy host. */
	public void recoil() {
		recoilTicks = 30;
	}

	// ---------------- helpers ----------------

	public static SymbioteEntity spawn(ServerLevel level, double x, double y, double z) {
		SymbioteEntity e = SymbioteEntityTypes.SYMBIOTE.create(level);
		if (e == null) {
			return null;
		}
		e.moveTo(x, y, z, level.random.nextFloat() * 360f, 0f);
		e.hoverY = y;
		level.addFreshEntity(e);
		return e;
	}

	public static List<SymbioteEntity> near(ServerLevel level, double x, double y, double z, double r) {
		return level.getEntitiesOfClass(SymbioteEntity.class, new AABB(x - r, y - r, z - r, x + r, y + r, z + r));
	}
}
