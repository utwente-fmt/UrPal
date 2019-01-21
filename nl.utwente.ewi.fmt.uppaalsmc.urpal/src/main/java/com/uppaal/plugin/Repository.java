package com.uppaal.plugin;

import java.beans.PropertyChangeListener;


/**
 * Repository is a shared placeholder for one item.
 * Repository provides an interface for consumers to be notified upon 
 * replacement of an item or changes in its content or internal structure.
 * @param <Item> the type of the item.
 */
public interface Repository<Item>
{
    /**
     * The name of the repository used by Registry.
     * @return unique name
     */
    public String getName();
    
    public enum ChangeType { 
        UPDATED,      // the item content has changed (e.g. text modified, node added or removed)
        RESTRUCTURED, // the item structure has changed (e.g. node has been moved)
        REPLACED      // the item was replaced entirely (completely new value)
    };

    /**
     * Adds a listener to be notified when the content of the repository is changed.
     * @param listener 
     */
    public void addListener(PropertyChangeListener listener);
    public void addListener(ChangeType change, PropertyChangeListener listener);
    public void removeListener(PropertyChangeListener listener);
    public void removeListener(ChangeType change, PropertyChangeListener listener);

    /**
     * The item the repository is currently holding.
     * @return the current item, or null if there is no item.
     */
    public Item get();

    /**
     * Puts a new item into repository, discards the old one and 
     * notifies listeners that the item has been replaced.
     * @param newitem a new value for the item
     */
    public void set(Item newitem);

    /**
     * Notifies all the listeners about the change.
     * No need to call this if set() is already called.
     * @param change 
     */
    public void fire(ChangeType change);
}
