package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.armor.SuperheroArmorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * One piece of an Iron Man suit. Carries its suit id and slot type so the restriction system
 * ({@link com.projecthero.mod.ironman.IronManArmor}), the ability system and the suit-summon system can
 * all identify it from the {@link ItemStack} alone.
 *
 * <p>The item deliberately does <em>not</em> grant abilities. Wearing it does nothing on its own:
 * every Iron Man capability is gated server-side on {@code player.hasPower(TONY_STARK) &&
 * isWearingValidIronManSuit()}. A player without the Tony Stark power who somehow gets a piece into
 * an armor slot has it ejected on the next server tick with "Stark armor rejects unauthorized user."
 * (see {@link com.projecthero.mod.ironman.IronManArmor#enforce}); the item is never destroyed.
 */
public class IronManArmorItem extends SuperheroArmorItem {
	private final String suitId;

	public IronManArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, String suitId) {
		super(material, type, properties);
		this.suitId = suitId;
	}

	public String suitId() {
		return suitId;
	}

	// v0.14.21: per-piece suit clips (crimson_vanguard.animation.json, shared by every mark's geometry)
	private static final software.bernie.geckolib.animation.RawAnimation LOCK_ON =
			software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.crimson_vanguard.suit_lock_on");
	private static final software.bernie.geckolib.animation.RawAnimation RELEASE =
			software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.crimson_vanguard.suit_release");
	private static final software.bernie.geckolib.animation.RawAnimation HELMET_OPEN =
			software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.crimson_vanguard.helmet_open");
	private static final software.bernie.geckolib.animation.RawAnimation HELMET_CLOSE =
			software.bernie.geckolib.animation.RawAnimation.begin().thenPlayAndHold("animation.crimson_vanguard.helmet_close");

	/**
	 * v0.14.21: on top of the shared idle, a {@code suit} controller plays the piece's lock-on / release clip while its
	 * synced clock ({@link com.projecthero.mod.ironman.suit.IronManSuitFx}) runs, and -- on the helmet -- the visor swing
	 * whenever the faceplate opens or closes. Purely client-side and driven only by synced state, so no GeckoLib
	 * trigger packets or stack ids are needed: for armour without a stack id GeckoLib keys the animation instance by
	 * (wearer entity id, slot), so each wearer's pieces animate independently and every viewer sees the same thing.
	 */
	@Override
	public void registerControllers(software.bernie.geckolib.animation.AnimatableManager.ControllerRegistrar controllers) {
		super.registerControllers(controllers);
		controllers.add(new software.bernie.geckolib.animation.AnimationController<>(this, "suit", 0, state -> {
			net.minecraft.world.entity.Entity e = state.getData(software.bernie.geckolib.constant.DataTickets.ENTITY);
			net.minecraft.world.entity.EquipmentSlot slot = state.getData(software.bernie.geckolib.constant.DataTickets.EQUIPMENT_SLOT);
			if (!(e instanceof Player p) || slot == null || p.level() == null) {
				return software.bernie.geckolib.animation.PlayState.STOP;
			}
			var fx = com.projecthero.mod.ironman.suit.IronManSuitFx.of(p);
			long now = p.level().getGameTime();
			int phase = fx.piecePhase(slot, now);
			software.bernie.geckolib.animation.RawAnimation clip = null;
			if (phase == com.projecthero.mod.ironman.suit.IronManSuitFx.PHASE_LOCK_ON) {
				clip = LOCK_ON;
			} else if (phase == com.projecthero.mod.ironman.suit.IronManSuitFx.PHASE_RELEASE) {
				clip = RELEASE;
			} else if (slot == net.minecraft.world.entity.EquipmentSlot.HEAD && fx.faceplateAge(now, 0f) >= 0f) {
				clip = com.projecthero.mod.ironman.IronManFaceplate.isOpen(p) ? HELMET_OPEN : HELMET_CLOSE;
			}
			if (clip == null) {
				return software.bernie.geckolib.animation.PlayState.STOP;
			}
			var controller = state.getController();
			if (controller.getAnimationState() == software.bernie.geckolib.animation.AnimationController.State.STOPPED
					|| controller.getCurrentRawAnimation() != clip) {
				controller.forceAnimationReset(); // a new phase always plays from frame 0, even the same clip again
			}
			return state.setAndContinue(clip);
		}));
	}

	/** The armour set id IS the suit id ({@code mark_iii}, ...) -- see {@link com.projecthero.mod.armor.SuperheroArmorVisuals}. */
	@Override
	public String armorSetId() {
		return suitId;
	}

	/**
	 * "changes 21": the Mark 1 is the only suit built at a normal crafting table -- record each piece
	 * as it is crafted so a full Mark 1 unlocks the Mark 2 blueprint in the Blank Blueprint picker.
	 * Every other mark is Fabricator-built and records its pieces from
	 * {@code StarkFabricatorBlockEntity}; this hook only ever fires for the table recipes.
	 */
	@Override
	public void onCraftedBy(ItemStack stack, Level level, Player player) {
		super.onCraftedBy(stack, level, player);
		if (player instanceof ServerPlayer serverPlayer
				&& com.projecthero.mod.ironman.TonyStark.hasPower(serverPlayer)) {
			com.projecthero.mod.ironman.TonyStark.recordSuitPiece(serverPlayer, suitId, getType());
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		tooltip.add(Component.translatable("item.projecthero.ironman.suit_piece",
				Component.translatable("projecthero.ironman.suit." + suitId + ".name")).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}
}
