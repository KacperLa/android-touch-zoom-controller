import io.github.kacperla.touchzoom.OrbitArtwork;

public class OrbitArtworkTest {
    private static int count;
    private static void check(boolean value) { count++; if(!value)throw new AssertionError("Orbit assertion "+count); }
    public static void main(String[] args) {
        check(OrbitArtwork.START_DEGREES>0);
        check(OrbitArtwork.SWEEP_DEGREES>180 && OrbitArtwork.SWEEP_DEGREES<360);
        check(OrbitArtwork.START_DEGREES*2+OrbitArtwork.SWEEP_DEGREES==360);
        // Ring and dot remain separate at every controller size and drag scale.
        for(float diameter:new float[]{48,56,64})for(float scale:new float[]{1,1.5f}) {
            float r=diameter*scale/2;
            float inner=r*(OrbitArtwork.RING_RADIUS-OrbitArtwork.RING_STROKE/2);
            float outer=r*(OrbitArtwork.RING_RADIUS+OrbitArtwork.RING_STROKE/2);
            check(inner>r*OrbitArtwork.DOT_RADIUS);
            check(outer<r-4*scale);
            check(r*OrbitArtwork.RING_STROKE>=4);
        }
        System.out.println(count+" Orbit artwork assertions passed");
    }
}
