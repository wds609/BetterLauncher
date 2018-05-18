package com.better.launcher.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.gesture.Gesture;
import android.gesture.GesturePoint;
import android.gesture.GestureStroke;
import android.gesture.GestureUtils;
import android.gesture.OrientedBoundingBox;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.android.launcher3.R;

import java.util.ArrayList;


public class GestureView extends FrameLayout {

    public static final int GESTURE_STROKE_TYPE_SINGLE = 0;


    private static final int FADE_ANIMATION_RATE = 16;
    private static final boolean GESTURE_RENDERING_ANTIALIAS = true;
    private static final boolean DITHER_FLAG = true;

    private final Paint mGesturePaint = new Paint();

    private long mFadeDuration = 150;
    private long mFadeOffset = 420;
    private long mFadingStart;
    private boolean mFadingHasStarted;
    private boolean mFadeEnabled = true;

    private int mCurrentColor;
    private float mGestureStrokeWidth = 30.0f;
    private int mInvalidateExtraBorder = 10;

    private int mGestureStrokeType = GESTURE_STROKE_TYPE_SINGLE;


    private float mGestureStrokeLengthThreshold = 80.0f;
    private float mGestureStrokeSquarenessTreshold = 0.2f;


    private final Rect mInvalidRect = new Rect();
    private final Path mPath = new Path();
    private boolean mGestureVisible = true;

    private float mX;
    private float mY;

    private float mCurveEndX;
    private float mCurveEndY;

    private float mTotalLength;
    private boolean mIsValidGesture = false;
    private boolean mPreviousWasGesturing = false;
    private boolean mInterceptEvents = true;
    private boolean mIsListeningForGestures;
    private boolean mResetGesture;

    // current gesture
    private Gesture mCurrentGesture;
    private final ArrayList<GesturePoint> mStrokeBuffer = new ArrayList<GesturePoint>(100);

    private final ArrayList<OnGestureListener> mOnGestureListeners =
            new ArrayList<OnGestureListener>();
    private final ArrayList<OnGesturePerformedListener> mOnGesturePerformedListeners =
            new ArrayList<OnGesturePerformedListener>();

    private boolean mHandleGestureActions;

    // fading out effect
    private boolean mIsFadingOut = false;
    private float mFadingAlpha = 1.0f;
    private final AccelerateDecelerateInterpolator mInterpolator =
            new AccelerateDecelerateInterpolator();

    private GestureControler mControler;
    private final FadeOutRunnable mFadingOut = new FadeOutRunnable();


    public GestureView(Context context) {
        super(context);
        init();
    }

    public GestureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public GestureView(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.GestureView, defStyleAttr, defStyleRes);
        mFadeEnabled = a.getBoolean(R.styleable.GestureView_fadeEnabled,
                mFadeEnabled);
        mCurrentColor = a.getColor(R.styleable.GestureView_gestureColor, context.getColor(R.color.gesture_color));
        a.recycle();

