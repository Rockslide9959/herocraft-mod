package com.projecthero.mod.client.ironman;

import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.entity.IronManSentryEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Rotations;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

/**
 * v0.15.9 Sentry Mode: the suit's real GeckoLib armour on an invisible, reusable client {@link ArmorStand} (the same trick
 * as {@code IronManDroneRenderer}), so the standing suit looks exactly like the worn one, lights included. Its back opens
 * as back panels swung open like doors ({@link IronManSentryClient#openDeg}, drawn by {@code SuperheroArmorRenderer}); every limb follows the
 * entity's eased pose channels ({@code IronManSentryEntity#animate}); a powered-down suit's lights are off.
 */
public class IronManSentryRenderer extends EntityRenderer<IronManSentryEntity> {
	private static final ResourceLocation FALLBACK = ProjectHeroMod.id("textures/item/repulsor.png");
	private static final Map<IronManSentryEntity, ArmorStand> STANDS = new WeakHashMap<>();

	public IronManSentryRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.4f;
	}

	@Override
	public void render(IronManSentryEntity sentry, float entityYaw, float partialTick, PoseStack pose,
			MultiBufferSource buffers, int packedLight) {
		if (sentry.level() == null) {
			return;
		}
		ArmorStand stand = STANDS.computeIfAbsent(sentry, IronManSentryRenderer::makeStand);
		for (int i = 0; i < 4; i++) {
			ItemStack want = sentry.stack(i);
			if (!ItemStack.matches(stand.getItemBySlot(IronManSentryEntity.SLOTS[i]), want)) {
				stand.setItemSlot(IronManSentryEntity.SLOTS[i], want.copy());
			}
		}
		float yaw = sentry.getViewYRot(partialTick);
		boolean powered = sentry.powered();
		// every limb from the entity's eased pose channels (IronManSentryEntity#animate): no snapping between states
		stand.setHeadPose(new Rotations(sentry.pose(IronManSentryEntity.P_HEAD_X, partialTick),
				sentry.pose(IronManSentryEntity.P_HEAD_Y, partialTick), 0f));
		stand.setRightArmPose(new Rotations(sentry.pose(IronManSentryEntity.P_RARM_X, partialTick),
				sentry.pose(IronManSentryEntity.P_RARM_Y, partialTick), sentry.pose(IronManSentryEntity.P_RARM_Z, partialTick)));
		stand.setLeftArmPose(new Rotations(sentry.pose(IronManSentryEntity.P_LARM_X, partialTick),
				sentry.pose(IronManSentryEntity.P_LARM_Y, partialTick), sentry.pose(IronManSentryEntity.P_LARM_Z, partialTick)));
		stand.setRightLegPose(new Rotations(sentry.pose(IronManSentryEntity.P_RLEG_X, partialTick), 0f,
				1f + sentry.pose(IronManSentryEntity.P_RLEG_Z, partialTick)));
		stand.setLeftLegPose(new Rotations(sentry.pose(IronManSentryEntity.P_LLEG_X, partialTick), 0f,
				-1f + sentry.pose(IronManSentryEntity.P_LLEG_Z, partialTick)));
		float lean = sentry.pose(IronManSentryEntity.P_LEAN, partialTick);
		float bob = sentry.pose(IronManSentryEntity.P_BOB, partialTick);

		pose.pushPose();
		pose.mulPose(Axis.YP.rotationDegrees(-yaw));
		pose.translate(0.0, bob, 0.0);
		if (Math.abs(lean) > 0.05f) {
			pose.translate(0.0, 1.0, 0.0);
			pose.mulPose(Axis.XP.rotationDegrees(lean));
			pose.translate(0.0, -1.0, 0.0);
		}
		var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
		dispatcher.setRenderShadow(false);
		IronManSentryClient.openDeg = sentry.openProgress(partialTick) * IronManSentryClient.OPEN_DEG;
		IronManSentryClient.dark = !powered;
		IronManSentryClient.flash = sentry.pose(IronManSentryEntity.P_FLASH, partialTick);
		IronManBattleDamage.renderingIntegrity = IronManBattleDamage.sentryIntegrity(sentry); // v0.15.15 battle damage
		try {
			dispatcher.render(stand, 0.0, 0.0, 0.0, 0.0f, partialTick, pose, buffers, packedLight);
		} finally {
			IronManSentryClient.openDeg = 0f;
			IronManSentryClient.dark = false;
			IronManSentryClient.flash = 0f;
			IronManBattleDamage.renderingIntegrity = Float.NaN;
			dispatcher.setRenderShadow(true);
			pose.popPose();
		}
		super.render(sentry, entityYaw, partialTick, pose, buffers, packedLight);
	}

	private static ArmorStand makeStand(IronManSentryEntity sentry) {
		ArmorStand stand = new ArmorStand(sentry.level(), 0, 0, 0);
		stand.setInvisible(true); // only the armour shows
		stand.setNoBasePlate(true);
		stand.setShowArms(true);
		stand.setNoGravity(true);
		stand.setYRot(0f);
		stand.yBodyRot = 0f;
		stand.yHeadRot = 0f;
		return stand;
	}

	@Override
	public ResourceLocation getTextureLocation(IronManSentryEntity sentry) {
		return FALLBACK;
	}
}
