import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/**
 * Generateur des visuels de marque allAnime (banniere Android TV + icones de lanceur).
 *
 * Les couleurs proviennent du logo fourni : corail #F94C4C, pastille blanche,
 * "all" gris #B7BAC4 et "Anime" charbon #242730.
 *
 * Utilisation (JDK 11+, depuis le dossier kotlin/) :
 *   java tools/BrandAssetGenerator.java app/src/main/res
 */
public final class BrandAssetGenerator {

    private static final Color CORAL = new Color(0xF9, 0x4C, 0x4C);
    private static final Color WHITE = Color.WHITE;
    private static final Color MIST_GREY = new Color(0xB7, 0xBA, 0xC4);
    private static final Color CHARCOAL = new Color(0x24, 0x27, 0x30);

    public static void main(String[] args) throws Exception {
        File res = new File(args.length > 0 ? args[0] : "app/src/main/res");

        write(banner(320, 180), new File(res, "drawable-xhdpi/banner.png"));

        // Icone de lanceur classique (pre-API 26) et couche premier plan adaptative.
        int[][] launcherSizes = {
                {48, 0}, {72, 1}, {96, 2}, {144, 3}, {192, 4}
        };
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int[] entry : launcherSizes) {
            int size = entry[0];
            String density = densities[entry[1]];
            write(launcher(size), new File(res, "mipmap-" + density + "/ic_launcher.png"));
            // La couche premier plan adaptative doit tenir dans les 66 % centraux.
            write(adaptiveForeground(size * 108 / 48),
                    new File(res, "mipmap-" + density + "/ic_launcher_foreground.png"));
        }
        System.out.println("Visuels generes dans " + res.getAbsolutePath());
    }

    /** Banniere Android TV : reprise fidele de la composition du logo. */
    private static BufferedImage banner(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(image);

        g.setColor(CORAL);
        g.fillRect(0, 0, width, height);

        double pillMargin = width * 0.07;
        double pillHeight = height * 0.52;
        double pillY = (height - pillHeight) / 2.0;
        double pillWidth = width - 2 * pillMargin;
        g.setColor(WHITE);
        g.fill(new RoundRectangle2D.Double(pillMargin, pillY, pillWidth, pillHeight,
                pillHeight, pillHeight));

        drawWordmark(g, (float) (width / 2.0), (float) (height / 2.0), height * 0.30f);

        g.dispose();
        return image;
    }

    /** Icone carree : pastille blanche sur fond corail, monogramme "aA". */
    private static BufferedImage launcher(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(image);

        g.setColor(CORAL);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, size * 0.22, size * 0.22));

        double inset = size * 0.16;
        g.setColor(WHITE);
        g.fill(new RoundRectangle2D.Double(inset, inset * 1.35, size - 2 * inset,
                size - 2.7 * inset, size * 0.32, size * 0.32));

        drawMonogram(g, size / 2f, size / 2f, size * 0.36f);

        g.dispose();
        return image;
    }

    /** Couche premier plan pour l'icone adaptative (fond corail fourni par la couche arriere). */
    private static BufferedImage adaptiveForeground(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = prepare(image);

        double safe = size * 0.66;
        double origin = (size - safe) / 2.0;
        g.setColor(WHITE);
        g.fill(new RoundRectangle2D.Double(origin, origin + safe * 0.17, safe, safe * 0.66,
                safe * 0.33, safe * 0.33));

        drawMonogram(g, size / 2f, size / 2f, (float) (safe * 0.40));

        g.dispose();
        return image;
    }

    /** Dessine "all" en gris puis "Anime" en charbon, centres sur (centerX, centerY). */
    private static void drawWordmark(Graphics2D g, float centerX, float centerY, float fontSize) {
        Font font = brandFont(fontSize);
        g.setFont(font);
        FontRenderContext frc = g.getFontRenderContext();

        String prefix = "all";
        String suffix = "Anime";
        double prefixWidth = font.getStringBounds(prefix, frc).getWidth();
        double suffixWidth = font.getStringBounds(suffix, frc).getWidth();
        double total = prefixWidth + suffixWidth;

        float baseline = centerY + fontSize * 0.36f;
        float startX = (float) (centerX - total / 2.0);

        g.setColor(MIST_GREY);
        g.drawString(prefix, startX, baseline);
        g.setColor(CHARCOAL);
        g.drawString(suffix, (float) (startX + prefixWidth), baseline);

        // Point d'exposant du logotype.
        double dot = fontSize * 0.13;
        g.fill(new Rectangle2D.Double(startX + total + dot * 0.4,
                baseline - fontSize * 0.95, dot, dot));
    }

    /** Monogramme "aA" utilise sur les icones carrees. */
    private static void drawMonogram(Graphics2D g, float centerX, float centerY, float fontSize) {
        Font font = brandFont(fontSize);
        g.setFont(font);
        FontRenderContext frc = g.getFontRenderContext();

        String prefix = "a";
        String suffix = "A";
        double prefixWidth = font.getStringBounds(prefix, frc).getWidth();
        double suffixWidth = font.getStringBounds(suffix, frc).getWidth();
        double total = prefixWidth + suffixWidth;

        float baseline = centerY + fontSize * 0.36f;
        float startX = (float) (centerX - total / 2.0);

        g.setColor(MIST_GREY);
        g.drawString(prefix, startX, baseline);
        g.setColor(CHARCOAL);
        g.drawString(suffix, (float) (startX + prefixWidth), baseline);
    }

    private static Font brandFont(float size) {
        Font arial = new Font("Arial", Font.BOLD, Math.round(size));
        if (!"Arial".equalsIgnoreCase(arial.getFamily())) {
            return new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size));
        }
        return arial.deriveFont(size);
    }

    private static Graphics2D prepare(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setStroke(new BasicStroke(1f));
        return g;
    }

    private static void write(BufferedImage image, File target) throws Exception {
        Files.createDirectories(target.getParentFile().toPath());
        ImageIO.write(image, "png", target);
        System.out.println("  - " + target.getPath() + " (" + image.getWidth() + "x"
                + image.getHeight() + ")");
    }

    private BrandAssetGenerator() {
    }
}
