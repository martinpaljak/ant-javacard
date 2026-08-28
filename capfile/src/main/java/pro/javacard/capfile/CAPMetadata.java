// SPDX-FileCopyrightText: 2026 Martin Paljak <martin@martinpaljak.net>
// SPDX-License-Identifier: MIT

package pro.javacard.capfile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

// The Java-Card-* manifest attributes and applet.xml, written by kits since 2.2.2 and 3.0.1
// Reading takes the binary components as the authority
public final class CAPMetadata {

    // Main attributes, in the order a converter writes them
    static final String CREATED_BY = "Created-By";
    static final String RUNTIME_DESCRIPTOR_VERSION = "Runtime-Descriptor-Version";
    static final String APPLICATION_TYPE = "Application-Type";
    static final String CLASSIC_PACKAGE_AID = "Classic-Package-AID";
    static final String SEALED = "Sealed";
    // Package section, in the order a converter writes them
    static final String CREATION_TIME = "Java-Card-CAP-Creation-Time";
    static final String CONVERTER_VERSION = "Java-Card-Converter-Version";
    static final String CONVERTER_PROVIDER = "Java-Card-Converter-Provider";
    static final String CAP_FILE_VERSION = "Java-Card-CAP-File-Version";
    static final String PKG_VERSION = "Java-Card-Package-Version";
    static final String PKG_NAME = "Java-Card-Package-Name";
    static final String PKG_AID = "Java-Card-Package-AID";
    static final String APPLET_NAME = "Java-Card-Applet-%d-Name";
    static final String APPLET_AID = "Java-Card-Applet-%d-AID";
    static final String IMPORT_AID = "Java-Card-Imported-Package-%d-AID";
    static final String IMPORT_VERSION = "Java-Card-Imported-Package-%d-Version";
    static final String INTEGER_SUPPORT = "Java-Card-Integer-Support-Required";

    // Attributes written back exactly as the CAP file had them
    private static final String[] CARRIED = {CREATED_BY, RUNTIME_DESCRIPTOR_VERSION, APPLICATION_TYPE, SEALED,
            CREATION_TIME, CONVERTER_VERSION, CONVERTER_PROVIDER, CAP_FILE_VERSION, INTEGER_SUPPORT};

    // The descriptor format version, not the target: converters from 3.0.1 to 26.0 all write 3.0
    private static final String DESCRIPTOR_VERSION = "3.0";
    private static final String CLASSIC_APPLET = "classic-applet";

    // What this library names itself where it stamps the creation time
    private static final String GENERATOR = "pro.javacard.capfile";

    // A converter writes it as java.util.Date does, in the machine's zone; a stamped time is UTC
    private static final DateTimeFormatter CREATION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss 'UTC' yyyy", Locale.ROOT);

    private final AID aid;
    private final String name;
    private final String version;
    private final List<Applet> applets;
    private final List<CAPPackage> imports;
    private final Attributes carried;

    public CAPMetadata(AID aid, String name, String version, List<Applet> applets) {
        this(aid, name, version, applets, Collections.<CAPPackage>emptyList(), new Attributes());
    }

    private CAPMetadata(AID aid, String name, String version, List<Applet> applets, List<CAPPackage> imports,
                        Attributes carried) {
        if (aid == null || name == null || version == null) {
            throw new IllegalArgumentException("Package AID, name and version are required");
        }
        this.aid = aid;
        this.name = name;
        this.version = version;
        this.applets = Collections.unmodifiableList(new ArrayList<>(applets));
        this.imports = Collections.unmodifiableList(new ArrayList<>(imports));
        this.carried = new Attributes(carried);
    }

    public static final class Applet {
        private final AID aid;
        private final String name;
        private final String className;

        public Applet(AID aid, String className) {
            this(aid, simpleName(className), className);
        }

        Applet(AID aid, String name, String className) {
            if (aid == null) {
                throw new IllegalArgumentException("Applet AID is required");
            }
            this.aid = aid;
            this.name = name;
            this.className = className;
        }

        public AID getAid() {
            return aid;
        }

        public Optional<String> getName() {
            return Optional.ofNullable(name);
        }

        public Optional<String> getClassName() {
            return Optional.ofNullable(className);
        }
    }

