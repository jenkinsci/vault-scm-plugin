/**
 * @author Antti Relander / Eficode
 * @verison 0.1
 * @since 2011-12-07
 */

package org.jvnet.hudson.plugins;

import org.jvnet.hudson.plugins.VaultSCMRevisionState;
import org.jvnet.hudson.plugins.VaultSCMChangeLogParser;
import org.jvnet.hudson.plugins.VaultSCMChangeLogSet;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.util.ArgumentListBuilder;

//XML parsing
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import sun.nio.cs.ext.ISCII91;

public class VaultSCM extends SCM {
	
	public static class VaultSCMDescriptor extends
	SCMDescriptor<VaultSCM> {
	
	
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
			return "Vault SCM";
		}
		
		@Override
		public SCM newInstance(StaplerRequest req, JSONObject formData)
				throws FormException {
			VaultSCM scm = req.bindJSON(VaultSCM.class, formData);
			return scm;
		}

	}
	
	//configuration variables from user interface

	private String server;	
	private String userName;
	private String password;
	private String repository; //name of the repository
	private String vaultSCMExecutable;
	private String path; //path in repository. Starts with $ sign.
	private Boolean ssl; //ssl enabled?
	private String merge = "overwrite"; //merge options: automatic, overwrite, later
        private Boolean makeWriteable;
	
	public Boolean getMakeWriteable() {
            return makeWriteable;
        }
        public void setMakeWriteable( Boolean makeWriteable ) {
            this.makeWriteable = makeWriteable;
        }
	public Boolean getSsl() {
		return ssl;
	}
	public void setSsl(Boolean ssl) {
		this.ssl = ssl;
	}
	public String getMerge() {
            if ( merge == null || merge.isEmpty() )
            {
                this.merge = "overwrite";
            }
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
	public String getServer() {
		return server;
	}
	public void setServer(String server) {
		this.server = server;
	}

	public String getUserName() {
		return userName;
	}
	public void setUserName(String userName) {
		this.userName = userName;
	}
	public String getPassword() {
		return password;
	}
	public void setPassword(String password) {
		this.password = password;
	}
	public String getRepository() {
		return repository;
	}
	public void setRepository(String repository) {
		this.repository = repository;
	}
	public String getVaultSCMExecutable() {
		return this.vaultSCMExecutable;
	}
	public void setVaultSCMExecutable(String vaultSCMExecutable) {
		this.vaultSCMExecutable = vaultSCMExecutable;
	}
	
	/**
	 * Singleton descriptor.
	 */
	@Extension
	public static final VaultSCMDescriptor DESCRIPTOR = new VaultSCMDescriptor();
	
	//format dates for vault client
	public static final SimpleDateFormat VAULT_DATETIME_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

	@DataBoundConstructor
	public VaultSCM(String server, String path, String userName,
			String password, String repository, String vaultSCMExecutable, Boolean ssl, String merge, Boolean makeWriteable) {
		this.server = server;
		this.userName = userName;
		this.password = password;
		this.repository = repository;
		this.vaultSCMExecutable = vaultSCMExecutable;
		this.path = path;
		this.ssl = ssl; //Default to true
		this.merge = merge.isEmpty() ? "overwrite" : merge;
                this.makeWriteable = makeWriteable;
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
		
		Date lastBuild = ((VaultSCMRevisionState)baseline).getDate();
		Date now = new Date();
		File temporaryFile = File.createTempFile("changes", "txt");
		int countChanges = determineChangeCount(launcher, workspace, listener, lastBuild,now,temporaryFile);
				
		if (countChanges == 0)
			return PollingResult.NO_CHANGES;
		else
			return PollingResult.BUILD_NOW;
			
		
	}
	
	
	@Override
	public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher,
			FilePath workspace, BuildListener listener, File changelogFile)
			throws IOException, InterruptedException {
		 boolean returnValue = true;
		 
		 if (server != null )
			 listener.getLogger().println("server: "+server);
		
		//populate the GET command
		//in some cases username, host and password can be empty e.g. if rememberlogin is used to store login data
    	ArgumentListBuilder argBuildr = new ArgumentListBuilder();
    	argBuildr.add(getVaultSCMExecutable());
    	argBuildr.add("GET");
    	
    	if (!server.isEmpty()) 
    		argBuildr.add("-host",server);
    	
    	if (!userName.isEmpty())
    		argBuildr.add("-user",userName);
    	
    	if (!password.isEmpty())
    		argBuildr.add("-password",password);
    	
    	if (!repository.isEmpty())
    		argBuildr.add("-repository",repository);
    	
    	if (this.ssl)
    		argBuildr.add("-ssl");

    	if (this.ssl)
    		argBuildr.add("-ssl");
        
        if ( this.makeWriteable)
                argBuildr.add("-makewritable");

        
    	argBuildr.add("-merge",merge);
    	argBuildr.add("-workingfolder",workspace.getRemote() );
    	argBuildr.add(this.path);
    	
		int cmdResult = launcher.launch().cmds(argBuildr).envs(build.getEnvironment(TaskListener.NULL))
				.stdout(listener.getLogger()).pwd(workspace).join();
		if (cmdResult == 0)
		{
			final Run<?, ?> lastBuild = build.getPreviousBuild();
			final Date lastBuildDate;

			if (lastBuild == null) {
				lastBuildDate = new Date();
				lastBuildDate.setTime(0); // default to January 1, 1970
				listener.getLogger().print("Never been built.");				
			} else
				lastBuildDate = lastBuild.getTimestamp().getTime();
			
			Date now = new Date(); //defaults to current
			
			returnValue = captureChangeLog(launcher, workspace,listener, lastBuildDate, now, changelogFile);
		}
		else
			returnValue = false;
			
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
            	argBuildr.add(getVaultSCMExecutable());
            	argBuildr.add("VERSIONHISTORY");
            	
            	if (!server.isEmpty())
            		argBuildr.add("-host",server);
            	
            	if (!userName.isEmpty())
            		argBuildr.add("-user",userName);
            	
            	if (!password.isEmpty())
            		argBuildr.add("-password",password);
            	
            	if (!repository.isEmpty())
            		argBuildr.add("-repository",repository);
            	
            	if (this.ssl)
            		argBuildr.add("-ssl");
            	
            	argBuildr.add("-enddate",today);
            	argBuildr.add("-begindate",latestBuildDate);
            	argBuildr.add(this.path);
            	
            	int cmdResult = launcher.launch().cmds(argBuildr).envs(new String[0]).stdout(bos).pwd(workspace).join();
            	if (cmdResult != 0)
            	{
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
        listener.getLogger().println("Change log file: " + changelogFile.getAbsolutePath() );
        
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
            	argBuildr.add(getVaultSCMExecutable());
            	argBuildr.add("VERSIONHISTORY");
            	
            	if (!server.isEmpty())
            		argBuildr.add("-host",server);
            	
            	if (!userName.isEmpty())
            		argBuildr.add("-user",userName);
            	
            	if (!password.isEmpty())
            		argBuildr.add("-password",password);
            	
            	if (!repository.isEmpty())
            		argBuildr.add("-repository",repository);
            	
            	if (this.ssl)
            		argBuildr.add("-ssl");
            	
            	argBuildr.add("-enddate",today);
            	argBuildr.add("-begindate",latestBuildDate);
            	argBuildr.add(this.path);
            	
            	int cmdResult = launcher.launch().cmds(argBuildr).envs(new String[0]).stdout(bos).pwd(workspace).join();
            	if (cmdResult != 0)
            	{
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
	
	
    private final static String[] MERGE_OPTION_STRINGS = new String[] {
        "automatic",
        "overwrite",
        "later",
    };
    
    private final static List<String> MERGE_OPTIONS = new ArrayList<String>();
    static {
        MERGE_OPTIONS.addAll(Arrays.asList(MERGE_OPTION_STRINGS));
    }
        
    public List<String> getMergeOptions() {
        return MERGE_OPTIONS;
    }

}
