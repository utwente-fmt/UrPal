package com.uppaal.plugin;

import com.uppaal.plugin.PluginWorkspace;

/**
 * Plugin provides an interface for Uppaal plugin.
 * Currently plugin may provide several workspaces (loaded as GUI components in tabs).
 *
 * An implementation may provide a constructor with single argument of 
 * type com.uppaal.plugin.Registry to benefit from and integrate with Uppaal 
 * framework. Otherwise a default constructor without arguments will be called.
 *
 * The plugin implementation should be packed into a single jar archive and be 
 * put into "plugins" directory of Uppaal installation.
 * The jar archive should contain manifest with attribute "Uppaal-Plugin"
 * which denotes the name of plugin implementation class within jar,
 * i.e. a line like "Uppaal-Plugin: com.example.MyPlugin" where MyPlugin 
 * is a class implementing Plugin interface.
 *
 * @see com.uppaal.plugin.Registry
 * @see com.uppaal.plugin.PluginWorkspace
 */
public interface Plugin
{
    /**
     * The method is called when GUI is being built and it should prepare
     * the workspaces to be displayed in the TabbedPane.
     * @return an array of workspaces to be loaded in tabs.
     * @see com.uppaal.plugin.PluginWorkspace
     */
    public PluginWorkspace[] getWorkspaces();

    /* 
       Other types of plugin objects (e.g. menus, toolbars) can be added here 
       in the future if/when needed.
    */
}
