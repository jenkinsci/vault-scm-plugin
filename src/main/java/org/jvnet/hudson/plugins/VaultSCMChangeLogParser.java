/**
 * @author Antti Relander / Eficode
 * @verison 0.1
 * @since 2011-12-07
 */
package org.jvnet.hudson.plugins;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.ChangeLogSet.Entry;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.jvnet.hudson.plugins.VaultSCMChangeLogSet.VaultSCMChangeLogSetEntry;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class VaultSCMChangeLogParser extends ChangeLogParser {
    
    @Override
    @SuppressWarnings("rawtypes")
    public ChangeLogSet<? extends Entry> parse(AbstractBuild build,
            File changelogFile) throws IOException, SAXException {

        String userName;
        String date;
        String comment;
        String version;
        String transactionId;
        //open the change log File
        VaultSCMChangeLogSet cls = new VaultSCMChangeLogSet(build);
        try {

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(changelogFile);
            doc.getDocumentElement().normalize();
            NodeList nodeLst = doc.getElementsByTagName("item");

            for (int s = 0; s < nodeLst.getLength(); s++) {

                Element mostRecentChange = (Element) nodeLst.item(s);
                userName = mostRecentChange.getAttribute("user");
                date = mostRecentChange.getAttribute("date");
                comment = mostRecentChange.getAttribute("comment");
                version = mostRecentChange.getAttribute("version");
                transactionId = mostRecentChange.getAttribute("txid");

                VaultSCMChangeLogSetEntry next = new VaultSCMChangeLogSetEntry(comment, version, date, cls, userName, transactionId);
                if (!cls.addEntry(next)) {
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }


        return cls;
    }
}
