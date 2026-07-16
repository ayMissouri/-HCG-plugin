package dev.amissouri.hcg.tweaks;

import java.util.List;

import org.bukkit.Material;

public interface Tweak {
    String id();

    String displayName();

    Material icon();

    List<String> summary();

    String command();

    List<Setting> settings();

    boolean isEnabled();

    void setEnabled(boolean enabled);
}
