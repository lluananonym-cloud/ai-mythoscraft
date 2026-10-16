package online.mythoscraft.plots;

/** Grid position of a plot ("x;z"). */
public record PlotId(int x, int z) {

    public static PlotId parse(String s) {
        String[] p = s.trim().split("[;,]");
        if (p.length != 2) {
            throw new IllegalArgumentException("plot id must look like 3;-2");
        }
        return new PlotId(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()));
    }

    @Override
    public String toString() {
        return x + ";" + z;
    }
}
