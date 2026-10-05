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
		// v0.14.21 round two: Iron Man eyes / arc reactor / palm repulsors glow (no-op for every other set)
		addRenderLayer(new com.projecthero.mod.client.ironman.IronManSuitGlowLayer(this));
	}

	/**
	 * v0.14.21 round two: the Mark V's real gauntlet blades. {@code geo/mark_v.geo.json} carries {@code right_blade} /
	 * {@code left_blade} bones (pivot at the top of the blade); each frame they are shown and stretched along Y by the
	 * wearer's eased extension ({@link com.projecthero.mod.client.ironman.IronManBladeClient}), so they slide out of and
	 * back into the gauntlets over {@link com.projecthero.mod.ironman.IronManBladeLook#EXTEND_TICKS} ticks. Set here, after
	 * GeckoLib has applied the animation pose, so no clip can reset it.
	 */
	@Override
	public void renderRecursively(PoseStack poseStack, SuperheroArmorItem animatable, software.bernie.geckolib.cache.object.GeoBone bone,
			net.minecraft.client.renderer.RenderType renderType, net.minecraft.client.renderer.MultiBufferSource bufferSource,
			VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
		String name = bone.getName();
		if ("right_blade".equals(name) || "left_blade".equals(name)) {
			float ext = getCurrentEntity() instanceof Player p
					? com.projecthero.mod.client.ironman.IronManBladeClient.extension(p, partialTick) : 0f;
			boolean show = com.projecthero.mod.ironman.IronManBladeLook.visible(ext);
			bone.setHidden(!show);
			if (!show) {
				return;
			}
			bone.setScaleY(com.projecthero.mod.ironman.IronManBladeLook.boneScale(ext));
		}
		// v0.14.21 self-assembly + faceplate lift: per-bone offset from the synced suit clock, in the parent's space
		if (animatable instanceof com.projecthero.mod.ironman.item.IronManArmorItem && getCurrentEntity() instanceof Player ip
				&& getCurrentSlot() != null) {
			poseStack.pushPose();
			try {
				if (!com.projecthero.mod.client.ironman.IronManAssemblyClient.apply(poseStack, bone, ip, getCurrentSlot(), partialTick)) {
					return;
				}
				// the H faceplate is up: drop the helmet's front faces so the wearer's face shows (shell + brow stay on)
				skipNorthFaces = "helmet".equals(name) && getCurrentSlot() == EquipmentSlot.HEAD
						&& com.projecthero.mod.client.ironman.IronManAssemblyClient.helmetFrontHidden(ip, partialTick);
				super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
						packedLight, packedOverlay, colour);
			} finally {
				skipNorthFaces = false;
				poseStack.popPose();
			}
			return;
		}
		// v0.14.28: the send-home suit standing in front of its owner builds itself with the same base-halves visual
		float standP = com.projecthero.mod.client.ironman.IronManSuitReveal.standBuild;
		if (standP >= 0f && standP < 1f && animatable instanceof com.projecthero.mod.ironman.item.IronManArmorItem
				&& !(getCurrentEntity() instanceof Player)) {
			poseStack.pushPose();
			try {
				if (com.projecthero.mod.client.ironman.IronManAssemblyClient.applyBuild(poseStack, bone, standP)) {
					super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
							partialTick, packedLight, packedOverlay, colour);
				}
			} finally {
				poseStack.popPose();
			}
			return;
		}
		super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
				packedLight, packedOverlay, colour);
	}

	/** v0.14.21: set while drawing the {@code helmet} bone with the faceplate raised (see {@link #renderRecursively}). */
	private boolean skipNorthFaces;

	@Override
	public void createVerticesOfQuad(software.bernie.geckolib.cache.object.GeoQuad quad, org.joml.Matrix4f poseState,
			org.joml.Vector3f normal, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
		if (skipNorthFaces && quad.direction() == net.minecraft.core.Direction.NORTH) {
			return;
		}
		super.createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, colour);
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
				// v0.6.16: retract the Max Steel helmet while its faceplate is open. (The Iron Man helmet no longer
				// retracts: since v0.14.21 only the faceplate lifts on its hinge and the helmet's front faces are
				// skipped -- see renderRecursively / IronManAssemblyClient.)
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
				setHelmetHidden(maxSteelHelmet);
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

		// Max Steel (v0.14.2) and the Symbiote no longer hide whole bones -- their suits materialise pixel by pixel in
		// the texture instead, see getRenderType below (MaxSteelNano / SymbioteDissolve).
		// Symbiote: v0.13.19 no longer hides whole bones -- the suit materialises pixel by pixel instead, see
		// getRenderType below and SymbioteDissolve.
	}

	/**
	 * v0.13.19: while a Symbiote suit (Normal host, Black Suit Spider-Man or Agent Venom) is coming on or going
	 * off, draw it with the pixel-dissolve copy of its texture that matches the transform clock, so it spreads
	 * over the host one pixel at a time.
	 */
	@Override
	public net.minecraft.client.renderer.RenderType getRenderType(SuperheroArmorItem animatable,
			net.minecraft.resources.ResourceLocation texture, @Nullable net.minecraft.client.renderer.MultiBufferSource bufferSource,
			float partialTick) {
		if (getCurrentEntity() instanceof Player p && isSymbioteSuit(animatable)
				&& com.projecthero.mod.client.symbiote.SymbioteReveal.isRevealing(p)) {
			texture = com.projecthero.mod.client.symbiote.SymbioteDissolve.texture(texture,
					com.projecthero.mod.client.symbiote.SymbioteReveal.progress(p, partialTick));
		}
		// v0.13.20: Moon Knight's suit materialises one pixel at a time too (and dissolves the same way on H)
		if (getCurrentEntity() instanceof Player mk && animatable instanceof com.projecthero.mod.moonknight.item.MoonKnightArmorItem) {
			float reveal = com.projecthero.mod.client.moonknight.MoonKnightReveal.progress(mk, partialTick);
			com.projecthero.mod.moonknight.MoonKnightAlter from = com.projecthero.mod.client.moonknight.MoonKnightReveal.swapFrom(mk);
			if (reveal < 1.0f) {
				texture = com.projecthero.mod.client.symbiote.SymbioteDissolve.texture(texture, reveal);
			} else if (from != null) {
				// v0.13.21: a new alter's suit rematerialises over the old one, pixel by pixel
				texture = com.projecthero.mod.client.moonknight.MoonKnightSuitSwap.texture(
						com.projecthero.mod.ProjectHeroMod.id(from.suitTexture()), texture,
						com.projecthero.mod.client.moonknight.MoonKnightReveal.swapProgress(mk, partialTick));
			}
		}
		// v0.14.2: Max Steel's nanotech reveal / power-down / mode swap, one texel at a time from the chest core
		if (getCurrentEntity() instanceof Player ms && animatable instanceof com.projecthero.mod.maxsteel.item.MaxSteelArmorItem) {
			texture = com.projecthero.mod.client.maxsteel.MaxSteelNano.texture(ms, texture, partialTick);
		}
		// v0.13.21: the Green Lantern suit sweeps on from the shoulders down one pixel row at a time (ArmorSweepReveal)
		if (getCurrentEntity() instanceof Player gl && animatable instanceof com.projecthero.mod.greenlantern.item.GreenLanternArmorItem) {
			texture = com.projecthero.mod.client.greenlantern.GreenLanternSuitReveal.texture(gl, texture, partialTick);
		}
		// v0.14.4: Thor's Armour forms piece by piece (boots -> greaves -> chest), each sweeping up from a lightning edge
		if (getCurrentEntity() instanceof Player th && animatable instanceof com.projecthero.mod.thorarmor.ThorArmorItem) {
			texture = com.projecthero.mod.client.thor.ThorSuitReveal.texture(th, getCurrentSlot(), texture, partialTick);
		}
		// v0.14.21: an Iron Man piece builds on plate by plate as it locks on (and breaks away coming off)
		if (getCurrentEntity() instanceof Player im && animatable instanceof com.projecthero.mod.ironman.item.IronManArmorItem ima) {
			texture = com.projecthero.mod.client.ironman.IronManSuitReveal.texture(im, ima.armorSetId(), getCurrentSlot(), texture, partialTick);
		} else if (animatable instanceof com.projecthero.mod.ironman.item.IronManArmorItem ima2
				&& com.projecthero.mod.client.ironman.IronManSuitReveal.standBuild >= 0f) {
			texture = com.projecthero.mod.client.ironman.IronManSuitReveal.textureAt(
					com.projecthero.mod.client.ironman.IronManSuitReveal.standBuild, true, false, ima2.armorSetId(),
					getCurrentSlot(), texture);
		}
		// v0.14.29 agent F: a damaged Iron Man suit (integrity < 35%) wears a scorched / cracked copy of its texture
		if (getCurrentEntity() instanceof Player bd && animatable instanceof com.projecthero.mod.ironman.item.IronManArmorItem bdi) {
			texture = com.projecthero.mod.client.ironman.IronManBattleDamage.texture(bd, bdi, texture);
		}
		// v0.14.11: the Flash Suit pours out of the ring on the right fist, texel by texel behind a lightning edge
		if (getCurrentEntity() instanceof Player fl && animatable instanceof com.projecthero.mod.flash.item.FlashSuitItem) {
			texture = com.projecthero.mod.client.flash.FlashSuitReveal.texture(fl, texture, partialTick);
		}
		return super.getRenderType(animatable, texture, bufferSource, partialTick);
	}

	private static boolean isSymbioteSuit(SuperheroArmorItem item) {
		return item instanceof com.projecthero.mod.spider.item.SymbioteArmorItem
				|| item instanceof com.projecthero.mod.symbiote.item.AgentVenomArmorItem
				|| item instanceof com.projecthero.mod.symbiote.item.SymbioteHostArmorItem;
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
