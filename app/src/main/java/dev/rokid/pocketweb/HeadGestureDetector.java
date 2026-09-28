package dev.rokid.pocketweb;

/** Relative head pose gestures. Pure Java so the thresholds can be tested without a device. */
public final class HeadGestureDetector {
    public enum Gesture { NONE, NEXT, PREVIOUS, ACTIVATE }

    private int yawAxis;
    private int pitchAxis;
    private float yawSign;
    private float pitchSign;
    private final float[] center = new float[3];
    private float yawFiltered;
    private float pitchFiltered;
    private boolean yawArmed = true;
    private boolean pitchArmed = true;
    private int yawCandidate;
    private long yawSince = -1;
    private long pitchSince = -1;
    private long cooldownUntil;

    public static float degreesBetween(float from, float to) {
        float difference = to - from;
        return (float) Math.toDegrees(Math.atan2(Math.sin(difference), Math.cos(difference)));
    }

    public static int strongestAxis(float[] from, float[] to, int exclude) {
        int best = -1;
        float largest = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (axis == exclude) continue;
            float size = Math.abs(degreesBetween(from[axis], to[axis]));
            if (size > largest) { largest = size; best = axis; }
        }
        return best;
    }

    public void configure(int yawAxis, float yawSign, int pitchAxis, float pitchSign, float[] pose) {
        if (yawAxis == pitchAxis || yawAxis < 0 || pitchAxis < 0) {
            throw new IllegalArgumentException("Head axes must be different");
        }
        this.yawAxis = yawAxis;
        this.pitchAxis = pitchAxis;
        this.yawSign = yawSign;
        this.pitchSign = pitchSign;
        recenter(pose);
    }

    public void recenter(float[] pose) {
        System.arraycopy(pose, 0, center, 0, 3);
        yawFiltered = pitchFiltered = 0;
        yawArmed = pitchArmed = true;
        yawCandidate = 0;
        yawSince = pitchSince = -1;
        cooldownUntil = 0;
    }

    public Gesture update(float[] pose, long nowMs) {
        float yaw = yawSign * degreesBetween(center[yawAxis], pose[yawAxis]);
        float pitch = pitchSign * degreesBetween(center[pitchAxis], pose[pitchAxis]);
        yawFiltered += .4f * (yaw - yawFiltered);
        pitchFiltered += .4f * (pitch - pitchFiltered);

        // A single excursion fires once. The head has to return to center to rearm.
        if (Math.abs(yawFiltered) < 7f) yawArmed = true;
        if (pitchFiltered < 6f) pitchArmed = true;
        if (nowMs < cooldownUntil) return Gesture.NONE;

        int direction = yawFiltered > 18f ? 1 : yawFiltered < -18f ? -1 : 0;
        if (yawArmed && direction != 0) {
            pitchSince = -1;
            if (yawCandidate != direction) {
                yawCandidate = direction;
                yawSince = nowMs;
            } else if (nowMs - yawSince >= 220) {
                yawArmed = false;
                yawSince = -1;
                cooldownUntil = nowMs + 600;
                return direction > 0 ? Gesture.NEXT : Gesture.PREVIOUS;
            }
        } else {
            yawCandidate = 0;
            yawSince = -1;
        }

        if (pitchArmed && pitchFiltered > 14f && Math.abs(yawFiltered) < 12f) {
            if (pitchSince < 0) pitchSince = nowMs;
            else if (nowMs - pitchSince >= 420) {
                pitchArmed = false;
                pitchSince = -1;
                cooldownUntil = nowMs + 850;
                return Gesture.ACTIVATE;
            }
        } else pitchSince = -1;
        return Gesture.NONE;
    }
}
