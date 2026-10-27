package com.projecthero.mod.quirk.erasure;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.quirk.Quirk;
import com.projecthero.mod.quirk.Quirks;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * v0.17.1 Quirks revamp (docs/QUIRKS_REVAMP_PROPOSAL.md 3.5, "Catnap"): <b>Aizawa's Sleeping Bag</b>
 * ({@code projecthero:sleeping_bag}). Anyone can use it: right-click at night on the ground and you crawl into the yellow bag
 * where you stand and sleep like in a bed -- the night is skipped by the usual sleep vote -- but your spawn point never moves
 * (no {@code setRespawnPosition}: the bag is not a bed). An Erasure holder wakes with Dry Eyes at 0 and his eyes open.
 *
 * <p>How it works: {@link LivingEntity#startSleeping} on the block you stand in (vanilla's own sleep, the sleep screen, the vote),
 * and Fabric's {@link EntitySleepEvents#ALLOW_BED} keeps the sleep valid while the sleeper holds a bag (vanilla wakes anyone
 * whose "bed" is not a bed block; both sides check it, so the hand is what both sides can see). The same vanilla rules as a bed
 * apply: only at night (or in a thunderstorm), only in an overworld-like dimension, not with monsters close by. The bag is
 * drawn round the sleeper on every client ({@code SleepingBagLayer}). Crafted from five yellow wool and a string.
 */
public final class ErasureSleepingBag extends Item {
	public static Item SLEEPING_BAG;

	public ErasureSleepingBag(Properties props) {
		super(props);
	}

	static void initialize() {
		SLEEPING_BAG = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("sleeping_bag"), new ErasureSleepingBag(new Item.Properties().stacksTo(1)));
		EntitySleepEvents.ALLOW_BED.register((entity, sleepingPos, state, vanillaResult) -> {
			if (!vanillaResult && inBag(entity)) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		EntitySleepEvents.MODIFY_SLEEPING_DIRECTION.register((entity, sleepingPos, dir) -> {
			if (dir == null && inBag(entity)) {
				return Direction.fromYRot(entity.getYRot()).getOpposite();
			}
			return dir;
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer sp && inBag(sp) && Quirks.holds(sp, Quirk.ERASURE)) {
				ErasureMoves.refresh(sp); // Catnap: wakes with Dry Eyes at 0
			}
		});
	}

	/** Client-safe: {@code e} holds a Sleeping Bag in either hand (the sleeper keeps it in hand: both sides can see it). */
	public static boolean holdsBag(LivingEntity e) {
		return SLEEPING_BAG != null && (e.getMainHandItem().is(SLEEPING_BAG) || e.getOffhandItem().is(SLEEPING_BAG));
	}

	/** Client-safe: asleep in a Sleeping Bag (not a bed). */
	public static boolean inBag(LivingEntity e) {
		return e.isSleeping() && holdsBag(e);
	}

	/**
	 * Crawls into the bag where {@code p} stands. Returns null on success, else the refusal's lang key (the vanilla bed
	 * messages where they fit).
	 */
	public static String trySleep(ServerPlayer p) {
		Level level = p.level();
		if (p.isSleeping() || !p.isAlive()) {
			return "message.projecthero.sleeping_bag.cant";
		}
		if (!level.dimensionType().natural() || !level.dimensionType().bedWorks()) {
			return "message.projecthero.sleeping_bag.not_here";
		}
		if (!p.onGround() || p.isInWater() || p.isPassenger()) {
			return "message.projecthero.sleeping_bag.ground";
		}
		if (level.isDay() && !level.isThundering()) {
			return "block.minecraft.bed.no_sleep";
		}
		BlockPos at = p.blockPosition();
		if (!p.isCreative()) {
			List<Monster> near = level.getEntitiesOfClass(Monster.class, new AABB(at).inflate(8.0, 5.0, 8.0), m -> m.isPreventingPlayerRest(p));
			if (!near.isEmpty()) {
				return "block.minecraft.bed.not_safe";
			}
		}
		double x = p.getX(), z = p.getZ(), y = p.getY();
		p.startSleeping(at);
		// lying on the ground in the bag, not on a mattress half a block up
		p.setPos(x, y + 0.12, z);
		if (level instanceof ServerLevel sl) {
			sl.updateSleepingPlayerList();
			sl.playSound(null, x, y, z, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.9f, 0.8f);
			sl.playSound(null, x, y, z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6f, 0.7f);
		}
		return null;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack st = player.getItemInHand(hand);
		if (!level.isClientSide() && player instanceof ServerPlayer sp) {
			String refused = trySleep(sp);
			if (refused != null) {
				sp.displayClientMessage(Component.translatable(refused), true);
				return InteractionResultHolder.fail(st);
			}
		}
		return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("item.projecthero.sleeping_bag.desc").withStyle(ChatFormatting.GRAY));
	}
}
