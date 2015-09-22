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
package de.bund.bfr.knime.openkrise;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;

import de.bund.bfr.knime.openkrise.common.Delivery;

public class Tracing {

	private static enum ScoreType {
		COMBINED, POSITIVE, NEGATIVE
	}

	private List<Delivery> deliveries;
	private Map<String, Double> stationWeights;
	private Map<String, Double> deliveryWeights;
	private Set<String> ccStations;
	private Set<String> ccDeliveries;
	private Set<String> killContaminationStations;
	private Set<String> killContaminationDeliveries;

	private transient Map<String, Delivery> deliveryMap;
	private transient SetMultimap<String, String> incomingDeliveries;
	private transient SetMultimap<String, String> outgoingDeliveries;
	private transient Map<String, Set<String>> backwardDeliveries;
	private transient Map<String, Set<String>> forwardDeliveries;
	private transient double positiveWeightSum;
	private transient double negativeWeightSum;

	public Tracing(Collection<Delivery> deliveries) {
		Set<String> allIds = new LinkedHashSet<>();

		for (Delivery d : deliveries) {
			allIds.add(d.getId());
		}

		this.deliveries = new ArrayList<>();

		for (Delivery d : deliveries) {
			Delivery copy = d.copy();

			copy.getAllNextIds().retainAll(allIds);
			copy.getAllPreviousIds().retainAll(allIds);
			this.deliveries.add(copy);
		}

		stationWeights = new LinkedHashMap<>();
		ccStations = new LinkedHashSet<>();
		deliveryWeights = new LinkedHashMap<>();
		ccDeliveries = new LinkedHashSet<>();
		killContaminationStations = new LinkedHashSet<>();
		killContaminationDeliveries = new LinkedHashSet<>();
	}

	public void setStationWeight(String stationId, double weight) {
		if (weight == 0.0) {
			stationWeights.remove(stationId);
		} else {
			stationWeights.put(stationId, weight);
		}
	}

	public void setDeliveryWeight(String deliveryId, double weight) {
		if (weight == 0.0) {
			deliveryWeights.remove(deliveryId);
		} else {
			deliveryWeights.put(deliveryId, weight);
		}
	}

	public void setCrossContaminationOfStation(String stationId, boolean enabled) {
		if (enabled) {
			ccStations.add(stationId);
		} else {
			ccStations.remove(stationId);
		}
	}

	public void setCrossContaminationOfDelivery(String deliveryId, boolean enabled) {
		if (enabled) {
			ccDeliveries.add(deliveryId);
		} else {
			ccDeliveries.remove(deliveryId);
		}
	}

	public void setKillContaminationOfStation(String stationId, boolean enabled) {
		if (enabled) {
			killContaminationStations.add(stationId);
		} else {
			killContaminationStations.remove(stationId);
		}
	}

	public void setKillContaminationOfDelivery(String deliveryId, boolean enabled) {
		if (enabled) {
			killContaminationDeliveries.add(deliveryId);
		} else {
			killContaminationDeliveries.remove(deliveryId);
		}
	}

	public void mergeStations(Set<String> toBeMerged, String mergedStationId) {
		for (Delivery d : deliveries) {
			if (toBeMerged.contains(d.getSupplierId())) {
				d.setSupplierId(mergedStationId);
			}

			if (toBeMerged.contains(d.getRecipientId())) {
				d.setRecipientId(mergedStationId);
			}
		}
	}

