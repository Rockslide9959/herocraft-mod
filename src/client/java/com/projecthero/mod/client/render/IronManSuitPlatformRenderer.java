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
		// v0.15.8, user request: the Suitcase tab -- the racked Mark 5 folds itself up (the suit-down backwards, twice as
		// fast), then the case forms at its chest, drops to the pad and hops off to its owner
		float packAge = be.packAge(partialTick);
		if (packAge >= 0f) {
			renderPack(be, rack, packAge, partialTick, pose, buffers, packedLight);
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

	/** v0.15.8: the fold -- the stand at its Mark 5 frame, then the suitcase forming, dropping and hopping away. */
	private void renderPack(IronManSuitPlatformBlockEntity be, ArmorStand rack, float age, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int light) {
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		float frame = com.projecthero.mod.ironman.suit.IronManMk5Suitcase.UP_TICKS - 6 - 2f * age; // 134 -> 26
		if (age < IronManSuitPlatformBlockEntity.PACK_FOLD_TICKS) {
			dispatcher.setRenderShadow(false);
			pose.pushPose();
			pose.translate(0.5, DISPLAY_Y_OFFSET, 0.5);
			pose.scale(RACK_SCALE, RACK_SCALE, RACK_SCALE);
			com.projecthero.mod.client.ironman.IronManGantryBuild.standMk5Frame = frame;
			try {
				dispatcher.render(rack, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, light);
			} finally {
				com.projecthero.mod.client.ironman.IronManGantryBuild.standMk5Frame = Float.NaN;
			}
			pose.popPose();
			dispatcher.setRenderShadow(true);
		}
		// the case: grows out of the chest as the chest top folds away, closes, drops to the pad, then hops off
		float caseFrom = IronManSuitPlatformBlockEntity.PACK_FOLD_TICKS - 4;
		float grow = smooth((age - caseFrom) / 8f);
		if (grow <= 0.01f) {
			return;
		}
		float drop = smooth((age - (caseFrom + 8)) / 5f);
		float hop = smooth((age - (caseFrom + 14)) / 6f);
		float chestY = (float) (DISPLAY_Y_OFFSET + 20.0 / 16.0 * RACK_SCALE);
		float y = chestY + (0.25f - chestY) * drop + 0.6f * (float) Math.sin(Math.PI * hop);
		float scale = 0.55f * grow * (1f - hop);
		if (scale <= 0.01f) {
			return;
		}
		if (caseStack == null) {
			caseStack = new ItemStack(com.projecthero.mod.ironman.item.IronManItems.MARK_V_SUITCASE);
		}
		pose.pushPose();
		pose.translate(0.5, y, 0.5);
		pose.scale(scale, scale, scale);
		com.projecthero.mod.client.ironman.MarkVSuitcaseRenderer.openness = 1f - smooth((age - (caseFrom + 4)) / 4f);
		try {
			Minecraft.getInstance().getItemRenderer().renderStatic(caseStack, net.minecraft.world.item.ItemDisplayContext.NONE,
					light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers, be.getLevel(), 0);
		} finally {
			com.projecthero.mod.client.ironman.MarkVSuitcaseRenderer.openness = 0f;
		}
		pose.popPose();
	}

	private static ItemStack caseStack;

	private static float smooth(float x) {
		x = Math.max(0f, Math.min(1f, x));
		return x * x * (3f - 2f * x);
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
