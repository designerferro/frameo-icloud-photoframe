package pt.photoframe;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.net.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Full authentication/settings round trip on a device; restores original credentials. */
public class RemoteRunner extends Instrumentation {
    String cookie="",csrf="";
    static class Result {int code;JSONObject body;String cookie;}
    void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    Result request(String path,JSONObject body,String origin,boolean csrfHeader)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL("http://127.0.0.1:8080"+path).openConnection();
        connection.setConnectTimeout(5000);connection.setReadTimeout(20000);connection.setRequestProperty("Cookie",cookie);
        if(body!=null){connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Origin",origin);if(csrfHeader)connection.setRequestProperty("X-CSRF-Token",csrf);try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}}
        Result result=new Result();result.code=connection.getResponseCode();result.cookie=connection.getHeaderField("Set-Cookie");
        InputStream input=result.code>=400?connection.getErrorStream():connection.getInputStream();result.body=new JSONObject(new String(MainActivity.read(input,200000),"UTF-8"));connection.disconnect();return result;
    }
    Result get(String path)throws Exception{return request(path,null,"",true);}
    Result post(String path,JSONObject body)throws Exception{return request(path,body,"http://127.0.0.1:8080",true);}
    Result login(String user,String password)throws Exception{
        Result r=post("/api/login",new JSONObject().put("username",user).put("password",password));if(r.code==200){cookie=r.cookie.split(";")[0];csrf=r.body.getString("csrf");}return r;
    }
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();SharedPreferences prefs=getTargetContext().getSharedPreferences("photoframe",0);
        Map<String,?> before=new HashMap<>(prefs.getAll());JSONObject settings=null;
        try{
            getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<100&&(MainActivity.active()==null||RemoteService.current==null);i++)Thread.sleep(100);
            RemoteService.reset(getTargetContext());
            check(get("/api/state").code==401,"Anonymous state must be denied");
            check(login("admin","wrong").code==401,"Wrong password accepted");
            check(login("admin","admin").body.getBoolean("firstAccess"),"Default login does not require credential change");
            check(get("/api/state").code==403,"Bootstrap login accessed configuration");
            check(post("/api/credentials",new JSONObject().put("username","admin").put("password","admin")).code==400,"Default credentials retained");
            String user="test-"+RemoteSecurity.token().substring(0,8),password=RemoteSecurity.token();
            check(post("/api/credentials",new JSONObject().put("username",user).put("password",password)).code==200,"Credential change failed");
            check(get("/api/state").code==401,"Credential change did not invalidate sessions");
            check(login("admin","admin").code==401,"Default credentials still work");
            check(login(user,password).code==200,"New login failed");
            Result state=get("/api/state");check(state.code==200,"Authenticated state unavailable");settings=state.body;
            String album=MainActivity.active().token;
            check(!settings.has("albumToken")&&!settings.has("url")&&(album==null||!settings.toString().contains(album)),"Stored album leaked");
            check(request("/api/settings",settings,"http://127.0.0.1:8080",false).code==403,"Missing CSRF accepted");
            check(request("/api/settings",settings,"http://untrusted.invalid",true).code==403,"Foreign origin accepted");
            JSONObject changed=new JSONObject(settings.toString()).put("order","random").put("volume",0).put("crop",!settings.getBoolean("crop"));
            check(post("/api/settings",changed).body.optString("order").equals("random"),"Random order setting not applied");
            check(prefs.getString("slideOrder","").equals("random"),"Remote order not persisted");
            final HashSet<File> shown=new HashSet<>();
            runOnMainSync(()->{
                MainActivity a=MainActivity.active();a.reorder();int count=a.slides.size();
                for(int i=0;i<count;i++){a.next();check(shown.add(a.slides.get(a.index)),"Random repeated an item within a cycle");}
                if(count>1){File previous=a.slides.get(a.index);a.next();check(!previous.equals(a.slides.get(a.index)),"Random repeated across cycle boundary");}
                ArrayList<File> sequence=new ArrayList<>(a.slides);int position=a.index;
                JSONArray unchanged=new JSONArray();for(File file:a.chronological)unchanged.put(file.getName());
                try{a.loadManifest(unchanged);}catch(Exception e){throw new RuntimeException(e);}
                check(a.slides.equals(sequence)&&a.index==position,"Unchanged sync reset the random sequence");
            });
            check(post("/api/settings",new JSONObject(changed.toString()).put("order","invalid")).body.has("error"),"Unknown order accepted");
            check(post("/api/settings",new JSONObject(changed.toString()).put("volume",11)).body.has("error"),"Invalid volume accepted");
            check(post("/api/settings",new JSONObject(settings.toString()).put("order","date")).body.optString("order").equals("date"),"Chronological order failed");
            runOnMainSync(()->{
                MainActivity a=MainActivity.active();ArrayList<File> original=new ArrayList<>(a.chronological);
                try{
                    if(original.size()>1){
                        JSONArray dates=new JSONArray().put(new JSONObject().put("name",original.get(0).getName()).put("date","2001-01-01"))
                            .put(new JSONObject().put("name",original.get(1).getName()).put("date","2000-01-01"));
                        a.loadManifest(dates);check(a.slides.get(0).equals(original.get(1)),"Dates were not sorted oldest first");
                        a.loadManifest(new JSONArray().put(original.get(0).getName()));check(a.slides.size()==1,"Legacy manifest rejected");
                    }
                }catch(Exception e){throw new RuntimeException(e);}finally{a.chronological.clear();a.chronological.addAll(original);a.reorder();a.next();}
            });
            check(post("/api/action",new JSONObject().put("action","clear").put("confirm",false)).body.has("error"),"Clear action did not require confirmation");
            check(post("/api/action",new JSONObject().put("action","android")).code==202,"Android settings action failed");
            Thread.sleep(800);check(get("/api/state").code==200,"Server unavailable while Android settings are open");
            check(post("/api/logout",new JSONObject()).code==200,"Logout failed");check(get("/api/state").code==401,"Logged-out session accepted");
            result.putString("result","PASS");result.putString("remote","Login, required credentials change, session invalidation, CSRF/Origin, private album, settings/order, pause availability and logout");
        }catch(Throwable e){result.putString("error",e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{
            SharedPreferences.Editor restore=prefs.edit();
            for(String name:new String[]{"remoteUser","remoteSalt","remoteHash","crop","orientation","videoSeconds","videoVolume","slideOrder","autoUpdate","remoteEnabled"}){
                Object value=before.get(name);if(value==null)restore.remove(name);else if(value instanceof String)restore.putString(name,(String)value);else if(value instanceof Integer)restore.putInt(name,(Integer)value);else if(value instanceof Boolean)restore.putBoolean(name,(Boolean)value);
            }restore.commit();if(RemoteService.current!=null)RemoteService.current.invalidate();
            runOnMainSync(()->{MainActivity activity=MainActivity.active();if(activity!=null){activity.photo.setScaleType(prefs.getBoolean("crop",false)?android.widget.ImageView.ScaleType.CENTER_CROP:android.widget.ImageView.ScaleType.FIT_CENTER);activity.reorder();activity.next();}});
            getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish(result.containsKey("result")?-1:0,result);
    }
}
