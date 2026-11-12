package com.catclapping.jetbrains;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings UI under Settings -> Tools -> Cat Clapping.
 */
public final class CatClappingConfigurable implements SearchableConfigurable {

    private JPanel rootPanel;
    private JTextField commandsField;
    private JCheckBox soundCheckBox;
    private JSlider volumeSlider;
    private JLabel volumeValueLabel;
    private JSlider sizeSlider;
    private JLabel sizeValueLabel;

    @Override
    public @NotNull String getId() {
        return "com.catclapping.jetbrains.settings";
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

        JPanel commandsRow = new JPanel(new BorderLayout(8, 4));
        commandsRow.add(new JLabel("Enabled commands (comma-separated):"), BorderLayout.NORTH);
        commandsField = new JTextField();
        commandsField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        commandsRow.add(commandsField, BorderLayout.CENTER);
        JLabel hint = new JLabel(
                "<html>Examples: <code>*</code> (all supported), <code>python, uv, gcc, cmake, executable, debug, tasks</code>. Empty disables celebrations.</html>");
        commandsRow.add(hint, BorderLayout.SOUTH);
        commandsRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        form.add(commandsRow);

        form.add(Box.createVerticalStrut(12));

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

        form.add(Box.createVerticalStrut(12));

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

    @Override
    public boolean isModified() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        return !parseCommands(commandsField.getText()).equals(settings.getEnabledCommands())
                || soundCheckBox.isSelected() != settings.isSoundEnabled()
                || volumeSlider.getValue() != settings.getVolumePercent()
                || sizeSlider.getValue() != settings.getCatSizePx();
    }

    @Override
    public void apply() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        settings.setEnabledCommands(parseCommands(commandsField.getText()));
        settings.setSoundEnabled(soundCheckBox.isSelected());
        settings.setVolumePercent(volumeSlider.getValue());
        settings.setCatSizePx(sizeSlider.getValue());
    }

    @Override
    public void reset() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        if (commandsField != null) {
            commandsField.setText(String.join(", ", settings.getEnabledCommands()));
        }
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
    }

    private static List<String> parseCommands(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String part : raw.split("[,\\n]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
