/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package top.marchand.xml.maven.catalog.model;

import java.io.Serializable;

/**
 *
 * @author cmarchand
 */
public class RewriteSystemModel implements Serializable {
    private String uriStartPrefix;
    private String rewritePrefix;
    private String groupId, artifactId, version;
    
    public RewriteSystemModel() {
        super();
    }
    
    public RewriteSystemModel(String uriStartPrefix, String rewritePrefix, String groupId, String artifactId, String version) {
        this();
        this.uriStartPrefix=uriStartPrefix;
        this.rewritePrefix=rewritePrefix;
        this.groupId=groupId;
        this.artifactId=artifactId;
        this.version=version;
    }

    public String getUriStartPrefix() {
        return uriStartPrefix;
    }

    public void setUriStartPrefix(String uriStartPrefix) {
        this.uriStartPrefix = uriStartPrefix;
    }

    public String getRewritePrefix() {
        return rewritePrefix;
    }

    public void setRewritePrefix(String rewritePrefix) {
        this.rewritePrefix = rewritePrefix;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
    
    

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (this.uriStartPrefix != null ? this.uriStartPrefix.hashCode() : 0);
        hash = 47 * hash + (this.rewritePrefix != null ? this.rewritePrefix.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final RewriteSystemModel other = (RewriteSystemModel) obj;
        if ((this.uriStartPrefix == null) ? (other.uriStartPrefix != null) : !this.uriStartPrefix.equals(other.uriStartPrefix)) {
            return false;
        }
        if ((this.rewritePrefix == null) ? (other.rewritePrefix != null) : !this.rewritePrefix.equals(other.rewritePrefix)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "\tRewriteSystemModel{" + "uriStartPrefix=" + uriStartPrefix + ", rewritePrefix=" + rewritePrefix + "}\n";
    }
    
    
}
