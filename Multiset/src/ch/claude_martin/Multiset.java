package ch.claude_martin;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;

import java.io.Serializable;
import java.lang.SuppressWarnings;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import edu.umd.cs.findbugs.annotations.*;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;

/**
 * {@link Map} based implementation of a Multiset. It doesn't maintain sorting or insertion order.
 * But it's not a regular set, because you can add an element more than once. If an element is
 * already present that equals the element to be inserted then it's multiplicity (count) is
 * incremented by one. <code>null</code> is allowed as an element.
 * 
 * <p>
 * An element with multiplicity zero is completely removed from the data structure and will not
 * occur as a tuple of {@code (element, 0)}. This needs to be considered when using methods such as
 * {@link #setMultiplicities(ToIntBiFunction)}, {@link #forEach(ObjIntConsumer)}, {@link #entries()}
 * , and {@link #merge(Multiset, IntBinaryOperator)}.
 * 
 * <p>
 * This can also be used like a <i>TreeList</i> (<code>Multiset.wrap(TreeMap::new)</code>) or even
 * to wrap any collection that behaves like a multiset (<code>Multiset.wrap(myList)</code>).
 * 
 * <p>
 * Any implementation for {@link Map} can be used. But since some do not allow <tt>null</tt> as a
 * key or throw {@link ClassCastException} on an inappropriate key type, those exceptions will be
 * ignored and it is assumed that the element is not in the Multiset.<br/>
 * Example: <code>Multiset.of(1,2,3).contains("hello!");</code> // returns false
 * <p>
 * <a href="https://humanoidreadable.wordpress.com/2015/02/20/multiset-in-java/">blog entry</a>
 * <p>
 * Copyright: You can use this for whatever you like! No restrictions.
 * 
 * @author Claude Martin
 *
 * @param <T>
 *          The type of the elements
 */
@DefaultAnnotationForParameters(NonNull.class)
public final class Multiset<T> extends AbstractCollection<T> implements Serializable {
  private static final long         serialVersionUID = -7083567870279959503L;

  /** The backing map. */
  final Map<T, Integer>             map;
  /** View as map. Lazy. This is the same as {@link #map} if and only if this is unmodifiable. */
  private transient Map<T, Integer> view             = null;

  /**
   * Total size of this multiset.
   * 
   * @implNote Modification must always be performend on the {@link #map} first. This will fail if
   *           this multiset {@link #isUnmodifiable() is unmodifiable}.
   */
  int                               size             = 0;

  public Multiset() {
    this.map = new HashMap<>();
  }

  /**
   * @see #wrap(Map)
   */
  private Multiset(final Map<T, Integer> map, final int size) {
    this.map = map;
    this.size = size;
    this.checkSize();
  }

  /** Creates an unmodifiable Multiset. */
  private Multiset(final Multiset<T> multiset, final boolean unmodifiable) {
    assert unmodifiable;
    this.map = this.view = Collections.unmodifiableMap(multiset.map);
    this.size = multiset.size;
    this.checkSize();
    return;
  }

  /** Creates an empty Multiset. */
  private Multiset(final boolean empty) {
    this.map = this.view = Collections.emptyMap();
    this.size = 0;
  }

  /** Creates a new Multiset and adds all elements from the given collection. */
  public Multiset(final Collection<? extends T> coll) {
    requireNonNull(coll, "coll");
    this.map = new HashMap<>(coll.size());
    for (final T t : coll)
      this.map.merge(t, 1, Integer::sum);
    this.size = coll.size();
    this.checkSize();
  }

  /**
   * Creates a new multiset and adds all elements from the given multiset. The returned multiset is
   * modifiable and not synchronized.
   */
  public Multiset(final Multiset<? extends T> multiset) {
    requireNonNull(multiset, "multiset");
    multiset.checkSize();
    this.map = new HashMap<>(multiset.map);
    this.size = multiset.size;
    this.checkSize();
  }

  /**
   * Creates a new {@link Multiset} containing all given elements.
   * 
   * <p>
   * Each is added as many times as it is passed to this method. Use chained calls to
   * {@link #add(Object, int)} to set multiplicities.
   * 
   * @param elements
   *          The elements
   * @return <code>new Multiset<>(asList(elements));</code>
   */
  @SafeVarargs
  @NonNull
  public static <T> Multiset<T> of(final T... elements) {
    requireNonNull(elements, "elements");
    return new Multiset<>(asList(elements));
  }

  /**
   * Creates a new {@link Multiset} containing all given elements, with the multiplicity defined by
   * the given function.
   * 
   * @param set
   *          The elements
   * @param m
   *          The multiplicity
   * @return new {@link Multiset} greated from the given data.
   */
  @NonNull
  public static <T> Multiset<T> of(final Set<? extends T> set, final ToIntFunction<? super T> m) {
    requireNonNull(set, "set");
    requireNonNull(m, "m");
    final Multiset<T> result = new Multiset<>();
    for (final T t : set)
      result._set(t, 0, m.applyAsInt(t));
    return result;
  }

