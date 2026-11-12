package com.projecthero.mod.client.doctorfate;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.doctorfate.DoctorFate;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.17.3 Doctor Fate: the gold Cloak of Destiny, on the shared {@link FlowingCapeLayer} cloth (the Superman cape's mesh, collar and
 * wind physics): it hangs while he stands, billows while he hovers and streams behind in fast flight. Shown while the helmet is on
 * (and while Nabu holds the body).
 */
public class FateCloakLayer extends FlowingCapeLayer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/doctor_fate/cloak.png");

	public FateCloakLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return DoctorFate.possessed(player) || DoctorFate.helmetShown(player, 0f) > 0.5f;
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		return TEXTURE;
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		return DoctorFate.isFlying(player);
	}
}
