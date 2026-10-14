package fr.networkguard.audit;

import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class VideoEvidenceTest {
    @Test public void aPort554OnlyMeansPossibleProtocolNotVideoProof(){
        assertEquals(2,VideoEvidence.category(Arrays.asList(80,443,554),false));
    }
    @Test public void repeatedPortsAreSuspiciousButNotProvenEmpty(){
        assertFalse(VideoEvidence.isRepetitiveProfile(7));
        assertTrue(VideoEvidence.isRepetitiveProfile(8));
        assertEquals(2,VideoEvidence.category(Arrays.asList(80,443,554),false));
    }
    @Test public void genericWebRemainsUncertain(){
        assertEquals(1,VideoEvidence.category(Arrays.asList(80,443),false));
        assertFalse(VideoEvidence.hasVideoProtocolClue(Arrays.asList(80,443)));
    }
    @Test public void onlyActualVideoFramesCanTurnGreen(){
        assertEquals(3,VideoEvidence.category(Arrays.asList(80,443),true));
        assertEquals(2,VideoEvidence.category(Arrays.asList(554),false));
    }
    @Test public void noListedPortsDoesNotEstablishAbsenceOfVideo(){
        assertEquals(0,VideoEvidence.category(Collections.emptyList(),false));
    }
}
