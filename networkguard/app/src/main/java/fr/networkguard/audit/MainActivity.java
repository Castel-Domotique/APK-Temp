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
import android.os.Handler;
import android.os.Looper;
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
import android.widget.EditText;
import android.text.InputType;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.ui.PlayerView;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
    private static final int LOCATION_REQUEST=107;
    private static final int BACK=0xff0b1728, PANEL=0xff1a2d43,
        TEXT=Color.WHITE, MUTED=0xffbecfe0, GREEN=0xff68e1b2,
        BLUE=0xff91c5ff, RED=0xffffa2a2;
    private LinearLayout root,wifiPanel,mobilePanel,wifiResults,publicResults,videoPanel;
    private TextView listSummary;
    private CheckBox showAllServices;
    private TextView wifiStatus,mobileStatus,videoStatus;
    private Spinner samplePicker;
    private CheckBox sampleNotice;
    private VideoView player;
    private PlayerView rtspView;
    private ExoPlayer rtspPlayer;
    private final Handler rtspHandler=new Handler(Looper.getMainLooper());
    private Runnable rtspWatchdog;
    private int playbackGeneration=0;
    private ImageView mjpegImage;
    private final MjpegReader mjpegReader=new MjpegReader();
    private final ExecutorService work=Executors.newSingleThreadExecutor();
    private final AtomicBoolean scanning=new AtomicBoolean(false);
    private WifiManager wifi;
    private boolean wifiReceiverRegistered=false,videoPlaying=false,gotFrame=false;
    private int detected=0;
    private final java.util.Map<String,Integer> portProfiles=new java.util.HashMap<>();
    private final List<PublicSampleScanner.Host> publicHosts=new ArrayList<>();
    private final java.util.Set<String> decodedVideoHosts=new java.util.HashSet<>();

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
        label(root,"0.7.1 • RTSP : diagnostic, arrêt automatique et filtre anti-faux positifs",MUTED);
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
        label(mobilePanel,"● Vert : vraie image décodée sur un appareil confirmé.\n● Orange : seul indice RTSP, sans image vérifiée.\n● Bleu : service web non identifié.\n● Gris : profil de ports répété/suspect ou autre.",MUTED);
        showAllServices=new CheckBox(this);
        showAllServices.setText("Afficher aussi les services web et profils suspects (non prouvés)");
        showAllServices.setTextSize(13);
        showAllServices.setTextColor(TEXT);
        showAllServices.setChecked(false);
        showAllServices.setOnCheckedChangeListener((button,checked)->renderPublicResults());
        mobilePanel.addView(showAllServices);
        listSummary=label(mobilePanel,"Filtre activé : seuls les indices RTSP seront affichés.",BLUE);
        publicResults=new LinearLayout(this);publicResults.setOrientation(LinearLayout.VERTICAL);
        mobilePanel.addView(publicResults);

        videoPanel=panel(root);
        title(videoPanel,"Vérification vidéo (mes appareils uniquement)",17,GREEN);
        videoStatus=label(videoPanel,"Aucune vidéo confirmée. Un port ouvert ne démontre PAS une faille.",MUTED);
        player=new VideoView(this);
        videoPanel.addView(player,new LinearLayout.LayoutParams(-1,dp(210)));
        player.setVisibility(View.GONE);
        rtspView=new PlayerView(this);
        rtspView.setUseController(false);
        videoPanel.addView(rtspView,new LinearLayout.LayoutParams(-1,dp(245)));
        rtspView.setVisibility(View.GONE);
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
        publicResults.removeAllViews();detected=0;portProfiles.clear();publicHosts.clear();decodedVideoHosts.clear();
        renderPublicResults();
        scanning.set(true);
        mobileStatus.setText("Analyse en 4G/5G de l'échantillon "+(sample+1)+" : 254 IP au maximum.\nServices réseau seulement ; aucune vidéo ouverte.");
        work.execute(()->{
            try{
                PublicSampleScanner.scan(net,sample,scanning,new PublicSampleScanner.Listener(){
                    @Override public void detected(PublicSampleScanner.Host h){
                        runOnUiThread(()->{
                            if(!scanning.get())return;
                            detected++;
                            publicHosts.add(h);
                            String profile=h.ports.toString();
                            portProfiles.put(profile,portProfiles.containsKey(profile)?portProfiles.get(profile)+1:1);
                            renderPublicResults();
                        });
                    }
                    @Override public void progress(int count,int total){
                        runOnUiThread(()->{
                            boolean repeated=false;
                            for(Integer frequency:portProfiles.values())if(frequency>=8)repeated=true;
                            mobileStatus.setText("Analyse : "+count+"/"+total+
                                " IP • "+detected+" hôte(s) avec un port TCP répondant.\n"+
                                "Port ouvert ≠ caméra ≠ vulnérabilité."+
                                (repeated?"\n⚠️ Nombreuses IP au profil identique : résultats possiblement liés à un filtrage/interception réseau, non à des caméras.":""));
                        });
                    }
                });
                runOnUiThread(()->mobileStatus.append("\nRecherche terminée ou arrêtée. Aucun résultat ne certifie tes caméras."));
            }catch(Exception e){runOnUiThread(()->mobileStatus.setText("Erreur de recherche : "+e.getMessage()));}
            finally{scanning.set(false);}
        });
    }
    private void renderPublicResults(){
        if(publicResults==null||listSummary==null||showAllServices==null)return;
        publicResults.removeAllViews();
        boolean showAll=showAllServices.isChecked();
        int likely=0,other=0,shown=0,suspicious=0;
        for(PublicSampleScanner.Host h:publicHosts){
            int cat=VideoEvidence.category(h.ports,decodedVideoHosts.contains(h.ip));
            boolean repetition=VideoEvidence.isRepetitiveProfile(
                portProfiles.containsKey(h.ports.toString())?portProfiles.get(h.ports.toString()):0);
            if(repetition&&cat!=3){
                cat=0; // unverified: not proof that the service has no video
                suspicious++;
            }
            if(cat>=2)likely++;
            else other++;
            if(!showAll && cat<2)continue;
            shown++;
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dp(11),dp(9),dp(11),dp(9));
            row.setBackground(bg());
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
            rp.setMargins(0,dp(4),0,dp(4));
            publicResults.addView(row,rp);
            TextView dot=new TextView(this);
            dot.setText("●");
            dot.setTextSize(26);
            int dotColor=cat==3?GREEN:cat==2?0xffffbf69:cat==1?BLUE:MUTED;
            dot.setTextColor(dotColor);
            dot.setPadding(dp(2),0,dp(10),0);
            row.addView(dot);
            LinearLayout desc=new LinearLayout(this);
            desc.setOrientation(LinearLayout.VERTICAL);
            row.addView(desc,new LinearLayout.LayoutParams(0,-2,1f));
            TextView ip=new TextView(this);
            ip.setText(h.ip);ip.setTextColor(TEXT);ip.setTextSize(16);
            desc.addView(ip);
            TextView details=new TextView(this);
            details.setTextSize(12);details.setTextColor(MUTED);
            String type=cat==3?"Images vidéo décodées sur ton appareil (direct à confirmer)"
                :cat==2?"Indice RTSP : aucune image vérifiée"
                :cat==1?"Service web uniquement identifié : flux inconnu"
                :repetition?"Ports répétés sur plusieurs IP : service non identifié"
                :"Service non identifié";
            details.setText(type+" • TCP "+h.ports);
            desc.addView(details);
            row.setOnClickListener(v->chooseOwnedHost(h));
        }
        listSummary.setText("Affichés : "+shown+" / "+publicHosts.size()+
            " • Indices RTSP non répétés : "+likely+
            " • Profils répétitifs à vérifier : "+suspicious+
            " • Autres : "+(other-suspicious)+
            (showAll?"\nToutes les observations sont affichées."
                :"\nProfils répétitifs et services web masqués par défaut, sans conclure à l'absence de vidéo."+
                "\nCoche la case ci-dessus pour tous les afficher."));
    }

    private void chooseOwnedHost(PublicSampleScanner.Host h){
        // Android AlertDialog setMessage() and setMultiChoiceItems() share the
        // same content slot. A custom view keeps the ownership checkbox visible.
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22),dp(12),dp(22),dp(7));

        TextView explanation=new TextView(this);
        explanation.setText("Adresse : "+h.ip+"\nPorts TCP : "+h.ports+
            "\n\nCe résultat ne prouve ni la présence d'une caméra ni une faille."+
            "\n\nSi tu ne reconnais pas avec certitude TON adresse publique, annule. " +
            "La présence d'une IP dans un échantillon aléatoire ne prouve aucune propriété.");
        explanation.setTextSize(14);
        content.addView(explanation);

        CheckBox confirmation=new CheckBox(this);
        confirmation.setText("Je confirme que CETTE adresse IP est bien celle de mon équipement et que je suis autorisé à le tester.");
        confirmation.setTextSize(14);
        confirmation.setPadding(0,dp(8),0,dp(5));
        content.addView(confirmation);

        AlertDialog dialog=new AlertDialog.Builder(this)
            .setTitle("Tester uniquement MON équipement")
            .setView(content)
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Choisir le test",(d,which)->ownedProtocolMenu(h))
            .create();
        dialog.setOnShowListener(d->{
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setEnabled(false);
            confirmation.setOnCheckedChangeListener((v,checked)->positive.setEnabled(checked));
        });
        dialog.show();
    }
    private void ownedProtocolMenu(PublicSampleScanner.Host host){
        final boolean rtsp=host.ports.contains(554)||host.ports.contains(8554);
        String[] methods={
            "Tester les flux HTTP(S) standards",
            rtsp ? "Lire un flux RTSP de MON appareil (554/8554)" :
                "RTSP : aucun port habituel détecté"
        };
        new AlertDialog.Builder(this)
            .setTitle("Test autorisé • "+host.ip)
            .setItems(methods,(dialog,choice)->{
                if(choice==0)probeOwnedHost(host);
                else if(rtsp)chooseRtspPath(host);
                else videoStatus.setText("Aucun port RTSP habituel détecté. Cela ne prouve pas l'absence de vidéo.");
            })
            .setNegativeButton("Annuler",null).show();
    }

    private void chooseRtspPath(PublicSampleScanner.Host host){
        int rtspPort=host.ports.contains(554)?554:8554;
        final String[] paths={"/", "/live", "/live.sdp", "/Streaming/Channels/101",
            "/cam/realmonitor?channel=1&subtype=0"};
        new AlertDialog.Builder(this)
            .setTitle("Chemin RTSP de TA caméra")
            .setMessage("Un port 554 ouvert ne révèle pas l'URL du flux. Choisis un chemin compatible avec TON modèle ou saisis-en un. Aucun identifiant n'est utilisé.")
            .setPositiveButton("Chemins courants",(d,which)->new AlertDialog.Builder(this)
                .setTitle("Essai manuel : un seul chemin")
                .setItems(paths,(dlg,index)->playRtsp(host.ip,rtspPort,paths[index]))
                .setNegativeButton("Annuler",null).show())
            .setNeutralButton("Chemin personnalisé",(d,which)->{
                EditText path=new EditText(this);
                path.setSingleLine(true);
                path.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
                path.setText("/");
                new AlertDialog.Builder(this)
                    .setTitle("Chemin RTSP sur TON équipement")
                    .setView(path)
                    .setNegativeButton("Annuler",null)
                    .setPositiveButton("Voir le direct",(dlg,choice)->playRtsp(host.ip,rtspPort,
                        path.getText().toString()))
                    .show();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void playRtsp(String host,int port,String path){
        try{cellularNetwork();}
        catch(Exception e){videoStatus.setText(e.getMessage());return;}
        if(path==null||!path.startsWith("/")||path.contains("@")||path.contains("://")||
            path.contains("#")||path.contains("\n")||path.length()>300){
            videoStatus.setText("Chemin RTSP invalide : indique seulement /chemin, sans identifiants.");
            return;
        }
        stopVideo();
        videoPlaying=true;gotFrame=false;currentVideoIp=host;
        final int generation=playbackGeneration;
        videoStatus.setTextColor(BLUE);
        videoStatus.setText("RTSP : connexion en cours depuis la 4G/5G. Attente d'une image réelle sur "+
            host+":"+port+path+". Abandon automatique si aucune image sous 18 secondes.");
        rtspWatchdog=()->{
            if(generation!=playbackGeneration||!videoPlaying||gotFrame)return;
            failRtsp(generation,"Temps d'attente dépassé (18 s) : aucune image reçue. "+
                "Le chemin RTSP peut être incorrect, l'accès peut être protégé ou le service ne pas être un flux caméra.");
        };
        rtspHandler.postDelayed(rtspWatchdog,18000);
        try{
            String uri="rtsp://"+host+":"+port+path;
            rtspPlayer=new ExoPlayer.Builder(this).build();
            rtspView.setPlayer(rtspPlayer);
            rtspView.setVisibility(View.VISIBLE);
            rtspPlayer.addListener(new Player.Listener(){
                @Override public void onRenderedFirstFrame(){
                    if(generation!=playbackGeneration||!videoPlaying)return;
                    if(!gotFrame){
                        gotFrame=true;
                        if(rtspWatchdog!=null)rtspHandler.removeCallbacks(rtspWatchdog);
                        rtspWatchdog=null;
                        showVideoProof();
                    }
                }
                @Override public void onPlaybackStateChanged(int state){
                    if(generation!=playbackGeneration||!videoPlaying||gotFrame)return;
                    if(state==Player.STATE_BUFFERING)
                        videoStatus.setText("RTSP : négociation/décodage en cours, aucune image vérifiée. Limite 18 s.");
                    else if(state==Player.STATE_READY)
                        videoStatus.setText("RTSP : connexion établie, attente de la première image décodée.");
                    else if(state==Player.STATE_ENDED)
                        failRtsp(generation,"Le média s'est terminé sans aucune image décodée.");
                }
                @Override public void onPlayerError(PlaybackException e){
                    if(generation!=playbackGeneration||!videoPlaying)return;
                    String extra=e.getCause()==null?"":(": "+e.getCause().getClass().getSimpleName());
                    failRtsp(generation,"Erreur RTSP "+e.getErrorCodeName()+extra+
                        ". Vérifie que le flux de ton équipement est correct et sans authentification.");
                }
            });
            // Default RTSP transport: UDP where supported, TCP fallback when necessary.
            // Forcing RTP/TCP exclusively incorrectly rejects some legitimate cameras.
            rtspPlayer.setMediaSource(new RtspMediaSource.Factory()
                .setTimeoutMs(6000)
                .createMediaSource(MediaItem.fromUri(uri)));
            rtspPlayer.prepare();
            rtspPlayer.play();
        }catch(Exception e){
            failRtsp(generation,"Impossible de démarrer le lecteur RTSP : "+e.getClass().getSimpleName());
        }
    }

    private void failRtsp(int generation,String reason){
        if(generation!=playbackGeneration)return;
        stopVideo();
        videoStatus.setTextColor(RED);
        videoStatus.setText(reason+" Aucun flux vidéo démontré. Un écran noir ne constitue pas une preuve d'absence de vidéo.");
    }

    private void probeOwnedHost(PublicSampleScanner.Host h){
        final Network mobile;
        try{mobile=cellularNetwork();}
        catch(Exception e){videoStatus.setText(e.getMessage());return;}
        stopVideo();
        videoStatus.setText("Vérification HTTP(S) sans compte sur l’adresse confirmée "+h.ip+". Le RTSP est un test distinct.");
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
                    videoStatus.setText("Aucun flux HTTP(S) standard trouvé. Le test RTSP est disponible séparément sur ton équipement confirmé ; les clouds propriétaires ne sont pas vérifiés.");
                    return;
                }
                videoStatus.setText(candidates.size()+" réponse(s) annonçant un média. La lecture réelle reste à confirmer.");
                String[] labels=new String[candidates.size()];
                for(int i=0;i<candidates.size();i++)labels[i]=candidates.get(i).toString();
                new AlertDialog.Builder(this)
                    .setTitle("Choisir le flux de TON appareil")
                    .setItems(labels,(d,which)->playVideo(candidates.get(which)))
                    .setNegativeButton("Annuler",null)
                    .show();
            });
        });
    }
    private void playVideo(URL url){
        try{cellularNetwork();}
        catch(Exception e){videoStatus.setText(e.getMessage());return;}
        stopVideo();videoPlaying=true;gotFrame=false;
        currentVideoIp=url.getHost();
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
    private String currentVideoIp="";
    private void showVideoProof(){
        if(!currentVideoIp.isEmpty()){
            decodedVideoHosts.add(currentVideoIp);
            renderPublicResults();
        }
        videoStatus.setTextColor(RED);
        videoStatus.setText("Images reçues sans identifiants depuis la 4G/5G. Vérifie qu'elles viennent bien de TA caméra et qu'elles sont en direct.");
    }
    private void stopVideo(){
        playbackGeneration++;
        videoPlaying=false;gotFrame=false;
        if(rtspWatchdog!=null){
            rtspHandler.removeCallbacks(rtspWatchdog);
            rtspWatchdog=null;
        }
        if(rtspPlayer!=null){
            rtspView.setPlayer(null);
            rtspPlayer.release();
            rtspPlayer=null;
        }
        if(rtspView!=null)rtspView.setVisibility(View.GONE);
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
