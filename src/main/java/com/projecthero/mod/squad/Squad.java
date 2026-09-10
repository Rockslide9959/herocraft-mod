package com.projecthero.mod.squad;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * One squad: a named group of players who cannot hurt each other and can see each other on the squad
 * screen (P). Deliberately its own concept rather than a vanilla scoreboard team -- a scoreboard team
 * is server-admin furniture that players cannot create or manage themselves, and this has to be
 * something any player can spin up mid-session with {@code /squad create}.
 *
 * <p>Immutable-ish value object: {@link SquadManager} owns every instance and is what marks the save
 * dirty, so mutations all go through it.
 */
public final class Squad {
	public static final Codec<Squad> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(s -> s.id),
			Codec.STRING.fieldOf("name").forGetter(s -> s.name),
			UUIDUtil.STRING_CODEC.fieldOf("leader").forGetter(s -> s.leader),
			UUIDUtil.STRING_CODEC.listOf().fieldOf("members").forGetter(s -> new ArrayList<>(s.members))
	).apply(instance, Squad::new));

	private final UUID id;
	private String name;
	private UUID leader;
	private final Set<UUID> members = new LinkedHashSet<>();

	private Squad(UUID id, String name, UUID leader, List<UUID> members) {
		this.id = id;
		this.name = name;
		this.leader = leader;
		this.members.addAll(members);
		this.members.add(leader);
	}

	static Squad create(String name, UUID leader) {
		return new Squad(UUID.randomUUID(), name, leader, List.of(leader));
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	void setName(String name) {
		this.name = name;
	}

	public UUID leader() {
		return leader;
	}

	void setLeader(UUID leader) {
		this.leader = leader;
		this.members.add(leader);
	}

	public Set<UUID> members() {
		return java.util.Collections.unmodifiableSet(members);
	}

	public boolean has(UUID player) {
		return members.contains(player);
	}

	public int size() {
		return members.size();
	}

	void add(UUID player) {
		members.add(player);
	}

	void remove(UUID player) {
		members.remove(player);
	}
}
