/*******************************************************************************
 * Copyright (c) 2016 German Federal Institute for Risk Assessment (BfR)
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
 *
 * Contributors:
 *     Department Biological Safety - BfR
 *******************************************************************************/
package de.bund.bfr.knime.gis.views.canvas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;

import com.google.common.base.Strings;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import de.bund.bfr.jung.BetterEdgeShapeTransformer;
import de.bund.bfr.jung.BetterGraphMouse;
import de.bund.bfr.jung.BetterPickingGraphMousePlugin;
import de.bund.bfr.jung.BetterScalingGraphMousePlugin;
import de.bund.bfr.jung.BetterVisualizationViewer;
import de.bund.bfr.jung.JungListener;
import de.bund.bfr.jung.JungUtils;
import de.bund.bfr.jung.MiddleEdgeArrowRenderingSupport;
import de.bund.bfr.jung.ZoomingPaintable;
import de.bund.bfr.knime.KnimeUtils;
import de.bund.bfr.knime.gis.views.canvas.dialogs.DefaultPropertySelectorCreator;
import de.bund.bfr.knime.gis.views.canvas.dialogs.HighlightDialog;
import de.bund.bfr.knime.gis.views.canvas.dialogs.HighlightListDialog;
import de.bund.bfr.knime.gis.views.canvas.dialogs.HighlightSelectionDialog;
import de.bund.bfr.knime.gis.views.canvas.dialogs.PropertiesDialog;
import de.bund.bfr.knime.gis.views.canvas.dialogs.PropertySelectorCreator;
import de.bund.bfr.knime.gis.views.canvas.dialogs.SinglePropertiesDialog;
import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.Element;
import de.bund.bfr.knime.gis.views.canvas.element.Node;
import de.bund.bfr.knime.gis.views.canvas.highlighting.HighlightConditionList;
import de.bund.bfr.knime.gis.views.canvas.util.CanvasLegend;
import de.bund.bfr.knime.gis.views.canvas.util.CanvasOptionsPanel;
import de.bund.bfr.knime.gis.views.canvas.util.CanvasPopupMenu;
import de.bund.bfr.knime.gis.views.canvas.util.EdgePropertySchema;
import de.bund.bfr.knime.gis.views.canvas.util.Naming;
import de.bund.bfr.knime.gis.views.canvas.util.NodePropertySchema;
import de.bund.bfr.knime.gis.views.canvas.util.PropertySchema;
import de.bund.bfr.knime.gis.views.canvas.util.Transform;
import de.bund.bfr.knime.ui.Dialogs;
import de.bund.bfr.knime.ui.ListFilterDialog;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.visualization.Layer;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.VisualizationImageServer;
import edu.uci.ics.jung.visualization.VisualizationServer.Paintable;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse.Mode;
import edu.uci.ics.jung.visualization.renderers.BasicEdgeArrowRenderingSupport;
import edu.uci.ics.jung.visualization.transform.MutableAffineTransformer;