  /**
   * Wraps a given map. Note that direct modification of the map will invalidate the
   * {@link Multiset}. This calculates the current size of the {@link Multiset}. Values of null, 0
   * or less than 0 will cause an exception.
   * 
   * @param map
   *          The map
   * @throws NullPointerException
   *           If any key is mapped to null.
   * @throws IllegalArgumentException
   *           If any value is equal to or less than 0.
   * @see #asMap()
   * @return A Multiset that is backed by the given map.
   */
  @CheckReturnValue
  @NonNull
  public static <T> Multiset<T> wrap(final Map<T, Integer> map) {
    requireNonNull(map, "map");
    final int size;
    if (map.isEmpty())
      size = 0;
    else
      size = map.values().parallelStream().mapToInt(i -> i).filter(i -> {
        if (i <= 0)
          throw new IllegalArgumentException("Illegal multiplicity: " + i);
        return true;
      }).sum();
    return new Multiset<>(map, size);
  }

  /**
   * Wraps a map. The given supplier can be a constructor, but the map does not have to be empty.
   * Any modification of the map will invalidate the multiset.
   * <p>
   * Example: <code>Multiset.wrap(TreeMap::new);</code>
   * 
   * @param ctor
   *          The constructor
   * @return A Multiset that is backed by a newly created map.
   * @see #asMap()
   */
  @CheckReturnValue
  @NonNull
  public static <T> Multiset<T> wrap(final Supplier<? extends Map<T, Integer>> ctor) {
    requireNonNull(ctor, "ctor");
    return wrap(requireNonNull(ctor.get(), "null was supplied instead of a map"));
  }

  /**
   * This will use a given collection and will apply all operations on the created multiset to the
   * collection. The collection should behave like a multiset, but any {@link List}-like
   * implementation can be used. Any direct modification of the collection will invalidate the
   * multiset.
   * 
   * @param coll
   *          A collection of elements. This must not be a {@link Set} nor a {@link Multiset}.
   * @return New Multiset that will apply all changes to the given collection.
   * 
   * @throws IllegalArgumentException
   *           If a {@link Set} or a {@link Multiset} is used.
   * @see #asMap()
   * @implNote This uses {@link FXCollections#observableMap(Map)} to observe the created Multiset.
   *           So the multiset isn't actually backed by the collection. All changes are simply
   *           applied to it. Do not expect good performance.
   */
  @CheckReturnValue
  @NonNull
  public static <T> Multiset<T> wrap(final Collection<T> coll) {
    requireNonNull(coll, "coll");
    if (coll instanceof Set)
      throw new IllegalArgumentException("Set can not be wrapped.");
    if (coll instanceof Multiset)
      throw new IllegalArgumentException("Multiset can not be wrapped.");

    final Map<T, Integer> map = new HashMap<>();
    for (final T t : coll)
      map.merge(t, 1, Integer::sum);
    final ObservableMap<T, Integer> obs = FXCollections.observableMap(map);
    obs.addListener(new MapChangeListener<T, Integer>() {
      @Override
      public void onChanged(final Change<? extends T, ? extends Integer> change) {
        final T t = change.getKey();
        final boolean wasRemoved = change.wasRemoved();
        final boolean wasAdded = change.wasAdded();
        final int added = wasAdded ? change.getValueAdded() : 0;
        final int removed = wasRemoved ? change.getValueRemoved() : 0;
        if (!wasAdded && wasRemoved)
          coll.removeIf(x -> Objects.equals(x, t));
        else if (removed > added)
          for (int i = 0; i < removed - added; i++)
            coll.remove(t);
        else if (added > removed)
          for (int i = 0; i < added - removed; i++)
            coll.add(t);
        // else : added == removed
      }
    });

    return new Multiset<>(obs, coll.size());
  }

  /**
   * Adds the object and returns true.
   * 
   * @see #insert(Object)
   */
  @Override
  public boolean add(final @Nullable T t) {
    this.insert(t);
    return true;
  }

  /**
   * Changes the multiplicity of an element. The value can be negative.
   * 
   * This returns a reference to the multiset for method chaining.
   * 
   * @param element
   *          an element
   * @param value
   *          how much the multiplicity should be altered
   * @see #setMultiplicity(T, int)
   * @see #add(T)
   * @see #of(Object...)
   * @return a reference to this object.
   */
  @NonNull
  public Multiset<T> add(final @Nullable T element, final int value) {
    if (value != 0) {
      final int oldM = this.getMultiplicity(element);
      final int newM = oldM + value;
      if (newM < 0)
        throw new NoSuchElementException("Element " + element + " could not be removed");
      this._set(element, oldM, newM);
    }
    return this;
  }

  /**
   * Sets the multiplicity of a certain element. The element is removed if m is 0 or added if it was
   * 0. Therefore this may lead to a modification of the underlying data structure. The
   * {@link #iterator() Iterator} supports @link {@link Iterator#remove() removal} of elements
   * during iteration.
   * 
   * @param element
   *          the element
   * @param m
   *          new multiplicity
   * @see #add(Object, int)
   * @returns the old multiplicity.
   */
  public int setMultiplicity(final T element, final int m) {
    if (m < 0)
      throw new IllegalArgumentException("m < 0");
    final int i = this.getMultiplicity(element);
    if (m == i)
      return i;
    this._set(element, i, m);
    return i;
  }

