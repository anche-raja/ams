package org.example.am.shared.dao.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.example.am.shared.dao.CustomerDAO;
import org.example.am.shared.domain.ContactType;
import org.example.am.shared.domain.Customer;
import org.example.am.shared.domain.ServiceType;
import org.example.am.shared.helper.AbstractBaseTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class CustomerDAOImplTest extends AbstractBaseTest {

    @Autowired
    @Qualifier("customerSharedDAO")
    private CustomerDAO customerDAO;

    @Test
    public void customerComesBackWithContactsAndServicesPopulated() {
        final Customer customer = customerDAO.getCustomer(CUSTOMER_ID);
        assertNotNull(customer);
        assertEquals("Riverbend Dental Group", customer.getCustomerName());
        assertEquals(4, customer.getContacts().size());
        assertEquals(2, customer.getServices().size());
        assertTrue(customer.hasActiveService());
        assertTrue(customer.hasActiveService(ServiceType.MANAGED_ROUTER));
    }

    @Test
    public void earlyAdopterColumnsOnlyMaterialiseForEnrolledCustomers() {
        assertNotNull(customerDAO.getCustomer(CUSTOMER_ID).getCustomerEarlyAdopter());
        assertTrue(customerDAO.getCustomer(CUSTOMER_ID).isEarlyAdopter());
        assertNull(customerDAO.getCustomer(OTHER_CUSTOMER_ID).getCustomerEarlyAdopter());
        assertFalse(customerDAO.getCustomer(OTHER_CUSTOMER_ID).isEarlyAdopter());
    }

    @Test
    public void unknownCustomerReturnsNullRatherThanAnEmptyShell() {
        assertNull(customerDAO.getCustomer(999999L));
    }

    @Test
    public void inactiveContactsAreStillLoadedButAreNotOfferedByType() {
        final Customer customer = customerDAO.getCustomer(CUSTOMER_ID);
        assertEquals(4, customer.getContacts().size());
        assertNotNull(customer.getContactByType(ContactType.ORDERING));
        // 2004 is a TECH contact, but it is inactive.
        assertNull(customer.getContactByType(ContactType.TECHNICAL));
    }

    @Test
    public void orderingFlagCanBeToggled() {
        assertTrue(customerDAO.getCustomer(CUSTOMER_ID).isOrderingEnabled());

        assertEquals(1, customerDAO.updateCanSubmitOrders(CUSTOMER_ID, false, TEST_USER));
        assertFalse(customerDAO.getCustomer(CUSTOMER_ID).isOrderingEnabled());

        assertEquals(0, customerDAO.updateCanSubmitOrders(999999L, true, TEST_USER));
    }

    @Test
    public void terminatedServicesDoNotCountAsActive() {
        assertFalse(customerDAO.getCustomer(OTHER_CUSTOMER_ID).hasActiveService());
        // Ordering also needs the flag, which 1002 does not have.
        assertFalse(customerDAO.getCustomer(OTHER_CUSTOMER_ID).isOrderingEnabled());
    }

    @Test
    public void inactiveCustomerCannotOrderEvenWithTheFlagSet() {
        final Customer inactive = customerDAO.getCustomer(1003L);
        assertTrue(inactive.isCanSubmitOrders());
        assertFalse(inactive.isActive());
        assertFalse(inactive.isOrderingEnabled());
    }

    @Test
    public void earlyAdopterCheckHasItsOwnCountQuery() {
        assertTrue(customerDAO.isEarlyAdopter(CUSTOMER_ID));
        assertFalse(customerDAO.isEarlyAdopter(OTHER_CUSTOMER_ID));
    }
}
