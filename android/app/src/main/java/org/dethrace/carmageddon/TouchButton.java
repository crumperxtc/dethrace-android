package org.dethrace.carmageddon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import org.libsdl.app.SDLActivity;

/**
 * Semi-transparent on-screen button that holds down one or more Android key
 * codes while touched. Shows an icon (with an optional small caption) or a
 * text label. Each finger is tracked on its own, so several buttons can be
 * held at once.
 */
public class TouchButton extends View {
    private final Icons.Icon icon;
    private final String label;
    private final int[] keycodes;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final RectF iconBox = new RectF();
    private boolean pressed;

    /** Text-only button. */
    public TouchButton(Context context, String label, int... keycodes) {
        this(context, null, label, keycodes);
    }

    /** Icon button; caption may be null. */
    public TouchButton(Context context, Icons.Icon icon, String caption, int... keycodes) {
        super(context);
        this.icon = icon;
        this.label = caption;
        this.keycodes = keycodes;
        float density = context.getResources().getDisplayMetrics().density;
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(2 * density);
        outline.setColor(0x99FFFFFF);
        iconStroke.setStyle(Paint.Style.STROKE);
        iconStroke.setStrokeWidth(2.5f * density);
        iconStroke.setStrokeJoin(Paint.Join.ROUND);
        iconStroke.setStrokeCap(Paint.Cap.ROUND);
        iconStroke.setColor(0xDDFFFFFF);
        iconFill.setStyle(Paint.Style.FILL);
        iconFill.setColor(0xDDFFFFFF);
        text.setColor(0xDDFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = outline.getStrokeWidth();
        bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
        float radius = Math.min(getWidth(), getHeight()) / 4f;
        fill.setColor(pressed ? 0x88FFFFFF : 0x33000000);
        canvas.drawRoundRect(bounds, radius, radius, fill);
        canvas.drawRoundRect(bounds, radius, radius, outline);

        if (icon == null) {
            fitText(getWidth(), getHeight() * 0.35f);
            canvas.drawText(label, getWidth() / 2f, (getHeight() - text.ascent() - text.descent()) / 2f, text);
            return;
        }

        float captionHeight = 0;
        if (label != null) {
            fitText(getWidth() * 0.9f, getHeight() * 0.13f);
            captionHeight = (text.descent() - text.ascent()) * 1.2f;
            canvas.drawText(label, getWidth() / 2f, getHeight() - inset * 2 - text.descent() - captionHeight * 0.1f, text);
        }
        float side = Math.min(getWidth(), getHeight() - captionHeight) * 0.7f;
        float cy = (getHeight() - captionHeight) / 2f;
        iconBox.set(getWidth() / 2f - side / 2, cy - side / 2, getWidth() / 2f + side / 2, cy + side / 2);
        icon.draw(canvas, iconBox, iconStroke, iconFill);
    }

    private void fitText(float width, float maxHeight) {
        text.setTextSize(Math.min(maxHeight, width / (label.length() * 0.62f + 0.5f)));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                setPressedKeys(true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setPressedKeys(false);
                return true;
            default:
                return true;
        }
    }

    void setPressedKeys(boolean down) {
        if (pressed == down) {
            return;
        }
        pressed = down;
        for (int keycode : keycodes) {
            if (down) {
                SDLActivity.onNativeKeyDown(keycode);
            } else {
                SDLActivity.onNativeKeyUp(keycode);
            }
        }
        invalidate();
    }
}
