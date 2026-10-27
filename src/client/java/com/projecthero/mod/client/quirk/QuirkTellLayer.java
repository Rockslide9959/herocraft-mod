package com.projecthero.mod.client.quirk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.client.quirk.fx.QuirkFxDraw;
import com.projecthero.mod.hero.PowerSuppression;
import com.projecthero.mod.quirk.PlusUltra;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.quirk.meter.QuirkBand;
import com.projecthero.mod.quirk.meter.QuirkMeterSpec;
import com.projecthero.mod.quirk.meter.QuirkMeters;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

/**
 * v0.17.1 Quirk framework: the "Quirk tells" layer -- a small visual that says "this player's Quirk is working", seen by
 * everyone. The kit draws its own ({@link QuirkClientKit#renderTell}: Endeavor's flame beard, Bakugo's palm sparks,
 * Uraraka's glowing pads); until it does, the framework's generic tell is a soft, pulsing glow at both hands in the Quirk's
 * colour while the meter is in its danger zone or pushed past 100, and while the Plus Ultra C is held.
 */
public class QuirkTellLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
	public QuirkTellLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
			float partial, float ageInTicks, float netHeadYaw, float headPitch) {
		Quirk q = Quirks.quirk(player);
		if (q == null || q.standalone || player.isInvisible() || PowerSuppression.isSuppressed(player)) {
			return;
		}
		QuirkClientKit kit = QuirkClient.kitFor(q);
		if (kit != null && kit.renderTell(pose, buffers, light, player, getParentModel(), partial)) {
			return;
		}
		float k = genericIntensity(player);
		if (k <= 0.01f) {
			return;
		}
		float pulse = 0.75f + 0.25f * Mth.sin(ageInTicks * 0.5f);
		VertexConsumer vc = QuirkFxDraw.glow(buffers, QuirkFxDraw.WHITE);
		int rgb = q.meterColour & 0xFFFFFF;
		for (ModelPart arm : new ModelPart[] { getParentModel().rightArm, getParentModel().leftArm }) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			pose.translate(arm == getParentModel().rightArm ? -0.06f : 0.06f, 0.62f, 0f);
			QuirkFxDraw.shell(vc, pose.last(), 0.11f + 0.05f * k * pulse, 0.25f, player.getId() * 31L, ageInTicks * 0.6f, 0xFFFFFF, 0.45f * k);
			QuirkFxDraw.shell(vc, pose.last(), 0.2f + 0.08f * k * pulse, 0.3f, player.getId() * 17L, ageInTicks * 0.4f, rgb, 0.3f * k * pulse);
			pose.popPose();
		}
	}

	/** The generic tell's strength (0 = off). */
	static float genericIntensity(AbstractClientPlayer player) {
		float hold = PlusUltra.clientHold(player);
		if (hold >= 0f) {
			return 0.5f + 0.5f * hold;
		}
		QuirkMeterSpec spec = QuirkMeters.spec(Quirks.quirk(player));
		if (spec == null) {
			return 0f;
		}
		QuirkBand band = QuirkMeters.clientBand(player);
		if (band == QuirkBand.BEYOND) {
			return 1f;
		}
		return spec.channelIsMeter && spec.direction == QuirkMeterSpec.Direction.BUILDS && spec.dangerous(Quirks.clientMeter(player)) ? 0.6f : 0f;
	}
}
