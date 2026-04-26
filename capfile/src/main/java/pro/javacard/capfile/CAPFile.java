// SPDX-FileCopyrightText: 2018 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

// Loosely based on code from GlobalPlatformPro, originally from GPJ
package pro.javacard.capfile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Parses a CAP file as specified in JavaCard 2.2 VM Specification, chapter 6.
 * CAP files are tiny, so we keep it in memory.
 */
public final class CAPFile {

    private static final String[] componentNames = {"Header", "Directory", "Import", "Applet", "Class", "Method", "StaticField", "Export",
            "ConstantPool", "RefLocation", "Descriptor", "Debug"};
    protected final Map<String, byte[]> entries; // All raw ZIP entries
    // Parsed content
    private final Map<AID, String> applets = new LinkedHashMap<>();
    private final List<CAPPackage> imports = new ArrayList<>();
    private final CAPPackage pkg;
    private final byte flags;
    private final String cap_version;
    // Metadata
    private Manifest manifest = null; // From 2.2.2
    private Document appletxml = null; // From 3.0.1
    private Path file;


    public static CAPFile fromStream(InputStream in) throws IOException {
        return new CAPFile(in);
    }

    public static CAPFile fromBytes(byte[] bytes) throws IOException {
        return fromStream(new ByteArrayInputStream(bytes));
    }

