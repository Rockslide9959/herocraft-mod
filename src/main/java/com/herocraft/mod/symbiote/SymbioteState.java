package com.herocraft.mod.symbiote;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The Symbiote's whole persistent state, in one small object under one namespaced attachment
 * ({@code herocraft:symbiote_state}). Shared by both host variants (v0.9.14) -- which variant a
 * bonded player currently is is derived live by {@link SymbioteHostType#of}, not stored here.
 *
 * <ul>
 *   <li>{@link #hasSymbiote} -- has this player ever bonded with a Symbiote. The unlock. Survives
 *       death and relog ({@code copyOnDeath} on the attachment). Never restored once purged --
 *       purged Hero-Tier/experimental powers stay gone even if the Symbiote is later removed.</li>
 *   <li>{@link #active} -- is the suit on, or in the middle of coming on / going off (mirrors
 *       {@code MaxSteelState#transformed}'s "on or coming on" semantics). Torn down on death / relog /
 *       dimension change by {@link Symbiote}; the persisted value is only ever a transient
 *       convenience.</li>
 *   <li>{@link #toggleReadyAt} -- absolute game-time the H toggle is available again (a ~0.75 s
 *       anti-spam gate, independent of the transform animation itself).</li>
 *   <li>{@link #transformDir} / {@link #transformStartTick} / {@link #transformDurationTicks} -- the
 *       suit-up / suit-down animation clock, read by {@link SymbioteTransform}. Exactly the
 *       {@code MaxSteelState} pattern: one write per transition, not one per tick.</li>
 *   <li>{@link #stowedArmor} -- whatever real armour the player was wearing when the Symbiote engaged,
 *       so it can be handed back exactly when it retracts. Index order is head / chest / legs / boots
 *       (see {@link SymbioteSuit#SLOTS}); empty stacks mean nothing was worn in that slot.</li>
 *   <li>{@link #abilityCooldowns} -- absolute game-time each of the six Normal-host ability slots
 *       (index = {@code AbilitySlot.ordinal()}) is next ready. Fixed size 6, read/written by
 *       {@link SymbioteAbilityManager}.</li>
 *   <li>{@link #frenzyEndTick} / {@link #frenzyDebuffEndTick} -- Frenzy's active window and the brief
 *       post-Frenzy defence dip that follows it.</li>
 *   <li>{@link #shieldHeld} / {@link #shieldGuard} -- Symbiote Shield is a HOLD ability now (v0.9.19):
 *       {@code shieldHeld} is true only while the player still has the key down and the guard bar
 *       hasn't run dry, {@code shieldGuard} is that bar's current value, 0..{@link #SHIELD_GUARD_MAX}
 *       (one point per tick of holding, draining while held and refilling twice as fast while not --
 *       see {@link SymbioteAbilityManager}).</li>
 *   <li>{@link #tendrilGrabTargetId} / {@link #tendrilGrabHeld} / {@link #tendrilGrabStartTick} --
 *       Tendril Grab is now a grab-and-hold-then-throw move (v0.9.19): the entity id currently
 *       suspended in front of the player (-1 for none), whether the key is still held, and when the
 *       grab started (for the auto-throw safety cap). See {@link SymbioteAbilityManager}.</li>
 * </ul>
 */
public final class SymbioteState {
	public static final int DIR_IDLE = 0;
	public static final int DIR_UP = 1;
	public static final int DIR_DOWN = 2;

	/** Full Symbiote Shield hold time, in ticks -- 9 seconds. */
	public static final float SHIELD_GUARD_MAX = 180.0f;

	public boolean hasSymbiote;
	public boolean active;
	public long toggleReadyAt;
	public int transformDir;
	public long transformStartTick;
	public int transformDurationTicks;
	public ItemContainerContents stowedArmor;
	public List<Long> abilityCooldowns;
	public long frenzyEndTick;
	public long frenzyDebuffEndTick;
	public boolean shieldHeld;
	public float shieldGuard;
	public int tendrilGrabTargetId;
	public boolean tendrilGrabHeld;
	public long tendrilGrabStartTick;

	public SymbioteState() {
		this(false, false, 0L, DIR_IDLE, 0L, 0, ItemContainerContents.EMPTY,
				defaultCooldowns(), 0L, 0L, false, SHIELD_GUARD_MAX, -1, false, 0L);
	}

	public SymbioteState(boolean hasSymbiote, boolean active, long toggleReadyAt, int transformDir,
			long transformStartTick, int transformDurationTicks, ItemContainerContents stowedArmor,
			List<Long> abilityCooldowns, long frenzyEndTick, long frenzyDebuffEndTick, boolean shieldHeld,
			float shieldGuard, int tendrilGrabTargetId, boolean tendrilGrabHeld, long tendrilGrabStartTick) {
		this.hasSymbiote = hasSymbiote;
		this.active = active;
		this.toggleReadyAt = toggleReadyAt;
		this.transformDir = transformDir;
		this.transformStartTick = transformStartTick;
		this.transformDurationTicks = transformDurationTicks;
		this.stowedArmor = stowedArmor == null ? ItemContainerContents.EMPTY : stowedArmor;
		this.abilityCooldowns = normalizeCooldowns(abilityCooldowns);
		this.frenzyEndTick = frenzyEndTick;
		this.frenzyDebuffEndTick = frenzyDebuffEndTick;
		this.shieldHeld = shieldHeld;
		this.shieldGuard = shieldGuard;
		this.tendrilGrabTargetId = tendrilGrabTargetId;
		this.tendrilGrabHeld = tendrilGrabHeld;
		this.tendrilGrabStartTick = tendrilGrabStartTick;
	}

	private static List<Long> defaultCooldowns() {
		return new java.util.ArrayList<>(java.util.Collections.nCopies(6, 0L));
	}

	private static List<Long> normalizeCooldowns(List<Long> in) {
		java.util.ArrayList<Long> out = new java.util.ArrayList<>(defaultCooldowns());
		if (in != null) {
			for (int i = 0; i < Math.min(6, in.size()); i++) {
				out.set(i, in.get(i) == null ? 0L : in.get(i));
			}
		}
		return out;
	}

	public SymbioteState copy() {
		return new SymbioteState(hasSymbiote, active, toggleReadyAt, transformDir, transformStartTick,
				transformDurationTicks, stowedArmor, new java.util.ArrayList<>(abilityCooldowns),
				frenzyEndTick, frenzyDebuffEndTick, shieldHeld, shieldGuard, tendrilGrabTargetId,
				tendrilGrabHeld, tendrilGrabStartTick);
	}

	public static final Codec<SymbioteState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("has_symbiote", false).forGetter(s -> s.hasSymbiote),
			Codec.BOOL.optionalFieldOf("active", false).forGetter(s -> s.active),
			Codec.LONG.optionalFieldOf("toggle_ready_at", 0L).forGetter(s -> s.toggleReadyAt),
			Codec.INT.optionalFieldOf("transform_dir", DIR_IDLE).forGetter(s -> s.transformDir),
			Codec.LONG.optionalFieldOf("transform_start_tick", 0L).forGetter(s -> s.transformStartTick),
			Codec.INT.optionalFieldOf("transform_duration_ticks", 0).forGetter(s -> s.transformDurationTicks),
			ItemContainerContents.CODEC.optionalFieldOf("stowed_armor", ItemContainerContents.EMPTY)
					.forGetter(s -> s.stowedArmor),
			Codec.LONG.listOf().optionalFieldOf("ability_cooldowns", defaultCooldowns()).forGetter(s -> s.abilityCooldowns),
			Codec.LONG.optionalFieldOf("frenzy_end_tick", 0L).forGetter(s -> s.frenzyEndTick),
			Codec.LONG.optionalFieldOf("frenzy_debuff_end_tick", 0L).forGetter(s -> s.frenzyDebuffEndTick),
			Codec.BOOL.optionalFieldOf("shield_held", false).forGetter(s -> s.shieldHeld),
			Codec.FLOAT.optionalFieldOf("shield_guard", SHIELD_GUARD_MAX).forGetter(s -> s.shieldGuard),
			Codec.INT.optionalFieldOf("tendril_grab_target_id", -1).forGetter(s -> s.tendrilGrabTargetId),
			Codec.BOOL.optionalFieldOf("tendril_grab_held", false).forGetter(s -> s.tendrilGrabHeld),
			Codec.LONG.optionalFieldOf("tendril_grab_start_tick", 0L).forGetter(s -> s.tendrilGrabStartTick)
	).apply(instance, SymbioteState::new));
}
