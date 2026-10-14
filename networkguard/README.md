# NetworkGuard 0.1 — prototype Android

**Objectif :** auditer uniquement son propre réseau depuis les données mobiles et tester si des flux vidéo HTTP/HTTPS sont accessibles sans compte.

## Mode 1 (implémenté)
- Bloque le test si le téléphone n'utilise pas le réseau mobile (4G/5G), ou si un VPN est actif.
- Entrée volontaire de l'IP publique ou du domaine du domicile ; scan TCP limité à des ports usuels.
- Test d'une URL caméra directe, ou d'un lien de partage cloud fourni par le propriétaire.
- Requête HTTP sans Cookies ni en-tête Authorization ; refuse les redirections comme preuves.
- Ne déclare pas un flux confirmé d'après une page HTML ou un simple port ouvert.
- Lecture en direct MJPEG si images réellement décodées ; lecture vidéo HTTP/HLS via les capacités du MediaPlayer Android.
- Mention distincte pour un lien contenant potentiellement un jeton secret.
- Ne réalise ni connexion par force brute, ni contournement du cloud fabricant, ni extraction de contenu privé.
- Écran de conseils pour désactiver la publication de flux sans authentification.

## Mode 2 (prévu, PAS ENCORE implémenté)
- Agent sécurisé Home Assistant Green / inventaire local et analyse de configuration.
- Connexion distante chiffrée, contrôle d'accès, comparateur avant/après correction.

## Limites volontaires
- Aucune découverte automatique de tous les fabricants cloud : elle dépend de leurs API et des permissions de l'utilisateur.
- RTSP hors HTTP/HTTPS non géré dans cette version.
- Un lien cloud à jeton secret n'est PAS une preuve qu'un inconnu pourrait trouver le lien.
- Un flux web non décodable ne peut PAS être annoncé « confirmé ».
- Un scan sans résultat n'est PAS une preuve de sécurité.
- La compatibilité caméra dépend du protocole. Le flux doit être validé visuellement par son propriétaire.

**Vie privée :** pas d'analyse sur serveur tiers, pas d'enregistrement automatique de vidéo, aucune permission caméra nécessaire.
