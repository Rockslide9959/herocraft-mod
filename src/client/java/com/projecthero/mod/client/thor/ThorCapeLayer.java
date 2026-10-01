package com.projecthero.mod.client.thor;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.render.FlowingCapeLayer;
import com.projecthero.mod.power.ThorPowers;
import com.projecthero.mod.thorarmor.ThorArmorItems;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * v0.14.16: Thor's crimson cape ("add a red cape to the thor suit"). The H-summoned Thor's Armour always promised "the
 * chestplate and cape" but its GeckoLib model ({@code geo/thor.geo.json}) never had a cape bone -- only the body /
 * arm / leg / boot shells -- so there was nothing to see. This draws a real flowing cloth cape (the shared
 * {@link FlowingCapeLayer}, the Superman Suit's cape physics) in deep crimson with a darker lining
 * ({@code textures/entity/thor_cape.png}) for anyone wearing the Thor's Armour chestplate, so every player sees it.
 *
 * <p>It streams in the wind of Thor's flight, swings and sways on the ground, follows the crouch, and unrolls down
 * from the collar in step with the chestplate's lightning reveal on suit-up (rolling back up on suit-down) -- see
 * {@link ThorSuitReveal#progress}.
 */
public class ThorCapeLayer extends FlowingCapeLayer {
	private static final ResourceLocation TEXTURE = ProjectHeroMod.id("textures/entity/thor_cape.png");

	public ThorCapeLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
		super(parent);
	}

	@Override
	protected boolean wearsCape(AbstractClientPlayer player) {
		return ThorArmorItems.CHESTPLATE != null && player.getItemBySlot(EquipmentSlot.CHEST).is(ThorArmorItems.CHESTPLATE);
	}

	@Override
	protected ResourceLocation texture(AbstractClientPlayer player) {
		return TEXTURE;
	}

	@Override
	protected boolean isFlying(AbstractClientPlayer player) {
		return ThorPowers.isFlying(player) || player.getAbilities().flying || player.isFallFlying();
	}

	@Override
	protected float unfurl(AbstractClientPlayer player, float partialTick) {
		return ThorSuitReveal.progress(player, EquipmentSlot.CHEST, partialTick);
	}
}
