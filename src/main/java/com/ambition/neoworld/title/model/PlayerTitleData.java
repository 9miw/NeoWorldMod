package com.ambition.neoworld.title.model;

import com.ambition.neoworld.title.service.TitleService;
import com.ambition.neoworld.title.TitleRegistry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;

/** Legacy world-save import only. Runtime changes must use TitleService. */
public final class PlayerTitleData {
    public static final Codec<PlayerTitleData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(PlayerTitleData::savedTitles),
        Codec.STRING.optionalFieldOf("equipped", "").forGetter(data -> data.equipped)
    ).apply(instance, PlayerTitleData::new));

    private final Set<String> unlocked;
    private String equipped;

    public PlayerTitleData() { this(List.of(), ""); }
    private PlayerTitleData(List<String> titles, String equipped) {
        // Preserve saved identifiers so temporarily removed definitions can return later.
        this.unlocked = new HashSet<>(titles);
        this.equipped = unlocked.contains(equipped) ? equipped : "";
    }
    private List<String> savedTitles() { return unlocked.stream().sorted().toList(); }
    public Set<String> unlockedTitles() { return Set.copyOf(unlocked); }
    public Optional<TitleDefinition> equippedTitle() { return TitleRegistry.find(equipped); }
    public String equippedId() { return equipped; }
}