	public Result getResult(boolean enforceTemporalOrder) {
		positiveWeightSum = 0.0;
		negativeWeightSum = 0.0;

		for (double w : Iterables.concat(stationWeights.values(), deliveryWeights.values())) {
			if (w > 0.0) {
				positiveWeightSum += w;
			} else {
				negativeWeightSum -= w;
			}
		}

		backwardDeliveries = new LinkedHashMap<>();
		forwardDeliveries = new LinkedHashMap<>();
		deliveryMap = new LinkedHashMap<>();
		incomingDeliveries = LinkedHashMultimap.create();
		outgoingDeliveries = LinkedHashMultimap.create();

		for (Delivery d : deliveries) {
			deliveryMap.put(d.getId(), d.copy());
			incomingDeliveries.put(d.getRecipientId(), d.getId());
			outgoingDeliveries.put(d.getSupplierId(), d.getId());
		}

		for (String stationId : ccStations) {
			if (!incomingDeliveries.containsKey(stationId) || !outgoingDeliveries.containsKey(stationId)) {
				continue;
			}

			for (String inId : incomingDeliveries.get(stationId)) {
				Delivery in = deliveryMap.get(inId);

				for (String outId : outgoingDeliveries.get(stationId)) {
					if (inId.equals(outId)) {
						continue;
					}

					Delivery out = deliveryMap.get(outId);

					if (!enforceTemporalOrder || in.isBefore(out)) {
						in.getAllNextIds().add(outId);
						out.getAllPreviousIds().add(inId);
					}
				}
			}
		}

		// delivery cc: all incoming-ccs are mixed
		for (String in1Id : ccDeliveries) {
			Delivery in1 = deliveryMap.get(in1Id);

			for (String in2Id : ccDeliveries) {
				if (in1Id.equals(in2Id)) {
					continue;
				}

				Delivery in2 = deliveryMap.get(in2Id);

				if (!in1.getRecipientId().equals(in2.getRecipientId())) {
					continue;
				}

				for (String out1Id : in1.getAllNextIds()) {
					Delivery out1 = deliveryMap.get(out1Id);

					if (!enforceTemporalOrder || in2.isBefore(out1)) {
						in2.getAllNextIds().add(out1Id);
						out1.getAllPreviousIds().add(in2Id);
					}
				}

				for (String out2Id : in2.getAllNextIds()) {
					Delivery out2 = deliveryMap.get(out2Id);

					if (!enforceTemporalOrder || in1.isBefore(out2)) {
						in1.getAllNextIds().add(out2Id);
						out2.getAllPreviousIds().add(in1Id);
					}
				}
			}
		}

		for (String stationId : killContaminationStations) {
			if (!incomingDeliveries.containsKey(stationId) || !outgoingDeliveries.containsKey(stationId)) {
				continue;
			}

			for (String inId : incomingDeliveries.get(stationId)) {
				deliveryMap.get(inId).getAllNextIds().clear();
			}

			for (String outId : outgoingDeliveries.get(stationId)) {
				deliveryMap.get(outId).getAllPreviousIds().clear();
			}
		}

		for (String deliveryId : killContaminationDeliveries) {
			Delivery d = deliveryMap.get(deliveryId);

			for (String next : d.getAllNextIds()) {
				deliveryMap.get(next).getAllPreviousIds().remove(deliveryId);
			}

			d.getAllNextIds().clear();
		}

		Result result = new Result();

		for (String stationId : Sets.union(incomingDeliveries.keySet(), outgoingDeliveries.keySet())) {
			result.stationScores.put(stationId, getStationScore(stationId, ScoreType.COMBINED));
			result.stationPositiveScores.put(stationId, getStationScore(stationId, ScoreType.POSITIVE));
			result.stationNegativeScores.put(stationId, getStationScore(stationId, ScoreType.NEGATIVE));
			result.forwardStationsByStation.put(stationId, getForwardStations(stationId));
			result.backwardStationsByStation.put(stationId, getBackwardStations(stationId));
			result.forwardDeliveriesByStation.put(stationId, getForwardDeliveries(stationId));
			result.backwardDeliveriesByStation.put(stationId, getBackwardDeliveries(stationId));
		}

		for (Delivery d : deliveryMap.values()) {
			result.deliveryScores.put(d.getId(), getDeliveryScore(d, ScoreType.COMBINED));
			result.deliveryPositiveScores.put(d.getId(), getDeliveryScore(d, ScoreType.POSITIVE));
			result.deliveryNegativeScores.put(d.getId(), getDeliveryScore(d, ScoreType.NEGATIVE));
			result.forwardStationsByDelivery.put(d.getId(), getForwardStations(d));
			result.backwardStationsByDelivery.put(d.getId(), getBackwardStations(d));
			result.forwardDeliveriesByDelivery.put(d.getId(), getForwardDeliveries(d));
			result.backwardDeliveriesByDelivery.put(d.getId(), getBackwardDeliveries(d));
		}

		return result;
	}

