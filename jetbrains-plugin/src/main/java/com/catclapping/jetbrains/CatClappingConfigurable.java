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

    private void triggerTestCelebration() {
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
                || soundCheckBox.isSelected() != settings.isSoundEnabled();
    }

    @Override
    public void apply() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        settings.setEnabledCommands(parseCommands(commandsField.getText()));
        settings.setSoundEnabled(soundCheckBox.isSelected());
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
