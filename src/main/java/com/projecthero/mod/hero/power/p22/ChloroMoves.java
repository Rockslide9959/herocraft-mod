package com.projecthero.mod.hero.power.p22;

import static com.projecthero.mod.hero.power.p22.PlantManipulationHandlers.*;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1 Chlorokinesis -- the moves themselves (registration, the meter and the numbers are in
 * {@link PlantManipulationHandlers}). Every public move spends its own Chlorophyll; the caller has already checked the
 * cooldown and that the meter can pay.
 */
public final class ChloroMoves {
	private static final ResourceLocation VERDANT_ARMOR_ID = com.projecthero.mod.ProjectHeroMod.id("chloro_verdant_armor");
	private static final ResourceLocation VERDANT_TOUGH_ID = com.projecthero.mod.ProjectHeroMod.id("chloro_verdant_toughness");
	private static final ResourceLocation VERDANT_KB_ID = com.projecthero.mod.ProjectHeroMod.id("chloro_verdant_kb");
	/** The pre-v0.17.1 Nature's Blessing modifier, cleared out of old saves. */
	private static final ResourceLocation LEGACY_BLESS = com.projecthero.mod.ProjectHeroMod.id("natures_blessing_atk");

	private ChloroMoves() {
	}

	// ================================================================ helpers

