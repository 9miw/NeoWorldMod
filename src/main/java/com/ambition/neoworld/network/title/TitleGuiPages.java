package com.ambition.neoworld.network.title;

import com.ambition.neoworld.title.TitlePlayerState;
import com.ambition.neoworld.title.model.TitleDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;

public final class TitleGuiPages {
    private TitleGuiPages() {}
    public static TitleGuiPagePayload build(TitleGuiRequestPayload request, TitlePlayerState state,
            List<TitleDefinition> definitions) {
        String search = request.search().strip().toLowerCase(Locale.ROOT);
        var matching = definitions.stream().filter(t -> switch (request.filter()) {
            case 1 -> t.category() == TitleDefinition.Category.GENERAL && t.rarity() == TitleDefinition.Rarity.COMMON;
            case 2 -> t.rarity() == TitleDefinition.Rarity.RARE;
            case 3 -> t.category() == TitleDefinition.Category.HUMOROUS;
            default -> true;
        }).filter(t -> request.ownership() == 0 || state.unlocked().containsKey(t.id()) == (request.ownership() == 1))
          .filter(t -> (t.name() + " " + t.display() + " " + t.id()).toLowerCase(Locale.ROOT).contains(search));
        var filtered = switch (request.sort()) {
            case 1 -> matching.sorted(Comparator.comparing(t -> t.rarity() != TitleDefinition.Rarity.RARE)).toList();
            case 2 -> matching.sorted(Comparator.comparing(TitleDefinition::name).thenComparing(TitleDefinition::id)).toList();
            default -> matching.toList();
        };
        int page = Math.min(request.page(), Math.max(0, (filtered.size() - 1) / TitleGuiPagePayload.PAGE_SIZE));
        var entries = filtered.stream().skip((long) page * TitleGuiPagePayload.PAGE_SIZE).limit(TitleGuiPagePayload.PAGE_SIZE)
            .map(t -> new TitleGuiPagePayload.Entry(t.id(), t.name(), t.display(), t.description(), t.requirement(), t.category().ordinal(),
                    t.rarity() == TitleDefinition.Rarity.RARE, t.rgb(), state.unlocked().containsKey(t.id()),
                    state.unlocked().getOrDefault(t.id(), 0L), state.progress().getOrDefault(t.condition().metric(), 0L),
                    t.condition().target())).toList();
        int unlocked = (int) definitions.stream().filter(t -> state.unlocked().containsKey(t.id())).count();
        return new TitleGuiPagePayload(request.requestId(), "", entries, page, filtered.size(), definitions.size(),
                unlocked, state.equipped(), state.showTitle(), state.showEffects());
    }
}
