package com.projecthero.mod.galactus.item;

import java.util.List;
import java.util.function.Consumer;

import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;
import com.projecthero.mod.galactus.entity.GalactusEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * v0.16.1: <b>the Ultimate Nullifier</b> -- assembled by the Silver Surfer from the Converters' parts. Hold right-click for
 * {@link GalactusConfig.Nullifier#useTicks} (3 s) while aiming at Galactus (anywhere on him, within
 * {@link GalactusConfig.Nullifier#range}): he retreats and the event is won. Used up; useless anywhere else.
 */
public class UltimateNullifierItem extends Item implements GeoItem {
	/** Set by the client (its GeckoLib item renderer). */
	public static Consumer<Consumer<GeoRenderProvider>> rendererFactory;
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	public UltimateNullifierItem(Properties properties) {
		super(properties);
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return Math.max(1, GalactusConfig.nullifier().useTicks);
	}

	@Override
	public UseAnim getUseAnimation(ItemStack stack) {
		return UseAnim.BOW;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server && target(server, player) == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.galactus.nullifier_no_target").withStyle(ChatFormatting.LIGHT_PURPLE),
					true);
			return InteractionResultHolder.fail(stack);
		}
		player.startUsingItem(hand);
		return InteractionResultHolder.consume(stack);
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		if (level instanceof ServerLevel server && user instanceof Player p) {
			Vec3 eye = p.getEyePosition();
			Vec3 tip = eye.add(p.getLookAngle().scale(1.2));
			server.sendParticles(GalactusFx.COSMIC_SMALL, tip.x, tip.y, tip.z, 4, 0.1, 0.1, 0.1, 0.02);
			int used = getUseDuration(stack, user) - remaining;
			if (used % 10 == 0) {
				server.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.6f + used * 0.02f);
				p.displayClientMessage(Component.translatable("message.projecthero.galactus.nullifier_charging",
						Math.round(100f * used / getUseDuration(stack, user))).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			}
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
		if (!(level instanceof ServerLevel server) || !(user instanceof ServerPlayer p)) {
			return stack;
		}
		GalactusEntity g = target(server, p);
		if (g == null || !aimedAt(p, g)) {
			p.displayClientMessage(Component.translatable("message.projecthero.galactus.nullifier_missed").withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return stack;
		}
		GalactusEvent e = g.event();
		if (e == null || !e.useNullifier(server, p)) {
			return stack;
		}
		stack.shrink(1);
		return stack;
	}

	/** The Galactus of the event {@code p} fights in, or null. */
	public static GalactusEntity target(ServerLevel level, Player p) {
		for (GalactusEvent e : GalactusEvent.all(level.getServer())) {
			if (e.dimension() == level.dimension() && e.isAt(level, p.blockPosition(), GalactusEvent.arena() + 64)) {
				GalactusEntity g = e.galactus(level);
				if (g != null) {
					return g;
				}
			}
		}
		return null;
	}

	/** Is {@code p} looking at {@code g} (his body, or a weak point), within range? */
	public static boolean aimedAt(Player p, GalactusEntity g) {
		double range = GalactusConfig.nullifier().range;
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(range));
		AABB body = g.getBoundingBox().inflate(4.0).expandTowards(0, 22, 0);
		if (body.contains(eye) || body.clip(eye, end).isPresent()) {
			return true;
		}
		for (int k = 0; k < 4; k++) {
			Vec3 w = g.weakPointPos(k);
			if (new AABB(w, w).inflate(5.0).clip(eye, end).isPresent()) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.ultimate_nullifier.hint").withStyle(ChatFormatting.LIGHT_PURPLE));
		tooltip.add(Component.translatable("item.projecthero.ultimate_nullifier.hint2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<UltimateNullifierItem>(this, "main", 0,
				state -> state.setAndContinue(com.projecthero.mod.galactus.entity.GalactusMob.raw(com.projecthero.mod.galactus.GalactusAnims.NULLIFIER_IDLE))));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	@Override
	public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
		if (rendererFactory != null) {
			rendererFactory.accept(consumer);
		}
	}
}
