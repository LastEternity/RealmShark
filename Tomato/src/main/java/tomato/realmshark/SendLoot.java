package tomato.realmshark;

import assets.IdToAsset;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.List;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import packets.data.StatData;
import packets.data.enums.StatType;
import packets.incoming.MapInfoPacket;
import tomato.backend.data.Entity;
import tomato.backend.data.TomatoData;
import tomato.realmshark.enums.CharacterClass;
import util.PropertiesManager;

public class SendLoot {

    public static class BridgeLogEntry {
        public final String timestamp;
        public final String level;
        public final String message;

        public BridgeLogEntry(String timestamp, String level, String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }
    }

    public static class BridgeEvent {
        public final String timestamp;
        public final String itemName;
        public final String dungeon;
        public final boolean sent;

        public BridgeEvent(String timestamp, String itemName, String dungeon, boolean sent) {
            this.timestamp = timestamp;
            this.itemName = itemName;
            this.dungeon = dungeon;
            this.sent = sent;
        }
    }

    public interface BridgeEventListener {
        void onBridgeEvent(BridgeEvent event);
    }

    public interface BridgeLogListener {
        void onBridgeLog(BridgeLogEntry entry);
    }

    private static final String BRIDGE_ENABLED_KEY = "realmshark.bridge.enabled";
    private static final String BRIDGE_ENDPOINT_KEY = "realmshark.bridge.endpoint";
    private static final String BRIDGE_GUILD_ID_KEY = "realmshark.bridge.guild_id";
    private static final String BRIDGE_LINK_TOKEN_KEY = "realmshark.bridge.link_token";
    private static final String BRIDGE_CSV_PATH_KEY = "realmshark.bridge.csv_path";
    private static final String BRIDGE_DEBUG_KEY = "realmshark.bridge.debug";
    private static final String BRIDGE_LOG_PATH_KEY = "realmshark.bridge.local_review_log";

    private static final String DEFAULT_LOG_PATH = "realmshark_missing_utst_review.log";
    private static final BlockingQueue<String> SEND_QUEUE = new LinkedBlockingQueue<>();
    private static final Set<String> TRACKED_ITEMS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> MISSING_UTST_SEEN = Collections.synchronizedSet(new HashSet<>());
    private static final List<BridgeEventListener> BRIDGE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<BridgeLogListener> BRIDGE_LOG_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<BridgeLogEntry> BRIDGE_LOG_BUFFER = new ArrayList<>();
    private static final int MAX_BRIDGE_LOG_BUFFER = 800;

    private static volatile boolean trackedItemsLoaded = false;
    private static volatile boolean warnedMissingBridgeConfig = false;
    private static volatile String lastLoadedCsvPath = "";

    public static void addBridgeEventListener(BridgeEventListener listener) {
        if (listener != null) {
            BRIDGE_LISTENERS.add(listener);
        }
    }

    public static void addBridgeLogListener(BridgeLogListener listener, boolean replayRecent) {
        if (listener == null) return;

        BRIDGE_LOG_LISTENERS.add(listener);
        if (!replayRecent) return;

        List<BridgeLogEntry> snapshot;
        synchronized (BRIDGE_LOG_BUFFER) {
            snapshot = new ArrayList<>(BRIDGE_LOG_BUFFER);
        }

        for (BridgeLogEntry entry : snapshot) {
            try {
                listener.onBridgeLog(entry);
            } catch (Exception ignored) {
            }
        }
    }

    public static void logExternalInfo(String message) {
        if (message == null || message.trim().isEmpty()) return;
        info(message.trim());
    }

    private static void publishBridgeLog(String level, String message) {
        BridgeLogEntry entry = new BridgeLogEntry(Instant.now().toString(), level, message);

        synchronized (BRIDGE_LOG_BUFFER) {
            BRIDGE_LOG_BUFFER.add(entry);
            if (BRIDGE_LOG_BUFFER.size() > MAX_BRIDGE_LOG_BUFFER) {
                BRIDGE_LOG_BUFFER.remove(0);
            }
        }

        for (BridgeLogListener listener : BRIDGE_LOG_LISTENERS) {
            try {
                listener.onBridgeLog(entry);
            } catch (Exception ignored) {
            }
        }
    }

