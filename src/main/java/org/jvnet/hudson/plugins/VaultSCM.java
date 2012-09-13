/**
 * @author Antti Relander / Eficode
 * @verison 0.1
 * @since 2011-12-07
 */
package org.jvnet.hudson.plugins;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
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

public class VaultSCM extends SCM {

    private static final Logger LOG = Logger.getLogger(VaultSCM.class.getName());

    public static class VaultSCMDescriptor extends SCMDescriptor<VaultSCM> {

        /**
         * Constructor for a new VaultSCMDescriptor.
         */
        protected VaultSCMDescriptor() {
            super(VaultSCM.class, null);
            load();
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
    //configuration variables from user interface
    private String serverName;
    private String userName;
    private Secret password;
    private String repositoryName; //name of the repository
    private String vaultExecutable;
    private String path; //path in repository. Starts with $ sign.
    private Boolean sslEnabled; //ssl enabled?
    private String merge;
    private Boolean makeWritableEnabled;

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

    public String getMerge() {
        return merge;
    }

    public void setMerge(String merge) {
        this.merge = merge;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
    //getters and setters

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

    public String getVaultExecutable() {
        return this.vaultExecutable;
    }

    public void setVaultSCMExecutable(String vaultExecutable) {
        this.vaultExecutable = vaultExecutable;
    }
    /**
     * Singleton descriptor.
     */
    @Extension
    public static final VaultSCMDescriptor DESCRIPTOR = new VaultSCMDescriptor();
    //format dates for vault client
    public static final SimpleDateFormat VAULT_DATETIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    @DataBoundConstructor
    public VaultSCM(String serverName, String path, String userName,
            String password, String repositoryName, String vaultExecutable, Boolean sslEnabled, String merge, Boolean makeWritableEnabled) {
        this.serverName = serverName;
        this.userName = userName;
        this.password = Secret.fromString(password);
        this.repositoryName = repositoryName;
        this.vaultExecutable = vaultExecutable;
        this.path = path;
        this.sslEnabled = sslEnabled; //Default to true
        this.merge = merge.isEmpty() ? "overwrite" : merge;
        this.makeWritableEnabled = makeWritableEnabled;

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
        final Date lastBuildDate = build.getTime();
        scmRevisionState.setDate(lastBuildDate);

        return scmRevisionState;
    }

    @Override
    /* 
     */
    protected PollingResult compareRemoteRevisionWith(
            AbstractProject<?, ?> project, Launcher launcher,
            FilePath workspace, TaskListener listener, SCMRevisionState baseline)
            throws IOException, InterruptedException {

        Date lastBuild = ((VaultSCMRevisionState) baseline).getDate();
        LOG.log(Level.INFO, "Last Build Date set to {0}", lastBuild.toString());
        Date now = new Date();
        File temporaryFile = File.createTempFile("changes", "txt");
        int countChanges = determineChangeCount(launcher, workspace, listener, lastBuild, now, temporaryFile);

        if (countChanges == 0) {
            return PollingResult.NO_CHANGES;
        } else {
            return PollingResult.BUILD_NOW;
        }
    }

    @Override
    public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher,
            FilePath workspace, BuildListener listener, File changelogFile)
            throws IOException, InterruptedException {
        boolean returnValue = true;

        if (serverName != null) {
            listener.getLogger().println("server: " + serverName);
        }

        //populate the GET command
        //in some cases username, host and password can be empty e.g. if rememberlogin is used to store login data
        ArgumentListBuilder argBuildr = new ArgumentListBuilder();
        argBuildr.add(getVaultExecutable());
        argBuildr.add("GET");

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

        if (this.makeWritableEnabled) {
            argBuildr.add("-makewritable");
        }


        argBuildr.add("-merge", merge);
        argBuildr.add("-workingfolder", workspace.getRemote());
        argBuildr.add(this.path);

        int cmdResult = launcher.launch().cmds(argBuildr).envs(build.getEnvironment(TaskListener.NULL)).stdout(listener.getLogger()).pwd(workspace).join();
        if (cmdResult == 0) {
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
        } else {
            returnValue = false;
        }

        listener.getLogger().println("Checkout completed.");
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

        FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            BufferedOutputStream bos = new BufferedOutputStream(os);
            PrintWriter writer = new PrintWriter(new FileWriter(changelogFile));
            try {

                ArgumentListBuilder argBuildr = new ArgumentListBuilder();
                argBuildr.add(getVaultExecutable());
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

    private int determineChangeCount(Launcher launcher, FilePath workspace,
            TaskListener listener, Date lastBuildDate, Date currentDate, File changelogFile) throws IOException, InterruptedException {

        int result = 0;

        String latestBuildDate = VAULT_DATETIME_FORMATTER.format(lastBuildDate);

        String today = (VAULT_DATETIME_FORMATTER.format(currentDate));

        FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            BufferedOutputStream bos = new BufferedOutputStream(os);
            PrintWriter writer = new PrintWriter(new FileWriter(changelogFile));
            try {

                ArgumentListBuilder argBuildr = new ArgumentListBuilder();
                argBuildr.add(getVaultExecutable());
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
    
    private final static String[] MERGE_OPTION_STRINGS = new String[]{
        "automatic",
        "overwrite",
        "later",};
}
