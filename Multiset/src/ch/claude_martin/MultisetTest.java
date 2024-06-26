package ch.claude_martin;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.lang.constant.Constable;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MultisetTest {

  final Multiset<?>         empty   = Multiset.emptyMultiset();
  final Multiset<Character> abc     = new Multiset<>();
  final Multiset<Integer>   numbers = Multiset.ofSupplier(TreeMap::new);

  final List<Multiset<?>>   list    = asList(this.empty, this.abc, this.numbers);

  @BeforeEach
  public void setUp() throws Exception {
    this.abc.clear();
    for (char c = 'a'; c <= 'z'; c++)
      this.abc.insert(c);
    this.numbers.clear();
    for (int i = 0; i < 10; i++)
      this.numbers.setMultiplicity(i, i);
  }

  @Test
  public final void testHashCode() {
    assertEquals(this.empty.hashCode(), Multiset.of().hashCode());
    assertEquals(this.abc.hashCode(), this.abc.hashCode());
    assertEquals(this.abc.hashCode(), this.abc.clone().hashCode());
    assertNotEquals(this.abc.hashCode(), this.numbers.hashCode());
  }

  @Test
  public final void testSize() {
    assertEquals(0, this.empty.size());
    assertEquals(26, this.abc.size());
    assertEquals(45, this.numbers.size());
  }

  @Test
  public final void testIsEmpty() {
    assertTrue(this.empty.isEmpty());
    assertFalse(this.abc.isEmpty());
    assertFalse(this.numbers.isEmpty());
  }

  @Test
  public final void testClear() {
    this.abc.clear();
    assertEquals(this.empty, this.abc);
  }

  public final void testMultisetCollection() {
    assertEquals(this.empty, new Multiset<>(this.empty.toList((a, b) -> 0)));
    assertEquals(this.abc, new Multiset<>(this.abc.toSet()));
  }

  @Test
  public final void testMultisetMultiset() {
    for (final Multiset<?> ms : this.list)
      assertEquals(ms, new Multiset<>(ms));
  }

  @Test
  public final void testOf() {
    assertEquals(this.empty, Multiset.of());
    for (final Multiset<?> ms : this.list)
      assertEquals(ms, Multiset.<Object> of(ms.toArray()));
  }

  @Test
  public final void testOfSetToIntFunction() {
    assertEquals(this.empty, Multiset.of(Collections.emptySet(), x -> 0));
    for (final Multiset<?> ms : this.list)
      assertEquals(ms, Multiset.<Object> of(ms.asSet(), ms::getMultiplicity));
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Test
  public final void testAdd() {

    assertTrue(this.abc.add('ä'));
    assertTrue(this.abc.add('ö'));
    assertTrue(this.abc.add('ü'));
    assertTrue(this.abc.contains('ä'));
    assertTrue(this.abc.getMultiplicity('ö') == 1);
    assertTrue(this.abc.setMultiplicity('ü', 1) == 1);

    try {
      ((Multiset) this.empty).add("foo");
      fail("can't add element to empty-set!");
    } catch (final Exception e) {
      // expected!
    }

    {
      final Multiset<Integer> m = new Multiset<>();
      assertTrue(m.isEmpty());

      m.add(0, 0);
      assertTrue(m.isEmpty());

      m.add(42, 40).add(42, 2);
      assertEquals(42, m.getMultiplicity(42));
      m.add(42, -41);
      assertEquals(1, m.getMultiplicity(42));

      m.add(8, 1).add(8, -1);
      assertEquals(0, m.getMultiplicity(8));

      try {
        m.add(123, -4);
        fail("can't remove nonexistant elements!");
      } catch (final Exception e) {
        // expected!
      }
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Test
  public final void testSetMultiplicity() {
    assertEquals(1, this.abc.setMultiplicity('x', 20));
    assertEquals(20, this.abc.setMultiplicity('x', 1));

    try {
      ((Multiset) this.empty).setMultiplicity('X', 123);
      fail("can't alter empty-set!");
    } catch (final Exception e) {
      // expected!
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Test
  public final void testInsert() {
    assertEquals(1, this.abc.insert('ä'));
    assertEquals(1, this.abc.insert('ö'));
    assertEquals(1, this.abc.insert('ü'));

    assertEquals(2, this.abc.insert('x'));
    assertEquals(2, this.abc.getMultiplicity('x'));

    try {
      ((Multiset) this.empty).insert("foo");
      fail("can't alter empty-set!");
    } catch (final Exception e) {
      // expected!
    }

  }

  @Test
  public final void testRemoveObject() {
    assertTrue(this.abc.remove('x'));
    assertFalse(this.abc.contains('x'));
    assertFalse(this.abc.remove('?'));

    assertEquals(8, this.numbers.getMultiplicity(8));
    assertTrue(this.numbers.remove(8));
    assertEquals(7, this.numbers.getMultiplicity(8));

    assertFalse(this.empty.remove(this.abc));

    assertFalse(this.empty.remove(null));
    assertFalse(this.empty.remove(new Object()));

    assertFalse(this.numbers.remove(null));
    assertFalse(this.numbers.remove(new Object()));

  }

  @Test
  public void testPoll() throws Exception {
    try {
      new Multiset<>().poll();
      fail("NoSuchElementException expected!");
    } catch (final NoSuchElementException e) {
      // expected!
    }
    while (!this.abc.isEmpty())
      this.abc.poll();
    assertEquals(this.empty, this.abc);
    assertFalse(this.abc.poll(e -> fail("can't poll from empty multiset")));

    final Multiset<Integer> num1 = this.numbers.clone();
    final Multiset<Integer> num2 = new Multiset<>();
    while (this.numbers.poll(num2::insert))
      ;
    assertEquals(this.empty, this.numbers);
    assertEquals(num1, num2);

    num2.poll(num2::add);
    assertEquals(num1, num2);
  }

  @Test
  public final void testSetMultiplicities() {
    this.abc.setMultiplicities((c, m) -> c);
    for (char c = 'a'; c <= 'z'; c++)
      assertEquals(0 + c, this.abc.getMultiplicity(c));

    this.numbers.setMultiplicities((n, m) -> 1);
    assertEquals(9, this.numbers.size());

    this.empty.setMultiplicities((a, b) -> 123);
    assertTrue(this.empty.isEmpty());
  }

  @Test
  @SuppressWarnings("unlikely-arg-type")
  public final void testRemoveAllCollection() {
    assertFalse(this.abc.removeAll(this.numbers.asSet()));

    assertTrue(this.abc.removeAll(this.abc.toList((a, b) -> a.getKey() - b.getKey())));
    assertTrue(this.abc.isEmpty());

    assertTrue(this.numbers.removeAll(asList(2, 3, 5, 7)));
    assertEquals(1 + 4 + 6 + 8 + 9, this.numbers.size);
  }

  @Test
  public final void testRemoveAllMultiset() {
    assertFalse(this.abc.removeAll(this.numbers));

    assertTrue(this.abc.removeAll(this.abc));
    assertTrue(this.abc.isEmpty());

    assertTrue(this.numbers.removeAll(Multiset.of(1, 2, 3, 4, 5, 6, 7, 8, 9)));
    assertEquals(this.empty, this.numbers);
  }

  @Test
  public final void testRetainAllCollection() {
    assertFalse(this.abc.retainAll(this.abc.toSet()));
    assertEquals(26, this.abc.size());

    assertTrue(this.numbers.retainAll(asList(2, 8)));
    assertEquals(2 + 8, this.numbers.size());

  }

  @Test
  public final void testRetainAllMultiset() {
    assertFalse(this.abc.retainAll(this.abc));
    assertEquals(26, this.abc.size());

    assertTrue(this.numbers.retainAll(Multiset.of(2, 8)));
    assertEquals(2 + 8, this.numbers.size());
  }

  @Test
  public final void testGetMultiplicity() {
    for (final char c : this.abc.asSet())
      assertEquals(1, this.abc.getMultiplicity(c));
    assertEquals(0, this.abc.getMultiplicity('ö'));

    for (final int n : this.numbers.asSet())
      assertEquals(n, this.numbers.getMultiplicity(n));

    assertEquals(0, this.numbers.getMultiplicity(null));
    assertEquals(0, this.numbers.getMultiplicity(new Object()));
  }

  @Test
  public final void testIterator() {
    assertFalse(this.empty.iterator().hasNext());
    assertTrue(this.abc.iterator().hasNext());

    for (final Character character : this.abc)
      assertTrue(this.abc.contains(character));

    try {
      this.empty.iterator().next();
      fail("empty set has no 'next' element");
    } catch (final NoSuchElementException e) {
      // expected!
    }

    try {
      this.numbers.iterator().remove();
      fail("can't remove element if next() was never invoked");
    } catch (final IllegalStateException e) {
      // expected!
    }

  }

  @Test
  public final void testToSet() {
    final Set<Character> set = this.abc.toSet();
    set.add('ö');
    assertTrue(set.contains('ö'));
    assertFalse(this.abc.contains('ö'));
  }

  @Test
  public final void testToString() {
    assertEquals("[]", this.empty.toString());

    assertTrue(this.abc.toString().startsWith("["));
    assertTrue(this.numbers.toString().endsWith("]"));

    this.abc.toString((c, m) -> c + " x " + m, ", ", "[ ", " ]");
    this.numbers.toString((n, m) -> n + " x " + m, ", ", "[ ", " ]");
  }

  @Test
  public final void testToList() {
    assertEquals(new ArrayList<>(this.abc), this.abc.toList((a, b) -> a.getKey() - b.getKey()));
    assertEquals(new ArrayList<>(this.abc), this.abc.toList(null));
  }

  @Test
  public final void testEntries() {
    final List<Integer> l = this.numbers.entries()
        .<Integer> flatMap(e -> IntStream.range(0, e.getValue()).mapToObj(i -> e.getKey()))
        .collect(Collectors.toList());

    final Multiset<Integer> multiset = new Multiset<>(l);
    assertEquals(this.numbers, multiset);

    try {
      this.numbers.entries().forEach(e -> e.setValue(1234));
      fail("can't modify entries!");
    } catch (final Exception e2) {
      // expected
    }

    try {
      Multiset.unmodifiableMultiset(this.numbers).entries().forEach(e -> e.setValue(1234));
      fail("can't modify entries!");
    } catch (final Exception e2) {
      // expected
    }
  }

  @Test
  public final void testAddAllCollection() {
    this.abc.addAll(this.abc.toSet());
    for (final Character c : this.abc.asSet())
      assertEquals(2, this.abc.getMultiplicity(c));
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Test
  public final void testAddAll() {
    this.numbers.addAll(this.numbers.clone());
    for (final Integer i : this.numbers.asSet())
      assertEquals(2 * i, this.numbers.getMultiplicity(i));

    int size = this.numbers.size();
    this.numbers.addAll(asList(1, 2, 3, 4));
    assertEquals(size + 4, this.numbers.size());

    size = this.abc.size();
    this.abc.addAll(Multiset.of('a', 'b', 'A', 'B', '!', '?', '1', '2'));
    assertEquals(size + 8, this.abc.size());

    // This does nothing:
    this.empty.addAll((Collection) this.empty);

    try {
      this.empty.addAll((Collection) this.abc);
    } catch (final UnsupportedOperationException e) {
      // expected
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Test
  public final void testEquals() {
    for (final Multiset ms : this.list) {
      assertTrue(ms.equals(ms));
      assertFalse(ms.equals(new Object()));
      assertEquals(ms.isEmpty(), ms.equals(ms.union(ms)));
    }
    assertTrue(
        Multiset.ofMap(Map.of("x", 1, "y", 5)).equals(Multiset.ofMap(Map.of("x", 1, "y", 5))));
    assertTrue(this.empty.equals(Multiset.of()));
    assertFalse(this.abc.equals(this.numbers));
    assertFalse(this.numbers.equals(this.abc));
    assertFalse(this.numbers.equals(this.empty));
    assertFalse(Multiset.of(1, 2, 3).equals(Multiset.of(1, 2, 4)));
  }

  @Test
  public final void testClone() {
    for (final Multiset<?> ms : this.list)
      assertEquals(ms, ms.clone());
  }

  @Test
  @SuppressWarnings("unlikely-arg-type")
  public final void testContainsAll() {
    for (final Multiset<?> ms : this.list)
      assertTrue(ms.containsAll(ms));

    assertFalse(this.abc.contains(this.numbers));
    assertFalse(this.numbers.contains(this.abc));

  }

  @Test
  public final void testForEach() {
    final List<Character> l = new ArrayList<>();
    this.abc.forEach(e -> l.add(e));
    assertEquals(l.size(), this.abc.size());

    final List<Integer> l2 = new ArrayList<>();
    this.numbers.forEach((e, m) -> l2.add(e));
    assertEquals(l2.size(), this.numbers.asSet().size());
  }

  @Test
  public final void testContains() {
    assertTrue(this.abc.contains('a'));
    assertFalse(this.abc.contains('ä'));
    assertFalse(this.numbers.contains(null));
    assertFalse(this.numbers.contains(new Object()));
  }

  @Test
  public final void testIntersect() {
    assertEquals(this.empty, this.abc.intersect(this.numbers));
    assertEquals(this.empty, this.abc.intersect(this.numbers, Serializable.class));
    assertEquals(this.abc, this.abc.intersect(this.abc));
    assertEquals(this.abc, this.abc.intersect(this.abc, Constable.class));

    assertEquals(Multiset.of(1, 2, 2, 3),
        Multiset.of(1, 1, 2, 2, 3, 3).intersect(Multiset.of(1, 2, 2, 3)));

    assertEquals(Multiset.of(1, 2, 2, 3),
        Multiset.of(1, 1, 2, 2, 3, 3).intersect(Multiset.of(1, 2, 2, 3), Integer.class));

  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Test
  public final void testUnion() {
    final Multiset union = new Multiset<>();
    union.addAll(this.abc);
    union.addAll(this.abc);

    assertEquals(union, this.abc.union(this.abc));

    final Multiset<Number> numbers2 = new Multiset<>(this.numbers);
    final Multiset<Double> doubles = Multiset.of(-1.0, Math.PI, Math.E);
    final Multiset<Number> union2 = numbers2.union(doubles);
    assertEquals(this.numbers.size() + doubles.size(), union2.size());

    union.clear();
    union.addAll(this.abc);
    union.addAll(this.numbers);
    assertEquals(union, this.abc.union(this.numbers, Serializable.class));

  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  @Test
  public final void testMinus() {
    for (final Multiset ms : this.list)
      assertEquals(this.empty, ms.minus(ms));
    final Multiset<Integer> minus = Multiset.of(1, 2, 2, 3, 3, 3, 4, 5, 5, 6, 6, 6)
        .minus(Multiset.of(1, 1, 1, 2, 2, 3));
    assertEquals(Multiset.of(4, 5, 5, 6, 6, 6), minus);
  }

  @Test
  public final void testMerge() {

    {
      final Multiset<Character> xyz = Multiset.of('x', 'y', 'z');
      final Multiset<Serializable> merge = //
          this.abc.merge(xyz, (a, b) -> a * b, Serializable.class);
      assertEquals(xyz, merge);
    }

    {
      final Multiset<Integer> merge = this.numbers.merge(this.numbers, (a, b) -> a * b);
      for (int n = 0; n < 10; n++)
        assertEquals(n * n, merge.getMultiplicity(n));
    }

  }

  @Test
  public final void testUnmodifiableMultiset() {
    final Multiset<Character> ums = Multiset.unmodifiableMultiset(this.abc);
    try {
      ums.insert('ö');
      fail("unmodifiableMultiset");
    } catch (final Exception e) {
      // expected
    }
    try {
      ums.asMap().put('ö', 5);
      fail("unmodifiableMultiset");
    } catch (final Exception e) {
      // expected
    }
    try {
      ums.asSet().add('ö');
      fail("unmodifiableMultiset");
    } catch (final Exception e) {
      // expected
    }
    try {
      final Iterator<Character> itr = ums.iterator();
      itr.next();
      itr.remove();
      fail("unmodifiableMultiset");
    } catch (final Exception e) {
      // expected
    }

    assertSame(this.empty, Multiset.unmodifiableMultiset(this.empty));
  }

  @Test
  public void testOfCollection() throws Exception {
    for (final Multiset<?> multiset : this.list) {
      final Multiset<?> wrap = Multiset.ofMap(multiset.asMap());
      assertEquals(multiset, wrap);
    }
    {
      final Multiset<Character> multiset = Multiset.ofMap(this.abc.asMap());
      for (int i = 0; i < 10; i++) {
        multiset.insert('ä');
        multiset.remove((char) ('a' + i));
        assertEquals(this.abc, multiset);
      }
    }
    {
      final Multiset<Character> multiset = Multiset.ofSupplier(() -> this.abc.asMap());
      assertEquals(this.abc, multiset);
    }
    for (final Supplier<Map<Character, Integer>> ctor : Arrays
        .<Supplier<Map<Character, Integer>>> asList(HashMap::new, TreeMap::new,
            ConcurrentHashMap::new)) {
      final Multiset<Character> wrap = Multiset.ofSupplier(ctor);
      assertEquals(this.empty, wrap);
      wrap.addAll(this.abc);
      assertEquals(this.abc, wrap);
    }
    {
      final Multiset<Foo> multiset = Multiset.ofSupplier(() -> new EnumMap<>(Foo.class));
      assertEquals(this.empty, multiset);
      multiset.add(Foo.BLA);
      multiset.add(Foo.BLUBB);
      assertEquals(Multiset.of(Foo.BLA, Foo.BLUBB), multiset);
    }

    final Map<String, Integer> map = new HashMap<>();
    try {
      map.clear();
      map.put("foo", 0);
      final Multiset<String> multiset = Multiset.ofMap(map);
      fail("IAE expected!" + multiset);
    } catch (final IllegalArgumentException e) {
      // expected
    }
    try {
      map.clear();
      map.put("foo", -3);
      final Multiset<String> multiset = Multiset.ofMap(map);
      fail("IAE expected! " + multiset);
    } catch (final IllegalArgumentException e) {
      // expected
    }
    try {
      map.clear();
      map.put("foo", null);
      final Multiset<String> multiset = Multiset.ofMap(map);
      fail("NPE expected! " + multiset);
    } catch (final NullPointerException e) {
      // expected
    }
  }

  enum Foo {
    BLA, BLUBB;
  }

  @Test
  public final void testIsUnmodifiable() {
    assertFalse(this.abc.isUnmodifiable());
    assertFalse(this.numbers.isUnmodifiable());
    assertTrue(this.empty.isUnmodifiable());
    assertTrue(Multiset.unmodifiableMultiset(this.abc).isUnmodifiable());
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  @Test
  public final void testAsMap() {
    {
      final Multiset<Character> actual = this.abc.clone();
      for (final Entry<Character, Integer> entry : actual.asMap().entrySet())
        entry.setValue(entry.getValue() + 1);

      final Multiset<Character> expected = this.abc.clone();
      expected.setMultiplicities((x, m) -> m + 1);
      assertEquals(expected, actual);
    }

    this.list.stream().map(ms -> ms.clone()).forEach(ms -> {
      final Map<?, Integer> map = ms.asMap();
      assertEquals(map, ms.map);

      map.entrySet().forEach(entry -> {
        try {
          entry.setValue(-1);
          fail("negative multiplicity");
        } catch (final Exception e) {
          // expected
        }
        try {
          entry.setValue(0);
          fail("multiplicity=0 => remove");
        } catch (final Exception e) {
          // expected
        }
        assertEquals(Objects.hashCode(entry.getKey()) ^ entry.getValue(), entry.hashCode());
        assertEquals(entry.getKey() + "=" + entry.getValue(), entry.toString());
        assertTrue(entry.equals(entry));
        try {
          assertTrue(entry.equals(ms.entries().filter(e -> e.equals(entry)).findAny()
              .orElseThrow(NoSuchElementException::new)));
        } catch (final Exception e) {
          fail("Can't compare entries: " + e);
        }
        assertFalse(entry.equals(new Object()));
      });
      if (!ms.isEmpty()) {
        final int size = ms.size();
        @SuppressWarnings("cast")
        final Set<Entry<?, Integer>> entrySet = (Set<Entry<?, Integer>>) (Set) map.entrySet();
        final Entry<?, Integer> someEntry = entrySet.iterator().next();
        assertTrue(entrySet.containsAll(ms.map.entrySet()));
        assertTrue(entrySet.contains(someEntry));
        assertTrue(entrySet.remove(someEntry));
        assertFalse(entrySet.remove(someEntry));
        assertFalse(entrySet.contains(someEntry));
        assertEquals(size - someEntry.getValue(), ms.size());
        entrySet.clear();
        assertTrue(ms.isEmpty());
        try {
          entrySet.add(someEntry);
          fail("Can't add to entry set");
        } catch (final Exception e) {
          // expected
        }
      }
    });
  }

  @Test
  public final void testAsSet() {
    final Set<Character> set = this.abc.asSet();
    this.abc.add(null);
    this.abc.add('ö');
    assertEquals(1, this.abc.getMultiplicity('ö'));
    for (final Iterator<Character> itr = set.iterator(); itr.hasNext();)
      if (itr.next() == null)
        itr.remove();

    assertEquals(0, this.abc.getMultiplicity(null));
    assertEquals(1, this.abc.getMultiplicity('ö'));

  }

  @Test
  public void testCheckedMultiset() throws Exception {
    {
      final Multiset<Character> checked = Multiset.checkedMultiset(this.abc, Character.class);
      assertTrue(checked.add('ö'));
      assertTrue(this.abc.contains('ö'));

      final Object o = "Not A Character";
      try {
        checked.add((Character) o);
        fail("checkedMultiset");
      } catch (final ClassCastException e) {
        // expected
      }
    }
    {
      final Map<String, Integer> map = new HashMap<>();
      try {
        Multiset.checkedMultiset(map, String.class).insert((String) (Object) 42);
        fail("checkedMultiset");
      } catch (final ClassCastException e) {
        // expected
      }
      assertTrue(map.isEmpty());
      try {
        map.put((String) (Object) 42, 123);
        final Multiset<String> checked = Multiset.checkedMultiset(map, String.class);
        fail("checkedMultiset: " + checked);
      } catch (final ClassCastException e) {
        // expected
      }
      map.clear();
      map.put("foo", 5);
      map.put(null, 5);
      Multiset.checkedMultiset(map, String.class).insert("bla");
      assertEquals(3, map.size());
      assertEquals(5 + 5 + 1, Multiset.ofMap(map).size());
    }
  }

  @Test
  public void testAllWrappers() throws Exception {
    {
      final Multiset<Character> wrap = Multiset.ofMap(Multiset
          .checkedMultiset(Multiset.unmodifiableMultiset(this.abc), Character.class).asMap());
      try {
        wrap.add((Character) (Object) "FOO");
        fail("checkedMultiset");
      } catch (final ClassCastException e) {
        // expected
      }
      try {
        wrap.add('ö');
        fail("unmodifiableMultiset");
      } catch (final UnsupportedOperationException e) {
        // expected
      }
    }
  }

  @Test
  public void testCollector() throws Exception {
    {
      final Multiset<Character> collected = IntStream.rangeClosed('a', 'z').parallel()
          .mapToObj(i -> (char) i).collect(Multiset.collector());
      assertEquals(this.abc, collected);
    }
    for (final Multiset<?> ms : this.list) {
      {
        final Multiset<?> collected = ms.stream().collect(Multiset.collector());
        assertEquals(ms, collected);
      }
      {
        final Multiset<?> collected = ms.parallelStream()
            .collect(Multiset.collector(() -> Multiset.ofSupplier(TreeMap::new)));
        assertEquals(ms, collected);
      }
    }
    {
      final Set<Integer> set = IntStream.range(-1024, 1024).mapToObj(i -> i)
          .collect(Collectors.toSet());
      final Multiset<Integer> bag = Multiset.of(set, i -> Math.abs(i % 137));
      final Multiset<Integer> collected = bag.parallelStream().collect(Multiset.collector());
      assertEquals(bag, collected);
    }
  }

  @Test
  public void testIsSubmultisetOf() throws Exception {
    assertFalse(Multiset.of(new Object()).isSubmultisetOf(this.empty));
    assertTrue(this.empty.isSubmultisetOf(this.empty));

    for (final Multiset<?> ms : this.list) {
      assertTrue(this.empty.isSubmultisetOf(ms));
      assertTrue(ms.isSubmultisetOf(ms));
      if (!ms.isEmpty())
        for (final Object o : ms)
          assertTrue(Multiset.of(o).isSubmultisetOf(ms));
      assertFalse(Multiset.of(new Object()).isSubmultisetOf(ms));

      final Multiset<Object> ms2 = ms.union(ms, Object.class);
      assertEquals(ms2.size(), ms.size() * 2);
      assertTrue(ms.isSubmultisetOf(ms2));
      assertTrue(ms2.isSubmultisetOf(ms2));
      assertTrue(ms.isEmpty() == ms2.isSubmultisetOf(ms));
    }
  }

  @Test
  public void testSerialize() throws Exception {
    for (final Multiset<?> ms : this.list)
      try (final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        try (final ObjectOutputStream out2 = new ObjectOutputStream(out)) {
          out2.writeObject(ms);
        }
        try (final ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray())) {
          try (final ObjectInputStream in2 = new ObjectInputStream(in)) {
            final Multiset<?> clone = (Multiset<?>) in2.readObject();
            assertEquals(ms, clone);
          }
        }
      }
  }
}
