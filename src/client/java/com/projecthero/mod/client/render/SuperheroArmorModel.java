package com.projecthero.mod.client.render;

import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.armor.SuperheroArmorVisuals;

import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.model.GeoModel;

/**
 * The shared GeckoLib {@link GeoModel} for every superhero armour set. It holds no per-set state --
 * it just forwards to {@link SuperheroArmorVisuals}, keyed by the piece's
 * {@link SuperheroArmorItem#armorSetId() armour-set id}. That is what lets one model + one renderer
 * serve Thor and all five Iron Man marks while each keeps its own texture (and, later, its own
 * geometry/animation) -- see docs/ARMOR_MODELS.md.
 */
public class SuperheroArmorModel extends GeoModel<SuperheroArmorItem> {
	@Override
	public ResourceLocation getModelResource(SuperheroArmorItem animatable) {
		return SuperheroArmorVisuals.get(animatable.armorSetId()).geometry();
	}

	/**
	 * v0.15.16 (user: the skin-based Green Lantern suits "look kinda thick"): every suit but the ring's own default one is a
	 * cloth-like costume made from a player skin, so it is drawn on a slimmer copy of the suit rig (shells 0.05 / 0.3 px
	 * off the body instead of 0.3 / 0.55, a thinner mask) -- same UVs, so the textures and the suit-up sweep are unchanged.
	 *
	 * <p>v0.15.18 (user: "skin pokes through the leg sides"): GeckoLib's {@code GeoArmorRenderer#applyBaseTransformations}
	 * places the leg bones at the vanilla leg x +/- 2 (i.e. 0.1 px toward the middle of the body, since vanilla legs sit at
	 * +/-1.9). The thick default rig's 0.3 px shell hides that; the slim rig's 0.05 px base layer did not, so the outer side
	 * of each leg sank 0.05 px inside the wearer's own leg and only the skin was visible from the side. The slim rig's leg
	 * cubes are moved 0.1 px outward to cancel the shift.
	 */
	private static final ResourceLocation GREEN_LANTERN_SLIM = com.projecthero.mod.ProjectHeroMod.id("geo/green_lantern_slim.geo.json");

	@Override
	public ResourceLocation getModelResource(SuperheroArmorItem animatable,
			software.bernie.geckolib.renderer.GeoRenderer<SuperheroArmorItem> renderer) {
		if ("green_lantern".equals(animatable.armorSetId()) && renderer instanceof software.bernie.geckolib.renderer.GeoArmorRenderer<?> armor
				&& armor.getCurrentEntity() instanceof net.minecraft.world.entity.player.Player wearer) {
			var st = wearer.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.GREEN_LANTERN_STATE, null);
			if (st != null && com.projecthero.mod.greenlantern.GreenLanternSuitStyle.byOrdinal(st.suitStyle)
					!= com.projecthero.mod.greenlantern.GreenLanternSuitStyle.DEFAULT) {
				return GREEN_LANTERN_SLIM;
			}
		}
		return getModelResource(animatable);
	}

	@Override
	public ResourceLocation getTextureResource(SuperheroArmorItem animatable) {
		return SuperheroArmorVisuals.get(animatable.armorSetId()).texture();
	}

	/**
	 * v0.12.16: the Wolverine Suit tears and bloodies as its wearer's health drops -- four textures,
	 * chosen by health fraction (over 90% clean, then 90 / 65 / 40%), and a fifth, almost fully torn off, during the Death Surge.
	 */
	@Override
	public ResourceLocation getTextureResource(SuperheroArmorItem animatable,
			software.bernie.geckolib.renderer.GeoRenderer<SuperheroArmorItem> renderer) {
		if ("wolverine".equals(animatable.armorSetId()) && renderer instanceof software.bernie.geckolib.renderer.GeoArmorRenderer<?> armor
				&& armor.getCurrentEntity() instanceof net.minecraft.world.entity.LivingEntity wearer) {
			int stage = com.projecthero.mod.client.wolverine.WolverineSuitWear.stage(wearer);
			if (stage > 0) {
				return com.projecthero.mod.ProjectHeroMod.id("textures/armor/wolverine_damaged_" + stage + ".png");
			}
		}
		return getTextureResource(animatable);
	}

	@Override
	public ResourceLocation getAnimationResource(SuperheroArmorItem animatable) {
		return SuperheroArmorVisuals.get(animatable.armorSetId()).animation();
	}
}
