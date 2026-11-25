package com.projecthero.mod.thoracquire;

import java.util.List;

import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7: <b>Valor</b>, the worthiness meter. Mjolnir only answers a player whose Valor is full ({@link #MAX}); it fills
 * from heroic deeds and is saved per player ({@link ThorAcquireAttachments#VALOR}, persistent, kept through death, synced to
 * the owner for the HUD bar and the Guidebook).
 *
 * <p>Sources ({@link Source}) and amounts, out of 1000 -- tuned so a new player fills it over a couple of hours of real play
 * (a raid, a boss, a hideout and a dungeon, plus villagers along the way), not in minutes:
 * <ul>
 * <li>Hero of the Village gained (winning a vanilla raid, a street-crime rescue, the Supervillain Raid's Champion effect): 150</li>
 * <li>Supervillain Raid won: +150 (on top of its Champion effect) -- Zombie Raid won: 150</li>
 * <li>World / raid boss killed (the c:bosses tag or 150+ max health) with a player's kill credit: 200 to every player within
 * {@link #BOSS_SHARE_RADIUS}</li>
 * <li>Hideout / dungeon mini-boss killed: 75 (same sharing)</li>
 * <li>Hideout, Ancient Temple or Vault Dungeon cleared (first clear only): 100 to every player who was there</li>
 * <li>A villager saved: killing a hostile that was going for a villager in the last 10 s, the villager still alive: 15, at most
 * once every 30 s per player</li>
 * <li>A zombie villager cured: 40</li>
 * </ul>
 */
public final class Valor {
	public static final int MAX = 1000;
	/** Players this close to a dying boss share its Valor (a group fight all counts). */
	public static final double BOSS_SHARE_RADIUS = 48.0;
	/** A hostile counts as "going for" a villager if it hit one within this many ticks, or is targeting one now. */
	public static final int VILLAGER_THREAT_TICKS = 200;
	/** How far the threatened villager may be from the hostile when it dies. */
	public static final double VILLAGER_RADIUS = 16.0;
	/** Saving villagers pays at most once per this many ticks per player. */
	public static final int VILLAGER_COOLDOWN = 600;

	public enum Source {
		HERO_OF_THE_VILLAGE(150),
		SUPERVILLAIN_RAID(150),
		ZOMBIE_RAID(150),
		BOSS(200),
		MINI_BOSS(75),
		CLEARED(100),
		VILLAGER_SAVED(15),
		VILLAGER_CURED(40);

		public final int amount;

		Source(int amount) {
			this.amount = amount;
		}

		public String langKey() {
			return "message.projecthero.valor.source." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private Valor() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Valor::onDeath);
	}

	public static int get(Player player) {
		return Math.max(0, Math.min(MAX, player.getAttachedOrElse(ThorAcquireAttachments.VALOR, 0)));
	}

	public static void set(Player player, int value) {
		player.setAttached(ThorAcquireAttachments.VALOR, Math.max(0, Math.min(MAX, value)));
	}

	public static boolean isFull(Player player) {
		return get(player) >= MAX;
	}

	/** Whole percent, 0..100. */
	public static int percent(Player player) {
		return percentOf(get(player));
	}

	public static int percentOf(int valor) {
		return Math.max(0, Math.min(100, (int) Math.floor(valor * 100.0 / MAX)));
	}

	/**
	 * Adds {@code source}'s Valor and tells the player. A player who is already Thor (worthy) still gains it silently.
	 *
	 * @return the Valor actually added
	 */
	public static int add(ServerPlayer player, Source source) {
		return add(player, source, source.amount);
	}

	public static int add(ServerPlayer player, Source source, int amount) {
		if (amount <= 0 || player.isSpectator()) {
			return 0;
		}
		// user: "valor only fills if you are unpowered" -- a powered player keeps what they have but earns nothing
		if (com.projecthero.mod.hero.HeroTiers.hasAnyPower(player)) {
			poweredHint(player);
			return 0;
		}
		int before = get(player);
		set(player, before + amount);
		int gained = get(player) - before;
		if (gained <= 0 || Worthiness.isWorthy(player)) {
			return gained;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.valor.gain", gained,
				Component.translatable(source.langKey()), percent(player)).withStyle(ChatFormatting.GOLD), true);
		if (isFull(player)) {
			player.sendSystemMessage(Component.translatable("message.projecthero.valor.full").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			player.sendSystemMessage(Component.translatable("message.projecthero.valor.full_hint").withStyle(ChatFormatting.AQUA));
			player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.2f);
		} else {
			player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.4f);
		}
		return gained;
	}

	/** Hint ticks between "your deeds don't count while you have powers" lines (10 minutes). */
	public static final int POWERED_HINT_INTERVAL = 12000;

	/** The subtle, rate-limited "Valor is earned by the unpowered" hint (gray, action bar). */
	private static void poweredHint(ServerPlayer player) {
		long now = player.server.overworld().getGameTime();
		long ready = player.getAttachedOrElse(ThorAcquireAttachments.VALOR_HINT_READY, 0L);
		if (now < ready) {
			return;
		}
		player.setAttached(ThorAcquireAttachments.VALOR_HINT_READY, now + POWERED_HINT_INTERVAL);
		player.displayClientMessage(Component.translatable("message.projecthero.valor.powered").withStyle(ChatFormatting.GRAY), true);
	}

	/** Every non-spectator player within {@code radius} of {@code pos} gets {@code source}. */
	public static void addNear(ServerLevel level, BlockPos pos, double radius, Source source) {
		for (ServerPlayer p : playersNear(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, radius)) {
			add(p, source);
		}
	}

	static List<ServerPlayer> playersNear(ServerLevel level, double x, double y, double z, double radius) {
		return level.getPlayers(p -> !p.isSpectator() && p.isAlive() && p.distanceToSqr(x, y, z) <= radius * radius);
	}

	// ---------------- kills ----------------

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || entity instanceof Player) {
			return;
		}
		ServerPlayer killer = creditedPlayer(entity, source);
		if (killer == null) {
			return;
		}
		if (isMiniBoss(entity)) {
			for (ServerPlayer p : playersNear(level, entity.getX(), entity.getY(), entity.getZ(), BOSS_SHARE_RADIUS)) {
				add(p, Source.MINI_BOSS);
			}
			return;
		}
		if (isBoss(entity)) {
			for (ServerPlayer p : playersNear(level, entity.getX(), entity.getY(), entity.getZ(), BOSS_SHARE_RADIUS)) {
				add(p, Source.BOSS);
			}
			return;
		}
		if (entity instanceof Enemy && entity instanceof Mob mob && threatenedVillager(level, mob) != null) {
			villagerSaved(killer);
		}
	}

	/** The player behind the kill: the attacker, a pet's owner, or whoever last hurt it. */
	static ServerPlayer creditedPlayer(LivingEntity entity, DamageSource source) {
		Entity by = source.getEntity();
		if (by instanceof ServerPlayer sp) {
			return sp;
		}
		if (by instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) {
			return owner;
		}
		return entity.getKillCredit() instanceof ServerPlayer sp ? sp : null;
	}

	/** Hideout guardians and the dungeons' bosses (the Ancient Temple's and the Vault Dungeon's). */
	public static boolean isMiniBoss(LivingEntity e) {
		return e instanceof com.projecthero.mod.hideout.HideoutBoss
				|| e instanceof com.projecthero.mod.temple.entity.AnkhatepBoss
				|| e instanceof com.projecthero.mod.temple.entity.TombSentinelBoss
				|| e instanceof com.projecthero.mod.vaultdungeon.entity.WardenKrossBoss
				|| e instanceof com.projecthero.mod.vaultdungeon.entity.RiotCaptainBoss;
	}

	/** A world / raid boss: the conventional c:bosses tag, or anything with 150+ max health (the mod's raid bosses all are). */
	public static boolean isBoss(LivingEntity e) {
		return !isMiniBoss(e) && (e.getType().is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags.BOSSES)
				|| e.getMaxHealth() >= 150f);
	}

	/** The living villager {@code mob} was going for (its target, or one it hit in the last 10 s), or null. */
	public static AbstractVillager threatenedVillager(ServerLevel level, Mob mob) {
		if (mob.getTarget() instanceof AbstractVillager v && v.isAlive() && v.distanceTo(mob) <= VILLAGER_RADIUS) {
			return v;
		}
		if (mob.getLastHurtMob() instanceof AbstractVillager v && v.isAlive() && v.distanceTo(mob) <= VILLAGER_RADIUS
				&& mob.tickCount - mob.getLastHurtMobTimestamp() <= VILLAGER_THREAT_TICKS) {
			return v;
		}
		return null;
	}

	/** A saved villager: 15 Valor, at most once every 30 s. */
	public static boolean villagerSaved(ServerPlayer player) {
		long now = player.server.overworld().getGameTime();
		long ready = player.getAttachedOrElse(ThorAcquireAttachments.VALOR_SAVE_READY, 0L);
		if (now < ready) {
			return false;
		}
		player.setAttached(ThorAcquireAttachments.VALOR_SAVE_READY, now + VILLAGER_COOLDOWN);
		add(player, Source.VILLAGER_SAVED);
		return true;
	}

	/** Called by the mixin on the vanilla "cured a zombie villager" trigger. */
	public static void villagerCured(ServerPlayer player) {
		add(player, Source.VILLAGER_CURED);
	}

	/** Called by the mixin when a player gains Hero of the Village (a raid won, a crime stopped). */
	public static void heroOfTheVillage(ServerPlayer player) {
		add(player, Source.HERO_OF_THE_VILLAGE);
	}

}
