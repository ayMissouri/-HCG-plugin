package dev.amissouri.hcg.tweaks;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;

public interface Setting {

    String name();

    Material icon();

    String valueLabel();

    List<String> description();

    void cycle(boolean forward);

    record Of(String name, Material icon, Supplier<String> value, Consumer<Boolean> onCycle,
              List<String> description) implements Setting {

        @Override
        public String valueLabel() {
            return value.get();
        }

        @Override
        public void cycle(boolean forward) {
            onCycle.accept(forward);
        }
    }

    static <E extends Enum<E>> E step(E current, E[] values, boolean forward) {
        int next = Math.floorMod(current.ordinal() + (forward ? 1 : -1), values.length);
        return values[next];
    }

    static int step(int current, int[] steps, boolean forward) {
        int index = 0;
        for (int i = 0; i < steps.length; i++) {
            if (Math.abs(steps[i] - current) < Math.abs(steps[index] - current)) {
                index = i;
            }
        }
        return steps[Math.floorMod(index + (forward ? 1 : -1), steps.length)];
    }
}
