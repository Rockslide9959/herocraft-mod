package com.herocraft.mod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.herocraft.mod.armor.SuperheroArmorItem;
import com.herocraft.mod.ironman.IronManFaceplate;
import com.herocraft.mod.ironman.item.IronManArmorItem;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Hides the wearer's skin <em>second layer</em> (the outer overlay -- hat, jacket, sleeves, trousers)
 * on any body region covered by a custom {@link SuperheroArmorItem}. The shared GeckoLib suit model
 * (crimson_vanguard) is a fitted humanoid shell; the vanilla overlay sits a fraction proud of the
 * base skin and otherwise showed through it as a translucent fringe around the arms and torso, which
 * made the suit read as scruffy.
 *
 * <p>Applied per body region, keyed off which armour slot actually holds a superhero piece -- a lone
 * helmet still leaves the body overlay intact. The base (first) skin layer is untouched.
 *
 * <p>Runs at {@code setupAnim} TAIL, i.e. after vanilla's {@code setModelProperties} has switched the
 * overlay parts on from the player's skin-customisation options, so this reliably wins. It fires in
 * third person and -- because {@code PlayerRenderer.renderHand} also routes through {@code setupAnim}
 * -- suppresses the first-person sleeve too, leaving the field clear for the armoured forearm drawn
 * by {@link com.herocraft.mod.client.render.SuperheroFirstPersonArm}. Cosmetic only.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin<T extends LivingEntity> {
	@Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void herocraft$hideSkinOverlayUnderArmor(T entity, float limbSwing, float limbSwingAmount,
			float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
		if (!(entity instanceof Player player)) {
			return;
		}
		// Access the overlay parts by casting rather than @Shadow: "hat" is declared on the SUPERclass
		// HumanoidModel, and Mixin resolves @Shadow fields only against the target class itself, so
		// shadowing it threw "@Shadow field hat was not located in the target class" at APPLY time and
		// took the whole client down during the resource reload. The fields are all public, so a plain
		// cast reaches every one of them (inherited or not) with no Mixin involvement.
		PlayerModel<?> model = (PlayerModel<?>) (Object) this;
		// Max Steel's suit-up / suit-down is a pixel-by-pixel reveal -- the player's own skin (second
		// layer included) must stay visible underneath while the suit forms, so do not suppress it
		// until the reveal has settled.
		if (com.herocraft.mod.client.maxsteel.MaxSteelReveal.isRevealing(player)) {
			return;
		}
		// Same reasoning for the Symbiote's chest -> limbs -> head reveal: the real armour is stowed and
		// the black suit's bones are still hidden bone-by-bone while this runs, so suppressing the skin
		// overlay too made the wearer flash bare-skinned-and-unarmoured for the whole animation instead
		// of looking like the suit is spreading over their normal clothed body.
		if (com.herocraft.mod.client.symbiote.SymbioteReveal.isRevealing(player)) {
			return;
		}
		if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SuperheroArmorItem
				&& !helmetRetracted(player)) {
			model.hat.visible = false;
		}
		if (player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SuperheroArmorItem) {
			model.jacket.visible = false;
			model.leftSleeve.visible = false;
			model.rightSleeve.visible = false;
		}
		if (player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof SuperheroArmorItem) {
			model.leftPants.visible = false;
			model.rightPants.visible = false;
		}
	}

	/**
	 * True while an Iron Man helmet is worn but retracted by the H-key faceplate toggle
	 * ("changes 20"). The overlay suppression above exists because the fitted suit shell would
	 * otherwise show a translucent fringe of the skin's second layer through it -- but once
	 * {@link com.herocraft.mod.client.render.SuperheroArmorRenderer#setHelmetHidden} has taken the
	 * helmet away there is no shell left to clash with, and keeping the hat layer off would strip the
	 * wearer's hair, hat or glasses off the very face the toggle just exposed.
	 */
	private static boolean helmetRetracted(Player player) {
		if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem
				&& IronManFaceplate.isOpen(player)) {
			return true;
		}
		if (player.getItemBySlot(EquipmentSlot.HEAD).getItem()
				instanceof com.herocraft.mod.maxsteel.item.MaxSteelArmorItem
				&& com.herocraft.mod.maxsteel.MaxSteelFaceplate.isOpen(player)) {
			return true;
		}
		// v0.6.20: Spider-Man costume mask pulled off -- show the wearer's hair/face overlay.
		return player.getItemBySlot(EquipmentSlot.HEAD).getItem()
				instanceof com.herocraft.mod.spider.item.SpiderManArmorItem
				&& com.herocraft.mod.spider.SpiderMask.isOpen(player);
	}
}