  /** Internal use only! */
  final void _set(final T element, final int oldMultiplicity, final int newMultiplicity) {
    assert newMultiplicity >= 0;
    if (oldMultiplicity == newMultiplicity)
      return;
    if (newMultiplicity == 0)
      this.map.remove(element);
    else
      this.map.put(element, newMultiplicity);
    this.size += -oldMultiplicity + newMultiplicity;
    this.checkSize();
  }

  /**
   * Sets the multiplicity of a certain key, only if currently set to the specified value.
   * 
   * @param element
   *          the element
   * @param oldMultiplicity
   *          old multiplicity
   * @param newMultiplicity
   *          new multiplicity
   * 
   * @return {@code true} if the value was replaced
   * 
   * @see Map#replace(Object, Object, Object)
   */
  public boolean setMultiplicity(final @Nullable T element, final int oldMultiplicity,
      final int newMultiplicity) {
    if (oldMultiplicity < 0 || newMultiplicity < 0)
      throw new IllegalArgumentException("negative multiplicity");
    if (this.getMultiplicity(element) == oldMultiplicity) {
      this.setMultiplicity(element, newMultiplicity);
      return true;
    }
    return false;
  }

  /**
   * Adds the element and returns the new multiplicity.
   * 
   * @param element
   *          The element to be added.
   * @see #add(Object)
   * @see #setMultiplicity(Object, int)
   * @return the new multiplicity.
   */
  public int insert(final @Nullable T element) {
    Integer m = this.map.get(element);
    if (null == m)
      m = 1;
    else
      m++;
    this.map.put(element, m);
    this.size++;
    this.checkSize();
    return m;
  }

  /**
   * Removes a single instance of the specified element from this multiset. This decrements the
   * multiplicity by one. Does nothing if it wasn't in the set. To remove all equal objects you must
   * call <code>{@link #setMultiplicity(Object, int) setMultiplicity(t, 0)}</code>.
   * 
   * <p>
   * If the backing {@link Map} throws a {@link ClassCastException} because the key is of
   * inappropriate type or {@link NullPointerException} because it doesn't allow null, this will
   * return 0 and ignore the exception.
   */
  @Override
  @SuppressWarnings("unchecked")
  public boolean remove(final @Nullable Object t) {
    final Integer value;
    try {
      value = this.map.get(t);
    } catch (final ClassCastException e) {
      return false;
    } catch (final NullPointerException e) {
      if (t == null)
        return false;
      throw e;
    }
    if (value == null)
      return false;
    else if (value.intValue() == 1)
      this.map.remove(t);
    else
      this.map.put((T) t, value - 1);
    this.size--;
    this.checkSize();
    return true;
  }

  /**
   * Removes and returns one single element. This is only a conveniance method. The
   * {@link #iterator() iterator} allows you to remove elements, which is the preferred way to poll
   * more than one element.
   * 
   * @implNote This uses an iterator to get and remove one single element.
   * 
   * @return any element, which could be {@code null}.
   * @throws NoSuchElementException
   *           if this multiset is empty.
   * @see #poll(Consumer)
   */
  @CheckForNull
  public T poll() {
    if (this.isEmpty())
      throw new NoSuchElementException();
    final Entry<T, Integer> next = this.map.entrySet().iterator().next();
    final T key = next.getKey();
    final int value = next.getValue();
    this._set(key, value, value - 1);
    return key;
  }

  /**
   * Removes and consumes one single element. Does nothing if this multiset is empty. The consumer
   * will receive any element from the multiset. That element might be {@code null}.
   * 
   * @return {@code true}, if an element was processed.
   * @see #poll()
   */
  public boolean poll(final Consumer<T> consumer) {
    requireNonNull(consumer, "consumer");
    if (this.isEmpty())
      return false;
    consumer.accept(this.poll());
    return true;
  }

  /**
   * Reset the multiplicity for each element in this {@link Multiset}.
   * 
   * <p>
   * Note that elements with a multiplicity of zero will not be processed, as they are not contained
   * in the multiset.
   * 
   * @param m
   *          function for new multiplicities
   * @return true, if any multiplicity of any element was altered.
   */
  public boolean setMultiplicities(final ToIntBiFunction<? super T, Integer> m) {
    requireNonNull(m, "m");
    if (this.isEmpty())
      return false;
    boolean modified = false;
    final Iterator<Entry<T, Integer>> itr = this.map.entrySet().iterator();
    while (itr.hasNext()) {
      final Entry<T, Integer> next = itr.next();
      final T t = next.getKey();
      final int oldm = next.getValue();
      final int newM = m.applyAsInt(t, oldm);
      if (oldm != newM) {
        if (newM == 0)
          itr.remove();
        else
          next.setValue(newM);
        this.size += -oldm + newM;
        modified = true;
      }
    }
    this.checkSize();
    return modified;
  }

  @SuppressWarnings({ "unchecked" })
  @Override
  public boolean removeAll(final Collection<?> c) {
    requireNonNull(c, "c");
    if (c instanceof Multiset)
      return this.removeAll((Multiset<? extends T>) c);
    return super.removeAll(c);
  }

