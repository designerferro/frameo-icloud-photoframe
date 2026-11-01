package pt.photoframe;
import android.content.*;
/** Relaunch the viewer after Package Manager replaces this package. */
public class ReplacedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            context.getSharedPreferences("photoframe",Context.MODE_PRIVATE).edit().putString("updateStatus","Atualização instalada com sucesso.").commit();
            context.startActivity(new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }
}
