package fr.networkguard.audit;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-assessment only. This activity neither attempts to access third-party
 * systems nor claims to detect remote webcam access from a public TCP port.
 * User identifiers stay in the EditText until the user explicitly opens a browser.
 */
public final class ThreatAuditActivity extends Activity {
    private static final int BG=0xff0b1728, PANEL=0xff1a2d43,
        GREEN=0xff68e1b2, BLUE=0xff91c5ff, PALE=0xffdae5f3, WARNING=0xffffc778;

    private LinearLayout content;
    private TextView diagnostic;
    private EditText identity;
    private final List<CheckBox> statements=new ArrayList<>();
    private String localDiagnostic="NON EFFECTUÉ";
    private boolean localAuditRun=false;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(22),dp(16),dp(36));
        scroll.addView(root);
        setContentView(scroll);

        heading(root,"NetworkGuard • Mon audit stalking",25,GREEN);
        text(root,"Deux adversaires simulés, uniquement pour tes propres comptes et équipements. "+
            "Aucune conclusion de piratage à partir d'un simple port ouvert.",PALE);
        button(root,"← Retour à NetworkGuard",this::finish);

        LinearLayout stranger=section(root);
        heading(stranger,"01 • Un inconnu qui part de zéro",19,GREEN);
        text(stranger,ThreatModel.profileDescription(0),PALE);
        text(stranger,"Ce scénario couvre ta visibilité publique et les services réellement attribuables à tes équipements. "+
            "Sans nom, IP ou identifiant, un scanner Internet ne peut pas distinguer tes caméras de celles des autres.",WARNING);
        button(stranger,"Consulter le radar Internet passif",()->{
            Toast.makeText(this,"Le radar est dans l'écran précédent. Un résultat public n'est pas une preuve que c'est chez toi.",Toast.LENGTH_LONG).show();
            finish();
        });
        addStatement(stranger,"J'ai constaté que des informations de mon domicile sont publiques");
        addStatement(stranger,"J'ai personnellement vérifié qu'un de mes équipements est accessible sans connexion");

        LinearLayout known=section(root);
        heading(known,"02 • Quelqu'un qui me connaît",19,GREEN);
        text(known,ThreatModel.profileDescription(1),PALE);
        text(known,"Tu peux commencer maintenant, sans être à la maison.",BLUE);
        identity=new EditText(this);
        identity.setSingleLine(true);
        identity.setHint("Ton pseudo, nom public ou adresse e-mail (facultatif)");
        identity.setTextColor(PALE);
        identity.setHintTextColor(0xffb3c0d0);
        known.addView(identity,new LinearLayout.LayoutParams(-1,dp(55)));
        text(known,"Aucune information saisie n'est enregistrée par NetworkGuard. "+
            "Le texte ne sera transmis au moteur de recherche que si tu appuies sur le bouton ci-dessous.",WARNING);
        button(known,"Rechercher mes propres traces publiques",this::searchOwnPublicFootprint);
        addStatement(known,"J'ai constaté des publications donnant mon adresse ou mes habitudes");
        addStatement(known,"J'ai repéré un appareil ou une session de compte que je ne reconnais pas");
        addStatement(known,"J'ai vérifié une autorisation caméra/micro ou accessibilité injustifiée");

        LinearLayout phone=section(root);
        heading(phone,"03 • Contrôle technique Android",19,GREEN);
        text(phone,"Analyse locale limitée, sans permission intrusive. "+
            "Vérifie les services d'accessibilité, administrateurs et VPN visibles par Android. "+
            "Ce ne sont pas des preuves d'espionnage.",PALE);
        button(phone,"Analyser les réglages visibles du téléphone",this::runLocalDiagnostics);
        diagnostic=text(phone,"Audit non lancé. Aucune conclusion.",BLUE);
        button(phone,"Ouvrir le tableau de bord confidentialité",()->openSettings(Settings.ACTION_PRIVACY_SETTINGS));
        button(phone,"Contrôler les services d'accessibilité",()->openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        button(phone,"Contrôler les accès aux notifications",()->openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        button(phone,"Ouvrir la sécurité (administrateurs d’appareil)",()->openSettings(Settings.ACTION_SECURITY_SETTINGS));
        button(phone,"Contrôler les applications et leurs permissions",()->openSettings(Settings.ACTION_APPLICATION_SETTINGS));

        LinearLayout webcam=section(root);
        heading(webcam,"04 • TV et webcams : test réel",19,GREEN);
        text(webcam,"Une caméra intégrée de TCL, Sony, Samsung ou LG n'est généralement pas un serveur vidéo public. "+
            "Un scanner 4G/5G ne peut ni prouver qu'elle est espionnée ni certifier sa protection.",PALE);
        text(webcam,"Quand tu seras devant l'appareil : vérifier la présence réelle du capteur, "+
            "ses autorisations, les applications de visioconférence, les accès distants, "+
            "les sessions connectées et les mises à jour. Pour une caméra IP qui t'appartient, "+
            "l'ancienne fonction RTSP/HTTP reste distincte et ne devient verte qu'après décodage d'images.",PALE);
        addStatement(webcam,"J'ai vérifié les permissions et fonctions distantes de ma télévision ou webcam");

        LinearLayout report=section(root);
        heading(report,"05 • Bilan sans fausses alertes",19,GREEN);
        text(report,"Une case cochée signifie seulement que TU déclares avoir constaté le fait. "+
            "NetworkGuard ne la transforme pas en intrusion techniquement prouvée. "+
            "Les vérifications non réalisées restent NON VÉRIFIÉES.",WARNING);
        button(report,"Copier mon compte rendu local",this::copyReport);
        text(report,"Aucune donnée n'est envoyée à un serveur par ce bilan.",BLUE);
    }

    private void searchOwnPublicFootprint(){
        String value=identity.getText().toString().trim();
        if(value.isEmpty()){
            Toast.makeText(this,"Saisis ton propre identifiant public pour une recherche ciblée.",Toast.LENGTH_LONG).show();
            return;
        }
        if(value.length()>150 || value.contains("\n")||value.contains("\r")){
            Toast.makeText(this,"Identifiant trop long ou invalide.",Toast.LENGTH_LONG).show();
            return;
        }
        String q="\""+value+"\"";
        Uri target=Uri.parse("https://www.google.com/search?q="+Uri.encode(q));
        new android.app.AlertDialog.Builder(this)
            .setTitle("Rechercher mes propres traces")
            .setMessage("La recherche va ouvrir Google dans ton navigateur. "+
                "Google recevra le texte saisi. Utilise uniquement tes propres identifiants. "+
                "NetworkGuard ne reçoit aucun résultat et ne prétend pas prouver une fuite.")
            .setNegativeButton("Annuler",null)
            .setPositiveButton("Ouvrir la recherche",(dlg,which)->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,target));}
                catch(Exception e){Toast.makeText(this,"Navigateur indisponible.",Toast.LENGTH_LONG).show();}
            }).show();
    }

    private void runLocalDiagnostics(){
        int accessibility=-1,admins=-1;
        boolean vpn=false;
        try{
            AccessibilityManager manager=(AccessibilityManager)getSystemService(Context.ACCESSIBILITY_SERVICE);
            if(manager!=null){
                List<AccessibilityServiceInfo> active=manager.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
                accessibility=active==null?0:active.size();
            }
        }catch(Exception ignored){}
        try{
            DevicePolicyManager manager=(DevicePolicyManager)getSystemService(Context.DEVICE_POLICY_SERVICE);
            if(manager!=null){
                List<ComponentName> active=manager.getActiveAdmins();
                admins=active==null?0:active.size();
            }
        }catch(Exception ignored){}
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
            Network active=cm==null?null:cm.getActiveNetwork();
            NetworkCapabilities cap=active==null?null:cm.getNetworkCapabilities(active);
            vpn=cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        }catch(Exception ignored){}
        localDiagnostic=ThreatModel.phoneSummary(accessibility,admins,vpn);
        localAuditRun=true;
        diagnostic.setText(localDiagnostic+"\n\nVérifie toi-même les noms des services dans les réglages. "+
            "Un nombre supérieur à zéro est courant et ne signifie pas espionnage.");
    }

    private void openSettings(String action){
        try{startActivity(new Intent(action));}
        catch(Exception e){
            Toast.makeText(this,"Ce réglage n'est pas disponible sur ce téléphone.",Toast.LENGTH_LONG).show();
        }
    }

    private void addStatement(LinearLayout parent,String phrase){
        CheckBox c=new CheckBox(this);
        c.setText(phrase);
        c.setTextSize(13);
        c.setTextColor(PALE);
        c.setPadding(0,dp(5),0,dp(5));
        parent.addView(c);
        statements.add(c);
    }

    private void copyReport(){
        StringBuilder report=new StringBuilder("NETWORKGUARD — Audit anti-stalking (deux profils)\n\n");
        report.append("PROFIL INCONNU\n").append(ThreatModel.profileDescription(0)).append("\n\n");
        report.append("PROFIL PERSONNE CONNUE\n").append(ThreatModel.profileDescription(1)).append("\n\n");
        report.append("OBSERVATIONS TÉLÉPHONE\n")
            .append(localAuditRun?localDiagnostic:"NON EXAMINÉ : audit local non lancé").append("\n\n");
        report.append("CONSTATS DÉCLARÉS PAR L'UTILISATEUR (non validés techniquement)\n");
        for(CheckBox check:statements){
            report.append(check.isChecked()?"[Déclaré] ":"[Non vérifié] ")
                .append(check.getText()).append("\n");
        }
        report.append("\nAucune intrusion et aucun flux vidéo ne sont confirmés par cet audit guidé. "+
            "Une recherche négative ne prouve pas l'absence de surveillance.");
        ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(cm!=null)cm.setPrimaryClip(ClipData.newPlainText("Audit NetworkGuard",report.toString()));
        Toast.makeText(this,"Compte rendu copié localement.",Toast.LENGTH_LONG).show();
    }

    private int dp(int px){return (int)(px*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(LinearLayout parent,String value,int color){
        TextView view=new TextView(this);
        view.setText(value);view.setTextColor(color);view.setTextSize(13);
        view.setPadding(0,dp(6),0,dp(10));
        parent.addView(view);
        return view;
    }
    private void heading(LinearLayout parent,String s,int size,int color){
        TextView view=text(parent,s,color);view.setTextSize(size);
    }
    private LinearLayout section(LinearLayout parent){
        LinearLayout pane=new LinearLayout(this);
        pane.setOrientation(LinearLayout.VERTICAL);
        pane.setPadding(dp(13),dp(12),dp(13),dp(15));
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(PANEL);drawable.setCornerRadius(dp(12));
        pane.setBackground(drawable);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(13),0,dp(4));parent.addView(pane,p);
        return pane;
    }
    private void button(LinearLayout parent,String title,Runnable action){
        Button b=new Button(this);
        b.setText(title);b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(BG);
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(GREEN);drawable.setCornerRadius(dp(9));
        b.setBackground(drawable);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(6),0,dp(7));
        p.height=dp(52);
        parent.addView(b,p);b.setOnClickListener(v->action.run());
    }
}