  /**
   * Removes all of this collection's elements that are also contained in the specified multiset.
   * 
   * @see #removeAll(Collection)
   */
  public boolean removeAll(final Multiset<?> set) {
    requireNonNull(set, "set");
    boolean result = false;
    if (set.isEmpty())
      return false;
    final Iterator<Entry<T, Integer>> itr = this.map.entrySet().iterator();
    while (itr.hasNext()) {
      final Entry<T, Integer> next = itr.next();
      if (set.contains(next.getKey())) {
        itr.remove();
        this.size -= next.getValue();
        result = true;
      }
    }
    this.checkSize();
    return result;
  }

  @Override
  public boolean retainAll(final Collection<?> c) {
    requireNonNull(c, "c");
    if (c instanceof Multiset)
      return this.retainAll((Multiset<?>) c);
    return this.asSet().retainAll(c);
  }

  /**
   * Retains only the elements in this multiset that are contained in the specified multiset
   * (optional operation).
   * 
   * @see #retainAll(Collection)
   */
  public boolean retainAll(final Multiset<?> set) {
    requireNonNull(set, "set");
    if (set == this)
      return false;
    return this.asSet().retainAll(set.asSet());
  }

  /**
   * Get the multiplicity or the given object. If the object is not in this multiset then the
   * multiplicity is 0.
   * 
   * <p>
   * If the backing {@link Map} throws a {@link ClassCastException} because the key is of
   * inappropriate type or {@link NullPointerException} because it doesn't allow null, this will
   * return 0 and ignore the exception.
   * 
   * @return the multiplicity.
   */
  public int getMultiplicity(final @Nullable Object t) {
    try {
      final Integer i = this.map.get(t);
      return i == null ? 0 : i;
    } catch (final ClassCastException e) {
      return 0;
    } catch (final NullPointerException e) {
      if (t == null)
        return 0;
      throw e;
    }
  }

  Iterator<T> iterator(final Iterator<Entry<T, Integer>> entries) {
    return new Iterator<T>() {
      int c = 0;
      @SuppressWarnings("unchecked")
      T   t = (T) Multiset.this;

      @Override
      public T next() {
        if (!this.hasNext())
          throw new NoSuchElementException();
        if (this.c == 0) {
          final Entry<T, Integer> e = entries.next();
          this.t = e.getKey();
          this.c = e.getValue();
        }
        this.c--;
        return this.t;
      }

      @Override
      public boolean hasNext() {
        return this.c > 0 || entries.hasNext();
      }

      @Override
      public void remove() {
        if (this.t == Multiset.this)
          throw new IllegalStateException();
        final int multiplicity = Multiset.this.getMultiplicity(this.t);
        if (multiplicity == 1) {
          entries.remove();
          Multiset.this.size--;
        } else
          Multiset.this._set(this.t, multiplicity, multiplicity - 1);
      }
    };
  }

  /**
   * Iterator of all elements. Each element is iterated as many times as it is in this multiset. The
   * {@link Iterator} supports the {@link Iterator#remove() remove} method.
   */
  @Override
  @NonNull
  public Iterator<T> iterator() {
    this.checkSize();
    return this.iterator(this.map.entrySet().iterator());
  }

  @Override
  @NonNull
  public Spliterator<T> spliterator() {
    return Spliterators.spliterator(this, 0);
  }

  /** The class invariant. */
  void checkSize() {
    assert this.map.values().parallelStream().mapToInt(i -> i).sum() == this.size : this.getClass()
        .getSimpleName() + ": wrong size";
  }

  /**
   * {@inheritDoc}
   * <p>
   * This returns the total number of elements, including repeated memberships (cardinality). To get
   * the amount of discinct elements use {@link #asMap()}.{@link Map#size() size()}.
   */
  @Override
  public int size() {
    this.checkSize();
    return this.size;
  }

  /**
   * Returns a copy of the underlying set of elements. Changes to this set do not alter the
   * Multiset.
   * 
   * <p>
   * If using the formal definition <code>(A, m)</code> of a multiset, the returned set is
   * <code>A</code> and <code>multiset::getMultiplicity</code> is <code>m</code>.
   * 
   * @see #asSet()
   * @see #entries()
   */
  @NonNull
  @CheckReturnValue
  public Set<T> toSet() {
    return new HashSet<>(this.map.keySet());
  }

  /**
   * Conventiance method to create a string representation of this multiset.
   * <p>
   * Example: {@code ms.toString((t, i) -> t + " x " + i, ", ", "[ ", " ]")}
   * 
   * @param f
   *          Mapping function for each entry (element and multiplicity)
   * @param delimiter
   *          the delimiter to be used between each entry
   * @param prefix
   *          the sequence of characters to be used at the beginning of the joined result
   * @param suffix
   *          the sequence of characters to be used at the end of the joined result
   * @return string representation of this multiset
   */
  @NonNull
  public String toString(final BiFunction<? super T, ? super Integer, String> f,
      final CharSequence delimiter, final CharSequence prefix, final CharSequence suffix) {
    requireNonNull(f, "f");
    requireNonNull(delimiter, "delimiter");
    requireNonNull(prefix, "prefix");
    requireNonNull(suffix, "suffix");

    return this.map.entrySet().stream().map(e -> f.apply(e.getKey(), e.getValue()))
        .collect(Collectors.joining(delimiter, prefix, suffix));
  }

