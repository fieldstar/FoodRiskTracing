/*******************************************************************************
 * Copyright (c) 2014 Federal Institute for Risk Assessment (BfR), Germany 
 * 
 * Developers and contributors are 
 * Christian Thoens (BfR)
 * Armin A. Weiser (BfR)
 * Matthias Filter (BfR)
 * Alexander Falenski (BfR)
 * Annemarie Kaesbohrer (BfR)
 * Bernd Appel (BfR)
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package de.bund.bfr.knime.gis.views.graphvisualizer;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.knime.core.node.BufferedDataTable;
import org.knime.core.node.DataAwareNodeDialogPane;
import org.knime.core.node.InvalidSettingsException;
import org.knime.core.node.NodeSettingsRO;
import org.knime.core.node.NodeSettingsWO;
import org.knime.core.node.NotConfigurableException;
import org.knime.core.node.port.PortObject;

import de.bund.bfr.knime.UI;
import de.bund.bfr.knime.gis.views.canvas.GraphCanvas;

/**
 * <code>NodeDialog</code> for the "GraphVisualizer" Node.
 * 
 * @author Christian Thoens
 */
public class GraphVisualizerNodeDialog extends DataAwareNodeDialogPane
		implements ActionListener, ComponentListener {

	private JPanel panel;
	private GraphCanvas graphCanvas;

	private boolean resized;

	private BufferedDataTable nodeTable;
	private BufferedDataTable edgeTable;

	private GraphVisualizerSettings set;

	/**
	 * New pane for configuring the GraphVisualizer node.
	 */
	protected GraphVisualizerNodeDialog() {
		set = new GraphVisualizerSettings();

		JButton inputButton = new JButton("Input");

		inputButton.addActionListener(this);

		panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.add(UI.createWestPanel(UI.createEmptyBorderPanel(inputButton)),
				BorderLayout.NORTH);
		panel.addComponentListener(this);

		addTab("Options", panel, false);
	}

	@Override
	protected void loadSettingsFrom(NodeSettingsRO settings, PortObject[] input)
			throws NotConfigurableException {
		nodeTable = (BufferedDataTable) input[0];
		edgeTable = (BufferedDataTable) input[1];
		set.getGraphSettings().loadSettings(settings);

		updateGraphCanvas(false);
		resized = false;
	}

	@Override
	protected void saveSettingsTo(NodeSettingsWO settings)
			throws InvalidSettingsException {
		updateSettings();
		set.getGraphSettings().saveSettings(settings);
	}

	@Override
	public void componentHidden(ComponentEvent e) {
	}

	@Override
	public void componentMoved(ComponentEvent e) {
	}

	@Override
	public void componentResized(ComponentEvent e) {
		if (SwingUtilities.getWindowAncestor(panel).isActive()) {
			resized = true;
		}
	}

	@Override
	public void componentShown(ComponentEvent e) {
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		GraphVisualizerInputDialog dialog = new GraphVisualizerInputDialog(
				(JButton) e.getSource(), nodeTable.getSpec(),
				edgeTable.getSpec(), set);

		dialog.setVisible(true);

		if (dialog.isApproved()) {
			updateSettings();
			updateGraphCanvas(true);
		}
	}

	private void updateGraphCanvas(boolean showWarning) {
		if (graphCanvas != null) {
			panel.remove(graphCanvas);
		}

		GraphVisualizerCanvasCreator creator = new GraphVisualizerCanvasCreator(
				nodeTable, edgeTable, set);

		graphCanvas = creator.createGraphCanvas();

		if (graphCanvas == null) {
			graphCanvas = new GraphCanvas(true);
			graphCanvas.setCanvasSize(set.getGraphSettings().getCanvasSize());

			if (showWarning) {
				JOptionPane.showMessageDialog(panel,
						"Error reading nodes and edges", "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		}

		panel.add(graphCanvas, BorderLayout.CENTER);
		panel.revalidate();
	}

	private void updateSettings() {
		List<String> selectedGraphNodes = new ArrayList<>(
				graphCanvas.getSelectedNodeIds());
		List<String> selectedGraphEdges = new ArrayList<>(
				graphCanvas.getSelectedEdgeIds());

		Collections.sort(selectedGraphNodes);
		Collections.sort(selectedGraphEdges);

		set.getGraphSettings().setShowLegend(graphCanvas.isShowLegend());
		set.getGraphSettings().setScaleX(graphCanvas.getScaleX());
		set.getGraphSettings().setScaleY(graphCanvas.getScaleY());
		set.getGraphSettings().setTranslationX(graphCanvas.getTranslationX());
		set.getGraphSettings().setTranslationY(graphCanvas.getTranslationY());
		set.getGraphSettings().setNodePositions(graphCanvas.getNodePositions());
		set.getGraphSettings().setNodeSize(graphCanvas.getNodeSize());
		set.getGraphSettings().setFontSize(graphCanvas.getFontSize());
		set.getGraphSettings().setFontBold(graphCanvas.isFontBold());
		set.getGraphSettings().setJoinEdges(graphCanvas.isJoinEdges());
		set.getGraphSettings().setArrowInMiddle(graphCanvas.isArrowInMiddle());
		set.getGraphSettings().setSkipEdgelessNodes(
				graphCanvas.isSkipEdgelessNodes());
		set.getGraphSettings().setCollapsedNodes(
				graphCanvas.getCollapsedNodes());
		set.getGraphSettings().setSelectedNodes(selectedGraphNodes);
		set.getGraphSettings().setSelectedEdges(selectedGraphEdges);
		set.getGraphSettings().setNodeHighlightConditions(
				graphCanvas.getNodeHighlightConditions());
		set.getGraphSettings().setEdgeHighlightConditions(
				graphCanvas.getEdgeHighlightConditions());
		set.getGraphSettings().setEditingMode(graphCanvas.getEditingMode());

		if (resized) {
			set.getGraphSettings().setCanvasSize(graphCanvas.getCanvasSize());
		}
	}
}
