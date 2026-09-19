package com.hhst.dydownloader.home;

import com.hhst.dydownloader.model.CardType;
import com.hhst.dydownloader.model.ResourceItem;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HomeCardList {
  private HomeCardList() {}

  public static List<HomeCard> build(
      List<ResourceItem> resources, Collection<HomeCard> queueCards,
      String query, CardType filter, Comparator<ResourceItem> comparator) {
    String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);
    Set<String> queuedKeys = new HashSet<>();
    for (HomeCard card : queueCards) queuedKeys.add(card.key());
    List<HomeCard> display = new ArrayList<>();
    queueCards.stream()
        .filter(card -> matches(card.item(), normalizedQuery, filter))
        .sorted(Comparator.comparingLong(HomeCard::queuedAt).reversed().thenComparing(HomeCard::key))
        .forEach(display::add);
    resources.stream()
        .filter(item -> !queuedKeys.contains(item.key()))
        .filter(item -> matches(item, normalizedQuery, filter))
        .sorted(comparator)
        .map(HomeCard::done)
        .forEach(display::add);
    return display;
  }

  private static boolean matches(ResourceItem item, String query, CardType filter) {
    return (filter == null || item.type() == filter)
        && (query.isEmpty()
            || item.text().toLowerCase(Locale.ROOT).contains(query)
            || item.authorNickname().toLowerCase(Locale.ROOT).contains(query));
  }
}