  /**
   * As a new, sorted List.
   * 
   * <p>
   * To sort by multiplicity, descending:<br>
   * <code>multiset.toList((a, b) -> b.getValue() - a.getValue())</code>
   */
  @NonNull
  @CheckReturnValue
  public List<T> toList(@Nullable final Comparator<Map.Entry<T, Integer>> comparator) {
    if (null == comparator)
      return this.stream().collect(Collectors.toList());
    return this.entries().sorted(comparator)
        .flatMap(e -> IntStream.range(0, e.getValue()).<T> mapToObj(x -> e.getKey()))
        .collect(Collectors.toList());
  }

  /**
   * Streams each element and its multiplicity. The returned entries are immutable. Mutable entries
   * can be accessed using: {@code this.asMap().entrySet()}
   * 
   * @see #forEach(ObjIntConsumer)
   */
  @NonNull
  @CheckReturnValue
  public Stream<Map.Entry<T, Integer>> entries() {
    final Stream<Entry<T, Integer>> stream = this.map.entrySet().stream();
    if (this.isUnmodifiable())
      return stream;
    return stream.map(SimpleImmutableEntry::new);
  }

  @Override
  public boolean addAll(final Collection<? extends T> c) {
    requireNonNull(c, "c");
    if (c instanceof Multiset)
      return this.addAll((Multiset<? extends T>) c);
    return super.addAll(c);
  }

  /**
   * Adds all of the elements in the specified multiset to this multiset. The behavior of this
   * operation is undefined if the specified collection is modified while the operation is in
   * progress. (This implies that the behavior of this call is undefined if the specified collection
   * is this collection, and this collection is nonempty.)
   * 
   * @see #addAll(Collection)
   */
  public boolean addAll(final Multiset<? extends T> ms) {
    requireNonNull(ms, "ms");
    for (final Entry<? extends T, Integer> e : ms.map.entrySet())
      this.add(e.getKey(), e.getValue());
    this.checkSize();
    return ms.size() != 0;
  }

  /**
   * Two {@link Multiset}s are equal if both contain the same elements with the same multiplicities.
   */
  @Override
  public boolean equals(final Object obj) {
    if (obj instanceof Multiset) {
      final Multiset<?> other = (Multiset<?>) obj;
      return this.size == other.size && this.map.equals(other.map);
    }
    return false;
  }

  /**
   * Returns a hash code for this Multiset. The hash code of a multiset is defined to be the sum of
   * the hash codes of each entry and that sum XORed with the {@link #size() size}.
   */
  @Override
  public int hashCode() {
    return this.size ^ this.map.hashCode();
  }

  /**
   * Removes all of the elements from this multiset (optional operation). The multiset will be empty
   * after this method returns.
   */
  @Override
  public void clear() {
    if (this.size == 0)
      return;
    this.map.clear();
    this.size = 0;
    assert this.isEmpty() : "not empty after clear";
    this.checkSize();
  }

  /**
   * Creates a clone. The cloned multiset is modifiable and not synchronized. This is equal to
   * {@link #Multiset(Multiset)}.
   */
  @Override
  @CheckReturnValue
  @NonNull
  protected Multiset<T> clone() {
    return new Multiset<>(this);
  }

  /**
   * {@inheritDoc}
   * 
   * @see #isSubmultisetOf(Multiset)
   */
  @Override
  public boolean containsAll(final Collection<?> c) {
    requireNonNull(c, "c");
    if (c instanceof Multiset)
      this.map.keySet().containsAll(((Multiset<?>) c).map.keySet());
    return this.map.keySet().containsAll(c);
  }

  /**
   * Returns <tt>true</tt> if this is a submultiset of the given Multiset.
   * 
   * A submultiset has no element with a higher multiplicity than the supermultiset. Every Multiset
   * is a submultiset of itself.
   * 
   * @return <tt>true</tt>, if this is a submultiset of the given Multiset.
   * @see #containsAll(Collection)
   */
  public boolean isSubmultisetOf(final Multiset<?> ms) {
    requireNonNull(ms, "ms");
    if (this.size > ms.size)
      return false;
    if (this == ms || this.isEmpty())
      return true;
    final Optional<?> any = this.map.entrySet().stream()
        .filter(e -> e.getValue() > ms.getMultiplicity(e.getKey())).findAny();
    return !any.isPresent();
  }

  @Override
  public boolean isEmpty() {
    return this.size == 0;
  }

  /**
   * Process each element only once, with the multiplicity given as a second parameter. The element
   * can be <code>null</code>, while the multiplicity is always greater than 0.
   * 
   * @see #entries()
   */
  public void forEach(final ObjIntConsumer<? super T> action) {
    requireNonNull(action, "action");
    this.map.forEach(action::accept);
  }

  /**
   * {@inheritDoc}
   * 
   * <p>
   * If the backing {@link Map} throws a {@link ClassCastException} because the key is of
   * inappropriate type or {@link NullPointerException} because it doesn't allow null, this will
   * return false and ignore the exception.
   */
  @Override
  public boolean contains(@Nullable final Object obj) {
    try {
      return this.map.containsKey(obj);
    } catch (final ClassCastException e) {
      return false;
    } catch (final NullPointerException e) {
      if (obj == null)
        return false;
      throw e;
    }
  }

