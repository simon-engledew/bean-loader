package com.engledew.data;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.sql.SQLException;

import com.engledew.reflection.Introspection;

/**
 * Used to cache the write methods for the bean properties needed when injecting data into a java bean.
 * 
 * @author Simon Engledew
 *
 * @param <T>
 */
public class BeanProperty<T>
{
    public static <T> BeanProperty<T> newInstance(Class<T> type, String property)
    throws SQLException
    {
        return new BeanProperty<T>(type, property);
    }
    
    private String property;
    private final Method readMethod, writeMethod;
    private final Class<?> propertyType;
    private final TypeLoader loader;

    private BeanProperty(Class<T> type, String property)
    throws SQLException
    {
        PropertyDescriptor propertyDescriptor = null;
        
        try
        {
            propertyDescriptor = Introspection.getPropertyDescriptor(type, property);
        }
        catch (IntrospectionException e) {}
        
        if (propertyDescriptor == null)
        {
            throw new IllegalAccessError(String.format("property '%s' not found", property));
        }
        
        this.property = property;
        this.readMethod = propertyDescriptor.getReadMethod();
        this.writeMethod = propertyDescriptor.getWriteMethod();
        this.propertyType = propertyDescriptor.getPropertyType();
        this.loader = TypeLoader.getTypeLoader(this.propertyType);
    }
    
    public Object read(T instance)
    {
        if (this.readMethod == null)
        {
            throw new IllegalAccessError(String.format("getter for '%s' not found", property));
        }
        
        try
        {
            return readMethod.invoke(instance);
        }
        catch (RuntimeException e) { throw e; }
        catch (Throwable t)
        {
            throw new IllegalAccessError(String.format("unable to read a '%s' from getter '%s' using value '%s'", property));
        }
    }
    
    public void write(T instance, String value)
    {
        if (this.writeMethod == null)
        {
            throw new IllegalAccessError(String.format("setter for '%s' not found", property));
        }
        
        try
        {
            writeMethod.invoke(instance, this.loader.load(this.propertyType, value));
        }
        catch (RuntimeException e) { throw e; }
        catch (Throwable t)
        {
            throw new IllegalAccessError(String.format("unable to construct a '%s' for setter '%s' using value '%s'", this.propertyType.getSimpleName(), writeMethod.getName(), value));
        }
    }
}