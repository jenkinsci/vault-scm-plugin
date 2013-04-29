/**
 * @author Antti Relander / Eficode
 * @verison 0.1
 * @since 2011-12-07
 */
package org.jvnet.hudson.plugins;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import org.jvnet.hudson.plugins.VaultSCMChangeLogSet.VaultSCMChangeLogSetEntry;
import org.kohsuke.stapler.export.Exported;

public final class VaultSCMChangeLogSet extends ChangeLogSet<VaultSCMChangeLogSetEntry> {
    
    protected VaultSCMChangeLogSet(AbstractBuild<?, ?> build) {
        super(build);
        changes = new ArrayList<VaultSCMChangeLogSetEntry>();
    }

    public Iterator<VaultSCMChangeLogSetEntry> iterator() {
        return changes.iterator();
    }

    @Override
    public boolean isEmptySet() {
        return changes.isEmpty();
    }

    public boolean addEntry(VaultSCMChangeLogSetEntry e) {
        return changes.add(e);
    }
    private Collection<VaultSCMChangeLogSetEntry> changes;

    public static class VaultSCMChangeLogSetEntry extends ChangeLogSet.Entry {

        @SuppressWarnings("rawtypes")
        public VaultSCMChangeLogSetEntry(String comment, String version, String date, ChangeLogSet parent, String userName, String transactionId) {
            this.affectedFile = "User defined path";
            this.comment = comment;
            this.version = version;
            this.transactionId = transactionId;
            this.date = date;
            this.user = User.get(userName);
            this.action = "edit";
            setParent(parent);
        }

        public VaultSCMChangeLogSetEntry() {
        }

        @Override
        public String getMsg() {
            return comment;
        }

        @Override
        public Collection<String> getAffectedPaths() {
            Collection<String> col = new ArrayList<String>();
            col.add("user defined path");
            return col;
        }

        @Override
        public User getAuthor() {
            if (user == null) {
                return User.getUnknown();
            }
            return user;
        }

        public String getVersion() {
            return version;
        }

        public String getComment() {
            return comment;
        }
        
        public String getTxId() {
            return transactionId;
        }

        public String getUrl() {
            AbstractProject<?,?> project = (AbstractProject<?,?>)getParent().build.getParent();
            VaultSCM scm = (VaultSCM)project.getScm();
            return scm.getServerName();
        }
        
        @Exported
        public EditType getEditType() {
            if (action.equalsIgnoreCase("delete")) {
                return EditType.DELETE;
            }
            if (action.equalsIgnoreCase("add")) {
                return EditType.ADD;
            }
            return EditType.EDIT;
        }

        @Exported
        String getPath() {
            return affectedFile;
        }
        String comment;
        String affectedFile;
        String version;
        String transactionId;
        String date;
        User user;

        String action; //default is edit	
    }
}
