package com.uppaal.plugin;

import java.beans.PropertyChangeListener;
import java.util.Set;

import com.uppaal.plugin.Repository;

/**
 * Registry implements a facade design pattern to components in Uppaal GUI.
 */
public interface Registry
{
    /**
     * Registers a repository for a given name of resource.The name of the repository has to be unique.
     * @param repository reference to generically typed repository.
     * @return previously stored repository under the same name if any.
     */
    public Repository publishRepository(Repository repository);
    /**
     * Removes the repository from the registry by its name.
     * @param name
     * @return the repository if any
     */
    public Repository retractRepository(String name);
    /**
     * Provides the registered repository by its unique name.
     * @param name of the repository
     * @return the repository if any
     */
    public Repository getRepository(String name);
    /**
     * Provides all names of the registered repositories.
     * @return 
     */
    public Set<String> getRepositoryNames();

    /**
     * Adds a listener for the repository publishing events.
     * @param listener to receive the notifications
     */
    public void addListener(PropertyChangeListener listener);
    /**
     * Adds a listener for specific repository publishing events.
     * @param name of the repository
     * @param listener to receive the notifications
     */
    public void addListener(String name, PropertyChangeListener listener);
    /**
     * Removes the specific listener from notifications about repository publishing.
     * @param listener used to receive the notifications
     */
    public void removeListener(PropertyChangeListener listener);    
    /**
     * Removes the listener from receiving specific repository publishing notifications.
     * @param name of the repository
     * @param listener used to receive the notifications
     */
    public void removeListener(String name, PropertyChangeListener listener);    
}
