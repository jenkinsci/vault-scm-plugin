/**
 * @author Antti Relander / Eficode
 * @verison 0.1
 * @since 2011-12-07
 */
package org.jvnet.hudson.plugins;

import hudson.scm.SCMRevisionState;
import java.util.Date;
import org.jvnet.hudson.plugins.VaultSCM.VaultObjectProperties;

public class VaultSCMRevisionState extends SCMRevisionState {

    public VaultObjectProperties objectProperties;
    // keep old member for the moment, in order to support persisted states in builds
    public Date buildDate;
    public String folderVersion;
    public String revisions;

    public VaultSCMRevisionState(VaultObjectProperties objectProperties) {
        this.objectProperties = objectProperties;
    }

    public VaultSCMRevisionState() {
        objectProperties = new VaultObjectProperties();
    }

    public void setModified(Date date) {
        if (objectProperties == null)
            return;
        objectProperties.modified = date;
    }

    public Date getModified() {
        if (objectProperties == null)
            return buildDate;
        return objectProperties.modified;
    }
    
    public void setVersion(String version) {
        if (objectProperties == null)
            return;
        objectProperties.version = version;
    }
    
    public String getVersion() {
        if (objectProperties == null)
            return "";
        return objectProperties.version;
    }
}
