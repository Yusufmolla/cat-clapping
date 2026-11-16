package com.catclapping.jetbrains;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Settings UI under Settings -> Tools -> Cat Clapping.
 */
public final class CatClappingConfigurable implements SearchableConfigurable {

    private JPanel rootPanel;
    private final Map<String, JCheckBox> supportedCheckBoxes = new LinkedHashMap<>();
    private final List<String> customCommands = new ArrayList<>();
    private JTextField customCommandField;
    private JPanel customCommandsContainer;
    private JCheckBox soundCheckBox;
    private JSlider volumeSlider;
    private JLabel volumeValueLabel;
    private JSlider sizeSlider;
    private JLabel sizeValueLabel;
    private JComboBox<CatPosition> positionCombo;
    private JComboBox<CatAnchor> anchorCombo;

    @Override
    public @NotNull String getId() {
        return "de.yusufmolla.catclapping.settings";
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Cat Clapping";
    }

    @Override
    public @Nullable JComponent createComponent() {
        rootPanel = new JPanel(new BorderLayout());
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(buildCommandsSection());
        form.add(Box.createVerticalStrut(10));
        form.add(buildCustomCommandsSection());
        form.add(Box.createVerticalStrut(14));

        soundCheckBox = new JCheckBox("Play celebration sound");
        soundCheckBox.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        form.add(soundCheckBox);

        form.add(Box.createVerticalStrut(8));

        volumeSlider = new JSlider(0, 100, CatClappingSettings.DEFAULT_VOLUME_PERCENT);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setPaintTicks(true);
        volumeValueLabel = new JLabel();
        volumeSlider.addChangeListener(e -> volumeValueLabel.setText(volumeSlider.getValue() + " %"));
        soundCheckBox.addItemListener(e -> volumeSlider.setEnabled(soundCheckBox.isSelected()));
        form.add(sliderRow("Volume:", volumeSlider, volumeValueLabel));

        form.add(Box.createVerticalStrut(8));

        sizeSlider = new JSlider(CatClappingSettings.MIN_CAT_SIZE_PX, CatClappingSettings.MAX_CAT_SIZE_PX,
                CatClappingSettings.DEFAULT_CAT_SIZE_PX);
        sizeSlider.setMajorTickSpacing(100);
        sizeSlider.setPaintTicks(true);
        sizeValueLabel = new JLabel();
        sizeSlider.addChangeListener(e -> sizeValueLabel.setText(sizeSlider.getValue() + " px"));
        form.add(sliderRow("Cat size (height):", sizeSlider, sizeValueLabel));

        form.add(Box.createVerticalStrut(8));

        form.add(buildPositionRow());

        form.add(Box.createVerticalStrut(14));

        JPanel testRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JButton testButton = new JButton("Test Cat Clapping");
        testButton.addActionListener(e -> triggerTestCelebration());
        testRow.add(testButton);
        testRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        form.add(testRow);

        rootPanel.add(form, BorderLayout.NORTH);
        reset();
        return rootPanel;
    }

    private JPanel buildCommandsSection() {
        supportedCheckBoxes.clear();

        JPanel section = new JPanel(new BorderLayout(0, 6));
        section.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.add(new JLabel("Supported commands (select which triggers celebrate):"), BorderLayout.WEST);

        JPanel bulkButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton selectAllButton = new JButton("Select All");
        selectAllButton.addActionListener(e -> setAllSupportedSelected(true));
        JButton deselectAllButton = new JButton("Deselect All");
        deselectAllButton.addActionListener(e -> setAllSupportedSelected(false));
        bulkButtons.add(selectAllButton);
        bulkButtons.add(deselectAllButton);
        header.add(bulkButtons, BorderLayout.EAST);
        section.add(header, BorderLayout.NORTH);

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        Map<String, List<CommandMatcher.SupportedCommand>> grouped = new LinkedHashMap<>();
        for (CommandMatcher.SupportedCommand cmd : CommandMatcher.supportedCommands()) {
            grouped.computeIfAbsent(cmd.category(), k -> new ArrayList<>()).add(cmd);
        }

        boolean firstCategory = true;
        for (Map.Entry<String, List<CommandMatcher.SupportedCommand>> entry : grouped.entrySet()) {
            if (!firstCategory) {
                listPanel.add(Box.createVerticalStrut(8));
            }
            firstCategory = false;

            JLabel categoryLabel = new JLabel(entry.getKey());
            categoryLabel.setFont(categoryLabel.getFont().deriveFont(Font.BOLD));
            categoryLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            listPanel.add(categoryLabel);
            listPanel.add(Box.createVerticalStrut(4));

            JPanel grid = new JPanel(new GridLayout(0, 3, 8, 2));
            grid.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            for (CommandMatcher.SupportedCommand cmd : entry.getValue()) {
                JCheckBox checkBox = new JCheckBox(cmd.label());
                checkBox.setToolTipText("Command selector: " + cmd.id());
                supportedCheckBoxes.put(cmd.id(), checkBox);
                grid.add(checkBox);
            }
            Dimension pref = grid.getPreferredSize();
            grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
            listPanel.add(grid);
        }

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setPreferredSize(new Dimension(560, 230));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        section.add(scrollPane, BorderLayout.CENTER);

        return section;
    }

