package org.example.am.internal.web.interceptors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

/**
 * Covers the parameter <em>name</em> rules, which is where the indexed properties of a repeating
 * form live.
 *
 * <p>Tested through the private matcher by reflection rather than through {@code intercept}: the
 * latter needs a live {@code ActionInvocation} and {@code ServletActionContext}, and the rule being
 * checked is a pure string property. The trade is deliberate - this stays a unit test, and the
 * end-to-end path is covered by actually submitting the subscriber PC grid.</p>
 */
public class ValidateSpecialCharacterInterceptorTest {

    private static boolean acceptsName(final String name) throws Exception {
        final Method method = ValidateSpecialCharacterInterceptor.class
                .getDeclaredMethod("isAcceptedName", String.class);
        method.setAccessible(true);
        return ((Boolean) method.invoke(null, name)).booleanValue();
    }

    @Test
    public void ordinaryNamesAreAccepted() throws Exception {
        assertTrue(acceptsName("searchTerm"));
        assertTrue(acceptsName("shippingAddress.addressLine1"));
        assertTrue(acceptsName("_csrf"));
    }

    /**
     * The case this rule exists for: the subscriber PC grid posts one set of fields per row, and
     * without brackets every one of those requests was refused.
     */
    @Test
    public void indexedPropertiesAreAccepted() throws Exception {
        assertTrue(acceptsName("subscriberPcs[0].hostName"));
        assertTrue(acceptsName("subscriberPcs[12].subscriberPcType.code"));
        assertTrue(acceptsName("subscriberPcs[999].ipAddress"));
    }

    /**
     * Struts hands the name to OGNL, which evaluates whatever sits between the brackets. Anything
     * that is not a plain index is therefore an expression, and refused.
     */
    @Test
    public void anythingOtherThanAnIndexInsideTheBracketsIsRejected() throws Exception {
        assertFalse(acceptsName("subscriberPcs[@java.lang.Runtime@getRuntime()].hostName"));
        assertFalse(acceptsName("subscriberPcs[top].hostName"));
        assertFalse(acceptsName("subscriberPcs[].hostName"));
        assertFalse(acceptsName("subscriberPcs[0=1].hostName"));
        // Four digits is past any grid this application renders.
        assertFalse(acceptsName("subscriberPcs[1234].hostName"));
    }

    @Test
    public void unbalancedBracketsAreRejected() throws Exception {
        assertFalse(acceptsName("subscriberPcs[0.hostName"));
        assertFalse(acceptsName("subscriberPcs0].hostName"));
    }

    @Test
    public void theOriginalCharacterRulesStillApplyToNames() throws Exception {
        assertFalse(acceptsName("<script>"));
        assertFalse(acceptsName("name;drop"));
        assertFalse(acceptsName("name\"quoted"));
    }

    /**
     * Brackets are accepted in a name but not in a value: no screen here produces one in a value,
     * so accepting them there would widen the surface for nothing.
     */
    @Test
    public void bracketsAreStillRejectedInAValue() throws Exception {
        final Method method = ValidateSpecialCharacterInterceptor.class
                .getDeclaredMethod("isAccepted", String.class);
        method.setAccessible(true);
        assertEquals(Boolean.FALSE, method.invoke(null, "till[0]"));
        assertEquals(Boolean.TRUE, method.invoke(null, "till-01"));
    }
}
