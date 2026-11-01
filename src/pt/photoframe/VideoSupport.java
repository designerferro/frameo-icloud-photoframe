package pt.photoframe;

import android.content.Context;
import android.graphics.Point;
import android.media.*;
import android.view.Surface;
import android.view.WindowManager;
import java.io.*;
import java.nio.ByteBuffer;

/** Device-local MP4 preparation. No bundled codecs or remote conversion service. */
final class VideoSupport {
    static final int BITRATE=2000000;
    static int maxSeconds(File cache){
        long free=cache==null?0:cache.getUsableSpace();
        long budget=Math.max(0,free-128L*1024*1024)/4;
        return (int)Math.max(1,Math.min(Integer.MAX_VALUE/1000,budget/((BITRATE+192000)/8)));
    }
    static int even(int n){return Math.max(16,n/2*2);}
    static void prepare(Context context,File source,File destination,int seconds)throws Exception{
        MediaExtractor extractor=new MediaExtractor();
        try{
            extractor.setDataSource(source.getAbsolutePath());
            int video=-1,audio=-1;MediaFormat vf=null,af=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime.startsWith("video/")&&video<0){video=i;vf=f;}
                if(mime.startsWith("audio/")&&audio<0){audio=i;af=f;}
            }
            if(vf==null)throw new IOException("No video track");
            Point display=new Point();((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealSize(display);
            int width=vf.getInteger(MediaFormat.KEY_WIDTH),height=vf.getInteger(MediaFormat.KEY_HEIGHT);
            int rotation=0;
            MediaMetadataRetriever metadata=new MediaMetadataRetriever();
            try{
                metadata.setDataSource(source.getAbsolutePath());
                String value=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if(value!=null)rotation=Integer.parseInt(value);
            }finally{metadata.release();}
            int renderedWidth=rotation%180==0?width:height,renderedHeight=rotation%180==0?height:width;
            float scale=Math.min(1f,Math.min(display.x/(float)renderedWidth,display.y/(float)renderedHeight));
            boolean compatible="video/avc".equals(vf.getString(MediaFormat.KEY_MIME))&&scale>=1f;
            if(af!=null&&!"audio/mp4a-latm".equals(af.getString(MediaFormat.KEY_MIME)))throw new IOException("Unsupported audio codec");
            if(compatible){remux(extractor,destination,video,audio,vf,af,rotation,seconds);return;}
            transcode(source,destination,extractor,audio,af,even((int)(renderedWidth*scale)),even((int)(renderedHeight*scale)),seconds);
        }finally{extractor.release();}
    }
    static void copyTrack(MediaExtractor ex,MediaMuxer mux,int source,int target,long limit)throws IOException{
        ex.selectTrack(source);ex.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        ByteBuffer data=ByteBuffer.allocate(8*1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        while(ex.getSampleTrackIndex()>=0){
            long time=ex.getSampleTime();if(time<0||time>=limit)break;
            data.clear();int size=ex.readSampleData(data,0);if(size<0)break;
            info.set(0,size,time,ex.getSampleFlags());mux.writeSampleData(target,data,info);ex.advance();
        }
        ex.unselectTrack(source);
    }
    static void remux(MediaExtractor ex,File destination,int video,int audio,MediaFormat vf,MediaFormat af,int rotation,int seconds)throws Exception{
        MediaMuxer mux=new MediaMuxer(destination.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        boolean started=false;
        try{
            mux.setOrientationHint(rotation);int v=mux.addTrack(vf),a=audio<0?-1:mux.addTrack(af);
            mux.start();started=true;copyTrack(ex,mux,video,v,seconds*1000000L);
            if(audio>=0)copyTrack(ex,mux,audio,a,seconds*1000000L);
        }finally{try{if(started)mux.stop();}finally{mux.release();}}
    }
    static void transcode(File source,File destination,MediaExtractor ex,int audio,MediaFormat af,int width,int height,int seconds)throws Exception{
        MediaCodec encoder=null;MediaPlayer player=null;Surface input=null;MediaMuxer mux=null;
        boolean codecStarted=false,muxStarted=false;
        try{
            encoder=MediaCodec.createEncoderByType("video/avc");
            MediaCodecInfo.VideoCapabilities caps=encoder.getCodecInfo().getCapabilitiesForType("video/avc").getVideoCapabilities();
            int wa=caps.getWidthAlignment(),ha=caps.getHeightAlignment();
            width=Math.max(wa,width/wa*wa);height=Math.max(ha,height/ha*ha);
            // Some frames have a 1280x800 screen but only encode up to 720 lines.
            int originalWidth=width,originalHeight=height;
            for(float factor=0.95f;!caps.isSizeSupported(width,height)&&factor>=0.1f;factor-=0.05f){
                width=Math.max(wa,(int)(originalWidth*factor)/wa*wa);
                height=Math.max(ha,(int)(originalHeight*factor)/ha*ha);
            }
            if(!caps.isSizeSupported(width,height))throw new IOException("Frame encoder cannot handle video size");
            MediaFormat format=MediaFormat.createVideoFormat("video/avc",width,height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE,BITRATE);format.setInteger(MediaFormat.KEY_FRAME_RATE,30);format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1);
            encoder.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);input=encoder.createInputSurface();encoder.start();codecStarted=true;
            player=new MediaPlayer();player.setDataSource(source.getAbsolutePath());player.setSurface(input);player.setVolume(0,0);player.prepare();
            long duration=Math.min(seconds*1000L,player.getDuration());
            mux=new MediaMuxer(destination.getAbsolutePath(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int audioOut=audio<0?-1:mux.addTrack(af),videoOut=-1;
            player.start();long start=android.os.SystemClock.elapsedRealtime(),first=-1,last=-1;boolean eos=false,finished=false;
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(!finished){
                if(Thread.currentThread().isInterrupted())throw new IOException("Conversion interrupted");
                long elapsed=android.os.SystemClock.elapsedRealtime()-start;
                if(!eos&&elapsed>=duration){player.pause();encoder.signalEndOfInputStream();eos=true;}
                if(elapsed>duration+15000)throw new IOException("Conversion timed out");
                int output=encoder.dequeueOutputBuffer(info,10000);
                if(output==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){videoOut=mux.addTrack(encoder.getOutputFormat());mux.start();muxStarted=true;}
                else if(output>=0){
                    if((info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0&&info.size>0){
                        if(!muxStarted)throw new IOException("Missing output format");
                        if(first<0)first=info.presentationTimeUs;
                        info.presentationTimeUs=Math.max(last+1,info.presentationTimeUs-first);last=info.presentationTimeUs;
                        if(info.presentationTimeUs<seconds*1000000L){ByteBuffer data=encoder.getOutputBuffer(output);data.position(info.offset);data.limit(info.offset+info.size);mux.writeSampleData(videoOut,data,info);}
                    }
                    finished=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;encoder.releaseOutputBuffer(output,false);
                }
            }
            if(last<0)throw new IOException("No encoded video frames");
            if(audio>=0)copyTrack(ex,mux,audio,audioOut,Math.min(seconds*1000000L,last+33333));
        }finally{
            if(player!=null)player.release();
            if(encoder!=null){try{if(codecStarted)encoder.stop();}finally{encoder.release();}}
            if(input!=null)input.release();
            if(mux!=null){try{if(muxStarted)mux.stop();}finally{mux.release();}}
        }
    }
}