    public AID getAid() {
        return aid;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public List<Applet> getApplets() {
        return applets;
    }

    public List<CAPPackage> getImports() {
        return imports;
    }

    // The CAP file format version, from the binary Header component where there is one
    public Optional<String> getCapFileVersion() {
        return getField(CAP_FILE_VERSION);
    }

    // Any manifest attribute this does not model, by name, as the CAP file stated it
    public Optional<String> getField(String name) {
        return Optional.ofNullable(carried.getValue(name));
    }

    public CAPMetadata withAppletClasses(Map<AID, String> classes) {
        List<Applet> named = new ArrayList<>();
        for (Applet applet : applets) {
            String className = classes.get(applet.aid);
            named.add(className == null ? applet : new Applet(applet.aid, className));
        }
        for (AID named_aid : classes.keySet()) {
            if (applets.stream().noneMatch(a -> a.aid.equals(named_aid))) {
                throw new IllegalArgumentException("Not an applet of this package: " + named_aid);
            }
        }
        return new CAPMetadata(aid, name, version, named, imports, carried);
    }

    public static CAPMetadata from(CAPFile cap) {
        Manifest manifest = cap.getManifest();
        Attributes main = manifest == null ? new Attributes() : manifest.getMainAttributes();
        Attributes section = manifest == null ? new Attributes() : orEmpty(packageSection(manifest));
        Map<AID, String> names = new LinkedHashMap<>();
        for (Applet applet : parseApplets(section)) {
            names.put(applet.aid, applet.name);
        }
        List<Applet> applets = new ArrayList<>();
        for (Map.Entry<AID, String> applet : cap.getApplets().entrySet()) {
            String carriedName = names.get(applet.getKey());
            applets.add(new Applet(applet.getKey(),
                    carriedName != null ? carriedName : simpleName(applet.getValue()), applet.getValue()));
        }
        Attributes carried = carriedFrom(main, section);
        put(carried, CAP_FILE_VERSION, cap.getCapVersion());
        if (carried.getValue(INTEGER_SUPPORT) == null) {
            carried.putValue(INTEGER_SUPPORT, cap.getFlags().contains("integers") ? "TRUE" : "FALSE");
        }
        return new CAPMetadata(cap.getPackageAID(), cap.getPackageName(), cap.getPackageVersion(),
                applets, cap.getImports(), carried);
    }

    // A JarInputStream parses the manifest as it opens and hands back nothing but the Manifest
    public static CAPMetadata from(Manifest manifest, InputStream appletXml) throws IOException {
        Attributes section = packageSection(manifest);
        if (section == null) {
            throw new IllegalArgumentException("Manifest describes no JavaCard package");
        }
        List<CAPPackage> imports = new ArrayList<>();
        for (int n = 1; section.getValue(String.format(IMPORT_AID, n)) != null; n++) {
            String[] v = orDefault(section.getValue(String.format(IMPORT_VERSION, n)), "0.0").split("\\.");
            imports.add(new CAPPackage(AID.fromString(section.getValue(String.format(IMPORT_AID, n))),
                    Integer.parseInt(v[0]), Integer.parseInt(v[1])));
        }
        CAPMetadata metadata = new CAPMetadata(AID.fromString(section.getValue(PKG_AID)), section.getValue(PKG_NAME),
                section.getValue(PKG_VERSION), parseApplets(section), imports,
                carriedFrom(manifest.getMainAttributes(), section));
        return appletXml == null ? metadata : metadata.withAppletClasses(appletClasses(appletXml));
    }

    public static CAPMetadata from(InputStream manifest, InputStream appletXml) throws IOException {
        return from(new Manifest(manifest), appletXml);
    }

    static Map<AID, String> appletClasses(InputStream appletXml) throws IOException {
        Map<AID, String> classes = new LinkedHashMap<>();
        NodeList declared = appletXmlDocument(appletXml).getElementsByTagName("applet");
        for (int i = 0; i < declared.getLength(); i++) {
            Element applet = (Element) declared.item(i);
            classes.put(AID.fromString(applet.getElementsByTagName("applet-AID").item(0).getTextContent()),
                    applet.getElementsByTagName("applet-class").item(0).getTextContent());
        }
        return classes;
    }

    private static Document appletXmlDocument(InputStream appletXml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Not really a threat (intended for self-generated local files) but still nice to have
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(appletXml);
            document.getDocumentElement().normalize();
            return document;
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException(e);
        }
    }

    public byte[] toManifest() {
        return toManifest(null);
    }

