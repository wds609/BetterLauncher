package com.better.launcher.gesture;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.Prediction;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.R;

import java.net.URISyntaxException;
import java.util.ArrayList;

public class GestureHelper {
    private GestureLibrary mGestureLibrary;
    private static final String GESTURE_FILE = "gestures";
    private static final float SUCCESS_MIN_SCORE = 4.5F;
    //key is component string
    private ArrayMap<String, ShortcutGesture> mShortcutGestures = new ArrayMap<>();
    private Bitmap mGestureBg;

    private static GestureHelper ourInstance = null;

    public static GestureHelper getInstance() {
        if (ourInstance == null) {
            ourInstance = new GestureHelper();
        }
        return ourInstance;
    }

    private GestureHelper() {
    }

    public void init(Context context) {
        mGestureLibrary = GestureLibraries.fromPrivateFile(context, GESTURE_FILE);
        Drawable gestureBg = context.getDrawable(R.drawable.gesture_bg);
        if (gestureBg instanceof BitmapDrawable) {
            mGestureBg = ((BitmapDrawable) gestureBg).getBitmap();
        }
        loadGestures();
    }

    private void loadGestures() {
        mGestureLibrary.load();
        loadAllShorcutGestures();
    }

    public String recognize(Gesture gesture) {
        ArrayList<Prediction> result = mGestureLibrary.recognize(gesture);
        if (result != null && result.size() >= 1) {
            Prediction prediction = result.get(0);
            if (prediction.score >= SUCCESS_MIN_SCORE) {
                return prediction.name;
            }
        }
        return null;
    }

    public void addGesture(String key, Gesture gesture) {
        mGestureLibrary.addGesture(key, gesture);
        mGestureLibrary.save();
        mGestureLibrary.load();
        mShortcutGestures.put(key, new ShortcutGesture(ComponentName.unflattenFromString(key), gesture));
    }

    public void modifyGesture(String key, Gesture gesture) {
        removeGesture(key);
        addGesture(key, gesture);
    }

    public void removeGesture(String key) {
        mGestureLibrary.removeEntry(key);
        mGestureLibrary.save();
        mGestureLibrary.load();
        mShortcutGestures.remove(key);
    }

    public boolean isAssignedGesture(String key) {
        return mShortcutGestures.containsKey(key);
    }


    private void loadAllShorcutGestures() {
        for (String name : mGestureLibrary.getGestureEntries()) {
            if (!mShortcutGestures.containsKey(name)) {
                mShortcutGestures.put(name, new ShortcutGesture(ComponentName.unflattenFromString(name), mGestureLibrary.getGestures(name).get(0)));
            }
        }
    }

    public ArrayList<ShortcutGesture> getAllShortcutGestures() {
        return new ArrayList<>(mShortcutGestures.values());
    }

    public Bitmap getGestureThumbnail(ComponentName componentName) {
        ShortcutGesture shortcutGesture = mShortcutGestures.get(componentName.flattenToShortString());
        if (shortcutGesture != null) {
            return getGestureThumbnail(shortcutGesture.gesture);
        }
        return null;
    }

    public Bitmap getGestureThumbnail(Gesture gesture) {
        int gestureBgSize = mGestureBg.getWidth();
        final Bitmap bitmap = Bitmap.createBitmap(gestureBgSize, gestureBgSize,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(mGestureBg, 0, 0, null);
        Path path = gesture.toPath();

        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(5);

        final RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        int gestureThumbnailSize = (int) (gestureBgSize * 0.5);

        final float sx = gestureThumbnailSize / bounds.width();
        final float sy = gestureThumbnailSize / bounds.height();
        final float scale = sx > sy ? sy : sx;
        paint.setStrokeWidth(5.0f / scale);
        path.offset(-bounds.left + (gestureBgSize - gestureThumbnailSize) / 2 / scale,
                -bounds.top + (gestureBgSize - gestureThumbnailSize) / 2 / scale);
        canvas.scale(scale, scale);
        canvas.drawPath(path, paint);

        return bitmap;
    }

    public static class ShortcutGesture {
        public ComponentName name;
        public Gesture gesture;

        public ShortcutGesture(ComponentName name, Gesture gesture) {
            this.name = name;
            this.gesture = gesture;
        }
    }

}
