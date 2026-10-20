package org.dethrace.carmageddon;

import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.RelativeLayout;

import org.libsdl.app.SDLActivity;

/**
 * SDLActivity with on-screen controls laid over the game.
 *
 * Driving uses Carmageddon's default keymap (numpad 4/6/8/2), which the menus
 * also accept. Don't send arrow keys: in a race they swing the camera.
 * Dragging on empty screen moves the camera instead (see LookArea).
 */
public class CarmageddonActivity extends SDLActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mLayout != null) {
            mLayout.addView(createControls(), new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        }
    }

    // Passed to SDL_main. The widescreen width comes from the real display size,
    // which native code can't rely on this early.
    @Override
    protected String[] getArguments() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int longSide = Math.max(metrics.widthPixels, metrics.heightPixels);
        int shortSide = Math.min(metrics.widthPixels, metrics.heightPixels);
        int width = 640;
        if (shortSide > 0) {
            width = Math.max(640, Math.min(1280, 480 * longSide / shortSide)) & ~7;
        }
        return new String[] { "--widescreen=" + width };
    }

    // Phone back button acts as Escape (pause menu / back out of menus)
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                onNativeKeyDown(KeyEvent.KEYCODE_ESCAPE);
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                onNativeKeyUp(KeyEvent.KEYCODE_ESCAPE);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // Implemented in libmain.so (android_main.c); ignored outside a race
    private static native void nativeCameraDrag(float yawDegrees, float zoom);
    private static native void nativeCameraReset();

    private RelativeLayout createControls() {
        RelativeLayout controls = new LookArea(this, mSurface, new LookArea.CameraListener() {
            @Override
            public void onDrag(float yawDegrees, float zoom) {
                nativeCameraDrag(yawDegrees, zoom);
            }

            @Override
            public void onReset() {
                nativeCameraReset();
            }
        });
        // The game's HUD sits in a centred 4:3 area; put the controls in the
        // strips either side of it (with a minimum width on less wide screens)
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int screenW = Math.max(metrics.widthPixels, metrics.heightPixels);
        int screenH = Math.min(metrics.widthPixels, metrics.heightPixels);
        int pad = dp(8);
        int side = Math.max((screenW - screenH * 4 / 3) / 2, dp(120));
        int colW = side - 2 * pad;
        int halfW = (colW - pad) / 2;
        int leftX = pad;
        int rightX = screenW - side + pad;

        // Left strip: menu keys, horn, steering
        int smallH = Math.min(dp(44), halfW);
        place(controls, new TouchButton(this, "ESC", KeyEvent.KEYCODE_ESCAPE), leftX, pad, halfW, smallH);
        place(controls, new TouchButton(this, "ENTER", KeyEvent.KEYCODE_ENTER), leftX + halfW + pad, pad, halfW, smallH);
        int hornH = Math.min(dp(64), colW / 2);
        place(controls, new TouchButton(this, Icons.HORN, null, KeyEvent.KEYCODE_H), leftX, pad * 2 + smallH, colW, hornH);

        int steerH = Math.min(screenH * 2 / 5, colW * 3 / 4);
        SteeringPad steering = new SteeringPad(this,
            new TouchButton(this, Icons.arrow(true), null, KeyEvent.KEYCODE_NUMPAD_4),
            new TouchButton(this, Icons.arrow(false), null, KeyEvent.KEYCODE_NUMPAD_6),
            pad);
        place(controls, steering, leftX, screenH - pad - steerH, colW, steerH);

        // Right strip: race actions (default keys Backspace, Insert, C, Tab), handbrake, pedals
        int cellH = Math.min(dp(60), halfW);
        place(controls, new TouchButton(this, Icons.WRENCH, "REPAIR", KeyEvent.KEYCODE_DEL), rightX, pad, halfW, cellH);
        place(controls, new TouchButton(this, Icons.RECOVER, "RECOVER", KeyEvent.KEYCODE_INSERT), rightX + halfW + pad, pad, halfW, cellH);
        place(controls, new TouchButton(this, Icons.CAMERA, "CAMERA", KeyEvent.KEYCODE_C), rightX, pad * 2 + cellH, halfW, cellH);
        place(controls, new TouchButton(this, Icons.MAP, "MAP", KeyEvent.KEYCODE_TAB), rightX + halfW + pad, pad * 2 + cellH, halfW, cellH);

        int pedalH = Math.min(screenH * 2 / 5, colW);
        int pedalY = screenH - pad - pedalH;
        place(controls, new TouchButton(this, Icons.pedal(true), "BRAKE", KeyEvent.KEYCODE_NUMPAD_2), rightX, pedalY, halfW, pedalH);
        place(controls, new TouchButton(this, Icons.pedal(false), "GAS", KeyEvent.KEYCODE_NUMPAD_8), rightX + halfW + pad, pedalY, halfW, pedalH);

        int handbrakeH = Math.min(dp(56), colW / 3);
        place(controls, new TouchButton(this, Icons.HANDBRAKE, null, KeyEvent.KEYCODE_SPACE), rightX, pedalY - pad - handbrakeH, colW, handbrakeH);

        return controls;
    }

    private static void place(RelativeLayout parent, android.view.View view, int x, int y, int width, int height) {
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(width, height);
        lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.setMargins(x, y, 0, 0);
        parent.addView(view, lp);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
