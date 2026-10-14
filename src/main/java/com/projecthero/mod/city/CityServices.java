package com.projecthero.mod.city;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2 City part B: vanilla professions that work in a City building get a City job on top of their trades (which
 * stay untouched). Found from the job-site memory plus the City piece that holds it ({@link CityBuilding}).
 *
 * <ul>
 * <li><b>Doctor</b> = a cleric whose job site is in the hospital. Everyone (players, villagers) inside the hospital's
 * pieces gets Regeneration II, refreshed every 2 s. Once a minute the doctor throws a Healing splash at a hurt player
 * or villager within 8 blocks. Sneak + use a doctor holding 2 emeralds: a full heal that also clears poison, wither and
 * hunger.</li>
 * <li><b>Firefighter</b> = a leatherworker whose job site is in the fire station. Fires in the City (recorded by the
 * block-change hook) are answered: it walks over and puts out the fire within 3 blocks, one every 10 ticks; one it
 * can't reach in 30 s it douses from where it is.</li>
 * <li><b>Newspaper seller</b> = a librarian whose job site is in the Daily Bulletin: always sells the Daily Bulletin
 * (1 emerald), as an extra trade.</li>
 * </ul>
 */
public final class CityServices {
	private static final Map<UUID, Long> LAST_SPLASH = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> FIRE_SINCE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_PUT_OUT = new ConcurrentHashMap<>();

	private CityServices() {
	}

