/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package top.marchand.xml.maven.catalog;

import top.marchand.xml.maven.catalog.model.CatalogModel;
import top.marchand.xml.maven.catalog.model.RewriteSystemModel;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javanet.staxutils.IndentingXMLStreamWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.apache.maven.shared.dependency.graph.traversal.DependencyNodeVisitor;

import java.util.HashMap;
import java.util.stream.Collectors;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltTransformer;

/**
 * Generates a catalog, based on dependency tree, where each dependency is re-written to the jar URL.
 */
@Mojo(
        name = "catalog",
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class Catalog extends AbstractMojo {

    public static final transient String SCHEME = "dependency:/";

    /**
     * Current project
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    public MavenProject project;

    /**
     * The catalog file.
     * It is always a path relative to <tt>${project.basedir}</tt>.
     */
    @Parameter(defaultValue = "catalog.xml")
    public String catalogFileName;

    /**
     * The URI patterns to generate.
     * Valid values are :
     * <table>
     *   <caption>Kind of patterns and URIs</caption>
     *   <thead>
     *     <tr><th>pattern</th><th>URI form</th></tr>
     *   </thead>
     *   <tbody>
     *     <tr><td><tt>standard</tt></td><td><tt>dependency:/goupId+artifactId/path/to/file.xml</tt></td></tr>
     *     <tr><td><tt>full</tt></td><td><tt>dependency:/groupId+artifactId$version/path/to/file.xml</tt></td></tr>
     *   </tbody>
     * </table>
     */
    @Parameter()
    public List<String> uriPatterns;
    {
        uriPatterns = new ArrayList<>();
        uriPatterns.add("standard");
    }

    /**
     * The entries to add in catalog. If not defined, <tt>&lt;rewriteURI/&gt;</tt>
     * and <tt>&lt;rewriteSystem/&gt;</tt> will be generated.
     */
    @Parameter()
    public List<String> generates;

    {
        generates = new ArrayList<>();
        generates.add("rewriteURI");
        generates.add("rewriteSystem");
    }

    /**
     * If defined, all catalog entries will be rewritten to this protocol.
     * Usually, if defined, the value is <tt>cp:/</tt>, and generaterd artifact
     * will be used with https://github.com/cmarchand/cp-protocol.
     */
    @Parameter()
    private String rewriteToProtocol;

    /**
     * If <tt>true</tt>, current artifact (the project which is actually built) will
     * be included in catalog.
     * This does not depend on {@link #includes} and {@link #excludes}
     */
    @Parameter()
    public boolean includeCurrentArtifact;

    /**
     * The next catalog to add to catalog. If null, no <tt>&lt;nextCatalog/&gt;</tt>
     * will be added.
     */
    @Parameter()
    public List<String> nextCatalogs;

    /**
     * List of artifacts to exclude.
     * Each artifact must be specified as <tt>groupId:artifactId</tt>.
     * Both <tt>groupId</tt> and <tt>artifactId</tt> can be replaced by <tt>*</tt>.
     * <tt>excludes</tt> and {@link #includes} are exclusives, and <strong>must not</strong> be used together.
     * <p>
     * If <tt>excludes</tt> is specified, all dependencies are used, except the ones that
     * match <tt>excludes</tt>.
     * <p>
     * Project's artifact is processed neither by <tt>excludes</tt> nor <tt>includes</tt>
     */
    @Parameter()
    public List<String> excludes;

    /**
     * List of artifacts to include.
     * Each artifact must be specified as <tt>groupId:artifactId</tt>.
     * Both <tt>groupId</tt> and <tt>artifactId</tt> can be replaced by <tt>*</tt>.
     * <tt>includes</tt> and {@link #excludes} are exclusives, and <strong>must not</strong> be used together.
     * <p>
     * If <tt>includes</tt> is specified, all dependencies that match <tt>includes</tt> are used.
     * <p>
     * Project's artifact is processed neither by <tt>excludes</tt> nor <tt>includes</tt>
     */
    @Parameter()
    public List<String> includes;

    /**
     * Allows to add <tt>&lt;delegatePublic /&gt;</tt> entries to generated catalog.
     * Each entry should be as :
     * <pre>&lt;delegateEntry&gt;
     *   &lt;startString&gt;publicIdStartString&lt;/startString&gt;
     *   &lt;catalog&gt;catalog&lt;/catalog&gt;
     * &lt;/delegateEntry&gt;</pre>
     */
    @Parameter()
    public List<DelegateEntry> delegatesPublic;

    /**
     * Allows to add <tt>&lt;delegateSystem /&gt;</tt> entries to generated catalog.
     * Each entry should be as :
     * <pre>&lt;delegateEntry&gt;
     *   &lt;startString&gt;systemIdStartString&lt;/startString&gt;
     *   &lt;catalog&gt;catalog&lt;/catalog&gt;
     * &lt;/delegateEntry&gt;</pre>
     */
    @Parameter()
    public List<DelegateEntry> delegatesSystem;

    /**
     * Allows to add <tt>&lt;delegateURI /&gt;</tt> entries to generated catalog.
     * Each entry should be as :
     * <pre>&lt;delegateEntry&gt;
     *   &lt;startString&gt;uriStartString&lt;/startString&gt;
     *   &lt;catalog&gt;catalog&lt;/catalog&gt;
     * &lt;/delegateEntry&gt;</pre>
     */
    @Parameter()
    public List<DelegateEntry> delegatesURI;

    /**
     * If set to <tt>true</tt>, removes the DOCTYPE from catalog file.
     * Default is true, so this is a major change since 1.0.6
     *
     * @since 1.0.7
     */
    @Parameter(defaultValue = "true")
    public boolean removeDoctype;

    /**
     * Allows to generate a special catalog for Oxygen, where
     * jar:file:/ URIs are transformed to zip:file:/ URIs.
     * Oxygen has a special mecanism to load these kind of resources.
     */
    @Parameter(defaultValue = "false")
    public boolean generateOxygenCatalog;

    @Component(hint = "default")
    private DependencyGraphBuilder dependencyGraphBuilder;

    @Parameter
    private ChmLogger.LogReason[] logReasons;

    private DependencyNode rootNode;

    private ChmLogger chmLogger;

    private HashMap<File, MyArtifact> dependencyDirs;
    private Processor proc;
    private DocumentBuilder builder;
    private XPathCompiler xpathCompiler;

    @Override
    public void execute() throws MojoExecutionException {
        dependencyDirs = new HashMap<>();
        proc = new Processor(Configuration.newConfiguration());
        builder = proc.newDocumentBuilder();
        xpathCompiler = proc.newXPathCompiler();
        chmLogger = new ChmLogger(getLog());
        for (ChmLogger.LogReason reason : logReasons) {
            chmLogger.enableReason(reason, true);
        }
        chmLogger.log(
                ChmLogger.LogReason.PARAMETERS,
                ChmLogger.LogLevel.INFO,
                "catalogFile: "+catalogFileName
        );
        final List<String> classpaths;
        try {
            classpaths = new ArrayList<>(project.getCompileClasspathElements().size());
            for (Object i : project.getCompileClasspathElements()) {
                classpaths.add(i.toString());
            }
            chmLogger.log(ChmLogger.LogReason.CLASSPATH, ChmLogger.LogLevel.INFO, LOG_PREFIX + "classpaths=" + classpaths);
            try {
                rootNode = dependencyGraphBuilder.buildDependencyGraph(project, buildArtifactFilter());
                final CatalogModel catalog = new CatalogModel();
                DependencyNodeVisitor visitor = new DependencyNodeVisitor() {
                    @Override
                    public boolean visit(DependencyNode dn) {
                        chmLogger.log(
                                ChmLogger.LogReason.VISITING,
                                ChmLogger.LogLevel.INFO,
                                LOG_PREFIX + "Visiting " + dn.toNodeString());
                        if (shouldProcessDependency(dn)) {
                            processDependency(dn, classpaths, catalog);
                        }
                        return true;
                    }

                    @Override
                    public boolean endVisit(DependencyNode dn) {
                        return true;
                    }
                };
                rootNode.accept(visitor);
                writeCatalog(catalog);
                getLog().debug(LOG_PREFIX + catalog.toString());
                if (generateOxygenCatalog) {
                    writeOxygenCatalog();
                }
            } catch (XMLStreamException | IOException | DependencyGraphBuilderException | SaxonApiException ex) {
                getLog().error(LOG_PREFIX + ex.getMessage(), ex);
                throw new MojoExecutionException(ex.getMessage(), ex);
            }
        } catch (DependencyResolutionRequiredException ex) {
            getLog().error(LOG_PREFIX + ex.getMessage(), ex);
        }
    }

    protected boolean shouldProcessDependency(DependencyNode dn) {
        Artifact artifact = dn.getArtifact();
        if (artifact.equals(project.getArtifact())) {
            chmLogger.log(
                    ChmLogger.LogReason.EXCLUSION,
                    ChmLogger.LogLevel.INFO,
                    "Current artifact is " + (includeCurrentArtifact ? "" : "not ") + "excluded");
            return includeCurrentArtifact;
        }
        String groupId = artifact.getGroupId();
        String artifactId = artifact.getArtifactId();

        String[] patterns = {groupId + ":" + artifactId, "*:" + artifactId, groupId + ":*"};
        if ((includes == null || includes.isEmpty()) && (excludes == null || excludes.isEmpty())) {
            return true;
        }
        if (includes != null && !includes.isEmpty()) {
            for (String pattern : patterns) {
                if (includes.contains(pattern)) {
                    return true;
                }
            }
            chmLogger.log(
                    ChmLogger.LogReason.EXCLUSION,
                    ChmLogger.LogLevel.INFO,
                    "Excluding " + artifact.toString() + " because includes is not empty and no pattern matches it"
            );
            return false;
        } else {
            for (String pattern : patterns) {
                if (excludes.contains(pattern)) {
                    chmLogger.log(
                            ChmLogger.LogReason.EXCLUSION,
                            ChmLogger.LogLevel.INFO,
                            "excluding " + artifact.toString() + " because it is excluded by " + pattern
                    );
                    return false;
                }
            }
            return true;
        }
    }

    private void processDependency(DependencyNode dn, List<String> classpaths, CatalogModel catalog) {
        String groupId = dn.getArtifact().getGroupId();
        String artifactId = dn.getArtifact().getArtifactId();
        String version = dn.getArtifact().getVersion();
        chmLogger.log(
                ChmLogger.LogReason.DEPENDENCY,
                ChmLogger.LogLevel.INFO,
                String.format("Processing dependency [%s:%s:%s]", groupId, artifactId, version)
        );
        if (rewriteToProtocol != null && rewriteToProtocol.length() > 1) {
            for (String pattern : uriPatterns) {
                RewriteSystemModel rsm = new RewriteSystemModel(
                        buildPattern(pattern, groupId, artifactId, version),
                        rewriteToProtocol,
                        groupId, artifactId, version);
                catalog.getEntries().add(rsm);
            }
        } else {
            try {
                String jarFileName = null;
                if (isInJarWithDependencies(dn)) {
                    getLog().debug(LOG_PREFIX + artifactId + " is in a jar-with-dependencies");
                    jarFileName = getJarFileForJarWithDependency(dn, classpaths);
                } else {
                    getLog().debug(LOG_PREFIX + artifactId + " is in a jar");
                    String artifactPath = constructArtifactPath(dn.getArtifact());
                    getLog().debug(LOG_PREFIX + "artifactPath= " + artifactPath);
                    for (String classpath : classpaths) {
                        chmLogger.log(
                                ChmLogger.LogReason.DEPENDENCY,
                                ChmLogger.LogLevel.INFO,
                                "\t comparing to classpath "+classpath
                        );
                        if (classpath.contains(artifactPath)) {
                            chmLogger.log(
                                    ChmLogger.LogReason.DEPENDENCY,
                                    ChmLogger.LogLevel.INFO,
                                    "\t\t"+classpath+" contains "+artifactPath
                            );
                            jarFileName = classpath;
                        } else if (classpath.endsWith("target/classes") || classpath.matches(".*[/\\\\]target[/\\\\][^/\\\\]+\\.jar")) {
                            // issue #2
                            getLog().debug("found classpath : " + classpath);
                            // dir should be the project basedir
                            File dir = new File(classpath).getParentFile().getParentFile();
                            if (isPathMatchesDependency(dir, groupId, artifactId, version)) {
                                chmLogger.log(
                                        ChmLogger.LogReason.DEPENDENCY,
                                        ChmLogger.LogLevel.INFO,
                                        "\t\t"+dir.getAbsolutePath()+" matches artifact"
                                );
                                jarFileName = classpath;
                            }
                        }
                    }
                }
                getLog().debug(LOG_PREFIX + artifactId + " -> " + jarFileName);
                if (jarFileName != null) {
                    for (String pattern : uriPatterns) {
                        RewriteSystemModel rsm = (jarFileName.endsWith(".jar")) ?
                                new RewriteSystemModel(
                                        buildPattern(pattern, groupId, artifactId, version),
                                        "jar:file:" + jarFileName + "!/",
                                        groupId, artifactId, version) :
                                new RewriteSystemModel(
                                        buildPattern(pattern, groupId, artifactId, version),
                                        new File(jarFileName).toURI().toString(),
                                        groupId, artifactId, version);
                        catalog.getEntries().add(rsm);
                    }
                } else {
                    chmLogger.log(
                            ChmLogger.LogReason.EXCLUSION,
                            ChmLogger.LogLevel.INFO,
                            "\t\tNo classpath found for artifact"
                    );
                }
            } catch (OverConstrainedVersionException ex) {
                getLog().error(LOG_PREFIX + ex.getMessage(), ex);
            }
        }
    }

    protected String buildPattern(String pattern, String groupId, String artifactId, String version) {
        StringBuilder sb = new StringBuilder(SCHEME);
        if ("full".equals(pattern) || "standard".equals(pattern)) {
            sb.append(groupId).append("+");
        }
        sb.append(artifactId);
        if ("full".equals(pattern)) {
            sb.append("$").append(version);
        }
        sb.append("/");
        return sb.toString();
    }

    boolean isPathMatchesDependency(final File dir, final String groupId, final String artifactId, final String version) {
        MyArtifact art = dependencyDirs.get(dir);
        if (art == null) {
            art = loadArtifactFromDir(dir);
        }
        if (art != null) {
            chmLogger.log(
                    ChmLogger.LogReason.DEPENDENCY,
                    ChmLogger.LogLevel.INFO,
                    "\t\t\tart is "+art
            );
            dependencyDirs.put(dir, art);
            boolean ret = groupId.equals(art.getGroupId()) && artifactId.equals(art.getArtifactId()) && version.equals(art.getVersion());
            if (ret == false) {
                chmLogger.log(
                        ChmLogger.LogReason.MATCHES,
                        ChmLogger.LogLevel.INFO,
                        art.toString() + " does not match " + groupId + ":" + artifactId + ":" + version
                );
            }
            return ret;
        } else {
            chmLogger.log(
                    ChmLogger.LogReason.MATCHES,
                    ChmLogger.LogLevel.INFO,
                    "no artifact found in dir " + dir.getAbsolutePath()
            );
            return false;
        }
    }

    MyArtifact loadArtifactFromDir(final File dir) {
        try {
            XdmNode pom = builder.build(new File(dir, "pom.xml"));
            xpathCompiler.declareNamespace("mvn", "http://maven.apache.org/POM/4.0.0");
            XPathSelector selector = xpathCompiler.compile("/mvn:project/(mvn:groupId | mvn:artifactId | mvn:version)").load();
            selector.setContextItem(pom);
            XdmValue ret = selector.evaluate();
            String groupId = null, artifactId = null, version = null;
            for (XdmSequenceIterator it = ret.iterator(); it.hasNext(); ) {
                XdmNode node = (XdmNode) it.next();
                switch (node.getNodeName().getLocalName()) {
                    case "groupId":
                        groupId = node.getStringValue();
                        break;
                    case "artifactId":
                        artifactId = node.getStringValue();
                        break;
                    case "version":
                        version = node.getStringValue();
                }
            }
            if (version == null || groupId == null) {
                String relativePath = "../pom.xml";
                // case where version is in parent pom. Look for it...
                XPathSelector selector2 = xpathCompiler.compile("/mvn:project/mvn:parent/mvn:relativePath").load();
                selector2.setContextItem(pom);
                XdmValue vRelativePath = selector2.evaluate();
                if (vRelativePath.size() > 0) {
                    getLog().debug("vRelativePath is a " + vRelativePath.getClass().getName());
                    getLog().debug("vRelativePath is " + vRelativePath.size() + " long");
                    getLog().debug("vRelativePath: " + vRelativePath.toString());
                    relativePath = ((XdmNode) vRelativePath).getStringValue();
                }
                File parentPomFile = new File(dir, relativePath);
                if (parentPomFile.isDirectory()) {
                    parentPomFile = new File(parentPomFile, "pom.xml");
                }
                if (version == null) {
                    XPathSelector versionSelector = xpathCompiler.compile("/mvn:project/mvn:version").load();
                    versionSelector.setContextItem(builder.build(parentPomFile));
                    version = ((XdmNode) versionSelector.evaluate()).getStringValue();
                }
                if (groupId == null) {
                    XPathSelector groupIdSelector = xpathCompiler.compile("/mvn:project/mvn:groupId").load();
                    groupIdSelector.setContextItem(builder.build(parentPomFile));
                    groupId = ((XdmNode) groupIdSelector.evaluate()).getStringValue();
                }
            }
            MyArtifact art = new MyArtifact(groupId, artifactId, version);
            getLog().debug(art.toString());
            return art;
        } catch (SaxonApiException ex) {
            getLog().error("in loadArtifactFromDir", ex);
            return null;
        }
    }

    private void writeOxygenCatalog() throws SaxonApiException {
        File sourceFile = new File(project.getBasedir(), catalogFileName);
        String oxygenCatalogFileName = sourceFile.getName();
        int lastIndex = oxygenCatalogFileName.lastIndexOf(".");
        oxygenCatalogFileName = oxygenCatalogFileName.substring(0, lastIndex) + "-oxygen" + oxygenCatalogFileName.substring(lastIndex);
        File targetFile = new File(sourceFile.getParentFile(), oxygenCatalogFileName);

        Source source = new StreamSource(getClass().getResourceAsStream("/top/marchand/xml/maven/catalog/xsl/oxygen-catalog-converter.xsl"));
        XsltTransformer tr = proc.newXsltCompiler().compile(source).load();
        tr.setSource(new StreamSource(sourceFile));
        Serializer dest = proc.newSerializer(targetFile);
        dest.setOutputProperty(Serializer.Property.INDENT, "true");
        tr.setDestination(dest);
        tr.transform();
    }

    private void writeCatalog(CatalogModel catalog) throws FileNotFoundException, XMLStreamException, IOException, MojoExecutionException {
        XMLOutputFactory fact = XMLOutputFactory.newFactory();
        File catalogFile = new File(project.getBasedir(), catalogFileName);
        File directory = catalogFile.getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(catalogFile)) {
            XMLStreamWriter writer = fact.createXMLStreamWriter(fos, "UTF-8");
            writer = new IndentingXMLStreamWriter(writer);
            writer.writeStartDocument("UTF-8", "1.0");
            if (!removeDoctype) {
                writer.writeDTD("<!DOCTYPE catalog PUBLIC \"-//OASIS//DTD Entity Resolution XML Catalog V1.0//EN\" \"http://www.oasis-open.org/committees/entity/release/1.0/catalog.dtd\">");
            }
            writer.setDefaultNamespace("urn:oasis:names:tc:entity:xmlns:xml:catalog");
            writer.writeStartElement(CATALOG_NS, "catalog");
            writer.writeAttribute("xmlns", CATALOG_NS);
            for (RewriteSystemModel rsm : catalog.getEntries()) {
                for (String generate : generates) {
                    writeCatalogEntry(writer, generate, rsm);
                }
            }
            if (delegatesPublic != null) {
                for (DelegateEntry de : delegatesPublic) {
                    writeDelegateEntry(writer, "delegatePublic", de);
                }
            }
            if (delegatesSystem != null) {
                for (DelegateEntry de : delegatesSystem) {
                    writeDelegateEntry(writer, "delegateSystem", de);
                }
            }
            if (delegatesURI != null) {
                for (DelegateEntry de : delegatesURI) {
                    writeDelegateEntry(writer, "delegateURI", de);
                }
            }
            if (nextCatalogs != null) {
                for (String nextCatalog : nextCatalogs) {
                    writer.writeEmptyElement(CATALOG_NS, "nextCatalog");
                    writer.writeAttribute("catalog", nextCatalog);
                }
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            fos.flush();
        }
    }

    protected void writeDelegateEntry(XMLStreamWriter writer, String delegate, DelegateEntry entry) throws XMLStreamException, MojoExecutionException {
        switch (delegate) {
            case "delegatePublic": {
                writer.writeEmptyElement(CATALOG_NS, "delegatePublic");
                writer.writeAttribute("publicIdStartString", entry.getStartString());
                writer.writeAttribute("catalog", entry.getCatalog());
                break;
            }
            case "delegateSystem": {
                writer.writeEmptyElement(CATALOG_NS, "delegateSystem");
                writer.writeAttribute("systemIdStartString", entry.getStartString());
                writer.writeAttribute("catalog", entry.getCatalog());
                break;
            }
            case "delegateURI": {
                writer.writeEmptyElement(CATALOG_NS, "delegateURI");
                writer.writeAttribute("uriStartString", entry.getStartString());
                writer.writeAttribute("catalog", entry.getCatalog());
                break;
            }
            default:
                throw new MojoExecutionException("Illegal value for generate: " + delegate);
        }
    }

    protected void writeCatalogEntry(XMLStreamWriter writer, final String entry, final RewriteSystemModel rsm) throws XMLStreamException, MojoExecutionException {
        switch (entry) {
            case "rewriteURI": {
                writer.writeEmptyElement(CATALOG_NS, "rewriteURI");
                writer.writeAttribute("uriStartString", rsm.getUriStartPrefix());
                writer.writeAttribute("rewritePrefix", rsm.getRewritePrefix());
                break;
            }
            case "rewriteSystem": {
                writer.writeEmptyElement(CATALOG_NS, "rewriteSystem");
                writer.writeAttribute("systemIdStartString", rsm.getUriStartPrefix());
                writer.writeAttribute("rewritePrefix", rsm.getRewritePrefix());
                break;
            }
            case "public": {
                writer.writeEmptyElement(CATALOG_NS, "public");
                writer.writeAttribute("publicId", rsm.getUriStartPrefix());
                writer.writeAttribute("uri", rsm.getRewritePrefix());
                break;
            }
            case "system": {
                writer.writeEmptyElement(CATALOG_NS, "system");
                writer.writeAttribute("name", rsm.getUriStartPrefix());
                writer.writeAttribute("uri", rsm.getRewritePrefix());
                break;
            }
            case "uri": {
                writer.writeEmptyElement(CATALOG_NS, "uri");
                writer.writeAttribute("name", rsm.getUriStartPrefix());
                writer.writeAttribute("uri", rsm.getRewritePrefix());
                break;
            }
            default:
                throw new MojoExecutionException("Illegal value for generate: " + entry);
        }
    }

    private ArtifactFilter buildArtifactFilter() {
        return new ArtifactFilter() {
            @Override
            public boolean include(Artifact artfct) {
                return true;
            }
        };
    }

    private static final transient String LOG_PREFIX = "[catalog] ";
    private static final transient String CATALOG_NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";

    /**
     * Return true if the dependency or one of its ancestor has a classifier in {@link #ACCEPTABLE_JAR_WITH_DEPENDENCIES_CLASSIFIERS}.
     *
     * @param dn The dependency to check
     * @return true or false...
     */
    private boolean isInJarWithDependencies(DependencyNode dn) {
        getLog().debug(LOG_PREFIX + " looking for parentry of " + dn.getArtifact().toString());
        String classifier = dn.getArtifact().getClassifier();
        getLog().debug(LOG_PREFIX + "classifier=" + classifier);
        if (classifier != null && Arrays.binarySearch(ACCEPTABLE_JAR_WITH_DEPENDENCIES_CLASSIFIERS, classifier) >= 0) {
            getLog().debug(LOG_PREFIX + " return true");
            return true;
        }
        if (dn.getParent() == null) {
            getLog().debug(LOG_PREFIX + "no parent, return false");
            return false;
        }
        return isInJarWithDependencies(dn.getParent());
    }

    private String getJarFileForJarWithDependency(final DependencyNode dn, List<String> classpthElements) throws OverConstrainedVersionException {
        if (dn == null) return null;
        String lastFound = null;
        DependencyNode currentDn = dn;
        while (currentDn != null) {
            String classifier = currentDn.getArtifact().getClassifier();
            if (classifier == null || Arrays.binarySearch(ACCEPTABLE_JAR_WITH_DEPENDENCIES_CLASSIFIERS, classifier) >= 0) {
                String artifactPath = constructArtifactPath(currentDn.getArtifact());
                for (String s : classpthElements) {
                    if (s.contains(artifactPath)) {
                        lastFound = s;
                        break;
                    }
                }
            }
            currentDn = currentDn.getParent();
        }
        return lastFound;
    }

    private final static String[] ACCEPTABLE_JAR_WITH_DEPENDENCIES_CLASSIFIERS = new String[]{
            "jar-with-dependencies",
            "jar-with-dependencies-and-model"
    };

    private String constructArtifactPath(Artifact art) throws OverConstrainedVersionException {
        String groups[] = art.getGroupId().split("\\.");
        getLog().debug(LOG_PREFIX + "groups=" + Arrays.toString(groups));
        String artifacts[] = art.getArtifactId().split("\\.");
        getLog().debug(LOG_PREFIX + "artifacts=" + Arrays.toString(artifacts));
        String[] elements = new String[groups.length + artifacts.length + 1];
        System.arraycopy(groups, 0, elements, 0, groups.length);
        System.arraycopy(artifacts, 0, elements, groups.length, artifacts.length);
        getLog().debug(LOG_PREFIX + "artifact.baseVersion=" + art.getBaseVersion());
        elements[elements.length - 1] = art.getBaseVersion();
        return Arrays.stream(elements)
                .filter(element -> element!=null && !element.isEmpty())
                .collect(Collectors.joining(File.separator));
    }

    /**
     * for UT only
     *
     * @param uriPatterns
     */
    void setUriPatterns(List<String> uriPatterns) {
        this.uriPatterns = uriPatterns;
    }

    private class MyArtifact {
        private final String groupId, artifactId, version;

        public MyArtifact(final String groupId, final String artifactId, final String version) {
            super();
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public String toString() {
            return String.format("artifact[%s,%s,%s]", groupId, artifactId, version);
        }
    }

    public static final transient String[] ALLOWED_URI_PATTERNS = {"compact", "full", "standard"};
    public static final transient String[] ALLOWED_REWRITES = {
            "public", "rewriteSystem", "rewriteURI", "system", "uri"};
}
