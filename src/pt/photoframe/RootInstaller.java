package pt.photoframe;

/** Stream a verified private APK into a Package Installer session (Android 6+). */
final class RootInstaller {
    static final String SUCCESS="PHOTOFRAME_INSTALL_OK";
    static String command(String source,long size){
        if(source==null||!source.startsWith("/")||source.indexOf('\n')>=0||size<=0)throw new IllegalArgumentException("APK inválido");
        String quoted="'"+source.replace("'","'\"'\"'")+"'";
        return "result=$(pm install-create -r -S "+size+") || { echo PHOTOFRAME_INSTALL_CREATE_FAILED; exit 1; }; "
            +"case \"$result\" in 'Success: created install session ['*']') ;; *) echo PHOTOFRAME_INSTALL_CREATE_FAILED; exit 1;; esac; "
            +"session=${result##*\\[}; session=${session%\\]}; "
            +"case \"$session\" in ''|*[!0-9]*) echo PHOTOFRAME_INSTALL_SESSION_INVALID; exit 1;; esac; "
            +"trap 'pm install-abandon \"$session\" >/dev/null 2>&1' EXIT; "
            +"result=$(pm install-write -S "+size+" \"$session\" base.apk - < "+quoted+") || { echo PHOTOFRAME_INSTALL_WRITE_FAILED; exit 1; }; "
            +"case \"$result\" in Success*) ;; *) echo PHOTOFRAME_INSTALL_WRITE_FAILED; exit 1;; esac; "
            +"result=$(pm install-commit \"$session\") || { echo PHOTOFRAME_INSTALL_COMMIT_FAILED; exit 1; }; "
            +"if [ \"$result\" = Success ]; then trap - EXIT; echo "+SUCCESS+"; else echo PHOTOFRAME_INSTALL_COMMIT_FAILED; exit 1; fi";
    }
}
