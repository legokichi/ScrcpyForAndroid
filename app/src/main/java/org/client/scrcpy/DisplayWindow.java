package org.client.scrcpy;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
import android.widget.FrameLayout;

public class DisplayWindow extends FrameLayout {
    private static final String TAG = "DisplayWindow";
    OnClickListener closeListener;
    OnMoveCallback moveCallback;
    OnActionCallback actionCallback;
    OnTouchListener onDisplayTouchListener;

    private float oldX;
    private float oldY;

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

        container = findViewById(R.id.container);
        surfaceView = findViewById(R.id.surface);
        actionbar = findViewById(R.id.actionbar);

        addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    post(DisplayWindow.this::applyRemoteSize);
                }
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

        header = findViewById(R.id.header);
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
                return onDisplayTouchListener.onTouch(view,motionEvent);
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
        post(this::applyRemoteSize);

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

    private void applyRemoteSize() {
        if (remoteWidth <= 0 || remoteHeight <= 0) {
            return;
        }

        int availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        int headerHeight = header.getMeasuredHeight();
        if (headerHeight <= 0) {
            header.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
            headerHeight = header.getMeasuredHeight();
        }

        int actionbarHeight = actionbar.getVisibility() == VISIBLE ? actionbar.getMeasuredHeight() : 0;
        if (actionbarHeight <= 0 && actionbar.getVisibility() == VISIBLE) {
            actionbar.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(availableHeight, MeasureSpec.AT_MOST));
            actionbarHeight = actionbar.getMeasuredHeight();
        }

        int maxContainerHeight = Math.max(0, availableHeight - headerHeight - actionbarHeight);
        int maxContainerWidth = availableWidth;
        if (maxContainerHeight == 0 || maxContainerWidth == 0) {
            return;
        }

        float ratio = (float) remoteWidth / (float) remoteHeight;
        int targetHeight = maxContainerHeight;
        int targetWidth = Math.round(targetHeight * ratio);

        if (targetWidth > maxContainerWidth) {
            targetWidth = maxContainerWidth;
            targetHeight = Math.round(targetWidth / ratio);
        }

        if (targetWidth <= 0 || targetHeight <= 0) {
            return;
        }

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

        requestLayout();
    }

    public interface OnMoveCallback {
        void onMove(float x,float y);
    }
    public interface OnActionCallback{
        void onAction(int actionType);
    }
}