  /**
   * The intersection of two multisets. This is equal to
   * <code>this.{@link #merge(Multiset, IntBinaryOperator) merge}(set, {@link Math#min(int, int) Math::min})</code>
   * . However, the type of the elements in the provided multiset does not matter.
   */
  @SuppressWarnings("unchecked")
  @CheckReturnValue
  @NonNull
  public Multiset<T> intersect(final Multiset<?> set) {
    requireNonNull(set, "set");
    return this.merge((Multiset<T>) set, Math::min);
  }

  /**
   * The disjoint union of two multisets. This is equal to
   * <code>this.{@link #merge(Multiset, IntBinaryOperator) merge}(set, {@link Integer#sum(int, int) Integer::sum})</code>
   * 
   * <p>
   * Note: A different definition of <i>union</i> would use {@link Math#max(int, int)} with
   * {@link #merge(Multiset, IntBinaryOperator)}.
   * 
   * @see #addAll(Collection)
   * @see #union(Multiset, Class)
   * @see #merge(Multiset, IntBinaryOperator)
   */
  @CheckReturnValue
  @NonNull
  public Multiset<T> union(final Multiset<? extends T> set) {
    requireNonNull(set, "set");
    return this.merge(set, Integer::sum);
  }

  /**
   * The union of two multisets. This is equal to
   * <code>this.{@link #merge(Multiset, IntBinaryOperator) merge}(set, {@link Integer#sum(int, int) Integer::sum}, superType)</code>
   * 
   * @see #union(Multiset)
   * @see #merge(Multiset, IntBinaryOperator, Class)
   */
  @CheckReturnValue
  @NonNull
  public <S> Multiset<S> union(final Multiset<? extends S> set, final Class<S> supertype) {
    requireNonNull(set, "set");
    requireNonNull(supertype, "supertype");
    return this.merge(set, Integer::sum, supertype);
  }

  /**
   * The relative complement of this and a given multiset.
   * 
   * @see #removeAll(Collection)
   */
  @CheckReturnValue
  @NonNull
  public Multiset<T> minus(final Multiset<? extends T> set) {
    requireNonNull(set, "set");
    final Multiset<T> result = new Multiset<>(this);
    if (set.isEmpty())
      return result;
    result.removeAll(set);
    return result;
  }

  /**
   * Create new multiset from this and another multiset. The given operation will calculate the new
   * multiplicity for all elements.
   * 
   * <p>
   * This pseudo-code illustrates how the operation is used to create a new multiset. In this case
   * the result is equal to the result of {@link #union(Multiset)}. <code><pre>
   * mset1 = [ a, a, a, b ];
   * mset2 = [ b, c ];
   * union = m1.merge(m2, Integer::sum);
   * // m3 = [ a, a, a, b, b, c ]
   * </pre></code>
   */
  @CheckReturnValue
  @NonNull
  public Multiset<T> merge(final Multiset<? extends T> set, final IntBinaryOperator operation) {
    requireNonNull(set, "set");
    requireNonNull(operation, "operation");
    final Multiset<T> result = new Multiset<>(this);
    if (set.isEmpty() && this.isEmpty())
      return result;
    Stream.concat(this.map.keySet().stream(), set.map.keySet().stream()).distinct().forEach(t -> {
      result.setMultiplicity(t, //
          operation.applyAsInt(this.getMultiplicity(t), set.getMultiplicity(t)));
    });
    result.checkSize();
    return result;
  }

  /**
   * Create new multiset from this and another multiset. The given operation will calculate the new
   * multiplicity for all elements.
   * 
   * @implNote Only with assertions enabled there will be a check that all elements are assignable
   *           to the given supertype. Use #checkedMultiset(Multiset, Class) for runtime type
   *           checks.
   * @param set
   *          The other multiset
   * @param operation
   *          the operation for the multiplicities
   * @param supertype
   *          a supertype of both multisets
   */
  @SuppressWarnings("unchecked")
  @CheckReturnValue
  @NonNull
  public <S> Multiset<S> merge(final Multiset<? extends S> set, final IntBinaryOperator operation,
      final Class<S> supertype) {
    requireNonNull(set, "set");
    requireNonNull(operation, "operation");
    requireNonNull(supertype, "supertype");
    assert this.asSet().stream().allMatch(e -> supertype.isAssignableFrom(e.getClass()));
    assert set.asSet().stream().allMatch(e -> supertype.isAssignableFrom(e.getClass()));
    return ((Multiset<S>) this).merge(set, operation);
  }

  private void readObject(final java.io.ObjectInputStream stream) throws Exception {
    requireNonNull(stream, "stream").defaultReadObject();
    this.checkSize();
  }

  /**
   * Returnes an unmodifiable view of the specified {@link Multiset}.
   * 
   * @param multiset
   *          the multiset for which an unmodifiable view is to be returned.
   * @return an unmodifiable view of the specified map.
   */
  @CheckReturnValue
  @NonNull
  public static <T> Multiset<T> unmodifiableMultiset(final Multiset<T> multiset) {
    requireNonNull(multiset, "multiset");
    if (multiset.isUnmodifiable())
      return multiset;
    if (multiset.isEmpty())
      return emptyMultiset();
    return new Multiset<>(multiset, true);
  }

