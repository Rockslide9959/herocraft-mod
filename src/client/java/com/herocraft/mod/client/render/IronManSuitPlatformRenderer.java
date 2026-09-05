package com.herocraft.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.herocraft.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/**
 * "Hall of Armor" display ("changes 16"): renders the racked suit as the actual worn armour <em>model</em>
 * on an invisible, slowly-rotating armour stand above the platform, instead of the four floating item
 * icons. The armour goes through vanilla's {@code HumanoidArmorLayer}, so the GeckoLib suit models
 * (see {@code SuperheroArmorRenderer}) show exactly as they do on a player.
 *
 * <p>The block entity now syncs its contents to the client on every change (chunk load included), so
 * this always reflects what is really stored -- it no longer needs the GUI to have been opened, and a
 * called-away suit stops showing the instant it leaves.
 */
public class IronManSuitPlatformRenderer implements BlockEntityRenderer<IronManSuitPlatformBlockEntity> {
	/** One reusable client-only armour stand per platform BE, so we do not allocate every frame. */
	private static final Map<IronManSuitPlatformBlockEntity, ArmorStand> STANDS = new WeakHashMap<>();

	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };

	public IronManSuitPlatformRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(IronManSuitPlatformBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {
		if (be.getLevel() == null || be.isEmptyPlatform()) {
			return;
		}
		ArmorStand stand = STANDS.computeIfAbsent(be, k -> makeStand(be));
		if (stand == null) {
			return;
		}

		boolean any = false;
		for (int i = 0; i < 4; i++) {
			ItemStack piece = be.getItem(i);
			stand.setItemSlot(SLOTS[i], piece);
			any |= !piece.isEmpty();
		}
		if (!any) {
			return;
		}

		float spin = (be.getLevel().getGameTime() + partialTick) * 1.4f;
		float bob = (float) Math.sin((be.getLevel().getGameTime() + partialTick) * 0.06f) * 0.03f;

		pose.pushPose();
		pose.translate(0.5, 0.05 + bob, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(spin));
		pose.scale(0.62f, 0.62f, 0.62f);
		// EntityRenderDispatcher renders relative to the current pose; suppress shadow/hitbox.
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
		dispatcher.setRenderShadow(true);
		pose.popPose();
	}

	private static ArmorStand makeStand(IronManSuitPlatformBlockEntity be) {
		if (be.getLevel() == null) {
			return null;
		}
		ArmorStand stand = new ArmorStand(be.getLevel(), 0, 0, 0);
		stand.setInvisible(true);      // only the armour shows
		stand.setNoBasePlate(true);
		stand.setShowArms(true);
		stand.setNoGravity(true);
		stand.setYRot(0f);
		stand.yBodyRot = 0f;
		stand.yHeadRot = 0f;
		return stand;
	}
}
