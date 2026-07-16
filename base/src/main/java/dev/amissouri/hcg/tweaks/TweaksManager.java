package dev.amissouri.hcg.tweaks;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class TweaksManager {

    private final Map<String, Tweak> tweaks = new LinkedHashMap<>();

    public void register(Tweak tweak) {
        tweaks.put(tweak.id().toLowerCase(Locale.ROOT), tweak);
    }

    public Tweak get(String id) {
        return id == null ? null : tweaks.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Tweak> all() {
        return Collections.unmodifiableCollection(tweaks.values());
    }
}
