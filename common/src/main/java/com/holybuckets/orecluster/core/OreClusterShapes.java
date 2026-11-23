package com.holybuckets.orecluster.core;

import com.holybuckets.foundation.AAA.ShapeGen;
import com.holybuckets.foundation.HBUtil.Fast3DArray;
import com.holybuckets.foundation.HBUtil.TripleInt;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class OreClusterShapes {

    private static final int MOUND_MIN_DIAMETER = 5;
    private static final double MOUND_HEIGHT_MIN = 0.85;
    private static final double MOUND_HEIGHT_MAX = 1.05;
    private static final double MOUND_STRETCH = 0.3;
    private static final double MOUND_RADIUS_JITTER = 0.6;
    private static final double MOUND_DRIFT_CHANCE = 0.35;
    private static final double MOUND_MAX_DRIFT = 0.25;
    private static final double MOUND_BASE_EDGE_DROP = 0.15;
    private static final double MOUND_EDGE_DROP = 0.3;

    private static final int SPIRE_MIN_BASE = 3;
    private static final int SPIRE_MAX_BASE = 9;
    private static final double SPIRE_BASE_RATIO = 0.5;
    private static final double SPIRE_HEIGHT_MIN = 1.8;
    private static final double SPIRE_HEIGHT_MAX = 2.6;
    private static final double SPIRE_TAPER_MIN = 1.2;
    private static final double SPIRE_TAPER_MAX = 1.7;
    private static final double SPIRE_MAX_LEAN = 0.2;
    private static final double SPIRE_WOBBLE = 0.25;
    private static final double SPIRE_MAX_WOBBLE = 1.0;
    private static final double SPIRE_RADIUS_JITTER = 0.35;
    private static final double SPIRE_ROOT_EDGE_DROP = 0.5;
    private static final double SPIRE_EDGE_DROP = 0.2;
    private static final double SPIRE_SPINE_LOW = 0.15;
    private static final double SPIRE_SPINE_HIGH = 0.6;

    private OreClusterShapes() {
    }

    public static Fast3DArray mound(TripleInt volume, Random rand) {
        int diameter = Math.max(MOUND_MIN_DIAMETER, Math.min(volume.x, volume.z));
        double radius = diameter / 2.0;
        int height = clamp((int) Math.round(radius * between(rand, MOUND_HEIGHT_MIN, MOUND_HEIGHT_MAX)), 2, Math.max(2, volume.y));
        double stretch = 1.0 + (rand.nextDouble() - 0.5) * MOUND_STRETCH;

        Set<BlockPos> shape = new LinkedHashSet<>();
        Set<BlockPos> below = null;
        double prevRadius = radius;
        double cx = 0;
        double cz = 0;

        for (int y = 0; y < height; y++) {
            double t = (y + 0.5) / height;
            double r = radius * Math.sqrt(Math.max(0, 1 - t * t));
            if (y > 0) r += (rand.nextDouble() * 2 - 1) * MOUND_RADIUS_JITTER;
            r = Math.max(0.5, Math.min(r, prevRadius));
            prevRadius = r;

            if (y > 0 && rand.nextDouble() < MOUND_DRIFT_CHANCE) {
                double limit = radius * MOUND_MAX_DRIFT;
                cx = clamp(cx + rand.nextInt(3) - 1, -limit, limit);
                cz = clamp(cz + rand.nextInt(3) - 1, -limit, limit);
            }

            BlockPos center = new BlockPos((int) Math.round(cx), y, (int) Math.round(cz));
            List<BlockPos> layer = ShapeGen.ellipse(center, oddWidth(r * stretch), oddWidth(r / stretch));
            roughenEdge(layer, rand, y == 0 ? MOUND_BASE_EDGE_DROP : MOUND_EDGE_DROP);
            if (below != null) layer = supported(layer, below, false);
            if (layer.isEmpty()) break;

            shape.addAll(layer);
            below = new HashSet<>(layer);
        }
        return toArray(shape);
    }

    public static Fast3DArray spire(TripleInt volume, Random rand) {
        int maxBase = Math.max(SPIRE_MIN_BASE, Math.min(volume.x, volume.z));
        int base = clamp((int) Math.round(maxBase * SPIRE_BASE_RATIO), SPIRE_MIN_BASE, Math.min(SPIRE_MAX_BASE, maxBase));
        if (base % 2 == 0) base++;
        int height = (int) Math.round(base * between(rand, SPIRE_HEIGHT_MIN, SPIRE_HEIGHT_MAX));
        height = Math.max(base + 1, Math.min(height, volume.y));

        double radius = base / 2.0;
        double taper = between(rand, SPIRE_TAPER_MIN, SPIRE_TAPER_MAX);
        double leanAngle = rand.nextDouble() * Math.PI * 2;
        double leanSlope = rand.nextDouble() * SPIRE_MAX_LEAN;
        double leanX = Math.cos(leanAngle) * leanSlope;
        double leanZ = Math.sin(leanAngle) * leanSlope;

        double[] radii = new double[height];
        BlockPos[] centers = new BlockPos[height];
        Set<BlockPos> shape = new LinkedHashSet<>();
        Set<BlockPos> below = null;
        double prevRadius = radius;
        double wobbleX = 0;
        double wobbleZ = 0;

        List<BlockPos> root = ShapeGen.circle(BlockPos.ZERO, base + 2);
        roughenEdge(root, rand, SPIRE_ROOT_EDGE_DROP);
        shape.addAll(root);

        for (int y = 0; y < height; y++) {
            double t = y / (double) (height - 1);
            double r = radius * Math.pow(1 - t, taper);
            if (y > 0) r += (rand.nextDouble() * 2 - 1) * SPIRE_RADIUS_JITTER;
            r = Math.max(0.0, Math.min(r, prevRadius));
            prevRadius = r;

            wobbleX = clamp(wobbleX + (rand.nextDouble() * 2 - 1) * SPIRE_WOBBLE, -SPIRE_MAX_WOBBLE, SPIRE_MAX_WOBBLE);
            wobbleZ = clamp(wobbleZ + (rand.nextDouble() * 2 - 1) * SPIRE_WOBBLE, -SPIRE_MAX_WOBBLE, SPIRE_MAX_WOBBLE);
            BlockPos center = new BlockPos((int) Math.round(leanX * y + wobbleX), y, (int) Math.round(leanZ * y + wobbleZ));

            List<BlockPos> layer = ShapeGen.circle(center, oddWidth(r));
            if (layer.size() > 9) roughenEdge(layer, rand, SPIRE_EDGE_DROP);
            if (below != null) layer = supported(layer, below, true);
            if (layer.isEmpty()) {
                height = y;
                break;
            }

            radii[y] = r;
            centers[y] = center;
            shape.addAll(layer);
            below = new HashSet<>(layer);
        }

        addSpines(shape, rand, radii, centers, height, base);
        return toArray(shape);
    }

    private static void addSpines(Set<BlockPos> shape, Random rand, double[] radii, BlockPos[] centers, int height, int base) {
        int low = Math.max(1, (int) (height * SPIRE_SPINE_LOW));
        int high = Math.max(low + 1, (int) (height * SPIRE_SPINE_HIGH));
        if (high > height) return;

        int spines = (base >= 5) ? 2 + rand.nextInt(3) : 1;
        for (int i = 0; i < spines; i++) {
            int y = low + rand.nextInt(high - low);
            if (centers[y] == null) continue;

            double angle = rand.nextDouble() * Math.PI * 2;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            double edge = Math.max(0.5, radii[y]);
            int length = 2 + rand.nextInt(2);
            int rise = 1 + rand.nextInt(3);

            BlockPos start = centers[y].offset((int) Math.round(dx * edge), 0, (int) Math.round(dz * edge));
            BlockPos end = start.offset((int) Math.round(dx * length), rise, (int) Math.round(dz * length));
            shape.addAll(ShapeGen.line(start, end));
        }
    }

    private static List<BlockPos> supported(List<BlockPos> layer, Set<BlockPos> below, boolean allowDiagonal) {
        List<BlockPos> kept = new ArrayList<>(layer.size());
        for (BlockPos pos : layer) {
            BlockPos under = pos.below();
            if (below.contains(under)) {
                kept.add(pos);
            } else if (allowDiagonal && (below.contains(under.north()) || below.contains(under.south())
                || below.contains(under.east()) || below.contains(under.west()))) {
                kept.add(pos);
            }
        }
        return kept;
    }

    private static void roughenEdge(List<BlockPos> layer, Random rand, double dropChance) {
        if (layer.size() <= 1 || dropChance <= 0) return;
        Set<BlockPos> drop = new HashSet<>();
        for (BlockPos pos : ShapeGen.outline(layer)) {
            if (rand.nextDouble() < dropChance) drop.add(pos);
        }
        if (drop.size() < layer.size()) layer.removeAll(drop);
    }

    private static Fast3DArray toArray(Set<BlockPos> shape) {
        Fast3DArray array = new Fast3DArray(shape.size());
        for (BlockPos pos : shape) array.add(pos);
        return array;
    }

    private static int oddWidth(double radius) {
        return 2 * (int) Math.round(Math.max(0, radius - 0.5)) + 1;
    }

    private static double between(Random rand, double min, double max) {
        return min + rand.nextDouble() * (max - min);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
