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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import de.bund.bfr.knime.UI;
import de.bund.bfr.knime.gis.views.canvas.element.Element;
import de.bund.bfr.knime.openkrise.TracingColumns;

public class EditableSinglePropertiesDialog extends JDialog implements
		ActionListener {

	private static final long serialVersionUID = 1L;

	private Element element;

	private JButton okButton;
	private JButton cancelButton;

	private JTextField caseField;
	private JCheckBox contaminationBox;
	private JCheckBox observedBox;

	private boolean approved;

	public EditableSinglePropertiesDialog(Component parent, Element element,
			Map<String, Class<?>> properties) {
		super(SwingUtilities.getWindowAncestor(parent), "Properties",
				DEFAULT_MODALITY_TYPE);
		this.element = element;

		JPanel leftNorthPanel = new JPanel();
		JPanel rightNorthPanel = new JPanel();

		leftNorthPanel.setLayout(new GridLayout(3, 1, 5, 5));
		rightNorthPanel.setLayout(new GridLayout(3, 1, 5, 5));

		double weight = 0.0;
		boolean crossContamination = false;
		boolean observed = false;

		if (element.getProperties().get(TracingColumns.WEIGHT) != null) {
			weight = (Double) element.getProperties()
					.get(TracingColumns.WEIGHT);
		}

		if (element.getProperties().get(TracingColumns.CROSS_CONTAMINATION) != null) {
			crossContamination = (Boolean) element.getProperties().get(
					TracingColumns.CROSS_CONTAMINATION);
		}

		if (element.getProperties().get(TracingColumns.OBSERVED) != null) {
			observed = (Boolean) element.getProperties().get(
					TracingColumns.OBSERVED);
		}

		caseField = new JTextField(String.valueOf(weight));
		contaminationBox = new JCheckBox("", crossContamination);
		observedBox = new JCheckBox("", observed);

		leftNorthPanel.add(new JLabel(TracingColumns.WEIGHT + ":"));
		leftNorthPanel
				.add(new JLabel(TracingColumns.CROSS_CONTAMINATION + ":"));
		leftNorthPanel.add(new JLabel(TracingColumns.OBSERVED + ":"));
		rightNorthPanel.add(caseField);
		rightNorthPanel.add(contaminationBox);
		rightNorthPanel.add(observedBox);

		JPanel northPanel = new JPanel();

		northPanel.setBorder(BorderFactory.createTitledBorder("Input"));
		northPanel.setLayout(new BorderLayout(5, 5));
		northPanel.add(leftNorthPanel, BorderLayout.WEST);
		northPanel.add(rightNorthPanel, BorderLayout.CENTER);

		JPanel leftCenterPanel = new JPanel();
		JPanel rightCenterPanel = new JPanel();

		leftCenterPanel
				.setLayout(new GridLayout(properties.size() - 3, 1, 5, 5));
		rightCenterPanel.setLayout(new GridLayout(properties.size() - 3, 1, 5,
				5));

		for (String property : properties.keySet()) {
			Object value = element.getProperties().get(property);

			if (!property.equals(TracingColumns.WEIGHT)
					&& !property.equals(TracingColumns.CROSS_CONTAMINATION)
					&& !property.equals(TracingColumns.OBSERVED)) {
				JTextField field = new JTextField();

				if (value != null) {
					field.setText(value.toString());
					field.setPreferredSize(new Dimension(field
							.getPreferredSize().width + 5, field
							.getPreferredSize().height));
				}

				field.setEditable(false);
				leftCenterPanel.add(new JLabel(property + ":"));
				rightCenterPanel.add(field);
			}
		}

		JPanel centerPanel = new JPanel();

		centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		centerPanel.setLayout(new BorderLayout(5, 5));
		centerPanel.add(leftCenterPanel, BorderLayout.WEST);
		centerPanel.add(rightCenterPanel, BorderLayout.CENTER);

		okButton = new JButton("OK");
		okButton.addActionListener(this);
		cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(this);

		setLayout(new BorderLayout());
		add(northPanel, BorderLayout.NORTH);
		add(new JScrollPane(UI.createNorthPanel(centerPanel)),
				BorderLayout.CENTER);
		add(UI.createEastPanel(UI.createHorizontalPanel(okButton, cancelButton)),
				BorderLayout.SOUTH);
		pack();
		setLocationRelativeTo(parent);
		UI.adjustDialog(this, 0.5, 1.0);
	}

	public boolean isApproved() {
		return approved;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == okButton) {
			if (caseField.getText().isEmpty()) {
				element.getProperties().put(TracingColumns.WEIGHT, 0.0);
			} else {
				try {
					element.getProperties().put(TracingColumns.WEIGHT,
							Double.parseDouble(caseField.getText()));
				} catch (NumberFormatException ex) {
					JOptionPane.showMessageDialog(this,
							"Please enter valid number for "
									+ TracingColumns.WEIGHT, "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

			element.getProperties().put(TracingColumns.CROSS_CONTAMINATION,
					contaminationBox.isSelected());
			element.getProperties().put(TracingColumns.OBSERVED,
					observedBox.isSelected());
			approved = true;
			dispose();
		} else if (e.getSource() == cancelButton) {
			approved = false;
			dispose();
		}
	}
}