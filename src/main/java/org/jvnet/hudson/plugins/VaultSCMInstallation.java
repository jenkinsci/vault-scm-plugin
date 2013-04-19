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
import hudson.Util;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import java.io.IOException;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Global configuration for the VaultSCM plugin, as shown on the Jenkins
 * Configure System page.
 *
 * @author Stuart Whelan
 */
//@Extension
public final class VaultSCMInstallation extends ToolInstallation implements NodeSpecific<VaultSCMInstallation> {

    /**
     * The default vault install folder.
     */
    private static final String DEFAULT_VAULT_LOCATION = "C:\\Program Files (x86)\\SourceGear\\Vault Client\\vault.exe";
    
    /**
     * The chosen vault install location.
     */
    private transient String vaultLocation;

    @DataBoundConstructor
    public VaultSCMInstallation(String name, String home, String vaultLocation) {
        super(name, home, null);
        this.vaultLocation = Util.fixEmpty(vaultLocation);
    }

    public String getVaultLocation() {
        return vaultLocation == null ? DEFAULT_VAULT_LOCATION
                : this.vaultLocation;
    }

    public void setVaultLocation(String vaultLocation) {
        this.vaultLocation = vaultLocation;
    }

    public VaultSCMInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new VaultSCMInstallation(getName(), translateFor(node, log), getVaultLocation());
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<VaultSCMInstallation> {

        public String getDisplayName() {
            return "Sourcegear Vault";
        }

        @Override
        public VaultSCMInstallation[] getInstallations() {
            return Hudson.getInstance().getDescriptorByType(VaultSCM.VaultSCMDescriptor.class).getInstallations();
        }

        @Override
        public void setInstallations(VaultSCMInstallation... installations) {
            Hudson.getInstance().getDescriptorByType(VaultSCM.VaultSCMDescriptor.class).setInstallations(installations);
        }
    }
}
