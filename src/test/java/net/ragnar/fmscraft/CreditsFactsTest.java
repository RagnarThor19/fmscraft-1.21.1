package net.ragnar.fmscraft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.ragnar.fmscraft.ending.CreditsFacts;
import net.ragnar.fmscraft.network.CreditsPayload.Fact;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditsFactsTest {
    private static final long HOUR = 20L * 60 * 60;

    private static CreditsFacts.Player player(String name, long playTime, String stats, String advancements) {
        JsonObject s = JsonParser.parseString("{" + stats + "}").getAsJsonObject();
        JsonObject custom = s.has("minecraft:custom") ? s.getAsJsonObject("minecraft:custom") : new JsonObject();
        custom.addProperty("minecraft:play_time", playTime);
        s.add("minecraft:custom", custom);
        return new CreditsFacts.Player(name, s, JsonParser.parseString("{" + advancements + "}").getAsJsonObject());
    }

    private static String nether(String time) {
        return "\"minecraft:story/enter_the_nether\": {\"criteria\": {\"entered_nether\": \"" + time + "\"}, \"done\": true}";
    }

    private static Map<String, List<String>> byKey(List<Fact> facts) {
        Map<String, List<String>> map = new HashMap<>();
        for (Fact f : facts) map.put(f.key(), f.args());
        return map;
    }

    @Test
    void nobodyDidAnythingMeansNoFacts() {
        List<CreditsFacts.Player> players = List.of(player("A", 5 * HOUR, "", ""), player("B", 5 * HOUR, "", ""));
        Map<String, List<String>> facts = byKey(CreditsFacts.pick(players, new Random(1)));
        facts.remove("credits.fmscraft.fact.play_time");     // someone always played longest
        facts.remove("credits.fmscraft.fact.fewest_deaths");  // and died least
        assertTrue(facts.isEmpty(), facts.toString());
    }

    @Test
    void factsGoToWhoeverDidTheMost() {
        List<CreditsFacts.Player> players = List.of(
                player("A", 2 * HOUR, "\"minecraft:killed\": {\"minecraft:creeper\": 40}", ""),
                player("B", 3 * HOUR, "\"minecraft:killed\": {\"minecraft:creeper\": 12, \"minecraft:turtle\": 1}", ""));
        Map<String, List<String>> facts = byKey(CreditsFacts.pick(players, new Random(1)));
        assertEquals(List.of("A", "40"), facts.get("credits.fmscraft.fact.creepers"));
        assertEquals(List.of("B"), facts.get("credits.fmscraft.fact.turtles.one"), "one turtle reads as one");
        assertEquals(List.of("B", "3.0"), facts.get("credits.fmscraft.fact.play_time"));
    }

    @Test
    void tooLittleIsntAFact() {
        List<CreditsFacts.Player> players = List.of(player("A", 2 * HOUR, "\"minecraft:killed\": {\"minecraft:creeper\": 3}", ""));
        assertFalse(byKey(CreditsFacts.pick(players, new Random(1))).containsKey("credits.fmscraft.fact.creepers"));
    }

    @Test
    void someoneWhoBarelyPlayedWinsNothing() {
        List<CreditsFacts.Player> players = List.of(player("Bot", 20 * 60, "\"minecraft:killed\": {\"minecraft:creeper\": 500}", ""));
        assertTrue(CreditsFacts.pick(players, new Random(1)).isEmpty());
    }

    @Test
    void firstLastAndNeverComeFromAdvancementDates() {
        List<CreditsFacts.Player> players = List.of(
                player("Early", 3 * HOUR, "", nether("2026-01-02 10:00:00 +0000")),
                player("Late", 3 * HOUR, "", nether("2026-03-02 10:00:00 +0000")),
                player("Homebody", 9 * HOUR, "", ""));
        Map<String, List<String>> facts = byKey(CreditsFacts.pick(players, new Random(1)));
        assertEquals(List.of("Early"), facts.get("credits.fmscraft.fact.first_nether"));
        assertEquals(List.of("Late"), facts.get("credits.fmscraft.fact.last_nether"));
        assertEquals(List.of("Homebody"), facts.get("credits.fmscraft.fact.never_nether"));
    }

    @Test
    void atMostTwentySpreadOverEveryone() {
        String busy = "\"minecraft:killed\": {\"minecraft:creeper\": 99, \"minecraft:enderman\": 99, \"minecraft:chicken\": 99, "
                + "\"minecraft:phantom\": 99, \"minecraft:squid\": 99, \"minecraft:bee\": 9, \"minecraft:wolf\": 9, \"minecraft:panda\": 9}, "
                + "\"minecraft:custom\": {\"minecraft:jump\": 99999, \"minecraft:deaths\": 99, \"minecraft:fish_caught\": 99, "
                + "\"minecraft:animals_bred\": 999, \"minecraft:open_chest\": 9999, \"minecraft:drop\": 9999, \"minecraft:enchant_item\": 99, "
                + "\"minecraft:bell_ring\": 99, \"minecraft:sleep_in_bed\": 99, \"minecraft:walk_one_cm\": 99999999, "
                + "\"minecraft:sprint_one_cm\": 99999999, \"minecraft:traded_with_villager\": 999}, "
                + "\"minecraft:crafted\": {\"minecraft:torch\": 9999, \"minecraft:iron_pickaxe\": 99}";
        List<CreditsFacts.Player> players = new ArrayList<>();
        players.add(player("Busy", 50 * HOUR, busy, ""));
        players.add(player("Quiet", 1 * HOUR, "\"minecraft:killed\": {\"minecraft:dolphin\": 1}", ""));
        for (int seed = 0; seed < 20; seed++) {
            List<Fact> facts = CreditsFacts.pick(players, new Random(seed));
            assertEquals(CreditsFacts.MAX_FACTS, facts.size());
            assertEquals(facts.size(), new HashSet<>(facts).size(), "no fact twice");
            assertTrue(facts.stream().anyMatch(f -> f.args().getFirst().equals("Quiet")), "the quiet one still gets theirs");
        }
    }

    @Test
    void everyFactHasItsText() {
        for (CreditsFacts.Candidate c : CreditsFacts.CANDIDATES) {
            assertTrue(c.text().startsWith("%s"), c.id() + " starts with the player");
            assertTrue(c.one() == null || !c.one().contains("%s %s"), c.id());
        }
        assertEquals(CreditsFacts.CANDIDATES.size(), CreditsFacts.CANDIDATES.stream().map(CreditsFacts.Candidate::id).distinct().count());
    }
}
