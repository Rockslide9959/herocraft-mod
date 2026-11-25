package com.projecthero.mod.shazam.data;

import java.util.HashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;

/**
 * v0.17.3 Shazam: the whole power for one player in one attachment ({@code projecthero:shazam_state}): persistent,
 * {@code copyOnDeath} and synced to every client (the suit layer, the cape, the flight, the HUD and Wisdom of Solomon read it).
 * Only {@code Shazam} changes it, through a {@link #copy()} saved at once (the Lightning bar ticks in place and is re-sent every
 * few ticks). All times are game times (0 = never / none). Saved as a plain compound so fields can be added freely.
 *
 * <p>It is also kept by players who are <i>not</i> the champion: a Shazam Family member's {@link #familyUntil}, the Wizard's
 * five-day {@link #wizardBanUntil} and the last innocent kill ({@link #innocentKillAt}) the Wizard judges.
 */
public final class ShazamState {
	/** The champion (granted by the Wizard / the command). */
	public boolean hasPower;
	/** Transformed (champion or family member). */
	public boolean transformed;
	/** The Lightning bar, 0..100. */
	public float lightning;
	/** The last word was spoken at this time (the 4 s gap). */
	public long lastWord;
	public boolean flying;
	/** Move id -> game time it is ready again. */
	public Map<String, Long> abilityReadyAt = new HashMap<>();

	/** Shazam Family member until this time (0 = not one). */
	public long familyUntil;
	/** The champion who shared the power with him (UUID string, "" = none). */
	public String familyChampion = "";
	/** The champion's family: member UUID string -> until. */
	public Map<String, Long> family = new HashMap<>();

	/** Courage of Achilles until this time (v0.17.7; was Achilles Stand). */
	public long standUntil;
	/** Wisdom of Solomon until this time. */
	public long wisdomUntil;
	/** Zeus Storm until this time. */
	public long stormUntil;
	/** The ultimate ("SHAZAM!" Thunderstrike since v0.17.7; was Eternity Strike) began at this time (0 = not running). */
	public long eternityStart;
	/** Muffled (Black Adam, later): no word until this time. */
	public long muffledUntil;

	// v0.17.7 kit (synced so every viewer plays the poses and the held effects)
	/** The body clip playing ({@code ShazamKit.ANIM_*}, 0 = none) and when it began. */
	public int anim;
	public long animStart;
	/** The hold move running ({@code ShazamKit.HOLD_*}, 0 = none) and when its key went down. */
	public int hold;
	public long holdStart;
	/** Thunder-charged (after the ultimate: every hit chains lightning) until this time. */
	public long thunderUntil;
	/** Mercury Burst (X while flying) until this time. */
	public long boostUntil;

	/** The Wizard won't come back to him until this time. */
	public long wizardBanUntil;
	/** The last time he killed a villager or an iron golem. */
	public long innocentKillAt;

	public ShazamState() {
	}

	public ShazamState copy() {
		ShazamState n = new ShazamState();
		n.hasPower = hasPower;
		n.transformed = transformed;
		n.lightning = lightning;
		n.lastWord = lastWord;
		n.flying = flying;
		n.abilityReadyAt = new HashMap<>(abilityReadyAt);
		n.familyUntil = familyUntil;
		n.familyChampion = familyChampion;
		n.family = new HashMap<>(family);
		n.standUntil = standUntil;
		n.wisdomUntil = wisdomUntil;
		n.stormUntil = stormUntil;
		n.eternityStart = eternityStart;
		n.muffledUntil = muffledUntil;
		n.anim = anim;
		n.animStart = animStart;
		n.hold = hold;
		n.holdStart = holdStart;
		n.thunderUntil = thunderUntil;
		n.boostUntil = boostUntil;
		n.wizardBanUntil = wizardBanUntil;
		n.innocentKillAt = innocentKillAt;
		return n;
	}

	/** A family member right now (not the champion himself). */
	public boolean family(long now) {
		return !hasPower && familyUntil > now;
	}

	/** May say the word: the champion, or a family member whose 10 minutes aren't up. */
	public boolean canSpeak(long now) {
		return hasPower || family(now);
	}

	/** How many family members are still family at {@code now}. */
	public int familySize(long now) {
		int n = 0;
		for (long until : family.values()) {
			if (until > now) {
				n++;
			}
		}
		return n;
	}

	public CompoundTag save() {
		CompoundTag t = new CompoundTag();
		t.putBoolean("has_power", hasPower);
		t.putBoolean("transformed", transformed);
		t.putFloat("lightning", lightning);
		t.putLong("last_word", lastWord);
		t.putBoolean("flying", flying);
		CompoundTag cds = new CompoundTag();
		abilityReadyAt.forEach(cds::putLong);
		t.put("ready_at", cds);
		t.putLong("family_until", familyUntil);
		t.putString("family_champion", familyChampion);
		CompoundTag fam = new CompoundTag();
		family.forEach(fam::putLong);
		t.put("family", fam);
		t.putLong("stand_until", standUntil);
		t.putLong("wisdom_until", wisdomUntil);
		t.putLong("storm_until", stormUntil);
		t.putLong("eternity_start", eternityStart);
		t.putLong("muffled_until", muffledUntil);
		t.putInt("anim", anim);
		t.putLong("anim_start", animStart);
		t.putInt("hold", hold);
		t.putLong("hold_start", holdStart);
		t.putLong("thunder_until", thunderUntil);
		t.putLong("boost_until", boostUntil);
		t.putLong("wizard_ban_until", wizardBanUntil);
		t.putLong("innocent_kill_at", innocentKillAt);
		return t;
	}

	public static ShazamState load(CompoundTag t) {
		ShazamState s = new ShazamState();
		s.hasPower = t.getBoolean("has_power");
		s.transformed = t.getBoolean("transformed");
		s.lightning = t.getFloat("lightning");
		s.lastWord = t.getLong("last_word");
		s.flying = t.getBoolean("flying");
		CompoundTag cds = t.getCompound("ready_at");
		for (String k : cds.getAllKeys()) {
			s.abilityReadyAt.put(k, cds.getLong(k));
		}
		s.familyUntil = t.getLong("family_until");
		s.familyChampion = t.getString("family_champion");
		CompoundTag fam = t.getCompound("family");
		for (String k : fam.getAllKeys()) {
			s.family.put(k, fam.getLong(k));
		}
		s.standUntil = t.getLong("stand_until");
		s.wisdomUntil = t.getLong("wisdom_until");
		s.stormUntil = t.getLong("storm_until");
		s.eternityStart = t.getLong("eternity_start");
		s.muffledUntil = t.getLong("muffled_until");
		s.anim = t.getInt("anim");
		s.animStart = t.getLong("anim_start");
		s.hold = t.getInt("hold");
		s.holdStart = t.getLong("hold_start");
		s.thunderUntil = t.getLong("thunder_until");
		s.boostUntil = t.getLong("boost_until");
		s.wizardBanUntil = t.getLong("wizard_ban_until");
		s.innocentKillAt = t.getLong("innocent_kill_at");
		return s;
	}

	public static final Codec<ShazamState> CODEC = CompoundTag.CODEC.xmap(ShazamState::load, ShazamState::save);
}
