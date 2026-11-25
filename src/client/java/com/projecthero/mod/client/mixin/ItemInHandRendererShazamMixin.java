package com.projecthero.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.client.shazam.ShazamPose;
import com.projecthero.mod.shazam.Shazam;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.7, first person: Shazam's moves play on his empty hands ({@link ShazamPose#firstPerson}) -- the combo's jabs and hook, the
 * uppercut, the cocked Haymaker and its release, the hand raised to call the lightning, Zeus's Wrath held out, the creature held
 * overhead -- with vanilla's punch swing and equip dip held off while a move owns them. Both hands draw (the off hand too, which
 * vanilla leaves empty), only while the main hand is empty (the Super Soldier's pattern).
 *
 * <p>v0.17.7 sprint-flight (user: "make it so that you see the hands going in front of you in first person"): while he sprint-flies
 * ({@link ShazamPose#sprintFlight}) both fists come up in front of the camera toward the middle of the screen
 * ({@link ShazamPose#firstPersonFlight}) and lower away when it ends. A held item is put away for it: through the first half of the
 * blend vanilla's own equip dip lowers the item out of view, then the bare fists rise in its place (the reverse on the way out). A
 * move still wins over the flight hands while it plays.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererShazamMixin {
	@Shadow
	protected abstract void renderPlayerArm(PoseStack pose, MultiBufferSource buffers, int light, float equipProgress, float swingProgress,
			HumanoidArm arm);

	/** Sprint-flight with something in the hand: the item dips away (vanilla's equip lowering) over the first half of the blend. */
	@ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
	private float projecthero$shazamFlightDip(float equipProgress, AbstractClientPlayer player, float partialTick, float pitch,
			InteractionHand hand, float swingProgress, ItemStack stack) {
		if (stack.isEmpty() || !Shazam.transformed(player)) {
			return equipProgress;
		}
		float fly = ShazamPose.sprintFlight(player, partialTick);
		return fly <= 0.001f ? equipProgress : Math.max(equipProgress, Mth.clamp(fly * 2f, 0f, 1f));
	}

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void projecthero$shazamHands(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float swingProgress,
			ItemStack stack, float equipProgress, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (player.isInvisible() || player.isScoping() || !Shazam.transformed(player)) {
			return;
		}
		boolean main = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
		boolean mainEmpty = player.getMainHandItem().isEmpty();
		float fly = ShazamPose.sprintFlight(player, partialTick);
		// with an item out, the fists only rise once the item has dipped away (the second half of the blend)
		boolean anyItem = !mainEmpty || !player.getOffhandItem().isEmpty();
		float hands = anyItem ? Mth.clamp(fly * 2f - 1f, 0f, 1f) : fly;
		float move = stack.isEmpty() && mainEmpty ? Math.min(1f, ShazamPose.firstPersonWeight(player, partialTick)) : 0f;
		if (!stack.isEmpty() && hands <= 0.001f) {
			return; // vanilla draws the item (lowered by the dip above while the flight blends)
		}
		if (move <= 0.001f && hands <= 0.001f) {
			return;
		}
		// the bare main arm was already on screen (vanilla draws it when the main hand is empty): it moves over from there;
		// the off hand, or a hand whose item dipped away, rises from below the view
		boolean bareMain = main && mainEmpty;
		pose.pushPose();
		ShazamPose.firstPersonFlight(player, arm, partialTick, hands * (1f - move), !bareMain, pose);
		if (move > 0.001f) {
			ShazamPose.firstPerson(player, arm, partialTick, pose);
		}
		float keep = 1f - Math.max(move, hands);
		renderPlayerArm(pose, buffers, light, bareMain ? equipProgress * keep : 0f, bareMain ? swingProgress * keep : 0f, arm);
		pose.popPose();
		ci.cancel();
	}
}
