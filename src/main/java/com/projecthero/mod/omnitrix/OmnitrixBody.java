package com.projecthero.mod.omnitrix;

import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.alien.AlienStats;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * v0.17.1: an alien's body as transient attribute modifiers ({@code projecthero:omnitrix_*}), and the health conversion. The
 * current health keeps its FRACTION across the change both ways (a human at 15/20 becomes a Four Arms at 30/40; a Four Arms
 * down to 10/40 turns back at 5/20) -- "health converts sensibly". Transient, so a save never keeps an alien body: the
 * framework turns the wearer back on logout.
 */
public final class OmnitrixBody {
	private static final ResourceLocation HEALTH = PowerToggles.id("omnitrix_health");
	private static final ResourceLocation SPEED = PowerToggles.id("omnitrix_speed");
	private static final ResourceLocation SCALE = PowerToggles.id("omnitrix_scale");
	private static final ResourceLocation ARMOR = PowerToggles.id("omnitrix_armor");
	private static final ResourceLocation TOUGH = PowerToggles.id("omnitrix_toughness");
	private static final ResourceLocation KNOCKBACK = PowerToggles.id("omnitrix_knockback");
	private static final ResourceLocation ATTACK = PowerToggles.id("omnitrix_attack");
	private static final ResourceLocation STEP = PowerToggles.id("omnitrix_step");
	private static final ResourceLocation JUMP = PowerToggles.id("omnitrix_jump");
	private static final ResourceLocation FALL = PowerToggles.id("omnitrix_safe_fall");
	private static final ResourceLocation GRAVITY = PowerToggles.id("omnitrix_gravity");
	private static final ResourceLocation REACH_BLOCK = PowerToggles.id("omnitrix_reach_block");
	private static final ResourceLocation REACH_ENTITY = PowerToggles.id("omnitrix_reach_entity");
	/** A zero modifier that only marks "the alien body is on" (an alien may change no other attribute). */
	private static final ResourceLocation MARK = PowerToggles.id("omnitrix_body");

	private OmnitrixBody() {
	}

	/** Puts the alien's body on, then sets health to {@code healthFraction} of the new maximum. */
	public static void apply(ServerPlayer p, AlienStats s, float healthFraction) {
		add(p, Attributes.MAX_HEALTH, HEALTH, s.maxHealth() - 20.0, AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.MOVEMENT_SPEED, SPEED, s.speed() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		add(p, Attributes.SCALE, SCALE, s.scale() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		add(p, Attributes.ARMOR, ARMOR, s.armor(), AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.ARMOR_TOUGHNESS, TOUGH, s.toughness(), AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, s.knockbackResist(), AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.ATTACK_DAMAGE, ATTACK, s.attack(), AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.STEP_HEIGHT, STEP, s.step(), AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.JUMP_STRENGTH, JUMP, s.jump(), AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.SAFE_FALL_DISTANCE, FALL, s.safeFall(), AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.GRAVITY, GRAVITY, s.gravity() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		add(p, Attributes.BLOCK_INTERACTION_RANGE, REACH_BLOCK, s.reach(), AttributeModifier.Operation.ADD_VALUE);
		add(p, Attributes.ENTITY_INTERACTION_RANGE, REACH_ENTITY, s.reach(), AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.LUCK, MARK, 0.0, AttributeModifier.Operation.ADD_VALUE);
		p.setHealth(Math.max(1f, clamp01(healthFraction) * p.getMaxHealth()));
	}

	/** Takes the alien's body off; health keeps its fraction ({@code keepHealth} false: leave it alone, e.g. on death). */
	public static void remove(ServerPlayer p, boolean keepHealth) {
		float frac = p.getMaxHealth() > 0f ? p.getHealth() / p.getMaxHealth() : 1f;
		for (var e : new Object[][] { { Attributes.MAX_HEALTH, HEALTH }, { Attributes.MOVEMENT_SPEED, SPEED }, { Attributes.SCALE, SCALE },
				{ Attributes.ARMOR, ARMOR }, { Attributes.ARMOR_TOUGHNESS, TOUGH }, { Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK },
				{ Attributes.ATTACK_DAMAGE, ATTACK }, { Attributes.STEP_HEIGHT, STEP }, { Attributes.JUMP_STRENGTH, JUMP },
				{ Attributes.SAFE_FALL_DISTANCE, FALL }, { Attributes.GRAVITY, GRAVITY }, { Attributes.BLOCK_INTERACTION_RANGE, REACH_BLOCK },
				{ Attributes.ENTITY_INTERACTION_RANGE, REACH_ENTITY }, { Attributes.LUCK, MARK } }) {
			@SuppressWarnings("unchecked")
			Holder<Attribute> attr = (Holder<Attribute>) e[0];
			PowerToggles.clearModifier(p, attr, (ResourceLocation) e[1]);
		}
		if (keepHealth && p.isAlive()) {
			p.setHealth(Math.max(1f, clamp01(frac) * p.getMaxHealth()));
		} else if (p.getHealth() > p.getMaxHealth()) {
			p.setHealth(p.getMaxHealth());
		}
	}

	/** Whether the alien body's modifiers are on (a relog drops transient modifiers). */
	public static boolean applied(ServerPlayer p) {
		var inst = p.getAttribute(Attributes.LUCK);
		return inst != null && inst.getModifier(MARK) != null;
	}

	private static void add(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
		if (Math.abs(amount) < 1.0e-6) {
			PowerToggles.clearModifier(p, attr, id);
			return;
		}
		PowerToggles.modifier(p, attr, id, amount, op);
	}

	private static float clamp01(float v) {
		return Float.isNaN(v) ? 1f : Math.max(0f, Math.min(1f, v));
	}
}