	private double getStationScore(String id, ScoreType type) {
		double denom = getDenom(type);

		if (denom == 0.0) {
			return 0.0;
		}

		double sum = getWeight(stationWeights.get(id), type);

		for (String stationId : getForwardStations(id)) {
			sum += getWeight(stationWeights.get(stationId), type);
		}

		for (String deliveryId : getForwardDeliveries(id)) {
			sum += getWeight(deliveryWeights.get(deliveryId), type);
		}

		return sum / denom;
	}

	private double getDeliveryScore(Delivery d, ScoreType type) {
		double denom = getDenom(type);

		if (denom == 0.0) {
			return 0.0;
		}

		double sum = getWeight(deliveryWeights.get(d.getId()), type);

		for (String stationId : getForwardStations(d)) {
			sum += getWeight(stationWeights.get(stationId), type);
		}

		for (String deliveryId : getForwardDeliveries(d)) {
			sum += getWeight(deliveryWeights.get(deliveryId), type);
		}

		return sum / denom;
	}

	private double getDenom(ScoreType type) {
		switch (type) {
		case COMBINED:
			return Math.max(positiveWeightSum, negativeWeightSum);
		case POSITIVE:
			return positiveWeightSum;
		case NEGATIVE:
			return negativeWeightSum;
		default:
			throw new RuntimeException("This should not happen.");
		}
	}

	private double getWeight(Double weight, ScoreType type) {
		if (weight == null) {
			return 0.0;
		}

		switch (type) {
		case COMBINED:
			return weight;
		case POSITIVE:
			return weight > 0.0 ? weight : 0.0;
		case NEGATIVE:
			return weight < 0.0 ? -weight : 0.0;
		default:
			throw new RuntimeException("This should not happen.");
		}
	}

	private Set<String> getForwardStations(String stationID) {
		Set<String> stations = new LinkedHashSet<>();

		for (String id : outgoingDeliveries.get(stationID)) {
			stations.addAll(getForwardStations(deliveryMap.get(id)));
		}

		return stations;
	}

	private Set<String> getBackwardStations(String stationID) {
		Set<String> stations = new LinkedHashSet<>();

		for (String id : incomingDeliveries.get(stationID)) {
			stations.addAll(getBackwardStations(deliveryMap.get(id)));
		}

		return stations;
	}

	private Set<String> getForwardDeliveries(String stationID) {
		Set<String> forward = new LinkedHashSet<>();

		for (String id : outgoingDeliveries.get(stationID)) {
			forward.add(id);
			forward.addAll(getForwardDeliveries(deliveryMap.get(id)));
		}

		return forward;
	}

	private Set<String> getBackwardDeliveries(String stationID) {
		Set<String> backward = new LinkedHashSet<>();

		for (String id : incomingDeliveries.get(stationID)) {
			backward.add(id);
			backward.addAll(getBackwardDeliveries(deliveryMap.get(id)));
		}

		return backward;
	}

	private Set<String> getBackwardDeliveries(Delivery d) {
		Set<String> backward = backwardDeliveries.get(d.getId());

		if (backward != null) {
			return backward;
		}

		backward = new LinkedHashSet<>();

		for (String prev : d.getAllPreviousIds()) {
			if (!prev.equals(d.getId())) {
				backward.add(prev);
				backward.addAll(getBackwardDeliveries(deliveryMap.get(prev)));
			}
		}

		backwardDeliveries.put(d.getId(), backward);

		return backward;
	}

	private Set<String> getForwardDeliveries(Delivery d) {
		Set<String> forward = forwardDeliveries.get(d.getId());

		if (forward != null) {
			return forward;
		}

		forward = new LinkedHashSet<>();

		for (String next : d.getAllNextIds()) {
			if (!next.equals(d.getId())) {
				forward.add(next);
				forward.addAll(getForwardDeliveries(deliveryMap.get(next)));
			}
		}

		forwardDeliveries.put(d.getId(), forward);

		return forward;
	}

