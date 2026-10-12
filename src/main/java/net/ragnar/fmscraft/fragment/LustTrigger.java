package net.ragnar.fmscraft.fragment;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.sin.Sin;
import net.ragnar.fmscraft.world.FmsWorldState;

import java.util.HashSet;
import java.util.Set;

/**
 * Lust: one player breeds every animal in the {@code fmscraft:lust_breedables} tag (the same list as vanilla's
 * "Two by Two"). Completing it gives 5 fragments and resets that player's progress, so it can be done again.
 * Called from BredAnimalsCriterionMixin, so it sees exactly the breedings vanilla credits to a player.
 */
public final class LustTrigger {
    public static final int FRAGMENTS = 5;
    public static final TagKey<EntityType<?>> BREEDABLES =
            TagKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(FmsCraft.MOD_ID, "lust_breedables"));

    private LustTrigger() {
    }

    public static void onBred(ServerPlayerEntity player, EntityType<?> type) {
        if (!type.isIn(BREEDABLES)) return;

        FmsWorldState state = FmsWorldState.get(player.getServer());
        String id = Registries.ENTITY_TYPE.getId(type).toString();
        if (!state.addLustProgress(player.getUuid(), id)) return;

        Set<String> required = requiredIds();
        Set<String> done = state.getLustProgress(player.getUuid());
        done.retainAll(required);

        if (done.size() >= required.size()) {
            state.clearLustProgress(player.getUuid());
            FragmentEffects.give(player, Sin.LUST, FRAGMENTS);
        } else {
            player.sendMessage(Text.translatable("message.fmscraft.lust_progress",
                    type.getName(), done.size(), required.size()).formatted(Formatting.LIGHT_PURPLE), true);
        }
    }

    public static Set<String> requiredIds() {
        Set<String> ids = new HashSet<>();
        for (RegistryEntry<EntityType<?>> entry : Registries.ENTITY_TYPE.iterateEntries(BREEDABLES)) {
            ids.add(Registries.ENTITY_TYPE.getId(entry.value()).toString());
        }
        return ids;
    }
}
