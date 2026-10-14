package fr.networkguard.audit;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.media.MediaPlayer;
import android.view.View;
import android.widget.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private final int BACK=0xff0b1728, PANEL=0xff1a2d43, TEXT=Color.WHITE,
        MUTED=0xffbecfe0, GREEN=0xff68e1b2, BLUE=0xff91c5ff, RED=0xffffa2a2;
    private LinearLayout root,wifiPanel,mobilePanel,devices,videoPanel,videoChoices;
    private TextView localStatus,ipStatus,remoteStatus,videoStatus;
    private VideoView player;
    private ImageView mjpegView;
    private CheckBox ownedNetwork,lookupAllowed;
    private final MjpegReader mjpegReader=new MjpegReader();
    private final ExecutorService tasks=Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning=new AtomicBoolean(false);
    private boolean showingVideo=false;
    private boolean receivedFrame=false;
    private int found=0;
    private String homeIp="";
    private final List<HomeWifiScanner.Host> discovered=new ArrayList<>();

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        ScrollView screen=new ScrollView(this);
        screen.setFillViewport(true);screen.setBackgroundColor(BACK);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(17),dp(23),dp(17),dp(30));
        screen.addView(root);setContentView(screen);
        title(root,"NetworkGuard",30,GREEN);
        label(root,"0.4.0 — Deux scanners séparés • Aucune IP à saisir",MUTED);

        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(nav);
        smallButton(nav,"Scanner Wi-Fi",()->showMode(true));
        smallButton(nav,"Scanner 4G/5G",()->showMode(false));

        wifiPanel=panel(root);
        title(wifiPanel,"01  Ton Wi-Fi domestique",20,GREEN);
        label(wifiPanel,"Découvre les appareils sur le réseau privé auquel ton téléphone est connecté, sans compte Freebox.",MUTED);
        label(wifiPanel,"Ce mode est LOCAL. Une caméra accessible ici n'est pas forcément accessible depuis Internet.",BLUE);
        ownedNetwork=new CheckBox(this);
        ownedNetwork.setText("Je confirme que ce Wi-Fi est chez moi ou que j'ai l'autorisation de le tester.");
        ownedNetwork.setTextColor(TEXT);ownedNetwork.setTextSize(13);
        wifiPanel.addView(ownedNetwork);
        lookupAllowed=new CheckBox(this);
        lookupAllowed.setText("Déterminer mon IP publique avec api4.ipify.org pour le test 4G ultérieur.");
        lookupAllowed.setTextColor(TEXT);lookupAllowed.setTextSize(13);
        lookupAllowed.setChecked(true);wifiPanel.addView(lookupAllowed);
        label(wifiPanel,"Cette simple consultation HTTPS ne donne aucun accès à ta Freebox. Sans elle, le mode extérieur ne peut pas savoir quelle adresse tester.",MUTED);
        button(wifiPanel,"Scanner mon Wi-Fi",this::startWifiScan);
        button(wifiPanel,"Arrêter le scan Wi-Fi",()->{
            scanning.set(false);
            localStatus.setText("Arrêt demandé. Les appareils déjà découverts restent affichés.");
        });
        localStatus=label(wifiPanel,"En attente du scan Wi-Fi.",BLUE);
        devices=new LinearLayout(this);devices.setOrientation(LinearLayout.VERTICAL);
        wifiPanel.addView(devices);

        mobilePanel=panel(root);
        title(mobilePanel,"02  Test extérieur 4G/5G",20,GREEN);
        label(mobilePanel,"Désactive le Wi-Fi et le VPN. Le téléphone teste l'adresse publique détectée précédemment chez toi, sans utiliser les identifiants ni les permissions locales.",MUTED);
        label(mobilePanel,"Le test ne présume PAS qu'un port ouvert donne accès à la vidéo. Il ne démontre pas la sécurité des caméras utilisant seulement un cloud.",MUTED);
        ipStatus=label(mobilePanel,"",BLUE);
        button(mobilePanel,"Tester l'exposition depuis la 4G/5G",this::startMobileScan);
        remoteStatus=label(mobilePanel,"Aucun test extérieur effectué.",MUTED);
        videoChoices=new LinearLayout(this);videoChoices.setOrientation(LinearLayout.VERTICAL);
        mobilePanel.addView(videoChoices);

        videoPanel=panel(root);
        title(videoPanel,"Preuve vidéo réelle",18,GREEN);
        videoStatus=label(videoPanel,"Aucune preuve : le programme n'invente jamais une vidéo.",MUTED);
        player=new VideoView(this);
        videoPanel.addView(player,new LinearLayout.LayoutParams(-1,dp(210)));player.setVisibility(View.GONE);
        mjpegView=new ImageView(this);
        mjpegView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        videoPanel.addView(mjpegView,new LinearLayout.LayoutParams(-1,dp(210)));mjpegView.setVisibility(View.GONE);
        button(videoPanel,"Arrêter le direct",this::stopVideo);
        label(root,"Résultats limités : port détecté ≠ faille ; image locale ≠ faille Internet ; absence de flux ≠ sécurité garantie. Le cloud propriétaire nécessite des vérifications propres au fabricant.",MUTED);
        homeIp=getPreferences(0).getString("home_public_ip","");
        renderStoredIp();
        showMode(true);
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable bg(){GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(13));return d;}
    private LinearLayout panel(LinearLayout parent){
        LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(12),dp(14),dp(12),dp(15));view.setBackground(bg());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(13),0,dp(6));parent.addView(view,p);return view;
    }
    private TextView label(LinearLayout parent,String value,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(13);
        t.setPadding(0,dp(4),0,dp(7));parent.addView(t);return t;
    }
    private TextView title(LinearLayout parent,String value,int size,int color){
        TextView t=label(parent,value,color);t.setTextSize(size);t.setPadding(0,0,0,dp(8));return t;
    }
    private void button(LinearLayout parent,String text,Runnable action){
        Button btn=new Button(this);btn.setAllCaps(false);btn.setText(text);btn.setTextSize(13);
        btn.setTextColor(BACK);
        GradientDrawable d=new GradientDrawable();d.setColor(GREEN);d.setCornerRadius(dp(9));btn.setBackground(d);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(46));
        p.setMargins(0,dp(7),0,dp(5));parent.addView(btn,p);
        btn.setOnClickListener(v->action.run());
    }
    private void smallButton(LinearLayout parent,String text,Runnable action){
        Button btn=new Button(this);btn.setText(text);btn.setAllCaps(false);btn.setTextSize(12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(51),1);
        p.setMargins(dp(3),dp(5),dp(3),dp(5));parent.addView(btn,p);
        btn.setOnClickListener(v->action.run());
    }
    private void showMode(boolean wifi){
        stopVideo();
        wifiPanel.setVisibility(wifi?View.VISIBLE:View.GONE);
        mobilePanel.setVisibility(wifi?View.GONE:View.VISIBLE);
        videoPanel.setVisibility(View.VISIBLE);
        renderStoredIp();
    }
    private Network requireNetwork(int transport)throws Exception{
        ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network net=cm.getActiveNetwork();
        NetworkCapabilities c=net==null?null:cm.getNetworkCapabilities(net);
        if(c==null||!c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)||
                c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)||!c.hasTransport(transport)||
                (transport==NetworkCapabilities.TRANSPORT_CELLULAR && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)))
            throw new Exception(transport==NetworkCapabilities.TRANSPORT_WIFI?
                "Connecte-toi à ton Wi-Fi privé et désactive le VPN.":
                "Désactive Wi-Fi et VPN puis utilise uniquement les données mobiles 4G/5G.");
        return net;
    }
    private void startWifiScan(){
        if(!ownedNetwork.isChecked()){
            localStatus.setText("Autorisation nécessaire : coche la confirmation de propriété du réseau.");
            return;
        }
        if(scanning.get()){
            localStatus.setText("Un scan Wi-Fi est déjà en cours.");return;
        }
        final Network wifi;final LinkProperties lp;
        try{
            wifi=requireNetwork(NetworkCapabilities.TRANSPORT_WIFI);
            ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            lp=cm.getLinkProperties(wifi);
            localStatus.setText("Sous-réseau : "+HomeWifiScanner.subnetInfo(lp)+"\nDécouverte des services en cours...");
        }catch(Exception e){localStatus.setText(e.getMessage());return;}
        scanning.set(true);found=0;discovered.clear();devices.removeAllViews();
        tasks.execute(()->{
            try{
                HomeWifiScanner.scan(wifi,lp,scanning,new HomeWifiScanner.Listener(){
                    @Override public void detected(HomeWifiScanner.Host host){
                        runOnUiThread(()->{
                            if(!scanning.get())return;
                            found++;discovered.add(host);
                            button(devices,host.ip+" • TCP "+host.openPorts+" — examiner",()->hostOptions(host));
                        });
                    }
                    @Override public void progress(int done,int total){
                        runOnUiThread(()->localStatus.setText("Scan Wi-Fi : "+done+"/"+total+
                            " adresses examinées ; "+found+" appareil(s) avec service répondant.\n"+
                            "Un appareil sans ces services peut rester invisible."));
                    }
                });
                boolean lookup=scanning.get() && lookupAllowed.isChecked();
                if(lookup){
                    try{
                        String publicIp=findExternalIPv4(wifi);
                        if(scanning.get()){
                            homeIp=publicIp;
                            getPreferences(0).edit().putString("home_public_ip",homeIp)
                                .putLong("home_public_ip_time",System.currentTimeMillis()).apply();
                            runOnUiThread(this::renderStoredIp);
                        }
                    }catch(Exception e){
                        runOnUiThread(()->localStatus.append("\nAdresse extérieure non déterminée : "+e.getMessage()));
                    }
                }
                runOnUiThread(()->localStatus.append("\nDécouverte terminée. Liste non exhaustive."));
            }catch(Exception e){runOnUiThread(()->localStatus.setText("Scan interrompu : "+e.getMessage()));}
            finally{scanning.set(false);}
        });
    }
    private String findExternalIPv4(Network wifi)throws Exception{
        HttpURLConnection c=(HttpURLConnection)wifi.openConnection(new URL("https://api4.ipify.org"));
        c.setConnectTimeout(5500);c.setReadTimeout(5500);c.setUseCaches(false);
        try{
            if(c.getResponseCode()!=200)throw new Exception("Service d'IP indisponible.");
            String ip;
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream()))){
                ip=reader.readLine();
            }
            if(ip==null||!ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))
                throw new Exception("Adresse publique IPv4 non valide.");
            SecurityRules.requirePublicHost(ip);
            return ip;
        }finally{c.disconnect();}
    }
    private void renderStoredIp(){
        if(ipStatus==null)return;
        if(homeIp==null||homeIp.isEmpty())
            ipStatus.setText("Aucune IP publique mémorisée. Lance d'abord le scanner Wi-Fi de chez toi.");
        else{
            long timestamp=getPreferences(0).getLong("home_public_ip_time",0L);
            long hours=timestamp==0?0:Math.max(0,(System.currentTimeMillis()-timestamp)/3600000L);
            ipStatus.setText("Adresse publique de sortie mémorisée : "+homeIp+
                "\nDétectée il y a environ "+hours+" h. Elle peut avoir changé entre les deux tests.");
        }
    }
    private void hostOptions(HomeWifiScanner.Host h){
        new AlertDialog.Builder(this).setTitle("Appareil Wi-Fi : "+h.ip)
            .setMessage("Ports accessibles depuis ton Wi-Fi : "+h.openPorts+
                "\n\nTester une éventuelle vidéo locale sans compte ? Cela ne démontre AUCUNE exposition sur Internet.")
            .setNegativeButton("Fermer",null)
            .setPositiveButton("Tester localement",(d,w)->testLocalVideo(h)).show();
    }
    private void testLocalVideo(HomeWifiScanner.Host h){
        final Network wifi;
        try{wifi=requireNetwork(NetworkCapabilities.TRANSPORT_WIFI);}
        catch(Exception e){videoStatus.setText(e.getMessage());return;}
        videoStatus.setText("Recherche locale de média sur "+h.ip+" sans identifiant…");
        tasks.execute(()->{
            ArrayList<URL> videos=findVideoUrls(h.ip,h.openPorts,wifi);
            runOnUiThread(()->{
                if(videos.isEmpty())videoStatus.setText("Aucune vidéo locale standard trouvée sur "+h.ip+". Autres protocoles non testés.");
                else{
                    videoStatus.setText("Un média LOCAL répond. Son existence ne prouve pas un accès depuis Internet.");
                    URL first=videos.get(0);
                    new AlertDialog.Builder(this).setTitle("Vidéo locale de ton appareil")
                        .setMessage(first.toString()+"\n\nVisualisation uniquement sur ton propre réseau Wi-Fi.")
                        .setNegativeButton("Annuler",null)
                        .setPositiveButton("Voir en direct",(d,w)->playVideo(first,false)).show();
                }
            });
        });
    }
    private void startMobileScan(){
        final Network mobile;
        try{mobile=requireNetwork(NetworkCapabilities.TRANSPORT_CELLULAR);}
        catch(Exception e){remoteStatus.setText(e.getMessage());return;}
        if(homeIp==null||homeIp.isEmpty()){
            remoteStatus.setText("Adresse extérieure inconnue. Lance le scanner Wi-Fi chez toi et autorise la récupération de ton IP publique.");return;
        }
        final String ip=homeIp;
        try{SecurityRules.requirePublicHost(ip);}
        catch(Exception e){remoteStatus.setText("Adresse publique invalide : "+e.getMessage());return;}
        stopVideo();
        videoChoices.removeAllViews();
        remoteStatus.setText("Test en cours de "+ip+" depuis la 4G/5G. Aucun compte ni connexion Freebox.");
        tasks.execute(()->{
            List<Integer> ports=new ArrayList<>();
            for(int port:new int[]{80,443,554,8554,8080,8443,8000,5000,8123}){
                try(Socket s=mobile.getSocketFactory().createSocket()){
                    s.connect(new InetSocketAddress(ip,port),1100);
                    ports.add(port);
                }catch(Exception ignored){}
            }
            ArrayList<URL> videos=findVideoUrls(ip,ports,mobile);
            runOnUiThread(()->{
                remoteStatus.setText("Adresse testée : "+ip+"\nPorts TCP joignables : "+ports+
                    "\nFlux vidéo standards répondant : "+videos.size()+
                    "\nCes résultats ne certifient pas la sécurité des caméras cloud ou des protocoles non pris en charge."+
                    "\nAttention : en cas de CGNAT, cette IP pourrait être partagée avec d'autres abonnés.");
                if(videos.isEmpty()){
                    label(videoChoices,"Aucune preuve de flux vidéo extérieur. Test NON CONCLUANT sur les autres protocoles.",MUTED);
                }else{
                    label(videoChoices,"Lecture uniquement si tu reconnais un service qui t'appartient :",BLUE);
                    int limit=Math.min(videos.size(),8);
                    for(int i=0;i<limit;i++){
                        URL u=videos.get(i);
                        button(videoChoices,"Vérifier le direct : "+u.getPort()+u.getPath(),()->{
                            try{requireNetwork(NetworkCapabilities.TRANSPORT_CELLULAR);}
                            catch(Exception e){videoStatus.setText(e.getMessage());return;}
                            new AlertDialog.Builder(this).setTitle("Ton flux vidéo ?")
                                .setMessage("Adresse : "+u+"\n\nConfirme que ce service t'appartient. L'application tentera une lecture sans identifiants.")
                                .setNegativeButton("Non",null)
                                .setPositiveButton("Oui, voir le direct",(d,w)->playVideo(u,true)).show();
                        });
                    }
                }
            });
        });
    }
    private ArrayList<URL> findVideoUrls(String ip,List<Integer> ports,Network network){
        ArrayList<URL> urls=new ArrayList<>();
        String[] paths={"/video","/mjpeg","/video.mjpg","/stream","/live.m3u8"};
        for(int port:ports){
            if(port==554||port==8554)continue; // RTSP is not handled by this HTTP video player.
            String protocol=(port==443||port==8443)?"https":"http";
            for(String path:paths){
                try{
                    URL url=new URL(protocol+"://"+ip+":"+port+path);
                    HttpProbe.Result result=HttpProbe.inspect(url,network);
                    if(result.playable)urls.add(url);
                }catch(Exception ignored){}
            }
        }
        return urls;
    }
    private void playVideo(URL url,boolean publicTest){
        stopVideo();
        try{
            if(publicTest)requireNetwork(NetworkCapabilities.TRANSPORT_CELLULAR);
            else requireNetwork(NetworkCapabilities.TRANSPORT_WIFI);
        }catch(Exception e){videoStatus.setText(e.getMessage());return;}
        showingVideo=true;receivedFrame=false;
        videoStatus.setText("Tentative de décodage d'images réelles… Aucun enregistrement.");
        if(url.getPath().endsWith(".mjpg")||url.getPath().equals("/mjpeg"))playMjpeg(url,publicTest);
        else{
            tasks.execute(()->{
                try{
                    HttpProbe.Result r=HttpProbe.inspect(url);
                    runOnUiThread(()->{
                        if(!showingVideo)return;
                        if(r.mjpeg)playMjpeg(url,publicTest);
                        else if(r.playable)playNative(url,publicTest);
                        else videoStatus.setText("Aucun format vidéo lisible. Exposition non démontrée.");
                    });
                }catch(Exception e){runOnUiThread(()->videoStatus.setText("Flux indisponible : "+e.getMessage()));}
            });
        }
    }
    private void playMjpeg(URL url,boolean publicTest){
        player.setVisibility(View.GONE);mjpegView.setVisibility(View.VISIBLE);
        mjpegReader.start(url,new MjpegReader.Listener(){
            @Override public void frame(Bitmap bitmap){
                runOnUiThread(()->{
                    if(!showingVideo||isFinishing())return;
                    mjpegView.setImageBitmap(bitmap);
                    if(!receivedFrame){receivedFrame=true;showProof(publicTest);}
                });
            }
            @Override public void error(String error){
                runOnUiThread(()->{if(showingVideo&&!receivedFrame)videoStatus.setText("Vidéo non confirmée : "+error);});
            }
        });
    }
    private void playNative(URL url,boolean publicTest){
        player.setVisibility(View.VISIBLE);mjpegView.setVisibility(View.GONE);
        player.setOnErrorListener((mp,what,extra)->{videoStatus.setText("Échec de lecture : aucune vidéo démontrée.");return true;});
        player.setOnInfoListener((mp,what,extra)->{
            if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){receivedFrame=true;showProof(publicTest);}
            return false;
        });
        player.setOnPreparedListener(mp->mp.start());
        player.setVideoPath(url.toString());player.requestFocus();player.start();
    }
    private void showProof(boolean publicTest){
        videoStatus.setText(publicTest?
            "Images reçues depuis la 4G sans identifiants. Bouge devant ta caméra pour confirmer le direct. Si ce flux est privé, vérifie sa protection.":
            "Images reçues EN Wi-Fi local. Ce résultat ne prouve pas un accès depuis Internet.");
        videoStatus.setTextColor(publicTest?RED:BLUE);
    }
    private void stopVideo(){
        showingVideo=false;receivedFrame=false;
        mjpegReader.stop();
        if(player!=null){player.stopPlayback();player.setVisibility(View.GONE);}
        if(mjpegView!=null){mjpegView.setImageDrawable(null);mjpegView.setVisibility(View.GONE);}
    }
    @Override protected void onStop(){super.onStop();stopVideo();}
    @Override protected void onDestroy(){super.onDestroy();scanning.set(false);stopVideo();tasks.shutdownNow();}
}
