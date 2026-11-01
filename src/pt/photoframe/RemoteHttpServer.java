package pt.photoframe;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Small bounded HTTP/1.1 server. Exact routes only; never serves arbitrary files. */
public final class RemoteHttpServer implements Closeable {
    public interface Router {Response handle(Request request)throws Exception;}
    public static final class Request {
        public String method,path,peer;public final Map<String,String> headers=new HashMap<>();public byte[] body;
        public String text()throws IOException{return new String(body,"UTF-8");}
    }
    public static final class Response {
        public int code;public String type;public byte[] bytes;public final Map<String,String> headers=new HashMap<>();
        public Response(int code,String type,byte[] bytes){this.code=code;this.type=type;this.bytes=bytes;}
        public static Response text(int code,String value)throws IOException{return new Response(code,"application/json; charset=utf-8",value.getBytes("UTF-8"));}
    }
    private final ServerSocket listener;
    private final Router router;
    private volatile boolean closed;
    private final Set<Socket> clients=Collections.newSetFromMap(new ConcurrentHashMap<Socket,Boolean>());
    private final ThreadPoolExecutor pool=new ThreadPoolExecutor(4,4,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(12));
    public RemoteHttpServer(int port,Router router)throws IOException{
        this.router=router;listener=new ServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress(port),8);
        Thread accept=new Thread(()->{while(!closed)try{
            final Socket client=listener.accept();client.setSoTimeout(5000);clients.add(client);
            if(!isLocal(client.getInetAddress())){clients.remove(client);client.close();continue;}
            try{pool.execute(()->serve(client));}catch(RejectedExecutionException e){clients.remove(client);client.close();}
        }catch(IOException e){if(!closed)try{Thread.sleep(250);}catch(InterruptedException ignored){}}},"PhotoframeHTTP");accept.setDaemon(true);accept.start();
    }
    public int port(){return listener.getLocalPort();}
    public static boolean isLocal(InetAddress address){return address.isLoopbackAddress()||address.isSiteLocalAddress()||address.isLinkLocalAddress();}
    private void serve(Socket client){
        try(Socket socket=client){
            Response response;
            try{Request request=parse(socket.getInputStream());request.peer=socket.getInetAddress().getHostAddress();response=router.handle(request);}
            catch(Exception e){response=Response.text(400,"{\"error\":\"Pedido inválido\"}");}
            OutputStream out=socket.getOutputStream();
            StringBuilder head=new StringBuilder("HTTP/1.1 "+response.code+" "+reason(response.code)+"\r\nContent-Type: "+response.type+"\r\nContent-Length: "+response.bytes.length+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'\r\n");
            for(Map.Entry<String,String> h:response.headers.entrySet())head.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
            out.write(head.append("\r\n").toString().getBytes("UTF-8"));out.write(response.bytes);out.flush();
        }catch(IOException ignored){}finally{clients.remove(client);}
    }
    static Request parse(InputStream input)throws IOException{
        ByteArrayOutputStream raw=new ByteArrayOutputStream();int state=0,c;
        while(state<4){c=input.read();if(c<0||raw.size()>=16384)throw new IOException();raw.write(c);
            state=(state==0&&c==13)?1:(state==1&&c==10)?2:(state==2&&c==13)?3:(state==3&&c==10)?4:0;
        }
        String[] lines=new String(raw.toByteArray(),"US-ASCII").split("\r\n");String[] first=lines[0].split(" ");
        if(first.length!=3||!first[2].equals("HTTP/1.1")||!first[1].startsWith("/")||first[1].contains("?")||first[1].length()>200)throw new IOException();
        Request r=new Request();r.method=first[0];r.path=first[1];
        for(int i=1;i<lines.length;i++){
            int colon=lines[i].indexOf(':');if(colon<=0)throw new IOException();
            String name=lines[i].substring(0,colon).toLowerCase(Locale.US),value=lines[i].substring(colon+1).trim();
            if(!name.matches("[a-z0-9-]+")||r.headers.put(name,value)!=null)throw new IOException();
        }
        if(!r.headers.containsKey("host")||r.headers.containsKey("transfer-encoding"))throw new IOException();
        int length;
        try{length=Integer.parseInt(r.headers.containsKey("content-length")?r.headers.get("content-length"):"0");}catch(NumberFormatException e){throw new IOException();}
        if(length<0||length>32768)throw new IOException();r.body=new byte[length];
        for(int at=0;at<length;){int n=input.read(r.body,at,length-at);if(n<0)throw new IOException();at+=n;}
        return r;
    }
    private static String reason(int code){switch(code){case 200:return "OK";case 202:return "Accepted";case 400:return "Bad Request";case 401:return "Unauthorized";case 403:return "Forbidden";case 404:return "Not Found";case 409:return "Conflict";case 429:return "Too Many Requests";case 503:return "Unavailable";default:return "Error";}}
    @Override public void close()throws IOException{closed=true;listener.close();for(Socket s:clients)try{s.close();}catch(IOException ignored){}pool.shutdownNow();}
}
