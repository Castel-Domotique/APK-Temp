package fr.networkguard.audit;

import java.util.Locale;

/**
 * Universal security-check categories. No particular model is assumed
 * to contain a webcam or to expose remote camera controls.
 */
public final class UniversalDeviceProfiles {
    private UniversalDeviceProfiles(){}
    public static final String[] BRANDS={"Toutes les marques","TCL","Sony","Samsung","LG","Autre"};
    public static final String[] TYPES={
        "Caméra IP / RTSP",
        "Webcam de PC / USB",
        "Smart TV avec caméra intégrée",
        "Tous fabricants de caméras IP (RTSP)"
    };
    public static String manufacturer(String text){
        if(text==null)return "Non identifiée";
        String s=text.toLowerCase(Locale.ROOT);
        if(s.contains("tcl"))return "TCL";
        if(s.contains("sony")||s.contains("bravia"))return "Sony";
        if(s.contains("samsung"))return "Samsung";
        if(s.contains("webos")||s.contains("lg "))return "LG";
        return "Non identifiée";
    }
    public static String capabilityWarning(int type){
        if(type==2)return "TV : la caméra intégrée est pilotée par le système et ses applications. "
            +"Une recherche Internet passive ne peut pas prouver qu'elle est accessible.";
        if(type==1)return "Webcam PC/USB : pas de présence Internet autonome. "
            +"Le risque dépend de l'ordinateur et des logiciels qui utilisent sa caméra.";
        if(type==0)return "Caméra IP : un index peut signaler un service RTSP, "
            +"mais ce n'est pas une preuve d'images accessibles.";
        return "Universalité des catégories et des fabricants, pas de détection garantie des flux privés.";
    }
    public static String offlineWebcamAdvice(){
        return "Une webcam intégrée ou USB n'a généralement pas d'adresse Internet autonome. "+
            "Il faut examiner l'ordinateur, les applications qui utilisent la caméra, leurs autorisations et les accès distants.\n\n"+
            "Sur Windows/macOS/Linux : vérifier les autorisations caméra, les applications de visioconférence, "+
            "le partage de bureau et les mises à jour.\n\n"+
            "Un index Internet ne peut pas prouver que quelqu'un active la webcam.";
    }

    public static String offlineTvAdvice(String brand){
        return "Contrôles universels pour "+brand+" :\n"+
            "• Confirmer physiquement la présence d'une caméra (certaines TV n'en ont pas).\n"+
            "• Vérifier les applications autorisées à utiliser la caméra et le micro.\n"+
            "• Désactiver les autorisations non nécessaires, le partage à distance et les comptes inutilisés.\n"+
            "• Mettre le système et les applications à jour.\n"+
            "• Vérifier le témoin ou le cache physique de la caméra, s'il existe.\n\n"+
            "Les menus exacts varient selon la référence. Aucun accès à la TV n'est réalisé ici.";
    }
}
