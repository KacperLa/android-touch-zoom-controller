package io.github.kacperla.touchzoom;
/** Main-thread, generation-scoped coalescing. No timer or self-rescheduling work. */
public final class InsetsEventQueue {
 private int generation;private boolean active,queued;
 public void start(int value){generation=value;active=true;queued=false;}
 public boolean request(int value){if(!active||value!=generation||queued)return false;queued=true;return true;}
 public boolean take(int value){if(!active||value!=generation||!queued)return false;queued=false;return true;}
 public void cancel(){queued=false;}
 public void stop(){active=false;queued=false;}
}
