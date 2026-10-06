package com.projecthero.mod.client.hulk;

import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.hulk.gladiator.GladiatorThrownWeapon;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.3: the Gladiator Hulk's thrown axe / hammer -- the weapon's own item model drawn big (a Hulk-sized weapon),
 * aligned to its heading and tumbling end over end like Stormbreaker's thrown axe. A hammer stuck in the ground stands
 * still, head down, tilted the way it flew in.
 */
public final class GladiatorWeaponRenderer<T extends GladiatorThrownWeapon> extends EntityRenderer<T> {
	private static final float SCALE = 2.2f;

	private final ItemRenderer itemRenderer;
	private final Supplier<Item> item;
	private final float spinPerTick;
	private ItemStack stack = ItemStack.EMPTY;

	public GladiatorWeaponRenderer(EntityRendererProvider.Context context, Supplier<Item> item, float spinPerTick) {
		super(context);
		this.itemRenderer = context.getItemRenderer();
		this.item = item;
		this.spinPerTick = spinPerTick;
		this.shadowRadius = 0.3f;
	}

	private ItemStack stack() {
		Item it = item.get();
		if (it != null && (stack.isEmpty() || !stack.is(it))) {
			stack = new ItemStack(it); // lazily: the gear's items may register after the renderers
		}
		return stack;
	}

	@Override
	public void render(T entity, float entityYaw, float partialTicks, PoseStack pose, MultiBufferSource buffer, int light) {
		ItemStack s = stack();
		if (s.isEmpty()) {
			return;
		}
		pose.pushPose();
		float yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
		pose.translate(0.0, 0.4, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		if (entity.phase() == GladiatorThrownWeapon.PHASE_STUCK) {
			// buried head-first: the item model turned so its head (top right) points down, its face toward the thrower,
			// leaning back the way it came
			pose.translate(0.0, 0.35, 0.0);
			pose.mulPose(Axis.XP.rotationDegrees(-20.0f));
			pose.mulPose(Axis.ZP.rotationDegrees(135.0f));
		} else {
			float pitch = Mth.rotLerp(partialTicks, entity.xRotO, entity.getXRot());
			pose.mulPose(Axis.XP.rotationDegrees(-pitch));
			// stand the blade in the plane of flight, then tumble about the side-to-side axis
			pose.mulPose(Axis.YP.rotationDegrees(90.0f));
			float spin = (entity.tickCount + partialTicks) * spinPerTick;
			pose.mulPose(Axis.ZP.rotationDegrees(-spin));
		}
		pose.scale(SCALE, SCALE, SCALE);
		itemRenderer.renderStatic(s, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffer, entity.level(), entity.getId());
		pose.popPose();
		super.render(entity, entityYaw, partialTicks, pose, buffer, light);
	}

	@Override
	public ResourceLocation getTextureLocation(T entity) {
		return TextureAtlas.LOCATION_BLOCKS; // unused -- renderStatic resolves the model's own textures
	}
}
