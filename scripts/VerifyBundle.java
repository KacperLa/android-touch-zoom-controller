import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Verify every bundle payload entry, not merely that one expected certificate is present. */
public final class VerifyBundle {
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("bundle path and expected certificate SHA256 required");
        int payload=0;
        byte[] buffer=new byte[32768];
        try(JarFile jar=new JarFile(Path.of(args[0]).toFile(),true)){
            var entries=jar.entries();
            while(entries.hasMoreElements()){
                JarEntry e=entries.nextElement();if(e.isDirectory())continue;
                try(InputStream in=jar.getInputStream(e)){while(in.read(buffer)!=-1){}}
                String n=e.getName().toUpperCase(Locale.ROOT);
                if(n.equals("META-INF/MANIFEST.MF") || (n.startsWith("META-INF/") && n.substring(9).indexOf('/')<0
                    && (n.endsWith(".SF")||n.endsWith(".RSA")||n.endsWith(".DSA")||n.endsWith(".EC"))))continue;
                Certificate[] certs=e.getCertificates();
                if(certs==null || certs.length!=1)throw new SecurityException("Unsigned or unexpected signer chain: "+e.getName());
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certs[0].getEncoded()));
                if(!hash.equalsIgnoreCase(args[1]))throw new SecurityException("Unexpected signer: "+e.getName());
                ((X509Certificate)certs[0]).checkValidity();payload++;
            }
        }
        if(payload==0)throw new SecurityException("No signed payload");
        System.out.println("Verified "+payload+" signed payload entries with expected valid certificate.");
    }
}
