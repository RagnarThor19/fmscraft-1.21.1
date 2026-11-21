package net.ragnar.fmscraft.fragment;

/**
 * How each sin's fragments are obtained (full rules in docs/DESIGN.md). The "first fragment" advancement is
 * data-driven (inventory_changed), so a trigger only has to put the fragment into the world.
 *
 *  - GREED    lucky strike: 2.5% gold, 2% diamond/emerald/debris, 1% redstone/lapis {@link LuckyStrike} (mixin)
 *  - WRATH    3 warden kills within 4 minutes, any players               {@link WrathTrigger}
 *  - PRIDE    a mob skull on an armor stand, once per skull              {@link PrideTrigger} (mixin)
 *  - GLUTTONY eat an enchanted golden apple                              {@link GluttonyTrigger} (mixin)
 *  - ENVY     kill the rare shivering Bogged (pumpkin, iron sword)       {@link EnvyTrigger} (mixins)
 *  - LUST     one player breeds every animal in Two by Two -> 5           {@link LustTrigger} (mixin)
 *  - SLOTH    5% per kill by a tamed wolf or tome summon                 {@link SlothTrigger}
 */
public final class FragmentTriggers {
    private FragmentTriggers() {
    }

    public static void register() {
        EnvyTrigger.register();
        WrathTrigger.register();
        SlothTrigger.register();
        PrideTrigger.register();
    }
}
