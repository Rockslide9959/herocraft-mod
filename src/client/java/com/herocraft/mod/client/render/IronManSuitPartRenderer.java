package com.herocraft.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.herocraft.mod.HeroCraftMod;
import com.herocraft.mod.ironman.entity.IronManSuitPartEntity;
import com.herocraft.mod.ironman.item.IronManItems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Renderer for a flying suit-part courier ("changes 18"): draws the actual worn armour <em>model</em>
 * -- the GeckoLib suit -- instead of the flat inventory sprite. The piece is put on an invisible,
 * reusable client {@link ArmorStand} (one per courier entity) and rendered through vanilla's
 * {@code HumanoidArmorLayer}, exactly like {@code IronManSuitPlatformRenderer}'s Hall-of-Armor
 * display, so it looks identical to the suit as it is when worn. Trailed by the entity's own
 * {@code END_ROD} particles.
 */
public class IronManSuitPartRenderer extends EntityRenderer<IronManSuitPartEntity> {
	private static final ResourceLocation FALLBACK = HeroCraftMod.id("textures/item/repulsor.png");

	/** One reusable client-only armour stand per courier, so we do not allocate every frame. */
	private static final Map<IronManSuitPartEntity, ArmorStand> STANDS = new WeakHashMap<>();

	private static EquipmentSlot slotFor(ArmorItem.Type type) {
		return switch (type) {
			case HELMET -> EquipmentSlot.HEAD;
			case CHESTPLATE -> EquipmentSlot.CHEST;
			case LEGGINGS -> EquipmentSlot.LEGS;
			case BOOTS -> EquipmentSlot.FEET;
			default -> EquipmentSlot.CHEST;
		};
	}

	public IronManSuitPartRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public void render(IronManSuitPartEntity entity, float entityYaw, float partialTicks, PoseStack pose,
			MultiBufferSource buffers, int packedLight) {
		if (entity.level() == null) {
			return;
		}
		var item = IronManItems.armor(entity.suitId(), entity.part());
		if (item == null) {
			super.render(entity, entityYaw, partialTicks, pose, buffers, packedLight);
			return;
		}
		ArmorStand stand = STANDS.computeIfAbsent(entity, IronManSuitPartRenderer::makeStand);
		if (stand == null) {
			return;
		}
		EquipmentSlot worn = slotFor(entity.part());
		for (EquipmentSlot s : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			stand.setItemSlot(s, s == worn ? new ItemStack(item) : ItemStack.EMPTY);
		}

		float age = entity.tickCount + partialTicks;
		pose.pushPose();
		pose.translate(0.0, 0.1, 0.0);
		pose.mulPose(Axis.YP.rotationDegrees(age * 14f));
		pose.mulPose(Axis.XP.rotationDegrees((float) Math.sin(age * 0.2f) * 12f));
		pose.scale(0.85f, 0.85f, 0.85f);

		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partialTicks, pose, buffers, packedLight);
		dispatcher.setRenderShadow(true);
		pose.popPose();

		super.render(entity, entityYaw, partialTicks, pose, buffers, packedLight);
	}

	private static ArmorStand makeStand(IronManSuitPartEntity entity) {
		if (entity.level() == null) {
			return null;
		}
		ArmorStand stand = new ArmorStand(entity.level(), 0, 0, 0);
		stand.setInvisible(true); // only the armour piece shows
		stand.setNoBasePlate(true);
		stand.setShowArms(true);
		stand.setNoGravity(true);
		stand.setYRot(0f);
		stand.yBodyRot = 0f;
		stand.yHeadRot = 0f;
		return stand;
	}

	@Override
	public ResourceLocation getTextureLocation(IronManSuitPartEntity entity) {
		return FALLBACK;
	}
}
