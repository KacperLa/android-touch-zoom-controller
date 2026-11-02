package io.github.kacperla.touchzoom;

/** Bounded multi-gesture capture lifetime. Main thread only; times are uptime milliseconds. */
public final class CaptureSession {
    public static final long DURATION_MS = 15_000;
    public static final int MAX_GESTURES = 16;
    public static final int MAX_REPORT_CHARS = 4_096;
    private final String[] gestures = new String[MAX_GESTURES];
    private long id, startedAt, deadline, observedAt;
    private int count, overflow;
    private long heartbeatCount, maxHeartbeatLateness;
    private String state = "CLEARED";

    public void start(long now) {
        if (now < 0 || now > Long.MAX_VALUE - DURATION_MS)
            throw new IllegalArgumentException("Invalid uptime");
        resetReports();
        id++;
        startedAt = observedAt = now;
        deadline = now + DURATION_MS;
        state = "RECORDING";
    }

    public void clear() {
        resetReports();
        id++;
        startedAt = deadline = observedAt = 0;
        state = "CLEARED";
    }

    /** The only per-event session check required; does not allocate. */
    public boolean recording(long now) {
        if (!"RECORDING".equals(state)) return false;
        observedAt = Math.max(observedAt, Math.min(now, deadline));
        if (now >= deadline) state = "ENDED";
        return "RECORDING".equals(state);
    }

    /** Freezes at the last observed time. Call recording(now) first for a precise manual stop. */
    public void stop() {
        if ("RECORDING".equals(state)) state = "ENDED";
    }

    /** Whole-capture main-thread scheduling observation, including gaps between gestures. */
    public void heartbeat(long due, long arrival) {
        if (!"RECORDING".equals(state)) return;
        // A beat due within the capture remains evidence even when a stall carries
        // its delivery past the deadline. Expire only after recording that delay.
        if (due >= startedAt && due < deadline) {
            heartbeatCount++;
            long lateness = arrival > due ? arrival - due : 0;
            maxHeartbeatLateness = Math.max(maxHeartbeatLateness, lateness);
        }
        recording(arrival);
    }

    public long id() { return id; }
    public long startedAt() { return startedAt; }
    public long deadline() { return deadline; }
    public String state() { return state; }
    public int gestureCount() { return count; }
    public int overflowCount() { return overflow; }

    /** Allows the final in-flight gesture to be appended after the timer ends. */
    public void addGesture(String report) {
        if ("CLEARED".equals(state) || report == null || report.isEmpty()) return;
        if (count == MAX_GESTURES) {
            if (overflow < Integer.MAX_VALUE) overflow++;
            return;
        }
        gestures[count++] = report.length() <= MAX_REPORT_CHARS ? report
                : report.substring(0, MAX_REPORT_CHARS - 14) + "\n[truncated]\n";
    }

    public String report(long now) {
        recording(now);
        StringBuilder b = new StringBuilder();
        b.append("Capture ID=").append(id).append(" state=").append(state)
                .append(" startedAt=").append(startedAt).append(" deadline=").append(deadline)
                .append(" elapsedMs=").append(observedAt - startedAt)
                .append(" gestures=").append(count).append(" overflow=").append(overflow)
                .append(" (max16 retained)\n")
                .append("Capture heartbeat count=").append(heartbeatCount)
                .append(" maxLatenessMs=").append(maxHeartbeatLateness).append('\n');
        for (int i = 0; i < count; i++)
            b.append("Gesture ").append(i + 1).append(":\n").append(gestures[i]).append('\n');
        return b.toString();
    }

    private void resetReports() {
        for (int i = 0; i < count; i++) gestures[i] = null;
        count = overflow = 0;
        heartbeatCount = maxHeartbeatLateness = 0;
    }
}
