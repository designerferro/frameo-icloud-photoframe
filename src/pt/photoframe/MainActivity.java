package pt.photoframe;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.content.pm.ActivityInfo;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline-first viewer. Cache is private to this app; no storage permission. */
public class MainActivity extends Activity {
    final Handler ui = new Handler();
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final ExecutorService updaterWorker = Executors.newSingleThreadExecutor();
    final ArrayList<File> slides = new ArrayList<>();
    ImageView photo, backdrop;
    TextView placeholder;
    SharedPreferences preferences;
    AppUpdater updater;
    FrameLayout surface;
    File cache;
    int index = -1;
    boolean busy = false, destroyed = false, resumed = false;
    String token, base, status = "A aguardar a primeira sincronização";
    final Runnable advance = new Runnable() { public void run() { next(); ui.postDelayed(this, 15000); } };
    final Runnable poll = new Runnable() { public void run() { sync(); ui.postDelayed(this, 900000); } };
    final Runnable updatePoll = new Runnable() { public void run() {
        if(preferences.getBoolean("autoUpdate",true))updater.check(false);
        ui.postDelayed(this,21600000);
    }};

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        preferences=getSharedPreferences("photoframe",MODE_PRIVATE);
        setRequestedOrientation(preferences.getInt("orientation",ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR));
        surface=new FrameLayout(this);surface.setBackgroundColor(Color.rgb(25,30,35));
        backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setColorFilter(Color.argb(105,0,0,0));
        photo=new ImageView(this);photo.setScaleType(preferences.getBoolean("crop",false)?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams full=new FrameLayout.LayoutParams(-1,-1);
        surface.addView(backdrop,full);surface.addView(photo,new FrameLayout.LayoutParams(-1,-1));
        placeholder=new TextView(this);placeholder.setTextColor(Color.WHITE);placeholder.setGravity(Gravity.CENTER);
        placeholder.setText("Photoframe\nConfigura um álbum no menu (toque prolongado).");
        surface.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        setContentView(surface);
        updater=new AppUpdater(this,updaterWorker);
        immersive();
        photo.setOnClickListener(v -> { ui.removeCallbacks(advance); next(); ui.postDelayed(advance,15000); });
        photo.setOnLongClickListener(v -> { menu(); return true; });
        placeholder.setOnLongClickListener(v->{menu();return true;});
        placeholder.setOnClickListener(v->configureAlbum());
        cache = new File(getFilesDir(),"photos"); cache.mkdirs();
        try {
            token=preferences.getString("albumToken",null);
            if(token!=null)base=albumBase(token);
            File manifest = new File(getFilesDir(),"slides.json");
            if(manifest.exists()) loadManifest(new JSONArray(new String(read(new FileInputStream(manifest),2000000),"UTF-8")));
        } catch(Exception e) { status="Erro de configuração: "+e.getClass().getSimpleName(); }
        next();
        if(token==null)ui.post(()->configureAlbum());
    }
    void immersive(){ getWindow().getDecorView().setSystemUiVisibility(5894); }
    @Override public void onWindowFocusChanged(boolean focused){ super.onWindowFocusChanged(focused); if(focused) immersive(); }
    @Override public void onResume(){super.onResume();resumed=true;ui.postDelayed(advance,15000);ui.post(poll);if(updater!=null)ui.post(updatePoll);}
    @Override public void onPause(){super.onPause();resumed=false;ui.removeCallbacks(advance);ui.removeCallbacks(poll);ui.removeCallbacks(updatePoll);}
    @Override public void onDestroy(){destroyed=true;ui.removeCallbacksAndMessages(null);worker.shutdownNow();updaterWorker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){menu();}
    void next(){
        if(slides.isEmpty()) return;
        index=(index+1)%slides.size();
        Bitmap b=BitmapFactory.decodeFile(slides.get(index).getAbsolutePath());
        if(b!=null){
            photo.setImageBitmap(b);placeholder.setVisibility(View.GONE);
            // A tiny scaled copy gives a soft background without a costly blur shader.
            Bitmap tiny=Bitmap.createScaledBitmap(b,24,24,true);
            Bitmap soft=Bitmap.createScaledBitmap(tiny,240,240,true);tiny.recycle();
            backdrop.setImageBitmap(soft);
        }
    }
    void menu(){
        LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);options.setPadding(24,8,24,8);
        TextView details=new TextView(this);details.setText(status+"\n\nAPK instalado: "+updater.installedLabel()+"\nÚltima release: "+updater.latestLabel()+"\nAutor: Codex");options.addView(details);
        addButton(options,"Atualizar fotografias agora",()->sync());
        addButton(options,"Procurar / forçar atualização do APK",()->updater.check(true));
        addButton(options,"Configurar álbum iCloud",()->configureAlbum());
        addButton(options,"Apagar álbum e fotografias locais",()->confirmClearAlbum());
        addButton(options,"Apresentação e orientação",()->displaySettings());
        CheckBox auto=new CheckBox(this);auto.setText("Atualizar APK automaticamente (GitHub)");auto.setChecked(preferences.getBoolean("autoUpdate",true));
        auto.setOnCheckedChangeListener((v,checked)->preferences.edit().putBoolean("autoUpdate",checked).apply());options.addView(auto);
        addButton(options,"Definições Android",()->startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)));
        ScrollView scroll=new ScrollView(this);scroll.addView(options);
        new AlertDialog.Builder(this).setTitle("Photoframe — "+slides.size()+" fotografias").setView(scroll).setNegativeButton("Fechar",null).show();
    }
    void addButton(LinearLayout parent,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->action.run());parent.addView(b);}
    void displaySettings(){
        String[] choices={"Foto inteira com fundo suave","Preencher o ecrã (recorta margens)","Orientação automática","Fixar horizontal","Fixar vertical"};
        new AlertDialog.Builder(this).setTitle("Apresentação").setItems(choices,(d,which)->{
            if(which<2){boolean crop=which==1;preferences.edit().putBoolean("crop",crop).apply();photo.setScaleType(crop?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);}
            else{int orientation=which==2?ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR:which==3?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                preferences.edit().putInt("orientation",orientation).apply();setRequestedOrientation(orientation);}
        }).show();
    }
    static String albumBase(String token)throws IOException{
        if(!token.matches("[ABCD][0-9A-Za-z_-]{5,}"))throw new IOException("Ligação de álbum inválida");
        String a="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int p=token.charAt(0)=='A'?a.indexOf(token.charAt(1)):a.indexOf(token.charAt(1))*62+a.indexOf(token.charAt(2));
        if(p<0||p>999)throw new IOException("Partição inválida");
        return String.format(Locale.US,"https://p%02d-sharedstreams.icloud.com/%s/sharedstreams/",p,token);
    }
    void configureAlbum(){
        EditText input=new EditText(this);input.setSingleLine(false);input.setHint("https://www.icloud.com/sharedalbum/#…");
        if(token!=null)input.setText("https://www.icloud.com/sharedalbum/#"+token);
        CheckBox clear=new CheckBox(this);clear.setText("Apagar fotografias anteriores ao mudar de álbum");clear.setChecked(true);
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.addView(input);fields.addView(clear);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Álbum público iCloud").setMessage("A ligação é guardada só nesta moldura. Quem tiver essa ligação pode ver as fotografias.")
            .setView(fields).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            if(busy){Toast.makeText(this,"Aguarda o fim da sincronização atual.",Toast.LENGTH_LONG).show();return;}
            try{
                URI uri=new URI(input.getText().toString().trim());String host=uri.getHost();
                if(!"https".equals(uri.getScheme())||!("www.icloud.com".equals(host)||"icloud.com".equals(host))||!"/sharedalbum/".equals(uri.getPath())||uri.getFragment()==null)throw new IOException();
                String nextToken=uri.getFragment().split(";")[0];String nextBase=albumBase(nextToken);
                if(token!=null&&!token.equals(nextToken)&&clear.isChecked())clearAlbumData();
                token=nextToken;base=nextBase;preferences.edit().putString("albumToken",token).apply();dialog.dismiss();sync();
            }catch(Exception e){input.setError("Usa a ligação Site público de um Álbum partilhado.");}
        }));dialog.show();
    }
    void confirmClearAlbum(){
        new AlertDialog.Builder(this).setTitle("Apagar álbum e fotografias locais?")
            .setMessage("Remove a ligação e todas as fotografias guardadas nesta moldura. Não altera o álbum no iCloud.")
            .setPositiveButton("Apagar",(d,w)->{
                if(busy){Toast.makeText(this,"Aguarda o fim da sincronização atual.",Toast.LENGTH_LONG).show();return;}
                try{clearAlbumData();}catch(IOException e){Toast.makeText(this,"Não foi possível apagar todos os dados. Tenta novamente.",Toast.LENGTH_LONG).show();}
            }).setNegativeButton("Cancelar",null).show();
    }
    void clearAlbumData()throws IOException{
        // UI thread only, with no sync in flight. Discard in-memory images immediately.
        photo.setImageDrawable(null);backdrop.setImageDrawable(null);slides.clear();index=-1;
        token=null;base=null;boolean cleared=preferences.edit().remove("albumToken").commit();
        placeholder.setText("Photoframe\nConfigura um álbum no menu (toque prolongado).");placeholder.setVisibility(View.VISIBLE);
        status="Álbum removido. Configura um álbum para sincronizar.";
        for(String name:new String[]{"slides.json","slides.tmp"}){
            File file=new File(getFilesDir(),name);if(file.exists()&&!file.delete())cleared=false;
        }
        File[] files=cache.listFiles();
        if(files==null)cleared=false;
        else for(File file:files)if(file.isFile()&&!file.delete())cleared=false;
        if(!cleared)throw new IOException("Cannot clear album data");
    }
    void loadManifest(JSONArray files) throws Exception {
        ArrayList<File> ready=new ArrayList<>();
        for(int i=0;i<files.length();i++){
            String name=files.getString(i);
            if(!name.matches("[a-zA-Z0-9-]+\\.jpg")) throw new IOException("Invalid cache name");
            File f=new File(cache,name); if(f.exists()) ready.add(f);
        }
        if(!ready.isEmpty()){slides.clear();slides.addAll(ready);index=-1;}
    }
    synchronized void sync(){
        if(busy||base==null||destroyed)return;
        busy=true;
        worker.execute(()->{
            try {
                JSONObject stream=post(base+"webstream",new JSONObject().put("streamCtag",JSONObject.NULL));
                JSONArray all=stream.getJSONArray("photos");
                ArrayList<JSONObject> items=new ArrayList<>();
                for(int i=0;i<all.length();i++){
                    JSONObject p=all.getJSONObject(i);
                    if(p.has("derivatives")&&!"video".equals(p.optString("mediaAssetType")))items.add(p);
                }
                Collections.sort(items,(a,b)->a.optString("dateCreated").compareTo(b.optString("dateCreated")));
                if(items.isEmpty())throw new IOException("Álbum vazio; mantidas as fotografias locais");
                JSONArray manifest=new JSONArray();
                for(int start=0;start<items.size();start+=25){
                    JSONArray ids=new JSONArray();
                    ArrayList<JSONObject> needed=new ArrayList<>();
                    for(int i=start;i<Math.min(start+25,items.size());i++){
                        JSONObject p=items.get(i),derivatives=p.getJSONObject("derivatives"),best=null;
                        long area=-1;
                        Iterator<String> keys=derivatives.keys();
                        while(keys.hasNext()){
                            JSONObject d=derivatives.getJSONObject(keys.next());
                            long a=d.getLong("width")*d.getLong("height");if(a>area){best=d;area=a;}
                        }
                        if(best==null)continue;
                        String sum=best.getString("checksum");
                        if(!sum.matches("[a-fA-F0-9]+"))throw new IOException("Invalid checksum");
                        String name=sum+".jpg";manifest.put(name);
                        if(!new File(cache,name).exists()){
                            ids.put(p.getString("photoGuid"));needed.add(best);
                        }
                    }
                    if(needed.isEmpty())continue;
                    JSONObject urls=post(base+"webasseturls",new JSONObject().put("photoGuids",ids)).getJSONObject("items");
                    for(JSONObject d:needed){
                        String sum=d.getString("checksum"); JSONObject asset=urls.getJSONObject(sum);
                        String host=asset.getString("url_location");
                        if(!host.endsWith(".icloud-content.com"))throw new IOException("Unexpected asset host");
                        byte[] bytes=get("https://"+host+asset.getString("url_path"));
                        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                        options.inSampleSize=1;
                        while(options.outWidth/options.inSampleSize>2560||options.outHeight/options.inSampleSize>2560)options.inSampleSize*=2;
                        options.inJustDecodeBounds=false;
                        Bitmap b=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                        if(b==null)throw new IOException("Invalid image");
                        File original=new File(cache,"orientation.tmp");write(original,bytes);
                        int orientation=new ExifInterface(original.getAbsolutePath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
                        original.delete();
                        Matrix matrix=new Matrix();
                        switch(orientation){
                            case 2:matrix.setScale(-1,1);break;
                            case 3:matrix.setRotate(180);break;
                            case 4:matrix.setScale(1,-1);break;
                            case 5:matrix.setRotate(90);matrix.postScale(-1,1);break;
                            case 6:matrix.setRotate(90);break;
                            case 7:matrix.setRotate(-90);matrix.postScale(-1,1);break;
                            case 8:matrix.setRotate(-90);break;
                        }
                        if(!matrix.isIdentity()){
                            Bitmap oriented=Bitmap.createBitmap(b,0,0,b.getWidth(),b.getHeight(),matrix,true);
                            if(oriented!=b)b.recycle();b=oriented;
                        }
                        float ratio=Math.min(1f,Math.min(1280f/b.getWidth(),1280f/b.getHeight()));
                        Bitmap resized=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*ratio)),Math.max(1,Math.round(b.getHeight()*ratio)),true);
                        File temp=new File(cache,sum+".tmp");
                        try(FileOutputStream out=new FileOutputStream(temp)){resized.compress(Bitmap.CompressFormat.JPEG,88,out);}
                        if(resized!=b)resized.recycle();b.recycle();
                        if(!temp.renameTo(new File(cache,sum+".jpg")))throw new IOException("Cannot save photo");
                    }
                }
                File temp=new File(getFilesDir(),"slides.tmp");write(temp,manifest.toString().getBytes("UTF-8"));
                if(!temp.renameTo(new File(getFilesDir(),"slides.json")))throw new IOException("Cannot save album");
                ui.post(()->{
                    if(destroyed)return;
                    try{loadManifest(manifest);next();status="Atualizado: "+new java.text.SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date());}
                    catch(Exception e){status="Erro ao abrir o álbum local";}
                });
                // Delete only this app's obsolete cached images after a successful update.
                HashSet<String> keep=new HashSet<>();for(int i=0;i<manifest.length();i++)keep.add(manifest.getString(i));
                for(File f:cache.listFiles())if(!keep.contains(f.getName()))f.delete();
                android.util.Log.i("Photoframe","SYNC_OK photos="+manifest.length());
            } catch(Exception e){
                android.util.Log.w("Photoframe","SYNC_FAILED "+e.getClass().getSimpleName());
                ui.post(()->{
                    status="Sem atualização: "+e.getClass().getSimpleName()+". A mostrar cópia local; tenta novamente em 1 minuto.";
                    ui.removeCallbacks(poll);if(resumed&&!destroyed)ui.postDelayed(poll,60000);
                });
            } finally {ui.post(()->busy=false);}
        });
    }
    JSONObject post(String url,JSONObject body)throws Exception{
        return new JSONObject(new String(request(url,body.toString().getBytes("UTF-8")),"UTF-8"));
    }
    byte[] get(String url)throws Exception{return request(url,null);}
    byte[] request(String url,byte[] payload)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(20000);c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent","Photoframe/1.0");
        try{
            if(payload!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(payload);}}
            int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code);
            return read(c.getInputStream(),20000000);
        }finally{c.disconnect();}
    }
    static byte[] read(InputStream input,int limit)throws IOException{
        try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int n,total=0;
            while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new IOException("Response too large");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    static void write(File file,byte[] bytes)throws IOException{try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}}
}
