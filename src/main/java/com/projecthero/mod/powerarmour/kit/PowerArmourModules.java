package com.projecthero.mod.powerarmour.kit;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.network.PowerArmourFxPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: the always-on side of the suit and its modules -- heavy footsteps, the Night Visor, the Rebreather, the
 * Ore Scanner (with the visor on), Reactive Plating (a burst when something hits you in melee).
 */
public final class PowerArmourModules {
	public static final int NIGHT_VISION_TICKS = 300;
	private static final int ORE_SCAN_INTERVAL = 40;

	private PowerArmourModules() {
	}

	public static void tickPassives(ServerPlayer player, PowerArmourState s, long now) {
		if (!s.suit.powered()) {
			return;
		}
		if (s.suit.has(ModuleType.NIGHT_VISOR)) {
			MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
			if (nv == null || nv.getDuration() < 220) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
			}
		}
		if (s.suit.has(ModuleType.REBREATHER)) {
			player.setAirSupply(player.getMaxAirSupply());
			if (player.hasEffect(MobEffects.POISON)) {
				player.removeEffect(MobEffects.POISON);
			}
		}
		// heavy steps
		if (player.onGround() && !player.isShiftKeyDown()) {
			float walked = player.walkDist;
			float last = s.readyAt.getOrDefault("step_dist", 0L) / 100f;
			if (walked - last > 1.6f || walked < last) {
				s.readyAt.put("step_dist", (long) (walked * 100f));
				player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_STEP, SoundSource.PLAYERS,
						0.45f, 0.8f + player.getRandom().nextFloat() * 0.2f);
			}
		}
		if (s.visor && s.suit.has(ModuleType.ORE_SCANNER) && now % ORE_SCAN_INTERVAL == 0) {
			scanOres(player, s);
		}
	}

	/** Sends the ores within {@link PowerArmourConfig#ORE_RANGE} to the wearer's visor (outlined through walls for a while). */
	public static void scanOres(ServerPlayer player, PowerArmourState s) {
		if (!s.suit.has(ModuleType.ORE_SCANNER)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		BlockPos c = player.blockPosition();
		int r = PowerArmourConfig.ORE_RANGE;
		List<Double> data = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r && data.size() < 3 * 200; x++) {
			for (int y = -r; y <= r && data.size() < 3 * 200; y++) {
				for (int z = -r; z <= r; z++) {
					m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
					BlockState st = level.getBlockState(m);
					if (isOre(st)) {
						data.add((double) m.getX());
						data.add((double) m.getY());
						data.add((double) m.getZ());
					}
				}
			}
		}
		ServerPlayNetworking.send(player, new PowerArmourFxPayload(PowerArmourFxPayload.ORES, player.getId(), data));
	}

	static boolean isOre(BlockState st) {
		return st.is(BlockTags.COAL_ORES) || st.is(BlockTags.IRON_ORES) || st.is(BlockTags.GOLD_ORES) || st.is(BlockTags.DIAMOND_ORES)
				|| st.is(BlockTags.EMERALD_ORES) || st.is(BlockTags.LAPIS_ORES) || st.is(BlockTags.REDSTONE_ORES) || st.is(BlockTags.COPPER_ORES)
				|| st.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS) || st.is(net.minecraft.world.level.block.Blocks.NETHER_QUARTZ_ORE);
	}

	/** Something hit the wearer: Reactive Plating answers a nearby attacker with a burst. */
	public static void onHurtBy(ServerPlayer player, PowerArmourState s, LivingEntity attacker, long now) {
		if (!s.suit.has(ModuleType.REACTIVE_PLATING) || !s.suit.powered() || attacker.distanceToSqr(player) > 16.0
				|| !PowerArmourAbilities.ready(s, "reactive", now) || !s.suit.spend(PowerArmourConfig.REACTIVE_COST)) {
			return;
		}
		float damage = PowerArmourConfig.REACTIVE_DAMAGE * PowerArmourAbilities.mul(s.suit);
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position(), 3.0)) {
			if (AbilityHelpers.hurtBurst(player, e, damage) && !PowerArmourAbilities.isBoss(e)) {
				Vec3 out = e.position().subtract(player.position()).normalize();
				AbilityHelpers.push(e, new Vec3(out.x * 0.9, 0.3, out.z * 0.9));
			}
		}
		player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.6, 0.7, 0.6, 0.3);
		player.serverLevel().playSound(null, player.getX(), player.getY() + 1, player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.9f, 0.6f);
		PowerArmourAbilities.cool(s, "reactive", PowerArmourConfig.REACTIVE_COOLDOWN, now);
	}
}
