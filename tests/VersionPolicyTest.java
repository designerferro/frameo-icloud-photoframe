import pt.photoframe.VersionPolicy;
public class VersionPolicyTest {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        require(VersionPolicy.eligible(1,2,false),"A newer APK must be offered");
        require(!VersionPolicy.eligible(2,1,true),"Manual force must not allow downgrade");
        require(!VersionPolicy.eligible(2,2,false),"Automatic same-version reinstalls must be blocked");
        require(VersionPolicy.eligible(2,2,true),"Explicit same-version reinstall must be possible");
        require(!VersionPolicy.eligible(0,2,true),"Invalid installed version must fail closed");
        require(!VersionPolicy.eligible(2,-1,true),"Invalid release version must fail closed");
        require(!VersionPolicy.validHash("abc"),"Truncated digest must be rejected");
        require(VersionPolicy.validHash(new String(new char[64]).replace('\0','a')),"SHA256 shape");
        require(!VersionPolicy.validVersion("1.1.0/../../other"),"Version path injection");
        require("Photoframe-v1.1.0.apk".equals(VersionPolicy.apkName("1.1.0")),"Stable asset naming");
        System.out.println("Update policy checks passed.");
    }
}
