package org.client.scrcpy;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class DisplayWindow extends FrameLayout {
    private static final String TAG = "DisplayWindow";
    OnClickListener closeListener;
    OnMoveCallback moveCallback;
    OnActionCallback actionCallback;
    OnTouchListener onDisplayTouchListener;

    private float oldX;
    private float oldY;
    private float userScale = 1f;
    private static final float MIN_SCALE = 0.3f;
    private static final float MAX_SCALE = 3.0f;
    private int baseContainerWidth;
    private int baseContainerHeight;
    private ScaleGestureDetector scaleGestureDetector;
    private boolean scaling;
    private boolean forwardingTouchToRemote;

    ViewGroup header;
    ViewGroup container;
    SurfaceView surfaceView;
    ViewGroup actionbar;
    private int remoteWidth;
    private int remoteHeight;

    public DisplayWindow(Context context) {
        super(context);
        init();
    }

    public DisplayWindow(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DisplayWindow(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init(){
        setClipChildren(false);
        setClipToPadding(false);
        LayoutInflater.from(getContext()).inflate(R.layout.window_display,this,true);

        header = findViewById(R.id.header);
        container = findViewById(R.id.container);
        surfaceView = findViewById(R.id.surface);
        actionbar = findViewById(R.id.actionbar);

        scaleGestureDetector = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                setUserScale(userScale * detector.getScaleFactor());
                return true;
            }

            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                scaling = true;
                dispatchCancelToRemote();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                scaling = false;
            }
        });

        findViewById(R.id.iv_close).setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN){
                    closeListener.onClick(view);
                }
                return false;
            }
        });

        header.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                final int action = motionEvent.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        oldX = motionEvent.getRawX();
                        oldY = motionEvent.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float disX = motionEvent.getRawX()-oldX;
                        float disY = motionEvent.getRawY()-oldY;
                        moveCallback.onMove(disX,disY);
                        oldX = motionEvent.getRawX();
                        oldY = motionEvent.getRawY();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:

                        break;
                }
                return true;
            }
        });
        findViewById(R.id.iv_mini).setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN){
                    if (container.isShown()){
                        container.setVisibility(View.GONE);
                        actionbar.setVisibility(View.GONE);
                    }else{
                        container.setVisibility(View.VISIBLE);
                        actionbar.setVisibility(View.VISIBLE);
                        post(DisplayWindow.this::applyCurrentSize);
                    }
                }
                return false;
            }
        });
        findViewById(R.id.action_back).setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN){
                    actionCallback.onAction(0);
                }
                return false;
            }
        });
        findViewById(R.id.action_home).setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN){
                    actionCallback.onAction(1);
                }
                return false;
            }
        });
        findViewById(R.id.action_menu).setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN){
                    actionCallback.onAction(2);
                }
                return false;
            }
        });
        container.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                scaleGestureDetector.onTouchEvent(motionEvent);
                if (scaling) {
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP
                            || motionEvent.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        scaling = false;
                    }
                    return true;
                }
                if (onDisplayTouchListener == null) {
                    return false;
                }
                boolean handled = onDisplayTouchListener.onTouch(view, motionEvent);
                if (handled) {
                    forwardingTouchToRemote = motionEvent.getActionMasked() != MotionEvent.ACTION_UP
                            && motionEvent.getActionMasked() != MotionEvent.ACTION_CANCEL;
                }
                return handled;
            }
        });
    }

    public void setCloseListener(OnClickListener closeListener) {
        this.closeListener = closeListener;
    }

    public void setMoveCallback(OnMoveCallback moveCallback) {
        this.moveCallback = moveCallback;
    }

    public void setActionCallback(OnActionCallback actionCallback) {
        this.actionCallback = actionCallback;
    }

    public void setOnDisplayTouchListener(OnTouchListener onDisplayTouchListener) {
        this.onDisplayTouchListener = onDisplayTouchListener;
    }

    public SurfaceView getSurfaceView() {
        return surfaceView;
    }

    public void setRemote(int w,int h){
        remoteWidth = w;
        remoteHeight = h;
        Log.d(TAG, "setRemote: " + remoteWidth + "," + remoteHeight);
        calculateBaseSize();
        post(this::applyCurrentSize);

    }

    public void hideHintTip(){
        findViewById(R.id.hint).setVisibility(GONE);
    }

    public Surface getDisplaySurface(){
        return surfaceView.getHolder().getSurface();
    }

    public int getSurfaceWidth(){
        return container.getMeasuredWidth();
    }

    public int getSurfaceHeight(){
        return container.getMeasuredHeight();
    }

    private void calculateBaseSize() {
        if (remoteWidth <= 0 || remoteHeight <= 0) {
            baseContainerWidth = 0;
            baseContainerHeight = 0;
            return;
        }

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int maxWidth = Math.max(dpToPx(160), (int) (screenWidth * 0.8f));
        int maxHeight = Math.max(dpToPx(160), (int) (screenHeight * 0.8f));

        float ratio = (float) remoteWidth / (float) remoteHeight;

        int widthCandidate = maxWidth;
        int heightCandidate = Math.round(widthCandidate / ratio);

        if (heightCandidate > maxHeight) {
            heightCandidate = maxHeight;
            widthCandidate = Math.round(heightCandidate * ratio);
        }

        int minWidth = dpToPx(160);
        int minHeight = dpToPx(200);

        if (widthCandidate < minWidth) {
            widthCandidate = minWidth;
            heightCandidate = Math.round(widthCandidate / ratio);
        }

        if (heightCandidate < minHeight) {
            heightCandidate = minHeight;
            widthCandidate = Math.round(heightCandidate * ratio);
        }

        baseContainerWidth = Math.max(1, widthCandidate);
        baseContainerHeight = Math.max(1, heightCandidate);
    }

    private void applyCurrentSize() {
        if (remoteWidth <= 0 || remoteHeight <= 0) {
            return;
        }

        if (baseContainerWidth <= 0 || baseContainerHeight <= 0) {
            calculateBaseSize();
        }

        float clampedScale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, userScale));
        if (clampedScale != userScale) {
            userScale = clampedScale;
        }

        int targetWidth = Math.max(1, Math.round(baseContainerWidth * userScale));
        int targetHeight = Math.max(1, Math.round(baseContainerHeight * userScale));

        ViewGroup.LayoutParams contentLp = container.getLayoutParams();
        if (contentLp.width != targetWidth || contentLp.height != targetHeight) {
            contentLp.width = targetWidth;
            contentLp.height = targetHeight;
            container.setLayoutParams(contentLp);
        }

        ViewGroup.LayoutParams headerLp = header.getLayoutParams();
        if (headerLp.width != targetWidth) {
            headerLp.width = targetWidth;
            header.setLayoutParams(headerLp);
        }

        ViewGroup.LayoutParams actionLp = actionbar.getLayoutParams();
        if (actionLp.width != targetWidth) {
            actionLp.width = targetWidth;
            actionbar.setLayoutParams(actionLp);
        }

        requestLayout();
    }

    private void setUserScale(float scale) {
        float clamped = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
        if (Math.abs(clamped - userScale) < 0.001f) {
            return;
        }
        userScale = clamped;
        applyCurrentSize();
    }

    private void dispatchCancelToRemote() {
        if (!forwardingTouchToRemote || onDisplayTouchListener == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        onDisplayTouchListener.onTouch(container, cancel);
        cancel.recycle();
        forwardingTouchToRemote = false;
    }

    private int dpToPx(int dp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return Math.round(dp * metrics.density);
    }

    public interface OnMoveCallback {
        void onMove(float x,float y);
    }
    public interface OnActionCallback{
        void onAction(int actionType);
    }
}
