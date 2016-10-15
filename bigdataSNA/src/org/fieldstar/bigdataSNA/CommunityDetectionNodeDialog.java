package org.fieldstar.bigdataSNA;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.SettingsModelIntegerBounded;

/**
 * <code>NodeDialog</code> for the "CommunityDetection" Node.
 * Input the SNA data, detect the community clusters.
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author XIAOQING ZENG
 */
public class CommunityDetectionNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring CommunityDetection node dialog.
     * This is just a suggestion to demonstrate possible default dialog
     * components.
     */
    protected CommunityDetectionNodeDialog() {
        super();
        
        addDialogComponent(new DialogComponentNumber(
                new SettingsModelIntegerBounded(
                    CommunityDetectionNodeModel.CFGKEY_COUNT,
                    CommunityDetectionNodeModel.DEFAULT_COUNT,
                    Integer.MIN_VALUE, Integer.MAX_VALUE),
                    "Counter:", /*step*/ 1, /*componentwidth*/ 5));
                    
    }
}

