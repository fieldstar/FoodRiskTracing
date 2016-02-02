/*******************************************************************************
 * Copyright (c) 2016 Federal Institute for Risk Assessment (BfR), Germany
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.TexturePaint;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.commons.collections15.Transformer;
import org.knime.base.data.xml.SvgCell;
import org.knime.base.data.xml.SvgImageContent;
import org.knime.core.data.image.png.PNGImageContent;
import org.knime.core.node.port.image.ImagePortObject;
import org.knime.core.node.port.image.ImagePortObjectSpec;
import org.w3c.dom.svg.SVGDocument;

import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import de.bund.bfr.knime.KnimeUtils;
import de.bund.bfr.knime.Pair;
import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.Element;
import de.bund.bfr.knime.gis.views.canvas.element.Node;
import de.bund.bfr.knime.gis.views.canvas.highlighting.AndOrHighlightCondition;
import de.bund.bfr.knime.gis.views.canvas.highlighting.HighlightCondition;
import de.bund.bfr.knime.gis.views.canvas.highlighting.HighlightConditionList;
import de.bund.bfr.knime.gis.views.canvas.highlighting.LogicalHighlightCondition;
import de.bund.bfr.knime.gis.views.canvas.highlighting.LogicalValueHighlightCondition;
import de.bund.bfr.knime.gis.views.canvas.highlighting.ValueHighlightCondition;
import de.bund.bfr.knime.gis.views.canvas.jung.BetterDirectedSparseMultigraph;
import de.bund.bfr.knime.gis.views.canvas.util.CanvasTransformers;
import de.bund.bfr.knime.gis.views.canvas.util.EdgePropertySchema;
import de.bund.bfr.knime.gis.views.canvas.util.PropertySchema;
import de.bund.bfr.knime.gis.views.canvas.util.Transform;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.Context;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.VisualizationImageServer;

public class CanvasUtils {

	public static final Color LEGEND_BACKGROUND = new Color(230, 230, 230);

	private static final int NODE_TEXTURE_SIZE = 3;
	private static final int EDGE_TEXTURE_SIZE = 5;
	private static final Color[] COLORS = new Color[] { new Color(255, 85, 85), new Color(85, 85, 255),
			new Color(85, 255, 85), new Color(255, 85, 255), new Color(85, 255, 255), new Color(255, 175, 175),
			new Color(128, 128, 128), new Color(192, 0, 0), new Color(0, 0, 192), new Color(0, 192, 0),
			new Color(192, 192, 0), new Color(192, 0, 192), new Color(0, 192, 192), new Color(64, 64, 64),
			new Color(255, 64, 64), new Color(64, 64, 255), new Color(64, 255, 64), new Color(255, 64, 255),
			new Color(64, 255, 255), new Color(192, 192, 192), new Color(128, 0, 0), new Color(0, 0, 128),
			new Color(0, 128, 0), new Color(128, 128, 0), new Color(128, 0, 128), new Color(0, 128, 128),
			new Color(255, 128, 128), new Color(128, 128, 255), new Color(128, 255, 128), new Color(255, 128, 255),
			new Color(128, 255, 255) };

	private CanvasUtils() {
	}

	public static double toPositiveDouble(Object value) {
		if (value instanceof Number) {
			double d = ((Number) value).doubleValue();

			return Double.isFinite(d) && d >= 0.0 ? d : 0.0;
		}

		return 0.0;
	}

	public static Transform getTransformForBounds(Dimension canvasSize, Rectangle2D bounds, Double zoomStep) {
		double widthRatio = canvasSize.width / bounds.getWidth();
		double heightRatio = canvasSize.height / bounds.getHeight();
		double canvasCenterX = canvasSize.width / 2.0;
		double canvasCenterY = canvasSize.height / 2.0;
		double centerX = bounds.getCenterX();
		double centerY = bounds.getCenterY();

		double scale = Math.min(widthRatio, heightRatio);

		if (zoomStep != null) {
			int zoom = (int) (Math.log(scale) / Math.log(2.0));

			scale = Math.pow(2.0, zoom);
		}

		double scaleX = scale;
		double scaleY = scale;
		double translationX = canvasCenterX - centerX * scaleX;
		double translationY = canvasCenterY - centerY * scaleY;

		return new Transform(scaleX, scaleY, translationX, translationY);
	}

	public static List<HighlightCondition> createCategorialHighlighting(Collection<? extends Element> elements,
			String property) {
		Set<Object> categories = elements.stream().map(e -> e.getProperties().get(property)).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		List<HighlightCondition> conditions = new ArrayList<>();
		int index = 0;

		for (Object category : KnimeUtils.ORDERING.sortedCopy(categories)) {
			Color color = COLORS[index++ % COLORS.length];
			LogicalHighlightCondition condition = new LogicalHighlightCondition(property,
					LogicalHighlightCondition.EQUAL_TYPE, category.toString());

			conditions.add(new AndOrHighlightCondition(condition, property + " = " + category, true, color, false,
					false, null));
		}

		return conditions;
	}

	@SuppressWarnings("unchecked")
	public static <V extends Node> void copyNodesAndEdges(Collection<V> nodes, Collection<Edge<V>> edges,
			Collection<V> newNodes, Collection<Edge<V>> newEdges) {
		Map<String, V> nodesById = new LinkedHashMap<>();

		for (V node : nodes) {
			V newNode = (V) node.copy();

			nodesById.put(node.getId(), newNode);
			newNodes.add(newNode);
		}

		for (Edge<V> edge : edges) {
			newEdges.add(new Edge<>(edge.getId(), new LinkedHashMap<>(edge.getProperties()),
					nodesById.get(edge.getFrom().getId()), nodesById.get(edge.getTo().getId())));
		}
	}

	public static <V extends Node> Map<Edge<V>, Set<Edge<V>>> joinEdges(Collection<Edge<V>> edges,
			EdgePropertySchema properties, Set<String> usedIds) {
		SetMultimap<Pair<V, V>, Edge<V>> edgeMap = LinkedHashMultimap.create();

		for (Edge<V> edge : edges) {
			edgeMap.put(new Pair<>(edge.getFrom(), edge.getTo()), edge);
		}

		Map<Edge<V>, Set<Edge<V>>> joined = new LinkedHashMap<>();
		int index = 0;

		for (Map.Entry<Pair<V, V>, Set<Edge<V>>> entry : Multimaps.asMap(edgeMap).entrySet()) {
			V from = entry.getKey().getFirst();
			V to = entry.getKey().getSecond();
			Map<String, Object> prop = new LinkedHashMap<>();

			for (Edge<V> edge : entry.getValue()) {
				CanvasUtils.addMapToMap(prop, properties, edge.getProperties());
			}

			while (!usedIds.add(index + "")) {
				index++;
			}

			prop.put(properties.getId(), index + "");
			prop.put(properties.getFrom(), from.getId());
			prop.put(properties.getTo(), to.getId());
			joined.put(new Edge<>(index + "", prop, from, to), entry.getValue());
		}

		return joined;
	}

	public static void addMapToMap(Map<String, Object> map, PropertySchema schema, Map<String, Object> addMap) {
		for (String property : schema.getMap().keySet()) {
			addObjectToMap(map, property, schema.getMap().get(property), addMap.get(property));
		}
	}

	public static void addObjectToMap(Map<String, Object> map, String property, Class<?> type, Object obj) {
		if (type == String.class) {
			String value = (String) obj;

			if (map.containsKey(property)) {
				if (map.get(property) == null || !map.get(property).equals(value)) {
					map.put(property, null);
				}
			} else {
				map.put(property, value);
			}
		} else if (type == Integer.class) {
			Integer value = (Integer) obj;

			if (map.get(property) != null) {
				if (value != null) {
					map.put(property, (Integer) map.get(property) + value);
				}
			} else {
				map.put(property, value);
			}
		} else if (type == Double.class) {
			Double value = (Double) obj;

			if (map.get(property) != null) {
				if (value != null) {
					map.put(property, (Double) map.get(property) + value);
				}
			} else {
				map.put(property, value);
			}
		} else if (type == Boolean.class) {
			Boolean value = (Boolean) obj;

			if (map.containsKey(property)) {
				if (map.get(property) == null || !map.get(property).equals(value)) {
					map.put(property, null);
				}
			} else {
				map.put(property, value);
			}
		}
	}

	public static <T extends Element> Set<T> getHighlightedElements(Collection<T> elements,
			List<HighlightCondition> highlightConditions) {
		Set<T> highlightedElements = new LinkedHashSet<>();

		for (HighlightCondition condition : highlightConditions) {
			condition.getValues(elements).entrySet().stream().filter(e -> e.getValue() != 0.0)
					.forEach(e -> highlightedElements.add(e.getKey()));
		}

		return highlightedElements;
	}

	public static Set<String> getElementIds(Collection<? extends Element> elements) {
		return elements.stream().map(e -> e.getId()).collect(Collectors.toSet());
	}

	public static <T extends Element> Set<T> getElementsById(Collection<T> elements, Set<String> ids) {
		return elements.stream().filter(e -> ids.contains(e.getId())).collect(Collectors.toSet());
	}

	public static <T extends Element> Map<String, T> getElementsById(Collection<T> elements) {
		return elements.stream().collect(Collectors.toMap(e -> e.getId(), e -> e));
	}

	public static <T> Set<T> getElementsById(Map<String, T> elements, Collection<String> ids) {
		return ids.stream().map(id -> elements.get(id)).filter(Objects::nonNull).collect(Collectors.toSet());
	}

	public static Map<String, Set<String>> getPossibleValues(Collection<? extends Element> elements) {
		SetMultimap<String, String> values = LinkedHashMultimap.create();

		for (Element e : elements) {
			for (Map.Entry<String, Object> entry : e.getProperties().entrySet()) {
				if (entry.getValue() instanceof Boolean) {
					values.putAll(entry.getKey(), Arrays.asList(Boolean.FALSE.toString(), Boolean.TRUE.toString()));
				} else if (entry.getValue() != null) {
					values.put(entry.getKey(), entry.getValue().toString());
				}
			}
		}

		return Multimaps.asMap(values);
	}

	public static AndOrHighlightCondition createIdHighlightCondition(Collection<String> ids, String idProperty) {
		List<List<LogicalHighlightCondition>> conditions = new ArrayList<>();

		for (String id : ids) {
			LogicalHighlightCondition c = new LogicalHighlightCondition(idProperty,
					LogicalHighlightCondition.EQUAL_TYPE, id);

			conditions.add(Arrays.asList(c));
		}

		return new AndOrHighlightCondition(conditions, null, false, Color.RED, false, false, null);
	}

	public static Set<String> getUsedProperties(HighlightCondition condition) {
		AndOrHighlightCondition logicalCondition = null;
		ValueHighlightCondition valueCondition = null;

		if (condition instanceof AndOrHighlightCondition) {
			logicalCondition = (AndOrHighlightCondition) condition;
		} else if (condition instanceof ValueHighlightCondition) {
			valueCondition = (ValueHighlightCondition) condition;
		} else if (condition instanceof LogicalValueHighlightCondition) {
			logicalCondition = ((LogicalValueHighlightCondition) condition).getLogicalCondition();
			valueCondition = ((LogicalValueHighlightCondition) condition).getValueCondition();
		}

		Set<String> properties = new LinkedHashSet<>();

		if (logicalCondition != null) {
			logicalCondition.getConditions().stream().flatMap(List::stream)
					.forEach(c -> properties.add(c.getProperty()));
		}

		if (valueCondition != null) {
			properties.add(valueCondition.getProperty());
		}

		return properties;
	}

	public static <V extends Node> void applyNodeHighlights(RenderContext<V, Edge<V>> renderContext,
			Collection<V> nodes, HighlightConditionList nodeHighlightConditions, int nodeSize, Integer nodeMaxSize) {
		applyNodeHighlights(renderContext, nodes, nodeHighlightConditions, nodeSize, nodeMaxSize, false);
	}

	public static <V extends Node> void applyNodeLabels(RenderContext<V, Edge<V>> renderContext, Collection<V> nodes,
			HighlightConditionList nodeHighlightConditions) {
		applyNodeHighlights(renderContext, nodes, nodeHighlightConditions, 0, null, true);
	}

	private static <V extends Node> void applyNodeHighlights(RenderContext<V, Edge<V>> renderContext,
			Collection<V> nodes, HighlightConditionList nodeHighlightConditions, int nodeSize, Integer nodeMaxSize,
			boolean labelsOnly) {
		List<Color> colors = new ArrayList<>();
		ListMultimap<V, Double> alphaValues = ArrayListMultimap.create();
		Map<V, Double> thicknessValues = new LinkedHashMap<>();
		SetMultimap<V, String> labelLists = LinkedHashMultimap.create();
		boolean prioritize = nodeHighlightConditions.isPrioritizeColors();

		if (!labelsOnly) {
			nodes.forEach(n -> thicknessValues.put(n, 0.0));
		}

		for (HighlightCondition condition : nodeHighlightConditions.getConditions()) {
			if (condition.isInvisible()) {
				continue;
			}

			Map<V, Double> values = condition.getValues(nodes);

			if (!labelsOnly && condition.isUseThickness()) {
				nodes.forEach(n -> thicknessValues.put(n, thicknessValues.get(n) + values.get(n)));
			}

			if (!labelsOnly && condition.getColor() != null) {
				colors.add(condition.getColor());

				for (V node : nodes) {
					List<Double> alphas = alphaValues.get(node);

					if (!prioritize || alphas.isEmpty() || Collections.max(alphas) == 0.0) {
						alphas.add(values.get(node));
					} else {
						alphas.add(0.0);
					}
				}
			}

			if (condition.getLabelProperty() != null) {
				String property = condition.getLabelProperty();

				for (V node : nodes) {
					if (values.get(node) != 0.0 && node.getProperties().get(property) != null) {
						labelLists.put(node, node.getProperties().get(property).toString());
					}
				}
			}
		}

		Map<V, String> labels = new LinkedHashMap<>();

		for (Map.Entry<V, Set<String>> entry : Multimaps.asMap(labelLists).entrySet()) {
			labels.put(entry.getKey(), Joiner.on("/").join(entry.getValue()));
		}

		if (!labelsOnly) {
			renderContext.setVertexShapeTransformer(
					CanvasTransformers.nodeShapeTransformer(nodeSize, nodeMaxSize, thicknessValues));
			renderContext.setVertexFillPaintTransformer(
					CanvasTransformers.nodeFillTransformer(renderContext, Multimaps.asMap(alphaValues), colors));
		}

		renderContext.setVertexLabelTransformer(node -> labels.get(node));
	}

	public static <V extends Node> void applyEdgeHighlights(RenderContext<V, Edge<V>> renderContext,
			Collection<Edge<V>> edges, HighlightConditionList edgeHighlightConditions, int edgeThickness,
			Integer edgeMaxThickness) {
		List<Color> colors = new ArrayList<>();
		ListMultimap<Edge<V>, Double> alphaValues = ArrayListMultimap.create();
		Map<Edge<V>, Double> thicknessValues = new LinkedHashMap<>();
		SetMultimap<Edge<V>, String> labelLists = LinkedHashMultimap.create();
		boolean prioritize = edgeHighlightConditions.isPrioritizeColors();

		edges.forEach(e -> thicknessValues.put(e, 0.0));

		for (HighlightCondition condition : edgeHighlightConditions.getConditions()) {
			if (condition.isInvisible()) {
				continue;
			}

			Map<Edge<V>, Double> values = condition.getValues(edges);

			if (condition.getColor() != null) {
				colors.add(condition.getColor());

				for (Edge<V> edge : edges) {
					List<Double> alphas = alphaValues.get(edge);

					if (!prioritize || alphas.isEmpty() || Collections.max(alphas) == 0.0) {
						alphas.add(values.get(edge));
					} else {
						alphas.add(0.0);
					}
				}
			}

			if (condition.isUseThickness()) {
				edges.forEach(e -> thicknessValues.put(e, thicknessValues.get(e) + values.get(e)));
			}

			if (condition.getLabelProperty() != null) {
				String property = condition.getLabelProperty();

				for (Edge<V> edge : edges) {
					if (values.get(edge) != 0.0 && edge.getProperties().get(property) != null) {
						labelLists.put(edge, edge.getProperties().get(property).toString());
					}
				}
			}
		}

		Pair<Transformer<Edge<V>, Stroke>, Transformer<Context<Graph<V, Edge<V>>, Edge<V>>, Shape>> strokeAndArrowTransformers = CanvasTransformers
				.edgeStrokeArrowTransformers(edgeThickness, edgeMaxThickness, thicknessValues);
		Map<Edge<V>, String> labels = new LinkedHashMap<>();

		for (Map.Entry<Edge<V>, Set<String>> entry : Multimaps.asMap(labelLists).entrySet()) {
			labels.put(entry.getKey(), Joiner.on("/").join(entry.getValue()));
		}

		renderContext.setEdgeDrawPaintTransformer(
				CanvasTransformers.edgeDrawTransformer(renderContext, Multimaps.asMap(alphaValues), colors));
		renderContext.setEdgeStrokeTransformer(strokeAndArrowTransformers.getFirst());
		renderContext.setEdgeArrowTransformer(strokeAndArrowTransformers.getSecond());
		renderContext.setEdgeLabelTransformer(edge -> labels.get(edge));
	}

	public static Paint mixColors(Color backgroundColor, List<Color> colors, List<Double> alphas, boolean forEdges) {
		double rb = backgroundColor.getRed() / 255.0;
		double gb = backgroundColor.getGreen() / 255.0;
		double bb = backgroundColor.getBlue() / 255.0;
		double ab = backgroundColor.getAlpha() / 255.0;
		List<Color> cs = new ArrayList<>();

		for (int i = 0; i < colors.size(); i++) {
			double alpha = alphas.get(i);

			if (alpha > 0.0) {
				double r = colors.get(i).getRed() / 255.0 * alpha + rb * (1 - alpha);
				double g = colors.get(i).getGreen() / 255.0 * alpha + gb * (1 - alpha);
				double b = colors.get(i).getBlue() / 255.0 * alpha + bb * (1 - alpha);
				double a = colors.get(i).getAlpha() / 255.0 * alpha + ab * (1 - alpha);

				cs.add(new Color((float) r, (float) g, (float) b, (float) a));
			}
		}

		if (cs.isEmpty()) {
			return backgroundColor;
		} else if (cs.size() == 1) {
			return cs.get(0);
		}

		BufferedImage img;
		int size = cs.size() * (forEdges ? EDGE_TEXTURE_SIZE : NODE_TEXTURE_SIZE);

		if (forEdges) {
			img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

			for (int i = 0; i < size; i++) {
				for (int j = 0; j < size; j++) {
					img.setRGB(i, j, cs.get((i / EDGE_TEXTURE_SIZE + j / EDGE_TEXTURE_SIZE) % cs.size()).getRGB());
				}
			}
		} else {
			img = new BufferedImage(size, 1, BufferedImage.TYPE_INT_ARGB);

			for (int i = 0; i < size; i++) {
				img.setRGB(i, 0, cs.get(i / NODE_TEXTURE_SIZE).getRGB());
			}
		}

		return new TexturePaint(img, new Rectangle(img.getWidth(), img.getHeight()));
	}

	public static void drawImageWithAlpha(Graphics2D g, BufferedImage img, int alpha) {
		float[] edgeScales = { 1f, 1f, 1f, alpha / 255.0f };
		float[] edgeOffsets = new float[4];

		g.drawImage(img, new RescaleOp(edgeScales, edgeOffsets, null), 0, 0);
	}

	public static <T extends Element> Set<T> removeInvisibleElements(Set<T> elements,
			HighlightConditionList highlightConditions) {
		Set<T> removed = new LinkedHashSet<>();

		highlightConditions.getConditions().stream().filter(c -> c.isInvisible()).forEach(c -> {
			Set<T> toRemove = c.getValues(elements).entrySet().stream().filter(e -> e.getValue() != 0.0)
					.map(e -> e.getKey()).collect(Collectors.toSet());

			elements.removeAll(toRemove);
			removed.addAll(toRemove);
		});

		return removed;
	}

	public static <V extends Node> Set<Edge<V>> removeNodelessEdges(Set<Edge<V>> edges, Set<V> nodes) {
		Set<Edge<V>> removed = edges.stream().filter(e -> !nodes.contains(e.getFrom()) || !nodes.contains(e.getTo()))
				.collect(Collectors.toSet());

		edges.removeAll(removed);

		return removed;
	}

	public static <V extends Node> Set<V> removeEdgelessNodes(Set<V> nodes, Set<Edge<V>> edges) {
		Set<V> nodesWithEdges = new LinkedHashSet<>();

		for (Edge<V> edge : edges) {
			nodesWithEdges.add(edge.getFrom());
			nodesWithEdges.add(edge.getTo());
		}

		Set<V> removed = new LinkedHashSet<>(Sets.difference(nodes, nodesWithEdges));

		nodes.removeAll(removed);

		return removed;
	}

	public static <V extends Node> Graph<V, Edge<V>> createGraph(Collection<V> nodes, Collection<Edge<V>> edges) {
		Graph<V, Edge<V>> graph = new BetterDirectedSparseMultigraph<>();

		nodes.forEach(n -> graph.addVertex(n));
		edges.forEach(e -> graph.addEdge(e, e.getFrom(), e.getTo()));

		return graph;
	}

	public static BufferedImage getBufferedImage(ICanvas<?>... canvas) {
		int width = Math.max(Stream.of(canvas).mapToInt(c -> c.getCanvasSize().width).sum(), 1);
		int height = Stream.of(canvas).mapToInt(c -> c.getCanvasSize().height).max().orElse(1);
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		int x = 0;

		for (ICanvas<?> c : canvas) {
			VisualizationImageServer<?, ?> server = c.getVisualizationServer(false);

			g.translate(x, 0);
			server.paint(g);
			x += c.getCanvasSize().width;
		}

		return img;
	}

	public static SVGDocument getSvgDocument(ICanvas<?>... canvas) {
		int width = Math.max(Stream.of(canvas).mapToInt(c -> c.getCanvasSize().width).sum(), 1);
		int height = Stream.of(canvas).mapToInt(c -> c.getCanvasSize().height).max().orElse(1);
		SVGDocument document = (SVGDocument) new SVGDOMImplementation().createDocument(null, "svg", null);
		SVGGraphics2D g = new SVGGraphics2D(document);
		int x = 0;

		g.setSVGCanvasSize(new Dimension(width, height));

		for (ICanvas<?> c : canvas) {
			VisualizationImageServer<?, ?> server = c.getVisualizationServer(true);

			g.translate(x, 0);
			server.paint(g);
			x += c.getCanvasSize().width;
		}

		g.dispose();
		document.replaceChild(g.getRoot(), document.getDocumentElement());

		return document;
	}

	public static ImagePortObject getImage(boolean asSvg, ICanvas<?>... canvas) throws IOException {
		if (asSvg) {
			return new ImagePortObject(new SvgImageContent(CanvasUtils.getSvgDocument(canvas)),
					new ImagePortObjectSpec(SvgCell.TYPE));
		} else {
			try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				ImageIO.write(CanvasUtils.getBufferedImage(canvas), "png", out);

				return new ImagePortObject(new PNGImageContent(out.toByteArray()),
						new ImagePortObjectSpec(PNGImageContent.TYPE));
			}
		}
	}

	public static ImagePortObjectSpec getImageSpec(boolean asSvg) {
		return new ImagePortObjectSpec(asSvg ? SvgCell.TYPE : PNGImageContent.TYPE);
	}
}
