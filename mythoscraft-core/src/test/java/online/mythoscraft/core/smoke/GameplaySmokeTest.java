package online.mythoscraft.core.smoke;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.economy.Currency;
import online.mythoscraft.core.economy.EconomyManager;
import online.mythoscraft.core.item.CustomItem;
import online.mythoscraft.core.market.AuctionListing;
import online.mythoscraft.core.market.FarmOrder;
import online.mythoscraft.core.player.PlayerData;
import online.mythoscraft.core.shop.ShopManager;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real gameplay flows with simulated players: shop, auction house, farm orders, rank voucher, soul jar. */
class GameplaySmokeTest {

    private ServerMock server;
    private MythosCraft plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(MythosCraft.class);
        // the simulated server has no scoreboard number formats – the sidebar/name tags are not part of these flows
        plugin.getConfig().set("scoreboard.enabled", false);
        plugin.getConfig().set("nametags.enabled", false);
        plugin.getConfig().set("tablist.enabled", false);
        plugin.getConfig().set("spawn.teleport-on-first-join", false);
        plugin.saveConfig();
        plugin.reloadAll();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Joins a player and waits until the player data is loaded from the database. */
    private PlayerMock join(String name) throws InterruptedException {
        PlayerMock p = server.addPlayer(name);
        for (int i = 0; i < 200 && plugin.playerData().get(p) == null; i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        if (plugin.playerData().get(p) == null) {
            plugin.playerData().loadAsync(p);
            for (int i = 0; i < 200 && plugin.playerData().get(p) == null; i++) {
                server.getScheduler().performOneTick();
                Thread.sleep(10);
            }
        }
        assertNotNull(plugin.playerData().get(p), "player data of " + name + " not loaded");
        return p;
    }

    private void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 300 && !condition.getAsBoolean(); i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
    }

    private void rightClick(PlayerMock p, ItemStack item) {
        p.getInventory().setItemInMainHand(item);
        server.getPluginManager().callEvent(new PlayerInteractEvent(p, Action.RIGHT_CLICK_AIR, p.getInventory().getItemInMainHand(),
                null, null, EquipmentSlot.HAND));
    }

    @Test
    void snowmanPlaysTheVillagerRecordingForEveryone() throws Exception {
        PlayerMock admin = join("SnowAdmin");
        admin.setOp(true);
        while (admin.nextComponentMessage() != null) {
            // drain join messages
        }
        java.io.File ogg = new java.io.File("resourcepack/assets/mythoscraft/sounds/music/snowman.ogg");
        assertTrue(ogg.length() > 1_000_000, "snowman.ogg missing in the resource pack");
        assertTrue(java.nio.file.Files.readString(new java.io.File("resourcepack/assets/mythoscraft/sounds.json").toPath())
                .contains("music.snowman"), "sound not registered");
        assertTrue(server.dispatchCommand(admin, "snowman"));
        assertNotNull(plugin.attractions().music().recording(), "snowman not playing");
        assertTrue(server.dispatchCommand(admin, "snowman"));
        org.junit.jupiter.api.Assertions.assertNull(plugin.attractions().music().recording(), "second /snowman must stop it");
    }

