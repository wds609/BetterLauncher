/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.better.launcher.activities;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.gesture.Gesture;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.launcher3.R;
import com.better.launcher.gesture.GestureHelper;
import com.better.launcher.views.GestureView;

import java.util.ArrayList;

public class CreateGestureActivity extends Activity {
    private static final float LENGTH_THRESHOLD = 120.0f;

    private Gesture mGesture;
    private View mDoneButton;
    private String gestureName;
    private boolean mIsModify;
    private RecyclerView mGestureTips;

    public static void launchCreateGestureActivity(Context context, String gestureName, boolean isModify) {
        Intent intent = new Intent(context, CreateGestureActivity.class);
        intent.putExtra("gestureName", gestureName);
        intent.putExtra("isModify", isModify);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_gesture);

        mDoneButton = findViewById(R.id.done);
        mDoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addOrModifyGesture();
            }
        });

        final GestureView overlay = (GestureView) findViewById(R.id.gestures_overlay);
        findViewById(R.id.reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                overlay.clear(false);
            }
        });
        overlay.addOnGestureListener(new GesturesProcessor());
        gestureName = getIntent().getStringExtra("gestureName");
        mIsModify = getIntent().getBooleanExtra("isModify", false);
        mGestureTips = findViewById(R.id.gesture_tips);
        mGestureTips.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mGestureTips.setAdapter(new GestureTipsAdpater(this, GestureHelper.getInstance().getAllShortcutGestures()));
    }

    public static class GestureTipsAdpater extends RecyclerView.Adapter<GestureTipsAdpater.ViewHolder> {
        private ArrayList<GestureHelper.ShortcutGesture> mDatas;
        private Context mContext;

        public static class ViewHolder extends RecyclerView.ViewHolder {
            private TextView mShortcut;
            private ImageView mGestureIcon;

            public ViewHolder(View itemView) {
                super(itemView);
                mShortcut = itemView.findViewById(R.id.shortcut_view);
                mGestureIcon = itemView.findViewById(R.id.gesture_icon);
            }

            public TextView getShortcut() {
                return mShortcut;
            }

            public ImageView getGestureIcon() {
                return mGestureIcon;
            }

        }

        public GestureTipsAdpater(Context context, ArrayList<GestureHelper.ShortcutGesture> datas) {
            this.mDatas = datas;
            mContext = context;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            View v = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.gesture_shortcut, viewGroup, false);

            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder viewHolder, int i) {
            GestureHelper.ShortcutGesture shortcutGesture = mDatas.get(i);
            viewHolder.getGestureIcon().setImageBitmap(GestureHelper.getInstance().getGestureThumbnail(shortcutGesture.gesture));
            viewHolder.getShortcut().setCompoundDrawablesWithIntrinsicBounds(null, getIcon(shortcutGesture.name), null, null);
        }

        private Drawable getIcon(ComponentName name) {
            PackageManager pm = mContext.getPackageManager();
            try {
                return pm.getActivityIcon(name);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public int getItemCount() {
            return mDatas.size();
        }

    }

    public void addOrModifyGesture() {
        if (mGesture != null) {
            if (mIsModify) {
                GestureHelper.getInstance().modifyGesture(gestureName, mGesture);
            } else {
                GestureHelper.getInstance().addGesture(gestureName, mGesture);
            }
            setResult(RESULT_OK);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }


    private class GesturesProcessor implements GestureView.OnGestureListener {
        public void onGestureStarted(GestureView overlay, MotionEvent event) {
            mDoneButton.setEnabled(false);
            mGesture = null;
        }

        public void onGesture(GestureView overlay, MotionEvent event) {
        }

        public void onGestureEnded(GestureView overlay, MotionEvent event) {
            if (overlay.isValidGesture()) {
                mGesture = overlay.getGesture();
                mDoneButton.setEnabled(true);
            }

        }

        public void onGestureCancelled(GestureView overlay, MotionEvent event) {
        }
    }
}
