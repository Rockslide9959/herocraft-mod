package com.projecthero.mod.client.ultron.contagion;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ultron.contagion.entity.HijackedMarkEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Rotations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.16.1: a Hijacked Mark (the Iron Legion): the real Mark's GeckoLib armour on an invisible client {@link ArmorStand}
 * (the drone / suit-platform trick), so it looks exactly like that suit -- with no one inside. While {@link #legion} is
 * set the suit glow layer ({@code IronManSuitGlowLayer}) tints every lens, reactor and seam Ultron red. The arms come up
 * for a repulsor; both arms and the chest point at the target while the unibeam charges.
 */
public class HijackedMarkRenderer extends EntityRenderer<HijackedMarkEntity> {
	/** True while a Hijacked Mark's suit is being drawn: its glowmask goes red. */
	public static boolean legion;

	private static final ResourceLocation FALLBACK = ProjectHeroMod.id("textures/item/repulsor.png");
	private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	private static final ArmorItem.Type[] TYPES = { ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS };
	private static final Map<HijackedMarkEntity, ArmorStand> STANDS = new WeakHashMap<>();

	public HijackedMarkRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(HijackedMarkEntity mark, float entityYaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight) {
		ArmorStand stand = STANDS.computeIfAbsent(mark, HijackedMarkRenderer::makeStand);
		String suit = mark.suitId();
		for (int i = 0; i < 4; i++) {
			Item want = IronManItems.armor(suit, TYPES[i]);
			ItemStack have = stand.getItemBySlot(SLOTS[i]);
			if (want != null && !have.is(want)) {
				stand.setItemSlot(SLOTS[i], new ItemStack(want));
			}
		}
		float yaw = Mth.rotLerp(partialTick, mark.yBodyRotO, mark.yBodyRot);
		float pitch = Mth.clamp(mark.getViewXRot(partialTick), -60f, 60f);
		double dx = mark.getX() - mark.xo, dz = mark.getZ() - mark.zo;
		float lean = Math.min(30f, (float) Math.sqrt(dx * dx + dz * dz) * 55f);
		stand.setHeadPose(new Rotations(pitch, 0f, 0f));
		if (mark.chargingUnibeam()) {
			stand.setRightArmPose(new Rotations(-20f, -25f, 10f));
			stand.setLeftArmPose(new Rotations(-20f, 25f, -10f));
		} else if (mark.action() == HijackedMarkEntity.ACTION_REPULSOR) {
			stand.setRightArmPose(new Rotations(-90f + pitch, 0f, 0f));
			stand.setLeftArmPose(new Rotations(8f + lean * 0.4f, 0f, -8f));
		} else {
			stand.setRightArmPose(new Rotations(10f + lean * 0.4f, 0f, 8f));
			stand.setLeftArmPose(new Rotations(10f + lean * 0.4f, 0f, -8f));
		}
		stand.setRightLegPose(new Rotations(lean * 0.25f, 0f, 2f));
		stand.setLeftLegPose(new Rotations(lean * 0.25f, 0f, -2f));
		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		if (lean > 0.5f) {
			pose.translate(0.0, 1.0, 0.0);
			pose.mulPose(Axis.XP.rotationDegrees(lean));
			pose.translate(0.0, -1.0, 0.0);
		}
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		legion = true;
		try {
			dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
		} finally {
			legion = false;
			dispatcher.setRenderShadow(true);
			pose.popPose();
		}
		super.render(mark, entityYaw, partialTick, pose, buffers, packedLight);
	}

	private static ArmorStand makeStand(HijackedMarkEntity mark) {
		ArmorStand stand = new ArmorStand(mark.level(), 0, 0, 0);
		stand.setInvisible(true);
		stand.setNoBasePlate(true);
		stand.setShowArms(true);
		stand.setNoGravity(true);
		return stand;
	}

	@Override
	public ResourceLocation getTextureLocation(HijackedMarkEntity mark) {
		return FALLBACK;
	}
}
