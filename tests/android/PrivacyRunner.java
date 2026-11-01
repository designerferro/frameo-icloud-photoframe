package pt.photoframe;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;

/** Exercises the actual dialog without logging or exporting the stored album. */
public class PrivacyRunner extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    public void onStart(){
        Bundle result=new Bundle();
        try{
            getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(2500);
            final MainActivity[] target=new MainActivity[1];
            final View[] root=new View[1];final EditText[] editor=new EditText[1];
            final String[] original=new String[2];final int[] count=new int[1];
            runOnMainSync(()->{
                try{
                    Class<?> type=Class.forName("android.app.ActivityThread");
                    Object thread=type.getMethod("currentActivityThread").invoke(null);
                    java.lang.reflect.Field records=type.getDeclaredField("mActivities");records.setAccessible(true);
                    MainActivity activity=null;
                    for(Object record:((java.util.Map<?,?>)records.get(thread)).values()){
                        java.lang.reflect.Field field=record.getClass().getDeclaredField("activity");field.setAccessible(true);
                        Object candidate=field.get(record);if(candidate instanceof MainActivity)activity=(MainActivity)candidate;
                    }
                    if(activity==null||activity.token==null)throw new AssertionError("Configure a test album first");
                    target[0]=activity;original[0]=activity.token;original[1]=activity.preferences.getString("albumToken",null);
                    count[0]=activity.slides.size();activity.configureAlbum();
                    Class<?> wm=Class.forName("android.view.WindowManagerGlobal");
                    Object manager=wm.getMethod("getInstance").invoke(null);
                    java.lang.reflect.Field views=wm.getDeclaredField("mViews");views.setAccessible(true);
                    java.util.List<?> roots=(java.util.List<?>)views.get(manager);
                    View dialog=(View)roots.get(roots.size()-1);EditText input=findInput(dialog);
                    if(input==null||input.length()!=0)throw new AssertionError("Saved link populated the editor");
                    if(!(input.getTransformationMethod() instanceof PasswordTransformationMethod))throw new AssertionError("New input is not masked");
                    checkNoToken(dialog,original[0]);root[0]=dialog;editor[0]=input;
                }catch(Exception e){throw new RuntimeException(e);}
            });
            // AlertDialog installs its button callbacks in the posted onShow event.
            waitForIdleSync();
            runOnMainSync(()->{
                    MainActivity activity=target[0];EditText input=editor[0];View dialog=root[0];
                    String saved=original[0],preference=original[1];
                    input.setText("invalid-link");dialog.findViewById(android.R.id.button1).performClick();
                    if(!saved.equals(activity.token))throw new AssertionError("Invalid input changed album");
                    if(!activity.busy&&input.getError()==null)throw new AssertionError("Invalid input was not rejected");
                    input.setText("");dialog.findViewById(android.R.id.button1).performClick();
                    if(!saved.equals(activity.token)||!preference.equals(activity.preferences.getString("albumToken",null))||count[0]!=activity.slides.size())throw new AssertionError("Empty save changed album or slides");
            });
            result.putString("result","PASS");result.putString("privacy","Empty masked editor, no stored token in dialog, invalid and empty saves preserve album");finish(-1,result);
        }catch(Throwable e){result.putString("error",e.getClass().getSimpleName());finish(0,result);}
    }
    static EditText findInput(View view){
        if(view instanceof EditText)return (EditText)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){EditText found=findInput(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    static void checkNoToken(View view,String token){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(token))throw new AssertionError("Stored token exposed in dialog");
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)checkNoToken(((ViewGroup)view).getChildAt(i),token);
    }
}
