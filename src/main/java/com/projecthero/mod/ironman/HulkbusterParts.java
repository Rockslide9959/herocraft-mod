package com.projecthero.mod.ironman;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.item.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Hulkbuster "Veronica" overhaul: "I want the armour to actually take damage based on the damage its taken
 * and its remaining integrity, an arm piece can break or a leg piece and some functions can be disabled".
 *
 * <p>The Hulkbuster I (mark_8) has six parts -- {@link #HEAD}, {@link #TORSO}, {@link #LARM}, {@link #RARM}, {@link #LLEG},
 * {@link #RLEG} -- each with a damage value, 0 whole .. 1 broken, kept on the CHESTPLATE stack
 * ({@link ModDataComponents#HULKBUSTER_PARTS}) so it travels with the suit everywhere the stacks go (worn, Sentry, drop
 * crate, Suit Platform, Uplink) and is synced to every viewer.
 *
 * <h2>The rule that ties parts to integrity</h2>
 * The whole-suit integrity still drives the suit (it absorbs every hit, 0 wrecks it -- {@link HulkbusterShield}). Each
 * part owns {@link #SHARE} of it; a hit that lands on a part ({@link #route}) adds {@code hit / (BREAK_SHARE * share * max)}
 * to that part, so a part breaks once it has soaked {@link #BREAK_SHARE} (70%) of its own share -- concentrated fire takes
 * a limb off long before the suit is wrecked, while evenly spread hits break everything at ~30% integrity. Repairs that
 * only raise integrity (the Suit Platform's slow repair) scale the parts back with it: {@link #effective} never shows
 * more damage than the integrity lost ({@code BREAK_SHARE * sum(share * damage) <= 1 - integrity}), and a full suit is
 * always whole. The Replacement Pod clears parts plate by plate ({@code HulkbusterPodEntity}).
 *
 * <h2>Broken parts</h2>
 * <ul>
 *   <li><b>Arm</b>: that fist / palm is dead -- the combo skips its blows, the Rocket Fist and the Palm Cannon fire from the
 *       other hand only; with both arms gone every fist move is offline, and the two-fisted Pile Driver needs both.</li>
 *   <li><b>Leg</b>: -25% walking speed per leg ({@link #LEG_SLOW_ID}), flight thrust x0.7 / x0.45, the Earthquake Stomp
 *       and the Pile Driver (a leap) are offline, the suit limps (client clip).</li>
 *   <li><b>Head</b>: the targeting is gone -- the Missile Swarm (needs locks) is offline, the targeting HUD is dark and the
 *       Mega Beam wobbles ({@link #beamWobble}).</li>
 *   <li><b>Torso</b>: the chest beam emitter is cracked -- the Mega Beam is offline and the reactor leaks
 *       {@link #TORSO_LEAK_PER_SECOND} energy a second.</li>
 * </ul>
 * Pure data + server helpers; the client reads the same component (HUD silhouette, greyed keys, per-part damage textures).
 */
public final class HulkbusterParts {
	public static final String SUIT_ID = "mark_8";
	public static final int HEAD = 0, TORSO = 1, LARM = 2, RARM = 3, LLEG = 4, RLEG = 5, COUNT = 6;
	/** Each part's share of the suit's integrity (sums to 1). */
	public static final float[] SHARE = { 0.12f, 0.28f, 0.15f, 0.15f, 0.15f, 0.15f };
	/** A part breaks after soaking this fraction of its own share of the integrity. */
	public static final float BREAK_SHARE = 0.7f;
	/** At or over this a part counts as broken. */
	public static final float BROKEN = 0.999f;
	/** Walking speed lost per broken leg (multiplied total). */
	public static final float LEG_SLOW = 0.25f;
	/** Energy a second a broken torso leaks out of the reactor. */
	public static final float TORSO_LEAK_PER_SECOND = 4f;
	public static final ResourceLocation LEG_SLOW_ID = ResourceLocation.fromNamespaceAndPath("projecthero", "hulkbuster_broken_leg");
	public static final String[] NAMES = { "head", "torso", "left_arm", "right_arm", "left_leg", "right_leg" };

	private HulkbusterParts() {
	}

	// ======================================================================== the component

	/** The raw stored damage on a chestplate (6 values, all 0 when absent). Never null. */
	public static float[] raw(ItemStack chest) {
		float[] d = new float[COUNT];
		List<Float> v = chest == null ? null : chest.get(ModDataComponents.HULKBUSTER_PARTS);
		if (v != null) {
			for (int i = 0; i < COUNT && i < v.size(); i++) {
				Float f = v.get(i);
				d[i] = f == null ? 0f : Mth.clamp(f, 0f, 1f);
			}
		}
		return d;
	}

	/** Store {@code d} on the chestplate (the component is removed when every part is whole). */
	public static void write(ItemStack chest, float[] d) {
		if (chest == null || chest.isEmpty()) {
			return;
		}
		boolean any = false;
		List<Float> out = new ArrayList<>(COUNT);
		for (int i = 0; i < COUNT; i++) {
			float v = Mth.clamp(d[i], 0f, 1f);
			// quantised to 1/1000 so tiny float noise does not resend the stack
			v = Math.round(v * 1000f) / 1000f;
			out.add(v);
			any |= v > 0f;
		}
		if (any) {
			chest.set(ModDataComponents.HULKBUSTER_PARTS, out);
		} else {
			chest.remove(ModDataComponents.HULKBUSTER_PARTS);
		}
	}

	/** Weighted damage (in integrity fractions) the raw parts stand for. */
	public static float weighted(float[] raw) {
		float s = 0f;
		for (int i = 0; i < COUNT; i++) {
			s += SHARE[i] * raw[i];
		}
		return BREAK_SHARE * s;
	}

	/**
	 * The damage to show / play by: the raw parts, scaled back so they never stand for more than the integrity actually
	 * missing ({@code 1 - integrityFraction}). A full suit is always whole.
	 */
	public static float[] effective(float[] raw, float integrityFraction) {
		float lost = 1f - Mth.clamp(integrityFraction, 0f, 1f);
		float w = weighted(raw);
		float k = w <= lost + 1.0e-4f || w <= 0f ? 1f : Math.max(0f, lost / w);
		float[] out = new float[COUNT];
		for (int i = 0; i < COUNT; i++) {
			out[i] = raw[i] * k;
		}
		return out;
	}

	public static boolean broken(float v) {
		return v >= BROKEN;
	}

	/** Bit {@code 1 << part} for every broken part. */
	public static int brokenMask(float[] eff) {
		int m = 0;
		for (int i = 0; i < COUNT; i++) {
			if (broken(eff[i])) {
				m |= 1 << i;
			}
		}
		return m;
	}

	// ======================================================================== reading a wearer (both sides)

	/** Is {@code e} wearing the whole Hulkbuster? */
	public static boolean wearing(LivingEntity e) {
		return e instanceof Player p && IronManArmor.wearingFullSuit(p, SUIT_ID);
	}

	/** The worn Hulkbuster's integrity fraction from the synced suit state (1 if unknown). */
	public static float integrityFraction(Player p) {
		TonyStarkState s = p.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		if (s == null) {
			return 1f;
		}
		Float v = s.suitIntegrity.get(SUIT_ID);
		float max = IronManEnergy.maxIntegrity(SUIT_ID);
		return v == null || max <= 0f ? 1f : Mth.clamp(v / max, 0f, 1f);
	}

	/** A worn Hulkbuster's effective part damage (all 0 when not worn). */
	public static float[] of(Player p) {
		if (!wearing(p)) {
			return new float[COUNT];
		}
		return effective(raw(p.getItemBySlot(EquipmentSlot.CHEST)), integrityFraction(p));
	}

	/** A loose / racked / Sentry chestplate's effective damage at {@code integrityFraction}. */
	public static float[] of(ItemStack chest, float integrityFraction) {
		return effective(raw(chest), integrityFraction);
	}

	/** Is that arm (right = true) of the worn suit still working? */
	public static boolean armWorks(Player p, boolean right) {
		return !broken(of(p)[right ? RARM : LARM]);
	}

	public static int brokenLegs(float[] eff) {
		return (broken(eff[LLEG]) ? 1 : 0) + (broken(eff[RLEG]) ? 1 : 0);
	}

	/** Flight thrust factor from the legs (the boot jets): 1, 0.7 with one leg gone, 0.45 with both. */
	public static double flightFactor(Player p) {
		if (!wearing(p)) {
			return 1.0;
		}
		return switch (brokenLegs(of(p))) {
			case 0 -> 1.0;
			case 1 -> 0.7;
			default -> 0.45;
		};
	}

	/**
	 * Why {@code moveId} can't fire with these parts (a lang key tail under {@code message.projecthero.ironman.hb_part.}),
	 * or null when it can. Shared by the router and the HUD (greyed key with a cross).
	 */
	public static String blockedBy(String moveId, float[] eff) {
		if (moveId == null || eff == null) {
			return null;
		}
		boolean lArm = broken(eff[LARM]), rArm = broken(eff[RARM]);
		boolean legs = broken(eff[LLEG]) || broken(eff[RLEG]);
		return switch (moveId) {
			case "mk8_punch_combo", "mk8_rocket_fist", "mk8_grab_pound", "mk8_grab_throw", "mk8_palm_cannon" -> lArm && rArm ? "arms" : null;
			case "mk8_pile_driver" -> lArm || rArm ? "arm" : legs ? "leg" : null;
			case "mk8_earthquake_stomp" -> legs ? "leg" : null;
			case "mk8_mega_beam" -> broken(eff[TORSO]) ? "torso" : null;
			case "mk8_missile_swarm" -> broken(eff[HEAD]) ? "head" : null;
			default -> null;
		};
	}

	// ======================================================================== taking hits (server)

	/**
	 * Which part {@code source} lands on, for a hit on {@code p}: the hit's height on the body (a projectile's / attacker's
	 * eyes / the source position, against the suit's height) picks head / chest band / legs, and the side it came from
	 * biases left / right. Falls and fire go to the legs; a source with no position anywhere (weighted by share).
	 */
	public static int route(Player p, DamageSource source, RandomSource rnd) {
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE) && source.getEntity() == null) {
			return rnd.nextBoolean() ? LLEG : RLEG;
		}
		Vec3 from = null;
		Entity direct = source.getDirectEntity();
		Entity att = source.getEntity();
		if (direct instanceof Projectile || direct != null && direct != att) {
			from = direct.position().add(0, direct.getBbHeight() * 0.5, 0);
		} else if (att instanceof LivingEntity le) {
			from = le.getEyePosition().add(0, -0.25, 0); // a blow lands a little under the eyes
		} else if (source.getSourcePosition() != null) {
			from = source.getSourcePosition();
		}
		if (from == null) {
			float r = rnd.nextFloat();
			for (int i = 0; i < COUNT; i++) {
				r -= SHARE[i];
				if (r <= 0f) {
					return i;
				}
			}
			return TORSO;
		}
		double height = Math.max(1.0, p.getBbHeight());
		double h = (from.y - p.getY()) / height;
		Vec3 flat = from.subtract(p.position()).multiply(1, 0, 1);
		double yaw = Math.toRadians(p.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		double side = flat.lengthSqr() < 1.0e-4 ? 0.0 : Mth.clamp(flat.normalize().dot(right), -1.0, 1.0);
		if (h > 0.80) {
			return rnd.nextFloat() < 0.8f ? HEAD : TORSO;
		}
		if (h > 0.42) {
			float r = rnd.nextFloat();
			float rArm = (float) (0.22 + 0.22 * side), lArm = (float) (0.22 - 0.22 * side);
			if (r < rArm) {
				return RARM;
			}
			if (r < rArm + lArm) {
				return LARM;
			}
			return TORSO;
		}
		return rnd.nextFloat() < 0.5 + 0.4 * side ? RLEG : LLEG;
	}

	/**
	 * A hit of {@code take} integrity landed on the worn suit (after the brace / i-frames, {@link HulkbusterShield}): the
	 * part it struck takes its share. Plays the break when a part goes.
	 */
	public static void onHit(ServerPlayer p, DamageSource source, float take) {
		if (take <= 0f || !wearing(p)) {
			return;
		}
		int part = route(p, source, p.getRandom());
		hit(p, part, take);
	}

	/** {@code take} integrity's worth of damage onto {@code part} of the worn suit (public for the gametests). */
	public static void hit(ServerPlayer p, int part, float take) {
		ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof IronManArmorItem a) || !SUIT_ID.equals(a.suitId())) {
			return;
		}
		float max = IronManEnergy.maxIntegrity(SUIT_ID);
		float[] d = raw(chest);
		boolean was = broken(d[part]);
		d[part] = Math.min(1f, d[part] + take / Math.max(1f, BREAK_SHARE * SHARE[part] * max));
		write(chest, d);
		if (!was && broken(d[part])) {
			onBreak(p, part);
		}
	}

	private static void onBreak(ServerPlayer p, int part) {
		ServerLevel level = p.serverLevel();
		Vec3 at = partPos(p, part);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, 0.3, 0.3, 0.3, 0.25);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 12, 0.25, 0.25, 0.25, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.55f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0f, 0.5f);
		IronManSounds.play(p, IronManSounds.POWER_FAIL, 0.7f, 1.2f);
		p.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_part.broke",
				Component.translatable("hud.projecthero.ironman.hb_part." + NAMES[part])).withStyle(ChatFormatting.RED), true);
	}

	/** A rough world position of {@code part} on a worn suit (server-side effects). */
	public static Vec3 partPos(Player p, int part) {
		double h = p.getBbHeight();
		double yaw = Math.toRadians(p.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return switch (part) {
			case HEAD -> p.position().add(0, h * 0.9, 0);
			case LARM -> p.position().add(right.scale(-0.7)).add(0, h * 0.6, 0);
			case RARM -> p.position().add(right.scale(0.7)).add(0, h * 0.6, 0);
			case LLEG -> p.position().add(right.scale(-0.3)).add(0, h * 0.2, 0);
			case RLEG -> p.position().add(right.scale(0.3)).add(0, h * 0.2, 0);
			default -> p.position().add(0, h * 0.62, 0);
		};
	}

	// ======================================================================== per tick (server)

	/** Every player tick: the broken-leg slow (added / removed) and the cracked torso's reactor leak. */
	public static void tick(ServerPlayer p) {
		float[] eff = of(p);
		int legs = brokenLegs(eff);
		AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			AttributeModifier cur = speed.getModifier(LEG_SLOW_ID);
			double want = -LEG_SLOW * legs;
			if (legs == 0) {
				if (cur != null) {
					speed.removeModifier(LEG_SLOW_ID);
				}
			} else if (cur == null || Math.abs(cur.amount() - want) > 1.0e-6) {
				speed.removeModifier(LEG_SLOW_ID);
				speed.addTransientModifier(new AttributeModifier(LEG_SLOW_ID, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (broken(eff[TORSO]) && p.tickCount % 10 == 0) {
			IronManEnergy.addEnergy(p, SUIT_ID, -TORSO_LEAK_PER_SECOND / 2f);
		}
	}

	/** Head broken: the Mega Beam's aim wanders (a slow wobble of up to ~6 degrees). */
	public static Vec3 beamWobble(Player p, Vec3 dir) {
		if (!broken(of(p)[HEAD])) {
			return dir;
		}
		float t = p.tickCount;
		double yaw = Math.sin(t * 0.31) * 0.07 + Math.sin(t * 0.83) * 0.035;
		double pitch = Math.cos(t * 0.27) * 0.05 + Math.sin(t * 0.67) * 0.03;
		Vec3 r = dir.yRot((float) yaw);
		return r.xRot((float) pitch).normalize();
	}

	// ======================================================================== repair

	/** Every damaged part (worst first) -- the Replacement Pod's work list. */
	public static int[] damagedOrder(float[] eff) {
		List<Integer> list = new ArrayList<>();
		for (int i = 0; i < COUNT; i++) {
			if (eff[i] > 0.02f) {
				list.add(i);
			}
		}
		list.sort((a, b) -> Float.compare(eff[b], eff[a]));
		int[] out = new int[list.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = list.get(i);
		}
		return out;
	}
}
