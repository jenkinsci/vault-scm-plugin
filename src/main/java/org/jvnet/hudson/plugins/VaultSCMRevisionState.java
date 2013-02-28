/**
 * @author Antti Relander / Eficode
 * @verison 0.1
 * @since 2011-12-07
 */
package org.jvnet.hudson.plugins;

import hudson.scm.SCMRevisionState;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class VaultSCMRevisionState extends SCMRevisionState {

    public Map<String, Long> revisions;

    public VaultSCMRevisionState(Map<String, Long> revisions) {

        this.revisions = revisions;
    }

    public VaultSCMRevisionState() {

        revisions = new HashMap<String, Long>();
        buildDate = new Date();
    }

    public void AddRevision(String key, long value) {
        if (revisions == null) {
            revisions = new HashMap<String, Long>();
        }
        revisions.put(key, value);
    }

    public void setRevisions(Map<String, Long> revisions) {
        this.revisions = revisions;
    }

    public void setDate(Date date) {
        buildDate = date;
    }

    public Date getDate() {
        return buildDate;
    }
    public Date buildDate;
}