  /**
   * Checks whether this is backed by an unmodifiable map. An unmodifiable {@link Multiset} always
   * has the same reference set for {@link #map} and {@link #view}.
   * 
   * @see #unmodifiableMultiset(Multiset)
   */
  public boolean isUnmodifiable() {
    return this.map == this.view;
  }

  /**
   * A map-view of this {@link Multiset}. Changes are reflected on the {@link Multiset}. However,
   * when using streams or iterators it is not allowed to set the multiplicity to 0. Instead the
   * entry needs to be {@link Iterator#remove() removed}.
   * <p>
   * Methods such as {@link Map#put}, {@link Map#clear}, and {@link Map#contains} are delegated to
   * the backing map of the {@link Multiset}.
   * 
   * @see #asSet()
   * @see #entries()
   */
  @CheckReturnValue
  @NonNull
  public Map<T, Integer> asMap() {
    final Map<T, Integer> v = this.view;
    if (v != null)
      return v;
    return this.view = new AbstractMap<T, Integer>() {
      private Set<java.util.Map.Entry<T, Integer>> entrySet;

      @Override
      public Integer put(final T key, final Integer value) {
        return Multiset.this.setMultiplicity(key, value);
      }

      @Override
      public Set<java.util.Map.Entry<T, Integer>> entrySet() {
        if (this.entrySet != null)
          return this.entrySet;
        return this.entrySet = new AbstractSet<Map.Entry<T, Integer>>() {

          @Override
          public Iterator<java.util.Map.Entry<T, Integer>> iterator() {
            final Iterator<java.util.Map.Entry<T, Integer>> itr = Multiset.this.map.entrySet()
                .iterator();
            return new Iterator<Map.Entry<T, Integer>>() {
              private Entry<T, Integer> last = null;

              @Override
              public boolean hasNext() {
                return itr.hasNext();
              }

              @Override
              public java.util.Map.Entry<T, Integer> next() {
                return this.last = new Entry<T, Integer>() {
                  final Entry<T, Integer> next  = itr.next();
                  final T                 key   = this.next.getKey();
                  int                     value = this.next.getValue();

                  @Override
                  public T getKey() {
                    return this.key;
                  }

                  @Override
                  public Integer getValue() {
                    return this.value;
                  }

                  @Override
                  public Integer setValue(final Integer value) {
                    requireNonNull(value, "value");
                    final int i = value.intValue();
                    if (i < 0)
                      throw new IllegalArgumentException("value=" + i);
                    if (i == 0)
                      throw new IllegalArgumentException(
                          "Can't set multiplicity to 0. Use remove() instead!");
                    final int old = this.value;
                    Multiset.this.setMultiplicity(this.key, i);
                    this.value = i;
                    return old;
                  }

                  @Override
                  public final int hashCode() {
                    return Objects.hashCode(this.key) ^ this.value;
                  }

                  @Override
                  public final boolean equals(final Object o) {
                    if (o == this)
                      return true;
                    if (o instanceof Map.Entry) {
                      final Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
                      if (Objects.equals(this.key, e.getKey())
                          && Objects.equals(this.value, e.getValue()))
                        return true;
                    }
                    return false;
                  }

                  @Override
                  public String toString() {
                    return this.key + "=" + this.value;
                  }
                };
              }

              @Override
              public void remove() {
                if (this.last == null)
                  throw new NoSuchElementException();
                itr.remove();
                Multiset.this.size -= this.last.getValue();
                Multiset.this.checkSize();
              }
            };

          }

          @Override
          public int size() {
            return Multiset.this.map.size();
          }

          @Override
          public boolean isEmpty() {
            return Multiset.this.map.isEmpty();
          }

          @Override
          public boolean contains(final Object o) {
            return Multiset.this.map.entrySet().contains(o);
          }

          @Override
          public boolean add(final java.util.Map.Entry<T, Integer> e) {
            // NOT SUPPORTED!!!
            return Multiset.this.map.entrySet().add(e);
          }

          @Override
          public boolean remove(final Object o) {
            if (o instanceof Map.Entry) {
              @SuppressWarnings("unchecked")
              final Entry<T, Integer> entry = (Entry<T, Integer>) o;
              final T key = entry.getKey();
              final Integer value = entry.getValue();
              if (value <= 0 || Multiset.this.getMultiplicity(key) != value)
                return false;
              Multiset.this._set(key, value, 0);
              return true;
            }
            return false;
          }

          @Override
          public boolean containsAll(final Collection<?> c) {
            return Multiset.this.map.entrySet().containsAll(c);
          }

          @Override
          public void clear() {
            Multiset.this.clear();
          }

        };
      }
    };

  }

  /**
   * Returns a set of all elements, without multiplicity. The set is backed by the multiset, so
   * changes to the multiset are reflected in the set, and vice-versa. Removing one element from the
   * set removes all equal elements from the multiset. It does not support the <code>add</code> and
   * <code>addAll</code> methods.
   * 
   * <p>
   * If using the formal definition <code>(A, m)</code> of a multiset, the returned set is
   * <code>A</code> and <code>multiset::getMultiplicity</code> is <code>m</code>.
   * 
   * @see #toSet()
   * @see #asMap()
   * @see #entries()
   * @return set-view of all elements
   */
  @CheckReturnValue
  @NonNull
  public Set<T> asSet() {
    return this.asMap().keySet();
  }

