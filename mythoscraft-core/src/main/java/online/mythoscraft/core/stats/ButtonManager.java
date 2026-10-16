package online.mythoscraft.core.stats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import online.mythoscraft.core.MythosCraft;
import online.mythoscraft.core.config.YamlStore;
import online.mythoscraft.core.util.Cooldowns;
import online.mythoscraft.core.util.Keys;
import online.mythoscraft.core.util.LocationCodec;
import online.mythoscraft.core.util.SoundSpec;
import online.mythoscraft.core.util.Text;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clickable command holograms ("Buttons"): floating text with an invisible hitbox. Right- or left-click runs the
 * command as the player – for everyone who can't (or doesn't want to) type in chat: Shop, RTP, Auktionshaus …
 */
public final class ButtonManager implements Listener {

    public static final class Button {
        final String name;
        final Location location;
        final String command;
        String text;
        TextDisplay display;
        Interaction hitbox;

        Button(String name, Location location, String command, String text) {
            this.name = name;
            this.location = location;
            this.command = command;
            this.text = text;
        }

        public String name() {
            return name;
        }

        public String command() {
            return command;
        }

        public Location location() {
            return location.clone();
        }
    }

    private static final String TAG = "button";

    private final MythosCraft plugin;
    private final YamlStore store;
    private final Cooldowns cooldowns = new Cooldowns();
    private final Map<String, Button> buttons = new LinkedHashMap<>();

    public ButtonManager(MythosCraft plugin) {
        this.plugin = plugin;
        this.store = new YamlStore(plugin, "data/buttons.yml");
    }

