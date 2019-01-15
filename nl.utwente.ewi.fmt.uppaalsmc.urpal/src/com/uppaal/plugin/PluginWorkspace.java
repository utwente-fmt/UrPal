package com.uppaal.plugin;

import java.awt.Component;
import javax.swing.Icon;

import com.uppaal.plugin.Plugin;

/**
 * PluginWorkspace is an interface of a plugin to UPPAAL.
 * @see Plugin
 */
public interface PluginWorkspace 
{
    /**
     * The title appears in TabbedPane title of this workspace.
     * @return title text
     * @see javax.swing.JTabbedPane#addTab(java.lang.String, javax.swing.Icon, java.awt.Component, java.lang.String)
     */
    public String getTitle();
    /**
     * Returns the icon that appears in TabbedPane title of this workspace.
     * @return icon image
     * @see javax.swing.JTabbedPane#addTab(java.lang.String, javax.swing.Icon, java.awt.Component, java.lang.String)
     */
    public Icon getIcon();
    /**
     * Returns the tool tip that appears on TabbedPane title of this workspace.
     * @return tooltip text
     * @see javax.swing.JTabbedPane#addTab(java.lang.String, javax.swing.Icon, java.awt.Component, java.lang.String)
     */
    public String getTitleToolTip();

    /**
     * Returns GUI representation of a workspace to appear in UPPAAL.
     * @return GUI component
     */
    public Component getComponent();

    /**
     * Returns the development index of this workspace which will determine
     * the position among other tabs.
     * The index is constant and is in relative scale within development model:
     * it is lower if the workspace is involved earlier in developement, 
     * and is greater if later.
     * For example, waterfall model assumes activities in the following 
     * sequence: specification, design, detail design, coding, testing, 
     * installation, integration testing, deployment support.
     * UPPAAL workspaces have the following indices:
     * editor 100, symbolic simulator 200, concrete simulator 300, verifier 400.
     * Hint: if your workspace generates timed automata model, then it can be 
     * placed before editor by using index less than 100; if workspace does 
     * some model transformation before loading into simulator, then it should 
     * be between 100 and 400. If workspace involves code generation then it 
     * can be after verifier by having an index greater than 400.
     * The tab order can appear arbitrary if there are several workspaces 
     * sharing the same index.
     * @return position index
     */
    public int getDevelopmentIndex();

    public boolean getCanZoom();
    public boolean getCanZoomToFit();
    public double getZoom();
    public void setZoom(double value);
    public void zoomToFit();
    public void zoomIn();
    public void zoomOut();

    /** 
     * Called whenever the status of this workspace changes. 
     * @param selected indicates whether the workspace is currently selected
     * in a tabbedpane.
     */
    public void setActive(boolean selected);
}
