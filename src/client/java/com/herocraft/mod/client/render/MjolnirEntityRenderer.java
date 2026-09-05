package com.herocraft.mod.client.render;

import com.herocraft.mod.entity.MjolnirEntity;
import com.herocraft.mod.item.ModItems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the flying/thrown Mjolnir instead of the generic {@code ThrownItemRenderer} billboard
 * every other thrown item uses (which ignores rotation entirely).
 *
 * <h2>No spin -- orientation comes from the flight direction</h2>
 * {@link MjolnirEntity.State#THROWN}, {@link MjolnirEntity.State#IMPACT} and
 * {@link MjolnirEntity.State#RETURNING} all use the same {@link #renderFlying} pose: the model's
 * own "head" axis points wherever the hammer is actually travelling, full stop -- head leading
 * outbound, head leading back toward the owner on the way home (its velocity really is aimed at the
 * owner by then, thanks to the steering in {@link MjolnirEntity#tickReturning}), and frozen at
 * whatever angle it struck while {@link MjolnirEntity.State#IMPACT} holds it in place. There is no
 * separate spin animation layered on top; a hammer with intention flies straight at its target, it
 * doesn't tumble.
 *
 * <h2>Two transforms, composed, not scattered rotate calls</h2>
 * Getting the model's own axis to line up with the velocity direction takes two independent pieces,
 * kept as two clearly-named steps rather than folded into one another:
 * <ul>
 *   <li>{@link #applyBaseOrientation} -- a fixed, model-space correction. The model is authored
 *   head-up along its own local +Y (grip at the bottom, head at the top -- see the item model's
 *   element bounds), but the direction-of-travel math below assumes "forward" is local +Z. This one
 *   constant rotation reconciles the two, once, regardless of which way the hammer happens to be
 *   flying at the time.</li>
 *   <li>{@link #alignToTravel} -- the actual, per-frame flight orientation, built from
 *   {@code Projectile.updateRotation}'s yaw/pitch (interpolated across the partial tick so it never
 *   steps at 20 Hz).</li>
 * </ul>
 * {@code renderFlying} composes them as {@code alignToTravel * applyBaseOrientation}: the base
 * correction runs first (closest to the raw model), then the whole reoriented model is aimed by
 * yaw/pitch -- so any future change to how the hammer flies only ever touches
 * {@link #alignToTravel}, and any future change to the model's own resting axes only ever touches
 * {@link #applyBaseOrientation}.
 *
 * <p>{@link MjolnirEntity.State#RESTING} is unrelated to flight and keeps its own upright,
 * stood-on-its-head pose (the way the prop is always photographed) rather than lying flat wherever
 * it happened to land.
 */
public final class MjolnirEntityRenderer extends EntityRenderer<MjolnirEntity> {
	/**
	 * The item model is built head-up/grip-down (head occupies y ~10.06..14.11, grip/haft y 0.5..14.375),
	 * so the resting pose is a half turn that puts the head on the floor. Its lowest point is then the
	 * top of the model, 14.375 - 8 = 6.375 pixels above the model's centre, scaled by the FIXED display
	 * transform's scale -- lift by exactly that so the head sits on the ground instead of sinking
	 * into it. Must track {@code mjolnir_thrown.json}'s "fixed" scale (1.15, bumped from 0.7 for the
	 * ~1-block world size) -- this is deliberately computed FROM that scale rather than hand-tuned
	 * separately, so the two can never drift out of sync again.
	 *
	 * <p>The 14.375 top is not an accident of whatever mesh happens to be in
	 * {@code mjolnir_thrown.json}: that file's geometry is deliberately re-fitted into the bounding box
	 * documented in its own {@code credit} field (x/z centred on 8, y 0.5..14.375) precisely so a
	 * re-modelled hammer does not invalidate this constant or any display transform. Change the model,
	 * keep the box.
	 */
	private static final float FIXED_DISPLAY_SCALE = 1.15f;
	private static final float UPRIGHT_LIFT = 6.375f / 16.0f * FIXED_DISPLAY_SCALE;

	private final ItemRenderer itemRenderer;

	/**
	 * The flying hammer is drawn from the 3D box model, not from the held item.
	 *
	 * <p>{@link com.herocraft.mod.item.ModItems#MJOLNIR}'s own model bakes a -45 degree Z rotation into
	 * every element, so that when held it lies along the diagonal a vanilla tool icon is drawn on. That
	 * bake is exactly wrong here: the flight math below needs the raw head-up local +Y axis. So the
	 * entity renders {@link com.herocraft.mod.item.ModItems#MJOLNIR_THROWN} instead -- identical
	 * geometry, no bake -- whose only purpose is to own {@code models/item/mjolnir_thrown.json}. Built
	 * once per renderer rather than per frame; it is only ever read.
	 */
	private final ItemStack modelStack;

	public MjolnirEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
		this.modelStack = new ItemStack(ModItems.MJOLNIR_THROWN);
		this.shadowRadius = 0.25f;
	}

	@Override
	public void render(MjolnirEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
			MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();

		// Mth.lerp on the raw pair would take the long way round the moment the hammer crosses the
		// -180/180 seam; rotLerp interpolates the shortest arc, which is what stops the visible snap
		// from 359 degrees to 0 as it turns.
		float yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		float pitch = Mth.rotLerp(partialTicks, entity.xRotO, entity.getXRot());

		if (entity.getState() == MjolnirEntity.State.RESTING) {
			renderResting(poseStack, yaw);
		} else {
			renderFlying(poseStack, yaw, pitch);
		}

		itemRenderer.renderStatic(this.modelStack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
				poseStack, buffer, entity.level(), entity.getId());

		poseStack.popPose();
		super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
	}

	/**
	 * Stand it on its head. Only the yaw is kept (so a hammer that landed facing north stays facing
	 * north); the pitch is deliberately discarded -- that is what used to leave it lying on its side
	 * at whatever angle it was travelling when it stopped.
	 */
	private static void renderResting(PoseStack poseStack, float yaw) {
		poseStack.translate(0.0f, UPRIGHT_LIFT, 0.0f);
		poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
	}

	/**
	 * THROWN, IMPACT and RETURNING all share this: point the model's head along {@code (yaw, pitch)},
	 * no spin, no pivot offset -- see the class javadoc for why this is composed from two named
	 * steps instead of one hand-tuned rotation.
	 */
	private static void renderFlying(PoseStack poseStack, float yaw, float pitch) {
		alignToTravel(poseStack, yaw, pitch);
		applyBaseOrientation(poseStack);
	}

	/**
	 * Turns the local frame so +Z points along the entity's direction of travel.
	 *
	 * <p>{@code yaw}/{@code pitch} come from {@code Projectile.updateRotation}, which derives them
	 * straight from the velocity as {@code atan2(dx, dz)} and {@code atan2(dy, horizontal)}. A yaw of
	 * {@code y} therefore describes the heading {@code (sin y, 0, cos y)}, which is exactly where
	 * {@code Axis.YP.rotationDegrees(y)} sends the local +Z; the pitch is negated because a positive
	 * {@code xRot} here means "climbing", the opposite of the look-vector convention.
	 *
	 * <p>Because the return flight's velocity is itself steered toward the owner (see
	 * {@code MjolnirEntity.tickReturning}), this alone is what makes a recalled hammer point at the
	 * player: its facing is derived from where it is actually going, never from a fixed rotation or
	 * a separate "face the owner" calculation.
	 */
	private static void alignToTravel(PoseStack poseStack, float yaw, float pitch) {
		poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
	}

	/**
	 * The model-space correction described in the class javadoc: reconciles the model's own head-up
	 * local +Y with {@link #alignToTravel}'s assumption that "forward" is local +Z. A quarter turn
	 * about X maps +Y onto +Z (and, as a side effect, the head's own wide axis onto -Y -- pointing
	 * "up" relative to the flight path rather than out to the side, which is what keeps a flying
	 * hammer reading as a compact silhouette instead of a broad flat face).
	 *
	 * <p>This is the ONE place that fact about the model lives for the flight pose. If the model is
	 * ever re-exported with a different rest orientation, this is the only line that needs to change
	 * -- {@link #alignToTravel} never needs to know or care how the raw model is authored.
	 */
	private static void applyBaseOrientation(PoseStack poseStack) {
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
	}

	@Override
	public ResourceLocation getTextureLocation(MjolnirEntity entity) {
		// Unused -- renderStatic resolves the item's own texture(s) itself. EntityRenderer still
		// requires an override since it's abstract; point at the item atlas as a harmless default.
		return net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS;
	}
}