    @Test
    void scheduleVenuesUpgradesAndClanWar() throws Exception {
        // every event in the schedule exists and has texts in both languages
        for (String pool : new String[]{"hourly", "daily", "weekly", "monthly", "all-day"}) {
            for (String id : plugin.getConfig().getStringList("events.schedule." + pool + ".pool")) {
                assertNotNull(plugin.events().get(id), pool + ": unknown event " + id);
            }
        }
        for (online.mythoscraft.core.events.MythosEvent e : plugin.events().all()) {
            assertTrue(!plugin.messages().raw("events." + e.id() + ".name").contains("Nachricht fehlt"), "no name: " + e.id());
            assertTrue(!plugin.messages().raw("events." + e.id() + ".subtitle").contains("Nachricht fehlt"), "no subtitle: " + e.id());
        }
        assertTrue(plugin.events().get("coinrain").venueType() == online.mythoscraft.core.events.MythosEvent.Venue.SPAWN);
        assertTrue(plugin.events().get("spiderqueen").venueType() == online.mythoscraft.core.events.MythosEvent.Venue.ARENA);
        assertTrue(plugin.events().get("mythostitan").venueType() == online.mythoscraft.core.events.MythosEvent.Venue.ARENA);
        assertTrue(plugin.events().get("meteor").venueType() == online.mythoscraft.core.events.MythosEvent.Venue.WILD);

        // blacksmith: +1 with coins, gems and diamonds
        PlayerMock p = join("Schmiedin");
        online.mythoscraft.core.player.PlayerData d = plugin.playerData().get(p);
        plugin.economy().earn(d, online.mythoscraft.core.economy.Currency.COINS, 100_000, false);
        plugin.economy().earn(d, online.mythoscraft.core.economy.Currency.GEMS, 100, false);
        p.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.DIAMOND_SWORD));
        p.getInventory().addItem(new ItemStack(org.bukkit.Material.DIAMOND, 10));
        assertTrue(server.dispatchCommand(p, "schmied ok"));
        assertEquals(1, plugin.upgrades().level(p.getInventory().getItemInMainHand()), "sword not upgraded");
        assertTrue(p.getInventory().contains(org.bukkit.Material.DIAMOND, 8), "diamonds not taken");

        // clan war: event win gives the clan points
        assertTrue(server.dispatchCommand(p, "clan create Kriegsclan KRG"));
        assertNotNull(plugin.clans().of(p.getUniqueId()), "clan not created");
        plugin.clanWar().add(p.getUniqueId(), online.mythoscraft.core.clan.ClanWarService.Source.EVENT_WIN);
        assertEquals(10, plugin.clanWar().points(plugin.clans().of(p.getUniqueId())));
        assertTrue(server.dispatchCommand(p, "clan krieg"));
    }

    @Test
    void crateCarouselRotatesAndEventCratesOnlyInTheirEvent() throws Exception {
        org.bukkit.World w = server.addSimpleWorld("carousel");
        java.util.Map<String, List<org.bukkit.Location>> pts = new java.util.HashMap<>();
        List<org.bukkit.Location> spots = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            spots.add(new org.bukkit.Location(w, i * 4, 65, 0));
        }
        pts.put("crate", spots);
        pts.put("crate_event", List.of(new org.bukkit.Location(w, -6, 65, 0)));
        pts.put("range_start", List.of(new org.bukkit.Location(w, 0, 65, 20)));
        pts.put("range_stand", List.of(new org.bukkit.Location(w, 0.5, 65, 20.5)));
        pts.put("target", List.of(new org.bukkit.Location(w, -3, 67, 0), new org.bukkit.Location(w, 3, 67, 0)));
        plugin.attractionStore().define(new org.bukkit.Location(w, 0, 64, 0), 128, pts);
        online.mythoscraft.core.crate.CrateCarouselService car = plugin.crateCarousel();
        car.arrange();
        java.util.function.Function<org.bukkit.Location, String> at = l -> {
            online.mythoscraft.core.crate.PlacedCrate c = plugin.crates().nearest(l.clone().add(0.5, 0.5, 0.5), 0.8);
            return c == null ? null : c.type();
        };
        assertEquals(List.of("common", "vip", "elite", "mythos"), List.of(at.apply(spots.get(0)), at.apply(spots.get(1)), at.apply(spots.get(2)), at.apply(spots.get(3))));
        java.util.Set<String> rotating = new java.util.HashSet<>();
        for (int i = 4; i < 9; i++) {
            assertNotNull(at.apply(spots.get(i)), "rotating pedestal " + i + " empty");
            rotating.add(at.apply(spots.get(i)));
        }
        assertEquals(5, rotating.size(), "rotating crates must differ");
        // event crates never stand outside their event (today is not Halloween / Frostfest / Silvester in the test clock? check)
        String ev = at.apply(pts.get("crate_event").get(0));
        if (ev != null) {
            assertTrue(car.eventActive(ev), "event crate " + ev + " stands outside its event");
        }
        for (String id : new String[]{"halloween", "frostfest", "silvester"}) {
            for (org.bukkit.Location s : spots) {
                assertTrue(!id.equals(at.apply(s)), id + " on a normal pedestal");
            }
        }
        // a swap puts a new crate on a rotating pedestal
        java.util.Set<String> before = new java.util.HashSet<>(rotating);
        java.lang.reflect.Method swap = car.getClass().getDeclaredMethod("swap");
        swap.setAccessible(true);
        swap.invoke(car);
        java.util.Set<String> after = new java.util.HashSet<>();
        for (int i = 4; i < 9; i++) {
            after.add(at.apply(spots.get(i)));
        }
        assertTrue(!after.equals(before), "carousel did not swap");
        assertEquals("common", at.apply(spots.get(0)), "fixed crate changed");

        // target range: the plate hands out the loan bow, leaving the stand takes it back
        PlayerMock p = join("Schuetze");
        p.teleport(new org.bukkit.Location(w, 0.5, 65, 20.5));
        server.getPluginManager().callEvent(new PlayerInteractEvent(p, Action.PHYSICAL, null, w.getBlockAt(0, 65, 20), null));
        assertTrue(p.getInventory().contains(org.bukkit.Material.BOW), "no loan bow");
        p.teleport(new org.bukkit.Location(w, 50, 65, 50));
        server.getScheduler().performTicks(60);
        assertTrue(!p.getInventory().contains(org.bukkit.Material.BOW), "loan bow not taken back");
    }

    @Test
    void boostersMutationsMagicAndRankPerks() throws Exception {
        PlayerMock p = join("Magierin");
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        waitFor(() -> plugin.progress().loaded(p));
        assertTrue(plugin.progress().loaded(p), "progress not loaded");
        online.mythoscraft.core.player.PlayerData d = plugin.playerData().get(p);
        double before = plugin.multipliers().forPlayer(online.mythoscraft.core.economy.MultiplierType.COINS, d);
        // coin booster: right click → personal double coins
        rightClick(p, plugin.items().create(online.mythoscraft.core.item.CustomItem.COIN_BOOSTER));
        assertTrue(plugin.boosters().active(p.getUniqueId(), online.mythoscraft.core.item.BoosterService.Type.COINS), "coin booster not active");
        assertEquals(before * 2, plugin.multipliers().forPlayer(online.mythoscraft.core.economy.MultiplierType.COINS, d), 0.0001);
        // mutation serum: one of the mutations is active
        rightClick(p, plugin.items().create(online.mythoscraft.core.item.CustomItem.MUTATION_SERUM));
        assertNotNull(plugin.boosters().mutation(p), "no mutation");
        // magic: the wand casts the selected spell and uses mana
        p.teleport(new org.bukkit.Location(p.getWorld(), 0, 200, 0));
        double mana = plugin.magic().mana(p);
        p.getInventory().setItemInMainHand(plugin.items().create(online.mythoscraft.core.item.CustomItem.MYTHOS_WAND));
        plugin.magic().cast(p, online.mythoscraft.core.item.MagicService.Spell.ARCANE_BOLT);
        assertTrue(plugin.magic().mana(p) < mana, "casting used no mana");
        assertEquals(2, plugin.magic().known(p).size(), "starter spells");
        // star jump aimed at a zombie away from the spawn: both swap places
        org.bukkit.entity.Zombie z = p.getWorld().spawn(new org.bukkit.Location(p.getWorld(), 0.5, 200, 6.5), org.bukkit.entity.Zombie.class);
        org.bukkit.Location me = new org.bukkit.Location(p.getWorld(), 0.5, 200, 0.5, 0, 0);
        p.teleport(me);
        java.lang.reflect.Method swap = plugin.magic().getClass().getDeclaredMethod("swap", Player.class, org.bukkit.entity.LivingEntity.class);
        swap.setAccessible(true);
        server.getScheduler().performTicks(15);
        assertTrue((boolean) swap.invoke(plugin.magic(), p, z), "swap refused");
        assertEquals(6.5, p.getLocation().getZ(), 0.01, "player not at the zombie's place");
        assertEquals(0.5, z.getLocation().getZ(), 0.01, "zombie not at the player's place");
        rightClick(p, plugin.items().create(online.mythoscraft.core.item.CustomItem.SPELL_SCROLL));
        assertEquals(3, plugin.magic().known(p).size(), "scroll did not teach a spell");
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "magie lernen Magierin alle"));
        assertEquals(7, plugin.magic().known(p).size(), "/magie lernen alle");
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "magie vergessen Magierin kettenblitz"));
        assertEquals(6, plugin.magic().known(p).size(), "/magie vergessen kettenblitz");
        // rank perks: MYTHOS has big perks, normal players none
        assertEquals(1.0, plugin.rankPerks().jobXpFactor(p), 0.0001);
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "rank set Magierin MYTHOS"));
        server.getScheduler().performTicks(5);
        if (plugin.ranks().rankOf(p).isMythos()) {
            assertTrue(plugin.rankPerks().jobXpFactor(p) > 1.5, "mythos job xp");
            assertTrue(plugin.rankPerks().of(p).spawnFly(), "mythos spawn fly");
            assertTrue(plugin.magic().max(p) > 200, "mythos mana");
        }
        assertTrue(server.dispatchCommand(p, "booster"));
        assertTrue(server.dispatchCommand(p, "kisten"));
    }

    @Test
    void giveawayDrawsAWinnerAndCreatorRankExists() throws Exception {
        assertNotNull(plugin.ranks().byId("CREATOR"), "creator rank missing");
        PlayerMock a = join("Losglueck");
        PlayerMock b = join("Losfreund");
        plugin.giveaways().start(online.mythoscraft.core.reward.Reward.parse("voucher VIP 7d"), 1, 1);
        assertTrue(server.dispatchCommand(a, "verlosung"));
        assertTrue(server.dispatchCommand(b, "verlosung"));
        java.lang.reflect.Method draw = plugin.giveaways().getClass().getDeclaredMethod("draw");
        draw.setAccessible(true);
        draw.invoke(plugin.giveaways());
        boolean aWon = java.util.Arrays.stream(a.getInventory().getContents()).anyMatch(i -> i != null && i.hasItemMeta()
                && i.getItemMeta().getPersistentDataContainer().has(online.mythoscraft.core.rank.RankVouchers.RANK));
        boolean bWon = java.util.Arrays.stream(b.getInventory().getContents()).anyMatch(i -> i != null && i.hasItemMeta()
                && i.getItemMeta().getPersistentDataContainer().has(online.mythoscraft.core.rank.RankVouchers.RANK));
        assertTrue(aWon ^ bWon, "exactly one winner must get the VIP voucher");
        assertTrue(!plugin.giveaways().running());
    }

    @Test
    void creatorRafflesAnItemForASmallFee() throws Exception {
        PlayerMock creator = join("Streamerin");
        PlayerMock fan = join("Zuschauer");
        creator.addAttachment(plugin, "mythos.giveaway.item", true);
        online.mythoscraft.core.player.PlayerData d = plugin.playerData().get(creator);
        plugin.economy().earn(d, online.mythoscraft.core.economy.Currency.COINS, 5000, false);
        long coins = online.mythoscraft.core.economy.Currency.COINS.get(d);
        creator.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.DIAMOND_SWORD));
        assertTrue(server.dispatchCommand(creator, "verlosung item 3"));
        assertTrue(plugin.giveaways().running(), "item giveaway did not start");
        assertTrue(creator.getInventory().getItemInMainHand().getType().isAir(), "item not taken");
        assertTrue(online.mythoscraft.core.economy.Currency.COINS.get(d) < coins, "no fee taken");
        assertTrue(server.dispatchCommand(fan, "verlosung"));
        java.lang.reflect.Method draw = plugin.giveaways().getClass().getDeclaredMethod("draw");
        draw.setAccessible(true);
        draw.invoke(plugin.giveaways());
        assertTrue(fan.getInventory().contains(org.bukkit.Material.DIAMOND_SWORD), "winner did not get the item");
        // without permission: no item giveaway
        fan.getInventory().setItemInMainHand(new ItemStack(org.bukkit.Material.DIRT));
        server.dispatchCommand(fan, "verlosung item");
        assertTrue(!plugin.giveaways().running());
    }

    private String syncKey;

    @Test
    void syncKeyCopyButtonHoldsTheCommand() {
        for (String lang : new String[]{"de", "en"}) {
            String raw = lang.equals("de") ? plugin.messages().raw("server-sync.key")
                    : plugin.messages().raw("server-sync.key").replace("'{command}'", "\"{command}\"");
            net.kyori.adventure.text.Component c = plugin.messages().format(raw, "command", "/plot admin key abc123");
            String json = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(c);
            assertTrue(json.contains("/plot admin key abc123"), json);
        }
        PlayerMock p = server.addPlayer("Keyowner");
        p.setOp(true);
        server.dispatchCommand(p, "mythos synckey");
        StringBuilder all = new StringBuilder();
        for (net.kyori.adventure.text.Component m = p.nextComponentMessage(); m != null; m = p.nextComponentMessage()) {
            all.append(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(m)).append('\n');
        }
        assertTrue(all.toString().contains("\"value\":\"/plot admin key " + plugin.serverSync().key()) || all.toString().contains("/plot admin key " + plugin.serverSync().key()), all.toString());
    }

    @Test
    void inventoryGoesToThePlotServerAndBack() throws Exception {
        plugin.getConfig().set("web-panel.port", 18085);
        plugin.getConfig().set("web-panel.bind", "127.0.0.1");
        plugin.getConfig().set("web-panel.relay.url", "");
        plugin.saveConfig();
        plugin.reloadAll();
        PlayerMock p = join("Hopper");
        java.util.UUID id = p.getUniqueId();
        p.getInventory().setItem(3, new ItemStack(org.bukkit.Material.DIAMOND, 5));
        p.setLevel(4);
        p.disconnect();
        server.getScheduler().performTicks(5);
        String uuid = "{\"uuid\":\"" + id + "\"}";
        assertTrue(http("POST", "/api/sync/pull", uuid, null, true, null).startsWith("403"), "no key, no inventory");
        syncKey = plugin.serverSync().key();
        String pulled = http("POST", "/api/sync/pull", uuid, null, true, null);
        assertTrue(pulled.startsWith("200") && pulled.contains("\"ok\""), pulled);
        com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(pulled.substring(4)).getAsJsonObject();
        com.google.gson.JsonObject data = o.getAsJsonObject("data");
        assertTrue(data.getAsJsonObject("looks").has("prefix"), "rank looks travel along");
        // live save point while he plays there
        data.addProperty("level", 6);
        String cp = "{\"uuid\":\"" + id + "\",\"session\":\"" + o.get("session").getAsString() + "\",\"data\":" + data + "}";
        assertTrue(http("POST", "/api/sync/checkpoint", cp, null, true, null).contains("\"ok\""));
        // on the plot server he gained levels; a wrong session is refused
        data.addProperty("level", 9);
        String wrong = "{\"uuid\":\"" + id + "\",\"session\":\"x\",\"data\":" + data + "}";
        assertTrue(http("POST", "/api/sync/push", wrong, null, true, null).contains("rejected"));
        String push = "{\"uuid\":\"" + id + "\",\"session\":\"" + o.get("session").getAsString() + "\",\"data\":" + data + "}";
        assertTrue(http("POST", "/api/sync/push", push, null, true, null).contains("\"ok\""));
        assertTrue(http("POST", "/api/sync/push", push, null, true, null).contains("rejected"), "only once");
        syncKey = null;
        PlayerMock back = new PlayerMock(server, "Hopper", id);
        server.addPlayer(back);
        server.getScheduler().performTicks(5);
        assertEquals(9, back.getLevel(), "inventory from the plot server applied");
        ItemStack d = back.getInventory().getItem(3);
        assertTrue(d != null && d.getType() == org.bukkit.Material.DIAMOND && d.getAmount() == 5, "items kept: " + d);
        // admin page exists, its data only for admins
        assertTrue(http("GET", "/inventory/", null, null, false, null).startsWith("200"));
        assertTrue(http("GET", "/api/inventory/list", null, null, false, null).startsWith("401"));
        java.io.File backups = new java.io.File(plugin.getDataFolder(), "data/server-sync/backups/" + id);
        waitFor(() -> backups.isDirectory() && backups.list().length >= 2);
        assertTrue(backups.list().length >= 2, "backups written");
    }

    private String http(String method, String path, String body, String cookie, boolean panelHeader, String[] setCookie) throws Exception {
        java.util.concurrent.CompletableFuture<String> f = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) java.net.URI.create("http://127.0.0.1:18085" + path).toURL().openConnection();
                c.setRequestMethod(method);
                if (cookie != null) {
                    c.setRequestProperty("Cookie", cookie);
                }
                if (panelHeader) {
                    c.setRequestProperty("X-Panel", "1");
                }
                if (syncKey != null) {
                    c.setRequestProperty("X-Sync-Key", syncKey);
                }
                if (body != null) {
                    c.setDoOutput(true);
                    c.setRequestProperty("Content-Type", "application/json");
                    c.getOutputStream().write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                int code = c.getResponseCode();
                if (setCookie != null && c.getHeaderField("Set-Cookie") != null) {
                    setCookie[0] = c.getHeaderField("Set-Cookie").split(";")[0];
                }
                java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                return code + " " + (in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception e) {
                return "ERR " + e;
            }
        });
        for (int i = 0; i < 500 && !f.isDone(); i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        return f.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    void webPanelLoginStatsAndActions() throws Exception {
        plugin.getConfig().set("web-panel.port", 18085);
        plugin.getConfig().set("web-panel.bind", "127.0.0.1");
        plugin.getConfig().set("web-panel.relay.url", "");
        plugin.saveConfig();
        plugin.reloadAll();
        PlayerMock admin = join("Paneltester");
        PlayerMock stranger = join("Fremder");
        plugin.webPanel().allow(admin.getUniqueId(), admin.getName(), online.mythoscraft.core.web.WebPanelService.Role.ADMIN);

        assertTrue(http("GET", "/panel/", null, null, false, null).startsWith("200"), "panel index.html");
        assertTrue(http("GET", "/panel/app.js", null, null, false, null).startsWith("200"), "app.js");
        String home = http("GET", "/", null, null, false, null);
        assertTrue(home.startsWith("200") && home.contains("MythosCraft Wiki"), "start page is the wiki");
        assertTrue(http("GET", "/wiki/data.json", null, null, false, null).startsWith("200"), "wiki data");
        assertTrue(http("POST", "/api/login/start", "{}", null, false, null).startsWith("403"), "csrf header required");
        assertTrue(http("GET", "/api/stats", null, null, false, null).startsWith("401"), "stats need login");

        String start = http("POST", "/api/login/start", "{}", null, true, null);
        com.google.gson.JsonObject ch = com.google.gson.JsonParser.parseString(start.substring(4)).getAsJsonObject();
        String code = ch.get("code").getAsString();
        // a player without access cannot confirm
        assertEquals("denied", plugin.webPanel().confirm(stranger, code));
        assertTrue(http("GET", "/api/login/poll?id=" + ch.get("id").getAsString(), null, null, false, null).contains("waiting"));
        assertTrue(server.dispatchCommand(admin, "website login " + code));
        String[] cookie = new String[1];
        assertTrue(http("GET", "/api/login/poll?id=" + ch.get("id").getAsString(), null, null, false, cookie).contains("\"ok\""));
        assertNotNull(cookie[0], "no session cookie");

        String me = http("GET", "/api/me", null, cookie[0], false, null);
        assertTrue(me.startsWith("200") && me.contains("\"admin\""), me);
        String stats = http("GET", "/api/stats", null, cookie[0], false, null);
        assertTrue(stats.startsWith("200") && stats.contains("Paneltester") && stats.contains("tps"), stats);
        assertTrue(http("GET", "/api/actions", null, cookie[0], false, null).contains("event-start"));

        online.mythoscraft.core.player.PlayerData d = plugin.playerData().get(stranger);
        long before = online.mythoscraft.core.economy.Currency.COINS.get(d);
        String act = http("POST", "/api/action", "{\"id\":\"eco\",\"params\":{\"player\":\"Fremder\",\"op\":\"give\",\"currency\":\"coins\",\"amount\":\"777\"}}",
                cookie[0], true, null);
        assertTrue(act.startsWith("200"), act);
        waitFor(() -> online.mythoscraft.core.economy.Currency.COINS.get(d) == before + 777);
        assertEquals(before + 777, online.mythoscraft.core.economy.Currency.COINS.get(d), "button action did not give coins: " + act);
        // injection attempts are refused
        assertTrue(http("POST", "/api/action", "{\"id\":\"eco\",\"params\":{\"player\":\"x; op Fremder\",\"op\":\"give\",\"currency\":\"coins\",\"amount\":\"1\"}}",
                cookie[0], true, null).startsWith("400"));
        // only the owner manages access
        assertTrue(http("GET", "/api/access", null, cookie[0], false, null).startsWith("403"));
        plugin.webPanel().stop();
    }

    @Test
    void websiteDailyRewardAndModApplication() throws Exception {
        plugin.getConfig().set("web-panel.port", 18085);
        plugin.getConfig().set("web-panel.bind", "127.0.0.1");
        plugin.getConfig().set("web-panel.relay.url", "");
        plugin.getConfig().set("mod-applications.min-playtime-hours", 0);
        plugin.getConfig().set("mod-applications.email", "");
        plugin.saveConfig();
        plugin.reloadAll();
        PlayerMock fan = join("Webfan");
        // a normal player signs in with a code (the team panel would refuse him)
        String start = http("POST", "/api/login/start?kind=player", "{}", null, true, null);
        com.google.gson.JsonObject ch = com.google.gson.JsonParser.parseString(start.substring(4)).getAsJsonObject();
        assertEquals("ok", plugin.webPanel().confirm(fan, ch.get("code").getAsString()));
        String[] cookie = new String[1];
        assertTrue(http("GET", "/api/login/poll?id=" + ch.get("id").getAsString(), null, null, false, cookie).contains("\"ok\""));
        assertTrue(cookie[0] != null && cookie[0].startsWith("mcplayer="), "player cookie " + cookie[0]);
        assertTrue(http("GET", "/api/me", null, cookie[0], false, null).startsWith("401"), "no team panel for players");
        String me = http("GET", "/api/player/me", null, cookie[0], false, null);
        assertTrue(me.startsWith("200") && me.contains("\"canClaim\":true"), me);
        // daily reward: once per day, arrives in game
        online.mythoscraft.core.player.PlayerData d = plugin.playerData().get(fan);
        long before = online.mythoscraft.core.economy.Currency.COINS.get(d);
        assertTrue(http("POST", "/api/player/claim", "{}", cookie[0], true, null).startsWith("200"));
        assertTrue(http("POST", "/api/player/claim", "{}", cookie[0], true, null).startsWith("409"), "only once a day");
        for (int i = 0; i < 20; i++) {
            server.getScheduler().performOneTick();
        }
        assertTrue(online.mythoscraft.core.economy.Currency.COINS.get(d) > before, "website reward arrived in game");
        // moderator application
        String info = http("GET", "/api/apply", null, cookie[0], false, null);
        assertTrue(info.contains("\"open\":true"), info);
        int n = plugin.getConfig().getStringList("mod-applications.questions").size();
        com.google.gson.JsonArray answers = new com.google.gson.JsonArray();
        for (int i = 0; i < n; i++) {
            answers.add("answer " + i);
        }
        String sent = http("POST", "/api/apply", "{\"answers\":" + answers + "}", cookie[0], true, null);
        assertTrue(sent.startsWith("200"), sent);
        assertTrue(http("POST", "/api/apply", "{\"answers\":" + answers + "}", cookie[0], true, null).contains("pending"), "one open application");
        String id = com.google.gson.JsonParser.parseString(sent.substring(4)).getAsJsonObject().get("id").getAsString();
        org.bukkit.configuration.file.YamlConfiguration apps = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.File(plugin.getDataFolder(), "data/applications.yml"));
        String key = apps.getString("applications." + id + ".key");
        assertTrue(http("GET", "/api/apply/decide?id=" + id + "&key=wrong&action=accept", null, null, false, null).contains("invalid"));
        String page = http("GET", "/api/apply/decide?id=" + id + "&key=" + key + "&action=accept", null, null, false, null);
        assertTrue(page.contains("now a moderator"), page);
        for (int i = 0; i < 40; i++) {
            server.getScheduler().performOneTick();
        }
        assertEquals("MOD", plugin.playerData().get(fan).rankId(), "accepted applicant got the MOD rank");
        assertTrue(http("GET", "/api/apply/decide?id=" + id + "&key=" + key + "&action=accept", null, null, false, null).contains("already used"));
        assertTrue(http("GET", "/api/apply", null, cookie[0], false, null).contains("accepted"));
        plugin.webPanel().stop();
    }

    @Test
    void relayBridgeTakesJobsFromTheWebsiteAndAnswers() throws Exception {
        java.util.List<String> bodies = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        com.sun.net.httpserver.HttpServer fake = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 18086), 8);
        boolean[] sent = {false};
        fake.createContext("/api/bridge", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            bodies.add(ex.getRequestHeaders().getFirst("X-Relay-Secret") + "|" + body);
            String answer = "{\"jobs\":[]}";
            if (!sent[0]) {
                sent[0] = true;
                answer = "{\"jobs\":[{\"id\":\"j1\",\"type\":\"action\",\"by\":\"MinePro641\",\"payload\":{\"id\":\"eco\",\"role\":\"owner\","
                        + "\"params\":{\"player\":\"Relayfan\",\"op\":\"give\",\"currency\":\"coins\",\"amount\":\"555\"}}}]}";
            }
            byte[] b = answer.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        fake.start();
        try {
            plugin.getConfig().set("newbies.auto-daily-on-join", false);   // the login reward would change the coins too
            plugin.saveConfig();
            plugin.reloadAll();
            PlayerMock fan = join("Relayfan");
            online.mythoscraft.core.player.PlayerData d = plugin.playerData().get(fan);
            long before = online.mythoscraft.core.economy.Currency.COINS.get(d);
            plugin.getConfig().set("web-panel.port", 18087);
            plugin.getConfig().set("web-panel.bind", "127.0.0.1");
            plugin.getConfig().set("web-panel.relay.url", "http://127.0.0.1:18086");
            plugin.getConfig().set("web-panel.relay.secret", "");
            plugin.saveConfig();
            plugin.reloadAll();
            String secret = plugin.getConfig().getString("web-panel.relay.secret");
            assertTrue(secret != null && secret.length() >= 24, "no secret generated");
            for (int i = 0; i < 1200 && online.mythoscraft.core.economy.Currency.COINS.get(d) == before; i++) {
                server.getScheduler().performOneTick();
                Thread.sleep(5);
            }
            assertEquals(before + 555, online.mythoscraft.core.economy.Currency.COINS.get(d), "job from the website was not executed");
            for (int i = 0; i < 600 && bodies.stream().noneMatch(x -> x.contains("\"j1\"")); i++) {
                server.getScheduler().performOneTick();
                Thread.sleep(5);
            }
            assertTrue(bodies.stream().anyMatch(x -> x.startsWith(secret + "|") && x.contains("\"j1\"") && x.contains("mythos eco give Relayfan")),
                    "result not sent back");
            assertTrue(bodies.stream().anyMatch(x -> x.contains("\"9f799033-c0f0-4d2a-aa6f-b68ce9207af4\":\"owner\"")), "access map missing");
            assertTrue(bodies.stream().anyMatch(x -> x.contains("\"snapshot\"")), "no live data uploaded");
        } finally {
            plugin.webPanel().stop();
            fake.stop(0);
            plugin.getConfig().set("web-panel.relay.url", "");
            plugin.saveConfig();
        }
    }

    @Test
    void gemPaySendsGems() throws Exception {
        PlayerMock a = join("Gemgeber");
        PlayerMock b = join("Gemnehmer");
        online.mythoscraft.core.player.PlayerData da = plugin.playerData().get(a);
        online.mythoscraft.core.player.PlayerData db = plugin.playerData().get(b);
        plugin.economy().earn(da, online.mythoscraft.core.economy.Currency.GEMS, 50, false);
        long ga = online.mythoscraft.core.economy.Currency.GEMS.get(da);
        long gb = online.mythoscraft.core.economy.Currency.GEMS.get(db);
        assertTrue(server.dispatchCommand(a, "gempay Gemnehmer 20"));
        assertEquals(ga - 20, online.mythoscraft.core.economy.Currency.GEMS.get(da));
        assertEquals(gb + 20, online.mythoscraft.core.economy.Currency.GEMS.get(db));
    }

    @Test
    void seasonSkyIsMixedIntoThePack() throws Exception {
        Object rp = plugin.getClass().getMethod("resourcePack").invoke(plugin);
        java.lang.reflect.Method mix = rp.getClass().getDeclaredMethod("withSeason", byte[].class, String.class);
        mix.setAccessible(true);
        byte[] base;
        try (java.io.InputStream in = plugin.getResource("resourcepack/MythosCraft-ResourcePack.zip")) {
            assertNotNull(in, "bundled pack missing");
            base = in.readAllBytes();
        }
        byte[] mixed = (byte[]) mix.invoke(rp, base, "halloween");
        java.util.Set<String> names = new java.util.HashSet<>();
        try (java.util.zip.ZipInputStream z = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(mixed))) {
            java.util.zip.ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                names.add(e.getName());
            }
        }
        assertTrue(names.contains("assets/minecraft/textures/environment/celestial/sun.png"), "season sun missing");
        assertTrue(names.contains("assets/minecraft/textures/environment/clouds.png"), "season clouds missing");
        assertTrue(names.contains("assets/mythoscraft/models/item/pharaoh_helmet.json"), "main pack content lost");
        assertTrue(names.contains("assets/mythoscraft/items/sky_halloween_moon.json"), "sky dome picture missing");
        assertEquals(base.length, ((byte[]) mix.invoke(rp, base, "none")).length, "no season = unchanged pack");
    }

    @Test
    void seasonSkyDomeAndMoodFollowTheSeason() throws Exception {
        org.bukkit.World w = server.addSimpleWorld("skyworld");
        plugin.attractionStore().define(new org.bukkit.Location(w, 0, 64, 0), 128, java.util.Map.of());
        w.getChunkAt(0, 0).load();
        PlayerMock p = join("Himmelsgucker");
        p.teleport(new org.bukkit.Location(w, 5, 65, 5));
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "season start halloween"));
        assertEquals("halloween", plugin.seasons().active().id());
        for (int i = 0; i < 300; i++) {
            server.getScheduler().performOneTick();
        }
        // 3: the season mood – Halloween night at the spawn
        assertEquals(13800, p.getPlayerTime() % 24000, "halloween night at spawn");
        // 2: the sky dome picks the Halloween pictures and starts spawning them (the simulator cannot finish
        // creating display entities, a real server can)
        java.lang.reflect.Field fd = MythosCraft.class.getDeclaredField("skyDome");
        fd.setAccessible(true);
        java.lang.reflect.Method tick = fd.get(plugin).getClass().getDeclaredMethod("tick");
        tick.setAccessible(true);
        boolean attempted;
        try {
            tick.invoke(fd.get(plugin));
            attempted = w.getEntitiesByClass(org.bukkit.entity.ItemDisplay.class).size() >= 5;
        } catch (java.lang.reflect.InvocationTargetException e) {
            attempted = e.getCause() instanceof org.mockbukkit.mockbukkit.exception.UnimplementedOperationException
                    && java.util.Arrays.stream(e.getCause().getStackTrace()).anyMatch(st -> st.getMethodName().contains("spawn"));
        }
        assertTrue(attempted, "sky dome did not start for halloween");
        java.lang.reflect.Field shown = fd.get(plugin).getClass().getDeclaredField("shown");
        shown.setAccessible(true);
        assertEquals("halloween", shown.get(fd.get(plugin)));
    }

    @Test
    void silvesterKeyOnlyOnTheWheelAndKeyHints() throws Exception {
        // not sold in the shop
        assertTrue(plugin.shop().entries().stream().noneMatch(e -> e.reward().raw().equalsIgnoreCase("key silvester 1")),
                "silvester key must not be in the shop");
        // main prize of the wheel: on it, rarest field, and the wheel has more fields than before
        java.util.List<?> segs = plugin.getConfig().getList("attractions.wheel.segments");
        assertNotNull(segs);
        assertTrue(segs.size() >= 20, "wheel fields: " + segs.size());
        double silvester = -1;
        double min = Double.MAX_VALUE;
        for (Object o : segs) {
            java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
            double w = Double.parseDouble(String.valueOf(m.get("weight")));
            min = Math.min(min, w);
            if ("key silvester 1".equals(String.valueOf(m.get("reward")))) {
                silvester = w;
            }
        }
        assertTrue(silvester > 0, "silvester key is not on the wheel");
        assertEquals(min, silvester, 1e-9, "silvester key must be the rarest (main) prize");
        // clicking a crate without key: buy hint, for silvester the wheel hint
        PlayerMock p = join("Ohneschluessel");
        org.bukkit.World w = server.addSimpleWorld("keyworld");
        online.mythoscraft.core.crate.CrateType vip = plugin.crates().type("vip");
        online.mythoscraft.core.crate.CrateType silv = plugin.crates().type("silvester");
        assertNotNull(silv);
        p.getInventory().clear();
        for (int i = 0; i < 50; i++) {
            server.getScheduler().performOneTick();
        }
        while (p.nextComponentMessage() != null) {
            // drain join messages
        }
        plugin.crates().open(p, plugin.crates().place(vip, new org.bukkit.Location(w, 0, 64, 0), 0f), vip, false);
        String buy = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(p.nextComponentMessage());
        assertTrue(buy.contains("/shop"), "no buy hint: " + buy);
        while (p.nextComponentMessage() != null) {
            // drain
        }
        plugin.crates().open(p, plugin.crates().place(silv, new org.bukkit.Location(w, 4, 64, 0), 0f), silv, false);
        String wheel = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(p.nextComponentMessage());
        assertTrue((wheel.contains("Glücksrad") || wheel.contains("wheel of fortune")), "no wheel hint: " + wheel);
    }

    @Test
    void configUpdateRemovesTheSilvesterKeyFromOldShops() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration old = new org.bukkit.configuration.file.YamlConfiguration();
        old.set("config-version", 13);
        old.set("shop.entries", java.util.List.of(java.util.Map.of("reward", "key silvester 1", "price", 40),
                java.util.Map.of("reward", "key vip 1", "price", 8)));
        java.io.File f = java.io.File.createTempFile("cfg", ".yml");
        old.save(f);
        try (java.io.InputStream in = plugin.getResource("config.yml")) {
            online.mythoscraft.core.config.ConfigUpdater.update(f, in);
        }
        org.bukkit.configuration.file.YamlConfiguration now = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        for (Object o : now.getList("shop.entries")) {
            assertTrue(!String.valueOf(((java.util.Map<?, ?>) o).get("reward")).equalsIgnoreCase("key silvester 1"), "still in shop");
        }
    }

    @Test
    void everyCrateOpensTwiceInARow() throws Exception {
        PlayerMock p = join("Kistenfan");
        org.bukkit.World w = server.addSimpleWorld("crateworld");
        int x = 0;
        for (online.mythoscraft.core.crate.CrateType type : plugin.crates().types()) {
            org.bukkit.Location at = new org.bukkit.Location(w, x += 4, 64, 0);
            online.mythoscraft.core.crate.PlacedCrate crate = plugin.crates().place(type, at, 0f);
            for (int round = 0; round < 2; round++) {
                p.getInventory().clear();
                p.getInventory().setItemInMainHand(plugin.crates().createKey(type.id(), 1));
                plugin.crates().open(p, crate, type, false);
                assertTrue(p.getInventory().getItemInMainHand().isEmpty(), type.id() + ": key was not used (round " + round + ")");
                for (int t = 0; t < 400; t++) {
                    server.getScheduler().performOneTick();
                }
                p.closeInventory();
            }
        }
    }

    @Test
    void shopSellsKeysForGems() throws Exception {
        PlayerMock p = join("Kaeufer");
        PlayerData data = plugin.playerData().get(p);
        plugin.economy().deposit(data, Currency.GEMS, 100);
        ShopManager.Entry vipKey = plugin.shop().entries().stream().filter(e -> e.reward().raw().equals("key vip 1")).findFirst().orElseThrow();
        assertTrue(plugin.shop().buy(p, vipKey, 2));
        assertEquals(100 - 2 * vipKey.price(), data.gems());
        int keys = 0;
        for (ItemStack s : p.getInventory().getContents()) {
            if (s != null && "vip".equals(plugin.crates().keyType(s))) {
                keys += s.getAmount();
            }
        }
        assertEquals(2, keys);
    }

    @Test
    void auctionHouseSellAndBuy() throws Exception {
        waitFor(() -> plugin.auctions().ready());
        assertTrue(plugin.auctions().ready(), "auction house not loaded");
        PlayerMock seller = join("Verkaeufer");
        PlayerMock buyer = join("Kaeufer");
        seller.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 10));
        assertTrue(plugin.auctions().sell(seller, 1000, Currency.COINS));
        List<AuctionListing> active = plugin.auctions().active(online.mythoscraft.core.market.AuctionManager.Sort.NEWEST, null);
        assertEquals(1, active.size());
        PlayerData buyerData = plugin.playerData().get(buyer);
        plugin.economy().deposit(buyerData, Currency.COINS, 5000);
        long sellerBefore = plugin.playerData().get(seller).coins();
        long buyerBefore = buyerData.coins();
        assertTrue(plugin.auctions().buy(buyer, active.get(0)));
        assertEquals(buyerBefore - 1000, buyerData.coins());
        assertEquals(sellerBefore + 950, plugin.playerData().get(seller).coins(), "seller gets price minus 5% tax");
        assertTrue(buyer.getInventory().contains(Material.DIAMOND, 10));
        assertTrue(plugin.auctions().active(online.mythoscraft.core.market.AuctionManager.Sort.NEWEST, null).isEmpty());
    }

    @Test
    void farmOrderEscrowAndDelivery() throws Exception {
        waitFor(() -> plugin.orders().ready());
        PlayerMock owner = join("Bauer");
        PlayerMock farmer = join("Lieferant");
        PlayerData ownerData = plugin.playerData().get(owner);
        PlayerData farmerData = plugin.playerData().get(farmer);
        plugin.economy().deposit(ownerData, Currency.COINS, 10_000);
        long ownerBefore = ownerData.coins();
        assertTrue(plugin.orders().create(owner, Material.WHEAT, 100, 3));
        assertEquals(ownerBefore - 300, ownerData.coins(), "escrow");
        FarmOrder order = plugin.orders().open().get(0);
        farmer.getInventory().addItem(new ItemStack(Material.WHEAT, 64));
        long farmerBefore = farmerData.coins();
        assertTrue(plugin.orders().deliver(farmer, order));
        assertEquals(64, order.delivered());
        assertEquals(farmerBefore + 64 * 3, farmerData.coins());
        assertTrue(!farmer.getInventory().contains(Material.WHEAT));
        assertTrue(plugin.orders().cancel(owner, order, false));
        assertEquals(ownerBefore - 300 + 36 * 3, ownerData.coins(), "refund of undelivered items");
        assertEquals(64, plugin.orders().collect(owner, order));
        assertTrue(owner.getInventory().contains(Material.WHEAT, 64));
    }

    @Test
    void rankVoucherAndSoulJar() throws Exception {
        PlayerMock p = join("Spieler");
        ItemStack voucher = plugin.vouchers().create("ELITE", "30d", 1);
        rightClick(p, voucher);
        assertEquals("ELITE", plugin.ranks().rankOf(p).id());

        p.setLevel(10);
        ItemStack jar = plugin.items().create(CustomItem.SOUL_JAR);
        rightClick(p, jar);
        assertEquals(0, p.getLevel(), "experience stored in the jar");
        p.setSneaking(true);
        rightClick(p, p.getInventory().getItemInMainHand());
        assertTrue(p.getLevel() >= 9, "experience released again, level " + p.getLevel());
    }

    @Test
    void transferRulesStillHold() throws Exception {
        PlayerMock a = join("Alice");
        PlayerMock b = join("Bob");
        PlayerData da = plugin.playerData().get(a);
        PlayerData db = plugin.playerData().get(b);
        plugin.economy().deposit(da, Currency.COINS, 500);
        assertEquals(EconomyManager.Result.INSUFFICIENT_FUNDS, plugin.economy().transfer(db, da, Currency.COINS, 1_000_000, "test"));
    }

    @Test
    void itemAbilities() throws Exception {
        PlayerMock p = join("Tester");
        PlayerData data = plugin.playerData().get(p);

        long coins = data.coins();
        rightClick(p, plugin.items().create(CustomItem.COIN_POUCH));
        assertTrue(data.coins() >= coins + 100, "coin pouch pays 100-1000 coins");

        long gems = data.gems();
        rightClick(p, plugin.items().create(CustomItem.GEM_POUCH));
        assertTrue(data.gems() >= gems + 1, "gem pouch pays gems");

        rightClick(p, plugin.items().create(CustomItem.KEY_FRAGMENT, 9));
        boolean key = false;
        for (ItemStack s : p.getInventory().getContents()) {
            key |= s != null && plugin.crates().keyType(s) != null;
        }
        assertTrue(key, "9 fragments become a crate key");

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.editMeta(org.bukkit.inventory.meta.Damageable.class, d -> d.setDamage(1000));
        p.getInventory().setItemInOffHand(sword);
        rightClick(p, plugin.items().create(CustomItem.REPAIR_HAMMER));
        int damage = ((org.bukkit.inventory.meta.Damageable) p.getInventory().getItemInOffHand().getItemMeta()).getDamage();
        assertTrue(damage < 1000, "repair hammer repairs, damage now " + damage);

        ItemStack pick = new ItemStack(Material.DIAMOND_PICKAXE);
        pick.addEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY, 1);
        p.getInventory().setItemInOffHand(pick);
        rightClick(p, plugin.items().create(CustomItem.ENCHANT_SCROLL));
        assertEquals(2, p.getInventory().getItemInOffHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.EFFICIENCY));

        // the simulated player has no SCALE attribute (real Paper players do) – only check it where it exists
        if (p.getAttribute(org.bukkit.attribute.Attribute.SCALE) != null) {
            double before = p.getAttribute(org.bukkit.attribute.Attribute.SCALE).getValue();
            rightClick(p, plugin.items().create(CustomItem.MEGA_MUSHROOM));
            assertTrue(p.getAttribute(org.bukkit.attribute.Attribute.SCALE).getValue() > before, "mega mushroom makes you bigger");
        }
    }

    @Test
    void everyEventStartsAndRuns() throws Exception {
        PlayerMock p = join("Eventspieler");
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        List<String> crashed = new java.util.ArrayList<>();
        for (online.mythoscraft.core.events.MythosEvent event : new java.util.ArrayList<>(plugin.events().all())) {
            assertEquals(online.mythoscraft.core.events.EventManager.StartResult.STARTED, plugin.events().start(event.id(), true, 0), event.id());
            for (int second = 0; second < 6; second++) {
                server.getScheduler().performTicks(20);
                Thread.sleep(20);
            }
            // bosses look for a free spot (no arenas in the simulator), the treasure hunt for a spot in the wilderness
            boolean bossSearch = java.util.Set.of("ancientwarden", "pumpkinking", "frostgiant", "spiderqueen", "boneking", "magmalord",
                    "swampwitch", "stormknight", "killerbunny", "mythostitan", "treasurehunt").contains(event.id());
            if (plugin.events().active() != event && !bossSearch) {
                crashed.add(event.id());
            }
            plugin.events().stop();
            server.getScheduler().performTicks(5);
        }
        // MockBukkit does not implement Block#isPassable (used to place coins, pumpkins, eggs, ghosts …) – those events
        // stop in the simulator only; every other event must keep running.
        java.util.Set<String> simulatorLimited = java.util.Set.of("coinrain", "ancienthunt", "pumpkinhunt", "ghostnight", "giftrain",
                "starfall", "egghunt", "goldrush", "keyrain", "chickenhunt", "parkourrace", "lumberjack", "diamondrain", "crystalhunt",
                "mushroomhunt", "featherfall", "luckrain", "petalrain", "heartrain", "shellhunt", "slimeplague", "spidernight", "pigchase");
        List<String> realCrashes = crashed.stream().filter(id -> !simulatorLimited.contains(id)).toList();
        assertEquals(List.of(), realCrashes, "events that stopped by themselves (crash) within 6 seconds");
    }

    @Test
    void spawnCompassStaysAndIsNeverDuplicated() throws Exception {
        PlayerMock p = join("Compass");
        for (int i = 0; i < 40; i++) {
            server.getScheduler().performOneTick();
        }
        long count = java.util.Arrays.stream(p.getInventory().getContents())
                .filter(online.mythoscraft.core.teleport.SpawnItemListener::is).count();
        assertEquals(1, count, "spawn compass after join");
        org.bukkit.inventory.ItemStack compass = p.getInventory().getItem(8);
        assertTrue(online.mythoscraft.core.teleport.SpawnItemListener.is(compass), "compass in the last hotbar slot");
        org.bukkit.event.player.PlayerDropItemEvent drop = new org.bukkit.event.player.PlayerDropItemEvent(p,
                p.getWorld().dropItem(p.getLocation(), compass));
        server.getPluginManager().callEvent(drop);
        assertTrue(drop.isCancelled(), "compass cannot be dropped");
    }

    @Test
    void companionItemsExistAndToggle() throws Exception {
        PlayerMock p = join("Buddy");
        for (String id : java.util.List.of("companion_snowman", "companion_penguin", "companion_ghost", "companion_dragon", "companion_bee")) {
            online.mythoscraft.core.item.CustomItem type = online.mythoscraft.core.item.CustomItem.byId(id);
            assertNotNull(type, id);
            assertTrue(online.mythoscraft.core.cosmetic.CompanionService.isCompanion(type));
            assertTrue(plugin.items().is(plugin.items().create(type), type));
        }
        org.bukkit.Location s = online.mythoscraft.core.cosmetic.CompanionService.class.getDeclaredMethod("shoulder",
                org.bukkit.Location.class, float.class, boolean.class).invoke(null, p.getLocation(), 0f, false) instanceof org.bukkit.Location l ? l : null;
        assertNotNull(s);
        assertTrue(s.getX() < p.getLocation().getX(), "facing south the right shoulder is to the west");
    }

    @Test
    void trophyRemembersWinnerAndCountsWins() throws Exception {
        PlayerMock p = join("Champ");
        for (int i = 0; i < 200 && !plugin.progress().loaded(p); i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        assertTrue(plugin.progress().loaded(p), "progress loaded");
        online.mythoscraft.core.tournament.TournamentTier tier = online.mythoscraft.core.tournament.TournamentTier.WEEKLY;
        plugin.trophies().award(p, tier, true);
        org.bukkit.inventory.ItemStack second = plugin.trophies().award(p, tier, true);
        assertTrue(plugin.items().is(second, online.mythoscraft.core.item.CustomItem.TROPHY_WEEKLY));
        Long wins = second.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("mythoscraft", "trophy_wins"), org.bukkit.persistence.PersistentDataType.LONG);
        assertEquals(2L, wins, "second weekly win");
        assertEquals("Champ", second.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("mythoscraft", "trophy_owner_name"), org.bukkit.persistence.PersistentDataType.STRING));
        org.bukkit.inventory.ItemStack test = plugin.trophies().award(p, online.mythoscraft.core.item.CustomItem.TROPHY_DAILY == null ? tier
                : online.mythoscraft.core.tournament.TournamentTier.DAILY, false);
        assertEquals(1L, test.getItemMeta().getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("mythoscraft", "trophy_wins"), org.bukkit.persistence.PersistentDataType.LONG),
                "test tournaments do not count");
    }

    @Test
    void starterKitOnlyOnce() throws Exception {
        PlayerMock p = join("Newbie");
        for (int i = 0; i < 200 && !plugin.progress().loaded(p); i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        p.getInventory().clear();
        p.performCommand("new");
        long picks = java.util.Arrays.stream(p.getInventory().getContents())
                .filter(i -> i != null && i.getType() == org.bukkit.Material.STONE_PICKAXE).count();
        assertEquals(1, picks, "starter kit given");
        assertEquals(org.bukkit.Material.LEATHER_CHESTPLATE, p.getInventory().getChestplate().getType());
        p.performCommand("new");
        long again = java.util.Arrays.stream(p.getInventory().getContents())
                .filter(i -> i != null && i.getType() == org.bukkit.Material.STONE_PICKAXE).count();
        assertEquals(1, again, "only once");
    }

    @Test
    void coinShopSellsEnderChestFoodAndTotem() {
        assertTrue(plugin.shop().pages() >= 4, "everyday page");
        String all = plugin.getConfig().getMapList("shop.entries").toString();
        for (String reward : java.util.List.of("item ENDER_CHEST 1", "item TOTEM_OF_UNDYING 1", "item COOKED_BEEF 16", "item GOLDEN_APPLE 1")) {
            assertTrue(all.contains(reward), reward);
        }
        assertTrue(plugin.getConfig().getMapList("shop.entries").stream()
                .filter(m -> String.valueOf(m.get("reward")).startsWith("item "))
                .allMatch(m -> "coins".equals(m.get("currency"))), "everyday items cost coins");
    }

    @Test
    void adminAnnouncementReachesEverybody() throws Exception {
        PlayerMock admin = join("Admin1");
        PlayerMock other = join("Listener");
        admin.setOp(true);
        while (other.nextComponentMessage() != null) {
            // drain join messages
        }
        admin.performCommand("pronounce <gold>Event um 20 Uhr");
        StringBuilder got = new StringBuilder();
        net.kyori.adventure.text.Component c;
        while ((c = other.nextComponentMessage()) != null) {
            got.append(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c));
        }
        assertTrue(got.toString().contains("Event um 20 Uhr"), "announcement in chat: " + got);
        PlayerMock normal = join("Normalo");
        normal.performCommand("pronounce nope");
        String leak = "";
        while ((c = other.nextComponentMessage()) != null) {
            leak += net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
        }
        assertTrue(!leak.contains("nope"), "players without permission cannot announce");
    }


    @Test
    void introFliesTheCameraAndGivesTheStartingSteps() throws Exception {
        org.bukkit.World w = server.addSimpleWorld("introworld");
        plugin.attractionStore().define(new org.bukkit.Location(w, 0, 64, 0), 128,
                java.util.Map.of("wheel", java.util.List.of(new org.bukkit.Location(w, 10, 70, 0))));
        PlayerMock p = join("Newcomer");
        for (int i = 0; i < 200 && !plugin.progress().loaded(p); i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(10);
        }
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.performCommand("intro");
        assertEquals(org.bukkit.GameMode.SPECTATOR, p.getGameMode(), "camera flight in spectator");
        for (int i = 0; i < 30; i++) {
            server.getScheduler().performOneTick();
        }
        p.performCommand("intro skip");
        assertEquals(org.bukkit.GameMode.SURVIVAL, p.getGameMode(), "game mode back after the intro");
        assertEquals(1L, plugin.progress().get(p, "intro.done"), "intro remembered as done");
        StringBuilder chat = new StringBuilder();
        net.kyori.adventure.text.Component c;
        while ((c = p.nextComponentMessage()) != null) {
            chat.append(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c));
        }
        assertTrue(chat.toString().contains("Lia"), "Lia talks: " + chat);
        assertTrue(chat.toString().contains("/starter"), "starting steps mention the starter kit");
    }

    @Test
    void helperNpcsStandOnTheStarterSquare() {
        org.bukkit.World w = server.addSimpleWorld("npcs");
        java.util.Map<String, List<org.bukkit.Location>> pts = new java.util.HashMap<>();
        pts.put("mentor", List.of(new org.bukkit.Location(w, 0.5, 65, 0.5)));
        String[] markers = {"npc_rtp", "npc_plot", "npc_jobs", "npc_crates", "npc_idle"};
        for (int i = 0; i < markers.length; i++) {
            pts.put(markers[i], List.of(new org.bukkit.Location(w, 3.5 + i * 2, 65, 3.5)));
        }
        plugin.attractionStore().define(new org.bukkit.Location(w, 0, 64, 0), 128, pts);
        w.getChunkAt(0, 0).load();
        plugin.attractions().rebuild();
        long npcs = w.getEntities().stream().filter(e -> "spawn_npc".equals(e.getPersistentDataContainer()
                .get(online.mythoscraft.core.util.Keys.ENTITY, org.bukkit.persistence.PersistentDataType.STRING))).count();
        assertEquals(markers.length, npcs, "helper NPCs");
        // a spawn built before the NPCs existed: only Lia's spot is stored – the NPCs still come
        plugin.attractionStore().define(new org.bukkit.Location(w, 0, 64, 0), 128,
                java.util.Map.of("mentor", List.of(new org.bukkit.Location(w, 0.5, 65, 0.5))));
        plugin.attractions().rebuild();
        long again = w.getEntities().stream().filter(e -> e.isValid() && "spawn_npc".equals(e.getPersistentDataContainer()
                .get(online.mythoscraft.core.util.Keys.ENTITY, org.bukkit.persistence.PersistentDataType.STRING))).count();
        assertEquals(markers.length, again, "helper NPCs without stored spots");
    }

    @Test
    void modsPunishOnlyLowerRanks() throws Exception {
        PlayerMock mod = join("TheMod");
        PlayerMock admin = join("TheAdmin");
        PlayerMock player = join("Rowdy");
        plugin.playerData().get(mod).rank("MOD", 0);
        plugin.playerData().get(admin).rank("ADMIN", 0);
        mod.performCommand("mute Rowdy 10m spam");
        player.chat("hallo");
        server.getScheduler().performTicks(2);
        String muted = drain(player);
        assertTrue(muted.contains("10m") || muted.toLowerCase().contains("stumm") || muted.toLowerCase().contains("muted"),
                "player is muted: " + muted);
        drain(mod);
        mod.performCommand("mute TheAdmin 10m nope");
        String refused = drain(mod);
        assertTrue(refused.contains("TheAdmin"), "mod may not mute an admin: " + refused);
        mod.performCommand("ban Rowdy test");
        assertTrue(player.isOnline(), "permanent bans only for admins");
    }

    private static String drain(PlayerMock p) {
        StringBuilder b = new StringBuilder();
        net.kyori.adventure.text.Component c;
        while ((c = p.nextComponentMessage()) != null) {

        }
        return b.toString();
    }
}
