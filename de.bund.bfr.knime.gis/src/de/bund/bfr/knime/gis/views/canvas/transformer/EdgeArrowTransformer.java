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
package de.bund.bfr.knime.gis.views.canvas.transformer;

import java.awt.Shape;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.collections15.Transformer;

import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.Node;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.util.Context;
import edu.uci.ics.jung.visualization.util.ArrowFactory;

public class EdgeArrowTransformer<V extends Node> implements
		Transformer<Context<Graph<V, Edge<V>>, Edge<V>>, Shape> {

	private Map<Edge<V>, Double> thicknessValues;

	public EdgeArrowTransformer(Map<Edge<V>, Double> thicknessValues) {
		this.thicknessValues = thicknessValues;

		if (!thicknessValues.isEmpty()) {
			double max = Collections.max(thicknessValues.values());

			if (max != 0.0) {
				for (Edge<V> edge : thicknessValues.keySet()) {
					thicknessValues.put(edge, thicknessValues.get(edge) / max);
				}
			}
		}
	}

	@Override
	public Shape transform(Context<Graph<V, Edge<V>>, Edge<V>> edge) {
		int t1 = (int) Math.round(thicknessValues.get(edge.element) * 20.0);
		int t2 = (int) Math.round(thicknessValues.get(edge.element) * 10.0);

		return ArrowFactory.getNotchedArrow(t1 + 8, t2 + 10, 4);
	}
}
