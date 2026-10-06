package com.projecthero.mod.client.ironman;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManMk5Suitcase;
import com.projecthero.mod.ironman.suit.IronManSuitFx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * v0.14.21: the Mark V Suitcase in the right hand during a case suit-up / fold, read off the synced {@link IronManSuitFx}
 * pose clock so every viewer sees it. (The ordinary held-item draw of a case is suppressed for the same window by
 * {@code ItemInHandLayerSuitcaseMixin}, so it is never drawn twice.)
 * <ul>
 *   <li><b>Case up</b> ({@code POSE_CASE_UP}): the case is in the hand and springs open over the first
 *       {@value #OPEN_TICKS} ticks; the suit then spreads out of it over the body (the per-piece radial reveal in
 *       {@code IronManSuitReveal}); over the last {@value #SHRINK_TICKS} ticks the empty case folds into the gauntlet
 *       (shrinks away) -- the real item is used up at that moment.</li>
 *   <li><b>Case down</b> ({@code POSE_CASE_DOWN}): the open case grows out of the gauntlet, takes the pieces back as
 *       they break away, and folds shut over the last {@value #CLOSE_TICKS} ticks.</li>
 * </ul>
 */
public class MarkVSuitcaseLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	static final int OPEN_TICKS = 10;
	static final int SHRINK_TICKS = 8;
	static final int CLOSE_TICKS = 12;
	private static ItemStack caseStack;

	public MarkVSuitcaseLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	/** True while {@code player} is in a case pose (the layer draws the case; the held-item draw must not). */
	public static boolean active(net.minecraft.world.entity.player.Player player) {
		if (player.level() == null) {
			return false;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		return (fx.poseKind() == IronManSuitFx.POSE_CASE_UP || fx.poseKind() == IronManSuitFx.POSE_CASE_DOWN
				|| mk5(fx)) && fx.poseAge(player.level().getGameTime(), 0f) >= 0f;
	}

	/** v0.14.29: the Mark 5 build / fold holds the case in both hands -- the left held item is hidden too. */
	public static boolean bothHands(net.minecraft.world.entity.player.Player player) {
		return mk5(IronManSuitFx.of(player));
	}

	private static boolean mk5(IronManSuitFx fx) {
		return fx.poseKind() == IronManSuitFx.POSE_MK5_UP || fx.poseKind() == IronManSuitFx.POSE_MK5_DOWN;
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
			float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
		if (player.level() == null || player.isInvisible()) {
			return;
		}
		IronManSuitFx fx = IronManSuitFx.of(player);
		float age = fx.poseAge(player.level().getGameTime(), partialTick);
		if (age >= 0f && mk5(fx)) {
			renderMk5(pose, buffers, light, player, fx.poseKind() == IronManSuitFx.POSE_MK5_UP, age);
			return;
		}
		boolean up = fx.poseKind() == IronManSuitFx.POSE_CASE_UP;
		if (age < 0f || (!up && fx.poseKind() != IronManSuitFx.POSE_CASE_DOWN)) {
			return;
		}
		float total = fx.poseTicks();
		float open;
		float scale;
		if (up) {
			// the pose runs FACEPLATE_TICKS past the build; the case is gone by then
			float buildEnd = total - IronManSuitFx.FACEPLATE_TICKS;
			open = smooth(age / OPEN_TICKS);
			scale = 1f - smooth((age - (buildEnd - SHRINK_TICKS)) / SHRINK_TICKS);
		} else {
			open = 1f - smooth((age - (total - CLOSE_TICKS)) / CLOSE_TICKS);
			scale = smooth(age / 6f);
		}
		if (scale <= 0.01f) {
			return;
		}
		if (caseStack == null) {
			caseStack = new ItemStack(IronManItems.MARK_V_SUITCASE);
		}
		pose.pushPose();
		getParentModel().translateToHand(net.minecraft.world.entity.HumanoidArm.RIGHT, pose);
		// same hand frame vanilla's ItemInHandLayer uses
		pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
		pose.mulPose(Axis.YP.rotationDegrees(180.0F));
		pose.translate(-1.0F / 16.0F, 0.125F, -0.625F);
		pose.scale(scale, scale, scale);
		MarkVSuitcaseRenderer.openness = open;
		try {
			Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, caseStack,
					ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers, light);
		} finally {
			MarkVSuitcaseRenderer.openness = 0f;
		}
		pose.popPose();
	}

	/**
	 * v0.14.29: the Mark 5 case between both hands, in front of the body. Suit-up: held out, then pulled onto the chest
	 * where it opens and shrinks into the chestplate. Suit-down: the same backwards -- it grows out of the chestplate,
	 * closes, and is pushed back out in front of the body.
	 */
	private void renderMk5(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, boolean up,
			float age) {
		float scale = IronManMk5Suitcase.caseScale(up, age);
		if (scale <= 0.01f) {
			return;
		}
		// 0 = held out in front .. 1 = against the chest
		// v0.15.8: the suit-down is the suit-up backwards
		float f = IronManMk5Suitcase.frame(up, age);
		float toChest = smooth((f - IronManMk5Suitcase.HOLD_TICKS) / 10f);
		float open = smooth((f - IronManMk5Suitcase.HOLD_TICKS) / IronManMk5Suitcase.MORPH_TICKS);
		if (caseStack == null) {
			caseStack = new ItemStack(IronManItems.MARK_V_SUITCASE);
		}
		pose.pushPose();
		getParentModel().body.translateAndRotate(pose);
		// body space: +y down, -z in front. Held out: between the hands ~8 px in front, ~9 px below the neck;
		// against the chest: 3.5 px in front at sternum height.
		float y = Mth.lerp(toChest, 9.5f, 6.5f) / 16f;
		float z = Mth.lerp(toChest, -7.5f, -3.5f) / 16f;
		pose.translate(0f, y, z);
		pose.mulPose(Axis.ZP.rotationDegrees(180f)); // model y-up -> world up
		float s = 0.8f * scale;
		pose.scale(s, s, s);
		MarkVSuitcaseRenderer.openness = open;
		try {
			Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, caseStack,
					ItemDisplayContext.NONE, false, pose, buffers, light);
		} finally {
			MarkVSuitcaseRenderer.openness = 0f;
		}
		pose.popPose();
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3f - 2f * x);
	}
}
