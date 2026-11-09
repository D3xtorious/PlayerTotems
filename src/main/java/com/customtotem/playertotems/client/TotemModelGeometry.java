package com.customtotem.playertotems.client;

import com.customtotem.playertotems.PlayerTotems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and renders the 3D totem model geometry from custom_totem.json.
 * Supports both Classic (4px) and Slim/Alex (3px) skin models, and eliminates
 * edge artifacts and texture bleeding.
 */
public class TotemModelGeometry {
    private static final List<Quad> QUADS_CLASSIC = new ArrayList<>();
    private static final List<Quad> QUADS_SLIM = new ArrayList<>();
    private static boolean initialized = false;

    public record Vertex(float x, float y, float z, float u, float v) {}
    public record Quad(Vertex v0, Vertex v1, Vertex v2, Vertex v3, float nx, float ny, float nz) {}

    public static synchronized void ensureLoaded() {
        if (initialized) return;
        initialized = true;

        try (InputStream in = TotemModelGeometry.class.getResourceAsStream("/assets/minecraft/models/item/custom_totem.json")) {
            if (in == null) {
                PlayerTotems.LOGGER.error("Could not find /assets/minecraft/models/item/custom_totem.json");
                return;
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray elements = root.getAsJsonArray("elements");
            if (elements == null) return;

            for (JsonElement elemJson : elements) {
                JsonObject elem = elemJson.getAsJsonObject();
                String elemName = elem.has("name") ? elem.get("name").getAsString() : "";
                // Skip inverted backdrop elements (e.g. "Голова(задник)") which are inverted duplicates of outer layers
                if (elemName.contains("задник") || elemName.toLowerCase().contains("backdrop")) {
                    continue;
                }

                JsonArray fromArr = elem.getAsJsonArray("from");
                JsonArray toArr = elem.getAsJsonArray("to");
                if (fromArr == null || toArr == null) continue;

                float x0 = fromArr.get(0).getAsFloat();
                float y0 = fromArr.get(1).getAsFloat();
                float z0 = fromArr.get(2).getAsFloat();

                float x1 = toArr.get(0).getAsFloat();
                float y1 = toArr.get(1).getAsFloat();
                float z1 = toArr.get(2).getAsFloat();

                // Skip inverted box elements (inside-out duplicates designed for backface culling tricks)
                if (x0 > x1 || y0 > y1 || z0 > z1) {
                    continue;
                }

                JsonObject faces = elem.getAsJsonObject("faces");
                if (faces == null) continue;

                addFaceIfPresent(faces, "north", x0, y0, z0, x1, y1, z1);
                addFaceIfPresent(faces, "south", x0, y0, z0, x1, y1, z1);
                addFaceIfPresent(faces, "west",  x0, y0, z0, x1, y1, z1);
                addFaceIfPresent(faces, "east",  x0, y0, z0, x1, y1, z1);
                addFaceIfPresent(faces, "up",    x0, y0, z0, x1, y1, z1);
                addFaceIfPresent(faces, "down",  x0, y0, z0, x1, y1, z1);
            }
            PlayerTotems.LOGGER.info("Successfully loaded geometry: {} Classic quads, {} Slim quads.", QUADS_CLASSIC.size(), QUADS_SLIM.size());
        } catch (Exception e) {
            PlayerTotems.LOGGER.error("Error loading custom_totem.json: {}", e.getMessage(), e);
        }

        buildBorderNodes();
    }

    private static void addFaceIfPresent(JsonObject faces, String faceName, float x0, float y0, float z0, float x1, float y1, float z1) {
        if (!faces.has(faceName)) return;
        JsonObject face = faces.getAsJsonObject(faceName);
        JsonArray uvArr = face.getAsJsonArray("uv");
        if (uvArr == null || uvArr.size() < 4) return;

        float rawU0 = uvArr.get(0).getAsFloat();
        float rawV0 = uvArr.get(1).getAsFloat();
        float rawU1 = uvArr.get(2).getAsFloat();
        float rawV1 = uvArr.get(3).getAsFloat();

        float uMax = Math.max(rawU0, rawU1);
        float vMax = Math.max(rawV0, rawV1);

        // Filter out interior slice joints and dummy placeholder faces mapped to v <= 1.05 or (0..8, 0..8)
        if (vMax <= 1.05f || (uMax <= 2.01f && vMax <= 2.01f)) {
            return;
        }

        // Filter out degenerate zero-area faces
        if (Math.abs(rawU1 - rawU0) < 0.005f || Math.abs(rawV1 - rawV0) < 0.005f) {
            return;
        }

        int rotation = face.has("rotation") ? face.get("rotation").getAsInt() : 0;

        // Build Classic quad
        addQuad(QUADS_CLASSIC, faceName, rawU0, rawV0, rawU1, rawV1, rotation, false, x0, y0, z0, x1, y1, z1);

        // Build Slim quad (remaps arm UVs for 3px Alex models)
        addQuad(QUADS_SLIM, faceName, rawU0, rawV0, rawU1, rawV1, rotation, true, x0, y0, z0, x1, y1, z1);
    }

    private static void addQuad(List<Quad> list, String faceName, float rawU0, float rawV0, float rawU1, float rawV1, int rotation, boolean isSlim, float x0, float y0, float z0, float x1, float y1, float z1) {
        // Convert from 0..16 grid to pixel scale (0..64)
        float pxU0 = rawU0 * 4.0f;
        float pxV0 = rawV0 * 4.0f;
        float pxU1 = rawU1 * 4.0f;
        float pxV1 = rawV1 * 4.0f;

        if (isSlim) {
            pxU0 = convertUToSlim(pxU0, pxV0);
            pxU1 = convertUToSlim(pxU1, pxV1);
        }

        // Half-texel clamp/inset (up to 0.25 pixel) to prevent GPU texture filtering from bleeding across pixel borders
        float maxInsetU = Math.min(0.25f, Math.abs(pxU1 - pxU0) * 0.25f);
        float maxInsetV = Math.min(0.25f, Math.abs(pxV1 - pxV0) * 0.25f);
        float du = Math.signum(pxU1 - pxU0) * maxInsetU;
        float dv = Math.signum(pxV1 - pxV0) * maxInsetV;

        pxU0 += du;
        pxU1 -= du;
        pxV0 += dv;
        pxV1 -= dv;

        // Normalize to 0.0 .. 1.0 UV space
        float u0 = pxU0 / 64.0f;
        float v0 = pxV0 / 64.0f;
        float u1 = pxU1 / 64.0f;
        float v1 = pxV1 / 64.0f;

        float[] uvs = applyRotation(u0, v0, u1, v1, rotation);

        Quad quad = switch (faceName) {
            case "north" -> new Quad(
                new Vertex(x1, y1, z0, uvs[0], uvs[1]),
                new Vertex(x1, y0, z0, uvs[2], uvs[3]),
                new Vertex(x0, y0, z0, uvs[4], uvs[5]),
                new Vertex(x0, y1, z0, uvs[6], uvs[7]),
                0, 0, -1
            );
            case "south" -> new Quad(
                new Vertex(x0, y1, z1, uvs[0], uvs[1]),
                new Vertex(x0, y0, z1, uvs[2], uvs[3]),
                new Vertex(x1, y0, z1, uvs[4], uvs[5]),
                new Vertex(x1, y1, z1, uvs[6], uvs[7]),
                0, 0, 1
            );
            case "west" -> new Quad(
                new Vertex(x0, y1, z0, uvs[0], uvs[1]),
                new Vertex(x0, y0, z0, uvs[2], uvs[3]),
                new Vertex(x0, y0, z1, uvs[4], uvs[5]),
                new Vertex(x0, y1, z1, uvs[6], uvs[7]),
                -1, 0, 0
            );
            case "east" -> new Quad(
                new Vertex(x1, y1, z1, uvs[0], uvs[1]),
                new Vertex(x1, y0, z1, uvs[2], uvs[3]),
                new Vertex(x1, y0, z0, uvs[4], uvs[5]),
                new Vertex(x1, y1, z0, uvs[6], uvs[7]),
                1, 0, 0
            );
            case "up" -> new Quad(
                new Vertex(x0, y1, z0, uvs[0], uvs[1]),
                new Vertex(x0, y1, z1, uvs[2], uvs[3]),
                new Vertex(x1, y1, z1, uvs[4], uvs[5]),
                new Vertex(x1, y1, z0, uvs[6], uvs[7]),
                0, 1, 0
            );
            case "down" -> new Quad(
                new Vertex(x0, y0, z1, uvs[0], uvs[1]),
                new Vertex(x0, y0, z0, uvs[2], uvs[3]),
                new Vertex(x1, y0, z0, uvs[4], uvs[5]),
                new Vertex(x1, y0, z1, uvs[6], uvs[7]),
                0, -1, 0
            );
            default -> null;
        };

        if (quad != null && !isDuplicateQuad(list, quad)) {
            list.add(quad);
        }
    }

    private static boolean isDuplicateQuad(List<Quad> list, Quad q) {
        for (Quad existing : list) {
            if (isVertexNear(existing.v0, q.v0) &&
                isVertexNear(existing.v1, q.v1) &&
                isVertexNear(existing.v2, q.v2) &&
                isVertexNear(existing.v3, q.v3)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVertexNear(Vertex a, Vertex b) {
        return Math.abs(a.x - b.x) < 0.002f &&
               Math.abs(a.y - b.y) < 0.002f &&
               Math.abs(a.z - b.z) < 0.002f;
    }

    /**
     * Converts a U pixel coordinate on a 64x64 skin from Classic (4px) arm layout
     * to Slim / Alex (3px) arm layout.
     */
    private static float convertUToSlim(float u, float v) {
        // Right Arm Inner: X in [40..56], Y in [16..32]
        // Right Sleeve:    X in [40..56], Y in [32..48]
        if (u >= 39.5f && u <= 56.5f && v >= 15.5f && v <= 48.5f) {
            if (u <= 44.0f) {
                return u; // Outer side stays unchanged
            } else if (u <= 48.0f) {
                // Front: 4px -> 3px
                float progress = (u - 44.0f) / 4.0f;
                return 44.0f + progress * 3.0f;
            } else if (u <= 52.0f) {
                // Inner side: shifted left by 1px
                float progress = (u - 48.0f) / 4.0f;
                return 47.0f + progress * 4.0f;
            } else {
                // Back: shifted left by 1px, 4px -> 3px
                float progress = (u - 52.0f) / 4.0f;
                return 51.0f + progress * 3.0f;
            }
        }

        // Left Arm Inner: X in [32..48], Y in [48..64]
        if (u >= 31.5f && u <= 48.5f && v >= 47.5f && v <= 64.5f) {
            if (u <= 36.0f) {
                return u; // Inner side stays unchanged
            } else if (u <= 40.0f) {
                // Front: 4px -> 3px
                float progress = (u - 36.0f) / 4.0f;
                return 36.0f + progress * 3.0f;
            } else if (u <= 44.0f) {
                // Outer side: shifted left by 1px
                float progress = (u - 40.0f) / 4.0f;
                return 39.0f + progress * 4.0f;
            } else {
                // Back: shifted left by 1px, 4px -> 3px
                float progress = (u - 44.0f) / 4.0f;
                return 43.0f + progress * 3.0f;
            }
        }

        // Left Sleeve: X in [48..64], Y in [48..64]
        if (u >= 47.5f && u <= 64.5f && v >= 47.5f && v <= 64.5f) {
            if (u <= 52.0f) {
                return u; // Inner side stays unchanged
            } else if (u <= 56.0f) {
                // Front: 4px -> 3px
                float progress = (u - 52.0f) / 4.0f;
                return 52.0f + progress * 3.0f;
            } else if (u <= 60.0f) {
                // Outer side: shifted left by 1px
                float progress = (u - 56.0f) / 4.0f;
                return 55.0f + progress * 4.0f;
            } else {
                // Back: shifted left by 1px, 4px -> 3px
                float progress = (u - 60.0f) / 4.0f;
                return 59.0f + progress * 3.0f;
            }
        }

        return u;
    }

    private static float[] applyRotation(float u0, float v0, float u1, float v1, int rotation) {
        float[] base = new float[] {
            u0, v0,
            u0, v1,
            u1, v1,
            u1, v0
        };
        int steps = (rotation / 90) % 4;
        if (steps <= 0) return base;

        float[] rotated = new float[8];
        int offset = steps * 2;
        for (int i = 0; i < 4; i++) {
            int srcIndex = (offset + i * 2) % 8;
            rotated[i * 2] = base[srcIndex];
            rotated[i * 2 + 1] = base[srcIndex + 1];
        }
        return rotated;
    }

    /**
     * Exact pixel-grid origin of the inner totem body in custom_totem.json.
     */
    private static final float X_ORIGIN = 0.00368f;
    private static final float Y_ORIGIN = 0.32515f;

    /**
     * Counter-clockwise 24-corner rectilinear silhouette polygon of the inner totem body
     * in 1x1 pixel grid units (gx, gy), tracing from the bottom base around the right side,
     * right wing/arm, head top, left wing/arm, and back to the bottom base.
     */
    private static final int[][] INNER_CONTOUR_GRID = {
        { 6,  0}, {10,  0}, {10,  1}, {11,  1}, {11,  3}, {12,  3},
        {12,  5}, {14,  5}, {14,  6}, {15,  6}, {15,  8}, {12,  8},
        {12, 15}, { 4, 15}, { 4,  8}, { 1,  8}, { 1,  6}, { 2,  6},
        { 2,  5}, { 4,  5}, { 4,  3}, { 5,  3}, { 5,  1}, { 6,  1}
    };

    /**
     * Pre-baked 3D volumetric crystal aura node along the totem silhouette.
     * All ring coordinates (x0..x2, y0..y2), Z-depths (zF1, zB1), 3D face normals,
     * and diagonal wave phase are precomputed once at startup for zero per-frame geometry math.
     */
    public record BorderNode(
        float x0, float y0,
        float x1, float y1, float zF1, float zB1,
        float x2, float y2,
        float innerNx, float innerNy, float innerNz,
        float outerNx, float outerNy, float outerNz,
        float wavePhase
    ) {}

    private static final List<BorderNode> BORDER_NODES = new ArrayList<>();

    private static void buildBorderNodes() {
        if (!BORDER_NODES.isEmpty()) return;

        int n = INNER_CONTOUR_GRID.length;
        float[][] edgeNormals = new float[n][2];
        int[] edgeLengths = new int[n];
        int totalPerimeter = 0;

        for (int i = 0; i < n; i++) {
            int[] p0 = INNER_CONTOUR_GRID[i];
            int[] p1 = INNER_CONTOUR_GRID[(i + 1) % n];
            int dx = p1[0] - p0[0];
            int dy = p1[1] - p0[1];
            int len = Math.abs(dx) + Math.abs(dy);
            edgeLengths[i] = len;
            totalPerimeter += len;
            // Outward normal for counter-clockwise polygon: (dy, -dx) / len
            edgeNormals[i][0] = (float) dy / len;
            edgeNormals[i][1] = (float) (-dx) / len;
        }

        // Exact 45-degree miter vector at each of the 24 corners: m_i = n_{i-1} + n_i
        float[][] cornerMiters = new float[n][2];
        for (int i = 0; i < n; i++) {
            float[] nPrev = edgeNormals[(i - 1 + n) % n];
            float[] nCurr = edgeNormals[i];
            cornerMiters[i][0] = nPrev[0] + nCurr[0];
            cornerMiters[i][1] = nPrev[1] + nCurr[1];
        }

        // Radial offsets for the 3-ring volumetric crystal profile:
        // Ring 0 (d0 = 0.00): tucked inside the inner totem body [7.50, 8.50]
        // Ring 1 (d1 = 0.34): 3D volumetric crest clearing the outer hat/jacket wear layer
        // Ring 2 (d2 = 0.64): razor-sharp outer crystal blade apex at z = 8.00
        final float d1 = 0.34f;
        final float d2 = 0.64f;

        int accumulatedLen = 0;
        for (int i = 0; i < n; i++) {
            int[] p0 = INNER_CONTOUR_GRID[i];
            int[] p1 = INNER_CONTOUR_GRID[(i + 1) % n];
            int edgeLen = edgeLengths[i];
            // Subdivide long edges into ~2px steps so straight edges stay straight while smooth in color
            int subdivisions = Math.max(1, (edgeLen + 1) / 2);
            float outNx = edgeNormals[i][0];
            float outNy = edgeNormals[i][1];

            // Precompute 3D facet unit normals for this edge's inner bevel and outer crystal blade
            float innerNx = -outNx * 0.24f;
            float innerNy = -outNy * 0.24f;
            float innerNz = 0.97f;

            float outerNx = outNx * 0.92f;
            float outerNy = outNy * 0.92f;
            float outerNz = 0.39f;

            for (int k = 0; k < subdivisions; k++) {
                float t = (float) k / (float) subdivisions;
                float gx = p0[0] + (p1[0] - p0[0]) * t;
                float gy = p0[1] + (p1[1] - p0[1]) * t;
                float x0 = X_ORIGIN + gx;
                float y0 = Y_ORIGIN + gy;

                // Use the exact 45-degree corner miter at the corner (k == 0), and pure perpendicular
                // edge normal at interior subdivision points so straight runs never skew
                float mx = (k == 0) ? cornerMiters[i][0] : outNx;
                float my = (k == 0) ? cornerMiters[i][1] : outNy;

                float x1 = x0 + mx * d1;
                float y1 = y0 + my * d1;
                float x2 = x0 + mx * d2;
                float y2 = y0 + my * d2;

                // Step Z-depth outward around the 3D head/hat layer (gy >= 7.5) vs body/wings
                boolean isHeadDepth = gy >= 7.5f;
                float zF1 = isHeadDepth ? 8.78f : 8.70f;
                float zB1 = isHeadDepth ? 7.22f : 7.30f;

                float s = (accumulatedLen + edgeLen * t) / (float) totalPerimeter;
                // Combine perimeter progression with a diagonal spatial wave (gx + gy)
                float wavePhase = (s * 0.65f + (gx + gy) * 0.025f) % 1.0f;

                BORDER_NODES.add(new BorderNode(
                    x0, y0,
                    x1, y1, zF1, zB1,
                    x2, y2,
                    innerNx, innerNy, innerNz,
                    outerNx, outerNy, outerNz,
                    wavePhase
                ));
            }
            accumulatedLen += edgeLen;
        }
    }

    /**
     * Converts HSV components into packed 32-bit ARGB color.
     */
    public static int getHsvColor(float hue, float saturation, float value) {
        float h = (hue % 1.0f) * 6.0f;
        if (h < 0.0f) h += 6.0f;
        int i = (int) h;
        float f = h - i;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - saturation * f);
        float t = value * (1.0f - saturation * (1.0f - f));

        float r = 0, g = 0, b = 0;
        switch (i % 6) {
            case 0 -> { r = value; g = t;     b = p; }
            case 1 -> { r = q;     g = value; b = p; }
            case 2 -> { r = p;     g = value; b = t; }
            case 3 -> { r = p;     g = q;     b = value; }
            case 4 -> { r = t;     g = p;     b = value; }
            case 5 -> { r = value; g = p;     b = q; }
        }

        int red = (int) (r * 255.0f + 0.5f);
        int green = (int) (g * 255.0f + 0.5f);
        int blue = (int) (b * 255.0f + 0.5f);

        return (0xFF << 24) | (red << 16) | (green << 8) | blue;
    }

    /**
     * Calculates a dynamic rainbow ARGB color value cycling smoothly over time (jeb_ effect).
     */
    public static int getRainbowColor() {
        long time = System.currentTimeMillis();
        float hue = (float) (time % 6000L) / 6000.0f;
        return getHsvColor(hue, 1.0f, 0.94f);
    }

    /**
     * Computes prismatic 3D facet colors for a node:
     * Ring 0 = deep inner root, Ring 1 = luminous jewel crest (+hue dispersion), Ring 2 = crisp outer apex (-hue dispersion).
     */
    private static int computeNodeColor( float wavePhase, float timeFactor, int ringIndex) {
        float baseHue = wavePhase - timeFactor;
        return switch (ringIndex) {
            case 0 -> getHsvColor(baseHue, 1.0f, 0.84f);
            case 1 -> getHsvColor(baseHue + 0.035f, 0.88f, 1.00f);
            default -> getHsvColor(baseHue - 0.035f, 1.0f, 0.76f);
        };
    }

    /**
     * High-contrast emissive minimum light so the 3D crystal aura gleams in dark caves and GUI slots.
     */
    private static int getAuraLight(int worldLight) {
        int block = (worldLight >> 4) & 0xF;
        int sky = (worldLight >> 20) & 0xF;
        int emissiveBlock = Math.max(block, 13);
        int emissiveSky = Math.max(sky, 13);
        return (emissiveSky << 20) | (emissiveBlock << 4);
    }

    /**
     * Renders a 3D volumetric crystal rainbow aura shell around the totem statue.
     *
     * <p>Uses 4 pre-baked 3D facets per segment (Front Inner Slope, Front Outer Crystal Blade,
     * Back Outer Crystal Blade, Back Inner Slope) that step in Z-depth around the 3D head and body
     * and converge to a sharp centerline apex at {@code z = 8.00f}.</p>
     */
    public static void renderRainbowBorder(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, boolean isSlim) {
        ensureLoaded();
        int n = BORDER_NODES.size();
        if (n < 3) return;

        int borderLight = getAuraLight(light);

        long now = System.currentTimeMillis();
        float timeFactor = (float) (now % 6000L) / 6000.0f; // Smooth 6.0s traveling chromatic wave

        final float zF0 = 8.48f, zB0 = 7.52f, zApex = 8.00f;

        BorderNode first = BORDER_NODES.get(0);
        int cA0 = computeNodeColor(first.wavePhase, timeFactor, 0);
        int cA1 = computeNodeColor(first.wavePhase, timeFactor, 1);
        int cA2 = computeNodeColor(first.wavePhase, timeFactor, 2);

        for (int i = 0; i < n; i++) {
            BorderNode a = BORDER_NODES.get(i);
            BorderNode b = BORDER_NODES.get((i + 1) % n);

            int cB0 = computeNodeColor(b.wavePhase, timeFactor, 0);
            int cB1 = computeNodeColor(b.wavePhase, timeFactor, 1);
            int cB2 = computeNodeColor(b.wavePhase, timeFactor, 2);

            // 1. Front Inner Bevel (Ring 0 Front -> Ring 1 Volumetric Crest Front)
            emitBorderQuad(consumer, pose,
                a.x0, a.y0, zF0,   cA0,
                b.x0, b.y0, zF0,   cB0,
                b.x1, b.y1, b.zF1, cB1,
                a.x1, a.y1, a.zF1, cA1,
                a.innerNx, a.innerNy, a.innerNz, overlay, borderLight);

            // 2. Front Outer Crystal Blade (Ring 1 Crest Front -> Ring 2 Sharp Apex at z = 8.00)
            emitBorderQuad(consumer, pose,
                a.x1, a.y1, a.zF1, cA1,
                b.x1, b.y1, b.zF1, cB1,
                b.x2, b.y2, zApex, cB2,
                a.x2, a.y2, zApex, cA2,
                a.outerNx, a.outerNy, a.outerNz, overlay, borderLight);

            // 3. Back Outer Crystal Blade (Ring 2 Sharp Apex at z = 8.00 -> Ring 1 Crest Back)
            emitBorderQuad(consumer, pose,
                a.x2, a.y2, zApex, cA2,
                b.x2, b.y2, zApex, cB2,
                b.x1, b.y1, b.zB1, cB1,
                a.x1, a.y1, a.zB1, cA1,
                a.outerNx, a.outerNy, -a.outerNz, overlay, borderLight);

            // 4. Back Inner Bevel (Ring 1 Crest Back -> Ring 0 Back)
            emitBorderQuad(consumer, pose,
                a.x1, a.y1, a.zB1, cA1,
                b.x1, b.y1, b.zB1, cB1,
                b.x0, b.y0, zB0,   cB0,
                a.x0, a.y0, zB0,   cA0,
                a.innerNx, a.innerNy, -a.innerNz, overlay, borderLight);

            cA0 = cB0;
            cA1 = cB1;
            cA2 = cB2;
        }
    }

    private static void emitBorderQuad(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float x0, float y0, float z0, int c0,
        float x1, float y1, float z1, int c1,
        float x2, float y2, float z2, int c2,
        float x3, float y3, float z3, int c3,
        float nx, float ny, float nz,
        int overlay,
        int light
    ) {
        consumer.addVertex(pose, x0, y0, z0).setColor(c0).setUv(0.5f, 0.5f).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x1, y1, z1).setColor(c1).setUv(0.5f, 0.5f).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x2, y2, z2).setColor(c2).setUv(0.5f, 0.5f).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x3, y3, z3).setColor(c3).setUv(0.5f, 0.5f).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }

    public static void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, boolean isSlim) {
        render(pose, consumer, light, overlay, isSlim, 0xFFFFFFFF);
    }

    public static void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, boolean isSlim, int color) {
        ensureLoaded();
        List<Quad> quads = isSlim ? QUADS_SLIM : QUADS_CLASSIC;

        for (Quad q : quads) {
            putVertex(consumer, pose, q.v0, color, overlay, light, q.nx, q.ny, q.nz);
            putVertex(consumer, pose, q.v1, color, overlay, light, q.nx, q.ny, q.nz);
            putVertex(consumer, pose, q.v2, color, overlay, light, q.nx, q.ny, q.nz);
            putVertex(consumer, pose, q.v3, color, overlay, light, q.nx, q.ny, q.nz);
        }
    }

    private static void putVertex(VertexConsumer consumer, PoseStack.Pose pose, Vertex v, int color, int overlay, int light, float nx, float ny, float nz) {
        consumer.addVertex(pose, v.x, v.y, v.z)
                .setColor(color)
                .setUv(v.u, v.v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