    public static CAPFile fromFile(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            CAPFile cap = fromStream(in);
            cap.file = path;
            return cap;
        }
    }

    public Optional<Path> getFile() {
        return Optional.ofNullable(file);
    }

    public byte[] getComponent(String name) {
        byte[] c = entries.get(pkg2jcdir(getPackageName()) + name + ".cap");
        return c == null ? null : c.clone();
    }

    public byte[] getMetaInfEntry(String name) {
        return entries.get("META-INF/" + name);
    }

    public Optional<byte[]> getZipComponent(String name) {
        return Optional.ofNullable(entries.get(name));
    }

    public void store(OutputStream to) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(to)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
    }

    // XXX: 21 rightfully complains about this without final (getComponent leaking this)
    protected CAPFile(InputStream in) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            // All ZIP entries
            entries = readEntries(zip);
        }

        // Parse manifest
        byte[] mf = entries.get("META-INF/MANIFEST.MF");
        if (mf != null) {
            ByteArrayInputStream mfi = new ByteArrayInputStream(mf);
            manifest = new Manifest(mfi);
        }

        // Only if there are applets
        byte[] ai = entries.get("APPLET-INF/applet.xml");
        if (ai != null) {
            try {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                // Not really a threat (intended for self-generated local files) but still nice to have
                dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                appletxml = dBuilder.parse(new ByteArrayInputStream(ai));
                appletxml.getDocumentElement().normalize();
            } catch (SAXException | ParserConfigurationException e) {
                throw new IOException(e);
            }
        }


        // Figure out package name. Failsafe without metadata as well, for 2.1.X support.
        String pkgname = null;
        for (String p : entries.keySet()) {
            if (p.endsWith("Header.cap")) {
                pkgname = jcdir2pkg(p);
                break;
            }
        }

        if (pkgname == null) {
            throw new IOException("Could not figure out the package name of the applet!");
        }

        // Parse package.
        // See JCVM 2.2 spec section 6.3 for offsets.
        byte[] header = entries.get(pkg2jcdir(pkgname) + "Header.cap");
        cap_version = String.format("%d.%d", header[8], header[7]);
        flags = header[9];

        pkg = new CAPPackage(new AID(header, 13, header[12]), header[11], header[10], pkgname);

        // Parse applets
        // See JCVM 2.2 spec section 6.5 for offsets.
        byte[] applet = getComponent("Applet");
        if (applet != null) {
            int offset = 4;
            for (int j = 0; j < (applet[3] & 0xFF); j++) {
                int len = applet[offset++];
                AID appaid = new AID(applet, offset, len);
                // We might already have it, with the name from metadata
                // FIXME: use metadata only as additional source
                if (!applets.containsKey(appaid)) {
                    applets.put(appaid, null);
                }
                // Skip install_method_offset
                offset += len + 2;
            }
        }
        // Parse imports
        byte[] imps = getComponent("Import");
        if (imps != null) {
            int offset = 4;
            for (int j = 0; j < (imps[3] & 0xFF); j++) {
                AID aid = new AID(imps, offset + 3, imps[offset + 2]);
                CAPPackage p = new CAPPackage(aid, imps[offset + 1], imps[offset]);
                imports.add(p);
                offset += imps[offset + 2] + 3;
            }
        }

        // Parse metadata to get applet names. Somewhat redundant
        if (appletxml != null) {
            NodeList apps = appletxml.getElementsByTagName("applet");
            for (int i = 0; i < apps.getLength(); i++) {
                Element app = (Element) apps.item(i);
                String name = app.getElementsByTagName("applet-class").item(0).getTextContent();
                String aidstring = app.getElementsByTagName("applet-AID").item(0).getTextContent();
                AID aid = AID.fromString(aidstring.replace("//aid/", "").replace("/", ""));
                if (!applets.containsKey(aid)) {
                    throw new IOException("applet.xml contains missing applet " + aid);
                }
                applets.put(aid, name);
            }
        }
    }

    private static Map<String, byte[]> readEntries(ZipInputStream in) throws IOException {
        Map<String, byte[]> result = new LinkedHashMap<>();
        ZipEntry entry = in.getNextEntry();
        while (entry != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int c;
            while ((c = in.read(buf)) != -1) {
                bos.write(buf, 0, c);
            }
            result.put(entry.getName(), bos.toByteArray());
            entry = in.getNextEntry();
        }
        return Collections.unmodifiableMap(result);
    }

    public AID getPackageAID() {
        return pkg.aid;
    }

    public List<AID> getAppletAIDs() {
        List<AID> result = new ArrayList<>();
        result.addAll(applets.keySet());
        return result;
    }

    public String getPackageVersion() {
        return pkg.getVersionString();
    }

    public String getPackageName() {
        return pkg.getName().orElseThrow(() -> new IllegalStateException("No package name"));
    }

    public byte[] getCode() {
        return _getCode(false);
    }

    @Deprecated
    public byte[] getCode(boolean includeDebug) {
        return _getCode(includeDebug);
    }

    byte[] _getCode(boolean includeDebug) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        for (String name : componentNames) {
            byte[] c = getComponent(name);
            if (c == null) {
                continue;
            }
            if (!includeDebug && (name.equals("Debug") || name.equals("Descriptor"))) {
                continue;
            }
            try {
                result.write(c);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return result.toByteArray();
    }

    public byte[] getLoadFileDataHash(String hash) {
        try {
            return MessageDigest.getInstance(hash).digest(getCode());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Not possible", e);
        }
    }

    @Deprecated
    public byte[] getLoadFileDataHash(String hash, boolean includeDebug) {
        try {
            return MessageDigest.getInstance(hash).digest(_getCode(includeDebug));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Not possible", e);
        }
    }

    public void dump(PrintStream out) {
        Optional<String> gpv = guessGlobalPlatformVersion();
        Optional<String> jcv = guessJavaCardVersion();
        String gpversion = gpv.isPresent() ? "/GlobalPlatform " + gpv.get() : "";

        out.println(String.format("CAP file (v%s), contains: %s for JavaCard %s%s", cap_version, String.join(", ", getFlags()), jcv.orElse("2.1.1?"), gpversion));
        out.printf("Package: %s %s v%s%n", pkg.getName().get(), pkg.getAid().toString(), pkg.getVersionString());
        for (Map.Entry<AID, String> applet : getApplets().entrySet()) {
            out.println("Applet:  " + (applet.getValue() == null ? "" : applet.getValue() + " ") + applet.getKey());
        }
        for (CAPPackage imp : getImports()) {
            out.println("Import:  " + imp);
        }
        // Check manifest for metadata
        if (manifest != null) {
            Attributes mains = manifest.getMainAttributes();

            // iterate all packages
            Map<String, Attributes> ent = manifest.getEntries();
            if (ent.keySet().size() > 1) {
                throw new IllegalArgumentException("Too many elements in CAP manifest");
            }
            if (ent.keySet().size() == 1) {
                Attributes caps = ent.get(ent.keySet().toArray()[0]);
                // Generic
                String jdk_name = mains.getValue("Created-By");
                // JC specific
                String cap_creation_time = caps.getValue("Java-Card-CAP-Creation-Time");
                String converter_version = caps.getValue("Java-Card-Converter-Version");
                String converter_provider = caps.getValue("Java-Card-Converter-Provider");

                out.println(String.format("Generated by %s converter %s", converter_provider, converter_version));
                out.println(String.format("On %s with JDK %s", cap_creation_time, jdk_name));
            }
        }
        out.println(String.format("Code size %d bytes (%d with debug)", getCode().length, getCode(true).length));
        out.println("SHA-256 " + HexUtils.bin2hex(getLoadFileDataHash("SHA-256")).toLowerCase());
        out.println("SHA-1   " + HexUtils.bin2hex(getLoadFileDataHash("SHA-1")).toLowerCase());
    }

    public List<String> getFlags() {
        return flags2strings(flags);
    }

    public static List<String> flags2strings(byte flags) {
        ArrayList<String> result = new ArrayList<>();
        // Table 6-3: CAP File Package Flags
        if ((flags & 0x01) == 0x01) {
            result.add("integers");
        }
        if ((flags & 0x02) == 0x02) {
            result.add("exports");
        }
        if ((flags & 0x04) == 0x04) {
            result.add("applets");
        }
        return result;
    }

    public List<CAPPackage> getImports() {
        return Collections.unmodifiableList(imports);
    }

    public Map<AID, String> getApplets() {
        return Collections.unmodifiableMap(applets);
    }

    // Guess the targeted JavaCard version based on imported package versions.
    //
    // Mapping derived from parsing export files in actual SDK kits (jc211 through jc320v25.1):
    //   framework      1.0=2.1.x, 1.2=2.2.1, 1.3=2.2.2, 1.4=3.0.1, 1.5=3.0.4, 1.6=3.0.5, 1.8=3.1.0, 1.9=3.2.0
    //   security/crypto 1.1=2.1.x, 1.2=2.2.1, 1.3=2.2.2, 1.4=3.0.1, 1.5=3.0.4, 1.6=3.0.5, 1.7=3.1.0, 1.8=3.2.0
    // Note: framework minor versions diverge from security/crypto starting from 3.1.0 (framework skips minor=7).
    // Note: SDK 2.1.1 and 2.1.2 ship identical module versions - indistinguishable.
    public Optional<String> guessJavaCardVersion() {
        // Primary: javacard.framework has a unique version progression (skips minor=7)
        AID jf = new AID("A0000000620101"); // javacard.framework
        for (CAPPackage p : imports) {
            if (p.aid.equals(jf)) {
                switch (p.minor) {
                    case 0:
                        return Optional.of("2.1.1");
                    case 1:
                        // No actual SDK ships framework 1.1; kept for historical reasons
                        return Optional.of("2.1.2");
                    case 2:
                        return Optional.of("2.2.1");
                    case 3:
                        return Optional.of("2.2.2");
                    case 4:
                        return Optional.of("3.0.1");
                    case 5:
                        return Optional.of("3.0.4");
                    case 6:
                        return Optional.of("3.0.5");
                    // minor=7 not used by any SDK
                    case 8:
                        return Optional.of("3.1.0");
                    case 9:
                        return Optional.of("3.2.0");
                    default:
                        return Optional.of(String.format("unknown: %d.%d", p.major, p.minor));
                }
            }
        }

        // Fallback: javacard.security and javacardx.crypto share identical version progression
        AID js = new AID("A0000000620102"); // javacard.security
        AID jc = new AID("A0000000620201"); // javacardx.crypto
        for (CAPPackage p : imports) {
            if (p.aid.equals(js) || p.aid.equals(jc)) {
                switch (p.minor) {
                    case 1:
                        return Optional.of("2.1.1");
                    case 2:
                        return Optional.of("2.2.1");
                    case 3:
                        return Optional.of("2.2.2");
                    case 4:
                        return Optional.of("3.0.1");
                    case 5:
                        return Optional.of("3.0.4");
                    case 6:
                        return Optional.of("3.0.5");
                    case 7:
                        return Optional.of("3.1.0");
                    case 8:
                        return Optional.of("3.2.0");
                    default:
                        return Optional.of(String.format("unknown: %d.%d", p.major, p.minor));
                }
            }
        }
        return Optional.empty();
    }

    // Guess GP version from org.globalplatform import version (A00000015100)
    public Optional<String> guessGlobalPlatformVersion() {
        AID jf = new AID("A00000015100");
        for (CAPPackage p : imports) {
            if (p.aid.equals(jf) && p.major == 1) {
                if (p.minor == 0) {
                    return Optional.of("2.1.1");
                } else if (p.minor >= 1 && p.minor <= 4) {
                    return Optional.of("2.2");
                } else if (p.minor == 5 || p.minor == 6) {
                    return Optional.of("2.2.1");
                } else if (p.minor == 7) {
                    // This is not really right, but a good indication nevertheless
                    return Optional.of("2.3.1+A");
                } else {
                    return Optional.of(String.format("unknown: %d.%d", p.major, p.minor));
                }
            }
        }
        return Optional.empty();
    }

    private static String pkg2jcdir(String pkgname) {
        return pkgname.replace(".", "/") + "/javacard/";
    }

    private static String jcdir2pkg(String jcdir) {
        return jcdir.substring(0, jcdir.lastIndexOf("/javacard/")).replace('/', '.');
    }

    public static void uncheckedDelete(Path p) throws UncheckedIOException {
        try {
            Files.delete(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Remove compiled code from capfile
    public static void strip(Path cap) throws IOException {
        Map<String, String> props = new HashMap<>();
        props.put("create", "false");

        URI zip_disk = URI.create("jar:" + cap.toUri());
        try (FileSystem zipfs = FileSystems.newFileSystem(zip_disk, props)) {
            List<Path> toDelete = Files.walk(zipfs.getPath("/")).filter(p -> p.toString().endsWith(".class")).collect(Collectors.toList());
            Collections.sort(toDelete, Collections.reverseOrder(Comparator.comparingInt(o -> o.toString().length())));
            toDelete.stream().forEach(CAPFile::uncheckedDelete);
        }
    }
}
