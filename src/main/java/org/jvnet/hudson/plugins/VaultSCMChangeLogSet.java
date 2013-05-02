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
import java.util.List;

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
        public VaultSCMChangeLogSetEntry(ChangeLogSet parent) {
            affectedFiles = new ArrayList<VaultAffectedFile>();
            setParent(parent);
        }

        @SuppressWarnings("rawtypes")
        public VaultSCMChangeLogSetEntry(String comment, String version, String date, ChangeLogSet parent, String userName, String transactionId, List<VaultAffectedFile> affectedFiles) {
            this.affectedFiles = new ArrayList<VaultAffectedFile>(affectedFiles);
            this.comment = comment;
            this.version = version;
            this.transactionId = transactionId;
            this.date = date;
            this.user = User.get(userName);
            setParent(parent);
        }

        public void addAffectedFile(VaultAffectedFile file) {
            affectedFiles.add(file);
        }
        
        @Override
        public Collection<VaultAffectedFile> getAffectedFiles() {
            return affectedFiles;
        }

        @Exported
        public List<VaultAffectedFile> getItems() {
            return affectedFiles;
        }
        
        @Override
        public String getMsg() {
            return comment;
        }

        @Override
        public Collection<String> getAffectedPaths() {
            return null;
        }

        @Override
        public User getAuthor() {
            if (user == null) {
                return User.getUnknown();
            }
            return user;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public User getUser() {
            return user;
        }

        public void setUser(User user) {
            this.user = user;
        }

        public void setComment(String comment) {
            this.comment = comment;
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
        
        public String getPath() {
            AbstractProject<?,?> project = (AbstractProject<?,?>)getParent().build.getParent();
            VaultSCM scm = (VaultSCM)project.getScm();
            return scm.getPath();
        }
        
        public String getRepId() {
            AbstractProject<?,?> project = (AbstractProject<?,?>)getParent().build.getParent();
            VaultSCM scm = (VaultSCM)project.getScm();
            return scm.getRepositoryId();
        }
        
        String comment;
        String version;
        String transactionId;
        String date;
        User user;

        List<VaultAffectedFile> affectedFiles;
        
        public static class VaultAffectedFile implements ChangeLogSet.AffectedFile {
            String version;
            String path;
            EditType editType;
            
            public VaultAffectedFile(String path, EditType editType, String version) {
                super();
                this.path = path;
                this.editType = editType;
                this.version = version;
            }
    
            
            public String getPath() {
                return path;
            }
    
            public EditType getEditType() {
                return editType;
            }
            
            public String getVersion() {
                return version;
            }
            
        }
    }

}
