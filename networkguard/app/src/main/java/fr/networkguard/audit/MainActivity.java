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
    private LinearLayout root,wifiPanel,mobilePanel,wifiResults,indexResults,videoPanel;
    private TextView wifiStatus,indexStatus,videoStatus;
    private Spinner typeChooser,brandChooser;
    private EditText netlasKey;
    private final List<NetlasIndex.Item> indexed=new ArrayList<>();
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
        label(root,"0.9.0 • Audit anti-stalking : 2 scénarios + radars",MUTED);
        LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(nav);
        navButton(nav,"Radar Wi-Fi",()->showMode(true));
        navButton(nav,"Radar 4G/5G",()->showMode(false));
        button(root,"Audit anti-stalking • Inconnu + personne qui me connaît",()->
            startActivity(new Intent(this,ThreatAuditActivity.class)));

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
        title(mobilePanel,"02 • Recherche Internet 4G/5G",20,GREEN);
        label(mobilePanel,"Sans IP à saisir, sans Wi-Fi domestique ni connexion Freebox. Recherche uniquement dans un index public ; aucun équipement distant n'est sondé pendant la découverte.",TEXT);
        label(mobilePanel,"La recherche n'est PAS exhaustive. Elle ne révèle pas les webcams privées intégrées aux télévisions ou aux ordinateurs.",MUTED);

        label(mobilePanel,"Type d'appareil recherché",BLUE);
        typeChooser=new Spinner(this);
        ArrayAdapter<String> types=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,
            UniversalDeviceProfiles.TYPES);
        types.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeChooser.setAdapter(types);
        mobilePanel.addView(typeChooser,new LinearLayout.LayoutParams(-1,dp(52)));

        label(mobilePanel,"Marque pour le guide TV (ne filtre pas les résultats Internet)",BLUE);
        brandChooser=new Spinner(this);
        ArrayAdapter<String> brands=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,
            UniversalDeviceProfiles.BRANDS);
        brands.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        brandChooser.setAdapter(brands);
        mobilePanel.addView(brandChooser,new LinearLayout.LayoutParams(-1,dp(52)));

        label(mobilePanel,"Index Netlas : une clé API personnelle peut être obtenue avec leur formule Community gratuite. Limites de requêtes selon ton compte.",MUTED);
        netlasKey=new EditText(this);
        netlasKey.setHint("Clé API Netlas — pas d'adresse IP");
        netlasKey.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        netlasKey.setSingleLine(true);
        netlasKey.setTextColor(TEXT);
        netlasKey.setHintTextColor(MUTED);
        mobilePanel.addView(netlasKey,new LinearLayout.LayoutParams(-1,dp(52)));
        label(mobilePanel,"La clé n'est pas sauvegardée par NetworkGuard et n'est transmise qu'au service Netlas via HTTPS.",MUTED);
        button(mobilePanel,"Ouvrir Netlas pour obtenir une clé gratuite",()->{
            Intent it=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://app.netlas.io/"));
            try{startActivity(it);}catch(Exception e){
                indexStatus.setText("Impossible d'ouvrir Netlas : "+e.getMessage());
            }
        });
        button(mobilePanel,"Rechercher sans IP (données publiques)",this::searchPublicIndex);
        button(mobilePanel,"Guide universel des caméras TV",this::showUniversalGuide);
        indexStatus=label(mobilePanel,"Prêt. Choisis un type d'appareil. Le mode TV propose un guide ; l'index détecte seulement des services Internet.",BLUE);
        indexResults=new LinearLayout(this);
        indexResults.setOrientation(LinearLayout.VERTICAL);
        mobilePanel.addView(indexResults);
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
    private void showUniversalGuide(){
        final int which=brandChooser.getSelectedItemPosition();
        final String brand=UniversalDeviceProfiles.BRANDS[which];
        new AlertDialog.Builder(this)
            .setTitle("Vérification universelle • "+brand)
            .setMessage(UniversalDeviceProfiles.offlineTvAdvice(brand))
            .setPositiveButton("Compris",null).show();
    }

    private void searchPublicIndex(){
        try{cellularNetwork();}
        catch(Exception e){indexStatus.setText(e.getMessage());return;}
        final int type=typeChooser.getSelectedItemPosition();
        final String token=netlasKey.getText().toString().trim();
        if(type==2){
            indexStatus.setText("Une caméra intégrée à une TV ne peut pas être identifiée par un index Internet. Le guide multi-marques explique les vérifications futures.");
            showUniversalGuide();
            return;
        }
        if(type==1){
            indexStatus.setText("Les webcams PC/USB ne sont pas identifiables directement par un index Internet. Vérifie plutôt les permissions et logiciels de l'ordinateur qui les utilise.");
            new AlertDialog.Builder(this)
                .setTitle("Webcam intégrée / USB")
                .setMessage(UniversalDeviceProfiles.offlineWebcamAdvice())
                .setPositiveButton("Compris",null).show();
            return;
        }
        if(token.isEmpty()){
            indexStatus.setText("Pour une recherche automatique en arrière-plan, saisis uniquement une clé Netlas (compte Community gratuit). Aucune adresse de tes appareils nécessaire.");
            return;
        }
        indexResults.removeAllViews();
        indexed.clear();
        indexStatus.setText("Consultation passive de l'index Netlas en 4G/5G… aucune connexion à une caméra.");
        work.execute(()->{
            try{
                List<NetlasIndex.Item> response=NetlasIndex.search(token,type);
                runOnUiThread(()->{
                    indexed.clear();indexed.addAll(response);
                    indexStatus.setText(response.size()+" observation(s) publiques retournées. Un indice de service ne prouve ni la présence d'une webcam ni l'accès à ses images.");
                    renderIndexResults();
                });
            }catch(Exception e){runOnUiThread(()->indexStatus.setText("Recherche indisponible : "+e.getMessage()));}
        });
    }

    private void renderIndexResults(){
        if(indexResults==null)return;
        indexResults.removeAllViews();
        for(NetlasIndex.Item result:indexed){
            boolean evidence=decodedVideoHosts.contains(result.ip);
            LinearLayout entry=new LinearLayout(this);
            entry.setOrientation(LinearLayout.VERTICAL);
            entry.setPadding(dp(12),dp(9),dp(12),dp(8));
            entry.setBackground(bg());
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(0,dp(5),0,dp(5));
            indexResults.addView(entry,p);
            String title=(evidence?"● Images décodées (sur équipement confirmé)":"● Observation indexée seulement")+
                "\n"+result.ip+(result.port>0?":"+result.port:"")+
                (result.name.isEmpty()?"":" • "+result.name);
            TextView primary=label(entry,title,evidence?GREEN:MUTED);
            primary.setTextSize(14);
            label(entry,"Protocole indexé : "+result.protocol+
                (result.lastSeen.isEmpty()?"":"\nObservation : "+result.lastSeen)+
                "\nAucun test actif effectué.",MUTED);
            entry.setOnClickListener(v->confirmIndexOwnership(result));
        }
    }

    private void confirmIndexOwnership(NetlasIndex.Item item){
        List<Integer> ports=new ArrayList<>();
        if(item.port>0 && item.port<=65535)ports.add(item.port);
        PublicSampleScanner.Host h=new PublicSampleScanner.Host(item.ip,ports);
        chooseOwnedHost(h);
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
            renderIndexResults();
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