	static Vec3 handOrigin(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1.0e-6) {
			double yaw = Math.toRadians(p.getYRot());
			right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
		} else {
			right = right.normalize();
		}
		return p.getEyePosition().add(look.scale(0.6)).add(right.scale(0.36)).add(0, -0.4, 0);
	}

	private static Vec3 mid(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	private static ServerLevel level(ServerPlayer p) {
		return AbilityHelpers.level(p);
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-6 ? Vec3.directionFromRotation(0, p.getYRot()) : f.normalize();
	}

	// ================================================================ R: Thorn Volley / Vine Lash

	/** Three thorns in a narrow fan from the hand (5 each + Poison I 2.5 s on a hit). Returns the thorns. */
	public static List<ThornProjectile> thornVolley(ServerPlayer p) {
		spend(p, COST_VOLLEY);
		ServerLevel level = level(p);
		Vec3 from = handOrigin(p);
		// the fan converges on the crosshair (the hand sits off to the side of the eye)
		Vec3 look = AbilityHelpers.aimPoint(p, 24.0).subtract(from).normalize();
		List<ThornProjectile> out = new ArrayList<>();
		float dmg = dmg(p, VOLLEY_DAMAGE);
		for (int i = 0; i < VOLLEY_THORNS; i++) {
			double yaw = Math.toRadians((i - (VOLLEY_THORNS - 1) / 2.0) * 5.0);
			Vec3 dir = look.yRot((float) yaw);
			out.add(ThornProjectile.fire(level, p, p, from, from.add(dir.scale(20.0)), dmg));
		}
		level.sendParticles(ChloroParticles.LEAF, from.x, from.y, from.z, 3, 0.1, 0.1, 0.1, 0.03);
		MutationVisuals.play(p, "p22.volley");
		AbilityHelpers.sound(p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.3f);
		AbilityHelpers.sound(p, SoundEvents.ARROW_SHOOT, 0.5f, 1.6f);
		return out;
	}

	/**
	 * Shift+R: a thorned vine whips out up to 14 blocks: 12 to the first creature and it is yanked to just in front of you
	 * (a boss is not moved but takes +4). A miss still cracks the whip (half the cost, a short cooldown). Returns the
	 * creature hit, or null.
	 */
	public static LivingEntity vineLash(ServerPlayer p) {
		ServerLevel level = level(p);
		LivingEntity t = AbilityHelpers.raycastEntity(p, LASH_RANGE);
		MutationVisuals.play(p, "p22.lash");
		AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_BREAK, 1.2f, 1.4f);
		if (t == null || !com.projecthero.mod.combat.HeroTargets.canHarm(p, t)) {
			spend(p, COST_LASH * 0.5f);
			ChloroFxEntity.spawn(p, ChloroFxEntity.VINE, 9, p.position()).follow(p).style(ChloroFxEntity.STYLE_LASH)
					.end(AbilityHelpers.aimPoint(p, LASH_RANGE));
			startCountdown(p, LASH_CD_RES, 20);
			return null;
		}
		spend(p, COST_LASH);
		boolean boss = !movable(t);
		AbilityHelpers.hurtBurst(p, t, dmg(p, LASH_DAMAGE + (boss ? LASH_BOSS_BONUS : 0)));
		ChloroFxEntity.spawn(p, ChloroFxEntity.VINE, 12, p.position()).follow(p).target(t).style(ChloroFxEntity.STYLE_LASH)
				.end(mid(t));
		if (!boss && t.isAlive()) {
			Vec3 dest = p.position().add(flatLook(p).scale(2.0));
			Vec3 d = dest.subtract(t.position());
			double len = d.length();
			Vec3 v = len < 1.0e-3 ? Vec3.ZERO : d.normalize().scale(Math.min(2.4, 0.45 + len * 0.16)).add(0, 0.3 + Math.min(0.3, len * 0.02), 0);
			t.setDeltaMovement(v);
			t.hurtMarked = true;
			t.hasImpulse = true;
		}
		level.sendParticles(ChloroParticles.SAP, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 8, 0.25, 0.3, 0.25, 0.05);
		level.sendParticles(ChloroParticles.LEAF, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.7f);
		startCountdown(p, LASH_CD_RES, LASH_CD);
		return t;
	}

	// ================================================================ G: Root Snare / Spore Cloud

	/**
	 * Roots erupt under the creature looked at (20 blocks) -- or, aimed at the ground, under every enemy within 2.5 of the
	 * spot: 6 at once, then pinned 4 s at 2 a second (a boss is only slowed). Returns how many were caught (0 = nothing
	 * there, nothing spent).
	 */
	public static int rootSnare(ServerPlayer p) {
		ServerLevel level = level(p);
		List<LivingEntity> caught = new ArrayList<>();
		LivingEntity t = AbilityHelpers.raycastEntity(p, SNARE_RANGE);
		Vec3 spot;
		if (t != null && com.projecthero.mod.combat.HeroTargets.canHarm(p, t)) {
			caught.add(t);
			spot = t.position();
		} else {
			BlockHitResult hit = AbilityHelpers.raycastBlock(p, SNARE_RANGE);
			if (hit.getType() != HitResult.Type.BLOCK) {
				return 0;
			}
			spot = hit.getLocation();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, spot, SNARE_SPLASH)) {
				caught.add(e);
			}
		}
		if (caught.isEmpty()) {
			return 0;
		}
		spend(p, COST_SNARE);
		float d = dmg(p, SNARE_DAMAGE);
		for (LivingEntity e : caught) {
			AbilityHelpers.hurtBurst(p, e, d);
			ChloroFxEntity fx = ChloroFxEntity.spawn(p, ChloroFxEntity.ROOTS, SNARE_TICKS, e.position());
			fx.radius(Math.max(0.5f, e.getBbWidth() * 0.6f));
			fx.hold(e);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), e.getX(), e.getY() + 0.1,
					e.getZ(), 20, 0.4, 0.1, 0.4, 0.15);
			level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.PLAYERS, 1.3f, 0.6f);
		}
		MutationVisuals.play(p, "p22.snare");
		AbilityHelpers.sound(p, SoundEvents.HANGING_ROOTS_PLACE, 1.2f, 0.6f);
		return caught.size();
	}

	/** Shift+G: a 4.5-block cloud of spores 3 blocks ahead for 8 s. */
	public static ChloroFxEntity sporeCloud(ServerPlayer p) {
		spend(p, COST_SPORES);
		Vec3 at = p.position().add(flatLook(p).scale(3.0)).add(0, 0.8, 0);
		Power power = power();
		ExperimentalPowers.setMarker(p, power, "spore", BlockPos.containing(at));
		set(p, "spore_ticks", SPORE_TICKS);
		ChloroFxEntity fx = ChloroFxEntity.spawn(p, ChloroFxEntity.SPORES, SPORE_TICKS, Vec3.atCenterOf(BlockPos.containing(at)))
				.radius((float) SPORE_RADIUS);
		MutationVisuals.play(p, "p22.spore_blow");
		level(p).sendParticles(ChloroParticles.SPORE, at.x, at.y, at.z, 50, 1.5, 0.8, 1.5, 0.02);
		AbilityHelpers.sound(p, SoundEvents.SPORE_BLOSSOM_PLACE, 1.2f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.PUFFER_FISH_BLOW_OUT, 0.8f, 0.7f);
		return fx;
	}

	/** Spore Cloud upkeep: particles, and every 10 ticks a pulse. */
	static void sporeTick(ServerPlayer p) {
		float left = res(p, "spore_ticks");
		if (left <= 0.5f) {
			return;
		}
		set(p, "spore_ticks", left - 1);
		Power power = power();
		BlockPos at = ExperimentalPowers.getMarker(p, power, "spore");
		var dim = ExperimentalPowers.getMarkerDimension(p, power, "spore");
		if (at == null || (dim != null && !dim.equals(p.level().dimension()))) {
			return;
		}
		if (left - 1 <= 0.5f) {
			ExperimentalPowers.clearMarker(p, power, "spore");
		}
		ServerLevel level = level(p);
		Vec3 c = Vec3.atCenterOf(at);
		if (p.tickCount % 3 == 0) {
			level.sendParticles(ChloroParticles.SPORE, c.x, c.y, c.z, 5, SPORE_RADIUS * 0.45, 0.7, SPORE_RADIUS * 0.45, 0.0);
		}
		if (((int) left) % 10 != 0) {
			return;
		}
		pulseSpores(p, c);
	}

	/** One Spore Cloud pulse centred on {@code c}: enemies poisoned, blinded, slowed and lulled; allies healed. */
	static void pulseSpores(ServerPlayer p, Vec3 c) {
		ServerLevel level = level(p);
		for (LivingEntity e : AbilityHelpers.living(level, c, SPORE_RADIUS, e -> true)) {
			if (e == p || Squads.areAllies(p, e) || e instanceof TreantEntity) {
				if (e.getHealth() < e.getMaxHealth()) {
					e.heal(1.5f);
				}
				e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 0, false, false, true));
				level.sendParticles(ChloroParticles.PETAL, e.getX(), e.getY() + 1.0, e.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
				continue;
			}
			if (!com.projecthero.mod.combat.HeroTargets.isHostile(p, e)) {
				continue; // a lingering cloud is rule 2 -- threats only, never the farm
			}
			AbilityHelpers.applyControl(e, MobEffects.POISON, 60, 0);
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 60, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
			if (e instanceof Mob m && movable(m)) {
				m.setTarget(null); // lulled
			}
		}
	}

	// ================================================================ X: Vine Grapple / Leaf Glide

	/**
	 * A vine to the block looked at (48 blocks) reels you in -- or, aimed at a creature within 30, reels it to you. No fall
	 * damage for 10 s after. Returns false (nothing spent) when there is nothing to catch.
	 */
	public static boolean vineGrapple(ServerPlayer p) {
		ServerLevel level = level(p);
		LivingEntity target = AbilityHelpers.raycastEntity(p, GRAPPLE_ENTITY_RANGE);
		if (target != null && com.projecthero.mod.combat.HeroTargets.canHarm(p, target) && movable(target)) {
			spend(p, COST_GRAPPLE);
			MutationVisuals.play(p, "p22.lash");
			Vec3 d = p.position().subtract(target.position());
			double len = d.length();
			AbilityHelpers.push(target, d.normalize().scale(Math.min(2.6, 0.6 + len * 0.12)).add(0, 0.25, 0));
			ChloroFxEntity.spawn(p, ChloroFxEntity.VINE, 10, p.position()).follow(p).target(target).end(mid(target));
			AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_HIT, 1.0f, 0.9f);
			return true;
		}
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, GRAPPLE_RANGE);
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		spend(p, COST_GRAPPLE);
		Vec3 anchor = hit.getLocation();
		Vec3 pull = anchor.subtract(p.position());
		double dist = pull.length();
		double speed = Math.min(2.6, 1.1 + dist * 0.035);
		MutationVisuals.play(p, "p22.grapple");
		AbilityHelpers.launchSelf(p, pull.normalize().scale(speed).add(0, 0.35, 0));
		int life = (int) Math.max(8, Math.min(30, dist / speed + 4));
		ChloroFxEntity.spawn(p, ChloroFxEntity.VINE, life, p.position()).follow(p).end(anchor);
		level.sendParticles(ChloroParticles.LEAF, anchor.x, anchor.y, anchor.z, 6, 0.2, 0.2, 0.2, 0.03);
		AbilityHelpers.sound(p, SoundEvents.WEEPING_VINES_HIT, 1.0f, 1.2f);
		set(p, "no_fall_until", p.level().getGameTime() + 200);
		return true;
	}

	/** Shift+X: the leaf canopy opens -- elytra flight while it is out (a hop first from the ground). */
	public static void startGlide(ServerPlayer p) {
		spend(p, COST_GLIDE_OPEN);
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 0.75, p.getDeltaMovement().z));
		}
		set(p, "glide", 1);
		set(p, "glide_t", 0);
		setKeyLit(p, AbilitySlot.SLOT_3, true);
		if (!p.onGround() && !p.isInWater()) {
			p.startFallFlying();
		}
		MutationVisuals.play(p, "p22.glide");
		level(p).sendParticles(ChloroParticles.LEAF, p.getX(), p.getY() + 2.0, p.getZ(), 18, 0.8, 0.2, 0.8, 0.02);
		AbilityHelpers.sound(p, SoundEvents.AZALEA_LEAVES_PLACE, 1.2f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), 0.8f, 1.2f);
	}

	static void glideTick(ServerPlayer p) {
		if (res(p, "glide") < 0.5f) {
			return;
		}
		float t = res(p, "glide_t") + 1;
		set(p, "glide_t", Math.min(t, 1000));
		if (((p.onGround() || p.isInWater()) && t > 8) || !p.isAlive() || p.getAbilities().flying || chlorophyll(p) <= 0.01f) {
			endGlide(p);
			return;
		}
		spend(p, GLIDE_DRAIN_PER_SECOND / 20.0f);
		p.resetFallDistance();
		if (!p.isFallFlying() && !p.onGround() && !p.isInWater() && t > 2) {
			p.startFallFlying();
		}
		MutationVisuals.ensure(p, "p22.glide");
		if (p.tickCount % 4 == 0) {
			level(p).sendParticles(ChloroParticles.LEAF, p.getX(), p.getY() + 1.8, p.getZ(), 1, 0.7, 0.1, 0.7, 0.0);
		}
	}

	public static void endGlide(ServerPlayer p) {
		if (res(p, "glide") < 0.5f) {
			return;
		}
		set(p, "glide", 0);
		setKeyLit(p, AbilitySlot.SLOT_3, false);
		MutationVisuals.stopIf(p, "p22.glide");
		if (p.isFallFlying()) {
			p.stopFallFlying();
		}
		p.resetFallDistance();
	}

	/** Lights a key's HUD box (the active border) while its Shift / toggle mode runs. */
	private static void setKeyLit(ServerPlayer p, AbilitySlot slot, boolean on) {
		Power power = power();
		if (power != null && power.ability(slot) != null) {
			ExperimentalPowers.setToggled(p, power, power.ability(slot), on);
		}
	}

	// ================================================================ Z: Overgrowth / Treant

	/** Z: the gather begins (1.5 s, rooted to the spot, roots coiling up the body). */
	public static void startOvergrowth(ServerPlayer p) {
		spend(p, COST_OVERGROWTH);
		set(p, "og_gather", OVERGROWTH_GATHER);
		set(p, "ult_charge", 0);
		ChloroFxEntity.spawn(p, ChloroFxEntity.OVERGROWTH, OVERGROWTH_GATHER + 30, p.position()).follow(p).radius((float) OVERGROWTH_RADIUS);
		MutationVisuals.play(p, "p22.gather");
		AbilityHelpers.sound(p, SoundEvents.ROOTED_DIRT_PLACE, 1.4f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.BIG_DRIPLEAF_TILT_DOWN, 1.0f, 0.5f);
	}

	/** Runs the gather; true on the tick it bursts (the caller starts the cooldown). */
	static boolean overgrowthTick(ServerPlayer p) {
		float left = res(p, "og_gather");
		if (left < 0.5f) {
			return false;
		}
		left -= 1;
		set(p, "og_gather", left);
		set(p, "ult_charge", (1.0f - left / OVERGROWTH_GATHER) * 100.0f);
		if (!p.isAlive()) {
			set(p, "og_gather", 0);
			set(p, "ult_charge", 0);
			return false;
		}
		p.setDeltaMovement(0, Math.min(0, p.getDeltaMovement().y), 0);
		p.hurtMarked = true;
		MutationVisuals.ensure(p, "p22.gather");
		ServerLevel level = level(p);
		if (p.tickCount % 2 == 0) {
			double a = p.tickCount * 0.6;
			level.sendParticles(ChloroParticles.SAP, p.getX() + Math.cos(a) * 0.7, p.getY() + (1 - left / OVERGROWTH_GATHER) * 2.0,
					p.getZ() + Math.sin(a) * 0.7, 1, 0, 0, 0, 0);
		}
		if (left >= 0.5f) {
			return false;
		}
		set(p, "ult_charge", 0);
		overgrowthBurst(p);
		return true;
	}

	/**
	 * The eruption: everything hostile within 12 takes 30, Poison II 6 s and is rooted 4 s (a boss gets Slowness IV); every
	 * crop and sapling in the radius matures, bare dirt greens, flowers sprout. Returns how many were hit.
	 */
	public static int overgrowthBurst(ServerPlayer p) {
		ServerLevel level = level(p);
		Vec3 c = p.position();
		MutationVisuals.stopIf(p, "p22.gather");
		MutationVisuals.play(p, "p22.slam");
		// the wave stays where it broke
		for (ChloroFxEntity fx : level.getEntitiesOfClass(ChloroFxEntity.class, p.getBoundingBox().inflate(2.0),
				f -> f.kind() == ChloroFxEntity.OVERGROWTH && p.getUUID().equals(f.ownerId()))) {
			fx.follow(null);
		}
		int hit = 0;
		int rooted = 0;
		float d = dmg(p, OVERGROWTH_DAMAGE);
		for (LivingEntity e : AbilityHelpers.hostilesAround(p, c, OVERGROWTH_RADIUS)) {
			AbilityHelpers.hurtBurst(p, e, d);
			AbilityHelpers.applyControl(e, MobEffects.POISON, 120, 1);
			if (movable(e) && rooted < 16) {
				ChloroFxEntity fx = ChloroFxEntity.spawn(p, ChloroFxEntity.ROOTS, SNARE_TICKS, e.position());
				fx.radius(Math.max(0.5f, e.getBbWidth() * 0.6f));
				fx.hold(e);
				rooted++;
			} else {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, SNARE_TICKS, 3);
			}
			hit++;
		}
		ChloroSurvival.overgrowLand(p, level, BlockPos.containing(c), (int) OVERGROWTH_RADIUS);
		level.sendParticles(ChloroParticles.LEAF, c.x, c.y + 1.0, c.z, 120, OVERGROWTH_RADIUS * 0.45, 1.2, OVERGROWTH_RADIUS * 0.45, 0.04);
		level.sendParticles(ChloroParticles.PETAL, c.x, c.y + 0.8, c.z, 50, OVERGROWTH_RADIUS * 0.4, 0.6, OVERGROWTH_RADIUS * 0.4, 0.03);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), c.x, c.y + 0.2, c.z, 90,
				OVERGROWTH_RADIUS * 0.35, 0.2, OVERGROWTH_RADIUS * 0.35, 0.2);
		AbilityHelpers.sound(p, SoundEvents.ROOTED_DIRT_BREAK, 2.0f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.GRASS_BREAK, 1.6f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.AZALEA_LEAVES_BREAK, 1.6f, 0.6f);
		return hit;
	}

	/** Shift+Z: a Treant grows up out of the ground where you look (one at a time). */
	public static TreantEntity summonTreant(ServerPlayer p) {
		ServerLevel level = level(p);
		Vec3 spot;
		BlockHitResult hit = AbilityHelpers.raycastBlock(p, 8.0);
		if (hit.getType() == HitResult.Type.BLOCK && hit.getDirection() == Direction.UP) {
			spot = hit.getLocation();
		} else {
			spot = p.position().add(flatLook(p).scale(2.0));
		}
		TreantEntity t = PlantEntities.TREANT.create(level);
		if (t == null) {
			return null;
		}
		removeTreant(p);
		spend(p, COST_TREANT);
		t.bind(p);
		t.moveTo(spot.x, spot.y, spot.z, p.getYRot() + 180.0f, 0.0f);
		if (!level.noCollision(t)) {
			t.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0.0f);
		}
		t.setYHeadRot(p.getYRot());
		level.addFreshEntity(t);
		set(p, "treant_ticks", TreantEntity.LIFETIME);
		ChloroFxEntity.spawn(p, ChloroFxEntity.ROOTS, 30, spot).radius(0.9f);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), spot.x, spot.y + 0.1, spot.z,
				40, 0.5, 0.1, 0.5, 0.2);
		level.sendParticles(ChloroParticles.LEAF, spot.x, spot.y + 2.2, spot.z, 24, 0.6, 0.5, 0.6, 0.03);
		MutationVisuals.play(p, "p22.summon");
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.5f, 0.5f);
		return t;
	}

	public static void removeTreant(ServerPlayer p) {
		for (TreantEntity t : level(p).getEntities(PlantEntities.TREANT, e -> e.ownerId().map(p.getUUID()::equals).orElse(false))) {
			t.wither();
		}
		set(p, "treant_ticks", 0);
	}

	// ================================================================ V: Bloom Burst

	/** A ring of flowers blooms round you for 6 s: you and your squad healed, poisons cleansed, crops bone-mealed. */
	public static ChloroFxEntity bloomBurst(ServerPlayer p) {
		spend(p, COST_BLOOM);
		ServerLevel level = level(p);
		set(p, "bloom_ticks", BLOOM_TICKS);
		ChloroFxEntity fx = ChloroFxEntity.spawn(p, ChloroFxEntity.BLOOM, BLOOM_TICKS, p.position()).follow(p).radius((float) BLOOM_RADIUS);
		for (LivingEntity e : bloomed(p)) {
			e.removeEffect(MobEffects.POISON);
			e.removeEffect(MobEffects.WITHER);
			e.removeEffect(MobEffects.HUNGER);
		}
		ChloroSurvival.growCrops(level, p.blockPosition(), (int) BLOOM_RADIUS, 1);
		level.sendParticles(ChloroParticles.PETAL, p.getX(), p.getY() + 0.6, p.getZ(), 40, BLOOM_RADIUS * 0.4, 0.3, BLOOM_RADIUS * 0.4, 0.02);
		MutationVisuals.play(p, "p22.bloom");
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
		AbilityHelpers.sound(p, SoundEvents.FLOWERING_AZALEA_PLACE, 1.4f, 0.8f);
		return fx;
	}

	/** Everyone the bloom heals: you, your squad, your Treant. */
	private static List<LivingEntity> bloomed(ServerPlayer p) {
		return AbilityHelpers.living(level(p), p.position(), BLOOM_RADIUS,
				e -> e == p || Squads.areAllies(p, e) || (e instanceof TreantEntity t && t.ownerId().map(p.getUUID()::equals).orElse(false)));
	}

	static void bloomTick(ServerPlayer p) {
		int left = Math.round(res(p, "bloom_ticks"));
		if (left <= 0) {
			return;
		}
		ServerLevel level = level(p);
		if (left % 20 == 0) {
			for (LivingEntity e : bloomed(p)) {
				if (e.getHealth() < e.getMaxHealth()) {
					e.heal(BLOOM_HEAL_PER_SECOND);
				}
				level.sendParticles(ChloroParticles.PETAL, e.getX(), e.getY() + 1.2, e.getZ(), 3, 0.3, 0.4, 0.3, 0.0);
			}
		}
		if (left % 5 == 0) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = BLOOM_RADIUS * (0.5 + level.random.nextDouble() * 0.5);
			level.sendParticles(ChloroParticles.PETAL, p.getX() + Math.cos(a) * r, p.getY() + 0.8, p.getZ() + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.0);
		}
		if (left == 1) {
			dropFruit(p);
		}
	}

	/** The flowers fruit as they wilt: 2 sweet berries, and an apple 35% of the time. */
	static void dropFruit(ServerPlayer p) {
		ServerLevel level = level(p);
		spawnItem(level, p.position().add(0, 0.6, 0), new ItemStack(Items.SWEET_BERRIES, 2));
		if (level.random.nextFloat() < 0.35f) {
			spawnItem(level, p.position().add(0, 0.6, 0), new ItemStack(Items.APPLE));
		}
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.PLAYERS, 1.0f, 1.1f);
	}

	static void spawnItem(ServerLevel level, Vec3 at, ItemStack stack) {
		ItemEntity it = new ItemEntity(level, at.x, at.y, at.z, stack);
		it.setDefaultPickUpDelay();
		level.addFreshEntity(it);
	}

	// ================================================================ C: Verdant Form

	/** Verdant Form on / off: the bark skin's armour, toughness and footing. */
	public static void setVerdant(ServerPlayer p, boolean on) {
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, LEGACY_BLESS);
		boolean was = res(p, "verdant") > 0.5f;
		set(p, "verdant", on ? 1 : 0);
		setKeyLit(p, AbilitySlot.SLOT_6, on);
		if (on) {
			applyVerdant(p);
			if (!was) {
				MutationVisuals.play(p, "p22.verdant");
				level(p).sendParticles(ChloroParticles.LEAF, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.4, 0.8, 0.4, 0.03);
				level(p).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState()), p.getX(), p.getY() + 1.0,
						p.getZ(), 20, 0.3, 0.7, 0.3, 0.05);
				AbilityHelpers.sound(p, SoundEvents.WOOD_PLACE, 1.0f, 0.6f);
				AbilityHelpers.sound(p, SoundEvents.AZALEA_LEAVES_PLACE, 1.0f, 0.8f);
			}
		} else {
			PowerToggles.clearModifier(p, Attributes.ARMOR, VERDANT_ARMOR_ID);
			PowerToggles.clearModifier(p, Attributes.ARMOR_TOUGHNESS, VERDANT_TOUGH_ID);
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, VERDANT_KB_ID);
			if (was) {
				level(p).sendParticles(ChloroParticles.LEAF, p.getX(), p.getY() + 1.0, p.getZ(), 16, 0.4, 0.8, 0.4, 0.03);
				AbilityHelpers.sound(p, SoundEvents.AZALEA_LEAVES_BREAK, 1.0f, 0.9f);
			}
		}
	}

	private static void applyVerdant(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.ARMOR, VERDANT_ARMOR_ID, VERDANT_ARMOR, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ARMOR_TOUGHNESS, VERDANT_TOUGH_ID, VERDANT_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, VERDANT_KB_ID, VERDANT_KB, AttributeModifier.Operation.ADD_VALUE);
	}

	static void verdantTick(ServerPlayer p) {
		if (res(p, "verdant") < 0.5f) {
			return;
		}
		if (!p.isAlive()) {
			return;
		}
		if (p.tickCount % 20 == 0) {
			applyVerdant(p); // a respawn / relog drops transient modifiers
		}
		if (inSunlight(p)) {
			// Photosynthesis
			if (p.tickCount % 40 == 0 && p.getHealth() < p.getMaxHealth()) {
				p.heal(1.0f);
			}
			if (p.tickCount % 160 == 0 && p.getFoodData().needsFood()) {
				p.getFoodData().eat(1, 0.25f);
			}
		} else {
			spend(p, VERDANT_DRAIN_PER_SECOND / 20.0f);
			if (chlorophyll(p) <= 0.01f) {
				setVerdant(p, false);
				p.displayClientMessage(Component.translatable("message.projecthero.chloro.verdant_wilts"), true);
				return;
			}
		}
		if (p.tickCount % 7 == 0) {
			level(p).sendParticles(ChloroParticles.LEAF, p.getX(), p.getY() + 1.0 + p.getRandom().nextDouble() * 0.8, p.getZ(), 1, 0.3, 0.3, 0.3,
					0.0);
		}
	}

	/** Verdant Form's thorns: a melee attacker takes 3. */
	private static final ThreadLocal<Boolean> IN_THORNS = ThreadLocal.withInitial(() -> false);

	static void onDamaged(LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		if (!(victim instanceof ServerPlayer p) || IN_THORNS.get() || !blessingOn(p)) {
			return;
		}
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker || attacker == p
				|| attacker.distanceToSqr(p) > 36.0) {
			return; // melee only: the hitter itself, close by
		}
		IN_THORNS.set(true);
		try {
			if (AbilityHelpers.hurt(p, attacker, VERDANT_THORNS)) {
				level(p).sendParticles(ChloroParticles.SAP, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 5,
						0.2, 0.2, 0.2, 0.05);
			}
		} finally {
			IN_THORNS.set(false);
		}
	}
}
