package com.projecthero.mod.client.ironman;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.drone.IronManDroneEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Rotations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.29 Remote Pilot drone: the suit's real GeckoLib armour on an invisible, reusable client {@link ArmorStand}
 * (the same trick as {@code IronManSuitPlatformRenderer} / {@code IronManSuitPartRenderer}), so the drone looks
 * exactly like the suit does when worn. It leans into its flight, the helmet follows the aim pitch, and the right arm
 * snaps forward for a moment when a repulsor goes off. Hidden from its own pilot in first person by vanilla (it is the
 * camera entity).
 */
public class IronManDroneRenderer extends EntityRenderer<IronManDroneEntity> {
	private static final ResourceLocation FALLBACK = ProjectHeroMod.id("textures/item/repulsor.png");
	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final Map<IronManDroneEntity, ArmorStand> STANDS = new WeakHashMap<>();

	public IronManDroneRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(IronManDroneEntity drone, float entityYaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int packedLight) {
		if (drone.level() == null) {
			return;
		}
		ArmorStand stand = STANDS.computeIfAbsent(drone, IronManDroneRenderer::makeStand);
		for (int i = 0; i < 4; i++) {
			ItemStack want = drone.stack(i);
			if (!ItemStack.matches(stand.getItemBySlot(SLOTS[i]), want)) {
				stand.setItemSlot(SLOTS[i], want.copy());
			}
		}
		float yaw = drone.getViewYRot(partialTick);
		float pitch = Mth.clamp(drone.getViewXRot(partialTick), -60f, 60f);
		// lean into horizontal motion (up to 35 deg)
		double dx = drone.getX() - drone.xo;
		double dz = drone.getZ() - drone.zo;
		float speed = (float) Math.sqrt(dx * dx + dz * dz);
		float lean = Math.min(35f, speed * 60f);

		stand.setHeadPose(new Rotations(pitch, 0f, 0f));
		if (drone.firing()) {
			stand.setRightArmPose(new Rotations(-90f + pitch, 0f, 0f));
		} else {
			stand.setRightArmPose(new Rotations(8f + lean * 0.4f, 0f, 6f));
		}
		stand.setLeftArmPose(new Rotations(8f + lean * 0.4f, 0f, -6f));
		stand.setRightLegPose(new Rotations(lean * 0.2f, 0f, 2f));
		stand.setLeftLegPose(new Rotations(lean * 0.2f, 0f, -2f));

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		if (lean > 0.5f) {
			pose.translate(0.0, 1.0, 0.0);
			pose.mulPose(Axis.XP.rotationDegrees(lean));
			pose.translate(0.0, -1.0, 0.0);
		}
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		try {
			dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
		} finally {
			dispatcher.setRenderShadow(true);
			pose.popPose();
		}
		super.render(drone, entityYaw, partialTick, pose, buffers, packedLight);
	}

	private static ArmorStand makeStand(IronManDroneEntity drone) {
		ArmorStand stand = new ArmorStand(drone.level(), 0, 0, 0);
		stand.setInvisible(true); // only the armour shows
		stand.setNoBasePlate(true);
		stand.setShowArms(true);
		stand.setNoGravity(true);
		stand.setYRot(0f);
		stand.yBodyRot = 0f;
		stand.yHeadRot = 0f;
		return stand;
	}

	@Override
	public ResourceLocation getTextureLocation(IronManDroneEntity drone) {
		return FALLBACK;
	}
}