    // A null creationTime carries over the creation time and creator the converter wrote
    public byte[] toManifest(LocalDateTime creationTime) {
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.putValue(Attributes.Name.MANIFEST_VERSION.toString(), "1.0");
        put(main, CREATED_BY, creationTime == null ? carried.getValue(CREATED_BY) : GENERATOR);
        main.putValue(RUNTIME_DESCRIPTOR_VERSION, descriptorVersion());
        main.putValue(APPLICATION_TYPE, orDefault(carried.getValue(APPLICATION_TYPE), CLASSIC_APPLET));
        main.putValue(CLASSIC_PACKAGE_AID, aid.toAidUri());
        main.putValue(SEALED, orDefault(carried.getValue(SEALED), "true"));

        Attributes section = new Attributes();
        put(section, CREATION_TIME, creationTime == null ? carried.getValue(CREATION_TIME) : CREATION_TIME_FORMAT.format(creationTime));
        put(section, CONVERTER_VERSION, carried.getValue(CONVERTER_VERSION));
        put(section, CONVERTER_PROVIDER, carried.getValue(CONVERTER_PROVIDER));
        put(section, CAP_FILE_VERSION, carried.getValue(CAP_FILE_VERSION));
        section.putValue(PKG_VERSION, version);
        section.putValue(PKG_NAME, name);
        section.putValue(PKG_AID, aid.toColonHex());
        int n = 1;
        for (Applet applet : applets) {
            put(section, String.format(APPLET_NAME, n), applet.name);
            section.putValue(String.format(APPLET_AID, n), applet.aid.toColonHex());
            n++;
        }
        n = 1;
        for (CAPPackage imported : imports) {
            section.putValue(String.format(IMPORT_AID, n), imported.getAid().toColonHex());
            section.putValue(String.format(IMPORT_VERSION, n), imported.getVersionString());
            n++;
        }
        put(section, INTEGER_SUPPORT, carried.getValue(INTEGER_SUPPORT));
        manifest.getEntries().put(name.replace('.', '/'), section);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            manifest.write(out);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write the manifest", e);
        }
        return out.toByteArray();
    }

    // The converter's own layout, down to the indentation of the wrapped attributes
    private static final String APPLET_XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<applet-app xmlns=\"http://java.sun.com/xml/ns/javacard\"\n"
            + "       xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
            + "       xsi:schemaLocation=\"http://java.sun.com/xml/ns/javacard ../../../../docs/schemas/internal/applet-app_3_0.xsd\"\n"
            + "       version=\"%s\">\n";
    private static final String APPLET_XML_APPLET = "  <applet>\n"
            + "    <description>%s</description>\n"
            + "    <display-name>%s</display-name>\n"
            + "    <applet-class>%s</applet-class>\n"
            + "    <applet-AID>%s</applet-AID>\n"
            + "  </applet>\n";

    public byte[] toAppletXml() {
        if (applets.isEmpty()) {
            throw new IllegalStateException("A package with no applets gets no applet.xml");
        }
        StringBuilder xml = new StringBuilder(String.format(APPLET_XML_HEAD, descriptorVersion()));
        for (Applet applet : applets) {
            if (applet.className == null) {
                throw new IllegalStateException("Applet class not known for " + applet.aid);
            }
            xml.append(String.format(APPLET_XML_APPLET, applet.name, applet.name, applet.className, applet.aid.toAidUri()));
        }
        return xml.append("</applet-app>\n").toString().getBytes(StandardCharsets.UTF_8);
    }

    private String descriptorVersion() {
        return orDefault(carried.getValue(RUNTIME_DESCRIPTOR_VERSION), DESCRIPTOR_VERSION);
    }

    private static List<Applet> parseApplets(Attributes section) {
        List<Applet> applets = new ArrayList<>();
        for (int n = 1; section.getValue(String.format(APPLET_AID, n)) != null; n++) {
            applets.add(new Applet(AID.fromString(section.getValue(String.format(APPLET_AID, n))),
                    section.getValue(String.format(APPLET_NAME, n)), null));
        }
        return applets;
    }

    // A JavaCard package is a manifest section carrying a package AID, a file digest is not
    private static Attributes packageSection(Manifest manifest) {
        Attributes found = null;
        for (Attributes section : manifest.getEntries().values()) {
            if (section.getValue(PKG_AID) != null) {
                if (found != null) {
                    throw new IllegalArgumentException("Manifest describes more than one JavaCard package");
                }
                found = section;
            }
        }
        return found;
    }

    private static Attributes carriedFrom(Attributes main, Attributes section) {
        Attributes carried = new Attributes();
        for (String attribute : CARRIED) {
            put(carried, attribute, orDefault(main.getValue(attribute), section.getValue(attribute)));
        }
        return carried;
    }

    // Attributes rejects a null value; an attribute the metadata does not carry is left out.
    private static void put(Attributes attributes, String name, String value) {
        if (value != null) {
            attributes.putValue(name, value);
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static Attributes orEmpty(Attributes section) {
        return section == null ? new Attributes() : section;
    }

    // foo.bar.Baz -> Baz, the applet name a converter writes
    private static String simpleName(String className) {
        return className == null ? null : className.substring(className.lastIndexOf('.') + 1);
    }
}
