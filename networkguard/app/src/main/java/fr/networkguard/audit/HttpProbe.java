package fr.networkguard.audit;

import java.net.HttpURLConnection;
import java.net.URL;

public final class HttpProbe {
    public static final class Result {
        public final int code;
        public final String contentType;
        public final String message;
        public final boolean playable;
        public final boolean mjpeg;
        public Result(int code, String contentType, String message, boolean playable, boolean mjpeg) {
            this.code = code; this.contentType = contentType; this.message = message;
            this.playable = playable; this.mjpeg = mjpeg;
        }
    }

    public static Result inspect(URL url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setInstanceFollowRedirects(false);
        c.setUseCaches(false);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "video/*,application/vnd.apple.mpegurl,application/x-mpegURL,multipart/x-mixed-replace,*/*;q=0.1");
        // Neither Cookie nor Authorization is ever sent. This is the unauthenticated test.
        try {
            int code = c.getResponseCode();
            String ct = c.getContentType();
            if (code == 401 || code == 403)
                return new Result(code, ct, "Authentification ou permission exigée par ce service.", false, false);
            if (code >= 300 && code < 400)
                return new Result(code, ct, "Redirection : impossible de confirmer un flux anonyme.", false, false);
            if (code != 200 && code != 206)
                return new Result(code, ct, "Réponse HTTP " + code + " : aucun flux confirmé.", false, false);
            if (SecurityRules.isVideoContentType(ct))
                return new Result(code, ct, "Contenu vidéo annoncé sans compte. Lecture réelle à vérifier ci-dessous.", true, SecurityRules.isMjpegContentType(ct));
            if (ct != null && ct.toLowerCase().startsWith("image/"))
                return new Result(code, ct, "Image disponible, mais ce n'est pas une preuve de flux vidéo en direct.", false, false);
            return new Result(code, ct, "Page accessible, mais aucun flux vidéo direct démontré.", false, false);
        } finally {
            c.disconnect();
        }
    }
}
