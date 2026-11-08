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
 * Image guard: looks at a downscaled copy of the screen with a small on-device model
 * (NudeNet 320n, ONNX). It only reports "exposed body part" detections. Pixels never leave the phone
 * and nothing is stored. Not for anything except the score; the bitmap is discarded right after.
 */
final class VisualGuard {
    private static final int N = 320;
    // NudeNet class ids
    private static final int[] EXPLICIT = {2, 3, 4, 6, 14};  // buttocks, female breast, female genitalia, anus, male genitalia (exposed)
    private static final int[] STRICT_EXTRA = {11, 13};      // armpits, belly (exposed)

    private OrtEnvironment env;
    private OrtSession session;
    private String inName;
    private boolean failed;
    private long lastSig;

    private final Bitmap tile = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888);
    private final Canvas canvas = new Canvas(tile);
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final int[] px = new int[N * N];
    private final float[] in = new float[3 * N * N];

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
            return true;
        } catch (Throwable t) {
            failed = true;
            return false;
        }
    }

    synchronized void close() {
        try { if (session != null) session.close(); } catch (Throwable ignored) { }
        session = null;
    }

    /**
     * @return highest score (0..1) of an exposed-anatomy class, or -1 when the screen is unchanged / the model is unavailable.
     */
    synchronized float analyze(Bitmap full, boolean strict) {
        if (session == null || full == null) return -1f;
        int w = full.getWidth(), h = full.getHeight();
        Rect[] tiles;
        if (h > w * 1.3f) { // tall phone screen: two overlapping tiles keep faces/bodies large enough for the model
            int th = (int) (h * 0.55f);
            tiles = new Rect[]{new Rect(0, 0, w, th), new Rect(0, h - th, w, h)};
        } else {
            tiles = new Rect[]{new Rect(0, 0, w, h)};
        }
        float best = 0f;
        long sig = 17;
        boolean any = false;
        float[][] results = new float[tiles.length][];
        for (int t = 0; t < tiles.length; t++) {
            Rect r = tiles[t];
            float sc = N / (float) Math.max(r.width(), r.height());
            canvas.drawColor(Color.BLACK);
            canvas.drawBitmap(full, r, new RectF(0, 0, r.width() * sc, r.height() * sc), paint);
            tile.getPixels(px, 0, N, 0, 0, N, N);
            for (int i = 0; i < px.length; i += 211) sig = sig * 31 + px[i];
            results[t] = null;
            // fill CHW RGB 0..1
            final int plane = N * N;
            for (int i = 0; i < plane; i++) {
                int p = px[i];
                in[i] = ((p >> 16) & 255) / 255f;
                in[plane + i] = ((p >> 8) & 255) / 255f;
                in[2 * plane + i] = (p & 255) / 255f;
            }
            // unchanged screen? (checked after both tiles below) -> still need the pixels, so run lazily
            results[t] = in.clone();
        }
        if (sig == lastSig) return -1f;
        lastSig = sig;
        for (int t = 0; t < tiles.length; t++) {
            float s = run(results[t], strict);
            if (s >= 0) { any = true; if (s > best) best = s; }
        }
        return any ? best : -1f;
    }

    private float run(float[] data, boolean strict) {
        try (OnnxTensor tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), new long[]{1, 3, N, N});
             OrtSession.Result res = session.run(Collections.singletonMap(inName, tensor))) {
            float[][][] out = (float[][][]) res.get(0).getValue(); // [1][22][2100]
            float[][] o = out[0];
            float best = 0f;
            for (int c : EXPLICIT) best = Math.max(best, max(o[4 + c]));
            if (strict) for (int c : STRICT_EXTRA) best = Math.max(best, max(o[4 + c]));
            return best;
        } catch (Throwable t) {
            return -1f;
        }
    }

    private static float max(float[] a) {
        float m = 0f;
        for (float v : a) if (v > m) m = v;
        return m;
    }
}
