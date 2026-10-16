package online.mythoscraft.core.item;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemRarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static online.mythoscraft.core.item.CustomItem.Kind.ARMOR;
import static online.mythoscraft.core.item.CustomItem.Kind.CHARM;
import static online.mythoscraft.core.item.CustomItem.Kind.CONSUMABLE;
import static online.mythoscraft.core.item.CustomItem.Kind.TOOL;
import static online.mythoscraft.core.item.CustomItem.Kind.TROPHY;
import static online.mythoscraft.core.item.CustomItem.Kind.VISUAL;
import static online.mythoscraft.core.item.CustomItem.Kind.WEAPON;
import static online.mythoscraft.core.item.ItemCollection.ANCIENT;
import static online.mythoscraft.core.item.ItemCollection.BEGLEITER;
import static online.mythoscraft.core.item.ItemCollection.CELESTIAL;
import static online.mythoscraft.core.item.ItemCollection.CORE;
import static online.mythoscraft.core.item.ItemCollection.DRAGON;
import static online.mythoscraft.core.item.ItemCollection.FARM;
import static online.mythoscraft.core.item.ItemCollection.SPRING;
import static online.mythoscraft.core.item.ItemCollection.OCEAN;
import static online.mythoscraft.core.item.ItemCollection.FOREST;
import static online.mythoscraft.core.item.ItemCollection.SKY;
import static online.mythoscraft.core.item.ItemCollection.CRYSTAL;
import static online.mythoscraft.core.item.ItemCollection.STORM;
import static online.mythoscraft.core.item.ItemCollection.FROSTFEST;
import static online.mythoscraft.core.item.ItemCollection.HALLOWEEN;
import static online.mythoscraft.core.item.ItemCollection.INFERNO;
import static online.mythoscraft.core.item.ItemCollection.TOURNAMENT;
import static online.mythoscraft.core.item.ItemCollection.MILLENNIUM;
import static online.mythoscraft.core.item.ItemCollection.COSMETIC;
import static online.mythoscraft.core.item.ItemCollection.SPACE;
import static online.mythoscraft.core.item.ItemCollection.SPAWNER;
import static online.mythoscraft.core.item.ItemCollection.SILVESTER;
import static org.bukkit.inventory.ItemRarity.EPIC;
import static org.bukkit.inventory.ItemRarity.RARE;
import static org.bukkit.inventory.ItemRarity.UNCOMMON;

/**
 * Catalogue of all custom items. The id doubles as custom_model_data string for the resource pack; clients without
 * the pack see the vanilla base item. Names and lore live in messages.yml (items.&lt;id&gt;).
 */