	private Set<String> getBackwardStations(Delivery d) {
		Set<String> result = new LinkedHashSet<>();

		result.add(d.getSupplierId());

		for (String id : getBackwardDeliveries(d)) {
			result.add(deliveryMap.get(id).getSupplierId());
		}

		return result;
	}

	private Set<String> getForwardStations(Delivery d) {
		Set<String> result = new LinkedHashSet<>();

		result.add(d.getRecipientId());

		for (String id : getForwardDeliveries(d)) {
			result.add(deliveryMap.get(id).getRecipientId());
		}

		return result;
	}

	public static final class Result {

		private Map<String, Double> stationScores;
		private Map<String, Double> stationPositiveScores;
		private Map<String, Double> stationNegativeScores;
		private Map<String, Double> deliveryScores;
		private Map<String, Double> deliveryPositiveScores;
		private Map<String, Double> deliveryNegativeScores;
		private Map<String, Set<String>> forwardStationsByStation;
		private Map<String, Set<String>> backwardStationsByStation;
		private Map<String, Set<String>> forwardDeliveriesByStation;
		private Map<String, Set<String>> backwardDeliveriesByStation;
		private Map<String, Set<String>> forwardStationsByDelivery;
		private Map<String, Set<String>> backwardStationsByDelivery;
		private Map<String, Set<String>> forwardDeliveriesByDelivery;
		private Map<String, Set<String>> backwardDeliveriesByDelivery;

		private Result() {
			stationScores = new LinkedHashMap<>();
			stationPositiveScores = new LinkedHashMap<>();
			stationNegativeScores = new LinkedHashMap<>();
			deliveryScores = new LinkedHashMap<>();
			deliveryPositiveScores = new LinkedHashMap<>();
			deliveryNegativeScores = new LinkedHashMap<>();
			forwardStationsByStation = new LinkedHashMap<>();
			backwardStationsByStation = new LinkedHashMap<>();
			forwardDeliveriesByStation = new LinkedHashMap<>();
			backwardDeliveriesByStation = new LinkedHashMap<>();
			forwardStationsByDelivery = new LinkedHashMap<>();
			backwardStationsByDelivery = new LinkedHashMap<>();
			forwardDeliveriesByDelivery = new LinkedHashMap<>();
			backwardDeliveriesByDelivery = new LinkedHashMap<>();
		}

		public Map<String, Double> getStationScores() {
			return stationScores;
		}

		public Map<String, Double> getStationPositiveScores() {
			return stationPositiveScores;
		}

		public Map<String, Double> getStationNegativeScores() {
			return stationNegativeScores;
		}

		public Map<String, Double> getDeliveryScores() {
			return deliveryScores;
		}

		public Map<String, Double> getDeliveryPositiveScores() {
			return deliveryPositiveScores;
		}

		public Map<String, Double> getDeliveryNegativeScores() {
			return deliveryNegativeScores;
		}

		public Map<String, Set<String>> getForwardStationsByStation() {
			return forwardStationsByStation;
		}

		public Map<String, Set<String>> getBackwardStationsByStation() {
			return backwardStationsByStation;
		}

		public Map<String, Set<String>> getForwardDeliveriesByStation() {
			return forwardDeliveriesByStation;
		}

		public Map<String, Set<String>> getBackwardDeliveriesByStation() {
			return backwardDeliveriesByStation;
		}

		public Map<String, Set<String>> getForwardStationsByDelivery() {
			return forwardStationsByDelivery;
		}

		public Map<String, Set<String>> getBackwardStationsByDelivery() {
			return backwardStationsByDelivery;
		}

		public Map<String, Set<String>> getForwardDeliveriesByDelivery() {
			return forwardDeliveriesByDelivery;
		}

		public Map<String, Set<String>> getBackwardDeliveriesByDelivery() {
			return backwardDeliveriesByDelivery;
		}
	}
}
