package pt.photoframe;

import java.security.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Platform-independent password/token primitives; no credentials are logged. */
public final class RemoteSecurity {
    private static final SecureRandom RANDOM=new SecureRandom();
    public static String token(){byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return hex(bytes);}
    public static String hash(String password,String salt)throws Exception{
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt.getBytes("UTF-8"),120000,256);
        try{return hex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded());}
        finally{spec.clearPassword();}
    }
    public static boolean equal(String a,String b){
        if(a==null||b==null)return false;
        try{return MessageDigest.isEqual(a.getBytes("UTF-8"),b.getBytes("UTF-8"));}catch(Exception e){return false;}
    }
    public static boolean validUsername(String value){return value!=null&&value.matches("[A-Za-z0-9_.-]{3,48}")&&!value.equalsIgnoreCase("admin");}
    public static boolean validPassword(String value){return value!=null&&value.length()>=8&&value.length()<=128&&!value.equalsIgnoreCase("admin");}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.US,"%02x",b&255));return s.toString();}
}
