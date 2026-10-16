package online.mythoscraft.core.smoke;

import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.crate.CrateType;
import online.mythoscraft.core.item.CustomItem;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the whole plugin on a simulated Paper server and checks that every configuration file loads without
 * warnings and that all crates, ranks, events, shop offers and items are usable.
 */
class PluginSmokeTest {

    private ServerMock server;
    private MythosCraft plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(MythosCraft.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void pluginStartsWithoutConfigWarnings() {
        assertTrue(plugin.isEnabled(), "plugin did not enable");
        assertEquals(java.util.List.of(), plugin.configProblems().list(), "config warnings");
    }

    @Test
    void contentIsLoaded() {
        assertEquals(27, plugin.crates().types().size(), "crate types");
        for (CrateType type : plugin.crates().types()) {
            assertFalse(type.table().isEmpty(), type.id() + " has no rewards");
            assertNotNull(plugin.crates().createKey(type.id(), 1), "key for " + type.id());
        }
        assertEquals(14, plugin.ranks().ranks().size(), "ranks");
        assertTrue(plugin.shop().entries().size() >= 30, "shop entries: " + plugin.shop().entries().size());
        assertEquals(40, plugin.events().all().size(), "events");
        for (String rank : new String[]{"VIP", "VIPPLUS", "ELITE", "HELD", "LEGENDE", "TITAN"}) {
            assertNotNull(plugin.vouchers().create(rank, "30d", 1), "voucher " + rank);
        }
    }

    @Test
    void everyItemCanBeCreated() {
        for (CustomItem item : CustomItem.values()) {
            ItemStack stack = plugin.items().create(item);
            assertNotNull(stack, item.id());
            assertEquals(item, plugin.items().identify(stack), item.id());
            assertNotNull(stack.getItemMeta().displayName(), item.id() + " has no name");
        }
    }
}
