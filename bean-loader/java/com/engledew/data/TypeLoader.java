package com.engledew.data;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import com.engledew.reflection.Introspection;

public abstract class TypeLoader
{
    public abstract Object load(Class<?> type, String value);
    
    private static final Map<Class<?>, TypeLoader> COMPLEX_TYPES = new HashMap<Class<?>, TypeLoader>();
    public static <C> void removeType(Class<C> type)
    {
        COMPLEX_TYPES.remove(type);
    }
    public static <C> void addType(Class<C> type, TypeLoader loader)
    {
        COMPLEX_TYPES.put(type, loader);
    }
    
    public static void setDateFormat(final DateFormat dateFormat)
    {
        DATE_FORMAT = new ThreadLocal<DateFormat>()
        {
            protected synchronized DateFormat initialValue()
            {
                return dateFormat;
            }
        };
    }
    private static ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>()
    {
        protected synchronized DateFormat initialValue()
        {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            return simpleDateFormat;
        }
    };
    private static final TypeLoader ENUM_TYPE_LOADER = new TypeLoader()
    {
        @SuppressWarnings("unchecked")
        public Object load(Class<?> type, String value)
        {
            return value == null ? null : Enum.valueOf((Class<? extends Enum>)type, value);
        }
    };
    private static final TypeLoader DEFAULT_TYPE_LOADER = new TypeLoader()
    {
        public Object load(Class<?> type, String value)
        {
            try
            {
                return value == null ? null : type.getConstructor(String.class).newInstance(value);
            }
            catch (RuntimeException e) { throw e; }
            catch (Throwable t) { throw new RuntimeException(String.format("failed to construct a value for '%s'", type.getSimpleName()), t); }
        }
    };
    static
    {
        TypeLoader.addType(Date.class, new TypeLoader()
        {
            public Object load(Class<?> type, String value)
            {
                try
                {
                    return value == null ? null : DATE_FORMAT.get().parse(value);
                }
                catch (ParseException e)
                {
                    throw new IllegalArgumentException(String.format("failed to parse the date '%s'", value), e);
                }
            }
        });
        
        TypeLoader primitiveLoader = new TypeLoader()
        {
            public Object load(Class<?> type, String value)
            {
                try
                {
                    return Introspection.getDerivativeType(type).getConstructor(String.class).newInstance(value == null ? "0" : value);
                }
                catch (RuntimeException e) { throw e; }
                catch (Throwable t) { throw new RuntimeException(t); }
            }
        };
        
        TypeLoader booleanLoader = new TypeLoader()
        {
            public Object load(Class<?> type, String value)
            {
                return value.equals("1") ? true : Boolean.parseBoolean(value);
            }
        };
        
        TypeLoader.addType(int.class, primitiveLoader);
        TypeLoader.addType(long.class, primitiveLoader);
        TypeLoader.addType(float.class, primitiveLoader);
        TypeLoader.addType(double.class, primitiveLoader);
        TypeLoader.addType(boolean.class, booleanLoader);
        TypeLoader.addType(Boolean.class, booleanLoader);
    }
    
    private static <T> T pick(T one, T two)
    {
        return one != null ? one : two;
    }
    
    public static TypeLoader getTypeLoader(Class<?> type)
    {
        return pick(Enum.class.isAssignableFrom(type) ? ENUM_TYPE_LOADER : COMPLEX_TYPES.get(type), DEFAULT_TYPE_LOADER);
    }
    
    public static Object newInstance(Class<?> type, String value)
    {
        return getTypeLoader(type).load(type, value);
    }
}