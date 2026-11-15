package com.projecthero.mod.client.targetdummy;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.targetdummy.DummyDamageKind;
import com.projecthero.mod.targetdummy.DummyHitPayload;
import com.projecthero.mod.targetdummy.TargetDummyEntity;

import net.minecraft.network.chat.Component;

/**
 * v0.17.5: what one client knows about one {@link TargetDummyEntity} -- the latest combat totals from the server, the
 * floating numbers still in the air and the wobble. Kept on the entity itself ({@code TargetDummyEntity#clientData}), so
 * it goes when the entity goes (no static map). Times are the entity's own client {@code tickCount}.
 */
public final class DummyClientStats {
	/** Ticks a floating number lives. */
	public static final int POPUP_LIFE = 26;
	/** The summary stays this long after a combat ends (then only while looked at). */
	public static final int SUMMARY_TICKS = 100;
	/** Most numbers in the air at once (a flamethrower would otherwise stack hundreds). */
	private static final int MAX_POPUPS = 40;

	/** One floating number. */
	public static final class Popup {
		final int born;
		final float value;
		final float raw;
		final DummyDamageKind kind;
		final boolean crit;
		final boolean dot;
		final float dx;
		final float dz;
		final float dy;

		Popup(int born, float value, float raw, DummyDamageKind kind, boolean crit, boolean dot, float dx, float dy, float dz) {
			this.born = born;
			this.value = value;
			this.raw = raw;
			this.kind = kind;
			this.crit = crit;
			this.dot = dot;
			this.dx = dx;
			this.dy = dy;
			this.dz = dz;
		}
	}

	final List<Popup> popups = new ArrayList<>();
	int hits;
	float total;
	float totalRaw;
	float biggest;
	float lastRaw;
	float lastDealt;
	Component lastLabel = Component.empty();
	DummyDamageKind lastKind = DummyDamageKind.PHYSICAL;
	boolean lastCrit;
	boolean cooldown;
	/** Client ticks of the combat's first and latest hit. */
	int firstHit;
	int lastHit = -100_000;

	// wobble
	float wobbleAmp;
	int wobbleStart = -100_000;
	float wobbleYaw;

	private int seed;

	public static DummyClientStats of(TargetDummyEntity dummy) {
		if (dummy.clientData instanceof DummyClientStats s) {
			return s;
		}
		DummyClientStats s = new DummyClientStats();
		dummy.clientData = s;
		return s;
	}

	/** One payload from the server. */
	void accept(TargetDummyEntity dummy, DummyHitPayload p) {
		int now = dummy.tickCount;
		cooldown = p.has(DummyHitPayload.COOLDOWN);
		if (p.has(DummyHitPayload.RESET)) {
			hits = 0;
			total = totalRaw = biggest = lastRaw = lastDealt = 0.0f;
			lastLabel = Component.empty();
			lastHit = -100_000;
			return;
		}
		hits = p.hits();
		total = p.total();
		totalRaw = p.totalRaw();
		biggest = p.biggest();
		lastRaw = p.raw();
		lastDealt = p.dealt();
		lastLabel = p.label();
		lastKind = DummyDamageKind.byId(p.kind());
		lastCrit = p.has(DummyHitPayload.CRIT);
		lastHit = now;
		firstHit = now - p.combatTicks();
		boolean dot = p.has(DummyHitPayload.DOT);

		// the floating number: scattered round the dummy's upper body
		float a = rand() * (float) Math.PI * 2.0f;
		float r = 0.25f + rand() * 0.2f;
		if (popups.size() >= MAX_POPUPS) {
			popups.remove(0);
		}
		popups.add(new Popup(now, p.dealt(), p.raw(), lastKind, lastCrit, dot, (float) Math.cos(a) * r, 1.25f + rand() * 0.45f, (float) Math.sin(a) * r));

		// the wobble: bigger for bigger hits, adds to a sway still going, small for damage-over-time ticks
		float age = now - wobbleStart;
		float left = wobbleAmp * (float) Math.exp(-age / 14.0f);
		float push = dot ? 2.0f : Math.min(30.0f, 5.0f + p.dealt() * 1.6f);
		wobbleAmp = Math.min(32.0f, Math.max(left, push) + (left > 0.5f ? push * 0.25f : 0.0f));
		wobbleStart = now;
		wobbleYaw = Float.isNaN(p.hitYaw()) ? rand() * 360.0f : p.hitYaw();
	}

	/** Current lean in degrees (damped sine). */
	float wobble(TargetDummyEntity dummy, float partialTick) {
		float t = dummy.tickCount - wobbleStart + partialTick;
		if (t < 0 || t > 70) {
			return 0.0f;
		}
		return wobbleAmp * (float) Math.exp(-t / 14.0f) * (float) Math.sin(t * 0.85f);
	}

	boolean inCombat(TargetDummyEntity dummy) {
		return hits > 0 && dummy.tickCount - lastHit <= TargetDummyEntity.COMBAT_TIMEOUT;
	}

	/** In the summary window right after a combat. */
	boolean inSummary(TargetDummyEntity dummy) {
		int since = dummy.tickCount - lastHit;
		return hits > 0 && since > TargetDummyEntity.COMBAT_TIMEOUT && since <= TargetDummyEntity.COMBAT_TIMEOUT + SUMMARY_TICKS;
	}

	/** Seconds of combat so far (live while fighting, frozen at the last hit after). */
	float combatSeconds(TargetDummyEntity dummy, float partialTick) {
		if (hits == 0) {
			return 0.0f;
		}
		int end = inCombat(dummy) ? dummy.tickCount : lastHit;
		return Math.max(0.0f, (end - firstHit + (inCombat(dummy) ? partialTick : 0.0f)) / 20.0f);
	}

	/** DPS: live while fighting (falls between hits), first-to-last hit once it is over -- at least one second either way. */
	float dps(TargetDummyEntity dummy, float partialTick) {
		if (hits == 0) {
			return 0.0f;
		}
		float seconds = inCombat(dummy) ? combatSeconds(dummy, partialTick) : (lastHit - firstHit) / 20.0f;
		return total / Math.max(1.0f, seconds);
	}

	private float rand() {
		seed = seed * 1103515245 + 12345;
		return ((seed >>> 8) & 0xFFFF) / 65535.0f;
	}
}
