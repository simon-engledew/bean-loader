package com.engledew.data;

import java.beans.IntrospectionException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * <p>Helper methods to inject the results of a prepared statement into Java beans and return them in a variety of formats.</p>
 * 
 * <p>Be aware that the majority of the helper methods within BeanLoader throw a variety of RuntimeExceptions (including the {@code RuntimeSQLException}). This is to ensure that
 * {@code BeanLoader} methods can conform to the {@code Iterable} specification. It is advisable to handle these exceptions appropriately.</p>
 * 
 * @author Simon Engledew
 *
 */
public final class BeanLoader
{
    public static class RuntimeSQLException extends RuntimeException
    {
        private static final long serialVersionUID = 2272112243806512649L;

        public RuntimeSQLException(SQLException cause)
        {
            super(cause);
        }
    }
    
    /* sealed static class */
    private BeanLoader() {}

    /**
     * 
     * @author Simon Engledew
     *
     * @param <T>
     */
    public static interface KeyedBean<T> {
        public T getKey();
    }
    /**
     * 
     * 
     * @author Simon Engledew
     *
     */
    public static interface TypeLoader {
        public Object load(Class<?> type, String value);
    }
    
    /**
     * Iterates through the results of a prepared statement and returns the data injected into beans of type {@code T}.
     * 
     * @author Simon Engledew
     *
     * @param <T>
     */
    private static class ResultLoader<T> implements Iterable<T>    
    {
        private final PreparedStatement preparedStatement;
        private final Class<T> type;
        
        private ResultLoader(Class<T> type, PreparedStatement preparedStatement)
        {
            this.type = type;
            this.preparedStatement = preparedStatement;
        }
        
        public Iterator<T> iterator()
        {
            try
            {
                final ResultSet resultSet = this.preparedStatement.executeQuery();
                final BeanProperty<T>[] properties = BeanLoader.deriveProperties(this.type, resultSet);

                return new Iterator<T>()
                {
                    private boolean next;
                    
                    public synchronized boolean hasNext()
                    {
                        try
                        {
                            return next || (next = resultSet.next()) || this.close();
                        }
                        catch (RuntimeException e) { this.close(); throw e; }
                        catch (SQLException e) { throw new RuntimeSQLException(e); }
                        catch (Throwable t) { this.close(); throw new RuntimeException("unable to load next row from ResultSet", t); }
                    }
                    
                    public synchronized T next()
                    {
                        try
                        {
                            if (this.next)
                            {
                                this.next = false;
                                
                                return BeanLoader.newInstance(type, properties, resultSet);
                            }
                            
                            throw new NoSuchElementException();
                        }
                        catch (RuntimeException e) { this.close(); throw e; }
                        catch (Throwable t) { this.close(); throw new RuntimeException(String.format("failed to iterate through ResultSet for bean of type '%s'", type.getSimpleName()), t); }
                    }
                    
                    public void remove()
                    {
                        throw new UnsupportedOperationException();
                    }
                    
                    private boolean close()
                    {
                        try
                        {
                            resultSet.close();
                        }
                        catch (RuntimeException e) { throw e; }
                        catch (SQLException e) { throw new RuntimeSQLException(e); }
                        catch (Throwable t) { throw new RuntimeException("unable to close ResultSet", t); }

                        return false;
                    }
                };
            }
            catch (RuntimeException e) { throw e; }
            catch (Throwable t) { throw new RuntimeException(t); }
        }
    }

    /**
     * Returns the first result from the {@code preparedStatement}'s resultSet without creating an {@code Iterable}:
     * 
     * <pre>
     * Person person = BeanLoader.first(Person.class, connection.prepareStatement("SELECT * FROM people LIMIT 1"));
     * </pre>
     * 
     * @param <T>
     * @param type
     * @param preparedStatement
     * @return
     * @throws RuntimeSQLException if an SQLException occurs
     */
    public static <T> T first(Class<T> type, PreparedStatement preparedStatement)
    {
        ResultSet resultSet = null;
        
        try
        {
            resultSet = preparedStatement.executeQuery();

            return resultSet.next() ? BeanLoader.newInstance(type, BeanLoader.deriveProperties(type, resultSet), resultSet) : null;
        }
        catch (RuntimeException e) { throw e; }
        catch (SQLException e) { throw new RuntimeSQLException(e); }
        catch (Throwable t) { throw new RuntimeException(t); }
        finally
        {
            try
            {
                if (resultSet != null) resultSet.close();
            }
            catch (SQLException e) { throw new RuntimeSQLException(e); }
        }
    }
    