    private static void info(String s) {
        publishBridgeLog("INFO", s);
    }

    private static void warn(String s) {
        publishBridgeLog("WARN", s);
        System.out.println("[RealmSharkBridge] " + s);
    }

    private static String tokenPreview(String token) {
        if (token == null) return "";
        if (token.length() <= 10) return token;
        return token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

    private static void logPayloadParts(String endpoint, String jsonPayload) {
        String oneLine = jsonPayload == null
            ? ""
            : jsonPayload.replaceAll("\\s+", " ").trim();

        if (oneLine.isEmpty()) {
            return;
        }

        debug("Payload: " + oneLine);
    }

    private static void publishBridgeEvent(String itemName, String dungeon, boolean sent) {
        BridgeEvent event = new BridgeEvent(Instant.now().toString(), itemName, dungeon, sent);
        for (BridgeEventListener listener : BRIDGE_LISTENERS) {
            try {
                listener.onBridgeEvent(event);
            } catch (Exception ignored) {
            }
        }
    }

    static {
        Thread sender = new Thread(() -> {
            while (true) {
                try {
                    String payload = SEND_QUEUE.take();
                    postToIngest(payload);
                } catch (Exception e) {
                    warn("Bridge sender loop error: " + e.getMessage());
                    debug("Bridge sender loop error: " + e.getMessage());
                }
            }
        }, "realmshark-bot-bridge");
        sender.setDaemon(true);
        sender.start();
    }

    private static void debug(String s) {
        if (asBool(PropertiesManager.getProperty(BRIDGE_DEBUG_KEY), false)) {
            publishBridgeLog("DEBUG", s);
            System.out.println("[RealmSharkBridge] " + s);
        }
    }

    private static boolean asBool(String raw, boolean fallback) {
        if (raw == null) return fallback;
        String s = raw.trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("yes") || s.equals("on");
    }

    private static String normalizedName(String name) {
        if (name == null) return "";
        String s = name;
        s = s.replace('\u2018', '\'');
        s = s.replace('\u2019', '\'');
        s = s.replace('\u02bc', '\'');
        s = s.replace('\u2032', '\'');
        s = s.replace('\u00b4', '\'');
        s = s.replace('`', '\'');
        s = s.replace('\u2010', '-');
        s = s.replace('\u2011', '-');
        s = s.replace('\u2012', '-');
        s = s.replace('\u2013', '-');
        s = s.replace('\u2014', '-');
        s = s.replace('\u2015', '-');
        s = s.replace('\u2212', '-');
        s = s.replaceAll("\\s*-\\s*", "-");
        s = s.replaceAll("\\s+", " ");
        return s.trim().toLowerCase();
    }

    private static int indexOfHeader(String[] headers, String target) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null && headers[i].trim().equalsIgnoreCase(target)) {
                return i;
            }
        }
        return -1;
    }

    private static String[] splitCsv(String line) {
        return line.split(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)", -1);
    }

    private static String unquote(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1).replace("\"\"", "\"").trim();
        }
        return s;
    }

    private static synchronized void loadTrackedItemsIfNeeded() {
        if (trackedItemsLoaded) return;

        info("Loading tracked items from CSV...");

        String configuredPath = PropertiesManager.getProperty(BRIDGE_CSV_PATH_KEY);
        String[] candidates;

        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            candidates = new String[] { configuredPath.trim() };
            info("CSV path override configured: " + configuredPath.trim());
        } else {
            candidates = new String[] {
                "rotmg_loot_drops_updated.csv",
                "../rotmgppebot/rotmg_loot_drops_updated.csv",
                "./rotmgppebot/rotmg_loot_drops_updated.csv"
            };
            info("CSV path not configured; trying fallback paths.");
        }

        for (String candidate : candidates) {
            try {
                Path p = Paths.get(candidate).normalize();
                info("Trying CSV: " + p.toString());
                if (!Files.exists(p)) {
                    info("CSV not found: " + p.toString());
                    continue;
                }

                try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    String header = br.readLine();
                    if (header == null) {
                        warn("CSV is empty: " + p.toString());
                        continue;
                    }

                    String[] headers = splitCsv(header);
                    int itemIdx = indexOfHeader(headers, "Item Name");
                    if (itemIdx < 0) {
                        warn("CSV missing required header 'Item Name': " + p.toString());
                        continue;
                    }

                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;
                        String[] cols = splitCsv(line);
                        if (itemIdx >= cols.length) continue;

                        String itemName = unquote(cols[itemIdx]);
                        String normalized = normalizedName(itemName);
                        if (!normalized.isEmpty()) {
                            TRACKED_ITEMS.add(normalized);
                        }
                    }
                }

                trackedItemsLoaded = true;
                lastLoadedCsvPath = p.toString();
                info("Loaded " + TRACKED_ITEMS.size() + " tracked items from " + p.toString());
                debug("Loaded " + TRACKED_ITEMS.size() + " tracked items from " + p.toString());
                return;
            } catch (Exception e) {
                warn("Failed loading csv from " + candidate + ": " + e.getMessage());
                debug("Failed loading csv from " + candidate + ": " + e.getMessage());
            }
        }

        trackedItemsLoaded = true;
        lastLoadedCsvPath = "";
        warn("Could not load rotmg_loot_drops_updated.csv, bridge will not send tracked items.");
    }

    public static synchronized void reloadTrackedItemsFromSettings() {
        TRACKED_ITEMS.clear();
        trackedItemsLoaded = false;
        warnedMissingBridgeConfig = false;
        lastLoadedCsvPath = "";

        info("Bridge settings saved. Reloading tracked CSV now...");
        loadTrackedItemsIfNeeded();

        if (TRACKED_ITEMS.isEmpty()) {
            warn("CSV reload completed but no tracked items are available.");
        } else {
            info(
                "CSV reload completed. Tracked items: " +
                TRACKED_ITEMS.size() +
                " | source: " +
                lastLoadedCsvPath
            );
        }
    }

    public static void sendSettingsConfirmationPing() {
        if (!asBool(PropertiesManager.getProperty(BRIDGE_ENABLED_KEY), false)) {
            warn("Bridge settings test not sent because bridge is disabled.");
            return;
        }

        if (!hasRequiredBridgeConfig()) {
            warn("Bridge settings test not sent because required bridge config is missing.");
            return;
        }

        String guildRaw = PropertiesManager.getProperty(BRIDGE_GUILD_ID_KEY);
        long guildId;
        try {
            guildId = Long.parseLong(guildRaw.trim());
        } catch (Exception ignored) {
            warn("Bridge settings test not sent: invalid guild id " + guildRaw);
            return;
        }

        String token = PropertiesManager.getProperty(BRIDGE_LINK_TOKEN_KEY).trim();

        JsonObject payload = new JsonObject();
        payload.addProperty("guild_id", guildId);
        payload.addProperty("link_token", token);
        payload.addProperty("event_type", "bridge_settings_test");
        payload.addProperty("source", "tomato");

        boolean queued = SEND_QUEUE.offer(payload.toString());
        info(
            "Queued bridge settings test ping to ingest endpoint=" +
            PropertiesManager.getProperty(BRIDGE_ENDPOINT_KEY) +
            " guild_id=" +
            guildId +
            " token=" +
            tokenPreview(token) +
            " queued=" +
            queued +
            " queue_size=" +
            SEND_QUEUE.size()
        );
    }

    private static class ParsedItem {
        final String rawName;
        final String baseName;
        final boolean shiny;
        final boolean divine;

        ParsedItem(String rawName, String baseName, boolean shiny, boolean divine) {
            this.rawName = rawName;
            this.baseName = baseName;
            this.shiny = shiny;
            this.divine = divine;
        }
    }

    private static boolean hasMetadataToken(int itemId, String token) {
        if (token == null || token.isEmpty()) return false;

        String label = IdToAsset.getIdLabel(itemId);
        String group = IdToAsset.getIdGroup(itemId);

        String combined = "";
        if (label != null) combined += label + " ";
        if (group != null) combined += group;

        String upper = combined.toUpperCase();
        String[] tokens = upper.split("[^A-Z0-9]+");
        for (String candidate : tokens) {
            if (candidate.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nameHasToken(String name, String token) {
        if (name == null || token == null || token.isEmpty()) return false;

        String[] tokens = name.toUpperCase().split("[^A-Z0-9]+");
        for (String candidate : tokens) {
            if (candidate.equals(token)) {
                return true;
            }
        }
        return false;
    }

    private static ParsedItem parseItemName(String rawName, int itemId) {
        if (rawName == null) return new ParsedItem("", "", false, false);

        String trimmed = rawName.trim();
        String lower = trimmed.toLowerCase();
        String shinySuffix = "(shiny)";
        String base = trimmed;
        boolean shinyFromNameSuffix = false;
        if (lower.endsWith(shinySuffix)) {
            base = trimmed.substring(0, trimmed.length() - shinySuffix.length()).trim();
            shinyFromNameSuffix = true;
        }

        base = stripRarityDecorators(base);

        boolean shiny = shinyFromNameSuffix || hasMetadataToken(itemId, "SHINY");
        boolean divine = hasMetadataToken(itemId, "DIVINE") || nameHasToken(base, "DIVINE");
        return new ParsedItem(trimmed, base, shiny, divine);
    }

    private static String stripRarityDecorators(String name) {
        if (name == null) return "";

        String s = name.trim();
        if (s.isEmpty()) return s;

        // Handle forms like "Rare Corsair Ring" and "Uncommon: Corsair Ring".
        s = s.replaceFirst("(?i)^\\s*(common|uncommon|rare|legendary|divine)\\s*[:-]?\\s+", "");

        // Handle forms like "Corsair Ring (Rare)".
        s = s.replaceFirst("(?i)\\s*\\((common|uncommon|rare|legendary|divine)\\)\\s*$", "");

        return s.trim();
    }

    private static boolean isRarityToken(String token) {
        return token.equals("COMMON")
            || token.equals("UNCOMMON")
            || token.equals("RARE")
            || token.equals("LEGENDARY")
            || token.equals("DIVINE");
    }

    private static String[] metadataTokens(int itemId) {
        String label = IdToAsset.getIdLabel(itemId);
        String group = IdToAsset.getIdGroup(itemId);

        String combined = "";
        if (label != null) combined += label + " ";
        if (group != null) combined += group;

        return combined.toUpperCase().split("[^A-Z0-9]+");
    }

    private static String metadataRaw(int itemId) {
        String label = IdToAsset.getIdLabel(itemId);
        String group = IdToAsset.getIdGroup(itemId);

        String combined = "";
        if (label != null) combined += label + " ";
        if (group != null) combined += group;
        return combined.toUpperCase(Locale.ROOT);
    }

    private static class RarityResolution {
        final String rarity;
        final String source;
        final int enchantCount;

        RarityResolution(String rarity, String source, int enchantCount) {
            this.rarity = rarity;
            this.source = source;
            this.enchantCount = enchantCount;
        }
    }

    private static String[] splitBagUniqueData(Entity bag) {
        if (bag == null) return new String[0];

        StatData udata = bag.stat.get(StatType.UNIQUE_DATA_STRING);
        if (udata == null || udata.stringStatValue == null || udata.stringStatValue.isEmpty()) {
            return new String[0];
        }

        return udata.stringStatValue.split(",", -1);
    }

    private static int enchantCountFromEncoded(String encoded) {
        if (encoded == null || encoded.isEmpty() || encoded.equals("AAIE_f_9__3__f8=")) {
            return 0;
        }

        String parsed = ParseEnchants.parse(encoded);
        if (parsed == null || parsed.isEmpty()) {
            return 0;
        }

        String normalized = parsed.trim();
        if (normalized.isEmpty() || normalized.equalsIgnoreCase("empty") || normalized.equalsIgnoreCase("[locked]")) {
            return 0;
        }

        int count = 0;
        for (String line : normalized.split("\\R")) {
            if (!line.trim().isEmpty()) {
                count++;
            }
        }

        return count;
    }

    private static int resolveEnchantCount(Entity bag, int slotIndex) {
        String[] encodedSlots = splitBagUniqueData(bag);
        if (slotIndex >= 0 && slotIndex < encodedSlots.length) {
            return enchantCountFromEncoded(encodedSlots[slotIndex]);
        }
        return 0;
    }

    private static String rarityFromGlowCount(int enchantCount) {
        // Matches requested mapping: 0 common, 1 uncommon, 2 rare, 3 legendary, 4 divine.
        switch (enchantCount) {
            case 0:
                return "common";
            case 1:
                return "uncommon";
            case 2:
                return "rare";
            case 3:
                return "legendary";
            case 4:
                return "divine";
            default:
                return null;
        }
    }

    private static RarityResolution resolveItemRarity(int itemId, Entity bag, int slotIndex) {
        String[] tokens = metadataTokens(itemId);
        int enchantCount = resolveEnchantCount(bag, slotIndex);

        for (String token : tokens) {
            if (isRarityToken(token)) {
                return new RarityResolution(token.toLowerCase(Locale.ROOT), "metadata_token", enchantCount);
            }
        }

        String rarityFromGlow = rarityFromGlowCount(enchantCount);
        if (rarityFromGlow != null) {
            return new RarityResolution(rarityFromGlow, "enchant_count", enchantCount);
        }

        return new RarityResolution("unknown", "fallback_unknown_default", enchantCount);
    }

    private static boolean isTracked(ParsedItem item) {
        String normalizedRaw = normalizedName(item.rawName);
        if (TRACKED_ITEMS.contains(normalizedRaw)) {
            return true;
        }

        // Shiny items may not have explicit "(shiny)" rows in the tracked CSV.
        String normalizedBase = normalizedName(item.baseName);
        return TRACKED_ITEMS.contains(normalizedBase);
    }

    private static boolean labelsContainExactToken(String labels, String token) {
        if (labels == null || labels.isEmpty() || token == null || token.isEmpty()) {
            return false;
        }

        for (String labelToken : labels.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+")) {
            if (labelToken.equals(token)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isUtOrStItem(int itemId) {
        ParseEquipment.Equipment equipment = ParseEquipment.getEquipmentById(itemId);
        if (equipment != null && equipment.labels != null && !equipment.labels.isEmpty()) {
            boolean isConsumable = labelsContainExactToken(equipment.labels, "CONSUMABLE");
            boolean isUtSt = labelsContainExactToken(equipment.labels, "UT") || labelsContainExactToken(equipment.labels, "ST");
            return isUtSt && !isConsumable;
        }

        // Fallback when equipment XML data is unavailable for this id.
        // Keep this strict so consumables (e.g., UT consumables) are not logged as missing equipment rows.
        boolean isUtSt = hasMetadataToken(itemId, "UT") || hasMetadataToken(itemId, "ST");
        boolean isConsumable = hasMetadataToken(itemId, "CONSUMABLE");
        boolean isEquipment = hasMetadataToken(itemId, "EQUIPMENT");
        return isUtSt && isEquipment && !isConsumable;
    }

    private static boolean noteMissingUtStLocally(int itemId, ParsedItem item, String dungeon) {
        String key = itemId + "|" + normalizedName(item.rawName);
        if (!MISSING_UTST_SEEN.add(key)) {
            return false;
        }

        String logPath = PropertiesManager.getProperty(BRIDGE_LOG_PATH_KEY);
        if (logPath == null || logPath.trim().isEmpty()) {
            logPath = DEFAULT_LOG_PATH;
        }

        String label = IdToAsset.getIdLabel(itemId);
        String group = IdToAsset.getIdGroup(itemId);

        String line =
            Instant.now().toString() +
            " | review=missing_utst_from_csv" +
            " | item_id=" + itemId +
            " | item_name=" + item.rawName +
            " | item_group=" + (group == null ? "" : group) +
            " | item_label=" + (label == null ? "" : label) +
            " | dungeon=" + (dungeon == null ? "" : dungeon) +
            System.lineSeparator();

        try {
            Path p = Paths.get(logPath);
            Path parent = p.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(
                p,
                line.getBytes(StandardCharsets.UTF_8),
                Files.exists(p) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE
            );
        } catch (Exception e) {
            warn("Failed to write local UT/ST review log: " + e.getMessage());
            debug("Failed to write local UT/ST review log: " + e.getMessage());
        }

        return true;
    }

    private static boolean hasRequiredBridgeConfig() {
        String endpoint = PropertiesManager.getProperty(BRIDGE_ENDPOINT_KEY);
        String guild = PropertiesManager.getProperty(BRIDGE_GUILD_ID_KEY);
        String token = PropertiesManager.getProperty(BRIDGE_LINK_TOKEN_KEY);

        boolean ok = endpoint != null && !endpoint.trim().isEmpty()
            && guild != null && !guild.trim().isEmpty()
            && token != null && !token.trim().isEmpty();

        if (!ok && !warnedMissingBridgeConfig) {
            warnedMissingBridgeConfig = true;
            warn("Missing bridge config. Set realmshark.bridge.endpoint, realmshark.bridge.guild_id, and realmshark.bridge.link_token.");
        }

        return ok;
    }

    private static void enqueueTrackedItem(
        TomatoData data,
        Entity player,
        int itemId,
        ParsedItem item,
        Entity bag,
        int slotIndex,
        boolean seasonal,
        boolean lootDrop,
        String dungeon
    ) {
        if (!hasRequiredBridgeConfig()) return;

        String guildRaw = PropertiesManager.getProperty(BRIDGE_GUILD_ID_KEY);
        long guildId;
        try {
            guildId = Long.parseLong(guildRaw.trim());
        } catch (Exception ignored) {
            warn("Invalid guild id: " + guildRaw);
            debug("Invalid guild id: " + guildRaw);
            return;
        }

        String token = PropertiesManager.getProperty(BRIDGE_LINK_TOKEN_KEY).trim();

        JsonObject payload = new JsonObject();
        payload.addProperty("guild_id", guildId);
        payload.addProperty("link_token", token);
        payload.addProperty("item_name", item.baseName);
        payload.addProperty("shiny", item.shiny);
        payload.addProperty("item_id", itemId);

        int characterId = -1;
        if (data != null) {
            characterId = data.getCharId();
        }
        if (characterId > 0) {
            payload.addProperty("character_id", characterId);
        }

        if (player != null) {
            String characterName = player.name();
            if (characterName != null && !characterName.isEmpty()) {
                payload.addProperty("character_name", characterName);
            }

            if (CharacterClass.isPlayerCharacter(player.objectType)) {
                String characterClass = CharacterClass.getName(player.objectType);
                if (characterClass != null && !characterClass.isEmpty()) {
                    payload.addProperty("character_class", characterClass);
                }
            }
        }

        String group = IdToAsset.getIdGroup(itemId);
        if (group != null && !group.isEmpty()) payload.addProperty("item_group", group);

        String label = IdToAsset.getIdLabel(itemId);
        if (label != null && !label.isEmpty()) payload.addProperty("item_label", label);

        RarityResolution rarity = resolveItemRarity(itemId, bag, slotIndex);
        payload.addProperty("item_rarity", rarity.rarity);

        boolean divineFromItemFlag = item.divine;
        boolean divineFromRarity = "divine".equals(rarity.rarity);
        boolean divineResolved = divineFromItemFlag || divineFromRarity;
        String divineSource;
        if (divineFromItemFlag && divineFromRarity) {
            divineSource = "item_and_rarity";
        } else if (divineFromItemFlag) {
            divineSource = "item_flag";
        } else if (divineFromRarity) {
            divineSource = "rarity";
        } else {
            divineSource = "none";
        }

        payload.addProperty("divine", divineResolved);

        if (dungeon != null && !dungeon.isEmpty()) payload.addProperty("dungeon", dungeon);
        payload.addProperty("is_seasonal", seasonal);
        payload.addProperty("loot_drop_bonus", lootDrop);
        payload.addProperty("source", "tomato");

        info(
            "Prepared tracked payload: " +
            "item_name=" + item.baseName +
            " | item_id=" + itemId +
            " | rarity=" + rarity.rarity +
            " | rarity_source=" + rarity.source +
            " | enchant_count=" + rarity.enchantCount +
            " | shiny=" + item.shiny +
            " | divine=" + divineResolved +
            " | divine_source=" + divineSource
        );

        boolean queued = SEND_QUEUE.offer(payload.toString());
        info(
            "Queued tracked item: " +
            item.rawName +
            " (id=" + itemId + ")" +
            (dungeon == null || dungeon.isEmpty() ? "" : " in " + dungeon) +
            " | rarity=" + rarity.rarity +
            " | divine=" + divineResolved +
            " | queued=" +
            queued +
            " queue_size=" +
            SEND_QUEUE.size()
        );
    }

    private static void postToIngest(String jsonPayload) {
        String endpoint = PropertiesManager.getProperty(BRIDGE_ENDPOINT_KEY);
        if (endpoint == null || endpoint.trim().isEmpty()) {
            warn("Bridge endpoint is empty; skipping payload send.");
            return;
        }

        logPayloadParts(endpoint, jsonPayload);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint.trim());
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(2500);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            byte[] out = jsonPayload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(out.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                warn("Ingest rejected with HTTP " + status);
                debug("Ingest rejected with HTTP " + status);
            } else {
                info("Ingest accepted with HTTP " + status);
                debug("Ingest accepted with HTTP " + status);
            }
        } catch (Exception e) {
            warn("Ingest post failed: " + e.getMessage());
            debug("Ingest post failed: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Kept for compatibility with existing call sites.
     */
    public static void beginLootTick(int tickSeed) {
        // No-op in HTTP mode.
    }

    /**
     * Kept for compatibility with existing call sites.
     */
    public static void flushPendingOverflow() {
        // No-op in HTTP mode.
    }

    public static void sendLoot(
        TomatoData data,
        MapInfoPacket map,
        Entity bag,
        Entity dropper,
        Entity player,
        long time
    ) {
        if (!asBool(PropertiesManager.getProperty(BRIDGE_ENABLED_KEY), false)) {
            info("Bridge is disabled; skipping loot processing for this bag.");
            return;
        }

        if (bag == null) {
            info("Loot processing skipped: bag entity is null.");
            return;
        }

        String dungeon = map != null ? map.name : "";

        loadTrackedItemsIfNeeded();
        boolean trackedAvailable = !TRACKED_ITEMS.isEmpty();
        if (!trackedAvailable) {
            warn("Tracked item set is empty; will log observed loot but cannot queue tracked sends.");
        }

        boolean isSeasonal = false;
        boolean lootDrop = false;

        if (player != null) {
            lootDrop = player.lootDropTime(time) > 0;
            StatData sesn = player.stat.get(StatType.SEASONAL.get());
            if (sesn != null && sesn.statValue == 1) {
                isSeasonal = true;
            }
        }

        for (int i = 0; i < 8; i++) {
            StatData sd = bag.stat.get(StatType.INVENTORY_0_STAT.get() + i);
            if (sd == null || sd.statValue < 1) continue;

            int itemId = sd.statValue;
            String itemName = IdToAsset.objectName(itemId);
            if (itemName == null || itemName.trim().isEmpty()) {
                info("Slot " + i + " has unknown/empty item name for id=" + itemId + ".");
                continue;
            }

            ParsedItem parsed = parseItemName(itemName, itemId);
            boolean tracked = trackedAvailable && isTracked(parsed);
            boolean utOrSt = isUtOrStItem(itemId);

            if (tracked) {
                info("Sending " + parsed.rawName + " to PPE Bot.");
                enqueueTrackedItem(data, player, itemId, parsed, bag, i, isSeasonal, lootDrop, dungeon);
                publishBridgeEvent(parsed.rawName, dungeon, true);
                continue;
            }

            if (utOrSt) {
                if (noteMissingUtStLocally(itemId, parsed, dungeon)) {
                    info("Item name not in CSV, contact Admin if you believe it should be.");
                    publishBridgeEvent(parsed.rawName, dungeon, false);
                } else {
                    debug("UT/ST missing from CSV already noted earlier: " + parsed.rawName + " (id=" + itemId + ")");
                }
            }
        }
    }
}
