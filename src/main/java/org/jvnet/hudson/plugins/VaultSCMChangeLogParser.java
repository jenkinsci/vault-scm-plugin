/**
 * @author Antti Relander / Eficode
 * @verison 0.1
 * @since 2011-12-07
 */
package org.jvnet.hudson.plugins;

import hudson.model.AbstractBuild;
import hudson.model.User;
import hudson.scm.ChangeLogParser;
import hudson.scm.EditType;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.lf5.LogLevel;
import org.jfree.util.Log;
import org.jvnet.hudson.plugins.VaultSCMChangeLogSet.VaultSCMChangeLogSetEntry.VaultAffectedFile;
import org.jvnet.hudson.plugins.VaultSCMChangeLogSet.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class VaultSCMChangeLogParser extends ChangeLogParser {
    
    @Override
    @SuppressWarnings("rawtypes")
    public ChangeLogSet<? extends Entry> parse(AbstractBuild build,
            File changelogFile) throws IOException, SAXException {

        String version;
        String transactionId;
        String transactionIdOld = "";
        String name; // name of file
        String typeName; // action
        
        //open the change log File
        VaultSCMChangeLogSet cls = new VaultSCMChangeLogSet(build);
        try {

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(changelogFile);
            doc.getDocumentElement().normalize();
            NodeList nodeLst = doc.getElementsByTagName("item");
            VaultSCMChangeLogSetEntry currentEntry = new VaultSCMChangeLogSetEntry(cls);
            
            for (int s = 0; s < nodeLst.getLength(); s++) {

                Element mostRecentChange = (Element) nodeLst.item(s);
                
                transactionId = mostRecentChange.getAttribute("txid");
                if (!transactionId.equals(transactionIdOld)) {
                    currentEntry = new VaultSCMChangeLogSetEntry(cls);
                    // these attributes should be the same for one transaction
                    currentEntry.setUser(User.get(mostRecentChange.getAttribute("user")));
                    currentEntry.setDate(mostRecentChange.getAttribute("date"));
                    currentEntry.setComment(mostRecentChange.getAttribute("comment"));
                    currentEntry.setTransactionId(transactionId);
                    transactionIdOld = transactionId;

                    cls.addEntry(currentEntry);
                }

             // these attributes change for each item
                name = mostRecentChange.getAttribute("name");
                version = mostRecentChange.getAttribute("version");
                typeName = mostRecentChange.getAttribute("typeName");

                // only handle file changes and creations for now
                if (typeName.equals("CheckIn") || typeName.equals("Created")) {
                    EditType editType;
                    if (typeName.equals("CheckIn")) {
                        editType = EditType.EDIT;
                    } else if (typeName.equals("Created")) {
                        editType = EditType.ADD;
                    } else {
                        editType = EditType.DELETE;
                    }
                    int posSlash = name.indexOf('/');
                    if (posSlash > 0){
                        name = name.substring(posSlash+1);
                    }
                    currentEntry.addAffectedFile(new VaultAffectedFile(name, editType, version));
                    Logger.getLogger(VaultSCMChangeLogParser.class.getName()).log(Level.INFO, "file: " + name + ", action: " + typeName + ", version: " + version);
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        return cls;
    }
}
