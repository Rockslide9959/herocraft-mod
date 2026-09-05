package com.herocraft.mod.event.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.herocraft.mod.event.EventConfig;
import com.herocraft.mod.event.boss.BossPowerController;
import com.herocraft.mod.event.boss.BossPowers;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The Powered Zombie Boss (waves 4, 8 and 12): an oversized, permanently glowing zombie carrying one
 * -- or, for the final boss, sometimes two -- of the mod's Experimental Powers, driven by a
 * {@link BossPowerController}.
 *
 * <h2>Division of responsibility</h2>
 * Everything that is the same regardless of which power the boss rolled lives here: health scaling,
 * the boss bar, the aura, target selection and switching, reacting to airborne and ranged players,
 * and holding the power's preferred distance. The power itself only decides <em>which ability, when</em>.
 * That is what makes adding a power to the boss roster a single small class.
 *
 * <h2>Difficulty comes from decisions, not from numbers</h2>
 * There is no accuracy cheat anywhere: every ability a controller fires is dodgeable, telegraphed or
 * both, per the design's explicit "do not create difficulty using unavoidable attacks or perfect
 * accuracy". The boss's edge is that it re-picks its target on a timer with a threat score, closes on
 * archers, follows fliers, and spends area abilities when players bunch up.
 *
 * <h2>Cost</h2>
 * Ability logic runs on a {@value #ABILITY_INTERVAL}-tick cadence and target re-evaluation on a
 * {@value #RETARGET_INTERVAL}-tick one; both use bounded, radius-limited queries. The aura is a small
 * fixed particle count every {@value #AURA_INTERVAL} ticks. Boss-bar membership is recomputed from the
 * level's existing player list, never from an entity scan.
 */
public class EmpoweredZombie extends RaidUndead {
	private static final EntityDataAccessor<String> DATA_POWER =
			SynchedEntityData.defineId(EmpoweredZombie.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<String> DATA_SECOND_POWER =
			SynchedEntityData.defineId(EmpoweredZombie.class, EntityDataSerializers.STRING);
	private static final EntityDataAccessor<Boolean> DATA_FINAL =
			SynchedEntityData.defineId(EmpoweredZombie.class, EntityDataSerializers.BOOLEAN);
	/**
	 * Supervillain Village Raid appearance ({@code chimera} / {@code arsenal} / {@code omega_mage}),
	 * or {@code ""} for an ordinary Zombie Raid Powered Zombie Boss. Cosmetic only -- it changes the
	 * model, the hitbox scale, the boss-bar name and the trophy, never the power.
	 */
	private static final EntityDataAccessor<String> DATA_VARIANT =
			SynchedEntityData.defineId(EmpoweredZombie.class, EntityDataSerializers.STRING);

	public static final float SCALE = 1.45f;
	private static final int ABILITY_INTERVAL = 10;
	private static final int RETARGET_INTERVAL = 70;
	private static final int AURA_INTERVAL = 5;
	/** Radius within which players see the boss bar. */
	private static final double BOSS_BAR_RADIUS = 80.0;
	/** Vanilla's hard clamp on the MAX_HEALTH attribute. */
	private static final double VANILLA_MAX_HEALTH = 1024.0;

	private final ServerBossEvent bossBar = new ServerBossEvent(
			Component.literal("Empowered Zombie"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

	private BossPowerController primary;
	private BossPowerController secondary;
	/** Damage each player has dealt since the last retarget; cleared on every retarget, so bounded. */
	private final Map<UUID, Float> threat = new HashMap<>();
	private int participantCount = 1;

	public EmpoweredZombie(EntityType<? extends EmpoweredZombie> type, Level level) {
		super(type, level);
		this.xpReward = 100;
		this.setPersistenceRequired();
		this.bossBar.setDarkenScreen(false);
		this.bossBar.setCreateWorldFog(false);
	}

	// ---------------- setup ----------------

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.MOVEMENT_SPEED, 0.27)
				.add(Attributes.FOLLOW_RANGE, 64.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.ARMOR_TOUGHNESS, 4.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(Attributes.ATTACK_KNOCKBACK, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_POWER, "");
		builder.define(DATA_SECOND_POWER, "");
		builder.define(DATA_FINAL, false);
		builder.define(DATA_VARIANT, "");
	}

	/**
	 * Configure a freshly created boss. Called by the raid before the entity is added to the world.
	 *
	 * @param powerKey    the Experimental Power it carries
	 * @param secondKey   an optional second power (final boss only), or {@code null}
	 * @param finalBoss   true for the wave-12 encounter
	 * @param players     participants to scale health against
	 */
	public void configure(String powerKey, String secondKey, boolean finalBoss, int players) {
		this.entityData.set(DATA_POWER, powerKey == null ? "" : powerKey);
		this.entityData.set(DATA_SECOND_POWER, secondKey == null ? "" : secondKey);
		this.entityData.set(DATA_FINAL, finalBoss);
		this.participantCount = Math.max(1, players);
		rebuildControllers();
		applyScaledHealth();
		AttributeInstance atk = getAttribute(Attributes.ATTACK_DAMAGE);
		if (atk != null) {
			atk.setBaseValue(finalBoss
					? EventConfig.raid().finalBossMeleeDamage
					: EventConfig.raid().bossMeleeDamage);
		}
		if (finalBoss) {
			bump(Attributes.MOVEMENT_SPEED, 0.03);
		}
		refreshBossBarName();
	}

	/**
	 * Boss health scaling from the design's table: 400 / 600 / 800 / 1000, then +150 each.
	 *
	 * <h4>The 1024 ceiling</h4>
	 * Vanilla clamps {@link Attributes#MAX_HEALTH} at {@value #VANILLA_MAX_HEALTH}, so any group past
	 * four players (or a final boss, with its multiplier) asks for more health than the attribute can
	 * hold -- and it is silently truncated rather than refused. Rather than let the table quietly stop
	 * meaning anything above that point, the surplus is converted into armour instead, so a large group
	 * still faces a measurably tougher boss. Armour rather than more effective health is deliberate: it
	 * scales down the raw numbers a superhero build puts out without turning the fight into a slog the
	 * way a second health bar would.
	 */
	private void applyScaledHealth() {
		EventConfig.ZombieRaid cfg = EventConfig.raid();
		double health = cfg.bossBaseHealth
				+ cfg.bossHealthPerPlayerTo4 * Math.max(0, Math.min(participantCount, 4) - 1)
				+ cfg.bossHealthPerPlayerBeyond4 * Math.max(0, participantCount - 4);
		if (isFinalBoss()) {
			health *= cfg.finalBossHealthMultiplier;
		}

		double overflow = Math.max(0.0, health - VANILLA_MAX_HEALTH);
		AttributeInstance attr = getAttribute(Attributes.MAX_HEALTH);
		if (attr != null) {
			attr.setBaseValue(Math.min(health, VANILLA_MAX_HEALTH));
		}
		if (overflow > 0.0) {
			bump(Attributes.ARMOR, Math.min(12.0, overflow / 100.0));
			bump(Attributes.ARMOR_TOUGHNESS, Math.min(6.0, overflow / 250.0));
		}
		setHealth(getMaxHealth());
	}

	private void bump(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double delta) {
		AttributeInstance attr = getAttribute(attribute);
		if (attr != null) {
			attr.setBaseValue(attr.getBaseValue() + delta);
		}
	}

	/**
	 * Configure this boss as the Supervillain Village Raid's wave-6 boss: a cosmetic {@code variant}
	 * on top of the ordinary powered-boss machinery. It runs with final-boss AI behaviour, its own
	 * health formula (450 + 150 per extra player, capped), the configured knockback resistance, and
	 * its boss bar reads "THE CHIMERA / GEOKINESIS".
	 *
	 * <p>v0.9.10 ("make the boss more aggressive and powerful like the zombie raid"): it now also gets
	 * a real configured melee damage ({@link EventConfig.SupervillainRaid#bossMeleeDamage}, was the
	 * bare entity default of 10) and the same small movement-speed bump {@link #configure} gives the
	 * Zombie Raid's final boss. The rest of the aggression -- telegraphed accurate ranged casts, the
	 * close-range shove, the SuperSpeed blitz / Flight aerial kit -- is shared {@code BossPowerController}
	 * / {@link EmpoweredZombie} behaviour and was inherited automatically.
	 */
	public void configureAsSupervillain(SupervillainVariant variant, String powerKey, int players) {
		this.entityData.set(DATA_VARIANT, variant == null ? "" : variant.id());
		this.entityData.set(DATA_POWER, powerKey == null ? "" : powerKey);
		this.entityData.set(DATA_SECOND_POWER, "");
		this.entityData.set(DATA_FINAL, true);
		this.participantCount = Math.max(1, players);
		rebuildControllers();

		EventConfig.SupervillainRaid cfg = EventConfig.supervillain();
		int scaled = Math.min(Math.max(1, players), Math.max(1, cfg.bossHealthPlayerCap));
		double health = cfg.bossBaseHealth + cfg.bossHealthPerAdditionalPlayer * (scaled - 1);
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) {
			hp.setBaseValue(Math.min(health, VANILLA_MAX_HEALTH));
		}
		AttributeInstance kb = getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (kb != null) {
			kb.setBaseValue(Math.max(0.0, Math.min(1.0, cfg.bossKnockbackResistance)));
		}
		AttributeInstance atk = getAttribute(Attributes.ATTACK_DAMAGE);
		if (atk != null) {
			atk.setBaseValue(cfg.bossMeleeDamage);
		}
		// The Zombie Raid's final boss gets this nudge; the Supervillain runs on final-boss AI, so it
		// should feel the same forward pressure.
		bump(Attributes.MOVEMENT_SPEED, 0.05);
		setHealth(getMaxHealth());
		refreshBossBarName();
		refreshDimensions();
	}

	/** The Supervillain appearance, or {@code null} for an ordinary Zombie Raid boss. */
	public SupervillainVariant variant() {
		return SupervillainVariant.byId(this.entityData.get(DATA_VARIANT));
	}

	private void rebuildControllers() {
		primary = BossPowers.create(powerKey(), this);
		String second = secondPowerKey();
		secondary = second.isEmpty() ? null : BossPowers.create(second, this);
	}

	// ---------------- identity ----------------

	public String powerKey() {
		return this.entityData.get(DATA_POWER);
	}

	public String secondPowerKey() {
		return this.entityData.get(DATA_SECOND_POWER);
	}

	public boolean isFinalBoss() {
		return this.entityData.get(DATA_FINAL);
	}

	public int participantCount() {
		return participantCount;
	}

	/**
	 * Multiplier applied to every boss-power ability's damage (spec section 33). 1.0 for an ordinary
	 * Zombie Raid boss; the Supervillain Raid scales its boss down so a player power that would one-shot
	 * a player when used by a boss stays "dangerous but fair". Only ever touches the boss.
	 */
	public float abilityDamageScale() {
		return variant() == null ? 1.0f : (float) EventConfig.supervillain().bossAbilityDamageScale;
	}

	/**
	 * Multiplier every controller cooldown is scaled by. Larger groups get slightly more frequent
	 * abilities (capped), and the final boss acts faster still.
	 */
	public double cooldownScale() {
		EventConfig.ZombieRaid cfg = EventConfig.raid();
		double reduction = Math.min(cfg.bossCooldownReductionCap,
				cfg.bossCooldownReductionPerPlayer * Math.max(0, participantCount - 1));
		double scale = cfg.bossCooldownMultiplier * (1.0 - reduction);
		if (isFinalBoss()) {
			scale *= cfg.finalBossCooldownMultiplier;
		}
		return Math.max(0.2, scale);
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		// Hitbox stays a reasonable combat size even when the visual model is much larger (spec §51):
		// the Supervillain models render at up to ~2.15x but collide at ~1.5x so they do not wedge in
		// village buildings.
		SupervillainVariant v = variant();
		float hitbox = v == null ? SCALE : switch (v) {
			case CHIMERA -> 1.55f;
			case ARSENAL -> 1.45f;
			case OMEGA_MAGE -> 1.30f;
		};
		return super.getDefaultDimensions(pose).scale(hitbox);
	}

	@Override
	public boolean isBaby() {
		return false;
	}

	/** Boss-scale: never yanked around by crowd control the way an ordinary raid mob is. */
	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		// Levitation and Slow Falling on a boss make encounters read as broken rather than hard.
		if (effect.getEffect() == net.minecraft.world.effect.MobEffects.LEVITATION) {
			return false;
		}
		return super.canBeAffected(effect);
	}

	// ---------------- ticking ----------------

	@Override
	public void aiStep() {
		super.aiStep();
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (primary == null && !powerKey().isEmpty()) {
			rebuildControllers();
		}
		if (!hasGlowingTag()) {
			setGlowingTag(true);
		}

		updateBossBar(server);

		if (tickCount % AURA_INTERVAL == 0) {
			emitAura(server);
		}
		if (tickCount % RETARGET_INTERVAL == 0) {
			retarget(server);
		}
		if (tickCount % ABILITY_INTERVAL == 0) {
			runAbilities(server);
		}
	}

	private void emitAura(ServerLevel server) {
		if (primary != null) {
			server.sendParticles(primary.auraParticle(), getX(), getY() + getBbHeight() * 0.6, getZ(),
					2, 0.5, 0.5, 0.5, 0.01);
		}
		if (secondary != null) {
			server.sendParticles(secondary.auraParticle(), getX(), getY() + getBbHeight() * 0.35, getZ(),
					1, 0.5, 0.4, 0.5, 0.01);
		}
	}

	/** Ticks left on the power-independent "get out of my face" shove. */
	private int shoveCooldown;

	private void runAbilities(ServerLevel server) {
		LivingEntity target = getTarget();
		if (primary != null) {
			primary.tickCooldowns(ABILITY_INTERVAL);
		}
		if (secondary != null) {
			secondary.tickCooldowns(ABILITY_INTERVAL);
		}
		if (shoveCooldown > 0) {
			shoveCooldown -= ABILITY_INTERVAL;
		}
		if (target == null || !target.isAlive()) {
			// No one to fight: make sure a Flight boss that lost its target mid-air is not left hovering.
			if (isNoGravity() && !onGround()) {
				setNoGravity(false);
			}
			return;
		}

		// Power-independent close-range answer: whatever the boss rolled, a player who crowds it gets
		// physically flung back. Every boss can do this, so meleeing one is never a completely free ride.
		if (shoveCooldown <= 0 && closeRangeRepel(server)) {
			shoveCooldown = 5 * 20;
		}

		// Let a controller finish a telegraphed cast it committed to last cycle before it picks a new move.
		boolean primaryBusy = primary != null && primary.resolvePendingCast(server, target);
		if (primary != null && !primaryBusy) {
			primary.tick(server, target);
		}
		// The second power runs at half rate so a dual-power boss is more varied, not twice as deadly.
		if (secondary != null && (tickCount / ABILITY_INTERVAL) % 2 == 0) {
			if (!secondary.resolvePendingCast(server, target)) {
				secondary.tick(server, target);
			}
		}
		holdPreferredRange(target);
	}

	/**
	 * Radial shove of anyone within about three blocks: light damage, a firm horizontal knockback and a
	 * little lift. This is the "if players get close he can knock them away" behaviour from the design,
	 * and it is deliberately on the boss rather than in any one power so it is always available.
	 */
	private boolean closeRangeRepel(ServerLevel server) {
		List<Player> near = server.getEntitiesOfClass(Player.class, getBoundingBox().inflate(3.2),
				p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
		if (near.isEmpty()) {
			return false;
		}
		server.playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 0.7f, 1.7f);
		server.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
				getX(), getY() + 1.0, getZ(), 14, 1.4, 0.6, 1.4, 0.05);
		for (Player p : near) {
			p.hurt(damageSources().mobAttack(this), 4.0f);
			net.minecraft.world.phys.Vec3 away = p.position().subtract(position());
			if (away.lengthSqr() < 1.0e-4) {
				away = new net.minecraft.world.phys.Vec3(0.1, 0.0, 0.1);
			}
			away = new net.minecraft.world.phys.Vec3(away.x, 0.0, away.z).normalize().scale(1.9);
			p.setDeltaMovement(p.getDeltaMovement().add(away.x, 0.5, away.z));
			p.hurtMarked = true;
		}
		return true;
	}

	/**
	 * Approach or back off toward the active power's preferred distance. Deliberately gentle: it only
	 * nudges the navigation target, so ordinary pathfinding still handles terrain and the boss cannot
	 * be walked into a wall by its own positioning logic.
	 */
	private void holdPreferredRange(LivingEntity target) {
		BossPowerController active = primary != null ? primary : secondary;
		if (active == null) {
			return;
		}
		double want = active.preferredRange();
		double have = distanceTo(target);
		if (have > want + 3.0) {
			getNavigation().moveTo(target, 1.15);
		} else if (have < want - 3.0 && want > 6.0) {
			// Too close for a ranged power: step back rather than sprinting away, so melee players
			// can still fight it and it never kites forever.
			net.minecraft.world.phys.Vec3 away = position().subtract(target.position());
			if (away.lengthSqr() > 1.0e-4) {
				away = away.normalize().scale(6.0);
				getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.0);
			}
		}
	}

	/**
	 * Pick who to fight. Scores every nearby player and takes the best, with a deliberate bias away
	 * from whoever is already the target so the boss rotates rather than tunnel-visioning one person
	 * for the whole fight (spec section 24).
	 */
	private void retarget(ServerLevel server) {
		List<Player> candidates = server.getEntitiesOfClass(Player.class,
				getBoundingBox().inflate(getAttributeValue(Attributes.FOLLOW_RANGE)),
				p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
		if (candidates.isEmpty()) {
			threat.clear();
			return;
		}
		LivingEntity current = getTarget();
		Player best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (Player player : candidates) {
			double distance = distanceTo(player);
			double score = 60.0 - distance;
			// Whoever has been hurting it most since the last switch is the most dangerous.
			score += threat.getOrDefault(player.getUUID(), 0.0f) * 0.5;
			// An airborne player (Iron Man, Thor, experimental flight) is otherwise safe from a
			// ground boss, so weight them up -- the powers that can answer flight will follow.
			if (!player.onGround() && player.getY() > getY() + 2.5) {
				score += 25.0;
			}
			// So is one standing well back and shooting.
			if (distance > 14.0) {
				score += 12.0;
			}
			if (player == current) {
				score -= 18.0; // rotate away from the current victim unless they are clearly the threat
			}
			if (score > bestScore) {
				bestScore = score;
				best = player;
			}
		}
		threat.clear();
		if (best != null && best != current) {
			setTarget(best);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		boolean hurt = super.hurt(source, amount);
		if (hurt && level() instanceof ServerLevel server) {
			if (source.getEntity() instanceof Player attacker) {
				threat.merge(attacker.getUUID(), amount, Float::sum);
			}
			if (primary != null) {
				primary.onDamaged(server, source, amount);
			}
			if (secondary != null) {
				secondary.onDamaged(server, source, amount);
			}
		}
		return hurt;
	}

	// ---------------- boss bar ----------------

	private void refreshBossBarName() {
		SupervillainVariant variant = variant();
		if (variant != null) {
			Component villain = Component.empty().append(variant.displayName())
					.append(Component.literal("  —  ").withStyle(ChatFormatting.GRAY))
					.append(BossPowers.displayName(powerKey()).copy().withStyle(ChatFormatting.GOLD));
			bossBar.setName(villain.copy().withStyle(ChatFormatting.DARK_RED));
			if (primary != null) {
				bossBar.setColor(primary.barColor());
			}
			return;
		}
		Component name = Component.translatable("entity.herocraft.empowered_zombie")
				.withStyle(isFinalBoss() ? ChatFormatting.DARK_RED : ChatFormatting.LIGHT_PURPLE);
		Component label = Component.empty().append(name)
				.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
				.append(BossPowers.displayName(powerKey()).copy().withStyle(ChatFormatting.GOLD));
		if (!secondPowerKey().isEmpty()) {
			label = label.copy()
					.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY))
					.append(BossPowers.displayName(secondPowerKey()).copy().withStyle(ChatFormatting.GOLD));
		}
		bossBar.setName(label);
		if (primary != null) {
			bossBar.setColor(primary.barColor());
		}
	}

	private void updateBossBar(ServerLevel server) {
		syncBarIdentity();
		if (bossBar.getName().getString().isEmpty() || tickCount % 40 == 0) {
			refreshBossBarName();
		}
		bossBar.setProgress(Math.max(0.0f, getHealth() / Math.max(1.0f, getMaxHealth())));
		if (tickCount % 20 != 0) {
			return;
		}
		// Membership from the level's own player list -- no entity query.
		double radiusSq = BOSS_BAR_RADIUS * BOSS_BAR_RADIUS;
		for (ServerPlayer player : server.players()) {
			boolean shouldSee = player.isAlive() && player.distanceToSqr(this) <= radiusSq;
			if (shouldSee) {
				bossBar.addPlayer(player);
			} else {
				bossBar.removePlayer(player);
			}
		}
	}

	/**
	 * Keep the boss bar's id equal to this entity's own (persistent) UUID.
	 *
	 * <p>{@code ServerBossEvent} mints a fresh random id on construction, and this entity's Java object
	 * is rebuilt every time its chunk reloads -- so without this, a player who leaves the area (dies and
	 * respawns far away, say) and returns is shown a <em>second</em> bar for the reloaded boss while the
	 * client still holds the first one, which it was never told to remove. Pinning the id means a
	 * reloaded boss reuses the same bar and the client just updates it.
	 */
	private void syncBarIdentity() {
		if (bossBar.getId().equals(getUUID())) {
			return;
		}
		java.util.List<ServerPlayer> members = java.util.List.copyOf(bossBar.getPlayers());
		members.forEach(bossBar::removePlayer);
		((com.herocraft.mod.mixin.BossEventAccessor) bossBar).herocraft$setId(getUUID());
		members.forEach(bossBar::addPlayer);
	}

	/**
	 * Tell <em>every</em> online player to drop this boss's bar, not just the ones currently in its
	 * member set. A player who was out of bar range when the boss died (or unloaded) was already
	 * dropped from the set, so {@link ServerBossEvent#removeAllPlayers()} would never reach them and
	 * they would keep a frozen ghost bar forever.
	 */
	private void broadcastBarRemoval() {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		var packet = net.minecraft.network.protocol.game.ClientboundBossEventPacket.createRemovePacket(bossBar.getId());
		for (ServerPlayer player : server.players()) {
			player.connection.send(packet);
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof ServerLevel server) {
			bossBar.removeAllPlayers();
			broadcastBarRemoval();
			server.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0f, 1.4f);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
					getX(), getY() + 1.0, getZ(), 60, 0.8, 1.0, 0.8, 0.08);
			server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
					getX(), getY() + 1.0, getZ(), 3, 0.5, 0.5, 0.5, 0.0);
		}
		super.die(source);
	}

	@Override
	public void remove(RemovalReason reason) {
		bossBar.removeAllPlayers();
		// On a real removal (killed, discarded by the raid) clear it off every client; on a chunk
		// unload this is harmless -- the reload re-adds the bar for anyone still in range.
		broadcastBarRemoval();
		super.remove(reason);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_POWER.equals(key) && !level().isClientSide()) {
			rebuildControllers();
			refreshBossBarName();
		}
		if (DATA_VARIANT.equals(key)) {
			refreshDimensions();
			if (!level().isClientSide()) {
				refreshBossBarName();
			}
		}
	}

	// ---------------- persistence ----------------

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putString("BossPower", powerKey());
		tag.putString("BossPower2", secondPowerKey());
		tag.putBoolean("FinalBoss", isFinalBoss());
		tag.putInt("Participants", participantCount);
		tag.putString("Variant", this.entityData.get(DATA_VARIANT));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		this.entityData.set(DATA_POWER, tag.getString("BossPower"));
		this.entityData.set(DATA_SECOND_POWER, tag.getString("BossPower2"));
		this.entityData.set(DATA_FINAL, tag.getBoolean("FinalBoss"));
		this.entityData.set(DATA_VARIANT, tag.getString("Variant"));
		this.participantCount = Math.max(1, tag.getInt("Participants"));
		rebuildControllers();
		refreshBossBarName();
		refreshDimensions();
	}
}
