package com.projecthero.mod.client.moonknight;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.moonknight.temple.KhonshuAltarBlockEntity;
import com.projecthero.mod.moonknight.temple.KhonshuRitual;
import com.projecthero.mod.moonknight.temple.KhonshuTemple;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the Scarab of Khonshu resting on the altar while a ritual is under way: slowly turning, bobbing, full-bright
 * (it glows in the moonlight), rising a little higher and spinning faster as the kneeling progresses.
 */
public class KhonshuAltarRenderer implements BlockEntityRenderer<KhonshuAltarBlockEntity> {
	private ItemStack scarab;

	public KhonshuAltarRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(KhonshuAltarBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers,
			int packedLight, int packedOverlay) {
		if (be.getLevel() == null || be.altarState() != KhonshuAltarBlockEntity.AltarState.HOLDING_SCARAB) {
			return;
		}
		if (scarab == null) {
			scarab = new ItemStack(KhonshuTemple.SCARAB_OF_KHONSHU);
		}
		float f = be.inRebirth() ? 1.0f : Math.min(1.0f, be.progress() / (float) KhonshuRitual.KNEEL_TICKS);
		float time = be.getLevel().getGameTime() + partialTick;
		float bob = (float) Math.sin(time * 0.08f) * 0.05f;
		pose.pushPose();
		pose.translate(0.5, 1.12 + 0.25 * f + bob, 0.5);
		pose.mulPose(Axis.YP.rotationDegrees(time * (1.5f + 6.0f * f)));
		pose.scale(0.8f, 0.8f, 0.8f);
		Minecraft.getInstance().getItemRenderer().renderStatic(scarab, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT,
				OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
		pose.popPose();
	}
}
