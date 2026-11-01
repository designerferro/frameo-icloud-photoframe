package pt.photoframe;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;

public class RootInstallerTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        Path directory=Files.createTempDirectory("photoframe-installer-");
        try{
            Path apk=directory.resolve("verified ' update.apk"),received=directory.resolve("received"),log=directory.resolve("log");
            byte[] bytes={0,1,2,3,10,127};Files.write(apk,bytes);
            Path pm=directory.resolve("pm");
            Files.write(pm,("#!/bin/sh\n"+
                "echo \"$1\" >> \"$INSTALL_LOG\"\n"+
                "case \"$1\" in\n"+
                "install-create) if [ \"$FAIL\" = create ]; then echo Failure; else echo 'Success: created install session [123]'; fi;;\n"+
                "install-write) if [ \"$FAIL\" = write ]; then echo Failure; else cat > \"$RECEIVED\"; echo 'Success: streamed'; fi;;\n"+
                "install-commit) if [ \"$FAIL\" = commit ]; then echo 'Failure [INSTALL_FAILED_INVALID_APK]'; else echo Success; fi;;\n"+
                "install-abandon) echo Success;;\nesac\n").getBytes(StandardCharsets.UTF_8));
            check(pm.toFile().setExecutable(true),"Executable mock");
            for(String failure:new String[]{"none","create","write","commit"}){
                Files.deleteIfExists(log);Files.deleteIfExists(received);
                ProcessBuilder builder=new ProcessBuilder("sh","-c",RootInstaller.command(apk.toString(),bytes.length)).redirectErrorStream(true);
                builder.environment().put("PATH",directory+File.pathSeparator+System.getenv("PATH"));
                builder.environment().put("FAIL",failure);builder.environment().put("INSTALL_LOG",log.toString());builder.environment().put("RECEIVED",received.toString());
                Process process=builder.start();ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int n;
                while((n=process.getInputStream().read(buffer))!=-1)output.write(buffer,0,n);
                int exit=process.waitFor();String result=output.toString("UTF-8").trim(),calls=new String(Files.readAllBytes(log),StandardCharsets.UTF_8);
                if(failure.equals("none")){check(exit==0&&result.equals(RootInstaller.SUCCESS),"Successful session");check(Arrays.equals(bytes,Files.readAllBytes(received)),"Private APK streamed intact");check(!calls.contains("install-abandon"),"Committed session retained");}
                else{check(exit!=0&&!result.contains(RootInstaller.SUCCESS),"Failure never reported as success: "+failure);if(!failure.equals("create"))check(calls.contains("install-abandon"),"Failed session abandoned");}
            }
            try{RootInstaller.command(apk.toString(),0);throw new AssertionError("Empty APK accepted");}catch(IllegalArgumentException expected){}
            System.out.println("Root installer session, quoting, streaming and failure checks passed.");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(directory)){paths.sorted(Comparator.reverseOrder()).forEach(path->{try{Files.delete(path);}catch(IOException e){throw new UncheckedIOException(e);}});}}
    }
}
