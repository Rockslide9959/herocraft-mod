package com.projecthero.mod.worthy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * v0.17.7 Thor x Super Soldier: one player's side of the lent-weapon kits, in one attachment ({@code projecthero:worthy_state},
 * {@link WorthyAttachments#STATE}): persistent, synced to every client (the owner's HUD reads the cooldowns and the view lists, every
 * viewer reads the animation, the thrown weapon's flight and the flying flag). Mutable, but only ever changed through
 * {@link #edit} -- a copy, the change, then {@code setAttached} -- so every change is synced.
 * <p>
 * The two view lists ({@link #lent} on a Thor, {@link #borrowed} on a Super Soldier) are NOT the authority: {@link WorthyLoans} is.
 * They are rebuilt from it ({@code WorthyLending.refreshView}) on every lend / return, at login and every second.
 */
public final class WorthyState {
	// ---- animation ids (client: WorthyPose). 0 = none.
	public static final int ANIM_NONE = 0;
	public static final int ANIM_HAMMER_COMBO = 1;
	public static final int ANIM_UPPERCUT = 2;
	public static final int ANIM_CALL_LIGHTNING = 3;
	public static final int ANIM_THUNDERCLAP = 4;
	public static final int ANIM_SLAM_RISE = 5;
	public static final int ANIM_SLAM_HIT = 6;
	public static final int ANIM_WORTHY_STORM = 7;
	public static final int ANIM_DASH = 8;
	public static final int ANIM_LAUNCH = 9;
	public static final int ANIM_GUARD = 10;
	public static final int ANIM_RALLY = 11;
	public static final int ANIM_THROW = 12;
	public static final int ANIM_AXE_CLEAVES = 13;
	public static final int ANIM_LIGHTNING_CLEAVE = 14;
	public static final int ANIM_EXEC_CHOP = 15;
	public static final int ANIM_VERDICT = 16;
	public static final int ANIM_SHIELD_STRIKE = 17;
	public static final int ANIM_BASH_LAUNCH = 18;
	public static final int ANIM_SHIELD_THROW = 19;
	public static final int ANIM_TWIN_THROW = 20;
	public static final int ANIM_CLAP = 21;
	public static final int ANIM_LIGHTNING_GUARD = 22;
	/** Retired v0.17.7 (the take-off whirl is gone; never set any more, kept so the id is not reused). */
	public static final int ANIM_TAKEOFF = 23;

	// ---- timed move ids (server: WorthyMoves.tickSequence). 0 = none.
	public static final int SEQ_NONE = 0;
	public static final int SEQ_HAMMER_COMBO = 1;
	public static final int SEQ_SLAM = 2;
	public static final int SEQ_WORTHY_STORM = 3;
	public static final int SEQ_AXE_CLEAVES = 4;
	public static final int SEQ_EXEC_CHOP = 5;
	public static final int SEQ_CRASH = 6;
	public static final int SEQ_VERDICT = 7;
	public static final int SEQ_SHIELD_STRIKE = 8;
	public static final int SEQ_BASH_LAUNCH = 9;
	public static final int SEQ_SHIELD_SLAM = 10;
	public static final int SEQ_CLAP = 11;
	public static final int SEQ_LAUNCH = 12;

	/** A weapon this Thor has lent out (the screen's "Lent to ..." line). */
	public record Lent(int weapon, String borrower) {
	}

	/** A weapon lent to this Super Soldier (which stacks count as lent, the HUD's owner name). */
	public record Borrowed(UUID hammerId, int weapon, String owner) {
	}

	/** {@code abilityId} -> game time it is ready again. */
	public final Map<String, Long> readyAt = new HashMap<>();
	/** No new move before this game time. */
	public long busyUntil;
	/** The move animation (ANIM_*), when it started, and whether its lead arm is the OFF hand's (the weapon held left). */
	public int anim;
	public long animStart;
	public boolean animOff;

	// ---- the thrown weapon (drawn by the client: out from {@link #from} to {@link #to}, hangs, then home to the live hand)
	/** -1 = nothing thrown, else the ThorWeapon ordinal. */
	public int throwWeapon = -1;
	public long throwStart;
	public int throwOut;
	public int throwHold;
	public int throwBack;
	public boolean throwOff;
	public double fromX, fromY, fromZ, toX, toY, toZ;
	/** Server: how many more enemies the throw may pass through, and who it already hit (entity ids). */
	public int throwPierce;
	public float throwDamage;
	public final List<Integer> throwHit = new ArrayList<>();

	// ---- the timed move running (server)
	public int seq;
	public long seqStart;
	public int seqTarget = -1;
	public double seqX, seqY, seqZ;
	public int seqWeapon;

	// ---- flight and timers
	public boolean flying;
	public long flyStart;
	public long boostUntil;
	public long flyToggleReady;
	public long noFallUntil;
	public long guardUntil;
	public boolean guardShield;
	public long chargedUntil;
	/** v0.17.7: the lent weapon (HAMMER_ID) his R called last -- with two lent to him and neither in hand, R alternates. Null = none. */
	public UUID lastCall;

	// ---- views of WorthyLoans
	public final List<Lent> lent = new ArrayList<>();
	public final List<Borrowed> borrowed = new ArrayList<>();

	public WorthyState() {
	}

	public static WorthyState of(Player player) {
		WorthyState s = player.getAttachedOrElse(WorthyAttachments.STATE, null);
		return s == null ? EMPTY : s;
	}

	/** Shared read-only default for a player who never touched the kit. Never mutate it. */
	public static final WorthyState EMPTY = new WorthyState();

	/** Copy, change, store (and so sync). Server side only. */
	public static void edit(Player player, java.util.function.Consumer<WorthyState> change) {
		WorthyState n = of(player).copy();
		change.accept(n);
		player.setAttached(WorthyAttachments.STATE, n);
	}

	public boolean borrows(UUID hammerId) {
		if (hammerId == null) {
			return false;
		}
		for (Borrowed b : borrowed) {
			if (b.hammerId().equals(hammerId)) {
				return true;
			}
		}
		return false;
	}

	/** The name of the Super Soldier holding this Thor's {@code weapon} (ordinal), or null if it is not lent. */
	public String lentTo(int weapon) {
		for (Lent l : lent) {
			if (l.weapon() == weapon) {
				return l.borrower();
			}
		}
		return null;
	}

	public WorthyState copy() {
		return fromTag(toTag());
	}

	// ------------------------------------------------------------------ (de)serialisation: one compound, so new fields never break a save

	public CompoundTag toTag() {
		CompoundTag t = new CompoundTag();
		CompoundTag cd = new CompoundTag();
		readyAt.forEach(cd::putLong);
		t.put("ReadyAt", cd);
		t.putLong("Busy", busyUntil);
		t.putInt("Anim", anim);
		t.putLong("AnimStart", animStart);
		t.putBoolean("AnimOff", animOff);
		t.putInt("ThrowWeapon", throwWeapon);
		t.putLong("ThrowStart", throwStart);
		t.putInt("ThrowOut", throwOut);
		t.putInt("ThrowHold", throwHold);
		t.putInt("ThrowBack", throwBack);
		t.putBoolean("ThrowOff", throwOff);
		t.putDouble("FromX", fromX);
		t.putDouble("FromY", fromY);
		t.putDouble("FromZ", fromZ);
		t.putDouble("ToX", toX);
		t.putDouble("ToY", toY);
		t.putDouble("ToZ", toZ);
		t.putInt("ThrowPierce", throwPierce);
		t.putFloat("ThrowDamage", throwDamage);
		t.putIntArray("ThrowHit", throwHit.stream().mapToInt(Integer::intValue).toArray());
		t.putInt("Seq", seq);
		t.putLong("SeqStart", seqStart);
		t.putInt("SeqTarget", seqTarget);
		t.putDouble("SeqX", seqX);
		t.putDouble("SeqY", seqY);
		t.putDouble("SeqZ", seqZ);
		t.putInt("SeqWeapon", seqWeapon);
		t.putBoolean("Flying", flying);
		t.putLong("FlyStart", flyStart);
		t.putLong("BoostUntil", boostUntil);
		t.putLong("FlyToggleReady", flyToggleReady);
		t.putLong("NoFallUntil", noFallUntil);
		t.putLong("GuardUntil", guardUntil);
		t.putBoolean("GuardShield", guardShield);
		t.putLong("ChargedUntil", chargedUntil);
		if (lastCall != null) {
			t.putUUID("LastCall", lastCall);
		}
		ListTag lentTag = new ListTag();
		for (Lent l : lent) {
			CompoundTag e = new CompoundTag();
			e.putInt("Weapon", l.weapon());
			e.putString("Name", l.borrower());
			lentTag.add(e);
		}
		t.put("Lent", lentTag);
		ListTag borrowedTag = new ListTag();
		for (Borrowed b : borrowed) {
			CompoundTag e = new CompoundTag();
			e.putUUID("Hammer", b.hammerId());
			e.putInt("Weapon", b.weapon());
			e.putString("Name", b.owner());
			borrowedTag.add(e);
		}
		t.put("Borrowed", borrowedTag);
		return t;
	}

	public static WorthyState fromTag(CompoundTag t) {
		WorthyState s = new WorthyState();
		CompoundTag cd = t.getCompound("ReadyAt");
		for (String k : cd.getAllKeys()) {
			s.readyAt.put(k, cd.getLong(k));
		}
		s.busyUntil = t.getLong("Busy");
		s.anim = t.getInt("Anim");
		s.animStart = t.getLong("AnimStart");
		s.animOff = t.getBoolean("AnimOff");
		s.throwWeapon = t.contains("ThrowWeapon") ? t.getInt("ThrowWeapon") : -1;
		s.throwStart = t.getLong("ThrowStart");
		s.throwOut = t.getInt("ThrowOut");
		s.throwHold = t.getInt("ThrowHold");
		s.throwBack = t.getInt("ThrowBack");
		s.throwOff = t.getBoolean("ThrowOff");
		s.fromX = t.getDouble("FromX");
		s.fromY = t.getDouble("FromY");
		s.fromZ = t.getDouble("FromZ");
		s.toX = t.getDouble("ToX");
		s.toY = t.getDouble("ToY");
		s.toZ = t.getDouble("ToZ");
		s.throwPierce = t.getInt("ThrowPierce");
		s.throwDamage = t.getFloat("ThrowDamage");
		for (int id : t.getIntArray("ThrowHit")) {
			s.throwHit.add(id);
		}
		s.seq = t.getInt("Seq");
		s.seqStart = t.getLong("SeqStart");
		s.seqTarget = t.contains("SeqTarget") ? t.getInt("SeqTarget") : -1;
		s.seqX = t.getDouble("SeqX");
		s.seqY = t.getDouble("SeqY");
		s.seqZ = t.getDouble("SeqZ");
		s.seqWeapon = t.getInt("SeqWeapon");
		s.flying = t.getBoolean("Flying");
		s.flyStart = t.getLong("FlyStart");
		s.boostUntil = t.getLong("BoostUntil");
		s.flyToggleReady = t.getLong("FlyToggleReady");
		s.noFallUntil = t.getLong("NoFallUntil");
		s.guardUntil = t.getLong("GuardUntil");
		s.guardShield = t.getBoolean("GuardShield");
		s.chargedUntil = t.getLong("ChargedUntil");
		s.lastCall = t.hasUUID("LastCall") ? t.getUUID("LastCall") : null;
		ListTag lentTag = t.getList("Lent", Tag.TAG_COMPOUND);
		for (int i = 0; i < lentTag.size(); i++) {
			CompoundTag e = lentTag.getCompound(i);
			s.lent.add(new Lent(e.getInt("Weapon"), e.getString("Name")));
		}
		ListTag borrowedTag = t.getList("Borrowed", Tag.TAG_COMPOUND);
		for (int i = 0; i < borrowedTag.size(); i++) {
			CompoundTag e = borrowedTag.getCompound(i);
			if (e.hasUUID("Hammer")) {
				s.borrowed.add(new Borrowed(e.getUUID("Hammer"), e.getInt("Weapon"), e.getString("Name")));
			}
		}
		return s;
	}

	public static final Codec<WorthyState> CODEC = CompoundTag.CODEC.xmap(WorthyState::fromTag, WorthyState::toTag);
}
