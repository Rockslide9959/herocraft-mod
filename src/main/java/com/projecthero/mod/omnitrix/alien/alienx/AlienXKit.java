package com.projecthero.mod.omnitrix.alien.alienx;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienKit;
import com.projecthero.mod.omnitrix.alien.AlienMove;
import com.projecthero.mod.omnitrix.alien.AlienStats;
import com.projecthero.mod.omnitrix.alien.Aliens;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2: Alien X, the Celestialsapien (Ben 10: Alien Force).
 *
 * <h2>v0.17.2 new system: the Celestial Council (docs/OMNITRIX_REVAMP_ALIENX.md)</h2>
 * User: "we need a new system for alien X ... he has to be a lot stronger and impervious to all forms of damage, it should be
 * difficult to use him aswell, also if you transform into alien X you watch battery stops depleting"; "Alien X can one shot
 * anything but the new system wont allow him to just go and hit things"; "when the player transforms into alien x i want his
 * body to remain in place, make alien x 2.5 blocks tall by the way, but the player gets transported to that dimension where he
 * talks to the two heads, make a good debating system where the player can do many many many things".
 * <ul>
 * <li>Transforming leaves his 2.5-block body standing, frozen and impervious ({@link AlienXBodyEntity}), and sends Ben to the
 * Celestial Council ({@link AlienXCouncil}): there he talks Serena and Bellicus into anything from a one-shot strike to
 * bringing the night ({@link AlienXActions}), and it happens at his body.</li>
 * <li>No key casts a power: R opens the council screen, V leaves the council (turns back, like N). Punches do nothing.</li>
 * <li>Impervious: no damage of any kind ({@link #allowDamage}; /kill and the void still work -- OmnitrixDamage lets them past
 * every kit), no knockback, no harmful effects, no fire, no drowning.</li>
 * <li>The Recalibrated Omnitrix's battery is held while he is Alien X (Omnitrix#HOLD_BATTERY, set in {@link #onTransform}).</li>
 * </ul>
 */
public final class AlienXKit extends AlienKit {
	public static final String ID = Aliens.ALIEN_X.id();

	public static final String ADDRESS = "address_council";
	public static final String LEAVE = "leave_council";
	public static final List<String> MOVE_IDS = List.of(ADDRESS, LEAVE);

	/** Synced timers: the council slot's origin (position), and which head speaks (and since when). */
	public static final String P_COUNCIL = "council", T_SPEAK_WHO = "speak_who", T_SPEAK_AT = "speak_at";

	private static final ResourceLocation STEADY = PowerToggles.id("omnitrix_alien_x_steady");

	// ---------------------------------------------------------------- sounds (vanilla samples, quiet, layered)
	public static final OmnitrixSfx.Cue CARRIED = OmnitrixSfx.cue("alien_x_carried")
			.layer(SoundEvents.BEACON_ACTIVATE, 0.25f, 1.6f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 1.8f, 1);
	public static final OmnitrixSfx.Cue DENIED = OmnitrixSfx.cue("alien_x_denied")
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.28f, 0.8f)
			.layer(SoundEvents.NOTE_BLOCK_BASS, 0.25f, 0.6f, 1);
	public static final OmnitrixSfx.Cue ERASE_SOUND = OmnitrixSfx.cue("alien_x_erase")
			.layer(SoundEvents.END_PORTAL_SPAWN, 0.14f, 1.8f)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 0.5f)
			.layer(SoundEvents.ENDERMAN_TELEPORT, 0.25f, 0.5f, 2);
	public static final OmnitrixSfx.Cue CREATE_SOUND = OmnitrixSfx.cue("alien_x_create")
			.layer(SoundEvents.AMETHYST_BLOCK_PLACE, 0.4f, 0.7f)
			.layer(SoundEvents.BEACON_POWER_SELECT, 0.25f, 0.8f)
			.layer(SoundEvents.AMETHYST_CLUSTER_PLACE, 0.3f, 1.2f, 2);
	public static final OmnitrixSfx.Cue BLINK_SOUND = OmnitrixSfx.cue("alien_x_blink")
			.layer(SoundEvents.ENDERMAN_TELEPORT, 0.3f, 0.8f)
			.layer(SoundEvents.CHORUS_FRUIT_TELEPORT, 0.22f, 1.2f)
			.layer(SoundEvents.AMETHYST_BLOCK_CHIME, 0.2f, 1.5f, 1);
	public static final OmnitrixSfx.Cue STOP_SOUND = OmnitrixSfx.cue("alien_x_time_stop")
			.layer(SoundEvents.BELL_RESONATE, 0.3f, 1.2f)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.3f, 0.5f)
			.layer(SoundEvents.CONDUIT_ACTIVATE, 0.22f, 0.7f, 2);
	public static final OmnitrixSfx.Cue CRUSH_SOUND = OmnitrixSfx.cue("alien_x_crush")
			.layer(SoundEvents.ANVIL_LAND, 0.2f, 0.5f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.22f, 0.6f, 1)
			.layer(SoundEvents.BEACON_DEACTIVATE, 0.2f, 0.6f);
	public static final OmnitrixSfx.Cue BANG_SOUND = OmnitrixSfx.cue("alien_x_big_bang")
			.layer(SoundEvents.END_PORTAL_SPAWN, 0.22f, 0.8f)
			.layer(SoundEvents.GENERIC_EXPLODE, 0.35f, 0.5f)
			.layer(SoundEvents.BEACON_ACTIVATE, 0.3f, 0.5f)
			.layer(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.18f, 0.7f, 3);
	public static final OmnitrixSfx.Cue AWAKEN = OmnitrixSfx.cue("alien_x_awaken")
			.layer(SoundEvents.CONDUIT_ACTIVATE, 0.3f, 0.6f)
			.layer(SoundEvents.BEACON_AMBIENT, 0.25f, 0.8f)
			.layer(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.2f, 0.5f, 4);

	private AlienXKit() {
	}

	public static void register() {
		Aliens.register(new AlienXKit());
		AlienXCouncil.initialize();
	}

	/** From {@code OmnitrixMod.clearSessionState} (the ServerStateReset hook). */
	public static void clearSessionState() {
		AlienXCouncil.clearSessionState();
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AlienStats stats() {
		// v0.17.2 (user: "make alien x 2.5 blocks tall by the way")
		return AlienStats.builder().health(60).speed(1.0f).scale(AlienXBodyEntity.SCALE).armor(14, 4).knockbackResist(1.0f).attack(0).step(0.5f).build();
	}

	@Override
	protected List<AlienMove> declareMoves() {
		return List.of(AlienMove.tap(ADDRESS, AbilitySlot.SLOT_1, 0), AlienMove.tap(LEAVE, AbilitySlot.SLOT_5, 0));
	}

	// ---------------------------------------------------------------- client-safe reads

	public static long enc(double v) {
		return Math.round(v * 64.0) + (1L << 40);
	}

	public static double dec(long v) {
		return (v - (1L << 40)) / 64.0;
	}

	/** A position stored in the synced timers ({@code <name>_x / _y / _z}), or null. */
	@Nullable
	public static Vec3 pos(Player p, String name) {
		OmnitrixState s = Omnitrix.state(p);
		long x = s.readyAt(ID + "." + name + "_x");
		if (x <= 0) {
			return null;
		}
		return new Vec3(dec(x), dec(s.readyAt(ID + "." + name + "_y")), dec(s.readyAt(ID + "." + name + "_z")));
	}

	/** Which head speaks (AlienXCouncil.WHO_*) and for how many ticks it has. */
	public static int speaker(Player p) {
		return (int) Omnitrix.state(p).readyAt(ID + "." + T_SPEAK_WHO);
	}

	public static long speakAt(Player p) {
		return Omnitrix.state(p).readyAt(ID + "." + T_SPEAK_AT);
	}

	// ---------------------------------------------------------------- lifecycle

	@Override
	public void onTransform(ServerPlayer p) {
		long now = p.level().getGameTime();
		// v0.17.2 (user: "if you transform into alien X you watch battery stops depleting and you can basically stay as him for as
		// long as you like until you detransform"): the battery is held where it was (Omnitrix reads and clears HOLD_BATTERY)
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(Omnitrix.HOLD_BATTERY, (long) Math.max(1, Omnitrix.batteryTicks(Omnitrix.state(p), now)));
		Omnitrix.save(p, n);
		PowerToggles.modifier(p, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, STEADY, 1.0, AttributeModifier.Operation.ADD_VALUE);
		OmnitrixSfx.play(level(p), p.position(), AWAKEN, 1.0f, 1.0f);
		AlienXCouncil.enter(p);
	}

	@Override
	public void onRevert(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, STEADY);
		AlienXCouncil.leave(p);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.keySet().removeIf(k -> k.startsWith(ID + ".") && !MOVE_IDS.contains(k.substring(ID.length() + 1)));
		n.kitFlags = 0;
		Omnitrix.save(p, n);
	}

	@Override
	public void tick(ServerPlayer p, OmnitrixState s, long now) {
		impervious(p);
	}

	/** v0.17.2 (user: "impervious to all forms of damage"): no fire, no breathlessness, no harmful effect stays on him. */
	private static void impervious(ServerPlayer p) {
		if (p.isOnFire()) {
			p.clearFire();
		}
		if (p.getAirSupply() < p.getMaxAirSupply()) {
			p.setAirSupply(p.getMaxAirSupply());
		}
		p.setTicksFrozen(0);
		p.resetFallDistance();
		var harmful = p.getActiveEffects().stream().filter(e -> e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
				.map(MobEffectInstance::getEffect).toList();
		harmful.forEach(p::removeEffect);
	}

	// ---------------------------------------------------------------- damage

	/**
	 * v0.17.2 (user: "impervious to all forms of damage"): nothing gets through -- mobs, players, fire, lava, falls, drowning,
	 * explosions, magic, wither. (/kill, the void and anything that bypasses invulnerability never reach a kit: OmnitrixDamage
	 * lets them through first, so nobody can be stuck as him.)
	 */
	@Override
	public boolean allowDamage(ServerPlayer p, DamageSource source, float amount) {
		return false;
	}

	// ---------------------------------------------------------------- keys

	@Override
	public void press(ServerPlayer p, AbilitySlot slot, boolean shift) {
		if (slot == AbilitySlot.SLOT_5 && !shift) {
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			return;
		}
		if (slot == AbilitySlot.SLOT_1 && !shift) {
			if (AlienXCouncil.inSession(p)) {
				AlienXCouncil.open(p);
			}
			return;
		}
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.no_keys").withStyle(ChatFormatting.GRAY), true);
	}

	/** v0.17.2 (user: "the new system wont allow him to just go and hit things"): a bare punch does nothing. */
	@Override
	public boolean onAttack(ServerPlayer p, Entity target) {
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.no_punch").withStyle(ChatFormatting.GRAY), true);
		return true;
	}
}