    private JPanel buildCustomCommandsSection() {
        JPanel section = new JPanel(new BorderLayout(0, 6));
        section.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel("Custom commands:");
        label.setPreferredSize(new Dimension(130, label.getPreferredSize().height));
        inputRow.add(label, BorderLayout.WEST);

        customCommandField = new JTextField();
        customCommandField.setToolTipText("Add extra commands (e.g. docker, ruff, myapp.exe). Comma-separated supported.");
        customCommandField.addActionListener(e -> addCustomCommandsFromInput());
        inputRow.add(customCommandField, BorderLayout.CENTER);

        JButton addButton = new JButton("Add");
        addButton.addActionListener(e -> addCustomCommandsFromInput());
        inputRow.add(addButton, BorderLayout.EAST);
        inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        section.add(inputRow, BorderLayout.NORTH);

        customCommandsContainer = new JPanel(new BorderLayout());
        section.add(customCommandsContainer, BorderLayout.CENTER);

        return section;
    }

    private JPanel buildPositionRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JLabel label = new JLabel("Display position:");
        label.setPreferredSize(new Dimension(130, label.getPreferredSize().height));
        row.add(label);
        row.add(Box.createHorizontalStrut(8));

        positionCombo = new JComboBox<>(CatPosition.values());
        row.add(positionCombo);
        row.add(Box.createHorizontalStrut(8));
        row.add(new JLabel("in"));
        row.add(Box.createHorizontalStrut(8));

