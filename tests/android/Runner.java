package pt.photoframe;
import android.app.Instrumentation;
import android.os.Bundle;
import android.media.*;
import android.content.*;
import java.io.*;
public class Runner extends Instrumentation {
 void onUi(Runnable action)throws Exception{
  final Throwable[] failure=new Throwable[1];
  runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});
  if(failure[0]!=null)throw new Exception("UI check failed",failure[0]);
 }
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){
  Bundle result=new Bundle();
  try{
   File dir=getTargetContext().getFilesDir();
   for(String name:new String[]{"small","large","rotated","long","longbaseline"}){
    File output=new File(dir,name+"-out.mp4");
    VideoSupport.prepare(getTargetContext(),new File("/data/local/tmp/"+name+".mp4"),output,name.startsWith("long")?30:2);
    MediaMetadataRetriever meta=new MediaMetadataRetriever();meta.setDataSource(output.getAbsolutePath());
    String w=meta.extractMetadata(18),h=meta.extractMetadata(19),d=meta.extractMetadata(9),audio=meta.extractMetadata(16);
    if(name.equals("long")){
      MediaExtractor check=new MediaExtractor();check.setDataSource(output.getAbsolutePath());
      try{for(int track=0;track<check.getTrackCount();track++)if(check.getTrackFormat(track).getString("mime").startsWith("video/")){
        if(VideoSupport.reorderedSamples(check,track,30))throw new Exception("Converted movie still has reordered frames");
      }}finally{check.release();}
    }
    if(name.startsWith("long")){if(Integer.parseInt(d)>13000||Integer.parseInt(d)<10000)throw new Exception("Long movie duration invalid");}
    else if(Integer.parseInt(d)>2300||Integer.parseInt(d)<1000)throw new Exception("Bad duration "+d);
    if(name.equals("large")&&Integer.parseInt(w)>1280)throw new Exception("Not resized");
    if(name.equals("rotated")&&Integer.parseInt(w)>=Integer.parseInt(h))throw new Exception("Rotated portrait became landscape");
    if(!"yes".equals(audio))throw new Exception("Audio missing");
    result.putString(name,w+"x"+h+" duration="+d+" audio="+audio);meta.release();
   }
   getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   Thread.sleep(3000);
   final MainActivity[] found=new MainActivity[1];
   onUi(()->{try{
    Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("currentActivityThread").invoke(null);
    java.lang.reflect.Field f=at.getDeclaredField("mActivities");f.setAccessible(true);
    java.util.Map<?,?> records=(java.util.Map<?,?>)f.get(thread);
    for(Object record:records.values()){java.lang.reflect.Field a=record.getClass().getDeclaredField("activity");a.setAccessible(true);Object candidate=a.get(record);if(candidate instanceof MainActivity)found[0]=(MainActivity)candidate;}
   }catch(Exception e){throw new RuntimeException(e);}});
   final MainActivity activity=found[0];if(activity==null)throw new Exception("No activity");
   final boolean[] synchronizing=new boolean[1];
   for(int wait=0;wait<300;wait++){onUi(()->synchronizing[0]=activity.busy);if(!synchronizing[0])break;Thread.sleep(100);}
   if(synchronizing[0])throw new Exception("Album sync still active before playback fixture");
   final android.content.SharedPreferences prefs=activity.preferences;
   final int oldSeconds=prefs.getInt("videoSeconds",30),oldVolume=prefs.getInt("videoVolume",0);
   final AudioManager audio=(AudioManager)activity.getSystemService(android.content.Context.AUDIO_SERVICE);
   final int oldSystemVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
   try{
    onUi(()->{prefs.edit().putInt("videoSeconds",1).putInt("videoVolume",0).commit();activity.slides.clear();activity.slides.add(new File(getTargetContext().getFilesDir(),"small-out.mp4"));activity.slides.add(new File(getTargetContext().getFilesDir(),"large-out.mp4"));activity.index=-1;activity.next();});
    Thread.sleep(650);
    onUi(()->{if(activity.playing==null||!activity.video.isPlaying())throw new AssertionError("Video did not start");prefs.edit().putInt("videoVolume",10).commit();activity.applyVolume();if(audio.getStreamVolume(AudioManager.STREAM_MUSIC)!=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))throw new AssertionError("Volume 10 is not maximum");prefs.edit().putInt("videoVolume",0).commit();activity.applyVolume();});
    final boolean[] advanced=new boolean[1];
    for(int wait=0;wait<30;wait++){Thread.sleep(50);onUi(()->advanced[0]=activity.index==1);if(advanced[0])break;}
    if(!advanced[0])throw new Exception("Duration did not advance");
    onUi(()->{callActivityOnPause(activity);if(activity.playing!=null)throw new AssertionError("Playback not released on pause");});
    result.putString("playback","PASS: play, timed advance, volume 0/10, pause cleanup");
   }finally{
    onUi(()->{prefs.edit().putInt("videoSeconds",oldSeconds).putInt("videoVolume",oldVolume).commit();audio.setStreamVolume(AudioManager.STREAM_MUSIC,oldSystemVolume,0);activity.finish();});
   }
   result.putString("result","PASS");finish(-1,result);
  }catch(Throwable e){result.putString("error",android.util.Log.getStackTraceString(e));finish(0,result);}
  finally{for(String name:new String[]{"small","large","rotated","long","longbaseline"})new File(getTargetContext().getFilesDir(),name+"-out.mp4").delete();}
 }
}
