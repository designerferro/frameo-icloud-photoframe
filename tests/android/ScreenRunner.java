package pt.photoframe;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
/** Exercises real sleep and the exact wakeup alarm, restoring schedule preferences. */
public class ScreenRunner extends Instrumentation {
    public void onCreate(Bundle b){super.onCreate(b);start();}
    void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();SharedPreferences p=c.getSharedPreferences("photoframe",0);
        boolean enabled=p.getBoolean("screenSchedule",false);String off=p.getString("screenOff","22:00"),on=p.getString("screenOn","08:00");
        PowerManager.WakeLock lock=((PowerManager)c.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"photoframe:test");lock.acquire(90000);
        try{
            c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1500);
            Calendar now=Calendar.getInstance();int n=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
            String start=String.format(Locale.US,"%02d:%02d",n/60,n%60);int end=(n+1)%1440;String finish=String.format(Locale.US,"%02d:%02d",end/60,end%60);
            runOnMainSync(()->MainActivity.active().saveScreen(true,start,finish));
            Thread.sleep(2500);
            PowerManager power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);
            check(!power.isInteractive(),"Screen did not switch off: "+p.getString("screenStatus",""));
            long deadline=SystemClock.elapsedRealtime()+70000;
            while(!power.isInteractive()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(250);
            check(power.isInteractive(),"Exact alarm did not wake the screen");
            for(int i=0;i<40&&p.getBoolean("screenSleeping",true);i++)Thread.sleep(100);
            check(!p.getBoolean("screenSleeping",true),"Sleep marker not cleared");
            result.putString("result","PASS");result.putString("details","Physical sleep and scheduled wakeup passed on Android 6");
        }catch(Throwable e){result.putString("result","FAIL: "+e);}
        finally{
            runOnMainSync(()->{p.edit().putBoolean("screenSchedule",false).commit();ScreenReceiver.refresh(c);});
            try{Thread.sleep(1200);}catch(Exception ignored){}
            runOnMainSync(()->{p.edit().putBoolean("screenSchedule",enabled).putString("screenOff",off).putString("screenOn",on).commit();ScreenReceiver.refresh(c);});
            if(lock.isHeld())lock.release();
        }
        finish(-1,result);
    }
}
