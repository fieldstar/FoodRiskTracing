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
package de.bund.bfr.knime.openkrise.views.canvas;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.RowSorterEvent;
import javax.swing.event.RowSorterListener;

import de.bund.bfr.knime.UI;
import de.bund.bfr.knime.gis.views.canvas.EdgePropertySchema;
import de.bund.bfr.knime.gis.views.canvas.ICanvas;
import de.bund.bfr.knime.gis.views.canvas.NodePropertySchema;
import de.bund.bfr.knime.gis.views.canvas.PropertySchema;
import de.bund.bfr.knime.gis.views.canvas.dialogs.PropertiesTable;
import de.bund.bfr.knime.gis.views.canvas.element.Edge;
import de.bund.bfr.knime.gis.views.canvas.element.Element;
import de.bund.bfr.knime.gis.views.canvas.element.Node;
import de.bund.bfr.knime.openkrise.TracingColumns;

public class EditablePropertiesDialog<V extends Node> extends JDialog implements
		ActionListener, CellEditorListener, RowSorterListener,
		ListSelectionListener {

	private static final long serialVersionUID = 1L;

	private static enum Type {
		NODE, EDGE
	}

	private ICanvas<V> parent;
	private Type type;

	private List<Element> elementList;

	private JScrollPane scrollPane;
	private PropertiesTable table;
	private InputTable inputTable;
	private JButton okButton;
	private JButton cancelButton;

	private JButton selectButton;
	private JButton weightButton;
	private JButton contaminationButton;
	private JButton filterButton;

	private boolean approved;

	private Map<String, InputTable.Input> values;

	private EditablePropertiesDialog(ICanvas<V> parent,
			Collection<? extends Element> elements, PropertySchema schema,
			Type type, boolean allowViewSelection) {
		super(SwingUtilities.getWindowAncestor(parent.getComponent()),
				"Properties", DEFAULT_MODALITY_TYPE);
		this.parent = parent;
		this.type = type;

		Set<String> idColumns = new LinkedHashSet<>();

		switch (type) {
		case NODE:
			idColumns.add(TracingColumns.ID);
			break;
		case EDGE:
			idColumns.addAll(Arrays.asList(TracingColumns.ID,
					TracingColumns.FROM, TracingColumns.TO));
			break;
		}

		PropertySchema uneditableSchema = new PropertySchema(
				new LinkedHashMap<>(schema.getMap()));

		uneditableSchema.getMap().remove(TracingColumns.WEIGHT);
		uneditableSchema.getMap().remove(TracingColumns.CROSS_CONTAMINATION);
		uneditableSchema.getMap().remove(TracingColumns.OBSERVED);

		elementList = new ArrayList<>(elements);
		table = new PropertiesTable(elementList, uneditableSchema, idColumns);
		table.getRowSorter().addRowSorterListener(this);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getSelectionModel().addListSelectionListener(this);
		inputTable = new InputTable(elementList);
		inputTable.getColumn(InputTable.INPUT).getCellEditor()
				.addCellEditorListener(this);
		inputTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		inputTable.getSelectionModel().addListSelectionListener(this);
		values = new LinkedHashMap<>();
		updateValues();

		okButton = new JButton("OK");
		okButton.addActionListener(this);
		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);

		selectButton = new JButton("Select in View");
		selectButton.addActionListener(this);
		weightButton = new JButton("Set All " + TracingColumns.WEIGHT);
		weightButton.addActionListener(this);
		contaminationButton = new JButton("Set All "
				+ TracingColumns.CROSS_CONTAMINATION);
		contaminationButton.addActionListener(this);
		filterButton = new JButton("Set All " + TracingColumns.OBSERVED);
		filterButton.addActionListener(this);

		JPanel cornerPanel = new JPanel();

		cornerPanel.setLayout(new GridLayout(1, 3));
		cornerPanel.add(getTableHeaderComponent(TracingColumns.WEIGHT));
		cornerPanel
				.add(getTableHeaderComponent(TracingColumns.CROSS_CONTAMINATION));
		cornerPanel.add(getTableHeaderComponent(TracingColumns.OBSERVED));

		scrollPane = new JScrollPane();
		scrollPane
				.setCorner(ScrollPaneConstants.UPPER_LEFT_CORNER, cornerPanel);
		scrollPane.setRowHeaderView(inputTable);
		scrollPane.setViewportView(table);
		scrollPane.setPreferredSize(UI.getMaxDimension(
				scrollPane.getPreferredSize(), table.getPreferredSize()));

		JViewport rowHeader = scrollPane.getRowHeader();

		rowHeader.setPreferredSize(new Dimension(
				cornerPanel.getMinimumSize().width, rowHeader
						.getPreferredSize().height));

		JPanel southPanel = new JPanel();

		southPanel.setLayout(new BorderLayout());
		southPanel.add(
				UI.createHorizontalPanel(new JLabel("Number of Elements: "
						+ elements.size())), BorderLayout.WEST);
		southPanel.add(UI.createHorizontalPanel(okButton, cancelButton),
				BorderLayout.EAST);

		List<JButton> buttons = new ArrayList<>();

		if (allowViewSelection) {
			buttons.add(selectButton);
		}

		if (schema.getMap().containsKey(TracingColumns.WEIGHT)) {
			buttons.add(weightButton);
		}

		if (schema.getMap().containsKey(TracingColumns.CROSS_CONTAMINATION)) {
			buttons.add(contaminationButton);
		}

		if (schema.getMap().containsKey(TracingColumns.OBSERVED)) {
			buttons.add(filterButton);
		}

		setLayout(new BorderLayout());
		add(UI.createWestPanel(UI.createHorizontalPanel(buttons
				.toArray(new JButton[0]))), BorderLayout.NORTH);
		add(scrollPane, BorderLayout.CENTER);
		add(southPanel, BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(parent.getComponent());
		UI.adjustDialog(this);
	}

	public static <V extends Node> EditablePropertiesDialog<V> createNodeDialog(
			ICanvas<V> parent, Collection<V> nodes, NodePropertySchema schema,
			boolean allowViewSelection) {
		return new EditablePropertiesDialog<V>(parent, nodes, schema,
				Type.NODE, allowViewSelection);
	}

	public static <V extends Node> EditablePropertiesDialog<V> createEdgeDialog(
			ICanvas<V> parent, Collection<Edge<V>> edges,
			EdgePropertySchema schema, boolean allowViewSelection) {
		return new EditablePropertiesDialog<V>(parent, edges, schema,
				Type.EDGE, allowViewSelection);
	}

	public boolean isApproved() {
		return approved;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == okButton) {
			approved = true;

			if (inputTable.isEditing()) {
				inputTable.getCellEditor().stopCellEditing();
			}

			for (Element element : elementList) {
				InputTable.Input input = values.get(element.getId());

				element.getProperties().put(TracingColumns.WEIGHT,
						input.getWeight());
				element.getProperties().put(TracingColumns.CROSS_CONTAMINATION,
						input.isCrossContamination());
				element.getProperties().put(TracingColumns.OBSERVED,
						input.isObserved());
			}

			dispose();
		} else if (e.getSource() == cancelButton) {
			approved = false;
			dispose();
		} else if (e.getSource() == selectButton) {
			switch (type) {
			case NODE:
				Set<V> nodes = new LinkedHashSet<>();

				for (Element element : table.getSelectedElements()) {
					nodes.add((V) element);
				}

				parent.setSelectedNodes(nodes);
				break;
			case EDGE:
				Set<Edge<V>> edges = new LinkedHashSet<>();

				for (Element element : table.getSelectedElements()) {
					edges.add((Edge<V>) element);
				}

				parent.setSelectedEdges(edges);
				break;
			}
		} else if (e.getSource() == weightButton) {
			Object result = JOptionPane.showInputDialog(this,
					"Set All Values to?", TracingColumns.WEIGHT,
					JOptionPane.QUESTION_MESSAGE, null, null, 1.0);
			Double value = null;

			try {
				value = Double.parseDouble(result.toString());
			} catch (NumberFormatException | NullPointerException ex) {
			}

			if (value != null) {
				setAllValuesTo(TracingColumns.WEIGHT, value);
			}
		} else if (e.getSource() == contaminationButton) {
			Object result = JOptionPane.showInputDialog(this,
					"Set All Values to?", TracingColumns.CROSS_CONTAMINATION,
					JOptionPane.QUESTION_MESSAGE, null, new Boolean[] {
							Boolean.TRUE, Boolean.FALSE }, Boolean.TRUE);

			if (result != null) {
				setAllValuesTo(TracingColumns.CROSS_CONTAMINATION, result);
			}
		} else if (e.getSource() == filterButton) {
			Object result = JOptionPane.showInputDialog(this,
					"Set All Values to?", TracingColumns.OBSERVED,
					JOptionPane.QUESTION_MESSAGE, null, new Boolean[] {
							Boolean.TRUE, Boolean.FALSE }, Boolean.TRUE);

			if (result != null) {
				setAllValuesTo(TracingColumns.OBSERVED, result);
			}
		}
	}

	@Override
	public void editingStopped(ChangeEvent e) {
		updateValues();
	}

	@Override
	public void editingCanceled(ChangeEvent e) {
	}

	@Override
	public void sorterChanged(RowSorterEvent e) {
		if (inputTable.isEditing()) {
			inputTable.getCellEditor().stopCellEditing();
		}

		applyValues();
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if (e.getSource() == inputTable.getSelectionModel()) {
			int i = inputTable.getSelectionModel().getAnchorSelectionIndex();
			int hScroll = scrollPane.getHorizontalScrollBar().getValue();

			table.getSelectionModel().setSelectionInterval(i, i);
			table.setVisible(false);
			table.scrollRectToVisible(new Rectangle(table.getCellRect(i, 0,
					true)));
			scrollPane.getHorizontalScrollBar().setValue(hScroll);
			table.setVisible(true);
		} else if (e.getSource() == table.getSelectionModel()) {
			int i = table.getSelectionModel().getAnchorSelectionIndex();

			inputTable.getSelectionModel().removeListSelectionListener(this);
			inputTable.getSelectionModel().setSelectionInterval(i, i);
			inputTable.getSelectionModel().addListSelectionListener(this);
		}
	}

	private void updateValues() {
		int idColumn = UI.findColumn(table, TracingColumns.ID);

		for (int row = 0; row < table.getRowCount(); row++) {
			String id = (String) table.getValueAt(row, idColumn);

			values.put(id, (InputTable.Input) inputTable.getValueAt(row, 0));
		}
	}

	private void applyValues() {
		int idColumn = UI.findColumn(table, TracingColumns.ID);

		for (int row = 0; row < table.getRowCount(); row++) {
			String id = (String) table.getValueAt(row, idColumn);

			inputTable.setValueAt(values.get(id), row, 0);
		}
	}

	private void setAllValuesTo(String column, Object value) {
		for (InputTable.Input input : values.values()) {
			if (column.equals(TracingColumns.WEIGHT)) {
				input.setWeight((Double) value);
			} else if (column.equals(TracingColumns.CROSS_CONTAMINATION)) {
				input.setCrossContamination((Boolean) value);
			} else if (column.equals(TracingColumns.OBSERVED)) {
				input.setObserved((Boolean) value);
			}
		}

		applyValues();
	}

	private static Component getTableHeaderComponent(String name) {
		JTable table = new JTable(new Object[1][1], new Object[] { name });

		return table.getTableHeader().getDefaultRenderer()
				.getTableCellRendererComponent(table, name, false, false, 0, 0);
	}
}
