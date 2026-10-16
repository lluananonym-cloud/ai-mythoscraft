package online.mythoscraft.core.smoke;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.arena.Arena;
import online.mythoscraft.core.tournament.TournamentTier;
import online.mythoscraft.core.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tournament arena survives a reload/restart, the spawn double jump glides without fall damage. */
class SpawnAndArenaSmokeTest {

    private ServerMock server;
    private MythosCraft plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(MythosCraft.class);
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

    @Test
    void dailyArenaIsFoundAgainAfterARestart() {
        World w = server.addSimpleWorld("arenas");
        Arena arena = plugin.arenas().create("daily", w.getName(), Cuboid.of(0, 60, 0, 60, 90, 60));
        assertNotNull(arena);
        plugin.tournaments().setArena(TournamentTier.DAILY, "daily");
        // the config entry gets lost (old file uploaded, other tool rewrote it …) and the server restarts
        plugin.getConfig().set("tournaments.daily.arena", "");
        plugin.saveConfig();
        plugin.reloadAll();
        assertNotNull(plugin.tournaments().arenaOf(TournamentTier.DAILY), "daily arena lost after reload");
        assertEquals("daily", plugin.tournaments().arenaName(TournamentTier.DAILY));
    }

    @Test
    void savedArenaLoadsAgain() throws Exception {
        World w = server.addSimpleWorld("arenas2");
        plugin.arenas().create("weekly", w.getName(), Cuboid.of(0, 60, 0, 80, 90, 80));
        plugin.arenas().save();
        for (int i = 0; i < 100; i++) {
            server.getScheduler().performOneTick();
            Thread.sleep(5);
        }
        java.nio.file.Path file = plugin.getDataFolder().toPath().resolve("arenas.yml");
        assertTrue(java.nio.file.Files.readString(file).contains("weekly"), "arena was not written");
        plugin.reloadAll();
        assertNotNull(plugin.arenas().get("weekly"), "saved arena could not be loaded again");
    }

    @Test
    void doubleJumpAtSpawnGlidesWithoutFallDamage() {
        World w = server.addSimpleWorld("spawnworld");
        Location center = new Location(w, 0.5, 64, 0.5);
        plugin.attractionStore().define(center, 96, Map.of("deco", List.of()));
        PlayerMock p = server.addPlayer("Gleiter");
        p.teleport(new Location(w, 5, 65, 5));
        p.setOnGround(true);
        for (int i = 0; i < 60; i++) {
            server.getScheduler().performOneTick();
        }
        assertTrue(p.getAllowFlight(), "double jump is armed on the spawn");
        PlayerToggleFlightEvent jump = new PlayerToggleFlightEvent(p, true);
        server.getPluginManager().callEvent(jump);
        assertTrue(jump.isCancelled(), "no real flying");
        assertFalse(p.getAllowFlight(), "only one glide");
        p.setOnGround(false);
        EntityDamageEvent fall = new EntityDamageEvent(p, EntityDamageEvent.DamageCause.FALL,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 6);
        server.getPluginManager().callEvent(fall);
        assertTrue(fall.isCancelled(), "no fall damage after gliding");
        // outside the spawn nothing is armed
        PlayerMock q = server.addPlayer("Draussen");
        q.teleport(new Location(w, 500, 65, 500));
        q.setOnGround(true);
        for (int i = 0; i < 60; i++) {
            server.getScheduler().performOneTick();
        }
        assertFalse(q.getAllowFlight(), "no glide outside the spawn");
    }
}
