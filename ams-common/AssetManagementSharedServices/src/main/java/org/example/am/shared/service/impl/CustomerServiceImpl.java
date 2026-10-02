package org.example.am.shared.service.impl;

import java.util.List;

import org.example.am.shared.dao.ContactDAO;
import org.example.am.shared.dao.CustomerDAO;
import org.example.am.shared.domain.AmsService;
import org.example.am.shared.domain.Contact;
import org.example.am.shared.domain.ContactType;
import org.example.am.shared.domain.Customer;
import org.example.am.shared.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service("customerService")
@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    @Autowired
    private CustomerDAO customerDAO;

    @Autowired
    private ContactDAO contactDAO;

    @Override
    public Customer getCustomer(final long customerId) {
        return customerDAO.getCustomer(customerId);
    }

    @Override
    public List<Contact> getActiveContacts(final long customerId, final ContactType contactType) {
        return contactDAO.getActiveContacts(customerId, contactType);
    }

    @Override
    public List<AmsService> getServices(final long customerId) {
        return customerDAO.getServicesForCustomer(customerId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = false)
    public boolean updateCanSubmitOrders(final long customerId, final boolean canSubmitOrders,
            final String userId) {
        return customerDAO.updateCanSubmitOrders(customerId, canSubmitOrders, userId) > 0;
    }

    @Override
    public boolean isOrderingEnabled(final long customerId) {
        final Customer customer = customerDAO.getCustomer(customerId);
        return customer != null && customer.isOrderingEnabled();
    }
}
