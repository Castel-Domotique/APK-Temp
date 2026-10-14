package fr.networkguard.audit;

import java.util.List;

/**
 * Classification of TCP-port observations, NOT of video streams.
 * A missing port is never proof that a camera has no video.
 */
public final class VideoEvidence {
    private VideoEvidence(){}

    public static boolean hasVideoProtocolClue(List<Integer> ports){
        return ports != null && (ports.contains(554) || ports.contains(8554));
    }

    public static boolean hasWebOnlyClue(List<Integer> ports){
        if(ports==null || ports.isEmpty() || hasVideoProtocolClue(ports))return false;
        return ports.contains(80)||ports.contains(443)||ports.contains(8080)||ports.contains(8000);
    }

    public static int category(List<Integer> ports,boolean videoFramesDecoded){
        if(videoFramesDecoded)return 3; // green, real decoded images on an owned target
        if(hasVideoProtocolClue(ports))return 2; // amber, RTSP-oriented port only
        if(hasWebOnlyClue(ports))return 1; // blue, web only (can also be a camera)
        return 0; // grey, unclassified
    }
}
