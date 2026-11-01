package pt.photoframe;
import java.util.Calendar;
/** Daily local-time interval; equal endpoints are rejected. */
public final class ScreenSchedule {
    public static int minute(String time){
        if(time==null||!time.matches("[0-2][0-9]:[0-5][0-9]"))throw new IllegalArgumentException("Usa horas HH:mm");
        int h=Integer.parseInt(time.substring(0,2));if(h>23)throw new IllegalArgumentException("Hora inválida");
        return h*60+Integer.parseInt(time.substring(3));
    }
    public static boolean asleep(int now,int start,int end){return start<end?now>=start&&now<end:now>=start||now<end;}
    public static long next(Calendar now,int start,int end){
        long result=Long.MAX_VALUE;
        for(int minute:new int[]{start,end}){
            Calendar c=(Calendar)now.clone();c.set(Calendar.HOUR_OF_DAY,minute/60);c.set(Calendar.MINUTE,minute%60);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
            if(c.getTimeInMillis()<=now.getTimeInMillis())c.add(Calendar.DAY_OF_MONTH,1);
            result=Math.min(result,c.getTimeInMillis());
        }return result;
    }
}
