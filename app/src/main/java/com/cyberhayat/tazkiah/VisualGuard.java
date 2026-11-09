package com.cyberhayat.tazkiah;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

/**
 * Image guard: checks a downscaled copy of the screen with a small on-device model (NudeNet 320n, ONNX).
 * Pixels never leave the phone and nothing is stored; the bitmap is thrown away right after scoring.
 */
final class VisualGuard {
    private static final int N = 320;
    // NudeNet class ids
    private static final int[] EXPLICIT = {2, 3, 4, 6, 14};   // buttocks, female breast, female genitalia, anus, male genitalia (exposed)
    private static final int[] SKIN = {13, 11};               // belly, armpits (exposed)
    private static final int[] FEMALE = {1, 16, 17, 0};       // female face, breast/buttocks/genitalia (covered)

    static volatile String lastError = "";
    static volatile long lastInferMs = 0;

    private OrtEnvironment env;
    private OrtSession session;
    private String inName;
    private boolean failed;
    private long lastSig;

    private final Bitmap tile = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
    private final Canvas canvas = new Canvas(tile);
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int[] px = new int[N * N];

    synchronized boolean load(Context c) {
        if (session != null) return true;
        if (failed) return false;
        try {
            InputStream is = c.getAssets().open("models/nudenet-320n.onnx");
            ByteArrayOutputStream bo = new ByteArrayOutputStream(13 * 1024 * 1024);
            byte[] b = new byte[65536];
            int n;
            while ((n = is.read(b)) > 0) bo.write(b, 0, n);
            is.close();
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions o = new OrtSession.SessionOptions();
            o.setIntraOpNumThreads(2);
            session = env.createSession(bo.toByteArray(), o);
            inName = session.getInputNames().iterator().next();
            lastError = "";
            return true;
        } catch (Throwable t) {
            failed = true;
            lastError = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            return false;
        }
    }

    synchronized void close() {
        try { if (session != null) session.close(); } catch (Throwable ignored) { }
        session = null;
    }

    /**
     * @param suggestive also react to bare midriff/armpits on a woman (strict + hyper modes)
     * @param tallTiles  how many overlapping tiles to use on a tall phone screen (2 or 3)
     * @return risk 0..1, or -1 when the screen is unchanged or the model is unavailable
     */
    synchronized float analyze(Bitmap full, boolean suggestive, int tallTiles) {
        if (session == null || full == null) return -1f;
        int w = full.getWidth(), h = full.getHeight();
        Rect[] tiles;
        if (h > w * 1.3f) {
            if (tallTiles >= 3) {
                int th = (int) (h * 0.42f);
                int mid = (int) (h * 0.29f);
                tiles = new Rect[]{new Rect(0, 0, w, th), new Rect(0, mid, w, mid + th), new Rect(0, h - th, w, h)};
            } else {
                int th = (int) (h * 0.55f);
                tiles = new Rect[]{new Rect(0, 0, w, th), new Rect(0, h - th, w, h)};
            }
        } else {
            tiles = new Rect[]{new Rect(0, 0, w, h)};
        }
        final int plane = N * N;
        float[][] data = new float[tiles.length][3 * plane];
        long sig = 17;
        for (int t = 0; t < tiles.length; t++) {
            Rect r = tiles[t];
            float sc = N / (float) Math.max(r.width(), r.height());
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(full, r, new RectF(0, 0, r.width() * sc, r.height() * sc), paint);
            tile.getPixels(px, 0, N, 0, 0, N, N);
            for (int i = 0; i < px.length; i += 197) sig = sig * 31 + px[i];
            float[] d = data[t];
            for (int i = 0; i < plane; i++) {
                int p = px[i];
                d[i] = ((p >> 16) & 255) / 255f;
                d[plane + i] = ((p >> 8) & 255) / 255f;
                d[2 * plane + i] = (p & 255) / 255f;
            }
        }
        if (sig == lastSig) return -1f;
        lastSig = sig;
        long t0 = System.nanoTime();
        float best = -1f;
        for (float[] d : data) {
            float s = run(d, suggestive);
            if (s > best) best = s;
        }
        lastInferMs = (System.nanoTime() - t0) / 1000000L;
        return best;
    }

    private float run(float[] data, boolean suggestive) {
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), new long[]{1, 3, N, N});
             OrtSession.Result res = session.run(Collections.singletonMap(inName, tensor))) {
            float[][] o = ((float[][][]) res.get(0).getValue())[0]; // [22][2100]
            float risk = 0f;
            for (int c : EXPLICIT) risk = Math.max(risk, max(o[4 + c]));
            if (suggestive) {
                float female = 0f, skin = 0f;
                for (int c : FEMALE) female = Math.max(female, max(o[4 + c]));
                for (int c : SKIN) skin = Math.max(skin, max(o[4 + c]));
                if (female >= 0.40f) risk = Math.max(risk, skin * 0.92f); // bare midriff / armpits with a woman in frame
            }
            return risk;
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
            return -1f;
        }
    }

    private static float max(float[] a) {
        float m = 0f;
        for (float v : a) if (v > m) m = v;
        return m;
    }
}
