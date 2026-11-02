package io.github.kacperla.touchzoom;
/** Short critical sections only; a renderer must draw outside this object’s monitor. */
public final class LatestRenderQueue<T> {
 private T latest;private boolean queued,stopped;
 public synchronized boolean offer(T value){if(stopped)return false;latest=value;if(queued)return false;queued=true;return true;}
 public synchronized T take(){if(stopped)return null;T v=latest;latest=null;return v;}
 public synchronized boolean complete(){if(!stopped && latest!=null)return true;queued=false;return false;}
 public synchronized void stop(){stopped=true;latest=null;}
}