public abstract class Canvas<V extends Node> extends JPanel
		implements JungListener, CanvasPopupMenu.ClickListener, CanvasOptionsPanel.ChangeListener, ICanvas<V> {

	private static final long serialVersionUID = 1L;
	private static final String IS_META_NODE = "IsMeta";

	protected BetterVisualizationViewer<V, Edge<V>> viewer;
	protected Transform transform;
	protected Naming naming;

	protected List<V> allNodes;
	protected List<Edge<V>> allEdges;
	protected Set<V> nodes;
	protected Set<Edge<V>> edges;
	protected Map<String, V> nodeSaveMap;
	protected Map<String, Edge<V>> edgeSaveMap;
	protected Map<Edge<V>, Set<Edge<V>>> joinMap;
	protected Map<String, Set<String>> collapsedNodes;

	protected NodePropertySchema nodeSchema;
	protected EdgePropertySchema edgeSchema;
	protected String metaNodeProperty;

	protected HighlightConditionList nodeHighlightConditions;
	protected HighlightConditionList edgeHighlightConditions;

	private CanvasOptionsPanel optionsPanel;
	private CanvasPopupMenu popup;

	public Canvas(List<V> nodes, List<Edge<V>> edges, NodePropertySchema nodeSchema, EdgePropertySchema edgeSchema,
			Naming naming) {
		this.nodeSchema = nodeSchema;
		this.edgeSchema = edgeSchema;
		this.naming = naming;
		transform = Transform.IDENTITY_TRANSFORM;
		nodeHighlightConditions = new HighlightConditionList();
		edgeHighlightConditions = new HighlightConditionList();

		this.nodes = new LinkedHashSet<>();
		this.edges = new LinkedHashSet<>();
		CanvasUtils.copyNodesAndEdges(nodes, edges, this.nodes, this.edges);
		allNodes = nodes;
		allEdges = edges;
		nodeSaveMap = CanvasUtils.getElementsById(this.nodes);
		edgeSaveMap = CanvasUtils.getElementsById(this.edges);
		joinMap = new LinkedHashMap<>();
		collapsedNodes = new LinkedHashMap<>();
		metaNodeProperty = KnimeUtils.createNewValue(IS_META_NODE, nodeSchema.getMap().keySet());
		nodeSchema.getMap().put(metaNodeProperty, Boolean.class);

		viewer = new BetterVisualizationViewer<>();
		viewer.setBackground(Color.WHITE);
		viewer.addPostRenderPaintable(new PostPaintable());
		viewer.addPostRenderPaintable(createZoomingPaintable());
		viewer.getGraphLayout().setGraph(CanvasUtils.createGraph(viewer, this.nodes, this.edges));

		RenderContext<V, Edge<V>> rc = viewer.getRenderContext();

		rc.setEdgeShapeTransformer(new BetterEdgeShapeTransformer<>(CanvasOptionsPanel.DEFAULT_FONT_SIZE));
		rc.setVertexFillPaintTransformer(JungUtils.newNodeFillTransformer(rc, null));
		rc.setVertexStrokeTransformer(JungUtils.newNodeStrokeTransformer(rc, null));
		rc.setVertexDrawPaintTransformer(JungUtils.newNodeDrawTransformer(rc));
		rc.setEdgeDrawPaintTransformer(JungUtils.newEdgeDrawTransformer(rc));
		rc.setEdgeFillPaintTransformer(JungUtils.newEdgeFillTransformer(rc, null));
		((MutableAffineTransformer) rc.getMultiLayerTransformer().getTransformer(Layer.LAYOUT)).addChangeListener(e -> {
			AffineTransform transform = ((MutableAffineTransformer) rc.getMultiLayerTransformer()
					.getTransformer(Layer.LAYOUT)).getTransform();
			boolean transformValid = transform.getScaleX() != 0.0 && transform.getScaleY() != 0.0;

			this.transform = transformValid ? new Transform(transform) : Transform.IDENTITY_TRANSFORM;
			applyTransform();
		});

		BetterGraphMouse<V, Edge<V>> graphMouse = new BetterGraphMouse<>(createPickingPlugin(), createScalingPlugin());

		graphMouse.setMode(CanvasOptionsPanel.DEFAULT_MODE);
		graphMouse.addChangeListener(this);

		viewer.setGraphMouse(graphMouse);

		setLayout(new BorderLayout());
		add(viewer, BorderLayout.CENTER);
	}

	@Override
	public JPanel getComponent() {
		return this;
	}

	@Override
	public void addCanvasListener(CanvasListener listener) {
		listenerList.add(CanvasListener.class, listener);
	}

	@Override
	public void removeCanvasListener(CanvasListener listener) {
		listenerList.remove(CanvasListener.class, listener);
	}

	@Override
	public Set<V> getNodes() {
		return nodes;
	}

	@Override
	public Set<Edge<V>> getEdges() {
		return edges;
	}

	@Override
	public Dimension getCanvasSize() {
		return viewer.getSize();
	}

	@Override
	public void setCanvasSize(Dimension canvasSize) {
		viewer.setPreferredSize(canvasSize);
		resetLayoutItemClicked();
	}

	@Override
	public Mode getEditingMode() {
		return optionsPanel.getEditingMode();
	}

	@Override
	public void setEditingMode(Mode editingMode) {
		optionsPanel.setEditingMode(editingMode);
	}

	@Override
	public boolean isShowLegend() {
		return optionsPanel.isShowLegend();
	}

	@Override
	public void setShowLegend(boolean showLegend) {
		optionsPanel.setShowLegend(showLegend);
	}

	@Override
	public boolean isJoinEdges() {
		return optionsPanel.isJoinEdges();
	}

	@Override
	public void setJoinEdges(boolean joinEdges) {
		optionsPanel.setJoinEdges(joinEdges);
	}

	@Override
	public boolean isSkipEdgelessNodes() {
		return optionsPanel.isSkipEdgelessNodes();
	}

	@Override
	public void setSkipEdgelessNodes(boolean skipEdgelessNodes) {
		optionsPanel.setSkipEdgelessNodes(skipEdgelessNodes);
	}

	@Override
	public boolean isShowEdgesInMetaNode() {
		return optionsPanel.isShowEdgesInMetaNode();
	}

	@Override
	public void setShowEdgesInMetaNode(boolean showEdgesInMetaNode) {
		optionsPanel.setShowEdgesInMetaNode(showEdgesInMetaNode);
	}

	@Override
	public int getFontSize() {
		return optionsPanel.getFontSize();
	}

	@Override
	public void setFontSize(int fontSize) {
		optionsPanel.setFontSize(fontSize);
	}

	@Override
	public boolean isFontBold() {
		return optionsPanel.isFontBold();
	}

	@Override
	public void setFontBold(boolean fontBold) {
		optionsPanel.setFontBold(fontBold);
	}

	@Override
	public int getNodeSize() {
		return optionsPanel.getNodeSize();
	}

	@Override
	public void setNodeSize(int nodeSize) {
		optionsPanel.setNodeSize(nodeSize);
	}

	@Override
	public Integer getNodeMaxSize() {
		return optionsPanel.getNodeMaxSize();
	}

	@Override
	public void setNodeMaxSize(Integer nodeMaxSize) {
		optionsPanel.setNodeMaxSize(nodeMaxSize);
	}

	@Override
	public int getEdgeThickness() {
		return optionsPanel.getEdgeThickness();
	}

	@Override
	public void setEdgeThickness(int edgeThickness) {
		optionsPanel.setEdgeThickness(edgeThickness);
	}

	@Override
	public Integer getEdgeMaxThickness() {
		return optionsPanel.getEdgeMaxThickness();
	}

	@Override
	public void setEdgeMaxThickness(Integer edgeMaxThickness) {
		optionsPanel.setEdgeMaxThickness(edgeMaxThickness);
	}

	@Override
	public boolean isArrowInMiddle() {
		return optionsPanel.isArrowInMiddle();
	}

	@Override
	public void setArrowInMiddle(boolean arrowInMiddle) {
		optionsPanel.setArrowInMiddle(arrowInMiddle);
	}

	@Override
	public String getLabel() {
		return optionsPanel.getLabel();
	}

	@Override
	public void setLabel(String label) {
		optionsPanel.setLabel(label);
	}

	@Override
	public int getBorderAlpha() {
		return optionsPanel.getBorderAlpha();
	}

	@Override
	public void setBorderAlpha(int borderAlpha) {
		optionsPanel.setBorderAlpha(borderAlpha);
	}

	@Override
	public boolean isAvoidOverlay() {
		return optionsPanel.isAvoidOverlay();
	}

	@Override
	public void setAvoidOverlay(boolean avoidOverlay) {
		optionsPanel.setAvoidOverlay(avoidOverlay);
	}

	@Override
	public NodePropertySchema getNodeSchema() {
		return nodeSchema;
	}

	@Override
	public EdgePropertySchema getEdgeSchema() {
		return edgeSchema;
	}

	@Override
	public Naming getNaming() {
		return naming;
	}

	@Override
	public Set<V> getSelectedNodes() {
		return new LinkedHashSet<>(Sets.intersection(viewer.getPickedVertexState().getPicked(), nodes));
	}

	@Override
	public void setSelectedNodes(Set<V> selectedNodes) {
		viewer.getPickedVertexState().clear();
		selectedNodes.stream().filter(n -> nodes.contains(n)).forEach(n -> viewer.getPickedVertexState().pick(n, true));
		nodePickingFinished();
	}

	@Override
	public Set<Edge<V>> getSelectedEdges() {
		return new LinkedHashSet<>(Sets.intersection(viewer.getPickedEdgeState().getPicked(), edges));
	}

	@Override
	public void setSelectedEdges(Set<Edge<V>> selectedEdges) {
		viewer.getPickedEdgeState().clear();
		selectedEdges.stream().filter(e -> edges.contains(e)).forEach(e -> viewer.getPickedEdgeState().pick(e, true));
		edgePickingFinished();
	}

	@Override
	public Set<String> getSelectedNodeIds() {
		return CanvasUtils.getElementIds(getSelectedNodes());
	}

	@Override
	public void setSelectedNodeIds(Set<String> selectedNodeIds) {
		setSelectedNodes(CanvasUtils.getElementsById(nodes, selectedNodeIds));
	}

	@Override
	public void setSelectedNodeIdsWithoutListener(Set<String> selectedNodeIds) {
		viewer.getPickedVertexState().clear();
		nodes.stream().filter(n -> selectedNodeIds.contains(n.getId()))
				.forEach(n -> viewer.getPickedVertexState().pick(n, true));
		popup.setNodeSelectionEnabled(!viewer.getPickedVertexState().getPicked().isEmpty());
	}

	@Override
	public Set<String> getSelectedEdgeIds() {
		return CanvasUtils.getElementIds(getSelectedEdges());
	}

	@Override
	public void setSelectedEdgeIds(Set<String> selectedEdgeIds) {
		setSelectedEdges(CanvasUtils.getElementsById(edges, selectedEdgeIds));
	}

	@Override
	public void setSelectedEdgeIdsWithoutListener(Set<String> selectedEdgeIds) {
		viewer.getPickedEdgeState().clear();
		edges.stream().filter(e -> selectedEdgeIds.contains(e.getId()))
				.forEach(e -> viewer.getPickedEdgeState().pick(e, true));
		popup.setEdgeSelectionEnabled(!viewer.getPickedEdgeState().getPicked().isEmpty());
	}

	@Override
	public HighlightConditionList getNodeHighlightConditions() {
		return nodeHighlightConditions;
	}

	@Override
	public void setNodeHighlightConditions(HighlightConditionList nodeHighlightConditions) {
		this.nodeHighlightConditions = nodeHighlightConditions;
		applyChanges();
		call(l -> l.nodeHighlightingChanged(this));
	}

	@Override
	public HighlightConditionList getEdgeHighlightConditions() {
		return edgeHighlightConditions;
	}

	@Override
	public void setEdgeHighlightConditions(HighlightConditionList edgeHighlightConditions) {
		this.edgeHighlightConditions = edgeHighlightConditions;
		applyChanges();
		call(l -> l.edgeHighlightingChanged(this));
	}

	@Override
	public void setHighlightConditions(HighlightConditionList nodeHighlightConditions,
			HighlightConditionList edgeHighlightConditions) {
		this.nodeHighlightConditions = nodeHighlightConditions;
		this.edgeHighlightConditions = edgeHighlightConditions;
		applyChanges();
		call(l -> l.highlightingChanged(this));
	}

	@Override
	public Map<String, Set<String>> getCollapsedNodes() {
		return collapsedNodes;
	}

	@Override
	public void setCollapsedNodes(Map<String, Set<String>> collapsedNodes) {
		Sets.difference(this.collapsedNodes.keySet(), collapsedNodes.keySet()).forEach(id -> nodeSaveMap.remove(id));
		this.collapsedNodes = collapsedNodes;
		applyChanges();
		call(l -> l.collapsedNodesChanged(this));
	}

	@Override
	public Transform getTransform() {
		return transform;
	}

	@Override
	public void setTransform(Transform transform) {
		this.transform = transform;
		((MutableAffineTransformer) viewer.getRenderContext().getMultiLayerTransformer().getTransformer(Layer.LAYOUT))
				.setTransform(transform.toAffineTransform());
		applyTransform();
		viewer.repaint();
	}

	@Override
	public void transformFinished() {
		call(l -> l.transformChanged(this));
	}

	@Override
	public void pickingFinished() {
		popup.setNodeSelectionEnabled(!viewer.getPickedVertexState().getPicked().isEmpty());
		popup.setEdgeSelectionEnabled(!viewer.getPickedEdgeState().getPicked().isEmpty());
		call(l -> l.selectionChanged(this));
	}

	@Override
	public void nodePickingFinished() {
		popup.setNodeSelectionEnabled(!viewer.getPickedVertexState().getPicked().isEmpty());
		call(l -> l.nodeSelectionChanged(this));
	}

	@Override
	public void edgePickingFinished() {
		popup.setEdgeSelectionEnabled(!viewer.getPickedEdgeState().getPicked().isEmpty());
		call(l -> l.edgeSelectionChanged(this));
	}

	@Override
	public void nodeMovementFinished() {
		call(l -> l.nodePositionsChanged(this));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void modeChangeFinished() {
		optionsPanel.removeChangeListener(this);
		optionsPanel.setEditingMode(((BetterGraphMouse<V, Edge<V>>) viewer.getGraphMouse()).getMode());
		optionsPanel.addChangeListener(this);
	}

	@Override
	public void doubleClickedOn(Object obj) {
		PropertySchema schema = null;

		if (obj instanceof Node) {
			schema = nodeSchema;
		} else if (obj instanceof Edge) {
			schema = edgeSchema;
		}

		if (schema != null) {
			SinglePropertiesDialog dialog = new SinglePropertiesDialog(viewer, (Element) obj, schema);

			dialog.setVisible(true);
		}
	}

	@Override
	public void resetLayoutItemClicked() {
		setTransform(Transform.IDENTITY_TRANSFORM);
		transformFinished();
	}

	@Override
	public void saveAsItemClicked() {
		File file = Dialogs.showImageFileChooser(this);

		if (file == null) {
			return;
		}

		if (file.getName().toLowerCase().endsWith(".png")) {
			try {
				BufferedImage img = new BufferedImage(viewer.getWidth(), viewer.getHeight(),
						BufferedImage.TYPE_INT_RGB);

				getVisualizationServer(false).paint(img.getGraphics());
				ImageIO.write(img, "png", file);
			} catch (IOException e) {
				Dialogs.showErrorMessage(this, "Error saving png file");
			}
		} else if (file.getName().toLowerCase().endsWith(".svg")) {
			try (Writer outsvg = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
				SVGGraphics2D g = new SVGGraphics2D(new SVGDOMImplementation().createDocument(null, "svg", null));

				g.setSVGCanvasSize(new Dimension(viewer.getWidth(), viewer.getHeight()));
				getVisualizationServer(true).paint(g);
				g.stream(outsvg, true);
			} catch (IOException e) {
				Dialogs.showErrorMessage(this, "Error saving svg file");
			}
		}
	}

	@Override
	public void selectConnectionsItemClicked() {
		Set<V> selected = getSelectedNodes();

		edges.stream().filter(e -> selected.contains(e.getFrom()) && selected.contains(e.getTo()))
				.forEach(e -> viewer.getPickedEdgeState().pick(e, true));
		edgePickingFinished();
	}

	@Override
	public void selectIncomingItemClicked() {
		Set<V> selected = getSelectedNodes();

		edges.stream().filter(e -> selected.contains(e.getTo()))
				.forEach(e -> viewer.getPickedEdgeState().pick(e, true));
		edgePickingFinished();
	}

	@Override
	public void selectOutgoingItemClicked() {
		Set<V> selected = getSelectedNodes();

		edges.stream().filter(e -> selected.contains(e.getFrom()))
				.forEach(e -> viewer.getPickedEdgeState().pick(e, true));
		edgePickingFinished();
	}

	@Override
	public void clearSelectedNodesItemClicked() {
		viewer.getPickedVertexState().clear();
		nodePickingFinished();
	}

	@Override
	public void clearSelectedEdgesItemClicked() {
		viewer.getPickedEdgeState().clear();
		edgePickingFinished();
	}

	@Override
	public void highlightSelectedNodesItemClicked() {
		HighlightListDialog dialog = openNodeHighlightDialog();

		dialog.setAutoAddCondition(CanvasUtils.createIdHighlightCondition(getSelectedNodeIds(), nodeSchema.getId()));
		dialog.setVisible(true);

		if (dialog.isApproved()) {
			setNodeHighlightConditions(dialog.getHighlightConditions());
		}
	}

	@Override
	public void highlightSelectedEdgesItemClicked() {
		HighlightListDialog dialog = openEdgeHighlightDialog();

		dialog.setAutoAddCondition(CanvasUtils.createIdHighlightCondition(getSelectedEdgeIds(), edgeSchema.getId()));
		dialog.setVisible(true);

		if (dialog.isApproved()) {
			setEdgeHighlightConditions(dialog.getHighlightConditions());
		}
	}

	@Override
	public void highlightNodesItemClicked() {
		HighlightListDialog dialog = openNodeHighlightDialog();

		dialog.setVisible(true);

		if (dialog.isApproved()) {
			setNodeHighlightConditions(dialog.getHighlightConditions());
		}
	}

	@Override
	public void highlightEdgesItemClicked() {
		HighlightListDialog dialog = openEdgeHighlightDialog();

		dialog.setVisible(true);

		if (dialog.isApproved()) {
			setEdgeHighlightConditions(dialog.getHighlightConditions());
		}
	}

	@Override
	public void clearHighlightedNodesItemClicked() {
		setNodeHighlightConditions(new HighlightConditionList());
	}

	@Override
	public void clearHighlightedEdgesItemClicked() {
		setEdgeHighlightConditions(new HighlightConditionList());
	}

	@Override
	public void selectHighlightedNodesItemClicked() {
		HighlightSelectionDialog dialog = new HighlightSelectionDialog(this, nodeHighlightConditions.getConditions());

		dialog.setVisible(true);

		if (dialog.isApproved()) {
			setSelectedNodes(CanvasUtils.getHighlightedElements(nodes, dialog.getHighlightConditions()));
		}
	}

	@Override
	public void selectHighlightedEdgesItemClicked() {
		HighlightSelectionDialog dialog = new HighlightSelectionDialog(this, edgeHighlightConditions.getConditions());

		dialog.setVisible(true);

		if (dialog.isApproved()) {
			setSelectedEdges(CanvasUtils.getHighlightedElements(edges, dialog.getHighlightConditions()));
		}
	}

	@Override
	public void highlightNodeCategoriesItemClicked() {
		String result = Dialogs.showInputDialog(this, "Select Property with Categories?", "Highlight Categories",
				nodeSchema.getMap().keySet());

		if (result != null) {
			nodeHighlightConditions.getConditions().addAll(CanvasUtils.createCategorialHighlighting(nodes, result));
			setNodeHighlightConditions(nodeHighlightConditions);
		}
	}

	@Override
	public void highlightEdgeCategoriesItemClicked() {
		String result = Dialogs.showInputDialog(this, "Select Property with Categories?", "Highlight Categories",
				edgeSchema.getMap().keySet());

		if (result != null) {
			edgeHighlightConditions.getConditions().addAll(CanvasUtils.createCategorialHighlighting(edges, result));
			setEdgeHighlightConditions(edgeHighlightConditions);
		}
	}

	@Override
	public void selectNodesItemClicked() {
		nodeSchema.getPossibleValues().clear();
		nodeSchema.getPossibleValues().putAll(CanvasUtils.getPossibleValues(nodeSaveMap.values()));

		HighlightDialog dialog = HighlightDialog.createFilterDialog(this, nodeSchema, null,
				createPropertySelectorCreator());

		dialog.setVisible(true);

		if (dialog.isApproved()) {
			setSelectedNodes(CanvasUtils.getHighlightedElements(nodes, Arrays.asList(dialog.getHighlightCondition())));
		}
	}

	@Override
	public void selectEdgesItemClicked() {
		edgeSchema.getPossibleValues().clear();
		edgeSchema.getPossibleValues().putAll(CanvasUtils.getPossibleValues(edgeSaveMap.values()));

		HighlightDialog dialog = HighlightDialog.createFilterDialog(this, edgeSchema, null,
				createPropertySelectorCreator());

		dialog.setVisible(true);

		if (dialog.isApproved()) {
			setSelectedEdges(CanvasUtils.getHighlightedElements(edges, Arrays.asList(dialog.getHighlightCondition())));
		}
	}

	@Override
	public void nodePropertiesItemClicked() {
		PropertiesDialog<V> dialog = PropertiesDialog.createNodeDialog(this, getSelectedNodes(), nodeSchema, true);

		dialog.setVisible(true);
	}

	@Override
	public void edgePropertiesItemClicked() {
		PropertiesDialog<V> dialog = PropertiesDialog.createEdgeDialog(this, getSelectedEdges(), edgeSchema, true);

		dialog.setVisible(true);
	}

	@Override
	public void nodeAllPropertiesItemClicked() {
		Set<V> pickedAll = new LinkedHashSet<>();

		for (V node : getSelectedNodes()) {
			if (collapsedNodes.containsKey(node.getId())) {
				pickedAll.addAll(CanvasUtils.getElementsById(nodeSaveMap, collapsedNodes.get(node.getId())));
			} else {
				pickedAll.add(node);
			}
		}

		PropertiesDialog<V> dialog = PropertiesDialog.createNodeDialog(this, pickedAll, nodeSchema, false);

		dialog.setVisible(true);
	}

	@Override
	public void edgeAllPropertiesItemClicked() {
		Set<Edge<V>> allPicked = new LinkedHashSet<>();

		for (Edge<V> p : getSelectedEdges()) {
			if (joinMap.containsKey(p)) {
				allPicked.addAll(joinMap.get(p));
			} else {
				allPicked.add(p);
			}
		}

		PropertiesDialog<V> dialog = PropertiesDialog.createEdgeDialog(this, allPicked, edgeSchema, false);

		dialog.setVisible(true);
	}

	@Override
	public void collapseToNodeItemClicked() {
		Set<String> selectedIds = getSelectedNodeIds();
		Set<String> selectedMetaIds = new LinkedHashSet<>(Sets.intersection(selectedIds, collapsedNodes.keySet()));

		if (!selectedMetaIds.isEmpty()) {
			String message = "Some of the selected " + naming.nodes() + " are already meta " + naming.nodes()
					+ ".\nCollapse all contained " + naming.nodes() + " into the new meta " + naming.node() + "?";

			if (Dialogs.showOkCancelDialog(this, message, "Confirm") == Dialogs.OkCancelResult.CANCEL) {
				return;
			}
		}

		String newId = null;

		while (true) {
			newId = Dialogs.showInputDialog(this, "Specify ID for Meta " + naming.Node(), naming.Node() + " ID", "");

			if (newId == null) {
				return;
			} else if (!nodeSaveMap.containsKey(newId)) {
				break;
			}

			Dialogs.showErrorMessage(this, "ID already exists, please specify different ID");
		}

		for (String id : selectedMetaIds) {
			selectedIds.remove(id);
			selectedIds.addAll(collapsedNodes.remove(id));
			nodeSaveMap.remove(id);
		}

		collapsedNodes.put(newId, selectedIds);
		applyChanges();
		setSelectedNodeIdsWithoutListener(new LinkedHashSet<>(Arrays.asList(newId)));
		call(l -> l.collapsedNodesAndPickingChanged(this));
	}

	@Override
	public void expandFromNodeItemClicked() {
		Set<String> selectedIds = getSelectedNodeIds();

		if (!Sets.difference(selectedIds, collapsedNodes.keySet()).isEmpty()) {
			Dialogs.showErrorMessage(this, "Some of the selected " + naming.nodes() + " are not collapsed");
			return;
		}

		Set<String> newIds = new LinkedHashSet<>();

		for (String id : selectedIds) {
			newIds.addAll(collapsedNodes.remove(id));
			nodeSaveMap.remove(id);
		}

		applyChanges();
		setSelectedNodeIdsWithoutListener(newIds);
		call(l -> l.collapsedNodesAndPickingChanged(this));
	}

	@Override
	public void collapseByPropertyItemClicked() {
		Set<String> selectedIds = CanvasUtils.getElementIds(getSelectedNodes());
		Set<String> idsToCollapse;

		if (!selectedIds.isEmpty()) {
			switch (Dialogs.showYesNoCancelDialog(this, "Use only the selected " + naming.nodes() + " for collapsing?",
					"Confirm")) {
			case YES:
				idsToCollapse = selectedIds;
				break;
			case NO:
				idsToCollapse = CanvasUtils.getElementIds(getNodes());
				break;
			case CANCEL:
			default:
				return;
			}
		} else {
			idsToCollapse = CanvasUtils.getElementIds(getNodes());
		}

		for (String id : idsToCollapse) {
			if (collapsedNodes.keySet().contains(id)) {
				String message;

				if (idsToCollapse == selectedIds) {
					message = "Some of the selected " + naming.nodes() + " are already collapsed.";
				} else {
					message = "Some of the " + naming.nodes()
							+ " are already collapsed. You have to clear all collapsed " + naming.nodes() + " before.";
				}

				Dialogs.showErrorMessage(this, message);
				return;
			}
		}

		String result = Dialogs.showInputDialog(this, "Select Property for Collapse?", "Collapse by Property",
				nodeSchema.getMap().keySet());

		if (result == null) {
			return;
		}

		SetMultimap<Object, V> nodesByProperty = LinkedHashMultimap.create();

		for (String id : idsToCollapse) {
			V node = nodeSaveMap.get(id);
			Object value = node.getProperties().get(result);

			if (value != null) {
				nodesByProperty.put(value, node);
			}
		}

		ListFilterDialog<Object> dialog = new ListFilterDialog<>(this,
				KnimeUtils.ORDERING.sortedCopy(nodesByProperty.keySet()));

		dialog.setVisible(true);

		if (!dialog.isApproved() || dialog.getFiltered().isEmpty()) {
			return;
		}

		nodesByProperty.keySet().retainAll(dialog.getFiltered());

		Set<String> newCollapsedIds = new LinkedHashSet<>();

		Multimaps.asMap(nodesByProperty).forEach((property, nodes) -> {
			String newId = KnimeUtils.createNewValue(property.toString(), nodeSaveMap.keySet());

			collapsedNodes.put(newId, CanvasUtils.getElementIds(nodes));
			newCollapsedIds.add(newId);
		});

		applyChanges();
		setSelectedNodeIdsWithoutListener(newCollapsedIds);
		call(l -> l.collapsedNodesAndPickingChanged(this));
	}

	@Override
	public void clearCollapsedNodesItemClicked() {
		nodeSaveMap.keySet().removeAll(collapsedNodes.keySet());
		collapsedNodes.clear();
		applyChanges();
		popup.setNodeSelectionEnabled(false);
		call(l -> l.collapsedNodesAndPickingChanged(this));
	}

	@SuppressWarnings("unchecked")
	@Override
	public void editingModeChanged() {
		((BetterGraphMouse<V, Edge<V>>) viewer.getGraphMouse()).setMode(optionsPanel.getEditingMode());
	}

	@Override
	public void showLegendChanged() {
		viewer.repaint();
		call(l -> l.showLegendChanged(this));
	}

	@Override
	public void joinEdgesChanged() {
		applyChanges();
		call(l -> l.edgeJoinChanged(this));
	}

	@Override
	public void skipEdgelessNodesChanged() {
		applyChanges();
		call(l -> l.skipEdgelessChanged(this));
	}

	@Override
	public void showEdgesInMetaNodeChanged() {
		applyChanges();
		call(l -> l.showEdgesInMetaNodeChanged(this));
	}

	@Override
	public void fontChanged() {
		Font font = new Font("default", optionsPanel.isFontBold() ? Font.BOLD : Font.PLAIN, getFontSize());

		viewer.getRenderContext().setEdgeShapeTransformer(new BetterEdgeShapeTransformer<>(getFontSize()));
		viewer.getRenderContext().setVertexFontTransformer(node -> font);
		viewer.getRenderContext().setEdgeFontTransformer(edge -> font);
		viewer.repaint();
		call(l -> l.fontChanged(this));
	}

	@Override
	public void nodeSizeChanged() {
		applyChanges();
		call(l -> l.nodeSizeChanged(this));
	}

	@Override
	public void edgeThicknessChanged() {
		applyChanges();
		call(l -> l.edgeThicknessChanged(this));
	}

	@Override
	public void arrowInMiddleChanged() {
		viewer.getRenderer().getEdgeRenderer().setEdgeArrowRenderingSupport(optionsPanel.isArrowInMiddle()
				? new MiddleEdgeArrowRenderingSupport<>() : new BasicEdgeArrowRenderingSupport<>());
		viewer.repaint();
		call(l -> l.arrowInMiddleChanged(this));
	}

	@Override
	public void labelChanged() {
		viewer.repaint();
		call(l -> l.labelChanged(this));
	}

	@Override
	public void borderAlphaChanged() {
		call(l -> l.borderAlphaChanged(this));
	}

	@Override
	public void avoidOverlayChanged() {
		call(l -> l.avoidOverlayChanged(this));
	}

	@Override
	public BetterVisualizationViewer<V, Edge<V>> getViewer() {
		return viewer;
	}

	@Override
	public VisualizationImageServer<V, Edge<V>> getVisualizationServer(boolean toSvg) {
		VisualizationImageServer<V, Edge<V>> server = new VisualizationImageServer<>(viewer.getGraphLayout(),
				viewer.getSize());

		server.setBackground(Color.WHITE);
		server.setRenderContext(viewer.getRenderContext());
		server.setRenderer(viewer.getRenderer());
		server.addPostRenderPaintable(new PostPaintable());

		return server;
	}

	@Override
	public CanvasOptionsPanel getOptionsPanel() {
		return optionsPanel;
	}

	@Override
	public void setOptionsPanel(CanvasOptionsPanel optionsPanel) {
		if (this.optionsPanel != null) {
			remove(this.optionsPanel);
		}

		this.optionsPanel = optionsPanel;
		optionsPanel.addChangeListener(this);
		add(optionsPanel, BorderLayout.SOUTH);
		revalidate();
	}

	@Override
	public CanvasPopupMenu getPopupMenu() {
		return popup;
	}

	@Override
	public void setPopupMenu(CanvasPopupMenu popup) {
		this.popup = popup;
		popup.addClickListener(this);
		viewer.setComponentPopupMenu(popup);
	}

	@Override
	public void applyChanges() {
		Set<String> selectedNodeIds = getSelectedNodeIds();
		Set<String> selectedEdgeIds = getSelectedEdgeIds();

		resetNodesAndEdges();
		applyNodeCollapse();
		applyInvisibility();
		applyJoinEdgesAndSkipEdgeless();
		applyShowEdgesInMetaNode();
		applyHighlights();
		viewer.getGraphLayout().setGraph(CanvasUtils.createGraph(viewer, nodes, edges));

		setSelectedNodeIdsWithoutListener(selectedNodeIds);
		setSelectedEdgeIdsWithoutListener(selectedEdgeIds);
		viewer.repaint();
	}

	@Override
	public void resetNodesAndEdges() {
		nodes = CanvasUtils.getElementsById(nodeSaveMap, CanvasUtils.getElementIds(allNodes));
		edges = CanvasUtils.getElementsById(edgeSaveMap, CanvasUtils.getElementIds(allEdges));
	}

	@Override
	public void applyNodeCollapse() {
		Map<String, String> collapseTo = new LinkedHashMap<>();

		collapsedNodes.forEach((to, fromList) -> fromList.forEach(from -> collapseTo.put(from, to)));

		Set<V> newNodes = nodes.stream().filter(n -> !collapseTo.containsKey(n.getId()))
				.collect(Collectors.toCollection(LinkedHashSet::new));

		collapsedNodes.forEach((metaId, containedNodes) -> {
			if (nodeSaveMap.containsKey(metaId)) {
				newNodes.add(nodeSaveMap.get(metaId));
			} else {
				V newNode = createMetaNode(metaId, CanvasUtils.getElementsById(nodeSaveMap, containedNodes));

				nodeSaveMap.put(newNode.getId(), newNode);
				newNodes.add(newNode);
			}
		});

		Set<Edge<V>> newEdges = new LinkedHashSet<>();
		Map<String, V> nodesById = CanvasUtils.getElementsById(newNodes);
		Map<String, Edge<V>> allEdgesById = CanvasUtils.getElementsById(allEdges);

		for (Edge<V> edge : edges) {
			String fromId = allEdgesById.get(edge.getId()).getFrom().getId();
			String toId = allEdgesById.get(edge.getId()).getTo().getId();
			V from = nodesById.get(collapseTo.containsKey(fromId) ? collapseTo.get(fromId) : fromId);
			V to = nodesById.get(collapseTo.containsKey(toId) ? collapseTo.get(toId) : toId);

			if (edge.getFrom().equals(from) && edge.getTo().equals(to)) {
				newEdges.add(edge);
			} else {
				Edge<V> newEdge = new Edge<>(edge.getId(), edge.getProperties(), from, to);

				newEdge.getProperties().put(edgeSchema.getFrom(), from.getId());
				newEdge.getProperties().put(edgeSchema.getTo(), to.getId());
				edgeSaveMap.put(newEdge.getId(), newEdge);
				newEdges.add(newEdge);
			}
		}

		nodes = newNodes;
		edges = newEdges;
	}

	@Override
	public void applyInvisibility() {
		CanvasUtils.removeInvisibleElements(nodes, nodeHighlightConditions);
		CanvasUtils.removeInvisibleElements(edges, edgeHighlightConditions);
		CanvasUtils.removeNodelessEdges(edges, nodes);
	}

	@Override
	public void applyJoinEdgesAndSkipEdgeless() {
		joinMap.clear();

		if (isJoinEdges()) {
			joinMap.putAll(CanvasUtils.joinEdges(edges, edgeSchema, allEdges));
			edges.clear();
			edges.addAll(joinMap.keySet());
		}

		if (isSkipEdgelessNodes()) {
			CanvasUtils.removeEdgelessNodes(nodes, edges);
		}
	}

	@Override
	public void applyHighlights() {
		CanvasUtils.applyNodeHighlights(viewer.getRenderContext(), nodes, nodeHighlightConditions, getNodeSize(),
				getNodeMaxSize(), metaNodeProperty);
		CanvasUtils.applyEdgeHighlights(viewer.getRenderContext(), edges, edgeHighlightConditions, getEdgeThickness(),
				getEdgeMaxThickness());
	}

	@Override
	public void applyShowEdgesInMetaNode() {
		if (!isShowEdgesInMetaNode()) {
			Predicate<Edge<V>> isNotInMetaNode = e -> e.getFrom() != e.getTo()
					|| !collapsedNodes.containsKey(e.getFrom().getId());

			edges = edges.stream().filter(isNotInMetaNode).collect(Collectors.toCollection(LinkedHashSet::new));
		}
	}

	protected Map<String, Point2D> getNodePositions(Collection<V> nodes) {
		Map<String, Point2D> map = new LinkedHashMap<>();
		Layout<V, Edge<V>> layout = viewer.getGraphLayout();

		for (V node : nodes) {
			Point2D pos = layout.transform(node);

			if (pos != null) {
				map.put(node.getId(), new Point2D.Double(pos.getX(), pos.getY()));
			}
		}

		return map;
	}

	protected HighlightListDialog openNodeHighlightDialog() {
		nodeSchema.getPossibleValues().clear();
		nodeSchema.getPossibleValues().putAll(CanvasUtils.getPossibleValues(nodeSaveMap.values()));

		HighlightListDialog dialog = new HighlightListDialog(this, nodeSchema, nodeHighlightConditions);

		dialog.setAllowShape(true);
		dialog.setSelectorCreator(createPropertySelectorCreator());

		return dialog;
	}

	protected HighlightListDialog openEdgeHighlightDialog() {
		edgeSchema.getPossibleValues().clear();
		edgeSchema.getPossibleValues().putAll(CanvasUtils.getPossibleValues(edgeSaveMap.values()));

		HighlightListDialog dialog = new HighlightListDialog(this, edgeSchema, edgeHighlightConditions);

		dialog.setSelectorCreator(createPropertySelectorCreator());
		dialog.addChecker(condition -> {
			if (isJoinEdges() && condition != null && condition.isInvisible()
					&& CanvasUtils.getUsedProperties(condition).contains(edgeSchema.getId())) {
				return "Joined " + naming.edges() + " cannot be made invisible.\nYou can uncheck \"Join "
						+ naming.Edges() + "\" and make the unjoined " + naming.edges() + " invisible.";
			}

			return null;
		});

		return dialog;
	}

	protected PropertySelectorCreator createPropertySelectorCreator() {
		return new DefaultPropertySelectorCreator();
	}

	protected BetterPickingGraphMousePlugin<V, Edge<V>> createPickingPlugin() {
		return new BetterPickingGraphMousePlugin<>(true);
	}

	protected BetterScalingGraphMousePlugin createScalingPlugin() {
		return new BetterScalingGraphMousePlugin(1 / 1.1f, 1.1f);
	}

	protected ZoomingPaintable createZoomingPaintable() {
		ZoomingPaintable zoom = new ZoomingPaintable(viewer, 1.2);

		zoom.addChangeListener(this);

		return zoom;
	}

	protected abstract void applyTransform();

	protected abstract V createMetaNode(String id, Collection<V> nodes);

	private void call(Consumer<CanvasListener> action) {
		Stream.of(getListeners(CanvasListener.class)).forEach(action);
	}

	private class PostPaintable implements Paintable {

		@Override
		public boolean useTransform() {
			return false;
		}

		@Override
		public void paint(Graphics graphics) {
			Graphics2D g = (Graphics2D) graphics;

			if (!Strings.isNullOrEmpty(getLabel())) {
				paintLabel(g);
			}

			if (optionsPanel.isShowLegend()) {
				new CanvasLegend<>(Canvas.this, nodeHighlightConditions, nodes, edgeHighlightConditions, edges)
						.paint(g);
			}

			Color currentColor = g.getColor();

			g.setColor(Color.BLACK);
			g.drawRect(0, 0, getCanvasSize().width - 1, getCanvasSize().height - 1);
			g.setColor(currentColor);
		}

		private void paintLabel(Graphics2D g) {
			int w = getCanvasSize().width;
			Font font = new Font("Default", Font.BOLD, 10);
			int fontHeight = g.getFontMetrics(font).getHeight();
			int fontAscent = g.getFontMetrics(font).getAscent();
			int dy = 2;

			int dx = 5;
			int sw = (int) font.getStringBounds(getLabel(), g.getFontRenderContext()).getWidth();
			Color currentColor = g.getColor();
			Font currentFont = g.getFont();

			g.setColor(ZoomingPaintable.BACKGROUND);
			g.fillRect(w - sw - 2 * dx, -1, sw + 2 * dx, fontHeight + 2 * dy);
			g.setColor(Color.BLACK);
			g.drawRect(w - sw - 2 * dx, -1, sw + 2 * dx, fontHeight + 2 * dy);
			g.setFont(font);
			g.drawString(getLabel(), w - sw - dx, dy + fontAscent);

			g.setColor(currentColor);
			g.setFont(currentFont);
		}
	}
}
