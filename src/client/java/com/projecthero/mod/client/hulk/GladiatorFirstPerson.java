package com.projecthero.mod.client.hulk;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.projecthero.mod.hulk.gladiator.GladiatorAbilities;
import com.projecthero.mod.hulk.gladiator.GladiatorGear;
import com.projecthero.mod.hulk.gladiator.GladiatorItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.5: Gladiator Hulk in first person -- both green fists in view, the hammer gripped in the right one and the axe in
 * the left (the third-person look: hammer {@code right_arm}, axe {@code left_arm}). A thrown weapon
 * ({@link GladiatorGear#weaponAway}) leaves that fist empty.
 *
 * <ul>
 *   <li>{@code ItemInHandRendererGladiatorMixin} draws both bare arms in place of whatever the hands hold (the Hulk's
 *       held items are hidden in third person too), unless he is using the item in that hand (eating, drinking...).
 *       Each arm gets vanilla's own swing progress, so the alternating swings
 *       ({@link com.projecthero.mod.hulk.gladiator.GladiatorSwing}) animate the arm that swung; the equip dip (the
 *       main hand lowering after every hit, or on a hotbar change) is left out -- the fists never change what they hold.</li>
 *   <li>{@code PlayerRendererGladiatorWeaponMixin} (TAIL of {@code renderHand}, where the pose stack is exactly where the
 *       arm was just drawn) then puts the weapon's own 3D item model in the fist -- handle up out of the fist, head
 *       tipped in and away so the middle of the view stays clear -- turning with every swing and bob.</li>
 * </ul>
 * Checked with the client screenshot harness (v0.15.5).
 */
public final class GladiatorFirstPerson {
	/** The weapon's size against the item model (the 42-pixel hammer comes out about one block long). */
	private static final float SCALE = 0.40f;
	/** Degrees the head is tipped in toward the middle of the view... */
	private static final float INWARD = 15.0f;
	/** ...and away from the eye. */
	private static final float LEAN = 25.0f;
	/** Degrees each weapon is turned about its own handle: the hammer's long head across the view, the axe blade out. */
	private static final float SPIN_HAMMER = 0.0f;
	private static final float SPIN_AXE = 180.0f;
	/** Where the fist closes on each handle, in item-model pixels (just above the pommel). */
	private static final float GRIP_HAMMER = -5.0f;
	private static final float GRIP_AXE = -6.0f;

	private GladiatorFirstPerson() {
	}

	/** Gladiator Hulk: both arms (and weapons) in first person. */
	public static boolean active(AbstractClientPlayer player) {
		return GladiatorAbilities.active(player);
	}

	/** Draw this hand as a bare (weapon-holding) Hulk arm instead of its item? */
	public static boolean drawsArm(AbstractClientPlayer player, InteractionHand hand) {
		if (!active(player) || player.isInvisible() || player.isScoping()) {
			return false;
		}
		return !(player.isUsingItem() && player.getUsedItemHand() == hand);
	}

	private static ItemStack weapon(AbstractClientPlayer player, boolean axe) {
		ItemStack worn = GladiatorGear.get(player, axe ? GladiatorGear.AXE : GladiatorGear.HAMMER);
		if (!worn.isEmpty()) {
			return worn;
		}
		Item item = axe ? GladiatorItems.AXE : GladiatorItems.HAMMER;
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	/**
	 * After the first-person arm is drawn: the hammer in the right fist, the axe in the left. {@code arm} still holds
	 * the pose it was just drawn with.
	 */
	public static void renderWeapon(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
			ModelPart arm, boolean rightArm) {
		if (!active(player) || player.isInvisible()) {
			return;
		}
		boolean axe = !rightArm;
		if (GladiatorGear.weaponAway(player, axe)) {
			return;
		}
		ItemStack stack = weapon(player, axe);
		if (stack.isEmpty()) {
			return;
		}
		float f = rightArm ? 1.0f : -1.0f;
		float spin = axe ? SPIN_AXE : SPIN_HAMMER;
		pose.pushPose();
		// to the middle of the fist, in the arm's own frame
		arm.translateAndRotate(pose);
		pose.translate(-f * 1.0f / 16.0f, 9.5f / 16.0f, 0.0f);
		// take the arm's own (tiny) rest rotation and vanilla's fixed first-person arm rotation back off, so what is left
		// is a view-space orientation that still turns with the swing (renderPlayerArm's swing rotations sit between its
		// 45-degree yaw and this fixed chain): Ry(45f) . swing . C . L = D at rest  =>  L = C^-1 . Ry(-45f) . D
		Quaternionf partRot = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot);
		Quaternionf chain = new Quaternionf()
				.rotateZ(f * 120.0f * Mth.DEG_TO_RAD)
				.rotateX(200.0f * Mth.DEG_TO_RAD)
				.rotateY(-f * 135.0f * Mth.DEG_TO_RAD);
		Quaternionf local = chain.mul(partRot).conjugate()
				.mul(new Quaternionf().rotationY(-f * 45.0f * Mth.DEG_TO_RAD))
				// D: the handle up out of the fist, its top tipped in toward the middle and away from the eye, then the
				// weapon turned about its own handle to show its head
				.mul(new Quaternionf().rotationZ(f * INWARD * Mth.DEG_TO_RAD))
				.mul(new Quaternionf().rotationX(-LEAN * Mth.DEG_TO_RAD))
				.mul(new Quaternionf().rotationY(f * spin * Mth.DEG_TO_RAD));
		pose.mulPose(local);
		pose.scale(SCALE, SCALE, SCALE);
		// the grip (just above the pommel) on the fist; the item model draws itself offset by -0.5 on every axis
		float gripY = axe ? GRIP_AXE : GRIP_HAMMER;
		pose.translate(0.0f, 0.5f - gripY / 16.0f, 0.0f);
		Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE, !rightArm, pose,
				buffers, player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
		pose.popPose();
	}
}
