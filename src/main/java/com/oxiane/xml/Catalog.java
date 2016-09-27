package com.oxiane.xml;

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
import com.oxiane.xml.model.CatalogModel;
import com.oxiane.xml.model.RewriteSystemModel;
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

/**
 * Goal which touches a timestamp file.
 *
 * @goal touch
 *
 * @phase process-sources
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
    
    @Parameter()
    private String rewriteToProtocol;
    
    @Component( hint = "default" )
    private DependencyGraphBuilder dependencyGraphBuilder;
    
    private DependencyNode rootNode;
    
    @Override
    public void execute() throws MojoExecutionException {
        final List<String> classpaths;
        try {
            classpaths = new ArrayList<>(project.getCompileClasspathElements().size());
            for(Object i:project.getCompileClasspathElements()) {
                classpaths.add(i.toString());
            }
            getLog().debug(LOG_PREFIX+"classpaths="+classpaths);
            if(rewriteToProtocol!=null && rewriteToProtocol.length()>0) {
                if(!rewriteToProtocol.endsWith(":")) rewriteToProtocol+=":";
            }
            try {
                rootNode = dependencyGraphBuilder.buildDependencyGraph( project, buildArtifactFilter() );
                final CatalogModel catalog = new CatalogModel();
                DependencyNodeVisitor visitor = new DependencyNodeVisitor() {
                    @Override
                    public boolean visit(DependencyNode dn) {
                        getLog().debug(LOG_PREFIX+"Visiting "+dn.toNodeString());
                        if(!dn.getArtifact().equals(project.getArtifact())) {
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
        if(rewriteToProtocol!=null && rewriteToProtocol.length()>1) {
            RewriteSystemModel rsm = new RewriteSystemModel(artifactId+":", rewriteToProtocol);
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
                        }
                    }
                }
                getLog().debug(LOG_PREFIX+artifactId+" -> "+jarFileName);
                RewriteSystemModel rsm = new RewriteSystemModel(artifactId+":", "jar:file:"+jarFileName+"!");
                if(!catalog.containsUriStartPrefix(rsm.getUriStartPrefix())) {
                    catalog.getEntries().add(rsm);
                }
            } catch (OverConstrainedVersionException ex) {
                getLog().error(LOG_PREFIX+ex.getMessage(), ex);
            }
        }
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
        // classifier==null || 
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
        getLog().debug(LOG_PREFIX+"artifacs="+Arrays.toString(artifacts));
        String[] elements = new String[groups.length + artifacts.length + 1];
        System.arraycopy(groups, 0, elements, 0, groups.length);
        System.arraycopy(artifacts, 0, elements, groups.length, artifacts.length);
        getLog().debug(LOG_PREFIX+"artifact.baseVersion="+art.getBaseVersion());
        elements[elements.length-1] = art.getBaseVersion();
        return Joiner.on(File.separator).skipNulls().join(elements);
    }
}
