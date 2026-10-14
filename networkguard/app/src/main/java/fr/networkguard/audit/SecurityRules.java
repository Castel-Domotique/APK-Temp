package fr.networkguard.audit;

import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;

public final class SecurityRules {
    private SecurityRules() {}

    public static String requirePublicHost(String hostname) throws Exception {
        if (hostname == null || hostname.trim().isEmpty()) throw new Exception("Indique ton adresse IP publique ou ton domaine.");
        hostname = hostname.trim();
        if (hostname.equalsIgnoreCase("localhost") || hostname.toLowerCase(Locale.ROOT).endsWith(".local"))
            throw new Exception("Adresse locale refusée : le test 1 doit viser Internet.");
        InetAddress[] addresses = InetAddress.getAllByName(hostname);
        if (addresses.length == 0) throw new Exception("Nom de domaine non résolu.");
        for (InetAddress ip : addresses) {
            byte[] b = ip.getAddress();
            if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress()
                || ip.isSiteLocalAddress() || ip.isMulticastAddress()
                || (b.length == 4 && ((b[0] & 255) == 100 && ((b[1] & 255) >= 64 && (b[1] & 255) <= 127)))
                || (b.length == 16 && ((b[0] & 0xfe) == 0xfc)))
                throw new Exception("Cette adresse n'est pas publique. Aucun test local dans le mode extérieur.");
        }
        return hostname;
    }

    public static URL parsePublicHttpUrl(String text) throws Exception {
        URL url = new URL(text.trim());
        String scheme = url.getProtocol().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http"))
            throw new Exception("Adresse HTTP/HTTPS requise. RTSP est prévu dans une version ultérieure.");
        if (url.getUserInfo() != null) throw new Exception("Identifiants intégrés dans l'URL refusés.");
        requirePublicHost(url.getHost());
        return url;
    }

    public static boolean isVideoContentType(String contentType) {
        if (contentType == null) return false;
        String s = contentType.toLowerCase(Locale.ROOT);
        return s.startsWith("video/") || s.contains("mpegurl") ||
            s.startsWith("multipart/x-mixed-replace");
    }

    public static boolean isMjpegContentType(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT)
            .startsWith("multipart/x-mixed-replace");
    }

    public static boolean urlMayContainAccessToken(String url) {
        if (url == null) return false;
        int q = url.indexOf('?');
        if (q < 0) return false;
        String query = url.substring(q + 1).toLowerCase(Locale.ROOT);
        return query.matches(".*(^|[?&])(token|auth|key|apikey|api_key|signature|sig|access_token|jwt|session|ticket|expires|policy)=.*")
            || query.contains("token=") || query.contains("signature=") || query.contains("key=");
    }
}
