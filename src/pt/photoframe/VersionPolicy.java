package pt.photoframe;
/** Pure Java update rules, shared by the Android client and host-side tests. */
public final class VersionPolicy {
    private VersionPolicy(){}
    public static boolean eligible(int installed,int candidate,boolean reinstall){
        return installed>0&&candidate>0&&(candidate>installed||(reinstall&&candidate==installed));
    }
    public static boolean validHash(String sha){return sha!=null&&sha.matches("[a-fA-F0-9]{64}");}
    public static boolean validVersion(String version){return version!=null&&version.matches("[0-9]+\\.[0-9]+\\.[0-9]+");}
    public static String apkName(String version){if(!validVersion(version))throw new IllegalArgumentException("Invalid version");return "Photoframe-v"+version+".apk";}
}