  /**
   * Creates a synchronized (thread-safe) collection and a synchronized map, both backed by the
   * specified multiset. The collection behaves just like the specified multiset, while the map
   * corresponds to {@link #asMap()}. Both data structures are modifiable and use the created
   * collection as the mutex for synchronization.
   * 
   * @see Collections#synchronizedCollection(Collection)
   * 
   * @param set
   *          the multiset
   * @param consumer
   *          accepts the collection and the map.
   * @throws RuntimeException
   *           if the security manager does not allow to create a SynchronizedMap by reflection.
   */
  @SuppressWarnings("unchecked")
  public static <T> void synchronizedMultiset(final Multiset<T> set,
      final BiConsumer<Collection<T>, Map<T, Integer>> consumer) throws RuntimeException {
    requireNonNull(set, "set");
    requireNonNull(consumer, "consumer");
    try {
      final Collection<T> collection = Collections.synchronizedCollection(set);
      final Class<? extends Map<T, Integer>> cls = (Class<? extends Map<T, Integer>>) Class
          .forName("java.util.Collections$SynchronizedMap");
      final Constructor<? extends Map<T, Integer>> ctor;
      ctor = cls.getDeclaredConstructor(Map.class, Object.class);
      ctor.setAccessible(true);
      final Map<T, Integer> map = ctor.newInstance(set.asMap(), collection);
      consumer.accept(collection, map);
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException | SecurityException
        | ClassNotFoundException ex) {
      throw new RuntimeException("Can't create synchronized Multiset.", ex);
    }
  }

  static Multiset<?> emptyMultiset;

  /** Returns an empty multiset (unmodifiable). */
  @SuppressWarnings("unchecked")
  @CheckReturnValue
  @NonNull
  public static <T> Multiset<T> emptyMultiset() {
    if (null == emptyMultiset)
      return (Multiset<T>) (emptyMultiset = new Multiset<T>(true));
    return (Multiset<T>) emptyMultiset;
  }

  /**
   * Returns a dynamically typechecked view of the given {@link Multiset}. Existing entries are not
   * checked.
   * 
   * @see Collections#checkedCollection(Collection, Class)
   */
  @CheckReturnValue
  @NonNull
  public static <T> Multiset<T> checkedMultiset(final Multiset<T> multiset, final Class<T> type) {
    requireNonNull(multiset, "multiset");
    requireNonNull(type, "type");
    return new Multiset<>(Collections.checkedMap(multiset.asMap(), type, Integer.class),
        multiset.size);
  }

  /**
   * Returns a dynamically typechecked view of the given {@link Map} as a {@link Multiset}. Existing
   * entries are not checked.
   * 
   * @see Collections#checkedCollection(Collection, Class)
   */
  @CheckReturnValue
  @NonNull
  public static <T> Multiset<T> checkedMultiset(final Map<T, Integer> map, final Class<T> type) {
    requireNonNull(map, "map");
    requireNonNull(type, "type");
    return Multiset.wrap(Collections.checkedMap(map, type, Integer.class));
  }

  /**
   * Returns a {@code Collector} that accumulates the input elements into a new {@code Multiset}.
   * 
   * @return a {@code Collector} which collects all the input elements into a {@code Multiset}
   */
  @CheckReturnValue
  @NonNull
  public static <T> Collector<T, ?, Multiset<T>> collector() {
    return collector(Multiset::new);
  }

  /**
   * Returns a {@code Collector} that accumulates the input elements into a new {@code Multiset}.
   * This can be used to define a custom implementation of the backing {@link Map} used by the
   * multiset (e.g. a {@link TreeMap}).
   * 
   * @param supplier
   *          Used to create the multisets used for accomulation. The supplied multisets must be
   *          empty.
   * @return a {@code Collector} which collects all the input elements into a {@code Multiset}
   */
  @CheckReturnValue
  @NonNull
  public static <T> Collector<T, ?, Multiset<T>> collector(final Supplier<Multiset<T>> supplier) {
    requireNonNull(supplier, "supplier");
    return new Collector<T, Multiset<T>, Multiset<T>>() {

      @Override
      public Supplier<Multiset<T>> supplier() {
        return supplier;
      }

      @Override
      public BiConsumer<Multiset<T>, T> accumulator() {
        // Equivalent to Multiset::insert, but simplified:
        return (ms, t) -> {
          final Integer m = ms.map.get(t);
          if (m == null)
            ms.map.put(t, 1);
          else
            ms.map.put(t, m + 1);
          ms.size++;
        };
      }

      @Override
      public BinaryOperator<Multiset<T>> combiner() {
        return (m1, m2) -> {
          final Multiset<T> smaller, larger;
          if (m1.map.entrySet().size() < m2.map.entrySet().size()) {
            smaller = m1;
            larger = m2;
          } else {
            smaller = m2;
            larger = m1;
          }
          larger.addAll(smaller);
          return larger;
        };
      }

      @Override
      public Function<Multiset<T>, Multiset<T>> finisher() {
        return Function.identity();
      }

      @Override
      public Set<java.util.stream.Collector.Characteristics> characteristics() {
        return EnumSet.of(Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
      }
    };
  }
}
