package fr.networkguard.audit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.VideoView;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST=107;
    private static final int BACK=0xff0b1728, PANEL=0xff1a2d43,
        TEXT=Color.WHITE, MUTED=0xffbecfe0, GREEN=0xff68e1b2,
        BLUE=0xff91c5ff, RED=0xffffa2a2;
    private LinearLayout root,wifiPanel,mobilePanel,wifiResults,publicResults,videoPanel;
    private TextView wifiStatus,mobileStatus,videoStatus;
    private Spinner samplePicker;
    private CheckBox sampleNotice;
    private VideoView player;
    private ImageView mjpegImage;
    private final MjpegReader mjpegReader=new MjpegReader();
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning=new AtomicBoolean(false);
    private WifiManager wifi;
    private boolean wifiReceiverRegistered=false,videoPlaying=false,gotFrame=false;
    private int detected=0;

    private final BroadcastReceiver wifiReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent intent){
            if(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())){
                boolean fresh=intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false);
                displayRadioResults(fresh);
            }
        }
    };

    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        getWindow().setStatusBarColor(BACK);
        getWindow().setNavigationBarColor(BACK);
        wifi=(WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ScrollView scroller=new ScrollView(this);
        scroller.setFillViewport(true);scroller.setBackgroundColor(BACK);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(24),dp(16),dp(28));
        scroller.addView(root);setContentView(scroller);
        title(root,"NetworkGuard",30,GREEN);
        label(root,"0.5.0 • Wi-Fi sans connexion + échantillons Internet en 4G/5G",MUTED);
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(nav);
        navButton(nav,"Radar Wi-Fi",()->showMode(true));
        navButton(nav,"Radar 4G/5G",()->showMode(false));

        wifiPanel=panel(root);
        title(wifiPanel,"01 • Wi-Fi sans connexion",20,GREEN);
        label(wifiPanel,"Recherche les bornes Wi-Fi à portée radio. Aucune connexion au réseau et aucun mot de passe.",TEXT);
        label(wifiPanel,"Une caméra qui émet son propre Wi-Fi peut apparaître. Les caméras cachées derrière une box ne sont PAS visibles par ce scan.",MUTED);
        label(wifiPanel,"Android exige la localisation précise autorisée et le service Position activé pour lire les réseaux Wi-Fi. NetworkGuard ne lit pas tes coordonnées GPS.",MUTED);
        button(wifiPanel,"Détecter les réseaux Wi-Fi à proximité",this::startRadioScan);
        wifiStatus=label(wifiPanel,"Active le Wi-Fi sans te connecter, puis lance le radar.",BLUE);
        wifiResults=new LinearLayout(this);wifiResults.setOrientation(LinearLayout.VERTICAL);
        wifiPanel.addView(wifiResults);

        mobilePanel=panel(root);
        title(mobilePanel,"02 • 4G/5G sans IP mémorisée",20,GREEN);
        label(mobilePanel,"Prototype indépendant de Shodan : connexion TCP sur 254 adresses publiques d'un échantillon. Aucune IP de chez toi n'est demandée ni récupérée.",TEXT);
        label(mobilePanel,"IMPORTANT : ces échantillons viennent du script reçu. Leur nom ne donne AUCUNE information fiable sur leur ville, et leur chance de contenir tes caméras est très faible.",RED);
        samplePicker=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item,PublicSampleScanner.SAMPLE_NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        samplePicker.setAdapter(adapter);
        mobilePanel.addView(samplePicker,new LinearLayout.LayoutParams(-1,dp(52)));
        sampleNotice=new CheckBox(this);
        sampleNotice.setText("J'ai compris : ce sont des réseaux publics tiers. Seule une connexion TCP est tentée. Je ne demanderai la vidéo que pour un équipement qui m'appartient.");
        sampleNotice.setTextSize(13);sampleNotice.setTextColor(TEXT);
        mobilePanel.addView(sampleNotice);
        button(mobilePanel,"Rechercher les services TCP dans cet échantillon",this::startSampleScan);
        button(mobilePanel,"Arrêter la recherche",()->{
            scanning.set(false);
            mobileStatus.setText("Arrêt demandé. Les résultats déjà trouvés restent affichés.");
        });
        mobileStatus=label(mobilePanel,"Aucune recherche effectuée.",BLUE);
        publicResults=new LinearLayout(this);publicResults.setOrientation(LinearLayout.VERTICAL);
        mobilePanel.addView(publicResults);

        videoPanel=panel(root);
        title(videoPanel,"Vérification vidéo (mes appareils uniquement)",17,GREEN);
        videoStatus=label(videoPanel,"Aucune vidéo confirmée. Un port ouvert ne démontre PAS une faille.",MUTED);
        player=new VideoView(this);
        videoPanel.addView(player,new LinearLayout.LayoutParams(-1,dp(210)));
        player.setVisibility(View.GONE);
        mjpegImage=new ImageView(this);
        mjpegImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        videoPanel.addView(mjpegImage,new LinearLayout.LayoutParams(-1,dp(210)));
        mjpegImage.setVisibility(View.GONE);
        button(videoPanel,"Arrêter la lecture",this::stopVideo);
        label(root,"Limites : les réseaux Wi-Fi ne donnent pas accès aux appareils derrière les routeurs ; les échantillons d'IP ne représentent pas une région. Aucun résultat ne prouve la sécurité d'un équipement cloud.",MUTED);
        registerWifiReceiver();
        showMode(true);
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable bg(){
        GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(12));return d;
    }
    private LinearLayout panel(LinearLayout parent){
        LinearLayout out=new LinearLayout(this);out.setOrientation(LinearLayout.VERTICAL);
        out.setPadding(dp(13),dp(14),dp(13),dp(17));out.setBackground(bg());
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(13),0,dp(7));parent.addView(out,p);return out;
    }
    private TextView label(LinearLayout container,String s,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(13);
        t.setPadding(0,dp(5),0,dp(8));container.addView(t);return t;
    }
    private void title(LinearLayout container,String s,int size,int color){
        TextView t=label(container,s,color);t.setTextSize(size);
    }
    private void button(LinearLayout container,String s,Runnable action){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(BACK);
        GradientDrawable d=new GradientDrawable();d.setColor(GREEN);d.setCornerRadius(dp(9));b.setBackground(d);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50));p.setMargins(0,dp(8),0,dp(5));
        container.addView(b,p);b.setOnClickListener(v->action.run());
    }
    private void navButton(LinearLayout container,String s,Runnable action){
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(12);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(53),1);
        p.setMargins(dp(2),dp(5),dp(2),dp(5));container.addView(b,p);b.setOnClickListener(v->action.run());
    }
    private void showMode(boolean radio){
        if(wifiPanel!=null)wifiPanel.setVisibility(radio?View.VISIBLE:View.GONE);
        if(mobilePanel!=null)mobilePanel.setVisibility(radio?View.GONE:View.VISIBLE);
        stopVideo();
    }
    private void registerWifiReceiver(){
        IntentFilter filter=new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if(Build.VERSION.SDK_INT>=33)
            registerReceiver(wifiReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(wifiReceiver,filter);
        wifiReceiverRegistered=true;
    }
    private boolean locationActive(){
        LocationManager lm=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if(lm==null)return false;
        if(Build.VERSION.SDK_INT>=28)return lm.isLocationEnabled();
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }
    private void startRadioScan(){
        if(wifi==null||!wifi.isWifiEnabled()){
            wifiStatus.setText("Wi-Fi désactivé. Active-le sans te connecter à un réseau.");
            startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));return;
        }
        ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active=cm.getActiveNetwork();
        NetworkCapabilities cap=active==null?null:cm.getNetworkCapabilities(active);
        if(cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){
            wifiStatus.setText("Ton téléphone est connecté au Wi-Fi. Déconnecte-le : ce mode fonctionne SANS connexion.");
            return;
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            wifiStatus.setText("Autorise la position précise pour afficher les points d'accès détectés (exigence Android).");
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST);
            return;
        }
        if(!locationActive()){
            wifiStatus.setText("Active Position dans les réglages Android, sans nécessité de partager tes coordonnées avec NetworkGuard.");
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));return;
        }
        try{
            boolean started=wifi.startScan();
            wifiStatus.setText(started?
                "Radar radio lancé. Attente des résultats…":
                "Nouveau scan limité par Android. Affichage des dernières mesures disponibles.");
            displayRadioResults(started);
        }catch(SecurityException se){
            wifiStatus.setText("Permission Wi-Fi/localisation refusée. Vérifie l'accès à la position précise.");
        }catch(Exception e){wifiStatus.setText("Erreur radar Wi-Fi : "+e.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(req,permissions,grants);
        if(req==LOCATION_REQUEST){
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                startRadioScan();
            else wifiStatus.setText("Sans localisation précise autorisée, Android bloque le scan des réseaux Wi-Fi.");
        }
    }
    private String wifiHint(String ssid){
        String s=ssid.toLowerCase(Locale.ROOT);
        String[] clues={"ipcam","ip-cam","camera","cam_","cam-","reolink","hikvision",
            "dahua","ezviz","imou","v380","tapo_cam","netcam","foscam","cctv","ipc-"};
        for(String hint:clues)if(s.contains(hint))
            return "Indice possible de réseau caméra (à vérifier)";
        return "Type d'appareil indéterminé";
    }
    private void displayRadioResults(boolean fresh){
        if(wifi==null||!wifi.isWifiEnabled())return;
        try{
            List<ScanResult> list=new ArrayList<>(wifi.getScanResults());
            Collections.sort(list,(a,b)->Integer.compare(b.level,a.level));
            wifiResults.removeAllViews();
            int n=Math.min(list.size(),80),possible=0;
            for(int i=0;i<n;i++){
                ScanResult result=list.get(i);
                String ssid=result.SSID==null||result.SSID.isEmpty()?"(SSID masqué)":result.SSID;
                String hint=wifiHint(ssid);
                if(hint.startsWith("Indice"))possible++;
                long age=Math.max(0,(SystemClock.elapsedRealtime()*1000L-result.timestamp)/1000000L);
                String details=ssid+"\n"+result.BSSID+"  •  "+result.level+" dBm  •  "+
                    result.frequency+" MHz\n"+hint;
                final String full="SSID : "+ssid+"\nBSSID : "+result.BSSID+
                    "\nPuissance : "+result.level+" dBm\nFréquence : "+result.frequency+
                    " MHz\nSécurité annoncée : "+result.capabilities+
                    "\nDernière observation : il y a ~"+age+" s\n\n"+hint+
                    "\n\nCes informations radio ne permettent pas d'accéder aux caméras d'un réseau.";
                button(wifiResults,details,()->new AlertDialog.Builder(this)
                    .setTitle("Réseau détecté").setMessage(full)
                    .setPositiveButton("OK",null).show());
            }
            wifiStatus.setText(n+" borne(s) Wi-Fi affichée(s), dont "+possible+
                " avec un nom évoquant une caméra.\n"+
                (fresh?"Résultats récents.":"Résultats possiblement anciens : Android peut limiter le scan.")+
                "\nAucun réseau rejoint, aucun appareil interne sondé.");
        }catch(SecurityException se){
            wifiStatus.setText("Android n'autorise pas la lecture du radar : vérifie l'autorisation Position précise.");
        }catch(Exception e){wifiStatus.setText("Résultats radio indisponibles : "+e.getMessage());}
    }
    private Network cellularNetwork()throws Exception{
        ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active=cm.getActiveNetwork();
        NetworkCapabilities cap=active==null?null:cm.getNetworkCapabilities(active);
        if(cap==null||!cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)||
           cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||
           cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
            throw new Exception("Utilise les données mobiles 4G/5G sans Wi-Fi connecté ni VPN.");
        return active;
    }
    private void startSampleScan(){
        if(scanning.get()){mobileStatus.setText("Recherche déjà en cours.");return;}
        if(!sampleNotice.isChecked()){
            mobileStatus.setText("Coche d'abord l'avertissement sur les échantillons publics.");return;
        }
        final Network net;
        try{net=cellularNetwork();}
        catch(Exception e){mobileStatus.setText(e.getMessage());return;}
        int sample=samplePicker.getSelectedItemPosition();
        publicResults.removeAllViews();detected=0;
        scanning.set(true);
        mobileStatus.setText("Analyse en 4G/5G de l'échantillon "+(sample+1)+" : 254 IP au maximum.\nServices réseau seulement ; aucune vidéo ouverte.");
        work.execute(()->{
            try{
                PublicSampleScanner.scan(net,sample,scanning,new PublicSampleScanner.Listener(){
                    @Override public void detected(PublicSampleScanner.Host h){
                        runOnUiThread(()->{
                            if(!scanning.get())return;
                            detected++;
                            button(publicResults,h.ip+" • ports TCP répondants : "+h.ports,
                                ()->chooseOwnedHost(h));
                        });
                    }
                    @Override public void progress(int count,int total){
                        runOnUiThread(()->mobileStatus.setText("Analyse : "+count+"/"+total+
                            " IP • "+detected+" hôte(s) avec un port répondant.\n"+
                            "Port ouvert ≠ caméra ≠ vulnérabilité."));
                    }
                });
                runOnUiThread(()->mobileStatus.append("\nRecherche terminée ou arrêtée. Aucun résultat ne certifie tes caméras."));
            }catch(Exception e){runOnUiThread(()->mobileStatus.setText("Erreur de recherche : "+e.getMessage()));}
            finally{scanning.set(false);}
        });
    }
    private void chooseOwnedHost(PublicSampleScanner.Host h){
        boolean[] confirmed={false};
        new AlertDialog.Builder(this)
            .setTitle("Vérification sur mon équipement")
            .setMessage("Adresse : "+h.ip+"\nPorts : "+h.ports+
                "\n\nLa détection d'un port ne permet pas de savoir à qui appartient cet appareil."+
                "\nLes tests HTTP et vidéo ne doivent être faits que sur un équipement dont tu es certain d'être propriétaire.")
            .setMultiChoiceItems(new String[]{"Je confirme être propriétaire de cet équipement."},
                confirmed,(dialog,which,checked)->confirmed[0]=checked)
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Vérifier mes flux",(dialog,which)->{
                if(!confirmed[0]){
                    mobileStatus.setText("Test vidéo refusé : propriété de l'équipement non confirmée.");
                    return;
                }
                probeOwnedHost(h);
            }).show();
    }
    private void probeOwnedHost(PublicSampleScanner.Host h){
        final Network mobile;
        try{mobile=cellularNetwork();}
        catch(Exception e){videoStatus.setText(e.getMessage());return;}
        stopVideo();
        videoStatus.setText("Recherche de formats vidéo HTTP(S) standards sans mot de passe sur "+h.ip+"...");
        work.execute(()->{
            List<URL> candidates=new ArrayList<>();
            String[] paths={"/video","/mjpeg","/video.mjpg","/stream","/live.m3u8"};
            for(int port:h.ports){
                if(port==554||port==8554)continue;
                String scheme=port==443?"https":"http";
                for(String path:paths){
                    try{
                        URL u=new URL(scheme+"://"+h.ip+":"+port+path);
                        HttpProbe.Result r=HttpProbe.inspect(u,mobile);
                        if(r.playable)candidates.add(u);
                    }catch(Exception ignored){}
                }
            }
            runOnUiThread(()->{
                if(candidates.isEmpty()){
                    videoStatus.setText("Aucune URL HTTP(S) vidéo standard trouvée sur TON équipement. RTSP et flux cloud privés non vérifiés.");
                    return;
                }
                videoStatus.setText(candidates.size()+" réponse(s) annonçant un média. La lecture réelle reste à confirmer.");
                URL selected=candidates.get(0);
                new AlertDialog.Builder(this).setTitle("Tester le direct réel ?")
                    .setMessage(selected.toString()+"\n\nAucun compte n'a été utilisé. Le contenu vidéo annoncé n'est pas encore une preuve de direct.")
                    .setNegativeButton("Non",null)
                    .setPositiveButton("Lire le flux", (d,w)->playVideo(selected)).show();
            });
        });
    }
    private void playVideo(URL url){
        try{cellularNetwork();}
        catch(Exception e){videoStatus.setText(e.getMessage());return;}
        stopVideo();videoPlaying=true;gotFrame=false;
        videoStatus.setText("Tentative de décodage du média en direct…");
        work.execute(()->{
            try{
                HttpProbe.Result r=HttpProbe.inspect(url);
                runOnUiThread(()->{
                    if(!videoPlaying)return;
                    if(!r.playable){videoStatus.setText("Le service ne fournit pas de vidéo vérifiable.");return;}
                    if(r.mjpeg){
                        mjpegImage.setVisibility(View.VISIBLE);
                        mjpegReader.start(url,new MjpegReader.Listener(){
                            @Override public void frame(Bitmap image){
                                runOnUiThread(()->{
                                    if(!videoPlaying||isFinishing())return;
                                    mjpegImage.setImageBitmap(image);
                                    if(!gotFrame){gotFrame=true;showVideoProof();}
                                });
                            }
                            @Override public void error(String err){
                                runOnUiThread(()->{
                                    if(videoPlaying&&!gotFrame)videoStatus.setText("Décodage MJPEG impossible : "+err);
                                });
                            }
                        });
                    }else{
                        player.setVisibility(View.VISIBLE);
                        player.setOnErrorListener((mp,what,extra)->{
                            videoStatus.setText("Le flux n'a pas pu être lu. Aucune preuve vidéo.");return true;
                        });
                        player.setOnInfoListener((mp,what,extra)->{
                            if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){
                                gotFrame=true;showVideoProof();
                            }
                            return false;
                        });
                        player.setOnPreparedListener(mp->mp.start());
                        player.setVideoPath(url.toString());
                        player.start();
                    }
                });
            }catch(Exception e){runOnUiThread(()->videoStatus.setText("Erreur vidéo : "+e.getMessage()));}
        });
    }
    private void showVideoProof(){
        videoStatus.setTextColor(RED);
        videoStatus.setText("Premières images décodées sans identifiants, depuis la 4G/5G. Bouge devant TA caméra pour vérifier le direct.");
    }
    private void stopVideo(){
        videoPlaying=false;gotFrame=false;
        mjpegReader.stop();
        if(player!=null){player.stopPlayback();player.setVisibility(View.GONE);}
        if(mjpegImage!=null){mjpegImage.setImageDrawable(null);mjpegImage.setVisibility(View.GONE);}
    }
    @Override protected void onStop(){super.onStop();stopVideo();}
    @Override protected void onDestroy(){
        super.onDestroy();
        scanning.set(false);stopVideo();work.shutdownNow();
        if(wifiReceiverRegistered)unregisterReceiver(wifiReceiver);
    }
}
