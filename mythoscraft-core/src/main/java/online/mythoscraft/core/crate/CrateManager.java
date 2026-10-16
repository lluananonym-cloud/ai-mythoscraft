package online.mythoscraft.core.crate;

import net.kyori.adventure.text.Component;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.ConfigProblems;
import online.mythoscraft.core.config.ConfigReader;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.display.Glyphs;
import online.mythoscraft.core.reward.Reward;
import online.mythoscraft.core.util.ItemBuilder;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.Text;
import online.mythoscraft.core.util.WeightedRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CrateManager {

    private final MythosCraft plugin;
    private final YamlStore definitions;
    private final YamlStore placedStore;
    private final Map<String, CrateType> types = new LinkedHashMap<>();
    private final Map<String, PlacedCrate> placed = new LinkedHashMap<>();
    private final Map<Long, List<PlacedCrate>> byChunk = new HashMap<>();
    private final Map<UUID, CrateOpening> openings = new HashMap<>();
    private List<String> hologram = List.of();
    private boolean bulkOpen = true;

    public CrateManager(MythosCraft plugin) {
        this.plugin = plugin;
        this.definitions = new YamlStore(plugin, "crates.yml");
        this.placedStore = new YamlStore(plugin, "data/placed-crates.yml");
    }

    public void reload(ConfigProblems problems) {
        despawnAll();
        YamlConfiguration yaml = definitions.load(true);
        ConfigReader root = new ConfigReader(yaml, "crates.yml", problems);
        hologram = root.strings("hologram");
        bulkOpen = root.bool("bulk-open", true);
        types.clear();
        rotationSize.clear();
        rewardSeason.clear();
        todayCache.clear();
        defaultRotation = root.integer("rotation-size", 16, 0, 1000);
        ConfigReader crates = root.sub("crates");
        for (String id : crates.keys()) {
            CrateType type = parseType(id.toLowerCase(Locale.ROOT), crates.sub(id));
            if (type.table().isEmpty()) {
                crates.warn(id, "Kiste hat keine gültigen Belohnungen – wird ignoriert");
                continue;
            }
            types.put(type.id(), type);
        }
        loadPlaced();
        dedupe();
        spawnLoaded();
        plugin.tasks().cancel(this);
        plugin.tasks().repeat(this, 20, 20, "crate ambience", this::ambience);
        plugin.getLogger().info("Loaded " + types.size() + " crate types, " + placed.size() + " placed crates");
    }

    // ---- daily rotation ------------------------------------------------------------------------------------

    private final Map<String, Integer> rotationSize = new java.util.HashMap<>();
    private final Map<CrateType.CrateReward, String> rewardSeason = new java.util.IdentityHashMap<>();
    private final Map<String, WeightedRandom<CrateType.CrateReward>> todayCache = new java.util.HashMap<>();
    private int defaultRotation = 16;

    /**
     * Today's rewards of a crate: crates with a big pool show a daily changing selection of the same size (coins, gems
     * and XP always stay), season-bound rewards only appear while their season is active.
     */
    public WeightedRandom<CrateType.CrateReward> table(CrateType type) {
        long day = plugin.multipliers().today().toEpochDay();
        online.mythoscraft.core.item.SeasonService.Season season = plugin.seasons() == null ? null : plugin.seasons().active();
        String sid = season == null ? "" : season.id();
        String key = type.id() + "|" + day + "|" + sid;
        WeightedRandom<CrateType.CrateReward> cached = todayCache.get(key);
        if (cached != null) {
            return cached;
        }
        if (todayCache.size() > 200) {
            todayCache.clear();
        }
        WeightedRandom<CrateType.CrateReward> table = new WeightedRandom<>(rotation(type, day, sid), CrateType.CrateReward::weight);
        if (table.isEmpty()) {
            table = type.table();
        }
        todayCache.put(key, table);
        return table;
    }

    List<CrateType.CrateReward> rotation(CrateType type, long day, String season) {
        List<CrateType.CrateReward> pool = new ArrayList<>();
        for (CrateType.CrateReward r : type.rewards()) {
            String s = rewardSeason.get(r);
            if (s == null || s.equalsIgnoreCase(season)) {
                pool.add(r);
            }
        }
        int size = rotationSize.getOrDefault(type.id(), defaultRotation);
        if (size <= 0 || pool.size() <= size) {
            return pool;
        }
        List<CrateType.CrateReward> fixed = new ArrayList<>();
        List<CrateType.CrateReward> rest = new ArrayList<>();
        for (CrateType.CrateReward r : pool) {
            switch (r.reward().type()) {
                case COINS, GEMS, XP -> fixed.add(r);
                default -> rest.add(r);
            }
        }
        java.util.Collections.shuffle(rest, new java.util.Random(type.id().hashCode() * 31L + day * 7919L + season.hashCode()));
        // keep at least one reward of every rarity that exists in the pool
        List<CrateType.CrateReward> picked = new ArrayList<>(fixed);
        java.util.EnumSet<Rarity> seen = java.util.EnumSet.noneOf(Rarity.class);
        picked.forEach(r -> seen.add(r.rarity()));
        for (CrateType.CrateReward r : rest) {
            if (picked.size() < size && !seen.contains(r.rarity())) {
                picked.add(r);
                seen.add(r.rarity());
            }
        }
        for (CrateType.CrateReward r : rest) {
            if (picked.size() >= size) {
                break;
            }
            if (!picked.contains(r)) {
                picked.add(r);
            }
        }
        List<CrateType.CrateReward> ordered = new ArrayList<>();
        for (CrateType.CrateReward r : type.rewards()) {
            if (picked.contains(r)) {
                ordered.add(r);
            }
        }
        return ordered;
    }

    private CrateType parseType(String id, ConfigReader c) {
        Color color;
        try {
            color = Color.fromRGB(Integer.parseInt(c.string("color", "#C084FC").replace("#", ""), 16));
        } catch (NumberFormatException e) {
            c.warn("color", "ungültige Farbe");
            color = Color.fromRGB(0xC084FC);
        }
        List<CrateType.CrateReward> rewards = new ArrayList<>();
        List<?> list = c.raw().getList("rewards");
        if (list != null) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> map)) {
                    c.warn("rewards", "Eintrag muss {reward: ..., weight: ...} sein");
                    continue;
                }
                try {
                    Reward reward = Reward.parse(String.valueOf(map.get("reward")));
                    double weight = map.get("weight") instanceof Number n ? n.doubleValue() : 1;
                    Rarity rarity = map.get("rarity") == null ? Rarity.COMMON
                            : Rarity.valueOf(String.valueOf(map.get("rarity")).toUpperCase(Locale.ROOT));
                    String name = map.get("name") == null ? null : String.valueOf(map.get("name"));
                    Material icon = map.get("icon") == null ? null : Material.matchMaterial(String.valueOf(map.get("icon")));
                    CrateType.CrateReward cr = new CrateType.CrateReward(reward, weight, rarity, name, icon);
                    rewards.add(cr);
                    if (map.get("season") != null) {
                        rewardSeason.put(cr, String.valueOf(map.get("season")).toLowerCase(Locale.ROOT));
                    }
                } catch (IllegalArgumentException e) {
                    c.warn("rewards", e.getMessage());
                }
            }
        }
        rotationSize.put(id, c.integer("rotation-size", defaultRotation, 0, 1000));
        return new CrateType(id, c.string("name", id), c.string("key-name", id + " Schlüssel"), c.strings("key-lore"),
                c.string("model", "crate_" + id), c.string("key-model", "key_" + id), color,
                c.enumValue("broadcast-from", Rarity.class, Rarity.EPIC), rewards,
                new WeightedRandom<>(rewards, CrateType.CrateReward::weight));
    }

    private void loadPlaced() {
        placed.clear();
        byChunk.clear();
        YamlConfiguration yaml = placedStore.load(false);
        ConfigurationSection sec = yaml.getConfigurationSection("crates");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            String type = sec.getString(id + ".type");
            Location loc = LocationCodec.fromString(sec.getString(id + ".location"));
            if (type == null || loc == null) {
                plugin.getLogger().warning("Placed crate " + id + " skipped (world missing or entry broken)");
                continue;
            }
            index(new PlacedCrate(id, type.toLowerCase(Locale.ROOT), loc, (float) sec.getDouble(id + ".yaw")));
        }
    }

    private void index(PlacedCrate crate) {
        placed.put(crate.id(), crate);
        byChunk.computeIfAbsent(crate.chunkKey(), k -> new ArrayList<>()).add(crate);
    }

    private void savePlaced() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlacedCrate c : placed.values()) {
            yaml.set("crates." + c.id() + ".type", c.type());
            yaml.set("crates." + c.id() + ".location", LocationCodec.toString(c.location()));
            yaml.set("crates." + c.id() + ".yaw", c.yaw());
        }
        placedStore.replace(yaml);
        placedStore.saveAsync(plugin.tasks());
    }

    // ---- types & keys -------------------------------------------------------------------------------------

    public Collection<CrateType> types() {
        return types.values();
    }

    public CrateType type(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    public Component displayName(String id) {
        CrateType t = type(id);
        return t == null ? Component.text(id) : Text.parse(t.name());
    }

    public ItemStack createKey(String crateId, int amount) {
        CrateType type = type(crateId);
        if (type == null) {
            return null;
        }
        return new ItemBuilder(Material.TRIPWIRE_HOOK, amount)
                .name(type.keyName())
                .lore(type.keyLore())
                .model(type.keyModel())
                .glint(true)
                .rarity(ItemRarity.EPIC)
                .tag(Keys.CRATE, type.id())
                .build();
    }

    private static final org.bukkit.NamespacedKey TEST_KEY = new org.bukkit.NamespacedKey(Keys.NAMESPACE, "test_key");
    /** players opening a crate with a test key: they see everything, but get no reward */
    private final java.util.Set<java.util.UUID> testers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Key for the team to test a crate: plays the whole opening, gives nothing. */
    public ItemStack createTestKey(String crateId, int amount) {
        ItemStack key = createKey(crateId, amount);
        if (key == null) {
            return null;
        }
        key.editMeta(m -> {
            m.getPersistentDataContainer().set(TEST_KEY, PersistentDataType.BYTE, (byte) 1);
            m.displayName(net.kyori.adventure.text.Component.text("[TEST] ", net.kyori.adventure.text.format.NamedTextColor.RED)
                    .append(java.util.Objects.requireNonNull(m.displayName())));
        });
        return key;
    }

    public static boolean isTestKey(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(TEST_KEY, PersistentDataType.BYTE);
    }

    public String keyType(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return item.getPersistentDataContainer().get(Keys.CRATE, PersistentDataType.STRING);
    }

    /** Item showing the whole crate model, used as icon in menus. */
    public ItemStack crateIcon(CrateType type) {
        return new ItemBuilder(Material.BARREL).name(type.name()).model(type.model()).build();
    }

    // ---- placing ------------------------------------------------------------------------------------------

    public PlacedCrate place(CrateType type, Location block, float yaw) {
        Location loc = block.toBlockLocation();
        PlacedCrate crate = new PlacedCrate(UUID.randomUUID().toString().substring(0, 8), type.id(), loc, yaw);
        index(crate);
        savePlaced();
        if (loc.isChunkLoaded()) {
            spawn(crate);
        }
        return crate;
    }

    public PlacedCrate nearest(Location loc, double maxDistance) {
        PlacedCrate best = null;
        double bestDist = maxDistance * maxDistance;
        for (PlacedCrate c : placed.values()) {
            if (!c.location().getWorld().equals(loc.getWorld())) {
                continue;
            }
            double d = c.location().clone().add(0.5, 0.5, 0.5).distanceSquared(loc);
            if (d <= bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /** Removes every placed crate (new spawn: the spawn places its own again – there are no crates anywhere else). */
    public void removeAll() {
        for (PlacedCrate c : new java.util.ArrayList<>(placed.values())) {
            c.despawn();
        }
        placed.clear();
        byChunk.clear();
        savePlaced();
    }

    /** Two crates on the same block (left over from older spawn builds) → keep one. */
    private void dedupe() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        boolean changed = false;
        for (PlacedCrate c : new java.util.ArrayList<>(placed.values())) {
            Location l = c.location();
            if (l == null || l.getWorld() == null) {
                continue;
            }
            if (!seen.add(l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ())) {
                c.despawn();
                placed.remove(c.id());
                List<PlacedCrate> list = byChunk.get(c.chunkKey());
                if (list != null) {
                    list.remove(c);
                }
                changed = true;
            }
        }
        if (changed) {
            plugin.getLogger().info("Removed duplicate crates on the same block");
            savePlaced();
        }
    }

    public void remove(PlacedCrate crate) {
        crate.despawn();
        placed.remove(crate.id());
        List<PlacedCrate> list = byChunk.get(crate.chunkKey());
        if (list != null) {
            list.remove(crate);
        }
        savePlaced();
    }

    public Collection<PlacedCrate> placed() {
        return placed.values();
    }

    public PlacedCrate byEntity(Entity entity) {
        String tag = entity.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
        if (tag == null || !tag.startsWith("crate:")) {
            return null;
        }
        return placed.get(tag.substring(6));
    }

    // ---- visuals ------------------------------------------------------------------------------------------

    /**
     * Removes crate visuals that belong to no crate any more (left over from old spawns / older plugin versions):
     * every crate-tagged entity that is not one of the current entities of an existing crate.
     */
    public int sweepOrphans(org.bukkit.entity.Entity[] entities) {
        int removed = 0;
        for (org.bukkit.entity.Entity e : entities) {
            String tag = e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
            if (tag == null || !tag.startsWith("crate:")) {
                continue;
            }
            PlacedCrate c = placed.get(tag.substring(6));
            if (c == null || !(e.equals(c.base) || e.equals(c.lid) || e.equals(c.hitbox) || e.equals(c.label))) {
                e.remove();
                removed++;
            }
        }
        return removed;
    }

    public void onChunkLoad(Chunk chunk) {
        sweepOrphans(chunk.getEntities());
        List<PlacedCrate> list = byChunk.get(PlacedCrate.chunkKey(chunk.getX(), chunk.getZ()));
        if (list == null) {
            return;
        }
        for (PlacedCrate c : list) {
            if (c.location().getWorld().equals(chunk.getWorld()) && !c.spawned()) {
                spawn(c);
            }
        }
    }

    public void onChunkUnload(Chunk chunk) {
        List<PlacedCrate> list = byChunk.get(PlacedCrate.chunkKey(chunk.getX(), chunk.getZ()));
        if (list != null) {
            for (PlacedCrate c : list) {
                if (c.location().getWorld().equals(chunk.getWorld())) {
                    c.despawn();
                }
            }
        }
    }

    private void spawnLoaded() {
        for (PlacedCrate c : placed.values()) {
            if (c.location().isChunkLoaded()) {
                spawn(c);
            }
        }
    }

    private void despawnAll() {
        placed.values().forEach(PlacedCrate::despawn);
        for (CrateOpening o : new ArrayList<>(openings.values())) {
            o.complete();
        }
    }

    private void spawn(PlacedCrate crate) {
        CrateType type = type(crate.type());
        if (type == null) {
            return;
        }
        crate.despawn();
        World world = crate.location().getWorld();
        Location center = crate.location().clone().add(0.5, 0.5, 0.5);
        center.setYaw(crate.yaw());
        String tag = "crate:" + crate.id();
        crate.base = world.spawn(center, ItemDisplay.class, d -> setupDisplay(d, type.model() + "_base", tag));
        crate.lid = world.spawn(center, ItemDisplay.class, d -> setupDisplay(d, type.model() + "_lid", tag));
        crate.hitbox = world.spawn(crate.location().clone().add(0.5, 0, 0.5), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.05f);
            i.setInteractionHeight(1.05f);
            i.setResponsive(true);
            i.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        crate.label = world.spawn(crate.location().clone().add(0.5, 1.55, 0.5), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0x40, 0, 0, 0));
            t.setViewRange(0.5f);
            t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        List<Component> lines = new ArrayList<>();
        for (String line : hologram) {
            lines.add(plugin.messages().format(online.mythoscraft.core.config.ConfigText.tr(line), "crate", Text.parse(type.name())));
        }
        crate.label.text(Component.join(net.kyori.adventure.text.JoinConfiguration.newlines(), lines));
    }

    private void setupDisplay(ItemDisplay d, String model, String tag) {
        d.setPersistent(false);
        d.setItemStack(new ItemBuilder(Material.BARREL).model(model).build());
        d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        d.setBillboard(Display.Billboard.FIXED);
        d.setViewRange(1.0f);
        d.setShadowRadius(0.6f);
        d.setShadowStrength(0.7f);
        d.setInterpolationDuration(0);
        d.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
    }

    /** Lid floats up and spins while a crate is being opened; lands again afterwards. */
    void animateLid(PlacedCrate crate, boolean open) {
        if (crate.lid == null || !crate.lid.isValid()) {
            return;
        }
        crate.lid.setInterpolationDelay(0);
        crate.lid.setInterpolationDuration(open ? 18 : 12);
        Transformation t = open
                ? new Transformation(new Vector3f(0, 0.55f, 0), new Quaternionf(new AxisAngle4f((float) Math.PI, 0, 1, 0)),
                new Vector3f(1f), new Quaternionf())
                : new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1f), new Quaternionf());
        crate.lid.setTransformation(t);
        crate.lid.setBrightness(open ? new Display.Brightness(15, 15) : null);
    }

    private void ambience() {
        for (PlacedCrate c : placed.values()) {
            if (!c.spawned()) {
                continue;
            }
            CrateType type = type(c.type());
            if (type == null) {
                continue;
            }
            Location center = c.location().clone().add(0.5, 0.6, 0.5);
            boolean near = false;
            for (Player p : center.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(center) < 20 * 20) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            Particle.DustOptions dust = new Particle.DustOptions(type.color(), 0.9f);
            double t = (System.currentTimeMillis() / 1000.0) % (Math.PI * 2);
            for (int i = 0; i < 6; i++) {
                double a = t + i * Math.PI / 3;
                center.getWorld().spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * 0.75, 0.2 + i * 0.1, Math.sin(a) * 0.75),
                        1, 0, 0, 0, 0, dust);
            }
            if (c.opening) {
                center.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 0.6, 0), 6, 0.2, 0.3, 0.2, 0.03);
            }
        }
    }

    // ---- opening ------------------------------------------------------------------------------------------

    public void open(Player player, PlacedCrate crate, CrateType type, boolean bulk) {
        CrateOpening running = openings.get(player.getUniqueId());
        if (running != null) {
            // an opening that is no longer shown (error, lag, closed client side) must never block the player
            if (running.age() < 15_000 && player.getOpenInventory().getTopInventory().getHolder(false) == running) {
                return;
            }
            openings.remove(player.getUniqueId());
            try {
                running.complete();
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Stuck crate opening of " + player.getName() + " cleaned up", e);
            }
            openings.remove(player.getUniqueId());
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!type.id().equals(keyType(hand))) {
            boolean buyable = plugin.shop().entries().stream().anyMatch(e -> e.reward().type() == online.mythoscraft.core.reward.Reward.Type.KEY
                    && e.reward().id().equalsIgnoreCase(type.id()));
            if (buyable) {
                plugin.messages().send(player, "crates.need-key-buy", "key", Text.parse(type.keyName()));
            } else if (type.id().equals("silvester")) {
                plugin.messages().send(player, "crates.need-key-wheel", "key", Text.parse(type.keyName()));
            } else if (plugin.crateCarousel().isRelic(type.id())) {
                plugin.messages().send(player, "crates.need-key-relic", "key", Text.parse(type.keyName()));
            } else {
                plugin.messages().send(player, "crates.need-key", "key", Text.parse(type.keyName()));
            }
            String held = keyType(hand);
            if (held != null && !held.equals(type.id())) {
                plugin.crateCarousel().hint(player, held);
            }
            player.setVelocity(player.getLocation().getDirection().multiply(-0.4).setY(0.25));
            online.mythoscraft.core.util.SoundSpec.of("entity.villager.no", 1f, 1f).play(player);
            return;
        }
        if (plugin.playerData().get(player) == null) {
            plugin.messages().send(player, "general.data-loading");
            return;
        }
        if (isTestKey(hand)) {
            testers.add(player.getUniqueId());
        } else {
            testers.remove(player.getUniqueId());
        }
        if (bulk && bulkOpen && hand.getAmount() > 1) {
            int amount = hand.getAmount();
            player.getInventory().setItemInMainHand(null);
            for (int i = 0; i < amount; i++) {
                CrateType.CrateReward reward = table(type).pick(java.util.concurrent.ThreadLocalRandom.current());
                grant(player, crate, type, reward, true);
            }
            plugin.messages().send(player, "crates.bulk-opened", "amount", amount, "crate", Text.parse(type.name()));
            return;
        }
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        CrateOpening opening;
        try {
            opening = new CrateOpening(plugin, this, player, crate, type);
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Crate " + type.id() + " could not be opened", e);
            player.getInventory().addItem(createKey(type.id(), 1));
            plugin.messages().send(player, "general.error");
            return;
        }
        openings.put(player.getUniqueId(), opening);
        crate.opening = true;
        animateLid(crate, true);
        try {
            opening.start();
        } catch (RuntimeException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Crate opening of " + type.id() + " failed – reward given directly", e);
            openings.remove(player.getUniqueId());
            crate.opening = false;
            animateLid(crate, false);
            opening.complete();
        }
    }

    void finished(CrateOpening opening, PlacedCrate crate) {
        openings.remove(opening.player().getUniqueId());
        if (crate != null) {
            crate.opening = openings.values().stream().anyMatch(o -> o.crate() == crate);
            if (!crate.opening) {
                animateLid(crate, false);
            }
        }
    }

    /** Gives one crate reward (shared by animation and bulk opening). */
    void grant(Player player, PlacedCrate crate, CrateType type, CrateType.CrateReward reward, boolean quiet) {
        if (testers.contains(player.getUniqueId())) {
            // test key: show what would have been won, give nothing, count nothing
            plugin.messages().send(player, "moderation.testkey-won", "reward", rewardName(reward), "crate", Text.parse(type.name()));
            return;
        }
        plugin.quests().count(player, online.mythoscraft.core.progress.QuestService.Type.CRATES, 1);
        plugin.jobs().crateOpened(player, reward.rarity());
        reward.reward().give(plugin, player, 1.0);
        online.mythoscraft.core.player.PlayerData data = plugin.playerData().get(player);
        if (data != null) {
            data.addCrateOpened();
        }
        Component name = rewardName(reward);
        if (!quiet) {
            plugin.messages().send(player, "crates.won", "reward", name, "crate", Text.parse(type.name()));
        }
        if (reward.rarity().ordinal() >= type.broadcastFrom().ordinal()) {
            plugin.messages().broadcast("crates.broadcast", "player", player.getName(), "reward", name,
                    "crate", Text.parse(type.name()));
        }
        if (crate != null && crate.location().getWorld() != null && !quiet) {
            online.mythoscraft.core.util.Effects.burst(crate.location().clone().add(0.5, 1.4, 0.5), reward.rarity().color(),
                    Color.WHITE, reward.rarity().ordinal() >= Rarity.LEGENDARY.ordinal());
        }
    }

    public Component rewardName(CrateType.CrateReward reward) {
        Component base = reward.name() != null ? Text.parse(reward.name()) : reward.reward().describe(plugin, 1.0);
        return Text.parse(reward.rarity().colorTag()).append(base);
    }

    public ItemStack rewardIcon(CrateType.CrateReward reward) {
        Reward r = reward.reward();
        ItemStack icon = switch (r.type()) {
            case COINS -> plugin.items().create(online.mythoscraft.core.item.CustomItem.MYTHOS_COIN);
            case GEMS -> plugin.items().create(online.mythoscraft.core.item.CustomItem.GEM_SHARD);
            case XP -> new ItemStack(Material.EXPERIENCE_BOTTLE);
            case KEY -> {
                ItemStack k = createKey(r.id(), 1);
                yield k == null ? new ItemStack(Material.TRIPWIRE_HOOK) : k;
            }
            case TOOL -> plugin.items().create(online.mythoscraft.core.item.CustomItem.byId(r.id()));
            case ITEM -> new ItemStack(Material.valueOf(r.id()), (int) Math.max(1, Math.min(64, r.amount())));
            case VOUCHER -> {
                String[] v = r.id().split("\\|");
                ItemStack voucher = plugin.vouchers().create(v[0], v[1], 1);
                yield voucher == null ? new ItemStack(Material.PAPER) : voucher;
            }
            case BOOK -> {
                ItemStack book = r.enchantedBook();
                yield book == null ? new ItemStack(Material.ENCHANTED_BOOK) : book;
            }
            case COMMAND -> new ItemStack(Material.NAME_TAG);
            case SPAWNER -> online.mythoscraft.core.item.SpawnerItems.create(online.mythoscraft.core.item.SpawnerItems.parse(r.id()), 1);
        };
        if (reward.icon() != null) {
            icon = icon.withType(reward.icon());
        }
        Component name = rewardName(reward);
        icon.editMeta(meta -> meta.displayName(Text.noItalic(name)));
        return icon;
    }

    public boolean isOpening(Player player) {
        return openings.containsKey(player.getUniqueId());
    }

    public void handleQuit(Player player) {
        CrateOpening opening = openings.get(player.getUniqueId());
        if (opening != null) {
            opening.complete();
        }
    }

    public Component guiTitle(char background, Component title, Player viewer) {
        return plugin.display().hasPack(viewer) ? Glyphs.guiTitle(background, title) : title;
    }

    public void shutdown() {
        despawnAll();
    }
}
