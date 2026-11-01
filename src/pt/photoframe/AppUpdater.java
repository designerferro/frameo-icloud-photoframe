package pt.photoframe;

import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Stable GitHub Releases only. APK identity, version, SHA-256 and signing cert are checked. */
final class AppUpdater {
    static final String REPOSITORY="designerferro/frameo-icloud-photoframe";
    static final String RELEASES="https://api.github.com/repos/"+REPOSITORY+"/releases/latest";
    private final MainActivity activity;
    private final ExecutorService worker;
    private final AtomicBoolean running=new AtomicBoolean();
    private final SharedPreferences prefs;
    private final PackageInfo installed;
    private final Handler ui=new Handler(Looper.getMainLooper());
    AppUpdater(MainActivity activity,ExecutorService worker){
        this.activity=activity;this.worker=worker;this.prefs=activity.getSharedPreferences("photoframe",Context.MODE_PRIVATE);
        try{installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNATURES);}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    String installedLabel(){return installed.versionName+" ("+installed.versionCode+")";}
    String latestLabel(){return prefs.getString("latestRelease","ainda não consultada");}
    void say(String message){ui.post(()->{if(!activity.destroyed)Toast.makeText(activity,message,Toast.LENGTH_LONG).show();});}
    void check(boolean manual){
        if(!manual&&System.currentTimeMillis()-prefs.getLong("lastApkCheck",0)<21600000)return;
        if(!running.compareAndSet(false,true)){if(manual)say("Já está a verificar uma atualização.");return;}
        if(manual)say("A consultar a última release no GitHub…");
        worker.execute(()->{
            try{
                JSONObject release=new JSONObject(new String(fetch(RELEASES,2000000),"UTF-8"));
                if(release.optBoolean("draft")||release.optBoolean("prerelease"))throw new IOException("Release não estável");
                String tag=release.getString("tag_name");
                String metadataUrl=asset(release,"update.json");
                JSONObject metadata=new JSONObject(new String(fetch(metadataUrl,32768),"UTF-8"));
                int code=metadata.getInt("versionCode");String name=metadata.getString("versionName");
                if(!VersionPolicy.validVersion(name)||!tag.equals("v"+name)||!REPOSITORY.equals(metadata.getString("repository")))throw new IOException("Metadados inconsistentes");
                String checksum=metadata.getString("sha256");
                if(!VersionPolicy.validHash(checksum))throw new IOException("Checksum inválido");
                String expected=VersionPolicy.apkName(name);
                if(!expected.equals(metadata.getString("apk")))throw new IOException("Nome APK inválido");
                String download=asset(release,expected);
                if(!download.startsWith("https://github.com/"+REPOSITORY+"/releases/download/"+tag+"/"))throw new IOException("Origem APK inválida");
                prefs.edit().putString("latestRelease",name+" ("+code+")").putLong("lastApkCheck",System.currentTimeMillis()).apply();
                if(!VersionPolicy.eligible(installed.versionCode,code,false)){
                    if(manual){
                        if(code==installed.versionCode){
                            ui.post(()->{if(!activity.destroyed)new AlertDialog.Builder(activity).setTitle("APK atualizado")
                                .setMessage("Instalado: "+installedLabel()+"\nGitHub: "+name+" ("+code+")\nPodes forçar a reinstalação desta release sem apagar os dados.")
                                .setPositiveButton("Reinstalar",(d,w)->downloadAndInstall(download,checksum,code,name)).setNegativeButton("Cancelar",null).show();});
                        }else say("Instalado: "+installedLabel()+". GitHub: "+name+" ("+code+"). Não será feito downgrade.");
                    }
                    return;
                }
                prepareAndInstall(download,checksum,code,name,manual);
            }catch(Exception e){
                android.util.Log.w("PhotoframeUpdate","CHECK_FAILED "+e.getClass().getSimpleName());
                // Back off automatic requests for 15 minutes, including HTTP 403/429.
                prefs.edit().putLong("lastApkCheck",System.currentTimeMillis()-20700000).apply();
                if(manual)say("Não foi possível atualizar: "+e.getMessage());
            }finally{running.set(false);}
        });
    }
    private void downloadAndInstall(String url,String hash,int code,String name){
        if(!running.compareAndSet(false,true)){say("Já está a atualizar.");return;}
        worker.execute(()->{try{prepareAndInstall(url,hash,code,name,true);}catch(Exception e){say("Atualização recusada: "+e.getMessage());}finally{running.set(false);}});
    }
    private void prepareAndInstall(String url,String checksum,int code,String name,boolean manual)throws Exception{
        if(!VersionPolicy.eligible(installed.versionCode,code,manual))throw new IOException("Versão inferior à instalada");
        if(manual)say("A descarregar Photoframe "+name+"…");
        byte[] apk=fetch(url,20000000);
        StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(apk))hex.append(String.format(Locale.US,"%02x",b&255));
        if(!checksum.equalsIgnoreCase(hex.toString()))throw new IOException("SHA-256 não coincide");
        File temp=new File(activity.getFilesDir(),"download-update.tmp");MainActivity.write(temp,apk);
        PackageInfo candidate=activity.getPackageManager().getPackageArchiveInfo(temp.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        if(candidate==null||!activity.getPackageName().equals(candidate.packageName)||candidate.versionCode!=code||!name.equals(candidate.versionName))throw new IOException("Identidade ou versão do APK inválida");
        if(!sameSignatures(installed.signatures,candidate.signatures))throw new IOException("Assinatura diferente. Só a mesma chave pode atualizar esta instalação.");
        File verified=new File(activity.getFilesDir(),"verified-update.apk");
        if(!temp.renameTo(verified))throw new IOException("Não foi possível preparar o APK");
        // Stage root installation using fixed private paths. pm install -r also enforces signature continuity.
        String root=runRoot("id",10000);
        if(root.contains("uid=0(")){
            if(manual)say("APK verificado. A instalar com root…");
            String staged="/data/local/tmp/photoframe-update.apk";
            String source=verified.getAbsolutePath();
            String command="cat '"+source+"' > '"+staged+"' && chmod 0644 '"+staged+"' && pm install -r '"+staged+"'";
            String result=runRoot(command,120000);
            if(!result.contains("Success"))throw new IOException("Falhou a instalação root; abre o menu para tentar novamente.");
        }else{
            ui.post(()->{
                if(activity.destroyed)return;
                new AlertDialog.Builder(activity).setTitle("Photoframe "+name+" disponível")
                    .setMessage("APK e assinatura verificados. Sem root, o Android precisa de confirmar a instalação. Os dados do álbum são preservados.")
                    .setPositiveButton("Instalar",(d,w)->{
                        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://pt.photoframe.updates/latest.apk"),"application/vnd.android.package-archive");
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try{activity.startActivity(intent);}catch(Exception e){say("Não foi possível abrir o instalador Android.");}
                    }).setNegativeButton("Mais tarde",null).show();
            });
        }
    }
    static boolean sameSignatures(Signature[] a,Signature[] b){
        if(a==null||b==null||a.length==0||a.length!=b.length)return false;
        HashSet<Signature> x=new HashSet<>(Arrays.asList(a));return x.equals(new HashSet<>(Arrays.asList(b)));
    }
    private static String runRoot(String command,long timeout){
        java.lang.Process p=null;
        try{
            p=Runtime.getRuntime().exec(new String[]{"su","-c",command});
            long until=SystemClock.elapsedRealtime()+timeout;
            while(true){try{p.exitValue();break;}catch(IllegalThreadStateException e){if(SystemClock.elapsedRealtime()>until){p.destroy();return "";}Thread.sleep(100);}}
            return new String(MainActivity.read(p.getInputStream(),65536),"UTF-8");
        }catch(Exception e){if(p!=null)p.destroy();return "";}
    }
    private static String asset(JSONObject release,String name)throws Exception{
        JSONArray assets=release.getJSONArray("assets");
        for(int i=0;i<assets.length();i++){
            JSONObject a=assets.getJSONObject(i);
            if(name.equals(a.getString("name"))){
                String url=a.getString("browser_download_url");
                if(!url.startsWith("https://github.com/"+REPOSITORY+"/releases/download/"))throw new IOException("Asset fora do repositório");
                return url;
            }
        }
        throw new IOException("Release sem "+name);
    }
    private static byte[] fetch(String address,int limit)throws Exception{
        for(int redirects=0;redirects<6;redirects++){
            URL url=new URL(address);String host=url.getHost();
            if(!"https".equals(url.getProtocol())||!(host.equals("api.github.com")||host.equals("github.com")||host.endsWith(".githubusercontent.com")))throw new IOException("Origem de atualização não permitida");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(20000);connection.setReadTimeout(45000);connection.setRequestProperty("User-Agent","Photoframe-Codex");
            connection.setRequestProperty("Accept",host.equals("api.github.com")?"application/vnd.github+json":"application/octet-stream");
            try{
                int status=connection.getResponseCode();
                if(status>=300&&status<400){address=new URL(url,connection.getHeaderField("Location")).toString();continue;}
                if(status!=200)throw new IOException("GitHub HTTP "+status);
                return MainActivity.read(connection.getInputStream(),limit);
            }finally{connection.disconnect();}
        }
        throw new IOException("Demasiados redirecionamentos");
    }
}
