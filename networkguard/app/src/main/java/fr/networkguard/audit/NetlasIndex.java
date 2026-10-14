package fr.networkguard.audit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Passive public index lookup: does not contact any discovered device.
 * Requires the user's own Netlas API token (possibly subject to provider quotas).
 * Public scan metadata is neither current availability nor ownership evidence.
 */
public final class NetlasIndex {
    private NetlasIndex() {}

    public static final class Item {
        public final String ip;
        public final String name;
        public final String protocol;
        public final int port;
        public final String lastSeen;
        Item(String ip,String name,String protocol,int port,String lastSeen){
            this.ip=ip;this.name=name;this.protocol=protocol;
            this.port=port;this.lastSeen=lastSeen;
        }
    }

    public static String query(int category){
        // The syntax uses documented Netlas response fields. No city-to-IP claim.
        if(category==1)return "geo.country:FR AND protocol:rtsp";
        if(category==2)return "geo.country:FR AND protocol:http";
        return "geo.country:FR AND protocol:rtsp";
    }

    public static List<Item> search(String token,int category)throws Exception{
        token=token==null?"":token.trim();
        if(token.isEmpty())throw new Exception("Clé Netlas nécessaire pour consulter cet index. Aucune adresse IP à saisir.");
        if(token.length()>1024 || token.contains("\n")||token.contains("\r"))
            throw new Exception("Clé API invalide.");
        URL url=new URL("https://app.netlas.io/api/responses/?start=0&q="+
                URLEncoder.encode(query(category),"UTF-8")+"&fields=ip,host,port,protocol,last_updated");
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestProperty("Authorization","Bearer "+token);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","NetworkGuard-Android/0.8");
        c.setConnectTimeout(9000);c.setReadTimeout(12000);
        c.setInstanceFollowRedirects(false);
        try {
            int status=c.getResponseCode();
            if(status==401||status==403)throw new Exception("Accès refusé par Netlas : vérifie ta clé et ses droits.");
            if(status==402)throw new Exception("Quota Netlas insuffisant. Aucune analyse n'a été effectuée.");
            if(status==429)throw new Exception("Limite de requêtes Netlas atteinte. Réessaie plus tard.");
            if(status!=200)throw new Exception("Index Netlas indisponible (HTTP "+status+").");
            StringBuilder body=new StringBuilder();
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream()))){
                String line;
                while((line=reader.readLine())!=null){
                    if(body.length()+line.length()>700000)
                        throw new Exception("Réponse de l'index trop volumineuse.");
                    body.append(line);
                }
            }
            JSONArray items=new JSONObject(body.toString()).optJSONArray("items");
            List<Item> result=new ArrayList<>();
            Set<String> unique=new HashSet<>();
            if(items==null)return result;
            for(int i=0;i<Math.min(20,items.length());i++){
                JSONObject envelope=items.optJSONObject(i);
                if(envelope==null)continue;
                JSONObject d=envelope.optJSONObject("data");
                if(d==null)continue;
                String ip=d.optString("ip","");
                if(!isPublicIPv4(ip))continue;
                int port=d.optInt("port",0);
                String protocol=d.optString("protocol","inconnu");
                String name=d.optString("host","");
                String seen=d.optString("last_updated","");
                String id=ip+":"+port+":"+protocol;
                if(unique.add(id))result.add(new Item(ip,name,protocol,port,seen));
            }
            return result;
        }finally{c.disconnect();}
    }

    public static boolean isPublicIPv4(String ip) {
        if(ip==null||!ip.matches("\\d{1,3}(\\.\\d{1,3}){3}"))return false;
        String[] parts=ip.split("\\.");
        int[] b=new int[4];
        for(int i=0;i<4;i++){
            b[i]=Integer.parseInt(parts[i]);
            if(b[i]>255)return false;
        }
        return b[0]!=0 && b[0]!=10 && b[0]!=127 && b[0]<224
            && !(b[0]==169&&b[1]==254)
            && !(b[0]==172&&b[1]>=16&&b[1]<=31)
            && !(b[0]==192&&b[1]==168)
            && !(b[0]==100&&b[1]>=64&&b[1]<=127)
            && !(b[0]==192&&b[1]==0&&b[2]==2)
            && !(b[0]==198&&b[1]==51&&b[2]==100)
            && !(b[0]==203&&b[1]==0&&b[2]==113);
    }
}
