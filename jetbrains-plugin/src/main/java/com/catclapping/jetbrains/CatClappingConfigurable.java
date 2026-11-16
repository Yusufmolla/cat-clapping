package com.catclapping.jetbrains;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.wm.WindowManager;
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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
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
    private JComboBox<CatAnchor> anchorCombo;
    private PositionPreviewPanel positionPreview;
    private JLabel positionReadoutLabel;

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
        sizeSlider.addChangeListener(e -> {
            sizeValueLabel.setText(sizeSlider.getValue() + " px");
            if (positionPreview != null) {
                positionPreview.repaint();
            }
        });
        form.add(sliderRow("Cat size (height):", sizeSlider, sizeValueLabel));

        form.add(Box.createVerticalStrut(10));
        form.add(buildPositionSection());
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
        scrollPane.setPreferredSize(new Dimension(560, 185));
        scrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
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

    private JPanel buildPositionSection() {
        JPanel section = new JPanel(new BorderLayout(12, 0));
        section.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Screen position:");
        title.setPreferredSize(new Dimension(130, title.getPreferredSize().height));
        title.setVerticalAlignment(JLabel.TOP);
        section.add(title, BorderLayout.WEST);

        JPanel body = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        positionPreview = new PositionPreviewPanel();
        body.add(positionPreview);
        body.add(Box.createHorizontalStrut(14));

        JPanel sideControls = new JPanel();
        sideControls.setLayout(new BoxLayout(sideControls, BoxLayout.Y_AXIS));

        JLabel dragHint = new JLabel("Drag the cat to place it:");
        dragHint.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        sideControls.add(dragHint);
        sideControls.add(Box.createVerticalStrut(6));

        JPanel anchorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        anchorRow.add(new JLabel("Area: "));
        anchorCombo = new JComboBox<>(CatAnchor.values());
        anchorCombo.addActionListener(e -> {
            if (positionPreview != null) {
                positionPreview.repaint();
            }
        });
        anchorRow.add(anchorCombo);
        anchorRow.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        sideControls.add(anchorRow);
        sideControls.add(Box.createVerticalStrut(8));

        positionReadoutLabel = new JLabel();
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            positionReadoutLabel.setForeground(muted);
        }
        positionReadoutLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        sideControls.add(positionReadoutLabel);
        sideControls.add(Box.createVerticalStrut(8));

        JPanel quickButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton centerBtn = new JButton("Center");
        centerBtn.addActionListener(e -> positionPreview.setNormalizedPosition(0.5, 0.5));
        JButton topCenterBtn = new JButton("Top Center");
        topCenterBtn.addActionListener(e -> positionPreview.setNormalizedPosition(
                CatClappingSettings.DEFAULT_CAT_POS_X, CatClappingSettings.DEFAULT_CAT_POS_Y));
        quickButtons.add(centerBtn);
        quickButtons.add(topCenterBtn);
        quickButtons.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        sideControls.add(quickButtons);

        body.add(sideControls);
        section.add(body, BorderLayout.CENTER);
        return section;
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

    private void updatePositionReadout(double posX, double posY) {
        if (positionReadoutLabel != null) {
            int xPct = (int) Math.round(posX * 100.0);
            int yPct = (int) Math.round(posY * 100.0);
            positionReadoutLabel.setText("Position: " + xPct + "% X, " + yPct + "% Y");
        }
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
                || Math.abs(positionPreview.getPosX() - settings.getCatPosX()) > 0.001
                || Math.abs(positionPreview.getPosY() - settings.getCatPosY()) > 0.001
                || anchorCombo.getSelectedItem() != settings.getCatAnchor();
    }

    @Override
    public void apply() {
        CatClappingSettings settings = CatClappingSettings.getInstance();
        settings.setEnabledCommands(collectSelectedCommands());
        settings.setSoundEnabled(soundCheckBox.isSelected());
        settings.setVolumePercent(volumeSlider.getValue());
        settings.setCatSizePx(sizeSlider.getValue());
        settings.setCatPosX(positionPreview.getPosX());
        settings.setCatPosY(positionPreview.getPosY());
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
        if (anchorCombo != null) {
            anchorCombo.setSelectedItem(settings.getCatAnchor());
        }
        if (positionPreview != null) {
            positionPreview.setNormalizedPosition(settings.getCatPosX(), settings.getCatPosY());
        }
    }

    /**
     * Interactive mini IDE window where the user can drag the cat to set its overlay position.
     */
    private final class PositionPreviewPanel extends JPanel {

        private static final int PREVIEW_HEIGHT = 170;
        private static final int PREVIEW_MARGIN = 4;

        private final int refWindowW;
        private final int refWindowH;
        private double posX = CatClappingSettings.DEFAULT_CAT_POS_X;
        private double posY = CatClappingSettings.DEFAULT_CAT_POS_Y;
        private volatile BufferedImage catImage;
        private boolean dragging;
        private int grabOffsetX;
        private int grabOffsetY;

        PositionPreviewPanel() {
            Dimension frameSize = resolveIdeWindowSize();
            this.refWindowW = frameSize.width;
            this.refWindowH = frameSize.height;
            int previewW = Math.max(240, Math.min(340,
                    (int) Math.round(PREVIEW_HEIGHT * ((double) refWindowW / refWindowH))));
            setPreferredSize(new Dimension(previewW, PREVIEW_HEIGHT));
            setMinimumSize(new Dimension(previewW, PREVIEW_HEIGHT));
            setMaximumSize(new Dimension(previewW, PREVIEW_HEIGHT));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Click or drag the cat to position it");

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                BufferedImage preview = CatAnimation.firstFramePreview();
                if (preview != null) {
                    catImage = preview;
                    SwingUtilities.invokeLater(this::repaint);
                }
            });

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    Rectangle catRect = computeCatBounds();
                    if (catRect.contains(e.getPoint())) {
                        grabOffsetX = e.getX() - catRect.x;
                        grabOffsetY = e.getY() - catRect.y;
                    } else {
                        grabOffsetX = catRect.width / 2;
                        grabOffsetY = catRect.height / 2;
                        updateFromMouse(e.getX(), e.getY());
                    }
                    dragging = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragging) {
                        updateFromMouse(e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragging = false;
                    updateHoverCursor(e.getPoint());
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    updateHoverCursor(e.getPoint());
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        double getPosX() {
            return posX;
        }

        double getPosY() {
            return posY;
        }

        void setNormalizedPosition(double x, double y) {
            this.posX = CatPlacement.clampNormalized(x);
            this.posY = CatPlacement.clampNormalized(y);
            updatePositionReadout(this.posX, this.posY);
            repaint();
        }

        private void updateHoverCursor(Point p) {
            if (dragging || computeCatBounds().contains(p)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            } else {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        }

        private void updateFromMouse(int mouseX, int mouseY) {
            Rectangle anchor = computeAnchorBounds();
            Rectangle cat = computeCatBounds();
            int targetX = mouseX - grabOffsetX;
            int targetY = mouseY - grabOffsetY;

            int usableW = anchor.width - cat.width - 2 * PREVIEW_MARGIN;
            int usableH = anchor.height - cat.height - 2 * PREVIEW_MARGIN;
            double nextX = usableW > 0
                    ? (double) (targetX - anchor.x - PREVIEW_MARGIN) / usableW
                    : 0.5;
            double nextY = usableH > 0
                    ? (double) (targetY - anchor.y - PREVIEW_MARGIN) / usableH
                    : 0.0;
            setNormalizedPosition(nextX, nextY);
        }

        private Rectangle computeAnchorBounds() {
            int w = getWidth() > 0 ? getWidth() : getPreferredSize().width;
            int h = getHeight() > 0 ? getHeight() : getPreferredSize().height;
            if (anchorCombo != null && anchorCombo.getSelectedItem() == CatAnchor.EDITOR) {
                int sidebarW = (int) Math.round(w * 0.22);
                int topBarH = 14;
                int terminalH = (int) Math.round(h * 0.27);
                return new Rectangle(sidebarW, topBarH, w - sidebarW, h - topBarH - terminalH);
            }
            return new Rectangle(0, 0, w, h);
        }

        private Rectangle computeCatBounds() {
            int w = getWidth() > 0 ? getWidth() : getPreferredSize().width;
            int h = getHeight() > 0 ? getHeight() : getPreferredSize().height;

            int[] raw = CatAnimation.rawSize();
            int rawW = raw != null && raw[0] > 0 ? raw[0] : 576;
            int rawH = raw != null && raw[1] > 0 ? raw[1] : 1024;
            int desiredH = sizeSlider != null ? sizeSlider.getValue() : CatClappingSettings.DEFAULT_CAT_SIZE_PX;
            int effectiveRealH = Math.min(desiredH, Math.max(refWindowH - 16, 32));
            int catH = Math.max(18, Math.min(h - 2 * PREVIEW_MARGIN,
                    (int) Math.round(effectiveRealH * ((double) h / refWindowH))));
            int catW = Math.max(10, (int) Math.round(catH * ((double) rawW / rawH)));

            Rectangle anchor = computeAnchorBounds();
            Point p = CatPlacement.computePoint(
                    posX, posY,
                    anchor.x, anchor.y, anchor.width, anchor.height,
                    w, h,
                    catW, catH, PREVIEW_MARGIN);
            return new Rectangle(p.x, p.y, catW, catH);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int w = getWidth();
                int h = getHeight();
                Color baseBg = UIManager.getColor("TextField.background");
                if (baseBg == null) {
                    baseBg = new Color(43, 45, 48);
                }
                Color panelBg = UIManager.getColor("Panel.background");
                if (panelBg == null) {
                    panelBg = new Color(60, 63, 65);
                }
                Color border = UIManager.getColor("Component.borderColor");
                if (border == null) {
                    border = Color.GRAY;
                }
                Color accent = UIManager.getColor("Component.focusColor");
                if (accent == null) {
                    accent = new Color(75, 139, 244);
                }

                // IDE window chrome
                g2.setColor(baseBg);
                g2.fillRect(0, 0, w, h);

                int sidebarW = (int) Math.round(w * 0.22);
                int topBarH = 14;
                int terminalH = (int) Math.round(h * 0.27);

                // Top bar, sidebar, bottom terminal blocks
                g2.setColor(panelBg);
                g2.fillRect(0, 0, w, topBarH);
                g2.fillRect(0, topBarH, sidebarW, h - topBarH);
                g2.fillRect(sidebarW, h - terminalH, w - sidebarW, terminalH);

                g2.setColor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 140));
                g2.drawLine(0, topBarH, w, topBarH);
                g2.drawLine(sidebarW, topBarH, sidebarW, h);
                g2.drawLine(sidebarW, h - terminalH, w, h - terminalH);

                // Highlight active anchor area
                Rectangle anchor = computeAnchorBounds();
                if (anchorCombo != null && anchorCombo.getSelectedItem() == CatAnchor.EDITOR) {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 24));
                    g2.fillRect(anchor.x, anchor.y, anchor.width, anchor.height);
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
                    g2.drawRect(anchor.x, anchor.y, Math.max(0, anchor.width - 1), Math.max(0, anchor.height - 1));
                }

                // Draw cat preview
                Rectangle cat = computeCatBounds();
                BufferedImage img = catImage;
                if (img != null) {
                    g2.drawImage(img, cat.x, cat.y, cat.width, cat.height, null);
                } else {
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
                    g2.fillRoundRect(cat.x, cat.y, cat.width, cat.height, 6, 6);
                }

                // Grab outline around cat
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{4f, 3f}, 0));
                g2.drawRoundRect(cat.x, cat.y, Math.max(1, cat.width - 1), Math.max(1, cat.height - 1), 6, 6);

                // Outer border
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(border);
                g2.drawRect(0, 0, w - 1, h - 1);
            } finally {
                g2.dispose();
            }
        }

        private static Dimension resolveIdeWindowSize() {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                JFrame frame = WindowManager.getInstance().getFrame(project);
                if (frame != null && frame.getWidth() > 0 && frame.getHeight() > 0) {
                    return new Dimension(frame.getWidth(), frame.getHeight());
                }
            }
            return new Dimension(1440, 900);
        }
    }
}
