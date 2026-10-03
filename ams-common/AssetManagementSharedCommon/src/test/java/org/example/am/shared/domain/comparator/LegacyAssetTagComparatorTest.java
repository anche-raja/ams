package org.example.am.shared.domain.comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.example.am.shared.domain.Asset;
import org.junit.jupiter.api.Test;

public class LegacyAssetTagComparatorTest {

    @Test
    public void tagsAreSortedNumericallyNotLexicographically() {
        final List<Asset> assets = new ArrayList<Asset>();
        assets.add(asset("NW-9"));
        assets.add(asset("NW-10"));
        assets.add(asset("NW-2"));

        Collections.sort(assets, new LegacyAssetTagComparator());

        assertEquals("NW-2", assets.get(0).getLegacyAssetTag());
        assertEquals("NW-9", assets.get(1).getLegacyAssetTag());
        assertEquals("NW-10", assets.get(2).getLegacyAssetTag());
    }

    @Test
    public void nullTagsAreMovedToTheEnd() {
        final List<Asset> assets = new ArrayList<Asset>();
        assets.add(asset(null));
        assets.add(asset("NW-1"));

        Collections.sort(assets, new LegacyAssetTagComparator());

        assertEquals("NW-1", assets.get(0).getLegacyAssetTag());
        assertEquals(null, assets.get(1).getLegacyAssetTag());
    }

    @Test
    public void multipleNullsAreHandledGracefully() {
        final List<Asset> assets = new ArrayList<Asset>();
        assets.add(asset(null));
        assets.add(asset(null));

        Collections.sort(assets, new LegacyAssetTagComparator());

        assertTrue(assets.get(0).getLegacyAssetTag() == null);
        assertTrue(assets.get(1).getLegacyAssetTag() == null);
    }

    private static Asset asset(final String tag) {
        final Asset asset = new Asset();
        asset.setLegacyAssetTag(tag);
        return asset;
    }
}
