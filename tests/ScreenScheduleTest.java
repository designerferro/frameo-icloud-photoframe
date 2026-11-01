import pt.photoframe.ScreenSchedule;
import java.util.*;
public class ScreenScheduleTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        for(int n=0;n<1440;n++){
            check(ScreenSchedule.asleep(n,1320,480)==(n>=1320||n<480));
            check(ScreenSchedule.asleep(n,600,1020)==(n>=600&&n<1020));
        }
        check(ScreenSchedule.minute("23:59")==1439);
        for(String t:new String[]{"24:00","12:60","1:00","-1:00","22:00;id",""}){try{ScreenSchedule.minute(t);throw new AssertionError(t);}catch(IllegalArgumentException expected){}}
        Calendar c=new GregorianCalendar(TimeZone.getTimeZone("Europe/Lisbon"));c.set(2026,10,1,22,0,0);c.set(Calendar.MILLISECOND,0);
        Calendar target=(Calendar)c.clone();target.add(Calendar.DAY_OF_MONTH,1);target.set(Calendar.HOUR_OF_DAY,8);
        check(ScreenSchedule.next(c,1320,480)==target.getTimeInMillis());
        c.set(Calendar.HOUR_OF_DAY,7);target=(Calendar)c.clone();target.set(Calendar.HOUR_OF_DAY,8);
        check(ScreenSchedule.next(c,1320,480)==target.getTimeInMillis());
        System.out.println("Screen schedule: all daily minutes, boundaries, validation and next alarm passed.");
    }
}
