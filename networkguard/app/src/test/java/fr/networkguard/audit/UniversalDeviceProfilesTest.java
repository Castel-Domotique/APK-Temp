package fr.networkguard.audit;

import static org.junit.Assert.*;
import org.junit.Test;

public final class UniversalDeviceProfilesTest {
    @Test public void allMajorTvBrandsCanBeRecognizedWithoutModel(){
        assertEquals("TCL",UniversalDeviceProfiles.manufacturer("TCL android"));
        assertEquals("Sony",UniversalDeviceProfiles.manufacturer("Sony Bravia"));
        assertEquals("Samsung",UniversalDeviceProfiles.manufacturer("Samsung Tizen"));
        assertEquals("LG",UniversalDeviceProfiles.manufacturer("LG webOS"));
    }
    @Test public void noModelIsAssumedToContainCamera(){
        assertTrue(UniversalDeviceProfiles.capabilityWarning(2).contains("ne peut pas prouver"));
    }
    @Test public void ipCameraStillNotProvenByOpenRtsp(){
        assertTrue(UniversalDeviceProfiles.capabilityWarning(0).contains("pas une preuve"));
    }
}
