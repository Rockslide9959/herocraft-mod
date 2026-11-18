package com.projecthero.mod.client.supersoldier;

import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * The Captain America shields' item renderer (v0.14.9 for the Adamantium Shield; v0.16.1 the Vibranium Shield; v0.17.5 the
 * stepped-dish {@link CapShieldModel} for all three, the Soldier's Shield included). The item model is
 * {@code builtin/entity}: the item renderer applies the JSON display transform and moves the origin to the block's corner,
 * so this puts it back at the centre and draws the dish front-on (+Z) -- the GUI icon, on the ground and in a frame.
 *
 * <p>Held in a humanoid's hand it is not drawn through here at all: {@code CapShieldHeldLayerMixin} straps it to the forearm
 * in third person and {@code CapShieldFirstPersonMixin} places it in first person (both {@link CapShieldPose}); the JSON's
 * hand transforms are only a fallback for other holders.
 */
public final class AdamantiumShieldRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
	public static final ResourceLocation TEXTURE = CapShieldModel.ADAMANTIUM;
	/** v0.16.1: the Vibranium Shield is drawn by the same model with its own art (same 64x64 layout). */
	public static final ResourceLocation VIBRANIUM_TEXTURE = CapShieldModel.VIBRANIUM;
	/** v0.17.5: the Soldier's Shield (the thrown shield's fallback look) too. */
	public static final ResourceLocation SOLDIER_TEXTURE = CapShieldModel.SOLDIER;

	private final ResourceLocation texture;

	public AdamantiumShieldRenderer() {
		this(TEXTURE);
	}

	public AdamantiumShieldRenderer(ResourceLocation texture) {
		this.texture = texture;
	}

	@Override
	public void render(ItemStack stack, ItemDisplayContext mode, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
		pose.pushPose();
		pose.translate(0.5f, 0.5f, 0.5f);
		pose.translate(0.0f, 0.0f, -CapShieldModel.MID_Z / 16.0f);
		CapShieldModel.render(pose, buffers, texture, stack.hasFoil(), light, overlay);
		pose.popPose();
	}
}