    /**
     * 
     * 
     * @param <T>
     * @param type
     * @param preparedStatement
     * @return
     */
    public static <T> Iterable<T> each(Class<T> type, PreparedStatement preparedStatement)
    {
        return new ResultLoader<T>(type, preparedStatement);
    }
    
    /**
     * Iterates through {@code each} bean derived from {@code preparedStatement}'s results and {@code add}s them to {@code collection}:
     * 
     * <pre>
     * {@code List<Person> people = BeanLoader.collect(Person.class, new ArrayList<Person>(), connection.prepareStatement("SELECT * FROM people"));}
     * </pre>
     * 
     * @param <T>
     * @param <C>
     * @param type
     * @param collection
     * @param preparedStatement
     * @return
     */
    public static <T, C extends Collection<T>> C collect(Class<T> type, C collection, PreparedStatement preparedStatement)
    {
        for (T t : BeanLoader.each(type, preparedStatement))
        {
            collection.add(t);
        }
        return collection;
    }
    
    /**
     * Iterates through {@code each} {@code KeyedBean} derived from {@code preparedStatement}'s results and {@code put}s them into {@code map} against their keys.
     * 
     * <pre>
     *  public class Person implements {@code KeyedBean<Integer>}
     *  {
     *      private int id;
     *      private String name;
     *      
     *      public Integer getKey() { return this.id; }
     *      public Integer getId() { return this.id; }
     *      public void setId(int id) { this.id = id; }
     *      
     *      ...
     *  }
     *  
     *  ...
     *  
     *  {@code SortedMap<Integer, Person> people = BeanLoader.map(Person.class, new TreeMap<Integer, Person>, connection.prepareStatement("SELECT * FROM people"));}
     * 
     * </pre>
     * 
     * @param <B>
     * @param <T>
     * @param <M>
     * @param type
     * @param map
     * @param preparedStatement
     * @return
     */
    public static <B, T extends KeyedBean<B>, M extends Map<B, T>> M map(Class<T> type, M map, PreparedStatement preparedStatement)
    {
        for (T t : BeanLoader.each(type, preparedStatement))
        {
            map.put(t.getKey(), t);
        }
        return map;
    }
    
    /**
     * Iterates through the column names described by the {@code ResultSetMetaData} in {@code resultSet} and 
     * collects a corresponding bean {@code Property} array.  
     * 
     * @param <T>
     * @param type
     * @param resultSet
     * @return
     * @throws SQLException
     * @throws IntrospectionException
     * @see BeanLoader.BeanProperty
     */
    private static <T> BeanProperty<T>[] deriveProperties(Class<T> type, ResultSet resultSet)
    throws SQLException, IntrospectionException
    {
        @SuppressWarnings("unchecked")
        BeanProperty<T>[] properties = new BeanProperty[resultSet.getMetaData().getColumnCount()];

        for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++)
        {
            properties[i - 1] = BeanProperty.newInstance(type, resultSet.getMetaData().getColumnName(i));
        }
        
        return properties;
    }
    
    /**
     * Creates a new instance of {@code type} and writes the data contained within {@code resultSet} to it.
     * 
     * @param <T>
     * @param type
     * @param properties
     * @param resultSet
     * @return
     * @throws 
     */
    private static <T> T newInstance(Class<T> type, BeanProperty<T>[] properties, ResultSet resultSet)
    {
        try
        {
            T instance = type.newInstance();
            
            for (int i = 1; i <= properties.length; i++)
            {
                properties[i - 1].write(instance, resultSet.getString(i));
            }
            
            return instance;
        }
        catch (RuntimeException e) { throw e; }
        catch (SQLException e) { throw new RuntimeSQLException(e); }
        catch (Throwable t)
        {
            throw new RuntimeException("unable to instantiate bean type '" + type.getSimpleName() + "'. if it is an inner class, did you forget the static keyword?", t);
        }
    }
}