        anchorCombo = new JComboBox<>(CatAnchor.values());
        row.add(anchorCombo);

        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return row;
    }

    private void setAllSupportedSelected(boolean selected) {
        for (JCheckBox checkBox : supportedCheckBoxes.values()) {
            checkBox.setSelected(selected);
        }
    }

    private void addCustomCommandsFromInput() {
        if (customCommandField == null) {
            return;
        }
        String raw = customCommandField.getText();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split("[,;\\n]+")) {
            String normalized = part.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if ("*".equals(normalized)) {
                setAllSupportedSelected(true);
                continue;
            }
            JCheckBox supportedBox = supportedCheckBoxes.get(normalized);
            if (supportedBox != null) {
                supportedBox.setSelected(true);
            } else if (!customCommands.contains(normalized)) {
                customCommands.add(normalized);
            }
        }
        customCommandField.setText("");
        rebuildCustomCommandsPanel();
    }

    private void rebuildCustomCommandsPanel() {
        if (customCommandsContainer == null) {
            return;
        }
        customCommandsContainer.removeAll();
        if (customCommands.isEmpty()) {
            JLabel hint = new JLabel("Optional: add extra terminal commands (e.g. docker, ruff, myapp.exe).");
            Color disabledColor = UIManager.getColor("Label.disabledForeground");
            if (disabledColor != null) {
                hint.setForeground(disabledColor);
            }
            customCommandsContainer.add(hint, BorderLayout.CENTER);
        } else {
            JPanel chips = new JPanel(new GridLayout(0, 3, 6, 4));
            Color borderColor = UIManager.getColor("Component.borderColor");
            if (borderColor == null) {
                borderColor = Color.GRAY;
            }
            for (String command : new ArrayList<>(customCommands)) {
                JPanel chip = new JPanel(new BorderLayout(4, 0));
                chip.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(borderColor),
                        BorderFactory.createEmptyBorder(2, 6, 2, 2)));
                chip.add(new JLabel(command), BorderLayout.CENTER);
                JButton removeBtn = new JButton("x");
                removeBtn.setMargin(new Insets(0, 4, 0, 4));
                removeBtn.setFocusable(false);
                removeBtn.setToolTipText("Remove " + command);
                removeBtn.addActionListener(e -> {
                    customCommands.remove(command);
                    rebuildCustomCommandsPanel();
                });
                chip.add(removeBtn, BorderLayout.EAST);
                chips.add(chip);
            }
            customCommandsContainer.add(chips, BorderLayout.CENTER);
        }
        customCommandsContainer.revalidate();
        customCommandsContainer.repaint();
        if (rootPanel != null) {
            rootPanel.revalidate();
            rootPanel.repaint();
        }
    }

    private static JPanel sliderRow(String title, JSlider slider, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel(title);
        label.setPreferredSize(new Dimension(130, label.getPreferredSize().height));
        row.add(label, BorderLayout.WEST);
        slider.setPreferredSize(new Dimension(320, slider.getPreferredSize().height));
        row.add(slider, BorderLayout.CENTER);
        valueLabel.setPreferredSize(new Dimension(60, valueLabel.getPreferredSize().height));
        row.add(valueLabel, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(560, slider.getPreferredSize().height + 8));
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return row;
    }

    private void triggerTestCelebration() {
        // Let the test button preview the values currently shown in the dialog.
        if (isModified()) {
            apply();
        }
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        if (projects.length > 0) {
            CelebrationService.getInstance(projects[0]).celebrate(true);
        } else {
            SoundPlayer.play();
        }
    }

    private List<String> collectSelectedCommands() {
        Set<String> selected = new LinkedHashSet<>();
        for (Map.Entry<String, JCheckBox> entry : supportedCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        selected.addAll(customCommands);
        return new ArrayList<>(selected);
    }

    @Override
    public boolean isModified() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        Set<String> currentSelection = new LinkedHashSet<>(collectSelectedCommands());
        Set<String> savedSelection = new LinkedHashSet<>(
                CatClappingSettings.resolveCommandSelection(settings.getEnabledCommands()));
        return !currentSelection.equals(savedSelection)
                || soundCheckBox.isSelected() != settings.isSoundEnabled()
                || volumeSlider.getValue() != settings.getVolumePercent()
                || sizeSlider.getValue() != settings.getCatSizePx()
                || positionCombo.getSelectedItem() != settings.getCatPosition()
                || anchorCombo.getSelectedItem() != settings.getCatAnchor();
    }

    @Override
    public void apply() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        settings.setEnabledCommands(collectSelectedCommands());
        settings.setSoundEnabled(soundCheckBox.isSelected());
        settings.setVolumePercent(volumeSlider.getValue());
        settings.setCatSizePx(sizeSlider.getValue());
        Object selectedPos = positionCombo.getSelectedItem();
        if (selectedPos instanceof CatPosition pos) {
            settings.setCatPosition(pos);
        }
        Object selectedAnchor = anchorCombo.getSelectedItem();
        if (selectedAnchor instanceof CatAnchor anchor) {
            settings.setCatAnchor(anchor);
        }
    }

    @Override
    public void reset() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        Set<String> resolved = new LinkedHashSet<>(
                CatClappingSettings.resolveCommandSelection(settings.getEnabledCommands()));
        for (Map.Entry<String, JCheckBox> entry : supportedCheckBoxes.entrySet()) {
            entry.getValue().setSelected(resolved.contains(entry.getKey()));
        }
        customCommands.clear();
        for (String cmd : resolved) {
            if (!CommandMatcher.isSupportedCommandId(cmd)) {
                customCommands.add(cmd);
            }
        }
        if (customCommandField != null) {
            customCommandField.setText("");
        }
        rebuildCustomCommandsPanel();

        if (soundCheckBox != null) {
            soundCheckBox.setSelected(settings.isSoundEnabled());
        }
        if (volumeSlider != null) {
            volumeSlider.setValue(settings.getVolumePercent());
            volumeValueLabel.setText(volumeSlider.getValue() + " %");
            volumeSlider.setEnabled(settings.isSoundEnabled());
        }
        if (sizeSlider != null) {
            sizeSlider.setValue(settings.getCatSizePx());
            sizeValueLabel.setText(sizeSlider.getValue() + " px");
        }
        if (positionCombo != null) {
            positionCombo.setSelectedItem(settings.getCatPosition());
        }
        if (anchorCombo != null) {
            anchorCombo.setSelectedItem(settings.getCatAnchor());
        }
    }
}
