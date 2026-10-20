package org.dethrace.carmageddon;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/**
 * Left/right steering buttons sharing one touch area, so a thumb can slide
 * from one to the other without lifting.
 */
public class SteeringPad extends LinearLayout {
    private final TouchButton left;
    private final TouchButton right;
    private int pointerId = -1;

    public SteeringPad(Context context, TouchButton left, TouchButton right, int gap) {
        super(context);
        this.left = left;
        this.right = right;
        setOrientation(HORIZONTAL);
        LayoutParams lp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        lp.rightMargin = gap;
        addView(left, lp);
        addView(right, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerId == -1) {
                    pointerId = event.getPointerId(event.getActionIndex());
                    steer(event.getX(event.getActionIndex()));
                }
                break;
            case MotionEvent.ACTION_MOVE: {
                int index = event.findPointerIndex(pointerId);
                if (index >= 0) {
                    steer(event.getX(index));
                }
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == pointerId) {
                    release();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                release();
                break;
        }
        return true;
    }

    private void steer(float x) {
        boolean goLeft = x < getWidth() / 2f;
        // Release first so both directions are never held together
        (goLeft ? right : left).setPressedKeys(false);
        (goLeft ? left : right).setPressedKeys(true);
    }

    private void release() {
        pointerId = -1;
        left.setPressedKeys(false);
        right.setPressedKeys(false);
    }
}
