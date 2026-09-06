package com.projecthero.mod.client.render;

import java.util.List;

import com.projecthero.mod.armor.SuperheroArmorItem;
import com.projecthero.mod.hero.power.p15.InvisibilityLightHandlers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * The one in-world armour renderer for every superhero armour set. GeckoLib's own
 * {@code HumanoidArmorLayerMixin} calls into this (via the {@link SuperheroArmorRenderProvider}
 * installed on {@link SuperheroArmorItem}) instead of the vanilla armour model, for any equipped
 * {@link SuperheroArmorItem}.
 *
 * <p>Bone visibility per slot (HEAD -> {@code armorHead}; CHEST -> {@code armorBody} + both arms;
 * LEGS -> both legs; FEET -> both boots) and following the player's live pose (walk / crouch / jump /
 * swim / ride, plus the mod's flight pose applied to the base {@code HumanoidModel}) are handled by
 * {@link GeoArmorRenderer} itself, because the supplied geometry uses GeckoLib's default armour bone
 * names ({@code armorHead}, {@code armorBody}, {@code armorRightArm}/{@code Left},
 * {@code armorRightLeg}/{@code Left}, {@code armorRightBoot}/{@code Left}) -- see
 * {@link #applyBoneVisibilityBySlot} for the one place that stock behaviour is overridden.
 */
public class SuperheroArmorRenderer extends GeoArmorRenderer<SuperheroArmorItem> {
	/**
	 * Every bone under the cubeless {@code armorHead} parent -- see {@link #setHelmetHidden}. This is
	 * the union across all suit geometries, not the bones of any one model: {@code helmet_brow} exists
	 * only on {@code mark_1}. Bones a given geo does not have are simply skipped ({@code getBone}
	 * returns an empty {@code Optional}), so one list serves every set.
	 */
	private static final List<String> HEAD_ARMOR_BONES = List.of(
			"helmet", "faceplate", "helmet_brow",
			// Max Steel's head layers
			"helmet_crown", "chin_guard", "head_shell");

	public SuperheroArmorRenderer() {
		super(new SuperheroArmorModel());
	}

	/**
	 * GeckoLib's own {@code applyBoneVisibilityBySlot} decides whether each bone is shown by copying
	 * the visibility flag off the corresponding part of the vanilla {@code HumanoidModel} it was handed
	 * as {@code original} for this slot's render pass ({@code baseModel.rightLeg.visible}, etc.) --
	 * reasonable for a mod re-skinning individual vanilla parts, but for LEGS specifically vanilla hands
	 * GeckoLib its separate slimmer <em>inner</em> armour model (leggings sit closer to the body than a
	 * chestplate or boots), a distinct {@code HumanoidModel} instance from the one HEAD/CHEST/FEET use.
	 * Reported bug: a fully-suited player rendered helmet + chestplate + boots correctly but the
	 * thigh/knee/shin plates never appeared -- LEGS is the one slot where that swapped-in instance's own
	 * {@code rightLeg}/{@code leftLeg} flags aren't reliably the "worn and visible" state GeckoLib
	 * assumes, so the copied flag reads false and {@code setBoneVisible} hides real, correctly-modelled
	 * geometry.
	 *
	 * <p>The shared {@code crimson_vanguard} model has no legitimate reason to hide a bone whose slot is
	 * actually worn -- unlike a vanilla per-limb quirk (a sleeping/riding pose, say), nothing about this
	 * armour set depends on that borrowed flag. So this override reimplements the same per-slot mapping
	 * GeckoLib documents, but shows the slot's own bones unconditionally instead of trusting whichever
	 * {@code HumanoidModel} instance happened to be passed in -- fixing LEGS without changing behaviour
	 * for the three slots that were already working.
	 */
	@Override
	protected void applyBoneVisibilityBySlot(EquipmentSlot slot) {
		setAllBonesVisible(false);
		switch (slot) {
			// (Max Steel pixel-reveal is applied AFTER this switch -- see below.)
			case HEAD -> {
				setBoneVisible(this.head, true);
				// "changes 19"/"changes 20": retract the helmet while the pilot has their Iron Man
				// faceplate open (H key), so their own skin shows through. v0.6.16: same for Max Steel.
				boolean ironManFaceplate = getCurrentEntity() instanceof Player p
						&& p.getItemBySlot(EquipmentSlot.HEAD).getItem()
								instanceof com.projecthero.mod.ironman.item.IronManArmorItem
						&& com.projecthero.mod.ironman.IronManFaceplate.isOpen(p);
				boolean maxSteelHelmet = getCurrentEntity() instanceof Player mp
						&& mp.getItemBySlot(EquipmentSlot.HEAD).getItem()
								instanceof com.projecthero.mod.maxsteel.item.MaxSteelArmorItem
						&& com.projecthero.mod.maxsteel.MaxSteelFaceplate.isOpen(mp);
				// v0.6.20: the Spider-Man costume's mask (H key) works the same way -- the mask geometry
				// all lives inside armorHead, so dropping the whole head bone is the clean "mask off".
				boolean spiderMask = getCurrentEntity() instanceof Player sp
						&& sp.getItemBySlot(EquipmentSlot.HEAD).getItem()
								instanceof com.projecthero.mod.spider.item.SpiderManArmorItem
						&& com.projecthero.mod.spider.SpiderMask.isOpen(sp);
				setHelmetHidden(ironManFaceplate || maxSteelHelmet);
				if (maxSteelHelmet || spiderMask) {
					// The undersuit / hood head layer covers the face too, so drop the whole head bone
					// for a clean reveal -- not just the crown / faceplate / chin guard.
					setBoneVisible(this.head, false);
				}
			}
			case CHEST -> {
				setBoneVisible(this.body, true);
				setBoneVisible(this.rightArm, true);
				setBoneVisible(this.leftArm, true);
			}
			case LEGS -> {
				setBoneVisible(this.rightLeg, true);
				setBoneVisible(this.leftLeg, true);
			}
			case FEET -> {
				setBoneVisible(this.rightBoot, true);
				setBoneVisible(this.leftBoot, true);
			}
			default -> {
			}
		}

		// Max Steel: while a suit-up / suit-down is animating, hide the bones the pixel-reveal clock has
		// not reached yet. The reveal set is global (not per-slot), so re-applying it after every slot's
		// visibility pass is correct -- the last write for this pass wins.
		if (getCurrentEntity() instanceof Player player
				&& player.getItemBySlot(slot).getItem() instanceof com.projecthero.mod.maxsteel.item.MaxSteelArmorItem
				&& com.projecthero.mod.client.maxsteel.MaxSteelReveal.isRevealing(player)) {
			for (String bone : com.projecthero.mod.client.maxsteel.MaxSteelReveal.boneNames()) {
				if (com.projecthero.mod.client.maxsteel.MaxSteelReveal.hidden(player, bone)) {
					getGeoModel().getBone(bone).ifPresent(b -> b.setHidden(true));
				}
			}
		}
		// Symbiote: same idea, chest -> arms+legs -> head.
		if (getCurrentEntity() instanceof Player symbiotePlayer
				&& symbiotePlayer.getItemBySlot(slot).getItem() instanceof com.projecthero.mod.spider.item.SymbioteArmorItem
				&& com.projecthero.mod.client.symbiote.SymbioteReveal.isRevealing(symbiotePlayer)) {
			for (String bone : com.projecthero.mod.client.symbiote.SymbioteReveal.boneNames()) {
				if (com.projecthero.mod.client.symbiote.SymbioteReveal.hidden(symbiotePlayer, bone)) {
					getGeoModel().getBone(bone).ifPresent(b -> b.setHidden(true));
				}
			}
		}
	}

	/**
	 * Stash the wearer for {@link com.projecthero.mod.armor.ArmorRenderContext} before GeckoLib resolves
	 * the model -- Max Steel's {@code armorSetId()} depends on which Turbo Mode the wearer is in, so
	 * the geo/texture can swap to the matching form.
	 */
	@Override
	public void prepForRender(net.minecraft.world.entity.Entity entity, net.minecraft.world.item.ItemStack stack,
			net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.client.model.HumanoidModel<?> baseModel,
			net.minecraft.client.renderer.MultiBufferSource bufferSource, float partialTick, float limbSwing,
			float limbSwingAmount, float netHeadYaw, float headPitch) {
		com.projecthero.mod.armor.ArmorRenderContext.set(entity);
		super.prepForRender(entity, stack, slot, baseModel, bufferSource, partialTick, limbSwing,
				limbSwingAmount, netHeadYaw, headPitch);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, @Nullable VertexConsumer buffer, int packedLight,
			int packedOverlay, int colour) {
		try {
			// Light Manipulation's cloak hides the whole player including armour -- mirror the mod's
			// HumanoidArmorLayerMixin so the GeckoLib armour path respects it too.
			if (getCurrentEntity() instanceof Player player && InvisibilityLightHandlers.hideArmor(player)) {
				return;
			}
			// Max Steel's Turbo Stealth fades the suit out entirely.
			if (getCurrentEntity() instanceof Player p
					&& com.projecthero.mod.client.maxsteel.MaxSteelReveal.isStealthed(p)) {
				return;
			}
			super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, colour);
		} finally {
			com.projecthero.mod.armor.ArmorRenderContext.clear();
		}
	}

	/**
	 * Shows or hides the entire head armour -- the {@code helmet} shell and the {@code faceplate}
	 * visor together -- for the open-faceplate state ({@link com.projecthero.mod.ironman.IronManFaceplate}).
	 *
	 * <p>Reported bug ("changes 20"): pressing H opened the faceplate but the wearer's skin never
	 * appeared underneath. The cause is in the geometry, not the toggle. {@code crimson_vanguard}'s
	 * {@code helmet} bone is two complete 8x8x8 boxes around the head (inflate 0.2 and 0.45), each
	 * with its own north face, and {@code faceplate} is only a thin 0.45-deep slab sitting in front of
	 * them. Hiding {@code faceplate} alone therefore peeled off the outer visor plate and left two
	 * solid helmet walls still covering the face -- the player was looking at the inside of the helmet,
	 * which reads as "nothing happened". Hiding the shell as well is what actually exposes the head,
	 * and it matches how the toggle is described in-fiction anyway: the helmet retracts.
	 *
	 * <p>The bones are named explicitly rather than hiding their shared {@code armorHead} parent. On
	 * GeckoLib 4.9.2 {@code GeoBone.setHidden} does also set {@code childrenHidden}, so hiding
	 * {@code armorHead} would in fact work -- but it is the bone {@code applyBoneVisibilityBySlot}
	 * turns back <em>on</em> for the HEAD pass, so the two would fight over the same flag. Listing the
	 * child bones keeps the retract state independent of the slot-visibility pass. Visibility is
	 * written on every HEAD pass in both directions, so nothing sticks hidden once the faceplate
	 * closes again.
	 */
	private void setHelmetHidden(boolean hidden) {
		for (String bone : HEAD_ARMOR_BONES) {
			getGeoModel().getBone(bone).ifPresent(b -> b.setHidden(hidden));
		}
	}
}
