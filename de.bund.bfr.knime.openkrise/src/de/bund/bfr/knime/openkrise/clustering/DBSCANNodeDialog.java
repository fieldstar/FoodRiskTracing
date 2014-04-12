package de.bund.bfr.knime.openkrise.clustering;

import org.knime.core.node.defaultnodesettings.DefaultNodeSettingsPane;
import org.knime.core.node.defaultnodesettings.DialogComponentBoolean;
import org.knime.core.node.defaultnodesettings.DialogComponentNumber;
import org.knime.core.node.defaultnodesettings.DialogComponentStringSelection;
import org.knime.core.node.defaultnodesettings.SettingsModelBoolean;
import org.knime.core.node.defaultnodesettings.SettingsModelDouble;
import org.knime.core.node.defaultnodesettings.SettingsModelInteger;
import org.knime.core.node.defaultnodesettings.SettingsModelString;

/**
 * <code>NodeDialog</code> for the "DBSCAN" Node.
 * 
 *
 * This node dialog derives from {@link DefaultNodeSettingsPane} which allows
 * creation of a simple dialog with standard components. If you need a more 
 * complex dialog please derive directly from 
 * {@link org.knime.core.node.NodeDialogPane}.
 * 
 * @author BfR
 */
public class DBSCANNodeDialog extends DefaultNodeSettingsPane {

    /**
     * New pane for configuring the DBSCAN node.
     */
    protected DBSCANNodeDialog() {
        addDialogComponent(new DialogComponentNumber(new SettingsModelInteger(DBSCANNodeModel.MINPTS, 6), "Enter minPts:", 1));
        addDialogComponent(new DialogComponentNumber(new SettingsModelDouble(DBSCANNodeModel.EPS, .09), "Enter epsilon:", 0.01));
        addDialogComponent(new DialogComponentBoolean(new SettingsModelBoolean(DBSCANNodeModel.DOUBLETTES, false), "Allow multiple unique points?"));
        addDialogComponent(new DialogComponentStringSelection(new SettingsModelString(DBSCANNodeModel.CHOSENMODEL, "DBSCAN"), "Choose model:", new String[]{"DBSCAN","KMeans"}));
    }
}
