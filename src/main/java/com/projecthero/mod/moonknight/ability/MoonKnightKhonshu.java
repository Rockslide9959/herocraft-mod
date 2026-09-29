package com.projecthero.mod.moonknight.ability;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.data.MoonKnightState;
import com.projecthero.mod.network.MoonKnightKhonshuFxPayload;
import com.projecthero.mod.squad.SquadManager;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Z -- Khonshu (Moon Knight Phase 6; on V before v0.13.21), plus the passive Khonshu's Resurrection.
 * <ul>
 *   <li><b>TAP</b> Moonbeam ({@code khonshu}, 10 s): a column of moonlight falls on the looked-at block / mob up to 40
 *       blocks away -- 10 (x lunar power) to every hostile within 2 blocks, double to the undead. Night only, 10
 *       Vengeance.</li>
 *   <li><b>HOLD 2 s</b> Eye of Khonshu (the ultimate): full moon + 100 Vengeance, once per night. Consumes all
 *       Vengeance; every hostile within 32 blocks glows and is Weakened (II) for 30 s, the player gets Strength II +
 *       Speed II for 30 s, and Khonshu's skull is drawn across the sky. Letting go early cancels for free.</li>
 *   <li><b>SNEAK+Z</b> Khonshu's Judgement ({@code khonshu_sneak}, 13 s): mark the targeted mob; if it dies within
 *       10 s, +20 Vengeance and 3 hearts back.</li>
 *   <li><b>Khonshu's Resurrection</b>: while suited, the first fatal hit of a lunar cycle is refused -- back to 6
 *       hearts with 2 s of invulnerability; it recharges at the next full moon night ({@code MoonKnight.tickSecond}).</li>
 * </ul>
 * Squad-mates are never hurt; bosses take at most {@link MoonKnightConfig#KHONSHU_BOSS_MAX_FRACTION} of their health
 * per Moonbeam and only Weakness I from the Eye.
 */
public final class MoonKnightKhonshu implements MoonKnightMove {
	public static final MoonKnightKhonshu INSTANCE = new MoonKnightKhonshu();

	private static final String TAP = "khonshu";
	private static final String SNEAK = "khonshu_sneak";
	/** {@code abilityReadyAt} key holding the night index ({@code dayTime / 24000}) the Eye was last opened on. */
	public static final String EYE_NIGHT_KEY = "eye_night";
	/** Slot number (1..6) of Z, for {@code MoonKnightAction.chargeKey}. */
	private static final int Z_SLOT = 4;

	private static final DustParticleOptions MOONLIGHT = new DustParticleOptions(new org.joml.Vector3f(0.86f, 0.92f, 1.0f), 1.6f);
	private static final DustParticleOptions PALE = new DustParticleOptions(new org.joml.Vector3f(0.97f, 0.97f, 0.94f), 1.0f);

	/** A hold in progress: CHARGING until it fires or is released; BLOCKED if a condition failed at the start. */
	private enum Hold { CHARGING, FIRED, BLOCKED }

	private record Mark(UUID target, long until) {
	}

	private static final Map<UUID, Hold> EYE_HOLD = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> EYE_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Mark> JUDGED = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> RESURRECT_SHIELD_UNTIL = new ConcurrentHashMap<>();

	private MoonKnightKhonshu() {
	}

	/** Registration (from {@code ProjectHeroMod}): the FX payload, the post-resurrection shield, disconnect cleanup. */
	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(MoonKnightKhonshuFxPayload.TYPE, MoonKnightKhonshuFxPayload.CODEC);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !shielded(entity, source));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID()));
	}

	public static void clearSessionState() {
		EYE_HOLD.clear();
		EYE_UNTIL.clear();
		JUDGED.clear();
		RESURRECT_SHIELD_UNTIL.clear();
	}

	private static void forget(UUID id) {
		EYE_HOLD.remove(id);
		EYE_UNTIL.remove(id);
		JUDGED.remove(id);
		RESURRECT_SHIELD_UNTIL.remove(id);
	}

	// ================================================================ TAP: Moonbeam

	@Override
	public void tap(ServerPlayer player) {
		moonbeam(player, MoonKnightAbilities.night(player));
	}

	/**
	 * Moonbeam, with the night check injectable (the gametests can't change the world clock). Returns true if the
	 * beam fell.
	 */
	public static boolean moonbeam(ServerPlayer player, boolean night) {
		if (!night) {
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.moonbeam_day")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
			return false;
		}
		if (!MoonKnightAbilities.ready(player, TAP)) {
			return false;
		}
		Vec3 at = moonbeamTarget(player);
		if (at == null) {
			MoonKnightAbilities.say(player, "message.projecthero.moon_knight.moonbeam_no_target");
			return false;
		}
		if (!MoonKnightAbilities.spendVengeance(player, MoonKnightConfig.MOONBEAM_COST)) {
			return false;
		}
		strikeMoonbeam(player, at, MoonKnightAbilities.power(player));
		MoonKnightAbilities.cooldown(player, TAP, MoonKnightConfig.MOONBEAM_COOLDOWN);
		return true;
	}

	/** The looked-at mob's feet, else the looked-at block, within 40 blocks; null if the look ray hits nothing. */
	public static Vec3 moonbeamTarget(ServerPlayer player) {
		LivingEntity e = AbilityHelpers.raycastEntity(player, MoonKnightConfig.MOONBEAM_RANGE);
		if (e != null) {
			return e.position();
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(player, MoonKnightConfig.MOONBEAM_RANGE);
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		return hit.getLocation();
	}

	/** Moonbeam damage for one target: base x lunar power, doubled against the undead, capped against bosses. */
	public static float moonbeamDamage(LivingEntity target, float power) {
		float dmg = MoonKnightLunar.scale(MoonKnightConfig.MOONBEAM_DAMAGE, power);
		if (target.getType().is(EntityTypeTags.UNDEAD)) {
			dmg *= MoonKnightConfig.MOONBEAM_UNDEAD_MULTIPLIER;
		}
		if (TitanCombat.isBoss(target)) {
			dmg = Math.min(dmg, target.getMaxHealth() * MoonKnightConfig.KHONSHU_BOSS_MAX_FRACTION);
		}
		return dmg;
	}

	/** Bring the moonlight down at {@code at}: hurt everything hostile in the column, draw it for everyone. Returns hits. */
	public static int strikeMoonbeam(ServerPlayer player, Vec3 at, float power) {
		ServerLevel level = player.serverLevel();
		double r = MoonKnightConfig.MOONBEAM_RADIUS;
		AABB column = new AABB(at.x - r, at.y - 1.5, at.z - r, at.x + r, at.y + 5.0, at.z + r);
		// moonlight: indirect magic credited to the player but with no direct entity, so it never counts as his melee
		// (the truncheon combo / alter melee bonuses key off a direct hit)
		DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
				.getHolderOrThrow(DamageTypes.INDIRECT_MAGIC), null, player);
		int hits = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, column, e -> isFoe(player, e))) {
			double dx = Math.max(Math.max(e.getBoundingBox().minX - at.x, at.x - e.getBoundingBox().maxX), 0.0);
			double dz = Math.max(Math.max(e.getBoundingBox().minZ - at.z, at.z - e.getBoundingBox().maxZ), 0.0);
			if (dx * dx + dz * dz > r * r) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(player, e, source, moonbeamDamage(e, power))) {
				hits++;
				level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10,
						0.2, 0.3, 0.2, 0.08);
				if (e.getType().is(EntityTypeTags.UNDEAD)) {
					level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8,
							0.25, 0.4, 0.25, 0.02);
				}
			}
		}
		moonbeamFx(player, level, at);
		return hits;
	}

	/** Whom Khonshu's light burns: hostile mobs, anything hunting the player, and players only where PvP allows. */
	static boolean isFoe(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive() || e instanceof ArmorStand) {
			return false;
		}
		if (e instanceof Player other) {
			MinecraftServer server = player.getServer();
			return server != null && server.isPvpAllowed() && HeroConfig.get().abilityPvpDamage
					&& !other.isSpectator() && !SquadManager.get(server).sameSquad(player.getUUID(), other.getUUID());
		}
		return e instanceof Enemy || (e instanceof Mob mob && mob.getTarget() == player);
	}

	private static void moonbeamFx(ServerPlayer player, ServerLevel level, Vec3 at) {
		MoonKnightAnim.play(player, MoonKnightAnim.MOONBEAM);
		// every nearby client draws the beam itself (a beacon-style column fading out)
		MoonKnightKhonshuFxPayload beam = new MoonKnightKhonshuFxPayload(MoonKnightKhonshuFxPayload.Kind.MOONBEAM,
				at.x, at.y, at.z, 30);
		for (ServerPlayer viewer : PlayerLookup.around(level, at, 160.0)) {
			send(viewer, beam);
		}
		// and particles for everyone: light pouring down the column, a ring at the radius, a flash at the impact
		for (int i = 0; i < 40; i++) {
			double y = at.y + 1.0 + i * 0.75;
			level.sendParticles(ParticleTypes.END_ROD, at.x, y, at.z, 1, 0.25, 0.2, 0.25, 0.0);
		}
		double r = MoonKnightConfig.MOONBEAM_RADIUS;
		for (int i = 0; i < 32; i++) {
			double ang = i * Math.PI * 2.0 / 32.0;
			level.sendParticles(MOONLIGHT, at.x + Math.cos(ang) * r, at.y + 0.15, at.z + Math.sin(ang) * r, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.2, at.z, 0, Math.cos(ang), 0.08, Math.sin(ang), 0.18);
		}
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(PALE, at.x, at.y + 1.0, at.z, 30, 0.6, 1.2, 0.6, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 1.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.4f, 0.6f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.35f, 1.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL,
				SoundSource.PLAYERS, 0.7f, 1.3f);
	}

	// ================================================================ HOLD: Eye of Khonshu

	@Override
	public void holdStart(ServerPlayer player) {
		Component blocker = eyeBlocker(player, MoonKnightAbilities.fullMoon(player));
		if (blocker != null) {
			player.displayClientMessage(blocker, true);
			EYE_HOLD.put(player.getUUID(), Hold.BLOCKED);
			return;
		}
		EYE_HOLD.put(player.getUUID(), Hold.CHARGING);
		MoonKnightAction c = MoonKnightAnim.action(player).with(MoonKnightAction.FLAG_CHARGING, true);
		c.chargeKey = Z_SLOT;
		c.chargeStart = player.level().getGameTime();
		MoonKnightAnim.save(player, c);
		MoonKnightAnim.play(player, MoonKnightAnim.EYE_CHARGE);
		ServerLevel level = player.serverLevel();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.5f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.PLAYERS, 0.4f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.eye_charging")
				.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC), true);
	}

	@Override
	public void holdTick(ServerPlayer player, int ticksHeld) {
		if (EYE_HOLD.get(player.getUUID()) != Hold.CHARGING) {
			return;
		}
		ServerLevel level = player.serverLevel();
		// motes of moonlight spiral in toward the raised hands, tighter as the Eye opens
		double frac = Math.min(1.0, ticksHeld / (double) MoonKnightConfig.EYE_HOLD_TICKS);
		double r = 2.6 - frac * 2.0;
		double ang = ticksHeld * 0.55;
		for (int k = 0; k < 3; k++) {
			double a = ang + k * Math.PI * 2.0 / 3.0;
			level.sendParticles(MOONLIGHT, player.getX() + Math.cos(a) * r, player.getY() + 2.4 + frac * 0.6,
					player.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (ticksHeld % 10 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
					SoundSource.PLAYERS, 0.8f, 0.6f + (float) frac * 0.5f);
		}
		if (ticksHeld >= MoonKnightConfig.EYE_HOLD_TICKS) {
			// the conditions are checked again at the moment it opens (Vengeance could have drained mid-hold)
			Component blocker = eyeBlocker(player, MoonKnightAbilities.fullMoon(player));
			endCharge(player);
			if (blocker != null) {
				player.displayClientMessage(blocker, true);
				EYE_HOLD.put(player.getUUID(), Hold.BLOCKED);
				return;
			}
			EYE_HOLD.put(player.getUUID(), Hold.FIRED);
			openEye(player);
		}
	}

	@Override
	public void holdRelease(ServerPlayer player, int ticksHeld) {
		Hold h = EYE_HOLD.remove(player.getUUID());
		if (h == Hold.CHARGING) {
			// let go before the Eye opened: nothing spent, no cooldown
			endCharge(player);
			MoonKnightAnim.stop(player, MoonKnightAnim.EYE_CHARGE);
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.eye_cancel")
					.withStyle(ChatFormatting.GRAY), true);
		}
	}

	@Override
	public void cancelHold(ServerPlayer player) {
		if (EYE_HOLD.remove(player.getUUID()) == Hold.CHARGING) {
			endCharge(player);
			MoonKnightAnim.stop(player, MoonKnightAnim.EYE_CHARGE);
		}
	}

	private static void endCharge(ServerPlayer player) {
		MoonKnightAction a = MoonKnightAnim.action(player);
		if (a.has(MoonKnightAction.FLAG_CHARGING) && a.chargeKey == Z_SLOT) {
			MoonKnightAction c = a.with(MoonKnightAction.FLAG_CHARGING, false);
			c.chargeKey = 0;
			MoonKnightAnim.save(player, c);
		}
	}

	/** Which night this is (a Minecraft night never crosses a {@code dayTime / 24000} boundary). */
	public static long nightIndex(ServerPlayer player) {
		return Math.floorDiv(player.level().getDayTime(), MoonKnightConfig.DAY_TICKS);
	}

	/**
	 * Why the Eye can't open right now (the message to show), or null if it can. {@code fullMoon} is injectable for the
	 * gametests; the key passes {@code MoonKnightAbilities.fullMoon}.
	 */
	public static Component eyeBlocker(ServerPlayer player, boolean fullMoon) {
		if (!fullMoon) {
			return Component.translatable("message.projecthero.moon_knight.eye_need_full_moon").withStyle(ChatFormatting.GRAY);
		}
		MoonKnightState s = MoonKnight.state(player);
		if (s.vengeance < MoonKnightConfig.VENGEANCE_MAX) {
			return Component.translatable("message.projecthero.moon_knight.eye_need_vengeance", (int) Math.floor(s.vengeance))
					.withStyle(ChatFormatting.RED);
		}
		Long used = s.abilityReadyAt.get(EYE_NIGHT_KEY);
		if (used != null && used == nightIndex(player)) {
			return Component.translatable("message.projecthero.moon_knight.eye_used").withStyle(ChatFormatting.GRAY);
		}
		return null;
	}

	/**
	 * Open the Eye (conditions already checked): consume all Vengeance -- deliberately, so it never triggers a Fracture
	 * -- mark tonight as used, reveal and weaken every hostile within 32 blocks, empower the player, draw the skull.
	 * Returns how many hostiles were caught.
	 */
	public static int openEye(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		long now = level.getGameTime();
		MoonKnightState c = MoonKnight.state(player).copy();
		c.vengeance = 0.0f;
		c.abilityReadyAt.put(EYE_NIGHT_KEY, nightIndex(player));
		MoonKnight.saveState(player, c);

		int ticks = MoonKnightConfig.EYE_DURATION;
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, MoonKnightConfig.EYE_PLAYER_AMPLIFIER, false, true, true));
		player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, MoonKnightConfig.EYE_PLAYER_AMPLIFIER, false, true, true));
		List<LivingEntity> foes = AbilityHelpers.living(level, player.position(), MoonKnightConfig.EYE_RADIUS,
				e -> e instanceof Enemy && e != player);
		for (LivingEntity e : foes) {
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false, true), player);
			int weak = TitanCombat.isBoss(e) ? 0 : MoonKnightConfig.EYE_WEAKNESS_AMPLIFIER;
			e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, weak, false, true, true), player);
			level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
		}
		EYE_UNTIL.put(player.getUUID(), now + ticks);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_EYE, true);
		MoonKnightAnim.play(player, MoonKnightAnim.EYE_RELEASE);

		// the sky sign: every client within range traces Khonshu's skull above the player, facing where he looks
		Vec3 look = player.getLookAngle();
		double yawDeg = Math.toDegrees(Math.atan2(-look.x, look.z));
		Vec3 centre = player.position().add(horizontal(look).scale(4.0)).add(0.0, MoonKnightConfig.EYE_SKULL_HEIGHT, 0.0);
		MoonKnightKhonshuFxPayload skull = new MoonKnightKhonshuFxPayload(MoonKnightKhonshuFxPayload.Kind.EYE_SKULL,
				centre.x, centre.y, centre.z, Math.round((float) yawDeg));
		for (ServerPlayer viewer : PlayerLookup.around(level, centre, 192.0)) {
			send(viewer, skull);
		}
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.3, 0.3, 0.3, 0.35);
		level.sendParticles(MOONLIGHT, player.getX(), player.getY() + 1.0, player.getZ(), 60, 1.5, 1.2, 1.5, 0.0);
		level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.5, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 1.2f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.0f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.eye", foes.size())
				.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
		return foes.size();
	}

	private static Vec3 horizontal(Vec3 look) {
		Vec3 h = new Vec3(look.x, 0.0, look.z);
		return h.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : h.normalize();
	}

	// ================================================================ SNEAK+Z: Khonshu's Judgement

	@Override
	public void sneak(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, SNEAK)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, MoonKnightConfig.JUDGEMENT_RANGE);
		if (target == null || target instanceof Player) {
			MoonKnightAbilities.say(player, "message.projecthero.moon_knight.judgement_no_target");
			return;
		}
		judge(player, target);
		MoonKnightAbilities.cooldown(player, SNEAK, MoonKnightConfig.JUDGEMENT_COOLDOWN);
	}

	/** Mark {@code target}: if it dies within {@link MoonKnightConfig#JUDGEMENT_TICKS}, the Moon Knight is repaid. */
	public static void judge(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.serverLevel();
		JUDGED.put(player.getUUID(), new Mark(target.getUUID(), level.getGameTime() + MoonKnightConfig.JUDGEMENT_TICKS));
		MoonKnightAnim.play(player, MoonKnightAnim.JUDGEMENT);
		Vec3 hand = AbilityHelpers.handPosition(player);
		Vec3 over = target.position().add(0.0, target.getBbHeight() + 0.6, 0.0);
		AbilityHelpers.line(level, hand, over, PALE, 3.0);
		level.sendParticles(ParticleTypes.END_ROD, over.x, over.y, over.z, 16, 0.25, 0.25, 0.25, 0.04);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.4f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 0.6f, 1.2f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.judgement", target.getDisplayName())
				.withStyle(ChatFormatting.WHITE), true);
	}

	/** A mob died (AFTER_DEATH): whoever judged it within the window is repaid. */
	public static void onEntityKilled(LivingEntity victim, DamageSource source) {
		if (JUDGED.isEmpty() || !(victim.level() instanceof ServerLevel level) || level.getServer() == null) {
			return;
		}
		long now = level.getGameTime();
		Iterator<Map.Entry<UUID, Mark>> it = JUDGED.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Mark> e = it.next();
			if (!e.getValue().target().equals(victim.getUUID())) {
				continue;
			}
			it.remove();
			ServerPlayer judge = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (judge == null || now > e.getValue().until() || !MoonKnight.hasPower(judge)) {
				continue;
			}
			fulfil(judge, victim);
		}
	}

	private static void fulfil(ServerPlayer player, LivingEntity victim) {
		MoonKnight.addVengeance(player, MoonKnightConfig.JUDGEMENT_REFUND);
		player.heal(MoonKnightConfig.JUDGEMENT_HEAL);
		ServerLevel level = player.serverLevel();
		if (victim.level() == level) {
			level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
					24, 0.3, 0.5, 0.3, 0.12);
			AbilityHelpers.line(level, victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0),
					player.position().add(0.0, 1.0, 0.0), MOONLIGHT, 2.0);
		}
		level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.0, player.getZ(), 3, 0.4, 0.2, 0.4, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.8f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6f, 1.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.judgement_fulfilled",
				Math.round(MoonKnightConfig.JUDGEMENT_REFUND)).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC), true);
	}

	/** Is {@code target} currently under this player's Judgement? (tests / future HUD) */
	public static boolean isJudged(ServerPlayer player, Entity target) {
		Mark m = JUDGED.get(player.getUUID());
		return m != null && m.target().equals(target.getUUID()) && m.until() >= player.level().getGameTime();
	}

	// ================================================================ Khonshu's Resurrection

	/** Khonshu's Resurrection: cancel a fatal hit once per moon cycle. Returns true if the death was averted. */
	public static boolean tryResurrect(ServerPlayer player) {
		MoonKnightState s = MoonKnight.state(player);
		if (!s.hasPact || !s.resurrectionCharged) {
			return false;
		}
		ServerLevel level = player.serverLevel();
		MoonKnightState c = s.copy();
		c.resurrectionCharged = false;
		c.resurrectionCycle = MoonKnightLunar.moonCycle(level);
		MoonKnight.saveState(player, c);

		player.setHealth(Math.min(player.getMaxHealth(), MoonKnightConfig.RESURRECT_HEALTH));
		player.clearFire();
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WITHER);
		player.setAirSupply(player.getMaxAirSupply());
		player.fallDistance = 0.0f;
		player.invulnerableTime = MoonKnightConfig.RESURRECT_INVULNERABLE_TICKS;
		RESURRECT_SHIELD_UNTIL.put(player.getUUID(), level.getGameTime() + MoonKnightConfig.RESURRECT_INVULNERABLE_TICKS);
		MoonKnightAnim.play(player, MoonKnightAnim.RESURRECT);

		// a white flash for the saved player, and for everyone: a burst of moonlight shaped like a crescent
		send(player, new MoonKnightKhonshuFxPayload(MoonKnightKhonshuFxPayload.Kind.RESURRECTION_FLASH,
				player.getX(), player.getY(), player.getZ(), 24));
		Vec3 look = horizontal(player.getLookAngle());
		Vec3 right = new Vec3(-look.z, 0.0, look.x);
		Vec3 c0 = player.position().add(0.0, 1.2, 0.0).subtract(look.scale(0.4));
		// a crescent standing behind the player, in the plane (right, up): the outer circle minus an offset inner one
		double outerR = 1.6;
		double innerR = 1.35;
		double innerOffset = 0.7;
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI * 2.0 / 48.0;
			double u = Math.cos(a);
			double v = Math.sin(a);
			if (Math.hypot(outerR * u - innerOffset, outerR * v) > innerR) {
				Vec3 dir = right.scale(u).add(0.0, v, 0.0);
				Vec3 p = c0.add(dir.scale(outerR));
				level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, dir.x, dir.y, dir.z, 0.12);
			}
			if (Math.hypot(innerOffset + innerR * u, innerR * v) < outerR) {
				Vec3 p = c0.add(right.scale(innerOffset + innerR * u)).add(0.0, innerR * v, 0.0);
				level.sendParticles(PALE, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.FLASH, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(PALE, player.getX(), player.getY() + 1.0, player.getZ(), 50, 0.6, 1.0, 0.6, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.2, 0.4, 0.2, 0.25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.0f, 1.2f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.9f, 1.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.resurrected")
				.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD, ChatFormatting.ITALIC), false);
		return true;
	}

	/** ALLOW_DAMAGE veto: the 2 s of invulnerability after a resurrection. */
	private static boolean shielded(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof ServerPlayer player) || RESURRECT_SHIELD_UNTIL.isEmpty()) {
			return false;
		}
		Long until = RESURRECT_SHIELD_UNTIL.get(player.getUUID());
		if (until == null) {
			return false;
		}
		if (player.level().getGameTime() >= until) {
			RESURRECT_SHIELD_UNTIL.remove(player.getUUID());
			return false;
		}
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	// ================================================================ upkeep

	@Override
	public void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		UUID id = player.getUUID();
		Long eye = EYE_UNTIL.get(id);
		if (eye != null) {
			if (now >= eye) {
				EYE_UNTIL.remove(id);
				MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_EYE, false);
			} else {
				AbilityHelpers.modeAura(player, MOONLIGHT, 3); // self-throttled (every 6 ticks)
			}
		}
		Mark mark = JUDGED.get(id);
		if (mark != null) {
			Entity target = player.serverLevel().getEntity(mark.target());
			if (now > mark.until() || !(target instanceof LivingEntity living) || !living.isAlive()) {
				if (now > mark.until()) {
					JUDGED.remove(id);
				}
			} else if (now % 4L == 0L) {
				drawMark(player.serverLevel(), living, now);
			}
		}
	}

	/** The mark of Judgement over the target's head: a slowly turning crescent of moonlight with a falling mote. */
	private static void drawMark(ServerLevel level, LivingEntity target, long now) {
		double cx = target.getX();
		double cy = target.getY() + target.getBbHeight() + 0.7;
		double cz = target.getZ();
		double spin = now * 0.08;
		for (int i = 0; i < 9; i++) {
			double a = spin + Math.toRadians(-100 + i * 25.0);
			double r = 0.45;
			double fx = Math.cos(spin + Math.PI / 2.0);
			double fz = Math.sin(spin + Math.PI / 2.0);
			// the crescent stands upright and turns about the vertical axis
			double x = cx + fx * Math.sin(a - spin) * r;
			double z = cz + fz * Math.sin(a - spin) * r;
			double y = cy + Math.cos(a - spin) * r;
			level.sendParticles(PALE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 1, 0.05, 0.05, 0.05, 0.0);
	}

	@Override
	public void onUntransform(ServerPlayer player) {
		UUID id = player.getUUID();
		EYE_HOLD.remove(id);
		JUDGED.remove(id);
		if (EYE_UNTIL.remove(id) != null) {
			MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_EYE, false);
		}
		endCharge(player);
	}

	// ================================================================ util

	/** Send a payload if the client can take it (mock players and vanilla clients are skipped quietly). */
	static void send(ServerPlayer player, CustomPacketPayload payload) {
		try {
			if (ServerPlayNetworking.canSend(player, payload.type())) {
				ServerPlayNetworking.send(player, payload);
			}
		} catch (RuntimeException ignored) {
			// a gametest mock connection has no play-network addon
		}
	}
}
