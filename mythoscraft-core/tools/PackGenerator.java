import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

/**
 * Generates the complete MythosCraft resource pack: pixel-art textures, 3D item models (crates, tools, relic,
 * meteor), item model definitions with vanilla fallbacks, font glyphs (rank badges, icons, logo, GUI backgrounds)
 * and pack.mcmeta.
 *
 * Run from the mythoscraft-core folder:  java tools/PackGenerator.java resourcepack
 */
public class PackGenerator {

    static Path out;

    public static void main(String[] args) throws IOException {
        out = Path.of(args.length > 0 ? args[0] : "resourcepack");
        if (Files.exists(out.resolve("assets"))) {
            try (Stream<Path> walk = Files.walk(out.resolve("assets"))) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(p);
                }
            }
        }
        Files.createDirectories(out);
        packMeta();
        packIcon();
        fonts();
        materials();
        sprites();
        tools();
        relicAndMeteor();
        animatedTextures();
        collectionSprites();
        collectionModels();
        waveThreeSprites();
        waveThreeModels();
        waveFourSprites();
        waveFiveSprites();
        waveSixSprites();
        waveSevenSprites();
        waveEightSprites();
        waveNineSprites();
        waveNineModels();
        waveTenModels();
        sounds();
        waveSixHats();
        armor();
        crates();
        relicPlaceholders();
        itemDefinitions();
        hdOverlay();
        System.out.println("Resource pack generated in " + out.toAbsolutePath());
    }

    // =====================================================================================================
    // Colours, images, pixel font
    // =====================================================================================================

    static int rgb(int hex) {
        return 0xFF000000 | hex;
    }

    static int argb(int a, int hex) {
        return (a << 24) | (hex & 0xFFFFFF);
    }

    static int mix(int c1, int c2, double t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) Math.round(((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) Math.round(((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) Math.round(((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) Math.round((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int shade(int c, double f) {
        int a = (c >>> 24) & 255;
        int r = clamp((int) Math.round(((c >> 16) & 255) * f));
        int g = clamp((int) Math.round(((c >> 8) & 255) * f));
        int b = clamp((int) Math.round((c & 255) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static final class Img {
        final BufferedImage img;
        final int w;
        final int h;

        Img(int w, int h) {
            this.w = w;
            this.h = h;
            this.img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }

        void set(int x, int y, int c) {
            if (x >= 0 && y >= 0 && x < w && y < h) {
                img.setRGB(x, y, c);
            }
        }

        int get(int x, int y) {
            return x >= 0 && y >= 0 && x < w && y < h ? img.getRGB(x, y) : 0;
        }

        void fill(int x, int y, int fw, int fh, int c) {
            for (int i = x; i < x + fw; i++) {
                for (int j = y; j < y + fh; j++) {
                    set(i, j, c);
                }
            }
        }

        /** Draws a 1px outline around every opaque pixel cluster (outside only). */
        void outline(int color) {
            List<int[]> marks = new ArrayList<>();
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if ((get(x, y) >>> 24) != 0) {
                        continue;
                    }
                    if (opaque(x - 1, y) || opaque(x + 1, y) || opaque(x, y - 1) || opaque(x, y + 1)) {
                        marks.add(new int[]{x, y});
                    }
                }
            }
            for (int[] m : marks) {
                set(m[0], m[1], color);
            }
        }

        boolean opaque(int x, int y) {
            return (get(x, y) >>> 24) > 0;
        }

        void save(String path) throws IOException {
            Path p = out.resolve(path);
            Files.createDirectories(p.getParent());
            ImageIO.write(img, "png", p.toFile());
        }
    }

    static final Map<Character, String[]> FONT = new LinkedHashMap<>();

    static {
        FONT.put('A', new String[]{".##.", "#..#", "####", "#..#", "#..#"});
        FONT.put('B', new String[]{"###.", "#..#", "###.", "#..#", "###."});
        FONT.put('D', new String[]{"###.", "#..#", "#..#", "#..#", "###."});
        FONT.put('G', new String[]{".###", "#...", "#.##", "#..#", ".###"});
        FONT.put('N', new String[]{"#..#", "##.#", "#.##", "#..#", "#..#"});
        FONT.put('U', new String[]{"#..#", "#..#", "#..#", "#..#", ".##."});
        FONT.put('W', new String[]{"#...#", "#...#", "#.#.#", "##.##", "#...#"});
        FONT.put('+', new String[]{"...", ".#.", "###", ".#.", "..."});
        FONT.put('C', new String[]{".###", "#...", "#...", "#...", ".###"});
        FONT.put('E', new String[]{"###", "#..", "##.", "#..", "###"});
        FONT.put('F', new String[]{"###", "#..", "##.", "#..", "#.."});
        FONT.put('H', new String[]{"#..#", "#..#", "####", "#..#", "#..#"});
        FONT.put('I', new String[]{"###", ".#.", ".#.", ".#.", "###"});
        FONT.put('L', new String[]{"#..", "#..", "#..", "#..", "###"});
        FONT.put('M', new String[]{"#...#", "##.##", "#.#.#", "#...#", "#...#"});
        FONT.put('O', new String[]{".##.", "#..#", "#..#", "#..#", ".##."});
        FONT.put('P', new String[]{"####", "#..#", "####", "#...", "#..."});
        FONT.put('R', new String[]{"###.", "#..#", "###.", "#.#.", "#..#"});
        FONT.put('S', new String[]{".###", "#...", ".##.", "...#", "###."});
        FONT.put('T', new String[]{"###", ".#.", ".#.", ".#.", ".#."});
        FONT.put('V', new String[]{"#...#", "#...#", "#...#", ".#.#.", "..#.."});
        FONT.put('Y', new String[]{"#...#", ".#.#.", "..#..", "..#..", "..#.."});
    }

    static int textWidth(String text, int scale) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += FONT.get(text.charAt(i))[0].length() * scale;
            if (i < text.length() - 1) {
                w += scale;
            }
        }
        return w;
    }

    interface PixelColor {
        int at(int x, int y);
    }

    /** Draws pixel-font text; colour may depend on the absolute pixel position (gradients). */
    static void drawText(Img img, String text, int x, int y, int scale, PixelColor color) {
        int cx = x;
        for (char ch : text.toCharArray()) {
            String[] glyph = FONT.get(ch);
            for (int row = 0; row < glyph.length; row++) {
                for (int col = 0; col < glyph[row].length(); col++) {
                    if (glyph[row].charAt(col) == '#') {
                        for (int sx = 0; sx < scale; sx++) {
                            for (int sy = 0; sy < scale; sy++) {
                                int px = cx + col * scale + sx;
                                int py = y + row * scale + sy;
                                img.set(px, py, color.at(px, py));
                            }
                        }
                    }
                }
            }
            cx += (glyph[0].length() + 1) * scale;
        }
    }

    // =====================================================================================================
    // pack.mcmeta + pack.png
    // =====================================================================================================

    static void packMeta() throws IOException {
        write("pack.mcmeta", """
                {
                  "pack": {
                    "description": [
                      {"text": "MythosCraft ", "color": "#c084fc", "bold": true},
                      {"text": "Ränge, Kisten & Mythos-Tools", "color": "#f5d0fe"}
                    ],
                    "pack_format": 46,
                    "supported_formats": [46, 999],
                    "min_format": 46,
                    "max_format": 999
                  }
                }
                """);
    }

    static void packIcon() throws IOException {
        Img img = new Img(128, 128);
        Random rnd = new Random(7);
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                double dx = x - 63.5;
                double dy = y - 63.5;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 60) {
                    int base = mix(rgb(0x3b1466), rgb(0x0d0618), d / 60.0);
                    if (rnd.nextInt(90) == 0) {
                        base = mix(base, rgb(0xffffff), 0.6);
                    }
                    img.set(x, y, base);
                }
                if (d > 56 && d <= 62) {
                    double t = (Math.atan2(dy, dx) + Math.PI) / (2 * Math.PI);
                    img.set(x, y, mix(rgb(0xf59e0b), rgb(0xc084fc), Math.abs(t * 2 - 1)));
                }
                if (d > 62 && d <= 63.5) {
                    img.set(x, y, rgb(0x1a0b2e));
                }
            }
        }
        double[] m = {30, 32, 47, 32, 64, 56, 81, 32, 98, 32, 98, 96, 82, 96, 82, 60, 64, 84, 46, 60, 46, 96, 30, 96};
        double[] shadow = new double[m.length];
        for (int i = 0; i < m.length; i++) {
            shadow[i] = m[i] + 3;
        }
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                if (inPolygon(x + 0.5, y + 0.5, shadow)) {
                    img.set(x, y, mix(img.get(x, y), rgb(0x05010a), 0.75));
                }
            }
        }
        Img letter = new Img(128, 128);
        shape(letter, (x, y) -> inPolygon(x, y, m), (x, y) -> {
            int c = mix(rgb(0xf3e8ff), rgb(0xf59e0b), (y - 32) / 64.0);
            return (x + y) % 23 == 0 ? mix(c, rgb(0xffffff), 0.4) : c;
        }, rgb(0x2e1065));
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                if (letter.opaque(x, y)) {
                    img.set(x, y, letter.get(x, y));
                }
            }
        }
        star(img, 26, 34, 5, rgb(0xfde68a));
        star(img, 101, 30, 4, rgb(0xe9d5ff));
        star(img, 104, 82, 3, rgb(0xfde68a));
        star(img, 22, 86, 3, rgb(0xe9d5ff));
        img.save("pack.png");
    }

    static void star(Img img, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            img.set(cx + i, cy, mix(color, rgb(0xffffff), 1 - Math.abs(i) / (double) r));
            img.set(cx, cy + i, mix(color, rgb(0xffffff), 1 - Math.abs(i) / (double) r));
        }
        img.set(cx - 1, cy - 1, color);
        img.set(cx + 1, cy - 1, color);
        img.set(cx - 1, cy + 1, color);
        img.set(cx + 1, cy + 1, color);
    }

    // =====================================================================================================
    // Fonts: rank badges, icons, logo, GUI backgrounds, spaces
    // =====================================================================================================

    static void fonts() throws IOException {
        badge("vip", "VIP", 0x16a34a, 0x4ade80, 0x052e16, 0xfde68a, false);
        badge("elite", "ELITE", 0x0369a1, 0x38bdf8, 0x082f49, 0xe0f2fe, false);
        badge("mythos", "MYTHOS", 0x7e22ce, 0xf59e0b, 0x2e1065, 0xfff7ed, true);
        badge("team", "TEAM", 0xb91c1c, 0xf87171, 0x450a0a, 0xfee2e2, false);
        badge("vipplus", "VIP+", 0x15803d, 0x86efac, 0x052e16, 0xfde047, false);
        badge("held", "HELD", 0xc2410c, 0xfb923c, 0x431407, 0xffedd5, false);
        badge("legende", "LEGENDE", 0xb45309, 0xfde047, 0x451a03, 0xfffbeb, false);
        badge("titan", "TITAN", 0x3730a3, 0x818cf8, 0x1e1b4b, 0xe0e7ff, false);
        badge("supporter", "SUPPORT", 0x0f766e, 0x2dd4bf, 0x042f2e, 0xccfbf1, false);
        badge("builder", "BUILDER", 0xa16207, 0xfacc15, 0x422006, 0xfefce8, false);
        badge("mod", "MOD", 0x1d4ed8, 0x60a5fa, 0x172554, 0xdbeafe, false);
        badge("admin", "ADMIN", 0xb91c1c, 0xf87171, 0x450a0a, 0xfee2e2, false);
        badge("owner", "OWNER", 0x7f1d1d, 0xef4444, 0x1c0a0a, 0xfde68a, false);
        badge("creator", "CREATOR", 0xdc2626, 0xf43f5e, 0x450a0a, 0xffffff, false);

        icon("coin", PackGenerator::drawCoin);
        icon("gem", PackGenerator::drawGem);
        icon("kill", PackGenerator::drawSword);
        icon("death", PackGenerator::drawSkull);
        icon("time", PackGenerator::drawClock);
        icon("event", PackGenerator::drawStarIcon);
        icon("key", (img) -> drawKey(img, 0xf59e0b));
        icon("rank", PackGenerator::drawCrown);
        icon("streak", PackGenerator::drawFlame);
        logo();
        guiCrateOpen();
        guiCratePreview();

        StringBuilder icons = new StringBuilder("{\n  \"providers\": [\n");
        icons.append("    {\"type\": \"space\", \"advances\": {\" \": 4}},\n");
        String[][] badges = {{"vip", "\\uE001"}, {"elite", "\\uE002"}, {"mythos", "\\uE003"}, {"team", "\\uE004"},
                {"vipplus", "\\uE005"}, {"held", "\\uE006"}, {"legende", "\\uE007"}, {"titan", "\\uE008"}, {"supporter", "\\uE009"},
                {"builder", "\\uE00A"}, {"mod", "\\uE00B"}, {"admin", "\\uE00C"}, {"owner", "\\uE00D"}, {"creator", "\\uE00E"}};
        for (String[] b : badges) {
            icons.append("    {\"type\": \"bitmap\", \"file\": \"mythoscraft:font/badge_").append(b[0])
                    .append(".png\", \"height\": 9, \"ascent\": 8, \"chars\": [\"").append(b[1]).append("\"]},\n");
        }
        String[][] iconChars = {{"coin", "\\uE010"}, {"gem", "\\uE011"}, {"kill", "\\uE012"}, {"death", "\\uE013"},
                {"time", "\\uE014"}, {"event", "\\uE015"}, {"key", "\\uE016"}, {"rank", "\\uE017"}, {"streak", "\\uE018"}};
        for (String[] i : iconChars) {
            icons.append("    {\"type\": \"bitmap\", \"file\": \"mythoscraft:font/icon_").append(i[0])
                    .append(".png\", \"height\": 8, \"ascent\": 7, \"chars\": [\"").append(i[1]).append("\"]},\n");
        }
        icons.append("    {\"type\": \"bitmap\", \"file\": \"mythoscraft:font/logo.png\", \"height\": 20, \"ascent\": 7, \"chars\": [\"\\uE020\"]}\n");
        icons.append("  ]\n}\n");
        write("assets/mythoscraft/font/icons.json", icons.toString());

        StringBuilder gui = new StringBuilder("{\n  \"providers\": [\n    {\"type\": \"space\", \"advances\": {");
        int[] values = {256, 128, 64, 32, 16, 8, 4, 2, 1};
        for (int i = 0; i < values.length; i++) {
            gui.append(String.format(Locale.ROOT, "\"\\uF8%02X\": %d, ", 0x01 + i, -values[i]));
        }
        for (int i = 0; i < values.length; i++) {
            gui.append(String.format(Locale.ROOT, "\"\\uF8%02X\": %d", 0x21 + i, values[i]));
            gui.append(i < values.length - 1 ? ", " : "");
        }
        gui.append("}},\n");
        gui.append("    {\"type\": \"bitmap\", \"file\": \"mythoscraft:font/gui_crate_open.png\", \"height\": 168, \"ascent\": 13, \"chars\": [\"\\uE100\"]},\n");
        gui.append("    {\"type\": \"bitmap\", \"file\": \"mythoscraft:font/gui_crate_preview.png\", \"height\": 222, \"ascent\": 13, \"chars\": [\"\\uE101\"]}\n");
        gui.append("  ]\n}\n");
        write("assets/mythoscraft/font/gui.json", gui.toString());
    }

    /** Rank badge: 18px tall texture (shown 9px tall) with gradient fill, border, highlight and 2x pixel text. */
    static void badge(String id, String text, int dark, int light, int border, int textColor, boolean mythic) throws IOException {
        int scale = 2;
        int textW = textWidth(text, scale);
        int deco = mythic ? 8 : 0;
        int w = textW + 10 + deco * 2;
        int h = 18;
        Img img = new Img(w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                boolean corner = (x == 0 || x == w - 1) && (y == 0 || y == h - 1);
                if (corner) {
                    continue;
                }
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                if (edge) {
                    img.set(x, y, rgb(border));
                    continue;
                }
                double t = (y - 1) / (double) (h - 3);
                int fill;
                if (mythic) {
                    double hx = x / (double) (w - 1);
                    int top = mix(rgb(0xa855f7), rgb(0xf59e0b), hx);
                    int bottom = mix(rgb(0x581c87), rgb(0xb45309), hx);
                    fill = mix(top, bottom, t);
                } else {
                    fill = mix(rgb(light), rgb(dark), t);
                }
                if (y == 1) {
                    fill = mix(fill, rgb(0xffffff), 0.45);
                } else if (y == h - 2) {
                    fill = shade(fill, 0.75);
                }
                img.set(x, y, fill);
            }
        }
        int tx = 5 + deco;
        int ty = 4;
        drawText(img, text, tx + 1, ty + 1, scale, (x, y) -> argb(170, border));
        drawText(img, text, tx, ty, scale, (x, y) -> mythic
                ? mix(rgb(0xfffbeb), rgb(0xfde68a), (y - ty) / 10.0)
                : mix(rgb(0xffffff), rgb(textColor), (y - ty) / 10.0));
        if (mythic) {
            sparkle(img, 7, 8, rgb(0xfef3c7));
            sparkle(img, w - 8, 8, rgb(0xfef3c7));
        }
        img.save("assets/mythoscraft/textures/font/badge_" + id + ".png");
    }

    static void sparkle(Img img, int cx, int cy, int color) {
        img.set(cx, cy, rgb(0xffffff));
        img.set(cx - 1, cy, color);
        img.set(cx + 1, cy, color);
        img.set(cx, cy - 1, color);
        img.set(cx, cy + 1, color);
        img.set(cx - 2, cy, argb(140, color));
        img.set(cx + 2, cy, argb(140, color));
        img.set(cx, cy - 2, argb(140, color));
        img.set(cx, cy + 2, argb(140, color));
    }

    interface Painter {
        void paint(Img img) throws IOException;
    }

    static void icon(String id, Painter painter) throws IOException {
        Img img = new Img(16, 16);
        painter.paint(img);
        img.save("assets/mythoscraft/textures/font/icon_" + id + ".png");
    }

    static void shape(Img img, BiPredicate<Double, Double> inside, PixelColor color, int outline) {
        for (int x = 0; x < img.w; x++) {
            for (int y = 0; y < img.h; y++) {
                if (inside.test(x + 0.5, y + 0.5)) {
                    img.set(x, y, color.at(x, y));
                }
            }
        }
        if (outline != 0) {
            img.outline(outline);
        }
    }

    static boolean inPolygon(double x, double y, double[] poly) {
        boolean in = false;
        for (int i = 0, j = poly.length / 2 - 1; i < poly.length / 2; j = i++) {
            double xi = poly[i * 2];
            double yi = poly[i * 2 + 1];
            double xj = poly[j * 2];
            double yj = poly[j * 2 + 1];
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    static double segDist(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        double cx = ax + t * dx;
        double cy = ay + t * dy;
        return Math.hypot(px - cx, py - cy);
    }

    static void drawCoin(Img img) {
        shape(img, (x, y) -> Math.hypot(x - 8, y - 8) <= 6.6, (x, y) -> {
            double d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
            int base = d > 5.2 ? rgb(0xd97706) : mix(rgb(0xfde047), rgb(0xf59e0b), (x + y) / 26.0);
            if (d > 5.2 && (x + y) % 3 == 0) {
                base = rgb(0xb45309);
            }
            return base;
        }, rgb(0x5a2d0c));
        String[] m = {"#...#", "##.##", "#.#.#", "#...#"};
        for (int r = 0; r < m.length; r++) {
            for (int c = 0; c < 5; c++) {
                if (m[r].charAt(c) == '#') {
                    img.set(6 + c, 6 + r, rgb(0xb45309));
                }
            }
        }
        img.set(5, 4, rgb(0xfffbeb));
        img.set(4, 5, rgb(0xfffbeb));
        img.set(5, 5, rgb(0xfef9c3));
    }

    static void drawGem(Img img) {
        double[] poly = {8, 2, 13.5, 6, 8, 14.5, 2.5, 6};
        shape(img, (x, y) -> inPolygon(x, y, poly), (x, y) -> {
            if (y <= 5) {
                return x < 8 ? rgb(0xf0abfc) : rgb(0xd946ef);
            }
            return x < 8 ? rgb(0xc026d3) : rgb(0x86198f);
        }, rgb(0x3b0764));
        img.set(5, 5, rgb(0xffffff));
        img.set(6, 4, rgb(0xfdf4ff));
        img.set(8, 6, rgb(0xf5d0fe));
        img.set(8, 7, rgb(0xe879f9));
    }

    static void drawSword(Img img) {
        shape(img, (x, y) -> segDist(x, y, 4.5, 11.5, 13, 3) < 1.25, (x, y) -> mix(rgb(0xf1f5f9), rgb(0x94a3b8), (x - y + 16) / 32.0), rgb(0x1e293b));
        Img hilt = img;
        for (int i = -2; i <= 2; i++) {
            hilt.set(5 + i, 10 + i, rgb(0xb45309));
        }
        hilt.set(3, 12, rgb(0x7c2d12));
        hilt.set(2, 13, rgb(0x7c2d12));
        hilt.set(1, 14, rgb(0xf59e0b));
        img.outline(rgb(0x1e293b));
    }

    static void drawSkull(Img img) {
        shape(img, (x, y) -> Math.hypot(x - 8, y - 7) <= 5.6 || (x >= 5 && x <= 11 && y >= 10 && y <= 14),
                (x, y) -> mix(rgb(0xffffff), rgb(0xcbd5e1), y / 16.0), rgb(0x1e293b));
        img.fill(5, 7, 2, 2, rgb(0x1e293b));
        img.fill(9, 7, 2, 2, rgb(0x1e293b));
        img.set(8, 10, rgb(0x334155));
        img.set(6, 13, rgb(0x64748b));
        img.set(8, 13, rgb(0x64748b));
        img.set(10, 13, rgb(0x64748b));
    }

    static void drawClock(Img img) {
        shape(img, (x, y) -> Math.hypot(x - 8, y - 8) <= 6.4, (x, y) -> {
            double d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
            return d > 5.0 ? rgb(0x0e7490) : mix(rgb(0xecfeff), rgb(0xa5f3fc), y / 16.0);
        }, rgb(0x083344));
        for (int i = 0; i < 4; i++) {
            img.set(8, 4 + i, rgb(0x083344));
        }
        for (int i = 0; i < 3; i++) {
            img.set(8 + i, 8, rgb(0x083344));
        }
        img.set(8, 2, rgb(0x67e8f9));
    }

    static void drawStarIcon(Img img) {
        double[] poly = new double[20];
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            double r = i % 2 == 0 ? 7.2 : 3.0;
            poly[i * 2] = 8 + Math.cos(a) * r;
            poly[i * 2 + 1] = 8.4 + Math.sin(a) * r;
        }
        shape(img, (x, y) -> inPolygon(x, y, poly), (x, y) -> mix(rgb(0xfef9c3), rgb(0xf59e0b), y / 14.0), rgb(0x713f12));
        img.set(7, 6, rgb(0xffffff));
    }

    static void drawKey(Img img, int accent) {
        shape(img, (x, y) -> {
            double ring = Math.hypot(x - 5, y - 5);
            boolean bow = ring <= 3.7 && ring >= 1.5;
            boolean shaft = segDist(x, y, 7, 7, 13.5, 13.5) < 1.1;
            boolean tooth1 = segDist(x, y, 11, 11, 9, 13) < 0.9;
            boolean tooth2 = segDist(x, y, 13, 13, 11.5, 14.5) < 0.9;
            return bow || shaft || tooth1 || tooth2;
        }, (x, y) -> mix(rgb(0xfde68a), rgb(0xd97706), (x + y) / 28.0), rgb(0x451a03));
        img.set(5, 5, rgb(accent));
        img.set(4, 5, rgb(accent));
        img.set(5, 4, rgb(accent));
        img.set(4, 4, mix(rgb(accent), rgb(0xffffff), 0.6));
    }

    static void drawCrown(Img img) {
        double[] poly = {2, 12.5, 2, 5, 5, 8, 8, 3, 11, 8, 14, 5, 14, 12.5};
        shape(img, (x, y) -> inPolygon(x, y, poly), (x, y) -> mix(rgb(0xfef08a), rgb(0xd97706), y / 13.0), rgb(0x713f12));
        img.set(8, 9, rgb(0xc084fc));
        img.set(5, 10, rgb(0x38bdf8));
        img.set(11, 10, rgb(0x4ade80));
        img.fill(3, 12, 10, 1, rgb(0xb45309));
    }

    static void drawFlame(Img img) {
        shape(img, (x, y) -> {
            double dx = (x - 8) / 4.6;
            double dy = (y - 10.5) / 4.2;
            boolean body = dx * dx + dy * dy <= 1;
            boolean tip = inPolygon(x, y, new double[]{4.2, 9, 8, 1.5, 11.8, 9});
            return body || tip;
        }, (x, y) -> mix(rgb(0xfef08a), rgb(0xea580c), Math.hypot(x - 8, y - 11) / 6.0), rgb(0x7c2d12));
        img.set(8, 11, rgb(0xffffff));
        img.set(8, 10, rgb(0xfef9c3));
    }

    static void logo() throws IOException {
        String text = "MYTHOSCRAFT";
        int scale = 6;
        int w = textWidth(text, scale) + 8;
        int h = 40;
        Img img = new Img(w, h);
        int tx = 4;
        int ty = 4;
        drawText(img, text, tx + 2, ty + 3, scale, (x, y) -> argb(200, 0x1a0b2e));
        drawText(img, text, tx, ty, scale, (x, y) -> {
            double hx = (x - tx) / (double) (w - 8);
            int top = mix(rgb(0xe9d5ff), rgb(0xfde68a), hx);
            int bottom = mix(rgb(0x9333ea), rgb(0xd97706), hx);
            int c = mix(top, bottom, (y - ty) / 30.0);
            if ((y - ty) % scale == 0) {
                c = mix(c, rgb(0xffffff), 0.35);
            }
            return c;
        });
        img.outline(rgb(0x2e1065));
        sparkle(img, 2, 36, rgb(0xfde68a));
        sparkle(img, w - 3, 2, rgb(0xe9d5ff));
        img.save("assets/mythoscraft/textures/font/logo.png");
    }

    // ---- GUI backgrounds (176 wide, drawn exactly over the vanilla container) ----------------------------

    static void slot(Img img, int x, int y, int fill, int dark, int light) {
        img.fill(x, y, 18, 18, fill);
        img.fill(x, y, 17, 1, dark);
        img.fill(x, y, 1, 17, dark);
        img.fill(x + 1, y + 17, 17, 1, light);
        img.fill(x + 17, y + 1, 1, 17, light);
    }

    static void panel(Img img, int x, int y, int w, int h, int top, int bottom, int border, Random rnd, boolean stars) {
        for (int i = x; i < x + w; i++) {
            for (int j = y; j < y + h; j++) {
                int c = mix(rgb(top), rgb(bottom), (j - y) / (double) h);
                int n = rnd.nextInt(7);
                c = shade(c, 0.94 + n * 0.02);
                if (stars && rnd.nextInt(140) == 0) {
                    c = mix(c, rgb(0xe9d5ff), 0.55);
                }
                img.set(i, j, c);
            }
        }
        for (int i = x; i < x + w; i++) {
            img.set(i, y, rgb(border));
            img.set(i, y + h - 1, rgb(border));
        }
        for (int j = y; j < y + h; j++) {
            img.set(x, j, rgb(border));
            img.set(x + w - 1, j, rgb(border));
        }
    }

    static void frame(Img img, int w, int h) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                boolean outer = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                boolean inner = x == 1 || y == 1 || x == w - 2 || y == h - 2;
                if ((x < 2 || x > w - 3) && (y < 2 || y > h - 3)) {
                    if (outer && inner) {
                        continue;
                    }
                }
                if (outer) {
                    img.set(x, y, rgb(0x0f0518));
                } else if (inner) {
                    double t = (x + y) / (double) (w + h);
                    img.set(x, y, mix(rgb(0xc084fc), rgb(0xf59e0b), t));
                }
            }
        }
        img.set(0, 0, 0);
        img.set(w - 1, 0, 0);
        img.set(0, h - 1, 0);
        img.set(w - 1, h - 1, 0);
        img.set(1, 1, rgb(0x0f0518));
        img.set(w - 2, 1, rgb(0x0f0518));
        img.set(1, h - 2, rgb(0x0f0518));
        img.set(w - 2, h - 2, rgb(0x0f0518));
    }

    static void playerInventory(Img img, int top, Random rnd) {
        // lighter area so the vanilla "Inventar" label (dark grey) stays readable
        panel(img, 3, top, 170, img.h - top - 3, 0xc9bfd9, 0xb3a6c9, 0x6b5b85, rnd, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(img, 7 + col * 18, top + 12 + row * 18, rgb(0x8b7fa3), rgb(0x4c3f63), rgb(0xe7e0f1));
            }
        }
        int hotbar = top + 12 + 3 * 18 + 4;
        for (int col = 0; col < 9; col++) {
            slot(img, 7 + col * 18, hotbar, rgb(0x8b7fa3), rgb(0x4c3f63), rgb(0xe7e0f1));
        }
    }

    static void guiCrateOpen() throws IOException {
        int w = 176;
        int h = 168;
        Img img = new Img(w, h);
        Random rnd = new Random(11);
        frame(img, w, h);
        panel(img, 2, 2, w - 4, 76, 0x2a1145, 0x12071f, 0x3b1466, rnd, true);
        // reel band (container row 2: slots y=35..52)
        for (int x = 4; x < w - 4; x++) {
            for (int y = 33; y < 55; y++) {
                img.set(x, y, mix(img.get(x, y), rgb(0x581c87), 0.45));
            }
        }
        for (int col = 0; col < 9; col++) {
            boolean center = col == 4;
            slot(img, 7 + col * 18, 35, center ? rgb(0x3b1466) : rgb(0x1f0b33), center ? rgb(0xf59e0b) : rgb(0x0f0518),
                    center ? rgb(0xfde68a) : rgb(0x6d28d9));
        }
        // pointer arrows above and below the winning slot
        for (int i = 0; i < 5; i++) {
            int width = 9 - i * 2;
            img.fill(88 - 4 + i, 27 + i, width, 1, i == 0 ? rgb(0xfef3c7) : rgb(0xf59e0b));
            img.fill(88 - 4 + i, 61 - i, width, 1, i == 0 ? rgb(0xfef3c7) : rgb(0xf59e0b));
        }
        sparkle(img, 30, 22, rgb(0xe9d5ff));
        sparkle(img, 146, 22, rgb(0xe9d5ff));
        sparkle(img, 22, 64, rgb(0xfde68a));
        sparkle(img, 154, 64, rgb(0xfde68a));
        playerInventory(img, 72, rnd);
        img.save("assets/mythoscraft/textures/font/gui_crate_open.png");
    }

    static void guiCratePreview() throws IOException {
        int w = 176;
        int h = 222;
        Img img = new Img(w, h);
        Random rnd = new Random(12);
        frame(img, w, h);
        panel(img, 2, 2, w - 4, 128, 0x2a1145, 0x12071f, 0x3b1466, rnd, true);
        // reward area: rows 1-4, columns 1-7
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slot(img, 7 + col * 18, 17 + row * 18, rgb(0x1f0b33), rgb(0x0f0518), rgb(0x7e22ce));
            }
        }
        for (int x = 24; x < 152; x++) {
            img.set(x, 33, rgb(0xa855f7));
            img.set(x, 34 + 4 * 18 + 1, rgb(0xa855f7));
        }
        sparkle(img, 14, 26, rgb(0xfde68a));
        sparkle(img, 161, 26, rgb(0xfde68a));
        sparkle(img, 14, 120, rgb(0xe9d5ff));
        sparkle(img, 161, 120, rgb(0xe9d5ff));
        playerInventory(img, 126, rnd);
        img.save("assets/mythoscraft/textures/font/gui_crate_preview.png");
    }

    // =====================================================================================================
    // Material atlas for 3D models (512x512, 8x8 tiles of 64x64, 2 texels per model unit)
    // =====================================================================================================

    static final String[] MATERIALS = {"wood", "gold", "obsidian", "crystal", "netherite", "steel", "sculk", "echo",
            "leather", "lava_rock", "lava_glow", "emerald", "diamond", "bone", "brass", "amethyst",
            "pumpkin", "candy", "ice", "snow", "ghost", "bat", "silver", "straw", "leaf", "ruby", "night"};

    static int materialIndex(String name) {
        for (int i = 0; i < MATERIALS.length; i++) {
            if (MATERIALS[i].equals(name)) {
                return i;
            }
        }
        throw new IllegalArgumentException(name);
    }

    static void materials() throws IOException {
        Img img = new Img(512, 512);
        Random rnd = new Random(42);
        for (int i = 0; i < MATERIALS.length; i++) {
            int ox = (i % 8) * 64;
            int oy = (i / 8) * 64;
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    img.set(ox + x, oy + y, materialPixel(MATERIALS[i], x, y, rnd));
                }
            }
        }
        img.save("assets/mythoscraft/textures/item/materials.png");
    }

    static int materialPixel(String m, int x, int y, Random rnd) {
        double n = rnd.nextDouble();
        return switch (m) {
            case "wood" -> {
                int base = (x / 3) % 2 == 0 ? rgb(0x5b3a1e) : rgb(0x4a2f17);
                if ((y + x * 7) % 11 == 0) {
                    base = rgb(0x3a230f);
                }
                yield shade(base, 0.92 + n * 0.16);
            }
            case "gold" -> {
                int base = mix(rgb(0xfde047), rgb(0xd97706), ((x + y) % 12) / 12.0);
                if (n > 0.93) {
                    base = rgb(0xfff7c2);
                }
                yield base;
            }
            case "brass" -> shade(mix(rgb(0xd4a017), rgb(0x92610f), ((x * 2 + y) % 10) / 10.0), 0.9 + n * 0.15);
            case "obsidian" -> {
                int base = mix(rgb(0x1e1030), rgb(0x0c0614), n);
                if (n > 0.95) {
                    base = rgb(0x7c3aed);
                }
                yield base;
            }
            case "crystal" -> {
                int base = mix(rgb(0xf0abfc), rgb(0x9333ea), (x + y) / 128.0 + (n - 0.5) * 0.2);
                if (n > 0.97 || (x % 16 == 3 && y % 16 == 5)) {
                    base = rgb(0xffffff);
                }
                yield base;
            }
            case "amethyst" -> shade(mix(rgb(0xc084fc), rgb(0x6b21a8), ((x + y * 3) % 16) / 16.0), 0.9 + n * 0.2);
            case "netherite" -> shade(mix(rgb(0x4a4142), rgb(0x302a2b), n), 1.0);
            case "steel" -> {
                int base = mix(rgb(0xe2e8f0), rgb(0x94a3b8), (y % 16) / 16.0);
                yield shade(base, 0.93 + n * 0.1);
            }
            case "sculk" -> {
                int base = mix(rgb(0x0b2b33), rgb(0x061a20), n);
                if (n > 0.92) {
                    base = rgb(0x15b8b0);
                }
                yield base;
            }
            case "echo" -> {
                int base = mix(rgb(0x99f6e4), rgb(0x14b8a6), (x + y) / 128.0);
                if (n > 0.94) {
                    base = rgb(0xffffff);
                }
                yield base;
            }
            case "leather" -> {
                int base = shade(rgb(0x6b2d1a), 0.9 + n * 0.2);
                if (y % 6 == 0) {
                    base = rgb(0x3f190d);
                }
                yield base;
            }
            case "lava_rock" -> {
                int base = mix(rgb(0x2b1d1a), rgb(0x16100e), n);
                double crack = Math.sin(x * 0.45 + Math.sin(y * 0.3) * 2.2) + Math.sin(y * 0.38 + x * 0.12);
                if (Math.abs(crack) < 0.16) {
                    base = mix(rgb(0xff6a00), rgb(0xffd166), n);
                }
                yield base;
            }
            case "lava_glow" -> {
                int base = mix(rgb(0xffd166), rgb(0xff4d1a), (Math.sin(x * 0.4) + Math.cos(y * 0.33) + 2) / 4.0);
                if (n > 0.96) {
                    base = rgb(0xfff4c2);
                }
                yield base;
            }
            case "emerald" -> shade(mix(rgb(0x6ee7b7), rgb(0x047857), ((x + y) % 14) / 14.0), 0.9 + n * 0.15);
            case "diamond" -> shade(mix(rgb(0xa5f3fc), rgb(0x0e7490), ((x * 3 + y) % 18) / 18.0), 0.9 + n * 0.15);
            case "bone" -> shade(rgb(0xf5f0e6), 0.9 + n * 0.1);
            case "pumpkin" -> {
                int base = x % 8 < 1 ? rgb(0xc2410c) : mix(rgb(0xfb923c), rgb(0xea580c), (x % 8) / 8.0);
                yield shade(base, 0.92 + n * 0.12);
            }
            case "candy" -> ((x + y) / 6) % 2 == 0 ? shade(rgb(0xef4444), 0.95 + n * 0.08) : shade(rgb(0xfafafa), 0.95 + n * 0.05);
            case "ice" -> {
                int base = mix(rgb(0xe0f2fe), rgb(0x7dd3fc), ((x * 2 + y) % 20) / 20.0);
                if (n > 0.95 || (x - y) % 13 == 0) {
                    base = rgb(0xffffff);
                }
                yield base;
            }
            case "snow" -> shade(rgb(0xf8fafc), 0.94 + n * 0.06);
            case "ghost" -> mix(rgb(0xecfeff), rgb(0x67e8f9), (y % 16) / 20.0 + n * 0.1);
            case "bat" -> {
                int base = mix(rgb(0x27202e), rgb(0x150f19), n);
                if ((x + y * 3) % 17 == 0) {
                    base = rgb(0x4c1d95);
                }
                yield base;
            }
            case "silver" -> shade(mix(rgb(0xf1f5f9), rgb(0xa1a1aa), ((x + y * 2) % 14) / 14.0), 0.95 + n * 0.08);
            case "straw" -> {
                int base = x % 3 == 0 ? rgb(0xa16207) : mix(rgb(0xfacc15), rgb(0xca8a04), n);
                yield base;
            }
            case "leaf" -> shade(mix(rgb(0x4ade80), rgb(0x166534), ((x * 3 + y) % 12) / 12.0), 0.9 + n * 0.15);
            case "ruby" -> {
                int base = mix(rgb(0xfb7185), rgb(0x9f1239), ((x + y) % 16) / 16.0);
                if (n > 0.96) {
                    base = rgb(0xffe4e6);
                }
                yield base;
            }
            case "night" -> {
                int base = mix(rgb(0x1e1b4b), rgb(0x0b0a1f), n * 0.7);
                if (n > 0.975) {
                    base = rgb(0xffffff);
                } else if (n > 0.95) {
                    base = rgb(0xa78bfa);
                }
                yield base;
            }
            default -> rgb(0xff00ff);
        };
    }

    // =====================================================================================================
    // Model building
    // =====================================================================================================

    static final class Element {
        double[] from;
        double[] to;
        String material;
        String texture = "#m";
        double[] rotationOrigin;
        double angle;
        String axis;
        Map<String, double[]> customUv;
        boolean shade = true;
        int lightEmission;
        boolean animated;

        Element(double x1, double y1, double z1, double x2, double y2, double z2, String material) {
            this.from = new double[]{x1, y1, z1};
            this.to = new double[]{x2, y2, z2};
            this.material = material;
        }

        Element rotate(String axis, double angle, double ox, double oy, double oz) {
            this.axis = axis;
            this.angle = angle;
            this.rotationOrigin = new double[]{ox, oy, oz};
            return this;
        }

        Element glow() {
            this.lightEmission = 15;
            return this;
        }

        /** Uses the animated texture bound to {@code key} instead of the material atlas. */
        Element anim(String key) {
            this.texture = "#" + key;
            this.animated = true;
            return this;
        }

        Element tex(String texture) {
            this.texture = texture;
            return this;
        }
    }

    static final Random UV_RANDOM = new Random(99);

    /** UV for a face from the material atlas (2 texels per unit, 32 px per uv unit). */
    static double[] materialUv(String material, double fw, double fh) {
        int idx = materialIndex(material);
        double tileU = (idx % 8) * 2.0;
        double tileV = (idx / 8) * 2.0;
        double uw = Math.min(2, fw / 16.0);
        double vh = Math.min(2, fh / 16.0);
        double offU = Math.floor(UV_RANDOM.nextDouble() * (2 - uw) * 16) / 16.0;
        double offV = Math.floor(UV_RANDOM.nextDouble() * (2 - vh) * 16) / 16.0;
        return new double[]{tileU + offU, tileV + offV, tileU + offU + uw, tileV + offV + vh};
    }

    static String num(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        String s = String.format(Locale.ROOT, "%.4f", v);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.equals("-0") ? "0" : s;
    }

    static String arr(double... v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(num(v[i]));
        }
        return sb.append(']').toString();
    }

    static String elementJson(Element e) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\"from\": ").append(arr(e.from)).append(", \"to\": ").append(arr(e.to));
        if (e.axis != null) {
            sb.append(", \"rotation\": {\"angle\": ").append(num(e.angle)).append(", \"axis\": \"").append(e.axis)
                    .append("\", \"origin\": ").append(arr(e.rotationOrigin)).append("}");
        }
        if (!e.shade) {
            sb.append(", \"shade\": false");
        }
        if (e.lightEmission > 0) {
            sb.append(", \"light_emission\": ").append(e.lightEmission);
        }
        double dx = e.to[0] - e.from[0];
        double dy = e.to[1] - e.from[1];
        double dz = e.to[2] - e.from[2];
        sb.append(", \"faces\": {");
        String[] faces = {"north", "east", "south", "west", "up", "down"};
        for (int i = 0; i < faces.length; i++) {
            String f = faces[i];
            double[] uv;
            double fw = (f.equals("east") || f.equals("west")) ? dz : dx;
            double fh = (f.equals("up") || f.equals("down")) ? dz : dy;
            if (e.customUv != null && e.customUv.containsKey(f)) {
                uv = e.customUv.get(f);
            } else if (e.animated) {
                uv = animUv(fw, fh);
            } else if (e.material != null) {
                uv = materialUv(e.material, fw, fh);
            } else {
                continue;
            }
            sb.append(i > 0 ? ", " : "").append("\"").append(f).append("\": {\"uv\": ").append(arr(uv))
                    .append(", \"texture\": \"").append(e.texture).append("\"}");
        }
        sb.append("}}");
        return sb.toString();
    }

    static final String HANDHELD_DISPLAY = """
              "display": {
                "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
                "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
                "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7]},
                "fixed": {"rotation": [0, 180, 0]},
                "gui": {"rotation": [0, 0, 0]}
              }""";

    static final String OBJECT_DISPLAY = """
              "display": {
                "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
                "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]},
                "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                "gui": {"rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625]},
                "head": {"translation": [0, 8, 0], "scale": [1, 1, 1]},
                "fixed": {"rotation": [0, 0, 0], "scale": [1, 1, 1]}
              }""";

    static void model(String name, String texture, List<Element> elements, String display, String guiLight) throws IOException {
        model(name, Map.of("m", texture), elements, display, guiLight);
    }

    static void model(String name, Map<String, String> textures, List<Element> elements, String display, String guiLight)
            throws IOException {
        StringBuilder sb = new StringBuilder("{\n  \"textures\": {");
        List<String> keys = new ArrayList<>(textures.keySet());
        keys.sort(String::compareTo);
        for (String key : keys) {
            sb.append("\"").append(key).append("\": \"").append(textures.get(key)).append("\", ");
        }
        sb.append("\"particle\": \"").append(textures.getOrDefault("m", textures.get(keys.get(0)))).append("\"},\n");
        if (guiLight != null) {
            sb.append("  \"gui_light\": \"").append(guiLight).append("\",\n");
        }
        sb.append("  \"elements\": [\n");
        for (int i = 0; i < elements.size(); i++) {
            sb.append(elementJson(elements.get(i))).append(i < elements.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n").append(display).append("\n}\n");
        write("assets/mythoscraft/models/item/" + name + ".json", sb.toString());
    }

    static void spriteModel(String name, String texture) throws IOException {
        write("assets/mythoscraft/models/item/" + name + ".json",
                "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\"layer0\": \"" + texture + "\"}\n}\n");
    }

    /** Tool modelled upright (handle along +y), then tilted -45° like every vanilla tool sprite. */
    static List<Element> diagonal(List<Element> upright) {
        for (Element e : upright) {
            e.rotate("z", -45, 8, 8, 8);
        }
        return upright;
    }

    static Element box(double x1, double y1, double z1, double x2, double y2, double z2, String material) {
        return new Element(x1, y1, z1, x2, y2, z2, material);
    }

    /** Square cross-section centred on x=z=8. */
    static Element rod(double half, double y1, double y2, String material) {
        return box(8 - half, y1, 8 - half, 8 + half, y2, 8 + half, material);
    }

    // =====================================================================================================
    // 2D sprites (coins, gems, keys)
    // =====================================================================================================

    static void sprites() throws IOException {
        Img coin = new Img(16, 16);
        drawCoin(coin);
        coin.save("assets/mythoscraft/textures/item/mythos_coin.png");
        spriteModel("mythos_coin", "mythoscraft:item/mythos_coin");

        Img gem = new Img(16, 16);
        drawGem(gem);
        gem.save("assets/mythoscraft/textures/item/mythos_gem.png");
        spriteModel("mythos_gem", "mythoscraft:item/mythos_gem");

        for (CrateTheme t : THEMES) {
            Img key = new Img(16, 16);
            drawKey(key, t.accent);
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    int c = key.get(x, y);
                    if ((c >>> 24) != 0 && x <= 8 && y <= 8 && c != rgb(0x451a03) && c != rgb(t.accent)) {
                        key.set(x, y, mix(c, rgb(t.band), 0.55));
                    }
                }
            }
            key.save("assets/mythoscraft/textures/item/key_" + t.id + ".png");
            spriteModel("key_" + t.id, "mythoscraft:item/key_" + t.id);
        }
    }

    // =====================================================================================================
    // 3D tools
    // =====================================================================================================

    static void tools() throws IOException {
        String tex = "mythoscraft:item/materials";

        List<Element> hammer = new ArrayList<>();
        hammer.add(rod(1.25, -3, -2, "gold"));
        hammer.add(rod(0.8, -2, 3, "leather"));
        hammer.add(rod(0.75, 3, 12.5, "wood"));
        hammer.add(rod(1.1, 11.5, 12.6, "gold"));
        hammer.add(box(3.5, 12.5, 5.5, 12.5, 17.5, 10.5, "obsidian"));
        hammer.add(box(3, 13, 6, 3.5, 17, 10, "gold"));
        hammer.add(box(12.5, 13, 6, 13, 17, 10, "gold"));
        hammer.add(box(7.4, 12.4, 5.4, 8.6, 17.6, 10.6, "gold"));
        hammer.add(box(4.8, 14, 5.1, 6.6, 16, 5.5, "crystal").glow());
        hammer.add(box(9.4, 14, 5.1, 11.2, 16, 5.5, "crystal").glow());
        hammer.add(box(4.8, 14, 10.5, 6.6, 16, 10.9, "crystal").glow());
        hammer.add(box(9.4, 14, 10.5, 11.2, 16, 10.9, "crystal").glow());
        model("mythos_hammer", tex, diagonal(hammer), HANDHELD_DISPLAY, "front");

        List<Element> shovel = new ArrayList<>();
        shovel.add(rod(1.2, -3, -2, "gold"));
        shovel.add(rod(0.8, -2, 2.5, "leather"));
        shovel.add(rod(0.7, 2.5, 11.5, "wood"));
        shovel.add(rod(1.1, 10.5, 11.8, "brass"));
        shovel.add(box(5, 11.8, 7.5, 11, 17.5, 8.5, "gold"));
        shovel.add(box(6, 17.5, 7.5, 10, 18.8, 8.5, "gold"));
        shovel.add(box(7, 18.8, 7.6, 9, 19.4, 8.4, "brass"));
        shovel.add(box(7.1, 13, 7.2, 8.9, 15.4, 8.8, "amethyst").glow());
        model("excavator", tex, diagonal(shovel), HANDHELD_DISPLAY, "front");

        List<Element> axe = new ArrayList<>();
        axe.add(rod(1.2, -3, -2, "gold"));
        axe.add(rod(0.8, -2, 3, "leather"));
        axe.add(rod(0.7, 3, 15, "wood"));
        axe.add(rod(1.0, 15, 16, "gold"));
        axe.add(box(8.6, 11.5, 7.3, 12.5, 16.5, 8.7, "steel"));
        axe.add(box(12.5, 10.5, 7.45, 13.6, 17.5, 8.55, "emerald").glow());
        axe.add(box(5.4, 13, 7.5, 7.4, 15, 8.5, "brass"));
        axe.add(box(9.3, 13.3, 7.1, 11.3, 14.7, 8.9, "emerald"));
        model("timber_axe", tex, diagonal(axe), HANDHELD_DISPLAY, "front");

        List<Element> blade = new ArrayList<>();
        blade.add(rod(1.3, -3, -1.8, "echo").glow());
        blade.add(rod(0.65, -1.8, 4, "leather"));
        blade.add(box(4.5, 4, 7, 11.5, 5.2, 9, "sculk"));
        blade.add(box(3.8, 3.8, 7.3, 4.5, 5.4, 8.7, "echo").glow());
        blade.add(box(11.5, 3.8, 7.3, 12.2, 5.4, 8.7, "echo").glow());
        blade.add(box(6.8, 5.2, 7.6, 9.2, 17, 8.4, "sculk"));
        blade.add(box(7.6, 6, 7.45, 8.4, 16.5, 8.55, "echo").glow());
        blade.add(box(7.3, 17, 7.65, 8.7, 18.4, 8.35, "sculk"));
        blade.add(box(7.7, 18.4, 7.7, 8.3, 19.2, 8.3, "echo").glow());
        model("warden_blade", tex, diagonal(blade), HANDHELD_DISPLAY, "front");

        List<Element> staff = new ArrayList<>();
        staff.add(rod(0.6, -3.5, 14, "obsidian"));
        staff.add(rod(0.85, 1.5, 2.2, "gold"));
        staff.add(rod(0.85, 8, 8.7, "gold"));
        staff.add(rod(1.5, 13.6, 14.6, "gold"));
        staff.add(box(6.6, 14.6, 6.6, 9.4, 17.4, 9.4, "lava_glow").glow());
        staff.add(box(6.1, 15.2, 7.4, 6.6, 16.8, 8.6, "lava_rock"));
        staff.add(box(9.4, 15.2, 7.4, 9.9, 16.8, 8.6, "lava_rock"));
        staff.add(box(7.4, 17.4, 7.4, 8.6, 18.2, 8.6, "lava_rock"));
        staff.add(box(6.2, 14.4, 6.2, 6.9, 15.6, 6.9, "gold"));
        staff.add(box(9.1, 14.4, 9.1, 9.8, 15.6, 9.8, "gold"));
        model("meteor_staff", tex, diagonal(staff), HANDHELD_DISPLAY, "front");

        List<Element> wand = new ArrayList<>();
        wand.add(rod(0.55, -2, 13, "brass"));
        wand.add(rod(0.8, 3, 3.6, "gold"));
        wand.add(rod(1.2, 12.6, 13.4, "gold"));
        wand.add(box(6.8, 13.4, 6.8, 9.2, 16.6, 9.2, "amethyst").glow());
        wand.add(box(7.4, 16.6, 7.4, 8.6, 17.6, 8.6, "crystal").glow());
        model("region_wand", tex, diagonal(wand), HANDHELD_DISPLAY, "front");
    }

    static void relicAndMeteor() throws IOException {
        String tex = "mythoscraft:item/materials";

        List<Element> relic = new ArrayList<>();
        relic.add(box(4, 0, 4, 12, 1.5, 12, "obsidian"));
        relic.add(box(3.5, 1, 3.5, 12.5, 1.6, 12.5, "gold"));
        relic.add(box(4.2, 1.6, 4.2, 5.2, 6, 5.2, "gold"));
        relic.add(box(10.8, 1.6, 4.2, 11.8, 6, 5.2, "gold"));
        relic.add(box(4.2, 1.6, 10.8, 5.2, 6, 11.8, "gold"));
        relic.add(box(10.8, 1.6, 10.8, 11.8, 6, 11.8, "gold"));
        relic.add(box(6, 3, 6, 10, 12.5, 10, "crystal").rotate("y", 45, 8, 8, 8).glow());
        relic.add(box(6.9, 12.5, 6.9, 9.1, 15, 9.1, "crystal").rotate("y", 45, 8, 8, 8).glow());
        relic.add(box(6.6, 1.6, 6.6, 9.4, 3, 9.4, "amethyst"));
        relic.add(box(2.5, 6.5, 7.5, 13.5, 7, 8.5, "gold").rotate("y", 22.5, 8, 8, 8));
        relic.add(box(7.5, 6.5, 2.5, 8.5, 7, 13.5, "gold").rotate("y", 22.5, 8, 8, 8));
        model("ancient_relic", tex, relic, OBJECT_DISPLAY, "side");

        List<Element> meteor = new ArrayList<>();
        meteor.add(box(3, 3, 3, 13, 13, 13, "lava_rock"));
        meteor.add(box(1.5, 5, 4, 3, 11, 12, "lava_rock"));
        meteor.add(box(13, 4, 5, 14.5, 10, 11, "lava_rock"));
        meteor.add(box(5, 13, 4, 11, 14.5, 12, "lava_rock"));
        meteor.add(box(4, 1.5, 5, 12, 3, 11, "lava_rock"));
        meteor.add(box(5, 4, 1.5, 11, 11, 3, "lava_rock"));
        meteor.add(box(4, 5, 13, 10, 12, 14.5, "lava_rock"));
        meteor.add(box(6, 6, 1.4, 7, 10, 1.5, "lava_glow").glow());
        meteor.add(box(9, 4.5, 14.5, 10, 9, 14.6, "lava_glow").glow());
        meteor.add(box(1.4, 7, 6, 1.5, 9, 10, "lava_glow").glow());
        meteor.add(box(7, 14.5, 6, 9, 14.6, 10, "lava_glow").glow());
        model("meteor", tex, meteor, OBJECT_DISPLAY, "side");

        List<Element> core = new ArrayList<>();
        core.add(box(3, 0, 3, 13, 3, 13, "lava_rock"));
        core.add(box(2, 0, 5, 3, 2, 11, "lava_rock"));
        core.add(box(13, 0, 5, 14, 2, 11, "lava_rock"));
        core.add(box(5.5, 2, 5.5, 10.5, 11, 10.5, "lava_glow").rotate("y", 45, 8, 8, 8).glow());
        core.add(box(6.8, 11, 6.8, 9.2, 14, 9.2, "lava_glow").rotate("y", 45, 8, 8, 8).glow());
        core.add(box(3.5, 2, 9, 6, 7, 11.5, "lava_glow").rotate("y", 22.5, 8, 8, 8).glow());
        core.add(box(10, 2, 4, 12.5, 6, 6.5, "lava_glow").rotate("y", 22.5, 8, 8, 8).glow());
        core.add(box(4, 2.5, 4, 6.5, 4, 6.5, "lava_rock"));
        model("meteor_core", tex, core, OBJECT_DISPLAY, "side");
    }

    // =====================================================================================================
    // Crates (base + lid as separate models for the opening animation, plus a combined icon model)
    // =====================================================================================================

    record CrateTheme(String id, int plankLight, int plankDark, int band, int bandDark, int accent, int emblem, boolean runes,
                      int runeColor) {
    }

    static final CrateTheme[] THEMES = {
            new CrateTheme("common", 0xa0763f, 0x7a5528, 0x9ca3af, 0x4b5563, 0xe5e7eb, 0x6b7280, false, 0),
            new CrateTheme("vip", 0x4a3423, 0x33231a, 0x22c55e, 0x14532d, 0x86efac, 0x16a34a, false, 0),
            new CrateTheme("elite", 0x3f4a5c, 0x2a3240, 0x38bdf8, 0x075985, 0xbae6fd, 0x0ea5e9, false, 0),
            new CrateTheme("mythos", 0x2a1145, 0x170826, 0xf59e0b, 0x92400e, 0xfde68a, 0xa855f7, true, 0xc084fc),
            new CrateTheme("ancient", 0x123238, 0x0a1c21, 0x334155, 0x0f172a, 0x5eead4, 0x14b8a6, true, 0x2dd4bf),
            new CrateTheme("halloween", 0x3b2a1e, 0x24170f, 0xea580c, 0x7c2d12, 0xfde047, 0xff7a1a, true, 0xff7a1a),
            new CrateTheme("frostfest", 0x93c5fd, 0x60a5fa, 0xf8fafc, 0x64748b, 0x7dd3fc, 0x38bdf8, true, 0xe0f2fe),
            new CrateTheme("celestial", 0x1e1b4b, 0x13112e, 0xa78bfa, 0x4c1d95, 0x67e8f9, 0x22d3ee, true, 0xc4b5fd),
            new CrateTheme("inferno", 0x292524, 0x1c1917, 0x991b1b, 0x450a0a, 0xff6a00, 0xff4d1a, true, 0xff6a00),
            new CrateTheme("farm", 0xb08d57, 0x8a6a3b, 0x65a30d, 0x365314, 0xfde047, 0x84cc16, false, 0),
            new CrateTheme("storm", 0x334155, 0x1e293b, 0x7dd3fc, 0x1e3a8a, 0xfde047, 0x38bdf8, true, 0xfde047),
            new CrateTheme("dragon", 0x2e1065, 0x1e0b2e, 0x7f1d1d, 0x450a0a, 0xf472b6, 0xa21caf, true, 0xe879f9),
            new CrateTheme("rang", 0x3b0764, 0x2e1065, 0xf59e0b, 0x92400e, 0xfde68a, 0xfbbf24, true, 0xfde68a),
            new CrateTheme("ocean", 0x0e7490, 0x164e63, 0xfde68a, 0xb45309, 0x67e8f9, 0x22d3ee, true, 0xa5f3fc),
            new CrateTheme("forest", 0x5b3a1e, 0x3f2a14, 0x4d7c0f, 0x1a2e05, 0x86efac, 0x22c55e, true, 0xbbf7d0),
            new CrateTheme("sky", 0xe0f2fe, 0xbae6fd, 0xfde047, 0xa16207, 0xffffff, 0x38bdf8, true, 0xfef9c3),
            new CrateTheme("crystal", 0x3b0764, 0x2e1065, 0xe9d5ff, 0x7e22ce, 0xf0abfc, 0xc026d3, true, 0xf5d0fe),
            new CrateTheme("millennium", 0xd6b98c, 0xa16207, 0x1d4ed8, 0x1e3a8a, 0xfde047, 0xfbbf24, true, 0x22d3ee),
            new CrateTheme("cosmetic", 0xfbcfe8, 0xf472b6, 0xa78bfa, 0x6d28d9, 0xffffff, 0xf472b6, true, 0xfdf4ff),
            new CrateTheme("begleiter", 0xfde68a, 0xf9a8d4, 0xf472b6, 0x9d174d, 0xffffff, 0xfbcfe8, true, 0xfff1f2),
            new CrateTheme("space", 0x1e1b4b, 0x0f172a, 0x94a3b8, 0x334155, 0x818cf8, 0x6366f1, true, 0xe0e7ff),
            new CrateTheme("spawner", 0x1f2937, 0x111827, 0x4b5563, 0x1f2937, 0xa3e635, 0x7c3aed, true, 0xa3e635),
            new CrateTheme("shadow", 0x1c1027, 0x0b0612, 0xf8fafc, 0x94a3b8, 0xe9d5ff, 0x6d28d9, true, 0xffffff),
            new CrateTheme("silvester", 0x1e1b4b, 0x0f172a, 0xfde047, 0xb45309, 0xa855f7, 0xfde047, true, 0xfef3c7),
            // relic crates V1–V4 (bronze, silver, arcane, ruby-gold)
            new CrateTheme("relic1", 0x6b4a2b, 0x4a321c, 0xc8743c, 0x7a3f12, 0xf6c58a, 0xd6a35c, true, 0xf59e0b),
            new CrateTheme("relic2", 0x475569, 0x334155, 0xe2e8f0, 0x64748b, 0xffffff, 0xcbd5e1, true, 0xf8fafc),
            new CrateTheme("relic3", 0x1e1b4b, 0x13112e, 0x22d3ee, 0x0e7490, 0xa855f7, 0x7c3aed, true, 0x67e8f9),
            new CrateTheme("relic4", 0x3f0d12, 0x24070a, 0xfde047, 0xb45309, 0xf43f5e, 0xf59e0b, true, 0xfde68a)
    };

    /** Atlas regions in pixels (128x128 texture, 8 px per uv unit, 2 px per model unit). */
    static final int[] R_SIDE = {0, 0, 28, 20};
    static final int[] R_TOP = {0, 20, 28, 28};
    static final int[] R_LID_SIDE = {32, 0, 30, 8};
    static final int[] R_LID_TOP = {32, 8, 30, 30};
    static final int[] R_BAND = {64, 0, 32, 32};
    static final int[] R_EMBLEM = {96, 0, 8, 8};
    static final int[] R_GEM = {104, 0, 8, 8};
    static final int[] R_INNER = {64, 32, 32, 32};

    static double[] uv(int[] r) {
        return new double[]{r[0] / 8.0, r[1] / 8.0, (r[0] + r[2]) / 8.0, (r[1] + r[3]) / 8.0};
    }

    static void crates() throws IOException {
        for (CrateTheme t : THEMES) {
            if (animatedCrate(t)) {
                animatedCrateTexture(t);
            } else {
                crateTexture(t);
            }
            Map<String, String> tex = Map.of("m", "mythoscraft:item/crate_" + t.id, "x", "mythoscraft:item/materials");
            List<Element> base = crateBase(t);
            List<Element> lid = crateLid(t);
            lid.addAll(crateDecorations(t));
            model("crate_" + t.id + "_base", tex, base, OBJECT_DISPLAY, "side");
            model("crate_" + t.id + "_lid", tex, lid, OBJECT_DISPLAY, "side");
            List<Element> all = new ArrayList<>(crateBase(t));
            all.addAll(crateLid(t));
            all.addAll(crateDecorations(t));
            model("crate_" + t.id, tex, all, OBJECT_DISPLAY, "side");
        }
    }

    static Element faced(double x1, double y1, double z1, double x2, double y2, double z2, int[] sides, int[] topBottom) {
        Element e = box(x1, y1, z1, x2, y2, z2, null);
        e.customUv = new LinkedHashMap<>();
        for (String f : new String[]{"north", "east", "south", "west"}) {
            e.customUv.put(f, uv(sides));
        }
        e.customUv.put("up", uv(topBottom));
        e.customUv.put("down", uv(topBottom));
        return e;
    }

    static Element solid(double x1, double y1, double z1, double x2, double y2, double z2, int[] region) {
        return faced(x1, y1, z1, x2, y2, z2, region, region);
    }

    static List<Element> crateBase(CrateTheme t) {
        List<Element> list = new ArrayList<>();
        list.add(faced(1, 0, 1, 15, 10, 15, R_SIDE, R_TOP));
        double[][] posts = {{0.5, 0.5}, {13.5, 0.5}, {0.5, 13.5}, {13.5, 13.5}};
        for (double[] p : posts) {
            list.add(solid(p[0], 0, p[1], p[0] + 2, 10.5, p[1] + 2, R_BAND));
        }
        list.add(solid(2.5, 9, 0.6, 13.5, 10, 1, R_BAND));
        list.add(solid(2.5, 9, 15, 13.5, 10, 15.4, R_BAND));
        list.add(solid(0.6, 9, 2.5, 1, 10, 13.5, R_BAND));
        list.add(solid(15, 9, 2.5, 15.4, 10, 13.5, R_BAND));
        list.add(solid(2.5, 0, 0.6, 13.5, 1, 1, R_BAND));
        list.add(solid(2.5, 0, 15, 13.5, 1, 15.4, R_BAND));
        list.add(solid(0.6, 0, 2.5, 1, 1, 13.5, R_BAND));
        list.add(solid(15, 0, 2.5, 15.4, 1, 13.5, R_BAND));
        list.add(solid(6, 3, 0.4, 10, 7, 1, R_EMBLEM));
        list.add(solid(6, 3, 15, 10, 7, 15.6, R_EMBLEM));
        list.add(solid(0.4, 3, 6, 1, 7, 10, R_EMBLEM));
        list.add(solid(15, 3, 6, 15.6, 7, 10, R_EMBLEM));
        Element[] gems = {solid(7, 4, 0.1, 9, 6, 0.4, R_GEM), solid(7, 4, 15.6, 9, 6, 15.9, R_GEM),
                solid(0.1, 4, 7, 0.4, 6, 9, R_GEM), solid(15.6, 4, 7, 15.9, 6, 9, R_GEM)};
        for (Element g : gems) {
            if (t.runes) {
                g.glow();
            }
            list.add(g);
        }
        return list;
    }

    static List<Element> crateLid(CrateTheme t) {
        List<Element> list = new ArrayList<>();
        list.add(faced(0.5, 10, 0.5, 15.5, 14, 15.5, R_LID_SIDE, R_LID_TOP));
        list.add(solid(0.3, 10.2, 0.3, 2.2, 14.2, 2.2, R_BAND));
        list.add(solid(13.8, 10.2, 0.3, 15.7, 14.2, 2.2, R_BAND));
        list.add(solid(0.3, 10.2, 13.8, 2.2, 14.2, 15.7, R_BAND));
        list.add(solid(13.8, 10.2, 13.8, 15.7, 14.2, 15.7, R_BAND));
        list.add(solid(4.5, 14, 4.5, 11.5, 14.6, 11.5, R_BAND));
        Element gem = solid(6.6, 14.6, 6.6, 9.4, 16, 9.4, R_GEM).rotate("y", 45, 8, 8, 8);
        if (t.runes) {
            gem.glow();
        }
        list.add(gem);
        return list;
    }

    static void crateTexture(CrateTheme t) throws IOException {
        crateBaseImage(t).save("assets/mythoscraft/textures/item/crate_" + t.id + ".png");
    }

    static Img crateBaseImage(CrateTheme t) {
        Img img = new Img(128, 128);
        Random rnd = new Random(t.id.hashCode());
        planks(img, R_SIDE, t, rnd, true);
        planks(img, R_TOP, t, rnd, false);
        planks(img, R_LID_SIDE, t, rnd, true);
        planks(img, R_LID_TOP, t, rnd, false);
        // lid top: decorative border + runes
        int[] lt = R_LID_TOP;
        for (int i = 0; i < lt[2]; i++) {
            for (int j = 0; j < lt[3]; j++) {
                boolean border = i < 2 || j < 2 || i >= lt[2] - 2 || j >= lt[3] - 2;
                if (border) {
                    img.set(lt[0] + i, lt[1] + j, i == 0 || j == 0 || i == lt[2] - 1 || j == lt[3] - 1 ? rgb(t.bandDark) : rgb(t.band));
                }
            }
        }
        if (t.runes) {
            runes(img, R_SIDE, t, rnd);
            runes(img, R_LID_TOP, t, rnd);
            runes(img, R_LID_SIDE, t, rnd);
        }
        // metal band texture with rivets
        int[] b = R_BAND;
        for (int i = 0; i < b[2]; i++) {
            for (int j = 0; j < b[3]; j++) {
                int c = mix(rgb(t.band), rgb(t.bandDark), ((i + j) % 9) / 12.0);
                c = shade(c, 0.9 + rnd.nextDouble() * 0.18);
                if (i % 8 == 3 && j % 8 == 3) {
                    c = mix(rgb(t.accent), rgb(0xffffff), 0.3);
                } else if (i % 8 == 4 && j % 8 == 4) {
                    c = rgb(t.bandDark);
                }
                img.set(b[0] + i, b[1] + j, c);
            }
        }
        // emblem plate (8x8) and gem (8x8)
        int[] e = R_EMBLEM;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                boolean edge = i == 0 || j == 0 || i == 7 || j == 7;
                img.set(e[0] + i, e[1] + j, edge ? rgb(t.bandDark) : mix(rgb(t.band), rgb(t.accent), (i + j) / 14.0));
            }
        }
        int[] g = R_GEM;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                double d = Math.abs(i - 3.5) + Math.abs(j - 3.5);
                int c = d < 3.2 ? mix(rgb(0xffffff), rgb(t.emblem), d / 3.2) : rgb(t.emblem);
                img.set(g[0] + i, g[1] + j, d > 4.5 ? shade(rgb(t.emblem), 0.6) : c);
            }
        }
        int[] in = R_INNER;
        img.fill(in[0], in[1], in[2], in[3], shade(rgb(t.plankDark), 0.5));
        return img;
    }

    static void planks(Img img, int[] r, CrateTheme t, Random rnd, boolean horizontal) {
        for (int i = 0; i < r[2]; i++) {
            for (int j = 0; j < r[3]; j++) {
                int plank = horizontal ? j / 5 : i / 7;
                int c = plank % 2 == 0 ? rgb(t.plankLight) : rgb(t.plankDark);
                double grain = rnd.nextDouble();
                c = shade(c, 0.9 + grain * 0.17);
                boolean seam = horizontal ? j % 5 == 4 : i % 7 == 6;
                if (seam) {
                    c = shade(rgb(t.plankDark), 0.6);
                }
                if (rnd.nextInt(45) == 0) {
                    c = shade(c, 0.75);
                }
                img.set(r[0] + i, r[1] + j, c);
            }
        }
    }

    static void runes(Img img, int[] r, CrateTheme t, Random rnd) {
        int count = Math.max(2, r[2] * r[3] / 90);
        for (int k = 0; k < count; k++) {
            int x = r[0] + 3 + rnd.nextInt(Math.max(1, r[2] - 6));
            int y = r[1] + 2 + rnd.nextInt(Math.max(1, r[3] - 4));
            int len = 2 + rnd.nextInt(3);
            boolean vertical = rnd.nextBoolean();
            for (int s = 0; s < len; s++) {
                int px = vertical ? x : x + s;
                int py = vertical ? y + s : y;
                if (px < r[0] + r[2] - 1 && py < r[1] + r[3] - 1) {
                    img.set(px, py, rgb(t.runeColor));
                }
            }
            img.set(x, y, rgb(0xffffff));
        }
    }

    // =====================================================================================================
    // Item definitions: select on custom_model_data string, embedded vanilla definition as fallback
    // =====================================================================================================

    static void itemDefinitions() throws IOException {
        Map<String, List<String>> plain = new LinkedHashMap<>();
        plain.put("netherite_pickaxe", List.of("mythos_hammer", "smelter_pickaxe", "ore_pickaxe", "crystal_pickaxe", "spawner_pickaxe", "newyear_pickaxe",
                "vein_pickaxe", "power_drill", "zap_pickaxe", "ghost_pickaxe"));
        plain.put("netherite_shovel", List.of("excavator", "ghost_shovel"));
        plain.put("netherite_axe", List.of("timber_axe", "inferno_axe", "thunder_hammer", "chainsaw", "newyear_axe", "mega_chainsaw", "ghost_axe"));
        plain.put("netherite_sword", List.of("warden_blade", "bat_blade", "frost_blade", "candy_cane", "star_saber", "wind_blade",
                "dragon_blade", "carrot_blade", "coral_blade", "cloud_sword", "khopesh", "nutcracker_sword", "newyear_sword",
                "soul_sword", "zap_sword", "ghost_sword"));
        plain.put("netherite_hoe", List.of("pumpkin_scythe", "harvest_sickle", "newyear_hoe", "ghost_hoe"));
        plain.put("blaze_rod", List.of("mythos_wand", "meteor_staff", "region_wand", "witch_broom", "ice_wand", "void_scepter", "ember_wand",
                "dragon_staff", "builder_wand", "blossom_wand", "sort_wand", "witch_broom_ride", "light_staff", "firework_wand", "tide_staff", "druid_staff", "crystal_wand", "pharaoh_staff", "laser_gun"));
        plain.put("paper", List.of("mythos_backpack", "healing_totem", "magnet_charm", "miner_lantern", "xp_tome", "return_feather",
                "lucky_clover", "coin_booster", "gem_booster", "xp_booster", "find_booster", "mutation_serum", "meteor_shower", "spell_scroll", "mana_elixir", "haste_rune", "ghost_lantern", "candy_bucket", "snow_cannon", "gift_box", "phoenix_feather",
                "nebula_orb", "trophy_daily", "trophy_weekly", "trophy_monthly", "watering_can", "seed_bag", "compost_charm",
                "static_charm", "dragon_scale", "dragon_heart", "ender_pouch", "jump_charm", "aqua_charm", "coin_pouch", "gem_pouch",
                "key_fragment", "swift_rune", "rtp_scroll", "mob_net", "boomerang", "wisdom_charm", "treasure_map", "smoke_bomb",
                "easter_egg", "rank_voucher", "golem_egg", "climbing_gloves", "gravity_gauntlet", "shield_crystal", "time_sand",
                "weather_orb", "ore_compass", "mega_mushroom", "return_crystal", "sponge_bomb", "repair_hammer", "enchant_scroll",
                "cloud_bottle", "hunger_charm", "soul_jar", "lucky_dice", "frost_walker_charm", "home_stone", "magic_bean", "web_shooter",
                "player_tracker", "confetti_cannon", "lucky_horseshoe", "party_hat", "golden_crown", "pearl_charm", "shell_horn",
                "treasure_net", "vine_whip", "owl_charm", "seed_of_life", "lumber_charm", "sky_glider", "wind_horn", "star_map",
                "comet_shard", "balloon", "arcane_tome", "prism_shield", "crystal_heart", "wheel_pointer", "wheel_hub", "scarab_charm", "ankh", "mummy_wrap",
                "pharaoh_treasure", "sun_disc", "pumpkin_mask", "skull_mask", "witch_hat", "santa_hat", "reindeer_antlers", "snowman_head",
                "bunny_ears", "heart_glasses", "sun_hat", "leaf_crown", "new_year_glasses", "star_halo", "top_hat", "cat_ears",
                "rocket_booster", "mars_orb", "moon_orb", "saturn_orb", "earth_orb", "star_dust", "snow_globe", "present_launcher",
                "mistletoe", "reindeer_bell", "newyear_trowel", "fortune_charm", "lucky_rocket",
                "companion_snowman", "companion_penguin", "companion_ghost", "companion_dragon", "companion_bee", "companion_shadow",
                "spawner_booster", "repair_charm", "paint_brush", "concrete_mixer", "dwarf_hat", "growth_totem", "blossom_crown", "bark_hand"));
        plain.put("water_bucket", List.of("infinite_bucket"));
        plain.put("totem_of_undying", List.of("minepro_totem"));
        plain.put("elytra", List.of("loan_elytra"));
        plain.put("snowball", List.of("pumpkin_bomb", "frost_bomb", "kraken_ink", "acorn_bomb", "thunder_bottle", "shard_bomb", "sand_bomb", "ornament_bomb"));
        plain.put("cookie", List.of("candy_corn", "lollipop", "fortune_cookie", "sushi", "berry_pie", "sky_cookie", "gingerbread"));
        plain.put("bread", List.of("honey_bread"));
        plain.put("apple", List.of("caramel_apple"));
        plain.put("honey_bottle", List.of("hot_cocoa", "dragon_flask", "storm_flask", "mythos_elixir", "healing_flask", "shrink_potion", "rainbow_potion", "diving_flask", "mana_potion", "eggnog", "job_elixir"));
        plain.put("gold_nugget", List.of("mythos_coin"));
        plain.put("amethyst_shard", List.of("mythos_gem"));
        plain.put("echo_shard", List.of("ancient_relic"));
        plain.put("fire_charge", List.of("meteor", "meteor_core"));
        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            List<String> ids = new ArrayList<>();
            for (ArmorSet s : ARMOR_SETS) {
                ids.add(s.id + "_" + piece);
            }
            plain.put("netherite_" + piece, ids);
        }
        List<String> keys = new ArrayList<>();
        List<String> crates = new ArrayList<>();
        for (CrateTheme t : THEMES) {
            keys.add("key_" + t.id);
            crates.add("crate_" + t.id);
            crates.add("crate_" + t.id + "_base");
            crates.add("crate_" + t.id + "_lid");
        }
        plain.put("tripwire_hook", keys);
        plain.put("barrel", crates);

        for (Map.Entry<String, List<String>> e : plain.entrySet()) {
            Map<String, String> cases = new LinkedHashMap<>();
            for (String id : e.getValue()) {
                cases.put(id, "{\"type\": \"minecraft:model\", \"model\": \"mythoscraft:item/" + id + "\"}");
            }
            definition(e.getKey(), cases);
        }

        // bows and the grappling hook reuse the vanilla state logic (pulling / cast) with our own models
        String bow = vanillaModel("bow");
        Map<String, String> bows = new LinkedHashMap<>();
        for (String id : new String[]{"ghost_bow", "comet_bow", "storm_bow", "ender_bow", "leaf_bow", "crystal_bow"}) {
            bows.put(id, bow.replace("minecraft:item/bow", "mythoscraft:item/" + id));
        }
        definition("bow", bows);
        Map<String, String> rods = new LinkedHashMap<>();
        for (String id : new String[]{"grappling_hook", "lucky_rod", "newyear_rod", "zap_rod"}) {
            rods.put(id, vanillaModel("fishing_rod").replace("minecraft:item/fishing_rod", "mythoscraft:item/" + id));
        }
        definition("fishing_rod", rods);
    }

    /** The "model" object of the vanilla 1.21.11 item definition (tools/vanilla/items). */
    static String vanillaModel(String item) throws IOException {
        String json = Files.readString(Path.of("tools/vanilla/items/" + item + ".json"), StandardCharsets.UTF_8).trim();
        int key = json.indexOf("\"model\"");
        int start = json.indexOf('{', key);
        int end = json.lastIndexOf('}');
        return json.substring(start, end).trim();
    }

    static void definition(String item, Map<String, String> cases) throws IOException {
        StringBuilder sb = new StringBuilder("{\n  \"model\": {\n    \"type\": \"minecraft:select\",\n");
        sb.append("    \"property\": \"minecraft:custom_model_data\",\n    \"index\": 0,\n    \"cases\": [\n");
        List<String> keys = new ArrayList<>(cases.keySet());
        keys.sort(String::compareTo);
        for (int i = 0; i < keys.size(); i++) {
            sb.append("      {\"when\": \"").append(keys.get(i)).append("\", \"model\": ").append(cases.get(keys.get(i)))
                    .append("}").append(i < keys.size() - 1 ? ",\n" : "\n");
        }
        sb.append("    ],\n    \"fallback\": ").append(vanillaModel(item)).append("\n  }\n}\n");
        write("assets/minecraft/items/" + item + ".json", sb.toString());
    }

    // =====================================================================================================
    // Animated textures (8 frames, interpolated) used by 3D models of the collections
    // =====================================================================================================

    interface FramePixel {
        int at(int x, int y, int frame, int frames);
    }

    static void animated(String path, int size, int frames, int frametime, FramePixel fn) throws IOException {
        Img img = new Img(size, size * frames);
        for (int f = 0; f < frames; f++) {
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    img.set(x, f * size + y, fn.at(x, y, f, frames));
                }
            }
        }
        img.save(path + ".png");
        write(path + ".png.mcmeta", "{\"animation\": {\"frametime\": " + frametime + ", \"interpolate\": true}}\n");
    }

    static double wave(double v) {
        return (Math.sin(v) + 1) / 2;
    }

    static void animatedTextures() throws IOException {
        String base = "assets/mythoscraft/textures/item/";
        animated(base + "anim_star", 64, 8, 3, (x, y, f, n) -> {
            double t = (x + y) / 128.0 + f / (double) n;
            int c = mix(mix(rgb(0x22d3ee), rgb(0xa855f7), wave(t * Math.PI * 2)), rgb(0xfde68a), wave(t * Math.PI * 4 + 1) * 0.5);
            int sx = (x * 7 + y * 13 + f * 5) % 61;
            return sx == 0 ? rgb(0xffffff) : sx == 1 ? mix(c, rgb(0xffffff), 0.6) : c;
        });
        animated(base + "anim_lava", 64, 8, 3, (x, y, f, n) -> {
            double v = Math.sin(x * 0.35 + f * 0.8) + Math.cos(y * 0.28 - f * 0.6) + Math.sin((x + y) * 0.17 + f);
            double t = (v + 3) / 6;
            return t > 0.62 ? mix(rgb(0xff8c1a), rgb(0xfff2a8), (t - 0.62) * 2.6) : mix(rgb(0x7f1d1d), rgb(0xff4d1a), t / 0.62);
        });
        animated(base + "anim_soul", 64, 8, 2, (x, y, f, n) -> {
            double flick = wave(f * 0.9 + x * 0.21) * 0.35 + wave(y * 0.15 - f * 1.3) * 0.35;
            return mix(rgb(0x0e7490), rgb(0xcffafe), Math.min(1, flick + (64 - y) / 160.0));
        });
        animated(base + "anim_frost", 64, 8, 3, (x, y, f, n) -> {
            int c = mix(rgb(0xe0f2fe), rgb(0x7dd3fc), ((x * 3 + y) % 23) / 23.0);
            int band = Math.floorMod(x + y - f * 16, 128);
            return band < 10 ? mix(c, rgb(0xffffff), 0.75 - band * 0.05) : c;
        });
        animated(base + "anim_chain", 64, 8, 1, (x, y, f, n) -> {
            int c = Math.floorMod(y + f * 2, 8) < 4 ? rgb(0xcbd5e1) : rgb(0x475569);
            return Math.floorMod(x, 8) == 0 ? mix(c, rgb(0x0f172a), 0.4) : c;
        });
        animated(base + "anim_pumpkin", 64, 8, 2, (x, y, f, n) -> {
            double glow = 0.75 + 0.25 * wave(f * 1.7 + x * 0.05);
            return mix(rgb(0x7c2d12), rgb(0xffd166), glow * (0.6 + 0.4 * wave(y * 0.4)));
        });
    }

    /** UV inside a 64x64 animation frame (4 px per uv unit, 2 texels per model unit). */
    static double[] animUv(double fw, double fh) {
        double uw = Math.min(16, fw / 2.0);
        double vh = Math.min(16, fh / 2.0);
        double offU = Math.floor(UV_RANDOM.nextDouble() * (16 - uw) * 4) / 4.0;
        double offV = Math.floor(UV_RANDOM.nextDouble() * (16 - vh) * 4) / 4.0;
        return new double[]{offU, offV, offU + uw, offV + vh};
    }

    // =====================================================================================================
    // 32x32 sprites for the collection items
    // =====================================================================================================

    /** Booster flask: a round bottle with a coloured glow and a symbol drawn by {@code symbol}. */
    static void boosterSprite(String name, int light, int dark, Painter symbol) throws IOException {
        sprite(name, img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 18) <= 10 || x >= 13 && x <= 19 && y >= 4 && y <= 10,
                    (x, y) -> y < 9 ? rgb(0xe2e8f0) : mix(light, dark, Math.hypot(x - 12, y - 14) / 15), INK);
            img.fill(12, 2, 9, 3, rgb(0x7c2d12));
            symbol.paint(img);
            img.set(10, 15, rgb(0xffffff));
            img.set(11, 14, rgb(0xffffff));
            img.outline(INK);
        });
    }

    static void sprite(String name, Painter painter) throws IOException {
        Img img = new Img(32, 32);
        painter.paint(img);
        img.save("assets/mythoscraft/textures/item/" + name + ".png");
        spriteModel(name, "mythoscraft:item/" + name);
    }

    interface FramePainter {
        void paint(Img img, int frame, int frames);
    }

    static void animatedSprite(String name, int frames, int frametime, FramePainter painter) throws IOException {
        Img strip = new Img(32, 32 * frames);
        for (int f = 0; f < frames; f++) {
            Img frame = new Img(32, 32);
            painter.paint(frame, f, frames);
            for (int x = 0; x < 32; x++) {
                for (int y = 0; y < 32; y++) {
                    strip.set(x, f * 32 + y, frame.get(x, y));
                }
            }
        }
        strip.save("assets/mythoscraft/textures/item/" + name + ".png");
        write("assets/mythoscraft/textures/item/" + name + ".png.mcmeta",
                "{\"animation\": {\"frametime\": " + frametime + ", \"interpolate\": true}}\n");
        spriteModel(name, "mythoscraft:item/" + name);
    }

    static final int INK = rgb(0x1b1022);

    static void collectionSprites() throws IOException {
        sprite("mythos_backpack", img -> {
            shape(img, (x, y) -> x >= 7 && x <= 25 && y >= 9 && y <= 28 && !(y > 26 && (x < 9 || x > 23)),
                    (x, y) -> mix(rgb(0x7e22ce), rgb(0x3b0764), y / 30.0), INK);
            shape(img, (x, y) -> x >= 9 && x <= 23 && y >= 6 && y <= 13, (x, y) -> mix(rgb(0xa855f7), rgb(0x6b21a8), y / 14.0), INK);
            img.fill(14, 12, 4, 4, rgb(0xf59e0b));
            img.fill(15, 13, 2, 2, rgb(0xfde68a));
            img.fill(10, 18, 12, 1, rgb(0x581c87));
            img.fill(11, 20, 10, 6, rgb(0x6b21a8));
            img.fill(12, 3, 8, 2, rgb(0x92400e));
            img.outline(INK);
        });
        sprite("healing_totem", img -> {
            shape(img, (x, y) -> x >= 10 && x <= 21 && y >= 5 && y <= 28, (x, y) -> mix(rgb(0x86efac), rgb(0x15803d), y / 30.0), INK);
            shape(img, (x, y) -> (x >= 5 && x < 10 || x > 21 && x <= 26) && y >= 12 && y <= 17, (x, y) -> rgb(0x22c55e), 0);
            heart(img, 16, 18, rgb(0xef4444));
            img.fill(12, 8, 2, 2, INK);
            img.fill(18, 8, 2, 2, INK);
            img.outline(INK);
        });
        sprite("magnet_charm", img -> {
            shape(img, (x, y) -> {
                double d = Math.hypot(x - 16, y - 14);
                return (d <= 11 && d >= 5.5 && y <= 14) || ((x >= 5 && x <= 10 || x >= 22 && x <= 27) && y >= 14 && y <= 26);
            }, (x, y) -> x < 16 ? rgb(0xef4444) : rgb(0x3b82f6), INK);
            img.fill(5, 23, 6, 4, rgb(0xe5e7eb));
            img.fill(22, 23, 6, 4, rgb(0xe5e7eb));
            img.outline(INK);
        });
        sprite("miner_lantern", img -> {
            shape(img, (x, y) -> x >= 10 && x <= 22 && y >= 9 && y <= 27, (x, y) -> (x == 10 || x == 22 || y == 9 || y == 27 || y == 18)
                    ? rgb(0x475569) : mix(rgb(0xfef08a), rgb(0xf59e0b), Math.hypot(x - 16, y - 18) / 9.0), INK);
            shape(img, (x, y) -> Math.hypot(x - 16, y - 7) <= 3.5 && y <= 8, (x, y) -> rgb(0x334155), 0);
            img.fill(15, 13, 3, 3, rgb(0xffffff));
            img.outline(INK);
        });
        sprite("xp_tome", img -> {
            shape(img, (x, y) -> x >= 7 && x <= 25 && y >= 5 && y <= 27, (x, y) -> x <= 9 ? rgb(0x14532d) : mix(rgb(0x22c55e), rgb(0x166534), y / 30.0), INK);
            img.fill(10, 25, 15, 2, rgb(0xf5f0e6));
            shape(img, (x, y) -> Math.abs(x - 17) + Math.abs(y - 15) <= 5, (x, y) -> mix(rgb(0xbef264), rgb(0x65a30d), y / 20.0), 0);
            img.fill(16, 14, 2, 2, rgb(0xffffff));
            img.outline(INK);
        });
        sprite("return_feather", img -> {
            shape(img, (x, y) -> {
                double t = (x + (31 - y)) / 2.0;
                double off = (x - (31 - y)) / 2.0;
                return t > 4 && t < 27 && Math.abs(off) < Math.sin((t - 4) / 23.0 * Math.PI) * 5.5;
            }, (x, y) -> (x + y) % 5 == 0 ? rgb(0xcbd5e1) : rgb(0xffffff), rgb(0x64748b));
            for (int i = 0; i < 22; i++) {
                img.set(5 + i, 26 - i, rgb(0x94a3b8));
            }
            img.outline(rgb(0x334155));
        });
        sprite("lucky_clover", img -> {
            for (int[] c : new int[][]{{11, 10}, {21, 10}, {11, 19}, {21, 19}}) {
                shape(img, (x, y) -> Math.hypot(x - c[0], y - c[1]) <= 5, (x, y) -> mix(rgb(0x4ade80), rgb(0x15803d), Math.hypot(x - c[0] + 2, y - c[1] + 2) / 7), 0);
            }
            for (int i = 0; i < 9; i++) {
                img.set(16 + i / 3, 19 + i, rgb(0x166534));
            }
            img.set(16, 14, rgb(0xfde68a));
            img.outline(rgb(0x052e16));
        });

        // ---- wave 10: personal boosters, mutation serum, meteor shower, Mythos magic ----------------------
        boosterSprite("coin_booster", rgb(0xfde047), rgb(0xb45309), (img) -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 17) <= 5.5, (x, y) -> mix(rgb(0xfef08a), rgb(0xca8a04), (x + y - 22) / 12.0), rgb(0x713f12));
            img.fill(15, 14, 2, 7, rgb(0x854d0e));
        });
        boosterSprite("gem_booster", rgb(0xe879f9), rgb(0x7e22ce), (img) -> {
            double[] gem = {16, 11, 21, 15, 16, 23, 11, 15};
            shape(img, (x, y) -> inPolygon(x, y, gem), (x, y) -> mix(rgb(0xf5d0fe), rgb(0xa21caf), (y - 11) / 12.0), rgb(0x581c87));
            img.set(14, 14, rgb(0xffffff));
        });
        boosterSprite("xp_booster", rgb(0xa3e635), rgb(0x3f6212), (img) -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 17) <= 5, (x, y) -> mix(rgb(0xd9f99d), rgb(0x4d7c0f), Math.hypot(x - 14, y - 15) / 7), rgb(0x1a2e05));
            img.set(14, 15, rgb(0xffffff));
            img.set(15, 15, rgb(0xffffff));
        });
        boosterSprite("find_booster", rgb(0x22d3ee), rgb(0x155e75), (img) -> {
            shape(img, (x, y) -> Math.abs(Math.hypot(x - 15, y - 16) - 4) < 1.3, (x, y) -> rgb(0xe2e8f0), rgb(0x0f172a));
            for (int i = 0; i < 4; i++) {
                img.set(18 + i, 19 + i, rgb(0x78350f));
                img.set(19 + i, 19 + i, rgb(0x78350f));
            }
            img.set(14, 15, rgb(0xa5f3fc));
        });
        sprite("mutation_serum", img -> {
            shape(img, (x, y) -> x >= 12 && x <= 20 && y >= 5 && y <= 27 && !(y > 24 && Math.abs(x - 16) > 27 - y + 1),
                    (x, y) -> y < 11 ? rgb(0xcbd5e1) : mix(rgb(0xbef264), rgb(0x16a34a), (y - 11) / 16.0), INK);
            img.fill(11, 4, 11, 2, rgb(0x64748b));
            for (int[] b : new int[][]{{14, 15}, {17, 19}, {15, 22}, {18, 13}}) {
                img.set(b[0], b[1], rgb(0xf0fdf4));
            }
            // DNA strand
            for (int y = 12; y <= 25; y++) {
                img.set(16 + (int) Math.round(Math.sin(y * 0.8) * 2), y, rgb(0x14532d));
            }
            img.outline(INK);
        });
        sprite("meteor_shower", img -> {
            for (int[] m : new int[][]{{9, 9, 3}, {20, 7, 2}, {14, 19, 4}}) {
                for (int i = 1; i < 9; i++) {
                    img.set(m[0] - i, m[1] - i, mix(rgb(0xfde047), rgb(0xdc2626), i / 9.0));
                    if (m[2] > 2) {
                        img.set(m[0] - i + 1, m[1] - i, mix(rgb(0xfb923c), rgb(0x7f1d1d), i / 9.0));
                    }
                }
                int r = m[2];
                shape(img, (x, y) -> Math.hypot(x - m[0] - 1, y - m[1] - 1) <= r, (x, y) -> mix(rgb(0x78716c), rgb(0x292524),
                        Math.hypot(x - m[0], y - m[1]) / (r + 1.0)), 0);
                img.set(m[0], m[1], rgb(0xfdba74));
            }
            img.outline(INK);
        });
        sprite("spell_scroll", img -> {
            shape(img, (x, y) -> x >= 8 && x <= 24 && y >= 7 && y <= 25, (x, y) -> mix(rgb(0xfef3c7), rgb(0xd6a85c), (y - 7) / 18.0), INK);
            img.fill(6, 5, 21, 3, rgb(0x92400e));
            img.fill(6, 24, 21, 3, rgb(0x92400e));
            double[] rune = {16, 10, 20, 16, 16, 22, 12, 16};
            shape(img, (x, y) -> inPolygon(x, y, rune) && !inPolygon(x, y, new double[]{16, 12, 18, 16, 16, 20, 14, 16}),
                    (x, y) -> rgb(0x9333ea), 0);
            img.set(16, 16, rgb(0xe9d5ff));
            img.outline(INK);
        });
        sprite("mana_elixir", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 19) <= 8 || x >= 14 && x <= 18 && y >= 6 && y <= 13,
                    (x, y) -> y < 12 ? rgb(0xe2e8f0) : mix(rgb(0x60a5fa), rgb(0x4c1d95), Math.hypot(x - 13, y - 16) / 11), INK);
            img.fill(13, 4, 7, 3, rgb(0x7c2d12));
            img.set(12, 16, rgb(0xffffff));
            img.set(13, 15, rgb(0xffffff));
            img.set(19, 21, rgb(0xc4b5fd));
            img.outline(INK);
        });
        sprite("mythos_wand", img -> {
            for (int i = 0; i < 20; i++) {
                img.set(6 + i, 27 - i, mix(rgb(0x7c2d12), rgb(0x451a03), i / 20.0));
                img.set(7 + i, 27 - i, mix(rgb(0xa16207), rgb(0x78350f), i / 20.0));
            }
            img.fill(11, 20, 3, 3, rgb(0xfacc15));
            shape(img, (x, y) -> Math.hypot(x - 26, y - 6) <= 4, (x, y) -> mix(rgb(0xf5d0fe), rgb(0x9333ea), Math.hypot(x - 25, y - 5) / 5), 0);
            img.set(25, 5, rgb(0xffffff));
            for (int[] sp : new int[][]{{21, 3}, {29, 10}, {30, 3}, {22, 11}}) {
                img.set(sp[0], sp[1], rgb(0xe9d5ff));
            }
            img.outline(INK);
        });
        sprite("haste_rune", img -> {
            shape(img, (x, y) -> x >= 8 && x <= 24 && y >= 4 && y <= 28 && !(x + y < 14 || x - y > 18), (x, y) -> mix(rgb(0x9ca3af), rgb(0x4b5563), y / 30.0), INK);
            int[][] bolt = {{17, 8}, {13, 16}, {17, 16}, {14, 24}, {20, 14}, {16, 14}, {19, 8}};
            double[] poly = new double[bolt.length * 2];
            for (int i = 0; i < bolt.length; i++) {
                poly[i * 2] = bolt[i][0];
                poly[i * 2 + 1] = bolt[i][1];
            }
            shape(img, (x, y) -> inPolygon(x, y, poly), (x, y) -> mix(rgb(0xfef08a), rgb(0xeab308), y / 26.0), 0);
            img.outline(INK);
        });
        sprite("candy_bucket", img -> {
            shape(img, (x, y) -> y >= 11 && y <= 28 && Math.abs(x - 16) <= 10 - (28 - y) * 0.12, (x, y) -> mix(rgb(0xfb923c), rgb(0xc2410c), (x - 6) / 20.0), INK);
            for (int x = 8; x <= 24; x += 4) {
                for (int y = 12; y <= 27; y++) {
                    if (img.get(x, y) != 0) {
                        img.set(x, y, rgb(0xb45309));
                    }
                }
            }
            img.fill(11, 16, 3, 3, INK);
            img.fill(19, 16, 3, 3, INK);
            img.fill(12, 22, 9, 2, INK);
            img.fill(10, 8, 4, 3, rgb(0xef4444));
            img.fill(15, 7, 3, 4, rgb(0xa855f7));
            img.fill(19, 8, 4, 3, rgb(0xfde047));
            shape(img, (x, y) -> Math.abs(Math.hypot(x - 16, y - 12) - 11) < 0.8 && y < 12, (x, y) -> rgb(0x334155), 0);
            img.outline(INK);
        });
        animatedSprite("ghost_lantern", 8, 2, (img, f, n) -> {
            shape(img, (x, y) -> x >= 10 && x <= 22 && y >= 9 && y <= 27, (x, y) -> (x == 10 || x == 22 || y == 9 || y == 27)
                    ? rgb(0x1e293b) : mix(rgb(0x0e7490), rgb(0x083344), (y - 9) / 18.0), INK);
            double flick = 0.7 + 0.3 * wave(f * 0.9);
            shape(img, (x, y) -> {
                double dx = (x - 16) / 3.6;
                double dy = (y - 21 + f % 2) / 5.5;
                return dx * dx + dy * dy <= 1 || inPolygon(x, y, new double[]{13, 20, 16, 11 + f % 3, 19, 20});
            }, (x, y) -> mix(rgb(0xcffafe), rgb(0x22d3ee), Math.hypot(x - 16, y - 20) / 6 / flick), 0);
            img.fill(14, 5, 5, 3, rgb(0x334155));
            img.fill(15, 3, 3, 2, rgb(0x334155));
            img.outline(INK);
        });
        sprite("pumpkin_bomb", img -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 11.0, 2) + Math.pow((y - 19) / 9.0, 2) <= 1, (x, y) -> (x % 5 == 1)
                    ? rgb(0xc2410c) : mix(rgb(0xfdba74), rgb(0xea580c), Math.hypot(x - 13, y - 15) / 12), INK);
            img.fill(12, 16, 3, 3, rgb(0xfde047));
            img.fill(18, 16, 3, 3, rgb(0xfde047));
            img.fill(12, 22, 9, 2, rgb(0xfde047));
            img.fill(15, 7, 3, 4, rgb(0x15803d));
            for (int i = 0; i < 6; i++) {
                img.set(18 + i, 6 - i / 2, rgb(0x78716c));
            }
            img.set(24, 3, rgb(0xfde047));
            img.set(25, 2, rgb(0xff4d1a));
            img.outline(INK);
        });
        sprite("candy_corn", img -> {
            double[] tri = {16, 4, 27, 27, 5, 27};
            shape(img, (x, y) -> inPolygon(x, y, tri), (x, y) -> y < 12 ? rgb(0xfef9c3) : y < 20 ? rgb(0xfb923c) : rgb(0xfacc15), rgb(0x713f12));
            img.set(13, 15, rgb(0xffffff));
        });
        sprite("lollipop", img -> {
            for (int i = 0; i < 12; i++) {
                img.set(15, 18 + i, rgb(0xf5f0e6));
                img.set(16, 18 + i, rgb(0xd6d3d1));
            }
            shape(img, (x, y) -> Math.hypot(x - 16, y - 11) <= 8.5, (x, y) -> {
                double a = Math.atan2(y - 11, x - 16) + Math.hypot(x - 16, y - 11) * 0.55;
                return Math.sin(a * 2) > 0 ? rgb(0xf472b6) : rgb(0xc084fc);
            }, rgb(0x701a75));
        });
        sprite("caramel_apple", img -> {
            img.fill(15, 2, 2, 9, rgb(0xd6d3d1));
            shape(img, (x, y) -> Math.hypot(x - 16, y - 19) <= 10, (x, y) -> y < 17 || (y < 20 && (x * 7) % 5 == 0)
                    ? mix(rgb(0xfbbf24), rgb(0xb45309), (y - 9) / 12.0) : mix(rgb(0xef4444), rgb(0x991b1b), Math.hypot(x - 12, y - 16) / 13), rgb(0x450a0a));
            img.set(12, 13, rgb(0xfef3c7));
        });
        sprite("gift_box", img -> {
            shape(img, (x, y) -> x >= 5 && x <= 27 && y >= 11 && y <= 28, (x, y) -> Math.abs(x - 16) <= 1 || Math.abs(y - 19) <= 1
                    ? rgb(0x16a34a) : mix(rgb(0xef4444), rgb(0xb91c1c), y / 30.0), INK);
            shape(img, (x, y) -> Math.hypot(x - 12, y - 8) <= 3.5 || Math.hypot(x - 20, y - 8) <= 3.5, (x, y) -> rgb(0x22c55e), 0);
            img.fill(15, 7, 3, 4, rgb(0x15803d));
            img.outline(INK);
        });
        sprite("hot_cocoa", img -> {
            shape(img, (x, y) -> x >= 7 && x <= 22 && y >= 12 && y <= 28, (x, y) -> y <= 13 ? rgb(0x78350f) : mix(rgb(0xfef2f2), rgb(0xfecaca), y / 30.0), INK);
            shape(img, (x, y) -> Math.abs(Math.hypot(x - 23, y - 20) - 4) < 1.2 && x > 22, (x, y) -> rgb(0xfecaca), 0);
            img.fill(10, 18, 9, 2, rgb(0xef4444));
            for (int i = 0; i < 6; i++) {
                img.set(11 + (i % 2), 9 - i, argb(170, 0xffffff));
                img.set(17 - (i % 2), 9 - i, argb(170, 0xffffff));
            }
            img.outline(INK);
        });
        sprite("snow_cannon", img -> {
            shape(img, (x, y) -> segDist(x, y, 6, 24, 25, 9) < 4.2, (x, y) -> mix(rgb(0xf8fafc), rgb(0x93c5fd), (x + y) / 50.0), INK);
            shape(img, (x, y) -> Math.hypot(x - 9, y - 26) <= 4, (x, y) -> rgb(0x1e3a8a), 0);
            shape(img, (x, y) -> Math.hypot(x - 26, y - 6) <= 2.5, (x, y) -> rgb(0xffffff), 0);
            img.fill(12, 17, 2, 2, rgb(0x0ea5e9));
            img.outline(INK);
        });
        animatedSprite("phoenix_feather", 8, 2, (img, f, n) -> {
            shape(img, (x, y) -> {
                double t = (x + (31 - y)) / 2.0;
                double off = (x - (31 - y)) / 2.0;
                return t > 3 && t < 28 && Math.abs(off) < Math.sin((t - 3) / 25.0 * Math.PI) * 6.5;
            }, (x, y) -> {
                double heat = wave((x + y) * 0.35 - f * 0.8);
                return mix(mix(rgb(0xff4d1a), rgb(0xfde047), heat), rgb(0xffffff), Math.max(0, heat - 0.85) * 4);
            }, rgb(0x7f1d1d));
            for (int i = 0; i < 22; i++) {
                img.set(5 + i, 26 - i, rgb(0xfef08a));
            }
        });
        animatedSprite("nebula_orb", 8, 3, (img, f, n) -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 12, (x, y) -> {
                double a = Math.atan2(y - 16, x - 16) + Math.hypot(x - 16, y - 16) * 0.3 - f * Math.PI / 4;
                double s = wave(a * 2);
                int c = mix(mix(rgb(0x1e1b4b), rgb(0x7c3aed), s), rgb(0xf472b6), wave(a * 3 + 1) * 0.5);
                return (x * 13 + y * 7 + f * 3) % 37 == 0 ? rgb(0xffffff) : c;
            }, rgb(0x0f0518));
            img.set(11, 10, argb(200, 0xffffff));
            img.set(12, 10, argb(140, 0xffffff));
        });
        sprite("dragon_flask", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 21) <= 8.5 || (x >= 13 && x <= 19 && y >= 6 && y <= 14), (x, y) -> y < 14
                    ? rgb(0xe2e8f0) : mix(rgb(0xfbbf24), rgb(0xdc2626), Math.hypot(x - 16, y - 23) / 8), INK);
            img.fill(12, 4, 8, 3, rgb(0x92400e));
            shape(img, (x, y) -> inPolygon(x, y, new double[]{13, 25, 16, 15, 19, 25}), (x, y) -> rgb(0xfef08a), 0);
            img.set(12, 18, rgb(0xffffff));
        });
        sprite("grappling_hook", img -> drawGrapple(img, true));
        sprite("grappling_hook_cast", img -> drawGrapple(img, false));
        for (String rod : new String[]{"grappling_hook", "grappling_hook_cast"}) {
            write("assets/mythoscraft/models/item/" + rod + ".json",
                    "{\n  \"parent\": \"minecraft:item/handheld_rod\",\n  \"textures\": {\"layer0\": \"mythoscraft:item/" + rod + "\"}\n}\n");
        }
        bow("ghost_bow", 0xe0f2fe, 0x6366f1, 0x22d3ee);
        bow("comet_bow", 0xf472b6, 0x7c3aed, 0xfde68a);
    }

    static void heart(Img img, int cx, int cy, int color) {
        shape(img, (x, y) -> {
            double dx = (x - cx) / 4.0;
            double dy = (y - cy) / 4.0;
            return Math.pow(dx * dx + dy * dy - 1, 3) - dx * dx * Math.pow(-dy, 3) <= 0;
        }, (x, y) -> color, 0);
    }

    static void drawGrapple(Img img, boolean withHook) {
        for (int i = 0; i < 24; i++) {
            img.set(3 + i, 28 - i, rgb(0x78350f));
            img.set(4 + i, 28 - i, rgb(0x92400e));
        }
        img.fill(3, 25, 4, 4, rgb(0x334155));
        if (withHook) {
            shape(img, (x, y) -> Math.abs(Math.hypot(x - 24, y - 10) - 5) < 1.4 && !(x < 22 && y > 10), (x, y) -> rgb(0xcbd5e1), 0);
            shape(img, (x, y) -> segDist(x, y, 24, 5, 28, 3) < 1.2 || segDist(x, y, 29, 10, 30, 14) < 1.2, (x, y) -> rgb(0xe2e8f0), 0);
        }
        img.outline(INK);
    }

    /** Bow with standby + 3 pulling stages (arrow drawn further back). */
    static void bow(String name, int wood, int dark, int string) throws IOException {
        for (int stage = -1; stage <= 2; stage++) {
            int pull = stage + 1;
            Img img = new Img(32, 32);
            // like the vanilla sprite: limbs from top-left to bottom-right, bulging towards the top-right,
            // arrow pointing to the top-right; the string is pulled towards the bottom-left
            double sq = Math.sqrt(2);
            shape(img, (x, y) -> {
                double t = ((x - 3) + (y - 3)) / 2.0 / 25.0;
                double perp = (x - y) / sq;
                double bulge = Math.sin(Math.max(0, Math.min(1, t)) * Math.PI) * 8.5;
                return t >= 0 && t <= 1 && Math.abs(perp - bulge) < 1.5;
            }, (x, y) -> Math.abs(x + y - 31) < 3 ? rgb(0x3f2a1d)
                    : mix(rgb(wood), rgb(dark), Math.abs(x + y - 31) / 26.0), INK);
            double pullBack = pull * 2.2;
            for (int i = 0; i <= 50; i++) {
                double t = i / 50.0;
                double off = -Math.sin(t * Math.PI) * pullBack / sq;
                img.set((int) Math.round(3 + 25 * t + off), (int) Math.round(3 + 25 * t - off), argb(235, string));
            }
            if (stage >= 0) {
                double cx = 15.5 - pullBack / sq;
                double cy = 15.5 + pullBack / sq;
                for (int i = 0; i <= 17; i++) {
                    int x = (int) Math.round(cx + i);
                    int y = (int) Math.round(cy - i);
                    img.set(x, y, i < 3 ? rgb(string) : i > 14 ? rgb(0xe2e8f0) : rgb(0x8b5a2b));
                    if (i > 14) {
                        img.set(x - 1, y, rgb(0x94a3b8));
                        img.set(x, y + 1, rgb(0x94a3b8));
                    }
                    if (i < 3) {
                        img.set(x - 1, y - 1, argb(200, string));
                        img.set(x + 1, y + 1, argb(200, string));
                    }
                }
            }
            String file = stage < 0 ? name : name + "_pulling_" + stage;
            // vanilla draws the bow on the upper left with the arrow pointing up-left – mirror so it is held correctly
            Img mirrored = new Img(32, 32);
            for (int x = 0; x < 32; x++) {
                for (int y = 0; y < 32; y++) {
                    mirrored.set(31 - x, y, img.get(x, y));
                }
            }
            mirrored.save("assets/mythoscraft/textures/item/" + file + ".png");
            write("assets/mythoscraft/models/item/" + file + ".json", "{\n  \"parent\": \"minecraft:item/bow\",\n  \"textures\": {\"layer0\": \"mythoscraft:item/" + file + "\"}\n}\n");
        }
    }

    // =====================================================================================================
    // New 3D weapons, staffs and trophies
    // =====================================================================================================

    static void collectionModels() throws IOException {
        Map<String, String> tex = Map.of("m", "mythoscraft:item/materials");

        List<Element> scythe = new ArrayList<>();
        scythe.add(rod(1.2, -3.5, -2.5, "gold"));
        scythe.add(rod(0.65, -2.5, 15.5, "wood"));
        scythe.add(rod(0.9, 3, 4, "brass"));
        scythe.add(box(6.6, 14.2, 6.6, 9.4, 17, 9.4, null).anim("p"));
        scythe.add(box(9.4, 15.6, 7.6, 15.5, 16.6, 8.4, "obsidian"));
        scythe.add(box(13.5, 12, 7.65, 15.5, 15.6, 8.35, "silver"));
        scythe.add(box(12.2, 10.2, 7.7, 14.4, 12.2, 8.3, "silver"));
        scythe.add(box(9.6, 15.2, 7.7, 15.2, 15.6, 8.3, "silver"));
        model("pumpkin_scythe", withAnim(tex, "p", "anim_pumpkin"), diagonal(scythe), HANDHELD_DISPLAY, "front");

        List<Element> batBlade = new ArrayList<>();
        batBlade.add(rod(1.2, -3, -1.8, "ruby"));
        batBlade.add(rod(0.65, -1.8, 4, "leather"));
        batBlade.add(box(3.2, 4, 7.4, 12.8, 5.2, 8.6, "bat"));
        batBlade.add(box(2.4, 5.2, 7.6, 4.4, 6.4, 8.4, "bat"));
        batBlade.add(box(11.6, 5.2, 7.6, 13.6, 6.4, 8.4, "bat"));
        batBlade.add(box(6.9, 5.2, 7.6, 9.1, 17, 8.4, "bat"));
        batBlade.add(box(7.7, 6, 7.5, 8.3, 16.4, 8.5, "ruby").glow());
        batBlade.add(box(7.4, 17, 7.7, 8.6, 18.6, 8.3, "bat"));
        model("bat_blade", tex, diagonal(batBlade), HANDHELD_DISPLAY, "front");

        List<Element> frostBlade = new ArrayList<>();
        frostBlade.add(rod(1.2, -3, -1.8, "diamond"));
        frostBlade.add(rod(0.65, -1.8, 4, "silver"));
        frostBlade.add(box(4.5, 4, 7.1, 11.5, 5.2, 8.9, "silver"));
        frostBlade.add(box(6.8, 5.2, 7.6, 9.2, 17.2, 8.4, "ice"));
        frostBlade.add(box(7.6, 5.6, 7.45, 8.4, 16.6, 8.55, null).anim("f").glow());
        frostBlade.add(box(7.4, 17.2, 7.65, 8.6, 18.8, 8.35, "ice"));
        model("frost_blade", withAnim(tex, "f", "anim_frost"), diagonal(frostBlade), HANDHELD_DISPLAY, "front");

        List<Element> cane = new ArrayList<>();
        cane.add(rod(0.9, -3, 15, "candy"));
        cane.add(box(8, 14, 7.1, 12, 15.8, 8.9, "candy"));
        cane.add(box(10.2, 11.4, 7.1, 12, 14, 8.9, "candy"));
        cane.add(rod(1.0, 3, 3.6, "leaf"));
        model("candy_cane", tex, diagonal(cane), HANDHELD_DISPLAY, "front");

        List<Element> saber = new ArrayList<>();
        saber.add(rod(1.2, -3, -1.8, "night"));
        saber.add(rod(0.65, -1.8, 4, "night"));
        saber.add(box(4.6, 4, 7.2, 11.4, 5, 8.8, "silver"));
        saber.add(box(7.4, 3.6, 6.8, 8.6, 5.4, 9.2, "crystal").glow());
        saber.add(box(6.8, 5, 7.55, 9.2, 18, 8.45, null).anim("s").glow());
        saber.add(box(7.3, 18, 7.7, 8.7, 19.6, 8.3, null).anim("s").glow());
        model("star_saber", withAnim(tex, "s", "anim_star"), diagonal(saber), HANDHELD_DISPLAY, "front");

        List<Element> infernoAxe = new ArrayList<>();
        infernoAxe.add(rod(1.2, -3, -2, "gold"));
        infernoAxe.add(rod(0.8, -2, 3, "leather"));
        infernoAxe.add(rod(0.7, 3, 15, "netherite"));
        infernoAxe.add(box(8.6, 11, 7.2, 12.6, 17, 8.8, "obsidian"));
        infernoAxe.add(box(12.6, 10, 7.4, 13.8, 18, 8.6, null).anim("l").glow());
        infernoAxe.add(box(5.2, 13, 7.5, 7.4, 15, 8.5, "obsidian"));
        infernoAxe.add(box(9.4, 13.4, 7.0, 11.6, 14.8, 9.0, null).anim("l").glow());
        model("inferno_axe", withAnim(tex, "l", "anim_lava"), diagonal(infernoAxe), HANDHELD_DISPLAY, "front");

        List<Element> broom = new ArrayList<>();
        broom.add(rod(0.55, -3.5, 12.5, "wood"));
        broom.add(rod(1.3, 12, 13.2, "gold"));
        broom.add(box(6.2, 13.2, 6.2, 9.8, 18.5, 9.8, "straw"));
        broom.add(box(5.6, 17, 5.6, 10.4, 19.5, 10.4, "straw"));
        broom.add(rod(0.9, -1, 0, "pumpkin"));
        model("witch_broom", tex, diagonal(broom), HANDHELD_DISPLAY, "front");

        List<Element> iceWand = new ArrayList<>();
        iceWand.add(rod(0.55, -2.5, 13, "silver"));
        iceWand.add(rod(0.85, 4, 4.6, "diamond"));
        iceWand.add(rod(1.3, 12.6, 13.4, "silver"));
        iceWand.add(box(6.9, 13.4, 6.9, 9.1, 17.6, 9.1, null).anim("f").glow());
        iceWand.add(box(7.5, 17.6, 7.5, 8.5, 19, 8.5, "ice").glow());
        model("ice_wand", withAnim(tex, "f", "anim_frost"), diagonal(iceWand), HANDHELD_DISPLAY, "front");

        List<Element> voidScepter = new ArrayList<>();
        voidScepter.add(rod(0.6, -3, 13, "night"));
        voidScepter.add(rod(0.95, 2, 2.6, "silver"));
        voidScepter.add(rod(1.6, 12.6, 13.6, "silver"));
        voidScepter.add(box(6.6, 13.6, 6.6, 9.4, 16.4, 9.4, null).anim("s").glow());
        voidScepter.add(box(5.4, 14.6, 7.6, 6.6, 15.4, 8.4, "silver"));
        voidScepter.add(box(9.4, 14.6, 7.6, 10.6, 15.4, 8.4, "silver"));
        voidScepter.add(box(7.6, 16.4, 7.6, 8.4, 18.4, 8.4, "silver"));
        model("void_scepter", withAnim(tex, "s", "anim_star"), diagonal(voidScepter), HANDHELD_DISPLAY, "front");

        List<Element> emberWand = new ArrayList<>();
        emberWand.add(rod(0.55, -2.5, 13, "netherite"));
        emberWand.add(rod(0.85, 3, 3.6, "gold"));
        emberWand.add(rod(1.2, 12.6, 13.4, "gold"));
        emberWand.add(box(6.9, 13.4, 6.9, 9.1, 16.2, 9.1, null).anim("l").glow());
        emberWand.add(box(7.4, 16.2, 7.4, 8.6, 17.4, 8.6, null).anim("l").glow());
        model("ember_wand", withAnim(tex, "l", "anim_lava"), diagonal(emberWand), HANDHELD_DISPLAY, "front");

        trophy("trophy_daily", "silver", "steel", "diamond");
        trophy("trophy_weekly", "gold", "brass", "ruby");
        trophy("trophy_monthly", "gold", "obsidian", "crystal");
    }

    static Map<String, String> withAnim(Map<String, String> base, String key, String texture) {
        Map<String, String> m = new LinkedHashMap<>(base);
        m.put(key, "mythoscraft:item/" + texture);
        return m;
    }

    static void trophy(String name, String cup, String base, String gem) throws IOException {
        List<Element> t = new ArrayList<>();
        t.add(box(4, 0, 4, 12, 1.5, 12, base));
        t.add(box(5, 1.5, 5, 11, 3, 11, base));
        t.add(box(7, 3, 7, 9, 6.5, 9, cup));
        t.add(box(6, 6.5, 6, 10, 7.5, 10, cup));
        t.add(box(4.5, 7.5, 4.5, 11.5, 13, 11.5, cup));
        t.add(box(4, 12.5, 4, 12, 13.5, 12, cup));
        t.add(box(2.6, 8.5, 7.4, 4.5, 9.5, 8.6, cup));
        t.add(box(2.6, 9.5, 7.4, 3.6, 12, 8.6, cup));
        t.add(box(11.5, 8.5, 7.4, 13.4, 9.5, 8.6, cup));
        t.add(box(12.4, 9.5, 7.4, 13.4, 12, 8.6, cup));
        t.add(box(7, 9, 4.1, 9, 11, 4.5, gem).glow());
        t.add(box(7, 9, 11.5, 9, 11, 11.9, gem).glow());
        model(name, Map.of("m", "mythoscraft:item/materials"), t, OBJECT_DISPLAY, "side");
    }

    // =====================================================================================================
    // Armour: worn texture (equipment layers) + inventory icons
    // =====================================================================================================

    record ArmorSet(String id, int base, int dark, int trim, int accent, String pattern) {
    }

    static final ArmorSet[] ARMOR_SETS = {
            new ArmorSet("mythos", 0x5b21b6, 0x2e1065, 0xf59e0b, 0xe879f9, "rune"),
            new ArmorSet("pumpkin", 0xea580c, 0x7c2d12, 0x1c1917, 0xfde047, "rib"),
            new ArmorSet("frost", 0xbae6fd, 0x38bdf8, 0xf8fafc, 0x0ea5e9, "crystal"),
            new ArmorSet("inferno", 0x7f1d1d, 0x1c1917, 0xf59e0b, 0xff6a00, "crack"),
            new ArmorSet("sculk", 0x0b3b44, 0x061a20, 0x334155, 0x2dd4bf, "vein"),
            new ArmorSet("dragon", 0x4c1d95, 0x1e0b2e, 0x991b1b, 0xf472b6, "scale"),
            new ArmorSet("storm", 0xbfdbfe, 0x64748b, 0xf8fafc, 0xfacc15, "bolt"),
            new ArmorSet("angel", 0xfefce8, 0xe7e5e4, 0xf59e0b, 0xfde68a, "feather"),
            new ArmorSet("ninja", 0x1f2937, 0x030712, 0x4b5563, 0xdc2626, "rib"),
            new ArmorSet("knight", 0xe2e8f0, 0x94a3b8, 0x475569, 0x1d4ed8, "crystal"),
            new ArmorSet("miner", 0xa16207, 0x57534e, 0x1c1917, 0xfde047, "crack"),
            new ArmorSet("viking", 0x78716c, 0x44403c, 0x7f1d1d, 0xd6b98c, "rib"),
            new ArmorSet("ocean", 0x0891b2, 0x164e63, 0xfde68a, 0x67e8f9, "scale"),
            new ArmorSet("druid", 0x4d7c0f, 0x3f2a14, 0x78350f, 0x86efac, "vein"),
            new ArmorSet("sky", 0xf0f9ff, 0x7dd3fc, 0xfde047, 0x38bdf8, "feather"),
            new ArmorSet("crystal", 0xa855f7, 0x3b0764, 0xe9d5ff, 0xf0abfc, "crystal"),
            new ArmorSet("pharaoh", 0xfbbf24, 0x1d4ed8, 0xfde68a, 0x22d3ee, "rib"),
            new ArmorSet("astronaut", 0xf8fafc, 0x94a3b8, 0x64748b, 0xf97316, "crystal")
    };

    static int armorPixel(ArmorSet s, int x, int y, Random rnd) {
        int c = mix(rgb(s.base), rgb(s.dark), ((x * 3 + y * 5) % 11) / 16.0 + rnd.nextDouble() * 0.12);
        switch (s.pattern) {
            case "rib" -> {
                if (x % 4 == 0) {
                    c = rgb(s.dark);
                }
            }
            case "crack" -> {
                if (Math.abs(Math.sin(x * 0.9 + Math.sin(y * 0.7) * 2)) < 0.12) {
                    c = rgb(s.accent);
                }
            }
            case "vein" -> {
                if (Math.abs(Math.sin(x * 0.6 + y * 0.45) + Math.cos(y * 0.5)) < 0.14) {
                    c = rgb(s.accent);
                }
            }
            case "crystal" -> {
                if ((x + y) % 7 == 0) {
                    c = mix(c, rgb(0xffffff), 0.55);
                }
            }
            case "scale" -> {
                if ((y % 3 == 0 && x % 2 == 0) || ((x + (y / 3) % 2) % 4 == 0 && y % 3 == 1)) {
                    c = shade(c, 0.6);
                } else if (rnd.nextInt(30) == 0) {
                    c = rgb(s.accent);
                }
            }
            case "bolt" -> {
                if (Math.abs(((x * 2 + y) % 12) - 6) == 0 && y % 5 < 3) {
                    c = rgb(s.accent);
                }
            }
            case "feather" -> {
                if ((x + y * 2) % 6 == 0) {
                    c = mix(c, rgb(s.accent), 0.5);
                }
            }
            default -> {
                if (rnd.nextInt(40) == 0) {
                    c = rgb(s.accent);
                }
            }
        }
        return c;
    }

    /** Paints the 6 faces of a box at (u,v) with size w×h×d, only rows from {@code fromRow} downwards. */
    static void armorBox(Img img, ArmorSet s, int u, int v, int w, int h, int d, int fromRow, Random rnd) {
        int[][] faces = {{u + d, v, w, d}, {u + d + w, v, w, d}, {u, v + d, d, h}, {u + d, v + d, w, h}, {u + d + w, v + d, d, h},
                {u + d + w + d, v + d, w, h}};
        for (int i = 0; i < faces.length; i++) {
            int[] f = faces[i];
            boolean side = i >= 2;
            for (int x = 0; x < f[2]; x++) {
                for (int y = 0; y < f[3]; y++) {
                    if (side && y < fromRow) {
                        continue;
                    }
                    if (!side && i == 0 && fromRow > 0) {
                        continue;
                    }
                    boolean edge = x == 0 || x == f[2] - 1 || y == f[3] - 1 || (side && y == fromRow);
                    img.set(f[0] + x, f[1] + y, edge ? rgb(s.trim) : armorPixel(s, f[0] + x, f[1] + y, rnd));
                }
            }
        }
    }

    static void armor() throws IOException {
        for (ArmorSet s : ARMOR_SETS) {
            Random rnd = new Random(s.id.hashCode());
            Img layer1 = new Img(64, 32);
            armorBox(layer1, s, 0, 0, 8, 8, 8, 0, rnd);
            armorBox(layer1, s, 16, 16, 8, 12, 4, 0, rnd);
            armorBox(layer1, s, 40, 16, 4, 12, 4, 0, rnd);
            armorBox(layer1, s, 0, 16, 4, 12, 4, 6, rnd);
            // helmet front details (head front face = 8..16, 8..16)
            switch (s.id) {
                case "pumpkin" -> {
                    layer1.fill(9, 11, 2, 2, rgb(s.accent));
                    layer1.fill(13, 11, 2, 2, rgb(s.accent));
                    layer1.fill(10, 14, 4, 1, rgb(s.accent));
                    layer1.fill(11, 0 + 3, 2, 2, rgb(0x15803d));
                }
                case "mythos" -> {
                    layer1.fill(11, 8, 2, 2, rgb(s.accent));
                    layer1.fill(8, 8, 1, 3, rgb(s.trim));
                    layer1.fill(15, 8, 1, 3, rgb(s.trim));
                    layer1.fill(9, 12, 6, 1, argb(255, 0x1b1022));
                }
                default -> layer1.fill(9, 12, 6, 1, argb(255, 0x0f172a));
            }
            // chest emblem (body front = 20..28, 20..32)
            layer1.fill(23, 22, 2, 3, rgb(s.accent));
            layer1.set(22, 23, rgb(s.accent));
            layer1.set(25, 23, rgb(s.accent));
            layer1.set(23, 23, rgb(0xffffff));
            layer1.save("assets/mythoscraft/textures/entity/equipment/humanoid/" + s.id + ".png");

            Img layer2 = new Img(64, 32);
            armorBox(layer2, s, 16, 16, 8, 12, 4, 7, rnd);
            armorBox(layer2, s, 0, 16, 4, 12, 4, 0, rnd);
            layer2.fill(20, 27, 8, 1, rgb(s.accent));
            layer2.save("assets/mythoscraft/textures/entity/equipment/humanoid_leggings/" + s.id + ".png");

            write("assets/mythoscraft/equipment/" + s.id + ".json", "{\n  \"layers\": {\n    \"humanoid\": [{\"texture\": \"mythoscraft:"
                    + s.id + "\"}],\n    \"humanoid_leggings\": [{\"texture\": \"mythoscraft:" + s.id + "\"}]\n  }\n}\n");

            armorIcon(s, "helmet", new double[]{8, 7, 24, 7, 26, 12, 26, 20, 21, 20, 21, 15, 11, 15, 11, 20, 6, 20, 6, 12});
            armorIcon(s, "chestplate", new double[]{5, 6, 12, 6, 16, 9, 20, 6, 27, 6, 28, 15, 23, 15, 23, 27, 9, 27, 9, 15, 4, 15});
            armorIcon(s, "leggings", new double[]{8, 5, 24, 5, 25, 27, 19, 27, 17, 12, 15, 12, 13, 27, 7, 27});
            armorIcon(s, "boots", new double[]{6, 13, 13, 13, 13, 22, 15, 26, 3, 26, 3, 22, 6, 22, 19, 13, 26, 13, 26, 22, 29, 22, 29, 26, 17, 26, 19, 22});
        }
    }

    static void armorIcon(ArmorSet s, String piece, double[] poly) throws IOException {
        Img img = new Img(32, 32);
        double[] first = poly;
        double[] second = null;
        if (piece.equals("boots")) {
            first = new double[]{6, 13, 13, 13, 13, 22, 15, 26, 3, 26, 3, 22, 6, 22};
            second = new double[]{19, 13, 26, 13, 26, 22, 29, 22, 29, 26, 17, 26, 19, 22};
        }
        double[] a = first;
        double[] b = second;
        Random rnd = new Random(piece.hashCode() + s.id.hashCode());
        shape(img, (x, y) -> inPolygon(x, y, a) || (b != null && inPolygon(x, y, b)),
                (x, y) -> mix(armorPixel(s, x, y, rnd), rgb(0xffffff), Math.max(0, (12 - y) / 40.0)), rgb(0x0b0612));
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 32; y++) {
                if (img.opaque(x, y) && img.get(x, y) != rgb(0x0b0612) && (!img.opaque(x, y - 1) || img.get(x, y - 1) == rgb(0x0b0612))) {
                    img.set(x, y, rgb(s.trim));
                }
            }
        }
        if (piece.equals("chestplate") || piece.equals("helmet")) {
            img.fill(15, piece.equals("helmet") ? 10 : 14, 3, 3, rgb(s.accent));
            img.set(16, piece.equals("helmet") ? 11 : 15, rgb(0xffffff));
        }
        String name = s.id + "_" + piece;
        img.save("assets/mythoscraft/textures/item/" + name + ".png");
        spriteModel(name, "mythoscraft:item/" + name);
    }

    // =====================================================================================================
    // Collection crates: animated texture (8 frames) + themed decorations
    // =====================================================================================================

    static boolean animatedCrate(CrateTheme t) {
        return switch (t.id) {
            case "halloween", "frostfest", "celestial", "inferno", "storm", "dragon", "rang", "ocean", "forest", "sky", "crystal", "millennium", "cosmetic", "begleiter", "space",
                    "spawner", "silvester", "shadow", "relic1", "relic2", "relic3", "relic4" -> true;
            default -> false;
        };
    }

    static void animatedCrateTexture(CrateTheme t) throws IOException {
        int frames = 8;
        Img strip = new Img(128, 128 * frames);
        for (int f = 0; f < frames; f++) {
            Img frame = crateImage(t, f, frames);
            for (int x = 0; x < 128; x++) {
                for (int y = 0; y < 128; y++) {
                    strip.set(x, f * 128 + y, frame.get(x, y));
                }
            }
        }
        String path = "assets/mythoscraft/textures/item/crate_" + t.id;
        strip.save(path + ".png");
        write(path + ".png.mcmeta", "{\"animation\": {\"frametime\": 3, \"interpolate\": true}}\n");
    }

    /** One animation frame: the normal crate texture with pulsing runes and the collection's own painting. */
    static Img crateImage(CrateTheme t, int f, int frames) {
        double pulse = wave(f * Math.PI * 2 / frames);
        CrateTheme glowing = new CrateTheme(t.id, t.plankLight, t.plankDark, t.band, t.bandDark, t.accent,
                mix(rgb(t.emblem), rgb(0xffffff), pulse * 0.35) & 0xFFFFFF, t.runes,
                mix(rgb(t.runeColor), rgb(0xffffff), pulse * 0.6) & 0xFFFFFF);
        Img img = crateBaseImage(glowing);
        Random rnd = new Random(t.id.hashCode() * 31L);
        switch (t.id) {
            case "ocean" -> {
                int[][] regions = {R_SIDE, R_LID_SIDE};
                for (int[] r : regions) {
                    for (int i = 0; i < r[2]; i++) {
                        int crest = r[3] / 2 + (int) Math.round(Math.sin(i * 0.5 + f * Math.PI * 2 / frames) * 2);
                        for (int j = crest; j < r[3]; j++) {
                            img.set(r[0] + i, r[1] + j, mix(img.get(r[0] + i, r[1] + j), j == crest ? rgb(0xe0f2fe) : rgb(0x0891b2), j == crest ? 0.8 : 0.45));
                        }
                    }
                }
            }
            case "forest" -> {
                vines(img, R_SIDE, rgb(0x15803d), rnd);
                vines(img, R_LID_SIDE, rgb(0x166534), rnd);
                for (int i = 0; i < 6; i++) {
                    int x = R_LID_TOP[0] + rnd.nextInt(R_LID_TOP[2]);
                    int y = R_LID_TOP[1] + rnd.nextInt(R_LID_TOP[3]);
                    img.set(x, y, mix(rgb(0xbbf7d0), rgb(0xfde047), wave(f * Math.PI * 2 / frames + i)));
                }
            }
            case "sky" -> {
                int[][] regions = {R_SIDE, R_LID_SIDE};
                for (int[] r : regions) {
                    int shift = f * r[2] / frames;
                    for (int c = 0; c < 2; c++) {
                        int cx = Math.floorMod(c * r[2] / 2 + shift, r[2]);
                        for (int i = -4; i <= 4; i++) {
                            for (int j = 0; j < 3; j++) {
                                if (Math.abs(i) + j < 5) {
                                    int x = r[0] + Math.floorMod(cx + i, r[2]);
                                    int y = r[1] + Math.min(r[3] - 1, 2 + j + c * 3);
                                    img.set(x, y, rgb(0xffffff));
                                }
                            }
                        }
                    }
                }
            }
            case "crystal" -> {
                int[][] regions = {R_SIDE, R_LID_SIDE, R_LID_TOP, R_TOP};
                for (int[] r : regions) {
                    for (int k = 0; k < 5; k++) {
                        int x = r[0] + 1 + rnd.nextInt(Math.max(1, r[2] - 2));
                        int y = r[1] + 1 + rnd.nextInt(Math.max(1, r[3] - 2));
                        double tw = wave(f * Math.PI * 2 / frames + k * 1.7);
                        int c = mix(rgb(0xc026d3), rgb(0xffffff), tw);
                        img.set(x, y, c);
                        if (tw > 0.6) {
                            img.set(x - 1, y, c);
                            img.set(x + 1, y, c);
                            img.set(x, y - 1, c);
                            img.set(x, y + 1, c);
                        }
                    }
                }
            }
            case "halloween" -> {
                int glow = mix(rgb(0xff7a1a), rgb(0xfde047), 0.3 + 0.7 * pulse);
                int[] s = R_SIDE;
                for (int i = 0; i < s[2]; i++) {
                    for (int j = 0; j < s[3]; j++) {
                        boolean leftEye = inPolygon(i + 0.5, j + 0.5, new double[]{3, 8, 6.5, 2, 10, 8});
                        boolean rightEye = inPolygon(i + 0.5, j + 0.5, new double[]{18, 8, 21.5, 2, 25, 8});
                        boolean mouth = j >= 15 && j <= 18 && i >= 4 && i <= 23 && !((i % 5 == 1) && j == 15) && !((i % 5 == 3) && j == 18);
                        if (leftEye || rightEye || mouth) {
                            img.set(s[0] + i, s[1] + j, mix(glow, rgb(0xffffff), (leftEye || rightEye) ? 0.15 : 0));
                        }
                    }
                }
                vines(img, R_LID_SIDE, rgb(0x15803d), rnd);
            }
            case "frostfest" -> {
                int[][] regions = {R_SIDE, R_LID_SIDE};
                for (int[] r : regions) {
                    for (int i = 0; i < r[2]; i++) {
                        int depth = 2 + (int) Math.round(wave(i * 0.9) * 2);
                        for (int j = 0; j < depth; j++) {
                            img.set(r[0] + i, r[1] + j, j == depth - 1 ? rgb(0xdbeafe) : rgb(0xffffff));
                        }
                    }
                    int band = (int) ((f / (double) frames) * (r[2] + r[3]) * 2) - r[3];
                    for (int i = 0; i < r[2]; i++) {
                        for (int j = 0; j < r[3]; j++) {
                            int d = Math.abs(i + j - band);
                            if (d < 3) {
                                img.set(r[0] + i, r[1] + j, mix(img.get(r[0] + i, r[1] + j), rgb(0xffffff), 0.45 - d * 0.12));
                            }
                        }
                    }
                }
                snowflake(img, R_LID_TOP[0] + 15, R_LID_TOP[1] + 15, mix(rgb(0x7dd3fc), rgb(0xffffff), pulse));
            }
            case "celestial" -> {
                int[][] regions = {R_SIDE, R_TOP, R_LID_SIDE, R_LID_TOP};
                for (int[] r : regions) {
                    int count = r[2] * r[3] / 30;
                    for (int k = 0; k < count; k++) {
                        int x = r[0] + 1 + rnd.nextInt(r[2] - 2);
                        int y = r[1] + 1 + rnd.nextInt(r[3] - 2);
                        double twinkle = wave((f + k * 1.7) * Math.PI * 2 / frames);
                        img.set(x, y, mix(rgb(0x6d28d9), rgb(0xffffff), twinkle));
                        if (k % 4 == 0 && twinkle > 0.6) {
                            int c = argb(200, 0xc4b5fd);
                            img.set(x - 1, y, c);
                            img.set(x + 1, y, c);
                            img.set(x, y - 1, c);
                            img.set(x, y + 1, c);
                        }
                    }
                }
                star(img, R_LID_TOP[0] + 15, R_LID_TOP[1] + 15, 6, mix(rgb(0x67e8f9), rgb(0xffffff), pulse));
            }
            case "inferno" -> {
                int[][] regions = {R_SIDE, R_TOP, R_LID_SIDE, R_LID_TOP};
                for (int[] r : regions) {
                    for (int i = 0; i < r[2]; i++) {
                        for (int j = 0; j < r[3]; j++) {
                            int x = r[0] + i;
                            int y = r[1] + j;
                            double crack = Math.sin(x * 0.55 + Math.sin(y * 0.4) * 2.4) + Math.sin(y * 0.47 + x * 0.15);
                            if (Math.abs(crack) < 0.2) {
                                double heat = wave(f * Math.PI * 2 / frames + (x + y) * 0.15);
                                img.set(x, y, mix(rgb(0xff4d1a), rgb(0xffe08a), heat));
                            }
                        }
                    }
                }
            }
            case "storm" -> {
                boolean flash = f == 2 || f == 5;
                int[][] regions = {R_SIDE, R_LID_SIDE, R_LID_TOP, R_TOP};
                for (int[] r : regions) {
                    int x0 = r[0] + 4 + rnd.nextInt(Math.max(1, r[2] - 10));
                    int y = r[1];
                    int x = x0;
                    while (y < r[1] + r[3] - 1) {
                        img.set(x, y, flash ? rgb(0xffffff) : rgb(0xfde047));
                        y++;
                        x += rnd.nextInt(3) - 1;
                        x = Math.max(r[0], Math.min(r[0] + r[2] - 1, x));
                    }
                    if (flash) {
                        for (int i = 0; i < r[2]; i++) {
                            for (int j = 0; j < r[3]; j++) {
                                img.set(r[0] + i, r[1] + j, mix(img.get(r[0] + i, r[1] + j), rgb(0xe0f2fe), 0.25));
                            }
                        }
                    }
                }
            }
            case "dragon" -> {
                int[][] regions = {R_SIDE, R_TOP, R_LID_SIDE, R_LID_TOP};
                for (int[] r : regions) {
                    for (int i = 0; i < r[2]; i++) {
                        for (int j = 0; j < r[3]; j++) {
                            int x = r[0] + i;
                            int y = r[1] + j;
                            if ((x + (y / 3) % 2 * 2) % 4 == 0 && y % 3 == 0) {
                                img.set(x, y, shade(img.get(x, y), 0.6));
                            }
                            double flame = wave(f * Math.PI * 2 / frames + x * 0.4 + y * 0.2);
                            if (j > r[3] - 4 && flame > 0.55) {
                                img.set(x, y, mix(rgb(0xa21caf), rgb(0xf472b6), flame));
                            }
                        }
                    }
                }
            }
            case "rang" -> {
                int[][] regions = {R_SIDE, R_LID_SIDE, R_LID_TOP};
                for (int[] r : regions) {
                    int band = (int) ((f / (double) frames) * (r[2] + r[3]) * 2) - r[3];
                    for (int i = 0; i < r[2]; i++) {
                        for (int j = 0; j < r[3]; j++) {
                            int d = Math.abs(i + j - band);
                            if (d < 2) {
                                img.set(r[0] + i, r[1] + j, mix(img.get(r[0] + i, r[1] + j), rgb(0xfef3c7), 0.6 - d * 0.25));
                            }
                        }
                    }
                }
                star(img, R_LID_TOP[0] + 15, R_LID_TOP[1] + 15, 6, mix(rgb(0xf59e0b), rgb(0xffffff), pulse));
            }
            case "farm" -> {
                int[] r = R_LID_TOP;
                for (int i = 2; i < r[2] - 2; i += 3) {
                    for (int j = 2; j < r[3] - 2; j++) {
                        if ((j + i) % 5 != 0) {
                            img.set(r[0] + i, r[1] + j, rgb(0xeab308));
                        }
                    }
                }
            }
            default -> {
            }
        }
        return img;
    }

    static void vines(Img img, int[] r, int color, Random rnd) {
        for (int i = 0; i < r[2]; i++) {
            int y = r[1] + r[3] / 2 + (int) Math.round(Math.sin(i * 0.7) * 2);
            img.set(r[0] + i, y, color);
            if (rnd.nextInt(5) == 0) {
                img.set(r[0] + i, y - 1, rgb(0x22c55e));
            }
        }
    }

    static void snowflake(Img img, int cx, int cy, int color) {
        for (int i = -7; i <= 7; i++) {
            img.set(cx + i, cy, color);
            img.set(cx, cy + i, color);
            if (Math.abs(i) <= 5) {
                img.set(cx + i, cy + i, color);
                img.set(cx + i, cy - i, color);
            }
        }
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int ex = cx + d[0] * 5;
            int ey = cy + d[1] * 5;
            img.set(ex + d[1], ey + d[0], color);
            img.set(ex - d[1], ey - d[0], color);
        }
    }

    /** Extra 3D parts on the lid of the collection crates (texture "#x" = material atlas). */
    static List<Element> crateDecorations(CrateTheme t) {
        List<Element> list = new ArrayList<>();
        switch (t.id) {
            case "halloween" -> {
                list.add(box(7.2, 14.6, 7.2, 8.8, 17.5, 8.8, "leaf").tex("#x"));
                list.add(box(8.8, 16.6, 7.6, 10.6, 17.4, 8.4, "leaf").tex("#x"));
                list.add(box(5.2, 14.6, 9.2, 7, 15.2, 11.4, "leaf").tex("#x"));
                list.add(box(1.5, 7.5, 0.2, 2.5, 10, 0.5, "pumpkin").tex("#x").glow());
                list.add(box(13.5, 7.5, 15.5, 14.5, 10, 15.8, "pumpkin").tex("#x").glow());
            }
            case "frostfest" -> {
                list.add(box(1, 14, 1, 15, 15, 15, "snow").tex("#x"));
                list.add(box(3, 15, 3, 13, 15.6, 13, "snow").tex("#x"));
                double[] xs = {2.2, 5.1, 8.3, 11.4, 13.6};
                for (int i = 0; i < xs.length; i++) {
                    double len = 1.4 + (i % 3) * 0.7;
                    list.add(box(xs[i], 10 - len, 0.3, xs[i] + 0.7, 10, 1.0, "ice").tex("#x").glow());
                    list.add(box(xs[i], 10 - len * 0.8, 15, xs[i] + 0.7, 10, 15.7, "ice").tex("#x").glow());
                    list.add(box(0.3, 10 - len * 0.9, xs[i], 1.0, 10, xs[i] + 0.7, "ice").tex("#x").glow());
                    list.add(box(15, 10 - len, xs[i], 15.7, 10, xs[i] + 0.7, "ice").tex("#x").glow());
                }
            }
            case "celestial" -> {
                list.add(box(1.5, 17.5, 1.5, 14.5, 18.2, 2.3, "crystal").tex("#x").rotate("y", 45, 8, 8, 8).glow());
                list.add(box(1.5, 17.5, 13.7, 14.5, 18.2, 14.5, "crystal").tex("#x").rotate("y", 45, 8, 8, 8).glow());
                list.add(box(1.5, 17.5, 2.3, 2.3, 18.2, 13.7, "crystal").tex("#x").rotate("y", 45, 8, 8, 8).glow());
                list.add(box(13.7, 17.5, 2.3, 14.5, 18.2, 13.7, "crystal").tex("#x").rotate("y", 45, 8, 8, 8).glow());
                list.add(box(6.8, 17, 6.8, 9.2, 19.4, 9.2, "night").tex("#x").rotate("y", 45, 8, 8, 8).glow());
            }
            case "farm" -> {
                list.add(box(4, 14, 4, 12, 16.5, 12, "straw").tex("#x"));
                list.add(box(5, 16.5, 5, 11, 17.5, 11, "straw").tex("#x"));
                list.add(box(7.5, 17.5, 7.5, 8.5, 20, 8.5, "leaf").tex("#x"));
                list.add(box(6.5, 19, 7.6, 9.5, 20.2, 8.4, "pumpkin").tex("#x"));
            }
            case "storm" -> {
                list.add(box(7.4, 14, 7.4, 8.6, 21, 8.6, "silver").tex("#x"));
                list.add(box(6.8, 19.5, 7.6, 9.2, 20.2, 8.4, "silver").tex("#x"));
                list.add(box(7.6, 21, 7.6, 8.4, 22.4, 8.4, "candy").tex("#x").glow());
                list.add(box(2, 14, 2, 4, 14.6, 4, "ice").tex("#x").glow());
                list.add(box(12, 14, 12, 14, 14.6, 14, "ice").tex("#x").glow());
            }
            case "dragon" -> {
                list.add(box(1, 13.5, 1, 3, 17, 3, "night").tex("#x").rotate("z", 22.5, 2, 14, 2));
                list.add(box(1.4, 17, 1.4, 2.6, 19.5, 2.6, "ruby").tex("#x").rotate("z", 22.5, 2, 14, 2).glow());
                list.add(box(13, 13.5, 1, 15, 17, 3, "night").tex("#x").rotate("z", -22.5, 14, 14, 2));
                list.add(box(13.4, 17, 1.4, 14.6, 19.5, 2.6, "ruby").tex("#x").rotate("z", -22.5, 14, 14, 2).glow());
                list.add(box(-1.5, 9, 6, 0.5, 13.5, 10, "night").tex("#x").rotate("z", 22.5, 0.5, 9, 8));
                list.add(box(15.5, 9, 6, 17.5, 13.5, 10, "night").tex("#x").rotate("z", -22.5, 15.5, 9, 8));
                list.add(box(6.6, 14.6, 6.6, 9.4, 16.2, 9.4, "amethyst").tex("#x").rotate("y", 45, 8, 8, 8).glow());
            }
            case "rang" -> {
                list.add(box(4, 14, 4, 12, 15, 12, "gold").tex("#x"));
                double[][] spikes = {{4, 4}, {7.4, 4}, {11, 4}, {4, 11}, {7.4, 11}, {11, 11}, {4, 7.4}, {11, 7.4}};
                for (double[] sp : spikes) {
                    list.add(box(sp[0], 15, sp[1], sp[0] + 1, 17, sp[1] + 1, "gold").tex("#x"));
                }
                list.add(box(7.2, 15, 7.2, 8.8, 16.6, 8.8, "ruby").tex("#x").glow());
            }
            case "ocean" -> {
                list.add(box(5, 14, 5, 11, 15.2, 11, "gold").tex("#x"));
                list.add(box(6.4, 15.2, 6.4, 9.6, 17.6, 9.6, "snow").tex("#x"));
                list.add(box(7.2, 17.6, 7.2, 8.8, 18.6, 8.8, "crystal").tex("#x").glow());
                list.add(box(1.5, 14, 1.5, 3, 16.5, 3, "candy").tex("#x"));
                list.add(box(13, 14, 13, 14.5, 16, 14.5, "candy").tex("#x"));
            }
            case "forest" -> {
                list.add(box(7.3, 14, 7.3, 8.7, 18, 8.7, "wood").tex("#x"));
                list.add(box(5, 17.5, 5, 11, 21, 11, "leaf").tex("#x"));
                list.add(box(6, 21, 6, 10, 22.5, 10, "leaf").tex("#x"));
                list.add(box(1, 14, 12, 3, 15.5, 14, "leaf").tex("#x"));
                list.add(box(13, 14, 1.5, 14.5, 15.5, 3, "emerald").tex("#x").glow());
            }
            case "sky" -> {
                list.add(box(3, 14, 6, 13, 16, 10, "snow").tex("#x"));
                list.add(box(5, 16, 5, 11, 17.6, 11, "snow").tex("#x"));
                list.add(box(7.2, 17.6, 7.2, 8.8, 19.6, 8.8, "gold").tex("#x").rotate("y", 45, 8, 8, 8).glow());
            }
            case "crystal" -> {
                list.add(box(6.6, 14, 6.6, 9.4, 19.5, 9.4, "amethyst").tex("#x").rotate("y", 45, 8, 8, 8).glow());
                list.add(box(2, 14, 2, 4, 17, 4, "amethyst").tex("#x").rotate("z", 22.5, 3, 14, 3).glow());
                list.add(box(12, 14, 12, 14, 16.5, 14, "amethyst").tex("#x").rotate("z", -22.5, 13, 14, 13).glow());
                list.add(box(12, 14, 2, 13.5, 15.8, 3.5, "crystal").tex("#x").glow());
            }
            case "inferno" -> {
                list.add(box(1, 13.5, 1, 3, 17, 3, "bone").tex("#x").rotate("z", 22.5, 2, 14, 2));
                list.add(box(1.4, 17, 1.4, 2.6, 19.5, 2.6, "bone").tex("#x").rotate("z", 22.5, 2, 14, 2));
                list.add(box(13, 13.5, 1, 15, 17, 3, "bone").tex("#x").rotate("z", -22.5, 14, 14, 2));
                list.add(box(13.4, 17, 1.4, 14.6, 19.5, 2.6, "bone").tex("#x").rotate("z", -22.5, 14, 14, 2));
                list.add(box(6.5, 14.6, 6.5, 9.5, 15.4, 9.5, "lava_glow").tex("#x").glow());
            }
            default -> {
            }
        }
        return list;
    }

    // =====================================================================================================
    // Third wave: farm / storm / dragon / spring items, rank voucher, broom ride model
    // =====================================================================================================

    static void flask(Img img, int liquid, int liquidDark, int cork) {
        shape(img, (x, y) -> Math.hypot(x - 16, y - 21) <= 8.5 || (x >= 13 && x <= 19 && y >= 7 && y <= 14),
                (x, y) -> y < 14 ? rgb(0xe2e8f0) : mix(rgb(liquid), rgb(liquidDark), Math.hypot(x - 14, y - 19) / 10), INK);
        img.fill(12, 4, 8, 3, rgb(cork));
        img.set(12, 18, rgb(0xffffff));
        img.set(13, 17, argb(200, 0xffffff));
    }

    static void pouch(Img img, int cloth, int dark, int tie) {
        shape(img, (x, y) -> (Math.pow((x - 16) / 11.0, 2) + Math.pow((y - 20) / 9.0, 2) <= 1)
                        || inPolygon(x, y, new double[]{11, 13, 21, 13, 23, 7, 9, 7}),
                (x, y) -> mix(rgb(cloth), rgb(dark), (x + y - 10) / 40.0 + (y < 13 ? -0.15 : 0)), INK);
        img.fill(10, 12, 12, 2, rgb(tie));
        img.set(13, 15, argb(160, 0xffffff));
    }

    static void charm(Img img, int gem, int gemDark) {
        for (int i = 0; i <= 20; i++) {
            double a = Math.PI * i / 20.0;
            img.set((int) Math.round(16 - Math.cos(a) * 9), (int) Math.round(12 - Math.sin(a) * 9), i % 2 == 0 ? rgb(0xd4a017) : rgb(0x92610f));
        }
        shape(img, (x, y) -> Math.abs(x - 16) / 7.0 + Math.abs(y - 20) / 9.0 <= 1,
                (x, y) -> mix(rgb(gem), rgb(gemDark), Math.hypot(x - 14, y - 17) / 10), INK);
        img.fill(14, 10, 4, 3, rgb(0xd4a017));
        img.set(14, 17, rgb(0xffffff));
        img.set(15, 16, argb(200, 0xffffff));
    }

    static void bolt(Img img, int ox, int oy, int color) {
        double[] poly = {ox + 3, oy, ox - 1, oy + 7, ox + 2, oy + 7, ox - 1, oy + 13, ox + 6, oy + 5, ox + 3, oy + 5, ox + 6, oy};
        shape(img, (x, y) -> inPolygon(x, y, poly), (x, y) -> color, 0);
    }

    static void waveThreeSprites() throws IOException {
        sprite("watering_can", img -> {
            shape(img, (x, y) -> x >= 8 && x <= 22 && y >= 12 && y <= 27, (x, y) -> mix(rgb(0x5eead4), rgb(0x0f766e), (x - 8) / 16.0), INK);
            shape(img, (x, y) -> segDist(x, y, 22, 17, 29, 9) < 1.6, (x, y) -> rgb(0x0d9488), 0);
            shape(img, (x, y) -> Math.abs(Math.hypot(x - 15, y - 12) - 6) < 1.2 && y < 12, (x, y) -> rgb(0x115e59), 0);
            img.fill(29, 7, 2, 3, rgb(0x99f6e4));
            img.set(30, 12, rgb(0x60a5fa));
            img.set(29, 15, rgb(0x60a5fa));
            img.fill(10, 15, 10, 2, rgb(0x99f6e4));
            img.outline(INK);
        });
        sprite("seed_bag", img -> {
            pouch(img, 0xa16207, 0x713f12, 0x65a30d);
            for (int[] s : new int[][]{{13, 19}, {17, 21}, {19, 18}, {15, 24}, {20, 23}}) {
                img.set(s[0], s[1], rgb(0xfef08a));
                img.set(s[0] + 1, s[1], rgb(0xca8a04));
            }
        });
        sprite("compost_charm", img -> {
            charm(img, 0x84cc16, 0x3f6212);
            img.set(16, 20, rgb(0x14532d));
            img.set(16, 21, rgb(0x14532d));
            img.set(15, 19, rgb(0x166534));
            img.set(17, 22, rgb(0x166534));
        });
        sprite("honey_bread", img -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 12.0, 2) + Math.pow((y - 18) / 7.0, 2) <= 1,
                    (x, y) -> y < 15 ? mix(rgb(0xd97706), rgb(0x92400e), (x % 6) / 8.0) : rgb(0xb45309), INK);
            for (int x = 8; x < 25; x += 5) {
                img.fill(x, 13, 2, 1, rgb(0xfde68a));
            }
            img.fill(10, 11, 12, 2, rgb(0xfbbf24));
            img.fill(13, 13, 2, 4, rgb(0xfbbf24));
            img.fill(19, 13, 2, 3, rgb(0xfbbf24));
        });
        sprite("fortune_cookie", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 18) <= 10 && Math.hypot(x - 16, y - 10) > 7,
                    (x, y) -> mix(rgb(0xfde68a), rgb(0xd97706), Math.hypot(x - 12, y - 15) / 14), rgb(0x78350f));
            img.fill(17, 9, 7, 3, rgb(0xfafafa));
            img.fill(18, 10, 4, 1, rgb(0xef4444));
        });
        sprite("static_charm", img -> {
            charm(img, 0x38bdf8, 0x1e3a8a);
            bolt(img, 14, 14, rgb(0xfde047));
        });
        sprite("storm_flask", img -> {
            flask(img, 0x7dd3fc, 0x4338ca, 0x64748b);
            bolt(img, 14, 15, rgb(0xfde047));
        });
        sprite("dragon_scale", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 3, 27, 10, 25, 22, 16, 29, 7, 22, 5, 10}),
                    (x, y) -> mix(rgb(0xc026d3), rgb(0x3b0764), (y - 3) / 26.0), INK);
            for (int y = 8; y < 27; y += 5) {
                for (int x = 9; x < 24; x++) {
                    if (img.get(x, y) != INK && img.opaque(x, y) && (x + y) % 3 == 0) {
                        img.set(x, y, rgb(0x1e0b2e));
                    }
                }
            }
            img.set(12, 9, rgb(0xf5d0fe));
        });
        animatedSprite("dragon_heart", 8, 2, (img, f, n) -> {
            double beat = f == 1 || f == 3 ? 1.12 : 1.0;
            shape(img, (x, y) -> {
                double dx = (x - 16) / (7.0 * beat);
                double dy = (y - 15) / (7.0 * beat);
                return Math.pow(dx * dx + dy * dy - 1, 3) - dx * dx * Math.pow(-dy, 3) <= 0;
            }, (x, y) -> mix(rgb(0xdc2626), rgb(0x581c87), Math.hypot(x - 13, y - 12) / 12), INK);
            img.set(12, 11, rgb(0xfecaca));
            img.set(13, 11, argb(180, 0xfecaca));
        });
        sprite("ender_pouch", img -> {
            pouch(img, 0x115e59, 0x042f2e, 0x14b8a6);
            shape(img, (x, y) -> Math.hypot(x - 16, y - 20) <= 3.6, (x, y) -> Math.hypot(x - 16, y - 20) < 1.5 ? rgb(0x064e3b)
                    : rgb(0x34d399), 0);
        });
        sprite("jump_charm", img -> {
            charm(img, 0x86efac, 0x15803d);
            img.fill(15, 16, 2, 8, rgb(0xffffff));
            img.set(14, 17, rgb(0xffffff));
            img.set(17, 17, rgb(0xffffff));
            img.set(13, 18, rgb(0xffffff));
            img.set(18, 18, rgb(0xffffff));
        });
        sprite("aqua_charm", img -> {
            charm(img, 0x22d3ee, 0x1d4ed8);
            for (int x = 11; x <= 21; x++) {
                img.set(x, 20 + (int) Math.round(Math.sin(x * 0.9) * 1.2), rgb(0xe0f2fe));
            }
        });
        sprite("coin_pouch", img -> {
            pouch(img, 0x92400e, 0x451a03, 0xfbbf24);
            shape(img, (x, y) -> Math.hypot(x - 16, y - 20) <= 4, (x, y) -> mix(rgb(0xfde047), rgb(0xd97706), (y - 16) / 8.0),
                    0);
            img.fill(15, 18, 2, 4, rgb(0x92400e));
        });
        sprite("gem_pouch", img -> {
            pouch(img, 0x581c87, 0x2e1065, 0xe879f9);
            shape(img, (x, y) -> Math.abs(x - 16) + Math.abs(y - 20) <= 4, (x, y) -> mix(rgb(0xf5d0fe), rgb(0xa21caf), (y - 16) / 8.0), 0);
        });
        sprite("key_fragment", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{6, 22, 14, 14, 18, 16, 22, 10, 27, 13, 20, 24, 15, 21, 10, 27}),
                    (x, y) -> mix(rgb(0xfde68a), rgb(0xa855f7), (x - 6) / 22.0), INK);
            img.set(20, 14, rgb(0xffffff));
            img.set(11, 22, rgb(0xfef3c7));
        });
        sprite("swift_rune", img -> {
            shape(img, (x, y) -> x >= 8 && x <= 24 && y >= 4 && y <= 28 && !(x + y < 14 || x - y > 18),
                    (x, y) -> mix(rgb(0xcbd5e1), rgb(0x64748b), y / 30.0), INK);
            for (int i = 0; i < 3; i++) {
                for (int x = 11; x <= 21; x++) {
                    img.set(x, 10 + i * 5 + (int) Math.round(Math.sin(x * 0.7 + i) * 1.3), rgb(0xe0f2fe));
                }
            }
        });
        sprite("rtp_scroll", img -> {
            shape(img, (x, y) -> x >= 7 && x <= 25 && y >= 8 && y <= 24, (x, y) -> mix(rgb(0xfef3c7), rgb(0xd6b98c), (x + y) / 50.0), INK);
            shape(img, (x, y) -> (Math.hypot(x - 7, y - 16) <= 3 || Math.hypot(x - 25, y - 16) <= 3) && y >= 7 && y <= 25,
                    (x, y) -> rgb(0xa16207), INK);
            shape(img, (x, y) -> Math.abs(Math.hypot(x - 16, y - 16) - 4) < 1.2, (x, y) -> rgb(0x7c3aed), 0);
            img.set(16, 16, rgb(0x22d3ee));
        });
        animatedSprite("mythos_elixir", 8, 3, (img, f, n) -> {
            flask(img, 0xc084fc, 0x6b21a8, 0xd4a017);
            for (int i = 0; i < 6; i++) {
                double a = f * Math.PI / 4 + i * Math.PI / 3;
                img.set((int) Math.round(16 + Math.cos(a) * 4), (int) Math.round(22 + Math.sin(a) * 3), rgb(0xfde68a));
            }
        });
        sprite("mob_net", img -> {
            for (int i = 0; i < 12; i++) {
                img.set(4 + i, 27 - i, rgb(0x92400e));
                img.set(5 + i, 27 - i, rgb(0x78350f));
            }
            shape(img, (x, y) -> Math.hypot(x - 20, y - 11) <= 8.5, (x, y) -> Math.abs(Math.hypot(x - 20, y - 11) - 8) < 1
                    ? rgb(0x94a3b8) : (x + y) % 3 == 0 || (x - y + 30) % 3 == 0 ? argb(220, 0xe5e7eb) : 0, 0);
            img.outline(INK);
        });
        sprite("boomerang", img -> {
            shape(img, (x, y) -> segDist(x, y, 5, 24, 16, 8) < 3.2 || segDist(x, y, 16, 8, 27, 24) < 3.2,
                    (x, y) -> mix(rgb(0xfbbf24), rgb(0xc2410c), y / 28.0), INK);
            img.fill(14, 9, 4, 3, rgb(0x7c2d12));
            img.set(8, 21, rgb(0x38bdf8));
            img.set(24, 21, rgb(0x38bdf8));
        });
        sprite("healing_flask", img -> {
            flask(img, 0xf87171, 0x991b1b, 0x78350f);
            img.fill(15, 18, 3, 7, rgb(0xffffff));
            img.fill(13, 20, 7, 3, rgb(0xffffff));
        });
        sprite("wisdom_charm", img -> {
            charm(img, 0xbef264, 0x3f6212);
            shape(img, (x, y) -> Math.pow((x - 16) / 4.0, 2) + Math.pow((y - 20) / 2.2, 2) <= 1, (x, y) -> rgb(0xffffff), 0);
            img.fill(15, 19, 2, 2, rgb(0x14532d));
        });
        sprite("treasure_map", img -> {
            shape(img, (x, y) -> x >= 5 && x <= 27 && y >= 6 && y <= 26 && !((x + y) % 11 == 0 && (x == 5 || x == 27)),
                    (x, y) -> mix(rgb(0xfde68a), rgb(0xb45309), Math.hypot(x - 10, y - 10) / 30), rgb(0x78350f));
            for (int i = 0; i < 14; i++) {
                if (i % 3 != 2) {
                    img.set(8 + i, 22 - (int) Math.round(Math.sin(i * 0.5) * 4) - i / 2, rgb(0x92400e));
                }
            }
            shape(img, (x, y) -> segDist(x, y, 20, 9, 25, 14) < 1 || segDist(x, y, 25, 9, 20, 14) < 1, (x, y) -> rgb(0xdc2626), 0);
        });
        sprite("frost_bomb", img -> {
            shape(img, (x, y) -> Math.hypot(x - 15, y - 19) <= 9.5, (x, y) -> mix(rgb(0xe0f2fe), rgb(0x0284c7),
                    Math.hypot(x - 12, y - 16) / 13), INK);
            for (int i = 0; i < 5; i++) {
                img.set(19 + i, 9 - i / 2, rgb(0x94a3b8));
            }
            img.set(24, 6, rgb(0xffffff));
            img.set(11, 15, rgb(0xffffff));
            img.fill(12, 21, 6, 1, rgb(0xbae6fd));
        });
        sprite("smoke_bomb", img -> {
            shape(img, (x, y) -> Math.hypot(x - 15, y - 20) <= 9, (x, y) -> mix(rgb(0x6b7280), rgb(0x111827),
                    Math.hypot(x - 12, y - 17) / 13), INK);
            for (int i = 0; i < 5; i++) {
                img.set(18 + i, 11 - i / 2, rgb(0xa8a29e));
            }
            shape(img, (x, y) -> Math.hypot(x - 24, y - 6) <= 2.5 || Math.hypot(x - 27, y - 4) <= 2, (x, y) -> argb(170, 0xd1d5db), 0);
        });
        animatedSprite("easter_egg", 8, 3, (img, f, n) -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 9.0, 2) + Math.pow((y - 17) / (y < 17 ? 12.0 : 10.0), 2) <= 1, (x, y) -> {
                int band = ((y + f) / 4) % 3;
                return band == 0 ? rgb(0xf9a8d4) : band == 1 ? rgb(0xa5f3fc) : rgb(0xfde68a);
            }, rgb(0x6b21a8));
            img.set(12, 9, rgb(0xffffff));
        });
        sprite("rank_voucher", img -> {
            shape(img, (x, y) -> x >= 3 && x <= 28 && y >= 9 && y <= 23 && !(Math.hypot(x - 3, y - 16) < 3 || Math.hypot(x - 28, y - 16) < 3),
                    (x, y) -> mix(rgb(0xfde68a), rgb(0xd97706), (x + y) / 50.0), rgb(0x78350f));
            star(img, 16, 16, 5, rgb(0x7c3aed));
            img.fill(6, 11, 1, 11, rgb(0xfef3c7));
            img.fill(25, 11, 1, 11, rgb(0xfef3c7));
        });
        sprite("lucky_rod", img -> drawRod(img, true));
        sprite("lucky_rod_cast", img -> drawRod(img, false));
        for (String rod : new String[]{"lucky_rod", "lucky_rod_cast"}) {
            write("assets/mythoscraft/models/item/" + rod + ".json",
                    "{\n  \"parent\": \"minecraft:item/handheld_rod\",\n  \"textures\": {\"layer0\": \"mythoscraft:item/" + rod + "\"}\n}\n");
        }
        bow("storm_bow", 0x7dd3fc, 0x1e3a8a, 0xfde047);
        bow("ender_bow", 0x2dd4bf, 0x581c87, 0xc4b5fd);
    }

    /** Fishing rod like the vanilla sprite: handle bottom-left, tip top-right, line hanging from the tip. */
    static void drawRod(Img img, boolean idle) {
        for (int i = 0; i < 24; i++) {
            img.set(4 + i, 27 - i, i < 8 ? rgb(0x78350f) : rgb(0xd4a017));
            img.set(5 + i, 27 - i, i < 8 ? rgb(0x92400e) : rgb(0xb45309));
        }
        img.fill(5, 24, 3, 3, rgb(0x334155));
        if (idle) {
            for (int y = 4; y < 24; y++) {
                img.set(28, y, argb(200, 0xe5e7eb));
            }
            shape(img, (x, y) -> Math.hypot(x - 28, y - 25) <= 2.2, (x, y) -> y < 25 ? rgb(0xef4444) : rgb(0xffffff), 0);
        }
        img.outline(INK);
    }

    static void waveThreeModels() throws IOException {
        Map<String, String> tex = Map.of("m", "mythoscraft:item/materials");

        List<Element> sickle = new ArrayList<>();
        sickle.add(rod(1.1, -3, -2, "gold"));
        sickle.add(rod(0.65, -2, 9, "wood"));
        sickle.add(rod(0.9, 8, 9.5, "brass"));
        sickle.add(box(7.6, 9.5, 7.6, 8.4, 14, 8.4, "silver"));
        sickle.add(box(8.4, 13, 7.65, 12.5, 14.4, 8.35, "silver"));
        sickle.add(box(11.5, 10.5, 7.65, 13, 13.5, 8.35, "silver"));
        sickle.add(box(10.5, 9, 7.7, 12, 10.8, 8.3, "silver"));
        sickle.add(box(8.6, 13.6, 7.5, 12.2, 14.6, 8.5, "leaf").glow());
        model("harvest_sickle", tex, diagonal(sickle), HANDHELD_DISPLAY, "front");

        List<Element> hammer = new ArrayList<>();
        hammer.add(rod(1.2, -3, -2, "silver"));
        hammer.add(rod(0.8, -2, 3, "leather"));
        hammer.add(rod(0.75, 3, 12.5, "netherite"));
        hammer.add(box(3, 12.5, 5, 13, 18, 11, "steel"));
        hammer.add(box(2.4, 13, 5.5, 3, 17.5, 10.5, "silver"));
        hammer.add(box(13, 13, 5.5, 13.6, 17.5, 10.5, "silver"));
        hammer.add(box(6.5, 13.8, 4.6, 9.5, 16.8, 5, null).anim("f").glow());
        hammer.add(box(6.5, 13.8, 11, 9.5, 16.8, 11.4, null).anim("f").glow());
        model("thunder_hammer", withAnim(tex, "f", "anim_frost"), diagonal(hammer), HANDHELD_DISPLAY, "front");

        List<Element> wind = new ArrayList<>();
        wind.add(rod(1.2, -3, -1.8, "ice"));
        wind.add(rod(0.65, -1.8, 4, "silver"));
        wind.add(box(4.4, 4, 7.3, 11.6, 5, 8.7, "silver"));
        wind.add(box(4.4, 5, 7.6, 5.4, 6.6, 8.4, "ice"));
        wind.add(box(10.6, 5, 7.6, 11.6, 6.6, 8.4, "ice"));
        wind.add(box(7, 5, 7.62, 9, 16.5, 8.38, "snow"));
        wind.add(box(7.6, 5.5, 7.5, 8.4, 16, 8.5, null).anim("f").glow());
        wind.add(box(7.4, 16.5, 7.7, 8.6, 18.6, 8.3, "snow"));
        model("wind_blade", withAnim(tex, "f", "anim_frost"), diagonal(wind), HANDHELD_DISPLAY, "front");

        List<Element> dblade = new ArrayList<>();
        dblade.add(rod(1.3, -3, -1.8, "ruby"));
        dblade.add(rod(0.65, -1.8, 4, "night"));
        dblade.add(box(3.6, 4, 7.1, 12.4, 5.4, 8.9, "obsidian"));
        dblade.add(box(2.8, 5.4, 7.4, 4.2, 7, 8.6, "obsidian"));
        dblade.add(box(11.8, 5.4, 7.4, 13.2, 7, 8.6, "obsidian"));
        dblade.add(box(6.6, 5.4, 7.6, 9.4, 17.4, 8.4, "obsidian"));
        dblade.add(box(7.5, 6, 7.48, 8.5, 16.8, 8.52, null).anim("l").glow());
        dblade.add(box(7.3, 17.4, 7.7, 8.7, 19.2, 8.3, "ruby").glow());
        model("dragon_blade", withAnim(tex, "l", "anim_lava"), diagonal(dblade), HANDHELD_DISPLAY, "front");

        List<Element> dstaff = new ArrayList<>();
        dstaff.add(rod(0.6, -3, 12.5, "obsidian"));
        dstaff.add(rod(0.9, 2, 2.6, "ruby"));
        dstaff.add(rod(1.5, 12.2, 13.2, "bone"));
        dstaff.add(box(6.6, 13.2, 6.6, 9.4, 16, 9.4, null).anim("s").glow());
        dstaff.add(box(5.6, 15, 7.5, 6.6, 18, 8.5, "bone").rotate("z", 22.5, 6, 15, 8));
        dstaff.add(box(9.4, 15, 7.5, 10.4, 18, 8.5, "bone").rotate("z", -22.5, 10, 15, 8));
        model("dragon_staff", withAnim(tex, "s", "anim_star"), diagonal(dstaff), HANDHELD_DISPLAY, "front");

        pickaxe("smelter_pickaxe", "netherite", "lava_rock", "lava_glow", "l", "anim_lava");
        pickaxe("ore_pickaxe", "steel", "diamond", "crystal", "f", "anim_frost");

        List<Element> wand = new ArrayList<>();
        wand.add(rod(0.55, -2.5, 12.6, "wood"));
        wand.add(rod(0.85, 3, 3.6, "gold"));
        wand.add(box(6.2, 12.6, 6.2, 9.8, 16.2, 9.8, "gold"));
        wand.add(box(5.8, 13, 5.8, 10.2, 13.4, 10.2, "brass"));
        wand.add(box(5.8, 15.4, 5.8, 10.2, 15.8, 10.2, "brass"));
        model("builder_wand", tex, diagonal(wand), HANDHELD_DISPLAY, "front");

        List<Element> carrot = new ArrayList<>();
        carrot.add(rod(1.1, -3, -1.8, "leaf"));
        carrot.add(rod(0.6, -1.8, 4, "wood"));
        carrot.add(box(4.8, 4, 7.3, 11.2, 5.2, 8.7, "leaf"));
        carrot.add(box(6.6, 5.2, 7.5, 9.4, 10, 8.5, "pumpkin"));
        carrot.add(box(7, 10, 7.55, 9, 15, 8.45, "pumpkin"));
        carrot.add(box(7.5, 15, 7.6, 8.5, 17.5, 8.4, "pumpkin"));
        model("carrot_blade", tex, diagonal(carrot), HANDHELD_DISPLAY, "front");

        List<Element> blossom = new ArrayList<>();
        blossom.add(rod(0.55, -2.5, 13, "wood"));
        blossom.add(rod(0.8, 4, 4.6, "leaf"));
        blossom.add(box(7.2, 13, 7.2, 8.8, 14.6, 8.8, "straw").glow());
        blossom.add(box(5, 13.2, 7.4, 7.2, 14.4, 8.6, "candy"));
        blossom.add(box(8.8, 13.2, 7.4, 11, 14.4, 8.6, "candy"));
        blossom.add(box(7.4, 14.6, 7.4, 8.6, 16.8, 8.6, "candy"));
        blossom.add(box(7.4, 13.2, 5, 8.6, 14.4, 7.2, "candy"));
        blossom.add(box(7.4, 13.2, 8.8, 8.6, 14.4, 11, "candy"));
        model("blossom_wand", tex, diagonal(blossom), HANDHELD_DISPLAY, "front");

        // broom to sit on (item display while riding): lies along +Z, bristles at the back
        List<Element> ride = new ArrayList<>();
        ride.add(box(7.4, 7.4, -6, 8.6, 8.6, 18, "wood"));
        ride.add(box(7.1, 7.1, -7, 8.9, 8.9, -6, "gold"));
        ride.add(box(6.6, 6.6, 18, 9.4, 9.4, 19, "gold"));
        ride.add(box(5.6, 5.6, 19, 10.4, 10.4, 24, "straw"));
        ride.add(box(5, 5, 23, 11, 11, 26, "straw"));
        ride.add(box(7.2, 9, 3, 8.8, 9.6, 6, "pumpkin").glow());
        model("witch_broom_ride", tex, ride, """
                  "display": {
                    "fixed": {"rotation": [0, 0, 0], "scale": [1, 1, 1]}
                  }""", "side");
    }

    static void pickaxe(String name, String handle, String head, String gem, String animKey, String anim) throws IOException {
        List<Element> pick = new ArrayList<>();
        pick.add(rod(1.15, -3, -2, "gold"));
        pick.add(rod(0.75, -2, 3, "leather"));
        pick.add(rod(0.7, 3, 14, handle));
        pick.add(box(2.5, 13, 7.1, 13.5, 15.4, 8.9, head));
        pick.add(box(1.4, 11.5, 7.3, 2.6, 14, 8.7, head));
        pick.add(box(13.4, 11.5, 7.3, 14.6, 14, 8.7, head));
        pick.add(box(6.8, 13.4, 6.8, 9.2, 15.8, 9.2, null).anim(animKey).glow());
        pick.add(box(7.4, 15.4, 7.4, 8.6, 16.2, 8.6, gem).glow());
        model(name, withAnim(Map.of("m", "mythoscraft:item/materials"), animKey, anim), diagonal(pick), HANDHELD_DISPLAY, "front");
    }

    // =====================================================================================================
    // Fourth wave sprites
    // =====================================================================================================

    static void waveFourSprites() throws IOException {
        sprite("golem_egg", img -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 9.0, 2) + Math.pow((y - 17) / (y < 17 ? 12.0 : 10.0), 2) <= 1,
                    (x, y) -> mix(rgb(0xe7e5e4), rgb(0x78716c), Math.hypot(x - 13, y - 12) / 14), rgb(0x292524));
            for (int[] spot : new int[][]{{12, 12}, {19, 15}, {14, 22}, {20, 23}}) {
                img.fill(spot[0], spot[1], 2, 2, rgb(0x65a30d));
            }
            img.set(12, 9, rgb(0xffffff));
        });
        sprite("light_staff", img -> {
            for (int i = 0; i < 18; i++) {
                img.set(5 + i, 27 - i, rgb(0xa16207));
                img.set(6 + i, 27 - i, rgb(0x713f12));
            }
            shape(img, (x, y) -> Math.hypot(x - 24, y - 8) <= 5, (x, y) -> mix(rgb(0xfffbeb), rgb(0xfacc15), Math.hypot(x - 23, y - 7) / 6), 0);
            for (int a = 0; a < 8; a++) {
                double r = Math.toRadians(a * 45);
                img.set((int) Math.round(24 + Math.cos(r) * 7), (int) Math.round(8 + Math.sin(r) * 7), argb(200, 0xfef08a));
            }
            img.outline(INK);
        });
        sprite("climbing_gloves", img -> {
            shape(img, (x, y) -> (x >= 7 && x <= 16 && y >= 12 && y <= 26) || (y >= 7 && y < 12 && (x - 7) % 3 != 2 && x >= 7 && x <= 15)
                    || (x >= 4 && x <= 7 && y >= 14 && y <= 19), (x, y) -> mix(rgb(0xa16207), rgb(0x57534e), y / 30.0), INK);
            shape(img, (x, y) -> (x >= 17 && x <= 26 && y >= 12 && y <= 26) || (y >= 7 && y < 12 && (x - 17) % 3 != 2 && x >= 17 && x <= 25),
                    (x, y) -> mix(rgb(0xb45309), rgb(0x44403c), y / 30.0), INK);
            img.fill(8, 23, 8, 2, rgb(0x94a3b8));
            img.fill(18, 23, 8, 2, rgb(0x94a3b8));
        });
        sprite("firework_wand", img -> {
            for (int i = 0; i < 18; i++) {
                img.set(5 + i, 27 - i, rgb(0xf472b6));
                img.set(6 + i, 27 - i, rgb(0x7c3aed));
            }
            star(img, 24, 8, 6, rgb(0xfde047));
            for (int[] c : new int[][]{{17, 4, 0xf472b6}, {28, 15, 0x38bdf8}, {28, 3, 0x86efac}, {19, 13, 0xf87171}}) {
                img.set(c[0], c[1], rgb(c[2]));
                img.set(c[0] + 1, c[1], argb(160, c[2]));
            }
            img.outline(INK);
        });
        sprite("gravity_gauntlet", img -> {
            shape(img, (x, y) -> x >= 8 && x <= 23 && y >= 13 && y <= 27, (x, y) -> mix(rgb(0x6d28d9), rgb(0x1e1b4b), y / 30.0), INK);
            for (int f = 0; f < 4; f++) {
                int fx = 8 + f * 4;
                int top = 6 + (f == 0 || f == 3 ? 2 : 0);
                shape(img, (x, y) -> x >= fx && x <= fx + 2 && y >= top && y < 13, (x, y) -> rgb(0x4c1d95), 0);
            }
            shape(img, (x, y) -> Math.hypot(x - 15.5, y - 20) <= 3.5, (x, y) -> mix(rgb(0xe9d5ff), rgb(0xa855f7), Math.hypot(x - 15, y - 19) / 4), 0);
            img.outline(INK);
        });
        sprite("shield_crystal", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 3, 25, 12, 16, 29, 7, 12}),
                    (x, y) -> mix(rgb(0xe0f2fe), rgb(0x4f46e5), (y - 3) / 26.0 + (x > 16 ? 0.15 : 0)), INK);
            for (int y = 6; y < 26; y++) {
                img.set(16, y, argb(140, 0xffffff));
            }
            img.set(13, 10, rgb(0xffffff));
        });
        sprite("time_sand", img -> {
            img.fill(8, 4, 16, 3, rgb(0xa16207));
            img.fill(8, 25, 16, 3, rgb(0xa16207));
            shape(img, (x, y) -> y > 6 && y < 25 && Math.abs(x - 15.5) <= 1 + Math.abs(y - 16) * 0.65,
                    (x, y) -> (y < 16 && y > 11) || y > 20 ? rgb(0xfde68a) : argb(150, 0xe0f2fe), INK);
            img.set(16, 16, rgb(0xfde68a));
            img.set(16, 17, rgb(0xfde68a));
        });
        sprite("weather_orb", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 11, (x, y) -> y < 16 ? mix(rgb(0x7dd3fc), rgb(0xe0f2fe), y / 16.0)
                    : mix(rgb(0x64748b), rgb(0x1e293b), (y - 16) / 11.0), INK);
            shape(img, (x, y) -> Math.hypot(x - 12, y - 11) <= 3, (x, y) -> rgb(0xfde047), 0);
            for (int i = 0; i < 4; i++) {
                img.set(13 + i * 3, 20 + i % 2, rgb(0x93c5fd));
                img.set(13 + i * 3, 21 + i % 2, rgb(0x93c5fd));
            }
            img.set(10, 8, rgb(0xffffff));
        });
        sprite("ore_compass", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 11, (x, y) -> Math.hypot(x - 16, y - 16) > 9 ? rgb(0xa16207)
                    : mix(rgb(0x1e293b), rgb(0x0f172a), y / 30.0), INK);
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 8, 18, 16, 16, 24, 14, 16}),
                    (x, y) -> y < 16 ? rgb(0x67e8f9) : rgb(0xe5e7eb), 0);
            img.set(16, 16, rgb(0xfde047));
        });
        sprite("mega_mushroom", img -> {
            shape(img, (x, y) -> y <= 17 && Math.pow((x - 16) / 13.0, 2) + Math.pow((y - 17) / 12.0, 2) <= 1,
                    (x, y) -> mix(rgb(0xef4444), rgb(0x991b1b), y / 18.0), INK);
            for (int[] d : new int[][]{{10, 10}, {18, 7}, {22, 13}, {14, 14}}) {
                shape(img, (x, y) -> Math.hypot(x - d[0], y - d[1]) <= 2, (x, y) -> rgb(0xfafafa), 0);
            }
            shape(img, (x, y) -> x >= 12 && x <= 20 && y > 17 && y <= 28, (x, y) -> mix(rgb(0xfef3c7), rgb(0xd6d3d1), x / 30.0), INK);
            img.fill(14, 21, 1, 2, rgb(0x1c1917));
            img.fill(18, 21, 1, 2, rgb(0x1c1917));
        });
        sprite("shrink_potion", img -> {
            flask(img, 0x5eead4, 0x0f766e, 0x78716c);
            img.fill(15, 21, 3, 1, rgb(0xffffff));
            img.fill(16, 20, 1, 3, rgb(0xffffff));
            img.set(13, 23, rgb(0xccfbf1));
            img.set(19, 19, rgb(0xccfbf1));
        });
    }

    // =====================================================================================================
    // Fifth wave sprites
    // =====================================================================================================

    static void waveFiveSprites() throws IOException {
        animatedSprite("return_crystal", 8, 3, (img, f, n) -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 3, 24, 13, 16, 29, 8, 13}),
                    (x, y) -> mix(mix(rgb(0xc4b5fd), rgb(0x22d3ee), wave(f * Math.PI / 4 + y * 0.2)), rgb(0x4c1d95), (y - 3) / 40.0), INK);
            img.set(13, 10, rgb(0xffffff));
            img.set(12, 12, argb(200, 0xffffff));
        });
        sprite("sponge_bomb", img -> {
            shape(img, (x, y) -> Math.hypot(x - 15, y - 19) <= 9.5, (x, y) -> (x * 3 + y * 5) % 7 == 0 ? rgb(0xa16207)
                    : mix(rgb(0xfde047), rgb(0xca8a04), Math.hypot(x - 12, y - 16) / 13), INK);
            for (int i = 0; i < 5; i++) {
                img.set(19 + i, 10 - i / 2, rgb(0x94a3b8));
            }
            img.set(24, 7, rgb(0xff4d1a));
        });
        sprite("repair_hammer", img -> {
            for (int i = 0; i < 16; i++) {
                img.set(6 + i, 27 - i, rgb(0x92400e));
                img.set(7 + i, 27 - i, rgb(0x78350f));
            }
            shape(img, (x, y) -> inPolygon(x, y, new double[]{14, 9, 21, 2, 29, 10, 22, 17}),
                    (x, y) -> mix(rgb(0xe2e8f0), rgb(0x475569), (x + y) / 45.0), 0);
            img.outline(INK);
        });
        sprite("enchant_scroll", img -> {
            shape(img, (x, y) -> x >= 7 && x <= 25 && y >= 8 && y <= 24, (x, y) -> mix(rgb(0xfdf4ff), rgb(0xe9d5ff), (x + y) / 50.0), INK);
            shape(img, (x, y) -> (Math.hypot(x - 7, y - 16) <= 3 || Math.hypot(x - 25, y - 16) <= 3) && y >= 7 && y <= 25,
                    (x, y) -> rgb(0x7c3aed), INK);
            for (int i = 0; i < 4; i++) {
                img.fill(11, 11 + i * 3, 2 + (i * 5) % 9, 1, rgb(0xa855f7));
                img.fill(15 + (i * 3) % 5, 11 + i * 3, 4, 1, rgb(0x6366f1));
            }
            sparkle(img, 22, 10, rgb(0xf0abfc));
        });
        sprite("cloud_bottle", img -> {
            flask(img, 0xf8fafc, 0xbfdbfe, 0x94a3b8);
            shape(img, (x, y) -> Math.hypot(x - 14, y - 21) <= 3 || Math.hypot(x - 18, y - 20) <= 3.5 || Math.hypot(x - 16, y - 23) <= 3,
                    (x, y) -> rgb(0xffffff), 0);
        });
        sprite("hunger_charm", img -> {
            charm(img, 0xfb923c, 0x9a3412);
            shape(img, (x, y) -> Math.hypot(x - 16, y - 20) <= 3, (x, y) -> mix(rgb(0xef4444), rgb(0x991b1b), y / 30.0), 0);
            img.fill(16, 16, 1, 2, rgb(0x166534));
        });
        sprite("soul_jar", img -> {
            shape(img, (x, y) -> x >= 9 && x <= 23 && y >= 9 && y <= 28, (x, y) -> (x == 9 || x == 23) ? rgb(0xe2e8f0)
                    : y > 15 ? mix(rgb(0xa7f3d0), rgb(0x0f766e), (y - 15) / 14.0) : argb(120, 0xe0f2fe), INK);
            img.fill(8, 5, 17, 4, rgb(0x78350f));
            for (int i = 0; i < 4; i++) {
                img.set(12 + i * 3, 19 + (i % 2) * 4, rgb(0xd9f99d));
            }
        });
        sprite("lucky_dice", img -> {
            shape(img, (x, y) -> x >= 6 && x <= 26 && y >= 6 && y <= 26, (x, y) -> mix(rgb(0xffffff), rgb(0xd6d3d1), (x + y) / 52.0), INK);
            for (int[] d : new int[][]{{10, 10}, {16, 10}, {22, 10}, {10, 22}, {16, 22}, {22, 22}}) {
                img.fill(d[0] - 1, d[1] - 1, 3, 3, rgb(0xdc2626));
            }
            img.fill(15, 15, 3, 3, rgb(0xfde047));
        });
    }

    // =====================================================================================================
    // Sixth wave: sprites and wearable hats
    // =====================================================================================================

    static final String HAT_DISPLAY = """
              "display": {
                "head": {"translation": [0, 0, 0], "scale": [1, 1, 1]},
                "gui": {"rotation": [25, -135, 0], "translation": [0, -6, 0], "scale": [0.7, 0.7, 0.7]},
                "ground": {"translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
                "fixed": {"translation": [0, -6, 0], "scale": [0.8, 0.8, 0.8]},
                "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
                "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
                "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]},
                "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -3, 0], "scale": [0.4, 0.4, 0.4]}
              }""";

    static void waveSixSprites() throws IOException {
        sprite("frost_walker_charm", img -> {
            charm(img, 0xbae6fd, 0x0369a1);
            snowflakeSmall(img, 16, 20, rgb(0xffffff));
        });
        sprite("home_stone", img -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 11.0, 2) + Math.pow((y - 18) / 9.0, 2) <= 1,
                    (x, y) -> mix(rgb(0xa8a29e), rgb(0x57534e), Math.hypot(x - 12, y - 14) / 14), INK);
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 12, 22, 17, 20, 17, 20, 23, 12, 23, 12, 17, 10, 17}),
                    (x, y) -> rgb(0xa3e635), 0);
            img.fill(15, 19, 2, 4, rgb(0x14532d));
        });
        sprite("magic_bean", img -> {
            shape(img, (x, y) -> Math.pow((x - 15) / 8.0, 2) + Math.pow((y - 18) / 10.0, 2) <= 1,
                    (x, y) -> mix(rgb(0x86efac), rgb(0x166534), Math.hypot(x - 12, y - 14) / 12), rgb(0x052e16));
            for (int i = 0; i < 6; i++) {
                img.set(18 + i, 8 - (int) Math.round(Math.sin(i * 0.9) * 2) - i / 2, rgb(0x22c55e));
            }
            img.set(12, 13, rgb(0xdcfce7));
            sparkle(img, 24, 4, rgb(0xfde047));
        });
        sprite("web_shooter", img -> {
            shape(img, (x, y) -> x >= 6 && x <= 20 && y >= 14 && y <= 22, (x, y) -> mix(rgb(0x9ca3af), rgb(0x374151), y / 30.0), INK);
            shape(img, (x, y) -> x >= 8 && x <= 12 && y > 22 && y <= 28, (x, y) -> rgb(0x4b5563), INK);
            for (int a = 0; a < 6; a++) {
                double r = Math.toRadians(a * 60);
                for (int d = 1; d <= 6; d++) {
                    img.set((int) Math.round(24 + Math.cos(r) * d), (int) Math.round(10 + Math.sin(r) * d), argb(220, 0xf3f4f6));
                }
            }
            for (int d = 2; d <= 6; d += 2) {
                for (int a = 0; a < 360; a += 20) {
                    img.set((int) Math.round(24 + Math.cos(Math.toRadians(a)) * d), (int) Math.round(10 + Math.sin(Math.toRadians(a)) * d),
                            argb(150, 0xe5e7eb));
                }
            }
        });
        sprite("player_tracker", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 11, (x, y) -> Math.hypot(x - 16, y - 16) > 9 ? rgb(0x0ea5e9)
                    : mix(rgb(0x0c4a6e), rgb(0x082f49), y / 30.0), INK);
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 8, 18, 16, 16, 24, 14, 16}), (x, y) -> y < 16 ? rgb(0xfacc15) : rgb(0xe5e7eb), 0);
            img.fill(19, 9, 3, 3, rgb(0xfde047));
            img.fill(20, 12, 1, 3, rgb(0xfde047));
        });
        animatedSprite("rainbow_potion", 8, 2, (img, f, n) -> {
            int[] colors = {0xf87171, 0xfb923c, 0xfde047, 0x4ade80, 0x38bdf8, 0xa78bfa};
            shape(img, (x, y) -> Math.hypot(x - 16, y - 21) <= 8.5 || (x >= 13 && x <= 19 && y >= 7 && y <= 14),
                    (x, y) -> y < 14 ? rgb(0xe2e8f0) : rgb(colors[Math.floorMod((y + x / 3 + f) / 2, colors.length)]), INK);
            img.fill(12, 4, 8, 3, rgb(0xd4a017));
            img.set(12, 18, rgb(0xffffff));
        });
        sprite("confetti_cannon", img -> {
            shape(img, (x, y) -> segDist(x, y, 6, 25, 20, 11) < 4, (x, y) -> mix(rgb(0xf472b6), rgb(0x7c3aed), (x + y) / 50.0), INK);
            int[] colors = {0xfde047, 0x38bdf8, 0x86efac, 0xf472b6, 0xfb923c};
            int i = 0;
            for (int[] c : new int[][]{{23, 6}, {27, 9}, {25, 3}, {29, 5}, {21, 3}, {28, 13}, {24, 10}}) {
                img.fill(c[0], c[1], 2, 1, rgb(colors[i++ % colors.length]));
            }
        });
        sprite("lucky_horseshoe", img -> {
            shape(img, (x, y) -> {
                double d = Math.hypot(x - 16, y - 14);
                return (d <= 11 && d >= 6 && y <= 14) || ((x >= 5 && x <= 10 || x >= 22 && x <= 27) && y > 14 && y <= 26);
            }, (x, y) -> mix(rgb(0xf8fafc), rgb(0x94a3b8), (x + y) / 50.0), INK);
            for (int[] h : new int[][]{{7, 18}, {7, 23}, {24, 18}, {24, 23}, {9, 9}, {22, 9}, {16, 5}}) {
                img.set(h[0], h[1], rgb(0x475569));
            }
            sparkle(img, 27, 4, rgb(0xfde047));
        });
    }

    static void snowflakeSmall(Img img, int cx, int cy, int color) {
        for (int i = -3; i <= 3; i++) {
            img.set(cx + i, cy, color);
            img.set(cx, cy + i, color);
        }
        img.set(cx - 2, cy - 2, color);
        img.set(cx + 2, cy + 2, color);
        img.set(cx - 2, cy + 2, color);
        img.set(cx + 2, cy - 2, color);
    }

    static void waveSixHats() throws IOException {
        Map<String, String> tex = Map.of("m", "mythoscraft:item/materials");
        List<Element> party = new ArrayList<>();
        double[][] tiers = {{5.5, 16, 17.5}, {6, 17.5, 19}, {6.5, 19, 20.5}, {7, 20.5, 22}, {7.4, 22, 23.5}, {7.7, 23.5, 25}};
        String[] colors = {"candy", "night", "candy", "crystal", "candy", "night"};
        for (int i = 0; i < tiers.length; i++) {
            double lo = tiers[i][0];
            party.add(box(lo, tiers[i][1], lo, 16 - lo, tiers[i][2], 16 - lo, colors[i]));
        }
        party.add(box(7.2, 25, 7.2, 8.8, 26.6, 8.8, "gold").glow());
        model("party_hat", tex, party, HAT_DISPLAY, "side");

        List<Element> crown = new ArrayList<>();
        crown.add(box(3.5, 15.5, 3.5, 12.5, 17.5, 12.5, "gold"));
        double[][] spikes = {{3.5, 3.5}, {7.4, 3.5}, {11.3, 3.5}, {3.5, 11.3}, {7.4, 11.3}, {11.3, 11.3}, {3.5, 7.4}, {11.3, 7.4}};
        for (double[] sp : spikes) {
            crown.add(box(sp[0], 17.5, sp[1], sp[0] + 1.2, 20, sp[1] + 1.2, "gold"));
        }
        crown.add(box(7.2, 16, 3.3, 8.8, 17, 3.5, "ruby").glow());
        crown.add(box(7.2, 16, 12.5, 8.8, 17, 12.7, "diamond").glow());
        crown.add(box(3.3, 16, 7.2, 3.5, 17, 8.8, "emerald").glow());
        crown.add(box(12.5, 16, 7.2, 12.7, 17, 8.8, "amethyst").glow());
        model("golden_crown", tex, crown, HAT_DISPLAY, "side");
    }

    // =====================================================================================================
    // Seventh wave: ocean, forest, sky and crystal collections
    // =====================================================================================================

    static void waveSevenSprites() throws IOException {
        Map<String, String> tex = Map.of("m", "mythoscraft:item/materials");

        // ---------- 3D weapons and staffs ----------
        List<Element> coral = new ArrayList<>();
        coral.add(rod(1.2, -3, -1.8, "crystal"));
        coral.add(rod(0.65, -1.8, 4, "silver"));
        coral.add(box(4.4, 4, 7.3, 11.6, 5, 8.7, "silver"));
        coral.add(box(7, 5, 7.6, 9, 15, 8.4, "candy"));
        coral.add(box(5.4, 8, 7.65, 7, 11.5, 8.35, "candy"));
        coral.add(box(9, 10, 7.65, 10.6, 13.5, 8.35, "candy"));
        coral.add(box(7.6, 5.5, 7.5, 8.4, 14.5, 8.5, null).anim("f").glow());
        coral.add(box(7.4, 15, 7.7, 8.6, 17.2, 8.3, "candy"));
        model("coral_blade", withAnim(tex, "f", "anim_frost"), diagonal(coral), HANDHELD_DISPLAY, "front");

        List<Element> tide = new ArrayList<>();
        tide.add(rod(0.6, -3, 12.5, "silver"));
        tide.add(rod(0.9, 2, 2.6, "crystal"));
        tide.add(box(7.4, 12.5, 7.4, 8.6, 17, 8.6, "silver"));
        tide.add(box(5, 13.5, 7.5, 6.2, 16.5, 8.5, "silver"));
        tide.add(box(9.8, 13.5, 7.5, 11, 16.5, 8.5, "silver"));
        tide.add(box(5, 12.5, 7.5, 11, 13.5, 8.5, "silver"));
        tide.add(box(7.6, 17, 7.6, 8.4, 18.4, 8.4, "crystal").glow());
        tide.add(box(6.8, 14, 6.8, 9.2, 15.6, 9.2, null).anim("f").glow());
        model("tide_staff", withAnim(tex, "f", "anim_frost"), diagonal(tide), HANDHELD_DISPLAY, "front");

        List<Element> druid = new ArrayList<>();
        druid.add(rod(0.65, -3, 13, "wood"));
        druid.add(rod(0.9, 3, 3.8, "leaf"));
        druid.add(box(5.6, 13, 7.4, 6.6, 16.5, 8.6, "wood").rotate("z", 22.5, 6, 13, 8));
        druid.add(box(9.4, 13, 7.4, 10.4, 16.5, 8.6, "wood").rotate("z", -22.5, 10, 13, 8));
        druid.add(box(6.8, 13.6, 6.8, 9.2, 16, 9.2, "emerald").glow());
        druid.add(box(4.6, 15.8, 6.6, 6.4, 17.2, 9.4, "leaf"));
        druid.add(box(9.6, 15.8, 6.6, 11.4, 17.2, 9.4, "leaf"));
        model("druid_staff", tex, diagonal(druid), HANDHELD_DISPLAY, "front");

        List<Element> cloud = new ArrayList<>();
        cloud.add(rod(1.2, -3, -1.8, "gold"));
        cloud.add(rod(0.65, -1.8, 4, "snow"));
        cloud.add(box(3.6, 4, 6.8, 12.4, 5.6, 9.2, "snow"));
        cloud.add(box(2.6, 4.4, 7.2, 4, 6, 8.8, "snow"));
        cloud.add(box(12, 4.4, 7.2, 13.4, 6, 8.8, "snow"));
        cloud.add(box(6.8, 5.6, 7.62, 9.2, 17, 8.38, "silver"));
        cloud.add(box(7.6, 6, 7.5, 8.4, 16.4, 8.5, null).anim("f").glow());
        cloud.add(box(7.3, 17, 7.7, 8.7, 19, 8.3, "silver"));
        model("cloud_sword", withAnim(tex, "f", "anim_frost"), diagonal(cloud), HANDHELD_DISPLAY, "front");

        List<Element> cwand = new ArrayList<>();
        cwand.add(rod(0.55, -2.5, 12.4, "silver"));
        cwand.add(rod(0.85, 3, 3.6, "amethyst"));
        cwand.add(box(6.6, 12.4, 6.6, 9.4, 13.4, 9.4, "silver"));
        cwand.add(box(7, 13.4, 7, 9, 17.6, 9, "amethyst").rotate("y", 45, 8, 8, 8).glow());
        cwand.add(box(7.4, 17.6, 7.4, 8.6, 18.8, 8.6, null).anim("s").glow());
        model("crystal_wand", withAnim(tex, "s", "anim_star"), diagonal(cwand), HANDHELD_DISPLAY, "front");

        pickaxe("crystal_pickaxe", "silver", "amethyst", "crystal", "s", "anim_star");

        bow("leaf_bow", 0x4ade80, 0x14532d, 0xd9f99d);
        bow("crystal_bow", 0xf0abfc, 0x7e22ce, 0xe0f2fe);

        // ---------- ocean ----------
        sprite("pearl_charm", img -> {
            charm(img, 0xf0f9ff, 0x67e8f9);
            sparkle(img, 22, 10, rgb(0xffffff));
        });
        sprite("shell_horn", img -> {
            shape(img, (x, y) -> segDist(x, y, 6, 24, 24, 9) < 2.5 + (24 - y) / 6.0, (x, y) -> mix(rgb(0xfde68a), rgb(0xf472b6), (x + y) / 50.0), INK);
            for (int i = 0; i < 5; i++) {
                img.set(9 + i * 3, 21 - i * 3, rgb(0xbe185d));
            }
            img.fill(25, 6, 3, 3, rgb(0xfef3c7));
        });
        sprite("kraken_ink", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 18) <= 8, (x, y) -> mix(rgb(0x4b5563), rgb(0x111827), Math.hypot(x - 13, y - 15) / 10), INK);
            for (int t = 0; t < 4; t++) {
                for (int i = 0; i < 6; i++) {
                    img.set(10 + t * 4 + (int) Math.round(Math.sin(i * 0.9 + t) * 1.2), 25 + i / 2, rgb(0x374151));
                }
            }
            img.set(13, 15, rgb(0xd1d5db));
        });
        sprite("diving_flask", img -> flask(img, 0x67e8f9, 0x0369a1, 0xa16207));
        sprite("sushi", img -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 11.0, 2) + Math.pow((y - 18) / 7.0, 2) <= 1, (x, y) -> rgb(0xf8fafc), INK);
            shape(img, (x, y) -> Math.pow((x - 16) / 9.0, 2) + Math.pow((y - 14) / 4.0, 2) <= 1, (x, y) -> mix(rgb(0xfda4af), rgb(0xfb7185), x / 30.0), 0);
            img.fill(15, 13, 2, 8, rgb(0x14532d));
        });
        sprite("treasure_net", img -> {
            for (int i = 4; i <= 28; i += 4) {
                for (int j = 4; j <= 28; j++) {
                    img.set(i, j, rgb(0xd6b98c));
                    img.set(j, i, rgb(0xa16207));
                }
            }
            img.fill(14, 14, 5, 4, rgb(0xfbbf24));
            sparkle(img, 22, 22, rgb(0x22d3ee));
        });

        // ---------- forest ----------
        sprite("vine_whip", img -> {
            img.fill(5, 22, 3, 7, rgb(0x78350f));
            for (int i = 0; i < 22; i++) {
                int x = 7 + i;
                int y = 22 - i + (int) Math.round(Math.sin(i * 0.5) * 3);
                img.set(x, y, rgb(0x15803d));
                img.set(x, y + 1, rgb(0x22c55e));
                if (i % 5 == 2) {
                    img.set(x, y - 1, rgb(0x86efac));
                }
            }
        });
        sprite("acorn_bomb", img -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 8.0, 2) + Math.pow((y - 19) / 9.0, 2) <= 1, (x, y) -> mix(rgb(0xca8a04), rgb(0x713f12), Math.hypot(x - 13, y - 16) / 11), INK);
            shape(img, (x, y) -> y <= 13 && Math.pow((x - 16) / 9.5, 2) + Math.pow((y - 13) / 5.0, 2) <= 1, (x, y) -> (x + y) % 2 == 0 ? rgb(0x4d7c0f) : rgb(0x365314), INK);
            img.fill(16, 5, 1, 4, rgb(0x713f12));
            img.fill(20, 21, 4, 4, rgb(0xef4444));
        });
        sprite("owl_charm", img -> {
            charm(img, 0xd6d3d1, 0x78350f);
            img.fill(13, 15, 2, 2, rgb(0xfde047));
            img.fill(18, 15, 2, 2, rgb(0xfde047));
        });
        sprite("berry_pie", img -> {
            shape(img, (x, y) -> y >= 14 && Math.pow((x - 16) / 13.0, 2) + Math.pow((y - 18) / 8.0, 2) <= 1, (x, y) -> mix(rgb(0xfcd34d), rgb(0xb45309), y / 30.0), INK);
            shape(img, (x, y) -> Math.pow((x - 16) / 10.0, 2) + Math.pow((y - 15) / 4.0, 2) <= 1, (x, y) -> (x * 7 + y * 3) % 5 == 0 ? rgb(0x7c3aed) : rgb(0xbe185d), 0);
        });
        sprite("seed_of_life", img -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 7.0, 2) + Math.pow((y - 18) / 9.0, 2) <= 1, (x, y) -> mix(rgb(0xbbf7d0), rgb(0x16a34a), Math.hypot(x - 13, y - 14) / 10), rgb(0x052e16));
            img.fill(16, 5, 1, 5, rgb(0x22c55e));
            img.fill(17, 6, 3, 2, rgb(0x4ade80));
            img.fill(12, 7, 3, 2, rgb(0x4ade80));
            sparkle(img, 24, 24, rgb(0xfde047));
        });
        sprite("lumber_charm", img -> {
            charm(img, 0xca8a04, 0x713f12);
            for (int i = 0; i < 6; i++) {
                img.set(13 + i, 20 - i, rgb(0xe5e7eb));
            }
        });

        // ---------- sky ----------
        sprite("sky_glider", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 6, 29, 20, 16, 16, 3, 20}), (x, y) -> mix(rgb(0xf0f9ff), rgb(0x38bdf8), y / 24.0), INK);
            img.fill(15, 16, 2, 10, rgb(0x78350f));
        });
        sprite("wind_horn", img -> {
            shape(img, (x, y) -> segDist(x, y, 6, 24, 22, 10) < 2 + (24 - y) / 5.0, (x, y) -> mix(rgb(0xe0f2fe), rgb(0x64748b), (x + y) / 50.0), INK);
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 5; j++) {
                    img.set(23 + j, 5 + i * 3, argb(200, 0xffffff));
                }
            }
        });
        sprite("thunder_bottle", img -> {
            flask(img, 0x6366f1, 0x312e81, 0x94a3b8);
            shape(img, (x, y) -> inPolygon(x, y, new double[]{17, 15, 14, 21, 16, 21, 14, 27, 19, 19, 17, 19}), (x, y) -> rgb(0xfde047), 0);
        });
        sprite("star_map", img -> {
            shape(img, (x, y) -> x >= 4 && x <= 28 && y >= 7 && y <= 25, (x, y) -> mix(rgb(0x1e3a8a), rgb(0x0f172a), y / 30.0), rgb(0xd6b98c));
            int[][] stars = {{8, 11}, {14, 9}, {20, 14}, {25, 10}, {11, 20}, {23, 21}, {17, 18}};
            for (int[] s : stars) {
                img.set(s[0], s[1], rgb(0xfef3c7));
            }
            for (int i = 0; i < 6; i++) {
                img.set(8 + i, 11 - i / 3, argb(140, 0xfde68a));
            }
        });
        sprite("sky_cookie", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 11, (x, y) -> mix(rgb(0xfde68a), rgb(0xd97706), Math.hypot(x - 12, y - 12) / 16), INK);
            for (int[] c : new int[][]{{11, 12}, {19, 11}, {15, 19}, {21, 19}}) {
                img.fill(c[0], c[1], 3, 2, rgb(0x93c5fd));
            }
        });
        sprite("comet_shard", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{20, 6, 26, 12, 18, 22, 12, 16}), (x, y) -> mix(rgb(0xfef08a), rgb(0xf97316), (x + y) / 48.0), INK);
            for (int i = 0; i < 9; i++) {
                img.set(11 - i, 18 + i, argb(220 - i * 20, 0xfde047));
                img.set(12 - i, 19 + i, argb(160 - i * 15, 0xf97316));
            }
        });
        sprite("balloon", img -> {
            shape(img, (x, y) -> Math.pow((x - 16) / 8.5, 2) + Math.pow((y - 12) / 10.0, 2) <= 1, (x, y) -> mix(rgb(0xf87171), rgb(0xb91c1c), Math.hypot(x - 13, y - 8) / 12), INK);
            img.set(13, 7, rgb(0xfecaca));
            for (int i = 0; i < 8; i++) {
                img.set(16 + (i % 2), 22 + i, rgb(0xe5e7eb));
            }
        });

        // ---------- crystal ----------
        sprite("arcane_tome", img -> {
            shape(img, (x, y) -> x >= 6 && x <= 26 && y >= 5 && y <= 27, (x, y) -> mix(rgb(0x6d28d9), rgb(0x2e1065), y / 30.0), INK);
            img.fill(24, 6, 2, 21, rgb(0xf5f5f4));
            shape(img, (x, y) -> inPolygon(x, y, new double[]{15, 10, 19, 16, 15, 22, 11, 16}), (x, y) -> rgb(0xc4b5fd), 0);
            img.set(15, 16, rgb(0xffffff));
        });
        sprite("mana_potion", img -> flask(img, 0x60a5fa, 0x5b21b6, 0xc4b5fd));
        sprite("prism_shield", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 4, 27, 9, 25, 20, 16, 28, 7, 20, 5, 9}), (x, y) -> {
                int[] c = {0xfbcfe8, 0xc4b5fd, 0x99f6e4, 0xbae6fd};
                return mix(rgb(c[Math.floorMod((int) ((x + y) / 6), c.length)]), rgb(0xffffff), 0.15);
            }, INK);
            img.fill(15, 8, 2, 16, rgb(0xffffff));
        });
        sprite("crystal_heart", img -> {
            shape(img, (x, y) -> Math.hypot(x - 11.5, y - 12) <= 5.5 || Math.hypot(x - 20.5, y - 12) <= 5.5
                    || inPolygon(x, y, new double[]{6, 14, 26, 14, 16, 26}), (x, y) -> mix(rgb(0xfda4af), rgb(0xc026d3), (x + y) / 48.0), INK);
            img.fill(10, 10, 2, 2, rgb(0xffffff));
        });
        sprite("shard_bomb", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 18) <= 8, (x, y) -> mix(rgb(0xd8b4fe), rgb(0x6b21a8), Math.hypot(x - 13, y - 15) / 10), INK);
            for (int[] c : new int[][]{{16, 6}, {9, 10}, {23, 10}}) {
                shape(img, (x, y) -> inPolygon(x, y, new double[]{c[0], c[1] - 3, c[0] + 2, c[1] + 3, c[0] - 2, c[1] + 3}), (x, y) -> rgb(0xf5d0fe), 0);
            }
        });
    }

    // =====================================================================================================
    // Eighth wave: spawn attractions (wheel, loan elytra) and season sounds
    // =====================================================================================================

    static void waveEightSprites() throws IOException {
        sprite("wheel_pointer", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 28, 25, 8, 16, 12, 7, 8}), (x, y) -> mix(rgb(0xfde047), rgb(0xb45309), y / 30.0), INK);
            img.fill(15, 13, 2, 8, rgb(0xfff7d6));
        });
        sprite("wheel_hub", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 12, (x, y) -> {
                double d = Math.hypot(x - 16, y - 16);
                if (d > 10.5) {
                    return rgb(0xb45309);
                }
                double a = Math.toDegrees(Math.atan2(y - 16, x - 16));
                return Math.floorMod((int) Math.floor((a + 360) / 30), 2) == 0 ? rgb(0xfde047) : rgb(0xf59e0b);
            }, INK);
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 3, (x, y) -> rgb(0xfef3c7), rgb(0x92400e));
        });
        sprite("loan_elytra", img -> {
            shape(img, (x, y) -> (Math.abs(x - 16) >= 1 && y >= 4 && y <= 28 && Math.abs(x - 16) <= 13 - Math.abs(y - 12) * 0.35),
                    (x, y) -> mix(rgb(0xa3e635), rgb(0x0e7490), (Math.abs(x - 16) + y) / 40.0), INK);
            for (int y = 8; y <= 24; y += 4) {
                for (int x = 4; x <= 28; x++) {
                    if (Math.abs(x - 16) >= 2 && img.get(x, y) != 0 && img.get(x, y) != INK) {
                        img.set(x, y, argb(160, 0xecfccb));
                    }
                }
            }
            sparkle(img, 25, 6, rgb(0xffffff));
        });
    }

    /**
     * Relic crate items, spawner booster and shadow companion only exist as HD models (tools/hd/relics.py). Without
     * the HD overlay they fall back to a plain sprite of their base item, so the pack is always complete.
     */
    static void relicPlaceholders() throws IOException {
        String[][] items = {{"sort_wand", "blaze_rod"}, {"infinite_bucket", "water_bucket"}, {"vein_pickaxe", "netherite_pickaxe"},
                {"repair_charm", "iron_ingot"}, {"paint_brush", "brush"}, {"concrete_mixer", "cauldron"}, {"soul_sword", "netherite_sword"},
                {"dwarf_hat", "red_dye"}, {"growth_totem", "totem_of_undying"}, {"power_drill", "netherite_pickaxe"},
                {"mega_chainsaw", "netherite_axe"}, {"minepro_totem", "totem_of_undying"}, {"blossom_crown", "pink_petals"},
                {"bark_hand", "stick"}, {"zap_pickaxe", "netherite_pickaxe"}, {"zap_sword", "netherite_sword"},
                {"zap_rod", "fishing_rod"}, {"zap_rod_cast", "fishing_rod_cast"}, {"spawner_booster", "trial_key"},
                {"companion_shadow", "black_dye"}, {"ghost_sword", "netherite_sword"}, {"ghost_pickaxe", "netherite_pickaxe"},
                {"ghost_axe", "netherite_axe"}, {"ghost_shovel", "netherite_shovel"}, {"ghost_hoe", "netherite_hoe"}};
        for (String[] it : items) {
            spriteModel(it[0], "minecraft:item/" + it[1]);
        }
    }

    /** HD models made with tools/hd (python tools/hd/build.py) are copied over the generated ones. */
    static void hdOverlay() throws IOException {
        Path src = Path.of("tools/hd/out");
        if (!Files.isDirectory(src)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path f : walk.filter(Files::isRegularFile).toList()) {
                Path target = out.resolve(src.relativize(f).toString());
                Files.createDirectories(target.getParent());
                Files.copy(f, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Custom sounds (sounds/*.ogg from tools/sounds) and sounds.json. */
    static void sounds() throws IOException {
        Path src = Path.of("tools/sounds");
        if (!Files.isDirectory(src)) {
            return;
        }
        StringBuilder json = new StringBuilder("{\n");
        List<Path> files;
        try (Stream<Path> list = Files.list(src)) {
            files = list.filter(p -> p.toString().endsWith(".ogg")).sorted().toList();
        }
        for (int i = 0; i < files.size(); i++) {
            String name = files.get(i).getFileName().toString().replace(".ogg", "");
            Path target = out.resolve("assets/mythoscraft/sounds/season/" + name + ".ogg");
            Files.createDirectories(target.getParent());
            Files.copy(files.get(i), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            json.append("  \"season.").append(name).append("\": {\"sounds\": [{\"name\": \"mythoscraft:season/")
                    .append(name).append("\"}]}").append(i < files.size() - 1 ? ",\n" : "\n");
        }
        // long songs (music/*.ogg) are streamed: "music.<name>"
        Path music = src.resolve("music");
        if (Files.isDirectory(music)) {
            List<Path> songs;
            try (Stream<Path> list = Files.list(music)) {
                songs = list.filter(p -> p.toString().endsWith(".ogg")).sorted().toList();
            }
            for (Path song : songs) {
                String name = song.getFileName().toString().replace(".ogg", "");
                Path target = out.resolve("assets/mythoscraft/sounds/music/" + name + ".ogg");
                Files.createDirectories(target.getParent());
                Files.copy(song, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                if (json.length() > 2) {
                    json.setLength(json.length() - 1);
                    json.append(",\n");
                }
                json.append("  \"music.").append(name).append("\": {\"sounds\": [{\"name\": \"mythoscraft:music/")
                        .append(name).append("\", \"stream\": true}]}\n");
            }
        }
        json.append("}\n");
        write("assets/mythoscraft/sounds.json", json.toString());
    }

    // =====================================================================================================
    // Ninth wave: millennium, cosmetics, space, spawner, chainsaw and frostfest extras
    // =====================================================================================================

    static void waveNineModels() throws IOException {
        Map<String, String> tex = Map.of("m", "mythoscraft:item/materials");

        List<Element> pstaff = new ArrayList<>();
        pstaff.add(rod(0.6, -3, 12.5, "gold"));
        pstaff.add(rod(0.85, 2, 2.8, "crystal"));
        pstaff.add(rod(0.85, 7, 7.8, "crystal"));
        pstaff.add(box(5.6, 12.5, 7.4, 10.4, 13.6, 8.6, "gold"));
        pstaff.add(box(5.6, 13.6, 7.4, 6.7, 17.2, 8.6, "gold"));
        pstaff.add(box(9.3, 13.6, 7.4, 10.4, 17.2, 8.6, "gold"));
        pstaff.add(box(5.6, 17.2, 7.4, 10.4, 18.3, 8.6, "gold"));
        pstaff.add(box(6.9, 14, 7.6, 9.1, 16.6, 8.4, null).anim("s").glow());
        model("pharaoh_staff", withAnim(tex, "s", "anim_star"), diagonal(pstaff), HANDHELD_DISPLAY, "front");

        List<Element> khopesh = new ArrayList<>();
        khopesh.add(rod(1.1, -3, -1.8, "gold"));
        khopesh.add(rod(0.65, -1.8, 4, "crystal"));
        khopesh.add(box(5, 4, 7.3, 11, 5, 8.7, "gold"));
        khopesh.add(box(7, 5, 7.6, 9, 11, 8.4, "steel"));
        khopesh.add(box(7.6, 11, 7.6, 10, 14, 8.4, "steel"));
        khopesh.add(box(9.4, 13, 7.6, 12, 16, 8.4, "steel"));
        khopesh.add(box(11, 15.5, 7.6, 12.8, 17.5, 8.4, "gold"));
        model("khopesh", tex, diagonal(khopesh), HANDHELD_DISPLAY, "front");

        List<Element> laser = new ArrayList<>();
        laser.add(rod(0.9, -3, 3, "netherite"));
        laser.add(rod(1.4, 3, 9, "silver"));
        laser.add(rod(1.6, 4, 4.8, "crystal"));
        laser.add(rod(1.6, 6.5, 7.3, "crystal"));
        laser.add(rod(0.7, 9, 15, "steel"));
        laser.add(box(7.1, 15, 7.1, 8.9, 16.6, 8.9, "ruby").glow());
        model("laser_gun", tex, diagonal(laser), HANDHELD_DISPLAY, "front");

        List<Element> nut = new ArrayList<>();
        nut.add(rod(1.2, -3, -1.8, "gold"));
        nut.add(rod(0.65, -1.8, 4, "ruby"));
        nut.add(box(4, 4, 7.2, 12, 5.4, 8.8, "gold"));
        nut.add(box(6.8, 5.4, 7.6, 9.2, 17, 8.4, "candy"));
        nut.add(box(7.4, 17, 7.7, 8.6, 18.8, 8.3, "snow"));
        model("nutcracker_sword", tex, diagonal(nut), HANDHELD_DISPLAY, "front");

        List<Element> saw = new ArrayList<>();
        saw.add(rod(0.8, -3, 2, "netherite"));
        saw.add(box(5, 2, 6, 11, 8, 10, "ruby"));
        saw.add(box(5.5, 3, 5.6, 10.5, 7, 6, "netherite"));
        saw.add(box(6.6, 8, 7.3, 9.4, 18, 8.7, "steel"));
        saw.add(box(6.3, 8, 7.2, 6.6, 18.4, 8.8, null).anim("c"));
        saw.add(box(9.4, 8, 7.2, 9.7, 18.4, 8.8, null).anim("c"));
        saw.add(box(6.3, 18, 7.2, 9.7, 18.4, 8.8, null).anim("c"));
        model("chainsaw", withAnim(tex, "c", "anim_chain"), diagonal(saw), HANDHELD_DISPLAY, "front");

        pickaxe("spawner_pickaxe", "netherite", "emerald", "amethyst", "s", "anim_star");

        // ---------- hats & masks (worn on the head) ----------
        List<Element> pm = new ArrayList<>();
        pm.add(box(-0.4, -0.4, -0.4, 16.4, 16.4, 16.4, "pumpkin"));
        pm.add(box(3, 8, -0.6, 6, 11, -0.4, "lava_glow").glow());
        pm.add(box(10, 8, -0.6, 13, 11, -0.4, "lava_glow").glow());
        pm.add(box(4, 3, -0.6, 12, 5, -0.4, "lava_glow").glow());
        pm.add(box(7.4, 16.4, 7.4, 8.6, 18, 8.6, "leaf"));
        model("pumpkin_mask", tex, pm, HAT_DISPLAY, "side");

        List<Element> sk = new ArrayList<>();
        sk.add(box(-0.4, 3, -0.4, 16.4, 16.4, 16.4, "bone"));
        sk.add(box(2.5, 7.5, -0.6, 6.5, 11, -0.4, "night"));
        sk.add(box(9.5, 7.5, -0.6, 13.5, 11, -0.4, "night"));
        sk.add(box(7.2, 5, -0.6, 8.8, 7, -0.4, "night"));
        sk.add(box(3, 0, -0.4, 13, 3, 3, "bone"));
        model("skull_mask", tex, sk, HAT_DISPLAY, "side");

        List<Element> witch = new ArrayList<>();
        witch.add(box(-3, 16, -3, 19, 16.8, 19, "night"));
        double[][] wt = {{2.5, 16.8, 19.5}, {3.5, 19.5, 22}, {4.5, 22, 24.5}, {5.5, 24.5, 26.5}, {6.5, 26.5, 28.5}};
        for (double[] t : wt) {
            witch.add(box(t[0], t[1], t[0], 16 - t[0], t[2], 16 - t[0], "night"));
        }
        witch.add(box(2.4, 17, 2.4, 13.6, 18.2, 13.6, "amethyst").glow());
        model("witch_hat", tex, witch, HAT_DISPLAY, "side");

        List<Element> santa = new ArrayList<>();
        santa.add(box(-0.6, 15.5, -0.6, 16.6, 18, 16.6, "snow"));
        double[][] st = {{1.5, 18, 20.5}, {3, 20.5, 23}, {4.5, 23, 25}, {6, 25, 26.5}};
        for (double[] t : st) {
            santa.add(box(t[0], t[1], t[0] + 1, 16 - t[0], t[2], 17 - t[0], "ruby"));
        }
        santa.add(box(6.6, 25.5, 12, 9.4, 28.3, 14.8, "snow"));
        model("santa_hat", tex, santa, HAT_DISPLAY, "side");

        List<Element> deer = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            double x = side < 0 ? 1 : 13.6;
            deer.add(box(x, 16, 7, x + 1.4, 22, 8.4, "wood"));
            deer.add(box(side < 0 ? x - 3 : x + 1.4, 20, 7, side < 0 ? x : x + 4.4, 21.2, 8.4, "wood"));
            deer.add(box(side < 0 ? x - 3 : x + 3, 21.2, 7, side < 0 ? x - 1.8 : x + 4.2, 24, 8.4, "wood"));
            deer.add(box(x, 22, 7, x + 1.4, 24.5, 8.4, "wood"));
        }
        deer.add(box(6.8, 3.5, -1.6, 9.2, 5.9, -0.2, "ruby").glow());
        model("reindeer_antlers", tex, deer, HAT_DISPLAY, "side");

        List<Element> snowman = new ArrayList<>();
        snowman.add(box(-0.4, -0.4, -0.4, 16.4, 16.4, 16.4, "snow"));
        snowman.add(box(3.5, 9, -0.6, 5.5, 11, -0.4, "night"));
        snowman.add(box(10.5, 9, -0.6, 12.5, 11, -0.4, "night"));
        snowman.add(box(7, 6, -4, 9, 8, -0.4, "pumpkin"));
        snowman.add(box(1, 16.4, 1, 15, 17.2, 15, "night"));
        snowman.add(box(3.5, 17.2, 3.5, 12.5, 23, 12.5, "night"));
        model("snowman_head", tex, snowman, HAT_DISPLAY, "side");

        List<Element> bunny = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            double x = side < 0 ? 3 : 10;
            bunny.add(box(x, 16, 7, x + 3, 28, 9, "snow").rotate("z", side * -22.5, x + 1.5, 16, 8));
            bunny.add(box(x + 0.7, 17.5, 6.8, x + 2.3, 26.5, 7, "candy").rotate("z", side * -22.5, x + 1.5, 16, 8));
        }
        model("bunny_ears", tex, bunny, HAT_DISPLAY, "side");

        List<Element> heart = new ArrayList<>();
        heart.add(box(1.5, 8, -1, 7, 12, -0.4, "ruby").glow());
        heart.add(box(9, 8, -1, 14.5, 12, -0.4, "ruby").glow());
        heart.add(box(2.5, 7, -1, 6, 8, -0.4, "ruby").glow());
        heart.add(box(10, 7, -1, 13.5, 8, -0.4, "ruby").glow());
        heart.add(box(7, 10, -1, 9, 10.8, -0.4, "gold"));
        heart.add(box(-0.6, 10, -0.6, 0, 10.8, 10, "gold"));
        heart.add(box(16, 10, -0.6, 16.6, 10.8, 10, "gold"));
        model("heart_glasses", tex, heart, HAT_DISPLAY, "side");

        List<Element> sun = new ArrayList<>();
        sun.add(box(-4, 16, -4, 20, 16.8, 20, "straw"));
        sun.add(box(2, 16.8, 2, 14, 21, 14, "straw"));
        sun.add(box(1.8, 17, 1.8, 14.2, 18.2, 14.2, "crystal"));
        model("sun_hat", tex, sun, HAT_DISPLAY, "side");

        List<Element> leaf = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            double x = 8 + Math.cos(a) * 7.6;
            double z = 8 + Math.sin(a) * 7.6;
            leaf.add(box(x - 1.3, 15.5, z - 1.3, x + 1.3, 18 + (i % 2), z + 1.3, i % 3 == 0 ? "pumpkin" : i % 3 == 1 ? "leaf" : "lava_rock"));
        }
        model("leaf_crown", tex, leaf, HAT_DISPLAY, "side");

        List<Element> ny = new ArrayList<>();
        ny.add(box(1, 7.5, -1, 7, 12.5, -0.4, "gold").glow());
        ny.add(box(9, 7.5, -1, 15, 12.5, -0.4, "gold").glow());
        ny.add(box(2, 8.5, -1.2, 6, 11.5, -1, "night"));
        ny.add(box(10, 8.5, -1.2, 14, 11.5, -1, "night"));
        ny.add(box(-0.6, 10, -0.6, 0, 10.8, 10, "gold"));
        ny.add(box(16, 10, -0.6, 16.6, 10.8, 10, "gold"));
        ny.add(box(7.4, 12.5, -1, 8.6, 15, -0.4, "crystal").glow());
        model("new_year_glasses", tex, ny, HAT_DISPLAY, "side");

        List<Element> halo = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5);
            double x = 8 + Math.cos(a) * 6.5;
            double z = 8 + Math.sin(a) * 6.5;
            halo.add(box(x - 0.9, 19.5, z - 0.9, x + 0.9, 20.5, z + 0.9, i % 4 == 0 ? "crystal" : "gold").glow());
        }
        model("star_halo", tex, halo, HAT_DISPLAY, "side");

        List<Element> top = new ArrayList<>();
        top.add(box(-2, 16, -2, 18, 16.8, 18, "night"));
        top.add(box(3, 16.8, 3, 13, 26, 13, "night"));
        top.add(box(2.8, 17.2, 2.8, 13.2, 18.6, 13.2, "ruby"));
        model("top_hat", tex, top, HAT_DISPLAY, "side");

        List<Element> cat = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            double x = side < 0 ? 1.5 : 10.5;
            cat.add(box(x, 16, 6, x + 4, 18, 9, "night"));
            cat.add(box(x + 0.5, 18, 6.5, x + 3.5, 19.5, 8.5, "night"));
            cat.add(box(x + 1.2, 19.5, 7, x + 2.8, 21, 8, "night"));
            cat.add(box(x + 1, 16.5, 5.8, x + 3, 19, 6, "candy"));
        }
        model("cat_ears", tex, cat, HAT_DISPLAY, "side");
    }

    static void waveNineSprites() throws IOException {
        sprite("scarab_charm", img -> {
            charm(img, 0x22d3ee, 0x0e7490);
            img.fill(14, 12, 4, 1, rgb(0xfde047));
            img.fill(13, 13, 6, 5, rgb(0x0f766e));
        });
        sprite("ankh", img -> {
            shape(img, (x, y) -> {
                double d = Math.hypot(x - 16, (y - 9) * 1.2);
                return (d <= 6 && d >= 3.2) || (x >= 14 && x <= 18 && y >= 13 && y <= 29) || (y >= 15 && y <= 18 && x >= 7 && x <= 25);
            }, (x, y) -> mix(rgb(0xfde047), rgb(0xb45309), (x + y) / 50.0), INK);
            sparkle(img, 25, 5, rgb(0xffffff));
        });
        sprite("mummy_wrap", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 10, (x, y) -> ((x * 2 + y) / 4) % 2 == 0 ? rgb(0xf5f5f4) : rgb(0xd6d3d1), INK);
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 3, (x, y) -> rgb(0xa8a29e), 0);
            img.fill(24, 18, 6, 3, rgb(0xe7e5e4));
        });
        sprite("sand_bomb", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 18) <= 9, (x, y) -> mix(rgb(0xfde68a), rgb(0xca8a04), Math.hypot(x - 13, y - 15) / 12), INK);
            img.fill(15, 6, 2, 4, rgb(0xa16207));
            img.set(17, 5, rgb(0xff6a00));
        });
        sprite("pharaoh_treasure", img -> {
            shape(img, (x, y) -> x >= 5 && x <= 27 && y >= 12 && y <= 26, (x, y) -> mix(rgb(0x1d4ed8), rgb(0x1e3a8a), y / 30.0), INK);
            shape(img, (x, y) -> x >= 5 && x <= 27 && y >= 7 && y < 12, (x, y) -> rgb(0xfbbf24), INK);
            img.fill(14, 15, 4, 5, rgb(0xfde047));
            sparkle(img, 9, 4, rgb(0xfde047));
            sparkle(img, 24, 3, rgb(0xffffff));
        });
        sprite("sun_disc", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 8, (x, y) -> mix(rgb(0xfde047), rgb(0xf97316), Math.hypot(x - 13, y - 13) / 11), INK);
            for (int i = 0; i < 12; i++) {
                double a = Math.toRadians(i * 30);
                for (int d = 10; d <= 13; d++) {
                    img.set((int) Math.round(16 + Math.cos(a) * d), (int) Math.round(16 + Math.sin(a) * d), rgb(0xfbbf24));
                }
            }
        });
        sprite("rocket_booster", img -> {
            shape(img, (x, y) -> segDist(x, y, 9, 23, 22, 10) < 4, (x, y) -> mix(rgb(0xf1f5f9), rgb(0x94a3b8), (x + y) / 50.0), INK);
            shape(img, (x, y) -> inPolygon(x, y, new double[]{22, 4, 28, 10, 24, 12, 20, 8}), (x, y) -> rgb(0xef4444), INK);
            img.fill(14, 15, 3, 3, rgb(0x38bdf8));
            for (int i = 0; i < 5; i++) {
                img.set(6 - i / 2, 26 + i / 2, rgb(0xff8c1a));
                img.set(5 - i / 2, 25 + i / 2, rgb(0xfde047));
            }
        });
        planet("mars_orb", 0xf97316, 0x7f1d1d, -1);
        planet("moon_orb", 0xe5e7eb, 0x6b7280, -1);
        planet("earth_orb", 0x38bdf8, 0x15803d, 0x16a34a);
        sprite("saturn_orb", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 8, (x, y) -> (y / 3) % 2 == 0 ? rgb(0xfde68a) : rgb(0xd97706), INK);
            for (int x = 2; x <= 30; x++) {
                int y = (int) Math.round(16 + (x - 16) * 0.25);
                if (Math.abs(x - 16) > 7 || y > 17) {
                    img.set(x, y, rgb(0xe7e5e4));
                    img.set(x, y + 1, rgb(0xa8a29e));
                }
            }
        });
        sprite("star_dust", img -> {
            shape(img, (x, y) -> (Math.hypot(x - 16, y - 20) <= 8 && y >= 15) || (x >= 13 && x <= 19 && y >= 9 && y < 15),
                    (x, y) -> y < 15 ? rgb(0xe2e8f0) : mix(rgb(0x818cf8), rgb(0x312e81), y / 30.0), INK);
            img.fill(12, 6, 8, 3, rgb(0xa16207));
            sparkle(img, 14, 20, rgb(0xfef3c7));
            sparkle(img, 19, 24, rgb(0xffffff));
        });
        sprite("gingerbread", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 9) <= 5 || (x >= 10 && x <= 22 && y >= 13 && y <= 22)
                    || (x >= 5 && x <= 27 && y >= 14 && y <= 17) || ((x >= 10 && x <= 14 || x >= 18 && x <= 22) && y > 22 && y <= 28),
                    (x, y) -> rgb(0xb45309), INK);
            img.set(14, 8, rgb(0xffffff));
            img.set(18, 8, rgb(0xffffff));
            img.fill(15, 16, 2, 2, rgb(0xef4444));
            img.fill(15, 20, 2, 2, rgb(0x22c55e));
        });
        sprite("snow_globe", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 13) <= 10, (x, y) -> argb(170, 0xbae6fd), INK);
            img.fill(8, 23, 16, 5, rgb(0x7c2d12));
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 8, 21, 19, 11, 19}), (x, y) -> rgb(0x166534), 0);
            for (int[] f : new int[][]{{10, 9}, {22, 11}, {13, 15}, {19, 6}}) {
                img.set(f[0], f[1], rgb(0xffffff));
            }
        });
        sprite("present_launcher", img -> {
            shape(img, (x, y) -> segDist(x, y, 6, 25, 20, 11) < 4, (x, y) -> mix(rgb(0x16a34a), rgb(0x14532d), (x + y) / 50.0), INK);
            img.fill(21, 4, 8, 8, rgb(0xef4444));
            img.fill(24, 4, 2, 8, rgb(0xfde047));
            img.fill(21, 7, 8, 2, rgb(0xfde047));
        });
        sprite("mistletoe", img -> {
            for (int i = 0; i < 8; i++) {
                img.set(16, 3 + i, rgb(0xb91c1c));
            }
            shape(img, (x, y) -> Math.pow((x - 11) / 6.0, 2) + Math.pow((y - 17) / 3.0, 2) <= 1
                    || Math.pow((x - 21) / 6.0, 2) + Math.pow((y - 17) / 3.0, 2) <= 1, (x, y) -> rgb(0x22c55e), INK);
            for (int[] b : new int[][]{{14, 21}, {16, 22}, {18, 21}}) {
                img.fill(b[0], b[1], 2, 2, rgb(0xf8fafc));
            }
        });
        sprite("ornament_bomb", img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 18) <= 9, (x, y) -> mix(rgb(0xf472b6), rgb(0x9d174d), Math.hypot(x - 13, y - 15) / 12), INK);
            img.fill(13, 6, 6, 4, rgb(0xfde047));
            img.fill(10, 16, 12, 2, rgb(0xfde047));
            img.set(12, 13, rgb(0xffffff));
        });
        sprite("eggnog", img -> flask(img, 0xfef3c7, 0xd97706, 0x7c2d12));
        sprite("reindeer_bell", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{16, 6, 24, 22, 8, 22}) || Math.hypot(x - 16, y - 8) <= 3,
                    (x, y) -> mix(rgb(0xfde047), rgb(0xb45309), (x + y) / 48.0), INK);
            img.fill(14, 23, 4, 3, rgb(0x7c2d12));
            img.fill(8, 4, 16, 2, rgb(0xb91c1c));
        });
    }

    static void planet(String name, int light, int dark, int land) throws IOException {
        sprite(name, img -> {
            shape(img, (x, y) -> Math.hypot(x - 16, y - 16) <= 11, (x, y) -> {
                int c = mix(rgb(light), rgb(dark), Math.hypot(x - 12, y - 12) / 16);
                if (land >= 0 && Math.sin(x * 0.6) + Math.cos(y * 0.5) > 0.9) {
                    c = rgb(land);
                }
                if (land < 0 && ((x * 7 + y * 3) % 17 == 0)) {
                    c = mix(c, rgb(0x000000), 0.3);
                }
                return c;
            }, INK);
            sparkle(img, 26, 5, rgb(0xffffff));
        });
    }

    // =====================================================================================================
    // Tenth wave: Silvester job gear
    // =====================================================================================================

    static void waveTenModels() throws IOException {
        Map<String, String> tex = Map.of("m", "mythoscraft:item/materials");
        pickaxe("newyear_pickaxe", "gold", "amethyst", "diamond", "s", "anim_star");

        List<Element> axe = new ArrayList<>();
        axe.add(rod(1.2, -3, -2, "gold"));
        axe.add(rod(0.8, -2, 3, "night"));
        axe.add(rod(0.7, 3, 15, "gold"));
        axe.add(box(8.6, 11, 7.2, 12.6, 17, 8.8, "amethyst"));
        axe.add(box(12.6, 10, 7.4, 13.8, 18, 8.6, null).anim("s").glow());
        axe.add(box(5.2, 13, 7.5, 7.4, 15, 8.5, "gold"));
        model("newyear_axe", withAnim(tex, "s", "anim_star"), diagonal(axe), HANDHELD_DISPLAY, "front");

        List<Element> hoe = new ArrayList<>();
        hoe.add(rod(1.2, -3, -2, "gold"));
        hoe.add(rod(0.7, -2, 15, "night"));
        hoe.add(box(7.3, 14, 7.3, 13.5, 16, 8.7, "gold"));
        hoe.add(box(11.5, 12, 7.4, 13.5, 14, 8.6, null).anim("s").glow());
        model("newyear_hoe", withAnim(tex, "s", "anim_star"), diagonal(hoe), HANDHELD_DISPLAY, "front");

        List<Element> sword = new ArrayList<>();
        sword.add(rod(1.2, -3, -1.8, "amethyst"));
        sword.add(rod(0.65, -1.8, 4, "night"));
        sword.add(box(3.8, 4, 7.1, 12.2, 5.4, 8.9, "gold"));
        sword.add(box(6.8, 5.4, 7.6, 9.2, 17, 8.4, "gold"));
        sword.add(box(7.6, 6, 7.5, 8.4, 16.4, 8.5, null).anim("s").glow());
        sword.add(box(7.3, 17, 7.7, 8.7, 19, 8.3, "diamond").glow());
        model("newyear_sword", withAnim(tex, "s", "anim_star"), diagonal(sword), HANDHELD_DISPLAY, "front");

        sprite("newyear_rod", img -> {
            drawRod(img, true);
            sparkle(img, 26, 4, rgb(0xfde047));
            sparkle(img, 8, 10, rgb(0xa855f7));
        });
        sprite("newyear_rod_cast", img -> {
            drawRod(img, false);
            sparkle(img, 26, 4, rgb(0xfde047));
        });
        for (String rod : new String[]{"newyear_rod", "newyear_rod_cast"}) {
            write("assets/mythoscraft/models/item/" + rod + ".json",
                    "{\n  \"parent\": \"minecraft:item/handheld_rod\",\n  \"textures\": {\"layer0\": \"mythoscraft:item/" + rod + "\"}\n}\n");
        }
        sprite("newyear_trowel", img -> {
            shape(img, (x, y) -> inPolygon(x, y, new double[]{14, 4, 26, 12, 18, 20, 10, 12}), (x, y) -> mix(rgb(0xfde047), rgb(0xb45309), (x + y) / 48.0), INK);
            img.fill(10, 18, 3, 3, rgb(0x78350f));
            for (int i = 0; i < 7; i++) {
                img.set(9 - i / 2, 21 + i, rgb(0x78350f));
            }
            sparkle(img, 24, 4, rgb(0xffffff));
        });
        sprite("fortune_charm", img -> {
            charm(img, 0xfde047, 0xa855f7);
            sparkle(img, 22, 9, rgb(0xffffff));
        });
        sprite("job_elixir", img -> flask(img, 0xa855f7, 0x581c87, 0xfde047));
        sprite("lucky_rocket", img -> {
            shape(img, (x, y) -> segDist(x, y, 9, 25, 21, 9) < 3.2, (x, y) -> ((x + y) / 4) % 2 == 0 ? rgb(0xef4444) : rgb(0xfde047), INK);
            shape(img, (x, y) -> inPolygon(x, y, new double[]{21, 3, 27, 9, 23, 11, 19, 7}), (x, y) -> rgb(0x22d3ee), INK);
            for (int i = 0; i < 5; i++) {
                img.set(7 - i, 27 + i / 2, rgb(0xfde047));
            }
            sparkle(img, 6, 6, rgb(0xffffff));
        });
    }

    static void write(String path, String content) throws IOException {
        Path p = out.resolve(path);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8);
    }
}
