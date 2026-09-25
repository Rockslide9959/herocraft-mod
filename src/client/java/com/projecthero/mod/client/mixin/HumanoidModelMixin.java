package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.projecthero.mod.client.FlightPoseHelper;
import com.projecthero.mod.item.ModItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The limb half of the flight pose (the body tilt lives in {@code PlayerRendererMixin}); together
 * they produce THOR_DESIGN.md's "hammer arm extended" flight look.
 *
 * <p>Only {@link FlightPoseHelper.Tier#FAST} (sprinting) raises the arm at all -- straight along
 * the body's own "up" axis, which the accompanying 90-degree body tilt then lays flat along the
 * direction of travel: the superman pose. {@link FlightPoseHelper.Tier#HOVER} used to raise the arm
 * the same way for an overhead-whirl pose, but standing still in the air with the hammer held
 * overhead read as an unnatural "backwards" grip, so hovering now leaves the arm alone entirely --
 * {@link FlightPoseHelper#armRaise} is 0 for hover, and this whole injection is a no-op for it.
 *
 * <p>Everything is applied as a lerp from vanilla's result toward the target, weighted by
 * {@link FlightPoseHelper#armRaise}, so entering/leaving the pose is a smooth transition rather
 * than a snap. Applies to every humanoid model -- including the armor layers, so the Thor suit
 * follows the pose -- but only for players with the synced flight flag set.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends LivingEntity> {
	/**
	 * 180 degrees in radians, negative so the arm sweeps up through the front rather than the back.
	 * ModelPart rotations are radians, and a resting arm hangs along the model's +Y (down) axis, so
	 * a half turn about X points it along the body's up axis.
	 */
	private static final float ARM_RAISED_XROT = -(float) Math.PI;
	/** The free arm tucks in along the body, so the sprint pose reads as one flat line. */
	private static final float FREE_ARM_TUCK_ZROT = 0.15f;

	@Shadow
	public ModelPart head;
	@Shadow
	public ModelPart hat;
	@Shadow
	public ModelPart rightArm;
	@Shadow
	public ModelPart leftArm;
	@Shadow
	public ModelPart rightLeg;
	@Shadow
	public ModelPart leftLeg;

	/**
	 * Web-swing pose (v0.6.17): the arm that fired this swing's web is thrown up toward the line, so a
	 * swinging Spider-Man looks like he just shot a web into the sky and is hanging from it. The hand
	 * alternates each swing ({@code SpiderManState.swingHandRight}), matching the web line's origin in
	 * {@code SpiderWebLineRenderer}.
	 */
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$spiderSwingArm(LivingEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (!(entity instanceof Player player)
				|| !com.projecthero.mod.spider.SpiderSwing.isSwinging(player)) {
			return;
		}
		com.projecthero.mod.spider.data.SpiderManState s = player.getAttachedOrElse(
				com.projecthero.mod.attachment.ModAttachments.SPIDER_MAN_STATE, null);
		if (s == null) {
			return;
		}
		ModelPart arm = s.swingHandRight ? this.rightArm : this.leftArm;
		arm.xRot = -2.65f;
		arm.yRot = 0.0f;
		arm.zRot = s.swingHandRight ? -0.15f : 0.15f;
	}

	/**
	 * Wolverine Claw Dash: arms thrust forward past the tilted body (so they point along the line of
	 * travel), claws leading, legs trailing, head kept up on the target.
	 */
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$clawDashPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (!(entity instanceof Player player)
				|| com.projecthero.mod.client.wolverine.WolverineDashPose.amount(player) <= 0.0f) {
			return;
		}
		float lean = (float) Math.toRadians(com.projecthero.mod.client.wolverine.WolverineDashPose.LEAN_DEGREES);
		float armX = -(1.5708f + lean);
		this.rightArm.xRot = armX;
		this.rightArm.yRot = -0.14f;
		this.rightArm.zRot = 0.0f;
		this.leftArm.xRot = armX;
		this.leftArm.yRot = 0.14f;
		this.leftArm.zRot = 0.0f;
		this.rightLeg.xRot = 0.25f;
		this.rightLeg.yRot = 0.0f;
		this.leftLeg.xRot = -0.15f;
		this.leftLeg.yRot = 0.0f;
		this.head.xRot -= lean * 0.85f;
		this.hat.xRot = this.head.xRot;
	}

	/** All Might's Smash / transformation poses (v0.12.33): driven by the synced power state, so every viewer and the armour shell agree. */
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$allMightPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (entity instanceof Player player) {
			com.projecthero.mod.client.allmight.AllMightPose.apply(player, (HumanoidModel<?>) (Object) this);
		}
	}

	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void projecthero$flightPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (!(entity instanceof Player player)) {
			return;
		}

		float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		levelHead(player, partialTick);

		// Legs straighten in proportion to the body lean, so hero flight (which leans without raising
		// the arms) doesn't run-cycle its legs in mid-air.
		float legLean = Math.abs(FlightPoseHelper.lean(player, partialTick)) / FlightPoseHelper.LEAN_FAST_DEGREES;
		if (legLean > 0.02f) {
			float w = Math.min(1.0f, legLean);
			this.rightLeg.xRot = Mth.lerp(w, this.rightLeg.xRot, 0.0f);
			this.rightLeg.yRot = Mth.lerp(w, this.rightLeg.yRot, 0.0f);
			this.rightLeg.zRot = Mth.lerp(w, this.rightLeg.zRot, 0.0f);
			this.leftLeg.xRot = Mth.lerp(w, this.leftLeg.xRot, 0.0f);
			this.leftLeg.yRot = Mth.lerp(w, this.leftLeg.yRot, 0.0f);
			this.leftLeg.zRot = Mth.lerp(w, this.leftLeg.zRot, 0.0f);
		}

		// Hero flight (Wind/rock/flame): lean like Thor but keep the arms pinned straight down at the
		// sides instead of letting them run-cycle in the air.
		if (FlightPoseHelper.heroFlight(player, partialTick)) {
			float w = Math.min(1.0f, legLean);
			this.rightArm.xRot = Mth.lerp(w, this.rightArm.xRot, 0.0f);
			this.rightArm.yRot = Mth.lerp(w, this.rightArm.yRot, 0.0f);
			this.rightArm.zRot = Mth.lerp(w, this.rightArm.zRot, 0.05f);
			this.leftArm.xRot = Mth.lerp(w, this.leftArm.xRot, 0.0f);
			this.leftArm.yRot = Mth.lerp(w, this.leftArm.yRot, 0.0f);
			this.leftArm.zRot = Mth.lerp(w, this.leftArm.zRot, -0.05f);
		}

		float raise = FlightPoseHelper.armRaise(player, partialTick);
		if (raise <= 0.001f) {
			return;
		}

		boolean mainHand = player.getMainHandItem().is(ModItems.MJOLNIR);
		boolean offHand = player.getOffhandItem().is(ModItems.MJOLNIR);
		if (!mainHand && !offHand) {
			return;
		}

		HumanoidArm hammerArm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
		boolean hammerOnRight = hammerArm == HumanoidArm.RIGHT;
		ModelPart arm = hammerOnRight ? this.rightArm : this.leftArm;
		ModelPart freeArm = hammerOnRight ? this.leftArm : this.rightArm;

		// raise is only ever meaningfully nonzero for Tier.FAST now (see class javadoc) -- straight
		// out, no inward tilt, dead straight in the direction of flight.
		arm.xRot = Mth.lerp(raise, arm.xRot, ARM_RAISED_XROT);
		arm.yRot = Mth.lerp(raise, arm.yRot, 0.0f);
		arm.zRot = Mth.lerp(raise, arm.zRot, 0.0f);

		float tuck = hammerOnRight ? FREE_ARM_TUCK_ZROT : -FREE_ARM_TUCK_ZROT;
		freeArm.xRot = Mth.lerp(raise, freeArm.xRot, 0.0f);
		freeArm.yRot = Mth.lerp(raise, freeArm.yRot, 0.0f);
		freeArm.zRot = Mth.lerp(raise, freeArm.zRot, tuck);

		// Straighten the legs: vanilla keeps walk-cycling them mid-air, which reads as running on
		// nothing while hovering and breaks the flat line of the superman pose while sprinting.
		this.rightLeg.xRot = Mth.lerp(raise, this.rightLeg.xRot, 0.0f);
		this.rightLeg.yRot = Mth.lerp(raise, this.rightLeg.yRot, 0.0f);
		this.rightLeg.zRot = Mth.lerp(raise, this.rightLeg.zRot, 0.0f);
		this.leftLeg.xRot = Mth.lerp(raise, this.leftLeg.xRot, 0.0f);
		this.leftLeg.yRot = Mth.lerp(raise, this.leftLeg.yRot, 0.0f);
		this.leftLeg.zRot = Mth.lerp(raise, this.leftLeg.zRot, 0.0f);
	}

	/**
	 * Cancels the body tilt out of the head, so a sprint-flying player looks where they are actually
	 * looking -- straight ahead down the flight path -- instead of having their face rotated a full
	 * 90 degrees into the ground along with the rest of the body.
	 *
	 * <p>The tilt itself is applied by {@code PlayerRendererMixin} as a render-space
	 * {@code Axis.XP.rotationDegrees(-lean)}. Render space and model space differ by
	 * {@code LivingEntityRenderer}'s {@code scale(-1, -1, 1)}, and conjugating an X rotation by that
	 * negates its angle -- so the tilt is a model-space {@code +lean} about X, and subtracting the
	 * same angle from {@code head.xRot} (which {@code ModelPart} applies innermost, in the head's own
	 * frame) puts the head back where it started.
	 *
	 * <p>Driven straight off {@link FlightPoseHelper#lean} rather than the arm-raise blend, so it
	 * tracks the tilt exactly at every point of the transition -- including the SLOW tier, which
	 * leans without raising the arm at all.
	 *
	 * <p>The hat (skin second layer over the head) is re-copied from the head afterwards: vanilla's
	 * {@code HumanoidModel.setupAnim} does {@code hat.copyFrom(head)} <em>before</em> this TAIL
	 * injection runs, so without the re-copy the hat keeps the un-levelled rotation and the body tilt
	 * then drags it off the face -- the "second layer disconnected while flying" bug. Applies to every
	 * flight in the mod because they all drive {@link FlightPoseHelper#lean}.
	 */
	private void levelHead(Player player, float partialTick) {
		float lean = FlightPoseHelper.lean(player, partialTick);
		if (Math.abs(lean) < 0.05f) {
			return;
		}
		this.head.xRot -= lean * ((float) Math.PI / 180.0f);
		this.hat.copyFrom(this.head);
	}
}
