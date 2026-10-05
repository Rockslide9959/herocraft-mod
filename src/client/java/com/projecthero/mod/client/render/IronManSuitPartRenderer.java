package com.projecthero.mod.client.render;

import java.util.Map;
import java.util.WeakHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.entity.IronManSuitPartEntity;
import com.projecthero.mod.ironman.item.IronManItems;

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
	private static final ResourceLocation FALLBACK = ProjectHeroMod.id("textures/item/repulsor.png");

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
		// v0.14.21: draw the REAL stack in transit (enchantment glint, custom model data, ...), not a fresh piece
		ItemStack carried = entity.piece();
		if (carried.isEmpty()) {
			var item = IronManItems.armor(entity.suitId(), entity.part());
			if (item == null) {
				super.render(entity, entityYaw, partialTicks, pose, buffers, packedLight);
				return;
			}
			carried = new ItemStack(item);
		}
		ArmorStand stand = STANDS.computeIfAbsent(entity, IronManSuitPartRenderer::makeStand);
		if (stand == null) {
			return;
		}
		EquipmentSlot worn = slotFor(entity.part());
		for (EquipmentSlot s : new EquipmentSlot[] {
				EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack want = s == worn ? carried : ItemStack.EMPTY;
			if (!ItemStack.matches(stand.getItemBySlot(s), want)) {
				stand.setItemSlot(s, want);
			}
		}

		// The entity sits at the piece's own place on the body. In flight the piece tumbles at 85% size around that
		// point; over the last 40% of the curve it stops tumbling, turns to the owner's body yaw and grows to full size,
		// so it arrives exactly lined up on its slot (the armour stand's origin = the owner's feet) and clamps on.
		if (entity.homeMode()) {
			// v0.14.28 send-home: one piece of a standing suit -- upright, full size, building itself on, then flying home
			float build = entity.build();
			if (build <= 0f) {
				return; // its turn has not come yet
			}
			double slotH = com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotHeight(worn);
			float yaw = net.minecraft.util.Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
			pose.pushPose();
			pose.mulPose(Axis.YP.rotationDegrees(-yaw));
			pose.translate(0.0, -slotH, 0.0);
			var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
			dispatcher.setRenderShadow(false);
			com.projecthero.mod.client.ironman.IronManSuitReveal.standBuild = build < 1f ? build : -1f;
			try {
				dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partialTicks, pose, buffers, packedLight);
			} finally {
				com.projecthero.mod.client.ironman.IronManSuitReveal.standBuild = -1f;
				dispatcher.setRenderShadow(true);
				pose.popPose();
			}
			return;
		}
		float age = entity.tickCount + partialTicks;
		float align = smooth((entity.progress() - 0.6f) / 0.4f);
		float ownerYaw = 0f;
		if (entity.level().getEntity(entity.ownerEntityId()) instanceof net.minecraft.world.entity.LivingEntity owner) {
			ownerYaw = net.minecraft.util.Mth.rotLerp(partialTicks, owner.yBodyRotO, owner.yBodyRot);
		}
		float spinYaw = age * 14f;
		float yaw = net.minecraft.util.Mth.rotLerp(align, spinYaw, -ownerYaw);
		float scale = net.minecraft.util.Mth.lerp(align, 0.85f, 1.0f);
		double slotHeight = com.projecthero.mod.ironman.suit.IronManSuitUpManager.slotHeight(worn);
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees((1f - align) * (float) Math.sin(age * 0.2f) * 12f));
		pose.scale(scale, scale, scale);
		pose.translate(0.0, -slotHeight, 0.0);

		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partialTicks, pose, buffers, packedLight);
		dispatcher.setRenderShadow(true);
		pose.popPose();

		super.render(entity, entityYaw, partialTicks, pose, buffers, packedLight);
	}

	private static float smooth(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3f - 2f * x);
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
