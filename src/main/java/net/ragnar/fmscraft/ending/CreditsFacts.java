package net.ragnar.fmscraft.ending;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UserCache;
import net.minecraft.util.WorldSavePath;
import net.ragnar.fmscraft.FmsCraft;
import net.ragnar.fmscraft.network.CreditsPayload.Fact;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/**
 * The fun facts in the True Ending credits ("X killed 41 creepers", "Y was the first one into the Nether").
 *
 * <p>They come from what vanilla already keeps for every player who ever joined, online or not: the statistics in
 * {@code world/stats/<uuid>.json} and the advancement dates in {@code world/advancements/<uuid>.json}. Every candidate
 * below names its winner; the credits show up to {@link #MAX_FACTS} of them, spread over as many players as possible.
 *
 * <p><b>To change the texts, edit {@link #CANDIDATES}</b> and run {@code gradlew runDatagen}. The first {@code %s} is the
 * player, the second the number. {@code one} is the text used when the number is exactly 1 (no number in it).
 */
public final class CreditsFacts {
    public static final int MAX_FACTS = 20;

    private static final long TICKS_PER_MINUTE = 20L * 60;
    /** Players with less time than this (bots, someone who popped in once) don't win facts. */
    static final long MIN_PLAY_TIME = 10 * TICKS_PER_MINUTE;
    private static final DateTimeFormatter ADVANCEMENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);

    private CreditsFacts() {
    }

    // ---- The facts ----

    public static final List<Candidate> CANDIDATES = List.of(
            // Mobs
            most("creepers", 10, Unit.COUNT, p -> p.stat("killed", "creeper"),
                    "%s killed %s creepers before any of them could hiss.", null),
            most("turtles", 1, Unit.COUNT, p -> p.stat("killed", "turtle"),
                    "%s killed %s turtles. They had families.", "%s killed a turtle. It had a family."),
            most("villagers", 1, Unit.COUNT, p -> p.stat("killed", "villager"),
                    "%s killed %s villagers. Hrmm.", "%s killed a villager. Hrmm."),
            most("iron_golems", 1, Unit.COUNT, p -> p.stat("killed", "iron_golem"),
                    "%s took down %s iron golems. The village is not happy.", "%s took down an iron golem. The village is not happy."),
            most("endermen", 5, Unit.COUNT, p -> p.stat("killed", "enderman"),
                    "%s looked %s endermen in the eye. And won.", null),
            most("chickens", 15, Unit.COUNT, p -> p.stat("killed", "chicken"),
                    "%s killed %s chickens. Bwok.", null),
            most("bees", 1, Unit.COUNT, p -> p.stat("killed", "bee"),
                    "%s killed %s bees. The bees did nothing wrong.", "%s killed a bee. The bee did nothing wrong."),
            most("wolves", 1, Unit.COUNT, p -> p.stat("killed", "wolf"),
                    "%s killed %s wolves. Hopefully nobody's dog.", "%s killed a wolf. Hopefully nobody's dog."),
            most("pandas", 1, Unit.COUNT, p -> p.stat("killed", "panda"),
                    "%s killed %s pandas. Why would you do that.", "%s killed a panda. Why would you do that."),
            most("dolphins", 1, Unit.COUNT, p -> p.stat("killed", "dolphin"),
                    "%s killed %s dolphins. Unforgivable.", "%s killed a dolphin. Unforgivable."),
            most("squids", 5, Unit.COUNT, p -> p.stat("killed", "squid", "glow_squid"),
                    "%s has something against squids: %s of them, gone.", null),
            most("phantoms", 5, Unit.COUNT, p -> p.stat("killed", "phantom"),
                    "%s swatted %s phantoms out of the sky. Go to sleep, people.", null),
            most("wardens", 1, Unit.COUNT, p -> p.stat("killed", "warden"),
                    "%s killed %s Wardens. In the dark. Absolute legend.", "%s killed a Warden. In the dark. Absolute legend."),
            most("dragon", 1, Unit.COUNT, p -> p.stat("killed", "ender_dragon"),
                    "%s slew the Ender Dragon %s times.", "%s landed the killing blow on the Ender Dragon."),
            most("wither", 1, Unit.COUNT, p -> p.stat("killed", "wither"),
                    "%s took down the Wither %s times.", "%s took down the Wither."),
            most("piglins", 10, Unit.COUNT, p -> p.stat("killed", "zombified_piglin"),
                    "%s made the whole Nether angry: %s zombified piglins down.", null),
            most("friends", 1, Unit.COUNT, p -> p.stat("custom", "player_kills"),
                    "%s killed their friends %s times. Friendship ended.", "%s killed a friend once. Just once. They remember."),

            // Dying
            most("deaths", 5, Unit.COUNT, p -> p.stat("custom", "deaths"),
                    "%s died %s times. Respawning is a skill too.", null),
            fewest("fewest_deaths", p -> p.stat("custom", "deaths"),
                    "%s only died %s times. Suspicious.", "%s died exactly once. Show-off."),
            most("blown_up", 3, Unit.COUNT, p -> p.stat("killed_by", "creeper"),
                    "%s was blown up by creepers %s times. Look behind you.", null),
            most("fallen", 500 * 100, Unit.BLOCKS, p -> p.stat("custom", "fall_one_cm"),
                    "%s fell a total of %s blocks. Gravity always wins.", null),
            most("damage", 500 * 20, Unit.HEARTS, p -> p.stat("custom", "damage_taken"),
                    "%s took %s hearts of damage and just kept going.", null),
            most("totems", 1, Unit.COUNT, p -> p.stat("used", "totem_of_undying"),
                    "%s cheated death %s times with a totem.", "%s cheated death with a totem."),

            // Getting around
            most("jumps", 2000, Unit.COUNT, p -> p.stat("custom", "jump"),
                    "%s jumped %s times. Why walk when you can hop.", null),
            most("walked", 20 * 100_000, Unit.KM, p -> p.stat("custom", "walk_one_cm"),
                    "%s walked %s km. That's a lot of steps.", null),
            most("sprinted", 10 * 100_000, Unit.KM, p -> p.stat("custom", "sprint_one_cm"),
                    "%s sprinted %s km. Always in a hurry.", null),
            most("elytra", 5 * 100_000, Unit.KM, p -> p.stat("custom", "aviate_one_cm"),
                    "%s flew %s km with elytra.", null),
            most("boat", 2 * 100_000, Unit.KM, p -> p.stat("custom", "boat_one_cm"),
                    "%s rowed %s km by boat. Captain.", null),
            most("swum", 1 * 100_000, Unit.KM, p -> p.stat("custom", "swim_one_cm"),
                    "%s swam %s km. Part fish.", null),
            most("horse", 1 * 100_000, Unit.KM, p -> p.stat("custom", "horse_one_cm"),
                    "%s rode %s km on horseback. Yeehaw.", null),
            most("pig", 10 * 100, Unit.BLOCKS, p -> p.stat("custom", "pig_one_cm"),
                    "%s rode a pig for %s blocks. Respect.", null),
            most("sneaking", 15 * TICKS_PER_MINUTE, Unit.MINUTES, p -> p.stat("custom", "sneak_time"),
                    "%s spent %s minutes sneaking. Very suspicious.", null),
            most("ender_pearls", 25, Unit.COUNT, p -> p.stat("used", "ender_pearl"),
                    "%s threw %s ender pearls. Teleporting everywhere.", null),

            // Living
            most("play_time", 0, Unit.HOURS, p -> p.stat("custom", "play_time"),
                    "%s played for %s hours. Go touch some grass.", null),
            most("slept", 15, Unit.COUNT, p -> p.stat("custom", "sleep_in_bed"),
                    "%s slept in a bed %s times. Well rested.", null),
            most("fish", 10, Unit.COUNT, p -> p.stat("custom", "fish_caught"),
                    "%s caught %s fish. Very patient.", null),
            most("bred", 25, Unit.COUNT, p -> p.stat("custom", "animals_bred"),
                    "%s bred %s animals. Lust approves.", null),
            most("trades", 30, Unit.COUNT, p -> p.stat("custom", "traded_with_villager"),
                    "%s traded with villagers %s times. Hrmm. Hrmm.", null),
            most("cake", 3, Unit.COUNT, p -> p.stat("custom", "eat_cake_slice"),
                    "%s ate %s slices of cake. Gluttony approves.", null),
            most("chests", 250, Unit.COUNT, p -> p.stat("custom", "open_chest"),
                    "%s opened %s chests. Nosy.", null),
            most("dropped", 500, Unit.COUNT, p -> p.stat("custom", "drop"),
                    "%s threw %s things on the ground. Litterbug.", null),
            most("enchanted", 10, Unit.COUNT, p -> p.stat("custom", "enchant_item"),
                    "%s enchanted %s items.", null),
            most("flowers", 1, Unit.COUNT, p -> p.stat("custom", "pot_flower"),
                    "%s potted %s flowers. Lovely.", "%s potted a flower. Lovely."),
            most("bells", 10, Unit.COUNT, p -> p.stat("custom", "bell_ring"),
                    "%s rang a bell %s times. Somebody stop them.", null),
            most("note_blocks", 20, Unit.COUNT, p -> p.stat("custom", "play_noteblock"),
                    "%s played %s notes on note blocks. A true musician.", null),
            most("raids", 1, Unit.COUNT, p -> p.stat("custom", "raid_win"),
                    "%s saved a village from %s raids.", "%s saved a village from a raid. Hero."),

            // Making and digging
            most("iron_pickaxes", 4, Unit.COUNT, p -> p.stat("crafted", "iron_pickaxe"),
                    "%s crafted %s iron pickaxes. Where do they all go?", null),
            most("torches", 300, Unit.COUNT, p -> p.stat("crafted", "torch"),
                    "%s crafted %s torches. It's bright in here.", null),
            most("diamonds", 10, Unit.COUNT, p -> p.stat("mined", "diamond_ore", "deepslate_diamond_ore"),
                    "%s mined %s diamond ores. Rich.", null),
            most("debris", 4, Unit.COUNT, p -> p.stat("mined", "ancient_debris"),
                    "%s dug up %s ancient debris.", null),
            most("dirt", 1000, Unit.COUNT, p -> p.stat("mined", "dirt", "grass_block", "coarse_dirt"),
                    "%s dug up %s blocks of dirt. Professional hole digger.", null),
            most("tools_broken", 5, Unit.COUNT, Player::toolsBroken,
                    "%s wore out %s tools. Maybe try mending.", null),
            most("rotten_flesh", 64, Unit.COUNT, p -> p.stat("picked_up", "rotten_flesh"),
                    "%s picked up %s rotten flesh. And kept it.", null),

            // Firsts and lasts
            first("first_nether", "story/enter_the_nether", "%s was the first one into the Nether."),
            last("last_nether", "story/enter_the_nether", "%s took the longest to find the Nether."),
            never("never_nether", "story/enter_the_nether", "%s never went to the Nether. Not even once."),
            first("first_end", "story/enter_the_end", "%s set foot in the End before anyone else."),
            first("first_diamond", "story/mine_diamond", "%s found the first diamond."),
            never("never_diamond", "story/mine_diamond", "%s never mined a single diamond."),
            first("first_iron", "story/smelt_iron", "%s smelted the first iron."),
            first("first_pet", "husbandry/tame_an_animal", "%s tamed the first pet."),
            first("first_seed", "husbandry/plant_seed", "%s planted the first seed. A farmer at heart.")
    );

    // ---- Picking ----

    /**
     * Up to {@link #MAX_FACTS} facts, each candidate at most once, handed out so everyone gets one before anyone gets
     * a second. None if nobody did anything worth one.
     */
    public static List<Fact> pick(List<Player> players, Random random) {
        List<Fact> won = new ArrayList<>();
        for (Candidate candidate : CANDIDATES) candidate.award(players).ifPresent(won::add);
        Collections.shuffle(won, random);

        List<Fact> picked = new ArrayList<>();
        Map<String, Integer> perPlayer = new HashMap<>();
        while (picked.size() < MAX_FACTS && !won.isEmpty()) {
            Fact next = won.stream().min(Comparator.comparingInt(f -> perPlayer.getOrDefault(f.args().getFirst(), 0))).orElseThrow();
            won.remove(next);
            picked.add(next);
            perPlayer.merge(next.args().getFirst(), 1, Integer::sum);
        }
        return picked;
    }

    /** The facts for everyone who ever played in this world, from their saved stats (saved fresh for those online). */
    public static List<Fact> collect(MinecraftServer server) {
        return pick(load(server), new Random());
    }

    public static List<Player> load(MinecraftServer server) {
        server.getPlayerManager().saveAllPlayerData();
        Path statsDir = server.getSavePath(WorldSavePath.STATS);
        Path advancementsDir = server.getSavePath(WorldSavePath.ADVANCEMENTS);
        List<Player> players = new ArrayList<>();
        if (!Files.isDirectory(statsDir)) return players;
        try (Stream<Path> files = Files.list(statsDir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
                String id = file.getFileName().toString().replace(".json", "");
                UUID uuid;
                try {
                    uuid = UUID.fromString(id);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                String name = nameOf(server, uuid);
                if (name == null) continue;
                JsonObject stats = read(file);
                JsonObject advancements = read(advancementsDir.resolve(id + ".json"));
                players.add(new Player(name, stats.has("stats") ? stats.getAsJsonObject("stats") : new JsonObject(), advancements));
            }
        } catch (IOException e) {
            FmsCraft.LOGGER.warn("Couldn't read the player stats for the credits", e);
        }
        players.sort(Comparator.comparing(Player::name));
        return players;
    }

    @Nullable
    private static String nameOf(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) return online.getGameProfile().getName();
        UserCache cache = server.getUserCache();
        return cache == null ? null : cache.getByUuid(uuid).map(GameProfile::getName).orElse(null);
    }

    private static JsonObject read(Path file) {
        if (!Files.isRegularFile(file)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement json = JsonParser.parseReader(reader);
            return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    // ---- The model ----

    /** A player's saved statistics ({@code "minecraft:killed": {...}}) and advancements, as vanilla writes them. */
    public record Player(String name, JsonObject stats, JsonObject advancements) {
        public long stat(String type, String... ids) {
            JsonObject group = stats.getAsJsonObject("minecraft:" + type);
            if (group == null) return 0;
            long total = 0;
            for (String id : ids) {
                JsonElement value = group.get("minecraft:" + id);
                if (value != null && value.isJsonPrimitive()) total += value.getAsLong();
            }
            return total;
        }

        long toolsBroken() {
            JsonObject group = stats.getAsJsonObject("minecraft:broken");
            if (group == null) return 0;
            long total = 0;
            for (Map.Entry<String, JsonElement> e : group.entrySet()) total += e.getValue().getAsLong();
            return total;
        }

        long playTime() {
            return stat("custom", "play_time");
        }

        /** When they got an advancement (the first of its criteria), or empty if they never did. */
        Optional<Instant> got(String advancement) {
            JsonElement entry = advancements.get("minecraft:" + advancement);
            if (entry == null || !entry.isJsonObject()) return Optional.empty();
            JsonObject criteria = entry.getAsJsonObject().getAsJsonObject("criteria");
            if (criteria == null) return Optional.empty();
            Instant earliest = null;
            for (Map.Entry<String, JsonElement> criterion : criteria.entrySet()) {
                try {
                    Instant at = ZonedDateTime.parse(criterion.getValue().getAsString(), ADVANCEMENT_TIME).toInstant();
                    if (earliest == null || at.isBefore(earliest)) earliest = at;
                } catch (Exception ignored) {
                }
            }
            return Optional.ofNullable(earliest);
        }
    }

    enum Unit {
        COUNT, BLOCKS, KM, HOURS, MINUTES, HEARTS;

        String format(long raw) {
            return switch (this) {
                case COUNT -> String.format(Locale.ROOT, "%,d", raw);
                case BLOCKS -> String.format(Locale.ROOT, "%,d", raw / 100);           // centimeters
                case KM -> String.format(Locale.ROOT, "%,.1f", raw / 100_000.0);        // centimeters
                case HOURS -> String.format(Locale.ROOT, "%,.1f", raw / (TICKS_PER_MINUTE * 60.0));
                case MINUTES -> String.format(Locale.ROOT, "%,d", raw / TICKS_PER_MINUTE);
                case HEARTS -> String.format(Locale.ROOT, "%,d", raw / 20);            // tenths of a health point
            };
        }
    }

    private interface Judge {
        /** The winner and their number, if anyone earned it. */
        Optional<Map.Entry<Player, Long>> winner(List<Player> players);
    }

    /** One possible fact: its translation key is {@code credits.fmscraft.fact.<id>} (and {@code .one}). */
    public record Candidate(String id, String text, @Nullable String one, boolean showsNumber, Unit unit, Judge judge) {
        public String key() {
            return "credits.fmscraft.fact." + id;
        }

        Optional<Fact> award(List<Player> players) {
            return judge.winner(players).map(win -> {
                String name = win.getKey().name();
                long value = win.getValue();
                if (!showsNumber) return new Fact(key(), List.of(name));
                if (one != null && value == 1) return new Fact(key() + ".one", List.of(name));
                return new Fact(key(), List.of(name, unit.format(value)));
            });
        }
    }

    private static List<Player> played(List<Player> players, long atLeast) {
        return players.stream().filter(p -> p.playTime() >= atLeast).toList();
    }

    /** Whoever has the most, if it's at least {@code min}. */
    private static Candidate most(String id, long min, Unit unit, ToLongFunction<Player> value, String text, @Nullable String one) {
        return new Candidate(id, text, one, true, unit, players -> played(players, MIN_PLAY_TIME).stream()
                .map(p -> Map.entry(p, value.applyAsLong(p)))
                .filter(e -> e.getValue() >= Math.max(1, min))
                .max(Map.Entry.comparingByValue()));
    }

    /** Whoever has the least, out of at least two who played an hour or more. */
    private static Candidate fewest(String id, ToLongFunction<Player> value, String text, String one) {
        return new Candidate(id, text, one, true, Unit.COUNT, players -> {
            List<Player> regulars = played(players, 60 * TICKS_PER_MINUTE);
            if (regulars.size() < 2) return Optional.empty();
            return regulars.stream().map(p -> Map.entry(p, value.applyAsLong(p))).min(Map.Entry.comparingByValue());
        });
    }

    /** Whoever got an advancement first. */
    private static Candidate first(String id, String advancement, String text) {
        return new Candidate(id, text, null, false, Unit.COUNT, players -> players.stream()
                .flatMap(p -> p.got(advancement).map(at -> Map.entry(p, at)).stream())
                .min(Map.Entry.comparingByValue())
                .map(e -> Map.entry(e.getKey(), 0L)));
    }

    /** Whoever got an advancement last, when at least two did. */
    private static Candidate last(String id, String advancement, String text) {
        return new Candidate(id, text, null, false, Unit.COUNT, players -> {
            List<Map.Entry<Player, Instant>> got = players.stream()
                    .flatMap(p -> p.got(advancement).map(at -> Map.entry(p, at)).stream()).toList();
            if (got.size() < 2) return Optional.empty();
            return got.stream().max(Map.Entry.comparingByValue()).map(e -> Map.entry(e.getKey(), 0L));
        });
    }

    /** Whoever played longest (two hours at least) without an advancement someone else got. */
    private static Candidate never(String id, String advancement, String text) {
        return new Candidate(id, text, null, false, Unit.COUNT, players -> {
            if (players.stream().noneMatch(p -> p.got(advancement).isPresent())) return Optional.empty();
            return played(players, 120 * TICKS_PER_MINUTE).stream()
                    .filter(p -> p.got(advancement).isEmpty())
                    .max(Comparator.comparingLong(Player::playTime))
                    .map(p -> Map.entry(p, 0L));
        });
    }
}
