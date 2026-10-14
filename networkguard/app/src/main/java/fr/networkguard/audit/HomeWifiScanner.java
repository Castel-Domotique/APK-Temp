package fr.networkguard.audit;

import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class HomeWifiScanner {
    private HomeWifiScanner(){}
    public static final int[] PORTS = {80,443,554,8554,8080,8000,8123};
    public static final class Host {
        public final String ip;
        public final List<Integer> openPorts;
        Host(String ip,List<Integer> ports){this.ip=ip;this.openPorts=ports;}
    }
    public interface Listener{
        void progress(int scanned,int total);
        void detected(Host host);
    }
    public static String subnetInfo(LinkProperties properties)throws Exception{
        LinkAddress a=privateAddress(properties);
        int prefix=a.getPrefixLength();
        return a.getAddress().getHostAddress()+"/"+prefix+" (recherche limitée à 254 hôtes)";
    }
    private static LinkAddress privateAddress(LinkProperties lp)throws Exception{
        if(lp==null)throw new Exception("Connexion réseau indisponible.");
        for(LinkAddress a:lp.getLinkAddresses()){
            InetAddress ip=a.getAddress();
            if(ip instanceof Inet4Address && ip.isSiteLocalAddress() && a.getPrefixLength()>=8 && a.getPrefixLength()<=30)return a;
        }
        throw new Exception("Aucune adresse IPv4 privée Wi-Fi détectée.");
    }
    public static void scan(Network wifi,LinkProperties lp,AtomicBoolean active,Listener listener)throws Exception{
        if(wifi==null)throw new Exception("Connexion Wi-Fi indisponible.");
        LinkAddress link=privateAddress(lp);
        byte[] b=link.getAddress().getAddress();
        int local=((b[0]&255)<<24)|((b[1]&255)<<16)|((b[2]&255)<<8)|(b[3]&255);
        // A larger LAN is deliberately capped at the local /24; a smaller LAN keeps its real mask.
        int prefix=Math.max(24,link.getPrefixLength());
        int mask= -1 << (32-prefix);
        int network=local & mask;
        int hosts=(1<<(32-prefix))-2;
        if(hosts<=0)throw new Exception("Sous-réseau trop petit.");
        ExecutorService executor=Executors.newFixedThreadPool(20);
        AtomicInteger done=new AtomicInteger();
        List<Future<?>> tasks=new ArrayList<>();
        try{
            for(int i=1;i<=hosts;i++){
                if(!active.get())break;
                int address=network+i;
                if(address==local){int n=done.incrementAndGet();if(n%8==0)listener.progress(n,hosts);continue;}
                final String ip=(address>>>24&255)+"."+(address>>>16&255)+"."+(address>>>8&255)+"."+(address&255);
                tasks.add(executor.submit(()->{
                    if(!active.get())return;
                    List<Integer> ports=new ArrayList<>();
                    for(int port:PORTS){
                        if(!active.get())break;
                        try(Socket s=wifi.getSocketFactory().createSocket()){
                            s.connect(new InetSocketAddress(ip,port),220);
                            ports.add(port);
                        }catch(Exception ignored){}
                    }
                    if(!ports.isEmpty() && active.get())listener.detected(new Host(ip,Collections.unmodifiableList(ports)));
                    int n=done.incrementAndGet();
                    if(n%8==0||n==hosts)listener.progress(n,hosts);
                }));
            }
            for(Future<?> f:tasks){
                if(!active.get())break;
                try{f.get();}catch(Exception ignored){}
            }
            listener.progress(done.get(),hosts);
        }finally{executor.shutdownNow();}
    }
}
