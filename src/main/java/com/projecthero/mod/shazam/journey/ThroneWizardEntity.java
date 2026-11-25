package com.projecthero.mod.shazam.journey;

import com.projecthero.mod.shazam.entity.WizardShazamEntity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition: <b>the Wizard on his throne</b> in the Rock of Eternity -- the same old wizard (the player model
 * in his own detailed skin, robes, beard and staff from {@code WizardShazamLayer}), but seated on the stone throne until the
 * champion comes before him, then rising ({@link #RISE_TICKS}) and stepping forward. The client poses him from the synced
 * {@link #riseAt} clock ({@code ThroneWizardModel}). He never fades, can't be hurt, doesn't move on his own, and isn't saved:
 * the journey puts him on the throne when the train nears the cave.
 */
public class ThroneWizardEntity extends WizardShazamEntity {
	public static final int RISE_TICKS = 40;
	/** He faces west, down the cave toward the end of the line. */
	public static final float FACING = 90.0f;

	private static final EntityDataAccessor<Long> RISE_AT = SynchedEntityData.defineId(ThroneWizardEntity.class, EntityDataSerializers.LONG);

	public ThroneWizardEntity(EntityType<? extends ThroneWizardEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(RISE_AT, 0L);
	}

	@Override
	protected boolean fades() {
		return false;
	}

	/** Starts rising now (server). */
	public void rise(long now) {
		entityData.set(RISE_AT, now);
	}

	public long riseAt() {
		return entityData.get(RISE_AT);
	}

	/** 1 seated .. 0 standing. */
	public float sitAmount(float partialTick) {
		long at = riseAt();
		if (at == 0L) {
			return 1.0f;
		}
		float k = Mth.clamp((float) (level().getGameTime() - at + partialTick) / RISE_TICKS, 0f, 1f);
		return 1.0f - k * k * (3f - 2f * k);
	}

	/** Moves him between the seat and his standing place as he rises (server; the client interpolates). */
	public void follow(Vec3 seat, Vec3 stand) {
		float s = sitAmount(0f);
		Vec3 p = stand.lerp(seat, s);
		setPos(p.x, p.y, p.z);
	}

	@Override
	public void tick() {
		super.tick();
		setYBodyRot(FACING);
		yBodyRotO = FACING;
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide() && player instanceof ServerPlayer sp) {
			ShazamJourney.wizardClicked(sp, this);
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}
}
