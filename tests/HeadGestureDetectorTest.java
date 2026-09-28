package dev.rokid.pocketweb;

public final class HeadGestureDetectorTest {
    private static void expect(HeadGestureDetector.Gesture actual, HeadGestureDetector.Gesture expected) {
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }

    private static float[] pose(float yawDegrees, float pitchDegrees) {
        return new float[] {(float) Math.toRadians(yawDegrees),
            (float) Math.toRadians(pitchDegrees), 0};
    }

    public static void main(String[] args) {
        float wrap = HeadGestureDetector.degreesBetween(
            (float) Math.toRadians(179), (float) Math.toRadians(-179));
        if (Math.abs(wrap - 2) > .01f) throw new AssertionError("Angle wrapping failed");
        if (HeadGestureDetector.strongestAxis(pose(0, 0), pose(20, 8), -1) != 0)
            throw new AssertionError("Yaw calibration axis failed");
        if (HeadGestureDetector.strongestAxis(pose(0, 0), pose(20, 8), 0) != 1)
            throw new AssertionError("Pitch calibration axis failed");

        HeadGestureDetector detector = new HeadGestureDetector();
        detector.configure(0, 1, 1, 1, pose(0, 0));
        expect(detector.update(pose(0, 0), 0), HeadGestureDetector.Gesture.NONE);
        expect(detector.update(pose(25, 0), 100), HeadGestureDetector.Gesture.NONE);
        expect(detector.update(pose(25, 0), 150), HeadGestureDetector.Gesture.NONE);
        expect(detector.update(pose(25, 0), 200), HeadGestureDetector.Gesture.NONE);
        expect(detector.update(pose(25, 0), 440), HeadGestureDetector.Gesture.NEXT);
        expect(detector.update(pose(25, 0), 1100), HeadGestureDetector.Gesture.NONE);
        for (int t = 1200; t <= 1400; t += 50) detector.update(pose(0, 0), t);
        detector.update(pose(-25, 0), 1500);
        detector.update(pose(-25, 0), 1550);
        detector.update(pose(-25, 0), 1600);
        expect(detector.update(pose(-25, 0), 1840), HeadGestureDetector.Gesture.PREVIOUS);

        detector.recenter(pose(0, 0));
        detector.update(pose(0, 0), 2000);
        detector.update(pose(0, 20), 2100);
        detector.update(pose(0, 20), 2150);
        detector.update(pose(0, 20), 2200);
        expect(detector.update(pose(0, 20), 2660), HeadGestureDetector.Gesture.ACTIVATE);
        expect(detector.update(pose(0, 20), 4000), HeadGestureDetector.Gesture.NONE);
        System.out.println("Head gesture tests passed: axis selection, wrap, hold, rearm, cooldown.");
    }
}
