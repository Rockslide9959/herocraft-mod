package com.projecthero.mod.blackpanther.plane;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Black Panther Ancestral Plane: <b>an ancestor</b> -- one of the robed kings and queens of old who wait for the player
 * under the great acacia. The player model in one of five detailed robed skins ({@link #variant}, 0 = the eldest, who speaks;
 * textures {@code textures/entity/ancestral/ancestor_N.png} from {@code scratchpad/v0177/gen_ancestor_skins.js}). No AI of their
 * own: the plane's story places, turns and walks them ({@link AncestralPlane}); the client animates the walk from the movement.
 * They can't be hurt or pushed and are never saved.
 */
public class AncestorEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(AncestorEntity.class, EntityDataSerializers.INT);

	public AncestorEntity(EntityType<? extends AncestorEntity> type, Level level) {
		super(type, level);
		noPhysics = true;
		setNoGravity(true);
		setInvulnerable(true);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(VARIANT, 0);
	}

	public int variant() {
		return entityData.get(VARIANT);
	}

	public void setVariant(int v) {
		entityData.set(VARIANT, v);
	}

	/** Puts him at {@code at}, facing {@code yaw}, his head turned toward {@code lookAt} (null: straight ahead). */
	public void place(Vec3 at, float yaw, Vec3 lookAt) {
		setPos(at.x, at.y, at.z);
		setYRot(yaw);
		setYBodyRot(yaw);
		float head = yaw;
		float pitch = 0f;
		if (lookAt != null) {
			Vec3 eye = at.add(0, getEyeHeight(), 0);
			head = PlaneGeometry.lookYaw(eye, lookAt);
			double h = Math.hypot(lookAt.x - eye.x, lookAt.z - eye.z);
			pitch = (float) Math.max(-25, Math.min(25, -Math.toDegrees(Math.atan2(lookAt.y - eye.y, h))));
			// never turn the head further than 70 degrees from the body
			float d = net.minecraft.util.Mth.wrapDegrees(head - yaw);
			head = yaw + net.minecraft.util.Mth.clamp(d, -70f, 70f);
		}
		setYHeadRot(head);
		setXRot(pitch);
	}

	@Override
	public void travel(Vec3 input) {
		// moved only by the story; the walk animation follows the movement on both sides
		calculateEntityAnimation(false);
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
	protected void doPush(Entity entity) {
	}

	@Override
	public boolean removeWhenFarAway(double distance) {
		return false;
	}

	@Override
	public boolean shouldShowName() {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide() && player instanceof ServerPlayer sp) {
			AncestralPlane.ancestorClicked(sp);
		}
		return InteractionResult.sidedSuccess(level().isClientSide());
	}
}
