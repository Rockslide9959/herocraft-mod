package com.projecthero.mod.combat;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Wires the three sonic-disruption sources the spec names into {@link SonicVulnerability}: bells,
 * goat horns, and the Warden's sonic boom. Deliberately event-driven, never a scan -- each source
 * already tells us exactly where and when a loud sound happened.
 */
public final class SonicTriggers {
	/** How far a rung bell or a played goat horn disrupts. */
	private static final double RADIUS = 16.0;
	/** ~5 s of disruption from an environmental source. */
	private static final int DISRUPT_TICKS = 100;

	private SonicTriggers() {
	}

	public static void initialize() {
		UseBlockCallback.EVENT.register(SonicTriggers::onUseBlock);
		UseItemCallback.EVENT.register(SonicTriggers::onUseItem);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SonicTriggers::onAllowDamage);
	}

	private static InteractionResult onUseBlock(Player player, net.minecraft.world.level.Level level,
			net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		if (level.getBlockState(hit.getBlockPos()).is(Blocks.BELL)) {
			disruptAround((ServerLevel) level, hit.getBlockPos().getCenter(), sp);
		}
		return InteractionResult.PASS;
	}

	private static InteractionResultHolder<net.minecraft.world.item.ItemStack> onUseItem(
			Player player, net.minecraft.world.level.Level level, net.minecraft.world.InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.pass(player.getItemInHand(hand));
		}
		if (player.getItemInHand(hand).is(Items.GOAT_HORN)) {
			disruptAround((ServerLevel) level, player.position(), sp);
		}
		return InteractionResultHolder.pass(player.getItemInHand(hand));
	}

	/** The Warden's sonic boom is already precisely targeted -- disrupt exactly its victim. */
	private static boolean onAllowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (source.is(DamageTypes.SONIC_BOOM) && entity.level() instanceof ServerLevel level) {
			SonicVulnerability.expose(entity, level.getGameTime(), DISRUPT_TICKS);
		}
		return true;
	}

	private static void disruptAround(ServerLevel level, net.minecraft.world.phys.Vec3 center, ServerPlayer source) {
		long now = level.getGameTime();
		AABB box = new AABB(center.x - RADIUS, center.y - RADIUS, center.z - RADIUS,
				center.x + RADIUS, center.y + RADIUS, center.z + RADIUS);
		for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			SonicVulnerability.expose(le, now, DISRUPT_TICKS);
		}
		AbilityHelpers.burst(level, center, ParticleTypes.NOTE, 6, 0.6);
	}
}
