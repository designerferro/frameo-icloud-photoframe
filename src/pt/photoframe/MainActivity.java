package pt.photoframe;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.media.AudioManager;
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
    private static java.lang.ref.WeakReference<MainActivity> instance=new java.lang.ref.WeakReference<>(null);
    static MainActivity active(){MainActivity a=instance.get();return a==null||a.destroyed?null:a;}
    final ArrayList<File> chronological=new ArrayList<>();
    final Handler ui = new Handler();
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final ExecutorService updaterWorker = Executors.newSingleThreadExecutor();
    final ArrayList<File> slides = new ArrayList<>();
    ImageView photo, backdrop;
    VideoView video;
    GestureDetector navigationGestures;
    MediaPlayer playing;
    final Runnable videoLimit = () -> next();
    TextView placeholder;
    SharedPreferences preferences;
    AppUpdater updater;
    FrameLayout surface;
    File cache;
    int index = -1;
    boolean busy = false, destroyed = false, resumed = false;
    String token, base, status = "A aguardar a primeira sincronização";
    final Runnable advance = new Runnable() { public void run() { next(); } };
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
        video=new VideoView(this);
        FrameLayout.LayoutParams vp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);
        surface.addView(video,vp);video.setVisibility(View.GONE);
        video.setOnPreparedListener(mp->{
            playing=mp;applyVolume();
            if(resumed){video.start();ui.removeCallbacks(videoLimit);ui.postDelayed(videoLimit,Math.min((long)mp.getDuration(),videoSeconds()*1000L));}
        });
        video.setOnCompletionListener(mp->next());
        video.setOnErrorListener((mp,what,extra)->{stopVideo();status="Vídeo incompatível; a avançar.";if(resumed)ui.postDelayed(advance,1000);return true;});
        navigationGestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapUp(MotionEvent e){
                if(e.getX()<surface.getWidth()/2f)previous();else next();return true;
            }
            @Override public void onLongPress(MotionEvent e){menu();}
        });
        navigationGestures.setOnDoubleTapListener(null);
        placeholder=new TextView(this);placeholder.setTextColor(Color.WHITE);placeholder.setGravity(Gravity.CENTER);
        placeholder.setText("Photoframe\nConfigura um álbum no menu (toque prolongado).");
        surface.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        setContentView(surface);
        updater=new AppUpdater(this,updaterWorker);
        immersive();
        placeholder.setOnLongClickListener(v->{menu();return true;});
        placeholder.setOnClickListener(v->configureAlbum());
        cache = new File(getFilesDir(),"photos"); cache.mkdirs();
        try {
            token=preferences.getString("albumToken",null);
            if(token!=null)base=albumBase(token);
            File manifest = new File(getFilesDir(),"slides.json");
            if(manifest.exists()) loadManifest(new JSONArray(new String(read(new FileInputStream(manifest),2000000),"UTF-8")));
        } catch(Exception e) { status="Erro de configuração: "+e.getClass().getSimpleName(); }
        instance=new java.lang.ref.WeakReference<>(this);
        if(preferences.getBoolean("remoteEnabled",true))startService(new Intent(this,RemoteService.class));
        ScreenReceiver.refresh(this);
        next();
        if(token==null)ui.post(()->configureAlbum());
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        // Use the whole activity width, including the margins around a fitted film.
        // Dialogs have their own window and retain their usual touch handling.
        if(navigationGestures!=null&&!slides.isEmpty()&&placeholder.getVisibility()==View.GONE)
            return navigationGestures.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }
    void immersive(){ getWindow().getDecorView().setSystemUiVisibility(5894); }
    @Override public void onWindowFocusChanged(boolean focused){ super.onWindowFocusChanged(focused); if(focused) immersive(); }
    @Override public void onResume(){super.onResume();resumed=true;if(ScreenReceiver.sleeping(this)){resumed=false;return;}if(index>=0&&index<slides.size()&&slides.get(index).getName().endsWith(".mp4")){index--;next();}else schedulePhoto();ui.post(poll);if(updater!=null)ui.post(updatePoll);}
    @Override public void onPause(){super.onPause();resumed=false;stopVideo();ui.removeCallbacks(advance);ui.removeCallbacks(poll);ui.removeCallbacks(updatePoll);}
    @Override public void onDestroy(){destroyed=true;if(instance.get()==this)instance.clear();stopVideo();ui.removeCallbacksAndMessages(null);worker.shutdownNow();updaterWorker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){menu();}
    void schedulePhoto(){ui.removeCallbacks(advance);if(resumed&&!ScreenReceiver.sleeping(this))ui.postDelayed(advance,15000);}
    void stopVideo(){ui.removeCallbacks(videoLimit);playing=null;if(video!=null){video.stopPlayback();video.setVisibility(View.GONE);}}
    int videoSeconds(){return Math.max(1,Math.min(preferences.getInt("videoSeconds",30),VideoSupport.maxSeconds(cache)));}
    void applyVolume(){
        if(playing==null)return;
        int level=Math.max(0,Math.min(10,preferences.getInt("videoVolume",0)));
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(level>0)audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.round(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)*level/10f),0);
        playing.setVolume(level==0?0f:1f,level==0?0f:1f);
    }
    void videoSettings(){
        int maximum=VideoSupport.maxSeconds(cache);
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(24,8,24,8);
        TextView info=new TextView(this);info.setText("Duração máxima em segundos (1–"+maximum+"). Por omissão: 30. O teto depende do espaço livre; reserva 128 MB e usa até 1/4 do restante para um filme. Filmes mais longos param neste limite.");fields.addView(info);
        EditText seconds=new EditText(this);seconds.setInputType(2);seconds.setText(String.valueOf(videoSeconds()));fields.addView(seconds);
        TextView label=new TextView(this);fields.addView(label);
        SeekBar volume=new SeekBar(this);volume.setMax(10);volume.setProgress(preferences.getInt("videoVolume",0));fields.addView(volume);
        label.setText("Volume: "+volume.getProgress()+" / 10 (0 = sem som)");
        volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){} public void onProgressChanged(SeekBar b,int n,boolean user){label.setText("Volume: "+n+" / 10 (0 = sem som)");}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Filmes e som").setView(fields).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
            try{int n=Integer.parseInt(seconds.getText().toString());if(n<1||n>maximum)throw new NumberFormatException();
                preferences.edit().putInt("videoSeconds",n).putInt("videoVolume",volume.getProgress()).apply();applyVolume();
                if(playing!=null){ui.removeCallbacks(videoLimit);ui.postDelayed(videoLimit,Math.max(0,n*1000L-video.getCurrentPosition()));}
                dialog.dismiss();sync();
            }catch(NumberFormatException e){seconds.setError("Escolhe entre 1 e "+maximum+" segundos.");}
        }));dialog.show();
    }
    void next(){
        ui.removeCallbacks(advance);stopVideo();
        if(slides.isEmpty()||ScreenReceiver.sleeping(this)) return;
        if(index==slides.size()-1&&preferences.getString("slideOrder","date").equals("random")){
            File previous=slides.get(index);Collections.shuffle(slides);
            if(slides.size()>1&&slides.get(0).equals(previous))Collections.swap(slides,0,1);
        }
        index=(index+1)%slides.size();
        showCurrent();
    }
    void previous(){
        ui.removeCallbacks(advance);stopVideo();
        if(slides.isEmpty()||ScreenReceiver.sleeping(this))return;
        index=index<0?slides.size()-1:(index+slides.size()-1)%slides.size();
        showCurrent();
    }
    void showCurrent(){
        File slide=slides.get(index);
        if(slide.getName().endsWith(".mp4")){
            if(!resumed)return;
            photo.setImageDrawable(null);backdrop.setImageDrawable(null);placeholder.setVisibility(View.GONE);
            video.setVisibility(View.VISIBLE);video.setVideoPath(slide.getAbsolutePath());return;
        }
        schedulePhoto();
        Bitmap b=BitmapFactory.decodeFile(slide.getAbsolutePath());
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
        addButton(options,"Apagar álbum e fotografias/filmes locais",()->confirmClearAlbum());
        addButton(options,"Apresentação e orientação",()->displaySettings());
        addButton(options,"Filmes: duração e volume",()->videoSettings());
        addButton(options,"Ordem de apresentação",()->orderSettings());
        addButton(options,"Horário do ecrã",()->screenSettings());
        addButton(options,"Acesso remoto",()->remoteSettingsMenu());
        CheckBox auto=new CheckBox(this);auto.setText("Atualizar APK automaticamente (GitHub)");auto.setChecked(preferences.getBoolean("autoUpdate",true));
        auto.setOnCheckedChangeListener((v,checked)->preferences.edit().putBoolean("autoUpdate",checked).apply());options.addView(auto);
        addButton(options,"Definições Android",()->startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)));
        ScrollView scroll=new ScrollView(this);scroll.addView(options);
        new AlertDialog.Builder(this).setTitle("Photoframe — "+slides.size()+" fotografias e filmes").setView(scroll).setNegativeButton("Fechar",null).show();
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
    void screenSettings(){
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(24,8,24,8);
        CheckBox enabled=new CheckBox(this);enabled.setText("Desligar ecrã diariamente (requer root)");enabled.setChecked(preferences.getBoolean("screenSchedule",false));fields.addView(enabled);
        TextView info=new TextView(this);info.setText("Hora local da moldura, formato HH:mm. Pode atravessar a meia-noite.\n"+preferences.getString("screenStatus",""));fields.addView(info);
        TextView offLabel=new TextView(this);offLabel.setText("Desligar às");fields.addView(offLabel);
        EditText off=new EditText(this);off.setSingleLine();off.setText(preferences.getString("screenOff","22:00"));fields.addView(off);
        TextView onLabel=new TextView(this);onLabel.setText("Ligar às");fields.addView(onLabel);
        EditText on=new EditText(this);on.setSingleLine();on.setText(preferences.getString("screenOn","08:00"));fields.addView(on);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Horário do ecrã").setView(fields).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(w->{try{saveScreen(enabled.isChecked(),off.getText().toString(),on.getText().toString());d.dismiss();}catch(IllegalArgumentException e){off.setError(e.getMessage());}}));d.show();
    }
    void saveScreen(boolean enabled,String off,String on){
        if(ScreenSchedule.minute(off)==ScreenSchedule.minute(on))throw new IllegalArgumentException("Escolhe horas diferentes para desligar e ligar");
        preferences.edit().putBoolean("screenSchedule",enabled).putString("screenOff",off).putString("screenOn",on).commit();ScreenReceiver.refresh(this);
        if(!ScreenReceiver.sleeping(this)&&hasWindowFocus()){resumed=true;next();}
    }
    void orderSettings(){
        String[] choices={"Aleatório","Por data da fotografia"};
        new AlertDialog.Builder(this).setTitle("Ordem de apresentação").setSingleChoiceItems(choices,preferences.getString("slideOrder","date").equals("random")?0:1,(dialog,which)->{
            preferences.edit().putString("slideOrder",which==0?"random":"date").apply();reorder();next();dialog.dismiss();
        }).setNegativeButton("Cancelar",null).show();
    }
    void reorder(){slides.clear();slides.addAll(chronological);if(preferences.getString("slideOrder","date").equals("random"))Collections.shuffle(slides);index=-1;}
    void remoteSettingsMenu(){
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(24,8,24,8);
        TextView address=new TextView(this);address.setText(RemoteService.addresses()+"\n\n"+RemoteService.serverState+"\nNo primeiro acesso: admin / admin. É obrigatório alterar o utilizador e a palavra-passe antes de configurar. Usar apenas na rede interna.");fields.addView(address);
        CheckBox enabled=new CheckBox(this);enabled.setText("Permitir acesso remoto na rede interna");enabled.setChecked(preferences.getBoolean("remoteEnabled",true));
        enabled.setOnCheckedChangeListener((button,value)->{preferences.edit().putBoolean("remoteEnabled",value).apply();if(value)startService(new Intent(this,RemoteService.class));else{stopService(new Intent(this,RemoteService.class));RemoteService.serverState="Desativado";}});fields.addView(enabled);
        addButton(fields,"Repor credenciais remotas",()->new AlertDialog.Builder(this).setTitle("Repor acesso remoto?").setMessage("Volta a admin / admin e termina as sessões abertas. O primeiro acesso obriga a escolher novas credenciais. Não altera o álbum.").setPositiveButton("Repor",(d,w)->{RemoteService.reset(this);Toast.makeText(this,"Credenciais repostas",Toast.LENGTH_LONG).show();}).setNegativeButton("Cancelar",null).show());
        new AlertDialog.Builder(this).setTitle("Acesso remoto").setView(fields).setNegativeButton("Fechar",null).show();
    }
    JSONObject remoteState()throws Exception{
        return new JSONObject().put("screenSchedule",preferences.getBoolean("screenSchedule",false)).put("screenOff",preferences.getString("screenOff","22:00")).put("screenOn",preferences.getString("screenOn","08:00")).put("screenStatus",preferences.getString("screenStatus","")).put("albumConfigured",token!=null).put("crop",preferences.getBoolean("crop",false))
            .put("orientation",preferences.getInt("orientation",ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR))
            .put("order",preferences.getString("slideOrder","date")).put("videoSeconds",videoSeconds())
            .put("maxVideoSeconds",VideoSupport.maxSeconds(cache)).put("volume",preferences.getInt("videoVolume",0))
            .put("remoteEnabled",preferences.getBoolean("remoteEnabled",true)).put("updateStatus",preferences.getString("updateStatus",""))
            .put("autoUpdate",preferences.getBoolean("autoUpdate",true)).put("count",slides.size()).put("busy",busy)
            .put("status",status).put("installed",updater.installedLabel()).put("latest",updater.latestLabel());
    }
    JSONObject remoteSettings(JSONObject changes)throws Exception{
        int orientation=changes.getInt("orientation"),duration=changes.getInt("videoSeconds"),volume=changes.getInt("volume");
        String order=changes.getString("order");boolean crop=changes.getBoolean("crop"),automatic=changes.getBoolean("autoUpdate"),remote=changes.optBoolean("remoteEnabled",true);
        if(orientation!=ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR&&orientation!=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE&&orientation!=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)throw new IllegalArgumentException("Orientação inválida");
        if(!order.equals("date")&&!order.equals("random"))throw new IllegalArgumentException("Ordem inválida");
        if(duration<1||duration>VideoSupport.maxSeconds(cache)||volume<0||volume>10)throw new IllegalArgumentException("Duração ou volume fora dos limites");
        boolean screenEnabled=changes.optBoolean("screenSchedule",preferences.getBoolean("screenSchedule",false));
        String screenOff=changes.optString("screenOff",preferences.getString("screenOff","22:00")),screenOn=changes.optString("screenOn",preferences.getString("screenOn","08:00"));
        if(ScreenSchedule.minute(screenOff)==ScreenSchedule.minute(screenOn))throw new IllegalArgumentException("Escolhe horas diferentes para desligar e ligar");
        boolean orderChanged=!order.equals(preferences.getString("slideOrder","date"));
        boolean durationChanged=duration!=preferences.getInt("videoSeconds",30);
        preferences.edit().putInt("orientation",orientation).putInt("videoSeconds",duration).putInt("videoVolume",volume).putString("slideOrder",order).putBoolean("crop",crop).putBoolean("autoUpdate",automatic).putBoolean("remoteEnabled",remote).commit();
        photo.setScaleType(crop?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.FIT_CENTER);applyVolume();
        if(playing!=null){ui.removeCallbacks(videoLimit);ui.postDelayed(videoLimit,Math.max(0,duration*1000L-video.getCurrentPosition()));}
        if(orderChanged){reorder();next();}
        if(durationChanged)sync();
        saveScreen(screenEnabled,screenOff,screenOn);
        JSONObject state=remoteState();setRequestedOrientation(orientation);if(!remote)ui.postDelayed(()->{stopService(new Intent(this,RemoteService.class));RemoteService.serverState="Desativado";},1500);return state;
    }
    JSONObject remoteAction(JSONObject command)throws Exception{
        String action=command.getString("action");
        switch(action){
            case "album":{
                if(busy)throw new IllegalArgumentException("Aguarda o fim da sincronização atual.");
                String link=command.optString("url").trim();if(link.isEmpty())return remoteState();
                String nextToken=parseAlbum(link),nextBase=albumBase(nextToken);
                if(token!=null&&!token.equals(nextToken)&&command.optBoolean("clear",true))clearAlbumData();
                token=nextToken;base=nextBase;preferences.edit().putString("albumToken",token).commit();sync();break;
            }
            case "clear":if(busy)throw new IllegalArgumentException("Aguarda o fim da sincronização atual.");if(!command.optBoolean("confirm"))throw new IllegalArgumentException("Confirma a remoção do álbum e ficheiros locais");clearAlbumData();break;
            case "sync":if(busy)throw new IllegalArgumentException("Já está a sincronizar");if(token==null)throw new IllegalArgumentException("Configura primeiro um álbum");sync();break;
            case "update":updater.checkRemote(command.optBoolean("reinstall"));break;
            case "android":startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));break;
            case "next":next();break;
            default:throw new IllegalArgumentException("Operação desconhecida");
        }
        return remoteState();
    }
    static String parseAlbum(String link)throws Exception{
        URI uri=new URI(link);String host=uri.getHost();
        if(!"https".equals(uri.getScheme())||!("www.icloud.com".equals(host)||"icloud.com".equals(host))||!"/sharedalbum/".equals(uri.getPath())||uri.getFragment()==null||uri.getUserInfo()!=null||uri.getPort()!=-1)throw new IllegalArgumentException("Usa a ligação Site público de um Álbum partilhado");
        String nextToken=uri.getFragment().split(";")[0];albumBase(nextToken);return nextToken;
    }
    static String albumBase(String token)throws IOException{
        if(!token.matches("[ABCD][0-9A-Za-z_-]{5,}"))throw new IOException("Ligação de álbum inválida");
        String a="0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        int p=token.charAt(0)=='A'?a.indexOf(token.charAt(1)):a.indexOf(token.charAt(1))*62+a.indexOf(token.charAt(2));
        if(p<0||p>999)throw new IOException("Partição inválida");
        return String.format(Locale.US,"https://p%02d-sharedstreams.icloud.com/%s/sharedstreams/",p,token);
    }
    void configureAlbum(){
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        input.setHint(token==null?"Introduzir ligação do álbum":"Nova ligação (vazio mantém o álbum)");
        // Never populate the editor with the saved URL or token, even masked.
        CheckBox clear=new CheckBox(this);clear.setText("Apagar fotografias e filmes anteriores ao mudar de álbum");clear.setChecked(true);
        LinearLayout fields=new LinearLayout(this);fields.setOrientation(LinearLayout.VERTICAL);fields.addView(input);fields.addView(clear);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Álbum público iCloud").setMessage((token==null?"Nenhum álbum configurado.":"Álbum configurado. A ligação guardada não é apresentada.")+" A nova ligação fica guardada só nesta moldura. Quem a tiver pode ver as fotografias.")
            .setView(fields).setPositiveButton("Guardar",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            if(input.getText().toString().trim().isEmpty()&&token!=null){dialog.dismiss();return;}
            if(busy){Toast.makeText(this,"Aguarda o fim da sincronização atual.",Toast.LENGTH_LONG).show();return;}
            try{
                String nextToken=parseAlbum(input.getText().toString().trim());String nextBase=albumBase(nextToken);
                if(token!=null&&!token.equals(nextToken)&&clear.isChecked())clearAlbumData();
                token=nextToken;base=nextBase;preferences.edit().putString("albumToken",token).apply();dialog.dismiss();sync();
            }catch(Exception e){input.setError("Usa a ligação Site público de um Álbum partilhado.");}
        }));dialog.show();
    }
    void confirmClearAlbum(){
        new AlertDialog.Builder(this).setTitle("Apagar álbum e fotografias/filmes locais?")
            .setMessage("Remove a ligação e todas as fotografias e filmes guardados nesta moldura. Não altera o álbum no iCloud.")
            .setPositiveButton("Apagar",(d,w)->{
                if(busy){Toast.makeText(this,"Aguarda o fim da sincronização atual.",Toast.LENGTH_LONG).show();return;}
                try{clearAlbumData();}catch(IOException e){Toast.makeText(this,"Não foi possível apagar todos os dados. Tenta novamente.",Toast.LENGTH_LONG).show();}
            }).setNegativeButton("Cancelar",null).show();
    }
    void clearAlbumData()throws IOException{
        // UI thread only, with no sync in flight. Discard in-memory images immediately.
        stopVideo();photo.setImageDrawable(null);backdrop.setImageDrawable(null);slides.clear();chronological.clear();index=-1;
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
        if(files.length()>0&&files.get(0) instanceof JSONObject){
            ArrayList<JSONObject> sorted=new ArrayList<>();for(int i=0;i<files.length();i++)sorted.add(files.getJSONObject(i));
            Collections.sort(sorted,(a,b)->a.optString("date").compareTo(b.optString("date")));files=new JSONArray();for(JSONObject item:sorted)files.put(item);
        }
        for(int i=0;i<files.length();i++){
            String name=files.get(i) instanceof JSONObject?files.getJSONObject(i).getString("name"):files.getString(i);
            if(!name.matches("[a-zA-Z0-9-]+\\.(jpg|mp4)")) throw new IOException("Invalid cache name");
            File f=new File(cache,name); if(f.exists()) ready.add(f);
        }
        if(!ready.isEmpty()){
            File current=index>=0&&index<slides.size()?slides.get(index):null;
            boolean same=slides.size()==ready.size()&&new HashSet<File>(slides).equals(new HashSet<File>(ready));
            chronological.clear();chronological.addAll(ready);
            boolean random=preferences.getString("slideOrder","date").equals("random");
            if(!(same&&random)){slides.clear();slides.addAll(ready);if(random){Collections.shuffle(slides);index=-1;}else index=current==null?-1:slides.indexOf(current);}
        }
    }
    synchronized void sync(){
        if(busy||base==null||destroyed)return;
        busy=true;status="A consultar o álbum iCloud…";
        worker.execute(()->{
            try {
                JSONObject stream=post(base+"webstream",new JSONObject().put("streamCtag",JSONObject.NULL));
                JSONArray all=stream.getJSONArray("photos");
                ArrayList<JSONObject> items=new ArrayList<>();
                for(int i=0;i<all.length();i++){
                    JSONObject p=all.getJSONObject(i);
                    if(p.has("derivatives"))items.add(p);
                }
                Collections.sort(items,(a,b)->a.optString("dateCreated").compareTo(b.optString("dateCreated")));
                if(items.isEmpty())throw new IOException("Álbum vazio; mantidas as fotografias locais");
                JSONArray manifest=new JSONArray();
                final int syncSeconds=videoSeconds();
                int skipped=0;
                for(int start=0;start<items.size();start+=25){
                    JSONArray ids=new JSONArray();
                    ArrayList<JSONObject> needed=new ArrayList<>();
                    for(int i=start;i<Math.min(start+25,items.size());i++){
                        JSONObject p=items.get(i),derivatives=p.getJSONObject("derivatives"),best=null;
                        boolean movie="video".equals(p.optString("mediaAssetType"));
                        long area=-1;
                        Iterator<String> keys=derivatives.keys();
                        while(keys.hasNext()){
                            String derivativeKey=keys.next();
                            if(movie&&"PosterFrame".equals(derivativeKey))continue;
                            JSONObject d=derivatives.getJSONObject(derivativeKey);
                            long a=d.getLong("width")*d.getLong("height");if(a>area){best=d;area=a;}
                        }
                        if(best==null)continue;
                        String sum=best.getString("checksum");
                        if(!sum.matches("[a-fA-F0-9]+"))throw new IOException("Invalid checksum");
                        String name=sum+(movie?"-v2-"+syncSeconds+".mp4":".jpg");manifest.put(new JSONObject().put("name",name).put("date",p.optString("dateCreated")));
                        best.put("localName",name).put("movie",movie);
                        if(!new File(cache,name).exists()){
                            ids.put(p.getString("photoGuid"));needed.add(best);
                        }
                    }
                    if(needed.isEmpty())continue;
                    JSONObject urls=post(base+"webasseturls",new JSONObject().put("photoGuids",ids)).getJSONObject("items");
                    for(JSONObject d:needed){
                        syncProgress("A descarregar fotografias e filmes…");
                        String sum=d.getString("checksum"); JSONObject asset=urls.getJSONObject(sum);
                        String host=asset.getString("url_location");
                        if(!host.endsWith(".icloud-content.com"))throw new IOException("Unexpected asset host");
                        String assetUrl="https://"+host+asset.getString("url_path");
                        if(d.optBoolean("movie")){
                            File source=new File(cache,sum+"-source.tmp"),temp=new File(cache,sum+"-video.tmp");
                            try{
                                downloadMovie(assetUrl,source);
                                syncProgress("A preparar um filme (até "+syncSeconds+" segundos)…");
                                VideoSupport.prepare(MainActivity.this,source,temp,syncSeconds);
                                if(!temp.renameTo(new File(cache,d.getString("localName"))))throw new IOException("Cannot save video");
                            }catch(Exception e){
                                skipped++;
                                for(int n=manifest.length()-1;n>=0;n--)if(d.getString("localName").equals(manifest.getJSONObject(n).getString("name")))manifest.remove(n);
                                android.util.Log.w("Photoframe","VIDEO_SKIPPED "+e.getClass().getSimpleName());
                            }finally{source.delete();temp.delete();}
                            continue;
                        }
                        byte[] bytes=get(assetUrl);
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
                if(manifest.length()==0){ui.post(()->status="Nenhum filme compatível; mantida a cópia local. Verifica os formatos e o espaço livre.");return;}
                final int skippedVideos=skipped;
                File temp=new File(getFilesDir(),"slides.tmp");write(temp,manifest.toString().getBytes("UTF-8"));
                if(!temp.renameTo(new File(getFilesDir(),"slides.json")))throw new IOException("Cannot save album");
                ui.post(()->{
                    if(destroyed)return;
                    try{loadManifest(manifest);next();status="Atualizado: "+new java.text.SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date())+(skippedVideos>0?" · "+skippedVideos+" filme(s) incompatível(is) ignorado(s)":"");}
                    catch(Exception e){status="Erro ao abrir o álbum local";}
                });
                // Delete only this app's obsolete cached images after a successful update.
                HashSet<String> keep=new HashSet<>();for(int i=0;i<manifest.length();i++)keep.add(manifest.getJSONObject(i).getString("name"));
                for(File f:cache.listFiles())if(!keep.contains(f.getName()))f.delete();
                android.util.Log.i("Photoframe","SYNC_OK photos="+manifest.length());
            } catch(Exception e){
                android.util.Log.w("Photoframe","SYNC_FAILED "+e.getClass().getSimpleName());
                ui.post(()->{
                    status=(e instanceof UnknownHostException?"Falha temporária de DNS. Não foi possível localizar o servidor iCloud.":"Sem atualização: "+e.getClass().getSimpleName()+".")+" A mostrar cópia local; tenta novamente em 1 minuto.";
                    ui.removeCallbacks(poll);if(resumed&&!destroyed)ui.postDelayed(poll,60000);
                });
            } finally {ui.post(()->{busy=false;if(!destroyed)preferences.edit().putString("lastSyncState",status).putLong("lastSyncFinished",System.currentTimeMillis()).apply();});}
        });
    }
    void syncProgress(String message){ui.post(()->{if(!destroyed){status=message;preferences.edit().putString("lastSyncState",message).apply();}});}
    void downloadMovie(String url,File destination)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(30000);
        long budget=Math.min(1024L*1024*1024,Math.max(0,(cache.getUsableSpace()-128L*1024*1024)/2));
        try{
            if(c.getResponseCode()!=200)throw new IOException("Video HTTP");
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(destination)){
                byte[] buffer=new byte[65536];long total=0;int n;
                while((n=in.read(buffer))!=-1){total+=n;if(total>budget||Thread.currentThread().isInterrupted())throw new IOException("Video storage limit");out.write(buffer,0,n);}
            }
        }finally{c.disconnect();}
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
