package com.projecthero.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.ironman.fabricator.IronManSuitPlatformBlockEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * "Hall of Armor" display ("changes 16"): renders the racked suit as the actual worn armour <em>model</em> on an
 * invisible, slowly-rotating armour stand above the platform (vanilla {@code HumanoidArmorLayer}, so the GeckoLib suit
 * models show exactly as on a player). The block entity syncs its contents on every change, so this always reflects
 * what is really stored.
 *
 * <p>v0.15.4: the platform's robotic arms (v0.15.1 deploy, v0.15.3 retrieve) are gone -- suiting up is done by the Stark
 * Gantry's arms now ({@link StarkGantryRenderer}, which reuses their model and texture), so this only draws the rack.
 */
public class IronManSuitPlatformRenderer implements BlockEntityRenderer<IronManSuitPlatformBlockEntity> {
	private static final Map<IronManSuitPlatformBlockEntity, ArmorStand> STANDS = new WeakHashMap<>();

	private static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final float RACK_SCALE = 0.62f;
	/** Height the racked suit stands at above the block (master: the raised floor plate of the new platform model). */
	public static final double DISPLAY_Y_OFFSET = 0.2;

	public IronManSuitPlatformRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(IronManSuitPlatformBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {
		Level level = be.getLevel();
		if (level == null) {
			return;
		}
		ArmorStand rack = STANDS.computeIfAbsent(be, k -> makeStand(level));
		boolean any = false;
		for (int i = 0; i < 4; i++) {
			ItemStack piece = be.getItem(i);
			if (!ItemStack.matches(rack.getItemBySlot(SLOTS[i]), piece)) {
				rack.setItemSlot(SLOTS[i], piece);
			}
			any |= !piece.isEmpty();
		}
		if (!any) {
			return;
		}
		float time = level.getGameTime() + partialTick;
		float spin = time * 1.4f;
		float bob = (float) Math.sin(time * 0.06f) * 0.03f;
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		pose.pushPose();
		pose.translate(0.5, DISPLAY_Y_OFFSET + bob, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(spin));
		pose.scale(RACK_SCALE, RACK_SCALE, RACK_SCALE);
		dispatcher.render(rack, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
		pose.popPose();
		dispatcher.setRenderShadow(true);
	}

	private static ArmorStand makeStand(Level level) {
		ArmorStand stand = new ArmorStand(level, 0, 0, 0);
		stand.setInvisible(true); // only the armour shows
		stand.setNoBasePlate(true);
		stand.setShowArms(true);
		stand.setNoGravity(true);
		stand.setYRot(0f);
		stand.yBodyRot = 0f;
		stand.yHeadRot = 0f;
		return stand;
	}
}
