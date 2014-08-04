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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.knime.core.node.BufferedDataTable;

import de.bund.bfr.knime.KnimeUtilities;
import de.bund.bfr.knime.gis.views.ViewUtilities;
import de.bund.bfr.knime.gis.views.canvas.GraphCanvas;
import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.GraphNode;

public class GraphVisualizerCanvasCreator {

	private BufferedDataTable nodeTable;
	private BufferedDataTable edgeTable;
	private GraphVisualizerSettings set;

	public GraphVisualizerCanvasCreator(BufferedDataTable nodeTable,
			BufferedDataTable edgeTable, GraphVisualizerSettings set) {
		this.nodeTable = nodeTable;
		this.edgeTable = edgeTable;
		this.set = set;
	}

	public GraphCanvas createGraphCanvas() {
		Map<String, Class<?>> nodeProperties = KnimeUtilities
				.getTableColumns(nodeTable.getSpec());
		Map<String, Class<?>> edgeProperties = KnimeUtilities
				.getTableColumns(edgeTable.getSpec());
		Map<String, GraphNode> nodes = ViewUtilities.readGraphNodes(nodeTable,
				nodeProperties, set.getGraphSettings().getNodeIdColumn(), null);

		if (nodes.isEmpty()) {
			return null;
		}

		List<Edge<GraphNode>> edges = ViewUtilities.readEdges(edgeTable,
				edgeProperties, nodes, null, set.getGraphSettings()
						.getEdgeFromColumn(), set.getGraphSettings()
						.getEdgeToColumn());
		String edgeIdProperty = ViewUtilities.createNewIdProperty(edges,
				edgeProperties);
		GraphCanvas canvas = new GraphCanvas(new ArrayList<>(nodes.values()),
				edges, nodeProperties, edgeProperties, set.getGraphSettings()
						.getNodeIdColumn(), edgeIdProperty, set
						.getGraphSettings().getEdgeFromColumn(), set
						.getGraphSettings().getEdgeToColumn(), true);

		canvas.setShowLegend(set.getGraphSettings().isShowLegend());
		canvas.setCanvasSize(set.getGraphSettings().getCanvasSize());
		canvas.setEditingMode(set.getGraphSettings().getEditingMode());
		canvas.setNodeSize(set.getGraphSettings().getNodeSize());
		canvas.setFontSize(set.getGraphSettings().getFontSize());
		canvas.setFontBold(set.getGraphSettings().isFontBold());
		canvas.setJoinEdges(set.getGraphSettings().isJoinEdges());
		canvas.setCollapsedNodes(set.getGraphSettings().getCollapsedNodes());
		canvas.setNodeHighlightConditions(set.getGraphSettings()
				.getNodeHighlightConditions());
		canvas.setEdgeHighlightConditions(set.getGraphSettings()
				.getEdgeHighlightConditions());
		canvas.setSkipEdgelessNodes(set.getGraphSettings()
				.isSkipEdgelessNodes());
		canvas.setSelectedNodeIds(new LinkedHashSet<>(set.getGraphSettings()
				.getSelectedNodes()));
		canvas.setSelectedEdgeIds(new LinkedHashSet<>(set.getGraphSettings()
				.getSelectedEdges()));

		if (!Double.isNaN(set.getGraphSettings().getScaleX())
				&& !Double.isNaN(set.getGraphSettings().getScaleY())
				&& !Double.isNaN(set.getGraphSettings().getTranslationX())
				&& !Double.isNaN(set.getGraphSettings().getTranslationY())) {
			canvas.setTransform(set.getGraphSettings().getScaleX(), set
					.getGraphSettings().getScaleY(), set.getGraphSettings()
					.getTranslationX(), set.getGraphSettings()
					.getTranslationY());
		}

		canvas.setNodePositions(set.getGraphSettings().getNodePositions());

		return canvas;
	}
}
