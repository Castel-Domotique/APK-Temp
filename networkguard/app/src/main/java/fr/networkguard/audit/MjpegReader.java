package fr.networkguard.audit;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MjpegReader {
    public interface Listener { void frame(Bitmap bitmap); void error(String msg); }
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread worker;
    private volatile HttpURLConnection connection;

    public void start(URL url, Listener listener) {
        stop();
        running.set(true);
        worker = new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                connection = c;
                c.setConnectTimeout(5000);
                c.setReadTimeout(12000);
                c.setInstanceFollowRedirects(false);
                c.setUseCaches(false);
                if (c.getResponseCode() != 200) throw new Exception("Code HTTP non valide.");
                if (!SecurityRules.isMjpegContentType(c.getContentType()))
                    throw new Exception("Le serveur n'envoie pas un flux MJPEG.");
                try (InputStream in = c.getInputStream()) {
                    int prev = -1;
                    boolean active = false;
                    ByteArrayOutputStream frame = new ByteArrayOutputStream(64 * 1024);
                    int now;
                    while (running.get() && (now = in.read()) >= 0) {
                        if (!active) {
                            if (prev == 0xff && now == 0xd8) {
                                frame.reset(); frame.write(0xff); frame.write(0xd8); active = true;
                            }
                        } else {
                            frame.write(now);
                            if (frame.size() > 2_000_000) { active = false; frame.reset(); }
                            else if (prev == 0xff && now == 0xd9) {
                                byte[] data = frame.toByteArray();
                                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                                if (bitmap != null && running.get()) listener.frame(bitmap);
                                active = false;
                            }
                        }
                        prev = now;
                    }
                }
            } catch (Exception ex) {
                if (running.get()) listener.error(ex.getMessage() == null ? "Lecture impossible" : ex.getMessage());
            } finally {
                HttpURLConnection c = connection; if (c != null) c.disconnect();
            }
        }, "mjpeg-reader");
        worker.start();
    }
    public void stop() {
        running.set(false);
        HttpURLConnection c = connection;
        if (c != null) c.disconnect();
        if (worker != null) worker.interrupt();
        worker = null;
        connection = null;
    }
}