	static Optional<BlockPos> jobSite(Villager v, ServerLevel level) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension()).map(GlobalPos::pos);
	}

	/** True if {@code v} has {@code profession} and its job site is in a {@code building} piece. */
	public static boolean worksIn(Villager v, ServerLevel level, VillagerProfession profession, CityBuilding building) {
		if (v.getVillagerData().getProfession() != profession) {
			return false;
		}
		Optional<BlockPos> site = jobSite(v, level);
		return site.isPresent() && building.contains(level, site.get());
	}

	public static boolean isDoctor(Villager v, ServerLevel level) {
		return worksIn(v, level, VillagerProfession.CLERIC, CityBuilding.HOSPITAL);
	}

	public static boolean isFirefighter(Villager v, ServerLevel level) {
		return worksIn(v, level, VillagerProfession.LEATHERWORKER, CityBuilding.FIRE_STATION);
	}

	// ---------------------------------------------------------------- doctor

	static void tickDoctor(Villager doc, ServerLevel level) {
		if ((doc.tickCount + doc.getId()) % 40 != 0 || !isDoctor(doc, level)) {
			return;
		}
		BlockPos site = jobSite(doc, level).orElseThrow();
		CityArea city = CityLocator.cityAt(level, site);
		CityConfig.Doctor cfg = CityConfig.doctor();
		if (city != null) {
			for (BoundingBox ward : CityBuilding.HOSPITAL.boxesNear(city, site, 48)) {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.of(ward),
						e -> e.isAlive() && (e instanceof Player || e instanceof Villager))) {
					MobEffectInstance cur = e.getEffect(MobEffects.REGENERATION);
					if (cur == null || cur.getDuration() < 50 || cur.getAmplifier() < cfg.wardRegenAmplifier) {
						e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, cfg.wardRegenAmplifier, true, true));
					}
				}
			}
		}
		if (doc.isSleeping()) {
			return;
		}
		long now = level.getGameTime();
		if (now - LAST_SPLASH.getOrDefault(doc.getUUID(), Long.MIN_VALUE / 2) < cfg.splashCooldownTicks) {
			return;
		}
		double r = cfg.splashRange;
		List<LivingEntity> hurt = level.getEntitiesOfClass(LivingEntity.class, doc.getBoundingBox().inflate(r),
				e -> e != doc && e.isAlive() && (e instanceof Villager || (e instanceof Player p && !p.isCreative() && !p.isSpectator()))
						&& e.getHealth() < e.getMaxHealth() - 2.0f && doc.hasLineOfSight(e));
		if (hurt.isEmpty()) {
			return;
		}
		LivingEntity worst = hurt.get(0);
		for (LivingEntity e : hurt) {
			if (e.getHealth() / e.getMaxHealth() < worst.getHealth() / worst.getMaxHealth()) {
				worst = e;
			}
		}
		throwHealing(level, doc, worst);
		LAST_SPLASH.put(doc.getUUID(), now);
	}

	static void throwHealing(ServerLevel level, Villager doc, LivingEntity at) {
		ThrownPotion potion = new ThrownPotion(level, doc);
		potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
		Vec3 d = at.position().add(0, at.getBbHeight() * 0.4, 0).subtract(doc.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		potion.shoot(d.x, d.y + flat * 0.2, d.z, 0.75f, 2.0f);
		doc.swing(InteractionHand.MAIN_HAND, true);
		level.playSound(null, doc.getX(), doc.getY(), doc.getZ(), SoundEvents.WITCH_THROW, SoundSource.NEUTRAL, 1.0f, 1.2f);
		level.addFreshEntity(potion);
	}

	/** Sneak + use a doctor holding emeralds: a paid full heal. */
	static InteractionResult onUseVillager(Player player, Villager v, InteractionHand hand) {
		if (!(player instanceof ServerPlayer sp) || !player.isShiftKeyDown() || !(v.level() instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(Items.EMERALD) || !isDoctor(v, level)) {
			return InteractionResult.PASS;
		}
		int cost = CityConfig.doctor().healCostEmeralds;
		if (held.getCount() < cost && !player.getAbilities().instabuild) {
			sp.displayClientMessage(Component.translatable("entity.projecthero.doctor.need", cost).withStyle(ChatFormatting.RED), true);
			return InteractionResult.SUCCESS;
		}
		healFully(level, sp);
		if (!player.getAbilities().instabuild) {
			held.shrink(cost);
		}
		v.playSound(SoundEvents.VILLAGER_YES, 1.0f, 1.0f);
		sp.displayClientMessage(Component.translatable("entity.projecthero.doctor.healed", cost).withStyle(ChatFormatting.GREEN), true);
		return InteractionResult.SUCCESS;
	}

	public static void healFully(ServerLevel level, ServerPlayer p) {
		p.setHealth(p.getMaxHealth());
		p.removeEffect(MobEffects.POISON);
		p.removeEffect(MobEffects.WITHER);
		p.removeEffect(MobEffects.HUNGER);
		p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
		level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.4, p.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.6f);
	}

	// ---------------------------------------------------------------- firefighter

	static void tickFirefighter(Villager ff, ServerLevel level) {
		if ((ff.tickCount + ff.getId()) % 10 != 0 || ff.isSleeping() || ff.isBaby()) {
			return;
		}
		LongOpenHashSet fires = CityDamageTracker.fires(level);
		if (fires.isEmpty() || !isFirefighter(ff, level)) {
			FIRE_SINCE.remove(ff.getUUID());
			return;
		}
		CityConfig.Firefighter cfg = CityConfig.firefighter();
		BlockPos station = jobSite(ff, level).orElseThrow();
		BlockPos nearest = null;
		double bestD = Double.MAX_VALUE;
		for (LongIterator it = fires.iterator(); it.hasNext();) {
			long l = it.nextLong();
			BlockPos p = BlockPos.of(l);
			if (!level.isLoaded(p)) {
				continue;
			}
			if (!(level.getBlockState(p).getBlock() instanceof BaseFireBlock)) {
				it.remove();
				continue;
			}
			if (p.distSqr(station) > (double) cfg.responseRange * cfg.responseRange) {
				continue;
			}
			double d = p.distToCenterSqr(ff.position());
			if (d < bestD) {
				bestD = d;
				nearest = p;
			}
		}
		if (nearest == null) {
			FIRE_SINCE.remove(ff.getUUID());
			return;
		}
		long now = level.getGameTime();
		long since = FIRE_SINCE.computeIfAbsent(ff.getUUID(), k -> now);
		double reach = cfg.extinguishRadius + 1.5;
		boolean close = bestD <= reach * reach;
		boolean stuck = now - since > 600 && bestD <= 16.0 * 16.0;
		if (!close && !stuck) {
			ff.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(nearest, 0.8f, 2));
			return;
		}
		if (now - LAST_PUT_OUT.getOrDefault(ff.getUUID(), Long.MIN_VALUE / 2) < cfg.ticksPerFire) {
			return;
		}
		// put out one fire around the target (the nearest burning block within the radius of it)
		BlockPos out = null;
		int r = cfg.extinguishRadius;
		for (BlockPos p : BlockPos.betweenClosed(nearest.offset(-r, -r, -r), nearest.offset(r, r, r))) {
			if (level.getBlockState(p).getBlock() instanceof BaseFireBlock) {
				out = p.immutable();
				break;
			}
		}
		if (out == null) {
			return;
		}
		douse(level, ff, out);
		fires.remove(out.asLong());
		LAST_PUT_OUT.put(ff.getUUID(), now);
		FIRE_SINCE.put(ff.getUUID(), now);
	}

	static void douse(ServerLevel level, Villager ff, BlockPos fire) {
		level.setBlock(fire, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, fire, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.0f);
		level.sendParticles(ParticleTypes.SPLASH, fire.getX() + 0.5, fire.getY() + 0.4, fire.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(ParticleTypes.CLOUD, fire.getX() + 0.5, fire.getY() + 0.6, fire.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.02);
		ff.swing(InteractionHand.MAIN_HAND, true);
		double dx = fire.getX() + 0.5 - ff.getX();
		double dz = fire.getZ() + 0.5 - ff.getZ();
		ff.setYHeadRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
	}

	// ---------------------------------------------------------------- the newspaper seller

	static void tickLibrarian(Villager lib, ServerLevel level) {
		if ((lib.tickCount + lib.getId()) % 200 != 0 || !worksIn(lib, level, VillagerProfession.LIBRARIAN, CityBuilding.BULLETIN)) {
			return;
		}
		ensureBulletinTrade(lib);
	}

	/** Adds the Daily Bulletin trade to {@code lib} if it isn't there. */
	public static void ensureBulletinTrade(Villager lib) {
		for (MerchantOffer o : lib.getOffers()) {
			if (o.getResult().is(CityBlocks.DAILY_BULLETIN)) {
				return;
			}
		}
		lib.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, Math.max(1, CityConfig.bulletin().priceEmeralds)),
				new ItemStack(CityBlocks.DAILY_BULLETIN), 32, 1, 0.0f));
	}

	public static void clearSessionState() {
		LAST_SPLASH.clear();
		FIRE_SINCE.clear();
		LAST_PUT_OUT.clear();
	}
}
