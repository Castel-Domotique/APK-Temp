package fr.networkguard.audit;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Network;
import android.media.MediaPlayer;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.net.Uri;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.text.InputType;
import java.net.*;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int NAVY = Color.rgb(11,20,34), PANEL = Color.rgb(22,36,56),
        TEXT = Color.WHITE, MUTED = Color.rgb(182,197,213),
        GREEN = Color.rgb(92,224,167), RED = Color.rgb(255,127,134), BLUE = Color.rgb(126,183,255);
    private LinearLayout root, videoBox;
    private EditText target, directUrl, cloudUrl;
    private TextView status, scanResult, videoStatus;
    private VideoView video;
    private ImageView mjpegImage;
    private MjpegReader mjpegReader = new MjpegReader();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private volatile boolean visuallyConfirmed = false;
    private boolean verifiedOwner = false;
    private TextView discoveryStatus;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(NAVY);
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(NAVY);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(25), dp(18), dp(30));
        scroller.addView(root);
        setContentView(scroller);
        title("NetworkGuard", 30, TEXT);
        note("MODE AVEUGLE • découverte publique sans IP à saisir", MUTED);
        section("00  RECHERCHE PUBLIQUE");
        note("Recherche dans Shodan, sans connexion à la Freebox. Les résultats peuvent appartenir à des inconnus : sélectionne seulement tes appareils.", MUTED);
        button("Rechercher les caméras indexées", () -> browse("webcam"));
        button("Rechercher les services RTSP indexés", () -> browse("port:554"));
        button("Rechercher les interfaces caméra", () -> browse("title:camera"));
        discoveryStatus = note("Choisis une recherche. Le navigateur s'ouvrira : plus de page blanche dans l'application.",BLUE);
        button("Importer une fiche Shodan copiée",this::chooseClipboard);
        note("Après la recherche, ouvre uniquement une fiche correspondant à TON IP, copie son lien /host/IP, reviens ici et appuie sur Importer. Shodan peut exiger un compte Shodan ou restreindre les résultats. Aucun compte Freebox nécessaire.", MUTED);
        section("01  TEST EXTÉRIEUR");
        note("Wi-Fi désactivé, VPN coupé. Le mode extérieur ne passe pas par le réseau local.", MUTED);
        target = input("Cible choisie depuis Shodan", false);
        target.setEnabled(false);
        target.setVisibility(View.GONE);
        note("Aucune IP à saisir. Choisis une fiche /host/IP et confirme qu'elle t'appartient.", MUTED);
        button("Scanner les ports exposés", () -> scanPorts());
        scanResult = note("Aucun scan réalisé.", MUTED);
        button("Chercher un flux vidéo standard sur MON équipement", this::probeOwnedStandardVideo);
        section("02  CAMÉRA EN DIRECT");
        note("Saisis une URL de flux HTTP(S) sur un équipement qui t'appartient. La vidéo n'est pas enregistrée.", MUTED);
        directUrl = input("https://mon-domaine:port/flux-camera", false);
        button("Tester le flux direct SANS compte", () -> probe(directUrl.getText().toString(), false));
        section("03  ACCÈS CLOUD");
        note("Teste un lien de partage cloud fourni par le fabricant. Aucun mot de passe, cookie ou session n'est utilisé.", MUTED);
        cloudUrl = input("https://lien-de-partage-fabricant/...", false);
        button("Tester le partage cloud SANS compte", () -> probe(cloudUrl.getText().toString(), true));
        videoBox = new LinearLayout(this);
        videoBox.setOrientation(LinearLayout.VERTICAL);
        videoBox.setPadding(dp(12),dp(12),dp(12),dp(12));
        videoBox.setBackground(cardBg());
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(-1, -2);
        boxParams.setMargins(0,dp(14),0,dp(10));
        root.addView(videoBox, boxParams);
        videoStatus = childText(videoBox, "Aucun direct vérifié. Une page HTML ou un port ouvert ne suffit pas.", MUTED, 14);
        video = new VideoView(this);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(-1,dp(215));
        vl.topMargin=dp(10);
        videoBox.addView(video,vl);
        video.setVisibility(View.GONE);
        mjpegImage = new ImageView(this);
        mjpegImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        videoBox.addView(mjpegImage,vl);
        mjpegImage.setVisibility(View.GONE);
        button("Arrêter le flux", this::stopPlayback);
        section("04  LIMITES");
        note("Les caméras cloud privées peuvent être absentes des index publics. Aucun résultat ne garantit leur sécurité. Audit Home Assistant séparé.", MUTED);
        section("Interpréter les résultats");
        note("• Port ouvert ≠ caméra accessible.\n• Lien avec jeton ≠ accès anonyme public.\n• Direct visible sans compte : exposition potentielle à corriger.\n• Aucun résultat ≠ réseau garanti sécurisé.\n• Si un flux est affiché, place-toi devant la caméra pour en vérifier le direct.", MUTED);
        status = note("Prêt. Autorise uniquement les tests sur tes appareils.", BLUE);
    }

    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private GradientDrawable cardBg() { GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(15));return g; }
    private TextView childText(LinearLayout container,String s,int color,int size){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(size);
        t.setLineSpacing(dp(3),1f);
        container.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    private TextView title(String s,int size,int color){return childText(root,s,color,size);}
    private TextView note(String s,int color){TextView t=title(s,14,color);t.setPadding(0,dp(7),0,dp(7));return t;}
    private void section(String s){TextView t=title(s,17,GREEN);t.setPadding(0,dp(22),0,dp(9));}
    private EditText input(String hint,boolean secret){
        EditText v=new EditText(this);
        v.setSingleLine(true);v.setTextSize(14);v.setTextColor(TEXT);v.setHintTextColor(MUTED);
        v.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        v.setHint(hint);v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setBackground(cardBg());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,dp(52));p.setMargins(0,dp(8),0,dp(6));root.addView(v,p);
        return v;
    }
    private void button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextColor(NAVY);b.setTextSize(13);b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GREEN);bg.setCornerRadius(dp(12));b.setBackground(bg);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.setMargins(0,dp(6),0,dp(5));
        root.addView(b,p);b.setOnClickListener(v->action.run());
    }
    private boolean isMobileInternet(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null) return false;
        Network n=cm.getActiveNetwork();
        if(n==null) return false;
        NetworkCapabilities cap=cm.getNetworkCapabilities(n);
        return cap!=null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            && !cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            && !cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            && cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    private boolean requireMobile(){
        if(isMobileInternet())return true;
        setStatus("Test refusé : désactive le Wi-Fi et le VPN, utilise les données mobiles 4G/5G.",RED);
        return false;
    }
    private void setStatus(String s,int color){if(status!=null) {status.setText(s);status.setTextColor(color);} }
    private void browse(String query){
        if(!requireMobile()){
            if(discoveryStatus!=null)discoveryStatus.setText("Wi-Fi ou VPN détecté : désactive-les puis réessaie sur la 4G/5G.");
            return;
        }
        String url="https://www.shodan.io/search?query="+Uri.encode(query);
        discoveryStatus.setText("Ouverture de Shodan dans ton navigateur. Sélectionne uniquement TES équipements, copie leur lien puis reviens sur Importer.");
        try {
            Intent intent=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
            startActivity(intent);
            setStatus("Recherche lancée dans le navigateur ; aucune cible scannée.",BLUE);
        }catch(Exception err){
            discoveryStatus.setText("Impossible d'ouvrir le navigateur. Vérifie qu'un navigateur Android est installé : "+err.getClass().getSimpleName());
            setStatus("Recherche impossible.",RED);
        }
    }
    private boolean selectResult(Uri uri){
        String host=uri.getHost();
        if(host!=null && (host.equalsIgnoreCase("shodan.io")||host.equalsIgnoreCase("www.shodan.io"))){
            String path=uri.getPath();
            if("https".equalsIgnoreCase(uri.getScheme()) && path!=null && path.startsWith("/host/")){
                String ip=path.substring(6);
                if(ip.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")){
                    new AlertDialog.Builder(this).setTitle("Ton équipement ?")
                        .setMessage("Adresse publique sélectionnée : "+ip+
                          "\n\nConfirme uniquement si tu reconnais cette adresse comme t'appartenant.")
                        .setNegativeButton("Non",null)
                        .setPositiveButton("Oui, c'est le mien",(d,w)->{
                            verifiedOwner=true;
                            target.setText(ip);
                            scanResult.setText("Équipement confirmé : "+ip+". Tu peux lancer son test.");
                            setStatus("Cible choisie sans saisie et sans Freebox.",BLUE);
                        }).show();
                    return true;
                }
            }
            return false;
        }
        if("https".equalsIgnoreCase(uri.getScheme()))
            startActivity(new Intent(Intent.ACTION_VIEW,uri));
        return true;
    }
    private void chooseClipboard(){
        ClipboardManager manager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        ClipData clipboard=manager==null?null:manager.getPrimaryClip();
        if(clipboard==null||clipboard.getItemCount()<1){
            discoveryStatus.setText("Presse-papiers vide : copie une fiche Shodan /host/IP depuis ton navigateur.");
            return;
        }
        CharSequence url=clipboard.getItemAt(0).coerceToText(this);
        String input=url==null?"":url.toString().trim();
        Uri uri=Uri.parse(input);
        String host=uri.getHost(), path=uri.getPath();
        if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||
          !(host.equalsIgnoreCase("shodan.io")||host.equalsIgnoreCase("www.shodan.io"))||
          path==null||!path.matches("/host/[0-9]{1,3}(\\.[0-9]{1,3}){3}")){
            discoveryStatus.setText("Lien non reconnu. Sur Shodan, ouvre la fiche d'hôte de TON équipement, puis copie son adresse de type https://www.shodan.io/host/8.8.8.8");
            return;
        }
        String ip=path.substring(6);
        pool.execute(()->{
            try{
                SecurityRules.requirePublicHost(ip);
                runOnUiThread(()->{
                    discoveryStatus.setText("Fiche reconnue : "+ip+". Confirme la propriété dans la fenêtre suivante.");
                    selectResult(uri);
                });
            }catch(Exception e){
                runOnUiThread(()->discoveryStatus.setText("Adresse privée ou invalide : copie une fiche IP publique Shodan."));
            }
        });
    }
    private void scanPorts(){
        if(!requireMobile())return;
        if(!verifiedOwner){setStatus("Sélectionne d'abord ton équipement et confirme la propriété.",RED);return;}
        String host=target.getText().toString().trim();
        if(host.contains("://")) {setStatus("Saisis seulement l'IP publique ou le domaine, sans http://.",RED);return;}
        setStatus("Scan extérieur en cours...",BLUE);
        pool.submit(()->{
            try{
                SecurityRules.requirePublicHost(host);
                int[] ports={80,443,554,8554,8080,8443,8000,5000,8123};
                StringBuilder b=new StringBuilder();
                int total=0;
                for(int port:ports){
                    if(Thread.currentThread().isInterrupted())return;
                    try(Socket socket=new Socket()){
                        socket.connect(new InetSocketAddress(host,port),1100);
                        b.append("• TCP ").append(port).append(" : connexion possible\n");
                        total++;
                    }catch(Exception ignored){}
                }
                int found=total;
                String message=(found==0?"Aucun des 9 ports testés n'a répondu. Cela ne prouve PAS que le réseau est sûr.\n":found+" port(s) joignable(s) parmi les 9 testés.\n")
                    +b+"\nUne connexion TCP ne prouve pas l'accès aux images.";
                runOnUiThread(()->{
                    // No home address stored.
                    scanResult.setText(message);scanResult.setTextColor(TEXT);
                    setStatus("Scan TCP terminé : "+found+" port(s) répond(ent), aucun flux vidéo déduit.",BLUE);
                });
            }catch(Exception ex){runOnUiThread(()->setStatus("Scan impossible : "+ex.getMessage(),RED));}
        });
    }

    private void probeOwnedStandardVideo(){
        if(!requireMobile())return;
        if(!verifiedOwner){setStatus("Confirme d'abord une fiche correspondant à ton appareil.",RED);return;}
        String ip=target.getText().toString().trim();
        setStatus("Recherche limitée de flux HTTP sur l'appareil confirmé...",BLUE);
        pool.execute(()->{
            int[] ports={80,8080,8000,443,8443};
            String[] paths={"/video","/mjpeg","/video.mjpg","/stream"};
            try{
                SecurityRules.requirePublicHost(ip);
                for(int port:ports){
                    String protocol=(port==443||port==8443)?"https":"http";
                    for(String path:paths){
                        URL u=new URL(protocol+"://"+ip+":"+port+path);
                        try{
                            HttpProbe.Result res=HttpProbe.inspect(u);
                            if(res.playable){
                                runOnUiThread(()->{
                                    if(!ip.equals(target.getText().toString()))return;
                                    directUrl.setText(u.toString());
                                    setStatus("Une piste de flux a été identifiée. Lecture directe en cours...",BLUE);
                                    probe(u.toString(),false);
                                });
                                return;
                            }
                        }catch(Exception ignored){}
                    }
                }
                runOnUiThread(()->setStatus("Aucun flux vidéo HTTP standard détecté. Cela ne prouve PAS que la caméra est protégée.",BLUE));
            }catch(Exception e){runOnUiThread(()->setStatus("Vérification non concluante : "+e.getMessage(),RED));}
        });
    }
    private void probe(String raw,boolean cloud){
        if(!requireMobile())return;
        stopPlayback();
        setStatus(cloud?"Vérification du partage cloud sans compte...":"Vérification du flux caméra sans compte...",BLUE);
        pool.submit(()->{
            try {
                URL url=SecurityRules.parsePublicHttpUrl(raw);
                boolean secret=SecurityRules.urlMayContainAccessToken(raw);
                HttpProbe.Result result=HttpProbe.inspect(url);
                runOnUiThread(()->{
                    videoStatus.setText("HTTP "+result.code+" — "+result.message+
                        (secret?"\nAttention : ce lien contient peut-être un jeton secret ; il ne prouve PAS un accès anonyme découvrable.":"")+
                        (url.getProtocol().equals("http")?"\nATTENTION : HTTP non chiffré.":""));
                    videoStatus.setTextColor(result.playable?BLUE:MUTED);
                    if(!result.playable){
                        setStatus("Pas de flux vidéo lisible prouvé par ce test.",BLUE);
                    }else{
                        setStatus("Un flux a répondu sans compte. Tentative de lecture vidéo réelle...",BLUE);
                        if(result.mjpeg) startMjpeg(url,secret);
                        else startNativeVideo(url,secret);
                    }
                });
            }catch(Exception ex){runOnUiThread(()->{
                videoStatus.setText("Test non concluant : "+ex.getMessage());
                setStatus("Aucune preuve vidéo.",RED);
            });}
        });
    }
    private void startMjpeg(URL url,boolean token){
        mjpegImage.setVisibility(View.VISIBLE); video.setVisibility(View.GONE);
        visuallyConfirmed=false;
        mjpegReader.start(url,new MjpegReader.Listener(){
            public void frame(Bitmap bitmap) {
                runOnUiThread(()->{
                    if(isFinishing())return;
                    mjpegImage.setImageBitmap(bitmap);
                    if(!visuallyConfirmed){
                        visuallyConfirmed=true;
                        markStreamRunning(token);
                    }
                });
            }
            public void error(String msg){runOnUiThread(()->{if(!visuallyConfirmed)setStatus("Erreur de lecture MJPEG : "+msg,RED);});}
        });
    }
    private void startNativeVideo(URL url,boolean token){
        visuallyConfirmed=false;video.setVisibility(View.VISIBLE);mjpegImage.setVisibility(View.GONE);
        video.setOnErrorListener((mp,what,extra)->{setStatus("Lecture impossible sur cet appareil. Flux non confirmé.",RED);return true;});
        video.setOnInfoListener((mp,what,extra)->{
            if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){
                visuallyConfirmed=true;
                markStreamRunning(token);
            }
            return false;
        });
        video.setOnPreparedListener(mp->{video.start();});
        video.setVideoPath(url.toString());
        video.requestFocus();video.start();
    }
    private void markStreamRunning(boolean token){
        setStatus(token?"Vidéo reçue via un lien possiblement secret : teste les droits de partage.":"Images reçues sans session ni identifiants. Vérifie que c'est du DIRECT en bougeant devant la caméra.",token?BLUE:RED);
        videoStatus.setText(token?
            "IMAGE RÉELLE : lecture via un lien pouvant contenir un secret. Un accès public ouvert à tous n'est PAS démontré.":
            "IMAGE RÉELLE REÇUE sans identifiant par l'application. Vérifie visuellement le direct. Corrige l'accès si ce flux est privé.");
        videoStatus.setTextColor(token?BLUE:RED);
    }
    private void stopPlayback(){
        mjpegReader.stop();
        if(video!=null){ video.stopPlayback();video.setVisibility(View.GONE); }
        if(mjpegImage!=null){mjpegImage.setImageDrawable(null);mjpegImage.setVisibility(View.GONE);}
        visuallyConfirmed=false;
    }
    @Override protected void onStop(){super.onStop();stopPlayback();}
    @Override protected void onDestroy(){super.onDestroy();stopPlayback();pool.shutdownNow();}
}
