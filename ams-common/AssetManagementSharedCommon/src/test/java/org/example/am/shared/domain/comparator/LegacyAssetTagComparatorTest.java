package org.example.am.shared.domain.comparator;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.example.am.shared.domain.Asset;
import org.junit.Test;

public class LegacyAssetTagComparatorTest {

    private static Asset withLegacyTag(final String tag) {
        final Asset asset = new Asset();
        asset.setLegacyAssetTag(tag);
        return asset;
    }

    private static List<String> sorted(final String... tags) {
        final List<Asset> assets = new ArrayList<Asset>();
        for (final String tag : tags) {
            assets.add(withLegacyTag(tag));
        }
        Collections.sort(assets, new LegacyAssetTagComparator());
        final List<String> result = new ArrayList<String>();
        for (final Asset asset : assets) {
            result.add(asset.getLegacyAssetTag());
        }
        return result;
    }

    /** The whole point of the comparator: AB-10 must not sort before AB-9. */
    @Test
    public void numericSuffixesSortNumericallyNotLexically() {
        assertEquals(java.util.Arrays.asList("AB9", "AB10", "AB100"), sorted("AB100", "AB10", "AB9"));
    }

    @Test
    public void thePrefixIsCompatedBeforeTheNumber() {
        assertEquals(java.util.Arrays.asList("AA100", "AB9"), sorted("AB9", "AA100"));
    }

    @Test
    public void tagsWithoutTheExpectedShapeSortAfterThoseWithIt() {
        assertEquals(java.util.Arrays.asList("AB9", "AB9X", "ZZZ"), sorted("ZZZ", "AB9X", "AB9"));
    }

    @Test
    public void nullTagsSortLast() {
        assertEquals(java.util.Arrays.asList("AB9", null), sorted("AB9", null));
    }
}
