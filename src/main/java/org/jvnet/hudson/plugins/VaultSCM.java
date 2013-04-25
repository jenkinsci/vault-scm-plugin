/**
 * @author Antti Relander / Eficode
 * @verison 0.1
 * @since 2011-12-07
 */
package org.jvnet.hudson.plugins;

import hudson.CopyOnWrite;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.jvnet.hudson.plugins.VaultSCMChangeLogSet.*;

public class VaultSCM extends SCM {

    private static final Logger LOG = Logger.getLogger(VaultSCM.class.getName());

    public static final class VaultSCMDescriptor extends SCMDescriptor<VaultSCM> {

        @CopyOnWrite
        private volatile VaultSCMInstallation[] installations = new VaultSCMInstallation[0];

        /**
         * Constructor for a new VaultSCMDescriptor.
         */
        protected VaultSCMDescriptor() {
            super(VaultSCM.class, null);
            load();
        }

        public VaultSCMInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(VaultSCMInstallation... antInstallations) {
            this.installations = antInstallations;
            save();
        }

        public VaultSCMInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(VaultSCMInstallation.DescriptorImpl.class);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "SourceGear Vault";
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData)
                throws FormException {
            VaultSCM scm = req.bindJSON(VaultSCM.class, formData);
            return scm;
        }
        private final static List<String> MERGE_OPTIONS = Arrays.asList("automatic", "overwrite", "later");

        public List<String> getMergeOptions() {
            return MERGE_OPTIONS;
        }
        private final static List<String> FILETIME_OPTIONS = Arrays.asList("checkin", "current", "modification");

        public List<String> getFileTimeOptions() {
            return FILETIME_OPTIONS;
        }