        init();
    }

    private void init() {
        setWillNotDraw(false);

        final Paint gesturePaint = mGesturePaint;
        gesturePaint.setAntiAlias(GESTURE_RENDERING_ANTIALIAS);
        gesturePaint.setColor(mCurrentColor);
        gesturePaint.setStyle(Paint.Style.STROKE);
        gesturePaint.setStrokeJoin(Paint.Join.ROUND);
        gesturePaint.setStrokeCap(Paint.Cap.ROUND);
        gesturePaint.setStrokeWidth(mGestureStrokeWidth);
        gesturePaint.setDither(DITHER_FLAG);

        setPaintAlpha(255);
    }


    public Gesture getGesture() {
        return mCurrentGesture;
    }

    public void setGesture(Gesture gesture) {
        if (mCurrentGesture != null) {
            clear(false);
        }

        mCurrentGesture = gesture;

        final Path path = mCurrentGesture.toPath();
        final RectF bounds = new RectF();
        path.computeBounds(bounds, true);

        mPath.rewind();
        mPath.addPath(path, -bounds.left + (getWidth() - bounds.width()) / 2.0f,
                -bounds.top + (getHeight() - bounds.height()) / 2.0f);

        mResetGesture = true;

        invalidate();
    }


    public void addOnGestureListener(GestureView.OnGestureListener listener) {
        mOnGestureListeners.add(listener);
    }

    public void removeOnGestureListener(GestureView.OnGestureListener listener) {
        mOnGestureListeners.remove(listener);
    }

    public void removeAllOnGestureListeners() {
        mOnGestureListeners.clear();
    }

    public void addOnGesturePerformedListener(GestureView.OnGesturePerformedListener listener) {
        mOnGesturePerformedListeners.add(listener);
        if (mOnGesturePerformedListeners.size() > 0) {
            mHandleGestureActions = true;
        }
    }

    public void removeOnGesturePerformedListener(GestureView.OnGesturePerformedListener listener) {
        mOnGesturePerformedListeners.remove(listener);
        if (mOnGesturePerformedListeners.size() <= 0) {
            mHandleGestureActions = false;
        }
    }

    public void removeAllOnGesturePerformedListeners() {
        mOnGesturePerformedListeners.clear();
        mHandleGestureActions = false;
    }


    public boolean isValidGesture() {
        return mIsValidGesture;
    }

    private void setCurrentColor(int color) {
        mCurrentColor = color;
        if (mFadingHasStarted) {
            setPaintAlpha((int) (255 * mFadingAlpha));
        } else {
            setPaintAlpha(255);
        }
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mCurrentGesture != null && mGestureVisible && isValidGesture()) {
            canvas.drawPath(mPath, mGesturePaint);
        }
    }

    private void setPaintAlpha(int alpha) {
        alpha += alpha >> 7;
        final int baseAlpha = mCurrentColor >>> 24;
        final int useAlpha = baseAlpha * alpha >> 8;
        mGesturePaint.setColor((mCurrentColor << 8 >>> 8) | (useAlpha << 24));
    }

    public void clear(boolean animated) {
        clear(animated, false, true);
    }

    private void clear(boolean animated, boolean fireActionPerformed, boolean immediate) {
        setPaintAlpha(255);
        removeCallbacks(mFadingOut);
        mResetGesture = false;
        mFadingOut.fireActionPerformed = fireActionPerformed;
        mFadingOut.resetMultipleStrokes = false;

        if (animated && mCurrentGesture != null) {
            mFadingAlpha = 1.0f;
            mIsFadingOut = true;
            mFadingHasStarted = false;
            mFadingStart = AnimationUtils.currentAnimationTimeMillis() + mFadeOffset;

            postDelayed(mFadingOut, mFadeOffset);
        } else {
            mFadingAlpha = 1.0f;
            mIsFadingOut = false;
            mFadingHasStarted = false;

            if (immediate) {
                mCurrentGesture = null;
                mPath.rewind();
                invalidate();
            } else if (fireActionPerformed) {
                postDelayed(mFadingOut, mFadeOffset);
            } else {
                mCurrentGesture = null;
                mPath.rewind();
                invalidate();
            }
        }
    }

    public void cancelClearAnimation() {
        setPaintAlpha(255);
        mIsFadingOut = false;
        mFadingHasStarted = false;
        removeCallbacks(mFadingOut);
        mPath.rewind();
        mCurrentGesture = null;
    }

    public void cancelGesture() {
        mIsListeningForGestures = false;

        // add the stroke to the current gesture
        mCurrentGesture.addStroke(new GestureStroke(mStrokeBuffer));

        // pass the event to handlers
        final long now = SystemClock.uptimeMillis();
        final MotionEvent event = MotionEvent.obtain(now, now,
                MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);

        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureCancelled(this, event);
        }

        event.recycle();

        clear(false);
        mIsValidGesture = false;
        mPreviousWasGesturing = false;
        mStrokeBuffer.clear();

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelClearAnimation();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (isEnabled() && (mControler == null || mControler != null && mControler.isGesutreEnabled())) {
            final boolean cancelDispatch = (mIsValidGesture || (mCurrentGesture != null &&
                    mCurrentGesture.getStrokesCount() > 0 && mPreviousWasGesturing)) &&
                    mInterceptEvents;

            processEvent(event);

            if (cancelDispatch) {
                event.setAction(MotionEvent.ACTION_CANCEL);
            }

            super.dispatchTouchEvent(event);

            return true;
        }

        return super.dispatchTouchEvent(event);
    }

    private boolean processEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDown(event);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mIsListeningForGestures) {
                    Rect rect = touchMove(event);
                    if (rect != null) {
                        invalidate(rect);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mIsListeningForGestures) {
                    touchUp(event, false);
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                if (mIsListeningForGestures) {
                    touchUp(event, true);
                    invalidate();
                    return true;
                }
        }

        return false;
    }

    private void touchDown(MotionEvent event) {
        mIsListeningForGestures = true;

        float x = event.getX();
        float y = event.getY();

        mX = x;
        mY = y;

        mTotalLength = 0;
        mIsValidGesture = false;

        if (mGestureStrokeType == GESTURE_STROKE_TYPE_SINGLE || mResetGesture) {
            mResetGesture = false;
            mCurrentGesture = null;
            mPath.rewind();
        }
        // if there is fading out going on, stop it.
        if (mFadingHasStarted) {
            cancelClearAnimation();
        } else if (mIsFadingOut) {
            setPaintAlpha(255);
            mIsFadingOut = false;
            mFadingHasStarted = false;
            removeCallbacks(mFadingOut);
        }

        if (mCurrentGesture == null) {
            mCurrentGesture = new Gesture();
        }

        mStrokeBuffer.add(new GesturePoint(x, y, event.getEventTime()));
        mPath.moveTo(x, y);

        final int border = mInvalidateExtraBorder;
        mInvalidRect.set((int) x - border, (int) y - border, (int) x + border, (int) y + border);

        mCurveEndX = x;
        mCurveEndY = y;

        // pass the event to handlers
        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        final int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureStarted(this, event);
        }
    }

    static final float TOUCH_TOLERANCE = 3;

    private Rect touchMove(MotionEvent event) {
        Rect areaToRefresh = null;

        final float x = event.getX();
        final float y = event.getY();

        final float previousX = mX;
        final float previousY = mY;

        final float dx = Math.abs(x - previousX);
        final float dy = Math.abs(y - previousY);

        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            areaToRefresh = mInvalidRect;

            // start with the curve end
            final int border = mInvalidateExtraBorder;
            areaToRefresh.set((int) mCurveEndX - border, (int) mCurveEndY - border,
                    (int) mCurveEndX + border, (int) mCurveEndY + border);

            float cX = mCurveEndX = (x + previousX) / 2;
            float cY = mCurveEndY = (y + previousY) / 2;

            mPath.quadTo(previousX, previousY, cX, cY);

            // union with the control point of the new curve
            areaToRefresh.union((int) previousX - border, (int) previousY - border,
                    (int) previousX + border, (int) previousY + border);

            // union with the end point of the new curve
            areaToRefresh.union((int) cX - border, (int) cY - border,
                    (int) cX + border, (int) cY + border);

            mX = x;
            mY = y;

            mStrokeBuffer.add(new GesturePoint(x, y, event.getEventTime()));

            if (!mIsValidGesture) {
                mTotalLength += (float) Math.hypot(dx, dy);

                if (mTotalLength > mGestureStrokeLengthThreshold) {
                    final OrientedBoundingBox box =
                            GestureUtils.computeOrientedBoundingBox(mStrokeBuffer);

                    if (box.squareness > mGestureStrokeSquarenessTreshold) {
                        mIsValidGesture = true;
                    }
                }
            }

            // pass the event to handlers
            final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
            final int count = listeners.size();
            for (int i = 0; i < count; i++) {
                listeners.get(i).onGesture(this, event);
            }
        }
        return areaToRefresh;
    }

    private void touchUp(MotionEvent event, boolean cancel) {
        mIsListeningForGestures = false;

        // A gesture wasn't started or was cancelled
        if (mCurrentGesture != null) {
            // add the stroke to the current gesture
            mCurrentGesture.addStroke(new GestureStroke(mStrokeBuffer));

            if (!cancel) {
                // pass the event to handlers
                final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
                int count = listeners.size();
                for (int i = 0; i < count; i++) {
                    listeners.get(i).onGestureEnded(this, event);
                }
                if (mFadeEnabled) {
                    clear(true, mHandleGestureActions && mIsValidGesture,
                            false);
                }
            } else {
                cancelGesture(event);

            }
        } else {
            cancelGesture(event);
        }
        mStrokeBuffer.clear();
        mPreviousWasGesturing = mIsValidGesture;
    }

    private void cancelGesture(MotionEvent event) {
        // pass the event to handlers
        final ArrayList<OnGestureListener> listeners = mOnGestureListeners;
        final int count = listeners.size();
        for (int i = 0; i < count; i++) {
            listeners.get(i).onGestureCancelled(this, event);
        }

        clear(false);
    }

    private void fireOnGesturePerformed() {
        final ArrayList<OnGesturePerformedListener> actionListeners = mOnGesturePerformedListeners;
        final int count = actionListeners.size();
        for (int i = 0; i < count; i++) {
            actionListeners.get(i).onGesturePerformed(this, mCurrentGesture);
        }
    }

    private class FadeOutRunnable implements Runnable {
        boolean fireActionPerformed;
        boolean resetMultipleStrokes;

        public void run() {
            if (mIsFadingOut) {
                final long now = AnimationUtils.currentAnimationTimeMillis();
                final long duration = now - mFadingStart;

                if (duration > mFadeDuration) {
                    if (fireActionPerformed) {
                        fireOnGesturePerformed();
                    }

                    mPreviousWasGesturing = false;
                    mIsFadingOut = false;
                    mFadingHasStarted = false;
                    mPath.rewind();
                    mCurrentGesture = null;
                    setPaintAlpha(255);
                } else {
                    mFadingHasStarted = true;
                    float interpolatedTime = Math.max(0.0f,
                            Math.min(1.0f, duration / (float) mFadeDuration));
                    mFadingAlpha = 1.0f - mInterpolator.getInterpolation(interpolatedTime);
                    setPaintAlpha((int) (255 * mFadingAlpha));
                    postDelayed(this, FADE_ANIMATION_RATE);
                }
            } else if (resetMultipleStrokes) {
                mResetGesture = true;
            } else {
                fireOnGesturePerformed();

                mFadingHasStarted = false;
                mPath.rewind();
                mCurrentGesture = null;
                mPreviousWasGesturing = false;
                setPaintAlpha(255);
            }

            invalidate();
        }
    }

    public static interface OnGestureListener {
        void onGestureStarted(GestureView overlay, MotionEvent event);

        void onGesture(GestureView overlay, MotionEvent event);

        void onGestureEnded(GestureView overlay, MotionEvent event);

        void onGestureCancelled(GestureView overlay, MotionEvent event);
    }

    public static interface OnGesturePerformedListener {
        void onGesturePerformed(GestureView overlay, Gesture gesture);
    }

    public interface GestureControler {
        public boolean isGesutreEnabled();
    }

    public void setGestureControler(GestureControler controler) {
        mControler = controler;
    }

}


