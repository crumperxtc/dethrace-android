package org.dethrace.carmageddon;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * Full-screen container for the on-screen buttons. Touches that miss every
 * button land here: dragging swings/zooms the race camera, a double tap
 * puts it back behind the car, and everything is still passed on to the
 * game so the menus can be tapped.
 */
public class LookArea extends RelativeLayout {
    // Degrees of camera swing / zoom units per dp dragged
    private static final float YAW_PER_DP = 0.4f;
    private static final float ZOOM_PER_DP = 0.006f;

    public interface CameraListener {
        void onDrag(float yawDegrees, float zoom);
        void onReset();
    }

    private final View gameView;
    private final CameraListener listener;
    private final GestureDetector doubleTap;
    private final float density;
    private int pointerId = -1;
    private float lastX;
    private float lastY;

    public LookArea(Context context, View gameView, CameraListener listener) {
        super(context);
        this.gameView = gameView;
        this.listener = listener;
        density = context.getResources().getDisplayMetrics().density;
        doubleTap = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                LookArea.this.listener.onReset();
                return true;
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameView != null) {
            gameView.dispatchTouchEvent(event);
        }
        doubleTap.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerId == -1) {
                    int index = event.getActionIndex();
                    pointerId = event.getPointerId(index);
                    lastX = event.getX(index);
                    lastY = event.getY(index);
                }
                break;
            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(pointerId);
                if (index >= 0) {
                    float dx = (event.getX(index) - lastX) / density;
                    float dy = (event.getY(index) - lastY) / density;
                    lastX = event.getX(index);
                    lastY = event.getY(index);
                    listener.onDrag(dx * YAW_PER_DP, dy * ZOOM_PER_DP);
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointerId) {
                    pointerId = -1;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pointerId = -1;
                break;
        }
        return true;
    }
}
