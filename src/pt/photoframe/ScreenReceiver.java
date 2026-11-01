package pt.photoframe;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
/** Exact wakeup alarm works during sleep, without keeping the CPU awake overnight. */
public class ScreenReceiver extends BroadcastReceiver {
    static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static boolean sleeping(Context c){
        android.content.SharedPreferences p=c.getSharedPreferences("photoframe",0);
        Calendar now=Calendar.getInstance();
        return p.getBoolean("screenSchedule",false)&&ScreenSchedule.asleep(now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE),ScreenSchedule.minute(p.getString("screenOff","22:00")),ScreenSchedule.minute(p.getString("screenOn","08:00")));
    }
    static void refresh(Context context){
        Context c=context.getApplicationContext();
        android.content.SharedPreferences p=c.getSharedPreferences("photoframe",0);
        AlarmManager alarms=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent alarm=PendingIntent.getBroadcast(c,42,new Intent(c,ScreenReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(alarm);
        if(p.getBoolean("screenSchedule",false))alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,ScreenSchedule.next(Calendar.getInstance(),ScreenSchedule.minute(p.getString("screenOff","22:00")),ScreenSchedule.minute(p.getString("screenOn","08:00"))),alarm);
        boolean off=sleeping(c);
        MainActivity activity=MainActivity.active();
        if(activity!=null){
            if(off){activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.stopVideo();activity.ui.removeCallbacks(activity.advance);}
            else activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        // Only undo sleep requested by this feature; do not wake unrelated manual sleep.
        if(!off&&!p.getBoolean("screenSleeping",false))return;
        PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock lock=power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"photoframe:schedule");lock.acquire(15000);
        worker.execute(()->{java.lang.Process process=null;try{
            process=new ProcessBuilder("su","-c",off?"input keyevent 223":"input keyevent 224").redirectErrorStream(true).start();
            long deadline=SystemClock.elapsedRealtime()+8000;
            while(true){try{if(process.exitValue()!=0)throw new java.io.IOException();break;}catch(IllegalThreadStateException running){if(SystemClock.elapsedRealtime()>=deadline)throw new java.io.IOException();Thread.sleep(50);}}
            p.edit().putBoolean("screenSleeping",off).putString("screenStatus",off?"Ecrã desligado pelo horário":"Ecrã ligado").commit();
            if(!off)c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }catch(Exception e){p.edit().putString("screenStatus","Não foi possível controlar o ecrã. Autoriza o acesso root para Photoframe.").apply();}
        finally{if(process!=null)process.destroy();if(lock.isHeld())lock.release();}});
    }
    @Override public void onReceive(Context c,Intent i){final PendingResult pending=goAsync();try{refresh(c);worker.execute(()->pending.finish());}catch(Exception e){pending.finish();}}
}
