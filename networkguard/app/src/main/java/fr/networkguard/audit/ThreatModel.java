package fr.networkguard.audit;

/** Never elevate a public clue or an unchecked item to a proven exploit. */
public final class ThreatModel {
    private ThreatModel(){}

    public static final String[] PROFILES={
        "INCONNU — part de zéro",
        "PERSONNE QUI TE CONNAÎT — indices personnels"
    };

    public static String status(boolean testActuallyConfirmed, boolean observedPotentialRisk){
        if(testActuallyConfirmed)return "FAIT TECHNIQUE VÉRIFIÉ";
        if(observedPotentialRisk)return "POINT À EXAMINER — AUCUNE INTRUSION PROUVÉE";
        return "NON VÉRIFIÉ — NE PROUVE PAS L'ABSENCE DE RISQUE";
    }

    public static String phoneSummary(int accessibility, int administrators, boolean vpn){
        String services=accessibility<0?"non disponibles":(accessibility+" visible(s)");
        String admins=administrators<0?"non disponibles":(administrators+" visible(s)");
        return "Audit des réglages Android :\n"+
            "• Services d'accessibilité activés : "+services+".\n"+
            "• Administrateurs d'appareil : "+admins+".\n"+
            "• VPN actif sur la connexion : "+(vpn?"oui":"non détecté")+".\n"+
            "• Autorisations caméra/micro, notifications, comptes et sessions : À VÉRIFIER dans les réglages.\n\n"+
            "Ces observations ne prouvent PAS qu'une personne t'espionne et ne détectent pas toutes les applications.";
    }

    public static String profileDescription(int profile){
        if(profile==0)
            return "Simulation d'un inconnu sans ton nom, ton IP ni ta localisation. "+
                "La recherche d'équipements quelconques sur Internet ne peut pas les relier à ton domicile. "+
                "Un flux vidéo ne peut être testé qu'après identification et autorisation de ton équipement.";
        return "Simulation d'une personne possédant ton nom ou pseudo : rechercher les publications "+
            "publiques, les indices de lieu, les paramètres de confidentialité, "+
            "les accès aux comptes et les applications pouvant surveiller le téléphone.";
    }
}