        public FormValidation doCheckServerName(@QueryParameter String value) throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckVaultExecutable(@QueryParameter String value) throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckRepositoryName(@QueryParameter String value) throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }

        public FormValidation doCheckPath(@QueryParameter String value) throws IOException, ServletException {
            return FormValidation.validateRequired(value);
        }
    }
    
    public static class VaultObjectProperties {
        public String version;
        public String objectID;
        public Date modified;
    }
    

    //configuration variables from user interface
    private String serverName;
    private String userName;
    private Secret password;
    private String repositoryName; //name of the repository
    private String vaultName; // The name of the vault installation from global config
    private String path; //path in repository. Starts with $ sign.
    private Boolean sslEnabled; //ssl enabled?
    private Boolean useNonWorkingFolder;
    private String merge;
    private String fileTime;
    private Boolean makeWritableEnabled;
    private Boolean verboseEnabled;

    public Boolean getMakeWritableEnabled() {
        return makeWritableEnabled;
    }

    public void setMakeWritableEnabled(Boolean makeWritableEnabled) {
        this.makeWritableEnabled = makeWritableEnabled;
    }

    public Boolean getSslEnabled() {
        return sslEnabled;
    }

    public void setSslEnabled(Boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public Boolean getVersboseEnabled() {
        return verboseEnabled;
    }

    public void setverboseEnabled(Boolean verboseEnabled) {
        this.verboseEnabled = verboseEnabled;
    }

    public Boolean getUseNonWorkingFolder() {
        return useNonWorkingFolder;
    }

    public void setNonWorkingFolder(Boolean useNonWorkingFolder) {
        this.useNonWorkingFolder = useNonWorkingFolder;
    }

    public String getMerge() {
        return merge;
    }

    public void setMerge(String merge) {
        this.merge = merge;
    }

    public String getFileTime() {
        return fileTime;
    }

    public void setFileTime(String fileTime) {
        this.fileTime = fileTime;
    }

    public String getVaultName() {
        return vaultName;
    }

    public void setVaultName(String vaultName) {
        this.vaultName = vaultName;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return Secret.toString(password);
    }

    public void setPassword(String password) {
        this.password = Secret.fromString(password);
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public VaultSCMInstallation getVault() {
        for (VaultSCMInstallation i : DESCRIPTOR.getInstallations()) {
            if (vaultName != null && i.getName().equals(vaultName)) {
                return i;
            }
        }
        return null;
    }
    /**
     * Singleton descriptor.
     */
    @Extension
    public static final VaultSCMDescriptor DESCRIPTOR = new VaultSCMDescriptor();
    //format dates for vault client
    public static final SimpleDateFormat VAULT_DATETIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    public static final String VAULT_FOLDER_VERSION_NAME = "VAULT_FOLDER_VERSION";

    @DataBoundConstructor
    public VaultSCM(String serverName, String path, String userName,
            String password, String repositoryName, String vaultName,
            Boolean sslEnabled, Boolean useNonWorkingFolder, String merge,
            String fileTime, Boolean makeWritableEnabled,
            Boolean verboseEnabled) {
        this.serverName = serverName;
        this.userName = userName;
        this.password = Secret.fromString(password);
        this.repositoryName = repositoryName;
        this.vaultName = vaultName;
        this.path = path;
        this.sslEnabled = sslEnabled; //Default to true
        this.useNonWorkingFolder = useNonWorkingFolder;
        this.merge = (merge.isEmpty() || merge == null) ? "overwrite" : merge;
        this.fileTime = (fileTime.isEmpty() || fileTime == null) ? "modification" : fileTime;
        this.makeWritableEnabled = makeWritableEnabled;
        this.verboseEnabled = verboseEnabled;

    }

    @Override
    public SCMDescriptor<?> getDescriptor() {
        return DESCRIPTOR;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> build,
            Launcher launcher, TaskListener listener) throws IOException,
            InterruptedException {

        VaultSCMRevisionState scmRevisionState = new VaultSCMRevisionState();
        // old date-based method
        final Date lastBuildDate = build.getTime();
        scmRevisionState.setModified(lastBuildDate);

        // current version-based method
        hudson.EnvVars environment = build.getEnvironment(listener);
        String folderVersion = environment.get(VAULT_FOLDER_VERSION_NAME);
        scmRevisionState.setVersion(folderVersion);
        
        listener.getLogger().println("calc revisions from build, folder version: " + folderVersion);

        return scmRevisionState;
    }

    @Override
    /* 
     */
    protected PollingResult compareRemoteRevisionWith(
            AbstractProject<?, ?> project, Launcher launcher,
            FilePath workspace, TaskListener listener, SCMRevisionState baseline)
            throws IOException, InterruptedException {

        // first try the version-based method
        String oldVersion = ((VaultSCMRevisionState) baseline).getVersion();
        if (oldVersion != null && !oldVersion.equals("")) {
            listener.getLogger().println("Old folder version: " + oldVersion);
            VaultObjectProperties objectProperties = getCurrentFolderProperties(launcher, workspace, listener);
            listener.getLogger().println("Current folder version: " + objectProperties.version);
            if (objectProperties.version != oldVersion) {
                return PollingResult.BUILD_NOW;
            }
            return PollingResult.NO_CHANGES;
        } else {
            // then the date-based method
            Date lastBuildDate = ((VaultSCMRevisionState) baseline).getModified();
            if (lastBuildDate == null) {
                AbstractBuild<?,?> lastBuild = project.getLastCompletedBuild();
                lastBuildDate = lastBuild != null ? lastBuild.getTime() : new Date(2000, 1, 1);
            }
            LOG.log(Level.INFO, "Last Build Date set to {0}", lastBuildDate.toString());
            Date now = new Date();
            File temporaryFile = File.createTempFile("changes", ".txt");
            int countChanges = determineChangeCount(launcher, workspace, listener, lastBuildDate, now, temporaryFile);
            temporaryFile.delete();
            if (countChanges == 0) {
                return PollingResult.NO_CHANGES;
            } else {
                return PollingResult.BUILD_NOW;
            }
        }
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env){
        super.buildEnvVars(build, env);

        // with the current implementation, the environment variable should already be available
        String existingFolderVersion = env.get(VAULT_FOLDER_VERSION_NAME);
        if (existingFolderVersion != null && !existingFolderVersion.equals("") )
            return;
        
        if (build.getChangeSet() == null || build.getChangeSet().isEmptySet()) {
            AbstractBuild<?, ?> previousBuild = build.getPreviousBuild();
            if (previousBuild != null) {
                buildEnvVars(previousBuild, env);
            } else {
                env.put(VAULT_FOLDER_VERSION_NAME, "NOT_SET");
            }
                
            return;
        }
        
        @SuppressWarnings("unchecked")
        ChangeLogSet<VaultSCMChangeLogSetEntry> cls = (ChangeLogSet<VaultSCMChangeLogSetEntry>)build.getChangeSet();
        Iterator<VaultSCMChangeLogSetEntry> it = cls.iterator();
        if (it.hasNext()) {
            VaultSCMChangeLogSetEntry entry = it.next();
            env.put(VAULT_FOLDER_VERSION_NAME, entry.getVersion());
        } 

    }

    private boolean checkVaultPath(String path, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
        FilePath exec = new FilePath(launcher.getChannel(), path);
        try {
            if (!exec.exists()) {
                return false;
            }
        } catch (IOException e) {
            listener.fatalError("Failed checking for existence of " + path);
            return false;
        }
        return true;
    }

    private String getVaultPath(Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        final String defaultPath = "C:\\Program Files\\SourceGear\\Vault Client\\vault.exe";
        final String defaultPathX86 = "C:\\Program Files (x86)\\SourceGear\\Vault Client\\vault.exe";

        VaultSCMInstallation installation = getVault();
        String pathToVault;

        if (installation == null) {
            // Check the first default location for vault...
            if (checkVaultPath(defaultPath, launcher, listener)) {
                pathToVault = defaultPath;
            } else if (checkVaultPath(defaultPathX86, launcher, listener)) {
                pathToVault = defaultPathX86;
            } else {
                listener.fatalError("Failed find vault client");
                return null;
            }
        } else {
            installation = installation.forNode(Computer.currentComputer().getNode(), listener);
            pathToVault = installation.getVaultLocation();
            if (!checkVaultPath(pathToVault, launcher, listener)) {
                listener.fatalError(pathToVault + " doesn't exist");
                return null;
            }
        }
        return pathToVault;
    }

    @Override
    public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher,
            FilePath workspace, BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {

        // first get current version, then use GETVERSION so that the folder version property retrieved from vault and the files on disk are perfectly in sync
        VaultObjectProperties objectProperties = getCurrentFolderProperties(launcher, workspace, listener);
        String folderVersion = objectProperties.version;

        boolean returnValue;

        String pathToVault = getVaultPath(launcher, listener);

        if (pathToVault == null) {
            return false;
        }

        if (serverName != null) {
            listener.getLogger().println("server: " + serverName);
        }
        //populate the GETVERSION command
        //in some cases username, host and password can be empty e.g. if rememberlogin is used to store login data
        ArgumentListBuilder argBuildr = new ArgumentListBuilder();

        argBuildr.add(pathToVault);
        argBuildr.add("GETVERSION");

        if (!serverName.isEmpty()) {
            argBuildr.add("-host", serverName);
        }

        if (!userName.isEmpty()) {
            argBuildr.add("-user", userName);
        }

        if (!Secret.toString(password)
                .isEmpty()) {
            argBuildr.add("-password");
            argBuildr.add(Secret.toString(password), true);
        }

        if (!repositoryName.isEmpty()) {
            argBuildr.add("-repository", repositoryName);
        }

        if (this.sslEnabled) {
            argBuildr.add("-ssl");
        }

        if (this.verboseEnabled) {
            argBuildr.add("-verbose");
        }

        if (this.makeWritableEnabled) {
            argBuildr.add("-makewritable");
        }

        argBuildr.add(
                "-merge", merge);
        argBuildr.add(
                "-setfiletime", fileTime);
        if (!this.useNonWorkingFolder) {
            argBuildr.add(
                    "-useworkingfolder");
        }
        argBuildr.add(
                folderVersion);
        argBuildr.add(
                this.path);
        argBuildr.add(
                workspace.getRemote());

        int cmdResult = launcher.launch().cmds(argBuildr).envs(build.getEnvironment(TaskListener.NULL)).stdout(listener.getLogger()).pwd(workspace).join();
        if (cmdResult
                == 0) {
            final Run<?, ?> lastBuild = build.getPreviousBuild();
            final Date lastBuildDate;

            if (lastBuild == null) {
                lastBuildDate = new Date();
                lastBuildDate.setTime(0); // default to January 1, 1970
                listener.getLogger().print("Never been built.");
            } else {
                lastBuildDate = lastBuild.getTimestamp().getTime();
            }

            Date now = new Date(); //defaults to current

            returnValue = captureChangeLog(launcher, workspace, listener, lastBuildDate, now, changelogFile);
            
            // already set VAULT_FOLDER_VERSION in environment
            hudson.EnvVars environment = build.getEnvironment(TaskListener.NULL);
            environment.put(VAULT_FOLDER_VERSION_NAME, folderVersion);
            listener.getLogger().println("Current folder version is " + folderVersion);
            
        } else {
            returnValue = false;
        }

        listener.getLogger()
                .println("Checkout completed.");
        return returnValue;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new VaultSCMChangeLogParser();
    }

    private boolean captureChangeLog(Launcher launcher, FilePath workspace,
            BuildListener listener, Date lastBuildDate, Date currentDate, File changelogFile) throws IOException, InterruptedException {

        boolean result = true;

        String latestBuildDate = VAULT_DATETIME_FORMATTER.format(lastBuildDate);

        String today = (VAULT_DATETIME_FORMATTER.format(currentDate));

        String pathToVault = getVaultPath(launcher, listener);

        if (pathToVault == null) {
            return false;
        }

        FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            BufferedOutputStream bos = new BufferedOutputStream(os);
            PrintWriter writer = new PrintWriter(new FileWriter(changelogFile));
            try {

                ArgumentListBuilder argBuildr = new ArgumentListBuilder();
                argBuildr.add(pathToVault);
                argBuildr.add("VERSIONHISTORY");

                if (!serverName.isEmpty()) {
                    argBuildr.add("-host", serverName);
                }

                if (!userName.isEmpty()) {
                    argBuildr.add("-user", userName);
                }

                if (!Secret.toString(password).isEmpty()) {
                    argBuildr.add("-password");
                    argBuildr.add(Secret.toString(password), true);
                }

                if (!repositoryName.isEmpty()) {
                    argBuildr.add("-repository", repositoryName);
                }

                if (this.sslEnabled) {
                    argBuildr.add("-ssl");
                }

                argBuildr.add("-enddate", today);
                argBuildr.add("-begindate", latestBuildDate);
                argBuildr.add(this.path);

                int cmdResult = launcher.launch().cmds(argBuildr).envs(new String[0]).stdout(bos).pwd(workspace).join();
                if (cmdResult != 0) {
                    listener.fatalError("Changelog failed with exit code " + cmdResult);
                    result = false;
                }


            } finally {
                writer.close();
                bos.close();
            }
        } finally {
            os.close();
        }

        listener.getLogger().println("Changelog calculated successfully.");
        listener.getLogger().println("Change log file: " + changelogFile.getAbsolutePath());

        return result;
    }

    private VaultObjectProperties getCurrentFolderProperties(Launcher launcher, FilePath workspace, TaskListener listener)
            throws IOException, InterruptedException {
        VaultObjectProperties result = new VaultObjectProperties();

        String pathToVault = getVaultPath(launcher, listener);

        if (pathToVault == null) {
            return result;
        }

        File tempFile = File.createTempFile("objectproperties", "xml");
        FileOutputStream os = new FileOutputStream(tempFile);
        try {
            BufferedOutputStream bos = new BufferedOutputStream(os);
            PrintWriter writer = new PrintWriter(new FileWriter(tempFile));
            try {

                ArgumentListBuilder argBuildr = new ArgumentListBuilder();
                argBuildr.add(pathToVault);
                argBuildr.add("LISTOBJECTPROPERTIES");

                if (!serverName.isEmpty()) {
                    argBuildr.add("-host", serverName);
                }

                if (!userName.isEmpty()) {
                    argBuildr.add("-user", userName);
                }

                if (!Secret.toString(password).isEmpty()) {
                    argBuildr.add("-password");
                    argBuildr.add(Secret.toString(password), true);
                }

                if (!repositoryName.isEmpty()) {
                    argBuildr.add("-repository", repositoryName);
                }

                if (this.sslEnabled) {
                    argBuildr.add("-ssl");
                }

                argBuildr.add(this.path);

                int cmdResult = launcher.launch().cmds(argBuildr).envs(new String[0]).stdout(bos).pwd(workspace).join();
                if (cmdResult != 0) {
                    listener.fatalError("List object properties failed with exit code " + cmdResult);
                }

            } finally {
                writer.close();
                bos.close();
            }
        } finally {
            os.close();
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(tempFile);
            doc.getDocumentElement().normalize();
            NodeList nodeLst = doc.getElementsByTagName("version");
            if (nodeLst.getLength() == 1) {
                result.version = nodeLst.item(0).getTextContent();
            }
            nodeLst = doc.getElementsByTagName("objectid");
            if (nodeLst.getLength() == 1) {
                result.objectID = nodeLst.item(0).getTextContent();
            }
            nodeLst = doc.getElementsByTagName("modifieddate");
            if (nodeLst.getLength() == 1) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat();
                result.modified = simpleDateFormat.parse(nodeLst.item(0).getTextContent());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        tempFile.delete();
        return result;
    }
    
    private int determineChangeCount(Launcher launcher, FilePath workspace,
            TaskListener listener, Date lastBuildDate, Date currentDate, File changelogFile) throws IOException, InterruptedException {

        int result = 0;

        String latestBuildDate = VAULT_DATETIME_FORMATTER.format(lastBuildDate);

        String today = (VAULT_DATETIME_FORMATTER.format(currentDate));

        String pathToVault = getVaultPath(launcher, listener);


        if (pathToVault == null) {
            return 0;
        }

        FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            BufferedOutputStream bos = new BufferedOutputStream(os);
            PrintWriter writer = new PrintWriter(new FileWriter(changelogFile));
            try {

                ArgumentListBuilder argBuildr = new ArgumentListBuilder();
                argBuildr.add(pathToVault);
                argBuildr.add("VERSIONHISTORY");

                if (!serverName.isEmpty()) {
                    argBuildr.add("-host", serverName);
                }

                if (!userName.isEmpty()) {
                    argBuildr.add("-user", userName);
                }

                if (!Secret.toString(password).isEmpty()) {
                    argBuildr.add("-password");
                    argBuildr.add(Secret.toString(password), true);
                }

                if (!repositoryName.isEmpty()) {
                    argBuildr.add("-repository", repositoryName);
                }

                if (this.sslEnabled) {
                    argBuildr.add("-ssl");
                }

                argBuildr.add("-enddate", today);
                argBuildr.add("-begindate", latestBuildDate);
                argBuildr.add(this.path);

                int cmdResult = launcher.launch().cmds(argBuildr).envs(new String[0]).stdout(bos).pwd(workspace).join();
                if (cmdResult != 0) {
                    listener.fatalError("Determine changes count failed with exit code " + cmdResult);
                    result = 0;
                }


            } finally {
                writer.close();
                bos.close();
            }
        } finally {
            os.close();
        }
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(changelogFile);
            doc.getDocumentElement().normalize();
            NodeList nodeLst = doc.getElementsByTagName("item");
            result = nodeLst.getLength();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }
}
