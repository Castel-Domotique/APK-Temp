package fr.networkguard.audit;
import org.junit.Test;
import static org.junit.Assert.*;

public class SecurityRulesTest {
    @Test public void htmlDoesNotProveVideo() {
        assertFalse(SecurityRules.isVideoContentType("text/html"));
        assertFalse(SecurityRules.isVideoContentType("image/jpeg"));
    }
    @Test public void mediaTypeRecognition() {
        assertTrue(SecurityRules.isVideoContentType("video/mp4"));
        assertTrue(SecurityRules.isVideoContentType("application/vnd.apple.mpegurl"));
        assertTrue(SecurityRules.isMjpegContentType("multipart/x-mixed-replace; boundary=frame"));
    }
    @Test public void tokenLinksAreNotAnonymousPublicLinks() {
        assertTrue(SecurityRules.urlMayContainAccessToken("https://camera.example/live?token=secret"));
        assertFalse(SecurityRules.urlMayContainAccessToken("https://camera.example/live"));
    }
}
