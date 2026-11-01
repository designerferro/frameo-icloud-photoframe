package pt.photoframe;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** LAN administration remains available independently of the slideshow activity. */
public class RemoteService extends Service {
    static final int PORT=8080;
    static volatile String serverState="A iniciar";
    static volatile RemoteService current;
    private SharedPreferences prefs;
    private RemoteHttpServer server;
    private final Map<String,Session> sessions=new HashMap<>();
    private final Map<String,long[]> attempts=new HashMap<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    static final class Session {final String csrf=RemoteSecurity.token();final long until=System.currentTimeMillis()+8*60*60*1000L;}
    @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("photoframe",MODE_PRIVATE);current=this;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(!prefs.getBoolean("remoteEnabled",true)){stopSelf();return START_NOT_STICKY;}
        if(server==null)try{
            Notification.Builder notification=new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_manage).setContentTitle("Photoframe — acesso remoto").setContentText("Configuração na rede interna · porta "+PORT).setOngoing(true);
            if(Build.VERSION.SDK_INT>=26){
                Class<?> channel=Class.forName("android.app.NotificationChannel");Object value=channel.getConstructor(String.class,CharSequence.class,int.class).newInstance("remote","Acesso remoto",2);
                Object manager=getSystemService(NOTIFICATION_SERVICE);manager.getClass().getMethod("createNotificationChannel",channel).invoke(manager,value);
                notification.getClass().getMethod("setChannelId",String.class).invoke(notification,"remote");
            }
            startForeground(8,notification.build());
            server=new RemoteHttpServer(PORT,this::route);serverState="Ativo";
        }catch(Exception e){serverState="Não foi possível abrir a porta "+PORT;stopSelf();}
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){if(server!=null)try{server.close();}catch(IOException ignored){}synchronized(this){sessions.clear();}current=null;stopForeground(true);super.onDestroy();}
    synchronized void invalidate(){sessions.clear();}
    static void reset(Context context){
        RemoteService service=current;
        Object lock=service==null?RemoteService.class:service;
        synchronized(lock){context.getSharedPreferences("photoframe",MODE_PRIVATE).edit().remove("remoteUser").remove("remoteSalt").remove("remoteHash").commit();if(service!=null)service.invalidate();}
    }
    private boolean configured(){return prefs.contains("remoteHash");}
    private RemoteHttpServer.Response json(int code,JSONObject data)throws IOException{return RemoteHttpServer.Response.text(code,data.toString());}
    private RemoteHttpServer.Response error(int code,String message)throws Exception{return json(code,new JSONObject().put("error",message));}
    private synchronized Session session(RemoteHttpServer.Request request){
        long now=System.currentTimeMillis();Iterator<Session> values=sessions.values().iterator();while(values.hasNext())if(values.next().until<now)values.remove();
        String cookie=request.headers.get("cookie");if(cookie==null)return null;
        for(String part:cookie.split(";")){String[] pair=part.trim().split("=",2);if(pair.length==2&&pair[0].equals("photoframe"))return sessions.get(pair[1]);}return null;
    }
    private boolean validHost(String host){
        try{
            if(host==null||!host.matches("[A-Za-z0-9.\\[\\]:-]+"))return false;
            URI uri=new URI("http://"+host);String name=uri.getHost();
            if(name==null)return false;
            if(name.equals("localhost")||name.equals("127.0.0.1")||name.equals("[::1]"))return true;
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements())for(InetAddress address:Collections.list(interfaces.nextElement().getInetAddresses()))if(name.equals(address.getHostAddress())||name.equals("["+address.getHostAddress()+"]"))return true;
        }catch(Exception ignored){}return false;
    }
    private RemoteHttpServer.Response asset(String name,String type)throws Exception{
        return new RemoteHttpServer.Response(200,type,MainActivity.read(getAssets().open(name),180000));
    }
    private RemoteHttpServer.Response route(RemoteHttpServer.Request request)throws Exception{
        String host=request.headers.get("host");if(!validHost(host))return error(403,"Endereço não permitido");
        if(!request.method.equals("GET")&&!request.method.equals("POST"))return error(400,"Método não permitido");
        if(request.method.equals("GET")){
            if(request.path.equals("/"))return asset("remote.html","text/html; charset=utf-8");
            if(request.path.equals("/app.js"))return asset("app.js","text/javascript; charset=utf-8");
            if(request.path.equals("/style.css"))return asset("style.css","text/css; charset=utf-8");
        }
        if(request.method.equals("POST")&&!("http://"+host).equals(request.headers.get("origin")))return error(403,"Origem não permitida");
        if(request.path.equals("/api/login")&&request.method.equals("POST"))return login(request);
        Session authenticated=session(request);if(authenticated==null)return error(401,"Inicia sessão");
        if(request.path.equals("/api/session")&&request.method.equals("GET"))return json(200,new JSONObject().put("csrf",authenticated.csrf).put("firstAccess",!configured()));
        if(request.method.equals("POST")&&!RemoteSecurity.equal(authenticated.csrf,request.headers.get("x-csrf-token")))return error(403,"Sessão inválida");
        if(request.path.equals("/api/credentials")&&request.method.equals("POST"))return credentials(request);
        if(request.path.equals("/api/logout")&&request.method.equals("POST")){synchronized(this){sessions.values().remove(authenticated);}RemoteHttpServer.Response r=json(200,new JSONObject().put("ok",true));r.headers.put("Set-Cookie","photoframe=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");return r;}
        if(!configured())return error(403,"Altera o utilizador e a palavra-passe antes de configurar");
        if(request.path.equals("/api/state")&&request.method.equals("GET"))return json(200,call(activity->activity.remoteState()));
        if(request.path.equals("/api/settings")&&request.method.equals("POST")){
            JSONObject changes=new JSONObject(request.text());return json(200,call(activity->activity.remoteSettings(changes)));
        }
        if(request.path.equals("/api/action")&&request.method.equals("POST")){
            JSONObject command=new JSONObject(request.text());return json(202,call(activity->activity.remoteAction(command)));
        }
        return error(404,"Página não encontrada");
    }
    private synchronized RemoteHttpServer.Response login(RemoteHttpServer.Request request)throws Exception{
        long now=System.currentTimeMillis();long[] limit=attempts.get(request.peer);
        if(limit!=null&&now-limit[0]<60000&&limit[1]>=8)return error(429,"Demasiadas tentativas. Aguarda um minuto.");
        if(limit==null||now-limit[0]>=60000){if(attempts.size()>128)attempts.clear();limit=new long[]{now,0};attempts.put(request.peer,limit);}
        limit[1]++;
        JSONObject body=new JSONObject(request.text());String user=body.optString("username"),password=body.optString("password");
        if(password.length()>128||user.length()>48)return error(401,"Credenciais incorretas");
        boolean valid=configured()?RemoteSecurity.equal(user,prefs.getString("remoteUser",""))&&RemoteSecurity.equal(RemoteSecurity.hash(password,prefs.getString("remoteSalt","")),prefs.getString("remoteHash","")):RemoteSecurity.equal(user,"admin")&&RemoteSecurity.equal(password,"admin");
        if(!valid)return error(401,"Credenciais incorretas");
        attempts.remove(request.peer);if(sessions.size()>=32)sessions.clear();
        String id=RemoteSecurity.token();Session created=new Session();sessions.put(id,created);
        RemoteHttpServer.Response r=json(200,new JSONObject().put("csrf",created.csrf).put("firstAccess",!configured()));
        r.headers.put("Set-Cookie","photoframe="+id+"; Path=/; HttpOnly; SameSite=Strict; Max-Age=28800");return r;
    }
    private synchronized RemoteHttpServer.Response credentials(RemoteHttpServer.Request request)throws Exception{
        // Recheck authentication under the same lock as credential reset/change.
        if(session(request)==null)return error(401,"Inicia sessão novamente");
        JSONObject body=new JSONObject(request.text());String user=body.optString("username"),password=body.optString("password");
        if(!RemoteSecurity.validUsername(user)||!RemoteSecurity.validPassword(password))return error(400,"Escolhe um utilizador diferente de admin (3–48 letras, números, ponto, hífen ou sublinhado) e palavra-passe de 8–128 caracteres.");
        String salt=RemoteSecurity.token(),hash=RemoteSecurity.hash(password,salt);
        if(!prefs.edit().putString("remoteUser",user).putString("remoteSalt",salt).putString("remoteHash",hash).commit())return error(503,"Não foi possível guardar");
        sessions.clear();RemoteHttpServer.Response r=json(200,new JSONObject().put("ok",true));r.headers.put("Set-Cookie","photoframe=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");return r;
    }
    interface ActivityCall {JSONObject run(MainActivity activity)throws Exception;}
    private JSONObject call(ActivityCall action)throws Exception{
        if(MainActivity.active()==null)startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<100&&MainActivity.active()==null;i++)Thread.sleep(50);
        final MainActivity activity=MainActivity.active();if(activity==null)return new JSONObject().put("error","A apresentação não está disponível. Abre Photoframe na moldura.");
        CountDownLatch done=new CountDownLatch(1);JSONObject[] value=new JSONObject[1];
        main.post(()->{try{MainActivity live=MainActivity.active();if(live==null)throw new IOException();value[0]=action.run(live);}catch(Exception e){try{value[0]=new JSONObject().put("error",e instanceof IllegalArgumentException?e.getMessage():"Não foi possível executar a operação");}catch(Exception ignored){}}finally{done.countDown();}});
        if(!done.await(10,TimeUnit.SECONDS))throw new IOException();return value[0];
    }
    static String addresses(){
        StringBuilder values=new StringBuilder();
        try{for(NetworkInterface device:Collections.list(NetworkInterface.getNetworkInterfaces()))for(InetAddress address:Collections.list(device.getInetAddresses()))if(address instanceof Inet4Address&&!address.isLoopbackAddress()&&address.isSiteLocalAddress())values.append("http://").append(address.getHostAddress()).append(":").append(PORT).append("\n");}catch(Exception ignored){}
        return values.length()==0?"Liga a moldura à rede Wi-Fi para ver o endereço.":values.toString().trim();
    }
}
