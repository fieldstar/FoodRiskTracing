/*******************************************************************************
 * Copyright (c) 2015 Federal Institute for Risk Assessment (BfR), Germany
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
package de.bund.bfr.knime.gis.views.canvas.jung.layout;

import java.awt.Dimension;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections15.Factory;
import org.apache.commons.collections15.map.LazyMap;

import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.algorithms.layout.util.RandomLocationTransformer;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.Pair;

public class FRLayout<V, E> extends AbstractLayout<V, E> {

	private static final double EPSILON = 0.000001;
	private static final double ATTRACTION_MULTIPLIER = 0.75;
	private static final double REPULSION_MULTIPLIER = 0.75;
	private static final int MAX_ITERATIONS = 700;

	private double forceConstant;
	private double temperature;
	private int currentIteration;
	private Map<V, FRVertexData> frVertexData;
	private double attraction_constant;
	private double repulsion_constant;
	private double max_dimension;

	public FRLayout(Graph<V, E> g) {
		super(g);

		frVertexData = LazyMap.decorate(new HashMap<V, FRVertexData>(), new Factory<FRVertexData>() {

			@Override
			public FRVertexData create() {
				return new FRVertexData();
			}
		});
	}

	@Override
	public void setSize(Dimension size) {
		if (!initialized) {
			setInitializer(new RandomLocationTransformer<V>(size));
		}

		super.setSize(size);
		max_dimension = Math.max(size.height, size.width);
	}

	@Override
	public void initialize() {
		Dimension d = getSize();

		currentIteration = 0;
		temperature = d.getWidth() / 10;
		forceConstant = Math.sqrt(d.getHeight() * d.getWidth() / getGraph().getVertexCount());
		attraction_constant = ATTRACTION_MULTIPLIER * forceConstant;
		repulsion_constant = REPULSION_MULTIPLIER * forceConstant;

		while (!done()) {
			step();
		}
	}

	@Override
	public void reset() {
		initialize();
	}

	public boolean done() {
		return currentIteration > MAX_ITERATIONS || temperature < 1.0 / max_dimension;
	}

	public void step() {
		currentIteration++;

		for (V v1 : getGraph().getVertices()) {
			calcRepulsion(v1);
		}

		for (E e : getGraph().getEdges()) {
			calcAttraction(e);
		}

		for (V v : getGraph().getVertices()) {
			if (!isLocked(v)) {
				calcPositions(v);
			}
		}

		cool();
	}

	private void calcPositions(V v) {
		FRVertexData fvd = frVertexData.get(v);

		if (fvd == null) {
			return;
		}

		Point2D xyd = transform(v);
		double deltaLength = Math.max(EPSILON, fvd.norm());
		double newXDisp = fvd.getX() / deltaLength * Math.min(deltaLength, temperature);

		if (Double.isNaN(newXDisp)) {
			throw new IllegalArgumentException("Unexpected mathematical result in FRLayout:calcPositions [xdisp]");
		}

		double newYDisp = fvd.getY() / deltaLength * Math.min(deltaLength, temperature);

		xyd.setLocation(xyd.getX() + newXDisp, xyd.getY() + newYDisp);
	}

	private void calcAttraction(E e) {
		Pair<V> endpoints = getGraph().getEndpoints(e);
		V v1 = endpoints.getFirst();
		V v2 = endpoints.getSecond();
		boolean v1_locked = isLocked(v1);
		boolean v2_locked = isLocked(v2);

		if (v1_locked && v2_locked) {
			return;
		}

		Point2D p1 = transform(v1);
		Point2D p2 = transform(v2);

		if (p1 == null || p2 == null) {
			return;
		}

		double xDelta = p1.getX() - p2.getX();
		double yDelta = p1.getY() - p2.getY();
		double deltaLength = Math.max(EPSILON, Math.sqrt((xDelta * xDelta) + (yDelta * yDelta)));
		double force = (deltaLength * deltaLength) / attraction_constant;

		if (Double.isNaN(force)) {
			throw new IllegalArgumentException("Unexpected mathematical result in FRLayout:calcPositions [force]");
		}

		double dx = (xDelta / deltaLength) * force;
		double dy = (yDelta / deltaLength) * force;

		if (!v1_locked) {
			frVertexData.get(v1).offset(-dx, -dy);
		}

		if (!v2_locked) {
			frVertexData.get(v2).offset(dx, dy);
		}
	}

	private void calcRepulsion(V v1) {
		FRVertexData fvd = frVertexData.get(v1);

		if (fvd == null) {
			return;
		}

		fvd.setLocation(0, 0);

		for (V v2 : getGraph().getVertices()) {
			if (v1 == v2) {
				continue;
			}

			Point2D p1 = transform(v1);
			Point2D p2 = transform(v2);

			if (p1 == null || p2 == null) {
				continue;
			}

			double xDelta = p1.getX() - p2.getX();
			double yDelta = p1.getY() - p2.getY();

			double deltaLength = Math.max(EPSILON, Math.sqrt((xDelta * xDelta) + (yDelta * yDelta)));

			double force = (repulsion_constant * repulsion_constant) / deltaLength;

			if (Double.isNaN(force)) {
				throw new RuntimeException("Unexpected mathematical result in FRLayout:calcPositions [repulsion]");
			}

			fvd.offset((xDelta / deltaLength) * force, (yDelta / deltaLength) * force);
		}
	}

	private void cool() {
		temperature *= (1.0 - currentIteration / (double) MAX_ITERATIONS);
	}

	private static class FRVertexData extends Point2D.Double {

		private static final long serialVersionUID = 1L;

		public void offset(double x, double y) {
			this.x += x;
			this.y += y;
		}

		public double norm() {
			return Math.sqrt(x * x + y * y);
		}
	}
}
