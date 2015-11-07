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
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javanet.staxutils.IndentingXMLStreamWriter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

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
    
    @Parameter( defaultValue = "${project.compileClasspathElements}", readonly = true, required = true )
    private List<String> classpathElements;
    
    @Parameter( defaultValue = "catalog.xml")
    private String catalogFileName;
    
    @Parameter( defaultValue = "jar")
    private String archiveExtensions;
    
    private String[] extensions = null;


    @Override
    public void execute() throws MojoExecutionException {
        CatalogModel catalog = new CatalogModel();
        for(String s: classpathElements) {
            getLog().debug(LOG_PREFIX+s);
            if(isArchive(s)) {
                try {
                    processJarFile(s, catalog);
                } catch (IOException ex) {
                    getLog().error(ex.getMessage());
                    if(getLog().isWarnEnabled()) {
                        getLog().warn(ex);
                    }
                }
            }
        }
        try {
            writeCatalog(catalog);
        } catch (XMLStreamException | IOException ex) {
            getLog().warn(ex.getMessage(),ex);
        }
        getLog().debug(catalog.toString());
    }
    
    private void processJarFile(String jarFileName, CatalogModel catalog) throws IOException {
        File jarFile = new File(jarFileName);
        if(!jarFile.exists()) {
            getLog().warn(LOG_PREFIX+jarFileName+" does not exists");
            return;
        }
        try (ZipFile zipFile = new ZipFile(jarFile)) {
            for(Enumeration<? extends ZipEntry> enumer=zipFile.entries();enumer.hasMoreElements();) {
                ZipEntry ze = enumer.nextElement();
                if(ze.getName().endsWith("pom.xml")) {
                    String middle = ze.getName().substring(15, ze.getName().length()-8);
                    int pos = middle.indexOf("/");
                    String groupId = middle.substring(0, pos);
                    String artifactId = middle.substring(pos+1);
                    getLog().debug(LOG_PREFIX+"groupId="+groupId+" artifactId="+artifactId);
                    RewriteSystemModel rsm = new RewriteSystemModel(artifactId+":", "jar:file:"+jarFileName+"!");
                    catalog.getEntries().add(rsm);
                    break;
                }
            }
        }
    }
    private void writeCatalog(CatalogModel catalog) throws FileNotFoundException, XMLStreamException, IOException {
        XMLOutputFactory fact = XMLOutputFactory.newFactory();
        File catalogFile = new File(catalogFileName);
        try (FileOutputStream fos = new FileOutputStream(catalogFile)) {
            XMLStreamWriter writer = fact.createXMLStreamWriter(fos);
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
            }
            writer.writeEndElement();
            writer.writeEndDocument();
            fos.flush();
        }
    }
    private boolean isArchive(String archiveName) {
        if(extensions==null) {
            extensions = archiveExtensions.toUpperCase().split(",");
        }
        String an = archiveName.toUpperCase();
        boolean isArchive = false;
        int count=0;
        while(!isArchive && count<extensions.length) {
            isArchive = an.endsWith(extensions[count]);
            count++;
        }
        return isArchive;
    }
    private static final transient String LOG_PREFIX = "[catalog] ";
    private static final transient String CATALOG_NS = "urn:oasis:names:tc:entity:xmlns:xml:catalog";
}
