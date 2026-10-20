package org.dethrace.carmageddon;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Simple line-art icons for the on-screen controls, drawn into a square box.
 * stroke is an outline paint, fill a solid paint of the same colour.
 */
final class Icons {
    interface Icon {
        void draw(Canvas canvas, RectF box, Paint stroke, Paint fill);
    }

    private Icons() {
    }

    /** Pedal plate with grip ridges; wide for the brake, tall for the accelerator. */
    static Icon pedal(final boolean wide) {
        return new Icon() {
            @Override
            public void draw(Canvas canvas, RectF box, Paint stroke, Paint fill) {
                float w = box.width() * (wide ? 0.9f : 0.6f);
                float h = box.height() * (wide ? 0.6f : 0.95f);
                RectF plate = new RectF(box.centerX() - w / 2, box.centerY() - h / 2,
                    box.centerX() + w / 2, box.centerY() + h / 2);
                float r = Math.min(w, h) * 0.25f;
                canvas.drawRoundRect(plate, r, r, stroke);
                int ridges = wide ? 4 : 6;
                float inset = w * 0.2f;
                for (int i = 1; i <= ridges; i++) {
                    float y = plate.top + plate.height() * i / (ridges + 1);
                    canvas.drawLine(plate.left + inset, y, plate.right - inset, y, stroke);
                }
            }
        };
    }

    static Icon arrow(final boolean left) {
        return new Icon() {
            @Override
            public void draw(Canvas canvas, RectF box, Paint stroke, Paint fill) {
                float s = box.width() * 0.4f;
                float dir = left ? -1 : 1;
                Path p = new Path();
                p.moveTo(box.centerX() + dir * s, box.centerY());
                p.lineTo(box.centerX() - dir * s * 0.7f, box.centerY() - s);
                p.lineTo(box.centerX() - dir * s * 0.7f, box.centerY() + s);
                p.close();
                canvas.drawPath(p, fill);
            }
        };
    }

    /** Trumpet horn with sound waves. */
    static final Icon HORN = new Icon() {
        @Override
        public void draw(Canvas canvas, RectF box, Paint stroke, Paint fill) {
            float x = box.left, y = box.centerY(), w = box.width(), h = box.height();
            Path p = new Path();
            p.moveTo(x + w * 0.08f, y - h * 0.08f);
            p.lineTo(x + w * 0.28f, y - h * 0.08f);
            p.lineTo(x + w * 0.6f, y - h * 0.3f);
            p.lineTo(x + w * 0.6f, y + h * 0.3f);
            p.lineTo(x + w * 0.28f, y + h * 0.08f);
            p.lineTo(x + w * 0.08f, y + h * 0.08f);
            p.close();
            canvas.drawPath(p, fill);
            for (int i = 0; i < 2; i++) {
                float r = h * (0.18f + i * 0.14f);
                RectF arc = new RectF(x + w * 0.62f - r, y - r, x + w * 0.62f + r, y + r);
                canvas.drawArc(arc, -45, 90, false, stroke);
            }
        }
    };

    /** Spanner: open jaw at the top right, handle down to the bottom left. */
    static final Icon WRENCH = new Icon() {
        @Override
        public void draw(Canvas canvas, RectF box, Paint stroke, Paint fill) {
            float cx = box.centerX(), cy = box.centerY(), s = box.width() / 2;
            canvas.save();
            canvas.rotate(45, cx, cy);
            Paint thick = new Paint(stroke);
            thick.setStrokeWidth(s * 0.2f);
            thick.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(cx, cy - s * 0.2f, cx, cy + s * 0.85f, thick);
            // Head: a ring open at the top, which makes the jaw
            float head = s * 0.32f;
            RectF ring = new RectF(cx - head, cy - s * 0.5f - head, cx + head, cy - s * 0.5f + head);
            canvas.drawArc(ring, -50, 280, false, thick);
            canvas.restore();
        }
    };

    /** Circular arrow: put the car back on the track. */
    static final Icon RECOVER = new Icon() {
        @Override
        public void draw(Canvas canvas, RectF box, Paint stroke, Paint fill) {
            float r = box.width() * 0.32f;
            RectF ring = new RectF(box.centerX() - r, box.centerY() - r, box.centerX() + r, box.centerY() + r);
            canvas.drawArc(ring, -60, 300, false, stroke);
            // Arrowhead at the end of the arc (angle -60 degrees)
            double a = Math.toRadians(-60);
            float ex = (float) (box.centerX() + r * Math.cos(a));
            float ey = (float) (box.centerY() + r * Math.sin(a));
            float head = r * 0.55f;
            Path p = new Path();
            p.moveTo(ex + head * 0.6f, ey + head * 0.35f);
            p.lineTo(ex - head * 0.75f, ey - head * 0.1f);
            p.lineTo(ex + head * 0.2f, ey - head * 0.9f);
            p.close();
            canvas.drawPath(p, fill);
        }
    };

    static final Icon CAMERA = new Icon() {
        @Override
        public void draw(Canvas canvas, RectF box, Paint stroke, Paint fill) {
            float w = box.width() * 0.8f, h = box.height() * 0.52f;
            RectF body = new RectF(box.centerX() - w / 2, box.centerY() - h / 2 + h * 0.1f,
                box.centerX() + w / 2, box.centerY() + h / 2 + h * 0.1f);
            canvas.drawRoundRect(body, h * 0.15f, h * 0.15f, stroke);
            canvas.drawRect(body.left + w * 0.18f, body.top - h * 0.22f, body.left + w * 0.42f, body.top, fill);
            canvas.drawCircle(body.centerX(), body.centerY(), h * 0.3f, stroke);
        }
    };

    /** Folded map: three panels in a zigzag. */
    static final Icon MAP = new Icon() {
        @Override
        public void draw(Canvas canvas, RectF box, Paint stroke, Paint fill) {
            float l = box.left + box.width() * 0.1f, r = box.right - box.width() * 0.1f;
            float t = box.top + box.height() * 0.18f, b = box.bottom - box.height() * 0.18f;
            float third = (r - l) / 3, tilt = (b - t) * 0.1f;
            Path p = new Path();
            p.moveTo(l, t + tilt);
            p.lineTo(l + third, t);
            p.lineTo(l + 2 * third, t + tilt);
            p.lineTo(r, t);
            p.lineTo(r, b - tilt);
            p.lineTo(l + 2 * third, b);
            p.lineTo(l + third, b - tilt);
            p.lineTo(l, b);
            p.close();
            canvas.drawPath(p, stroke);
            canvas.drawLine(l + third, t, l + third, b - tilt, stroke);
            canvas.drawLine(l + 2 * third, t + tilt, l + 2 * third, b, stroke);
        }
    };

    /** Parking brake warning symbol: (P) */
    static final Icon HANDBRAKE = new Icon() {
        @Override
        public void draw(Canvas canvas, RectF box, Paint stroke, Paint fill) {
            float r = box.height() * 0.3f;
            float cx = box.centerX(), cy = box.centerY();
            canvas.drawCircle(cx, cy, r, stroke);
            RectF outer = new RectF(cx - r * 1.45f, cy - r * 1.25f, cx + r * 1.45f, cy + r * 1.25f);
            canvas.drawArc(outer, 140, 80, false, stroke);
            canvas.drawArc(outer, -40, 80, false, stroke);
            Paint text = new Paint(fill);
            text.setTextAlign(Paint.Align.CENTER);
            text.setFakeBoldText(true);
            text.setTextSize(r * 1.3f);
            canvas.drawText("P", cx, cy - (text.ascent() + text.descent()) / 2, text);
        }
    };
}
