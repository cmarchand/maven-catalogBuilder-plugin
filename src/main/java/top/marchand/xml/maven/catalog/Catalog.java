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
import com.google.common.base.Joiner;
import java.util.HashMap;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;

/**
 * Generates a catalog, based on dependency tree, where each dependency is re-written to the jar URL.
 */
@Mojo(
        name = "catalog", 
        defaultPhase = LifecyclePhase.PROCESS_RESOURCES, 
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class Catalog extends AbstractMojo {
    
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;
    
    @Parameter( defaultValue = "catalog.xml")
    private String catalogFileName;
    
    @Parameter (defaultValue = "artifactId:/")
    private String patternUrl;
    
    @Parameter()
    private String rewriteToProtocol;
    
    @Parameter()
    private boolean includeCurrentArtifact;
    
    @Component( hint = "default" )
    private DependencyGraphBuilder dependencyGraphBuilder;
    
    private DependencyNode rootNode;
    
    private HashMap<File,MyArtifact> dependencyDirs;
    private DocumentBuilder builder;
    private XPathCompiler compiler;
    
    @Override
    public void execute() throws MojoExecutionException {
        dependencyDirs = new HashMap<>();
        Processor proc = new Processor(Configuration.newConfiguration());
        builder = proc.newDocumentBuilder();
        compiler = proc.newXPathCompiler();
        if(rewriteToProtocol!=null && !rewriteToProtocol.endsWith(":/")) {
            rewriteToProtocol=rewriteToProtocol.concat(":/");
        }
        if(!patternUrl.endsWith(":/")) {
            throw new MojoExecutionException("Illegal patternUrl value. patternUrl must end with :/");
        }
        
        final List<String> classpaths;
        try {
            classpaths = new ArrayList<>(project.getCompileClasspathElements().size());
            for(Object i:project.getCompileClasspathElements()) {
                classpaths.add(i.toString());
            }
            getLog().debug(LOG_PREFIX+"classpaths="+classpaths);
            try {
                rootNode = dependencyGraphBuilder.buildDependencyGraph( project, buildArtifactFilter() );
                final CatalogModel catalog = new CatalogModel();
                DependencyNodeVisitor visitor = new DependencyNodeVisitor() {
                    @Override
                    public boolean visit(DependencyNode dn) {
                        getLog().debug(LOG_PREFIX+"Visiting "+dn.toNodeString());
                        if(!dn.getArtifact().equals(project.getArtifact()) || includeCurrentArtifact) {
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
                getLog().debug(LOG_PREFIX+catalog.toString());
            } catch (XMLStreamException | IOException | DependencyGraphBuilderException ex) {
                getLog().error(LOG_PREFIX+ex.getMessage(),ex);
            }
        } catch(DependencyResolutionRequiredException ex) {
            getLog().error(LOG_PREFIX+ex.getMessage(),ex);
        }
    }
    
    private void processDependency(DependencyNode dn, List<String> classpaths, CatalogModel catalog) {
        String artifactId = dn.getArtifact().getArtifactId();
        String groupId = dn.getArtifact().getGroupId();
        String version = dn.getArtifact().getVersion();
        if(rewriteToProtocol!=null && rewriteToProtocol.length()>1) {
            RewriteSystemModel rsm = new RewriteSystemModel(buildPattern(groupId,artifactId,version), rewriteToProtocol);
            if(!catalog.containsUriStartPrefix(rsm.getUriStartPrefix())) {
                catalog.getEntries().add(rsm);
            }
        } else {
            try {
                String jarFileName = null;
                if(isInJarWithDependencies(dn)) {
                    getLog().debug(LOG_PREFIX+artifactId+" is in a jar-with-dependencies");
                    jarFileName = getJarFileForJarWithDependency(dn, classpaths);
                } else {
                    getLog().debug(LOG_PREFIX+artifactId+" is in a jar");
                    String artifactPath = constructArtifactPath(dn.getArtifact());
                    getLog().debug(LOG_PREFIX+"artifactPath= "+artifactPath);
                    for(String s:classpaths) {
                        if(s.contains(artifactPath)) {
                            jarFileName = s;
                        } else if(s.endsWith("target/classes") || s.matches(".*[/\\\\]target[/\\\\][^/\\\\]+\\.jar")) {
                            // issue #2
                            getLog().debug("found classpath : "+s);
                            // dir should be the project basedir
                            File dir = new File(s).getParentFile().getParentFile();
                            if(isPathMatchesDependency(dir,groupId, artifactId, version)) {
                                jarFileName = s;
                            } else {
                                getLog().debug(LOG_PREFIX+s+" does not match ("+dir.getAbsolutePath()+","+groupId+","+artifactId+","+version+")");
                                getLog().debug(LOG_PREFIX+artifactId+":/ won't be bind to a classpath element");
                            }
                        }
                    }
                }
                getLog().debug(LOG_PREFIX+artifactId+" -> "+jarFileName);
                if(jarFileName!=null) {
                    RewriteSystemModel rsm = (jarFileName.endsWith(".jar")) ?
                            new RewriteSystemModel(buildPattern(groupId,artifactId,version), "jar:file:"+jarFileName+"!/") :
                            new RewriteSystemModel(buildPattern(groupId,artifactId,version), new File(jarFileName).toURI().toString());
                    if(!catalog.containsUriStartPrefix(rsm.getUriStartPrefix())) {
                        catalog.getEntries().add(rsm);
                    }
                }
            } catch (OverConstrainedVersionException ex) {
                getLog().error(LOG_PREFIX+ex.getMessage(), ex);
            }
        }
    }
    boolean isPathMatchesDependency(final File dir, final String groupId, final String artifactId, final String version) {
        MyArtifact art = dependencyDirs.get(dir);
        if(art==null) {
            art=loadArtifactFromDir(dir);
            if(art!=null) {
                dependencyDirs.put(dir,art);
                return groupId.equals(art.getGroupId()) && artifactId.equals(art.getArtifactId()) && version.equals(art.getVersion());
            } else {
                return false;
            }
        } else {
            return groupId.equals(art.getGroupId()) && artifactId.equals(art.getArtifactId()) && version.equals(art.getVersion());
        }
    }
    MyArtifact loadArtifactFromDir(final File dir) {
        try {
            XdmNode pom = builder.build(new File(dir,"pom.xml"));
            compiler.declareNamespace("mvn", "http://maven.apache.org/POM/4.0.0");
            XPathSelector selector = compiler.compile("/mvn:project/(mvn:groupId | mvn:artifactId | mvn:version)").load();
            selector.setContextItem(pom);
            XdmValue ret = selector.evaluate();
            String groupId=null, artifactId=null, version=null;
            for(XdmSequenceIterator it=ret.iterator();it.hasNext();) {
                XdmNode node=(XdmNode)it.next();
                switch(node.getNodeName().getLocalName()) {
                    case "groupId": groupId = node.getStringValue(); break;
                    case "artifactId": artifactId = node.getStringValue(); break;
                    case "version": version = node.getStringValue();
                }
            }
            if(version==null || groupId==null) {
                String relativePath = "../pom.xml";
                // case where version is in parent pom. Look for it...
                XPathSelector selector2 = compiler.compile("/mvn:project/mvn:parent/mvn:relativePath").load();
                selector2.setContextItem(pom);
                XdmValue vRelativePath = selector2.evaluate();
                if(vRelativePath.size()>0) {
                    getLog().debug("vRelativePath is a "+vRelativePath.getClass().getName());
                    getLog().debug("vRelativePath is "+vRelativePath.size()+" long");
                    getLog().debug("vRelativePath: "+vRelativePath.toString());
                    relativePath = ((XdmNode)vRelativePath).getStringValue();
                }
                File parentPomFile = new File(dir, relativePath);
                if(parentPomFile.isDirectory()) {
                    parentPomFile = new File(parentPomFile, "pom.xml");
                }
                if(version==null) {
                    XPathSelector versionSelector = compiler.compile("/mvn:project/mvn:version").load();
                    versionSelector.setContextItem(builder.build(parentPomFile));
                    version = ((XdmNode)versionSelector.evaluate()).getStringValue();
                }
                if(groupId==null) {
                    XPathSelector groupIdSelector = compiler.compile("/mvn:project/mvn:groupId").load();
                    groupIdSelector.setContextItem(builder.build(parentPomFile));
                    groupId = ((XdmNode)groupIdSelector.evaluate()).getStringValue();
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
    
    String buildPattern(final String groupId, final String artifactId, final String version) {
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<patternUrl.length(); i++) {
            String s = patternUrl.substring(i);
            if(s.startsWith("groupId")) {
                sb.append(groupId);
                i+=("groupId".length()-1);
            } else if(s.startsWith("artifactId")) {
                sb.append(artifactId);
                i+=("artifactId".length()-1);
            } else if(s.startsWith("version")) {
                sb.append(version);
                i+=("version".length()-1);
            } else {
                sb.append(s.substring(0, 1));
            }
        }
        return sb.toString();
    }
    
    private void writeCatalog(CatalogModel catalog) throws FileNotFoundException, XMLStreamException, IOException {
        XMLOutputFactory fact = XMLOutputFactory.newFactory();
        File catalogFile = new File(project.getBasedir(), catalogFileName);
        File directory = catalogFile.getParentFile();
        if(!directory.exists()) {
            directory.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(catalogFile)) {
            XMLStreamWriter writer = fact.createXMLStreamWriter(fos,"UTF-8");
            writer = new IndentingXMLStreamWriter(writer);
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeDTD("<!DOCTYPE catalog PUBLIC \"-//OASIS//DTD Entity Resolution XML Catalog V1.0//EN\" \"http://www.oasis-open.org/committees/entity/release/1.0/catalog.dtd\">");
            writer.setDefaultNamespace("urn:oasis:names:tc:entity:xmlns:xml:catalog");
            writer.writeStartElement(CATALOG_NS, "catalog");
            writer.writeAttribute("xmlns", CATALOG_NS);
            for(RewriteSystemModel rsm:catalog.getEntries()) {
                writer.writeStartElement(CATALOG_NS, "rewriteURI");
                writer.writeAttribute("uriStartString", rsm.getUriStartPrefix());
                writer.writeAttribute("rewritePrefix", rsm.getRewritePrefix());
                writer.writeEndElement();
                writer.writeStartElement(CATALOG_NS, "rewriteSystem");
                writer.writeAttribute("systemIdStartString", rsm.getUriStartPrefix());
                writer.writeAttribute("rewritePrefix", rsm.getRewritePrefix());
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            fos.flush();
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
     * @param dn The dependency to check
     * @return true or false...
     */
    private boolean isInJarWithDependencies(DependencyNode dn) {
        getLog().debug(LOG_PREFIX+" looking for parentry of "+dn.getArtifact().toString());
        String classifier = dn.getArtifact().getClassifier();
        getLog().debug(LOG_PREFIX+"classifier="+classifier);
        if( classifier!=null && Arrays.binarySearch(ACCEPTABLE_JAR_WITH_DEPENDENCIES_CLASSIFIERS, classifier)>=0) {
            getLog().debug(LOG_PREFIX+" return true");
            return true;
        }
        if(dn.getParent()==null) {
            getLog().debug(LOG_PREFIX+"no parent, return false");
            return false;
        }
        return isInJarWithDependencies(dn.getParent());
    }
    private String getJarFileForJarWithDependency(final DependencyNode dn, List<String> classpthElements) throws OverConstrainedVersionException {
        if(dn==null) return null;
        String lastFound = null;
        DependencyNode currentDn = dn;
        while(currentDn!=null) {
            String classifier = currentDn.getArtifact().getClassifier();
            if(classifier==null || Arrays.binarySearch(ACCEPTABLE_JAR_WITH_DEPENDENCIES_CLASSIFIERS, classifier)>=0) {
                String artifactPath = constructArtifactPath(currentDn.getArtifact());
                for(String s:classpthElements) {
                    if(s.contains(artifactPath)) {
                        lastFound=s;
                        break;
                    }
                }
            }
            currentDn = currentDn.getParent();
        }
        return lastFound;
    }
    
    private final static String[] ACCEPTABLE_JAR_WITH_DEPENDENCIES_CLASSIFIERS = new String[] {
        "jar-with-dependencies",
        "jar-with-dependencies-and-model"
    };
    private String constructArtifactPath(Artifact art) throws OverConstrainedVersionException {
        String groups[] = art.getGroupId().split("\\.");
        getLog().debug(LOG_PREFIX+"groups="+Arrays.toString(groups));
        String artifacts[] = art.getArtifactId().split("\\.");
        getLog().debug(LOG_PREFIX+"artifacts="+Arrays.toString(artifacts));
        String[] elements = new String[groups.length + artifacts.length + 1];
        System.arraycopy(groups, 0, elements, 0, groups.length);
        System.arraycopy(artifacts, 0, elements, groups.length, artifacts.length);
        getLog().debug(LOG_PREFIX+"artifact.baseVersion="+art.getBaseVersion());
        elements[elements.length-1] = art.getBaseVersion();
        return Joiner.on(File.separator).skipNulls().join(elements);
    }
    
    /**
     * for UT only
     * @param patternUrl 
     */
    void setPatternUrl(String patternUrl) {
        this.patternUrl = patternUrl;
    }
    
    private class MyArtifact {
        private final String groupId, artifactId, version;
        public MyArtifact(final String groupId, final String artifactId, final String version) {
            super();
            this.groupId=groupId;
            this.artifactId=artifactId;
            this.version=version;
        }
        public String getGroupId() { return groupId; }
        public String getArtifactId() { return artifactId; }
        public String getVersion() { return version; }
        @Override
        public String toString() {
            return String.format("artifact[%s,%s,%s]", groupId, artifactId, version);
        }
    }

}