    public void reload() {
        buttons.values().forEach(this::despawn);
        buttons.clear();
        YamlConfiguration yaml = store.load(false);
        ConfigurationSection sec = yaml.getConfigurationSection("buttons");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                Location loc = LocationCodec.fromString(sec.getString(name + ".location"));
                String cmd = sec.getString(name + ".command", "");
                if (name.equals("spawn_event") && cmd.equals("event")) {
                    cmd = "event tp"; // the spawn event button beams you to the running event
                }
                if (loc == null || cmd.isBlank()) {
                    plugin.getLogger().warning("Button " + name + " skipped (world missing or no command)");
                    continue;
                }
                buttons.put(name, new Button(name, loc, cmd, sec.getString(name + ".text")));
            }
        }
        for (Button b : buttons.values()) {
            if (b.location.isChunkLoaded()) {
                spawn(b);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Button b : buttons.values()) {
            yaml.set("buttons." + b.name + ".location", LocationCodec.toString(b.location));
            yaml.set("buttons." + b.name + ".command", b.command);
            if (b.text != null) {
                yaml.set("buttons." + b.name + ".text", b.text);
            }
        }
        store.replace(yaml);
        store.saveAsync(plugin.tasks());
    }

    /** @param text MiniMessage text, null = preset from messages.yml (buttons.presets.&lt;name&gt;) or the command */
    public Button create(String name, Location location, String command, String text) {
        String id = name.toLowerCase(Locale.ROOT);
        Button old = buttons.remove(id);
        if (old != null) {
            despawn(old);
        }
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        Button b = new Button(id, location, cmd, text);
        buttons.put(id, b);
        save();
        spawn(b);
        return b;
    }

    public boolean setText(String name, String text) {
        Button b = buttons.get(name.toLowerCase(Locale.ROOT));
        if (b == null) {
            return false;
        }
        b.text = text;
        save();
        if (b.display != null && b.display.isValid()) {
            b.display.text(render(b));
        }
        return true;
    }

    public boolean delete(String name) {
        Button b = buttons.remove(name.toLowerCase(Locale.ROOT));
        if (b == null) {
            return false;
        }
        despawn(b);
        save();
        return true;
    }

    public Collection<Button> all() {
        return buttons.values();
    }

    private Component render(Button b) {
        if (b.text != null && !b.text.isBlank()) {
            return Text.parse(b.text.replace("\\n", "\n"));
        }
        String preset = "buttons.presets." + b.name;
        if (plugin.messages().has(preset)) {
            return Component.join(JoinConfiguration.newlines(), plugin.messages().getList(preset));
        }
        return plugin.messages().get("buttons.default", "command", "/" + b.command);
    }

    private void spawn(Button b) {
        despawn(b);
        String tag = TAG + ":" + b.name;
        b.display = b.location.getWorld().spawn(b.location, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setLineWidth(200);
            t.setBackgroundColor(Color.fromARGB(0x88, 0x2E, 0x10, 0x65));
            t.setViewRange(0.5f);
            t.text(render(b));
            t.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
        b.hitbox = b.location.getWorld().spawn(b.location.clone().add(0, -0.35, 0), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.8f);
            i.setInteractionHeight(1.1f);
            i.setResponsive(true);
            i.getPersistentDataContainer().set(Keys.ENTITY, PersistentDataType.STRING, tag);
        });
    }

    private void despawn(Button b) {
        if (b.display != null && b.display.isValid()) {
            b.display.remove();
        }
        if (b.hitbox != null && b.hitbox.isValid()) {
            b.hitbox.remove();
        }
        b.display = null;
        b.hitbox = null;
    }

    public void onChunkLoad(Chunk chunk) {
        for (Button b : buttons.values()) {
            if (inChunk(b, chunk) && (b.display == null || !b.display.isValid())) {
                spawn(b);
            }
        }
    }

    public void onChunkUnload(Chunk chunk) {
        for (Button b : buttons.values()) {
            if (inChunk(b, chunk)) {
                despawn(b);
            }
        }
    }

    private static boolean inChunk(Button b, Chunk chunk) {
        return b.location.getWorld().equals(chunk.getWorld()) && b.location.getBlockX() >> 4 == chunk.getX()
                && b.location.getBlockZ() >> 4 == chunk.getZ();
    }

    private Button of(Entity e) {
        String tag = e.getPersistentDataContainer().get(Keys.ENTITY, PersistentDataType.STRING);
        return tag != null && tag.startsWith(TAG + ":") ? buttons.get(tag.substring(TAG.length() + 1)) : null;
    }

    private void press(Player p, Button b) {
        if (cooldowns.remainingSeconds("button", p.getUniqueId()) > 0) {
            return;
        }
        cooldowns.set("button", p.getUniqueId(), 800);
        SoundSpec.of("block.note_block.chime", 0.6f, 1.6f).play(p);
        p.performCommand(b.command.replace("{player}", p.getName()));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onRightClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Button b = of(event.getRightClicked());
        if (b != null) {
            event.setCancelled(true);
            press(event.getPlayer(), b);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        Button b = of(event.getEntity());
        if (b != null) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player p) {
                press(p, b);
            }
        }
    }

    /** Names of the buttons {@code /spawn place} creates (in the order of the spawn's button markers). */
    public static List<String[]> spawnPresets() {
        List<String[]> list = new ArrayList<>();
        list.add(new String[]{"shop", "shop"});
        list.add(new String[]{"auktion", "ah"});
        list.add(new String[]{"auftraege", "auftrag"});
        list.add(new String[]{"rtp", "rtp"});
        list.add(new String[]{"turnier", "tournament join"});
        list.add(new String[]{"event", "event tp"});
        list.add(new String[]{"daily", "daily"});
        list.add(new String[]{"stats", "stats"});
        list.add(new String[]{"clan", "clan"});
        list.add(new String[]{"musik", "musik"});
        list.add(new String[]{"aufgaben", "aufgaben"});
        list.add(new String[]{"parkour", "parkour"});
        list.add(new String[]{"ringe", "ringe"});
        list.add(new String[]{"dropper", "dropper"});
        list.add(new String[]{"labyrinth", "labyrinth"});
        list.add(new String[]{"wetten", "wette"});
        list.add(new String[]{"blockparty", "blockparty"});
        list.add(new String[]{"spleef", "spleef"});
        list.add(new String[]{"zielschiessen", "zielschiessen"});
        return list;
    }

    public void shutdown() {
        buttons.values().forEach(this::despawn);
    }
}
