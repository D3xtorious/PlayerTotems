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

    public static void render(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, boolean isSlim) {
        ensureLoaded();
        int color = 0xFFFFFFFF;
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
