package pt.photoframe;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only shares the verified update APK, using a temporary installer URI grant. */
public class UpdateProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File apk(Uri uri)throws FileNotFoundException{
        if(!"/latest.apk".equals(uri.getPath()))throw new FileNotFoundException();
        return new File(getContext().getFilesDir(),"verified-update.apk");
    }
    @Override public String getType(Uri uri){return "application/vnd.android.package-archive";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException();
        return ParcelFileDescriptor.open(apk(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});
        try{File file=apk(uri);cursor.addRow(new Object[]{"Photoframe.apk",file.length()});}catch(IOException ignored){}
        return cursor;
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
}
