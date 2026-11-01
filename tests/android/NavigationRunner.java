package pt.photoframe;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;
import java.util.*;
/** Real touch events over photographs, video and the full-window margins. */
public class NavigationRunner extends Instrumentation {
    MainActivity activity;
    void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    void ui(Runnable action){final Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    void tap(float fraction,long hold)throws Exception{
        final int[] size=new int[2];ui(()->{size[0]=activity.surface.getWidth();size[1]=activity.surface.getHeight();});
        long start=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,size[0]*fraction,size[1]*0.5f,0);sendPointerSync(down);down.recycle();
        Thread.sleep(hold);
        MotionEvent up=MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,size[0]*fraction,size[1]*0.5f,0);sendPointerSync(up);up.recycle();waitForIdleSync();
    }
    int step=0;
    void position(int n){step++;ui(()->check(activity.index==n,"Step "+step+": expected item "+n+", got "+activity.index+"; "+activity.status));}
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();SharedPreferences p=getTargetContext().getSharedPreferences("photoframe",0);
        boolean schedule=p.getBoolean("screenSchedule",false);String order=p.getString("slideOrder","date");
        ArrayList<File> original=new ArrayList<>(),chronological=new ArrayList<>();int[] originalIndex={-1};
        ArrayList<File> temporary=new ArrayList<>();
        try{
            getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            for(int i=0;i<300&&(MainActivity.active()==null||MainActivity.active().busy);i++)Thread.sleep(100);
            activity=MainActivity.active();check(activity!=null,"Activity unavailable");
            for(int i=0;i<2;i++){
                File f=new File(activity.cache,"navigation-test-"+i+".jpg");temporary.add(f);
                Bitmap b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);b.eraseColor(i==0?0xff224466:0xff886644);
                try(OutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.JPEG,90,out);}b.recycle();
            }
            File movie=new File(activity.cache,"navigation-test-movie.mp4");temporary.add(movie);
            try(InputStream in=new FileInputStream("/data/local/tmp/navigation-test.mp4");OutputStream out=new FileOutputStream(movie)){byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1)out.write(data,0,n);}
            ui(()->{
                original.addAll(activity.slides);chronological.addAll(activity.chronological);originalIndex[0]=activity.index;
                activity.ui.removeCallbacks(activity.poll);p.edit().putBoolean("screenSchedule",false).putString("slideOrder","date").commit();
                activity.slides.clear();activity.slides.add(temporary.get(0));activity.slides.add(movie);activity.slides.add(temporary.get(1));activity.index=-1;activity.next();
            });
            position(0);tap(.9f,60);position(1);Thread.sleep(800);
            ui(()->check(activity.video.getVisibility()==View.VISIBLE,"Film not selected"));
            tap(.99f,60);position(2);tap(.1f,60);position(1);Thread.sleep(400);
            tap(.01f,60);position(0);tap(.1f,60);position(2);tap(.5f,60);position(0);
            ui(()->p.edit().putString("slideOrder","random").commit());
            ArrayList<File> sequence=new ArrayList<>(activity.slides);tap(.1f,60);position(2);
            ui(()->check(activity.slides.equals(sequence),"Previous reshuffled current random sequence"));
            tap(.9f,60);position(0); // New random cycle must avoid immediately repeating its last item.
            int before=activity.index;tap(.9f,1000);
            ui(()->check(!activity.hasWindowFocus(),"Long press did not open the menu window"));
            check(activity.index==before,"Long press navigated");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();
            ui(()->check(activity.hasWindowFocus(),"Closing menu did not restore slideshow focus"));
            result.putString("result","PASS");result.putString("details","Left/right photo and movie taps, film margins, centre, wraparound, random previous and long-press menu passed");
        }catch(Throwable e){result.putString("result","FAIL: "+e);}
        finally{
            if(activity!=null)ui(()->{
                activity.stopVideo();activity.slides.clear();activity.slides.addAll(original);activity.chronological.clear();activity.chronological.addAll(chronological);
                p.edit().putBoolean("screenSchedule",schedule).putString("slideOrder",order).commit();activity.index=originalIndex[0];
                if(activity.index>=0&&activity.index<activity.slides.size())activity.showCurrent();activity.ui.post(activity.poll);ScreenReceiver.refresh(activity);
            });
            for(File file:temporary)file.delete();
        }
        finish(-1,result);
    }
}
