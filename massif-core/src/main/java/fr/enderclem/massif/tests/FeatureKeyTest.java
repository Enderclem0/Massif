package fr.enderclem.massif.tests;

import fr.enderclem.massif.blackboard.FeatureKey;

/**
 * Covers the {@code namespace:local_name} naming contract for FeatureKey:
 * good names parse, bad names fail loudly, namespaces split correctly.
 */
public final class FeatureKeyTest {

    private FeatureKeyTest() {}

    public static int run() {
        try {
            validNamesParse();
            invalidNamesAreRejected();
            equalityIsByFullName();
            System.out.println("  FeatureKeyTest: OK");
            return 0;
        } catch (Throwable t) {
            System.err.println("  FeatureKeyTest FAILED: " + t.getMessage());
            t.printStackTrace(System.err);
            return 1;
        }
    }

    private static void validNamesParse() {
        FeatureKey<Integer> k = FeatureKey.of("core:zone_graph", Integer.class);
        TestAssert.assertTrue("core:zone_graph".equals(k.name()), "name");
        TestAssert.assertTrue("core".equals(k.namespace()), "namespace");
        TestAssert.assertTrue("zone_graph".equals(k.localName()), "local");

        FeatureKey<String> nested = FeatureKey.of("biomesoplenty:canopy_layer", String.class);
        TestAssert.assertTrue("biomesoplenty".equals(nested.namespace()), "third-party namespace");
        TestAssert.assertTrue("canopy_layer".equals(nested.localName()), "third-party local");
    }

    private static void invalidNamesAreRejected() {
        TestAssert.assertThrows(IllegalArgumentException.class,
            () -> FeatureKey.of("no_colon", Integer.class), "no colon");
        TestAssert.assertThrows(IllegalArgumentException.class,
            () -> FeatureKey.of(":trailing", Integer.class), "empty namespace");
        TestAssert.assertThrows(IllegalArgumentException.class,
            () -> FeatureKey.of("leading:", Integer.class), "empty local name");
        TestAssert.assertThrows(IllegalArgumentException.class,
            () -> FeatureKey.of("too:many:colons", Integer.class), "two colons");
    }

    private static void equalityIsByFullName() {
        FeatureKey<Integer> a = FeatureKey.of("core:k", Integer.class);
        FeatureKey<Integer> b = FeatureKey.of("core:k", Integer.class);
        FeatureKey<Integer> c = FeatureKey.of("core:other", Integer.class);
        TestAssert.assertTrue(a.equals(b), "same name equal");
        TestAssert.assertTrue(a.hashCode() == b.hashCode(), "same name same hash");
        TestAssert.assertTrue(!a.equals(c), "different names unequal");
    }
}
