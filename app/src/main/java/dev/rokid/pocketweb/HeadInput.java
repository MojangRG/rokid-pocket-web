package dev.rokid.pocketweb;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Foreground-only head input with a touchpad-guided, device-specific axis calibration. */
final class HeadInput implements SensorEventListener {
    interface Listener {
        void onPrompt(String text);
        void onEnabled(boolean enabled);
        void onGesture(HeadGestureDetector.Gesture gesture);
    }

    private final SensorManager manager;
    private final Sensor sensor;
    private final Listener listener;
    private final HeadGestureDetector detector = new HeadGestureDetector();
    private final float[] matrix = new float[9];
    private final float[] current = new float[3];
    private float[] center;
    private int yawAxis;
    private float yawSign;
    private int pitchAxis;
    private float pitchSign;
    private int calibrationStep = -1;
    private boolean enabled;
    private boolean listening;
    private boolean resumed;
    private boolean hasSample;
    private boolean recenterOnNextSample;

    HeadInput(Context context, Listener listener) {
        manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor gameRotation = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        sensor = gameRotation != null ? gameRotation :
                manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        this.listener = listener;
    }

    boolean available() { return sensor != null; }
    boolean isEnabled() { return enabled; }
    boolean isCalibrating() { return calibrationStep >= 0; }

    void onResume() {
        resumed = true;
        recenterOnNextSample = enabled;
        updateRegistration();
    }

    void onPause() {
        resumed = false;
        updateRegistration();
    }

    void startCalibration() {
        if (sensor == null) return;
        enabled = false;
        calibrationStep = 0;
        hasSample = false;
        listener.onEnabled(false);
        updateRegistration();
        listener.onPrompt("УПРАВЛЕНИЕ ГОЛОВОЙ\n\n1/5 Смотри прямо и нажми тачпад.\n\nНазад — отмена.");
    }

    void disable() {
        enabled = false;
        calibrationStep = -1;
        listener.onPrompt(null);
        listener.onEnabled(false);
        updateRegistration();
    }

    boolean recenter() {
        if (enabled && hasSample) {
            detector.recenter(current);
            return true;
        }
        return false;
    }

    void capture() {
        if (calibrationStep < 0) return;
        if (!hasSample) {
            listener.onPrompt("Жду данные датчика. Подержи голову неподвижно и нажми ещё раз.");
            return;
        }
        switch (calibrationStep) {
            case 0:
                center = current.clone();
                calibrationStep = 1;
                listener.onPrompt("2/5 Поверни голову ВПРАВО примерно на 25° и нажми тачпад.");
                break;
            case 1:
                yawAxis = HeadGestureDetector.strongestAxis(center, current, -1);
                if (yawAxis < 0 || Math.abs(HeadGestureDetector.degreesBetween(
                        center[yawAxis], current[yawAxis])) < 12f) {
                    listener.onPrompt("Поворот маловат. Поверни голову ВПРАВО сильнее и нажми ещё раз.");
                    return;
                }
                yawSign = Math.signum(HeadGestureDetector.degreesBetween(center[yawAxis], current[yawAxis]));
                calibrationStep = 2;
                listener.onPrompt("3/5 Верни голову ПРЯМО и нажми тачпад.");
                break;
            case 2:
                if (Math.abs(HeadGestureDetector.degreesBetween(
                        center[yawAxis], current[yawAxis])) > 15f) {
                    listener.onPrompt("Верни голову ПРЯМО и нажми ещё раз.");
                    return;
                }
                center = current.clone();
                calibrationStep = 3;
                listener.onPrompt("4/5 Наклони голову ВНИЗ примерно на 20° и нажми тачпад.");
                break;
            case 3:
                pitchAxis = HeadGestureDetector.strongestAxis(center, current, yawAxis);
                if (pitchAxis < 0 || Math.abs(HeadGestureDetector.degreesBetween(
                        center[pitchAxis], current[pitchAxis])) < 9f) {
                    listener.onPrompt("Кивок не распознан. Наклони голову ВНИЗ сильнее и нажми ещё раз.");
                    return;
                }
                pitchSign = Math.signum(HeadGestureDetector.degreesBetween(center[pitchAxis], current[pitchAxis]));
                calibrationStep = 4;
                listener.onPrompt("5/5 Снова смотри ПРЯМО и нажми тачпад. После этого голова управляет карточками.");
                break;
            case 4:
                if (Math.abs(HeadGestureDetector.degreesBetween(
                        center[yawAxis], current[yawAxis])) > 15f ||
                        Math.abs(HeadGestureDetector.degreesBetween(
                        center[pitchAxis], current[pitchAxis])) > 12f) {
                    listener.onPrompt("Снова смотри ПРЯМО и нажми ещё раз.");
                    return;
                }
                detector.configure(yawAxis, yawSign, pitchAxis, pitchSign, current);
                calibrationStep = -1;
                enabled = true;
                listener.onPrompt(null);
                listener.onEnabled(true);
                break;
            default:
                break;
        }
    }

    private void updateRegistration() {
        boolean wanted = resumed && sensor != null && (enabled || isCalibrating());
        if (wanted && !listening) {
            listening = manager.registerListener(this, sensor, 20_000);
            if (!listening) disable();
        } else if (!wanted && listening) {
            manager.unregisterListener(this);
            listening = false;
            hasSample = false;
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        SensorManager.getRotationMatrixFromVector(matrix, event.values);
        SensorManager.getOrientation(matrix, current);
        hasSample = true;
        if (!enabled) return;
        if (recenterOnNextSample) {
            detector.recenter(current);
            recenterOnNextSample = false;
            return;
        }
        HeadGestureDetector.Gesture gesture = detector.update(current, event.timestamp / 1_000_000L);
        if (gesture != HeadGestureDetector.Gesture.NONE) listener.onGesture(gesture);
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
