/*
 * The MIT License
 * 
 * Copyright (c) 2012 Frederik Fromm
 * Copyright (c) 2012 Steven G. Brown
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Global configuration for the VaultSCM plugin, as shown on the Jenkins
 * Configure System page.
 *
 * @author Stuart Whelan
 */
@Extension
public class VaultSCMConfig extends GlobalConfiguration {

    /**
     * The default vault install folder.
     */
    private static final String DEFAULT_VAULT_LOCATION = "C:\\Program Files (x86)\\SourceGear\\Vault Client\\vault.exe";
    /**
     * The chosen vault install location.
     */
    private String vaultLocation;
    /**
     * The chosen vault server name.
     */
    private String serverName;
    /**
     * The chosen vault user name.
     */
    private String userName;
    /**
     * The chosen vault password.
     */
    private Secret password;
    /**
     * The chosen vault repository name.
     */
    private String repositoryName;
    /**
     * The chosen vault repository path.
     */
    private String repositoryPath;
    private Boolean sslEnabled;
    private Boolean makeWritableEnabled;
    private String mergeOption;

    /**
     * Constructor
     */
    public VaultSCMConfig() {
        load();
    }

    public String getVaultLocation() {
        return vaultLocation == null ? DEFAULT_VAULT_LOCATION
                : this.vaultLocation;
    }

    public void setVaultLocation(String vaultLocation) {
        this.vaultLocation = vaultLocation;
    }

    public String getServerName() {
        return this.serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getUserName() {
        return this.userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Secret getPassword() {
        return this.password;
    }

    public void setPassword(Secret password) {
        this.password = password;
    }

    public String getRepositoryName() {
        return this.repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getRepositoryPath() {
        return this.repositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    public Boolean getSslEnabled() {
        return this.sslEnabled;
    }

    public void setSslEnabled(Boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public Boolean getMakeWritableEnabled() {
        return this.makeWritableEnabled;
    }

    public void setMakeWritableEnabled(Boolean makeWritable) {
        this.makeWritableEnabled = makeWritable;
    }

    public String getMergeOption() {
        return mergeOption == null ? "overwrite" : this.mergeOption;
    }

    public void setMergeOption(String mergeOption) {
        this.mergeOption = mergeOption;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json)
            throws Descriptor.FormException {
        req.bindJSON(this, json);
        save();
        return true;
    }

    /**
     * Get the currently configured global VaultSCM settings.
     *
     * @return the VaultSCM global config
     */
    public static VaultSCMConfig get() {
        return GlobalConfiguration.all().get(VaultSCMConfig.class);
    }
    private final static String[] MERGE_OPTION_STRINGS = new String[]{
        "automatic",
        "overwrite",
        "later",};
}