public enum CustomItem {
    // ---- core tools ---------------------------------------------------------------------------------------
    MYTHOS_HAMMER(spec("mythos_hammer", Material.NETHERITE_PICKAXE, EPIC, TOOL, CORE)),
    EXCAVATOR(spec("excavator", Material.NETHERITE_SHOVEL, EPIC, TOOL, CORE)),
    TIMBER_AXE(spec("timber_axe", Material.NETHERITE_AXE, EPIC, TOOL, CORE)),
    RELIC_COMPASS(spec("relic_compass", Material.COMPASS, RARE, TOOL, ANCIENT)),
    REGION_WAND(spec("region_wand", Material.BLAZE_ROD, RARE, TOOL, CORE)),
    GRAPPLING_HOOK(spec("grappling_hook", Material.FISHING_ROD, RARE, TOOL, CORE)),
    MYTHOS_BACKPACK(spec("mythos_backpack", Material.PAPER, EPIC, TOOL, CORE).stack(1)),
    HEALING_TOTEM(spec("healing_totem", Material.PAPER, EPIC, TOOL, CORE).stack(1).cooldown()),
    MAGNET_CHARM(spec("magnet_charm", Material.PAPER, RARE, CHARM, CORE).stack(1)),
    MINER_LANTERN(spec("miner_lantern", Material.PAPER, RARE, CHARM, CORE).stack(1)),
    XP_TOME(spec("xp_tome", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),
    RETURN_FEATHER(spec("return_feather", Material.PAPER, UNCOMMON, CONSUMABLE, CORE).stack(16)),
    LUCKY_CLOVER(spec("lucky_clover", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),
    // personal boosters, mutations, meteor shower
    COIN_BOOSTER(spec("coin_booster", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),
    GEM_BOOSTER(spec("gem_booster", Material.PAPER, EPIC, CONSUMABLE, CORE).stack(16)),
    XP_BOOSTER(spec("xp_booster", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),
    FIND_BOOSTER(spec("find_booster", Material.PAPER, EPIC, CONSUMABLE, CORE).stack(16)),
    MUTATION_SERUM(spec("mutation_serum", Material.PAPER, EPIC, CONSUMABLE, CORE).stack(16)),
    METEOR_SHOWER(spec("meteor_shower", Material.PAPER, EPIC, CONSUMABLE, CORE).stack(16)),
    // Mythos magic
    MYTHOS_WAND(spec("mythos_wand", Material.BLAZE_ROD, EPIC, TOOL, CORE)),
    SPELL_SCROLL(spec("spell_scroll", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),
    MANA_ELIXIR(spec("mana_elixir", Material.PAPER, UNCOMMON, CONSUMABLE, CORE).stack(16)),
    HASTE_RUNE(spec("haste_rune", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),

    // ---- ancient ------------------------------------------------------------------------------------------
    WARDEN_BLADE(spec("warden_blade", Material.NETHERITE_SWORD, EPIC, WEAPON, ANCIENT).weapon(9, 1.6).cooldown()),

    // ---- halloween ("Schauernacht") -----------------------------------------------------------------------
    PUMPKIN_SCYTHE(spec("pumpkin_scythe", Material.NETHERITE_HOE, EPIC, WEAPON, HALLOWEEN).weapon(10, 1.0)),
    BAT_BLADE(spec("bat_blade", Material.NETHERITE_SWORD, EPIC, WEAPON, HALLOWEEN).weapon(8, 1.8)),
    GHOST_BOW(spec("ghost_bow", Material.BOW, EPIC, WEAPON, HALLOWEEN)),
    WITCH_BROOM(spec("witch_broom", Material.BLAZE_ROD, EPIC, TOOL, HALLOWEEN).cooldown()),
    GHOST_LANTERN(spec("ghost_lantern", Material.PAPER, EPIC, TOOL, HALLOWEEN).stack(1).cooldown()),
    CANDY_BUCKET(spec("candy_bucket", Material.PAPER, RARE, TOOL, HALLOWEEN).stack(1).cooldown()),
    PUMPKIN_BOMB(spec("pumpkin_bomb", Material.SNOWBALL, RARE, CONSUMABLE, HALLOWEEN).stack(16)),
    CANDY_CORN(spec("candy_corn", Material.COOKIE, UNCOMMON, CONSUMABLE, HALLOWEEN).food(2, 1f)),
    LOLLIPOP(spec("lollipop", Material.COOKIE, UNCOMMON, CONSUMABLE, HALLOWEEN).food(3, 2f)),
    CARAMEL_APPLE(spec("caramel_apple", Material.APPLE, RARE, CONSUMABLE, HALLOWEEN).food(6, 6f)),

    // ---- frostfest ----------------------------------------------------------------------------------------
    FROST_BLADE(spec("frost_blade", Material.NETHERITE_SWORD, EPIC, WEAPON, FROSTFEST).weapon(8.5, 1.6)),
    CANDY_CANE(spec("candy_cane", Material.NETHERITE_SWORD, RARE, WEAPON, FROSTFEST).weapon(6, 2.0)),
    ICE_WAND(spec("ice_wand", Material.BLAZE_ROD, EPIC, TOOL, FROSTFEST).cooldown()),
    SNOW_CANNON(spec("snow_cannon", Material.PAPER, RARE, TOOL, FROSTFEST).stack(1).cooldown()),
    GIFT_BOX(spec("gift_box", Material.PAPER, RARE, CONSUMABLE, FROSTFEST).stack(16)),
    HOT_COCOA(spec("hot_cocoa", Material.HONEY_BOTTLE, UNCOMMON, CONSUMABLE, FROSTFEST).food(4, 4f)),

    // ---- celestial ----------------------------------------------------------------------------------------
    STAR_SABER(spec("star_saber", Material.NETHERITE_SWORD, EPIC, WEAPON, CELESTIAL).weapon(10, 1.7).glint()),
    COMET_BOW(spec("comet_bow", Material.BOW, EPIC, WEAPON, CELESTIAL)),
    VOID_SCEPTER(spec("void_scepter", Material.BLAZE_ROD, EPIC, TOOL, CELESTIAL).cooldown()),
    PHOENIX_FEATHER(spec("phoenix_feather", Material.PAPER, EPIC, CHARM, CELESTIAL).stack(1).glint()),
    NEBULA_ORB(spec("nebula_orb", Material.PAPER, EPIC, CHARM, CELESTIAL).stack(1).glint()),

    // ---- inferno ------------------------------------------------------------------------------------------
    INFERNO_AXE(spec("inferno_axe", Material.NETHERITE_AXE, EPIC, WEAPON, INFERNO).weapon(11, 1.0)),
    METEOR_STAFF(spec("meteor_staff", Material.BLAZE_ROD, EPIC, TOOL, INFERNO).cooldown()),
    EMBER_WAND(spec("ember_wand", Material.BLAZE_ROD, RARE, TOOL, INFERNO).cooldown()),
    DRAGON_FLASK(spec("dragon_flask", Material.HONEY_BOTTLE, RARE, CONSUMABLE, INFERNO).food(2, 2f)),

    // ---- farming ("Ernte") ---------------------------------------------------------------------------------
    HARVEST_SICKLE(spec("harvest_sickle", Material.NETHERITE_HOE, EPIC, TOOL, FARM).cooldown()),
    WATERING_CAN(spec("watering_can", Material.PAPER, RARE, TOOL, FARM).stack(1).cooldown()),
    SEED_BAG(spec("seed_bag", Material.PAPER, UNCOMMON, TOOL, FARM).stack(1)),
    LUCKY_ROD(spec("lucky_rod", Material.FISHING_ROD, EPIC, TOOL, FARM)),
    COMPOST_CHARM(spec("compost_charm", Material.PAPER, RARE, CHARM, FARM).stack(1)),
    HONEY_BREAD(spec("honey_bread", Material.BREAD, UNCOMMON, CONSUMABLE, FARM).food(7, 9f)),
    FORTUNE_COOKIE(spec("fortune_cookie", Material.COOKIE, RARE, CONSUMABLE, FARM).food(2, 1f)),

    // ---- storm ("Sturm") ----------------------------------------------------------------------------------
    THUNDER_HAMMER(spec("thunder_hammer", Material.NETHERITE_AXE, EPIC, WEAPON, STORM).weapon(11, 0.9).cooldown()),
    WIND_BLADE(spec("wind_blade", Material.NETHERITE_SWORD, EPIC, WEAPON, STORM).weapon(8, 2.0).cooldown()),
    STORM_BOW(spec("storm_bow", Material.BOW, EPIC, WEAPON, STORM)),
    STATIC_CHARM(spec("static_charm", Material.PAPER, RARE, CHARM, STORM).stack(1)),
    STORM_FLASK(spec("storm_flask", Material.HONEY_BOTTLE, RARE, CONSUMABLE, STORM).food(2, 2f)),

    // ---- dragon ("Drachen") -------------------------------------------------------------------------------
    DRAGON_BLADE(spec("dragon_blade", Material.NETHERITE_SWORD, EPIC, WEAPON, DRAGON).weapon(10.5, 1.6).glint()),
    DRAGON_STAFF(spec("dragon_staff", Material.BLAZE_ROD, EPIC, TOOL, DRAGON).cooldown()),
    DRAGON_SCALE(spec("dragon_scale", Material.PAPER, EPIC, CHARM, DRAGON).stack(1)),
    DRAGON_HEART(spec("dragon_heart", Material.PAPER, EPIC, CONSUMABLE, DRAGON).stack(16).cooldown()),

    // ---- more core items ----------------------------------------------------------------------------------
    SMELTER_PICKAXE(spec("smelter_pickaxe", Material.NETHERITE_PICKAXE, EPIC, TOOL, CORE)),
    BUILDER_WAND(spec("builder_wand", Material.BLAZE_ROD, EPIC, TOOL, CORE).cooldown()),
    ENDER_POUCH(spec("ender_pouch", Material.PAPER, RARE, TOOL, CORE).stack(1)),
    JUMP_CHARM(spec("jump_charm", Material.PAPER, UNCOMMON, CHARM, CORE).stack(1)),
    AQUA_CHARM(spec("aqua_charm", Material.PAPER, RARE, CHARM, CORE).stack(1)),
    COIN_POUCH(spec("coin_pouch", Material.PAPER, UNCOMMON, CONSUMABLE, CORE).stack(16)),
    GEM_POUCH(spec("gem_pouch", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),
    KEY_FRAGMENT(spec("key_fragment", Material.PAPER, RARE, CONSUMABLE, CORE).stack(64)),
    SWIFT_RUNE(spec("swift_rune", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),
    RTP_SCROLL(spec("rtp_scroll", Material.PAPER, UNCOMMON, CONSUMABLE, CORE).stack(16)),
    MYTHOS_ELIXIR(spec("mythos_elixir", Material.HONEY_BOTTLE, EPIC, CONSUMABLE, CORE).food(4, 4f).glint()),

    // ---- third wave: utility, combat, treasure ------------------------------------------------------------
    ORE_PICKAXE(spec("ore_pickaxe", Material.NETHERITE_PICKAXE, EPIC, TOOL, ANCIENT)),
    MOB_NET(spec("mob_net", Material.PAPER, RARE, TOOL, FARM).stack(1)),
    ENDER_BOW(spec("ender_bow", Material.BOW, EPIC, WEAPON, CELESTIAL)),
    BOOMERANG(spec("boomerang", Material.PAPER, EPIC, WEAPON, STORM).stack(1).cooldown()),
    HEALING_FLASK(spec("healing_flask", Material.HONEY_BOTTLE, RARE, CONSUMABLE, CORE).food(1, 1f)),
    WISDOM_CHARM(spec("wisdom_charm", Material.PAPER, RARE, CHARM, ANCIENT).stack(1)),
    TREASURE_MAP(spec("treasure_map", Material.PAPER, EPIC, CONSUMABLE, ANCIENT).stack(16).cooldown()),
    FROST_BOMB(spec("frost_bomb", Material.SNOWBALL, RARE, CONSUMABLE, FROSTFEST).stack(16)),
    SMOKE_BOMB(spec("smoke_bomb", Material.PAPER, RARE, CONSUMABLE, HALLOWEEN).stack(16).cooldown()),

    // ---- spring ("Frühling") ------------------------------------------------------------------------------
    EASTER_EGG(spec("easter_egg", Material.PAPER, RARE, CONSUMABLE, SPRING).stack(16)),
    CARROT_BLADE(spec("carrot_blade", Material.NETHERITE_SWORD, RARE, WEAPON, SPRING).weapon(7, 1.8)),
    BLOSSOM_WAND(spec("blossom_wand", Material.BLAZE_ROD, RARE, TOOL, SPRING).cooldown()),

    // ---- fourth wave: fun and utility --------------------------------------------------------------------
    GOLEM_EGG(spec("golem_egg", Material.PAPER, EPIC, CONSUMABLE, CORE).stack(4).cooldown()),
    LIGHT_STAFF(spec("light_staff", Material.BLAZE_ROD, RARE, TOOL, CORE).cooldown()),
    CLIMBING_GLOVES(spec("climbing_gloves", Material.PAPER, RARE, CHARM, STORM).stack(1)),
    FIREWORK_WAND(spec("firework_wand", Material.BLAZE_ROD, RARE, TOOL, CELESTIAL).cooldown()),
    GRAVITY_GAUNTLET(spec("gravity_gauntlet", Material.PAPER, EPIC, TOOL, STORM).stack(1).cooldown()),
    SHIELD_CRYSTAL(spec("shield_crystal", Material.PAPER, EPIC, CONSUMABLE, INFERNO).stack(8).cooldown()),
    TIME_SAND(spec("time_sand", Material.PAPER, RARE, TOOL, CELESTIAL).stack(1).cooldown()),
    WEATHER_ORB(spec("weather_orb", Material.PAPER, RARE, TOOL, FROSTFEST).stack(1).cooldown()),
    ORE_COMPASS(spec("ore_compass", Material.PAPER, EPIC, TOOL, ANCIENT).stack(1).cooldown()),
    MEGA_MUSHROOM(spec("mega_mushroom", Material.PAPER, EPIC, CONSUMABLE, FARM).stack(8).cooldown()),
    SHRINK_POTION(spec("shrink_potion", Material.HONEY_BOTTLE, EPIC, CONSUMABLE, FARM).food(1, 1f)),

    // ---- fifth wave ---------------------------------------------------------------------------------------
    RETURN_CRYSTAL(spec("return_crystal", Material.PAPER, EPIC, TOOL, CELESTIAL).stack(1).cooldown()),
    SPONGE_BOMB(spec("sponge_bomb", Material.PAPER, RARE, CONSUMABLE, FROSTFEST).stack(16).cooldown()),
    REPAIR_HAMMER(spec("repair_hammer", Material.PAPER, RARE, CONSUMABLE, CORE).stack(8)),
    ENCHANT_SCROLL(spec("enchant_scroll", Material.PAPER, EPIC, CONSUMABLE, ANCIENT).stack(8)),
    CLOUD_BOTTLE(spec("cloud_bottle", Material.PAPER, RARE, TOOL, STORM).stack(1).cooldown()),
    HUNGER_CHARM(spec("hunger_charm", Material.PAPER, RARE, CHARM, FARM).stack(1)),
    SOUL_JAR(spec("soul_jar", Material.PAPER, EPIC, TOOL, ANCIENT).stack(1)),
    LUCKY_DICE(spec("lucky_dice", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16).cooldown()),

    // ---- sixth wave: travel, fun and cosmetics -----------------------------------------------------------
    FROST_WALKER_CHARM(spec("frost_walker_charm", Material.PAPER, RARE, CHARM, FROSTFEST).stack(1)),
    HOME_STONE(spec("home_stone", Material.PAPER, RARE, CONSUMABLE, CORE).stack(16)),
    MAGIC_BEAN(spec("magic_bean", Material.PAPER, EPIC, CONSUMABLE, SPRING).stack(8)),
    WEB_SHOOTER(spec("web_shooter", Material.PAPER, RARE, WEAPON, HALLOWEEN).stack(1).cooldown()),
    PLAYER_TRACKER(spec("player_tracker", Material.PAPER, EPIC, TOOL, STORM).stack(1).cooldown()),
    RAINBOW_POTION(spec("rainbow_potion", Material.HONEY_BOTTLE, RARE, CONSUMABLE, CELESTIAL).food(2, 2f)),
    CONFETTI_CANNON(spec("confetti_cannon", Material.PAPER, RARE, TOOL, CELESTIAL).stack(1).cooldown()),
    LUCKY_HORSESHOE(spec("lucky_horseshoe", Material.PAPER, RARE, CHARM, FARM).stack(1)),
    PARTY_HAT(spec("party_hat", Material.PAPER, RARE, TOOL, CELESTIAL).stack(1).hat()),
    GOLDEN_CROWN(spec("golden_crown", Material.PAPER, EPIC, TOOL, CORE).stack(1).hat().glint()),

    // ---- ocean ("Ozean") ------------------------------------------------------------------------------------
    CORAL_BLADE(spec("coral_blade", Material.NETHERITE_SWORD, EPIC, WEAPON, OCEAN).weapon(8.5, 1.6)),
    TIDE_STAFF(spec("tide_staff", Material.BLAZE_ROD, EPIC, TOOL, OCEAN).cooldown()),
    PEARL_CHARM(spec("pearl_charm", Material.PAPER, RARE, CHARM, OCEAN).stack(1)),
    SHELL_HORN(spec("shell_horn", Material.PAPER, EPIC, TOOL, OCEAN).stack(1).cooldown()),
    KRAKEN_INK(spec("kraken_ink", Material.SNOWBALL, RARE, CONSUMABLE, OCEAN).stack(16)),
    DIVING_FLASK(spec("diving_flask", Material.HONEY_BOTTLE, UNCOMMON, CONSUMABLE, OCEAN).food(2, 2f)),
    SUSHI(spec("sushi", Material.COOKIE, UNCOMMON, CONSUMABLE, OCEAN).food(6, 7f)),
    TREASURE_NET(spec("treasure_net", Material.PAPER, RARE, CONSUMABLE, OCEAN).stack(16).cooldown()),

    // ---- forest ("Wald") -----------------------------------------------------------------------------------
    DRUID_STAFF(spec("druid_staff", Material.BLAZE_ROD, EPIC, TOOL, FOREST).cooldown()),
    LEAF_BOW(spec("leaf_bow", Material.BOW, EPIC, WEAPON, FOREST)),
    VINE_WHIP(spec("vine_whip", Material.PAPER, EPIC, WEAPON, FOREST).stack(1).cooldown()),
    ACORN_BOMB(spec("acorn_bomb", Material.SNOWBALL, RARE, CONSUMABLE, FOREST).stack(16)),
    OWL_CHARM(spec("owl_charm", Material.PAPER, RARE, CHARM, FOREST).stack(1)),
    BERRY_PIE(spec("berry_pie", Material.COOKIE, UNCOMMON, CONSUMABLE, FOREST).food(8, 8f)),
    SEED_OF_LIFE(spec("seed_of_life", Material.PAPER, EPIC, CONSUMABLE, FOREST).stack(8).cooldown()),
    LUMBER_CHARM(spec("lumber_charm", Material.PAPER, RARE, CHARM, FOREST).stack(1)),

    // ---- sky ("Himmel") ------------------------------------------------------------------------------------
    SKY_GLIDER(spec("sky_glider", Material.PAPER, EPIC, CHARM, SKY).stack(1)),
    WIND_HORN(spec("wind_horn", Material.PAPER, RARE, TOOL, SKY).stack(1).cooldown()),
    CLOUD_SWORD(spec("cloud_sword", Material.NETHERITE_SWORD, EPIC, WEAPON, SKY).weapon(8, 1.8)),
    THUNDER_BOTTLE(spec("thunder_bottle", Material.SNOWBALL, RARE, CONSUMABLE, SKY).stack(16)),
    STAR_MAP(spec("star_map", Material.PAPER, RARE, TOOL, SKY).stack(1).cooldown()),
    SKY_COOKIE(spec("sky_cookie", Material.COOKIE, UNCOMMON, CONSUMABLE, SKY).food(3, 2f)),
    COMET_SHARD(spec("comet_shard", Material.PAPER, RARE, CONSUMABLE, SKY).stack(16)),
    BALLOON(spec("balloon", Material.PAPER, RARE, TOOL, SKY).stack(1).cooldown()),

    // ---- crystal ("Kristall") ------------------------------------------------------------------------------
    CRYSTAL_WAND(spec("crystal_wand", Material.BLAZE_ROD, EPIC, TOOL, CRYSTAL).cooldown()),
    ARCANE_TOME(spec("arcane_tome", Material.PAPER, RARE, CONSUMABLE, CRYSTAL).stack(16)),
    MANA_POTION(spec("mana_potion", Material.HONEY_BOTTLE, EPIC, CONSUMABLE, CRYSTAL).food(1, 1f)),
    PRISM_SHIELD(spec("prism_shield", Material.PAPER, EPIC, CONSUMABLE, CRYSTAL).stack(8).cooldown()),
    CRYSTAL_BOW(spec("crystal_bow", Material.BOW, EPIC, WEAPON, CRYSTAL)),
    CRYSTAL_HEART(spec("crystal_heart", Material.PAPER, EPIC, CHARM, CRYSTAL).stack(1)),
    CRYSTAL_PICKAXE(spec("crystal_pickaxe", Material.NETHERITE_PICKAXE, EPIC, TOOL, CRYSTAL)),
    SHARD_BOMB(spec("shard_bomb", Material.SNOWBALL, RARE, CONSUMABLE, CRYSTAL).stack(16)),

    // ---- millennium ("Millennium" – pharaohs) -------------------------------------------------------------
    PHARAOH_STAFF(spec("pharaoh_staff", Material.BLAZE_ROD, EPIC, TOOL, MILLENNIUM).cooldown()),
    KHOPESH(spec("khopesh", Material.NETHERITE_SWORD, EPIC, WEAPON, MILLENNIUM).weapon(9, 1.6)),
    SCARAB_CHARM(spec("scarab_charm", Material.PAPER, RARE, CHARM, MILLENNIUM).stack(1)),
    ANKH(spec("ankh", Material.PAPER, EPIC, CHARM, MILLENNIUM).stack(4).glint()),
    MUMMY_WRAP(spec("mummy_wrap", Material.PAPER, UNCOMMON, CONSUMABLE, MILLENNIUM).stack(16).cooldown()),
    SAND_BOMB(spec("sand_bomb", Material.SNOWBALL, RARE, CONSUMABLE, MILLENNIUM).stack(16)),
    PHARAOH_TREASURE(spec("pharaoh_treasure", Material.PAPER, RARE, CONSUMABLE, MILLENNIUM).stack(16)),
    SUN_DISC(spec("sun_disc", Material.PAPER, EPIC, TOOL, MILLENNIUM).stack(1).cooldown()),

    // ---- cosmetics (masks and hats, partly bound to a season) ------------------------------------------------
    PUMPKIN_MASK(spec("pumpkin_mask", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    SKULL_MASK(spec("skull_mask", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    WITCH_HAT(spec("witch_hat", Material.PAPER, EPIC, TOOL, COSMETIC).stack(1).hat()),
    // companions: right-click to let them sit on your head (again to send them home)
    COMPANION_SNOWMAN(spec("companion_snowman", Material.PAPER, EPIC, TOOL, BEGLEITER).stack(1)),
    COMPANION_PENGUIN(spec("companion_penguin", Material.PAPER, EPIC, TOOL, BEGLEITER).stack(1)),
    COMPANION_GHOST(spec("companion_ghost", Material.PAPER, EPIC, TOOL, BEGLEITER).stack(1)),
    COMPANION_DRAGON(spec("companion_dragon", Material.PAPER, EPIC, TOOL, BEGLEITER).stack(1)),
    COMPANION_BEE(spec("companion_bee", Material.PAPER, EPIC, TOOL, BEGLEITER).stack(1)),
    SANTA_HAT(spec("santa_hat", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    REINDEER_ANTLERS(spec("reindeer_antlers", Material.PAPER, EPIC, TOOL, COSMETIC).stack(1).hat()),
    SNOWMAN_HEAD(spec("snowman_head", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    BUNNY_EARS(spec("bunny_ears", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    HEART_GLASSES(spec("heart_glasses", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    SUN_HAT(spec("sun_hat", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    LEAF_CROWN(spec("leaf_crown", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    NEW_YEAR_GLASSES(spec("new_year_glasses", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    STAR_HALO(spec("star_halo", Material.PAPER, EPIC, TOOL, COSMETIC).stack(1).hat().glint()),
    TOP_HAT(spec("top_hat", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),
    CAT_EARS(spec("cat_ears", Material.PAPER, RARE, TOOL, COSMETIC).stack(1).hat()),

    // ---- space ("Weltraum") -----------------------------------------------------------------------------------
    ROCKET_BOOSTER(spec("rocket_booster", Material.PAPER, EPIC, TOOL, SPACE).stack(1)),
    MARS_ORB(spec("mars_orb", Material.PAPER, EPIC, CHARM, SPACE).stack(1)),
    MOON_ORB(spec("moon_orb", Material.PAPER, EPIC, CHARM, SPACE).stack(1)),
    SATURN_ORB(spec("saturn_orb", Material.PAPER, EPIC, CHARM, SPACE).stack(1)),
    EARTH_ORB(spec("earth_orb", Material.PAPER, EPIC, CHARM, SPACE).stack(1)),
    LASER_GUN(spec("laser_gun", Material.BLAZE_ROD, EPIC, TOOL, SPACE).cooldown()),
    STAR_DUST(spec("star_dust", Material.PAPER, RARE, CONSUMABLE, SPACE).stack(16)),

    // ---- spawner -------------------------------------------------------------------------------------------
    SPAWNER_PICKAXE(spec("spawner_pickaxe", Material.NETHERITE_PICKAXE, EPIC, TOOL, SPAWNER).glint()),

    // ---- Schauernacht / Frostfest extras -------------------------------------------------------------------
    CHAINSAW(spec("chainsaw", Material.NETHERITE_AXE, EPIC, TOOL, HALLOWEEN).cooldown()),
    GINGERBREAD(spec("gingerbread", Material.COOKIE, UNCOMMON, CONSUMABLE, FROSTFEST).food(6, 6f)),
    SNOW_GLOBE(spec("snow_globe", Material.PAPER, RARE, TOOL, FROSTFEST).stack(1).cooldown()),
    PRESENT_LAUNCHER(spec("present_launcher", Material.PAPER, EPIC, TOOL, FROSTFEST).stack(1).cooldown()),
    MISTLETOE(spec("mistletoe", Material.PAPER, RARE, CHARM, FROSTFEST).stack(1)),
    NUTCRACKER_SWORD(spec("nutcracker_sword", Material.NETHERITE_SWORD, EPIC, WEAPON, FROSTFEST).weapon(8.5, 1.6)),
    ORNAMENT_BOMB(spec("ornament_bomb", Material.SNOWBALL, RARE, CONSUMABLE, FROSTFEST).stack(16)),
    EGGNOG(spec("eggnog", Material.HONEY_BOTTLE, UNCOMMON, CONSUMABLE, FROSTFEST).food(4, 4f)),
    REINDEER_BELL(spec("reindeer_bell", Material.PAPER, RARE, TOOL, FROSTFEST).stack(1).cooldown()),

    // ---- Silvester (job gear) ----------------------------------------------------------------------------------
    NEWYEAR_PICKAXE(spec("newyear_pickaxe", Material.NETHERITE_PICKAXE, EPIC, TOOL, SILVESTER).glint()),
    NEWYEAR_AXE(spec("newyear_axe", Material.NETHERITE_AXE, EPIC, TOOL, SILVESTER).glint()),
    NEWYEAR_HOE(spec("newyear_hoe", Material.NETHERITE_HOE, EPIC, TOOL, SILVESTER).glint()),
    NEWYEAR_ROD(spec("newyear_rod", Material.FISHING_ROD, EPIC, TOOL, SILVESTER).glint()),
    NEWYEAR_SWORD(spec("newyear_sword", Material.NETHERITE_SWORD, EPIC, WEAPON, SILVESTER).weapon(9, 1.6).glint()),
    NEWYEAR_TROWEL(spec("newyear_trowel", Material.PAPER, EPIC, CHARM, SILVESTER).stack(1).glint()),
    JOB_ELIXIR(spec("job_elixir", Material.HONEY_BOTTLE, EPIC, CONSUMABLE, SILVESTER).food(1, 1f)),
    LUCKY_ROCKET(spec("lucky_rocket", Material.PAPER, RARE, CONSUMABLE, SILVESTER).stack(16)),
    FORTUNE_CHARM(spec("fortune_charm", Material.PAPER, EPIC, CHARM, SILVESTER).stack(1).glint()),

    // ---- tournament trophies -----------------------------------------------------------------------------
    TROPHY_DAILY(spec("trophy_daily", Material.PAPER, RARE, TROPHY, TOURNAMENT).stack(1)),
    TROPHY_WEEKLY(spec("trophy_weekly", Material.PAPER, EPIC, TROPHY, TOURNAMENT).stack(1)),
    TROPHY_MONTHLY(spec("trophy_monthly", Material.PAPER, EPIC, TROPHY, TOURNAMENT).stack(1).glint()),

    // ---- armour (8 sets × 4 pieces) ----------------------------------------------------------------------
    // Every set has its own role: mythos = best all-rounder, pumpkin/storm = light and fast, frost = defender,
    // inferno = attacker, sculk = heavy tank (slower), dragon = berserker, angel = extra hearts.
    MYTHOS_HELMET(armor("mythos", EquipmentSlot.HEAD, CORE, EPIC, 3, 3.5, 0.1, 2, 0.25, 0.01)),
    MYTHOS_CHESTPLATE(armor("mythos", EquipmentSlot.CHEST, CORE, EPIC, 8, 3.5, 0.1, 2, 0.25, 0.01)),
    MYTHOS_LEGGINGS(armor("mythos", EquipmentSlot.LEGS, CORE, EPIC, 6, 3.5, 0.1, 2, 0.25, 0.01)),
    MYTHOS_BOOTS(armor("mythos", EquipmentSlot.FEET, CORE, EPIC, 3, 3.5, 0.1, 2, 0.25, 0.01)),
    PUMPKIN_HELMET(armor("pumpkin", EquipmentSlot.HEAD, HALLOWEEN, RARE, 2, 1.0, 0.0, 0, 0.0, 0.03)),
    PUMPKIN_CHESTPLATE(armor("pumpkin", EquipmentSlot.CHEST, HALLOWEEN, RARE, 6, 1.0, 0.0, 0, 0.0, 0.03)),
    PUMPKIN_LEGGINGS(armor("pumpkin", EquipmentSlot.LEGS, HALLOWEEN, RARE, 5, 1.0, 0.0, 0, 0.0, 0.03)),
    PUMPKIN_BOOTS(armor("pumpkin", EquipmentSlot.FEET, HALLOWEEN, RARE, 2, 1.0, 0.0, 0, 0.0, 0.03)),
    FROST_HELMET(armor("frost", EquipmentSlot.HEAD, FROSTFEST, EPIC, 3, 2.5, 0.15, 1, 0.0, 0.0)),
    FROST_CHESTPLATE(armor("frost", EquipmentSlot.CHEST, FROSTFEST, EPIC, 8, 2.5, 0.15, 1, 0.0, 0.0)),
    FROST_LEGGINGS(armor("frost", EquipmentSlot.LEGS, FROSTFEST, EPIC, 6, 2.5, 0.15, 1, 0.0, 0.0)),
    FROST_BOOTS(armor("frost", EquipmentSlot.FEET, FROSTFEST, EPIC, 3, 2.5, 0.15, 1, 0.0, 0.0)),
    INFERNO_HELMET(armor("inferno", EquipmentSlot.HEAD, INFERNO, EPIC, 3, 2.0, 0.05, 0, 0.5, 0.01)),
    INFERNO_CHESTPLATE(armor("inferno", EquipmentSlot.CHEST, INFERNO, EPIC, 7, 2.0, 0.05, 0, 0.5, 0.01)),
    INFERNO_LEGGINGS(armor("inferno", EquipmentSlot.LEGS, INFERNO, EPIC, 5, 2.0, 0.05, 0, 0.5, 0.01)),
    INFERNO_BOOTS(armor("inferno", EquipmentSlot.FEET, INFERNO, EPIC, 3, 2.0, 0.05, 0, 0.5, 0.01)),
    SCULK_HELMET(armor("sculk", EquipmentSlot.HEAD, ANCIENT, EPIC, 4, 4.0, 0.25, 0, 0.0, -0.02)),
    SCULK_CHESTPLATE(armor("sculk", EquipmentSlot.CHEST, ANCIENT, EPIC, 9, 4.0, 0.25, 0, 0.0, -0.02)),
    SCULK_LEGGINGS(armor("sculk", EquipmentSlot.LEGS, ANCIENT, EPIC, 7, 4.0, 0.25, 0, 0.0, -0.02)),
    SCULK_BOOTS(armor("sculk", EquipmentSlot.FEET, ANCIENT, EPIC, 4, 4.0, 0.25, 0, 0.0, -0.02)),
    DRAGON_HELMET(armor("dragon", EquipmentSlot.HEAD, DRAGON, EPIC, 3, 3.0, 0.1, 1, 0.75, 0.0)),
    DRAGON_CHESTPLATE(armor("dragon", EquipmentSlot.CHEST, DRAGON, EPIC, 8, 3.0, 0.1, 1, 0.75, 0.0)),
    DRAGON_LEGGINGS(armor("dragon", EquipmentSlot.LEGS, DRAGON, EPIC, 6, 3.0, 0.1, 1, 0.75, 0.0)),
    DRAGON_BOOTS(armor("dragon", EquipmentSlot.FEET, DRAGON, EPIC, 3, 3.0, 0.1, 1, 0.75, 0.0)),
    STORM_HELMET(armor("storm", EquipmentSlot.HEAD, STORM, RARE, 2, 1.5, 0.0, 0, 0.0, 0.05)),
    STORM_CHESTPLATE(armor("storm", EquipmentSlot.CHEST, STORM, RARE, 7, 1.5, 0.0, 0, 0.0, 0.05)),
    STORM_LEGGINGS(armor("storm", EquipmentSlot.LEGS, STORM, RARE, 5, 1.5, 0.0, 0, 0.0, 0.05)),
    STORM_BOOTS(armor("storm", EquipmentSlot.FEET, STORM, RARE, 2, 1.5, 0.0, 0, 0.0, 0.05)),
    ANGEL_HELMET(armor("angel", EquipmentSlot.HEAD, CELESTIAL, EPIC, 3, 2.5, 0.05, 3, 0.0, 0.0)),
    ANGEL_CHESTPLATE(armor("angel", EquipmentSlot.CHEST, CELESTIAL, EPIC, 7, 2.5, 0.05, 3, 0.0, 0.0)),
    ANGEL_LEGGINGS(armor("angel", EquipmentSlot.LEGS, CELESTIAL, EPIC, 6, 2.5, 0.05, 3, 0.0, 0.0)),
    ANGEL_BOOTS(armor("angel", EquipmentSlot.FEET, CELESTIAL, EPIC, 3, 2.5, 0.05, 3, 0.0, 0.0)),
    NINJA_HELMET(armor("ninja", EquipmentSlot.HEAD, HALLOWEEN, RARE, 2, 0.5, 0.0, 0, 0.25, 0.04)),
    NINJA_CHESTPLATE(armor("ninja", EquipmentSlot.CHEST, HALLOWEEN, RARE, 5, 0.5, 0.0, 0, 0.25, 0.04)),
    NINJA_LEGGINGS(armor("ninja", EquipmentSlot.LEGS, HALLOWEEN, RARE, 4, 0.5, 0.0, 0, 0.25, 0.04)),
    NINJA_BOOTS(armor("ninja", EquipmentSlot.FEET, HALLOWEEN, RARE, 2, 0.5, 0.0, 0, 0.25, 0.04)),
    KNIGHT_HELMET(armor("knight", EquipmentSlot.HEAD, CORE, EPIC, 3, 3.0, 0.2, 1, 0.0, -0.01)),
    KNIGHT_CHESTPLATE(armor("knight", EquipmentSlot.CHEST, CORE, EPIC, 8, 3.0, 0.2, 1, 0.0, -0.01)),
    KNIGHT_LEGGINGS(armor("knight", EquipmentSlot.LEGS, CORE, EPIC, 6, 3.0, 0.2, 1, 0.0, -0.01)),
    KNIGHT_BOOTS(armor("knight", EquipmentSlot.FEET, CORE, EPIC, 3, 3.0, 0.2, 1, 0.0, -0.01)),
    MINER_HELMET(armor("miner", EquipmentSlot.HEAD, ANCIENT, RARE, 2, 1.5, 0.05, 0, 0.0, 0.0)),
    MINER_CHESTPLATE(armor("miner", EquipmentSlot.CHEST, ANCIENT, RARE, 6, 1.5, 0.05, 0, 0.0, 0.0)),
    MINER_LEGGINGS(armor("miner", EquipmentSlot.LEGS, ANCIENT, RARE, 5, 1.5, 0.05, 0, 0.0, 0.0)),
    MINER_BOOTS(armor("miner", EquipmentSlot.FEET, ANCIENT, RARE, 2, 1.5, 0.05, 0, 0.0, 0.0)),
    VIKING_HELMET(armor("viking", EquipmentSlot.HEAD, INFERNO, EPIC, 3, 2.5, 0.1, 1, 0.5, -0.01)),
    VIKING_CHESTPLATE(armor("viking", EquipmentSlot.CHEST, INFERNO, EPIC, 7, 2.5, 0.1, 1, 0.5, -0.01)),
    VIKING_LEGGINGS(armor("viking", EquipmentSlot.LEGS, INFERNO, EPIC, 6, 2.5, 0.1, 1, 0.5, -0.01)),
    VIKING_BOOTS(armor("viking", EquipmentSlot.FEET, INFERNO, EPIC, 3, 2.5, 0.1, 1, 0.5, -0.01)),
    OCEAN_HELMET(armor("ocean", EquipmentSlot.HEAD, OCEAN, EPIC, 3, 2.0, 0.1, 1, 0.0, 0.01)),
    OCEAN_CHESTPLATE(armor("ocean", EquipmentSlot.CHEST, OCEAN, EPIC, 7, 2.0, 0.1, 1, 0.0, 0.01)),
    OCEAN_LEGGINGS(armor("ocean", EquipmentSlot.LEGS, OCEAN, EPIC, 6, 2.0, 0.1, 1, 0.0, 0.01)),
    OCEAN_BOOTS(armor("ocean", EquipmentSlot.FEET, OCEAN, EPIC, 3, 2.0, 0.1, 1, 0.0, 0.01)),
    DRUID_HELMET(armor("druid", EquipmentSlot.HEAD, FOREST, EPIC, 2, 2.0, 0.05, 2, 0.0, 0.0)),
    DRUID_CHESTPLATE(armor("druid", EquipmentSlot.CHEST, FOREST, EPIC, 7, 2.0, 0.05, 2, 0.0, 0.0)),
    DRUID_LEGGINGS(armor("druid", EquipmentSlot.LEGS, FOREST, EPIC, 5, 2.0, 0.05, 2, 0.0, 0.0)),
    DRUID_BOOTS(armor("druid", EquipmentSlot.FEET, FOREST, EPIC, 2, 2.0, 0.05, 2, 0.0, 0.0)),
    SKY_HELMET(armor("sky", EquipmentSlot.HEAD, SKY, RARE, 2, 1.5, 0.0, 0, 0.1, 0.03)),
    SKY_CHESTPLATE(armor("sky", EquipmentSlot.CHEST, SKY, RARE, 6, 1.5, 0.0, 0, 0.1, 0.03)),
    SKY_LEGGINGS(armor("sky", EquipmentSlot.LEGS, SKY, RARE, 5, 1.5, 0.0, 0, 0.1, 0.03)),
    SKY_BOOTS(armor("sky", EquipmentSlot.FEET, SKY, RARE, 2, 1.5, 0.0, 0, 0.1, 0.03)),
    CRYSTAL_HELMET(armor("crystal", EquipmentSlot.HEAD, CRYSTAL, EPIC, 3, 2.5, 0.05, 0, 0.25, 0.0)),
    CRYSTAL_CHESTPLATE(armor("crystal", EquipmentSlot.CHEST, CRYSTAL, EPIC, 7, 2.5, 0.05, 0, 0.25, 0.0)),
    CRYSTAL_LEGGINGS(armor("crystal", EquipmentSlot.LEGS, CRYSTAL, EPIC, 5, 2.5, 0.05, 0, 0.25, 0.0)),
    CRYSTAL_BOOTS(armor("crystal", EquipmentSlot.FEET, CRYSTAL, EPIC, 3, 2.5, 0.05, 0, 0.25, 0.0)),
    PHARAOH_HELMET(armor("pharaoh", EquipmentSlot.HEAD, MILLENNIUM, EPIC, 3, 3.0, 0.05, 1, 0.5, 0.02)),
    PHARAOH_CHESTPLATE(armor("pharaoh", EquipmentSlot.CHEST, MILLENNIUM, EPIC, 8, 3.0, 0.05, 1, 0.5, 0.02)),
    PHARAOH_LEGGINGS(armor("pharaoh", EquipmentSlot.LEGS, MILLENNIUM, EPIC, 6, 3.0, 0.05, 1, 0.5, 0.02)),
    PHARAOH_BOOTS(armor("pharaoh", EquipmentSlot.FEET, MILLENNIUM, EPIC, 3, 3.0, 0.05, 1, 0.5, 0.02)),
    ASTRONAUT_HELMET(armor("astronaut", EquipmentSlot.HEAD, SPACE, EPIC, 3, 3.5, 0.2, 2, 0.0, -0.01)),
    ASTRONAUT_CHESTPLATE(armor("astronaut", EquipmentSlot.CHEST, SPACE, EPIC, 8, 3.5, 0.2, 2, 0.0, -0.01)),
    ASTRONAUT_LEGGINGS(armor("astronaut", EquipmentSlot.LEGS, SPACE, EPIC, 6, 3.5, 0.2, 2, 0.0, -0.01)),
    ASTRONAUT_BOOTS(armor("astronaut", EquipmentSlot.FEET, SPACE, EPIC, 3, 3.5, 0.2, 2, 0.0, -0.01)),

    // ---- visuals (event objects, currency icons) ----------------------------------------------------------
    MYTHOS_COIN(spec("mythos_coin", Material.GOLD_NUGGET, UNCOMMON, VISUAL, CORE).glint()),
    GEM_SHARD(spec("mythos_gem", Material.AMETHYST_SHARD, RARE, VISUAL, CORE).glint()),
    ANCIENT_RELIC(spec("ancient_relic", Material.ECHO_SHARD, EPIC, VISUAL, ANCIENT).glint()),
    METEOR_ROCK(spec("meteor", Material.FIRE_CHARGE, RARE, VISUAL, INFERNO).glint()),
    METEOR_CORE(spec("meteor_core", Material.FIRE_CHARGE, EPIC, VISUAL, INFERNO).glint());

    public enum Kind { TOOL, WEAPON, ARMOR, CONSUMABLE, CHARM, TROPHY, VISUAL }

    /** Builder-style description of one item. */
    public static final class Spec {
        final String id;
        final Material base;
        final ItemRarity rarity;
        final Kind kind;
        final ItemCollection collection;
        boolean glint;
        boolean cooldownGroup;
        Integer maxStack;
        Double damage;
        Double attackSpeed;
        double armor;
        double toughness;
        double knockback;
        double health;
        boolean hat;
        double attackBonus;
        double speedBonus;
        String armorSet;
        EquipmentSlot slot;
        Integer nutrition;
        float saturation;

        Spec(String id, Material base, ItemRarity rarity, Kind kind, ItemCollection collection) {
            this.id = id;
            this.base = base;
            this.rarity = rarity;
            this.kind = kind;
            this.collection = collection;
        }

        /** Cosmetic: can be worn on the head (shows its 3D model), no armour value. */
        Spec hat() {
            hat = true;
            return this;
        }

        Spec glint() {
            glint = true;
            return this;
        }

        Spec cooldown() {
            cooldownGroup = true;
            return this;
        }

        Spec stack(int size) {
            maxStack = size;
            return this;
        }

        Spec weapon(double damage, double speed) {
            this.damage = damage;
            this.attackSpeed = speed;
            return this;
        }

        Spec food(int nutrition, float saturation) {
            this.nutrition = nutrition;
            this.saturation = saturation;
            return this;
        }
    }

    private static Spec spec(String id, Material base, ItemRarity rarity, Kind kind, ItemCollection collection) {
        return new Spec(id, base, rarity, kind, collection);
    }

    private static Spec armor(String set, EquipmentSlot slot, ItemCollection collection, ItemRarity rarity, double armor,
                              double toughness, double knockback, double health, double attack, double speed) {
        String piece = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            default -> "boots";
        };
        Material base = Material.valueOf("NETHERITE_" + piece.toUpperCase(Locale.ROOT));
        Spec s = new Spec(set + "_" + piece, base, rarity, ARMOR, collection);
        s.armorSet = set;
        s.slot = slot;
        s.armor = armor;
        s.toughness = toughness;
        s.knockback = knockback;
        s.health = health;
        s.attackBonus = attack;
        s.speedBonus = speed;
        return s;
    }

    private final Spec spec;

    CustomItem(Spec spec) {
        this.spec = spec;
    }

    public String id() {
        return spec.id;
    }

    public Material base() {
        return spec.base;
    }

    public ItemRarity rarity() {
        return spec.rarity;
    }

    public Kind kind() {
        return spec.kind;
    }

    public ItemCollection collection() {
        return spec.collection;
    }

    Spec spec() {
        return spec;
    }

    /** Can be handed out with /mythos item give and used as crate/event reward. */
    public boolean giveable() {
        return spec.kind != VISUAL;
    }

    public boolean tool() {
        return spec.kind == TOOL || spec.kind == WEAPON;
    }

    public boolean isArmor() {
        return spec.kind == ARMOR;
    }

    public String armorSet() {
        return spec.armorSet;
    }

    public EquipmentSlot slot() {
        return spec.slot;
    }

    public static CustomItem byId(String id) {
        String s = id.toLowerCase(Locale.ROOT);
        for (CustomItem item : values()) {
            if (item.spec.id.equals(s)) {
                return item;
            }
        }
        return null;
    }

    public static List<CustomItem> ofCollection(ItemCollection collection) {
        List<CustomItem> out = new ArrayList<>();
        for (CustomItem item : values()) {
            if (item.spec.collection == collection && item.giveable()) {
                out.add(item);
            }
        }
        return out;
    }

    /** The four pieces of an armour set, or an empty list. */
    public static List<CustomItem> armorSet(String set) {
        List<CustomItem> out = new ArrayList<>();
        for (CustomItem item : values()) {
            if (set.equals(item.spec.armorSet)) {
                out.add(item);
            }
        }
        return out;
    }
}
