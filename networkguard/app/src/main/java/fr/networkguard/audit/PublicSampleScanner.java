package fr.networkguard.audit;

import android.net.Network;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small, bounded public IPv4 samples. These presets are NOT geographically
 * located camera inventories, and an open TCP port is NOT a vulnerability.
 * Only TCP connect is performed until the user confirms ownership separately.
 */
public final class PublicSampleScanner {
    private PublicSampleScanner() {}
    public static final String[] SAMPLE_NAMES = {
        "Échantillon A — 193.48.1.0/24 (position inconnue)",
        "Échantillon B — 194.2.0.0/24 (position inconnue)",
        "Échantillon C — 195.154.10.0/24 (position inconnue)"
    };
    private static final String[] SAMPLES = {"193.48.1", "194.2.0", "195.154.10"};
    private static final int[] PORTS = {80,443,554,8080,8554,8000};
    public static final class Host {
        public final String ip;
        public final List<Integer> ports;
        Host(String ip,List<Integer> ports) {
            this.ip=ip; this.ports=Collections.unmodifiableList(ports);
        }
    }
    public interface Listener {
        void progress(int finished,int total);
        void detected(Host host);
    }
    public static int sampleCount(){return SAMPLES.length;}
    public static void scan(Network mobile,int sample,AtomicBoolean running,Listener listener)throws Exception {
        if(mobile==null||sample<0||sample>=SAMPLES.length)
            throw new Exception("Échantillon invalide.");
        ExecutorService executor=Executors.newFixedThreadPool(8);
        AtomicInteger finished=new AtomicInteger();
        List<Future<?>> futures=new ArrayList<>();
        try {
            for(int i=1;i<=254;i++){
                if(!running.get())break;
                String ip=SAMPLES[sample]+"."+i;
                futures.add(executor.submit(()->{
                    if(!running.get())return;
                    List<Integer> open=new ArrayList<>();
                    for(int port:PORTS){
                        if(!running.get())break;
                        try(Socket socket=mobile.getSocketFactory().createSocket()){
                            socket.connect(new InetSocketAddress(ip,port),400);
                            open.add(port);
                        }catch(Exception ignored){}
                    }
                    if(!open.isEmpty()&&running.get())listener.detected(new Host(ip,open));
                    int n=finished.incrementAndGet();
                    if(n%10==0||n==254)listener.progress(n,254);
                }));
            }
            for(Future<?> future:futures){
                if(!running.get())break;
                try{future.get();}catch(Exception ignored){}
            }
            listener.progress(finished.get(),254);
        }finally{executor.shutdownNow();}
    }
}
