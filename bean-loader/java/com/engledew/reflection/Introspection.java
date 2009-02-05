package com.engledew.reflection;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Introspection
{
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TYPES;
    static 
    {
        Map<Class<?>, Class<?>> primitiveTypes = new HashMap<Class<?>, Class<?>>();
        
        primitiveTypes.put(int.class, Integer.class);
        primitiveTypes.put(long.class, Long.class);
        primitiveTypes.put(double.class, Double.class);
        primitiveTypes.put(float.class, Float.class);
        
        PRIMITIVE_TYPES = Collections.unmodifiableMap(primitiveTypes);
    }
    
    public static Class<?> getDerivativeType(Class<?> type)
    {
        Class<?> derivativeType = PRIMITIVE_TYPES.get(type);
        
        return derivativeType != null ? derivativeType : type;
    }
    
    private static final HashMap<Class<?>, Map<String, PropertyDescriptor>> PROPERTY_DESCRIPTOR_MAPS = new HashMap<Class<?>, Map<String, PropertyDescriptor>>();

    public static synchronized Map<String, PropertyDescriptor> getPropertyDescriptorMap(Class<?> klass)
    throws IntrospectionException
    {
        Map<String, PropertyDescriptor> propertyDescriptors = PROPERTY_DESCRIPTOR_MAPS.get(klass);
        
        if (propertyDescriptors == null)
        {
            propertyDescriptors = new HashMap<String, PropertyDescriptor>(); 
            
            for (PropertyDescriptor propertyDescriptor : Introspector.getBeanInfo(klass).getPropertyDescriptors())
            {
                propertyDescriptors.put(propertyDescriptor.getName(), propertyDescriptor);
            }

            PROPERTY_DESCRIPTOR_MAPS.put(klass, propertyDescriptors);
        }

        return propertyDescriptors;
    }
    
    public static PropertyDescriptor getPropertyDescriptor(Class<?> klass, String property)
    throws IntrospectionException
    {
        return Introspection.getPropertyDescriptorMap(klass).get(property);
    }
}

