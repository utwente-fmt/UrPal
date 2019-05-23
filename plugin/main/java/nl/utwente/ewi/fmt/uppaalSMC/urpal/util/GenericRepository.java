package nl.utwente.ewi.fmt.uppaalSMC.urpal.util;

import com.uppaal.plugin.Repository;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 *
 * @author Marius Mikucionis <marius@cs.aau.dk>
 * @param <Item>
 */
public class GenericRepository<Item> implements Repository<Item>
{
    protected final String name;
    protected Item item = null;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public GenericRepository(final String name) { this.name = name; }

    @Override
    public String getName() { return name; }

    @Override
    public Item get() { return item; }

    @Override
    public void set(Item newitem)
    {
        if (this.item != newitem) {
            Item olditem = item;
            item = newitem;
            pcs.firePropertyChange(Repository.ChangeType.REPLACED.name(), olditem, newitem);
        }
    }

    @Override
    public void addListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    @Override
    public void addListener(Repository.ChangeType change, PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(change.name(), listener);
    }

    @Override
    public void removeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public void removeListener(ChangeType change, PropertyChangeListener listener)
    {
        pcs.removePropertyChangeListener(change.name(), listener);
    }

    @Override
    public void fire(Repository.ChangeType change)
    {
        pcs.firePropertyChange(change.name(), null, item);
    }
}