import pt.photoframe.RemoteSecurity;
import pt.photoframe.RemoteHttpServer;
import java.net.*;
import java.io.*;

public class RemoteCoreTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String request(int port,String data)throws Exception{
        try(Socket s=new Socket("127.0.0.1",port)){
            s.setSoTimeout(5000);s.getOutputStream().write(data.getBytes("UTF-8"));
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=s.getInputStream().read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");
        }
    }
    public static void main(String[] args)throws Exception{
        String salt=RemoteSecurity.token(),password=RemoteSecurity.token();
        check(salt.matches("[a-f0-9]{64}")&&!salt.equals(RemoteSecurity.token()),"Random salts/tokens");
        String hash=RemoteSecurity.hash(password,salt);
        check(RemoteSecurity.equal(hash,RemoteSecurity.hash(password,salt)),"Password can be verified");
        check(!RemoteSecurity.equal(hash,RemoteSecurity.hash(password+"x",salt)),"Wrong password rejected");
        check(!RemoteSecurity.equal(hash,RemoteSecurity.hash(password,RemoteSecurity.token())),"Salt changes digest");
        check(!RemoteSecurity.validUsername("admin")&&RemoteSecurity.validUsername("owner-1"),"Bootstrap username must change");
        check(!RemoteSecurity.validPassword("admin")&&!RemoteSecurity.validPassword("short"),"Bootstrap and short passwords rejected");
        try(RemoteHttpServer server=new RemoteHttpServer(0,r->RemoteHttpServer.Response.text(200,"{\"ok\":true}"))){
            String prefix="GET / HTTP/1.1\r\nHost: localhost\r\n";
            String response=request(server.port(),prefix+"\r\n");
            check(response.startsWith("HTTP/1.1 200")&&response.contains("Cache-Control: no-store")&&response.contains("frame-ancestors 'none'"),"Normal response and browser protections");
            check(request(server.port(),prefix+"Content-Length: 40000\r\n\r\n").startsWith("HTTP/1.1 400"),"Oversized bodies rejected before read");
            check(request(server.port(),prefix+"Content-Length: 0\r\nContent-Length: 1\r\n\r\n").startsWith("HTTP/1.1 400"),"Ambiguous length rejected");
            check(request(server.port(),prefix+"Transfer-Encoding: chunked\r\n\r\n").startsWith("HTTP/1.1 400"),"Ambiguous transfer encoding rejected");
            check(request(server.port(),"GET /?sensitive=value HTTP/1.1\r\nHost: localhost\r\n\r\n").startsWith("HTTP/1.1 400"),"Query strings refused");
        }
        System.out.println("Remote security and HTTP boundary checks passed.");
    }
}
