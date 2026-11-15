package com.projecthero.mod.loot.sites;

import com.projecthero.mod.darkseid.entity.DarkseidEntityTypes;
import com.projecthero.mod.darkseid.entity.ParademonEntity;
import com.projecthero.mod.doctorfate.DoctorFateItems;
import com.projecthero.mod.event.entity.RaidEntityTypes;
import com.projecthero.mod.event.entity.RaidZombie;
import com.projecthero.mod.frostgiant.FrostGiantEntityTypes;
import com.projecthero.mod.symbiote.SymbioteHost;
import com.projecthero.mod.syndicate.SyndicateEntityTypes;
import com.projecthero.mod.syndicate.entity.SyndicateCriminal;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;
import com.projecthero.mod.syndicate.entity.SyndicateSkin;
import com.projecthero.mod.ultron.UltronEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * v0.17.5 Tier 2 Sites: the guard squads. Every guard is an <b>existing</b> mob that never burns in daylight: Syndicate
 * crooks (some in the new agent / soldier / cultist skins, still the Syndicate's player-model bodies), Ultron drones,
 * Parademons, Symbiote-infested husks / spiders / vindicators, Zombie Raid zombies and sword skeletons, Tomb Guardians
 * and Frost Giant raiders. A guard is persistent, tagged {@link #TAG} and held near its post until it has a target.
 *
 * <p>{@link #spawn} works on a {@code WorldGenLevel} (the annex piece) and a {@code ServerLevel} (tests, harness).
 */
public final class SiteGuards {
	public static final String TAG = "projecthero.site_guard";

	public enum Kind {
		// Syndicate looters / smugglers
		THUG, PISTOL, SHOTGUN, SNIPER,
		// government agents and soldiers (Syndicate bodies in the site skins)
		AGENT, AGENT_SHOTGUN, SOLDIER, SOLDIER_RIFLE, SOLDIER_MELEE,
		// cultists (Tomb of Nabu, Temple of Khonshu)
		CULTIST,
		// tech
		DRONE, SENTINEL,
		// Apokolips
		PARADEMON, PARADEMON_RANGED,
		// Symbiote hosts (husks, spiders and vindicators never burn)
		HOST_HUSK, HOST_SPIDER, HOST_VINDICATOR,
		// undead (Zombie Raid mobs: no daylight burning)
		RAID_ZOMBIE, ARMOURED_ZOMBIE, SWORD_SKELETON,
		// tomb mummies
		TOMB_GUARDIAN,
		// Frost Giants
		FROST_RAIDER, FROST_HOUND
	}

	private SiteGuards() {
	}

	public static boolean isGuard(Mob mob) {
		return mob.getTags().contains(TAG);
	}

	private static EntityType<? extends Mob> typeOf(Kind kind) {
		return switch (kind) {
			case THUG, SOLDIER_MELEE, CULTIST -> SyndicateEntityTypes.THUG;
			case PISTOL, SHOTGUN, SNIPER, AGENT, AGENT_SHOTGUN, SOLDIER, SOLDIER_RIFLE -> SyndicateEntityTypes.GUNMAN;
			case DRONE -> UltronEntityTypes.DRONE;
			case SENTINEL -> UltronEntityTypes.SENTINEL_DRONE;
			case PARADEMON, PARADEMON_RANGED -> DarkseidEntityTypes.PARADEMON;
			case HOST_HUSK -> EntityType.HUSK;
			case HOST_SPIDER -> EntityType.SPIDER;
			case HOST_VINDICATOR -> EntityType.VINDICATOR;
			case RAID_ZOMBIE, ARMOURED_ZOMBIE -> RaidEntityTypes.RAID_ZOMBIE;
			case SWORD_SKELETON -> RaidEntityTypes.SWORD_SKELETON;
			case TOMB_GUARDIAN -> DoctorFateItems.TOMB_GUARDIAN;
			case FROST_RAIDER -> FrostGiantEntityTypes.RAIDER;
			case FROST_HOUND -> FrostGiantEntityTypes.HOUND;
		};
	}

	/**
	 * A guard of {@code kind} standing at {@code pos} (feet), facing {@code yaw}, held within {@code leash} blocks of its
	 * post. Returns the mob (already added to the level), or null.
	 */
	public static Mob spawn(ServerLevelAccessor level, Kind kind, BlockPos pos, float yaw, int leash) {
		EntityType<? extends Mob> type = typeOf(kind);
		if (type == null) {
			return null;
		}
		Mob mob = type.create(level.getLevel());
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
		mob.setYHeadRot(yaw);
		mob.setYBodyRot(yaw);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
		switch (kind) {
			case PISTOL -> ((SyndicateGunman) mob).setKind(SyndicateGunman.Kind.PISTOL);
			case SHOTGUN -> ((SyndicateGunman) mob).setKind(SyndicateGunman.Kind.SHOTGUN);
			case SNIPER -> ((SyndicateGunman) mob).setKind(SyndicateGunman.Kind.SNIPER);
			case AGENT -> dress((SyndicateGunman) mob, SyndicateGunman.Kind.PISTOL, SyndicateSkin.SITE_AGENT, "site_agent");
			case AGENT_SHOTGUN -> dress((SyndicateGunman) mob, SyndicateGunman.Kind.SHOTGUN, SyndicateSkin.SITE_AGENT, "site_agent");
			case SOLDIER -> dress((SyndicateGunman) mob, SyndicateGunman.Kind.SHOTGUN, SyndicateSkin.SITE_SOLDIER, "site_soldier");
			case SOLDIER_RIFLE -> dress((SyndicateGunman) mob, SyndicateGunman.Kind.SNIPER, SyndicateSkin.SITE_SOLDIER, "site_soldier");
			case SOLDIER_MELEE -> melee((SyndicateCriminal) mob, SyndicateSkin.SITE_SOLDIER, "site_soldier", Items.IRON_SWORD);
			case CULTIST -> melee((SyndicateCriminal) mob, SyndicateSkin.SITE_CULTIST, "site_cultist", Items.GOLDEN_SWORD);
			case PARADEMON -> ((ParademonEntity) mob).setup(ParademonEntity.Variant.STANDARD, null);
			case PARADEMON_RANGED -> ((ParademonEntity) mob).setup(ParademonEntity.Variant.RANGED, null);
			case HOST_HUSK, HOST_SPIDER, HOST_VINDICATOR -> SymbioteHost.mark(mob);
			case RAID_ZOMBIE -> ((RaidZombie) mob).setVariant(RaidZombie.Variant.BASIC);
			case ARMOURED_ZOMBIE -> ((RaidZombie) mob).setVariant(RaidZombie.Variant.ARMOURED);
			default -> {
			}
		}
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		if (leash > 0) {
			mob.restrictTo(pos, leash);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static void dress(SyndicateGunman gunman, SyndicateGunman.Kind gun, SyndicateSkin skin, String name) {
		gunman.setKind(gun);
		gunman.setSkin(skin);
		gunman.setCustomName(Component.translatable("entity.projecthero." + name).withStyle(ChatFormatting.WHITE));
	}

	private static void melee(SyndicateCriminal crook, SyndicateSkin skin, String name, net.minecraft.world.item.Item weapon) {
		crook.setSkin(skin);
		crook.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapon));
		crook.setDropChance(EquipmentSlot.MAINHAND, 0f);
		crook.setCustomName(Component.translatable("entity.projecthero." + name).withStyle(ChatFormatting.WHITE));
	}
}
