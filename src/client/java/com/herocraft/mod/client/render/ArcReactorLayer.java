package com.herocraft.mod.client.render;

import com.herocraft.mod.ironman.TonyStark;
import com.herocraft.mod.ironman.item.IronManArmorItem;
import com.herocraft.mod.ironman.item.IronManItems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders a small glowing Arc Reactor on the chest of any player who has the Tony Stark power and is
 * <b>not</b> wearing an Iron Man chestplate (the suit has its own). Purely cosmetic; the power is the
 * synced {@link TonyStark} attachment, so it shows correctly for other players too.
 */
public class ArcReactorLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	private final ItemRenderer itemRenderer;
	private final ItemStack reactor = new ItemStack(IronManItems.ARC_REACTOR);

	public ArcReactorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
			ItemRenderer itemRenderer) {
		super(parent);
		this.itemRenderer = itemRenderer;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
			float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (!TonyStark.hasPower(player) || player.isInvisible()) {
			return;
		}
		if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem) {
			return;
		}
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		// body pivot is at the neck; move to the chest surface, facing forward. "changes 18": sit it a
		// little higher on the chest (Y is down in model space, so a smaller value = higher).
		pose.translate(0.0, 0.22, -0.145);
		pose.mulPose(Axis.XP.rotationDegrees(180f));
		pose.scale(0.28f, 0.28f, 0.28f);
		itemRenderer.renderStatic(reactor, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY,
				pose, buffers, player.level(), player.getId());
		pose.popPose();
	}
}
