package io.github.yoima.clearthirdpersonaim.gametest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.BiPredicate;

/**
 * スクリーンショットのピクセル比較。しきい値の根拠は DECISIONS.md の「自動クライアントテストのしきい値」を参照。
 */
final class ScreenshotAnalysis {
    /** 1 ピクセルの色差 (RGB 各チャンネルの差の最大値) がこれを超えたら「変化した」とみなす。 */
    static final int CHANGED_PIXEL_DIFF = 24;
    /** プレイヤーのシルエット判定 (プレイヤーあり/透明化エフェクト) の色差しきい値。背景 (白コンクリート) との差は十分大きい。 */
    static final int SILHOUETTE_DIFF = 40;
    /** 透明化される領域で、変化したピクセルが占めるべき最小割合。 */
    static final double MIN_CHANGED_FRACTION_TRANSPARENT = 0.6;
    /** 透明化されない領域で許される、変化したピクセルの最大割合 (アニメーションの微小な揺れ・アンチエイリアス分)。 */
    static final double MAX_CHANGED_FRACTION_UNCHANGED = 0.05;
    /** 透明化されない領域で許される平均色差。 */
    static final double MAX_MEAN_DIFF_UNCHANGED = 6.0;
    /** 画面全体がほぼ一致するとみなす平均色差 (一人称・解除後の比較)。 */
    static final double MAX_MEAN_DIFF_IDENTICAL = 2.0;
    /** 画面全体がほぼ一致するとみなす、変化ピクセルの最大割合。 */
    static final double MAX_CHANGED_FRACTION_IDENTICAL = 0.01;
    /**
     * 理想のブレンド画像との平均色差の上限。8bit の丸めとアンチエイリアス境界の誤差だけを許す。
     * パーツ単位の半透明描画だと重なった内側のパーツが見えて、平均で数十の差が出る。
     */
    static final double MAX_MEAN_DIFF_BLEND = 8.0;
    /** 評価に使う領域の最小ピクセル数。これ未満だと統計として意味が無いのでテスト失敗にする。 */
    static final int MIN_REGION_PIXELS = 200;

    private ScreenshotAnalysis() {
    }

    static BufferedImage load(Path path) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) throw new IOException("not an image: " + path);
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static int diff(int rgbA, int rgbB) {
        int dr = Math.abs(((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF));
        int dg = Math.abs(((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF));
        int db = Math.abs((rgbA & 0xFF) - (rgbB & 0xFF));
        return Math.max(dr, Math.max(dg, db));
    }

    /** プレイヤーのシルエット (プレイヤーが写っている画像と、写っていない画像の差)。 */
    static boolean[][] silhouette(BufferedImage withPlayer, BufferedImage withoutPlayer) {
        int w = withPlayer.getWidth(), h = withPlayer.getHeight();
        boolean[][] mask = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                mask[y][x] = diff(withPlayer.getRGB(x, y), withoutPlayer.getRGB(x, y)) > SILHOUETTE_DIFF;
            }
        }
        return mask;
    }

    /** シルエットの外接矩形 [minX, minY, maxX, maxY]。 */
    static int[] bounds(boolean[][] mask) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        for (int y = 0; y < mask.length; y++) {
            for (int x = 0; x < mask[y].length; x++) {
                if (!mask[y][x]) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        return new int[]{minX, minY, maxX, maxY};
    }

    record RegionStats(int pixels, double meanDiff, double changedFraction) {
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "pixels=%d mean=%.2f changed=%.3f", pixels, meanDiff, changedFraction);
        }
    }

    /** actual と「front を alpha、back を 1-alpha で混ぜた理想画像」を比較する。 */
    static RegionStats compareToBlend(BufferedImage actual, BufferedImage front, BufferedImage back, double alpha,
                                      boolean[][] mask, BiPredicate<Integer, Integer> region) {
        int w = actual.getWidth(), h = actual.getHeight();
        BufferedImage expected = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int f = front.getRGB(x, y), b = back.getRGB(x, y);
                int r = (int) Math.round(((f >> 16) & 0xFF) * alpha + ((b >> 16) & 0xFF) * (1 - alpha));
                int g = (int) Math.round(((f >> 8) & 0xFF) * alpha + ((b >> 8) & 0xFF) * (1 - alpha));
                int bl = (int) Math.round((f & 0xFF) * alpha + (b & 0xFF) * (1 - alpha));
                expected.setRGB(x, y, (r << 16) | (g << 8) | bl);
            }
        }
        return compare(actual, expected, mask, region);
    }

    /** {@code region} が真のピクセルだけで a と b を比較する。region が null なら画面全体。 */
    static RegionStats compare(BufferedImage a, BufferedImage b, boolean[][] mask, BiPredicate<Integer, Integer> region) {
        int w = Math.min(a.getWidth(), b.getWidth()), h = Math.min(a.getHeight(), b.getHeight());
        long sum = 0;
        int count = 0, changed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (mask != null && !mask[y][x]) continue;
                if (region != null && !region.test(x, y)) continue;
                int d = diff(a.getRGB(x, y), b.getRGB(x, y));
                sum += d;
                count++;
                if (d > CHANGED_PIXEL_DIFF) changed++;
            }
        }
        if (count == 0) return new RegionStats(0, 0, 0);
        return new RegionStats(count, (double) sum / count, (double) changed / count);
    }
}
